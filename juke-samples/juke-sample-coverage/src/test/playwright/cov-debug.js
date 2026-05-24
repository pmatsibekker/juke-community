const { chromium } = require('@playwright/test');
(async () => {
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage();
  const msgs = [];
  page.on('console', m => msgs.push(`[${m.type()}] ${m.text()}`));
  page.on('pageerror', e => msgs.push(`[pageerror] ${e.message}`));
  
  await page.goto('http://localhost:8080/');
  await page.waitForTimeout(3000);
  
  const r = await page.evaluate(() => ({
    typeofCov: typeof window.__coverage__,
    keys: window.__coverage__ ? Object.keys(window.__coverage__).length : -1,
    first: window.__coverage__ ? Object.keys(window.__coverage__)[0] : null,
    hasRoot: !!document.querySelector('#root > *'),
    titleText: document.title,
  }));
  console.log('Result:', JSON.stringify(r, null, 2));
  console.log('Console messages:');
  msgs.forEach(m => console.log(' ', m));
  await browser.close();
})().catch(e => { console.error(e.message); process.exit(1); });
