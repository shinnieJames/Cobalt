import type { BrowserContext, CDPSession, Page } from "playwright";
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
    this.debugScripts.clear();
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
          for (const prop of (props.result ?? []).slice(0, 20)) {
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
            return {
              functionName: (frame.functionName as string) ?? "",
              scriptId: (location?.scriptId as string) ?? "",
              url: (frame.url as string) ?? "",
              lineNumber: ((location?.lineNumber as number) ?? 0) + 1,
              columnNumber: ((location?.columnNumber as number) ?? 0) + 1,
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
