// Pure, in-process WASM byte patcher. The write-counterpart to reading a module
// binary: deterministic, length-preserving mutations used for RE intervention
// (neutralizing a function to isolate behavior, forcing a constant, defeating a
// build-time gate). Generalizes the hand-rolled patcher from the MLow work.

import type { WasmAnalysis } from "../types/wasm.js";

export type WasmPatchOp =
  | { kind: "clearSharedMemory" }
  | { kind: "noopFunction"; funcIndex: number }
  | { kind: "overwriteBytes"; address: number; bytesBase64: string }
  | { kind: "overwriteFileBytes"; fileOffset: number; bytesBase64: string };

export interface WasmPatchResult {
  patched: Buffer;
  applied: string[];
  errors: string[];
}

/** Finds the first occurrence of {@code needle} in {@code haystack}, or -1. */
function indexOf(haystack: Uint8Array, needle: number[]): number {
  outer: for (let i = 0; i + needle.length <= haystack.length; i++) {
    for (let j = 0; j < needle.length; j++) {
      if (haystack[i + j] !== needle[j]) continue outer;
    }
    return i;
  }
  return -1;
}

/**
 * Clears the SHARED bit (0x02) on the {@code env.memory} import limits, so a
 * shared-memory (pthread) module can be loaded by tooling that rejects shared
 * memory. Length-preserving (one flag byte). No-op if not found or already
 * non-shared.
 */
function clearSharedMemory(bytes: Buffer, applied: string[], errors: string[]): void {
  // env.memory import declaration: 03 'e' 'n' 'v' 06 'm' 'e' 'm' 'o' 'r' 'y' 02 <limit flags>
  const needle = [0x03, 0x65, 0x6e, 0x76, 0x06, 0x6d, 0x65, 0x6d, 0x6f, 0x72, 0x79, 0x02];
  const at = indexOf(bytes, needle);
  if (at < 0) {
    errors.push("clearSharedMemory: env.memory import not found");
    return;
  }
  const limitOffset = at + needle.length;
  const flags = bytes[limitOffset];
  if ((flags & 0x02) === 0) {
    applied.push("clearSharedMemory: already non-shared (no change)");
    return;
  }
  bytes[limitOffset] = flags & ~0x02;
  applied.push(`clearSharedMemory: flags 0x${flags.toString(16)} -> 0x${bytes[limitOffset].toString(16)} at file offset ${limitOffset}`);
}

/**
 * Replaces a function body with a length-preserving no-op: zero local
 * declarations, {@code bodySize - 2} {@code nop}s, then {@code end}. Subsequent
 * code-section offsets are unchanged.
 */
function noopFunction(bytes: Buffer, analysis: WasmAnalysis, funcIndex: number, applied: string[], errors: string[]): void {
  const fn = analysis.functions.find((f) => f.index === funcIndex);
  if (!fn) {
    errors.push(`noopFunction: function index ${funcIndex} is not a defined function`);
    return;
  }
  if (fn.bodySize < 2) {
    errors.push(`noopFunction: body of ${funcIndex} too short to no-op`);
    return;
  }
  bytes[fn.bodyOffset] = 0x00; // no local declarations
  for (let i = 1; i < fn.bodySize - 1; i++) bytes[fn.bodyOffset + i] = 0x01; // nop
  bytes[fn.bodyOffset + fn.bodySize - 1] = 0x0b; // end
  applied.push(`noopFunction: funcIndex ${funcIndex} (${fn.bodySize} bytes at file offset ${fn.bodyOffset})`);
}

/** Maps a linear-memory address to a file offset via an active data segment. */
function addressToFileOffset(analysis: WasmAnalysis, address: number): number | null {
  for (const seg of analysis.dataSegments ?? []) {
    if (seg.mode !== "active" || seg.offset == null) continue;
    if (address >= seg.offset && address < seg.offset + seg.byteLength) {
      return seg.fileOffset + (address - seg.offset);
    }
  }
  return null;
}

function overwriteAt(bytes: Buffer, fileOffset: number, patch: Uint8Array, label: string, applied: string[], errors: string[]): void {
  if (fileOffset < 0 || fileOffset + patch.length > bytes.length) {
    errors.push(`${label}: file offset ${fileOffset} + ${patch.length} bytes is out of range`);
    return;
  }
  bytes.set(patch, fileOffset);
  applied.push(`${label}: wrote ${patch.length} bytes at file offset ${fileOffset}`);
}

/**
 * Applies a sequence of patch operations to a copy of {@code binary}, returning
 * the patched bytes plus per-operation applied/error logs. All operations are
 * length-preserving, so the module structure stays valid.
 *
 * @param binary the original module bytes
 * @param analysis its structural analysis (for function bodies and data segments)
 * @param ops the operations to apply, in order
 */
export function patchWasm(binary: Buffer | Uint8Array, analysis: WasmAnalysis, ops: WasmPatchOp[]): WasmPatchResult {
  const patched = Buffer.from(binary);
  const applied: string[] = [];
  const errors: string[] = [];

  for (const op of ops) {
    switch (op.kind) {
      case "clearSharedMemory":
        clearSharedMemory(patched, applied, errors);
        break;
      case "noopFunction":
        noopFunction(patched, analysis, op.funcIndex, applied, errors);
        break;
      case "overwriteBytes": {
        const fileOffset = addressToFileOffset(analysis, op.address);
        if (fileOffset == null) {
          errors.push(`overwriteBytes: address 0x${op.address.toString(16)} is not within an active data segment`);
          break;
        }
        overwriteAt(patched, fileOffset, Buffer.from(op.bytesBase64, "base64"), `overwriteBytes@0x${op.address.toString(16)}`, applied, errors);
        break;
      }
      case "overwriteFileBytes":
        overwriteAt(patched, op.fileOffset, Buffer.from(op.bytesBase64, "base64"), `overwriteFileBytes@${op.fileOffset}`, applied, errors);
        break;
    }
  }

  return { patched, applied, errors };
}
