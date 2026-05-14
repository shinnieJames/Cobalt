import { createHash } from "node:crypto";
import type { Dirent } from "node:fs";
import { access, mkdir, readdir, readFile, writeFile } from "node:fs/promises";
import { dirname, join } from "node:path";
import type {
  ModuleAnalysis,
} from "../types/analysis.js";
import type { ParsedModule, ParsedNativeModule } from "../types/module.js";
import type {
  SnapshotIndex,
  SnapshotManifest,
  SnapshotModuleRecord,
  SnapshotNativeModuleRecord,
  SnapshotPlatform,
} from "../types/snapshot.js";
import type { WasmAnalysis } from "../types/wasm.js";
import { analyzeWasm } from "../analysis/wasm-analyzer.js";
import {
  ALL_PLATFORMS,
  SCHEMA_VERSION,
  computeGlobalHash,
  ensureDirectories,
  indexPath,
  manifestPath,
  moduleToRecord,
  nativeModuleToRecord,
  nativePath,
  nativesDirPath,
  parseDependencies,
  parseExports,
  platformDir,
  snapshotIdForRevision,
  sourceDirPath,
  sourcePath,
  type SourceFileEntry,
  sanitizeModuleFilename,
} from "./snapshot-utils.js";
import { createLogger } from "../utils/logger.js";

const log = createLogger("storage");

export async function listSnapshots(platform: SnapshotPlatform): Promise<string[]> {
  await ensureDirectories(platform);
  const entries: Dirent[] = await readdir(platformDir(platform), { withFileTypes: true });
  return entries
    .filter((entry: Dirent) => entry.isDirectory())
    .map((entry: Dirent) => entry.name)
    .sort()
    .reverse();
}

export async function listAllSnapshots(): Promise<Array<{ platform: SnapshotPlatform; snapshotId: string }>> {
  const results: Array<{ platform: SnapshotPlatform; snapshotId: string }> = [];
  for (const p of ALL_PLATFORMS) {
    const snapshots = await listSnapshots(p);
    for (const s of snapshots) {
      results.push({ platform: p, snapshotId: s });
    }
  }
  return results;
}

export async function loadManifest(platform: SnapshotPlatform, snapshotId: string): Promise<SnapshotManifest> {
  const raw = await readFile(manifestPath(platform, snapshotId), "utf8");
  return JSON.parse(raw) as SnapshotManifest;
}

export async function loadIndex(platform: SnapshotPlatform, snapshotId: string): Promise<SnapshotIndex> {
  const raw = await readFile(indexPath(platform, snapshotId), "utf8");
  return JSON.parse(raw) as SnapshotIndex;
}

export async function saveIndex(
  platform: SnapshotPlatform,
  snapshotId: string,
  revision: string,
  analyses: ModuleAnalysis[]
): Promise<void> {
  log.info(`saving index: platform=${platform} snapshot=${snapshotId} analyses=${analyses.length}`);
  const payload: SnapshotIndex = {
    schemaVersion: SCHEMA_VERSION,
    snapshotId,
    revision,
    builtAt: new Date().toISOString(),
    analyses,
  };
  const json = JSON.stringify(payload, null, 2);
  await writeFile(indexPath(platform, snapshotId), json, "utf8");
  log.debug(`index saved: ${(json.length / 1024).toFixed(0)}KB written`);
}

export async function loadModuleSource(
  platform: SnapshotPlatform,
  snapshotId: string,
  relativePath: string
): Promise<string> {
  return readFile(sourcePath(platform, snapshotId, relativePath), "utf8");
}

export async function snapshotExists(platform: SnapshotPlatform, snapshotId: string): Promise<boolean> {
  try {
    await access(manifestPath(platform, snapshotId));
    return true;
  } catch {
    return false;
  }
}

export async function indexExists(platform: SnapshotPlatform, snapshotId: string): Promise<boolean> {
  try {
    await access(indexPath(platform, snapshotId));
    return true;
  } catch {
    return false;
  }
}

export async function findSnapshotByRevision(
  platform: SnapshotPlatform,
  revision: string
): Promise<string | null> {
  const snapshots = await listSnapshots(platform);
  for (const snapshotId of snapshots) {
    try {
      const manifest = await loadManifest(platform, snapshotId);
      if (manifest.revision === revision) return snapshotId;
    } catch {
      continue;
    }
  }
  return null;
}

export async function loadLatestSnapshotId(platform: SnapshotPlatform): Promise<string | null> {
  const snapshots = await listSnapshots(platform);
  return snapshots.length ? snapshots[0] : null;
}

export async function createSnapshot(
  platform: SnapshotPlatform,
  revision: string,
  modules: ParsedModule[],
  nativeModules?: ParsedNativeModule[]
): Promise<SnapshotManifest> {
  log.info(`creating snapshot: platform=${platform} revision=${revision} modules=${modules.length} natives=${nativeModules?.length ?? 0}`);
  await ensureDirectories(platform);
  const seenNames = new Map<string, number>();
  const fileEntries = new Map<string, SourceFileEntry>();

  const sorted = modules.slice().sort((a, b) => a.name.localeCompare(b.name));
  const recordsTemp: SnapshotModuleRecord[] = [];

  for (const mod of sorted) {
    const base = sanitizeModuleFilename(mod.name);
    const count = seenNames.get(base) ?? 0;
    seenNames.set(base, count + 1);
    const filename = count === 0 ? `${base}.js` : `${base}_${count}.js`;
    const relativeSourcePath = join("sources", filename);
    const fileEntry: SourceFileEntry = {
      moduleName: mod.name,
      sourcePath: relativeSourcePath,
    };
    fileEntries.set(mod.name, fileEntry);
    recordsTemp.push(moduleToRecord(mod, fileEntry));
  }

  const globalHash = computeGlobalHash(recordsTemp);
  const snapshotId = snapshotIdForRevision(revision, globalHash);

  if (await snapshotExists(platform, snapshotId)) {
    log.info(`snapshot already exists: ${snapshotId}, loading existing manifest`);
    return loadManifest(platform, snapshotId);
  }
  log.info(`creating new snapshot: ${snapshotId}`);

  await mkdir(sourceDirPath(platform, snapshotId), { recursive: true });

  const nativeRecords = await storeNativeModules(platform, snapshotId, nativeModules ?? []);

  const manifest: SnapshotManifest = {
    snapshotId,
    platform,
    revision,
    createdAt: new Date().toISOString(),
    moduleCount: recordsTemp.length,
    globalHash,
    modules: recordsTemp,
    nativeModules: nativeRecords.length > 0 ? nativeRecords : undefined,
  };

  await writeFile(manifestPath(platform, snapshotId), JSON.stringify(manifest, null, 2), "utf8");

  const WRITE_BATCH = 200;
  for (let i = 0; i < sorted.length; i += WRITE_BATCH) {
    await Promise.all(
      sorted.slice(i, i + WRITE_BATCH).map(async (module) => {
        const entry = fileEntries.get(module.name);
        if (!entry) return;
        const fullPath = sourcePath(platform, snapshotId, entry.sourcePath);
        await mkdir(dirname(fullPath), { recursive: true });
        await writeFile(fullPath, module.body, "utf8");
      })
    );
  }

  return manifest;
}

async function storeNativeModules(
  platform: SnapshotPlatform,
  snapshotId: string,
  nativeModules: ParsedNativeModule[]
): Promise<SnapshotNativeModuleRecord[]> {
  if (nativeModules.length === 0) return [];

  await mkdir(nativesDirPath(platform, snapshotId), { recursive: true });

  const records: SnapshotNativeModuleRecord[] = [];
  const seenNames = new Map<string, number>();

  for (const native of nativeModules) {
    const base = sanitizeModuleFilename(native.name);
    const count = seenNames.get(base) ?? 0;
    seenNames.set(base, count + 1);
    const fileName = count === 0 ? base : `${base}_${count}`;
    const contentHash = createHash("sha256").update(native.binary).digest("hex");

    const record = nativeModuleToRecord(native, fileName, contentHash);
    records.push(record);

    const binaryPath = nativePath(platform, snapshotId, record.filePath);
    await writeFile(binaryPath, native.binary);

    let analysis: WasmAnalysis;
    try {
      analysis = analyzeWasm(native.name, native.binary);
    } catch (error) {
      analysis = {
        name: native.name,
        types: [],
        imports: [],
        exports: [],
        functions: [],
        memories: [],
        tables: [],
        globals: [],
        customSections: [],
        sectionSizes: {
          type: 0, import: 0, function: 0, table: 0, memory: 0,
          global: 0, export: 0, start: 0, element: 0, code: 0,
          data: 0, dataCount: 0, custom: {},
        },
        totalSize: native.binary.length,
      };
    }

    const analysisJsonPath = nativePath(platform, snapshotId, record.analysisPath);
    await writeFile(analysisJsonPath, JSON.stringify(analysis, null, 2), "utf8");
  }

  return records;
}

export async function loadNativeModuleBinary(
  platform: SnapshotPlatform,
  snapshotId: string,
  relativePath: string
): Promise<Buffer> {
  return readFile(nativePath(platform, snapshotId, relativePath));
}

export async function loadNativeModuleAnalysis(
  platform: SnapshotPlatform,
  snapshotId: string,
  relativePath: string
): Promise<WasmAnalysis> {
  const raw = await readFile(nativePath(platform, snapshotId, relativePath), "utf8");
  return JSON.parse(raw) as WasmAnalysis;
}

export async function loadParsedModulesFromDirectory(
  dir: string
): Promise<ParsedModule[]> {
  const files: Dirent[] = await readdir(dir, { withFileTypes: true });
  const jsonFiles = files
    .filter((entry: Dirent) => entry.isFile() && entry.name.endsWith(".json"))
    .map((entry: Dirent) => entry.name)
    .filter((name: string) => name !== "WhatsappWeb.json")
    .sort();

  const parsedModules: ParsedModule[] = [];

  for (const jsonFile of jsonFiles) {
    const raw = await readFile(join(dir, jsonFile), "utf8");
    const payload = JSON.parse(raw) as Record<string, unknown> | unknown[];
    if (Array.isArray(payload)) continue;
    const name = typeof payload.name === "string" ? payload.name : null;
    if (!name) continue;

    const jsPath = join(dir, `${name}.js`);
    let body: string;
    try {
      body = await readFile(jsPath, "utf8");
    } catch {
      continue;
    }

    parsedModules.push({
      name,
      dependencies: parseDependencies(payload.dependencies),
      exports: parseExports(payload.exports),
      body,
    });
  }

  return parsedModules;
}
