import wabtInit from "wabt";
import type {
    WasmAnalysis,
    WasmCustomSection,
    WasmExportEntry,
    WasmFunctionEntry,
    WasmGlobalEntry,
    WasmImportEntry,
    WasmMemoryEntry,
    WasmSectionSizes,
    WasmTableEntry,
    WasmTypeEntry,
} from "../types/wasm.js";

let wabtInstance: Awaited<ReturnType<typeof wabtInit>> | null = null;

async function getWabt() {
  if (!wabtInstance) {
    wabtInstance = await wabtInit();
  }
  return wabtInstance;
}

const WASM_MAGIC = 0x6d736100;
const WASM_VERSION = 1;

const SECTION = {
  CUSTOM: 0,
  TYPE: 1,
  IMPORT: 2,
  FUNCTION: 3,
  TABLE: 4,
  MEMORY: 5,
  GLOBAL: 6,
  EXPORT: 7,
  START: 8,
  ELEMENT: 9,
  CODE: 10,
  DATA: 11,
  DATA_COUNT: 12,
} as const;

const VAL_TYPE_NAMES: Record<number, string> = {
  0x7f: "i32",
  0x7e: "i64",
  0x7d: "f32",
  0x7c: "f64",
  0x7b: "v128",
  0x70: "funcref",
  0x6f: "externref",
};

const EXTERNAL_KIND: Record<number, WasmExportEntry["kind"]> = {
  0: "function",
  1: "table",
  2: "memory",
  3: "global",
};

class BinaryReader {
  public pos = 0;
  private view: DataView;
  private bytes: Uint8Array;

  constructor(buffer: Buffer | Uint8Array) {
    this.bytes =
      buffer instanceof Buffer ? new Uint8Array(buffer.buffer, buffer.byteOffset, buffer.byteLength) : buffer;
    this.view = new DataView(this.bytes.buffer, this.bytes.byteOffset, this.bytes.byteLength);
  }

  get length(): number {
    return this.bytes.length;
  }

  readByte(): number {
    return this.bytes[this.pos++];
  }

  readU32Leb(): number {
    let result = 0;
    let shift = 0;
    let byte: number;
    do {
      byte = this.bytes[this.pos++];
      result |= (byte & 0x7f) << shift;
      shift += 7;
    } while (byte & 0x80);
    return result >>> 0;
  }

  readI32Leb(): number {
    let result = 0;
    let shift = 0;
    let byte: number;
    do {
      byte = this.bytes[this.pos++];
      result |= (byte & 0x7f) << shift;
      shift += 7;
    } while (byte & 0x80);
    if (shift < 32 && byte & 0x40) result |= -(1 << shift);
    return result;
  }

  readI64Leb(): bigint {
    let result = 0n;
    let shift = 0n;
    let byte: number;
    do {
      byte = this.bytes[this.pos++];
      result |= BigInt(byte & 0x7f) << shift;
      shift += 7n;
    } while (byte & 0x80);
    if (shift < 64n && byte & 0x40) result |= -(1n << shift);
    return result;
  }

  readF32(): number {
    const val = this.view.getFloat32(this.pos, true);
    this.pos += 4;
    return val;
  }

  readF64(): number {
    const val = this.view.getFloat64(this.pos, true);
    this.pos += 8;
    return val;
  }

  readU32Fixed(): number {
    const val = this.view.getUint32(this.pos, true);
    this.pos += 4;
    return val;
  }

  readName(): string {
    const len = this.readU32Leb();
    const bytes = this.bytes.slice(this.pos, this.pos + len);
    this.pos += len;
    return new TextDecoder().decode(bytes);
  }

  readValType(): string {
    const byte = this.readByte();
    return VAL_TYPE_NAMES[byte] ?? `unknown(0x${byte.toString(16)})`;
  }

  readVec<T>(reader: () => T): T[] {
    const count = this.readU32Leb();
    const result: T[] = [];
    for (let i = 0; i < count; i++) result.push(reader());
    return result;
  }

  skip(n: number): void {
    this.pos += n;
  }
}

interface RawSection {
  id: number;
  offset: number;
  size: number;
  contentOffset: number;
}

function readSections(r: BinaryReader): RawSection[] {
  const magic = r.readU32Fixed();
  if (magic !== WASM_MAGIC) throw new Error("Invalid WASM magic number");
  const version = r.readU32Fixed();
  if (version !== WASM_VERSION) throw new Error(`Unsupported WASM version: ${version}`);

  const sections: RawSection[] = [];
  while (r.pos < r.length) {
    const id = r.readByte();
    const offset = r.pos - 1;
    const size = r.readU32Leb();
    const contentOffset = r.pos;
    sections.push({ id, offset, size, contentOffset });
    r.pos = contentOffset + size;
  }
  return sections;
}

function readFuncType(r: BinaryReader): WasmTypeEntry {
  const tag = r.readByte();
  if (tag !== 0x60) throw new Error(`Expected functype tag 0x60, got 0x${tag.toString(16)}`);
  const params = r.readVec(() => r.readValType());
  const results = r.readVec(() => r.readValType());
  return { params, results };
}

function readLimits(r: BinaryReader): { initial: number; maximum?: number } {
  const flags = r.readByte();
  const initial = r.readU32Leb();
  const maximum = flags & 0x01 ? r.readU32Leb() : undefined;
  return { initial, maximum };
}

function readGlobalType(r: BinaryReader): { valueType: string; mutable: boolean } {
  const valueType = r.readValType();
  const mutable = r.readByte() === 0x01;
  return { valueType, mutable };
}

function skipInitExpr(r: BinaryReader): void {
  while (r.pos < r.length) {
    const opcode = r.readByte();
    if (opcode === 0x0b) return;
    switch (opcode) {
      case 0x41: r.readI32Leb(); break;
      case 0x42: r.readI64Leb(); break;
      case 0x43: r.readF32(); break;
      case 0x44: r.readF64(); break;
      case 0x23: r.readU32Leb(); break;
      case 0xd0: r.readByte(); break;
      case 0xd2: r.readU32Leb(); break;
      default: break;
    }
  }
}

function parseTypeSection(r: BinaryReader, section: RawSection): WasmTypeEntry[] {
  r.pos = section.contentOffset;
  return r.readVec(() => readFuncType(r));
}

function parseImportSection(
  r: BinaryReader,
  section: RawSection,
  types: WasmTypeEntry[]
): WasmImportEntry[] {
  r.pos = section.contentOffset;
  return r.readVec(() => {
    const module = r.readName();
    const name = r.readName();
    const kind = r.readByte();

    const entry: WasmImportEntry = {
      module,
      name,
      kind: EXTERNAL_KIND[kind] ?? "function",
    };

    switch (kind) {
      case 0: {
        const typeIndex = r.readU32Leb();
        entry.typeIndex = typeIndex;
        entry.type = types[typeIndex];
        break;
      }
      case 1: {
        const elementType = r.readValType();
        const limits = readLimits(r);
        entry.tableType = { elementType, ...limits };
        break;
      }
      case 2: {
          entry.memoryType = readLimits(r);
        break;
      }
      case 3: {
          entry.globalType = readGlobalType(r);
        break;
      }
    }

    return entry;
  });
}

function parseFunctionSection(r: BinaryReader, section: RawSection): number[] {
  r.pos = section.contentOffset;
  return r.readVec(() => r.readU32Leb());
}

function parseTableSection(r: BinaryReader, section: RawSection): WasmTableEntry[] {
  r.pos = section.contentOffset;
  return r.readVec(() => {
    const elementType = r.readValType();
    const limits = readLimits(r);
    return { elementType, ...limits };
  });
}

function parseMemorySection(r: BinaryReader, section: RawSection): WasmMemoryEntry[] {
  r.pos = section.contentOffset;
  return r.readVec(() => readLimits(r));
}

function parseGlobalSection(r: BinaryReader, section: RawSection): WasmGlobalEntry[] {
  r.pos = section.contentOffset;
  const count = r.readU32Leb();
  const globals: WasmGlobalEntry[] = [];
  for (let i = 0; i < count; i++) {
    const { valueType, mutable } = readGlobalType(r);
    skipInitExpr(r);
    globals.push({ index: i, valueType, mutable });
  }
  return globals;
}

function parseExportSection(r: BinaryReader, section: RawSection): WasmExportEntry[] {
  r.pos = section.contentOffset;
  return r.readVec(() => {
    const name = r.readName();
    const kindByte = r.readByte();
    const index = r.readU32Leb();
    return {
      name,
      kind: EXTERNAL_KIND[kindByte] ?? "function",
      index,
    };
  });
}

function parseStartSection(r: BinaryReader, section: RawSection): number {
  r.pos = section.contentOffset;
  return r.readU32Leb();
}

function parseCodeSection(
  r: BinaryReader,
  section: RawSection,
  typeIndices: number[],
  types: WasmTypeEntry[],
  importedFuncCount: number,
  functionNames: Map<number, string>
): WasmFunctionEntry[] {
  r.pos = section.contentOffset;
  const count = r.readU32Leb();
  const functions: WasmFunctionEntry[] = [];

  for (let i = 0; i < count; i++) {
    const bodySize = r.readU32Leb();
    const bodyOffset = r.pos;
    const funcIndex = importedFuncCount + i;
    const typeIndex = typeIndices[i] ?? -1;
    const funcType = types[typeIndex] ?? { params: [], results: [] };

    const localDecls: string[] = [];
    const localDeclCount = r.readU32Leb();
    for (let j = 0; j < localDeclCount; j++) {
      const localCount = r.readU32Leb();
      const localType = r.readValType();
      for (let k = 0; k < localCount; k++) localDecls.push(localType);
    }

    functions.push({
      index: funcIndex,
      name: functionNames.get(funcIndex),
      typeIndex,
      type: funcType,
      bodyOffset,
      bodySize,
      localDecls,
    });

    r.pos = bodyOffset + bodySize;
  }

  return functions;
}

function parseNameSection(
  r: BinaryReader,
  section: RawSection
): Map<number, string> {
  const names = new Map<number, string>();
  r.pos = section.contentOffset;
  const customName = r.readName();
  if (customName !== "name") return names;

  const end = section.contentOffset + section.size;
  while (r.pos < end) {
    const subsectionId = r.readByte();
    const subsectionSize = r.readU32Leb();
    const subsectionEnd = r.pos + subsectionSize;

    if (subsectionId === 1) {
      const count = r.readU32Leb();
      for (let i = 0; i < count; i++) {
        const index = r.readU32Leb();
        const name = r.readName();
        names.set(index, name);
      }
    }

    r.pos = subsectionEnd;
  }

  return names;
}

function resolveExportTypes(
  exports: WasmExportEntry[],
  functions: WasmFunctionEntry[],
  importedFuncCount: number,
  types: WasmTypeEntry[],
  imports: WasmImportEntry[]
): void {
  for (const exp of exports) {
    if (exp.kind !== "function") continue;
    if (exp.index < importedFuncCount) {
      exp.type = imports[exp.index]?.type;
    } else {
      const localIdx = exp.index - importedFuncCount;
      exp.type = functions[localIdx]?.type;
    }
  }
}

export function analyzeWasm(name: string, binary: Buffer | Uint8Array): WasmAnalysis {
  const r = new BinaryReader(binary);
  const rawSections = readSections(r);

  let types: WasmTypeEntry[] = [];
  let imports: WasmImportEntry[] = [];
  let typeIndices: number[] = [];
  let tables: WasmTableEntry[] = [];
  let memories: WasmMemoryEntry[] = [];
  let globals: WasmGlobalEntry[] = [];
  let exports: WasmExportEntry[] = [];
  let functions: WasmFunctionEntry[] = [];
  let startFunction: number | undefined;
  const customSections: WasmCustomSection[] = [];
  let functionNames = new Map<number, string>();

  const sectionSizes: WasmSectionSizes = {
    type: 0, import: 0, function: 0, table: 0, memory: 0,
    global: 0, export: 0, start: 0, element: 0, code: 0,
    data: 0, dataCount: 0, custom: {},
  };

  const SECTION_SIZE_KEY: Record<number, keyof Omit<WasmSectionSizes, "custom">> = {
    [SECTION.TYPE]: "type",
    [SECTION.IMPORT]: "import",
    [SECTION.FUNCTION]: "function",
    [SECTION.TABLE]: "table",
    [SECTION.MEMORY]: "memory",
    [SECTION.GLOBAL]: "global",
    [SECTION.EXPORT]: "export",
    [SECTION.START]: "start",
    [SECTION.ELEMENT]: "element",
    [SECTION.CODE]: "code",
    [SECTION.DATA]: "data",
    [SECTION.DATA_COUNT]: "dataCount",
  };

  for (const section of rawSections) {
    const key = SECTION_SIZE_KEY[section.id];
    if (key) {
      sectionSizes[key] = section.size;
    } else if (section.id === SECTION.CUSTOM) {
      const savedPos = r.pos;
      r.pos = section.contentOffset;
      const customName = r.readName();
      r.pos = savedPos;
      sectionSizes.custom[customName] = (sectionSizes.custom[customName] ?? 0) + section.size;
      customSections.push({ name: customName, offset: section.offset, size: section.size });
    }
  }

  for (const section of rawSections) {
    if (section.id === SECTION.CUSTOM) {
      const savedPos = r.pos;
      r.pos = section.contentOffset;
      const peekName = r.readName();
      r.pos = savedPos;
      if (peekName === "name") {
        functionNames = parseNameSection(r, section);
      }
    }
  }

  for (const section of rawSections) {
    switch (section.id) {
      case SECTION.TYPE:
        types = parseTypeSection(r, section);
        break;
      case SECTION.IMPORT:
        imports = parseImportSection(r, section, types);
        break;
      case SECTION.FUNCTION:
        typeIndices = parseFunctionSection(r, section);
        break;
      case SECTION.TABLE:
        tables = parseTableSection(r, section);
        break;
      case SECTION.MEMORY:
        memories = parseMemorySection(r, section);
        break;
      case SECTION.GLOBAL:
        globals = parseGlobalSection(r, section);
        break;
      case SECTION.EXPORT:
        exports = parseExportSection(r, section);
        break;
      case SECTION.START:
        startFunction = parseStartSection(r, section);
        break;
    }
  }

  const importedFuncCount = imports.filter((i) => i.kind === "function").length;

  for (const section of rawSections) {
    if (section.id === SECTION.CODE) {
      functions = parseCodeSection(r, section, typeIndices, types, importedFuncCount, functionNames);
      break;
    }
  }

  resolveExportTypes(exports, functions, importedFuncCount, types, imports);

  for (const imp of imports) {
    if (imp.kind === "memory" && imp.memoryType) {
      memories.push(imp.memoryType);
    }
    if (imp.kind === "table" && imp.tableType) {
      tables.push(imp.tableType);
    }
  }

  return {
    name,
    types,
    imports,
    exports,
    functions,
    memories,
    tables,
    globals,
    startFunction,
    customSections,
    sectionSizes,
    totalSize: binary.length,
  };
}

function toFreshUint8Array(binary: Buffer | Uint8Array): Uint8Array {
  const copy = new Uint8Array(binary.length);
  copy.set(binary);
  return copy;
}

export async function disassembleWasm(binary: Buffer | Uint8Array): Promise<string> {
  const wabt = await getWabt();
  const mod = wabt.readWasm(toFreshUint8Array(binary), { readDebugNames: true });
  try {
    mod.generateNames();
    mod.applyNames();
    return mod.toText({ foldExprs: false, inlineExport: false });
  } finally {
    mod.destroy();
  }
}

export async function disassembleWasmFunction(
  binary: Buffer | Uint8Array,
  functionIndex: number
): Promise<string | null> {
  const wat = await disassembleWasm(binary);
  const lines = wat.split("\n");

  let currentFuncIndex = -1;
  let depth = 0;
  let funcStart = -1;
  let funcEnd = -1;
  let inFunc = false;

  for (let i = 0; i < lines.length; i++) {
    const trimmed = lines[i].trimStart();

    if (trimmed.startsWith("(func ")) {
      currentFuncIndex++;
      if (currentFuncIndex === functionIndex) {
        funcStart = i;
        inFunc = true;
        depth = 0;
      }
    }

    if (inFunc) {
      for (const ch of trimmed) {
        if (ch === "(") depth++;
        if (ch === ")") depth--;
      }
      if (depth <= 0 && funcStart !== -1) {
        funcEnd = i;
        break;
      }
    }
  }

  if (funcStart === -1 || funcEnd === -1) return null;
  return lines.slice(funcStart, funcEnd + 1).join("\n");
}
