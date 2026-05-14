import type { ParsedModule, ParsedNativeModule } from "../types/module.js";
import type { SnapshotPlatform } from "../types/snapshot.js";
import type { PlatformBridge } from "../types/bridge.js";
import { connectToPlatform } from "../bridge/connect.js";
import { parseModules } from "./parser.js";
import { createLogger } from "../utils/logger.js";

const log = createLogger("extractor:browser");

const PAGE_LOAD_TIMEOUT = 60_000;
const WAIT_AFTER_LOAD = 5_000;
const LAZY_CHUNK_BATCH_SIZE = 50;
const LAZY_CHUNK_SETTLE_WAIT = 10_000;
const WASM_TRIGGER_TIMEOUT = 15_000;
const WASM_SETTLE_WAIT = 5_000;
const MAX_RETRIES = 3;
const RETRY_DELAY = 5_000;

interface WasmTrigger {
  name: string;
  script: string;
}

const WASM_TRIGGERS: WasmTrigger[] = [
  {
    name: "voip",
    script: `(async () => {
      try {
        if (typeof require !== 'function') return;
        const loader = require("WAWebVoipWebWasmVariantLoader");
        if (loader && loader.loadVoipWasmVariant) {
          await loader.loadVoipWasmVariant();
        }
      } catch(e) {}
    })()`,
  },
];

interface CapturedResponses {
  jsUrls: string[];
  wasmCaptures: Map<string, Buffer>;
}

async function extractRevision(bridge: PlatformBridge): Promise<string> {
  return bridge.evaluate(() => {
    if (typeof require !== "function") {
      throw new Error(
        "Could not extract client_revision from WhatsApp Web: require is not a function"
      );
    }

    const siteData = require("SiteData");
    if (!siteData) {
      throw new Error(
        "Could not extract client_revision from WhatsApp Web: no SiteData found"
      );
    }

    const result = siteData.client_revision?.toString();
    if (!result) {
      throw new Error(
        "Could not extract client_revision from WhatsApp Web: no client_revision found"
      );
    }

    return result;
  });
}

async function forceLoadAllChunks(bridge: PlatformBridge): Promise<void> {
  await bridge.evaluate((batchSize: number) => {
    if (typeof require !== "function") return;
    try {
      const Bootloader = require("Bootloader");
      if (!Bootloader?.__debug?.revMap || !Bootloader.loadResources) return;
      const loaded = Bootloader.__debug.DOMAppendedJSHashes;
      const all = [...Bootloader.__debug.revMap.keys()];
      const pending =
        loaded instanceof Set
          ? all.filter((h: string) => !loaded.has(h))
          : all;
      for (let i = 0; i < pending.length; i += batchSize) {
        try {
          Bootloader.loadResources(pending.slice(i, i + batchSize));
        } catch {}
      }
    } catch {}
  }, LAZY_CHUNK_BATCH_SIZE);
  await new Promise((resolve) => setTimeout(resolve, LAZY_CHUNK_SETTLE_WAIT));
}

async function discoverBxDataWasmUrls(
  bridge: PlatformBridge
): Promise<string[]> {
  return bridge.evaluate(() => {
    const urls: string[] = [];
    const pattern =
      /"uri"\s*:\s*"(https:\\\/\\\/static\.whatsapp\.net\\\/rsrc\.php\\\/[^"]+\.wasm)"/g;
    for (const script of Array.from(
      document.querySelectorAll("script:not([src])")
    )) {
      const text = script.textContent ?? "";
      let m: RegExpExecArray | null;
      while ((m = pattern.exec(text))) {
        urls.push(m[1].replace(/\\\//g, "/"));
      }
    }
    return urls;
  });
}

async function runWasmTriggers(bridge: PlatformBridge): Promise<void> {
  for (const trigger of WASM_TRIGGERS) {
    try {
      await Promise.race([
        bridge.evaluate(trigger.script),
        new Promise((resolve) => setTimeout(resolve, WASM_TRIGGER_TIMEOUT)),
      ]);
    } catch {
      // Expected — triggers may fail if modules are unavailable
    }
  }
  await new Promise((resolve) => setTimeout(resolve, WASM_SETTLE_WAIT));
}

async function collectResponses(
  bridge: PlatformBridge
): Promise<CapturedResponses> {
  const jsUrls = new Set<string>();
  const wasmCaptures = new Map<string, Buffer>();
  const pendingCaptures: Promise<void>[] = [];

  // Discover already-loaded resources (relevant for desktop platforms
  // where the page is already loaded before we connect)
  const alreadyLoaded = await bridge.getLoadedResourceUrls();
  for (const url of alreadyLoaded.jsUrls) jsUrls.add(url);
  for (const url of alreadyLoaded.wasmUrls) {
    pendingCaptures.push(
      bridge
        .fetchBinary(url)
        .then((buf) => {
          wasmCaptures.set(url, buf);
        })
        .catch(() => {})
    );
  }

  // Listen for new responses during lazy loading
  bridge.onResponse((response) => {
    if (response.url.endsWith(".js")) {
      jsUrls.add(response.url);
    }
    const isWasm =
      response.url.endsWith(".wasm") ||
      response.contentType.includes("application/wasm");
    if (isWasm) {
      pendingCaptures.push(
        response
          .getBody()
          .then((body) => {
            wasmCaptures.set(response.url, body);
          })
          .catch(() => {})
      );
    }
  });

  // For web platform: navigate (listeners are already set up, so all
  // responses from the initial page load will be captured)
  if (bridge.platform === "web") {
    await bridge.navigate("https://web.whatsapp.com/", {
      timeout: PAGE_LOAD_TIMEOUT,
    });
    await new Promise((resolve) => setTimeout(resolve, WAIT_AFTER_LOAD));
  }

  // Force load lazy chunks
  await forceLoadAllChunks(bridge);

  // Discover and fetch WASM URLs embedded in inline scripts
  const bxWasmUrls = await discoverBxDataWasmUrls(bridge);
  for (const url of bxWasmUrls) {
    if (wasmCaptures.has(url)) continue;
    pendingCaptures.push(
      bridge
        .fetchBinary(url)
        .then((buf) => {
          wasmCaptures.set(url, buf);
        })
        .catch(() => {})
    );
  }

  // Trigger runtime WASM loading (voip, etc.)
  await runWasmTriggers(bridge);

  await Promise.all(pendingCaptures);
  bridge.removeResponseListeners();

  return { jsUrls: [...jsUrls], wasmCaptures };
}

function deriveNativeName(url: string): string {
  try {
    const pathname = new URL(url).pathname;
    const segments = pathname.split("/");
    const filename = segments[segments.length - 1] ?? "";
    const name = filename.replace(/\.wasm$/, "");
    return name || `wasm-${Date.now()}`;
  } catch {
    return `wasm-${Date.now()}`;
  }
}

async function fetchWithRetry(url: string): Promise<string> {
  for (let attempt = 1; attempt <= MAX_RETRIES; attempt += 1) {
    try {
      const res = await fetch(url);
      if (!res.ok) {
        throw new Error(`HTTP ${res.status}`);
      }
      return res.text();
    } catch (error) {
      if (attempt === MAX_RETRIES) throw error;
      await new Promise((resolve) => setTimeout(resolve, RETRY_DELAY));
    }
  }
  throw new Error("Unreachable retry state");
}

async function parseCollectedResponses(
  jsUrls: string[],
  wasmCaptures: Map<string, Buffer>
): Promise<[ParsedModule[], ParsedNativeModule[]]> {
  const allModules: ParsedModule[] = [];
  const results = await Promise.all(
    jsUrls.map(async (url) => {
      try {
        const content = await fetchWithRetry(url);
        return parseModules(content);
      } catch {
        return [] as ParsedModule[];
      }
    })
  );

  for (const modules of results) {
    allModules.push(...modules);
  }

  const nativeModules: ParsedNativeModule[] = [];
  for (const [url, binary] of wasmCaptures) {
    nativeModules.push({
      name: deriveNativeName(url),
      url,
      binary,
    });
  }

  return [allModules, nativeModules];
}

function assertJsPlatform(platform: SnapshotPlatform): void {
  if (platform !== "web" && platform !== "desktop_windows") {
    throw new Error(
      `JS-bundle extractor does not support platform="${platform}". ` +
        `Use the native Mach-O pipeline (extractNativeMachOModules) instead.`
    );
  }
}

export async function checkRevision(
  platform: SnapshotPlatform = "web"
): Promise<string> {
  assertJsPlatform(platform);
  log.info(`checking revision for platform=${platform}`);
  const bridge = await connectToPlatform(platform);
  try {
    if (bridge.platform === "web") {
      log.debug("navigating to web.whatsapp.com");
      await bridge.navigate("https://web.whatsapp.com/", {
        timeout: PAGE_LOAD_TIMEOUT,
      });
      await new Promise((resolve) => setTimeout(resolve, WAIT_AFTER_LOAD));
    }
    const revision = await extractRevision(bridge);
    log.info(`revision check result: ${revision}`);
    return revision;
  } finally {
    await bridge.disconnect();
  }
}

export async function extractModules(
  platform: SnapshotPlatform = "web"
): Promise<[string, ParsedModule[], ParsedNativeModule[]]> {
  assertJsPlatform(platform);
  log.info(`extracting modules for platform=${platform}`);
  const bridge = await connectToPlatform(platform);
  try {
    log.debug("collecting responses (JS + WASM)");
    const { jsUrls, wasmCaptures } = await collectResponses(bridge);
    log.info(`collected ${jsUrls.length} JS URLs, ${wasmCaptures.size} WASM binaries`);
    const revision = await extractRevision(bridge);
    log.info(`extracted revision: ${revision}`);
    await bridge.disconnect();

    log.debug("parsing collected responses");
    const [allModules, nativeModules] = await parseCollectedResponses(
      jsUrls,
      wasmCaptures
    );
    log.info(`parsed ${allModules.length} modules, ${nativeModules.length} native modules`);
    return [revision, allModules, nativeModules];
  } catch (error) {
    log.error("extraction failed", error);
    await bridge.disconnect();
    throw error;
  }
}
