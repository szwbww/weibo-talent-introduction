const fs = require("fs");
const path = require("path");
const assert = require("assert");
const { describe, it } = require("node:test");

const root = path.join(__dirname, "..", "..", "main", "resources", "static");
const html = fs.readFileSync(path.join(root, "index.html"), "utf-8");
const css = fs.readFileSync(path.join(root, "styles.css"), "utf-8");

describe("expert filter layout", () => {
    it("keeps all primary dropdown filters, including discipline, in one row", () => {
        const row = html.match(/<div class="expert-filter-row expert-filter-row-primary">([\s\S]*?)<\/div>/)?.[1] || "";
        assert.ok(row, "primary filter row must exist");
        ["expertSortBy", "expertIndexLevel", "contactStatusFilter", "contactNeedsAttentionFilter",
            "contactReplyModeFilter", "expertTagFilter", "expertEmailDomainFilter", "expertRegionFilter",
            "expertDisciplineFilter"].forEach((id) => assert.ok(row.includes(`id="${id}"`), `${id} must be in primary row`));
    });

    it("groups research type and data completeness into the styled second row", () => {
        const tagsStart = html.indexOf('<div class="expert-filter-row expert-filter-row-tags">');
        const metricsStart = html.indexOf('<div class="expert-filter-row expert-filter-row-metrics">');
        const row = tagsStart >= 0 && metricsStart > tagsStart ? html.slice(tagsStart, metricsStart) : "";
        assert.ok(row.includes('id="expertTypeTagSelect"'));
        assert.ok(row.includes('id="hasFieldTagSelect"'));
        assert.match(css, /\.expert-filter-row-tags\s*\{[\s\S]*?display:\s*grid;/);
        assert.match(css, /\.expert-filter-tag-group\s*\{[\s\S]*?background:\s*rgba\(255,\s*255,\s*255,\s*\.42\)/);
    });

    it("puts metric filters in a dedicated third row", () => {
        const row = html.match(/<div class="expert-filter-row expert-filter-row-metrics">([\s\S]*?)<\/div>/)?.[1] || "";
        ["expertHIndexMinFilter", "expertCitationMinFilter", "expertRecentYearsFilter", "expertGateTemplateFilter"]
            .forEach((id) => assert.ok(row.includes(`id="${id}"`), `${id} must be in metrics row`));
    });

    it("uses a complete three-column grid at medium desktop widths", () => {
        assert.match(css, /@media \(min-width: 1181px\) and \(max-width: 1500px\) \{[\s\S]*?\.expert-filter-row-primary\s*\{\s*grid-template-columns:\s*repeat\(3, minmax\(0, 1fr\)\);/);
        assert.match(css, /\.expert-filter-row-primary > \.toolbar-label\s*\{\s*min-width:\s*0;\s*flex-direction:\s*column;\s*align-items:\s*stretch;\s*gap:\s*4px;/);
        assert.match(css, /\.expert-filter-row-primary > \.toolbar-label select\s*\{\s*width:\s*100%;\s*min-width:\s*0;/);
        assert.match(css, /@media \(max-width: 1180px\) \{[\s\S]*?\.expert-filter-row-primary\s*\{\s*grid-template-columns:\s*repeat\(3, minmax\(0, 1fr\)\);/);
    });

    it("hides the one-time discovery actions while keeping their ids available", () => {
        assert.match(html, /id="enrichBackfillBtn"[^>]*hidden/);
        assert.match(html, /id="enrichYearBackfillBtn"[^>]*hidden/);
    });

    it("removes the pager's visible row-count label", () => {
        const pager = html.slice(html.indexOf('id="contactPager"'), html.indexOf("</section>", html.indexOf('id="contactPager"')));
        assert.ok(!pager.includes("显示行数:"));
    });
});
