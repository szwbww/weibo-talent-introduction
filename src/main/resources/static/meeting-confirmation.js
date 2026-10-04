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
 * 纯导出：filterZones / groupMeetingZones / formatOffsetSeconds / buildCountryOptions /
 *   normalizeMeetingText / sanitizeDraftHtml / planMeetingInsertion（契约签名）。
 *
 * 正文由服务端按启用的通用 `MEETING_INVITATION` 模板渲染：弹窗不选模板、不编辑
 * 模板正文、不填称呼与签名，只发时区/起止本地时间/Zoom 链接（I-1/I-2/I-5）。
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

    // ---- 会议专用国家/本场偏移分组（F-1/F-2：只在会议组件内使用） ----

    /** 秒 → "UTC+5:30"/"UTC-3"/"UTC+0"（只做算术格式化，不用 Intl 反推时区规则）。 */
    function formatOffsetSeconds(seconds) {
        var value = Number(seconds);
        if (!isFinite(value)) return "";
        var sign = value < 0 ? "-" : "+";
        var abs = Math.abs(Math.round(value));
        var hours = Math.floor(abs / 3600);
        var minutes = Math.round((abs % 3600) / 60);
        return "UTC" + sign + hours + (minutes ? ":" + (minutes < 10 ? "0" + minutes : minutes) : "");
    }

    /** 组起止偏移文案：相同只给一个；不同用 → 保留 DST 变化（F-1）。 */
    function groupOffsetLabel(group) {
        if (!group) return "";
        var start = group.offsetLabel || formatOffsetSeconds(group.startOffsetSeconds);
        var end = formatOffsetSeconds(group.endOffsetSeconds);
        if (!start) return end;
        if (!end || start === end) return start;
        return start + " → " + end;
    }

    function byZoneIdAsc(a, b) {
        var left = String(a && a.id != null ? a.id : "");
        var right = String(b && b.id != null ? b.id : "");
        return left < right ? -1 : left > right ? 1 : 0;
    }

    /** 组代表项：优先 id==canonicalZoneId 的可用项字典序首项，否则可用 raw ID 字典序首项（F-2）。 */
    function pickGroupRepresentative(members) {
        var canonical = members.filter(function (member) {
            return member.canonicalZoneId != null && String(member.canonicalZoneId) === String(member.id);
        });
        var pool = canonical.length ? canonical : members;
        return pool.slice().sort(byZoneIdAsc)[0];
    }

    /**
     * 会议专用分组（F-1）：key = countryCode + startOffsetSeconds + endOffsetSeconds，
     * 不跨国合并；成员必须带国家元信息、localTimeIssue=null、非空 endOffsetSeconds，
     * 且来自当前起止请求。组按起点偏移降序、终点偏移降序、代表 ID 字典序稳定排序。
     * 纯函数：不改动入参，不依赖组件状态。
     */
    function groupMeetingZones(zones, countryCode) {
        var code = String(countryCode == null ? "" : countryCode);
        if (!code) return [];
        var byKey = {};
        var order = [];
        (Array.isArray(zones) ? zones : []).forEach(function (zone) {
            if (!zone || typeof zone !== "object") return;
            if (String(zone.countryCode == null ? "" : zone.countryCode) !== code) return;
            if (zone.localTimeIssue != null && String(zone.localTimeIssue) !== "") return;
            if (zone.endOffsetSeconds == null) return;
            var start = Number(zone.offsetSeconds);
            var end = Number(zone.endOffsetSeconds);
            if (!isFinite(start) || !isFinite(end)) return;
            var key = code + "|" + start + "|" + end;
            if (!byKey[key]) {
                byKey[key] = {
                    key: key,
                    countryCode: code,
                    countryLabelZh: zone.countryLabelZh || code,
                    startOffsetSeconds: start,
                    endOffsetSeconds: end,
                    offsetLabel: zone.offsetLabel || "",
                    members: []
                };
                order.push(key);
            }
            byKey[key].members.push(zone);
        });
        var groups = order.map(function (key) { return byKey[key]; });
        groups.forEach(function (group) {
            group.members.sort(byZoneIdAsc);
            group.representative = pickGroupRepresentative(group.members);
            group.memberIds = group.members.map(function (member) { return String(member.id); });
        });
        groups.sort(function (a, b) {
            if (a.startOffsetSeconds !== b.startOffsetSeconds) return b.startOffsetSeconds - a.startOffsetSeconds;
            if (a.endOffsetSeconds !== b.endOffsetSeconds) return b.endOffsetSeconds - a.endOffsetSeconds;
            return byZoneIdAsc(a.representative, b.representative);
        });
        return groups;
    }

    /** 国家显示名（UTC 特殊项固定“协调世界时”，其余用服务端中文名，不回退城市）。 */
    function countryNameFor(code, labelZh) {
        if (String(code == null ? "" : code) === "UTC") return "协调世界时";
        return String(labelZh || code || "");
    }

    /** 国家选项：只保留有国家元信息的项（null 国家 SystemV 不进列表），中文名排序、同码排序。 */
    function buildCountryOptions(zones) {
        var seen = {};
        var out = [];
        (Array.isArray(zones) ? zones : []).forEach(function (zone) {
            if (!zone || typeof zone !== "object") return;
            var code = zone.countryCode == null ? "" : String(zone.countryCode);
            if (!code || seen[code]) return;
            seen[code] = true;
            out.push({ code: code, label: countryNameFor(code, zone.countryLabelZh),
                labelEn: String(zone.countryLabelEn || "") });
        });
        out.sort(function (a, b) {
            var byLabel = String(a.label).localeCompare(String(b.label), "zh-Hans-CN");
            if (byLabel !== 0) return byLabel;
            return a.code < b.code ? -1 : a.code > b.code ? 1 : 0;
        });
        return out;
    }

    /** 目录是否携带 01 国家元信息；空目录视为可用（不冒充配置不匹配）。 */
    function catalogHasCountryMetadata(zones) {
        var list = Array.isArray(zones) ? zones : [];
        if (list.length === 0) return true;
        return list.some(function (zone) {
            return zone && zone.countryCode != null && String(zone.countryCode) !== "";
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
            zonesSeq: 0,
            zonesKey: "",
            zonesMeeting: false,
            zonesPending: false,
            zonesError: false,
            zonesMetaMissing: false,
            selectedCountryCode: "",
            countryQuery: "",
            countryListOpen: false,
            activeCountryIndex: -1,
            zoneGroups: [],
            selectedGroupMemberIds: [],
            unresolvedZoneId: "",
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
        var previewToken = null;
        var zonesTimer = null;
        var zonesToken = null;
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
            if (!canPreview()) return false; // 当前起止/目录/选择仍须合法（F-4）
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

        // ---- 国家选择 + 会议时区分组（F-1/F-2/F-3/F-5；S-1/S-2） ----

        function findZoneById(zoneId) {
            var found = null;
            (state.zones || []).forEach(function (zone) {
                if (zone && String(zone.id) === String(zoneId)) found = zone;
            });
            return found;
        }

        function selectedRawIssue() {
            var raw = state.selectedZone ? findZoneById(state.selectedZone.id) : null;
            return raw && raw.localTimeIssue ? String(raw.localTimeIssue) : "";
        }

        /** 当前国家在本场会议下推导出的有效分组；非当前起止响应/缺元信息一律为空。 */
        function currentGroups() {
            if (!state.selectedCountryCode || !meetingReady()) return [];
            return groupMeetingZones(state.zones, state.selectedCountryCode);
        }

        /** 本场起止的完整 key；起止未填完整或本地 end<=start 时为空。 */
        function meetingKey() {
            var start = startLocalValue();
            var end = endLocalValue();
            if (!start || !end || start >= end) return "";
            return start + "|" + end;
        }

        /** 目录必须是当前起止的会议模式响应，且未 pending/error/缺元信息。 */
        function meetingReady() {
            if (!state.zonesMeeting || state.zonesPending || state.zonesError) return false;
            if (state.zonesMetaMissing) return false;
            var key = meetingKey();
            return !!key && state.zonesKey === key;
        }

        function groupById(groupId) {
            var found = null;
            (state.zoneGroups || []).forEach(function (group) {
                if (group.memberIds.indexOf(String(groupId)) !== -1) found = group;
            });
            return found;
        }

        function groupByIdIn(groups, groupId) {
            var found = null;
            (Array.isArray(groups) ? groups : []).forEach(function (group) {
                if (group.memberIds.indexOf(String(groupId)) !== -1) found = group;
            });
            return found;
        }

        function groupLabel(group) {
            if (!group) return "";
            return countryNameFor(group.countryCode, group.countryLabelZh) +
                "（" + groupOffsetLabel(group) + "）";
        }

        /** 当前选中项是否可提交：在当前响应里、无 issue、且落在某个有效组内。 */
        function selectedZoneUsable() {
            if (!state.selectedZone || state.unresolvedZoneId) return false;
            if (!meetingReady()) return false;
            var raw = findZoneById(state.selectedZone.id);
            if (!raw || (raw.localTimeIssue != null && String(raw.localTimeIssue) !== "")) return false;
            return !!groupById(raw.id);
        }

        function setCountrySummary(text) {
            var summary = el("meetingCountrySummary");
            if (summary) summary.textContent = text || "";
        }

        /** 组内成员是否仍落于单一有效组；返回 true 表示保留原 raw ID 合法。 */
        function reconcileGroupMembership(groups) {
            var prev = state.selectedGroupMemberIds || [];
            if (!prev.length) {
                var current = groupByIdIn(groups, state.selectedZone.id);
                if (current) {
                    state.selectedGroupMemberIds = current.memberIds.slice();
                }
                return !!current;
            }
            var keys = {};
            prev.forEach(function (id) {
                var group = groupByIdIn(groups, id);
                if (group) keys[group.key] = group;
            });
            var keyList = Object.keys(keys);
            if (keyList.length > 1) return false;
            if (keyList.length === 1) {
                state.selectedGroupMemberIds = keys[keyList[0]].memberIds.slice();
            }
            return true;
        }

        function adoptGroup(group) {
            if (!group || !group.representative) return;
            state.selectedZone = group.representative;
            state.selectedGroupMemberIds = group.memberIds.slice();
            state.unresolvedZoneId = "";
        }

        function clearZoneSelection() {
            state.selectedZone = null;
            state.selectedGroupMemberIds = [];
        }

        /** 同步下拉输入框/hint 的国家＋UTC 文案（正在输入时不覆盖用户输入）。 */
        function syncZoneDisplay() {
            var input = el("meetingZoneSearch");
            var hint = el("meetingZoneHint");
            var group = state.selectedZone ? groupById(state.selectedZone.id) : null;
            if (group) {
                if (input && !state.editing) input.value = groupLabel(group);
                if (hint) hint.textContent = groupLabel(group) + " · 日期和时间均按此时区填写";
                return;
            }
            if (input && !state.editing) input.value = "";
            if (hint) hint.textContent = "日期和时间均按所选时区填写。";
        }

        /** 单一入口刷新时区块显隐、国家摘要、下拉文案（F-3/F-5；S-1/S-2）。 */
        function refreshZoneUi() {
            var groups = currentGroups();
            state.zoneGroups = groups;
            var field = el("meetingZoneField");
            if (!field) return;
            var country = state.selectedCountryCode;
            if (!country) {
                field.hidden = true;
                setCountrySummary(state.zonesMetaMissing
                    ? "会议时区配置版本不匹配，请刷新后重试"
                    : state.unresolvedZoneId
                        ? "该旧时区没有国家归属，请重新选择国家和时区"
                        : meetingReady()
                            ? "请选择国家/地区。"
                            : "填写完整会议日期和时间后显示时区。");
            } else if (!meetingReady()) {
                field.hidden = true;
                setCountrySummary(state.zonesMetaMissing
                    ? "会议时区配置版本不匹配，请刷新后重试"
                    : state.zonesPending ? "正在更新会议时区…"
                        : state.zonesError ? "会议时区加载失败，请重试"
                            : "填写完整会议日期和时间后显示时区。");
            } else {
                var issue = selectedRawIssue();
                if (issue) {
                    field.hidden = groups.length <= 1;
                    setCountrySummary(issue);
                } else if (groups.length === 0) {
                    field.hidden = true;
                    setCountrySummary("该时间没有可用时区，请调整日期或时间。");
                } else if (groups.length === 1) {
                    field.hidden = true;
                    if (!state.selectedZone || !groupById(state.selectedZone.id)) adoptGroup(groups[0]);
                    setCountrySummary(groupLabel(groups[0]));
                } else {
                    field.hidden = false;
                    var selectedGroup = state.selectedZone ? groupById(state.selectedZone.id) : null;
                    setCountrySummary(selectedGroup ? groupLabel(selectedGroup) : "该国家/地区有多个时区，请选择。");
                }
            }
            if (field.hidden) {
                closeZoneList(false);
                moveFocusOutOfHiddenField(field);
            }
            syncZoneDisplay();
        }

        function moveFocusOutOfHiddenField(field) {
            if (!doc || !field || typeof field.contains !== "function") return;
            var active = doc.activeElement;
            if (!active || !field.contains(active)) return;
            var country = el("meetingCountrySearch");
            if (country && typeof country.focus === "function") {
                try { country.focus(); } catch (e) { /* noop */ }
            }
        }

        function renderCountryOptions() {
            var select = el("meetingCountry");
            if (!select) return;
            var previous = state.selectedCountryCode;
            var options = buildCountryOptions(state.zones);
            var html = '<option value="">请选择国家/地区</option>';
            options.forEach(function (option) {
                html += '<option value="' + escapeHtml(option.code) + '">' +
                    escapeHtml(option.label) + "</option>";
            });
            select.innerHTML = html;
            var stillThere = previous && options.some(function (option) { return option.code === previous; });
            state.selectedCountryCode = stillThere ? previous : "";
            if (!stillThere && previous) {
                clearZoneSelection();
            }
            select.value = state.selectedCountryCode;
            if (state.countryListOpen) renderCountrySearch();
            else closeCountryList();
        }

        function countryMatches() {
            var query = normalizeZoneText(state.countryQuery);
            return buildCountryOptions(state.zones).filter(function (option) {
                return !query || normalizeZoneText([option.label, option.labelEn, option.code].join(" "))
                    .indexOf(query) !== -1;
            });
        }

        function closeCountryList() {
            state.countryListOpen = false;
            state.countryQuery = "";
            state.activeCountryIndex = -1;
            var list = el("meetingCountryOptions");
            if (list) list.hidden = true;
            var input = el("meetingCountrySearch");
            if (!input) return;
            var selected = buildCountryOptions(state.zones).filter(function (option) {
                return option.code === state.selectedCountryCode;
            })[0];
            input.value = selected ? selected.label : "";
            input.setAttribute("aria-expanded", "false");
            input.removeAttribute("aria-activedescendant");
        }

        function renderCountrySearch() {
            var input = el("meetingCountrySearch");
            var list = el("meetingCountryOptions");
            if (!input || !list || input.disabled) return;
            var options = countryMatches();
            if (state.activeCountryIndex >= options.length) state.activeCountryIndex = -1;
            list.innerHTML = options.map(function (option, index) {
                return '<button type="button" role="option" tabindex="-1" id="meeting-country-option-' + index +
                    '" data-country="' + escapeHtml(option.code) + '" aria-selected="' +
                    (option.code === state.selectedCountryCode ? "true" : "false") + '"' +
                    (index === state.activeCountryIndex ? ' data-active="true"' : "") + '>' +
                    escapeHtml(option.label) + '</button>';
            }).join("") || '<div class="meeting-zone-empty">没有匹配的国家/地区</div>';
            state.countryListOpen = true;
            list.hidden = false;
            input.setAttribute("aria-expanded", "true");
            input.removeAttribute("aria-activedescendant");
            if (state.activeCountryIndex >= 0) {
                var id = "meeting-country-option-" + state.activeCountryIndex;
                input.setAttribute("aria-activedescendant", id);
                var active = el(id);
                if (active && active.scrollIntoView) active.scrollIntoView({ block: "nearest" });
            }
        }

        function selectCountry(code) {
            var select = el("meetingCountry");
            if (!select || !countryMatches().some(function (option) { return option.code === code; })) return;
            select.value = code;
            if (code !== state.selectedCountryCode) onCountryChanged();
            else closeCountryList();
        }

        function bindCountrySearch() {
            var input = el("meetingCountrySearch");
            var list = el("meetingCountryOptions");
            input.addEventListener("focus", function () {
                if (!state.countryListOpen) renderCountrySearch();
            });
            input.addEventListener("input", function () {
                state.countryQuery = input.value || "";
                state.activeCountryIndex = -1;
                renderCountrySearch();
            });
            input.addEventListener("blur", closeCountryList);
            input.addEventListener("keydown", function (event) {
                if (event.isComposing) return;
                var key = event.key;
                if (key === "ArrowDown" || key === "ArrowUp") {
                    event.preventDefault();
                    var count = countryMatches().length;
                    if (count) state.activeCountryIndex = state.activeCountryIndex < 0
                        ? (key === "ArrowDown" ? 0 : count - 1)
                        : (state.activeCountryIndex + (key === "ArrowDown" ? 1 : -1) + count) % count;
                    renderCountrySearch();
                } else if (key === "Enter") {
                    event.preventDefault();
                    if (!state.countryListOpen) { renderCountrySearch(); return; }
                    var options = countryMatches();
                    var pick = options[state.activeCountryIndex] || (options.length === 1 ? options[0] : null);
                    if (pick) selectCountry(pick.code);
                } else if (key === "Escape" && state.countryListOpen) {
                    event.preventDefault();
                    if (event.stopPropagation) event.stopPropagation();
                    event._countryEscapeHandled = true;
                    closeCountryList();
                } else if (key === "Tab") closeCountryList();
            });
            // 在 blur 前选择，防止关闭候选后鼠标事件丢失；click 同时支持触屏/辅助技术。
            list.addEventListener("mousedown", function (event) { event.preventDefault(); });
            function choose(event) {
                var button = event.target.closest && event.target.closest('button[data-country]');
                if (button) selectCountry(button.getAttribute("data-country"));
            }
            list.addEventListener("mousedown", choose);
            list.addEventListener("click", choose);
            el("toggleCountry").addEventListener("mousedown", function (event) { event.preventDefault(); });
            el("toggleCountry").addEventListener("click", function () {
                if (state.countryListOpen) closeCountryList();
                else { input.focus(); renderCountrySearch(); }
            });
        }

        /** 候选过滤：组标签/偏移/成员别名联合命中（别名只用于搜索，不展示）。 */
        function filterGroupOptions(groups, query) {
            var list = Array.isArray(groups) ? groups : [];
            var q = normalizeZoneText(query);
            if (!q) return list.slice();
            var qSeconds = parseUtcOffsetSeconds(q);
            return list.filter(function (group) {
                var text = group.members.map(function (member) {
                    return [
                        group.countryLabelZh,
                        countryNameFor(group.countryCode, group.countryLabelZh),
                        groupOffsetLabel(group),
                        member.id,
                        member.labelZh,
                        member.offsetLabel,
                        (Array.isArray(member.aliases) ? member.aliases : []).join(" ")
                    ].join(" ");
                }).join(" ");
                if (normalizeZoneText(text).indexOf(q) !== -1) return true;
                if (qSeconds !== null && Number(group.startOffsetSeconds) === qSeconds) return true;
                return false;
            });
        }

        function renderZoneOptions() {
            var list = el("meetingZoneOptions");
            var input = el("meetingZoneSearch");
            if (!list || !input) return;
            var groups = filterGroupOptions(state.zoneGroups, state.query);
            if (groups.length === 0) {
                list.innerHTML = '<div class="meeting-zone-empty">没有匹配的时区，请尝试英文城市名或 UTC+3。</div>';
                input.setAttribute("aria-expanded", "true");
                input.removeAttribute("aria-activedescendant");
                list.hidden = false;
                return;
            }
            var selectedId = state.selectedZone ? String(state.selectedZone.id) : "";
            var html = groups.map(function (group, index) {
                var selected = group.memberIds.indexOf(selectedId) !== -1;
                var check = selected ? " ✓" : "";
                return '<button type="button" id="meeting-zone-option-' + index +
                    '" role="option" aria-selected="' + (selected ? "true" : "false") +
                    '" data-zone="' + escapeHtml(group.representative.id) + '" tabindex="-1">' +
                    '<span><span data-role="zone-label">' +
                    escapeHtml(countryNameFor(group.countryCode, group.countryLabelZh)) +
                    "</span></span>" +
                    '<span data-role="zone-offset">' + escapeHtml(groupOffsetLabel(group)) + check +
                    "</span></button>";
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
            if (restoreLabel && !state.editing) syncZoneDisplay();
        }

        function openZoneList() {
            var list = el("meetingZoneOptions");
            if (!list) return;
            renderZoneOptions();
            state.zoneListOpen = true;
        }

        function selectGroup(group) {
            if (!group || !group.representative) return;
            state.selectedZone = group.representative;
            state.selectedGroupMemberIds = group.memberIds.slice();
            state.unresolvedZoneId = "";
            state.editing = false;
            state.query = "";
            state.activeZoneIndex = -1;
            closeZoneList(false);
            refreshZoneUi();
            formChanged();
        }

        function selectGroupById(zoneId) {
            var group = null;
            (state.zoneGroups || []).forEach(function (item) {
                if (item.memberIds.indexOf(String(zoneId)) !== -1) group = item;
            });
            if (!group) {
                var raw = findZoneById(zoneId);
                if (raw) group = groupByIdIn(currentGroups(), zoneId);
            }
            if (group) selectGroup(group);
        }

        // ---- 表单读取 ----

        function fieldValue(id) {
            var node = el(id);
            return node ? String(node.value || "") : "";
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
            if (!state.selectedCountryCode) issues.meetingCountrySearch = "请选择国家/地区";
            if (state.unresolvedZoneId) {
                issues.meetingZoneSearch = "该旧时区没有国家归属，请重新选择国家和时区";
            } else if (state.selectedCountryCode && !selectedZoneUsable()) {
                issues.meetingZoneSearch = selectedRawIssue() || "请选择会议时区";
            }
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
            var complete = !!startLocal && !!endLocal && startLocal < endLocal &&
                !!zoomValue() && validUrl(zoomValue()) && !state.unresolvedZoneId &&
                selectedZoneUsable();
            return { complete: complete, issues: issues };
        }

        function zoneSelectedValue() {
            return state.selectedZone ? String(state.selectedZone.id) : "";
        }

        function zoomValue() {
            return fieldValue("meetingUrl").trim();
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
            var generatedAt = "";
            if (state.savedMeeting && state.savedMeeting.input && state.savedMeeting.input.generatedAt) {
                generatedAt = String(state.savedMeeting.input.generatedAt);
            } else if (state.options && state.options.generatedAt) {
                generatedAt = String(state.options.generatedAt);
            }
            return {
                zoneId: zoneSelectedValue(),
                startLocal: startLocalValue(),
                endLocal: endLocalValue(),
                zoomUrl: zoomValue(),
                generatedAt: generatedAt
            };
        }

        function canPreview() {
            return !!state.options && localIssues().complete;
        }

        function invalidatePreview() {
            state.latestPreviewReady = false;
            state.preview = null;
            disableDownloadLink();
            setPreviewPaneIdle();
            var apply = applyButton();
            if (apply) apply.disabled = true;
        }

        function schedulePreview() {
            previewToken = {};
            var token = previewToken;
            if (previewTimer) {
                if (typeof clearTimeout === "function") clearTimeout(previewTimer);
                previewTimer = null;
            }
            state.formRevision += 1;
            invalidatePreview();
            applyAriaInvalid(localIssues().issues);
            if (!canPreview()) return;
            if (typeof setTimeout === "function") {
                previewTimer = setTimeout(function () {
                    if (previewToken !== token) return;
                    previewTimer = null;
                    if (state.disposed || !state.open) return;
                    runPreview();
                }, PREVIEW_DEBOUNCE_MS);
            }
        }

        // ---- 会议时区目录（F-4：独立 zonesSeq + 完整起止 key；不复用 configSeq） ----

        function invalidateZones() {
            state.zonesKey = "";
            state.zonesMeeting = false;
            state.zonesPending = false;
            state.zonesError = false;
            state.zoneGroups = [];
        }

        function scheduleZonesRefresh() {
            zonesToken = {};
            var token = zonesToken;
            if (zonesTimer) {
                if (typeof clearTimeout === "function") clearTimeout(zonesTimer);
                zonesTimer = null;
            }
            var key = meetingKey();
            state.zonesPending = !!key;
            state.zonesError = false;
            refreshZoneUi();
            syncApplyState();
            if (!key) return;
            if (typeof setTimeout === "function") {
                zonesTimer = setTimeout(function () {
                    if (zonesToken !== token) return;
                    zonesTimer = null;
                    if (state.disposed || !state.open) return;
                    runZonesRequest();
                }, PREVIEW_DEBOUNCE_MS);
            } else {
                runZonesRequest();
            }
        }

        function runZonesRequest() {
            var key = meetingKey();
            if (!key) {
                state.zonesPending = false;
                refreshZoneUi();
                syncApplyState();
                return;
            }
            var startLocal = startLocalValue();
            var endLocal = endLocalValue();
            var zoneDate = startLocal.slice(0, 10);
            var mySeq = ++state.zonesSeq;
            var url = "/api/mail/meeting-confirmation/time-zones?date=" +
                encodeURIComponent(zoneDate) +
                "&startLocal=" + encodeURIComponent(startLocal) +
                "&endLocal=" + encodeURIComponent(endLocal);
            apiFn(url).then(function (data) {
                if (state.disposed || !state.open || mySeq !== state.zonesSeq) return;
                if (meetingKey() !== key) return; // 起止已变，旧目录不得落地
                state.zones = Array.isArray(data) ? data : [];
                state.zonesMeeting = true;
                state.zonesKey = key;
                state.zonesPending = false;
                state.zonesError = false;
                state.zonesMetaMissing = !catalogHasCountryMetadata(state.zones);
                if (state.zonesMetaMissing) {
                    state.formRevision += 1;
                    invalidatePreview();
                    showStatus("会议时区配置版本不匹配，请刷新后重试", "config");
                }
                reconcileAfterZones();
            }).catch(function () {
                if (state.disposed || !state.open || mySeq !== state.zonesSeq) return;
                if (meetingKey() !== key) return;
                state.zonesPending = false;
                state.zonesError = true;
                state.zonesMeeting = false;
                state.zonesKey = "";
                state.zoneGroups = [];
                state.formRevision += 1;
                invalidatePreview();
                showStatus("会议时区加载失败，请重试", "zones");
                refreshZoneUi();
                syncApplyState();
            });
        }

        /** 目录落地后核对国家选择/原 raw 选择，并重算分组（F-2 改期分拆）。 */
        function reconcileAfterZones() {
            renderCountryOptions();
            if (state.selectedZone && !state.zonesMetaMissing) {
                var raw = findZoneById(state.selectedZone.id);
                var groups = currentGroups();
                var group = raw ? groupByIdIn(groups, raw.id) : null;
                if (!raw || !raw.countryCode) {
                    // 原 id 不在当前响应/无国家归属：保留 raw 值供恢复，阻断预览/应用（F-6）
                    state.unresolvedZoneId = String(state.selectedZone.id);
                    refreshLoadStatus("该旧时区没有国家归属，请重新选择国家和时区", null);
                } else {
                    state.unresolvedZoneId = "";
                    if (!state.selectedCountryCode) state.selectedCountryCode = String(raw.countryCode);
                    if (group && !reconcileGroupMembership(groups)) {
                        // 原组成员落入多个新偏移对：清选择并要求重选（F-2）
                        clearZoneSelection();
                        refreshLoadStatus("会议日期或时间变化后，原时区选项已分开，请重新选择。", null);
                    }
                }
            }
            refreshZoneUi();
            syncApplyState();
            schedulePreview();
        }

        function runPreview() {
            if (!canPreview()) {
                setPreviewPaneIdle();
                return;
            }
            var mySeq = ++state.previewSeq;
            var revision = state.formRevision;
            var zoneKey = state.zonesKey;
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
                if (zoneKey !== state.zonesKey) return; // 起止目录已变：旧预览作废
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
                if (revision !== state.formRevision) return;
                if (zoneKey !== state.zonesKey) return;
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

        // ---- 表单变更 ----

        function formChanged() {
            refreshError(null);
            if (state.startPassedShown) {
                state.startPassedShown = false;
                refreshLoadStatus(null, null);
            }
            schedulePreview();
        }

        /** 起止任一变化：先撤销预览/应用，再按完整起止重取目录（F-4）。 */
        function onMeetingTimeChanged() {
            invalidateZones();
            formChanged();
            scheduleZonesRefresh();
        }

        function onMeetingDateChanged() {
            var date = fieldValue("meetingDate");
            var endValue = fieldValue("meetingEndDate");
            if (date && (!endValue || endValue < date)) {
                var endDate = el("meetingEndDate");
                if (endDate) endDate.value = date;
            }
            onMeetingTimeChanged();
        }

        function onCountryChanged() {
            var select = el("meetingCountry");
            state.selectedCountryCode = select ? String(select.value || "") : "";
            closeCountryList();
            state.unresolvedZoneId = "";
            clearZoneSelection();
            refreshError(null);
            refreshLoadStatus(null, null);
            refreshZoneUi();
            schedulePreview();
            syncApplyState();
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

        // ---- 配置加载（options + 目录并行；seq 保护） ----

        function loadConfig() {
            var mySeq = ++state.configSeq;
            state.phase = "config-loading";
            state.configError = false;
            state.options = null;
            setFieldsDisabled(true);
            showStatus("正在加载会议配置…", null);
            var contactId = Number(state.openCtx.contactId);
            var processingId = Number(state.openCtx.processingId);
            var optsUrl = "/api/mail/unmatched-inbound/" + processingId +
                "/meeting-confirmation/options?contactId=" + contactId +
                "&senderAccountCode=" + encodeURIComponent(state.openCtx.senderAccountCode || "");
            var saved = state.savedMeeting && state.savedMeeting.input ? state.savedMeeting.input : null;
            var savedStart = saved ? String(saved.startLocal || "") : "";
            var savedEnd = saved ? String(saved.endLocal || "") : "";
            var completeSaved = savedStart.indexOf("T") !== -1 && savedEnd.indexOf("T") !== -1 &&
                savedStart < savedEnd;
            var zoneDate = savedStart.indexOf("T") !== -1 ? savedStart.slice(0, 10) : chinaToday();
            var zonesUrl = "/api/mail/meeting-confirmation/time-zones?date=" + encodeURIComponent(zoneDate);
            if (completeSaved) {
                zonesUrl += "&startLocal=" + encodeURIComponent(savedStart) +
                    "&endLocal=" + encodeURIComponent(savedEnd);
            }
            Promise.all([
                apiFn(optsUrl),
                apiFn(zonesUrl)
            ]).then(function (results) {
                if (state.disposed || !state.open || mySeq !== state.configSeq) return;
                var optionsData = results[0] || {};
                var zonesData = results[1];
                state.options = optionsData;
                state.zones = Array.isArray(zonesData) ? zonesData : [];
                state.zonesMeeting = completeSaved;
                state.zonesPending = false;
                state.zonesError = false;
                state.zonesMetaMissing = !catalogHasCountryMetadata(state.zones);
                populateFormFromOptions();
                state.zonesKey = completeSaved ? meetingKey() : "";
                state.phase = "ready";
                setFieldsDisabled(false);
                refreshLoadStatus(null, null);
                var context = el("meetingContext");
                if (context) {
                    context.textContent = state.openCtx.expertLabel + " · 回复账号 " +
                        String(optionsData.resolvedAccountCode || state.openCtx.senderAccountCode || "-");
                }
                if (state.zonesMetaMissing) {
                    showStatus("会议时区配置版本不匹配，请刷新后重试", "config");
                }
                reconcileAfterZones();
            }).catch(function () {
                if (state.disposed || !state.open || mySeq !== state.configSeq) return;
                state.phase = "config-error";
                state.configError = true;
                state.options = null;
                state.zones = [];
                setFieldsDisabled(true);
                showStatus("会议配置加载失败，请重试", "config");
            });
        }

        function populateFormFromOptions() {
            var saved = state.savedMeeting && state.savedMeeting.input ? state.savedMeeting.input : null;
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
            state.selectedCountryCode = "";
            state.selectedGroupMemberIds = [];
            state.unresolvedZoneId = "";
            if (zoneId) {
                var found = findZoneById(zoneId);
                if (found && found.countryCode) {
                    // 保留 raw ID（含 Brazil/East 旧别名）；国家由 01 元信息给出
                    state.selectedZone = found;
                    state.selectedCountryCode = String(found.countryCode);
                } else {
                    // 目录缺该 id/无国家归属：保留 raw 值供恢复，阻断预览与应用（F-6）
                    state.selectedZone = {
                        id: zoneId, labelZh: zoneId, aliases: [], offsetLabel: "",
                        offsetSeconds: 0, countryCode: null, canonicalZoneId: null,
                        endOffsetSeconds: null, localTimeIssue: null
                    };
                    state.unresolvedZoneId = zoneId;
                }
            }
            renderCountryOptions();
            refreshZoneUi();
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
                        if (pick) selectGroupById(pick.getAttribute("data-zone"));
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
                // I-4：候选项选择必须早于搜索框 blur 关闭列表 —— 在 mousedown 阶段
                // 先 preventDefault（阻止默认焦点转移 → 不触发 blur），再走既有
                // selectGroupById 路径；选择逻辑不复制。
                list.addEventListener("mousedown", function (event) {
                    var target = event.target;
                    var button = target && typeof target.closest === "function"
                        ? target.closest('button[role="option"][data-zone]')
                        : null;
                    if (!button) return;
                    event.preventDefault();
                    selectGroupById(button.getAttribute("data-zone"));
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
            ["meetingStart", "meetingEnd", "meetingEndDate"].forEach(function (id) {
                var node = el(id);
                if (node) node.addEventListener("input", onMeetingTimeChanged);
            });
            var date = el("meetingDate");
            if (date) date.addEventListener("input", onMeetingDateChanged);
            var url = el("meetingUrl");
            if (url) url.addEventListener("input", function () { formChanged(); });
            var country = el("meetingCountry");
            if (country) country.addEventListener("change", onCountryChanged);
            var insertSelect = el("insertMode");
            if (insertSelect) insertSelect.addEventListener("change", function () {
                state.mode = String(insertSelect.value || "append");
                refreshLoadStatus(null, null);
                syncApplyState();
            });
        }

        function dialogClickOutside(event) {
            var countryField = el("meetingCountryField");
            if (state.countryListOpen && countryField && !countryField.contains(event.target)) closeCountryList();
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
            if (event._zoneEscapeHandled || event._countryEscapeHandled) return;
            if (state.countryListOpen) {
                event.preventDefault();
                closeCountryList();
                return;
            }
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
            closeCountryList();
            state.latestPreviewReady = false;
            state.preview = null;
            state.editing = false;
            state.zoneListOpen = false;
            state.activeZoneIndex = -1;
            state.retryAction = null;
            state.zonesSeq += 1;
            state.zonesPending = false;
            state.zonesKey = "";
            state.zonesMeeting = false;
            state.zoneGroups = [];
            previewToken = {};
            zonesToken = {};
            if (previewTimer) {
                if (typeof clearTimeout === "function") clearTimeout(previewTimer);
                previewTimer = null;
            }
            if (zonesTimer) {
                if (typeof clearTimeout === "function") clearTimeout(zonesTimer);
                zonesTimer = null;
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
                } else if (state.retryAction === "zones") {
                    runZonesRequest();
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
            bindCountrySearch();
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
                state.activeZoneIndex = -1;
                // 关闭/切目标/重开都使旧时区目录与预览失效（F-4）
                state.zonesSeq += 1;
                state.zones = [];
                state.zonesKey = "";
                state.zonesMeeting = false;
                state.zonesPending = false;
                state.zonesError = false;
                state.zonesMetaMissing = false;
                state.selectedCountryCode = "";
                closeCountryList();
                state.zoneGroups = [];
                state.selectedGroupMemberIds = [];
                state.unresolvedZoneId = "";
                previewToken = {};
                zonesToken = {};
                if (zonesTimer) {
                    if (typeof clearTimeout === "function") clearTimeout(zonesTimer);
                    zonesTimer = null;
                }
                revokePreviewBlob();
                state.triggerEl = null;
                if (doc.activeElement && doc.activeElement.nodeType === 1) {
                    state.triggerEl = doc.activeElement;
                }
                if (!dialog && !buildDialog()) return null;
                ["meetingDate", "meetingStart", "meetingEndDate", "meetingEnd",
                    "meetingUrl"].forEach(function (id) {
                        var node = el(id);
                        if (node) node.value = "";
                    });
                var insertSelect = el("insertMode");
                if (insertSelect) insertSelect.value = "append";
                var countrySelect = el("meetingCountry");
                if (countrySelect) {
                    countrySelect.innerHTML = '<option value="">请选择国家/地区</option>';
                    countrySelect.value = "";
                }
                setCountrySummary("填写完整会议日期和时间后显示时区。");
                var zoneField = el("meetingZoneField");
                if (zoneField) zoneField.hidden = true;
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
                state.zonesSeq += 1;
                state.zonesPending = false;
                previewToken = {};
                zonesToken = {};
                if (previewTimer) {
                    if (typeof clearTimeout === "function") clearTimeout(previewTimer);
                    previewTimer = null;
                }
                if (zonesTimer) {
                    if (typeof clearTimeout === "function") clearTimeout(zonesTimer);
                    zonesTimer = null;
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
        '<div id="meetingCountryField" class="meeting-zone-field">' +
        '<label id="meetingCountryLabel" for="meetingCountrySearch">国家/地区</label>' +
        '<select id="meetingCountry" hidden aria-label="已选国家/地区"></select>' +
        '<div class="meeting-zone-control">' +
        '<input id="meetingCountrySearch" type="search" role="combobox" aria-autocomplete="list" aria-expanded="false" aria-controls="meetingCountryOptions" aria-labelledby="meetingCountryLabel" aria-describedby="meetingCountrySummary" autocomplete="off" placeholder="搜索国家/地区（中文、英文或代码）">' +
        '<button type="button" id="toggleCountry" aria-label="展开国家/地区选项">⌄</button>' +
        '</div>' +
        '<div id="meetingCountryOptions" class="meeting-zone-options" role="listbox" aria-label="国家/地区选项" hidden></div>' +
        '<small id="meetingCountrySummary" aria-live="polite">填写完整会议日期和时间后显示时区。</small>' +
        "</div>" +
        '<div class="meeting-fields">' +
        '<label>开始日期<input type="date" id="meetingDate" required></label>' +
        '<label>开始时间<input type="time" id="meetingStart" required step="60"></label>' +
        "</div>" +
        '<div class="meeting-fields">' +
        '<label>结束日期<input type="date" id="meetingEndDate" required></label>' +
        '<label>结束时间<input type="time" id="meetingEnd" required step="60"></label>' +
        "</div>" +
        '<div id="meetingZoneField" class="meeting-zone-field" hidden>' +
        '<label id="meetingZoneLabel" for="meetingZoneSearch">会议时区</label>' +
        '<div class="meeting-zone-control">' +
        '<input id="meetingZoneSearch" type="search" role="combobox" aria-autocomplete="list" aria-expanded="false" aria-controls="meetingZoneOptions" aria-labelledby="meetingZoneLabel" autocomplete="off" placeholder="选择 UTC 偏移">' +
        '<button type="button" id="toggleZone" aria-label="展开时区选项">⌄</button>' +
        "</div>" +
        '<div id="meetingZoneOptions" class="meeting-zone-options" role="listbox" aria-label="会议时区选项" hidden></div>' +
        '<small id="meetingZoneHint">日期和时间均按所选时区填写。</small>' +
        "</div>" +
        '<div class="meeting-clock" id="meetingClock" aria-live="polite"></div>' +
        '<label>Zoom 会议链接<input type="url" id="meetingUrl" required maxlength="2048" placeholder="https://zoom.us/j/…">' +
        "<small>粘贴已创建的会议链接，包含入会密码参数。</small>" +
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
        groupMeetingZones: groupMeetingZones,
        formatOffsetSeconds: formatOffsetSeconds,
        groupOffsetLabel: groupOffsetLabel,
        buildCountryOptions: buildCountryOptions,
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
