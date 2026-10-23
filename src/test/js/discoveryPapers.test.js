const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const staticDir = path.join(__dirname, '../../main/resources/static');
const html = fs.readFileSync(path.join(staticDir, 'index.html'), 'utf8');
const source = fs.readFileSync(path.join(staticDir, 'discovery-papers.js'), 'utf8');
const app = fs.readFileSync(path.join(staticDir, 'app.js'), 'utf8');
const css = fs.readFileSync(path.join(staticDir, 'styles.css'), 'utf8');
const plan = fs.readFileSync(path.join(__dirname, '../../../docs/plans/2026-10-23/discovery-paper-05-frontend.md'), 'utf8');
const flush = async () => { for (let i = 0; i < 12; i++) await new Promise(resolve => setImmediate(resolve)); };

// A bounded HTML parser, not a universal-selector stub: absent IDs/selectors return null.
// Only the element/selector/event operations consumed by this module are implemented.
function dom() {
    const document = { activeElement: null };
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
        setAttribute(k, v) { this.attrs[k] = String(v); if (k.startsWith('data-')) this.dataset[k.slice(5).replace(/-([a-z])/g, (_, c) => c.toUpperCase())] = String(v); if (k === 'hidden' || k === 'disabled') this[k] = true; }
        getAttribute(k) { return this.attrs[k] ?? null; }
        removeAttribute(k) { delete this.attrs[k]; }
        appendChild(c) { c.parentElement = this; this.children.push(c); return c; }
        replaceChildren(...nodes) { this.children.forEach(c => { c.parentElement = null; }); this.children = []; this._text = ''; nodes.forEach(c => this.appendChild(c)); }
        remove() { if (this.parentElement) this.parentElement.children = this.parentElement.children.filter(c => c !== this); this.parentElement = null; }
        contains(c) { return this === c || this.children.some(n => n.contains(c)); }
        matches(selector) { const match = selector.match(/^(#[\w-]+|\.[\w-]+|[\w-]+)?(?:\[([\w-]+)(?:=["']?([^"'\]]+)["']?)?\])?$/); if (!match) throw new Error('Unsupported selector: ' + selector); const [, base, key, value] = match; return (!base || (base[0] === '#' ? this.attrs.id === base.slice(1) : base[0] === '.' ? this.classList.contains(base.slice(1)) : this.tagName === base.toUpperCase())) && (!key || (key.startsWith('data-') ? Object.hasOwn(this.dataset, key.slice(5).replace(/-([a-z])/g, (_, c) => c.toUpperCase())) && (value === undefined || this.dataset[key.slice(5).replace(/-([a-z])/g, (_, c) => c.toUpperCase())] === value) : Object.hasOwn(this.attrs, key) && (value === undefined || this.attrs[key] === value))); }
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
const setting = { revision: 3, enabled: false, maxBytes: 20 * 1073741824, minFreeBytes: 15 * 1073741824, effectiveEnabled: false, gate: 'DISABLED' };
function stats(id = null) { return { executionId: id, counts: { papers: 3, attempts: 4, savedAttempts: 3, newFiles: 2, reusedAttempts: 1, failedAttempts: 1, notStoredPapers: 1 }, storage: { archiveUsedBytes: 3072, blobBytes: 3072, tempBytes: 0, fileCount: 2, protectedPaperCount: 1, diskTotalBytes: null, diskUsedBytes: null, diskAvailableBytes: null, measuredAt: '2026-10-24T00:00:00Z' } }; }
function row(id = 1, protectedFlag = true) { return { id, revision: 2, executionId: 101, title: '<img src=x onerror=alert(1)>', paperKey: 'fixture-' + id, state: 'STORED', sourceName: 'fixture', protected: protectedFlag, storedBytes: 1024, readableAttemptIds: [id + 10], firstCapturedAt: '2023-01-01', extractionSummary: { emails: 2, resolved: 1 } }; }
function deletion(ids = [1]) { return { ids, snapshotHash: 'a'.repeat(64), deletableFiles: 1, deletableBytes: 1024, protectedRecords: [], sharedFiles: [], busyFiles: [], notStored: [], items: [] }; }
function harness(handler, contextPath = '/talent') {
    const document = dom(), root = document.getElementById('taskProgressModal'), calls = [], timers = new Map(), blobs = new Set(), fetched = [], statuses = [];
    let timerId = 0;
    const sandbox = { window: {}, document, console, URLSearchParams, AbortController, DOMException, Blob, setTimeout: (fn, ms) => { const id = ++timerId; timers.set(id, { fn, ms }); return id; }, clearTimeout: id => timers.delete(id), URL: { createObjectURL: () => { const url = 'blob:fixture/' + blobs.size; blobs.add(url); return url; }, revokeObjectURL: url => blobs.delete(url) }, fetch: async (url, options) => { fetched.push({ url, options }); return { ok: true, headers: { get: () => 'text/plain' }, blob: async () => new Blob(['<script>evil()</script> quote']) }; } };
    vm.runInNewContext(source, sandbox);
    const api = async (url, options) => { calls.push({ url, options, body: options.body && JSON.parse(options.body) }); const result = handler && await handler(url, options, calls.at(-1)); if (result !== undefined) return result; if (url.endsWith('/settings')) return { ...setting }; if (url.includes('/stats')) return stats(); return { items: [row()], total: 1 }; };
    const view = sandbox.window.DiscoveryPapers.mount({ root, api, makeUrl: p => contextPath + p, onStatus: m => statuses.push(m) });
    const find = name => root.querySelector('[data-dp="' + name + '"]');
    const act = name => root.querySelector('[data-dp-action="' + name + '"]');
    return { document, root, calls, timers, blobs, fetched, statuses, view, find, act, sandbox };
}

test('exact approved HTML/CSS contract, real IDs and asset keys', () => {
    const d = dom();
    for (const id of ['discoveryPapersTab', 'discoveryPapersPane', 'discoveryPaperReader', 'discoveryPaperSettings', 'discoveryPaperDelete', 'dpMappings', 'dpQualifications']) assert.ok(d.getElementById(id), id);
    assert.equal(d.getElementById('missing'), null);
    const expected = plan.match(/```css\n(\/\* discovery-paper-archive-contract:start \*\/[\s\S]*?)\n```/)[1];
    assert.equal(css.slice(css.indexOf('/* discovery-paper-archive-contract:start */')).trim(), expected);
    const blocks = [...plan.matchAll(/```html\n([\s\S]*?)\n```/g)].map(m => m[1]);
    for (const i of [0, 3, 5]) assert.ok(html.includes(i === 0 ? blocks[i].split('\n').slice(1).join('\n') : blocks[i]));
    assert.ok(app.includes(blocks[1]));
    assert.equal((html.match(/\?v=/g) || []).length, 17);
    assert.ok(!source.includes('localStorage')); assert.ok(!source.includes('innerHTML')); assert.ok(!source.includes('style.'));
});

test('lazy visit, exact API shape, null unknown, current-page protected selection and unprotect only', async () => {
    const h = harness((url, _, call) => url.endsWith('/protection') ? { changed: 1, unchanged: 0, conflicts: [], items: [{ id: 1, revision: 3, protected: false }] } : undefined);
    assert.equal(h.calls.length, 0); h.view.setExecution(101, [{ executionId: 101 }]); assert.equal(h.calls.length, 0);
    h.view.setVisible(true); await flush();
    assert.match(h.calls.find(c => c.url.includes('size=')).url, /page=0&size=20&executionId=101/);
    assert.equal(h.find('diskTotal').textContent, '未知'); assert.match(h.find('archiveCounts').textContent, /成功留存 3 次＝新存 2 次＋复用 1 次/);
    h.find('selectPage').checked = true; h.find('selectPage').dispatch('change'); assert.equal(h.act('unprotect').disabled, false);
    h.act('unprotect').click(); await flush();
    const write = h.calls.find(c => c.url.endsWith('/protection')); assert.deepEqual(JSON.parse(JSON.stringify(write.body)), { items: [{ id: 1, revision: 2 }], protected: false });
    assert.match(h.find('selection').textContent, /0/); assert.equal(h.act('delete').disabled, true); assert.equal(h.calls.filter(c => c.url.endsWith('/delete')).length, 0);
    assert.match(h.statuses[0], /已变更 1/); h.view.dispose(); assert.equal(h.timers.size, 0);
});

test('delete preview cancel is read-only; 409 refreshes preview and waits for a new click', async () => {
    let deletes = 0;
    const h = harness(url => { if (url.endsWith('/delete-preview')) return deletion(); if (url.endsWith('/delete')) { deletes++; if (deletes === 1) throw Object.assign(new Error('changed'), { status: 409, data: { preview: { ...deletion(), snapshotHash: 'b'.repeat(64) } } }); return { deletedFiles: 1, deletedBytes: 1024, skipped: [], failed: [], remainingFiles: 0 }; } });
    h.view.setVisible(true); await flush(); h.find('selectPage').checked = true; h.find('selectPage').dispatch('change'); h.act('delete').click(); await flush();
    assert.equal(h.root.querySelector('#discoveryPaperDelete').open, true); assert.equal(deletes, 0); h.act('close-delete').click(); assert.equal(deletes, 0);
    h.act('delete').click(); await flush(); h.act('confirm-delete').click(); await flush(); assert.equal(deletes, 1); assert.match(h.find('deleteError').textContent, /未自动重试/);
    h.act('confirm-delete').click(); await flush(); assert.equal(deletes, 2); assert.equal(h.calls.filter(c => c.url.endsWith('/delete'))[1].body.snapshotHash, 'b'.repeat(64)); h.view.dispose();
});

test('503 and 409 settings retain draft, exact revision/bytes, Esc focus return', async () => {
    let failure = 503;
    const h = harness((url, options) => { if (url.endsWith('/settings') && options.method === 'PUT') throw Object.assign(new Error('unavailable'), { status: failure }); });
    h.view.setVisible(true); await flush(); const trigger = h.act('settings'); trigger.focus(); trigger.click(); h.find('maxGiB').value = 25; h.find('enabled').checked = true;
    h.find('settingsForm').dispatch('submit'); await flush(); assert.equal(h.find('maxGiB').value, '25'); assert.equal(h.root.querySelector('#discoveryPaperSettings').open, true); assert.match(h.find('settingsError').textContent, /输入已保留/);
    const call = h.calls.find(c => c.options.method === 'PUT'); assert.equal(call.body.maxBytes, 25 * 1073741824); assert.equal(call.body.revision, 3);
    failure = 409; h.find('settingsForm').dispatch('submit'); await flush(); assert.equal(h.find('maxGiB').value, '25'); assert.match(h.find('settingsError').textContent, /设置版本已变化/);
    let bubbled = false; h.root.addEventListener('keydown', () => { bubbled = true; }); h.find('maxGiB').dispatch('keydown', { key: 'Escape' }); assert.equal(bubbled, false, 'Escape must not reach task modal before native cancel');
    h.root.querySelector('#discoveryPaperSettings').dispatch('cancel'); assert.equal(h.document.activeElement, trigger); assert.equal(h.root.querySelector('#discoveryPaperSettings').open, false); h.view.dispose();
});

test('slow A response cannot replace B; filtering clears selection; old stats explicitly labeled', async () => {
    let resolveA, fail = false;
    const h = harness(url => { if (url.includes('size=20') && url.includes('executionId=101')) return new Promise(resolve => { resolveA = resolve; }); if (url.includes('size=20')) return { items: [row(2, false)], total: 1 }; if (url.includes('/stats') && fail) throw new Error('fixture stats outage'); });
    h.view.setExecution(101); h.view.setVisible(true); await flush(); const a = h.calls.find(c => c.url.includes('size=20')).options.signal;
    h.view.setExecution(202, [{ executionId: 202 }]); await flush(); resolveA({ items: [row(99)], total: 1 }); await flush(); assert.equal(a.aborted, true); assert.equal(h.root.querySelector('tr[data-dp-id]').dataset.dpId, '2');
    fail = true; const poll = [...h.timers.values()].find(t => t.ms === 10000); h.timers.clear(); await poll.fn(); await flush(); assert.match(h.find('measuredAt').textContent, /旧快照/);
    h.find('selectPage').checked = true; h.find('selectPage').dispatch('change'); h.find('protected').value = 'true'; h.find('protected').dispatch('change'); await flush(); assert.match(h.find('selection').textContent, /0/); h.view.dispose();
});

test('GET reader never analyzes; escaped text, explicit POST, baseline missing, deleted evidence survives', async () => {
    const record = row(); const analysis = { analysisVersion: 1, parserVersion: { identityExtraction: 3 }, analyzedAt: '2026-10-24', baselineCoverage: 'BASELINE_MISSING', coverage: { status: 'PARTIAL', scannedPages: 1, totalPages: 2 }, deltaAddedBindings: null, after: [{ authorName: '<script>evil()</script>', email: 'fixture@example.invalid', identityEvidence: 'quote' }], qualificationFacts: [{ authorName: 'Fixture', kind: 'DOCTORAL_STUDENT', quote: '<b>quote</b>', paragraph: 1, paperYear: 2023 }] };
    let deleted = false;
    const h = harness(url => { if (/\/1$/.test(url)) return { record: { ...record, state: deleted ? 'DELETED' : 'STORED' }, paper: {}, outcome: null, analysis, attempts: [{ id: 11, kind: 'EMBEDDED_TEXT', status: deleted ? 'DELETED' : 'SAVED', readable: !deleted }] }; if (url.endsWith('/analysis')) return analysis; });
    h.view.setVisible(true); await flush(); h.act('view').click(); await flush(); assert.equal(h.calls.filter(c => c.url.endsWith('/analysis')).length, 0); assert.equal(h.fetched[0].url, '/talent/api/expert-discovery/papers/1/attempts/11/content');
    assert.match(h.find('text').textContent, /<script>/); assert.equal(h.find('text').children.length, 0); assert.match(h.find('comparisonSummary').textContent, /BASELINE_MISSING/); assert.match(h.find('qualificationCards').textContent, /2023/);
    h.act('analyze').click(); await flush(); assert.equal(h.calls.filter(c => c.url.endsWith('/analysis')).length, 1); assert.equal(h.calls.find(c => c.url.endsWith('/analysis')).options.method, 'POST');
    h.act('close-reader').click(); deleted = true; h.act('view').click(); await flush(); assert.equal(h.act('reader-download').disabled, true); assert.equal(h.act('analyze').disabled, true); assert.match(h.find('qualificationCards').textContent, /博士在读/);
    h.view.dispose(); assert.equal(h.root.querySelector('#discoveryPaperReader').open, false); assert.equal(h.timers.size, 0); assert.equal(h.blobs.size, 0);
});

test('PDF blob released on switch/close/dispose and content URLs honor root context', async () => {
    const h = harness(url => /\/1$/.test(url) ? { record: row(), paper: {}, attempts: [{ id: 11, kind: 'PDF_URL', status: 'SAVED', readable: true }], analysis: null, outcome: null } : undefined, '');
    h.sandbox.fetch = async url => { h.fetched.push({ url }); return { ok: true, headers: { get: () => 'application/pdf' }, blob: async () => new Blob(['%PDF-fixture']) }; };
    h.view.setVisible(true); await flush(); h.act('view').click(); await flush(); assert.equal(h.blobs.size, 1); h.act('close-reader').click(); assert.equal(h.blobs.size, 0);
    assert.equal(h.fetched[0].url, '/api/expert-discovery/papers/1/attempts/11/content'); h.act('download').click(); await flush(); assert.match(h.fetched.at(-1).url, /\?download=true$/);
    h.act('view').click(); await flush(); h.view.setExecution(202); await flush(); assert.equal(h.blobs.size, 0); assert.equal(h.root.querySelector('#discoveryPaperReader').open, false); h.view.dispose();
});

test('pipeline summary is execution-scoped, remountable and never eagerly loads papers', async () => {
    const h = harness(); const summary = h.document.createElement('div'); summary.dataset.dp = 'runSummary'; const counts = h.document.createElement('span'); counts.dataset.dp = 'runCounts'; summary.appendChild(counts); h.root.appendChild(summary);
    h.view.setExecution(321, [{ executionId: 321 }]); h.view.setVisible(false, true); await flush(); assert.match(counts.textContent, /执行 #321/); assert.equal(h.calls.length, 1); assert.match(h.calls[0].url, /stats\?executionId=321/);
    h.view.setVisible(false, false); assert.equal(h.timers.size, 0); h.view.dispose();
});

test('empty/error states stay operable and a wholly protected preview cannot commit', async () => {
    let empty = true;
    const h = harness(url => {
        if (url.includes('size=20')) { if (empty) return { items: [], total: 0 }; throw new Error('list fixture unavailable'); }
        if (url.endsWith('/delete-preview')) return { ...deletion(), deletableFiles: 0, protectedRecords: [1] };
    });
    h.view.setVisible(true); await flush(); assert.match(h.find('rows').textContent, /暂无论文原文记录/); assert.equal(h.find('selectPage').disabled, true);
    empty = false; h.find('status').value = 'DELETED'; h.find('status').dispatch('change'); await flush(); assert.match(h.find('error').textContent, /列表读取失败/); h.view.dispose();
    const protectedOnly = harness(url => url.endsWith('/delete-preview') ? { ...deletion(), deletableFiles: 0, protectedRecords: [1] } : undefined);
    protectedOnly.view.setVisible(true); await flush(); protectedOnly.find('selectPage').checked = true; protectedOnly.find('selectPage').dispatch('change'); protectedOnly.act('delete').click(); await flush(); assert.equal(protectedOnly.act('confirm-delete').disabled, true); protectedOnly.view.dispose();
});
