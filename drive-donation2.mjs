import { chromium } from 'playwright';
const shotsDir = 'C:\\Users\\Nandini\\AppData\\Local\\Temp\\claude\\d--backend\\40da47c2-3983-4d9c-ab17-d4c211485084\\scratchpad\\shots';

const browser = await chromium.launch({ args: ['--no-sandbox'] });
const page = await browser.newPage();

const email = `donortest2${Date.now()}@example.com`;
await page.goto('http://localhost:5173/register');
await page.fill('input[name="fullName"]', 'Donor Test Two');
await page.fill('input[name="email"]', email);
await page.fill('input[name="password"]', 'Password123');
await page.fill('input[name="confirmPassword"]', 'Password123');
await page.click('button:has-text("Create account")');
await page.waitForSelector('text=Welcome Back', { timeout: 10000 });
await page.fill('input[name="email"]', email);
await page.fill('input[name="password"]', 'Password123');
await page.click('button:has-text("Log In")');
await page.waitForURL('**/dashboard', { timeout: 10000 });

await page.goto('http://localhost:5173/donate');
await page.waitForSelector('.ngo-card', { timeout: 10000 });
await page.locator('.ngo-card').first().click();
await page.click('button:has-text("Continue")');
await page.waitForSelector('text=Select Donation Amount');
await page.click('button.amount-chip:has-text("₹100")');
await page.click('button:has-text("Continue to Payment")');
await page.waitForSelector('text=Review & Pay');
await page.click('button:has-text("Pay ₹100")');

await page.waitForTimeout(6000);
await page.screenshot({ path: shotsDir + '\\donate-03-loaded.png' });

const rzpFrame = page.frames().find(f => f.url().includes('api.razorpay.com/v1/checkout/public'));
if (rzpFrame) {
  console.log('Found razorpay frame, dumping visible text...');
  try {
    const bodyText = await rzpFrame.locator('body').innerText({ timeout: 5000 });
    console.log('FRAME TEXT:', bodyText.slice(0, 1000));
  } catch (e) {
    console.log('Could not read frame text:', e.message);
  }
} else {
  console.log('No razorpay checkout frame found. Frames:', page.frames().map(f => f.url()));
}

await browser.close();
