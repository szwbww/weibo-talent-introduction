const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const { test } = require('node:test');
const base = 'src/main/resources/static/';
const source = fs.readFileSync(base + 'app.js', 'utf8');
function fn(name) {
    const match = source.match(new RegExp('(?:async\\s+)?function\\s+' + name + '\\s*\\([^)]*\\)\\s*\\{[\\s\\S]*?\\n\\}'));
    assert.ok(match, 'missing production function ' + name);
    return match[0];
}

test('routing labels distinguish unknown from unchecked and expose no false negative', () => {
    const ctx = vm.createContext({});
    vm.runInContext(fn('discoveryEmailRoutingLabel'), ctx);
    assert.equal(ctx.discoveryEmailRoutingLabel(null), '收信服务：未检测');
    assert.equal(ctx.discoveryEmailRoutingLabel({ status: 'UNKNOWN' }), '收信服务：未确认');
    assert.equal(ctx.discoveryEmailRoutingLabel({ status: 'MICROSOFT' }), '微软收信服务');
});

test('real DOM contains filter, lookup button and status with accessible labels', () => {
    const html = fs.readFileSync(base + 'index.html', 'utf8');
    for (const id of ['discoveryEmailRoutingFilter', 'discoveryEmailRoutingRefresh', 'discoveryEmailRoutingStatus']) {
        assert.match(html, new RegExp('id="' + id + '"'));
    }
    assert.match(html, /value="MICROSOFT"/);
    assert.match(html, /value="UNCHECKED"/);
});

test('list request sends routing filter to server', () => {
    const ctx = vm.createContext({ URLSearchParams, DISCOVERY_REVIEW_PATH: '/review', DISCOVERY_REVIEW_LEVEL: 'RAW',
        DISCOVERY_REVIEW_TAG: 'discovered', discoveryReviewState: { page: 1, pageSize: 20, emailRouting: 'MICROSOFT' } });
    vm.runInContext(fn('discoveryReviewListPath'), ctx);
    assert.match(ctx.discoveryReviewListPath(false), /emailRouting=MICROSOFT/);
});

function refreshContext(api) {
    const status = { textContent: '' };
    const state = { emailRoutingBusy: false, loading: false, requestSeq: 1, listSeq: 1, tab: 'review',
        rows: [{ docId: 'one' }, { docId: 'two' }] };
    const ctx = vm.createContext({ discoveryReviewState: state, api, discoveryReviewElements: () => ({ routingStatus: status }),
        discoveryReviewListCurrent: (seq, listSeq) => state.requestSeq === seq && state.listSeq === listSeq,
        renderDiscoveryReviewRows() {}, refreshDiscoveryReviewList() {}, discoveryReviewErrorMessage: e => e.message });
    vm.runInContext(fn('refreshDiscoveryEmailRoutingPage'), ctx);
    return { ctx, state, status };
}

test('historical lookup is serial, deduplicated and reports partial layer failure', async () => {
    let active = 0, peak = 0;
    const ids = [];
    const { ctx, state, status } = refreshContext(async (path, options) => {
        assert.equal(path, '/api/discovery/email-routing/refresh');
        peak = Math.max(peak, ++active);
        ids.push(JSON.parse(options.body).docId);
        await new Promise(resolve => setImmediate(resolve)); active--;
        return { routing: { status: 'MICROSOFT' }, updatedLayers: ['RAW'], failedLayers: { CANDIDATE: 'VERSION_CONFLICT' } };
    });
    state.rows.push({ docId: 'one' });
    await ctx.refreshDiscoveryEmailRoutingPage();
    assert.deepEqual(ids, ['one', 'two']);
    assert.equal(peak, 1);
    assert.equal(state.emailRoutingBusy, false);
    assert.match(status.textContent, /写入异常 2/);
});

test('leaving page stops further lookup and ignores late result', async () => {
    let resolve, calls = 0;
    const { ctx, state } = refreshContext(() => { calls++; return new Promise(r => { resolve = r; }); });
    const pending = ctx.refreshDiscoveryEmailRoutingPage();
    state.listSeq++;
    resolve({ routing: { status: 'MICROSOFT' }, updatedLayers: ['RAW'] });
    await pending;
    assert.equal(calls, 1);
    assert.equal(state.rows[0].emailRouting, undefined);
    assert.equal(state.emailRoutingBusy, false);
});

test('transport error stops batch and explicitly reports uncertain write outcome', async () => {
    let calls = 0;
    const { ctx, status } = refreshContext(async () => { calls++; throw new Error('timeout'); });
    await ctx.refreshDiscoveryEmailRoutingPage();
    assert.equal(calls, 1);
    assert.match(status.textContent, /当前请求可能已写入/);
});
