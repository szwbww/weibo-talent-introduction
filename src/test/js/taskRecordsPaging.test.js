const fs = require("fs");
const path = require("path");
const vm = require("vm");
const assert = require("assert");
const { describe, it } = require("node:test");

const root = path.join(__dirname, "..", "..", "main", "resources");
const html = fs.readFileSync(path.join(root, "static", "index.html"), "utf-8");
const app = fs.readFileSync(path.join(root, "static", "app.js"), "utf-8");

function extractFn(name) {
    const regex = new RegExp("(?:async\\s+)?function\\s+" + name + "\\s*\\([^)]*\\)\\s*\\{[\\s\\S]*?\\n\\}");
    const match = app.match(regex);
    if (!match) throw new Error("Could not find " + name + " in app.js");
    return match[0];
}

function extractConst(name) {
    const regex = new RegExp("const\\s+" + name + "\\s*=\\s*\\{[\\s\\S]*?\\n\\};");
    const match = app.match(regex);
    if (!match) throw new Error("Could not find const " + name + " in app.js");
    return match[0];
}

function createSandbox({ apiImpl }) {
    const elements = {
        pager: { hidden: false },
        prev: { disabled: false },
        next: { disabled: false },
        info: { textContent: "" },
        table: { innerHTML: "" },
        typeFilter: { value: "" },
        statusFilter: { value: "" }
    };
    const sandbox = {
        TASK_PAGE_SIZE: 50,
        state: { tasksPage: 0, tasksTotal: 0 },
        URLSearchParams: class {
            constructor() {
                this.map = new Map();
            }
            set(k, v) {
                this.map.set(k, v);
            }
            toString() {
                return Array.from(this.map.entries())
                    .map(([k, v]) => `${k}=${encodeURIComponent(v)}`)
                    .join("&");
            }
        },
        api: async (url) => apiImpl(url),
        $: (sel) => {
            const map = {
                "#taskPager": elements.pager,
                "#taskPrevPage": elements.prev,
                "#taskNextPage": elements.next,
                "#taskPageInfo": elements.info,
                "#tasksTable": elements.table,
                "#taskTypeFilter": elements.typeFilter,
                "#taskStatusFilter": elements.statusFilter
            };
            return map[sel];
        }
    };
    vm.createContext(sandbox);
    vm.runInContext(extractConst("statusLabels"), sandbox);
    for (const name of ["escapeHtml", "badge", "labelStatus", "renderTaskPager", "loadTasks"]) {
        vm.runInContext(extractFn(name), sandbox);
    }
    return { sandbox, elements };
}

function items(count, startId) {
    return Array.from({ length: count }, (_, i) => ({
        id: startId - i,
        taskType: "AUTO_REPLY_ALL",
        triggerType: "SCHEDULED",
        status: "SUCCESS",
        successCount: 4,
        failureCount: 0,
        startedAt: "2026-08-16T10:00:00",
        errorMessage: null
    }));
}

describe("task records paging (b1)", () => {
    it("S0-2: total=0 keeps #taskPager hidden", async () => {
        const { sandbox, elements } = createSandbox({ apiImpl: async () => ({ items: [], total: 0 }) });
        await sandbox.loadTasks();
        assert.strictEqual(elements.pager.hidden, true, "#taskPager must stay hidden when total is 0");
    });

    it("S0-2: first page disables prev and shows 第 1 页 / 共 137 条", async () => {
        const { sandbox, elements } = createSandbox({
            apiImpl: async () => ({ items: items(50, 1000), total: 137 })
        });
        await sandbox.loadTasks();
        assert.strictEqual(elements.pager.hidden, false);
        assert.strictEqual(elements.prev.disabled, true, "first page must disable 上一页");
        assert.strictEqual(elements.next.disabled, false);
        assert.strictEqual(elements.info.textContent, "第 1 页 / 共 137 条");
    });

    it("S0-2: last page disables next", async () => {
        const { sandbox, elements } = createSandbox({
            apiImpl: async () => ({ items: items(37, 1000), total: 137 })
        });
        sandbox.state.tasksPage = 2; // page 3 of 3: (2+1)*50 = 150 >= 137
        await sandbox.loadTasks();
        assert.strictEqual(elements.pager.hidden, false);
        assert.strictEqual(elements.next.disabled, true, "last page must disable 下一页");
        assert.strictEqual(elements.prev.disabled, false);
        assert.strictEqual(elements.info.textContent, "第 3 页 / 共 137 条");
    });

    it("T0-5: filter changes and the query button reset tasksPage to 0", () => {
        const btnBinding = /\$\("#loadTasksBtn"\)\.addEventListener\("click", \(\) => \{[\s\S]*?state\.tasksPage = 0;[\s\S]*?loadTasks\(\);/;
        assert.match(app, btnBinding, "loadTasksBtn click must reset tasksPage then loadTasks");
        for (const selector of ["#taskTypeFilter", "#taskStatusFilter"]) {
            const changeBinding = new RegExp(
                '\\$\\("' + selector + '"\\)\\.addEventListener\\("change", \\(\\) => \\{[\\s\\S]*?state\\.tasksPage = 0;[\\s\\S]*?loadTasks\\(\\);'
            );
            assert.match(app, changeBinding, selector + " change must reset tasksPage then loadTasks");
        }
    });

    it("N0-1: seven table columns render verbatim from the pre-change baseline", async () => {
        const { sandbox, elements } = createSandbox({
            apiImpl: async () => ({
                items: [{
                    id: 42,
                    taskType: "AUTO_REPLY_ALL",
                    triggerType: "QUEUE",
                    status: "SUCCESS",
                    successCount: 4,
                    failureCount: 0,
                    startedAt: "2026-08-16T10:00:00",
                    errorMessage: null
                }],
                total: 137
            })
        });
        await sandbox.loadTasks();
        const expected = [
            `
        <tr class="task-row" data-task-id="42" data-task-type="AUTO_REPLY_ALL" onclick="toggleTaskDetail(this)" style="cursor:pointer;">
            <td>42</td>
            <td>AUTO_REPLY_ALL</td>
            <td>QUEUE</td>
            <td>${sandbox.badge(sandbox.labelStatus("SUCCESS"), "ok")}</td>
            <td>4/0</td>
            <td>2026-08-16T10:00:00</td>
            <td></td>
        </tr>
    `
        ].join("");
        assert.strictEqual(elements.table.innerHTML, expected);
    });

    it("S0-1: index.html carries the taskPager skeleton verbatim", () => {
        const pagerIndex = html.indexOf('id="taskPager"');
        assert.ok(pagerIndex >= 0, "#taskPager must exist in index.html");
        const skeleton = html.slice(pagerIndex - 13, pagerIndex + 300);
        assert.match(skeleton, /<div id="taskPager" class="list-pager" hidden>/);
        assert.match(skeleton, /<button class="button small" id="taskPrevPage">上一页<\/button>/);
        assert.match(skeleton, /<span id="taskPageInfo" class="list-pager-info"><\/span>/);
        assert.match(skeleton, /<button class="button small" id="taskNextPage">下一页<\/button>/);
        const pagerMatches = html.match(/id="taskPager"/g);
        assert.strictEqual(pagerMatches.length, 1, "#taskPager must appear exactly once");
    });
});
