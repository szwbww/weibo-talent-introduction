const fs = require("fs");
const path = require("path");
const vm = require("vm");
const assert = require("assert");
const { describe, it } = require("node:test");

const staticDir = path.join(__dirname, "..", "..", "main", "resources", "static");
const appJs = fs.readFileSync(path.join(staticDir, "app.js"), "utf-8");
const stylesCss = fs.readFileSync(path.join(staticDir, "styles.css"), "utf-8");
const mailboxChatJs = fs.readFileSync(path.join(staticDir, "mailbox-chat.js"), "utf-8");
const indexHtml = fs.readFileSync(path.join(staticDir, "index.html"), "utf-8");

function sourceThrough(name) {
    const start = appJs.indexOf(`function ${name}(`);
    if (start < 0) throw new Error(`Could not find function ${name}`);
    const openBrace = appJs.indexOf("{", appJs.indexOf(")", start));
    let depth = 0;
    for (let index = openBrace; index < appJs.length; index += 1) {
        if (appJs[index] === "{") {
            depth += 1;
        } else if (appJs[index] === "}") {
            depth -= 1;
            if (depth === 0) return appJs.slice(start, index + 1);
        }
    }
    throw new Error(`Could not close function ${name}`);
}

function sandbox() {
    const context = {
        Set,
        String,
        RegExp,
        btoa: (text) => Buffer.from(text, "binary").toString("base64"),
        atob: (text) => Buffer.from(text, "base64").toString("binary"),
        encodeURIComponent,
        decodeURIComponent,
        escape,
        unescape
    };
    vm.createContext(context);
    vm.runInContext(sourceThrough("escapeHtml"), context);
    vm.runInContext(appJs.match(/const MICROSOFT_FILE_CARD = [^;]+;/)[0], context);
    vm.runInContext(appJs.match(/const SHAREPOINT_FILE_URL = [^;]+;/)[0], context);
    for (const name of ["isSharePointFileUrl", "extractMailExternalFileCards", "renderMailBody", "encodeTranslateSrc", "translatableBody"]) {
        vm.runInContext(sourceThrough(name), context);
    }
    return context;
}

const CARD_URL = "https://agrkfsedu-my.sharepoint.com/:u:/g/personal/aziza_aboulila_agr_kfs_edu_eg/example";
const CARD = `remaini\u200B[https://res.public.onecdn.static.microsoft/assets/fluentui-resources/1.1.0/app-min/assets/item-types/24/archive.png]Publications 3.rar<${CARD_URL}>\u200Bng documents`;

describe("SharePoint 文件卡展示", () => {
    it("将有效文件卡移出正文并恢复被切断单词", () => {
        const view = sandbox();
        const rendered = view.renderMailBody(CARD, true);

        assert.match(rendered, /remaining documents/);
        assert.match(rendered, /附件链接：/);
        assert.match(rendered, /Publications 3\.rar/);
        assert.match(rendered, /href="https:\/\/agrkfsedu-my\.sharepoint\.com/);
        assert.match(rendered, /target="_blank"/);
        assert.match(rendered, /rel="noopener noreferrer"/);
        assert.ok(!rendered.includes("remaini\u200B"));
    });

    it("保持翻译输入和未启用的普通正文不变", () => {
        const view = sandbox();
        const plain = view.renderMailBody(CARD, false);
        const translated = view.translatableBody(CARD, { externalFileLinks: true });

        assert.match(plain, /Publications 3\.rar/);
        assert.ok(!plain.includes("mail-external-file-link"));
        assert.match(translated, /data-translate-src="/);
        assert.match(translated, /mail-external-file-link/);
    });

    it("拒绝非 HTTPS SharePoint、伪造主机和注入型文件名", () => {
        const view = sandbox();
        for (const url of [
            "http://tenant.sharepoint.com/file",
            "https://tenant.sharepoint.com.evil/file",
            "https://user@tenant.sharepoint.com/file"
        ]) {
            const card = CARD.replace(CARD_URL, url);
            const rendered = view.renderMailBody(card, true);
            assert.ok(!rendered.includes("mail-external-file-link"), url);
            assert.ok(rendered.includes(`&lt;${url}`), url);
        }
        const escaped = view.renderMailBody(CARD.replace("Publications 3.rar", '<img src=x onerror=alert(1)>'), true);
        assert.ok(!escaped.includes("<img src=x"));
        assert.match(escaped, /&lt;img src=x/);
    });

    it("仅邮件展示点启用文件卡转换", () => {
        const enabled = (appJs.match(/externalFileLinks: true/g) || []).length;
        assert.equal(enabled, 6);
        assert.match(sourceThrough("renderMailItem"), /externalFileLinks: true/);
        assert.match(appJs, /translatableBody\(rendered\)/, "AI 草稿仍使用默认纯文本展示");
    });

    it("邮件箱聊天视图复用安全的文件卡展示并更新缓存版本", () => {
        assert.match(mailboxChatJs, /function messageDisplayHtml\(text\)[\s\S]*global\.renderMailBody\(text, true\)/);
        assert.match(mailboxChatJs, /mc-body">\$\{messageDisplayHtml\(displayBody\)\}/);
        assert.match(indexHtml, /mailbox-chat\.js\?v=20260919-sharepoint-file-card-display/);
    });

    it("链接样式遵守现有正文主色契约", () => {
        assert.ok(stylesCss.includes(`.mail-external-file-link {\n    color: var(--primary);\n    font-weight: 600;\n    text-decoration: underline;\n    text-underline-offset: 2px;\n    overflow-wrap: anywhere;\n}`));
        assert.ok(stylesCss.includes(`.mail-external-file-link:hover {\n    color: var(--primary-hover);\n}`));
    });
});
