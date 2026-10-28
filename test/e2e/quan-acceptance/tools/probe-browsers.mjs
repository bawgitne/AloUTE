import { chromium, firefox, webkit } from 'playwright';
const engines = [
  ['chrome', () => chromium.launch({ channel: 'chrome', headless: true })],
  ['edge', () => chromium.launch({ channel: 'msedge', headless: true })],
  ['firefox', () => firefox.launch({ headless: true })],
  ['webkit', () => webkit.launch({ headless: true })]
];
for (const [name, launch] of engines) {
  try {
    const b = await launch();
    console.log(name, 'OK', b.version());
    await b.close();
  } catch (e) {
    console.log(name, 'FAIL', e.message.split('\n')[0]);
  }
}
