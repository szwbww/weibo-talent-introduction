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

// Batch send functions under test (all top-level in app.js).
const BATCH_SEND_FNS = [
    "batchSendModeLabel",
    "batchSendStatusLabel",
    "batchSendStatusBadgeType",
    "batchSendButtonStates",
    "applyBatchSendBanner",
    "renderBatchSendAccountTable",
    "applyBatchSendControls",
    "refreshBatchSendControls",
    "readBatchSendConfigForm",
    "enableBatchSendSchedule",
    "handleBatchSendToggle",
    "handleBatchSendStart",
    "handleBatchSendPause"
];

// Build a per-selector element stub store so tests can assert on DOM state.
function createElementStore() {
    const store = new Map();
    function el(id) {
        if (!store.has(id)) {
            store.set(id, {
                id,
                textContent: "",
                className: "",
                innerHTML: "",
                hidden: null,
                disabled: false,
                value: "",
                checked: false,
                title: "",
                dataset: {},
                parentElement: null
            });
        }
        return store.get(id);
    }
    return {
        el,
        get: (id) => store.get(id)
    };
}

function createBatchSendSandbox() {
    const store = createElementStore();
    const sandbox = {
        // $ selector -> element stub (selector is like "#batchSendStartBtn")
        $: (sel) => store.el(sel.replace(/^#/, "")),
        $$: () => [],
        document: { body: { classList: { add: () => {}, remove: () => {} } } },
        escapeHtml: (v) => String(v == null ? "" : v)
            .replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;")
            .replaceAll('"', "&quot;").replaceAll("'", "&#39;"),
        showStatus: () => {},
        api: async () => ({}),
        setInterval: () => null,
        clearInterval: () => {}
    };
    vm.createContext(sandbox);
    for (const name of BATCH_SEND_FNS) {
        vm.runInContext(extractFn(name), sandbox);
    }
    sandbox.__store = store;
    return sandbox;
}

describe("Batch Send Controls (phase 04)", () => {
    function setConfigForm(sb, fields) {
        const map = {
            batchSendAutoEnabled: "checked",
            batchSendFrequency: "value",
            batchSendTime: "value",
            batchSendDailyCap: "value",
            batchSendRoundSize: "value",
            batchSendPerMailIntervalSec: "value",
            batchSendPerRoundIntervalSec: "value",
            batchSendSelfCheckTtlMin: "value"
        };
        for (const [id, prop] of Object.entries(map)) {
            const el = sb.$("#" + id);
            el[prop] = fields[id] ?? (prop === "value" ? "" : false);
        }
    }

    describe("L4-1 button state machine", () => {
        it("IDLE: only 开始 enabled; 暂停/手动 disabled", () => {
            const s = batchSendButtonStates_ref("IDLE");
            assert.deepStrictEqual(s, { start: false, pause: true, manual: true });
        });
        it("RUNNING: only 暂停 enabled; 开始/手动 disabled", () => {
            const s = batchSendButtonStates_ref("RUNNING");
            assert.deepStrictEqual(s, { start: true, pause: false, manual: true });
        });
        it("PAUSED: 开始(继续) and 手动 enabled; 暂停 disabled", () => {
            const s = batchSendButtonStates_ref("PAUSED");
            assert.deepStrictEqual(s, { start: false, pause: true, manual: false });
        });
        it("unknown status: all disabled (safe default)", () => {
            const s = batchSendButtonStates_ref("WEIRD");
            assert.deepStrictEqual(s, { start: true, pause: true, manual: true });
        });

        function batchSendButtonStates_ref(status) {
            const sb = createBatchSendSandbox();
            // Spread into a test-context object so deepStrictEqual doesn't trip on the
            // vm-context prototype of the object returned by the sandbox function.
            return { ...sb.batchSendButtonStates(status) };
        }
    });

    describe("I-2 mode badge labels", () => {
        it("AUTO -> 自动定时", () => {
            const sb = createBatchSendSandbox();
            assert.strictEqual(sb.batchSendModeLabel("AUTO"), "自动定时");
        });
        it("MANUAL -> 手动", () => {
            const sb = createBatchSendSandbox();
            assert.strictEqual(sb.batchSendModeLabel("MANUAL"), "手动");
        });
        it("NONE/other -> —", () => {
            const sb = createBatchSendSandbox();
            assert.strictEqual(sb.batchSendModeLabel("NONE"), "—");
            assert.strictEqual(sb.batchSendModeLabel(undefined), "—");
        });
    });

    describe("I-9 status badge labels", () => {
        it("RUNNING -> 运行中, PAUSED -> 已暂停, IDLE -> 空闲", () => {
            const sb = createBatchSendSandbox();
            assert.strictEqual(sb.batchSendStatusLabel("RUNNING"), "运行中");
            assert.strictEqual(sb.batchSendStatusLabel("PAUSED"), "已暂停");
            assert.strictEqual(sb.batchSendStatusLabel("IDLE"), "空闲");
        });
    });

    describe("L4-2 banner visibility (I-5)", () => {
        it("PAUSED + NO_AVAILABLE_ACCOUNT shows banner", () => {
            const sb = createBatchSendSandbox();
            sb.applyBatchSendBanner({ status: "PAUSED", pauseReason: "NO_AVAILABLE_ACCOUNT" });
            assert.strictEqual(sb.__store.get("batchSendPausedBanner").hidden, false);
        });
        it("PAUSED + other reason hides banner", () => {
            const sb = createBatchSendSandbox();
            sb.applyBatchSendBanner({ status: "PAUSED", pauseReason: "OPERATOR" });
            assert.strictEqual(sb.__store.get("batchSendPausedBanner").hidden, true);
        });
        it("RUNNING hides banner", () => {
            const sb = createBatchSendSandbox();
            sb.applyBatchSendBanner({ status: "RUNNING", pauseReason: "" });
            assert.strictEqual(sb.__store.get("batchSendPausedBanner").hidden, true);
        });
        it("IDLE hides banner (refresh after recovery clears it)", () => {
            const sb = createBatchSendSandbox();
            sb.applyBatchSendBanner({ status: "PAUSED", pauseReason: "NO_AVAILABLE_ACCOUNT" });
            assert.strictEqual(sb.__store.get("batchSendPausedBanner").hidden, false);
            sb.applyBatchSendBanner({ status: "IDLE", pauseReason: "" });
            assert.strictEqual(sb.__store.get("batchSendPausedBanner").hidden, true);
        });
        it("null statusView hides banner", () => {
            const sb = createBatchSendSandbox();
            sb.applyBatchSendBanner(null);
            assert.strictEqual(sb.__store.get("batchSendPausedBanner").hidden, true);
        });
    });

    describe("applyBatchSendControls end-to-end (L4-1 + I-2 + I-8 + L4-2)", () => {
        it("IDLE: 切换按钮 enabled labeled 开始执行(action=start); 暂停按钮隐藏; 手动隐藏/disabled; badges set; banner hidden", () => {
            const sb = createBatchSendSandbox();
            sb.applyBatchSendControls({
                status: "IDLE", mode: "NONE", pauseReason: "",
                accounts: []
            });
            const start = sb.__store.get("batchSendStartBtn");
            const pause = sb.__store.get("batchSendPauseBtn");
            const manual = sb.__store.get("batchSendManualBtn");
            assert.strictEqual(start.disabled, false);
            assert.strictEqual(start.textContent, "开始执行");
            assert.strictEqual(start.dataset.action, "start");
            assert.strictEqual(pause.hidden, true);
            // 手动执行按钮始终显示，仅在非 PAUSED 时禁用
            assert.strictEqual(manual.hidden, false);
            assert.strictEqual(manual.disabled, true);
            assert.strictEqual(sb.__store.get("batchSendModeBadge").textContent, "—");
            assert.strictEqual(sb.__store.get("batchSendStatusBadge").textContent, "空闲");
            assert.strictEqual(sb.__store.get("batchSendPausedBanner").hidden, true);
        });

        it("RUNNING + AUTO: 切换按钮 labeled 暂停(action=pause) enabled; mode badge 自动定时; status badge 运行中", () => {
            const sb = createBatchSendSandbox();
            sb.applyBatchSendControls({
                status: "RUNNING", mode: "AUTO", pauseReason: "",
                roundNumber: 2, dailyCap: 1000, dailySentTotal: 120,
                sentTotal: 120, failedTotal: 3,
                accounts: [
                    { accountCode: "acct1", todaySent: 60, dailyLimit: 100, success: 60, failed: 2, paused: false },
                    { accountCode: "acct2", todaySent: 0, dailyLimit: 100, success: 0, failed: 1, paused: true, pauseReason: "SELF_CHECK_FAILED:timeout" }
                ]
            });
            assert.strictEqual(sb.__store.get("batchSendStartBtn").disabled, false);
            assert.strictEqual(sb.__store.get("batchSendStartBtn").textContent, "暂停");
            assert.strictEqual(sb.__store.get("batchSendStartBtn").dataset.action, "pause");
            assert.strictEqual(sb.__store.get("batchSendPauseBtn").hidden, true);
            // 手动执行按钮始终显示，RUNNING 时禁用
            assert.strictEqual(sb.__store.get("batchSendManualBtn").hidden, false);
            assert.strictEqual(sb.__store.get("batchSendManualBtn").disabled, true);
            assert.strictEqual(sb.__store.get("batchSendModeBadge").textContent, "自动定时");
            assert.strictEqual(sb.__store.get("batchSendModeBadge").className, "badge primary");
            assert.strictEqual(sb.__store.get("batchSendStatusBadge").textContent, "运行中");
            // progress panel shown because RUNNING
            assert.strictEqual(sb.__store.get("batchSendProgressPanel").hidden, false);
            // summary row rendered with round/daily/sent/failed
            const summaryHtml = sb.__store.get("batchSendSummaryRow").innerHTML;
            assert.ok(summaryHtml.includes("轮次"));
            assert.ok(summaryHtml.includes("120/1000"));
            // account table renders both accounts; paused one shows 自动暂停 badge with reason tooltip
            const tableHtml = sb.__store.get("batchSendAccountTable").innerHTML;
            assert.ok(tableHtml.includes("acct1"));
            assert.ok(tableHtml.includes("acct2"));
            assert.ok(tableHtml.includes("自动暂停"));
            assert.ok(tableHtml.includes("SELF_CHECK_FAILED:timeout"));
        });

        it("PAUSED + NO_AVAILABLE_ACCOUNT: 切换(继续)+手动 enabled/visible, 暂停按钮隐藏; banner shown", () => {
            const sb = createBatchSendSandbox();
            sb.applyBatchSendControls({
                status: "PAUSED", mode: "MANUAL", pauseReason: "NO_AVAILABLE_ACCOUNT",
                accounts: []
            });
            const start = sb.__store.get("batchSendStartBtn");
            assert.strictEqual(start.disabled, false);
            assert.strictEqual(start.textContent, "继续/恢复");
            assert.strictEqual(start.dataset.action, "start");
            assert.strictEqual(sb.__store.get("batchSendManualBtn").hidden, false);
            assert.strictEqual(sb.__store.get("batchSendManualBtn").disabled, false);
            assert.strictEqual(sb.__store.get("batchSendPauseBtn").hidden, true);
            assert.strictEqual(sb.__store.get("batchSendModeBadge").textContent, "手动");
            assert.strictEqual(sb.__store.get("batchSendModeBadge").className, "badge warn");
            assert.strictEqual(sb.__store.get("batchSendStatusBadge").textContent, "已暂停");
            // banner visible (I-5/L4-2)
            assert.strictEqual(sb.__store.get("batchSendPausedBanner").hidden, false);
        });

        it("PAUSED with empty accounts still shows progress panel (status-driven)", () => {
            const sb = createBatchSendSandbox();
            sb.applyBatchSendControls({ status: "PAUSED", mode: "MANUAL", pauseReason: "OPERATOR", accounts: [] });
            assert.strictEqual(sb.__store.get("batchSendProgressPanel").hidden, false);
            // banner hidden because pauseReason != NO_AVAILABLE_ACCOUNT
            assert.strictEqual(sb.__store.get("batchSendPausedBanner").hidden, true);
        });

        it("manual button disabled in every non-PAUSED state (I-9 409 guard mirror)", () => {
            const sb = createBatchSendSandbox();
            for (const status of ["IDLE", "RUNNING", "WEIRD"]) {
                sb.applyBatchSendControls({ status, mode: "MANUAL", accounts: [] });
                assert.strictEqual(sb.__store.get("batchSendManualBtn").disabled, true, `manual should be disabled for ${status}`);
            }
            sb.applyBatchSendControls({ status: "PAUSED", mode: "MANUAL", accounts: [] });
            assert.strictEqual(sb.__store.get("batchSendManualBtn").disabled, false, "manual should be enabled for PAUSED");
        });

        it("IDLE + autoEnabled: schedule is active so toggle button shows pause", () => {
            const sb = createBatchSendSandbox();
            sb.applyBatchSendControls({ status: "IDLE", mode: "AUTO", autoEnabled: true, accounts: [] });

            const start = sb.__store.get("batchSendStartBtn");
            assert.strictEqual(start.disabled, false);
            assert.strictEqual(start.textContent, "暂停");
            assert.strictEqual(start.dataset.action, "pause");
            assert.strictEqual(sb.__store.get("batchSendStatusBadge").textContent, "定时中");
            assert.strictEqual(sb.__store.get("batchSendStatusBadge").className, "badge primary");
        });
    });

    describe("config form seconds <-> milliseconds conversion", () => {
        function setForm(sb, fields) {
            setConfigForm(sb, fields);
        }

        it("seconds are converted to ms on read (1s -> 1000ms, 60s -> 60000ms)", () => {
            const sb = createBatchSendSandbox();
            setForm(sb, {
                batchSendAutoEnabled: true,
                batchSendFrequency: "daily",
                batchSendTime: "00:00",
                batchSendDailyCap: "1000",
                batchSendRoundSize: "50",
                batchSendPerMailIntervalSec: "1",
                batchSendPerRoundIntervalSec: "60",
                batchSendSelfCheckTtlMin: "30"
            });
            const payload = sb.readBatchSendConfigForm();
            assert.strictEqual(payload.autoEnabled, true);
            assert.strictEqual(payload.cron, "0 0 0 * * ?");
            assert.strictEqual(payload.dailyCap, 1000);
            assert.strictEqual(payload.roundSize, 50);
            assert.strictEqual(payload.perMailIntervalMs, 1000);
            assert.strictEqual(payload.perRoundIntervalMs, 60000);
            assert.strictEqual(payload.selfCheckTtlMinutes, 30);
        });

        it("dailyCap < roundSize throws", () => {
            const sb = createBatchSendSandbox();
            setForm(sb, {
                batchSendAutoEnabled: true,
                batchSendFrequency: "daily",
                batchSendTime: "00:00",
                batchSendDailyCap: "10",
                batchSendRoundSize: "50",
                batchSendPerMailIntervalSec: "1",
                batchSendPerRoundIntervalSec: "60",
                batchSendSelfCheckTtlMin: "30"
            });
            assert.throws(() => sb.readBatchSendConfigForm(), /每批上限/);
        });

        it("blank time falls back to default 09:00 cron", () => {
            const sb = createBatchSendSandbox();
            setForm(sb, {
                batchSendAutoEnabled: true,
                batchSendFrequency: "daily",
                batchSendTime: "",
                batchSendDailyCap: "1000",
                batchSendRoundSize: "50",
                batchSendPerMailIntervalSec: "1",
                batchSendPerRoundIntervalSec: "60",
                batchSendSelfCheckTtlMin: "30"
            });
            assert.strictEqual(sb.readBatchSendConfigForm().cron, "0 0 9 * * ?");
        });

        it("non-numeric interval throws", () => {
            const sb = createBatchSendSandbox();
            setForm(sb, {
                batchSendAutoEnabled: true,
                batchSendFrequency: "daily",
                batchSendTime: "00:00",
                batchSendDailyCap: "1000",
                batchSendRoundSize: "50",
                batchSendPerMailIntervalSec: "abc",
                batchSendPerRoundIntervalSec: "60",
                batchSendSelfCheckTtlMin: "30"
            });
            assert.throws(() => sb.readBatchSendConfigForm(), /数字/);
        });
    });

    describe("schedule start behavior", () => {
        it("launch modal binds the start button to the toggle handler", () => {
            assert.ok(
                !appJsSource.includes("startBtn.onclick = handleBatchSendStart"),
                "batch send start button must use handleBatchSendToggle so pause clicks cannot start the timer"
            );
        });

        it("IDLE start enables schedule without launching manual outreach", async () => {
            const sb = createBatchSendSandbox();
            setConfigForm(sb, {
                batchSendAutoEnabled: true,
                batchSendFrequency: "daily",
                batchSendTime: "09:30",
                batchSendDailyCap: "1000",
                batchSendRoundSize: "50",
                batchSendPerMailIntervalSec: "1",
                batchSendPerRoundIntervalSec: "60",
                batchSendSelfCheckTtlMin: "30"
            });
            const calls = [];
            sb.api = async (url, options) => {
                calls.push({ url, method: options?.method, body: options?.body });
                return JSON.parse(options.body);
            };
            sb.fillBatchSendConfigForm = () => {};
            sb.refreshBatchSendControls = async () => {};
            let toast = null;
            sb.showModalToast = (message, type) => { toast = { message, type }; };
            let status = null;
            sb.showStatus = (message, type) => { status = { message, type }; };

            await sb.enableBatchSendSchedule("IDLE");

            assert.deepStrictEqual(calls.map(c => c.url), ["/api/mail/batch-send/config"]);
            assert.strictEqual(JSON.parse(calls[0].body).autoEnabled, true);
            assert.strictEqual(JSON.parse(calls[0].body).cron, "0 30 9 * * ?");
            assert.ok(!calls.some(c => c.url === "/api/mail/manual-outreach/start"));
            assert.ok(!calls.some(c => c.url === "/api/mail/batch-send/start-auto"));
            assert.strictEqual(toast.type, "ok");
            assert.strictEqual(status.type, "ok");
        });

        it("PAUSED resume enables schedule and clears pause without immediate execution", async () => {
            const sb = createBatchSendSandbox();
            setConfigForm(sb, {
                batchSendAutoEnabled: true,
                batchSendFrequency: "daily",
                batchSendTime: "09:30",
                batchSendDailyCap: "1000",
                batchSendRoundSize: "50",
                batchSendPerMailIntervalSec: "1",
                batchSendPerRoundIntervalSec: "60",
                batchSendSelfCheckTtlMin: "30"
            });
            const calls = [];
            sb.api = async (url, options) => {
                calls.push({ url, method: options?.method, body: options?.body });
                return options?.body ? JSON.parse(options.body) : {};
            };
            sb.fillBatchSendConfigForm = () => {};
            sb.refreshBatchSendControls = async () => {};
            sb.showModalToast = () => {};
            sb.showStatus = () => {};

            await sb.enableBatchSendSchedule("PAUSED");

            assert.deepStrictEqual(calls.map(c => c.url), [
                "/api/mail/batch-send/config",
                "/api/mail/batch-send/resume-schedule"
            ]);
            assert.ok(!calls.some(c => c.url === "/api/mail/manual-outreach/start"));
            assert.ok(!calls.some(c => c.url === "/api/mail/batch-send/start-auto"));
        });

        it("pause while timer is active disables schedule without pausing an execution", async () => {
            const sb = createBatchSendSandbox();
            const calls = [];
            sb.api = async (url, options) => {
                calls.push({ url, method: options?.method });
                return url === "/api/mail/batch-send/status"
                    ? { status: "IDLE", mode: "AUTO", autoEnabled: true, accounts: [] }
                    : {};
            };
            let modalToast = null;
            sb.showModalToast = (message, type) => { modalToast = { message, type }; };
            sb.showStatus = () => {};

            await sb.handleBatchSendPause();

            assert.deepStrictEqual(calls.map(c => c.url), [
                "/api/mail/batch-send/status",
                "/api/mail/batch-send/pause-schedule",
                "/api/mail/batch-send/status"
            ]);
            assert.ok(!calls.some(c => c.url === "/api/mail/batch-send/pause"));
            assert.deepStrictEqual(modalToast, { message: "已暂停定时发送", type: "ok" });
        });

        it("toggle rechecks status so stale start action does not restart an active timer", async () => {
            const sb = createBatchSendSandbox();
            setConfigForm(sb, {
                batchSendAutoEnabled: true,
                batchSendFrequency: "daily",
                batchSendTime: "09:30",
                batchSendDailyCap: "1000",
                batchSendRoundSize: "50",
                batchSendPerMailIntervalSec: "1",
                batchSendPerRoundIntervalSec: "60",
                batchSendSelfCheckTtlMin: "30"
            });
            const start = sb.$("#batchSendStartBtn");
            start.textContent = "暂停";
            start.dataset.action = "start";
            const calls = [];
            sb.api = async (url, options) => {
                calls.push({ url, method: options?.method, body: options?.body });
                return url === "/api/mail/batch-send/status"
                    ? { status: "IDLE", mode: "AUTO", autoEnabled: true, accounts: [] }
                    : {};
            };
            sb.confirm = () => true;
            sb.showStatus = () => {};
            sb.showModalToast = () => {};
            sb.fillBatchSendConfigForm = () => {};

            await sb.handleBatchSendToggle();

            assert.ok(calls.some(c => c.url === "/api/mail/batch-send/pause-schedule"));
            assert.ok(!calls.some(c => c.url === "/api/mail/batch-send/config"));
        });
    });
});
