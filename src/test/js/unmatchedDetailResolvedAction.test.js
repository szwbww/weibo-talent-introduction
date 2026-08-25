const fs = require("fs");
const path = require("path");
const assert = require("assert");
const { describe, it } = require("node:test");

const app = fs.readFileSync(
    path.join(__dirname, "..", "..", "main", "resources", "static", "app.js"),
    "utf-8"
);

function detailFunctionSource() {
    const start = app.indexOf("async function showUnmatchedDetail(id)");
    const end = app.indexOf("\nasync function handleUnmatchedAction", start);
    assert.ok(start >= 0 && end > start, "showUnmatchedDetail must exist");
    return app.slice(start, end);
}

describe("unmatched detail resolved action", () => {
    it("renders the resolved button only for manual-review mail and reuses the existing action", () => {
        const source = detailFunctionSource();

        assert.match(source, /record\.processStatus\s*===\s*["']MANUAL_REVIEW["']/);
        assert.match(
            source,
            /data-action="mark-unmatched-resolved"[^>]*data-id="\$\{id\}"[^>]*>标记已处理/
        );
        assert.match(source, /class="panel-head-actions"/);
    });

    it("keeps the detail action on the same mark-resolved endpoint as the list action", () => {
        const handlerStart = app.indexOf("async function handleUnmatchedAction(element)");
        const handlerEnd = app.indexOf("\n    if (action === \"bind-candidate\")", handlerStart);
        const handler = app.slice(handlerStart, handlerEnd);

        assert.match(handler, /openActionDialog\("mark-unmatched-resolved"\)/);
        assert.match(handler, /\/api\/mail\/unmatched-inbound\/\$\{id\}\/mark-resolved/);
    });
});
