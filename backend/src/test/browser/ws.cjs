const {chromium}=require(process.env.PLAYWRIGHT_MODULE_PATH || require('node:path').resolve('target/browser-check/node_modules/playwright-core'));const assert=require('node:assert/strict');const crypto=require('node:crypto');
(async()=>{
 const browser=await chromium.launch({channel:'msedge',headless:true});let diagnostic;
 try{
  const errors=[],stamp=Date.now(),password=crypto.randomBytes(18).toString('hex');
  async function actor(prefix){
   const context=await browser.newContext();await context.addInitScript(()=>{
    const Native=window.WebSocket;window.__sockets=[];window.__blockSockets=false;
    window.WebSocket=class extends Native{constructor(...args){super(...args);if(String(args[0]).includes('/api/ws/documents')){window.__sockets.push(this);if(window.__blockSockets)queueMicrotask(()=>this.close());}}};
   });
   const name=prefix+stamp;const csrf=await(await context.request.get('http://127.0.0.1:5173/api/auth/csrf')).json();
   const registered=await context.request.post('http://127.0.0.1:5173/api/auth/register',{headers:{[csrf.headerName]:csrf.token},data:{username:name,email:name+'@example.test',password}});assert.equal(registered.status(),201);
   const page=await context.newPage();page.on('pageerror',e=>errors.push(e.message));await page.goto('http://127.0.0.1:5173');
   await page.getByRole('textbox',{name:'用户名或邮箱',exact:true}).fill(name);await page.getByRole('textbox',{name:'密码',exact:true}).fill(password);await page.getByRole('button',{name:'登录',exact:true}).click();await page.getByRole('heading',{name:'个人资料',exact:true}).waitFor();return {context,page,name};
  }
  async function write(actor,method,path,body){const token=await(await actor.context.request.get('http://127.0.0.1:5173/api/auth/csrf')).json();return actor.context.request.fetch('http://127.0.0.1:5173'+path,{method,headers:{[token.headerName]:token.token},...(body?{data:body}:{})});}
  const rich=text=>({type:'doc',content:[{type:'paragraph',content:[{type:'text',text}]}]});
  const a=await actor('syncA_'),b=await actor('syncB_'),c=await actor('syncC_');diagnostic=b.page;
  const title='M2-WS-browser-'+stamp;
  const created=await write(a,'POST','/api/documents',{title,content:rich('initial')});assert.equal(created.status(),201);const doc=await created.json();
  for(const [user,role] of [[b,'EDITOR'],[c,'VIEWER']])assert.equal((await write(a,'POST',`/api/documents/${doc.id}/permissions`,{login:user.name,role})).status(),201);
  async function open(user,name=title){await user.page.getByRole('button',{name:'刷新列表',exact:true}).click();await user.page.locator('.document-link').filter({hasText:name}).click();await user.page.locator('.realtime-status').filter({hasText:'实时连接正常'}).waitFor();}
  async function saved(user,text){await user.page.getByRole('button',{name:'编辑文档',exact:true}).click();await user.page.locator('.tiptap').fill(text);await user.page.getByRole('button',{name:'保存文档',exact:true}).click();await user.page.getByText('文档保存成功。',{exact:true}).waitFor();}
  async function sees(user,text){await user.page.locator('.tiptap').filter({hasText:text}).waitFor();}
  await open(a);await open(b);await open(c);
  await saved(a,'A committed version 2');await sees(b,'A committed version 2');await sees(c,'A committed version 2');
  await saved(b,'B committed version 3');await sees(a,'B committed version 3');await sees(c,'B committed version 3');
  await b.page.evaluate(()=>{for(const socket of window.__sockets)if(socket.readyState===1)socket.close(4001,'test automatic reconnect');});await b.page.locator('.realtime-status').filter({hasText:/断开|重连/}).waitFor();await b.page.locator('.realtime-status').filter({hasText:'实时连接正常'}).waitFor();await sees(b,'B committed version 3');
  assert.equal(await c.page.getByRole('button',{name:'编辑文档',exact:true}).count(),0);
  await b.page.getByRole('button',{name:'编辑文档',exact:true}).click();await b.page.locator('.tiptap').fill('LOCAL DRAFT protected');
  await saved(a,'A committed version 4');await b.page.getByText(/检测到远端更新/).waitFor();assert.equal(await b.page.locator('.tiptap').innerText(),'LOCAL DRAFT protected');
  await b.page.getByRole('button',{name:'保存文档',exact:true}).click();await b.page.getByText(/文档已被其他操作更新/).waitFor();assert.equal(await b.page.locator('.tiptap').innerText(),'LOCAL DRAFT protected');
  async function reloadDraft(){await b.page.getByRole('button',{name:'重新加载最新版本',exact:true}).click();await b.page.getByRole('button',{name:'放弃修改',exact:true}).click();await b.page.getByText('只读查看',{exact:true}).waitFor();}
  await reloadDraft();await sees(b,'A committed version 4');
  async function disconnect(){await b.page.evaluate(()=>{window.__blockSockets=true;for(const ws of window.__sockets)if(ws.readyState===1)ws.close(4001,'test transport interruption');});await b.page.locator('.realtime-status').filter({hasText:/断开|重连/}).waitFor();}
  async function reconnect(){await b.page.evaluate(()=>window.__blockSockets=false);await b.page.getByRole('button',{name:'重连实时通知',exact:true}).click();await b.page.locator('.realtime-status').filter({hasText:'实时连接正常'}).waitFor();}
  await disconnect();assert.equal((await b.context.request.get('http://127.0.0.1:5173/api/documents/'+doc.id)).status(),200);
  await saved(a,'A offline version 5');await reconnect();await sees(b,'A offline version 5');
  await b.page.getByRole('button',{name:'编辑文档',exact:true}).click();await b.page.locator('.tiptap').fill('OFFLINE DRAFT protected');
  await disconnect();await saved(a,'A offline version 6');await reconnect();await b.page.getByText(/检测到远端更新/).waitFor();assert.equal(await b.page.locator('.tiptap').innerText(),'OFFLINE DRAFT protected');
  await b.page.getByRole('button',{name:'保存文档',exact:true}).click();await b.page.getByText(/文档已被其他操作更新/).waitFor();await reloadDraft();await sees(b,'A offline version 6');
  const alternate=await(await write(a,'POST','/api/documents',{title:'M2-WS-alternate-'+stamp,content:rich('other document')})).json();
  await write(a,'POST',`/api/documents/${alternate.id}/permissions`,{login:b.name,role:'EDITOR'});await open(b,alternate.title);
  await saved(a,'A old-document version 7');await b.page.waitForTimeout(350);assert.equal(await b.page.locator('.tiptap').innerText(),'other document');
  await open(b);await sees(b,'A old-document version 7');
  const members=await(await a.context.request.get(`http://127.0.0.1:5173/api/documents/${doc.id}/permissions`)).json();const bId=members.find(m=>m.username===b.name).userId;
  await b.page.getByRole('button',{name:'编辑文档',exact:true}).click();await b.page.locator('.tiptap').fill('DOWNGRADED DRAFT protected');
  await write(a,'PUT',`/api/documents/${doc.id}/permissions/${bId}`,{role:'VIEWER'});
  await b.page.getByRole('button',{name:'保存文档',exact:true}).waitFor();await b.page.waitForFunction(()=>document.querySelector('.metadata')?.textContent.includes('VIEWER'));
  assert.equal(await b.page.getByRole('button',{name:'保存文档',exact:true}).isDisabled(),true);assert.equal(await b.page.locator('.tiptap').innerText(),'DOWNGRADED DRAFT protected');
  await write(a,'DELETE',`/api/documents/${doc.id}/permissions/${bId}`);await b.page.locator('.realtime-status').filter({hasText:'订阅已失效'}).waitFor();
  assert.equal((await b.context.request.get('http://127.0.0.1:5173/api/documents/'+doc.id)).status(),404);
  await saved(a,'A revoked version 8');await b.page.waitForTimeout(300);assert.equal(await b.page.locator('.tiptap').innerText(),'DOWNGRADED DRAFT protected');
  const current=await(await a.context.request.get('http://127.0.0.1:5173/api/documents/'+doc.id)).json();assert.equal(current.version,8);
  await b.page.screenshot({path:'target/browser-check/ws-draft-protected.png',fullPage:true});
  await write(c,'POST','/api/auth/logout');await c.page.getByRole('button',{name:'登录',exact:true}).waitFor();
  await write(a,'DELETE','/api/documents/'+doc.id);await write(a,'DELETE','/api/documents/'+alternate.id);
  assert.deepEqual(errors,[]);
  console.log('PASS real browser bidirectional auto-sync, VIEWER updates, protected online/offline drafts and 409, reconnect catch-up, HTTP during WS outage, document switch, downgrade/revoke, logout; DB version=8; no JS errors');
 }catch(e){if(diagnostic)await diagnostic.screenshot({path:'target/browser-check/ws-failure.png',fullPage:true});throw e;}
 finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1});
