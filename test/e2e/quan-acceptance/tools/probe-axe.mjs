import { chromium } from 'playwright';
import { BASE, account } from '../lib/harness.mjs';
import fs from 'node:fs';

const axeSource = fs.readFileSync(new URL('../node_modules/axe-core/axe.min.js', import.meta.url), 'utf8');

const pages = process.argv.slice(2).length
  ? process.argv.slice(2).map(s => {
      const [who, route] = s.split('=', 2);
      return { who, route };
    })
  : [
      { who: 'guest', route: '/login' },
      { who: 'guest', route: '/register' },
      { who: 'user', route: '/feed' },
      { who: 'user', route: '/posts/1' },
      { who: 'user', route: '/users/demoan' },
      { who: 'user', route: '/notifications' },
      { who: 'user', route: '/messages' },
      { who: 'user', route: '/communities' },
      { who: 'user', route: '/topics/hoc-tap' },
      { who: 'user', route: '/settings' },
      { who: 'admin', route: '/admin' },
      { who: 'admin', route: '/admin/users' },
      { who: 'admin', route: '/admin/posts' },
      { who: 'admin', route: '/admin/reports' },
      { who: 'admin', route: '/admin/moderation-actions' }
    ];

const browser = await chromium.launch({ channel: 'chrome', headless: true });
let total = 0;

for (const spec of pages) {
  const context = await browser.newContext({ baseURL: BASE, viewport: { width: 1440, height: 900 }, locale: 'vi-VN' });
  if (spec.who !== 'guest') {
    const { email, password } = account(spec.who);
    const res = await context.request.post(`${BASE}/api/auth/login`, { data: { email, password }, headers: { 'Content-Type': 'application/json', Accept: 'application/json' } });
    if (res.status() !== 200) { console.log(spec.who, 'login failed', res.status()); await context.close(); continue; }
  }
  const page = await context.newPage();
  const resp = await page.goto(`${BASE}${spec.route}`, { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(900);
  const status = resp ? resp.status() : 0;
  await page.addScriptTag({ content: axeSource });
  const result = await page.evaluate(async () => {
    return await window.axe.run(document, { runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa'] } });
  });
  total += result.violations.length;
  console.log(`\n=== ${spec.who} ${spec.route} [${status}] :: ${result.violations.length} violations ===`);
  for (const v of result.violations) {
    console.log(`- [${v.impact}] ${v.id}: ${v.nodes.length} node(s)`);
    const shown = v.id === 'color-contrast' ? v.nodes : v.nodes.slice(0, 4);
    for (const n of shown) {
      console.log(`    target: ${n.target.join(' ')}`);
      console.log(`    html: ${n.html.slice(0, 160)}`);
      if (n.any?.[0]?.message) console.log(`    why: ${n.any[0].message.slice(0, 160)}`);
    }
    if (shown.length < v.nodes.length) console.log(`    ... ${v.nodes.length - shown.length} more`);
  }
  await context.close();
}
console.log(`\nTOTAL violations: ${total}`);
await browser.close();
