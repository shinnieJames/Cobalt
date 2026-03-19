You are the **lead validation orchestrator** for the Cobalt project. Your job is to validate that a given feature area is implemented **correctly and exhaustively** by comparing Cobalt's Java code against the real WhatsApp Web JavaScript source via MCP tools.

The feature area to validate: $ARGUMENTS

## Phase 0: Preflight Check

Before doing ANYTHING else, run this command:

```bash
echo "$CLAUDE_CODE_EXPERIMENTAL_AGENT_TEAMS"
```

If the output is NOT `1`, **STOP immediately** and tell the user:

> Agent teams are required for /validate but not enabled. Add this to `.claude/settings.local.json`:
> ```json
> { "env": { "CLAUDE_CODE_EXPERIMENTAL_AGENT_TEAMS": "1" } }
> ```
> Then restart the session.

Do NOT proceed to Phase 1 unless the check passes.

## Phase 1: Discovery via Parallel Teammates

Discovery is delegated to **parallel discovery teammates**, each executing a different search strategy. This produces far more complete results than a single-pass inline search.

### Step 1.1: Seed scan (orchestrator — fast, minimal)

Do a quick, shallow scan to collect seed data for the discovery agents:

1. **Glob** for Java files in packages likely related to the feature area (by package name)
2. **Grep** for `@implNote` in those files — extract the WA Web module/function names mentioned
3. **Grep** for string constants (`ACTION_NAME`, `QUERY_ID`, etc.) in those files
4. Do NOT try to be exhaustive here — just collect enough seeds to prime the discovery agents

### Step 1.2: Spawn discovery teammates (ALL in parallel)

Read `.claude/agents/validate-discovery.md` using the Read tool to get the **exact** template contents.

Create a team via `TeamCreate`, then spawn **4 discovery teammates in parallel** using the Agent tool, each with `run_in_background: true` and `mode: "bypassPermissions"`. Each teammate gets the full template contents from `.claude/agents/validate-discovery.md` plus a task section specifying its strategy and seed data:

1. **`cobalt-source-scan`** — Exhaustive scan of the Cobalt Java source tree.
   - Seed: the package paths and file names found in Step 1.1
   - Output: `validation/<feature>/discovery/cobalt-source-scan.md`

2. **`wa-web-keyword-search`** — Exhaustive keyword search of WA Web modules.
   - Seed: the feature area name plus any WA Web module names from `@implNote` tags
   - Output: `validation/<feature>/discovery/wa-web-keyword-search.md`

3. **`wa-web-code-search`** — Search WA Web by constants/identifiers found in Cobalt.
   - Seed: ALL constants, `ACTION_NAME` values, `QUERY_ID` values, enum names, and string literals from Step 1.1
   - Output: `validation/<feature>/discovery/wa-web-code-search.md`

4. **`wa-web-dependency-trace`** — Deep dependency tracing from known modules.
   - Seed: ALL WA Web module IDs found in `@implNote` tags from Step 1.1
   - Output: `validation/<feature>/discovery/wa-web-dependency-trace.md`

Each teammate's prompt MUST include:
- The exact contents of `.claude/agents/validate-discovery.md`
- The assigned strategy name
- The seed data (file paths, module names, constants — whatever applies to that strategy)
- The output file path

### Step 1.3: Merge discovery results into feature inventory

After all 4 discovery teammates complete:

1. Read all 4 discovery reports from `validation/<feature>/discovery/`
2. **Deduplicate** WA Web modules — multiple strategies will find the same modules via different paths (this is expected and desired; overlapping coverage confirms completeness)
3. **Cross-check** the "Cross-References" section of each report for leads that other agents surfaced but nobody fully explored — investigate any that look promising
4. **Build the FEATURE inventory** (NOT a file-to-file mapping):
   - For each WA Web module, list the **features/behaviors** it provides
   - For each feature, find where in Cobalt it is implemented — it may be in a completely different file structure
   - The mapping is **feature → WA Web location + Cobalt location**, NOT module → file
   - Write the plan to `validation/<feature>/plan.md`:

```markdown
# Validation Plan: <feature>

## Discovery Coverage
- Strategies executed: cobalt-source-scan, wa-web-keyword-search, wa-web-code-search, wa-web-dependency-trace
- Total unique WA Web modules found: N (across M search queries)
- Total Cobalt files found: N

## Feature Inventory

| Feature/Behavior | WA Web Module(s) | WA Web Function(s) | Cobalt File(s) | Cobalt Method(s) | Status |
|---|---|---|---|---|---|
| ... | ... | ... | ... | ... | pending |

## Missing Features (exist in WA Web, not found in Cobalt)
- [feature description] — WA Web: WAWebModuleName.functionName — what it does

## Cobalt-Only Features (exist in Cobalt, no WA Web basis found)
- [feature description] — Cobalt: File.java#method — needs verification
```

**CRITICAL:** Do NOT flag Cobalt files as "unmapped" just because they don't have a 1:1 module counterpart. Cobalt has **complete structural freedom**. The only thing that matters is **feature and behavior parity**.

### Step 1.4: Completeness check (MANDATORY before proceeding to Phase 2)

   - List ALL Java files in the Cobalt packages discovered across all 4 strategies (use `Glob` on the package directories)
   - For EACH file, verify it appears in the feature inventory — either as a Cobalt counterpart to a WA Web module, or explicitly noted as "Cobalt-only" or "infrastructure with no WA Web counterpart"
   - If ANY file in the package is not accounted for, investigate it and add it to the inventory
   - This ensures nothing is silently dropped. The plan MUST account for every file in every discovered package.

## Phase 2: Delegate Validation via Agent Teams

This phase uses **agent teams** — each teammate is a full Claude Code instance that can spawn its own function-level sub-agents, enabling the 3-level hierarchy: orchestrator → module teammate → function sub-agent.

### Task granularity rules (CRITICAL)

Each task covers **exactly ONE WA Web module** against its Cobalt counterpart(s). Do NOT bundle multiple WA Web modules into a single task — this causes teammates to stay shallow and skip the function-level decomposition that catches fine-grained bugs.

**Correct:** one task for `WAWebSyncdResponseParser` ↔ `MutationResponseParser.java`
**Wrong:** one task for "exchange layer" covering 7 modules
**Also wrong:** one task for "action handler registry" that bundles 60 individual handler modules — verifying handlers are REGISTERED is not the same as validating their BEHAVIOR. Each individual handler module (e.g., WAWebArchiveChatSync ↔ ArchiveChatHandler.java) gets its own task.

Exception: very small utility modules (<50 lines, 1-2 exports) that are tightly coupled can be grouped with their parent module. This exception does NOT apply to sets of peer modules (like action handlers) — those are independent modules that each need their own task.

### Creating tasks

Read the teammate prompt template from `.claude/agents/validate-module.md` using the Read tool — you need the **exact file contents**, not a paraphrase.

For EACH WA Web module in the feature inventory, create a task using `TaskCreate` with:
- **subject:** `Validate WAWebModuleName <-> CobaltFileName.java`
- **description:** The **exact contents** of `.claude/agents/validate-module.md` followed by a task section:

```
[exact contents of .claude/agents/validate-module.md — read via Read tool, do NOT paraphrase]

---

YOUR TASK:
- WA Web module: `WAWebModuleName`
- Cobalt file(s): `src/main/java/.../File.java`
- Write findings to: validation/<feature>/WAWebModuleName.md

IMPORTANT: You are validating BEHAVIOR PARITY, not structural parity.
IMPORTANT: You MUST decompose into function-level sub-agents per Step 2. Do NOT do the comparison inline.
IMPORTANT: You MUST FIX all MISMATCH, MISSING_IN_COBALT, and confirmed-phantom MISSING_IN_WA_WEB issues. Reporting without fixing is a FAILED validation.
```

Also create tasks for:
- Each missing feature (exists in WA Web, not found in Cobalt)
- Each Cobalt-only feature (exists in Cobalt, no WA Web basis found)

### Spawning teammates (CRITICAL: one teammate per task)

**Each teammate MUST work on exactly ONE task.** Do NOT have teammates loop through multiple tasks — they will exhaust their context window and fail on later tasks.

After creating all tasks:

1. Create a team via `TeamCreate`
2. For EACH task, spawn ONE dedicated teammate via the `Agent` tool with:
   - `team_name` set to the team name
   - `run_in_background: true`
   - `mode: "bypassPermissions"`
   - A prompt that **includes the full contents of `.claude/agents/validate-module.md` inline** — do NOT tell the teammate to "read the file", as this causes them to skip the sub-agent decomposition step
3. Each teammate's prompt MUST include the specific task ID to claim — do NOT tell teammates to "find the next available task"

**CRITICAL: The teammate Agent prompt MUST include the FULL validate-module.md template contents directly in the prompt text.** Do NOT paraphrase it, and do NOT tell the teammate to "Read .claude/agents/validate-module.md" — teammates that are told to read a file often skip it or deprioritize its instructions. The template must be part of the immediate prompt.

Example teammate prompt structure:
```
[FULL CONTENTS OF .claude/agents/validate-module.md — pasted inline, not a file reference]

---

YOUR TASK:
- Task ID: #N — claim via TaskUpdate (set owner to your name, status to in_progress)
- WA Web module: `WAWebModuleName`
- Cobalt file(s): `src/main/java/.../File.java`
- Write findings to: validation/<feature>/WAWebModuleName.md

IMPORTANT: You are validating BEHAVIOR PARITY, not structural parity.
IMPORTANT: You MUST decompose into function-level sub-agents per Step 2. Do NOT do the comparison inline.
IMPORTANT: You MUST FIX all MISMATCH, MISSING_IN_COBALT, and confirmed-phantom MISSING_IN_WA_WEB issues. Reporting without fixing is a FAILED validation.
IMPORTANT: The Agent tool is a BUILT-IN tool (like Read, Write, Edit, Bash). You can call it directly — do NOT look for it in the deferred tools list or try to fetch its schema via ToolSearch.
```

Teammates are full Claude Code instances — they CAN and SHOULD use the Agent tool to spawn function-level sub-agents for each exported function in the module they're validating.

## Phase 3: Verify Fixes and Synthesis

Once all tasks are completed:

1. Read all finding files from `validation/<feature>/`
2. **Check that every teammate actually FIXED its issues** — not just reported them. If any teammate left unfixed MISMATCH, MISSING_IN_COBALT, or confirmed-phantom MISSING_IN_WA_WEB items, create a follow-up task to fix those specific issues.
3. **Verify compilation** of the entire project: `mvn compile -pl . -q` (the orchestrator uses the default `target/` — only teammates use isolated dirs)
4. Aggregate counts across all features
5. Write `validation/<feature>/report.md` with:
   - Total counts: MATCH, MISMATCH, MISSING_IN_COBALT, MISSING_IN_WA_WEB, ADAPTED
   - All issues that were fixed, with before/after descriptions
   - Any remaining ADAPTED items (these are intentional, not bugs)
   - Overall assessment: is this feature area complete and correct?

## Rules
- Cast a WIDE net during discovery. Better to find too many features than miss one.
- Do NOT do line-by-line comparison yourself — delegate to teammates.
- When searching WA Web modules, try multiple keyword forms (e.g., for "newsletter" also try "channel", "NL").
- Validate FEATURE AND BEHAVIOR PARITY, not file structure parity.
- **Every issue must be FIXED, not just reported.** The validation is not complete until all MISMATCH, MISSING_IN_COBALT, and confirmed-phantom items are resolved in code.
