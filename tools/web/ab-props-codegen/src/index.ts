import { parseArgs } from "node:util";
import { fetchWhatsAppWebJs } from "./whatsapp/fetcher.js";
import { parseABProps } from "./parser/ab-props-parser.js";
import { filterReferencedProps } from "./parser/ab-props-filter.js";
import { writeABPropJava } from "./generator/java-writer.js";

const { values: args } = parseArgs({
    options: {
        "output-dir": { type: "string", default: "../../../modules/model/src/main/java" },
        "package":    { type: "string", default: "com.github.auties00.cobalt.props" },
    },
});

const outputDir = args["output-dir"]!;
const pkg = args["package"]!;

async function main(): Promise<void> {
    console.log("=".repeat(50));
    console.log("WhatsApp AB Props Extractor");
    console.log("=".repeat(50));
    console.log(`Output dir : ${outputDir}`);
    console.log(`Package    : ${pkg}`);
    console.log("");

    // 1. Fetch JS from WhatsApp Web
    const jsContent = await fetchWhatsAppWebJs();
    console.log(`\nTotal JS content: ${(jsContent.length / 1_000_000).toFixed(1)} MB`);

    // 2. Parse AB prop definitions
    console.log("\nParsing AB prop definitions...");
    const allProps = parseABProps(jsContent);
    console.log(`Found ${allProps.length} AB props`);

    // 3. Filter to only props that are actually referenced in the source
    // console.log("\nFiltering to referenced props...");
    // const { filtered: props, removedCount } = filterReferencedProps(allProps, jsContent);
    // console.log(`Kept ${props.length} referenced props (removed ${removedCount} unreferenced)`);

    const typeCounts = { bool: 0, int: 0, float: 0, string: 0 };
    for (const prop of allProps) {
        typeCounts[prop.type]++;
    }
    console.log(`  bool: ${typeCounts.bool}, int: ${typeCounts.int}, float: ${typeCounts.float}, string: ${typeCounts.string}`);

    // 4. Generate ABProp.java
    console.log("\nGenerating ABProp.java...");
    const count = await writeABPropJava(allProps, outputDir, pkg);
    console.log(`Wrote ABProp.java with ${count} constants`);

    console.log("\n" + "=".repeat(50));
    console.log(`Done! Generated ${count} AB prop constants (from ${allProps.length} total).`);
    console.log("=".repeat(50));
}

main().catch((err: unknown) => {
    const message = err instanceof Error ? err.stack ?? err.message : String(err);
    console.error(`[FATAL] ${message}`);
    process.exit(1);
});
