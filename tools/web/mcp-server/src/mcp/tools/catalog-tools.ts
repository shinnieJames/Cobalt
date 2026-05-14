import type { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import type { SnapshotCatalog } from "../../services/catalog.js";
import type { SnapshotPlatform } from "../../types/snapshot.js";
import {
  getAnnotation,
  listAnnotations,
  removeAnnotation,
  setAnnotation,
} from "../../services/annotations.js";
import { createLogger } from "../../utils/logger.js";

const log = createLogger("tools:catalog");

const platformSchema = z.enum(["web", "desktop_windows", "desktop_macos", "ios"]).optional()
  .describe("Platform to query. Defaults to 'web'.");

interface CatalogToolsContext {
  requireCatalog: (platform?: SnapshotPlatform) => SnapshotCatalog;
  switchCatalog: (platform: SnapshotPlatform, snapshotId: string) => Promise<SnapshotCatalog>;
  listLoadedCatalogs: () => SnapshotCatalog[];
}

export function registerCatalogTools(
  server: McpServer,
  context: CatalogToolsContext
): void {
  const { requireCatalog, switchCatalog, listLoadedCatalogs } = context;

server.tool(
  "search_modules",
  "Full-text fuzzy search over module names, exports, dependencies, symbols, and literals. Use as a starting point to discover modules related to a topic.",
  {
    query: z.string(),
    platform: platformSchema,
    limit: z.number().optional().default(20),
    tolerance: z.number().optional().default(1),
  },
  async ({
    query,
    platform,
    limit,
    tolerance,
  }: {
    query: string;
    platform?: SnapshotPlatform;
    limit: number;
    tolerance: number;
  }) => {
    log.debug(`search_modules: query="${query}" platform=${platform ?? "default"} limit=${limit} tolerance=${tolerance}`);
    const start = performance.now();
    const results = await requireCatalog(platform).searchModules(query, limit, tolerance);
    log.info(`search_modules: query="${query}" returned ${results.length} results in ${(performance.now() - start).toFixed(1)}ms`);
    return {
      content: [{ type: "text" as const, text: JSON.stringify(results, null, 2) }],
    };
  }
);

server.tool(
  "list_modules",
  "Returns the full module universe for the requested platform, or for every loaded platform when no platform is given. Each entry carries name, platform, sourceBytes, exports, dependencies, and sourcePath — enough to bucket modules by name pattern, size, or surface area without fetching individual sources. Prefer this over reading the on-disk manifest.json: it stays in sync with the active snapshot and merges multi-platform catalogs in one call.",
  {
    platform: platformSchema,
  },
  async ({ platform }: { platform?: SnapshotPlatform }) => {
    log.debug(`list_modules: platform=${platform ?? "all"}`);
    const start = performance.now();
    const catalogs = platform ? [requireCatalog(platform)] : listLoadedCatalogs();
    if (catalogs.length === 0) {
      return {
        content: [{ type: "text" as const, text: "No catalogs initialized." }],
        isError: true,
      };
    }
    const modules = catalogs.flatMap((catalog) => catalog.listModules());
    log.info(`list_modules: platform=${platform ?? "all"} returned ${modules.length} modules in ${(performance.now() - start).toFixed(1)}ms`);
    return {
      content: [{ type: "text" as const, text: JSON.stringify(modules, null, 2) }],
    };
  }
);

server.tool(
  "get_module_metadata",
  "Returns module metadata: dependencies, exports, source hash, and byte size. Use to understand a module's role and connections before reading source.",
  {
    name: z.string(),
    platform: platformSchema,
  },
  async ({ name, platform }: { name: string; platform?: SnapshotPlatform }) => {
    log.debug(`get_module_metadata: name="${name}" platform=${platform ?? "default"}`);
    try {
      const result = requireCatalog(platform).getModuleMetadata(name);
      log.info(`get_module_metadata: name="${name}" deps=${result.dependencies.length} exports=${result.exports.length} bytes=${result.sourceBytes}`);
      return {
        content: [{ type: "text" as const, text: JSON.stringify(result, null, 2) }],
      };
    } catch (error) {
      log.warn(`get_module_metadata: name="${name}" error: ${error instanceof Error ? error.message : String(error)}`);
      return {
        content: [{ type: "text" as const, text: error instanceof Error ? error.message : String(error) }],
        isError: true,
      };
    }
  }
);

server.tool(
  "get_exports",
  "Returns export bindings for a module: maps each export name to its local implementation symbol and binding kind (identifier, member expression, computed, or unresolved).",
  {
    name: z.string(),
    platform: platformSchema,
  },
  async ({ name, platform }: { name: string; platform?: SnapshotPlatform }) => {
    log.debug(`get_exports: name="${name}" platform=${platform ?? "default"}`);
    try {
      const result = requireCatalog(platform).getExports(name);
      log.info(`get_exports: name="${name}" returned ${result.length} bindings`);
      return {
        content: [{ type: "text" as const, text: JSON.stringify(result, null, 2) }],
      };
    } catch (error) {
      log.warn(`get_exports: name="${name}" error: ${error instanceof Error ? error.message : String(error)}`);
      return {
        content: [{ type: "text" as const, text: error instanceof Error ? error.message : String(error) }],
        isError: true,
      };
    }
  }
);

server.tool(
  "resolve_export",
  "Resolves a specific module export to its implementation: local symbol name, binding kind, byte range, and source provenance.",
  {
    moduleName: z.string(),
    exportName: z.string(),
    platform: platformSchema,
  },
  async ({
    moduleName,
    exportName,
    platform,
  }: {
    moduleName: string;
    exportName: string;
    platform?: SnapshotPlatform;
  }) => {
    log.debug(`resolve_export: module="${moduleName}" export="${exportName}" platform=${platform ?? "default"}`);
    try {
      const result = await requireCatalog(platform).resolveExport(moduleName, exportName);
      log.info(`resolve_export: module="${moduleName}" export="${exportName}" kind=${result.bindingKind}`);
      return {
        content: [{ type: "text" as const, text: JSON.stringify(result, null, 2) }],
      };
    } catch (error) {
      log.warn(`resolve_export: module="${moduleName}" export="${exportName}" error: ${error instanceof Error ? error.message : String(error)}`);
      return {
        content: [{ type: "text" as const, text: error instanceof Error ? error.message : String(error) }],
        isError: true,
      };
    }
  }
);

server.tool(
  "get_module_source",
  "Returns module source code as raw text. Supports byte-range or line-range slicing. Line-based slicing (1-based, inclusive end) is preferred over byte offsets. Without range parameters returns the full source.",
  {
    name: z.string(),
    platform: platformSchema,
    startByte: z.number().optional().describe("Start byte offset."),
    endByte: z.number().optional().describe("End byte offset."),
    startLine: z.number().optional().describe("1-based start line number (preferred)."),
    endLine: z.number().optional().describe("1-based end line number, inclusive (preferred)."),
  },
  async ({
    name,
    platform,
    startByte,
    endByte,
    startLine,
    endLine,
  }: {
    name: string;
    platform?: SnapshotPlatform;
    startByte?: number;
    endByte?: number;
    startLine?: number;
    endLine?: number;
  }) => {
    const rangeDesc = startLine != null ? `lines=${startLine}-${endLine ?? "end"}` : startByte != null ? `bytes=${startByte}-${endByte ?? "end"}` : "full";
    log.debug(`get_module_source: name="${name}" range=${rangeDesc} platform=${platform ?? "default"}`);
    try {
      const result = await requireCatalog(platform).getModuleSource(name, { startByte, endByte, startLine, endLine });
      log.info(`get_module_source: name="${name}" returned ${result.endByte - result.startByte} bytes (${result.startByte}-${result.endByte})`);
      return {
        content: [{ type: "text" as const, text: result.source }],
      };
    } catch (error) {
      log.warn(`get_module_source: name="${name}" error: ${error instanceof Error ? error.message : String(error)}`);
      return {
        content: [{ type: "text" as const, text: error instanceof Error ? error.message : String(error) }],
        isError: true,
      };
    }
  }
);

server.tool(
  "get_symbol_source",
  "Returns the exact source code for a named symbol (function, class, variable) in a module. Use after get_exports or get_module_metadata to read specific implementations.",
  {
    moduleName: z.string(),
    symbolName: z.string(),
    platform: platformSchema,
  },
  async ({
    moduleName,
    symbolName,
    platform,
  }: {
    moduleName: string;
    symbolName: string;
    platform?: SnapshotPlatform;
  }) => {
    log.debug(`get_symbol_source: module="${moduleName}" symbol="${symbolName}" platform=${platform ?? "default"}`);
    try {
      const result = await requireCatalog(platform).getSymbolSource(moduleName, symbolName);
      log.info(`get_symbol_source: module="${moduleName}" symbol="${symbolName}" ${result.source.length} chars`);
      return {
        content: [{ type: "text" as const, text: result.source }],
      };
    } catch (error) {
      log.warn(`get_symbol_source: module="${moduleName}" symbol="${symbolName}" error: ${error instanceof Error ? error.message : String(error)}`);
      return {
        content: [{ type: "text" as const, text: error instanceof Error ? error.message : String(error) }],
        isError: true,
      };
    }
  }
);

server.tool(
  "find_references",
  "Finds pre-indexed references to an exact symbol name across modules. Only matches symbols indexed during analysis. For pattern-based search use search_code instead.",
  {
    symbol: z.string(),
    platform: platformSchema,
    scope: z.string().optional().describe("Restrict to a single module name."),
    maxResults: z.number().optional().default(50),
  },
  async ({
    symbol,
    platform,
    scope,
    maxResults,
  }: {
    symbol: string;
    platform?: SnapshotPlatform;
    scope?: string;
    maxResults: number;
  }) => {
    log.debug(`find_references: symbol="${symbol}" scope=${scope ?? "all"} maxResults=${maxResults} platform=${platform ?? "default"}`);
    try {
      const start = performance.now();
      const result = await requireCatalog(platform).findReferences(symbol, scope, maxResults);
      log.info(`find_references: symbol="${symbol}" returned ${result.length} refs in ${(performance.now() - start).toFixed(1)}ms`);
      return {
        content: [{ type: "text" as const, text: JSON.stringify(result, null, 2) }],
      };
    } catch (error) {
      log.warn(`find_references: symbol="${symbol}" error: ${error instanceof Error ? error.message : String(error)}`);
      return {
        content: [{ type: "text" as const, text: error instanceof Error ? error.message : String(error) }],
        isError: true,
      };
    }
  }
);

server.tool(
  "trace_dependencies",
  "BFS traversal of the module dependency graph. Forward traces what a module depends on; reverse traces what depends on it.",
  {
    moduleName: z.string(),
    platform: platformSchema,
    depth: z.number().optional().default(3),
    direction: z.enum(["forward", "reverse"]).optional().default("forward"),
  },
  async ({
    moduleName,
    platform,
    depth,
    direction,
  }: {
    moduleName: string;
    platform?: SnapshotPlatform;
    depth: number;
    direction: "forward" | "reverse";
  }) => {
    log.debug(`trace_dependencies: module="${moduleName}" depth=${depth} direction=${direction} platform=${platform ?? "default"}`);
    try {
      const result = requireCatalog(platform).traceDependencies(moduleName, depth, direction);
      log.info(`trace_dependencies: module="${moduleName}" direction=${direction} returned ${result.length} nodes`);
      return {
        content: [{ type: "text" as const, text: JSON.stringify(result, null, 2) }],
      };
    } catch (error) {
      log.warn(`trace_dependencies: module="${moduleName}" error: ${error instanceof Error ? error.message : String(error)}`);
      return {
        content: [{ type: "text" as const, text: error instanceof Error ? error.message : String(error) }],
        isError: true,
      };
    }
  }
);

server.tool(
  "search_code",
  "Cross-module search by regex or literal. Use searchIn='source' for full source code search (slow without scope — provide scope when possible). Use searchIn='literals' to search only pre-indexed string constants (fast, no scope needed).",
  {
    pattern: z.string(),
    platform: platformSchema,
    mode: z.enum(["regex", "literal"]).optional().default("regex"),
    searchIn: z.enum(["source", "literals"]).optional().default("source")
      .describe("'source': full source code search; 'literals': pre-indexed string constants (faster)."),
    scope: z.string().optional().describe("Module name to restrict source search to (recommended for 'source')."),
    maxResults: z.number().optional().default(20),
  },
  async ({
    pattern,
    platform,
    mode,
    searchIn,
    scope,
    maxResults,
  }: {
    pattern: string;
    platform?: SnapshotPlatform;
    mode: "regex" | "literal";
    searchIn: "source" | "literals";
    scope?: string;
    maxResults: number;
  }) => {
    log.debug(`search_code: pattern="${pattern}" mode=${mode} searchIn=${searchIn} scope=${scope ?? "all"} maxResults=${maxResults} platform=${platform ?? "default"}`);
    try {
      const start = performance.now();
      const catalog = requireCatalog(platform);
      if (searchIn === "literals") {
        const result = catalog.searchLiterals(pattern, mode, maxResults);
        log.info(`search_code(literals): pattern="${pattern}" returned ${result.length} results in ${(performance.now() - start).toFixed(1)}ms`);
        return {
          content: [{ type: "text" as const, text: JSON.stringify(result, null, 2) }],
        };
      }
      const result = await catalog.searchCode(pattern, mode, scope, maxResults);
      log.info(`search_code(source): pattern="${pattern}" scope=${scope ?? "all"} returned ${result.length} results in ${(performance.now() - start).toFixed(1)}ms`);
      return {
        content: [{ type: "text" as const, text: JSON.stringify(result, null, 2) }],
      };
    } catch (error) {
      log.warn(`search_code: pattern="${pattern}" error: ${error instanceof Error ? error.message : String(error)}`);
      return {
        content: [{ type: "text" as const, text: error instanceof Error ? error.message : String(error) }],
        isError: true,
      };
    }
  }
);

// Native module tools ---

server.tool(
  "list_native_modules",
  "Lists all native (WASM) modules in the snapshot with name, URL, content hash, and byte size.",
  {
    platform: platformSchema,
  },
  async ({ platform }: { platform?: SnapshotPlatform }) => {
    log.debug(`list_native_modules: platform=${platform ?? "default"}`);
    try {
      const result = requireCatalog(platform).listNativeModules();
      log.info(`list_native_modules: returned ${result.length} native modules`);
      return {
        content: [{ type: "text" as const, text: JSON.stringify(result, null, 2) }],
      };
    } catch (error) {
      log.warn(`list_native_modules: error: ${error instanceof Error ? error.message : String(error)}`);
      return {
        content: [{ type: "text" as const, text: error instanceof Error ? error.message : String(error) }],
        isError: true,
      };
    }
  }
);

server.tool(
  "get_native_module_metadata",
  "Returns structural analysis of a WASM module: function signatures, imports, exports, memory/table/global declarations, section sizes, and cross-references to JS loader modules.",
  {
    name: z.string(),
    platform: platformSchema,
  },
  async ({ name, platform }: { name: string; platform?: SnapshotPlatform }) => {
    log.debug(`get_native_module_metadata: name="${name}" platform=${platform ?? "default"}`);
    try {
      const catalog = requireCatalog(platform);
      const analysis = await catalog.getNativeModuleAnalysis(name);
      const crossRefs = await catalog.getNativeModuleCrossReferences(name);
      log.info(`get_native_module_metadata: name="${name}" functions=${analysis.functions?.length ?? 0} imports=${analysis.imports?.length ?? 0}`);
      return {
        content: [{ type: "text" as const, text: JSON.stringify({ ...analysis, crossReferences: crossRefs }, null, 2) }],
      };
    } catch (error) {
      log.warn(`get_native_module_metadata: name="${name}" error: ${error instanceof Error ? error.message : String(error)}`);
      return {
        content: [{ type: "text" as const, text: error instanceof Error ? error.message : String(error) }],
        isError: true,
      };
    }
  }
);

server.tool(
  "get_native_module_wat",
  "Returns WAT (WebAssembly Text) disassembly. Without functionIndex returns the full module; with functionIndex returns a single function.",
  {
    name: z.string(),
    platform: platformSchema,
    functionIndex: z.number().optional(),
  },
  async ({
    name,
    platform,
    functionIndex,
  }: {
    name: string;
    platform?: SnapshotPlatform;
    functionIndex?: number;
  }) => {
    try {
      const result = await requireCatalog(platform).getNativeModuleWat(name, functionIndex);
      return {
        content: [{ type: "text" as const, text: result }],
      };
    } catch (error) {
      return {
        content: [{ type: "text" as const, text: error instanceof Error ? error.message : String(error) }],
        isError: true,
      };
    }
  }
);

server.tool(
  "get_native_module_binary",
  "Returns a base64-encoded slice of a WASM module binary with optional byte range.",
  {
    name: z.string(),
    platform: platformSchema,
    startByte: z.number().optional(),
    endByte: z.number().optional(),
  },
  async ({
    name,
    platform,
    startByte,
    endByte,
  }: {
    name: string;
    platform?: SnapshotPlatform;
    startByte?: number;
    endByte?: number;
  }) => {
    try {
      const catalog = requireCatalog(platform);
      const record = catalog.getNativeModuleRecord(name);
      const { binary, startByte: start, endByte: end } =
        await catalog.getNativeModuleBinary(name, startByte, endByte);
      return {
        content: [
          {
            type: "text" as const,
            text: JSON.stringify({
              snapshotId: catalog.snapshotId,
              name: record.name,
              contentHash: record.contentHash,
              totalBytes: record.sizeBytes,
              startByte: start,
              endByte: end,
              base64: binary.toString("base64"),
            }, null, 2),
          },
        ],
      };
    } catch (error) {
      return {
        content: [{ type: "text" as const, text: error instanceof Error ? error.message : String(error) }],
        isError: true,
      };
    }
  }
);

// Persistent annotations ---

server.tool(
  "manage_annotations",
  "Manages persistent module annotations for the current snapshot. Actions: 'set' (create/update), 'get' (read one), 'list' (read all with optional filter), 'remove' (delete).",
  {
    action: z.enum(["set", "get", "list", "remove"]),
    platform: platformSchema,
    moduleName: z.string().optional().describe("Required for set/get/remove."),
    label: z.string().optional().describe("Short label (required for 'set')."),
    notes: z.string().optional().default("").describe("Detailed notes (used with 'set')."),
    filter: z.string().optional().describe("Substring filter for 'list' (over module name, label, or notes)."),
  },
  async ({
    action,
    platform,
    moduleName,
    label,
    notes,
    filter,
  }: {
    action: "set" | "get" | "list" | "remove";
    platform?: SnapshotPlatform;
    moduleName?: string;
    label?: string;
    notes: string;
    filter?: string;
  }) => {
    log.debug(`manage_annotations: action=${action} module=${moduleName ?? "none"} platform=${platform ?? "default"}`);
    try {
      const catalog = requireCatalog(platform);
      const snapshotId = catalog.snapshotId;

      switch (action) {
        case "set": {
          if (!moduleName) throw new Error("moduleName is required for 'set'.");
          if (!label) throw new Error("label is required for 'set'.");
          catalog.getModuleRecord(moduleName);
          const result = await setAnnotation(snapshotId, moduleName, label, notes);
          return {
            content: [{ type: "text" as const, text: JSON.stringify({ module: moduleName, ...result }, null, 2) }],
          };
        }
        case "get": {
          if (!moduleName) throw new Error("moduleName is required for 'get'.");
          const result = await getAnnotation(snapshotId, moduleName);
          return {
            content: [{ type: "text" as const, text: JSON.stringify({ module: moduleName, annotation: result }, null, 2) }],
          };
        }
        case "list": {
          const result = await listAnnotations(snapshotId, filter);
          return {
            content: [{ type: "text" as const, text: JSON.stringify({
              snapshotId,
              count: result.length,
              annotations: result,
            }, null, 2) }],
          };
        }
        case "remove": {
          if (!moduleName) throw new Error("moduleName is required for 'remove'.");
          const removed = await removeAnnotation(snapshotId, moduleName);
          return {
            content: [{ type: "text" as const, text: JSON.stringify({ module: moduleName, removed }, null, 2) }],
          };
        }
      }
    } catch (error) {
      return {
        content: [{ type: "text" as const, text: error instanceof Error ? error.message : String(error) }],
        isError: true,
      };
    }
  }
);

// Snapshot switching ---

server.tool(
  "switch_snapshot",
  "Hot-reloads the catalog to a different snapshot without restarting the server. Use list_snapshots to find available IDs.",
  {
    snapshotId: z.string(),
    platform: platformSchema,
  },
  async ({ snapshotId, platform }: { snapshotId: string; platform?: SnapshotPlatform }) => {
    log.info(`switch_snapshot: snapshotId="${snapshotId}" platform=${platform ?? "web"}`);
    try {
      const p = platform ?? "web";
      const newCatalog = await switchCatalog(p, snapshotId);
      log.info(`switch_snapshot: switched to revision=${newCatalog.revision} modules=${newCatalog.getAllModules().length}`);
      return {
        content: [{ type: "text" as const, text: JSON.stringify({
          snapshotId: newCatalog.snapshotId,
          revision: newCatalog.revision,
          modules: newCatalog.getAllModules().length,
        }, null, 2) }],
      };
    } catch (error) {
      log.error(`switch_snapshot: snapshotId="${snapshotId}" error: ${error instanceof Error ? error.message : String(error)}`);
      return {
        content: [{ type: "text" as const, text: error instanceof Error ? error.message : String(error) }],
        isError: true,
      };
    }
  }
);
}
