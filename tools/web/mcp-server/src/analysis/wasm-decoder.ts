// Faithful WebAssembly instruction decoder.
//
// Unlike the old init-expression byte-scan, this decodes (or precisely skips)
// the immediates of every opcode so the cursor stays exactly aligned with the
// bytecode. That alignment is the whole point: a call graph or const xref built
// on a desynced cursor is silently wrong. Coverage is MVP + reference-types +
// tail-call + bulk-memory/sat-trunc (0xFC) + SIMD (0xFD) + threads/atomics
// (0xFE). Anything outside that set (GC 0xFB, the exception-handling proposal,
// unknown bytes) is reported as `partial` rather than guessed at.
//
// References: WebAssembly core binary format, section "Instructions"
// (https://webassembly.github.io/spec/core/binary/instructions.html) and
// WABT's src/opcode.def.

import { BinaryReader } from "./wasm-binary-reader.js";

/** Immediate-operand layout of an opcode. Drives how many bytes to consume. */
const enum Imm {
  UNKNOWN = 0,
  NONE,
  U32,
  U32X2,
  S32,
  S64,
  F32,
  F64,
  BLOCKTYPE,
  MEMARG,
  MEMARG_LANE,
  BR_TABLE,
  SELECT_T,
  V128_CONST,
  SHUFFLE,
  LANE,
  REFTYPE,
  BYTE1,
}

// Single-byte opcode -> immediate shape. Index by opcode value; UNKNOWN (0)
// marks "not in our coverage set" so the walker degrades to `partial`.
const SHAPE = new Uint8Array(256).fill(Imm.UNKNOWN);

function setRange(lo: number, hi: number, shape: Imm): void {
  for (let op = lo; op <= hi; op++) SHAPE[op] = shape;
}

// Control.
SHAPE[0x00] = Imm.NONE; // unreachable
SHAPE[0x01] = Imm.NONE; // nop
SHAPE[0x02] = Imm.BLOCKTYPE; // block
SHAPE[0x03] = Imm.BLOCKTYPE; // loop
SHAPE[0x04] = Imm.BLOCKTYPE; // if
SHAPE[0x05] = Imm.NONE; // else (also handled explicitly for depth)
SHAPE[0x0b] = Imm.NONE; // end (handled explicitly for depth)
SHAPE[0x0c] = Imm.U32; // br
SHAPE[0x0d] = Imm.U32; // br_if
SHAPE[0x0e] = Imm.BR_TABLE; // br_table
SHAPE[0x0f] = Imm.NONE; // return
SHAPE[0x10] = Imm.U32; // call
SHAPE[0x11] = Imm.U32X2; // call_indirect (typeidx, tableidx)
SHAPE[0x12] = Imm.U32; // return_call
SHAPE[0x13] = Imm.U32X2; // return_call_indirect

// Parametric.
SHAPE[0x1a] = Imm.NONE; // drop
SHAPE[0x1b] = Imm.NONE; // select
SHAPE[0x1c] = Imm.SELECT_T; // select t*

// Variable / table.
setRange(0x20, 0x24, Imm.U32); // local.get/set/tee, global.get/set
SHAPE[0x25] = Imm.U32; // table.get
SHAPE[0x26] = Imm.U32; // table.set

// Memory loads/stores all carry a memarg.
setRange(0x28, 0x3e, Imm.MEMARG);
SHAPE[0x3f] = Imm.U32; // memory.size (reserved memidx)
SHAPE[0x40] = Imm.U32; // memory.grow (reserved memidx)

// Constants.
SHAPE[0x41] = Imm.S32; // i32.const
SHAPE[0x42] = Imm.S64; // i64.const
SHAPE[0x43] = Imm.F32; // f32.const
SHAPE[0x44] = Imm.F64; // f64.const

// Numeric + comparison + conversion + sign-extension: no immediates.
setRange(0x45, 0xc4, Imm.NONE);

// Reference types.
SHAPE[0xd0] = Imm.REFTYPE; // ref.null
SHAPE[0xd1] = Imm.NONE; // ref.is_null
SHAPE[0xd2] = Imm.U32; // ref.func

/** Resolves the immediate shape of a prefixed (0xFC/0xFD/0xFE) instruction. */
function prefixShape(prefix: number, sub: number): Imm {
  if (prefix === 0xfc) {
    // bulk-memory + saturating truncation.
    if (sub <= 7) return Imm.NONE; // i32/i64.trunc_sat_f*
    switch (sub) {
      case 8: return Imm.U32X2; // memory.init (dataidx, memidx)
      case 9: return Imm.U32; // data.drop
      case 10: return Imm.U32X2; // memory.copy (memidx, memidx)
      case 11: return Imm.U32; // memory.fill
      case 12: return Imm.U32X2; // table.init (elemidx, tableidx)
      case 13: return Imm.U32; // elem.drop
      case 14: return Imm.U32X2; // table.copy (tableidx, tableidx)
      case 15: // table.grow
      case 16: // table.size
      case 17: return Imm.U32; // table.fill
      default: return Imm.UNKNOWN;
    }
  }
  if (prefix === 0xfd) {
    // SIMD / v128. Default is no immediate (arithmetic/compare/convert and the
    // relaxed-SIMD ops); only the memory, const, shuffle, and lane ops differ.
    if (sub <= 0x0b) return Imm.MEMARG; // v128.load* / v128.store
    if (sub === 0x0c) return Imm.V128_CONST;
    if (sub === 0x0d) return Imm.SHUFFLE; // i8x16.shuffle
    if (sub >= 0x15 && sub <= 0x22) return Imm.LANE; // extract_lane / replace_lane
    if (sub >= 0x54 && sub <= 0x5b) return Imm.MEMARG_LANE; // v128.load/store*_lane
    if (sub >= 0x5c && sub <= 0x5d) return Imm.MEMARG; // v128.load32/64_zero
    return Imm.NONE;
  }
  if (prefix === 0xfe) {
    // threads / atomics. fence is the only non-memarg form.
    if (sub === 0x03) return Imm.BYTE1; // atomic.fence
    return Imm.MEMARG;
  }
  return Imm.UNKNOWN;
}

/** Reads a memarg, honoring the multi-memory flag bit (0x40) in the alignment. */
function readMemarg(r: BinaryReader): { align: number; offset: number } {
  const alignFlags = r.readU32Leb();
  if (alignFlags & 0x40) r.readU32Leb(); // explicit memidx
  const offset = r.readU32Leb();
  return { align: alignFlags & ~0x40, offset };
}

/**
 * Per-instruction callbacks. All optional; the walker fires only those that are
 * present, so a caller that wants just call edges pays nothing for the rest.
 */
export interface InstrVisitor {
  onCall?(funcIdx: number, at: number): void;
  onCallIndirect?(typeIdx: number, tableIdx: number, at: number): void;
  onRefFunc?(funcIdx: number, at: number): void;
  onI32Const?(value: number, at: number): void;
  onMemAccess?(opcode: number, offset: number, align: number, at: number): void;
  /**
   * Fired for {@code memory.init}. {@code destAddr} is the destination linear
   * address when it was pushed as a literal {@code i32.const} immediately
   * before (the Emscripten pattern for placing a passive segment), else
   * {@code null}.
   */
  onMemoryInit?(dataIndex: number, destAddr: number | null, at: number): void;
  onBlockStart?(opcode: number, at: number): void;
  onBlockEnd?(at: number): void;
  onElse?(at: number): void;
}

/** Outcome of decoding one function body. */
export interface WalkResult {
  /** True if an opcode outside the coverage set was hit; decode stopped there. */
  partial: boolean;
  /** Byte offset at which decoding stopped (the unknown opcode) when partial. */
  stoppedAt?: number;
  /** Opcode that could not be decoded, when partial. */
  unknownOpcode?: number;
}

/**
 * Walks one function body, firing visitor callbacks. {@code bodyOffset} is the
 * start of the locals declaration (the position recorded as `bodyOffset` by the
 * code-section parser); {@code bodySize} is the declared body length. On a clean
 * decode the cursor lands exactly on {@code bodyOffset + bodySize}; on an
 * unknown opcode it stops, sets {@code partial}, and repositions to the body end
 * so the caller can continue to the next function.
 */
export function walkFunctionBody(
  r: BinaryReader,
  bodyOffset: number,
  bodySize: number,
  visitor: InstrVisitor
): WalkResult {
  const bodyEnd = bodyOffset + bodySize;
  r.pos = bodyOffset;

  // Local declarations: vec of (count, valtype).
  const localGroups = r.readU32Leb();
  for (let i = 0; i < localGroups; i++) {
    r.readU32Leb(); // count
    r.readByte(); // valtype
  }

  let depth = 0;
  // Rolling window of the most recent consecutive i32.const values, used to
  // recover the destination address of a memory.init (which Emscripten emits as
  // `i32.const dest; i32.const off; i32.const size; memory.init`).
  const constWindow: number[] = [];
  while (r.pos < bodyEnd) {
    const at = r.pos;
    const opcode = r.readByte();

    if (opcode === 0x0b) {
      // end: closes a structured block, or terminates the function at depth 0.
      if (depth === 0) {
        r.pos = bodyEnd;
        return { partial: false };
      }
      depth--;
      constWindow.length = 0;
      visitor.onBlockEnd?.(at);
      continue;
    }
    if (opcode === 0x05) {
      constWindow.length = 0;
      visitor.onElse?.(at); // else
      continue;
    }

    let sub = -1;
    let shape: Imm;
    if (opcode === 0xfc || opcode === 0xfd || opcode === 0xfe) {
      sub = r.readU32Leb();
      shape = prefixShape(opcode, sub);
    } else {
      shape = SHAPE[opcode] as Imm;
    }

    if (shape === Imm.UNKNOWN) {
      r.pos = bodyEnd;
      return { partial: true, stoppedAt: at, unknownOpcode: opcode };
    }

    let pushedConst: number | null = null;
    switch (shape) {
      case Imm.NONE:
        break;
      case Imm.U32: {
        const v = r.readU32Leb();
        if (opcode === 0x10) visitor.onCall?.(v, at);
        else if (opcode === 0x12) visitor.onCall?.(v, at); // return_call
        else if (opcode === 0xd2) visitor.onRefFunc?.(v, at);
        break;
      }
      case Imm.U32X2: {
        const a = r.readU32Leb();
        const b = r.readU32Leb();
        if (opcode === 0x11 || opcode === 0x13) visitor.onCallIndirect?.(a, b, at);
        else if (opcode === 0xfc && sub === 8) {
          const dest = constWindow.length >= 3 ? constWindow[constWindow.length - 3] : null;
          visitor.onMemoryInit?.(a, dest, at);
        }
        break;
      }
      case Imm.S32: {
        const v = r.readI32Leb();
        if (opcode === 0x41) {
          pushedConst = v;
          visitor.onI32Const?.(v, at);
        }
        break;
      }
      case Imm.S64:
        r.readI64Leb();
        break;
      case Imm.F32:
        r.skip(4);
        break;
      case Imm.F64:
        r.skip(8);
        break;
      case Imm.BLOCKTYPE:
        r.readS33();
        depth++;
        visitor.onBlockStart?.(opcode, at);
        break;
      case Imm.MEMARG: {
        const { align, offset } = readMemarg(r);
        visitor.onMemAccess?.(opcode, offset, align, at);
        break;
      }
      case Imm.MEMARG_LANE:
        readMemarg(r);
        r.skip(1); // lane index
        break;
      case Imm.BR_TABLE: {
        const n = r.readU32Leb();
        for (let i = 0; i < n; i++) r.readU32Leb();
        r.readU32Leb(); // default label
        break;
      }
      case Imm.SELECT_T: {
        const n = r.readU32Leb();
        r.skip(n); // valtype bytes
        break;
      }
      case Imm.V128_CONST:
      case Imm.SHUFFLE:
        r.skip(16);
        break;
      case Imm.LANE:
      case Imm.REFTYPE:
      case Imm.BYTE1:
        r.skip(1);
        break;
    }

    // Maintain the i32.const run: extend it on a const, break it otherwise.
    if (pushedConst !== null) {
      constWindow.push(pushedConst);
      if (constWindow.length > 3) constWindow.shift();
    } else {
      constWindow.length = 0;
    }
  }

  // Ran to the declared end without a balancing function-level `end`.
  return { partial: r.pos !== bodyEnd, stoppedAt: r.pos };
}

/** A resolved constant expression (global init, element/data offset, elem item). */
export type ConstExpr =
  | { kind: "i32"; value: number }
  | { kind: "i64"; value: bigint }
  | { kind: "f32" }
  | { kind: "f64" }
  | { kind: "global"; index: number }
  | { kind: "ref.func"; index: number }
  | { kind: "ref.null" }
  | { kind: "unknown" };

/**
 * Decodes a constant expression up to and including its terminating {@code end},
 * returning the value it produces. Replaces the old hand-rolled init-expression
 * skipper: it advances the cursor correctly for any opcode and additionally
 * resolves the common single-instruction forms (notably {@code i32.const} for
 * active data/element segment offsets and {@code ref.func} for element items).
 * For multi-instruction extended-const expressions the last recognized producer
 * wins; truly unrecognized shapes yield {@code unknown} but stay aligned.
 */
export function decodeConstExpr(r: BinaryReader): ConstExpr {
  let result: ConstExpr = { kind: "unknown" };
  while (r.pos < r.length) {
    const op = r.readByte();
    if (op === 0x0b) break; // end
    switch (op) {
      case 0x41:
        result = { kind: "i32", value: r.readI32Leb() };
        break;
      case 0x42:
        result = { kind: "i64", value: r.readI64Leb() };
        break;
      case 0x43:
        r.skip(4);
        result = { kind: "f32" };
        break;
      case 0x44:
        r.skip(8);
        result = { kind: "f64" };
        break;
      case 0x23:
        result = { kind: "global", index: r.readU32Leb() };
        break;
      case 0xd2:
        result = { kind: "ref.func", index: r.readU32Leb() };
        break;
      case 0xd0:
        r.skip(1); // reftype
        result = { kind: "ref.null" };
        break;
      default: {
        // Extended-const arithmetic (i32.add, etc.) and any other op: consume
        // its immediates via the shape table to stay aligned.
        const shape = (op === 0xfc || op === 0xfd || op === 0xfe
          ? prefixShape(op, r.readU32Leb())
          : (SHAPE[op] as Imm));
        consumeImmediates(r, shape);
        break;
      }
    }
  }
  return result;
}

/** Advances past an instruction's immediates given its shape (no callbacks). */
function consumeImmediates(r: BinaryReader, shape: Imm): void {
  switch (shape) {
    case Imm.U32:
      r.readU32Leb();
      break;
    case Imm.U32X2:
      r.readU32Leb();
      r.readU32Leb();
      break;
    case Imm.S32:
      r.readI32Leb();
      break;
    case Imm.S64:
      r.readI64Leb();
      break;
    case Imm.F32:
      r.skip(4);
      break;
    case Imm.F64:
      r.skip(8);
      break;
    case Imm.BLOCKTYPE:
      r.readS33();
      break;
    case Imm.MEMARG:
      readMemarg(r);
      break;
    case Imm.MEMARG_LANE:
      readMemarg(r);
      r.skip(1);
      break;
    case Imm.BR_TABLE: {
      const n = r.readU32Leb();
      for (let i = 0; i < n; i++) r.readU32Leb();
      r.readU32Leb();
      break;
    }
    case Imm.SELECT_T: {
      const n = r.readU32Leb();
      r.skip(n);
      break;
    }
    case Imm.V128_CONST:
    case Imm.SHUFFLE:
      r.skip(16);
      break;
    case Imm.LANE:
    case Imm.REFTYPE:
    case Imm.BYTE1:
      r.skip(1);
      break;
    case Imm.NONE:
    case Imm.UNKNOWN:
    default:
      break;
  }
}
