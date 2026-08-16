const fs = require("fs");
const path = require("path");
const vm = require("vm");
const assert = require("assert");
const { describe, it } = require("node:test");

const root = path.join(__dirname, "..", "..", "main", "resources");
const html = fs.readFileSync(path.join(root, "static", "index.html"), "utf-8");
const app = fs.readFileSync(path.join(root, "static", "app.js"), "utf-8");

function extractFn(name) {
    const regex = new RegExp("(?:async\\s+)?function\\s+" + name + "\\s*\\([^)]*\\)\\s*\\{[\\s\\S]*?\\n\\}");
    const match = app.match(regex);
    if (!match) throw new Error("Could not find " + name + " in app.js");
    return match[0];
}

/** 明细渲染沙箱：预载 escapeHtml / renderTaskDetailRawBlocks / toggleTaskDetail。 */
function createDetailSandbox({ apiImpl }) {
    const sandbox = {
        api: async (url) => apiImpl(url),
        document: {
            createElement: (tag) => ({ tagName: tag, className: "", innerHTML: "", dataset: {} })
        }
    };
    vm.createContext(sandbox);
    vm.runInContext(extractFn("escapeHtml"), sandbox);
    vm.runInContext(extractFn("renderTaskDetailRawBlocks"), sandbox);
    vm.runInContext(extractFn("toggleTaskDetail"), sandbox);
    return sandbox;
}

async function renderDetailRow(detail) {
    const sandbox = createDetailSandbox({
        apiImpl: async () => detail
    });
    let detailRow = null;
    const row = {
        dataset: { taskId: String(detail.id), taskType: detail.taskType || "MANUAL_INITIAL_OUTREACH" },
        nextElementSibling: null,
        after: (el) => { detailRow = el; }
    };
    await sandbox.toggleTaskDetail(row);
    return detailRow.innerHTML;
}

/** 收发件箱沙箱：预载 loadMailbox 及其依赖（过滤条由 loadMailbox 内联渲染）。 */
function createMailboxSandbox({ apiImpl, mailboxOverrides }) {
    const elements = {
        startDate: { value: "", disabled: false, classList: { toggle: () => {} } },
        endDate: { value: "", disabled: false, classList: { toggle: () => {} } },
        accountCode: { value: "" },
        recipient: { value: "" },
        keyword: { value: "" },
        direction: { value: "" },
        tagFilter: { value: "" },
        filterBar: { hidden: true },
        filterText: { textContent: "" }
    };
    const state = {
        mailbox: {
            items: [], groups: [], viewMode: "MAIL", page: 0, totalCount: 0, pageSize: 20,
            accountsLoaded: true, dateDefaultsApplied: true, onlyPending: false, tagFilter: "",
            detailContext: null, taskExecutionId: null, taskExecutionLabel: null,
            ...mailboxOverrides
        }
    };
    const sandbox = {
        state,
        URLSearchParams: class {
            constructor() {
                this.map = new Map();
            }
            set(k, v) {
                this.map.set(k, v);
            }
            toString() {
                return Array.from(this.map.entries())
                    .map(([k, v]) => `${k}=${encodeURIComponent(v)}`)
                    .join("&");
            }
        },
        api: async (url) => apiImpl(url),
        document: { querySelector: () => null },
        $: (sel) => ({
            "#mailboxFilterStartDate": elements.startDate,
            "#mailboxFilterEndDate": elements.endDate,
            "#mailboxFilterAccountCode": elements.accountCode,
            "#mailboxFilterRecipient": elements.recipient,
            "#mailboxFilterKeyword": elements.keyword,
            "#mailboxFilterDirection": elements.direction,
            "#mailboxFilterTag": elements.tagFilter,
            "#mailboxExecutionFilterBar": elements.filterBar,
            "#mailboxExecutionFilterText": elements.filterText
        })[sel]
    };
    vm.createContext(sandbox);
    vm.runInContext(extractFn("mailboxViewMode"), sandbox);
    vm.runInContext(extractFn("mailboxPendingOnly"), sandbox);
    vm.runInContext(extractFn("syncMailboxViewModeControls"), sandbox);
    vm.runInContext("async function loadMailboxAccounts() {}", sandbox);
    vm.runInContext("function renderMailboxTable() {}", sandbox);
    vm.runInContext("function renderMailboxExpertGroups() {}", sandbox);
    vm.runInContext("function renderMailboxPagination() {}", sandbox);
    vm.runInContext("async function refreshUnmatchedBadge() {}", sandbox);
    vm.runInContext(extractFn("loadMailbox"), sandbox);
    return { sandbox, elements };
}

describe("task drilldown (b4)", () => {
    it("I2b-1: drilldownState NONE renders the verbatim disabled span without data-action/href/button", async () => {
        const out = await renderDetailRow({ id: 13023, taskType: "EXPERT_ENRICHMENT", drilldownState: "NONE" });
        assert.ok(out.includes('<span class="text-muted">该任务无个体明细</span>'),
            "NONE must render the verbatim disabled text (S2b-2)");
        assert.ok(!out.includes("data-action"), "NONE must not render a clickable action");
        assert.ok(!out.includes("href"), "NONE must not render a link");
        assert.ok(!out.includes("<button"), "NONE must not render a button (M-4)");
    });

    it("I2b-2: PRE_FEATURE renders its distinct disabled text", async () => {
        const out = await renderDetailRow({ id: 100, taskType: "MANUAL_INITIAL_OUTREACH", drilldownState: "PRE_FEATURE" });
        assert.ok(out.includes('<span class="text-muted">该执行早于本功能上线，无法关联</span>'),
            "PRE_FEATURE must keep its own wording");
        assert.ok(!out.includes("data-action"), "PRE_FEATURE must not be clickable");
    });

    it("I2b-2: QUEUE_DISPATCHED renders its distinct disabled text", async () => {
        const out = await renderDetailRow({ id: 101, taskType: "INITIAL_OUTREACH", drilldownState: "QUEUE_DISPATCHED" });
        assert.ok(out.includes('<span class="text-muted">该执行经队列派发，邮件未直接关联</span>'),
            "QUEUE_DISPATCHED must keep its own wording");
        assert.ok(!out.includes("<button"), "QUEUE_DISPATCHED must not render a button");
    });

    it("I2b-2: the three disabled wordings are distinct from each other", async () => {
        const none = await renderDetailRow({ id: 1, drilldownState: "NONE" });
        const pre = await renderDetailRow({ id: 1, drilldownState: "PRE_FEATURE" });
        const queue = await renderDetailRow({ id: 1, drilldownState: "QUEUE_DISPATCHED" });
        const set = new Set([none.match(/<span class="text-muted">[^<]+<\/span>/)[0],
            pre.match(/<span class="text-muted">[^<]+<\/span>/)[0],
            queue.match(/<span class="text-muted">[^<]+<\/span>/)[0]]);
        assert.strictEqual(set.size, 3, "three disabled causes must be distinguishable (I2b-2)");
    });

    it("S2b-1: AVAILABLE MAIL_BY_EXECUTION renders the verbatim link-btn button", async () => {
        const out = await renderDetailRow({
            id: 13023, taskType: "MANUAL_INITIAL_OUTREACH",
            drilldownState: "AVAILABLE", drilldown: "MAIL_BY_EXECUTION", drilldownCount: 10
        });
        assert.ok(out.includes(
            '<button type="button" class="link-btn" data-action="task-drilldown-mail" data-execution-id="13023">查看本次发出的邮件（10 封）</button>'),
            "AVAILABLE mail entry must match the S2b-1 skeleton verbatim");
    });

    it("S2b-1: AVAILABLE EXPERT_BY_POLL_DETAIL renders one clickable button per expert", async () => {
        const out = await renderDetailRow({
            id: 5, taskType: "AUTO_REPLY_ALL",
            drilldownState: "AVAILABLE", drilldown: "EXPERT_BY_POLL_DETAIL", drilldownCount: 1,
            experts: [{ expertContactId: 4471, expertEmail: "a@b.edu", expertName: "王某某", outcome: "REPLIED" }]
        });
        assert.ok(out.includes(
            '<button type="button" class="link-btn" data-action="task-drilldown-contact" data-contact-id="4471">王某某 &lt;a@b.edu&gt;</button>'),
            "expert entry must render name+email with escaped angle brackets");
    });

    it("T2b-5: mail drilldown click sets taskExecutionId, label and resets page", async () => {
        const sandbox = {
            state: {
                mailbox: { taskExecutionId: null, taskExecutionLabel: null, page: 5, onlyPending: false },
                taskTypeOptions: [{ code: "MANUAL_INITIAL_OUTREACH", label: "批量首发邮件", count: 1 }]
            },
            setView: () => {},
            loadMailbox: async () => {},
            showStatus: () => {}
        };
        vm.createContext(sandbox);
        vm.runInContext(extractFn("handleTaskDrilldownMail"), sandbox);
        sandbox.handleTaskDrilldownMail({
            dataset: { executionId: "13023" },
            closest: () => ({
                dataset: {},
                previousElementSibling: { dataset: { taskType: "MANUAL_INITIAL_OUTREACH" } }
            })
        });
        assert.strictEqual(sandbox.state.mailbox.taskExecutionId, 13023,
            "click must set state.mailbox.taskExecutionId");
        assert.strictEqual(sandbox.state.mailbox.page, 0,
            "click must reset mailbox page to 0");
        assert.strictEqual(sandbox.state.mailbox.taskExecutionLabel, "批量首发邮件",
            "click must carry the task type label for the hint bar");
    });

    it("T2b-5: contact drilldown handler reuses openContactInList", () => {
        const sandbox = { openContactInList: (id) => ({ catch: () => {} }), showStatus: () => {} };
        vm.createContext(sandbox);
        vm.runInContext(extractFn("handleTaskDrilldownContact"), sandbox);
        sandbox.handleTaskDrilldownContact({ dataset: { contactId: "4471" } });
    });

    it("N2b-2: setMailboxPendingOnly(true) clears the batch filter state", () => {
        const sandbox = {
            state: { mailbox: { onlyPending: false, taskExecutionId: 13023, taskExecutionLabel: "批量首发邮件" } },
            document: { querySelector: () => ({ checked: false }) }
        };
        vm.createContext(sandbox);
        vm.runInContext(extractFn("setMailboxPendingOnly"), sandbox);
        sandbox.setMailboxPendingOnly(true);
        assert.strictEqual(sandbox.state.mailbox.onlyPending, true);
        assert.strictEqual(sandbox.state.mailbox.taskExecutionId, null,
            "pending entries must clear the execution filter (N2b-2)");
        assert.strictEqual(sandbox.state.mailbox.taskExecutionLabel, null);
    });

    it("S2b-3: hint bar shows the normal form with a known label", async () => {
        const { sandbox, elements } = createMailboxSandbox({
            apiImpl: async () => ({ items: [], totalCount: 0 }),
            mailboxOverrides: { taskExecutionId: 13023, taskExecutionLabel: "批量首发邮件" }
        });
        await sandbox.loadMailbox();
        assert.strictEqual(elements.filterText.textContent,
            "正在查看：批量首发邮件 执行 #13023 发出的邮件");
        assert.strictEqual(elements.filterBar.hidden, false);
    });

    it("S2b-3: hint bar shows the retention form without a label (I2b-4)", async () => {
        const { sandbox, elements } = createMailboxSandbox({
            apiImpl: async () => ({ items: [], totalCount: 0 }),
            mailboxOverrides: { taskExecutionId: 13023, taskExecutionLabel: null }
        });
        await sandbox.loadMailbox();
        assert.strictEqual(elements.filterText.textContent,
            "正在查看：执行 #13023（记录已过保留期）发出的邮件");
        assert.strictEqual(elements.filterBar.hidden, false);
    });

    it("S2b-3: hint bar stays hidden and empty without a filter (N2b-1)", async () => {
        const { sandbox, elements } = createMailboxSandbox({
            apiImpl: async () => ({ items: [], totalCount: 0 })
        });
        await sandbox.loadMailbox();
        assert.strictEqual(elements.filterBar.hidden, true);
        assert.strictEqual(elements.filterText.textContent, "");
    });

    it("S2b-3: filtered load sends taskExecutionId in the query", async () => {
        let capturedUrl = null;
        const { sandbox } = createMailboxSandbox({
            apiImpl: async (url) => { capturedUrl = url; return { items: [], totalCount: 0 }; },
            mailboxOverrides: { taskExecutionId: 13023, taskExecutionLabel: "批量首发邮件" }
        });
        await sandbox.loadMailbox();
        assert.ok(capturedUrl.includes("taskExecutionId=13023"),
            "filtered load must add taskExecutionId to the query");
    });

    it("S2b-3: index.html carries the hint bar skeleton after the existing toolbar", () => {
        const barIndex = html.indexOf('id="mailboxExecutionFilterBar"');
        assert.ok(barIndex >= 0, "hint bar must exist in the mailbox view");
        assert.ok(html.slice(barIndex).startsWith('id="mailboxExecutionFilterBar" class="toolbar" hidden>'),
            "hint bar must keep the verbatim S2b-3 skeleton");
        assert.ok(html.includes('<span class="text-muted" id="mailboxExecutionFilterText"></span>'),
            "filter text element must be verbatim");
        assert.ok(html.includes('<button type="button" class="button small" id="mailboxExecutionFilterClear">清除过滤</button>'),
            "clear button must be verbatim");
        const searchBtnIndex = html.indexOf('id="mailboxSearchBtn"');
        const bulkIndex = html.indexOf('id="bulkOutreachBtn"');
        assert.ok(barIndex > searchBtnIndex,
            "hint bar must come after the existing .toolbar (A2b-10)");
        assert.ok(barIndex < bulkIndex,
            "hint bar must not share the bulkOutreachBtn row");
    });
});
