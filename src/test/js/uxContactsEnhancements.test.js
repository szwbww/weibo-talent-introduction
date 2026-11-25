"use strict";

const { describe, it } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const ROOT = path.join(__dirname, "..", "..", "main", "resources", "static");
const indexHtml = fs.readFileSync(path.join(ROOT, "index.html"), "utf-8");
const appJs = fs.readFileSync(path.join(ROOT, "app.js"), "utf-8");
const contactDetailsJs = fs.readFileSync(path.join(ROOT, "contact-details.js"), "utf-8");
const shellCss = fs.readFileSync(path.join(ROOT, "console-shell.css"), "utf-8");

describe("Task 4: Contacts Search/Reset, Viewport-Priority Cache, QA Filter & Table Polish", () => {
    it("adds quick search, clear filters button, and DB mode notice to contacts view without breaking 3-row filter grid", () => {
        assert.ok(indexHtml.includes('id="contactQuickSearch"'), "contact quick search input must exist");
        assert.ok(indexHtml.includes('id="contactClearFiltersBtn"'), "clear filters button must exist");
        assert.ok(indexHtml.includes('id="contactDbModeNotice"'), "DB mode notice must exist");
        assert.ok(appJs.includes("resetAllContactFilters"), "app.js must define resetAllContactFilters");
        assert.ok(appJs.includes("filterRenderedContactList"), "app.js must support instant in-page contact filtering");
    });

    it("removes duplicate #loadQaAuditBtn listener and adds QA rules quick search/policy filter", () => {
        const auditBindings = appJs.match(/\$\("#loadQaAuditBtn"\)\?\.addEventListener\("click"/g) || [];
        assert.equal(auditBindings.length, 1, "#loadQaAuditBtn click listener must only be bound once");
        assert.ok(indexHtml.includes('id="qaRuleSearchInput"'), "QA rules toolbar must include search input");
        assert.ok(indexHtml.includes('id="qaRulePolicyFilter"'), "QA rules toolbar must include policy filter");
    });

    it("preserves cachedRead contract while using bounded cache and viewport-priority ordering in contact-details.js", () => {
        assert.ok(!contactDetailsJs.includes("listProfiles.clear();listNotes.clear();"),
            "loadContacts should not wipe warm caches on every filter switch");
        assert.ok(contactDetailsJs.includes("pruneCache"), "contact-details.js should bound cache via pruneCache");
        assert.ok(contactDetailsJs.includes("getBoundingClientRect"), "viewport visibility should prioritize visible rows");
    });

    it("applies flat hover polish, natural label casing, and tabular-nums in console-shell.css", () => {
        assert.match(shellCss, /font-variant-numeric:\s*tabular-nums/, "tables and metrics should use tabular-nums");
        assert.match(shellCss, /\.console-ui label\{[^}]*text-transform:none/, "console-ui labels should not force uppercase");
    });
});
