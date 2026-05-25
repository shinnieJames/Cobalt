export interface DebugScriptInfo {
  scriptId: string;
  url: string;
  startLine: number;
  startColumn: number;
  endLine: number;
  endColumn: number;
  hash: string | null;
  executionContextId: number | null;
  length: number | null;
  /** "WebAssembly" for wasm scripts, "JavaScript"/undefined for JS. */
  scriptLanguage: string | null;
  /** For wasm scripts, the module-relative byte offset of the code section. */
  codeOffset: number | null;
}

export interface SetBreakpointResult {
  breakpointId: string;
  locations: Array<{
    scriptId: string;
    lineNumber: number;
    columnNumber: number;
  }>;
}

export interface EvaluateResult {
  resultType: string;
  value: unknown;
  description: string | null;
  unserializableValue: string | null;
  exception: string | null;
}

export interface RuntimeRemoteObject {
  type?: string;
  value?: unknown;
  description?: string;
  unserializableValue?: string;
}

export interface RuntimeExceptionDetails {
  text?: string;
  exception?: RuntimeRemoteObject;
}

export interface RuntimeEvaluateResponse {
  result?: RuntimeRemoteObject;
  exceptionDetails?: RuntimeExceptionDetails;
}

export interface DebuggerScriptParsedEvent {
  scriptId: string;
  url: string;
  startLine: number;
  startColumn: number;
  endLine: number;
  endColumn: number;
  hash?: string;
  executionContextId?: number;
  length?: number;
  scriptLanguage?: string;
  codeOffset?: number;
}

export interface WasmMemoryReadResult {
  addr: number;
  len: number;
  /** Base64 of the linear-memory slice, or {@code null} if the read failed. */
  base64: string | null;
  error?: string;
}

export interface DebuggerSetBreakpointResponse {
  breakpointId: string;
  actualLocation?: {
    scriptId: string;
    lineNumber: number;
    columnNumber: number;
  };
}

export interface DebuggerSetBreakpointByUrlResponse {
  breakpointId: string;
  locations: Array<{
    scriptId: string;
    lineNumber: number;
    columnNumber: number;
  }>;
}

export type DebugCommand = "pause" | "resume" | "step_over" | "step_into" | "step_out";

export interface PausedCallFrame {
  /** CDP call-frame id, required for evaluateOnCallFrame / wasm memory reads. */
  callFrameId: string;
  functionName: string;
  scriptId: string;
  url: string;
  lineNumber: number;
  columnNumber: number;
  /** "WebAssembly" when this frame is a wasm frame, else undefined. */
  scriptLanguage?: string;
  scopeChain: Array<{
    type: string;
    object?: { objectId?: string };
  }>;
  variables: Array<{
    name: string;
    value: string;
    type: string;
  }>;
}

export interface PausedState {
  reason: string;
  callFrames: PausedCallFrame[];
  ts: string;
}
