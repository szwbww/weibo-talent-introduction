"use strict";

// fast-p 04 样式契约测试（S-1/I-6）：
// 1) 落地 meeting-confirmation.css 与 evidence/meeting-confirmation.target.css
//    逐字（字节）一致 —— 不增一行、不减一行、不重排。
// 2) DOM class 白名单：meeting-confirmation.js / mailbox-chat.js（会议模板）中
//    的字面量 class 必须全部命中 S-1 CSS / styles.css / mailbox-chat.css；
//    未列出的新 class、inline style、全局 p/button 污染一律禁止。
// 3) 独立资源文件：组件/CSS 均为独立文件；index.html 的注册（统一键/顺序/无重复）
//    由 meetingConfirmationAssets.test.js 覆盖（A2：退役本文件未注册断言）。
// 4) 不引入 rev 类/导航/预览 mock/fetch 拦截痕迹；无 element.style 写入。

const fs = require("fs");
const path = require("path");
const assert = require("assert");
const { describe, it } = require("node:test");

const ROOT = path.join(__dirname, "..", "..", "main", "resources", "static");
const cssPath = path.join(ROOT, "meeting-confirmation.css");
const cssSource = fs.readFileSync(cssPath, "utf-8");
const componentSource = fs.readFileSync(path.join(ROOT, "meeting-confirmation.js"), "utf-8");
const chatSource = fs.readFileSync(path.join(ROOT, "mailbox-chat.js"), "utf-8");
const mailboxChatCss = fs.readFileSync(path.join(ROOT, "mailbox-chat.css"), "utf-8");
const stylesSource = fs.readFileSync(path.join(ROOT, "styles.css"), "utf-8");

const TARGET_CSS = path.join(__dirname, "..", "..", "..", "docs", "plans", "2026-09-09", "meeting-confirmation-evidence", "meeting-confirmation.target.css");

function literalClasses(source) {
    const out = new Set();
    const re = /class="([^"]*)"/g;
    let match;
    while ((match = re.exec(source)) !== null) {
        String(match[1]).split(/\s+/).filter(Boolean).forEach((cls) => out.add(cls));
    }
    const singleRe = /class='([^']*)'/g;
    while ((match = singleRe.exec(source)) !== null) {
        String(match[1]).split(/\s+/).filter(Boolean).forEach((cls) => out.add(cls));
    }
    return out;
}

function declaredIn(source, cls) {
    return new RegExp(`\\.${cls}(?=[\\s,{.:\\[])`).test(source);
}

describe("S-1: 落地 CSS 与 04 evidence 逐字（字节）一致", () => {
    it("meeting-confirmation.css 与 evidence/meeting-confirmation.target.css 字节一致", () => {
        const target = fs.readFileSync(TARGET_CSS, "utf-8");
        assert.strictEqual(cssSource, target);
    });

    it("文件是独立资源（index.html 注册检查见 meetingConfirmationAssets.test.js）", () => {
        assert.ok(fs.existsSync(cssPath), "CSS 文件独立存在");
        assert.ok(fs.existsSync(path.join(ROOT, "meeting-confirmation.js")), "组件文件独立存在");
    });
});

describe("S-1/I-6: DOM class 白名单与模板卫生", () => {
    it("meeting-confirmation.js 模板字面量 class 全部命中 S-1/既有样式", () => {
        const classes = literalClasses(componentSource);
        assert.ok(classes.size > 0, "must find template classes to audit");
        const unknown = [];
        classes.forEach((cls) => {
            if (/\$/.test(cls)) return;
            if (declaredIn(cssSource, cls)) return;
            if (declaredIn(stylesSource, cls)) return;
            if (declaredIn(mailboxChatCss, cls)) return;
            unknown.push(cls);
        });
        assert.deepStrictEqual(unknown, [], "组件模板 class 必须已在样式契约中声明");
    });

    it("mailbox-chat.js 会议模板的字面量 class 不越过旧样式白名单（meeting-* 只经运行时拼接）", () => {
        const classes = literalClasses(chatSource);
        classes.forEach((cls) => {
            if (/\$/.test(cls)) return;
            assert.ok(!String(cls).startsWith("meeting-"), `mailbox-chat.js 不得出现字面量 meeting- class：${cls}`);
        });
    });

    it("新文件模板不写 inline style；宿主不写 element.style", () => {
        assert.ok(!/"style="/.test(componentSource), "meeting-confirmation.js 模板不得出现 style 属性");
        assert.ok(!/'style='/.test(componentSource), "meeting-confirmation.js 模板不得出现 style 属性");
        assert.ok(!/\.style\s*=[^=]/.test(componentSource), "组件不得写 element.style");
        assert.ok(!/\.style\s*=[^=]/.test(chatSource), "mailbox-chat.js 宿主不得写 element.style");
        assert.ok(!/"style="/.test(chatSource), "mailbox-chat.js 模板不得出现 style 属性");
    });

    it("S-1 不复制预览导航/rev 类/mock 数据/fetch 拦截", () => {
        assert.ok(!cssSource.includes(".rev-"), "CSS 不含预览 rev 类");
        assert.ok(!cssSource.includes("#manual"), "CSS 不含预览 #manual 范围");
        assert.ok(!componentSource.includes("fetch("), "组件不直接 fetch/拦截");
    });

    it("既有 mailbox-chat.css 未被会议规则污染（独立 CSS 边界）", () => {
        assert.ok(!mailboxChatCss.includes(".meeting-"), "mailbox-chat.css 不含会议 class");
        assert.ok(!stylesSource.includes(".meeting-dialog"), "全局 styles.css 不含会议弹窗规则");
        // 会议标记 class 全部经 ${mcCls(...)} 动态拼接（模板字面量含 $，被旧白名单跳过）
        assert.ok(/class="\$\{mcCls\(/.test(chatSource), "会议标记必须以 ${mcCls(...)} 模板字面量出现");
        assert.ok(!chatSource.includes('class="meeting-'), "不得出现字面量 meeting- class");
    });
});

describe("S-2/S-3/S-4/S-5: 关键 id 与 data-role 源文本存在性（DOM stub 防悬空）", () => {
    const COMPONENT_IDS = [
        "id=\"meetingForm\"", "id=\"meetingTitle\"", "id=\"meetingContext\"",
        "id=\"closeMeeting\"", "id=\"meetingLoadStatus\"", "id=\"retryMeeting\"", "id=\"meetingTemplate\"",
        "id=\"templateDetails\"", "id=\"templateText\"", "id=\"resetTemplate\"", "id=\"meetingName\"",
        "id=\"meetingZoneLabel\"", "id=\"meetingZoneSearch\"", "id=\"toggleZone\"", "id=\"meetingZoneOptions\"",
        "id=\"meetingZoneHint\"", "id=\"meetingDate\"", "id=\"meetingStart\"", "id=\"meetingEndDate\"",
        "id=\"meetingEnd\"", "id=\"meetingClock\"", "id=\"meetingUrl\"", "id=\"meetingSignature\"",
        "id=\"insertModeLabel\"", "id=\"insertMode\"", "id=\"meetingError\"", "id=\"meetingBody\"",
        "id=\"meetingFilename\"", "id=\"meetingFileMeta\"", "id=\"downloadMeeting\"", "id=\"inspectIcs\"",
        "id=\"meetingRaw\"", "id=\"cancelMeeting\"", "id=\"applyMeeting\""
    ];
    const HOST_ROLES = [
        "data-action=\"mc-open-meeting\"", "data-role=\"meeting-attachment\"", "data-action=\"mc-edit-meeting\"",
        "data-action=\"mc-remove-meeting\"", "data-action=\"mc-download-meeting\"", "data-action=\"mc-download-sent-meeting\"",
        "data-role=\"sent-meeting-attachment\"", "data-meeting-block=\"true\"", "data-role=\"calendar-download\""
    ];

    it("S-2 组件关键 id 在源文本唯一存在", () => {
        assert.ok(/setAttribute\("id",\s*"meetingDialog"\)/.test(componentSource), "dialog id 经 setAttribute 唯一设置");
        COMPONENT_IDS.forEach((needle) => {
            const count = componentSource.split(needle).length - 1;
            assert.ok(count >= 1, `${needle} 必须在 meeting-confirmation.js 中存在`);
        });
    });

    it("S-3/S-5 宿主 data-action/data-role 在 mailbox-chat.js 源文本存在", () => {
        HOST_ROLES.forEach((needle) => {
            assert.ok(chatSource.includes(needle), `${needle} 必须在 mailbox-chat.js 中存在`);
        });
    });

    it("S-4 时区候选结构与文案在组件源文本存在", () => {
        assert.ok(componentSource.includes("role=\"option\""), "候选项 role=option");
        assert.ok(componentSource.includes("meeting-zone-option-"), "候选 id 前缀");
        assert.ok(componentSource.includes("没有匹配的时区，请尝试英文城市名或 UTC+3。"), "空结果固定文案");
        assert.ok(componentSource.includes("aria-activedescendant"), "键盘 active 跟随 input");
    });

    it("空结果/加载/错误/ready/stale/sending 均有确定文案源", () => {
        assert.ok(componentSource.includes("正在生成邮件与日历…"));
        assert.ok(componentSource.includes("请填写左侧配置后预览邮件。"));
        assert.ok(componentSource.includes("请修正左侧配置后预览邮件。"));
        assert.ok(componentSource.includes("会议配置加载失败，请重试"));
        assert.ok(componentSource.includes("预览生成失败，请重试"));
        assert.ok(componentSource.includes("会议时间已过去，请核对"));
        assert.ok(chatSource.includes("待重新确认"));
        assert.ok(chatSource.includes("已发送日历"));
        assert.ok(chatSource.includes("data-meeting-sending"));
    });
});
