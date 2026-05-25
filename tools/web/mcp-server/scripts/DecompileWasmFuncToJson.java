// Ghidra headless post-analysis script: decompiles ONE WASM function (by wasm
// function index) to C pseudocode and writes it as JSON.
// Usage: analyzeHeadless ... -postScript DecompileWasmFuncToJson.java <funcIndex> <outputPath>
//
// Requires the nneonneo ghidra-wasm-plugin (provides wasm.WasmLoader and
// wasm.analysis.WasmAnalysis). The plugin addresses defined functions at
// CODE_BASE + code-entry offset and exposes WasmLoader.getFunctionAddress to map
// a wasm function index to a Ghidra Address. API targets plugin v2.x; adjust if
// a future plugin release changes these signatures.
// @category Decompiler
import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileOptions;
import ghidra.app.decompiler.DecompileResults;
import ghidra.app.decompiler.DecompiledFunction;
import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.Function;

import java.io.FileWriter;
import java.io.PrintWriter;

import wasm.WasmLoader;
import wasm.analysis.WasmAnalysis;

public class DecompileWasmFuncToJson extends GhidraScript {

    @Override
    public void run() throws Exception {
        String[] args = getScriptArgs();
        int funcIndex = Integer.parseInt(args[0]);
        String outputPath = args[1];

        Address addr = WasmLoader.getFunctionAddress(
                currentProgram.getAddressFactory(),
                WasmAnalysis.getState(currentProgram).getModule(),
                funcIndex);
        Function func = currentProgram.getFunctionManager().getFunctionAt(addr);

        String name = "";
        String signature = "";
        String code = "";
        boolean ok = false;

        if (func != null) {
            name = func.getName();
            signature = func.getSignature().getPrototypeString();
            DecompInterface decompiler = new DecompInterface();
            decompiler.setOptions(new DecompileOptions());
            decompiler.openProgram(currentProgram);
            DecompileResults res = decompiler.decompileFunction(func, 120, monitor);
            if (res != null && res.decompileCompleted()) {
                DecompiledFunction df = res.getDecompiledFunction();
                if (df != null) {
                    code = df.getC();
                    ok = true;
                }
            }
            decompiler.dispose();
        }

        PrintWriter w = new PrintWriter(new FileWriter(outputPath));
        w.println("{");
        w.println("  \"funcIndex\": " + funcIndex + ",");
        w.println("  \"name\": \"" + esc(name) + "\",");
        w.println("  \"address\": \"" + (addr == null ? "" : addr.toString()) + "\",");
        w.println("  \"signature\": \"" + esc(signature) + "\",");
        w.println("  \"decompiled\": " + ok + ",");
        w.println("  \"code\": \"" + esc(code) + "\"");
        w.println("}");
        w.close();

        println("Wrote decompiled function " + funcIndex + " to " + outputPath);
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
