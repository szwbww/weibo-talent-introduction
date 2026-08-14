const fs = require("fs");
const path = require("path");
const vm = require("vm");
const assert = require("assert");
const { describe, it } = require("node:test");

const staticRoot = path.join(__dirname, "..", "..", "main", "resources", "static");
const appSource = fs.readFileSync(path.join(staticRoot, "app.js"), "utf-8");
const indexSource = fs.readFileSync(path.join(staticRoot, "index.html"), "utf-8");
const stylesSource = fs.readFileSync(path.join(staticRoot, "styles.css"), "utf-8");

function extractFn(name) {
    const match = appSource.match(new RegExp("function\\s+" + name + "\\s*\\([^)]*\\)\\s*\\{[\\s\\S]*?\\n\\}"));
    return match ? match[0] : null;
}

function createElement(listeners) {
    return {
        style: {},
        classList: { add() {}, remove() {} },
        addEventListener(type, listener) { listeners[type] = listener; },
        setPointerCapture() {},
        hasPointerCapture() { return false; },
        releasePointerCapture() {}
    };
}

function initLayout(savedWidth) {
    const resizerListeners = {};
    const defaultListeners = {};
    const wideListeners = {};
    const splitListeners = {};
    const container = { style: {}, getBoundingClientRect: () => ({ left: 0, width: 1200 }) };
    const listPanel = { style: {} };
    const resizer = createElement(resizerListeners);
    const elements = {
        contactsLayoutResizer: resizer,
        btnLayoutDefault: createElement(defaultListeners),
        btnLayoutWideList: createElement(wideListeners),
        btnLayoutSplit: createElement(splitListeners)
    };
    const writes = [];
    const sandbox = {
        document: {
            body: { style: {} },
            getElementById: (id) => elements[id] || null,
            querySelector: (selector) => selector === ".contacts-layout" ? container :
                (selector === ".contacts-list-panel" ? listPanel : null)
        },
        localStorage: {
            getItem: () => savedWidth,
            setItem: (key, value) => writes.push([key, value])
        },
        window: { innerWidth: 1440 }
    };
    vm.createContext(sandbox);
    vm.runInContext(extractFn("initLayoutResizer"), sandbox);
    sandbox.initLayoutResizer();
    return { container, writes, resizerListeners, defaultListeners };
}

describe("contacts layout default", () => {
    it("uses the middle 500px preset for a fresh layout and every reset path", () => {
        const result = initLayout(null);
        assert.strictEqual(result.container.style.gridTemplateColumns, "500px 6px minmax(0, 1fr)");
        assert.deepStrictEqual(result.writes, [["contacts-list-width", 500]]);

        result.container.style.gridTemplateColumns = "360px 6px minmax(0, 1fr)";
        result.defaultListeners.click();
        assert.strictEqual(result.container.style.gridTemplateColumns, "500px 6px minmax(0, 1fr)");

        result.container.style.gridTemplateColumns = "360px 6px minmax(0, 1fr)";
        result.resizerListeners.dblclick();
        assert.strictEqual(result.container.style.gridTemplateColumns, "500px 6px minmax(0, 1fr)");
    });

    it("preserves a saved personal list width", () => {
        const result = initLayout("360");
        assert.strictEqual(result.container.style.gridTemplateColumns, "360px 6px minmax(0, 1fr)");
        assert.deepStrictEqual(result.writes, []);
    });

    it("matches the 500px runtime default in both desktop CSS rules", () => {
        const desktopRules = [...stylesSource.matchAll(/\.contacts-layout\s*\{[\s\S]*?\n\}/g)]
            .map((match) => match[0])
            .filter((rule) => rule.includes("6px minmax(0, 1fr)"));
        assert.strictEqual(desktopRules.length, 2);
        desktopRules.forEach((rule) => {
            assert.match(rule, /grid-template-columns:\s*500px 6px minmax\(0, 1fr\);/);
        });
        assert.match(stylesSource, /@media \(max-width: 1024px\)[\s\S]*?\.contacts-layout\s*\{[\s\S]*?grid-template-columns:\s*1fr !important;/);
    });

    it("labels the reset button with the middle preset width", () => {
        assert.match(indexSource, /id="btnLayoutDefault" title="默认分栏 \(500px\)"/);
    });
});
