/**
 * 专家会议确认 · 独立弹窗组件（fast-p 04 T1/T2；S-1/S-2/S-4）。
 *
 * 普通 IIFE：浏览器挂 window.MailboxMeeting；Node 测试走 module.exports。
 * 无 npm/构建依赖。本文件只做只读 API 调用（options / time-zones / preview）与
 * 草稿事务回调（onApply），不写任何业务数据（I-1）：
 * - create({api, contextPath, onApply, onStatus}) → controller
 * - controller.open({ownerKey,targetKey,contactId,processingId,senderAccountCode,
 *     expertLabel,editorHtml,editorText,savedMeeting})
 * - controller.close({restoreFocus}) / controller.dispose()
 * 纯导出：filterZones / normalizeMeetingText / sanitizeDraftHtml /
 *   planMeetingInsertion（T1 契约签名）。
 *
 * 可信边界（I-6）：正文/时区/文件只绑定服务端值；不使用 fetch 拦截、不维护
 * 演示/造数据、不写 element.style；外部文本一律 textContent 或 escape；恢复/插入的
 * 正文 html 只接受本页捕获内容且经 sanitizeDraftHtml 白名单；只渲染 S-1 声明过的
 * class。草稿正文识别（flow）优先取宿主在 savedMeeting 上附加的瞬态 _flow
 * （宿主基于真实 DOM 计算，不随持久化草稿落库）。
 */
(function (global) {
    "use strict";

    if (global && global.MailboxMeeting) return;

    var API = null;

    // ------------------------------------------------------------------
    // 基础工具
    // ------------------------------------------------------------------

    function escapeHtml(value) {
        return String(value == null ? "" : value)
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;")
            .replace(/'/g, "&#039;");
    }

    function docRoot() {
        if (typeof document !== "undefined" && document) return document;
        if (global && global.document) return global.document;
        return null;
    }

    function deepCopy(value) {
        if (value == null) return value;
        if (Array.isArray(value)) return value.map(function (item) { return deepCopy(item); });
        if (typeof value === "object") {
            var out = {};
            Object.keys(value).forEach(function (key) { out[key] = deepCopy(value[key]); });
            return out;
        }
        return value;
    }

    /** 会议正文可见文字规范化：NFKC、NBSP→空格、行尾统一、逐行收白、空行剔除。 */
    function normalizeMeetingText(value) {
        return String(value == null ? "" : value)
            .normalize("NFKC")
            .replace(/\u00a0/g, " ")
            .replace(/\r\n?/g, "\n")
            .split("\n")
            .map(function (line) { return line.replace(/[ \t\f\v]+/g, " ").trim(); })
            .filter(Boolean)
            .join("\n");
    }

    function elementText(node) {
        if (!node) return "";
        if (typeof node.innerText === "string") return node.innerText;
        if (node.textContent != null) return String(node.textContent);
        return "";
    }

    // ------------------------------------------------------------------
    // 时区搜索（I-5：只搜索服务端目录数据；搜索词与选中 id 分离）
    // ------------------------------------------------------------------

    function normalizeZoneText(value) {
        return String(value == null ? "" : value)
            .normalize("NFKC")
            .toLowerCase()
            .replace(/\u2212/g, "-")
            .replace(/\s+/g, " ")
            .trim();
    }

    /** 把 "UTC+03:00"/"UTC+3"/"utc-5:30" 归一为秒数；不是 UTC 偏移串返回 null。 */
    function parseUtcOffsetSeconds(query) {
        var match = /^utc([+-])(\d{1,2})(?::(\d{2}))?$/.exec(normalizeZoneText(query));
        if (!match) return null;
        var hours = Number(match[2]);
        var minutes = match[3] != null ? Number(match[3]) : 0;
        if (hours > 14 || minutes > 59) return null;
        var total = hours * 3600 + minutes * 60;
        return match[1] === "-" ? -total : total;
    }

    /** 目录过滤（不截断）：id/labelZh/aliases/offsetLabel 联合包含；UTC±H:MM 按偏移匹配。 */
    function filterZones(zones, query) {
        var list = Array.isArray(zones) ? zones : [];
        var q = normalizeZoneText(query);
        if (!q) return list.slice();
        var qSeconds = parseUtcOffsetSeconds(q);
        return list.filter(function (zone) {
            if (!zone || typeof zone !== "object") return false;
            var id = normalizeZoneText(zone.id);
            var label = normalizeZoneText(zone.labelZh);
            var offset = normalizeZoneText(zone.offsetLabel);
            var aliasText = normalizeZoneText((Array.isArray(zone.aliases) ? zone.aliases : []).join(" "));
            if (id.indexOf(q) !== -1 || label.indexOf(q) !== -1 ||
                aliasText.indexOf(q) !== -1 || offset.indexOf(q) !== -1) {
                return true;
            }
            if (qSeconds !== null && Number(zone.offsetSeconds) === qSeconds) return true;
            return false;
        });
    }

    // ------------------------------------------------------------------
    // 草稿/正文 html 白名单清洗（I-6：template.content 遍历；无正则清 html）
    // ------------------------------------------------------------------

    var ALLOWED_TAGS = {
        div: true, p: true, br: true, b: true, strong: true,
        i: true, em: true, u: true, ul: true, ol: true, li: true, a: true, span: true
    };
    var REMOVE_TAGS = { script: true, style: true, iframe: true, object: true };

    function allowedLinkHref(href) {
        var value = String(href == null ? "" : href).trim();
        if (/^https?:\/\//i.test(value) || /^mailto:/i.test(value)) return value;
        return null;
    }

    /** 递归清洗：把 node 清洗后的节点 append 进 out。script/style/iframe/object
     *  连内容删除；其它非白名单标签 unwrap；a 只保留校验过协议 href + target/rel；
     *  div 只保留 meeting-body-block + data-meeting-block=true，其余属性全删。 */
    function sanitizeNode(node, doc, out) {
        if (!node) return;
        if (node.nodeType === 3) {
            if (node.data) out.push(doc.createTextNode(String(node.data)));
            return;
        }
        if (node.nodeType !== 1) return;
        var tag = String(node.tagName || "").toLowerCase();
        if (REMOVE_TAGS[tag]) return;
        var children = node.childNodes ? Array.prototype.slice.call(node.childNodes) : [];
        if (!ALLOWED_TAGS[tag]) {
            for (var i = 0; i < children.length; i += 1) sanitizeNode(children[i], doc, out);
            return;
        }
        var el = doc.createElement(tag);
        if (tag === "a") {
            var href = allowedLinkHref(node.getAttribute ? node.getAttribute("href") : "");
            if (href) {
                el.setAttribute("href", href);
                el.setAttribute("target", "_blank");
                el.setAttribute("rel", "noopener noreferrer");
            }
        } else if (tag === "div") {
            var cls = node.getAttribute ? node.getAttribute("class") || "" : "";
            var blockAttr = node.getAttribute ? node.getAttribute("data-meeting-block") || "" : "";
            if (cls.split(/\s+/).indexOf("meeting-body-block") !== -1 && String(blockAttr) === "true") {
                el.setAttribute("class", "meeting-body-block");
                el.setAttribute("data-meeting-block", "true");
            }
        }
        for (var j = 0; j < children.length; j += 1) {
            var bucket = [];
            sanitizeNode(children[j], doc, bucket);
            for (var k = 0; k < bucket.length; k += 1) el.appendChild(bucket[k]);
        }
        out.push(el);
    }

    /** 清洗整段 draft html；返回可安全 innerHTML 的字符串。 */
    function sanitizeDraftHtml(html, documentRef) {
        var doc = documentRef || docRoot();
        var input = String(html == null ? "" : html);
        if (!doc || !input) return "";
        var template = doc.createElement("template");
        template.innerHTML = input;
        var root = (template.content && template.content.childNodes) ? template.content : template;
        var out = doc.createElement("div");
        var children = root.childNodes ? Array.prototype.slice.call(root.childNodes) : [];
        for (var i = 0; i < children.length; i += 1) {
            var bucket = [];
            sanitizeNode(children[i], doc, bucket);
            for (var k = 0; k < bucket.length; k += 1) out.appendChild(bucket[k]);
        }
        return out.innerHTML;
    }

    /**
     * 插入决策（T1 纯导出）：依据编辑器现状 + 保存快照 + 模式返回动作计划。
     *  - action "replace"：整篇替换为会议块（空正文或显式 replace；清 QA）
     *  - action "update"：原位更新唯一未手改的会议块（保留其余内容与 QA）
     *  - action "append"：正文尾部追加会议块（保留内容与 QA）
     *  - allowed=false + reason "hand-edited"：已手改块禁止追加/静默覆盖
     *  blockHtml 为会议块内部清洗后的 html（不含外层 wrapper）。
     */
    function planMeetingInsertion(editor, saved, result, mode) {
        var doc = (editor && editor.ownerDocument) ? editor.ownerDocument : docRoot();
        var htmlBody = result && result.htmlBody ? String(result.htmlBody) : "";
        var innerHtml = doc ? sanitizeDraftHtml(htmlBody, doc) : "";
        if (!editor || !result || !doc) {
            return { allowed: false, reason: "invalid", action: null, qaClear: false, blockHtml: innerHtml };
        }
        var query = typeof editor.querySelectorAll === "function"
            ? Array.prototype.slice.call(editor.querySelectorAll('[data-meeting-block="true"]'))
            : [];
        var block = query.length > 0 ? query[0] : null;
        var savedBlockText = saved && saved.blockText ? normalizeMeetingText(saved.blockText) : "";
        var editorEmpty = normalizeMeetingText(elementText(editor)) === "";
        var textSame = !!(saved && saved.blockText && block &&
            normalizeMeetingText(elementText(block)) === savedBlockText);
        if (mode === "replace") {
            return { allowed: true, action: "replace", qaClear: true, blockHtml: innerHtml };
        }
        if (!saved || !saved.blockText || !block) {
            // 无既有会议块（首次/块被删/旧附件已移除）
            if (editorEmpty) {
                return { allowed: true, action: "replace", qaClear: true, blockHtml: innerHtml };
            }
            return { allowed: true, action: "append", qaClear: false, blockHtml: innerHtml };
        }
        if (block && textSame && query.length === 1) {
            return { allowed: true, action: "update", qaClear: false, blockHtml: innerHtml };
        }
        return { allowed: false, reason: "hand-edited", action: null, qaClear: false, blockHtml: innerHtml };
    }

    // ------------------------------------------------------------------
    // 组件实例
    // ------------------------------------------------------------------

    var PREVIEW_DEBOUNCE_MS = 300;
    var activeController = null;

    function create(config) {
        var options = config || {};
        var apiFn = options.api;
        var onApply = typeof options.onApply === "function" ? options.onApply : null;
        var onStatus = typeof options.onStatus === "function" ? options.onStatus : null;
        if (typeof apiFn !== "function") {
            apiFn = function () { return Promise.reject(new Error("api 不可用")); };
        }

        var doc = null;
        var dialog = null;
        var state = {
            disposed: false,
            open: false,
            phase: "idle",
            seq: 0,
            configSeq: 0,
            previewSeq: 0,
            options: null,
            zones: [],
            zoneDate: "",
            templates: [],
            baseTemplateId: null,
            selectedZone: null,
            zoneListOpen: false,
            activeZoneIndex: -1,
            editing: false,
            query: "",
            latestPreviewReady: false,
            preview: null,
            previewPending: false,
            previewNetworkError: false,
            formRevision: 0,
            isEdit: false,
            flow: "fill",
            mode: "append",
            savedMeeting: null,
            blobUrl: "",
            openCtx: null,
            triggerEl: null,
            startPassedShown: false,
            retryAction: null
        };
        var previewTimer = null;
        var loadStatusEl = null;
        var controllerHandle = null;

        function el(id) {
            if (!doc || typeof doc.getElementById !== "function") return null;
            return doc.getElementById(id);
        }

        function inform(message, type) {
            if (onStatus) {
                try { onStatus(message, type || "ok"); } catch (e) { /* noop */ }
            }
        }

        function refreshLoadStatus(text, retryAction) {
            if (!loadStatusEl) return;
            var span = loadStatusEl.querySelector ? loadStatusEl.querySelector("span") : null;
            if (span) span.textContent = text || "";
            var retry = el("retryMeeting");
            state.retryAction = retryAction || null;
            if (retry) retry.hidden = !state.retryAction;
            loadStatusEl.hidden = !text && !state.retryAction;
        }

        function refreshError(text) {
            var error = el("meetingError");
            if (!error) return;
            error.textContent = text || "";
            error.hidden = !text;
        }

        function showStatus(text, retryAction) {
            refreshError(null);
            refreshLoadStatus(text, retryAction);
        }

        function setFieldsDisabled(disabled) {
            if (!dialog || !dialog.querySelectorAll) return;
            Array.prototype.forEach.call(dialog.querySelectorAll("input,select,textarea,button"), function (node) {
                if (node.id === "cancelMeeting" || node.id === "closeMeeting") return;
                node.disabled = disabled;
            });
        }

        function applyButton() {
            return el("applyMeeting");
        }

        function syncApplyState() {
            var apply = applyButton();
            if (apply) apply.disabled = !applyEnabled();
        }

        function applyEnabled() {
            if (state.phase !== "ready") return false;
            if (!state.latestPreviewReady || !state.preview) return false;
            if (state.flow === "conflict" && state.mode !== "replace") return false;
            return true;
        }

        function revokePreviewBlob() {
            if (state.blobUrl) {
                if (typeof URL !== "undefined" && typeof URL.revokeObjectURL === "function") {
                    try { URL.revokeObjectURL(state.blobUrl); } catch (e) { /* noop */ }
                }
                state.blobUrl = "";
            }
        }

        function buildBlobUrl(icsText) {
            if (typeof URL === "undefined" || typeof URL.createObjectURL !== "function") return "";
            var BlobCtor = (typeof Blob !== "undefined") ? Blob : null;
            if (!BlobCtor) return "";
            try {
                var blob = new BlobCtor([String(icsText || "")], { type: "text/calendar;charset=UTF-8" });
                return URL.createObjectURL(blob);
            } catch (e) {
                return "";
            }
        }

        function disableDownloadLink() {
            var link = el("downloadMeeting");
            if (!link) return;
            revokePreviewBlob();
            link.removeAttribute("href");
            link.removeAttribute("download");
            link.setAttribute("aria-disabled", "true");
            link.setAttribute("tabindex", "-1");
        }

        function enableDownloadLink(icsText, filename) {
            var link = el("downloadMeeting");
            if (!link) return;
            revokePreviewBlob();
            var url = buildBlobUrl(icsText);
            if (url) {
                link.setAttribute("href", url);
                link.setAttribute("download", String(filename || "meeting.ics"));
                link.setAttribute("aria-disabled", "false");
                link.setAttribute("tabindex", "0");
                state.blobUrl = url;
            } else {
                disableDownloadLink();
            }
        }

        function setPreviewBusy(busy) {
            state.previewPending = busy;
            var body = el("meetingBody");
            if (body && body.setAttribute) body.setAttribute("aria-busy", busy ? "true" : "false");
            syncApplyState();
        }

        // ---- 时区下拉（S-4） ----

        function zoneLabel(zone) {
            if (!zone) return "";
            var offset = zone.offsetLabel ? " (" + zone.offsetLabel + ")" : "";
            return String(zone.labelZh || zone.id || "") + offset;
        }

        function renderZoneOptions() {
            var list = el("meetingZoneOptions");
            var input = el("meetingZoneSearch");
            if (!list || !input) return;
            var zones = filterZones(state.zones, state.query);
            if (zones.length === 0) {
                list.innerHTML = '<div class="meeting-zone-empty">没有匹配的时区，请尝试英文城市名或 UTC+3。</div>';
                input.setAttribute("aria-expanded", "true");
                input.removeAttribute("aria-activedescendant");
                list.hidden = false;
                return;
            }
            var selectedId = state.selectedZone ? state.selectedZone.id : "";
            var html = zones.map(function (zone, index) {
                var selected = String(zone.id) === String(selectedId);
                var offset = zone.offsetLabel || "";
                var check = selected ? " ✓" : "";
                return '<button type="button" id="meeting-zone-option-' + index +
                    '" role="option" aria-selected="' + (selected ? "true" : "false") +
                    '" data-zone="' + escapeHtml(zone.id) + '" tabindex="-1">' +
                    '<span><span data-role="zone-label">' + escapeHtml(zone.labelZh || zone.id) +
                    "</span><small>" + escapeHtml(zone.id) + "</small></span>" +
                    '<span data-role="zone-offset">' + escapeHtml(offset) + check + "</span></button>";
            }).join("");
            list.innerHTML = html;
            input.setAttribute("aria-expanded", "true");
            list.hidden = false;
            updateActiveOption();
        }

        function optionButtons() {
            var list = el("meetingZoneOptions");
            if (!list || typeof list.querySelectorAll !== "function") return [];
            return Array.prototype.slice.call(list.querySelectorAll('button[role="option"]'));
        }

        function updateActiveOption() {
            var input = el("meetingZoneSearch");
            var buttons = optionButtons();
            buttons.forEach(function (button, index) {
                if (index === state.activeZoneIndex) button.classList.add("focused");
                else button.classList.remove("focused");
            });
            if (!input) return;
            if (state.activeZoneIndex >= 0 && state.activeZoneIndex < buttons.length) {
                input.setAttribute("aria-activedescendant", "meeting-zone-option-" + state.activeZoneIndex);
            } else {
                input.removeAttribute("aria-activedescendant");
            }
        }

        function closeZoneList(restoreLabel) {
            var list = el("meetingZoneOptions");
            var input = el("meetingZoneSearch");
            if (list) list.hidden = true;
            if (input) {
                input.setAttribute("aria-expanded", "false");
                input.removeAttribute("aria-activedescendant");
            }
            state.zoneListOpen = false;
            state.activeZoneIndex = -1;
            if (restoreLabel && !state.editing && input && state.selectedZone) {
                input.value = zoneLabel(state.selectedZone);
            }
        }

        function openZoneList() {
            var list = el("meetingZoneOptions");
            if (!list) return;
            renderZoneOptions();
            state.zoneListOpen = true;
        }

        function selectZone(zone) {
            state.selectedZone = zone;
            state.editing = false;
            state.query = "";
            state.activeZoneIndex = -1;
            var input = el("meetingZoneSearch");
            if (input) {
                input.value = zoneLabel(zone);
                input.removeAttribute("aria-activedescendant");
            }
            closeZoneList(false);
            var hint = el("meetingZoneHint");
            if (hint) hint.textContent = String(zone.id) + " · 日期和时间均按此时区填写";
            formChanged();
        }

        function selectZoneById(zoneId) {
            var zone = null;
            (state.zones || []).forEach(function (item) {
                if (String(item.id) === String(zoneId)) zone = item;
            });
            if (zone) selectZone(zone);
        }

        // ---- 表单读取 ----

        function fieldValue(id) {
            var node = el(id);
            return node ? String(node.value || "") : "";
        }

        function signatureValue() {
            return fieldValue("meetingSignature").replace(/\r\n?/g, "\n").trim();
        }

        function validUrl(value) {
            if (!value) return false;
            if (!/^https?:\/\//i.test(value)) return false;
            if (/[\s]/.test(value)) return false;
            // eslint-disable-next-line no-control-regex
            if (/[\u0000-\u001f\u007f]/.test(value)) return false;
            return true;
        }

        function localIssues() {
            var issues = {};
            var template = fieldValue("meetingTemplate");
            if (!template) issues.meetingTemplate = "请选择会议邮件模板";
            if (!templateBodyValue()) issues.templateText = "会议模板正文不能为空";
            if (!meetingNameValue()) issues.meetingName = "请填写专家称呼";
            if (!zoneSelectedValue()) issues.meetingZoneSearch = "请选择会议时区";
            var startLocal = startLocalValue();
            var endLocal = endLocalValue();
            if (!startLocal) {
                issues.meetingDate = issues.meetingDate || "请填写开始日期";
                issues.meetingStart = "请填写开始时间";
            }
            if (!endLocal) {
                issues.meetingEndDate = issues.meetingEndDate || "请填写结束日期";
                issues.meetingEnd = "请填写结束时间";
            }
            if (startLocal && endLocal && startLocal >= endLocal) {
                issues.meetingEnd = "结束时间必须晚于开始时间";
            }
            if (!zoomValue()) issues.meetingUrl = "请填写 Zoom 会议链接";
            else if (!validUrl(zoomValue())) issues.meetingUrl = "请输入有效的 Zoom 会议链接";
            if (!signatureValue()) issues.meetingSignature = "请填写发件签名";
            var complete = !!startLocal && !!endLocal && startLocal < endLocal &&
                !!meetingNameValue() && !!zoomValue() && !!signatureValue() &&
                !!zoneSelectedValue() && !!template && !!templateBodyValue() && validUrl(zoomValue());
            return { complete: complete, issues: issues };
        }

        function meetingNameValue() {
            return fieldValue("meetingName").trim();
        }

        function zoneSelectedValue() {
            return state.selectedZone ? String(state.selectedZone.id) : "";
        }

        function zoomValue() {
            return fieldValue("meetingUrl").trim();
        }

        function templateBodyValue() {
            return fieldValue("templateText");
        }

        function startLocalValue() {
            var date = fieldValue("meetingDate");
            var time = fieldValue("meetingStart");
            return date && time ? date + "T" + time : "";
        }

        function endLocalValue() {
            var date = fieldValue("meetingEndDate");
            var time = fieldValue("meetingEnd");
            return date && time ? date + "T" + time : "";
        }

        function applyAriaInvalid(issues) {
            if (!dialog || typeof dialog.querySelectorAll !== "function") return;
            Array.prototype.forEach.call(dialog.querySelectorAll("[aria-invalid]"), function (node) {
                node.removeAttribute("aria-invalid");
            });
            Object.keys(issues).forEach(function (id) {
                var node = el(id);
                if (node) node.setAttribute("aria-invalid", "true");
            });
        }

        // ---- 预览（T2：300ms debounce；序号防过期响应覆盖） ----

        function buildMeetingInput() {
            var templateId = baseTemplateIdValue();
            var generatedAt = "";
            if (state.savedMeeting && state.savedMeeting.input && state.savedMeeting.input.generatedAt) {
                generatedAt = String(state.savedMeeting.input.generatedAt);
            } else if (state.options && state.options.generatedAt) {
                generatedAt = String(state.options.generatedAt);
            }
            return {
                templateId: templateId,
                templateBody: templateBodyValue(),
                expertSalutation: meetingNameValue(),
                zoneId: zoneSelectedValue(),
                startLocal: startLocalValue(),
                endLocal: endLocalValue(),
                zoomUrl: zoomValue(),
                senderSignature: signatureValue(),
                generatedAt: generatedAt
            };
        }

        function baseTemplateIdValue() {
            var value = fieldValue("meetingTemplate");
            if (value && value !== "custom") return Number(value);
            return state.baseTemplateId != null ? Number(state.baseTemplateId) : null;
        }

        function canPreview() {
            var check = localIssues();
            if (!check.complete) return false;
            if (!state.options || !state.templates || state.templates.length === 0) return false;
            return true;
        }

        function schedulePreview() {
            if (previewTimer) {
                if (typeof clearTimeout === "function") clearTimeout(previewTimer);
                previewTimer = null;
            }
            state.formRevision += 1;
            state.latestPreviewReady = false;
            state.preview = null;
            disableDownloadLink();
            setPreviewPaneIdle();
            var apply = applyButton();
            if (apply) apply.disabled = true;
            applyAriaInvalid(localIssues().issues);
            if (!canPreview()) return;
            if (typeof setTimeout === "function") {
                previewTimer = setTimeout(function () {
                    previewTimer = null;
                    if (state.disposed || !state.open) return;
                    runPreview();
                }, PREVIEW_DEBOUNCE_MS);
            }
        }

        function runPreview() {
            if (!canPreview()) {
                setPreviewPaneIdle();
                return;
            }
            var mySeq = ++state.previewSeq;
            var revision = state.formRevision;
            state.latestPreviewReady = false;
            state.preview = null;
            syncApplyState();
            setPreviewBusy(true);
            showStatus("正在生成邮件与日历…", null);
            var url = "/api/mail/unmatched-inbound/" + Number(state.openCtx.processingId) +
                "/meeting-confirmation/preview";
            var body = {
                contactId: Number(state.openCtx.contactId),
                senderAccountCode: state.openCtx.senderAccountCode || null,
                meeting: buildMeetingInput()
            };
            apiFn(url, { method: "POST", body: JSON.stringify(body) }).then(function (data) {
                if (state.disposed || !state.open || mySeq !== state.previewSeq) return;
                if (revision !== state.formRevision) return; // 过期响应不覆盖新值
                setPreviewBusy(false);
                state.previewNetworkError = false;
                state.preview = data || null;
                state.latestPreviewReady = true;
                renderPreviewPane(data);
                var startUtc = data && data.startUtc;
                var startMs = startUtc ? Date.parse(String(startUtc)) : NaN;
                if (Number.isFinite(startMs) && startMs < Date.now()) {
                    state.startPassedShown = true;
                    refreshLoadStatus("会议时间已过去，请核对", null);
                } else {
                    refreshLoadStatus(null, null);
                }
                syncApplyState();
            }).catch(function (err) {
                if (state.disposed || !state.open || mySeq !== state.previewSeq) return;
                setPreviewBusy(false);
                state.preview = null;
                state.latestPreviewReady = false;
                var serverMessage = err && err.data && typeof err.data.message === "string" ? err.data.message : "";
                if (serverMessage) {
                    state.previewNetworkError = false;
                    refreshError(serverMessage);
                    setPreviewPaneInvalid();
                    refreshLoadStatus(null, null);
                } else {
                    state.previewNetworkError = true;
                    refreshError(null);
                    setPreviewPaneInvalid();
                    showStatus("预览生成失败，请重试", "preview");
                }
                syncApplyState();
            });
        }

        // ---- 右栏（S-2 绑定表） ----

        function meetingBodyEl() {
            return el("meetingBody");
        }

        function setPreviewPaneIdle() {
            var body = meetingBodyEl();
            if (body) {
                body.innerHTML = "";
                body.appendChild(doc.createTextNode("请填写左侧配置后预览邮件。"));
                body.setAttribute("aria-busy", "false");
            }
            var clock = el("meetingClock");
            if (clock) clock.textContent = "时间待确认";
            var filename = el("meetingFilename");
            if (filename) filename.textContent = "日历附件待生成";
            var meta = el("meetingFileMeta");
            if (meta) meta.textContent = "填写完整后可下载";
            var raw = el("meetingRaw");
            if (raw) {
                raw.textContent = "";
                raw.hidden = true;
            }
            var inspect = el("inspectIcs");
            if (inspect) {
                inspect.textContent = "查看文件内容";
                inspect.disabled = true;
            }
            disableDownloadLink();
        }

        function setPreviewPaneInvalid() {
            var body = meetingBodyEl();
            if (body) {
                body.innerHTML = "";
                body.appendChild(doc.createTextNode("请修正左侧配置后预览邮件。"));
                body.setAttribute("aria-busy", "false");
            }
            var clock = el("meetingClock");
            if (clock) clock.textContent = "时间待确认";
            var filename = el("meetingFilename");
            if (filename) filename.textContent = "日历附件待生成";
            var meta = el("meetingFileMeta");
            if (meta) meta.textContent = "填写完整后可下载";
            var raw = el("meetingRaw");
            if (raw) {
                raw.textContent = "";
                raw.hidden = true;
            }
            var inspect = el("inspectIcs");
            if (inspect) {
                inspect.textContent = "查看文件内容";
                inspect.disabled = true;
            }
            disableDownloadLink();
        }

        function renderPreviewPane(data) {
            var preview = data || {};
            var attachment = preview.attachment || {};
            var body = meetingBodyEl();
            if (body) {
                body.innerHTML = "";
                var holder = doc.createElement("div");
                holder.innerHTML = String(preview.htmlBody || "");
                while (holder.firstChild) body.appendChild(holder.firstChild);
                body.setAttribute("aria-busy", "false");
            }
            var clock = el("meetingClock");
            if (clock) {
                clock.textContent = "";
                var b = doc.createElement("b");
                b.appendChild(doc.createTextNode("北京时间"));
                clock.appendChild(b);
                clock.appendChild(doc.createElement("br"));
                var china = doc.createElement("span");
                china.setAttribute("data-role", "china-time");
                china.appendChild(doc.createTextNode(String(preview.chinaTime || "")));
                clock.appendChild(china);
                clock.appendChild(doc.createElement("br"));
                var duration = doc.createElement("span");
                duration.setAttribute("data-role", "meeting-duration");
                duration.appendChild(doc.createTextNode("会议时长 " + Number(preview.durationMinutes || 0) + " 分钟"));
                clock.appendChild(duration);
            }
            var filename = el("meetingFilename");
            if (filename) filename.textContent = String(attachment.filename || "日历附件待生成");
            var meta = el("meetingFileMeta");
            if (meta) {
                var byteLength = Number(attachment.byteLength) || 0;
                meta.textContent = "日历事件 · " + Number(preview.durationMinutes || 0) + " 分钟 · " +
                    (byteLength / 1024).toFixed(1) + " KB";
            }
            var raw = el("meetingRaw");
            if (raw) {
                raw.textContent = String(attachment.icsText || "");
                raw.hidden = true;
            }
            var inspect = el("inspectIcs");
            if (inspect) {
                inspect.textContent = "查看文件内容";
                inspect.disabled = !String(attachment.icsText || "");
            }
            enableDownloadLink(String(attachment.icsText || ""), String(attachment.filename || "meeting.ics"));
            syncApplyState();
        }

        // ---- 模板（T2：有效 id 填充；custom 只保留 baseTemplateId） ----

        function populateTemplates() {
            var select = el("meetingTemplate");
            if (!select) return;
            var list = state.templates || [];
            var html = "";
            list.forEach(function (item) {
                html += '<option value="' + escapeHtml(String(item.id)) + '">' +
                    escapeHtml(String(item.name || item.id)) + "</option>";
            });
            if (list.length > 0) {
                html += '<option value="custom">自定义本次模板</option>';
            }
            select.innerHTML = html;
        }

        function applyTemplateSelection() {
            var select = el("meetingTemplate");
            if (!select) return;
            var saved = state.savedMeeting;
            var savedTemplateId = saved && saved.input && saved.input.templateId != null
                ? String(saved.input.templateId)
                : "";
            var savedBody = saved && saved.input ? String(saved.input.templateBody || "") : "";
            var known = state.templates.some(function (item) { return String(item.id) === savedTemplateId; });
            if (savedTemplateId && known) {
                select.value = savedTemplateId;
                var textarea = el("templateText");
                if (textarea) textarea.value = savedBody;
                state.baseTemplateId = Number(savedTemplateId);
                return;
            }
            if (savedTemplateId && !known && state.templates.length > 0) {
                select.value = "custom";
                var ta = el("templateText");
                if (ta) ta.value = savedBody;
                state.baseTemplateId = Number(savedTemplateId);
                refreshLoadStatus("原会议模板已不可用，请重新选择有效模板", null);
                return;
            }
            if (state.templates.length > 0) {
                var first = state.templates[0];
                select.value = String(first.id);
                var t2 = el("templateText");
                if (t2) t2.value = String(first.body || "");
                state.baseTemplateId = Number(first.id);
            } else {
                select.value = "";
                state.baseTemplateId = null;
            }
        }

        function onTemplateChange() {
            var select = el("meetingTemplate");
            var value = select ? select.value : "";
            var found = null;
            (state.templates || []).forEach(function (item) {
                if (String(item.id) === value) found = item;
            });
            if (found) {
                state.baseTemplateId = Number(found.id);
                var textarea = el("templateText");
                if (textarea) textarea.value = String(found.body || "");
            } else if (value === "custom" && state.baseTemplateId == null && state.templates.length > 0) {
                state.baseTemplateId = Number(state.templates[0].id);
            }
            formChanged();
        }

        function onTemplateTextInput() {
            var select = el("meetingTemplate");
            if (select && select.value !== "custom" && state.templates.length > 0) {
                var previous = select.value;
                select.value = "custom";
                if (state.baseTemplateId == null && previous && previous !== "custom") {
                    state.baseTemplateId = Number(previous);
                }
            }
            formChanged();
        }

        function onResetTemplate() {
            var targetId = state.baseTemplateId;
            if (targetId == null) {
                var select = el("meetingTemplate");
                var value = select ? select.value : "";
                if (value && value !== "custom") targetId = Number(value);
            }
            var found = null;
            (state.templates || []).forEach(function (item) {
                if (String(item.id) === String(targetId)) found = item;
            });
            if (!found) return;
            var sel = el("meetingTemplate");
            if (sel) sel.value = String(found.id);
            state.baseTemplateId = Number(found.id);
            var textarea = el("templateText");
            if (textarea) textarea.value = String(found.body || "");
            formChanged();
        }

        // ---- 表单变更 ----

        function formChanged() {
            refreshError(null);
            if (state.startPassedShown) {
                state.startPassedShown = false;
                refreshLoadStatus(null, null);
            }
            schedulePreview();
        }

        function onMeetingDateChanged() {
            var date = fieldValue("meetingDate");
            var endValue = fieldValue("meetingEndDate");
            if (date && (!endValue || endValue < date)) {
                var endDate = el("meetingEndDate");
                if (endDate) endDate.value = date;
            }
            var zoneDate = date || chinaToday();
            if (zoneDate !== state.zoneDate) {
                loadZones(zoneDate);
            }
            formChanged();
        }

        function chinaToday() {
            try {
                var fmt = new Intl.DateTimeFormat("en-CA", {
                    timeZone: "Asia/Shanghai",
                    year: "numeric", month: "2-digit", day: "2-digit"
                });
                var parts = fmt.formatToParts(new Date());
                var map = {};
                parts.forEach(function (part) { map[part.type] = part.value; });
                if (map.year && map.month && map.day) {
                    return map.year + "-" + map.month + "-" + map.day;
                }
            } catch (e) { /* fallthrough */ }
            var now = new Date();
            var month = String(now.getMonth() + 1);
            var day = String(now.getDate());
            return now.getFullYear() + "-" + (month.length === 1 ? "0" + month : month) + "-" +
                (day.length === 1 ? "0" + day : day);
        }

        // ---- 配置加载（options / time-zones 并行；seq 保护） ----

        function loadZones(zoneDate) {
            if (!zoneDate) return;
            var mySeq = ++state.configSeq;
            state.zoneDate = zoneDate;
            var url = "/api/mail/meeting-confirmation/time-zones?date=" + encodeURIComponent(zoneDate);
            apiFn(url).then(function (data) {
                if (state.disposed || !state.open || mySeq !== state.configSeq) return;
                var zones = Array.isArray(data) ? data : [];
                state.zones = zones;
                if (state.selectedZone) {
                    var kept = null;
                    zones.forEach(function (zone) {
                        if (String(zone.id) === String(state.selectedZone.id)) kept = zone;
                    });
                    if (kept) {
                        state.selectedZone = kept;
                        var input = el("meetingZoneSearch");
                        if (input) input.value = zoneLabel(kept);
                    }
                }
                if (state.zoneListOpen) renderZoneOptions();
            }).catch(function () {
                // 目录重载失败不阻断表单；保留旧目录（I-5 不隐式改 zone）
            });
        }

        function loadConfig() {
            var mySeq = ++state.configSeq;
            state.phase = "config-loading";
            state.configError = false;
            state.options = null;
            state.templates = [];
            setFieldsDisabled(true);
            showStatus("正在加载会议配置…", null);
            var contactId = Number(state.openCtx.contactId);
            var processingId = Number(state.openCtx.processingId);
            var optsUrl = "/api/mail/unmatched-inbound/" + processingId +
                "/meeting-confirmation/options?contactId=" + contactId +
                "&senderAccountCode=" + encodeURIComponent(state.openCtx.senderAccountCode || "");
            var saved = state.savedMeeting && state.savedMeeting.input ? state.savedMeeting.input : null;
            var zoneDate = saved && saved.startLocal ? String(saved.startLocal).slice(0, 10) : chinaToday();
            state.zoneDate = zoneDate;
            var zonesUrl = "/api/mail/meeting-confirmation/time-zones?date=" + encodeURIComponent(zoneDate);
            Promise.all([
                apiFn(optsUrl),
                apiFn(zonesUrl)
            ]).then(function (results) {
                if (state.disposed || !state.open || mySeq !== state.configSeq) return;
                var optionsData = results[0] || {};
                var zonesData = results[1];
                state.options = optionsData;
                state.templates = Array.isArray(optionsData.templates) ? optionsData.templates : [];
                state.zones = Array.isArray(zonesData) ? zonesData : [];
                populateTemplates();
                applyTemplateSelection();
                populateFormFromOptions();
                state.phase = "ready";
                if (state.templates.length === 0) {
                    setFieldsDisabled(true);
                    showStatus("会议模板不可用，请先启用会议确认专用模板", null);
                } else {
                    setFieldsDisabled(false);
                    refreshLoadStatus(null, null);
                }
                var context = el("meetingContext");
                if (context) {
                    context.textContent = state.openCtx.expertLabel + " · 回复账号 " +
                        String(optionsData.resolvedAccountCode || state.openCtx.senderAccountCode || "-");
                }
                // 初始无值时先跑一次便利校验/占位（不请求）
                schedulePreview();
                syncApplyState();
            }).catch(function () {
                if (state.disposed || !state.open || mySeq !== state.configSeq) return;
                state.phase = "config-error";
                state.configError = true;
                state.options = null;
                state.templates = [];
                state.zones = [];
                setFieldsDisabled(true);
                showStatus("会议配置加载失败，请重试", "config");
            });
        }

        function populateFormFromOptions() {
            var saved = state.savedMeeting && state.savedMeeting.input ? state.savedMeeting.input : null;
            var name = el("meetingName");
            if (name) name.value = saved ? String(saved.expertSalutation || "") : String(state.options.expertSalutation || "");
            var signature = el("meetingSignature");
            if (signature) signature.value = saved ? String(saved.senderSignature || "") : String(state.options.senderSignature || "");
            var date = el("meetingDate");
            var start = el("meetingStart");
            var endDate = el("meetingEndDate");
            var end = el("meetingEnd");
            if (saved) {
                var startLocal = String(saved.startLocal || "");
                var endLocal = String(saved.endLocal || "");
                if (startLocal.indexOf("T") !== -1) {
                    if (date) date.value = startLocal.slice(0, 10);
                    if (start) start.value = startLocal.slice(11, 16);
                }
                if (endLocal.indexOf("T") !== -1) {
                    if (endDate) endDate.value = endLocal.slice(0, 10);
                    if (end) end.value = endLocal.slice(11, 16);
                }
            } else {
                if (date) date.value = "";
                if (start) start.value = "";
                if (endDate) endDate.value = "";
                if (end) end.value = "";
            }
            var zoneId = saved ? String(saved.zoneId || "") : String(state.options.defaultZoneId || "");
            var urlInput = el("meetingUrl");
            if (urlInput) {
                urlInput.value = saved ? String(saved.zoomUrl || "") : "";
            }
            state.selectedZone = null;
            if (zoneId) {
                var found = null;
                (state.zones || []).forEach(function (zone) {
                    if (String(zone.id) === zoneId) found = zone;
                });
                if (found) {
                    state.selectedZone = found;
                } else {
                    // 目录缺该 id：以 id 兜底占位（不发明数据；仍可在预览时报错）
                    state.selectedZone = { id: zoneId, labelZh: zoneId, aliases: [], offsetLabel: "", offsetSeconds: 0 };
                }
            }
            var search = el("meetingZoneSearch");
            if (search) search.value = state.selectedZone ? zoneLabel(state.selectedZone) : "";
            var hint = el("meetingZoneHint");
            if (hint) {
                hint.textContent = state.selectedZone
                    ? String(state.selectedZone.id) + " · 日期和时间均按此时区填写"
                    : "下方日期和时间均按所选时区填写。";
            }
        }

        // ---- flow（正文插入语义；宿主经 savedMeeting._flow 提供真实 DOM 判定） ----

        function fallbackFlow() {
            var saved = state.savedMeeting;
            var editorHtml = String(state.openCtx.editorHtml || "");
            var editorText = String(state.openCtx.editorText || "");
            var holder = doc.createElement("div");
            holder.innerHTML = editorHtml;
            var block = holder.querySelector ? holder.querySelector('[data-meeting-block="true"]') : null;
            function hasText() {
                return normalizeMeetingText(editorText) !== "";
            }
            var textSame = false;
            if (saved && saved.blockText && block) {
                textSame = normalizeMeetingText(block.textContent || "") === normalizeMeetingText(String(saved.blockText));
            }
            if (saved && block && textSame) return "update";
            if (!hasText()) return "fill";
            if (saved) return "conflict";
            return "add";
        }

        function effectiveFlow() {
            var saved = state.savedMeeting;
            if (saved && saved._flow && ["fill", "add", "update", "conflict"].indexOf(saved._flow) !== -1) {
                return saved._flow;
            }
            return fallbackFlow();
        }

        // ---- 事件 ----

        function bindZoneSearch() {
            var input = el("meetingZoneSearch");
            if (!input) return;
            input.addEventListener("focus", function () {
                if (state.disposed || !state.open) return;
                if (!state.zoneListOpen) {
                    state.editing = false;
                    state.query = "";
                    openZoneList();
                }
            });
            input.addEventListener("input", function () {
                state.editing = true;
                state.query = input.value || "";
                state.activeZoneIndex = -1;
                openZoneList();
            });
            input.addEventListener("blur", function () {
                if (state.disposed || !state.open) return;
                state.editing = false;
                closeZoneList(true);
            });
            input.addEventListener("keydown", function (event) {
                var key = event.key || "";
                var buttons = optionButtons();
                if (key === "ArrowDown" || key === "ArrowUp") {
                    event.preventDefault();
                    if (buttons.length === 0) {
                        if (!state.zoneListOpen) {
                            state.query = "";
                            openZoneList();
                        }
                        return;
                    }
                    if (!state.zoneListOpen) {
                        state.zoneListOpen = true;
                        state.activeZoneIndex = key === "ArrowDown" ? 0 : buttons.length - 1;
                    } else if (state.activeZoneIndex < 0) {
                        state.activeZoneIndex = key === "ArrowDown" ? 0 : buttons.length - 1;
                    } else if (key === "ArrowDown") {
                        state.activeZoneIndex = (state.activeZoneIndex + 1) % buttons.length;
                    } else {
                        state.activeZoneIndex = (state.activeZoneIndex - 1 + buttons.length) % buttons.length;
                    }
                    updateActiveOption();
                    return;
                }
                if (key === "Enter") {
                    if (state.zoneListOpen && buttons.length > 0) {
                        event.preventDefault();
                        var pick = null;
                        if (state.activeZoneIndex >= 0 && state.activeZoneIndex < buttons.length) {
                            pick = buttons[state.activeZoneIndex];
                        } else if (buttons.length === 1) {
                            pick = buttons[0];
                        }
                        if (pick) selectZoneById(pick.getAttribute("data-zone"));
                    }
                    return;
                }
                if (key === "Escape") {
                    if (state.zoneListOpen) {
                        event.preventDefault();
                        if (event.stopPropagation) event.stopPropagation();
                        event._zoneEscapeHandled = true;
                        state.editing = false;
                        closeZoneList(true);
                    }
                    return;
                }
                if (key === "Tab") {
                    if (state.zoneListOpen) {
                        state.editing = false;
                        closeZoneList(true);
                    }
                    return;
                }
            });
            var list = el("meetingZoneOptions");
            if (list) {
                list.addEventListener("click", function (event) {
                    var target = event.target;
                    var button = target && typeof target.closest === "function"
                        ? target.closest('button[role="option"][data-zone]')
                        : null;
                    if (!button) return;
                    event.preventDefault();
                    selectZoneById(button.getAttribute("data-zone"));
                });
            }
            var toggle = el("toggleZone");
            if (toggle) {
                toggle.addEventListener("click", function () {
                    if (state.disposed || !state.open) return;
                    if (state.zoneListOpen) {
                        state.editing = false;
                        closeZoneList(true);
                    } else {
                        state.editing = false;
                        state.query = "";
                        openZoneList();
                        if (input && typeof input.focus === "function") input.focus();
                    }
                });
            }
        }

        function bindFormEvents() {
            var ids = ["meetingName", "meetingStart", "meetingEnd", "meetingEndDate", "meetingUrl", "meetingSignature"];
            ids.forEach(function (id) {
                var node = el(id);
                if (node) node.addEventListener("input", function () { formChanged(); });
            });
            var date = el("meetingDate");
            if (date) date.addEventListener("input", onMeetingDateChanged);
            var templateSelect = el("meetingTemplate");
            if (templateSelect) templateSelect.addEventListener("change", onTemplateChange);
            var templateText = el("templateText");
            if (templateText) templateText.addEventListener("input", onTemplateTextInput);
            var reset = el("resetTemplate");
            if (reset) reset.addEventListener("click", onResetTemplate);
            var insertSelect = el("insertMode");
            if (insertSelect) insertSelect.addEventListener("change", function () {
                state.mode = String(insertSelect.value || "append");
                refreshLoadStatus(null, null);
                syncApplyState();
            });
        }

        function dialogClickOutside(event) {
            if (!state.zoneListOpen) return;
            var target = event.target;
            if (!target) return;
            var inControl = function (node) {
                return node && target.contains && node.contains(target);
            };
            var search = el("meetingZoneSearch");
            var list = el("meetingZoneOptions");
            var toggle = el("toggleZone");
            if (inControl(search) || inControl(list) || inControl(toggle)) return;
            state.editing = false;
            closeZoneList(true);
        }

        function keydownOnDialog(event) {
            if (state.disposed || !state.open) return;
            if ((event.key || "") !== "Escape") return;
            if (event._zoneEscapeHandled) return;
            if (state.zoneListOpen) {
                event.preventDefault();
                if (event.stopPropagation) event.stopPropagation();
                event._zoneEscapeHandled = true;
                state.editing = false;
                closeZoneList(true);
                return;
            }
            event.preventDefault();
            closeDialog({ restoreFocus: true });
        }

        function onSubmit(event) {
            if (event && typeof event.preventDefault === "function") event.preventDefault();
            submitApply();
        }

        function submitApply() {
            if (!state.open) return;
            if (!applyEnabled()) {
                if (state.flow === "conflict" && state.mode !== "replace") {
                    refreshLoadStatus("会议正文已手动修改；请选择替换整篇正文，或取消后移除日历附件。", null);
                }
                return;
            }
            if (!onApply || !state.openCtx) return;
            var accepted = false;
            try {
                accepted = onApply(state.openCtx.targetKey, {
                    preview: state.preview,
                    mode: state.mode,
                    capturedEditorRevision: state.openCtx.editorRevision
                });
            } catch (e) {
                accepted = false;
            }
            if (!accepted) {
                refreshError("回复目标已变化，请重新打开会议确认");
                return;
            }
            closeDialog({ restoreFocus: true });
        }

        function closeDialog(opts) {
            var restore = !!(opts && opts.restoreFocus);
            if (!state.open) return;
            state.open = false;
            state.latestPreviewReady = false;
            state.preview = null;
            state.editing = false;
            state.zoneListOpen = false;
            state.activeZoneIndex = -1;
            state.retryAction = null;
            if (previewTimer) {
                if (typeof clearTimeout === "function") clearTimeout(previewTimer);
                previewTimer = null;
            }
            revokePreviewBlob();
            if (dialog) {
                if (typeof dialog.close === "function") {
                    try { dialog.close(); } catch (e) { /* noop */ }
                }
                if (dialog.removeAttribute) dialog.removeAttribute("open");
            }
            var trigger = state.triggerEl;
            state.triggerEl = null;
            if (restore && trigger && typeof trigger.focus === "function") {
                try { trigger.focus(); } catch (e) { /* noop */ }
            }
        }

        // ---- 事件绑定 ----

        function bindDialog() {
            var form = el("meetingForm");
            if (form) form.addEventListener("submit", onSubmit);
            var applyBtn = applyButton();
            if (applyBtn) applyBtn.addEventListener("click", function (event) {
                if (event && typeof event.preventDefault === "function") event.preventDefault();
                submitApply();
            });
            var closeBtn = el("closeMeeting");
            if (closeBtn) closeBtn.addEventListener("click", function () { closeDialog({ restoreFocus: true }); });
            var cancelBtn = el("cancelMeeting");
            if (cancelBtn) cancelBtn.addEventListener("click", function () { closeDialog({ restoreFocus: true }); });
            var retry = el("retryMeeting");
            if (retry) retry.addEventListener("click", function () {
                if (state.retryAction === "config") {
                    state.phase = "idle";
                    loadConfig();
                } else if (state.retryAction === "preview") {
                    runPreview();
                }
            });
            var download = el("downloadMeeting");
            if (download) {
                download.addEventListener("click", function (event) {
                    if (!state.latestPreviewReady || state.previewPending) {
                        if (event && typeof event.preventDefault === "function") event.preventDefault();
                    }
                });
            }
            var inspect = el("inspectIcs");
            if (inspect) {
                inspect.addEventListener("click", function () {
                    var raw = el("meetingRaw");
                    if (!raw) return;
                    var willOpen = raw.hidden;
                    raw.hidden = !willOpen;
                    inspect.textContent = willOpen ? "收起文件内容" : "查看文件内容";
                });
            }
            if (dialog) {
                dialog.addEventListener("keydown", keydownOnDialog);
                dialog.addEventListener("click", dialogClickOutside);
            }
            bindZoneSearch();
            bindFormEvents();
        }

        function buildDialog() {
            doc = docRoot();
            if (!doc || !doc.body || typeof doc.createElement !== "function") return null;
            var existing = doc.getElementById("meetingDialog");
            if (existing && activeController && activeController !== controllerHandle) {
                try { activeController.dispose(); } catch (e) { /* noop */ }
            }
            dialog = doc.createElement("dialog");
            dialog.setAttribute("class", "meeting-dialog");
            dialog.setAttribute("id", "meetingDialog");
            dialog.setAttribute("aria-labelledby", "meetingTitle");
            dialog.innerHTML = DIALOG_HTML;
            doc.body.appendChild(dialog);
            loadStatusEl = el("meetingLoadStatus");
            bindDialog();
            return dialog;
        }

        function showDialog() {
            if (!dialog) return;
            if (typeof dialog.showModal === "function") {
                try { dialog.showModal(); } catch (e) { dialog.setAttribute("open", ""); }
            } else {
                dialog.setAttribute("open", "");
            }
        }

        // ---- controller ----

        controllerHandle = {
            open: function (params) {
                var opts = params || {};
                if (state.disposed) return null;
                doc = docRoot();
                if (!doc) return null;
                state.seq += 1;
                state.openCtx = {
                    ownerKey: String(opts.ownerKey || ""),
                    targetKey: String(opts.targetKey || ""),
                    contactId: opts.contactId,
                    processingId: opts.processingId,
                    senderAccountCode: String(opts.senderAccountCode || ""),
                    expertLabel: String(opts.expertLabel || ""),
                    editorHtml: String(opts.editorHtml || ""),
                    editorText: String(opts.editorText || ""),
                    editorRevision: opts.editorRevision == null ? 0 : Number(opts.editorRevision)
                };
                state.savedMeeting = opts.savedMeeting ? deepCopy(opts.savedMeeting) : null;
                state.isEdit = !!state.savedMeeting;
                state.selectedZone = null;
                state.mode = "append";
                state.startPassedShown = false;
                state.latestPreviewReady = false;
                state.preview = null;
                state.previewNetworkError = false;
                state.query = "";
                state.editing = false;
                state.zoneListOpen = false;
                revokePreviewBlob();
                state.triggerEl = null;
                if (doc.activeElement && doc.activeElement.nodeType === 1) {
                    state.triggerEl = doc.activeElement;
                }
                if (!dialog && !buildDialog()) return null;
                ["meetingName", "meetingDate", "meetingStart", "meetingEndDate", "meetingEnd",
                    "meetingUrl", "meetingSignature", "templateText"].forEach(function (id) {
                        var node = el(id);
                        if (node) node.value = "";
                    });
                var insertSelect = el("insertMode");
                if (insertSelect) insertSelect.value = "append";
                state.flow = effectiveFlow();
                var selectLabel = el("insertModeLabel");
                if (selectLabel) {
                    selectLabel.hidden = !(state.flow === "add" || state.flow === "conflict");
                }
                var title = el("meetingTitle");
                if (title) title.textContent = state.isEdit ? "编辑会议确认" : "专家会议确认";
                var applyBtn = applyButton();
                if (applyBtn) applyBtn.textContent = state.isEdit ? "更新并填入回复" : "确认并填入回复";
                var context = el("meetingContext");
                if (context) context.textContent = state.openCtx.expertLabel + " · 回复账号 " +
                    String(state.openCtx.senderAccountCode || "-");
                refreshError(null);
                refreshLoadStatus(null, null);
                setPreviewPaneIdle();
                syncApplyState();
                state.open = true;
                showDialog();
                loadConfig();
                return controllerHandle;
            },
            close: function (opts) {
                closeDialog(opts || {});
                return controllerHandle;
            },
            dispose: function () {
                state.disposed = true;
                state.open = false;
                if (previewTimer) {
                    if (typeof clearTimeout === "function") clearTimeout(previewTimer);
                    previewTimer = null;
                }
                revokePreviewBlob();
                if (dialog && dialog.parentNode) {
                    try { dialog.parentNode.removeChild(dialog); } catch (e) { /* noop */ }
                }
                dialog = null;
                if (activeController === controllerHandle) activeController = null;
            }
        };

        if (activeController && activeController !== controllerHandle) {
            try { activeController.dispose(); } catch (e) { /* noop */ }
        }
        activeController = controllerHandle;
        return controllerHandle;
    }

    // ------------------------------------------------------------------
    // S-2 弹窗 DOM（层级/id/文案逐字契约；动态容器由绑定表填充）
    // ------------------------------------------------------------------

    var DIALOG_HTML =
        '<form id="meetingForm" novalidate>' +
        '<header class="meeting-head">' +
        '<div>' +
        '<h2 id="meetingTitle">专家会议确认</h2>' +
        '<p id="meetingContext">生成确认邮件与日历附件</p>' +
        "</div>" +
        '<button type="button" class="meeting-close" id="closeMeeting" aria-label="关闭会议确认">×</button>' +
        "</header>" +
        '<div class="meeting-grid">' +
        '<div class="meeting-form">' +
        '<p id="meetingLoadStatus" class="meeting-status" role="status" aria-live="polite" hidden>' +
        "<span></span>" +
        '<button type="button" class="meeting-link" id="retryMeeting" hidden>重试</button>' +
        "</p>" +
        '<label>邮件模板<select id="meetingTemplate"></select></label>' +
        '<details id="templateDetails" class="meeting-template">' +
        "<summary>查看模板与变量</summary>" +
        '<textarea id="templateText" maxlength="10000" aria-label="会议邮件模板正文" spellcheck="false"></textarea>' +
        "<small>修改仅用于本次回复；保存的模板由「邮件模板」统一维护。</small>" +
        "<p>" +
        "<code>{{expert_salutation}}</code> 专家称呼<br>" +
        "<code>{{meeting_time}}</code> 日期、时间和时区<br>" +
        "<code>{{zoom_url}}</code> 会议链接<br>" +
        "<code>{{sender_signature}}</code> 发件账号签名</p>" +
        '<button type="button" class="meeting-link" id="resetTemplate">恢复所选模板</button>' +
        "</details>" +
        '<label>专家称呼 <input id="meetingName" required maxlength="100" placeholder="Professor Basdogan">' +
        "<small>用于 Dear 后的称呼，可按专家习惯调整。</small>" +
        "</label>" +
        '<div class="meeting-zone-field">' +
        '<label id="meetingZoneLabel" for="meetingZoneSearch">会议时区</label>' +
        '<div class="meeting-zone-control">' +
        '<input id="meetingZoneSearch" type="search" role="combobox" aria-autocomplete="list" aria-expanded="false" aria-controls="meetingZoneOptions" aria-labelledby="meetingZoneLabel" autocomplete="off" placeholder="搜索国家、城市、时区或 UTC 偏移">' +
        '<button type="button" id="toggleZone" aria-label="展开时区选项">⌄</button>' +
        "</div>" +
        '<div id="meetingZoneOptions" class="meeting-zone-options" role="listbox" aria-label="会议时区选项" hidden></div>' +
        '<small id="meetingZoneHint">下方日期和时间均按所选时区填写。</small>' +
        "</div>" +
        '<div class="meeting-fields">' +
        '<label>开始日期<input type="date" id="meetingDate" required></label>' +
        '<label>开始时间<input type="time" id="meetingStart" required step="60"></label>' +
        "</div>" +
        '<div class="meeting-fields">' +
        '<label>结束日期<input type="date" id="meetingEndDate" required></label>' +
        '<label>结束时间<input type="time" id="meetingEnd" required step="60"></label>' +
        "</div>" +
        '<div class="meeting-clock" id="meetingClock" aria-live="polite"></div>' +
        '<label>Zoom 会议链接<input type="url" id="meetingUrl" required maxlength="2048" placeholder="https://zoom.us/j/…">' +
        "<small>粘贴已创建的会议链接，包含入会密码参数。</small>" +
        "</label>" +
        '<label>发件签名<textarea id="meetingSignature" required maxlength="2000"></textarea>' +
        "<small>默认带入回复账号签名。</small>" +
        "</label>" +
        '<label id="insertModeLabel" hidden>正文已有内容<select id="insertMode">' +
        '<option value="append">保留原文，追加确认邮件</option>' +
        '<option value="replace">替换整篇正文</option>' +
        "</select>" +
        "<small>请检查原文是否包含其他会议时间。</small>" +
        "</label>" +
        "</div>" +
        '<section class="meeting-preview" aria-label="会议邮件和日历预览">' +
        "<h3>" +
        '<span class="meeting-step">邮件正文</span>' +
        "<span>填写后自动预览</span>" +
        "</h3>" +
        '<div id="meetingError" class="meeting-error" role="alert" hidden></div>' +
        '<div id="meetingBody" class="meeting-paper" aria-busy="false"></div>' +
        '<div class="meeting-file">' +
        '<span class="meeting-file-icon">ICS</span>' +
        '<div class="meeting-file-main">' +
        '<strong id="meetingFilename"></strong>' +
        '<small id="meetingFileMeta"></small>' +
        '<div class="meeting-file-actions">' +
        '<a id="downloadMeeting" class="meeting-link" aria-disabled="true" tabindex="-1">下载 ICS 查看</a>' +
        '<button type="button" id="inspectIcs" class="meeting-link" disabled>查看文件内容</button>' +
        "</div>" +
        "</div>" +
        "</div>" +
        '<pre id="meetingRaw" class="meeting-raw" tabindex="0" aria-label="日历文件内容" hidden></pre>' +
        '<p class="meeting-note">日历包含会议时间、Zoom 链接及团队签名，可下载后导入日历查看。确认后作为待发送附件加入当前回复。</p>' +
        "</section>" +
        "</div>" +
        '<footer class="meeting-bottom">' +
        "<p>确认仅填入草稿，发送仍由人工操作。</p>" +
        "<div>" +
        '<button type="button" class="button" id="cancelMeeting">取消</button>' +
        '<button type="submit" class="button primary" id="applyMeeting" disabled>确认并填入回复</button>' +
        "</div>" +
        "</footer>" +
        "</form>";

    API = Object.freeze({
        create: create,
        filterZones: filterZones,
        normalizeMeetingText: normalizeMeetingText,
        sanitizeDraftHtml: sanitizeDraftHtml,
        planMeetingInsertion: planMeetingInsertion
    });

    if (global) {
        try {
            global.MailboxMeeting = API;
        } catch (e) { /* noop */ }
    }
    if (typeof module !== "undefined" && module.exports) {
        module.exports = API;
    }
})(typeof window !== "undefined" ? window : (typeof globalThis !== "undefined" ? globalThis : this));
