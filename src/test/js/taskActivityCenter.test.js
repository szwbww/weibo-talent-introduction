const fs = require("fs");
const path = require("path");
const vm = require("vm");
const assert = require("assert");
const { describe, it } = require("node:test");

const staticDir = path.join(__dirname, "..", "..", "main", "resources", "static");
const appSource = fs.readFileSync(path.join(staticDir, "app.js"), "utf-8");
const htmlSource = fs.readFileSync(path.join(staticDir, "index.html"), "utf-8");
const cssSource = fs.readFileSync(path.join(staticDir, "styles.css"), "utf-8");

/**
 * c3/T-3：缓存键**不写死**在测试里 —— 从随包发布的 `index.html` 派生，
 * 因此后续任何一次键 bump 都不需要再改测试；断言的仍然是资源一致性契约。
 */
const CACHE_KEY = (() => {
    const match = htmlSource.match(/styles\.css\?v=([^"'&<>]+)/);
    if (!match) throw new Error("index.html must register styles.css with a ?v= cache key");
    return match[1];
})();

const ORDERED_ASSETS = [
    "styles.css",
    "expert-materials.css",
    "mailbox-chat.css",
    "meeting-confirmation.css",
    "world-clock.css",
    "trust-reply-workbench.js",
    "expert-materials.js",
    "meeting-confirmation.js",
    "mailbox-chat.js",
    "app.js",
    "world-clock.js"
];

/** 历史键必须零命中（K-frontend-cache-key-triad：新旧键不得混用）。 */
const RETIRED_KEYS = ["20260920-manual-material-upload", "20260922-task-activity-center", "20260903-bounce-warning"];

// ── source extraction ──────────────────────────────────────────────────────────────

/** `const X = ...;` with brace/bracket/paren balancing (object literals span many lines). */
function extractConstStatement(name) {
    const header = `const ${name} =`;
    const start = appSource.indexOf(header);
    if (start < 0) throw new Error(`Could not find ${header} in app.js`);
    let index = start + header.length;
    let depth = 0;
    let quote = null;
    while (index < appSource.length) {
        const char = appSource[index];
        if (quote) {
            if (char === "\\") { index += 2; continue; }
            if (char === quote) quote = null;
        } else if (char === '"' || char === "'" || char === "`") {
            quote = char;
        } else if (char === "{" || char === "[" || char === "(") {
            depth += 1;
        } else if (char === "}" || char === "]" || char === ")") {
            depth -= 1;
        } else if (char === ";" && depth === 0) {
            return appSource.slice(start, index + 1);
        }
        index += 1;
    }
    throw new Error(`Unterminated ${header}`);
}

function extractFn(name) {
    const regex = new RegExp(`(?:async\\s+)?function\\s+${name}\\s*\\([^)]*\\)\\s*\\{`);
    const match = regex.exec(appSource);
    if (!match) throw new Error(`Could not find ${name} in app.js`);
    const startIndex = match.index + match[0].length;
    let depth = 1;
    let index = startIndex;
    while (depth > 0 && index < appSource.length) {
        const char = appSource[index];
        if (char === "{") depth += 1;
        else if (char === "}") depth -= 1;
        index += 1;
    }
    if (depth > 0) throw new Error(`Unmatched braces in ${name}`);
    return appSource.slice(match.index, index);
}

const OBSERVER_FUNCTIONS = [
    "taskActivityDocumentHidden",
    "taskActivityTriggerLabel",
    "formatTaskActivityElapsed",
    "taskActivitySafeCount",
    "taskActivitySafePercentage",
    "taskActivityExecutionId",
    "taskActivityStatusLabel",
    "taskActivityCanOpenControl",
    "startTaskActivityPolling",
    "stopTaskActivityPolling",
    "resetTaskActivityDom",
    "scheduleTaskActivityRefresh",
    "taskActivityResponseIsCurrent",
    "refreshTaskActivity",
    "taskActivityCollectionChanged",
    "applyTaskActivitySnapshot",
    "renderTaskActivityTransientEmpty",
    "renderTaskActivityError",
    "showTaskActivityListHint",
    "paintTaskActivityEntryPoints",
    "taskActivityCardFields",
    "taskActivityCardHtml",
    "syncTaskActivityProgressBar",
    "updateTaskActivityCard",
    "renderTaskActivityCards",
    "renderTaskActivityPager",
    "syncTaskActivityDetailButtons",
    "isCurrentTaskActivityDetail",
    "refreshTaskActivityDetail",
    "refreshTaskActivityDetailIfNeeded",
    "setTaskActivityDetailStatus",
    "renderTaskActivityDetail",
    "openTaskActivityDetail",
    "closeTaskActivityDetail",
    "taskActivityLogTime",
    "renderTaskActivityLogs",
    "loadTaskActivityLogs",
    "openTaskActivityControl",
    "initTaskActivityObserver",
    "renderTaskDetailRawBlocks",
    "resumeProgressPollingIfNeeded"
];

const OBSERVER_CONSTS = [
    "statusLabels",
    "taskButtonMapping",
    "taskActivityTriggerLabels",
    "taskActivityState",
    "TASK_ACTIVITY_POLL_INTERVAL_MS",
    "TASK_ACTIVITY_PAGE_SIZE",
    "TASK_ACTIVITY_LOG_LINE_LIMIT",
    "TASK_ACTIVITY_NOTE_OK",
    "TASK_ACTIVITY_NOTE_STALE"
];

// ── minimal DOM ────────────────────────────────────────────────────────────────────

const VOID_TAGS = new Set(["BR", "IMG", "INPUT", "HR", "META", "LINK"]);

function datasetKey(name) {
    return name.slice(5).replace(/-([a-z])/g, (_, c) => c.toUpperCase());
}

function attributeName(prop) {
    return `data-${String(prop).replace(/[A-Z]/g, (c) => `-${c.toLowerCase()}`)}`;
}

/** 真实 DOM 的 dataset 会同步写回属性；stub 必须一样，否则属性选择器会查不到节点。 */
function createDataset(element) {
    return new Proxy({}, {
        get: (target, prop) => (typeof prop === "string" ? element.attrs[attributeName(prop)] : undefined),
        set: (target, prop, value) => {
            element.attrs[attributeName(prop)] = String(value);
            return true;
        },
        deleteProperty: (target, prop) => {
            delete element.attrs[attributeName(prop)];
            return true;
        },
        has: (target, prop) => element.attrs[attributeName(prop)] !== undefined,
        ownKeys: () => Object.keys(element.attrs).filter((name) => name.startsWith("data-")),
        getOwnPropertyDescriptor: (target, prop) => ({ configurable: true, enumerable: true })
    });
}

class StubElement {
    constructor(tagName) {
        this.tagName = String(tagName).toUpperCase();
        this.childNodes = [];
        this.parentNode = null;
        this.attrs = {};
        this.dataset = createDataset(this);
        this._classes = new Set();
        this._text = "";
        this._innerHtml = "";
        this.hidden = false;
        this.disabled = false;
        this.value = 0;
        this.listeners = {};
        this.focused = false;
    }

    get classList() {
        const self = this;
        return {
            add: (...names) => names.forEach((name) => self._classes.add(name)),
            remove: (...names) => names.forEach((name) => self._classes.delete(name)),
            contains: (name) => self._classes.has(name),
            toggle: (name, force) => {
                const on = force === undefined ? !self._classes.has(name) : force === true;
                if (on) self._classes.add(name); else self._classes.delete(name);
                return on;
            }
        };
    }

    get className() { return Array.from(this._classes).join(" "); }

    set className(value) {
        this._classes = new Set(String(value == null ? "" : value).split(/\s+/).filter(Boolean));
    }

    get children() { return this.childNodes.filter((node) => node instanceof StubElement); }

    get firstChild() { return this.childNodes[0] || null; }

    get nextSibling() {
        if (!this.parentNode) return null;
        const index = this.parentNode.childNodes.indexOf(this);
        return this.parentNode.childNodes[index + 1] || null;
    }

    get textContent() {
        if (this.childNodes.length === 0) return this._text;
        return this.childNodes.map((node) => (node instanceof StubElement ? node.textContent : node)).join("");
    }

    set textContent(value) {
        this.childNodes = [];
        this._text = String(value == null ? "" : value);
        this._innerHtml = "";
    }

    get innerHTML() { return this._innerHtml; }

    set innerHTML(value) {
        this._innerHtml = String(value == null ? "" : value);
        this._text = "";
        this.childNodes = parseHtml(this._innerHtml);
        this.childNodes.forEach((node) => { if (node instanceof StubElement) node.parentNode = this; });
    }

    setAttribute(name, value) {
        const text = String(value);
        this.attrs[name] = text;
        if (name === "class") this.className = text;
        else if (name === "hidden") this.hidden = true;
        else if (name === "disabled") this.disabled = true;
        else if (name.startsWith("data-")) this.dataset[datasetKey(name)] = text;
    }

    getAttribute(name) {
        return this.attrs[name] === undefined ? null : this.attrs[name];
    }

    removeAttribute(name) {
        delete this.attrs[name];
        if (name.startsWith("data-")) delete this.dataset[datasetKey(name)];
    }

    addEventListener(type, handler) {
        (this.listeners[type] = this.listeners[type] || []).push(handler);
    }

    dispatch(type, event = {}) {
        (this.listeners[type] || []).forEach((handler) => handler(event));
    }

    insertBefore(node, reference) {
        if (reference === node) return node;
        if (node.parentNode) node.parentNode.removeChild(node);
        if (reference && node.nextSibling === reference) return node;
        const index = reference ? this.childNodes.indexOf(reference) : -1;
        if (index < 0) this.childNodes.push(node);
        else this.childNodes.splice(index, 0, node);
        node.parentNode = this;
        return node;
    }

    removeChild(node) {
        const index = this.childNodes.indexOf(node);
        if (index >= 0) this.childNodes.splice(index, 1);
        node.parentNode = null;
    }

    remove() {
        if (this.parentNode) this.parentNode.removeChild(this);
    }

    focus() {
        this.focused = true;
        if (this.ownerDocument) this.ownerDocument.activeElement = this;
    }

    querySelector(selector) { return this.querySelectorAll(selector)[0] || null; }

    querySelectorAll(selector) {
        const results = [];
        collect(this, selector, results);
        return results;
    }
}

function parseSelector(selector) {
    const steps = [];
    let combinator = " ";
    for (const token of selector.trim().split(/\s+/)) {
        if (token === ">") { combinator = ">"; continue; }
        steps.push({ combinator, compound: token });
        combinator = " ";
    }
    return steps;
}

function matchesCompound(element, compound) {
    const tokens = compound.match(/(?:^[a-zA-Z][\w-]*)|(?:\.[\w-]+)|(?:#[a-zA-Z][\w-]*)|(?:\[[^\]]+\])/g) || [];
    return tokens.every((token) => {
        if (token.startsWith(".")) return element._classes.has(token.slice(1));
        if (token.startsWith("#")) return element.attrs.id === token.slice(1);
        if (token.startsWith("[")) {
            const inner = token.slice(1, -1);
            const equals = inner.indexOf("=");
            if (equals < 0) return element.attrs[inner] !== undefined;
            const name = inner.slice(0, equals);
            const value = inner.slice(equals + 1).replace(/^["']|["']$/g, "");
            return String(element.attrs[name]) === value;
        }
        return element.tagName === token.toUpperCase();
    });
}

function matchesSteps(element, steps, index) {
    if (!matchesCompound(element, steps[index].compound)) return false;
    if (index === 0) return true;
    if (steps[index].combinator === ">") {
        return element.parentNode instanceof StubElement && matchesSteps(element.parentNode, steps, index - 1);
    }
    let ancestor = element.parentNode;
    while (ancestor instanceof StubElement) {
        if (matchesSteps(ancestor, steps, index - 1)) return true;
        ancestor = ancestor.parentNode;
    }
    return false;
}

function collect(root, selector, results) {
    const steps = parseSelector(selector);
    root.childNodes.forEach((node) => {
        if (!(node instanceof StubElement)) return;
        if (matchesSteps(node, steps, steps.length - 1)) results.push(node);
        collect(node, selector, results);
    });
}

function decodeEntities(text) {
    return text
        .replace(/&lt;/g, "<")
        .replace(/&gt;/g, ">")
        .replace(/&quot;/g, '"')
        .replace(/&#039;/g, "'")
        .replace(/&amp;/g, "&");
}

function parseHtml(html) {
    const root = [];
    const stack = [];
    const push = (node) => {
        const parent = stack[stack.length - 1];
        if (parent) {
            parent.childNodes.push(node);
            if (node instanceof StubElement) node.parentNode = parent;
        } else {
            root.push(node);
        }
    };
    const tagRegex = /<\/?([a-zA-Z][\w-]*)((?:\s+[^\s/>"']+(?:\s*=\s*(?:"[^"]*"|'[^']*'|[^\s"'>]+))?)*)\s*\/?>/g;
    let lastIndex = 0;
    let match;
    while ((match = tagRegex.exec(html)) !== null) {
        const text = html.slice(lastIndex, match.index);
        if (text) push(decodeEntities(text));
        lastIndex = tagRegex.lastIndex;
        if (match[0].startsWith("</")) {
            stack.pop();
            continue;
        }
        const element = new StubElement(match[1]);
        applyAttributes(element, match[2] || "");
        push(element);
        if (!match[0].endsWith("/>") && !VOID_TAGS.has(element.tagName)) stack.push(element);
    }
    const tail = html.slice(lastIndex);
    if (tail) push(tail);
    return root;
}

function applyAttributes(element, raw) {
    const attrRegex = /([^\s=]+)(?:\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'>]+)))?/g;
    let match;
    while ((match = attrRegex.exec(raw)) !== null) {
        element.setAttribute(match[1], match[2] ?? match[3] ?? match[4] ?? "");
    }
}

const FIXTURE_HTML = `
    <header class="topnav task-center-nav">
        <nav class="nav-tabs" aria-label="Main">
            <button class="nav-tab" data-view="tasks">
                <span>任务记录</span>
                <span id="taskActiveNavBadge" class="task-center-pill task-center-nav-count" hidden>
                    <i class="task-center-dot" aria-hidden="true"></i>
                    <span id="taskActiveNavCount"></span>
                </span>
            </button>
        </nav>
    </header>
    <div class="topbar-actions task-center-actions">
        <button type="button" id="taskActiveGlobalBtn" class="task-center-pill task-center-global" hidden>
            <i class="task-center-dot" aria-hidden="true"></i>
            <span id="taskActiveGlobalText"></span>
            <span aria-hidden="true">↗</span>
        </button>
    </div>
    <section id="taskActiveSection" aria-labelledby="taskActiveHeading">
        <div class="task-center-section-head">
            <h2 id="taskActiveHeading">正在执行 <span id="taskActiveCount" class="task-center-count">—</span></h2>
            <span id="taskActiveUpdated" class="task-center-note" role="status" aria-live="polite">正在读取任务状态…</span>
        </div>
        <div id="taskActiveEmpty" class="task-center-empty" hidden>暂无执行中的任务</div>
        <div id="taskActiveCards" class="task-center-grid"></div>
        <div id="taskActivePager" class="list-pager" hidden>
            <button type="button" class="button small" id="taskActivePrevPage">上一页</button>
            <span id="taskActivePageInfo" class="list-pager-info"></span>
            <button type="button" class="button small" id="taskActiveNextPage">下一页</button>
        </div>
    </section>
    <section id="taskActiveDetail" class="task-center-detail" aria-labelledby="taskActiveDetailTitle" hidden>
        <div class="task-center-detail-head">
            <h2 id="taskActiveDetailTitle"></h2>
            <button type="button" id="taskActiveDetailClose" class="task-center-link">收起详情 ↑</button>
        </div>
        <div id="taskActiveDetailStatus" class="task-center-note" role="status" aria-live="polite"></div>
        <div id="taskActiveDetailBody" class="task-center-detail-body"></div>
        <div class="task-center-detail-actions">
            <button type="button" id="taskActiveLoadLogs" class="task-center-link" hidden>加载批次日志</button>
            <button type="button" id="taskActiveOpenControl" class="button small secondary" hidden>打开任务控制</button>
        </div>
        <pre id="taskActiveLogs" class="task-center-log" hidden></pre>
    </section>
    <button type="button" id="taskHistoryRefreshHint" class="task-center-link" hidden>任务状态有变化，刷新执行记录</button>
`;

function createSandbox() {
    const root = new StubElement("div");
    root.innerHTML = FIXTURE_HTML;
    const documentStub = {
        hidden: false,
        activeElement: null,
        body: new StubElement("body"),
        listeners: {},
        createElement: (tag) => new StubElement(tag),
        getElementById: (id) => root.querySelector(`#${id}`),
        querySelector: (selector) => root.querySelector(selector),
        querySelectorAll: (selector) => root.querySelectorAll(selector),
        addEventListener: (type, handler) => {
            (documentStub.listeners[type] = documentStub.listeners[type] || []).push(handler);
        },
        dispatch: (type) => (documentStub.listeners[type] || []).forEach((handler) => handler({ type }))
    };
    const timers = new Map();
    let timerSeq = 0;
    const sandbox = {
        contextPath: "",
        state: { view: "tasks", tasksPage: 2, tasksTotal: 137 },
        currentTaskModal: null,
        document: documentStub,
        window: {},
        console,
        apiCalls: [],
        pendingRequests: [],
        timerCalls: [],
        openTaskModalCalls: [],
        openTaskLaunchModalCalls: 0,
        openBatchSendTaskModalCalls: 0,
        loadTasksCalls: 0,
        statusCalls: [],
        $: (selector) => documentStub.querySelector(selector),
        $$: (selector) => documentStub.querySelectorAll(selector),
        api: (requestPath, options) => new Promise((resolve, reject) => {
            sandbox.apiCalls.push({ path: requestPath, options });
            sandbox.pendingRequests.push({ path: requestPath, resolve, reject });
        }),
        openTaskModal: (...args) => { sandbox.openTaskModalCalls.push(args); },
        openTaskLaunchModal: () => { sandbox.openTaskLaunchModalCalls += 1; },
        openBatchSendTaskModal: () => { sandbox.openBatchSendTaskModalCalls += 1; },
        loadTasks: async () => { sandbox.loadTasksCalls += 1; },
        showStatus: (message, type) => { sandbox.statusCalls.push({ message, type }); },
        setTimeout: (fn, delay) => {
            const id = ++timerSeq;
            timers.set(id, { fn, delay });
            sandbox.timerCalls.push({ id, delay });
            return id;
        },
        clearTimeout: (id) => { timers.delete(id); }
    };
    vm.createContext(sandbox);
    for (const name of OBSERVER_CONSTS) {
        vm.runInContext(extractConstStatement(name), sandbox);
    }
    vm.runInContext("function labelStatus(value) { return statusLabels[value] || value || ''; }", sandbox);
    vm.runInContext("function badge(value, type) { return `<span class=\"badge ${type || ''}\">${escapeHtml(value)}</span>`; }", sandbox);
    vm.runInContext("function escapeHtml(value) { return String(value ?? '').replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('\"', '&quot;').replaceAll(\"'\", '&#039;'); }", sandbox);
    for (const name of OBSERVER_FUNCTIONS) {
        vm.runInContext(extractFn(name), sandbox);
    }
    sandbox.__timers = timers;
    sandbox.__root = root;
    // const 声明是词法绑定，不会成为 sandbox 属性；用访问器读它。
    vm.runInContext("function __taskActivityState() { return taskActivityState; }", sandbox);
    sandbox.initTaskActivityObserver();
    return sandbox;
}

function flush() {
    return new Promise((resolve) => setImmediate(resolve));
}

/** The most recent request: tests must never settle a request they already resolved. */
function latestRequest(sandbox) {
    return sandbox.pendingRequests[sandbox.pendingRequests.length - 1];
}

function resolveRequest(request, data) {
    request.resolve(data);
}

function activeItem(overrides = {}) {
    return Object.assign({
        id: 12845,
        taskType: "EXPERT_ENRICHMENT",
        taskTypeLabel: "学术数据补全",
        triggerType: "SCHEDULED",
        status: "RUNNING",
        startedAt: "2026-09-22 14:28:32",
        elapsedSeconds: 226,
        metricLabel: null,
        successCount: 0,
        failureCount: 0,
        hasProgressUi: true,
        progress: null
    }, overrides);
}

function activeResponse(items, total = items.length, page = 0, size = 6) {
    return { items, total, page, size };
}

/** Boot the observer with a first successful snapshot already applied. */
async function bootWith(sandbox, data) {
    sandbox.startTaskActivityPolling();
    resolveRequest(latestRequest(sandbox), data);
    await flush();
    sandbox.pendingRequests.length = 0;
    sandbox.apiCalls.length = 0;
    return sandbox;
}

function cardIds(sandbox) {
    return Array.from(sandbox.$("#taskActiveCards").querySelectorAll(".task-center-card"))
        .map((card) => card.dataset.executionId);
}

// ── S-0 / S-1 / S-2 / S-3 / S-4 / I-8: static contracts ────────────────────────────

describe("task activity center static contract", () => {
    const block = (() => {
        const start = cssSource.indexOf("/* task-center-contract:start */");
        const end = cssSource.indexOf("/* task-center-contract:end */");
        assert.ok(start >= 0 && end > start, "styles.css must carry the S-0 markers");
        return cssSource.slice(start, end + "/* task-center-contract:end */".length);
    })();

    it("S-0: the contract block is appended once and nothing follows it", () => {
        assert.strictEqual(cssSource.split("/* task-center-contract:start */").length - 1, 1);
        assert.strictEqual(cssSource.split("/* task-center-contract:end */").length - 1, 1);
        const after = cssSource.slice(cssSource.indexOf("/* task-center-contract:end */")
            + "/* task-center-contract:end */".length);
        assert.strictEqual(after.trim(), "", "S-0 must be the last thing in styles.css");
    });

    it("S-0: three/two/one column breakpoints and dark theme are declared verbatim", () => {
        assert.ok(block.includes(".task-center-grid {\n    display: grid;\n    grid-template-columns: repeat(3, minmax(0, 1fr));\n    gap: 14px;"),
            "desktop grid must be three columns with 14px gap");
        assert.ok(block.includes("@media (max-width: 1100px) {\n    .task-center-nav .nav-tabs {\n        flex-basis: 100%;\n        flex-wrap: nowrap;\n    }\n    .task-center-grid {\n        grid-template-columns: repeat(2, minmax(0, 1fr));\n    }\n}"),
            "<=1100px must fall back to two columns");
        assert.ok(block.includes("@media (max-width: 760px) {\n    .task-center-grid {\n        grid-template-columns: minmax(0, 1fr);\n    }\n    .task-center-card {\n        padding: 13px;\n    }\n}"),
            "<=760px must fall back to one column with 13px padding");
        assert.ok(block.includes("@media (prefers-color-scheme: dark)"));
        assert.ok(block.includes("background: rgba(21, 31, 48, 0.85);"));
        assert.ok(block.includes("color: #e2e8f0;"));
    });

    it("S-1: the topnav keeps its ten entries plus logout and both task entries", () => {
        assert.ok(htmlSource.includes('<header class="topnav task-center-nav">'));
        assert.ok(htmlSource.includes('<div class="topbar-actions task-center-actions">'));
        const navRegion = htmlSource.slice(htmlSource.indexOf('<nav class="nav-tabs"'), htmlSource.indexOf("</header>"));
        assert.strictEqual((navRegion.match(/data-view="/g) || []).length, 10,
            "all ten navigation entries must survive the new header classes");
        assert.ok(navRegion.includes('class="nav-tab logout-btn" id="logoutBtn"'), "logout stays");
        assert.ok(navRegion.includes('id="currentUserDisplay"'), "the user name stays");
        assert.strictEqual((navRegion.match(/id="taskActiveNavBadge"/g) || []).length, 1);
        // 徽标是 span，不是嵌套 button；任务 tab 仍是唯一 button。
        const badgeIndex = navRegion.indexOf('id="taskActiveNavBadge"');
        assert.ok(navRegion.slice(badgeIndex).startsWith('id="taskActiveNavBadge" class="task-center-pill task-center-nav-count" hidden>'));
        const tasksTabStart = navRegion.lastIndexOf("<button", navRegion.indexOf('data-view="tasks"'));
        const tasksTab = navRegion.slice(tasksTabStart, navRegion.indexOf("</nav>"));
        assert.strictEqual((tasksTab.match(/<button/g) || []).length, 1, "任务 tab 内不得嵌套 button");
        assert.strictEqual((tasksTab.match(/<span/g) || []).length, 3);
    });

    it("S-1: the global entry is a standalone control, not a nav tab", () => {
        const globalIndex = htmlSource.indexOf('id="taskActiveGlobalBtn"');
        assert.ok(globalIndex >= 0, "global entry must exist");
        assert.ok(htmlSource.slice(globalIndex).startsWith('id="taskActiveGlobalBtn" class="task-center-pill task-center-global" hidden'));
        assert.ok(htmlSource.slice(globalIndex, htmlSource.indexOf("</button>", globalIndex)).includes('id="taskActiveGlobalText"'));
        assert.ok(!htmlSource.slice(globalIndex - 200, globalIndex).includes('class="nav-tab"'),
            "global control must not be a .nav-tab (setView would treat it as a view switch)");
        assert.strictEqual((htmlSource.match(/id="taskActiveGlobalBtn"/g) || []).length, 1);
    });

    it("S-2: the running section carries the fixed skeleton and independent pager", () => {
        for (const id of ["taskActiveSection", "taskActiveHeading", "taskActiveCount", "taskActiveUpdated",
            "taskActiveEmpty", "taskActiveCards", "taskActivePager", "taskActivePrevPage",
            "taskActivePageInfo", "taskActiveNextPage"]) {
            assert.strictEqual((htmlSource.match(new RegExp(`id="${id}"`, "g")) || []).length, 1, `${id} must exist once`);
        }
        assert.ok(htmlSource.includes('<div id="taskActiveCards" class="task-center-grid"></div>'));
        assert.ok(htmlSource.includes('<div id="taskActivePager" class="list-pager" hidden>'));
        const tasksView = htmlSource.slice(htmlSource.indexOf('id="view-tasks"'));
        assert.ok(tasksView.indexOf('id="taskActiveSection"') < tasksView.indexOf('id="taskActiveDetail"'));
        assert.ok(tasksView.indexOf('id="taskActiveDetail"') < tasksView.indexOf('id="taskHistoryRefreshHint"'));
        assert.ok(tasksView.indexOf('id="taskHistoryRefreshHint"') < tasksView.indexOf('<div class="toolbar">'),
            "the active area must precede the existing toolbar");
    });

    it("S-3: exactly one in-page detail section, no overlay, plain-text log sink", () => {
        assert.strictEqual((htmlSource.match(/id="taskActiveDetail"/g) || []).length, 1);
        assert.ok(htmlSource.includes('<section id="taskActiveDetail" class="task-center-detail" aria-labelledby="taskActiveDetailTitle" hidden>'));
        assert.ok(htmlSource.includes('<pre id="taskActiveLogs" class="task-center-log" hidden></pre>'));
        const detailRegion = htmlSource.slice(htmlSource.indexOf('id="taskActiveDetail"'),
            htmlSource.indexOf('id="taskHistoryRefreshHint"'));
        assert.ok(!detailRegion.includes("modal-overlay"), "详情不得是 overlay/drawer");
        assert.ok(!detailRegion.includes("style="), "详情不得携带 inline style");
        assert.ok(!detailRegion.includes("onclick="), "详情不得携带内联事件");
    });

    it("manual interruption has one real reason form and an interrupted history filter", () => {
        for (const id of ["taskActiveInterruptPanel", "taskActiveInterruptReason", "taskActiveInterruptDetail", "taskActiveInterruptSubmit"]) {
            assert.strictEqual((htmlSource.match(new RegExp(`id="${id}"`, "g")) || []).length, 1);
        }
        assert.ok(htmlSource.includes('<option value="OTHER">其他原因（填写说明）</option>'));
        assert.ok(htmlSource.includes('<option value="INTERRUPTED">已中断</option>'));
        assert.ok(appSource.includes('function interruptTaskActivityExecution()'));
        assert.ok(appSource.includes('method: "POST", body: JSON.stringify({ reasonCode, detail })'));
    });

    it("S-4: the history table keeps its seven columns, filters and pager", () => {
        const viewStart = htmlSource.indexOf('id="view-tasks"');
        const viewEnd = htmlSource.indexOf("</section>", htmlSource.indexOf('id="taskPager"'));
        const view = htmlSource.slice(viewStart, viewEnd);
        for (const header of ["审计 ID", "任务类型", "触发方式", "当前状态", "发信统计/成功数", "开始时间", "异常堆栈/错误原因"]) {
            assert.ok(view.includes(`<th>${header}</th>`), `${header} column must survive`);
        }
        for (const id of ["taskTypeFilter", "taskStatusFilter", "loadTasksBtn", "tasksTable", "taskPager",
            "taskPrevPage", "taskPageInfo", "taskNextPage"]) {
            assert.strictEqual((htmlSource.match(new RegExp(`id="${id}"`, "g")) || []).length, 1, `${id} must survive`);
        }
        assert.ok(view.includes("<h2>执行记录</h2>"));
    });

    it("I-8: all eleven versioned assets share the same cache key and the old one is gone", () => {
        const keys = htmlSource.match(/\?v=[^"']+/g) || [];
        assert.strictEqual(keys.length, 11, `expected 11 versioned assets, found ${keys.length}`);
        keys.forEach((key) => assert.strictEqual(key, `?v=${CACHE_KEY}`));
        assert.ok(!htmlSource.includes("task-center.css"), "不得新增资源文件");
        assert.ok(htmlSource.includes('<script src="task-modal-runtime.js"></script>'),
            "task-modal-runtime.js 保持无版本键的原引用方式");
        assert.ok(!htmlSource.includes("task-modal-runtime.js?v="));

        // c3/T-3：注册顺序与发布 triad（样式 / 工作台 / 主脚本）必须同键。
        let previous = -1;
        for (const asset of ORDERED_ASSETS) {
            const at = htmlSource.indexOf(asset + "?v=" + CACHE_KEY);
            assert.ok(at > previous, asset + " must stay in registration order (CSS then workbench -> app)");
            previous = at;
        }
        for (const retired of RETIRED_KEYS) {
            if (retired === CACHE_KEY) continue;
            assert.ok(!htmlSource.includes(retired), "retired cache key must have zero hits in index.html: " + retired);
        }
        // 键字面量只允许出现在 index.html：脚本与测试都不得再固化它。
        const stragglers = [
            ...fs.readdirSync(staticDir).filter((name) => name.endsWith(".js")).map((name) => path.join(staticDir, name)),
            ...fs.readdirSync(__dirname).filter((name) => name.endsWith(".js")).map((name) => path.join(__dirname, name))
        ].filter((file) => fs.readFileSync(file, "utf-8").includes(CACHE_KEY));
        assert.deepStrictEqual(stragglers, [],
            "a cache key literal must live only in index.html — found in: " + stragglers.join(", "));
        assert.ok(/^[0-9]{8}-[a-z0-9-]+$/.test(CACHE_KEY), "cache key must be <yyyymmdd>-<slug>, got: " + CACHE_KEY);
    });

    it("I-8: the new markup carries no inline style or inline handler", () => {
        const globalStart = htmlSource.indexOf('id="taskActiveGlobalBtn"');
        const globalEnd = htmlSource.indexOf("</button>", globalStart);
        const sectionStart = htmlSource.indexOf('id="taskActiveSection"');
        const sectionEnd = htmlSource.indexOf('id="taskHistoryRefreshHint"');
        assert.ok(globalStart >= 0 && sectionStart >= 0 && sectionEnd > sectionStart);
        const region = htmlSource.slice(globalStart, globalEnd)
            + htmlSource.slice(sectionStart, sectionEnd + 200);
        assert.ok(!region.includes("style="), "新增 DOM 不得有 inline style");
        assert.ok(!region.includes("onclick="), "新增 DOM 不得有内联事件");
    });

    it("I-8: production sources carry no preview fixture data", () => {
        assert.ok(!appSource.includes("12846"), "生产脚本不得包含演示 fixture");
        assert.ok(!appSource.includes("preview.js"));
    });

    it("I-8: every id the observer reaches for exists in the real index.html", () => {
        const observerSource = OBSERVER_FUNCTIONS.map((name) => extractFn(name)).join("\n");
        const ids = new Set();
        const regex = /\$\("#([\w-]+)"/g;
        let match;
        while ((match = regex.exec(observerSource)) !== null) ids.add(match[1]);
        assert.ok(ids.size >= 12, `expected the observer to address many ids, found ${ids.size}`);
        ids.forEach((id) => {
            assert.ok(htmlSource.includes(`id="${id}"`), `#${id} is addressed by app.js but missing from index.html`);
        });
    });

    it("S-2/I-8: card rendering uses native progress value and never style.width", () => {
        const source = (extractFn("taskActivityCardHtml") + extractFn("syncTaskActivityProgressBar")
            + extractFn("updateTaskActivityCard")).replace(/^\s*\/\/.*$/gm, "");
        assert.ok(source.includes('value="${fields.percentage}"'), "创建路径必须用原生 value 属性");
        assert.ok(source.includes("bar.value = fields.percentage"), "更新路径必须写原生 value");
        assert.ok(!/style\.(width|display)/.test(source), "不得用 inline style 控制进度条");
        assert.ok(!source.includes("innerHTML = "), "复用路径不得重建卡片 HTML");
    });
});

// ── I-1 / I-5: non-intrusive observation ───────────────────────────────────────────

describe("task activity observer is non-intrusive", () => {
    it("I-1: login, ticks and new active rows never open a modal", async () => {
        const sandbox = createSandbox();
        sandbox.currentTaskModal = { taskType: "EXPERT_ENRICHMENT", generation: 7 };
        const preservedContext = sandbox.currentTaskModal;

        sandbox.startTaskActivityPolling();
        resolveRequest(latestRequest(sandbox), activeResponse([activeItem({ status: "RUNNING" })], 1));
        await flush();
        sandbox.__timers.forEach((timer) => { sandbox.clearTimeout(0); timer.fn(); });
        await flush();
        resolveRequest(latestRequest(sandbox), activeResponse([activeItem({ id: 99, status: "CANCELLING" })], 1));
        await flush();

        assert.deepStrictEqual(sandbox.openTaskModalCalls, []);
        assert.strictEqual(sandbox.openTaskLaunchModalCalls, 0);
        assert.strictEqual(sandbox.openBatchSendTaskModalCalls, 0);
        assert.strictEqual(sandbox.currentTaskModal, preservedContext, "已打开的业务弹框上下文不得被替换");
        assert.ok(!sandbox.document.body.classList.contains("modal-open"));
    });

    it("I-1: the resume entry point only starts the observer", async () => {
        const sandbox = createSandbox();
        await sandbox.resumeProgressPollingIfNeeded();
        assert.strictEqual(sandbox.__taskActivityState().started, true);
        assert.strictEqual(sandbox.apiCalls.length, 1);
        assert.strictEqual(sandbox.apiCalls[0].path, "/api/task-executions/active?page=0&size=6");
        assert.deepStrictEqual(sandbox.openTaskModalCalls, []);
        assert.strictEqual(sandbox.openBatchSendTaskModalCalls, 0);
    });

    it("I-5: repeated start keeps exactly one polling chain", async () => {
        const sandbox = createSandbox();
        sandbox.startTaskActivityPolling();
        sandbox.startTaskActivityPolling();
        sandbox.startTaskActivityPolling();
        assert.strictEqual(sandbox.apiCalls.length, 1, "重复 start 不得并发请求");
        resolveRequest(latestRequest(sandbox), activeResponse([], 0));
        await flush();
        assert.strictEqual(sandbox.__timers.size, 1, "只允许一个待执行定时器");
        assert.strictEqual(sandbox.timerCalls[0].delay, 5000);
    });

    it("I-5: a tick never reloads the history table or resets its filters", async () => {
        const sandbox = createSandbox();
        await bootWith(sandbox, activeResponse([activeItem()], 1));
        const before = { view: sandbox.state.view, page: sandbox.state.tasksPage, total: sandbox.state.tasksTotal };

        sandbox.__timers.forEach((timer) => timer.fn());
        await flush();
        resolveRequest(latestRequest(sandbox), activeResponse([activeItem({ status: "CANCELLING" })], 1));
        await flush();

        assert.strictEqual(sandbox.loadTasksCalls, 0, "全局 tick 不得整表重绘");
        assert.deepStrictEqual(
            { view: sandbox.state.view, page: sandbox.state.tasksPage, total: sandbox.state.tasksTotal },
            before
        );
    });

    it("I-5: an unresolved request is never followed by a second one", async () => {
        const sandbox = createSandbox();
        sandbox.startTaskActivityPolling();
        sandbox.refreshTaskActivity();
        sandbox.refreshTaskActivity();
        assert.strictEqual(sandbox.apiCalls.length, 1, "未 resolve 时不得并发");

        resolveRequest(latestRequest(sandbox), activeResponse([], 0));
        await flush();
        assert.strictEqual(sandbox.apiCalls.length, 2, "pendingRefresh 只补一轮");
    });

    it("I-5: hidden pages stop requesting and visible resumes exactly once", async () => {
        const sandbox = createSandbox();
        await bootWith(sandbox, activeResponse([activeItem()], 3));

        sandbox.document.hidden = true;
        sandbox.document.dispatch("visibilitychange");
        assert.strictEqual(sandbox.__timers.size, 0, "隐藏后不得留下定时器");

        sandbox.document.hidden = false;
        sandbox.document.dispatch("visibilitychange");
        sandbox.document.dispatch("visibilitychange");
        assert.strictEqual(sandbox.apiCalls.length, 1, "恢复可见只补一轮，未完成时不并发");

        resolveRequest(latestRequest(sandbox), activeResponse([activeItem()], 3));
        await flush();
        assert.strictEqual(sandbox.apiCalls.length, 2);
        assert.strictEqual(sandbox.apiCalls[1].path, "/api/task-executions/active?page=0&size=6");
    });

    it("I-5: a failed request keeps the previous numbers and marks them stale", async () => {
        const sandbox = createSandbox();
        await bootWith(sandbox, activeResponse([activeItem()], 3));
        assert.strictEqual(sandbox.$("#taskActiveNavCount").textContent, "3");

        sandbox.refreshTaskActivity();
        latestRequest(sandbox).reject(new Error("500 Internal Server Error"));
        await flush();

        assert.strictEqual(sandbox.$("#taskActiveCount").textContent, "3", "失败不得把计数置零");
        assert.strictEqual(sandbox.$("#taskActiveGlobalText").textContent, "3 个任务 · 更新失败");
        assert.strictEqual(sandbox.$("#taskActiveGlobalBtn").classList.contains("task-center-stale"), true);
        assert.strictEqual(sandbox.$("#taskActiveNavBadge").classList.contains("task-center-stale"), true);
        assert.match(sandbox.$("#taskActiveNavBadge").getAttribute("aria-label"), /^3 个任务执行中，上次更新失败/);
        assert.strictEqual(sandbox.statusCalls.length, 0, "每轮失败不得 toast");
    });

    it("I-5: the first failure reports an unavailable state instead of an empty list", async () => {
        const sandbox = createSandbox();
        sandbox.startTaskActivityPolling();
        latestRequest(sandbox).reject(new Error("offline"));
        await flush();

        assert.strictEqual(sandbox.$("#taskActiveGlobalText").textContent, "任务状态不可用");
        assert.strictEqual(sandbox.$("#taskActiveGlobalBtn").hidden, false);
        assert.strictEqual(sandbox.$("#taskActiveNavBadge").hidden, true);
        assert.strictEqual(sandbox.$("#taskActiveEmpty").hidden, true, "空态只能由成功且 total=0 产生");
    });

    it("I-5: responses that land after stop or re-login never write the UI", async () => {
        const sandbox = createSandbox();
        sandbox.startTaskActivityPolling();
        const firstRequest = latestRequest(sandbox);

        sandbox.stopTaskActivityPolling();
        resolveRequest(firstRequest, activeResponse([activeItem()], 4));
        await flush();
        assert.strictEqual(sandbox.$("#taskActiveNavCount").textContent, "", "退出后旧响应不得复活计数");
        assert.strictEqual(sandbox.$("#taskActiveGlobalBtn").hidden, true);
        assert.strictEqual(sandbox.__timers.size, 0, "旧 finally 不得重新挂 timer");

        sandbox.startTaskActivityPolling();
        sandbox.stopTaskActivityPolling();
        sandbox.startTaskActivityPolling();
        assert.strictEqual(sandbox.apiCalls.length, 3, "重新登录只有一条观察链");
        resolveRequest(latestRequest(sandbox), activeResponse([activeItem()], 2));
        await flush();
        assert.strictEqual(sandbox.$("#taskActiveNavCount").textContent, "2");
    });

    it("I-5: a stale page response cannot overwrite the new page", async () => {
        const sandbox = createSandbox();
        await bootWith(sandbox, activeResponse([activeItem({ id: 1 })], 13));

        sandbox.refreshTaskActivity();
        const stalePageRequest = latestRequest(sandbox);
        sandbox.$("#taskActiveNextPage").dispatch("click");
        assert.strictEqual(sandbox.__taskActivityState().activePage, 1);

        resolveRequest(stalePageRequest, activeResponse([activeItem({ id: 1 })], 13));
        await flush();
        assert.deepStrictEqual(cardIds(sandbox), ["1"], "旧页响应不得覆盖");
        assert.strictEqual(sandbox.apiCalls[1].path, "/api/task-executions/active?page=1&size=6");

        resolveRequest(latestRequest(sandbox), activeResponse([activeItem({ id: 7 })], 13));
        await flush();
        assert.deepStrictEqual(cardIds(sandbox), ["7"]);
    });
});

// ── I-2: collection semantics ─────────────────────────────────────────────────────

describe("task activity collection semantics", () => {
    it("I-2: two runs of the same type stay two cards and the count is the record total", async () => {
        const sandbox = createSandbox();
        await bootWith(sandbox, activeResponse([
            activeItem({ id: 11, taskType: "AUTO_REPLY_ALL", taskTypeLabel: "全量账号自动收信回复", triggerType: "SCHEDULED" }),
            activeItem({ id: 12, taskType: "AUTO_REPLY_ALL", taskTypeLabel: "全量账号自动收信回复", triggerType: "SCHEDULED" })
        ], 13));

        assert.deepStrictEqual(cardIds(sandbox), ["11", "12"]);
        assert.strictEqual(sandbox.$("#taskActiveNavCount").textContent, "13");
        assert.strictEqual(sandbox.$("#taskActiveGlobalText").textContent, "13 个任务执行中");
        assert.strictEqual(sandbox.$("#taskActiveCount").textContent, "13");
        assert.strictEqual(sandbox.$("#taskActivePageInfo").textContent, "第 1 / 3 页 · 共 13 条");
        assert.strictEqual(sandbox.$("#taskActiveNextPage").disabled, false);
        assert.strictEqual(sandbox.$("#taskActivePrevPage").disabled, true);
    });

    it("I-2: the request carries the page and size for the current view", async () => {
        const sandbox = createSandbox();
        sandbox.startTaskActivityPolling();
        assert.strictEqual(sandbox.apiCalls[0].path, "/api/task-executions/active?page=0&size=6");
        resolveRequest(latestRequest(sandbox), activeResponse([activeItem()], 1));
        await flush();

        sandbox.state.view = "contacts";
        sandbox.refreshTaskActivity();
        assert.strictEqual(sandbox.apiCalls[1].path, "/api/task-executions/active?page=0&size=1",
            "不在任务页时只取 1 条用于全局计数");
        resolveRequest(latestRequest(sandbox), activeResponse([activeItem()], 5));
        await flush();
        assert.strictEqual(sandbox.$("#taskActiveNavCount").textContent, "5");
        assert.deepStrictEqual(cardIds(sandbox), ["12845"], "后台响应不得替换任务页卡片");
    });

    it("I-2: an unknown type keeps its raw code and shows the raw trigger", async () => {
        const sandbox = createSandbox();
        await bootWith(sandbox, activeResponse([
            activeItem({
                id: 900, taskType: "PREVIEW_UNKNOWN_TASK", taskTypeLabel: "PREVIEW_UNKNOWN_TASK",
                triggerType: "QUEUE", metricLabel: null, hasProgressUi: false
            })
        ], 1));

        const card = sandbox.$("#taskActiveCards").querySelector(".task-center-card");
        assert.strictEqual(card.querySelector("h3").textContent, "PREVIEW_UNKNOWN_TASK");
        assert.ok(card.querySelector(".task-center-card-top > .task-center-source").textContent.includes("队列触发"));
        assert.strictEqual(card.querySelector(".task-center-card-footer > .task-center-note").textContent, "— 无统计");
    });

    it("I-2: 99+ keeps the exact count in the aria label", async () => {
        const sandbox = createSandbox();
        await bootWith(sandbox, activeResponse([activeItem()], 124));

        assert.strictEqual(sandbox.$("#taskActiveNavCount").textContent, "99+");
        assert.strictEqual(sandbox.$("#taskActiveNavBadge").getAttribute("aria-label"), "124 个任务执行中，按任务记录统计");
    });

    it("I-2: a success with total=0 hides both entries and shows the empty state", async () => {
        const sandbox = createSandbox();
        await bootWith(sandbox, activeResponse([], 0));

        assert.strictEqual(sandbox.$("#taskActiveNavBadge").hidden, true);
        assert.strictEqual(sandbox.$("#taskActiveGlobalBtn").hidden, true);
        assert.strictEqual(sandbox.$("#taskActiveEmpty").hidden, false);
        assert.strictEqual(sandbox.$("#taskActiveEmpty").textContent, "暂无执行中的任务");
        assert.deepStrictEqual(cardIds(sandbox), []);
    });

    it("I-2: total>0 with an empty page is a transient refresh, not an empty state", async () => {
        const sandbox = createSandbox();
        await bootWith(sandbox, activeResponse([], 13));

        assert.strictEqual(sandbox.$("#taskActiveEmpty").hidden, false);
        assert.strictEqual(sandbox.$("#taskActiveEmpty").textContent, "正在刷新任务列表…");
    });

    it("I-2: a page beyond the new maximum is clamped and re-queried once", async () => {
        const sandbox = createSandbox();
        await bootWith(sandbox, activeResponse([activeItem({ id: 1 })], 13));

        sandbox.__taskActivityState().activePage = 2;
        sandbox.refreshTaskActivity();
        resolveRequest(latestRequest(sandbox), activeResponse([], 7));
        await flush();

        assert.strictEqual(sandbox.__taskActivityState().activePage, 1);
        assert.strictEqual(sandbox.apiCalls[1].path, "/api/task-executions/active?page=1&size=6");
        resolveRequest(latestRequest(sandbox), activeResponse([activeItem({ id: 5 })], 7));
        await flush();
        assert.deepStrictEqual(cardIds(sandbox), ["5"]);
    });

    it("I-2: the active pager never touches the history pager", async () => {
        const sandbox = createSandbox();
        await bootWith(sandbox, activeResponse([activeItem({ id: 1 })], 13));

        sandbox.$("#taskActiveNextPage").dispatch("click");
        resolveRequest(latestRequest(sandbox), activeResponse([activeItem({ id: 2 })], 13));
        await flush();

        assert.strictEqual(sandbox.state.tasksPage, 2, "历史页码不得被活动分页改写");
        assert.strictEqual(sandbox.__taskActivityState().activePage, 1);
        assert.strictEqual(sandbox.loadTasksCalls, 0);
    });
});

// ── I-3: progress must match the same execution ───────────────────────────────────

describe("task activity progress binding", () => {
    it("I-3: a matching execution id renders the live percentage", async () => {
        const sandbox = createSandbox();
        await bootWith(sandbox, activeResponse([
            activeItem({
                id: 12845,
                progress: { status: "RUNNING", processedCount: 126, totalCount: 300, percentage: 42, message: "正在补全专家学术信息" }
            })
        ], 1));

        const card = sandbox.$("#taskActiveCards").querySelector(".task-center-card");
        const bar = card.querySelector("progress.task-center-progress");
        assert.ok(bar, "已知百分比必须渲染原生 progress");
        assert.strictEqual(bar.value, 42);
        assert.strictEqual(card.querySelector(".task-center-message").textContent, "正在补全专家学术信息");
        assert.strictEqual(card.querySelector(".task-center-progress-text > span").textContent, "已处理 126 / 300");
        assert.strictEqual(card.querySelector(".task-center-progress-text > strong").textContent, "42%");
        assert.strictEqual(card.querySelector(".task-center-card-top > .task-center-pill > span").textContent, "运行中");
    });

    it("I-3: no live data means no bar, no invented state and no fabricated counters", async () => {
        const sandbox = createSandbox();
        await bootWith(sandbox, activeResponse([activeItem({ progress: null })], 1));

        const card = sandbox.$("#taskActiveCards").querySelector(".task-center-card");
        assert.strictEqual(card.querySelector("progress.task-center-progress"), null, "未知进度不渲染进度条");
        assert.strictEqual(card.querySelector(".task-center-message").textContent, "记录为执行中，暂无实时进度");
        assert.strictEqual(card.querySelector(".task-center-progress-text > span").textContent, "暂无实时计数");
        assert.strictEqual(card.querySelector(".task-center-progress-text > strong").textContent, "进度未知");
    });

    it("I-3: totalCount<=0 yields an unknown percentage but keeps the processed count", async () => {
        const sandbox = createSandbox();
        await bootWith(sandbox, activeResponse([
            activeItem({ progress: { status: "RUNNING", processedCount: 7, totalCount: 0, percentage: null, message: "" } })
        ], 1));

        const card = sandbox.$("#taskActiveCards").querySelector(".task-center-card");
        assert.strictEqual(card.querySelector("progress.task-center-progress"), null);
        assert.strictEqual(card.querySelector(".task-center-progress-text > span").textContent, "已处理 7");
        assert.strictEqual(card.querySelector(".task-center-progress-text > strong").textContent, "进度未知");
        assert.strictEqual(card.querySelector(".task-center-message").textContent, "正在执行");
    });

    it("I-3: the progress bar disappears when a later tick loses its live data", async () => {
        const sandbox = createSandbox();
        await bootWith(sandbox, activeResponse([
            activeItem({ progress: { status: "RUNNING", processedCount: 126, totalCount: 300, percentage: 42, message: "x" } })
        ], 1));
        assert.ok(sandbox.$("#taskActiveCards").querySelector("progress.task-center-progress"));

        sandbox.refreshTaskActivity();
        resolveRequest(latestRequest(sandbox), activeResponse([activeItem({ progress: null })], 1));
        await flush();

        assert.strictEqual(sandbox.$("#taskActiveCards").querySelector("progress.task-center-progress"), null);
        assert.strictEqual(sandbox.$("#taskActiveCards").querySelector(".task-center-message").textContent,
            "记录为执行中，暂无实时进度");
    });

    it("I-3: a cancelling memory status only adds the label, never rewrites the record status", async () => {
        const sandbox = createSandbox();
        await bootWith(sandbox, activeResponse([
            activeItem({ status: "RUNNING", progress: { status: "CANCELLING", processedCount: 1, totalCount: 10, percentage: 10, message: "取消中" } })
        ], 1));

        const card = sandbox.$("#taskActiveCards").querySelector(".task-center-card");
        assert.strictEqual(card.querySelector(".task-center-card-top > .task-center-pill > span").textContent, "取消中");
        assert.strictEqual(sandbox.__taskActivityState().lastItems[0].status, "RUNNING", "记录状态必须原样保留");
    });

    it("I-3: metric text reuses the catalog label with persisted counts only", async () => {
        const sandbox = createSandbox();
        await bootWith(sandbox, activeResponse([
            activeItem({ metricLabel: "已发送/失败", successCount: 124, failureCount: 2 })
        ], 1));

        assert.strictEqual(
            sandbox.$("#taskActiveCards").querySelector(".task-center-card-footer > .task-center-note").textContent,
            "124/2 已发送/失败"
        );
    });

    it("I-3: elapsed seconds format as mm:ss or hh:mm:ss", () => {
        const sandbox = createSandbox();
        assert.strictEqual(sandbox.formatTaskActivityElapsed(226), "03:46");
        assert.strictEqual(sandbox.formatTaskActivityElapsed(0), "00:00");
        assert.strictEqual(sandbox.formatTaskActivityElapsed(-5), "00:00");
        assert.strictEqual(sandbox.formatTaskActivityElapsed(null), "00:00");
        assert.strictEqual(sandbox.formatTaskActivityElapsed(3725), "1:02:05");
    });
});

// ── I-6 / I-7: detail, control and the history refresh hint ───────────────────────

describe("task activity detail", () => {
    it("I-6: opening B while A is in flight keeps B even when A resolves later", async () => {
        const sandbox = createSandbox();
        await bootWith(sandbox, activeResponse([activeItem({ id: 1 }), activeItem({ id: 2 })], 2));

        sandbox.openTaskActivityDetail(1);
        const requestA = latestRequest(sandbox);
        sandbox.openTaskActivityDetail(2);
        const requestB = latestRequest(sandbox);
        assert.strictEqual(requestA.path, "/api/task-executions/1/detail");
        assert.strictEqual(requestB.path, "/api/task-executions/2/detail");

        resolveRequest(requestB, {
            id: 2, taskType: "EXPERT_ENRICHMENT", taskTypeLabel: "学术数据补全", status: "SUCCESS",
            startedAt: "2026-09-22 14:00:00", durationSeconds: 65, rawRequestPayload: "{\"a\":1}", rawResultSummary: null, rawTruncated: false
        });
        await flush();
        resolveRequest(requestA, {
            id: 1, taskType: "EXPERT_ENRICHMENT", taskTypeLabel: "学术数据补全", status: "FAILED",
            startedAt: "2026-09-22 13:00:00", durationSeconds: 10, rawRequestPayload: null, rawResultSummary: "boom", rawTruncated: false
        });
        await flush();

        assert.strictEqual(sandbox.$("#taskActiveDetailTitle").textContent, "学术数据补全 · #2");
        assert.ok(sandbox.$("#taskActiveDetailStatus").textContent.includes("耗时：01:05"));
        assert.ok(sandbox.$("#taskActiveDetailBody").textContent.includes('{"a":1}'));
        assert.ok(!sandbox.$("#taskActiveDetailBody").textContent.includes("boom"), "A 的迟到响应不得写回");
    });

    it("I-6: a same-type replacement never re-targets the open detail", async () => {
        const sandbox = createSandbox();
        await bootWith(sandbox, activeResponse([activeItem({ id: 1 })], 1));
        sandbox.openTaskActivityDetail(1);
        resolveRequest(latestRequest(sandbox), {
            id: 1, taskType: "EXPERT_ENRICHMENT", taskTypeLabel: "学术数据补全", status: "RUNNING",
            startedAt: "2026-09-22 14:00:00", durationSeconds: null, rawRequestPayload: null, rawResultSummary: null, rawTruncated: false
        });
        await flush();

        sandbox.refreshTaskActivity();
        resolveRequest(latestRequest(sandbox), activeResponse([activeItem({ id: 777 })], 1));
        await flush();
        resolveRequest(latestRequest(sandbox), {
            id: 1, taskType: "EXPERT_ENRICHMENT", taskTypeLabel: "学术数据补全", status: "RUNNING",
            startedAt: "2026-09-22 14:00:00", durationSeconds: null, rawRequestPayload: null, rawResultSummary: null, rawTruncated: false
        });
        await flush();

        assert.strictEqual(sandbox.__taskActivityState().detailId, 1, "详情必须仍是所选执行");
        assert.strictEqual(sandbox.$("#taskActiveDetailTitle").textContent, "学术数据补全 · #1");
        assert.ok(sandbox.$("#taskActiveDetailStatus").textContent.includes("执行中"),
            "卡片已离开本页时只显示 beganAt 与执行中，不猜进度");
    });

    it("I-6: closing or stopping does not let a late response revive the panel", async () => {
        const sandbox = createSandbox();
        await bootWith(sandbox, activeResponse([activeItem({ id: 1 })], 1));

        sandbox.openTaskActivityDetail(1);
        const request = latestRequest(sandbox);
        sandbox.$("#taskActiveDetailClose").dispatch("click");
        resolveRequest(request, {
            id: 1, taskType: "EXPERT_ENRICHMENT", taskTypeLabel: "学术数据补全", status: "SUCCESS",
            startedAt: "2026-09-22 14:00:00", durationSeconds: 5, rawRequestPayload: null, rawResultSummary: "late", rawTruncated: false
        });
        await flush();

        assert.strictEqual(sandbox.$("#taskActiveDetail").hidden, true);
        assert.strictEqual(sandbox.$("#taskActiveDetailBody").textContent, "");
        assert.strictEqual(sandbox.__taskActivityState().detailId, null);
    });

    it("S-3: closing restores focus to the card button that opened it", async () => {
        const sandbox = createSandbox();
        await bootWith(sandbox, activeResponse([activeItem({ id: 1 })], 1));

        sandbox.openTaskActivityDetail(1);
        const button = sandbox.$("#taskActiveCards").querySelector('button[data-task-active-detail="1"]');
        assert.strictEqual(button.getAttribute("aria-expanded"), "true");

        sandbox.$("#taskActiveDetailClose").dispatch("click");
        assert.strictEqual(button.focused, true, "收起后焦点归还原详情按钮");
        assert.strictEqual(button.getAttribute("aria-expanded"), "false");
    });

    it("I-6: a 404 keeps an explicit empty state and stops retrying", async () => {
        const sandbox = createSandbox();
        await bootWith(sandbox, activeResponse([activeItem({ id: 1 })], 1));

        sandbox.openTaskActivityDetail(1);
        const notFound = new Error("Task execution not found: 1");
        notFound.status = 404;
        latestRequest(sandbox).reject(notFound);
        await flush();

        assert.strictEqual(sandbox.$("#taskActiveDetailStatus").textContent, "执行记录不存在或已被清理");
        assert.strictEqual(sandbox.__taskActivityState().detailId, 1, "不跳其他执行");

        sandbox.refreshTaskActivity();
        resolveRequest(latestRequest(sandbox), activeResponse([activeItem({ id: 1 })], 1));
        await flush();
        assert.strictEqual(sandbox.apiCalls.filter((call) => call.path.endsWith("/detail")).length, 1,
            "404 后不再重试详情");
    });

    it("I-7: a terminal detail is kept on screen and stops the detail polling", async () => {
        const sandbox = createSandbox();
        await bootWith(sandbox, activeResponse([activeItem({ id: 1 })], 1));

        sandbox.openTaskActivityDetail(1);
        resolveRequest(latestRequest(sandbox), {
            id: 1, taskType: "EXPERT_ENRICHMENT", taskTypeLabel: "学术数据补全", status: "FAILED",
            startedAt: "2026-09-22 14:00:00", durationSeconds: 12, rawRequestPayload: null, rawResultSummary: null, rawTruncated: false
        });
        await flush();

        sandbox.refreshTaskActivity();
        resolveRequest(latestRequest(sandbox), activeResponse([], 0));
        await flush();

        assert.strictEqual(sandbox.apiCalls.filter((call) => call.path.endsWith("/detail")).length, 1,
            "终态详情不再周期请求");
        assert.strictEqual(sandbox.$("#taskActiveDetail").hidden, false, "终态内容保留");
        assert.ok(sandbox.$("#taskActiveDetailStatus").textContent.includes("失败"));
        assert.ok(sandbox.$("#taskActiveDetailStatus").textContent.includes("错误原因可在下方执行记录查看"));
    });

    it("I-6: the control entry is gated on taskButtonMapping and re-checks the identity", async () => {
        const sandbox = createSandbox();
        await bootWith(sandbox, activeResponse([activeItem({ id: 1, taskType: "EXPERT_ENRICHMENT" })], 1));

        sandbox.openTaskActivityDetail(1);
        resolveRequest(latestRequest(sandbox), {
            id: 1, taskType: "EXPERT_ENRICHMENT", taskTypeLabel: "学术数据补全", status: "RUNNING",
            startedAt: "2026-09-22 14:00:00", durationSeconds: null, rawRequestPayload: null, rawResultSummary: null, rawTruncated: false
        });
        await flush();
        assert.strictEqual(sandbox.$("#taskActiveOpenControl").hidden, false);

        // 同类型已换成另一次执行：不得打开控制
        sandbox.$("#taskActiveOpenControl").dispatch("click");
        resolveRequest(latestRequest(sandbox), { taskType: "EXPERT_ENRICHMENT", status: "RUNNING", executionId: 777 });
        await flush();
        assert.deepStrictEqual(sandbox.openTaskModalCalls, [], "id 不一致不得打开控制");
        assert.strictEqual(sandbox.$("#taskActiveDetailStatus").textContent,
            "该执行已结束或已被新执行替代，请刷新记录");

        // 匹配：只打开一次既有控制弹框
        sandbox.$("#taskActiveOpenControl").dispatch("click");
        resolveRequest(latestRequest(sandbox), { taskType: "EXPERT_ENRICHMENT", status: "RUNNING", executionId: 1 });
        await flush();
        assert.strictEqual(sandbox.openTaskModalCalls.length, 1);
        assert.strictEqual(sandbox.openTaskModalCalls[0][0], "EXPERT_ENRICHMENT");
        assert.strictEqual(sandbox.openTaskModalCalls[0][2], "discoverBtn");
        assert.strictEqual(sandbox.openTaskModalCalls[0][3].knownActiveAtOpen, true);
    });

    it("I-6: the control entry is hidden for types without a launch button", async () => {
        const sandbox = createSandbox();
        await bootWith(sandbox, activeResponse([
            activeItem({ id: 5, taskType: "AUTO_REPLY_ALL", taskTypeLabel: "全量账号自动收信回复", hasProgressUi: false })
        ], 1));

        sandbox.openTaskActivityDetail(5);
        resolveRequest(latestRequest(sandbox), {
            id: 5, taskType: "AUTO_REPLY_ALL", taskTypeLabel: "全量账号自动收信回复", status: "RUNNING",
            startedAt: "2026-09-22 14:00:00", durationSeconds: null, rawRequestPayload: null, rawResultSummary: null, rawTruncated: false
        });
        await flush();

        assert.strictEqual(sandbox.$("#taskActiveOpenControl").hidden, true, "无启动按钮的类型不显示控制入口");
        assert.strictEqual(sandbox.$("#taskActiveLoadLogs").hidden, true, "hasProgressUi=false 不显示日志入口");
    });

    it("I-6: batch logs are loaded on demand for the selected execution only", async () => {
        const sandbox = createSandbox();
        await bootWith(sandbox, activeResponse([activeItem({ id: 42 })], 1));

        sandbox.openTaskActivityDetail(42);
        resolveRequest(latestRequest(sandbox), {
            id: 42, taskType: "EXPERT_ENRICHMENT", taskTypeLabel: "学术数据补全", status: "RUNNING",
            startedAt: "2026-09-22 14:00:00", durationSeconds: null, rawRequestPayload: null, rawResultSummary: null, rawTruncated: false
        });
        await flush();
        assert.strictEqual(sandbox.$("#taskActiveLoadLogs").hidden, false);

        sandbox.refreshTaskActivity();
        resolveRequest(latestRequest(sandbox), activeResponse([activeItem({ id: 42 })], 1));
        await flush();
        assert.strictEqual(sandbox.apiCalls.filter((call) => call.path.includes("/logs")).length, 0,
            "日志不得随每 5 秒刷新拉取");

        sandbox.$("#taskActiveLoadLogs").dispatch("click");
        const logRequest = sandbox.pendingRequests[sandbox.pendingRequests.length - 1];
        assert.strictEqual(logRequest.path,
            "/api/task-progress/EXPERT_ENRICHMENT/logs?executionId=42&batchOnly=true");
        resolveRequest(logRequest, [
            { id: 1, batchNumber: 1, status: "RUNNING", message: "第一批", createdAt: "2026-09-22T14:00:00.000" },
            { id: 2, batchNumber: 0, status: "RUNNING", message: null, createdAt: "2026-09-22T14:01:00.000" }
        ]);
        await flush();

        const logs = sandbox.$("#taskActiveLogs");
        assert.strictEqual(logs.hidden, false);
        assert.ok(logs.textContent.includes("2026-09-22 14:00:00"));
        assert.ok(logs.textContent.includes("批次 1"));
        assert.ok(logs.textContent.includes("批次 -"));
    });

    it("I-6: more than 50 batch logs are capped and labelled", async () => {
        const sandbox = createSandbox();
        await bootWith(sandbox, activeResponse([activeItem({ id: 42 })], 1));
        const rows = Array.from({ length: 55 }, (_, index) => ({
            id: index + 1, batchNumber: index + 1, status: "RUNNING", message: `m${index + 1}`,
            createdAt: "2026-09-22T14:00:00.000"
        }));

        sandbox.openTaskActivityDetail(42);
        resolveRequest(latestRequest(sandbox), {
            id: 42, taskType: "EXPERT_ENRICHMENT", taskTypeLabel: "学术数据补全", status: "RUNNING",
            startedAt: "2026-09-22 14:00:00", durationSeconds: null, rawRequestPayload: null, rawResultSummary: null, rawTruncated: false
        });
        await flush();
        sandbox.$("#taskActiveLoadLogs").dispatch("click");
        resolveRequest(sandbox.pendingRequests[sandbox.pendingRequests.length - 1], rows);
        await flush();

        const lines = sandbox.$("#taskActiveLogs").textContent.split("\n");
        assert.strictEqual(lines.length, 51, "最多 50 条 + 1 行说明");
        assert.ok(lines[0].includes("批次 6"), "只展示最后 50 条");
        assert.strictEqual(lines[50], "（仅展示最近 50 条批次日志）");
    });

    it("I-6: an empty batch log list is reported explicitly", async () => {
        const sandbox = createSandbox();
        await bootWith(sandbox, activeResponse([activeItem({ id: 42 })], 1));
        sandbox.openTaskActivityDetail(42);
        resolveRequest(latestRequest(sandbox), {
            id: 42, taskType: "EXPERT_ENRICHMENT", taskTypeLabel: "学术数据补全", status: "RUNNING",
            startedAt: "2026-09-22 14:00:00", durationSeconds: null, rawRequestPayload: null, rawResultSummary: null, rawTruncated: false
        });
        await flush();
        sandbox.$("#taskActiveLoadLogs").dispatch("click");
        resolveRequest(sandbox.pendingRequests[sandbox.pendingRequests.length - 1], []);
        await flush();

        assert.strictEqual(sandbox.$("#taskActiveLogs").textContent, "该执行暂无批次日志");
    });

    it("I-7: membership changes only surface the refresh hint, and it never notifies", async () => {
        const sandbox = createSandbox();
        await bootWith(sandbox, activeResponse([activeItem({ id: 1 })], 3));
        assert.strictEqual(sandbox.$("#taskHistoryRefreshHint").hidden, true, "首次快照不提示");

        sandbox.refreshTaskActivity();
        resolveRequest(latestRequest(sandbox), activeResponse([activeItem({ id: 1 })], 2));
        await flush();
        assert.strictEqual(sandbox.$("#taskHistoryRefreshHint").hidden, false);
        assert.strictEqual(sandbox.statusCalls.length, 0, "不得发完成/失败通知");
        assert.strictEqual(sandbox.openTaskModalCalls.length, 0);

        sandbox.$("#taskHistoryRefreshHint").dispatch("click");
        assert.strictEqual(sandbox.$("#taskHistoryRefreshHint").hidden, true, "点击后清除提示");
        assert.strictEqual(sandbox.loadTasksCalls, 1);
        assert.strictEqual(sandbox.state.tasksPage, 2, "按当前筛选与页码刷新");
    });

    it("I-7: a status flip on the same page also surfaces the hint", async () => {
        const sandbox = createSandbox();
        await bootWith(sandbox, activeResponse([activeItem({ id: 1, status: "RUNNING" })], 1));

        sandbox.refreshTaskActivity();
        resolveRequest(latestRequest(sandbox), activeResponse([activeItem({ id: 1, status: "CANCELLING" })], 1));
        await flush();

        assert.strictEqual(sandbox.$("#taskHistoryRefreshHint").hidden, false);
    });
});

// ── I-8: injection safety ─────────────────────────────────────────────────────────

describe("task activity injection safety", () => {
    const hostile = '<img src=x onerror="alert(1)">"\' \n<script>alert(2)</script>';

    it("I-8: server text is escaped on creation and written as text on update", async () => {
        const sandbox = createSandbox();
        await bootWith(sandbox, activeResponse([
            activeItem({ id: 3, taskType: hostile, taskTypeLabel: hostile, progress: null })
        ], 1));

        const card = sandbox.$("#taskActiveCards").querySelector(".task-center-card");
        assert.strictEqual(card.querySelector("h3").textContent, hostile, "标题必须逐字为纯文本");
        assert.strictEqual(card.querySelectorAll("img").length, 0, "不得生成服务端提供的标签");
        assert.strictEqual(card.querySelectorAll("script").length, 0);
        const html = sandbox.taskActivityCardHtml(activeItem({ id: 3, taskTypeLabel: hostile }), 3);
        assert.ok(!html.includes("<img"), "生成的卡片 HTML 不得含未转义标签");
        assert.ok(html.includes("&lt;img"), "生成的卡片 HTML 必须转义");

        sandbox.refreshTaskActivity();
        resolveRequest(latestRequest(sandbox), activeResponse([
            activeItem({ id: 3, taskType: hostile, taskTypeLabel: `${hostile}!`, progress: null })
        ], 1));
        await flush();
        assert.strictEqual(card.querySelector("h3").textContent, `${hostile}!`);
    });

    it("I-8: a non-numeric execution id is ignored instead of being addressed", async () => {
        const sandbox = createSandbox();
        await bootWith(sandbox, activeResponse([
            activeItem({ id: "abc" }), activeItem({ id: -4 }), activeItem({ id: 0 }), activeItem({ id: 8 })
        ], 4));

        assert.deepStrictEqual(cardIds(sandbox), ["8"]);
        sandbox.openTaskActivityDetail("abc");
        assert.strictEqual(sandbox.__taskActivityState().detailId, null);
        sandbox.openTaskActivityDetail(-1);
        assert.strictEqual(sandbox.__taskActivityState().detailId, null);
    });

    it("I-8: out-of-range percentages are clamped before they reach the DOM", () => {
        const sandbox = createSandbox();
        assert.strictEqual(sandbox.taskActivitySafePercentage(140), 100);
        assert.strictEqual(sandbox.taskActivitySafePercentage(-3), 0);
        assert.strictEqual(sandbox.taskActivitySafePercentage("42"), 42);
        assert.strictEqual(sandbox.taskActivitySafePercentage("nope"), null);
        assert.strictEqual(sandbox.taskActivitySafePercentage(null), null);
    });
});
