/* Browser verification for the guest/accessibility hardening.
   Guest checks are production-safe; the authenticated check uses local dev seed data only. */
const { chromium } = require('playwright');
const fs = require('node:fs');
const path = require('node:path');

const BASE = process.env.BASE_URL || 'http://localhost:8081';
const OUT = process.env.OUT_DIR || 'test-results/quan-frontend-hardening/after';
/* Local dev seed only; never committed. See docs/DEMO_CHECKLIST_VI.md. */
const SEED_EMAIL = process.env.ALOUTE_SEED_EMAIL || '';
const SEED_PASSWORD = process.env.ALOUTE_SEED_PASSWORD || '';
const results = [];
const check = (name, pass, detail) => results.push({ name, pass: !!pass, detail });

(async () => {
  const browser = await chromium.launch({ channel: 'chrome', headless: true });
  fs.mkdirSync(OUT, { recursive: true });

  // 1) Guest at 360px: keyboard skip link + gated interaction surfaces
  let context = await browser.newContext({ viewport: { width: 360, height: 900 } });
  let page = await context.newPage();
  const guestErrors = [];
  page.on('console', m => { if (m.type() === 'error') guestErrors.push(m.text()); });
  await page.goto(BASE + '/feed', { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(800);

  await page.keyboard.press('Tab');
  await page.waitForTimeout(300);
  const firstStop = await page.evaluate(() => {
    const el = document.activeElement;
    const r = el.getBoundingClientRect();
    return { cls: el.className, text: (el.textContent || '').trim(), top: r.top, height: r.height };
  });
  check('skip link is the first tab stop', /aloute-skip-link/.test(firstStop.cls), firstStop);
  check('skip link becomes visible on focus', firstStop.top >= 0 && firstStop.height > 0, firstStop);

  await page.keyboard.press('Enter');
  await page.waitForTimeout(250);
  const afterSkip = await page.evaluate(() => ({ hash: location.hash, activeId: document.activeElement.id }));
  check('skip link moves focus to #main-content', afterSkip.hash === '#main-content' && afterSkip.activeId === 'main-content', afterSkip);

  const guestState = await page.evaluate(() => ({
    composer: !!document.querySelector('.composer-box'),
    guestCta: !!document.querySelector('section[aria-label="Đăng nhập để đăng bài"]'),
    actionMenus: document.querySelectorAll('[data-action="open-report-modal"]').length,
    quoteButtons: document.querySelectorAll('[data-action="open-quote-modal"]').length,
    h1: Array.from(document.querySelectorAll('h1')).map(h => h.textContent.trim())
  }));
  check('guest sees no composer', !guestState.composer, guestState);
  check('guest sees the login CTA instead', guestState.guestCta, guestState);
  check('guest sees no report/action menu', guestState.actionMenus === 0, guestState);
  check('guest sees no quote trigger', guestState.quoteButtons === 0, guestState);
  check('feed has one h1', guestState.h1.length === 1, guestState.h1);
  const guestCtas = await page.evaluate(() => Array.from(document.querySelectorAll('.aloute-auth-cta'))
    .filter(el => el.offsetParent !== null)
    .map(el => { const r = el.getBoundingClientRect(); return { text: el.textContent.trim(), w: +r.width.toFixed(1), h: +r.height.toFixed(1) }; }));
  check('guest auth CTAs reach 44px height', guestCtas.length > 0 && guestCtas.every(t => t.h >= 44), guestCtas);
  check('no console errors on guest feed', guestErrors.length === 0, guestErrors);
  await page.screenshot({ path: path.join(OUT, 'verify-guest-feed-360.png') });

  await page.goto(BASE + '/posts/1', { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(600);
  const guestPostState = await page.evaluate(() => ({
    createActions: Array.from(document.querySelectorAll('[data-action="navigate-create-post"]')).filter(el => el.offsetParent !== null).length,
    modal: !!document.querySelector('#createPostModal'),
    composerTextarea: Array.from(document.querySelectorAll('#createPostModal textarea')).length
  }));
  check('guest sees no create-post action on post detail', guestPostState.createActions === 0, guestPostState);
  check('guest has no create-post modal/composer', !guestPostState.modal && guestPostState.composerTextarea === 0, guestPostState);
  check('no console errors on guest post detail', guestErrors.length === 0, guestErrors);

  await page.goto(BASE + '/topics/hoc-tap', { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(500);
  const guestTopicState = await page.evaluate(() => ({
    h1: (document.querySelector('h1')?.textContent || '').trim(),
    createActions: Array.from(document.querySelectorAll('[data-action="navigate-create-post"]')).filter(el => el.offsetParent !== null).length,
    modal: !!document.querySelector('#createPostModal')
  }));
  check('guest sees no create-post action on a public topic page', guestTopicState.createActions === 0 && !guestTopicState.modal, guestTopicState);
  check('public topic page still renders for guests', guestTopicState.h1.startsWith('#'), guestTopicState.h1);
  await page.screenshot({ path: path.join(OUT, 'verify-guest-topic-360.png') });
  await context.close();

  // 2) Login link still works with JavaScript disabled (real href fallback)
  context = await browser.newContext({ viewport: { width: 390, height: 900 }, javaScriptEnabled: false });
  page = await context.newPage();
  await page.goto(BASE + '/feed', { waitUntil: 'domcontentloaded' });
  await page.click('[data-action="navigate-login"]');
  await page.waitForLoadState('domcontentloaded');
  check('guest login link works without JavaScript', page.url().includes('/login'), page.url());
  await context.close();

  // 3) Authenticated local-dev smoke: gating must not hide authenticated UI
  context = await browser.newContext({ viewport: { width: 390, height: 900 } });
  page = await context.newPage();
  const authErrors = [];
  page.on('console', m => { if (m.type() === 'error') authErrors.push(m.text()); });
  let loggedIn = false;
  if (SEED_EMAIL && SEED_PASSWORD) {
    await page.goto(BASE + '/login', { waitUntil: 'domcontentloaded' });
    await page.fill('input[name="email"]', SEED_EMAIL);
    await page.fill('input[name="password"]', SEED_PASSWORD);
    await page.click('#login-submit-btn');
    await page.waitForURL(u => !u.pathname.startsWith('/login'), { timeout: 15000 }).catch(() => {});
    loggedIn = !page.url().includes('/login');
    check('local dev login succeeds', loggedIn, page.url());
  } else {
    console.log('SKIP authenticated local-dev smoke: ALOUTE_SEED_EMAIL / ALOUTE_SEED_PASSWORD not set');
  }

  if (loggedIn) {
    await page.goto(BASE + '/feed', { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(800);
    const authState = await page.evaluate(() => ({
      composer: !!document.querySelector('.composer-box'),
      actionMenus: document.querySelectorAll('[data-action="open-report-modal"]').length,
      quoteButtons: document.querySelectorAll('[data-action="open-quote-modal"]').length
    }));
    check('authenticated user sees the composer', authState.composer, authState);
    check('authenticated user sees the post action menu', authState.actionMenus > 0, authState);
    await page.screenshot({ path: path.join(OUT, 'verify-auth-feed-390.png') });

    await page.goto(BASE + '/posts/1', { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(500);
    const replyState = await page.evaluate(() => ({
      replyForm: !!document.querySelector('#reply-form'),
      guestPrompt: !!document.querySelector('.guest-prompt-icon')
    }));
    check('authenticated user sees the reply form', replyState.replyForm, replyState);
    check('authenticated page shows no guest reply prompt', !replyState.guestPrompt, replyState);
    const authCreate = await page.evaluate(() => ({
      actions: Array.from(document.querySelectorAll('[data-action="navigate-create-post"]')).filter(el => el.offsetParent !== null).length,
      modal: !!document.querySelector('#createPostModal')
    }));
    check('authenticated user sees create-post action and modal', authCreate.actions > 0 && authCreate.modal, authCreate);

    await page.goto(BASE + '/topics/hoc-tap', { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(500);
    const authTopicCreate = await page.evaluate(() => Array.from(document.querySelectorAll('[data-action="navigate-create-post"]'))
      .filter(el => el.offsetParent !== null)
      .map(el => { const r = el.getBoundingClientRect(); return { text: el.textContent.trim(), h: +r.height.toFixed(1) }; }));
    check('authenticated user still sees create-post on topic page', authTopicCreate.length > 0, authTopicCreate);
    check('topic create-post action reaches 44px', authTopicCreate.every(t => t.h >= 44), authTopicCreate);

    await page.goto(BASE + '/settings', { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(400);
    const singleLayoutState = await page.evaluate(() => ({
      skip: !!document.querySelector('a.aloute-skip-link[href="#main-content"]'),
      main: !!document.querySelector('main#main-content')
    }));
    check('single layout (settings) exposes the skip link', singleLayoutState.skip && singleLayoutState.main, singleLayoutState);

    await page.goto(BASE + '/feed', { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(800);
    await page.click('.navbar-notification-link');
    await page.waitForTimeout(400);
    const markAll = await page.evaluate(() => {
      const el = document.querySelector('#btn-navbar-mark-all-read');
      if (!el) return null;
      const r = el.getBoundingClientRect();
      return { h: +r.height.toFixed(1), w: +r.width.toFixed(1), visible: el.offsetParent !== null };
    });
    check('dense notification menu action keeps its compact height', !!markAll && markAll.visible && markAll.h < 44, markAll);
    check('no console errors on authenticated pages', authErrors.length === 0, authErrors);
  }
  await context.close();

  await browser.close();
  const summary = { base: BASE, generatedAt: new Date().toISOString(), pass: results.every(r => r.pass), results };
  fs.writeFileSync(path.join(OUT, 'verification.json'), JSON.stringify(summary, null, 2));
  for (const r of results) console.log(`${r.pass ? 'PASS' : 'FAIL'} ${r.name}`);
  console.log('OVERALL', summary.pass ? 'PASS' : 'FAIL');
  process.exitCode = summary.pass ? 0 : 1;
})().catch(err => { console.error(err); process.exit(1); });
