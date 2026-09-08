/**
 * 共享材料组件（子计划 08 · I-1..I-4 / 样式契约 S-1）。
 *
 * 同一 contactId 只存在一个 store：数据、筛选、选择、轮询全部按联系人共享；
 * mode 只是布局差异（inline 内嵌专家页 / drawer 原生 dialog / selectionOnly
 * 供 AI 选件复用），三个 host 渲染同一份页面状态，绝不产生第二套 fetch 队列。
 *
 * 契约要点：
 * - GET /api/expert-contacts/{id}/materials 只读；任何 GET 不触发 POST/文件下载。
 * - 300ms 搜索防抖 + 请求 epoch：过期响应（旧专家/旧筛选）直接丢弃，不覆盖当前 DOM。
 * - 每页 10 行；表头勾选框只选本页；selection 按 contactId 保存并跨页/跨筛选保留；
 *   POST 只发送所选 id（去重，1..500），从不隐式全选。
 * - 提交成功才显示 QUEUED；失败保留已选并显示原因；提交中/重复提交禁按钮。
 * - 活动任务可见时每 2 秒刷新当前页与 summary；不可见（unmount）即暂停；
 *   下次 mount 立即重新拉取。AbortController 只取消读请求，绝不取消已提交的
 *   POST 服务端任务。
 * - drawer 使用原生 <dialog class="em-drawer">；关闭/Esc 只释放 UI，队列继续。
 * - 只更新受影响的行单元格（状态列/文件信息行），不重建输入框与选择控件。
 *
 * 子计划 09 扩展（selectionOnly 供 AI 选件复用）：
 * - selectionOnly 视图下，analysisSupported=false 的行 checkbox 禁用并在状态列
 *   给出原因（图片行固定文案“当前不支持图片文字识别”），表头“选择本页”也只勾选
 *   可分析行；行选择本身仍是共享 store，AI 分析候选由宿主取 共享选择∩可分析。
 * - subscribe(contactId, listener)：共享 store 订阅（listener 收到 live 快照，
 *   每次同步后调用一次），供 AI 获取流程观察所选文件存储状态；绝不另造一份
 *   下载状态拷贝。
 * - requestTransfers(contactId, ids)/setSelection(contactId, ids)：
 *   程序化走共享 store 的获取提交与选择替换（同 epoch/轮询/视图同步）；
 *   requestTransfers 复用既有的提交/错误/提交后刷新语义并返回服务端响应。
 *
 * API 由宿主（app.js）经 ExpertMaterials.configure({ api, contextPath, labels })
 * 注入；URL 保留 /talent 上下文由 contextPath 提供。样式见
 * expert-materials.css（S-1 契约逐字复制）。
 */
(function (global) {
    "use strict";

    const PAGE_SIZE = 10;
    const MAX_TRANSFER_IDS = 500;
    const DEFAULT_POLL_MS = 2000;
    const DEFAULT_DEBOUNCE_MS = 300;

    const STORAGE_STATE_LABELS = {
        METADATA_ONLY: "仅文件信息",
        QUEUED: "排队中",
        DOWNLOADING: "获取中",
        STORED: "已存服务器",
        FAILED: "获取失败",
        SOURCE_UNAVAILABLE: "来源不可用"
    };
    const STORAGE_STATE_ORDER = [
        "METADATA_ONLY",
        "QUEUED",
        "DOWNLOADING",
        "STORED",
        "FAILED",
        "SOURCE_UNAVAILABLE"
    ];

    // 无样式宿主标签：失败/来源不可用两桶合计进「失败」统计 span；
    // 行内 data-state 与错误说明区分具体原因。
    function defaultFormatFileSize(size) {
        if (!size) return "0 B";
        if (size < 1024) return `${size} B`;
        if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
        return `${(size / 1024 / 1024).toFixed(1)} MB`;
    }

    const cfg = {
        api: null,
        contextPath: "",
        labels: {
            documentType: (value) => value || "",
            documentStatus: (value) => value || "",
            fileSize: defaultFormatFileSize
        },
        pollMs: DEFAULT_POLL_MS,
        debounceMs: DEFAULT_DEBOUNCE_MS
    };

    /** contactId -> Store；store 存活的唯一条件是至少一个 view 挂载中。 */
    const stores = new Map();
    let viewSeq = 0;

    // ------------------------------------------------------------------
    // 内部工具
    // ------------------------------------------------------------------

    function el(tag, cls, attrs) {
        const node = document.createElement(tag);
        if (cls) {
            String(cls).split(/\s+/).filter(Boolean).forEach((name) => node.classList.add(name));
        }
        if (attrs) {
            Object.keys(attrs).forEach((key) => {
                if (key === "dataset") {
                    Object.keys(attrs.dataset).forEach((dataKey) => {
                        node.dataset[dataKey] = attrs.dataset[dataKey];
                    });
                } else if (attrs[key] !== null && attrs[key] !== undefined) {
                    node.setAttribute(key, String(attrs[key]));
                }
            });
        }
        return node;
    }

    function text(node, value) {
        node.textContent = value == null ? "" : String(value);
    }

    function fmtDateTime(value) {
        if (!value) return "";
        const s = String(value).replace("T", " ");
        return s.length > 16 ? s.slice(0, 16) : s;
    }

    function splitSourceKey(key) {
        const idx = key.indexOf(":");
        if (idx <= 0 || idx === key.length - 1) return null;
        return { type: key.slice(0, idx), id: key.slice(idx + 1) };
    }

    function makeError(message) {
        const error = new Error(message || "请求失败");
        return error;
    }

    function isAbortError(error) {
        return !!(error && (error.name === "AbortError" || error.code === 20));
    }

    function byId(store, attachmentId) {
        return store.items.get(Number(attachmentId)) || null;
    }

    function storageStateOf(item) {
        return (item && item.storageState) || "";
    }

    function isActiveState(state) {
        return state === "QUEUED" || state === "DOWNLOADING";
    }

    // ------------------------------------------------------------------
    // store
    // ------------------------------------------------------------------

    function createStore(contactId) {
        const store = {
            contactId,
            items: new Map(),       // attachmentId -> 合并后的服务器行（含页面无关数据）
            pageOrder: [],          // 当前页 attachmentId 顺序
            page: 0,
            total: 0,               // 当前筛选下 total（服务器返回）
            summary: null,          // 全专家 summary（不受筛选影响）
            q: "",
            sourceKey: "",          // "type:id" 或 ""
            state: "",              // 存储状态筛选或 ""
            seenSources: new Map(), // "type:id" -> { type, id, subject }
            selection: new Set(),   // 已选 attachmentId（跨页/跨筛选保留）
            seq: 0,                 // 请求 epoch
            inFlight: null,         // AbortController（只取消读请求）
            loading: false,
            loadedOnce: false,
            loadError: null,
            actionError: null,
            submitting: false,
            views: new Set(),
            listeners: new Set(),
            pollTimer: null,
            pollScheduled: false,
            searchTimer: null
        };
        return store;
    }

    function dropStore(store) {
        if (store.inFlight) {
            try { store.inFlight.abort(); } catch (e) { /* noop */ }
            store.inFlight = null;
        }
        clearPollTimer(store);
        if (store.searchTimer) {
            clearTimeout(store.searchTimer);
            store.searchTimer = null;
        }
        stores.delete(store.contactId);
    }

    // ------------------------------------------------------------------
    // 视图生命周期
    // ------------------------------------------------------------------

    function pruneDeadViews() {
        stores.forEach((store) => {
            const dead = [...store.views].filter((view) => !view.disposed && view.host && !view.host.isConnected);
            dead.forEach((view) => disposeView(view));
        });
    }

    function disposeView(view) {
        if (view.disposed) return;
        view.disposed = true;
        view.handlers.forEach(([target, type, fn]) => {
            try { target.removeEventListener(type, fn); } catch (e) { /* noop */ }
        });
        view.handlers = [];
        const store = stores.get(view.contactId);
        if (store && store.views) {
            store.views.delete(view);
            if (store.views.size === 0) {
                dropStore(store);
            }
        }
    }

    function viewErrorTarget(view) {
        return view;
    }

    // ------------------------------------------------------------------
    // 轮询
    // ------------------------------------------------------------------

    function storeHasActiveTask(store) {
        if (store.summary && store.summary.active > 0) return true;
        for (const item of store.items.values()) {
            if (isActiveState(storageStateOf(item))) return true;
        }
        return false;
    }

    function clearPollTimer(store) {
        if (store.pollTimer) {
            clearTimeout(store.pollTimer);
            store.pollTimer = null;
        }
        store.pollScheduled = false;
    }

    function schedulePoll(store) {
        clearPollTimer(store);
        if (!storeHasActiveTask(store)) return;
        if (!store.views.size) return;
        if (store.loadError) return;
        store.pollScheduled = true;
        store.pollTimer = setTimeout(() => {
            store.pollTimer = null;
            store.pollScheduled = false;
            if (!store.views.size) return;
            // 轮询刷新当前页 + summary，不触发用户态错误/加载闪烁。
            fetchPage(store, { page: store.page, silent: true, reason: "poll" });
        }, cfg.pollMs);
    }

    // ------------------------------------------------------------------
    // 请求
    // ------------------------------------------------------------------

    function materialsUrl(store, page) {
        const query = new URLSearchParams();
        query.set("page", String(page));
        query.set("size", String(PAGE_SIZE));
        const q = store.q.trim();
        if (q) query.set("q", q);
        if (store.sourceKey) {
            const pair = splitSourceKey(store.sourceKey);
            if (pair) {
                query.set("source", pair.type);
                query.set("sourceId", pair.id);
            }
        }
        if (store.state) query.set("state", store.state);
        return `/api/expert-contacts/${store.contactId}/materials?${query.toString()}`;
    }

    function mergeSources(store, items) {
        items.forEach((item) => {
            const src = item && item.source;
            if (!src || !src.type || src.id == null) return;
            const key = `${src.type}:${src.id}`;
            if (!store.seenSources.has(key)) {
                store.seenSources.set(key, { type: src.type, id: src.id, subject: src.subject || "" });
            }
        });
    }

    function applyPage(store, data, page) {
        const items = Array.isArray(data && data.items) ? data.items : [];
        const byIdMap = new Map();
        const order = [];
        items.forEach((item) => {
            if (!item || item.attachmentId == null) return;
            const id = Number(item.attachmentId);
            const existing = store.items.get(id);
            // 服务器状态按 attachmentId 合并；本地没有客户端写回状态，服务器为准。
            store.items.set(id, item);
            byIdMap.set(id, item);
            order.push(id);
            if (existing && existing.storageState !== item.storageState) {
                // 状态变化时确保选择集不丢（选择与状态无关）。
            }
        });
        store.pageOrder = order;
        store.page = page;
        store.total = Number.isFinite(Number(data && data.total)) ? Number(data.total) : items.length;
        store.summary = (data && data.summary) || store.summary;
        store.loadedOnce = true;
        store.loadError = null;
        store.actionError = null;
        mergeSources(store, items);
    }

    async function fetchPage(store, opts) {
        const options = opts || {};
        const page = options.page != null ? options.page : store.page;
        if (store.inFlight) {
            try { store.inFlight.abort(); } catch (e) { /* noop */ }
        }
        store.seq += 1;
        const requestSeq = store.seq;
        const controller = new AbortController();
        store.inFlight = controller;
        if (!options.silent) {
            store.loading = true;
        }
        store.views.forEach((view) => syncView(view, "loading"));
        let data = null;
        let error = null;
        try {
            data = await cfg.api(materialsUrl(store, page), { signal: controller.signal });
            if (requestSeq !== store.seq) return; // 过期响应丢弃
            applyPage(store, data, page);
            store.loading = false;
            store.views.forEach((view) => {
                syncView(view, options.reason === "poll" ? "poll" : "page");
            });
            schedulePoll(store);
        } catch (err) {
            if (isAbortError(err) || requestSeq !== store.seq) return;
            error = err;
            store.loading = false;
            if (!options.silent || !store.loadedOnce) {
                store.loadError = error;
                store.views.forEach((view) => syncView(view, "error"));
            }
        } finally {
            if (store.inFlight === controller) {
                store.inFlight = null;
            }
        }
        return data;
    }

    // ------------------------------------------------------------------
    // 提交（POST /transfers）—— 从不取消服务端任务
    // ------------------------------------------------------------------

    function selectedIds(store) {
        return [...store.selection]
            .map((id) => Number(id))
            .filter((id) => Number.isFinite(id) && id > 0);
    }

    function applyTransferResponse(store, payload, response) {
        const items = Array.isArray(response && response.items) ? response.items : [];
        items.forEach((entry) => {
            if (!entry || entry.attachmentId == null) return;
            const id = Number(entry.attachmentId);
            const item = store.items.get(id);
            if (!item) return;
            const next = Object.assign({}, item);
            if (entry.state) next.storageState = entry.state;
            if (entry.transferId != null) next.transferId = entry.transferId;
            store.items.set(id, next);
        });
        const accepted = Number(response && response.acceptedCount) || 0;
        const alreadyReady = Number(response && response.alreadyReadyCount) || 0;
        let note = "";
        if (accepted > 0 || alreadyReady > 0) {
            note = `已提交获取 ${accepted} 份，其中 ${alreadyReady} 份已就绪。`;
        } else if (payload.length > 0) {
            note = "所选文件均无需重新获取，请查看各行状态。";
        }
        return note;
    }

    async function submitTransfers(store, view, attachmentIds) {
        const ids = attachmentIds && attachmentIds.length
            ? attachmentIds.map(Number).filter((id) => Number.isFinite(id) && id > 0)
            : selectedIds(store);
        const distinct = [...new Set(ids)];
        if (distinct.length === 0) return { ok: true, response: null };
        if (distinct.length > MAX_TRANSFER_IDS) {
            const limitMessage = `一次最多获取 ${MAX_TRANSFER_IDS} 份，当前已选 ${distinct.length} 份，请分批获取。`;
            store.actionError = limitMessage;
            store.views.forEach((v) => syncView(v, "action-error"));
            return { ok: false, error: limitMessage };
        }
        if (store.submitting) return { ok: false, error: "正在提交获取请求，请稍候" };
        store.submitting = true;
        store.actionError = null;
        store.views.forEach((v) => syncView(v, "submitting"));
        try {
            const response = await cfg.api(`/api/expert-contacts/${store.contactId}/materials/transfers`, {
                method: "POST",
                body: JSON.stringify({ attachmentIds: distinct })
            });
            if (!stores.has(store.contactId)) return { ok: true, response }; // 已关闭/切走：服务端任务继续，本客户端不再写 DOM
            store.note = applyTransferResponse(store, distinct, response) || store.note || "";
            store.views.forEach((v) => syncView(v, "submitted"));
            // 提交成功后再确认 QUEUED 并取得最新 summary；失败保留选择与原因。
            fetchPage(store, { page: store.page, silent: true, reason: "after-submit" });
            return { ok: true, response };
        } catch (err) {
            if (!stores.has(store.contactId)) return { ok: false, error: (err && err.message) ? err.message : "提交失败，请重试" };
            const message = (err && err.message) ? err.message : "提交失败，请重试";
            store.actionError = message;
            store.views.forEach((v) => syncView(v, "action-error"));
            return { ok: false, error: message };
        } finally {
            if (stores.has(store.contactId)) {
                store.submitting = false;
                store.views.forEach((v) => syncView(v, "submit-idle"));
            }
        }
    }

    /**
     * 09：程序化获取请求（AI 选件“获取所选文件并分析”）。走与 footer 完全相同的
     * 提交路径（共享 store、错误区、提交后刷新），并返回服务端响应供宿主核对。
     */
    async function requestTransfers(contactId, attachmentIds) {
        const store = stores.get(Number(contactId));
        if (!store) return { ok: false, error: "材料列表尚未加载，请稍后重试" };
        return submitTransfers(store, null, attachmentIds);
    }

    // ------------------------------------------------------------------
    // 渲染骨架（每个 view 一次；轮询/翻页不重建骨架）
    // ------------------------------------------------------------------

    function buildPanel(store, view) {
        const section = el("section", "expert-materials", { "aria-label": "专家上传资料", "aria-busy": "true" });
        const elements = { section };

        const header = el("header");
        const title = el("h3");
        text(title, "专家上传资料");
        const count = el("span");
        text(count, "");
        title.appendChild(count);
        header.appendChild(title);
        if (view.mode === "drawer") {
            const closeBtn = el("button", "button", { type: "button", "data-action": "close" });
            text(closeBtn, "关闭");
            header.appendChild(closeBtn);
        } else if (view.mode === "inline") {
            // 内嵌于专家详情：header 右侧保留既有「AI 智能分析」入口；
            // 动作仍由 app.js 在 #contactDetail 的既有委托处理（09 桥接改造同入口）。
            const aiBtn = el("button", "button small primary", {
                type: "button",
                "data-action": "open-ai-analysis",
                "data-contact-id": String(store.contactId)
            });
            text(aiBtn, "AI 智能分析");
            header.appendChild(aiBtn);
        }
        section.appendChild(header);

        const policy = el("p", "em-policy");
        text(policy, "检查回复只登记附件信息。查看清单不会获取文件；点击“获取到服务器”后开始下载。");
        section.appendChild(policy);

        const stats = el("div", "em-stats", { "aria-label": "全部资料统计" });
        const statSpans = [];
        const statDefs = [
            ["已存服务器", "stored"],
            ["待获取", "metadataOnly"],
            ["获取中", "active"],
            ["失败", "failedTotal"]
        ];
        statDefs.forEach(([label, key]) => {
            const span = el("span");
            text(span, label);
            const strong = el("strong");
            text(strong, "0");
            span.appendChild(strong);
            stats.appendChild(span);
            statSpans.push({ key, strong });
        });
        section.appendChild(stats);

        const filters = el("div", "em-filters");
        const search = el("input", null, { type: "search", "aria-label": "搜索文件名", placeholder: "搜索文件名" });
        const sourceSelect = el("select", null, { "aria-label": "来源来信" });
        const stateSelect = el("select", null, { "aria-label": "存储状态" });
        rebuildSourceOptions(store, sourceSelect);
        rebuildStateOptions(stateSelect, store.state);
        filters.appendChild(search);
        filters.appendChild(sourceSelect);
        filters.appendChild(stateSelect);
        section.appendChild(filters);

        const tableHead = el("div", "em-table-head");
        const pageCheckbox = el("input", null, { type: "checkbox", "aria-label": "选择本页" });
        pageCheckbox.checked = false;
        const col1 = el("span");
        text(col1, "文件 / 类型 / 来源");
        const col2 = el("span");
        text(col2, "存储状态 / 操作");
        tableHead.appendChild(pageCheckbox);
        tableHead.appendChild(col1);
        tableHead.appendChild(col2);
        section.appendChild(tableHead);

        const rows = el("div", "em-rows", { "aria-label": "材料列表" });
        section.appendChild(rows);

        const empty = el("p", "em-empty", { hidden: "" });
        text(empty, "正在加载资料…");
        section.appendChild(empty);

        const errorBox = el("div", "em-error", { role: "alert", hidden: "" });
        const errorText = el("span");
        errorBox.appendChild(errorText);
        section.appendChild(errorBox);

        const pager = el("div", "em-pager");
        const pagerInfo = el("span");
        text(pagerInfo, "");
        const prevBtn = el("button", "button", { type: "button", disabled: "" });
        text(prevBtn, "上一页");
        const nextBtn = el("button", "button", { type: "button", disabled: "" });
        text(nextBtn, "下一页");
        pager.appendChild(pagerInfo);
        pager.appendChild(prevBtn);
        pager.appendChild(nextBtn);
        section.appendChild(pager);

        let footer = null;
        let selectionBox = null;
        let selectionCount = null;
        let selectionHint = null;
        let clearBtn = null;
        let transferBtn = null;
        if (view.mode !== "selectionOnly") {
            footer = el("footer");
            selectionBox = el("div", "em-selection");
            const selStrong = el("strong");
            selectionCount = selStrong;
            text(selectionCount, "已选 0 份");
            selectionHint = el("small");
            text(selectionHint, "筛选与翻页保留已选文件");
            selectionBox.appendChild(selStrong);
            selectionBox.appendChild(selectionHint);
            clearBtn = el("button", "button", { type: "button", disabled: "", "data-action": "clear" });
            text(clearBtn, "清空选择");
            transferBtn = el("button", "button primary", { type: "button", disabled: "", "data-action": "transfer" });
            text(transferBtn, "获取所选到服务器");
            footer.appendChild(selectionBox);
            footer.appendChild(clearBtn);
            footer.appendChild(transferBtn);
            section.appendChild(footer);
        }

        elements.count = count;
        elements.statSpans = statSpans;
        elements.search = search;
        elements.sourceSelect = sourceSelect;
        elements.stateSelect = stateSelect;
        elements.pageCheckbox = pageCheckbox;
        elements.rows = rows;
        elements.empty = empty;
        elements.errorBox = errorBox;
        elements.errorText = errorText;
        elements.pager = pager;
        elements.pagerInfo = pagerInfo;
        elements.prevBtn = prevBtn;
        elements.nextBtn = nextBtn;
        elements.footer = footer;
        elements.selectionCount = selectionCount;
        elements.selectionHint = selectionHint;
        elements.clearBtn = clearBtn;
        elements.transferBtn = transferBtn;
        view.elements = elements;
        view.root = section;
        return section;
    }

    function rebuildStateOptions(select, currentState) {
        select.textContent = "";
        const all = el("option", null, { value: "" });
        text(all, "全部状态");
        select.appendChild(all);
        STORAGE_STATE_ORDER.forEach((state) => {
            const option = el("option", null, { value: state });
            text(option, STORAGE_STATE_LABELS[state] || state);
            select.appendChild(option);
        });
        select.value = currentState || "";
    }

    function rebuildSourceOptions(store, select) {
        const current = select.value;
        select.textContent = "";
        const all = el("option", null, { value: "" });
        text(all, "全部来信");
        select.appendChild(all);
        store.seenSources.forEach((source) => {
            const option = el("option", null, { value: `${source.type}:${source.id}` });
            text(option, source.subject || "来信");
            select.appendChild(option);
        });
        if (current) select.value = current;
        else select.value = store.sourceKey;
    }

    // ------------------------------------------------------------------
    // 行渲染
    // ------------------------------------------------------------------

    function fileInfoLabel(store, item) {
        const type = cfg.labels.documentType(item.documentType) || item.documentType || "";
        const status = cfg.labels.documentStatus(item.documentStatus) || item.documentStatus || "";
        const size = sizeLabel(item);
        return [type, status, size].filter(Boolean).join(" · ");
    }

    function sizeLabel(item) {
        if (item.actualSize != null && item.actualSize !== "") {
            return `${cfg.labels.fileSize(Number(item.actualSize))}（实际）`;
        }
        if (item.encodedSize != null && item.encodedSize !== "") {
            return `邮箱估算 ${cfg.labels.fileSize(Number(item.encodedSize))}`;
        }
        return "大小未知";
    }

    function sourceLabel(item) {
        const src = item && item.source;
        if (!src) return "来源待核对";
        const pieces = [];
        if (src.subject) pieces.push(src.subject);
        const time = fmtDateTime(src.receivedAt);
        if (time) pieces.push(time);
        return pieces.length ? pieces.join(" · ") : "来源待核对";
    }

    function errorReason(item) {
        if (item.error && item.error.message) return item.error.message;
        if (item.error && item.error.code) return item.error.code;
        return "";
    }

    /** selectionOnly（AI 选件）：分析能力来自服务端 analysisSupported，不能按扩展名伪装。 */
    function rowSelectableForAnalysis(view, item) {
        return view.mode !== "selectionOnly" || item.analysisSupported !== false;
    }

    /** 不可分析行的可见原因：图片给 OCR 说明，其它格式给支持范围说明。 */
    function analysisBlockReason(item) {
        const contentType = (item && item.contentType) || "";
        const fileName = ((item && item.fileName) || "").toLowerCase();
        const isImage = contentType.indexOf("image/") === 0 ||
            /\.(jpe?g|png|gif|webp|bmp|tiff?|heic)$/.test(fileName);
        return isImage ? "当前不支持图片文字识别" : "仅支持 PDF/文本格式分析";
    }

    function buildStateCell(store, view, item) {
        const cell = el("div", "em-state", { "data-state": item.storageState });
        const state = item.storageState;
        const labelSpan = el("span");
        text(labelSpan, STORAGE_STATE_LABELS[state] || state);
        cell.appendChild(labelSpan);
        if (state === "QUEUED" || state === "DOWNLOADING") {
            const progress = el("progress");
            const max = Number(item.encodedSize);
            const downloaded = Number(item.bytesDownloaded) || 0;
            if (state === "DOWNLOADING" && Number.isFinite(max) && max > 0) {
                progress.setAttribute("max", String(max));
                progress.setAttribute("value", String(Math.min(downloaded, max)));
            }
            cell.appendChild(progress);
            if (state === "DOWNLOADING" && (!Number.isFinite(max) || max <= 0) && downloaded > 0) {
                const bytes = el("small");
                text(bytes, `已获取 ${cfg.labels.fileSize(downloaded)}`);
                cell.appendChild(bytes);
            }
        } else if (state === "METADATA_ONLY") {
            if (item.canFetch) {
                const action = el("button", "em-link", { type: "button", "data-action": "fetch", "data-attachment-id": String(item.attachmentId) });
                text(action, "获取到服务器");
                cell.appendChild(action);
            }
        } else if (state === "STORED") {
            if (item.canDownload && item.downloadUrl) {
                const download = el("a", "em-link", { href: `${cfg.contextPath}${item.downloadUrl}`, download: "" });
                text(download, "下载到电脑");
                cell.appendChild(download);
            }
            if (item.canPreview && item.previewUrl) {
                const preview = el("a", "em-link", {
                    href: `${cfg.contextPath}${item.previewUrl}`,
                    target: "_blank",
                    rel: "noopener"
                });
                text(preview, "预览");
                cell.appendChild(preview);
            }
        } else if (state === "FAILED") {
            if (item.canFetch) {
                const action = el("button", "em-link", { type: "button", "data-action": "fetch", "data-attachment-id": String(item.attachmentId) });
                text(action, "重试");
                cell.appendChild(action);
            }
        }
        // SOURCE_UNAVAILABLE：不提供盲拉按钮，只说明原因。
        const reason = errorReason(item);
        if (reason && (state === "FAILED" || state === "SOURCE_UNAVAILABLE")) {
            const detail = el("small");
            text(detail, reason);
            cell.appendChild(detail);
        }
        // selectionOnly（AI 选件）：不可分析行必须可见解释（S-2：图片禁选说明）。
        if (view.mode === "selectionOnly" && item.analysisSupported === false) {
            const block = el("small");
            text(block, analysisBlockReason(item));
            cell.appendChild(block);
        }
        return cell;
    }

    function buildRow(store, view, item) {
        const id = Number(item.attachmentId);
        const fileName = item.fileName || "?";
        const selectable = rowSelectableForAnalysis(view, item);
        const selected = selectable && store.selection.has(id);
        const row = el("div", "em-row", {
            "data-selected": selected ? "true" : "false",
            "data-attachment-id": String(id)
        });
        const checkbox = el("input", null, { type: "checkbox", "aria-label": `选择 ${fileName}` });
        checkbox.checked = selected;
        if (!selectable) checkbox.disabled = true;
        row.appendChild(checkbox);

        const file = el("div", "em-file");
        const nameEl = el("strong", null, { title: fileName });
        text(nameEl, fileName);
        const meta1 = el("small");
        text(meta1, fileInfoLabel(store, item));
        const meta2 = el("small");
        text(meta2, sourceLabel(item));
        file.appendChild(nameEl);
        file.appendChild(meta1);
        file.appendChild(meta2);
        row.appendChild(file);

        row.appendChild(buildStateCell(store, view, item));
        return row;
    }

    function itemSnapshot(item) {
        return JSON.stringify([
            item && item.storageState,
            item && item.bytesDownloaded,
            item && item.actualSize,
            item && item.encodedSize,
            item && item.fileName,
            item && item.documentType,
            item && item.documentStatus,
            item && item.canFetch,
            item && item.canDownload,
            item && item.canPreview,
            item && item.downloadUrl,
            item && item.previewUrl,
            item && item.source && item.source.subject,
            item && item.source && item.source.receivedAt,
            item && item.error && item.error.code,
            item && item.error && item.error.message
        ]);
    }

    /** 页面内容一致时只局部更新行（状态列/文件信息），绝不重建输入框与选择控件。 */
    function updateRowInPlace(row, store, view, item) {
        const current = itemSnapshot(item);
        const prior = row.dataset.emSnapshot || "";
        if (current === prior) return;
        row.dataset.emSnapshot = current;
        // 文件信息第一行（类型/审核/大小）
        const file = row.querySelector ? row.querySelector(".em-file") : null;
        if (file) {
            const smalls = file.querySelectorAll ? file.querySelectorAll("small") : [];
            if (smalls && smalls.length > 0) text(smalls[0], fileInfoLabel(store, item));
        }
        // 状态列整体替换（该列不含输入框；复选框在列外，保持不动）
        const oldState = row.querySelector ? row.querySelector(".em-state") : null;
        if (oldState && oldState.parentNode) {
            const fresh = buildStateCell(store, view, item);
            oldState.parentNode.replaceChild(fresh, oldState);
        }
        syncRowSelection(row, store, view, item);
    }

    function syncRowSelection(row, store, view, item) {
        const id = Number(row.dataset.attachmentId);
        const current = item || byId(store, id);
        const selectable = view ? rowSelectableForAnalysis(view, current) : true;
        const selected = selectable && store.selection.has(id);
        row.dataset.selected = selected ? "true" : "false";
        const checkbox = row.querySelector ? row.querySelector("input[type=checkbox]") : null;
        if (checkbox) {
            checkbox.checked = selected;
            if (!selectable) checkbox.disabled = true;
        }
    }

    function renderRows(store, view) {
        const rowsEl = view.elements.rows;
        const oldRows = rowsEl.children ? Array.from(rowsEl.children) : [];
        const existing = new Map();
        oldRows.forEach((rowEl) => {
            if (rowEl.dataset && rowEl.dataset.attachmentId != null) {
                existing.set(Number(rowEl.dataset.attachmentId), rowEl);
            }
        });
        const order = store.pageOrder.slice();
        const wantedIds = new Set(order);
        // 1) 移除已不在本页的行
        oldRows.forEach((rowEl) => {
            const id = Number(rowEl.dataset && rowEl.dataset.attachmentId);
            if (!wantedIds.has(id) && rowEl.parentNode) {
                rowEl.parentNode.removeChild(rowEl);
            }
        });
        // 2) 追加缺失行 / 原位更新
        order.forEach((id) => {
            const item = byId(store, id);
            if (!item) return;
            const rowEl = existing.get(id);
            if (rowEl && rowEl.isConnected) {
                updateRowInPlace(rowEl, store, view, item);
            } else {
                const fresh = buildRow(store, view, item);
                fresh.dataset.emSnapshot = itemSnapshot(item);
                rowsEl.appendChild(fresh);
            }
        });
        // 3) 勾选状态每次与 selection store 对齐（第二 host / 第二视图同步）
        (rowsEl.children ? Array.from(rowsEl.children) : []).forEach((rowEl) => {
            if (rowEl.dataset && rowEl.dataset.attachmentId != null) {
                syncRowSelection(rowEl, store, view);
            }
        });
    }

    // ------------------------------------------------------------------
    // 视图同步 + 共享 store 订阅（子计划 09：AI 获取状态消费）
    // ------------------------------------------------------------------

    /** 只读快照（live 引用）：宿主经 subscribe/getState 观察，不另造状态拷贝。 */
    function snapshotOf(store) {
        return {
            contactId: store.contactId,
            items: store.items,
            summary: store.summary,
            selection: store.selection,
            submitting: store.submitting,
            actionError: store.actionError,
            loading: store.loading,
            loadedOnce: store.loadedOnce,
            total: store.total,
            page: store.page
        };
    }

    function notifySubscribers(store, reason) {
        if (!store || store.listeners.size === 0) return;
        const snapshot = snapshotOf(store);
        store.listeners.forEach((listener) => {
            try {
                listener(snapshot, reason || "");
            } catch (err) {
                // 订阅者异常绝不阻断 store 自身的视图同步/轮询。
            }
        });
    }

    function syncView(view, reason) {
        syncViewImpl(view, reason);
        const store = stores.get(view.contactId);
        notifySubscribers(store, reason);
    }

    function syncViewImpl(view, reason) {
        if (view.disposed || !view.elements) return;
        const store = stores.get(view.contactId);
        if (!store) return;
        const elements = view.elements;
        const loading = store.loading && !store.loadedOnce;
        elements.section.setAttribute("aria-busy", loading ? "true" : "false");

        if (store.loadError) {
            // 加载失败：清空列表，显示 em-error + 重试
            elements.rows.textContent = "";
            elements.empty.hidden = true;
            elements.errorBox.hidden = false;
            elements.errorText.textContent = "";
            elements.errorText.appendChild(document.createTextNode("加载失败，请重试。"));
            const reload = el("button", "em-link", { type: "button", "data-action": "reload" });
            text(reload, "重试");
            elements.errorText.appendChild(reload);
            renderStatsAndChrome(store, view);
            return;
        }
        if (store.actionError) {
            // 提交失败：保留当前列表与已选，显示原因
            renderRows(store, view);
            elements.empty.hidden = true;
            elements.errorBox.hidden = false;
            elements.errorText.textContent = "";
            elements.errorText.appendChild(document.createTextNode(store.actionError));
            renderStatsAndChrome(store, view);
            return;
        }

        elements.errorBox.hidden = true;
        elements.errorText.textContent = "";
        renderRows(store, view);
        const rowEls = elements.rows.children ? Array.from(elements.rows.children) : [];
        const hasRows = rowEls.length > 0;
        if (!hasRows) {
            if (store.loading) {
                elements.empty.hidden = false;
                text(elements.empty, "正在加载资料…");
            } else if (store.loadedOnce && store.total === 0 && store.summary && store.summary.total > 0) {
                elements.empty.hidden = false;
                text(elements.empty, "没有符合条件的资料。");
            } else if (store.loadedOnce) {
                elements.empty.hidden = false;
                text(elements.empty, "暂无资料文件。");
            } else {
                elements.empty.hidden = false;
                text(elements.empty, "正在加载资料…");
            }
        } else {
            elements.empty.hidden = true;
        }
        renderStatsAndChrome(store, view);
    }

    /** 骨架级信息更新：header 计数 / stats / pager / 表头勾选 / footer。 */
    function renderStatsAndChrome(store, view) {
        if (view.disposed || !view.elements) return;
        const elements = view.elements;

        // header 计数（全专家 summary.total）
        text(elements.count, store.summary ? `${store.summary.total} 份` : "");
        // 来信选项随新发现的来源来信累积（选择值不丢失）
        if (elements.sourceSelect && elements.sourceSelect._seenCount !== store.seenSources.size) {
            rebuildSourceOptions(store, elements.sourceSelect);
            elements.sourceSelect._seenCount = store.seenSources.size;
        }
        // stats（全专家 summary；不受筛选影响）
        elements.statSpans.forEach(({ key, strong }) => {
            if (!store.summary) {
                text(strong, "0");
                return;
            }
            let value = 0;
            if (key === "failedTotal") {
                value = Number(store.summary.failed) + Number(store.summary.sourceUnavailable);
            } else {
                value = Number(store.summary[key]) || 0;
            }
            text(strong, String(value));
        });

        // pager
        const totalPages = Math.max(1, Math.ceil(store.total / PAGE_SIZE));
        const currentPage = Math.min(store.page, totalPages - 1);
        text(elements.pagerInfo, `共 ${store.total} 份 · ${PAGE_SIZE} 条/页 · 第 ${currentPage + 1}/${totalPages} 页`);
        elements.prevBtn.disabled = store.page <= 0 || store.submitting;
        elements.nextBtn.disabled = store.page >= totalPages - 1 || store.submitting;

        // 表头勾选框 = 只选本页（selectionOnly：只统计/操作可分析行）
        const pageIds = store.pageOrder.filter((id) => {
            if (view.mode !== "selectionOnly") return true;
            const item = byId(store, id);
            return !item || item.analysisSupported !== false;
        });
        const selectedOnPage = pageIds.filter((id) => store.selection.has(id)).length;
        if (pageIds.length === 0) {
            elements.pageCheckbox.checked = false;
            elements.pageCheckbox.indeterminate = false;
            elements.pageCheckbox.disabled = true;
        } else {
            elements.pageCheckbox.disabled = false;
            elements.pageCheckbox.checked = selectedOnPage === pageIds.length;
            elements.pageCheckbox.indeterminate = selectedOnPage > 0 && selectedOnPage < pageIds.length;
        }

        // footer（selectionOnly 无 footer）
        if (elements.selectionCount && elements.transferBtn && elements.clearBtn) {
            const count = store.selection.size;
            text(elements.selectionCount, `已选 ${count} 份`);
            if (elements.selectionHint && store.note && !store.submitting) {
                text(elements.selectionHint, store.note);
            } else if (elements.selectionHint && store.note) {
                text(elements.selectionHint, "正在提交…");
            } else if (elements.selectionHint) {
                text(elements.selectionHint, store.submitting ? "正在提交…" : "筛选与翻页保留已选文件");
            }
            elements.clearBtn.disabled = count === 0 || store.submitting;
            // 超过单次上限时不禁用主按钮：点击后在错误区明确提示分批获取，
            // 绝不静默截断或隐式分批发送。
            elements.transferBtn.disabled = count === 0 || store.submitting;
            text(elements.transferBtn, store.submitting ? "正在提交…" : "获取所选到服务器");
        }
    }

    // ------------------------------------------------------------------
    // 事件
    // ------------------------------------------------------------------

    function on(elTarget, type, fn, view) {
        elTarget.addEventListener(type, fn);
        view.handlers.push([elTarget, type, fn]);
    }

    function bindEvents(store, view) {
        const section = view.root;
        const elements = view.elements;

        // 点击委托：只在 [data-action] 上动作；未识别动作（如 open-ai-analysis）
        // 原样冒泡给 app.js 的既有 #contactDetail 委托，本组件不拦截。
        const onClick = (event) => {
            const target = event.target;
            if (!target || !target.closest) return;
            const actionEl = target.closest("[data-action]");
            if (!actionEl) return;
            const action = actionEl.getAttribute("data-action");
            if (action === "close") {
                if (view.dialog && typeof view.dialog.close === "function") {
                    view.dialog.close();
                } else {
                    view.unmount();
                }
                return;
            }
            if (action === "reload") {
                store.loadError = null;
                fetchPage(store, { page: 0, reason: "reload" });
                return;
            }
            if (action === "clear") {
                store.selection.clear();
                store.note = "";
                store.views.forEach((v) => syncView(v, "selection"));
                return;
            }
            if (action === "transfer") {
                submitTransfers(store, view);
                return;
            }
            if (action === "fetch") {
                const id = Number(actionEl.getAttribute("data-attachment-id"));
                if (!Number.isFinite(id) || store.submitting) return;
                submitTransfers(store, view, [id]);
                return;
            }
            // 其它 data-action（如 open-ai-analysis）交由上层处理，不拦截。
        };
        on(section, "click", onClick, view);

        // change 委托：表头勾选（本页）与行勾选、筛选下拉
        const onChange = (event) => {
            const target = event.target;
            if (!target) return;
            if (target.type === "checkbox" && target.closest && target.closest(".em-row")) {
                const rowEl = target.closest(".em-row");
                const id = Number(rowEl && rowEl.dataset && rowEl.dataset.attachmentId);
                if (!Number.isFinite(id)) return;
                if (target.checked) {
                    store.selection.add(id);
                } else {
                    store.selection.delete(id);
                }
                store.note = "";
                store.actionError = null;
                store.views.forEach((v) => syncView(v, "selection"));
                return;
            }
            if (target === elements.pageCheckbox) {
                const pageIds = store.pageOrder.filter((id) => {
                    if (view.mode !== "selectionOnly") return true;
                    const item = byId(store, id);
                    return !item || item.analysisSupported !== false;
                });
                if (target.checked) {
                    pageIds.forEach((id) => store.selection.add(id));
                } else {
                    pageIds.forEach((id) => store.selection.delete(id));
                }
                store.note = "";
                store.actionError = null;
                store.views.forEach((v) => syncView(v, "selection"));
                return;
            }
            if (target === elements.stateSelect) {
                store.state = target.value;
                store.page = 0;
                fetchPage(store, { page: 0, reason: "state" });
                return;
            }
            if (target === elements.sourceSelect) {
                store.sourceKey = target.value;
                store.page = 0;
                fetchPage(store, { page: 0, reason: "source" });
            }
        };
        on(section, "change", onChange, view);

        // 搜索：300ms 防抖后整页重查
        const onSearchInput = () => {
            if (store.searchTimer) {
                clearTimeout(store.searchTimer);
                store.searchTimer = null;
            }
            store.searchTimer = setTimeout(() => {
                store.searchTimer = null;
                const next = elements.search.value;
                if (next === store.q) return;
                store.q = next;
                store.page = 0;
                fetchPage(store, { page: 0, reason: "search" });
            }, cfg.debounceMs);
        };
        on(elements.search, "input", onSearchInput, view);

        const onPrev = () => {
            if (store.page > 0 && !store.submitting) {
                fetchPage(store, { page: store.page - 1, reason: "prev" });
            }
        };
        const onNext = () => {
            const totalPages = Math.max(1, Math.ceil(store.total / PAGE_SIZE));
            if (store.page < totalPages - 1 && !store.submitting) {
                fetchPage(store, { page: store.page + 1, reason: "next" });
            }
        };
        on(elements.prevBtn, "click", onPrev, view);
        on(elements.nextBtn, "click", onNext, view);

        if (view.dialog) {
            const onDialogClose = () => {
                // Esc/关闭按钮只是释放 UI；已提交的服务端任务继续。
                view.unmount();
            };
            on(view.dialog, "close", onDialogClose, view);
        }
    }

    function mountView(store, view, host) {
        view.host = host;
        let rootNode;
        if (view.mode === "drawer") {
            const dialog = el("dialog", "em-drawer", { "aria-label": "专家资料管理" });
            view.dialog = dialog;
            rootNode = dialog;
            host.appendChild(dialog);
        } else {
            rootNode = host;
        }
        const section = buildPanel(store, view);
        rootNode.appendChild(section);
        view.root = section;
        bindEvents(store, view);

        if (store.loadedOnce) {
            renderRows(store, view);
            syncView(view, "mount");
            // 已有数据的新 host：静默刷新一次，保证与服务器一致。
            fetchPage(store, { page: store.page, silent: true, reason: "mount-refresh" });
        } else {
            syncView(view, "mount");
            if (!store.inFlight) {
                fetchPage(store, { page: 0, reason: "mount" });
            }
        }
        if (view.dialog && typeof view.dialog.showModal === "function") {
            view.dialog.showModal();
        }
        return view;
    }

    function createView(store, opts) {
        viewSeq += 1;
        const view = {
            id: viewSeq,
            contactId: store.contactId,
            host: null,
            mode: opts.mode || "inline",
            disposed: false,
            handlers: [],
            elements: null,
            root: null,
            dialog: null,
            unmount() {
                if (view.disposed) return;
                if (view.root && view.root.parentNode) {
                    view.root.parentNode.removeChild(view.root);
                }
                if (view.dialog && view.dialog.parentNode) {
                    view.dialog.parentNode.removeChild(view.dialog);
                }
                disposeView(view);
            }
        };
        return view;
    }

    // ------------------------------------------------------------------
    // 公共 API
    // ------------------------------------------------------------------

    function configure(options) {
        const opts = options || {};
        if (typeof opts.api === "function") cfg.api = opts.api;
        if (typeof opts.contextPath === "string") cfg.contextPath = opts.contextPath;
        if (opts.labels && typeof opts.labels === "object") {
            if (typeof opts.labels.documentType === "function") cfg.labels.documentType = opts.labels.documentType;
            if (typeof opts.labels.documentStatus === "function") cfg.labels.documentStatus = opts.labels.documentStatus;
            if (typeof opts.labels.fileSize === "function") cfg.labels.fileSize = opts.labels.fileSize;
        }
        if (Number.isFinite(Number(opts.pollMs)) && Number(opts.pollMs) > 0) {
            cfg.pollMs = Number(opts.pollMs);
        }
        if (Number.isFinite(Number(opts.debounceMs)) && Number(opts.debounceMs) > 0) {
            cfg.debounceMs = Number(opts.debounceMs);
        }
    }

    function normalizeMode(mode) {
        if (mode === "drawer" || mode === "selectionOnly") return mode;
        return "inline";
    }

    function mount(opts) {
        const options = opts || {};
        const host = options.host;
        const contactId = Number(options.contactId);
        if (!host || !Number.isFinite(contactId) || contactId <= 0) {
            throw new Error("ExpertMaterials.mount requires a real positive contactId and a host element");
        }
        if (!cfg.api) {
            throw new Error("ExpertMaterials is not configured: call ExpertMaterials.configure({ api, contextPath }) first");
        }
        pruneDeadViews();
        let store = stores.get(contactId);
        if (!store) {
            store = createStore(contactId);
            stores.set(contactId, store);
        }
        const view = createView(store, { mode: normalizeMode(options.mode) });
        store.views.add(view);
        mountView(store, view, host);
        return view;
    }

    function unmount(opts) {
        const options = opts || {};
        const host = options.host;
        if (!host) return false;
        let removed = false;
        stores.forEach((store) => {
            const targets = [...store.views].filter((view) => !view.disposed && view.host === host);
            targets.forEach((view) => {
                removed = true;
                view.unmount();
            });
        });
        return removed;
    }

    /** 释放某容器内部（或其自身）的全部视图：专家切换/详情面板重写前调用。 */
    function unmountHostsIn(rootEl) {
        if (!rootEl) return 0;
        let count = 0;
        stores.forEach((store) => {
            const targets = [...store.views].filter((view) => {
                if (view.disposed || !view.host) return false;
                return view.host === rootEl || (typeof rootEl.contains === "function" && rootEl.contains(view.host));
            });
            targets.forEach((view) => {
                count += 1;
                view.unmount();
            });
        });
        return count;
    }

    /**
     * 09：订阅某联系人的共享 store（AI 获取状态消费）。listener(snapshot, reason)
     * 在每次视图同步后调用一次；snapshot 为 live 只读引用（items/summary/selection），
     * 订阅者绝不应另存下载状态副本。返回退订函数；store 不存在（无挂载视图）返回 null。
     */
    function subscribe(contactId, listener) {
        const store = stores.get(Number(contactId));
        if (!store || typeof listener !== "function") return null;
        store.listeners.add(listener);
        listener(snapshotOf(store), "subscribe");
        return () => {
            store.listeners.delete(listener);
        };
    }

    /** 09：读取当前共享 store 快照（无则 null）；只读用途。 */
    function getState(contactId) {
        const store = stores.get(Number(contactId));
        return store ? snapshotOf(store) : null;
    }

    /** 09：整体替换某联系人的选择集（AI 默认勾选/带入）；同步全部视图，不发起任何请求。 */
    function setSelection(contactId, ids) {
        const store = stores.get(Number(contactId));
        if (!store) return false;
        const next = new Set();
        (Array.isArray(ids) ? ids : []).forEach((id) => {
            const n = Number(id);
            if (Number.isFinite(n) && n > 0) next.add(n);
        });
        store.selection = next;
        store.note = "";
        store.actionError = null;
        store.views.forEach((v) => syncView(v, "selection"));
        return true;
    }

    const api = {
        configure,
        mount,
        unmount,
        unmountHostsIn,
        subscribe,
        getState,
        setSelection,
        requestTransfers,
        version: "1"
    };

    if (global) {
        global.ExpertMaterials = api;
    }
})(typeof window !== "undefined" ? window : (typeof globalThis !== "undefined" ? globalThis : this));
