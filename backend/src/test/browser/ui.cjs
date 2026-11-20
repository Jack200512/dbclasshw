// Run from backend/ with MySQL, backend and Vite running; uses real Edge/API data.
const { chromium } = require(process.env.PLAYWRIGHT_MODULE_PATH || require('node:path').resolve('target/browser-check/node_modules/playwright-core'));
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const { mkdirSync } = require('node:fs');
(async () => {
  mkdirSync('target/browser-check', { recursive: true });
  const browser = await chromium.launch({ channel: 'msedge', headless: true });
  const context = await browser.newContext({ viewport: { width: 1366, height: 768 } });
  const page = await context.newPage();
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  // Check Vue warnings as well as unhandled exceptions; expected HTTP failures are separate.
  page.on('console', message => { if (message.type() === 'warning' && /Vue warn/.test(message.text())) errors.push(message.text()); });
  try {
    const stamp = Date.now(), name = 'uiSmoke_' + stamp, password = crypto.randomBytes(18).toString('hex');
    await page.goto('http://127.0.0.1:5173');
    assert(await page.getByRole('button', { name: '登录', exact: true }).isDisabled());
    await page.getByRole('button', { name: '去注册', exact: true }).click();
    await page.getByRole('textbox', { name: '用户名', exact: true }).fill(name);
    await page.getByRole('textbox', { name: '邮箱', exact: true }).fill(name + '@example.test');
    await page.getByRole('textbox', { name: '密码', exact: true }).fill(password);
    await page.getByRole('button', { name: '注册', exact: true }).click();
    await page.getByText('注册成功，请登录。', { exact: true }).waitFor();
    async function login() {
      await page.getByRole('textbox', { name: '用户名或邮箱', exact: true }).fill(name);
      await page.getByRole('textbox', { name: '密码', exact: true }).fill(password);
      await page.getByRole('button', { name: '登录', exact: true }).click();
      await page.getByRole('button', { name: '新建文档', exact: true }).waitFor();
    }
    await page.getByRole('textbox', { name: '密码', exact: true }).fill('incorrect password');
    await page.getByRole('button', { name: '登录', exact: true }).click();
    await page.getByText('用户名、邮箱或密码错误。', { exact: true }).waitFor();
    await login();
    const title = 'UI-' + stamp + '长标题'.repeat(60);
    const body = '长文本与换行安全展示。\n'.repeat(100);
    await page.getByRole('button', { name: '新建文档', exact: true }).click();
    await page.getByRole('textbox', { name: '文档标题', exact: true }).fill(title);
    await page.locator('.document-main .tiptap').fill(body);
    await page.locator('.document-main .tiptap').press('ControlOrMeta+A');
    await page.getByRole('button', { name: '加粗', exact: true }).click();
    assert.equal(await page.getByRole('button', { name: '加粗', exact: true }).getAttribute('aria-pressed'), 'true');
    const creation = page.waitForResponse(r => r.request().method() === 'POST' && r.url().endsWith('/api/documents'));
    await page.getByRole('button', { name: '创建文档', exact: true }).click();
    assert.equal((await creation).status(), 201);
    await page.getByText('只读查看', { exact: true }).waitFor();
    await page.getByRole('button', { name: '编辑文档', exact: true }).click();
    await page.getByRole('textbox', { name: '文档标题', exact: true }).fill(title + ' draft');
    await page.locator('.document-main .tiptap').fill('UI unsaved draft');
    await page.getByText('有未保存修改', { exact: true }).waitFor();
    async function inViewport(locator, width, height) {
      const box = await locator.boundingBox();assert(box);
      assert(box.x >= -1 && box.y >= -1 && box.x + box.width <= width + 1 && box.y + box.height <= height + 1, JSON.stringify(box));
    }
    for (const [width, height] of [[1366, 768], [1920, 1080]]) {
      await page.setViewportSize({ width, height });
      await page.evaluate(() => window.scrollTo(0, 0));
      assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false);
      await inViewport(page.getByRole('button', { name: '保存文档', exact: true }), width, height);
      await inViewport(page.getByRole('button', { name: '共享文档', exact: true }), width, height);
      await page.screenshot({ path: `target/browser-check/ui-layout-${width}.png`, fullPage: true });
      await page.getByRole('button', { name: '共享文档', exact: true }).click();
      const sharing = page.getByRole('dialog', { name: '共享管理', exact: true });
      await sharing.waitFor();await page.waitForTimeout(350); // Finish dialog transition before measuring.
      await inViewport(sharing, width, height);
      await inViewport(sharing.getByRole('button', { name: '添加共享', exact: true }), width, height);
      await sharing.getByRole('button', { name: 'Close this dialog', exact: true }).click();
      assert.equal(await page.getByRole('textbox', { name: '文档标题', exact: true }).inputValue(), title + ' draft');
      await page.getByRole('button', { name: '历史版本', exact: true }).click();
      await page.getByRole('button', { name: '预览版本 1', exact: true }).click();
      const history = page.getByRole('dialog', { name: '历史版本', exact: true });
      await page.locator('.history-preview .tiptap').waitFor();await page.waitForTimeout(350);
      await inViewport(history, width, height);
      await inViewport(history.getByRole('button', { name: '恢复此版本', exact: true }), width, height);
      assert.equal(await page.locator('.history-preview .tiptap').getAttribute('contenteditable'), 'false');
      const scrolling = await page.locator('.history-preview .tiptap').evaluate(e => e.scrollHeight > e.clientHeight);
      assert(scrolling, 'Long preview should scroll inside its editor');
      await page.screenshot({ path: `target/browser-check/ui-history-${width}.png`, fullPage: true });
      await history.getByRole('button', { name: '关闭历史', exact: true }).click();
      assert.equal(await page.locator('.document-main .tiptap').innerText(), 'UI unsaved draft');
      console.log(`PASS layout ${width}x${height}: no horizontal overflow; actions and dialogs fit; long preview scrolls; draft retained`);
    }
    // Persistent network failure feedback must keep the draft and expose retry controls.
    await page.route('**/api/documents?*', route => route.abort('failed'));
    await page.getByRole('button', { name: '刷新列表', exact: true }).click();
    await page.getByRole('button', { name: '重试列表', exact: true }).waitFor();
    assert.equal(await page.locator('.document-main .tiptap').innerText(), 'UI unsaved draft');
    await page.unroute('**/api/documents?*');
    await page.getByRole('button', { name: '重试列表', exact: true }).click();
    await page.getByRole('button', { name: '取消编辑', exact: true }).click();
    await page.getByRole('button', { name: '放弃修改', exact: true }).click();
    await page.getByRole('textbox', { name: '标题关键词', exact: true }).fill('no-match-' + stamp);
    await page.getByRole('button', { name: '搜索', exact: true }).click();
    await page.getByText('没有匹配的文档。', { exact: true }).waitFor();
    await page.getByRole('textbox', { name: '标题关键词', exact: true }).fill(String(stamp));
    await page.getByRole('button', { name: '搜索', exact: true }).click();
    await page.locator('.document-link').filter({ hasText: title }).waitFor();
    await page.getByRole('button', { name: '退出登录', exact: true }).click();
    await page.getByRole('button', { name: '登录', exact: true }).waitFor();
    await login();
    await page.reload();await page.locator('.document-link').filter({ hasText: title }).click();
    await page.getByText('只读查看', { exact: true }).waitFor();
    await page.getByRole('button', { name: '删除文档', exact: true }).click();
    await page.getByRole('button', { name: '确定删除', exact: true }).click();
    await page.getByText('文档已删除。', { exact: true }).waitFor();
    assert.deepEqual(errors, []);
    console.log('PASS UI registration/login/error/logout/re-login/refresh, toolbar selection, long content, draft protections, list failure/retry, search and delete; no Vue warnings or JS errors');
  } catch (error) {
    await page.screenshot({ path: 'target/browser-check/ui-failure.png', fullPage: true });throw error;
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
