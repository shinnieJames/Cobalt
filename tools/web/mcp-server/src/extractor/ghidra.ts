import { execFile } from "node:child_process";
import { access, readFile, mkdtemp, rm } from "node:fs/promises";
import { join, dirname } from "node:path";
import { tmpdir } from "node:os";
import { fileURLToPath } from "node:url";
import { createLogger } from "../utils/logger.js";

const log = createLogger("ghidra");

const CURRENT_DIR = dirname(fileURLToPath(import.meta.url));
const SCRIPTS_DIR = join(CURRENT_DIR, "..", "..", "scripts");
const GHIDRA_SCRIPT_NAME = "DecompileToJson.java";

const DEFAULT_ANALYSIS_TIMEOUT_SEC = 1800;
const DEFAULT_MAX_CPU = 4;

export interface GhidraOptions {
  ghidraPath?: string;
  analysisTimeoutSec?: number;
  maxCpu?: number;
  processor?: string;
  compilerSpec?: string;
}

export interface GhidraDecompiledFunction {
  name: string;
  address: string;
  signature: string;
  size: number;
  isThunk: boolean;
  isExternal: boolean;
  decompiled: boolean;
  code: string;
}

export interface GhidraOutput {
  binary: string;
  architecture: string;
  compiler: string;
  functionCount: number;
  functions: GhidraDecompiledFunction[];
}

export async function findGhidraInstallation(
  explicitPath?: string
): Promise<string> {
  // 1. Explicit path
  if (explicitPath) {
    const headless = analyzeHeadlessPath(explicitPath);
    if (await fileExists(headless)) return explicitPath;
    throw new Error(`Ghidra not found at: ${explicitPath}`);
  }

  // 2. GHIDRA_INSTALL_DIR env var
  const envPath = process.env.GHIDRA_INSTALL_DIR;
  if (envPath) {
    const headless = analyzeHeadlessPath(envPath);
    if (await fileExists(headless)) return envPath;
  }

  // 3. Common locations
  const candidates = [
    // macOS Homebrew
    "/opt/homebrew/Caskroom/ghidra",
    // Linux common
    "/opt/ghidra",
    "/usr/share/ghidra",
    // Home directory
    join(process.env.HOME ?? "", "ghidra"),
  ];

  for (const candidate of candidates) {
    const headless = analyzeHeadlessPath(candidate);
    if (await fileExists(headless)) return candidate;
    // Homebrew installs in versioned subdirs
    try {
      const { readdir } = await import("node:fs/promises");
      const entries = await readdir(candidate);
      for (const entry of entries) {
        const subPath = join(candidate, entry);
        // Look for ghidra_*_PUBLIC dirs
        const nested = await readdir(subPath).catch(() => []);
        for (const n of nested) {
          if (n.startsWith("ghidra_")) {
            const ghidraDir = join(subPath, n);
            if (await fileExists(analyzeHeadlessPath(ghidraDir))) return ghidraDir;
          }
        }
      }
    } catch {
      // Directory doesn't exist
    }
  }

  // 4. Try PATH (analyzeHeadless might be on the system path)
  try {
    await execFileAsync("analyzeHeadless", ["--help"]);
    return ""; // Empty path means it's on PATH
  } catch {
    // Not on PATH
  }

  throw new Error(
    "Ghidra installation not found. Set GHIDRA_INSTALL_DIR environment variable " +
      "or install Ghidra: brew install --cask ghidra (macOS) or download from https://ghidra-sre.org"
  );
}

export async function decompileBinary(
  binaryPath: string,
  options: GhidraOptions = {}
): Promise<GhidraOutput> {
  const ghidraDir = await findGhidraInstallation(options.ghidraPath);
  const headless = ghidraDir
    ? analyzeHeadlessPath(ghidraDir)
    : "analyzeHeadless";

  const projectDir = await mkdtemp(join(tmpdir(), "ghidra-"));
  const outputPath = join(projectDir, "decompiled.json");

  const args = [
    projectDir,
    "TempProject",
    "-import",
    binaryPath,
    "-processor",
    options.processor ?? "AARCH64:LE:64:v8A",
    "-cspec",
    options.compilerSpec ?? "default",
    "-scriptPath",
    SCRIPTS_DIR,
    "-postScript",
    GHIDRA_SCRIPT_NAME,
    outputPath,
    "-analysisTimeoutPerFile",
    String(options.analysisTimeoutSec ?? DEFAULT_ANALYSIS_TIMEOUT_SEC),
    "-max-cpu",
    String(options.maxCpu ?? DEFAULT_MAX_CPU),
    "-deleteProject",
  ];

  log.info(`running headless analysis on ${binaryPath}`);
  log.debug(`command: ${headless} ${args.join(" ")}`);

  try {
    const { stdout, stderr } = await execFileAsync(headless, args, {
      timeout: (options.analysisTimeoutSec ?? DEFAULT_ANALYSIS_TIMEOUT_SEC) * 1000 * 2,
      maxBuffer: 50 * 1024 * 1024,
    });

    if (stderr) {
      // Ghidra writes progress to stderr — extract key messages
      const lines = stderr.split("\n");
      for (const line of lines) {
        if (
          line.includes("Decompiled ") ||
          line.includes("Done.") ||
          line.includes("ERROR") ||
          line.includes("WARN")
        ) {
          log.debug(line.trim());
        }
      }
    }
  } catch (error) {
    await rm(projectDir, { recursive: true, force: true }).catch(() => {});
    const message =
      error instanceof Error ? error.message : String(error);
    throw new Error(`Ghidra analysis failed: ${message}`);
  }

  if (!(await fileExists(outputPath))) {
    await rm(projectDir, { recursive: true, force: true }).catch(() => {});
    throw new Error(
      "Ghidra analysis completed but no output file was produced. " +
        "The binary may be encrypted or unsupported."
    );
  }

  const raw = await readFile(outputPath, "utf8");
  await rm(projectDir, { recursive: true, force: true }).catch(() => {});

  return JSON.parse(raw) as GhidraOutput;
}

export interface WasmDecompileResult {
  funcIndex: number;
  name: string;
  address: string;
  signature: string;
  decompiled: boolean;
  code: string;
}

/**
 * Decompiles a single WASM function to C pseudocode via Ghidra headless and the
 * nneonneo ghidra-wasm-plugin. The WasmLoader auto-detects the {@code \0asm}
 * magic and binds language {@code Wasm:LE:32:default}, so no {@code -processor}
 * is passed (unlike {@link decompileBinary}, which targets ARM64 native binaries
 * and is left unchanged). Requires the plugin to be installed in the Ghidra
 * Extensions directory; if Ghidra or the plugin is absent this rejects, and the
 * caller should fall back to WAT.
 *
 * @param wasmPath path to the {@code .wasm} file
 * @param funcIndex the WASM function index to decompile
 * @param options Ghidra discovery and resource options
 */
export async function decompileWasmFunction(
  wasmPath: string,
  funcIndex: number,
  options: GhidraOptions = {}
): Promise<WasmDecompileResult> {
  const ghidraDir = await findGhidraInstallation(options.ghidraPath);
  const headless = ghidraDir ? analyzeHeadlessPath(ghidraDir) : "analyzeHeadless";

  const projectDir = await mkdtemp(join(tmpdir(), "ghidra-wasm-"));
  const outputPath = join(projectDir, "func.json");
  const timeoutSec = options.analysisTimeoutSec ?? DEFAULT_ANALYSIS_TIMEOUT_SEC;

  const args = [
    projectDir,
    "WasmProj",
    "-import",
    wasmPath,
    // No -processor: the WasmLoader binds Wasm:LE:32:default automatically.
    "-scriptPath",
    SCRIPTS_DIR,
    "-postScript",
    "DecompileWasmFuncToJson.java",
    String(funcIndex),
    outputPath,
    "-analysisTimeoutPerFile",
    String(timeoutSec),
    "-max-cpu",
    String(options.maxCpu ?? DEFAULT_MAX_CPU),
    "-deleteProject",
  ];

  log.info(`running headless wasm decompile on ${wasmPath} funcIndex=${funcIndex}`);
  try {
    await execFileAsync(headless, args, { timeout: timeoutSec * 1000 * 2, maxBuffer: 50 * 1024 * 1024 });
  } catch (error) {
    await rm(projectDir, { recursive: true, force: true }).catch(() => {});
    const message = error instanceof Error ? error.message : String(error);
    throw new Error(
      `Ghidra wasm decompile failed (is the ghidra-wasm-plugin installed in the Ghidra Extensions dir?): ${message}`
    );
  }

  if (!(await fileExists(outputPath))) {
    await rm(projectDir, { recursive: true, force: true }).catch(() => {});
    throw new Error("Ghidra wasm decompile produced no output (function index out of range or plugin missing).");
  }

  const raw = await readFile(outputPath, "utf8");
  await rm(projectDir, { recursive: true, force: true }).catch(() => {});
  return JSON.parse(raw) as WasmDecompileResult;
}

function analyzeHeadlessPath(ghidraDir: string): string {
  if (process.platform === "win32") {
    return join(ghidraDir, "support", "analyzeHeadless.bat");
  }
  return join(ghidraDir, "support", "analyzeHeadless");
}

async function fileExists(path: string): Promise<boolean> {
  try {
    await access(path);
    return true;
  } catch {
    return false;
  }
}

function execFileAsync(
  command: string,
  args: string[],
  options?: { timeout?: number; maxBuffer?: number }
): Promise<{ stdout: string; stderr: string }> {
  // Node 24 refuses to execFile a .bat/.cmd directly on Windows (the
  // CVE-2024-27980 shell requirement -> spawn EINVAL). Route the batch launcher
  // through the shell with every token double-quoted so space-containing paths
  // (e.g. "New folder", the temp project dir) survive. POSIX is unchanged: the
  // launcher there is a plain script that execs fine without a shell.
  const isWindowsBatch = process.platform === "win32" && /\.(bat|cmd)$/i.test(command);
  const spawnCommand = isWindowsBatch ? `"${command}"` : command;
  const spawnArgs = isWindowsBatch ? args.map((arg) => `"${arg}"`) : args;
  return new Promise((resolve, reject) => {
    execFile(
      spawnCommand,
      spawnArgs,
      {
        timeout: options?.timeout ?? 0,
        maxBuffer: options?.maxBuffer ?? 10 * 1024 * 1024,
        encoding: "utf8",
        shell: isWindowsBatch,
        windowsHide: true,
      },
      (error, stdout, stderr) => {
        if (error) {
          reject(error);
        } else {
          resolve({ stdout: stdout as string, stderr: stderr as string });
        }
      }
    );
  });
}
