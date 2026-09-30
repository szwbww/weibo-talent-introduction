"use strict";

// fast-p 01 引用邮件模板 —— 样式/DOM/资源登记合同（S-1/S-2/S-3/S-4）：
// 1) 计划文本是唯一实现依据：从计划的 S-1/S-2 css fenced block 逐字读取，断言 styles.css 逐字包含；
// 2) S-1 入口按钮（位置 + class + data-action）与 S-2 全量 data-role / data-action 出现在
//    mailbox-chat.js 模板（DOM-stub 测试里节点恒存在，故必须查源文本）；
// 3) S-2 html 里出现的每个 class 必须在 styles.css 声明（复用 button/primary/checkbox-row）；
// 4) S-4 资源登记：11 个 ?v= 同值且为新键（键值从 index.html 派生，不写死历史数量）；
// 5) 模板不得新增 inline style；mailbox-chat.css 保持字节基线（02 evidence 同一文件）。
// 无 npm 依赖、无真实网络。

const fs = require("fs");
const path = require("path");
const assert = require("assert");
const { describe, it } = require("node:test");

const ROOT = path.join(__dirname, "..", "..", "main", "resources", "static");
const chatSource = fs.readFileSync(path.join(ROOT, "mailbox-chat.js"), "utf-8");
const stylesSource = fs.readFileSync(path.join(ROOT, "styles.css"), "utf-8");
const chatCssSource = fs.readFileSync(path.join(ROOT, "mailbox-chat.css"), "utf-8");
const indexSource = fs.readFileSync(path.join(ROOT, "index.html"), "utf-8");

const PLAN_PATH = path.join(__dirname, "..", "..", "..", "docs", "plans", "2026-09-30", "manual-reply-template-reference.md");
const planSource = fs.readFileSync(PLAN_PATH, "utf-8");

// mailbox-chat.css 的字节基线证据（02 计划落地目标，与 mailboxChatStyle.test.js 同一文件）。
const CHAT_CSS_BASELINE = path.join(
    __dirname, "..", "..", "..", "docs", "plans", "2026-09-09", "mailbox-refinement-evidence", "mailbox-chat.target.css"
);

// S-4：键值只允许存在于 index.html（既有契约：脚本与测试都不得固化当前键字面量），
// 因此这里全部从 index.html 派生，只断言「已替换旧键 / 全部同值 / 数量不变」。
const RETIRED_CACHE_KEY = "20260929-bounce-alert";
const CACHE_KEY = (() => {
    const match = indexSource.match(/styles\.css\?v=([^"'&<>\s]+)/);
    if (!match) throw new Error("index.html must register styles.css with a ?v= cache key");
    return match[1];
})();

const SHARED_CLASSES = ["button", "primary", "checkbox-row", "mail-chat"];

function cssBlock(marker) {
    const blocks = Array.from(planSource.matchAll(/```css\n([\s\S]*?)```/g)).map((match) => match[1]);
    const block = blocks.find((candidate) => candidate.includes(marker));
    if (!block) throw new Error(`plan must contain the verbatim css block for ${marker}`);
    return block;
}

function htmlBlock(marker) {
    const blocks = Array.from(planSource.matchAll(/```html\n([\s\S]*?)```/g)).map((match) => match[1]);
    const block = blocks.find((candidate) => candidate.includes(marker));
    if (!block) throw new Error(`plan must contain the verbatim html block for ${marker}`);
    return block;
}

const S1_CSS = cssBlock(".mail-chat .button.reply-template-trigger");
const S2_CSS = cssBlock(".reply-template-dialog");
const S1_HTML = htmlBlock("reply-template-trigger");
const S2_HTML = htmlBlock('<dialog class="reply-template-dialog"');

function attrValues(html, attr) {
    return Array.from(html.matchAll(new RegExp(`${attr}="([^"]*)"`, "g"))).map((match) => match[1]);
}

function classNames(html) {
    const out = new Set();
    attrValues(html, "class").forEach((value) => {
        value.split(/\s+/).filter(Boolean).forEach((cls) => out.add(cls));
    });
    return Array.from(out);
}

describe("fast-p 01 S-1/S-2：计划逐字 CSS 合同", () => {
    it("styles.css 逐字包含 S-1 与 S-2 的 css 块", () => {
        assert.ok(S1_CSS.includes(".mail-chat .button.reply-template-trigger{"), "S-1 块必须覆盖触发按钮");
        assert.ok(S1_CSS.includes(":focus-visible"), "S-1 块必须含焦点态");
        assert.ok(stylesSource.includes(S1_CSS), "styles.css 必须逐字包含 S-1 块");
        assert.ok(S2_CSS.includes(".reply-template-dialog{"), "S-2 块必须覆盖弹框");
        assert.ok(S2_CSS.includes("@media(max-width:760px)"), "S-2 块必须含窄屏规则");
        assert.ok(stylesSource.includes(S2_CSS), "styles.css 必须逐字包含 S-2 块");
    });

    it("新增 class 全部只在 S-1/S-2 块内声明，未污染历史块", () => {
        const withoutNew = stylesSource.replace(S1_CSS, "").replace(S2_CSS, "");
        const newClasses = new Set();
        [S1_CSS, S2_CSS].forEach((block) => {
            Array.from(block.matchAll(/\.([a-z][a-z0-9-]*)\s*[,{:[. ]/g)).forEach((match) => {
                if (match[1].indexOf("reply-template") === 0) newClasses.add(match[1]);
            });
        });
        assert.ok(newClasses.size >= 20, "S-1/S-2 必须声明完整的 reply-template-* 集合");
        newClasses.forEach((cls) => {
            assert.ok(
                new RegExp(`\\.${cls}(?=[\\s,{.:\\[])`).test(withoutNew) === false,
                `${cls} 只能在本计划新增的 S-1/S-2 块内声明`
            );
        });
    });

    it("S-2 的 [hidden] 依赖全局规则，不新造 display 工具类", () => {
        assert.match(stylesSource, /\[hidden\] \{\s*display: none !important;/, "全局 [hidden] 规则必须存在");
        assert.ok(!/\.reply-template-[a-z-]*hidden/.test(stylesSource), "不得新增 hidden 工具类");
    });
});

describe("fast-p 01 S-1/S-2：DOM 层级与 class 声明", () => {
    it("S-1 入口按钮逐字出现在 mailbox-chat.js，且顺序为会议/材料/引用模板/跟进", () => {
        assert.strictEqual(
            S1_HTML.trim(),
            '<button class="button reply-template-trigger" type="button" data-action="mc-open-template-reference">引用模板</button>',
            "S-1 按钮 HTML 合同"
        );
        assert.ok(chatSource.includes(S1_HTML.trim()), "按钮必须逐字存在于模板");
        assert.ok(
            chatSource.includes("${meetingTrigger}${materialTrigger}${templateReferenceTrigger}${followUpButton}"),
            "插入位置必须是会议/材料/引用模板/跟进"
        );
    });

    it("S-2 的每个 data-role 都在 mailbox-chat.js 模板里出现", () => {
        const roles = attrValues(S2_HTML, "data-role");
        assert.ok(roles.length >= 15, "S-2 合同必须覆盖全部动态节点");
        const missing = roles.filter((role) => !chatSource.includes(`data-role="${role}"`));
        assert.deepStrictEqual(missing, [], "每个 data-role 都必须在模板里存在");
    });

    it("S-1/S-2 的每个 data-action 都在 mailbox-chat.js 里出现", () => {
        const actions = attrValues(S1_HTML, "data-action").concat(attrValues(S2_HTML, "data-action"));
        assert.ok(actions.includes("mc-select-template-reference"), "模板选项动作");
        const missing = actions.filter((action) => !chatSource.includes(`"${action}"`));
        assert.deepStrictEqual(missing, [], "每个 data-action 都必须在实现里被处理");
    });

    it("S-2 html 里的每个 class 都在 styles.css 声明（复用类除外）", () => {
        const classes = classNames(S2_HTML).concat(classNames(S1_HTML));
        assert.ok(classes.includes("reply-template-trigger") && classes.includes("reply-template-dialog"));
        const unknown = classes.filter((cls) => {
            if (SHARED_CLASSES.includes(cls)) return false;
            return !new RegExp(`\\.${cls}(?=[\\s,{.:\\[])`).test(stylesSource);
        });
        assert.deepStrictEqual(unknown, [], "新增 class 必须已声明或为既有复用类");
        const notPrefixed = classes.filter((cls) => !SHARED_CLASSES.includes(cls) && cls.indexOf("reply-template-") !== 0);
        assert.deepStrictEqual(notPrefixed, [], "新增 class 必须统一 reply-template-* 前缀");
    });

    it("模板与弹框容器不带 inline style", () => {
        assert.ok(!/"style="/.test(chatSource), "mailbox-chat.js 模板不得出现 style 属性");
        assert.ok(!/'style='/.test(chatSource), "mailbox-chat.js 模板不得出现 style 属性");
        assert.ok(!/style="/.test(S2_HTML), "S-2 合同不得带 inline style");
    });
});

describe("fast-p 01 S-4：资源登记与字节基线", () => {
    it("index.html 的 11 个版本化资源统一为新键且旧键已移除", () => {
        assert.notStrictEqual(CACHE_KEY, RETIRED_CACHE_KEY, "本次实施必须替换旧缓存键");
        assert.match(CACHE_KEY, /^[0-9]{8}-[a-z0-9-]+$/, "键必须是 <yyyymmdd>-<slug>");
        const keys = Array.from(indexSource.matchAll(/\?v=([^"'&<>\s]+)/g)).map((match) => match[1]);
        assert.strictEqual(keys.length, 11, "index.html 必须仍是 11 个版本化资源（不新增 script/link）");
        assert.deepStrictEqual(Array.from(new Set(keys)), [CACHE_KEY], "全部资源键必须同值");
        assert.ok(indexSource.indexOf(RETIRED_CACHE_KEY) === -1, "不得残留旧键");
    });

    it("mailbox-chat.css 保持字节基线且不含本计划新增 class", () => {
        assert.strictEqual(chatCssSource, fs.readFileSync(CHAT_CSS_BASELINE, "utf-8"), "mailbox-chat.css 必须与字节基线一致");
        assert.ok(chatCssSource.indexOf("reply-template") === -1, "本计划样式不得写入 mailbox-chat.css");
    });
});
