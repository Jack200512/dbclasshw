// Run from backend/. Vite must be running; port 8080 must be free.
// Starts/stops ONLY its own direct Java child. Never manages MySQL or other processes.
const { chromium } = require(process.env.PLAYWRIGHT_MODULE_PATH || require('node:path').resolve('target/browser-check/node_modules/playwright-core'));
const assert = require('node:assert/strict');
const { spawn } = require('node:child_process');
const { once } = require('node:events');
const { mkdirSync, openSync, closeSync } = require('node:fs');
const net = require('node:net');
const crypto = require('node:crypto');
const base = 'http://127.0.0.1:5173';
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
let server, browser;

async function start() {
  // Fail if occupied, rather than touching an existing backend.
  const probe = net.createServer();
  await new Promise((resolve, reject) => probe.once('error', reject).listen(8080, resolve));
  await new Promise(resolve => probe.close(resolve));
  mkdirSync('target/browser-check', { recursive: true });
  const log = openSync('target/browser-check/lifecycle-backend.log', 'a');
  server = spawn('java', ['-jar', 'target/backend-0.1.0-SNAPSHOT.jar'], {
    windowsHide: true, stdio: ['ignore', log, log],
  });
  closeSync(log);
  server.on('error', error => { console.error('Backend launch failed:', error.message); });
  for (let i = 0; i < 120; i++) {
    if (server.exitCode !== null) throw new Error('Backend exited; inspect lifecycle-backend.log');
    try {
      const response = await fetch('http://127.0.0.1:8080/api/health', { signal: AbortSignal.timeout(500) });
      if (response.ok) return;
    } catch {}
    await sleep(500);
  }
  throw new Error('Backend startup timed out');
}
async function stop() {
  if (!server || server.exitCode !== null) return;
  const exited = once(server, 'exit');
  server.kill();
  await exited;
  server = null;
}

(async () => {
  await start();
  browser = await chromium.launch({ channel: 'msedge', headless: true });
  const context = await browser.newContext();
  const page = await context.newPage();
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  const suffix = Date.now(), name = 'accept_' + suffix;
  const password = crypto.randomBytes(18).toString('hex');
  const title = 'M2-acceptance-' + suffix;
  async function login(loginName) {
    await page.getByRole('textbox', { name: '用户名或邮箱', exact: true }).fill(loginName);
    await page.getByRole('textbox', { name: '密码', exact: true }).fill(password);
    await page.getByRole('button', { name: '登录', exact: true }).click();
    await page.getByRole('heading', { name: '个人资料', exact: true }).waitFor();
  }
  async function open(name) {
    await page.locator('.document-link').filter({ hasText: name }).click();
    await page.getByRole('heading', { name, exact: true }).waitFor();
    await page.locator('.tiptap').waitFor();
  }
  async function write(method, path, data) {
    const token = await (await context.request.get(base + '/api/auth/csrf')).json();
    return context.request.fetch(base + path, { method, headers: { [token.headerName]: token.token }, ...(data ? { data } : {}) });
  }
  await page.goto(base);
  await page.getByRole('button', { name: '去注册', exact: true }).click();
  await page.getByRole('textbox', { name: '用户名', exact: true }).fill(name);
  await page.getByRole('textbox', { name: '邮箱', exact: true }).fill(name + '@example.test');
  await page.getByRole('textbox', { name: '密码', exact: true }).fill(password);
  await page.getByRole('button', { name: '注册', exact: true }).click();
  await page.getByText('注册成功，请登录。', { exact: true }).waitFor();
  await login(name);
  assert((await context.cookies()).some(c => c.name === 'JSESSIONID' && c.httpOnly));
  assert(!(await page.evaluate(() => document.cookie)).includes('JSESSIONID'));
  await page.getByText('暂无文档，点击新建文档开始。', { exact: true }).waitFor();
  const email = name + '_changed@example.test';
  await page.getByRole('textbox', { name: '邮箱', exact: true }).fill(email);
  await page.getByRole('button', { name: '保存资料', exact: true }).click();
  await page.getByText('资料已更新。', { exact: true }).waitFor();
  await page.getByRole('button', { name: '新建文档', exact: true }).click();
  await page.getByRole('textbox', { name: '文档标题', exact: true }).fill('   ');
  assert(await page.getByRole('button', { name: '创建文档', exact: true }).isDisabled());
  await page.getByRole('textbox', { name: '文档标题', exact: true }).fill(title);
  await page.locator('.tiptap').fill('富文本 <script>literal</script>');
  await page.locator('.tiptap').press('ControlOrMeta+A');
  await page.getByRole('button', { name: '加粗', exact: true }).click();
  await page.getByRole('button', { name: '斜体', exact: true }).click();
  await page.getByRole('button', { name: '标题', exact: true }).click();
  let posts = 0;
  page.on('request', r => { if (r.method() === 'POST' && r.url().endsWith('/api/documents')) posts++; });
  const creation = page.waitForResponse(r => r.request().method() === 'POST' && r.url().endsWith('/api/documents'));
  await page.getByRole('button', { name: '创建文档', exact: true }).evaluate(button => { button.click(); button.click(); });
  const response = await creation;
  assert.equal(response.status(), 201);
  const document = await response.json();
  await page.getByText('只读查看', { exact: true }).waitFor();
  assert.equal(posts, 1);
  for (const type of ['heading', 'bold', 'italic']) assert(document.content.includes(type));
  assert.equal(await page.locator('.tiptap script').count(), 0);
  await page.getByRole('button', { name: '编辑文档', exact: true }).click();
  await page.getByRole('textbox', { name: '文档标题', exact: true }).fill(title + ' saved');
  await page.locator('.tiptap').fill('手动保存且重启后保留 😀');
  await page.route('**/api/documents/' + document.id, route => route.request().method() === 'PUT' ? route.abort('failed') : route.continue());
  await page.getByRole('button', { name: '保存文档', exact: true }).click();
  await page.getByText(/本地输入已保留/).waitFor();
  assert.equal((await page.locator('.tiptap').innerText()).trimEnd(), '手动保存且重启后保留 😀');
  await page.unroute('**/api/documents/' + document.id);
  await page.getByRole('button', { name: '保存文档', exact: true }).click();
  await page.getByText('文档保存成功。', { exact: true }).waitFor();
  const before = await (await context.request.get(base + '/api/documents/' + document.id)).json();
  assert.equal(before.version, 2);
  await page.getByRole('textbox', { name: '标题关键词', exact: true }).fill('missing-' + suffix);
  await page.getByRole('button', { name: '搜索', exact: true }).click();
  await page.getByText('没有匹配的文档。', { exact: true }).waitFor();
  await page.getByRole('textbox', { name: '标题关键词', exact: true }).fill(title);
  await page.getByRole('button', { name: '搜索', exact: true }).click();
  await open(title + ' saved');
  await page.reload();
  await open(title + ' saved');
  assert.equal((await page.locator('.tiptap').innerText()).trimEnd(), '手动保存且重启后保留 😀');
  console.log('PASS A/B: browser registration, profile, rich text, duplicate prevention, failed-save draft, save/search/refresh');

  // Real backend outage/restart, not a mocked successful response.
  await stop();
  await page.getByRole('button', { name: '重新检查', exact: true }).click();
  await page.getByText(/连接失败，请确认后端已启动后重试/).waitFor();
  await page.getByRole('button', { name: '刷新列表', exact: true }).click();
  await page.getByRole('button', { name: '重试列表', exact: true }).waitFor();
  await start();
  await page.reload();
  await login(email);
  await open(title + ' saved');
  const after = await (await context.request.get(base + '/api/documents/' + document.id)).json();
  assert.deepEqual(after, before);
  assert.equal((await page.locator('.tiptap').innerText()).trimEnd(), '手动保存且重启后保留 😀');
  console.log('PASS B/F: real process restart, expired server session, email re-login, exact persisted document');

  // Queue an older real GET response while selecting another document.
  const alternate = await (await write('POST', '/api/documents', { title: title + ' alternate', content: { type: 'doc', content: [{ type: 'paragraph' }] } })).json();
  await page.getByRole('button', { name: '刷新列表', exact: true }).click();
  await page.locator('.document-link').filter({ hasText: alternate.title }).waitFor();
  await page.route('**/api/documents/' + document.id, async route => { const result = await route.fetch(); await sleep(700); await route.fulfill({ response: result }); });
  await page.locator('.document-link').filter({ hasText: title + ' saved' }).click();
  await open(alternate.title);
  await sleep(900);
  assert.equal(await page.locator('.document-main h2').innerText(), alternate.title);
  await page.unroute('**/api/documents/' + document.id);
  await open(title + ' saved');
  await page.getByRole('button', { name: '删除文档', exact: true }).click();
  await page.getByRole('button', { name: '确定删除', exact: true }).click();
  await page.getByText('文档已删除。', { exact: true }).waitFor();
  assert.equal((await context.request.get(base + '/api/documents/' + document.id)).status(), 404);
  assert.equal((await write('DELETE', '/api/documents/' + alternate.id)).status(), 204);
  const oldSession = (await context.cookies()).find(c => c.name === 'JSESSIONID').value;
  await page.getByRole('button', { name: '退出登录', exact: true }).click();
  await page.getByRole('button', { name: '登录', exact: true }).waitFor();
  const old = await browser.newContext({ extraHTTPHeaders: { Cookie: 'JSESSIONID=' + oldSession } });
  assert.equal((await old.request.get(base + '/api/users/me')).status(), 401);
  await old.close();
  await login(email);
  assert.equal((await (await context.request.get(base + '/api/users/me')).json()).email, email);
  await write('POST', '/api/auth/logout');
  await page.getByRole('button', { name: '刷新列表', exact: true }).click();
  await page.getByText('会话已失效，请重新登录。', { exact: true }).waitFor();
  assert.deepEqual(errors, []);
  console.log('PASS A/B/F: stale-response isolation, delete/404, logout/old-session denial, re-login/profile, external session loss; no JS errors');
})().catch(error => { console.error(error); process.exitCode = 1; }).finally(async () => {
  if (browser) await browser.close();
  await stop();
});
