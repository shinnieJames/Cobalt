export interface WasmTypeEntry {
  params: string[];
  results: string[];
}

export interface WasmImportEntry {
  module: string;
  name: string;
  kind: "function" | "table" | "memory" | "global";
  typeIndex?: number;
  type?: WasmTypeEntry;
  tableType?: { elementType: string; initial: number; maximum?: number };
  memoryType?: { initial: number; maximum?: number };
  globalType?: { valueType: string; mutable: boolean };
}

export interface WasmExportEntry {
  name: string;
  kind: "function" | "table" | "memory" | "global";
  index: number;
  type?: WasmTypeEntry;
}

export interface WasmFunctionEntry {
  index: number;
  name?: string;
  typeIndex: number;
  type: WasmTypeEntry;
  bodyOffset: number;
  bodySize: number;
  localDecls: string[];
}

export interface WasmMemoryEntry {
  initial: number;
  maximum?: number;
}

export interface WasmTableEntry {
  elementType: string;
  initial: number;
  maximum?: number;
}

export interface WasmGlobalEntry {
  index: number;
  valueType: string;
  mutable: boolean;
  name?: string;
}

export interface WasmCustomSection {
  name: string;
  offset: number;
  size: number;
}

export interface WasmElementSegment {
  /** Active segments populate a table at instantiation; passive/declared do not. */
  mode: "active" | "passive" | "declared";
  /** Target table index (0 unless the segment names one explicitly). */
  tableIndex: number;
  /**
   * Resolved i32 base slot for active segments, i.e. the table index of the
   * first item. {@code null} when the offset expression is not a constant
   * (e.g. depends on an imported global) or the segment is not active.
   */
  offset: number | null;
  /**
   * Function indices the segment installs, in order. The k-th entry lands at
   * table slot {@code offset + k} for active segments. A {@code -1} entry marks
   * a {@code ref.null} hole in an expression-element segment.
   */
  funcIndices: number[];
}

export interface WasmDataSegment {
  /** Active segments copy into linear memory at instantiation; passive do not. */
  mode: "active" | "passive";
  /** Target memory index (0 unless the segment names one explicitly). */
  memoryIndex: number;
  /**
   * Resolved i32 base address in linear memory for active segments. {@code null}
   * when the offset expression is non-constant or the segment is passive.
   */
  offset: number | null;
  /** Number of bytes the segment carries. */
  byteLength: number;
  /**
   * Absolute offset of the segment's bytes within the {@code .wasm} file, so the
   * raw bytes can be fetched on demand without inlining them into the analysis.
   */
  fileOffset: number;
}

export interface WasmSectionSizes {
  type: number;
  import: number;
  function: number;
  table: number;
  memory: number;
  global: number;
  export: number;
  start: number;
  element: number;
  code: number;
  data: number;
  dataCount: number;
  custom: Record<string, number>;
}

export interface WasmCrossReference {
  loaderModules: string[];
  referencingModules: string[];
}

export interface WasmAnalysis {
  name: string;
  types: WasmTypeEntry[];
  imports: WasmImportEntry[];
  exports: WasmExportEntry[];
  functions: WasmFunctionEntry[];
  memories: WasmMemoryEntry[];
  tables: WasmTableEntry[];
  globals: WasmGlobalEntry[];
  startFunction?: number;
  customSections: WasmCustomSection[];
  sectionSizes: WasmSectionSizes;
  totalSize: number;
  /**
   * Element segments (table initializers). Optional so analyses produced before
   * element parsing existed still deserialize; backfilled lazily when absent.
   */
  elements?: WasmElementSegment[];
  /**
   * Data segment descriptors (no bytes; fetch via {@code fileOffset}). Optional
   * for the same backward-compatibility reason as {@link elements}.
   */
  dataSegments?: WasmDataSegment[];
}
