/* Recheck UI: server-owned frozen rosters, receipts and traces; memory-only host lifecycle. */
(function (global) {
    "use strict";
    const BASE = "/api/discovery/rechecks";
    const SIZE = 20;
    const names = ["名单快照与保护", "来源论文与原文", "姓名 / 邮箱身份", "学术与资格补证", "直接准入判定", "Apollo 辅助核验", "综合准入结论", "结果记录与同步"];
    const states = { NOT_STARTED: "未开始", RUNNING: "执行中", SUCCESS: "完成", INSUFFICIENT: "证据不足 / 受保护", SKIPPED: "已跳过（非失败）", ERROR: "错误", CANCELLED: "已取消", PREPARING: "正在固定名单", READY: "名单已固定，待确认", COMPLETED: "已完成", FAILED: "失败", INTERRUPTED: "已中断", QUEUED: "排队中", RESERVED: "待启动", RETRY_WAIT: "等待重试" };
    const active = run => run && ["PREPARING", "RUNNING"].includes(run.phase);
    const value = data => data == null || data === "" ? "未记录" : typeof data === "object" ? JSON.stringify(data) : String(data);
    function unpack(raw) {
        if (raw == null) return null;
        if (typeof raw === "string") { try { raw = JSON.parse(raw); } catch (_) { return raw; } }
        return raw && Object.prototype.hasOwnProperty.call(raw, "value") ? raw.value : raw;
    }
    function safeUrl(raw, base) {
        if (typeof raw !== "string" || !raw.trim()) return null;
        try {
            const url = new URL(raw, base);
            if (!["http:", "https:"].includes(url.protocol) || url.username || url.password) return null;
            if (url.pathname.includes("/api/") && url.origin !== new URL(base).origin) return null;
            return url.href;
        } catch (_) { return null; }
    }
    function createDiscoveryRecheck({ root, api, getSelectedDocIds, getFilter, onApplied, openPaper }) {
        const doc = root.ownerDocument;
        const find = key => root.querySelector('[data-rr="' + key + '"]');
        const dialogs = { config: find("config"), history: find("history-dialog"), detail: find("detail") };
        let mounted = false, epoch = 0, timer = null, run = null, prepared = null, preparedSpec = null;
        let rows = [], latest = {}, history = { kind: "runs", page: 0, total: 0 }, manifestPage = 0;
        let person = null, selectedStep = null, appliedRevision = null;
        const controllers = new Map(), sequences = new Map(), listeners = [], focusTargets = new Map(), actions = new WeakMap();
        const text = (key, data) => { find(key).textContent = value(data); };
        function node(tag, className, content) {
            const el = doc.createElement(tag);
            if (className) el.className = className;
            if (content != null) el.textContent = String(content);
            return el;
        }
        function add(parent, tag, className, content) { const el = node(tag, className, content); parent.appendChild(el); return el; }
        function button(parent, label, action) { const el = add(parent, "button", "rr-link", label); el.type = "button"; actions.set(el, action); return el; }
        function listen(el, event, fn) { el.addEventListener(event, fn); listeners.push(() => el.removeEventListener(event, fn)); }
        function stop() { clearTimeout(timer); timer = null; }
        function invalidate() {
            epoch++; stop(); controllers.forEach(c => c.abort()); controllers.clear();
            find("prepare").disabled = false;
        }
        async function request(lane, path, options = {}) {
            controllers.get(lane)?.abort();
            const controller = new AbortController(), token = epoch, sequence = (sequences.get(lane) || 0) + 1;
            controllers.set(lane, controller); sequences.set(lane, sequence);
            try {
                const result = await api(BASE + path, { ...options, signal: controller.signal });
                if (!mounted || token !== epoch || sequences.get(lane) !== sequence || controller.signal.aborted) throw new DOMException("Stale response", "AbortError");
                return result;
            } finally { if (controllers.get(lane) === controller) controllers.delete(lane); }
        }
        const post = (lane, path, body) => request(lane, path, { method: "POST", ...(body === undefined ? {} : { body: JSON.stringify(body) }) });
        function error(key, e, action) {
            if (e.name === "AbortError" || !mounted) return;
            const data = e.data || {};
            find(key).dataset.tone = "error";
            text(key, (data.code || "RECHECK_NETWORK_ERROR") + "：" + (data.message || e.message || "读取失败") + "；" + action);
        }
        function status(key, message) { delete find(key).dataset.tone; text(key, message); }
        function show(name, trigger) {
            focusTargets.set(dialogs[name], trigger || doc.activeElement);
            Object.entries(dialogs).forEach(([key, dialog]) => { if (key !== name && dialog.open) dialog.close(); });
            if (!dialogs[name].open) dialogs[name].showModal();
        }
        function close(name) {
            invalidate();
            if (dialogs[name].open) dialogs[name].close();
            schedule();
        }
        function currentSpec() {
            const checked = name => root.querySelector('input[name="rr-' + name + '"]:checked')?.value;
            return { scope: checked("scope") || "IDS", docIds: [...getSelectedDocIds()].map(String).sort(), filter: { ...getFilter() }, mode: checked("mode") || "AUDIT", evidenceMode: checked("evidence") || "REUSE" };
        }
        function signature() { return JSON.stringify(currentSpec()); }
        function invalidatePrepared() {
            prepared = null; preparedSpec = null; find("start").disabled = true;
            find("manifest-panel").hidden = true; text("scope-hash", "名单未固定");
            status("prepare-status", "范围或选项已变化，请重新固定名单。");
        }
        function checkSpec() { if (preparedSpec && preparedSpec !== signature()) { invalidate(); invalidatePrepared(); schedule(); } }
        function openConfig(trigger) {
            invalidate(); checkSpec();
            if (!root.querySelector('input[name="rr-scope"]:checked')) root.querySelector('input[value="IDS"]').checked = true;
            text("selected-count", getSelectedDocIds().length);
            text("filter-summary", value(getFilter()));
            show("config", trigger);
            if (prepared) { renderPrepared(); if (active(run)) poll(); }
            else { find("start").disabled = true; status("prepare-status", "请先固定名单；关闭窗口不会取消任务。"); schedule(); }
        }
        async function prepare() {
            invalidate(); invalidatePrepared();
            const spec = currentSpec(); preparedSpec = JSON.stringify(spec);
            if (spec.scope === "IDS" && !spec.docIds.length) { status("prepare-status", "请选择专家，或改用当前筛选的全部页。"); return; }
            const token = epoch;
            find("prepare").disabled = true; status("prepare-status", "正在固定名单… 尚不可启动。");
            try {
                run = await post("run", "/prepare", { ...spec, docIds: spec.scope === "IDS" ? spec.docIds : [], requestKey: global.crypto.randomUUID() });
                prepared = run; manifestPage = 0; renderRun(); renderPrepared();
                if (run.phase === "READY") await loadManifest();
                schedule();
            } catch (e) { error("prepare-status", e, "点击固定名单重试"); }
            finally { if (token === epoch && mounted) find("prepare").disabled = false; }
        }
        function renderPrepared() {
            if (!prepared) return;
            checkSpec(); if (!prepared) return;
            const access = prepared.settings?.apolloAccess;
            text("apollo-config", "冻结 Apollo search=" + value(access?.searchEnabled) + " / detail=" + value(access?.detailEnabled) + "；配置版本 " + value(access?.settingRevision));
            text("scope-hash", "固定 " + value(prepared.counts?.total) + " 人 · " + prepared.mode + " / " + prepared.evidenceMode + " · hash " + value(prepared.rosterHash));
            status("prepare-status", (states[prepared.phase] || prepared.phase) + "；任务 #" + prepared.taskId + (prepared.prepareTask?.error ? "；" + prepared.prepareTask.error + "，请重新固定名单" : ""));
            find("start").disabled = prepared.phase !== "READY" || !prepared.rosterHash;
            find("manifest-panel").hidden = prepared.phase !== "READY";
        }
        function renderRun() {
            if (!run) return;
            find("run").hidden = false;
            text("run-title", "重审 #" + run.runId); text("run-state", states[run.phase] || run.phase);
            text("run-context", run.mode + " / " + run.evidenceMode + " · 固定范围 " + run.scope + " · hash " + value(run.rosterHash));
            const c = run.counts;
            const metrics = [["固定总数", c.total], ["已处理", c.processed], [run.mode === "AUDIT" ? "预计准入" : "实际准入", run.mode === "AUDIT" ? c.wouldAdmit : c.actualAdmitted], ["待验证", c.pending], ["错误", c.errors], ["取消 / 保护（独立子计数）", "取消 " + value(c.cancelled) + " / 保护 " + value(c.protected)]];
            find("metrics").replaceChildren();
            metrics.forEach(([label, count]) => { const cell = add(find("metrics"), "div"); add(cell, "span", null, label); add(cell, "strong", null, value(count)).setAttribute("data-value", ""); });
            find("progress").max = Math.max(1, c.total); find("progress").value = c.processed;
            status("run-message", "排队 / 等待 " + value(c.pending) + "；处理中 " + value(c.processing) + "；候选待同步 " + value(c.syncPending) + " / 同步失败 " + value(c.syncErrors) + "（不是准入失败）。取消不撤销已准入；保护为独立重叠子计数。" + (run.prepareTask?.error ? " " + run.prepareTask.error : ""));
            find("cancel-run").disabled = !["PREPARING", "READY", "RUNNING"].includes(run.phase);
            if (run.mode === "ENFORCE" && c.actualAdmitted > 0 && appliedRevision !== run.runId + ":" + run.revision) {
                appliedRevision = run.runId + ":" + run.revision; onApplied();
            }
        }
        function schedule() { stop(); if (mounted && !doc.hidden && active(run)) timer = setTimeout(() => { timer = null; poll(); }, 2000); }
        async function poll() {
            stop(); if (!mounted || doc.hidden || !run) return;
            const id = run.runId;
            try {
                const result = await request("run", "/" + id);
                if (run.runId !== id) return;
                run = result; renderRun();
                if (prepared?.runId === id) { const ready = prepared.phase !== "READY" && result.phase === "READY"; prepared = result; renderPrepared(); if (ready && dialogs.config.open) await loadManifest(); }
                if (dialogs.detail.open && person?.runId === id) {
                    const itemId = person.itemId;
                    const detail = await request("person", "/" + id + "/items/" + itemId + "/steps");
                    if (person?.itemId === itemId) { person = detail; renderDetail(); }
                }
            } catch (e) { error(dialogs.config.open ? "prepare-status" : "run-message", e, "打开重审记录重新读取任务"); }
            schedule();
        }
        async function start() {
            checkSpec(); if (!prepared || prepared.phase !== "READY") return;
            find("start").disabled = true;
            try { run = await post("run", "/" + prepared.runId + "/start", { rosterHash: prepared.rosterHash, revision: prepared.revision }); prepared = run; renderRun(); close("config"); }
            catch (e) { error("prepare-status", e, "重新固定名单后确认执行"); if (e.name !== "AbortError") find("start").disabled = false; }
        }
        async function cancelRun() {
            if (!run) return;
            invalidate();
            try { run = await post("run", "/" + run.runId + "/cancel"); if (prepared?.runId === run.runId) prepared = run; renderRun(); renderPrepared(); }
            catch (e) { error("run-message", e, "重新读取任务后再次取消"); }
            schedule();
        }
        function table(container, headings) {
            container.replaceChildren(); const wrap = add(container, "div", "rr-table-wrap"), t = add(wrap, "table", "rr-table");
            const head = add(add(t, "thead"), "tr"); headings.forEach(h => add(head, "th", null, h)); return add(t, "tbody");
        }
        function pager(prefix, page, total) { text(prefix + "-page", "第 " + (page + 1) + " / " + Math.max(1, Math.ceil(total / SIZE)) + " 页 · " + total + " 条，每页20"); find(prefix + "-prev").disabled = page === 0; find(prefix + "-next").disabled = (page + 1) * SIZE >= total; }
        async function loadManifest() {
            if (!prepared || prepared.phase !== "READY") return;
            try {
                const page = await request("manifest", "/" + prepared.runId + "/manifest?page=" + manifestPage + "&size=20");
                const body = table(find("manifest"), ["docId", "身份 hash", "事实 hash", "基线 revision", "证据 hash", "状态"]);
                page.items.forEach(item => { const tr = add(body, "tr"); [item.docId, item.identityHash, item.factFingerprint, item.expectedAdmissionRevision, item.evidenceHash, item.state].forEach(v => add(tr, "td", null, value(v))); });
                pager("manifest", manifestPage, page.total);
            } catch (e) { error("prepare-status", e, "重新固定名单或翻页重试"); }
        }
        async function openHistory(kind, id, trigger) {
            invalidate(); person = null; history = { kind, id, page: 0, total: 0 };
            show("history", trigger); await loadHistory(); schedule();
        }
        async function loadHistory() {
            const h = { ...history };
            status("history-status", "正在读取" + (h.kind === "items" ? "本轮逐人流程" : h.kind === "person" ? "此人历次重审" : "重审记录") + "…");
            const path = h.kind === "items" ? "/" + h.id + "/items" : "/runs";
            try {
                const page = await request("history", path + "?page=" + h.page + "&size=20" + (h.kind === "person" ? "&docId=" + encodeURIComponent(h.id) : ""));
                history.total = page.total;
                const head = node("tr"); (h.kind === "items" ? ["专家 / docId", "执行状态", "执行前", "建议 / 实际应用", "当前 / 同步", "操作"] : ["开始时间 / 任务", "模式 / 证据", "固定范围", "预计 / 实际准入", "状态", "操作"]).forEach(label => add(head, "th", null, label));
                find("history-head").replaceChildren(head); find("history-rows").replaceChildren();
                page.items.forEach(item => {
                    const tr = add(find("history-rows"), "tr");
                    const fields = h.kind === "items" ? [[item.givenNames, item.familyNames].filter(Boolean).join(" ") + " / " + item.docId, item.state, item.before, "建议 " + value(item.proposed) + " / 应用 " + value(item.applied), "当前 " + value(item.current) + " / 同步 " + item.projection] : [value(item.createdAt) + " / #" + item.taskId, item.mode + " / " + item.evidenceMode, value(item.counts.total) + " 人 / " + item.scope, "预计 " + value(item.counts.wouldAdmit) + " / 实际 " + value(item.counts.actualAdmitted), states[item.phase] || item.phase];
                    fields.forEach(v => add(tr, "td", null, value(v)));
                    button(add(tr, "td"), "查看", trigger => h.kind === "items" ? openDetail(item.runId, item.itemId, trigger) : selectRun(item, h.kind === "person" ? h.id : null, trigger));
                });
                status("history-status", h.kind === "items" ? "本轮逐人流程；建议、实际应用和当前状态分别展示。" : h.kind === "person" ? "docId " + h.id + " 的历次重审；选择轮次后查看该人。" : "全部重审记录；选择轮次查看逐人流程。");
                pager("history", history.page, page.total);
            } catch (e) { error("history-status", e, "重新打开记录或翻页重试"); }
        }
        async function selectRun(selected, docId, trigger) {
            invalidate(); run = selected; renderRun(); schedule();
            if (docId) { await locatePerson(selected.runId, docId, trigger); }
            else { history = { kind: "items", id: selected.runId, page: 0, total: 0 }; await loadHistory(); }
        }
        async function locatePerson(runId, docId, trigger) {
            try {
                // Server orders items by docId; scan bounded pages only after an explicit person action, never N+1 on list render.
                for (let page = 0; ; page++) {
                    const result = await request("person", "/" + runId + "/items?page=" + page + "&size=20");
                    const item = result.items.find(i => i.docId === docId);
                    if (item) { await openDetail(runId, item.itemId, trigger); return; }
                    if ((page + 1) * SIZE >= result.total) break;
                }
                status("history-status", "RECHECK_ITEM_NOT_FOUND：本轮未找到此人；请重新打开此人历史。");
            } catch (e) { error("history-status", e, "重新打开此人历史"); }
        }
        async function openDetail(runId, itemId, trigger) {
            invalidate(); person = null; selectedStep = null;
            text("detail-person", "正在读取…");
            find("steps").replaceChildren(); find("step-body").replaceChildren(); find("detail-summary").replaceChildren(); find("sync-retry").hidden = true;
            root.querySelector("#recheckDetailTitle").textContent = "逐步验证"; show("detail", trigger);
            try { person = await request("person", "/" + runId + "/items/" + itemId + "/steps"); renderDetail(); }
            catch (e) { error("detail-person", e, "关闭后重新打开验证流程"); }
            schedule();
        }
        function renderDetail() {
            text("detail-person", [person.givenNames, person.familyNames].filter(Boolean).join(" ") + " · " + person.docId + " · " + person.state);
            const summary = find("detail-summary"); summary.replaceChildren();
            add(summary, "p", null, "主卡点：" + value(person.trace?.blockingStep) + "；全部原因：" + value(person.allReasons));
            add(summary, "p", null, "执行前 " + value(person.before) + "；本轮建议 " + value(person.proposed) + "；当前 " + value(person.current) + "；实际应用 " + value(person.applied));
            add(summary, "p", null, "候选同步 " + person.projection + "；" + value(person.projectionError) + "；同步重试不会重做资格或外调。");
            const steps = person.trace?.steps || [], last = new Map(); steps.forEach(s => last.set(s.stepNo, s));
            selectedStep = selectedStep || person.trace?.blockingStep || [...last.values()].filter(s => !["NOT_STARTED", "RUNNING"].includes(s.state)).map(s => s.stepNo).sort((a, b) => b - a)[0] || 1;
            find("steps").replaceChildren();
            names.forEach((name, index) => {
                const number = index + 1, step = last.get(number), li = add(find("steps"), "li"), b = add(li, "button", "rr-step"); b.type = "button"; b.dataset.stepNo = String(number);
                if (step) b.dataset.state = step.state;
                if (number === selectedStep) b.setAttribute("aria-current", "step");
                add(b, "span", "rr-step-number", number); const title = add(b, "span", "rr-step-text", name); add(title, "small", null, step ? states[step.state] || step.state : "历史未记录");
                actions.set(b, () => { selectedStep = number; renderDetail(); });
            });
            renderStep(last.get(selectedStep), steps.filter(s => s.stepNo === selectedStep));
            text("detail-version", "版本 " + value(person.versions) + " · 更新 " + value(person.updatedAt));
            find("sync-retry").hidden = !(person.actualAdmitted && person.applied && person.state === "COMPLETED" && ["PENDING", "ERROR"].includes(person.projection));
        }
        function renderStep(step, attempts) {
            const body = find("step-body"); body.replaceChildren();
            add(body, "h4", null, selectedStep + ". " + names[selectedStep - 1]).setAttribute("data-step-title", "");
            add(body, "span", "rr-tag", step ? states[step.state] || step.state : "历史未记录").setAttribute("data-step-state", "");
            add(body, "p", null, step ? value(unpack(step.reasonJson)) : "历史未记录；不推断成功或执行时间。").setAttribute("data-step-reason", "");
            const facts = add(body, "dl", "rr-facts");
            const factKeys = { 输入: "input", 证据: "evidence", 版本: "version", 执行时间: "time", 尝试次数: "attempt" };
            const fact = (label, content, cls) => { add(facts, "dt", null, label); const dd = add(facts, "dd", cls, value(content)); if (factKeys[label]) dd.setAttribute("data-step-" + factKeys[label], ""); return dd; };
            fact("输入", unpack(step?.inputJson));
            const evidence = fact("证据", "");
            (person.sourceRefs || []).forEach(ref => {
                add(evidence, "p", "rr-muted", "来源 " + value(ref.sourceName) + " / " + value(ref.sourceItemKey) + " · SHA " + value(ref.bodySha256) + " · 页 " + value(ref.page));
                if (Number.isSafeInteger(ref.archiveRecordId) && ref.archiveRecordId > 0 && Number.isSafeInteger(ref.attemptId) && ref.attemptId > 0 && /^[a-f0-9]{64}$/i.test(ref.bodySha256 || "")) {
                    const link = add(evidence, "a", "rr-link", "查看留存原文"); link.href = "#";
                    actions.set(link, async () => {
                        const token = epoch, controller = new AbortController();
                        controllers.get("paper")?.abort(); controllers.set("paper", controller);
                        try { await openPaper({ ...ref }, link, controller.signal); }
                        catch (e) { if (mounted && token === epoch) error("detail-person", e, "核对固定来源后重新打开原文"); }
                        finally { if (controllers.get("paper") === controller) controllers.delete("paper"); }
                    });
                }
                const url = safeUrl(ref.sourceUrl, doc.baseURI); if (url) { const link = add(evidence, "a", "rr-link", "来源链接"); link.href = url; link.target = "_blank"; link.rel = "noopener noreferrer"; }
            });
            if (!person.sourceRefs?.length) evidence.textContent = "来源 / SHA / 页码未记录";
            fact("版本", unpack(step?.versionJson), "rr-code");
            let time = "历史未记录";
            if (step?.startedAt) { time = step.startedAt + " ～ " + value(step.finishedAt); if (step.finishedAt) { const ms = Date.parse(step.finishedAt) - Date.parse(step.startedAt); if (Number.isFinite(ms) && ms >= 0) time += "（" + ms + " ms）"; } }
            fact("执行时间", time); fact("尝试次数", attempts.length || "历史未记录");
            const output = unpack(step?.outputJson); fact("输出", output);
            const quote = output?.quote || output?.evidence?.quote;
            const quoteNode = add(body, "blockquote", "rr-quote", quote); quoteNode.setAttribute("data-step-quote", ""); quoteNode.hidden = !quote;
            attempts.forEach(a => { const detail = add(body, "details"); add(detail, "summary", null, "尝试 #" + a.attemptNo + " · " + (states[a.state] || a.state)); add(detail, "p", "rr-muted", "原因 " + value(unpack(a.reasonJson)) + "；输入 " + value(unpack(a.inputJson)) + "；输出 " + value(unpack(a.outputJson)) + "；版本 " + value(unpack(a.versionJson)) + "；时间 " + value(a.startedAt) + " ～ " + value(a.finishedAt)); });
            const delta = add(body, "div", "rr-delta"); add(add(delta, "div", null, "执行前"), "strong", null, value(person.before)).setAttribute("data-before", ""); add(add(delta, "div", null, "本轮建议 / 实际应用"), "strong", null, "建议 " + value(person.proposed) + " / 实际应用 " + value(person.applied)).setAttribute("data-after", "");
        }
        async function syncRetry() {
            if (!person || find("sync-retry").hidden) return;
            const { runId, itemId } = person; find("sync-retry").disabled = true;
            try { await post("sync", "/" + runId + "/sync-retry", { itemIds: [itemId] }); await openDetail(runId, itemId, find("sync-retry")); onApplied(); }
            catch (e) { error("detail-person", e, "关闭后重新读取同步状态，再仅重试候选同步"); }
            finally { if (mounted) find("sync-retry").disabled = false; }
        }
        async function onRows(nextRows, action) {
            rows = nextRows || []; checkSpec();
            if (!mounted) return;
            if (action?.docId) {
                invalidate();
                try { const result = await request("latest", "/latest?" + new URLSearchParams({ docIds: String(action.docId) })); const found = result[action.docId]; if (found) { run = found; renderRun(); await locatePerson(found.runId, action.docId, action.trigger); } else await openHistory("person", action.docId, action.trigger); }
                catch (e) { if (e.name !== "AbortError") { error("run-message", e, "点击验证流程重试"); find("run").hidden = false; } }
                return;
            }
            controllers.get("latest")?.abort();
            const ids = [...new Set(rows.map(r => r.docId).filter(Boolean))].slice(0, 100);
            if (!ids.length) { latest = {}; return; }
            try {
                latest = await request("latest", "/latest?" + new URLSearchParams({ docIds: ids.join(",") }));
                root.parentElement.querySelectorAll("button[data-recheck-doc]").forEach(button => {
                    const saved = latest[button.dataset.recheckDoc];
                    button.title = saved ? "最近重审 #" + saved.runId + " · " + (states[saved.phase] || saved.phase) : "尚无重审记录，查看历史";
                });
            }
            catch (e) { if (e.name !== "AbortError") { error("run-message", e, "重新加载审核列表以读取最新重审记录"); find("run").hidden = false; } }
        }
        function click(event) {
            const target = event.target.closest("button,a"); if (!target || !root.contains(target) || target.disabled) return;
            const dynamic = actions.get(target); if (dynamic) { event.preventDefault(); dynamic(target); return; }
            const key = target.dataset.rr;
            if (key?.startsWith("close-")) { close(key.slice(6)); return; }
            if (key === "open") openConfig(target);
            else if (key === "prepare") prepare();
            else if (key === "start") start();
            else if (key === "cancel-run") cancelRun();
            else if (key === "history") openHistory("runs", null, target);
            else if (key === "run-details" && run) openHistory("items", run.runId, target);
            else if (key === "person-history" && person) openHistory("person", person.docId, target);
            else if (key === "sync-retry") syncRetry();
            else if (key === "manifest-prev" || key === "manifest-next") { manifestPage += key.endsWith("prev") ? -1 : 1; loadManifest(); }
            else if (key === "history-prev" || key === "history-next") { history.page += key.endsWith("prev") ? -1 : 1; loadHistory(); }
        }
        function mount() {
            if (mounted) return; mounted = true; invalidate();
            if (!run) find("run").hidden = true;
            listen(root, "click", click);
            listen(root, "change", event => { if (event.target.matches('input[name^="rr-"]')) { invalidate(); invalidatePrepared(); schedule(); } });
            listen(doc, "visibilitychange", () => { invalidate(); if (!doc.hidden && run) poll(); });
            Object.entries(dialogs).forEach(([name, dialog]) => {
                listen(dialog, "keydown", event => { if (event.key === "Escape") event.stopPropagation(); });
                listen(dialog, "cancel", event => { event.preventDefault(); event.stopPropagation(); close(name); });
                listen(dialog, "close", () => { const target = focusTargets.get(dialog); focusTargets.delete(dialog); if (mounted && target?.isConnected) target.focus(); });
            });
            schedule();
        }
        function unmount() {
            mounted = false; invalidate(); listeners.splice(0).forEach(remove => remove());
            Object.values(dialogs).forEach(dialog => { if (dialog.open) dialog.close(); }); focusTargets.clear();
            person = null; rows = []; latest = {}; prepared = null; preparedSpec = null;
        }
        return { mount, unmount, onRows };
    }
    global.createDiscoveryRecheck = createDiscoveryRecheck;
    if (typeof module !== "undefined" && module.exports) module.exports = { createDiscoveryRecheck, safeUrl };
})(typeof window === "undefined" ? globalThis : window);
