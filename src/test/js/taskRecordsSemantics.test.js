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

function extractConst(name) {
    const regex = new RegExp("const\\s+" + name + "\\s*=\\s*\\{[\\s\\S]*?\\n\\};");
    const match = app.match(regex);
    if (!match) throw new Error("Could not find const " + name + " in app.js");
    return match[0];
}

function createSandbox({ apiImpl, stateOverrides, elementsOverrides }) {
    const elements = {
        pager: { hidden: false },
        prev: { disabled: false },
        next: { disabled: false },
        info: { textContent: "" },
        table: { innerHTML: "" },
        typeFilter: { value: "", innerHTML: "" },
        statusFilter: { value: "" },
        ...elementsOverrides
    };
    const state = {
        tasksPage: 0,
        tasksTotal: 0,
        taskTypeOptions: [],
        ...stateOverrides
    };
    const sandbox = {
        TASK_PAGE_SIZE: 50,
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
        document: {
            createElement: (tag) => ({ tagName: tag, className: "", innerHTML: "", dataset: {} })
        },
        $: (sel) => {
            const map = {
                "#taskPager": elements.pager,
                "#taskPrevPage": elements.prev,
                "#taskNextPage": elements.next,
                "#taskPageInfo": elements.info,
                "#tasksTable": elements.table,
                "#taskTypeFilter": elements.typeFilter,
                "#taskStatusFilter": elements.statusFilter
            };
            return map[sel];
        }
    };
    vm.createContext(sandbox);
    vm.runInContext(extractConst("statusLabels"), sandbox);
    for (const name of [
        "escapeHtml", "badge", "labelStatus", "renderTaskPager",
        "loadTaskTypeOptions", "loadTasks", "renderTaskDetailRawBlocks", "toggleTaskDetail",
        "normalizeDiscoveryResultSummary", "renderDiscoverySummaryText", "renderBySourceTable",
        "isEnrichmentBySource", "renderEnrichmentSourceTable", "normalizeEnrichmentResultSummary"
    ]) {
        vm.runInContext(extractFn(name), sandbox);
    }
    return { sandbox, elements };
}

function taskItem(overrides) {
    return {
        id: 42,
        taskType: "AUTO_REPLY_ALL",
        taskTypeLabel: "全量账号自动收信回复",
        triggerType: "QUEUE",
        status: "SUCCESS",
        successCount: 4,
        failureCount: 0,
        metricLabel: null,
        startedAt: "2026-08-16T10:00:00",
        errorMessage: null,
        ...overrides
    };
}

describe("task records semantics (b2)", () => {
    it("I1-2: metricLabel null renders 无统计 and never 0/0", async () => {
        const { sandbox, elements } = createSandbox({
            apiImpl: async () => ({ items: [taskItem({ metricLabel: null })], total: 1 })
        });
        await sandbox.loadTasks();
        const htmlOut = elements.table.innerHTML;
        assert.ok(htmlOut.includes('<td><span class="text-muted">— 无统计</span></td>'),
            "no-metricLabel row must render the verbatim 无统计 cell");
        assert.ok(!htmlOut.includes("0/0"), "no-metricLabel row must NOT render 0/0");
    });

    it("S1-3: metricLabel present renders count plus muted semantic label in one cell", async () => {
        const { sandbox, elements } = createSandbox({
            apiImpl: async () => ({
                items: [taskItem({ successCount: 12, failureCount: 1, metricLabel: "补全成功/失败" })],
                total: 1
            })
        });
        await sandbox.loadTasks();
        assert.ok(elements.table.innerHTML.includes(
            '<td>12/1 <span class="text-muted">补全成功/失败</span></td>'),
            "metricLabel row must render S1-3 verbatim cell");
    });

    it("I1-1: column 2 renders taskTypeLabel with raw code fallback", async () => {
        const { sandbox, elements } = createSandbox({
            apiImpl: async () => ({
                items: [taskItem({ taskTypeLabel: "学术数据补全" })],
                total: 1
            })
        });
        await sandbox.loadTasks();
        assert.ok(elements.table.innerHTML.includes("<td>学术数据补全</td>"),
            "column 2 must render the catalog label");
    });

    it("S1-4: detail without renderer renders the two verbatim pre blocks", async () => {
        let detailRow = null;
        const { sandbox } = createSandbox({
            apiImpl: async (url) => {
                if (url === "/api/task-executions/42/detail") {
                    return {
                        rawRequestPayload: '{"total":10,"sent":3,"failed":2}',
                        rawResultSummary: '{"enriched":12,"failed":1}',
                        rawTruncated: false
                    };
                }
                return null;
            }
        });
        const row = {
            dataset: { taskId: "42", taskType: "AUTO_REPLY_ALL" },
            nextElementSibling: null,
            after: (el) => { detailRow = el; }
        };
        await sandbox.toggleTaskDetail(row);
        assert.ok(detailRow, "detail row must be inserted");
        const expected = '<td colspan="7" style="padding:12px 16px;background:var(--surface);">'
            + '<div class="text-muted">请求参数</div>'
            + `<div class="pre">${sandbox.escapeHtml('{"total":10,"sent":3,"failed":2}')}</div>`
            + '<div class="text-muted">执行结果</div>'
            + `<div class="pre">${sandbox.escapeHtml('{"enriched":12,"failed":1}')}</div>`
            + "</td>";
        assert.strictEqual(detailRow.innerHTML, expected);
    });

    it("I1-6: rawTruncated appends the truncation notice after the pre blocks", async () => {
        let detailRow = null;
        const { sandbox } = createSandbox({
            apiImpl: async (url) => {
                if (url === "/api/task-executions/42/detail") {
                    return {
                        rawRequestPayload: '{"a":1}',
                        rawResultSummary: '{"b":2}',
                        rawTruncated: true
                    };
                }
                return null;
            }
        });
        const row = {
            dataset: { taskId: "42", taskType: "AUTO_REPLY_ALL" },
            nextElementSibling: null,
            after: (el) => { detailRow = el; }
        };
        await sandbox.toggleTaskDetail(row);
        const notice = '<div class="text-muted">内容过长已截断，完整内容见服务端日志</div>';
        assert.ok(detailRow.innerHTML.includes(notice), "truncation notice must be present when rawTruncated");
        assert.ok(detailRow.innerHTML.indexOf(notice) > detailRow.innerHTML.indexOf('<div class="pre">'),
            "notice must come after a pre block");
    });

    it("V-2/R-2: automatic enrichment details render enrichment stages, not discovery zeros", async () => {
        // 08 自动批次落的是 claimed/succeeded/pending/unmatched/failed + bySource（补全阶段），
        // 不是发现漏斗；按发现列渲染会让每个来源显示成一排 0。
        const automaticSummary = JSON.stringify({
            claimed: 3, succeeded: 1, pending: 1, unmatched: 1, failed: 0,
            bySource: { OPENALEX: { enqueued: 3, succeeded: 1, pending: 1, unmatched: 1, failed: 0 } }
        });
        let detailRow = null;
        const { sandbox } = createSandbox({
            apiImpl: async (url) => {
                if (url === "/api/task-executions/42/detail") {
                    return { rawRequestPayload: null, rawResultSummary: automaticSummary, rawTruncated: false };
                }
                return null;
            }
        });
        const row = {
            dataset: { taskId: "42", taskType: "EXPERT_ENRICHMENT" },
            nextElementSibling: null,
            after: (el) => { detailRow = el; }
        };
        await sandbox.toggleTaskDetail(row);

        assert.ok(detailRow, "detail row must be inserted");
        const rendered = detailRow.innerHTML;
        for (const header of ["平台", "入队", "成功", "待补", "未匹配", "失败"]) {
            assert.ok(rendered.includes(`>${header}</th>`), `enrichment stage column must be rendered: ${header}`);
        }
        assert.ok(!rendered.includes(">论文</th>"), "discovery columns must not be used for automatic enrichment");
        assert.ok(rendered.includes(">3</td>") && rendered.includes(">0</td>"),
            "rendered values must be the real per-stage counts (enqueued=3, failed=0)");
        assert.ok(rendered.includes(`<div class="pre">${sandbox.escapeHtml(automaticSummary)}</div>`),
            "the raw result summary stays visible below the stage table");
    });

    it("V-2/R-2: the shared bySource renderer keeps the discovery table for discovery run data", () => {
        // 反向断言：同一入口对发现漏斗数据仍渲染「论文/收录」列，形状识别不会把两者混在一起。
        const { sandbox } = createSandbox({ apiImpl: async () => null });
        const container = { innerHTML: "" };
        sandbox.renderBySourceTable(
            { OPENALEX: { papersSearched: 25, authorsExtracted: 9, emailsValid: 3, indexed: 1, promoted: 1 } },
            container
        );
        assert.ok(container.innerHTML.includes(">论文</th>") && container.innerHTML.includes(">收录</th>"),
            "discovery bySource must keep the discovery columns");
        assert.ok(!container.innerHTML.includes(">入队</th>"), "discovery bySource must not render enrichment columns");
    });

    it("S1-1: task type dropdown keeps the placeholder option and preserves selection", async () => {
        const { sandbox, elements } = createSandbox({
            apiImpl: async () => [
                { code: "AUTO_REPLY_ALL", label: "全量账号自动收信回复", count: 37 },
                { code: "EXPERT_ENRICHMENT", label: "学术数据补全", count: 5 }
            ],
            stateOverrides: { taskTypeOptions: null },
            elementsOverrides: { typeFilter: { value: "AUTO_REPLY_ALL", innerHTML: "" } }
        });
        await sandbox.loadTaskTypeOptions();
        assert.strictEqual(elements.typeFilter.innerHTML,
            '<option value="">全部自动化任务</option>'
            + '<option value="AUTO_REPLY_ALL">全量账号自动收信回复（37）</option>'
            + '<option value="EXPERT_ENRICHMENT">学术数据补全（5）</option>');
        assert.strictEqual(elements.typeFilter.value, "AUTO_REPLY_ALL",
            "selected value must survive the option rebuild");
        assert.deepStrictEqual(sandbox.state.taskTypeOptions,
            [{ code: "AUTO_REPLY_ALL", label: "全量账号自动收信回复", count: 37 },
                { code: "EXPERT_ENRICHMENT", label: "学术数据补全", count: 5 }],
            "options must be cached in state.taskTypeOptions");
    });

    it("I1-1: index.html keeps no concrete taskType option in #taskTypeFilter", () => {
        const start = html.indexOf('<select id="taskTypeFilter">');
        const end = html.indexOf("</select>", start);
        assert.ok(start >= 0, "#taskTypeFilter must exist");
        const section = html.slice(start, end);
        assert.ok(!/option value="[A-Z]/.test(section),
            "#taskTypeFilter must contain no hardcoded taskType options (I1-1)");
    });
});
