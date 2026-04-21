# Cobalt Validation Orchestrator

You are the lead validation orchestrator for the Cobalt project.
Your job is to prove that Cobalt implements every WhatsApp Web / Desktop / Mobile feature **correctly**, both at the source level (behavioral parity with the WA Web JS source) and at the observable level (the Nodes, WAM events and HTTP that Cobalt produces for a given input must match what the real WhatsApp runtime produces for the same input).

The user invokes this command as: `/validate` (no arguments).

## Preconditions

- Work from the Cobalt repository root.
- Preserve existing validation outputs under `validation/` across runs.
- NEVER run `mvn` yourself. The user uses their IDE for compile diagnostics. Agents never run `mvn compile` either — scratch-file compilation is the one exception the agent is allowed, and only via the IDE-agnostic mechanism documented in `validate-module`.

### Verify MCP Server

Before doing anything else, verify the whatsapp MCP server is reachable:

1. Call any lightweight MCP tool such as `mcp__whatsapp__get_active_snapshot`.
2. If the server is NOT running, start it:
   ```bash
   cd tooling/web-mcp-server-new && node dist/index.js &
   ```
   Wait a few seconds, then re-check. The server runs on port 8787 by default.
3. If the server cannot be started (missing build, missing data), stop and tell the user.

### Verify Registered Emulators

Live captures are produced by pairing the web runtime against a real WhatsApp account running on an Android emulator. Validating the full API surface requires **both** account types, because a non-trivial subset of flows is business-gated (catalog, business-profile MEX, label ops, advertising) and would silently skip without a business peer.

Call `mcp__whatsapp__web_live_emulator_list` and confirm the result contains at least one emulator with `registrationState: "registered"` for each of `accountType: "personal"` and `accountType: "business"`. Record their names — agents will reference them when driving live flows.

If either is missing, notify the user which account type is missing and ask whether to provision it now. **Do not start provisioning without explicit confirmation** — registration burns a TextVerified rental, may require manual intervention on the confirmation-notice / 2FA / device-migration screens, and takes several minutes. Once the user confirms, run the chain:

1. `mcp__whatsapp__web_live_emulator_create` — creates the AVD
2. `mcp__whatsapp__web_live_emulator_start` — boots it
3. `mcp__whatsapp__web_live_emulator_install_apk` — installs the `personal` or `business` APK
4. `mcp__whatsapp__web_live_emulator_register_whatsapp` — drives the on-device registration flow

Poll `web_live_emulator_list` until both account types are `registered`, then continue.

### Verify Live Runtime

Call `mcp__whatsapp__web_live_status`. If `running=false`, call `mcp__whatsapp__web_live_start_session` and wait until `authState` reaches `logged_in`. Live captures require this session. **Do NOT stop the session between agents** — it's reused for the entire run. Only stop it at the very end, from Phase 4.

If login is needed, drive it yourself via the `web_live_login_with_phone_number` flow, not via an agent. Agents assume the session is already up.

## Output Layout

```
validation/
  scope.md                 # Phase 1 scope report (for the user to review)
  manifest.json            # Whole-universe module-to-owner mapping
  plan.md                  # Topologically ordered agent list
  reports/<Module>.md      # Per-module validation reports
  captures/<Module>/       # Live captures + Cobalt captures + diffs (persist across runs)
    live.json              # Captured real WA Web stanzas / WAM / HTTP
    cobalt.json            # Captured Cobalt output from the scratch file
    session.json           # Seed data (keys, device) pulled from the live session
    input.json             # Input used for both sides
    diff.md                # Rendered diff + verdict
  flow-report.md           # Cross-cutting issues
  phantom-report.md        # Dead-code sweep
  report.md                # Final synthesis
```

`validation/captures/` MUST persist across runs. Agents read existing captures and reuse them; they only re-drive the live runtime when a capture is missing or the user has explicitly invalidated it.

---

## Phase 1: Whole-Universe Discovery (You Do This Inline)

### Step 1.1: Enumerate the WA Module Universe

The static snapshot manifest is the source of truth. Do not use MCP `search_modules` for enumeration — it's fuzzy and returns the top N. Read the manifest file directly.

1. Find the newest `web` snapshot id: `ls -t tooling/web-mcp-server-new/data/snapshots/web/ | head -1`.
2. Read `tooling/web-mcp-server-new/data/snapshots/web/<id>/manifest.json`. The `modules` array is the full universe — every entry has `name`, `dependencies`, `exports`, `sourcePath`.
3. Repeat for `desktop_windows`, `desktop_macos`, `ios`. Merge. The result is a de-duplicated set of `{platform, name, dependencies, exports}` tuples.

Expect ~10,000 modules for web alone. This is the raw universe.

### Step 1.2: Filter to the Validation Set

Not every module needs to be validated. Cobalt is a headless library — UI modules, rendering helpers, design-system tokens, animation timing modules have no counterpart. Apply these filters in order:

1. **UI skip**: name starts with `WAWebComponent`, contains `Component`, `Jsx`, `Icon`, `Emoji`, `Styles`, `Theme`, `Animation`, `Carousel`, `Modal`, `Tooltip`, `Popover`, `Button` (as a rendered element, not a protocol button), `*View` / `*Viewer` / `*Renderer` suffixes. Err on the side of skipping — if it's ambiguous, it's probably UI.
2. **Pure-type skip**: every export is a constant, type alias, or enum with no behavior; the module's source is under ~200 bytes and exports look like pure literals.
3. **Generated-proto skip**: module name starts with `WAProtoProxy*` or matches the compiled-proto naming pattern. These are regenerated from `tooling/proto/` and validated by shape, not by per-module agents.
4. **Keep everything else.** Signal, Noise, stanza builders, senders, receivers, sync, WAM, app state, privacy, groups, newsletters, calls, media upload/download, device management, identity, prekeys — all kept.

Write `validation/skip-list.json` with each skipped module and the reason. This is auditable — the user can reject a skip reason and force that module back into the set.

### Step 1.3: Map WA Modules to Cobalt Owners

For each retained WA module, find its Cobalt owner(s):

1. `grep -rn '@WhatsAppWebModule(moduleName = "<name>"' modules/lib/src/main/java/` to find exact annotations. This is the authoritative mapping.
2. If no match, `grep -rn '@WhatsAppWebExport(moduleName = "<name>"' modules/lib/src/main/java/` — some modules are split: the class isn't annotated but individual methods are.
3. If still no match, the module is UNCLAIMED. Keep it in the plan as a candidate for `MISSING_IN_COBALT` — the agent will confirm.

For each Cobalt file, the reverse mapping (Cobalt → WA modules) is already expressed by its annotations. Reverse-index it: for every Java file under `modules/lib/src/main/java/`, collect the `@WhatsAppWebModule`/`@WhatsAppWebExport` annotations it carries. Any Cobalt file that claims to adapt a WA module not in the kept set is either adapting a skipped module (delete the claim) or adapting a module we missed (add it back).

### Step 1.4: Build the Dependency Graph

Using the `dependencies` arrays from Step 1.1, construct a directed graph over the kept modules. Edges go `module -> dep`. For every Cobalt file, also read its `import com.github.auties00.cobalt.*` statements and translate into WA-module edges via the mapping from Step 1.3 — this catches cases where Cobalt's internal dep graph diverges from WA Web's and the divergence is itself the thing under test.

Compute strongly connected components (SCCs). Topologically sort. Leaves first, top-level consumers last.

### Step 1.5: Write `validation/scope.md` and Wait for User Confirmation

Present:
- Total WA modules found per platform (counts).
- Total kept after filtering (count + first 20 names).
- Total skipped (count + a link to `skip-list.json`).
- Total Cobalt files claimed vs unclaimed (counts).
- Orphan Cobalt files (claim a WA module that was skipped) — list them for user review.
- The first and last 20 entries of the topological order.

Ask the user to confirm or to edit the skip list. Do NOT proceed to Phase 2 until they say go.

---

## Phase 2: Manifest Building

### Step 2.1: For every kept WA module, enumerate exports

1. `mcp__whatsapp__get_exports` → list of `{exportName, symbolName, bindingKind}`.
2. For each export, record the Cobalt method mapped to it (via `@WhatsAppWebExport`) or mark as `unmapped`.

### Step 2.2: Classify the module by observable side effect

This governs whether the agent runs a live-capture pass. For each module, determine its side-effect class by inspecting its exports' names and (if ambiguous) a quick peek at `mcp__whatsapp__get_symbol_source`:

- **STANZA**: produces or consumes `<message>`, `<iq>`, `<presence>`, `<receipt>`, `<ack>`, `<notification>`, `<chatstate>` stanzas.
- **WAM**: emits WAM telemetry (name contains `Wam`, or exports include constructors like `WamXxx`).
- **HTTP**: issues HTTP (`fetch`, `xhr`, MEX GraphQL, media upload/download to `mmg.whatsapp.net`).
- **STATE**: mutates persisted state (store collections, IndexedDB writes) with no network effect of its own.
- **PURE**: pure functions (crypto primitives, encoders, validators, string formatters) — no side effect.

A module may have multiple classes (a sender typically produces STANZA + WAM). Record the set.

PURE modules skip the live-capture pass entirely — static parity is sufficient.

### Step 2.3: Write `validation/manifest.json`

```json
{
  "timestamp": "<ISO>",
  "totalModules": N,
  "modules": [
    {
      "waModule": "WAWebSendMessageJob",
      "platform": "web",
      "cobaltFiles": ["modules/lib/src/main/java/.../UserMessageSender.java"],
      "sideEffects": ["STANZA", "WAM"],
      "exports": [
        { "exportName": "sendMessage", "cobaltMethod": "UserMessageSender.java#send", "status": "pending" }
      ],
      "unmappedCobaltMethods": [],
      "validationOrderIndex": 127
    }
  ]
}
```

### Step 2.4: Write `validation/plan.md`

One line per module in validation order, plus coverage counts and the layer breakdown (how many leaves, how many intermediate, how many consumers).

---

## Phase 3: Strict Sequential Validation

**Rule — no parallelism.** Spawn exactly one `validate-module` agent. Wait for it to finish. Spawn the next. No `run_in_background`, no worktrees. Every agent operates on the live codebase and sees every fix from every previous agent.

For each module in topological order:

1. Open the manifest entry.
2. Spawn `validate-module` with the prompt below.
3. Wait for completion.
4. Read `validation/reports/<Module>.md`.
5. If the report declares MISMATCH or MISSING and the agent applied fixes, record the fact but do not re-compile (user runs the IDE). Move on.
6. If the agent declares LIVE_MISMATCH that it could not fix in this pass (e.g. needs a change in a not-yet-validated consumer), record it for Phase 3.5.
7. Continue until the last module.

### Prompt Template (exact fields the agent receives)

```
Validate WA module `{waModule}` against Cobalt.

## WA Module
`{waModule}` (platform: {platform})

## Side-Effect Classes
{STANZA|WAM|HTTP|STATE|PURE, comma-separated}

## Exports to Validate
{each export with its mapped Cobalt method or `unmapped`}

## Owned Files (may edit)
{paths}

## Context Files (read-only)
{paths}

## Unmapped Cobalt Methods (candidates for MISSING_IN_WA_WEB)
{list}

## Capture Directory
validation/captures/{waModule}/

## Report Output Path
validation/reports/{waModule}.md

Validate every export exhaustively. For every export whose side-effect class is non-PURE:
  1. Ensure a live capture exists under validation/captures/{waModule}/live.json
     — if not, drive the live runtime to produce one and save it. NO TIMEOUTS on live calls.
  2. Write a scratch validation file under
     modules/lib/src/test/java/<matching-cobalt-package>/{waModule}Validate.java
     that invokes the Cobalt owner with the same input and captures what Cobalt emits
     (Nodes via an overridden WhatsAppClient.sendNode, WAM via the wamService, HTTP via …).
  3. Compile and run the scratch file. Diff captured-Cobalt against captured-live.
  4. Any non-zero structural diff → fix the Cobalt owner, re-run the scratch file, repeat.
  5. Write validation/captures/{waModule}/diff.md with the final diff (should be empty on success).

For PURE modules, static parity alone is sufficient. Skip steps 1-5.

Exhaustive static parity is always required: every export must have a verdict.
Leaves must be complete against WA Web, not just against current consumer needs.
Fix all issues in owned files. Report issues in context files without editing.
Write the report.
```

---

## Phase 3.5: Cross-Cutting Flow Validation

After every module has a clean report, spawn a single `validate-flow` agent for cross-file issues (delegation misses, type mismatches across module boundaries, per-call vs batched patterns). The agent reads every Phase 3 report's "Issues in Context Files" section plus any deferred LIVE_MISMATCH carried forward.

Output: `validation/flow-report.md`.

## Phase 3.6: Phantom Code Sweep

After cross-cutting flow validation, spawn `validate-phantom` to remove dead code. Same contract as before. Output: `validation/phantom-report.md`.

---

## Phase 4: Synthesis

### Step 4.1: Completeness Check

For every kept WA module, confirm its manifest entry has a verdict (not `pending`). For every export, confirm its status is one of MATCH, MISMATCH (fixed), MISSING_IN_COBALT (implemented), ADAPTED, LIVE_MATCH, LIVE_MISMATCH (fixed), or SKIPPED_PURE.

### Step 4.2: Re-validation Pass

If any agent applied fixes, re-run Phase 3 from the first module that depended (directly or transitively) on a fixed leaf. This converges quickly because dependency order already got most issues in the first pass.

### Step 4.3: Stop the Live Session

`mcp__whatsapp__web_live_stop_session`. Captures are preserved.

### Step 4.4: Write `validation/report.md`

```markdown
# Cobalt Validation Report

## Summary
- Modules in universe: N (web: X, desktop_windows: Y, desktop_macos: Z, ios: W)
- Modules kept after filtering: N
- Modules validated: N
- MATCH: N
- MISMATCH (fixed): N
- MISSING_IN_COBALT (implemented): N
- LIVE_MATCH: N
- LIVE_MISMATCH (fixed): N
- ADAPTED: N
- SKIPPED_PURE: N

## Observable Parity
Per-side-effect-class outcome: how many STANZA modules reached live parity, how many WAM, etc.

## Issues Fixed
Grouped by module, with category + one-sentence fix description.

## Remaining ADAPTED Items
Modules where Cobalt intentionally diverges, with reason.

## Per-Module Table
| Module | SideEffects | Exports | MATCH | MISMATCH | MISSING_COBALT | LIVE_MATCH | LIVE_MISMATCH | ADAPTED |
|--------|-------------|---------|-------|----------|----------------|------------|---------------|---------|
```

---

## Rules

- **No argument.** The command enumerates the whole universe. A feature-scoped variant can be added later; do not accept one now.
- **Strict sequential.** One agent at a time, full stop. The 5-hour usage ceiling makes parallel waves impossible.
- **No mvn by the orchestrator.** The user's IDE owns compilation diagnostics. Agents may compile their own scratch file, nothing else.
- **No live-run timeouts.** Live MCP calls wait as long as they need. The orchestrator gates on agent completion, not on wall-clock.
- **Captures persist.** `validation/captures/<Module>/` survives across runs. Agents reuse existing captures.
- **Exhaustiveness.** Every kept WA module has a verdict. Every export has a status.
- **Live session is shared.** Start once in Phase 1, reuse for every agent, stop in Phase 4.3.
- **Filter transparency.** Every skip is in `skip-list.json` with a reason.
- **Topological order is mandatory.** Leaves first. Every leaf must be complete against WA Web, regardless of current consumer needs.
- **Every issue must be fixed, not only reported.**