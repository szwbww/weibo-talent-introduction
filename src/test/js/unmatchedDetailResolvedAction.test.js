const fs = require("fs");
const path = require("path");
const vm = require("vm");
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

function extractFn(name) {
    const regex = new RegExp("(?:async\\s+)?function\\s+" + name + "\\s*\\([^)]*\\)\\s*\\{[\\s\\S]*?\\n\\}");
    const match = app.match(regex);
    return match ? match[0] : null;
}

const CANCEL_BUTTON = '<button class="button secondary" data-action="cancel-unmatched-resolved" data-id="7">取消处理</button>';

describe("inbound cancel-resolved mailbox button (I-1, S-1)", () => {
    const renderActions = extractFn("renderMailboxActions");
    assert.ok(renderActions, "renderMailboxActions must exist");

    function render(row) {
        const sandbox = {
            escapeHtml: (value) => String(value == null ? "" : value)
        };
        vm.createContext(sandbox);
        vm.runInContext(renderActions, sandbox);
        return sandbox.renderMailboxActions(row);
    }

    const eligible = {
        source: "INBOUND_PROCESSING",
        processStatus: "PROCESSED",
        reasonType: "MANUAL_RESOLVED",
        inboundProcessingId: 7,
        expertContactId: null,
        id: "m1"
    };

    it("renders the S-1 cancel button only for the exact eligible row and nowhere else", () => {
        const hit = render(eligible);
        assert.ok(hit.includes(CANCEL_BUTTON), "eligible row must render the verbatim cancel button");
        assert.strictEqual((hit.match(/cancel-unmatched-resolved/g) || []).length, 1, "cancel action must appear exactly once");
        assert.ok(!hit.includes("查看/处理") && !hit.includes("标记已处理"), "eligible row must not render the process branch");

        const mutations = [
            { source: "SEND" },
            { source: undefined },
            { processStatus: "MANUAL_REVIEW" },
            { processStatus: undefined },
            { reasonType: "AUTO_RESOLVED" },
            { reasonType: undefined },
            { inboundProcessingId: undefined },
            { inboundProcessingId: 0 }
        ];
        for (const mutation of mutations) {
            const row = { ...eligible, ...mutation };
            const out = render(row);
            assert.ok(!out.includes("cancel-unmatched-resolved"), `must not render cancel for ${JSON.stringify(mutation)}`);
        }
    });

    it("keeps the process branch for MANUAL_REVIEW and the view button ordering (S-1)", () => {
        const manual = render({ ...eligible, processStatus: "MANUAL_REVIEW" });
        assert.ok(manual.includes("查看/处理") && manual.includes("标记已处理"), "MANUAL_REVIEW keeps 查看/处理 + 标记已处理");
        assert.ok(!manual.includes("cancel-unmatched-resolved"), "MANUAL_REVIEW must not show cancel");

        const withExpert = render({ ...eligible, expertContactId: 9 });
        const viewIdx = withExpert.indexOf("view-mail");
        const cancelIdx = withExpert.indexOf("cancel-unmatched-resolved");
        assert.ok(viewIdx >= 0 && cancelIdx > viewIdx, "cancel button must follow the existing 查看 button");
    });
});

describe("inbound cancel-resolved dialog schema (S-2)", () => {
    it("registers the schema verbatim in ACTION_DIALOG_SCHEMAS", () => {
        const schemasStart = app.indexOf("const ACTION_DIALOG_SCHEMAS");
        const schemasEnd = app.indexOf("function openActionDialog");
        const schemasBlock = app.slice(schemasStart, schemasEnd);
        const sandbox = {};
        vm.createContext(sandbox);
        vm.runInContext(schemasBlock + "\nthis.__schemas = ACTION_DIALOG_SCHEMAS;", sandbox);
        assert.strictEqual(
            JSON.stringify(sandbox.__schemas["cancel-unmatched-resolved"]),
            JSON.stringify({
                title: "取消处理",
                fields: [
                    { name: "operatorName", label: "操作人姓名", type: "text", required: true },
                    { name: "note", label: "取消原因", type: "textarea", required: false }
                ]
            })
        );
    });
});

describe("inbound cancel-resolved action flow (I-2, I-3)", () => {
    const handler = extractFn("handleUnmatchedAction");
    assert.ok(handler, "handleUnmatchedAction must exist");

    function runCancel(options = {}) {
        const calls = { api: [], status: [], refresh: 0, dialog: [] };
        const sandbox = {
            openActionDialog: async (type) => {
                calls.dialog.push(type);
                return options.dialogResult === undefined ? { operatorName: "验收员", note: "误标" } : options.dialogResult;
            },
            api: async (url, opts) => {
                calls.api.push({ url, opts });
                if (options.apiImpl) return options.apiImpl(url, opts);
                return {};
            },
            showStatus: (message, kind) => { calls.status.push({ message, kind }); },
            refreshMailboxAfterPendingAction: async () => { calls.refresh += 1; }
        };
        vm.createContext(sandbox);
        vm.runInContext(handler, sandbox);
        const element = { dataset: { action: "cancel-unmatched-resolved", id: "42" } };
        return { promise: sandbox.handleUnmatchedAction(element), calls };
    }

    it("POSTs once to cancel-resolved with the exact body and refreshes once on success", async () => {
        const { promise, calls } = runCancel();
        await promise;

        assert.deepStrictEqual(calls.dialog, ["cancel-unmatched-resolved"]);
        assert.strictEqual(calls.api.length, 1, "exactly one request");
        assert.strictEqual(calls.api[0].url, "/api/mail/unmatched-inbound/42/cancel-resolved");
        assert.strictEqual(calls.api[0].opts.method, "POST");
        assert.deepStrictEqual(JSON.parse(calls.api[0].opts.body), { operatorName: "验收员", note: "误标" });
        assert.deepStrictEqual(calls.status, [{ message: "已取消处理，可重新处理", kind: undefined }]);
        assert.strictEqual(calls.refresh, 1, "refresh must run exactly once after success");
    });

    it("sends zero requests when the dialog is cancelled and never refreshes", async () => {
        const { promise, calls } = runCancel({ dialogResult: null });
        await promise;

        assert.strictEqual(calls.api.length, 0, "dialog cancel must not request");
        assert.strictEqual(calls.status.length, 0, "dialog cancel must not show success");
        assert.strictEqual(calls.refresh, 0, "dialog cancel must not refresh");
    });

    it("surfaces API failure without the success branch", async () => {
        const { promise, calls } = runCancel({ apiImpl: async () => { throw new Error("409 Conflict"); } });

        await assert.rejects(promise, /409 Conflict/);
        assert.strictEqual(calls.api.length, 1);
        assert.strictEqual(calls.status.length, 0, "API failure must not show success");
        assert.strictEqual(calls.refresh, 0, "API failure must not refresh");
    });

    it("never mutates row/DOM or closes the detail context itself (I-3)", () => {
        const cancelStart = handler.indexOf('if (action === "cancel-unmatched-resolved")');
        assert.ok(cancelStart >= 0, "cancel branch must exist");
        const cancelBlock = handler.slice(cancelStart, handler.indexOf("\n    if (", cancelStart + 1));

        assert.ok(cancelBlock.includes("refreshMailboxAfterPendingAction()"), "success must reuse the mailbox refresh");
        assert.ok(!cancelBlock.includes("unmountMailboxTrustReplyHosts"), "cancel must not unmount the detail host");
        assert.ok(!cancelBlock.includes("hidden = true"), "cancel must not close the detail panel");
        assert.ok(!cancelBlock.includes("detailContext"), "cancel must not rewrite detail context");
        assert.ok(!cancelBlock.includes("state.mailbox.items"), "cancel must not mutate mailbox rows client-side");
    });
});

describe("inbound cancel-resolved audit log (I-4)", () => {
    const renderLog = extractFn("renderLogDetail");
    assert.ok(renderLog, "renderLogDetail must exist");

    function renderLogFor(log) {
        const sandbox = {
            tryParseJson: (value) => (value ? JSON.parse(value) : null),
            labelStatus: (value) => value,
            operatorStatusLabels: {},
            indexLevelLabels: {},
            escapeHtml: (value) => String(value == null ? "" : value)
        };
        vm.createContext(sandbox);
        vm.runInContext(renderLog, sandbox);
        return sandbox.renderLogDetail(log);
    }

    it("shares the MARK processStatus transition renderer and shows PROCESSED → MANUAL_REVIEW", () => {
        const cancelOut = renderLogFor({
            actionType: "CANCEL_INBOUND_RESOLVED",
            beforeValue: '{"processStatus":"PROCESSED"}',
            afterValue: '{"processStatus":"MANUAL_REVIEW"}'
        });
        assert.ok(cancelOut.includes("PROCESSED → MANUAL_REVIEW"), "cancel log must render the transition");

        const markOut = renderLogFor({
            actionType: "MARK_INBOUND_RESOLVED",
            beforeValue: '{"processStatus":"MANUAL_REVIEW"}',
            afterValue: '{"processStatus":"PROCESSED"}'
        });
        assert.ok(markOut.includes("MANUAL_REVIEW → PROCESSED"), "mark log transition must stay intact");
    });

    it("labels CANCEL_INBOUND_RESOLVED as 取消处理", () => {
        const labelFn = extractFn("actionTypeLabel");
        assert.ok(labelFn, "actionTypeLabel must exist");
        const sandbox = {};
        vm.createContext(sandbox);
        vm.runInContext(labelFn, sandbox);
        assert.strictEqual(sandbox.actionTypeLabel("CANCEL_INBOUND_RESOLVED"), "取消处理");
        assert.strictEqual(sandbox.actionTypeLabel("MARK_INBOUND_RESOLVED"), "标记已处理");
    });
});

describe("inbound cancel-resolved event routing", () => {
    it("routes the action through the shared #mailboxList delegation whitelist", () => {
        const start = app.indexOf('$("#mailboxList").addEventListener("click"');
        const end = app.indexOf("summarizeManualOutreachPending", start);
        const listener = app.slice(start, end);

        assert.ok(listener.includes('"cancel-unmatched-resolved"'), "whitelist must include the cancel action");
        assert.match(
            listener,
            /\["open-pending", "mark-unmatched-resolved", "view-unmatched", "open-contact-from-unmatched", "cancel-unmatched-resolved"\]\.includes\(target\.dataset\.action\)/,
            "cancel must flow into the shared handleUnmatchedAction catch(showStatus) path"
        );
    });
});
