// Derived reverse-engineering index over a single WASM module.
//
// Everything here is computed from the raw bytes plus the structural
// WasmAnalysis, and is far too heavy to bake into the stored analysis.json, so
// it is built on demand and memoized (the catalog caches one instance per
// content hash). Facets are independently lazy: the cheap string table does not
// pay for the full instruction decode, and the instruction decode does not pay
// for the vtable memory scan.

import type { WasmAnalysis } from "../types/wasm.js";
import { BinaryReader } from "./wasm-binary-reader.js";
import { walkFunctionBody } from "./wasm-decoder.js";

/** A direct or indirect call edge target with the byte offset of the call site. */
export interface IndirectSite {
  typeIndex: number;
  tableIndex: number;
  at: number;
}

/** Call-graph facet: forward edges, reverse edges, and indirect call sites. */
export interface CallGraph {
  /** funcidx -> set of direct callees (via {@code call} and {@code ref.func}). */
  calls: Map<number, Set<number>>;
  /** funcidx -> set of direct callers. */
  callers: Map<number, Set<number>>;
  /** funcidx -> indirect ({@code call_indirect}) sites in its body. */
  indirect: Map<number, IndirectSite[]>;
}

/** String-reference facet: the symbol-recovery cross-reference. */
export interface StringRefs {
  /** funcidx -> distinct data strings its body loads via {@code i32.const}. */
  byFunction: Map<number, string[]>;
  /** data string -> funcidxs whose bodies load its address. */
  byString: Map<string, number[]>;
}

interface MemorySegment {
  base: number;
  bytes: Uint8Array;
}

interface StringEntry {
  start: number;
  str: string;
}

function isPrintable(b: number): boolean {
  return b >= 0x20 && b < 0x7f;
}

const ASCII_DECODER = new TextDecoder("latin1");

export class WasmIndex {
  private readonly bytes: Uint8Array;
  private readonly analysis: WasmAnalysis;
  private readonly funcTypeIndex = new Map<number, number>();
  private readonly funcName = new Map<number, string>();
  private readonly reader: BinaryReader;

  private _segments?: MemorySegment[];
  private _stringEntries?: StringEntry[];
  private _slotMap?: Map<number, Map<number, number>>;
  private _tableFuncs?: Map<number, Set<number>>;
  private _functionsByType?: Map<number, number[]>;
  private _callGraph?: CallGraph;
  private _stringRefs?: StringRefs;

  constructor(binary: Buffer | Uint8Array, analysis: WasmAnalysis) {
    this.bytes = binary instanceof Buffer ? new Uint8Array(binary.buffer, binary.byteOffset, binary.byteLength) : binary;
    this.analysis = analysis;
    this.reader = new BinaryReader(this.bytes);

    let funcIdx = 0;
    for (const imp of analysis.imports) {
      if (imp.kind === "function") {
        this.funcTypeIndex.set(funcIdx, imp.typeIndex ?? -1);
        funcIdx++;
      }
    }
    for (const fn of analysis.functions) {
      this.funcTypeIndex.set(fn.index, fn.typeIndex);
      if (fn.name) this.funcName.set(fn.index, fn.name);
    }
  }

  /** Returns the recorded name for a function index, or {@code undefined}. */
  nameOf(funcIdx: number): string | undefined {
    return this.funcName.get(funcIdx);
  }

  // --- Table / element facet ---------------------------------------------

  /** Map of {@code tableIndex -> (slot -> funcidx)} from active element segments. */
  slotMap(): Map<number, Map<number, number>> {
    if (this._slotMap) return this._slotMap;
    const map = new Map<number, Map<number, number>>();
    const tableFuncs = new Map<number, Set<number>>();
    for (const seg of this.analysis.elements ?? []) {
      if (seg.mode !== "active" || seg.offset == null) continue;
      let table = map.get(seg.tableIndex);
      if (!table) {
        table = new Map<number, number>();
        map.set(seg.tableIndex, table);
      }
      let funcs = tableFuncs.get(seg.tableIndex);
      if (!funcs) {
        funcs = new Set<number>();
        tableFuncs.set(seg.tableIndex, funcs);
      }
      for (let k = 0; k < seg.funcIndices.length; k++) {
        const fn = seg.funcIndices[k];
        if (fn < 0) continue; // ref.null hole
        table.set(seg.offset + k, fn);
        funcs.add(fn);
      }
    }
    this._slotMap = map;
    this._tableFuncs = tableFuncs;
    return map;
  }

  /** Resolves a table slot to the function index installed there, or {@code -1}. */
  funcAtSlot(slot: number, tableIndex = 0): number {
    return this.slotMap().get(tableIndex)?.get(slot) ?? -1;
  }

  private tableFuncs(): Map<number, Set<number>> {
    if (!this._tableFuncs) this.slotMap();
    return this._tableFuncs!;
  }

  private functionsByType(): Map<number, number[]> {
    if (this._functionsByType) return this._functionsByType;
    const map = new Map<number, number[]>();
    for (const [fn, ty] of this.funcTypeIndex) {
      const list = map.get(ty);
      if (list) list.push(fn);
      else map.set(ty, [fn]);
    }
    this._functionsByType = map;
    return map;
  }

  /**
   * Resolves the candidate callees of a {@code call_indirect} site: functions
   * installed in the given table whose signature matches the site's type index.
   * This narrows the (often large) table to the handful of type-compatible
   * targets.
   */
  resolveIndirect(typeIndex: number, tableIndex = 0): number[] {
    const inTable = this.tableFuncs().get(tableIndex);
    if (!inTable) return [];
    const out: number[] = [];
    for (const fn of inTable) {
      if (this.funcTypeIndex.get(fn) === typeIndex) out.push(fn);
    }
    return out;
  }

  // --- Memory image / data string facet ----------------------------------

  private segments(): MemorySegment[] {
    if (this._segments) return this._segments;
    const dataSegs = this.analysis.dataSegments ?? [];
    const hasPassive = dataSegs.some((s) => s.mode === "passive");
    const passiveBases = hasPassive ? this.passiveBases() : new Map<number, number>();

    const segs: MemorySegment[] = [];
    for (let i = 0; i < dataSegs.length; i++) {
      const seg = dataSegs[i];
      let base: number | null = null;
      if (seg.mode === "active") base = seg.offset;
      else base = passiveBases.get(i) ?? null; // passive: resolved from memory.init
      if (base == null) continue;
      segs.push({ base, bytes: this.bytes.subarray(seg.fileOffset, seg.fileOffset + seg.byteLength) });
    }
    segs.sort((a, b) => a.base - b.base);
    this._segments = segs;
    return segs;
  }

  private _passiveBases?: Map<number, number>;

  /**
   * Resolves the linear-memory base of each passive data segment by scanning
   * function bodies for the {@code memory.init dataidx} that places it (with a
   * literal {@code i32.const} destination). This recovers static data for
   * Emscripten pthread/shared-memory builds, whose segments are all passive.
   */
  private passiveBases(): Map<number, number> {
    if (this._passiveBases) return this._passiveBases;
    const map = new Map<number, number>();
    for (const fn of this.analysis.functions) {
      walkFunctionBody(this.reader, fn.bodyOffset, fn.bodySize, {
        onMemoryInit: (dataIndex, destAddr) => {
          if (destAddr != null && !map.has(dataIndex)) map.set(dataIndex, destAddr);
        },
      });
    }
    this._passiveBases = map;
    return map;
  }

  private stringEntries(): StringEntry[] {
    if (this._stringEntries) return this._stringEntries;
    const entries: StringEntry[] = [];
    for (const seg of this.segments()) {
      const { base, bytes } = seg;
      let i = 0;
      while (i < bytes.length) {
        const start = i;
        while (i < bytes.length && isPrintable(bytes[i])) i++;
        const len = i - start;
        if (len >= 4) {
          entries.push({ start: base + start, str: ASCII_DECODER.decode(bytes.subarray(start, i)) });
        }
        while (i < bytes.length && !isPrintable(bytes[i])) i++;
      }
    }
    entries.sort((a, b) => a.start - b.start);
    this._stringEntries = entries;
    return entries;
  }

  /** Returns the data string at exactly {@code addr}, if any. */
  stringAt(addr: number): string | null {
    const entries = this.stringEntries();
    const i = this.floorIndex(entries, addr);
    if (i < 0) return null;
    return entries[i].start === addr ? entries[i].str : null;
  }

  /** Returns the data string whose byte range contains {@code addr}, if any. */
  stringContaining(addr: number): string | null {
    const entries = this.stringEntries();
    const i = this.floorIndex(entries, addr);
    if (i < 0) return null;
    const e = entries[i];
    return addr >= e.start && addr < e.start + e.str.length ? e.str : null;
  }

  /** Number of distinct extracted data strings. */
  stringCount(): number {
    return this.stringEntries().length;
  }

  private floorIndex(entries: StringEntry[], addr: number): number {
    let lo = 0;
    let hi = entries.length - 1;
    let ans = -1;
    while (lo <= hi) {
      const mid = (lo + hi) >>> 1;
      if (entries[mid].start <= addr) {
        ans = mid;
        lo = mid + 1;
      } else {
        hi = mid - 1;
      }
    }
    return ans;
  }

  /** Reads {@code len} bytes of linear memory at {@code addr} from active segments. */
  readMemory(addr: number, len: number): Uint8Array | null {
    for (const seg of this.segments()) {
      if (addr >= seg.base && addr + len <= seg.base + seg.bytes.length) {
        const off = addr - seg.base;
        return seg.bytes.subarray(off, off + len);
      }
    }
    return null;
  }

  /** Reads a little-endian 32-bit word at {@code addr}, or {@code null} if unmapped. */
  readU32(addr: number): number | null {
    const b = this.readMemory(addr, 4);
    if (!b) return null;
    return (b[0] | (b[1] << 8) | (b[2] << 16) | (b[3] << 24)) >>> 0;
  }

  /** Linear-memory addresses whose 4-byte LE word equals {@code value}. */
  findWordsEqualTo(value: number): number[] {
    const target = value >>> 0;
    const out: number[] = [];
    for (const seg of this.segments()) {
      const { base, bytes } = seg;
      for (let i = 0; i + 4 <= bytes.length; i++) {
        const word = (bytes[i] | (bytes[i + 1] << 8) | (bytes[i + 2] << 16) | (bytes[i + 3] << 24)) >>> 0;
        if (word === target) out.push(base + i);
      }
    }
    return out;
  }

  // --- Code scan facet (call graph + string refs) ------------------------

  private codeScan(): void {
    if (this._callGraph && this._stringRefs) return;

    const calls = new Map<number, Set<number>>();
    const callers = new Map<number, Set<number>>();
    const indirect = new Map<number, IndirectSite[]>();
    const refByFunction = new Map<number, string[]>();
    const refByString = new Map<string, number[]>();

    const addEdge = (from: number, to: number): void => {
      let s = calls.get(from);
      if (!s) calls.set(from, (s = new Set<number>()));
      s.add(to);
      let c = callers.get(to);
      if (!c) callers.set(to, (c = new Set<number>()));
      c.add(from);
    };

    for (const fn of this.analysis.functions) {
      const seen = new Set<string>(); // de-dup strings per function
      walkFunctionBody(this.reader, fn.bodyOffset, fn.bodySize, {
        onCall: (callee) => addEdge(fn.index, callee),
        onRefFunc: (callee) => addEdge(fn.index, callee),
        onCallIndirect: (typeIndex, tableIndex, at) => {
          let sites = indirect.get(fn.index);
          if (!sites) indirect.set(fn.index, (sites = []));
          sites.push({ typeIndex, tableIndex, at });
        },
        onI32Const: (value) => {
          const str = this.stringContaining(value);
          if (str == null || seen.has(str)) return;
          seen.add(str);
          let fns = refByFunction.get(fn.index);
          if (!fns) refByFunction.set(fn.index, (fns = []));
          fns.push(str);
          let owners = refByString.get(str);
          if (!owners) refByString.set(str, (owners = []));
          owners.push(fn.index);
        },
      });
    }

    this._callGraph = { calls, callers, indirect };
    this._stringRefs = { byFunction: refByFunction, byString: refByString };
  }

  /** The call-graph facet (built on first access). */
  callGraph(): CallGraph {
    this.codeScan();
    return this._callGraph!;
  }

  /** The string-reference facet (built on first access). */
  stringRefs(): StringRefs {
    this.codeScan();
    return this._stringRefs!;
  }

  /** Function indices whose bodies load the address of an exact data string. */
  functionsReferencing(str: string): number[] {
    return [...(this.stringRefs().byString.get(str) ?? [])].sort((a, b) => a - b);
  }

  /** Function indices whose bodies load the address of any string containing {@code needle}. */
  functionsReferencingSubstring(needle: string): number[] {
    const hits = new Set<number>();
    for (const [str, fns] of this.stringRefs().byString) {
      if (str.includes(needle)) for (const fn of fns) hits.add(fn);
    }
    return [...hits].sort((a, b) => a - b);
  }

  /** Extracted data strings matching {@code test}, up to {@code max}, with their addresses. */
  matchStrings(test: (s: string) => boolean, max: number): Array<{ address: number; value: string }> {
    const out: Array<{ address: number; value: string }> = [];
    for (const e of this.stringEntries()) {
      if (out.length >= max) break;
      if (test(e.str)) out.push({ address: e.start, value: e.str });
    }
    return out;
  }

  /**
   * Function indices whose bodies contain an {@code i32.const} equal to
   * {@code value}. Runs a fresh full-body decode (not memoized) because the
   * standing index only retains constants that resolve to a data string; this
   * is the gated, explicit path for locating an arbitrary magic number.
   */
  functionsWithI32Const(value: number): number[] {
    const hits: number[] = [];
    for (const fn of this.analysis.functions) {
      let found = false;
      walkFunctionBody(this.reader, fn.bodyOffset, fn.bodySize, {
        onI32Const: (v) => {
          if (v === value) found = true;
        },
      });
      if (found) hits.push(fn.index);
    }
    return hits;
  }
}

/** Builds a fresh index. The catalog memoizes the result per content hash. */
export function buildWasmIndex(binary: Buffer | Uint8Array, analysis: WasmAnalysis): WasmIndex {
  return new WasmIndex(binary, analysis);
}
