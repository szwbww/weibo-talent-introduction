const assert = require('node:assert/strict');
const path = require('node:path');
const { pathToFileURL } = require('node:url');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const { defaultIgnoreConfig, defaultEnterpriseConfig } = require('../core.js');

(async () => {
  const browser = await chromium.launch({ headless: true, ...(process.env.TEST_BROWSER_PATH ? { executablePath: process.env.TEST_BROWSER_PATH } : {}) });
  try {
    const page = await browser.newPage({ viewport: { width: 500, height: 950 } });
    const key = 'contactout-visible-export-v1';
    const rulesKey = 'contactout-ignore-rules-v1';
    const enterprisesKey = 'contactout-enterprise-ratings-v1';
    const onlineKey = 'contactout-online-rating-v1';
    const originalRows = [{ name: 'Existing Person', company: 'Example Corp', jobTitle: 'Senior Chemist', emails: ['test@example.com'] }];
    const legacy = { schemaVersion: 1, scope: '旧配置', revision: 'legacy', rules: [] };
    const enterpriseConfig = { schemaVersion: 1, revision: 'saved-enterprises', companies: [{
      id: 'example', name: 'Example Corp', aliases: ['Example Corporation'], headquartersCountry: 'United States',
      developedEconomy: true, global500: false, highSalaryIndustryPoints: 20, priorityIndustryPoints: 60,
      priorityIndustry: '半导体先进材料', checkedAt: '2026-09-19', sources: ['https://example.com/evidence']
    }] };
    const onlineConfig = { enabled: false, deepseekApiKey: 'deepseek-test', tavilyApiKey: 'tavily-test', cache: {} };
    const storage = { [key]: originalRows, [rulesKey]: legacy, [enterprisesKey]: enterpriseConfig, [onlineKey]: onlineConfig };
    await page.exposeFunction('__get', keys => Object.fromEntries((Array.isArray(keys) ? keys : [keys]).map(key => [key, storage[key]])));
    await page.exposeFunction('__set', values => Object.assign(storage, values));
    await page.exposeFunction('__remove', key => { delete storage[key]; });
    await page.addInitScript(() => {
      globalThis.chrome = {
        storage: { local: { get: keys => __get(keys), set: values => __set(values), remove: key => __remove(key) } },
        tabs: { query: async () => [{ id: 1, url: 'https://example.test/' }] }
      };
    });
    await page.goto(pathToFileURL(path.resolve(__dirname, '../popup.html')).href);
    await page.waitForFunction(() => !document.getElementById('mark').disabled);
    assert.equal(await page.locator('#people').innerText(), '1 人');
    assert.equal(await page.locator('#mark').innerText(), '标记职级（红／黄）');
    assert.match(await page.locator('#status').innerText(), /旧版岗位规则/);
    assert.deepEqual(JSON.parse(await page.locator('#rules-json').inputValue()), legacy);
    assert.deepEqual(JSON.parse(await page.locator('#enterprise-json').inputValue()), enterpriseConfig);
    assert.equal(await page.locator('#online-deepseek-key').getAttribute('type'), 'password');
    assert.equal(await page.locator('#online-tavily-key').getAttribute('type'), 'password');
    assert.equal(await page.locator('#online-deepseek-key').inputValue(), 'deepseek-test');
    assert.equal(await page.locator('#online-tavily-key').inputValue(), 'tavily-test');
    for (const id of ['enterprise-json', 'save-enterprises', 'reset-enterprises', 'export-enterprises', 'import-enterprises', 'enterprise-file']) assert.equal(await page.locator('#' + id).count(), 1, id);
    await page.locator('#rules-panel summary').click();
    await page.locator('#reset-rules').click();
    assert.equal(JSON.parse(await page.locator('#rules-json').inputValue()).schemaVersion, 3);
    assert.deepEqual(storage[rulesKey], legacy);
    await page.reload();
    await page.waitForFunction(() => !document.getElementById('mark').disabled);
    assert.deepEqual(JSON.parse(await page.locator('#rules-json').inputValue()), legacy);
    await page.locator('#rules-panel summary').click();
    await page.locator('#rules-json').fill('{"schemaVersion":9}');
    await page.locator('#save-rules').click();
    await page.waitForFunction(() => document.getElementById('status').textContent.includes('schemaVersion'));
    assert.deepEqual(storage[rulesKey], legacy);
    assert.deepEqual(storage[key], originalRows);
    const config = defaultIgnoreConfig();
    config.scope = '自定义材料项目';
    config.revision = 'v3';
    config.rules = [{ id: 'operator', enabled: true, reason: '生产操作', currentTitleEquals: ['Manufacturing Operator III'] }];
    await page.locator('#rules-json').fill(JSON.stringify(config));
    await page.locator('#save-rules').click();
    await page.waitForFunction(() => document.getElementById('status').textContent.includes('规则已保存'));
    assert.deepEqual(storage[rulesKey], config);
    assert.deepEqual(storage[key], originalRows);
    await page.reload();
    await page.waitForFunction(() => !document.getElementById('mark').disabled);
    assert.deepEqual(JSON.parse(await page.locator('#rules-json').inputValue()), config);
    await page.locator('#rules-panel summary').click();
    await page.locator('#reset-rules').click();
    assert.deepEqual(storage[rulesKey], config, 'Reset fills editor only, until saved');
    await page.locator('#rules-file').setInputFiles({ name: 'updated.json', mimeType: 'application/json', buffer: Buffer.from(JSON.stringify({ ...config, revision: 'v3-unsaved' })) });
    await page.waitForFunction(() => document.getElementById('status').textContent.includes('配置已导入'));
    assert.deepEqual(storage[rulesKey], config, 'Import requires explicit save');
    assert.equal(JSON.parse(await page.locator('#rules-json').inputValue()).revision, 'v3-unsaved');
    assert.deepEqual(storage[key], originalRows);
    await page.locator('#reset-enterprises').click();
    assert.deepEqual(JSON.parse(await page.locator('#enterprise-json').inputValue()), defaultEnterpriseConfig());
    assert.deepEqual(storage[enterprisesKey], enterpriseConfig, 'Enterprise reset fills editor only');
    await page.locator('#enterprise-file').setInputFiles({ name: 'bad.json', mimeType: 'application/json', buffer: Buffer.from(JSON.stringify({ ...enterpriseConfig, companies: [{ ...enterpriseConfig.companies[0], highSalaryIndustryPoints: 15 }] })) });
    await page.waitForFunction(() => document.getElementById('status').textContent.includes('企业评分导入失败'));
    assert.deepEqual(storage[enterprisesKey], enterpriseConfig);
    const updatedEnterprises = { ...enterpriseConfig, revision: 'enterprise-v2' };
    await page.locator('#enterprise-file').setInputFiles({ name: 'enterprise.json', mimeType: 'application/json', buffer: Buffer.from(JSON.stringify(updatedEnterprises)) });
    await page.waitForFunction(() => document.getElementById('status').textContent.includes('企业评分已导入编辑框'));
    assert.deepEqual(storage[enterprisesKey], enterpriseConfig, 'Enterprise import requires explicit save');
    await page.locator('#save-enterprises').click();
    await page.waitForFunction(() => document.getElementById('status').textContent.includes('企业评分已保存'));
    assert.deepEqual(storage[enterprisesKey], updatedEnterprises);
    assert.deepEqual(storage[key], originalRows);
    await page.screenshot({ path: '/private/tmp/contactout-popup-review.png', fullPage: true });
    await page.evaluate(() => {
      chrome.tabs.query = async () => [{ id: 1, url: 'https://contactout.com/search' }];
      chrome.scripting = { executeScript: async request => {
        if (request.files) return [];
        globalThis.lastMarkArgs = request.args;
        return [{ result: { scanned: 10, kept: 2, marked: 2, unknown: 6 } }];
      } };
    });
    await page.locator('#mark').click();
    await page.waitForFunction(() => document.getElementById('status').textContent.includes('黄色待核实 6'));
    assert.match(await page.locator('#status').innerText(), /保留 2 人，红色 2 人/);
    assert.equal(await page.evaluate(() => lastMarkArgs[1].revision), 'v3', 'Unsaved imported revision must not apply');
    assert.equal(await page.evaluate(() => lastMarkArgs[2].revision), 'enterprise-v2');
    const downloadEvent = page.waitForEvent('download');
    await page.locator('#export-rules').click();
    const download = await downloadEvent;
    const stream = await download.createReadStream();
    let downloaded = '';
    for await (const chunk of stream) downloaded += chunk.toString();
    assert.deepEqual(JSON.parse(downloaded), config, 'Export saved config, not unsaved editor');
    const enterpriseDownloadEvent = page.waitForEvent('download');
    await page.locator('#export-enterprises').click();
    const enterpriseDownload = await enterpriseDownloadEvent;
    const enterpriseStream = await enterpriseDownload.createReadStream();
    let downloadedEnterprises = '';
    for await (const chunk of enterpriseStream) downloadedEnterprises += chunk.toString();
    assert.deepEqual(JSON.parse(downloadedEnterprises), updatedEnterprises);
    await page.locator('#clear').click();
    assert.deepEqual(storage[key], originalRows, 'First clear click only confirms');
    await page.locator('#clear').click();
    await page.waitForFunction(() => document.getElementById('people').textContent === '0 人');
    assert.equal(storage[key], undefined);
    assert.deepEqual(storage[rulesKey], config, 'Clearing experts does not clear rules');
    assert.deepEqual(storage[enterprisesKey], updatedEnterprises, 'Clearing experts does not clear enterprise ratings');
    assert.deepEqual(storage[onlineKey], onlineConfig, 'Clearing experts does not clear online settings/cache');
    console.log('PASS: popup v1 preservation, v3 and enterprise explicit save, direct-key storage isolation, validation/reload/import/reset/export, and confirmed expert-only clear.');
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
