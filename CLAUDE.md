# Cobalt

Cobalt is a Java implementation of the WhatsApp platform. It supports two independent transports behind one sealed API:

1. **Linked clients** (`LinkedWhatsAppClient`) — reimplementation of WhatsApp Web/Desktop (companion, QR or pairing-code) and WhatsApp Mobile (primary, phone-number registration) over the encrypted binary-XMPP socket with Signal/Noise cryptography.
2. **Cloud API client** (`CloudWhatsAppClient`) — Meta's official WhatsApp Cloud API over `graph.facebook.com` REST plus an embedded webhook receiver.

## Build

- Maven project: `mvn compile` from root
- Java 25
- Module system with `module-info.java` in every Maven module

## Maven Modules

| Module                | artifactId           | JPMS name                          | Purpose                                                                                                                                                                                                    |
|-----------------------|----------------------|------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `modules/model`       | `cobalt-model`       | `com.github.auties00.cobalt.model` | Hand-written protobuf domain model. No dependency on `lib`.                                                                                                                                                |
| `modules/lib`         | `cobalt-lib`         | `com.github.auties00.cobalt`       | The client library: clients, socket, stream handlers, store, sync, media, calls, Cloud API. Depends on `model`, `wam-core`, `source-meta`.                                                                 |
| `modules/wam-core`    | `cobalt-wam-core`    | `com.github.auties00.cobalt.wam`   | WAM (WhatsApp Metrics) telemetry: `@WamEvent`/`@WamProperty`/`@WamEnum` annotations, binary wire encoder/decoder, and a JavaPoet annotation processor generating `*Impl`/`*Builder`/`WamEventRegistry`.    |
| `modules/source-meta` | `cobalt-source-meta` | `com.github.auties00.cobalt.meta`  | Source provenance annotations (`@WhatsAppWebModule`, `@WhatsAppWebExport`, `@WhatsAppMobileClass`, `@WhatsAppMobileMethod`) and the processor emitting `META-INF/wa-source-manifest.json` at compile time. |

Dependency direction: `lib -> {model, wam-core, source-meta}`; `model`, `wam-core`, and `source-meta` are independent of each other. Javadoc cross-references must respect this direction (`model` can never `{@link}` a `lib` type).

## Architecture

### Client Hierarchy
Everything is sealed for exhaustive pattern matching. The `client` package is split by transport: `client` holds the shared surface (`WhatsAppClient`, `WhatsAppClientBuilder`, `WhatsAppClientErrorHandler`, `WhatsAppClientDisconnectReason`, `WhatsAppClientProxy`/`WhatsAppClientProxyAuthenticator`), `client.linked` the Linked flavour, `client.cloud` the Cloud flavour.
- `WhatsAppClient` (sealed) permits `LinkedWhatsAppClient` and `CloudWhatsAppClient`; carries the transport-agnostic surface: lifecycle (`connect`/`disconnect`/`reconnect`/`waitForDisconnection`), message send, reactions, read receipts (`markChatAsRead` + message-keyed `markMessageAsRead`), typing indicator, own business profile (edit + query), block list (`blockContact`/`unblockContact`/`queryBlockedContacts`), shared-listener registration, and `removeListener(WhatsAppListener)`.
- `LinkedWhatsAppClient` -> implemented by package-private `LiveLinkedWhatsAppClient`. Flavour selected by `LinkedWhatsAppClientType`: `WEB` (companion device) or `MOBILE` (primary device, SMS/voice/OTP registration via the `registration` package). Linked-only support types carry the `LinkedWhatsAppClient*` prefix (`LinkedWhatsAppClientDevice`, `LinkedWhatsAppClientVerificationHandler`, `LinkedWhatsAppClientSixPartsKeys`, ...).
- `CloudWhatsAppClient` -> implemented by `LiveCloudWhatsAppClient`. Backed by the `cloud` package: `CloudApiClient` (HTTPS to `graph.facebook.com`, Bearer token + optional `appsecret_proof`), `CloudMessageEncoder` (`MessageContainer` -> `/messages` JSON), `CloudWebhookDecoder` (webhook payloads -> `ChatMessageInfo`/typed update models), `CloudWebhookServer` (`jdk.httpserver` on virtual threads; `hub.challenge` GET handshake, `X-Hub-Signature-256` HmacSHA256 verification on POST). Phone-number verification mirrors the Linked ceremony via `CloudWhatsAppClientVerificationHandler` (`CloudWhatsAppClient.verifyPhoneNumber`).
- Builders: `WhatsAppClient.builder()` -> `.linkedApi()` (`LinkedWhatsAppClientBuilder` -> `.webClient()` / `.mobileClient()` / `.customClient()`) or `.cloudApi()` (`CloudWhatsAppClientBuilder`, staged like the Linked builder: `.loadConnection(accessToken, phoneNumberId)` -> `Options` (Graph config, proxy, error handler) -> optional `.webhook(verifyToken, port)` sub-stage -> `.build()`; `.loadConnection(CloudWhatsAppStore)` is the pre-built-store branch). Flavour interfaces also expose `LinkedWhatsAppClient.builder()` / `CloudWhatsAppClient.builder()` directly.
- **Naming rule:** production implementations are `Live<Interface>` (e.g., `LiveLinkedWhatsAppClient`, `LiveCallService`); the interface keeps the clean name. Never `Default*`. When the two transports expose the same operation, the Linked name wins and the Cloud method is renamed to match.

### Threading Model
Virtual threads (Project Loom) with direct blocking calls — no `CompletableFuture`. JS `await` / native async maps to a plain blocking call on a virtual thread.
- Every listener invocation runs on its own virtual thread; listeners can block freely without stalling the socket reader.
- `net` package owns connection resilience: `ReconnectSupervisor` (unbounded backoff + jitter on a dedicated thread), `KeepAliveService` (periodic `w:p` ping), `NetworkConnectivityMonitor` (native OS monitoring via FFM/jextract bindings: `Iphlpapi` on Windows, `Netlink` on Linux, `SCReachability` on macOS; bindings regenerated by `regenerate-bindings.sh`).
- Locking conventions: `SignalCryptoLocks` serializes Signal session/sender-key ratchets across encryption AND decryption; `MessageSender` hoists a per-conversation send queue (template method: final `send` -> enqueue -> abstract `doSend`); app-state push/pull rounds are serialized behind a single lock.
- MDBX persistence uses one dedicated platform writer thread with batched transactions (see Persistence).

### Store System
`WhatsAppStore` (sealed) permits `LinkedWhatsAppStore` and `CloudWhatsAppStore`.

`LinkedWhatsAppStore` replaces WA Web's multi-database architecture (~12 IndexedDB databases, ~100 IDB tables, ~45 reactive Collections, UserPrefs) with **seven composed sub-stores**, each a `ConcurrentHashMap`-backed `@ProtobufMessage`:
- `SignalStore` — Signal/Noise keys, sessions, sender keys, identity trust, ADV credential (extends `SignalProtocolStore`).
- `AccountStore` — own identity and profile: JID/LID, device, versions, name, picture, business profile.
- `ContactStore` — contacts, PN<->LID mapping, per-user device lists, block list.
- `ChatStore` — chats, newsletters, status, messages, calls. This is the persistence-variant domain: `ProtobufChatStore` is abstract and each persistence strategy supplies its concrete subclass.
- `SyncStore` — app-state (syncd) per-collection state machines, sync keys, mutation queues, AB-props.
- `SettingsStore` — privacy, preferences, stickers, labels, quick replies.
- `BusinessStore` — runtime-only business/payments/bot state (not persisted).

`ProtobufWhatsAppStore` is the abstract in-memory facade composing the sub-stores as protobuf fields; it is delegator-free (consumers call `store.signal().x()`, not flattened pass-through methods). Key WA Web counterparts: `WAWebSignalStorage`, `WAWebModelStorageInitialize`, `WAWebCollections`, `WAWebUserPrefsBase`.

`CloudWhatsAppStore` is deliberately lightweight: access token, phone-number id, WABA id, app secret, webhook config.

**DI pattern:** services (store, client, etc.) are injected via constructor and held as fields, never reached through global getters.

### Persistence (`store/persistent`)
Obtained via `WhatsAppStoreFactory.persistent()` / `.persistent(Path)` / `.persistent(Path, long mapSize)` or `.temporary()` (RAM-only `TemporaryStore`). Default root `$HOME/.cobalt/proto`, layout `<dir>/<clientType>/<sessionId>/`:
- `store.proto` — protobuf snapshot of the metadata sub-stores.
- `messages.mdbx/` — a libmdbx environment (FFM bindings under `store/persistent/mdbx/bindings`, jextract-generated `Mdbx`) holding three named databases: `chat_messages` (key `chatJid+0x00+msgId`, per-chat range scans), `newsletter_messages` (key `newsletterJid+0x00+serverId` big-endian), `status_messages` (flat by `msgId`).
- Write path: single dedicated platform writer thread (`MdbxWriteQueue`), batched transactions, one fsync per batch. Read path: short transactions with eager heap copies (no mmap references escape). Opened with `MDBX_NOSTICKYTHREADS` (virtual-thread safe) and `MDBX_LIFORECLAIM`.

Other `$HOME/.cobalt/` users: `errors/` (default error-handler dump), `cache/` (Android version metadata), `cache/natives/` (`NativeLibLoader` extraction root).

### Error Model (INTENTIONALLY DIFFERENT FROM WA WEB)
Cobalt's error handling is **deliberately redesigned** to be configurable — do NOT replicate WA Web's inline recovery logic:
- **Sealed exception hierarchy:** all extend `WhatsAppException` (sealed abstract `RuntimeException`, ~23 direct permits). Each has `isFatal()` (session-invalidating: BadMac, Banned, LoggedOut, ADV/LID failures, store corruption; non-fatal: single message, media transfer, single AB prop).
- **Pluggable error handler:** `WhatsAppClientErrorHandler` returns `Result` (`DISCARD`, `DISCONNECT`, `RECONNECT`, `LOG_OUT`, `BAN`). Recovery is user-configurable, NOT hardcoded.
- **Key families:** `WhatsAppSessionException` (sealed: `BadMac`, `Closed`, `Conflict`, `LoggedOut`, `Banned`, `Reconnect`), `WhatsAppMessageException`, `WhatsAppMediaException`, `WhatsAppWebAppStateSyncException`, `WhatsAppAdvValidationException`, `WhatsAppStreamException`, `WhatsAppRegistrationException`, `WhatsAppCallException`, and for the Cloud transport `WhatsAppCloudException` (sealed: `CloudAuthException` for HTTP 401/Graph code 190, `CloudApiException` carrying code/subcode/`fbtrace_id`).
- **Validation rule:** when WA Web has inline error recovery (try/catch with retry/disconnect/ignore), Cobalt throws the appropriate exception subtype instead. Only flag as missing if the exception THROW itself is missing, not the recovery logic.

### Listener Model
`WhatsAppListener` (sealed) permits three branches: `LinkedListener` (~60 single-method per-event interfaces in `listener/linked`, e.g. `LinkedChatsListener`), `CloudListener` (~13 in `listener/cloud`, all with typed payload models, e.g. `CloudTemplateStatusListener` receiving `CloudTemplateStatusUpdate`; the raw-envelope escape hatch is `CloudWebhookReceivedListener`), and five transport-agnostic listeners directly in `listener` that receive the root `WhatsAppClient`: `NewMessageListener`, `MessageStatusListener`, `MessageDeletedListener`, `LoggedInListener`, `DisconnectedListener`. The shared five register through typed `addXxxListener` methods on `WhatsAppClient` itself; flavour events register through `addListener(LinkedListener)`/`addListener(CloudListener)` or their typed conveniences; removal is uniformly `removeListener(WhatsAppListener)`. `LinkedWhatsAppClientListener` / `CloudWhatsAppClientListener` are non-sealed aggregators implementing every event (including the shared five) for subclass-style listeners. Internal always-registered listeners live under `listener/linked/internal`.

### Protobuf System
Custom protobuf library (`com.github.auties00:protobuf-serialization-plugin`), NOT Google protobuf:
- **Annotations:** `@ProtobufMessage(name = "...")`, `@ProtobufProperty(index = N, type = ProtobufType.XXX)`
- **Fields:** Package-private, accessed via `fieldName()` getters (NOT `getFieldName()`). Nullable fields return `Optional<T>`. Lists return unmodifiable `List<T>` defaulting to `List.of()`.
- **Construction:** Constructors are package-private. Use the generated builder: `ClassNameBuilder` (e.g., `MessageKey` → `MessageKeyBuilder`). For nested classes, concatenate parent names: `TemplateButton.CallButton` → `TemplateButtonCallButtonBuilder`. Mutable variants also have setter methods returning `this`.
- **Enums:** `@ProtobufEnum` with `@ProtobufEnumIndex` on the int constructor parameter.
- **Oneofs:** Sealed interfaces (e.g., `TemplateButtonVariant`) with inner static classes implementing each variant.
- **Mixins:** `@ProtobufMixin` classes for type conversion (e.g., `InstantSecondsMixin` converts `Instant` ↔ `Long`).
- **Custom serializers:** `@ProtobufSerializer`/`@ProtobufDeserializer` on types (e.g., `Jid` has custom `of(ProtobufString)` deserializer).

### Nodes/Stanzas
Built via `NodeBuilder` with `.description()`, `.attribute()`, `.content()`.
`Node` has convenience methods to get and stream attributes and content: use the best convenience method to improve code readability. Typed stanza models live in `node/iq`, `node/mex`, `node/smax`, `node/usync`.

### Naming Conventions
Cobalt class names mirror WA Web modules but drop the `WA`/`WAWeb` prefix:

| Cobalt Pattern                            | Maps to WA Web                                                           |
|-------------------------------------------|--------------------------------------------------------------------------|
| `*Sender` (e.g., `PeerMessageSender`)     | Send/dispatch modules                                                    |
| `*Receiver` (e.g., `ChatMessageReceiver`) | Message handling/processing modules                                      |
| `*Handler` (e.g., `ArchiveChatHandler`)   | Sync action handlers                                                     |
| `*Service` (e.g., `DeviceService`)        | Stateful service modules                                                 |
| `*Mex` (e.g., `FetchAboutStatusMex`)      | MEX/GraphQL operations                                                   |
| `*Action` (e.g., `ArchiveChatAction`)     | Sync action protobuf models                                              |
| `*Stanza` (e.g., `ChatFanoutStanza`)      | Stanza/node builders                                                     |
| `Live*` (e.g., `LiveCallService`)         | Production impl of a Cobalt interface (no WA counterpart for the prefix) |
| `Cloud*` (e.g., `CloudMessageEncoder`)    | Meta Cloud API surface (no WA Web counterpart)                           |

### Source Provenance Annotations (`modules/source-meta`)
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

The Cloud API surface has no WA Web/Mobile counterpart: `cloud` package classes carry NO provenance annotations; their contract source is Meta's public Cloud API documentation/OpenAPI spec.

## Tools (`tools/`)

Reverse-engineering and codegen tooling. All `tools/web/*` are TypeScript/Node (build `npm run build`, run `npm start` or `node dist/index.js`); they are NOT part of the Maven build.

| Tool                                       | Purpose                                                                                                                                                                                                                                                                                                              |
|--------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `tools/web/mcp-server`                     | MCP server exposing the WA Web RE surface used during development: module/export search, dependency graphs, WASM analysis (wabt/Ghidra), live sessions (Playwright/CDP), stanza/WAM/network capture, Android emulator control, snapshot management (multi-version catalogs under `~/.cobalt/`). See its `README.md`. |
| `tools/web/ab-props-codegen`               | Extracts AB-prop (feature flag) definitions from a live WA Web bundle and regenerates `modules/model/.../model/props/ABProp.java`.                                                                                                                                                                                   |
| `tools/web/wam-codegen`                    | Extracts WAM event/enum schemas from the WAM runtime and regenerates the per-event Java classes in `modules/lib/.../cobalt/wam/` (consumed by the `wam-core` processor).                                                                                                                                             |
| `tools/web/proto-extractor`                | Extracts protobuf definitions from WA Web JS chunks (`internalSpec`) and WASM binaries into `whatsapp.proto` — the reference for `modules/model` coverage.                                                                                                                                                           |
| `tools/web/graphql-extractor`              | Extracts MEX/GraphQL operation specs from the Relay persisted-query layer into `schemas.json` (operation id, kind, variables, transport: `stanza_mex` / `http_relay` / `http_comet`).                                                                                                                                |
| `tools/web/scripts`                        | Paste-into-console instrumentation for a live WA Web tab: `ab-props.js` (live flag query/override), `stanza-logger.js` (binary XML intercept), `wam-logger.js` (telemetry intercept).                                                                                                                                |
| `tools/mobile/{android,ios}/frida-scripts` | Frida instrumentation for the native apps (mbedtls hooks, iOS registration key extraction). Reference-only, not maintained. Requires rooted/jailbroken device + frida-compile.                                                                                                                                       |

## Documentation Style Per Module

The full javadoc rules below apply to ALL Java main source. Module-specific deltas:

| Module                | `@implNote`           | `meta.*` provenance annotations    | Notes                                                                                                 |
|-----------------------|-----------------------|------------------------------------|-------------------------------------------------------------------------------------------------------|
| `modules/lib`         | Allowed (rules below) | Required where a WA mapping exists | The only module with WA provenance; Cloud API classes are exempt from provenance (no WA counterpart). |
| `modules/model`       | NO                    | NO                                 | Pure data model; spec lives in summary + body paragraphs; cannot `{@link}` lib types.                 |
| `modules/wam-core`    | NO                    | NO                                 | Standard javadoc; processor/generator internals documented like any library code.                     |
| `modules/source-meta` | NO                    | NO                                 | Standard javadoc; the annotations document themselves via their own javadoc.                          |
| `tools/*`             | n/a (TypeScript/JS)   | n/a                                | Follow each tool's local conventions; no Cobalt javadoc rules.                                        |
| All `src/test/java`   | NEVER                 | NEVER                              | Lighter rules in the `### Tests` subsection below.                                                    |

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
