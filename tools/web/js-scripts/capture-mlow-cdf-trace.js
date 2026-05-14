// capture-mlow-cdf-trace.js
//
// Browser-page hook that records every range-decoder
// (CDF address, decoded symbol) pair as the live MLow
// decoder processes captured SPEECH frames. The output
// trace lets us compare the Java MLowRangeDecoder's
// output symbol-by-symbol against the WASM ground truth.
//
// **DESIGN**
//
// 1) The live Voip WASM exports __indirect_function_table.
//    Slot 5891 = AudioDecoderMLowImpl::DecodeInternal
//    (i32 x6 -> i32) on current snapshots — the same slot
//    install-mlow-allworkers-prehook.js already hooks for
//    pre/post wrapping. (For reference, in the cached
//    offline snapshot the corresponding absolute function
//    index is fn#1919 at slot 883 — different identifier
//    space, same C++ method.)
//
// 2) DecodeInternal calls (transitively) a range-decoder
//    decode_icdf primitive that takes the ICDF table base
//    address as one arg. We instrument the WASM by setting
//    a CDP breakpoint at the entry of every fn that loads
//    one of the 13 known CDF base addresses (0x5c, 0xb9,
//    0x121, 0x14b, 0x15a, 0x1ce, 0x22a, 0x43c, 0x44a,
//    0xba0, 0x1cdd, 0x2144, 0x221f).
//
// 3) When the breakpoint fires we read the WASM stack to
//    capture (cdf_addr_being_loaded, byte_pos_in_payload)
//    and after the function returns we read the val/rng
//    state to derive the decoded symbol. The trace is
//    flushed via globalThis.__mlowCdfTraceFlush() and
//    written to captures/cdf-trace-<n>.json by
//    dump-from-page.js for the Java side to consume.
//
// **USAGE**
//
//   1. Load this script via CDP Page.addScriptToEvaluate-
//      OnNewDocument (per the prehook pattern).
//   2. Start a VoIP call so the live decoder runs.
//   3. After call ends, dump the trace ring to disk via
//      dump-from-page.js.
//   4. Java consumes the dumped JSON via
//      MLowCdfTraceCorpus.loadResource("captures/cdf-trace-*.json").
//
// **CAVEATS**
//
// - CDP breakpoints in WASM run at MAIN-THREAD pace, which
//   is too slow for real-time decode. Use sparingly: enable
//   only for ~1 second of captured audio (~50 frames),
//   disable, dump.
// - The fn-index → CDF-base-load mapping must be re-derived
//   per WASM revision. The fns are listed in REF_FNS_*
//   arrays of MLowArithmeticTables.java.

(() => {
    if (globalThis.__mlowCdfTraceInstalled) return;
    globalThis.__mlowCdfTraceInstalled = true;

    // Known CDF linear addresses (matches MLowArithmeticTables).
    const CDF_BASES = [
        0x5c,   // CDF_1
        0xb9,   // CDF_2
        0x121,  // CDF_3
        0x14b,  // CDF_4
        0x15a,  // CDF_5
        0x1ce,  // CDF_6
        0x22a,  // CDF_7
        0x43c,  // CDF_8
        0x44a,  // CDF_9
        0xba0,  // CDF_10
        0x1cdd, // CDF_11
        0x2144, // CDF_12
        0x221f, // CDF_13
    ];

    // Per-call entry: {ts, cdfBase, sym, valBefore, valAfter, rngBefore, rngAfter, bytePos}
    const trace = [];
    const traceCap = 100000;

    function pushEntry(e) {
        if (trace.length < traceCap) trace.push(e);
    }

    // The actual instrumentation requires CDP breakpoints
    // set on the WASM functions that load each CDF_BASES
    // value. The set of fn indices to break on is derived
    // from REF_FNS_n arrays. Java-side, the orchestrator
    // calls Page.setBreakpointsActive=true and emits
    // Debugger.setBreakpoint with location matching each
    // function's start.
    //
    // When a break fires, the Java orchestrator reads the
    // WASM heap (val, rng, rem, pos) before stepping, then
    // resumes; on the matching return frame it reads the
    // post-decode state. The (cdf_base, pre-state, post-
    // state) triple is enough to algebraically recover the
    // decoded symbol.
    //
    // This file in isolation just provides:
    //   - The list of CDF base addresses to break on
    //   - A globalThis.__mlowCdfTraceFlush() function the
    //     orchestrator can call to dump the trace as JSON

    globalThis.__mlowCdfTrace = trace;
    globalThis.__mlowCdfBases = CDF_BASES;

    /**
     * Returns a serializable snapshot of the trace ring.
     * Java orchestrator polls this via CDP Runtime.evaluate.
     */
    globalThis.__mlowCdfTraceFlush = () => {
        const out = trace.slice(0);
        trace.length = 0;
        return JSON.stringify({
            capturedAt: Date.now(),
            n: out.length,
            cdfBases: CDF_BASES,
            entries: out,
        });
    };

    /**
     * Stages the trace JSON in `globalThis.__mlowDumpJson` so
     * the standard `dump-from-page.js` CDP dumper can pull it
     * down in chunks. Same pattern other capture scripts use.
     *
     * After calling this, run:
     *   node dump-from-page.js <cdpPort> __mlowDumpJson \
     *       captures/cdf-trace-<n>.json
     */
    globalThis.__mlowCdfTraceStage = () => {
        globalThis.__mlowDumpJson = globalThis.__mlowCdfTraceFlush();
        return globalThis.__mlowDumpJson.length;
    };

    /**
     * Direct API for synthesised entries (for testing the
     * pipeline before WASM breakpoints are wired).
     */
    globalThis.__mlowCdfTracePush = (cdfBase, sym, bytePos, valBefore, valAfter, rngBefore, rngAfter) => {
        pushEntry({
            ts: performance.now(),
            cdfBase,
            sym,
            bytePos,
            valBefore,
            valAfter,
            rngBefore,
            rngAfter,
        });
    };

    console.log('[mlow-cdf-trace] Installed in worker. ' +
            'CDF_BASES:', CDF_BASES.map(b => '0x' + b.toString(16)).join(', '));
})();
