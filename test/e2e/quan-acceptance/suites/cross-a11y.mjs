import fs from 'node:fs';
import process from 'node:process';
import { chromium, firefox, webkit } from 'playwright';
import {
  BASE, STATUS, test, record, blocked, login, guest, open, api,
  diagnostics, assert, assertEqual, assertClean, screenshot, saveJson, uniqueSuffix
} from '../lib/harness.mjs';

const S = 'cross-a11y';
const AXE_SOURCE = fs.readFileSync(new URL('../node_modules/axe-core/axe.min.js', import.meta.url), 'utf8');
const AXE_TAGS = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa'];

const axeAudit = [];

async function scan(page) {
  await page.addScriptTag({ content: AXE_SOURCE });
  return page.evaluate(async tags => {
    const result = await window.axe.run(document, { runOnly: { type: 'tag', values: tags } });
    return {
      violations: result.violations.map(v => ({
        id: v.id,
        impact: v.impact,
        help: v.help,
        nodes: v.nodes.map(n => n.target.join(' ')).slice(0, 8)
      })),
      passes: result.passes.length,
      incomplete: result.incomplete.map(v => v.id)
    };
  }, AXE_TAGS);
}

function blocking(violations) {
  return violations.filter(v => v.impact === 'serious' || v.impact === 'critical');
}

async function auditRoute(results, session, id, who, route, axeSummary) {
  return test(results, S, id, `Axe WCAG 2.0/2.1/2.2 AA — ${who} ${route}`, async () => {
    const response = await open(session, route, { wait: 900 });
    assert(response && response.status() === 200, `${route} phải trả 200, nhận ${response ? response.status() : 'null'}`);
    const result = await scan(session.page);
    const entry = { who, route, violationCount: result.violations.length, violations: result.violations };
    axeSummary.push(entry);
    axeAudit.push(entry);
    const bad = blocking(result.violations);
    assert(bad.length === 0,
      `${bad.length} vi phạm serious/critical: ${bad.map(v => `${v.id} [${v.nodes.join(' | ')}]`).join(' ; ')}`);
    return {
      route,
      passes: result.passes,
      violations: result.violations.map(v => `${v.id}:${v.impact}`),
      incomplete: result.incomplete
    };
  });
}

async function overflow(page) {
  return page.evaluate(() => ({
    scrollWidth: document.documentElement.scrollWidth,
    clientWidth: document.documentElement.clientWidth,
    innerWidth: window.innerWidth
  }));
}

async function createCommunity(session) {
  let cookies = await session.context.cookies();
  if (!cookies.some(c => c.name === 'XSRF-TOKEN')) {
    await open(session, '/feed', { wait: 300 });
    cookies = await session.context.cookies();
  }
  const token = cookies.find(c => c.name === 'XSRF-TOKEN');
  const slug = `qa-a11y-${uniqueSuffix()}`;
  const response = await api(session, 'POST', '/api/communities', {
    name: `QA A11y ${slug}`, slug, description: 'Cộng đồng tạm cho kiểm tra accessibility', isPrivate: false
  }, { headers: token ? { 'X-XSRF-TOKEN': decodeURIComponent(token.value) } : {} });
  assertEqual(response.status, 201, 'Tạo cộng đồng tạm');
  return { community: response.body.data, slug, token: token ? decodeURIComponent(token.value) : null };
}

async function engineSmoke(results, id, label, launcher) {
  let engine;
  try {
    engine = await launcher();
  } catch (error) {
    blocked(results, S, id, `Cross-browser smoke — ${label}`, `Không khởi chạy được ${label}: ${error.message}`);
    return;
  }

  let page = null;
  try {
    const context = await engine.newContext({
      baseURL: BASE,
      viewport: { width: 1440, height: 900 },
      locale: 'vi-VN',
      timezoneId: 'Asia/Ho_Chi_Minh'
    });
    page = await context.newPage();
    const diag = diagnostics(page);

    const loginPage = await page.goto(`${BASE}/login`, { waitUntil: 'domcontentloaded' });
    assertEqual(loginPage.status(), 200, 'GET /login');
    await page.waitForTimeout(500);
    const basics = await page.evaluate(() => ({
      lang: document.documentElement.lang,
      title: document.title,
      bodyBg: getComputedStyle(document.body).backgroundColor,
      primaryToken: getComputedStyle(document.documentElement).getPropertyValue('--aloute-text-primary').trim()
    }));
    assert(basics.lang && basics.lang.toLowerCase().startsWith('vi'), `html lang phải là vi, nhận "${basics.lang}"`);
    assert(basics.title.trim().length > 0, 'Trang phải có title');
    assert(basics.bodyBg !== 'rgba(0, 0, 0, 0)', 'CSS phải được tải (body background không trong suốt)');

    const login = await context.request.post(`${BASE}/api/auth/login`, {
      data: { email: process.env.ALOUTE_USER_EMAIL, password: process.env.ALOUTE_USER_PASSWORD },
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' }
    });
    assertEqual(login.status(), 200, 'API login');

    const feed = await page.goto(`${BASE}/feed`, { waitUntil: 'domcontentloaded' });
    assertEqual(feed.status(), 200, 'GET /feed');
    await page.waitForTimeout(1000);
    const posts = await page.locator('.post-card').count();
    assert(posts > 0, 'Feed phải render ít nhất một bài viết');
    assertClean(diag, `${label} diagnostics`);

    record(results, S, id, `Cross-browser smoke — ${label}`, STATUS.PASS, {
      engineVersion: engine.version(),
      posts,
      title: basics.title,
      bodyBg: basics.bodyBg,
      token: basics.primaryToken
    });
    await context.close();
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    if (!page) {
      // The engine crashed before any page existed, so the app was never exercised:
      // this is an environment limitation, not a product defect.
      blocked(results, S, id, `Cross-browser smoke — ${label}`,
        `Trình duyệt ${label} ${engine.version()} không tạo được trang trên máy này: ${message}`);
    } else {
      record(results, S, id, `Cross-browser smoke — ${label}`, STATUS.FAIL, {
        engineVersion: engine.version(),
        error: message
      });
    }
  } finally {
    await engine.close().catch(() => {});
  }
}

export async function run({ browser, results }) {
  const axeSummary = [];
  const user = await login(browser, 'user');
  const admin = await login(browser, 'admin');
  const anon = await guest(browser);

  try {
    // ---------------------------------------------------------------------
    // Axe — guest
    // ---------------------------------------------------------------------
    await auditRoute(results, anon, 'A11Y-01', 'guest', '/', axeSummary);
    await auditRoute(results, anon, 'A11Y-02', 'guest', '/login', axeSummary);
    await auditRoute(results, anon, 'A11Y-03', 'guest', '/register', axeSummary);

    // ---------------------------------------------------------------------
    // Axe — authenticated user
    // ---------------------------------------------------------------------
    await auditRoute(results, user, 'A11Y-04', 'user', '/feed', axeSummary);
    await auditRoute(results, user, 'A11Y-05', 'user', '/posts/1', axeSummary);
    await auditRoute(results, user, 'A11Y-06', 'user', '/users/demoan', axeSummary);
    await auditRoute(results, user, 'A11Y-07', 'user', '/notifications', axeSummary);
    await auditRoute(results, user, 'A11Y-08', 'user', '/messages', axeSummary);
    await auditRoute(results, user, 'A11Y-09', 'user', '/settings', axeSummary);
    await auditRoute(results, user, 'A11Y-10', 'user', '/communities', axeSummary);
    await auditRoute(results, user, 'A11Y-11', 'user', '/topics/hoc-tap', axeSummary);
    await auditRoute(results, user, 'A11Y-12', 'user', '/friends', axeSummary);
    await auditRoute(results, user, 'A11Y-13', 'user', '/saved', axeSummary);
    await auditRoute(results, user, 'A11Y-14', 'user', '/search?q=aloute', axeSummary);

    // ---------------------------------------------------------------------
    // Axe — admin
    // ---------------------------------------------------------------------
    await auditRoute(results, admin, 'A11Y-15', 'admin', '/admin', axeSummary);
    await auditRoute(results, admin, 'A11Y-16', 'admin', '/admin/users', axeSummary);
    await auditRoute(results, admin, 'A11Y-17', 'admin', '/admin/posts', axeSummary);
    await auditRoute(results, admin, 'A11Y-18', 'admin', '/admin/reports', axeSummary);
    await auditRoute(results, admin, 'A11Y-19', 'admin', '/admin/moderation-actions', axeSummary);

    // Community detail uses a freshly created public community; deleted afterwards.
    let tempCommunity = null;
    try {
      tempCommunity = await createCommunity(user);
      await auditRoute(results, user, 'A11Y-20', 'user', `/c/${tempCommunity.slug}`, axeSummary);
    } finally {
      if (tempCommunity) {
        await api(user, 'DELETE', `/api/communities/${tempCommunity.community.id}`, undefined, {
          headers: tempCommunity.token ? { 'X-XSRF-TOKEN': tempCommunity.token } : {}
        });
      }
    }

    // ---------------------------------------------------------------------
    // Document structure & keyboard
    // ---------------------------------------------------------------------
    await test(results, S, 'A11Y-21', 'Cấu trúc tài liệu: lang, title, một main, có h1, skip link', async () => {
      await open(user, '/feed', { wait: 700 });
      const doc = await user.page.evaluate(() => ({
        lang: document.documentElement.lang,
        title: document.title,
        mainCount: document.querySelectorAll('main').length,
        h1Count: document.querySelectorAll('h1').length,
        skipLinks: document.querySelectorAll('a.aloute-skip-link').length,
        mainId: document.querySelector('main')?.id || null
      }));
      assert(doc.lang && doc.lang.toLowerCase().startsWith('vi'), 'html lang=vi');
      assert(doc.title.trim().length > 0, 'title không rỗng');
      assertEqual(doc.mainCount, 1, 'Số lượng main');
      assert(doc.h1Count >= 1, 'Phải có ít nhất một h1');
      assertEqual(doc.skipLinks, 1, 'Số lượng skip link');
      assertEqual(doc.mainId, 'main-content', 'main phải có id main-content');
      return doc;
    });

    await test(results, S, 'A11Y-22', 'Keyboard: Tab trúng skip link, Enter chuyển focus vào main', async () => {
      await open(user, '/feed', { wait: 700 });
      await user.page.keyboard.press('Tab');
      const first = await user.page.evaluate(() => ({
        tag: document.activeElement?.tagName,
        cls: document.activeElement?.className || ''
      }));
      assert(String(first.cls).includes('aloute-skip-link'), `Phần tử focus đầu tiên phải là skip link, nhận ${first.tag}.${first.cls}`);
      await user.page.keyboard.press('Enter');
      await user.page.waitForTimeout(400);
      const focused = await user.page.evaluate(() => ({
        id: document.activeElement?.id || '',
        tag: document.activeElement?.tagName || ''
      }));
      assert(focused.id === 'main-content' || focused.tag === 'MAIN', `Focus phải vào main, nhận ${focused.tag}#${focused.id}`);
      return { first, focused };
    });

    // ---------------------------------------------------------------------
    // Responsive viewports
    // ---------------------------------------------------------------------
    await test(results, S, 'RESP-01', 'Mobile 360×800: feed không tràn ngang, menu offcanvas hiện, sidebar ẩn', async () => {
      const session = await login(browser, 'user', { viewport: { width: 360, height: 800 } });
      try {
        await open(session, '/feed', { wait: 900 });
        const size = await overflow(session.page);
        assert(size.scrollWidth <= size.clientWidth + 1, `Tràn ngang: scrollWidth=${size.scrollWidth} clientWidth=${size.clientWidth}`);
        assert(await session.page.locator('.aloute-nav-toggle-btn').isVisible(), 'Nút menu mobile phải hiển thị');
        assert(!(await session.page.locator('aside.aloute-left-panel').isVisible()), 'Sidebar trái phải ẩn ở mobile');
        await screenshot(session.page, 'resp-360-feed');
        return size;
      } finally {
        await session.context.close();
      }
    });

    await test(results, S, 'RESP-02', 'Tablet 768×1024: sidebar hiện, không tràn ngang', async () => {
      const session = await login(browser, 'user', { viewport: { width: 768, height: 1024 } });
      try {
        await open(session, '/feed', { wait: 900 });
        const size = await overflow(session.page);
        assert(size.scrollWidth <= size.clientWidth + 1, `Tràn ngang: scrollWidth=${size.scrollWidth} clientWidth=${size.clientWidth}`);
        assert(!(await session.page.locator('.aloute-nav-toggle-btn').isVisible()), 'Nút menu mobile phải ẩn từ md');
        assert(await session.page.locator('aside.aloute-left-panel').isVisible(), 'Sidebar trái phải hiện từ md');
        await screenshot(session.page, 'resp-768-feed');
        return size;
      } finally {
        await session.context.close();
      }
    });

    await test(results, S, 'RESP-03', 'Desktop 1440×900: sidebar + ô tìm kiếm hiện, không tràn ngang', async () => {
      const session = await login(browser, 'user', { viewport: { width: 1440, height: 900 } });
      try {
        await open(session, '/feed', { wait: 900 });
        const size = await overflow(session.page);
        assert(size.scrollWidth <= size.clientWidth + 1, `Tràn ngang: scrollWidth=${size.scrollWidth} clientWidth=${size.clientWidth}`);
        assert(await session.page.locator('aside.aloute-left-panel').isVisible(), 'Sidebar trái phải hiện ở desktop');
        assert(await session.page.locator('form[role="search"]').first().isVisible(), 'Ô tìm kiếm phải hiện ở desktop');
        await screenshot(session.page, 'resp-1440-feed');
        return size;
      } finally {
        await session.context.close();
      }
    });

    await test(results, S, 'RESP-04', 'Mobile 360: trang Tin nhắn không tràn ngang, pane hội thoại dùng được', async () => {
      const session = await login(browser, 'user', { viewport: { width: 360, height: 800 } });
      try {
        await open(session, '/messages', { wait: 1000 });
        const size = await overflow(session.page);
        assert(size.scrollWidth <= size.clientWidth + 1, `Tràn ngang: scrollWidth=${size.scrollWidth} clientWidth=${size.clientWidth}`);
        const panes = await session.page.evaluate(() => ({
          conversation: !!document.querySelector('#conversation-pane') && getComputedStyle(document.querySelector('#conversation-pane')).display !== 'none',
          chat: !!document.querySelector('#chat-active-container') && !document.querySelector('#chat-active-container').classList.contains('d-none')
        }));
        assert(panes.conversation || panes.chat, 'Phải hiển thị pane danh sách hoặc pane chat');
        await screenshot(session.page, 'resp-360-messages');
        return { ...size, panes };
      } finally {
        await session.context.close();
      }
    });

    await test(results, S, 'RESP-05', 'Mobile 360: trang quản trị không tràn ngang, bảng cuộn trong container', async () => {
      const session = await login(browser, 'admin', { viewport: { width: 360, height: 800 } });
      try {
        await open(session, '/admin', { wait: 1000 });
        const size = await overflow(session.page);
        assert(size.scrollWidth <= size.clientWidth + 1, `Tràn ngang: scrollWidth=${size.scrollWidth} clientWidth=${size.clientWidth}`);
        const tables = await session.page.locator('.admin-table-container').count();
        assert(tables > 0, 'Trang admin phải có container bảng');
        await screenshot(session.page, 'resp-360-admin');
        return { ...size, tables };
      } finally {
        await session.context.close();
      }
    });

    await test(results, S, 'RESP-06', 'Touch target ≥ 24×24 px cho nút navbar và thanh tương tác (mobile)', async () => {
      const session = await login(browser, 'user', { viewport: { width: 360, height: 800 } });
      try {
        await open(session, '/feed', { wait: 900 });
        const small = await session.page.evaluate(() => {
          const nodes = Array.from(document.querySelectorAll('.aloute-navbar button, .aloute-navbar .btn, .interaction-bar .interaction-btn'));
          return nodes
            .filter(el => {
              const style = getComputedStyle(el);
              const rect = el.getBoundingClientRect();
              return style.display !== 'none' && style.visibility !== 'hidden' && rect.width > 0 && rect.height > 0;
            })
            .map(el => {
              const rect = el.getBoundingClientRect();
              return {
                tag: el.tagName,
                label: (el.getAttribute('aria-label') || el.textContent || '').trim().slice(0, 40),
                width: Math.round(rect.width),
                height: Math.round(rect.height)
              };
            })
            .filter(item => item.width < 24 || item.height < 24);
        });
        assert(small.length === 0, `Touch target < 24px: ${small.map(s => `${s.label}(${s.width}x${s.height})`).join(', ')}`);
        return { checked: true };
      } finally {
        await session.context.close();
      }
    });

    // ---------------------------------------------------------------------
    // Cross-browser
    // ---------------------------------------------------------------------
    await engineSmoke(results, 'XB-01', 'Chromium (Chrome)', () => chromium.launch({ channel: 'chrome', headless: true }));
    await engineSmoke(results, 'XB-02', 'Chromium (Edge)', () => chromium.launch({ channel: 'msedge', headless: true }));
    await engineSmoke(results, 'XB-03', 'Firefox', () => firefox.launch({ headless: true }));
    await engineSmoke(results, 'XB-04', 'WebKit (Safari engine)', () => webkit.launch({ headless: true }));

    // ---------------------------------------------------------------------
    // Performance
    // ---------------------------------------------------------------------
    await test(results, S, 'PERF-01', 'Feed: DCL/load/LCP và transfer size qua 3 lần tải (Chrome)', async () => {
      await user.page.addInitScript(() => {
        window.__lcp = 0;
        try {
          new PerformanceObserver(list => {
            for (const entry of list.getEntries()) window.__lcp = entry.startTime;
          }).observe({ type: 'largest-contentful-paint', buffered: true });
        } catch (_) { /* unsupported */ }
      });
      const runs = [];
      for (let i = 0; i < 3; i += 1) {
        const wallStart = Date.now();
        const response = await user.page.goto(`${BASE}/feed`, { waitUntil: 'load', timeout: 25000 });
        assertEqual(response.status(), 200, 'GET /feed');
        await user.page.waitForTimeout(700);
        const metrics = await user.page.evaluate(() => {
          const nav = performance.getEntriesByType('navigation')[0] || {};
          const resources = performance.getEntriesByType('resource');
          return {
            dcl: Math.round(nav.domContentLoadedEventEnd || 0),
            load: Math.round(nav.loadEventEnd || 0),
            domInteractive: Math.round(nav.domInteractive || 0),
            responseStart: Math.round(nav.responseStart || 0),
            transferSize: nav.transferSize || 0,
            resourceCount: resources.length,
            resourceTransfer: Math.round(resources.reduce((sum, r) => sum + (r.transferSize || 0), 0)),
            externalRequests: resources.filter(r => /^https?:/.test(r.name) && !r.name.startsWith(location.origin)).length,
            lcp: Math.round(window.__lcp || 0)
          };
        });
        runs.push({ ...metrics, wall: Date.now() - wallStart });
      }
      const dcl = runs.map(r => r.dcl).sort((a, b) => a - b);
      const median = dcl[Math.floor(dcl.length / 2)];
      assert(median < 3000, `DCL trung vị quá cao: ${median}ms`);
      assert(runs.every(r => r.load < 5000), 'Có lần tải load > 5000ms');
      return { medianDcl: median, runs };
    });

    await test(results, S, 'PERF-02', 'API latency p95 < 800ms cho feed, thông báo và unread-count', async () => {
      const endpoints = [
        '/api/feed?page=0&size=10',
        '/api/notifications?filter=ALL&page=0&size=20',
        '/api/notifications/unread-count'
      ];
      const summary = {};
      for (const endpoint of endpoints) {
        const samples = [];
        for (let i = 0; i < 6; i += 1) {
          const start = process.hrtime.bigint();
          const response = await user.context.request.get(`${BASE}${endpoint}`, { headers: { Accept: 'application/json' } });
          const elapsed = Number(process.hrtime.bigint() - start) / 1e6;
          assertEqual(response.status(), 200, `GET ${endpoint}`);
          if (i > 0) samples.push(elapsed);
        }
        samples.sort((a, b) => a - b);
        const p50 = samples[Math.floor(samples.length / 2)];
        const p95 = samples[Math.min(samples.length - 1, Math.ceil(samples.length * 0.95) - 1)];
        summary[endpoint] = { p50: Math.round(p50), p95: Math.round(p95), samples: samples.map(s => Math.round(s)) };
        assert(p95 < 800, `${endpoint} p95=${Math.round(p95)}ms vượt 800ms`);
      }
      return summary;
    });

    await test(results, S, 'PERF-03', 'Tài nguyên CSS cục bộ trả 200 và đúng content-type', async () => {
      const response = await user.context.request.get(`${BASE}/css/app.css`);
      assertEqual(response.status(), 200, 'GET /css/app.css');
      const headers = response.headers();
      assert(/text\/css/.test(headers['content-type'] || ''), `Content-Type phải là text/css, nhận ${headers['content-type']}`);
      return {
        contentLength: (await response.body()).length,
        cacheControl: headers['cache-control'] || null,
        etag: headers.etag || null
      };
    });
  } finally {
    saveJson('axe-results.json', {
      tags: AXE_TAGS,
      generatedAt: new Date().toISOString(),
      routes: axeSummary
    });
    await anon.context.close();
    await user.context.close();
    await admin.context.close();
  }
}
