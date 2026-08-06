const fs = require("fs");
const path = require("path");
const vm = require("vm");
const assert = require("assert");
const { describe, it } = require("node:test");

const appJsPath = path.join(__dirname, "..", "..", "main", "resources", "static", "app.js");
const indexPath = path.join(__dirname, "..", "..", "main", "resources", "static", "index.html");
const appJsSource = fs.readFileSync(appJsPath, "utf-8");
const indexSource = fs.readFileSync(indexPath, "utf-8");

function extractFn(name) {
    const regex = new RegExp("(?:async\\s+)?function\\s+" + name + "\\s*\\([^)]*\\)\\s*\\{[\\s\\S]*?\\n\\}");
    const match = appJsSource.match(regex);
    if (!match) throw new Error("Could not find " + name + " in app.js");
    return match[0];
}

function createAutoTagSandbox(response) {
    const requests = [];
    const statuses = [];
    const loadingStates = [];
    const sandbox = {
        state: {
            mailbox: {
                detailContext: {
                    source: "INBOUND_PROCESSING",
                    id: 42,
                    inboundProcessingId: 42
                }
            }
        },
        api: async (url, options) => {
            requests.push({ url, options });
            return response;
        },
        showStatus: (message, type) => statuses.push({ message, type }),
        refreshMailboxInboundTagsAfterChange: async () => {},
        setTagEditorLoading: (_editor, loading, message) => loadingStates.push({ loading, message }),
        inboundSummaryOperatorName: () => "admin",
        $: () => null,
        requests,
        statuses,
        loadingStates
    };
    vm.createContext(sandbox);
    vm.runInContext(extractFn("mailboxTagEditInboundId"), sandbox);
    vm.runInContext(extractFn("showAutoApplyTagStatus"), sandbox);
    vm.runInContext(extractFn("mailboxAutoApplyTags"), sandbox);
    return sandbox;
}

describe("mailbox inbound tag actions", () => {
    it("tells the operator when auto QA tag matching adds nothing", async () => {
        const sb = createAutoTagSandbox({ tags: [], addedCount: 0 });

        await sb.mailboxAutoApplyTags();

        assert.strictEqual(sb.requests.length, 1);
        assert.ok(sb.requests[0].url.includes("/api/inbound-summary/mails/42/tags/auto"));
        assert.deepStrictEqual(sb.statuses, [{ message: "未匹配到 QA 规则", type: "error" }]);
    });

    it("shows loading while mailbox auto QA tags are applying", async () => {
        const sb = createAutoTagSandbox({ tags: [{ tagId: 1 }], addedCount: 1 });

        await sb.mailboxAutoApplyTags();

        assert.deepStrictEqual(sb.loadingStates, [
            { loading: true, message: "正在自动匹配 QA 标签..." },
            { loading: false, message: undefined }
        ]);
    });

    it("keeps newly added expert tag visible when ES refresh lags", async () => {
        const calls = [];
        const sandbox = {
            api: async (url, options) => {
                calls.push({ url, options });
                return { success: true };
            },
            loadContacts: async () => {},
            loadExpertTagOptions: async () => {},
            refreshExpertTagsFromEs: async () => ({ found: true, tags: ["自动晋升"] }),
            $: () => ({ value: "" }),
            URLSearchParams
        };
        vm.createContext(sandbox);
        vm.runInContext(extractFn("mutateExpertTag"), sandbox);

        const tags = await sandbox.mutateExpertTag("0000-0001", "CANDIDATE", "承诺回复材料", "add");

        assert.deepStrictEqual(Array.from(tags), ["自动晋升", "承诺回复材料"]);
        assert.ok(calls[0].url.includes("/api/experts/tags/add"));
    });

    it("uses the standard modal panel structure for inbound add tag dialog", () => {
        const modalStart = indexSource.indexOf('id="inboundAddTagModal"');
        const modalEnd = indexSource.indexOf('id="accountModal"', modalStart);
        const modalHtml = indexSource.slice(modalStart, modalEnd);

        assert.ok(modalHtml.includes("modal-backdrop"));
        assert.ok(modalHtml.includes("modal-panel"));
        assert.ok(!modalHtml.includes("modal-card"));
        assert.ok(modalHtml.indexOf("modal-backdrop") < modalHtml.indexOf("modal-panel"));
    });
});
