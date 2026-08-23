import { readFile } from "node:fs/promises";
import { acceptanceBounds, mergeAggregates } from "./calibration-core.mjs";

const paths = process.argv.slice(2);
if (paths.length === 0) {
  console.error("usage: node merge-aggregates.mjs <aggregate.json> [...]");
  process.exitCode = 2;
} else {
  const inputs = await Promise.all(paths.map(async (path) => JSON.parse(await readFile(path, "utf8"))));
  const merged = mergeAggregates(inputs);
  const candidates = [6_250, 12_500, 18_750].map((tolerance) => ({
    tolerance,
    ...acceptanceBounds(merged.errorHistogram, tolerance),
  }));
  process.stdout.write(JSON.stringify({
    schemaVersion: 1,
    attempts: merged.attempts,
    publicationEligible: merged.attempts >= 20,
    minimumPublicCellSize: 20,
    toleranceAcceptanceBounds: candidates,
    aggregate: merged,
  }, null, 2) + "\n");
}
