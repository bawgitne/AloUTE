import process from 'node:process';
import { chromium } from 'playwright';
import {
  BASE, CHANNEL, HEADLESS, OUT, createRun, writeResults, saveJson, accountsConfigured
} from './lib/harness.mjs';

const ALL_SUITES = ['auth-guest', 'social', 'community', 'messaging-admin', 'cross-a11y', 'resilience'];

const args = process.argv.slice(2);
const suiteArgIndex = args.indexOf('--suite');
const selected = suiteArgIndex >= 0
  ? args[suiteArgIndex + 1].split(',').map(s => s.trim()).filter(Boolean)
  : ALL_SUITES;

for (const name of selected) {
  if (!ALL_SUITES.includes(name)) {
    console.error(`Unknown suite "${name}". Available: ${ALL_SUITES.join(', ')}`);
    process.exit(2);
  }
}

const results = createRun(selected);
results.accountsConfigured = accountsConfigured();
saveJson('run-environment.json', {
  baseUrl: BASE,
  channel: CHANNEL,
  headless: HEADLESS,
  node: process.version,
  out: OUT,
  suites: selected,
  accountsConfigured: results.accountsConfigured
});

let browser;
try {
  browser = await chromium.launch({ channel: CHANNEL, headless: HEADLESS });
} catch (error) {
  console.error(`Cannot launch browser (${CHANNEL}): ${error.message}`);
  process.exit(3);
}

for (const name of selected) {
  console.log(`\n=== Suite: ${name} ===`);
  try {
    const mod = await import(`./suites/${name}.mjs`);
    await mod.run({ browser, results });
  } catch (error) {
    const { record, STATUS } = await import('./lib/harness.mjs');
    record(results, name, `${name}-suite`, `Suite ${name} crashed`, STATUS.FAIL, {
      error: error instanceof Error ? error.message : String(error),
      stack: error instanceof Error ? error.stack?.split('\n').slice(0, 6).join('\n') : null
    });
  }
}

await browser.close();
const summary = writeResults(results);
console.log(`\nResults: ${summary.file}`);
console.log(`Counts: ${JSON.stringify(summary.counts)} — overall ${summary.overall}`);
if (summary.counts.FAIL) process.exitCode = 1;
