"use strict";
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const staticDir = path.join(__dirname, '../../main/resources/static');
const html = fs.readFileSync(path.join(staticDir, 'index.html'), 'utf8');
const source = fs.readFileSync(path.join(staticDir, 'discovery-recheck.js'), 'utf8');
const css = fs.readFileSync(path.join(staticDir, 'styles.css'), 'utf8');
const plan = fs.readFileSync(path.join(__dirname, '../../../docs/plans/2026-10-24/discovery-recheck-09-frontend.md'), 'utf8');
const flush = async () => { for (let i = 0; i < 12; i++) await new Promise(resolve => setImmediate(resolve)); };
function dom() {
    const document = { activeElement: null, hidden: false, baseURI: "http://localhost/talent/" };
    class Element {
        constructor(tag) { this.tagName = tag.toUpperCase(); this.children = []; this.parentElement = null; this.ownerDocument = document; this.attrs = {}; this.dataset = {}; this.listeners = {}; this.hidden = false; this.disabled = false; this.checked = false; this.open = false; this._value = null; this._text = ''; }
        set className(v) { this.attrs.class = v; }
        get className() { return this.attrs.class || ''; }
        get classList() { return { toggle: (name, value) => { const names = new Set(this.className.split(/\s+/).filter(Boolean)); if (value ?? !names.has(name)) names.add(name); else names.delete(name); this.className = [...names].join(' '); }, contains: name => this.className.split(/\s+/).includes(name) }; }
        set textContent(v) { this.children = []; this._text = String(v ?? ''); }
        get textContent() { return this._text + this.children.map(c => c.textContent).join(''); }
        set value(v) { this._value = String(v); }
        get value() { return this._value ?? (this.tagName === 'SELECT' ? this.children[0]?.value ?? '' : this.attrs.value ?? ''); }
        get isConnected() { return document.body.contains(this); }
        setAttribute(k, v) { this.attrs[k] = String(v); if (k.startsWith('data-')) this.dataset[k.slice(5).replace(/-([a-z])/g, (_, c) => c.toUpperCase())] = String(v); if (k === 'hidden' || k === 'disabled' || k === 'checked') this[k] = true; }
        getAttribute(k) { return this.attrs[k] ?? null; }
        removeAttribute(k) { delete this.attrs[k]; }
        appendChild(c) { c.parentElement = this; this.children.push(c); return c; }
        replaceChildren(...nodes) { this.children.forEach(c => { c.parentElement = null; }); this.children = []; this._text = ''; nodes.forEach(c => this.appendChild(c)); }
        remove() { if (this.parentElement) this.parentElement.children = this.parentElement.children.filter(c => c !== this); this.parentElement = null; }
        contains(c) { return this === c || this.children.some(n => n.contains(c)); }
        matches(selector) { if (selector.includes(',')) return selector.split(',').some(s => this.matches(s)); if (selector.endsWith(':checked')) return this.checked && this.matches(selector.slice(0, -8)); if (selector === 'input[name^="rr-"]') return this.tagName === 'INPUT' && (this.attrs.name || '').startsWith('rr-'); const match = selector.match(/^(#[\w-]+|\.[\w-]+|[\w-]+)?(?:\[([\w-]+)(?:=["']?([^"'\]]+)["']?)?\])?$/); if (!match) throw new Error('Unsupported selector: ' + selector); const [, base, key, value] = match; return (!base || (base[0] === '#' ? this.attrs.id === base.slice(1) : base[0] === '.' ? this.classList.contains(base.slice(1)) : this.tagName === base.toUpperCase())) && (!key || (key.startsWith('data-') ? Object.hasOwn(this.dataset, key.slice(5).replace(/-([a-z])/g, (_, c) => c.toUpperCase())) && (value === undefined || this.dataset[key.slice(5).replace(/-([a-z])/g, (_, c) => c.toUpperCase())] === value) : Object.hasOwn(this.attrs, key) && (value === undefined || this.attrs[key] === value))); }
        querySelectorAll(s) { return this.children.flatMap(c => [...(c.matches(s) ? [c] : []), ...c.querySelectorAll(s)]); }
        querySelector(s) { return this.querySelectorAll(s)[0] || null; }
        closest(s) { return this.matches(s) ? this : this.parentElement?.closest(s) || null; }
        addEventListener(t, f) { (this.listeners[t] ??= new Set()).add(f); }
        removeEventListener(t, f) { this.listeners[t]?.delete(f); }
        dispatch(t, props = {}) { const event = { target: this, preventDefault() {}, stopPropagation() { this.stopped = true; }, ...props }; let current = this; do { [...(current.listeners[t] || [])].forEach(fn => fn(event)); current = event.stopped ? null : current.parentElement; } while (current); }
        click() { if (!this.disabled) this.dispatch('click'); }
        focus() { document.activeElement = this; }
        showModal() { this.open = true; }
        close() { this.open = false; this.dispatch('close'); }
        scrollIntoView() {}
    }
    const events = new Element('document'); document.addEventListener = events.addEventListener.bind(events); document.removeEventListener = events.removeEventListener.bind(events); document.dispatch = events.dispatch.bind(events); document.events = events;
    document.createElement = t => new Element(t);
    document.createTextNode = v => { const n = new Element('#text'); n.textContent = v; return n; };
    document.body = new Element('body');
    const stack = [document.body], voidTags = new Set(['input', 'br', 'hr', 'img', 'meta', 'link', 'source']);
    // Only the actual task modal is needed; browser evidence separately exercises the complete page.
    const start = html.indexOf('<div id="taskProgressModal"');
    const end = html.indexOf('<dialog id="discoveryPaperDelete"', start);
    const closing = html.indexOf('</dialog>', end) + '</dialog>'.length;
    for (const token of html.slice(start, closing).match(/<!--[\s\S]*?-->|<[^>]+>|[^<]+/g)) {
        if (token.startsWith('<!--')) continue;
        if (token.startsWith('</')) { stack.pop(); continue; }
        if (token.startsWith('<')) { const tag = token.match(/^<([\w-]+)/)[1], el = new Element(tag); for (const a of token.slice(tag.length + 1, -1).matchAll(/([\w-]+)(?:="([^"]*)")?/g)) el.setAttribute(a[1], a[2] ?? ''); stack.at(-1).appendChild(el); if (!voidTags.has(tag)) stack.push(el); }
        else stack.at(-1)._text += token;
    }
    document.getElementById = id => document.body.querySelector('#' + id);
    return document;
}

const counts = { total: 2, processed: 0, queued: 2, cancelled: 0, errors: 0, wouldAdmit: 0, actualAdmitted: 0, syncPending: 0, syncErrors: 0, synced: 0, pending: 2, processing: 0, protected: 0 };
function run(phase = 'READY', mode = 'AUDIT') { return { runId: 42, taskId: 42, phase, mode, evidenceMode: 'REUSE', revision: 3, scope: 'IDS', rosterHash: 'a'.repeat(64), counts: { ...counts }, settings: { apolloAccess: { searchEnabled: true, detailEnabled: false, settingRevision: 7 } }, prepareTask: { status: 'SUCCESS' }, createdAt: '2026-10-25T00:00:00Z' }; }
function item(id = 1) {
    return { itemId: id, runId: 42, docId: 'doc-' + id, givenNames: '<b>Person ' + id + '</b>', state: 'COMPLETED', before: { decision: 'NEEDS_REVIEW', revision: 2 }, proposed: 'AUTO_PASSED', current: { decision: 'NEEDS_REVIEW', revision: 2 }, applied: null, projection: 'NONE', actualAdmitted: false, allReasons: ['IDENTITY_UNRESOLVED'], sourceRefs: [{ archiveRecordId: 3, attemptId: 4, bodySha256: 'b'.repeat(64), sourceName: 'paper', sourceItemKey: 'source' }], trace: { recorded: true, blockingStep: 3, steps: Array.from({ length: 8 }, (_, index) => ({ stepNo: index + 1, attemptNo: 1, state: index === 2 ? 'INSUFFICIENT' : index < 2 ? 'SUCCESS' : 'SKIPPED', inputJson: '{"value":{"name":"<script>unsafe</script>"}}', reasonJson: '{"value":["IDENTITY_UNRESOLVED"]}', versionJson: '{"value":{"parser":3}}', outputJson: '{"value":{"quote":"<img src=x>"}}', startedAt: '2026-10-25T00:00:00Z', finishedAt: '2026-10-25T00:00:01Z' })) }, versions: [{ parser: 3 }], updatedAt: '2026-10-25T00:00:01Z' };
}
function harness(handler) {
    const document = dom(), root = document.getElementById('discoveryRecheckRoot'), calls = [], timers = new Map(), opened = [];
    let timerId = 0, selected = ['doc-1', 'doc-2'], filter = { q: '', issue: '', decision: '' }, applied = 0;
    const sandbox = { window: { crypto: require('node:crypto').webcrypto }, console, document, URL, URLSearchParams, AbortController, DOMException,
        setTimeout: (fn, ms) => { const id = ++timerId; timers.set(id, { fn, ms }); return id; }, clearTimeout: id => timers.delete(id) };
    vm.runInNewContext(source, sandbox);
    const api = async (url, options) => {
        calls.push({ url, options, body: options.body && JSON.parse(options.body) });
        const result = handler && await handler(url, options, calls.at(-1));
        if (result !== undefined) return result;
        if (url.endsWith('/active')) return [];
        if (url.includes('/latest?')) return { 'doc-1': run(), 'doc-2': run() };
        if (url.endsWith('/prepare')) return run();
        if (url.endsWith('/start')) return run('RUNNING');
        if (url.endsWith('/cancel')) return run('CANCELLED');
        if (url.includes('/steps')) return item(Number(url.match(/items\/(\d+)/)[1]));
        if (url.includes('/items?')) return { total: 2, items: [item(1), item(2)] };
        if (url.includes('/manifest?')) return { total: 2, items: [{ docId: 'doc-1', identityHash: 'c'.repeat(64), state: 'RESERVED' }] };
        if (url.includes('/runs?')) return { total: 21, items: [run()] };
        return run('COMPLETED');
    };
    const view = sandbox.window.createDiscoveryRecheck({ root, api, getSelectedDocIds: () => selected, getFilter: () => filter, onApplied: () => applied++, openPaper: ref => opened.push(ref) });
    const find = name => root.querySelector('[data-rr="' + name + '"]');
    view.mount();
    return { document, root, view, calls, timers, find, opened, applied: () => applied, setSelected: v => { selected = v; }, setFilter: v => { filter = v; } };
}

test('approved exact root, full CSS, real IDs, only allowed classes and no inline style', () => {
    const expectedDom = plan.match(/```html\n(<section class="dr-recheck"[\s\S]*?)\n```/)[1];
    const amended = expectedDom.replace('data-rr="open">批量重新审核', 'data-rr="open">重新审核全部待审核 / 所选专家')
        .replace('<fieldset class="rr-field"><legend>名单范围</legend>', '<fieldset class="rr-field"><legend>名单范围</legend><label class="rr-option"><input type="radio" name="rr-scope" value="ALL_PENDING" checked>全部待审核专家<small>服务端固定待审核范围；无需先加载列表，不受当前页和搜索条件限制。</small></label>')
        .replace('<button class="button secondary" type="button" data-rr="prepare">', '<button class="button secondary" type="button" data-rr="cancel-preparation" disabled>取消当前任务</button><button class="button secondary" type="button" data-rr="prepare">');
    assert.ok(html.includes(amended));
    const expectedCss = plan.match(/```css\n([\s\S]*?)\n```/)[1];
    assert.equal(css.slice(css.indexOf('.dr-recheck{'), css.indexOf('.dr-recheck{') + expectedCss.length), expectedCss);
    const d = dom();
    for (const id of ['discoveryRecheckRoot', 'recheckConfigTitle', 'recheckHistoryTitle', 'recheckDetailTitle']) assert.ok(d.getElementById(id), id);
    const root = d.getElementById('discoveryRecheckRoot');
    assert.equal(root.querySelectorAll('dialog').length, 3);
    assert.equal(root.querySelectorAll('[style]').length, 0);
    const allowed = new Set([...expectedDom.matchAll(/class="([^"]+)"/g)].flatMap(m => m[1].split(' ')));
    for (const match of expectedCss.matchAll(/\.([\w-]+)/g)) allowed.add(match[1]);
    allowed.add('small');
    for (const match of source.matchAll(/(?:add|node)\([^\n]*?,\s*"([a-z-]+(?: [a-z-]+)*)"/g)) {
        // Dynamic runtime classes are also checked below on rendered nodes.
        if (match[1].startsWith('rr-')) assert.ok(allowed.has(match[1]), match[1]);
    }
    assert.ok(!source.includes('innerHTML')); assert.ok(!source.includes('localStorage')); assert.ok(!source.includes('style='));
    assert.ok(html.indexOf('discoveryRecheckRoot') > html.indexOf('data-review-action="REJECT"'));
    assert.ok(html.indexOf('discoveryRecheckRoot') < html.indexOf('id="discoveryReviewRows"'));
});

test('default audit/reuse, READY authority, paginated frozen roster, option/filter/selection invalidation', async () => {
    let phase = 'PREPARING';
    const h = harness(url => url.endsWith('/prepare') || /rechecks\/42$/.test(url) ? run(phase) : undefined);
    const trigger = h.find('open'); trigger.focus(); trigger.click(); await flush();
    assert.equal(h.root.querySelector('input[name="rr-mode"]:checked').value, 'AUDIT');
    assert.equal(h.root.querySelector('input[name="rr-evidence"]:checked').value, 'REUSE');
    assert.equal(h.find('start').disabled, true);
    h.find('prepare').click(); await flush();
    assert.equal(h.calls.find(c => c.url.endsWith('/prepare')).body.scope, 'ALL_PENDING');
    assert.deepEqual(h.calls.find(c => c.url.endsWith('/prepare')).body.docIds, []);
    assert.equal(h.find('start').disabled, true); assert.equal(h.timers.size, 1);
    phase = 'READY'; const poll = [...h.timers.values()][0]; h.timers.clear(); await poll.fn(); await flush();
    assert.equal(h.find('start').disabled, false); assert.match(h.find('scope-hash').textContent, /固定 2 人/);
    assert.match(h.find('apollo-config').textContent, /search=true \/ detail=false/);
    assert.ok(h.calls.some(c => c.url.endsWith('/manifest?page=0&size=20')));
    h.setFilter({ q: 'changed' }); await h.view.onRows([]);
    assert.equal(h.find('start').disabled, false); assert.match(h.find('scope-hash').textContent, /固定 2 人/);
    h.view.unmount(); assert.equal(h.timers.size, 0);
});

test('prepare wire separates selected IDs, all-page filters and server-owned ALL_PENDING', async () => {
    for (const scope of ['IDS', 'ALL_MATCHING', 'ALL_PENDING']) {
        const h = harness();
        h.setFilter({ q: 'Ada', issue: 'IDENTITY_UNRESOLVED', decision: 'NEEDS_REVIEW' });
        h.find('open').click(); await flush();
        for (const input of h.root.querySelectorAll('input[name="rr-scope"]')) input.checked = input.value === scope;
        h.find('prepare').click(); await flush();
        const body = h.calls.find(c => c.url.endsWith('/prepare')).body;
        assert.equal(body.scope, scope);
        assert.deepEqual(body.docIds, scope === 'IDS' ? ['doc-1', 'doc-2'] : []);
        if (scope === 'ALL_MATCHING') assert.deepEqual(body.filter, { q: 'Ada', issue: 'IDENTITY_UNRESOLVED', decision: 'NEEDS_REVIEW' });
        else assert.ok(!Object.hasOwn(body, 'filter'));
        assert.equal(body.mode, 'AUDIT'); assert.equal(body.evidenceMode, 'REUSE');
        h.view.unmount();
    }
});

test('restore READY after re-entry without prepare and submit frozen authority', async () => {
    const saved = { ...run('READY'), scope: 'ALL_PENDING', filter: { decision: 'NEEDS_REVIEW' }, docIds: [] };
    const h = harness(url => url.endsWith('/active') ? [saved] : undefined);
    await flush(); h.find('open').click(); await flush();
    assert.equal(h.find('prepare').disabled, true);
    assert.equal(h.find('start').disabled, false);
    assert.ok(h.calls.some(c => c.url.endsWith('/manifest?page=0&size=20')));
    h.find('prepare').click(); await flush();
    assert.equal(h.calls.filter(c => c.url.endsWith('/prepare')).length, 0);
    h.view.unmount(); h.view.mount(); await flush();
    h.find('open').click(); await flush(); h.find('start').click(); await flush();
    assert.deepEqual(h.calls.find(c => c.url.endsWith('/start')).body, { rosterHash: saved.rosterHash, revision: saved.revision });
    h.view.unmount();
});

test('reopen refreshes a roster completed in another tab and unlocks a new preparation', async () => {
    let finished = false;
    const h = harness(url => url.endsWith('/active') ? (finished ? [] : [run('READY')])
        : /rechecks\/42$/.test(url) ? run('COMPLETED') : undefined);
    await flush(); h.find('open').click(); await flush();
    assert.equal(h.find('prepare').disabled, true);
    h.find('close-config').click(); finished = true;
    h.find('open').click(); await flush();
    assert.equal(h.find('prepare').disabled, false);
    assert.equal(h.find('start').disabled, true);
    assert.equal(h.calls.filter(c => c.url.endsWith('/prepare')).length, 0);
    h.view.unmount();
});

test('accepted preparation remains disabled and option changes cannot create another task', async () => {
    const h = harness(url => url.endsWith('/prepare') ? run('PREPARING') : undefined);
    h.find('open').click(); await flush(); h.find('prepare').click(); await flush();
    assert.equal(h.find('prepare').disabled, true);
    h.find('prepare').click(); h.root.querySelector('input[value="REFRESH"]').dispatch('change'); await flush();
    assert.equal(h.calls.filter(c => c.url.endsWith('/prepare')).length, 1);
    assert.equal(h.find('start').disabled, true); h.view.unmount();
});

test('persisted detail evidence renders literal quotes and exact frozen reader identity, not step output quotes', async () => {
    const detail = item(2), ref = detail.sourceRefs[0];
    detail.evidence = { state: 'RECORDED', total: 1, truncated: false, items: [{
        provider: 'PAPER', field: 'education', value: '{"completed":true}', excerpt: 'Ada earned her PhD. <img src=x>',
        sourceUrl: 'https://publisher.invalid/article', responseSha256: ref.bodySha256, fetchedAt: '2020-01-02T00:00:00Z',
        assertionSource: 'SELF', temporalScope: 'HISTORICAL', jsonPointer: '/archive/3/attempt/4/page/0/paragraph/2/year/2020',
        page: null, paragraph: 2, sourceRef: ref
    }] };
    const h = harness(url => url.includes('/items/2/steps') ? detail : undefined);
    await h.view.onRows([], { docId: 'doc-2', trigger: h.find('open') });
    h.find('steps').querySelectorAll('button')[3].click();
    assert.match(h.find('step-body').textContent, /Ada earned her PhD\. <img src=x>/);
    assert.match(h.find('step-body').textContent, /PAPER.*education/);
    assert.match(h.find('step-body').textContent, /页 未知.*段落 2/);
    assert.match(h.find('step-body').textContent, /2020-01-02T00:00:00Z.*HISTORICAL/);
    assert.equal(h.find('step-body').querySelectorAll('img').length, 0);
    const quotes = h.find('step-body').querySelectorAll('[data-step-quote]');
    assert.equal(quotes.length, 1); assert.equal(quotes[0].hidden, false);
    h.find('step-body').querySelector('a').click();
    assert.deepEqual(JSON.parse(JSON.stringify(h.opened[0])), ref); h.view.unmount();
});

test('missing damaged empty and bounded evidence remain explicit without invented quotes', async () => {
    for (const [state, label] of [['MISSING', '未记录'], ['MALFORMED', '损坏'], ['EMPTY', '未保存']]) {
        const detail = item(2); detail.evidence = { state, total: 0, truncated: false, items: [] };
        const h = harness(url => url.includes('/items/2/steps') ? detail : undefined);
        await h.view.onRows([], { docId: 'doc-2', trigger: h.find('open') });
        h.find('steps').querySelectorAll('button')[3].click();
        assert.match(h.find('step-body').textContent, new RegExp(label));
        assert.equal(h.find('step-body').querySelectorAll('[data-step-quote]').length, 0); h.view.unmount();
    }
    const detail = item(2); detail.evidence = { state: 'RECORDED', total: 101, truncated: true, items: [] };
    const h = harness(url => url.includes('/items/2/steps') ? detail : undefined);
    await h.view.onRows([], { docId: 'doc-2', trigger: h.find('open') });
    h.find('steps').querySelectorAll('button')[3].click();
    assert.match(h.find('step-body').textContent, /截断.*101/); h.view.unmount();
});

test('Esc only closes, restores focus, keeps run; explicit cancel has its own endpoint', async () => {
    const h = harness(); const trigger = h.find('open'); trigger.focus(); trigger.click(); await flush();
    h.find('prepare').click(); await flush();
    h.find('config').dispatch('cancel'); assert.equal(h.find('config').open, false);
    assert.equal(h.document.activeElement, trigger);
    assert.equal(h.calls.filter(c => c.url.endsWith('/cancel')).length, 0);
    trigger.click(); assert.equal(h.find('start').disabled, false);
    h.find('close-config').click(); h.find('cancel-run').click(); await flush();
    assert.equal(h.calls.filter(c => c.url.endsWith('/cancel')).length, 1);
    h.view.unmount();
});

test('single 2s timer, hidden pause/resume GET and terminal stop; unmount removes listeners and stale requests', async () => {
    let phase = 'RUNNING';
    const h = harness(url => /rechecks\/42$/.test(url) ? run(phase) : undefined);
    h.find('open').click(); await flush(); h.find('prepare').click(); await flush(); h.find('start').click(); await flush();
    assert.equal(h.timers.size, 1); assert.equal([...h.timers.values()][0].ms, 2000);
    h.document.hidden = true; h.document.dispatch('visibilitychange'); assert.equal(h.timers.size, 0);
    h.document.hidden = false; h.document.dispatch('visibilitychange'); await flush(); assert.equal(h.timers.size, 1);
    phase = 'COMPLETED'; const poll = [...h.timers.values()][0]; h.timers.clear(); await poll.fn(); await flush(); assert.equal(h.timers.size, 0);
    h.view.unmount(); assert.equal(h.timers.size, 0);
    for (const el of [h.root, h.document.events, ...h.root.querySelectorAll('dialog')]) for (const listeners of Object.values(el.listeners)) assert.equal(listeners.size, 0);
});

test('batch latest ≤100, old detail Promise cannot replace new person; eight real steps, source and all four deltas', async () => {
    let resolve;
    const h = harness(url => url.includes('/items/1/steps') ? new Promise(r => { resolve = r; }) : undefined);
    await h.view.onRows(Array.from({ length: 100 }, (_, index) => ({ docId: 'doc-' + index })));
    assert.equal(h.calls.filter(c => c.url.includes('/latest?')).length, 1);
    assert.equal(new URL('http://local' + h.calls.find(c => c.url.includes('/latest?')).url).searchParams.get('docIds').split(',').length, 100);
    const first = h.view.onRows([], { docId: 'doc-1', trigger: h.find('open') }); await flush();
    await h.view.onRows([], { docId: 'doc-2', trigger: h.find('open') });
    resolve(item(1)); await first; await flush();
    assert.match(h.find('detail-person').textContent, /doc-2/); assert.doesNotMatch(h.find('detail-person').textContent, /doc-1/);
    assert.equal(h.find('steps').children.length, 8); assert.equal(h.find('steps').querySelector('[aria-current="step"]').dataset.stepNo, '3');
    assert.match(h.find('detail-summary').textContent, /执行前.*本轮建议.*当前.*实际应用/);
    assert.match(h.find('step-body').textContent, /SHA.*页 未记录/);
    assert.match(h.find('step-body').textContent, /<script>unsafe/);
    assert.equal(h.find('step-body').querySelectorAll('script').length, 0);
    h.find('step-body').querySelector('a').click(); assert.equal(h.opened.length, 1);
    const classes = h.root.querySelectorAll('[class]').flatMap(el => el.className.split(' '));
    const allowed = new Set([...plan.matchAll(/class="([^"]+)"/g)].flatMap(m => m[1].split(' ')));
    for (const cls of classes) assert.ok(allowed.has(cls), cls);
    assert.equal(h.root.querySelectorAll('[style]').length, 0); h.view.unmount();
});

test('history all/run/person uses clear size20 pagination; projection retry requires existing receipt', async () => {
    const h = harness(url => url.includes('/items/2/steps') ? { ...item(2), actualAdmitted: true, applied: { decision: 'AUTO_PASSED', revision: 4 }, projection: 'ERROR', projectionError: 'ES_503' } : undefined);
    h.find('history').click(); await flush(); assert.match(h.find('history-status').textContent, /全部重审/);
    h.find('history-next').click(); await flush(); assert.ok(h.calls.some(c => c.url.endsWith('/runs?page=1&size=20')));
    h.find('history-rows').querySelector('button').click(); await flush();
    assert.match(h.find('history-status').textContent, /本轮逐人/);
    assert.ok(h.calls.some(c => c.url.endsWith('/42/items?page=0&size=20')));
    await h.view.onRows([], { docId: 'doc-2', trigger: h.find('open') }); assert.equal(h.find('sync-retry').hidden, false);
    h.find('sync-retry').click(); await flush();
    assert.deepEqual(h.calls.find(c => c.url.endsWith('/sync-retry')).body, { itemIds: [2] });
    h.find('person-history').click(); await flush();
    assert.ok(h.calls.some(c => c.url.endsWith('/runs?page=0&size=20&docId=doc-2')));
    h.view.unmount();
});

test('old trace means historical absence, network errors retain code/action and never fabricate zero', async () => {
    const h = harness(url => url.includes('/items/2/steps') ? { ...item(2), trace: { recorded: false, steps: [], blockingStep: null } } : url.endsWith('/prepare') ? Promise.reject(Object.assign(new Error('offline'), { data: { code: 'UNAVAILABLE' } })) : undefined);
    h.find('open').click(); await flush(); h.find('prepare').click(); await flush();
    assert.match(h.find('prepare-status').textContent, /UNAVAILABLE.*固定名单重试/); assert.equal(h.find('start').disabled, true);
    await h.view.onRows([], { docId: 'doc-2', trigger: h.find('open') });
    assert.equal(h.find('steps').children.length, 8); assert.match(h.find('step-body').textContent, /历史未记录/);
    assert.equal(h.find('steps').querySelectorAll('[data-state]').length, 0); assert.equal(h.find('sync-retry').hidden, true); h.view.unmount();
});

test('unsafe schemes, cross-origin API refs and credential URLs are rejected', () => {
    const { safeUrl } = require(path.join(staticDir, 'discovery-recheck.js'));
    assert.equal(safeUrl('javascript:alert(1)', 'https://local/talent/'), null);
    assert.equal(safeUrl('https://evil/api/paper', 'https://local/talent/'), null);
    assert.equal(safeUrl('https://user:password@host/paper', 'https://local/talent/'), null);
    assert.equal(safeUrl('/talent/api/paper', 'https://local/talent/'), 'https://local/talent/api/paper');
    assert.equal(safeUrl('https://publisher.invalid/paper', 'https://local/talent/'), 'https://publisher.invalid/paper');
});
