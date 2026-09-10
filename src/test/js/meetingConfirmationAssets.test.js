"use strict";

// fast-p 05 资源激活测试（T3；I-1/I-2/S-1）：
// 1) index.html 恰好 9 个带 ?v= 资源（7 旧 + meeting-confirmation.css/.js），全部同值
//    20260910-mailbox-spacing，无重复注册、无旧键残留；
// 2) S-1 注册顺序：meeting CSS 紧跟 mailbox-chat.css 之后；meeting JS 在 mailbox-chat.js
//    与 app.js 之前；link 全在 head、script 全在 body；task-modal-runtime.js 保持未版本化原位；
// 3) 组件已引用且不注入样例/预览 mock 数据：注册行仅相对路径 + 单一版本查询串；
//    组件文件不携带 sample/mock/fixture/demo 数据或 fetch 改写痕迹（I-2）。

const fs = require("fs");
const path = require("path");
const assert = require("assert");
const { describe, it } = require("node:test");

const ROOT = path.join(__dirname, "..", "..", "main", "resources", "static");
const indexPath = path.join(ROOT, "index.html");
const html = fs.readFileSync(indexPath, "utf-8");
const meetingSource = fs.readFileSync(path.join(ROOT, "meeting-confirmation.js"), "utf-8");

const CACHE_KEY = "20260910-mailbox-spacing";
const CSS_ORDER = ["styles.css", "expert-materials.css", "mailbox-chat.css", "meeting-confirmation.css"];
const JS_ORDER = ["trust-reply-workbench.js", "expert-materials.js", "meeting-confirmation.js",
    "mailbox-chat.js", "app.js"];
const ALL_ASSETS = [...CSS_ORDER, ...JS_ORDER];

describe("T3: 9 个带版本资源统一键与注册（I-1/S-1）", () => {
    it("恰好 9 个带 ?v= 资源且全部等于 20260910-mailbox-spacing，无旧键残留", () => {
        const keys = [...html.matchAll(/\?v=([0-9a-z-]+)/g)].map((match) => match[1]);
        assert.strictEqual(keys.length, 9, `index.html 必须恰好注册 9 个带版本资源，实际 ${keys.length}`);
        assert.ok(keys.every((key) => key === CACHE_KEY), `全部键必须等于 ${CACHE_KEY}: ${keys}`);
        assert.ok(!html.includes("20260909-mailbox-refinement"),
            "上一缓存键 20260909-mailbox-refinement 必须 0 命中 index.html");
    });

    it("9 个资源 = 7 旧资源 + meeting-confirmation.css/.js，各恰好注册 1 次（无重复）", () => {
        const refs = [];
        const linkRe = /<link rel="stylesheet" href="([^"]+\.css)\?v=([0-9a-z-]+)">/g;
        const scriptRe = /<script src="([^"]+\.js)\?v=([0-9a-z-]+)"><\/script>/g;
        let match;
        while ((match = linkRe.exec(html)) !== null) refs.push({ tag: "link", name: match[1] });
        while ((match = scriptRe.exec(html)) !== null) refs.push({ tag: "script", name: match[1] });
        assert.strictEqual(refs.length, 9, "link+script 带版本注册行合计必须为 9");
        for (const asset of ALL_ASSETS) {
            const hits = refs.filter((ref) => ref.name === asset).length;
            assert.strictEqual(hits, 1, `${asset} 必须恰好注册 1 次，实际 ${hits}`);
        }
        // 无重名：9 个名字互不重复
        assert.strictEqual(new Set(refs.map((ref) => ref.name)).size, 9,
            "9 个注册资源名不得重复");
    });

    it("link 全在 head、script 全在 body；task-modal-runtime.js 未版本化且位于组件脚本之前", () => {
        const headEnd = html.indexOf("</head>");
        const bodyEnd = html.lastIndexOf("</body>");
        assert.ok(headEnd > 0 && bodyEnd > headEnd, "index.html 结构异常");
        const linkRe = /<link rel="stylesheet" href="[^"]+\.css\?v=20260910-mailbox-spacing">/g;
        const scriptRe = /<script src="[^"]+\.js\?v=20260910-mailbox-spacing"><\/script>/g;
        let match;
        while ((match = linkRe.exec(html)) !== null) {
            assert.ok(match.index < headEnd, "样式 link 必须位于 head 内");
        }
        while ((match = scriptRe.exec(html)) !== null) {
            assert.ok(match.index > headEnd && match.index < bodyEnd, "脚本必须位于 body 内");
        }
        const runtimeAt = html.indexOf('<script src="task-modal-runtime.js"></script>');
        const trustAt = html.indexOf("trust-reply-workbench.js?v=" + CACHE_KEY);
        assert.ok(runtimeAt > 0 && runtimeAt < trustAt,
            "task-modal-runtime.js 必须保持未版本化且位于组件脚本之前（原位不动）");
        assert.ok(!html.includes("task-modal-runtime.js?v="),
            "task-modal-runtime.js 不得携带版本键");
    });

    it("CSS 顺序：meeting-confirmation.css 紧跟 mailbox-chat.css 之后（S-1）", () => {
        let previous = -1;
        for (const asset of CSS_ORDER) {
            const at = html.indexOf(`${asset}?v=${CACHE_KEY}`);
            assert.ok(at > previous,
                `CSS 顺序违规：${asset} 必须注册在上一 CSS 之后（meeting CSS 在 mailbox-chat.css 之后）`);
            previous = at;
        }
    });

    it("脚本顺序：meeting-confirmation.js 在 mailbox-chat.js 之前、app.js 之前（S-1）", () => {
        let previous = -1;
        for (const asset of JS_ORDER) {
            const at = html.indexOf(`${asset}?v=${CACHE_KEY}`);
            assert.ok(at > previous,
                `脚本顺序违规：${asset} 必须注册在上一脚本之后（meeting JS 在 mailbox-chat.js/app.js 之前）`);
            previous = at;
        }
    });
});

describe("T3: 组件被引用且不注入样例/预览 mock 数据（I-2）", () => {
    it("meeting-confirmation.css/.js 均以统一键注册且作为独立文件存在", () => {
        assert.ok(fs.existsSync(path.join(ROOT, "meeting-confirmation.css")),
            "meeting-confirmation.css 文件必须独立存在");
        assert.ok(fs.existsSync(path.join(ROOT, "meeting-confirmation.js")),
            "meeting-confirmation.js 文件必须独立存在");
        assert.ok(html.includes(`meeting-confirmation.css?v=${CACHE_KEY}`),
            "index.html 必须注册组件样式");
        assert.ok(html.includes(`meeting-confirmation.js?v=${CACHE_KEY}`),
            "index.html 必须注册组件脚本");
    });

    it("meeting 注册行仅相对路径 + 单一版本查询串，无样例/预览 mock 数据引用（I-2）", () => {
        const registrationLines = html.split("\n").filter((line) =>
            line.includes("meeting-confirmation.css?v=") || line.includes("meeting-confirmation.js?v="));
        assert.strictEqual(registrationLines.length, 2,
            `meeting-confirmation 注册行必须恰为 2 行（css+js），实际 ${registrationLines.length}`);
        for (const line of registrationLines) {
            assert.doesNotMatch(line, /sample|mock|preview|fixture|demo|data:/i,
                `注册行不得引用样例/预览 mock 数据: ${line.trim()}`);
            assert.ok(line.includes(`?v=${CACHE_KEY}`), `注册行必须携带统一键: ${line.trim()}`);
            assert.doesNotMatch(line, /&[a-z]+=/, `注册行不得含额外查询参数: ${line.trim()}`);
        }
    });

    it("组件脚本自身不携带样例/模拟数据或 fetch 改写痕迹（I-2）", () => {
        assert.doesNotMatch(meetingSource, /\bsample\b|\bmock\b|\bfixture\b|\bdemo\b/i,
            "meeting-confirmation.js 不得内嵌样例/模拟数据标识");
        assert.ok(!/window\.fetch\s*=|fetch\s*=\s*\(|overrideFetch/.test(meetingSource),
            "meeting-confirmation.js 不得改写/拦截 fetch");
        assert.ok(!/data:(text|application)\//.test(meetingSource),
            "meeting-confirmation.js 不得内嵌 data: 注入载荷");
    });
});
