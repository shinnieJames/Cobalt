import { chromium, type BrowserContext, type Page } from "playwright";
import { fetchBinary, fetchText, sleep } from "./http.js";
import { extractBootstrapUrl, extractVersion } from "./service-worker.js";
import { scanForWasmUrls } from "./url-scanner.js";
import {
    forceLoadAllChunks,
    installCaptureHooks,
    listStashedWasmModuleKeys,
    probeWasmLoaders,
    readCapturedWasmUrls,
    snapshotHeap,
} from "./inject.js";

const BASE_URL = "https://web.whatsapp.com";
const PAGE_LOAD_TIMEOUT_MS = 60_000;
const WAIT_AFTER_LOAD_MS = 5_000;
const LAZY_CHUNK_BATCH_SIZE = 50;
const LAZY_CHUNK_SETTLE_WAIT_MS = 10_000;
const WASM_LOADER_TIMEOUT_MS = 15_000;
const WASM_SETTLE_WAIT_MS = 5_000;

/** A downloaded JavaScript chunk served by WhatsApp Web. */
export interface JsChunk {
    readonly url: string;
    readonly content: string;
}

/** A downloaded {@code .wasm} module served by WhatsApp Web. */
export interface WasmBinary {
    readonly url: string;
    readonly data: Uint8Array;
    /**
     * Optional post-instantiation memory snapshot of the wasm module, captured
     * by reading {@code module.HEAPU8} after an Emscripten loader returns. Used
     * for wasms compiled with passive data segments (e.g. pthread-enabled
     * builds), where the static data section is empty and memory is populated
     * at runtime via {@code memory.init} instructions.
     */
    readonly memorySnapshot?: Uint8Array;
}

/** Everything captured from a WhatsApp Web page load. */
export interface FetchResult {
    /** The WhatsApp Web version, e.g. {@code "2.3000.1039683107"}. */
    readonly version: string;
    /** Every {@code .js} resource downloaded during the page load. */
    readonly chunks: readonly JsChunk[];
    /** Every {@code .wasm} resource downloaded. */
    readonly wasmBinaries: readonly WasmBinary[];
}

interface PageCaptureState {
    readonly jsUrls: Set<string>;
    readonly wasmUrls: Set<string>;
    readonly wasmMemorySnapshots: Map<string, Uint8Array>;
}

/**
 * Launches Chromium, navigates to WhatsApp Web, captures every {@code .js}
 * and {@code .wasm} URL the page references, force-loads every JS chunk
 * known to the BDLDR, probes every wasm-related JS module to trigger its
 * wasm dependency, downloads all resources, and returns them.
 *
 * @returns a {@link FetchResult} with the version, JS chunks, and wasm binaries.
 *
 * @remarks
 * Chromium must run headed: in headless mode WA Web's bundle refuses to
 * progress past the splash screen, which is where most chunks are requested.
 */
export async function fetchWhatsAppWeb(): Promise<FetchResult> {
    console.log("[INFO] Launching browser...");
    const browser = await chromium.launch({ headless: false });
    const context = await browser.newContext({ viewport: { width: 1920, height: 1080 } });

    const state: PageCaptureState = {
        jsUrls: new Set(),
        wasmUrls: new Set(),
        wasmMemorySnapshots: new Map(),
    };

    await wireNetworkRoute(context, state);
    await context.addInitScript(installCaptureHooks);

    const page = await context.newPage();

    let pageHtml = "";
    try {
        console.log("[INFO] Loading WhatsApp Web...");
        await page.goto(`${BASE_URL}/`, {
            waitUntil: "networkidle",
            timeout: PAGE_LOAD_TIMEOUT_MS,
        });
        await sleep(WAIT_AFTER_LOAD_MS);

        await page.evaluate(forceLoadAllChunks, LAZY_CHUNK_BATCH_SIZE);
        await sleep(LAZY_CHUNK_SETTLE_WAIT_MS);

        const probeResult = await page.evaluate(probeWasmLoaders, WASM_LOADER_TIMEOUT_MS);
        console.log(`[INFO] Wasm-loader probe: ${probeResult.probed} modules tried, ${probeResult.loaded} Emscripten modules loaded`);
        await sleep(WASM_SETTLE_WAIT_MS);

        await drainCapturedWasmUrls(page, state);
        await drainHeapSnapshots(page, state);

        pageHtml = await page.content();
    } catch (err) {
        const message = err instanceof Error ? err.message : String(err);
        console.warn(`[WARN] Page load issue: ${message}`);
    } finally {
        await context.close();
        await browser.close();
    }

    console.log(`[INFO] Page session: ${state.jsUrls.size} JS files, ${state.wasmUrls.size} wasm files`);

    const version = await fetchVersionAndBootstrap(state);
    const chunks = await downloadChunks(state);
    sweepChunkAndHtmlForWasmUrls(pageHtml, chunks, state);
    const wasmBinaries = await downloadWasmBinaries(state);

    console.log(`[INFO] Downloaded ${chunks.length} JS chunks and ${wasmBinaries.length} wasm modules`);
    return { version, chunks, wasmBinaries };
}

async function wireNetworkRoute(context: BrowserContext, state: PageCaptureState): Promise<void> {
    await context.route("**/*", async (route, request) => {
        const url = request.url();
        if (url.endsWith(".js")) state.jsUrls.add(url);
        else if (url.endsWith(".wasm")) state.wasmUrls.add(url);
        await route.continue();
    });
}

async function drainCapturedWasmUrls(page: Page, state: PageCaptureState): Promise<void> {
    const captured = await page.evaluate(readCapturedWasmUrls);
    for (const u of captured) {
        try {
            state.wasmUrls.add(new URL(u, BASE_URL).toString());
        } catch { /* ignore malformed */ }
    }
    console.log(`[INFO] WebAssembly hook surfaced ${new Set(captured).size} unique wasm URLs (SW cache hits)`);
}

async function drainHeapSnapshots(page: Page, state: PageCaptureState): Promise<void> {
    const keys = await page.evaluate(listStashedWasmModuleKeys);
    for (const key of keys) {
        try {
            const base64 = await page.evaluate(snapshotHeap, key);
            if (!base64) continue;
            const buf = Buffer.from(base64, "base64");
            const absolute = (() => { try { return new URL(key, BASE_URL).toString(); } catch { return key; } })();
            state.wasmMemorySnapshots.set(absolute, new Uint8Array(buf.buffer, buf.byteOffset, buf.byteLength));
            console.log(`[INFO] Captured heap snapshot for ${absolute.split("/").pop()} (${(buf.length / 1_000_000).toFixed(1)} MB)`);
        } catch (e) {
            const m = e instanceof Error ? e.message : String(e);
            console.warn(`[WARN] Heap snapshot failed for ${key}: ${m}`);
        }
    }
}

async function fetchVersionAndBootstrap(state: PageCaptureState): Promise<string> {
    console.log("[INFO] Fetching service worker for version detection...");
    const swSource = await fetchText(`${BASE_URL}/sw.js`);
    const version = extractVersion(swSource);
    console.log(`[INFO] WhatsApp Web version: ${version}`);

    const bootstrapUrl = extractBootstrapUrl(swSource);
    if (bootstrapUrl) {
        state.jsUrls.add(bootstrapUrl);
    } else {
        console.warn("[WARN] No importScripts URL found in sw.js");
    }
    return version;
}

async function downloadChunks(state: PageCaptureState): Promise<JsChunk[]> {
    const chunks: JsChunk[] = [];
    const list = [...state.jsUrls];
    for (let i = 0; i < list.length; i++) {
        const url = list[i]!;
        const filename = url.split("/").pop()?.split("?")[0] ?? url;
        try {
            console.log(`[INFO] [${i + 1}/${list.length}] Downloading ${filename}...`);
            chunks.push({ url, content: await fetchText(url) });
        } catch (err) {
            const message = err instanceof Error ? err.message : String(err);
            console.error(`[ERROR] Failed to download ${filename}: ${message}`);
        }
    }
    return chunks;
}

function sweepChunkAndHtmlForWasmUrls(pageHtml: string, chunks: readonly JsChunk[], state: PageCaptureState): void {
    if (pageHtml) {
        for (const url of scanForWasmUrls(pageHtml)) state.wasmUrls.add(url);
    }
    for (const chunk of chunks) {
        for (const url of scanForWasmUrls(chunk.content)) state.wasmUrls.add(url);
    }
    console.log(`[INFO] Total wasm URLs after manifest+JS scan: ${state.wasmUrls.size}`);
}

async function downloadWasmBinaries(state: PageCaptureState): Promise<WasmBinary[]> {
    const binaries: WasmBinary[] = [];
    const list = [...state.wasmUrls];
    for (let i = 0; i < list.length; i++) {
        const url = list[i]!;
        const filename = url.split("/").pop()?.split("?")[0] ?? url;
        try {
            console.log(`[INFO] [${i + 1}/${list.length}] Downloading ${filename}...`);
            const data = await fetchBinary(url);
            binaries.push({ url, data, memorySnapshot: state.wasmMemorySnapshots.get(url) });
        } catch (err) {
            const message = err instanceof Error ? err.message : String(err);
            console.error(`[ERROR] Failed to download ${filename}: ${message}`);
        }
    }
    return binaries;
}
