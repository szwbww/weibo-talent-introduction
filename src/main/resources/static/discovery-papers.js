/* Authenticated archive UI. All business state comes from the archive API. */
(function () {
    "use strict";
    const BASE = "/api/expert-discovery/papers";
    const GiB = 1073741824;
    const unknown = value => value == null ? "未知" : String(value);
    const bytes = value => value == null ? "未知" : value >= GiB ? (value / GiB).toFixed(2) + " GiB" : (value / 1048576).toFixed(2) + " MiB";
    const states = { STORED: "已留存", NOT_STORED: "未留存", SAVE_ERROR: "保存异常", DELETED: "已删除", PROCESSING: "处理中" };
    const kinds = { ASSISTANT_PROFESSOR: "助理教授", ASSOCIATE_PROFESSOR: "副教授", PROFESSOR: "教授", COMPLETED_DOCTORATE: "已取得博士学位", DOCTORAL_STUDENT: "博士在读", CONFLICT: "存在冲突" };
    function mount({ root, api, makeUrl, onStatus }) {
        const doc = root.ownerDocument;
        const find = name => root.querySelector('[data-dp="' + name + '"]');
        const action = name => root.querySelector('[data-dp-action="' + name + '"]');
        const dialogs = { reader: root.querySelector('#discoveryPaperReader'), settings: root.querySelector('#discoveryPaperSettings'), delete: root.querySelector('#discoveryPaperDelete') };
        let visible = false, pipeline = false, disposed = false, epoch = 0, page = 0, execution = null;
        let rows = [], total = 0, selected = new Set(), settings = null, settingsRevision = null, snapshot = null, detail = null, preview = null;
        let timer = null, debounce = null, busy = false, refreshing = false, readerSeq = 0, contentSeq = 0, blobUrl = null;
        let runStats = null, runLoading = false, runFailed = false;
        const controllers = new Set(), listeners = [], downloadUrls = new Set(), focusTargets = new Map(), locators = new WeakMap();
        function text(name, value) { find(name).textContent = value; }
        function error(name, value) { text(name, value || ''); find(name).hidden = !value; }
        function status(message) { if (onStatus) onStatus(message); }
        function listen(el, type, fn) { el.addEventListener(type, fn); listeners.push(() => el.removeEventListener(type, fn)); }
        function stale(token) { return disposed || token !== epoch; }
        async function request(path, options = {}) {
            const token = epoch, controller = new AbortController();
            controllers.add(controller);
            try {
                const result = await api(BASE + path, { ...options, signal: controller.signal });
                if (stale(token) || controller.signal.aborted) throw new DOMException('Stale response', 'AbortError');
                return result;
            } finally { controllers.delete(controller); }
        }
        const post = (path, body) => request(path, { method: 'POST', body: JSON.stringify(body) });
        function revoke() { if (blobUrl) URL.revokeObjectURL(blobUrl); blobUrl = null; find('pdf').removeAttribute('src'); }
        function close(name) { if (name === 'reader') { readerSeq++; contentSeq++; revoke(); detail = null; } if (name === 'delete') preview = null; if (dialogs[name].open) dialogs[name].close(); }
        function show(name, trigger) { focusTargets.set(dialogs[name], trigger || doc.activeElement); if (!dialogs[name].open) dialogs[name].showModal(); }
        function suspend() {
            epoch++; controllers.forEach(c => c.abort()); controllers.clear();
            clearTimeout(timer); clearTimeout(debounce); timer = debounce = null;
            refreshing = busy = runLoading = false;
            find('settingsForm').querySelector('[type="submit"]').disabled = false;
            Object.keys(dialogs).forEach(close);
            downloadUrls.forEach(url => URL.revokeObjectURL(url)); downloadUrls.clear();
            selection();
        }
        function counts(c) {
            return '论文 ' + unknown(c?.papers) + ' 篇 · 下载尝试 ' + unknown(c?.attempts) + ' 次 · 成功留存 ' + unknown(c?.savedAttempts) + ' 次＝新存 ' + unknown(c?.newFiles) + ' 次＋复用 ' + unknown(c?.reusedAttempts) + ' 次 · 失败尝试 ' + unknown(c?.failedAttempts) + ' 次 · 无原文论文 ' + unknown(c?.notStoredPapers) + ' 篇';
        }
        function policy() {
            const s = settings;
            find('policy').dataset.state = s?.gate || 'UNAVAILABLE';
            text('policy', s ? '留存' + (s.enabled ? '已开启' : '已关闭') + ' · 实际生效：' + (s.effectiveEnabled ? '是' : '否') + ' · 上限 ' + bytes(s.maxBytes) + ' · 最低可用 ' + bytes(s.minFreeBytes) + ' · ' + s.gate + (s.reasonCode ? ' / ' + s.reasonCode : '') : '留存设置未知，请重新进入本页重试');
        }
        function storage(failed) {
            const s = snapshot?.storage;
            text('archiveBytes', bytes(s?.archiveUsedBytes)); text('diskTotal', bytes(s?.diskTotalBytes)); text('diskAvailable', bytes(s?.diskAvailableBytes));
            text('archiveCounts', counts(snapshot?.counts) + ' · 去重文件 ' + unknown(s?.fileCount) + ' 个 · 保护论文 ' + unknown(s?.protectedPaperCount) + ' 篇');
            text('diskBreakdown', '磁盘已用 ' + bytes(s?.diskUsedBytes) + ' · 保留 ' + bytes(s?.diskReservedBytes) + ' · 原文文件 ' + bytes(s?.blobBytes) + ' · 临时文件 ' + bytes(s?.tempBytes));
            text('measuredAt', (failed ? '读取失败，以下为旧快照；' : '') + (s ? '快照时间 ' + s.measuredAt : '未知，尚无成功快照'));
            find('diskMeter').max = s?.diskTotalBytes > 0 ? s.diskTotalBytes : 1;
            find('diskMeter').value = s?.diskUsedBytes ?? 0;
        }
        function selection() {
            text('selection', '已选 ' + selected.size + ' 篇');
            ['protect', 'unprotect', 'delete'].forEach(name => { action(name).disabled = busy || refreshing || !selected.size; });
            find('selectPage').disabled = busy || refreshing || !rows.length;
            find('selectPage').checked = rows.length > 0 && rows.every(r => selected.has(r.id));
            find('selectPage').indeterminate = selected.size > 0 && !find('selectPage').checked;
            root.querySelectorAll('tr[data-dp-id]').forEach(tr => {
                const checked = selected.has(Number(tr.dataset.dpId));
                tr.classList.toggle('is-selected', checked); tr.querySelector('input').checked = checked;
            });
        }
        function node(tag, className, value) { const el = doc.createElement(tag); if (className) el.className = className; if (value != null) el.textContent = value; return el; }
        function append(parent, tag, cls, value) { const el = node(tag, cls, value); parent.appendChild(el); return el; }
        function link(parent, label, name, disabled) { const b = append(parent, 'button', 'dp-link', label); b.type = 'button'; b.dataset.dpAction = name; b.disabled = Boolean(disabled); return b; }
        function delta(a) { return !a ? '未运行解析对照' : a.baselineCoverage === 'BASELINE_MISSING' ? 'BASELINE_MISSING · 无基线，增量未知' : a.deltaAddedBindings == null ? '增量未知' : '新增关联 ' + a.deltaAddedBindings; }
        function renderRows() {
            const body = find('rows'); body.replaceChildren();
            rows.forEach(r => {
                const tr = append(body, 'tr'); tr.dataset.dpId = String(r.id);
                const check = append(append(tr, 'td'), 'input'); check.type = 'checkbox'; check.setAttribute('aria-label', '选择论文'); check.dataset.dpAction = 'select';
                const title = append(tr, 'td'); append(title, 'span', 'dp-title', r.title || r.paperKey); append(title, 'span', 'dp-sub', r.doi || 'DOI 未记录');
                append(title, 'span', 'dp-badge' + (r.state === 'STORED' ? ' dp-ok' : r.state === 'SAVE_ERROR' ? ' dp-warn' : ''), states[r.state] || r.state);
                append(title, 'span', 'dp-badge dp-protected', '已保护').hidden = !r.protected;
                const source = append(tr, 'td'); const src = append(source, 'span', '', r.sourceName); src.dataset.dp = 'source'; append(source, 'span', 'dp-sub', bytes(r.storedBytes)).dataset.dp = 'bytes';
                const run = append(tr, 'td'); append(run, 'span', '', '执行 #' + r.executionId).dataset.dp = 'executionLabel'; append(run, 'span', 'dp-sub', r.firstCapturedAt).dataset.dp = 'createdAt';
                const binding = append(tr, 'td'); append(binding, 'span', '', '邮箱 ' + unknown(r.extractionSummary?.emails) + ' / 姓名关联 ' + unknown(r.extractionSummary?.resolved)).dataset.dp = 'bindings'; append(binding, 'span', 'dp-sub', delta(r.analysisSummary)).dataset.dp = 'delta';
                append(append(tr, 'td'), 'span', '', r.analysisSummary ? '查看解析记录 · ' + unknown(r.analysisSummary.coverage?.status) : '未运行解析对照').dataset.dp = 'facts';
                const tools = append(append(tr, 'td'), 'div', 'dp-actions'); link(tools, '查看记录', 'view'); link(tools, '下载', 'download', !r.readableAttemptIds?.length);
            });
            if (!rows.length) { const td = append(append(body, 'tr'), 'td', 'dp-empty', '暂无论文原文记录'); td.colSpan = 7; }
            text('listInfo', '共 ' + total + ' 篇 · 每页 20 篇'); text('pageInfo', '第 ' + (page + 1) + ' 页');
            action('previous').disabled = refreshing || page === 0; action('next').disabled = refreshing || (page + 1) * 20 >= total;
            selection();
        }
        function params() {
            const p = new URLSearchParams({ page: String(page), size: '20' });
            [['execution', 'executionId'], ['q', 'q'], ['status', 'status'], ['protected', 'protected'], ['gain', 'hasGain']].forEach(([name, key]) => { if (find(name).value) p.set(key, find(name).value); });
            return p;
        }
        async function refresh() {
            if (!visible || disposed || refreshing) return;
            const token = epoch; refreshing = true; selection(); error('error', '');
            clearTimeout(timer);
            const p = params(), statsQuery = p.has('executionId') ? '?executionId=' + p.get('executionId') : '';
            await Promise.all([
                request('/settings').then(value => { settings = value; policy(); }).catch(e => { if (!stale(token) && e.name !== 'AbortError') { policy(); error('error', '设置读取失败：' + e.message); } }),
                request('/stats' + statsQuery).then(value => { snapshot = value; storage(false); }).catch(e => { if (!stale(token) && e.name !== 'AbortError') { storage(true); error('error', '统计读取失败：' + e.message); } }),
                request('?' + p).then(value => { rows = value.items; total = value.total; selected = new Set([...selected].filter(id => rows.some(r => r.id === id))); }).catch(e => { if (!stale(token) && e.name !== 'AbortError') { rows = []; total = 0; selected.clear(); error('error', '列表读取失败：' + e.message + '；请切换页签重试'); } })
            ]);
            if (stale(token)) return;
            refreshing = false; renderRows(); timer = setTimeout(refresh, 10000);
        }
        function filterChanged() { suspend(); selected.clear(); page = 0; rows = []; snapshot = null; storage(false); if (visible) refresh(); }
        function renderRun() {
            const host = find('runSummary'); if (!host) return;
            host.hidden = !pipeline || execution == null;
            const target = host.querySelector('[data-dp="runCounts"]');
            target.textContent = '执行 #' + execution + ' · ' + (runFailed ? '读取失败，以下为旧快照；' : '') + counts(runStats?.counts) + (runStats ? ' · 快照 ' + runStats.storage.measuredAt : ' · 尚无快照');
        }
        async function refreshRun() {
            renderRun();
            if (!pipeline || execution == null || runLoading || disposed) return;
            const token = epoch, id = execution; runLoading = true;
            try { const value = await request('/stats?executionId=' + id); if (id !== execution) return; runStats = value; runFailed = false; }
            catch (e) { if (e.name !== 'AbortError' && !stale(token)) runFailed = true; }
            finally { if (!stale(token)) { runLoading = false; renderRun(); clearTimeout(timer); timer = setTimeout(refreshRun, 10000); } }
        }
        function setExecution(id, runs = []) {
            id = Number.isSafeInteger(Number(id)) && Number(id) > 0 ? Number(id) : null;
            const changed = execution !== id;
            if (changed) { suspend(); execution = id; selected.clear(); page = 0; rows = []; snapshot = null; runStats = null; runFailed = false; find('execution').value = id == null ? '' : String(id); }
            const select = find('execution'), value = changed ? (id == null ? '' : String(id)) : select.value;
            const ids = [...new Set(runs.map(r => Number(r.executionId)).filter(n => Number.isSafeInteger(n) && n > 0).concat(id == null ? [] : [id]))];
            select.replaceChildren(node('option', '', '全部任务')); select.children[0].value = '';
            ids.forEach(n => { const o = append(select, 'option', '', '执行 #' + n); o.value = String(n); }); select.value = value;
            renderRun();
            if (changed) { storage(false); renderRows(); if (visible) refresh(); if (pipeline) refreshRun(); }
        }
        function setVisible(value, showPipeline = false) {
            if (disposed) return;
            if (visible === value && pipeline === showPipeline) { renderRun(); return; }
            suspend(); visible = Boolean(value); pipeline = Boolean(showPipeline);
            renderRun(); if (visible) refresh(); else if (pipeline) refreshRun();
        }
        async function protect(value) {
            busy = true; selection(); const token = epoch;
            try {
                const result = await post('/protection', { items: rows.filter(r => selected.has(r.id)).map(r => ({ id: r.id, revision: r.revision })), protected: value });
                selected.clear(); await refresh();
                if (!stale(token)) status('已变更 ' + result.changed + ' · 未变更 ' + result.unchanged + ' · 冲突 ' + result.conflicts.map(c => '#' + c.id + ' ' + c.reasonCode).join('、'));
            } catch (e) { if (e.name !== 'AbortError' && !stale(token)) error('error', e.message); }
            finally { if (!stale(token)) { busy = false; selection(); } }
        }
        function renderPreview(value) {
            preview = value; text('deleteFiles', unknown(value.deletableFiles)); text('deleteBytes', bytes(value.deletableBytes) + '（' + value.deletableBytes + ' B）');
            text('deleteSkipped', '保护记录 ' + value.protectedRecords.length + ' · 共享文件 ' + value.sharedFiles.length + ' · BUSY ' + value.busyFiles.length + ' · 未留存 ' + value.notStored.length + '；' + value.items.map(i => '#' + i.id + ' ' + i.reasonCodes.join('/')).join('；'));
            action('confirm-delete').disabled = value.deletableFiles <= 0;
        }
        async function deletePreview(trigger) {
            busy = true; selection(); const token = epoch;
            try { error('deleteError', ''); renderPreview(await post('/delete-preview', { ids: [...selected] })); show('delete', trigger); }
            catch (e) { if (e.name !== 'AbortError' && !stale(token)) error('error', e.message); }
            finally { if (!stale(token)) { busy = false; selection(); } }
        }
        async function deleteCommit() {
            if (!preview || busy) return;
            const captured = preview, token = epoch; busy = true; action('confirm-delete').disabled = true;
            try {
                const value = await post('/delete', { ids: captured.ids, snapshotHash: captured.snapshotHash });
                close('delete'); selected.clear(); await refresh();
                if (stale(token)) return;
                error('error', '已删除文件 ' + value.deletedFiles + '，逻辑字节 ' + value.deletedBytes + ' B；磁盘以新快照为准。跳过：' + value.skipped.map(c => '#' + c.id + ' ' + c.reasonCode).join('、') + '；失败：' + value.failed.map(c => '#' + c.id + ' ' + c.reasonCode).join('、'));
            } catch (e) {
                if (stale(token) || e.name === 'AbortError') return;
                if (e.status === 409) {
                    preview = null;
                    try { renderPreview(e.data?.preview || await post('/delete-preview', { ids: captured.ids })); error('deleteError', '原文状态已变化，已更新预检查，请重新确认；未自动重试删除。'); }
                    catch (next) { error('deleteError', next.message); }
                } else error('deleteError', e.message);
            } finally { if (!stale(token)) { busy = false; action('confirm-delete').disabled = !preview || preview.deletableFiles <= 0; } }
        }
        function openSettings(trigger) {
            if (!settings) { error('error', '设置尚未读取成功，请重新进入本页'); return; }
            find('enabled').checked = settings.enabled; find('maxGiB').value = settings.maxBytes / GiB; find('minFreeGiB').value = settings.minFreeBytes / GiB;
            settingsRevision = settings.revision;
            error('settingsError', ''); show('settings', trigger);
        }
        async function saveSettings(event) {
            event.preventDefault(); if (busy) return;
            const max = Number(find('maxGiB').value), min = Number(find('minFreeGiB').value);
            if (![max, min].every(n => Number.isInteger(n) && n >= 1 && n <= 1024)) { error('settingsError', '请输入 1–1024 的整数 GiB'); return; }
            const token = epoch; busy = true; const button = find('settingsForm').querySelector('[type="submit"]'); button.disabled = true;
            try { const result = await request('/settings', { method: 'PUT', body: JSON.stringify({ revision: settingsRevision, enabled: find('enabled').checked, maxBytes: max * GiB, minFreeBytes: min * GiB }) }); settings = result; policy(); close('settings'); await refresh(); }
            catch (e) { if (!stale(token) && e.name !== 'AbortError') error('settingsError', e.status === 409 ? '设置版本已变化，请关闭后重新进入本页载入；当前输入保留。' : '保存失败，输入已保留：' + e.message); }
            finally { if (!stale(token)) { busy = false; button.disabled = false; } }
        }
        function contentPath(id, attempt, download) { return BASE + '/' + id + '/attempts/' + attempt + '/content' + (download ? '?download=true' : ''); }
        async function content(id, attempt, download = false) {
            const controller = new AbortController(), token = epoch; controllers.add(controller);
            try {
                const response = await fetch(makeUrl(contentPath(id, attempt, download)), { credentials: 'same-origin', signal: controller.signal });
                // The host owns login redirects for JSON and binary requests alike.
                if (typeof window.handleAuthResponse === 'function') await window.handleAuthResponse(response);
                if (!response.ok) { const e = new Error(response.status === 410 ? '原文已删除，已有解析记录仍保留' : '原文读取失败：' + response.status); e.status = response.status; throw e; }
                const type = (response.headers.get('Content-Type') || '').split(';')[0].toLowerCase();
                if (!['application/pdf', 'text/plain', 'text/xml', 'application/xml', 'text/html', 'application/xhtml+xml'].includes(type)) throw new Error('不支持的原文类型：' + type);
                const blob = await response.blob(); if (stale(token) || controller.signal.aborted) throw new DOMException('Stale content', 'AbortError');
                return { blob, type };
            } finally { controllers.delete(controller); }
        }
        async function download(id, attempt) {
            const token = epoch;
            try {
                const value = await content(id, attempt, true); if (stale(token)) return;
                const url = URL.createObjectURL(value.blob); downloadUrls.add(url);
                const a = node('a'); a.href = url; a.download = 'paper-' + id + '-' + attempt + (value.type === 'application/pdf' ? '.pdf' : '.txt'); doc.body.appendChild(a); a.click(); a.remove();
                // The click has handed the blob to the browser; no application cache remains.
                URL.revokeObjectURL(url); downloadUrls.delete(url);
            } catch (e) { if (!stale(token) && e.name !== 'AbortError') error('error', e.message); }
        }
        async function loadOriginal() {
            revoke(); const seq = ++contentSeq, current = detail, attemptId = Number(find('attempts').value);
            find('pdf').hidden = find('text').hidden = true; find('text').replaceChildren(); find('originalMessage').hidden = false;
            const attempt = current?.attempts.find(a => a.id === attemptId);
            action('reader-download').disabled = action('analyze').disabled = !attempt?.readable;
            if (!attempt?.readable) { text('originalMessage', '原文不可用；已有解析记录仍保留。'); return; }
            text('originalMessage', '正在读取原文…');
            try {
                const value = await content(current.record.id, attemptId); if (seq !== contentSeq || detail !== current) return;
                if (value.type === 'application/pdf') { blobUrl = URL.createObjectURL(value.blob); find('pdf').src = blobUrl; find('pdf').hidden = false; }
                else { const raw = await value.blob.text(); if (seq !== contentSeq || detail !== current) return; text('text', raw); find('text').hidden = false; }
                find('originalMessage').hidden = true;
            } catch (e) { if (seq === contentSeq && e.name !== 'AbortError') { text('originalMessage', e.message); action('analyze').disabled = action('reader-download').disabled = true; } }
        }
        function evidenceTabs(name, focus = false) {
            ['mappings', 'qualifications'].forEach(key => { const button = action(key), selectedTab = name === key; button.classList.toggle('is-active', selectedTab); button.setAttribute('aria-selected', String(selectedTab)); button.setAttribute('tabindex', selectedTab ? '0' : '-1'); root.querySelector(key === 'mappings' ? '#dpMappings' : '#dpQualifications').hidden = !selectedTab; if (selectedTab && focus) button.focus(); });
        }
        function locate(fact) {
            if (blobUrl && Number.isSafeInteger(fact.page) && fact.page > 0) { find('pdf').src = blobUrl + '#page=' + fact.page; text('readerFoot', '已跳转 PDF 第 ' + fact.page + ' 页；无坐标，未作精准高亮。'); return; }
            const pre = find('text'), raw = pre.textContent, quote = fact.quote;
            const at = typeof quote === 'string' && quote ? raw.indexOf(quote) : -1;
            if (!pre.hidden && at >= 0) { pre.replaceChildren(doc.createTextNode(raw.slice(0, at))); const mark = append(pre, 'mark', '', quote); pre.appendChild(doc.createTextNode(raw.slice(at + quote.length))); mark.scrollIntoView({ block: 'center' }); text('readerFoot', '已定位原句；段落 ' + unknown(fact.paragraph)); }
            else text('readerFoot', '无法在当前文件定位原句；原句仍保留供查阅，请选择对应原文。');
        }
        function card(parent, fact, qualification) {
            const article = append(parent, 'article', 'dp-evidence-card'); append(article, 'h4', '', fact.authorName || '姓名未解析'); append(article, 'p', 'dp-muted', fact.email || '邮箱未记录').dataset.dp = 'personEmail';
            append(append(article, 'div', 'dp-actions'), 'span', 'dp-badge', qualification ? kinds[fact.kind] || fact.kind : '邮箱 / 姓名关联').dataset.dp = 'evidenceKind';
            append(article, 'blockquote', '', fact.quote || fact.identityEvidence || '未记录原句');
            const footer = append(article, 'footer'); append(footer, 'span', '', qualification ? (fact.paperYear == null ? '论文年份未记录' : '论文年份 ' + fact.paperYear) + ' · 页 ' + unknown(fact.page) + ' / 段 ' + unknown(fact.paragraph) : '来源：解析证据').dataset.dp = 'evidenceLocator';
            const button = link(footer, '定位原句', 'locate'); locators.set(button, { ...fact, quote: fact.quote || fact.identityEvidence });
        }
        function renderEvidence() {
            const a = detail.analysis, first = detail.outcome?.first;
            const firstInfo = first ? '首次采集 ' + unknown(first.capturedAt) + ' · 版本 ' + JSON.stringify(first.parserVersion ?? first.extractionVersion ?? detail.outcome.schemaVersion ?? null) : '首次采集基线未记录';
            text('comparisonSummary', firstInfo + '；' + (a ? '新分析 ' + unknown(a.analyzedAt) + ' · 版本 ' + unknown(a.analysisVersion) + ' / ' + JSON.stringify(a.parserVersion) + ' · ' + delta(a) + ' · 移除关联 ' + unknown(a.delta?.removedBindings) + ' · ' + a.baselineCoverage + ' · ' + a.coverage?.status : '尚未运行解析对照；首次提取结果见下方'));
            find('comparisonSummary').className = a && (a.coverage?.status === 'PARTIAL' || a.baselineCoverage === 'BASELINE_MISSING') ? 'dp-warning' : 'dp-muted';
            text('coverage', a ? '覆盖 ' + a.coverage?.status + ' · 扫描 ' + unknown(a.coverage?.scannedPages) + '/' + unknown(a.coverage?.totalPages) + ' 页 · ' + [...(a.reasons || []), ...(a.coverage?.reasons || [])].join('、') : '尚未运行解析对照；首次提取结果见下方');
            find('mappingCards').replaceChildren(); find('qualificationCards').replaceChildren();
            (a?.after || []).forEach(fact => card(find('mappingCards'), fact, false));
            (a?.qualificationFacts || []).forEach(fact => card(find('qualificationCards'), fact, true));
            if (!a?.after?.length) append(find('mappingCards'), 'p', 'dp-muted', a ? '未提取到关联' : '尚未运行解析对照；首次提取结果见下方');
            if (!a?.qualificationFacts?.length) append(find('qualificationCards'), 'p', 'dp-muted', a ? '未提取到本人资格原句，不代表无资格' : '尚未运行解析对照；首次提取结果见下方');
        }
        async function reader(id, trigger) {
            close('reader'); const seq = ++readerSeq, token = epoch;
            try {
                const value = await request('/' + id); if (seq !== readerSeq || stale(token)) return;
                detail = value; root.querySelector('#dpReaderTitle').textContent = value.record.title || value.record.paperKey;
                text('readerSubtitle', '执行 #' + value.record.executionId + ' · ' + (states[value.record.state] || value.record.state)); error('analysisError', ''); text('readerFoot', '原文预览不会自动运行解析或改变专家准入。');
                const select = find('attempts'); select.replaceChildren(); value.attempts.forEach(a => { const option = append(select, 'option', '', '#' + a.id + ' · ' + a.kind + ' · ' + a.status + ' · 记录采集 ' + value.record.firstCapturedAt + '（文件时间未提供）'); option.value = String(a.id); });
                const readable = value.attempts.find(a => a.readable); if (readable) select.value = String(readable.id);
                renderEvidence(); evidenceTabs('mappings'); show('reader', trigger); loadOriginal();
            } catch (e) { if (!stale(token) && seq === readerSeq && e.name !== 'AbortError') error('error', e.message); }
        }
        async function analyze() {
            if (!detail || busy) return;
            const current = detail, token = epoch, seq = readerSeq; busy = true; action('analyze').disabled = true; error('analysisError', '');
            try { await post('/' + current.record.id + '/analysis', { force: false }); const value = await request('/' + current.record.id); if (detail !== current || seq !== readerSeq) return; detail = value; renderEvidence(); await refresh(); }
            catch (e) { if (!stale(token) && detail === current && e.name !== 'AbortError') error('analysisError', e.status === 409 ? '原文状态已变化，请刷新后重试' : e.status === 410 ? '原文已删除，已有解析记录仍保留' : e.message); }
            finally { if (!stale(token)) { busy = false; action('analyze').disabled = !detail?.attempts.some(a => a.readable); } }
        }
        function click(event) {
            const target = event.target.closest('[data-dp-action]'); if (!target || !root.contains(target) || target.disabled) return;
            const name = target.dataset.dpAction;
            if (name === 'locate') { locate(locators.get(target)); return; }
            const tr = target.closest('tr[data-dp-id]'), row = tr && rows.find(r => r.id === Number(tr.dataset.dpId));
            if (name.startsWith('close-')) { close(name.slice(6)); return; }
            if (name === 'select' && row) { if (target.checked) selected.add(row.id); else selected.delete(row.id); selection(); }
            else if (name === 'view' && row) reader(row.id, target);
            else if (name === 'download' && row) download(row.id, row.readableAttemptIds[0]);
            else if (name === 'reader-download' && detail) download(detail.record.id, Number(find('attempts').value));
            else if (name === 'protect' || name === 'unprotect') protect(name === 'protect');
            else if (name === 'delete') deletePreview(target);
            else if (name === 'confirm-delete') deleteCommit();
            else if (name === 'settings') openSettings(target);
            else if (name === 'analyze') analyze();
            else if (name === 'mappings' || name === 'qualifications') evidenceTabs(name);
            else if (name === 'previous' || name === 'next') { const next = page + (name === 'previous' ? -1 : 1); suspend(); page = next; selected.clear(); refresh(); }
            else if (name === 'open-run-papers') { find('execution').value = String(execution); filterChanged(); root.querySelector('#discoveryPapersTab').click(); }
        }
        listen(root, 'click', click);
        listen(find('selectPage'), 'change', () => { selected = find('selectPage').checked ? new Set(rows.map(r => r.id)) : new Set(); selection(); });
        ['execution', 'status', 'protected', 'gain'].forEach(name => listen(find(name), 'change', filterChanged));
        listen(find('q'), 'input', () => { suspend(); selected.clear(); selection(); debounce = setTimeout(() => { debounce = null; filterChanged(); }, 400); });
        listen(find('attempts'), 'change', loadOriginal); listen(find('settingsForm'), 'submit', saveSettings);
        Object.values(dialogs).forEach(dialog => {
            // Native cancel happens after keydown; stop the host's task-modal Escape handler first.
            listen(dialog, 'keydown', event => { if (event.key === 'Escape') event.stopPropagation(); });
            listen(dialog, 'cancel', event => { event.preventDefault(); event.stopPropagation(); close(Object.keys(dialogs).find(k => dialogs[k] === dialog)); });
            listen(dialog, 'close', () => { const target = focusTargets.get(dialog); focusTargets.delete(dialog); if (target?.isConnected && !disposed) target.focus(); });
        });
        ['mappings', 'qualifications'].forEach(name => listen(action(name), 'keydown', event => { if (['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) { event.preventDefault(); evidenceTabs(event.key === 'Home' ? 'mappings' : event.key === 'End' ? 'qualifications' : name === 'mappings' ? 'qualifications' : 'mappings', true); } }));
        renderRows(); storage(false);
        return { setVisible, setExecution, dispose() { if (disposed) return; disposed = true; suspend(); listeners.splice(0).forEach(remove => remove()); focusTargets.clear(); } };
    }
    window.DiscoveryPapers = Object.freeze({ mount });
})();
