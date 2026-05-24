#!/usr/bin/env node
/**
 * Re-derives the SFrame cipher function indices for the LIVE
 * snapshot's Voip WASM. The Java AesTableLocatorTest does the same
 * thing offline against a cached snapshot; this script targets a
 * pre-downloaded WASM file (typically the latest) so we can set a
 * CDP Debugger breakpoint with the correct function indices.
 *
 * Strategy:
 *   1. Find the AES S-box (16-byte known prefix) in the data section
 *      bytes — passive segments, so we scan the WASM bytes directly.
 *   2. Decode the data section to map data bytes to linear-memory
 *      offsets when memory.init copies them at runtime. We can't
 *      easily simulate memory.init, so we report the WITHIN-SEGMENT
 *      offsets and rely on the Java oracle for the runtime offset.
 *
 * For the simpler purpose of locating the cipher functions, we use
 * a different, more direct strategy: look for the `0x2 0x6 1 0x77`
 * Itanium C++ mangled string `N2wa6sframe6cipher17WASframeAESCipherE`
 * (or a substring) in the data section, find its offset, find
 * functions referencing it via i32.const, and walk the resulting
 * neighborhood.
 *
 * Usage: node find-cipher-fn.js <path-to-wasm>
 */
'use strict';

const fs = require('fs');
const path = process.argv[2];
if (!path) { console.error('usage: find-cipher-fn.js <wasm-path>'); process.exit(1); }
const wasm = fs.readFileSync(path);
console.log('wasm size:', wasm.length, 'bytes');

function findAll(haystack, needle) {
    const out = [];
    let pos = 0;
    while ((pos = haystack.indexOf(needle, pos)) !== -1) {
        out.push(pos);
        pos++;
    }
    return out;
}

// Look for the AES-128 CTR mode marker string and the cipher class typeinfo
const markerStrings = [
    'AES-128 integer counter mode',
    'WASframeAESCipherE',
    'CipherInterfaceE',
    'wa_sframe_encrypt'
];
console.log('\n--- string anchors (file offsets within full WASM) ---');
for (const s of markerStrings) {
    const hits = findAll(wasm, Buffer.from(s));
    console.log(`  "${s}": ${hits.length} hit(s) at`, hits);
}

// AES S-box pattern
const sbox = Buffer.from([0x63, 0x7c, 0x77, 0x7b, 0xf2, 0x6b, 0x6f, 0xc5,
                          0x30, 0x01, 0x67, 0x2b, 0xfe, 0xd7, 0xab, 0x76]);
console.log('\n--- AES S-box hits ---');
console.log('  ', findAll(wasm, sbox));

// To find a function's body offset, we'd need to parse the WASM
// sections. The Java side does this; mirror it here in trimmed form.
// Section IDs we care about: 3 (function), 7 (export), 9 (element), 10 (code), 11 (data).
function readVarUint(buf, off) {
    let result = 0, shift = 0, i = off;
    while (true) {
        const b = buf[i++];
        result |= (b & 0x7F) << shift;
        if ((b & 0x80) === 0) return [result >>> 0, i];
        shift += 7;
        if (shift >= 32) throw new Error('varuint too long');
    }
}
function readVarSint(buf, off) {
    let result = 0n, shift = 0n, i = off;
    let b;
    while (true) {
        b = buf[i++];
        result |= BigInt(b & 0x7F) << shift;
        shift += 7n;
        if ((b & 0x80) === 0) break;
    }
    if ((b & 0x40) !== 0 && shift < 64n) result |= ~0n << shift;
    return [Number(BigInt.asIntN(32, result)), i];
}

// Walk sections
let p = 8; // skip magic + version
const sections = {};
while (p < wasm.length) {
    const id = wasm[p++];
    const [len, p2] = readVarUint(wasm, p);
    sections[id] = sections[id] || [];
    sections[id].push({start: p2, end: p2 + len});
    p = p2 + len;
}
console.log('\n--- WASM sections found:');
for (const id of Object.keys(sections)) {
    console.log(`  section ${id}: ${sections[id].length} occurrence(s), first at`,
                sections[id][0]);
}

// Parse the import section to count imported functions
let importedFunctionCount = 0;
if (sections[2]) {
    const imp = sections[2][0];
    let q = imp.start;
    const [count, q2] = readVarUint(wasm, q); q = q2;
    for (let i = 0; i < count; i++) {
        const [modLen, q3] = readVarUint(wasm, q); q = q3;
        q += modLen;
        const [nameLen, q4] = readVarUint(wasm, q); q = q4;
        q += nameLen;
        const kind = wasm[q++];
        if (kind === 0) { // function
            importedFunctionCount++;
            const [, q5] = readVarUint(wasm, q); q = q5;
        } else if (kind === 1) { // table
            q += 1; const [, q5] = readVarUint(wasm, q); q = q5;
            const [, q6] = readVarUint(wasm, q); q = q6;
            // skip max if flag was 0/1 — depend on flag (already parsed)
        } else if (kind === 2) { // memory
            const flags = wasm[q++];
            const [, q5] = readVarUint(wasm, q); q = q5;
            if (flags & 1) { const [, q6] = readVarUint(wasm, q); q = q6; }
        } else if (kind === 3) { // global
            q += 2;
        } else throw new Error('unknown import kind ' + kind);
    }
}
console.log('imported functions:', importedFunctionCount);

// Walk the code section, recording each function body's start offset
const codeSec = sections[10][0];
let q = codeSec.start;
const [funcCount, q2] = readVarUint(wasm, q); q = q2;
const bodyStarts = []; // file offset of body start for local function `local`
for (let i = 0; i < funcCount; i++) {
    const [bodySize, q3] = readVarUint(wasm, q); q = q3;
    bodyStarts.push(q); // start of this body's bytes (after the body-size prefix)
    q += bodySize;
}
console.log('local function bodies:', bodyStarts.length);

// Heuristic: scan every body for i32.const operands. Build a map from
// linear-mem-style offsets to functions referencing them. We can't
// distinguish "real" linear offsets from random literal numbers
// without knowing the runtime memory layout, but the AES-table
// offset must appear MANY times in a small handful of functions —
// the round routines.

// We don't know the runtime offset of the AES tables in this WASM
// without running it. Instead, look for clusters of consecutive
// i32.const values: an AES round emits ~16 loads per call, each
// at offsets sbox+0, sbox+256, sbox+512, sbox+768, sbox+1024, etc.
// (S-box + 4 T-tables, 256 B / 1 KiB each).

function* scanI32Const(body) {
    for (let i = 0; i < body.length; i++) {
        if (body[i] !== 0x41) continue;
        try {
            const [v, n] = readVarSint(body, i + 1);
            yield v;
        } catch (_) {}
    }
}

const codeStart = bodyStarts[0];
const codeEnd = sections[10][0].end;
const codeBytes = wasm.slice(codeStart, codeEnd);
// per-function body start within codeBytes (relative)
const bodyRelStarts = bodyStarts.map(x => x - codeStart);

// For each function, count i32.const values that look like AES-table
// addresses: clustered (same baseAddr + multiples of 256).
const candidates = []; // {localIdx, score, base}
for (let i = 0; i < funcCount; i++) {
    const bStart = bodyRelStarts[i];
    const bEnd = (i + 1 < funcCount) ? bodyRelStarts[i + 1] : codeBytes.length;
    const body = codeBytes.slice(bStart, bEnd);
    const consts = [];
    for (const v of scanI32Const(body)) consts.push(v);
    if (consts.length < 12) continue;
    // Look for "AES base address" — value where multiple consts are within [base, base+5KB]
    // and several are at base + multiples of 256.
    const counts = new Map();
    for (const v of consts) counts.set(v, (counts.get(v) || 0) + 1);
    // Try each candidate base = some i32.const value in this function;
    // count how many of the consts fall in [base, base+5120] and are
    // at offsets divisible by 256.
    let bestBase = -1, bestScore = 0;
    for (const base of new Set(consts)) {
        // AES tables sit in real heap territory — well above the
        // stack and small-int operands that pollute typical
        // i32.const usage.
        if (base < 0x10000 || base >= 0x10000000) continue;
        let score = 0;
        const offsetsHit = new Set();
        for (const v of consts) {
            const d = v - base;
            // Require offsets at exact 256-byte alignment for the
            // S-box (256 B) + T0..T3 (1 KiB each = 0, 256, 1280,
            // 2304, 3328, 4352).
            if (d >= 0 && d <= 4352 && d % 256 === 0) {
                score++;
                offsetsHit.add(d);
            }
        }
        // Score only counts if multiple distinct table offsets are hit.
        if (offsetsHit.size >= 3 && score > bestScore) {
            bestScore = score;
            bestBase = base;
        }
    }
    if (bestScore >= 4) {
        candidates.push({local: i, abs: importedFunctionCount + i, score: bestScore, base: bestBase, totalConsts: consts.length});
    }
}
candidates.sort((a, b) => b.score - a.score);
console.log('\n--- AES-table candidate functions (top 20) ---');
for (const c of candidates.slice(0, 20)) {
    console.log(`  fn#${c.abs}  score=${c.score}  totalConsts=${c.totalConsts}  base=0x${c.base.toString(16)}`);
}
