# Cobalt Protocol Diff

You are comparing Cobalt's outgoing stanza for a given semantic action against WhatsApp Web's outgoing stanza for the same action. WhatsApp Web is the oracle; Cobalt is the hypothesis. Your output is a structural diff plus a pointer to the WA Web source that produced the reference stanza.

The user invokes this command as: `/cobalt-protocol-diff <action description>` — e.g. `send a text message to +391234567890`, `mark chat as archived`, `subscribe to presence for +391234567890`.

## Preconditions

1. **MCP server reachable.** `curl -s -o /dev/null -w "%{http_code}" http://localhost:8787/mcp` returns `200`. If not, start it as in `/validate` or `/cobalt-pair-and-run`.
2. **MCP WA Web session is logged in.** Call `mcp__whatsapp__web_live_status`. `authState` must be `logged_in`. If not, stop and tell the user to log in via `mcp__whatsapp__web_live_start_session` + `mcp__whatsapp__web_live_login_with_phone_number` + `mcp__whatsapp__web_live_wait_for_login` first. Do NOT try to log in yourself — the account used for MCP's WA Web and the one used by Cobalt may be different, and the user has to decide.
3. **Active snapshot matches runtime.** Call `mcp__whatsapp__web_live_validate_snapshot_revision`. If the revision does not match, warn the user (stanza structure may differ from source); continue only if they confirm.

## Execution Plan

### Phase 1: Clear stale capture

Call `mcp__whatsapp__web_live_clear_capture` with `domain=stanza`, `scope=buffer`. This drops any stanzas from prior actions so your query returns only what your trigger produced. Leave `scope=buffer` (not `history`) so historical stanzas remain available if you need them for comparison.

### Phase 2: Trigger the action on WA Web

Three options, prefer them in this order:

**Option A — User does it in the browser.** Tell the user in one short sentence: "Perform the action in WhatsApp Web now." Wait for them to confirm. This is the most accurate because it exercises the exact code path WA Web takes in production.

**Option B — `web_live_debug_eval`.** If the user doesn't want to click through, find the WA Web function that performs the action and invoke it directly:
  1. `mcp__whatsapp__search_modules` with a query that describes the action (e.g. `"send text message"`, `"archive chat"`).
  2. `mcp__whatsapp__get_exports` on the top candidate module to list its public bindings.
  3. `mcp__whatsapp__resolve_export` to map an export to its implementation symbol.
  4. `mcp__whatsapp__get_symbol_source` to confirm the function signature.
  5. `mcp__whatsapp__web_live_debug_eval` with an expression that resolves the module and calls the function with appropriate arguments. Wrap in `(async () => { ... })()` if the function returns a Promise, and pass `awaitPromise=true`.

  Option B is fragile — WA Web's modules are minified and often require a fully populated store (a loaded chat, a known JID, etc.). If the eval throws, fall back to Option A.

**Option C — `web_live_stanza_send_node`.** Do NOT use this for diffing. It lets you inject arbitrary stanzas, which defeats the purpose of capturing what WA Web *would* produce.

### Phase 3: Capture the WA Web stanza

Call `mcp__whatsapp__web_live_stanza_query_nodes` with:
- `direction`: `out` (we want what WA Web sent to the server).
- `tag`: filter to the stanza type you expect (e.g. `message`, `iq`, `presence`, `chatstate`). If you don't know, omit and filter after.
- `limit`: `20` (enough to catch the action plus any follow-up stanzas).
- `history`: `false` (only the post-clear buffer).

Expect the result to contain multiple stanzas — an action like "send a message" can emit a `message` stanza plus `receipt`s, `iq` queries for recipient keys, etc. Pick the stanza(s) that directly represent the action. If ambiguous, include all of them in the report.

If the query returns zero stanzas, the action didn't fire or the injected capture script isn't attached:
- Verify via `mcp__whatsapp__web_live_status` that the session is still `logged_in` and the injected MCP script is loaded (the `status` payload reports this).
- Retry Phase 2 once. If still empty, stop and report the symptom.

### Phase 4: Identify the WA Web source

For each captured stanza, find the JS that built it:

1. `mcp__whatsapp__search_code` with `pattern` = a distinctive attribute or tag combination from the stanza (e.g. the exact `type` + `subtype` pair, or a rare attribute name). Use `mode=literal` first; fall back to `mode=regex` if needed. Set `scope` to narrow if the codebase is large.
2. For each hit, `mcp__whatsapp__resolve_export` and `mcp__whatsapp__get_symbol_source` to fetch the builder function.
3. Record the module name, export name, and a 5–15 line excerpt showing the stanza construction.

This gives the user the reference implementation they need to match in Cobalt.

### Phase 5: Capture the Cobalt stanza (if available)

You have two sub-paths:

**5a. The user already has a Cobalt log to compare.** If they pointed you at one, parse lines emitted by `addNodeSentListener` / `addNodeReceivedListener`. The canonical log format in Cobalt examples is `Sent node %s` (see `WebQrLoginExample.java`). Filter outbound entries, find the one matching the action, and extract the node toString.

**5b. The user wants you to run Cobalt now.** Delegate to `/cobalt-pair-and-run` logic: author a short Cobalt test that performs the same action, wire `addNodeSentListener` to emit `COBALT_NODE_OUT=<toString>`, run it under the pair-and-login flow, and parse the log. Don't duplicate the pair-and-run skill inline — either chain it by recommending the user run it first, or call it programmatically.

If Cobalt hasn't implemented the action yet, skip Phase 5 and stop after Phase 6. The user's goal in that case is just to see the oracle.

### Phase 6: Structural diff

Both sides of the comparison produce a `Node` (Cobalt) or a stanza JSON blob (WA Web). Normalize before diffing:

- **Strip volatile attributes:** `id` (request id), `t` (timestamp), `notify` (display name cache), `offline`, `key-index` unless the action is specifically about keys.
- **Sort attribute keys alphabetically.**
- **Recurse into content.** Children are a list and order matters for some stanzas (receipts, enc types) — preserve order.
- **Normalize JIDs:** both sides may render the same JID differently (`@s.whatsapp.net` vs `@c.us`, hyphenated device IDs, etc.). Unify to the canonical form WA Web uses in its wire format.

Produce a unified diff with three sections per stanza:
1. **Equal** — attributes present on both sides with matching values (list, don't dump).
2. **Divergent** — attributes on both sides with different values (show both).
3. **Asymmetric** — attributes present on one side only (show which side).

If there are no divergent or asymmetric entries after normalization, report `STANZA MATCH` and stop.

### Phase 7: Report

Write to stdout (no file). Structure:

```
ACTION: <the user's description>

WA WEB STANZA (oracle):
<pretty-printed stanza>

SOURCE:
<module>::<export>  (<snapshot revision>)
<5-15 line excerpt>

COBALT STANZA (hypothesis):
<pretty-printed node>  [or: "not captured this run"]

DIFF:
  equal:      [list]
  divergent:  [pairs]
  asymmetric: [which side, which field]

VERDICT: MATCH | DIVERGENT | NOT COMPARED
```

## Rules

- Never invent a stanza. If capture fails, say so.
- Do not normalize away semantically meaningful differences (e.g. the presence or absence of an `enc` child, `type="result"` vs `type="error"`). The goal is to detect Cobalt bugs, not to hide them.
- Do not write a fix. This skill reports divergence; fixing Cobalt is a separate step the user decides on.
- Do not clear stanza history (`scope=history`) — other sessions may depend on it. Only clear the active buffer.
- Do not call `web_live_stop_session`. Leave the MCP session in the state you found it.
- Do not include the user's phone number or any chat content in the report unless the user has indicated the target chat is test-only.
