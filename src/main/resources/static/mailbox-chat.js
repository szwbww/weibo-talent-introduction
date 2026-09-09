/**
 * 收发件箱专家聊天（mailbox-refinement 02 · I-1..I-8 / 样式契约 S-1..S-7）。
 *
 * 布局：S-1 两栏（专家列表 + 会话）；聊天挂载时 view-mailbox 加 mc-refined，
 * #mailboxRefreshBtn 移到 panel-head-actions 首位，退出还原；唯一筛选节点
 * （mailboxFilter* 七字段 + #mailboxSearchBtn）在聊天时迁入 ⋯ popover（S-2），
 * 字段修改只是草稿，应用/Enter 才生效，重置/清除保留 tab 与 q；退出聊天还原父节点。
 *
 * 关键约束（与宿主/既有模块的关系）：
 * - 全部专家/消息请求走 conversations API；列表排序唯一权威在服务端（01），
 *   UI 不 sort、不发 waitingReply；三 tab = 全部(无参)/关注(followed)/待处理(pendingOnly)。
 * - 来信/邮件标签只属于 INBOUND_PROCESSING：timeline.tags 直读、POST 回包直显、
 *   删除按 tagId；发件无标签入口。标签 modal 经宿主 adapter（app.js
 *   mcHostOpenInboundTagModal）打开旧 #inboundAddTagModal，成功回调服务器回包 tags。
 * - 翻译：点击才 POST /api/translate，输出 escapeText，缓存按 source:id+正文，失败重试。
 * - 管理 overlay（S-3）：普通 div portal 到 document.body 的
 *   .mail-chat.mc-overlay-root（生产 .panel 带 backdrop-filter，fixed 不能嵌套其内）；
 *   状态/层级取消零请求，保存只发变化的两端点并逐项回读；标签即时保存（共享
 *   fetchExpertTagsFromEs/mutateExpertTag/renderMailboxExpertTagEditor）。
 * - 位置缓存（I-5）：模块 Map（sessionUser/contactId/accountScope），≤10 会话 LRU，
 *   每会话 ≤500 条已加载消息；不写 localStorage 正文。scroll/selectExpert/unmount/
 *   loadOlder/quiet-refresh 保存；恢复锚点或 fallback scrollTop（0 有效）；无缓存首访
 *   定位最新一封信顶部 ~8px；quiet refresh 合并按 source:id、服务端状态胜。
 * - 人工回复（S-5）：workbench 默认折叠（LIVE_INBOUND 宿主不变）、manual 默认展开；
 *   草稿随会话缓存按 targetKey 存取，新来信提示选择目标，绝不静默替换；发送仍走
 *   宿主 mcHostSendRichReply（服务端校验 + QA 审计）。
 * - 专家标签行（S-7）：只读 summary.expertTags（[] 无占位、null 标签暂不可用），
 *   名称经 expertTagLabels 显示映射、未知原值 escape；原生 title 完整文本；卡片不
 *   显示待处理 badge；加删专家标签后静默刷新列表/头部。
 * - 本文件只声明 S-1..S-7 及既有全局 class；无 inline style；不改
 *   trust-reply-workbench.js/expert-materials 内部。
 */
(function (global) {
    "use strict";

    if (global.MailboxChat) return;

    const PAGE_SIZE = 20;
    const MESSAGE_LIMIT = 50;
    const MESSAGE_CACHE_LIMIT = 500;
    const SESSION_CACHE_LIMIT = 10;
    const SEARCH_DEBOUNCE_MS = 300;
    const SCROLL_SAVE_DEBOUNCE_MS = 180;
    const VERSION = "2";

    const CHIP_ALL = "all";
    const CHIP_FOLLOWED = "followed";
    const CHIP_PENDING = "pending";

    const FILTER_CHIPS = [
        { key: CHIP_ALL, label: "全部" },
        { key: CHIP_FOLLOWED, label: "关注" },
        { key: CHIP_PENDING, label: "待处理" }
    ];

    const SOURCE_LABELS = {
        INBOUND_PROCESSING: "专家来信",
        MAIL_RECORD: "往来邮件"
    };

    // 高级筛选字段（与 index.html #mailboxLegacyToolbar 内唯一节点一一对应）
    const FILTER_FIELDS = [
        { id: "mailboxFilterAccountCode", key: "accountCode", label: "邮箱账号", kind: "select", wide: false },
        { id: "mailboxFilterDirection", key: "direction", label: "收发方向", kind: "select", wide: false },
        { id: "mailboxFilterTag", key: "label", label: "邮件标签", kind: "select", wide: false },
        { id: "mailboxFilterRecipient", key: "recipientEmail", label: "专家邮箱", kind: "input", wide: false, placeholder: "输入邮箱关键词" },
        { id: "mailboxFilterKeyword", key: "keyword", label: "主题 / 内容关键词", kind: "input", wide: true, placeholder: "搜索邮件主题或正文" },
        { id: "mailboxFilterStartDate", key: "startDate", label: "开始日期", kind: "date", wide: false },
        { id: "mailboxFilterEndDate", key: "endDate", label: "结束日期", kind: "date", wide: false }
    ];

    const FILTER_KEYS = FILTER_FIELDS.map((field) => field.key);

    // 会话缓存（模块级）：key = `${sessionUser}|${accountScope}|${contactId}`
    const sessionStore = new Map();

    // ------------------------------------------------------------------
    // 宿主上下文访问（app.js 顶层全局函数；缺失时按渐进式降级）
    // ------------------------------------------------------------------

    function hostFn(name) {
        return typeof global[name] === "function" ? global[name] : null;
    }

    function hostApi() {
        return hostFn("api") || function () { return Promise.reject(new Error("api 不可用")); };
    }

    function hostShowStatus(message, type) {
        const fn = hostFn("showStatus");
        if (fn) fn(message, type || "ok");
    }

    function operatorName() {
        try {
            return (global.localStorage && global.localStorage.getItem("operatorName")) || "console";
        } catch (e) {
            return "console";
        }
    }

    function openDialog(type, options) {
        const fn = hostFn("openActionDialog");
        if (!fn) return Promise.resolve(null);
        return fn(type, options || {});
    }

    function docRoot() {
        return (typeof global.document !== "undefined" && global.document) ? global.document : null;
    }

    // ------------------------------------------------------------------
    // 文本/时间工具（与既有正文显示点同一安全语义）
    // ------------------------------------------------------------------

    function escapeText(value) {
        const text = value == null ? "" : String(value);
        if (typeof global.escapeHtml === "function") return global.escapeHtml(text);
        return String(text)
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;")
            .replace(/'/g, "&#039;");
    }

    function datePart(iso) {
        return String(iso || "").slice(0, 10);
    }

    function timePart(iso) {
        const value = String(iso || "");
        const idx = value.indexOf("T");
        return idx >= 0 ? value.slice(idx + 1, idx + 6) : "";
    }

    function chatSubjectPrefill(inboundSubject) {
        if (typeof global.buildManualReplySubject === "function") {
            return global.buildManualReplySubject(inboundSubject);
        }
        const trimmed = String(inboundSubject || "").trim();
        if (!trimmed) return "Re:";
        const prefixed = trimmed.slice(0, 3).toLowerCase() === "re:" ? trimmed : `Re: ${trimmed}`;
        return prefixed.length > 255 ? prefixed.slice(0, 255) : prefixed;
    }

    function expertTagLabel(value) {
        const text = String(value == null ? "" : value);
        // app.js 顶层 const（非 window 属性）；经典脚本同 realm 经全局词法作用域可见
        if (typeof expertTagLabels !== "undefined" && expertTagLabels && expertTagLabels[text]) {
            return expertTagLabels[text];
        }
        return text;
    }

    function displayNameForCatalog(catalog, value) {
        const list = Array.isArray(catalog) ? catalog : [];
        const pair = list.find((entry) => Array.isArray(entry) && String(entry[0]) === String(value));
        return pair ? String(pair[1] || value) : (value == null || value === "" ? "" : String(value));
    }

    function statusCatalog() {
        const catalog = global.operatorStatusOptions;
        return Array.isArray(catalog) ? catalog : [];
    }

    function levelCatalog() {
        const catalog = global.indexLevelOptions;
        return Array.isArray(catalog) ? catalog : [];
    }

    function optionsFromCatalog(catalog, selected) {
        if (typeof global.optionsFromArray === "function" && Array.isArray(catalog)) {
            return global.optionsFromArray(catalog, false, "请选择", selected || "");
        }
        const arr = Array.isArray(catalog) ? catalog : [];
        return arr.map((pair) => {
            const value = Array.isArray(pair) ? pair[0] : "";
            const label = Array.isArray(pair) ? (pair[1] || value) : String(pair || "");
            const sel = String(value) === String(selected) ? " selected" : "";
            return `<option value="${escapeText(value)}"${sel}>${escapeText(label)}</option>`;
        }).join("");
    }

    // ------------------------------------------------------------------
    // 全局实例表（host -> instance；同一 #mailboxList 重复 mount = 刷新）
    // ------------------------------------------------------------------

    const instances = new Map();

    function getInstance(host) {
        return instances.get(host) || null;
    }

    function chipParams(chip) {
        if (chip === CHIP_FOLLOWED) return { followed: true };
        if (chip === CHIP_PENDING) return { pendingOnly: true };
        return {};
    }

    function sessionUserFromOptions(options) {
        const value = options && options.sessionUser ? String(options.sessionUser) : "";
        return value || operatorName();
    }

    function filterAccountScope(filters) {
        const value = filters && filters.accountCode ? String(filters.accountCode) : "";
        return value;
    }

    // ------------------------------------------------------------------
    // 会话缓存（位置/窗口/草稿）
    // ------------------------------------------------------------------

    function conversationCacheKey(user, accountScope, contactId) {
        return `${user}|${accountScope || ""}|${contactId}`;
    }

    function touchSession(key) {
        const rec = sessionStore.get(key);
        if (!rec) return;
        sessionStore.delete(key);
        sessionStore.set(key, rec);
    }

    function getConversationRecord(user, accountScope, contactId) {
        const key = conversationCacheKey(user, accountScope, contactId);
        const rec = sessionStore.get(key);
        if (rec) {
            sessionStore.delete(key);
            sessionStore.set(key, rec);
            rec.lastUsed = Date.now();
        }
        return rec || null;
    }

    function upsertConversationRecord(user, accountScope, contactId, patch) {
        const key = conversationCacheKey(user, accountScope, contactId);
        let rec = sessionStore.get(key);
        if (!rec) {
            rec = {
                key,
                contactId,
                accountScope: accountScope || "",
                items: [],
                nextBefore: null,
                hasMore: false,
                anchorKey: null,
                anchorRelTop: 0,
                scrollTop: 0,
                scrollTopValid: false,
                drafts: new Map(),
                lastUsed: Date.now()
            };
        }
        if (patch) {
            if (patch.items) rec.items = patch.items;
            if (patch.nextBefore !== undefined) rec.nextBefore = patch.nextBefore;
            if (patch.hasMore !== undefined) rec.hasMore = patch.hasMore;
            if (patch.anchorKey !== undefined) rec.anchorKey = patch.anchorKey;
            if (patch.anchorRelTop !== undefined) rec.anchorRelTop = patch.anchorRelTop;
            if (patch.scrollTop !== undefined) rec.scrollTop = patch.scrollTop;
            if (patch.scrollTopValid !== undefined) rec.scrollTopValid = patch.scrollTopValid;
            if (patch.drafts) rec.drafts = patch.drafts;
        }
        rec.lastUsed = Date.now();
        sessionStore.delete(key);
        sessionStore.set(key, rec);
        // LRU：超出上限淘汰最旧
        if (sessionStore.size > SESSION_CACHE_LIMIT) {
            let oldestKey = null;
            let oldestTs = Infinity;
            sessionStore.forEach((entry, entryKey) => {
                if (entry.lastUsed < oldestTs) {
                    oldestTs = entry.lastUsed;
                    oldestKey = entryKey;
                }
            });
            if (oldestKey !== null && oldestKey !== key) sessionStore.delete(oldestKey);
        }
        return rec;
    }

    function dropConversationRecord(user, accountScope, contactId) {
        sessionStore.delete(conversationCacheKey(user, accountScope, contactId));
    }

    function createInstance(host, options) {
        const instance = {
            host,
            root: null,
            elements: {},
            options: options || {},
            seq: 0,
            listSeq: 0,
            msgSeq: 0,
            convEpoch: 0,
            searchTimer: null,
            saveTimer: null,
            searchText: "",
            user: sessionUserFromOptions(options),
            filters: Object.assign({}, (options && options.filters) || {}),
            chip: (options && options.filters && typeof options.filters.pendingOnly === "boolean" && options.filters.pendingOnly) ? CHIP_PENDING : CHIP_ALL,
            chipUserTouched: false,
            legacyFilterState: null,
            tagOptions: { loading: false, loaded: false, failed: false, items: [] },
            list: { page: 0, total: 0, items: [], loading: false, error: "" },
            selectedContactId: null,
            selectedSummary: null,
            focusHandledContactId: null,
            focusLocating: false,
            focusMissedContactId: null,
            conversation: {
                loading: false,
                error: "",
                items: [],
                nextBefore: null,
                hasMore: false,
                contact: null,
                accountScope: null
            },
            manual: {
                mode: "none", // "none" | "inbound" | "outboundOnly"
                targetProcessingId: null,
                targetAccountCode: "",
                targetKey: null,
                qa: null,
                busy: false
            },
            draftsRef: null,
            workbench: { instance: null, processingId: null },
            logs: { loaded: false },
            translations: new Map(),
            tagAdapter: null,
            manage: { open: false, trigger: null },
            popoverOpen: false,
            loadOlderBusy: false,
            pendingPrompt: null,
            dismissedNewInbound: null,
            disposed: false
        };

        const handlers = [];
        const portalHandlers = [];

        function listen(type, handler) {
            host.addEventListener(type, handler);
            handlers.push([type, handler]);
        }

        function listenPortal(type, handler) {
            const root = portalRoot();
            if (!root) return;
            root.addEventListener(type, handler);
            portalHandlers.push([root, type, handler]);
        }

        // --------------------------------------------------------------
        // S-1：宿主 chrome（mc-refined / 刷新按钮迁移 / 旧筛选节点迁移）
        // --------------------------------------------------------------

        function viewRoot() {
            const doc = docRoot();
            if (!doc) return null;
            if (host.closest) {
                const found = host.closest("#view-mailbox");
                if (found) return found;
            }
            if (typeof doc.getElementById === "function") {
                return doc.getElementById("view-mailbox") || null;
            }
            return null;
        }

        function legacyToolbarEl() {
            const doc = docRoot();
            if (!doc) return null;
            if (typeof doc.getElementById === "function") return doc.getElementById("mailboxLegacyToolbar") || null;
            return null;
        }

        function conversationPanelEl() {
            const doc = docRoot();
            if (!doc) return null;
            if (typeof doc.getElementById === "function") return doc.getElementById("mailboxConversationPanel") || null;
            return null;
        }

        function currentDraftsMap() {
            if (instance.draftsRef) return instance.draftsRef;
            const record = getConversationRecord(instance.user, instance.conversation.accountScope || "", Number(instance.selectedContactId || 0));
            if (record) {
                instance.draftsRef = record.drafts;
                return record.drafts;
            }
            return null;
        }

        function ensureDraftsMap() {
            let drafts = currentDraftsMap();
            if (drafts) return drafts;
            const contactId = Number(instance.selectedContactId);
            if (!Number.isFinite(contactId) || contactId <= 0) return null;
            const scope = instance.conversation.accountScope || "";
            const record = upsertConversationRecord(instance.user, scope, contactId, {});
            instance.draftsRef = record.drafts;
            return record.drafts;
        }

        function getDraft(targetKey) {
            const drafts = currentDraftsMap();
            if (!drafts || !targetKey) return null;
            return drafts.get(targetKey) || null;
        }

        function setDraft(targetKey, draft) {
            const drafts = ensureDraftsMap();
            if (!drafts || !targetKey) return;
            drafts.set(targetKey, draft);
        }

        function deleteDraft(targetKey) {
            const drafts = currentDraftsMap();
            if (drafts && targetKey) drafts.delete(targetKey);
        }

        function setRefined(on) {
            const view = viewRoot();
            if (!view || !view.classList) return;
            if (on) view.classList.add("mc-refined");
            else view.classList.remove("mc-refined");
        }

        function moveRefreshButtonIntoActions() {
            const doc = docRoot();
            if (!doc || typeof doc.getElementById !== "function") return;
            const btn = doc.getElementById("mailboxRefreshBtn");
            const toolbar = legacyToolbarEl();
            const panel = conversationPanelEl();
            if (!btn || !toolbar || !panel) return;
            if (instance.legacyFilterState && instance.legacyFilterState.refreshBtn) return;
            const actions = panel.querySelector ? panel.querySelector(".panel-head-actions") : null;
            if (!actions || !actions.insertBefore) return;
            instance.legacyFilterState = instance.legacyFilterState || {};
            instance.legacyFilterState.refreshBtn = {
                parent: btn.parentNode,
                next: btn.nextSibling
            };
            if (btn.parentNode) btn.parentNode.removeChild(btn);
            actions.insertBefore(btn, actions.firstChild);
            btn.addEventListener("click", onMovedRefreshClick);
        }

        function restoreRefreshButton() {
            const state = instance.legacyFilterState;
            if (!state || !state.refreshBtn) return;
            const btn = typeof docRoot === "function" && docRoot() && typeof docRoot().getElementById === "function"
                ? docRoot().getElementById("mailboxRefreshBtn")
                : null;
            if (btn && state.refreshBtn.parent) {
                if (btn.parentNode) btn.parentNode.removeChild(btn);
                const anchor = state.refreshBtn.next && state.refreshBtn.next.parentNode === state.refreshBtn.parent
                    ? state.refreshBtn.next
                    : null;
                if (anchor) state.refreshBtn.parent.insertBefore(btn, anchor);
                else state.refreshBtn.parent.appendChild(btn);
            }
            btn.removeEventListener("click", onMovedRefreshClick);
            state.refreshBtn = null;
        }

        function onMovedRefreshClick() {
            refreshFromHost();
        }

        // ---- S-2：唯一筛选节点的迁移（聊天时进 popover，退出还原） ----

        function captureLegacyFilterLayout() {
            const toolbar = legacyToolbarEl();
            if (!toolbar) return null;
            const doc = docRoot();
            if (!doc || typeof doc.getElementById !== "function") return null;
            const controls = [];
            let allFound = true;
            FILTER_FIELDS.forEach((field) => {
                const el = doc.getElementById(field.id);
                if (!el || !el.parentNode) {
                    allFound = false;
                    return;
                }
                controls.push({ id: field.id, element: el, parent: el.parentNode, next: el.nextSibling });
            });
            const searchBtn = doc.getElementById("mailboxSearchBtn");
            if (!searchBtn || !searchBtn.parentNode) allFound = false;
            controls.push({
                id: "mailboxSearchBtn",
                element: searchBtn,
                parent: searchBtn ? searchBtn.parentNode : null,
                next: searchBtn ? searchBtn.nextSibling : null
            });
            // 记录旧 toolbar 元素顺序（含未迁移的稳定节点），还原时按原序重排
            const elementOrder = toolbar.children ? Array.from(toolbar.children) : [];
            return allFound ? { toolbar, controls, elementOrder } : null;
        }

        function migrateLegacyFilterNodes() {
            const layout = captureLegacyFilterLayout();
            if (!layout) return false;
            instance.legacyFilterState = instance.legacyFilterState || {};
            instance.legacyFilterState.layout = layout;
            const fieldsRoot = host.querySelector ? host.querySelector("#mailboxFilterFields") : null;
            if (!fieldsRoot) return false;
            FILTER_FIELDS.forEach((field) => {
                const found = layout.controls.find((entry) => entry.id === field.id);
                if (!found) return;
                const label = docRoot().createElement ? null : null;
                const labelEl = docRoot() && typeof docRoot().createElement === "function"
                    ? docRoot().createElement("label")
                    : null;
                if (!labelEl) return;
                labelEl.setAttribute("class", field.wide ? "mc-field mc-field-wide" : "mc-field");
                const text = docRoot() && typeof docRoot().createTextNode === "function"
                    ? docRoot().createTextNode(field.label)
                    : null;
                if (text) labelEl.appendChild(text);
                if (found.element.parentNode) found.element.parentNode.removeChild(found.element);
                labelEl.appendChild(found.element);
                fieldsRoot.appendChild(labelEl);
            });
            const searchBtn = layout.controls.find((entry) => entry.id === "mailboxSearchBtn");
            if (searchBtn && searchBtn.element) {
                const footer = host.querySelector ? host.querySelector("#mcFilterPopover footer") : null;
                if (footer && searchBtn.element.parentNode) {
                    searchBtn.element.parentNode.removeChild(searchBtn.element);
                    const anchor = footer.querySelector('[data-action="mc-reset-filters"]');
                    footer.insertBefore(searchBtn.element, anchor ? anchor.nextSibling : null);
                }
                if (searchBtn.element.textContent !== "应用筛选") {
                    searchBtn.element.textContent = "应用筛选";
                }
            }
            return true;
        }

        function createFallbackFilterFields() {
            const doc = docRoot();
            if (!doc || typeof doc.createElement !== "function") return;
            const fieldsRoot = host.querySelector ? host.querySelector("#mailboxFilterFields") : null;
            if (!fieldsRoot) return;
            FILTER_FIELDS.forEach((field) => {
                const label = doc.createElement("label");
                label.setAttribute("class", field.wide ? "mc-field mc-field-wide" : "mc-field");
                label.appendChild(doc.createTextNode(field.label));
                let control;
                if (field.kind === "select") {
                    control = doc.createElement("select");
                    control.setAttribute("id", field.id);
                    control.appendChild(doc.createTextNode(""));
                    if (field.id === "mailboxFilterTag") {
                        control.innerHTML = '<option value="">全部标签</option>';
                    } else if (field.id === "mailboxFilterAccountCode") {
                        control.innerHTML = '<option value="">全部邮箱账号</option>';
                    } else if (field.id === "mailboxFilterDirection") {
                        control.innerHTML = '<option value="">全部收发方向</option><option value="INBOUND">收件 (INBOUND)</option><option value="OUTBOUND">发件 (OUTBOUND)</option>';
                    }
                } else {
                    control = doc.createElement("input");
                    control.setAttribute("id", field.id);
                    control.setAttribute("type", field.kind === "date" ? "date" : "text");
                    if (field.placeholder) control.setAttribute("placeholder", field.placeholder);
                }
                label.appendChild(control);
                fieldsRoot.appendChild(label);
            });
            const footer = host.querySelector ? host.querySelector("#mcFilterPopover footer") : null;
            if (footer) {
                const applyBtn = doc.createElement("button");
                applyBtn.setAttribute("type", "button");
                applyBtn.setAttribute("class", "button primary");
                applyBtn.setAttribute("id", "mailboxSearchBtn");
                applyBtn.appendChild(doc.createTextNode("应用筛选"));
                const resetBtn = footer.querySelector('[data-action="mc-reset-filters"]');
                if (resetBtn && resetBtn.nextSibling) footer.insertBefore(applyBtn, resetBtn.nextSibling);
                else footer.appendChild(applyBtn);
            }
        }

        function filterControl(id) {
            const doc = docRoot();
            if (doc && typeof doc.getElementById === "function") {
                const el = doc.getElementById(id);
                if (el) return el;
            }
            return host.querySelector ? host.querySelector(`#${id}`) : null;
        }

        function ensureFilterFieldsPresent() {
            const fieldsRoot = host.querySelector ? host.querySelector("#mailboxFilterFields") : null;
            if (!fieldsRoot) return;
            if (fieldsRoot.childNodes.length === 0) {
                const migrated = migrateLegacyFilterNodes();
                if (!migrated) createFallbackFilterFields();
            }
            // 账号/方向下拉的默认值（从真实节点读，不写死）
            const doc = docRoot();
            const accountSelect = filterControl("mailboxFilterAccountCode");
            if (accountSelect && accountSelect.querySelectorAll && accountSelect.querySelectorAll("option").length === 0 && doc && typeof doc.createElement === "function") {
                const option = doc.createElement("option");
                option.setAttribute("value", "");
                option.appendChild(doc.createTextNode("全部邮箱账号"));
                accountSelect.appendChild(option);
            }
        }

        function restoreLegacyFilterNodes() {
            const state = instance.legacyFilterState;
            if (!state || !state.layout) return;
            const layout = state.layout;
            layout.controls.forEach((entry) => {
                const el = entry.element;
                if (el && el.parentNode) el.parentNode.removeChild(el);
            });
            // 按原 toolbar 元素顺序整体重建（稳定节点 + 迁回的控制节点各归原位）
            const toolbar = layout.toolbar;
            if (toolbar) {
                const ordered = [];
                const controlById = new Map(layout.controls.map((entry) => [entry.id, entry.element]));
                const seen = new Set();
                (layout.elementOrder || []).forEach((node) => {
                    if (!node || !node.getAttribute) return;
                    const id = node.getAttribute("id");
                    const controlEl = controlById.get(id);
                    if (controlEl && !seen.has(controlEl)) {
                        ordered.push(controlEl);
                        seen.add(controlEl);
                    } else if (node.parentNode) {
                        ordered.push(node);
                    }
                });
                // 防御：仍在 document 中但未进入原顺序的迁移节点补到末尾
                layout.controls.forEach((entry) => {
                    const el = entry.element;
                    if (el && el.parentNode && !seen.has(el)) {
                        ordered.push(el);
                        seen.add(el);
                    }
                });
                ordered.forEach((node) => {
                    if (node.parentNode) node.parentNode.removeChild(node);
                });
                ordered.forEach((node) => toolbar.appendChild(node));
            }
            // 还原旧标签类别选项与文案
            const tagSelect = filterControl("mailboxFilterTag");
            if (tagSelect && state.tagSelectOriginalHtml != null) {
                tagSelect.innerHTML = state.tagSelectOriginalHtml;
            }
            const searchBtn = filterControl("mailboxSearchBtn");
            if (searchBtn && state.searchBtnText != null && searchBtn.textContent !== state.searchBtnText) {
                searchBtn.textContent = state.searchBtnText;
            }
            instance.legacyFilterState = null;
        }

        function rememberLegacyFilterTexts() {
            if (instance.legacyFilterState && instance.legacyFilterState.textsCaptured) return;
            const tagSelect = filterControl("mailboxFilterTag");
            const searchBtn = filterControl("mailboxSearchBtn");
            instance.legacyFilterState = instance.legacyFilterState || {};
            if (tagSelect) instance.legacyFilterState.tagSelectOriginalHtml = tagSelect.innerHTML;
            if (searchBtn) instance.legacyFilterState.searchBtnText = searchBtn.textContent;
            instance.legacyFilterState.textsCaptured = true;
        }

        // --------------------------------------------------------------
        // 渲染骨架（S-1 两栏 + S-2 搜索/筛选）
        // --------------------------------------------------------------

        function skeletonHtml() {
            const chipButtons = FILTER_CHIPS.map((chip) =>
                `<button class="mc-filter" type="button" data-action="mc-filter" data-chip="${chip.key}" aria-pressed="${instance.chip === chip.key ? "true" : "false"}">${escapeText(chip.label)}</button>`
            ).join("");
            return `
                <div class="mail-chat">
                    <aside class="mc-experts" aria-label="专家会话列表">
                        <div class="mc-list-tools">
                            <div class="mc-search-row">
                                <input type="search" aria-label="搜索专家" placeholder="搜索专家姓名、邮箱">
                                <button class="mc-icon" type="button" data-action="mc-more-filters" title="更多筛选" aria-label="更多筛选" aria-expanded="false" aria-controls="mcFilterPopover">⋯<span class="mc-filter-count" hidden></span></button>
                                <div class="mc-filter-popover" id="mcFilterPopover" role="dialog" aria-label="更多筛选" hidden>
                                    <header><strong>更多筛选</strong><button class="mc-close" type="button" data-action="mc-close-filters" aria-label="关闭筛选">×</button></header>
                                    <div class="mc-filter-fields" id="mailboxFilterFields"></div>
                                    <p class="mc-inline-error" role="alert" hidden></p>
                                    <footer><button class="mc-text-button" type="button" data-action="mc-reset-filters">重置</button><button class="button primary" type="button" id="mailboxSearchBtn">应用筛选</button></footer>
                                </div>
                            </div>
                            <div class="mc-filters">
                                ${chipButtons}
                            </div>
                            <div class="mc-filter-summary" hidden></div>
                        </div>
                        <div class="mc-expert-list" aria-live="polite"></div>
                        <div class="mc-pager"></div>
                    </aside>
                    <section class="mc-conversation" aria-label="专家往来信件"></section>
                </div>
            `;
        }

        function expertsRoot() {
            return host.querySelector ? host.querySelector(".mc-expert-list") : null;
        }

        function pagerRoot() {
            return host.querySelector ? host.querySelector(".mc-pager") : null;
        }

        function conversationBody() {
            return host.querySelector ? host.querySelector(".mc-conversation") : null;
        }

        function renderSkeleton() {
            host.innerHTML = skeletonHtml();
        }

        function bindFilterEvents() {
            listen("click", onClick);
            listen("input", onInput);
            listen("change", onChange);
            listen("keydown", onKeyDown);
        }

        function bindScrollListener() {
            const scroll = scrollEl();
            if (!scroll || scroll.__mcScrollBound) return;
            scroll.__mcScrollBound = true;
            if (typeof scroll.addEventListener === "function") {
                scroll.addEventListener("scroll", onScrollEvent);
                handlers.push(["scroll", onScrollEvent]);
            }
        }

        function portalRoot() {
            const doc = docRoot();
            if (!doc) return null;
            return instance.elements.portalRoot || null;
        }

        // --------------------------------------------------------------
        // 筛选状态（S-2 草稿语义）
        // --------------------------------------------------------------

        function committedFilterCount() {
            const filters = instance.filters || {};
            return FILTER_KEYS.filter((key) => String(filters[key] || "").trim() !== "").length;
        }

        function renderFilterChrome() {
            const count = committedFilterCount();
            const countBadge = host.querySelector ? host.querySelector(".mc-filter-count") : null;
            if (countBadge) {
                if (count > 0) {
                    countBadge.textContent = String(count);
                    countBadge.hidden = false;
                } else {
                    countBadge.textContent = "";
                    countBadge.hidden = true;
                }
            }
            const summary = host.querySelector ? host.querySelector(".mc-filter-summary") : null;
            if (summary) {
                if (count > 0) {
                    summary.hidden = false;
                    summary.innerHTML = `<span>${count} 项筛选已生效</span><button class="mc-text-button" type="button" data-action="mc-clear-filters">清除</button>`;
                } else {
                    summary.hidden = true;
                    summary.innerHTML = "";
                }
            }
        }

        function currentFieldValues() {
            const values = {};
            FILTER_FIELDS.forEach((field) => {
                const el = filterControl(field.id);
                values[field.key] = el ? String(el.value || "") : "";
            });
            return values;
        }

        function populateFieldsFromCommitted() {
            FILTER_FIELDS.forEach((field) => {
                const el = filterControl(field.id);
                if (!el) return;
                el.value = String(instance.filters[field.key] || "");
            });
        }

        function setPopoverOpen(open) {
            const doc = docRoot();
            const popover = host.querySelector ? host.querySelector("#mcFilterPopover") : null;
            const toggle = host.querySelector ? host.querySelector('[data-action="mc-more-filters"]') : null;
            if (popover) popover.hidden = !open;
            if (toggle) toggle.setAttribute("aria-expanded", open ? "true" : "false");
            instance.popoverOpen = open;
            if (open && popover) {
                ensureTagOptionsLoaded();
            }
            if (!open && toggle && typeof toggle.focus === "function" && doc && open === false) {
                // 焦点归还触发按钮（关闭路径各自处理焦点，避免与重开冲突）
            }
        }

        function showFilterError(message) {
            const popover = host.querySelector ? host.querySelector("#mcFilterPopover") : null;
            if (!popover) return;
            const error = popover.querySelector(".mc-inline-error");
            if (error) {
                error.textContent = message;
                error.hidden = false;
            }
        }

        function clearFilterError() {
            const popover = host.querySelector ? host.querySelector("#mcFilterPopover") : null;
            if (!popover) return;
            const error = popover.querySelector(".mc-inline-error");
            if (error) {
                error.textContent = "";
                error.hidden = true;
            }
        }

        function openFilterPopover() {
            clearFilterError();
            populateFieldsFromCommitted();
            setPopoverOpen(true);
        }

        function closeFilterPopover({ restore = true, focusButton = true } = {}) {
            if (restore) {
                // 未应用：恢复为已生效值
                populateFieldsFromCommitted();
            }
            clearFilterError();
            setPopoverOpen(false);
            if (focusButton) {
                const toggle = host.querySelector ? host.querySelector('[data-action="mc-more-filters"]') : null;
                if (toggle && typeof toggle.focus === "function") toggle.focus();
            }
        }

        function validateFieldDates(values) {
            const start = String(values.startDate || "").trim();
            const end = String(values.endDate || "").trim();
            if (start && end && start > end) {
                return "开始日期不能晚于结束日期";
            }
            return "";
        }

        function applyFiltersFromFields() {
            const values = currentFieldValues();
            const dateError = validateFieldDates(values);
            if (dateError) {
                showFilterError(dateError);
                return;
            }
            clearFilterError();
            const next = {};
            FILTER_KEYS.forEach((key) => {
                const value = String(values[key] || "").trim();
                if (value) next[key] = value;
            });
            instance.filters = next;
            syncTagOptionsWithCommitted();
            setPopoverOpen(false);
            renderFilterChrome();
            instance.list.page = 0;
            loadList();
        }

        function resetAdvancedFilters() {
            instance.filters = {};
            populateFieldsFromCommitted();
            clearFilterError();
            setPopoverOpen(false);
            renderFilterChrome();
            instance.list.page = 0;
            loadList();
        }

        function restoreAdvancedFilters() {
            instance.filters = {};
            populateFieldsFromCommitted();
            renderFilterChrome();
            instance.list.page = 0;
            loadList();
        }

        // ---- 标签选项（S-2：真实 label 去重） ----

        function loadTagOptions(silent) {
            if (instance.tagOptions.loading) return;
            if (instance.tagOptions.loaded) return;
            instance.tagOptions.loading = true;
            instance.tagOptions.failed = false;
            hostApi()("/api/inbound-summary/tags/options").then((data) => {
                if (instance.disposed) return;
                const items = (data && Array.isArray(data.items)) ? data.items : [];
                const seen = new Set();
                const labels = [];
                items.forEach((item) => {
                    const label = item && item.label != null ? String(item.label) : "";
                    if (label && !seen.has(label)) {
                        seen.add(label);
                        labels.push(label);
                    }
                });
                instance.tagOptions = { loading: false, loaded: true, failed: false, items: labels };
                renderTagSelectOptions();
                syncTagOptionsWithCommitted();
            }).catch(() => {
                if (instance.disposed) return;
                instance.tagOptions = { loading: false, loaded: false, failed: true, items: [] };
                const popover = host.querySelector ? host.querySelector("#mcFilterPopover") : null;
                if (popover && !popover.hidden) {
                    const error = popover.querySelector(".mc-inline-error");
                    if (error) {
                        error.hidden = false;
                        error.innerHTML = '<span>标签选项加载失败，请重试。</span><button class="mc-text-button" type="button" data-action="mc-retry-tag-options">重试</button>';
                    }
                }
            });
        }

        function ensureTagOptionsLoaded() {
            if (!instance.tagOptions.loaded && !instance.tagOptions.loading) {
                loadTagOptions(true);
            } else if (instance.tagOptions.failed && !instance.tagOptions.loading) {
                const popover = host.querySelector ? host.querySelector("#mcFilterPopover") : null;
                if (popover && !popover.hidden) {
                    const error = popover.querySelector(".mc-inline-error");
                    if (error) {
                        error.hidden = false;
                        error.innerHTML = '<span>标签选项加载失败，请重试。</span><button class="mc-text-button" type="button" data-action="mc-retry-tag-options">重试</button>';
                    }
                }
            }
        }

        function renderTagSelectOptions() {
            const select = filterControl("mailboxFilterTag");
            if (!select) return;
            const labels = instance.tagOptions.items || [];
            const current = String(select.value || "");
            select.innerHTML = '<option value="">全部标签</option>' + labels.map((label) => {
                const sel = label === current ? " selected" : "";
                return `<option value="${escapeText(label)}"${sel}>${escapeText(label)}</option>`;
            }).join("");
        }

        function syncTagOptionsWithCommitted() {
            const current = String(instance.filters.label || "");
            if (!current) return;
            const select = filterControl("mailboxFilterTag");
            if (!select) return;
            const hasOption = Array.prototype.some.call(select.querySelectorAll ? select.querySelectorAll("option") : [], (option) => String(option.value || "") === current);
            if (!hasOption) {
                delete instance.filters.label;
                renderFilterChrome();
            }
        }

        // --------------------------------------------------------------
        // 专家列表
        // --------------------------------------------------------------

        function conversationsParams(page) {
            const params = new URLSearchParams();
            params.set("page", String(page));
            params.set("size", String(PAGE_SIZE));
            const q = instance.searchText.trim();
            if (q) params.set("q", q);
            const chipValues = chipParams(instance.chip);
            if (chipValues.followed) params.set("followed", "true");
            if (chipValues.pendingOnly) params.set("pendingOnly", "true");
            const filters = instance.filters || {};
            if (filters.accountCode) params.set("accountCode", filters.accountCode);
            if (filters.direction) params.set("direction", filters.direction);
            if (filters.startDate) params.set("startDate", filters.startDate);
            if (filters.endDate) params.set("endDate", filters.endDate);
            if (filters.recipientEmail) params.set("recipientEmail", filters.recipientEmail);
            if (filters.keyword) params.set("keyword", filters.keyword);
            if (filters.label) params.set("label", filters.label);
            return params;
        }

        function personTagNames(item) {
            const tags = item && Array.isArray(item.expertTags) ? item.expertTags : null;
            if (!tags) return null;
            return tags.map((tag) => expertTagLabel(tag));
        }

        function renderPerson(item) {
            const pendingCount = Number(item.pendingCount) || 0;
            const latest = item.latestMessage || null;
            const latestLine = latest
                ? `${latest.direction === "INBOUND" ? "最近来信" : "最近发件"}：${latest.subject || "(无主题)"}`
                : "暂无往来";
            const accounts = Array.isArray(item.accountCodes) && item.accountCodes.length
                ? item.accountCodes.join("、")
                : (item.email || "-");
            const active = instance.selectedContactId != null
                && String(item.contactId) === String(instance.selectedContactId);
            const tagNames = personTagNames(item);
            const tagLine = tagNames === null
                ? `<span class="mc-person-tags-unavailable" title="标签暂不可用">标签暂不可用</span>`
                : (tagNames.length === 0 ? "" : `<span class="mc-person-tags" title="专家标签：${escapeText(tagNames.join("、"))}">${tagNames.map((name) => `<span class="mc-person-tag">${escapeText(name)}</span>`).join("")}</span>`);
            const ariaLabel = tagNames !== null && tagNames.length > 0
                ? `查看${item.name || item.email || ""}往来邮件；专家标签：${tagNames.join("、")}`
                : `查看${item.name || item.email || ""}往来邮件`;
            return `
                <div class="mc-person" data-active="${active ? "true" : "false"}" data-contact-id="${escapeText(item.contactId)}">
                    <button class="mc-person-main" type="button" data-action="mc-select-expert" data-contact-id="${escapeText(item.contactId)}" aria-label="${escapeText(ariaLabel)}"${active ? ' aria-current="true"' : ""}>
                        <span class="mc-person-heading"><strong>${escapeText(item.name || item.email || "-")}</strong></span>
                        <small>${escapeText(accounts)}</small>
                        <small>${escapeText(latestLine)}</small>
                        <span class="mc-person-meta">
                            <span class="mc-person-counts">收 ${Number(item.receivedCount) || 0} · 发 ${Number(item.sentCount) || 0}</span>
                            ${tagLine}
                        </span>
                    </button>
                    <button class="mc-follow" type="button" data-action="mc-toggle-follow" data-contact-id="${escapeText(item.contactId)}" aria-label="${item.followed ? "取消关注该专家" : "关注该专家"}" aria-pressed="${item.followed ? "true" : "false"}">${item.followed ? "★" : "☆"}</button>
                </div>
            `;
        }

        function renderExpertList() {
            const root = expertsRoot();
            if (!root) return;
            if (instance.list.error) {
                root.innerHTML = `<div class="mc-error" role="alert">加载失败，请重试。<button class="button" type="button" data-action="mc-retry-list">重试</button></div>`;
                return;
            }
            const items = instance.list.items || [];
            if (items.length === 0) {
                root.innerHTML = `<div class="mc-empty">没有符合条件的专家</div>`;
                return;
            }
            root.innerHTML = items.map(renderPerson).join("");
        }

        function renderPager() {
            const root = pagerRoot();
            if (!root) return;
            const total = Number(instance.list.total) || 0;
            const maxPage = Math.max(0, Math.ceil(total / PAGE_SIZE) - 1);
            const page = instance.list.page;
            root.innerHTML = `
                <span>第 ${page + 1}/${maxPage + 1} 页 · 共 ${total} 位</span>
                <button class="button" type="button" data-action="mc-page-prev"${page <= 0 ? " disabled" : ""}>上一页</button>
                <button class="button" type="button" data-action="mc-page-next"${page >= maxPage ? " disabled" : ""}>下一页</button>
            `;
        }

        function fetchList(extra) {
            const options = extra || {};
            const page = options.page != null ? options.page : instance.list.page;
            instance.listSeq += 1;
            const mySeq = instance.listSeq;
            instance.list.loading = true;
            instance.list.error = "";
            const url = `/api/mail/mailbox/conversations?${conversationsParams(page).toString()}`;
            return hostApi()(url).then((data) => {
                if (instance.disposed || mySeq !== instance.listSeq) return null;
                instance.list.items = (data && Array.isArray(data.items)) ? data.items : [];
                instance.list.total = Number(data && data.total) || 0;
                instance.list.page = page;
                instance.list.loading = false;
                renderExpertList();
                renderPager();
                return data;
            }).catch((err) => {
                if (instance.disposed || mySeq !== instance.listSeq) return null;
                instance.list.loading = false;
                instance.list.error = err && err.message ? err.message : "加载失败";
                renderExpertList();
                renderPager();
                hostShowStatus(`获取邮件记录失败: ${instance.list.error}`, "error");
                return null;
            });
        }

        function loadList() {
            return fetchList({ page: instance.list.page }).then((data) => {
                if (instance.disposed) return data;
                resolveFocusAndSelection();
                return data;
            });
        }

        // 空页回退最后有效页（I-1：处理最后待处理信后列表可能空页）
        function refreshListWithFallback() {
            const page = instance.list.page;
            return fetchList({ page }).then((data) => {
                if (instance.disposed) return data;
                const items = instance.list.items || [];
                const total = Number(instance.list.total) || 0;
                if (items.length === 0 && page > 0 && total > 0) {
                    return fetchList({ page: page - 1 }).then((retryData) => {
                        if (instance.disposed) return retryData;
                        resolveFocusAndSelection();
                        return retryData;
                    });
                }
                resolveFocusAndSelection();
                return data;
            });
        }

        // --------------------------------------------------------------
        // 焦点（T1-5：外部 focus 新 contactId 必须响应）
        // --------------------------------------------------------------

        function clearSelectedConversation() {
            teardownConversationSubViews();
            instance.selectedContactId = null;
            instance.selectedSummary = null;
            instance.conversation.contact = null;
            instance.draftsRef = null;
        }

        function renderFocusMissed(focus) {
            const identity = focus && focus.email ? `${focus.email}` : (focus && focus.contactId != null ? `#${focus.contactId}` : "");
            const body = conversationBody();
            if (!body) return;
            body.innerHTML = `<div class="mc-empty">未找到该专家${identity ? `（${escapeText(identity)}）` : ""}的会话记录，可能无访问权限或暂无邮件。</div>`;
        }

        function renderConversationEmpty() {
            const body = conversationBody();
            if (!body) return;
            body.innerHTML = '<div class="mc-empty">请选择左侧专家查看往来信件</div>';
        }

        function resolveFocusAndSelection() {
            const items = instance.list.items || [];
            const focus = instance.options.focus;
            if (focus && focus.contactId != null
                && String(instance.focusHandledContactId || "") !== String(focus.contactId)) {
                if (instance.selectedContactId == null
                    || String(instance.selectedContactId) !== String(focus.contactId)) {
                    const found = items.find((item) => String(item.contactId) === String(focus.contactId));
                    if (found) {
                        selectExpert(found, { skipListReload: true });
                        return;
                    }
                    locateFocusExpert(focus);
                    return;
                }
                instance.focusHandledContactId = focus.contactId;
            }
            if (instance.selectedContactId != null && instance.list.items && instance.list.items.length > 0) {
                const stillPresent = instance.list.items.some(
                    (item) => String(item.contactId) === String(instance.selectedContactId)
                );
                if (stillPresent) {
                    const freshSummary = findSummaryByContactId(instance.selectedContactId);
                    if (freshSummary) instance.selectedSummary = freshSummary;
                    refreshConversationQuiet();
                } else {
                    // 当前筛选不再包含该专家：先保存，再回到“请选择专家”空态
                    saveConversationState();
                    clearSelectedConversation();
                    renderConversationEmpty();
                }
                return;
            }
            if (instance.selectedContactId == null && items.length === 0 && !focus) {
                renderConversationEmpty();
            }
        }

        function locateFocusExpert(focus) {
            if (!focus || focus.contactId == null) return;
            if (instance.focusLocating) return;
            if (String(instance.focusMissedContactId || "") === String(focus.contactId)) {
                renderFocusMissed(focus);
                return;
            }
            if (!focus.email) {
                instance.focusMissedContactId = focus.contactId;
                renderFocusMissed(focus);
                return;
            }
            instance.focusLocating = true;
            instance.listSeq += 1;
            const mySeq = instance.listSeq;
            const params = new URLSearchParams();
            params.set("page", "0");
            params.set("size", String(PAGE_SIZE));
            params.set("q", String(focus.email).trim());
            hostApi()(`/api/mail/mailbox/conversations?${params.toString()}`).then((data) => {
                instance.focusLocating = false;
                if (instance.disposed || mySeq !== instance.listSeq) return;
                const located = (data && Array.isArray(data.items) ? data.items : []).find(
                    (item) => String(item.contactId) === String(focus.contactId)
                );
                if (located) {
                    instance.focusHandledContactId = focus.contactId;
                    selectExpert(located, { skipListReload: true });
                } else {
                    instance.focusMissedContactId = focus.contactId;
                    instance.focusHandledContactId = focus.contactId;
                    renderFocusMissed(focus);
                }
            }).catch(() => {
                instance.focusLocating = false;
                if (instance.disposed || mySeq !== instance.listSeq) return;
                instance.focusMissedContactId = focus.contactId;
                instance.focusHandledContactId = focus.contactId;
                renderFocusMissed(focus);
            });
        }

        // --------------------------------------------------------------
        // 选择专家 → 右侧会话（I-5：缓存恢复）
        // --------------------------------------------------------------

        function findSummaryByContactId(contactId) {
            const items = instance.list.items || [];
            return items.find((item) => String(item.contactId) === String(contactId)) || null;
        }

        function teardownConversationSubViews() {
            closeManageOverlay({ restoreFocus: false });
            if (instance.workbench.instance) {
                try { instance.workbench.instance.unmount(); } catch (e) { /* noop */ }
                instance.workbench.instance = null;
                instance.workbench.processingId = null;
            }
            const releaseMaterials = hostFn("unmountExpertMaterialsHosts");
            if (releaseMaterials && instance.host) releaseMaterials(instance.host);
        }

        function accountFilterFromOptions() {
            return filterAccountScope(instance.filters);
        }

        function selectExpert(item, options) {
            const opts = options || {};
            // 切换前保存旧会话
            saveConversationState();
            teardownConversationSubViews();
            instance.selectedContactId = Number(item.contactId);
            instance.selectedSummary = item;
            instance.seq += 1;
            instance.convEpoch += 1;
            const mySeq = instance.seq;
            const myEpoch = instance.convEpoch;
            instance.conversation.loading = true;
            instance.conversation.error = "";
            instance.conversation.items = [];
            instance.conversation.nextBefore = null;
            instance.conversation.hasMore = false;
            instance.conversation.contact = null;
            instance.manual = {
                mode: "none",
                targetProcessingId: null,
                targetAccountCode: "",
                targetKey: null,
                qa: null,
                busy: false
            };
            instance.logs = { loaded: false };
            instance.dismissedNewInbound = null;
            instance.pendingPrompt = null;
            instance.translations = new Map();
            instance.loadOlderBusy = false;
            instance.draftsRef = null;
            renderConversationScaffold();

            const contactId = Number(item.contactId);
            const accountFilter = accountFilterFromOptions();
            const msgParams = new URLSearchParams();
            msgParams.set("limit", String(MESSAGE_LIMIT));
            if (accountFilter) msgParams.set("accountCode", accountFilter);
            const contactPromise = hostApi()(`/api/expert-contacts/${contactId}`).catch(() => null);
            const messagesPromise = hostApi()(`/api/mail/mailbox/conversations/${contactId}/messages?${msgParams.toString()}`)
                .catch(() => null);

            Promise.all([contactPromise, messagesPromise]).then(([contact, msgData]) => {
                if (instance.disposed || mySeq !== instance.seq || myEpoch !== instance.convEpoch) return;
                instance.conversation.contact = contact && contact.contact ? contact.contact : (contact || null);
                instance.conversation.accountScope = accountFilter;
                const serverItems = (msgData && Array.isArray(msgData.items)) ? msgData.items : [];
                instance.conversation.nextBefore = (msgData && msgData.nextBefore) || null;
                instance.conversation.hasMore = !!(msgData && msgData.hasMore);
                const cached = getConversationRecord(instance.user, accountFilter, contactId);
                if (cached && cached.items && cached.items.length > 0) {
                    // 恢复缓存窗口（保留已加载历史），服务端同 key 状态胜
                    instance.conversation.items = mergeServerIntoWindow(cached.items, serverItems);
                    if (cached.nextBefore && (!instance.conversation.nextBefore || cached.items.length > serverItems.length)) {
                        instance.conversation.nextBefore = cached.nextBefore;
                    }
                    if (cached.hasMore && cached.items.length > serverItems.length) {
                        instance.conversation.hasMore = cached.hasMore;
                    }
                    instance.draftsRef = cached.drafts;
                    instance.conversation.loading = false;
                    renderConversationContent({ restoreRecord: cached });
                } else {
                    instance.conversation.items = serverItems;
                    instance.conversation.loading = false;
                    renderConversationContent({ locateLatest: true });
                }
                if (!opts.skipListReload) {
                    fetchList({ page: instance.list.page });
                }
            }).catch(() => {
                if (instance.disposed || mySeq !== instance.seq || myEpoch !== instance.convEpoch) return;
                const cached = getConversationRecord(instance.user, accountFilter, contactId);
                if (cached && cached.items && cached.items.length > 0) {
                    instance.conversation.items = cached.items;
                    instance.conversation.nextBefore = cached.nextBefore;
                    instance.conversation.hasMore = cached.hasMore;
                    instance.conversation.loading = false;
                    instance.draftsRef = cached.drafts;
                    renderConversationContent({ restoreRecord: cached });
                } else {
                    instance.conversation.loading = false;
                    instance.conversation.error = "加载失败";
                    renderConversationContent({});
                }
            });
        }

        function mergeServerIntoWindow(windowItems, serverItems) {
            const ordered = [];
            const indexByKey = new Map();
            (windowItems || []).forEach((message) => {
                ordered.push(message);
                indexByKey.set(`${message.source}:${message.id}`, ordered.length - 1);
            });
            (serverItems || []).forEach((message) => {
                const key = `${message.source}:${message.id}`;
                if (indexByKey.has(key)) {
                    ordered[indexByKey.get(key)] = message;
                } else {
                    ordered.push(message);
                    indexByKey.set(key, ordered.length - 1);
                }
            });
            return ordered;
        }

        function renderConversationScaffold() {
            const body = conversationBody();
            if (!body) return;
            const name = (instance.selectedSummary && (instance.selectedSummary.name || instance.selectedSummary.email)) || "…";
            body.innerHTML = `
                <header class="mc-header"><div class="mc-identity"><h2>${escapeText(name)}</h2><p>正在加载往来信件…</p></div><div class="mc-actions"></div></header>
                <div class="mc-timeline-head"><span>往来信件</span><span class="mc-position-hint"></span><button class="mc-text-button" type="button" data-action="mc-latest">↓ 最新消息</button></div>
                <div class="mc-scroll" tabindex="0" aria-label="往来信件滚动区"><div class="mc-empty">正在加载往来信件…</div></div>
            `;
            bindScrollListener();
        }

        function conversationSummaryInfo() {
            const summary = instance.selectedSummary || {};
            const parts = [];
            if (summary.email) parts.push(summary.email);
            const accounts = Array.isArray(summary.accountCodes) && summary.accountCodes.length
                ? summary.accountCodes.join("、")
                : "";
            if (accounts) parts.push(`账号 ${accounts}`);
            return parts.join(" · ");
        }

        function renderHeader() {
            const body = conversationBody();
            if (!body) return;
            const summary = instance.selectedSummary || {};
            const contactId = Number(instance.selectedContactId);
            const followed = summary.followed === true;
            const materialCount = Number(summary.materialCount) || 0;
            const head = body.querySelector(".mc-header");
            if (!head) return;
            const identity = head.querySelector(".mc-identity");
            if (identity) {
                identity.innerHTML = `<h2>${escapeText(summary.name || summary.email || "-")}</h2><p>${escapeText(conversationSummaryInfo() || "-")}</p>`;
            }
            const actions = head.querySelector(".mc-actions");
            if (actions) {
                actions.innerHTML = `
                    <button class="button" type="button" data-action="mc-toggle-follow" data-contact-id="${escapeText(contactId)}">${followed ? "★ 已关注" : "☆ 关注"}</button>
                    <button class="button" type="button" data-action="mc-open-materials" data-contact-id="${escapeText(contactId)}">材料 ${materialCount}</button>
                    <button class="button" type="button" data-action="mc-manage-expert">管理</button>
                `;
            }
            const meta = head.querySelector(".mc-header-meta");
            if (meta) {
                renderHeaderMeta(meta);
            }
        }

        function renderHeaderMeta(metaEl) {
            if (!metaEl) return;
            const contact = instance.conversation.contact || null;
            const summary = instance.selectedSummary || {};
            const statusText = contact ? displayNameForCatalog(statusCatalog(), contact.operatorStatus) : "";
            const levelText = contact ? displayNameForCatalog(levelCatalog(), contact.currentIndexLevel) : "";
            const statusBadge = statusText ? `<span class="mc-badge" data-tone="success">${escapeText(statusText)}</span>` : "";
            const levelBadge = levelText ? `<span class="mc-badge">${escapeText(levelText)}</span>` : "";
            const orcid = (contact && contact.orcidId) || summary.orcid || "";
            const expertTagSpans = headerExpertTagSpans();
            metaEl.innerHTML = `
                ${statusBadge}${levelBadge}${expertTagSpans}${orcid ? `<span>ORCID ${escapeText(orcid)}</span>` : ""}<button class="mc-text-button" type="button" data-action="mc-open-expert" data-contact-id="${escapeText(Number(instance.selectedContactId))}">查看专家详情 ↗</button>
            `;
        }

        function headerExpertTagSpans() {
            const contact = instance.conversation.contact || null;
            const orcidId = contact && (contact.orcidId || contact.expertOrcidId) ? String(contact.orcidId || contact.expertOrcidId) : "";
            const level = contact && contact.currentIndexLevel ? String(contact.currentIndexLevel) : "";
            const tags = instance.headerTags || null;
            if (tags === null) return "";
            if (tags.length === 0) return "";
            return tags.map((tag) => `<span class="expert-tag">${escapeText(expertTagLabel(tag))}</span>`).join("");
        }

        function refreshHeaderExpertTags() {
            const contact = instance.conversation.contact || null;
            const orcidId = contact && (contact.orcidId || contact.expertOrcidId) ? String(contact.orcidId || contact.expertOrcidId) : "";
            if (!orcidId) {
                instance.headerTags = [];
                return;
            }
            const level = contact && contact.currentIndexLevel ? String(contact.currentIndexLevel) : "CANDIDATE";
            const fetchTags = hostFn("fetchExpertTagsFromEs");
            if (!fetchTags) return;
            fetchTags(orcidId, level).then((data) => {
                if (instance.disposed) return;
                const currentContact = instance.conversation.contact || null;
                const currentOrcid = currentContact && (currentContact.orcidId || currentContact.expertOrcidId)
                    ? String(currentContact.orcidId || currentContact.expertOrcidId)
                    : "";
                if (currentOrcid !== orcidId) return;
                instance.headerTags = (data && Array.isArray(data.tags)) ? data.tags : [];
                const body = conversationBody();
                if (body) {
                    const meta = body.querySelector(".mc-header-meta");
                    if (meta) renderHeaderMeta(meta);
                }
            }).catch(() => {
                if (instance.disposed) return;
                instance.headerTags = instance.headerTags || [];
            });
        }

        function latestInboundMessage() {
            const messages = instance.conversation.items || [];
            const summary = instance.selectedSummary || {};
            const latest = summary.latestInbound || null;
            if (!latest || latest.processingId == null) return null;
            const found = messages.find((message) =>
                message.source === "INBOUND_PROCESSING" && String(message.id) === String(latest.processingId)
            );
            return found || null;
        }

        function daySeparatorsHtml() {
            const messages = instance.conversation.items || [];
            let lastDay = "";
            let html = "";
            messages.forEach((message) => {
                const day = datePart(message.eventAt);
                if (day && day !== lastDay) {
                    html += `<div class="mc-day">${escapeText(day)}</div>`;
                    lastDay = day;
                }
                html += renderMessage(message);
            });
            return html;
        }

        // ---- 邮件卡片（S-4） ----

        function messageDisplayText(message) {
            const cleaned = message.cleanedBody != null ? String(message.cleanedBody) : "";
            const raw = message.body != null ? String(message.body) : "";
            return (cleaned.trim() ? cleaned : raw).trim();
        }

        function translationState(key, bodyText) {
            const state = instance.translations.get(key);
            if (!state) {
                const next = { status: "idle", text: "", bodyText };
                instance.translations.set(key, next);
                return next;
            }
            return state;
        }

        function translateButtonLabel(state) {
            if (!state) return "翻译";
            if (state.status === "loading") return "翻译中…";
            if (state.status === "error") return "翻译失败，重试";
            if (state.status === "ok" && state.expanded) return "收起译文";
            return "翻译";
        }

        function renderMessage(message) {
            const direction = message.direction === "OUTBOUND" ? "OUTBOUND" : "INBOUND";
            const isInboundProcessing = message.source === "INBOUND_PROCESSING";
            const key = `${message.source}:${message.id}`;
            const account = message.accountCode || "";
            const who = isInboundProcessing
                ? `${SOURCE_LABELS.INBOUND_PROCESSING || "专家来信"} · ${timePart(message.eventAt)}${account ? ` · ${escapeText(account)}` : ""}`
                : `${direction === "OUTBOUND" ? "发出邮件" : "往来邮件"} · ${timePart(message.eventAt)}${account ? ` · ${escapeText(account)}` : ""}`;
            const subject = message.subject || "(无主题)";
            const displayBody = messageDisplayText(message);
            const bodyHtml = displayBody ? `<div class="mc-body">${escapeText(displayBody)}</div>` : "";
            const attachmentHtml = Number(message.attachmentCount) > 0 ? renderAttachmentSummary(message) : "";
            const statusBadge = renderStatusBadge(message, direction);
            const pending = isInboundProcessing && message.processStatus === "MANUAL_REVIEW";
            const processed = isInboundProcessing && message.processStatus === "PROCESSED";
            const tagRow = isInboundProcessing ? renderTagRow(message, key) : "";
            const translationStateFor = translationState(key, displayBody);
            const translationHtml = renderTranslationBlock(translationStateFor);
            const canTranslate = !!displayBody;
            const footerButtons = [];
            if (canTranslate) {
                footerButtons.push(`<button class="mc-text-button" type="button" data-action="mc-translate" data-message-key="${escapeText(key)}">${escapeText(translateButtonLabel(translationStateFor))}</button>`);
            }
            if (isInboundProcessing) {
                footerButtons.push(`<button class="mc-text-button" type="button" data-action="mc-add-mail-tag" data-message-key="${escapeText(key)}">＋ 添加标签</button>`);
            }
            if (pending) {
                footerButtons.push(`<button class="mc-text-button mc-process" type="button" data-action="mc-mark-resolved" data-message-key="${escapeText(key)}">✓ 标记已处理</button>`);
            } else if (processed) {
                footerButtons.push(`<span class="mc-done">✓ 已处理</span>`);
            }
            const footerHtml = footerButtons.length > 0
                ? `<footer>${footerButtons.join("")}</footer>`
                : "";
            return `
                <article class="mc-message" data-direction="${direction}" data-source="${escapeText(message.source)}" data-id="${escapeText(message.id)}" data-message-key="${escapeText(key)}">
                    <header><span>${who}</span>${statusBadge ? `<span>${statusBadge}</span>` : ""}</header>
                    <h3>${escapeText(subject)}</h3>
                    ${bodyHtml}
                    ${attachmentHtml}
                    ${translationHtml}
                    ${tagRow}
                    <p class="mc-inline-error" role="alert" hidden></p>
                    ${footerHtml}
                </article>
            `;
        }

        function renderStatusBadge(message, direction) {
            if (direction === "INBOUND") {
                if (message.processStatus === "MANUAL_REVIEW") {
                    return '<span class="mc-badge" data-tone="pending">待处理</span>';
                }
                return "";
            }
            if (message.sendStatus === "SENT") {
                return '<span class="mc-badge" data-tone="success">已发送</span>';
            }
            if (message.sendStatus === "FAILED") {
                return '<span class="mc-badge" data-tone="error">发送失败</span>';
            }
            return "";
        }

        function renderAttachmentSummary(message) {
            const count = Number(message.attachmentCount) || 0;
            const names = Array.isArray(message.firstAttachmentNames) ? message.firstAttachmentNames : [];
            const nameRows = names.slice(0, 3).map((name) =>
                `<div title="${escapeText(name)}">${escapeText(name)}</div>`
            ).join("");
            return `
                <details class="mc-mail-extras">
                    <summary>附件 ${count} 份 · 仅文件信息</summary>
                    <div class="mc-attachment-names">${nameRows}<button class="button" type="button" data-action="mc-view-attachments" data-contact-id="${escapeText(message.contactId)}">查看全部附件</button></div>
                </details>
            `;
        }

        function renderTagRow(message, key) {
            const tags = Array.isArray(message.tags) ? message.tags : [];
            if (tags.length === 0) return "";
            const chips = tags.map((tag) => {
                if (typeof global.renderInboundTagChip === "function") {
                    return global.renderInboundTagChip(tag, { removable: true, removeAction: "mc-remove-mail-tag" });
                }
                const cls = ["inbound-tag-chip"].concat(tag && tag.tagType === "QA" ? ["qa"] : ["custom"]).join(" ");
                return `<span class="${cls}">${escapeText(tag && tag.label ? tag.label : "")}<button type="button" class="chip-x" data-action="mc-remove-mail-tag" data-tag-id="${escapeText(tag.tagId)}" title="删除标签">×</button></span>`;
            }).join("");
            return `<div class="mc-tag-row" data-role="mail-tags" data-message-key="${escapeText(key)}">${chips}</div>`;
        }

        function renderTranslationBlock(state) {
            if (!state || state.status === "idle") return "";
            if (state.status === "loading") {
                return `<div class="mc-translation">翻译中…</div>`;
            }
            if (state.status === "error") return "";
            if (state.expanded && state.text) {
                return `<div class="mc-translation">${escapeText(state.text)}</div>`;
            }
            return "";
        }

        function timelineMarkup() {
            if (instance.conversation.error && (instance.conversation.items || []).length === 0) {
                return `<div class="mc-error" role="alert">加载失败，请重试。<button class="button" type="button" data-action="mc-retry-conversation">重试</button></div>`;
            }
            const messages = instance.conversation.items || [];
            if (messages.length === 0) {
                if (instance.conversation.loading) {
                    return '<div class="mc-timeline"><div class="mc-empty">正在加载往来信件…</div></div>';
                }
                return '<div class="mc-timeline"><div class="mc-empty">该专家暂无往来信件</div></div>';
            }
            const loadOlder = instance.conversation.hasMore
                ? '<button class="button mc-load-older" type="button" data-action="mc-load-older">加载更早信件</button>'
                : "";
            return `<div class="mc-timeline">${loadOlder}${daySeparatorsHtml()}</div>`;
        }

        function scrollEl() {
            const body = conversationBody();
            return body && body.querySelector ? body.querySelector(".mc-scroll") : null;
        }

        function timelineEl() {
            const scroll = scrollEl();
            return scroll && scroll.querySelector ? scroll.querySelector(".mc-timeline") : null;
        }

        function renderTimelineMarkup() {
            const scroll = scrollEl();
            if (!scroll) return;
            const markup = timelineMarkup();
            const existing = scroll.querySelector ? scroll.querySelector(".mc-timeline") : null;
            const emptyBox = scroll.querySelector ? scroll.querySelector(".mc-error, .mc-empty") : null;
            if (existing) {
                existing.outerHTML = markup;
            } else if (emptyBox && !existing) {
                emptyBox.outerHTML = markup;
            } else {
                scroll.insertAdjacentHTML("afterbegin", markup);
            }
            rebindDetails();
        }

        function renderTimeline() {
            const scroll = scrollEl();
            if (!scroll) return;
            const anchor = captureAnchor();
            renderTimelineMarkup();
            restoreToAnchor(anchor);
        }

        function conversationContentHtml() {
            const header = `
                <header class="mc-header">
                    <div class="mc-identity"></div>
                    <div class="mc-actions"></div>
                    <div class="mc-header-meta"></div>
                </header>
            `;
            const timelineHead = `
                <div class="mc-timeline-head"><span>往来信件</span><span class="mc-position-hint"></span><button class="mc-text-button" type="button" data-action="mc-latest">↓ 最新消息</button></div>
            `;
            const scroll = `
                <div class="mc-scroll" tabindex="0" aria-label="往来信件滚动区">
                    ${timelineMarkup()}
                </div>
            `;
            return { header, timelineHead, scroll };
        }

        function renderConversationContent(options) {
            const opts = options || {};
            const body = conversationBody();
            if (!body) return;
            if (instance.selectedContactId == null) {
                renderConversationEmpty();
                return;
            }
            const { header, timelineHead, scroll } = conversationContentHtml();
            body.innerHTML = header + timelineHead + scroll;
            bindScrollListener();
            renderHeader();
            const scrollNode = body.querySelector(".mc-scroll");
            const failedNoItems = instance.conversation.error && (instance.conversation.items || []).length === 0;
            if (scrollNode && !failedNoItems) {
                renderWorkbenchSectionInto(scrollNode);
                renderManualSectionInto(scrollNode);
                renderLogsBlockInto(scrollNode);
            }
            rebindDetails();
            const record = opts.restoreRecord || null;
            if (record) {
                restoreFromRecord(record);
            } else if (opts.locateLatest) {
                locateLatestTop();
            }
            saveConversationState();
            const retryTags = hostFn("fetchExpertTagsFromEs");
            if (retryTags) refreshHeaderExpertTags();
        }

        // ---- 锚点 / 滚动位置（I-5） ----

        function contentOffsetTop(el, container) {
            if (!el || !container) return 0;
            if (typeof el.getBoundingClientRect === "function" && typeof container.getBoundingClientRect === "function") {
                return el.getBoundingClientRect().top - container.getBoundingClientRect().top + (Number(container.scrollTop) || 0);
            }
            let top = 0;
            let node = el;
            while (node && node !== container && node.nodeType === 1) {
                const offset = Number(node.offsetTop);
                if (Number.isFinite(offset)) top += offset;
                node = node.parentNode;
            }
            return top;
        }

        function scrollMax(scroll) {
            const height = Number(scroll.scrollHeight);
            const client = Number(scroll.clientHeight);
            if (Number.isFinite(height) && Number.isFinite(client) && height > 0 && client > 0) {
                return Math.max(0, height - client);
            }
            return Infinity;
        }

        function clampScrollTop(scroll, value) {
            const max = scrollMax(scroll);
            if (!Number.isFinite(value)) value = 0;
            if (max !== Infinity && value > max) return max;
            if (value < 0) return 0;
            return value;
        }

        function setScrollTop(value) {
            const scroll = scrollEl();
            if (!scroll) return;
            const clamped = clampScrollTop(scroll, value);
            scroll.scrollTop = clamped;
        }

        function getScrollTop() {
            const scroll = scrollEl();
            if (!scroll) return 0;
            const value = Number(scroll.scrollTop);
            return Number.isFinite(value) ? value : 0;
        }

        function messageElByKey(key) {
            const scroll = scrollEl();
            if (!scroll || !scroll.querySelectorAll) return null;
            const found = scroll.querySelectorAll(`[data-message-key="${CSS_ESCAPE(key)}"]`);
            return found.length ? found[0] : null;
        }

        function CSS_ESCAPE(value) {
            return String(value).replace(/"/g, '\\"');
        }

        function visibleAnchorKey(scroll) {
            const articles = scroll.querySelectorAll ? scroll.querySelectorAll(".mc-message") : [];
            const scrollTop = Number(scroll.scrollTop) || 0;
            let candidate = null;
            let candidateTop = Infinity;
            articles.forEach((article) => {
                const top = contentOffsetTop(article, scroll);
                const height = Number(article.offsetHeight) || 0;
                if (top + height > scrollTop && top < candidateTop) {
                    candidateTop = top;
                    candidate = article;
                }
            });
            if (!candidate) return null;
            return candidate.getAttribute("data-message-key");
        }

        function captureAnchor() {
            const scroll = scrollEl();
            if (!scroll) return null;
            const anchorKey = visibleAnchorKey(scroll);
            const scrollTop = getScrollTop();
            const anchorRelTop = anchorKey ? contentOffsetTopForMessageKey(anchorKey, scroll) - scrollTop : 0;
            return { anchorKey, anchorRelTop, scrollTop };
        }

        function contentOffsetTopForMessageKey(key, scroll) {
            const el = messageElByKey(key);
            return el ? contentOffsetTop(el, scroll) : null;
        }

        function restoreToAnchor(anchor) {
            if (!anchor) return;
            const scroll = scrollEl();
            if (!scroll) return;
            if (anchor.anchorKey) {
                const top = contentOffsetTopForMessageKey(anchor.anchorKey, scroll);
                if (top !== null) {
                    setScrollTop(top - (Number(anchor.anchorRelTop) || 0));
                    return;
                }
            }
            // 锚点缺失：同窗口 fallback scrollTop
            if (anchor.scrollTop !== undefined && anchor.scrollTop !== null) {
                setScrollTop(Number(anchor.scrollTop) || 0);
            }
        }

        function locateLatestTop() {
            const scroll = scrollEl();
            if (!scroll) return;
            const articles = scroll.querySelectorAll ? scroll.querySelectorAll(".mc-timeline .mc-message") : [];
            if (articles.length === 0) return;
            const last = articles[articles.length - 1];
            const top = contentOffsetTop(last, scroll);
            setScrollTop(top - 8);
        }

        function lastMessageEl() {
            const scroll = scrollEl();
            if (!scroll || !scroll.querySelectorAll) return null;
            const articles = scroll.querySelectorAll ? scroll.querySelectorAll(".mc-timeline .mc-message") : [];
            return articles.length ? articles[articles.length - 1] : null;
        }

        // ---- 会话状态保存（scroll/selectExpert/unmount/loadOlder/quiet） ----

        function saveConversationState() {
            const contactId = Number(instance.selectedContactId);
            if (!Number.isFinite(contactId) || contactId <= 0) return;
            const items = instance.conversation.items || [];
            if (items.length > MESSAGE_CACHE_LIMIT) {
                // 超限：不再保存，下次进入定位最新
                dropConversationRecord(instance.user, instance.conversation.accountScope || "", contactId);
                return;
            }
            const scroll = scrollEl();
            const anchor = captureAnchor();
            const drafts = currentDraftsMap();
            const rec = upsertConversationRecord(instance.user, instance.conversation.accountScope || "", contactId, {
                items: items.slice(),
                nextBefore: instance.conversation.nextBefore,
                hasMore: instance.conversation.hasMore,
                anchorKey: anchor ? anchor.anchorKey : null,
                anchorRelTop: anchor ? anchor.anchorRelTop : 0,
                scrollTop: scroll ? getScrollTop() : 0,
                scrollTopValid: !!scroll,
                drafts: drafts || new Map()
            });
            instance.draftsRef = rec.drafts;
        }

        function restoreFromRecord(record) {
            if (!record) return;
            if (record.anchorKey) {
                const scroll = scrollEl();
                if (scroll) {
                    const top = contentOffsetTopForMessageKey(record.anchorKey, scroll);
                    if (top !== null) {
                        setScrollTop(top - (Number(record.anchorRelTop) || 0));
                        return;
                    }
                }
            }
            if (record.scrollTopValid) {
                setScrollTop(Number(record.scrollTop) || 0);
                return;
            }
            locateLatestTop();
        }

        function onScroll() {
            if (instance.disposed) return;
            clearTimeout(instance.saveTimer);
            const scroll = scrollEl();
            if (!scroll) return;
            instance.saveTimer = setTimeout(() => {
                instance.saveTimer = null;
                if (instance.disposed) return;
                saveConversationState();
            }, SCROLL_SAVE_DEBOUNCE_MS);
        }

        // --------------------------------------------------------------
        // 标记已处理（I-1：服务端重查列表；空页回退；不动编辑器）
        // --------------------------------------------------------------

        function messageByKey(key) {
            const messages = instance.conversation.items || [];
            return messages.find((message) => `${message.source}:${message.id}` === key) || null;
        }

        function setInlineError(article, message) {
            if (!article || !article.querySelector) return;
            const error = article.querySelector(".mc-inline-error");
            if (!error) return;
            error.textContent = message;
            error.hidden = false;
        }

        function clearInlineError(article) {
            if (!article || !article.querySelector) return;
            const error = article.querySelector(".mc-inline-error");
            if (!error) return;
            error.textContent = "";
            error.hidden = true;
        }

        function markResolvedByKey(key) {
            const message = messageByKey(key);
            if (!message || !(message.processStatus === "MANUAL_REVIEW")) return;
            const processingId = Number(message.id);
            openDialog("mark-unmatched-resolved").then((payload) => {
                if (!payload) return;
                return hostApi()(`/api/mail/unmatched-inbound/${processingId}/mark-resolved`, {
                    method: "POST",
                    body: JSON.stringify(payload)
                }).then(() => {
                    if (instance.disposed) return;
                    hostShowStatus("已标记为处理完成");
                    const index = (instance.conversation.items || []).findIndex((item) => `${item.source}:${item.id}` === key);
                    if (index >= 0) {
                        const updated = Object.assign({}, instance.conversation.items[index], {
                            processStatus: "PROCESSED"
                        });
                        const items = instance.conversation.items.slice();
                        items[index] = updated;
                        instance.conversation.items = items;
                    }
                    const summary = instance.selectedSummary;
                    if (summary) {
                        summary.pendingCount = Math.max(0, (Number(summary.pendingCount) || 0) - 1);
                    }
                    const refreshBadge = hostFn("refreshUnmatchedBadge");
                    if (refreshBadge) refreshBadge();
                    renderTimeline();
                    // 服务端顺序重查当前页；空页回退
                    refreshListWithFallback();
                    // 新来信检查（可能最新来信变化）但不打断编辑器
                    checkInboundChangeQuiet();
                    saveConversationState();
                }).catch((err) => {
                    if (instance.disposed) return;
                    hostShowStatus(err && err.message ? err.message : "标记失败", "error");
                    const article = messageElByKey(key);
                    if (article) setInlineError(article, err && err.message ? err.message : "标记失败，请重试");
                });
            }).catch((err) => {
                if (instance.disposed) return;
                hostShowStatus(err && err.message ? err.message : "标记失败", "error");
            });
        }

        // ---- 邮件标签（I-3：timeline.tags 直读 / POST 回包 / 删除按 tagId） ----

        function openInboundTagModal(message) {
            const contactId = Number(instance.selectedContactId);
            const adapter = {
                inboundId: Number(message.id),
                source: "INBOUND_PROCESSING",
                contactId,
                onTagsChanged: (tags) => {
                    if (instance.disposed) return;
                    const currentContact = Number(instance.selectedContactId);
                    if (currentContact !== contactId) return;
                    updateMessageTags(`${message.source}:${message.id}`, tags);
                }
            };
            const openFn = hostFn("mcHostOpenInboundTagModal");
            if (openFn) {
                openFn(adapter);
                return;
            }
            hostShowStatus("添加标签功能不可用，请刷新后重试", "error");
        }

        function updateMessageTags(key, tags) {
            const index = (instance.conversation.items || []).findIndex((item) => `${item.source}:${item.id}` === key);
            if (index < 0) return;
            const updated = Object.assign({}, instance.conversation.items[index], {
                tags: Array.isArray(tags) ? tags : []
            });
            const items = instance.conversation.items.slice();
            items[index] = updated;
            instance.conversation.items = items;
            renderTimeline();
            saveConversationState();
        }

        function removeMailTagByKey(key, tagId) {
            const message = messageByKey(key);
            if (!message || !(message.source === "INBOUND_PROCESSING")) return;
            const tagIdNum = Number(tagId);
            hostApi()(`/api/inbound-summary/tags/${tagIdNum}`, { method: "DELETE" }).then(() => {
                if (instance.disposed) return;
                hostShowStatus("标签已删除", "ok");
                const index = (instance.conversation.items || []).findIndex((item) => `${item.source}:${item.id}` === key);
                if (index >= 0) {
                    const tags = Array.isArray(instance.conversation.items[index].tags)
                        ? instance.conversation.items[index].tags.filter((tag) => Number(tag.tagId) !== tagIdNum)
                        : [];
                    const updated = Object.assign({}, instance.conversation.items[index], { tags });
                    const items = instance.conversation.items.slice();
                    items[index] = updated;
                    instance.conversation.items = items;
                }
                renderTimeline();
                // 静默校验窗口 tags（服务端状态胜）并刷新列表 membership
                refreshConversationQuiet();
                fetchList({ page: instance.list.page });
            }).catch((err) => {
                if (instance.disposed) return;
                hostShowStatus(err && err.message ? err.message : "标签删除失败", "error");
                const article = messageElByKey(key);
                if (article) setInlineError(article, err && err.message ? err.message : "标签删除失败，请重试");
            });
        }

        // ---- 翻译（I-4） ----

        function toggleTranslateByKey(key) {
            const message = messageByKey(key);
            if (!message) return;
            const bodyText = messageDisplayText(message);
            if (!bodyText) return;
            const state = translationState(key, bodyText);
            if (state.status === "loading") return;
            if (state.status === "ok" && state.expanded) {
                state.expanded = false;
                renderTimeline();
                return;
            }
            if (state.status === "ok" && !state.expanded) {
                state.expanded = true;
                renderTimeline();
                return;
            }
            // 发起翻译（一次点击一次请求）
            state.status = "loading";
            state.expanded = false;
            renderTimeline();
            hostApi()("/api/translate", {
                method: "POST",
                body: JSON.stringify({ text: bodyText })
            }).then((result) => {
                if (instance.disposed) return;
                const latest = translationState(key, bodyText);
                if (latest.bodyText !== bodyText) return;
                if (result && result.ok && result.translatedText) {
                    latest.status = "ok";
                    latest.text = String(result.translatedText);
                    latest.expanded = true;
                } else {
                    latest.status = "error";
                }
                renderTimeline();
            }).catch(() => {
                if (instance.disposed) return;
                const latest = translationState(key, bodyText);
                if (latest.bodyText !== bodyText) return;
                latest.status = "error";
                renderTimeline();
            });
        }

        // --------------------------------------------------------------
        // 会话内容：workbench / manual / logs（S-5）
        // --------------------------------------------------------------

        function renderWorkbenchSectionInto(scroll) {
            if (!scroll) return;
            const summary = instance.selectedSummary || {};
            const latestInbound = summary.latestInbound || null;
            const canGenerate = latestInbound && latestInbound.processingId != null;
            const hostHtml = canGenerate
                ? '<div data-trust-host></div>'
                : '<div class="mc-note">暂无专家来信，暂不能生成回复</div>';
            scroll.insertAdjacentHTML("beforeend", `
                <details class="mc-section" data-section="workbench">
                    <summary>可信回复工作台</summary>
                    <div class="mc-section-content">${hostHtml}</div>
                </details>
            `);
        }

        function ensureWorkbenchMounted() {
            const summary = instance.selectedSummary || {};
            const latestInbound = summary.latestInbound || null;
            const processingId = latestInbound && latestInbound.processingId != null ? Number(latestInbound.processingId) : null;
            if (processingId == null) return;
            if (instance.workbench.instance && instance.workbench.processingId === processingId) return;
            if (instance.workbench.instance) {
                try { instance.workbench.instance.unmount(); } catch (e) { /* noop */ }
                instance.workbench.instance = null;
                instance.workbench.processingId = null;
            }
            const hostEl = host.querySelector ? host.querySelector('.mc-section[data-section="workbench"] [data-trust-host]') : null;
            if (!hostEl) return;
            const adapter = hostFn("mcHostMountWorkbench");
            if (!adapter) return;
            const controller = adapter(hostEl, processingId, {
                onComplete: (assembly) => adoptAssembly(processingId, assembly)
            });
            if (controller) {
                instance.workbench.instance = controller;
                instance.workbench.processingId = processingId;
            }
        }

        function renderManualSectionInto(scroll) {
            if (!scroll) return;
            const summary = instance.selectedSummary || {};
            const latestInbound = summary.latestInbound || null;
            const mode = latestInbound && latestInbound.processingId != null ? "inbound" : "outboundOnly";
            const targetProcessingId = mode === "inbound" ? Number(latestInbound.processingId) : null;
            const targetAccount = mode === "inbound" ? (latestInbound.accountCode || "") : "";
            const targetKey = mode === "inbound" ? `${Number(instance.selectedContactId)}:${targetProcessingId}:${targetAccount}` : null;
            const targetMsg = targetProcessingId != null ? latestInboundMessage() : null;
            const defaultSubject = targetMsg ? chatSubjectPrefill(targetMsg.subject) : "Re:";
            const draft = targetKey != null ? getDraft(targetKey) : null;

            instance.manual.mode = mode;
            instance.manual.targetProcessingId = targetProcessingId;
            instance.manual.targetAccountCode = targetAccount;
            instance.manual.targetKey = targetKey;
            instance.manual.qa = draft && draft.qa ? draft.qa : null;
            instance.manual.busy = false;

            const manualContent = mode === "inbound"
                ? manualComposeHtml(targetKey, targetProcessingId, targetAccount, draft, defaultSubject)
                : manualFollowUpHtml();
            scroll.insertAdjacentHTML("beforeend", `
                <details class="mc-section" data-section="manual" open>
                    <summary>人工回复</summary>
                    <div class="mc-section-content">${manualContent}</div>
                </details>
            `);
        }

        function manualTargetInfoText(processingId, account) {
            const parts = [];
            if (account) parts.push(escapeText(account));
            if (processingId != null) parts.push(`目标来信 #${processingId}`);
            return parts.join(" · ");
        }

        function manualComposeHtml(targetKey, processingId, account, draft, defaultSubject) {
            const subjectValue = draft ? draft.subject : defaultSubject;
            const editorText = draft ? draft.text : "";
            const targetInfo = manualTargetInfoText(processingId, account);
            return `
                <div class="mc-compose" data-role="manual-compose" data-target-key="${escapeText(targetKey)}">
                    <label>主题<input aria-label="回复主题" value="${escapeText(subjectValue)}"></label>
                    <div class="mc-editor-tools">
                        <button class="button" type="button" data-action="mc-rich-command" data-command="bold">B</button>
                        <button class="button" type="button" data-action="mc-rich-command" data-command="italic">I</button>
                        <button class="button" type="button" data-action="mc-rich-command" data-command="insertUnorderedList">列表</button>
                        <button class="button" type="button" data-action="mc-rich-command" data-command="createLink">链接</button>
                    </div>
                    <div class="mc-editor" contenteditable="true" role="textbox" aria-multiline="true" aria-label="人工回复正文" data-role="mc-editor">${editorText ? escapeText(editorText) : ""}</div>
                    <div class="mc-compose-footer">
                        <span data-role="target-info">回复账号与目标来信信息：${targetInfo}</span>
                        <button class="button primary" type="button" data-action="mc-send-manual">发送人工回复</button>
                    </div>
                </div>
            `;
        }

        function manualFollowUpHtml() {
            return `
                <div data-role="manual-followup">
                    <div class="mc-note">该专家暂无来信。请使用既有模板发送跟进邮件；系统不会在没有真实来信时伪造可生成的人工富文本回复。</div>
                    <button class="button primary" type="button" data-action="mc-template-follow" data-contact-id="${escapeText(instance.selectedContactId)}">选择模板发送跟进邮件</button>
                </div>
            `;
        }

        function renderLogsBlockInto(scroll) {
            if (!scroll) return;
            scroll.insertAdjacentHTML("beforeend", `
                <details class="mc-section" data-section="logs">
                    <summary>操作日志</summary>
                    <div class="mc-section-content"><div class="mc-empty">正在加载操作日志…</div></div>
                </details>
            `);
        }

        function loadLogs() {
            if (instance.logs.loaded) return;
            instance.logs.loaded = true;
            const scroll = scrollEl();
            const content = scroll ? scroll.querySelector('.mc-section[data-section="logs"] .mc-section-content') : null;
            if (!content) return;
            const contactId = Number(instance.selectedContactId);
            hostApi()(`/api/operator-action-logs?expertContactId=${contactId}&pageSize=50&pageOffset=0`).then((data) => {
                if (instance.disposed) return;
                const renderLogs = hostFn("renderOperatorLogs");
                const records = data && Array.isArray(data.records) ? data.records : (data ? (data.records || []) : []);
                content.innerHTML = renderLogs ? renderLogs(records) : `<div class="mc-empty">暂无操作日志</div>`;
            }).catch(() => {
                if (!instance.disposed) content.innerHTML = '<div class="mc-empty">日志加载失败</div>';
            });
        }

        // --------------------------------------------------------------
        // 关注（乐观更新 + 失败回滚）
        // --------------------------------------------------------------

        function setFollowControlsDisabled(disabled) {
            if (!host.querySelectorAll) return;
            host.querySelectorAll("[data-action=mc-toggle-follow]").forEach((button) => {
                button.disabled = disabled;
            });
        }

        function renderFollowButtons() {
            const summary = instance.selectedSummary || {};
            renderHeader();
            const items = instance.list.items || [];
            const index = items.findIndex((item) => String(item.contactId) === String(instance.selectedContactId));
            if (index >= 0) {
                const item = items[index];
                const sameSummary = summary.contactId != null && String(summary.contactId) === String(instance.selectedContactId);
                // 列表星标（未选中专家时）保留自身乐观状态；已选中专家以 summary 为准
                const followed = sameSummary ? summary.followed === true : item.followed === true;
                items[index] = Object.assign({}, item, { followed });
            }
            renderExpertList();
        }

        function toggleFollow(contactId) {
            const contactIdNum = Number(contactId);
            const items = instance.list.items || [];
            const item = items.find((entry) => String(entry.contactId) === String(contactIdNum));
            const summary = instance.selectedSummary;
            const target = (summary && String(summary.contactId) === String(contactIdNum)) ? summary : item;
            if (!target) return;
            const current = target.followed === true;
            const optimistic = !current;
            target.followed = optimistic;
            setFollowControlsDisabled(true);
            renderFollowButtons();
            const method = optimistic ? "PUT" : "DELETE";
            const request = hostApi()(`/api/mail/mailbox/conversations/${contactIdNum}/follow`, { method });
            request.then(() => {
                if (instance.disposed) return;
                setFollowControlsDisabled(false);
                if (target === summary) summary.followed = optimistic;
                renderFollowButtons();
                hostShowStatus(optimistic ? "已关注该专家" : "已取消关注", "ok");
                fetchList({ page: instance.list.page });
            }).catch((err) => {
                if (instance.disposed) return;
                target.followed = current;
                setFollowControlsDisabled(false);
                renderFollowButtons();
                hostShowStatus((err && err.message) ? `关注操作失败：${err.message}` : "关注操作失败", "error");
            });
        }

        // --------------------------------------------------------------
        // 管理 overlay（S-3）
        // --------------------------------------------------------------

        function createPortalRoot() {
            const doc = docRoot();
            if (!doc || !doc.body) return null;
            const root = doc.createElement("div");
            root.setAttribute("class", "mail-chat mc-overlay-root");
            root.setAttribute("data-role", "mc-overlay-root");
            doc.body.appendChild(root);
            return root;
        }

        function removePortalRoot() {
            const root = instance.elements.portalRoot;
            if (root && root.parentNode) root.parentNode.removeChild(root);
            instance.elements.portalRoot = null;
        }

        function ensurePortalRoot() {
            if (instance.elements.portalRoot) return instance.elements.portalRoot;
            const root = createPortalRoot();
            if (root) instance.elements.portalRoot = root;
            return root;
        }

        function manageOverlayEl() {
            const root = instance.elements.portalRoot;
            if (!root || !root.querySelector) return null;
            return root.querySelector(".mc-manage-overlay") || null;
        }

        function buildManageHtml(contact) {
            const name = (instance.selectedSummary && instance.selectedSummary.name) || (contact && contact.expertName) || "-";
            const sub = (instance.selectedSummary && instance.selectedSummary.email) || (contact && contact.expertEmail) || "";
            const statusValue = (contact && contact.operatorStatus) || "";
            const levelValue = (contact && contact.currentIndexLevel) || "";
            const statusOptions = optionsFromCatalog(statusCatalog(), statusValue);
            const levelOptions = optionsFromCatalog(levelCatalog(), levelValue);
            return `
                <div class="mc-manage-overlay" data-role="manage-overlay" hidden>
                    <section class="mc-manage-dialog" role="dialog" aria-modal="true" aria-labelledby="mcManageTitle" tabindex="-1">
                        <header><div><h3 id="mcManageTitle">专家管理</h3><p>${escapeText(sub || "")}</p></div><button class="mc-close" type="button" data-action="mc-close-manage" aria-label="关闭专家管理">×</button></header>
                        <div class="mc-status-grid">
                            <label class="mc-field">专家状态<select data-role="status-select" data-current-value="${escapeText(statusValue)}">${statusOptions}</select></label>
                            <label class="mc-field">专家层级<select data-role="level-select" data-current-value="${escapeText(levelValue)}">${levelOptions}</select></label>
                        </div>
                        <div class="mc-settings-tags" data-role="expert-tags"><p>正在加载专家标签…</p></div>
                        <p class="mc-manage-note">状态和层级点击保存；标签修改即时生效。</p>
                        <p class="mc-inline-error" role="alert" hidden></p>
                        <footer class="mc-dialog-actions">
                            <button class="button" type="button" data-action="mc-close-manage">取消</button>
                            <button class="button primary" type="button" data-action="mc-save-settings">保存变更</button>
                        </footer>
                    </section>
                </div>
            `;
        }

        function openManageOverlay() {
            const contactId = Number(instance.selectedContactId);
            if (!Number.isFinite(contactId) || contactId <= 0) return;
            const trigger = host.querySelector ? host.querySelector('[data-action="mc-manage-expert"]') : null;
            const contact = instance.conversation.contact || null;
            const pending = contact ? Promise.resolve(contact) : hostApi()(`/api/expert-contacts/${contactId}`).then((data) => data && data.contact ? data.contact : null).catch(() => null);
            pending.then((loaded) => {
                if (instance.disposed) return;
                if (Number(instance.selectedContactId) !== contactId) return;
                if (!loaded) {
                    hostShowStatus("专家资料加载失败，无法打开管理", "error");
                    return;
                }
                instance.conversation.contact = loaded;
                instance.manage.open = true;
                instance.manage.trigger = trigger;
                const root = ensurePortalRoot();
                if (!root) {
                    hostShowStatus("管理面板挂载失败", "error");
                    return;
                }
                root.innerHTML = buildManageHtml(loaded);
                const overlay = manageOverlayEl();
                if (overlay) {
                    overlay.hidden = false;
                    const statusSelect = overlay.querySelector('[data-role="status-select"]');
                    const levelSelect = overlay.querySelector('[data-role="level-select"]');
                    if (statusSelect) statusSelect.value = (loaded && loaded.operatorStatus) || "";
                    if (levelSelect) levelSelect.value = (loaded && loaded.currentIndexLevel) || "";
                    loadManageExpertTags(loaded);
                    const dialog = overlay.querySelector(".mc-manage-dialog");
                    if (dialog && typeof dialog.focus === "function") dialog.focus();
                }
            });
        }

        function managePortalContact() {
            return instance.conversation.contact || null;
        }

        function loadManageExpertTags(contact) {
            const overlay = manageOverlayEl();
            if (!overlay) return;
            const tagsRoot = overlay.querySelector ? overlay.querySelector('[data-role="expert-tags"]') : null;
            if (!tagsRoot) return;
            const orcidId = contact && (contact.orcidId || contact.expertOrcidId) ? String(contact.orcidId || contact.expertOrcidId) : "";
            const level = contact && contact.currentIndexLevel ? String(contact.currentIndexLevel) : "CANDIDATE";
            if (!orcidId) {
                tagsRoot.innerHTML = '<p class="mc-note">该专家在 ES 中无画像文档，标签功能不可用</p>';
                instance.headerTags = [];
                return;
            }
            const expertRef = { orcidId, currentIndexLevel: level, expertOrcidId: orcidId, expertIndexLevel: level };
            const fetchTags = hostFn("fetchExpertTagsFromEs");
            const renderEditor = hostFn("renderMailboxExpertTagEditor");
            const request = fetchTags
                ? fetchTags(orcidId, level)
                : Promise.resolve({ found: false, tags: [] });
            request.then((data) => {
                if (instance.disposed) return;
                const current = managePortalContact();
                const currentOrcid = current && (current.orcidId || current.expertOrcidId) ? String(current.orcidId || current.expertOrcidId) : "";
                if (currentOrcid !== orcidId) return;
                const profileMissing = !data || data.found === false;
                const tags = (data && Array.isArray(data.tags)) ? data.tags : [];
                instance.headerTags = profileMissing ? [] : tags.slice();
                if (renderEditor) {
                    tagsRoot.innerHTML = renderEditor(expertRef, tags, "mcManageExpertTagEditor", profileMissing);
                } else {
                    tagsRoot.innerHTML = profileMissing
                        ? '<p class="mc-note">该专家在 ES 中无画像文档，标签功能不可用</p>'
                        : '<p class="mc-note">暂无专家标签</p>';
                }
                const body = conversationBody();
                if (body) {
                    const meta = body.querySelector(".mc-header-meta");
                    if (meta) renderHeaderMeta(meta);
                }
            }).catch(() => {
                if (instance.disposed) return;
                tagsRoot.innerHTML = '<p class="mc-note">标签加载失败。<button class="mc-text-button" type="button" data-action="mc-retry-manage-tags">重试</button></p>';
            });
        }

        function retryManageExpertTags() {
            const overlay = manageOverlayEl();
            if (!overlay) return;
            const tagsRoot = overlay.querySelector ? overlay.querySelector('[data-role="expert-tags"]') : null;
            if (tagsRoot) tagsRoot.innerHTML = "<p>正在加载专家标签…</p>";
            loadManageExpertTags(managePortalContact());
        }

        function setManageError(message) {
            const overlay = manageOverlayEl();
            if (!overlay) return;
            const error = overlay.querySelector(".mc-inline-error");
            if (error) {
                error.textContent = message;
                error.hidden = false;
            }
        }

        function clearManageError() {
            const overlay = manageOverlayEl();
            if (!overlay) return;
            const error = overlay.querySelector(".mc-inline-error");
            if (error) {
                error.textContent = "";
                error.hidden = true;
            }
        }

        function closeManageOverlay(options) {
            const opts = options || {};
            const wasOpen = instance.manage.open;
            instance.manage.open = false;
            const overlay = manageOverlayEl();
            if (overlay) overlay.hidden = true;
            const root = instance.elements.portalRoot;
            if (root) root.innerHTML = "";
            if (wasOpen && opts.restoreFocus !== false) {
                const trigger = instance.manage.trigger;
                if (trigger && typeof trigger.focus === "function") trigger.focus();
            }
            instance.manage.trigger = null;
        }

        function manageStatusLevelChanged() {
            const overlay = manageOverlayEl();
            if (!overlay || !overlay.querySelector) return null;
            const statusSelect = overlay.querySelector('[data-role="status-select"]');
            const levelSelect = overlay.querySelector('[data-role="level-select"]');
            if (!statusSelect || !levelSelect) return null;
            const newStatus = String(statusSelect.value || "");
            const newLevel = String(levelSelect.value || "");
            const currentStatus = String(statusSelect.dataset && statusSelect.dataset.currentValue || "");
            const currentLevel = String(levelSelect.dataset && levelSelect.dataset.currentValue || "");
            return {
                statusChanged: !!(newStatus && newStatus !== currentStatus),
                levelChanged: !!(newLevel && newLevel !== currentLevel),
                newStatus,
                newLevel,
                currentStatus,
                currentLevel
            };
        }

        function saveManageSettings(button) {
            const change = manageStatusLevelChanged();
            if (!change) return;
            const contactId = Number(instance.selectedContactId);
            if (!Number.isFinite(contactId) || contactId <= 0) return;
            if (!change.statusChanged && !change.levelChanged) {
                hostShowStatus("专家状态和层级均未变化");
                return;
            }
            if (button) button.disabled = true;
            const operator = operatorName();
            const tasks = [];
            if (change.statusChanged) {
                tasks.push({
                    label: "专家状态",
                    request: hostApi()(`/api/expert-contacts/${contactId}/operator-status`, {
                        method: "POST",
                        body: JSON.stringify({ operatorStatus: change.newStatus, operatorName: operator })
                    })
                });
            }
            if (change.levelChanged) {
                tasks.push({
                    label: "专家层级",
                    request: hostApi()(`/api/expert-contacts/${contactId}/index-level`, {
                        method: "POST",
                        body: JSON.stringify({ targetLevel: change.newLevel, operatorName: operator })
                    })
                });
            }
            const contact = instance.conversation.contact;
            Promise.all(tasks.map((task) => task.request.then(() => ({ label: task.label, ok: true })).catch((err) => ({ label: task.label, ok: false, error: err && err.message ? err.message : "请求失败" })))).then((results) => {
                if (instance.disposed) return;
                const failures = results.filter((result) => !result.ok);
                const overlay = manageOverlayEl();
                // 逐端点回读：成功的端点更新 dataset/contact，失败端点保持原值
                if (contact) {
                    if (change.statusChanged && results[0] && results[0].ok) contact.operatorStatus = change.newStatus;
                    if (change.levelChanged && results[results.length - 1] && results[results.length - 1].ok) contact.currentIndexLevel = change.newLevel;
                }
                const statusSelect = overlay && overlay.querySelector ? overlay.querySelector('[data-role="status-select"]') : null;
                const levelSelect = overlay && overlay.querySelector ? overlay.querySelector('[data-role="level-select"]') : null;
                const statusOk = !change.statusChanged || (results[0] && results[0].ok);
                const levelOk = !change.levelChanged || (results[results.length - 1] && results[results.length - 1].ok);
                if (statusOk && statusSelect && statusSelect.dataset) statusSelect.dataset.currentValue = change.newStatus;
                if (levelOk && levelSelect && levelSelect.dataset) levelSelect.dataset.currentValue = change.newLevel;
                if (failures.length === 0) {
                    clearManageError();
                    hostShowStatus("专家信息已更新", "ok");
                    renderHeader();
                    // 层级变化可能影响 ES 标签读取层级：重读头部标签
                    if (change.levelChanged) refreshHeaderExpertTags();
                } else {
                    const detail = failures.map((failure) => `${failure.label}保存失败${failure.error ? `：${failure.error}` : ""}`).join("；");
                    setManageError(failures.length === results.length ? detail : `部分变更未保存：${detail}`);
                    hostShowStatus("专家信息保存失败，请重试", "error");
                    if (failures.length < results.length) renderHeader();
                }
                if (button) button.disabled = false;
            });
        }

        function manageDialogFocusables() {
            const overlay = manageOverlayEl();
            if (!overlay || !overlay.querySelectorAll) return [];
            const dialog = overlay.querySelector(".mc-manage-dialog");
            if (!dialog || !dialog.querySelectorAll) return [];
            return dialog.querySelectorAll("button, [href], input, select, textarea, [tabindex]:not([tabindex='-1'])").filter((el) => !el.disabled && el.getAttribute("hidden") !== "hidden");
        }

        function trapManageFocus(event) {
            if (!instance.manage.open) return;
            const overlay = manageOverlayEl();
            if (!overlay || overlay.hidden) return;
            if (event.key !== "Tab") return;
            const focusables = manageDialogFocusables();
            if (focusables.length === 0) return;
            const first = focusables[0];
            const last = focusables[focusables.length - 1];
            const active = typeof global.document !== "undefined" && global.document.activeElement ? global.document.activeElement : null;
            if (event.shiftKey) {
                if (active === first || !overlay.contains(active)) {
                    event.preventDefault();
                    if (typeof last.focus === "function") last.focus();
                }
            } else if (active === last || !overlay.contains(active)) {
                event.preventDefault();
                if (typeof first.focus === "function") first.focus();
            }
        }

        // ---- 专家标签动作（即时保存；更新列表行与头部） ----

        function handleExpertTagAction(element, action) {
            const editor = typeof element.closest === "function" ? element.closest(".expert-tag-editor") : null;
            if (!editor || !editor.dataset) return;
            const orcidId = editor.dataset.orcid;
            const level = editor.dataset.level;
            const editorId = editor.id || "mcManageExpertTagEditor";
            if (!orcidId) return;
            const contactId = Number(instance.selectedContactId);
            if (action === "expert-add-tag-open") {
                const fetchTags = hostFn("fetchExpertTagsFromEs");
                const openAdd = hostFn("openExpertTagAddDialog");
                const mutate = hostFn("mutateExpertTag");
                const updateEditor = hostFn("updateExpertTagEditor");
                const setLoading = hostFn("setTagEditorLoading");
                if (!fetchTags || !openAdd || !mutate) return;
                fetchTags(orcidId, level).then((existing) => {
                    if (existing && existing.found === false) {
                        hostShowStatus("该专家在 ES 中无画像文档，标签功能不可用", "warn");
                        return null;
                    }
                    return openAdd((existing && existing.tags) || []);
                }).then((tag) => {
                    if (!tag || !mutate) return;
                    if (setLoading && editor) setLoading(editor, true, "正在添加标签...");
                    return mutate(orcidId, level, tag, "add").then((tags) => {
                        if (instance.disposed || Number(instance.selectedContactId) !== contactId) return;
                        if (updateEditor) updateEditor(orcidId, tags, level, editorId);
                        instance.headerTags = Array.isArray(tags) ? tags.slice() : [];
                        hostShowStatus("标签已添加", "ok");
                        refreshAfterExpertTagChange(contactId);
                    }).catch((err) => {
                        hostShowStatus(err && err.message ? err.message : "标签添加失败", "error");
                    });
                }).catch(() => {});
                return;
            }
            if (action === "expert-remove-tag") {
                const tag = element.dataset.tag;
                if (!tag) return;
                const mutate = hostFn("mutateExpertTag");
                const updateEditor = hostFn("updateExpertTagEditor");
                if (!mutate) return;
                mutate(orcidId, level, tag, "remove").then((tags) => {
                    if (instance.disposed || Number(instance.selectedContactId) !== contactId) return;
                    if (updateEditor) updateEditor(orcidId, tags, level, editorId);
                    instance.headerTags = Array.isArray(tags) ? tags.slice() : [];
                    hostShowStatus("标签已删除", "ok");
                    refreshAfterExpertTagChange(contactId);
                }).catch((err) => {
                    hostShowStatus(err && err.message ? err.message : "标签删除失败", "error");
                });
            }
        }

        function refreshAfterExpertTagChange(contactId) {
            const meta = conversationBody() ? conversationBody().querySelector(".mc-header-meta") : null;
            if (meta) renderHeaderMeta(meta);
            // 静默刷新会话 summary + 当前列表行（服务端 expertTags）
            fetchList({ page: instance.list.page }).then(() => {
                if (instance.disposed) return;
                const fresh = findSummaryByContactId(instance.selectedContactId);
                if (fresh) instance.selectedSummary = fresh;
            });
            const scope = instance.conversation.accountScope || "";
            const record = getConversationRecord(instance.user, scope, Number(contactId));
            if (record) {
                // 保留窗口/位置，仅确保后续保存不丢
            }
        }

        // --------------------------------------------------------------
        // 材料抽屉 / 专家详情（宿主适配）
        // --------------------------------------------------------------

        function openMaterials(contactId) {
            const adapter = hostFn("mcHostOpenMaterials");
            if (adapter) adapter(contactId);
        }

        function openExpertDetail(contactId) {
            const adapter = hostFn("mcHostOpenExpertDetail");
            if (adapter) adapter(contactId);
        }

        function openFollowUpTemplateFlow() {
            const contactId = Number(instance.selectedContactId);
            if (!contactId) return;
            const adapter = hostFn("mcHostOpenFollowUp");
            if (adapter) adapter(contactId);
        }

        // --------------------------------------------------------------
        // 人工回复：草稿 / 采用 / 发送（I-7 保持原业务）
        // --------------------------------------------------------------

        function currentTargetKey() {
            return instance.manual.mode === "inbound" && instance.manual.targetKey
                ? instance.manual.targetKey
                : null;
        }

        function manualComposeEl() {
            return host.querySelector ? host.querySelector('[data-role="manual-compose"]') : null;
        }

        function manualInputs(composeEl) {
            const root = composeEl || manualComposeEl();
            if (!root || !root.querySelector) return null;
            const subjectInput = root.querySelector('input[aria-label="回复主题"]');
            const editor = root.querySelector('[aria-label="人工回复正文"]');
            if (!subjectInput || !editor) return null;
            return { subjectInput, editor };
        }

        function readManualValues(composeEl) {
            const inputs = manualInputs(composeEl);
            if (!inputs) return null;
            return {
                subject: inputs.subjectInput.value || "",
                html: typeof inputs.editor.innerHTML === "string" ? inputs.editor.innerHTML : "",
                text: typeof inputs.editor.innerText === "string" ? inputs.editor.innerText : String(inputs.editor.textContent || ""),
                qa: instance.manual.qa ? snapshotQa(instance.manual.qa) : null
            };
        }

        function saveDraftFromInputs() {
            const key = currentTargetKey();
            if (!key) return;
            const values = readManualValues();
            if (!values) return;
            setDraft(key, {
                subject: values.subject,
                html: values.html,
                text: values.text,
                qa: values.qa,
                updatedAt: new Date().toISOString()
            });
        }

        function snapshotQa(qa) {
            return {
                ragFactCodes: Array.isArray(qa.ragFactCodes) ? qa.ragFactCodes.slice() : [],
                ragCorpusFingerprint: qa.ragCorpusFingerprint || "",
                baselineText: qa.baselineText || ""
            };
        }

        function adoptAssembly(processingId, assembly) {
            if (instance.manual.mode !== "inbound" || Number(instance.manual.targetProcessingId) !== Number(processingId)) return;
            const composeEl = manualComposeEl();
            if (!composeEl) return;
            const inputs = manualInputs(composeEl);
            if (!inputs) return;
            const assemblyText = (assembly && (assembly.renderedDraftText || assembly.rawDraftText || assembly.text)) || "";
            const usedFactCodes = assembly && Array.isArray(assembly.usedFactCodes)
                ? assembly.usedFactCodes.slice()
                : [];
            const ragCorpusFingerprint = assembly && assembly.ragCorpusFingerprint
                ? assembly.ragCorpusFingerprint
                : "";
            instance.manual.qa = {
                ragFactCodes: usedFactCodes,
                ragCorpusFingerprint: ragCorpusFingerprint || "",
                baselineText: assemblyText
            };
            if (inputs.editor.innerText !== assemblyText) {
                inputs.editor.innerText = assemblyText;
            }
            saveDraftFromInputs();
            hostShowStatus("草稿已采用到人工回复区，请确认后发送", "ok");
            const manualSection = composeEl.closest ? composeEl.closest('.mc-section[data-section="manual"]') : null;
            if (manualSection && !manualSection.open && manualSection.setAttribute) {
                manualSection.setAttribute("open", "");
            }
        }

        function sendManualReply() {
            const key = currentTargetKey();
            if (!key || instance.manual.busy) return;
            const composeEl = manualComposeEl();
            const inputs = manualInputs(composeEl);
            if (!inputs) return;
            const subject = (inputs.subjectInput.value || "").trim();
            if (!subject) {
                hostShowStatus("请输入邮件主题", "error");
                return;
            }
            const hasBodyHtml = typeof inputs.editor.innerHTML === "string" && inputs.editor.innerHTML.trim();
            if (!hasBodyHtml) {
                hostShowStatus("请输入邮件正文", "error");
                return;
            }
            const textBody = typeof inputs.editor.innerText === "string" ? inputs.editor.innerText : String(inputs.editor.textContent || "");
            const requestBody = {
                senderAccountCode: null,
                subject,
                htmlBody: inputs.editor.innerHTML,
                textBody,
                operatorName: operatorName()
            };
            const qa = instance.manual.qa;
            if (qa && qa.ragFactCodes && qa.ragFactCodes.length) {
                requestBody.ragFactCodes = qa.ragFactCodes.slice();
                requestBody.ragCorpusFingerprint = qa.ragCorpusFingerprint || "";
                requestBody.edited = textBody.trim() !== (qa.baselineText || "").trim();
            }
            const processingId = Number(instance.manual.targetProcessingId);
            instance.manual.busy = true;
            setSendButtonDisabled(true);
            const adapter = hostFn("mcHostSendRichReply");
            const request = adapter
                ? adapter(processingId, requestBody)
                : Promise.reject(new Error("发送能力不可用"));
            request.then((sent) => {
                if (instance.disposed) return;
                instance.manual.busy = false;
                setSendButtonDisabled(false);
                if (sent) {
                    deleteDraft(key);
                    instance.manual.qa = null;
                    afterSuccessfulSend(key);
                }
                // 失败保留全部输入（不清草稿、不改 QA）
            }).catch(() => {
                if (instance.disposed) return;
                instance.manual.busy = false;
                setSendButtonDisabled(false);
            });
        }

        function setSendButtonDisabled(disabled) {
            const composeEl = manualComposeEl();
            if (!composeEl || !composeEl.querySelector) return;
            const button = composeEl.querySelector('[data-action="mc-send-manual"]');
            if (button) button.disabled = disabled;
        }

        function afterSuccessfulSend(clearedKey) {
            const contactId = Number(instance.selectedContactId);
            const myEpoch = instance.convEpoch;
            const msgParams = new URLSearchParams();
            msgParams.set("limit", String(MESSAGE_LIMIT));
            const accountFilter = instance.conversation.accountScope || "";
            if (accountFilter) msgParams.set("accountCode", accountFilter);
            hostApi()(`/api/mail/mailbox/conversations/${contactId}/messages?${msgParams.toString()}`).then((msgData) => {
                if (instance.disposed || myEpoch !== instance.convEpoch) return;
                const serverItems = (msgData && Array.isArray(msgData.items)) ? msgData.items : [];
                instance.conversation.items = mergeServerIntoWindow(instance.conversation.items, serverItems);
                instance.conversation.nextBefore = (msgData && msgData.nextBefore) || null;
                instance.conversation.hasMore = !!(msgData && msgData.hasMore);
                renderTimeline();
                checkInboundChangeQuiet();
                saveConversationState();
            }).catch(() => {});
            fetchList({ page: instance.list.page });
        }

        // ---- quiet refresh（I-5：合并保留已加载窗口） ----

        function refreshConversationQuiet() {
            const contactId = Number(instance.selectedContactId);
            if (!Number.isFinite(contactId) || contactId <= 0) return;
            const myEpoch = instance.convEpoch;
            const params = new URLSearchParams();
            params.set("limit", String(MESSAGE_LIMIT));
            const scopeAccount = instance.conversation.accountScope || "";
            if (scopeAccount) params.set("accountCode", scopeAccount);
            hostApi()(`/api/mail/mailbox/conversations/${contactId}/messages?${params.toString()}`).then((msgData) => {
                if (instance.disposed || myEpoch !== instance.convEpoch) return;
                const serverItems = (msgData && Array.isArray(msgData.items)) ? msgData.items : [];
                if (serverItems.length === 0 && (instance.conversation.items || []).length === 0) {
                    instance.conversation.nextBefore = (msgData && msgData.nextBefore) || null;
                    instance.conversation.hasMore = !!(msgData && msgData.hasMore);
                    return;
                }
                instance.conversation.items = mergeServerIntoWindow(instance.conversation.items, serverItems);
                instance.conversation.nextBefore = (msgData && msgData.nextBefore) || null;
                instance.conversation.hasMore = !!(msgData && msgData.hasMore);
                renderTimeline();
                checkInboundChangeQuiet();
                saveConversationState();
            }).catch(() => {});
        }

        function checkInboundChangeQuiet() {
            const summary = instance.selectedSummary || findSummaryByContactId(instance.selectedContactId);
            if (!summary) return;
            const latest = summary.latestInbound || null;
            const mode = instance.manual.mode;
            if (mode !== "inbound") return;
            const currentProcessing = instance.manual.targetProcessingId;
            const newestProcessing = latest && latest.processingId != null ? Number(latest.processingId) : null;
            if (newestProcessing == null || newestProcessing === currentProcessing) return;
            if (instance.dismissedNewInbound && instance.dismissedNewInbound === `${currentProcessing}:${newestProcessing}`) return;
            const draft = currentTargetKey() ? getDraft(currentTargetKey()) : null;
            const hasEditedDraft = draft && (draft.subject || draft.html || draft.text);
            if (!hasEditedDraft) {
                // 无已编辑草稿：静默跟随新目标
                retargetManual(newestProcessing, latest.accountCode || "");
                return;
            }
            const message = `该专家收到新的来信（#${newestProcessing}，${latest.receivedAt || ""}）。当前草稿仍基于来信 #${currentProcessing}。是否将回复目标切换到新来信？新来信主题将重新预填，正文与已采用回复事实保留；保留原目标请选「取消」。`;
            openDialog("confirm", { message }).then((confirmed) => {
                if (instance.disposed) return;
                if (confirmed) {
                    instance.dismissedNewInbound = null;
                    retargetManual(newestProcessing, latest.accountCode || "", { keepBody: true });
                } else {
                    instance.dismissedNewInbound = `${currentProcessing}:${newestProcessing}`;
                }
            });
        }

        function retargetManual(newProcessingId, newAccount, options) {
            const opts = options || {};
            const oldKey = currentTargetKey();
            const draft = oldKey ? getDraft(oldKey) : null;
            const contactId = Number(instance.selectedContactId);
            const newKey = `${contactId}:${newProcessingId}:${newAccount}`;
            if (draft) {
                const next = Object.assign({}, draft, { subject: "", updatedAt: new Date().toISOString() });
                setDraft(newKey, next);
                if (oldKey && oldKey !== newKey) deleteDraft(oldKey);
            }
            instance.manual.targetProcessingId = Number(newProcessingId);
            instance.manual.targetAccountCode = newAccount || "";
            instance.manual.targetKey = newKey;
            instance.manual.qa = draft && draft.qa ? snapshotQa(draft.qa) : null;
            const composeEl = manualComposeEl();
            if (!composeEl) return;
            const inputs = manualInputs(composeEl);
            if (inputs) {
                const targetMsg = (instance.conversation.items || []).find(
                    (message) => message.source === "INBOUND_PROCESSING" && String(message.id) === String(newProcessingId)
                );
                inputs.subjectInput.value = chatSubjectPrefill(targetMsg ? targetMsg.subject : "");
            }
            const info = composeEl.querySelector('[data-role="target-info"]');
            if (info) info.textContent = `回复账号与目标来信信息：${manualTargetInfoText(Number(newProcessingId), newAccount)}`;
            if (composeEl.dataset) composeEl.dataset.targetKey = newKey;
        }

        // --------------------------------------------------------------
        // 富文本工具（既有 execCommand 语义）
        // --------------------------------------------------------------

        function runRichCommand(command) {
            if (typeof document === "undefined" || typeof document.execCommand !== "function") return;
            if (command === "createLink") {
                const url = typeof prompt === "function" ? prompt("请输入链接 URL:") : null;
                if (url) document.execCommand(command, false, url);
                return;
            }
            document.execCommand(command, false, null);
        }

        // --------------------------------------------------------------
        // 事件（host 委托 + portal 委托；unmount 解绑）
        // --------------------------------------------------------------

        function findMessageByKeyAttr(button) {
            const key = button.dataset ? button.dataset.messageKey : "";
            if (!key) return null;
            return messageByKey(key);
        }

        function onClick(event) {
            if (instance.disposed) return;
            const target = event.target;
            const button = target && typeof target.closest === "function"
                ? target.closest("[data-action]")
                : null;
            const data = button ? (button.dataset || {}) : {};
            const action = button ? data.action : "";
            if (action === "mc-select-expert") {
                const contactId = Number(data.contactId);
                const item = findSummaryByContactId(contactId);
                if (item) selectExpert(item);
                return;
            }
            if (action === "mc-toggle-follow") {
                toggleFollow(data.contactId);
                return;
            }
            if (action === "mc-filter") {
                const chip = data.chip || CHIP_ALL;
                instance.chip = FILTER_CHIPS.some((entry) => entry.key === chip) ? chip : CHIP_ALL;
                instance.chipUserTouched = true;
                instance.list.page = 0;
                syncChipButtons();
                loadList();
                return;
            }
            if (action === "mc-more-filters") {
                if (instance.popoverOpen) {
                    closeFilterPopover({ restore: true, focusButton: false });
                } else {
                    openFilterPopover();
                }
                return;
            }
            if (action === "mc-close-filters") {
                closeFilterPopover({ restore: true, focusButton: true });
                return;
            }
            if (action === "mc-reset-filters") {
                resetAdvancedFilters();
                return;
            }
            if (action === "mc-clear-filters") {
                restoreAdvancedFilters();
                return;
            }
            if (action === "mc-retry-tag-options") {
                instance.tagOptions.loading = false;
                instance.tagOptions.loaded = false;
                instance.tagOptions.failed = false;
                clearFilterError();
                loadTagOptions(false);
                return;
            }
            if (action === "mc-page-prev") {
                if (instance.list.page > 0) {
                    instance.list.page -= 1;
                    fetchList({ page: instance.list.page });
                }
                return;
            }
            if (action === "mc-page-next") {
                instance.list.page += 1;
                fetchList({ page: instance.list.page });
                return;
            }
            if (action === "mc-retry-list") {
                fetchList({ page: instance.list.page });
                return;
            }
            if (action === "mc-retry-conversation") {
                const item = findSummaryByContactId(instance.selectedContactId);
                if (item) selectExpert(item);
                return;
            }
            if (action === "mc-load-older") {
                loadOlderMessages();
                return;
            }
            if (action === "mc-mark-resolved") {
                markResolvedByKey(data.messageKey || "");
                return;
            }
            if (action === "mc-remove-mail-tag") {
                const article = typeof button.closest === "function" ? button.closest(".mc-message") : null;
                const key = article && article.dataset ? article.dataset.messageKey : "";
                if (key) removeMailTagByKey(key, data.tagId);
                return;
            }
            if (action === "mc-translate") {
                toggleTranslateByKey(data.messageKey || "");
                return;
            }
            if (action === "mc-add-mail-tag") {
                const message = findMessageByKeyAttr(button);
                if (message) openInboundTagModal(message);
                return;
            }
            if (action === "mc-latest") {
                const el = lastMessageEl();
                if (el) {
                    const scroll = scrollEl();
                    if (scroll) setScrollTop(contentOffsetTop(el, scroll) - 8);
                }
                return;
            }
            if (action === "mc-view-attachments") {
                openMaterials(data.contactId);
                return;
            }
            if (action === "mc-open-materials") {
                openMaterials(data.contactId);
                return;
            }
            if (action === "mc-open-expert") {
                openExpertDetail(data.contactId);
                return;
            }
            if (action === "mc-manage-expert") {
                openManageOverlay();
                return;
            }
            if (action === "mc-save-settings") {
                saveManageSettings(button);
                return;
            }
            if (action === "mc-close-manage") {
                closeManageOverlay({ restoreFocus: true });
                return;
            }
            if (action === "mc-send-manual") {
                sendManualReply();
                return;
            }
            if (action === "mc-template-follow") {
                openFollowUpTemplateFlow();
                return;
            }
            if (action === "mc-rich-command") {
                runRichCommand(data.command || "");
                return;
            }
            if (action === "expert-add-tag-open" || action === "expert-remove-tag") {
                handleExpertTagAction(button, action);
                return;
            }
            // 非 data-action 节点：#mailboxSearchBtn（应用筛选；重置/清除均带 data-action）
            if (button === null && target && typeof target.closest === "function") {
                const applyBtn = target.closest("#mailboxSearchBtn");
                if (applyBtn) {
                    applyFiltersFromFields();
                }
            }
        }

        function onClickPortal(event) {
            if (instance.disposed) return;
            const target = event.target;
            const button = target && typeof target.closest === "function"
                ? target.closest("[data-action]")
                : null;
            if (!button) return;
            const action = button.dataset ? button.dataset.action : "";
            if (action === "expert-add-tag-open" || action === "expert-remove-tag") {
                handleExpertTagAction(button, action);
                return;
            }
            if (action === "mc-save-settings") {
                saveManageSettings(button);
                return;
            }
            if (action === "mc-close-manage") {
                closeManageOverlay({ restoreFocus: true });
                return;
            }
            if (action === "mc-retry-manage-tags") {
                retryManageExpertTags();
                return;
            }
        }

        function onPortalKeyDown(event) {
            if (instance.disposed) return;
            if (event.key === "Escape" && instance.manage.open) {
                const overlay = manageOverlayEl();
                if (overlay && !overlay.hidden) {
                    event.preventDefault();
                    closeManageOverlay({ restoreFocus: true });
                    return;
                }
            }
            trapManageFocus(event);
        }

        function onOutsideFilterClick(event) {
            if (instance.disposed) return;
            if (!instance.popoverOpen) return;
            const popover = host.querySelector ? host.querySelector("#mcFilterPopover") : null;
            const toggle = host.querySelector ? host.querySelector('[data-action="mc-more-filters"]') : null;
            if (!popover) return;
            const target = event.target;
            if (!target) return;
            if (popover.contains && popover.contains(target)) return;
            if (toggle && toggle.contains && toggle.contains(target)) return;
            closeFilterPopover({ restore: true, focusButton: true });
        }

        function onKeyDown(event) {
            if (instance.disposed) return;
            const target = event.target;
            if (!target) return;
            if (event.key === "Escape" && instance.popoverOpen) {
                const popover = host.querySelector ? host.querySelector("#mcFilterPopover") : null;
                if (popover && popover.contains && popover.contains(target)) {
                    event.preventDefault();
                    closeFilterPopover({ restore: true, focusButton: true });
                    return;
                }
            }
            if (event.key === "Enter") {
                const tag = target.tagName ? String(target.tagName).toLowerCase() : "";
                const id = target.id || (target.getAttribute ? target.getAttribute("id") : "") || "";
                if ((tag === "input") && (id === "mailboxFilterRecipient" || id === "mailboxFilterKeyword")) {
                    event.preventDefault();
                    applyFiltersFromFields();
                    return;
                }
                if (tag === "button" && id === "mailboxSearchBtn") {
                    event.preventDefault();
                    applyFiltersFromFields();
                    return;
                }
            }
        }

        function onInput(event) {
            if (instance.disposed) return;
            const target = event.target;
            if (!target) return;
            const searchInput = host.querySelector ? host.querySelector('.mc-search-row input[type="search"]') : null;
            if (searchInput && target === searchInput) {
                clearTimeout(instance.searchTimer);
                const value = target.value || "";
                instance.searchTimer = setTimeout(() => {
                    instance.searchTimer = null;
                    if (instance.disposed) return;
                    if (instance.searchText === value) return;
                    instance.searchText = value;
                    instance.list.page = 0;
                    loadList();
                }, SEARCH_DEBOUNCE_MS);
                return;
            }
            const composeEl = manualComposeEl();
            if (composeEl && composeEl.contains && composeEl.contains(target)) {
                saveDraftFromInputs();
            }
        }

        function onChange(event) {
            if (instance.disposed) return;
            const target = event.target;
            if (!target || !target.id) return;
            const inPopover = host.querySelector && host.querySelector("#mcFilterPopover");
            if (inPopover && inPopover.contains && inPopover.contains(target)) {
                // 草稿态：不改已生效筛选，只清错误提示
                clearFilterError();
            }
        }

        function onScrollEvent() {
            onScroll();
        }

        function handleDetailsToggle(event) {
            if (instance.disposed) return;
            const target = event.target;
            if (!target || target.nodeType !== 1) return;
            if (target.matches && target.matches('.mc-section[data-section="workbench"]')) {
                if (target.open) ensureWorkbenchMounted();
                return;
            }
            if (target.matches && target.matches('.mc-section[data-section="logs"]')) {
                if (target.open) loadLogs();
                return;
            }
        }

        function bindDetails(scopeEl) {
            if (!scopeEl || !scopeEl.querySelectorAll) return;
            scopeEl.querySelectorAll("details").forEach((details) => {
                if (details.__mcToggleBound) return;
                details.__mcToggleBound = true;
                if (typeof details.addEventListener === "function") {
                    details.addEventListener("toggle", handleDetailsToggle);
                }
            });
        }

        function rebindDetails() {
            const body = conversationBody();
            if (body) bindDetails(body);
            const overlay = manageOverlayEl();
            if (overlay) bindDetails(overlay);
        }

        function syncChipButtons() {
            if (!host.querySelectorAll) return;
            host.querySelectorAll(".mc-filter[data-action=mc-filter]").forEach((button) => {
                const pressed = button.dataset && button.dataset.chip === instance.chip;
                button.setAttribute("aria-pressed", pressed ? "true" : "false");
            });
        }

        function loadOlderMessages() {
            if (!instance.conversation.hasMore || !instance.conversation.nextBefore) return;
            if (instance.loadOlderBusy) return;
            instance.loadOlderBusy = true;
            const contactId = Number(instance.selectedContactId);
            const myEpoch = instance.convEpoch;
            const params = new URLSearchParams();
            params.set("limit", String(MESSAGE_LIMIT));
            params.set("before", instance.conversation.nextBefore);
            const accountFilter = instance.conversation.accountScope || "";
            if (accountFilter) params.set("accountCode", accountFilter);
            const anchor = captureAnchor();
            hostApi()(`/api/mail/mailbox/conversations/${contactId}/messages?${params.toString()}`).then((data) => {
                if (instance.disposed || myEpoch !== instance.convEpoch) return;
                const older = (data && Array.isArray(data.items)) ? data.items : [];
                const known = new Set((instance.conversation.items || []).map((message) => `${message.source}:${message.id}`));
                const merged = older.filter((message) => !known.has(`${message.source}:${message.id}`))
                    .concat(instance.conversation.items || []);
                instance.conversation.items = merged;
                instance.conversation.nextBefore = (data && data.nextBefore) || null;
                instance.conversation.hasMore = !!(data && data.hasMore);
                instance.loadOlderBusy = false;
                renderTimelineMarkup();
                if (anchor) restoreToAnchor(anchor);
                saveConversationState();
            }).catch((err) => {
                if (instance.disposed) return;
                instance.loadOlderBusy = false;
                hostShowStatus(`加载更早信件失败：${err && err.message ? err.message : ""}`, "error");
            });
        }

        // --------------------------------------------------------------
        // 实例 API / options
        // --------------------------------------------------------------

        function applyOptions(options) {
            const next = options || {};
            if (next.focus && next.focus.contactId != null) {
                instance.options.focus = { contactId: next.focus.contactId, email: next.focus.email || "" };
            }
            if (next.filters) {
                // 快照完整替换，不能合并残留旧值（I-2）
                instance.filters = Object.assign({}, next.filters);
                // 仅初次（用户尚未操作 tab）允许外部 onlyPending 初始化
                if (typeof next.filters.pendingOnly === "boolean" && !instance.chipUserTouched) {
                    if (next.filters.pendingOnly && instance.chip !== CHIP_PENDING) {
                        instance.chip = CHIP_PENDING;
                        syncChipButtons();
                    } else if (!next.filters.pendingOnly && instance.chip === CHIP_PENDING) {
                        instance.chip = CHIP_ALL;
                        syncChipButtons();
                    }
                }
                renderFilterChrome();
            }
            return next;
        }

        function unmount() {
            if (instance.disposed) return;
            saveConversationState();
            instance.disposed = true;
            clearTimeout(instance.searchTimer);
            clearTimeout(instance.saveTimer);
            teardownConversationSubViews();
            handlers.forEach((pair) => host.removeEventListener(pair[0], pair[1]));
            handlers.length = 0;
            portalHandlers.forEach((pair) => {
                const [root, type, fn] = pair;
                if (root && typeof root.removeEventListener === "function") root.removeEventListener(type, fn);
            });
            portalHandlers.length = 0;
            const doc = docRoot();
            if (doc && typeof doc.removeEventListener === "function") {
                doc.removeEventListener("click", onOutsideFilterClick);
            }
            restoreRefreshButton();
            restoreLegacyFilterNodes();
            removePortalRoot();
            setRefined(false);
            if (instance.host) instance.host.innerHTML = "";
            instances.delete(instance.host);
        }

        function refreshFromHost() {
            if (instance.disposed) return;
            saveConversationState();
            return fetchList({ page: instance.list.page }).then((data) => {
                if (instance.disposed) return data;
                if (instance.selectedContactId != null) refreshConversationQuiet();
                return data;
            });
        }

        function refresh() {
            if (instance.disposed) return Promise.resolve();
            instance.list.page = 0;
            return loadList();
        }

        // --------------------------------------------------------------
        // 组装
        // --------------------------------------------------------------

        function attach() {
            renderSkeleton();
            rememberLegacyFilterTexts();
            ensureFilterFieldsPresent();
            renderFilterChrome();
            const doc = docRoot();
            if (doc && typeof doc.addEventListener === "function") {
                doc.addEventListener("click", onOutsideFilterClick);
            }
            bindFilterEvents();
            ensurePortalRoot();
            listenPortal("click", onClickPortal);
            listenPortal("keydown", onPortalKeyDown);
            bindDetails(host);
            setRefined(true);
            moveRefreshButtonIntoActions();
        }

        attach();
        if (!(options && options.focus && options.focus.contactId != null)) {
            renderConversationEmpty();
        }
        return {
            instance,
            applyOptions,
            refresh,
            refreshFromHost,
            loadList,
            unmount,
            isMounted: () => true
        };
    }

    // ------------------------------------------------------------------
    // 公共 API（mount 同 host 再次调用 = 刷新语义，不重复建 DOM）
    // ------------------------------------------------------------------

    function mount(host, options) {
        if (!host) throw new Error("MailboxChat.mount requires a host element");
        const existing = instances.get(host);
        if (existing) {
            const opts = options || {};
            if (opts.filters || opts.focus || opts.sessionUser) {
                if (typeof existing.applyOptions === "function") existing.applyOptions(opts);
            }
            if (typeof existing.loadList === "function") existing.loadList();
            return existing;
        }
        const controller = createInstance(host, options || {});
        const api = {
            applyOptions: (next) => { if (controller) controller.applyOptions(next); },
            refresh: () => { if (controller) return controller.refresh(); return undefined; },
            refreshFromHost: () => { if (controller) return controller.refreshFromHost(); return undefined; },
            loadList: () => { if (controller) return controller.loadList(); return undefined; },
            unmount: () => { if (controller) controller.unmount(); },
            isMounted: () => { if (controller) return controller.isMounted(); return false; }
        };
        controller.api = api;
        instances.set(host, api);
        api.loadList();
        return api;
    }

    function unmount(host) {
        if (!host) return false;
        const api = instances.get(host);
        if (api && typeof api.unmount === "function") {
            api.unmount();
            return true;
        }
        return false;
    }

    function isMounted(host) {
        if (!host) return false;
        const api = instances.get(host);
        return !!api;
    }

    global.MailboxChat = Object.freeze({
        mount,
        unmount,
        isMounted,
        version: VERSION
    });
})(typeof window !== "undefined" ? window : (typeof globalThis !== "undefined" ? globalThis : this));
