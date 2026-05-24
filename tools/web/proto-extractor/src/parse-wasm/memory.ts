/** A single active wasm data segment, placed at a fixed linear-memory address. */
export interface DataSegment {
    /** The linear-memory address this segment occupies. */
    readonly address: number;
    /** The raw bytes of this segment. */
    readonly data: Uint8Array;
}

const WASM_MAGIC = 0x6d736100; /* "\0asm" little-endian */
const SECTION_DATA = 11;
const OPCODE_I32_CONST = 0x41;
const OPCODE_END = 0x0b;

/**
 * Decodes one unsigned LEB128 integer.
 *
 * @returns the value and the number of bytes consumed.
 * @throws Error if the encoding exceeds 5 bytes (i.e. would overflow u32).
 */
function readVarUint(buf: Uint8Array, offset: number): [number, number] {
    let result = 0;
    let shift = 0;
    let bytes = 0;
    while (true) {
        const byte = buf[offset + bytes];
        if (byte === undefined) throw new Error("LEB128 EOF");
        bytes++;
        result += (byte & 0x7f) * Math.pow(2, shift);
        if ((byte & 0x80) === 0) break;
        shift += 7;
        if (shift > 35) throw new Error("LEB128 too long for u32");
    }
    return [result >>> 0, bytes];
}

/** Decodes one signed LEB128 integer (used by {@code i32.const} init expressions). */
function readVarInt(buf: Uint8Array, offset: number): [number, number] {
    let result = 0;
    let shift = 0;
    let bytes = 0;
    let byte = 0;
    while (true) {
        byte = buf[offset + bytes]!;
        bytes++;
        result |= (byte & 0x7f) << shift;
        shift += 7;
        if ((byte & 0x80) === 0) break;
        if (shift > 35) throw new Error("LEB128 too long for i32");
    }
    if (shift < 32 && (byte & 0x40)) {
        result |= -(1 << shift);
    }
    return [result, bytes];
}

/** Treats a list of {@link DataSegment}s as a sparse byte map, providing pointer-style reads. */
export class WasmMemory {
    constructor(public readonly segments: readonly DataSegment[]) {}

    /**
     * Returns the byte slice of {@code length} bytes starting at virtual
     * address {@code addr}, or {@code null} if the range escapes the loaded
     * segments.
     */
    read(addr: number, length: number): Uint8Array | null {
        for (const seg of this.segments) {
            if (addr >= seg.address && addr + length <= seg.address + seg.data.length) {
                const offset = addr - seg.address;
                return seg.data.subarray(offset, offset + length);
            }
        }
        return null;
    }

    /** Reads a little-endian unsigned 32-bit word, or returns {@code null}. */
    readU32(addr: number): number | null {
        const bytes = this.read(addr, 4);
        if (!bytes) return null;
        return (
            (bytes[0]! | (bytes[1]! << 8) | (bytes[2]! << 16) | (bytes[3]! << 24)) >>> 0
        );
    }

    /** Reads a little-endian signed 32-bit word, or returns {@code null}. */
    readI32(addr: number): number | null {
        const u = this.readU32(addr);
        return u === null ? null : (u | 0);
    }

    /**
     * Reads a null-terminated UTF-8 C string starting at virtual address
     * {@code addr}.
     *
     * @returns the decoded string, or {@code null} if the address falls
     *          outside any segment or no terminator is found within
     *          {@code maxLength} bytes.
     */
    readCString(addr: number, maxLength = 1024): string | null {
        for (const seg of this.segments) {
            if (addr < seg.address || addr >= seg.address + seg.data.length) continue;
            const start = addr - seg.address;
            const limit = Math.min(start + maxLength, seg.data.length);
            let end = start;
            while (end < limit && seg.data[end] !== 0) end++;
            if (end >= limit || seg.data[end] !== 0) return null;
            return Buffer.from(seg.data.subarray(start, end)).toString("utf-8");
        }
        return null;
    }
}

/**
 * Parses every active data segment out of a wasm binary and returns them in a
 * {@link WasmMemory}. Passive segments and segments with non-constant offsets
 * are skipped.
 *
 * @param binary - the wasm module bytes.
 * @returns a {@link WasmMemory} of the active data segments.
 * @throws Error if {@code binary} is not a well-formed wasm module header.
 */
export function parseWasmDataSegments(binary: Uint8Array): WasmMemory {
    const view = new DataView(binary.buffer, binary.byteOffset, binary.byteLength);
    if (view.getUint32(0, true) !== WASM_MAGIC) {
        throw new Error("Not a wasm module");
    }

    const segments: DataSegment[] = [];
    let off = 8; /* skip 4-byte magic + 4-byte version */

    while (off < binary.length) {
        const sectionId = binary[off++]!;
        const [sectionSize, sectionSizeBytes] = readVarUint(binary, off);
        off += sectionSizeBytes;
        const sectionEnd = off + sectionSize;

        if (sectionId !== SECTION_DATA) {
            off = sectionEnd;
            continue;
        }

        const [count, countBytes] = readVarUint(binary, off);
        off += countBytes;
        for (let i = 0; i < count; i++) {
            const [mode, modeBytes] = readVarUint(binary, off);
            off += modeBytes;

            if (mode === 0 || mode === 2) {
                if (mode === 2) {
                    const [, memidxBytes] = readVarUint(binary, off);
                    off += memidxBytes;
                }
                if (binary[off] !== OPCODE_I32_CONST) {
                    throw new Error(`Unsupported data offset opcode 0x${binary[off]!.toString(16)}`);
                }
                off++;
                const [addr, addrBytes] = readVarInt(binary, off);
                off += addrBytes;
                if (binary[off] !== OPCODE_END) {
                    throw new Error(`Expected end opcode, got 0x${binary[off]!.toString(16)}`);
                }
                off++;
                const [dataLen, dataLenBytes] = readVarUint(binary, off);
                off += dataLenBytes;
                segments.push({
                    address: addr >>> 0,
                    data: binary.subarray(off, off + dataLen),
                });
                off += dataLen;
            } else if (mode === 1) {
                const [dataLen, dataLenBytes] = readVarUint(binary, off);
                off += dataLenBytes;
                off += dataLen;
            } else {
                throw new Error(`Unknown data segment mode ${mode}`);
            }
        }

        off = sectionEnd;
    }

    return new WasmMemory(segments);
}
