import { firefox } from 'playwright';
for (const opts of [
  { headless: false },
  { headless: true, firefoxUserPrefs: { 'fission.autostart': false, 'browser.tabs.remote.autostart': false } }
]) {
  try {
    const b = await firefox.launch(opts);
    const ctx = await b.newContext();
    const page = await ctx.newPage();
    const res = await page.goto('http://127.0.0.1:8082/login', { waitUntil: 'domcontentloaded', timeout: 20000 });
    console.log(JSON.stringify(opts), 'OK', res.status(), await page.title());
    await b.close();
  } catch (e) {
    console.log(JSON.stringify(opts), 'FAIL', e.message.split('\n')[0]);
  }
}
