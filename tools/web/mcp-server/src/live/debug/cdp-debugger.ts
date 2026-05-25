import type { BrowserContext, CDPSession, Page } from "playwright";
import { readFile } from "node:fs/promises";
import type {
  DebugCommand,
  DebuggerScriptParsedEvent,
  DebuggerSetBreakpointByUrlResponse,
  DebuggerSetBreakpointResponse,
  DebugScriptInfo,
  EvaluateResult,
  PausedState,
  RuntimeEvaluateResponse,
  SetBreakpointResult,
  WasmMemoryReadResult,
} from "../../types/live/debug.js";
import { createLogger } from "../../utils/logger.js";

const log = createLogger("live:debugger");

function isScriptParsedEvent(value: unknown): value is DebuggerScriptParsedEvent {
  if (value == null || typeof value !== "object") return false;
  const payload = value as Record<string, unknown>;
  return (
    typeof payload.scriptId === "string" &&
    typeof payload.startLine === "number" &&
    typeof payload.startColumn === "number" &&
    typeof payload.endLine === "number" &&
    typeof payload.endColumn === "number"
  );
}

export class LiveCdpDebugger {
  private cdp: CDPSession | null = null;
  private scriptParsedListenerAttached = false;
  private readonly debugScripts = new Map<string, DebugScriptInfo>();
  private lastPausedState: PausedState | null = null;
  private fetchEnabled = false;
  private readonly replacements = new Map<string, string>();
  private readonly requireContext: () => BrowserContext;
  private readonly requirePage: () => Page;

  constructor(requireContext: () => BrowserContext, requirePage: () => Page) {
    this.requireContext = requireContext;
    this.requirePage = requirePage;
  }

  async detach(): Promise<void> {
    if (!this.cdp) return;
    log.info("detaching CDP debugger session");
    await this.cdp.detach().catch(() => undefined);
    this.cdp = null;
    this.scriptParsedListenerAttached = false;
    this.fetchEnabled = false;
    this.debugScripts.clear();
    this.replacements.clear();
  }

  /**
   * Serves the bytes of {@code filePath} in place of any request whose URL
   * contains {@code urlSubstring}, via CDP Fetch interception. Used to install a
   * patched WASM binary without touching the JS/wasm import boundary. Restricted
   * to wasm-like requests to minimize interception overhead; subsequent loads of
   * the module receive the replacement.
   */
  async serveReplacement(urlSubstring: string, filePath: string): Promise<void> {
    const cdp = await this.ensureCdp();
    this.replacements.set(urlSubstring, filePath);
    if (this.fetchEnabled) return;

    cdp.on("Fetch.requestPaused", async (event: unknown) => {
      const e = event as { requestId: string; request: { url: string } };
      try {
        const match = [...this.replacements.entries()].find(([sub]) => e.request.url.includes(sub));
        if (match) {
          const body = await readFile(match[1]);
          await cdp.send("Fetch.fulfillRequest", {
            requestId: e.requestId,
            responseCode: 200,
            responseHeaders: [{ name: "Content-Type", value: "application/wasm" }],
            body: body.toString("base64"),
          });
          return;
        }
      } catch {
        /* fall through to continue the request unmodified */
      }
      await cdp.send("Fetch.continueRequest", { requestId: e.requestId }).catch(() => undefined);
    });
    await cdp.send("Fetch.enable", { patterns: [{ urlPattern: "*wasm*", requestStage: "Request" }] });
    this.fetchEnabled = true;
  }

  /** Removes all serve replacements and disables Fetch interception. */
  async clearReplacements(): Promise<void> {
    this.replacements.clear();
    if (this.fetchEnabled && this.cdp) {
      await this.cdp.send("Fetch.disable").catch(() => undefined);
      this.fetchEnabled = false;
    }
  }


  async listScripts(filter?: string, limit: number = 200): Promise<DebugScriptInfo[]> {
    await this.ensureCdp();
    const normalizedFilter = filter ? filter.toLowerCase() : null;
    return [...this.debugScripts.values()]
      .filter((script) =>
        normalizedFilter ? script.url.toLowerCase().includes(normalizedFilter) : true
      )
      .sort((a, b) => a.url.localeCompare(b.url))
      .slice(0, Math.max(1, limit));
  }

  async evaluate(expression: string, awaitPromise: boolean = true): Promise<EvaluateResult> {
    const cdp = await this.ensureCdp();
    const responseRaw = await cdp.send("Runtime.evaluate", {
      expression,
      awaitPromise,
      returnByValue: true,
      generatePreview: true,
      replMode: true,
    });
    const response = responseRaw as RuntimeEvaluateResponse;
    const result = response.result;
    return {
      resultType: result?.type ?? "undefined",
      value: result?.value,
      description: result?.description ?? null,
      unserializableValue: result?.unserializableValue ?? null,
      exception:
        response.exceptionDetails?.text ??
        response.exceptionDetails?.exception?.description ??
        null,
    };
  }

  async setBreakpointByUrl(
    url: string,
    lineNumberOneBased: number,
    columnNumberOneBased: number = 1,
    condition?: string
  ): Promise<SetBreakpointResult> {
    const cdp = await this.ensureCdp();
    const responseRaw = await cdp.send("Debugger.setBreakpointByUrl", {
      url,
      lineNumber: Math.max(0, lineNumberOneBased - 1),
      columnNumber: Math.max(0, columnNumberOneBased - 1),
      condition,
    });
    const response = responseRaw as DebuggerSetBreakpointByUrlResponse;
    return {
      breakpointId: response.breakpointId,
      locations: response.locations ?? [],
    };
  }

  async setBreakpointByScriptId(
    scriptId: string,
    lineNumberOneBased: number,
    columnNumberOneBased: number = 1,
    condition?: string
  ): Promise<SetBreakpointResult> {
    const cdp = await this.ensureCdp();
    const responseRaw = await cdp.send("Debugger.setBreakpoint", {
      location: {
        scriptId,
        lineNumber: Math.max(0, lineNumberOneBased - 1),
        columnNumber: Math.max(0, columnNumberOneBased - 1),
      },
      condition,
    });
    const response = responseRaw as DebuggerSetBreakpointResponse;
    const location = response.actualLocation ? [response.actualLocation] : [];
    return {
      breakpointId: response.breakpointId,
      locations: location,
    };
  }

  /**
   * Sets a breakpoint inside a wasm script at an absolute module byte offset.
   * For wasm, V8 locates instructions by {@code lineNumber: 0} plus a
   * {@code columnNumber} equal to the byte offset, so this bypasses the
   * one-based line/column convention of {@link setBreakpointByScriptId}. The
   * caller computes the offset as {@code codeOffset + bodyOffset + instrOffset}.
   */
  async setWasmBreakpoint(
    scriptId: string,
    byteOffset: number,
    condition?: string
  ): Promise<SetBreakpointResult> {
    const cdp = await this.ensureCdp();
    const responseRaw = await cdp.send("Debugger.setBreakpoint", {
      location: { scriptId, lineNumber: 0, columnNumber: Math.max(0, byteOffset) },
      condition,
    });
    const response = responseRaw as DebuggerSetBreakpointResponse;
    return {
      breakpointId: response.breakpointId,
      locations: response.actualLocation ? [response.actualLocation] : [],
    };
  }

  /**
   * Reads a slice of a paused wasm frame's linear memory and returns it as
   * base64. Evaluates in the frame context against the {@code module} scope's
   * {@code memories[0]}, chunking the copy to avoid argument-count limits on
   * large reads.
   */
  async readWasmMemory(callFrameId: string, addr: number, len: number): Promise<WasmMemoryReadResult> {
    const cdp = await this.ensureCdp();
    const expression = `(() => {
      const mem = (typeof memories !== "undefined" && memories[0]) || (typeof memory !== "undefined" && memory);
      if (!mem) return { __err: "no memory in frame scope" };
      const view = new Uint8Array(mem.buffer, ${Math.max(0, addr)}, ${Math.max(0, len)});
      let bin = "";
      for (let i = 0; i < view.length; i += 0x8000) {
        bin += String.fromCharCode.apply(null, view.subarray(i, i + 0x8000));
      }
      return { __b64: btoa(bin) };
    })()`;
    try {
      const responseRaw = await cdp.send("Debugger.evaluateOnCallFrame", {
        callFrameId,
        expression,
        returnByValue: true,
      });
      const response = responseRaw as RuntimeEvaluateResponse;
      const value = response.result?.value as { __b64?: string; __err?: string } | undefined;
      if (response.exceptionDetails) {
        return { addr, len, base64: null, error: response.exceptionDetails.text ?? "evaluation failed" };
      }
      if (value?.__err) return { addr, len, base64: null, error: value.__err };
      return { addr, len, base64: value?.__b64 ?? null };
    } catch (err) {
      return { addr, len, base64: null, error: err instanceof Error ? err.message : String(err) };
    }
  }

  /** Evaluates an expression in a paused frame's context, returning by value. */
  async evaluateOnCallFrame(callFrameId: string, expression: string): Promise<EvaluateResult> {
    const cdp = await this.ensureCdp();
    const responseRaw = await cdp.send("Debugger.evaluateOnCallFrame", {
      callFrameId,
      expression,
      returnByValue: true,
      generatePreview: true,
    });
    const response = responseRaw as RuntimeEvaluateResponse;
    const result = response.result;
    return {
      resultType: result?.type ?? "undefined",
      value: result?.value,
      description: result?.description ?? null,
      unserializableValue: result?.unserializableValue ?? null,
      exception:
        response.exceptionDetails?.text ??
        response.exceptionDetails?.exception?.description ??
        null,
    };
  }

  async removeBreakpoint(breakpointId: string): Promise<void> {
    const cdp = await this.ensureCdp();
    await cdp.send("Debugger.removeBreakpoint", { breakpointId });
  }

  async command(command: DebugCommand): Promise<void> {
    const cdp = await this.ensureCdp();
    if (command === "pause") {
      await cdp.send("Debugger.pause");
      return;
    }
    if (command === "resume") {
      this.lastPausedState = null;
      await cdp.send("Debugger.resume");
      return;
    }
    if (command === "step_over") {
      await cdp.send("Debugger.stepOver");
      return;
    }
    if (command === "step_into") {
      await cdp.send("Debugger.stepInto");
      return;
    }
    await cdp.send("Debugger.stepOut");
  }

  async getPausedState(): Promise<PausedState | null> {
    if (!this.lastPausedState) return null;

    const cdp = await this.ensureCdp();
    const enriched = { ...this.lastPausedState };

    for (const frame of enriched.callFrames) {
      if (!frame.scopeChain) continue;
      // Wasm frames expose locals, globals, and the operand stack as scopes
      // (none of type "global"), so they are enumerated here; the module scope
      // can be large, so allow more properties for wasm than for JS.
      const cap = frame.scriptLanguage === "WebAssembly" ? 200 : 20;
      const scopeVars: Array<{ name: string; value: string; type: string }> = [];
      for (const scope of frame.scopeChain) {
        if (scope.type === "global") continue;
        if (!scope.object?.objectId) continue;
        try {
          const propsResult = await cdp.send("Runtime.getProperties", {
            objectId: scope.object.objectId,
            ownProperties: true,
            generatePreview: true,
          });
          const props = propsResult as { result: Array<{ name: string; value?: { type: string; value?: unknown; description?: string } }> };
          for (const prop of (props.result ?? []).slice(0, cap)) {
            scopeVars.push({
              name: prop.name,
              value: prop.value?.description ?? String(prop.value?.value ?? "undefined"),
              type: prop.value?.type ?? "undefined",
            });
          }
        } catch { /* scope may be unavailable */ }
      }
      frame.variables = scopeVars;
    }

    return enriched;
  }

  private async ensureCdp(): Promise<CDPSession> {
    if (this.cdp) return this.cdp;
    const context = this.requireContext();
    const page = this.requirePage();
    const cdp = await context.newCDPSession(page);
    if (!this.scriptParsedListenerAttached) {
      cdp.on("Debugger.scriptParsed", (event: unknown) => {
        if (!isScriptParsedEvent(event)) return;
        this.debugScripts.set(event.scriptId, {
          scriptId: event.scriptId,
          url: event.url ?? "",
          startLine: event.startLine,
          startColumn: event.startColumn,
          endLine: event.endLine,
          endColumn: event.endColumn,
          hash: event.hash ?? null,
          executionContextId: event.executionContextId ?? null,
          length: event.length ?? null,
          scriptLanguage: event.scriptLanguage ?? null,
          codeOffset: event.codeOffset ?? null,
        });
      });
      cdp.on("Debugger.paused", (event: unknown) => {
        const payload = event as Record<string, unknown>;
        const callFrames = payload.callFrames as Array<Record<string, unknown>> | undefined;
        const reason = (payload.reason as string) ?? "other";

        this.lastPausedState = {
          reason,
          callFrames: (callFrames ?? []).slice(0, 20).map((frame) => {
            const location = frame.location as Record<string, unknown> | undefined;
            const scopeChain = frame.scopeChain as Array<Record<string, unknown>> | undefined;
            const scriptId = (location?.scriptId as string) ?? "";
            const scriptLanguage = this.debugScripts.get(scriptId)?.scriptLanguage ?? undefined;
            return {
              callFrameId: (frame.callFrameId as string) ?? "",
              functionName: (frame.functionName as string) ?? "",
              scriptId,
              url: (frame.url as string) ?? "",
              lineNumber: ((location?.lineNumber as number) ?? 0) + 1,
              columnNumber: ((location?.columnNumber as number) ?? 0) + 1,
              scriptLanguage: scriptLanguage ?? undefined,
              scopeChain: (scopeChain ?? []).map((scope) => ({
                type: (scope.type as string) ?? "unknown",
                object: scope.object as { objectId?: string } | undefined,
              })),
              variables: [],
            };
          }),
          ts: new Date().toISOString(),
        };
      });
      cdp.on("Debugger.resumed", () => {
        this.lastPausedState = null;
      });
      this.scriptParsedListenerAttached = true;
    }
    await cdp.send("Runtime.enable");
    await cdp.send("Debugger.enable");
    this.cdp = cdp;
    return cdp;
  }
}
