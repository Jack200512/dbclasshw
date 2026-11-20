const {chromium}=require(process.env.PLAYWRIGHT_MODULE_PATH || require('node:path').resolve('target/browser-check/node_modules/playwright-core'));
const assert=require('node:assert/strict');const crypto=require('node:crypto');
(async()=>{
 const browser=await chromium.launch({channel:'msedge',headless:true});let pageA;
 try{
  const errors=[],stamp=Date.now().toString(),password=crypto.randomBytes(18).toString('hex');
  async function user(prefix){
   const context=await browser.newContext();const name=prefix+stamp;
   const csrf=await(await context.request.get('http://127.0.0.1:5173/api/auth/csrf')).json();
   const registered=await context.request.post('http://127.0.0.1:5173/api/auth/register',{headers:{[csrf.headerName]:csrf.token},data:{username:name,email:name+'@example.test',password}});
   assert.equal(registered.status(),201);const profile=await registered.json();
   const page=await context.newPage();page.on('pageerror',e=>errors.push(e.message));
   await page.goto('http://127.0.0.1:5173');await page.getByRole('textbox',{name:'用户名或邮箱',exact:true}).fill(name);
   await page.getByRole('textbox',{name:'密码',exact:true}).fill(password);await page.getByRole('button',{name:'登录',exact:true}).click();
   await page.getByRole('heading',{name:'个人资料',exact:true}).waitFor();return {context,page,name,id:profile.id};
  }
  const a=await user('shareBrowserA_'),b=await user('shareBrowserB_'),c=await user('shareBrowserC_');pageA=a.page;
  await a.page.getByRole('button',{name:'新建文档',exact:true}).click();const title='M2-shared-browser-'+stamp;
  await a.page.getByRole('textbox',{name:'文档标题',exact:true}).fill(title);await a.page.locator('.tiptap').fill('Owner original');
  const creating=a.page.waitForResponse(r=>r.url().endsWith('/api/documents')&&r.request().method()==='POST');
  await a.page.getByRole('button',{name:'创建文档',exact:true}).click();const created=await creating;assert.equal(created.status(),201);const doc=await created.json();
  await a.page.getByRole('button',{name:'共享文档',exact:true}).click();
  await a.page.getByRole('textbox',{name:'共享用户名或邮箱',exact:true}).fill(b.name);
  await a.page.locator('.sharing .share-actions .el-select').click();await a.page.getByRole('option',{name:'编辑者 EDITOR',exact:true}).click();
  await a.page.getByRole('button',{name:'添加共享',exact:true}).click();await a.page.locator('.members li').filter({hasText:b.name}).waitFor();
  await a.page.getByRole('textbox',{name:'共享用户名或邮箱',exact:true}).fill(c.name+'@example.test');
  await a.page.locator('.sharing .share-actions .el-select').click();await a.page.getByRole('option',{name:'查看者 VIEWER',exact:true}).click();
  await a.page.getByRole('button',{name:'添加共享',exact:true}).click();await a.page.locator('.members li').filter({hasText:c.name}).waitFor();
  for(const actor of [b,c]){
   await actor.page.getByRole('button',{name:'刷新列表',exact:true}).click();await actor.page.getByRole('button',{name:'共享给我的',exact:true}).click();
   await actor.page.locator('.document-link').filter({hasText:title}).click();await actor.page.getByText('只读查看',{exact:true}).waitFor();
   assert.equal(await actor.page.getByRole('button',{name:'删除文档',exact:true}).count(),0);assert.equal(await actor.page.getByRole('button',{name:'共享文档',exact:true}).count(),0);
  }
  assert.equal(await c.page.getByRole('button',{name:'编辑文档',exact:true}).count(),0);assert.equal(await c.page.locator('.tiptap').getAttribute('contenteditable'),'false');
  await b.page.getByRole('textbox',{name:'标题关键词',exact:true}).fill(stamp);await b.page.getByRole('button',{name:'搜索',exact:true}).click();await b.page.locator('.document-link').filter({hasText:title}).waitFor();
  await b.page.getByRole('button',{name:'编辑文档',exact:true}).click();await b.page.locator('.tiptap').fill('EDITOR changed rich content');
  await b.page.getByRole('button',{name:'保存文档',exact:true}).click();await b.page.getByText('文档保存成功。',{exact:true}).waitFor();
  await a.page.getByRole('button',{name:'重新加载',exact:true}).click();await a.page.getByText('只读查看',{exact:true}).waitFor();
  assert((await a.page.locator('.tiptap').innerText()).includes('EDITOR changed rich content'));
  await a.page.getByRole('button',{name:'共享文档',exact:true}).click();await a.page.locator('.members li').filter({hasText:b.name}).waitFor();
  await b.page.getByRole('button',{name:'编辑文档',exact:true}).click();await b.page.locator('.tiptap').fill('Local draft must survive role change');
  const changing=a.page.waitForResponse(r=>r.url().endsWith('/permissions/'+b.id)&&r.request().method()==='PUT');
  await a.page.locator('.members li').filter({hasText:b.name}).locator('.el-select').click();await a.page.getByRole('option',{name:'查看者 VIEWER',exact:true}).click();
  assert.equal((await changing).status(),200);
  await b.page.waitForFunction(()=>document.querySelector('.metadata')?.textContent.includes('VIEWER')); assert(await b.page.getByRole('button',{name:'保存文档',exact:true}).isDisabled()); assert((await b.page.locator('.tiptap').innerText()).includes('Local draft must survive'));
  for(const actor of [c,b]){
   await a.page.locator('.members li').filter({hasText:actor.name}).getByRole('button',{name:'撤销共享',exact:true}).click();
   await a.page.getByRole('button',{name:'确定撤销',exact:true}).click();await a.page.locator('.members li').filter({hasText:actor.name}).waitFor({state:'detached'});
   assert.equal((await actor.context.request.get('http://127.0.0.1:5173/api/documents/'+doc.id)).status(),404);
  }
  await b.page.locator('.realtime-status').filter({hasText:'订阅已失效'}).waitFor(); assert(await b.page.getByRole('button',{name:'保存文档',exact:true}).isDisabled());
  assert((await b.page.locator('.tiptap').innerText()).includes('Local draft must survive'));
  await c.page.getByRole('button',{name:'刷新列表',exact:true}).click();await c.page.getByText('暂无文档，点击新建文档开始。',{exact:true}).waitFor();
  await b.page.screenshot({path:'target/browser-check/sharing-revoked-draft.png',fullPage:true});
  await a.page.getByRole('button',{name:'删除文档',exact:true}).click();await a.page.getByRole('button',{name:'确定删除',exact:true}).click();await a.page.getByText('文档已删除。',{exact:true}).waitFor();
  assert.deepEqual(errors,[]);
  console.log('PASS real browser A/B/C sharing UI, editor save visible to owner, viewer readonly, role downgrade/revoke reject saves while preserving draft, shared list, owner delete, no JS errors');
 }catch(e){if(pageA)await pageA.screenshot({path:'target/browser-check/sharing-failure.png',fullPage:true});throw e;}
 finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1});
