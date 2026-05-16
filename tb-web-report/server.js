import express from 'express';
import puppeteer from 'puppeteer-core';

const PORT = parseInt(process.env.HTTP_BIND_PORT || '8383', 10);
const BIND = process.env.HTTP_BIND_ADDRESS || '0.0.0.0';
const NAV_TIMEOUT = parseInt(process.env.DEFAULT_PAGE_NAVIGATION_TIMEOUT || '120000', 10);
const IDLE_WAIT = parseInt(process.env.DASHBOARD_IDLE_WAIT_TIME || '3000', 10);
const USE_NEW_PAGE = (process.env.USE_NEW_PAGE_FOR_REPORT || 'true').toLowerCase() === 'true';
const CHROME_EXE = process.env.PUPPETEER_EXECUTABLE_PATH || '/usr/bin/chromium';
const LOG_LEVEL = (process.env.LOGGER_LEVEL || 'info').toLowerCase();

const log = (lvl, ...args) => {
  if (LOG_LEVEL === 'debug' || lvl !== 'debug') console.log(`[${new Date().toISOString()}] [${lvl}]`, ...args);
};

const app = express();
app.use(express.json({ limit: '4mb' }));

let browserPromise;
async function getBrowser() {
  if (!browserPromise) {
    log('info', `Launching Chromium at ${CHROME_EXE}`);
    browserPromise = puppeteer.launch({
      executablePath: CHROME_EXE,
      args: ['--no-sandbox', '--disable-dev-shm-usage', '--disable-gpu', '--hide-scrollbars'],
      defaultViewport: { width: 1600, height: 1200 },
    }).catch(err => {
      browserPromise = null;
      throw err;
    });
  }
  return browserPromise;
}

app.get('/health', (req, res) => res.json({ ok: true }));

app.post('/api/generateReport', async (req, res) => {
  const { url, jwt, format = 'pdf', viewport, navigationTimeoutMs, idleWaitMs } = req.body || {};
  if (!url) return res.status(400).json({ error: 'url required' });
  const fmt = String(format).toLowerCase();
  if (fmt !== 'pdf' && fmt !== 'png') return res.status(400).json({ error: 'format must be pdf or png' });

  const browser = await getBrowser();
  const context = USE_NEW_PAGE ? await browser.createBrowserContext() : null;
  const page = await (context ?? browser).newPage();
  try {
    if (viewport && viewport.width && viewport.height) {
      await page.setViewport({ width: viewport.width, height: viewport.height });
    }
    if (jwt) {
      // Inject the access token into localStorage before the Angular app boots,
      // so the dashboard route resolves as the impersonated user.
      await page.evaluateOnNewDocument(token => {
        try { localStorage.setItem('jwt_token', token); } catch (_) {}
      }, jwt);
      // Visit the origin once so localStorage is scoped to the right host before
      // we navigate to the dashboard route.
      const origin = new URL(url).origin;
      await page.goto(origin, { waitUntil: 'domcontentloaded', timeout: navigationTimeoutMs || NAV_TIMEOUT });
    }

    log('debug', `Rendering ${fmt} ${url}`);
    await page.goto(url, { waitUntil: 'networkidle0', timeout: navigationTimeoutMs || NAV_TIMEOUT });
    await new Promise(r => setTimeout(r, idleWaitMs || IDLE_WAIT));

    let out;
    if (fmt === 'pdf') {
      out = await page.pdf({ format: 'A4', printBackground: true, preferCSSPageSize: true });
      res.setHeader('Content-Type', 'application/pdf');
    } else {
      out = await page.screenshot({ type: 'png', fullPage: true });
      res.setHeader('Content-Type', 'image/png');
    }
    res.status(200).send(out);
  } catch (err) {
    log('error', 'render failed:', err.message || err);
    res.status(500).json({ error: String(err.message || err) });
  } finally {
    try { await page.close(); } catch (_) {}
    if (context) try { await context.close(); } catch (_) {}
  }
});

const server = app.listen(PORT, BIND, () => log('info', `tb-web-report listening on ${BIND}:${PORT}`));

const shutdown = async () => {
  log('info', 'Shutdown signal received');
  server.close(() => log('info', 'HTTP server closed'));
  try { (await getBrowser()).close(); } catch (_) {}
  setTimeout(() => process.exit(0), 1000).unref();
};
process.on('SIGTERM', shutdown);
process.on('SIGINT', shutdown);
