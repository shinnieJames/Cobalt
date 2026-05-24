// capture-postfilter-url.js
//
// Browser-page hook that captures the ExecuTorch postfilter
// model URL when the Voip WASM requests it via the
// `get_bwe_ml_model_path_js` env import. The model isn't
// shipped inside the WASM data section (verified via
// ExecuTorchModelExtractTest); the WASM is wired to the
// ExecuTorch runtime (XnnpackBackend, MmapDataLoader, ~30
// executorch::* symbols) but the .pte is fetched at runtime
// from a URL the JS shim returns.
//
// Once the URL is captured the .pte can be downloaded
// out-of-band, parsed via the official ExecuTorch tooling,
// and either run via JNI or re-implemented in pure Java —
// see task #81 in the project task list.
//
// **USAGE**
//
//   1. Load this script via CDP Page.addScriptToEvaluateOnNewDocument
//      (per the prehook pattern).
//   2. Start a VoIP call so the codec attempts to load the
//      postfilter model.
//   3. Read the captured URL via globalThis.__mlowPostfilterUrl.
//
// **DESIGN**
//
// The Voip worker exposes the host bridge as a property on
// the Worker's globalThis (the embedding-specific name varies
// — try `Module.env`, `wasmEnv`, `globalThis.__voipEnv`, the
// install-mlow-allworkers-prehook.js sets up these aliases).
// We monkey-patch `get_bwe_ml_model_path_js`: when the WASM
// calls it, the wrapper records the returned string and
// forwards the call.
//
// If `get_bwe_ml_model_path_js` is registered as a wasm
// import only (no JS object reference exposed), the alternative
// is to set a CDP breakpoint at the WASM call site that loads
// the model path string and read the returned UTF-8 from heap.

(() => {
    if (globalThis.__mlowPostfilterUrlInstalled) return;
    globalThis.__mlowPostfilterUrlInstalled = true;

    // Captured URLs in observation order. Some codec configs
    // load multiple models (one for each bandwidth band);
    // we record all distinct paths so callers can pick the
    // postfilter-specific one by inspection.
    const urls = [];
    const seen = new Set();

    function capture(url, source) {
        if (typeof url !== 'string' || url.length === 0) return;
        if (seen.has(url)) return;
        seen.add(url);
        urls.push({ ts: Date.now(), source, url });
        console.log('[postfilter-url-capture]', source, '→', url);
    }

    // Find the env table (set up by install-mlow-allworkers-prehook
    // under any of these names).
    function findEnv() {
        for (const name of ['__voipEnv', 'wasmEnv', 'env']) {
            const e = globalThis[name];
            if (e && typeof e === 'object'
                    && typeof e.get_bwe_ml_model_path_js === 'function') {
                return e;
            }
        }
        return null;
    }

    function tryWrap() {
        const env = findEnv();
        if (!env) return false;
        const orig = env.get_bwe_ml_model_path_js;
        env.get_bwe_ml_model_path_js = function (...args) {
            const result = orig.apply(this, args);
            capture(result, 'env.get_bwe_ml_model_path_js');
            return result;
        };
        return true;
    }

    // Try once now, retry on a short timer for late-init
    // workers (the env table is built during ctor drain).
    if (!tryWrap()) {
        let attempts = 0;
        const interval = setInterval(() => {
            attempts++;
            if (tryWrap() || attempts > 100) clearInterval(interval);
        }, 50);
    }

    // Network-path fallback: capture any URL whose path or
    // headers look like an ExecuTorch model fetch. The codec
    // may bypass the env import entirely and grab the .pte
    // through fetch/XHR — this catches that path. We look for
    // .pte/.bin extensions plus the static.whatsapp.net /
    // mmg.whatsapp.net hosts the WAM strings reference. Hooks
    // are no-ops on the data path: we observe and forward.
    const URL_HINT_RE = /\.(pte|bin)(\?|#|$)|whatsapp\.net|\/mlow\/|postfilter|model_/i;
    const captureIfModel = (url, source) => {
        if (typeof url !== 'string') return;
        if (URL_HINT_RE.test(url)) capture(url, source);
    };
    if (typeof globalThis.fetch === 'function') {
        const origFetch = globalThis.fetch.bind(globalThis);
        globalThis.fetch = function (input, init) {
            try {
                if (typeof input === 'string') {
                    captureIfModel(input, 'fetch');
                } else if (input && typeof input.url === 'string') {
                    captureIfModel(input.url, 'fetch.Request');
                }
            } catch (e) {}
            return origFetch(input, init);
        };
    }
    if (typeof globalThis.XMLHttpRequest === 'function') {
        const proto = globalThis.XMLHttpRequest.prototype;
        const origOpen = proto.open;
        proto.open = function (method, url) {
            try { captureIfModel(url, 'xhr.open'); } catch (e) {}
            return origOpen.apply(this, arguments);
        };
    }

    // Same flush convention as capture-mlow-cdf-trace.js.
    globalThis.__mlowPostfilterUrl = urls;
    globalThis.__mlowPostfilterUrlFlush = () => JSON.stringify({
        capturedAt: Date.now(),
        urls,
    });
    globalThis.__mlowPostfilterUrlStage = () => {
        globalThis.__mlowDumpJson = globalThis.__mlowPostfilterUrlFlush();
        return globalThis.__mlowDumpJson.length;
    };

    console.log('[postfilter-url-capture] hook armed; waiting for '
            + 'get_bwe_ml_model_path_js to be called');
})();
