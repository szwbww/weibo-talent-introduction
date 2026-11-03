/* Owned, authenticated view of durable paper-reprocess runs. Closing is never cancellation. */
(function () {
    'use strict';
    const BASE = '/api/expert-discovery/paper-reprocess';
    const activeStates = new Set(['PREPARING', 'RUNNING', 'CANCELLING']);
    const states = { PREPARING: '固定范围中', RUNNING: '处理中', CANCELLING: '取消中', COMPLETED: '已完成', COMPLETED_WITH_ERRORS: '已完成，部分失败', FAILED: '失败', INTERRUPTED: '已中断', CANCELLED: '已取消' };
    const traceStates = { NOT_STARTED: '未开始', PENDING: '未开始', SUCCESS: '成功', INSUFFICIENT: '证据不足', CANCELLED: '已取消', SKIPPED: '已跳过', RUNNING: '进行中', ERROR: '错误' };
    const steps = ['读取原文', '重新解析', '身份关联', '补录 / 补齐', '资格重审', '保存结果'];
    const reasons = { SOURCE_MISSING: '无可用留存原文（未留存、已删除或保存异常）', SOURCE_CHANGED: '原文或元数据已变化', PARSE_FAILED: '解析失败', PARSED: '解析已完成', CONFLICT: '来源身份冲突', SOURCE_IDENTITY_CONFLICT: '来源身份冲突', IDENTITY_NOT_BOUND: '缺少可核验的姓名与邮箱关联', AUDIT_CANDIDATE: '仅对照候选，未核查档案是否存在', BUSINESS_PROTECTED: '人工、联系或应用档案保护', EXISTING_IDENTITY_CONFLICT: '已有档案身份冲突', EMAIL_MULTIPLE_DOCUMENTS: '邮箱关联多个档案', FOREIGN_DOCUMENT: '档案不属于本任务可续接范围', SOURCE_IDENTITY_NOT_UNIQUE: '原文身份不唯一', NO_ELIGIBLE_EMAIL: '未取得符合现有规则的邮箱', APPLY_FAILED: '补录或登记失败', CANCELLED: '任务已取消', CANCEL_REQUESTED: '已请求取消', WAITING_RECHECK_SLOT: '等待原重审任务槽位', WAITING_VERIFICATION: '等待资格核验', VERSION_CHANGED: '解析版本已变化，请新建任务', EXECUTION_INTERRUPTED: '任务执行中断' };
    const value = v => v == null ? '—' : String(v);
    const reason = r => [r?.reasonCode && (reasons[r.reasonCode] || r.reasonCode), r?.reason].filter(Boolean).join(' · ') || '尚无处理结论';
    function mount({ root, api, makeUrl, openPaper, openVerification, onStatus }) {
        const doc = root.ownerDocument;
        const find = name => root.querySelector('[data-rp="' + name + '"]');
        const action = name => root.querySelector('[data-rp-action="' + name + '"]');
        const confirm = root.querySelector('#paperReprocessConfirm'), detailDialog = root.querySelector('#paperReprocessDetail');
        const template = root.querySelector('#rpTraceTemplate');
        let source = null, visible = false, disposed = false, epoch = 0, runSeq = 0, itemSeq = 0, dialogSeq = 0;
        let preview = null, apollo = null, snapshot = null, timer = null, submitting = false, mutating = false, authFailed = false;
        let tab = 'PAPER', historyPage = 0, selectedItem = null, renderedList = '';
        const pages = { PAPER: 0, TARGET: 0 }, totals = { PAPER: null, TARGET: null };
        const controllers = new Set(), listeners = [], focusTargets = new Map(), requests = new Map();
        function listen(el, event, fn, capture = false) { el.addEventListener(event, fn, capture); listeners.push(() => el.removeEventListener(event, fn, capture)); }
        function text(name, v) { find(name).textContent = value(v); }
        function error(name, message) { text(name, message || ''); find(name).hidden = !message; }
        function stale(owner) { return disposed || !visible || owner !== epoch; }
        function stop() { clearTimeout(timer); timer = null; }
        function abort() { controllers.forEach(c => c.abort()); controllers.clear(); }
        async function request(path, options = {}, absolute = false) {
            const owner = epoch, controller = new AbortController(); controllers.add(controller);
            try {
                const result = await api((absolute ? '' : BASE) + path, { ...options, signal: controller.signal });
                if (stale(owner) || controller.signal.aborted) throw new DOMException('旧上下文', 'AbortError');
                return result;
            } catch (e) {
                if (!stale(owner) && (e.status === 401 || e.status === 403)) { authFailed = true; stop(); action('open').disabled = action('start').disabled = true; }
                throw e;
            } finally { controllers.delete(controller); }
        }
        const post = (path, body) => request(path, { method: 'POST', body: JSON.stringify(body) });
        function failure(e, target = 'error') {
            if (e.name === 'AbortError') return;
            error(target, (authFailed ? '权限失效，已停止更新；历史结果保留。' : snapshot ? '更新失败，显示上次结果。' : '读取失败，尚无成功快照。') + ' ' + e.message);
        }
        function node(tag, cls, content) { const el = doc.createElement(tag); if (cls) el.className = cls; if (content != null) el.textContent = String(content); return el; }
        function append(parent, tag, cls, content) { const el = node(tag, cls, content); parent.appendChild(el); return el; }
        function button(parent, label, fn, disabled = false) { const b = append(parent, 'button', 'dp-link', label); b.type = 'button'; b.disabled = disabled; b.addEventListener('click', fn); return b; }
        function show(dialog, trigger) { focusTargets.set(dialog, trigger || doc.activeElement); if (!dialog.open) dialog.showModal(); }
        function lockMode(locked) { root.querySelectorAll('input[name="paperReprocessMode"]').forEach(input => { input.disabled = locked; }); }
        function close(dialog) { if (dialog.open) dialog.close(); }
        function scopeText() {
            action('open').disabled = !visible || !preview || authFailed || submitting || Boolean(preview.activeRun);
            text('scopeText', source == null ? '请选择一个执行任务' : preview ? '执行 #' + source + ' · 全部 ' + value(preview.recordCount) + ' 篇 · 缺原文 ' + value(preview.missingSourcePapers) + ' 篇' : '执行 #' + source + ' · 正在读取整批范围…');
        }
        function schedule() {
            stop();
            if (visible && !authFailed && snapshot && activeStates.has(snapshot.run.state)) timer = setTimeout(refreshRun, 5000);
        }
        function render() {
            const panel = find('runPanel'); panel.hidden = !snapshot;
            if (!snapshot) return;
            const r = snapshot.run, s = snapshot.summary;
            text('runTitle', '重新处理 #' + r.executionId + ' · ' + (r.mode === 'AUDIT' ? '仅解析对照' : '补录 / 补齐并自动重审'));
            text('runScope', '源执行 #' + r.sourceExecutionId + ' · 创建 ' + r.createdAt + ' · 更新 ' + r.updatedAt);
            text('runState', states[r.state] || r.state);
            text('papersTotal', snapshot.totalKnown ? s.papers : null);
            text('papersProcessed', s.paperComplete == null ? null : s.paperComplete + s.paperErrors + s.paperCancelled);
            text('newRecords', s.actualNew); text('repairedRecords', s.actualUpdated); text('admitted', s.actualAdmitted); text('pendingVerification', s.pendingVerification);
            const proposal = r.mode === 'AUDIT'
                ? '拟补录 ' + value(s.wouldCreate) + ' / 拟补齐 ' + value(s.wouldRepair) + '（目标；— 表示未核查档案存在性，候选不等于拟新增）'
                : '正式补录按实际结果统计；拟变化仅对照模式提供';
            text('summary', (r.mode === 'AUDIT' ? '仅对照，未应用；实际业务数为0。' : '') + proposal + '。无变化 ' + value(s.unchanged) + ' · 冲突 ' + value(s.conflict) + ' · 保护 ' + value(s.protected) + ' · 资格不满足 ' + value(s.qualification?.notMet) + ' / 未知 ' + value(s.qualification?.unknown) + ' / 冲突 ' + value(s.qualification?.conflict) + ' / 未评估 ' + value(s.qualification?.notEvaluated) + ' / 未取得核验 ' + value(s.qualification?.unavailable) + ' · 缺源论文 ' + value(s.missingSourcePapers) + ' · 论文失败 ' + value(s.paperErrors) + ' / 取消 ' + value(s.paperCancelled) + ' · 目标失败 ' + value(s.targetErrors) + ' · 候选同步失败 ' + value(s.syncFailure) + '。准入按档案计数，不等于去重人数；目标按本批唯一邮箱计数，分类可重叠，不可相加。' + (s.targetTotalKnown ? '目标总量已固定。' : '目标总量仍在形成。') + '未知统计以 — 显示。');
            const progress = find('progress'); progress.max = snapshot.totalKnown && s.papers > 0 ? s.papers : 1;
            if (snapshot.totalKnown) progress.value = s.paperComplete + s.paperErrors + s.paperCancelled; else progress.removeAttribute('value');
            text('phase', snapshot.progressText + ' · 阶段 ' + (reasons[r.scope?.reasonCode] || r.phase));
            action('cancel').hidden = !activeStates.has(r.state) || r.cancelRequested;
            action('cancel').disabled = mutating || authFailed;
            action('retry').hidden = !['FAILED', 'INTERRUPTED', 'CANCELLED', 'COMPLETED_WITH_ERRORS'].includes(r.state);
            action('retry').disabled = mutating || authFailed;
            action('history').disabled = authFailed;
        }
        function renderPager() {
            const total = totals[tab], page = pages[tab];
            text('pageInfo', '第 ' + (page + 1) + ' / ' + (total == null ? '—' : Math.max(1, Math.ceil(total / 20))) + ' 页 · 每页20条 · 共 ' + value(total) + (tab === 'PAPER' ? ' 篇论文' : ' 个邮箱目标'));
            action('previous').disabled = page === 0 || total == null || authFailed;
            action('next').disabled = total == null || (page + 1) * 20 >= total || authFailed;
        }
        function renderRows(items, kind) {
            const body = find(kind === 'PAPER' ? 'paperRows' : 'targetRows'); body.replaceChildren();
            for (const item of items) {
                const tr = append(body, 'tr'), input = item.input || {}, result = item.result || {}, analysis = result.analysis || {};
                if (kind === 'PAPER') {
                    const title = append(tr, 'td'); append(title, 'span', 'dp-title', input.metadata?.title || input.paperKey || '论文 #' + input.recordId); append(title, 'span', 'dp-sub', '原执行 #' + source + ' · 记录 #' + input.recordId);
                    const first = input.first?.outcome || input.first;
                    const firstBindings = Array.isArray(first?.emails) ? first.emails.filter(e => e.givenNames?.trim() && e.familyNames?.trim()).length : null;
                    append(tr, 'td', '', firstBindings == null ? '首次基线缺失，关联未知' : '首次姓名关联 ' + firstBindings);
                    append(tr, 'td', '', Array.isArray(analysis.after) ? '本次姓名关联 ' + analysis.after.filter(b => b.authorName).length : '本次尚未完成解析');
                    append(tr, 'td', '', '新增绑定 ' + value(analysis.deltaAddedBindings) + ' · ' + reason(result));
                    append(tr, 'td', '', (result.trace || []).map(t => t.stepNo + ' ' + (traceStates[t.state] || t.state)).join('；') || '未开始');
                } else {
                    const ref = input.paperRefs?.[0] || result.paperRefs?.[0] || {};
                    const name = append(tr, 'td'); append(name, 'span', 'dp-title', [ref.givenNames, ref.familyNames].filter(Boolean).join(' ') || '姓名未关联'); append(name, 'span', 'dp-sub', input.email);
                    append(tr, 'td', '', ({ NEW: '新增档案', REPAIRED: '补齐已有档案', UNCHANGED: '无变化', PROTECTED: '受保护', CONFLICT: '冲突', ERROR: '错误', SKIPPED: '已跳过' })[result.targetOutcome] || (snapshot.run.mode === 'AUDIT' ? '仅对照，未应用' : '尚未处理'));
                    append(tr, 'td', '', '核验 ' + value(result.verificationState) + ' · 本次实际准入 ' + value(result.actualAdmitted) + ' · 同步 ' + value(result.projection));
                    append(tr, 'td', '', reason(result));
                }
                button(append(tr, 'td'), '查看六步', e => showItem(item.id, e.currentTarget));
            }
            if (!items.length) { const cell = append(append(body, 'tr'), 'td', 'dp-empty', '暂无' + (kind === 'PAPER' ? '论文' : '档案') + '记录'); cell.colSpan = kind === 'PAPER' ? 6 : 5; }
        }
        async function loadItems() {
            if (!snapshot) return;
            const owner = epoch, seq = ++itemSeq, id = snapshot.run.executionId, kind = tab, page = pages[kind];
            try {
                const result = await request('/runs/' + id + '/items?kind=' + kind + '&page=' + page + '&size=20');
                if (stale(owner) || seq !== itemSeq || snapshot?.run.executionId !== id) return;
                totals[kind] = result.total;
                // Identical poll payloads must not replace row nodes: the open-detail trigger stays connected,
                // so closing a dialog can still restore focus to it.
                const signature = id + '|' + kind + '|' + page + '|' + JSON.stringify(result.items);
                if (signature !== renderedList) { renderedList = signature; renderRows(result.items, kind); }
                renderPager();
            } catch (e) { if (!stale(owner) && seq === itemSeq) failure(e); }
        }
        async function refreshRun() {
            if (!snapshot || !visible || authFailed) return;
            const owner = epoch, seq = ++runSeq, id = snapshot.run.executionId;
            try {
                const result = await request('/runs/' + id);
                if (stale(owner) || seq !== runSeq) return;
                snapshot = result; error('error', ''); render(); await loadItems();
            } catch (e) { if (!stale(owner) && seq === runSeq) failure(e); }
            finally { if (!stale(owner) && seq === runSeq) schedule(); }
        }
        async function selectRun(run) {
            if (run.sourceExecutionId !== source) throw new Error('任务不属于当前来源');
            stop(); runSeq++; itemSeq++; pages.PAPER = pages.TARGET = 0; totals.PAPER = totals.TARGET = null; renderedList = '';
            snapshot = { run, summary: {}, totalKnown: false, progressText: '正在读取持久结果…' }; render(); renderPager();
            await refreshRun();
        }
        async function resume() {
            if (!visible || source == null) { scopeText(); return; }
            const owner = epoch;
            preview = null; apollo = null; authFailed = false; scopeText();
            try {
                const p = await request('/preview?sourceExecutionId=' + source);
                if (stale(owner)) return;
                // Preview may report a globally active run; never restore another source's run.
                preview = p; scopeText();
                const pending = requests.get(source);
                const list = await request('/runs?sourceExecutionId=' + source + (pending ? '&requestKey=' + encodeURIComponent(pending.requestKey) : '') + '&page=0&size=20');
                if (stale(owner)) return;
                const run = list.items.find(r => activeStates.has(r.state)) || list.items[0];
                if (run) { if (pending) requests.delete(source); await selectRun(run); }
                else if (pending) { error('error', '上次提交结果尚未查到。重新打开确认后将使用同一请求标识，不会新建第二个请求。'); }
            } catch (e) { if (!stale(owner)) { preview = null; scopeText(); failure(e); text('scopeText', e.data?.code === 'SOURCE_RUNNING' ? '采集任务尚未结束' : '整批范围读取失败：' + e.message); } }
        }
        async function openConfirm(trigger) {
            if (!preview || submitting || authFailed) return;
            const pending = requests.get(source);
            const mode = pending?.mode === 'ENFORCE' ? 'ENFORCE' : 'AUDIT';
            root.querySelector('input[name="paperReprocessMode"][value="' + mode + '"]').checked = true;
            lockMode(Boolean(pending));
            action('start').disabled = true; error('confirmError', '');
            text('confirmExecution', '#' + source); text('confirmScope', '正在重新读取全部页范围…'); text('confirmApollo', '正在读取已保存配置…'); text('confirmHint', '正在读取范围…');
            show(confirm, trigger); const owner = epoch, seq = ++dialogSeq;
            try {
                const [p, a] = await Promise.all([request('/preview?sourceExecutionId=' + source), request('/api/expert-discovery/verification/apollo-settings', {}, true)]);
                if (stale(owner) || seq !== dialogSeq || !confirm.open) return;
                preview = p; apollo = a; scopeText();
                text('confirmScope', '全部 ' + p.recordCount + ' 篇；缺原文 ' + value(p.missingSourcePapers) + ' 篇（包含未留存、删除、保存异常）');
                text('confirmApollo', '免费搜索' + (a.searchEnabled ? '开启' : '关闭') + ' / 付费详情' + (a.detailEnabled ? '开启' : '关闭') + ' · 已保存版本 ' + value(a.revision));
                text('confirmHint', p.activeRun ? '已有活动任务，请关闭后查看记录'
                    : pending ? '上次提交结果未知；将继续使用同一请求标识与' + (pending.mode === 'ENFORCE' ? '正式补录' : '仅对照') + '模式'
                    : '范围与页面筛选 / 勾选无关；默认仅对照');
                action('start').disabled = Boolean(p.activeRun);
            } catch (e) { if (!stale(owner) && seq === dialogSeq) { failure(e, 'confirmError'); text('confirmHint', '范围或设置读取失败，不能启动'); } }
        }
        async function start() {
            if (submitting || action('start').disabled || !preview || !apollo || authFailed) return;
            const owner = epoch, id = source;
            let pending = requests.get(id);
            if (!pending) {
                pending = { requestKey: window.crypto.randomUUID(), sourceExecutionId: id, mode: root.querySelector('input[name="paperReprocessMode"]:checked').value, expectedScopeHash: preview.expectedScopeHash };
                requests.set(id, pending);
                lockMode(true);
            }
            submitting = true; action('start').disabled = true; scopeText();
            try {
                // An uncertain submission is always looked up before a same-key replay.
                const prior = await request('/runs?sourceExecutionId=' + id + '&requestKey=' + encodeURIComponent(pending.requestKey) + '&page=0&size=20');
                const run = prior.items[0] || await post('/runs', pending);
                if (stale(owner)) return;
                requests.delete(id); lockMode(false); close(confirm); await selectRun(run);
            } catch (e) {
                if (stale(owner)) return;
                const deterministic = Number.isFinite(e.status) && e.status >= 400 && e.status < 500;
                if (deterministic) {
                    // A rejected submission never became a durable request; retaining its key/hash would
                    // wedge every retry behind the same stale scope or version.
                    requests.delete(id); lockMode(false); preview = null;
                    action('start').disabled = true;
                    error('confirmError', e.data?.message || e.message || '提交未被受理');
                    text('confirmHint', '提交未被受理；正在重新读取最新范围…');
                    scopeText();
                    // A deterministic rejection is safe to retry with a new key once the current scope is read.
                    request('/preview?sourceExecutionId=' + id).then(p => {
                        if (stale(owner) || !confirm.open) return;
                        preview = p; scopeText();
                        action('start').disabled = Boolean(p.activeRun);
                        text('confirmHint', p.activeRun ? '已有活动任务，请关闭后查看记录' : '范围已更新；可重新确认');
                    }).catch(() => { if (!stale(owner) && confirm.open) text('confirmHint', '范围读取失败；请关闭后重试'); });
                } else if (e.name !== 'AbortError') {
                    failure(e, 'confirmError');
                    try {
                        const found = await request('/runs?sourceExecutionId=' + id + '&requestKey=' + encodeURIComponent(pending.requestKey) + '&page=0&size=20');
                        if (found.items[0]) { requests.delete(id); lockMode(false); close(confirm); await selectRun(found.items[0]); }
                        else text('confirmHint', '提交结果未知；重试仍使用同一请求标识及模式，请勿新建另一任务');
                    } catch (lookup) { failure(lookup, 'confirmError'); }
                }
            } finally { if (!stale(owner)) { submitting = false; action('start').disabled = authFailed || !preview || Boolean(preview.activeRun); scopeText(); } }
        }
        async function mutate(operation) {
            if (mutating || !snapshot || authFailed) return;
            const owner = epoch, current = snapshot.run; mutating = true; render();
            try { const run = await post('/runs/' + current.executionId + '/' + operation, { revision: current.revision }); if (!stale(owner)) { snapshot.run = run; render(); await refreshRun(); } }
            catch (e) { if (!stale(owner)) failure(e); }
            finally { if (!stale(owner)) { mutating = false; render(); } }
        }
        function switchTab(kind, focus = false) {
            tab = kind;
            ['PAPER', 'TARGET'].forEach(k => { const selected = kind === k, b = action(k === 'PAPER' ? 'papers' : 'targets'); b.setAttribute('aria-selected', String(selected)); b.tabIndex = selected ? 0 : -1; root.querySelector(k === 'PAPER' ? '#rpPaperResults' : '#rpTargetResults').hidden = !selected; if (selected && focus) b.focus(); });
            renderPager(); loadItems();
        }
        async function history(trigger, page = 0) {
            const owner = epoch, seq = ++dialogSeq; historyPage = page; selectedItem = null;
            text('detailSubtitle', '源执行 #' + source + ' · 历史任务');
            show(detailDialog, trigger); const host = find('detailBody'); host.replaceChildren(node('p', 'dp-empty', '正在读取历史…'));
            try {
                const result = await request('/runs?sourceExecutionId=' + source + '&page=' + page + '&size=20');
                if (stale(owner) || seq !== dialogSeq || !detailDialog.open) return;
                host.replaceChildren();
                const wrap = append(host, 'div', 'rp-log-wrap'), table = append(wrap, 'table'), head = append(append(table, 'thead'), 'tr');
                ['任务号', '源任务', '模式', '状态', '时间', '打开'].forEach(label => append(head, 'th', '', label));
                const body = append(table, 'tbody');
                result.items.forEach(run => { const row = append(body, 'tr'); [run.executionId, run.sourceExecutionId, run.mode === 'AUDIT' ? '仅对照' : '正式', states[run.state] || run.state, run.createdAt].forEach(v => append(row, 'td', '', v)); button(append(row, 'td'), '打开', async () => { close(detailDialog); await selectRun(run); }); });
                if (!result.items.length) append(host, 'p', 'dp-empty', '暂无历史任务');
                const pager = append(host, 'div', 'rp-pager'); button(pager, '上一页', () => history(trigger, page - 1), page === 0); append(pager, 'span', 'dp-muted', '第 ' + (page + 1) + ' 页 · 共 ' + result.total + ' 个任务'); button(pager, '下一页', () => history(trigger, page + 1), (page + 1) * 20 >= result.total);
            } catch (e) { if (!stale(owner) && seq === dialogSeq) { host.replaceChildren(node('p', 'dp-error', '历史读取失败：' + e.message)); failure(e); } }
        }
        function itemEvidence(item) {
            const input = item.input || {}, result = item.result || {}, ref = input.paperRefs?.[0] || result.paperRefs?.[0] || {};
            const evidence = ref.identityEvidence;
            return { ref, recordId: input.recordId || ref.recordId, attemptId: input.bodies?.[0]?.attemptId || ref.attemptIds?.[0],
                bodySha256: input.bodies?.[0]?.hash || ref.bodySha256 || ref.bodyHash || null,
                quote: typeof evidence === 'string' ? evidence : evidence?.quote || evidence?.sentence || '' };
        }
        async function showItem(id, trigger) {
            const owner = epoch, seq = ++dialogSeq, run = snapshot.run;
            show(detailDialog, trigger); text('detailSubtitle', '任务 #' + run.executionId + ' · 正在读取逐项记录…');
            find('detailBody').replaceChildren(node('p', 'dp-empty', '正在读取…'));
            try {
                const item = await request('/runs/' + run.executionId + '/items/' + id);
                if (stale(owner) || seq !== dialogSeq || !detailDialog.open) return;
                selectedItem = item;
                const host = find('detailBody'), fragment = template.content.cloneNode(true), list = fragment.querySelector('.rp-trace'), sample = list.querySelector('li'); list.replaceChildren();
                const result = item.result || {}, evidence = itemEvidence(item);
                for (let i = 1; i <= 6; i++) {
                    const step = result.trace?.find(s => s.stepNo === i), state = step?.state || 'NOT_STARTED', li = sample.cloneNode(true);
                    li.dataset.state = ({ NOT_STARTED: 'PENDING', INSUFFICIENT: 'BLOCKED', CANCELLED: 'SKIPPED' })[state] || state;
                    li.querySelector('i').textContent = i; li.querySelector('strong').textContent = steps[i - 1];
                    let extra = '';
                    if (i <= 2) extra = ' · 记录 #' + value(evidence.recordId) + ' · attempt ' + value(evidence.attemptId) + ' · 页数 ' + value(result.analysis?.coverage?.scannedPages ?? result.analysis?.coverage?.pagesScanned) + ' · 版本 ' + value(run.scope?.codeVersion) + ' / ' + JSON.stringify(run.scope?.parserVersions || {});
                    if (i === 3) extra = ' · 新增绑定 ' + value(result.analysis?.deltaAddedBindings) + '；身份不等于准入';
                    if (i === 4) extra = ' · 原档案 ' + value(result.before?.docId ?? (result.beforeAbsent ? '不存在' : null)) + ' → 当前档案 ' + value(result.docId) + ' · 新增 ' + value(result.actualNew) + ' / 补齐 ' + value(result.actualUpdated) + ' · 收据 ' + value(result.receipt?.reason || result.targetOutcome);
                    if (i === 5) extra = result.verificationItemId ? ' · 核验 ' + value(result.verificationState) + ' · 实际准入 ' + value(result.actualAdmitted) : ' · 未进入资格重审：' + (run.mode === 'AUDIT' ? '仅对照不请求资格服务' : reason(result));
                    if (i === 6) extra = ' · 同步 ' + value(result.projection) + ' · 同步失败 ' + value(result.syncFailure);
                    li.querySelector('p').textContent = (traceStates[state] || state) + extra + ' · ' + reason(result); list.appendChild(li);
                }
                fragment.querySelector('.rp-source').querySelector('p').textContent = '记录 #' + value(evidence.recordId) + ' · attempt ' + value(evidence.attemptId) + ' · 原文只读；不会自动运行分析';
                fragment.querySelector('blockquote').textContent = evidence.quote || '本项未留存可显示的原句，请查看原文与关联论文记录。';
                const original = fragment.querySelector('[data-rp-action="open-source"]'); original.disabled = !evidence.recordId || typeof openPaper !== 'function';
                original.addEventListener('click', () => {
                    const result = openPaper({ recordId: evidence.recordId, attemptId: evidence.attemptId, bodySha256: evidence.bodySha256 }, original);
                    if (result && typeof result.catch === 'function') result.catch(e => text('detailSubtitle', e?.message || '原文打开失败'));
                });
                const verification = fragment.querySelector('[data-rp-action="open-verification"]');
                verification.disabled = !result.verificationRunId || !result.verificationItemId || typeof openVerification !== 'function';
                verification.textContent = verification.disabled ? '未进入资格重审：' + (run.mode === 'AUDIT' ? '仅对照，未应用' : reason(result)) : '查看逐人核验（原8步）';
                verification.addEventListener('click', () => openVerification(result.verificationRunId, result.verificationItemId, verification));
                host.replaceChildren(fragment); text('detailSubtitle', '任务 #' + run.executionId + ' · ' + (item.kind === 'PAPER' ? '论文' : '邮箱目标') + ' #' + item.id + ' · ' + reason(result));
            } catch (e) { if (!stale(owner) && seq === dialogSeq) { find('detailBody').replaceChildren(node('p', 'dp-error', e.message)); failure(e); } }
        }
        listen(root, 'click', e => {
            const target = e.target.closest('[data-rp-action]'); if (!target || target.disabled) return;
            const name = target.dataset.rpAction;
            if (name === 'open') openConfirm(target);
            else if (name === 'close-confirm') { dialogSeq++; close(confirm); }
            else if (name === 'close-detail') { dialogSeq++; close(detailDialog); }
            else if (name === 'start') start();
            else if (name === 'cancel') mutate('cancel');
            else if (name === 'retry') mutate('retry');
            else if (name === 'history') history(target);
            else if (name === 'papers' || name === 'targets') switchTab(name === 'papers' ? 'PAPER' : 'TARGET');
            else if (name === 'previous' || name === 'next') { pages[tab] += name === 'next' ? 1 : -1; loadItems(); }
        });
        listen(root, 'keydown', e => { if (!e.target.matches('[data-rp-action="papers"],[data-rp-action="targets"]')) return; if (['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(e.key)) { e.preventDefault(); switchTab(e.key === 'Home' ? 'PAPER' : e.key === 'End' ? 'TARGET' : tab === 'PAPER' ? 'TARGET' : 'PAPER', true); } });
        [confirm, detailDialog].forEach(dialog => {
            listen(dialog, 'cancel', () => { dialogSeq++; });
            // Native Escape must close only this dialog. The task host also listens for document
            // keydown; stopping propagation here keeps its close task modal out of the new dialog.
            listen(dialog, 'keydown', e => { if (e.key === 'Escape') e.stopPropagation(); });
            listen(dialog, 'close', () => { focusTargets.get(dialog)?.focus(); });
        });
        // A disabled start button can leave real focus on BODY, so the dialog's own keydown never
        // sees the Escape. Own it at the document capture phase — before the host's document
        // handler — but only while one of these dialogs is actually open. Stopping propagation never
        // cancels the native dialog close (no preventDefault), and the listener is removed on dispose.
        listen(doc, 'keydown', e => { if (e.key === 'Escape' && (confirm.open || detailDialog.open)) e.stopPropagation(); }, true);
        function suspend() { epoch++; runSeq++; itemSeq++; dialogSeq++; stop(); abort(); submitting = mutating = false; snapshot = null; preview = null; renderedList = ''; close(confirm); close(detailDialog); render(); scopeText(); }
        function setExecution(id) {
            const next = Number.isSafeInteger(Number(id)) && Number(id) > 0 ? Number(id) : null;
            if (next === source) return;
            suspend(); source = next; preview = apollo = snapshot = null; pages.PAPER = pages.TARGET = 0; totals.PAPER = totals.TARGET = null; authFailed = false;
            error('error', ''); render(); scopeText(); if (visible) resume();
        }
        function setVisible(show) { if (disposed) return; if (!show) { visible = false; suspend(); } else if (!visible) { visible = true; resume(); } }
        scopeText();
        return { setExecution, setVisible, suspend, dispose() { if (disposed) return; visible = false; suspend(); disposed = true; listeners.forEach(remove => remove()); } };
    }
    window.DiscoveryPaperReprocess = { mount };
})();
