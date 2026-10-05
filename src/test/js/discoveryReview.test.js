// c5（I-1～I-4 / S-1～S-3）：深度发现审核页签的 HTML/CSS 契约、请求范围、生命周期与在途隔离。
//
// 本文件加载**生产源码段**（app.js 的 c5 连续块 + 被单独抽取的接缝函数），只对 DOM/transport/计时器
// 做隔离桩。真实 index.html 的 id 另有源文本断言，避免 DOM stub 掩盖悬空引用
// （K-dom-stub-tests-hide-dangling-refs）。

const fs = require("fs");
const path = require("path");
const vm = require("vm");
const assert = require("assert");
const { describe, it } = require("node:test");

const staticDir = path.join(__dirname, "..", "..", "main", "resources", "static");
const appJsPath = path.join(staticDir, "app.js");
const indexHtml = fs.readFileSync(path.join(staticDir, "index.html"), "utf-8");
const stylesCss = fs.readFileSync(path.join(staticDir, "styles.css"), "utf-8");
const appJsSource = fs.readFileSync(appJsPath, "utf-8");

function extractFn(name) {
    const regex = new RegExp("(?:async\\s+)?function\\s+" + name + "\\s*\\([^)]*\\)\\s*\\{[\\s\\S]*?\\n\\}");
    const match = appJsSource.match(regex);
    if (!match) throw new Error("Could not find " + name + " in app.js");
    return match[0];
}

function sectionSource() {
    const start = appJsSource.indexOf("// ── c5（I-1");
    const end = appJsSource.indexOf("async function handleCancelTask()");
    assert.ok(start > 0 && end > start, "the c5 discovery review section must stay in app.js");
    return appJsSource.slice(start, end);
}

const flush = async (times = 8) => {
    for (let i = 0; i < times; i += 1) await new Promise((resolve) => setImmediate(resolve));
};

// ── 最小 DOM 桩 ───────────────────────────────────────────────────────────────

function makeClassList() {
    const set = new Set();
    return {
        contains: (cls) => set.has(cls),
        add: (cls) => set.add(cls),
        remove: (cls) => set.delete(cls),
        toggle: (cls, force) => {
            const on = force === undefined ? !set.has(cls) : Boolean(force);
            if (on) set.add(cls); else set.delete(cls);
            return on;
        }
    };
}

function walk(node, out) {
    (node.children || []).forEach((child) => { out.push(child); walk(child, out); });
    return out;
}

function makeEl(tagName) {
    const el = {
        tagName: String(tagName || "div").toUpperCase(),
        children: [],
        parentElement: null,
        className: "",
        textContent: "",
        hidden: false,
        disabled: false,
        checked: false,
        value: "",
        open: false,
        type: "",
        focused: false,
        dataset: {},
        attributes: {},
        classList: makeClassList(),
        listeners: {},
        appendChild(child) {
            el.children.push(child);
            if (child) child.parentElement = el;
            return child;
        },
        setAttribute(key, value) {
            el.attributes[key] = String(value);
            if (key.indexOf("data-") === 0) {
                el.dataset[key.slice(5).replace(/-([a-z])/g, (m, c) => c.toUpperCase())] = String(value);
            }
        },
        getAttribute(key) {
            return Object.prototype.hasOwnProperty.call(el.attributes, key) ? el.attributes[key] : null;
        },
        removeAttribute(key) { delete el.attributes[key]; },
        addEventListener(type, fn) { (el.listeners[type] = el.listeners[type] || []).push(fn); },
        dispatch(type, event) {
            (el.listeners[type] || []).slice().forEach((fn) => fn(Object.assign({ target: el }, event || {})));
        },
        click() { el.dispatch("click", {}); },
        focus() { el.focused = true; },
        querySelectorAll(selector) {
            const all = [];
            walk(el, all);
            const tag = String(selector || "").toUpperCase();
            return all.filter((node) => node.tagName === tag);
        }
    };
    // innerHTML="" 必须像真实 DOM 一样清空子节点（渲染接缝依赖它重建表格/记录）。
    Object.defineProperty(el, "innerHTML", {
        enumerable: true,
        get() { return el.__innerHTML || ""; },
        set(value) {
            el.__innerHTML = String(value);
            if (String(value) === "") el.children = [];
        }
    });
    return el;
}

function makeSandbox(overrides = {}) {
    const elements = new Map();
    const el = (id) => {
        if (!elements.has(id)) elements.set(id, makeEl("div"));
        return elements.get(id);
    };
    // S-2 的四格计数：每格一个 <strong>
    const counts = el("discoveryReviewCounts");
    for (let i = 0; i < 4; i += 1) {
        const cell = makeEl("div");
        cell.appendChild(makeEl("strong"));
        counts.appendChild(cell);
    }
    const pane = el("discoveryReviewPane");
    const actionButtons = ["APPROVE", "HOLD", "REJECT"].map((action) => {
        const btn = makeEl("button");
        btn.dataset.reviewAction = action;
        pane.appendChild(btn);
        return btn;
    });
    const timers = [];
    const apiCalls = [];
    const document = {
        createElement: (tag) => makeEl(tag),
        getElementById: (id) => el(id),
        querySelector: (selector) => el(String(selector).replace(/^#/, "")),
        querySelectorAll: (selector) => {
            if (selector === "#discoveryReviewPane [data-review-action]") return actionButtons;
            return [];
        },
        body: { classList: makeClassList() }
    };
    const sandbox = {
        console,
        DISCOVERY_TASK_TYPE: "EXPERT_DISCOVERY",
        URLSearchParams,
        document,
        $: (selector) => el(String(selector).replace(/^#/, "")),
        $$: (selector) => document.querySelectorAll(selector),
        currentTaskModal: null,
        isCurrentTaskModal: (taskType, generation) => sandbox.currentTaskModal != null
            && sandbox.currentTaskModal.taskType === taskType
            && sandbox.currentTaskModal.generation === generation,
        api: (requestPath, options) => {
            apiCalls.push({ path: requestPath, options: options || {} });
            if (typeof sandbox.apiHandler === "function") {
                return Promise.resolve().then(() => sandbox.apiHandler(requestPath, options || {}));
            }
            return Promise.resolve({});
        },
        setTimeout: (fn, ms) => {
            const handle = { fn, ms, cancelled: false };
            timers.push(handle);
            return handle;
        },
        clearTimeout: (handle) => { if (handle) handle.cancelled = true; },
        setInterval: () => 0,
        clearInterval: () => {}
    };
    Object.assign(sandbox, overrides);
    sandbox.__elements = elements;
    sandbox.__actionButtons = actionButtons;
    sandbox.__counts = counts;
    sandbox.__timers = timers;
    sandbox.__apiCalls = apiCalls;
    return sandbox;
}

function loadSandbox(overrides = {}, fnNames = []) {
    const sandbox = makeSandbox(overrides);
    vm.createContext(sandbox);
    const epilogue = "\n;globalThis.__dr = { state: discoveryReviewState, els: discoveryReviewElements };";
    const source = [sectionSource(), ...fnNames.map(extractFn)].join("\n");
    vm.runInContext(source + epilogue, sandbox);
    return sandbox;
}

async function openReviewTab(sandbox) {
    sandbox.currentTaskModal = { taskType: "EXPERT_DISCOVERY", generation: 3 };
    sandbox.initDiscoveryReview("EXPERT_DISCOVERY", 3);
    sandbox.$("#discoveryReviewTab").dispatch("click", {});
    await flush();
}

function expertRow(index, overrides = {}) {
    return Object.assign({
        docId: "doc-" + index,
        level: "RAW",
        orcidId: "0000-0000-0000-" + index,
        email: "expert" + index + "@example.org",
        givenNames: "Given" + index,
        familyNames: "Family" + index,
        institution: "Institute " + index,
        country: "US",
        researchFields: "AI",
        disciplineCategory: "CS",
        institutionEvidence: "evidence",
        filterResult: "PASS",
        tags: ["discovered"],
        automaticStatus: "NEEDS_REVIEW",
        automaticReasons: [{
            code: "INSTITUTION_MISSING", label: "缺少机构", field: "institution",
            observed: null, expected: null, sourceLocation: "profile.institution"
        }],
        automaticHints: [],
        revision: 1,
        initialized: true,
        decision: "NEEDS_REVIEW",
        decisionManual: false,
        identityChanged: false,
        reviewedActor: null,
        reviewedAt: null,
        addressWarning: null
    }, overrides);
}

function countStrongs(sandbox) {
    return sandbox.__counts.querySelectorAll("strong");
}

function timerCount(sandbox, cancelled) {
    return sandbox.__timers.filter((t) => t.cancelled === cancelled).length;
}

async function runNextTimer(sandbox) {
    const pending = sandbox.__timers.filter((t) => t.cancelled !== true);
    assert.ok(pending.length > 0, "a pending timer must exist");
    const timer = pending[pending.length - 1];
    timer.cancelled = true;
    await timer.fn();
    await flush();
}

// ── S-1/S-2/S-3 静态契约 ─────────────────────────────────────────────────────

describe("c5 discovery review · S-1/S-2/S-3 contract", () => {
    it("index.html declares the tabs, the pipeline wrapper, and every review id", () => {
        for (const id of [
            "discoveryReviewTabs", "discoveryPipelineTab", "discoveryReviewTab", "discoveryReviewHistoryTab",
            "discoveryPipelinePane", "discoveryReviewPane", "discoveryReviewHistoryPane",
            "discoveryReviewCounts", "discoveryReviewSearch", "discoveryReviewIssue", "discoveryReviewDecision",
            "discoveryReviewPageSize", "discoveryReviewApprovePage", "discoveryReviewApproveAll",
            "discoveryReviewSelection", "discoveryReviewError", "discoveryReviewResult",
            "discoveryReviewSelectPage", "discoveryReviewRows", "discoveryReviewPageInfo",
            "discoveryReviewPrev", "discoveryReviewNext", "discoveryReviewConfirm",
            "discoveryReviewConfirmScope", "discoveryReviewConfirmReasons", "discoveryReviewConfirmPeople",
            "discoveryReviewNote", "discoveryReviewCancelConfirm", "discoveryReviewCommit"
        ]) {
            assert.ok(indexHtml.includes('id="' + id + '"'), "index.html must declare #" + id);
        }
        const navAt = indexHtml.indexOf('id="discoveryReviewTabs"');
        const paneAt = indexHtml.indexOf('id="discoveryPipelinePane"');
        const reviewAt = indexHtml.indexOf('id="discoveryReviewPane"');
        const historyAt = indexHtml.indexOf('id="discoveryReviewHistoryPane"');
        const configAt = indexHtml.indexOf('id="taskModalConfigSection"');
        assert.ok(navAt > 0 && navAt < paneAt, "the tab nav must precede the pipeline wrapper");
        assert.ok(paneAt < configAt, "the pipeline wrapper must contain the original pipeline content");
        assert.ok(paneAt < reviewAt && reviewAt < historyAt, "review panes must follow the pipeline wrapper");
    });

    it("the nav declares the three tabs with roving selection attributes", () => {
        const navStart = indexHtml.indexOf('id="discoveryReviewTabs"');
        const nav = indexHtml.slice(navStart - 200, indexHtml.indexOf("</nav>", navStart));
        assert.ok(nav.includes('role="tablist"'), "the nav must be a tablist");
        for (const label of ["采集运行", "专家审核", "审核记录"]) {
            assert.ok(nav.includes(">" + label + "</button>"), "missing tab: " + label);
        }
        assert.strictEqual((nav.match(/aria-selected="true"/g) || []).length, 1, "exactly one tab starts selected");
        assert.strictEqual((nav.match(/tabindex="-1"/g) || []).length, 2, "inactive tabs start with tabindex=-1");
        assert.ok(nav.includes('aria-controls="discoveryPipelinePane"'));
        assert.ok(nav.includes('aria-controls="discoveryReviewPane"'));
        assert.ok(nav.includes('aria-controls="discoveryReviewHistoryPane"'));
    });

    it("the new review DOM has no inline styles and no undeclared classes", () => {
        const reviewAt = indexHtml.indexOf('id="discoveryReviewPane"');
        const historyEnd = indexHtml.indexOf("</section>", indexHtml.indexOf('id="discoveryReviewHistoryPane"'));
        const block = indexHtml.slice(reviewAt, historyEnd);
        assert.ok(!block.includes("style="), "no inline styles on the review DOM");
        const allowed = new Set([
            "modal-body", "dr-review", "dr-counts", "dr-toolbar", "dr-confirm", "dr-table-wrap",
            "data-table", "dr-table", "dr-pager", "button", "secondary", "primary", "bsc-input"
        ]);
        const classes = block.match(/class="([^"]*)"/g) || [];
        classes.forEach((attr) => {
            attr.replace(/class="|"/g, "").split(/\s+/).filter(Boolean).forEach((cls) => {
                assert.ok(allowed.has(cls), "undeclared class in the review DOM: " + cls);
            });
        });
    });

    it("keeps the S-1/S-2/S-3 CSS blocks verbatim", () => {
        const s1 = `#taskProgressModal[data-discovery-review="true"] .modal-content.task-modal {
    max-width: 1180px;
    width: calc(100vw - 48px);
}
.dr-review {
    color: var(--text-main);
    font-size: 13px;
    line-height: 1.6;
}
.dr-review [role="alert"] {
    padding: 12px;
    color: var(--error-strong);
    background: var(--error-bg);
    border: 1px solid var(--error-border);
    border-radius: var(--radius-sm);
}
.dr-review button:disabled {
    opacity: 0.45;
    cursor: not-allowed;
    transform: none;
    box-shadow: none;
}
.dr-review :focus-visible,
#discoveryReviewTabs :focus-visible {
    outline: 2px solid var(--primary);
    outline-offset: 3px;
}
@media (max-width: 640px) {
    #taskProgressModal[data-discovery-review="true"] .modal-content.task-modal {
        width: calc(100vw - 20px);
    }
}`;
        const s2 = `.dr-counts {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 12px;
}
.dr-counts > div {
    padding: 14px;
    background: var(--bg-subtle);
    border: 1px solid var(--panel-border);
    border-radius: var(--radius-md);
}
.dr-counts strong {
    display: block;
    font-size: 24px;
    color: var(--primary);
}
.dr-toolbar,
.dr-pager {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
}
.dr-toolbar .bsc-input {
    width: 180px;
}
.dr-pager {
    justify-content: space-between;
}
.dr-table-wrap {
    overflow: auto;
    border: 1px solid var(--panel-border);
    border-radius: var(--radius-md);
}
.dr-table {
    min-width: 1000px;
}
.dr-table th,
.dr-table td {
    padding: 12px 10px;
    font-size: 12px;
    line-height: 1.6;
    vertical-align: top;
    overflow-wrap: anywhere;
}
.dr-table th {
    background: #f8fafc;
    color: var(--text-secondary);
}
.dr-table tbody tr:hover {
    background: #f8faff;
}
.dr-state {
    display: inline-block;
    padding: 2px 8px;
    border: 1px solid var(--panel-border);
    border-radius: var(--radius-sm);
    color: var(--text-secondary);
    background: var(--bg-subtle);
}
.dr-state[data-state="AUTO_PASSED"],
.dr-state[data-state="MANUAL_APPROVED"],
.dr-state[data-state="LEGACY_APPROVED"] {
    color: var(--success);
    background: var(--success-bg);
    border-color: var(--success-border);
}
.dr-review input[type="checkbox"] {
    width: 15px;
    height: 15px;
    accent-color: var(--primary);
}
@media (max-width: 640px) {
    .dr-counts {
        grid-template-columns: repeat(2, minmax(0, 1fr));
    }
}`;
        const s3 = `.dr-confirm,
.dr-record {
    padding: 16px;
    border: 1px solid var(--panel-border);
    border-radius: var(--radius-md);
    background: #ffffff;
}
.dr-confirm {
    border-color: rgba(30, 64, 175, 0.25);
    background: #f7f9ff;
}
.dr-confirm p,
.dr-record p {
    margin: 8px 0;
    color: var(--text-secondary);
}
.dr-confirm textarea {
    min-height: 68px;
    margin: 8px 0;
    resize: vertical;
}`;
        assert.ok(stylesCss.includes(s1), "S-1 CSS must be verbatim");
        assert.ok(stylesCss.includes(s2), "S-2 CSS must be verbatim");
        assert.ok(stylesCss.includes(s3), "S-3 CSS must be verbatim");
        assert.ok(stylesCss.trimEnd().endsWith("/* mailbox-suspension-contract:end */"),
            "the pre-existing mailbox contract block must stay at the file end");
    });

    it("keeps one cache key for all eleven assets and other task modals at 700px", () => {
        const keys = Array.from(indexHtml.matchAll(/[?"]v=([^"'&<>]+)/g)).map((m) => m[1]);
        const unique = Array.from(new Set(keys));
        assert.strictEqual(keys.length, 11, "exactly eleven cache-busted assets");
        assert.strictEqual(unique.length, 1, "all assets must share one cache key");
        assert.match(unique[0], /^[0-9]{8}-[a-z0-9-]+$/, "cache key must be <yyyymmdd>-<slug>");
        assert.notStrictEqual(unique[0], "20261004-mailbox-suspension-followup",
            "the previous release key must be bumped");
        // 只有 data-discovery-review 标记触发宽度；其余任务仍是 700px 基规则。
        assert.ok(stylesCss.includes(".modal-content.task-modal {\n    max-width: 700px;\n}"));
        assert.ok(stylesCss.includes('@media (max-width: 640px)'));
    });

    it("the review rows builder only ever writes expert facts through textContent", () => {
        const builder = extractFn("buildDiscoveryReviewRow");
        assert.ok(!/innerHTML\s*=/.test(builder), "row builder must not write innerHTML");
        assert.ok(!/insertAdjacentHTML|outerHTML/.test(builder), "row builder must not inject HTML");
        assert.ok(builder.includes("textContent"), "row builder must use textContent");
    });
});

// ── 生命周期与页签 ───────────────────────────────────────────────────────────

describe("c5 discovery review · lifecycle (I-1 / I-3)", () => {
    it("shows the tabs only for EXPERT_DISCOVERY and keeps the pipeline pane otherwise", () => {
        const sandbox = loadSandbox();
        sandbox.apiHandler = () => ({ total: 0, experts: [] });
        sandbox.currentTaskModal = { taskType: "EXPERT_ENRICHMENT", generation: 1 };
        sandbox.initDiscoveryReview("EXPERT_ENRICHMENT", 1);
        assert.strictEqual(sandbox.$("#discoveryReviewTabs").hidden, true, "other tasks hide the tab nav");
        assert.strictEqual(sandbox.$("#discoveryPipelinePane").hidden, false, "other tasks keep the pipeline");
        assert.strictEqual(sandbox.$("#taskProgressModal").getAttribute("data-discovery-review"), null);

        sandbox.currentTaskModal = { taskType: "EXPERT_DISCOVERY", generation: 2 };
        sandbox.initDiscoveryReview("EXPERT_DISCOVERY", 2);
        assert.strictEqual(sandbox.$("#discoveryReviewTabs").hidden, false, "deep discovery shows the tab nav");
        assert.strictEqual(sandbox.$("#taskProgressModal").getAttribute("data-discovery-review"), "true");
    });

    it("tab click switches panes, aria-selected, tabindex, and loads the list once", async () => {
        const sandbox = loadSandbox();
        sandbox.apiHandler = (requestPath) => {
            if (requestPath.includes("decision=")) return { total: 0, experts: [] };
            return { total: 20, experts: [expertRow(1)] };
        };
        await openReviewTab(sandbox);
        assert.strictEqual(sandbox.$("#discoveryReviewPane").hidden, false);
        assert.strictEqual(sandbox.$("#discoveryPipelinePane").hidden, true);
        assert.strictEqual(sandbox.$("#discoveryReviewTab").getAttribute("aria-selected"), "true");
        assert.strictEqual(sandbox.$("#discoveryReviewTab").getAttribute("tabindex"), "0");
        assert.strictEqual(sandbox.$("#discoveryPipelineTab").getAttribute("tabindex"), "-1");
        const listCalls = sandbox.__apiCalls.filter((c) => c.path.indexOf("/experts?") > -1
            && c.path.indexOf("decision=") < 0);
        assert.strictEqual(listCalls.length, 1, "one list request per tab activation");
        assert.ok(listCalls[0].path.includes("level=RAW") && listCalls[0].path.includes("tag=discovered"));
        assert.ok(!listCalls[0].path.includes("from=20"), "first page starts at from=0");
    });

    it("arrow keys move the roving selection and focus the new tab", async () => {
        const sandbox = loadSandbox();
        sandbox.apiHandler = () => ({ total: 0, experts: [] });
        await openReviewTab(sandbox);
        sandbox.$("#discoveryPipelineTab").click();
        let prevented = 0;
        sandbox.$("#discoveryPipelineTab").dispatch("keydown", {
            key: "ArrowRight",
            preventDefault: () => { prevented += 1; }
        });
        await flush(4);
        assert.strictEqual(prevented, 1, "an arrow key must prevent default scrolling");
        assert.strictEqual(sandbox.$("#discoveryReviewTab").getAttribute("aria-selected"), "true");
        assert.strictEqual(sandbox.$("#discoveryPipelineTab").getAttribute("aria-selected"), "false");
        assert.strictEqual(sandbox.$("#discoveryReviewTab").focused, true, "the new tab receives focus");
        assert.strictEqual(sandbox.$("#discoveryReviewPane").hidden, false);
        assert.strictEqual(sandbox.$("#discoveryPipelinePane").hidden, true);
    });

    it("a second list request that resolves first is not overwritten by the stale one (I-3)", async () => {
        const pending = [];
        const sandbox = loadSandbox({
            apiHandler: (requestPath) => {
                if (requestPath.includes("decision=")) return { total: 0, experts: [] };
                return new Promise((resolve) => { pending.push({ resolve }); });
            }
        }, ["refreshDiscoveryReviewList"]);
        sandbox.currentTaskModal = { taskType: "EXPERT_DISCOVERY", generation: 5 };
        sandbox.initDiscoveryReview("EXPERT_DISCOVERY", 5);
        sandbox.refreshDiscoveryReviewList();
        sandbox.refreshDiscoveryReviewList();
        await flush(2);
        assert.strictEqual(pending.length, 2, "two in-flight list requests");
        pending[1].resolve({ total: 1, experts: [expertRow(2)] });
        await flush(4);
        pending[0].resolve({ total: 1, experts: [expertRow(1)] });
        await flush(4);
        assert.strictEqual(sandbox.__dr.state.rows.length, 1);
        assert.strictEqual(sandbox.__dr.state.rows[0].docId, "doc-2",
            "the newer response must win; the stale response is dropped");
    });

    it("closing the panel voids in-flight responses without cancelling any background task (I-3)", async () => {
        const pending = [];
        const sandbox = loadSandbox({
            apiHandler: (requestPath) => {
                if (requestPath.includes("decision=")) return { total: 0, experts: [] };
                return new Promise((resolve) => { pending.push({ resolve }); });
            }
        }, ["refreshDiscoveryReviewList"]);
        sandbox.currentTaskModal = { taskType: "EXPERT_DISCOVERY", generation: 6 };
        sandbox.initDiscoveryReview("EXPERT_DISCOVERY", 6);
        sandbox.refreshDiscoveryReviewList();
        await flush(2);
        sandbox.resetDiscoveryReviewPanel();
        const callsBefore = sandbox.__apiCalls.length;
        pending.forEach((entry) => entry.resolve({ total: 3, experts: [expertRow(9)] }));
        await flush(4);
        assert.strictEqual(sandbox.__dr.state.rows.length, 0, "closed panel must not accept stale data");
        assert.strictEqual(sandbox.$("#discoveryReviewTabs").hidden, true);
        assert.strictEqual(sandbox.$("#taskProgressModal").getAttribute("data-discovery-review"), null);
        const cancels = sandbox.__apiCalls.slice(callsBefore).filter((c) => c.path.indexOf("/cancel") > -1);
        assert.strictEqual(cancels.length, 0, "closing must never cancel a background review task");
    });
});

// ── 数量与列表 ───────────────────────────────────────────────────────────────

describe("c5 discovery review · counts and list (I-1)", () => {
    it("renders the four server counts and never counts auto-passed as pending", async () => {
        const sandbox = loadSandbox();
        sandbox.apiHandler = (requestPath) => {
            const query = new URLSearchParams(requestPath.split("?")[1] || "");
            const decision = query.get("decision");
            if (decision) {
                const totals = {
                    AUTO_PASSED: 7, NEEDS_REVIEW: 10005, MANUAL_APPROVED: 2,
                    LEGACY_APPROVED: 1, HOLD: 3, REJECTED: 4
                };
                const total = totals[decision] || 0;
                const from = Number(query.get("from") || 0);
                const size = Number(query.get("size") || 100);
                return { total, experts: Array.from({ length: Math.min(size, Math.max(0, total - from)) },
                    (_, i) => expertRow(from + i, { decision })) };
            }
            return { total: 20, experts: [expertRow(1)] };
        };
        await openReviewTab(sandbox);
        const strongs = countStrongs(sandbox);
        assert.strictEqual(strongs[0].textContent, "7", "自动通过 count");
        assert.strictEqual(strongs[1].textContent, "10005", "待人工审核 count");
        assert.strictEqual(strongs[2].textContent, "3", "人工/历史通过 = MANUAL_APPROVED + LEGACY_APPROVED");
        assert.strictEqual(strongs[3].textContent, "7", "暂缓/不通过 = HOLD + REJECTED");
        assert.notStrictEqual(strongs[1].textContent, "10012", "auto-passed must never inflate the pending count");
    });

    it("keeps the failing count cell undefined and surfaces a specific error", async () => {
        const sandbox = loadSandbox();
        sandbox.apiHandler = (requestPath) => {
            const query = new URLSearchParams(requestPath.split("?")[1] || "");
            const decision = query.get("decision");
            if (decision === "HOLD") throw new Error("503 服务不可用");
            if (decision) return { total: 1, experts: [expertRow(1, { decision })] };
            return { total: 0, experts: [] };
        };
        await openReviewTab(sandbox);
        const strongs = countStrongs(sandbox);
        assert.strictEqual(strongs[3].textContent, "—", "failed group must not fake a number");
        assert.strictEqual(sandbox.$("#discoveryReviewError").hidden, false);
        assert.ok(sandbox.$("#discoveryReviewError").textContent.includes("部分准入数量读取失败"));
        assert.ok(sandbox.$("#discoveryReviewError").textContent.includes("暂缓 / 不通过"));
    });

    it("uses admission presence for display and counts without a revision heuristic", async () => {
        const sandbox = loadSandbox();
        const rows = [
            expertRow(1, { initialized: false, revision: 7 }),
            expertRow(2, { initialized: true, revision: 0 }),
            expertRow(3, { initialized: true, revision: 0, identityChanged: true })
        ];
        sandbox.apiHandler = (requestPath) => {
            const query = new URLSearchParams(requestPath.split("?")[1] || "");
            const decision = query.get("decision");
            if (decision === "NEEDS_REVIEW") return { total: 3, experts: rows };
            if (decision === "UNINITIALIZED") return { total: 1, experts: [rows[0]] };
            if (decision) return { total: 0, experts: [] };
            return { total: 3, experts: rows };
        };
        await openReviewTab(sandbox);
        assert.strictEqual(countStrongs(sandbox)[1].textContent, "2");
        const rendered = sandbox.$("#discoveryReviewRows").children;
        assert.strictEqual(rendered[0].children[5].children[0].textContent, "未初始化");
        assert.strictEqual(rendered[1].children[5].children[0].textContent, "待人工审核");
        assert.ok(rendered[2].children[5].children.some(node => node.textContent.includes("身份已变化")));
        sandbox.$("#discoveryReviewDecision").value = "UNINITIALIZED";
        sandbox.discoveryReviewApplyFilterChange();
        await flush();
        assert.ok(sandbox.__apiCalls.some(call => call.path.includes("decision=UNINITIALIZED")));
        assert.strictEqual(sandbox.__dr.state.rows.length, 1);
    });

    it("marks only NEEDS_REVIEW rows as reviewable and keeps field states distinct", async () => {
        const sandbox = loadSandbox();
        sandbox.apiHandler = (requestPath) => {
            const query = new URLSearchParams(requestPath.split("?")[1] || "");
            if (query.get("decision")) return { total: 0, experts: [] };
            return {
                total: 2,
                experts: [
                    expertRow(1, { decision: "AUTO_PASSED" }),
                    expertRow(2, { institution: "", email: null })
                ]
            };
        };
        await openReviewTab(sandbox);
        const rows = sandbox.$("#discoveryReviewRows").children;
        assert.strictEqual(rows.length, 2);
        const autoCheckbox = rows[0].children[0].children[0];
        assert.strictEqual(autoCheckbox.disabled, true, "auto-passed rows are not reviewable");
        const pendingCheckbox = rows[1].children[0].children[0];
        assert.strictEqual(pendingCheckbox.disabled, false, "pending rows are reviewable");
        const institutionCell = rows[1].children[2];
        assert.ok(institutionCell.children[0].textContent.includes("未记录"), "empty value is 未记录");
        const emailCell = rows[1].children[1];
        assert.ok(emailCell.children[1].textContent.includes("未记录"), "null value is 未记录");
    });
});

// ── 范围与确认（I-2 / S-3） ─────────────────────────────────────────────────

describe("c5 discovery review · scope and confirm (I-2)", () => {
    function scopeHandler(sandbox) {
        return (requestPath, options) => {
            if (requestPath.indexOf("/batches/prepare") > -1) {
                const body = JSON.parse(options.body || "{}");
                if (body.scope === "ALL_MATCHING") {
                    return { batchKey: "bk-all", phase: "PREPARING", total: 0, counts: {}, items: [] };
                }
                return {
                    batchKey: "bk-ids",
                    batchHash: "hash-ids",
                    itemCount: (body.docIds || []).length,
                    items: (body.docIds || []).map((docId) => ({ docId, state: "STAGED" }))
                };
            }
            if (requestPath.indexOf("/confirm") > -1) {
                return { batchKey: "bk-ids", total: 20, applied: 20, stale: 0, failed: 0, skipped: 0, items: [] };
            }
            if (requestPath.indexOf("/batches/") > -1) {
                return { batchKey: "bk-all", phase: "READY", batchHash: "hash-all", total: 10005, counts: {}, items: [], nextCursor: null };
            }
            const query = new URLSearchParams(requestPath.split("?")[1] || "");
            if (query.get("decision")) return { total: 0, experts: [] };
            return { total: 20, experts: Array.from({ length: 20 }, (_, i) => expertRow(i + 1)) };
        };
    }

    it("current page prepares exactly the displayed reviewable ids", async () => {
        const sandbox = loadSandbox();
        sandbox.apiHandler = scopeHandler(sandbox);
        await openReviewTab(sandbox);
        sandbox.$("#discoveryReviewApprovePage").click();
        assert.strictEqual(sandbox.$("#discoveryReviewConfirm").hidden, false);
        assert.ok(sandbox.$("#discoveryReviewConfirmScope").textContent.includes("固定总数：20"));
        assert.strictEqual(sandbox.$("#discoveryReviewCommit").disabled, false);
        sandbox.$("#discoveryReviewCommit").click();
        await flush(6);
        const prepare = sandbox.__apiCalls.find((c) => c.path.indexOf("/batches/prepare") > -1);
        assert.ok(prepare, "prepare must be called");
        const body = JSON.parse(prepare.options.body);
        assert.strictEqual(body.scope, "IDS");
        assert.strictEqual(body.docIds.length, 20);
        assert.strictEqual(body.action, "APPROVE");
        assert.ok(!sandbox.__apiCalls.some((c) => c.path.includes("/confirm")),
            "preparation must not apply before the fixed snapshot is confirmed");
        sandbox.$("#discoveryReviewCommit").click();
        await flush(6);
        const confirm = sandbox.__apiCalls.find((c) => c.path.indexOf("/confirm") > -1);
        assert.ok(confirm, "confirm must be called");
        assert.deepStrictEqual(JSON.parse(confirm.options.body), { batchHash: "hash-ids" },
            "confirm must send only batchHash");
        assert.ok(sandbox.$("#discoveryReviewResult").textContent.includes("成功 20"));
    });

    it("all pages waits for READY and shows the server total, then confirms with the stored hash", async () => {
        const sandbox = loadSandbox();
        let batchStatus;
        sandbox.apiHandler = (requestPath, options) => {
            if (requestPath.indexOf("/batches/prepare") > -1) {
                return { batchKey: "bk-all", phase: "PREPARING", total: 0, counts: {}, items: [] };
            }
            if (requestPath.indexOf("/confirm") > -1) {
                return { batchKey: "bk-all", total: 10005, applied: 0, stale: 0, failed: 0, skipped: 0, items: [] };
            }
            if (requestPath.indexOf("/batches/bk-all") > -1) {
                return batchStatus;
            }
            const query = new URLSearchParams(requestPath.split("?")[1] || "");
            if (query.get("decision")) return { total: 0, experts: [] };
            return { total: 20, experts: [expertRow(1)] };
        };
        batchStatus = { batchKey: "bk-all", phase: "PREPARING", total: 0, counts: {}, items: [], nextCursor: null };
        await openReviewTab(sandbox);
        sandbox.$("#discoveryReviewApproveAll").click();
        sandbox.$("#discoveryReviewCommit").click();
        await flush(6);
        const prepare = sandbox.__apiCalls.find((c) => c.path.indexOf("/batches/prepare") > -1);
        const prepareBody = JSON.parse(prepare.options.body);
        assert.strictEqual(prepareBody.scope, "ALL_MATCHING");
        assert.ok(prepare.path.includes("tag=discovered"));
        assert.ok(sandbox.$("#discoveryReviewCommit").disabled, "commit must stay disabled while PREPARING");
        assert.ok(sandbox.$("#discoveryReviewCommit").textContent.includes("正在固定名单"));
        assert.ok(sandbox.$("#discoveryReviewResult").textContent.includes("正在固定名单"));
        assert.ok(!sandbox.__apiCalls.some((c) => c.path.indexOf("/confirm") > -1),
            "202/PREPARING must never be treated as confirmation");

        batchStatus = { batchKey: "bk-all", phase: "READY", batchHash: "hash-all", total: 10005, counts: {}, items: [], nextCursor: null };
        await runNextTimer(sandbox);
        assert.ok(sandbox.$("#discoveryReviewConfirmScope").textContent.includes("固定总数：10005"));
        assert.strictEqual(sandbox.$("#discoveryReviewCommit").disabled, false);
        sandbox.$("#discoveryReviewCommit").click();
        await flush(6);
        const confirm = sandbox.__apiCalls.find((c) => c.path.indexOf("/confirm") > -1);
        assert.ok(confirm.path.includes("/batches/bk-all/confirm"));
        assert.deepStrictEqual(JSON.parse(confirm.options.body), { batchHash: "hash-all" });
        assert.strictEqual(sandbox.$("#discoveryReviewCommit").disabled, true, "APPLYING is not completion");
        assert.ok(sandbox.$("#discoveryReviewResult").textContent.includes("处理中"));
    });

    it("refreshes success counts only after the persisted APPLIED phase (I-1)", async () => {
        const sandbox = loadSandbox();
        let phase = "PREPARING";
        sandbox.apiHandler = (requestPath, options) => {
            if (requestPath.indexOf("/batches/prepare") > -1) {
                return { batchKey: "bk-all", phase: "PREPARING", total: 3, counts: {}, items: [] };
            }
            if (requestPath.indexOf("/confirm") > -1) {
                return { batchKey: "bk-all", total: 3, applied: 0, stale: 0, failed: 0, skipped: 0, items: [] };
            }
            if (requestPath.indexOf("/batches/bk-all") > -1) {
                return {
                    batchKey: "bk-all", phase, batchHash: "h", total: 3,
                    applied: phase === "APPLIED" ? 2 : 0, failed: phase === "APPLIED" ? 1 : 0,
                    stale: 0, cancelled: 0, pending: phase === "APPLIED" ? 0 : 3,
                    items: [], nextCursor: null
                };
            }
            const query = new URLSearchParams(requestPath.split("?")[1] || "");
            if (query.get("decision")) return { total: 0, experts: [] };
            return { total: 0, experts: [] };
        };
        sandbox.currentTaskModal = { taskType: "EXPERT_DISCOVERY", generation: 7 };
        sandbox.initDiscoveryReview("EXPERT_DISCOVERY", 7);
        sandbox.$("#discoveryReviewTab").dispatch("click", {});
        await flush();
        sandbox.$("#discoveryReviewApproveAll").click();
        sandbox.$("#discoveryReviewCommit").click();
        await flush(6);
        phase = "APPLYING";
        await runNextTimer(sandbox);
        assert.ok(!sandbox.$("#discoveryReviewResult").textContent.includes("已应用"),
            "APPLYING must not report success");
        phase = "APPLIED";
        await runNextTimer(sandbox);
        assert.ok(sandbox.$("#discoveryReviewResult").textContent.includes("已应用 2"));
        assert.ok(sandbox.$("#discoveryReviewResult").textContent.includes("失败 1"));
    });

    it("stops interrupted apply polling without completion refresh and retries only on explicit record action", async () => {
        const sandbox = loadSandbox();
        let phase = "READY";
        const status = () => ({
            batchKey: "bk-interrupted", batchHash: "fixed-hash", phase, total: 3,
            applied: phase === "APPLIED" ? 3 : 1, pending: phase === "APPLIED" ? 0 : 2,
            failed: 0, stale: 0, cancelled: 0, items: [], nextCursor: null
        });
        sandbox.apiHandler = (requestPath) => {
            if (requestPath.includes("/batches/prepare")) return status();
            if (requestPath.endsWith("/confirm")) { phase = "APPLYING"; return status(); }
            if (requestPath.endsWith("/retry")) { phase = "APPLYING"; return status(); }
            if (requestPath.includes("/batches/bk-interrupted")) return status();
            if (requestPath.endsWith("/task-executions/41")) {
                return { rawResultSummary: JSON.stringify({ batchKey: "bk-interrupted" }) };
            }
            return { total: 0, experts: [] };
        };
        await openReviewTab(sandbox);
        sandbox.$("#discoveryReviewApproveAll").click();
        sandbox.$("#discoveryReviewCommit").click();
        await flush();
        sandbox.$("#discoveryReviewCommit").click();
        await flush();
        const callsBeforeInterruption = sandbox.__apiCalls.length;
        phase = "INTERRUPTED";
        await runNextTimer(sandbox);
        assert.strictEqual(sandbox.__dr.state.confirm.phase, "INTERRUPTED");
        assert.strictEqual(sandbox.__dr.state.confirm.counts.READY, 2);
        assert.ok(sandbox.$("#discoveryReviewConfirmScope").textContent.includes("已中断"));
        assert.ok(sandbox.$("#discoveryReviewResult").textContent.includes("不会自动继续"));
        assert.ok(!sandbox.$("#discoveryReviewResult").textContent.includes("已完成"));
        assert.strictEqual(sandbox.$("#discoveryReviewCommit").textContent, "已中断");
        assert.strictEqual(sandbox.$("#discoveryReviewCommit").disabled, true);
        assert.strictEqual(sandbox.__dr.state.pollTimer, null);
        assert.strictEqual(sandbox.__apiCalls.length, callsBeforeInterruption + 1,
            "the interruption poll must not refresh counts/list or restart the worker");
        sandbox.resumeDiscoveryReviewPolling();
        await sandbox.discoveryReviewCommit();
        assert.strictEqual(sandbox.__dr.state.pollTimer, null);
        assert.ok(!sandbox.__apiCalls.some((call) => call.path.endsWith("/retry")));
        sandbox.invalidateDiscoveryReviewBatch("changed filter");
        assert.strictEqual(sandbox.__dr.state.confirm.phase, "INTERRUPTED",
            "submitted interrupted snapshot must not be silently discarded on filter changes");

        const record = { id: 41, taskType: "DISCOVERY_REVIEW_PREPARE", status: "SUCCESS" };
        const key = sandbox.discoveryReviewRecordKey(record);
        const detail = { record, batchKey: "bk-interrupted" };
        sandbox.__dr.state.recordDetails[key] = detail;
        sandbox.discoveryReviewApplyRecordStatus(detail, status());
        const article = sandbox.buildDiscoveryReviewRecord(record);
        const retryButton = article.children[article.children.length - 1].children[0];
        assert.strictEqual(retryButton.textContent, "重试未处理项");
        assert.strictEqual(retryButton.disabled, false);
        retryButton.click();
        await flush();
        assert.strictEqual(sandbox.__apiCalls.filter((call) => call.path.endsWith("/retry")).length, 1);
        assert.strictEqual(detail.phase, "APPLYING");
        assert.ok(!sandbox.$("#discoveryReviewResult").textContent.includes("已完成"));

        sandbox.discoveryReviewApplyBatchStatus(status());
        sandbox.resumeDiscoveryReviewPolling();
        phase = "APPLIED";
        await runNextTimer(sandbox);
        assert.ok(sandbox.$("#discoveryReviewResult").textContent.includes("审核已完成：已应用 3"));
        assert.strictEqual(sandbox.__dr.state.confirm.counts.READY, 0);
    });

    it("requires a note before REJECT and never re-sends the query on confirm", async () => {
        const sandbox = loadSandbox();
        sandbox.apiHandler = scopeHandler(sandbox);
        await openReviewTab(sandbox);
        const rejectButton = sandbox.__actionButtons.find((b) => b.dataset.reviewAction === "REJECT");
        // 勾选当前页一人
        const firstRowCheckbox = sandbox.$("#discoveryReviewRows").children[0].children[0].children[0];
        firstRowCheckbox.checked = true;
        firstRowCheckbox.dispatch("change", {});
        rejectButton.click();
        assert.strictEqual(sandbox.$("#discoveryReviewCommit").disabled, true, "REJECT needs a note");
        sandbox.$("#discoveryReviewCommit").click();
        await flush(2);
        assert.strictEqual(sandbox.__apiCalls.filter((c) => c.path.indexOf("/batches/prepare") > -1).length, 0);
        assert.ok(sandbox.$("#discoveryReviewError").textContent.includes("不通过必须填写备注"));
        sandbox.$("#discoveryReviewNote").value = "研究经历不符合";
        sandbox.$("#discoveryReviewNote").dispatch("input", {});
        assert.strictEqual(sandbox.$("#discoveryReviewCommit").disabled, false);
        sandbox.$("#discoveryReviewCommit").click();
        await flush(6);
        const prepare = sandbox.__apiCalls.find((c) => c.path.indexOf("/batches/prepare") > -1);
        assert.strictEqual(JSON.parse(prepare.options.body).note, "研究经历不符合");
    });

    it("changing a filter voids an unconfirmed snapshot but never drops a submitted batch", async () => {
        const sandbox = loadSandbox();
        let phase = "READY";
        sandbox.apiHandler = (requestPath) => {
            if (requestPath.indexOf("/batches/prepare") > -1) {
                return { batchKey: "bk-all", phase: "PREPARING", total: 10005, counts: {}, items: [] };
            }
            if (requestPath.indexOf("/confirm") > -1) {
                return { batchKey: "bk-all", total: 10005, applied: 0, stale: 0, failed: 0, skipped: 0, items: [] };
            }
            if (requestPath.indexOf("/batches/bk-all") > -1) {
                return {
                    batchKey: "bk-all", phase, batchHash: "h", total: 10005,
                    counts: {}, items: [], nextCursor: null
                };
            }
            const query = new URLSearchParams(requestPath.split("?")[1] || "");
            if (query.get("decision")) return { total: 0, experts: [] };
            return { total: 20, experts: [expertRow(1)] };
        };
        await openReviewTab(sandbox);
        // 1) 尚未提交的确认框：筛选变化即作废
        sandbox.$("#discoveryReviewApprovePage").click();
        assert.ok(sandbox.__dr.state.confirm && !sandbox.__dr.state.confirm.batchKey);
        sandbox.$("#discoveryReviewIssue").value = "ANY";
        sandbox.$("#discoveryReviewIssue").dispatch("change", {});
        assert.strictEqual(sandbox.__dr.state.confirm, null, "unconfirmed snapshot is voided by a filter change");
        assert.strictEqual(sandbox.$("#discoveryReviewConfirm").hidden, true);
        // 2) 已提交并进入应用的批次：筛选变化不丢弃视图，也不扩大范围
        sandbox.$("#discoveryReviewApproveAll").click();
        sandbox.$("#discoveryReviewCommit").click();
        await flush(6);
        await runNextTimer(sandbox);
        assert.strictEqual(sandbox.$("#discoveryReviewCommit").disabled, false, "READY allows confirmation");
        sandbox.$("#discoveryReviewCommit").click();
        await flush(6);
        assert.strictEqual(sandbox.__dr.state.confirm.phase, "APPLYING");
        sandbox.$("#discoveryReviewIssue").value = "";
        sandbox.$("#discoveryReviewIssue").dispatch("change", {});
        assert.ok(sandbox.__dr.state.confirm && sandbox.__dr.state.confirm.batchKey === "bk-all",
            "a submitted APPLYING batch must stay visible and keep its frozen scope");
    });

    it("invalidates slow review responses on tab switches and pending prepare on selection changes", async () => {
        const sandbox = loadSandbox();
        let resolveList;
        sandbox.apiHandler = (requestPath) => {
            if (requestPath.includes("decision=")) return { total: 0, experts: [] };
            return new Promise(resolve => { resolveList = resolve; });
        };
        sandbox.currentTaskModal = { taskType: "EXPERT_DISCOVERY", generation: 3 };
        sandbox.initDiscoveryReview("EXPERT_DISCOVERY", 3);
        sandbox.selectDiscoveryReviewTab("review");
        await flush();
        sandbox.selectDiscoveryReviewTab("pipeline");
        resolveList({ total: 1, experts: [expertRow(1)] });
        await flush();
        assert.strictEqual(sandbox.__dr.state.rows.length, 0);
        const before = sandbox.__dr.state.prepareSeq;
        sandbox.__dr.state.confirm = { scope: "PAGE", phase: null };
        sandbox.closeDiscoveryReviewConfirm();
        assert.ok(sandbox.__dr.state.prepareSeq > before);
    });

    it("only enables per-expert revoke for the current manual revision", () => {
        const sandbox = loadSandbox();
        sandbox.__dr.state.rows = [expertRow(1, { decisionManual: true, revision: 4 })];
        assert.strictEqual(sandbox.discoveryReviewIsCurrentDecision({
            expertDocId: "doc-1", state: "APPLIED", action: "APPROVE", expectedRevision: 3
        }), true);
        assert.strictEqual(sandbox.discoveryReviewIsCurrentDecision({
            expertDocId: "doc-1", state: "APPLIED", action: "APPROVE", expectedRevision: 2
        }), false);
    });

    it("no new review filter is added to the batch-send console (I-4)", () => {
        assert.ok(!appJsSource.includes("sendReview"), "no sendReview switch may exist");
        assert.ok(!indexHtml.includes("sendReview"), "no sendReview DOM may exist");
        assert.ok(!/batchConfigEditor[A-Za-z]*ReviewFilter/.test(indexHtml),
            "no review filter may be added to the batch editor");
    });
});
