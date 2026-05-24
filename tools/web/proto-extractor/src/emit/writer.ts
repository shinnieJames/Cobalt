import { mkdir, writeFile } from "node:fs/promises";
import { dirname } from "node:path";
import type { ParsedProtos } from "../types.js";
import { getEntityLines } from "./stringify.js";

/** Module-name prefix used by {@code parseProtobufCFromWasm} to label its synthetic module. */
const WASM_MODULE_PREFIX = "wasm:";

const JS_BANNER = [
    "// ============================================================",
    "// Extracted from WhatsApp Web JavaScript modules",
    "// (internalSpec declarations on protobuf-runtime classes)",
    "// ============================================================",
].join("\n");

const WASM_BANNER = [
    "// ============================================================",
    "// Extracted from WhatsApp Web WASM modules",
    "// (protobuf-c reflection tables in native code)",
    "// ============================================================",
].join("\n");

/**
 * Serialises every top-level identifier in {@code parsed} into proto2 source.
 *
 * @param parsed - the parsed proto schema to emit.
 * @param version - the WhatsApp Web version string to embed in the header.
 * @param pkg - the proto package declaration to emit.
 * @returns the proto2 source.
 *
 * @remarks
 * The output is split into two sections, each sorted alphabetically: first
 * the JS-derived protos (extracted from {@code internalSpec} declarations),
 * then the wasm-derived protos (extracted from {@code protobuf-c} reflection
 * tables inside native modules). A banner comment introduces each section.
 */
export function generateProtoSource(
    parsed: ParsedProtos,
    version: string,
    pkg: string,
): string {
    const jsDecoded: Record<string, string> = {};
    const wasmDecoded: Record<string, string> = {};

    for (const moduleName of parsed.moduleOrder) {
        const info = parsed.modulesInfo[moduleName]!;
        const target = moduleName.startsWith(WASM_MODULE_PREFIX) ? wasmDecoded : jsDecoded;

        for (const ident of Object.values(info.identifiers)) {
            const path = parsed.indentation[ident.name]?.indentation;
            if (path?.length) continue;

            const lines = getEntityLines(ident, info.identifiers, parsed.indentation);
            target[ident.name] = lines.join("\n");
        }
    }

    const sortedJs = Object.keys(jsDecoded).sort().map((n) => jsDecoded[n]).join("\n");
    const sortedWasm = Object.keys(wasmDecoded).sort().map((n) => wasmDecoded[n]).join("\n");

    const sections: string[] = [];
    if (sortedJs.length) sections.push(`${JS_BANNER}\n\n${sortedJs}`);
    if (sortedWasm.length) sections.push(`${WASM_BANNER}\n\n${sortedWasm}`);

    return `syntax = "proto2";\npackage ${pkg};\n\n/// WhatsApp Version: ${version}\n\n${sections.join("\n")}`;
}

/**
 * Writes the generated proto source to {@code outputPath}, creating parent
 * directories as needed.
 *
 * @param parsed - the parsed proto schema to emit.
 * @param version - the WhatsApp Web version string to embed in the header.
 * @param pkg - the proto package declaration to emit.
 * @param outputPath - the file path to write to.
 */
export async function writeProtoFile(
    parsed: ParsedProtos,
    version: string,
    pkg: string,
    outputPath: string,
): Promise<void> {
    await mkdir(dirname(outputPath), { recursive: true });
    await writeFile(outputPath, generateProtoSource(parsed, version, pkg));
}
