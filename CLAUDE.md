# Cobalt

Cobalt is a Java reimplementation of WhatsApp Web/Desktop/Mobile.

## Build

- Maven project: `mvn compile` from root
- Java 25 with preview features enabled
- Module system with `module-info.java`

## Architecture

### Async Model
Virtual threads (Project Loom) with direct blocking calls — no `CompletableFuture`. JS `await` maps to a plain blocking call on a virtual thread.

### Protobuf System
Custom protobuf library (`com.github.auties00:protobuf-serialization-plugin`), NOT Google protobuf:
- **Annotations:** `@ProtobufMessage(name = "...")`, `@ProtobufProperty(index = N, type = ProtobufType.XXX)`
- **Fields:** Package-private, accessed via `fieldName()` getters (NOT `getFieldName()`). Nullable fields return `Optional<T>`. Lists return unmodifiable `List<T>` defaulting to `List.of()`.
- **Construction:** Constructors are package-private. Use the generated builder: `ClassNameBuilder` (e.g., `MessageKey` → `MessageKeyBuilder`). For nested classes, concatenate parent names: `TemplateButton.CallButton` → `TemplateButtonCallButtonBuilder`. Mutable variants also have setter methods returning `this`.
- **Enums:** `@ProtobufEnum` with `@ProtobufEnumIndex` on the int constructor parameter.
- **Oneofs:** Sealed interfaces (e.g., `TemplateButtonVariant`) with inner static classes implementing each variant.
- **Mixins:** `@ProtobufMixin` classes for type conversion (e.g., `InstantSecondsMixin` converts `Instant` ↔ `Long`).
- **Custom serializers:** `@ProtobufSerializer`/`@ProtobufDeserializer` on types (e.g., `Jid` has custom `of(ProtobufString)` deserializer).

### Store System
Single `AbstractWhatsAppStore` flattens WA Web's multi-database architecture:
- WA Web uses ~12 IndexedDB databases, ~100 IDB tables, ~45 in-memory reactive Collections, and a key-value UserPrefs store.
- Cobalt collapses ALL of this into one `AbstractWhatsAppStore` with `ConcurrentHashMap` fields per entity type.
- **Key WA Web store modules:** `WAWebSignalStorage` (Signal protocol), `WAWebModelStorageInitialize` (chats/contacts/messages/sync), `WAWebCollections` (in-memory), `WAWebUserPrefsBase` (user preferences).
- **Cobalt DI pattern:** Services (store, client, etc.) are injected via constructor, NOT accessed via getters. Classes receive dependencies as constructor parameters and store them as fields.

### Error Model (INTENTIONALLY DIFFERENT FROM WA WEB)
Cobalt's error handling is **deliberately redesigned** to be configurable — do NOT replicate WA Web's inline recovery logic:
- **Sealed exception hierarchy:** All extend `WhatsAppException` (sealed abstract `RuntimeException`). Each has `isFatal()`.
- **Pluggable error handler:** `WhatsAppClientErrorHandler` returns `Result` (`DISCARD`, `DISCONNECT`, `RECONNECT`, `LOG_OUT`, `BAN`). Recovery is user-configurable, NOT hardcoded.
- **Key exception types:** `WhatsAppSessionException` (sealed: `BadMac`, `Closed`, `Conflict`, `LoggedOut`, `Banned`, `Reconnect`), `WhatsAppMessageException.Receive` (Signal crypto), `WhatsAppMediaException` (sealed: `Connection`, `Upload`, `Download`, `Processing`), `WhatsAppWebAppStateSyncException` (15 subtypes), `WhatsAppAdvValidationException` (6 subtypes).
- **Validation rule:** When WA Web has inline error recovery (try/catch with retry/disconnect/ignore), Cobalt throws the appropriate exception subtype instead. Only flag as missing if the exception THROW itself is missing, not the recovery logic.

### Naming Conventions
Cobalt class names mirror WA Web modules but drop the `WA`/`WAWeb` prefix:

| Cobalt Pattern                            | Maps to WA Web                      |
|-------------------------------------------|-------------------------------------|
| `*Sender` (e.g., `PeerMessageSender`)     | Send/dispatch modules               |
| `*Receiver` (e.g., `ChatMessageReceiver`) | Message handling/processing modules |
| `*Handler` (e.g., `ArchiveChatHandler`)   | Sync action handlers                |
| `*Service` (e.g., `DeviceService`)        | Stateful service modules            |
| `*Mex` (e.g., `FetchAboutStatusMex`)      | MEX/GraphQL operations              |
| `*Action` (e.g., `ArchiveChatAction`)     | Sync action protobuf models         |
| `*Stanza` (e.g., `ChatFanoutStanza`)      | Stanza/node builders                |

### Source Provenance Annotations (`cobalt-source-meta`)
Cobalt tracks its relationship to WhatsApp source code via annotations in `com.github.auties00.cobalt.meta`, split into two families:

**WhatsApp Web** (Web + Windows Desktop — same JS codebase; the Electron-era macOS desktop also shared this bundle):
- `@WhatsAppWebModule(moduleName = "WAWebFoo")` on types — which JS module(s) the class adapts
- `@WhatsAppWebExport(moduleName = "WAWebFoo", exports = "bar", adaptation = ...)` on methods/fields/constructors — which export is implemented
- `WhatsAppWebPlatform` enum: `SHARED` (default), `WINDOWS`, `MAC_OS` — for desktop-specific divergences

**WhatsApp Mobile** (iOS / Android — native codebases; the macOS desktop app is a Mac Catalyst port of the iOS binary and uses `IOS`):
- `@WhatsAppMobileClass(className = "WAFoo", platform = ...)` on types — which native class is adapted
- `@WhatsAppMobileMethod(className = "WAFoo", methods = "-bar:", platform = ..., adaptation = ...)` on methods/fields/constructors
- `WhatsAppMobilePlatform` enum: `IOS`, `ANDROID` — no default, must be specified

**Shared model:**
- `WhatsAppAdaptation` enum: `DIRECT` (same logic), `ADAPTED` (same purpose, different structure)
- All annotations are `@Repeatable` and `SOURCE` retention
- The annotation processor generates `META-INF/wa-source-manifest.json` at compile time

**Statement-level traceability** (inline `// WAWebFoo.bar: ...` comments inside method bodies) remains as comments — Java annotations cannot target arbitrary statements.

### Nodes/Stanzas
Built via `NodeBuilder` with `.description()`, `.attribute()`, `.content()`.
`Node` has convenience methods to get and stream attributes and content: use the best convenience method to improve code readability.

## Javadoc Requirements
ALL members (public, protected, package-private, private) in main source (`src/main/java`) MUST have JDK 21+ multiline javadoc. No `@since` tags. Use source provenance annotations (`@WhatsAppWebModule`/`@WhatsAppWebExport` for Web, `@WhatsAppMobileClass`/`@WhatsAppMobileMethod` for Mobile) to declare WA source mappings. Test code (`src/test/java`) follows the lighter rules in the `### Tests` subsection below.

### Member documentation structure
Each member's javadoc has these layers, in order:
1. **Summary line** — one verb-led sentence, third person, present tense, ends with period.
2. **Body paragraph(s)** — the normative specification: what the member does, its contract, boundary conditions, and corner cases, written implementation-independent. Behavioural prose belongs here, NOT in a tag ("Opens the encrypted tunnel, installs a JVM shutdown hook, and begins the inbound read loop."). Omit it when the summary line already says everything; never pad. Never use it for "wire-level counterpart of X" or "delegates to Y" chronicling — the annotations and signature say that.
3. **`@apiNote`** (optional; public API only) — non-normative caller guidance the spec itself does not carry: when to prefer this over an alternative, when NOT to call it, rationale for a surprising shape. Restrict it to the genuinely public surface (`WhatsAppClient`, `WhatsAppStore`, listeners, public builders); package-private and private members have no API audience, so they get none. It is NOT mandatory — write it only when there is a real note to make. If the content describes what the member does, it is spec: move it to the body paragraph.
4. **`@implNote`** (`modules/lib`-only) — implementer-facing: non-obvious algorithmic choice, numeric-constant origin, or deliberate permanent Cobalt-vs-WA divergence. Starts with "This implementation...". Skip when the body is self-explanatory. Pure WA provenance ("maps to `WAWebFoo.bar`") goes in the `@WhatsAppWebExport`/`@WhatsAppWebModule` annotations, NOT here. **`@implNote` and the `com.github.auties00.cobalt.meta.*` annotations are `modules/lib`-only** — never add them to `modules/model` or other Maven submodules.

For interfaces and abstract methods, also use `@implSpec` (normative contract for overriders). `@implSpec` says what subclasses MUST do; `@implNote` says what THIS implementation happens to do. `@implSpec` is the one tag a body paragraph cannot replace — it does not inherit via `{@inheritDoc}` — so keep it wherever there is an override contract.

The body paragraph is the default home for description; tags carry only what their definition demands. Class-level javadoc may additionally have a lifecycle-mapping body. If a would-be `@apiNote` reads like "this is the entry point that does X, Y, Z", it is the spec and belongs in the body.

### Style Rules
- Third person declarative present tense ("Returns the value", not "Return the value").
- Summary sentence must be complete and standalone.
- Methods start with a verb phrase: "Returns...", "Compares...", "Performs...".
- Wrap Java keywords/types in `{@code ...}`: `{@code null}`, `{@code true}`, `{@code Optional}`.
- **`{@link}` aggressively** — every linkable reference in the javadoc body and block tags. Do not save linking for "first reference only".
- **`{@snippet :}` for non-trivial input formats** — derived ids, rolling clocks, base64 blobs, JIDs with a specific server, JSON shapes. Skip for trivial getters/setters.
- **`@param <T>`** required for every type parameter on every generic.
- **ASCII only** in javadoc and inline comments. No em-dash, no en-dash, no curly quotes, no ellipsis character, no arrows, no decorative bullets. Use semicolon, comma, period, parenthetical, sentence break, straight ASCII quotes, three dots (`...`), `->`, and HTML `<ul><li>` lists. ASCII hyphen-minus inside compound adjectives is fine (`fire-and-forget`, `two-tier`).

### `{@inheritDoc}` for `@Override` methods
- **No deviation from parent contract:** write no javadoc; the doclet auto-inherits.
- **Same contract, added context:** `{@inheritDoc}` in the summary plus a new `@apiNote` and/or `@implNote`. Block-tag inheritance works too: `@param x {@inheritDoc} additional detail`.
- **Contract divergence** (stricter preconditions, additional throws, different null/return semantics): write the override doc from scratch.
- **JDK 22+ `{@inheritDoc S}`** selects a specific supertype when multiple parents document the method; rarely needed.
- `@implSpec` and `@implNote` are never inherited. Each implementation declares its own.

### Inline comments
- **Default: write nothing.** Well-named identifiers carry the *what*. A comment is only justified for hidden constraints, external-bug workarounds, numeric-constant origin (`// 400 matches server's reported limit`), or `// TODO:` / `// FIXME:`.
- **Always delete:** end-of-line `// WAWebFoo.bar: <JS snippet>` chronicler comments; comments that paraphrase the line above them; `// ADAPTED: ...` justification tags (relocate to `@implNote` if load-bearing, otherwise delete); XML stanza-shape diagrams duplicated at every consumer; **decorative section dividers** like `// -------- video_state --------`, `// === Send Message ===`, or any line of dashes/equals/stars used to visually group statements or members (if a class needs section dividers to be navigable, split the class instead).

### Classification: `@implNote` vs `// TODO:` vs `// FIXME:`
When recording a Cobalt-vs-WA divergence:
- **`@implNote`** — divergences with architectural justification (Cobalt has no tab concept, no IndexedDB-as-stable-store, no Falco internal pipeline, deliberately redesigned error model). Permanent.
- **`// TODO:`** — unimplemented features. No architectural blocker; Cobalt could fix but has not yet (buffer persistence across restarts, retry-after-reconnect for fire-and-forget broadcasts).
- **`// FIXME:`** — known-wrong behaviour; current code observably does the wrong thing.
- **Litmus test:** if the divergence prose contains data-loss language ("silently discarded", "lost on restart", "not preserved", "dropped on close"), it is NOT a design choice. Default to `// TODO:`, not `@implNote`. `@implNote` is reserved for architectural reasons.
- After your work, `grep -n "TODO\|FIXME" <package>` should be the canonical "what is missing here" map.

### No "how we got here" prose
Delete on sight in both javadoc and inline comments:
- "Note: previously this returned X but now returns Y..."
- "Updated to handle the case where...", "Now correctly handles...", "Initially...", "Originally..."
- "Per feedback, we now...", "The original version did X, but this implementation..."
- "TODO: clean up after review" left in after the review
- Embedded apologies ("apologies for the earlier confusion") or self-references ("as discussed, this now...")

Git history is the record of how the code got here. Javadoc is the record of what the code IS today. State the present-tense contract; never carry forward the apology.

### Block Tag Order
`@apiNote` → `@implSpec` → `@implNote` → `@param` → `@return` → `@throws` → `@see`

### Required Tags
- `@param` for every parameter, `@param <T>` for every type parameter on every generic.
- `@return` for every non-void method.
- `@throws` for every checked exception and every significant unchecked exception.
- `@WhatsAppWebExport` / `@WhatsAppMobileMethod` on every method/constructor/field that maps to WA source.

### Tests
Test code (`src/test/java`) is exempt from the "every member is documented" rule. For tests the JUnit annotations and method names ARE the documentation, because `@DisplayName` surfaces in the IDE tree, Surefire, and CI whereas test-method javadoc renders nowhere at runtime. Preference order, highest first:
1. **Short method name + `@DisplayName`** — the primary record of what a test asserts. Keep the method name terse (`singleIq`) and put the full intent in `@DisplayName` ("produces a single IQ with the canonical usync attribute order"). Do NOT also write a javadoc summary that restates the `@DisplayName` — that is duplication.
2. **`@Nested` classes** — carry structure; their `@DisplayName` groups read as sentences in the runner.
3. **`@ParameterizedTest` with `@MethodSource`/`@CsvSource`/`@EnumSource`, and `@TestFactory`/`DynamicTest`** — collapse families of near-identical cases into one method so there is one name to maintain, not N. Prefer these over copy-pasted `@Test` methods.
4. **Inline `// ...` comments** — only for non-obvious *why*: a magic fixture value, captured-fixture provenance, a regression reference. Same bar as production inline comments.
5. **Javadoc** — minimal. A test class MAY carry one short plain class-level paragraph stating what the suite covers and any non-obvious harness design (deterministic clock, canned IQ queue). That is the only javadoc tests need; per-`@Test` javadoc is not required and usually just duplicates `@DisplayName`.

Never put `@apiNote`, `@implNote`, `@implSpec`, or `com.github.auties00.cobalt.meta.*` annotations on test types or test methods: a test has no API audience and maps to no WA export. Demote any such content to the plain class-level paragraph or an inline comment.

This carve-out is applied opportunistically: when you touch a test file, strip per-`@Test` javadoc that duplicates its `@DisplayName` and demote class-level `@apiNote`/`@implNote` to a plain paragraph. Do not mass-rewrite untouched test files.

### Reference linting
After adding or editing javadoc, verify every `{@link}`, `{@linkplain}`, `{@value}`, `@see`, `@param`, `@throws`, and `@exception` actually resolves. The `javadoc-check` Maven profile (parent `pom.xml`) runs the standard doclet with `-Xdoclint:reference` and fails on any unresolved reference. The target module must compile first (the protobuf processor must have emitted the generated `*Builder` sources the comments link to); a no-compile run reports false positives for classes that exist. To lint one module in isolation:

```
mvn -DskipTests -pl modules/lib -am install
mvn -Pjavadoc-check -pl modules/lib compile javadoc:javadoc-no-fork -DskipTests
```

Cross-module references must respect the dependency direction: `model` cannot link to `lib` types (`lib` depends on `model`, not the reverse), so a `{@link WhatsAppClient}` from `modules/model` can never resolve.

## Validation System
The `/validate` command validates Cobalt's Java against WhatsApp Web's JS source via MCP tools.
- **Orchestrator:** `.claude/commands/validate.md` — discovery, manifest building, dependency-ordered agent spawning, cross-cutting flow validation, phantom sweep, synthesis.
- **Module validator agent:** `.claude/agents/validate-module.md` — per-module exhaustive comparison and fixes.
- **Cross-cutting flow agent:** `.claude/agents/validate-flow.md` — multi-file architectural pattern fixes (delegation, type mismatches across boundaries, batched-vs-per-item).
- **Phantom sweep agent:** `.claude/agents/validate-phantom.md` — whole-codebase dead-code removal, verified against WA Web before deletion.
- **MCP server:** `.mcp.json` registers `whatsapp` MCP (HTTP at localhost:8787) from `tools/web/mcp-server`.
- **Exhaustiveness guarantee:** The orchestrator builds a manifest of ALL WA Web exports via `get_exports`, then validates every entry. The completeness check at the end verifies every export has a verdict.
- **Source manifest:** The annotation processor generates `META-INF/wa-source-manifest.json` mapping Cobalt types/members to WA Web modules/exports and WA Mobile classes/methods. The validation system consumes this manifest for cross-referencing.
- **Dependency ordering:** Validation runs in topological order (leaves first, consumers last) so each agent's dependencies are already in place when it runs.
