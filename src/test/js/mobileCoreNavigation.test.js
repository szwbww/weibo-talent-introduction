const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const { test } = require('node:test');
const root = path.join(__dirname, '../../main/resources/static');
const source = fs.readFileSync(path.join(root, 'app.js'), 'utf8');
const html = fs.readFileSync(path.join(root, 'index.html'), 'utf8');
function fn(name) {
    const match = source.match(new RegExp('(?:async )?function ' + name + '\\([^)]*\\) \\{[\\s\\S]*?\\n\\}'));
    assert.ok(match, name + ' must exist');
    return match[0];
}
function element() {
    return { dataset: {}, style: {}, children: [], listeners: {}, scrollTop: 0, innerHTML: '', textContent: '', hidden: false,
        classList: { add() {}, remove() {}, toggle() {} },
        replaceChildren() { this.children = []; }, appendChild(e) { this.children.push(e); },
        addEventListener(type, cb) { this.listeners[type] = cb; }, focus() { this.focused = true; }, isConnected: true,
        scrollIntoView() { this.scrolled = true; } };
}
function setup() {
    const els = Object.fromEntries(['#view-contacts', '#mobileCoreView', '#mobileContactsBack', '#mobileContactStatus', '.main', '#contactList', '#contactDetail', '#contactHeadActions', '#expertIndexLevel', '.contact-detail-panel', '#viewTitle', '#viewSubtitle', '#manualMailOption', '#senderBindingSelect'].map(k => [k, element()]));
    els['#view-contacts'].dataset.mobilePane = 'list';
    els['.main'].scrollTop = 420; els['#contactList'].scrollTop = 33;
    const tabs = [...html.matchAll(/class="nav-tab[^\"]*" data-view="([^\"]+)"/g)].map(m => ({ ...element(), dataset: {view: m[1]}, textContent: m[1] }));
    const frames = [], calls = [], trigger = element();
    const sandbox = {
        mobileContactPresentation: { generation: 0, mainScrollTop: 0, listScrollTop: 0, trigger: null },
        mobileCoreNavigationBound: false,
        state: { view: 'contacts', contacts: [], selectedExpertOrcid: 'A', page: 2, query: 'Ada', accounts: [], monitoring: {openTracking: {}} },
        viewMeta: Object.fromEntries(tabs.map(t => [t.dataset.view, [t.textContent, '']])),
        $: k => els[k] || null, $$: k => k === '.nav-tabs .nav-tab[data-view]' ? tabs : [],
        document: { activeElement: trigger, createElement: () => element(), querySelector: k => els[k] || null },
        window: { innerWidth: 390, matchMedia: () => ({matches: true, addEventListener() {}}) },
        requestAnimationFrame: cb => frames.push(cb),
        localStorage: {setItem() { throw new Error('unexpected storage write'); }},
        clearMailboxExpertFocus: () => calls.push('clear-focus'), setView: v => calls.push(v),
        refreshCurrentView: () => calls.push('refresh'), showStatus: (...args) => calls.push(args),
        unmountAiTrainingTrustReply() {}, unmountMailboxTrustReplyHosts() {}, closeOpenTrackingDetail() {},
        taskActivityState: {listRequestSequence: 0}, closeTaskActivityDetail() {}, resumeProgressPollingIfNeeded() {}, stopTaskWatcher() {},
        indexLevelLabels: {}, operatorStatusOptions: [], indexLevelOptions: [],
        escapeHtml: v => String(v || ''), renderAcademicProfilePanel: () => '', renderExpertTagEditor: () => '',
        renderKeywords: () => '', badge: () => '', labelStatus: () => '', renderNoContactMaterialsEmpty: () => '',
        optionsFromArray: () => '', renderManualAttentionBanner: () => '', updateSenderBindingDirtyState() {},
        renderExpertMaterialRow: () => '', renderExpertDocuments: () => '', renderOperatorLogs: () => '',
        renderMailSendOptionGroups: () => '', renderMeetingSchedule: () => '', formatStatusTransition: () => '',
        renderContactListItems: () => calls.push('list-render'), loadEmailAliases() {},
        fetchExpertTagsFromEs: async () => ({tags: []}), loadMailSendOptions: async () => [],
        api: async () => [],
    };
    vm.createContext(sandbox);
    for (const name of ['isMobileCoreViewport', 'navigateFromCoreMenu', 'bindMobileCoreNavigation', 'onMobileCoreViewportChange', 'beginContactDetailPresentation', 'isCurrentContactDetailPresentation', 'finishContactDetailPresentation', 'failContactDetailPresentation', 'returnToMobileContactsList', 'scrollBackToContactsList', 'backToListBtnHtml', 'renderDetailSubTabs', 'showExpertDetail', 'loadContactDetail', 'openContactInList']) vm.runInContext(fn(name), sandbox);
    return {s: sandbox, els, tabs, frames, calls, trigger};
}
function deferred() { let resolve, reject; const promise = new Promise((yes, no) => {resolve = yes; reject = no;}); return {promise, resolve, reject}; }
function contact(id) { return {contact: {id, expertName: id, expertEmail: id + '@example.org', orcidId: id}}; }

test('one menu source, ten options, mailbox reset once, invalid values ignored', () => {
    const {s, els, tabs, calls} = setup();
    s.bindMobileCoreNavigation(); s.bindMobileCoreNavigation();
    assert.equal(tabs.length, 10);
    assert.equal(els['#mobileCoreView'].children.length, 10);
    els['#mobileCoreView'].value = 'mailbox';
    els['#mobileCoreView'].listeners.change();
    assert.deepEqual(calls, ['clear-focus', 'mailbox']);
    s.navigateFromCoreMenu(undefined); s.navigateFromCoreMenu('toString');
    assert.equal(calls.length, 2);
    assert.match(fn('bindEvents'), /\$\$\("\.nav-tabs \.nav-tab\[data-view\]"\)/);
});

test('programmatic navigation syncs menu without clearing explicit mailbox focus', () => {
    const {s, els, calls} = setup();
    vm.runInContext(fn('setView'), s);
    s.setView('mailbox');
    assert.equal(els['#mobileCoreView'].value, 'mailbox');
    assert.ok(!calls.includes('clear-focus'));
});

test('return preserves selection, filters and both scroll owners without querying', () => {
    const {s, els, frames, calls, trigger} = setup();
    s.beginContactDetailPresentation();
    els['.main'].scrollTop = 0; els['#contactList'].scrollTop = 0;
    s.returnToMobileContactsList(); frames.splice(0).forEach(cb => cb());
    assert.equal(els['#view-contacts'].dataset.mobilePane, 'list');
    assert.equal(els['.main'].scrollTop, 420); assert.equal(els['#contactList'].scrollTop, 33);
    assert.equal(s.state.page, 2); assert.equal(s.state.query, 'Ada'); assert.equal(s.state.selectedExpertOrcid, 'A');
    assert.equal(trigger.focused, true); assert.deepEqual(calls, []);
});

for (const kind of ['expert', 'contact']) {
    function run(s, id) { return kind === 'expert' ? s.showExpertDetail({orcidId: id, displayName: id}) : s.loadContactDetail(id); }
    function requests(s, pending) {
        if (kind === 'expert') s.fetchExpertTagsFromEs = id => pending[id].promise;
        else s.api = url => /^\/api\/expert-contacts\/[AB]$/.test(url) ? pending[url.split('/').pop()].promise : Promise.resolve([]);
    }
    test(`${kind}: response A cannot overwrite B`, async () => {
        const {s, els, frames, calls} = setup(), pending = {A: deferred(), B: deferred()};
        requests(s, pending);
        const a = run(s, 'A'), b = run(s, 'B');
        pending.B.resolve(kind === 'expert' ? {tags: []} : contact('B')); await b;
        const detail = els['#contactDetail'].innerHTML, head = els['#contactHeadActions'].innerHTML;
        assert.match(detail, /<h2>B<\/h2>/, JSON.stringify(calls));
        pending.A.resolve(kind === 'expert' ? {tags: []} : contact('A')); await a;
        assert.equal(els['#contactDetail'].innerHTML, detail); assert.equal(els['#contactHeadActions'].innerHTML, head);
        frames.splice(0).forEach(cb => cb());
        assert.equal(els['#view-contacts'].dataset.mobilePane, 'detail');
    });
    for (const leave of ['return', 'navigation']) test(`${kind}: late response after ${leave} cannot mutate detail or scroll`, async () => {
        const {s, els, frames} = setup(), pending = {A: deferred()}; requests(s, pending);
        const a = run(s, 'A');
        if (leave === 'return') s.returnToMobileContactsList();
        else { vm.runInContext(fn('setView'), s); s.setView('tasks'); }
        const detail = els['#contactDetail'].innerHTML, head = els['#contactHeadActions'].innerHTML;
        pending.A.resolve(kind === 'expert' ? {tags: []} : contact('A')); await a;
        frames.splice(0).forEach(cb => cb());
        assert.equal(els['#contactDetail'].innerHTML, detail); assert.equal(els['#contactHeadActions'].innerHTML, head);
        assert.ok(!els['.contact-detail-panel'].scrolled);
    });
    test(`${kind}: failure hides prior actionable content and offers return`, async () => {
        const {s, els} = setup(), pending = {A: deferred()}; requests(s, pending);
        const a = run(s, 'A'); pending.A.reject(new Error('offline'));
        if (kind === 'contact') await assert.rejects(a, /offline/); else await a;
        assert.equal(els['#view-contacts'].dataset.mobileLoading, 'true');
        assert.equal(els['#mobileContactStatus'].textContent, '专家详情加载失败，请返回列表重试');
        assert.equal(els['#mobileContactStatus'].hidden, false);
        s.returnToMobileContactsList(); assert.equal(els['#view-contacts'].dataset.mobilePane, 'list');
    });
}

test('CSS exactly matches approved block, unique controls, resources share one new key', () => {
    const plan = fs.readFileSync(path.join(__dirname, '../../../docs/plans/2026-10-03/mobile-core-01-shell-contacts.md'), 'utf8');
    const css = fs.readFileSync(path.join(root, 'styles.css'), 'utf8');
    const block = text => text.match(/\/\* mobile-core-01:start \*\/[\s\S]*?\/\* mobile-core-01:end \*\//)?.[0];
    assert.equal(block(css), block(plan));
    for (const id of ['mobileCoreView', 'mobileContactsBack', 'mobileContactStatus']) assert.equal(html.split(`id="${id}"`).length - 1, 1);
    const keys = [...html.matchAll(/[?]v=([^" ]+)/g)].map(m => m[1]);
    assert.equal(keys.length, 11); assert.equal(new Set(keys).size, 1);
    assert.notEqual(keys[0], '20261002-mailbox-last-reply');
});

test('mobile shell overrides the later WorldClock header nowrap rule', () => {
    const css = fs.readFileSync(path.join(root, 'styles.css'), 'utf8');
    assert.match(css, /@media \(max-width: 760px\)\s*\{\s*\.topnav\.task-center-nav\.world-clock-header\s*\{\s*flex-wrap: wrap; padding: 8px 12px; gap: 8px;/);
});

test('cross-page expert opening enters detail before list fetch and ignores return during fetch', async () => {
    const {s, els, calls} = setup(), pending = deferred();
    s.loadContacts = () => pending.promise;
    const opening = s.openContactInList(10);
    assert.equal(els['#view-contacts'].dataset.mobilePane, 'detail');
    s.returnToMobileContactsList();
    pending.resolve(); await opening;
    assert.equal(els['#view-contacts'].dataset.mobilePane, 'list');
    assert.equal(s.state.selectedExpertOrcid, 'A');
    assert.ok(!calls.includes('list-render'));
});

test('successful detail pending animation frame cannot scroll after return or leaving contacts', async () => {
    for (const leave of ['return', 'navigation']) {
        const {s, els, frames} = setup();
        await s.showExpertDetail({orcidId: 'A', displayName: 'A'});
        if (leave === 'return') s.returnToMobileContactsList();
        else { vm.runInContext(fn('setView'), s); s.setView('tasks'); }
        frames.splice(0).forEach(cb => cb());
        assert.ok(!els['.contact-detail-panel'].scrolled);
    }
});

test('contact sender-account await cannot write a stale header', async () => {
    const {s, els} = setup(), pending = deferred();
    let requestedAccounts = false;
    s.api = url => {
        if (url === '/api/mail/sender-accounts') { requestedAccounts = true; return pending.promise; }
        return Promise.resolve(url === '/api/expert-contacts/A' ? contact('A') : []);
    };
    const a = s.loadContactDetail('A');
    while (!requestedAccounts) await Promise.resolve();
    s.returnToMobileContactsList(); pending.resolve([]); await a;
    assert.equal(els['#contactHeadActions'].innerHTML, '');
    assert.equal(els['#contactDetail'].innerHTML, '');
});


test('cross-page list-fetch failure shows return and preserves rejection for caller', async () => {
    const {s, els} = setup();
    s.loadContacts = async () => { throw new Error('offline'); };
    await assert.rejects(s.openContactInList(10), /offline/);
    assert.equal(els['#mobileContactStatus'].textContent, '专家详情加载失败，请返回列表重试');
    assert.equal(els['#view-contacts'].dataset.mobileLoading, 'true');
    s.returnToMobileContactsList();
    assert.equal(els['#view-contacts'].dataset.mobilePane, 'list');
});


for (const action of ['send-manual-mail', 'rebind-sender-account', 'clear-sender-change-mark']) {
    for (const navigation of ['return', 'selectB', 'leavePage', 'unchanged']) {
        test(`${action}: pending business completion respects ${navigation}`, async () => {
            const {s, els, frames, calls} = setup(), pending = deferred(), requests = [];
            s.state.accounts = [{accountCode: 'sender', enabled: true}];
            s.api = (url, options) => {
                requests.push({url, options});
                if (options?.method === 'POST') return pending.promise;
                return Promise.resolve(/^\/api\/expert-contacts\/[AB]$/.test(url) ? contact(url.split('/').pop()) : []);
            };
            s.loadContacts = async () => calls.push('collection-refresh');
            els['#manualMailOption'].value = 'INTRODUCTION:1';
            els['#senderBindingSelect'].value = 'sender';
            vm.runInContext(fn('handleContactAction'), s);
            await s.loadContactDetail('A'); frames.splice(0).forEach(cb => cb());
            els['.contact-detail-panel'].scrolled = false;
            const work = s.handleContactAction({dataset: {id: 'A', action}});
            assert.equal(requests.filter(r => r.options?.method === 'POST').length, 1);
            if (navigation === 'return') s.returnToMobileContactsList();
            if (navigation === 'selectB') {
                s.state.selectedExpertOrcid = 'B';
                await s.loadContactDetail('B'); frames.splice(0).forEach(cb => cb());
                els['.contact-detail-panel'].scrolled = false;
            }
            if (navigation === 'leavePage') { vm.runInContext(fn('setView'), s); s.setView('tasks'); }
            const before = {head: els['#contactHeadActions'].innerHTML, detail: els['#contactDetail'].innerHTML, count: requests.length};
            pending.resolve({}); await work; frames.splice(0).forEach(cb => cb());
            assert.equal(requests.filter(r => r.options?.method === 'POST').length, 1);
            assert.ok(calls.some(c => Array.isArray(c) && /已/.test(c[0])), 'business success remains visible');
            assert.equal(calls.filter(c => c === 'collection-refresh').length, action === 'send-manual-mail' ? 0 : 1);
            if (navigation !== 'unchanged') {
                assert.equal(requests.length, before.count, 'stale completion must not start detail reads');
                assert.equal(els['#contactHeadActions'].innerHTML, before.head);
                assert.equal(els['#contactDetail'].innerHTML, before.detail);
                assert.equal(els['#view-contacts'].dataset.mobilePane, navigation === 'selectB' ? 'detail' : 'list');
                assert.ok(!els['.contact-detail-panel'].scrolled);
                assert.equal(s.state.selectedExpertOrcid, navigation === 'selectB' ? 'B' : 'A');
            } else {
                assert.ok(requests.length > before.count, 'unchanged expert still refreshes after successful action');
                assert.match(els['#contactDetail'].innerHTML, /<h2>A<\/h2>/);
            }
        });
    }
}

test('meeting confirmation completion cannot reopen a returned detail', async () => {
    const {s, els, calls} = setup(), pending = deferred(), requests = [];
    vm.runInContext(fn('confirmMeetingSchedule'), s);
    s.formValues = () => ({chinaTime: '2026-10-03 10:00', meetingTool: 'Zoom', meetingLink: 'https://example.org', note: ''});
    s.loadContacts = async () => calls.push('collection-refresh');
    s.api = (url, options) => { requests.push({url, options}); return pending.promise; };
    s.beginContactDetailPresentation();
    const work = s.confirmMeetingSchedule({dataset: {contactId: 'A', scheduleId: '1'}});
    s.returnToMobileContactsList(); pending.resolve({}); await work;
    assert.equal(requests.length, 1);
    assert.equal(requests[0].options.method, 'POST');
    assert.equal(calls.filter(c => c === 'collection-refresh').length, 1);
    assert.equal(els['#view-contacts'].dataset.mobilePane, 'list');
});

function eventHandler(marker) {
    const start = source.indexOf(marker);
    assert.ok(start >= 0);
    const end = source.indexOf('\n    });', start);
    return source.slice(start + marker.indexOf('async (event)'), end + 6);
}
for (const action of ['add-alias', 'delete-alias']) test(`${action}: old completion cannot replace selected B`, async () => {
    const {s, els, calls} = setup(), pending = deferred(), requests = [];
    const email = {value: 'a@example.org'};
    const oldQuery = s.$;
    s.$ = q => q === '#newAliasEmail' ? email : oldQuery(q);
    s.openActionDialog = async () => true;
    s.api = (url, options) => { requests.push({url, options}); return pending.promise; };
    s.beginContactDetailPresentation();
    const handler = vm.runInContext('(' + eventHandler('    document.addEventListener("click", async (event) => {\n        const element = event.target.closest("[data-action]");') + ')', s);
    await handler({target: {closest: () => ({dataset: {action, contactId: 'A', aliasId: '1'}})}});
    s.beginContactDetailPresentation(); s.state.selectedExpertOrcid = 'B';
    els['#contactDetail'].innerHTML = 'B';
    pending.resolve({}); await Promise.resolve(); await Promise.resolve();
    assert.equal(requests.length, 1);
    assert.equal(els['#contactDetail'].innerHTML, 'B');
    assert.equal(s.state.selectedExpertOrcid, 'B');
    assert.ok(calls.some(c => Array.isArray(c) && c[0].includes('别名')));
});

test('save-contact changes completion keeps returned list and still refreshes collection', async () => {
    const {s, els, calls} = setup(), pending = deferred(), started = deferred();
    const save = {dataset: {contactId: 'A'}, disabled: false, textContent: ''};
    const status = {dataset: {original: 'OLD'}, value: 'NEW'};
    const oldQuery = s.$;
    s.$ = q => q === '#saveContactChangesBtn' ? save : q === '#operatorStatusSelect' ? status : oldQuery(q);
    s.openActionDialog = async () => true;
    let writes = 0;
    s.handleOperatorStatusChange = () => { writes++; started.resolve(); return pending.promise; };
    s.loadContacts = async () => calls.push('collection-refresh');
    s.beginContactDetailPresentation();
    const handler = vm.runInContext('(' + eventHandler('    $("#contactHeadActions").addEventListener("click", async (event) => {') + ')', s);
    const work = handler({target: {closest: q => q === '#saveContactChangesBtn' ? save : null}});
    await started.promise; assert.equal(writes, 1);
    s.returnToMobileContactsList(); pending.resolve(); await work;
    assert.equal(els['#view-contacts'].dataset.mobilePane, 'list');
    assert.equal(els['#contactDetail'].innerHTML, '');
    assert.equal(calls.filter(c => c === 'collection-refresh').length, 1);
});
