const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const base = path.join(__dirname, '../../main/resources/static');
const html = fs.readFileSync(path.join(base, 'index.html'), 'utf8');
const css = fs.readFileSync(path.join(base, 'styles.css'), 'utf8');
const source = fs.readFileSync(path.join(base, 'discovery-paper-reprocess.js'), 'utf8');
const plan = fs.readFileSync(path.join(__dirname, '../../../docs/plans/2026-11-01/paper-reprocess-05-frontend.md'), 'utf8');
const flush = async () => { for (let i = 0; i < 18; i++) await new Promise(resolve => setImmediate(resolve)); };

// Parse the real S-6 source. Missing selectors stay missing (no universal DOM stubs).
function dom() {
    const document = { activeElement: null };
    class Element {
        constructor(tag) { this.tagName = tag.toUpperCase(); this.children = []; this.parentElement = null; this.ownerDocument = document; this.attrs = {}; this.dataset = {}; this.listeners = {}; this.hidden = false; this.disabled = false; this.checked = false; this.open = false; this._text = ''; }
        set className(v) { this.attrs.class = v; }
        get className() { return this.attrs.class || ''; }
        set textContent(v) { this.children = []; this._text = String(v ?? ''); }
        get textContent() { return this._text + this.children.map(c => c.textContent).join(''); }
        get value() { return this._value ?? this.attrs.value ?? ''; }
        set value(v) { this._value = v; }
        setAttribute(k, v) { this.attrs[k] = String(v); if (k.startsWith('data-')) this.dataset[k.slice(5).replace(/-([a-z])/g, (_, c) => c.toUpperCase())] = String(v); if (['hidden', 'disabled', 'checked'].includes(k)) this[k] = true; }
        removeAttribute(k) { delete this.attrs[k]; }
        getAttribute(k) { return this.attrs[k] ?? null; }
        appendChild(child) { if (child.tagName === '#FRAGMENT') { [...child.children].forEach(c => this.appendChild(c)); return child; } child.parentElement = this; this.children.push(child); return child; }
        replaceChildren(...children) { this.children = []; this._text = ''; children.forEach(c => this.appendChild(c)); }
        cloneNode(deep) { const el = new Element(this.tagName); for (const [k,v] of Object.entries(this.attrs)) el.setAttribute(k,v); el._text = this._text; if (deep) this.children.forEach(c => el.appendChild(c.cloneNode(true))); return el; }
        matches(selector) {
            if (selector.includes(',')) return selector.split(',').some(s => this.matches(s));
            const checked = selector.endsWith(':checked'); if (checked) selector = selector.slice(0, -8);
            const tag = selector.match(/^[\w-]+/), id = selector.match(/#([\w-]+)/), cls = selector.match(/\.([\w-]+)/);
            if (tag && this.tagName !== tag[0].toUpperCase() || id && this.attrs.id !== id[1] || cls && !this.className.split(/\s+/).includes(cls[1])) return false;
            for (const a of selector.matchAll(/\[([\w-]+)(?:="([^"]*)")?\]/g)) { const v = a[1].startsWith('data-') ? this.dataset[a[1].slice(5).replace(/-([a-z])/g, (_, c) => c.toUpperCase())] : this.attrs[a[1]]; if (v === undefined || a[2] !== undefined && v !== a[2]) return false; }
            return !checked || this.checked;
        }
        querySelectorAll(s) { return this.children.flatMap(c => [...(c.matches(s) ? [c] : []), ...c.querySelectorAll(s)]); }
        querySelector(s) { return this.querySelectorAll(s)[0] || null; }
        closest(s) { return this.matches(s) ? this : this.parentElement?.closest(s) || null; }
        addEventListener(type, fn) { (this.listeners[type] ??= new Set()).add(fn); }
        removeEventListener(type, fn) { this.listeners[type]?.delete(fn); }
        dispatch(type, props = {}) { const event = { target: this, preventDefault() { this.prevented = true; }, stopPropagation() { this.stopped = true; }, ...props }; let el = this; do { event.currentTarget = el; [...(el.listeners[type] || [])].forEach(fn => fn(event)); el = event.stopped ? null : el.parentElement; } while (el); return event; }
        click() { if (!this.disabled) this.dispatch('click'); }
        focus() { document.activeElement = this; }
        showModal() { this.open = true; this.focus(); }
        close() { this.open = false; this.dispatch('close'); }
    }
    document.createElement = tag => new Element(tag);
    document.body = new Element('body');
    const stack = [document.body], start = html.indexOf('<div id="taskProgressModal"'), end = html.indexOf('</template>', html.indexOf('<template id="rpTraceTemplate"', start)) + 11;
    for (const token of html.slice(start, end).match(/<!--[\s\S]*?-->|<[^>]+>|[^<]+/g)) {
        if (token.startsWith('<!--')) continue;
        if (token.startsWith('</')) { stack.pop(); continue; }
        if (token.startsWith('<')) { const tag = token.match(/^<([\w-]+)/)[1], el = new Element(tag); for (const a of token.slice(tag.length + 1, -1).matchAll(/([\w-]+)(?:="([^"]*)")?/g)) el.setAttribute(a[1], a[2] ?? ''); stack.at(-1).appendChild(el); if (tag === 'template') { el.content = new Element('#fragment'); stack.push(el.content); } else if (!['input','br','hr','img','meta','link','source'].includes(tag)) stack.push(el); }
        else stack.at(-1)._text += token;
    }
    return document;
}
const run = (id = 7001, source = 9001, state = 'RUNNING', mode = 'AUDIT') => ({ executionId: id, sourceExecutionId: source, mode, state, phase: 'PARSE', revision: 3, scope: { codeVersion: 'v1', parserVersions: { pdf: 2 } }, createdAt: '2026-11-02', updatedAt: '2026-11-02' });
const summary = { papers: 121, paperComplete: 42, paperErrors: 0, paperCancelled: 0, targets: 121, targetErrors: 0, actualNew: 0, actualUpdated: 0, actualAdmitted: 0, pendingVerification: 0, wouldCreate: null, wouldRepair: null, unchanged: 0, conflict: 1, protected: 0, syncFailure: 0, missingSourcePapers: 0, targetTotalKnown: false, qualification: { notMet: 0, unknown: 0, conflict: 0, notEvaluated: 0, unavailable: 121 } };
function paper(id = 1) { return { id, kind: 'PAPER', input: { recordId: id, paperKey: 'W' + id, metadata: { title: '<img src=x onerror=alert(1)>' }, first: { outcome: { emails: [{ givenNames: 'Alice', familyNames: 'Smith' }] } }, bodies: [{ attemptId: 100 + id }] }, result: { analysis: { after: [{ authorName: 'Alice Smith' }, { authorName: 'Bob Jones' }], deltaAddedBindings: 1, coverage: { scannedPages: 2 } }, trace: [{ stepNo: 1, state: 'SUCCESS' }], reasonCode: 'PARSED' } }; }
function target(id = 2) { return { id, kind: 'TARGET', input: { email: 'alice@example.invalid', paperRefs: [{ recordId: 1, attemptIds: [101], givenNames: '<b>Alice</b>', familyNames: 'Smith', identityEvidence: '<script>not HTML</script>' }] }, result: { verificationRunId: 8001, verificationItemId: 91, verificationState: 'RUNNING', actualNew: 1, targetOutcome: 'NEW', trace: [{ stepNo: 1, state: 'SUCCESS' }, { stepNo: 3, state: 'INSUFFICIENT' }, { stepNo: 5, state: 'RUNNING' }], reason: '长中文原因'.repeat(80) } }; }
function harness(handler = () => undefined, openPaper) {
    const document = dom(), root = document.body.querySelector('#taskProgressModal'), calls = [], timers = new Map(), opened = [], verification = [];
    let timer = 0, uuid = 0;
    const sandbox = { window: { crypto: { randomUUID: () => '11111111-1111-4111-8111-' + String(++uuid).padStart(12, '0') } }, console, AbortController, DOMException, URLSearchParams, setTimeout: (fn, ms) => { timers.set(++timer, { fn, ms }); return timer; }, clearTimeout: id => timers.delete(id) };
    vm.runInNewContext(source, sandbox);
    const api = async (url, options = {}) => { const call = { url, options, body: options.body && JSON.parse(options.body) }; calls.push(call); const result = await handler(url, options, call); if (result !== undefined) return result; if (url.includes('/preview')) return { sourceExecutionId: 9001, recordCount: 121, missingSourcePapers: 3, expectedScopeHash: 'a'.repeat(64), activeRun: null }; if (url.endsWith('/apollo-settings')) return { revision: 5, searchEnabled: true, detailEnabled: false }; if (/\/items\//.test(url)) return paper(); if (url.includes('/items?')) return { items: url.includes('kind=TARGET') ? [target()] : [paper()], total: 121 }; if (/\/runs\/\d+$/.test(url)) return { run: run(), summary: { ...summary }, totalKnown: true, progressText: '已处理42/121篇' }; if (url.endsWith('/runs') && options.method === 'POST') return run(); return { items: [], total: 0 }; };
    const view = sandbox.window.DiscoveryPaperReprocess.mount({ root, api, makeUrl: p => p,
        openPaper: openPaper || ((...args) => opened.push(args)), openVerification: (...args) => verification.push(args) });
    const find = name => root.querySelector('[data-rp="' + name + '"]'), act = name => root.querySelector('[data-rp-action="' + name + '"]');
    return { view, document, root, calls, timers, find, act, opened, verification, uuid: () => uuid };
}

 test('byte exact CSS and DOM contract, untouched preceding CSS, real selectors and triad', () => {
    const expected = plan.split('```css\n')[1].split('```')[0];
    const actual = css.slice(css.indexOf('/* paper-reprocess-contract:start */'));
    assert.equal(actual, expected);
    const domBlock = plan.split('```html\n')[1].split('```')[0];
    for (const block of domBlock.split(/<!-- S-[\s\S]*?-->\n/).filter(Boolean)) assert.ok(html.includes(block.trimEnd()), 'exact S-6 block');
    const d = dom();
    for (const id of ['paperReprocessConfirm', 'paperReprocessDetail', 'rpPaperResults', 'rpTargetResults', 'rpTraceTemplate']) assert.ok(d.body.querySelector('#' + id), id);
    assert.equal(d.body.querySelector('#nonexistent'), null);
    const keys = ['styles.css', 'trust-reply-workbench.js', 'app.js'].map(name => html.match(new RegExp(name.replace('.', '\\.') + '\\?v=([^" ]+)'))[1]);
    assert.equal(new Set(keys).size, 1);
    assert.ok(html.indexOf('src="discovery-paper-reprocess.js') < html.indexOf('src="discovery-papers.js'));
    assert.ok(!source.includes('innerHTML')); assert.ok(!source.includes('.style')); assert.ok(!source.includes('localStorage'));
    assert.ok(!source.includes('size=100'), 'no client global aggregation');
 });

test('lazy explicit121 scope and saved Apollo only, disabled preview failure and default AUDIT', async () => {
    const h = harness(); assert.equal(h.calls.length, 0); assert.equal(h.act('open').disabled, true);
    h.view.setExecution(9001); h.view.setVisible(true); await flush();
    assert.match(h.find('scopeText').textContent, /全部 121 篇.*缺原文 3/); h.act('open').click(); await flush();
    assert.match(h.find('confirmScope').textContent, /全部 121 篇/); assert.match(h.find('confirmApollo').textContent, /免费搜索开启 \/ 付费详情关闭/);
    assert.equal(h.root.querySelector('input[value="AUDIT"]').checked, true); assert.equal(h.act('start').disabled, false);
    assert.ok(h.calls.every(c => c.options.method !== 'PUT')); h.view.dispose();
    const failed = harness(url => { if (url.includes('/preview')) throw new Error('不可读取'); }); failed.view.setExecution(9001); failed.view.setVisible(true); await flush(); assert.equal(failed.act('open').disabled, true); assert.match(failed.find('scopeText').textContent, /读取失败/); failed.view.dispose();
});

test('double submit timeout lookup uses one UUID and same run; close/reopen never cancel', async () => {
    let saved = null, posts = 0;
    const h = harness((url, options, call) => { if (url.endsWith('/runs') && options.method === 'POST') { posts++; saved = run(); throw new Error('HTTP timeout'); } if (url.includes('requestKey=') && saved) return { items: [saved], total: 1 }; });
    h.view.setExecution(9001); h.view.setVisible(true); await flush(); h.act('open').focus(); h.act('open').click(); await flush(); h.act('start').click(); h.act('start').click(); await flush();
    assert.equal(posts, 1); assert.equal(h.uuid(), 1); assert.match(h.find('runTitle').textContent, /7001/);
    const submitted = h.calls.find(c => c.options.method === 'POST').body; assert.equal(submitted.sourceExecutionId, 9001); assert.equal(submitted.mode, 'AUDIT'); assert.ok(!('q' in submitted));
    h.view.setVisible(false); assert.equal(h.timers.size, 0); h.view.setVisible(true); await flush(); assert.ok(h.calls.filter(c => c.url.includes('requestKey=')).every(c => c.url.includes(submitted.requestKey))); assert.equal(posts, 1); assert.equal(h.calls.filter(c => c.url.includes('/cancel')).length, 0); h.view.dispose();
});

test('uncertain submission repeated click retains original ENFORCE mode and request key', async () => {
    const h = harness((url, options) => { if (url.endsWith('/runs') && options.method === 'POST') throw new Error('network'); });
    h.view.setExecution(9001); h.view.setVisible(true); await flush(); h.act('open').click(); await flush();
    const modeDialog = h.root.querySelector('#paperReprocessConfirm'); modeDialog.querySelector('input[value="AUDIT"]').checked = false; modeDialog.querySelector('input[value="ENFORCE"]').checked = true;
    h.act('start').click(); await flush(); h.act('start').click(); await flush(); const writes = h.calls.filter(c => c.options.method === 'POST'); assert.equal(writes.length, 2); assert.equal(writes[0].body.requestKey, writes[1].body.requestKey); assert.equal(writes[0].body.mode, 'ENFORCE'); assert.equal(h.uuid(), 1); h.view.dispose();
});

test('reopened confirmation restores pending ENFORCE mode and keeps the same request key', async () => {
    const h = harness((url, options) => { if (url.endsWith('/runs') && options.method === 'POST') throw new Error('network'); });
    h.view.setExecution(9001); h.view.setVisible(true); await flush();
    h.act('open').click(); await flush();
    const dialog = h.root.querySelector('#paperReprocessConfirm');
    dialog.querySelector('input[value="AUDIT"]').checked = false; dialog.querySelector('input[value="ENFORCE"]').checked = true;
    h.act('start').click(); await flush();
    h.act('close-confirm').click(); await flush();
    h.act('open').click(); await flush();
    assert.equal(dialog.querySelector('input[value="ENFORCE"]').checked, true, 'pending ENFORCE stays visible');
    assert.equal(dialog.querySelector('input[value="AUDIT"]').checked, false);
    assert.equal(dialog.querySelector('input[value="ENFORCE"]').disabled, true, 'mode is locked while the key is pending');
    assert.match(h.find('confirmHint').textContent, /同一请求标识.*正式补录/);
    h.act('start').click(); await flush();
    const writes = h.calls.filter(c => c.options.method === 'POST');
    assert.equal(writes.length, 2); assert.equal(writes[0].body.requestKey, writes[1].body.requestKey);
    assert.equal(writes[0].body.mode, 'ENFORCE'); assert.equal(writes[1].body.mode, 'ENFORCE');
});

test('deterministic rejection clears the pending key and retries with a fresh UUID after preview refresh', async () => {
    let posts = 0, fail = true;
    const h = harness((url, options) => {
        if (url.endsWith('/runs') && options.method === 'POST') {
            posts++;
            if (fail) { fail = false; throw Object.assign(new Error('范围已变化'), { status: 409 }); }
            return run();
        }
        if (url.includes('requestKey=')) return { items: [], total: 0 };
    });
    h.view.setExecution(9001); h.view.setVisible(true); await flush();
    h.act('open').click(); await flush();
    h.act('start').click(); await flush();
    assert.equal(posts, 1);
    assert.match(h.find('confirmError').textContent, /范围已变化/);
    assert.equal(h.act('start').disabled, false, 'fresh preview re-enables submission');
    assert.equal(h.root.querySelector('input[value="AUDIT"]').disabled, false, 'mode is unlocked after the rejection');
    h.act('start').click(); await flush();
    const writes = h.calls.filter(c => c.options.method === 'POST');
    assert.equal(writes.length, 2); assert.notEqual(writes[0].body.requestKey, writes[1].body.requestKey);
    assert.equal(h.uuid(), 2);
});

test('reader rejection surfaces a Chinese error instead of an unhandled promise', async () => {
    const item = paper(); item.input.bodies[0].hash = 'a'.repeat(64);
    const h = harness(url => { if (url.includes('/runs?')) return { items: [run()], total: 1 }; if (/\/items\//.test(url)) return item; },
        () => Promise.reject(new Error('PAPER_SOURCE_UNAVAILABLE：固定原文已删除')));
    h.view.setExecution(9001); h.view.setVisible(true); await flush();
    h.find('paperRows').querySelector('button').click(); await flush();
    h.act('open-source').click(); await flush();
    assert.match(h.find('detailSubtitle').textContent, /PAPER_SOURCE_UNAVAILABLE/);
});

test('source ownership drops late response, aborts frontend only, no filter scope in requests', async () => {
    let resolve;
    const h = harness(url => url.includes('/preview?sourceExecutionId=9001') ? new Promise(r => { resolve = r; }) : undefined);
    h.view.setExecution(9001); h.view.setVisible(true); await flush(); const signal = h.calls[0].options.signal;
    h.view.setExecution(9002); await flush(); resolve({ recordCount: 999, expectedScopeHash: 'old' }); await flush();
    assert.equal(signal.aborted, true); assert.match(h.find('scopeText').textContent, /9002/); assert.ok(!h.find('scopeText').textContent.includes('999')); assert.ok(h.calls.every(c => !/[?&](?:q|selected|executionId)=/.test(c.url))); h.view.dispose();
});

test('restored run independent20row pagers keyboard tabs, persisted counts not page aggregates', async () => {
    const h = harness(url => url.includes('/runs?') ? { items: [run()], total: 1 } : undefined);
    h.view.setExecution(9001); h.view.setVisible(true); await flush(); assert.equal(h.find('papersTotal').textContent, '121'); assert.equal(h.find('papersProcessed').textContent, '42'); assert.match(h.find('summary').textContent, /拟补录 —.*分类可重叠/);
    assert.match(h.find('paperRows').textContent, /首次姓名关联 1.*本次姓名关联 2.*新增绑定 1/);
    h.act('next').click(); await flush(); assert.match(h.find('pageInfo').textContent, /第 2 \/ 7/); h.act('targets').click(); await flush(); assert.match(h.find('pageInfo').textContent, /第 1 \/ 7/); h.act('next').click(); await flush(); h.act('papers').click(); await flush(); assert.match(h.find('pageInfo').textContent, /第 2 \/ 7/);
    h.act('papers').dispatch('keydown', { key: 'ArrowRight' }); await flush(); assert.equal(h.document.activeElement, h.act('targets')); assert.equal(h.act('targets').getAttribute('aria-selected'), 'true'); assert.ok(h.calls.filter(c => c.url.includes('/items?')).every(c => c.url.endsWith('size=20'))); h.view.dispose();
});

test('visible5s poll terminal stop, auth stop, transient errors preserve last snapshot', async () => {
    let state = 'RUNNING', error = null;
    const h = harness(url => { if (url.includes('/runs?')) return { items: [run()], total: 1 }; if (/\/runs\/\d+$/.test(url)) { if (error) throw Object.assign(new Error('fixture outage'), { status: error }); return { run: run(7001,9001,state), summary: { ...summary }, totalKnown: true, progressText: '快照' }; } });
    h.view.setExecution(9001); h.view.setVisible(true); await flush(); assert.equal([...h.timers.values()][0].ms, 5000);
    error = 500; let poll = [...h.timers.values()][0]; h.timers.clear(); await poll.fn(); await flush(); assert.match(h.find('error').textContent, /更新失败，显示上次结果/); assert.equal(h.find('papersProcessed').textContent, '42');
    error = 401; poll = [...h.timers.values()][0]; h.timers.clear(); await poll.fn(); await flush(); assert.equal(h.timers.size, 0); assert.match(h.find('error').textContent, /权限失效/); assert.match(h.find('runTitle').textContent, /7001/);
    error = null; state = 'COMPLETED'; h.view.setVisible(false); h.view.setVisible(true); await flush(); assert.equal(h.timers.size, 0); assert.match(h.find('runState').textContent, /已完成/); h.view.dispose();
});

test('six fixed steps map cancelled/insufficient, safe raw text and genuine old8step callback', async () => {
    const item = target(); item.result.trace.push({ stepNo: 6, state: 'CANCELLED' });
    const h = harness(url => { if (url.includes('/runs?')) return { items: [run()], total: 1 }; if (/\/items\//.test(url)) return item; });
    h.view.setExecution(9001); h.view.setVisible(true); await flush(); const trigger = h.find('paperRows').querySelector('button'); trigger.focus(); trigger.click(); await flush();
    const steps = h.find('detailBody').querySelectorAll('li'); assert.equal(steps.length, 6); assert.equal(steps[2].dataset.state, 'BLOCKED'); assert.equal(steps[5].dataset.state, 'SKIPPED'); assert.match(steps[5].textContent, /已取消/); assert.equal(h.find('detailBody').querySelector('blockquote').textContent, '<script>not HTML</script>');
    h.act('open-verification').click(); assert.equal(h.verification.length, 1); assert.equal(h.verification[0][0], 8001); assert.equal(h.verification[0][1], 91);
    h.act('open-source').click(); assert.equal(h.opened.length, 1); assert.equal(h.opened[0][0].recordId, 1); assert.equal(h.opened[0][0].attemptId, 101);
    h.act('close-detail').click(); assert.equal(h.document.activeElement, trigger); assert.equal(h.calls.filter(c => /analysis|cancel/.test(c.url)).length, 0); h.view.dispose();
});

test('explicit cancel once, history source-bound and close/Escape/dispose frontend only', async () => {
    let resolveCancel;
    const h = harness((url, options) => { if (url.endsWith('/cancel')) return new Promise(r => { resolveCancel = r; }); if (url.includes('/runs?')) return { items: [run()], total: 1 }; });
    h.view.setExecution(9001); h.view.setVisible(true); await flush(); h.act('history').click(); await flush(); assert.match(h.find('detailBody').textContent, /7001.*9001/); h.act('close-detail').click();
    h.act('cancel').click(); h.act('cancel').click(); await flush(); assert.equal(h.calls.filter(c => c.url.endsWith('/cancel')).length, 1); resolveCancel(run(7001,9001,'CANCELLING')); await flush();
    h.view.suspend(); assert.equal(h.find('runPanel').hidden, true, 'closing clears the stale run panel'); h.view.dispose(); assert.equal(h.timers.size, 0); assert.equal(h.calls.filter(c => c.url.endsWith('/cancel')).length, 1);
});
