const fs = require("fs");
const path = require("path");
const vm = require("vm");
const assert = require("assert");
const { describe, it } = require("node:test");

const staticDir = path.join(__dirname, "..", "..", "main", "resources", "static");
const html = fs.readFileSync(path.join(staticDir, "index.html"), "utf-8");
const css = fs.readFileSync(path.join(staticDir, "styles.css"), "utf-8");
const app = fs.readFileSync(path.join(staticDir, "app.js"), "utf-8");

// 逐字 CSS 契约（plan 04 S-1..S-5）：styles.css 中对应规则块与契约逐字一致，
// 含全部状态选择器。S-1 追加在 :root 末尾，S-2..S-5 追加在文件末尾。
const CSS_S1 = `    --verbatim: #7c3aed;
    --verbatim-bg: rgba(124, 58, 237, 0.06);
    --verbatim-border: rgba(124, 58, 237, 0.24);`;
const CSS_S2 = `.rag-kb-layout {
    display: grid;
    grid-template-columns: 186px minmax(0, 1fr) 400px;
    gap: 14px;
    align-items: start;
}

@media (max-width: 1200px) {
    .rag-kb-layout {
        grid-template-columns: 1fr;
    }
}`;
const CSS_S3 = `.rag-kb-filters {
    padding: 8px 0;
}

.rag-kb-filter-label {
    font-size: 10.5px;
    letter-spacing: 0.06em;
    color: var(--text-muted);
    padding: 6px 14px 4px;
}

.rag-kb-filter-item {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 5px 14px;
    font-size: 12.5px;
    cursor: pointer;
    color: var(--text-secondary);
    border-left: 2px solid transparent;
}

.rag-kb-filter-item:hover {
    background: var(--surface);
}

.rag-kb-filter-item.active {
    background: var(--primary-light);
    border-left-color: var(--primary);
    color: var(--primary);
    font-weight: 500;
}

.rag-kb-search {
    padding: 9px 12px;
    border-bottom: 1px solid var(--line);
}

.rag-kb-search input {
    width: 100%;
    border: 1px solid var(--border);
    border-radius: var(--radius-sm);
    padding: 6px 10px;
    font-size: 12.5px;
    font-family: inherit;
    outline: none;
    background: var(--surface);
}

.rag-kb-search input:focus {
    border-color: var(--primary);
    background: #fff;
}

.rag-kb-list {
    max-height: 640px;
    overflow: auto;
}

.rag-kb-row {
    padding: 9px 14px;
    border-bottom: 1px solid var(--line);
    cursor: pointer;
    display: grid;
    grid-template-columns: 1fr auto;
    gap: 4px 10px;
}

.rag-kb-row:hover {
    background: var(--surface);
}

.rag-kb-row.active {
    background: var(--primary-light);
    box-shadow: inset 2px 0 0 var(--primary);
}

.rag-kb-row.disabled {
    opacity: 0.45;
}

.rag-kb-row-code {
    color: var(--primary);
    font-weight: 600;
    font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
    font-size: 11px;
}

.rag-kb-row-meta {
    grid-column: 1 / 2;
    font-size: 11.5px;
    color: var(--text-muted);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}`;
const CSS_S4 = `.rag-badge {
    display: inline-block;
    font-size: 10.5px;
    padding: 1px 7px;
    border-radius: 999px;
    border: 1px solid;
    line-height: 1.6;
    white-space: nowrap;
}

.rag-badge.verbatim {
    color: var(--verbatim);
    background: var(--verbatim-bg);
    border-color: var(--verbatim-border);
    font-weight: 600;
}

.rag-badge.risk-high {
    color: var(--error);
    background: var(--error-bg);
    border-color: var(--error-border);
}

.rag-badge.risk-medium {
    color: var(--warning);
    background: var(--warning-bg);
    border-color: var(--warning-border);
}

.rag-badge.risk-low {
    color: var(--success);
    background: var(--success-bg);
    border-color: var(--success-border);
}

.rag-badge.status-review {
    color: var(--warning);
    background: var(--warning-bg);
    border-color: var(--warning-border);
}

.rag-badge.status-approved {
    color: var(--success);
    background: var(--success-bg);
    border-color: var(--success-border);
}

.rag-badge.status-disabled {
    color: var(--text-muted);
    background: var(--surface);
    border-color: var(--border);
}`;
const CSS_S5 = `.rag-kb-detail-body {
    padding: 14px;
}

.rag-kb-field {
    margin-bottom: 15px;
}

.rag-kb-field-label {
    font-size: 10.5px;
    letter-spacing: 0.06em;
    color: var(--text-muted);
    margin-bottom: 5px;
    display: flex;
    justify-content: space-between;
    align-items: center;
}

.rag-kb-answer {
    border: 1px solid var(--border);
    border-radius: var(--radius-sm);
    padding: 10px 12px;
    font-size: 12.5px;
    line-height: 1.75;
    background: var(--surface);
    white-space: pre-wrap;
    width: 100%;
    font-family: inherit;
    color: var(--ink);
}

.rag-kb-answer.verbatim {
    border-color: var(--verbatim-border);
    background: var(--verbatim-bg);
}

.rag-kb-verbatim-warning {
    display: flex;
    gap: 8px;
    align-items: flex-start;
    background: var(--verbatim-bg);
    border: 1px solid var(--verbatim-border);
    border-radius: var(--radius-sm);
    padding: 8px 11px;
    font-size: 11.5px;
    color: var(--verbatim);
    margin-bottom: 7px;
    line-height: 1.6;
}

.rag-kb-chips {
    display: flex;
    flex-wrap: wrap;
    gap: 5px;
}

.rag-kb-chip {
    font-size: 11px;
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: 6px;
    padding: 2px 7px;
    color: var(--text-secondary);
}

.rag-kb-chip.coverage {
    color: var(--primary);
    background: var(--primary-light);
    border-color: rgba(30, 64, 175, 0.18);
    font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}

.rag-kb-detail-foot {
    border-top: 1px solid var(--line);
    padding: 11px 14px;
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 10px;
}`;

function extractFunction(name) {
    const start = app.indexOf(`function ${name}(`);
    const asyncStart = app.indexOf(`async function ${name}(`);
    const index = asyncStart >= 0 && (start < 0 || asyncStart < start) ? asyncStart : start;
    if (index < 0) throw new Error("missing " + name);
    let depth = 0;
    let opened = false;
    for (let cursor = index; cursor < app.length; cursor += 1) {
        if (app[cursor] === "{") { depth += 1; opened = true; }
        if (app[cursor] === "}") {
            depth -= 1;
            if (opened && depth === 0) return app.slice(index, cursor + 1);
        }
    }
    throw new Error("unterminated " + name);
}

const WARNING_TEXT = "逐字出信。这段文字会原封不动出现在发给专家的邮件里，模型只拿到占位符、无权改写。改这里 = 改对外话术。";

describe("RAG 知识库页 (plan 04)", () => {
    it("G-6 三点同步：按钮 data-tab=ragKb / 面板 aiTabRagKb / app.js 白名单链", () => {
        assert.ok(html.includes('<button type="button" class="ai-tab" data-tab="ragKb">RAG 知识库</button>'));
        assert.ok(html.includes('<div class="ai-tab-content" id="aiTabRagKb">'));
        const switchTab = extractFunction("switchAiTrainingTab");
        assert.ok(switchTab.includes('(tab === "ragKb" && panelId === "aiTabRagKb")'));
        const retiredQaPanel = "aiTab" + "Qa";
        assert.ok(!switchTab.includes(retiredQaPanel), "whitelist chain must not reference the retired QA panel");
        assert.ok(!html.includes('data-tab="qa">QA 知识库'), "old QA button must be gone");
        assert.ok(!html.includes('id="' + retiredQaPanel + '"'), "old QA panel must be gone");
        assert.ok(!app.includes('(tab === "qa" && panelId === "' + retiredQaPanel + '")'), "old whitelist entry must be gone");
    });

    it("G-8 + S-2 骨架：渲染函数按 id 取元素，这些 id 必须真实存在于 index.html", () => {
        ["ragKbFilters", "ragKbSearch", "ragKbList", "ragKbDetail", "ragKbListCount", "ragKbFingerprint"].forEach((id) => {
            assert.ok(html.includes(`id="${id}"`), `missing ${id} in index.html`);
        });
        assert.ok(html.includes('class="rag-kb-layout"'), "three-column layout skeleton required");
        assert.ok(html.includes('class="rag-kb-search"'));
        assert.ok(html.includes('id="ragKbSearch" placeholder="搜索 fact_code / 名称 / 问法 / 正文…"'));
    });

    it("loadAiTraining 改调 loadRagKb；旧 QA 渲染函数已随 07 清理", () => {
        const mainLoad = extractFunction("loadAiTraining");
        assert.ok(mainLoad.includes("loadRagKb()"), "main loader must call loadRagKb");
        assert.ok(!mainLoad.includes("loadAiTraining" + "Qa"), "main loader must no longer load QA rules");
        ["loadRagKb", "renderRagKbFilters", "renderRagKbList", "renderRagKbDetail", "saveRagFact"].forEach((name) => {
            assert.ok(app.includes(`function ${name}(`) || app.includes(`async function ${name}(`), `missing ${name}`);
        });
        ["renderAiTraining" + "QaPager", "renderAiTraining" + "QaTable", "loadAiTraining" + "Qa"].forEach((name) => {
            assert.ok(!app.includes(`function ${name}(`) && !app.includes(`async function ${name}(`), `retired QA render function must be gone: ${name}`);
        });
    });

    it("S-1：三个 verbatim token 与 :root 契约逐字一致", () => {
        assert.ok(css.includes(CSS_S1), "S-1 token block must be byte-identical");
        const value = css.match(/--verbatim:\s*([^;]+);/)[1].trim();
        assert.strictEqual(value, "#7c3aed");
        const occurrences = css.split("--verbatim").length - 1;
        assert.ok(occurrences >= 3, `expected >= 3 --verbatim mentions, got ${occurrences}`);
        assert.ok(!css.includes("--violet:"), "no violet token family may be introduced");
    });

    it("S-2..S-5：新增规则块与契约逐字一致（含全部状态选择器）", () => {
        [CSS_S2, CSS_S3, CSS_S4, CSS_S5].forEach((block, index) => {
            assert.ok(css.includes(block), `S-${index + 2} block must be byte-identical in styles.css`);
        });
        [".rag-badge.verbatim", ".rag-badge.risk-high", ".rag-badge.risk-medium", ".rag-badge.risk-low",
            ".rag-badge.status-review", ".rag-badge.status-approved", ".rag-badge.status-disabled",
            ".rag-kb-row.active", ".rag-kb-row.disabled", ".rag-kb-row-code", ".rag-kb-row-meta",
            ".rag-kb-verbatim-warning", ".rag-kb-answer.verbatim", ".rag-kb-chip.coverage",
            ".rag-kb-filter-item.active", ".rag-kb-detail-foot"].forEach((sel) => {
            assert.ok(css.includes(sel), `missing ${sel}`);
        });
    });

    it("G-5：三处 ?v= 缓存键同值且等于 20260903-bounce-warning", () => {
        ["styles.css", "trust-reply-workbench.js", "app.js"].forEach((asset) => {
            assert.ok(html.includes(`${asset}?v=20260903-bounce-warning`), `${asset} key`);
        });
        const keys = [...html.matchAll(/\?v=([0-9a-z-]+)/g)].map((match) => match[1]);
        assert.ok(keys.length >= 3, `expected 3+ cache keys, got ${keys.length}`);
        assert.ok(keys.every((key) => key === "20260903-bounce-warning"), `all keys must share one value: ${keys}`);
    });

    it("VERBATIM 事实渲染顶部逐字警示条（DOM stub 跑 renderRagKbDetail）", () => {
        const rendered = runDetailRenderer(verbatimFact());
        assert.ok(rendered.includes("rag-kb-verbatim-warning"), "VERBATIM fact must render the warning bar");
        assert.ok(rendered.includes(WARNING_TEXT), "warning copy must match verbatim");
        assert.ok(rendered.includes("rag-badge verbatim"), "VERBATIM badge must render");
        assert.ok(rendered.includes('data-rag-field="answer"'), "answer field must be editable");
    });

    it("COMPOSE 事实不渲染警示条，仍给出可编辑表单与保存按钮（DOM stub）", () => {
        const rendered = runDetailRenderer(composeFact());
        assert.ok(!rendered.includes("rag-kb-verbatim-warning"), "non-verbatim fact must not warn");
        assert.ok(!rendered.includes(WARNING_TEXT));
        assert.ok(rendered.includes('data-rag-action="save"'), "save button must exist");
        assert.ok(rendered.includes('data-rag-action="toggle"'), "enable/disable button must exist");
        assert.ok(rendered.includes("rag-kb-detail-foot"), "detail footer must exist");
    });

    it("列表渲染输出契约类名与选中/停用标记（DOM stub 跑 renderRagKbList/Filter）", () => {
        const panels = { ragKbList: { innerHTML: "", textContent: "" }, ragKbFilters: { innerHTML: "", textContent: "" }, ragKbListCount: { innerHTML: "", textContent: "" } };
        const calls = [];
        const facts = [verbatimFact(), composeFact()];
        const sandbox = makeSandbox(panels, calls, facts);
        vm.runInContext(extractFunction("ragKbMatchesFilters"), sandbox);
        vm.runInContext(extractFunction("ragKbCategoryCounts"), sandbox);
        vm.runInContext(extractFunction("ragKbEffectiveStatus"), sandbox);
        vm.runInContext(extractFunction("ragKbStatusBadgeClass"), sandbox);
        vm.runInContext(extractFunction("ragKbBadgesHtml"), sandbox);
        vm.runInContext(extractFunction("renderRagKbList"), sandbox);
        vm.runInContext(extractFunction("renderRagKbFilters"), sandbox);
        sandbox.state.aiTraining.ragKbSelected = "KB-FUND-033";
        sandbox.renderRagKbList();
        assert.ok(panels.ragKbList.innerHTML.includes("rag-kb-row"), "list must render rows");
        assert.ok(panels.ragKbList.innerHTML.includes("rag-kb-row active"), "selected row active");
        assert.ok(panels.ragKbList.innerHTML.includes("rag-kb-row disabled"), "disabled fact row class");
        assert.ok(panels.ragKbList.innerHTML.includes("KB-FUND-033"));
        sandbox.state.aiTraining.ragKbFilterKind = "render";
        sandbox.state.aiTraining.ragKbFilterValue = "VERBATIM";
        sandbox.renderRagKbFilters();
        assert.ok(panels.ragKbFilters.innerHTML.includes("rag-kb-filter-item active"), "active filter item");
        assert.ok(panels.ragKbFilters.innerHTML.includes("rag-kb-filter-label"), "filter group labels");
    });
});

function verbatimFact() {
    return { factCode: "KB-FUND-033", area: "FUND", seq: 33, title: "薪资与资助方式", category: "Funding",
        questionVariants: "how much compensation|salary", coverageKeys: "finance.compensation",
        answer: "Compensation is paid by the enterprise.", replyPolicy: "AUTO", status: "APPROVED",
        riskLevel: "HIGH", renderMode: "VERBATIM", sourceRefs: "QA_FACT_PROPOSAL:fact-33",
        legacyRuleId: null, enabled: true };
}

function composeFact() {
    return { factCode: "KB-ENT-012", area: "ENT", seq: 12, title: "合作企业类型", category: "Eligibility",
        questionVariants: "which enterprise", coverageKeys: "enterprise.type",
        answer: "Enterprises in the programme are domestic research bodies.", replyPolicy: "AUTO",
        status: "APPROVED", riskLevel: "LOW", renderMode: "COMPOSE", sourceRefs: "",
        legacyRuleId: 43, enabled: false };
}

function runDetailRenderer(fact) {
    const panels = { ragKbDetail: { innerHTML: "", textContent: "" } };
    const calls = [];
    const sandbox = makeSandbox(panels, calls, [fact]);
    sandbox.state.aiTraining.ragKbSelected = fact.factCode;
    vm.runInContext(extractFunction("ragKbEffectiveStatus"), sandbox);
    vm.runInContext(extractFunction("ragKbStatusBadgeClass"), sandbox);
    vm.runInContext(extractFunction("ragKbBadgesHtml"), sandbox);
    vm.runInContext(extractFunction("renderRagKbDetail"), sandbox);
    sandbox.renderRagKbDetail();
    return panels.ragKbDetail.innerHTML;
}

function makeSandbox(panels, calls, facts) {
    const sandbox = {
        state: { aiTraining: {
            ragKbFacts: facts,
            ragKbSelected: null,
            ragKbFingerprint: "",
            ragKbFactCount: 0,
            ragKbFilterKind: "all",
            ragKbFilterValue: "",
            ragKbSearch: ""
        } },
        escapeHtml: (value) => String(value ?? "")
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;"),
        $: (selector) => {
            calls.push(selector);
            const key = String(selector).replace("#", "");
            return panels[key] || { innerHTML: "", textContent: "" };
        },
        window: { localStorage: { getItem: () => "c5-test" } }
    };
    vm.createContext(sandbox);
    return sandbox;
}
