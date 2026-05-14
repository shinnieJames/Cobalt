# Cobalt MCP

A graph-first MCP server for reverse engineering WhatsApp across all platforms.
Extracts, indexes, and exposes JavaScript module bundles as a structured knowledge graph — then lets you attach a live debugger, inspect protocol traffic, and manipulate runtime state, all through MCP tools your AI agent can call.

## Platforms

| Platform                       | Extraction method                            | Live session      |
|--------------------------------|----------------------------------------------|-------------------|
| **WhatsApp Web**               | Playwright (Chromium)                        | CDP               |
| **WhatsApp Desktop (Windows)** | CDP                                          | CDP               |
| **WhatsApp Desktop (macOS)**   | Ghidra decompilation of Mac Catalyst Mach-O  | NOT YET SUPPORTED |
| **WhatsApp iOS**               | Ghidra decompilation of decrypted IPA/Mach-O | NOT YET SUPPORTED |
| **WhatsApp Android**           | NOT YET SUPPORTED                            | NOT YET SUPPORTED |

Since late 2024 the macOS desktop app is a Mac Catalyst port of the iOS binary (not an Electron web wrapper). Point `WEB_MCP_MACOS_BINARY` at `/Applications/WhatsApp.app` (or its Mach-O executable) to extract a `desktop_macos` snapshot via the same Ghidra pipeline used for iOS.

## Features

### Static analysis — Module knowledge graph

Every extracted JavaScript module is parsed with Babel into a rich AST index:

- **Fuzzy search** across module names, exports, dependencies, symbols, and string literals (powered by Orama with English stemming)
- **Dependency graph traversal** — BFS forward (what does this module use?) and reverse (what uses this module?)
- **Export resolution** — trace any export back to its implementing symbol with exact byte ranges
- **Cross-module call edges** — see which functions in module A call exports from module B
- **Symbol references** — find every usage of a function/class/variable across the entire bundle
- **Code search** — regex or literal search across all module sources, or fast indexed literal search
- **Switch dispatch detection** — automatically identifies polymorphic routing patterns
- **Persistent annotations** — label and annotate modules with notes that survive across sessions

### Static analysis — WASM

Native WebAssembly modules get their own analysis pipeline:

- **Structural analysis** — imports, exports, function signatures, memory/table/global declarations, section sizes
- **WAT disassembly** — full module or per-function WebAssembly Text output
- **Binary access** — base64-encoded binary slices with byte-range support
- **Cross-references** — identifies which JS loader modules reference each WASM module

### Revision snapshots

Every extraction produces a versioned snapshot tied to the client revision:

- **Snapshot history** — keep multiple snapshots per platform, switch between them at runtime
- **Diff engine** — compare any two snapshots to see added/removed/changed modules with symbol-level deltas and optional source excerpts
- **Hot reload** — switch the active catalog to a different snapshot without restarting the server

### Live sessions

Attach to a running WhatsApp instance for real-time inspection and control:

- **Session lifecycle** — start, stop, health-check, validate snapshot-to-runtime revision match
- **Phone login automation** — submit phone number, extract pairing code, wait for login completion
- **ADB integration** — list Android devices, automate pairing code entry on-device via UI automation

### Protocol observability

Inspect and inject protocol-level traffic in real time:

- **Stanza capture** — query inbound/outbound XML stanzas with filters on tag, id, from, to, attributes, or free-text
- **Stanza injection** — send custom stanza nodes for targeted protocol experiments
- **WAM telemetry** — capture, query, and inject WAM (WhatsApp Analytics/Metrics) events
- **WAM schema discovery** — inspect event constructor definitions and field schemas before crafting events
- **Network capture** — record and query WebSocket frames and HTTP requests with direction/URL/content filters

### AB prop control

Full read/write access to the runtime's A/B testing flags:

- **Query** — list all flags, filter by name, diff against defaults
- **Schema discovery** — inspect flag definitions (name, code, type, defaults)
- **Mutate** — set individual flags, reset one or all to defaults

### JavaScript debugger

Full CDP debugger integration:

- **Script discovery** — list runtime scripts by URL pattern
- **Expression evaluation** — execute arbitrary JavaScript with promise support
- **Breakpoints** — set by URL+line or scriptId+line, with optional conditions
- **Stepping** — pause, resume, step over, step into, step out
- **Paused state inspection** — call stack, scope variables, and pause reason

## Installation

### Prerequisites

```bash
cd tools/web/mcp-server
npm install
npx playwright install chromium
npm run build
```

For iOS analysis, install [Ghidra](https://ghidra-sre.org/) and optionally set `GHIDRA_INSTALL_DIR`.

### Claude Code

**CLI:**

```bash
claude mcp add --transport stdio \
  --env WEB_MCP_PLATFORM=auto \
  --env WEB_MCP_LOG_LEVEL=info \
  cobalt-mcp -- node tools/web/mcp-server/dist/index.js
```

Add `--scope user` for global (all projects) or `--scope project` to commit it in `.mcp.json`.

**JSON** — add to `.mcp.json` at the project root (or `~/.claude/.mcp.json` for global):

```json
{
  "mcpServers": {
    "cobalt-mcp": {
      "type": "stdio",
      "command": "node",
      "args": ["tools/web/mcp-server/dist/index.js"],
      "env": {
        "WEB_MCP_PLATFORM": "auto",
        "WEB_MCP_LOG_LEVEL": "info"
      }
    }
  }
}
```

### OpenAI Codex

**CLI:**

```bash
codex mcp add cobalt-mcp \
  --env WEB_MCP_PLATFORM=auto \
  --env WEB_MCP_LOG_LEVEL=info \
  -- node tools/web/mcp-server/dist/index.js
```

**JSON** — add to `.codex/config.toml` or `codex.json`:

```json
{
  "mcpServers": {
    "cobalt-mcp": {
      "type": "stdio",
      "command": "node",
      "args": ["tools/web/mcp-server/dist/index.js"],
      "env": {
        "WEB_MCP_PLATFORM": "auto",
        "WEB_MCP_LOG_LEVEL": "info"
      }
    }
  }
}
```

### Gemini CLI

**CLI:**

```bash
gemini mcp add \
  -e WEB_MCP_PLATFORM=auto \
  -e WEB_MCP_LOG_LEVEL=info \
  cobalt-mcp node tools/web/mcp-server/dist/index.js
```

Add `--scope user` for global configuration.

**JSON** — add to `~/.gemini/settings.json`:

```json
{
  "mcpServers": {
    "cobalt-mcp": {
      "command": "node",
      "args": ["tools/web/mcp-server/dist/index.js"],
      "env": {
        "WEB_MCP_PLATFORM": "auto",
        "WEB_MCP_LOG_LEVEL": "info"
      }
    }
  }
}
```

### OpenCode

**JSON** — add to `opencode.jsonc`:

```json
{
  "mcp": {
    "cobalt-mcp": {
      "type": "local",
      "command": ["node", "tools/web/mcp-server/dist/index.js"],
      "environment": {
        "WEB_MCP_PLATFORM": "auto",
        "WEB_MCP_LOG_LEVEL": "info"
      }
    }
  }
}
```

## Configuration

All configuration is via environment variables. Copy `.env.example` to `.env` and adjust:

| Variable                       | Description                                                                  | Default      |
|--------------------------------|------------------------------------------------------------------------------|--------------|
| `WEB_MCP_PLATFORM`             | Target platform(s): `auto`, `web`, `desktop_windows`, `desktop_macos`, `ios` | `auto`       |
| `WEB_MCP_MODE`                 | Startup mode: empty for live extraction, `cached` for offline                | empty (live) |
| `WEB_MCP_SNAPSHOT_ID`          | Load a specific snapshot by ID                                               | —            |
| `WEB_MCP_SOURCE_DIR`           | Ingest modules from a local directory of JS files                            | —            |
| `WEB_MCP_REVISION`             | Override revision string when ingesting from directory                       | —            |
| `WEB_MCP_IOS_BINARY`           | Path to decrypted IPA or Mach-O binary                                       | —            |
| `WEB_MCP_MACOS_BINARY`         | Path to WhatsApp.app bundle or its Mach-O executable                         | auto-detect  |
| `GHIDRA_INSTALL_DIR`           | Ghidra installation path (auto-detected if unset)                            | —            |
| `WEB_MCP_NATIVE_ANALYSIS_TIMEOUT` | Per-file Ghidra timeout in seconds (native Mach-O pipeline)               | `1800`       |
| `WEB_MCP_NATIVE_MAX_CPU`       | Max CPU cores for Ghidra (native Mach-O pipeline)                            | `4`          |
| `WEB_MCP_WEB_LOCALE`           | UI locale for WhatsApp Web                                                   | `en-US`      |
| `WEB_MCP_PHONE_NUMBER`         | Phone number for automated login (E.164)                                     | —            |
| `WEB_MCP_PHONE_COUNTRY_CODE`   | Country code override for phone login                                        | —            |
| `ADB_PATH`                     | Path to adb binary                                                           | `adb`        |
| `WEB_MCP_LOG_LEVEL`            | Log level: `debug`, `info`, `warn`, `error`, `silent`                        | `info`       |

## Tool reference

### Catalog tools (static analysis)

| Tool                  | Description                                                                            |
|-----------------------|----------------------------------------------------------------------------------------|
| `search_modules`      | Full-text fuzzy search over module names, exports, dependencies, symbols, and literals |
| `get_module_metadata` | Returns dependencies, exports, source hash, and byte size for a module                 |
| `get_exports`         | Returns export bindings with binding kind (identifier, member, computed, unresolved)   |
| `resolve_export`      | Resolves a specific export to its implementing symbol with byte range and source       |
| `get_module_source`   | Returns module source code with optional byte-range or line-range slicing              |
| `get_symbol_source`   | Returns the exact source code for a named function, class, or variable                 |
| `find_references`     | Finds all pre-indexed references to a symbol across modules                            |
| `trace_dependencies`  | BFS traversal of the dependency graph (forward or reverse)                             |
| `search_code`         | Regex or literal search across source code or pre-indexed string literals              |
| `manage_annotations`  | Create, read, list, or remove persistent module annotations                            |

### Native module tools (WASM)

| Tool                         | Description                                                                             |
|------------------------------|-----------------------------------------------------------------------------------------|
| `list_native_modules`        | Lists all WASM modules with name, URL, hash, and size                                   |
| `get_native_module_metadata` | Structural analysis: function signatures, imports, exports, memory, globals, cross-refs |
| `get_native_module_wat`      | WAT disassembly (full module or single function)                                        |
| `get_native_module_binary`   | Base64-encoded binary slice with byte-range support                                     |

### Snapshot tools

| Tool                  | Description                                                                          |
|-----------------------|--------------------------------------------------------------------------------------|
| `list_snapshots`      | Lists available snapshot IDs across platforms                                        |
| `get_active_snapshot` | Returns current snapshot metadata (ID, revision, module count)                       |
| `get_revision_diff`   | Compares two snapshots with module/export/symbol deltas and optional source excerpts |
| `switch_snapshot`     | Hot-reloads the catalog to a different snapshot                                      |

### Live session tools

| Tool                                  | Description                                                            |
|---------------------------------------|------------------------------------------------------------------------|
| `web_live_start_session`              | Starts a live session (web browser or Windows desktop via CDP)         |
| `web_live_stop_session`               | Stops the live session and clears capture state                        |
| `web_live_status`                     | Health check: session state, auth state, revision match                |
| `web_live_validate_snapshot_revision` | Confirms static snapshot matches live runtime revision                 |
| `web_live_login_with_phone_number`    | Submits phone number and extracts pairing code                         |
| `web_live_wait_for_login`             | Blocks until login completes                                           |
| `web_live_adb_list_devices`           | Lists connected Android devices via ADB                                |
| `web_live_adb_link_pairing_code`      | Automates pairing code entry on an Android device                      |

### Observability tools

| Tool                                 | Description                                                           |
|--------------------------------------|-----------------------------------------------------------------------|
| `web_live_stanza_query_nodes`        | Query captured stanza traffic with direction/tag/id/attr/text filters |
| `web_live_stanza_send_node`          | Send a custom stanza node into the live runtime                       |
| `web_live_wam_get_events`            | Query captured WAM telemetry events                                   |
| `web_live_wam_send_event`            | Send a custom WAM event with optional props                           |
| `web_live_wam_get_event_definitions` | Inspect WAM event constructor schemas                                 |
| `web_live_ab_props_query`            | List/filter/diff AB test flags                                        |
| `web_live_ab_props_get`              | Look up a single AB prop by name                                      |
| `web_live_ab_props_set`              | Set an AB prop value                                                  |
| `web_live_ab_props_reset`            | Reset one AB prop to default                                          |
| `web_live_ab_props_reset_all`        | Reset all AB props to defaults                                        |
| `web_live_ab_props_definitions`      | Inspect AB prop schemas (name, code, type, defaults)                  |
| `web_live_clear_capture`             | Clear stanza, WAM, network, or all capture buffers                    |

### Debug tools

| Tool                                   | Description                                           |
|----------------------------------------|-------------------------------------------------------|
| `web_live_debug_list_scripts`          | List runtime scripts by URL filter                    |
| `web_live_debug_eval`                  | Evaluate JavaScript in the live runtime               |
| `web_live_debug_set_breakpoint_url`    | Set breakpoint by script URL + line/column            |
| `web_live_debug_set_breakpoint_script` | Set breakpoint by scriptId + line/column              |
| `web_live_debug_remove_breakpoint`     | Remove a breakpoint                                   |
| `web_live_debug_command`               | Debugger stepping: pause, resume, step over/into/out  |
| `web_live_debug_paused_state`          | Inspect call stack, scope variables, and pause reason |

### Network tools

| Tool                      | Description                                                         |
|---------------------------|---------------------------------------------------------------------|
| `web_live_network_start`  | Start capturing WebSocket frames and HTTP requests                  |
| `web_live_network_stop`   | Stop network capture                                                |
| `web_live_network_query`  | Query captured frames/requests with type/direction/URL/text filters |
| `web_live_network_status` | Current capture state and frame/request counts                      |