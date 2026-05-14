import type { ParsedModule } from "../types/module.js";
import type { GhidraDecompiledFunction, GhidraOutput } from "./ghidra.js";

interface ObjCClass {
  name: string;
  superClass: string | null;
  methods: GhidraDecompiledFunction[];
}

const OBJC_METHOD_RE = /^([+-])\[(\S+)\s+(.+)]$/;

export function parseGhidraOutput(output: GhidraOutput): ParsedModule[] {
  const classMap = new Map<string, ObjCClass>();
  const standaloneFunctions: GhidraDecompiledFunction[] = [];

  for (const func of output.functions) {
    if (func.isThunk || func.isExternal) continue;
    if (!func.decompiled || !func.code) continue;

    const match = OBJC_METHOD_RE.exec(func.name);
    if (match) {
      const className = match[2];
      let cls = classMap.get(className);
      if (!cls) {
        cls = { name: className, superClass: null, methods: [] };
        classMap.set(className, cls);
      }
      cls.methods.push(func);
    } else {
      standaloneFunctions.push(func);
    }
  }

  // Try to extract superclass info from decompiled code
  for (const cls of classMap.values()) {
    cls.superClass = inferSuperClass(cls);
  }

  const modules: ParsedModule[] = [];

  // Each ObjC class becomes a module
  for (const cls of classMap.values()) {
    const exports = cls.methods.map((m) => {
      const match = OBJC_METHOD_RE.exec(m.name);
      return match ? `${match[1]}${match[3]}` : m.name;
    });

    const dependencies = inferDependencies(cls, classMap);
    const body = generateClassSource(cls);

    modules.push({
      name: cls.name,
      dependencies,
      exports,
      body,
    });
  }

  // Group remaining C functions by module/file if possible
  if (standaloneFunctions.length > 0) {
    const grouped = groupStandaloneFunctions(standaloneFunctions);
    for (const [groupName, funcs] of grouped) {
      const exports = funcs.map((f) => f.name);
      const body = funcs
        .map((f) => `// ${f.signature}\n// Address: ${f.address}\n${f.code}`)
        .join("\n\n");

      modules.push({
        name: groupName,
        dependencies: [],
        exports,
        body,
      });
    }
  }

  return modules;
}

function inferSuperClass(cls: ObjCClass): string | null {
  // Look for patterns like "objc_msgSendSuper" calls or class_getSuperclass
  for (const method of cls.methods) {
    // Ghidra decompiler output often includes super class references
    const superMatch = method.code.match(
      /\b(\w+)_super\b|objc_msgSendSuper.*?&OBJC_CLASS_\$_(\w+)/
    );
    if (superMatch) {
      return superMatch[1] ?? superMatch[2] ?? null;
    }
  }
  return null;
}

function inferDependencies(
  cls: ObjCClass,
  allClasses: Map<string, ObjCClass>
): string[] {
  const deps = new Set<string>();

  for (const method of cls.methods) {
    // Find references to other known classes in the decompiled code
    // Pattern: OBJC_CLASS_$_ClassName or [ClassName method]
    const classRefPattern = /OBJC_CLASS_\$_(\w+)/g;
    let match: RegExpExecArray | null;
    while ((match = classRefPattern.exec(method.code))) {
      const refClass = match[1];
      if (refClass !== cls.name && allClasses.has(refClass)) {
        deps.add(refClass);
      }
    }

    // Pattern: [ClassName alloc] or [ClassName new] etc.
    const msgSendPattern = /\[(\w+)\s+\w+/g;
    while ((match = msgSendPattern.exec(method.code))) {
      const refClass = match[1];
      if (
        refClass !== cls.name &&
        refClass !== "self" &&
        refClass !== "super" &&
        allClasses.has(refClass)
      ) {
        deps.add(refClass);
      }
    }

    // Pattern: type references in signatures (ClassName *)
    const typeRefPattern = /(\w+)\s*\*/g;
    while ((match = typeRefPattern.exec(method.signature))) {
      const refClass = match[1];
      if (
        refClass !== cls.name &&
        refClass !== "void" &&
        refClass !== "id" &&
        refClass !== "SEL" &&
        refClass !== "char" &&
        refClass !== "int" &&
        refClass !== "unsigned" &&
        allClasses.has(refClass)
      ) {
        deps.add(refClass);
      }
    }
  }

  return [...deps].sort();
}

function generateClassSource(cls: ObjCClass): string {
  const lines: string[] = [];

  // Header
  const superPart = cls.superClass ? ` : ${cls.superClass}` : "";
  lines.push(`@interface ${cls.name}${superPart}`);
  lines.push("");

  // Method declarations
  const instanceMethods = cls.methods.filter((m) => m.name.startsWith("-["));
  const classMethods = cls.methods.filter((m) => m.name.startsWith("+["));

  if (classMethods.length > 0) {
    lines.push("// Class methods");
    for (const m of classMethods) {
      const sel = OBJC_METHOD_RE.exec(m.name);
      lines.push(`// + (${extractReturnType(m.signature)})${sel ? sel[3] : m.name};`);
    }
    lines.push("");
  }

  if (instanceMethods.length > 0) {
    lines.push("// Instance methods");
    for (const m of instanceMethods) {
      const sel = OBJC_METHOD_RE.exec(m.name);
      lines.push(`// - (${extractReturnType(m.signature)})${sel ? sel[3] : m.name};`);
    }
    lines.push("");
  }

  lines.push("@end");
  lines.push("");
  lines.push("// === Decompiled Implementation ===");
  lines.push("");

  // Full decompiled code for each method
  for (const m of [...classMethods, ...instanceMethods]) {
    lines.push(`// ${m.name}`);
    lines.push(`// Address: ${m.address} | Size: ${m.size} bytes`);
    lines.push(m.code);
    lines.push("");
  }

  return lines.join("\n");
}

function extractReturnType(signature: string): string {
  // Ghidra signatures look like: "void -[Class method:](id self, SEL sel, ...)"
  const match = signature.match(/^(\S+)\s/);
  if (match) {
    const type = match[1];
    if (type === "undefined" || type === "undefined8") return "id";
    if (type === "undefined4") return "int";
    if (type === "undefined1") return "BOOL";
    return type;
  }
  return "id";
}

function groupStandaloneFunctions(
  functions: GhidraDecompiledFunction[]
): Map<string, GhidraDecompiledFunction[]> {
  const groups = new Map<string, GhidraDecompiledFunction[]>();

  for (const func of functions) {
    // Group by prefix: functions starting with the same prefix likely belong together
    let groupName = "_CFunctions";

    // Try to extract a meaningful group from the function name
    const prefixMatch = func.name.match(/^_?([A-Z][a-zA-Z]+?)_/);
    if (prefixMatch) {
      groupName = `_C_${prefixMatch[1]}`;
    }

    let group = groups.get(groupName);
    if (!group) {
      group = [];
      groups.set(groupName, group);
    }
    group.push(func);
  }

  return groups;
}
