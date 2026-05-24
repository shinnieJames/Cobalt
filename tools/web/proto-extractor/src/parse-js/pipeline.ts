import { parse } from "acorn";
import * as walk from "acorn-walk";
import type { JsChunk } from "../fetch/index.js";
import type {
    CrossRef,
    EnumValue,
    Identifier,
    IndentationEntry,
    ModuleInfo,
    ParsedProtos,
} from "../types.js";
import {
    type AstNode,
    extractAllExpressions,
    getNesting,
    renameIdentifier,
} from "./ast.js";
import { applyConstraints, findByAlias, parseMember } from "./members.js";

/** Parses one JS chunk and returns every top-level {@code __d("Module", ...)} call whose body assigns to {@code <alias>.internalSpec}. */
function findProtoModules(chunk: JsChunk): AstNode[] {
    /* Some chunks contain a `Foo$Bar` enum split across two declarations.
     * The legacy extractor patched one specific symbol; we apply the same
     * defensive patch so the AST parse succeeds. */
    const patched = chunk.content.replaceAll(
        "LimitSharing$Trigger",
        "LimitSharing$TriggerType",
    );

    let body: AstNode[];
    try {
        body = parse(patched, { ecmaVersion: "latest", sourceType: "script" }).body as AstNode[];
    } catch (err) {
        const message = err instanceof Error ? err.message : String(err);
        console.warn(`[WARN] Skipping ${chunk.url} (parse failure): ${message}`);
        return [];
    }

    return body.filter((m) => {
        const expressions = extractAllExpressions(m);
        return expressions.some((e) => e?.left?.property?.name === "internalSpec");
    });
}

/** Walks one proto module and collects every cross-module {@code r("$Other")} alias assignment. */
function collectCrossRefs(module: AstNode, info: ModuleInfo): void {
    walk.simple(module, {
        AssignmentExpression(node: AstNode) {
            if (
                node?.right?.type === "CallExpression" &&
                Array.isArray(node.right.arguments) &&
                node.right.arguments.length === 1 &&
                node.right.arguments[0].type !== "ObjectExpression"
            ) {
                const ref: CrossRef = {
                    alias: node.left.name,
                    module: node.right.arguments[0].value,
                };
                info.crossRefs.push(ref);
            }
        },
    });
}

/** Walks one proto module and seeds an empty {@link Identifier} for every member assignment. */
function collectIdentifiers(
    module: AstNode,
    info: ModuleInfo,
    indentation: Record<string, IndentationEntry>,
): void {
    const assignments: AstNode[] = [];
    walk.simple(module, {
        AssignmentExpression(node: AstNode) {
            const left = node?.left;
            const propName = left?.property?.name;
            if (
                propName &&
                propName !== "internalSpec" &&
                propName !== "internalDefaults" &&
                propName !== "name"
            ) {
                assignments.push(left);
            }
        },
    });

    const seeded: [string, Identifier][] = assignments.map((a) => {
        const key = renameIdentifier(a.property.name);
        const parentPath = getNesting(key);

        indentation[key] = indentation[key] ?? {};
        indentation[key]!.indentation = parentPath;

        if (parentPath.length) {
            indentation[parentPath] = indentation[parentPath] ?? {};
            indentation[parentPath]!.members = indentation[parentPath]!.members ?? new Set<string>();
            indentation[parentPath]!.members!.add(key);
        }

        return [key, { name: key }];
    });

    /* The legacy code reversed before building the lookup table so that when
     * the same identifier is assigned twice in source order, the FIRST one
     * wins after Object.fromEntries dedupes by key. */
    info.identifiers = Object.fromEntries(seeded.reverse());
}

/**
 * Walks one proto module and collects the enum bodies keyed by their local alias.
 *
 * @remarks
 * WA Web emits enums in two shapes:
 * - {@code A.internalSpec={NAME:0, ...}} - direct ObjectExpression assignment to
 *   {@code internalSpec}.
 * - {@code A=t({NAME:0, ...})} - wrapper-call form where the values live inside
 *   the first call argument.
 */
function collectEnumAliases(module: AstNode): Record<string, EnumValue[]> {
    const enumAliases: Record<string, EnumValue[]> = {};
    walk.ancestor(module, {
        Property(node: AstNode, _state: unknown, ancestors: AstNode[]) {
            const father = ancestors[ancestors.length - 3];
            const grandfather = ancestors[ancestors.length - 4];

            if (
                father?.type === "AssignmentExpression" &&
                father?.left?.property?.name === "internalSpec" &&
                father?.right?.properties?.length
            ) {
                const values: EnumValue[] = father.right.properties.map((p: AstNode) => ({
                    name: p.key.name,
                    id: p.value.value,
                }));
                enumAliases[father.left.name] = values;
            } else if (
                node?.key?.name &&
                father?.arguments?.length > 0
            ) {
                const values: EnumValue[] = father.arguments[0]?.properties?.map((p: AstNode) => ({
                    name: p.key.name,
                    id: p.value.value,
                })) ?? [];
                const nameAlias = grandfather?.left?.name ?? grandfather?.id?.name;
                if (nameAlias) {
                    enumAliases[nameAlias] = values;
                }
            }
        },
    } as walk.AncestorVisitors<unknown>);
    return enumAliases;
}

/** Resolves each identifier's local alias and, for enums, copies the matching value list onto it. */
function linkIdentifierAliases(
    module: AstNode,
    info: ModuleInfo,
    enumAliases: Record<string, EnumValue[]>,
): void {
    walk.simple(module, {
        AssignmentExpression(node: AstNode) {
            if (
                node?.left?.type === "MemberExpression" &&
                info.identifiers[renameIdentifier(node.left.property.name)]
            ) {
                const ident = info.identifiers[renameIdentifier(node.left.property.name)]!;
                ident.alias = node.right.name;
                ident.enumValues = enumAliases[ident.alias!];
            }
        },
    });
}

/** Walks one module and attaches the field list to every identifier whose {@code internalSpec} was declared inline. */
function collectMessageMembers(
    module: AstNode,
    info: ModuleInfo,
    allModules: Record<string, ModuleInfo>,
): void {
    walk.simple(module, {
        AssignmentExpression(node: AstNode) {
            if (
                node?.left?.type !== "MemberExpression" ||
                node.left.property?.name !== "internalSpec" ||
                node.right?.type !== "ObjectExpression"
            ) {
                return;
            }

            const targetIdent = findByAlias(info.identifiers, node.left.object.name);
            if (!targetIdent) {
                console.warn(`found message specification for unknown identifier alias: ${node.left.object.name}`);
                return;
            }

            const constraints: AstNode[] = [];
            const rawMembers: AstNode[] = [];
            for (const p of node.right.properties) {
                p.key.name = p.key.type === "Identifier" ? p.key.name : p.key.value;
                (p.key.name.substring(0, 2) === "__" ? constraints : rawMembers).push(p);
            }

            const parsed = rawMembers.map((p) =>
                parseMember(p.key.name, p.value.elements, info, allModules, targetIdent.name),
            );

            targetIdent.members = applyConstraints(constraints, parsed);
        },
    });
}

/**
 * Extracts every protobuf identifier (messages and enums) declared across the
 * given JS chunks served by WhatsApp Web.
 *
 * @param chunks - JS chunks downloaded from WhatsApp Web.
 * @returns the extracted schema in {@link ParsedProtos} shape.
 *
 * @remarks
 * Runs three passes per module so cross-references resolve cleanly:
 * 1. Seed identifiers, collect cross-module aliases, attach enum bodies.
 * 2. Link the local one-letter alias of each identifier.
 * 3. Resolve every {@code internalSpec} field, including cross-module
 *    references.
 */
export function parseProtoModules(chunks: readonly JsChunk[]): ParsedProtos {
    const modulesInfo: Record<string, ModuleInfo> = {};
    const indentation: Record<string, IndentationEntry> = {};
    const moduleOrder: string[] = [];
    const allModuleNodes: { name: string; module: AstNode }[] = [];

    for (const chunk of chunks) {
        const modules = findProtoModules(chunk);
        for (const module of modules) {
            const moduleName: string | undefined = module?.expression?.arguments?.[0]?.value;
            if (!moduleName || modulesInfo[moduleName]) continue;

            modulesInfo[moduleName] = { crossRefs: [], identifiers: {} };
            moduleOrder.push(moduleName);
            allModuleNodes.push({ name: moduleName, module });

            collectCrossRefs(module, modulesInfo[moduleName]!);
        }
    }

    for (const { name, module } of allModuleNodes) {
        const info = modulesInfo[name]!;
        collectIdentifiers(module, info, indentation);
        const enumAliases = collectEnumAliases(module);
        linkIdentifierAliases(module, info, enumAliases);
    }

    for (const { name, module } of allModuleNodes) {
        collectMessageMembers(module, modulesInfo[name]!, modulesInfo);
    }

    return { modulesInfo, indentation, moduleOrder };
}
