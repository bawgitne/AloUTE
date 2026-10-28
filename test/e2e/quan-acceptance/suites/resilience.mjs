import {
  BASE, test, assert, assertEqual, login, guest, open, api, uniqueSuffix, screenshot
} from '../lib/harness.mjs';

const S = 'resilience';

async function apiWrite(session, method, route, data) {
  const token = (await session.context.cookies(BASE)).find(c => c.name === 'XSRF-TOKEN')?.value;
  return api(session, method, route, data, { headers: token ? { 'X-XSRF-TOKEN': token } : {} });
}

async function loadConversation(page) {
  await page.locator('#conversations-list .aloute-conversation-item').first().waitFor({ state: 'visible', timeout: 12000 });
  await page.locator('#conversations-list .aloute-conversation-item').first().click();
  await page.locator('#chat-active-container').waitFor({ state: 'visible', timeout: 12000 });
  await page.locator('#chat-history .aloute-message-bubble').first().waitFor({ state: 'visible', timeout: 12000 });
}

export async function run({ browser, results }) {
  const tag = uniqueSuffix();
  const user = await login(browser, 'user');
  const an = await login(browser, 'demoan');
  const g = await guest(browser);

  /* ---------------- Guest gate & error pages ---------------- */

  await test(results, S, 'RES-01', 'Guest vào feed thấy CTA và được đưa tới trang đăng nhập', async () => {
    const res = await open(g, '/feed', { wait: 600 });
    assertEqual(res.status(), 200, 'HTTP /feed guest');
    const cta = g.page.locator('section[aria-label="Đăng nhập để đăng bài"]');
    await cta.waitFor({ state: 'visible', timeout: 10000 });
    await cta.locator('a.aloute-auth-cta', { hasText: 'Đăng nhập' }).click();
    await g.page.waitForURL('**/login', { timeout: 10000 });
    await g.page.locator('#login-form').waitFor({ state: 'visible', timeout: 10000 });
    await screenshot(g.page, 'res-01-guest-login-gate');
    return { from: '/feed', to: '/login' };
  });

  await test(results, S, 'RES-02', 'Route không tồn tại (đã đăng nhập) trả trang 404 HTML có lối về feed', async () => {
    const res = await open(user, `/khong-ton-tai-${tag}`, { wait: 400 });
    assertEqual(res.status(), 404, 'HTTP route không tồn tại');
    const contentType = String(res.headers()['content-type'] || '');
    assert(contentType.includes('text/html'), `404 phải là HTML, nhận ${contentType}`);
    const heading = (await user.page.locator('#error-content h1').textContent())?.trim() || '';
    assert(heading.includes('404'), `Tiêu đề 404 không đúng: ${heading}`);
    assertEqual(await user.page.locator('#error-content a[href="/feed"]').count(), 1, 'Thiếu liên kết về feed');
    await screenshot(user.page, 'res-02-404');
    return { status: 404, heading };
  });

  await test(results, S, 'RES-03', 'API tài nguyên không tồn tại trả JSON envelope 404', async () => {
    const r = await api(g, 'GET', '/api/posts/999999');
    assertEqual(r.status, 404, 'GET /api/posts/999999');
    assertEqual(r.body?.success, false, 'Envelope success=false');
    assert(r.body?.error?.code, 'Thiếu error.code');
    return { status: 404, code: r.body?.error?.code };
  });

  await test(results, S, 'RES-04', 'Phiên hết hạn: API riêng tư trả 401 JSON', async () => {
    const expiring = await login(browser, 'user');
    await expiring.context.clearCookies();
    const r = await api(expiring, 'GET', '/api/notifications');
    assertEqual(r.status, 401, 'GET notifications sau khi xóa phiên');
    assertEqual(r.body?.success, false, 'Envelope 401');
    await expiring.context.close();
    return { status: 401 };
  });

  await test(results, S, 'RES-05', 'Thiếu CSRF header: ghi dữ liệu bị từ chối 403', async () => {
    const r = await api(user, 'POST', '/api/posts', {
      content: `QA csrf ${tag}`, visibility: 'PUBLIC', allowReply: true, allowQuote: true
    });
    assertEqual(r.status, 403, 'POST bài thiếu X-XSRF-TOKEN');
    return { status: 403 };
  });

  /* ---------------- Notifications resilience ---------------- */

  await test(results, S, 'RES-06', 'Mất mạng khi tải thông báo: lỗi tiếng Việt + thử lại thành công', async () => {
    const predicate = url => url.pathname === '/api/notifications';
    const handler = route => route.abort('failed');
    await an.page.route(predicate, handler);
    await open(an, '/notifications', { wait: 800 });
    await an.page.locator('#notifications-error').waitFor({ state: 'visible', timeout: 12000 });
    const msg = (await an.page.locator('#notifications-error-msg').textContent())?.trim() || '';
    assert(/Không thể kết nối đến máy chủ/i.test(msg), `Thông báo lỗi mạng không đúng: ${msg}`);
    await screenshot(an.page, 'res-06-notifications-offline');
    await an.page.unroute(predicate, handler);
    await an.page.click('#btn-retry-notifications');
    await an.page.locator('#notifications-list .aloute-notification-item').first().waitFor({ state: 'visible', timeout: 15000 });
    return { message: msg, recovered: true };
  });

  /* ---------------- Messaging resilience ---------------- */

  await test(results, S, 'RES-07', 'Mất mạng khi tải danh sách hội thoại: lỗi tiếng Việt + thử lại', async () => {
    const predicate = url => url.pathname === '/api/conversations';
    const handler = route => route.abort('failed');
    await user.page.route(predicate, handler);
    await open(user, '/messages', { wait: 900 });
    await user.page.locator('#conversations-error').waitFor({ state: 'visible', timeout: 12000 });
    const msg = (await user.page.locator('#conversations-error-msg').textContent())?.trim() || '';
    assert(/Không thể kết nối đến máy chủ/i.test(msg), `Thông báo lỗi danh sách không đúng: ${msg}`);
    await screenshot(user.page, 'res-07-conversations-offline');
    await user.page.unroute(predicate, handler);
    await user.page.click('#btn-retry-conversations');
    await user.page.locator('#conversations-list .aloute-conversation-item').first().waitFor({ state: 'visible', timeout: 15000 });
    return { message: msg, recovered: true };
  });

  await test(results, S, 'RES-08', 'Mất mạng khi tải lịch sử tin nhắn: lỗi tiếng Việt + thử lại', async () => {
    await open(user, '/messages', { wait: 800 });
    await loadConversation(user.page);
    const predicate = url => /\/api\/conversations\/\d+\/messages$/.test(url.pathname);
    const handler = route => route.abort('failed');
    await user.page.route(predicate, handler);
    await user.page.click('#btn-refresh-messages');
    await user.page.locator('#messages-error').waitFor({ state: 'visible', timeout: 12000 });
    const msg = (await user.page.locator('#messages-error-msg').textContent())?.trim() || '';
    assert(/Không thể kết nối đến máy chủ/i.test(msg), `Thông báo lỗi lịch sử không đúng: ${msg}`);
    await screenshot(user.page, 'res-08-history-offline');
    await user.page.unroute(predicate, handler);
    await user.page.click('#btn-retry-messages');
    await user.page.locator('#chat-history .aloute-message-bubble').first().waitFor({ state: 'visible', timeout: 15000 });
    return { message: msg, recovered: true };
  });

  await test(results, S, 'RES-09', 'Gửi tin nhắn lỗi mạng: giữ nháp, báo lỗi tiếng Việt, thử lại thành công', async () => {
    const content = `QA retry send ${tag}`;
    await user.page.fill('#message-input', content);
    const predicate = url => /\/api\/conversations\/\d+\/messages$/.test(url.pathname);
    const handler = route => {
      if (route.request().method() === 'POST') return route.abort('failed');
      return route.continue();
    };
    await user.page.route(predicate, handler);
    await user.page.click('#btn-send-message');
    await user.page.locator('#send-error-bar').waitFor({ state: 'visible', timeout: 12000 });
    const msg = (await user.page.locator('#send-error-text').textContent())?.trim() || '';
    assert(/Không thể kết nối đến máy chủ/i.test(msg), `Lỗi gửi mạng không đúng: ${msg}`);
    assertEqual(await user.page.inputValue('#message-input'), content, 'Nháp phải được giữ lại sau lỗi');
    await screenshot(user.page, 'res-09-send-offline');
    await user.page.unroute(predicate, handler);
    await user.page.click('#btn-retry-send');
    await user.page.locator('#chat-history .message-text').filter({ hasText: content }).last()
      .waitFor({ state: 'visible', timeout: 15000 });
    assertEqual(await user.page.inputValue('#message-input'), '', 'Input phải xóa sau khi gửi lại thành công');
    return { message: msg, recovered: true };
  });

  await test(results, S, 'RES-10', 'Lỗi validation phía server trả envelope 422 và không tạo dữ liệu', async () => {
    const r = await apiWrite(an, 'POST', '/api/posts', {
      content: '', visibility: 'PUBLIC', allowReply: true, allowQuote: true
    });
    assertEqual(r.status, 422, 'POST bài nội dung rỗng');
    assertEqual(r.body?.success, false, 'Envelope validation');
    assert(r.body?.error?.code, 'Thiếu error.code validation');
    return { status: 422, code: r.body?.error?.code };
  });

  await test(results, S, 'RES-11', 'API logout thu hồi phiên và chặn API sau đó', async () => {
    const session = await login(browser, 'demobinh');
    await open(session, '/feed');
    const logout = await apiWrite(session, 'POST', '/api/auth/logout');
    assert([200, 204].includes(logout.status), `Logout HTTP ${logout.status}`);
    const me = await api(session, 'GET', '/api/auth/me');
    assertEqual(me.status, 401, 'API me sau logout');
    await session.context.close();
    return { logout: logout.status, me: me.status };
  });

  await screenshot(an.page, 'res-final-notifications');
}
