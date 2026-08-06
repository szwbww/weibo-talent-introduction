const fs = require("fs");
const path = require("path");
const vm = require("vm");
const assert = require("assert");
const { describe, it } = require("node:test");

const appJsPath = path.join(__dirname, "..", "..", "main", "resources", "static", "app.js");
const appJsSource = fs.readFileSync(appJsPath, "utf-8");
const stylesCssPath = path.join(__dirname, "..", "..", "main", "resources", "static", "styles.css");
const stylesCssSource = fs.readFileSync(stylesCssPath, "utf-8");

function extractFn(name) {
    const regex = new RegExp("(?:async\\s+)?function\\s+" + name + "\\s*\\([^)]*\\)\\s*\\{[\\s\\S]*?\\n\\}");
    const match = appJsSource.match(regex);
    if (!match) throw new Error("Could not find " + name + " in app.js");
    return match[0];
}

function createSandbox() {
    const sandbox = {
        api: async () => ({ found: true, tags: [] }),
        URLSearchParams
    };
    vm.createContext(sandbox);
    vm.runInContext(extractFn("fetchExpertTagsFromEs"), sandbox);
    vm.runInContext(extractFn("renderExpertTagEditor"), sandbox);
    vm.runInContext(`
        const expertTagLabels = {
            auto_promoted: "自动晋升",
            verified: "已验证",
            discovered: "新发现",
            "承诺回复材料": "承诺回复材料"
        };
        function escapeHtml(v) {
            return String(v == null ? "" : v);
        }
    `, sandbox);
    return sandbox;
}

function normalizeWhitespace(html) {
    return html.replace(/\s+/g, " ").trim();
}

// S-1 contract block (plan verbatim) with concrete values substituted
const S1_EXPECTED = normalizeWhitespace(`
<div class="detail-section expert-tag-editor" id="expertTagEditor" data-orcid="0000-0001" data-level="CANDIDATE" data-profile-missing="true">
    <div class="inbound-tag-editor-head">
        <h3>专家标签</h3>
    </div>
    <div class="inbound-tag-editor-chips"><span class="muted">该专家在 ES 中无画像文档，标签功能不可用</span></div>
</div>
`);

// S-2 contract block (plan verbatim) with concrete values and empty-tags chips
const S2_EXPECTED = normalizeWhitespace(`
<div class="detail-section expert-tag-editor" id="expertTagEditor" data-orcid="0000-0001" data-level="CANDIDATE">
    <div class="inbound-tag-editor-head">
        <h3>专家标签</h3>
        <div class="inbound-tag-editor-actions">
            <button type="button" class="button primary small" data-action="expert-add-tag-open">+ 添加标签</button>
        </div>
    </div>
    <div class="inbound-tag-editor-chips"><span class="muted">暂无标签</span></div>
</div>
`);

// ──────────────────────────────────────────────────────────────────────────
// SUITE: expert profile absence degrades, never errors (P1 I-1/I-3/I-4/S-1/S-2)
// ──────────────────────────────────────────────────────────────────────────

describe("expertProfileAbsence: found=false renders S-1 degraded editor (I-3/S-1)", () => {
    it("produces the S-1 DOM verbatim: no write actions, muted notice, data-profile-missing", () => {
        const sb = createSandbox();
        const html = sb.renderExpertTagEditor([], "0000-0001", "CANDIDATE", "expertTagEditor", true);

        assert.strictEqual(normalizeWhitespace(html), S1_EXPECTED, "found=false output must match S-1 verbatim");
        assert.ok(!html.includes('data-action="expert-add-tag-open"'), "must NOT contain add-tag button");
        assert.ok(!html.includes('data-action="expert-remove-tag"'), "must NOT contain remove-tag buttons");
        assert.ok(html.includes('data-profile-missing="true"'), "must carry data-profile-missing=true");
        assert.ok(html.includes('class="muted"'), "must carry muted notice class");
        assert.ok(html.includes("该专家在 ES 中无画像文档，标签功能不可用"), "must carry the exact notice text");
        assert.ok(html.includes('data-orcid="0000-0001"') && html.includes('data-level="CANDIDATE"'),
            "container id/data-orcid/data-level preserved (updateExpertTagEditor matching)");
    });
});

describe("expertProfileAbsence: found=true renders S-2 editor verbatim (S-2)", () => {
    it("keeps the present-profile DOM identical to the S-2 baseline", () => {
        const sb = createSandbox();
        const html = sb.renderExpertTagEditor([], "0000-0001", "CANDIDATE", "expertTagEditor", false);

        assert.strictEqual(normalizeWhitespace(html), S2_EXPECTED, "found=true output must match S-2 verbatim");
        assert.ok(html.includes('data-action="expert-add-tag-open"'), "add-tag button must be present");
        assert.ok(!html.includes('data-profile-missing="true"'), "no data-profile-missing on present profiles");
    });
});

describe("expertProfileAbsence: found === undefined falls back to present-profile (I-4)", () => {
    it("fetchExpertTagsFromEs treats a missing found field as found", async () => {
        const sb = createSandbox();
        sb.api = async () => ({ orcidId: "0000-0001", tags: ["承诺回复材料"] });

        const result = await sb.fetchExpertTagsFromEs("0000-0001", "CANDIDATE");

        assert.strictEqual(result.found, true, "undefined found must be treated as present profile");
        assert.deepStrictEqual(result.tags, ["承诺回复材料"]);
    });

    it("renderExpertTagEditor with undefined profileMissing renders the S-2 branch", () => {
        const sb = createSandbox();
        const html = sb.renderExpertTagEditor([], "0000-0001", "CANDIDATE");

        assert.strictEqual(normalizeWhitespace(html), S2_EXPECTED, "undefined profileMissing must not degrade");
    });
});

describe("expertProfileAbsence: api errors propagate, never degrade to found=false (I-1)", () => {
    it("fetchExpertTagsFromEs rethrows the api error", async () => {
        const sb = createSandbox();
        sb.api = async () => { throw new Error("es down"); };

        await assert.rejects(
            sb.fetchExpertTagsFromEs("0000-0001", "CANDIDATE"),
            /es down/
        );
    });

    it("found=false is only produced by an explicit profile response", async () => {
        const sb = createSandbox();
        sb.api = async () => ({ found: false, tags: [] });

        const result = await sb.fetchExpertTagsFromEs("0000-0001", "CANDIDATE");

        assert.strictEqual(result.found, false);
        assert.deepStrictEqual(result.tags, []);
    });
});

// K-dom-stub-tests-hide-dangling-refs: the classes used by S-1 must exist in styles.css
describe("expertProfileAbsence: S-1 classes exist in styles.css", () => {
    it("muted and inbound-tag-editor-chips are real CSS classes", () => {
        assert.ok(stylesCssSource.includes(".muted {"), ".muted must exist in styles.css");
        assert.ok(stylesCssSource.includes(".inbound-tag-editor-chips"), ".inbound-tag-editor-chips must exist in styles.css");
        assert.ok(stylesCssSource.includes(".expert-tag-editor"), ".expert-tag-editor must exist in styles.css");
        assert.ok(stylesCssSource.includes(".inbound-tag-editor-head"), ".inbound-tag-editor-head must exist in styles.css");
    });
});
