const fs = require("fs");
const path = require("path");
const vm = require("vm");
const assert = require("assert");
const { describe, it } = require("node:test");

const appJsPath = path.join(__dirname, "..", "..", "main", "resources", "static", "app.js");
const appJsSource = fs.readFileSync(appJsPath, "utf-8");
const runtimePath = path.join(__dirname, "..", "..", "main", "resources", "static", "task-modal-runtime.js");
const runtimeSource = fs.readFileSync(runtimePath, "utf-8");

function extractFn(name) {
    const regex = new RegExp("(?:async\\s+)?function\\s+" + name + "\\s*\\([^)]*\\)\\s*\\{[\\s\\S]*?\\n\\}");
    const match = appJsSource.match(regex);
    if (!match) throw new Error("Could not find " + name + " in app.js");
    return match[0];
}

function createSandbox() {
    const sandbox = {
        contextPath: "",
        TASK_WATCHER_INTERVAL_MS: 3000,
        TASK_WATCHER_LAUNCH_GRACE_MS: 30000,
        TASK_WATCHER_MAX_INITIAL_204: 10,
        taskButtonMapping: {
            EXPERT_REVALIDATION: { btnId: "revalidateBtn", label: "重新验证候选人" }
        },
        taskWatchers: {},
        document: {
            body: { classList: { remove: () => {} } },
            querySelector: () => ({ hidden: false })
        },
        $: () => ({ hidden: false }),
        fetch: async () => ({
            ok: true,
            status: 200,
            json: async () => ({ status: "COMPLETED" })
        }),
        setInterval: () => 1,
        clearInterval: () => {},
        setTimeout: (fn) => fn(),
        clearTimeout: () => {},
        openTaskLaunchModal: () => {},
        stopTaskModalPolling: () => {},
        showStatus: () => {},
        restoreTaskButton: () => {}
    };
    vm.createContext(sandbox);
    vm.runInContext(runtimeSource, sandbox);
    vm.runInContext(extractFn("closeTaskModal"), sandbox);
    vm.runInContext(extractFn("pollTaskWatcher"), sandbox);
    vm.runInContext(extractFn("startTaskWatcher"), sandbox);
    vm.runInContext(extractFn("stopTaskWatcher"), sandbox);
    vm.runInContext(extractFn("handleRevalidateCandidates"), sandbox);
    vm.runInContext(extractFn("isCurrentTaskWatcher"), sandbox);
    return sandbox;
}

describe("task modal lifecycle integration", () => {
    it("running task opened from real handler starts watcher on immediate close and restores on terminal", async () => {
        const sandbox = createSandbox();
        const events = [];
        sandbox.isTaskRunning = async () => true;
        sandbox.openTaskModal = (taskType, label, btnId, options) => {
            const ctx = sandbox.createTaskModalContext(taskType, label, btnId, "PROGRESS");
            ctx.knownActiveAtOpen = options.knownActiveAtOpen;
            sandbox.currentTaskModal = ctx;
            events.push(["open", options.knownActiveAtOpen]);
        };
        sandbox.setInterval = () => {
            events.push(["watcher-started"]);
            return 7;
        };
        sandbox.restoreTaskButton = (btnId) => events.push(["restore", btnId]);
        sandbox.showStatus = (message, level) => events.push(["status", message, level]);

        await sandbox.handleRevalidateCandidates();
        sandbox.closeTaskModal();
        await new Promise(resolve => setImmediate(resolve));

        assert.strictEqual(events.length, 4);
        assert.strictEqual(events[0][0], "open");
        assert.strictEqual(events[0][1], true);
        assert.strictEqual(events[1][0], "watcher-started");
        assert.strictEqual(events[2][0], "restore");
        assert.strictEqual(events[2][1], "revalidateBtn");
        assert.strictEqual(events[3][0], "status");
        assert.strictEqual(events[3][1], "重新验证候选人 已完成");
        assert.strictEqual(events[3][2], "ok");
    });

    it("A/B watcher replacement keeps B alive and only completes/notifies on B", async () => {
        const sandbox = createSandbox();
        const events = [];

        sandbox.isTaskRunning = async () => true;
        sandbox.restoreTaskButton = (btnId) => events.push(["restore", btnId]);
        sandbox.showStatus = (message, level) => events.push(["status", message, level]);
        sandbox.openTaskModal = (taskType, label, btnId, options) => {
            sandbox.stopTaskWatcher(taskType, false);
            const ctx = sandbox.createTaskModalContext(taskType, label, btnId, "PROGRESS");
            ctx.knownActiveAtOpen = options.knownActiveAtOpen;
            sandbox.currentTaskModal = ctx;
        };

        // A's fetch resolver
        let resolveA;
        const promiseA = new Promise(resolve => { resolveA = resolve; });

        // B's fetch resolver
        let resolveB;
        const promiseB = new Promise(resolve => { resolveB = resolve; });

        let fetchCount = 0;
        sandbox.fetch = async (url) => {
            fetchCount++;
            if (fetchCount === 1) {
                // Watcher A
                await promiseA;
                return {
                    ok: true,
                    status: 200,
                    json: async () => ({ status: "COMPLETED", executionId: 100 })
                };
            }
            if (fetchCount === 2) {
                // Watcher B first query (RUNNING)
                await promiseB;
                return {
                    ok: true,
                    status: 200,
                    json: async () => ({ status: "RUNNING", executionId: 101 })
                };
            }
            if (fetchCount === 3) {
                // Watcher B second query (COMPLETED)
                return {
                    ok: true,
                    status: 200,
                    json: async () => ({ status: "COMPLETED", executionId: 101 })
                };
            }
            return { status: 204 };
        };

        // 1. Start Watcher A by simulating closing a launch/running modal
        sandbox.openTaskModal("EXPERT_REVALIDATION", "重新验证候选人", "revalidateBtn", { knownActiveAtOpen: true });
        sandbox.closeTaskModal();
        const watcherA = sandbox.taskWatchers["EXPERT_REVALIDATION"];
        assert.ok(watcherA);

        // 2. Open task modal again (which stops A)
        sandbox.openTaskModal("EXPERT_REVALIDATION", "重新验证候选人", "revalidateBtn", { knownActiveAtOpen: true });
        assert.strictEqual(sandbox.taskWatchers["EXPERT_REVALIDATION"], undefined);

        // 3. Close it immediately again to start Watcher B
        sandbox.closeTaskModal();
        const watcherB = sandbox.taskWatchers["EXPERT_REVALIDATION"];
        assert.ok(watcherB);
        assert.notStrictEqual(watcherA, watcherB);

        // 4. Resolve A's query (returns COMPLETED for 100)
        resolveA();
        await new Promise(resolve => setImmediate(resolve));

        // Watcher B should be completely unaffected
        assert.strictEqual(sandbox.taskWatchers["EXPERT_REVALIDATION"], watcherB);
        assert.strictEqual(events.length, 0); // No restore or notification from A

        // 5. Let B's first query resolve (returns RUNNING)
        resolveB();
        await new Promise(resolve => setImmediate(resolve));

        // Watcher B is still active
        assert.strictEqual(sandbox.taskWatchers["EXPERT_REVALIDATION"], watcherB);
        assert.strictEqual(watcherB.observedActive, true);
        assert.strictEqual(events.length, 0);

        // 6. Run next poll for B (resolves to COMPLETED for 101)
        await sandbox.pollTaskWatcher("EXPERT_REVALIDATION");

        // Watcher B is completed, button restored, and notification sent
        assert.strictEqual(sandbox.taskWatchers["EXPERT_REVALIDATION"], undefined);
        assert.strictEqual(events.length, 2);
        assert.strictEqual(events[0][0], "restore");
        assert.strictEqual(events[0][1], "revalidateBtn");
        assert.strictEqual(events[1][0], "status");
        assert.strictEqual(events[1][1], "重新验证候选人 已完成");
        assert.strictEqual(events[1][2], "ok");
    });
});
