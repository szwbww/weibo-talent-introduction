const fs = require("fs");
const path = require("path");
const vm = require("vm");
const assert = require("assert");
const { describe, it } = require("node:test");

const appJsPath = path.join(__dirname, "..", "..", "main", "resources", "static", "app.js");
const appJsSource = fs.readFileSync(appJsPath, "utf-8");

function extractFn(name) {
    const regex = new RegExp("(?:async\\s+)?function\\s+" + name + "\\s*\\([^)]*\\)\\s*\\{[\\s\\S]*?\\n\\}");
    const match = appJsSource.match(regex);
    if (!match) throw new Error("Could not find " + name + " in app.js");
    return match[0];
}

/** 极简 DOM stub：$(sel) 返回带 querySelector 的节点，thead/tbody 各自可写 innerHTML。 */
function createSandbox(monitoringState) {
    const nodes = new Map();
    function node(id) {
        if (!nodes.has(id)) {
            const children = new Map();
            nodes.set(id, {
                id,
                innerHTML: "",
                textContent: "",
                querySelector(tag) {
                    if (!children.has(tag)) children.set(tag, { innerHTML: "" });
                    return children.get(tag);
                }
            });
        }
        return nodes.get(id);
    }
    const sandbox = {
        Math,
        Number,
        String,
        nodes,
        state: { monitoring: monitoringState },
        $: (sel) => node(sel.replace(/^#/, ""))
    };
    vm.createContext(sandbox);
    ["escapeHtml", "formatPercent", "monitoringDistributionBar", "monitoringReplyRateCell",
     "monitoringRangeLabel", "renderMonitoringProviderDistribution", "renderMonitoringCards"]
        .forEach((fn) => vm.runInContext(extractFn(fn), sandbox));
    return sandbox;
}

function baseRow(overrides) {
    return Object.assign({
        provider: "gmail",
        sentCount: 100,
        repliedCount: 10,
        matureCohortCount: 100,
        matureRepliedCount: 10,
        undeliveredCount: 0
    }, overrides);
}

function providerHtml(rows, unattributedBounceCount) {
    const sandbox = createSandbox({
        providerDistribution: rows,
        unattributedBounceCount: unattributedBounceCount || 0,
        rangeDays: 30
    });
    sandbox.renderMonitoringProviderDistribution();
    const table = sandbox.nodes.get("monitoringProviderDistributionTable");
    return { head: table.querySelector("thead").innerHTML, body: table.querySelector("tbody").innerHTML };
}

describe("provider distribution — 未送达列 (I-5)", () => {
    it("表头恰好 7 列，且不再有硬退率 / 软退", () => {
        const { head } = providerHtml([baseRow({})], 0);
        assert.equal((head.match(/<th>/g) || []).length, 7);
        assert.ok(head.includes("<th>未送达(人)</th>"));
        assert.ok(!head.includes("硬退率"), "硬退率列应已删除");
        assert.ok(!head.includes("软退"), "软退列应已删除");
    });

    it("空状态行 colspan 为 7", () => {
        const { body } = providerHtml([], 0);
        assert.ok(body.includes('colspan="7"'));
        assert.ok(!body.includes('colspan="8"'), "colspan 必须随列数从 8 改为 7");
        assert.ok(body.includes("暂无数据"));
    });

    it("每个数据行恰好 7 个单元格", () => {
        const { body } = providerHtml([baseRow({})], 0);
        assert.equal((body.match(/<td/g) || []).length, 7);
    });
});

describe("provider distribution — 队列为 0 仍显示未送达 (I-9)", () => {
    it("cohort=0 且 undelivered=4 时，未送达格渲染 4 而不是 -", () => {
        const { body } = providerHtml([baseRow({
            provider: "tencent", sentCount: 0, repliedCount: 0,
            matureCohortCount: 0, matureRepliedCount: 0, undeliveredCount: 4
        })], 0);
        const cells = body.match(/<td[^>]*>([\s\S]*?)<\/td>/g);
        const last = cells[cells.length - 1];
        assert.ok(last.includes("4"), "未送达格应显示 4，实际：" + last);
        assert.ok(!/>-</.test(last), "未送达格不得因队列为 0 而渲染成 -");
    });

    it("undelivered=0 时渲染 0，不是空白也不是 -", () => {
        const { body } = providerHtml([baseRow({ undeliveredCount: 0 })], 0);
        const cells = body.match(/<td[^>]*>([\s\S]*?)<\/td>/g);
        assert.ok(cells[cells.length - 1].includes("0"));
    });

    it("渲染未送达格的代码不含任何以 cohort 为条件的分支", () => {
        const fn = extractFn("renderMonitoringProviderDistribution");
        const undeliveredLine = fn.split("\n").find((l) => l.includes("undeliveredCount"));
        assert.ok(undeliveredLine, "未找到渲染 undeliveredCount 的行");
        assert.ok(!undeliveredLine.includes("cohort"),
            "未送达单元格不得依赖 cohort（I-9）：" + undeliveredLine);
    });
});

describe("provider distribution — 未归因表尾 (I-4 / S-2)", () => {
    it("unattributedBounceCount > 0 时渲染表尾行，文案逐字且 colspan=7", () => {
        const { body } = providerHtml([baseRow({})], 6);
        assert.ok(body.includes(
            '<tr><td colspan="7" class="text-muted" style="text-align:center;">' +
            "另有 6 封退信未能关联到专家（关联为空或专家已不存在），未计入上表任何一行。</td></tr>"
        ), "表尾文案与 S-2 契约不一致：" + body);
        assert.ok(!body.includes("收件人与关联专家均缺失"),
            "禁止使用与查询判据不符的旧文案（查询不判断 failed_recipient）");
    });

    it("unattributedBounceCount = 0 时不渲染表尾行", () => {
        const { body } = providerHtml([baseRow({})], 0);
        assert.ok(!body.includes("未能关联到专家"));
    });

    it("有数据行时表尾行仍然存在（防 || 短路吞掉表尾）", () => {
        const { body } = providerHtml([baseRow({}), baseRow({ provider: "edu" })], 3);
        assert.ok(body.includes("gmail") && body.includes("edu"));
        assert.ok(body.includes("另有 3 封退信未能关联到专家"));
    });

    it("无数据行但有未归因退信时，空状态行与表尾行并存", () => {
        const { body } = providerHtml([], 2);
        assert.ok(body.includes("暂无数据"));
        assert.ok(body.includes("另有 2 封退信未能关联到专家"));
    });
});

describe("最高未送达服务商卡片 (I-9)", () => {
    function cardsHtml(providers) {
        const sandbox = createSandbox({
            summary: {},
            providerDistribution: providers,
            regionDistribution: [],
            unattributedBounceCount: 0,
            rangeDays: 30
        });
        sandbox.renderMonitoringCards();
        return sandbox.nodes.get("monitoringCards").innerHTML;
    }

    it("按未送达人数降序选取，而不是按队列人数或比率", () => {
        const html = cardsHtml([
            baseRow({ provider: "gmail", sentCount: 1000, undeliveredCount: 5 }),
            baseRow({ provider: "edu", sentCount: 50, undeliveredCount: 9 })
        ]);
        assert.ok(html.includes("edu (9/50)"), "应选未送达最多的 edu，实际：" + html);
        assert.ok(!html.includes("gmail (5/1000)"));
    });

    it("队列为 0 但有未送达的服务商必须能被选中", () => {
        const html = cardsHtml([
            baseRow({ provider: "gmail", sentCount: 1000, undeliveredCount: 1 }),
            baseRow({ provider: "tencent", sentCount: 0, undeliveredCount: 7 })
        ]);
        assert.ok(html.includes("tencent (7/0)"),
            "候选集必须按 undeliveredCount 过滤而非 sentCount（I-9），实际：" + html);
    });

    it("全部未送达为 0 时显示 -", () => {
        const html = cardsHtml([baseRow({ undeliveredCount: 0 })]);
        assert.ok(html.includes("最高未送达服务商"));
        assert.ok(!html.includes("gmail ("));
    });

    it("卡片标题已改名，旧名不再出现", () => {
        assert.ok(appJsSource.includes("最高未送达服务商"));
        assert.ok(!appJsSource.includes("最高退信服务商"));
    });

    it("候选集与排序不含任何除法", () => {
        const fn = extractFn("renderMonitoringCards");
        const chain = fn.slice(fn.indexOf("worstUndeliveredProvider"), fn.indexOf("const cards"));
        assert.ok(chain.includes("undeliveredCount || 0) > 0"),
            "候选集应按 undeliveredCount 过滤：" + chain);
        assert.ok(!/sentCount \|\| 0\) > 0/.test(chain), "不得按 sentCount 过滤候选集");
        assert.ok(!chain.includes("/"), "排序键不得做除法（零分母）：" + chain);
    });
});

describe("接口返回形状解包 (I-7)", () => {
    it("provider-distribution 的 catch 兜底是对象而非数组", () => {
        assert.match(appJsSource,
            /provider-distribution[^\n]*\.catch\(\(\) => \(\{ rows: \[\], unattributedBounceCount: 0 \}\)\)/);
    });

    it("state.providerDistribution 取的是 rows，仍是数组", () => {
        assert.match(appJsSource,
            /state\.monitoring\.providerDistribution = providerDistribution\?\.rows \|\| \[\];/);
        assert.match(appJsSource,
            /state\.monitoring\.unattributedBounceCount = providerDistribution\?\.unattributedBounceCount \|\| 0;/);
    });

    it("region-distribution 的数组兜底未被误改", () => {
        assert.match(appJsSource, /region-distribution[^\n]*\.catch\(\(\) => \[\]\)/);
        assert.match(appJsSource, /state\.monitoring\.regionDistribution = regionDistribution \|\| \[\];/);
    });

    it("state.monitoring 初值含 unattributedBounceCount", () => {
        assert.match(appJsSource, /unattributedBounceCount: 0,/);
    });

    it("renderMonitoringCards 仍把 providerDistribution 当数组用", () => {
        const fn = extractFn("renderMonitoringCards");
        assert.ok(fn.includes("state.monitoring.providerDistribution || []"),
            "若把整个响应对象赋给 state，此处 .filter 会抛 TypeError");
    });
});

describe("后端字段已彻底移除", () => {
    it("app.js 不再引用 hardBounceCount / softBounceCount", () => {
        assert.ok(!appJsSource.includes("hardBounceCount"));
        assert.ok(!appJsSource.includes("softBounceCount"));
    });
});
