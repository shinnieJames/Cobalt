You are the **lead validation orchestrator** for the Cobalt project. Your job is to validate that a given feature area is implemented **correctly and exhaustively** by comparing Cobalt's Java code against the real WhatsApp Web JavaScript source via MCP tools.

The feature area to validate: $ARGUMENTS

## Phase 0: Resolve Project Path

Before anything else, get the Windows-style absolute path to this project:

```bash
cygpath -w "$(pwd)" 2>/dev/null || pwd -W 2>/dev/null || pwd
```

Store the result as a literal string (e.g., `C:\Users\Alessandro Autiero\Desktop\Cobalt1`) — you will paste this exact string into every `.bat` launcher script wherever this document says `PROJECT_PATH`. **NEVER use shell variable syntax like `$PROJECT_PATH` or `${PROJECT_PATH}` — always substitute the actual path string directly.** Also create the output directories:

```bash
mkdir -p "validation/<feature>/discovery" "validation/<feature>/tasks" "validation/<feature>/scripts"
```

Replace `<feature>` with a short kebab-case name for the feature area (e.g., `app-state-sync`, `newsletter`, `group-management`).

## IMPORTANT: Windows Compatibility Rules

- **NO shell variables in commands.** This runs on a Windows terminal. `$VARIABLE`, `${VARIABLE}`, and `$(...)` substitutions do NOT work in `.bat` scripts or `start` commands. Always paste the actual literal string value.
- **All paths in `.bat` files and `start` commands must be literal absolute Windows paths** (e.g., `C:\Users\Alessandro Autiero\Desktop\Cobalt1`).

## Phase 1: Discovery via Parallel CLI Instances

Discovery is delegated to **parallel `claude` instances** running in separate terminal windows, each executing a different search strategy.

### Step 1.1: Seed scan (orchestrator — fast, minimal)

Do a quick, shallow scan to collect seed data for the discovery instances:

1. **Glob** for Java files in packages likely related to the feature area (by package name)
2. **Grep** for `@implNote` in those files — extract the WA Web module/function names mentioned
3. **Grep** for string constants (`ACTION_NAME`, `QUERY_ID`, etc.) in those files
4. Do NOT try to be exhaustive here — just collect enough seeds to prime the discovery instances

### Step 1.2: Write task files and launch discovery windows (ALL in parallel)

For EACH of the 4 strategies below, write a **task file** and a **launcher script**, then open a new terminal window.

#### The 4 discovery strategies

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

#### Task file format

Write each task file to `validation/<feature>/tasks/discovery-<strategy>.md` using the Write tool:

```markdown
## Strategy
<strategy name, e.g. cobalt-source-scan>

## Seed Data
<paste ALL relevant seed data here — file paths, module names, constants, etc.>

## Output Path
validation/<feature>/discovery/<strategy>.md
```

#### Launcher script format

Write each `.bat` file to `validation/<feature>/scripts/discovery-<strategy>.bat` using the Write tool. The script MUST:
- Use `@echo off`
- `cd /d` to the project path (use `PROJECT_PATH` from Phase 0)
- Run `claude --dangerously-skip-permissions` with a short initial prompt that tells the instance to read its instruction and task files

Example `.bat` content (adapt the paths for each strategy):

```bat
@echo off
cd /d "<ACTUAL_PROJECT_PATH>"
claude --dangerously-skip-permissions "You are a discovery agent for the Cobalt validation system. Before doing ANYTHING else, read these two files using the Read tool: (1) .claude/agents/validate-discovery.md — your full instructions (2) validation/<feature>/tasks/discovery-<strategy>.md — your specific task and seed data. After reading BOTH files, execute the search strategy described in your task file. Do NOT proceed without reading both files first."
```

**CRITICAL:** Replace `PROJECT_PATH` with the actual Windows-style absolute path (e.g., `C:\Users\Alessandro Autiero\Desktop\Cobalt1`). Replace `<feature>` and `<strategy>` with the actual values. **NEVER use `$PROJECT_PATH` or any shell variable syntax** — always paste the literal path string.

#### Launch all 4 windows

After writing all 4 task files and all 4 `.bat` scripts, launch them in parallel using 4 Bash tool calls (all in one message). You MUST use the **actual literal absolute Windows-style path** in the `start` command — relative paths and shell variables do NOT work:

```bash
start "Discovery: cobalt-source-scan" "C:\Users\...\Cobalt1\validation\<feature>\scripts\discovery-cobalt-source-scan.bat"
```

```bash
start "Discovery: wa-web-keyword-search" "C:\Users\...\Cobalt1\validation\<feature>\scripts\discovery-wa-web-keyword-search.bat"
```

```bash
start "Discovery: wa-web-code-search" "C:\Users\...\Cobalt1\validation\<feature>\scripts\discovery-wa-web-code-search.bat"
```

```bash
start "Discovery: wa-web-dependency-trace" "C:\Users\...\Cobalt1\validation\<feature>\scripts\discovery-wa-web-dependency-trace.bat"
```

**CRITICAL:** The paths above are illustrative — substitute the real absolute path. **NEVER use `$PROJECT_PATH` or any `$VARIABLE` syntax** — Windows terminals do not expand shell variables. Always paste the literal path string.

### Step 1.3: Wait for discovery outputs

After launching, tell the user how many windows were opened and what to expect. Then poll for all 4 output files using a single Bash call:

```bash
expected=4; elapsed=0; timeout=3600; while [ "$(ls validation/<feature>/discovery/*.md 2>/dev/null | wc -l)" -lt "$expected" ] && [ "$elapsed" -lt "$timeout" ]; do echo "Waiting for discovery reports... ($(ls validation/<feature>/discovery/*.md 2>/dev/null | wc -l)/$expected complete) - ${elapsed}s elapsed"; sleep 30; elapsed=$((elapsed + 30)); done; count=$(ls validation/<feature>/discovery/*.md 2>/dev/null | wc -l); if [ "$count" -ge "$expected" ]; then echo "All $expected discovery reports ready"; else echo "TIMEOUT: only $count/$expected reports after ${timeout}s"; fi
```

Set a generous timeout (up to 10 minutes, i.e., `timeout: 600000`) on this Bash call.

### Step 1.4: Merge discovery results into feature inventory

After all 4 discovery reports exist:

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

### Step 1.5: Completeness check (MANDATORY before proceeding to Phase 2)

   - List ALL Java files in the Cobalt packages discovered across all 4 strategies (use `Glob` on the package directories)
   - For EACH file, verify it appears in the feature inventory — either as a Cobalt counterpart to a WA Web module, or explicitly noted as "Cobalt-only" or "infrastructure with no WA Web counterpart"
   - If ANY file in the package is not accounted for, investigate it and add it to the inventory
   - This ensures nothing is silently dropped. The plan MUST account for every file in every discovered package.

## Phase 2: Delegate Validation via CLI Instances

This phase spawns **`claude` CLI instances in new terminal windows** — each is a full interactive Claude Code process that can use the Agent tool to spawn its own function-level sub-agents, enabling the 3-level hierarchy: orchestrator → module CLI instance → function sub-agent.

### Task granularity rules (CRITICAL)

Each CLI instance covers **exactly ONE WA Web module** against its Cobalt counterpart(s). Do NOT bundle multiple WA Web modules into a single instance — this causes validators to stay shallow and skip the function-level decomposition that catches fine-grained bugs.

**Correct:** one instance for `WAWebSyncdResponseParser` ↔ `MutationResponseParser.java`
**Wrong:** one instance for "exchange layer" covering 7 modules
**Also wrong:** one instance for "action handler registry" that bundles 60 individual handler modules — verifying handlers are REGISTERED is not the same as validating their BEHAVIOR. Each individual handler module (e.g., WAWebArchiveChatSync ↔ ArchiveChatHandler.java) gets its own instance.

Exception: very small utility modules (<50 lines, 1-2 exports) that are tightly coupled can be grouped with their parent module. This exception does NOT apply to sets of peer modules (like action handlers) — those are independent modules that each need their own instance.

### Writing task files and launcher scripts

For EACH WA Web module in the feature inventory:

#### 1. Write a task file

Write to `validation/<feature>/tasks/validate-<ModuleName>.md`:

```markdown
## WA Web Module
`WAWebModuleName`

## Cobalt File(s)
`src/main/java/.../File.java`

## Output Path
validation/<feature>/WAWebModuleName.md

## Instructions
- You are validating BEHAVIOR PARITY, not structural parity.
- You MUST decompose into function-level sub-agents per Step 2. Do NOT do the comparison inline.
- You MUST FIX all MISMATCH, MISSING_IN_COBALT, and confirmed-phantom MISSING_IN_WA_WEB issues. Reporting without fixing is a FAILED validation.
- The Agent tool is a BUILT-IN tool (like Read, Write, Edit, Bash). You can call it directly — do NOT look for it in the deferred tools list or try to fetch its schema via ToolSearch.
```

#### 2. Write a launcher script

Write to `validation/<feature>/scripts/validate-<ModuleName>.bat`:

```bat
@echo off
cd /d "<ACTUAL_PROJECT_PATH>"
claude --dangerously-skip-permissions "You are a module-level validator for the Cobalt project. Before doing ANYTHING else, read these two files using the Read tool: (1) .claude/agents/validate-module.md — your full validation instructions (2) validation/<feature>/tasks/validate-<ModuleName>.md — your specific task. After reading BOTH files, execute the validation as described. Do NOT proceed without reading both files first."
```

**CRITICAL:** Replace `<ACTUAL_PROJECT_PATH>` with the actual Windows-style absolute path (e.g., `C:\Users\Alessandro Autiero\Desktop\Cobalt1`). Replace `<feature>` and `<ModuleName>` with actual values. **NEVER use `$PROJECT_PATH` or any `$VARIABLE` syntax** — Windows terminals do not expand shell variables.

Also create task files and launcher scripts for:
- Each missing feature (exists in WA Web, not found in Cobalt)
- Each Cobalt-only feature (exists in Cobalt, no WA Web basis found)

### Launching instances in batches

**CRITICAL: One instance per module.** Do NOT try to bundle modules into a single instance.

Launch instances in **batches of at most 4** to avoid API rate limits. For each batch, send one Bash tool call per module in that batch, all in a single message (use the actual literal absolute Windows path — **NEVER use `$VARIABLE` syntax**):

```bash
start "Validate: <ModuleName>" "C:\Users\...\Cobalt1\validation\<feature>\scripts\validate-<ModuleName>.bat"
```

**Batching procedure:**
1. Sort all modules into batches of up to 4
2. Launch batch 1 (up to 10 `start` commands in parallel Bash calls)
3. Wait until all output files from batch 1 are written (poll loop below)
4. Launch batch 2, wait for completion, and so on until all batches are done

If there are 4 or fewer modules total, launch them all at once (single batch).

After launching each batch, poll for that batch's expected output files:

```bash
expected=<N>; elapsed=0; timeout=7200; while [ "$(ls validation/<feature>/*.md 2>/dev/null | grep -v plan.md | grep -v report.md | wc -l)" -lt "$expected" ] && [ "$elapsed" -lt "$timeout" ]; do echo "Waiting for validation reports... ($(ls validation/<feature>/*.md 2>/dev/null | grep -v plan.md | grep -v report.md | wc -l)/$expected complete) - ${elapsed}s elapsed"; sleep 30; elapsed=$((elapsed + 30)); done; count=$(ls validation/<feature>/*.md 2>/dev/null | grep -v plan.md | grep -v report.md | wc -l); if [ "$count" -ge "$expected" ]; then echo "All $expected validation reports ready"; else echo "TIMEOUT: only $count/$expected reports after ${timeout}s"; fi
```

Set `expected` to the total number of module reports expected. Use `timeout: 600000` on this Bash call.

## Phase 3: Verify Fixes and Synthesis

Once all validation reports exist:

1. Read all finding files from `validation/<feature>/`
2. **Check that every instance actually FIXED its issues** — not just reported them. If any instance left unfixed MISMATCH, MISSING_IN_COBALT, or confirmed-phantom MISSING_IN_WA_WEB items, write a follow-up task file + launcher script and spawn a new terminal window to fix those specific issues.
3. **Verify compilation** of the entire project: `mvn compile -pl . -q "-Dcobalt.build.dir=target-validate-final"` — then delete `target-validate-final` after success
4. Aggregate counts across all features
5. Write `validation/<feature>/report.md` with:
   - Total counts: MATCH, MISMATCH, MISSING_IN_COBALT, MISSING_IN_WA_WEB, ADAPTED
   - All issues that were fixed, with before/after descriptions
   - Any remaining ADAPTED items (these are intentional, not bugs)
   - Overall assessment: is this feature area complete and correct?

## Rules
- Cast a WIDE net during discovery. Better to find too many features than miss one.
- Do NOT do line-by-line comparison yourself — delegate to CLI instances.
- When searching WA Web modules, try multiple keyword forms (e.g., for "newsletter" also try "channel", "NL").
- Validate FEATURE AND BEHAVIOR PARITY, not file structure parity.
- **Every issue must be FIXED, not just reported.** The validation is not complete until all MISMATCH, MISSING_IN_COBALT, and confirmed-phantom items are resolved in code.
