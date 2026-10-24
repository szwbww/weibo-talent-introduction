const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const { test } = require('node:test');
const root = path.resolve(__dirname, '../../main/resources/static');
const app = fs.readFileSync(path.join(root, 'app.js'), 'utf8');
const html = fs.readFileSync(path.join(root, 'index.html'), 'utf8');
const css = fs.readFileSync(path.join(root, 'styles.css'), 'utf8');
const plan = fs.readFileSync(path.resolve(__dirname, '../../../docs/plans/2026-10-23/discovery-paper-07-apollo-frontend.md'), 'utf8');
const section = app.slice(app.indexOf('// apollo-verification-ui:start'), app.indexOf('// apollo-verification-ui:end'));
const extract = name => app.match(new RegExp('(?:async\\s+)?function\\s+' + name + '\\s*\\([^)]*\\)\\s*\\{[\\s\\S]*?\\n\\}'))[0];
const saved = (searchEnabled = true, detailEnabled = false, revision = 3) => ({ revision, source: 'SAVED', searchEnabled, detailEnabled, effectiveSearch: searchEnabled, effectiveDetails: searchEnabled && detailEnabled, keyConfigured: true, serviceEnabled: true, workerEnabled: true, updatedAt: '2026-10-24T00:00:00Z' });
const capability = { enabled: true, ready: true, apolloDefault: false, reasonCodes: [] };
function element() {
    return { hidden: false, disabled: false, checked: false, innerHTML: '', textContent: '', dataset: {}, listeners: {},
        classList: { add() {}, remove() {} }, closest() { return this; }, contains() { return false; },
        addEventListener(k, v) { this.listeners[k] = v; }, removeEventListener(k) { delete this.listeners[k]; } };
}
function setup(handler = async url => url.endsWith('apollo-settings') ? saved() : capability) {
    const elements = new Map();
    for (const id of ['taskModalRunBody', 'taskModalRunHead', 'discoveryVerificationLogHost', 'taskLaunchApolloRow', 'taskLaunchApolloAssist', 'taskLaunchApolloHint', 'apcPaidDetails', 'apcSave', 'apcReload', 'apcSaveState', 'apcError', 'apcEffective', 'apcSavedAt']) {
        assert.ok(html.includes(`id="${id}"`), `${id} exists in actual HTML`); elements.set(id, element());
    }
    const calls = [], messages = [];
    const s = { console, URLSearchParams, AbortController, Map, Set, document: { activeElement: null },
        $: id => elements.get(id.slice(1)), currentTaskModal: { taskType: 'EXPERT_DISCOVERY', generation: 1 },
        setTimeout() {}, clearTimeout() {}, formatBeijingInstant: String,
        showStatus: (...args) => messages.push(args), api: async (url, options = {}) => { calls.push({ url, options }); return handler(url, options); } };
    s.isCurrentTaskModal = (type, generation) => s.currentTaskModal?.taskType === type && s.currentTaskModal?.generation === generation;
    vm.createContext(s);
    vm.runInContext(extract('escapeHtml') + '\n' + section + '\n' + extract('postDiscoveryLaunch') + '\nconst PIPELINE_LAUNCH_TIMEOUT_MS=15000; globalThis.state=()=>apvState;', s);
    s.apvInit('EXPERT_DISCOVERY', 1);
    s.elements = elements; s.calls = calls; s.messages = messages;
    s.change = (id, checked) => s.apcEvent({ type: 'change', target: { id, checked } });
    return s;
}
const deferred = () => { let resolve, reject; const promise = new Promise((a, b) => { resolve = a; reject = b; }); return { promise, resolve, reject }; };

test('S1/S2/S3 exact approved DOM and scoped CSS; assets share the new key', () => {
    const dom = plan.match(/```html\n([\s\S]*?)\n```/)[1];
    assert.ok(html.includes(dom));
    const expected = plan.match(/```css\n([\s\S]*?)\n```/)[1];
    const start = '/* discovery-apollo-settings-contract:start */', end = '/* discovery-apollo-settings-contract:end */';
    assert.equal(css.slice(css.indexOf(start), css.indexOf(end) + end.length), expected);
    const line = [...plan.matchAll(/```html\n([\s\S]*?)\n```/g)][1][1];
    assert.ok(app.includes(line.replace('<span data-apc="runConfig"></span>', '<span data-apc="runConfig">${apcRunConfig(summary.apolloAccess)}</span>').replace('<span data-apc="runAttempts"></span>', '<span data-apc="runAttempts">${apcRunAttempts(summary.apolloAttempts)}</span>')));
    assert.doesNotMatch(dom, /style=|onclick=/);
    for (const match of dom.matchAll(/(?:aria-labelledby|aria-describedby|for)="([^"]+)"/g)) assert.ok(dom.includes(`id="${match[1]}"`));
    const keys = [...html.matchAll(/\?v=([^"<>]+)/g)].map(m => m[1]);
    assert.equal(keys.length, 17);
    assert.equal(new Set(keys).size, 1);
    assert.ok(keys[0].length > 0);
    assert.match(html, /<script src="task-modal-runtime.js"><\/script>/);
});

test('I1/I2 four independent combinations, draft-only edits, server CAS readback and reopen', async () => {
    let stored = saved();
    const s = setup(async (url, options) => {
        if (!url.endsWith('apollo-settings')) return capability;
        if (options.method === 'PUT') { const body = JSON.parse(options.body); assert.equal(body.revision, stored.revision); stored = saved(body.searchEnabled, body.detailEnabled, stored.revision + 1); }
        return stored;
    });
    await s.apvLoadCapabilities(1);
    for (const [search, detail] of [[false, false], [false, true], [true, true], [true, false]]) {
        const before = s.calls.filter(c => c.options.method === 'PUT').length;
        s.change('taskLaunchApolloAssist', search); s.change('apcPaidDetails', detail);
        assert.equal(s.elements.get('apcPaidDetails').disabled, false);
        assert.equal(s.calls.filter(c => c.options.method === 'PUT').length, before);
        assert.equal(s.apvCanLaunch(), false);
        await s.apcSaveSettings();
        assert.equal(s.state().apolloSettings.saved.searchEnabled, search);
        assert.equal(s.state().apolloSettings.saved.detailEnabled, detail);
        assert.equal(s.apvCanLaunch(), true);
        if (!search && detail) assert.match(s.elements.get('apcEffective').textContent, /搜索关闭期间不调用/);
        s.apvDispose(); s.apvInit('EXPERT_DISCOVERY', 1); await s.apvLoadCapabilities(1);
        assert.equal(s.elements.get('taskLaunchApolloAssist').checked, search);
        assert.equal(s.elements.get('apcPaidDetails').checked, detail);
    }
    s.change('apcPaidDetails', true); await s.apcLoadSettings();
    assert.equal(s.state().apolloSettings.dirty, false);
    assert.equal(s.elements.get('apcPaidDetails').checked, false);
});

test('I1/I4 duplicate saves are single-flight, disabled inputs and 409/503 preserve draft', async () => {
    let pending;
    const s = setup(async (url, options) => options.method === 'PUT' ? pending.promise : url.endsWith('apollo-settings') ? saved() : capability);
    await s.apvLoadCapabilities(1);
    s.change('apcPaidDetails', true);
    for (const status of [409, 503, 400, 401]) {
        pending = deferred();
        const first = s.apcSaveSettings(); const second = s.apcSaveSettings();
        assert.equal(s.elements.get('apcPaidDetails').disabled, true);
        assert.equal(s.elements.get('apcReload').disabled, true);
        assert.equal(s.state().apolloSettings.status, 'saving');
        pending.reject(Object.assign(Error('failure'), { status })); await Promise.all([first, second]);
        assert.equal(s.state().apolloSettings.draft.detailEnabled, true);
        assert.equal(s.state().apolloSettings.saved.detailEnabled, false);
        assert.equal(s.state().apolloSettings.dirty, true);
        assert.equal(s.elements.get('apcError').textContent, status === 409 ? '配置已被其他会话修改，请恢复已保存值后重新设置' : '保存失败，原配置仍生效');
    }
    assert.equal(s.calls.filter(c => c.options.method === 'PUT').length, 4);
    pending = deferred(); const saving = s.apcSaveSettings(); pending.resolve(saved(false, true, 8)); await saving;
    assert.equal(s.state().apolloSettings.revision, 8);
    assert.equal(s.elements.get('taskLaunchApolloAssist').checked, false, 'server readback wins');
});

test('I1 GET failure/malformed/404 has no fallback, retry required; local-only does not need Apollo ready', async () => {
    for (const status of [401, 404, 503, 'malformed']) {
        let failed = true;
        const s = setup(async url => {
            if (!url.endsWith('apollo-settings')) return { enabled: false, ready: false, apolloDefault: true, reasonCodes: ['VERIFICATION_DISABLED'] };
            if (failed) { if (status === 'malformed') return {}; throw Object.assign(Error('failure'), { status }); }
            return { ...saved(false, true), keyConfigured: false, serviceEnabled: false, workerEnabled: false };
        });
        await s.apvLoadCapabilities(1);
        assert.equal(s.state().apolloSettings.saved, null);
        assert.equal(s.elements.get('apcSave').disabled, true);
        assert.equal(s.apvCanLaunch(), false);
        if (status === 404) assert.match(s.elements.get('apcError').textContent, /后端未就绪/);
        failed = false; await s.apcLoadSettings();
        assert.equal(s.apvCanLaunch(), true);
        assert.match(s.elements.get('apcEffective').textContent, /API Key：未配置 · 服务：未启用 · Worker：未启用/);
    }
});

test('I4 slow GET/capabilities/PUT from A cannot mutate B or closed modal; request sequence wins', async () => {
    const old = deferred(); const s = setup(() => old.promise);
    const loading = s.apvLoadCapabilities(1), original = s.state();
    s.apvDispose(); s.currentTaskModal.generation = 2; s.apvInit('EXPERT_DISCOVERY', 2);
    s.api = async url => url.endsWith('apollo-settings') ? saved(false, true, 9) : capability;
    await s.apvLoadCapabilities(2); old.resolve(saved()); await loading;
    assert.equal(s.state().apolloSettings.revision, 9);
    assert.ok([...original.apolloSettings.controllers].every(c => c.signal.aborted));
    const put = deferred(); s.api = () => put.promise; s.change('apcPaidDetails', false);
    const saving = s.apcSaveSettings(); s.apvDispose(); put.resolve(saved()); await saving;
    assert.equal(s.state(), null);
    assert.deepEqual(Object.keys(s.elements.get('taskLaunchApolloRow').listeners), []);
    s.apvInit('EXPERT_DISCOVERY', 2);
    const first = deferred(), second = deferred(); let count = 0;
    s.api = () => ++count === 1 ? first.promise : second.promise;
    const a = s.apcLoadSettings(), b = s.apcLoadSettings(); second.resolve(saved(false, true, 12)); await b;
    first.resolve(saved()); await a;
    assert.equal(s.state().apolloSettings.revision, 12);
});

test('I3 run snapshot/channel counts never read form; unknown is not zero; escaped metadata', async () => {
    const s = setup(); await s.apvLoadCapabilities(1);
    s.change('apcPaidDetails', true);
    const summary = { coverage: 'NOT_RECORDED', counts: null, apolloAccess: { searchEnabled: true, detailEnabled: true, settingRevision: 2, source: 'SAVED' }, apolloAttempts: { search: 3, detail: 2, other: 1, pendingDispatch: 1 } };
    const output = s.apvSummaryHtml(summary);
    for (const value of ['付费详情 开', '保存版本 2', '免费搜索尝试 3', '付费详情尝试 2', '其他 1', '登记后尚未确认完成 1', '不等于实际扣费']) assert.ok(output.includes(value));
    assert.match(s.apcRunConfig({ ...summary.apolloAccess, source: 'LEGACY' }), /旧任务沿用原规则/);
    assert.equal(s.apcRunConfig(null), '本次配置：未记录');
    assert.equal(s.apcRunAttempts(null), '分渠道尝试：未记录');
    assert.match(s.apcRunAttempts({ search: 0, detail: 0, other: 0, pendingDispatch: 0 }), /免费搜索尝试 0/);
    assert.doesNotMatch(s.apcRunAttempts({ search: '<img>', other: 0 }), /<img>/);
    assert.match(s.apcRunAttempts({}), /未记录/);
});

test('I1 default/keyword/continuous omit override; explicit external false/true remain compatible', async () => {
    const s = setup();
    for (const keywords of ['', 'machine learning']) for (const continuous of [false, true]) {
        await s.postDiscoveryLaunch(keywords, ['OPENALEX'], true, continuous);
        const { url, options } = s.calls.at(-1);
        assert.ok(!url.includes('apolloAssist')); assert.ok(!Object.hasOwn(JSON.parse(options.body || '{}'), 'apolloAssist'));
    }
    for (const override of [false, true]) {
        await s.postDiscoveryLaunch('x', [], false, false, override); assert.ok(s.calls.at(-1).url.includes('apolloAssist=' + override));
        await s.postDiscoveryLaunch('', [], false, false, override); assert.equal(JSON.parse(s.calls.at(-1).options.body).apolloAssist, override);
    }
});
