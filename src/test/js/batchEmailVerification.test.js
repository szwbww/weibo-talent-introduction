const fs = require("fs");
const path = require("path");
const vm = require("vm");
const assert = require("assert");
const { describe, it } = require("node:test");

const appJsPath = path.join(__dirname, "..", "..", "main", "resources", "static", "app.js");
const appJsSource = fs.readFileSync(appJsPath, "utf-8");
const stylesPath = path.join(__dirname, "..", "..", "main", "resources", "static", "styles.css");
const stylesSource = fs.readFileSync(stylesPath, "utf-8");
const indexHtmlPath = path.join(__dirname, "..", "..", "main", "resources", "static", "index.html");
const indexSource = fs.readFileSync(indexHtmlPath, "utf-8");

function extractFn(name) {
    const regex = new RegExp("(?:async\\s+)?function\\s+" + name + "\\s*\\([^)]*\\)\\s*\\{[\\s\\S]*?\\n\\}");
    const match = appJsSource.match(regex);
    if (!match) throw new Error("Could not find " + name + " in app.js");
    return match[0];
}

/** Top-level `var NAME = ...;` declaration (quote/bracket aware, so markup stays intact). */
function extractDeclaration(name) {
    const start = appJsSource.indexOf("var " + name + " =");
    if (start < 0) throw new Error("Could not find " + name + " in app.js");
    let depth = 0;
    let quote = null;
    for (let i = start; i < appJsSource.length; i++) {
        const ch = appJsSource[i];
        if (quote) {
            if (ch === "\\") { i++; continue; }
            if (ch === quote) quote = null;
            continue;
        }
        if (ch === '"' || ch === "'" || ch === "`") { quote = ch; continue; }
        if (ch === "{" || ch === "(" || ch === "[") depth++;
        else if (ch === "}" || ch === ")" || ch === "]") depth--;
        else if (ch === ";" && depth === 0) return appJsSource.slice(start, i + 1);
    }
    throw new Error("Could not terminate declaration of " + name);
}

const VERIFICATION_DECLARATIONS = [
    "BATCH_EMAIL_VERIFICATION_HINT",
    "BATCH_EMAIL_VERIFICATION_UNSUPPORTED_HINT",
    "BATCH_EMAIL_VERIFICATION_PAGE_SIZE",
    "BATCH_EMAIL_VERIFICATION_NOTE",
    "BATCH_EMAIL_VERIFICATION_ERROR_LABELS",
    "BATCH_EMAIL_VERIFICATION_SEND_REASON_LABELS",
    "BATCH_EMAIL_VERIFICATION_TAG_ERROR_LABELS"
].map(extractDeclaration);

const VERIFICATION_FUNCTIONS = [
    "emailVerificationFieldId",
    "emailVerificationToggleId",
    "emailVerificationLabelId",
    "emailVerificationHintId",
    "emailVerificationToggleChecked",
    "updateEmailVerificationToggleLabel",
    "refreshEmailVerificationState",
    "emailVerificationDecisionText",
    "emailVerificationDecisionBadgeClass",
    "emailVerificationReasonText",
    "emailVerificationSendReasonText",
    "emailVerificationSendText",
    "emailVerificationTagErrorText",
    "emailVerificationTagText",
    "emailVerificationIsRunning",
    "batchEmailVerificationRowHtml",
    "batchEmailVerificationMetricsHtml",
    "batchEmailVerificationNoteText",
    "setBatchEmailVerificationPagerLoading",
    "clearBatchEmailVerificationDisplay",
    "resetBatchEmailVerification",
    "renderBatchEmailVerification",
    "renderBatchEmailVerificationFailure",
    "loadBatchEmailVerification",
    "batchEmailVerificationNextPage",
    "batchEmailVerificationPrevPage",
    "batchEmailVerificationTrackExpanded"
];

function createElement(extra = {}) {
    return Object.assign({
        hidden: false,
        disabled: false,
        checked: false,
        open: false,
        value: "",
        textContent: "",
        innerHTML: "",
        className: "",
        attributes: {},
        children: {},
        listeners: {},
        classList: {
            names: new Set(),
            add(name) { this.names.add(name); },
            remove(name) { this.names.delete(name); },
            contains(name) { return this.names.has(name); }
        },
        getAttribute(name) {
            return Object.prototype.hasOwnProperty.call(this.attributes, name) ? this.attributes[name] : null;
        },
        setAttribute(name, value) { this.attributes[name] = String(value); },
        querySelector(selector) {
            if (!this.children[selector]) this.children[selector] = createElement();
            return this.children[selector];
        },
        addEventListener(type, handler) {
            (this.listeners[type] = this.listeners[type] || []).push(handler);
        }
    }, extra);
}

function createElementStore() {
    const store = new Map();
    return {
        get(id) {
            if (!store.has(id)) store.set(id, createElement({ id }));
            return store.get(id);
        }
    };
}

/** Loads the real escapeHtml/formatDateTime plus every function under test. */
function loadVerificationFunctions(sandbox) {
    VERIFICATION_DECLARATIONS.forEach((declaration) => vm.runInContext(declaration, sandbox));
    vm.runInContext(extractFn("escapeHtml"), sandbox);
    vm.runInContext(extractFn("formatDateTime"), sandbox);
    VERIFICATION_FUNCTIONS.forEach((name) => vm.runInContext(extractFn(name), sandbox));
    return sandbox;
}

function verificationState(overrides = {}) {
    return Object.assign({
        logConfigId: null,
        logExecutionId: null,
        logMode: "execution",
        verificationExecutionId: null,
        verificationExecutionStatus: null,
        verificationCursorStack: [],
        verificationAfterId: 0,
        verificationNextAfterId: null,
        verificationHasMore: false,
        verificationRequestSeq: 0,
        verificationLoading: false,
        verificationLoaded: false,
        verificationExpandedIds: []
    }, overrides);
}

function verificationSandbox(options = {}) {
    const elements = createElementStore();
    const state = verificationState(options.state);
    const sandbox = Object.assign({
        batchTaskState: state,
        document: { getElementById: (id) => (options.missingElements || []).includes(id) ? null : elements.get(id) },
        api: async () => { throw new Error("api must be stubbed by the test"); },
        console: { error: () => {}, warn: () => {} }
    }, options.extra || {});
    vm.createContext(sandbox);
    loadVerificationFunctions(sandbox);
    return { sandbox, elements, state };
}

const flush = () => new Promise((resolve) => setImmediate(resolve));

function verificationRow(overrides = {}) {
    return Object.assign({
        id: 1,
        expertDocId: "doc-1",
        orcidId: "0000-0002-1825-0097",
        expertName: "张三",
        email: "zhangsan@university.edu",
        decision: "PASS",
        providerState: "deliverable",
        providerReason: null,
        errorCode: null,
        checkedAt: "2026-08-06T10:00:05",
        requestCount: 1,
        sendStatus: "SENT",
        sendReason: null,
        tagStatus: "NOT_REQUIRED",
        tagError: null
    }, overrides);
}

function verificationPayload(overrides = {}) {
    return Object.assign({
        executionId: 101,
        enabled: true,
        summary: { total: 1, pending: 0, passed: 1, rejected: 0, errors: 0, tagFailed: 0 },
        items: [verificationRow()],
        nextAfterId: null,
        hasMore: false
    }, overrides);
}

describe("batch email verification switch propagation (I-1 / S-1)", () => {
    it("V1: editor save payload and recipient preview both carry the switch state", async () => {
        const elements = createElementStore();
        const bodies = [];
        const sandbox = {
            document: { getElementById: (id) => elements.get(id) },
            batchTaskState: { editorMode: "create", editorId: null, editorAutoEnabled: false },
            readBatchTagPickerValue: () => [],
            readBatchRegionPickerValue: () => [],
            readBatchMultiPickerValue: () => ["PRODUCTION_RND"],
            gateToggleChecked: () => false,
            resolveBatchTemplateMailType: () => "INTRODUCTION",
            showStatus: () => {},
            hideBatchConfigEditor: () => {},
            loadBatchConfigList: () => {},
            api: async (url, options) => {
                bodies.push(JSON.parse(options.body));
                return {};
            }
        };
        vm.createContext(sandbox);
        vm.runInContext(extractFn("saveBatchConfigEditor"), sandbox);
        vm.runInContext(extractFn("buildConfigEditorRecipientSnapshot"), sandbox);

        elements.get("batchConfigEditorName").value = "介绍邮件任务";
        elements.get("batchConfigEditorFrequency").value = "daily";
        elements.get("batchConfigEditorTime").value = "07:30";

        elements.get("batchConfigEditorEmailVerification").checked = true;
        await sandbox.saveBatchConfigEditor();
        assert.strictEqual(bodies[0].emailVerificationEnabled, true,
            "an enabled switch must reach the config payload");

        const enabledSnapshot = sandbox.buildConfigEditorRecipientSnapshot();
        assert.strictEqual(enabledSnapshot.emailVerificationEnabled, true,
            "the recipient preview uses the same switch value as the save path");

        elements.get("batchConfigEditorEmailVerification").checked = false;
        await sandbox.saveBatchConfigEditor();
        assert.strictEqual(bodies[1].emailVerificationEnabled, false,
            "an unset switch must be submitted as false, never omitted");
        assert.strictEqual(sandbox.buildConfigEditorRecipientSnapshot().emailVerificationEnabled, false);
    });

    it("V2: MATERIAL_REMINDER disables the switch and forces it off", () => {
        const elements = createElementStore();
        const sandbox = {
            document: { getElementById: (id) => elements.get(id) },
            resolveBatchTemplateMailType: () => "MATERIAL_REMINDER"
        };
        vm.createContext(sandbox);
        loadVerificationFunctions(sandbox);

        elements.get("batchManualTemplateId").value = "7";
        elements.get("batchManualEmailVerification").checked = true;

        sandbox.refreshEmailVerificationState("manual");

        const field = elements.get("manualFieldEmailVerification");
        const checkbox = elements.get("batchManualEmailVerification");
        assert.strictEqual(checkbox.disabled, true, "material reminders must disable the switch");
        assert.strictEqual(checkbox.checked, false, "material reminders must explicitly submit false");
        assert.strictEqual(field.classList.contains("is-disabled"), true);
        assert.strictEqual(elements.get("batchManualEmailVerificationLabel").textContent, "已关闭");
        assert.strictEqual(elements.get("batchManualEmailVerificationHint").textContent, "仅介绍邮件支持发送前验证");
        assert.strictEqual(elements.get("editorFieldEmailVerification").classList.contains("is-disabled"), false,
            "the other panel must not be touched");
    });

    it("V3: switching back to an introduction template re-enables without turning it on", () => {
        const elements = createElementStore();
        let mailType = "MATERIAL_REMINDER";
        const sandbox = {
            document: { getElementById: (id) => elements.get(id) },
            resolveBatchTemplateMailType: () => mailType
        };
        vm.createContext(sandbox);
        loadVerificationFunctions(sandbox);

        elements.get("batchConfigEditorEmailVerification").checked = true;
        sandbox.refreshEmailVerificationState("editor");
        assert.strictEqual(elements.get("batchConfigEditorEmailVerification").checked, false);

        mailType = "INTRODUCTION";
        sandbox.refreshEmailVerificationState("editor");

        const checkbox = elements.get("batchConfigEditorEmailVerification");
        assert.strictEqual(checkbox.disabled, false, "introduction templates must re-enable the switch");
        assert.strictEqual(checkbox.checked, false, "re-enabling must never silently turn verification on");
        assert.strictEqual(elements.get("editorFieldEmailVerification").classList.contains("is-disabled"), false);
        assert.strictEqual(elements.get("batchConfigEditorEmailVerificationHint").textContent,
            "仅验证通过才发送；未通过跳过并标记邮箱异常。会消耗 Emailable 额度。");
    });

    it("V4: the manual draft keeps the switch and reports it as a diff against the source", () => {
        const elements = createElementStore();
        const sandbox = {
            batchTaskState: { manualSource: null },
            document: { getElementById: (id) => elements.get(id) },
            readBatchTagPickerValue: () => [],
            readBatchRegionPickerValue: () => [],
            readBatchMultiPickerValue: () => [],
            resolveBatchTemplateMailType: () => "INTRODUCTION"
        };
        vm.createContext(sandbox);
        [
            "deepCloneConfig",
            "normalizeManualSnapshot",
            "readManualFormValues",
            "formatManualDiffValue",
            "computeManualDiffs",
            "computeAndRenderDiffs",
            "clearAllDiffMarkers"
        ].forEach((name) => vm.runInContext(extractFn(name), sandbox));

        assert.strictEqual(sandbox.deepCloneConfig({ id: 1, emailVerificationEnabled: true }).emailVerificationEnabled, true,
            "selecting a source config must carry the switch");
        assert.strictEqual(sandbox.deepCloneConfig({ id: 1 }).emailVerificationEnabled, false,
            "a legacy config without the column must clone as false");

        const source = {
            id: 1, configName: "介绍邮件任务", emailVerificationEnabled: false,
            templateId: null, funnelLevel: null, tags: [], regions: [], emailDomains: [],
            discipline: null, researchDirectionFilter: "ANY", operatorStatuses: [],
            expertTypes: [], senderAccountCodes: [], gateFilterEnabled: false,
            roundSize: null, roundsPerRun: null, perMailIntervalMs: null,
            perRoundIntervalMs: null, selfCheckTtlMinutes: null
        };
        sandbox.batchTaskState.manualSource = source;

        assert.strictEqual(sandbox.computeManualDiffs().length, 0, "identical values must not read as a diff");

        elements.get("batchManualEmailVerification").checked = true;
        const diffs = sandbox.computeManualDiffs();
        assert.strictEqual(diffs.length, 1, "the switch is the only changed field");
        assert.strictEqual(diffs[0].key, "emailVerificationEnabled");
        assert.strictEqual(diffs[0].label, "发送前验证邮箱");
        assert.strictEqual(diffs[0].oldDisplay, "关闭");
        assert.strictEqual(diffs[0].newDisplay, "开启");

        sandbox.computeAndRenderDiffs();
        const field = elements.get("manualFieldEmailVerification");
        assert.strictEqual(field.classList.contains("is-config-diff"), true, "the field must be marked as modified");
        assert.strictEqual(field.querySelector(".batch-config-diff-original").textContent, "原：关闭");
        assert.strictEqual(source.emailVerificationEnabled, false, "diffing must never write back to the source config");
    });

    it("V5: the manual execution posts this run's switch and never rewrites the source config", async () => {
        const calls = [];
        const values = {
            mailType: "INTRODUCTION", roundsPerRun: 1, roundSize: 5,
            perMailIntervalMs: 1000, perRoundIntervalMs: 60000, selfCheckTtlMinutes: 30,
            funnelLevel: "CANDIDATE", tags: [], regions: [], emailDomains: [], discipline: null,
            operatorStatuses: [], expertTypes: [], senderAccountCodes: [], researchDirectionFilter: "ANY",
            gateFilterEnabled: false, emailVerificationEnabled: true, templateId: 7
        };
        const sandbox = {
            batchTaskState: { manualSource: { id: 7 }, manualDraft: {} },
            readManualFormValues: () => values,
            document: { getElementById: () => null },
            closeBatchManualConfirmDialog: () => {},
            showStatus: () => {},
            openBatchConfigLogs: () => {},
            openBatchExecutionLogs: () => {},
            api: async (url, options) => {
                calls.push({ url, method: options.method, body: JSON.parse(options.body) });
                return { executionId: 55 };
            }
        };
        vm.createContext(sandbox);
        vm.runInContext(extractFn("buildManualExecutionSnapshot"), sandbox);
        vm.runInContext(extractFn("confirmManualExecution"), sandbox);

        assert.strictEqual(sandbox.buildManualExecutionSnapshot().emailVerificationEnabled, true,
            "the execution snapshot must carry the switch for this run only");

        await sandbox.confirmManualExecution();

        assert.strictEqual(calls.length, 1, "a manual run must not touch the config endpoints");
        assert.strictEqual(calls[0].body.snapshot.emailVerificationEnabled, true);
        assert.ok(calls[0].url.indexOf("/manual-executions") >= 0,
            "a manual run must post to the manual-executions route");
    });

    it("V6: the confirm dialog shows this run's switch value", () => {
        const elements = createElementStore();
        const sandbox = {
            batchTaskState: { manualSource: { id: 7, configName: "介绍邮件任务", roundsPerRun: 2, roundSize: 20 } },
            document: { getElementById: (id) => elements.get(id) },
            escapeHtml: (value) => String(value == null ? "" : value),
            computeManualDiffs: () => []
        };
        vm.createContext(sandbox);
        vm.runInContext(extractFn("showBatchManualConfirm"), sandbox);

        elements.get("batchManualEmailVerification").checked = true;
        sandbox.showBatchManualConfirm();
        assert.ok(elements.get("batchManualConfirmBody").innerHTML.includes("发送前验证邮箱: 开启"),
            "the confirmation must spell out this run's value");
        assert.ok(!elements.get("batchManualConfirmBody").innerHTML.includes("undefined"));

        elements.get("batchManualEmailVerification").checked = false;
        sandbox.showBatchManualConfirm();
        assert.ok(elements.get("batchManualConfirmBody").innerHTML.includes("发送前验证邮箱: 关闭"));
    });

    it("V7: the task row shows the verification pill next to the gate pill", () => {
        const sandbox = {
            escapeHtml: (value) => String(value == null ? "" : value),
            regionLabel: (value) => value || "",
            cronToDisplayText: () => "",
            renderBatchConfigStatusToggle: () => "",
            batchGatePillHtml: () => '<span class="batch-gate-pill">门禁过滤 · 开</span>'
        };
        vm.createContext(sandbox);
        vm.runInContext(extractFn("renderBatchConfigRow"), sandbox);

        const base = { id: 1, configName: "任务", mailType: "INTRODUCTION", cron: null, nextFireTime: null, lastExecutedAt: null };
        const on = sandbox.renderBatchConfigRow(Object.assign({}, base, { emailVerificationEnabled: true }));
        const off = sandbox.renderBatchConfigRow(Object.assign({}, base, { emailVerificationEnabled: false }));

        assert.ok(on.includes('<span class="batch-gate-pill">邮箱验证 · 开</span>'));
        assert.ok(off.includes('<span class="batch-gate-pill is-off">邮箱验证 · 关</span>'));
    });
});

describe("batch email verification detail area (I-2 / I-3 / I-4 / S-2)", () => {
    it("V8: a snapshot without the switch reports 未启用邮箱验证 and never fabricates zeros", () => {
        const { sandbox, elements } = verificationSandbox();
        sandbox.renderBatchEmailVerification(
            {
                executionId: 101, enabled: false,
                summary: { total: 0, pending: 0, passed: 0, rejected: 0, errors: 0, tagFailed: 0 },
                items: [], nextAfterId: null, hasMore: false
            },
            "SUCCESS"
        );

        assert.strictEqual(elements.get("batchLogEmailVerificationNote").textContent, "未启用邮箱验证");
        assert.strictEqual(elements.get("batchLogEmailVerificationMetrics").innerHTML, "",
            "a disabled run must not show a 0/0/0 metric block");
        assert.strictEqual(elements.get("batchLogEmailVerificationTableWrap").hidden, true);
        assert.strictEqual(elements.get("batchLogEmailVerificationPager").hidden, true);
    });

    it("V9: an enabled run with no rows yet reports 尚未进入邮箱验证", () => {
        const { sandbox, elements } = verificationSandbox();
        sandbox.renderBatchEmailVerification(
            {
                executionId: 101, enabled: true,
                summary: { total: 0, pending: 0, passed: 0, rejected: 0, errors: 0, tagFailed: 0 },
                items: [], nextAfterId: null, hasMore: false
            },
            "RUNNING"
        );

        assert.strictEqual(elements.get("batchLogEmailVerificationNote").textContent, "尚未进入邮箱验证");
        assert.strictEqual(elements.get("batchLogEmailVerificationTableWrap").hidden, true);
    });

    it("V10: the three cells come from the whole-execution aggregate and pending stays out of them", () => {
        const { sandbox, elements } = verificationSandbox();
        sandbox.renderBatchEmailVerification(
            verificationPayload({ summary: { total: 5, pending: 1, passed: 2, rejected: 1, errors: 1, tagFailed: 0 } }),
            "RUNNING"
        );

        const metrics = elements.get("batchLogEmailVerificationMetrics").innerHTML;
        assert.ok(metrics.includes('<div class="batch-log-metric is-success">'), "passed keeps is-success");
        assert.ok(metrics.includes('<div class="batch-log-metric is-skipped">'), "rejected keeps is-skipped");
        assert.ok(metrics.includes('<div class="batch-log-metric is-failure">'), "service errors keep is-failure");
        assert.ok(metrics.includes("验证通过") && metrics.includes("未通过") && metrics.includes("服务异常"));
        assert.strictEqual(metrics.split('class="batch-log-metric ').length - 1, 3, "exactly three cells");

        const note = elements.get("batchLogEmailVerificationNote").textContent;
        assert.ok(note.includes("当前有 1 条仍在验证中"), "pending is reported as text, not as passed");
        assert.ok(note.includes("服务异常会停止本次执行"));
    });

    it("V11: PASS is not send success, SKIP keeps the provider verdict, ERROR is a service fault, tag failures are labelled", () => {
        const { sandbox, elements } = verificationSandbox();
        sandbox.renderBatchEmailVerification(
            verificationPayload({
                items: [
                    verificationRow({ id: 1, decision: "PASS", sendStatus: "NOT_SENT", sendReason: "TEMPLATE_RENDER_FAILED", providerState: "deliverable" }),
                    verificationRow({ id: 2, decision: "SKIP", providerState: "undeliverable", providerReason: "rejected_email", sendStatus: "SKIPPED", sendReason: "EMAIL_VERIFICATION_REJECTED", tagStatus: "APPLIED" }),
                    verificationRow({ id: 3, decision: "ERROR", providerState: null, providerReason: null, errorCode: "EMAIL_VERIFY_AUTH_ERROR", sendStatus: "NOT_SENT", tagStatus: "NOT_REQUIRED" }),
                    verificationRow({ id: 4, decision: "SKIP", providerState: "risky", providerReason: null, sendStatus: "SKIPPED", sendReason: "EMAIL_VERIFICATION_REJECTED", tagStatus: "FAILED", tagError: "ES_WRITE_FAILED:CANDIDATE" })
                ]
            }),
            "SUCCESS"
        );

        const html = elements.get("batchLogEmailVerificationRows").innerHTML;
        assert.ok(html.includes("未发送（模板渲染失败（TEMPLATE_RENDER_FAILED））"),
            "a passing address that never reached SMTP must show 未发送 with its own reason");
        assert.ok(html.includes('<span class="badge warn">未通过</span>'), "SKIP must read as not passed");
        assert.ok(html.includes("undeliverable / rejected_email"), "the provider state/reason must survive verbatim");
        assert.ok(html.includes("已跳过"));
        assert.ok(html.includes('<span class="badge error">验证服务异常</span>'));
        assert.ok(html.includes("验证服务鉴权失败（EMAIL_VERIFY_AUTH_ERROR）"),
            "controlled error codes get a Chinese meaning plus the code");
        assert.ok(html.includes("已标记邮箱异常"));
        assert.ok(html.includes("标签写入失败（写入标签失败（ES_WRITE_FAILED:CANDIDATE））"));
        assert.ok(html.includes('<span class="badge error">标签写入失败'),
            "a failed tag write must carry a textual badge, not colour alone");
        assert.ok(html.includes("请求次数：1"));
        assert.ok(html.includes("验证时间："));
    });

    it("history reuse shows the original time, zero requests and escaped source id", () => {
        const { sandbox } = verificationSandbox();
        const row = verificationRow({ reusedFromId: 42, requestCount: 0, sendStatus: "NOT_SENT" });
        const html = sandbox.batchEmailVerificationRowHtml(row);
        assert.ok(html.includes("复用历史验证（原始记录 #42）"));
        assert.ok(html.includes("请求次数：0"));
        assert.ok(html.includes("未发送"));
        assert.ok(html.includes(sandbox.formatDateTime(row.checkedAt)));
        const escaped = sandbox.batchEmailVerificationRowHtml(Object.assign({}, row, { reusedFromId: '<img onerror="boom">' }));
        assert.ok(!escaped.includes('<img'));
        assert.ok(escaped.includes('&lt;img'));
        const legacy = sandbox.batchEmailVerificationRowHtml(Object.assign({}, row, { reusedFromId: null }));
        assert.ok(legacy.includes("复用验证结果（原始记录未关联）"));
        const pending = sandbox.batchEmailVerificationRowHtml(verificationRow({ decision: "PENDING", requestCount: 0 }));
        assert.ok(pending.includes("尚未调用"));
        assert.ok(!pending.includes("复用历史验证"));
    });

    it("V12: a still-SENDING row only becomes 结果未确认 once the run reaches a final state", () => {
        const { sandbox, elements } = verificationSandbox();
        const payload = verificationPayload({
            items: [verificationRow({ decision: "PASS", sendStatus: "SENDING", checkedAt: null, requestCount: 2 })]
        });

        sandbox.renderBatchEmailVerification(payload, "RUNNING");
        assert.ok(elements.get("batchLogEmailVerificationRows").innerHTML.includes("发送中"));

        sandbox.renderBatchEmailVerification(payload, "SUCCESS");
        const html = elements.get("batchLogEmailVerificationRows").innerHTML;
        assert.ok(html.includes("结果未确认"), "a final run must not claim the send is still in flight");
        assert.ok(!html.includes(">未发送<"), "an unconfirmed send must not be reported as not sent");
    });

    it("V13: a pending row is never rendered as passed, and says so plainly after the run ends", () => {
        const { sandbox, elements } = verificationSandbox();
        sandbox.renderBatchEmailVerification(
            verificationPayload({
                summary: { total: 1, pending: 1, passed: 0, rejected: 0, errors: 0, tagFailed: 0 },
                items: [verificationRow({
                    decision: "PENDING", sendStatus: "NOT_SENT", providerState: null,
                    checkedAt: null, requestCount: 0, tagStatus: "NOT_REQUIRED"
                })]
            }),
            "SUCCESS"
        );

        const html = elements.get("batchLogEmailVerificationRows").innerHTML;
        assert.ok(html.includes('<span class="badge info">验证未完成</span>'));
        assert.ok(html.includes("未发送"));
        assert.ok(!html.includes(">通过<"), "pending rows must never be rendered as passed");
        assert.ok(elements.get("batchLogEmailVerificationNote").textContent.includes("仍未完成验证"));
    });

    it("V14: provider text is escaped, so a hostile reason cannot become markup", () => {
        const { sandbox, elements } = verificationSandbox();
        const hostileState = '<img src=x onerror="alert(1)">';
        sandbox.renderBatchEmailVerification(
            verificationPayload({
                items: [verificationRow({
                    decision: "SKIP",
                    providerState: hostileState,
                    providerReason: "</td></tr><script>alert(2)</script>"
                })]
            }),
            "SUCCESS"
        );

        const html = elements.get("batchLogEmailVerificationRows").innerHTML;
        assert.ok(html.includes("&lt;img src=x onerror=&quot;alert(1)&quot;&gt;"), "the raw markup must be escaped");
        assert.ok(!html.includes("<img"), "no element may be created from provider text");
        assert.ok(!html.includes("<script>"), "no script may be created from provider text");
    });

    it("V15: next pushes a cursor and prev pops it, and each request carries afterId/limit", async () => {
        const urls = [];
        const responses = [];
        const { sandbox, state } = verificationSandbox({
            state: { logExecutionId: 101, logConfigId: 7, logMode: "config" },
            extra: {
                api: async (url) => {
                    urls.push(url);
                    return new Promise((resolve) => responses.push(resolve));
                }
            }
        });

        const first = sandbox.loadBatchEmailVerification(7, 101, "RUNNING");
        assert.strictEqual(state.verificationLoading, true, "an in-flight page must be tracked");
        responses.shift()(verificationPayload({ hasMore: true, nextAfterId: 50 }));
        await first;
        assert.strictEqual(urls[0], "/api/mail/batch-send/executions/101/email-verifications?afterId=0&limit=50&configId=7");

        sandbox.batchEmailVerificationNextPage();
        assert.strictEqual(urls[1], "/api/mail/batch-send/executions/101/email-verifications?afterId=50&limit=50&configId=7");
        assert.deepStrictEqual(Array.from(state.verificationCursorStack), [0]);
        responses.shift()(verificationPayload({ hasMore: false, nextAfterId: null }));
        await flush();

        sandbox.batchEmailVerificationNextPage();
        assert.strictEqual(urls.length, 2, "no next page may be requested when hasMore is false");

        sandbox.batchEmailVerificationPrevPage();
        assert.strictEqual(urls[2], "/api/mail/batch-send/executions/101/email-verifications?afterId=0&limit=50&configId=7");
        assert.deepStrictEqual(Array.from(state.verificationCursorStack), []);
        responses.shift()(verificationPayload());
        await flush();
    });

    it("V16: polling never duplicates an in-flight page request and keeps the operator's page", async () => {
        const urls = [];
        const responses = [];
        const { sandbox, elements, state } = verificationSandbox({
            state: { logExecutionId: 101, logMode: "execution" },
            extra: {
                api: async (url) => {
                    urls.push(url);
                    return new Promise((resolve) => responses.push(resolve));
                }
            }
        });

        const pending = sandbox.loadBatchEmailVerification(null, 101, "RUNNING", { poll: true });
        sandbox.loadBatchEmailVerification(null, 101, "RUNNING", { poll: true });
        assert.strictEqual(urls.length, 1, "a poll must not start a second request for the same page");
        responses.shift()(verificationPayload({ hasMore: true, nextAfterId: 50 }));
        await pending;

        sandbox.batchEmailVerificationNextPage();
        responses.shift()(verificationPayload({
            hasMore: false,
            nextAfterId: null,
            items: [verificationRow({ id: 51, expertName: "李四", email: "lisi@university.edu" })]
        }));
        await flush();

        const poll = sandbox.loadBatchEmailVerification(null, 101, "RUNNING", { poll: true });
        assert.strictEqual(urls[2], "/api/mail/batch-send/executions/101/email-verifications?afterId=50&limit=50",
            "the poll must refresh the page the operator is on, not page 1");
        responses.shift()(verificationPayload({
            hasMore: false,
            items: [verificationRow({ id: 51, expertName: "李四", email: "lisi@university.edu" })]
        }));
        await poll;

        assert.deepStrictEqual(Array.from(state.verificationCursorStack), [0], "a poll must keep the cursor stack");
        assert.ok(elements.get("batchLogEmailVerificationRows").innerHTML.includes("lisi@university.edu"));
        assert.ok(elements.get("batchLogEmailVerificationPage").textContent.includes("第 2 页"));
        assert.strictEqual(state.verificationLoading, false, "a finished request must release the loading lock");
    });

    it("V17: switching execution discards the previous in-flight page", async () => {
        const urls = [];
        const responses = [];
        const { sandbox, elements, state } = verificationSandbox({
            state: { logExecutionId: 101, logMode: "execution" },
            extra: {
                api: async (url) => {
                    urls.push(url);
                    return new Promise((resolve) => responses.push(resolve));
                }
            }
        });

        const stale = sandbox.loadBatchEmailVerification(null, 101, "RUNNING");
        assert.strictEqual(state.verificationExecutionId, 101);
        state.logExecutionId = 202;
        const current = sandbox.loadBatchEmailVerification(null, 202, "RUNNING");
        assert.strictEqual(state.verificationExecutionId, 202, "the cursor belongs to the newly opened execution");

        responses.shift()(verificationPayload({ items: [verificationRow({ id: 9, email: "old@university.edu" })] }));
        await stale;
        responses.shift()(verificationPayload({ items: [verificationRow({ id: 90, email: "new@university.edu" })] }));
        await current;

        assert.strictEqual(urls[0], "/api/mail/batch-send/executions/101/email-verifications?afterId=0&limit=50");
        assert.strictEqual(urls[1], "/api/mail/batch-send/executions/202/email-verifications?afterId=0&limit=50");
        const html = elements.get("batchLogEmailVerificationRows").innerHTML;
        assert.ok(html.includes("new@university.edu"), "the current execution must win");
        assert.ok(!html.includes("old@university.edu"), "a stale response must never overwrite the newer render");
    });

    it("V18: a failed request reports the failure and never shows fabricated zeros", async () => {
        let fail = true;
        const { sandbox, elements } = verificationSandbox({
            state: { logExecutionId: 101, logMode: "execution" },
            extra: {
                api: async () => {
                    if (fail) throw new Error("500 Internal Server Error");
                    return verificationPayload({ items: [verificationRow({ id: 7, email: "kept@university.edu" })] });
                }
            }
        });

        await sandbox.loadBatchEmailVerification(null, 101, "SUCCESS");
        const note = elements.get("batchLogEmailVerificationNote");
        assert.ok(note.textContent.includes("验证明细加载失败"), "the failure must be explicit");
        assert.strictEqual(note.classList.contains("is-error"), true);
        assert.strictEqual(elements.get("batchLogEmailVerificationMetrics").innerHTML, "",
            "a first failure must not render a 0/0/0 summary");
        assert.strictEqual(elements.get("batchLogEmailVerificationTableWrap").hidden, true);

        fail = false;
        await sandbox.loadBatchEmailVerification(null, 101, "SUCCESS");
        assert.ok(elements.get("batchLogEmailVerificationRows").innerHTML.includes("kept@university.edu"));

        fail = true;
        await sandbox.loadBatchEmailVerification(null, 101, "SUCCESS", { poll: true });
        assert.ok(note.textContent.includes("验证明细加载失败"), "the failure stays visible");
        assert.ok(note.textContent.includes("可能是上一次结果"), "loaded rows must be labelled as possibly stale");
        assert.ok(elements.get("batchLogEmailVerificationRows").innerHTML.includes("kept@university.edu"),
            "already-loaded rows must not be wiped by a failed refresh");
    });

    it("V19: expanded rows are remembered by id so polling keeps them open", () => {
        const { sandbox, elements, state } = verificationSandbox();

        sandbox.batchEmailVerificationTrackExpanded(1, true);
        sandbox.batchEmailVerificationTrackExpanded(1, true);
        assert.deepStrictEqual(state.verificationExpandedIds, ["1"], "an expansion is tracked once");
        sandbox.batchEmailVerificationTrackExpanded(2, true);
        sandbox.batchEmailVerificationTrackExpanded(1, false);
        assert.deepStrictEqual(state.verificationExpandedIds, ["2"]);

        sandbox.renderBatchEmailVerification(
            verificationPayload({ items: [verificationRow({ id: 1 }), verificationRow({ id: 2 })] }),
            "RUNNING"
        );
        const html = elements.get("batchLogEmailVerificationRows").innerHTML;
        assert.ok(html.includes('data-verification-id="2" open'), "the re-rendered row stays expanded");
        assert.ok(html.includes('data-verification-id="1">'), "untracked rows stay collapsed");
    });

    it("V20: paging controls are locked while loading and at the cursor boundaries", async () => {
        const responses = [];
        const { sandbox, elements, state } = verificationSandbox({
            state: { logExecutionId: 101, logMode: "execution" },
            extra: { api: async () => new Promise((resolve) => responses.push(resolve)) }
        });

        const pending = sandbox.loadBatchEmailVerification(null, 101, "RUNNING");
        assert.strictEqual(elements.get("batchLogEmailVerificationPrev").disabled, true);
        assert.strictEqual(elements.get("batchLogEmailVerificationNext").disabled, true, "paging is locked while loading");

        responses.shift()(verificationPayload({ hasMore: true, nextAfterId: 50 }));
        await pending;
        assert.strictEqual(elements.get("batchLogEmailVerificationPrev").disabled, true, "no previous page from the first page");
        assert.strictEqual(elements.get("batchLogEmailVerificationNext").disabled, false);

        state.verificationLoading = true;
        sandbox.setBatchEmailVerificationPagerLoading(true);
        assert.strictEqual(elements.get("batchLogEmailVerificationNext").disabled, true);
    });

    it("V21: closing the drawer invalidates in-flight detail responses", async () => {
        const responses = [];
        const { sandbox, elements, state } = verificationSandbox({
            state: { logExecutionId: 101, logMode: "execution" },
            extra: { api: async () => new Promise((resolve) => responses.push(resolve)) }
        });

        const pending = sandbox.loadBatchEmailVerification(null, 101, "RUNNING");
        sandbox.resetBatchEmailVerification();
        assert.strictEqual(state.verificationLoading, false);
        assert.strictEqual(state.verificationExecutionId, null);

        responses.shift()(verificationPayload({ items: [verificationRow({ id: 3, email: "late@university.edu" })] }));
        await pending;

        assert.ok(!elements.get("batchLogEmailVerificationRows").innerHTML.includes("late@university.edu"),
            "a response that arrives after closing must not repaint the drawer");
        assert.strictEqual(elements.get("batchLogEmailVerificationNote").textContent, "");
    });
});

describe("batch email verification static contract (S-1 / S-2 / I-5)", () => {
    it("S1: both switches render the declared skeleton ids", () => {
        [
            "editorFieldEmailVerification", "batchConfigEditorEmailVerification",
            "batchConfigEditorEmailVerificationLabel", "batchConfigEditorEmailVerificationHint",
            "manualFieldEmailVerification", "batchManualEmailVerification",
            "batchManualEmailVerificationLabel", "batchManualEmailVerificationHint"
        ].forEach((id) => {
            assert.strictEqual(indexSource.split('id="' + id + '"').length - 1, 1, id + " must exist exactly once in index.html");
        });
        assert.strictEqual(indexSource.split('<span class="batch-config-field-label">发送前验证邮箱（Emailable）</span>').length - 1, 2,
            "both panels must label the switch identically");
        assert.ok(indexSource.includes("仅验证通过才发送；未通过跳过并标记邮箱异常。会消耗 Emailable 额度。仅影响本次执行。"),
            "the manual hint must spell out that it only affects this run");
        ["batchConfigEditorEmailVerification", "batchManualEmailVerification"].forEach((id) => {
            const at = indexSource.indexOf('id="' + id + '"');
            const tag = indexSource.slice(indexSource.lastIndexOf("<", at), indexSource.indexOf(">", at));
            assert.ok(tag.includes('type="checkbox"'), id + " must be a checkbox");
            assert.ok(!tag.includes("style="), id + " must not use inline styles");
        });
    });

    it("S2: the drawer carries the declared detail area between the metrics and the integrity warning", () => {
        [
            "batchLogEmailVerification", "batchLogEmailVerificationTitle", "batchLogEmailVerificationNote",
            "batchLogEmailVerificationMetrics", "batchLogEmailVerificationTableWrap", "batchLogEmailVerificationRows",
            "batchLogEmailVerificationPager", "batchLogEmailVerificationPrev", "batchLogEmailVerificationPage",
            "batchLogEmailVerificationNext"
        ].forEach((id) => {
            assert.strictEqual(indexSource.split('id="' + id + '"').length - 1, 1, id + " must exist exactly once in index.html");
        });

        const metricsAt = indexSource.indexOf('id="batchLogMetrics"');
        const verificationAt = indexSource.indexOf('id="batchLogEmailVerification"');
        const integrityAt = indexSource.indexOf('id="batchLogIntegrityWarning"');
        assert.ok(metricsAt > 0 && metricsAt < verificationAt && verificationAt < integrityAt,
            "the verification area sits after batchLogMetrics and before integrityWarning (I-5)");

        const area = indexSource.slice(verificationAt, integrityAt);
        assert.ok(area.includes('<th scope="col">专家 / 邮箱</th>') && area.includes('<th scope="col">验证结果</th>')
            && area.includes('<th scope="col">发送结果</th>') && area.includes('<th scope="col">标签处理</th>'),
            "four fixed columns");
        assert.ok(area.includes('class="batch-log-metrics"'), "the summary reuses the existing metric class");
        assert.ok(!area.includes("style="), "new DOM must not use inline styles");
    });

    it("S2: the appended stylesheet block is verbatim and the reused rules stay untouched", () => {
        const block = [
            "/* 批量介绍邮件：Emailable 执行明细（仅此区域） */",
            ".batch-email-verification { margin: 14px 0; }",
            ".batch-email-verification h4 { margin: 0 0 8px; font-size: 13px; color: var(--text-main); }",
            ".batch-email-verification-note { margin: 6px 0; color: var(--text-sidebar); font-size: 12px; line-height: 1.6; }",
            ".batch-email-verification-note.is-error { color: var(--error-strong); }",
            ".batch-email-verification-table-wrap { max-width: 100%; overflow-x: auto; border: 1px solid var(--panel-border); border-radius: var(--radius-md); }",
            ".batch-email-verification-table { width: 100%; min-width: 560px; border-collapse: collapse; font-size: 12px; line-height: 1.5; }",
            ".batch-email-verification-table th, .batch-email-verification-table td { padding: 8px 10px; text-align: left; vertical-align: top; border-bottom: 1px solid var(--panel-border); overflow-wrap: anywhere; }",
            ".batch-email-verification-table th { color: var(--text-sidebar); background: var(--bg-subtle); font-weight: 600; }",
            ".batch-email-verification-table tbody tr:last-child td { border-bottom: 0; }",
            ".batch-email-verification-table details { margin-top: 4px; color: var(--text-secondary); }",
            ".batch-email-verification-table summary { cursor: pointer; color: var(--primary); }",
            ".batch-email-verification-table summary:hover { color: var(--primary-hover); }",
            ".batch-email-verification-table summary:active { color: var(--primary-active); }",
            ".batch-email-verification-table summary:focus-visible { outline: 2px solid var(--primary); outline-offset: 2px; border-radius: var(--radius-sm); }",
            ".batch-email-verification-pager { display: flex; align-items: center; justify-content: flex-end; flex-wrap: wrap; gap: 8px; margin-top: 8px; }",
            ".batch-email-verification-pager .button:disabled { opacity: .5; cursor: not-allowed; pointer-events: none; }"
        ].join("\n");
        assert.ok(stylesSource.includes(block), "the S-2 block must appear verbatim");
        assert.strictEqual(stylesSource.split("/* 批量介绍邮件：Emailable 执行明细（仅此区域） */").length - 1, 1);
        assert.ok(stylesSource.includes(".batch-log-metrics { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; margin: 14px 0; }"),
            "the reused summary rules must stay byte-identical");
        assert.ok(stylesSource.includes(".batch-log-drawer {\n  position: absolute;\n  top: 0;\n  right: 0;\n  bottom: 0;\n  z-index: 4;\n  width: min(620px, 72%);"),
            "the drawer keeps its declared width");
        assert.ok(stylesSource.includes(".batch-task-status-toggle input:checked + .batch-task-status-switch {\n  background: var(--primary);\n}"),
            "the reused switch rules must stay byte-identical");
    });

    it("I5: every versioned static asset shares one release key", () => {
        // The literal must stay only in index.html (taskActivityCenter I-8 scans src/test/js for it),
        // so this test derives the value instead of pinning it.
        const keys = [...indexSource.matchAll(/\?v=([^"']+)/g)].map((match) => match[1]);
        assert.strictEqual(keys.length, 11, "index.html must keep exactly the 11 versioned asset keys");
        assert.strictEqual(new Set(keys).size, 1, "all versioned keys must share one value: " + keys.join(", "));
        const cacheKey = keys[0];
        assert.match(cacheKey, /^[0-9]{8}-[a-z0-9-]+$/, "the shared key must be <yyyymmdd>-<slug>");
        assert.ok(indexSource.includes('href="styles.css?v=' + cacheKey + '"'));
        assert.ok(indexSource.includes('src="app.js?v=' + cacheKey + '"'));
        assert.ok(indexSource.includes('src="trust-reply-workbench.js?v=' + cacheKey + '"'),
            "the CSS / workbench / app triad must carry the same key");
    });

    it("I5: the new area only reads — it never issues a write request of its own", () => {
        const start = appJsSource.indexOf("// ── 邮箱验证明细区");
        const end = appJsSource.indexOf("function renderBatchTimeline(rows)");
        assert.ok(start > 0 && end > start, "the detail area must exist in app.js");
        const area = appJsSource.slice(start, end);
        assert.ok(area.includes('"/api/mail/batch-send/executions/"') && area.includes('"/email-verifications"') && area.includes('"?afterId="'),
            "the only backend call of the area is the read-only detail GET");
        assert.ok(!/method:\s*"(POST|PUT|PATCH|DELETE)"/.test(area),
            "the detail area must never issue a write request");
        assert.ok(!/apiKey|api_key|secret/i.test(area), "no credential may be referenced");
    });
});
