const fs = require("fs");
const path = require("path");
const vm = require("vm");
const assert = require("assert");
const { describe, it } = require("node:test");

const appJsPath = path.join(__dirname, "..", "..", "main", "resources", "static", "app.js");
const appJsSource = fs.readFileSync(appJsPath, "utf-8");

function extractFn(name) {
    const regex = new RegExp("(?:async\\s+)?function\\s+" + name + "\\s*\\([^)]*\\)\\s*\\{[\\s\\S]*?\\n\\}");
    const match = appJsSource.match(regex);
    if (!match) throw new Error("Could not find " + name + " in app.js");
    return match[0];
}

function shanghaiTodayString() {
    return new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Shanghai" }).format(new Date());
}

function shanghaiWeekAgoString() {
    const weekAgo = new Date();
    weekAgo.setDate(weekAgo.getDate() - 7);
    return new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Shanghai" }).format(weekAgo);
}

function createMailboxSandbox() {
    const store = new Map();
    function el(id) {
        if (!store.has(id)) {
            store.set(id, {
                id,
                value: "",
                innerHTML: "",
                textContent: ""
            });
        }
        return store.get(id);
    }

    const sandbox = {
        $: (sel) => el(sel.replace(/^#/, "")),
        URLSearchParams,
        state: {
            mailbox: {
                items: [],
                page: 0,
                totalCount: 0,
                pageSize: 20,
                accountsLoaded: true,
                dateDefaultsApplied: false
            }
        },
        api: async () => ({ items: [], totalCount: 0 }),
        showStatus: () => {},
        renderMailboxTable: () => {},
        renderMailboxPagination: () => {},
        escapeHtml: (v) => String(v == null ? "" : v)
    };

    vm.createContext(sandbox);
    vm.runInContext(extractFn("monitoringToday"), sandbox);
    vm.runInContext("async function loadMailboxAccounts() {}", sandbox);
    vm.runInContext(extractFn("loadMailbox"), sandbox);
    sandbox.__store = store;
    return sandbox;
}

describe("mailbox date default", () => {
    it("state.mailbox tracks one-time date default initialization", () => {
        assert.match(
            appJsSource,
            /dateDefaultsApplied:\s*false/,
            "state.mailbox should track dateDefaultsApplied"
        );
        assert.match(
            appJsSource,
            /!state\.mailbox\.dateDefaultsApplied\s*&&\s*!startInput\.value\s*&&\s*!endInput\.value/,
            "loadMailbox should only apply defaults before first initialization"
        );
        assert.match(
            appJsSource,
            /state\.mailbox\.dateDefaultsApplied\s*=\s*true/,
            "loadMailbox should mark date defaults as applied after first check"
        );
    });

    it("first loadMailbox fills default date range", async () => {
        const sb = createMailboxSandbox();
        let requestUrl = "";

        sb.api = async (url) => {
            requestUrl = url;
            return { items: [], totalCount: 0 };
        };

        await sb.loadMailbox();

        const startInput = sb.$("#mailboxFilterStartDate");
        const endInput = sb.$("#mailboxFilterEndDate");
        assert.equal(startInput.value, shanghaiWeekAgoString());
        assert.equal(endInput.value, shanghaiTodayString());
        assert.equal(sb.state.mailbox.dateDefaultsApplied, true);

        const query = new URLSearchParams(requestUrl.split("?")[1] || "");
        assert.equal(query.get("startDate"), shanghaiWeekAgoString());
        assert.equal(query.get("endDate"), shanghaiTodayString());
    });

    it("cleared dates stay empty on subsequent loadMailbox calls", async () => {
        const sb = createMailboxSandbox();
        const requestUrls = [];

        sb.api = async (url) => {
            requestUrls.push(url);
            return { items: [], totalCount: 0 };
        };

        await sb.loadMailbox();

        sb.$("#mailboxFilterStartDate").value = "";
        sb.$("#mailboxFilterEndDate").value = "";

        await sb.loadMailbox();

        assert.equal(sb.$("#mailboxFilterStartDate").value, "");
        assert.equal(sb.$("#mailboxFilterEndDate").value, "");

        const query = new URLSearchParams(requestUrls[1].split("?")[1] || "");
        assert.equal(query.get("startDate"), null);
        assert.equal(query.get("endDate"), null);
    });
});
