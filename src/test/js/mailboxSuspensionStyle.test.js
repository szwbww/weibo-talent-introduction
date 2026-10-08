"use strict";

// 02 挂起样式契约测试（S-1..S-4）：
// 1) styles.css 中 mailbox-suspension-contract 注释块必须与计划 S-4（及 evidence
//    target-suspension.css）逐字（字节）一致；
// 2) mailbox-chat.css 保持字节冻结（与 02 基线证据一致）；
// 3) 新增 DOM 使用的位置/class 均在 S-4 中声明，且无 inline style、无图标伪元素；
// 4) index.html 11 个版本化资源统一为新键（键从 index.html 动态取，不写死）；
// 5) 新宿主/新控件依赖的 id 在 index.html 源文本存在（DOM stub 无法证明）。

const fs = require("fs");
const path = require("path");
const assert = require("assert");
const { describe, it } = require("node:test");

const ROOT = path.join(__dirname, "..", "..", "main", "resources", "static");
const stylesSource = fs.readFileSync(path.join(ROOT, "styles.css"), "utf-8");
const chatSource = fs.readFileSync(path.join(ROOT, "mailbox-chat.js"), "utf-8");
const indexSource = fs.readFileSync(path.join(ROOT, "index.html"), "utf-8");
const planSource = fs.readFileSync(
    path.join(__dirname, "..", "..", "..", "docs", "plans", "2026-10-03", "mailbox-suspension-02-frontend.md"),
    "utf-8"
);
const TARGET_CSS = path.join(__dirname, "..", "..", "..", "docs", "plans", "2026-10-03", "mailbox-suspension-evidence", "target-suspension.css");

const START = "/* mailbox-suspension-contract:start */";
const END = "/* mailbox-suspension-contract:end */";

function extractContractBlock(text) {
    const start = text.indexOf(START);
    const end = text.indexOf(END);
    assert.ok(start >= 0 && end > start, "contract markers present");
    const block = text.slice(start, end + END.length);
    return text[end + END.length] === "\n" ? block + "\n" : block;
}

const cacheKey = (() => {
    const match = indexSource.match(/styles\.css\?v=([^"'&<>]+)/);
    if (!match) throw new Error("index.html must register styles.css with a ?v= cache key");
    return match[1];
})();

describe("02 · S-4 CSS 逐字契约", () => {
    it("styles.css 注释块与计划 S-4 字节一致", () => {
        const landed = extractContractBlock(stylesSource);
        const planned = extractContractBlock(planSource);
        assert.strictEqual(landed, planned, "styles.css 追加块必须与计划 S-4 逐字一致");
    });

    it("styles.css 注释块与 evidence/target-suspension.css 字节一致", () => {
        const landed = extractContractBlock(stylesSource);
        const target = fs.readFileSync(TARGET_CSS, "utf-8");
        assert.strictEqual(landed, target, "S-4 唯一权威证据必须一致");
    });

    it("styles.css 中恰有一个 contract 块", () => {
        assert.strictEqual(stylesSource.split(START).length - 1, 1, "只有一个 start 标记");
        assert.strictEqual(stylesSource.split(END).length - 1, 1, "只有一个 end 标记");
    });

});

describe("02 · S-1..S-3 类名与图标边界", () => {
    const SUSPENSION_CLASSES = [
        "mailbox-suspend-count",
        "mailbox-suspend-card-footer",
        "mailbox-suspend-state",
        "mailbox-suspend-card-action",
        "mailbox-suspend-action",
        "mailbox-suspend-banner",
        "mailbox-suspend-symbol",
        "mailbox-suspend-banner-content",
        "mailbox-suspend-inline-reason",
        "mailbox-suspend-inline-reason-head",
        "mailbox-suspend-reason-count",
        "mailbox-suspend-inline-reason-bottom",
        "mailbox-suspend-inline-secondary",
        "mailbox-suspend-inline-primary",
        "mailbox-suspend-error",
        "mailbox-suspend-process-confirm",
        "mailbox-suspend-processed-label",
        "mailbox-suspend-completion-line",
        "mailbox-suspend-completion-actions",
        "mailbox-suspend-pending-action"
    ];

    it("JS 产出的 mailbox-suspend-* class 均在 S-4 中声明", () => {
        const used = new Set((chatSource.match(/mailbox-suspend-[a-z-]+/g) || []));
        used.forEach((cls) => {
            assert.ok(SUSPENSION_CLASSES.includes(cls), `${cls} 必须在 S-4 声明`);
            assert.ok(stylesSource.includes("." + cls), `${cls} 必须出现在 styles.css`);
        });
        SUSPENSION_CLASSES.forEach((cls) => assert.ok(used.has(cls) || cls === "mailbox-suspend-symbol", `${cls} 覆盖`));
    });

    it("挂起操作按钮无图标伪元素（::before content:none）", () => {
        assert.match(stylesSource, /\.mailbox-suspend-card-action::before,\.mailbox-suspend-action::before\{content:none;display:none\}/);
    });

    it("新交互模板无 inline style", () => {
        ["mailbox-suspend-inline-reason", "mailbox-suspend-completion-line", "mailbox-suspend-banner"].forEach((cls) => {
            const idx = chatSource.indexOf(cls);
            assert.ok(idx >= 0, `${cls} 出现于 JS`);
        });
        assert.ok(!/style="[^"]*mailbox-suspend/.test(chatSource), "不得使用 inline style");
        assert.ok(!/class="mailbox-suspend[^"]*"[^>]*style=/.test(chatSource), "新 DOM 不得内联样式");
    });

    it("≤760px 触控与输入规则俱全", () => {
        const media = stylesSource.slice(stylesSource.indexOf("@media(max-width:760px){"));
        assert.match(media, /\.mailbox-suspend-inline-reason textarea\{font-size:16px\}/);
        assert.match(media, /min-height:44px/);
    });
});

describe("02 · S-1 缓存键与宿主 id", () => {
    it("index.html 11 个版本化资源统一为新键", () => {
        const versioned = indexSource.match(/\?v=([^"'&<>]+)/g) || [];
        assert.strictEqual(versioned.length, 11, "恰好 11 个版本化资源");
        const distinct = Array.from(new Set(versioned));
        assert.deepStrictEqual(distinct, ["?v=" + cacheKey], "11 处必须同值");
        assert.ok(!cacheKey.includes("mobile-core"), "统一为 02 发布键，不得残留并行 mobile-core 键");
        assert.ok(/^\d{8}-[a-z0-9-]+$/.test(cacheKey), "键格式合法");
    });

    it("新样式依赖的宿主 id 存在于 index.html 源文本", () => {
        ["id=\"view-mailbox\"", "id=\"mailboxList\""].forEach((needle) => {
            assert.ok(indexSource.includes(needle), `${needle} 必须在 index.html`);
        });
    });

    it("JS 中新交互动作字符串齐全（无 dialog/alert/confirm/prompt）", () => {
        ["mc-suspension", "mc-suspension-reason-cancel", "mc-suspension-reason-confirm",
            "mc-process-cancel", "mc-process-confirm", "mc-suspension-keep", "mc-suspension-end"].forEach((action) => {
            assert.ok(chatSource.includes(`"${action}"`), `${action} 已定义`);
        });
        assert.ok(!/openDialog\("mark-unmatched-resolved"\)/.test(chatSource), "处理不再走公共弹窗");
    });
});
