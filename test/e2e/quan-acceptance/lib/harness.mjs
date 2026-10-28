import fs from 'node:fs';
import path from 'node:path';

export const BASE = process.env.BASE_URL || 'http://127.0.0.1:8082';
export const CHANNEL = process.env.BROWSER_CHANNEL || 'chrome';
export const HEADLESS = process.env.HEADED !== '1';

const stamp = new Date().toISOString().replace(/[:.]/g, '-');
export const OUT = process.env.OUT_DIR
  ? path.resolve(process.env.OUT_DIR)
  : path.resolve('test-results/quan-e2e-acceptance', stamp);

export const STATUS = Object.freeze({
  PASS: 'PASS',
  FAIL: 'FAIL',
  BLOCKED: 'BLOCKED',
  NOT_RUN: 'NOT RUN'
});

/* Local dev seed accounts only. Values come from the environment; no credential is stored here.
   See docs/DEMO_CHECKLIST_VI.md for the canonical local-only seed table. */
const ACCOUNTS = {
  admin: { email: process.env.ALOUTE_ADMIN_EMAIL, password: process.env.ALOUTE_ADMIN_PASSWORD },
  user: { email: process.env.ALOUTE_USER_EMAIL, password: process.env.ALOUTE_USER_PASSWORD },
  demoan: { email: process.env.ALOUTE_DEMOAN_EMAIL, password: process.env.ALOUTE_DEMOAN_PASSWORD },
  demobinh: { email: process.env.ALOUTE_DEMOBINH_EMAIL, password: process.env.ALOUTE_DEMOBINH_PASSWORD }
};

export function accountsConfigured() {
  return Object.values(ACCOUNTS).every(a => a.email && a.password);
}

export function account(name) {
  const a = ACCOUNTS[name];
  if (!a || !a.email || !a.password) {
    throw new Error(`Missing local seed credentials for "${name}" (set ALOUTE_${name.toUpperCase()}_EMAIL/PASSWORD)`);
  }
  return a;
}

export function createRun(suiteNames) {
  fs.mkdirSync(path.join(OUT, 'screenshots'), { recursive: true });
  return {
    runId: path.basename(OUT),
    baseUrl: BASE,
    browserChannel: CHANNEL,
    startedAt: new Date().toISOString(),
    suites: suiteNames,
    environment: {
      node: process.version,
      platform: process.platform,
      headless: HEADLESS,
      baseUrl: BASE
    },
    entries: []
  };
}

export function record(results, suite, id, name, status, detail = null, evidence = null) {
  const entry = { id, suite, name, status, detail, evidence, at: new Date().toISOString() };
  results.entries.push(entry);
  const tag = status.padEnd(7);
  console.log(`${tag} ${id} ${name}${status === STATUS.FAIL && detail?.error ? ` :: ${detail.error}` : ''}`);
  return entry;
}

export async function test(results, suite, id, name, fn, evidence = null) {
  try {
    const detail = await fn();
    record(results, suite, id, name, STATUS.PASS, detail, evidence);
    return true;
  } catch (error) {
    record(results, suite, id, name, STATUS.FAIL, {
      error: error instanceof Error ? error.message : String(error),
      stack: error instanceof Error ? error.stack?.split('\n').slice(0, 4).join('\n') : null
    }, evidence);
    return false;
  }
}

export function blocked(results, suite, id, name, reason, evidence = null) {
  return record(results, suite, id, name, STATUS.BLOCKED, { reason }, evidence);
}

export function notRun(results, suite, id, name, reason, evidence = null) {
  return record(results, suite, id, name, STATUS.NOT_RUN, { reason }, evidence);
}

export function assert(condition, message) {
  if (!condition) throw new Error(message);
}

export function assertEqual(actual, expected, message) {
  if (actual !== expected) throw new Error(`${message}: expected ${JSON.stringify(expected)}, got ${JSON.stringify(actual)}`);
}

export async function login(browser, who, options = {}) {
  const { email, password } = account(who);
  const context = await browser.newContext({
    baseURL: BASE,
    viewport: options.viewport || { width: 1440, height: 900 },
    locale: 'vi-VN',
    timezoneId: 'Asia/Ho_Chi_Minh',
    ignoreHTTPSErrors: true
  });
  const res = await context.request.post(`${BASE}/api/auth/login`, {
    data: { email, password },
    headers: { Accept: 'application/json', 'Content-Type': 'application/json' }
  });
  if (res.status() !== 200) {
    const body = await res.text().catch(() => '');
    await context.close();
    throw new Error(`login ${who} failed: HTTP ${res.status()} ${body.slice(0, 160)}`);
  }
  const me = await context.request.get(`${BASE}/api/auth/me`, { headers: { Accept: 'application/json' } });
  const meBody = await me.json().catch(() => null);
  const page = await context.newPage();
  return { who, context, page, me: meBody?.data ?? null, email };
}

export async function guest(browser, options = {}) {
  const context = await browser.newContext({
    baseURL: BASE,
    viewport: options.viewport || { width: 1440, height: 900 },
    locale: 'vi-VN',
    timezoneId: 'Asia/Ho_Chi_Minh',
    ignoreHTTPSErrors: true
  });
  const page = await context.newPage();
  return { context, page };
}

export async function open(session, route, options = {}) {
  const response = await session.page.goto(`${BASE}${route}`, {
    waitUntil: options.waitUntil || 'domcontentloaded',
    timeout: options.timeout || 20000
  });
  if (options.settle !== false) await session.page.waitForTimeout(options.wait || 350);
  return response;
}

export async function api(session, method, route, data, options = {}) {
  const request = options.context ? session.context.request : session.context.request;
  const response = await request.fetch(`${BASE}${route}`, {
    method,
    data,
    failOnStatusCode: false,
    headers: { Accept: 'application/json', ...(options.headers || {}) },
    timeout: options.timeout || 20000,
    ...(options.multipart ? { multipart: options.multipart } : {})
  });
  let body = null;
  const text = await response.text().catch(() => '');
  try { body = text ? JSON.parse(text) : null; } catch { body = text; }
  return { status: response.status(), body, headers: response.headers(), text };
}

export function diagnostics(page) {
  const consoleErrors = [];
  const pageErrors = [];
  const failedRequests = [];
  const requests = [];
  page.on('console', message => {
    if (message.type() === 'error') consoleErrors.push(message.text());
  });
  page.on('pageerror', error => pageErrors.push(error.message));
  page.on('request', request => requests.push({ method: request.method(), url: request.url() }));
  page.on('requestfailed', request => {
    const url = request.url();
    if (url.startsWith('data:')) return;
    failedRequests.push({ url, error: request.failure()?.errorText || 'unknown' });
  });
  return { consoleErrors, pageErrors, failedRequests, requests };
}

export function assertClean(diag, message = 'browser diagnostics') {
  const errors = [...diag.pageErrors, ...diag.consoleErrors];
  if (errors.length) throw new Error(`${message}: ${errors.slice(0, 3).join(' | ')}`);
}

export async function screenshot(page, name, options = {}) {
  const file = path.join(OUT, 'screenshots', `${name}.png`);
  await page.screenshot({ path: file, fullPage: options.fullPage === true });
  return file;
}

export async function visibleText(page, selector) {
  const locator = page.locator(selector).first();
  if (!(await locator.count())) return null;
  return (await locator.textContent())?.trim() ?? null;
}

export async function waitForText(page, selector, pattern, timeout = 8000) {
  const locator = page.locator(selector).first();
  await locator.waitFor({ state: 'visible', timeout });
  const text = (await locator.textContent()) || '';
  if (pattern && !pattern.test(text)) {
    throw new Error(`text ${selector} did not match ${pattern}: ${text.trim().slice(0, 160)}`);
  }
  return text.trim();
}

export function uniqueSuffix() {
  return `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`;
}

export function writeResults(results) {
  results.finishedAt = new Date().toISOString();
  const counts = results.entries.reduce((acc, entry) => {
    acc[entry.status] = (acc[entry.status] || 0) + 1;
    return acc;
  }, {});
  results.counts = counts;
  results.overall = counts.FAIL ? 'FAIL' : (counts.BLOCKED || counts['NOT RUN'] ? 'PASS WITH LIMITATIONS' : 'PASS');
  const file = path.join(OUT, 'results.json');
  fs.writeFileSync(file, JSON.stringify(results, null, 2));
  return { file, counts, overall: results.overall };
}

export function saveJson(name, value) {
  const file = path.join(OUT, name);
  fs.writeFileSync(file, JSON.stringify(value, null, 2));
  return file;
}
