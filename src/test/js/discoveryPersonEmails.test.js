"use strict";

// 07-ui 定向 UI 测试（I-1/I-2/S-1/S-2）：
// - 06 人物详情响应 → 列表/头部邮箱摘要与详情只读邮箱卡片（两宿主同一片段）。
// - 历史入口沿用它原有的 contactId + 邮箱；无 contactId 不渲染入口。
// - generation 守卫：旧响应不覆盖新页面。
// - 所有邮箱/来源文本经 escapeHtml(textContent) 转义。
// 用实际函数 + 两个宿主 DOM 骨架，不靠未声明的全局 stub 掩盖引用缺失。

const fs = require("fs");
const path = require("path");
const vm = require("vm");
const assert = require("assert");
const { describe, it } = require("node:test");

const ROOT = path.join(__dirname, "..", "..", "main", "resources", "static");
const appJsSource = fs.readFileSync(path.join(ROOT, "app.js"), "utf-8");
const stylesCssSource = fs.readFileSync(path.join(ROOT, "styles.css"), "utf-8");

function extractFn(name) {
    const regex = new RegExp("(?:async\\s+)?function\\s+" + name + "\\s*\\([^)]*\\)\\s*\\{[\\s\\S]*?\\n\\}");
    const match = appJsSource.match(regex);
    if (!match) throw new Error("Could not find " + name + " in app.js");
    return match[0];
}

function escapeHtml(value) {
    return String(value == null ? "" : value)
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;");
}

// 两个详情宿主的 DOM 骨架：都含同一 host 容器选择器，有宿主另含头部邮箱 span。
function makeRoot(options = {}) {
    const host = { innerHTML: "" };
    const headerSpan = { textContent: "" };
    return {
        host,
        headerSpan,
        querySelector(selector) {
            if (selector.includes("discovery-person-emails-host")) return host;
            if (selector.includes("detail-email-summary")) return options.withHeader ? headerSpan : null;
            return null;
        }
    };
}

function member(docId, email, extra = {}) {
    return {
        docId,
        levels: ["CANDIDATE"],
        email,
        contactId: Object.prototype.hasOwnProperty.call(extra, "contactId") ? extra.contactId : null,
        contactStatus: extra.contactStatus ?? null,
        operatorStatus: extra.operatorStatus ?? null,
        admissionStatus: Object.prototype.hasOwnProperty.call(extra, "admissionStatus") ? extra.admissionStatus : null,
        representative: extra.representative === true,
        stale: false,
        evidenceSource: extra.evidenceSource ?? null
    };
}

function personFixture(members, conflicts = []) {
    return {
        personId: "PERSON-1",
        revision: 3,
        representativeDocId: members[0] ? members[0].docId : "",
        requestedDocId: members[0] ? members[0].docId : "",
        members,
        conflicts
    };
}

function createSandbox(overrides = {}) {
    const sandbox = {
        escapeHtml,
        URLSearchParams,
        operatorStatusLabels: { NOT_CONTACTED: "未联系", REPLIED: "已回复" },
        labelStatus: (v) => (v == null ? "" : String(v)),
        api: async () => personFixture([member("doc1", "a@x.com")]),
        isCurrentContactDetailPresentation: () => true
    };
    Object.assign(sandbox, overrides);
    vm.createContext(sandbox);
    vm.runInContext([
        "discoveryAdmissionLabel",
        "renderDiscoveryPersonEmailsCard",
        "renderDiscoveryPersonEmailsUnavailable",
        "loadDiscoveryPersonEmails"
    ].map(extractFn).join("\n\n"), sandbox);
    return sandbox;
}

// ──────────────────────────────────────────────────────────────────────────
describe("07-ui S-2：详情联系方式卡片（成员行 / 历史入口 / 冲突行）", () => {
    it("三个邮箱按成员重复；复用既有 class；每行 data-member-doc-id；有 contactId 才渲染历史按钮", () => {
        const sb = createSandbox();
        const person = personFixture([
            member("doc1", "primary@x.com", { representative: true, contactId: 11, admissionStatus: "ADMITTED", operatorStatus: "NOT_CONTACTED", evidenceSource: "ORCID" }),
            member("doc2", "secondary@x.com", { contactId: 12, admissionStatus: null, contactStatus: "WAITING_REPLY" }),
            member("doc3", "third@x.com", { admissionStatus: "MANUAL_REJECTED" })
        ]);
        const html = sb.renderDiscoveryPersonEmailsCard(person);

        assert.ok(html.includes('data-role="discovery-person-emails"'), "card carries the fragment role");
        assert.ok(html.includes('class="metadata-card span-all"'), "reuses metadata-card span-all");
        assert.ok(html.includes('class="metadata-card-header"'), "reuses metadata-card-header");
        assert.ok(html.includes('class="metadata-card-value"'), "reuses metadata-card-value");
        assert.strictEqual((html.match(/data-member-doc-id="/g) || []).length, 3, "one row per member");
        assert.ok(html.includes('data-member-doc-id="doc1"'));
        assert.ok(html.includes('data-member-doc-id="doc3"'));
        assert.strictEqual((html.match(/data-action="open-person-member-history"/g) || []).length, 2,
            "only members with a contactId render the history entry");
        assert.ok(html.includes('class="link-btn"'), "history entry reuses link-btn");
        assert.ok(html.includes('data-contact-id="11"') && html.includes('data-contact-id="12"'));
        assert.ok(html.includes('data-member-email="primary@x.com"'), "entry carries the original email");
        assert.ok(!html.includes('data-contact-id="null"'));
        assert.ok(html.includes("可发送"), "ADMITTED admission shows 可发送");
        assert.ok(html.includes("准入：准入未取得"), "missing admission is not shown as sendable");
        assert.ok(html.includes("准入：不合格"), "rejected admission label");
        assert.ok(html.includes("主邮箱"), "representative email is labelled");
    });

    it("确有成员资料冲突时追加差异行", () => {
        const sb = createSandbox();
        const html = sb.renderDiscoveryPersonEmailsCard(personFixture(
            [member("doc1", "a@x.com", { contactId: 1 })],
            ["OPERATOR_STATUS_DIFFERS", "EXPERT_NAME_DIFFERS"]
        ));
        assert.ok(html.includes("成员资料存在差异，请查看对应邮箱档案"), "conflict hint row is rendered");
    });

    it("无成员时不渲染卡片", () => {
        const sb = createSandbox();
        assert.strictEqual(sb.renderDiscoveryPersonEmailsCard(personFixture([])), "");
        assert.strictEqual(sb.renderDiscoveryPersonEmailsCard(null), "");
        assert.strictEqual(sb.renderDiscoveryPersonEmailsCard({ members: [] }), "");
    });

    it("长邮箱与来源文本逐字转义为文本（无未转义尖括号）", () => {
        const sb = createSandbox();
        const html = sb.renderDiscoveryPersonEmailsCard(personFixture([
            member("doc<1>", "very.long.email.address.for<test>@extremely-long-domain.example.org",
                { contactId: 1, evidenceSource: "<img src=x onerror=alert(1)>" })
        ]));
        assert.ok(html.includes("&lt;img src=x onerror=alert(1)&gt;"), "source is escaped");
        assert.ok(!html.includes("<img"), "no raw markup from source");
        assert.ok(html.includes("very.long.email.address.for&lt;test&gt;@extremely-long-domain.example.org"),
            "email is escaped as text");
        assert.ok(html.includes('data-member-doc-id="doc&lt;1&gt;"'), "docId is escaped");
    });

    it("卡片只使用 styles.css 中已声明的 class（无新 CSS）", () => {
        const sb = createSandbox();
        const html = sb.renderDiscoveryPersonEmailsCard(personFixture([
            member("doc1", "a@x.com", { representative: true, contactId: 1, admissionStatus: "ADMITTED" })
        ]));
        const classes = new Set();
        for (const m of html.matchAll(/class="([^"]+)"/g)) {
            m[1].split(/\s+/).filter(Boolean).forEach((c) => classes.add(c));
        }
        for (const cls of classes) {
            assert.ok(stylesCssSource.includes("." + cls), `class .${cls} must already be declared in styles.css`);
        }
        assert.ok(!/style="/.test(html), "no inline style attribute");
    });

    it("无法读取人物详情时占位显示 状态未取得", () => {
        const sb = createSandbox();
        const html = sb.renderDiscoveryPersonEmailsUnavailable();
        assert.ok(html.includes('data-role="discovery-person-emails"'));
        assert.ok(html.includes("状态未取得"));
        assert.ok(html.includes('class="metadata-card-value"'));
    });
});

// ──────────────────────────────────────────────────────────────────────────
describe("07-ui R-12：逐邮箱准入/邮箱状态只认真值 token（旧 filterResult 绝不显示可发送）", () => {
    it("只有 ADMITTED 显示可发送；抑制/不合格/待复核/身份变更/未知各有准确标签", () => {
        const sb = createSandbox();
        assert.strictEqual(sb.discoveryAdmissionLabel("ADMITTED"), "可发送");
        assert.strictEqual(sb.discoveryAdmissionLabel("MANUAL_REJECTED"), "不合格");
        assert.strictEqual(sb.discoveryAdmissionLabel("NEEDS_REVIEW"), "待复核");
        assert.strictEqual(sb.discoveryAdmissionLabel("IDENTITY_CHANGED"), "身份已变更");
        assert.strictEqual(sb.discoveryAdmissionLabel("UNINITIALIZED"), "准入未取得");
        assert.strictEqual(sb.discoveryAdmissionLabel("SUPPRESSED"), "已退订");
        assert.strictEqual(sb.discoveryAdmissionLabel("UNKNOWN"), "准入未取得");
        assert.strictEqual(sb.discoveryAdmissionLabel(null), "准入未取得");
    });

    it("旧 filterResult 值（PASSED/REJECTED）与任意未知 token 都不显示为可发送", () => {
        const sb = createSandbox();
        for (const legacy of ["PASSED", "REJECTED", "HOLD", "TRUE", "", "   "]) {
            assert.notStrictEqual(sb.discoveryAdmissionLabel(legacy), "可发送", `legacy token ${legacy} must not be sendable`);
        }
    });

    it("filterResult=PASSED 的档案在 HOLD/抑制/未知状态下，卡片都不显示可发送", () => {
        const sb = createSandbox();
        const html = sb.renderDiscoveryPersonEmailsCard(personFixture([
            member("doc1", "primary@x.com", { representative: true, contactId: 1, admissionStatus: "MANUAL_REJECTED" }),
            member("doc2", "suppressed@x.com", { contactId: 2, admissionStatus: "SUPPRESSED" }),
            member("doc3", "unknown@x.com", { contactId: 3, admissionStatus: "UNKNOWN" }),
            member("doc4", "legacy@x.com", { contactId: 4, admissionStatus: "PASSED" })
        ]));
        assert.ok(!html.includes("可发送"), "no member may render as sendable");
        assert.ok(html.includes("准入：不合格"));
        assert.ok(html.includes("准入：已退订"));
        assert.ok(html.includes("准入：准入未取得"));
        assert.ok(html.includes("准入：准入状态未知"), "unknown token is not labelled sendable");
    });
});

// ──────────────────────────────────────────────────────────────────────────
describe("07-ui I-2：两宿主共用片段 + generation 守卫", () => {
    for (const host of ["showExpertDetail", "loadContactDetail"]) {
        it(`${host}：把人物卡片写入同一 host 选择器，多邮箱时头部摘要取响应数量`, async () => {
            const sb = createSandbox();
            const root = makeRoot({ withHeader: true });
            const person = personFixture([
                member("doc1", "primary@x.com", { representative: true, contactId: 1, admissionStatus: "ADMITTED" }),
                member("doc2", "b@x.com", { contactId: 2 }),
                member("doc3", "c@x.com", { contactId: 3 })
            ]);
            sb.api = async (url) => {
                assert.ok(String(url).includes("/api/experts/person"), "uses the person detail endpoint");
                assert.ok(String(url).includes("docId=doc1"));
                return person;
            };
            const result = await sb.loadDiscoveryPersonEmails(root, "doc1", "CANDIDATE", 1);
            assert.strictEqual(result, person);
            assert.ok(root.host.innerHTML.includes('data-member-doc-id="doc1"'), "card lands in the host container");
            assert.strictEqual(root.headerSpan.textContent, "primary@x.com · 共 3 个邮箱",
                "header summary counts the response members");
        });
    }

    it("非 discovered（无 personId）不渲染卡片，保持原 UI", async () => {
        const sb = createSandbox();
        const root = makeRoot({ withHeader: true });
        sb.api = async () => ({ personId: null, members: [member("doc1", "a@x.com")], conflicts: [] });
        await sb.loadDiscoveryPersonEmails(root, "doc1", "CANDIDATE", 1);
        assert.strictEqual(root.host.innerHTML, "", "no card for a non-person detail");
        assert.strictEqual(root.headerSpan.textContent, "", "header untouched");
    });

    it("docId 缺失时不请求、不写宿主", async () => {
        const sb = createSandbox();
        const root = makeRoot();
        let calls = 0;
        sb.api = async () => { calls += 1; return personFixture([]); };
        await sb.loadDiscoveryPersonEmails(root, "", "CANDIDATE", 1);
        assert.strictEqual(calls, 0);
        assert.strictEqual(root.host.innerHTML, "");
    });

    it("404（不存在的档案）保持原 UI；其他读取失败显示 状态未取得", async () => {
        const sb = createSandbox();
        const root404 = makeRoot();
        sb.api = async () => { const e = new Error("Person not found"); e.status = 404; throw e; };
        await sb.loadDiscoveryPersonEmails(root404, "doc1", "CANDIDATE", 1);
        assert.strictEqual(root404.host.innerHTML, "", "404 keeps the original UI");

        const rootErr = makeRoot();
        sb.api = async () => { throw new Error("boom"); };
        await sb.loadDiscoveryPersonEmails(rootErr, "doc1", "CANDIDATE", 1);
        assert.ok(rootErr.host.innerHTML.includes("状态未取得"), "load failure shows the unavailable status");
    });

    it("快速切页：旧 generation 的响应不覆盖新页面", async () => {
        const root = makeRoot({ withHeader: true });
        const deferred = [];
        let current = 1;
        const sb = createSandbox({
            api: () => new Promise((resolve, reject) => deferred.push({ resolve, reject })),
            isCurrentContactDetailPresentation: (generation) => generation === current
        });
        const first = sb.loadDiscoveryPersonEmails(root, "docA", "CANDIDATE", 1);
        const second = sb.loadDiscoveryPersonEmails(root, "docB", "CANDIDATE", 2);
        // 新页面（generation=2）先返回
        current = 2;
        deferred[1].resolve(personFixture([
            member("docB", "b@x.com", { representative: true, contactId: 2 }),
            member("docB2", "b2@x.com", { contactId: 22 })
        ]));
        await second;
        const afterSecond = root.host.innerHTML;
        assert.ok(afterSecond.includes('data-member-doc-id="docB"'), "new page rendered first");

        // 旧页面（generation=1）随后返回，必须被丢弃
        deferred[0].resolve(personFixture(
            [member("docA", "a@x.com", { representative: true, contactId: 1 })],
        ));
        await first;
        assert.strictEqual(root.host.innerHTML, afterSecond, "stale response must not overwrite the new page");
        assert.strictEqual(root.headerSpan.textContent, "b@x.com · 共 2 个邮箱", "header keeps the new page summary");
    });

    it("历史按钮点击沿用它原有的 contactId 与邮箱（open-person-member-history）", async () => {
        const calls = [];
        const sb = { openExpertMailbox: (id, email) => calls.push([id, email]) };
        vm.createContext(sb);
        vm.runInContext(extractFn("handleContactAction"), sb);
        await sb.handleContactAction({ dataset: { action: "open-person-member-history", contactId: "7", memberEmail: "m@x.com" } });
        assert.deepStrictEqual(calls, [["7", "m@x.com"]], "routes to mailbox using the original contactId + email");
    });

    it("无 memberEmail 时历史入口仍以原 contactId 导航", async () => {
        const calls = [];
        const sb = { openExpertMailbox: (id, email) => calls.push([id, email]) };
        vm.createContext(sb);
        vm.runInContext(extractFn("handleContactAction"), sb);
        await sb.handleContactAction({ dataset: { action: "open-person-member-history", contactId: "9" } });
        assert.deepStrictEqual(calls, [["9", ""]]);
    });
});

// ──────────────────────────────────────────────────────────────────────────
describe("07-ui I-1/S-1：列表邮箱摘要取响应数量", () => {
    function createListSandbox() {
        const store = new Map();
        function el(id) {
            if (!store.has(id)) {
                store.set(id, { id, innerHTML: "", hidden: false, textContent: "", disabled: false });
            }
            return store.get(id);
        }
        const sandbox = {
            $: (selector) => el(selector.replace(/^#/, "")),
            state: { contacts: [], selectedExpertOrcid: null },
            escapeHtml,
            indexLevelLabels: { CANDIDATE: "筛选" },
            operatorStatusLabels: {},
            expertTagLabels: {},
            contactBadgeType: () => "",
            labelStatus: (v) => v || "",
            badge: (v, t) => `<span class="badge ${t || ""}">${v}</span>`,
            staggerListItems: () => {}
        };
        vm.createContext(sandbox);
        vm.runInContext(extractFn("renderContactListItems"), sandbox);
        sandbox.__store = store;
        return sandbox;
    }

    it("多邮箱显示「主邮箱 · 共 N 个邮箱」，单一/未知保持原文本", () => {
        const sb = createListSandbox();
        sb.state.contacts = [
            { orcidId: "o1", email: "a@x.com", displayName: "A", indexLevel: "CANDIDATE", contactId: 1, discoveryEmailCount: 3 },
            { orcidId: "o2", email: "b@x.com", displayName: "B", indexLevel: "CANDIDATE", contactId: 2, discoveryEmailCount: 1 },
            { orcidId: "o3", email: "", displayName: "C", indexLevel: "CANDIDATE", contactId: null }
        ];
        sb.renderContactListItems();
        const html = sb.$("#contactList").innerHTML;
        assert.ok(html.includes("a@x.com · 共 3 个邮箱"), "multi-email row shows the count from the response");
        assert.ok(!html.includes("b@x.com · 共"), "single-email row keeps the original text");
        assert.ok(html.includes("邮箱未知"), "missing email keeps the original fallback");
    });

    it("ES 分支无成员数量时逐字保持原邮箱文本", () => {
        const sb = createListSandbox();
        sb.state.contacts = [
            { orcidId: "o1", email: "a@x.com", displayName: "A", indexLevel: "CANDIDATE", contactId: 1 }
        ];
        sb.renderContactListItems();
        const html = sb.$("#contactList").innerHTML;
        assert.ok(html.includes(">a@x.com<"), "row keeps the representative email verbatim");
        assert.ok(!html.includes("共"), "no synthesized count");
    });
});

// ──────────────────────────────────────────────────────────────────────────
describe("07-ui epoch2：ES 列表分支消费 06 的 memberCount（I-1/S-1）", () => {
    // 照 loadContactsFilter.test.js 的 DOM stub 范式：把 renderContactListItems + loadContacts
    // 抽入 vm 沙箱，令 useDbContactPath=false 走 ES 列表分支。
    function createEsListSandbox(apiResponse) {
        const store = new Map();
        function el(id) {
            if (!store.has(id)) {
                store.set(id, {
                    id, value: "", innerHTML: "", hidden: false, disabled: false,
                    textContent: "", selectedOptions: null,
                    parentElement: { style: {}, title: "" }
                });
            }
            return store.get(id);
        }
        const sandbox = {
            $: (sel) => el(sel.replace(/^#/, "")),
            URLSearchParams,
            escapeHtml,
            state: { contactsPage: 0, contacts: [], contactsTotalHits: 0, lastEmailProvidersLevel: null, selectedExpertOrcid: null },
            indexLevelLabels: { CANDIDATE: "筛选" },
            operatorStatusLabels: {},
            expertTagLabels: {},
            contactBadgeType: () => "",
            labelStatus: (v) => (v == null ? "" : String(v)),
            badge: (v, t) => `<span class="badge ${t || ""}">${v}</span>`,
            staggerListItems: () => {},
            renderContactListSkeleton: () => {},
            renderContactPager: () => {},
            loadEmailProviders: () => {},
            loadRegions: () => {},
            loadExpertTagOptions: () => {},
            loadOperatorStatusSyncTooltip: () => {},
            api: async () => apiResponse
        };
        vm.createContext(sandbox);
        vm.runInContext(extractFn("renderContactListItems"), sandbox);
        vm.runInContext(extractFn("loadContacts"), sandbox);
        sandbox.__store = store;
        return sandbox;
    }

    it("ES 响应 memberCount>1 时列表渲染「主邮箱 · 共 N 个邮箱」", async () => {
        const sb = createEsListSandbox({
            experts: [{ orcidId: "o1", email: "a@x.com", displayName: "A", indexLevel: "CANDIDATE", contactId: 1, memberCount: 3 }],
            totalHits: 1
        });
        sb.$("#expertIndexLevel").value = "CANDIDATE";
        sb.$("#expertIndexSize").value = "50";
        await sb.loadContacts();
        assert.strictEqual(sb.state.contacts[0].discoveryEmailCount, 3, "consumes memberCount from the ES response");
        assert.ok(sb.$("#contactList").innerHTML.includes("a@x.com · 共 3 个邮箱"), "multi-email ES row shows the count");
    });

    it("ES 响应 memberCount==1 或缺失时逐字保持原邮箱文本", async () => {
        const sb = createEsListSandbox({
            experts: [
                { orcidId: "o1", email: "a@x.com", displayName: "A", indexLevel: "CANDIDATE", contactId: 1, memberCount: 1 },
                { orcidId: "o2", email: "b@x.com", displayName: "B", indexLevel: "CANDIDATE", contactId: 2 }
            ],
            totalHits: 2
        });
        sb.$("#expertIndexLevel").value = "CANDIDATE";
        sb.$("#expertIndexSize").value = "50";
        await sb.loadContacts();
        const html = sb.$("#contactList").innerHTML;
        assert.ok(html.includes(">a@x.com<"), "memberCount==1 keeps the original text");
        assert.ok(html.includes(">b@x.com<"), "missing memberCount keeps the original text");
        assert.ok(!html.includes("共"), "no synthesized count for single/missing memberCount");
    });
});

// ──────────────────────────────────────────────────────────────────────────
describe("07-ui：两详情宿主接入同一片段（源契约）", () => {
    it("两个宿主模板都输出同一 host 容器与同一头部摘要标记", () => {
        for (const name of ["showExpertDetail", "loadContactDetail"]) {
            const fn = extractFn(name);
            assert.ok(fn.includes('data-role="discovery-person-emails-host"'), `${name} declares the shared host container`);
            assert.ok(fn.includes('data-role="detail-email-summary"'), `${name} marks the header email span`);
            assert.ok(fn.includes("loadDiscoveryPersonEmails(contactDetail"), `${name} loads the shared fragment`);
            assert.ok(fn.includes('typeof loadDiscoveryPersonEmails === "function"'), `${name} guards the shared call for sliced tests`);
        }
        assert.ok(appJsSource.includes("function loadDiscoveryPersonEmails(root, docId, level, generation)"));
        assert.ok(appJsSource.includes("function renderDiscoveryPersonEmailsCard(person)"));
    });

    it("历史入口经既有 click 委托路由（不新增全局监听器）", () => {
        const fn = extractFn("handleContactAction");
        assert.ok(fn.includes('action === "open-person-member-history"'), "delegated handler routes the history entry");
        assert.ok(fn.includes("openExpertMailbox(element.dataset.contactId"), "uses the original contactId navigation");
    });
});
