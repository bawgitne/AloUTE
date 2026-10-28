/* Read-only Playwright measurement harness for AloUTE frontend hardening.
   Usage: NODE_PATH=<playwright node_modules> OUT_DIR=<dir> RUN_LABEL=baseline node measure.cjs */
const { chromium } = require('playwright');
const fs = require('node:fs');
const path = require('node:path');

const BASE = process.env.BASE_URL || 'http://localhost:8081';
const OUT = process.env.OUT_DIR || 'test-results/quan-frontend-hardening/baseline';
const LABEL = process.env.RUN_LABEL || 'baseline';
const VIEWPORTS = [360, 375, 390, 768, 1440];
const ROUTES = [
  { path: '/feed', name: 'feed' },
  { path: '/search?q=hoc-tap', name: 'search' },
  { path: '/posts/1', name: 'post-detail' }
];

(async () => {
  const browser = await chromium.launch({ channel: 'chrome', headless: true });
  fs.mkdirSync(OUT, { recursive: true });
  const report = { base: BASE, label: LABEL, generatedAt: new Date().toISOString(), viewports: VIEWPORTS, runs: [] };

  for (const width of VIEWPORTS) {
    const context = await browser.newContext({ viewport: { width, height: 900 }, deviceScaleFactor: 1 });
    const page = await context.newPage();
    let consoleErrors = [];
    let failedRequests = [];
    page.on('console', m => { if (m.type() === 'error') consoleErrors.push(m.text()); });
    page.on('requestfailed', r => failedRequests.push({ url: r.url(), error: r.failure() ? r.failure().errorText : null }));

    for (const route of ROUTES) {
      await page.goto(BASE + route.path, { waitUntil: 'domcontentloaded' });
      await page.waitForTimeout(1200);

      const metrics = await page.evaluate(() => {
        const rect = el => {
          if (!el) return null;
          const r = el.getBoundingClientRect();
          return { x: +r.x.toFixed(1), y: +r.y.toFixed(1), w: +r.width.toFixed(1), h: +r.height.toFixed(1), right: +r.right.toFixed(1), left: +r.left.toFixed(1) };
        };
        const visible = el => !!el && el.offsetParent !== null && getComputedStyle(el).visibility !== 'hidden';
        const nav = document.querySelector('.aloute-navbar');
        const register = document.querySelector('[data-action="navigate-register"]');
        const login = document.querySelector('[data-action="navigate-login"]');
        const interactive = Array.from(document.querySelectorAll(
          '.aloute-navbar .btn, .aloute-navbar button, .composer-box, .interaction-btn, .aloute-auth-cta, .sidebar-nav-link, .post-card .btn, .post-card button, .post-card-header .dropdown > button, #reply-form .btn, #reply-form textarea'
        )).filter(visible);
        const smallTargets = interactive.map(el => ({
          tag: el.tagName,
          cls: (el.className || '').toString().slice(0, 90),
          label: (el.getAttribute('aria-label') || el.textContent || '').trim().slice(0, 40),
          ...rect(el)
        })).filter(t => t.w < 44 || t.h < 44);
        const h1s = Array.from(document.querySelectorAll('h1')).map(h => h.textContent.trim());
        const skip = document.querySelector('a[href="#main-content"]');
        const cs = getComputedStyle(document.documentElement);
        const regRect = register ? register.getBoundingClientRect() : null;
        return {
          title: document.title,
          lang: document.documentElement.lang,
          innerWidth: window.innerWidth,
          clientWidth: document.documentElement.clientWidth,
          scrollWidth: document.documentElement.scrollWidth,
          horizontalOverflow: document.documentElement.scrollWidth > document.documentElement.clientWidth,
          navbar: rect(nav),
          navbarScrollWidth: nav ? nav.scrollWidth : null,
          navbarClientWidth: nav ? nav.clientWidth : null,
          register: rect(register),
          registerHref: register ? register.getAttribute('href') : null,
          login: rect(login),
          loginHref: login ? login.getAttribute('href') : null,
          registerClipped: regRect ? (regRect.right > document.documentElement.clientWidth + 0.5 || regRect.left < -0.5) : null,
          registerVisible: visible(register),
          h1s,
          h1Count: h1s.length,
          skipLink: skip ? { href: skip.getAttribute('href'), text: skip.textContent.trim(), rect: rect(skip) } : null,
          mainPresent: !!document.querySelector('main#main-content'),
          composerVisible: visible(document.querySelector('.composer-box')),
          replyFormVisible: visible(document.querySelector('#reply-form')),
          reportActionCount: document.querySelectorAll('[data-action="open-report-modal"]').length,
          authActionLinks: document.querySelectorAll('[data-action="navigate-login"], [data-action="navigate-register"]').length,
          hashAuthLinks: Array.from(document.querySelectorAll('[data-action="navigate-login"], [data-action="navigate-register"]')).filter(a => a.getAttribute('href') === '#').length,
          tokenTextPrimary: cs.getPropertyValue('--al-text-primary').trim() || null,
          tokenAlouteTextPrimary: cs.getPropertyValue('--aloute-text-primary').trim() || null,
          smallTargets
        };
      });

      report.runs.push({ width, route: route.path, name: route.name, metrics, consoleErrors: [...consoleErrors], failedRequests: [...failedRequests] });

      if (route.name === 'feed' || route.name === 'search') {
        await page.screenshot({ path: path.join(OUT, `${route.name}-${width}.png`) });
        await page.screenshot({ path: path.join(OUT, `${route.name}-${width}-full.png`), fullPage: true });
      }
      consoleErrors = [];
      failedRequests = [];
    }
    await context.close();
  }

  await browser.close();
  fs.writeFileSync(path.join(OUT, 'measurements.json'), JSON.stringify(report, null, 2));
  console.log('WROTE', path.join(OUT, 'measurements.json'), 'runs=', report.runs.length);
})().catch(err => { console.error(err); process.exit(1); });
