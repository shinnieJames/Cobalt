import type { Identifier, IndentationEntry, ModuleInfo, ParsedProtos } from "../types.js";
import { WasmMemory, parseWasmDataSegments } from "./memory.js";
import { scanDescriptors } from "./descriptors.js";
import { groupOneofs, parseEnumValues, parseFields } from "./fields.js";

/** Registers the {@code canonical -> parent} relationship for nested-type emission. */
function registerNesting(
    canonical: string,
    indentation: Record<string, IndentationEntry>,
): void {
    const parts = canonical.split("$");
    const parent = parts.slice(0, -1).join("$");
    indentation[canonical] = indentation[canonical] ?? {};
    indentation[canonical]!.indentation = parent;
    if (parent.length) {
        indentation[parent] = indentation[parent] ?? {};
        indentation[parent]!.members = indentation[parent]!.members ?? new Set<string>();
        indentation[parent]!.members!.add(canonical);
    }
}

function emptyResult(): ParsedProtos {
    return { modulesInfo: {}, indentation: {}, moduleOrder: [] };
}

/**
 * Extracts every {@code protobuf-c}-generated message and enum schema embedded
 * in {@code binary} and returns it in Cobalt's {@link ParsedProtos} shape.
 *
 * @param binary - the wasm module bytes (used only if {@code memorySnapshot}
 *                 is omitted; the wasm is parsed for its static data section).
 * @param moduleLabel - the synthetic module name to file the extracted
 *                      identifiers under (used as a key in
 *                      {@link ParsedProtos.modulesInfo}).
 * @param memorySnapshot - if supplied, a post-instantiation {@code HEAPU8} view
 *                         of the wasm. Used instead of the static data section.
 *                         Required for Emscripten pthread builds (e.g. the WA
 *                         Voip wasm) where data segments are passive.
 * @returns the extracted schema. Empty if no descriptors are found, so callers
 *          can merge unconditionally without first probing.
 */
export function parseProtobufCFromWasm(
    binary: Uint8Array,
    moduleLabel: string,
    memorySnapshot?: Uint8Array,
): ParsedProtos {
    let mem: WasmMemory;
    if (memorySnapshot && memorySnapshot.length > 0) {
        mem = new WasmMemory([{ address: 0, data: memorySnapshot }]);
    } else {
        try {
            mem = parseWasmDataSegments(binary);
        } catch (err) {
            const message = err instanceof Error ? err.message : String(err);
            console.warn(`[WARN] Skipping ${moduleLabel}: ${message}`);
            return emptyResult();
        }
    }

    const { messages, enums } = scanDescriptors(mem);
    if (messages.length === 0 && enums.length === 0) {
        return emptyResult();
    }

    const descriptors = new Map<number, { kind: "message" | "enum"; canonical: string }>();
    for (const m of messages) descriptors.set(m.address, { kind: "message", canonical: m.canonical });
    for (const e of enums) descriptors.set(e.address, { kind: "enum", canonical: e.canonical });

    const identifiers: Record<string, Identifier> = {};
    const indentation: Record<string, IndentationEntry> = {};

    for (const m of messages) {
        const fields = parseFields(mem, m.fieldsPtr, m.fieldCount, descriptors);
        identifiers[m.canonical] = { name: m.canonical, members: groupOneofs(fields) };
        registerNesting(m.canonical, indentation);
    }
    for (const e of enums) {
        identifiers[e.canonical] = {
            name: e.canonical,
            enumValues: parseEnumValues(mem, e.valuesPtr, e.valueCount),
        };
        registerNesting(e.canonical, indentation);
    }

    const info: ModuleInfo = { crossRefs: [], identifiers };
    return {
        modulesInfo: { [moduleLabel]: info },
        indentation,
        moduleOrder: [moduleLabel],
    };
}
