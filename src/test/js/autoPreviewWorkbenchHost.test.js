const fs = require("fs");
const path = require("path");
const assert = require("assert");
const { describe, it } = require("node:test");

const root = path.join(__dirname, "..", "..", "main", "resources", "static");
const appSource = fs.readFileSync(path.join(root, "app.js"), "utf-8");
const htmlSource = fs.readFileSync(path.join(root, "index.html"), "utf-8");
const cssSource = fs.readFileSync(path.join(root, "styles.css"), "utf-8");

// P1 (I-4): the AUTO_PREVIEW host in the mailbox detail panel is retired.
// The workbench runtime keeps the mode; only the host adapter is gone.
const RETIRED_TOKENS = [
    "data-trust-reply-auto-preview-host",
    "data-auto-preview-status",
    "data-auto-preview-body",
    "auto-preview-section",
    "autoPreviewTrustReplyInstance",
    "unmountAutoPreviewTrustReply",
    "mountAutoPreviewTrustReply",
    "loadAutoPreviewIntoHost",
    "renderAutoPreviewIntoHost",
    "renderAutoPreviewError",
    "waitForWorkbenchReady",
    "自动回复预览"
];

describe("AUTO_PREVIEW workbench host (retired in P1)", () => {
    it("I-4: no retired identifier survives in app.js / index.html / styles.css", () => {
        for (const token of RETIRED_TOKENS) {
            assert.ok(!appSource.includes(token), `app.js must not contain ${token}`);
            assert.ok(!htmlSource.includes(token), `index.html must not contain ${token}`);
            assert.ok(!cssSource.includes(token), `styles.css must not contain ${token}`);
        }
    });

    it("I-4: the degraded copy of the removed preview is gone too", () => {
        assert.ok(!appSource.includes("无法解析自动回复上下文"),
            "the auto-preview degraded notice must be removed with the section");
    });

    it("I-4: app.js no longer calls the auto-reply-preview endpoint", () => {
        assert.ok(!/auto-reply-\W*\+?\s*["']preview["']/.test(appSource),
            "app.js must not build the /auto-reply-preview path any more");
        assert.ok(!appSource.includes("auto-reply-preview"),
            "app.js must not reference the auto-reply-preview endpoint literally");
    });

    it("I-5: unmountMailboxTrustReplyHosts survives as the single teardown seam", () => {
        assert.match(appSource, /function unmountMailboxTrustReplyHosts\(\) \{\s*unmountLiveTrustReply\(\);\s*\}/,
            "the shared teardown entry must remain, with a LIVE-only body");
        const callSites = (appSource.match(/unmountMailboxTrustReplyHosts\(\)/g) || []).length;
        assert.ok(callSites >= 9, `expected the definition plus 8 call sites, found ${callSites}`);
    });

    it("I-4: the LIVE workbench host is untouched", () => {
        assert.ok(appSource.includes("data-trust-reply-live-host"), "LIVE host must remain");
        assert.ok(appSource.includes("function mountLiveTrustReply(recordId)"), "LIVE mount must remain");
    });
});
