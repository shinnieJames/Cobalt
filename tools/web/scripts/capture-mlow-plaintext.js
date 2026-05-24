#!/usr/bin/env node
/**
 * Captures plaintext MLow frames by setting a CDP Debugger breakpoint
 * at the SFrame cipher's encrypt entry inside the live Voip WASM
 * worker. Pre-encryption plaintext is what Phase 3.3 reverse-
 * engineering needs.
 *
 * The cipher entry on the LIVE snapshot's WASM is fn#6892
 *   - signature (i32, i32, i32) -> i32
 *   - body byte offset 0x2b7e01
 *   - function-table slot 4820 (vtable entry; sibling of fn#6891
 *     at slot 4819)
 *
 * The cipher class is facebook::sframe::CipherInterface, the
 * algorithm is AES-128 in CTR mode (verified by typeinfo string +
 * "AES-128 integer counter mode" literal in the vtable
 * initializer).
 *
 * Strategy:
 *   1. Attach to all Voip workers via browser-level CDP with
 *      autoAttach + waitForDebuggerOnStart.
 *   2. On each attached worker, enable Debugger and wait for the
 *      WASM scriptParsed event identifying the Voip module.
 *   3. Set a breakpoint at the cipher entry body offset.
 *   4. On Debugger.paused: read the input pointer's bytes from
 *      WASM linear memory (the plaintext MLow frame), record into
 *      a JSON dump, resume.
 *   5. After a configurable capture window, dump the corpus to a
 *      file.
 *
 * Tradeoff: each pause is a CDP roundtrip (~10 ms). At audio rate
 * (50/sec on a 20 ms frame) this stalls the codec by ~50% per
 * second. Audio may glitch but the call should stay alive.
 *
 * Usage: node capture-mlow-plaintext.js [cdpPort] [outFile]
 */
'use strict';

const fs = require('fs');
const cdpPort = process.argv[2] ? parseInt(process.argv[2], 10) : 55006;
const outFile = process.argv[3] || `mlow-plaintext-${Date.now()}.json`;

// AES round routines on the live YQjmnxbJlry.wasm. The vtable
// entries fn#6884/fn#6885 turned out NOT to be on the audio
// hot path — captured zero hits during a 3-minute active call
// while the SAB ring confirmed 2200 RTP frames going out of the
// codec worker. So we instead break on the AES INNER ROUND
// itself: any encrypt/decrypt operation in the entire WASM
// must call this. The trade-off is many hits per call (~10
// rounds × 50 fps × 14 workers = thousands of pauses per second
// across the cluster), so audio will glitch heavily during
// capture.
const CIPHER_2ARG_BODY_OFFSET = 0x2b7f50;  // fn#6881 — inner round (16 loads)
const CIPHER_3ARG_BODY_OFFSET = 0x2b881d;  // fn#6886 — wrapping round (16 loads)

class CDP {
    constructor(ws) {
        this.ws = ws; this.nextId = 1; this.pending = new Map(); this.evHandlers = [];
        ws.onmessage = (ev) => {
            const m = JSON.parse(ev.data);
            if (m.id && this.pending.has(m.id)) {
                const {resolve, reject} = this.pending.get(m.id);
                this.pending.delete(m.id);
                if (m.error) reject(new Error(m.error.message)); else resolve(m.result);
            } else if (m.method) this.evHandlers.forEach(h => h(m));
        };
    }
    send(method, params, sessionId) {
        const id = this.nextId++;
        return new Promise((resolve, reject) => {
            this.pending.set(id, {resolve, reject});
            const msg = {id, method, params: params || {}};
            if (sessionId) msg.sessionId = sessionId;
            this.ws.send(JSON.stringify(msg));
        });
    }
    on(handler) { this.evHandlers.push(handler); }
}

const captures = [];
let scriptIdsByWorker = new Map();
const handledTargets = new Set();

async function attachAndInstrument(cdp, sessionId, ti) {
    if (handledTargets.has(sessionId)) return;
    handledTargets.add(sessionId);
    const isVoip = (ti.title || '').includes('WAWebVoip');
    if (!isVoip) {
        try { await cdp.send('Runtime.runIfWaitingForDebugger', {}, sessionId); } catch {}
        return;
    }
    console.log(`attaching to Voip worker ${ti.targetId.slice(0, 8)}...`);
    await cdp.send('Debugger.enable', {}, sessionId);
    await cdp.send('Debugger.setBreakpointsActive', {active: true}, sessionId).catch(()=>{});
    await cdp.send('Runtime.runIfWaitingForDebugger', {}, sessionId).catch(() => {});
}

async function onScriptParsed(cdp, sessionId, params) {
    if (!handledTargets.has(sessionId)) return;
    const url = params.url || '';
    // Log every script for diagnostics
    if (url.endsWith('.wasm') || url.includes('whatsapp')) {
        console.log(`  [${sessionId.slice(0,8)}] scriptParsed url=${url.slice(0,80)} scriptId=${params.scriptId}`);
    }
    // Match any large Voip WASM — the file name changes per
    // snapshot. The cipher sits deep in the binary so require
    // length > 9 MB to avoid 2-3 MB helper WASMs.
    const isVoipWasm = url.endsWith('.wasm') && (params.length || 0) > 9_000_000;
    if (!isVoipWasm) return;
    // Instrument every Voip worker we see. Audio frames flow
    // through ONE specific worker (per drainStats observation,
    // typically the SCTP DCThread pthread which spawns ~16+ into
    // the call), and we don't know in advance which one — so we
    // BP all of them. Each worker only fires when it actually
    // runs the cipher; idle ones sit silent.
    // Only set breakpoint once per Voip worker on the codec WASM.
    // The Voip WASM is the larger one (~9.8 MB); skip the rate-control
    // and other WASM modules that are typically much smaller.
    const codeOffset = params.codeOffset;
    if (scriptIdsByWorker.has(sessionId)) return;
    scriptIdsByWorker.set(sessionId, params.scriptId);
    console.log(`  worker ${sessionId.slice(0, 8)} WASM script=${params.scriptId} url=${url}`);
    // Set breakpoints on BOTH cipher vtable entries (slot 4819 =
    // fn#6891 and slot 4820 = fn#6892). One is encrypt, one is
    // decrypt; both are interesting and we'll filter post-capture.
    for (const [name, off] of [['cipher2arg', CIPHER_2ARG_BODY_OFFSET],
                                ['cipher3arg', CIPHER_3ARG_BODY_OFFSET]]) {
        try {
            const r = await cdp.send('Debugger.setBreakpoint', {
                location: {
                    scriptId: params.scriptId, lineNumber: 0, columnNumber: off
                }
            }, sessionId);
            console.log(`  ${sessionId.slice(0,8)} BP @ ${name}: bp=${r.breakpointId} actualCol=${r.actualLocation && r.actualLocation.columnNumber}`);
        } catch (e) {
            console.warn(`  ${sessionId.slice(0,8)} BP @ ${name} failed: ${e.message}`);
        }
    }
}

const bpHitOnce = new Set();
async function onPaused(cdp, sessionId, params) {
    console.log(`  [${sessionId.slice(0,8)}] PAUSED reason=${params.reason} hitBPs=${JSON.stringify(params.hitBreakpoints||[])}`);
    if (params.reason !== 'other' && params.reason !== 'instrumentation') {
        await cdp.send('Debugger.resume', {}, sessionId).catch(()=>{});
        return;
    }
    // Disable our breakpoints on this worker after the first hit
    // so the codec doesn't pause 500x/s (would glitch audio
    // beyond recovery). One frame is enough to begin Phase 3.3.
    if (!bpHitOnce.has(sessionId)) {
        bpHitOnce.add(sessionId);
        for (const bp of (params.hitBreakpoints||[])) {
            cdp.send('Debugger.removeBreakpoint', {breakpointId: bp}, sessionId).catch(()=>{});
        }
    }
    const cf = params.callFrames && params.callFrames[0];
    if (!cf) {
        await cdp.send('Debugger.resume', {}, sessionId).catch(()=>{});
        return;
    }
    // Identify which function we hit (fn#6891 vs fn#6892) by the
    // call frame's column number.
    const col = cf.location && cf.location.columnNumber;
    const fnTag = (col && col >= CIPHER_2ARG_BODY_OFFSET && col < CIPHER_3ARG_BODY_OFFSET) ? 'cipher2arg'
        : (col && col >= CIPHER_3ARG_BODY_OFFSET) ? 'cipher3arg' : 'unknown';
    try {
        // Locals for fn#6892 (i32,i32,i32) -> i32: arg0=state/this,
        // arg1=in_ptr, arg2=out_ptr_or_size. Read them via the
        // call frame's scopeChain. WASM locals appear as a "local"
        // scope.
        const localScope = cf.scopeChain && cf.scopeChain.find(s => s.type === 'local');
        let arg0 = -1, arg1 = -1, arg2 = -1;
        if (localScope && localScope.object && localScope.object.objectId) {
            const props = await cdp.send('Runtime.getProperties', {
                objectId: localScope.object.objectId,
                ownProperties: true
            }, sessionId);
            for (const p of (props.result || [])) {
                if (p.name === 'arg#0' || p.name === '$var0') arg0 = p.value && p.value.value;
                else if (p.name === 'arg#1' || p.name === '$var1') arg1 = p.value && p.value.value;
                else if (p.name === 'arg#2' || p.name === '$var2') arg2 = p.value && p.value.value;
            }
            // V8 newer versions name them '$var0' or '$arg0' depending on version
        }
        // If the standard names didn't work, dump all locals so we can adapt.
        if (arg1 === -1 && localScope && localScope.object) {
            const props = await cdp.send('Runtime.getProperties', {
                objectId: localScope.object.objectId,
                ownProperties: true
            }, sessionId);
            console.log(`    locals shape: ${(props.result||[]).map(p => p.name+'='+(p.value&&p.value.value)).join(', ')}`);
        }
        if (arg1 !== -1) {
            // arg2 might be the buffer length — try reading some bytes from arg1
            // assuming a reasonable cipher block (16-512 B). Without knowing
            // the exact length contract we read 256 B.
            const READ_LEN = arg2 > 0 && arg2 < 4096 ? arg2 : 256;
            const memBytes = await readWasmMemory(cdp, sessionId, cf.callFrameId, arg1, READ_LEN);
            captures.push({
                ts: Date.now(),
                worker: sessionId.slice(0, 8),
                fn: fnTag,
                arg0, arg1, arg2,
                bytesB64: memBytes ? Buffer.from(memBytes).toString('base64') : null
            });
            if (captures.length % 10 === 0) {
                console.log(`  captured ${captures.length} frames; latest worker=${sessionId.slice(0,8)} arg0=0x${(arg0>>>0).toString(16)} arg1=0x${(arg1>>>0).toString(16)} arg2=${arg2} bytes=${memBytes ? memBytes.length : 'n/a'}`);
            }
        }
    } catch (e) {
        console.warn(`  capture error: ${e.message}`);
    } finally {
        try { await cdp.send('Debugger.resume', {}, sessionId); } catch {}
    }
}

async function readWasmMemory(cdp, sessionId, callFrameId, ptr, len) {
    // Use Debugger.evaluateOnCallFrame with a JS expression that reads
    // memory via the WASM module's "memories" property. The exact
    // expression depends on V8 version. Try a couple shapes.
    const expressions = [
        `(()=>{try{const m=memories[0];const v=new Uint8Array(m.buffer, ${ptr}, ${len});const a=Array.from(v);return JSON.stringify(a);}catch(e){return 'ERR:'+e.message;}})()`,
        `(()=>{try{const v=new Uint8Array(globals[0].buffer, ${ptr}, ${len});return JSON.stringify(Array.from(v));}catch(e){return 'ERR:'+e.message;}})()`
    ];
    for (const expr of expressions) {
        try {
            const r = await cdp.send('Debugger.evaluateOnCallFrame', {
                callFrameId, expression: expr, returnByValue: true
            }, sessionId);
            if (r.result && typeof r.result.value === 'string'
                && !r.result.value.startsWith('ERR:')) {
                return JSON.parse(r.result.value);
            }
        } catch (e) {}
    }
    return null;
}

async function main() {
    const ver = await (await fetch(`http://127.0.0.1:${cdpPort}/json/version`)).json();
    const ws = new WebSocket(ver.webSocketDebuggerUrl);
    await new Promise(r => { ws.onopen = r; });
    const cdp = new CDP(ws);
    console.log('attached to browser session');

    cdp.on(async (msg) => {
        try {
            if (msg.method === 'Target.attachedToTarget') {
                await attachAndInstrument(cdp, msg.params.sessionId, msg.params.targetInfo);
            } else if (msg.method === 'Debugger.scriptParsed' && msg.sessionId) {
                await onScriptParsed(cdp, msg.sessionId, msg.params);
            } else if (msg.method === 'Debugger.paused' && msg.sessionId) {
                await onPaused(cdp, msg.sessionId, msg.params);
            }
        } catch (e) {
            console.warn('handler err:', e.message);
        }
    });

    await cdp.send('Target.setDiscoverTargets', {discover: true});
    await cdp.send('Target.setAutoAttach', {
        autoAttach: true,
        waitForDebuggerOnStart: true,
        flatten: true
    });
    // Manually attach to existing Voip workers and the page so our
    // handler sees them.
    const targets = await cdp.send('Target.getTargets', {});
    for (const t of targets.targetInfos) {
        if ((t.type === 'page' || t.type === 'worker' || t.type === 'service_worker')
            && (t.url || t.title || '').match(/whatsapp|WAWebVoip/i)) {
            try {
                const a = await cdp.send('Target.attachToTarget', {targetId: t.targetId, flatten: true});
                await attachAndInstrument(cdp, a.sessionId, t);
                if (t.type === 'page') {
                    // Propagate auto-attach so child workers spawned later are caught.
                    await cdp.send('Target.setAutoAttach', {
                        autoAttach: true, waitForDebuggerOnStart: true, flatten: true
                    }, a.sessionId).catch(()=>{});
                }
            } catch (e) {
                console.warn(`pre-attach ${t.targetId} failed: ${e.message}`);
            }
        }
    }
    console.log('listening for breakpoints. Press Ctrl+C to stop and dump.');
    process.on('SIGINT', () => { dumpAndExit(); });

    // Stay open
    await new Promise(() => {});
}

function dumpAndExit() {
    fs.writeFileSync(outFile, JSON.stringify({
        capturedAt: Date.now(),
        n: captures.length,
        sends: captures
    }, null, 2));
    console.log(`\nwrote ${outFile} (${captures.length} captures)`);
    process.exit(0);
}

main().catch(e => { console.error(e); process.exit(1); });
