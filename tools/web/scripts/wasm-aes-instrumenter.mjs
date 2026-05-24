#!/usr/bin/env node
//
// CDP-based pre-instantiation hook for the WAVoip wasm.
//
// Drives Chrome DevTools Protocol Target.setAutoAttach with
// waitForDebuggerOnStart=true so we receive each new worker target paused
// at startup. While the worker is paused we inject a JS hook that
// overrides WebAssembly.instantiate / WebAssembly.instantiateStreaming
// before the wasm loads. The hook captures (1) the WebAssembly.Memory
// from the imports object passed to instantiate, and (2) the resulting
// instance's __indirect_function_table; it then replaces slot 4946 (AES
// encrypt, fn 7000) with a JS wrapper that snapshots wasm memory at the
// two i32 pointer arguments before and after the call. Plaintext is
// posted out via console.log with a magic JSON prefix the controller
// matches on Runtime.consoleAPICalled events.
//
// Usage as a library (preferred — see generate-interaction.mjs):
//   import { setupInstrumenter } from './wasm-aes-instrumenter.mjs';
//   const inst = await setupInstrumenter({ cdpPort, sessionLabel });
//   inst.on('aes', (event) => { ... });
//
// Default AES-related table slots (snapshot 1039561367 build IL8soPYFU4n):
//   4946 → fn 7000 — AES encrypt, sig (i32,i32) -> i32
//   4947 → fn 7001 — AES init wrapper, sig (i32,i32,i32) -> i32
// If slots move between revisions, override via the opts.

import { WebSocket } from "ws";
import { EventEmitter } from "node:events";

const COBALT_MAGIC = "__COBALT_AES__";

// The hook body — injected as a string into every new worker target,
// EVALUATED BEFORE any worker code runs (target is paused on startup).
function buildHookScript(aesTableSlot, aesInitSlot) {
  return `
(function () {
  if (globalThis.__cobaltAesHookInstalled) return;
  globalThis.__cobaltAesHookInstalled = true;
  const SLOT = ${aesTableSlot};
  const INIT_SLOT = ${aesInitSlot};
  const t0 = (globalThis.performance && performance.now) ? performance.now() : Date.now();

  function bytesToB64(buf, off, len) {
    const u = new Uint8Array(buf, off, len);
    let s = '';
    for (let i = 0; i < u.length; i += 0x8000) s += String.fromCharCode.apply(null, u.subarray(i, i + 0x8000));
    return btoa(s);
  }
  function emit(payload) {
    try { console.log(${JSON.stringify(COBALT_MAGIC)} + ' ' + JSON.stringify(payload)); } catch (_) {}
  }

  // Stash the original WebAssembly.Instance constructor up front so that
  // when we build the trampoline below we can bypass our own hook (which
  // is registered later in this script) and avoid an infinite recursion.
  const OrigWebAssemblyInstance = WebAssembly.Instance;
  // Capture (Memory, Module) from the imports object that the worker
  // hands to WebAssembly.instantiate / instantiateStreaming.
  let captured = { mem: null, instances: new Set() };
  // Trampoline templates for two signatures.
  // (i32,i32)->i32 = type 'iii' (2 i32 args, 1 i32 result)
  const TRAMPOLINE_2 = new Uint8Array([
    0x00,0x61,0x73,0x6d,0x01,0x00,0x00,0x00,                                  // magic + version
    0x01,0x07,0x01,0x60,0x02,0x7f,0x7f,0x01,0x7f,                             // type
    0x02,0x0c,0x01,0x03,0x65,0x6e,0x76,0x04,0x74,0x72,0x61,0x70,0x00,0x00,    // import "env" "trap"
    0x03,0x02,0x01,0x00,                                                       // function
    0x07,0x06,0x01,0x02,0x66,0x6e,0x00,0x01,                                   // export "fn"
    0x0a,0x0a,0x01,0x08,0x00,0x20,0x00,0x20,0x01,0x10,0x00,0x0b                // code: body size 8
  ]);
  // (i32,i32,i32)->i32 — 3-arg variant for AES init wrapper
  const TRAMPOLINE_3 = new Uint8Array([
    0x00,0x61,0x73,0x6d,0x01,0x00,0x00,0x00,                                  // magic + version
    0x01,0x08,0x01,0x60,0x03,0x7f,0x7f,0x7f,0x01,0x7f,                        // type (3 i32 -> i32)
    0x02,0x0c,0x01,0x03,0x65,0x6e,0x76,0x04,0x74,0x72,0x61,0x70,0x00,0x00,    // import "env" "trap"
    0x03,0x02,0x01,0x00,                                                       // function
    0x07,0x06,0x01,0x02,0x66,0x6e,0x00,0x01,                                   // export "fn"
    // code section: body size = 10, payload size = 12 (=count + bodysize + body)
    0x0a,0x0c,0x01,0x0a,0x00,0x20,0x00,0x20,0x01,0x20,0x02,0x10,0x00,0x0b
  ]);

  function buildTrampoline(jsFn, argCount) {
    try {
      const tpl = argCount === 3 ? TRAMPOLINE_3 : TRAMPOLINE_2;
      const tramModule = new WebAssembly.Module(tpl);
      const tramInst = new OrigWebAssemblyInstance(tramModule, { env: { trap: jsFn } });
      return tramInst.exports.fn;
    } catch (e) { return null; }
  }

  function installOnInstance(instance) {
    try {
      if (captured.instances.has(instance)) return false;
      captured.instances.add(instance);
      const exp = instance.exports;
      const table = exp && exp.__indirect_function_table;
      const memory = (exp && exp.memory) || captured.mem;
      if (!table || !memory) {
        emit({ phase: 'install-skip', reason: 'no-table-or-memory', hasTable: !!table, hasMemory: !!memory });
        return false;
      }

      // Install AES INIT trampoline at INIT_SLOT (3 i32 args).
      if (INIT_SLOT > 0 && table.length > INIT_SLOT) {
        try {
          const origInit = table.get(INIT_SLOT);
          if (typeof origInit === 'function') {
            const initWrapper = buildTrampoline(function (ctxPtr, keyPtr, keyBits) {
              emit({ phase: 'aes-init', initSlot: INIT_SLOT, ctxPtr, keyPtr, keyBits, t: ((globalThis.performance && performance.now) ? performance.now() : Date.now()) - t0 });
              try { return (origInit(ctxPtr, keyPtr, keyBits) | 0); } catch (e) { throw e; }
            }, 3);
            if (initWrapper) {
              try { table.set(INIT_SLOT, initWrapper); emit({ phase: 'install-ok', slot: INIT_SLOT, kind: 'init', tableLen: table.length }); }
              catch (e) { emit({ phase: 'install-skip', kind: 'init', reason: 'table-set', err: String(e && e.message) }); }
            } else { emit({ phase: 'install-skip', kind: 'init', reason: 'no-wrapper' }); }
          }
        } catch (_) {}
      }

      if (table.length <= SLOT) {
        emit({ phase: 'install-skip', reason: 'slot-out-of-range', tableLen: table.length, slot: SLOT });
        return false;
      }
      let orig;
      try { orig = table.get(SLOT); } catch (e) { emit({ phase:'install-skip', reason:'table-get-throw', err: String(e) }); return false; }
      if (typeof orig !== 'function') { emit({ phase: 'install-skip', reason: 'slot-empty', slot: SLOT }); return false; }

      // Build a wasm-callable wrapper by synthesizing a 55-byte
      // trampoline module on the fly: it imports our JS function and
      // exports a real wasm function (with the correct (i32,i32)->i32
      // type) which we then drop into table slot SLOT. This sidesteps
      // both \`WebAssembly.Function\` (not exposed in plain Chrome) and
      // emscripten's \`convertJsFunctionToWasm\` (defined later in worker init).
      const jsWrapper = function (statePtr, roundKeyPtr) {
        let pt, key, ct, result, err;
        try { pt = bytesToB64(memory.buffer, statePtr, 16); } catch (e) { pt = 'ERR:' + e.message; }
        try { key = bytesToB64(memory.buffer, roundKeyPtr, 256); } catch (e) { key = 'ERR:' + e.message; }
        try { result = orig(statePtr, roundKeyPtr); } catch (e) { err = String(e && e.message); }
        try { ct = bytesToB64(memory.buffer, statePtr, 16); } catch (e) { ct = 'ERR:' + e.message; }
        emit({ phase: 'aes', statePtr, roundKeyPtr, pt, key, ct, err, t: ((globalThis.performance && performance.now) ? performance.now() : Date.now()) - t0 });
        if (err) throw new Error(err);
        return (result | 0);
      };
      const wrapper = buildTrampoline(jsWrapper, 2);
      if (!wrapper) {
        emit({ phase: 'install-skip', reason: 'no-wrapper' });
        return false;
      }
      try { table.set(SLOT, wrapper); }
      catch (e) {
        emit({ phase: 'install-skip', reason: 'table-set', err: String(e && e.message), wrapperKind: typeof wrapper });
        return false;
      }
      emit({ phase: 'install-ok', slot: SLOT, tableLen: table.length });
      return true;
    } catch (e) {
      emit({ phase: 'install-error', err: String(e && e.message) });
      return false;
    }
  }

  function scanImportsForMemory(importsObj) {
    try {
      if (importsObj && typeof importsObj === 'object') {
        for (const ns of Object.values(importsObj)) {
          if (ns && typeof ns === 'object') {
            for (const v of Object.values(ns)) {
              if (v && typeof v === 'object' && (v instanceof WebAssembly.Memory)) {
                captured.mem = v;
                emit({ phase: 'capture-memory', source: 'imports', pages: v.buffer.byteLength >>> 16 });
              }
            }
          }
        }
      }
    } catch (_) {}
  }
  function wrapInstantiate(orig) {
    return function (bufOrModule, importsObj) {
      scanImportsForMemory(importsObj);
      const ret = orig.apply(WebAssembly, arguments);
      Promise.resolve(ret).then((r) => {
        const inst = r && r.instance ? r.instance : r;
        if (inst && inst.exports) installOnInstance(inst);
      }, (e) => { emit({ phase: 'instantiate-rejected', err: String(e && e.message) }); });
      return ret;
    };
  }
  const origInstantiate = WebAssembly.instantiate;
  WebAssembly.instantiate = wrapInstantiate(origInstantiate.bind(WebAssembly));
  const origStreaming = WebAssembly.instantiateStreaming;
  WebAssembly.instantiateStreaming = wrapInstantiate(origStreaming.bind(WebAssembly));

  // ALSO override the synchronous WebAssembly.Instance constructor.
  // Emscripten pthread workers receive an already-compiled
  // WebAssembly.Module from the main thread and call
  // new WebAssembly.Instance(module, imports) — bypassing instantiate().
  const OrigInstance = WebAssembly.Instance;
  function CobaltInstance(module, importsObj) {
    scanImportsForMemory(importsObj);
    const inst = new OrigInstance(module, importsObj);
    // installOnInstance is microtask-safe; do it sync since the wasm
    // body may already try to call slot 4946 in the next microtask.
    try { installOnInstance(inst); } catch (_) {}
    return inst;
  }
  CobaltInstance.prototype = OrigInstance.prototype;
  WebAssembly.Instance = CobaltInstance;

  emit({ phase: 'hook-installed' });
})();
`;
}

/**
 * Drives Target.setAutoAttach on a browser-level CDP endpoint. For each
 * new worker target it injects the AES hook BEFORE any worker code runs.
 * Returns an EventEmitter that emits:
 *   'aes' : { statePtr, roundKeyPtr, pt, key, ct, t, _targetUrl }
 *   'install' : { phase: 'install-ok' | 'install-skip' | ..., _targetUrl }
 *   'attached' : { type, url, sessionId }
 */
export async function setupInstrumenter({ cdpPort, label = "default", aesTableSlot = 4946, aesInitSlot = 4947 }) {
  if (!cdpPort) throw new Error("cdpPort required");

  const versionInfo = await (await fetch(`http://127.0.0.1:${cdpPort}/json/version`)).json();
  const wsUrl = versionInfo.webSocketDebuggerUrl;
  const emitter = new EventEmitter();
  const hookScript = buildHookScript(aesTableSlot, aesInitSlot);

  const ws = new WebSocket(wsUrl);
  let nextId = 0;
  const pending = new Map();
  // Map sessionId -> { type, url }
  const sessions = new Map();

  function send(method, params = {}, sessionId = undefined) {
    return new Promise((resolve, reject) => {
      const id = ++nextId;
      pending.set(id, { resolve, reject });
      const msg = { id, method, params };
      if (sessionId) msg.sessionId = sessionId;
      ws.send(JSON.stringify(msg));
      setTimeout(() => {
        if (pending.delete(id)) reject(new Error(`${method}: timeout`));
      }, 10000);
    });
  }

  await new Promise((resolve, reject) => {
    ws.once("open", resolve);
    ws.once("error", reject);
  });

  ws.on("message", async (data) => {
    let msg;
    try { msg = JSON.parse(data.toString()); } catch (_) { return; }
    if (msg.id !== undefined && pending.has(msg.id)) {
      const p = pending.get(msg.id);
      pending.delete(msg.id);
      if (msg.error) p.reject(new Error(JSON.stringify(msg.error)));
      else p.resolve(msg.result);
      return;
    }
    if (msg.method === "Target.attachedToTarget") {
      const { sessionId, targetInfo, waitingForDebugger } = msg.params;
      sessions.set(sessionId, { type: targetInfo.type, url: targetInfo.url, title: targetInfo.title });
      emitter.emit("attached", { type: targetInfo.type, url: targetInfo.url, title: targetInfo.title, sessionId });
      try {
        // Recursive auto-attach so nested workers also catch the hook.
        await send("Target.setAutoAttach", { autoAttach: true, waitForDebuggerOnStart: true, flatten: true }, sessionId);
        // Enable Runtime so we can evaluate + receive consoleAPICalled.
        await send("Runtime.enable", {}, sessionId);
        // Inject the hook script. Run it in the default context once
        // the target is up enough to take eval.
        await send("Runtime.evaluate", { expression: hookScript, includeCommandLineAPI: false, awaitPromise: false }, sessionId);
      } catch (e) {
        emitter.emit("inject-error", { sessionId, url: targetInfo.url, err: String(e.message) });
      } finally {
        // Resume the worker if it was paused.
        if (waitingForDebugger) {
          try { await send("Runtime.runIfWaitingForDebugger", {}, sessionId); } catch (_) {}
        }
      }
      return;
    }
    if (msg.method === "Target.detachedFromTarget") {
      sessions.delete(msg.params.sessionId);
      return;
    }
    if (msg.method === "Runtime.consoleAPICalled") {
      const { sessionId } = msg;
      const target = sessions.get(sessionId);
      const args = msg.params.args || [];
      const first = args[0];
      if (!first || typeof first.value !== "string" || !first.value.startsWith(COBALT_MAGIC + " ")) return;
      let payload;
      try { payload = JSON.parse(first.value.slice(COBALT_MAGIC.length + 1)); } catch (_) { return; }
      if (!payload) return;
      payload._targetUrl = target ? target.url : "?";
      payload._targetTitle = target ? target.title : "?";
      payload._sessionId = sessionId;
      if (payload.phase === "aes") emitter.emit("aes", payload);
      else if (payload.phase === "aes-init") emitter.emit("aes-init", payload);
      else if (typeof payload.phase === "string" && payload.phase.startsWith("install")) emitter.emit("install", payload);
      else emitter.emit("event", payload);
      return;
    }
  });

  // Set the root auto-attach on the browser target. This catches all
  // pages and (via recursive setAutoAttach in the handler above) all
  // workers spawned under them.
  await send("Target.setAutoAttach", { autoAttach: true, waitForDebuggerOnStart: true, flatten: true });
  emitter.emit("ready", { label, cdpPort });

  emitter.close = function () {
    try { ws.close(); } catch (_) {}
  };
  return emitter;
}

// ------ CLI mode for ad-hoc testing ------
if (import.meta.url === `file://${process.argv[1].replace(/\\/g, "/")}` || process.argv[1].endsWith("wasm-aes-instrumenter.mjs")) {
  const port = Number(process.argv[2]);
  if (!port) {
    console.error("usage: wasm-aes-instrumenter.mjs <cdp-port> [aesTableSlot]");
    process.exit(1);
  }
  const slot = Number(process.argv[3]) || 4946;
  const inst = await setupInstrumenter({ cdpPort: port, label: "cli", aesTableSlot: slot });
  inst.on("attached", (e) => console.log("[attached]", e.type, e.url));
  inst.on("install", (e) => console.log("[install]", e.phase, JSON.stringify(e).slice(0, 200)));
  inst.on("aes", (e) => console.log("[aes]", "ptr=" + e.statePtr, "pt=" + (e.pt || "").slice(0, 40)));
  inst.on("inject-error", (e) => console.log("[inject-error]", e.url, e.err));
  console.log("instrumenter running, press ctrl-c to stop");
  await new Promise(() => {});
}
