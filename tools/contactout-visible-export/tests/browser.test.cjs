const assert = require('node:assert/strict');
const path = require('node:path');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const root = path.resolve(__dirname, '..');
(async () => {
  const browser = await chromium.launch({ headless: true, ...(process.env.TEST_BROWSER_PATH ? { executablePath: process.env.TEST_BROWSER_PATH } : {}) });
  try {
    const page = await browser.newPage();
    // All responses are fixtures. No ContactOut network traffic or account access.
    await page.route('**/*', route => route.fulfill({ contentType: 'text/html', body: `<!doctype html><html><body>
      <header><input type="checkbox">Select all <span>account@example.com</span></header>
      <section><input type="checkbox"><div><h3>Test Person</h3><a href="https://www.linkedin.com/in/test-person">in</a><p>Santa Clara, California, United States</p><p>Technical <b>Staff Engineer</b> at <a href="/company/example">Example Corp</a> in 2012 - Present</p></div><div><span>work@example.com</span><span>a@gmail.com</span><span>b@hotmail.com</span><span>***@me.com</span><span hidden>hidden@gmail.com</span><span style="display:none">displaynone@gmail.com</span><a href="mailto:secret@gmail.com">View email</a><input value="input@gmail.com"><details><summary>More</summary><span>closed@gmail.com</span></details></div></section>
      <section><input type="checkbox"><h3>Masked Person</h3><p>Santa Clara, California</p><p>Staff Engineer at Example Corp in 2020 - Present</p><span>***@example.com</span><span>a***suffix@gmail.com</span></section>
      <section style="margin-top:1200px"><input type="checkbox"><h3>Second Person</h3><p>Santa Clara, California</p><p>Principal Engineer at Example Corp in 2020 - Present</p><span>second@gmail.com</span></section>
      <section style="visibility:hidden"><input type="checkbox"><h3>Invisible Person</h3><p>Hidden</p><span>invisible@gmail.com</span></section>
      <div><span>unmatched@gmail.com</span></div>
    </body></html>` }));
    await page.goto('https://contactout.com/search');
    await page.addScriptTag({ path: path.join(root, 'core.js') });
    await page.addScriptTag({ path: path.join(root, 'collector.js') });
    const result = await page.evaluate(() => collectContactOutVisible());
    assert.equal(result.rows.length, 2, JSON.stringify(result));
    assert.deepEqual(result.rows[0].emails, ['work@example.com', 'a@gmail.com', 'b@hotmail.com']);
    assert.equal(result.rows[0].name, 'Test Person');
    assert.equal(result.rows[0].jobTitle, 'Technical Staff Engineer');
    assert.equal(result.rows[0].company, 'Example Corp');
    assert.equal(result.rows[0].linkedin, 'https://www.linkedin.com/in/test-person');
    assert.deepEqual(result.rows[1].emails, ['second@gmail.com']);
    assert.ok(result.unmatched >= 1);
    const json = JSON.stringify(result.rows);
    for (const email of ['hidden@gmail.com', 'secret@gmail.com', 'input@gmail.com', 'displaynone@gmail.com', 'closed@gmail.com', 'account@example.com', 'invisible@gmail.com']) assert.ok(!json.includes(email), email);
    console.log('PASS: rendered-only extraction, multiple addresses, masked and hidden exclusion, per-person boundaries, offscreen loaded cards.');

    await page.setContent(`<!doctype html><html><body>
      <section id="revealed"><input type="checkbox"><h3>Revealed Researcher</h3><a href="https://www.linkedin.com/in/revealed">in</a><p>Senior Chemist at Materials Corp in 2019 - Present</p><span>researcher@example.com</span><span>personal@example.com</span><button class="more">...more</button><p hidden class="details">PhD in Polymer Chemistry; Die attach film research</p><button class="reveal">View email</button><button class="phone">View phone</button><a href="mailto:hidden-address@example.com">Contact</a></section>
      <section id="masked"><input type="checkbox"><h3>Masked Researcher</h3><p>Scientist at Materials Corp in 2020 - Present</p><span>***@example.com</span><button class="more">Show more</button><button class="reveal">View email</button></section>
      <section id="expanded"><input type="checkbox"><h3>Expanded Researcher</h3><p>Principal Scientist at Materials Corp in 2018 - Present</p><span>expanded@example.com</span><button class="more">Show less</button><p>Already visible research history</p></section>
      <section id="offscreen" style="margin-top:1500px"><input type="checkbox"><h3>Offscreen Researcher</h3><a href="https://www.linkedin.com/in/offscreen">in</a><p>Staff Engineer at Materials Corp in 2017 - Present</p><span>offscreen@example.com</span><button class="more">Show more</button><p hidden class="details">Advanced packaging development</p></section>
      <section id="failed"><input type="checkbox"><h3>Failed Researcher</h3><p>Scientist at Materials Corp in 2021 - Present</p><span>failed@example.com</span><button class="more">...more</button></section>
    </body></html>`);
    await page.evaluate(() => {
      globalThis.clicks = [];
      document.addEventListener('click', event => {
        const button = event.target.closest('button');
        if (!button) return;
        const card = button.closest('section');
        clicks.push(card.id + ':' + button.className);
        if (!['revealed', 'offscreen'].includes(card.id) || button.className !== 'more') return;
        // Simulate asynchronous rendering and replacement of the card by React.
        setTimeout(() => {
          const replacement = card.cloneNode(true);
          replacement.querySelector('.details').hidden = false;
          replacement.querySelector('.more').textContent = 'Show less';
          card.replaceWith(replacement);
        }, 120);
      });
    });
    const expanded = await page.evaluate(() => expandAndCollectContactOutVisible());
    assert.equal(expanded.rows.length, 4);
    assert.equal(expanded.expanded, 2);
    assert.equal(expanded.expansionFailed, 1);
    assert.match(expanded.rows.find(row => row.name === 'Revealed Researcher').profileText, /PhD in Polymer Chemistry/);
    assert.match(expanded.rows.find(row => row.name === 'Offscreen Researcher').profileText, /Advanced packaging development/);
    assert.deepEqual(expanded.rows[0].emails, ['researcher@example.com', 'personal@example.com']);
    assert.deepEqual(await page.evaluate(() => clicks), ['revealed:more', 'offscreen:more', 'failed:more']);
    await page.evaluate(() => document.getElementById('failed').remove());
    const repeated = await page.evaluate(() => expandAndCollectContactOutVisible());
    assert.equal(repeated.expanded, 0);
    assert.equal(repeated.expansionFailed, 0);
    assert.deepEqual(await page.evaluate(() => clicks), ['revealed:more', 'offscreen:more', 'failed:more']);
    console.log('PASS: revealed-email-only expansion, async card replacement, offscreen details, failed-expansion reporting, no email/phone reveal clicks, no collapsing on recapture.');

    await page.setContent(`<!doctype html><html><body>
      <section id="review-open"><input type="checkbox"><h3>Review Open</h3><a href="https://www.linkedin.com/in/review-open">in</a><p>Junior Engineer at Example Corp in 2024 - Present</p><button class="more">...more</button><p hidden class="history">Principal Scientist at Example Corp in 2018 - 2024</p><button class="email">View email</button><button class="phone">View phone</button><button class="ai">AI write personalized message</button></section>
      <section id="review-fail"><input type="checkbox"><h3>Review Fail</h3><a href="https://www.linkedin.com/in/review-fail">in</a><p>Scientist at Example Corp in 2024 - Present</p><button class="more">Show more</button><button class="email">View email</button></section>
      <section id="review-ready"><input type="checkbox"><h3>Review Ready</h3><a href="https://www.linkedin.com/in/review-ready">in</a><p>Senior Chemist at Example Corp in 2024 - Present</p><button class="email">View email</button></section>
    </body></html>`);
    await page.evaluate(() => {
      globalThis.analysisClicks = [];
      document.addEventListener('click', event => {
        const button = event.target.closest('button');
        if (!button) return;
        const card = button.closest('section');
        analysisClicks.push(card.id + ':' + button.className);
        if (card.id !== 'review-open' || button.className !== 'more') return;
        setTimeout(() => {
          const replacement = card.cloneNode(true);
          replacement.querySelector('.history').hidden = false;
          replacement.querySelector('.more').textContent = 'Show less';
          card.replaceWith(replacement);
        }, 120);
      });
    });
    const reviewExpansion = await page.evaluate(() => expandContactOutReviewDetails());
    assert.deepEqual(reviewExpansion, { scanned: 3, expanded: 1, expansionFailed: 1 });
    assert.match(await page.locator('#review-open').innerText(), /Principal Scientist/);
    assert.deepEqual(await page.evaluate(() => analysisClicks), ['review-open:more', 'review-fail:more']);
    console.log('PASS: analysis expands all visible review cards, survives React replacement, and clicks only more controls without requiring email visibility.');

    const card = (id, jobs, options = '') => `<section id="${id}"><input type="checkbox"><h3>Person ${id}</h3><a href="https://www.linkedin.com/in/${id}">in</a><p>Boston, Massachusetts</p>${jobs.map(job => `<div><p>${job}</p></div>`).join('')}<p>${options}</p><span>***@example.com</span><button>View email</button></section>`;
    await page.setContent('<html><head></head><body><header><input type="checkbox">Data Scientist filter</header>' + [
      card('data', ['Senior <b>Data Scientist</b> at Example Corp in 2024 - Present']),
      card('senior', ['Senior Scientist II at Example Corp in 2024 - Present']),
      card('past', ['Data Scientist at Example Corp in 2020 - 2023', 'Principal Scientist at Example Corp in 2023 - Present']),
      card('mixed', ['Data Scientist at Example Corp in 2024 - Present', 'Senior Chemist at Example Corp in 2023 - Present']),
      card('partial', ['Data Scientist at Example Corp in 2024 - Present', 'Senior Chemist - Present']),
      card('associate', ['Associate Scientist at Example Corp in 2024 - Present', 'Senior Research Chemist at Example Corp in 2015 - 2024']),
      card('skills', ['Senior Materials Engineer at Example Corp in 2024 - Present'], 'Skills: Data Scientist, Fragrance Scientist'),
      card('operator', ['Manufacturing Operator III at Example Corp in 2024 - Present'], 'operator@example.com'),
      card('unknown', ['Scientist at Example Corp']),
      card('patent', ['Senior Information Scientist at Example Corp in 2024 - Present', 'Expert Patent Information at Example Corp in 2020 - 2024']),
      card('hair', ['Scientist - Hair Care Formulation at Example Corp - Present'])
    ].join('') + '</body></html>');
    await page.evaluate(() => { globalThis.reviewClicks = 0; document.addEventListener('click', () => reviewClicks++); });
    const legacy = { schemaVersion: 1, scope: '旧版方向', revision: 'v1', rules: [
      { id: 'old', enabled: true, reason: '旧排除', currentTitleEquals: ['Senior Data Scientist', 'Data Scientist', 'Manufacturing Operator III', 'Scientist - Hair Care Formulation'] },
      { id: 'patent', enabled: true, reason: '专利信息', currentTitleEquals: ['Senior Information Scientist'], pastTitleEqualsAny: ['Expert Patent Information'] }
    ] };
    const review = await page.evaluate(config => markContactOutIgnored(config), legacy);
    assert.equal(review.marked, 4, JSON.stringify(review));
    assert.deepEqual(await page.locator('[data-contactout-ignore]').evaluateAll(cards => cards.map(card => card.id).sort()), ['data', 'hair', 'operator', 'patent']);
    const exportWhileMarked = await page.evaluate(() => collectContactOutVisible());
    assert.equal(exportWhileMarked.rows.length, 1);
    assert.equal(exportWhileMarked.rows[0].name, 'Person operator');
    assert.ok(!exportWhileMarked.rows[0].profileText.includes('本轮可忽略'));
    await page.evaluate(config => {
      document.querySelector('#data div p').textContent = 'Senior Chemist at Example Corp in 2025 - Present';
      markContactOutIgnored(config);
    }, legacy);
    await page.waitForFunction(() => !document.getElementById('data').hasAttribute('data-contactout-ignore'));
    const custom = await page.evaluate(() => {
      const config = { schemaVersion: 1, scope: '自定义方向', revision: 'v1' };
      config.rules = [{ id: 'test', enabled: true, reason: '<img src=x onerror=alert(1)>', currentTitleEquals: ['Senior Chemist'] }];
      return markContactOutIgnored(config);
    });
    assert.equal(custom.marked, 1);
    assert.deepEqual(await page.locator('[data-contactout-ignore]').evaluateAll(cards => cards.map(card => card.id)), ['data']);
    assert.equal(await page.locator('[data-contactout-review-badge]').count(), 0, 'Review marks never inject explanatory UI into ContactOut cards');
    assert.equal(await page.evaluate(() => reviewClicks), 0);
    await page.evaluate(() => clearContactOutIgnoreMarks());
    assert.equal(await page.locator('[data-contactout-review-badge],[data-contactout-ignore]').count(), 0);
    console.log('PASS: masked-email review, exact title rules, past-role/skills/multiple-current safeguards, recycled-card cleanup, custom config and plain-text reasons, no annotation leakage into exports, no clicks.');

    await page.setContent('<html><head></head><body>' + [
      card('senior', ['Senior Fragrance Scientist at Example Corp in 2024 - Present']),
      card('junior', ['Junior Engineer at Example Corp in 2024 - Present'], 'junior@example.com'),
      card('ordinary', ['Scientist III at Example Corp in 2024 - Present'], 'ordinary@example.com; second@example.com'),
      card('history', ['Sales Manager at Example Corp in 2024 - Present', 'Senior Process Engineer at Example Corp in 2020 - 2024']),
      card('collapsed', ['Junior Engineer at Example Corp in 2024 - Present'], '<button class="more">Show more</button><span hidden class="history">Senior Scientist at Example Corp in 2020 - 2024</span>'),
      card('mixed', ['Junior Engineer at Example Corp in 2024 - Present', 'Scientist at Example Corp in 2023 - Present']),
      card('skills', ['Junior Engineer at Example Corp in 2024 - Present'], 'Skills: Senior Scientist, Principal Engineer'),
      card('partial', ['Junior Engineer at Example Corp in 2024 - Present', 'Senior Chemist - 2020']),
      card('undated', ['Junior Engineer at Example Corp in 2024 - Present', 'Scientist at Example Corp']),
      card('unknown', ['Scientist at Example Corp'])
    ].join('') + '</body></html>');
    const originalReviewBackgrounds = await page.evaluate(() => Object.fromEntries(['junior', 'ordinary'].map(id => [id, getComputedStyle(document.getElementById(id)).backgroundColor])));
    await page.evaluate(() => { globalThis.reviewClicks = 0; document.addEventListener('click', () => reviewClicks++); });
    const threeState = await page.evaluate(() => markContactOutIgnored());
    assert.deepEqual(threeState, { scanned: 10, marked: 2, unknown: 6, kept: 2 });
    assert.deepEqual(await page.locator('[data-contactout-ignore]').evaluateAll(cards => cards.map(c => c.id).sort()), ['junior', 'skills']);
    assert.deepEqual(await page.locator('[data-contactout-review="review"]').evaluateAll(cards => cards.map(c => c.id).sort()), ['collapsed', 'mixed', 'ordinary', 'partial', 'undated', 'unknown']);
    assert.equal(await page.locator('#junior').evaluate(c => getComputedStyle(c).backgroundColor), originalReviewBackgrounds.junior);
    assert.equal(await page.locator('#ordinary').evaluate(c => getComputedStyle(c).backgroundColor), originalReviewBackgrounds.ordinary);
    assert.equal(await page.locator('#junior').evaluate(c => getComputedStyle(c).outlineColor), 'rgb(220, 38, 38)');
    assert.equal(await page.locator('#ordinary').evaluate(c => getComputedStyle(c).outlineColor), 'rgb(217, 119, 6)');
    assert.equal(await page.locator('[data-contactout-review-badge]').count(), 0);
    await page.screenshot({ path: '/private/tmp/contactout-three-state-review.png', fullPage: true });
    const coloredExport = await page.evaluate(() => collectContactOutVisible());
    assert.equal(coloredExport.rows.length, 2);
    assert.deepEqual(coloredExport.rows.find(r => r.name === 'Person ordinary').emails, ['ordinary@example.com', 'second@example.com']);
    assert.ok(!/待核实（|可忽略候选（/.test(JSON.stringify(coloredExport.rows)));
    await page.evaluate(() => {
      document.querySelector('#collapsed .history').hidden = false;
      document.querySelector('#collapsed .more').textContent = 'Show less';
      document.querySelector('#ordinary div p').textContent = 'Principal Scientist at Example Corp in 2024 - Present';
      document.querySelector('#junior div p').textContent = 'Scientist at Example Corp in 2024 - Present';
      markContactOutIgnored();
    });
    await page.waitForFunction(() => !document.querySelector('#collapsed[data-contactout-ignore],#collapsed[data-contactout-review],#ordinary[data-contactout-ignore],#ordinary[data-contactout-review]') && document.querySelector('#junior[data-contactout-review="review"]'));
    assert.equal(await page.locator('#junior[data-contactout-ignore]').count(), 0);
    await page.evaluate(() => markContactOutIgnored());
    assert.equal(await page.locator('#junior [data-contactout-review-badge]').count(), 0);
    assert.equal(await page.evaluate(() => reviewClicks), 0);
    await page.evaluate(() => clearContactOutIgnoreMarks());
    assert.equal(await page.locator('[data-contactout-review-badge],[data-contactout-ignore],[data-contactout-review],#contactout-review-style').count(), 0);
    assert.equal(await page.evaluate(() => __contactoutReviewState), null);
    console.log('PASS: generic three-state, hidden/malformed history, historical protection, skills isolation, color styles, DOM reuse, export cleanliness and zero review clicks.');

    await page.setContent('<html><head></head><body>' + [
      card('g500', ['Engineer at Global Corp in 2024 - Present']),
      card('score100-low', ['Engineer at Score 100 Corp in 2024 - Present']),
      card('score100-ok', ['Senior Scientist at Score 100 Corp in 2024 - Present']),
      card('score70-low', ['Senior Engineer at Score 70 Corp in 2024 - Present']),
      card('score70-ok', ['Staff Engineer at Score 70 Corp in 2024 - Present']),
      card('score50-low', ['Principal Engineer at Score 50 Corp in 2024 - Present']),
      card('score50-ok', ['Chief Engineer at Score 50 Corp in 2024 - Present']),
      card('unlisted', ['Staff Engineer at Unknown Corp in 2024 - Present']),
      card('cross', ['Sales Manager at Global Corp in 2024 - Present', 'Engineer at Score 50 Corp in 2020 - 2023']),
      card('history-ok', ['Sales Manager at Unknown Corp in 2024 - Present', 'Staff Engineer at Score 70 Corp in 2020 - 2023'])
    ].join('') + '</body></html>');
    const enterpriseResult = await page.evaluate(() => {
      const company = (id, name, developedEconomy, global500, highSalaryIndustryPoints, priorityIndustryPoints) => ({
        id, name, aliases: [], headquartersCountry: developedEconomy ? 'Advanced' : 'Other', developedEconomy, global500,
        ...(global500 ? { global500Year: 2026 } : {}), highSalaryIndustryPoints, priorityIndustryPoints,
        priorityIndustry: priorityIndustryPoints ? '重点产业' : '非重点产业', checkedAt: '2026-09-19', sources: ['https://example.com/' + id]
      });
      const enterprises = { schemaVersion: 1, revision: 'browser-test', companies: [
        company('g500', 'Global Corp', true, true, 0, 0),
        company('score100', 'Score 100 Corp', true, false, 20, 60),
        company('score70', 'Score 70 Corp', true, false, 10, 40),
        company('score50', 'Score 50 Corp', false, false, 0, 40)
      ] };
      globalThis.enterpriseReviewClicks = 0;
      document.addEventListener('click', () => enterpriseReviewClicks++);
      return markContactOutIgnored(ContactOutExport.defaultIgnoreConfig(), enterprises);
    });
    assert.deepEqual(enterpriseResult, { scanned: 10, marked: 4, unknown: 1, kept: 5 });
    assert.deepEqual(await page.locator('[data-contactout-ignore]').evaluateAll(cards => cards.map(c => c.id).sort()), ['cross', 'score100-low', 'score50-low', 'score70-low']);
    assert.deepEqual(await page.locator('[data-contactout-review="review"]').evaluateAll(cards => cards.map(c => c.id)), ['unlisted']);
    assert.equal(await page.locator('[data-contactout-review-badge]').count(), 0);
    assert.equal(await page.locator('#score100-low').evaluate(c => getComputedStyle(c).backgroundColor), 'rgba(0, 0, 0, 0)');
    assert.equal(await page.locator('#unlisted').evaluate(c => getComputedStyle(c).backgroundColor), 'rgba(0, 0, 0, 0)');
    assert.equal(await page.locator('#score100-low').evaluate(c => getComputedStyle(c).outlineColor), 'rgb(220, 38, 38)');
    assert.equal(await page.locator('#unlisted').evaluate(c => getComputedStyle(c).outlineColor), 'rgb(217, 119, 6)');
    assert.equal(await page.evaluate(() => enterpriseReviewClicks), 0);
    await page.evaluate(() => clearContactOutIgnoreMarks());
    console.log('PASS: enterprise score gates, Global 500 distinction, same-job evidence, history qualification, unknown company review and zero clicks.');

    await page.evaluate(() => {
      const render = id => `<section id="${id}"><input type="checkbox"><h3>${id}</h3><a href="https://www.linkedin.com/in/${id}">in</a><p>R&D Scientist at Henkel in 2025 - Present</p><button class="more">Show more</button><p hidden class="history">Senior R&D Scientist at Unlisted History Corp in 2020 - 2024</p><button class="email">View email</button><button class="phone">View phone</button><button class="ai">AI write personalized message</button></section>`;
      document.body.innerHTML = render('auto-one');
      globalThis.autoClicks = [];
      document.addEventListener('click', event => {
        const button = event.target.closest('button');
        if (!button) return;
        const card = button.closest('section');
        autoClicks.push(card.id + ':' + button.className);
        if (button.className !== 'more') return;
        setTimeout(() => { card.querySelector('.history').hidden = false; button.textContent = 'Show less'; }, 80);
      });
      const enterprise = { schemaVersion:1, revision:'henkel', companies:[{ id:'henkel', name:'Henkel', aliases:[], headquartersCountry:'Germany', developedEconomy:true, global500:false, highSalaryIndustryPoints:10, priorityIndustryPoints:0, priorityIndustry:'先进材料', checkedAt:'2026-09-19', sources:['https://www.henkel.com/company'] }] };
      globalThis.autoStore = {
        'contactout-ignore-rules-v1': ContactOutExport.defaultIgnoreConfig(),
        'contactout-enterprise-ratings-v1': enterprise,
        'contactout-online-rating-v1': { enabled:false }
      };
      globalThis.autoListeners = [];
      globalThis.chrome = { storage:{ local:{ get:async keys => Object.fromEntries(keys.map(key => [key, autoStore[key]])) }, onChanged:{ addListener:listener => autoListeners.push(listener) } } };
      const original = globalThis.markContactOutIgnored;
      globalThis.autoMarks = 0;
      globalThis.markContactOutIgnored = (...args) => { autoMarks++; return original(...args); };
      globalThis.renderAuto = id => { document.body.innerHTML = render(id); };
      globalThis.emitAutoStorage = key => autoListeners.forEach(listener => listener({ [key]: { newValue:autoStore[key] } }, 'local'));
    });
    await page.addScriptTag({ path: path.join(root, 'autopilot.js') });
    await page.waitForFunction(() => document.getElementById('auto-one')?.hasAttribute('data-contactout-ignore'), null, { timeout: 5000 });
    assert.equal(await page.locator('#auto-one [data-contactout-review-badge]').count(), 0);
    assert.deepEqual(await page.evaluate(() => autoClicks), ['auto-one:more']);
    await page.evaluate(() => {
      globalThis.autoAttributeMutations = 0;
      new MutationObserver(records => { autoAttributeMutations += records.length; }).observe(document.querySelector('#auto-one'), { attributes:true, attributeFilter:['data-contactout-ignore','data-contactout-review'] });
    });
    await page.evaluate(() => { emitAutoStorage('contactout-enterprise-ratings-v1'); });
    await page.waitForFunction(() => autoMarks >= 2);
    assert.equal(await page.evaluate(() => autoAttributeMutations), 0, 'unchanged conclusions do not clear and repaint border state');
    assert.deepEqual(await page.evaluate(() => autoClicks), ['auto-one:more'], 'saved-config changes reapply markers but do not collapse/reclick more');
    const marksBeforeTextChange = await page.evaluate(() => autoMarks);
    await page.evaluate(() => { document.querySelector('#auto-one p').textContent = 'Chief Engineer at Henkel in 2025 - Present'; });
    await page.waitForFunction(previous => autoMarks > previous && !document.querySelector('#auto-one[data-contactout-ignore],#auto-one[data-contactout-review]'), marksBeforeTextChange);
    await page.evaluate(() => renderAuto('auto-two'));
    await page.waitForFunction(() => document.getElementById('auto-two')?.hasAttribute('data-contactout-ignore'));
    assert.deepEqual(await page.evaluate(() => autoClicks), ['auto-one:more', 'auto-two:more']);
    await page.evaluate(() => { autoStore['contactout-online-rating-v1'] = { enabled:true }; renderAuto('auto-online'); emitAutoStorage('contactout-online-rating-v1'); });
    await page.waitForTimeout(1700);
    assert.equal(await page.locator('#auto-online[data-contactout-ignore],#auto-online[data-contactout-review]').count(), 0, 'manual autopilot is disabled when online scoring is enabled');
    assert.deepEqual(await page.evaluate(() => autoClicks), ['auto-one:more', 'auto-two:more'], 'autopilot never clicks email, phone, AI or pagination controls');
    console.log('PASS: manual enterprise autopilot loads saved rules, prioritizes the current scored employer, re-applies on storage change, expands new pagination results, and stops in online mode.');
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
