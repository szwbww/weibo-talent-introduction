const fs = require("fs");
const path = require("path");
const vm = require("vm");
const assert = require("node:assert/strict");
const { test } = require("node:test");

const source = fs.readFileSync(path.join(__dirname, "../../main/resources/static/app.js"), "utf8");
const render = source.match(/function renderMonitoringActivityTable\([^)]*\)\s*\{[\s\S]*?\n\}/)[0];

function renderRows(rows) {
    const head = {}, body = {};
    const sandbox = {
        state: { monitoring: { subTab: "introductions", rows } },
        $: () => ({ querySelector: (selector) => selector === "thead" ? head : body }),
        escapeHtml: (value) => String(value).replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;"),
        monitoringContactCell: () => "Expert",
        badge: () => "SENT",
        labelStatus: () => "Sent"
    };
    vm.runInNewContext(`${render}\nrenderMonitoringActivityTable();`, sandbox);
    return { head: head.innerHTML, body: body.innerHTML };
}

test("introduction table displays every saved email and escapes markup", () => {
    const result = renderRows([{ expertEmails: ["primary@example.org", "alias@example.org", "<alias>@example.org"] }]);
    assert.match(result.head, /<th>专家邮箱<\/th>/);
    assert.match(result.body, /primary@example\.org/);
    assert.match(result.body, /alias@example\.org/);
    assert.match(result.body, /&lt;alias&gt;@example.org/);
    assert.doesNotMatch(result.body, /<alias>/);
});

test("empty emails and empty table preserve column alignment", () => {
    assert.match(renderRows([{ expertEmails: [] }]).body, /<td[^>]*>-<\/td>/);
    assert.match(renderRows([]).body, /colspan="8"/);
});
