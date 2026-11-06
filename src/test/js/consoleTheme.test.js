"use strict";
const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const root = path.join(__dirname, "../../main/resources/static");
const read = file => fs.readFileSync(path.join(root, file), "utf8");

function start({ cookie = "", protocol = "https:", ready = "loading", deniedRead = false, deniedWrite = false, missingButton = false } = {}) {
    const attrs = new Map(), buttonAttrs = new Map(), events = new Map(), clicks = [];
    const writes = [];
    const button = {
        setAttribute: (key, value) => buttonAttrs.set(key, value),
        addEventListener: (event, fn) => { assert.equal(event, "click"); clicks.push(fn); }
    };
    const draft = { value: "尚未发送的回复", selectionStart: 4 }, conversation = { id: 42 };
    const document = {
        readyState: ready,
        documentElement: { setAttribute: (key, value) => attrs.set(key, value), getAttribute: key => attrs.get(key) },
        getElementById(id) { assert.equal(id, "consoleThemeToggle"); return missingButton ? null : button; },
        addEventListener: (name, fn, options) => events.set(name, { fn, options }),
        get cookie() { if (deniedRead) throw new Error("Cookie blocked"); return cookie; },
        set cookie(value) { if (deniedWrite) throw new Error("Cookie blocked"); writes.push(value); cookie = "session=unchanged; " + value.split(";")[0]; }
    };
    const location = { protocol, reload() { assert.fail("theme must not reload"); }, assign() { assert.fail("theme must not navigate"); } };
    vm.runInNewContext(read("console-theme.js"), {
        document, location, window: { location },
        fetch() { assert.fail("theme must not request business data"); }
    });
    return { attrs, buttonAttrs, events, clicks, writes, draft, conversation, document,
        mount() { events.get("DOMContentLoaded")?.fn(); },
        click() { assert.equal(clicks.length, 1); clicks[0](); }
    };
}

test("defaults to light synchronously, before DOM ready, without creating a cookie", () => {
    const page = start();
    assert.equal(page.attrs.get("data-console-theme"), "light");
    assert.equal(page.clicks.length, 0);
    assert.equal(page.events.get("DOMContentLoaded").options.once, true);
    page.mount();
    assert.equal(page.buttonAttrs.get("aria-label"), "切换到黑色主题");
    assert.equal(page.buttonAttrs.get("aria-pressed"), "false");
    assert.equal(page.writes.length, 0);
});

test("restores the exact preference cookie before body is available", () => {
    const page = start({ cookie: "session=keep; other_talent_console_theme=light; talent_console_theme=dark" });
    assert.equal(page.attrs.get("data-console-theme"), "dark");
    page.mount();
    assert.equal(page.buttonAttrs.get("aria-label"), "切换到白色主题");
    assert.equal(page.buttonAttrs.get("title"), "切换到白色主题");
    assert.equal(page.buttonAttrs.get("aria-pressed"), "true");
    assert.equal(page.writes.length, 0);
});

test("invalid, unrelated and inaccessible cookies safely default to light", () => {
    for (const cookie of ["talent_console_theme=system", "talent_console_theme=DARK", "talent_console_theme=%ZZ", "other_talent_console_theme=dark", "talent_console_theme=", "talent_console_theme=dark=bad"]) {
        assert.equal(start({ cookie }).attrs.get("data-console-theme"), "light", cookie);
    }
    assert.equal(start({ deniedRead: true }).attrs.get("data-console-theme"), "light");
});

test("click toggles both directions and persists only its host cookie for one year", () => {
    const page = start({ ready: "complete", cookie: "session=keep" });
    const draft = page.draft, conversation = page.conversation;
    page.click();
    assert.equal(page.attrs.get("data-console-theme"), "dark");
    assert.equal(page.buttonAttrs.get("aria-pressed"), "true");
    assert.match(page.writes[0], /^talent_console_theme=dark;/);
    for (const attribute of ["Path=/", "Max-Age=31536000", "SameSite=Lax", "Secure"]) assert.ok(page.writes[0].includes(attribute));
    assert.ok(!page.writes[0].includes("Domain="));
    assert.equal(start({ cookie: page.document.cookie }).attrs.get("data-console-theme"), "dark");
    page.click();
    assert.equal(page.attrs.get("data-console-theme"), "light");
    assert.equal(page.buttonAttrs.get("aria-label"), "切换到黑色主题");
    assert.match(page.writes[1], /^talent_console_theme=light;/);
    assert.equal(start({ cookie: page.document.cookie }).attrs.get("data-console-theme"), "light");
    assert.equal(page.draft, draft); assert.equal(page.conversation, conversation);
    assert.equal(draft.value, "尚未发送的回复"); assert.equal(draft.selectionStart, 4);
});

test("HTTP preview persists without Secure; unavailable storage does not prevent switching", () => {
    const page = start({ protocol: "http:", ready: "interactive" });
    page.click(); assert.ok(!page.writes[0].includes("Secure"));
    const blocked = start({ ready: "complete", deniedWrite: true });
    blocked.click(); assert.equal(blocked.attrs.get("data-console-theme"), "dark");
    blocked.click(); assert.equal(blocked.attrs.get("data-console-theme"), "light");
    assert.doesNotThrow(() => start({ ready: "complete", missingButton: true }));
});

test("real entry has one accessible control and synchronous pre-paint bootstrap", () => {
    const html = read("index.html");
    assert.match(html, /<html[^>]+data-console-theme="light"/);
    const script = '<script src="console-theme.js?v=';
    assert.equal(html.split(script).length - 1, 1);
    assert.ok(html.indexOf(script) < html.indexOf('<link rel="stylesheet"'));
    const tag = html.match(/<script[^>]+src="console-theme\.js[^>]*>/)[0];
    assert.doesNotMatch(tag, /\b(?:async|defer)\b/);
    assert.equal((html.match(/id="consoleThemeToggle"/g) || []).length, 1);
    const button = html.match(/<button[^>]+id="consoleThemeToggle"[\s\S]*?<\/button>/)[0];
    assert.match(button, /type="button"/); assert.match(button, /aria-pressed="false"/);
    assert.doesNotMatch(button, /style=|nav-tab|data-view/);
    assert.equal((button.match(/aria-hidden="true"/g) || []).length, 2);
    assert.ok(html.indexOf('id="consoleThemeToggle"') < html.indexOf('id="refreshBtn"'));
    assert.ok(html.indexOf('console-theme.css?v=') > html.indexOf('console-shell.css?v='));
    const keys = [...html.matchAll(/(?:href|src)="[^"?]+\.(?:css|js)\?v=([^"&]+)/g)].map(m => m[1]);
    assert.equal(new Set(keys).size, 1);
});

test("OS dark rules exclude explicitly themed documents, preserving their declarations", () => {
    for (const file of ["styles.css", "world-clock.css"]) {
        const css = read(file);
        let at = 0, blocks = 0;
        while ((at = css.indexOf("@media (prefers-color-scheme: dark)", at)) >= 0) {
            const open = css.indexOf("{", at);
            let end = open + 1, depth = 1;
            for (; depth; end++) { if (css[end] === "{") depth++; if (css[end] === "}") depth--; }
            const rules = [...css.slice(open + 1, end - 1).matchAll(/([^{}]+)\{[^{}]*\}/g)];
            assert.ok(rules.length);
            for (const rule of rules) for (const selector of rule[1].trim().split(/,\s*(?![^()]*\))/)) {
                assert.ok(selector.startsWith(":root:not([data-console-theme])"), `${file}: ${selector}`);
            }
            blocks++; at = end;
        }
        assert.equal(blocks, file === "styles.css" ? 2 : 1);
    }
});
