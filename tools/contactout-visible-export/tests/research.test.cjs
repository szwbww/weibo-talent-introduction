const test = require('node:test');
const assert = require('node:assert/strict');
const research = require('../research.js');
const { createRatingServer, createLookup, deepseekGroundedResearch } = require('../rating-server.cjs');
const sources = [{ url: 'https://example.com/company', title: 'Company official' }, { url: 'https://fortune.com/ranking/global500/2026/', title: 'Fortune' }];
const fact = value => ({ value, sources: [0], evidence: 'Exact Corp is a materials company in Germany.' });
const facts = () => ({ identity: fact('Exact Corp'), sameEntity: fact(true), country: fact('Germany'), developedEconomy: fact(true), global500: { ...fact(false), sources: [1] }, global500Year: fact(2026), sector: fact('advanced_materials'), priority: fact('ic_materials') });
const assess = (data = facts()) => research.assess('Exact Corp', data, sources, Date.UTC(2026, 8, 19));
const assessWithSources = (data, evidence) => research.assess('Exact Corp', data, evidence, Date.UTC(2026, 8, 19));

test('score deterministic components, priority tiers and Global 500 override', () => {
  assert.equal(assess().score, 90);
  assert.equal(assess().requiredLevel, 2);
  assert.equal(assess({ ...facts(), priority: fact('ic') }).score, 70);
  assert.equal(assess({ ...facts(), priority: fact('other') }).requiredLevel, 4);
  const direct = assess({ ...facts(), global500: { ...fact(true), sources: [1] } });
  assert.equal(direct.score, 100);
  assert.equal(direct.requiredLevel, 1);
  assert.deepEqual(direct.company.aliases, []);
  assert.equal(direct.company.name, 'Exact Corp');
  assert.equal(assess({ ...facts(), global500:{...fact(true),sources:[1]}, priority:fact(null), sector:fact(null), country:fact(null), developedEconomy:fact(null) }).requiredLevel, 1);
});
test('unknown, fake citations, entity mismatch and invalid positive ranking never become low scores', () => {
  for (const data of [
    { ...facts(), priority: fact(null) }, { ...facts(), sameEntity: fact(false) },
    { ...facts(), country: { ...fact('Germany'), sources: [999] } },
    { ...facts(), global500: fact(true) },
    { ...facts(), global500: { ...fact(true), sources: [1] }, global500Year: fact(null) },
    { ...facts(), sector: fact('made_up') }
  ]) {
    assert.equal(assess(data).status, 'review');
    assert.equal(assess(data).score, null);
    assert.equal(assess(data).company, null);
  }
});
test('unverified Global 500 defaults to non-Global-500 scoring with a low-confidence notice', () => {
  const cases = [
    (() => { const value = facts(); delete value.global500; delete value.global500Year; return value; })(),
    { ...facts(), global500: fact(null), global500Year: fact(null) },
    { ...facts(), global500: { ...fact(false), sources: [0] }, global500Year: fact(null) },
    { ...facts(), global500: { ...fact(false), sources: [0] }, global500Year: fact(2022) }
  ];
  for (const data of cases) {
    const result = assessWithSources(data, sources.slice(0, 1));
    assert.equal(result.status, 'rated');
    assert.equal(result.score, 90);
    assert.equal(result.requiredLevel, 2);
    assert.equal(result.company.global500, false);
    assert.match(result.reason, /世界五百强未证实，按非五百强 0 分（低置信）/);
  }
  assert.equal(research.fresh({ ...assess(), ruleVersion: 'online-v3-direct' }, Date.UTC(2026, 8, 19)), false);
});
test('cache expiry and rule-version invalidation', () => {
  const r = assess();
  assert.ok(research.fresh(r, r.checkedAtMs + 1000));
  assert.equal(research.fresh(r, r.expiresAt), false);
  assert.equal(research.fresh({ ...r, ruleVersion: 'old' }, r.checkedAtMs), false);
  const config = research.enterpriseConfig([r, { ...r, expiresAt: 0 }], r.checkedAtMs);
  assert.equal(config.companies.length, 1);
});
test('grounding is required and provider uses Tavily before DeepSeek extraction', async () => {
  const calls = [];
  const fetcher = async (url, opts) => {
    calls.push({ url, headers: opts.headers, body: JSON.parse(opts.body) });
    return { ok: true, json: async () => calls.length === 1 ? {
      results: sources.map(s => ({ url:s.url, title:s.title, content:'Exact Corp is a materials company in Germany.' }))
    } : { choices: [{ message: { content: JSON.stringify(facts()) } }] } };
  };
  const r = await deepseekGroundedResearch('Exact Corp', { deepseekApiKey: 'deepseek-test', tavilyApiKey: 'tavily-test', fetcher, now: () => Date.UTC(2026, 8, 19) });
  assert.equal(r.status, 'rated');
  assert.equal(calls[0].url, 'https://api.tavily.com/search');
  assert.equal(calls[0].headers.Authorization, 'Bearer tavily-test');
  assert.equal(calls[1].url, 'https://api.deepseek.com/chat/completions');
  assert.equal(calls[1].headers.Authorization, 'Bearer deepseek-test');
  assert.equal(calls[1].body.response_format.type, 'json_object');
  assert.ok(!JSON.stringify(calls[1].body).includes('tavily-test'));
  const empty = await deepseekGroundedResearch('Exact Corp', { deepseekApiKey: 'test', tavilyApiKey: 'tavily', fetcher: async () => ({ ok: true, json: async () => ({ results: [] }) }) });
  assert.equal(empty.status, 'review');
});
test('coalescing, cached reuse, forced refresh and HTTP auth/validation', async t => {
  let count = 0;
  const lookup = createLookup(async name => { count++; await new Promise(r => setTimeout(r, 20)); return { ...assess(), query: name }; });
  await Promise.all([lookup('Exact Corp'), lookup('Exact Corp')]);
  assert.equal(count, 1);
  await lookup('Exact Corp');
  assert.equal(count, 1);
  await lookup('Exact Corp', true);
  assert.equal(count, 2);
  const server = createRatingServer({ token: 'a'.repeat(48), lookup });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  t.after(() => new Promise(resolve => server.close(resolve)));
  const url = `http://127.0.0.1:${server.address().port}/rate`;
  const headers = { 'Content-Type': 'application/json', 'X-ContactOut-Token': 'a'.repeat(48), Origin: 'chrome-extension://' + 'a'.repeat(32) };
  assert.equal((await fetch(url, { method: 'POST', headers, body: JSON.stringify({ company: 'Exact Corp' }) })).status, 200);
  assert.equal((await fetch(url, { method: 'POST', headers: { ...headers, Origin: 'https://evil.example' }, body: '{}' })).status, 403);
  assert.equal((await fetch(url, { method: 'POST', headers: { ...headers, 'X-ContactOut-Token': 'wrong' }, body: '{}' })).status, 401);
  assert.equal((await fetch(url, { method: 'POST', headers, body: JSON.stringify({ company: 'x'.repeat(201) }) })).status, 400);
});

test('missing credentials, forged quote, retry and body limits', async t => {
  await assert.rejects(deepseekGroundedResearch('Exact Corp', { deepseekApiKey: '', tavilyApiKey: 'test' }), /DEEPSEEK_API_KEY/);
  let calls = 0;
  await assert.rejects(deepseekGroundedResearch('Exact Corp', { deepseekApiKey: 'not-logged', tavilyApiKey: 'also-not-logged', fetcher: async () => { calls++; return { ok: false, status: 429 }; } }), /限流/);
  assert.equal(calls, 1, 'no automatic retry after paid provider request');
  const forged = await deepseekGroundedResearch('Exact Corp', { deepseekApiKey:'deepseek', tavilyApiKey:'tavily', fetcher: async (url) => ({ ok:true, json:async () => url.includes('tavily') ? { results:[{url:'https://example.com/company',title:'Company',content:'No supporting claim here.'}] } : { choices:[{message:{content:JSON.stringify(facts())}}] } }) });
  assert.equal(forged.status, 'review', 'model evidence must occur in cited search content');
  let failRefresh = false, runs = 0;
  const lookup = createLookup(async () => { runs++; if (failRefresh) throw new Error('refresh failed'); return assess(); });
  await lookup('Exact Corp');
  failRefresh = true;
  await assert.rejects(lookup('Exact Corp', true));
  await assert.rejects(lookup('Exact Corp'), /refresh failed/);
  assert.equal(runs,3,'failed forced refresh must invalidate server cached score');
  const server = createRatingServer({ token: 'b'.repeat(48), lookup: async () => { throw new Error('secret must not leak'); } });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  t.after(() => new Promise(resolve => server.close(resolve)));
  const url = `http://127.0.0.1:${server.address().port}/rate`;
  const headers = { 'Content-Type': 'application/json', 'X-ContactOut-Token': 'b'.repeat(48) };
  assert.equal((await fetch(url, { method: 'POST', headers, body: ' '.repeat(5000) })).status, 413);
  const failed = await fetch(url, { method: 'POST', headers, body: '{"company":"Exact Corp"}' });
  assert.equal(failed.status, 502);
  assert.ok(!(await failed.text()).includes('secret'));
});

test('real DOM company filter excludes profile employers and job-title chips', { skip: !process.env.PLAYWRIGHT_MODULE }, async () => {
  const path = require('node:path');
  const { chromium } = require(process.env.PLAYWRIGHT_MODULE);
  const browser = await chromium.launch({ headless: true, executablePath: process.env.TEST_BROWSER_PATH });
  try {
    const page = await browser.newPage();
    await page.route('**/*', r => r.fulfill({ contentType:'text/html', body:`<html><body>
      <aside><div><label>Job title</label><div class="multiValue">Senior Scientist<button>×</button></div></div>
      <div><label>Company</label><div><div class="multiValue"><span>Exact Corp</span><button>Remove Exact Corp</button></div></div></div></aside>
      <section><input type="checkbox"><h3>Expert</h3><a href="https://linkedin.com/in/person">in</a><p>Senior Engineer at Other Corp in 2020 - Present</p><button>View email</button></section>
      </body></html>` }));
    await page.goto('https://contactout.com/search');
    await page.addScriptTag({ path: path.resolve(__dirname, '../core.js') });
    await page.addScriptTag({ path: path.resolve(__dirname, '../collector.js') });
    const snapshot = await page.evaluate(() => readContactOutCompanyFilters());
    assert.deepEqual(snapshot.companies, ['Exact Corp']);
    await page.locator('aside .multiValue').last().evaluate(node => node.remove());
    assert.deepEqual((await page.evaluate(() => readContactOutCompanyFilters())).companies, [], 'must not ascend into Job title chips');
    const stale = await page.evaluate(snapshot => markContactOutForCompanySnapshot(snapshot, ContactOutExport.defaultIgnoreConfig(), ContactOutExport.defaultEnterpriseConfig()), snapshot);
    assert.deepEqual(stale, { stale:true });
  } finally { await browser.close(); }
});

test('popup direct query/cache/switch/failure does not mutate expert or manual company storage', { skip: !process.env.PLAYWRIGHT_MODULE }, async () => {
  const path = require('node:path');
  const { pathToFileURL } = require('node:url');
  const { chromium } = require(process.env.PLAYWRIGHT_MODULE);
  const core = require('../core.js');
  const browser = await chromium.launch({ headless: true, executablePath: process.env.TEST_BROWSER_PATH });
  try {
    const store = {
      'contactout-visible-export-v1': [{ name:'Existing', emails:['existing@example.com'] }],
      'contactout-ignore-rules-v1': core.defaultIgnoreConfig(),
      'contactout-enterprise-ratings-v1': core.defaultEnterpriseConfig(),
      'contactout-online-rating-v1': { enabled:true, deepseekApiKey:'deepseek-test', tavilyApiKey:'tavily-test', cache:{} }
    };
    const original = JSON.stringify(store['contactout-visible-export-v1']);
    const page = await browser.newPage({ viewport:{ width:500, height:1000 } });
    await page.exposeFunction('__get', keys => Object.fromEntries((Array.isArray(keys)?keys:[keys]).map(k=>[k,store[k]])));
    await page.exposeFunction('__set', values => Object.assign(store,values));
    const events = [];
    await page.exposeFunction('__event', event => events.push(event));
    let requests = 0, deepRequests = 0, fail = false, hold = null;
    await page.route('https://api.tavily.com/search', async route => {
      requests++;
      const query = route.request().postDataJSON().query;
      const name = query.split(' headquarters ')[0];
      events.push('search:' + name);
      if (name === 'Slow Corp') await new Promise(resolve => { hold = resolve; });
      await route.fulfill({ status: fail ? 503:200, contentType:'application/json', body:JSON.stringify(fail ? {} : { results:sources.map(s => ({ url:s.url, title:s.title, content:'Exact Corp is a materials company in Germany.' })) }) });
    });
    await page.route('https://api.deepseek.com/chat/completions', async route => {
      deepRequests++;
      const request = route.request().postDataJSON();
      assert.equal(request.response_format.type, 'json_object');
      assert.ok(!JSON.stringify(request).includes('deepseek-test'));
      assert.ok(!JSON.stringify(request).includes('tavily-test'));
      await route.fulfill({ status:200, contentType:'application/json', body:JSON.stringify({ choices:[{ message:{ content:JSON.stringify(facts()) } }] }) });
    });
    await page.addInitScript(() => {
      globalThis.company = 'Exact Corp'; globalThis.marks = [];
      globalThis.chrome = { storage:{local:{get:__get,set:__set}}, tabs:{query:async()=>[{id:1,url:'https://contactout.com/search'}]}, scripting:{executeScript:async request=>{
        if(request.files) return [];
        const code = request.func.toString();
        if(code.includes('readContactOutCompanyFilters')) return [{result:{companies:[company],url:'https://contactout.com/search'}}];
        if(code.includes('expandContactOutReviewDetails')) {
          await __event('expand:' + company);
          return [{result:{scanned:1,expanded:1,expansionFailed:0}}];
        }
        if(code.includes('markContactOutForCompanySnapshot')) {
          if(request.args[0].companies[0] !== company) return [{result:{stale:true}}];
          await __event('mark:' + company);
          marks.push({snapshot:request.args[0], config:request.args[2]});
        }
        return [{result:{scanned:1,marked:0,unknown:0,kept:1}}];
      }}};
    });
    await page.goto(pathToFileURL(path.resolve(__dirname,'../popup.html')).href);
    await page.waitForFunction(()=>document.querySelector('#online-status').textContent.includes('已核查'));
    assert.equal(requests,1);
    assert.ok(events.indexOf('expand:Exact Corp') < events.indexOf('search:Exact Corp'), 'online analysis must expand before network scoring');
    assert.match(await page.locator('#online-results').innerText(),/90分.*L2/);
    await page.locator('#mark').click();
    await page.waitForFunction(()=>document.querySelector('#online-results').textContent.includes('缓存'));
    assert.equal(requests,1);
    await page.evaluate(()=>{company='Next Corp';});
    await page.waitForFunction(()=>document.querySelector('#online-results').textContent.includes('Next Corp'));
    assert.equal(requests,2);
    fail=true;
    await page.locator('#online-refresh').click();
    await page.waitForFunction(()=>document.querySelector('#online-status').textContent.includes('联网服务请求失败'));
    assert.ok(!store['contactout-online-rating-v1'].cache[research.key('Next Corp')]);
    assert.equal(await page.locator('#online-results .card').count(),0,'failed refresh must not display old score');
    fail=false;
    await page.evaluate(()=>{company='Slow Corp';});
    await page.waitForFunction(()=>document.querySelector('#online-status').textContent.includes('正在联网核实 Slow Corp'));
    while (!hold) await new Promise(r=>setTimeout(r,20));
    await page.evaluate(()=>{company='Newest Corp'; marks=[];});
    hold();
    await page.waitForFunction(()=>document.querySelector('#online-results').textContent.includes('Newest Corp'));
    const marks = await page.evaluate(()=>marks);
    assert.ok(marks.length > 0);
    assert.ok(marks.every(m=>m.snapshot.companies[0]==='Newest Corp'), 'stale snapshot never marks the new company page');
    assert.ok(deepRequests >= 2, 'DeepSeek is called only after Tavily returns sources');
    assert.equal(JSON.stringify(store['contactout-visible-export-v1']),original);
    assert.deepEqual(store['contactout-enterprise-ratings-v1'],core.defaultEnterpriseConfig());
    await page.screenshot({path:'/private/tmp/contactout-online-popup.png',fullPage:true});
  } finally {await browser.close();}
});
