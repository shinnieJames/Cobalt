# WAM fixture capture

The fixtures in this directory underpin the WAM-package parity test
suite. Each fixture pins the live WhatsApp Web bundle's output for a
slice of the WAM pipeline, and Cobalt's matching code path must
reproduce the same bytes / results.

Re-capture when:

- the static snapshot revision in `tools/web/mcp-server/data/snapshots/active.json` moves, or
- a fixture's `snapshotRevision` / `liveRuntimeRevision` header drifts more than ~1 month behind the live revision, or
- the corresponding Java test starts failing because the WAM JS bundle changed shape.

## Prerequisites

- The `whatsapp` MCP server is reachable and the `personal` (or any
  named, logged-in) session is running.
- Note the current `snapshotRevision` (from
  `mcp__whatsapp__get_active_snapshot`) and `liveRuntimeRevision`
  (from `mcp__whatsapp__web_live_status`). Update `SNAPSHOT_REVISION` /
  `LIVE_REVISION` at the top of `generate.mjs` to the new pair before
  re-running.

## Usage

```
node generate.mjs --session=personal [--topic=<name>]
```

Each capture writes to MCP's `data/captures/<sessionId>/wam/<name>.json`.
Curate the outputs into this directory (overwriting the existing
`.expected.json` / `ed25519-live-bundle-vectors.json` files) and inspect
the diff before committing. A delta in encoder output signals a
wire-format change in the WA Web bundle, which Cobalt must follow.

## Fixtures

| Capture topic                  | Java consumer                 | What it pins                                                                                                                                       |
|--------------------------------|-------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------|
| `wam-tags-roundtrip`           | `WamTagsKatTest`              | Every role bit / LAST flag / WIDE_ID flag / value-type mask emitted by `WAWebWamLibProtocol.{writeGlobalAttribute,writeEvent,writeField}`.         |
| `wam-encoder-type-matrix`      | `WamEventEncoderKatTest`      | Full event-with-fields byte sequences across the WamType x edge-value matrix.                                                                      |
| `wam-global-encoder`           | `WamGlobalEncoderKatTest`     | `writeGlobalAttribute` output for every named global in `WamGlobalEncoder.java`.                                                                   |
| `wam-events-multi-field`       | `WamGeneratedImplKatTest`     | Representative multi-field events per channel (REGULAR / REALTIME / PRIVATE / sampled).                                                            |
| `wam-buffer-headers`           | `WamBufferHeaderKatTest`      | The 8-byte WAM buffer header for every (channelByte, seqNum) combination Cobalt cares about.                                                       |
| `wam-beaconing`                | `WamBeaconingTest`            | `maybeGetEventSequenceNumber` under deterministic Math.random + clock + UserPrefs stubs.                                                           |
| `wam-event-definitions`        | `WamEventRegistryAuditTest`   | Every registered WA Web event (eventId, channel, fields), plus every named global, sourced from `WAWebWamCodegenUtils.metrics`.                    |
| `ed25519-live-bundle-vectors`  | `Ed25519LiveBundleKatTest`    | `hashToPoint`, `blindToken`, `unblindToken` outputs for eight deterministic input triples (msg, scalar, sk). Reconstructed; see comment in driver. |

## Why session, not emulator

The persistent Chromium user-data-dir under
`tools/web/mcp-server/data/sessions/<sessionId>/chromium` keeps the
session logged in across server restarts, so
`web_live_start_session sessionId=personal` is the only command needed
to bring the bundle back online. The registered Android emulators
(`primary`, `business`) are not required for any of these captures —
every capture runs entirely in the web bundle's JS runtime.
