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

// 02：聊天卡片「＋ 添加标签」宿主 adapter —— 新 chat 目标不依赖 detailContext/固定 id
describe("mailbox chat inbound tag adapter (02)", () => {
    function createAdapterSandbox({ detailContext = null, initialAdapter = null } = {}) {
        const calls = { shown: 0, hidden: 0, refreshMailboxAfter: 0, refreshThreadAfter: 0, statuses: [], requests: [] };
        const sandbox = {
            state: {
                mailbox: {
                    detailContext,
                    addTagInboundId: null,
                    chatTagAdapter: initialAdapter
                },
                inboundSummary: { tagEditInboundId: null, selectedId: null },
                qaRules: []
            },
            api: async (url, options) => {
                calls.requests.push({ url, options });
                if (/\/api\/inbound-summary\/mails\/\d+\/tags$/.test(url) && options && options.method === "POST") {
                    return { tags: [{ tagId: 88, label: "服务器回包", tagType: "QA", qaRuleId: 4 }] };
                }
                return {};
            },
            showStatus: (message, type) => calls.statuses.push({ message, type }),
            $: (sel) => {
                if (sel === "#inboundAddTagType") return { value: "qa" };
                if (sel === "#inboundAddTagQaRule") return { value: "5" };
                if (sel === "#inboundAddTagCustomLabel") return { value: "" };
                return null;
            },
            inboundSummaryOperatorName: () => "admin",
            refreshMailboxInboundTagsAfterChange: async () => { calls.refreshMailboxAfter += 1; },
            refreshInboundThreadAfterTagChange: async () => { calls.refreshThreadAfter += 1; },
            URLSearchParams
        };
        vm.createContext(sandbox);
        sandbox.__calls = calls;
        // show/hide 仅记录，不触碰真实 DOM
        vm.runInContext("function showInboundAddTagModal() { __calls.shown += 1; }", sandbox);
        vm.runInContext("function hideInboundAddTagModal() { __calls.hidden += 1; state.mailbox.addTagInboundId = null; state.inboundSummary.tagEditInboundId = null; state.mailbox.chatTagAdapter = null; }", sandbox);
        vm.runInContext(extractFn("showMailboxAddTagModal"), sandbox);
        vm.runInContext(extractFn("mcHostOpenInboundTagModal"), sandbox);
        vm.runInContext(extractFn("submitInboundAddTag"), sandbox);
        return { sandbox, calls };
    }

    it("adapter 打开旧 modal：记录 {inboundId,source,contactId,onTagsChanged}，不依赖 detailContext", () => {
        const { sandbox, calls } = createAdapterSandbox();
        const onTagsChanged = () => {};
        const ok = sandbox.mcHostOpenInboundTagModal({ inboundId: 101, source: "INBOUND_PROCESSING", contactId: 7, onTagsChanged });
        assert.strictEqual(ok, true);
        assert.strictEqual(calls.shown, 1);
        assert.strictEqual(sandbox.state.mailbox.addTagInboundId, 101);
        assert.deepStrictEqual(
            { inboundId: sandbox.state.mailbox.chatTagAdapter.inboundId, contactId: sandbox.state.mailbox.chatTagAdapter.contactId, hasCallback: typeof sandbox.state.mailbox.chatTagAdapter.onTagsChanged === "function" },
            { inboundId: 101, contactId: 7, hasCallback: true }
        );
        // 无 detailContext 也能打开（新 chat 目标）
        assert.strictEqual(sandbox.state.mailbox.detailContext, null);
    });

    it("adapter 非法 id 拒绝打开", () => {
        const { sandbox, calls } = createAdapterSandbox();
        const ok = sandbox.mcHostOpenInboundTagModal({ inboundId: null });
        assert.strictEqual(ok, false);
        assert.strictEqual(calls.shown, 0);
    });

    it("提交成功后把服务器 POST 回包 tags 回调给 adapter；adapter 被清理", async () => {
        const callbacks = [];
        const initialAdapter = {
            inboundId: 101,
            source: "INBOUND_PROCESSING",
            contactId: 7,
            onTagsChanged: (tags) => callbacks.push(tags)
        };
        const { sandbox, calls } = createAdapterSandbox({ initialAdapter });
        sandbox.state.mailbox.chatTagAdapter = initialAdapter;
        sandbox.state.mailbox.addTagInboundId = 101;
        await sandbox.submitInboundAddTag();
        assert.strictEqual(callbacks.length, 1, "POST 回包 tags 直传给聊天 adapter");
        assert.deepStrictEqual(callbacks[0], [{ tagId: 88, label: "服务器回包", tagType: "QA", qaRuleId: 4 }]);
        assert.strictEqual(calls.refreshMailboxAfter, 0, "adapter 路径不刷新旧 mailbox 编辑器");
        assert.strictEqual(calls.refreshThreadAfter, 0, "adapter 路径不刷新来信汇总线程");
        assert.strictEqual(calls.hidden, 1);
        assert.strictEqual(sandbox.state.mailbox.chatTagAdapter, null, "提交后 adapter 清理");
        assert.ok(calls.requests.some((entry) => entry.url === "/api/inbound-summary/mails/101/tags" && entry.options.method === "POST"));
    });

    it("无 adapter（旧来信汇总/详情分支）：detailContext 存在时走 refreshMailboxInboundTagsAfterChange", async () => {
        const { sandbox, calls } = createAdapterSandbox({
            detailContext: { source: "INBOUND_PROCESSING", id: 42, inboundProcessingId: 42 }
        });
        sandbox.state.mailbox.addTagInboundId = 42;
        await sandbox.submitInboundAddTag();
        assert.strictEqual(calls.refreshMailboxAfter, 1, "旧分支照常回读 mailbox 标签编辑器");
        assert.strictEqual(calls.refreshThreadAfter, 0);
    });

    it("modal 关闭/取消清空 adapter（hide 清理）", async () => {
        const { sandbox, calls } = createAdapterSandbox({ initialAdapter: { inboundId: 1, onTagsChanged: null } });
        sandbox.state.mailbox.chatTagAdapter = { inboundId: 1, onTagsChanged: null };
        sandbox.state.mailbox.addTagInboundId = 1;
        await sandbox.submitInboundAddTag();
        assert.strictEqual(calls.hidden, 1);
        assert.strictEqual(sandbox.state.mailbox.chatTagAdapter, null);
    });
});
