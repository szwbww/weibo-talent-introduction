/* 全球北京时间与时区对照（fast-p 01 独立组件）：纯换算 + 只读目录 + 全局宿主。
 *
 * 普通 script（非 ES module），必须在 app.js 之后加载：目录经宿主 window.api() 读取，
 * 筛选复用宿主 MailboxMeeting.filterZones，DOM 模板逐字取自计划 S-4，动态文本一律
 * 走 textContent。不写业务状态、不持久化、不新增后端接口；浏览器在 DOMContentLoaded
 * 自动挂载，Node 测试经 module.exports 载入纯函数与 mount。 */
(function (global) {
    "use strict";

    var CATALOG_PATH = "/api/mail/meeting-confirmation/time-zones?date=";
    var BEIJING_OFFSET_SECONDS = 28800;
    var BEIJING_OFFSET_MS = BEIJING_OFFSET_SECONDS * 1000;
    var MIN_YEAR = 2000;
    var MAX_YEAR = 2100;
    var PAGE_SIZE = 6;
    var TICK_INTERVAL_MS = 1000;
    var FIT_TOLERANCE_PX = 1;
    var ICON_ONLY_CLASS = "world-clock-icon-only";
    var HEADER_CLASS = "world-clock-header";
    var COMMON_ZONE_IDS = [
        "Europe/London",
        "Europe/Berlin",
        "America/New_York",
        "America/Los_Angeles",
        "Asia/Tokyo",
        "Asia/Seoul"
    ];
    var INPUT_STATE_ATTRIBUTES = ["aria-invalid"];

    var TRIGGER_HTML = [
        '<button type="button" id="worldClockTrigger" class="world-clock-trigger" aria-label="查看当前北京时间与全球时区" aria-expanded="false" aria-controls="worldClockPanel">',
        '    <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="9"></circle><path d="M12 7v5l3 2"></path></svg>',
        '    <span id="worldClockHeaderTime" class="world-clock-label"></span>',
        '    <span class="world-clock-label">北京时间</span>',
        '    <span class="world-clock-chevron" aria-hidden="true">⌄</span>',
        '</button>',
    ].join("\n");

    var PANEL_HTML = [
        '<section id="worldClockPanel" class="world-clock-panel" role="region" aria-labelledby="worldClockTitle" hidden>',
        '    <header class="world-clock-head">',
        '        <div>',
        '            <h2 id="worldClockTitle">全球时间</h2>',
        '            <span class="world-clock-caption world-clock-now-label">当前北京时间</span>',
        '            <time id="worldClockNow" class="world-clock-now"></time>',
        '            <button type="button" id="worldClockUseNow" class="world-clock-link">使用当前时间换算</button>',
        '        </div>',
        '        <button type="button" id="worldClockClose" class="world-clock-close" aria-label="关闭全球时间">×</button>',
        '    </header>',
        '    <div class="world-clock-body">',
        '        <div class="world-clock-fields">',
        '            <label for="worldClockDate">日期（北京时间）<input id="worldClockDate" type="date" min="2000-01-01" max="2100-12-31" required></label>',
        '            <label for="worldClockTime">时间（北京时间）<input id="worldClockTime" type="time" step="60" required></label>',
        '        </div>',
        '        <p id="worldClockInputError" class="world-clock-status world-clock-error" role="alert" hidden>请填写 2000–2100 年内有效的北京日期和时间。</p>',
        '        <div class="world-clock-filters">',
        '            <label for="worldClockSearch">搜索全球时区<input id="worldClockSearch" type="search" placeholder="国家、城市、IANA 或 UTC+5:30" autocomplete="off"></label>',
        '            <label for="worldClockRegion">时区分组<select id="worldClockRegion"><option value="">全部</option><option value="Europe">欧洲</option><option value="America">美洲</option><option value="Asia">亚洲</option><option value="Africa">非洲</option><option value="PacificAustralia">太平洋/澳洲</option><option value="Other">其他/别名</option></select></label>',
        '        </div>',
        '        <div class="world-clock-tabs" role="group" aria-label="时区范围">',
        '            <button type="button" id="worldClockCommon" class="world-clock-tab" aria-pressed="true">常用 · 欧美日韩</button>',
        '            <button type="button" id="worldClockGlobal" class="world-clock-tab" aria-pressed="false">全球时区</button>',
        '        </div>',
        '        <p id="worldClockLoadStatus" class="world-clock-status" role="status" hidden></p>',
        '        <button type="button" id="worldClockRetry" class="world-clock-link" hidden>重新加载时区</button>',
        '        <div class="world-clock-results-head"><strong>同一时刻 · 全球对照</strong><span id="worldClockCount" class="world-clock-caption" aria-live="polite"></span></div>',
        '        <table id="worldClockTable" class="world-clock-table" aria-label="所选北京时间对应的全球时间"><thead><tr><th scope="col">国家 / 城市</th><th scope="col">当地日期与时间</th><th scope="col">与北京时差</th></tr></thead><tbody id="worldClockRows"></tbody></table>',
        '        <div id="worldClockEmpty" class="world-clock-empty" hidden>没有匹配的时区，试试“伦敦”或“UTC+5:30”。</div>',
        '        <div id="worldClockUnsupported" class="world-clock-caption" role="status" hidden></div>',
        '        <footer class="world-clock-footer">',
        '            <span class="world-clock-caption">按所选时刻换算 · 自动适配夏令时</span>',
        '            <div id="worldClockPager" class="world-clock-pager" hidden><button type="button" id="worldClockPrev" class="world-clock-page">上一页</button><span id="worldClockPageLabel"></span><button type="button" id="worldClockNext" class="world-clock-page">下一页</button></div>',
        '        </footer>',
        '    </div>',
        '</section>',
    ].join("\n");

    var ROW_HTML = [
        '<tr>',
        '    <td><span class="world-clock-name"></span><span class="world-clock-zone"></span></td>',
        '    <td><span class="world-clock-local"><span data-field="time"></span><span class="world-clock-day" hidden></span></span><span class="world-clock-date"></span></td>',
        '    <td><span data-field="difference"></span><span class="world-clock-zone" data-field="offset"></span></td>',
        '</tr>',
    ].join("\n");

    // ------------------------------------------------------------------
    // 纯换算：北京固定 UTC+08:00 与目标 IANA 区
    // ------------------------------------------------------------------

    function pad2(value) {
        return value < 10 ? "0" + value : String(value);
    }

    function daysInMonth(year, month) {
        return new Date(Date.UTC(year, month, 0)).getUTCDate();
    }

    /** 北京日历分解（固定 UTC+08:00，与设备时区无关）。 */
    function beijingParts(epoch) {
        var shifted = new Date(epoch + BEIJING_OFFSET_MS);
        return {
            year: shifted.getUTCFullYear(),
            month: shifted.getUTCMonth() + 1,
            day: shifted.getUTCDate(),
            hour: shifted.getUTCHours(),
            minute: shifted.getUTCMinutes(),
            second: shifted.getUTCSeconds()
        };
    }

    function dateText(parts) {
        return parts.year + "-" + pad2(parts.month) + "-" + pad2(parts.day);
    }

    function timeText(parts, withSeconds) {
        var text = pad2(parts.hour) + ":" + pad2(parts.minute);
        return withSeconds ? text + ":" + pad2(parts.second) : text;
    }

    function invalidResult(reason) {
        return { valid: false, reason: reason, epoch: null, date: "", time: "" };
    }

    /**
     * 解析北京日期时间输入（I-4）：只接受 YYYY-MM-DD / HH:mm，年份 2000–2100（含端点）。
     * 先按数值构造 UTC 日历，再反查北京日历；不使用 Date 的自动归一化，失败显式 invalid。
     */
    function parseBeijingInput(dateInput, timeInput) {
        var date = typeof dateInput === "string" ? dateInput.trim() : "";
        var time = typeof timeInput === "string" ? timeInput.trim() : "";
        var dateMatch = /^(\d{4})-(\d{2})-(\d{2})$/.exec(date);
        var timeMatch = /^(\d{2}):(\d{2})$/.exec(time);
        if (!dateMatch || !timeMatch) return invalidResult("format");
        var year = Number(dateMatch[1]);
        var month = Number(dateMatch[2]);
        var day = Number(dateMatch[3]);
        var hour = Number(timeMatch[1]);
        var minute = Number(timeMatch[2]);
        if (year < MIN_YEAR || year > MAX_YEAR) return invalidResult("year");
        if (month < 1 || month > 12) return invalidResult("month");
        if (day < 1 || day > daysInMonth(year, month)) return invalidResult("day");
        if (hour > 23 || minute > 59) return invalidResult("clock");
        var epoch = Date.UTC(year, month - 1, day, hour, minute) - BEIJING_OFFSET_MS;
        var back = beijingParts(epoch);
        if (back.year !== year || back.month !== month || back.day !== day ||
            back.hour !== hour || back.minute !== minute) {
            return invalidResult("calendar");
        }
        return { valid: true, reason: "", epoch: epoch, date: dateText(back), time: timeText(back, false) };
    }

    function formatUtcOffset(offsetSeconds) {
        var sign = offsetSeconds < 0 ? "-" : "+";
        var absolute = Math.abs(offsetSeconds);
        var hours = Math.floor(absolute / 3600);
        var minutes = Math.floor((absolute - hours * 3600) / 60);
        return "UTC" + sign + pad2(hours) + ":" + pad2(minutes);
    }

    /** 与北京时差，精确到分钟：无时差 / 快 X 小时 Y 分 / 慢 X 分。 */
    function formatDifference(differenceSeconds) {
        if (differenceSeconds === 0) return "无时差";
        var absolute = Math.abs(differenceSeconds);
        var hours = Math.floor(absolute / 3600);
        var minutes = Math.floor((absolute - hours * 3600) / 60);
        var parts = [];
        if (hours) parts.push(hours + " 小时");
        if (minutes) parts.push(minutes + " 分");
        if (!parts.length) parts.push("0 分");
        return (differenceSeconds < 0 ? "慢 " : "快 ") + parts.join(" ");
    }

    var formatterCache = Object.create(null);

    function zoneFormatter(zoneId) {
        if (!formatterCache[zoneId]) {
            formatterCache[zoneId] = new Intl.DateTimeFormat("en-US", {
                timeZone: zoneId,
                year: "numeric",
                month: "2-digit",
                day: "2-digit",
                hour: "2-digit",
                minute: "2-digit",
                hourCycle: "h23"
            });
        }
        return formatterCache[zoneId];
    }

    /** 单个 IANA 区在同一 epoch 的当地日历；不支持的 id 返回 null。 */
    function zoneLocalParts(zoneId, epoch) {
        var parts = zoneFormatter(zoneId).formatToParts(new Date(epoch));
        var local = { year: 0, month: 0, day: 0, hour: 0, minute: 0 };
        for (var i = 0; i < parts.length; i += 1) {
            var type = parts[i].type;
            if (type === "year") local.year = Number(parts[i].value);
            else if (type === "month") local.month = Number(parts[i].value);
            else if (type === "day") local.day = Number(parts[i].value);
            else if (type === "hour") local.hour = Number(parts[i].value);
            else if (type === "minute") local.minute = Number(parts[i].value);
        }
        if (!local.year || !local.month || !local.day) return null;
        return local;
    }

    function dayNumber(parts) {
        return Date.UTC(parts.year, parts.month - 1, parts.day) / 86400000;
    }

    /**
     * 目录条目按同一 epoch 投影（I-4/I-5）：所有目标区都从同一 epoch 经 IANA 换算，
     * 不使用设备时区、固定时差或目录 offset；不合契约条目跳过，Intl 不支持的 id 计入
     * unsupported。返回新对象，不修改入参。
     */
    function projectZones(rawZones, epoch) {
        var result = { zones: [], unsupported: [] };
        if (!Array.isArray(rawZones) || typeof epoch !== "number" || !isFinite(epoch)) return result;
        var beijingDay = dayNumber(beijingParts(epoch));
        for (var i = 0; i < rawZones.length; i += 1) {
            var raw = rawZones[i];
            if (!raw || typeof raw !== "object") continue;
            if (typeof raw.id !== "string" || raw.id.trim() === "") continue;
            if (typeof raw.labelZh !== "string") continue;
            if (!Array.isArray(raw.aliases)) continue;
            var local;
            try {
                local = zoneLocalParts(raw.id, epoch);
            } catch (error) {
                local = null;
            }
            if (!local) {
                result.unsupported.push(raw.id);
                continue;
            }
            var offsetSeconds =
                (Date.UTC(local.year, local.month - 1, local.day, local.hour, local.minute) - epoch) / 1000;
            var differenceSeconds = offsetSeconds - BEIJING_OFFSET_SECONDS;
            var dayOffset = dayNumber(local) - beijingDay;
            result.zones.push({
                id: raw.id,
                labelZh: raw.labelZh,
                aliases: raw.aliases.slice(),
                localDate: dateText(local),
                localTime: timeText(local, false),
                offsetSeconds: offsetSeconds,
                offsetLabel: formatUtcOffset(offsetSeconds),
                differenceSeconds: differenceSeconds,
                differenceLabel: formatDifference(differenceSeconds),
                dayOffset: dayOffset,
                dayLabel: dayOffset === -1 ? "前一天" : (dayOffset === 1 ? "后一天" : "")
            });
        }
        return result;
    }

    /** 完整 UTC 偏移串（归一全角与减号后）→ 秒数；不是完整偏移串返回 null。 */
    function parseOffsetQuery(query) {
        var text = String(query == null ? "" : query)
            .trim()
            .toLowerCase()
            .replace(/＋/g, "+")
            .replace(/[－−–—]/g, "-")
            .replace(/\s+/g, "");
        var match = /^utc([+-])(\d{1,2})(?::(\d{2}))?$/.exec(text);
        if (!match) return null;
        var hours = Number(match[2]);
        var minutes = match[3] != null ? Number(match[3]) : 0;
        if (hours > 14 || minutes > 59) return null;
        var total = hours * 3600 + minutes * 60;
        return match[1] === "-" ? -total : total;
    }

    function regionOf(zoneId) {
        var head = String(zoneId).split("/")[0];
        if (head === "Europe" || head === "America" || head === "Asia" || head === "Africa") return head;
        if (head === "Pacific" || head === "Australia") return "PacificAustralia";
        return "Other";
    }

    /** 目录响应 → 组件元信息（保留 id/labelZh/aliases，丢弃目录 offset）。 */
    function normalizeCatalog(data) {
        if (!Array.isArray(data)) return null;
        var catalog = [];
        for (var i = 0; i < data.length; i += 1) {
            var item = data[i];
            if (!item || typeof item !== "object") continue;
            if (typeof item.id !== "string" || item.id.trim() === "") continue;
            if (typeof item.labelZh !== "string") continue;
            if (!Array.isArray(item.aliases)) continue;
            catalog.push({ id: item.id, labelZh: item.labelZh, aliases: item.aliases.slice() });
        }
        return catalog.length ? catalog : null;
    }

    // ------------------------------------------------------------------
    // DOM 小工具
    // ------------------------------------------------------------------

    function currentDocument() {
        if (typeof document !== "undefined" && document) return document;
        if (global && global.document) return global.document;
        return null;
    }

    function isElement(node) {
        return !!node && node.nodeType === 1;
    }

    function elementChildren(node) {
        var children = [];
        if (!node) return children;
        var childNodes = node.childNodes || [];
        for (var i = 0; i < childNodes.length; i += 1) {
            if (isElement(childNodes[i])) children.push(childNodes[i]);
        }
        return children;
    }

    function hasClass(node, name) {
        return isElement(node) && !!node.classList && node.classList.contains(name);
    }

    /** 固定模板（S-4）是唯一 DOM 来源：解析 + 克隆，动态文本另行以 textContent 写入。 */
    function buildFromTemplate(doc, html) {
        var template = doc.createElement("template");
        template.innerHTML = html;
        var content = template.content;
        var first = content ? content.firstElementChild : null;
        return first ? first.cloneNode(true) : null;
    }

    function focusNode(node) {
        if (node && typeof node.focus === "function") node.focus();
    }

    function blurNode(node) {
        if (node && typeof node.blur === "function") node.blur();
    }

    // ------------------------------------------------------------------
    // 组件实例
    // ------------------------------------------------------------------

    function createInstance(header, api, filterZones) {
        var doc = currentDocument();
        if (!doc) return null;
        var side = header.querySelector(".topnav-side");
        var logout = side ? side.querySelector("#logoutBtn") : null;
        if (!side || !logout) return null;
        if (header.querySelector("#worldClockTrigger") || header.querySelector("#worldClockPanel")) return null;
        var trigger = buildFromTemplate(doc, TRIGGER_HTML);
        var panel = buildFromTemplate(doc, PANEL_HTML);
        if (!trigger || !panel) return null;
        var nodes = {
            trigger: trigger,
            headerTime: trigger.querySelector("#worldClockHeaderTime"),
            chevron: trigger.querySelector(".world-clock-chevron"),
            panel: panel,
            now: panel.querySelector("#worldClockNow"),
            closeButton: panel.querySelector("#worldClockClose"),
            useNow: panel.querySelector("#worldClockUseNow"),
            date: panel.querySelector("#worldClockDate"),
            time: panel.querySelector("#worldClockTime"),
            inputError: panel.querySelector("#worldClockInputError"),
            search: panel.querySelector("#worldClockSearch"),
            region: panel.querySelector("#worldClockRegion"),
            common: panel.querySelector("#worldClockCommon"),
            globalTab: panel.querySelector("#worldClockGlobal"),
            loadStatus: panel.querySelector("#worldClockLoadStatus"),
            retry: panel.querySelector("#worldClockRetry"),
            count: panel.querySelector("#worldClockCount"),
            table: panel.querySelector("#worldClockTable"),
            rows: panel.querySelector("#worldClockRows"),
            empty: panel.querySelector("#worldClockEmpty"),
            unsupported: panel.querySelector("#worldClockUnsupported"),
            pager: panel.querySelector("#worldClockPager"),
            previous: panel.querySelector("#worldClockPrev"),
            pageLabel: panel.querySelector("#worldClockPageLabel"),
            next: panel.querySelector("#worldClockNext")
        };
        var missing = Object.keys(nodes).some(function (key) { return !nodes[key]; });
        if (missing) return null;

        var shell = findShell(header);
        var observers = [];
        var state = {
            destroyed: false,
            open: false,
            sessionVisible: true,
            selectedInitialized: false,
            selectedDate: "",
            selectedTime: "",
            epoch: null,
            catalogStatus: "idle",
            catalog: null,
            query: "",
            region: "",
            scope: "common",
            page: 0,
            requestSeq: 0,
            controller: null,
            timer: null,
            fitHandle: null,
            fitPending: false
        };
        var projectionMemo = null;

        side.insertBefore(trigger, logout);
        header.appendChild(panel);
        header.classList.add(HEADER_CLASS);

        trigger.addEventListener("click", onTriggerClick);
        nodes.closeButton.addEventListener("click", onCloseClick);
        nodes.useNow.addEventListener("click", onUseNowClick);
        nodes.date.addEventListener("input", onInput);
        nodes.time.addEventListener("input", onInput);
        nodes.search.addEventListener("input", onSearchInput);
        nodes.region.addEventListener("change", onRegionChange);
        nodes.common.addEventListener("click", onCommonClick);
        nodes.globalTab.addEventListener("click", onGlobalClick);
        nodes.previous.addEventListener("click", onPreviousClick);
        nodes.next.addEventListener("click", onNextClick);
        nodes.retry.addEventListener("click", loadCatalog);
        doc.addEventListener("keydown", onKeyDown);
        doc.addEventListener("pointerdown", onPointerDown);
        doc.addEventListener("focusin", onFocusIn);
        doc.addEventListener("visibilitychange", onVisibilityChange);

        observeShell(observers);
        observeLayout(observers);
        var ready = doc.fonts && doc.fonts.ready;
        if (ready && typeof ready.then === "function") {
            ready.then(function () { scheduleFit(); }, function () { /* 字体清单不可用则跳过 */ });
        }

        state.sessionVisible = isSessionVisible();
        if (state.sessionVisible) startTimer();

        var handle = {
            open: open,
            close: close,
            destroy: destroy
        };
        return handle;

        // ---------------- 布局与可见性 ----------------

        function findShell() {
            var node = header.parentNode;
            while (node) {
                if (hasClass(node, "app-shell")) return node;
                node = node.parentNode;
            }
            return null;
        }

        function isSessionVisible() {
            if (!shell) return true;
            if (shell.hidden) return false;
            var display = shell.style && shell.style.display ? String(shell.style.display) : "";
            return display !== "none";
        }

        function observeShell(registry) {
            if (!shell || typeof MutationObserver !== "function") return;
            var observer = new MutationObserver(syncSessionVisibility);
            observer.observe(shell, { attributes: true, attributeFilter: ["style", "hidden"] });
            registry.push(observer);
        }

        function observeLayout(registry) {
            if (typeof ResizeObserver === "function") {
                var resizeObserver = new ResizeObserver(scheduleFit);
                resizeObserver.observe(header);
                registry.push(resizeObserver);
            }
            if (typeof MutationObserver !== "function") return;
            var nav = header.querySelector(".nav-tabs");
            if (nav) {
                var navObserver = new MutationObserver(scheduleFit);
                navObserver.observe(nav, {
                    childList: true,
                    characterData: true,
                    subtree: true,
                    attributes: true,
                    attributeFilter: ["hidden"]
                });
                registry.push(navObserver);
            }
            var userDisplay = header.querySelector("#currentUserDisplay");
            if (userDisplay) {
                var userObserver = new MutationObserver(scheduleFit);
                userObserver.observe(userDisplay, { childList: true, characterData: true, subtree: true });
                registry.push(userObserver);
            }
        }

        function syncSessionVisibility() {
            if (state.destroyed) return;
            var visible = isSessionVisible();
            if (visible === state.sessionVisible) return;
            state.sessionVisible = visible;
            if (visible) {
                startTimer();
                scheduleFit();
                return;
            }
            resetSession();
        }

        function scheduleFit() {
            if (state.destroyed || state.fitPending) return;
            state.fitPending = true;
            if (typeof requestAnimationFrame !== "function") {
                state.fitPending = false;
                fitHeader();
                return;
            }
            state.fitHandle = requestAnimationFrame(function () {
                state.fitHandle = null;
                state.fitPending = false;
                fitHeader();
            });
        }

        function cancelFit() {
            if (state.fitHandle !== null && typeof cancelAnimationFrame === "function") {
                cancelAnimationFrame(state.fitHandle);
            }
            state.fitHandle = null;
            state.fitPending = false;
        }

        function computedStyle(node) {
            var owner = node && node.ownerDocument;
            var view = owner && owner.defaultView;
            if (view && typeof view.getComputedStyle === "function") return view.getComputedStyle(node);
            if (typeof getComputedStyle === "function") return getComputedStyle(node);
            return null;
        }

        function styleNumber(node, property, fallbackProperty) {
            var styles = computedStyle(node);
            if (!styles) return 0;
            var raw = styles[property];
            if ((raw === undefined || raw === null || raw === "") && fallbackProperty) raw = styles[fallbackProperty];
            var value = parseFloat(raw);
            return isFinite(value) ? value : 0;
        }

        function nodeWidth(node) {
            if (!node) return 0;
            if (typeof node.getBoundingClientRect === "function") {
                var rect = node.getBoundingClientRect();
                if (rect && isFinite(rect.width)) return rect.width;
            }
            var offset = Number(node.offsetWidth);
            return isFinite(offset) ? offset : 0;
        }

        function outerWidth(node) {
            return nodeWidth(node) + styleNumber(node, "marginLeft") + styleNumber(node, "marginRight");
        }

        function rowWidth(node) {
            var children = elementChildren(node);
            if (!children.length) return outerWidth(node);
            var gap = styleNumber(node, "columnGap", "gap");
            var total = 0;
            for (var i = 0; i < children.length; i += 1) total += outerWidth(children[i]);
            if (children.length > 1) total += gap * (children.length - 1);
            return total;
        }

        function navWidth(nav) {
            var tabs = elementChildren(nav).filter(function (child) { return hasClass(child, "nav-tab"); });
            if (!tabs.length) return rowWidth(nav);
            var gap = styleNumber(nav, "columnGap", "gap");
            var total = 0;
            for (var i = 0; i < tabs.length; i += 1) total += outerWidth(tabs[i]);
            if (tabs.length > 1) total += gap * (tabs.length - 1);
            return total;
        }

        /** 完整态所需宽度：header 自身 padding + 各子块自身宽度与间距相加。 */
        function requiredWidth() {
            var children = elementChildren(header).filter(function (child) { return child !== panel; });
            var gaps = Math.max(0, children.length - 1);
            var total = styleNumber(header, "paddingLeft") + styleNumber(header, "paddingRight") +
                gaps * styleNumber(header, "columnGap", "gap");
            for (var i = 0; i < children.length; i += 1) {
                var child = children[i];
                if (hasClass(child, "nav-tabs")) total += navWidth(child);
                else if (hasClass(child, "topnav-side")) total += rowWidth(child);
                else total += outerWidth(child);
            }
            return total;
        }

        function headerWidth() {
            var client = Number(header.clientWidth);
            if (isFinite(client) && client > 0) return client;
            return nodeWidth(header);
        }

        /** 先同步恢复完整态测量，再按可用宽度决定是否降级为图标（同一回调内完成）。 */
        function fitHeader() {
            if (state.destroyed || !state.sessionVisible) return;
            trigger.classList.remove(ICON_ONLY_CLASS);
            var required = requiredWidth();
            var available = headerWidth();
            if (required - available >= FIT_TOLERANCE_PX) trigger.classList.add(ICON_ONLY_CLASS);
        }

        // ---------------- 实时钟 ----------------

        function startTimer() {
            if (state.timer !== null || state.destroyed) return;
            refreshClock();
            state.timer = setInterval(tick, TICK_INTERVAL_MS);
            if (state.timer && typeof state.timer.unref === "function") state.timer.unref();
        }

        function stopTimer() {
            if (state.timer === null) return;
            clearInterval(state.timer);
            state.timer = null;
        }

        function tick() {
            if (state.destroyed) return;
            refreshClock();
        }

        function refreshClock() {
            var nowMs = Date.now();
            var parts = beijingParts(nowMs);
            nodes.now.textContent = dateText(parts) + " " + timeText(parts, true);
            nodes.now.setAttribute("datetime", new Date(nowMs).toISOString());
            nodes.headerTime.textContent =
                pad2(parts.month) + "月" + pad2(parts.day) + "日 " + timeText(parts, false);
        }

        function onVisibilityChange() {
            if (state.destroyed) return;
            if (doc.hidden === true) {
                stopTimer();
                return;
            }
            if (state.sessionVisible) startTimer();
        }

        // ---------------- 打开 / 关闭 ----------------

        function open() {
            if (state.destroyed || !state.sessionVisible) return;
            if (!state.selectedInitialized) {
                var nowParts = beijingParts(Date.now());
                state.selectedDate = dateText(nowParts);
                state.selectedTime = timeText(nowParts, false);
                state.selectedInitialized = true;
            }
            nodes.date.value = state.selectedDate;
            nodes.time.value = state.selectedTime;
            state.open = true;
            panel.hidden = false;
            trigger.setAttribute("aria-expanded", "true");
            nodes.chevron.textContent = "⌃";
            applySelection();
            if (state.catalogStatus === "idle") loadCatalog();
            render();
            focusNode(nodes.date);
        }

        function close(restoreFocus) {
            if (state.destroyed) return;
            state.open = false;
            panel.hidden = true;
            trigger.setAttribute("aria-expanded", "false");
            nodes.chevron.textContent = "⌄";
            if (restoreFocus) {
                focusNode(trigger);
                return;
            }
            if (doc.activeElement && panel.contains(doc.activeElement)) blurNode(doc.activeElement);
        }

        function onTriggerClick() {
            if (state.open) close(true);
            else open();
        }

        function onCloseClick() {
            close(true);
        }

        function onKeyDown(event) {
            if (!state.open) return;
            if (event.key !== "Escape" && event.key !== "Esc") return;
            close(true);
        }

        function onPointerDown(event) {
            if (!state.open) return;
            var target = event.target;
            if (panel.contains(target) || trigger.contains(target)) return;
            close(false);
        }

        /** Tab 把焦点移出浮层与 trigger 时关闭，但绝不回抢焦点（I-9）。 */
        function onFocusIn(event) {
            if (!state.open) return;
            var target = event.target;
            if (panel.contains(target) || trigger.contains(target)) return;
            close(false);
        }

        // ---------------- 选择与筛选 ----------------

        function applySelection() {
            state.selectedDate = nodes.date.value;
            state.selectedTime = nodes.time.value;
            var parsed = parseBeijingInput(state.selectedDate, state.selectedTime);
            state.epoch = parsed.valid ? parsed.epoch : null;
            state.page = 0;
        }

        function onInput() {
            applySelection();
            render();
        }

        function onUseNowClick() {
            var nowParts = beijingParts(Date.now());
            state.selectedDate = dateText(nowParts);
            state.selectedTime = timeText(nowParts, false);
            state.selectedInitialized = true;
            nodes.date.value = state.selectedDate;
            nodes.time.value = state.selectedTime;
            applySelection();
            render();
        }

        function onSearchInput() {
            state.query = nodes.search.value;
            if (state.query.trim() !== "") state.scope = "global";
            state.page = 0;
            render();
        }

        function onRegionChange() {
            state.region = nodes.region.value;
            if (state.region !== "") state.scope = "global";
            state.page = 0;
            render();
        }

        function selectScope(scope) {
            state.scope = scope;
            state.query = "";
            state.region = "";
            state.page = 0;
            nodes.search.value = "";
            nodes.region.value = "";
            render();
        }

        function onCommonClick() {
            selectScope("common");
        }

        function onGlobalClick() {
            selectScope("global");
        }

        function onPreviousClick() {
            state.page = Math.max(0, state.page - 1);
            render();
        }

        function onNextClick() {
            state.page += 1;
            render();
        }

        // ---------------- 目录 ----------------

        function loadCatalog() {
            if (state.destroyed || state.catalog || state.catalogStatus === "loading") return;
            state.catalogStatus = "loading";
            state.requestSeq += 1;
            var seq = state.requestSeq;
            var date = state.selectedDate || dateText(beijingParts(Date.now()));
            var controller = typeof AbortController === "function" ? new AbortController() : null;
            state.controller = controller;
            render();
            var response;
            try {
                response = api(CATALOG_PATH + date, controller ? { signal: controller.signal } : undefined);
            } catch (error) {
                response = Promise.reject(error);
            }
            Promise.resolve(response).then(function (data) {
                if (!isCurrentRequest(seq)) return;
                state.controller = null;
                var catalog = normalizeCatalog(data);
                if (catalog) {
                    state.catalog = catalog;
                    state.catalogStatus = "ready";
                    projectionMemo = null;
                } else {
                    state.catalogStatus = "error";
                }
                render();
            }, function () {
                if (!isCurrentRequest(seq)) return;
                state.controller = null;
                state.catalogStatus = "error";
                render();
            });
        }

        function isCurrentRequest(seq) {
            return !state.destroyed && seq === state.requestSeq;
        }

        function invalidateRequests() {
            state.requestSeq += 1;
            var controller = state.controller;
            state.controller = null;
            if (controller && typeof controller.abort === "function") {
                try {
                    controller.abort();
                } catch (error) {
                    /* 已经中止 */
                }
            }
        }

        function resetSession() {
            invalidateRequests();
            stopTimer();
            close(false);
            state.selectedInitialized = false;
            state.selectedDate = "";
            state.selectedTime = "";
            state.epoch = null;
            state.catalog = null;
            state.catalogStatus = "idle";
            state.query = "";
            state.region = "";
            state.scope = "common";
            state.page = 0;
            projectionMemo = null;
            nodes.date.value = "";
            nodes.time.value = "";
            nodes.search.value = "";
            nodes.region.value = "";
            nodes.rows.textContent = "";
            INPUT_STATE_ATTRIBUTES.forEach(function (name) {
                nodes.date.removeAttribute(name);
                nodes.time.removeAttribute(name);
            });
        }

        // ---------------- 渲染 ----------------

        function currentProjection() {
            if (projectionMemo && projectionMemo.epoch === state.epoch && projectionMemo.catalog === state.catalog) {
                return projectionMemo.value;
            }
            var value = projectZones(state.catalog, state.epoch);
            projectionMemo = { epoch: state.epoch, catalog: state.catalog, value: value };
            return value;
        }

        function filteredZones(projected) {
            var list = state.scope === "common" ? commonZones(projected) : projected.slice();
            if (state.region) {
                list = list.filter(function (zone) { return regionOf(zone.id) === state.region; });
            }
            var query = state.query.trim();
            if (!query) return list;
            var offsetSeconds = parseOffsetQuery(query);
            if (offsetSeconds !== null) {
                return list.filter(function (zone) { return zone.offsetSeconds === offsetSeconds; });
            }
            var matched = filterZones(list, query);
            return Array.isArray(matched) ? matched : [];
        }

        function commonZones(projected) {
            var byId = Object.create(null);
            for (var i = 0; i < projected.length; i += 1) byId[projected[i].id] = projected[i];
            var list = [];
            for (var j = 0; j < COMMON_ZONE_IDS.length; j += 1) {
                var zone = byId[COMMON_ZONE_IDS[j]];
                if (zone) list.push(zone);
            }
            return list;
        }

        function buildRow(zone) {
            var row = buildFromTemplate(doc, ROW_HTML);
            row.querySelector(".world-clock-name").textContent = zone.labelZh;
            row.querySelector(".world-clock-zone").textContent = zone.id;
            row.querySelector('[data-field="time"]').textContent = zone.localTime;
            row.querySelector(".world-clock-date").textContent = zone.localDate;
            row.querySelector('[data-field="difference"]').textContent = zone.differenceLabel;
            row.querySelector('[data-field="offset"]').textContent = zone.offsetLabel;
            var day = row.querySelector(".world-clock-day");
            if (zone.dayLabel) {
                day.textContent = zone.dayLabel;
                day.hidden = false;
            }
            return row;
        }

        function syncControls() {
            var blocked = state.catalogStatus === "loading" || state.catalogStatus === "error";
            nodes.search.disabled = blocked;
            nodes.region.disabled = blocked;
            nodes.retry.disabled = state.catalogStatus === "loading";
            nodes.common.setAttribute("aria-pressed", state.scope === "common" ? "true" : "false");
            nodes.globalTab.setAttribute("aria-pressed", state.scope === "global" ? "true" : "false");
        }

        function render() {
            if (state.destroyed) return;
            syncControls();
            if (state.epoch === null) {
                nodes.inputError.hidden = false;
                nodes.date.setAttribute("aria-invalid", "true");
                nodes.time.setAttribute("aria-invalid", "true");
                nodes.table.hidden = true;
                nodes.empty.hidden = true;
                nodes.pager.hidden = true;
                nodes.unsupported.hidden = true;
                nodes.count.textContent = "";
                nodes.rows.textContent = "";
                return;
            }
            nodes.inputError.hidden = true;
            INPUT_STATE_ATTRIBUTES.forEach(function (name) {
                nodes.date.removeAttribute(name);
                nodes.time.removeAttribute(name);
            });
            if (state.catalogStatus !== "ready" || !state.catalog) {
                var statusText = "";
                if (state.catalogStatus === "loading") statusText = "正在加载时区…";
                else if (state.catalogStatus === "error") statusText = "时区加载失败，请重试。";
                nodes.loadStatus.textContent = statusText;
                nodes.loadStatus.hidden = statusText === "";
                nodes.loadStatus.classList.toggle("world-clock-error", state.catalogStatus === "error");
                nodes.retry.hidden = state.catalogStatus !== "error";
                nodes.table.hidden = true;
                nodes.empty.hidden = true;
                nodes.pager.hidden = true;
                nodes.unsupported.hidden = true;
                nodes.count.textContent = "";
                nodes.rows.textContent = "";
                return;
            }
            nodes.loadStatus.textContent = "";
            nodes.loadStatus.hidden = true;
            nodes.loadStatus.classList.remove("world-clock-error");
            nodes.retry.hidden = true;
            var projection = currentProjection();
            nodes.unsupported.textContent = projection.unsupported.length
                ? "当前浏览器有 " + projection.unsupported.length + " 个目录时区暂不可用。"
                : "";
            nodes.unsupported.hidden = projection.unsupported.length === 0;
            var zones = filteredZones(projection.zones);
            var pageCount = Math.max(1, Math.ceil(zones.length / PAGE_SIZE));
            if (state.page > pageCount - 1) state.page = pageCount - 1;
            if (state.page < 0) state.page = 0;
            var visible = zones.slice(state.page * PAGE_SIZE, state.page * PAGE_SIZE + PAGE_SIZE);
            nodes.rows.textContent = "";
            for (var i = 0; i < visible.length; i += 1) nodes.rows.appendChild(buildRow(visible[i]));
            nodes.count.textContent = "共 " + zones.length + " 个时区";
            nodes.table.hidden = visible.length === 0;
            nodes.empty.hidden = visible.length !== 0;
            nodes.pager.hidden = zones.length <= PAGE_SIZE;
            nodes.previous.disabled = state.page <= 0;
            nodes.next.disabled = state.page >= pageCount - 1;
            nodes.pageLabel.textContent = "第 " + (state.page + 1) + " / " + pageCount + " 页";
        }

        // ---------------- 销毁 ----------------

        function destroy() {
            if (state.destroyed) return;
            state.destroyed = true;
            invalidateRequests();
            stopTimer();
            cancelFit();
            trigger.removeEventListener("click", onTriggerClick);
            nodes.closeButton.removeEventListener("click", onCloseClick);
            nodes.useNow.removeEventListener("click", onUseNowClick);
            nodes.date.removeEventListener("input", onInput);
            nodes.time.removeEventListener("input", onInput);
            nodes.search.removeEventListener("input", onSearchInput);
            nodes.region.removeEventListener("change", onRegionChange);
            nodes.common.removeEventListener("click", onCommonClick);
            nodes.globalTab.removeEventListener("click", onGlobalClick);
            nodes.previous.removeEventListener("click", onPreviousClick);
            nodes.next.removeEventListener("click", onNextClick);
            nodes.retry.removeEventListener("click", loadCatalog);
            doc.removeEventListener("keydown", onKeyDown);
            doc.removeEventListener("pointerdown", onPointerDown);
            doc.removeEventListener("focusin", onFocusIn);
            doc.removeEventListener("visibilitychange", onVisibilityChange);
            for (var i = 0; i < observers.length; i += 1) {
                var observer = observers[i];
                if (observer && typeof observer.disconnect === "function") observer.disconnect();
            }
            observers.length = 0;
            if (doc.activeElement && panel.contains(doc.activeElement)) blurNode(doc.activeElement);
            if (typeof trigger.remove === "function") trigger.remove();
            if (typeof panel.remove === "function") panel.remove();
            header.classList.remove(HEADER_CLASS);
            projectionMemo = null;
            if (liveInstance && liveInstance.handle === handle) liveInstance = null;
        }
    }

    var liveInstance = null;

    function mount(options) {
        if (liveInstance) return liveInstance.handle;
        var config = options || {};
        if (typeof config.api !== "function" || typeof config.filterZones !== "function") return null;
        if (!isElement(config.header) || typeof config.header.querySelector !== "function") return null;
        var handle = createInstance(config.header, config.api, config.filterZones);
        if (!handle) return null;
        liveInstance = { handle: handle };
        return handle;
    }

    function findHeader(doc) {
        var shell = doc.querySelector(".app-shell");
        var header = shell ? shell.querySelector(".topnav") : null;
        if (!header) header = doc.querySelector(".topnav");
        return header || null;
    }

    /** 自动启动：只在预期宿主与宿主依赖都在场时挂载，否则静默跳过（不影响 app.js）。 */
    function boot() {
        if (liveInstance) return liveInstance.handle;
        var doc = currentDocument();
        if (!doc) return null;
        var header = findHeader(doc);
        var api = global && typeof global.api === "function" ? global.api : null;
        var mailbox = global ? global.MailboxMeeting : null;
        var filterZones = mailbox && typeof mailbox.filterZones === "function" ? mailbox.filterZones : null;
        if (!header || !api || !filterZones) return null;
        return mount({ header: header, api: api, filterZones: filterZones });
    }

    var API = Object.freeze({
        mount: mount,
        parseBeijingInput: parseBeijingInput,
        projectZones: projectZones,
        templates: Object.freeze({
            trigger: TRIGGER_HTML,
            panel: PANEL_HTML,
            row: ROW_HTML
        })
    });

    if (global) {
        try {
            global.WorldClock = API;
        } catch (error) {
            /* noop */
        }
    }
    if (typeof module !== "undefined" && module.exports) {
        module.exports = API;
    }

    if (typeof document !== "undefined" && document) {
        if (document.readyState === "loading") {
            document.addEventListener("DOMContentLoaded", boot);
        } else {
            boot();
        }
    }
})(typeof window !== "undefined" ? window : (typeof globalThis !== "undefined" ? globalThis : this));
