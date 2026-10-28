/*
 * user1 只读邮件视图（fast-p 03 · I-2/I-3/I-4 · S-1）。
 *
 * 独立模块，不 mount MailboxChat / 工作台 / 专家材料 / 联系人详情，也不读写任何
 * localStorage/sessionStorage。当前正文只存在于本实例内存：切专家、刷新、
 * focus/visible、翻更早页都重新请求后端；403 立即清空正文与分页 cursor，
 * 401 交由宿主 api 的既有 handleAuthResponse 收口。
 *
 * 唯一导出：window.MailboxReadOnly = { mount(host, { api, sessionUser }), unmount(host) }。
 * mount 返回的 handle 只暴露 refresh（重新读取列表与已选专家）与 unmount。
 */
(function (global) {
    "use strict";

    var LIST_PAGE_SIZE = 20;
    var DETAIL_PAGE_LIMIT = 50;
    var REQUEST_TIMEOUT_MS = 15000;
    var LIST_PATH = "/api/mail/mailbox/conversations";

    // S-1 固定文案：全部经 textContent 输出，绝不拼 HTML。
    var COPY = {
        grantEmpty: "暂无已授权的专家邮件",
        searchEmpty: "没有匹配的已授权专家",
        noSelection: "请选择左侧专家",
        forbidden: "暂无权限查看该专家邮件",
        network: "加载失败，请刷新重试",
        loading: "加载中…",
        noMail: "暂无往来邮件"
    };

    var instances = new Map();

    function makeNode(doc, tag, className) {
        var node = doc.createElement(tag);
        if (className) node.className = className;
        return node;
    }

    function setText(node, text) {
        node.textContent = text === null || text === undefined ? "" : String(text);
    }

    function statusNode(doc, className, text, role) {
        var node = makeNode(doc, "p", className);
        if (role) node.setAttribute("role", role);
        setText(node, text);
        return node;
    }

    function buttonNode(doc, action, label, className) {
        var node = makeNode(doc, "button", className || "button");
        node.setAttribute("type", "button");
        node.dataset.action = action;
        setText(node, label);
        return node;
    }

    function formatTime(value) {
        var text = value === null || value === undefined ? "" : String(value);
        var match = /^(\d{4}-\d{2}-\d{2})T(\d{2}:\d{2})/.exec(text);
        return match ? match[1] + " " + match[2] : text;
    }

    function directionLabel(direction) {
        if (direction === "INBOUND") return "收件";
        if (direction === "OUTBOUND") return "发件";
        return direction === null || direction === undefined ? "" : String(direction);
    }

    function messageBody(message) {
        var cleaned = message && message.cleanedBody;
        if (typeof cleaned === "string" && cleaned.trim() !== "") return cleaned;
        var body = message && message.body;
        return typeof body === "string" ? body : "";
    }

    function messageKey(message) {
        return String(message && message.source) + ":" + String(message && message.id);
    }

    function latestSubject(item) {
        var latest = item && item.latestMessage;
        return latest && typeof latest.subject === "string" ? latest.subject : "";
    }

    function normalizeList(data) {
        var items = data && Array.isArray(data.items) ? data.items : [];
        var total = Number(data && data.total);
        return {
            items: items,
            total: Number.isFinite(total) ? total : items.length
        };
    }

    function mergeMessages(existing, incoming) {
        var seen = new Set();
        var merged = [];
        var all = (existing || []).concat(Array.isArray(incoming) ? incoming : []);
        all.forEach(function (message) {
            var key = messageKey(message);
            if (seen.has(key)) return;
            seen.add(key);
            merged.push(message);
        });
        // 时间线正序；服务端窗口是倒序返回，但同刻消息保持原有相对顺序（稳定排序）。
        merged.sort(function (left, right) {
            return String(left && left.eventAt).localeCompare(String(right && right.eventAt));
        });
        return merged;
    }

    function createInstance(host, options) {
        var doc = host.ownerDocument || global.document;
        var api = options.api;
        var sessionUser = options.sessionUser || {};

        var destroyed = false;
        var generation = 0;
        var inflight = new Set();
        var reloadPromise = null;

        var listPage = 0;
        var listQuery = "";
        var listItems = [];
        var listTotal = 0;
        var listLoading = false;
        var listFailed = false;
        var listSeq = 0;

        var selected = null;
        var detailItems = [];
        var detailNextBefore = null;
        var detailHasMore = false;
        var detailStatus = "idle"; // idle | loading | loadingOlder | ready | forbidden | error
        var detailSeq = 0;

        // ---- S-1 DOM 骨架（静态壳一次构建，动态文案全部 textContent） ----------------
        var root = makeNode(doc, "div", "mail-chat");
        root.setAttribute("data-role", "readonly-mailbox");

        var aside = makeNode(doc, "aside", "mc-experts");
        aside.setAttribute("aria-label", "已授权专家");

        var tools = makeNode(doc, "div", "mc-list-tools");
        var searchRow = makeNode(doc, "div", "mc-search-row");
        var searchInput = makeNode(doc, "input");
        searchInput.setAttribute("type", "search");
        searchInput.setAttribute("aria-label", "搜索已授权专家");
        searchInput.setAttribute("placeholder", "搜索姓名或邮箱");
        var searchButton = buttonNode(doc, "search", "搜索");
        searchRow.appendChild(searchInput);
        searchRow.appendChild(searchButton);
        var refreshButton = buttonNode(doc, "refresh", "刷新");
        var note = makeNode(doc, "p", "mc-note");
        setText(note, "只读：仅显示管理员授权的专家邮件");
        tools.appendChild(searchRow);
        tools.appendChild(refreshButton);
        tools.appendChild(note);

        var expertList = makeNode(doc, "div", "mc-expert-list");

        var pager = makeNode(doc, "div", "mc-pager");
        var previousButton = buttonNode(doc, "previous", "上一页");
        var pagerLabel = makeNode(doc, "span");
        pagerLabel.setAttribute("aria-live", "polite");
        setText(pagerLabel, "第 1 页，共 0 位专家");
        var nextButton = buttonNode(doc, "next", "下一页");
        pager.appendChild(previousButton);
        pager.appendChild(pagerLabel);
        pager.appendChild(nextButton);

        aside.appendChild(tools);
        aside.appendChild(expertList);
        aside.appendChild(pager);

        var conversation = makeNode(doc, "section", "mc-conversation");
        conversation.setAttribute("aria-label", "往来邮件");
        var header = makeNode(doc, "header", "mc-header");
        var identity = makeNode(doc, "div", "mc-identity");
        var identityName = makeNode(doc, "h2");
        var identityEmail = makeNode(doc, "p");
        identity.appendChild(identityName);
        identity.appendChild(identityEmail);
        var readOnlyBadge = makeNode(doc, "span", "mc-badge");
        setText(readOnlyBadge, "只读");
        header.appendChild(identity);
        header.appendChild(readOnlyBadge);
        var scroll = makeNode(doc, "div", "mc-scroll");
        conversation.appendChild(header);
        conversation.appendChild(scroll);

        root.appendChild(aside);
        root.appendChild(conversation);

        // ---- 请求（AbortController + 15s 超时；signal 沿既有 api 传递） -------------
        function requestJson(path) {
            var controller = new AbortController();
            var timedOut = false;
            var timer = setTimeout(function () {
                timedOut = true;
                controller.abort();
            }, REQUEST_TIMEOUT_MS);
            inflight.add(controller);
            var settle = function () {
                clearTimeout(timer);
                inflight.delete(controller);
            };
            return Promise.resolve()
                .then(function () {
                    return api(path, { method: "GET", cache: "no-store", signal: controller.signal });
                })
                .then(function (data) {
                    settle();
                    return data;
                }, function (error) {
                    settle();
                    // 跨 realm 的 Error 不保证 instanceof，保留原始错误对象上的 status/data。
                    var failure = error && typeof error === "object" ? error : new Error(String(error));
                    failure.timedOut = timedOut;
                    throw failure;
                });
        }

        function isStale(gen, seq, seqRef) {
            return destroyed || gen !== generation || seq !== seqRef();
        }

        function failureKind(error) {
            var status = error && error.status;
            if (status === 401) return "session";
            if (status === 403) return "forbidden";
            return "network";
        }

        function currentListSeq() { return listSeq; }
        function currentDetailSeq() { return detailSeq; }

        function listUrl() {
            var query = "?page=" + encodeURIComponent(String(listPage)) + "&size=" + LIST_PAGE_SIZE;
            if (listQuery) query += "&q=" + encodeURIComponent(listQuery);
            return LIST_PATH + query;
        }

        function detailUrl(contactId, before) {
            var query = "?limit=" + DETAIL_PAGE_LIMIT;
            if (before) query += "&before=" + encodeURIComponent(before);
            return LIST_PATH + "/" + encodeURIComponent(String(contactId)) + "/messages" + query;
        }

        // ---- 列表渲染 --------------------------------------------------------------
        function renderList() {
            expertList.replaceChildren();
            if (listLoading) {
                expertList.appendChild(statusNode(doc, "mc-empty", COPY.loading));
                updatePager();
                return;
            }
            if (listFailed) {
                expertList.appendChild(statusNode(doc, "mc-error", COPY.network, "alert"));
                updatePager();
                return;
            }
            if (!listItems.length) {
                expertList.appendChild(statusNode(doc, "mc-empty", listQuery ? COPY.searchEmpty : COPY.grantEmpty));
                updatePager();
                return;
            }
            listItems.forEach(function (item) {
                var person = makeNode(doc, "div", "mc-person");
                person.dataset.active = selected && String(selected.contactId) === String(item.contactId) ? "true" : "false";
                var main = makeNode(doc, "button", "mc-person-main");
                main.setAttribute("type", "button");
                main.dataset.action = "select-expert";
                main.dataset.contactId = String(item.contactId);
                var name = makeNode(doc, "strong");
                setText(name, item.name || item.email || "");
                var email = makeNode(doc, "small");
                setText(email, item.email || "");
                var subject = makeNode(doc, "small");
                setText(subject, latestSubject(item));
                main.appendChild(name);
                main.appendChild(email);
                main.appendChild(subject);
                person.appendChild(main);
                expertList.appendChild(person);
            });
            updatePager();
        }

        function updatePager() {
            setText(pagerLabel, "第 " + (listPage + 1) + " 页，共 " + listTotal + " 位专家");
            previousButton.disabled = listLoading || listPage <= 0;
            nextButton.disabled = listLoading || (listPage + 1) * LIST_PAGE_SIZE >= listTotal;
        }

        // ---- 会话渲染 --------------------------------------------------------------
        function renderIdentity() {
            setText(identityName, selected ? (selected.name || selected.email || "") : "");
            setText(identityEmail, selected ? (selected.email || "") : "");
        }

        function renderConversation() {
            scroll.replaceChildren();
            renderIdentity();
            if (!selected) {
                scroll.appendChild(statusNode(doc, "mc-empty", COPY.noSelection));
                return;
            }
            if (detailStatus === "loading") {
                scroll.appendChild(statusNode(doc, "mc-empty", COPY.loading));
                return;
            }
            if (detailStatus === "forbidden") {
                scroll.appendChild(statusNode(doc, "mc-error", COPY.forbidden, "alert"));
                return;
            }
            if (detailStatus === "error") {
                scroll.appendChild(statusNode(doc, "mc-error", COPY.network, "alert"));
                return;
            }
            var timeline = makeNode(doc, "div", "mc-timeline");
            if (detailHasMore) {
                var older = buttonNode(doc, "older", "加载更早邮件", "mc-text-button mc-load-older");
                older.disabled = detailStatus === "loadingOlder";
                timeline.appendChild(older);
            }
            if (!detailItems.length) {
                timeline.appendChild(statusNode(doc, "mc-empty", COPY.noMail));
            }
            detailItems.forEach(function (message) {
                var article = makeNode(doc, "article", "mc-message");
                article.dataset.direction = message && message.direction ? String(message.direction) : "";
                var head = makeNode(doc, "header");
                var direction = makeNode(doc, "span");
                setText(direction, directionLabel(message && message.direction));
                var time = makeNode(doc, "time");
                setText(time, formatTime(message && message.eventAt));
                head.appendChild(direction);
                head.appendChild(time);
                var subject = makeNode(doc, "h3");
                setText(subject, message && message.subject ? message.subject : "");
                var body = makeNode(doc, "div", "mc-body");
                setText(body, messageBody(message));
                article.appendChild(head);
                article.appendChild(subject);
                article.appendChild(body);
                timeline.appendChild(article);
            });
            scroll.appendChild(timeline);
        }

        function scrollToLatest() {
            if (!scroll) return;
            scroll.scrollTop = scroll.scrollHeight;
        }

        // ---- 读取 ------------------------------------------------------------------
        function loadList() {
            var gen = generation;
            var seq = ++listSeq;
            listLoading = true;
            listFailed = false;
            renderList();
            return requestJson(listUrl()).then(function (data) {
                if (isStale(gen, seq, currentListSeq)) return;
                var normalized = normalizeList(data);
                listItems = normalized.items;
                listTotal = normalized.total;
                listLoading = false;
                listFailed = false;
                renderList();
            }, function (error) {
                if (isStale(gen, seq, currentListSeq)) return;
                listLoading = false;
                listItems = [];
                listTotal = 0;
                listFailed = failureKind(error) !== "session";
                renderList();
            });
        }

        function loadSelectedDetail() {
            if (!selected) return Promise.resolve();
            var gen = generation;
            var seq = ++detailSeq;
            var contactId = selected.contactId;
            // 刷新/重选先清正文：任何失败都不能回退到旧内容（I-3）。
            detailItems = [];
            detailNextBefore = null;
            detailHasMore = false;
            detailStatus = "loading";
            renderConversation();
            return requestJson(detailUrl(contactId)).then(function (data) {
                if (isStale(gen, seq, currentDetailSeq)) return;
                if (!selected || String(selected.contactId) !== String(contactId)) return;
                detailItems = mergeMessages([], data && data.items);
                detailNextBefore = data && data.nextBefore ? data.nextBefore : null;
                detailHasMore = Boolean(data && data.hasMore) && Boolean(detailNextBefore);
                detailStatus = "ready";
                renderConversation();
                scrollToLatest();
            }, function (error) {
                if (isStale(gen, seq, currentDetailSeq)) return;
                if (!selected || String(selected.contactId) !== String(contactId)) return;
                clearDetailBody();
                if (failureKind(error) === "session") return;
                detailStatus = failureKind(error) === "forbidden" ? "forbidden" : "error";
                renderConversation();
            });
        }

        function loadOlder() {
            if (!selected || !detailHasMore || !detailNextBefore) return Promise.resolve();
            if (detailStatus === "loading" || detailStatus === "loadingOlder") return Promise.resolve();
            var gen = generation;
            var seq = ++detailSeq;
            var contactId = selected.contactId;
            var before = detailNextBefore;
            var previousScrollTop = scroll.scrollTop;
            var previousScrollHeight = scroll.scrollHeight;
            detailStatus = "loadingOlder";
            renderConversation();
            return requestJson(detailUrl(contactId, before)).then(function (data) {
                if (isStale(gen, seq, currentDetailSeq)) return;
                if (!selected || String(selected.contactId) !== String(contactId)) return;
                detailItems = mergeMessages(detailItems, data && data.items);
                detailNextBefore = data && data.nextBefore ? data.nextBefore : null;
                detailHasMore = Boolean(data && data.hasMore) && Boolean(detailNextBefore);
                detailStatus = "ready";
                renderConversation();
                // 保留阅读位置：新增内容在上方，滚动补偿等于高度差。
                scroll.scrollTop = previousScrollTop + (scroll.scrollHeight - previousScrollHeight);
            }, function (error) {
                if (isStale(gen, seq, currentDetailSeq)) return;
                if (!selected || String(selected.contactId) !== String(contactId)) return;
                clearDetailBody();
                if (failureKind(error) === "session") return;
                detailStatus = failureKind(error) === "forbidden" ? "forbidden" : "error";
                renderConversation();
            });
        }

        function clearDetailBody() {
            detailItems = [];
            detailNextBefore = null;
            detailHasMore = false;
        }

        function clearSelection() {
            selected = null;
            detailSeq += 1;
            clearDetailBody();
            detailStatus = "idle";
            renderList();
            renderConversation();
        }

        function selectExpert(contactId) {
            var item = null;
            listItems.forEach(function (candidate) {
                if (String(candidate.contactId) === String(contactId)) item = candidate;
            });
            selected = {
                contactId: contactId,
                name: item ? (item.name || "") : "",
                email: item ? (item.email || "") : ""
            };
            detailSeq += 1;
            clearDetailBody();
            renderList();
            renderConversation();
            return loadSelectedDetail();
        }

        function reloadAll() {
            if (destroyed) return Promise.resolve();
            var tasks = [loadList()];
            if (selected) tasks.push(loadSelectedDetail());
            else renderConversation();
            return Promise.all(tasks);
        }

        // focus / visibility 合并：同一时刻只保留一组在途请求。
        function scheduleReload() {
            if (destroyed) return Promise.resolve();
            if (reloadPromise) return reloadPromise;
            reloadPromise = reloadAll().then(function () {
                reloadPromise = null;
            }, function () {
                reloadPromise = null;
            });
            return reloadPromise;
        }

        // ---- 交互 ------------------------------------------------------------------
        function onClick(event) {
            var target = event && event.target;
            var actionNode = target && typeof target.closest === "function" ? target.closest("[data-action]") : null;
            if (!actionNode || !actionNode.dataset) return;
            var action = actionNode.dataset.action;
            if (action === "search") {
                listQuery = String(searchInput.value || "").trim();
                listPage = 0;
                clearSelection();
                return loadList();
            }
            if (action === "refresh") {
                // 刷新保留已选专家并重新读取列表与正文（正文先清后取，I-3）。
                return scheduleReload();
            }
            if (action === "select-expert") {
                return selectExpert(actionNode.dataset.contactId);
            }
            if (action === "previous") {
                if (listPage <= 0 || listLoading) return undefined;
                listPage -= 1;
                return loadList();
            }
            if (action === "next") {
                if (listLoading || (listPage + 1) * LIST_PAGE_SIZE >= listTotal) return undefined;
                listPage += 1;
                return loadList();
            }
            if (action === "older") {
                return loadOlder();
            }
            return undefined;
        }

        function onSearchKeydown(event) {
            if (event && event.key === "Enter") {
                event.preventDefault();
                listQuery = String(searchInput.value || "").trim();
                listPage = 0;
                clearSelection();
                loadList();
            }
        }

        function onFocus() {
            if (doc.hidden) return;
            scheduleReload();
        }

        function onVisibilityChange() {
            if (doc.hidden) return;
            scheduleReload();
        }

        function start() {
            root.addEventListener("click", onClick);
            searchInput.addEventListener("keydown", onSearchKeydown);
            global.addEventListener("focus", onFocus);
            if (typeof doc.addEventListener === "function") {
                doc.addEventListener("visibilitychange", onVisibilityChange);
            }
            host.replaceChildren(root);
            renderList();
            renderConversation();
            loadList();
        }

        function destroy() {
            if (destroyed) return;
            destroyed = true;
            generation += 1;
            inflight.forEach(function (controller) {
                try {
                    controller.abort();
                } catch (error) {
                    // abort 只用于终止在途读请求，失败无后续影响。
                }
            });
            inflight.clear();
            root.removeEventListener("click", onClick);
            searchInput.removeEventListener("keydown", onSearchKeydown);
            global.removeEventListener("focus", onFocus);
            if (typeof doc.removeEventListener === "function") {
                doc.removeEventListener("visibilitychange", onVisibilityChange);
            }
            clearDetailBody();
            detailStatus = "idle";
            selected = null;
            listItems = [];
            listTotal = 0;
            host.replaceChildren();
        }

        return {
            sessionUser: sessionUser,
            start: start,
            destroy: destroy,
            handle: {
                refresh: function () {
                    if (destroyed) return Promise.resolve();
                    return scheduleReload();
                },
                unmount: function () {
                    unmount(host);
                }
            }
        };
    }

    function mount(host, options) {
        if (!host || typeof host.replaceChildren !== "function") return null;
        unmount(host);
        var instance = createInstance(host, options || {});
        instances.set(host, instance);
        try {
            instance.start();
        } catch (error) {
            instances.delete(host);
            throw error;
        }
        return instance.handle;
    }

    function unmount(host) {
        var instance = instances.get(host);
        if (!instance) return;
        instances.delete(host);
        instance.destroy();
    }

    global.MailboxReadOnly = {
        mount: mount,
        unmount: unmount
    };
}(typeof window !== "undefined" ? window : globalThis));
