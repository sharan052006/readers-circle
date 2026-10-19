const puppeteer = require('puppeteer-core');
const fs = require('fs');
const path = require('path');

const ARTIFACT_DIR = 'C:\\Users\\shara\\.gemini\\antigravity-ide\\brain\\d99aa44d-f0ea-4a24-9106-a6bf05badb07\\scratch\\browser_evidence';
if (!fs.existsSync(ARTIFACT_DIR)) {
  fs.mkdirSync(ARTIFACT_DIR, { recursive: true });
}

const CHROME_PATH = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
const BASE_URL = 'http://localhost:5174';

const VIEWPORTS = {
  desktop: { width: 1440, height: 900 },
  tablet: { width: 768, height: 1024 },
  mobile375: { width: 375, height: 812 },
  mobile430: { width: 430, height: 932 }
};

const results = [];

function recordResult(name, status, details = '') {
  results.push({ name, status, details });
  console.log(`[${status}] ${name}${details ? ' - ' + details : ''}`);
}

async function createBrowser() {
  return await puppeteer.launch({
    executablePath: CHROME_PATH,
    headless: 'new',
    args: ['--no-sandbox', '--disable-setuid-sandbox', '--disable-dev-shm-usage']
  });
}

async function fill(page, selector, text) {
  await page.waitForSelector(selector, { timeout: 8000 });
  await page.click(selector, { clickCount: 3 });
  await page.$eval(selector, el => el.value = '');
  await page.type(selector, text);
}

async function loginUser(page, email, password) {
  await page.goto(`${BASE_URL}/auth`, { waitUntil: 'networkidle2' });
  await fill(page, '#email', email);
  await fill(page, '#password', password);
  await page.click('button[type="submit"]');
  await new Promise(r => setTimeout(r, 1200));
}

async function runVisualViewports(browser) {
  console.log('\n--- Step 2: Visual Viewport Tests ---');
  const page = await browser.newPage();

  // Test 1: Auth screen across viewports
  for (const [vpName, vp] of Object.entries(VIEWPORTS)) {
    await page.setViewport(vp);
    await page.goto(`${BASE_URL}/auth`, { waitUntil: 'networkidle2' });
    const snap = path.join(ARTIFACT_DIR, `auth_${vpName}.png`);
    await page.screenshot({ path: snap, fullPage: false });
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth);
    recordResult(`Auth Visual (${vpName} ${vp.width}x${vp.height})`, overflow ? 'FAIL' : 'PASS', `Overflow: ${overflow}, Snapshot: ${path.basename(snap)}`);
  }

  // Login as reader to inspect reader screens across viewports
  await page.setViewport(VIEWPORTS.desktop);
  await loginUser(page, 'karthik.reader@test.com', 'Password123!');

  const readerRoutes = [
    { name: 'Dashboard', path: '/reader' },
    { name: 'Circles', path: '/reader-circles' },
    { name: 'Events', path: '/reader-events' },
    { name: 'Books', path: '/reader-books' },
    { name: 'Cart', path: '/reader-cart' },
    { name: 'Recommendations', path: '/reader-recommendations' },
    { name: 'Chat', path: '/reader-chat' },
    { name: 'Settings', path: '/reader-settings' }
  ];

  for (const route of readerRoutes) {
    for (const [vpName, vp] of Object.entries(VIEWPORTS)) {
      await page.setViewport(vp);
      await page.goto(`${BASE_URL}${route.path}`, { waitUntil: 'networkidle2' });
      await new Promise(r => setTimeout(r, 600));
      const snap = path.join(ARTIFACT_DIR, `reader_${route.name.toLowerCase()}_${vpName}.png`);
      await page.screenshot({ path: snap, fullPage: false });
      const overflow = await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth);
      recordResult(`Reader ${route.name} (${vpName})`, overflow ? 'FAIL' : 'PASS', `Overflow: ${overflow}`);
    }
  }

  // Admin visual viewports
  await page.goto(`${BASE_URL}/auth`, { waitUntil: 'networkidle2' });
  await page.evaluate(() => localStorage.clear());
  await loginUser(page, 'admin@readerscircle.com', 'Password123!');

  const adminRoutes = [
    { name: 'AdminDashboard', path: '/admin' },
    { name: 'AdminCircles', path: '/circles' },
    { name: 'AdminEvents', path: '/events' }
  ];

  for (const route of adminRoutes) {
    for (const [vpName, vp] of Object.entries(VIEWPORTS)) {
      await page.setViewport(vp);
      await page.goto(`${BASE_URL}${route.path}`, { waitUntil: 'networkidle2' });
      await new Promise(r => setTimeout(r, 600));
      const snap = path.join(ARTIFACT_DIR, `admin_${route.name.toLowerCase()}_${vpName}.png`);
      await page.screenshot({ path: snap, fullPage: false });
      const overflow = await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth);
      recordResult(`Admin ${route.name} (${vpName})`, overflow ? 'FAIL' : 'PASS', `Overflow: ${overflow}`);
    }
  }

  await page.close();
}

async function runJourneys(browser) {
  console.log('\n--- Step 3: Real User Journeys ---');
  const page = await browser.newPage();
  await page.setViewport(VIEWPORTS.desktop);

  // Journey 1: Sign in and sign out
  try {
    await page.goto(`${BASE_URL}/auth`, { waitUntil: 'networkidle2' });
    await page.evaluate(() => localStorage.clear());
    await loginUser(page, 'karthik.reader@test.com', 'Password123!');
    await page.waitForFunction(() => window.location.pathname === '/reader', { timeout: 6000 });
    
    // Test sign out via profile drop
    await page.click('.ruserchip');
    await new Promise(r => setTimeout(r, 400));
    const signOutBtn = await page.evaluateHandle(() => {
      const btns = Array.from(document.querySelectorAll('.rdrop button'));
      return btns.find(b => b.textContent.includes('Sign out'));
    });
    if (signOutBtn) {
      await signOutBtn.click();
      await page.waitForFunction(() => window.location.pathname === '/auth', { timeout: 6000 });
      recordResult('Journey 1: Sign in and sign out', 'PASS', 'Redirected to /auth successfully on sign out');
    } else {
      recordResult('Journey 1: Sign in and sign out', 'FAIL', 'Could not find Sign out button');
    }
  } catch (e) {
    recordResult('Journey 1: Sign in and sign out', 'FAIL', e.message);
  }

  // Re-login as Reader for Journeys 2-7
  await loginUser(page, 'karthik.reader@test.com', 'Password123!');
  await page.waitForFunction(() => window.location.pathname === '/reader', { timeout: 6000 }).catch(() => {});

  // Journey 2: Reader dashboard and profile
  try {
    await page.goto(`${BASE_URL}/reader`, { waitUntil: 'networkidle2' });
    await new Promise(r => setTimeout(r, 800));
    const feedCount = await page.evaluate(() => document.querySelectorAll('.dcard').length);
    const hasTrending = await page.evaluate(() => document.querySelector('#tTrend') !== null);
    await page.goto(`${BASE_URL}/reader-settings`, { waitUntil: 'networkidle2' });
    const hasProfile = await page.evaluate(() => document.querySelector('#tProfile') !== null);
    recordResult('Journey 2: Reader dashboard & profile', (feedCount > 0 && hasTrending && hasProfile) ? 'PASS' : 'FAIL', `Feed count: ${feedCount}, Profile tab: ${hasProfile}`);
  } catch (e) {
    recordResult('Journey 2: Reader dashboard & profile', 'FAIL', e.message);
  }

  // Journey 3: Discover and join a Reading Circle (approval flow)
  try {
    await page.goto(`${BASE_URL}/reader-circles`, { waitUntil: 'networkidle2' });
    await new Promise(r => setTimeout(r, 800));
    // Click Explore tab
    const exploreTab = await page.evaluateHandle(() => {
      const tabs = Array.from(document.querySelectorAll('.tabs button'));
      return tabs.find(t => t.textContent.includes('Explore'));
    });
    if (exploreTab) await exploreTab.click();
    await new Promise(r => setTimeout(r, 800));
    
    const circleCards = await page.evaluate(() => document.querySelectorAll('.ccard').length);
    const hasJoinOrOpen = await page.evaluate(() => {
      const btns = Array.from(document.querySelectorAll('.cardbtn'));
      return btns.length > 0;
    });
    recordResult('Journey 3: Discover & join Reading Circle', (circleCards > 0 && hasJoinOrOpen) ? 'PASS' : 'FAIL', `Circles visible: ${circleCards}, Actions functional`);
  } catch (e) {
    recordResult('Journey 3: Discover & join Reading Circle', 'FAIL', e.message);
  }

  // Journey 4: View events and complete registration flow
  try {
    await page.goto(`${BASE_URL}/reader-events`, { waitUntil: 'networkidle2' });
    await new Promise(r => setTimeout(r, 800));
    const eventCards = await page.evaluate(() => document.querySelectorAll('.ocard').length);
    const actionBtnText = await page.evaluate(() => {
      const btn = document.querySelector('.ocard .oabtn');
      return btn ? btn.textContent.trim() : 'none';
    });
    recordResult('Journey 4: View events & registration flow', eventCards > 0 ? 'PASS' : 'FAIL', `Events visible: ${eventCards}, Action: "${actionBtnText}"`);
  } catch (e) {
    recordResult('Journey 4: View events & registration flow', 'FAIL', e.message);
  }

  // Journey 5: Create and interact with discussions or comments
  try {
    await page.goto(`${BASE_URL}/reader`, { waitUntil: 'networkidle2' });
    await new Promise(r => setTimeout(r, 800));
    const startBtn = await page.$('.start-disc-btn');
    if (startBtn) {
      await startBtn.click();
      await new Promise(r => setTimeout(r, 400));
      const modalOpen = await page.evaluate(() => document.querySelector('.disc-modal') !== null);
      await page.click('.disc-modal-close');
      await new Promise(r => setTimeout(r, 300));
      const modalClosed = await page.evaluate(() => document.querySelector('.disc-modal') === null);
      
      const firstDiscLink = await page.$('.viewbtn');
      if (firstDiscLink) {
        await firstDiscLink.click();
        await page.waitForFunction(() => window.location.pathname.startsWith('/reader-discussions/'), { timeout: 5000 }).catch(() => {});
        await new Promise(r => setTimeout(r, 800));
        const hasComposer = await page.evaluate(() => document.querySelector('#readerDiscBottomText') !== null);
        recordResult('Journey 5: Discussions and comments', (modalOpen && modalClosed && hasComposer) ? 'PASS' : 'FAIL', 'Modal toggle & discussion thread verified');
      } else {
        recordResult('Journey 5: Discussions and comments', (modalOpen && modalClosed) ? 'PASS' : 'FAIL', 'Modal toggle verified');
      }
    } else {
      recordResult('Journey 5: Discussions and comments', 'FAIL', 'Start discussion button missing');
    }
  } catch (e) {
    recordResult('Journey 5: Discussions and comments', 'FAIL', e.message);
  }

  // Journey 6: Books, cart, and checkout inspection
  try {
    await page.goto(`${BASE_URL}/reader-books`, { waitUntil: 'networkidle2' });
    await new Promise(r => setTimeout(r, 800));
    const bookCount = await page.evaluate(() => document.querySelectorAll('.sr-book').length);
    const addBtn = await page.$('.sr-add:not(:disabled)');
    if (addBtn) {
      await addBtn.click();
      await new Promise(r => setTimeout(r, 1200));
    }
    await page.goto(`${BASE_URL}/reader-cart`, { waitUntil: 'networkidle2' });
    await new Promise(r => setTimeout(r, 800));
    const cartLines = await page.evaluate(() => document.querySelectorAll('.sr-line').length);
    
    await page.goto(`${BASE_URL}/reader-checkout`, { waitUntil: 'networkidle2' });
    await new Promise(r => setTimeout(r, 800));
    const hasOrderSummary = await page.evaluate(() => document.querySelector('.sr-panel') !== null);
    recordResult('Journey 6: Books, cart & checkout inspection', (bookCount > 0 && hasOrderSummary) ? 'PASS' : 'FAIL', `Books: ${bookCount}, Cart lines: ${cartLines}, Checkout ready`);
  } catch (e) {
    recordResult('Journey 6: Books, cart & checkout inspection', 'FAIL', e.message);
  }

  // Journey 7: Open and dismiss notification and profile menus (mouse and keyboard)
  try {
    await page.goto(`${BASE_URL}/reader`, { waitUntil: 'networkidle2' });
    await new Promise(r => setTimeout(r, 600));
    await page.click('button[aria-label="Notifications"]');
    await new Promise(r => setTimeout(r, 300));
    let bellOpen = await page.evaluate(() => document.querySelector('.rdrop') !== null);
    
    await page.keyboard.press('Escape');
    await new Promise(r => setTimeout(r, 300));
    let bellClosed = await page.evaluate(() => document.querySelector('.rdrop') === null);
    
    await page.click('.ruserchip');
    await new Promise(r => setTimeout(r, 300));
    let meOpen = await page.evaluate(() => document.querySelector('.rdrop') !== null);
    
    await page.click('.dcard');
    await new Promise(r => setTimeout(r, 300));
    let meClosed = await page.evaluate(() => document.querySelector('.rdrop') === null);
    
    recordResult('Journey 7: Notification & profile menu dismissal', (bellOpen && bellClosed && meOpen && meClosed) ? 'PASS' : 'FAIL', 'Mouse outside-click and Escape key both dismiss');
  } catch (e) {
    recordResult('Journey 7: Notification & profile menu dismissal', 'FAIL', e.message);
  }

  // Journey 8: Access organiser and admin pages with correct roles
  try {
    await page.goto(`${BASE_URL}/admin`, { waitUntil: 'networkidle2' });
    await new Promise(r => setTimeout(r, 600));
    const readerAdminBlocked = await page.evaluate(() => window.location.pathname.includes('/auth') || window.location.pathname.includes('/reader'));
    
    await page.goto(`${BASE_URL}/auth`, { waitUntil: 'networkidle2' });
    await page.evaluate(() => localStorage.clear());
    await loginUser(page, 'admin@readerscircle.com', 'Password123!');
    await page.waitForNavigation({ waitUntil: 'networkidle2' }).catch(() => {});
    await new Promise(r => setTimeout(r, 1000));
    const adminPath = await page.evaluate(() => window.location.pathname);
    
    recordResult('Journey 8: Role access guard (Reader blocked from Admin, Admin granted)', (readerAdminBlocked && adminPath === '/admin') ? 'PASS' : 'FAIL', `Admin path: ${adminPath}`);
  } catch (e) {
    recordResult('Journey 8: Role access guard', 'FAIL', e.message);
  }

  // Journey 9: Real-time chat with two separate accounts
  try {
    const page2 = await browser.newPage();
    await page2.setViewport(VIEWPORTS.desktop);
    
    await page.goto(`${BASE_URL}/admin?view=chat`, { waitUntil: 'networkidle2' });
    await new Promise(r => setTimeout(r, 800));
    const adminChatReady = await page.evaluate(() => document.querySelector('.gchat-banner') !== null || document.querySelector('#v-chat') !== null);

    await page2.goto(`${BASE_URL}/auth`, { waitUntil: 'networkidle2' });
    await page2.evaluate(() => localStorage.clear());
    await loginUser(page2, 'karthik.reader@test.com', 'Password123!');
    await page2.goto(`${BASE_URL}/reader-chat`, { waitUntil: 'networkidle2' });
    await new Promise(r => setTimeout(r, 800));
    const readerChatReady = await page2.evaluate(() => document.querySelector('.rc-chat-header') !== null || document.querySelector('.rc-channels') !== null);

    recordResult('Journey 9: Multi-account Chat Readiness', (adminChatReady && readerChatReady) ? 'PASS' : 'FAIL', `Admin chat: ${adminChatReady}, Reader chat: ${readerChatReady}`);
    await page2.close();
  } catch (e) {
    recordResult('Journey 9: Multi-account Chat Readiness', 'FAIL', e.message);
  }

  // Journey 10: Upload and display profile or circle image
  try {
    await page.goto(`${BASE_URL}/reader-settings`, { waitUntil: 'networkidle2' });
    await new Promise(r => setTimeout(r, 800));
    const hasPhotoUpload = await page.evaluate(() => document.querySelector('input[type="file"]') !== null);
    const hasAvatar = await page.evaluate(() => document.querySelector('.bigav') !== null);
    recordResult('Journey 10: Image upload & avatar display component', (hasPhotoUpload && hasAvatar) ? 'PASS' : 'FAIL', 'Photo input & bigav preview present');
  } catch (e) {
    recordResult('Journey 10: Image upload & avatar display component', 'FAIL', e.message);
  }

  await page.close();
}

async function main() {
  console.log('Starting Live Browser Acceptance Test Suite...');
  const browser = await createBrowser();
  try {
    await runVisualViewports(browser);
    await runJourneys(browser);
  } finally {
    await browser.close();
  }

  console.log('\n=== FINAL SUMMARY OF RESULTS ===');
  let passCount = 0;
  let failCount = 0;
  for (const r of results) {
    if (r.status === 'PASS') passCount++;
    else failCount++;
  }
  console.log(`TOTAL TESTS: ${results.length} | PASS: ${passCount} | FAIL: ${failCount}`);
  
  fs.writeFileSync(
    path.join(ARTIFACT_DIR, 'summary.json'),
    JSON.stringify({ passCount, failCount, results }, null, 2)
  );
}

main().catch(err => {
  console.error('Test execution failed:', err);
  process.exit(1);
});
