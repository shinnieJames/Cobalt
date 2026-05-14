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
ALL members (public, protected, package-private, private) MUST have JDK-style multiline javadoc. No `@since` tags. Use source provenance annotations (`@WhatsAppWebModule`/`@WhatsAppWebExport` for Web, `@WhatsAppMobileClass`/`@WhatsAppMobileMethod` for Mobile) to declare WA source mappings.

### Style Rules
- Third person declarative present tense: "Returns the value" not "Return the value"
- Summary sentence must be complete and standalone
- Methods start with verb phrase: "Returns...", "Compares...", "Performs..."
- Wrap Java keywords/types in `{@code ...}`: `{@code null}`, `{@code true}`, `{@code Optional}`
- `{@link}` for first reference, `{@code}` for subsequent

### Block Tag Order
`@apiNote` → `@implSpec` → `@implNote` → `@param` → `@return` → `@throws` → `@see`

### Required Tags
- `@param` for every parameter
- `@return` for every non-void method
- `@throws` for every checked exception and significant unchecked exceptions
- `@WhatsAppWebExport` / `@WhatsAppMobileMethod` on every method/constructor/field that maps to WA source

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
