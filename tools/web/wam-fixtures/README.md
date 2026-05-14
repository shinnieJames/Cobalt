# WAM fixture capture

The fixtures under `modules/lib/src/test/resources/fixtures/wam/` underpin
the WAM-package parity test suite. Each fixture pins the live WhatsApp
Web bundle's output for a slice of the WAM pipeline, and Cobalt's
matching code path must reproduce the same bytes / results.

The capture procedure below is a reproducible recipe. Re-run it when:

- the static snapshot revision in `tools/web/mcp-server/data/snapshots/active.json` moves, or
- a fixture's `snapshotRevision` / `liveRuntimeRevision` header drifts more than ~1 month behind the live revision, or
- the corresponding Java test starts failing because the WAM JS bundle changed shape.

## Prerequisites

- The `whatsapp` MCP server is reachable and the `personal` (or any
  named, logged-in) session is running. See the memory note
  *Web sessions persist; don't boot emulators* for context.
- The static snapshot revision is known. Look it up via
  `mcp__whatsapp__get_active_snapshot`. The live runtime revision is
  reported by `mcp__whatsapp__web_live_status`. Embed both numbers in
  every fixture header so future drift is loud, not silent.

## Capture procedure (per fixture)

For each entry under `snippets/`, run:

```
mcp__whatsapp__web_live_debug_eval_to_file
  sessionId   = personal
  topic       = <name without .js>
  outputPath  = modules/lib/src/test/resources/fixtures/wam/<name>.json
  expression  = <contents of snippets/<name>.js>
```

The `web_live_debug_eval_to_file` tool wraps the evaluated result in a
schema envelope; `WamFixtures.loadOracle(...)` un-stringifies the inner
`result.value` payload at test time.

## Fixtures

| Snippet | Java consumer | What it pins |
|---|---|---|
| `01-wam-tags-roundtrip.js` | `WamTagsKatTest` | Every role bit / LAST flag / WIDE_ID flag / value-type mask emitted by `WAWebWamLibProtocol.{writeGlobalAttribute,writeEvent,writeField}`. |
| `02-wam-encoder-type-matrix.js` | `WamEventEncoderKatTest` | Full event-with-fields byte sequences across the WamType × edge-value matrix. |
| `03-wam-global-encoder.js` | `WamGlobalEncoderKatTest` | `writeGlobalAttribute` output for every named global in `WamGlobalEncoder.java`. |
| `04-wam-events-multi-field.js` | `WamGeneratedImplKatTest` | Representative multi-field events per channel (REGULAR / REALTIME / PRIVATE / sampled). |
| `05-wam-buffer-headers.js` | `WamBufferHeaderKatTest` | The 8-byte WAM buffer header for every (channelByte, seqNum) combination Cobalt cares about. |
| `06-wam-beaconing.js` | `WamBeaconingTest` | `maybeGetEventSequenceNumber` under deterministic Math.random + clock + UserPrefs stubs. |
| `07-wam-event-definitions.js` | `WamEventRegistryAuditTest` | Every registered WA Web event (eventId, channel, fields), plus every named global, sourced from `WAWebWamCodegenUtils.metrics`. |

## Snapshot pinning

When updating the captures:

1. Note the current `snapshotRevision` (from `get_active_snapshot`) and
   `liveRuntimeRevision` (from `web_live_status`).
2. Edit each snippet's header constants — search for `snapshotRevision:`
   and `liveRuntimeRevision:` and update both to the new pair.
3. Re-run the captures.
4. Inspect the diff before committing. A delta in the encoder output
   would signal a wire-format change in the WA Web bundle, which Cobalt
   must follow.

## Why session, not emulator

Per memory `feedback_web_session_no_emulator_needed`: the persistent
Chromium user-data-dir under
`tools/web/mcp-server/data/sessions/<sessionId>/chromium` keeps the
session logged in across server restarts, so `web_live_start_session
sessionId=personal` is the only command needed to bring the bundle back
online. The registered Android emulators (`primary`, `business`) are not
required for any of these captures — every snippet runs entirely in the
web bundle's JS runtime.
