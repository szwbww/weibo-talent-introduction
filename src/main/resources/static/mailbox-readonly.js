/*
 * Restricted mailbox: READ review drafts, FULL delegated conversation.
 * The independent list never enters the administrator bootstrap.
 * All content lives only in the mounted instance; ownership and request
 * epochs isolate every response. READ history mail stays text-only.
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

    function appendExpertTags(doc, parent, tags) {
        if (!Array.isArray(tags)) {
            parent.appendChild(statusNode(doc, "mc-note", "标签暂不可用"));
            return;
        }
        if (!tags.length) return;
        var row = makeNode(doc, "div", "mc-tag-row");
        row.dataset.role = "readonly-expert-tags";
        tags.forEach(function (tag) {
            var value = String(tag == null ? "" : tag);
            var label = typeof expertTagLabels !== "undefined" && expertTagLabels &&
                Object.prototype.hasOwnProperty.call(expertTagLabels, value) ? expertTagLabels[value] : value;
            var chip = makeNode(doc, "span", "expert-tag");
            setText(chip, label);
            chip.setAttribute("title", label);
            row.appendChild(chip);
        });
        parent.appendChild(row);
    }

    function appendMailTags(doc, parent, message) {
        if (!message || message.source !== "INBOUND_PROCESSING" ||
                !Array.isArray(message.tags) || !message.tags.length) return;
        var row = makeNode(doc, "div", "mc-tag-row");
        row.dataset.role = "readonly-mail-tags";
        message.tags.forEach(function (tag) {
            var className = "inbound-tag-chip " + (tag && tag.tagType === "QA" ? "qa" : "custom");
            if (tag && tag.active === false) className += " inactive";
            var chip = makeNode(doc, "span", className);
            setText(chip, tag && tag.label);
            row.appendChild(chip);
        });
        parent.appendChild(row);
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
        var delegated = null;
        var pendingOnly = false;
        var listScope = "all";
        var draftTotalExperts = null;
        var pendingTotal = null;
        var pendingSeq = 0;
        var draftCountSeq = 0;

        function releaseDelegated() {
            if (delegated) delegated.unmount();
            delegated = null;
        }

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
        searchInput.setAttribute("placeholder", "搜索专家姓名");
        var searchButton = buttonNode(doc, "search", "搜索");
        searchRow.appendChild(searchInput);
        searchRow.appendChild(searchButton);
        var refreshButton = buttonNode(doc, "refresh", "↻");
        refreshButton.setAttribute("aria-label", "刷新");
        refreshButton.setAttribute("title", "刷新");
        var note = makeNode(doc, "p", "mc-note");
        setText(note, "仅显示管理员授权的专家邮件");
        tools.appendChild(searchRow);
        searchRow.appendChild(refreshButton);
        var scopes = makeNode(doc, "div", "mc-filters");
        scopes.setAttribute("aria-label", "专家会话范围");
        var allButton = buttonNode(doc, "reader-all", "全部", "mc-filter");
        var draftsButton = buttonNode(doc, "reader-drafts", "草稿 ", "mc-filter");
        var draftsCount = makeNode(doc, "span", "mailbox-suspend-count");
        draftsCount.dataset.role = "reader-draft-experts"; draftsButton.appendChild(draftsCount);
        var readButton = buttonNode(doc, "reader-read", "可读", "mc-filter");
        var fullButton = buttonNode(doc, "reader-full", "可写", "mc-filter");
        var pendingButton = buttonNode(doc, "reader-pending", "待处理 ", "mc-filter");
        var pendingCount = makeNode(doc, "span", "mailbox-suspend-count");
        pendingCount.dataset.role = "reader-pending-experts";
        pendingButton.appendChild(pendingCount);
        scopes.appendChild(allButton); scopes.appendChild(draftsButton); scopes.appendChild(readButton); scopes.appendChild(fullButton); scopes.appendChild(pendingButton);
        tools.appendChild(scopes);

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
        identity.appendChild(identityName);
        var readOnlyBadge = makeNode(doc, "span", "mc-badge");
        setText(readOnlyBadge, "可读 · 提交草稿");
        header.appendChild(identity);
        header.appendChild(readOnlyBadge);
        var scroll = makeNode(doc, "div", "mc-scroll");
        var messages = makeNode(doc, "div");
        scroll.appendChild(messages);
        conversation.appendChild(header);
        conversation.appendChild(scroll);

        root.appendChild(aside);
        root.appendChild(conversation);
        var delegatedHost = makeNode(doc, "div", "mc-delegated-host");
        function restoreReadDetail() {
            releaseDelegated();
            if (delegatedHost.parentNode) delegatedHost.parentNode.replaceChild(conversation, delegatedHost);
        }

        // Persistent composer: timeline refresh never replaces its native editing range.
        var REVIEW_PATH = "/api/mail/mailbox/review-drafts";
        var draft = null, requestedDraftId = null, revision = 0, savedRevision = 0, conflict = false;
        var reviewSeq = 0, reviewReadSeq = 0, historySeq = 0, eventsSeq = 0, navigationSeq = 0;
        var saving = null, busy = false, denied = false, createRequest = null;
        var draftItems = [], draftPage = 0, draftTotal = 0, eventItems = [], eventBefore = null;
        var eventDraftId = null, panel = null, commentText = "";
        var sanitizer = global.MailboxMeeting && global.MailboxMeeting.sanitizeDraftHtml;
        var review = makeNode(doc, "details", "mc-section mc-reader-review");
        review.setAttribute("open", "");
        var reviewSummary = makeNode(doc, "summary"); setText(reviewSummary, "回复草稿"); review.appendChild(reviewSummary);
        review.dataset.role = "review-drafts";
        var reviewContent = makeNode(doc, "div", "mc-section-content");
        var compose = makeNode(doc, "div", "mc-compose");
        compose.dataset.role = "review-compose";
        review.appendChild(reviewContent); reviewContent.appendChild(compose);
        scroll.appendChild(review);
        function reviewButton(parent, action, label, className, id) {
            var node = makeNode(doc, "button", className || "button");
            node.setAttribute("type", "button"); node.dataset.reviewAction = action;
            if (id !== undefined) node.dataset.draftId = String(id);
            setText(node, label); parent.appendChild(node); return node;
        }
        function roleNode(parent, tag, className, role) {
            var node = makeNode(doc, tag, className);
            node.dataset.role = role; parent.appendChild(node); return node;
        }
        var controls = makeNode(doc, "div", "mail-review-controls"); compose.appendChild(controls);
        var historyButton = reviewButton(controls, "history", "草稿历史", "mc-text-button");
        historyButton.setAttribute("aria-expanded", "false");
        var newButton = reviewButton(controls, "new", "＋ 新增草稿");
        var stateBadge = roleNode(reviewSummary, "span", "mc-badge", "review-state");
        var draftSelect = makeNode(doc, "select", "mail-review-select");
        draftSelect.setAttribute("aria-label", "选择回复草稿");
        controls.insertBefore(draftSelect, historyButton);
        draftSelect.addEventListener("change", function () {
            if (draftSelect.value) navigate(function () { return loadDraft(Number(draftSelect.value), false); });
        });
        var historyPanel = roleNode(compose, "div", "mail-review-panel", "review-history");
        var subjectLabel = makeNode(doc, "label"); setText(subjectLabel, "主题");
        var subjectInput = roleNode(subjectLabel, "input", "", "review-subject");
        subjectInput.setAttribute("aria-label", "草稿主题");
        subjectInput.setAttribute("readonly", "");
        var subjectDetails = makeNode(doc, "div", "mc-reader-subject");
        var subjectSummary = makeNode(doc, "span"); setText(subjectSummary, "沿用原邮件主题");
        subjectDetails.appendChild(subjectLabel); subjectDetails.appendChild(subjectSummary); compose.appendChild(subjectDetails);
        var stateHint = roleNode(compose, "p", "mc-reader-state-hint", "review-state-hint");
        var editorTools = makeNode(doc, "div", "mc-editor-tools"); compose.appendChild(editorTools);
        [["bold", "B"], ["italic", "I"], ["insertUnorderedList", "列表"], ["createLink", "链接"]].forEach(function (entry) {
            var node = makeNode(doc, "button", "button");
            node.setAttribute("type", "button"); node.dataset.reviewCommand = entry[0];
            setText(node, entry[1]); editorTools.appendChild(node);
        });
        var editor = roleNode(compose, "div", "mc-editor", "review-editor");
        editor.setAttribute("role", "textbox"); editor.setAttribute("aria-multiline", "true");
        editor.setAttribute("aria-label", "草稿正文");
        var reason = roleNode(compose, "p", "mc-note", "review-return-reason");
        var reviewError = roleNode(compose, "p", "mc-error", "review-error"); reviewError.setAttribute("role", "alert");
        var footer = makeNode(doc, "div", "mail-review-footer"); compose.appendChild(footer);
        var commentsButton = reviewButton(footer, "comments", "评论 0", "mc-text-button");
        reviewButton(footer, "events", "变动历史", "mc-text-button");
        var discussion = roleNode(compose, "div", "mail-review-panel", "review-discussion");
        var composeFooter = makeNode(doc, "div", "mc-compose-footer"); compose.appendChild(composeFooter);
        var saveState = roleNode(composeFooter, "span", "mc-reader-save-state", "review-save-state"); saveState.setAttribute("role", "status");
        composeFooter.appendChild(footer);
        var actions = makeNode(doc, "div", "mail-review-actions"); composeFooter.appendChild(actions);
        var deleteButton = reviewButton(actions, "delete", "删除草稿", "mc-text-button danger");
        var saveButton = reviewButton(actions, "save", "保存草稿");
        var withdrawButton = reviewButton(actions, "withdraw", "取消提交");
        var submitButton = reviewButton(actions, "submit", "提交审核", "button primary");
        var reloadButton = reviewButton(actions, "reload", "重新加载");

        function pending() { return delegated ? delegated.hasPendingDrafts() : revision !== savedRevision || Boolean(saving); }
        function editable() {
            return Boolean(draft && !draft.deleted && !denied && detailStatus === "ready" && !conflict && sanitizer
                && (draft.state === "DRAFT" || draft.state === "RETURNED"));
        }
        function reviewContext() {
            return { owner: sessionUser, gen: generation, contact: selected && String(selected.contactId),
                id: draft && draft.id, version: draft && draft.version, rev: revision, seq: reviewSeq };
        }
        function sameContext(ctx, checkVersion) {
            return !destroyed && !denied && ctx.owner === sessionUser && ctx.gen === generation
                && ctx.contact === (selected && String(selected.contactId)) && ctx.seq === reviewSeq
                && ctx.id === (draft && draft.id) && (!checkVersion || ctx.version === (draft && draft.version));
        }
        function showReviewError(text) { setText(reviewError, text); reviewError.hidden = !text; }
        function safeBody(node, html, text) {
            if (sanitizer) node.innerHTML = sanitizer(String(html || ""));
            else setText(node, text || "");
        }
        function stateLabel(item) {
            if (item && item.deleted) return "已删除";
            return { DRAFT: "未提交", RETURNED: "已退回", SUBMITTED: "已提交", APPROVED: "已审核" }[item && item.state] || "暂无草稿";
        }
        function renderReview() {
            review.hidden = !selected || denied || detailStatus !== "ready";
            historyPanel.hidden = panel !== "history";
            discussion.hidden = panel !== "comments" && panel !== "events";
            historyButton.setAttribute("aria-expanded", String(panel === "history"));
            setText(stateBadge, draft ? stateLabel(draft) : draftItems.some(function (item) { return !item.deleted; }) ? "未选择草稿" : "暂无草稿");
            subjectDetails.hidden = subjectLabel.hidden = editorTools.hidden = editor.hidden = footer.hidden = composeFooter.hidden = !draft;
            subjectInput.disabled = true;
            stateHint.hidden = !draft;
            setText(stateHint, draft ? ({ DRAFT: "提交后由管理员审核，通过前不会发送邮件。", SUBMITTED: "已提交，等待管理员审核。取消提交后可继续编辑。", RETURNED: "请按退回意见修改后重新提交。", APPROVED: "已审核，内容已锁定。审核通过不代表邮件已发送。" }[draft.state] || "") : "");
            setText(submitButton, draft && draft.state === "RETURNED" ? "重新提交" : "提交审核");
            draftSelect.replaceChildren();
            var choices = draftItems.filter(function (item) { return !item.deleted; });
            if (draft && !choices.some(function (item) { return item.id === draft.id; })) choices.unshift(draft);
            if (!draft) {
                var placeholder = makeNode(doc, "option"); placeholder.value = ""; placeholder.disabled = true;
                setText(placeholder, choices.length ? "选择已有草稿" : "暂无草稿"); draftSelect.appendChild(placeholder);
            }
            choices.forEach(function (item) {
                var option = makeNode(doc, "option"); option.value = String(item.id);
                setText(option, "草稿 #" + item.id + " · " + stateLabel(item)); draftSelect.appendChild(option);
            });
            draftSelect.value = draft ? String(draft.id) : ""; draftSelect.disabled = !choices.length || busy || Boolean(saving);
            editor.setAttribute("contenteditable", String(editable() && !busy));
            editorTools.hidden = !draft || !editable();
            Array.from(editorTools.children).forEach(function (node) { node.disabled = !editable() || busy; });
            reason.hidden = !draft || !draft.returnReason; setText(reason, draft && draft.returnReason);
            setText(commentsButton, "评论 " + (draft ? draft.commentCount : 0));
            deleteButton.hidden = !draft || draft.deleted || draft.state === "APPROVED";
            saveButton.hidden = submitButton.hidden = !draft || draft.deleted || !["DRAFT", "RETURNED"].includes(draft.state);
            withdrawButton.hidden = !draft || draft.deleted || draft.state !== "SUBMITTED";
            reloadButton.hidden = !conflict;
            Array.from(actions.children).forEach(function (node) { node.disabled = busy || Boolean(saving); });
            saveButton.disabled = submitButton.disabled = busy || Boolean(saving) || !editable();
            newButton.disabled = busy || Boolean(saving) || denied;
            setText(saveState, !draft ? "暂无草稿" : draft.state === "APPROVED" ? "已审核，不能修改"
                : saving ? "保存中…" : revision !== savedRevision ? "尚未保存" : "已保存");
        }
        function resetReview() {
            reviewSeq += 1; historySeq += 1; eventsSeq += 1;
            draft = null; requestedDraftId = null; revision = savedRevision = 0; conflict = false;
            saving = null; busy = false; createRequest = null; commentText = "";
            draftItems = []; draftPage = draftTotal = 0; eventItems = []; eventBefore = null;
            eventDraftId = null; panel = null;
            subjectInput.value = ""; setText(editor, ""); historyPanel.replaceChildren(); discussion.replaceChildren();
            showReviewError(""); renderReview();
        }
        function revokeAccess() {
            restoreReadDetail();
            generation += 1; denied = true; navigationSeq += 1;
            inflight.forEach(function (controller) { controller.abort(); });
            clearDetailBody(); detailSeq += 1; listSeq += 1;
            listItems = []; listTotal = 0; listLoading = false;
            if (selected) selected.expertTags = [];
            resetReview(); detailStatus = "forbidden"; renderList(); renderConversation();
        }
        function acceptDraft(data, replace) {
            draft = data;
            if (replace) {
                subjectInput.value = data.subject || ""; safeBody(editor, data.html, data.text);
                revision = savedRevision = 0;
            }
            conflict = false; showReviewError(sanitizer ? "" : "编辑组件加载失败"); renderReview();
            if (panel === "comments" || panel === "events") renderDiscussion();
        }
        function reviewFailure(error, operation) {
            if (error && (error.status === 401 || error.status === 403)) return;
            if (error && error.status === 409) conflict = true;
            var detail = error && error.data && error.data.message || error && error.message || "请稍后重试";
            showReviewError(conflict ? "草稿已被更新，请重新加载" : (error && error.reviewOperation || operation || "操作") + "失败：" + detail); renderReview();
        }
        function loadDraft(id, preserve) {
            requestedDraftId = id;
            var ctx = reviewContext(), seq = ++reviewReadSeq;
            return requestJson(REVIEW_PATH + "/" + id).then(function (data) {
                if (!sameContext(ctx, true) || seq !== reviewReadSeq) return;
                if (preserve && pending()) {
                    if (!draft || data.version !== draft.version || data.state !== draft.state || data.deleted) {
                        // Retain the local version/body for copying, but immediately honor a server lock.
                        draft = Object.assign({}, draft, { state: data.state, deleted: data.deleted, returnReason: data.returnReason });
                        conflict = true; showReviewError("草稿已被更新，请重新加载"); renderReview();
                        renderDiscussion();
                    }
                    if (draft && data.version === draft.version) {
                        draft = Object.assign({}, draft, { commentCount: data.commentCount });
                        renderReview();
                        if (panel === "comments" || panel === "events") loadEvents(data.id, false);
                    }
                    return;
                }
                acceptDraft(data, true);
                if (panel === "comments" || panel === "events") loadEvents(data.id, false);
            }, function (error) {
                if (sameContext(ctx, true) && seq === reviewReadSeq) {
                    conflict = true; reviewFailure(error);
                }
            });
        }
        function saveDraft() {
            if (saving) return saving.then(function () { return saveDraft(); });
            if (!draft || revision === savedRevision) return Promise.resolve();
            if (!editable()) return Promise.reject(new Error("草稿已被更新，请重新加载"));
            var ctx = reviewContext();
            var body = { expectedVersion: draft.version, subject: subjectInput.value, html: editor.innerHTML };
            var promise = requestJson(REVIEW_PATH + "/" + draft.id, "PUT", body).then(function (data) {
                if (!sameContext(ctx, true)) return;
                draft = data; savedRevision = ctx.rev;
                if (revision === ctx.rev) { subjectInput.value = data.subject; safeBody(editor, data.html, data.text); }
                showReviewError("");
                refreshDraftList();
            }, function (error) {
                if (error) error.reviewOperation = "保存草稿";
                if (sameContext(ctx, true)) reviewFailure(error, "保存草稿");
                throw error;
            }).finally(function () { if (saving === promise) { saving = null; renderReview(); } });
            saving = promise; renderReview(); return promise;
        }
        function flushDrafts() {
            if (delegated) return delegated.flushDrafts();
            if (!pending()) return Promise.resolve();
            var ctx = reviewContext();
            return saveDraft().then(function () {
                if (!sameContext(ctx, false)) {
                    if (destroyed || denied) throw new Error("暂无权限查看该专家邮件");
                    return;
                }
                if (pending()) return flushDrafts();
            }).catch(function (error) {
                if (sameContext(ctx, false)) reviewFailure(error);
                throw error;
            });
        }
        function navigate(action) {
            var seq = ++navigationSeq, ctx = reviewContext();
            return flushDrafts().then(function () {
                if (seq !== navigationSeq || !sameContext(ctx, false)) return;
                return action();
            }).catch(function (error) { if (sameContext(ctx, false)) reviewFailure(error); });
        }
        function renderHistory() {
            historyPanel.replaceChildren();
            if (!draftItems.length) historyPanel.appendChild(statusNode(doc, "mc-note", "暂无草稿"));
            draftItems.forEach(function (item) {
                var entry = makeNode(doc, "article", "mail-review-entry"), label = makeNode(doc, "span");
                setText(label, "草稿 #" + item.id + " · " + stateLabel(item) + " · " + formatTime(item.updatedAt));
                entry.appendChild(label);
                if (!item.deleted) reviewButton(entry, "select", "打开草稿", "mc-text-button", item.id);
                reviewButton(entry, "events", "变动历史", "mc-text-button", item.id); historyPanel.appendChild(entry);
            });
            if (draftItems.length < draftTotal) reviewButton(historyPanel, "more-drafts", "加载更多草稿", "mc-text-button");
        }
        function loadDrafts(more) {
            if (!selected || denied) return Promise.resolve();
            var ctx = reviewContext(), seq = ++historySeq, page = more ? draftPage + 1 : 0;
            return requestJson(REVIEW_PATH + "?contactId=" + ctx.contact + "&includeDeleted=true&page=" + page + "&size=20").then(function (data) {
                if (!sameContext(ctx, true) || seq !== historySeq) return;
                draftItems = more ? draftItems.concat(data.items || []) : (data.items || []);
                draftTotal = data.total; draftPage = page; renderHistory(); renderReview();
            }, function (error) { if (sameContext(ctx, true) && seq === historySeq) reviewFailure(error); });
        }
        function renderDiscussion() {
            discussion.replaceChildren();
            eventItems.forEach(function (item) {
                if (panel === "comments" && item.eventType !== "COMMENT") return;
                var entry = makeNode(doc, "article", "mail-review-entry"), title = makeNode(doc, "strong");
                setText(title, item.actorUsername + " · " + formatTime(item.createdAt) + " · " + item.eventType);
                entry.appendChild(title);
                var payload = item.payload || {}, body = makeNode(doc, "div", "mc-body");
                setText(body, payload.text || payload.reason || payload.subject || ""); entry.appendChild(body);
                if (panel === "events" && (payload.html !== undefined || payload.text !== undefined)) {
                    var versionBody = roleNode(entry, "div", "mc-body", "review-version-body"); safeBody(versionBody, payload.html, payload.text);
                }
                discussion.appendChild(entry);
            });
            if (draft && eventDraftId === draft.id && !draft.deleted && draft.state !== "APPROVED") {
                var input = makeNode(doc, "textarea", "mail-review-input");
                input.setAttribute("aria-label", "评论内容"); input.setAttribute("maxlength", "2000");
                input.value = commentText; input.disabled = busy;
                input.addEventListener("input", function () { commentText = input.value; }); discussion.appendChild(input);
                var post = reviewButton(discussion, "comment", "发布评论"); post.disabled = busy || conflict;
            }
            if (eventBefore) reviewButton(discussion, "more-events", "加载更早历史", "mc-text-button");
        }
        function loadEvents(id, more) {
            var ctx = reviewContext(), seq = ++eventsSeq;
            if (!more) { eventItems = []; eventBefore = null; eventDraftId = Number(id); discussion.replaceChildren(); }
            var before = more && eventBefore ? "&beforeId=" + eventBefore : "";
            return requestJson(REVIEW_PATH + "/" + id + "/events?limit=20" + before).then(function (data) {
                if (!sameContext(ctx, true) || seq !== eventsSeq || eventDraftId !== Number(id)) return;
                var items = Array.isArray(data) ? data : [];
                eventItems = more ? eventItems.concat(items) : items;
                eventBefore = items.length === 20 ? items[items.length - 1].id : null; renderDiscussion();
            }, function (error) { if (sameContext(ctx, true) && seq === eventsSeq) reviewFailure(error); });
        }
        function createDraft() {
            return navigate(function () {
                if (busy || denied || !selected) return;
                if (!createRequest) createRequest = { contactId: Number(selected.contactId),
                    clientRequestId: global.crypto && global.crypto.randomUUID ? global.crypto.randomUUID()
                        : "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, function (part) {
                            var random = Math.floor(Math.random() * 16);
                            return (part === "x" ? random : (random & 3) | 8).toString(16);
                        }), subject: "", html: "" };
                var ctx = reviewContext(); busy = true; renderReview();
                return requestJson(REVIEW_PATH, "POST", createRequest).then(function (data) {
                    if (!sameContext(ctx, true)) return;
                    resetReview(); acceptDraft(data, true);
                    refreshDraftList();
                }, function (error) { if (sameContext(ctx, true)) reviewFailure(error); })
                    .finally(function () { if (sameContext(ctx, false)) { busy = false; renderReview(); } });
            });
        }
        function mutateDraft(action) {
            if (busy || saving || !draft || draft.deleted || draft.state === "APPROVED" || conflict) return Promise.resolve();
            busy = true; renderReview(); if (!discussion.hidden) renderDiscussion();
            var ctx = reviewContext();
            return (action === "submit" ? flushDrafts() : Promise.resolve()).then(function () {
                if (!sameContext(ctx, false)) return;
                ctx = reviewContext();
                var path = REVIEW_PATH + "/" + draft.id, body = { expectedVersion: draft.version }, method = "POST";
                if (action === "delete") { method = "DELETE"; path += "?expectedVersion=" + draft.version; body = undefined; }
                else path += "/" + (action === "comment" ? "comments" : action);
                if (action === "comment") body.text = commentText;
                return requestJson(path, method, body).then(function (data) {
                    if (!sameContext(ctx, true)) return;
                    if (action !== "comment") refreshDraftList();
                    if (action === "delete") { resetReview(); return loadDrafts(false); }
                    if (action === "comment") { commentText = ""; return loadDraft(draft.id, true); }
                    if (revision !== ctx.rev) {
                        draft = data; conflict = true;
                        showReviewError("草稿已被更新，请重新加载"); renderReview();
                    } else acceptDraft(data, true);
                });
            }).catch(function (error) { if (sameContext(ctx, false)) reviewFailure(error, {submit: "提交审核", withdraw: "取消提交", delete: "删除草稿", comment: "添加评论"}[action]); })
                .finally(function () {
                    if (sameContext(ctx, false)) { busy = false; renderReview(); if (!discussion.hidden) renderDiscussion(); }
                });
        }
        function onReviewInput() {
            if (!editable()) return;
            revision += 1; showReviewError(""); renderReview();
        }
        function refreshDraftList() {
            if (!destroyed && !denied) { loadList(); loadDraftCount(); }
        }
        function onBeforeUnload(event) {
            if (!pending()) return;
            event.preventDefault(); event.returnValue = "";
        }
        function onEditorPaste(event) {
            if (!editable()) { event.preventDefault(); return; }
            event.preventDefault();
            var clipboard = event.clipboardData, html = clipboard && clipboard.getData("text/html");
            if (html) doc.execCommand("insertHTML", false, sanitizer(html));
            else doc.execCommand("insertText", false, clipboard ? clipboard.getData("text/plain") : "");
        }
        function onReviewMouseDown(event) {
            if (event.target && event.target.closest("[data-review-command]")) event.preventDefault();
        }
        function onReviewClick(event) {
            if (detailStatus !== "ready" || delegated) return;
            var target = event.target, command = target && target.closest("[data-review-command]");
            if (command) {
                if (!editable() || busy) return;
                var value = null;
                if (command.dataset.reviewCommand === "createLink") {
                    value = global.prompt("链接 URL（http/https/mailto）");
                    if (!value || !/^(https?:\/\/|mailto:)[^\s]+$/i.test(value.trim())) return;
                    value = value.trim();
                }
                editor.focus(); doc.execCommand(command.dataset.reviewCommand, false, value); return;
            }
            var node = target && target.closest("[data-review-action]");
            if (!node || node.disabled) return;
            var action = node.dataset.reviewAction;
            if (action === "save") return saveDraft().catch(function () {});
            if (action === "new") return createDraft();
            if (["submit", "withdraw", "delete", "comment"].includes(action)) return mutateDraft(action);
            if (action === "select") return navigate(function () { resetReview(); return loadDraft(Number(node.dataset.draftId), false); });
            if (action === "reload") {
                var id = draft ? draft.id : requestedDraftId;
                if (!id || !selected || denied || destroyed) return;
                if (!global.confirm("请先复制本地文字。重新加载将放弃未保存内容，继续？")) return;
                resetReview(); return loadDraft(id, false);
            }
            if (action === "history") {
                panel = panel === "history" ? null : "history"; renderReview(); if (panel) return loadDrafts(false);
            }
            if (action === "events" || action === "comments") {
                var eventId = Number(node.dataset.draftId || (draft && draft.id)); if (!eventId) return;
                panel = panel === action && eventDraftId === eventId ? null : action; renderReview();
                if (panel) return loadEvents(eventId, false);
            }
            if (action === "more-drafts") return loadDrafts(true);
            if (action === "more-events") return loadEvents(eventDraftId, true);
        }

        // ---- 请求（AbortController + 15s 超时；signal 沿既有 api 传递） -------------
        function requestJson(path, method, body) {
            var requestGeneration = generation, requestOwner = selected;
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
                    var options = { method: method || "GET", cache: "no-store", signal: controller.signal };
                    if (body !== undefined) options.body = JSON.stringify(body);
                    return api(path, options);
                })
                .then(function (data) {
                    settle();
                    return data;
                }, function (error) {
                    settle();
                    // 跨 realm 的 Error 不保证 instanceof，保留原始错误对象上的 status/data。
                    var failure = error && typeof error === "object" ? error : new Error(String(error));
                    failure.timedOut = timedOut;
                    if (!destroyed && requestGeneration === generation && requestOwner === selected &&
                            (failure.status === 401 || failure.status === 403)) revokeAccess();
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
            [[allButton, "all"], [draftsButton, "drafts"], [readButton, "read"], [fullButton, "full"]].forEach(function (entry) {
                entry[0].setAttribute("aria-pressed", String(listScope === entry[1]));
            });
            pendingButton.setAttribute("aria-pressed", String(pendingOnly));
            setText(pendingCount, pendingTotal === null ? "不可用" : pendingTotal);
            var query = "?page=" + encodeURIComponent(String(listPage)) + "&size=" + LIST_PAGE_SIZE;
            if (listQuery) query += "&q=" + encodeURIComponent(listQuery);
            if (pendingOnly) query += "&pendingOnly=true";
            if (listScope === "drafts") query += "&draftsOnly=true";
            if (listScope === "read" || listScope === "full") query += "&accessLevel=" + listScope.toUpperCase();
            return LIST_PATH + query;
        }

        function detailUrl(contactId, before) {
            var query = "?limit=" + DETAIL_PAGE_LIMIT;
            if (before) query += "&before=" + encodeURIComponent(before);
            return LIST_PATH + "/" + encodeURIComponent(String(contactId)) + "/messages" + query;
        }

        var schedules = new Map(), schedulesSeq = 0, infoSeq = 0;
        var readInfo = { contactId: null, note: "备注加载中…", timing: "所在地加载中…" };
        function catalogLabel(catalog, value) {
            var pair = (Array.isArray(catalog) ? catalog : []).find(function (item) { return item[0] === value; });
            return pair ? pair[1] : (value || "");
        }
        function scheduleText(id) { return schedules.get(String(id)) || "排期加载中…"; }
        function loadScheduleSummaries(items) {
            var ids = items.map(function (item) { return item.contactId; }), seq = ++schedulesSeq, gen = generation;
            schedules.clear();
            if (!ids.length) return;
            requestJson("/api/meeting-calendar/summaries?contactIds=" + ids.join(",")).then(function (rows) {
                if (destroyed || seq !== schedulesSeq || gen !== generation) return;
                (Array.isArray(rows) ? rows : []).forEach(function (row) {
                    var when = row.next && row.next.startUtc;
                    var short = when && typeof global.formatBeijingMeetingShort === "function" ? global.formatBeijingMeetingShort(when) : "";
                    schedules.set(String(row.contactId), Number(row.activeCount) > 0 ? "已有排期 · " + row.activeCount + "场" + (short ? " · " + short : "") : "暂无排期");
                });
                ids.forEach(function (id) { if (!schedules.has(String(id))) schedules.set(String(id), "排期暂不可用"); });
                renderList(); renderIdentity();
            }).catch(function () {
                if (destroyed || seq !== schedulesSeq || gen !== generation) return;
                ids.forEach(function (id) { schedules.set(String(id), "排期暂不可用"); }); renderList(); renderIdentity();
            });
        }
        function loadReadInfo(contactId) {
            var seq = ++infoSeq, gen = generation;
            readInfo = { contactId: String(contactId), note: "备注加载中…", timing: "所在地加载中…" };
            function current() { return !destroyed && !denied && gen === generation && seq === infoSeq && selected && String(selected.contactId) === String(contactId); }
            requestJson("/api/mail/contact-notes/" + contactId).then(function (data) {
                if (!current()) return;
                readInfo.note = data.note || "暂无备注"; renderIdentity();
            }).catch(function () { if (current()) { readInfo.note = "备注暂不可用"; renderIdentity(); } });
            requestJson("/api/mail/contact-locations/" + contactId + "/timing").then(function (data) {
                if (!current()) return;
                var location = data.location || {}, rec = data.recommendation;
                readInfo.timing = location.configured ? location.countryLabel || location.countryCode : "暂未配置所在地";
                if (rec && rec.beijingStart && rec.beijingEnd) {
                    var a = formatTime(rec.beijingStart), b = formatTime(rec.beijingEnd);
                    readInfo.timing += " · 建议北京 " + a + " — " + b;
                }
                renderIdentity();
            }).catch(function () { if (current()) { readInfo.timing = "所在地暂不可用"; renderIdentity(); } });
        }
        function appendText(parent, text, className) {
            var node = makeNode(doc, "div", className || "mc-reader-meta"); setText(node, text); parent.appendChild(node); return node;
        }
        function pendingText(item) {
            var count = Number(item.displayPendingCount === undefined ? item.pendingCount : item.displayPendingCount) || 0;
            var owner = item.displayPendingOwner === "user1" ? "由你处理" : "管理员处理";
            return (item.displaySuspended ? "已挂起 · " : "") + count + " 条待处理" + (count ? " · " + owner : "")
                + (item.displaySuspendReason ? " · " + item.displaySuspendReason : "");
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
                setText(name, (item.name || "").trim() || "未填写姓名");
                var subject = makeNode(doc, "small");
                setText(subject, latestSubject(item));
                var nameRow = makeNode(doc, "div", "mc-reader-name");
                nameRow.appendChild(name);
                var access = makeNode(doc, "span", "mc-badge" + (item.accessLevel === "FULL" ? " mc-access-full" : ""));
                setText(access, item.accessLevel === "FULL" ? "可写" : "可读"); nameRow.appendChild(access);
                main.appendChild(nameRow);
                appendText(main, (item.accountCodes || []).join("、"));
                if (item.accessLevel === "FULL" && item.email) appendText(main, item.email);
                main.appendChild(subject);
                appendText(main, "上次回复 " + (item.latestInbound && item.latestInbound.receivedAt ? formatTime(item.latestInbound.receivedAt) + " 北京时间" : "暂无回复"), "mc-reader-reply-time");
                appendText(main, "收 " + (Number(item.receivedCount) || 0) + " · 发 " + (Number(item.sentCount) || 0));
                appendExpertTags(doc, main, item.expertTags);
                appendText(main, scheduleText(item.contactId), "mc-reader-schedule");
                appendText(main, pendingText(item));
                person.appendChild(main);
                var ownDrafts = Array.isArray(item.ownDrafts) ? item.ownDrafts : [];
                if (ownDrafts.length) {
                    if (listScope === "drafts") {
                        ownDrafts.forEach(function (entry) {
                            var label = entry.kind === "MANUAL" ? "草稿" : ({DRAFT: "未提交", SUBMITTED: "已提交", RETURNED: "已退回", APPROVED: "已审核"}[entry.state] || "草稿");
                            var open = buttonNode(doc, "reader-open-draft", label + " · " + (entry.subject || "无主题"), "mc-text-button mc-reader-draft");
                            open.dataset.contactId = String(item.contactId); open.dataset.draftId = String(entry.id); open.dataset.draftKind = entry.kind;
                            person.appendChild(open);
                        });
                    } else {
                        var draftBadge = makeNode(doc, "small", "mc-reader-draft-count"); setText(draftBadge, "草稿 " + ownDrafts.length); main.appendChild(draftBadge);
                    }
                }
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
            setText(identityName, selected ? ((selected.name || "").trim() || "未填写姓名") : "");
            identity.replaceChildren(identityName);
            if (selected) {
                appendText(identity, "账号 " + (selected.accountCodes || []).join("、"));
                appendText(identity, "专家上次回复 " + (selected.latestInbound && selected.latestInbound.receivedAt ? formatTime(selected.latestInbound.receivedAt) + " 北京时间" : "暂无回复"), "mc-reader-reply-time");
                appendText(identity, scheduleText(selected.contactId), "mc-reader-schedule");
                var badges = makeNode(doc, "div", "mc-reader-badges"); identity.appendChild(badges);
                [catalogLabel(global.operatorStatusOptions, selected.operatorStatus), catalogLabel(global.indexLevelOptions, selected.currentIndexLevel),
                    { NONE: "未标记", FOLLOWING: "跟进中", PROVIDED: "已提供" }[selected.progressStatus]].filter(Boolean).forEach(function (text) { appendText(badges, text, "mc-badge"); });
                appendExpertTags(doc, badges, selected.expertTags);
                appendText(identity, pendingText(selected));
                if (readInfo.contactId === String(selected.contactId)) {
                    appendText(identity, "备注：" + readInfo.note, "mc-reader-note");
                    appendText(identity, readInfo.timing);
                }
            }
        }

        function renderConversation() {
            renderReview();
            messages.replaceChildren();
            renderIdentity();
            if (!selected) {
                messages.appendChild(statusNode(doc, "mc-empty", COPY.noSelection));
                return;
            }
            if (detailStatus === "loading") {
                messages.appendChild(statusNode(doc, "mc-empty", COPY.loading));
                return;
            }
            if (detailStatus === "forbidden") {
                messages.appendChild(statusNode(doc, "mc-error", COPY.forbidden, "alert"));
                return;
            }
            if (detailStatus === "error") {
                messages.appendChild(statusNode(doc, "mc-error", COPY.network, "alert"));
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
                appendMailTags(doc, article, message);
                timeline.appendChild(article);
            });
            messages.appendChild(timeline);
        }

        function scrollToLatest() {
            if (!scroll) return;
            scroll.scrollTop = scroll.scrollHeight;
        }

        // ---- 读取 ------------------------------------------------------------------
        function loadDraftCount() {
            var gen = generation, seq = ++draftCountSeq;
            return requestJson(LIST_PATH + "?page=0&size=1&draftsOnly=true").then(function (data) {
                if (destroyed || gen !== generation || seq !== draftCountSeq) return;
                draftTotalExperts = Number(data && data.total); setText(draftsCount, Number.isFinite(draftTotalExperts) ? draftTotalExperts : "");
            }, function () { if (!destroyed && gen === generation && seq === draftCountSeq) setText(draftsCount, ""); });
        }
        function loadPendingCount() {
            var gen = generation, seq = ++pendingSeq;
            return requestJson(LIST_PATH + "?page=0&size=1&pendingOnly=true").then(function (data) {
                if (destroyed || gen !== generation || seq !== pendingSeq) return;
                pendingTotal = Number.isFinite(Number(data && data.total)) ? Number(data.total) : null;
                setText(pendingCount, pendingTotal === null ? "不可用" : pendingTotal);
            }, function () {
                if (destroyed || gen !== generation || seq !== pendingSeq) return;
                pendingTotal = null; setText(pendingCount, "不可用");
            });
        }
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
                loadScheduleSummaries(listItems);
                listTotal = normalized.total;
                listLoading = false;
                listFailed = false;
                if (selected) {
                    listItems.forEach(function (item) {
                        if (String(item.contactId) !== String(selected.contactId)) return;
                        Object.assign(selected, item);
                        if (delegated) {
                            if (item.accessLevel === "FULL") delegated.applyOptions({ summary: item });
                            else loadSelectedDetail();
                        }
                        selected.expertTags = Array.isArray(item.expertTags) ? item.expertTags.slice() : null;
                    });
                    renderIdentity();
                }
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
            restoreReadDetail();
            // 刷新/重选先清正文：任何失败都不能回退到旧内容（I-3）。
            detailItems = [];
            detailNextBefore = null;
            detailHasMore = false;
            detailStatus = "loading";
            renderConversation();
            return requestJson(detailUrl(contactId)).then(function (data) {
                if (isStale(gen, seq, currentDetailSeq)) return;
                if (!selected || String(selected.contactId) !== String(contactId)) return;
                if (!data || (data.accessLevel !== "READ" && data.accessLevel !== "FULL")) {
                    detailStatus = "forbidden"; renderConversation(); return;
                }
                var openDraft = selected.openDraft;
                delete selected.openDraft;
                if (data.accessLevel === "FULL") {
                    resetReview();
                    if (!global.MailboxChat || typeof global.MailboxChat.mount !== "function") {
                        detailStatus = "error"; renderConversation(); return;
                    }
                    conversation.parentNode.replaceChild(delegatedHost, conversation);
                    delegated = global.MailboxChat.mount(delegatedHost, {
                        mode: "delegated-conversation", contactId: Number(contactId),
                        sessionUser: sessionUser.username,
                        summary: Object.assign({}, selected),
                        draftId: openDraft && openDraft.kind === "MANUAL" ? openDraft.id : null,
                        onAccessChanged: function () {
                            if (!destroyed && gen === generation && seq === detailSeq && selected && String(selected.contactId) === String(contactId)) {
                                restoreReadDetail(); detailStatus = "forbidden"; renderConversation();
                                scheduleReload();
                            }
                        },
                        onChanged: function () { if (!destroyed) { loadList(); loadPendingCount(); loadDraftCount(); } }
                    });
                    return;
                }
                detailItems = mergeMessages([], data && data.items);
                detailNextBefore = data && data.nextBefore ? data.nextBefore : null;
                detailHasMore = Boolean(data && data.hasMore) && Boolean(detailNextBefore);
                detailStatus = "ready";
                loadReadInfo(contactId);
                renderConversation();
                renderReview();
                scrollToLatest();
                loadDrafts(false);
                if (openDraft && openDraft.kind === "REVIEW") return loadDraft(openDraft.id, false);
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
            restoreReadDetail();
            resetReview();
            selected = null;
            detailSeq += 1;
            clearDetailBody();
            detailStatus = "idle";
            renderList(); renderConversation(); renderReview();
        }

        function selectExpert(contactId, ownDraft) {
            return navigate(function () {
                var item = null;
                listItems.forEach(function (candidate) {
                    if (String(candidate.contactId) === String(contactId)) item = candidate;
                });
                resetReview();
                selected = Object.assign({}, item, {
                    contactId: contactId,
                    openDraft: ownDraft || (listScope === "drafts" && item && Array.isArray(item.ownDrafts) ? item.ownDrafts[0] : null),
                    name: item ? (item.name || "") : "",
                    expertTags: item && Array.isArray(item.expertTags) ? item.expertTags.slice() : null
                });
                detailSeq += 1; clearDetailBody();
                renderList(); renderConversation(); renderReview();
                return loadSelectedDetail();
            });
        }

        function reloadAll() {
            if (destroyed || denied) return Promise.resolve();
            var tasks = [loadList(), loadPendingCount(), loadDraftCount()];
            if (selected) {
                tasks.push(loadSelectedDetail());
                if (draft && !saving && !busy) tasks.push(loadDraft(draft.id, true));
            } else renderConversation();
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
            if (action === "reader-open-draft") {
                return selectExpert(actionNode.dataset.contactId, { id: Number(actionNode.dataset.draftId), kind: actionNode.dataset.draftKind });
            }
            if (["reader-all", "reader-pending", "reader-drafts", "reader-read", "reader-full"].includes(action)) {
                return navigate(function () {
                    listScope = action.slice(7); pendingOnly = listScope === "pending"; listPage = 0;
                    clearSelection(); return Promise.all([loadList(), loadPendingCount(), loadDraftCount()]);
                });
            }
            if (action === "search") {
                return navigate(function () {
                    listQuery = String(searchInput.value || "").trim(); listPage = 0;
                    clearSelection(); return loadList();
                });
            }
            if (action === "refresh") {
                if (denied) { denied = false; clearSelection(); return scheduleReload(); }
                return navigate(scheduleReload);
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
                onClick({ target: searchButton });
            }
        }

        var accessCheck = null;
        function checkCurrentAccess() {
            if (doc.hidden || destroyed || denied || !selected || accessCheck) return;
            var ctx = reviewContext(), expected = delegated ? "FULL" : "READ";
            accessCheck = requestJson(LIST_PATH + "/" + selected.contactId + "/access").then(function (data) {
                if (!sameContext(ctx, false)) return;
                if (!data || data.accessLevel !== expected) {
                    // Do not silently discard edits when a grant changes; retain the local draft until explicit reload.
                    if (delegated && delegated.lockAccess) delegated.lockAccess();
                    conflict = true; showReviewError("专家权限已变化，请保留正文后点击刷新"); renderReview();
                }
            }).catch(function () { /* requestJson already handles session expiry and revoked access */ })
                .finally(function () { accessCheck = null; });
        }
        function onFocus() { checkCurrentAccess(); }
        function onVisibilityChange() { checkCurrentAccess(); }

        function start() {
            root.addEventListener("click", onClick);
            root.addEventListener("click", onReviewClick);
            root.addEventListener("mousedown", onReviewMouseDown);
            subjectInput.addEventListener("input", onReviewInput);
            editor.addEventListener("input", onReviewInput);
            editor.addEventListener("paste", onEditorPaste);
            global.addEventListener("beforeunload", onBeforeUnload);
            renderReview();
            searchInput.addEventListener("keydown", onSearchKeydown);
            global.addEventListener("focus", onFocus);
            if (typeof doc.addEventListener === "function") {
                doc.addEventListener("visibilitychange", onVisibilityChange);
            }
            host.replaceChildren(root);
            renderList();
            renderConversation();
            loadList();
            loadPendingCount(); loadDraftCount();
        }

        function destroy() {
            if (destroyed) return;
            restoreReadDetail();
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
            root.removeEventListener("click", onReviewClick);
            root.removeEventListener("mousedown", onReviewMouseDown);
            subjectInput.removeEventListener("input", onReviewInput);
            editor.removeEventListener("input", onReviewInput);
            editor.removeEventListener("paste", onEditorPaste);
            global.removeEventListener("beforeunload", onBeforeUnload);
            resetReview();
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
                    if (denied) { denied = false; clearSelection(); return scheduleReload(); }
                    return flushDrafts().then(scheduleReload);
                },
                flushDrafts: flushDrafts,
                hasPendingDrafts: function () { return delegated ? delegated.hasPendingDrafts() : pending(); },
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
