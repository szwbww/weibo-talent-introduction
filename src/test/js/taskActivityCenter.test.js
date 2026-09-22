const fs = require("fs");
const path = require("path");
const assert = require("assert");
const { describe, it } = require("node:test");

const staticDir = path.join(__dirname, "..", "..", "main", "resources", "static");
const html = fs.readFileSync(path.join(staticDir, "index.html"), "utf-8");
const appJs = fs.readFileSync(path.join(staticDir, "app.js"), "utf-8");

/**
 * c3/T-3：缓存键契约。
 *
 * 键**不写死**在这个文件里 —— 从随包发布的 `index.html` 派生，因此后续任何一次键 bump
 * 都不需要再改测试（本文件存在的理由就是替代上一轮把键固化的做法）。
 * 断言的仍然是资源一致性契约：11 个版本化资源同键、同一批顺序、发布 triad 同键、
 * 未版本化的运行时脚本保持未版本化、且除 index.html 外没有任何文件夹带这个键。
 */
const CACHE_KEY = (() => {
    const match = html.match(/styles\.css\?v=([^"'&<>]+)/);
    if (!match) throw new Error("index.html must register styles.css with a ?v= cache key");
    return match[1];
})();

const ORDERED_ASSETS = [
    "styles.css",
    "expert-materials.css",
    "mailbox-chat.css",
    "meeting-confirmation.css",
    "world-clock.css",
    "trust-reply-workbench.js",
    "expert-materials.js",
    "meeting-confirmation.js",
    "mailbox-chat.js",
    "app.js",
    "world-clock.js"
];

// 历史键：必须零命中，避免新旧键混用（K-frontend-cache-key-triad）。
const RETIRED_KEYS = ["20260920-manual-material-upload", "20260922-task-activity-center", "20260903-bounce-warning"];

function jsFilesUnder(dir) {
    return fs.readdirSync(dir)
        .filter(name => name.endsWith(".js"))
        .map(name => path.join(dir, name));
}

describe("task activity center cache key contract (c3/T-3)", () => {
    it("derives one live key from the shipped index.html and applies it to exactly eleven resources", () => {
        const keys = (html.match(/\?v=[^"]+/g) || []).map(token => token.slice(3));
        assert.strictEqual(keys.length, 11, "index.html must carry exactly eleven cache-busted asset URLs");
        assert.deepStrictEqual([...new Set(keys)], [CACHE_KEY], "every cache key must equal the derived key " + CACHE_KEY);
    });

    it("keeps the published triad on the same key and the asset order stable", () => {
        for (const asset of ORDERED_ASSETS) {
            assert.ok(html.includes(asset + "?v=" + CACHE_KEY), asset + " must use the derived key");
        }
        let previous = -1;
        for (const asset of ORDERED_ASSETS) {
            const at = html.indexOf(asset + "?v=" + CACHE_KEY);
            assert.ok(at > previous, asset + " must stay in registration order (CSS then workbench -> app)");
            previous = at;
        }
        // K-frontend-cache-key-triad：样式、工作台与主脚本必须同值同时 bump。
        for (const asset of ["styles.css", "trust-reply-workbench.js", "app.js"]) {
            assert.ok(html.includes(asset + "?v=" + CACHE_KEY), asset + " is part of the published triad");
        }
    });

    it("leaves the unversioned runtime script unversioned", () => {
        assert.ok(html.includes('<script src="task-modal-runtime.js"></script>'),
            "task-modal-runtime.js must stay unversioned (it is a runtime dependency of app.js)");
        assert.ok(!html.includes("task-modal-runtime.js?v="));
    });

    it("retires every previous key literal", () => {
        for (const retired of RETIRED_KEYS) {
            if (retired === CACHE_KEY) continue;
            assert.ok(!html.includes(retired), "retired cache key must have zero hits in index.html: " + retired);
        }
    });

    it("keeps the live key out of every shipped script and every test file", () => {
        const stragglers = [
            ...jsFilesUnder(staticDir),
            ...jsFilesUnder(__dirname),
            path.join(staticDir, "task-modal-runtime.js")
        ].filter(file => fs.readFileSync(file, "utf-8").includes(CACHE_KEY));
        assert.deepStrictEqual(stragglers, [],
            "a cache key literal must live only in index.html — found in: " + stragglers.join(", "));
    });

    it("uses a single well-formed key token", () => {
        assert.ok(/^[0-9]{8}-[a-z0-9-]+$/.test(CACHE_KEY), "cache key must be <yyyymmdd>-<slug>, got: " + CACHE_KEY);
        assert.ok(!appJs.includes("?v="), "app.js must not build asset URLs that bypass index.html keys");
    });
});
