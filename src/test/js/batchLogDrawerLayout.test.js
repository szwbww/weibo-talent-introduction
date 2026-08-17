const fs = require("fs");
const path = require("path");
const vm = require("vm");
const assert = require("assert");
const { describe, it } = require("node:test");

const appPath = path.join(__dirname, "..", "..", "main", "resources", "static", "app.js");
const appSource = fs.readFileSync(appPath, "utf-8");

function extractFn(name) {
    const regex = new RegExp("(?:async\\s+)?function\\s+" + name + "\\s*\\([^)]*\\)\\s*\\{[\\s\\S]*?\\n\\}");
    const match = appSource.match(regex);
    return match ? match[0] : null;
}

function element(value = "") {
    return {
        value,
        textContent: "",
        innerHTML: "",
        hidden: false,
        dataset: {},
        setAttribute() {},
        querySelectorAll() { return []; }
    };
}

function makeSandbox(overrides = {}) {
    const sandbox = {
        escapeHtml: (v) => String(v == null ? "" : v),
        regionLabel: (v) => v || "",
        operatorStatusLabel: (v) => (v === "NOT_CONTACTED" ? "未联系" : v || ""),
        cronToDisplayText: () => "",
        renderBatchConfigStatusToggle: () => "",
        batchGatePillHtml: () => '<span class="batch-gate-pill is-off">门禁过滤 · 关</span>',
        formatDateTime: (v) => String(v || "")
    };
    Object.assign(sandbox, overrides);
    return sandbox;
}

function makeConfig(parts) {
    return {
        id: 1,
        configName: "多条件任务",
        mailType: "INTRODUCTION",
        autoEnabled: false,
        funnelLevel: parts.funnelLevel || null,
        tags: parts.tags || [],
        regions: parts.regions || [],
        emailDomains: parts.emailDomains || [],
        operatorStatuses: parts.operatorStatuses || [],
        discipline: parts.discipline || null,
        templateId: null,
        cron: null,
        nextFireTime: null,
        lastExecutedAt: null
    };
}

const sixPartConfig = makeConfig({
    funnelLevel: "CANDIDATE",
    tags: ["AI", "STEM"],
    regions: ["South America"],
    emailDomains: ["gmail.com"],
    discipline: "STEM",
    operatorStatuses: ["NOT_CONTACTED"]
});

describe("batch log drawer layout", () => {
    it("I-1: renderBatchConfigRow keeps exactly 7 <td> cells with 6 filter conditions", () => {
        const renderRow = extractFn("renderBatchConfigRow");
        assert.ok(renderRow, "renderBatchConfigRow must exist");

        const sandbox = makeSandbox();
        vm.createContext(sandbox);
        vm.runInContext(renderRow, sandbox);

        const html = sandbox.renderBatchConfigRow(sixPartConfig);
        const tdCount = (html.match(/<td/g) || []).length;
        assert.strictEqual(tdCount, 7,
            "row with 6 filter conditions must render 7 <td> cells, got " + tdCount);
    });

    it("I-1: scope cell is truncated at the array layer, never by character count", () => {
        const renderRow = extractFn("renderBatchConfigRow");
        assert.ok(renderRow, "renderBatchConfigRow must exist");
        assert.ok(!renderRow.includes("substring"),
            "renderBatchConfigRow must not truncate HTML by substring");

        const sandbox = makeSandbox();
        vm.createContext(sandbox);
        vm.runInContext(renderRow, sandbox);

        const html = sandbox.renderBatchConfigRow(sixPartConfig);
        assert.ok(html.includes("状态: 未联系"),
            "last filter line must be present in full, untruncated");
        assert.ok(!html.includes("batch-task-sc</td>"),
            "scope cell must not be cut mid-tag");
    });

    it("S-3: 4+ filters fold into exactly one details.log-detail.batch-task-scope-more with 展开剩余 N 项", () => {
        const renderRow = extractFn("renderBatchConfigRow");
        assert.ok(renderRow, "renderBatchConfigRow must exist");

        const sandbox = makeSandbox();
        vm.createContext(sandbox);
        vm.runInContext(renderRow, sandbox);

        const html = sandbox.renderBatchConfigRow(sixPartConfig);
        const detailsMatches = html.match(/<details class="log-detail batch-task-scope-more">/g) || [];
        assert.strictEqual(detailsMatches.length, 1,
            "exactly one foldable details element expected, got " + detailsMatches.length);
        assert.ok(html.includes("<summary>展开剩余 3 项</summary>"),
            "summary must read 展开剩余 3 项 (6 parts minus 3 visible)");
    });

    it("I-2: gate pill line stays after the </details> fold, never inside it", () => {
        const renderRow = extractFn("renderBatchConfigRow");
        assert.ok(renderRow, "renderBatchConfigRow must exist");

        const sandbox = makeSandbox();
        vm.createContext(sandbox);
        vm.runInContext(renderRow, sandbox);

        const html = sandbox.renderBatchConfigRow(sixPartConfig);
        const detailsClose = html.indexOf("</details>");
        const pill = html.indexOf("门禁过滤 · 关");
        assert.ok(detailsClose !== -1 && pill !== -1, "details fold and gate pill must both exist");
        assert.ok(pill > detailsClose,
            "gate pill must come after the </details> fold, not be folded inside it");
        assert.ok(/<span class="batch-task-scope-line"><span class="batch-gate-pill/.test(html),
            "gate pill must stay wrapped in its own .batch-task-scope-line");
    });

    it("I-1/I-2: no <details> at 3 or fewer filters; 无限制 and pill still render at 0", () => {
        const renderRow = extractFn("renderBatchConfigRow");
        assert.ok(renderRow, "renderBatchConfigRow must exist");

        const sandbox = makeSandbox();
        vm.createContext(sandbox);
        vm.runInContext(renderRow, sandbox);

        const threePart = makeConfig({ funnelLevel: "CANDIDATE", emailDomains: ["gmail.com"], discipline: "STEM" });
        const threeHtml = sandbox.renderBatchConfigRow(threePart);
        assert.ok(!threeHtml.includes("<details"), "3 filters must not fold into a details element");

        const emptyHtml = sandbox.renderBatchConfigRow(makeConfig({}));
        assert.ok(emptyHtml.includes("无限制"), "empty filters must still render 无限制");
        assert.ok(emptyHtml.includes("门禁过滤 · 关"), "gate pill must still render at 0 filters");
    });

    it("I-4: empty reasons/errorSamples hide the three exception sections but never the timeline", () => {
        const renderDetail = extractFn("renderBatchExecutionDetail");
        assert.ok(renderDetail, "renderBatchExecutionDetail must exist");
        assert.ok(!renderDetail.includes("batchLogTimelineSection"),
            "renderBatchExecutionDetail must never hide the timeline section");

        const elements = {
            batchLogFailureSection: element(),
            batchLogSkippedSection: element(),
            batchLogErrorSamples: element()
        };
        const sandbox = {
            document: { getElementById: (id) => elements[id] || null },
            renderBatchLiveSection: () => {},
            renderOutcomeMetrics: () => {},
            renderIntegrityWarning: () => {},
            renderReasons: () => {},
            renderErrorSamples: () => {},
            renderBatchTimeline: () => {},
            renderLogStatusInfo: () => {}
        };
        vm.createContext(sandbox);
        vm.runInContext(renderDetail, sandbox);

        sandbox.renderBatchExecutionDetail({
            failureReasons: {},
            skippedReasons: {},
            errorSamples: [],
            progressRows: []
        });

        assert.strictEqual(elements.batchLogFailureSection.hidden, true,
            "empty failureReasons must hide the failure section");
        assert.strictEqual(elements.batchLogSkippedSection.hidden, true,
            "empty skippedReasons must hide the skipped section");
        assert.strictEqual(elements.batchLogErrorSamples.hidden, true,
            "empty errorSamples must hide the error samples section");
    });

    it("I-4: non-empty data keeps the three exception sections visible", () => {
        const renderDetail = extractFn("renderBatchExecutionDetail");
        assert.ok(renderDetail, "renderBatchExecutionDetail must exist");

        const elements = {
            batchLogFailureSection: element(),
            batchLogSkippedSection: element(),
            batchLogErrorSamples: element()
        };
        const sandbox = {
            document: { getElementById: (id) => elements[id] || null },
            renderBatchLiveSection: () => {},
            renderOutcomeMetrics: () => {},
            renderIntegrityWarning: () => {},
            renderReasons: () => {},
            renderErrorSamples: () => {},
            renderBatchTimeline: () => {},
            renderLogStatusInfo: () => {}
        };
        vm.createContext(sandbox);
        vm.runInContext(renderDetail, sandbox);

        sandbox.renderBatchExecutionDetail({
            failureReasons: { b: { label: "退信", count: 1 } },
            skippedReasons: { a: { label: "无门禁", count: 2 } },
            errorSamples: ["sample"],
            progressRows: []
        });

        assert.strictEqual(elements.batchLogFailureSection.hidden, false);
        assert.strictEqual(elements.batchLogSkippedSection.hidden, false);
        assert.strictEqual(elements.batchLogErrorSamples.hidden, false);
    });
});
