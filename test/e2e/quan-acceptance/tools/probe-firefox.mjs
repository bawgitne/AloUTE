import { firefox } from 'playwright';
const b = await firefox.launch({ headless: true });
console.log('version', b.version());
for (const opts of [
  {},
  { viewport: { width: 1440, height: 900 } },
  { viewport: { width: 1440, height: 900 }, locale: 'vi-VN', timezoneId: 'Asia/Ho_Chi_Minh', baseURL: 'http://127.0.0.1:8082' }
]) {
  try {
    const ctx = await b.newContext(opts);
    const page = await ctx.newPage();
    const res = await page.goto('http://127.0.0.1:8082/login', { waitUntil: 'domcontentloaded', timeout: 20000 });
    console.log('opts', JSON.stringify(opts), 'status', res.status(), 'title', await page.title());
    await ctx.close();
  } catch (e) {
    console.log('opts', JSON.stringify(opts), 'FAIL', e.message.split('\n')[0]);
  }
}
await b.close();
