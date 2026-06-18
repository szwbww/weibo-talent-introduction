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
    return sandbox;
}

// Create a sandbox with all bootstrap-relevant functions extracted from app.js.
// Sub-functions of bootstrap() that are NOT auth-relevant (bindEvents, initManualOutreach, etc.)
// are replaced with stubs to avoid needing the full DOM.
function createBootstrapSandbox(authMeResponse) {
    let badgeCalled = 0;
    let resumePollingCalled = 0;
    let refreshViewCalled = 0;

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
