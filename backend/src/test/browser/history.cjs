// From backend/, with real MySQL, backend and Vite running. No successful response mocks.
const { chromium } = require(process.env.PLAYWRIGHT_MODULE_PATH || require('node:path').resolve('target/browser-check/node_modules/playwright-core'));
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const { execFileSync } = require('node:child_process');
const { mkdirSync } = require('node:fs');
const base = 'http://127.0.0.1:5173';
(async () => {
  mkdirSync('target/browser-check', { recursive: true });
  const browser = await chromium.launch({ channel: 'msedge', headless: true });
  let diagnostic;
  try {
    const errors = [], stamp = Date.now(), password = crypto.randomBytes(18).toString('hex');
    async function write(actor, method, path, data) {
      const token = await (await actor.context.request.get(base + '/api/auth/csrf')).json();
      return actor.context.request.fetch(base + path, { method, headers: { [token.headerName]: token.token }, ...(data ? { data } : {}) });
    }
    async function user(suffix) {
      const actor = { context: await browser.newContext(), name: 'histBrowser_' + suffix + stamp };
      const response = await write(actor, 'POST', '/api/auth/register', { username: actor.name, email: actor.name + '@example.test', password });
      assert.equal(response.status(), 201);actor.id = (await response.json()).id;
      actor.page = await actor.context.newPage();actor.page.on('pageerror', e => errors.push(e.message));
      await actor.page.goto(base);
      await actor.page.getByRole('textbox', { name: '用户名或邮箱', exact: true }).fill(actor.name);
      await actor.page.getByRole('textbox', { name: '密码', exact: true }).fill(password);
      await actor.page.getByRole('button', { name: '登录', exact: true }).click();
      await actor.page.getByRole('heading', { name: '个人资料', exact: true }).waitFor();
      return actor;
    }
    const a = await user('A'), b = await user('B'), c = await user('C');diagnostic = a.page;
    const title = 'M3-history-browser-' + stamp;
    await a.page.getByRole('button', { name: '新建文档', exact: true }).click();
    await a.page.getByRole('textbox', { name: '文档标题', exact: true }).fill(title);
    await a.page.locator('.document-main .el-form .tiptap').fill('original v1');
    const creation = a.page.waitForResponse(r => r.request().method() === 'POST' && r.url().endsWith('/api/documents'));
    await a.page.getByRole('button', { name: '创建文档', exact: true }).click();
    const created = await creation;assert.equal(created.status(), 201);const doc = await created.json();
    async function save(actor, text) {
      await actor.page.getByRole('button', { name: '编辑文档', exact: true }).click();
      await actor.page.locator('.document-main .el-form .tiptap').fill(text);
      await actor.page.getByRole('button', { name: '保存文档', exact: true }).click();
      await actor.page.getByText('文档保存成功。', { exact: true }).waitFor();
    }
    await save(a, 'saved v2');await save(a, 'saved v3');
    const historyPath = `/api/documents/${doc.id}/versions`;
    const original = await (await a.context.request.get(base + historyPath)).json();
    assert.deepEqual(original.map(v => v.versionNumber), [3, 2, 1]);
    for (const [actor, role] of [[b, 'EDITOR'], [c, 'VIEWER']]) {
      assert.equal((await write(a, 'POST', `/api/documents/${doc.id}/permissions`, { login: actor.name, role })).status(), 201);
      await actor.page.getByRole('button', { name: '刷新列表', exact: true }).click();
      await actor.page.locator('.document-link').filter({ hasText: title }).click();
      await actor.page.locator('.realtime-status').filter({ hasText: '实时连接正常' }).waitFor();
    }
    async function preview(actor, version) {
      await actor.page.getByRole('button', { name: '历史版本', exact: true }).click();
      await actor.page.getByRole('button', { name: `预览版本 ${version}`, exact: true }).click();
      await actor.page.locator('.history-preview h3').filter({ hasText: 'v' + version }).waitFor();
    }
    async function restore(actor, version, expectedStatus = 200) {
      await preview(actor, version);
      await actor.page.getByRole('button', { name: '恢复此版本', exact: true }).click();
      const result = actor.page.waitForResponse(r => r.request().method() === 'POST' && r.url().endsWith(`/${version}/restore`));
      await actor.page.getByRole('button', { name: '确认恢复', exact: true }).click();
      assert.equal((await result).status(), expectedStatus);
      await actor.page.getByRole('dialog', { name: '历史版本', exact: true }).waitFor({ state: 'hidden' });
    }
    await preview(c, 1);
    assert.equal(await c.page.locator('.history-preview .tiptap').getAttribute('contenteditable'), 'false');
    assert.equal(await c.page.getByRole('button', { name: '恢复此版本', exact: true }).count(), 0);
    await c.page.getByRole('dialog', { name: '历史版本', exact: true }).getByRole('button', { name: 'Close this dialog', exact: true }).click();
    await preview(a, 1);
    assert.equal(await a.page.locator('.history-preview .tiptap').innerText(), 'original v1');
    assert.equal(await a.page.locator('.document-main .el-form .tiptap').innerText(), 'saved v3');
    await a.page.getByRole('dialog', { name: '历史版本', exact: true }).getByRole('button', { name: 'Close this dialog', exact: true }).click();
    await restore(a, 1);
    await b.page.locator('.document-main .el-form .tiptap').filter({ hasText: 'original v1' }).waitFor();
    assert((await b.page.locator('.metadata').innerText()).includes('版本 4'));
    await b.page.getByRole('button', { name: '编辑文档', exact: true }).click();
    await b.page.locator('.document-main .el-form .tiptap').fill('unsaved historical draft');
    await restore(a, 2);
    await b.page.getByText(/检测到远端更新/).waitFor();
    await preview(b, 1);
    assert.equal(await b.page.locator('.document-main .el-form .tiptap').innerText(), 'unsaved historical draft');
    await b.page.getByRole('button', { name: '恢复此版本', exact: true }).click();
    await b.page.getByText(/当前有未保存草稿，成功恢复会放弃这些修改/).waitFor();
    await b.page.getByRole('button', { name: '取消', exact: true }).click();
    assert.equal(await b.page.locator('.document-main .el-form .tiptap').innerText(), 'unsaved historical draft');
    await b.page.getByRole('button', { name: '恢复此版本', exact: true }).click();
    const conflict = b.page.waitForResponse(r => r.url().endsWith('/1/restore'));
    await b.page.getByRole('button', { name: '确认恢复', exact: true }).click();
    assert.equal((await conflict).status(), 409);
    await b.page.getByText(/文档已被其他操作更新/).waitFor();
    assert.equal(await b.page.locator('.document-main .el-form .tiptap').innerText(), 'unsaved historical draft');
    await b.page.getByRole('button', { name: '重新加载最新版本', exact: true }).click();
    await b.page.getByRole('button', { name: '放弃修改', exact: true }).click();
    await b.page.getByText('只读查看', { exact: true }).waitFor();
    await restore(b, 1);
    await a.page.locator('.document-main .el-form .tiptap').filter({ hasText: 'original v1' }).waitFor();
    const all = await (await a.context.request.get(base + historyPath)).json();
    assert.deepEqual(all.filter(v => v.versionNumber <= 3), original);
    assert.deepEqual(all.map(v => v.versionNumber), [6, 5, 4, 3, 2, 1]);
    assert.equal(all[0].editorId, b.id);
    await a.page.reload();await a.page.locator('.document-link').filter({ hasText: title }).click();
    await a.page.locator('.document-main .el-form .tiptap').filter({ hasText: 'original v1' }).waitFor();
    await preview(a, 2);
    // Real HTTP failure does not replace the current preview/editor with fake success.
    await a.page.route('**/versions/3', route => route.abort('failed'));
    await a.page.getByRole('button', { name: '预览版本 3', exact: true }).click();
    await a.page.getByRole('button', { name: '重试历史预览', exact: true }).waitFor();
    await a.page.unroute('**/versions/3');
    await a.page.getByRole('button', { name: '重试历史预览', exact: true }).click();
    await a.page.locator('.history-preview .tiptap').filter({ hasText: 'saved v3' }).waitFor();
    await a.page.getByRole('dialog', { name: '历史版本', exact: true }).getByRole('button', { name: 'Close this dialog', exact: true }).click();
    const legacyTitle = 'M3-legacy-browser-' + stamp;
    const legacyId = execFileSync('java', ['--class-path', 'target/maven-repository/com/mysql/mysql-connector-j/9.7.0/mysql-connector-j-9.7.0.jar', 'src/test/browser/HistoryPlainFixture.java', String(a.id), a.name, legacyTitle], { encoding: 'utf8', windowsHide: true }).trim();
    await a.page.getByRole('button', { name: '刷新列表', exact: true }).click();
    await a.page.locator('.document-link').filter({ hasText: legacyTitle }).click();
    await preview(a, 7);
    assert((await a.page.locator('.history-preview .tiptap').innerText()).includes('历史纯文本'));
    assert.equal(await a.page.locator('.history-preview .tiptap img').count(), 0);
    assert.equal(await a.page.evaluate(() => window.__unsafe), undefined);
    await a.page.screenshot({ path: 'target/browser-check/history-success.png', fullPage: true });
    await a.page.getByRole('dialog', { name: '历史版本', exact: true }).getByRole('button', { name: 'Close this dialog', exact: true }).click();
    await restore(a, 7);
    const plain = await (await a.context.request.get(base + '/api/documents/' + legacyId)).json();
    assert.equal(plain.version, 8);assert.equal(plain.contentFormat, 'PLAIN_TEXT');
    for (const id of [doc.id, legacyId]) assert.equal((await write(a, 'DELETE', '/api/documents/' + id)).status(), 204);
    assert.deepEqual(errors, []);
    console.log('PASS Edge history v1-v6, immutable snapshots, preview isolation, OWNER/EDITOR restore, VIEWER readonly, confirmation/cancel/409 draft protection, bidirectional restored-content sync, refresh, failed-preview retry, plain-text baseline/restore, no JS errors');
  } catch (e) {
    if (diagnostic) await diagnostic.screenshot({ path: 'target/browser-check/history-failure.png', fullPage: true });
    throw e;
  } finally { await browser.close(); }
})().catch(e => { console.error(e); process.exitCode = 1; });
