"use strict";

// fast-p 05 资源激活测试（T3；I-1/I-2/S-1）+ 02 注册回归：
// 1) index.html 恰好 11 个带 ?v= 资源（7 旧 + meeting-confirmation.css/.js 与
//    world-clock.css/.js），全部同值 20260917-global-world-clock，无重复注册；
// 2) S-1 注册顺序：meeting CSS 紧跟 mailbox-chat.css 之后；meeting JS 在 mailbox-chat.js
//    与 app.js 之前；world-clock CSS/JS 分别位于最后；link 全在 head、script 全在 body；
//    task-modal-runtime.js 保持未版本化原位；
// 3) 组件已引用且不注入样例/预览 mock 数据：注册行仅相对路径 + 单一版本查询串；
//    组件文件不携带 sample/mock/fixture/demo 数据或 fetch 改写痕迹（I-2）；
// 4) 02：新资源各注册一次且顺序正确；顶栏删除轮询日志入口后 logout/currentUserDisplay/
//    pollLogPanel/closePollLogPanelBtn 仍在，九个 data-view 集合与顺序不变（I-7/S-5）。

const fs = require("fs");
const path = require("path");
const assert = require("assert");
const { describe, it } = require("node:test");

const ROOT = path.join(__dirname, "..", "..", "main", "resources", "static");
const indexPath = path.join(ROOT, "index.html");
const html = fs.readFileSync(indexPath, "utf-8");
const meetingSource = fs.readFileSync(path.join(ROOT, "meeting-confirmation.js"), "utf-8");

const CACHE_KEY = "20260917-global-world-clock";
const CSS_ORDER = ["styles.css", "expert-materials.css", "mailbox-chat.css", "meeting-confirmation.css",
    "world-clock.css"];
const JS_ORDER = ["trust-reply-workbench.js", "expert-materials.js", "meeting-confirmation.js",
    "mailbox-chat.js", "app.js", "world-clock.js"];
const ALL_ASSETS = [...CSS_ORDER, ...JS_ORDER];

describe("T3: 11 个带版本资源统一键与注册（I-1/S-1）", () => {
    it("恰好 11 个带 ?v= 资源且全部等于 20260917-global-world-clock，无旧键残留", () => {
        const keys = [...html.matchAll(/\?v=([0-9a-z-]+)/g)].map((match) => match[1]);
        assert.strictEqual(keys.length, 11, `index.html 必须恰好注册 11 个带版本资源，实际 ${keys.length}`);
        assert.ok(keys.every((key) => key === CACHE_KEY), `全部键必须等于 ${CACHE_KEY}: ${keys}`);
        assert.ok(!html.includes("20260910-mailbox-spacing"),
            "上一缓存键 20260910-mailbox-spacing 必须 0 命中 index.html");
    });

    it("11 个资源 = 9 旧资源 + world-clock.css/.js，各恰好注册 1 次（无重复）", () => {
        const refs = [];
        const linkRe = /<link rel="stylesheet" href="([^"]+\.css)\?v=([0-9a-z-]+)">/g;
        const scriptRe = /<script src="([^"]+\.js)\?v=([0-9a-z-]+)"><\/script>/g;
        let match;
        while ((match = linkRe.exec(html)) !== null) refs.push({ tag: "link", name: match[1] });
        while ((match = scriptRe.exec(html)) !== null) refs.push({ tag: "script", name: match[1] });
        assert.strictEqual(refs.length, 11, "link+script 带版本注册行合计必须为 11");
        for (const asset of ALL_ASSETS) {
            const hits = refs.filter((ref) => ref.name === asset).length;
            assert.strictEqual(hits, 1, `${asset} 必须恰好注册 1 次，实际 ${hits}`);
        }
        // 无重名：11 个名字互不重复
        assert.strictEqual(new Set(refs.map((ref) => ref.name)).size, 11,
            "11 个注册资源名不得重复");
    });

    it("link 全在 head、script 全在 body；task-modal-runtime.js 未版本化且位于组件脚本之前", () => {
        const headEnd = html.indexOf("</head>");
        const bodyEnd = html.lastIndexOf("</body>");
        assert.ok(headEnd > 0 && bodyEnd > headEnd, "index.html 结构异常");
        const linkRe = /<link rel="stylesheet" href="[^"]+\.css\?v=20260917-global-world-clock">/g;
        const scriptRe = /<script src="[^"]+\.js\?v=20260917-global-world-clock"><\/script>/g;
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

describe("T4: 02 注册新资源与顶栏入口回归（I-7/I-8/S-5/S-6）", () => {
    const versionedRefs = () => {
        const refs = [];
        const linkRe = /<link rel="stylesheet" href="([^"]+\.css)\?v=([0-9a-z-]+)">/g;
        const scriptRe = /<script src="([^"]+\.js)\?v=([0-9a-z-]+)"><\/script>/g;
        let match;
        while ((match = linkRe.exec(html)) !== null) refs.push({ tag: "link", name: match[1], key: match[2] });
        while ((match = scriptRe.exec(html)) !== null) refs.push({ tag: "script", name: match[1], key: match[2] });
        return refs;
    };

    it("新资源各自作为独立文件存在、以正确标签注册 1 次且携带统一键（I-8/S-6）", () => {
        for (const [name, tag] of [["world-clock.css", "link"], ["world-clock.js", "script"]]) {
            assert.ok(fs.existsSync(path.join(ROOT, name)), `${name} 必须作为独立文件存在`);
            const hits = versionedRefs().filter((ref) => ref.name === name);
            assert.strictEqual(hits.length, 1, `${name} 必须恰好注册 1 次，实际 ${hits.length}`);
            assert.strictEqual(hits[0].tag, tag, `${name} 必须以 ${tag} 注册`);
            assert.strictEqual(hits[0].key, CACHE_KEY, `${name} 必须携带统一键 ${CACHE_KEY}`);
        }
    });

    it("world-clock.css 在最后一个旧 CSS 之后；world-clock.js 在 app.js 之后且为最后一个带版本脚本（I-8/S-6）", () => {
        const lastOldCssAt = html.indexOf(`meeting-confirmation.css?v=${CACHE_KEY}`);
        const clockCssAt = html.indexOf(`world-clock.css?v=${CACHE_KEY}`);
        assert.ok(lastOldCssAt > -1 && clockCssAt > lastOldCssAt,
            "world-clock.css 必须注册在最后一个旧 CSS（meeting-confirmation.css）之后");
        const appAt = html.indexOf(`app.js?v=${CACHE_KEY}`);
        const clockJsAt = html.indexOf(`world-clock.js?v=${CACHE_KEY}`);
        assert.ok(appAt > -1 && clockJsAt > appAt, "world-clock.js 必须注册在 app.js 之后");
        const lastScript = versionedRefs().filter((ref) => ref.tag === "script").pop();
        assert.strictEqual(lastScript.name, "world-clock.js", "world-clock.js 必须是最后一个带版本脚本");
        const scriptAt = html.indexOf(`meeting-confirmation.js?v=${CACHE_KEY}`);
        assert.ok(scriptAt > -1 && scriptAt < appAt,
            "meeting-confirmation.js 仍必须位于 app.js 之前（相对依赖顺序不变）");
    });

    it("删除轮询日志入口但保留用户名/退出/日志面板（I-7/S-5）", () => {
        assert.ok(!html.includes("showPollLogBtn"), "轮询日志入口 #showPollLogBtn 必须已删除");
        assert.ok(!html.includes("showPollLog()"), "轮询日志按钮的 onclick 调用不得残留在 index.html");
        for (const id of ["currentUserDisplay", "logoutBtn", "pollLogPanel", "pollLogBody", "closePollLogPanelBtn"]) {
            assert.ok(html.includes(`id="${id}"`), `#${id} 必须保留在 index.html`);
        }
    });

    it("顶栏侧栏只剩用户名与退出登录一个按钮，顺序为用户在前（S-5）", () => {
        const sideStart = html.indexOf('<div class="topnav-side">');
        assert.ok(sideStart > -1, "index.html 必须保留 .topnav-side");
        const sideEnd = html.indexOf("</header>", sideStart);
        assert.ok(sideEnd > sideStart, "index.html 结构异常：topnav 未闭合");
        const side = html.slice(sideStart, sideEnd);
        const userAt = side.indexOf('id="currentUserDisplay"');
        const logoutAt = side.indexOf('id="logoutBtn"');
        assert.ok(userAt > -1 && logoutAt > userAt, "顶栏顺序必须为当前用户在前、退出登录在后");
        assert.strictEqual((side.match(/<button/g) || []).length, 1,
            "顶栏侧栏必须只剩退出登录一个按钮（时钟由 01 boot 动态插入）");
        assert.ok(side.includes('<span>退出登录</span>'), "退出登录文案与节点必须保持");
    });

    it("原九个 data-view 导航集合与顺序不变（I-1/S-5）", () => {
        const views = [...html.matchAll(/data-view="([^"]+)"/g)].map((match) => match[1]);
        assert.deepStrictEqual(views, ["monitoring", "accounts", "mail-templates", "suppressions",
            "contacts", "mailbox", "inbound-summary", "ai-training", "tasks"],
            "九个 data-view 导航的集合与顺序不得变化");
    });
});
