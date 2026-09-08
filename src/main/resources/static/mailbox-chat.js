/**
 * 收发件箱专家聊天（子计划 10 · I-1..I-5 / 样式契约 S-3/S-4/S-5）。
 *
 * 左侧只展示专家（按 child-07 conversations summary API 聚合、20 位/页），
 * 右侧固定顺序呈现 设置 → 往来信件 → 可信回复工作台（默认折叠）→ 人工回复
 * （默认展开）→ 操作日志（默认折叠）。宿主（app.js）在收发件箱视图且无
 * taskExecutionId 时挂载本组件；旧 table/group 渲染保留给任务钻取与脚本未
 * 加载兼容分支（loadMailbox 守卫在 app.js）。
 *
 * 关键约束（与宿主/既有模块的关系）：
 * - 一切专家会话/消息/关注请求走 child-07 conversations API；消息渲染一律以
 *   (source,id) 为键；正文经既有文本安全函数渲染；不做任何下载/IMAP 触发。
 * - “标记已处理”只对 INBOUND_PROCESSING + MANUAL_REVIEW 的消息出现，走既有
 *   pending/unmatched mark-resolved API（宿主 openActionDialog 收集操作人），
 *   成功后局部更新该消息、该专家 pending 计数与全局未处理角标，不重建编辑器。
 * - 可信工作台 = TrustReplyWorkbench.mount 的 LIVE_INBOUND 固定宿主（经 app.js
 *   适配器 mcHostMountWorkbench 注入真实 processingId，autoBootstrap=false：
 *   默认折叠不请求、展开不自动生成）；切专家销毁旧 mount、丢弃旧响应。
 * - 人工回复采用/发送沿用宿主 mcHostSendRichReply（服务端校验 + QA 审计 +
 *   安全确认流程），内存草稿按 contactId+targetInboundId+account 键存；
 *   新来信带已编辑草稿时提示用户选择目标，绝不静默改写主题/正文/QA 信息；
 *   发送成功才清草稿并刷新计数；失败保留全部输入。
 * - 无来信（纯发件）专家：时间线保留全部发件；工作台显示“暂无专家来信，暂不能
 *   生成回复”（0 次生成调用）；人工区显示说明 + “选择模板发送跟进邮件”，按钮走
 *   既有专家模板发件流程（COMPOSE_TEMPLATE，绝不伪造自由富文本与 processingId）。
 * - 材料：header“材料 N”与单信“查看全部附件”均经 app.js 适配器 mcHostOpenMaterials
 *   打开 child-08 ExpertMaterials 抽屉（同一 contactId 共享 store，本组件不复制
 *   材料 DOM/逻辑）。
 * - 本文件只声明 S-3 及既有全局 class；无 inline style；不改 trust-reply-workbench.js。
 */
(function (global) {
    "use strict";

    if (global.MailboxChat) return;

    const PAGE_SIZE = 20;
    const MESSAGE_LIMIT = 50;
    const SEARCH_DEBOUNCE_MS = 300;
    const VERSION = "1";

    const CHIP_ALL = "all";
    const CHIP_FOLLOWED = "followed";
    const CHIP_PENDING = "pending";
    const CHIP_WAITING = "waiting";

    const FILTER_CHIPS = [
        { key: CHIP_ALL, label: "全部" },
        { key: CHIP_FOLLOWED, label: "关注" },
        { key: CHIP_PENDING, label: "待处理" },
        { key: CHIP_WAITING, label: "待专家回复" }
    ];

    const SOURCE_LABELS = {
        INBOUND_PROCESSING: "专家来信",
        MAIL_RECORD: "往来邮件"
    };

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

    // ------------------------------------------------------------------
    // 纯文本/时间工具（与既有正文显示点同一安全语义）
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

    function chipParams(chip) {
        if (chip === CHIP_FOLLOWED) return { followed: true };
        if (chip === CHIP_PENDING) return { pendingOnly: true };
        if (chip === CHIP_WAITING) return { waitingReply: true };
        return {};
    }

    // ------------------------------------------------------------------
    // 全局实例表（host -> instance；同一 #mailboxList 重复 mount = 刷新）
    // ------------------------------------------------------------------

    const instances = new Map();

    function getInstance(host) {
        return instances.get(host) || null;
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
            searchTimer: null,
            searchText: "",
            chip: CHIP_ALL,
            list: { page: 0, total: 0, items: [], loading: false, error: "" },
            selectedContactId: null,
            selectedSummary: null,
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
            drafts: new Map(),
            workbench: { instance: null, processingId: null },
            settings: { loaded: false, contactId: null, tagsState: "idle" },
            logs: { loaded: false },
            extras: new Map(),
            dismissedNewInbound: null,
            pendingPrompt: null,
            disposed: false
        };

        const handlers = [];

        function listen(type, handler) {
            host.addEventListener(type, handler);
            handlers.push([type, handler]);
        }

        // --------------------------------------------------------------
        // 渲染骨架（S-3 层级：专家栏 + 会话栏）
        // --------------------------------------------------------------

        function skeletonHtml() {
            return `
                <div class="mail-chat">
                    <aside class="mc-experts" aria-label="专家会话列表">
                        <div class="mc-list-tools">
                            <input type="search" aria-label="搜索专家" placeholder="搜索专家姓名、邮箱">
                            <div class="mc-filters">
                                ${FILTER_CHIPS.map((chip) =>
        `<button class="mc-filter" type="button" data-action="mc-filter" data-chip="${chip.key}" aria-pressed="${instance.chip === chip.key ? "true" : "false"}">${escapeText(chip.label)}</button>`
    ).join("")}
                            </div>
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

        // --------------------------------------------------------------
        // 专家列表
        // --------------------------------------------------------------

        function conversationsParams(page) {
            const params = new URLSearchParams();
            params.set("page", String(page));
            params.set("size", String(PAGE_SIZE));
            const q = instance.searchText.trim();
            if (q) params.set("q", q);
            const chipParamsValue = chipParams(instance.chip);
            if (chipParamsValue.followed) params.set("followed", "true");
            if (chipParamsValue.pendingOnly) params.set("pendingOnly", "true");
            if (chipParamsValue.waitingReply) params.set("waitingReply", "true");
            const filters = instance.options.filters || {};
            if (filters.pendingOnly && !chipParamsValue.pendingOnly) params.set("pendingOnly", "true");
            if (filters.accountCode) params.set("accountCode", filters.accountCode);
            if (filters.direction) params.set("direction", filters.direction);
            if (filters.startDate) params.set("startDate", filters.startDate);
            if (filters.endDate) params.set("endDate", filters.endDate);
            if (filters.subject) params.set("subject", filters.subject);
            if (filters.label) params.set("label", filters.label);
            return params;
        }

        function renderPerson(item) {
            const waiting = item.waitingReply === true;
            const pendingCount = Number(item.pendingCount) || 0;
            const latest = item.latestMessage || null;
            const latestLine = latest
                ? `${latest.direction === "INBOUND" ? "最近来信" : "最近发件"}：${latest.subject || "(无主题)"}`
                : "暂无往来";
            const badges = [];
            if (waiting) badges.push('<span class="mc-badge" data-tone="waiting">待专家回复</span>');
            if (pendingCount > 0) badges.push(`<span class="mc-badge" data-tone="pending">待处理</span>`);
            const accounts = Array.isArray(item.accountCodes) && item.accountCodes.length
                ? item.accountCodes.join("、")
                : (item.email || "-");
            const active = instance.selectedContactId != null
                && String(item.contactId) === String(instance.selectedContactId);
            return `
                <div class="mc-person" data-active="${active ? "true" : "false"}" data-contact-id="${escapeText(item.contactId)}">
                    <button class="mc-person-main" type="button" data-action="mc-select-expert" data-contact-id="${escapeText(item.contactId)}"${active ? ' aria-current="true"' : ""}>
                        <strong>${escapeText(item.name || item.email || "-")}</strong>
                        <small>${escapeText(accounts)}</small>
                        <small>${escapeText(latestLine)}</small>
                        <span class="mc-person-meta">
                            <span>收 ${Number(item.receivedCount) || 0} · 发 ${Number(item.sentCount) || 0}</span>
                            ${badges.join("")}
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
                if (instance.disposed || mySeq !== instance.listSeq) return;
                instance.list.items = (data && Array.isArray(data.items)) ? data.items : [];
                instance.list.total = Number(data && data.total) || 0;
                instance.list.page = page;
                instance.list.loading = false;
                renderExpertList();
                renderPager();
                return data;
            }).catch((err) => {
                if (instance.disposed || mySeq !== instance.listSeq) return;
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
                if (instance.disposed) return;
                resolveFocusAndSelection();
                return data;
            });
        }

        function resolveFocusAndSelection() {
            const items = instance.list.items || [];
            const focus = instance.options.focus;
            if (focus && focus.contactId != null && instance.selectedContactId == null) {
                const found = items.find((item) => String(item.contactId) === String(focus.contactId));
                if (found) {
                    selectExpert(found, { skipListReload: true });
                    return;
                }
                // 焦点专家不在当前页：以焦点邮箱精确搜索该专家（对应旧 by-expert 聚焦语义）
                if (focus.email) {
                    const q = String(focus.email).trim();
                    instance.listSeq += 1;
                    const mySeq = instance.listSeq;
                    const params = conversationsParams(0);
                    params.set("q", q);
                    hostApi()(`/api/mail/mailbox/conversations?${params.toString()}`).then((data) => {
                        if (instance.disposed || mySeq !== instance.listSeq) return;
                        const located = (data && data.items || []).find(
                            (item) => String(item.contactId) === String(focus.contactId)
                        );
                        if (located) {
                            instance.list.items = data.items || [];
                            instance.list.total = Number(data && data.total) || 0;
                            instance.list.page = 0;
                            renderExpertList();
                            renderPager();
                            selectExpert(located, { skipListReload: true });
                        }
                    }).catch(() => {});
                }
                return;
            }
            if (instance.selectedContactId != null && instance.list.items && instance.list.items.length > 0) {
                const stillPresent = instance.list.items.some(
                    (item) => String(item.contactId) === String(instance.selectedContactId)
                );
                const accountFilter = instance.options.filters && instance.options.filters.accountCode
                    ? instance.options.filters.accountCode
                    : "";
                if (stillPresent && instance.conversation.accountScope !== accountFilter) {
                    const item = findSummaryByContactId(instance.selectedContactId);
                    if (item) {
                        selectExpert(item, { skipListReload: true });
                        return;
                    }
                }
                if (stillPresent) {
                    const freshSummary = findSummaryByContactId(instance.selectedContactId);
                    if (freshSummary) instance.selectedSummary = freshSummary;
                    // 刷新该专家最新往来（静默；不改动人工区 DOM/草稿），随后检查新来信目标
                    refreshConversationQuiet();
                } else {
                    // 当前筛选不再包含该专家：回到“请选择专家”空态（聊天列表驱动）
                    teardownConversationSubViews();
                    instance.selectedContactId = null;
                    instance.selectedSummary = null;
                    renderConversationEmpty();
                }
                return;
            }
            if (instance.selectedContactId != null && (!instance.list.items || instance.list.items.length === 0)) {
                teardownConversationSubViews();
                instance.selectedContactId = null;
                instance.selectedSummary = null;
                renderConversationEmpty();
                return;
            }
            if (instance.selectedContactId == null && items.length > 0 && !focus) {
                // 无聚焦时保持“请选择专家”空态，不擅自代选
                renderConversationEmpty();
            }
        }

        function refreshConversationQuiet() {
            const contactId = Number(instance.selectedContactId);
            if (!Number.isFinite(contactId) || contactId <= 0) return;
            instance.msgSeq += 1;
            const mySeq = instance.msgSeq;
            const params = new URLSearchParams();
            params.set("limit", String(MESSAGE_LIMIT));
            const scopeAccount = instance.conversation.accountScope || "";
            if (scopeAccount) params.set("accountCode", scopeAccount);
            hostApi()(`/api/mail/mailbox/conversations/${contactId}/messages?${params.toString()}`).then((msgData) => {
                if (instance.disposed || mySeq !== instance.msgSeq) return;
                instance.conversation.items = (msgData && Array.isArray(msgData.items)) ? msgData.items : [];
                instance.conversation.nextBefore = (msgData && msgData.nextBefore) || null;
                instance.conversation.hasMore = !!(msgData && msgData.hasMore);
                if (instance.selectedContactId == null) return;
                renderTimeline();
                checkInboundChangeQuiet();
            }).catch(() => {});
        }

        // --------------------------------------------------------------
        // 选择专家 → 右侧会话
        // --------------------------------------------------------------

        function findSummaryByContactId(contactId) {
            const items = instance.list.items || [];
            return items.find((item) => String(item.contactId) === String(contactId)) || null;
        }

        function teardownConversationSubViews() {
            if (instance.workbench.instance) {
                try { instance.workbench.instance.unmount(); } catch (e) { /* noop */ }
                instance.workbench.instance = null;
                instance.workbench.processingId = null;
            }
            const releaseMaterials = hostFn("unmountExpertMaterialsHosts");
            if (releaseMaterials && instance.host) releaseMaterials(instance.host);
        }

        function selectExpert(item, options) {
            const opts = options || {};
            teardownConversationSubViews();
            instance.selectedContactId = Number(item.contactId);
            instance.selectedSummary = item;
            instance.seq += 1;
            const mySeq = instance.seq;
            instance.conversation.loading = true;
            instance.conversation.error = "";
            instance.conversation.items = [];
            instance.conversation.nextBefore = null;
            instance.conversation.hasMore = false;
            instance.manual = {
                mode: "none",
                targetProcessingId: null,
                targetAccountCode: "",
                targetKey: null,
                qa: null,
                busy: false
            };
            instance.settings = { loaded: false, contactId: Number(item.contactId), tagsState: "idle" };
            instance.logs = { loaded: false };
            instance.dismissedNewInbound = null;
            instance.pendingPrompt = null;
            renderConversationScaffold();

            const contactId = Number(item.contactId);
            const accountFilter = instance.options.filters && instance.options.filters.accountCode
                ? instance.options.filters.accountCode
                : "";
            const msgParams = new URLSearchParams();
            msgParams.set("limit", String(MESSAGE_LIMIT));
            if (accountFilter) msgParams.set("accountCode", accountFilter);
            const contactPromise = hostApi()(`/api/expert-contacts/${contactId}`).catch(() => null);
            const messagesPromise = hostApi()(`/api/mail/mailbox/conversations/${contactId}/messages?${msgParams.toString()}`)
                .catch(() => ({ items: [], nextBefore: null, hasMore: false }));

            Promise.all([contactPromise, messagesPromise]).then(([contact, msgData]) => {
                if (instance.disposed || mySeq !== instance.seq) return;
                instance.conversation.contact = contact && contact.contact ? contact.contact : (contact || null);
                instance.conversation.items = (msgData && Array.isArray(msgData.items)) ? msgData.items : [];
                instance.conversation.nextBefore = (msgData && msgData.nextBefore) || null;
                instance.conversation.hasMore = !!(msgData && msgData.hasMore);
                instance.conversation.accountScope = accountFilter;
                instance.conversation.loading = false;
                renderConversationContent();
                if (!opts.skipListReload) {
                    // 静默刷新列表，保证计数/关注等与服务器一致
                    fetchList({ page: instance.list.page });
                }
            });
        }

        function renderConversationScaffold() {
            const body = conversationBody();
            if (!body) return;
            body.innerHTML = `
                <header class="mc-header"><div class="mc-identity"><h2>${escapeText((instance.selectedSummary && (instance.selectedSummary.name || instance.selectedSummary.email)) || "…")}</h2><p>正在加载往来信件…</p></div><div class="mc-actions"></div></header>
                <div class="mc-scroll"><div class="mc-empty">正在加载往来信件…</div></div>
            `;
        }

        function conversationSummaryInfo() {
            const summary = instance.selectedSummary || {};
            const parts = [];
            if (summary.email) parts.push(summary.email);
            if (summary.orcid) parts.push(`ORCID ${summary.orcid}`);
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
                    <button class="button" type="button" data-action="mc-toggle-follow" data-contact-id="${escapeText(contactId)}">${followed ? "已关注" : "关注"}</button>
                    <button class="button" type="button" data-action="mc-open-materials" data-contact-id="${escapeText(contactId)}">材料 ${materialCount}</button>
                    <button class="button" type="button" data-action="mc-open-expert" data-contact-id="${escapeText(contactId)}">查看专家详情</button>
                `;
            }
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

        function renderMessage(message) {
            const direction = message.direction === "OUTBOUND" ? "OUTBOUND" : "INBOUND";
            const isInboundProcessing = message.source === "INBOUND_PROCESSING";
            const key = `${message.source}:${message.id}`;
            const account = message.accountCode || "";
            const who = isInboundProcessing
                ? `${SOURCE_LABELS.INBOUND_PROCESSING || "专家来信"} · ${timePart(message.eventAt)}${account ? ` · ${escapeText(account)}` : ""}`
                : `${direction === "OUTBOUND" ? "发出邮件" : "往来邮件"} · ${timePart(message.eventAt)}${account ? ` · ${escapeText(account)}` : ""}`;
            const statusBadge = renderStatusBadge(message, direction);
            const subject = message.subject || "(无主题)";
            const displayBody = String(message.cleanedBody || message.body || "").trim()
                ? escapeText(message.cleanedBody || message.body)
                : "";
            const attachmentHtml = Number(message.attachmentCount) > 0
                ? renderAttachmentSummary(message)
                : "";
            const extrasHtml = renderExtrasBlock(message, direction);
            const footerHtml = renderFooter(message, direction);
            return `
                <article class="mc-message" data-direction="${direction}" data-source="${escapeText(message.source)}" data-id="${escapeText(message.id)}" data-message-key="${escapeText(key)}">
                    <header><span>${who}</span>${statusBadge ? `<span>${statusBadge}</span>` : ""}</header>
                    <h3>${escapeText(subject)}</h3>
                    ${displayBody ? `<div class="mc-body">${displayBody}</div>` : ""}
                    ${attachmentHtml}
                    ${extrasHtml}
                    ${footerHtml ? `<footer>${footerHtml}</footer>` : ""}
                </article>
            `;
        }

        function renderStatusBadge(message, direction) {
            if (direction === "INBOUND") {
                if (isManualReview(message)) {
                    return '<span class="mc-badge" data-tone="pending">待处理</span>';
                }
                if (message.processStatus === "PROCESSED") {
                    return '<span class="mc-badge" data-tone="success">已处理</span>';
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

        function isManualReview(message) {
            return message.processStatus === "MANUAL_REVIEW";
        }

        function renderAttachmentSummary(message) {
            const count = Number(message.attachmentCount) || 0;
            const names = Array.isArray(message.firstAttachmentNames) ? message.firstAttachmentNames : [];
            const nameRows = names.slice(0, 3).map((name) =>
                `<div title="${escapeText(name)}">${escapeText(name)}</div>`
            ).join("");
            return `
                <details class="mc-mail-extras" data-role="attachment-extras">
                    <summary>附件 ${count} 份 · 仅文件信息</summary>
                    <div class="mc-attachment-names">${nameRows}<button class="button" type="button" data-action="mc-view-attachments" data-contact-id="${escapeText(message.contactId)}">查看全部附件</button></div>
                </details>
            `;
        }

        function renderExtrasBlock(message, direction) {
            const technicalParts = [];
            if (message.messageId) technicalParts.push(`<div><span>Message-ID</span><code>${escapeText(message.messageId)}</code></div>`);
            if (message.inReplyTo) technicalParts.push(`<div><span>In-Reply-To</span><code>${escapeText(message.inReplyTo)}</code></div>`);
            if (message.accountCode) technicalParts.push(`<div><span>账号</span><code>${escapeText(message.accountCode)}</code></div>`);
            const isInboundProcessing = message.source === "INBOUND_PROCESSING";
            const originalText = direction === "INBOUND" && String(message.body || "").trim()
                ? String(message.body)
                : "";
            const tagHost = isInboundProcessing
                ? `<div data-role="mail-tags" data-inbound-id="${escapeText(message.id)}"></div>`
                : "";
            const originalHtml = originalText
                ? `<details><summary>原始正文（含引用与签名）</summary>${typeof global.translatableBody === "function" ? global.translatableBody(originalText) : `<div class="pre">${escapeText(originalText)}</div>`}</details>`
                : "";
            const techHtml = technicalParts.length
                ? `<details class="mail-technical-detail"><summary>技术信息 · Message-ID / 账号</summary><div class="mail-technical-grid">${technicalParts.join("")}</div></details>`
                : "";
            if (!tagHost && !originalHtml && !techHtml) return "";
            const tagsLine = tagHost
                ? `<p>邮件标签：</p>${tagHost}`
                : "";
            return `
                <details class="mc-mail-extras" data-role="mail-extras" data-source="${escapeText(message.source)}" data-id="${escapeText(message.id)}">
                    <summary>原文、翻译、邮件标签与技术信息</summary>
                    <div>${originalHtml}${tagsLine}${techHtml}</div>
                </details>
            `;
        }

        function renderFooter(message, direction) {
            if (direction === "INBOUND" && isManualReview(message)) {
                return `<button class="button" type="button" data-action="mc-mark-resolved" data-message-key="${escapeText(`${message.source}:${message.id}`)}">标记已处理</button>`;
            }
            return "";
        }

        function loadMailTags(inboundId, containerEl) {
            if (!containerEl) return;
            const key = `inbound:${inboundId}`;
            if (instance.extras.has(key)) {
                containerEl.innerHTML = instance.extras.get(key);
                return;
            }
            hostApi()(`/api/inbound-summary/mails/${inboundId}/thread`).then((data) => {
                if (instance.disposed) return;
                const tags = (data && Array.isArray(data.tags)) ? data.tags : [];
                let html = tags.length === 0 ? '<p>暂无邮件标签</p>' : "";
                html += tags.map((tag) => {
                    if (typeof global.renderInboundTagChip === "function") {
                        return global.renderInboundTagChip(tag, { removable: false });
                    }
                    const label = tag && tag.label ? tag.label : "";
                    const cls = ["inbound-tag-chip"].concat(tag && tag.tagType === "QA" ? ["qa"] : ["custom"]).join(" ");
                    return `<span class="${cls}">${escapeText(label)}</span>`;
                }).join("");
                containerEl.innerHTML = html;
                instance.extras.set(key, html);
            }).catch(() => {
                if (!instance.disposed) containerEl.innerHTML = "";
            });
        }

        function timelineMarkup() {
            if (instance.conversation.error) {
                return `<div class="mc-error" role="alert">加载失败，请重试。<button class="button" type="button" data-action="mc-retry-conversation">重试</button></div>`;
            }
            const messages = instance.conversation.items || [];
            if (messages.length === 0) {
                return '<div class="mc-timeline"><div class="mc-empty">该专家暂无往来信件</div></div>';
            }
            const loadOlder = instance.conversation.hasMore
                ? '<button class="button mc-load-older" type="button" data-action="mc-load-older">加载更早信件</button>'
                : "";
            return `<div class="mc-timeline">${loadOlder}${daySeparatorsHtml()}</div>`;
        }

        function renderTimeline() {
            const body = conversationBody();
            if (!body) return;
            const scroll = body.querySelector(".mc-scroll");
            if (!scroll) return;
            const timeline = scroll.querySelector(".mc-timeline");
            if (timeline) {
                timeline.outerHTML = timelineMarkup();
                rebindDetails();
                return;
            }
            const box = scroll.querySelector(".mc-error, .mc-empty");
            if (box) {
                box.outerHTML = timelineMarkup();
                rebindDetails();
                return;
            }
            scroll.insertAdjacentHTML("afterbegin", timelineMarkup());
            rebindDetails();
        }

        function renderSettingsBlock() {
            const body = conversationBody();
            if (!body) return;
            const scroll = body.querySelector(".mc-scroll");
            if (!scroll) return;
            const contact = instance.conversation.contact || null;
            if (!contact) {
                const existing = scroll.querySelector(".mc-expert-settings");
                if (existing) existing.remove();
                return;
            }
            let settings = scroll.querySelector(".mc-expert-settings");
            const statusValue = contact.operatorStatus || "";
            const levelValue = contact.currentIndexLevel || "";
            const statusOptions = optionsFromCatalog(global.operatorStatusOptions, statusValue);
            const levelOptions = optionsFromCatalog(global.indexLevelOptions, levelValue);
            const settingsHtml = `
                <details class="mc-expert-settings" data-role="expert-settings"${instance.settings.open ? " open" : ""}>
                    <summary>专家状态、层级与标签</summary>
                    <div class="mc-settings-content">
                        <label><span>专家状态</span>
                            <select data-role="status-select" data-current-value="${escapeText(statusValue)}">${statusOptions}</select>
                        </label>
                        <label><span>专家层级</span>
                            <select data-role="level-select" data-current-value="${escapeText(levelValue)}">${levelOptions}</select>
                        </label>
                        <button class="button primary" type="button" data-action="mc-save-settings">保存变更</button>
                        <div data-role="expert-tags">${instance.settings.tagsState === "loading" ? "<p>正在加载专家标签…</p>" : ""}</div>
                    </div>
                </details>
            `;
            if (!settings) {
                scroll.insertAdjacentHTML("afterbegin", settingsHtml);
                settings = scroll.querySelector(".mc-expert-settings");
            } else {
                settings.outerHTML = settingsHtml;
            }
            if (instance.settings.tagsState === "idle") {
                loadExpertTags(contact);
            }
        }

        function optionsFromCatalog(catalog, selected) {
            if (typeof global.optionsFromArray === "function" && Array.isArray(catalog)) {
                return global.optionsFromArray(catalog, false, "请选择", selected || "");
            }
            const arr = Array.isArray(catalog) ? catalog : [];
            return arr.map((pair) => {
                const value = Array.isArray(pair) ? pair[0] : "";
                const label = Array.isArray(pair) ? (pair[1] || value) : String(pair || "");
                const sel = selected === value ? " selected" : "";
                return `<option value="${escapeText(value)}"${sel}>${escapeText(label)}</option>`;
            }).join("");
        }

        function loadExpertTags(contact) {
            instance.settings.tagsState = "loading";
            const tagsRootEl = host.querySelector ? host.querySelector("[data-role=expert-tags]") : null;
            if (tagsRootEl) tagsRootEl.innerHTML = "<p>正在加载专家标签…</p>";
            const orcidId = contact.orcidId || contact.expertOrcidId || "";
            const level = contact.currentIndexLevel || "CANDIDATE";
            if (!orcidId) {
                instance.settings.tagsState = "loaded";
                if (tagsRootEl) tagsRootEl.innerHTML = "<p>该专家无 ORCID 画像，标签不可用</p>";
                return;
            }
            const fetchTags = hostFn("fetchExpertTagsFromEs");
            const renderEditor = hostFn("renderMailboxExpertTagEditor");
            const request = fetchTags
                ? fetchTags(orcidId, level)
                : Promise.resolve({ found: false, tags: [] });
            request.then((data) => {
                if (instance.disposed || !tagsRootEl) return;
                instance.settings.tagsState = "loaded";
                if (renderEditor) {
                    tagsRootEl.innerHTML = renderEditor(
                        { orcidId, currentIndexLevel: level },
                        (data && data.tags) || [],
                        "mcChatSettingsTagEditor",
                        data && data.found === false
                    );
                } else {
                    tagsRootEl.innerHTML = "<p>暂无专家标签</p>";
                }
            }).catch(() => {
                if (instance.disposed || !tagsRootEl) return;
                instance.settings.tagsState = "loaded";
                tagsRootEl.innerHTML = "<p>标签加载失败</p>";
            });
        }

        function renderWorkbenchSection() {
            const body = conversationBody();
            if (!body) return;
            const scroll = body.querySelector(".mc-scroll");
            if (!scroll) return;
            const summary = instance.selectedSummary || {};
            const latestInbound = summary.latestInbound || null;
            const canGenerate = latestInbound && latestInbound.processingId != null;
            let existing = scroll.querySelector('.mc-section[data-section="workbench"]');
            if (existing) existing.remove();
            const hostHtml = canGenerate
                ? '<div data-trust-host></div>'
                : '<div class="mc-note">暂无专家来信，暂不能生成回复</div>';
            const openAttr = instance.workbench.open ? " open" : "";
            scroll.insertAdjacentHTML("beforeend", `
                <details class="mc-section" data-section="workbench"${openAttr}>
                    <summary>可信回复工作台 · 基于最新来信</summary>
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

        function renderManualSection() {
            const body = conversationBody();
            if (!body) return;
            const scroll = body.querySelector(".mc-scroll");
            if (!scroll) return;
            const summary = instance.selectedSummary || {};
            const latestInbound = summary.latestInbound || null;
            const mode = latestInbound && latestInbound.processingId != null ? "inbound" : "outboundOnly";
            const targetProcessingId = mode === "inbound" ? Number(latestInbound.processingId) : null;
            const targetAccount = mode === "inbound" ? (latestInbound.accountCode || "") : "";
            const targetKey = mode === "inbound" ? `${Number(instance.selectedContactId)}:${targetProcessingId}:${targetAccount}` : null;

            let existing = scroll.querySelector('.mc-section[data-section="manual"]');
            if (existing && instance.manual.mode === mode && instance.manual.targetKey === targetKey) {
                return; // 同一目标：保留用户正在编辑的内容
            }
            if (existing) existing.remove();

            const targetMsg = targetProcessingId != null ? latestInboundMessage() : null;
            const defaultSubject = targetMsg ? chatSubjectPrefill(targetMsg.subject) : "Re:";
            const draft = targetKey != null ? (instance.drafts.get(targetKey) || null) : null;

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

        function renderLogsBlock() {
            const body = conversationBody();
            if (!body) return;
            const scroll = body.querySelector(".mc-scroll");
            if (!scroll) return;
            let existing = scroll.querySelector('.mc-section[data-section="logs"]');
            if (existing) existing.remove();
            const contactId = Number(instance.selectedContactId);
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
            const scroll = host.querySelector ? host.querySelector(".mc-scroll") : null;
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

        function renderConversationEmpty() {
            const body = conversationBody();
            if (!body) return;
            body.innerHTML = '<div class="mc-empty">请选择左侧专家查看往来信件</div>';
        }

        function renderConversationContent() {
            const body = conversationBody();
            if (!body) return;
            if (instance.conversation.loading && (instance.conversation.items || []).length === 0) {
                renderConversationScaffold();
                return;
            }
            if (instance.conversation.error && (instance.conversation.items || []).length === 0) {
                const scrollHtml = `<div class="mc-scroll"><div class="mc-error" role="alert">加载失败，请重试。<button class="button" type="button" data-action="mc-retry-conversation">重试</button></div></div>`;
                body.innerHTML = `<header class="mc-header"><div class="mc-identity"><h2>${escapeText((instance.selectedSummary && instance.selectedSummary.name) || "-")}</h2></div><div class="mc-actions"></div></header>${scrollHtml}`;
                return;
            }
            if (instance.selectedContactId == null) {
                renderConversationEmpty();
                return;
            }
            body.innerHTML = `
                <header class="mc-header"><div class="mc-identity"></div><div class="mc-actions"></div></header>
                <div class="mc-scroll"></div>
            `;
            renderHeader();
            const scroll = body.querySelector(".mc-scroll");
            scroll.insertAdjacentHTML("afterbegin", timelineMarkup());
            renderSettingsBlock();
            renderWorkbenchSection();
            renderManualSection();
            renderLogsBlock();
            rebindDetails();
        }

        // --------------------------------------------------------------
        // 标记已处理（I-1：局部刷新，不动编辑器，无下载）
        // --------------------------------------------------------------

        function messageByKey(key) {
            const messages = instance.conversation.items || [];
            return messages.find((message) => `${message.source}:${message.id}` === key) || null;
        }

        function markResolvedByKey(key) {
            const message = messageByKey(key);
            if (!message || !isManualReview(message)) return;
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
                    // 列表 pending 计数与全局角标
                    const summary = instance.selectedSummary;
                    if (summary) {
                        summary.pendingCount = Math.max(0, (Number(summary.pendingCount) || 0) - 1);
                    }
                    const refreshBadge = hostFn("refreshUnmatchedBadge");
                    if (refreshBadge) refreshBadge();
                    renderTimeline();
                    renderPersonRowsQuiet();
                    // 新来信检查（可能最新来信变化）但不打断编辑器
                    checkInboundChangeQuiet();
                }).catch((err) => {
                    hostShowStatus(err && err.message ? err.message : "标记失败", "error");
                });
            }).catch((err) => {
                hostShowStatus(err && err.message ? err.message : "标记失败", "error");
            });
        }

        function renderPersonRowsQuiet() {
            const items = instance.list.items || [];
            const index = items.findIndex((item) => String(item.contactId) === String(instance.selectedContactId));
            if (index >= 0 && instance.selectedSummary) {
                const merged = Object.assign({}, items[index], {
                    pendingCount: instance.selectedSummary.pendingCount,
                    followed: instance.selectedSummary.followed === true
                });
                const next = items.slice();
                next[index] = merged;
                instance.list.items = next;
            }
            renderExpertList();
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
                items[index] = Object.assign({}, items[index], { followed: summary.followed === true });
                renderExpertList();
            } else {
                renderExpertList();
            }
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
            }).catch((err) => {
                if (instance.disposed) return;
                target.followed = current;
                setFollowControlsDisabled(false);
                renderFollowButtons();
                hostShowStatus((err && err.message) ? `关注操作失败：${err.message}` : "关注操作失败", "error");
            });
        }

        // --------------------------------------------------------------
        // 人工回复：草稿 / 采用 / 发送
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
            instance.drafts.set(key, {
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
                : (assembly && Array.isArray(assembly.canonicalFactIds) ? [] : []);
            const ragCorpusFingerprint = assembly && assembly.ragCorpusFingerprint
                ? assembly.ragCorpusFingerprint
                : "";
            // I-3：采用只是 UI 边界 —— 草稿记录当前原文与 QA 载荷，最终发送仍以服务端校验为准
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
                    instance.drafts.delete(key);
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
            // 刷新该专家消息窗口（包含刚发送的外发记录）
            const msgParams = new URLSearchParams();
            msgParams.set("limit", String(MESSAGE_LIMIT));
            const accountFilter = instance.options.filters && instance.options.filters.accountCode
                ? instance.options.filters.accountCode
                : "";
            if (accountFilter) msgParams.set("accountCode", accountFilter);
            instance.seq += 1;
            const mySeq = instance.seq;
            hostApi()(`/api/mail/mailbox/conversations/${contactId}/messages?${msgParams.toString()}`).then((msgData) => {
                if (instance.disposed || mySeq !== instance.seq) return;
                instance.conversation.items = (msgData && Array.isArray(msgData.items)) ? msgData.items : [];
                instance.conversation.nextBefore = (msgData && msgData.nextBefore) || null;
                instance.conversation.hasMore = !!(msgData && msgData.hasMore);
                renderTimeline();
                checkInboundChangeQuiet();
            }).catch(() => {});
            // 静默刷新列表（计数/最新消息变化）
            fetchList({ page: instance.list.page });
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
            const draft = currentTargetKey() ? instance.drafts.get(currentTargetKey()) : null;
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
            const draft = oldKey ? instance.drafts.get(oldKey) : null;
            const contactId = Number(instance.selectedContactId);
            const newKey = `${contactId}:${newProcessingId}:${newAccount}`;
            // 迁移草稿到新键（保留正文/QA；主题在 UI 按新来信重预填）
            if (draft) {
                instance.drafts.set(newKey, Object.assign({}, draft, {
                    subject: "",
                    updatedAt: new Date().toISOString()
                }));
                if (oldKey && oldKey !== newKey) instance.drafts.delete(oldKey);
            }
            instance.manual.targetProcessingId = Number(newProcessingId);
            instance.manual.targetAccountCode = newAccount || "";
            instance.manual.targetKey = newKey;
            instance.manual.qa = draft && draft.qa ? snapshotQa(draft.qa) : null;
            const composeEl = manualComposeEl();
            if (!composeEl) return;
            // 只重写主题输入与目标信息；正文 DOM 保留用户编辑
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
        // 设置保存 / 专家标签动作
        // --------------------------------------------------------------

        function saveSettings(buttonEl) {
            const body = conversationBody();
            if (!body || !body.querySelector) return;
            const statusSelect = body.querySelector('[data-role="status-select"]');
            const levelSelect = body.querySelector('[data-role="level-select"]');
            if (!statusSelect || !levelSelect) return;
            const newStatus = statusSelect.value || "";
            const newLevel = levelSelect.value || "";
            const currentStatus = statusSelect.dataset ? statusSelect.dataset.currentValue || "" : "";
            const currentLevel = levelSelect.dataset ? levelSelect.dataset.currentValue || "" : "";
            const statusChanged = newStatus && newStatus !== currentStatus;
            const levelChanged = newLevel && newLevel !== currentLevel;
            if (!statusChanged && !levelChanged) {
                hostShowStatus("专家状态和层级均未变化");
                return;
            }
            const contactId = Number(instance.selectedContactId);
            if (buttonEl) buttonEl.disabled = true;
            const requests = [];
            if (statusChanged) {
                requests.push(hostApi()(`/api/expert-contacts/${contactId}/operator-status`, {
                    method: "POST",
                    body: JSON.stringify({ operatorStatus: newStatus, operatorName: operatorName() })
                }));
            }
            if (levelChanged) {
                requests.push(hostApi()(`/api/expert-contacts/${contactId}/index-level`, {
                    method: "POST",
                    body: JSON.stringify({ targetLevel: newLevel, operatorName: operatorName() })
                }));
            }
            Promise.all(requests).then(() => {
                hostShowStatus("专家信息已更新", "ok");
                if (statusSelect.dataset) statusSelect.dataset.currentValue = newStatus;
                if (levelSelect.dataset) levelSelect.dataset.currentValue = newLevel;
                const contact = instance.conversation.contact;
                if (contact) {
                    if (statusChanged) contact.operatorStatus = newStatus;
                    if (levelChanged) contact.currentIndexLevel = newLevel;
                }
                if (buttonEl) buttonEl.disabled = false;
            }).catch((err) => {
                hostShowStatus(`专家信息更新失败：${err && err.message ? err.message : ""}`, "error");
                if (buttonEl) buttonEl.disabled = false;
            });
        }

        function handleExpertTagAction(element, action) {
            const editor = typeof element.closest === "function" ? element.closest(".expert-tag-editor") : null;
            if (!editor || !editor.dataset) return;
            const orcidId = editor.dataset.orcid;
            const level = editor.dataset.level;
            const editorId = editor.id || "mcChatSettingsTagEditor";
            if (!orcidId) return;
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
                        if (updateEditor) updateEditor(orcidId, tags, level, editorId);
                        hostShowStatus("标签已添加", "ok");
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
                    if (updateEditor) updateEditor(orcidId, tags, level, editorId);
                    hostShowStatus("标签已删除", "ok");
                }).catch((err) => {
                    hostShowStatus(err && err.message ? err.message : "标签删除失败", "error");
                });
            }
        }

        // --------------------------------------------------------------
        // 材料抽屉（child-08 同一 store）
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
        // 事件（root 委托；unmount 时逐对解绑）
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
            if (!button) return;
            const action = button.dataset ? button.dataset.action : "";
            const data = button.dataset || {};
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
                instance.list.page = 0;
                syncChipButtons();
                loadList();
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
            if (action === "mc-save-settings") {
                saveSettings(button);
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
        }

        function onInput(event) {
            if (instance.disposed) return;
            const target = event.target;
            if (!target) return;
            const searchInput = host.querySelector ? host.querySelector('.mc-list-tools input[type="search"]') : null;
            if (searchInput && target === searchInput) {
                clearTimeout(instance.searchTimer);
                const value = target.value || "";
                instance.searchTimer = setTimeout(() => {
                    instance.searchTimer = null;
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

        // details 'toggle' 不冒泡：渲染后为节点直接绑定（S-5 焦点/键盘不重建）
        function handleDetailsToggle(event) {
            if (instance.disposed) return;
            const target = event.target;
            if (!target || target.nodeType !== 1) return;
            if (target.classList && target.classList.contains("mc-expert-settings")) {
                instance.settings.open = target.open === true;
                return;
            }
            if (target.matches && target.matches('.mc-section[data-section="workbench"]')) {
                instance.workbench.open = target.open === true;
                if (target.open) ensureWorkbenchMounted();
                return;
            }
            if (target.matches && target.matches('.mc-section[data-section="logs"]')) {
                if (target.open) loadLogs();
                return;
            }
            if (target.matches && target.matches('[data-role="mail-extras"]')) {
                if (target.open) {
                    const inboundId = target.dataset ? target.dataset.id : "";
                    const source = target.dataset ? target.dataset.source : "";
                    if (source === "INBOUND_PROCESSING" && inboundId != null && target.querySelector) {
                        const tagsRoot = target.querySelector('[data-role="mail-tags"]');
                        if (tagsRoot && !tagsRoot.innerHTML.trim()) {
                            loadMailTags(inboundId, tagsRoot);
                        }
                    }
                }
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
            const contactId = Number(instance.selectedContactId);
            const params = new URLSearchParams();
            params.set("limit", String(MESSAGE_LIMIT));
            params.set("before", instance.conversation.nextBefore);
            const accountFilter = instance.options.filters && instance.options.filters.accountCode
                ? instance.options.filters.accountCode
                : "";
            if (accountFilter) params.set("accountCode", accountFilter);
            instance.conversation.loading = true;
            hostApi()(`/api/mail/mailbox/conversations/${contactId}/messages?${params.toString()}`).then((data) => {
                if (instance.disposed) return;
                const older = (data && Array.isArray(data.items)) ? data.items : [];
                const known = new Set((instance.conversation.items || []).map((message) => `${message.source}:${message.id}`));
                const merged = older.filter((message) => !known.has(`${message.source}:${message.id}`))
                    .concat(instance.conversation.items || []);
                instance.conversation.items = merged;
                instance.conversation.nextBefore = (data && data.nextBefore) || null;
                instance.conversation.hasMore = !!(data && data.hasMore);
                instance.conversation.loading = false;
                renderTimeline();
            }).catch((err) => {
                if (instance.disposed) return;
                instance.conversation.loading = false;
                hostShowStatus(`加载更早信件失败：${err && err.message ? err.message : ""}`, "error");
            });
        }

        function applyOptions(options) {
            const next = options || {};
            const filters = next.filters || {};
            instance.options = Object.assign({}, instance.options, next, { filters: Object.assign({}, instance.options.filters || {}, filters || {}) });
            if (next.focus) {
                instance.options.focus = { contactId: next.focus.contactId, email: next.focus.email || "" };
            }
            if (filters && typeof filters.pendingOnly === "boolean") {
                if (filters.pendingOnly && instance.chip !== CHIP_PENDING) {
                    instance.chip = CHIP_PENDING;
                    syncChipButtons();
                } else if (!filters.pendingOnly && instance.chip === CHIP_PENDING) {
                    instance.chip = CHIP_ALL;
                    syncChipButtons();
                }
            }
            return next;
        }

        // --------------------------------------------------------------
        // 实例 API
        // --------------------------------------------------------------

        function unmount() {
            if (instance.disposed) return;
            instance.disposed = true;
            clearTimeout(instance.searchTimer);
            teardownConversationSubViews();
            handlers.forEach((pair) => host.removeEventListener(pair[0], pair[1]));
            handlers.length = 0;
            if (instance.host) instance.host.innerHTML = "";
            instances.delete(instance.host);
        }

        function refresh() {
            if (instance.disposed) return;
            instance.list.page = 0;
            return loadList();
        }

        function attach() {
            listen("click", onClick);
            listen("input", onInput);
        }

        // 组装
        renderSkeleton();
        attach();
        if (!(options && options.focus && options.focus.contactId != null)) {
            renderConversationEmpty();
        }
        return {
            instance,
            applyOptions,
            refresh,
            loadList,
            unmount
        };
    }

    // ------------------------------------------------------------------
    // 公共 API（mount 同 host 再次调用 = 刷新语义，不重复建 DOM）
    // ------------------------------------------------------------------

    function mount(host, options) {
        if (!host) throw new Error("MailboxChat.mount requires a host element");
        const existing = instances.get(host);
        if (existing) {
            if (typeof existing.applyOptions === "function") existing.applyOptions(options || {});
            if (typeof existing.loadList === "function") existing.loadList();
            return existing;
        }
        const controller = createInstance(host, options || {});
        const api = {
            applyOptions: (next) => { if (controller) controller.applyOptions(next); },
            refresh: () => { if (controller) return controller.refresh(); return undefined; },
            loadList: () => { if (controller) return controller.loadList(); return undefined; },
            unmount: () => { if (controller) controller.unmount(); }
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

    global.MailboxChat = Object.freeze({
        mount,
        unmount,
        version: VERSION
    });
})(typeof window !== "undefined" ? window : (typeof globalThis !== "undefined" ? globalThis : this));
