/**
 * Page-side scripts injected into the WhatsApp Web context via
 * {@link import("playwright").Page.evaluate} and
 * {@link import("playwright").BrowserContext.addInitScript}.
 *
 * @remarks
 * Every function in this file executes inside the browser, not in Node. They
 * receive only the arguments Playwright explicitly forwards; they cannot close
 * over Node-side variables. Each function is self-contained.
 *
 * Window-level state, written by these scripts and consumed by the orchestrator
 * in {@link import("./browser.ts").fetchWhatsAppWeb}:
 * - {@code window.__WA_CAPTURED_WASM_URLS__}: every wasm URL passed to
 *   {@link WebAssembly.instantiateStreaming}, {@link WebAssembly.compileStreaming},
 *   or {@link fetch} (including service-worker cache hits).
 * - {@code window.__WA_REGISTERED_MODULES__}: every module name passed to
 *   {@code __d(name, ...)} since the bootloader assigned that global.
 * - {@code window.__WA_WASM_MODULES__}: Emscripten module objects keyed by the
 *   wasm URL they loaded.
 */

/**
 * Installs hooks BEFORE WA's bundle runs:
 *  - Wraps {@link WebAssembly.instantiateStreaming}/{@link WebAssembly.compileStreaming}
 *    and {@link fetch} to capture every wasm URL the page requests.
 *  - Installs a property descriptor on {@code window.__d} so every module
 *    registration passes through a wrapper that appends the module name to
 *    {@code window.__WA_REGISTERED_MODULES__}.
 *
 * @remarks
 * Run via {@link import("playwright").BrowserContext.addInitScript} so this
 * code executes before any of WA's own scripts.
 */
export function installCaptureHooks(): void {
    const w = window as Window & {
        __WA_CAPTURED_WASM_URLS__?: string[];
        __WA_REGISTERED_MODULES__?: string[];
        __WA_WASM_MODULES__?: Record<string, { HEAPU8?: Uint8Array }>;
        __d?: unknown;
    };
    if (!w.__WA_CAPTURED_WASM_URLS__) w.__WA_CAPTURED_WASM_URLS__ = [];
    if (!w.__WA_REGISTERED_MODULES__) w.__WA_REGISTERED_MODULES__ = [];
    const captured = w.__WA_CAPTURED_WASM_URLS__;
    const registered = w.__WA_REGISTERED_MODULES__;

    const trackUrl = (src: unknown): void => {
        try {
            let url: string | null = null;
            if (typeof src === "string") url = src;
            else if (src && typeof src === "object" && "url" in (src as { url?: unknown })) {
                const u = (src as { url?: unknown }).url;
                if (typeof u === "string") url = u;
            }
            if (url && url.includes(".wasm")) captured.push(url);
        } catch { /* ignore */ }
    };

    const originalIS = WebAssembly.instantiateStreaming;
    if (originalIS) {
        WebAssembly.instantiateStreaming = function (source, ...rest) {
            Promise.resolve(source).then(trackUrl);
            return originalIS.call(this, source, ...rest);
        };
    }
    const originalCS = WebAssembly.compileStreaming;
    if (originalCS) {
        WebAssembly.compileStreaming = function (source, ...rest) {
            Promise.resolve(source).then(trackUrl);
            return originalCS.call(this, source, ...rest);
        };
    }
    const originalFetch = window.fetch;
    if (originalFetch) {
        window.fetch = function (this: typeof globalThis, input, init) {
            try {
                const url = typeof input === "string" ? input :
                    input instanceof URL ? input.toString() :
                    (input as { url?: string }).url;
                if (url && /\.wasm($|\?)/.test(url)) captured.push(url);
            } catch { /* ignore */ }
            return originalFetch.call(this, input as RequestInfo, init);
        } as typeof fetch;
    }

    let realD: Function | undefined;
    let wrappedD: Function | undefined;
    Object.defineProperty(w, "__d", {
        configurable: true,
        get() { return wrappedD; },
        set(value: unknown) {
            if (typeof value === "function") {
                realD = value as Function;
                wrappedD = function (this: unknown, name: unknown, ...rest: unknown[]) {
                    if (typeof name === "string") {
                        try { registered.push(name); } catch { /* ignore */ }
                    }
                    return (realD as Function).call(this, name, ...rest);
                };
            } else {
                realD = undefined;
                wrappedD = undefined;
            }
        },
    });
}

/**
 * Force-loads every JS chunk the BDLDR knows about, in batches.
 *
 * @param batchSize - number of resource hashes per call to {@code Bootloader.loadResources}.
 *
 * @remarks
 * WA's bootloader exposes its full chunk registry as
 * {@code require("Bootloader").__debug.revMap}. Iterating those hashes and
 * calling {@code loadResources(batch)} triggers each chunk's network fetch,
 * which in turn causes every {@code __d} registration in that chunk to fire.
 * The wrapper installed by {@link installCaptureHooks} records the names.
 */
export async function forceLoadAllChunks(batchSize: number): Promise<void> {
    const req = (globalThis as { require?: Function }).require;
    if (typeof req !== "function") return;
    try {
        const Bootloader = req("Bootloader") as {
            __debug?: { revMap?: Map<string, unknown>; DOMAppendedJSHashes?: Set<string> };
            loadResources?: (hashes: string[]) => void;
        };
        if (!Bootloader?.__debug?.revMap || !Bootloader.loadResources) return;
        const loaded = Bootloader.__debug.DOMAppendedJSHashes;
        const all = [...Bootloader.__debug.revMap.keys()];
        const pending = loaded instanceof Set ? all.filter((h) => !loaded.has(h)) : all;
        for (let i = 0; i < pending.length; i += batchSize) {
            try {
                Bootloader.loadResources(pending.slice(i, i + batchSize));
            } catch { /* ignore */ }
        }
    } catch { /* ignore */ }
}

/** Summary of one wasm-loader probe pass. */
export interface ProbeWasmLoadersResult {
    /** Number of registered modules matching the {@code /wasm/i} filter. */
    readonly probed: number;
    /** Number of probes that returned an Emscripten module. */
    readonly loaded: number;
}

/**
 * Iterates every registered module whose name matches {@code /wasm/i},
 * requires it, and invokes every exported function. Any return value with a
 * {@code HEAPU8} property is recognised as an Emscripten module and stashed
 * in {@code window.__WA_WASM_MODULES__}, keyed by the most recently captured
 * wasm URL (the URL that this loader most likely fetched).
 *
 * @param timeoutMs - per-call timeout. Individual loader functions that hang
 *                    are abandoned after this many milliseconds; the iteration
 *                    continues with the next function.
 * @returns the probe summary.
 */
export async function probeWasmLoaders(timeoutMs: number): Promise<ProbeWasmLoadersResult> {
    const w = window as Window & {
        __WA_REGISTERED_MODULES__?: string[];
        __WA_CAPTURED_WASM_URLS__?: string[];
        __WA_WASM_MODULES__?: Record<string, { HEAPU8?: Uint8Array }>;
    };
    const req = (globalThis as { require?: Function }).require;
    if (typeof req !== "function") return { probed: 0, loaded: 0 };

    const registered = w.__WA_REGISTERED_MODULES__ ?? [];
    const uniqueNames = Array.from(new Set(registered.filter((n) => /wasm/i.test(n))));
    w.__WA_WASM_MODULES__ = w.__WA_WASM_MODULES__ ?? {};
    let loaded = 0;

    const withTimeout = <T,>(p: Promise<T>): Promise<T | undefined> =>
        Promise.race([
            p,
            new Promise<undefined>((resolve) => setTimeout(() => resolve(undefined), timeoutMs)),
        ]);

    for (const name of uniqueNames) {
        let mod: Record<string, unknown> | undefined;
        try { mod = req(name) as Record<string, unknown>; } catch { continue; }
        if (!mod || typeof mod !== "object") continue;
        for (const key of Object.keys(mod)) {
            const fn = mod[key];
            if (typeof fn !== "function") continue;
            let result: unknown;
            try {
                result = await withTimeout(Promise.resolve((fn as () => unknown)()));
            } catch { continue; }
            if (result && typeof result === "object" && (result as { HEAPU8?: unknown }).HEAPU8 instanceof Uint8Array) {
                const urls = w.__WA_CAPTURED_WASM_URLS__ ?? [];
                const urlKey = urls[urls.length - 1] ?? `${name}:${key}`;
                w.__WA_WASM_MODULES__[urlKey] = result as { HEAPU8: Uint8Array };
                loaded++;
            }
        }
    }
    return { probed: uniqueNames.length, loaded };
}

/** Returns the list of wasm URLs surfaced via {@link installCaptureHooks}. */
export function readCapturedWasmUrls(): string[] {
    const w = window as Window & { __WA_CAPTURED_WASM_URLS__?: string[] };
    return w.__WA_CAPTURED_WASM_URLS__ ?? [];
}

/** Returns the keys (wasm URLs) of {@code window.__WA_WASM_MODULES__}. */
export function listStashedWasmModuleKeys(): string[] {
    const w = window as Window & { __WA_WASM_MODULES__?: Record<string, { HEAPU8?: Uint8Array }> };
    return Object.keys(w.__WA_WASM_MODULES__ ?? {});
}

/**
 * Returns a base64-encoded snapshot of the {@code HEAPU8} of the stashed
 * Emscripten module identified by {@code key}, or {@code null} if no module
 * is stored under that key.
 *
 * @param key - the wasm URL the loader fetched.
 * @returns the base64-encoded heap, or {@code null}.
 *
 * @remarks
 * {@link btoa} requires Latin-1 input; the heap is encoded in 32 KiB chunks
 * to avoid the {@code apply()} argument-count cap on large arrays.
 */
export function snapshotHeap(key: string): string | null {
    const w = window as Window & { __WA_WASM_MODULES__?: Record<string, { HEAPU8?: Uint8Array }> };
    const heap = w.__WA_WASM_MODULES__?.[key]?.HEAPU8;
    if (!heap) return null;
    let s = "";
    const chunk = 0x8000;
    for (let i = 0; i < heap.length; i += chunk) {
        s += String.fromCharCode.apply(null, Array.from(heap.subarray(i, i + chunk)));
    }
    return btoa(s);
}
