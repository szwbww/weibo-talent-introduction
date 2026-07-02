const fs = require("fs");
const path = require("path");
const assert = require("assert");
const { describe, it } = require("node:test");

const appJsPath = path.join(__dirname, "..", "..", "main", "resources", "static", "app.js");
const appJsSource = fs.readFileSync(appJsPath, "utf-8");

describe("inbound summary expert grouping defaults", () => {
    it("renders expert groups collapsed by default", () => {
        assert.ok(
            !appJsSource.includes('<details class="inbound-expert-group" open>'),
            "expert-group details must not include open attribute"
        );
    });
});
