import crypto from 'node:crypto';
import {
  BASE, STATUS, test, blocked, notRun, assert, assertEqual, login, guest, open, api,
  diagnostics, assertClean, screenshot, visibleText, waitForText, record, uniqueSuffix
} from '../lib/harness.mjs';

const S = 'auth-guest';

async function statusText(page, options = {}) {
  const box = page.locator('#auth-status').first();
  await box.waitFor({ state: 'visible', timeout: 8000 });
  if (options.settle !== false) {
    await page.waitForFunction(() => {
      const el = document.getElementById('auth-status');
      const text = (el?.textContent || '').trim();
      return text.length > 0 && !/^Đang\s/i.test(text);
    }, null, { timeout: options.timeout || 12000 }).catch(() => null);
  }
  return (await box.textContent())?.trim() ?? '';
}

async function gotoExpectLogin(page, route) {
  const response = await page.goto(`${BASE}${route}`, { waitUntil: 'domcontentloaded', timeout: 20000 });
  await page.waitForTimeout(400);
  return { status: response?.status() ?? 0, url: page.url() };
}

function decodeJwt(token) {
  const [header, payload] = token.split('.');
  return {
    header: JSON.parse(Buffer.from(header, 'base64url').toString('utf8')),
    payload: JSON.parse(Buffer.from(payload, 'base64url').toString('utf8'))
  };
}

function signHs256(header, payload, secretB64) {
  const key = Buffer.from(secretB64, 'base64');
  const h = Buffer.from(JSON.stringify(header)).toString('base64url');
  const p = Buffer.from(JSON.stringify(payload)).toString('base64url');
  const sig = crypto.createHmac('sha256', key).update(`${h}.${p}`).digest('base64url');
  return `${h}.${p}.${sig}`;
}

export async function run({ browser, results }) {
  const runLabel = uniqueSuffix();

  /* ---------- Guest surface ---------- */

  await test(results, S, 'AUTH-01', 'Guest xem /feed công khai, thấy CTA đăng nhập và bài viết', async () => {
    const g = await guest(browser);
    const diag = diagnostics(g.page);
    const res = await open(g, '/feed');
    assertEqual(res.status(), 200, 'HTTP /feed');
    const h1 = await g.page.locator('h1').count();
    assert(h1 >= 1, 'Trang feed phải có h1');
    const cta = g.page.locator('section[aria-label="Đăng nhập để đăng bài"]');
    assert(await cta.count(), 'Guest phải thấy CTA đăng nhập thay composer');
    const posts = await g.page.locator('article.post-card').count();
    assert(posts > 0, 'Guest phải thấy bài viết công khai');
    assertClean(diag, 'Guest feed diagnostics');
    await screenshot(g.page, 'auth-01-guest-feed');
    await g.context.close();
    return { posts, h1 };
  });

  await test(results, S, 'AUTH-02', 'Trang đăng nhập render form, skip link và nhãn', async () => {
    const g = await guest(browser);
    const diag = diagnostics(g.page);
    const res = await open(g, '/login');
    assertEqual(res.status(), 200, 'HTTP /login');
    assert(await g.page.locator('#login-form').count() === 1, 'Thiếu #login-form');
    assert(await g.page.locator('label[for="email"]').count() === 1, 'Thiếu label email');
    assert(await g.page.locator('label[for="password"]').count() === 1, 'Thiếu label password');
    const skip = g.page.locator('a.aloute-skip-link, a.skip-link, a[href="#main-content"]').first();
    assert(await skip.count(), 'Thiếu skip link');
    await g.page.keyboard.press('Tab');
    const first = await g.page.evaluate(() => document.activeElement?.className || document.activeElement?.textContent || '');
    assert(String(first).includes('skip'), `Tab đầu tiên không phải skip link: ${first}`);
    assertClean(diag, 'Login diagnostics');
    await screenshot(g.page, 'auth-02-login');
    await g.context.close();
  });

  await test(results, S, 'AUTH-03', 'Đăng nhập UI hợp lệ chuyển /feed và cookie HttpOnly', async () => {
    const g = await guest(browser);
    const diag = diagnostics(g.page);
    await open(g, '/login');
    await g.page.fill('#email', process.env.ALOUTE_USER_EMAIL || '');
    await g.page.fill('#password', process.env.ALOUTE_USER_PASSWORD || '');
    await g.page.click('#login-submit-btn');
    await g.page.waitForURL('**/feed', { timeout: 15000 });
    const cookies = await g.context.cookies(BASE);
    const auth = cookies.find(c => c.name === 'aloute_access_token');
    assert(auth, 'Thiếu cookie aloute_access_token');
    assertEqual(auth.httpOnly, true, 'Cookie auth phải HttpOnly');
    const me = await api({ context: g.context }, 'GET', '/api/auth/me');
    assertEqual(me.status, 200, 'GET /api/auth/me sau login');
    assert(me.body?.data?.email, 'Response me thiếu email');
    assertClean(diag, 'Login UI diagnostics');
    await screenshot(g.page, 'auth-03-feed-after-login');
    await g.context.close();
    return { httpOnly: auth.httpOnly, sameSite: auth.sameSite };
  });

  await test(results, S, 'AUTH-04', 'Đăng nhập sai mật khẩu báo lỗi, không chuyển trang', async () => {
    const g = await guest(browser);
    await open(g, '/login');
    await g.page.fill('#email', process.env.ALOUTE_USER_EMAIL || '');
    await g.page.fill('#password', `wrong-${runLabel}`);
    await g.page.click('#login-submit-btn');
    const text = await statusText(g.page);
    assert(/không chính xác|không đúng|invalid/i.test(text), `Thông báo sai: ${text}`);
    assert(g.page.url().endsWith('/login'), `Không được chuyển trang: ${g.page.url()}`);
    assertEqual(await g.page.inputValue('#password'), '', 'Mật khẩu phải được xóa sau lỗi');
    await screenshot(g.page, 'auth-04-wrong-password');
    await g.context.close();
    return { message: text };
  });

  await test(results, S, 'AUTH-05', 'Submit rỗng hiển thị validation phía client', async () => {
    const g = await guest(browser);
    await open(g, '/login');
    await g.page.click('#login-submit-btn');
    await g.page.waitForTimeout(300);
    const invalidEmail = await g.page.locator('#email.is-invalid').count();
    const invalidPassword = await g.page.locator('#password.is-invalid').count();
    assert(invalidEmail === 1 && invalidPassword === 1, `Thiếu is-invalid: email=${invalidEmail}, password=${invalidPassword}`);
    const focused = await g.page.evaluate(() => document.activeElement?.id);
    assert(focused === 'email' || focused === 'password', `Focus không vào field lỗi: ${focused}`);
    assertEqual(await g.page.locator('#auth-status').evaluate(el => el.className.includes('d-none')), true, 'Không được gọi API khi form rỗng');
    await g.context.close();
    return { invalidEmail, invalidPassword, focused };
  });

  await test(results, S, 'AUTH-06', 'Guest truy cập route bảo vệ bị chuyển /login', async () => {
    const g = await guest(browser);
    const routes = ['/messages', '/notifications', '/saved', '/bookmarks', '/settings', '/friends', '/profile'];
    const observed = [];
    for (const route of routes) {
      const r = await gotoExpectLogin(g.page, route);
      observed.push({ route, status: r.status, url: new URL(r.url).pathname });
      assert(r.url.includes('/login'), `${route} phải chuyển /login, nhận ${r.url}`);
    }
    await g.context.close();
    return { routes: observed };
  });

  await test(results, S, 'AUTH-07', 'Guest API riêng tư trả 401 JSON, không HTML', async () => {
    const g = await guest(browser);
    const endpoints = ['/api/auth/me', '/api/conversations', '/api/notifications/unread-count', '/api/posts/bookmarks'];
    const observed = [];
    for (const route of endpoints) {
      const r = await api({ context: g.context }, 'GET', route);
      observed.push({ route, status: r.status, code: r.body?.error?.code ?? null });
      assertEqual(r.status, 401, `HTTP ${route}`);
      assert(r.body && typeof r.body === 'object', `${route} phải trả JSON envelope`);
    }
    await g.context.close();
    return { endpoints: observed };
  });

  await test(results, S, 'AUTH-08', 'Sau đăng nhập, refresh giữ phiên; trang auth chuyển /feed', async () => {
    const session = await login(browser, 'user');
    await open(session, '/feed');
    await session.page.reload({ waitUntil: 'domcontentloaded' });
    assert(session.page.url().includes('/feed'), 'Refresh /feed bị chuyển trang');
    for (const route of ['/login', '/register']) {
      const res = await session.page.goto(`${BASE}${route}`, { waitUntil: 'domcontentloaded' });
      await session.page.waitForTimeout(500);
      assert(session.page.url().includes('/feed'), `${route} phải redirect /feed khi đã đăng nhập, nhận ${session.page.url()} (HTTP ${res?.status()})`);
    }
    await session.context.close();
  });

  await test(results, S, 'AUTH-09', 'Đăng xuất bằng menu UI: chuyển /login, cookie bị thu hồi', async () => {
    const session = await login(browser, 'user');
    await open(session, '/feed');
    const oldToken = (await session.context.cookies(BASE)).find(c => c.name === 'aloute_access_token')?.value;
    assert(oldToken, 'Thiếu token trước logout');
    await session.page.click('#userNavbarDropdown');
    await session.page.locator('[data-action="logout"]').click();
    await session.page.waitForURL('**/login?logout=1', { timeout: 15000 });
    const after = (await session.context.cookies(BASE)).find(c => c.name === 'aloute_access_token');
    assert(!after, 'Cookie auth vẫn còn sau logout');
    const r = await gotoExpectLogin(session.page, '/settings');
    assert(r.url.includes('/login'), 'Route bảo vệ phải chuyển login sau logout');
    const reuse = await guest(browser);
    await reuse.context.addCookies([{
      name: 'aloute_access_token', value: oldToken, domain: '127.0.0.1', path: '/', httpOnly: true, sameSite: 'Lax'
    }]);
    const revoked = await gotoExpectLogin(reuse.page, '/settings');
    assert(revoked.url.includes('/login'), 'Token cũ phải bị thu hồi (revocation)');
    await reuse.context.close();
    await session.context.close();
    return { logoutUrl: '/login?logout=1', revokedTokenAccepted: false };
  });

  await test(results, S, 'AUTH-10', 'Browser Back sau logout không lộ nội dung bảo vệ', async () => {
    const session = await login(browser, 'user');
    await open(session, '/settings');
    const h1 = await session.page.locator('h1').first().textContent();
    await session.page.click('#userNavbarDropdown');
    await session.page.locator('[data-action="logout"]:visible').first().click();
    await session.page.waitForURL('**/login?logout=1', { timeout: 15000 });
    await session.page.goBack({ waitUntil: 'domcontentloaded' }).catch(() => null);
    await session.page.waitForTimeout(600);
    if (!session.page.url().includes('/login')) {
      await session.page.reload({ waitUntil: 'domcontentloaded' });
      await session.page.waitForTimeout(500);
    }
    assert(session.page.url().includes('/login'), `Back sau logout phải quay về login, nhận ${session.page.url()}`);
    await session.context.close();
    return { previousHeading: (h1 || '').trim().slice(0, 60) };
  });

  await test(results, S, 'AUTH-11', 'Đa tab: tab thứ hai dùng chung phiên; logout tab một chặn tab hai', async () => {
    const session = await login(browser, 'user');
    const tab2 = await session.context.newPage();
    await tab2.goto(`${BASE}/settings`, { waitUntil: 'domcontentloaded' });
    assert(tab2.url().includes('/settings'), `Tab 2 phải có phiên, nhận ${tab2.url()}`);
    await open(session, '/feed');
    await session.page.click('#userNavbarDropdown');
    await session.page.locator('[data-action="logout"]').click();
    await session.page.waitForURL('**/login?logout=1', { timeout: 15000 });
    await tab2.goto(`${BASE}/settings`, { waitUntil: 'domcontentloaded' });
    await tab2.waitForTimeout(400);
    assert(tab2.url().includes('/login'), `Tab 2 phải bị chặn sau logout, nhận ${tab2.url()}`);
    await session.context.close();
  });

  await test(results, S, 'AUTH-12', 'Cookie JWT bị sửa/không hợp lệ bị coi là guest', async () => {
    const g = await guest(browser);
    await g.context.addCookies([{
      name: 'aloute_access_token', value: 'tampered.invalid.token', domain: '127.0.0.1', path: '/', httpOnly: true, sameSite: 'Lax'
    }]);
    const me = await api({ context: g.context }, 'GET', '/api/auth/me');
    assertEqual(me.status, 401, 'Token rác phải 401');
    const r = await gotoExpectLogin(g.page, '/settings');
    assert(r.url.includes('/login'), 'Token rác phải bị coi là guest');
    await g.context.close();
  });

  const jwtSecret = process.env.ALOUTE_JWT_SECRET_B64;
  if (!jwtSecret) {
    blocked(results, S, 'AUTH-13', 'JWT hết hạn bị từ chối (token ký bằng dev secret)', 'Thiếu ALOUTE_JWT_SECRET_B64 trong môi trường chạy QA');
  } else {
    await test(results, S, 'AUTH-13', 'JWT hết hạn bị từ chối (token ký bằng dev secret)', async () => {
      const session = await login(browser, 'user');
      const token = (await session.context.cookies(BASE)).find(c => c.name === 'aloute_access_token')?.value;
      assert(token, 'Thiếu token để giả lập hết hạn');
      const { header, payload } = decodeJwt(token);
      const now = Math.floor(Date.now() / 1000);
      payload.exp = now - 60;
      payload.iat = now - 120;
      const expired = signHs256(header, payload, jwtSecret);
      const g = await guest(browser);
      await g.context.addCookies([{
        name: 'aloute_access_token', value: expired, domain: '127.0.0.1', path: '/', httpOnly: true, sameSite: 'Lax'
      }]);
      const me = await api({ context: g.context }, 'GET', '/api/auth/me');
      assertEqual(me.status, 401, 'JWT hết hạn phải 401');
      const r = await gotoExpectLogin(g.page, '/settings');
      assert(r.url.includes('/login'), 'JWT hết hạn phải bị coi là guest');
      await g.context.close();
      await session.context.close();
      return { expiredHandled: true };
    });
  }

  await test(results, S, 'AUTH-14', 'Route công khai không trả 500 khi guest (feed/post/topic/search)', async () => {
    const g = await guest(browser);
    const routes = ['/', '/feed', '/posts/1', '/topics/hoc-tap', '/search', '/communities'];
    const observed = [];
    for (const route of routes) {
      const res = await g.page.goto(`${BASE}${route}`, { waitUntil: 'domcontentloaded' });
      observed.push({ route, status: res?.status() });
      assert(res && res.status() < 500, `${route} trả HTTP ${res?.status()}`);
    }
    await g.context.close();
    return { routes: observed };
  });

  await test(results, S, 'AUTH-15', 'Guest /post/create phải chuyển login thay vì lỗi JSON', async () => {
    const g = await guest(browser);
    const r = await gotoExpectLogin(g.page, '/post/create');
    if (r.status >= 500 || !r.url.includes('/login')) {
      await screenshot(g.page, 'auth-15-guest-post-create');
      await g.context.close();
      throw new Error(`Guest /post/create trả HTTP ${r.status} tại ${r.url} — kỳ vọng 302 /login (BL-22 backend)`);    }
    await g.context.close();
  });

  await test(results, S, 'AUTH-16', 'Guest /admin phải chuyển login/403 HTML thay vì JSON API', async () => {
    const g = await guest(browser);
    const r = await gotoExpectLogin(g.page, '/admin');
    const body = await g.page.content();
    const isJson = /"error"\s*:\s*{/.test(body) || /AUTH_UNAUTHENTICATED/.test(body);
    if (r.status >= 500 || isJson || !r.url.includes('/login')) {
      await screenshot(g.page, 'auth-16-guest-admin');
      await g.context.close();
      throw new Error(`Guest /admin trả HTTP ${r.status}, URL ${r.url}, JSON=${isJson} — kỳ vọng redirect/403 HTML (BL-22 backend)`);
    }
    await g.context.close();
  });

  await test(results, S, 'AUTH-17', 'Trang đăng ký + validation client + email trùng', async () => {
    const g = await guest(browser);
    const diag = diagnostics(g.page);
    await open(g, '/register');
    await g.page.fill('#username', `qa ${runLabel}`);
    await g.page.fill('#email', `qa-${runLabel}@aloute.test`);
    await g.page.fill('#password', 'Qa@12345678');
    await g.page.fill('#confirmPassword', 'Qa@12345679');
    await g.page.click('#register-submit-btn');
    await g.page.waitForTimeout(300);
    assert(await g.page.locator('#confirmPassword.is-invalid').count() === 1, 'Không báo lỗi xác nhận mật khẩu');
    await g.page.fill('#email', process.env.ALOUTE_USER_EMAIL || '');
    await g.page.fill('#confirmPassword', 'Qa@12345678');
    await g.page.click('#register-submit-btn');
    const text = await statusText(g.page);
    assert(/tồn tại|đã được sử dụng|đã tồn tại/i.test(text), `Email trùng phải báo lỗi: ${text}`);
    assertEqual(diag.pageErrors.length, 0, `Page error: ${diag.pageErrors.join(' | ')}`);
    await screenshot(g.page, 'auth-17-register-existing');
    await g.context.close();
    return { message: text.slice(0, 80) };
  });

  await test(results, S, 'AUTH-18', 'Đăng ký mới chuyển /verify-otp; OTP sai bị từ chối', async () => {
    const g = await guest(browser);
    const email = `qa.reg.${runLabel}@aloute.test`;
    globalThis.__qaRegEmail = email;
    await open(g, '/register');
    await g.page.fill('#username', `qa${runLabel}`);
    await g.page.fill('#email', email);
    await g.page.fill('#password', 'Qa@12345678');
    await g.page.fill('#confirmPassword', 'Qa@12345678');
    await g.page.click('#register-submit-btn');
    await g.page.waitForURL('**/verify-otp**', { timeout: 15000 });
    const display = await visibleText(g.page, '#display-email');
    assert(display && display.includes('***') && display.includes('@aloute.test'), `Trang OTP phải hiển thị email đã che: ${display}`);
    await g.page.fill('#otp', '000000');
    await g.page.click('#verify-submit-btn');
    const text = await statusText(g.page);
    assert(/không hợp lệ|hết hạn/i.test(text), `OTP sai phải báo lỗi: ${text}`);
    assert(g.page.url().includes('/verify-otp'), 'OTP sai không được chuyển trang');
    await screenshot(g.page, 'auth-18-otp-invalid');
    await g.context.close();
    return { emailDomain: email.split('@')[1], otpRejected: true };
  });

  await test(results, S, 'AUTH-19', 'Resend OTP bị giới hạn tần suất (cooldown 60s)', async () => {
    const g = await guest(browser);
    const email = `qa.resend.${runLabel}@aloute.test`;
    await open(g, '/register');
    await g.page.fill('#username', `resend${runLabel}`);
    await g.page.fill('#email', email);
    await g.page.fill('#password', 'Qa@12345678');
    await g.page.fill('#confirmPassword', 'Qa@12345678');
    await g.page.click('#register-submit-btn');
    await g.page.waitForURL('**/verify-otp**', { timeout: 15000 });
    await g.page.click('#resend-otp-btn');
    const first = await statusText(g.page);
    assert(/đã được xử lý|xử lý/i.test(first), `Resend đầu tiên phải được xử lý: ${first}`);
    await g.page.waitForTimeout(300);
    const disabled = await g.page.locator('#resend-otp-btn').isDisabled();
    const countdown = await g.page.locator('#resend-otp-btn').textContent();
    assertEqual(disabled, true, 'Nút resend phải bị khóa trong cooldown');
    const xsrf = (await g.context.cookies(BASE)).find(c => c.name === 'XSRF-TOKEN')?.value;
    const statuses = [];
    for (let i = 0; i < 10; i += 1) {
      const direct = await api({ context: g.context }, 'POST', '/api/auth/resend-registration-otp', { email }, {
        headers: xsrf ? { 'X-XSRF-TOKEN': xsrf } : {}
      });
      statuses.push(direct.status);
    }
    assert(statuses.includes(429), `Chuỗi resend phải chạm 429: ${statuses.join(',')}`);
    await screenshot(g.page, 'auth-19-resend');
    await g.context.close();
    return { firstMessage: first.slice(0, 120), countdown: (countdown || '').trim(), resendStatuses: statuses };
  });

  await test(results, S, 'AUTH-20', 'Quên mật khẩu trả thông báo generic cho email tồn tại và không tồn tại', async () => {
    const g = await guest(browser);
    const knownEmail = globalThis.__qaRegEmail || process.env.ALOUTE_USER_EMAIL || '';
    await open(g, '/forgot-password');
    await g.page.fill('#email', knownEmail);
    await g.page.click('#forgot-submit-btn');
    const known = await statusText(g.page);
    await open(g, '/forgot-password');
    await g.page.fill('#email', `khong-ton-tai-${runLabel}@aloute.test`);
    await g.page.click('#forgot-submit-btn');
    const unknown = await statusText(g.page);
    assert(known.length > 10 && unknown.length > 10, 'Thiếu phản hồi quên mật khẩu');
    const norm = t => t.replace(/\s+/g, ' ').trim();
    assertEqual(norm(known), norm(unknown), 'Thông báo phải giống nhau để chống enumeration');
    assert(/nếu|đã gửi|kiểm tra|email/i.test(known), `Thông báo không đúng: ${known}`);
    await screenshot(g.page, 'auth-20-forgot');
    await g.context.close();
    return { message: norm(known).slice(0, 140) };
  });

  await test(results, S, 'AUTH-21', 'Reset password thiếu token bị chặn ngay trên UI', async () => {
    const g = await guest(browser);
    await open(g, '/reset-password');
    const warning = await visibleText(g.page, '#auth-status');
    assert(warning && /không hợp lệ|thiếu/i.test(warning), `Không có cảnh báo token: ${warning}`);
    const disabled = await g.page.locator('#reset-submit-btn').isDisabled();
    assertEqual(disabled, true, 'Nút reset phải bị vô hiệu hóa khi thiếu token');
    await g.context.close();
    return { warning: warning.slice(0, 100) };
  });

  await test(results, S, 'AUTH-22', 'JS tắt: trang login render, điều hướng /register và /feed hoạt động', async () => {
    const context = await browser.newContext({
      baseURL: BASE, javaScriptEnabled: false, viewport: { width: 1280, height: 800 }, locale: 'vi-VN'
    });
    const page = await context.newPage();
    const res = await page.goto(`${BASE}/login`, { waitUntil: 'domcontentloaded' });
    assertEqual(res.status(), 200, 'HTTP /login (JS off)');
    assert(await page.locator('#login-form').count() === 1, 'Form login phải render không cần JS');
    await page.click('a[href="/register"]');
    await page.waitForURL('**/register');
    assert(await page.locator('#register-form').count() === 1, 'Form register phải render không cần JS');
    await page.goto(`${BASE}/feed`, { waitUntil: 'domcontentloaded' });
    assert(await page.locator('article.post-card').count() > 0, 'Feed SSR phải có bài viết khi JS tắt');
    const guestCta = await page.locator('section[aria-label="Đăng nhập để đăng bài"]').count();
    assert(guestCta === 1, 'Guest CTA phải render không cần JS');
    await context.close();
    return { jsOffNavigation: true, feedSsr: true };
  });

  await test(results, S, 'AUTH-23', 'JS tắt: submit form login không đưa mật khẩu vào URL', async () => {
    const context = await browser.newContext({
      baseURL: BASE, javaScriptEnabled: false, viewport: { width: 1280, height: 800 }, locale: 'vi-VN'
    });
    const page = await context.newPage();
    await page.goto(`${BASE}/login`, { waitUntil: 'domcontentloaded' });
    const method = await page.locator('#login-form').getAttribute('method');
    const action = await page.locator('#login-form').getAttribute('action');
    const formHasGetFallback = method === null || method.toLowerCase() === 'get';
    if (formHasGetFallback) {
      await page.fill('#email', 'qa-nojs@aloute.test');
      await page.fill('#password', 'NotARealPassword123');
      await Promise.all([
        page.waitForLoadState('domcontentloaded').catch(() => null),
        page.click('#login-submit-btn').catch(() => null)
      ]);
      await page.waitForTimeout(500);
      const urlHasPassword = page.url().toLowerCase().includes('password=');
      await context.close();
      if (urlHasPassword) {
        throw new Error('Form login không có method/action: khi JS tắt, mật khẩu bị gửi qua query string');
      }
      return { method, action, urlHasPassword };
    }
    await context.close();
    return { method, action };
  });

  await test(results, S, 'AUTH-24', 'CSRF: POST cookie-auth thiếu X-XSRF-TOKEN bị từ chối', async () => {
    const session = await login(browser, 'user');
    const noCsrf = await api({ context: session.context }, 'POST', '/api/posts', {
      content: `csrf probe ${runLabel}`, visibility: 'PUBLIC'
    });
    assert(noCsrf.status === 403 || noCsrf.status === 401, `Thiếu CSRF phải 403/401, nhận ${noCsrf.status}`);
    const token = (await session.context.cookies(BASE)).find(c => c.name === 'XSRF-TOKEN')?.value;
    assert(token, 'Thiếu cookie XSRF-TOKEN sau khi mở trang');
    const withCsrf = await api({ context: session.context }, 'POST', '/api/posts', {
      content: `csrf probe ok ${runLabel}`, visibility: 'PUBLIC'
    }, { headers: { 'X-XSRF-TOKEN': token } });
    assertEqual(withCsrf.status, 201, 'POST có CSRF phải 201');
    await api({ context: session.context }, 'DELETE', `/api/posts/${withCsrf.body?.data?.id}`, null, {
      headers: { 'X-XSRF-TOKEN': token }
    });
    await session.context.close();
    return { withoutCsrf: noCsrf.status, withCsrf: withCsrf.status };
  });

  await test(results, S, 'AUTH-25', 'Không rò rỉ token/OTP trong HTML công khai', async () => {
    const g = await guest(browser);
    await open(g, '/login');
    const html = await g.page.content();
    const patterns = [/eyJ[a-zA-Z0-9_-]{10,}\./, /otpHash/i, /twoFactorTicket/i, /serviceAccountKey/i];
    const hits = patterns.filter(p => p.test(html)).map(p => String(p));
    assertEqual(hits.length, 0, `HTML login chứa dữ liệu nhạy cảm: ${hits.join(', ')}`);
    await g.context.close();
  });

  await test(results, S, 'AUTH-26', '2FA: tài khoản seed đăng nhập trực tiếp, không lộ section 2FA', async () => {
    const g = await guest(browser);
    await open(g, '/login');
    const hidden = await g.page.locator('#login-2fa-section').evaluate(el => el.classList.contains('d-none'));
    assertEqual(hidden, true, 'Section 2FA phải ẩn mặc định');
    notRun(results, S, 'AUTH-26b', 'Thiết lập và xác thực 2FA đầy đủ', 'Tài khoản seed không bật 2FA; endpoint setup 2FA không có trong OpenAPI canonical');
    await g.context.close();
    return { defaultHidden: true };
  });
}
