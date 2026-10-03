"use strict";

// fast-p 2026-10-02 c3 样式契约测试（S-1/S-2/S-3）：
// 1) 计划文本是唯一实现依据：从 plan 的 S-1/S-2/S-3 ```css 块逐字读取期望文本，断言
//    styles.css 逐字包含并按 S-1→S-2→S-3 顺序保留（后续搜索增强可追加局部样式；A2 最小收窄后的
//    task-center 守卫：本功能按合同追加在文件尾部）。
// 2) 新增 class 必须在 styles.css 声明；字节锁定的 mailbox-chat.css 不得吸收任何
//    contact-timing 规则。
// 3) 模板卫生：无 inline style；dialog 一律由 createElement("dialog") 建立后挂 body，
//    绝不出现在 innerHTML 模板里（避免嵌套 dialog）。
// 4) S-1/S-2 骨架的关键属性与入口 data-action 存在于 mailbox-chat.js。
// 5) 资源登记：全部 ?v= 同值、无旧键残留、数量与位置不变。

const fs = require("fs");
const path = require("path");
const assert = require("assert");
const { describe, it } = require("node:test");

const ROOT = path.join(__dirname, "..", "..", "main", "resources", "static");
const stylesSource = fs.readFileSync(path.join(ROOT, "styles.css"), "utf-8");
const chatCssSource = fs.readFileSync(path.join(ROOT, "mailbox-chat.css"), "utf-8");
const chatSource = fs.readFileSync(path.join(ROOT, "mailbox-chat.js"), "utf-8");
const indexSource = fs.readFileSync(path.join(ROOT, "index.html"), "utf-8");

const PLAN_PATH = path.join(__dirname, "..", "..", "..", "docs", "plans", "2026-10-02", "contact-timing-03-compact-ui.md");
const planSource = fs.readFileSync(PLAN_PATH, "utf-8");

// 02 计划落地的 mailbox-chat.css 字节基线（与 mailboxChatStyle.test.js 同一文件）。
const CHAT_CSS_BASELINE = path.join(__dirname, "..", "..", "..", "docs", "plans", "2026-09-09", "mailbox-refinement-evidence", "mailbox-chat.target.css");

const RETIRED_CACHE_KEY = "20261001-email-verification-allowlist";
const CACHE_KEY = (() => {
    const match = indexSource.match(/styles\.css\?v=([^"'&<>\s]+)/);
    if (!match) throw new Error("index.html must register styles.css with a ?v= cache key");
    return match[1];
})();

/** 取 plan 中紧跟在给定注释标记之后的 ```css 块全文（含标记行）。 */
function cssBlockAfter(marker) {
    const at = planSource.indexOf(marker);
    if (at < 0) throw new Error("plan must declare " + marker);
    const open = planSource.lastIndexOf("```css\n", at);
    if (open < 0) throw new Error("missing css fence for " + marker);
    const bodyStart = open + "```css\n".length;
    const close = planSource.indexOf("```", bodyStart);
    if (close < 0) throw new Error("unterminated css fence for " + marker);
    return planSource.slice(bodyStart, close);
}

const S1_CSS = cssBlockAfter("/* contact-timing S-1 */");
const S2_CSS = cssBlockAfter("/* contact-timing S-2 */");
const S3_CSS = cssBlockAfter("/* contact-timing S-3 */");
const APPENDED_CSS = S1_CSS + "\n" + S2_CSS + "\n" + S3_CSS;

describe("S-1/S-2/S-3: styles.css 逐字追加合同", () => {
    it("styles.css 按 S-1→S-2→S-3 连续保留（不删一行、不改一个值）", () => {
        [["S-1", S1_CSS], ["S-2", S2_CSS], ["S-3", S3_CSS]].forEach(([name, block]) => {
            assert.ok(stylesSource.includes(block), `${name} 的完整 CSS 块必须逐字出现在 styles.css`);
        });
        assert.ok(stylesSource.includes(APPENDED_CSS), "三块必须按 S-1→S-2→S-3 顺序连续保留");
    });

    it("追加点紧接既有契约块（A2 后的 task-center 守卫语义：其后不得再有第二个 task-center 块）", () => {
        const before = stylesSource.slice(0, stylesSource.indexOf(APPENDED_CSS));
        assert.match(before, /\/\* task-center-contract:end \*\/\n\n$/, "追加点必须紧跟既有契约块之后");
        assert.ok(!before.includes("/* contact-timing S-1 */"), "S-1 块只能出现一次");
        assert.strictEqual(stylesSource.split("/* contact-timing S-1 */").length - 1, 1);
        assert.strictEqual(stylesSource.split("/* contact-timing S-2 */").length - 1, 1);
        assert.strictEqual(stylesSource.split("/* contact-timing S-3 */").length - 1, 1);
    });

    it("S-1/S-2/S-3 关键实值逐字（颜色/字重/尺寸/断点/禁用与焦点）", () => {
        assert.match(S1_CSS, /\.contact-timing-recommend strong\{font-size:12px;font-weight:600;color:#285ac0;font-variant-numeric:tabular-nums\}/);
        assert.match(S1_CSS, /\.mail-chat \.contact-timing\{display:inline-flex;align-items:center;flex-wrap:wrap;gap:9px;margin-left:auto;padding-left:10px;border-left:1px solid #e1e8f2;min-height:22px;max-width:100%;font-size:11px;line-height:1\.6\}/);
        assert.match(S1_CSS, /\.mail-chat \.contact-timing \.mc-text-button:disabled\{opacity:\.45;cursor:not-allowed\}/);
        assert.match(S1_CSS, /\.mail-chat \.contact-timing \.mc-text-button:focus-visible\{outline:2px solid #6389d3;outline-offset:2px\}/);
        assert.match(S2_CSS, /^\.contact-timing-dialog\{position:fixed;inset:0;margin:auto;padding:0;width:440px;max-width:calc\(100vw - 32px\)/m);
        assert.match(S2_CSS, /\.contact-timing-dialog\[data-kind=evidence\]\{width:360px\}/);
        assert.match(S2_CSS, /\.contact-timing-dialog\{[^}]*background:#fff/);
        assert.match(S2_CSS, /\.contact-timing-dialog \[hidden\]\{display:none!important\}/);
        assert.match(S3_CSS, /^@media\(max-width:760px\)\{/m);
        assert.match(S3_CSS, /\.mail-chat \.contact-timing\{margin-left:0;padding-left:0;border-left:0\}/);
    });

    it("新增规则只作用于 contact-timing 作用域，不新增全局 p/select/button 覆盖", () => {
        assert.ok(!/^\s*p\s*\{/m.test(S2_CSS), "不得出现裸 p 规则");
        assert.ok(!/^\s*select\s*\{/m.test(S2_CSS), "不得出现裸 select 规则");
        assert.ok(S2_CSS.includes(".contact-timing-dialog .contact-timing-help"), "help 文本按作用域着色");
        assert.ok(S2_CSS.includes(".contact-timing-dialog .contact-timing-error"), "error 文本按作用域着色");
        assert.ok(S2_CSS.includes(".contact-timing-dialog .button:disabled"), "复用 .button 并局部补 disabled");
    });
});

describe("S-1/S-2: 新 class 声明与字节锁定的 mailbox-chat.css", () => {
    const TIMING_CLASSES = [
        "contact-timing", "contact-timing-location", "contact-timing-recommend", "contact-timing-info",
        "contact-timing-note", "contact-timing-dialog", "contact-timing-close", "contact-timing-body",
        "contact-timing-field", "contact-timing-help", "contact-timing-error", "contact-timing-range",
        "contact-timing-history"
    ];

    it("全部新 class 已在 styles.css 声明", () => {
        TIMING_CLASSES.forEach((cls) => {
            assert.ok(new RegExp(`\\.${cls}(?=[\\s,{.:\\[])`).test(stylesSource), `${cls} 必须在 styles.css 声明`);
        });
    });

    it("mailbox-chat.css 与 02 evidence 基线字节一致且不含 contact-timing", () => {
        assert.strictEqual(chatCssSource, fs.readFileSync(CHAT_CSS_BASELINE, "utf-8"), "mailbox-chat.css 必须逐字等于字节基线");
        assert.ok(chatCssSource.indexOf("contact-timing") === -1, "本功能样式不得写入 mailbox-chat.css");
    });

    it("S-2 复用既有 .button/.primary/.mc-text-button，不新增按钮基类", () => {
        assert.ok(/\.button\.primary\s*\{/.test(stylesSource), "复用既有 .button.primary");
        assert.ok(chatCssSource.includes(".mail-chat .mc-text-button{"), "复用既有 .mc-text-button");
        assert.ok(!/\.contact-timing-(primary|button)\{/.test(stylesSource), "不得另造按钮类");
    });
});

describe("S-1/S-2/S-3: 模板卫生与骨架属性", () => {
    it("mailbox-chat.js 模板无 inline style", () => {
        assert.ok(!/"style="/.test(chatSource), "不得出现 style 属性");
        assert.ok(!/'style='/.test(chatSource), "不得出现 style 属性");
    });

    it("dialog 由 createElement 建立后挂 body，绝不出现在 innerHTML 模板中", () => {
        assert.ok(chatSource.includes('doc.createElement("dialog")'), "必须用 createElement(\"dialog\") 建立自有弹窗");
        assert.ok(chatSource.includes("doc.body.appendChild(node)"), "自有弹窗必须直接挂在 document.body");
        assert.ok(!/<dialog class="contact-timing-dialog"/.test(chatSource), "禁止在 innerHTML 里写 dialog（会形成嵌套 dialog）");
        assert.ok(chatSource.includes('node.setAttribute("class", "contact-timing-dialog")'), "弹窗 class 由 setAttribute 写入");
        assert.ok(!/class="[^"]*\bmail-chat\b[^"]*contact-timing-dialog/.test(chatSource), "弹窗不得带 .mail-chat（grid/高度规则会破坏浮层）");
    });

    it("S-1 骨架：data-role/aria-live、国家入口与依据入口的 data-action 存在", () => {
        assert.ok(chatSource.includes('data-role="contact-timing" aria-live="polite"'), "S-1 组骨架逐字");
        assert.ok(chatSource.includes('class="mc-text-button contact-timing-location" type="button" data-action="mc-contact-location" aria-haspopup="dialog"'));
        assert.ok(chatSource.includes('class="mc-text-button contact-timing-info" type="button" data-action="mc-contact-timing-evidence" aria-label="查看推荐依据" aria-haspopup="dialog"'));
        assert.ok(chatSource.includes('<span class="contact-timing-note">'), "S-1 注记骨架");
        assert.ok(chatSource.includes('data-action="mc-contact-timing-retry">重试</button>'), "S-1 重试按钮骨架");
        ["mc-contact-location", "mc-contact-timing-evidence", "mc-contact-timing-retry"]
            .forEach((action) => assert.ok(chatSource.includes(`action === "${action}"`), `${action} 必须接入事件分派`));
    });

    it("S-2 骨架：表单/字段名/关闭入口与文案逐字存在", () => {
        assert.ok(chatSource.includes('data-role="contact-location-form"'), "配置表单骨架");
        assert.ok(chatSource.includes('name="countryCode" required'), "国家字段骨架");
        assert.ok(chatSource.includes('name="zoneId"'), "时区字段骨架");
        assert.ok(chatSource.includes('data-role="contact-zone-field"'), "时区字段容器骨架");
        assert.ok(chatSource.includes('data-role="contact-default-zone"'), "默认时区提示骨架");
        assert.ok(chatSource.includes('data-role="contact-history-limit"'), "截断提示骨架");
        assert.ok(chatSource.includes("样本不足时按当地 08:00–17:00 推荐，保存后显示北京时间。"));
        assert.ok(chatSource.includes("仅使用最近 1000 条范围内的去重来信。"));
        assert.ok(chatSource.includes("基于全部业务账号的已关联来信；最近 180 天，近期记录权重更高。"));
        assert.ok(chatSource.includes("配置所在地"), "未配置文案");
        assert.ok(chatSource.includes("建议北京 "), "推荐前缀文案");
        assert.ok(chatSource.includes("推荐计算中…") && chatSource.includes("推荐暂不可用"), "加载/错误文案");
        assert.ok(chatSource.includes("所在地已保存，推荐更新失败"), "保存成功但重读失败文案");
        assert.ok(chatSource.includes('class="button contact-timing-close" type="button" data-action="mc-contact-dialog-close"'), "关闭按钮骨架");
    });

    it("时区一律显式指定，代码不把 ISO 串交给设备默认时区", () => {
        assert.ok(chatSource.includes('const CONTACT_TIMING_BEIJING_ZONE = "Asia/Shanghai"'), "北京时区常量必须是 Asia/Shanghai");
        assert.ok(chatSource.includes("timeZone: String(zoneId)"), "格式化必须显式传入时区");
        assert.ok(chatSource.includes("hourCycle: \"h23\""), "小时必须是稳定 24 小时制");
        assert.ok(chatSource.includes('"次日"'), "跨北京日期末端必须显示次日");
        const timingRegion = chatSource.slice(
            chatSource.indexOf("// 联系时间（fast-p 2026-10-02 c3 · I-1..I-6"),
            chatSource.indexOf("// 会议排期（fast-p 03")
        );
        assert.ok(timingRegion.length > 0, "必须能切出联系时间代码区域");
        const timingCodeOnly = timingRegion
            .split("\n")
            .filter((line) => !/^\s*(\/\/|\*|\/\*)/.test(line))
            .join("\n");
        assert.ok(!/localStorage|sessionStorage/.test(timingCodeOnly), "联系时间代码不得读写浏览器存储");
    });
});

describe("资源版本：11 个键同值且无旧键残留", () => {
    it("全部 ?v= 同值，数量与位置不变，键格式合法", () => {
        const keys = Array.from(indexSource.matchAll(/\?v=([^"'&<>\s]+)/g)).map((match) => match[1]);
        assert.strictEqual(keys.length, 11, "11 个带版本资源（5 CSS + 6 JS）");
        assert.deepStrictEqual(Array.from(new Set(keys)), [CACHE_KEY], "全部资源键必须同值");
        assert.match(CACHE_KEY, /^\d{8}-[a-z0-9-]+$/, "键必须是 <yyyymmdd>-<slug>");
        assert.notStrictEqual(CACHE_KEY, RETIRED_CACHE_KEY, "本次实施必须换键（新键值以 index.html 为唯一来源）");
        assert.ok(CACHE_KEY.slice(0, 8) >= "20261002", "资源日期不得早于联系时间样式实施日期");
        assert.ok(indexSource.indexOf(RETIRED_CACHE_KEY) === -1, "不得残留旧键");
        assert.strictEqual(
            (indexSource.match(/href="styles\.css\?v=/g) || []).length, 1,
            "styles.css 标签数量与位置不变"
        );
        assert.strictEqual((indexSource.match(/src="mailbox-chat\.js\?v=/g) || []).length, 1);
        assert.strictEqual((indexSource.match(/src="app\.js\?v=/g) || []).length, 1);
    });
});
