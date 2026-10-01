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

/** c3: 放行结果白名单 helper —— 由被抽取的 app.js 函数直接调用，必须与宿主函数一起加载。 */
const EMAIL_POLICY_HELPERS = [
    "batchEmailVerificationAllowedStates",
    "emailVerificationAllowedStateLabel",
    "emailVerificationPolicyFieldId",
    "emailVerificationPolicyOptionId",
    "normalizeEmailVerificationAllowedStates",
    "readEmailVerificationAllowedStates",
    "fillEmailVerificationAllowedStates",
    "updateEmailVerificationPolicyState",
    "emailVerificationAllowedStatesText",
    "emailVerificationAllowedStatesScopeText"
];

function loadEmailPolicyHelpers(sandbox) {
    EMAIL_POLICY_HELPERS.forEach((name) => vm.runInContext(extractFn(name), sandbox));
    return sandbox;
}

const VERIFICATION_FUNCTIONS = [
    "emailVerificationFieldId",
    "emailVerificationToggleId",
    "emailVerificationLabelId",
    "emailVerificationHintId",
    "emailVerificationToggleChecked",
    "updateEmailVerificationToggleLabel",
    "refreshEmailVerificationState",
    ...EMAIL_POLICY_HELPERS,
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
        loadEmailPolicyHelpers(sandbox);
        vm.runInContext(extractFn("saveBatchConfigEditor"), sandbox);
        vm.runInContext(extractFn("buildConfigEditorRecipientSnapshot"), sandbox);

        elements.get("batchConfigEditorName").value = "介绍邮件任务";
        elements.get("batchConfigEditorFrequency").value = "daily";
        elements.get("batchConfigEditorTime").value = "07:30";

        elements.get("batchConfigEditorEmailVerification").checked = true;
        elements.get("batchConfigEditorExcludeVerifiedUnavailableEmails").checked = true;
        await sandbox.saveBatchConfigEditor();
        assert.strictEqual(bodies[0].emailVerificationEnabled, true,
            "an enabled switch must reach the config payload");
        assert.strictEqual(bodies[0].excludeVerifiedUnavailableEmails, true,
            "the independent historical filter must reach the config payload");

        const enabledSnapshot = sandbox.buildConfigEditorRecipientSnapshot();
        assert.strictEqual(enabledSnapshot.emailVerificationEnabled, true,
            "the recipient preview uses the same switch value as the save path");
        assert.strictEqual(enabledSnapshot.excludeVerifiedUnavailableEmails, true);

        elements.get("batchConfigEditorEmailVerification").checked = false;
        elements.get("batchConfigEditorExcludeVerifiedUnavailableEmails").checked = false;
        await sandbox.saveBatchConfigEditor();
        assert.strictEqual(bodies[1].emailVerificationEnabled, false,
            "an unset switch must be submitted as false, never omitted");
        assert.strictEqual(sandbox.buildConfigEditorRecipientSnapshot().emailVerificationEnabled, false);
        assert.strictEqual(bodies[1].excludeVerifiedUnavailableEmails, false);
        assert.strictEqual(sandbox.buildConfigEditorRecipientSnapshot().excludeVerifiedUnavailableEmails, false);
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
        elements.get("batchManualExcludeVerifiedUnavailableEmails").checked = true;

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
        assert.strictEqual(elements.get("batchManualExcludeVerifiedUnavailableEmails").checked, true,
            "template type must not disable the independent historical filter");
        assert.strictEqual(elements.get("batchManualExcludeVerifiedUnavailableEmails").disabled, false);
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
            "按下方勾选结果放行；不可投递始终跳过并标记邮箱异常。单邮箱验证未完成、超时或响应异常时暂缓；鉴权、额度、限流或服务故障停止执行。会消耗 Emailable 额度。");
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
        loadEmailPolicyHelpers(sandbox);
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
            emailVerificationAllowedStates: ["deliverable", "risky", "unknown"],
            roundSize: null, roundsPerRun: null, perMailIntervalMs: null,
            perRoundIntervalMs: null, selfCheckTtlMinutes: null
        };
        sandbox.batchTaskState.manualSource = source;
        // 表单已按来源回填（三态），放行组与本用例无关，必须保持与来源一致以免多出一条差异。
        ["batchManualAllowDeliverable", "batchManualAllowRisky", "batchManualAllowUnknown"].forEach((id) => {
            elements.get(id).checked = true;
        });

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
            gateFilterEnabled: false, emailVerificationEnabled: true, excludeVerifiedUnavailableEmails: true, templateId: 7
        };
        const sandbox = {
            batchTaskState: { manualSource: { id: 7, excludeVerifiedUnavailableEmails: true }, manualDraft: {} },
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

        assert.strictEqual(sandbox.buildManualExecutionSnapshot().emailVerificationEnabled, true);
        assert.strictEqual(sandbox.buildManualExecutionSnapshot().excludeVerifiedUnavailableEmails, true,
            "the execution snapshot must carry both independent switches for this run");

        await sandbox.confirmManualExecution();

        assert.strictEqual(calls.length, 1, "a manual run must not touch the config endpoints");
        assert.strictEqual(calls[0].body.snapshot.emailVerificationEnabled, true);
        assert.strictEqual(calls[0].body.snapshot.excludeVerifiedUnavailableEmails, true);
        assert.strictEqual(sandbox.batchTaskState.manualSource.excludeVerifiedUnavailableEmails, true,
            "execution does not update the source config");
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
        loadEmailPolicyHelpers(sandbox);
        vm.runInContext(extractFn("showBatchManualConfirm"), sandbox);

        elements.get("batchManualEmailVerification").checked = true;
        elements.get("batchManualExcludeVerifiedUnavailableEmails").checked = true;
        sandbox.showBatchManualConfirm();
        assert.ok(elements.get("batchManualConfirmBody").innerHTML.includes("发送前验证邮箱: 开启"),
            "the confirmation must spell out this run's value");
        assert.ok(!elements.get("batchManualConfirmBody").innerHTML.includes("undefined"));
        assert.ok(elements.get("batchManualConfirmBody").innerHTML.includes("排除已验证不可用邮箱: 开启"));

        elements.get("batchManualEmailVerification").checked = false;
        sandbox.showBatchManualConfirm();
        assert.ok(elements.get("batchManualConfirmBody").innerHTML.includes("发送前验证邮箱: 关闭"));
        elements.get("batchManualExcludeVerifiedUnavailableEmails").checked = false;
        sandbox.showBatchManualConfirm();
        assert.ok(elements.get("batchManualConfirmBody").innerHTML.includes("排除已验证不可用邮箱: 关闭"));
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
        loadEmailPolicyHelpers(sandbox);
        vm.runInContext(extractFn("renderBatchConfigRow"), sandbox);

        const base = { id: 1, configName: "任务", mailType: "INTRODUCTION", cron: null, nextFireTime: null, lastExecutedAt: null };
        const on = sandbox.renderBatchConfigRow(Object.assign({}, base, {
            emailVerificationEnabled: true, excludeVerifiedUnavailableEmails: true
        }));
        const off = sandbox.renderBatchConfigRow(Object.assign({}, base, {
            emailVerificationEnabled: false, excludeVerifiedUnavailableEmails: false
        }));

        assert.ok(on.includes('<span class="batch-gate-pill">邮箱验证 · 开</span>'));
        assert.ok(off.includes('<span class="batch-gate-pill is-off">邮箱验证 · 关</span>'));
        assert.ok(on.includes('<span class="batch-gate-pill">过滤已验证不可用邮箱 · 开</span>'));
        assert.ok(off.includes('<span class="batch-gate-pill is-off">过滤已验证不可用邮箱 · 关</span>'));
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
        assert.ok(metrics.includes("策略放行") && metrics.includes("未通过") && metrics.includes("验证异常"));
        assert.ok(metrics.includes('<div class="batch-log-metric-label">验证异常</div><div class="batch-log-metric-value">1</div>'),
            "the renamed aggregate label keeps the original summary.errors count");
        assert.strictEqual(metrics.split('class="batch-log-metric ').length - 1, 3, "exactly three cells");

        const note = elements.get("batchLogEmailVerificationNote").textContent;
        assert.ok(note.includes("当前有 1 条仍在验证中"), "pending is reported as text, not as passed");
        assert.ok(note.includes("单邮箱验证未完成") && note.includes("鉴权、额度、限流或服务故障"));
    });

    it("risky and unknown are displayed as policy permission with provider evidence", () => {
        const { sandbox, elements } = verificationSandbox();
        sandbox.renderBatchEmailVerification(verificationPayload({ items: [
            verificationRow({ providerState: "risky", providerReason: "low_deliverability" }),
            verificationRow({ id: 2, providerState: "unknown", providerReason: "unavailable_smtp" })
        ] }), "SUCCESS");
        const html = elements.get("batchLogEmailVerificationRows").innerHTML;
        assert.ok(html.includes("按策略放行"));
        assert.ok(html.includes("risky / low_deliverability"));
        assert.ok(html.includes("unknown / unavailable_smtp"));
    });

    it("V11: PASS is not send success, SKIP keeps the provider verdict, ERROR is a service fault, tag failures are labelled", () => {
        const { sandbox, elements } = verificationSandbox();
        sandbox.renderBatchEmailVerification(
            verificationPayload({
                items: [
                    verificationRow({ id: 1, decision: "PASS", sendStatus: "NOT_SENT", sendReason: "TEMPLATE_RENDER_FAILED", providerState: "deliverable" }),
                    verificationRow({ id: 2, decision: "SKIP", providerState: "undeliverable", providerReason: "rejected_email", sendStatus: "SKIPPED", sendReason: "EMAIL_VERIFICATION_REJECTED", tagStatus: "APPLIED" }),
                    verificationRow({ id: 3, decision: "ERROR", providerState: null, providerReason: null, errorCode: "EMAIL_VERIFY_AUTH_ERROR", sendStatus: "NOT_SENT", tagStatus: "NOT_REQUIRED" }),
                    verificationRow({ id: 4, decision: "ERROR", providerState: null, providerReason: null, errorCode: "EMAIL_VERIFY_INCOMPLETE", sendStatus: "SKIPPED", sendReason: "EMAIL_VERIFICATION_DEFERRED", tagStatus: "NOT_REQUIRED" }),
                    verificationRow({ id: 5, decision: "SKIP", providerState: "risky", providerReason: null, sendStatus: "SKIPPED", sendReason: "EMAIL_VERIFICATION_REJECTED", tagStatus: "FAILED", tagError: "ES_WRITE_FAILED:CANDIDATE" })
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
        assert.ok(html.includes('<span class="badge warn">验证暂缓</span>'), "only explicitly deferred ERROR rows use the warning badge");
        assert.ok(html.includes("验证服务未返回结果（EMAIL_VERIFY_INCOMPLETE）"), "deferred rows retain their original error code");
        assert.ok(html.includes("邮箱验证暂缓，本次未发送（EMAIL_VERIFICATION_DEFERRED）"), "deferred send reason is explicit");
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
        assert.ok(indexSource.includes("按下方勾选结果放行；不可投递始终跳过并标记邮箱异常。单邮箱验证未完成、超时或响应异常时暂缓；鉴权、额度、限流或服务故障停止执行。会消耗 Emailable 额度。仅影响本次执行。"),
            "the manual hint must spell out local defer and global stop behavior");
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

function exclusionFilterSandbox(extra = {}) {
    const elements = createElementStore();
    const sandbox = Object.assign({
        document: { getElementById: (id) => elements.get(id) },
        batchTaskState: { manualSource: null, manualDraft: null, configs: [] },
        readBatchTagPickerValue: () => [],
        readBatchRegionPickerValue: () => [],
        readBatchMultiPickerValue: () => [],
        setBatchTagPickerValue: () => {},
        setBatchRegionPickerValue: () => {},
        setBatchMultiPickerValue: () => {},
        resolveBatchTemplateMailType: () => "INTRODUCTION",
        fillBatchManualTemplateSelector: () => {},
        fillBatchConfigEditorTemplateSelector: () => {},
        refreshBatchGateState: () => {},
        refreshEmailVerificationState: () => {},
        syncBatchConfigEditorScheduleFields: () => {},
        updateBatchConfigVolumeHint: () => {},
        computeAndRenderDiffs: () => {},
        scheduleRecipientPreview: () => {},
        updateGateToggleLabel: () => {},
        console: { warn: () => {} }
    }, extra);
    vm.createContext(sandbox);
    [
        "deepCloneConfig",
        "fillManualFormDefaults",
        "fillManualFormFromDraft",
        "readManualFormValues",
        "buildManualExecutionSnapshot",
        "normalizeManualSnapshot",
        "formatManualDiffValue",
        "computeManualDiffs",
        "computeAndRenderDiffs",
        "clearAllDiffMarkers",
        "excludeVerifiedUnavailableToggleId",
        "updateExcludeVerifiedUnavailableToggleLabel",
        "showBatchConfigEditor",
        "buildConfigEditorRecipientSnapshot",
        "baseHintHtml",
        "refreshRecipientPreview"
    ].forEach((name) => vm.runInContext(extractFn(name), sandbox));
    loadEmailPolicyHelpers(sandbox);
    return { sandbox, elements };
}

describe("verified-unavailable email filter UI (04-filter-ui)", () => {
    it("uses the approved, keyboard-operable filter markup and real DOM ids", () => {
        const ids = [
            "editorFieldExcludeVerifiedUnavailableEmails",
            "batchConfigEditorExcludeVerifiedUnavailableEmails",
            "batchConfigEditorExcludeVerifiedUnavailableEmailsLabel",
            "batchConfigEditorExcludeVerifiedUnavailableEmailsHint",
            "manualFieldExcludeVerifiedUnavailableEmails",
            "batchManualExcludeVerifiedUnavailableEmails",
            "batchManualExcludeVerifiedUnavailableEmailsLabel",
            "batchManualExcludeVerifiedUnavailableEmailsHint"
        ];
        ids.forEach((id) => {
            assert.strictEqual(indexSource.split('id="' + id + '"').length - 1, 1,
                id + " must exist exactly once in the real index.html");
        });
        for (const [id, hintId] of [
            ["batchConfigEditorExcludeVerifiedUnavailableEmails", "batchConfigEditorExcludeVerifiedUnavailableEmailsHint"],
            ["batchManualExcludeVerifiedUnavailableEmails", "batchManualExcludeVerifiedUnavailableEmailsHint"]
        ]) {
            const at = indexSource.indexOf('id="' + id + '"');
            const start = indexSource.lastIndexOf("<label", at);
            const end = indexSource.indexOf("</label>", at) + "</label>".length;
            const label = indexSource.slice(start, end);
            const input = indexSource.slice(indexSource.lastIndexOf("<input", at), indexSource.indexOf(">", at));
            assert.ok(label.includes('class="batch-task-status-toggle batch-gate-toggle"'), "reuse the existing switch label");
            assert.ok(input.includes('type="checkbox"') && input.includes('aria-label="排除已验证不可用邮箱"'));
            assert.ok(input.includes('aria-describedby="' + hintId + '"'));
            assert.ok(!input.includes("style="), "the switch has no inline styling");
        }
        assert.ok(indexSource.includes("按最近一年内的最新有效验证结果，排除不可投递邮箱；不发起新的验证请求。"));
        assert.ok(indexSource.includes("按最近一年内的最新有效验证结果，排除不可投递邮箱；不发起新的验证请求。仅影响本次执行，不修改原定时任务。"));
    });

    it("defaults new forms on, keeps missing source values off, and carries manual overrides only in the execution snapshot", () => {
        const { sandbox, elements } = exclusionFilterSandbox();
        vm.runInContext(extractFn("computeAndRenderDiffs"), sandbox);
        vm.runInContext(extractFn("showBatchConfigEditor"), sandbox);
        sandbox.showBatchConfigEditor(null);
        assert.strictEqual(elements.get("batchConfigEditorExcludeVerifiedUnavailableEmails").checked, true);
        sandbox.showBatchConfigEditor({ id: 1, configName: "旧配置" });
        assert.strictEqual(elements.get("batchConfigEditorExcludeVerifiedUnavailableEmails").checked, false,
            "a stored config that lacks the field displays off");
        assert.strictEqual(elements.get("batchConfigEditorExcludeVerifiedUnavailableEmailsLabel").textContent, "已关闭");
        sandbox.showBatchConfigEditor({ id: 2, configName: "新配置", excludeVerifiedUnavailableEmails: true });
        assert.strictEqual(elements.get("batchConfigEditorExcludeVerifiedUnavailableEmails").checked, true);

        assert.strictEqual(sandbox.deepCloneConfig({ id: 1 }).excludeVerifiedUnavailableEmails, false,
            "legacy source configs must default false");
        sandbox.fillManualFormDefaults();
        assert.strictEqual(sandbox.batchTaskState.manualDraft.excludeVerifiedUnavailableEmails, true,
            "an independent manual form defaults on");
        assert.strictEqual(elements.get("batchManualExcludeVerifiedUnavailableEmails").checked, true);
        assert.strictEqual(elements.get("batchManualExcludeVerifiedUnavailableEmailsLabel").textContent, "已开启");

        const source = sandbox.deepCloneConfig({ id: 7, configName: "来源", excludeVerifiedUnavailableEmails: true });
        sandbox.batchTaskState.manualSource = source;
        sandbox.batchTaskState.manualDraft = sandbox.deepCloneConfig(source);
        sandbox.fillManualFormFromDraft();
        const toggle = elements.get("batchManualExcludeVerifiedUnavailableEmails");
        assert.strictEqual(toggle.checked, true, "the chosen source populates the manual draft");
        toggle.checked = false;
        sandbox.computeAndRenderDiffs();
        const field = elements.get("manualFieldExcludeVerifiedUnavailableEmails");
        assert.strictEqual(field.classList.contains("is-config-diff"), true);
        assert.strictEqual(field.querySelector(".batch-config-diff-original").textContent, "原：开启");
        const diffs = sandbox.computeManualDiffs();
        const filterDiff = diffs.find((item) => item.key === "excludeVerifiedUnavailableEmails");
        assert.deepStrictEqual(JSON.parse(JSON.stringify(filterDiff)), {
            key: "excludeVerifiedUnavailableEmails",
            label: "排除已验证不可用邮箱",
            oldDisplay: "开启",
            newDisplay: "关闭"
        });
        const snapshot = sandbox.buildManualExecutionSnapshot();
        assert.strictEqual(snapshot.excludeVerifiedUnavailableEmails, false);
        assert.strictEqual(sandbox.batchTaskState.manualSource.excludeVerifiedUnavailableEmails, true,
            "overriding this run must not mutate its source");

        sandbox.batchTaskState.manualSource = null;
        sandbox.fillManualFormDefaults();
        assert.strictEqual(elements.get("batchManualExcludeVerifiedUnavailableEmails").checked, true,
            "clearing the source restores the independent default");

        sandbox.batchTaskState.manualSource = sandbox.deepCloneConfig({ id: 8, configName: "旧来源" });
        sandbox.batchTaskState.manualDraft = sandbox.deepCloneConfig(sandbox.batchTaskState.manualSource);
        sandbox.fillManualFormFromDraft();
        assert.strictEqual(elements.get("batchManualExcludeVerifiedUnavailableEmails").checked, false,
            "an old source with no field must not inherit the independent default");
        assert.strictEqual(elements.get("batchManualExcludeVerifiedUnavailableEmailsLabel").textContent, "已关闭");
        assert.strictEqual(sandbox.computeManualDiffs().some((item) => item.key === "excludeVerifiedUnavailableEmails"), false);
    });

    it("uses the selected gate response's total and exclusion count without an additional request", async () => {
        const elements = createElementStore();
        const calls = [];
        const responses = [
            { totalSendable: 18, excludedVerifiedUnavailable: 4, pending: 18, retryable: 0 },
            { totalSendable: 11, excludedVerifiedUnavailable: 2, pending: 11, retryable: 0 }
        ];
        const sandbox = Object.assign({
            document: { getElementById: (id) => elements.get(id) },
            recipientPreviewRequestSeq: { editor: 0 },
            batchGateState: { editor: { available: true } },
            recipientPreviewHintId: () => "preview",
            buildConfigEditorRecipientSnapshot: () => ({ excludeVerifiedUnavailableEmails: true }),
            gateToggleId: () => "gate",
            api: async (url, options) => {
                calls.push({ url, body: JSON.parse(options.body) });
                return responses.shift();
            },
            console: { warn: () => {} }
        }, {});
        vm.createContext(sandbox);
        vm.runInContext(extractFn("baseHintHtml"), sandbox);
        vm.runInContext(extractFn("refreshRecipientPreview"), sandbox);
        elements.get("gate").checked = true;
        sandbox.refreshRecipientPreview("editor");
        await flush();
        assert.strictEqual(calls.length, 2, "the existing gate preview remains two requests");
        assert.ok(calls.every((call) => call.url === "/api/mail/batch-send/recipients/preview"));
        assert.ok(calls.every((call) => call.body.excludeVerifiedUnavailableEmails === true));
        assert.ok(elements.get("preview").innerHTML.includes("<strong>11</strong>"));
        assert.ok(elements.get("preview").innerHTML.includes("已排除不可用邮箱 <strong>2</strong> 位"),
            "use the selected response's count, not a difference between gate branches");
    });

    it("renders the historical exclusion count when the template gate is unavailable and defaults a missing count to zero", async () => {
        const elements = createElementStore();
        const calls = [];
        const sandbox = {
            document: { getElementById: (id) => elements.get(id) },
            recipientPreviewRequestSeq: { manual: 0 },
            batchGateState: { manual: { available: false } },
            recipientPreviewHintId: () => "preview",
            buildManualExecutionSnapshot: () => ({ excludeVerifiedUnavailableEmails: true }),
            gateToggleId: () => "gate",
            api: async (url, options) => {
                calls.push(JSON.parse(options.body));
                return { totalSendable: 6, pending: 6, retryable: 0 };
            },
            console: { warn: () => {} }
        };
        vm.createContext(sandbox);
        vm.runInContext(extractFn("baseHintHtml"), sandbox);
        vm.runInContext(extractFn("refreshRecipientPreview"), sandbox);
        sandbox.refreshRecipientPreview("manual");
        await flush();
        assert.strictEqual(calls.length, 1, "unavailable gate state retains its one-request path");
        assert.strictEqual(calls[0].excludeVerifiedUnavailableEmails, true);
        assert.ok(elements.get("preview").innerHTML.includes("<strong>6</strong>"));
        assert.ok(elements.get("preview").innerHTML.includes("已排除不可用邮箱 <strong>0</strong> 位"));
    });

    it("uses the gate-off branch when selected and clears counts on error without accepting an older response", async () => {
        const elements = createElementStore();
        const requests = [];
        let filterEnabled = true;
        const sandbox = {
            document: { getElementById: (id) => elements.get(id) },
            recipientPreviewRequestSeq: { editor: 0 },
            batchGateState: { editor: { available: true } },
            recipientPreviewHintId: () => "preview",
            buildConfigEditorRecipientSnapshot: () => ({ excludeVerifiedUnavailableEmails: filterEnabled }),
            gateToggleId: () => "gate",
            api: (url, options) => new Promise((resolve, reject) => {
                requests.push({ body: JSON.parse(options.body), resolve, reject });
            }),
            console: { warn: () => {} }
        };
        vm.createContext(sandbox);
        vm.runInContext(extractFn("baseHintHtml"), sandbox);
        vm.runInContext(extractFn("refreshRecipientPreview"), sandbox);

        elements.get("gate").checked = false;
        sandbox.refreshRecipientPreview("editor");
        requests[0].resolve({ totalSendable: 18, excludedVerifiedUnavailable: 4, pending: 18 });
        requests[1].resolve({ totalSendable: 11, excludedVerifiedUnavailable: 2, pending: 11 });
        await flush();
        assert.ok(elements.get("preview").innerHTML.includes("命中 <strong>18</strong>"));
        assert.ok(elements.get("preview").innerHTML.includes("已排除不可用邮箱 <strong>4</strong> 位"));

        sandbox.refreshRecipientPreview("editor");
        assert.ok(elements.get("preview").innerHTML.includes("计算中"));
        filterEnabled = false;
        sandbox.refreshRecipientPreview("editor");
        assert.strictEqual(requests.length, 6, "one existing pair per preview, never a third filter request");
        assert.ok(requests.slice(4).every((request) => request.body.excludeVerifiedUnavailableEmails === false));
        requests[4].resolve({ totalSendable: 22, excludedVerifiedUnavailable: 7, pending: 22 });
        requests[5].resolve({ totalSendable: 12, excludedVerifiedUnavailable: 5, pending: 12 });
        await flush();
        assert.ok(elements.get("preview").innerHTML.includes("命中 <strong>22</strong>"));
        assert.ok(!elements.get("preview").innerHTML.includes("已排除不可用邮箱"),
            "the disabled filter must not display the response's excluded count");
        requests[2].resolve({ totalSendable: 100, excludedVerifiedUnavailable: 80 });
        requests[3].resolve({ totalSendable: 90, excludedVerifiedUnavailable: 70 });
        await flush();
        assert.ok(elements.get("preview").innerHTML.includes("命中 <strong>22</strong>"),
            "an older preview must not overwrite the latest value");

        filterEnabled = true;
        sandbox.refreshRecipientPreview("editor");
        requests[6].reject(new Error("ES unavailable"));
        requests[7].resolve({ totalSendable: 9, excludedVerifiedUnavailable: 3 });
        await flush();
        assert.strictEqual(elements.get("preview").textContent, "预估失败：ES unavailable");
        assert.ok(!elements.get("preview").innerHTML.includes("已排除不可用邮箱"),
            "failed preview must not retain a stale count");
    });
});

// ── c3：Emailable 放行结果白名单（I-1～I-4 / S-1～S-3） ────────────────────────────────

/** 只加载放行组 helper + 一个 DOM stub store 的最小 sandbox。 */
function policySandbox(extra = {}) {
    const elements = createElementStore();
    const sandbox = Object.assign({
        document: { getElementById: (id) => (extra.missingElements || []).includes(id) ? null : elements.get(id) }
    }, extra.extra || {});
    vm.createContext(sandbox);
    loadEmailPolicyHelpers(sandbox);
    return { sandbox, elements };
}

describe("batch email verification allow-list (c3 / I-1 / I-2 / I-3 / I-4 / S-2)", () => {
    it("S-2: both panels carry one real three-state checkbox group with unique ids and the declared CSS", () => {
        const ids = [
            "editorFieldEmailVerificationAllowedStates", "batchConfigEditorEmailVerificationAllowedStatesLabel",
            "batchConfigEditorEmailVerificationPolicyHint", "manualFieldEmailVerificationAllowedStates",
            "batchManualEmailVerificationAllowedStatesLabel", "batchManualEmailVerificationPolicyHint"
        ];
        ids.forEach((id) => {
            assert.strictEqual(indexSource.split('id="' + id + '"').length - 1, 1,
                id + " must exist exactly once in the real index.html");
        });
        [["batchConfigEditorAllow", "editor"], ["batchManualAllow", "manual"]].forEach(([prefix]) => {
            [["Deliverable", "deliverable"], ["Risky", "risky"], ["Unknown", "unknown"]].forEach(([suffix, value]) => {
                const id = prefix + suffix;
                assert.strictEqual(indexSource.split('id="' + id + '"').length - 1, 1,
                    id + " must exist exactly once in the real index.html");
                const at = indexSource.indexOf('id="' + id + '"');
                const tag = indexSource.slice(indexSource.lastIndexOf("<", at), indexSource.indexOf(">", at));
                assert.ok(tag.includes('type="checkbox"'), id + " must be a real checkbox");
                assert.ok(tag.includes('value="' + value + '"'), id + " must carry its literal state value");
                assert.ok(!tag.includes("style="), id + " must not use inline styles");
            });
        });
        assert.strictEqual((indexSource.match(/class="batch-email-policy-option"/g) || []).length, 6,
            "exactly two groups of three options");
        assert.ok(!indexSource.includes("AllowUndeliverable"),
            "undeliverable must never be a selectable option");
        ["batchConfigEditorEmailVerificationAllowedStatesLabel", "batchManualEmailVerificationAllowedStatesLabel"].forEach((labelId, i) => {
            const hintId = i === 0 ? "batchConfigEditorEmailVerificationPolicyHint" : "batchManualEmailVerificationPolicyHint";
            assert.ok(indexSource.includes('role="group" aria-labelledby="' + labelId + '" aria-describedby="' + hintId + '"'),
                "the options must be a labelled group");
        });
        assert.strictEqual(indexSource.split("不可投递（undeliverable）始终跳过，不可放行。").length - 1, 2);
        assert.strictEqual(indexSource.split("仅在发送前验证开启时生效；未勾选结果跳过并记入日志，不占成功发信额度。全不选时不放行任何验证结果。").length - 1, 2,
            "both panels must state that the list only applies while verification is on");

        const block = [
            ".batch-email-policy-options {",
            "  display: flex;",
            "  flex-wrap: wrap;",
            "  gap: 9px 18px;",
            "  align-items: center;",
            "}",
            ".batch-config-field .batch-email-policy-option {",
            "  display: inline-flex;",
            "  flex-direction: row;",
            "  align-items: center;",
            "  gap: 7px;",
            "  min-height: 34px;",
            "  color: var(--text-main);",
            "  font-size: 13px;",
            "  cursor: pointer;",
            "}",
            ".batch-email-policy-option input[type=\"checkbox\"] {",
            "  width: 16px;",
            "  height: 16px;",
            "  min-height: 16px;",
            "  margin: 0;",
            "  padding: 0;",
            "  accent-color: var(--primary);",
            "}",
            ".batch-email-policy-option input[type=\"checkbox\"]:focus-visible {",
            "  outline: 2px solid var(--primary);",
            "  outline-offset: 2px;",
            "}",
            ".batch-email-policy-option small {",
            "  color: var(--text-muted);",
            "  font-size: 11px;",
            "}",
            ".batch-email-policy-fixed {",
            "  margin-top: 9px;",
            "  color: var(--text-muted);",
            "  font-size: 11px;",
            "}",
            ".batch-email-policy-hint {",
            "  margin-top: 9px;",
            "  color: var(--text-muted);",
            "  font-size: 11px;",
            "  line-height: 1.6;",
            "}",
            ".batch-email-policy-field.is-disabled {",
            "  opacity: .52;",
            "}",
            ".batch-email-policy-field.is-disabled .batch-email-policy-option,",
            ".batch-email-policy-field.is-disabled input[type=\"checkbox\"] {",
            "  cursor: not-allowed;",
            "}",
            "@media (max-width: 600px) {",
            "  .batch-email-policy-options { gap: 4px 10px; }",
            "}"
        ].join("\n");
        assert.ok(stylesSource.includes(block), "the S-2 allow-list CSS must appear verbatim");
        assert.strictEqual(stylesSource.split(".batch-email-policy-field.is-disabled {").length - 1, 1);
    });

    it("I-1: the editor fills from the API array in fixed order, keeps [] verbatim and defaults a new task to deliverable", () => {
        const { sandbox, elements } = policySandbox();

        sandbox.fillEmailVerificationAllowedStates("editor", ["deliverable", "risky"]);
        assert.deepStrictEqual(Array.from(sandbox.readEmailVerificationAllowedStates("editor")), ["deliverable", "risky"]);
        assert.strictEqual(elements.get("batchConfigEditorAllowUnknown").checked, false);

        sandbox.fillEmailVerificationAllowedStates("editor", ["unknown", "deliverable"]);
        assert.deepStrictEqual(Array.from(sandbox.readEmailVerificationAllowedStates("editor")), ["deliverable", "unknown"],
            "the stored/read order is the fixed deliverable,risky,unknown order");

        sandbox.fillEmailVerificationAllowedStates("editor", []);
        assert.deepStrictEqual(Array.from(sandbox.readEmailVerificationAllowedStates("editor")), [],
            "an explicit empty selection must never fall back to a default");

        sandbox.fillEmailVerificationAllowedStates("editor", undefined);
        assert.deepStrictEqual(Array.from(sandbox.readEmailVerificationAllowedStates("editor")),
            ["deliverable", "risky", "unknown"],
            "a legacy response without the field is the three-state compat branch, not the previous edit value");

        sandbox.fillEmailVerificationAllowedStates("editor", ["deliverable"]);
        assert.deepStrictEqual(Array.from(sandbox.readEmailVerificationAllowedStates("editor")), ["deliverable"],
            "a new task / explicit selection only allows deliverable");
        assert.strictEqual(elements.get("batchConfigEditorAllowRisky").checked, false);
        assert.strictEqual(elements.get("batchConfigEditorAllowUnknown").checked, false);
    });

    it("I-1: the editor save payload and the recipient preview carry exactly the checked states", async () => {
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
            api: async (url, options) => { bodies.push(JSON.parse(options.body)); return {}; }
        };
        vm.createContext(sandbox);
        loadEmailPolicyHelpers(sandbox);
        vm.runInContext(extractFn("saveBatchConfigEditor"), sandbox);
        vm.runInContext(extractFn("buildConfigEditorRecipientSnapshot"), sandbox);

        elements.get("batchConfigEditorName").value = "介绍邮件任务";
        elements.get("batchConfigEditorFrequency").value = "daily";
        elements.get("batchConfigEditorTime").value = "07:30";
        elements.get("batchConfigEditorEmailVerification").checked = true;

        elements.get("batchConfigEditorAllowRisky").checked = true;
        await sandbox.saveBatchConfigEditor();
        assert.deepStrictEqual(Array.from(bodies[0].emailVerificationAllowedStates), ["risky"],
            "only the checked states may be submitted");
        assert.deepStrictEqual(Array.from(sandbox.buildConfigEditorRecipientSnapshot().emailVerificationAllowedStates), ["risky"],
            "the preview must carry the same selection as the save path");

        elements.get("batchConfigEditorAllowRisky").checked = false;
        await sandbox.saveBatchConfigEditor();
        assert.deepStrictEqual(Array.from(bodies[1].emailVerificationAllowedStates), [],
            "an all-unchecked save must stay [] — never a three-state default");
        assert.deepStrictEqual(Array.from(sandbox.buildConfigEditorRecipientSnapshot().emailVerificationAllowedStates), []);
    });

    it("I-2: the master switch disables the group without losing the selection and material reminders keep it disabled", () => {
        const elements = createElementStore();
        let mailType = "INTRODUCTION";
        const sandbox = {
            document: { getElementById: (id) => elements.get(id) },
            resolveBatchTemplateMailType: () => mailType
        };
        vm.createContext(sandbox);
        loadVerificationFunctions(sandbox);

        sandbox.fillEmailVerificationAllowedStates("editor", ["deliverable", "risky"]);
        sandbox.refreshEmailVerificationState("editor");

        const group = elements.get("editorFieldEmailVerificationAllowedStates");
        const options = ["batchConfigEditorAllowDeliverable", "batchConfigEditorAllowRisky", "batchConfigEditorAllowUnknown"];
        options.forEach((id) => assert.strictEqual(elements.get(id).disabled, true, id + " must be disabled while the switch is off"));
        assert.strictEqual(group.classList.contains("is-disabled"), true);
        assert.strictEqual(elements.get("batchConfigEditorAllowDeliverable").checked, true, "off must keep the selection");
        assert.strictEqual(elements.get("batchConfigEditorAllowRisky").checked, true);

        elements.get("batchConfigEditorEmailVerification").checked = true;
        sandbox.updateEmailVerificationToggleLabel("editor");
        options.forEach((id) => assert.strictEqual(elements.get(id).disabled, false, id + " must be operable once the switch is on"));
        assert.strictEqual(group.classList.contains("is-disabled"), false);
        sandbox.refreshEmailVerificationState("editor");
        assert.strictEqual(elements.get("batchConfigEditorEmailVerificationLabel").textContent, "已开启");

        mailType = "MATERIAL_REMINDER";
        sandbox.refreshEmailVerificationState("editor");
        options.forEach((id) => assert.strictEqual(elements.get(id).disabled, true, "material reminders must disable the group"));
        assert.strictEqual(group.classList.contains("is-disabled"), true);

        mailType = "INTRODUCTION";
        sandbox.refreshEmailVerificationState("editor");
        assert.strictEqual(elements.get("batchConfigEditorEmailVerification").checked, false,
            "switching back to an introduction template must not turn verification on");
        options.forEach((id) => assert.strictEqual(elements.get(id).disabled, true,
            "an off switch keeps the group unavailable after switching back"));
        assert.strictEqual(elements.get("batchConfigEditorEmailVerificationHint").textContent,
            "按下方勾选结果放行；不可投递始终跳过并标记邮箱异常。单邮箱验证未完成、超时或响应异常时暂缓；鉴权、额度、限流或服务故障停止执行。会消耗 Emailable 额度。");
    });

    it("I-3: the manual panel clones the source array, diffs it in its own row and restores the independent default", () => {
        const { sandbox, elements } = exclusionFilterSandbox();

        sandbox.fillManualFormDefaults();
        assert.deepStrictEqual(Array.from(sandbox.batchTaskState.manualDraft.emailVerificationAllowedStates), ["deliverable"],
            "an independent manual run defaults to deliverable only");
        assert.deepStrictEqual(Array.from(sandbox.readEmailVerificationAllowedStates("manual")), ["deliverable"]);
        assert.strictEqual(elements.get("batchManualAllowRisky").checked, false);
        assert.strictEqual(elements.get("batchManualAllowUnknown").checked, false);

        const source = sandbox.deepCloneConfig({
            id: 7, configName: "来源任务", emailVerificationEnabled: true,
            emailVerificationAllowedStates: ["deliverable", "risky"]
        });
        assert.deepStrictEqual(Array.from(source.emailVerificationAllowedStates), ["deliverable", "risky"]);
        source.emailVerificationAllowedStates.push("unknown");
        assert.deepStrictEqual(Array.from(sandbox.deepCloneConfig({
            id: 7, configName: "来源任务", emailVerificationAllowedStates: ["deliverable", "risky"]
        }).emailVerificationAllowedStates), ["deliverable", "risky"],
            "the clone must be a copy, never a shared reference");

        const bound = sandbox.deepCloneConfig({ id: 7, configName: "来源任务", emailVerificationAllowedStates: ["deliverable", "risky"] });
        sandbox.batchTaskState.manualSource = bound;
        sandbox.batchTaskState.manualDraft = sandbox.deepCloneConfig(bound);
        sandbox.fillManualFormFromDraft();
        assert.strictEqual(elements.get("batchManualAllowDeliverable").checked, true);
        assert.strictEqual(elements.get("batchManualAllowRisky").checked, true);
        assert.strictEqual(elements.get("batchManualAllowUnknown").checked, false, "the source array rules the manual panel");

        elements.get("batchManualAllowUnknown").checked = true;
        const diffs = sandbox.computeManualDiffs();
        assert.strictEqual(diffs.length, 1, "only the allow-list changed");
        assert.deepStrictEqual(JSON.parse(JSON.stringify(diffs[0])), {
            key: "emailVerificationAllowedStates",
            label: "允许发送的验证结果",
            oldDisplay: "可投递、有风险",
            newDisplay: "可投递、有风险、未知"
        });
        sandbox.computeAndRenderDiffs();
        const allowField = elements.get("manualFieldEmailVerificationAllowedStates");
        assert.strictEqual(allowField.classList.contains("is-config-diff"), true);
        assert.strictEqual(allowField.querySelector(".batch-config-diff-original").textContent, "原：可投递、有风险");
        assert.strictEqual(elements.get("manualFieldEmailVerification").classList.contains("is-config-diff"), false,
            "the allow-list must not reuse the switch's diff container");
        assert.deepStrictEqual(Array.from(sandbox.buildManualExecutionSnapshot().emailVerificationAllowedStates),
            ["deliverable", "risky", "unknown"]);
        assert.deepStrictEqual(Array.from(bound.emailVerificationAllowedStates), ["deliverable", "risky"],
            "a manual override must never rewrite its source config");

        elements.get("batchManualAllowDeliverable").checked = false;
        elements.get("batchManualAllowRisky").checked = false;
        elements.get("batchManualAllowUnknown").checked = false;
        const emptyDiff = sandbox.computeManualDiffs();
        assert.strictEqual(emptyDiff.length, 1);
        assert.strictEqual(emptyDiff[0].newDisplay, "不放行任何结果",
            "an empty selection is displayed as an explicit decision, not as 不限");
        assert.deepStrictEqual(Array.from(sandbox.buildManualExecutionSnapshot().emailVerificationAllowedStates), []);

        sandbox.batchTaskState.manualSource = null;
        sandbox.fillManualFormDefaults();
        assert.deepStrictEqual(Array.from(sandbox.readEmailVerificationAllowedStates("manual")), ["deliverable"],
            "clearing the source restores the independent default");
    });

    it("I-3: the confirm dialog spells out the effective selection and 未启用 while verification is off", () => {
        const elements = createElementStore();
        const sandbox = {
            batchTaskState: { manualSource: { id: 7, configName: "介绍邮件任务", roundsPerRun: 2, roundSize: 20 } },
            document: { getElementById: (id) => elements.get(id) },
            escapeHtml: (value) => String(value == null ? "" : value),
            computeManualDiffs: () => []
        };
        vm.createContext(sandbox);
        loadEmailPolicyHelpers(sandbox);
        vm.runInContext(extractFn("showBatchManualConfirm"), sandbox);

        sandbox.showBatchManualConfirm();
        assert.ok(elements.get("batchManualConfirmBody").innerHTML.includes("允许发送的验证结果：未启用"),
            "an off switch shows 未启用 instead of a stale list");

        elements.get("batchManualEmailVerification").checked = true;
        elements.get("batchManualAllowDeliverable").checked = true;
        elements.get("batchManualAllowUnknown").checked = true;
        sandbox.showBatchManualConfirm();
        assert.ok(elements.get("batchManualConfirmBody").innerHTML.includes("允许发送的验证结果：可投递、未知"),
            "the confirmation must show this run's effective selection");

        elements.get("batchManualAllowDeliverable").checked = false;
        elements.get("batchManualAllowUnknown").checked = false;
        sandbox.showBatchManualConfirm();
        assert.ok(elements.get("batchManualConfirmBody").innerHTML.includes("允许发送的验证结果：不放行任何结果"));
    });

    it("I-4: a policy skip is explained as a selection skip, never as an invalid address", () => {
        const { sandbox, elements } = verificationSandbox();
        const policySkipRow = verificationRow({
            id: 1, decision: "SKIP", providerState: "risky", providerReason: "low_deliverability",
            sendStatus: "SKIPPED", sendReason: "EMAIL_VERIFICATION_POLICY_SKIP", tagStatus: "NOT_REQUIRED"
        });

        sandbox.renderBatchEmailVerification(verificationPayload({ items: [policySkipRow] }), "SUCCESS");
        const policyHtml = elements.get("batchLogEmailVerificationRows").innerHTML;
        assert.ok(policyHtml.includes('<span class="badge warn">按策略跳过</span>'),
            "a policy skip must be labelled as a policy decision");
        assert.ok(policyHtml.includes("未勾选该验证结果，本次未发送（EMAIL_VERIFICATION_POLICY_SKIP）"),
            "the send cell must say the state was not selected this run");
        assert.ok(policyHtml.includes("risky / low_deliverability"), "the provider verdict stays verbatim");
        assert.ok(policyHtml.includes("无需处理"), "a NOT_REQUIRED tag must read as nothing to do");
        assert.ok(!policyHtml.includes("未通过") && !policyHtml.includes("已标记邮箱异常"),
            "a policy skip must never be presented as an invalid address or an abnormal mailbox");

        sandbox.renderBatchEmailVerification(verificationPayload({
            items: [
                verificationRow({
                    id: 2, decision: "SKIP", providerState: "undeliverable", providerReason: "rejected_email",
                    sendStatus: "SKIPPED", sendReason: "EMAIL_VERIFICATION_REJECTED", tagStatus: "APPLIED"
                }),
                verificationRow({
                    id: 3, decision: "SKIP", providerState: "unknown", providerReason: null,
                    sendStatus: "SKIPPED", sendReason: "SOME_UNKNOWN_REASON", tagStatus: "NOT_REQUIRED"
                })
            ]
        }), "SUCCESS");
        const rows = elements.get("batchLogEmailVerificationRows").innerHTML;
        assert.ok(rows.includes('<span class="badge warn">未通过</span>'),
            "the historical rejection keeps its original wording");
        assert.ok(rows.includes("验证未通过，未发送（EMAIL_VERIFICATION_REJECTED）"));
        assert.ok(rows.includes("已标记邮箱异常"), "a real rejection still carries the tag");
        assert.ok(rows.includes("SOME_UNKNOWN_REASON"), "an unknown reason is shown verbatim instead of being guessed");
        assert.ok(!rows.includes("按策略跳过"),
            "the policy wording must not leak onto rows without the policy-skip reason");
    });

    it("S-3: the task row carries the allowed states inside the existing scope line", () => {
        const sandbox = {
            escapeHtml: (value) => String(value == null ? "" : value),
            regionLabel: (value) => value || "",
            cronToDisplayText: () => "",
            renderBatchConfigStatusToggle: () => "",
            batchGatePillHtml: () => '<span class="batch-gate-pill">门禁过滤 · 开</span>'
        };
        vm.createContext(sandbox);
        loadEmailPolicyHelpers(sandbox);
        vm.runInContext(extractFn("renderBatchConfigRow"), sandbox);

        const base = { id: 1, configName: "任务", mailType: "INTRODUCTION", cron: null, nextFireTime: null, lastExecutedAt: null };
        const selected = sandbox.renderBatchConfigRow(Object.assign({}, base, {
            emailVerificationEnabled: true, emailVerificationAllowedStates: ["deliverable", "unknown"]
        }));
        assert.ok(selected.includes('<span class="batch-task-scope-line"><span class="batch-gate-pill">邮箱验证 · 开</span>放行：可投递、未知</span>'),
            "the allowed states share the existing pill's scope line");

        const empty = sandbox.renderBatchConfigRow(Object.assign({}, base, {
            emailVerificationEnabled: true, emailVerificationAllowedStates: []
        }));
        assert.ok(empty.includes("放行：无"), "an empty list says 放行：无, not 不限");

        const legacy = sandbox.renderBatchConfigRow(Object.assign({}, base, {
            emailVerificationEnabled: true
        }));
        assert.ok(legacy.includes("放行：可投递、有风险、未知"),
            "a legacy row without the field keeps the three-state wording");

        const off = sandbox.renderBatchConfigRow(Object.assign({}, base, {
            emailVerificationEnabled: false, emailVerificationAllowedStates: ["deliverable"]
        }));
        assert.ok(off.includes('<span class="batch-gate-pill is-off">邮箱验证 · 关</span>'));
        assert.ok(!off.includes("放行："), "an off switch does not consume the list");

        const hostile = sandbox.renderBatchConfigRow(Object.assign({}, base, {
            emailVerificationEnabled: true, emailVerificationAllowedStates: ['<img onerror="boom">']
        }));
        assert.ok(!hostile.includes("<img"), "an unrecognised state can never become markup");
        assert.ok(hostile.includes("放行：无"));
    });
});
