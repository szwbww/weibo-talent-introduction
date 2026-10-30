const fs = require("fs");
const path = require("path");
const vm = require("vm");
const assert = require("assert");
const { describe, it } = require("node:test");

const appJsPath = path.join(__dirname, "..", "..", "main", "resources", "static", "app.js");
const appJsSource = fs.readFileSync(appJsPath, "utf-8");
const runtimePath = path.join(__dirname, "..", "..", "main", "resources", "static", "task-modal-runtime.js");
const runtimeSource = fs.readFileSync(runtimePath, "utf-8");

function extractFunctionBody(source, functionName) {
    const headerRegex = new RegExp(`(?:async\\s+)?function\\s+${functionName}\\s*\\([^)]*\\)\\s*\\{`, 'g');
    const match = headerRegex.exec(source);
    if (!match) {
        throw new Error(`Could not find function declaration for ${functionName}`);
    }

    const startIndex = match.index + match[0].length;
    let braceCount = 1;
    let index = startIndex;

    while (braceCount > 0 && index < source.length) {
        const char = source[index];
        if (char === '{') {
            braceCount++;
        } else if (char === '}') {
            braceCount--;
        }
        index++;
    }

    if (braceCount > 0) {
        throw new Error(`Unmatched braces in function ${functionName}`);
    }

    return source.substring(startIndex, index - 1);
}

function stripEventListeners(source) {
    let result = source;
    let index = 0;
    while (true) {
        index = result.indexOf("addEventListener", index);
        if (index === -1) break;

        const nextAddEventListener = result.indexOf("addEventListener", index + 16);
        const openBraceIndex = result.indexOf("{", index);

        if (openBraceIndex !== -1 && (nextAddEventListener === -1 || openBraceIndex < nextAddEventListener)) {
            const prefix = result.substring(index, openBraceIndex);
            if (prefix.includes("=>") || prefix.includes("function")) {
                let braceCount = 1;
                let scanIndex = openBraceIndex + 1;
                while (braceCount > 0 && scanIndex < result.length) {
                    const char = result[scanIndex];
                    if (char === '{') {
                        braceCount++;
                    } else if (char === '}') {
                        braceCount--;
                    }
                    scanIndex++;
                }

                if (braceCount === 0) {
                    const closeParenIndex = result.indexOf(")", scanIndex - 1);
                    if (closeParenIndex !== -1) {
                        const before = result.substring(0, openBraceIndex);
                        const after = result.substring(closeParenIndex);
                        result = before + "() => {}" + after;
                        index = before.length + 8;
                        continue;
                    }
                }
            }
        }
        index += 16;
    }
    return result;
}

function extractPreAuthInitFunctions(source) {
    const bootstrapBody = extractFunctionBody(source, "bootstrap");
    const calls = [];
    const callRegex = /\b(\w+)\s*\(/g;
    let match;
    while ((match = callRegex.exec(bootstrapBody)) !== null) {
        const name = match[1];
        if (name === "checkAuth") {
            break;
        }
        calls.push(name);
    }

    const result = {};
    for (const name of calls) {
        result[name] = extractFunctionBody(source, name);
    }
    return result;
}

function extractFn(name) {
    const regex = new RegExp("(?:async\\s+)?function\\s+" + name + "\\s*\\([^)]*\\)\\s*\\{[\\s\\S]*?\\n\\}");
    const match = appJsSource.match(regex);
    if (!match) throw new Error("Could not find " + name + " in app.js");
    return match[0];
}

function createSandbox() {
    const sandbox = {
        contextPath: "",
        appStarted: false,
        taskWatchers: {},
        currentTaskModal: null,
        state: {
            monitoring: {
                autoRefreshTimer: null,
                date: null,
                summary: null,
                senderHealth: []
            },
            view: "monitoring"
        },
        document: {
            body: { classList: { remove: () => {} } },
            getElementById: (id) => {
                if (!sandbox.elements[id]) {
                    sandbox.elements[id] = { hidden: true, textContent: "", value: "" };
                }
                return sandbox.elements[id];
            },
            querySelector: (sel) => {
                if (sel === ".app-shell") {
                    return sandbox.elements.appShell;
                }
                return { hidden: true, style: { display: "" } };
            }
        },
        window: {},
        elements: {
            appShell: { style: { display: "grid" } },
            loginOverlay: { hidden: true },
            changePasswordOverlay: { hidden: true },
            currentUserDisplay: { textContent: "" },
            loginError: { hidden: true, textContent: "" },
            changePasswordError: { hidden: true, textContent: "" },
            taskProgressModal: { hidden: true }
        },
        $: (selector) => {
            if (selector.startsWith("#")) {
                const id = selector.substring(1);
                if (!sandbox.elements[id]) {
                    sandbox.elements[id] = { hidden: true, textContent: "", value: "" };
                }
                return sandbox.elements[id];
            }
            if (selector === ".app-shell") {
                return sandbox.elements.appShell;
            }
            return { hidden: true, style: { display: "" } };
        },
        $$: () => [],
        apiCalls: [],
        api: async (path, options) => {
            sandbox.apiCalls.push({ path, options });
            if (path === "/api/auth/me") {
                return sandbox.authMeResponse;
            }
            return {};
        },
        authMeResponse: { authenticated: false },
        showStatusCalls: [],
        showStatus: (msg, type) => {
            sandbox.showStatusCalls.push({ msg, type });
        },
        stopTaskModalPolling: () => {},
        stopTaskWatcher: (taskType, restore) => {
            delete sandbox.taskWatchers[taskType];
        },
        stopTaskActivityPollingCalls: 0,
        stopTaskActivityPolling: () => {
            sandbox.stopTaskActivityPollingCalls++;
        },
        refreshCurrentViewCalled: 0,
        refreshCurrentView: () => {
            sandbox.refreshCurrentViewCalled++;
        },
        updateUnmatchedBadgeCalled: 0,
        updateUnmatchedBadge: (counts) => {
            sandbox.updateUnmatchedBadgeCalled++;
        },
        resumeProgressPollingIfNeeded: async () => {},
        initBatchSendBanner: () => {},
        stopBatchSendBannerPoll: () => {},
        stopBatchSendStatusPoll: () => {},
        location: {
            reloadCalled: 0,
            reload: () => {
                sandbox.location.reloadCalled++;
            }
        },
        clearTimeoutCalls: [],
        clearTimeout: (timer) => {
            sandbox.clearTimeoutCalls.push(timer);
        },
        setTimeoutCalls: [],
        setTimeout: (fn, delay) => {
            sandbox.setTimeoutCalls.push({ fn, delay });
            return 12345;
        },
        renderMonitoringCardsCalled: 0,
        renderMonitoringCards: () => {
            sandbox.renderMonitoringCardsCalled++;
        },
        renderMonitoringSenderHealthCalled: 0,
        renderMonitoringSenderHealth: () => {
            sandbox.renderMonitoringSenderHealthCalled++;
        },
        renderMonitoringLastRefreshedCalled: 0,
        renderMonitoringLastRefreshed: () => {
            sandbox.renderMonitoringLastRefreshedCalled++;
        },
        URLSearchParams: class {
            set() {}
            toString() { return ""; }
        }
    };
    vm.createContext(sandbox);
    vm.runInContext(runtimeSource, sandbox);
    vm.runInContext("var appStarted = false;", sandbox);
    vm.runInContext(extractFn("startAuthenticatedApp"), sandbox);
    vm.runInContext(extractFn("stopAuthenticatedApp"), sandbox);
    vm.runInContext(extractFn("stopAllWatchers"), sandbox);
    vm.runInContext(extractFn("checkAuth"), sandbox);
    vm.runInContext(extractFn("scheduleMonitoringAutoRefresh"), sandbox);
    vm.runInContext(extractFn("monitoringToday"), sandbox);
    vm.runInContext(extractFn("shiftMonitoringDate"), sandbox);
    vm.runInContext(extractFn("monitoringWindowParams"), sandbox);
    injectMailReadOnlyHelpers(sandbox);
    return sandbox;
}

// 03（I-1/I-2）：reader 身份分支依赖的 app.js 函数与常量。
const MAIL_READ_ONLY_FN_NAMES = [
    "mailReadOnlyState",
    "isMailReadOnlyMode",
    "isMailReadOnlyRequestAllowed",
    "showMailReadOnlyIdentityError",
    "applyMailReadOnlyShell",
    "renderMailReadOnlyLoadError",
    "startMailReadOnlyApp",
    "releaseMailReadOnly"
];

function injectMailReadOnlyHelpers(sandbox) {
    vm.runInContext(
        "var MAIL_READ_ONLY_USERNAME = 'user1';"
        + " var MAIL_READ_ONLY_COPY = {"
        + " identityError: '权限信息异常，请重新登录',"
        + " moduleError: '只读邮件视图加载失败，请刷新' };",
        sandbox
    );
    MAIL_READ_ONLY_FN_NAMES.forEach((name) => {
        vm.runInContext(extractFn(name), sandbox);
    });
}

// Create a sandbox with all bootstrap-relevant functions extracted from app.js.
// Sub-functions of bootstrap() that are NOT auth-relevant (bindEvents, initManualOutreach, etc.)
// are replaced with stubs to avoid needing the full DOM.
function createBootstrapSandbox(authMeResponse) {
    let badgeCalled = 0;
    let resumePollingCalled = 0;
    let refreshViewCalled = 0;
    let stopActivityPollingCalled = 0;

    const elementStore = {
        appShell: { style: { display: "grid" } },
        loginOverlay: { hidden: true },
        changePasswordOverlay: { hidden: true },
        currentUserDisplay: { textContent: "" },
        loginError: { hidden: true, textContent: "" },
        taskProgressModal: { hidden: true }
    };

    const sandbox = {
        contextPath: "",
        appStarted: false,
        taskWatchers: {},
        currentTaskModal: null,
        state: {
            monitoring: { autoRefreshTimer: null, date: null, summary: null, senderHealth: [] },
            view: "monitoring"
        },
        document: {
            body: { classList: { remove: () => {} } },
            getElementById: (id) => {
                if (!elementStore[id]) elementStore[id] = { hidden: true, textContent: "", value: "" };
                return elementStore[id];
            },
            querySelector: (sel) => {
                if (sel === ".app-shell") return elementStore.appShell;
                return { hidden: true, style: { display: "" } };
            }
        },
        window: {},
        $: (selector) => {
            if (selector.startsWith("#")) {
                const id = selector.substring(1);
                if (!elementStore[id]) elementStore[id] = { hidden: true, textContent: "", value: "" };
                return elementStore[id];
            }
            if (selector === ".app-shell") return elementStore.appShell;
            return { hidden: true, style: { display: "" } };
        },
        $$: () => [],
        apiCalls: [],
        api: async (path, options) => {
            sandbox.apiCalls.push({ path, options });
            if (path === "/api/auth/me") return authMeResponse;
            return {};
        },
        showStatus: () => {},
        stopTaskModalPolling: () => {},
        stopTaskWatcher: () => {},
        // 观察器停止依赖：真实 stopAuthenticatedApp 会调用它；缺这个 stub 会让
        // 鉴权清理路径直接 ReferenceError，而不是被 typeof 防御掩盖。
        stopTaskActivityPolling: () => { stopActivityPollingCalled++; },
        clearTimeout: () => {},
        setTimeout: (fn, delay) => 12345,

        // Tracking stubs for functions called within startAuthenticatedApp
        updateUnmatchedBadge: () => { badgeCalled++; },
        resumeProgressPollingIfNeeded: async () => { resumePollingCalled++; },
        refreshCurrentView: () => { refreshViewCalled++; },
        initBatchSendBanner: () => {},
        stopBatchSendBannerPoll: () => {},
        stopBatchSendStatusPoll: () => {},

        // Stubs for bootstrap sub-functions (not auth-relevant, just need to not crash)
        bindMobileCoreNavigation: () => {},
        bindEvents: () => {},
        initManualOutreach: () => {},
        initBulkAutoReply: () => {},
        initPollLogPanel: () => {},
        initLayoutResizer: () => {},
        bindAuthEvents: () => {},

        // Counters for test assertions
        _badgeCalled: () => badgeCalled,
        _refreshViewCalled: () => refreshViewCalled,
        _resumePollingCalled: () => resumePollingCalled,
        _stopTaskActivityPollingCalled: () => stopActivityPollingCalled,
    };

    vm.createContext(sandbox);
    vm.runInContext(runtimeSource, sandbox);
    vm.runInContext("var appStarted = false;", sandbox);
    // Load the real auth-relevant functions
    vm.runInContext(extractFn("startAuthenticatedApp"), sandbox);
    vm.runInContext(extractFn("stopAuthenticatedApp"), sandbox);
    vm.runInContext(extractFn("stopAllWatchers"), sandbox);
    vm.runInContext(extractFn("checkAuth"), sandbox);
    // Load the real bootstrap function (it calls the stubs above)
    vm.runInContext(extractFn("bootstrap"), sandbox);
    injectMailReadOnlyHelpers(sandbox);

    return sandbox;
}

describe("auth state machine tests", () => {
    it("checkAuth unauthenticated -> hides shell, shows login, clears watchers", async () => {
        const sandbox = createSandbox();
        sandbox.authMeResponse = { authenticated: false };
        sandbox.appStarted = true;
        sandbox.taskWatchers["TEST_TASK"] = { intervalId: 1 };

        await sandbox.checkAuth();

        assert.strictEqual(sandbox.appStarted, false);
        assert.strictEqual(sandbox.elements.appShell.style.display, "none");
        assert.strictEqual(sandbox.elements.loginOverlay.hidden, false);
        assert.strictEqual(sandbox.elements.changePasswordOverlay.hidden, true);
        assert.strictEqual(Object.keys(sandbox.taskWatchers).length, 0);
        assert.strictEqual(sandbox.refreshCurrentViewCalled, 0);
    });

    it("checkAuth authenticated but needs change password -> hides shell, shows change password overlay", async () => {
        const sandbox = createSandbox();
        sandbox.authMeResponse = { authenticated: true, username: "admin", mustChangePassword: true };

        await sandbox.checkAuth();

        assert.strictEqual(sandbox.appStarted, false);
        assert.strictEqual(sandbox.elements.appShell.style.display, "none");
        assert.strictEqual(sandbox.elements.loginOverlay.hidden, true);
        assert.strictEqual(sandbox.elements.changePasswordOverlay.hidden, false);
        assert.strictEqual(sandbox.refreshCurrentViewCalled, 0);
    });

    it("checkAuth authenticated and password changed -> shows shell, sets username, starts app", async () => {
        const sandbox = createSandbox();
        sandbox.authMeResponse = { authenticated: true, username: "admin", mustChangePassword: false };

        await sandbox.checkAuth();

        assert.strictEqual(sandbox.appStarted, true);
        assert.strictEqual(sandbox.elements.appShell.style.display, "grid");
        assert.strictEqual(sandbox.elements.loginOverlay.hidden, true);
        assert.strictEqual(sandbox.elements.changePasswordOverlay.hidden, true);
        assert.strictEqual(sandbox.elements.currentUserDisplay.textContent, "admin");
        assert.strictEqual(sandbox.refreshCurrentViewCalled, 1);
    });

    it("unauthenticated bootstrap: only /api/auth/me is called, no business data loading", async () => {
        const sandbox = createBootstrapSandbox({ authenticated: false });

        vm.runInContext("bootstrap()", sandbox);
        // checkAuth is async – wait for microtask queue to drain
        await new Promise(r => setTimeout(r, 50));

        assert.ok(sandbox.apiCalls.length >= 1, "At least one API call expected");
        assert.strictEqual(sandbox.apiCalls[0].path, "/api/auth/me",
            "First API call must be /api/auth/me");

        const businessCalls = sandbox.apiCalls.filter(c => !c.path.startsWith("/api/auth/"));
        assert.strictEqual(businessCalls.length, 0,
            "Unauthenticated: no business API calls. Found: " +
            businessCalls.map(c => c.path).join(", "));

        assert.strictEqual(sandbox._badgeCalled(), 0, "badge should not load");
        assert.strictEqual(sandbox._refreshViewCalled(), 0, "view should not load");
        assert.strictEqual(sandbox._resumePollingCalled(), 0, "polling should not start");
    });

    it("mustChangePassword=true bootstrap: no business data loading", async () => {
        const sandbox = createBootstrapSandbox({
            authenticated: true, username: "admin", mustChangePassword: true
        });

        vm.runInContext("bootstrap()", sandbox);
        await new Promise(r => setTimeout(r, 50));

        assert.strictEqual(sandbox.apiCalls[0].path, "/api/auth/me");
        assert.strictEqual(sandbox._badgeCalled(), 0, "badge should not load");
        assert.strictEqual(sandbox._refreshViewCalled(), 0, "view should not load");
        assert.strictEqual(sandbox._resumePollingCalled(), 0, "polling should not start");
    });

    it("fully authenticated bootstrap: loads badge, polling, and default view once", async () => {
        const sandbox = createBootstrapSandbox({
            authenticated: true, username: "admin", mustChangePassword: false
        });

        vm.runInContext("bootstrap()", sandbox);
        await new Promise(r => setTimeout(r, 50));

        assert.strictEqual(sandbox.apiCalls[0].path, "/api/auth/me");
        assert.strictEqual(sandbox._badgeCalled(), 1, "badge should load once");
        assert.strictEqual(sandbox._refreshViewCalled(), 1, "view should load once");
        assert.strictEqual(sandbox._resumePollingCalled(), 1, "polling should start once");
    });

    it("repeated startAuthenticatedApp after bootstrap does not re-trigger badge or view", async () => {
        const sandbox = createBootstrapSandbox({
            authenticated: true, username: "admin", mustChangePassword: false
        });

        vm.runInContext("bootstrap()", sandbox);
        await new Promise(r => setTimeout(r, 50));

        // Call startAuthenticatedApp again
        vm.runInContext('startAuthenticatedApp("admin")', sandbox);

        assert.strictEqual(sandbox._badgeCalled(), 1, "badge should not reload");
        assert.strictEqual(sandbox._refreshViewCalled(), 1, "view should not reload");
    });
});

describe("task activity observer teardown", () => {
    it("checkAuth unauthenticated clears the observer", async () => {
        const sandbox = createSandbox();
        sandbox.authMeResponse = { authenticated: false };
        sandbox.appStarted = true;

        await sandbox.checkAuth();

        assert.strictEqual(sandbox.stopTaskActivityPollingCalls, 1,
            "未登录必须清理观察器");
    });

    it("checkAuth mustChangePassword clears the observer", async () => {
        const sandbox = createSandbox();
        sandbox.authMeResponse = { authenticated: true, username: "admin", mustChangePassword: true };

        await sandbox.checkAuth();

        assert.strictEqual(sandbox.stopTaskActivityPollingCalls, 1,
            "强制改密必须清理观察器");
    });

    it("handleAuthResponse 401 clears the observer", async () => {
        const sandbox = createSandbox();
        sandbox.appStarted = true;
        sandbox.window.stopAuthenticatedApp = sandbox.stopAuthenticatedApp;
        const response = { status: 401, clone: () => response };

        try {
            await sandbox.handleAuthResponse(response);
            assert.fail("Should throw on 401");
        } catch (e) {
            assert.strictEqual(e.message, "UNAUTHORIZED");
        }

        assert.strictEqual(sandbox.stopTaskActivityPollingCalls, 1,
            "401 后不得留下活跃观察器");
    });

    it("handleAuthResponse 403 PASSWORD_CHANGE_REQUIRED clears the observer", async () => {
        const sandbox = createSandbox();
        sandbox.appStarted = true;
        sandbox.window.stopAuthenticatedApp = sandbox.stopAuthenticatedApp;
        const response = {
            status: 403,
            clone: () => response,
            json: async () => ({ code: "PASSWORD_CHANGE_REQUIRED", message: "first change" })
        };

        try {
            await sandbox.handleAuthResponse(response);
            assert.fail("Should throw on 403");
        } catch (e) {
            assert.strictEqual(e.message, "PASSWORD_CHANGE_REQUIRED");
        }

        assert.strictEqual(sandbox.stopTaskActivityPollingCalls, 1,
            "强制改密后不得留下活跃观察器");
    });

    it("explicit stopAuthenticatedApp clears the observer", () => {
        const sandbox = createSandbox();

        sandbox.stopAuthenticatedApp();

        assert.strictEqual(sandbox.stopTaskActivityPollingCalls, 1);
    });

    it("unauthenticated bootstrap never starts the observer", async () => {
        const sandbox = createBootstrapSandbox({ authenticated: false });

        vm.runInContext("bootstrap()", sandbox);
        await new Promise(r => setTimeout(r, 50));

        assert.strictEqual(sandbox._resumePollingCalled(), 0, "未登录不得启动观察器");
        assert.strictEqual(sandbox._stopTaskActivityPollingCalled(), 1,
            "未登录路径必须清理观察器");
    });

    it("authenticated bootstrap starts the observer once and repeated start keeps it single", async () => {
        const sandbox = createBootstrapSandbox({
            authenticated: true, username: "admin", mustChangePassword: false
        });

        vm.runInContext("bootstrap()", sandbox);
        await new Promise(r => setTimeout(r, 50));

        assert.strictEqual(sandbox._resumePollingCalled(), 1, "登录后只启动一次观察器");
        assert.strictEqual(sandbox._stopTaskActivityPollingCalled(), 0, "已登录不得清理观察器");

        vm.runInContext('startAuthenticatedApp("admin")', sandbox);

        assert.strictEqual(sandbox._resumePollingCalled(), 1, "重复 start 不得重启观察器链");
        assert.strictEqual(sandbox._stopTaskActivityPollingCalled(), 0);
    });
});

describe("pre-auth init safety scan", () => {
    const NETWORK_CALL_PATTERNS = [
        /\bfetch\s*\(/,           // 裸 fetch
        /\bapi\s*\(/,             // 项目封装的 api()
        /\bXMLHttpRequest\b/,     // XHR
        /\bnew\s+Request\s*\(/,   // Request 构造
        /\b\$\.ajax\b/,           // jQuery（虽然项目不用，防御性检查）
    ];

    const preAuthFns = extractPreAuthInitFunctions(appJsSource);
    for (const [name, body] of Object.entries(preAuthFns)) {
        it(`${name} contains no network calls`, () => {
            const stripped = stripEventListeners(body);
            for (const pattern of NETWORK_CALL_PATTERNS) {
                assert.ok(!pattern.test(stripped),
                    `${name} contains network call matching ${pattern}`);
            }
        });
    }
});

describe("auth function unit tests", () => {
    it("handleAuthResponse intercepts 401 and stops app", async () => {
        const sandbox = createSandbox();
        sandbox.appStarted = true;
        sandbox.window.stopAuthenticatedApp = sandbox.stopAuthenticatedApp;

        const response = {
            status: 401,
            clone: () => response
        };

        try {
            await sandbox.handleAuthResponse(response);
            assert.fail("Should throw on 401");
        } catch (e) {
            assert.strictEqual(e.message, "UNAUTHORIZED");
        }

        assert.strictEqual(sandbox.appStarted, false);
        assert.strictEqual(sandbox.elements.loginOverlay.hidden, false);
        assert.strictEqual(sandbox.elements.changePasswordOverlay.hidden, true);
        assert.strictEqual(sandbox.elements.appShell.style.display, "none");
    });

    it("handleAuthResponse intercepts 403 PASSWORD_CHANGE_REQUIRED and stops app", async () => {
        const sandbox = createSandbox();
        sandbox.appStarted = true;
        sandbox.window.stopAuthenticatedApp = sandbox.stopAuthenticatedApp;

        const response = {
            status: 403,
            clone: () => response,
            json: async () => ({ code: "PASSWORD_CHANGE_REQUIRED", message: "first change" })
        };

        try {
            await sandbox.handleAuthResponse(response);
            assert.fail("Should throw on 403");
        } catch (e) {
            assert.strictEqual(e.message, "PASSWORD_CHANGE_REQUIRED");
        }

        assert.strictEqual(sandbox.appStarted, false);
        assert.strictEqual(sandbox.elements.loginOverlay.hidden, true);
        assert.strictEqual(sandbox.elements.changePasswordOverlay.hidden, false);
        assert.strictEqual(sandbox.elements.appShell.style.display, "none");
    });

    it("stopAuthenticatedApp clears monitoring autoRefreshTimer", () => {
        const sandbox = createSandbox();
        sandbox.state.monitoring.autoRefreshTimer = 999;

        sandbox.stopAuthenticatedApp();

        assert.strictEqual(sandbox.state.monitoring.autoRefreshTimer, null);
        assert.deepStrictEqual(sandbox.clearTimeoutCalls, [999]);
    });

    it("401 after timer stops rescheduling", async () => {
        const sandbox = createSandbox();
        sandbox.appStarted = true;
        sandbox.state.view = "monitoring";
        sandbox.window.stopAuthenticatedApp = sandbox.stopAuthenticatedApp;

        // Start the refresh loop
        sandbox.scheduleMonitoringAutoRefresh();
        assert.strictEqual(sandbox.setTimeoutCalls.length, 1);

        // Capture the setTimeout callback
        const callback = sandbox.setTimeoutCalls[0].fn;

        // Make api throw 401 and handle auth response (which calls stopAuthenticatedApp)
        sandbox.api = async () => {
            const resp = { status: 401, clone: () => resp };
            await sandbox.handleAuthResponse(resp);
        };

        try {
            await callback();
        } catch (e) {
            // Interceptor throws
        }

        // Verify stopAuthenticatedApp has run, state updated, and no new timer rescheduled
        assert.strictEqual(sandbox.appStarted, false);
        assert.strictEqual(sandbox.setTimeoutCalls.length, 1); // Still 1, no second call in finally
    });

    it("re-login only creates one timer", () => {
        const sandbox = createSandbox();
        sandbox.state.monitoring.autoRefreshTimer = 123;
        sandbox.state.view = "monitoring";

        // Calling scheduleMonitoringAutoRefresh clears the old timer first
        sandbox.scheduleMonitoringAutoRefresh();

        assert.deepStrictEqual(sandbox.clearTimeoutCalls, [123]);
        assert.strictEqual(sandbox.setTimeoutCalls.length, 1);
        assert.strictEqual(sandbox.state.monitoring.autoRefreshTimer, 12345);
    });

    it("repeated startAuthenticatedApp does not recreate initialization chain", () => {
        const sandbox = createSandbox();
        sandbox.appStarted = false;

        sandbox.startAuthenticatedApp("admin");
        assert.strictEqual(sandbox.appStarted, true);
        assert.strictEqual(sandbox.refreshCurrentViewCalled, 1);

        sandbox.startAuthenticatedApp("admin");
        assert.strictEqual(sandbox.appStarted, true);
        assert.strictEqual(sandbox.refreshCurrentViewCalled, 1); // Still 1, not incremented
    });

    it("startAuthenticatedApp calls updateUnmatchedBadge on first start", () => {
        const sandbox = createSandbox();
        sandbox.appStarted = false;
        let badgeCalled = 0;
        sandbox.updateUnmatchedBadge = () => { badgeCalled++; };
        vm.runInContext(extractFn("startAuthenticatedApp"), sandbox);

        sandbox.startAuthenticatedApp("admin");
        assert.strictEqual(badgeCalled, 1);
        assert.strictEqual(sandbox.refreshCurrentViewCalled, 1);

        // Second call should not trigger badge again
        sandbox.startAuthenticatedApp("admin");
        assert.strictEqual(badgeCalled, 1);
        assert.strictEqual(sandbox.refreshCurrentViewCalled, 1);
    });
});

// ---------------------------------------------------------------------------
// fast-p 2026-10-27 · user1 只读邮件视图（03 · I-1/I-2/I-3）
// 身份来自 login/me 元数据；user1 缺元数据不得进入 admin 分支；reader 生命周期
// 早退在旧账号/群推/自动回复/草稿路径之前；app.api 在已知 reader 时本地拒绝越界请求。
// ---------------------------------------------------------------------------

function createReaderSandbox(meResponse) {
    const sandbox = createSandbox();
    sandbox.authMeResponse = meResponse;
    sandbox.ensureVariableMeta = undefined;
    return sandbox;
}

function stubReadOnlyModule(sandbox) {
    const mounts = [];
    sandbox.window.MailboxReadOnly = {
        mount(host, options) {
            mounts.push({ host, options });
            return { refresh: () => Promise.resolve(), unmount() {} };
        },
        unmount() {}
    };
    sandbox.readOnlyMounts = mounts;
    return mounts;
}

describe("reader identity branch (03 I-1/I-2)", () => {
    it("me: user1 + mailReadOnly mounts only the readonly module and locks the view", async () => {
        const sandbox = createReaderSandbox({
            authenticated: true, username: "user1", mustChangePassword: false, mailReadOnly: true
        });
        const mounts = stubReadOnlyModule(sandbox);

        await sandbox.checkAuth();

        assert.strictEqual(sandbox.appStarted, true);
        assert.strictEqual(sandbox.state.view, "mailbox");
        assert.strictEqual(sandbox.state.mailReadOnly.active, true);
        assert.strictEqual(sandbox.state.mailReadOnly.username, "user1");
        assert.strictEqual(mounts.length, 1);
        assert.strictEqual(mounts[0].options.sessionUser.username, "user1");
        assert.strictEqual(typeof mounts[0].options.api, "function");
        assert.strictEqual(sandbox.refreshCurrentViewCalled, 0, "admin refresh chain must not run");
        assert.strictEqual(sandbox.updateUnmatchedBadgeCalled, 0);
        assert.strictEqual(sandbox.elements.currentUserDisplay.textContent, "user1");
        assert.strictEqual(sandbox.elements.loginOverlay.hidden, true);
        assert.deepStrictEqual(sandbox.apiCalls.map((call) => call.path), ["/api/auth/me"]);
    });

    it("me: user1 without mailReadOnly shows the fixed error and never starts admin", async () => {
        const sandbox = createReaderSandbox({
            authenticated: true, username: "user1", mustChangePassword: false
        });
        const mounts = stubReadOnlyModule(sandbox);

        await sandbox.checkAuth();

        assert.strictEqual(mounts.length, 0);
        assert.strictEqual(sandbox.appStarted, false);
        assert.strictEqual(sandbox.elements.loginOverlay.hidden, false);
        assert.strictEqual(sandbox.elements.loginError.textContent, "权限信息异常，请重新登录");
        assert.strictEqual(sandbox.elements.loginError.hidden, false);
        assert.strictEqual(sandbox.refreshCurrentViewCalled, 0);
        assert.notStrictEqual(sandbox.state.view, "mailbox");
    });

    it("me: admin keeps the original admin startup even when metadata is inconsistent", async () => {
        const sandbox = createReaderSandbox({
            authenticated: true, username: "admin", mustChangePassword: false, mailReadOnly: true
        });
        stubReadOnlyModule(sandbox);

        await sandbox.checkAuth();

        assert.strictEqual(sandbox.state.view, "monitoring");
        assert.strictEqual(sandbox.refreshCurrentViewCalled, 1);
        assert.strictEqual(sandbox.updateUnmatchedBadgeCalled, 1);
        assert.strictEqual(sandbox.state.mailReadOnly, undefined);
    });

    it("missing readonly module shows the fixed load error without any fallback", async () => {
        const sandbox = createReaderSandbox({
            authenticated: true, username: "user1", mustChangePassword: false, mailReadOnly: true
        });
        sandbox.api = async (path) => {
            sandbox.apiCalls.push({ path });
            return sandbox.authMeResponse;
        };
        sandbox.refreshCurrentView = () => { sandbox.refreshCurrentViewCalled++; };

        await sandbox.checkAuth();

        assert.strictEqual(sandbox.state.view, "mailbox");
        assert.strictEqual(sandbox.elements.mailboxList.textContent, "只读邮件视图加载失败，请刷新");
        assert.strictEqual(sandbox.refreshCurrentViewCalled, 0);
    });

    it("login response passes username + mailReadOnly to the host", async () => {
        const handlers = {};
        const elements = {
            loginForm: { addEventListener: (type, handler) => { handlers[type] = handler; } },
            loginUsername: { value: " user1 " },
            loginPassword: { value: "secret" },
            loginError: { hidden: true, textContent: "" },
            changePasswordForm: { addEventListener() {} },
            logoutBtn: { addEventListener() {} }
        };
        const startCalls = [];
        const sandbox = {
            apiCalls: [],
            api: async (path, options) => {
                sandbox.apiCalls.push({ path, options });
                return { username: "user1", mailReadOnly: true, mustChangePassword: false };
            },
            startAuthenticatedApp: (username, mailReadOnly) => startCalls.push({ username, mailReadOnly }),
            stopAuthenticatedApp: () => {},
            showStatus: () => {},
            isMailReadOnlyMode: () => false,
            setView: function setViewStub() {},
            flushMailboxChatDrafts: async () => {},
            location: { reload() {} },
            $: (selector) => elements[selector.replace("#", "")] || { addEventListener() {}, hidden: true, textContent: "", value: "" }
        };
        vm.createContext(sandbox);
        vm.runInContext(extractFn("bindAuthEvents"), sandbox);
        vm.runInContext("bindAuthEvents()", sandbox);

        await handlers.submit({ preventDefault() {} });

        assert.deepStrictEqual(sandbox.apiCalls[0].path, "/api/auth/login");
        assert.deepStrictEqual(startCalls, [{ username: "user1", mailReadOnly: true }]);
    });
});

describe("reader lifecycle guards (03 I-2)", () => {
    function fnSandbox(names, base) {
        const sandbox = Object.assign({}, base);
        vm.createContext(sandbox);
        vm.runInContext(
            "var MAIL_READ_ONLY_USERNAME = 'user1';"
            + " var MAIL_READ_ONLY_COPY = {"
            + " identityError: '权限信息异常，请重新登录',"
            + " moduleError: '只读邮件视图加载失败，请刷新' };",
            sandbox
        );
        vm.runInContext(extractFn("mailReadOnlyState"), sandbox);
        vm.runInContext(extractFn("isMailReadOnlyMode"), sandbox);
        names.forEach((name) => vm.runInContext(extractFn(name), sandbox));
        return sandbox;
    }
    const readerState = () => ({ active: true, username: "user1", handle: null });

    it("setView rejects every non-mailbox view in reader mode", () => {
        const sandbox = fnSandbox(["setView"], {
            state: { view: "mailbox", mailReadOnly: readerState() }
        });
        vm.runInContext('setView("accounts"); setView("contacts"); setView("tasks");', sandbox);
        assert.strictEqual(sandbox.state.view, "mailbox");
    });

    it("refreshMailboxGroupPush does not call the settings endpoint", async () => {
        const sandbox = fnSandbox(["refreshMailboxGroupPush"], {
            state: { view: "mailbox", mailReadOnly: readerState() },
            appStarted: true,
            calls: [],
            api: async (path) => { sandbox.calls.push(path); return {}; }
        });
        await vm.runInContext("refreshMailboxGroupPush()", sandbox);
        assert.deepStrictEqual(sandbox.calls, []);
    });

    it("refreshAutoReplySummary does not call the summary endpoint", async () => {
        const sandbox = fnSandbox(["refreshAutoReplySummary"], {
            state: { view: "mailbox", mailReadOnly: readerState() },
            calls: [],
            api: async (path) => { sandbox.calls.push(path); return {}; },
            $: () => ({ textContent: "", disabled: false })
        });
        await vm.runInContext("refreshAutoReplySummary()", sandbox);
        assert.deepStrictEqual(sandbox.calls, []);
    });

    it("updateUnmatchedBadge never re-enters the pending-badge request", () => {
        let badgeCalls = 0;
        const sandbox = fnSandbox(["updateUnmatchedBadge"], {
            state: { mailReadOnly: readerState() },
            refreshUnmatchedBadge: () => { badgeCalls++; }
        });
        vm.runInContext("updateUnmatchedBadge(); updateUnmatchedBadge({}, 3);", sandbox);
        assert.strictEqual(badgeCalls, 0);
    });

    it("loadMailbox refreshes only the readonly handle and never loads accounts", async () => {
        let refreshed = 0;
        let accountLoads = 0;
        const sandbox = fnSandbox(["loadMailbox"], {
            state: { view: "mailbox", mailReadOnly: { active: true, username: "user1", handle: { refresh: async () => { refreshed++; } } } },
            loadMailboxAccounts: async () => { accountLoads++; }
        });
        await vm.runInContext("loadMailbox()", sandbox);
        assert.strictEqual(refreshed, 1);
        assert.strictEqual(accountLoads, 0);
    });

    it("refreshCurrentView refreshes only the readonly handle", async () => {
        let refreshed = 0;
        const sandbox = fnSandbox(["refreshCurrentView"], {
            state: { view: "mailbox", mailReadOnly: { active: true, username: "user1", handle: { refresh: async () => { refreshed++; } } } },
            loadAccounts: async () => { throw new Error("must not load accounts"); },
            loadMailbox: async () => { throw new Error("must not run legacy mailbox"); }
        });
        await vm.runInContext("refreshCurrentView()", sandbox);
        assert.strictEqual(refreshed, 1);
    });

    it("loadMailboxAccounts does not request sender accounts for reader", async () => {
        const sandbox = fnSandbox(["loadMailboxAccounts"], {
            state: { mailbox: {}, mailReadOnly: readerState() },
            calls: [],
            api: async (path) => { sandbox.calls.push(path); return []; }
        });
        await vm.runInContext("loadMailboxAccounts()", sandbox);
        assert.deepStrictEqual(sandbox.calls, []);
    });

    it("bindMobileCoreNavigation keeps only the mailbox option for reader", () => {
        const options = [];
        const menu = {
            replaceChildren() { options.length = 0; },
            appendChild(option) { options.push(option); },
            addEventListener() {},
            value: ""
        };
        const tabs = [
            { dataset: { view: "accounts", } },
            { dataset: { view: "mailbox" } },
            { dataset: { view: "tasks" } }
        ];
        const sandbox = fnSandbox(["bindMobileCoreNavigation"], {
            state: { view: "accounts", mailReadOnly: readerState() },
            mobileCoreNavigationBound: false,
            viewMeta: { accounts: ["邮箱账号", ""], mailbox: ["收发件箱", ""], tasks: ["任务记录", ""] },
            document: { createElement: () => ({ value: "", textContent: "" }) },
            window: { matchMedia: () => ({ addEventListener() {} }) },
            $: (selector) => (selector === "#mobileCoreView" ? menu : { addEventListener() {} }),
            $$: () => tabs,
            returnToMobileContactsList: () => {},
            onMobileCoreViewportChange: () => {}
        });
        sandbox.mobileCoreNavigationBound = false;
        vm.runInContext("bindMobileCoreNavigation()", sandbox);
        assert.strictEqual(options.length, 1);
        assert.strictEqual(options[0].value, "mailbox");
        assert.strictEqual(options[0].textContent, "收发件箱");
        assert.strictEqual(menu.value, "mailbox");
    });
});

describe("reader api guard (03 I-2)", () => {
    function apiSandbox(active) {
        const sandbox = {
            state: { mailReadOnly: { active, username: active ? "user1" : "admin", handle: null } },
            contextPath: "",
            fetchCalls: [],
            fetch: async (url, options) => {
                sandbox.fetchCalls.push({ url, options });
                return { ok: true, status: 200, text: async () => "" };
            },
            handleAuthResponse: async () => {}
        };
        vm.createContext(sandbox);
        ["mailReadOnlyState", "isMailReadOnlyMode", "isMailReadOnlyRequestAllowed", "api"].forEach((name) => {
            vm.runInContext(extractFn(name), sandbox);
        });
        return sandbox;
    }

    it("rejects non-whitelisted business requests before fetch", async () => {
        const sandbox = apiSandbox(true);
        await assert.rejects(
            () => vm.runInContext('api("/api/mail/sender-accounts")', sandbox),
            (error) => error.status === 403 && error.data && error.data.code === "READ_ONLY_FORBIDDEN"
        );
        await assert.rejects(
            () => vm.runInContext('api("/api/expert-inbound-notifications/settings", { method: "PUT" })', sandbox),
            (error) => error.status === 403
        );
        assert.deepStrictEqual(sandbox.fetchCalls, []);
    });

    it("allows exact reader draft method/path pairs and rejects forbidden capabilities before fetch", async () => {
        const sandbox = apiSandbox(true);
        const base = "/api/mail/mailbox/review-drafts";
        const allowed = [
            ["GET", "/api/mail/mailbox/conversations?page=0&size=20"],
            ["GET", "/api/mail/mailbox/conversations/7/messages?limit=50"],
            ["GET", "/api/auth/me"], ["POST", "/api/auth/login"], ["POST", "/api/auth/logout"],
            ["GET", base + "?contactId=7"], ["POST", base], ["GET", base + "/1"],
            ["PUT", base + "/1"], ["DELETE", base + "/1?expectedVersion=2"],
            ["GET", base + "/1/events"], ["POST", base + "/1/submit"],
            ["POST", base + "/1/withdraw"], ["POST", base + "/1/comments"]
        ];
        for (const [method, path] of allowed) {
            await vm.runInContext(`api(${JSON.stringify(path)}, {method:${JSON.stringify(method)}})`, sandbox);
        }
        assert.strictEqual(sandbox.fetchCalls.length, allowed.length);
        for (const suffix of ["send", "approve", "return", "translate", "context", "confirm-context",
            "events/1", "attachments", "comments/1"]) {
            for (const method of ["GET", "POST", "PUT", "DELETE"]) {
                await assert.rejects(() => vm.runInContext(`api("${base}/1/${suffix}",{method:"${method}"})`, sandbox),
                    error => error.status === 403);
            }
        }
        for (const path of [base + "/0", base + "/01", base + "/-1", base + "/x", base + "/1/",
            "/api/mail/mailbox/conversations/x/messages", "/api/mail/attachments/1",
            "/api/mail/mailbox/reply-drafts/1"]) {
            await assert.rejects(() => vm.runInContext(`api(${JSON.stringify(path)})`, sandbox), error => error.status === 403);
        }
        assert.strictEqual(sandbox.fetchCalls.length, allowed.length);
    });

    it("does not restrict the admin path", async () => {
        const sandbox = apiSandbox(false);
        await vm.runInContext('api("/api/mail/sender-accounts")', sandbox);
        assert.strictEqual(sandbox.fetchCalls.length, 1);
    });
});

describe("reader logout flush (04 I-17)", () => {
    it("awaits reader flush, aborts logout on failure, and never flushes admin drafts", async () => {
        let handler, fail = true;
        const calls = [];
        const sandbox = {
            $: selector => selector === "#logoutBtn" ? { addEventListener(type, fn) { handler = fn; } } : null,
            setView: {}, isMailReadOnlyMode: () => true,
            mailReadOnlyState: () => ({ handle: { async flushDrafts() { calls.push("flush"); if (fail) throw Error("offline"); } } }),
            flushMailboxChatDrafts: () => { throw Error("admin flush"); },
            api: async path => calls.push(path), location: { reload() { calls.push("reload"); } }
        };
        vm.createContext(sandbox);
        vm.runInContext(extractFn("bindAuthEvents"), sandbox);
        sandbox.bindAuthEvents();
        await handler();
        assert.deepStrictEqual(calls, ["flush"]);
        fail = false;
        await handler();
        assert.deepStrictEqual(calls, ["flush", "flush", "/api/auth/logout", "reload"]);
    });
});
