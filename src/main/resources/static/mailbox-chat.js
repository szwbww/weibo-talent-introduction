/**
 * 收发件箱专家聊天（mailbox-refinement 02 · I-1..I-8 / 样式契约 S-1..S-7）。
 *
 * 布局：S-1 两栏（专家列表 + 会话）；聊天挂载时 view-mailbox 加 mc-refined，
 * #mailboxRefreshBtn 移到 panel-head-actions 首位，退出还原；唯一筛选节点
 * （mailboxFilter* 七字段 + #mailboxSearchBtn）在聊天时迁入 ⋯ popover（S-2），
 * 字段修改只是草稿，应用/Enter 才生效，重置/清除保留 tab 与 q；退出聊天还原父节点。
 *
 * 关键约束（与宿主/既有模块的关系）：
 * - 全部/已提供/跟进中/已回复/待处理专家请求走 conversations API；列表排序唯一权威在服务端（01），
 *   UI 不 sort、不发 waitingReply。
 * - 第五个 tab「待匹配」是邮件级队列（I-1/I-3/I-8）：只请求
 *   /api/mail/unmatched-inbound?unmatchedOnly=true（未关联专家的 MANUAL_REVIEW 来信），
 *   选中键为独立 selectedUnmatchedId（不写 selectedContactId/sessionStore），
 *   详情复用唯一 #unmatchedDetailPanel —— 经宿主 mcHostMountUnmatchedDetail 挂入右栏
 *   .mc-scroll，离开 tab/记录消失/切页/unmount 前必须 mcHostReleaseUnmatchedDetail 归还。
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
    const CHIP_PROVIDED = "provided";
    const CHIP_FOLLOWED = "followed";
    const CHIP_REPLIED = "replied";
    const CHIP_PENDING = "pending";
    // 02（I-1/S-1）：三态标记；固定顺序 全部/已提供/跟进中/待处理/已挂起/已回复/待匹配。
    const CHIP_SUSPENDED = "suspended";
    const CHIP_UNMATCHED = "unmatched";

    const FILTER_CHIPS = [
        { key: CHIP_ALL, label: "全部" },
        { key: CHIP_PROVIDED, label: "已提供" },
        { key: CHIP_FOLLOWED, label: "跟进中" },
        { key: CHIP_PENDING, label: "待处理" },
        { key: CHIP_SUSPENDED, label: "已挂起" },
        { key: CHIP_REPLIED, label: "已回复" },
        { key: CHIP_UNMATCHED, label: "待匹配" }
    ];

    // 02（S-1）：待处理/已挂起各有专家数计数 span；不可用时隐藏，绝不伪造 0。
    const CHIP_COUNT_KEYS = [CHIP_PENDING, CHIP_SUSPENDED];

    // 02（I-1）：progressStatus 三态唯一真值（NONE=未标记）。旧响应完全缺字段才可从
    // followed 派生；字段存在但非法一律显示「状态不可用」，不冒充 NONE。
    const PROGRESS_NONE = "NONE";
    const PROGRESS_FOLLOWING = "FOLLOWING";
    const PROGRESS_PROVIDED = "PROVIDED";
    const PROGRESS_STATUSES = [PROGRESS_NONE, PROGRESS_FOLLOWING, PROGRESS_PROVIDED];
    // 02（S-2/I-1）：标签与菜单项的唯一真值表；取消均写 NONE，绝不出现「进入跟进/进入提供」。
    const PROGRESS_MENU = {
        [PROGRESS_NONE]: { label: "未标记", options: [[PROGRESS_FOLLOWING, "跟进中"], [PROGRESS_PROVIDED, "已提供"]] },
        [PROGRESS_FOLLOWING]: { label: "跟进中", options: [[PROGRESS_NONE, "取消跟进"], [PROGRESS_PROVIDED, "已提供"]] },
        [PROGRESS_PROVIDED]: { label: "已提供", options: [[PROGRESS_FOLLOWING, "跟进中"], [PROGRESS_NONE, "取消提供"]] }
    };
    const PROGRESS_INVALID_LABEL = "状态不可用";

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
    // 会议确认（fast-p 04）：mailbox-chat 侧宿主接入。
    // 组件（window.MailboxMeeting）缺席时全部降级 —— 不生成 trigger/附件卡，
    // 不调用任何会议函数、不向未定义对象发消息；草稿不含 meeting 字段仍走旧路径。
    // meeting-* 规则声明在独立 meeting-confirmation.css，不在 mailbox-chat.css；
    // 既有样式白名单契约只认 mailbox-chat.css/styles.css 字面 class，因此这里在
    // 运行时拼 "meeting-" 前缀，模板不出现字面量 meeting-* class。
    // ------------------------------------------------------------------

    function meetingLib() {
        return (typeof global.MailboxMeeting !== "undefined" && global.MailboxMeeting)
            ? global.MailboxMeeting
            : null;
    }

    function meetingEnabled() {
        const lib = meetingLib();
        return !!(lib && typeof lib.create === "function");
    }

    function mcCls(name) {
        return "meeting-" + String(name);
    }

    // 草稿 meeting 快照 revision：模块级单调本地整数（I-7）
    let meetingDraftSeq = 0;

    // 会议发送中的模块级 inFlight（ownerKey|targetKey；volatile，不写缓存/DB）
    const meetingInFlight = new Set();


    // ------------------------------------------------------------------
    // 联系时间（fast-p 2026-10-02 c3）：状态行紧凑所在地 + 北京时间推荐。
    // 真值在后台：前端只读 /api/mail/contact-locations/*，不推断国家、不重算习惯、
    // 不写 localStorage/sessionStorage；所有日期按显式时区（Asia/Shanghai /
    // effectiveZoneId）格式化，绝不交给设备默认时区。弹窗是自有原生 dialog，
    // 直接挂 document.body（.mc-conversation overflow:hidden、.panel backdrop-filter
    // 的包含块会裁剪 fixed 浮层），关闭只移除自有节点。
    // ------------------------------------------------------------------

    const CONTACT_TIMING_BEIJING_ZONE = "Asia/Shanghai";
    const CONTACT_TIMING_LOADING_TEXT = "推荐计算中…";
    const CONTACT_TIMING_ERROR_TEXT = "推荐暂不可用";
    const CONTACT_TIMING_SAVED_NO_TIMING_TEXT = "所在地已保存，推荐更新失败";
    const CONTACT_TIMING_SAVE_ERROR_TEXT = "保存失败，请重试";
    const CONTACT_TIMING_LOAD_ERROR_TEXT = "所在地配置加载失败";
    const CONTACT_TIMING_COUNTRY_REQUIRED_TEXT = "请选择国家 / 地区";

    // 每次打开弹窗生成唯一 id 前缀，避免同页不同实例的 label/id 互撞。
    let contactTimingInstanceSeq = 0;


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

    // 02（I-1）：菜单/标签唯一定义查询；非法状态返回 null（渲染为「状态不可用」）。
    function progressMenuDef(status) {
        return PROGRESS_MENU[status] || null;
    }

    function isProgressStatus(value) {
        return PROGRESS_STATUSES.indexOf(value) >= 0;
    }

    // 02（I-1）：解析一条列表/挂起对象的真实状态。字段完全缺失才从 followed 派生；
    // 字段存在但非法（含 null/未知值）返回 "INVALID"，绝不降级成 NONE。
    function progressStatusOf(item) {
        if (!item) return PROGRESS_NONE;
        if (item.progressStatus === undefined) {
            return item.followed === true ? PROGRESS_FOLLOWING : PROGRESS_NONE;
        }
        return isProgressStatus(item.progressStatus) ? item.progressStatus : "INVALID";
    }

    function progressFollowed(status) {
        return status === PROGRESS_FOLLOWING;
    }

    // 人工富文本换行规范化：普通换行保留，连续空行最多保留一行；与服务端保持一致。
    function normalizeManualTextLineBreaks(value) {
        const text = value == null ? "" : String(value);
        if (!text) return text;
        let result = "";
        let pendingBlankLine = false;
        text.replace(/\r\n?/g, "\n").split("\n").forEach((line) => {
            if (/^[ \t]*$/.test(line)) {
                if (result) pendingBlankLine = true;
                return;
            }
            if (result) result += pendingBlankLine ? "\n\n" : "\n";
            result += line;
            pendingBlankLine = false;
        });
        return result;
    }

    const MANUAL_CONSECUTIVE_BR = /<br\s*\/?>(?:\s*<br\s*\/?>)+/gi;
    const MANUAL_EMPTY_BLOCK_RUN = /(<(?:p|div)(?:\s[^>]*)?>(?:\s|&nbsp;|&amp;nbsp;|<br\s*\/?>)*<\/(?:p|div)>)(?:\s*<(?:p|div)(?:\s[^>]*)?>(?:\s|&nbsp;|&amp;nbsp;|<br\s*\/?>)*<\/(?:p|div)>)+/gi;

    function normalizeManualRichHtmlLineBreaks(value) {
        let html = value == null ? "" : String(value);
        if (!html) return html;
        for (let pass = 0; pass < 8; pass += 1) {
            const next = html.replace(MANUAL_CONSECUTIVE_BR, "<br><br>").replace(MANUAL_EMPTY_BLOCK_RUN, "$1");
            if (next === html) return html;
            html = next;
        }
        return html;
    }

    function datePart(iso) {
        return String(iso || "").slice(0, 10);
    }

    function timePart(iso) {
        const value = String(iso || "");
        const idx = value.indexOf("T");
        return idx >= 0 ? value.slice(idx + 1, idx + 6) : "";
    }

    // --------------------------------------------------------------
    // 上次回复（fast-p 2026-10-02 · mailbox-last-reply-time；I-1..I-5 / S-1..S-3）
    // 唯一数据来源：当前 summary 的 latestInbound.receivedAt（I-1）；接口是无 offset 的
    // ISO_LOCAL_DATE_TIME，一律按北京时间解释（I-2）。本块只做纯展示，不写任何缓存/状态（I-5）。
    // --------------------------------------------------------------

    const LAST_REPLY_ZONE_LABEL = "北京时间";
    const LAST_REPLY_EMPTY_TEXT = "尚未回复";
    const LAST_REPLY_UNAVAILABLE_TEXT = "回复时间暂不可用";

    /**
     * I-2：无 offset 的本地日期时间 → 北京时间显示模型 { display, title, datetime }，不可信时 null。
     * 支持日期+时分、可选秒与任意位小数秒（截断到毫秒）；只接受严格完整字符串（不做 trim，前后空白即不可用），
     * 拒绝空白/普通日期文本/非法分量，且对照输入分量拒绝 Date 自动进位（如 2 月 30 日）与 Intl 异常。秒仅校验不显示。
     */
    function formatLastReplyTime(receivedAt) {
        const raw = typeof receivedAt === "string" ? receivedAt : "";
        const match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2})(?:\.(\d+))?)?$/.exec(raw);
        if (!match) return null;
        const year = Number(match[1]);
        const month = Number(match[2]);
        const day = Number(match[3]);
        const hour = Number(match[4]);
        const minute = Number(match[5]);
        const second = match[6] == null ? 0 : Number(match[6]);
        if (month < 1 || month > 12) return null;
        if (day < 1 || day > 31) return null;
        if (hour > 23 || minute > 59 || second > 59) return null;
        const intl = global.Intl;
        if (!intl || typeof intl.DateTimeFormat !== "function") return null;
        // 显式附加 +08:00 按北京时间解释；小数秒截取到毫秒精度（界面不显示毫秒）。
        const fraction = match[7] ? "." + match[7].slice(0, 3) : "";
        const date = new Date(`${match[1]}-${match[2]}-${match[3]}T${match[4]}:${match[5]}:${String(second).padStart(2, "0")}${fraction}+08:00`);
        if (Number.isNaN(date.getTime())) return null;
        let parts;
        try {
            parts = new intl.DateTimeFormat("zh-CN", {
                timeZone: "Asia/Shanghai",
                hourCycle: "h23",
                year: "numeric",
                month: "2-digit",
                day: "2-digit",
                weekday: "long",
                hour: "2-digit",
                minute: "2-digit",
                second: "2-digit"
            }).formatToParts(date);
        } catch (e) {
            return null;
        }
        const fields = {};
        (parts || []).forEach((part) => {
            if (part && part.type && part.type !== "literal") fields[part.type] = part.value;
        });
        if (!fields.year || !fields.month || !fields.day || !fields.weekday || !fields.hour || !fields.minute) return null;
        if (Number(fields.year) !== year || Number(fields.month) !== month || Number(fields.day) !== day) return null;
        if (Number(fields.hour) % 24 !== hour || Number(fields.minute) !== minute) return null;
        const dateLabel = `${fields.year}-${fields.month}-${fields.day}`;
        const clock = `${String(Number(fields.hour) % 24).padStart(2, "0")}:${fields.minute}`;
        const display = `${dateLabel} ${fields.weekday} ${clock}`;
        return {
            display,
            title: `${LAST_REPLY_ZONE_LABEL} ${display}`,
            datetime: `${dateLabel}T${clock}:00+08:00`
        };
    }

    /**
     * I-1/I-3：列表与详情共用的展示模型。
     * kind = time（正常北京时间）
     * | none（仅严格 `latestInbound === null` 且 `receivedCount === 0`）
     * | unavailable（`latestInbound` 缺失/undefined/数组/非对象/结构异常，或时间空白、非法；
     *   这些一律不可用，与 receivedCount 无关；`null && receivedCount > 0` 同样不可用）。
     */
    function lastReplyDisplay(summary) {
        const item = summary || {};
        const inbound = item.latestInbound;
        if (inbound === null) {
            if (item.receivedCount === 0) {
                return { kind: "none", text: LAST_REPLY_EMPTY_TEXT, aria: LAST_REPLY_EMPTY_TEXT };
            }
            return { kind: "unavailable", text: LAST_REPLY_UNAVAILABLE_TEXT, aria: LAST_REPLY_UNAVAILABLE_TEXT };
        }
        if (typeof inbound !== "object" || Array.isArray(inbound)) {
            return { kind: "unavailable", text: LAST_REPLY_UNAVAILABLE_TEXT, aria: LAST_REPLY_UNAVAILABLE_TEXT };
        }
        const formatted = formatLastReplyTime(inbound.receivedAt);
        if (!formatted) return { kind: "unavailable", text: LAST_REPLY_UNAVAILABLE_TEXT, aria: LAST_REPLY_UNAVAILABLE_TEXT };
        return {
            kind: "time",
            display: formatted.display,
            title: formatted.title,
            datetime: formatted.datetime,
            text: formatted.display,
            aria: formatted.title
        };
    }

    /** I-2：time 元素（正常）或 empty span（空/异常）；普通文本与属性值都经 escapeText（S-1/S-2）。 */
    function lastReplyContentMarkup(display) {
        if (display && display.kind === "time") {
            return `<time datetime="${escapeText(display.datetime)}" title="${escapeText(display.title)}">${escapeText(display.display)}</time>`;
        }
        return `<span class="mailbox-reply-empty">${escapeText(display ? display.text : LAST_REPLY_UNAVAILABLE_TEXT)}</span>`;
    }

    /** S-1：列表行（标签 + 内容）。 */
    function lastReplyListMarkup(summary) {
        return `<span class="mailbox-reply-list"><span>上次回复</span>${lastReplyContentMarkup(lastReplyDisplay(summary))}</span>`;
    }

    /** S-2：详情槽内部（标签 + 内容 + 北京时间尾注）；空/异常分支省略尾注。 */
    function lastReplyDetailInner(display) {
        const zone = display && display.kind === "time" ? `<span class="mailbox-reply-zone">${LAST_REPLY_ZONE_LABEL}</span>` : "";
        return `<span>专家上次回复</span>${lastReplyContentMarkup(display)}${zone}`;
    }

    /** S-1：按钮 aria-label 末尾追加同一纯文本说明（含北京时间）。 */
    function lastReplyAriaSuffix(display) {
        return `上次回复 ${display ? display.aria : LAST_REPLY_UNAVAILABLE_TEXT}`;
    }

    /**
     * fast-p 03（I-2）：会议草稿卡时间文本。只用 preview 的真实 UTC 值经统一的中文北京
     * formatter（宿主 formatBeijingMeetingRange）渲染；不回显 input.zoneId 的原 IANA 串，
     * 也不出现英文周/月。formatter 缺席或 UTC 值缺失时返回空串（不伪造时间）。
     */
    function meetingCardMetaTextFor(preview, formatRange) {
        if (!preview || !preview.startUtc || !preview.endUtc) return "";
        if (typeof formatRange !== "function") return "";
        const when = String(formatRange(preview.startUtc, preview.endUtc) || "");
        if (!when) return "";
        const duration = Number(preview.durationMinutes) || 0;
        return duration > 0 ? when + " · " + duration + " 分钟" : when;
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

    // ------------------------------------------------------------------
    // 跟进邮件（followup 01 · I-5/I-6）：自然短正文 + 与线程锚点同源的完整引用。
    // 引用纯文本转换是确定性的：先按块级/换行标签保留边界，再在惰性文档取 textContent，
    // 最后统一 CRLF、去行尾空白并把 3 个以上连续换行压为 2 个。所有输出再经 escapeText
    // 或 DOM textContent，绝不把邮件正文当 HTML 插入。
    // ------------------------------------------------------------------

    /** 与 MailSenderAccountService.SIMULATOR_ACCOUNT_CODE 同值：模拟器发件永不作为候选。 */
    const FOLLOWUP_SIMULATOR_ACCOUNT = "SIMULATOR_NOOP";
    const FOLLOWUP_HTMLISH = /<[a-z!/][^>]*>/i;
    const FOLLOWUP_ENTITY = /&(?:amp|lt|gt|quot|apos|nbsp|#\d+|#x[0-9a-fA-F]+);/g;

    const FOLLOWUP_BODY_LINES = {
        video: "Just following up on my email below about a brief Zoom call. Would you be available sometime this week or next? We’re happy to work around your time zone.",
        meetingReminder: "This is a courteous reminder of our scheduled meeting. We would be honored by your participation at the appointed time.",
        cv: "Just following up on my note below. When convenient, could you please send your CV? It will help us identify suitable industry partners.",
        generic: "I hope you’re doing well. I wanted to follow up on my previous email and would be happy to continue our conversation. Please feel free to share any thoughts or questions you may have. I look forward to hearing from you."
    };

    function quotePlainTextFromSource(raw) {
        const value = raw == null ? "" : String(raw);
        if (!value) return "";
        if (!FOLLOWUP_HTMLISH.test(value)) return finalizeQuotePlainText(value);
        const withBlockBreaks = value
            .replace(/<br\s*\/?>/gi, "\n")
            .replace(/<\/li\s*>/gi, "\n")
            .replace(/<\/(p|div|blockquote|h[1-6])\s*>/gi, "\n\n");
        return finalizeQuotePlainText(htmlFragmentToPlainText(withBlockBreaks));
    }

    /**
     * 惰性文档取纯文本（I-6）：优先 `DOMParser.parseFromString(...).body.textContent`；
     * 沙箱没有 DOMParser 时剥标签后解实体 —— 两者在同一输入上同结果（块级边界已在上一步
     * 转成换行，故 textContent 不会吞掉段落）。
     */
    function htmlFragmentToPlainText(html) {
        if (typeof global.DOMParser === "function") {
            try {
                const parsed = new global.DOMParser().parseFromString(html, "text/html");
                if (parsed && parsed.body) return String(parsed.body.textContent || "");
            } catch (e) { /* 退回文本剥离 */ }
        }
        return decodeQuoteEntities(html.replace(/<[^>]*>/g, ""));
    }

    function decodeQuoteEntities(value) {
        return String(value == null ? "" : value).replace(FOLLOWUP_ENTITY, (entity) => {
            const name = entity.slice(1, -1).toLowerCase();
            if (name === "amp") return "&";
            if (name === "lt") return "<";
            if (name === "gt") return ">";
            if (name === "quot") return "\"";
            if (name === "apos") return "'";
            if (name === "nbsp") return " ";
            if (name.charAt(0) === "#") {
                const code = name.charAt(1) === "x" ? parseInt(name.slice(2), 16) : parseInt(name.slice(1), 10);
                if (Number.isFinite(code) && code > 0) return String.fromCharCode(code);
            }
            return entity;
        });
    }

    function finalizeQuotePlainText(value) {
        return String(value == null ? "" : value)
            .replace(/\r\n?/g, "\n")
            .split("\n")
            .map((line) => line.replace(/[ \t]+$/, ""))
            .join("\n")
            .replace(/\n{3,}/g, "\n\n")
            .trim();
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
        if (chip === CHIP_PROVIDED) return { providedOnly: true };
        if (chip === CHIP_FOLLOWED) return { followed: true };
        if (chip === CHIP_REPLIED) return { repliedOnly: true };
        if (chip === CHIP_PENDING) return { pendingOnly: true };
        if (chip === CHIP_SUSPENDED) return { suspendedOnly: true };
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
            expertNote: { contactId: null, readSeq: 0, loaded: false, loading: false, error: "", data: null, saving: false, dialog: null },
            searchTimer: null,
            saveTimer: null,
            searchText: "",
            user: sessionUserFromOptions(options),
            filters: Object.assign({}, (options && options.filters) || {}),
            // 02（I-1）：普通新 mount 默认暂定「待处理」，由首次列表探测决定是否转「跟进中」；
            // 深链接 focus 优先，按其原语义（pendingOnly ? 待处理 : 全部）选出初始 Tab。
            chip: ((options && options.focus && options.focus.contactId != null)
                ? ((options && options.filters && options.filters.pendingOnly) ? CHIP_PENDING : CHIP_ALL)
                : CHIP_PENDING),
            chipUserTouched: false,
            // 02（I-1）：默认 Tab 探测（实例级，不写 localStorage）。focus 深链接不探测。
            defaultProbe: {
                active: !(options && options.focus && options.focus.contactId != null),
                decided: false
            },
            initialized: false,
            legacyFilterState: null,
            // 02（T1）：Tab 专家数计数（pending/suspended 各一个独立请求序号）。
            chipCounts: { pending: null, suspended: null, seq: 0 },
            // 02（T2/I-2）：当前选中专家的挂起真值（只来自 01 GET/PUT/DELETE 回包）。
            suspension: {
                contactId: null,
                seq: 0,
                workEpoch: 0,
                loading: false,
                error: "",
                loaded: false,
                suspended: false,
                suspendReason: null,
                suspensionPendingCount: 0,
                progressStatus: PROGRESS_NONE,
                followed: false
            },
            // 02（S-2/I-5）：卡片状态菜单（同一实例最多一个展开）与按 contactId 的在途写集合。
            progressMenu: { contactId: null, el: null, trigger: null, options: [], index: -1 },
            progressBusy: new Set(),
            // 02（S-3）：行内挂起原因表单（同一实例只保留一份）。
            reasonForm: { contactId: null, trigger: null, busy: false, editing: false },
            // 02（T3/I-7）：authenticated 身份只从 GET /api/auth/me 取（不读 localStorage 冒充）。
            auth: { ready: false, username: "", seq: 0, loading: false, failed: false },
            // 02（T3/I-7）：原位处理确认（同一实例只保留一条）与本次挂起周期的服务端 resolvedBy。
            processConfirm: { key: null, busy: false },
            resolvedLabels: new Map(),
            // 02（I-3）：完成提示行状态（anchorKey=归零的那条 PROCESSED 来信）。
            completion: { anchorKey: null, kept: false, busy: false, pendingAnchorKey: null },
            tagOptions: { loading: false, loaded: false, failed: false, items: [] },
            list: { page: 0, total: 0, items: [], loading: false, error: "" },
            selectedContactId: null,
            selectedSummary: null,
            /** 待匹配（邮件级）选择键；与 selectedContactId/sessionStore 完全独立（I-6）。 */
            selectedUnmatchedId: null,
            /** 当前承载 #unmatchedDetailPanel 的 .mc-scroll 宿主；null = 未持有 lease（I-5）。 */
            unmatchedDetailHost: null,
            mobilePane: "list",
            paneEpoch: 0,
            focusPaneEpoch: null,
            listScrollTop: 0,
            listTrigger: null,
            mobileMedia: null,
            pendingPosition: null,
            clearedEditorSnapshot: null,
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
                mode: "none", // "none" | "inbound" | "outbound" | "unavailable"
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
            meeting: {
                controller: null,
                editorRevision: 0,
                sending: false,
                lastBlobUrl: "",
                // fast-p 03：contactId → { state: "ok"|"error", activeCount, next }（只读缓存，非真值）
                summaryByContact: new Map(),
                summaryEpoch: -1
            },
            followup: { open: false, targetKey: null, selectedId: null, selectedCopy: null, trigger: null },
            materialRequest: { open: false, identity: null, items: [], seq: 0, trigger: null },
            // fast-p 01（I-6）：引用模板弹框的单次临时状态；不是持久 store，不加 draft 字段。
            templateReference: {
                open: false,
                seq: 0,
                identity: null,
                items: [],
                selectedId: null,
                preview: null,
                loading: "",
                error: "",
                trigger: null
            },
            // fast-p 2026-10-02 c3（I-1..I-6）：所在地/推荐时间的单次临时状态；
            // 不是持久 store，不写草稿 Map/localStorage/sessionStorage。
            // data 只属于 contactId 对应的 timing 响应；dialog 为当前打开的弹窗（多则一）。
            contactTiming: {
                contactId: null,
                seq: 0,
                data: null,
                loading: false,
                error: "",
                dialog: null,
                dialogSeq: 0,
                prefix: "",
                catalog: null
            },
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
            const chipButtons = FILTER_CHIPS.map((chip) => {
                const countSpan = CHIP_COUNT_KEYS.indexOf(chip.key) >= 0
                    ? `<span class="mailbox-suspend-count" data-chip-count="${chip.key}" hidden></span>`
                    : "";
                return `<button class="mc-filter" type="button" data-action="mc-filter" data-chip="${chip.key}" aria-pressed="${instance.chip === chip.key ? "true" : "false"}">${escapeText(chip.label)}${countSpan}</button>`;
            }).join("");
            return `
                <div class="mail-chat mobile-core-mailbox" data-mobile-pane="list">
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
                    <button type="button" class="button mobile-mailbox-back" data-action="mobile-mailbox-back">返回会话列表</button>
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

        function conversationVisible() {
            if (instance.mobileMedia && instance.mobileMedia.matches && instance.mobilePane === "list") return false;
            const scroll = scrollEl();
            return !scroll || typeof scroll.getClientRects !== "function" || scroll.getClientRects().length > 0;
        }

        function saveCurrentConversation() {
            const cleared = instance.clearedEditorSnapshot;
            const values = readManualValues();
            const key = currentTargetKey();
            // 已发送且已删的草稿仍可能留在 DOM；生命周期采集不能复活它。
            const unchangedSent = cleared && cleared.key === key && cleared.draftsMap === currentDraftsMap()
                && !getDraft(key) && values && values.subject === cleared.snapshot.subject
                && values.html === cleared.snapshot.html && values.text === cleared.snapshot.text;
            if (!unchangedSent) saveDraftFromInputs();
            saveConversationState();
        }

        function afterPaneFrame(callback) {
            const paneEpoch = instance.paneEpoch;
            const convEpoch = instance.convEpoch;
            const run = () => {
                if (!instance.disposed && paneEpoch === instance.paneEpoch && convEpoch === instance.convEpoch) callback();
            };
            if (typeof global.requestAnimationFrame === "function") global.requestAnimationFrame(run);
            else run();
        }

        function restoreVisiblePosition() {
            if (!conversationVisible()) return;
            const record = instance.pendingPosition || getConversationRecord(instance.user,
                instance.conversation.accountScope || "", Number(instance.selectedContactId || 0));
            if (record) restoreFromRecord(record);
            instance.pendingPosition = null;
        }

        function setMobilePane(pane, trigger) {
            if (pane === "detail" && instance.mobilePane === "list") {
                const list = expertsRoot();
                instance.listScrollTop = list ? Number(list.scrollTop) || 0 : 0;
                instance.pendingPosition = instance.pendingPosition || getConversationRecord(instance.user,
                    instance.conversation.accountScope || "", Number(instance.selectedContactId || 0));
            }
            if (trigger) instance.listTrigger = trigger;
            instance.mobilePane = pane;
            instance.paneEpoch += 1;
            const root = host.querySelector ? host.querySelector(".mobile-core-mailbox") : null;
            if (root) root.setAttribute("data-mobile-pane", pane);
            afterPaneFrame(() => {
                if (pane === "detail") restoreVisiblePosition();
                else if (instance.mobileMedia && instance.mobileMedia.matches) {
                    const list = expertsRoot();
                    if (list) list.scrollTop = instance.listScrollTop;
                    const action = isUnmatchedChip() ? "mc-select-unmatched" : "mc-select-expert";
                    const idKey = isUnmatchedChip() ? "unmatched-id" : "contact-id";
                    const id = isUnmatchedChip() ? instance.selectedUnmatchedId : instance.selectedContactId;
                    const current = host.querySelector(`[data-action="${action}"][data-${idKey}="${id}"]`);
                    const trigger = current || (host.contains && host.contains(instance.listTrigger) ? instance.listTrigger : null);
                    if (trigger && typeof trigger.focus === "function") trigger.focus({ preventScroll: true });
                }
            });
        }

        function returnToMobileList() {
            saveCurrentConversation();
            setMobilePane("list");
            // R-1（V-1）：离开详情即作废在途挂起 UI 工作并撤下未提交的原因表单。
            invalidateSuspensionWork();
        }

        function onMobileViewportChange() {
            // CSS owns layout; resizing never rebuilds an editor or issues a request.
            instance.paneEpoch += 1;
            afterPaneFrame(restoreVisiblePosition);
        }

        function bindMobileViewport() {
            if (typeof global.matchMedia !== "function") return;
            instance.mobileMedia = global.matchMedia("(max-width: 760px)");
            if (typeof instance.mobileMedia.addEventListener === "function") instance.mobileMedia.addEventListener("change", onMobileViewportChange);
            else if (typeof instance.mobileMedia.addListener === "function") instance.mobileMedia.addListener(onMobileViewportChange);
        }

        function saveBeforeScopeChange(prev, next) {
            if (String(prev || "") !== String(next || "")) saveCurrentConversation();
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
            const prevAccount = String(instance.filters.accountCode || "");
            saveBeforeScopeChange(prevAccount, next.accountCode);
            instance.filters = next;
            meetingCloseDisposeOnAccountScopeChange(prevAccount, String(next.accountCode || ""));
            syncTagOptionsWithCommitted();
            setPopoverOpen(false);
            renderFilterChrome();
            freezeDefaultProbe();
            instance.list.page = 0;
            loadList();
        }

        function resetAdvancedFilters() {
            const prevAccount = String(instance.filters.accountCode || "");
            saveBeforeScopeChange(prevAccount, "");
            instance.filters = {};
            meetingCloseDisposeOnAccountScopeChange(prevAccount, "");
            populateFieldsFromCommitted();
            clearFilterError();
            setPopoverOpen(false);
            renderFilterChrome();
            freezeDefaultProbe();
            instance.list.page = 0;
            loadList();
        }

        function restoreAdvancedFilters() {
            const prevAccount = String(instance.filters.accountCode || "");
            saveBeforeScopeChange(prevAccount, "");
            instance.filters = {};
            meetingCloseDisposeOnAccountScopeChange(prevAccount, "");
            populateFieldsFromCommitted();
            renderFilterChrome();
            freezeDefaultProbe();
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

        function conversationsParams(page, chipOverride, sizeOverride) {
            const params = new URLSearchParams();
            params.set("page", String(page));
            params.set("size", String(sizeOverride != null ? sizeOverride : PAGE_SIZE));
            const q = instance.searchText.trim();
            if (q) params.set("q", q);
            const chipValues = chipParams(chipOverride != null ? chipOverride : instance.chip);
            if (chipValues.providedOnly) params.set("providedOnly", "true");
            if (chipValues.followed) params.set("followed", "true");
            if (chipValues.repliedOnly) params.set("repliedOnly", "true");
            if (chipValues.pendingOnly) params.set("pendingOnly", "true");
            if (chipValues.suspendedOnly) params.set("suspendedOnly", "true");
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

        // 待匹配（邮件级）请求：不带任何专家会话高级筛选（I-3/I-8）。
        function unmatchedParams(page) {
            const params = new URLSearchParams();
            params.set("unmatchedOnly", "true");
            params.set("pageSize", String(PAGE_SIZE));
            params.set("pageOffset", String(page * PAGE_SIZE));
            const q = instance.searchText.trim();
            if (q) params.set("query", q);
            return params;
        }

        function isUnmatchedChip() {
            return instance.chip === CHIP_UNMATCHED;
        }

        function personTagNames(item) {
            const tags = item && Array.isArray(item.expertTags) ? item.expertTags : null;
            if (!tags) return null;
            return tags.map((tag) => expertTagLabel(tag));
        }

        // 02（S-2/I-2/I-6）：卡片挂起 footer。N 一律用跨账号 suspensionPendingCount；
        // 未挂起且 N=0 不渲染；已挂起即使 N>0 也不得 data-pending=true。
        function suspensionCardValues(item) {
            const suspended = item && item.suspended === true;
            const count = Number(item && item.suspensionPendingCount) || 0;
            return { suspended, count, show: suspended || count > 0 };
        }

        function suspensionStateText(suspended, count) {
            return suspended ? `已挂起 · ${count} 条待处理` : `${count} 条待处理`;
        }

        function suspensionCardFooterHtml(item) {
            const values = suspensionCardValues(item);
            if (!values.show) return "";
            const pendingFlag = (!values.suspended && values.count > 0) ? "true" : "false";
            const label = values.suspended ? "取消挂起" : "挂起";
            const disabled = instance.auth.ready ? "" : " disabled";
            return `
                    <div class="mailbox-suspend-card-footer">
                        <span class="mailbox-suspend-state" data-pending="${pendingFlag}">${escapeText(suspensionStateText(values.suspended, values.count))}</span>
                        <button type="button" class="mailbox-suspend-card-action" data-action="mc-suspension" data-contact-id="${escapeText(item.contactId)}"${disabled}>${label}</button>
                    </div>`;
        }

        // 02（S-2/I-1）：卡片右上角三态状态控件（标签/菜单按 PROGRESS_MENU 唯一表）。
        // 非法状态渲染为禁用「状态不可用」；在途写按 contactId 继承 busy。
        function progressActionsHtml(item) {
            const contactKey = String(item.contactId);
            const name = item.name || item.email || "该专家";
            const status = progressStatusOf(item);
            const def = progressMenuDef(status);
            const busy = instance.progressBusy.has(contactKey);
            const disabled = !def || busy;
            const label = def ? def.label : PROGRESS_INVALID_LABEL;
            const aria = def ? `${name}：${label}，选择状态` : `${name}：${PROGRESS_INVALID_LABEL}`;
            const menuHtml = def ? `
                            <span class="mailbox-progress-menu" role="menu" aria-label="${escapeText(`${name}的状态操作`)}" hidden>
                                ${def.options.map(([next, optionLabel]) => `<button class="mailbox-progress-option" type="button" role="menuitem" data-action="mc-set-progress" data-contact-id="${escapeText(item.contactId)}" data-progress="${next}"${busy ? " disabled" : ""}>${escapeText(optionLabel)}</button>`).join("")}
                            </span>` : "";
            return `
                        <span class="mailbox-progress">
                            <button class="mailbox-progress-status" type="button" data-action="mc-progress-menu" data-contact-id="${escapeText(item.contactId)}" data-progress="${def ? escapeText(status) : ""}" aria-haspopup="menu" aria-expanded="false" aria-label="${escapeText(aria)}"${disabled ? " disabled" : ""}>${escapeText(label)}</button>${menuHtml}
                        </span>`;
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
            const cardName = item.name || item.email || "-";
            const active = instance.selectedContactId != null
                && String(item.contactId) === String(instance.selectedContactId);
            const tagNames = personTagNames(item);
            const tagLine = tagNames === null
                ? `<span class="mc-person-tags-unavailable" title="标签暂不可用">标签暂不可用</span>`
                : (tagNames.length === 0 ? "" : `<span class="mc-person-tags" title="专家标签：${escapeText(tagNames.join("、"))}">${tagNames.map((name) => `<span class="mc-person-tag">${escapeText(name)}</span>`).join("")}</span>`);
            const ariaLabel = (tagNames !== null && tagNames.length > 0
                ? `查看${item.name || item.email || ""}往来邮件；专家标签：${tagNames.join("、")}`
                : `查看${item.name || item.email || ""}往来邮件`)
                + `；${lastReplyAriaSuffix(lastReplyDisplay(item))}`;
            return `
                <div class="mc-person mailbox-progress-card" data-replied="${instance.chip === CHIP_REPLIED ? "true" : "false"}" data-active="${active ? "true" : "false"}" data-contact-id="${escapeText(item.contactId)}">
                    <button class="mc-person-main" type="button" data-action="mc-select-expert" data-contact-id="${escapeText(item.contactId)}" aria-label="${escapeText(ariaLabel)}"${active ? ' aria-current="true"' : ""}>
                        <span class="mc-person-heading"><strong title="${escapeText(cardName)}">${escapeText(cardName)}</strong></span>
                        <small>${escapeText(accounts)}</small>
                        <small>${escapeText(latestLine)}</small>
                        ${lastReplyListMarkup(item)}
                        <span class="mc-person-meta">
                            <span class="mc-person-counts">收 ${Number(item.receivedCount) || 0} · 发 ${Number(item.sentCount) || 0}</span>
                            ${tagLine}
                        </span>
                        <span class="calendar-summary" data-role="meeting-summary"></span>
                    </button>
                    <span data-role="person-actions">
                        ${progressActionsHtml(item)}
                        ${instance.chip === CHIP_REPLIED ? `<button class="mc-text-button" type="button" data-action="mc-dismiss-replied" data-contact-id="${escapeText(item.contactId)}" aria-label="将${escapeText(item.name || item.email || "该专家")}移出已回复" title="移出已回复，仍可在全部查看">移出</button>` : ""}
                    </span>${suspensionCardFooterHtml(item)}
                </div>
            `;
        }

        function renderExpertList() {
            closeProgressMenu({ restoreFocus: false });
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
            root.querySelectorAll(".mc-person").forEach(applyMeetingSummaryToCard);
        }

        // S-2：待匹配邮件卡片（邮件级，无状态菜单/专家标签/收发计数）。
        function renderUnmatchedPerson(item) {
            const id = Number(item.id);
            const active = instance.selectedUnmatchedId != null
                && String(id) === String(instance.selectedUnmatchedId);
            return `
                <div class="mc-person" data-active="${active ? "true" : "false"}" data-unmatched-id="${escapeText(id)}">
                    <button class="mc-person-main" type="button" data-action="mc-select-unmatched" data-unmatched-id="${escapeText(id)}"${active ? ' aria-current="true"' : ""}>
                        <span class="mc-person-heading"><strong>${escapeText(item.subject || "（无主题）")}</strong></span>
                        <small>${escapeText(item.fromEmail || "-")}</small>
                        <small>${escapeText(item.receivedAt || "-")} · 账号：${escapeText(item.senderAccountCode || "-")}</small>
                        <span class="mc-person-meta"><span class="mc-badge" data-tone="pending">未关联专家</span></span>
                    </button>
                </div>
            `;
        }

        function renderUnmatchedList() {
            const root = expertsRoot();
            if (!root) return;
            if (instance.list.error) {
                root.innerHTML = `<div class="mc-error" role="alert">待匹配来信加载失败，请重试。<button class="button" type="button" data-action="mc-retry-list">重试</button></div>`;
                return;
            }
            const items = instance.list.items || [];
            if (items.length === 0) {
                root.innerHTML = `<div class="mc-empty">暂无待匹配来信</div>`;
                return;
            }
            root.innerHTML = items.map(renderUnmatchedPerson).join("");
        }

        function renderList() {
            if (isUnmatchedChip()) renderUnmatchedList();
            else renderExpertList();
        }

        // 02（T1/S-1）：Tab 计数=同一筛选范围的专家数（GET total，page=0&size=1）。
        // 复用当前列表结果；另一 Tab 只作一次请求；失败隐藏数字，绝不显示假 0。
        function setChipCount(key, total) {
            instance.chipCounts[key] = total == null ? null : (Number(total) || 0);
            const span = host.querySelector
                ? host.querySelector(`.mailbox-suspend-count[data-chip-count="${key}"]`)
                : null;
            if (!span) return;
            if (total == null) {
                span.textContent = "";
                span.hidden = true;
            } else {
                span.textContent = String(Number(total) || 0);
                span.hidden = false;
            }
        }

        // 02（I-1/I-5）：用户点击 Tab/改动筛选即冻结默认决策，晚到的首查探测不得覆盖。
        function freezeDefaultProbe() {
            instance.defaultProbe.active = false;
            instance.defaultProbe.decided = true;
        }

        function loadChipCounts() {
            if (instance.disposed) return;
            instance.chipCounts.seq += 1;
            const mySeq = instance.chipCounts.seq;
            CHIP_COUNT_KEYS.forEach((key) => {
                if (instance.chip === key && !instance.list.error) {
                    setChipCount(key, Number(instance.list.total) || 0);
                    return;
                }
                const params = conversationsParams(0, key, 1);
                hostApi()(`/api/mail/mailbox/conversations?${params.toString()}`).then((data) => {
                    if (instance.disposed || mySeq !== instance.chipCounts.seq) return;
                    setChipCount(key, data && data.total != null ? Number(data.total) : 0);
                }).catch(() => {
                    if (instance.disposed || mySeq !== instance.chipCounts.seq) return;
                    setChipCount(key, null);
                });
            });
        }

        function renderPager() {
            const root = pagerRoot();
            if (!root) return;
            const total = Number(instance.list.total) || 0;
            const maxPage = Math.max(0, Math.ceil(total / PAGE_SIZE) - 1);
            const page = instance.list.page;
            const unit = isUnmatchedChip() ? "封" : "位";
            root.innerHTML = `
                <span>第 ${page + 1}/${maxPage + 1} 页 · 共 ${total} ${unit}</span>
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
            // 请求模式在发出时固化；晚到的另一模式响应由 listSeq 拒绝（I-3）。
            const unmatched = isUnmatchedChip();
            const url = unmatched
                ? `/api/mail/unmatched-inbound?${unmatchedParams(page).toString()}`
                : `/api/mail/mailbox/conversations?${conversationsParams(page).toString()}`;
            return hostApi()(url).then((data) => {
                if (instance.disposed || mySeq !== instance.listSeq) return null;
                instance.list.items = unmatched
                    ? ((data && Array.isArray(data.records)) ? data.records : [])
                    : ((data && Array.isArray(data.items)) ? data.items : []);
                instance.list.total = unmatched
                    ? (Number(data && data.totalCount) || 0)
                    : (Number(data && data.total) || 0);
                instance.list.page = page;
                instance.list.loading = false;
                renderList();
                renderPager();
                // 02（I-1）：首次 mount 默认 Tab 探测。仅普通（非 focus）新实例、用户尚未
                // 点击 Tab/筛选、且本次就是暂定的待处理首查时生效；total=0 才切跟进中。
                instance.initialized = true;
                if (instance.defaultProbe.active && !instance.defaultProbe.decided
                    && !unmatched && instance.chip === CHIP_PENDING) {
                    instance.defaultProbe.decided = true;
                    instance.defaultProbe.active = false;
                    if ((Number(instance.list.total) || 0) === 0) {
                        instance.chip = CHIP_FOLLOWED;
                        syncChipButtons();
                        instance.list.page = 0;
                        loadChipCounts();
                        return fetchList({ page: 0 });
                    }
                }
                if (unmatched) resolveUnmatchedSelection();
                else {
                    // I-4：列表与已选专家详情取同一份当前行，只替换详情回复时间槽。
                    const selectedId = instance.selectedContactId;
                    if (selectedId != null) {
                        const selectedRow = findSummaryByContactId(selectedId);
                        if (selectedRow) renderLastReplyHeader(selectedRow);
                    }
                    // fast-p 03（I-3）：批量摘要按当前页专家 id，epoch = 本次列表请求
                    loadMeetingSummaries();
                }
                loadChipCounts();
                return data;
            }).catch((err) => {
                if (instance.disposed || mySeq !== instance.listSeq) return null;
                instance.list.loading = false;
                instance.list.error = err && err.message ? err.message : "加载失败";
                renderList();
                renderPager();
                hostShowStatus(
                    unmatched
                        ? `获取待匹配来信失败: ${instance.list.error}`
                        : `获取邮件记录失败: ${instance.list.error}`,
                    "error"
                );
                if (unmatched) {
                    clearUnmatchedState();
                    renderUnmatchedEmpty();
                }
                return null;
            });
        }

        function loadList() {
            return fetchList({ page: instance.list.page }).then((data) => {
                if (instance.disposed) return data;
                if (isUnmatchedChip()) resolveUnmatchedSelection();
                else resolveFocusAndSelection();
                return data;
            });
        }

        // --------------------------------------------------------------
        // 02（T2/T3/I-2/I-3/I-4/I-7）：挂起状态、行内原因、取消/结束、完成提示。
        // 真值与原因只来自 01 GET/PUT/DELETE；状态变更后一律重查，绝不本地自减推断。
        // --------------------------------------------------------------

        const SUSPENSION_LOAD_ERROR_TEXT = "挂起状态读取失败，请重试";
        const AUTH_LOAD_ERROR_TEXT = "登录状态读取失败，请刷新重试";

        // 相对引用节点插入 HTML（afterbegin/beforeend 之外的相对位置，兼容真实 DOM 与测试桩）。
        function insertHtmlRelative(referenceEl, html, position) {
            if (!referenceEl || !referenceEl.parentNode) return null;
            const doc = docRoot();
            if (!doc || typeof doc.createElement !== "function") return null;
            const parent = referenceEl.parentNode;
            const temp = doc.createElement("div");
            temp.innerHTML = html;
            const nodes = Array.prototype.slice.call(temp.childNodes || []);
            const list = parent.childNodes || [];
            const idx = Array.prototype.indexOf.call(list, referenceEl);
            const ref = idx >= 0 ? (list[idx + 1] || null) : null;
            nodes.forEach((node) => {
                if (position === "before") parent.insertBefore(node, referenceEl);
                else parent.insertBefore(node, ref);
            });
            return nodes[0] || null;
        }

        function resetSuspensionState() {
            instance.suspension = {
                contactId: null,
                seq: instance.suspension.seq + 1,
                workEpoch: instance.suspension.workEpoch + 1,
                loading: false,
                error: "",
                loaded: false,
                suspended: false,
                suspendReason: null,
                suspensionPendingCount: 0,
                progressStatus: PROGRESS_NONE,
                followed: false
            };
            instance.completion = {
                anchorKey: null, kept: false, busy: false,
                pendingAnchorKey: null, renderedAnchor: null, renderedKept: false
            };
            instance.processConfirm = { key: null, busy: false };
            instance.resolvedLabels = new Map();
            detachReasonForm();
        }

        function suspensionMatchesContact(contactId) {
            return instance.suspension.contactId != null
                && String(instance.suspension.contactId) === String(contactId);
        }

        // 详情视图的挂起真值：优先权威 GET，其次列表行摘要（只在匹配 contactId 时）。
        function suspensionViewForContact(contactId, summary) {
            if (suspensionMatchesContact(contactId) && instance.suspension.loaded) {
                return {
                    suspended: instance.suspension.suspended,
                    suspendReason: instance.suspension.suspendReason,
                    count: instance.suspension.suspensionPendingCount,
                    progressStatus: instance.suspension.progressStatus
                };
            }
            const row = summary || findSummaryByContactId(contactId) || {};
            return {
                suspended: row.suspended === true,
                suspendReason: row.suspendReason == null ? null : String(row.suspendReason),
                count: Number(row.suspensionPendingCount) || 0,
                progressStatus: progressStatusOf(row)
            };
        }

        // R-1（V-1）：挂起异步回包只在发起时的详情上下文仍当前时生效。
        // 移动端返回列表、换专家/Tab/账号、卸载都会让旧回包成为 no-op。
        function captureSuspensionContext(contactId) {
            return {
                contactId: Number(contactId),
                user: instance.user,
                convEpoch: instance.convEpoch,
                paneEpoch: instance.paneEpoch,
                workEpoch: instance.suspension.workEpoch
            };
        }

        function suspensionContextAlive(ctx) {
            if (!ctx || instance.disposed) return false;
            if (ctx.workEpoch !== instance.suspension.workEpoch) return false;
            if (ctx.convEpoch !== instance.convEpoch) return false;
            if (ctx.paneEpoch !== instance.paneEpoch) return false;
            return String(ctx.user) === String(instance.user);
        }

        // 离开详情/切 Tab/销毁：作废在途挂起工作并撤下未提交的原因表单（不清挂起真值）。
        function invalidateSuspensionWork() {
            instance.suspension.workEpoch += 1;
            instance.suspension.seq += 1;
            instance.suspension.loading = false;
            instance.completion.busy = false;
            detachReasonForm();
        }

        function loadSuspensionState(contactId) {
            const id = Number(contactId);
            if (!Number.isFinite(id) || id <= 0) return;
            if (isUnmatchedChip()) return;
            instance.suspension.seq += 1;
            const mySeq = instance.suspension.seq;
            const ctx = captureSuspensionContext(id);
            instance.suspension.contactId = id;
            instance.suspension.loading = true;
            instance.suspension.error = "";
            return hostApi()(`/api/mail/mailbox/conversations/${id}/suspension`).then((data) => {
                if (mySeq !== instance.suspension.seq || !suspensionContextAlive(ctx)) return;
                applySuspensionState(id, data);
            }).catch((err) => {
                if (mySeq !== instance.suspension.seq || !suspensionContextAlive(ctx)) return;
                instance.suspension.loading = false;
                instance.suspension.loaded = false;
                instance.suspension.error = err && err.message ? err.message : SUSPENSION_LOAD_ERROR_TEXT;
                renderSuspensionDetail();
            });
        }

        function applySuspensionState(contactId, data) {
            const state = (data && typeof data === "object") ? data : {};
            instance.suspension.contactId = Number(contactId);
            instance.suspension.loading = false;
            instance.suspension.loaded = true;
            instance.suspension.error = "";
            instance.suspension.suspended = state.suspended === true;
            instance.suspension.suspendReason = state.suspendReason == null ? null : String(state.suspendReason);
            instance.suspension.suspensionPendingCount = Number(state.suspensionPendingCount) || 0;
            // 02（T-3）：真值取 progressStatus；响应缺字段才从 followed 兼容派生。
            instance.suspension.progressStatus = progressStatusOf(state);
            instance.suspension.followed = progressFollowed(instance.suspension.progressStatus);
            recomputeCompletionLine();
            renderSuspensionDetail();
        }

        function lastProcessedInboundKey() {
            const items = instance.conversation.items || [];
            for (let i = items.length - 1; i >= 0; i -= 1) {
                const message = items[i];
                if (message.source === "INBOUND_PROCESSING" && message.processStatus === "PROCESSED") {
                    return `${message.source}:${message.id}`;
                }
            }
            return null;
        }

        // I-3：suspended 且跨账号计数=0 才允许提示；锚点仍须是已加载且 PROCESSED 的来信。
        function recomputeCompletionLine() {
            const state = instance.suspension;
            if (!state.suspended || state.suspensionPendingCount > 0) {
                instance.completion.anchorKey = null;
                instance.completion.kept = false;
                instance.completion.pendingAnchorKey = null;
                return;
            }
            let anchor = null;
            const pendingAnchor = instance.completion.pendingAnchorKey;
            const pendingMessage = pendingAnchor ? messageByKey(pendingAnchor) : null;
            if (pendingMessage && pendingMessage.processStatus === "PROCESSED") {
                anchor = pendingAnchor;
            } else {
                anchor = lastProcessedInboundKey();
            }
            instance.completion.pendingAnchorKey = null;
            if (!anchor) {
                instance.completion.anchorKey = null;
                instance.completion.kept = false;
                return;
            }
            if (instance.completion.anchorKey !== anchor) {
                instance.completion.anchorKey = anchor;
                instance.completion.kept = false;
            }
        }

        function suspensionDetailButtonHtml(contactId, view) {
            if (!view.suspended && !(view.count > 0)) return "";
            const label = view.suspended ? "取消挂起" : "挂起";
            const disabled = instance.auth.ready ? "" : " disabled";
            return `<button type="button" class="button mailbox-suspend-action" data-action="mc-suspension" data-contact-id="${escapeText(contactId)}"${disabled}>${label}</button>`;
        }

        function suspensionBannerHtml(view) {
            if (!view.suspended) return "";
            const title = view.count > 0
                ? `此会话已挂起 · ${view.count} 条待处理`
                : "消息已全部处理 · 等待结束挂起";
            const reason = view.suspendReason ? `<small>${escapeText(`挂起原因：${view.suspendReason}`)}</small>` : "";
            const disabled = !instance.auth.ready || instance.reasonForm.busy ? " disabled" : "";
            return `
                <div class="mailbox-suspend-banner" role="status">
                    <span class="mailbox-suspend-symbol" aria-hidden="true">Ⅱ</span>
                    <div class="mailbox-suspend-banner-content">
                        <strong>${escapeText(title)}</strong>
                        ${reason}
                    </div>
                    <div data-role="suspension-reason-actions">
                        <button type="button" class="mailbox-suspend-card-action" data-action="mc-suspension-edit-reason" data-contact-id="${escapeText(instance.selectedContactId)}"${disabled}>${view.suspendReason ? "编辑原因" : "添加原因"}</button>
                    </div>
                </div>`;
        }

        function renderSuspensionBanner() {
            const body = conversationBody();
            if (!body) return;
            const html = suspensionBannerHtml(suspensionViewForContact(instance.selectedContactId));
            const existing = body.querySelector(".mailbox-suspend-banner");
            if (existing) {
                if (html) existing.outerHTML = html;
                else if (existing.parentNode) existing.parentNode.removeChild(existing);
                return;
            }
            if (!html) return;
            const head = body.querySelector(".mc-timeline-head");
            if (head && head.parentNode) insertHtmlRelative(head, html, "before");
        }

        // 只更新挂起按钮槽/banner/完成行，不重建整个详情（草稿/滚动/材料/排期不动）。
        function renderSuspensionDetail() {
            if (isUnmatchedChip()) return;
            if (instance.selectedContactId == null) return;
            if (!suspensionMatchesContact(instance.selectedContactId)) return;
            const body = conversationBody();
            if (!body) return;
            recomputeCompletionLine();
            const contactId = Number(instance.selectedContactId);
            const view = suspensionViewForContact(contactId);
            const actions = body.querySelector(".mc-actions");
            if (actions) {
                const existing = actions.querySelector('[data-action="mc-suspension"]');
                if (existing && existing.parentNode) existing.parentNode.removeChild(existing);
                const html = suspensionDetailButtonHtml(contactId, view);
                if (html) actions.insertAdjacentHTML("afterbegin", html);
            }
            renderSuspensionBanner();
            syncCompletionLine();
        }

        // 完成行重绘按 anchor/kept 变化触发；renderMessage 读取 completion 状态。
        function syncCompletionLine() {
            const want = instance.completion.anchorKey != null;
            const rendered = instance.completion.renderedAnchor || null;
            const changed = (want !== !!rendered)
                || (want && rendered !== instance.completion.anchorKey)
                || (instance.completion.renderedKept !== instance.completion.kept);
            if (!changed) return;
            renderTimeline();
            instance.completion.renderedAnchor = want ? instance.completion.anchorKey : null;
            instance.completion.renderedKept = instance.completion.kept;
        }

        // 身份就绪前禁用新挂起/处理按钮；读取失败的提示只在用户尝试挂起/处理时给出，
        // 避免在未提供 /api/auth/me 的宿主上于 mount 阶段弹出全局错误。
        function loadAuthenticatedUser() {
            instance.auth.seq += 1;
            const mySeq = instance.auth.seq;
            instance.auth.loading = true;
            hostApi()("/api/auth/me").then((data) => {
                if (instance.disposed || mySeq !== instance.auth.seq) return;
                instance.auth.loading = false;
                if (data && data.authenticated === true && data.username) {
                    instance.auth.ready = true;
                    instance.auth.username = String(data.username);
                    instance.auth.failed = false;
                    renderList();
                    renderSuspensionDetail();
                    renderTimeline();
                } else {
                    instance.auth.ready = false;
                    instance.auth.failed = true;
                }
            }).catch(() => {
                if (instance.disposed || mySeq !== instance.auth.seq) return;
                instance.auth.loading = false;
                instance.auth.failed = true;
            });
        }

        // ---- 行内挂起原因（S-3；禁止 dialog/alert/confirm/prompt） ----

        function reasonFormSection(contactId) {
            const section = host.querySelector ? host.querySelector(".mailbox-suspend-inline-reason") : null;
            if (!section) return null;
            if (contactId != null && String(section.dataset ? section.dataset.contactId : "") !== String(contactId)) return null;
            return section;
        }

        function detachReasonForm() {
            const section = host.querySelector ? host.querySelector(".mailbox-suspend-inline-reason") : null;
            if (section && section.parentNode) section.parentNode.removeChild(section);
            instance.reasonForm.contactId = null;
            instance.reasonForm.busy = false;
            const trigger = instance.reasonForm.trigger;
            instance.reasonForm.trigger = null;
            return trigger;
        }

        function reasonFormHtml(contactId, editing, reason) {
            return `
                <section class="mailbox-suspend-inline-reason" aria-label="填写挂起原因" data-contact-id="${escapeText(contactId)}">
                    <div class="mailbox-suspend-inline-reason-head"><strong>${editing ? "编辑挂起原因" : "挂起此会话"}</strong><span>原因选填</span></div>
                    <textarea aria-label="挂起原因（选填）" maxlength="500" rows="2" placeholder="例如：等待专家补充材料，稍后跟进">${escapeText(reason || "")}</textarea>
                    <div class="mailbox-suspend-inline-reason-bottom">
                        <small>仅内部可见 · <span class="mailbox-suspend-reason-count">0 / 500</span></small>
                        <div>
                            <button type="button" class="mailbox-suspend-inline-secondary" data-action="mc-suspension-reason-cancel">取消</button>
                            <button type="button" class="mailbox-suspend-inline-primary" data-action="mc-suspension-reason-confirm" data-contact-id="${escapeText(contactId)}">${editing ? "保存" : "确认挂起"}</button>
                        </div>
                    </div>
                    <div class="mailbox-suspend-error" role="alert" hidden></div>
                </section>`;
        }

        function openReasonForm(contactId, trigger, editing = false) {
            const id = Number(contactId);
            if (!Number.isFinite(id) || id <= 0) return;
            detachReasonForm();
            const body = conversationBody();
            let anchor = null;
            if (trigger && typeof trigger.closest === "function") {
                anchor = trigger.closest(".mc-person") || trigger.closest(".mc-header");
            }
            if (!anchor && body) anchor = body.querySelector(".mc-header");
            if (!anchor || !anchor.parentNode) {
                hostShowStatus("挂起操作不可用，请刷新后重试", "error");
                return;
            }
            const reason = editing ? suspensionViewForContact(id).suspendReason || "" : "";
            insertHtmlRelative(anchor, reasonFormHtml(id, editing, reason), "after");
            instance.reasonForm.editing = editing;
            instance.reasonForm.contactId = id;
            instance.reasonForm.trigger = trigger || null;
            instance.reasonForm.busy = false;
            const section = reasonFormSection(id);
            const textarea = section ? section.querySelector("textarea") : null;
            if (textarea) textarea.value = reason;
            updateReasonCount(section, reason);
            if (textarea && typeof textarea.focus === "function") textarea.focus();
        }

        function setReasonFormBusy(section, busy) {
            instance.reasonForm.busy = !!busy;
            if (!section) return;
            const textarea = section.querySelector("textarea");
            if (textarea) textarea.disabled = !!busy;
            const cancel = section.querySelector('[data-action="mc-suspension-reason-cancel"]');
            const confirm = section.querySelector('[data-action="mc-suspension-reason-confirm"]');
            if (cancel) cancel.disabled = !!busy;
            if (confirm) {
                confirm.disabled = !!busy;
                confirm.textContent = instance.reasonForm.editing ? (busy ? "正在保存…" : "保存") : (busy ? "正在挂起…" : "确认挂起");
            }
        }

        function setReasonFormError(section, message) {
            if (!section) return;
            const error = section.querySelector(".mailbox-suspend-error");
            if (!error) return;
            error.textContent = message || "";
            error.hidden = !message;
        }

        function updateReasonCount(section, value) {
            if (!section) return;
            const counter = section.querySelector(".mailbox-suspend-reason-count");
            if (!counter) return;
            const text = value == null ? "" : String(value);
            counter.textContent = `${text.length} / 500`;
        }

        function cancelReasonForm() {
            const trigger = detachReasonForm();
            if (trigger && trigger.isConnected !== false && typeof trigger.focus === "function") {
                trigger.focus();
                return;
            }
            const chip = host.querySelector ? host.querySelector(`.mc-filter[data-chip="${instance.chip}"]`) : null;
            if (chip && typeof chip.focus === "function") chip.focus();
        }

        function confirmSuspensionReason(contactId) {
            const id = Number(contactId);
            const section = reasonFormSection(id);
            if (!section || instance.reasonForm.busy) return;
            const textarea = section.querySelector("textarea");
            const reasonText = textarea && typeof textarea.value === "string" ? textarea.value.trim() : "";
            const ctx = captureSuspensionContext(id);
            setReasonFormBusy(section, true);
            setReasonFormError(section, "");
            const editing = instance.reasonForm.editing;
            hostApi()(`/api/mail/mailbox/conversations/${id}/suspension${editing ? "/reason" : ""}`, {
                method: editing ? "PATCH" : "PUT",
                body: JSON.stringify({ reason: reasonText || null })
            }).then((data) => {
                if (!editing) refreshSuspensionBadge();
                if (!suspensionContextAlive(ctx)) {
                    // R-1（V-1）：迟到回包只解除本表单 busy，不撤下/不改写新上下文。
                    if (reasonFormSection(id) === section) setReasonFormBusy(section, false);
                    return;
                }
                instance.reasonForm.busy = false;
                if (reasonFormSection(id) === section) detachReasonForm();
                if (!editing) hostShowStatus("已挂起该会话", "ok");
                if (String(instance.selectedContactId) === String(id)) {
                    applySuspensionState(id, data);
                }
                refreshListWithFallback();
            }).catch((err) => {
                if (!suspensionContextAlive(ctx)) {
                    if (reasonFormSection(id) === section) setReasonFormBusy(section, false);
                    return;
                }
                setReasonFormBusy(section, false);
                setReasonFormError(section, err && err.message ? err.message : (editing ? "保存失败，请重试" : "挂起失败，请重试"));
            });
        }

        // ---- 显式取消/结束（DELETE；I-4 用服务端回包决定归入哪个 Tab） ----

        function suspensionActionError(trigger, message) {
            let section = null;
            if (trigger && typeof trigger.closest === "function") {
                section = trigger.closest(".mailbox-suspend-card-footer") || trigger.closest(".mc-actions");
            }
            if (!section || !section.parentNode) return;
            let error = section.parentNode.querySelector(".mailbox-suspend-error[data-role=suspension-action]");
            if (!error) {
                insertHtmlRelative(section, `<div class="mailbox-suspend-error" role="alert" data-role="suspension-action" hidden></div>`, "after");
                error = section.parentNode.querySelector(".mailbox-suspend-error[data-role=suspension-action]");
            }
            if (!error) return;
            error.textContent = message || "";
            error.hidden = !message;
        }

        function clearSuspensionActionError() {
            const el = host.querySelector ? host.querySelector('.mailbox-suspend-error[data-role="suspension-action"]') : null;
            if (el && el.parentNode) el.parentNode.removeChild(el);
        }

        function refreshSuspensionBadge() {
            const refreshBadge = hostFn("refreshUnmatchedBadge");
            if (refreshBadge) refreshBadge();
        }

        // 02（I-4）：挂起结束按回包 progressStatus 归类：pending>0→待处理；否则
        // PROVIDED→已提供、FOLLOWING→跟进中、NONE→已回复（沿原服务端资格）。
        function afterSuspensionRemoved(id, data) {
            const state = (data && typeof data === "object") ? data : {};
            const count = Number(state.suspensionPendingCount) || 0;
            const progress = progressStatusOf(state);
            hostShowStatus(
                count > 0
                    ? "已取消挂起，回到「待处理」"
                    : (progress === PROGRESS_PROVIDED
                        ? "已结束挂起，可在「已提供」查看"
                        : (progress === PROGRESS_FOLLOWING ? "已结束挂起，可在「跟进中」查看" : "已结束挂起")),
                "ok"
            );
            returnToMobileList();
            clearSuspensionActionError();
            const nextChip = count > 0
                ? CHIP_PENDING
                : (progress === PROGRESS_PROVIDED
                    ? CHIP_PROVIDED
                    : (progress === PROGRESS_FOLLOWING ? CHIP_FOLLOWED : CHIP_REPLIED));
            instance.chip = nextChip;
            instance.chipUserTouched = true;
            freezeDefaultProbe();
            instance.list.page = 0;
            syncChipButtons();
            syncSearchChrome();
            instance.completion.anchorKey = null;
            instance.completion.kept = false;
            instance.completion.busy = false;
            instance.completion.pendingAnchorKey = null;
            if (suspensionMatchesContact(id)) {
                instance.suspension.suspended = false;
                instance.suspension.suspensionPendingCount = count;
                instance.suspension.progressStatus = progress;
                instance.suspension.followed = progressFollowed(progress);
                instance.suspension.loaded = true;
            }
            renderList();
            loadList();
        }

        // 02（T2.2）：点击挂起先 GET 该 contact 状态；仍可挂起才插入行内原因表单。
        function onSuspensionClick(contactId, trigger) {
            const id = Number(contactId);
            if (!Number.isFinite(id) || id <= 0) return;
            if (instance.reasonForm.busy) return;
            const detailSelected = String(instance.selectedContactId) === String(id);
            const view = detailSelected
                ? suspensionViewForContact(id)
                : suspensionViewForContact(id, findSummaryByContactId(id));
            if (view.suspended) {
                resumeSuspension(id, trigger);
                return;
            }
            if (!instance.auth.ready) {
                hostShowStatus(AUTH_LOAD_ERROR_TEXT, "error");
                return;
            }
            const seq = instance.suspension.seq + 1;
            instance.suspension.seq = seq;
            const ctx = captureSuspensionContext(id);
            hostApi()(`/api/mail/mailbox/conversations/${id}/suspension`).then((data) => {
                if (seq !== instance.suspension.seq || !suspensionContextAlive(ctx)) return;
                if (data && data.suspended === true) {
                    if (String(instance.selectedContactId) === String(id)) applySuspensionState(id, data);
                    else refreshListWithFallback();
                    return;
                }
                openReasonForm(id, trigger);
            }).catch((err) => {
                if (seq !== instance.suspension.seq || !suspensionContextAlive(ctx)) return;
                hostShowStatus(err && err.message ? err.message : SUSPENSION_LOAD_ERROR_TEXT, "error");
            });
        }

        function resumeSuspension(id, trigger) {
            if (!instance.auth.ready || instance.reasonForm.busy) return;
            const contactId = Number(id);
            if (!Number.isFinite(contactId) || contactId <= 0) return;
            const ctx = captureSuspensionContext(contactId);
            if (trigger) trigger.disabled = true;
            clearSuspensionActionError();
            hostApi()(`/api/mail/mailbox/conversations/${contactId}/suspension`, { method: "DELETE" }).then((data) => {
                refreshSuspensionBadge();
                if (!suspensionContextAlive(ctx)) {
                    // R-1（V-1）：迟到回包只恢复按钮，不改导航/列表/挂起状态。
                    if (trigger && trigger.isConnected !== false) trigger.disabled = false;
                    return;
                }
                afterSuspensionRemoved(contactId, data);
            }).catch((err) => {
                if (!suspensionContextAlive(ctx)) {
                    if (trigger && trigger.isConnected !== false) trigger.disabled = false;
                    return;
                }
                if (trigger) trigger.disabled = false;
                suspensionActionError(trigger, err && err.message ? err.message : "取消挂起失败，请重试");
            });
        }

        function endSuspension(contactId) {
            const id = Number(contactId);
            if (instance.completion.busy) return;
            const ctx = captureSuspensionContext(id);
            instance.completion.busy = true;
            const line = completionLineEl();
            setCompletionBusy(line, true);
            hostApi()(`/api/mail/mailbox/conversations/${id}/suspension`, { method: "DELETE" }).then((data) => {
                refreshSuspensionBadge();
                if (!suspensionContextAlive(ctx)) {
                    // R-1（V-1）：迟到回包只解除完成行 busy，不改导航/列表/挂起状态。
                    instance.completion.busy = false;
                    setCompletionBusy(line, false);
                    return;
                }
                instance.completion.busy = false;
                afterSuspensionRemoved(id, data);
            }).catch((err) => {
                if (!suspensionContextAlive(ctx)) {
                    instance.completion.busy = false;
                    setCompletionBusy(line, false);
                    return;
                }
                instance.completion.busy = false;
                setCompletionBusy(line, false);
                if (line) setCompletionError(line, err && err.message ? err.message : "结束挂起失败，请重试");
            });
        }

        function completionLineEl() {
            const scroll = scrollEl();
            return scroll && scroll.querySelector ? scroll.querySelector(".mailbox-suspend-completion-line") : null;
        }

        function setCompletionBusy(line, busy) {
            if (!line) return;
            const keep = line.querySelector('[data-action="mc-suspension-keep"]');
            const end = line.querySelector('[data-action="mc-suspension-end"]');
            if (keep) keep.disabled = !!busy;
            if (end) {
                end.disabled = !!busy;
                end.textContent = busy ? "正在结束…" : "结束挂起";
            }
        }

        function setCompletionError(line, message) {
            if (!line) return;
            const error = line.querySelector(".mailbox-suspend-error");
            if (!error) return;
            error.textContent = message || "";
            error.hidden = !message;
        }

        function completionLineHtml() {
            const contactId = Number(instance.selectedContactId);
            const kept = instance.completion.kept;
            const progress = instance.suspension.progressStatus;
            const busy = instance.completion.busy;
            const small = kept
                ? "会话仍保留在「已挂起」，可随时结束。"
                : (progress === PROGRESS_PROVIDED
                    ? "结束后仍保留「已提供」，可在「已提供」查看。"
                    : (progress === PROGRESS_FOLLOWING
                        ? "结束后仍保留跟进中，可在「跟进中」查看。"
                        : "结束后按现有「已回复」规则归类。"));
            return `
                <div class="mailbox-suspend-completion-line" role="status" data-contact-id="${escapeText(contactId)}">
                    <div>
                        <strong>${kept ? "已继续挂起" : "所有消息已处理，是否结束挂起？"}</strong>
                        <small>${escapeText(small)}</small>
                        <div class="mailbox-suspend-error" role="alert" hidden></div>
                    </div>
                    <div class="mailbox-suspend-completion-actions">
                        ${kept ? "" : `<button type="button" class="mailbox-suspend-inline-secondary" data-action="mc-suspension-keep" data-contact-id="${escapeText(contactId)}"${busy ? " disabled" : ""}>继续挂起</button>`}
                        <button type="button" class="mailbox-suspend-inline-primary" data-action="mc-suspension-end" data-contact-id="${escapeText(contactId)}"${busy ? " disabled" : ""}>${busy ? "正在结束…" : "结束挂起"}</button>
                    </div>
                </div>`;
        }

        function keepSuspension() {
            if (instance.completion.kept) return;
            instance.completion.kept = true;
            instance.completion.renderedKept = null;
            syncCompletionLine();
        }

        // ---- 原位处理确认（S-3/I-7） ----

        function openProcessConfirm(key) {
            const message = messageByKey(key);
            if (!message || message.processStatus !== "MANUAL_REVIEW") return;
            if (!instance.auth.ready) {
                hostShowStatus(AUTH_LOAD_ERROR_TEXT, "error");
                return;
            }
            instance.processConfirm.key = key;
            instance.processConfirm.busy = false;
            renderTimeline();
        }

        function cancelProcessConfirm() {
            if (instance.processConfirm.key == null) return;
            const key = instance.processConfirm.key;
            instance.processConfirm.key = null;
            instance.processConfirm.busy = false;
            renderTimeline();
            const article = messageElByKey(key);
            const pending = article ? article.querySelector('[data-action="mc-mark-resolved"]') : null;
            if (pending && typeof pending.focus === "function") pending.focus();
        }

        function focusArticle(key) {
            const article = messageElByKey(key);
            if (!article) return;
            if (article.setAttribute && !article.getAttribute("tabindex")) article.setAttribute("tabindex", "-1");
            if (typeof article.focus === "function") article.focus();
        }

        // 空页回退最后有效页（I-1：处理最后待处理信后列表可能空页）
        // 02（T-2）：首查与回退 fetch 后先检查 disposed/data==null 并短路——过期/失败的 null
        // 回包不得触发旧页回退或选中项协调。
        function refreshListWithFallback() {
            const page = instance.list.page;
            return fetchList({ page }).then((data) => {
                if (instance.disposed || data == null) return data;
                const items = instance.list.items || [];
                const total = Number(instance.list.total) || 0;
                if (items.length === 0 && page > 0 && total > 0) {
                    return fetchList({ page: page - 1 }).then((retryData) => {
                        if (instance.disposed || retryData == null) return retryData;
                        resolveFocusAndSelection();
                        return retryData;
                    });
                }
                resolveFocusAndSelection();
                return data;
            });
        }

        // --------------------------------------------------------------
        // 待匹配（邮件级队列，I-3/I-5/I-6）
        // --------------------------------------------------------------

        function renderUnmatchedEmpty() {
            const body = conversationBody();
            if (!body) return;
            body.setAttribute("aria-label", "待匹配来信处理");
            body.innerHTML = `
                <div class="mc-scroll" tabindex="0" aria-label="待匹配来信详情"><div class="mc-empty">请选择左侧待匹配来信</div></div>
            `;
        }

        // 归还 lease（幂等）：详情面板先回到原父节点与原位置，才允许改写右栏/根节点（I-5）。
        function releaseUnmatchedDetailNode() {
            const release = hostFn("mcHostReleaseUnmatchedDetail");
            if (release) release();
            instance.unmatchedDetailHost = null;
        }

        function clearUnmatchedState() {
            releaseUnmatchedDetailNode();
            instance.selectedUnmatchedId = null;
        }

        // 宿主内是否仍真实持有唯一的 #unmatchedDetailPanel（宿主被外部归还/清空时自愈）。
        function unmatchedDetailMounted() {
            const host = instance.unmatchedDetailHost;
            if (!host) return false;
            const panel = host.querySelector ? host.querySelector("#unmatchedDetailPanel") : null;
            if (!panel) {
                instance.unmatchedDetailHost = null;
                return false;
            }
            return true;
        }

        function resolveUnmatchedSelection() {
            const items = instance.list.items || [];
            if (instance.selectedUnmatchedId == null) {
                releaseUnmatchedDetailNode();
                renderUnmatchedEmpty();
                return;
            }
            const stillPresent = items.some((item) => String(item.id) === String(instance.selectedUnmatchedId));
            if (!stillPresent) {
                // 选中记录经服务端刷新消失（绑定成功/已标记处理/切页）：先归还，再回空态。
                clearUnmatchedState();
                setMobilePane("list");
                renderUnmatchedEmpty();
                return;
            }
            if (!unmatchedDetailMounted()) mountUnmatchedDetail(instance.selectedUnmatchedId);
        }

        function selectUnmatched(id) {
            const items = instance.list.items || [];
            const item = items.find((entry) => String(entry.id) === String(id));
            if (!item) return;
            setMobilePane("detail");
            const changed = String(instance.selectedUnmatchedId) !== String(item.id);
            instance.selectedUnmatchedId = Number(item.id);
            renderUnmatchedList();
            if (!changed && unmatchedDetailMounted()) return;
            mountUnmatchedDetail(Number(item.id));
        }

        function mountUnmatchedDetail(id) {
            releaseUnmatchedDetailNode();
            const body = conversationBody();
            if (!body) return;
            const mount = hostFn("mcHostMountUnmatchedDetail");
            body.setAttribute("aria-label", "待匹配来信处理");
            body.innerHTML = `
                <div class="mc-scroll" tabindex="0" aria-label="待匹配来信详情"><div class="mc-empty">正在加载来信详情…</div></div>
            `;
            const scrollHost = body.querySelector ? body.querySelector(".mc-scroll") : null;
            if (!mount || !scrollHost) {
                body.innerHTML = `
                    <div class="mc-scroll" tabindex="0" aria-label="待匹配来信详情"><div class="mc-empty" role="alert">来信处理面板不可用，请刷新页面重试</div></div>
                `;
                return;
            }
            instance.unmatchedDetailHost = scrollHost;
            let started = null;
            try {
                started = mount(scrollHost, id);
            } catch (e) {
                instance.unmatchedDetailHost = null;
                scrollHost.innerHTML = `<div class="mc-empty" role="alert">来信处理面板不可用，请刷新页面重试</div>`;
                return;
            }
            if (started && typeof started.catch === "function") {
                started.catch(() => {
                    if (instance.disposed || instance.unmatchedDetailHost !== scrollHost) return;
                    instance.unmatchedDetailHost = null;
                    scrollHost.innerHTML = `<div class="mc-empty" role="alert">来信处理面板不可用，请刷新页面重试</div>`;
                });
            }
        }

        function syncSearchChrome() {
            const unmatched = isUnmatchedChip();
            const input = host.querySelector ? host.querySelector('.mc-search-row input[type="search"]') : null;
            if (input && typeof input.setAttribute === "function") {
                input.setAttribute("aria-label", unmatched ? "搜索待匹配来信" : "搜索专家");
                input.setAttribute("placeholder", unmatched ? "搜索发件邮箱、主题" : "搜索专家姓名、邮箱");
            }
            const toggle = host.querySelector ? host.querySelector('[data-action="mc-more-filters"]') : null;
            if (toggle) toggle.hidden = unmatched;
            if (unmatched && instance.popoverOpen) closeFilterPopover({ restore: false, focusButton: false });
        }

        // 离开待匹配 Tab：归还 lease 并把右栏恢复为专家空态。
        function leaveUnmatchedMode() {
            clearUnmatchedState();
            renderConversationEmpty();
            const body = conversationBody();
            if (body) body.setAttribute("aria-label", "专家往来信件");
        }

        // --------------------------------------------------------------
        // 焦点（T1-5：外部 focus 新 contactId 必须响应）
        // --------------------------------------------------------------

        function clearSelectedConversation() {
            instance.seq += 1;
            instance.convEpoch += 1;
            instance.pendingPosition = null;
            teardownConversationSubViews();
            resetSuspensionState();
            instance.selectedContactId = null;
            instance.selectedSummary = null;
            instance.conversation.contact = null;
            instance.draftsRef = null;
        }

        function renderFocusMissed(focus) {
            saveCurrentConversation();
            clearSelectedConversation();
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
                        instance.focusHandledContactId = focus.contactId;
                        selectExpert(found, { skipListReload: true, present: instance.focusPaneEpoch === instance.paneEpoch });
                        return;
                    }
                    locateFocusExpert(focus);
                    return;
                }
                instance.focusHandledContactId = focus.contactId;
            }
            if (instance.selectedContactId != null && !instance.list.error) {
                const stillPresent = instance.list.items.some(
                    (item) => String(item.contactId) === String(instance.selectedContactId)
                );
                if (stillPresent) {
                    const freshSummary = findSummaryByContactId(instance.selectedContactId);
                    if (freshSummary) instance.selectedSummary = freshSummary;
                    if (String(instance.conversation.accountScope || "") !== accountFilterFromOptions()) {
                        selectExpert(freshSummary, { skipListReload: true, present: false, force: true });
                    } else if (!instance.conversation.loading) refreshConversationQuiet();
                } else {
                    // 当前筛选不再包含该专家：先保存，再回到“请选择专家”空态
                    saveCurrentConversation();
                    clearSelectedConversation();
                    setMobilePane("list");
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
            const presentationEpoch = instance.focusPaneEpoch;
            const selectionEpoch = instance.convEpoch;
            instance.focusLocating = true;
            instance.listSeq += 1;
            const mySeq = instance.listSeq;
            const params = new URLSearchParams();
            params.set("page", "0");
            params.set("size", String(PAGE_SIZE));
            params.set("q", String(focus.email).trim());
            hostApi()(`/api/mail/mailbox/conversations?${params.toString()}`).then((data) => {
                instance.focusLocating = false;
                if (instance.disposed || mySeq !== instance.listSeq || selectionEpoch !== instance.convEpoch) return;
                const located = (data && Array.isArray(data.items) ? data.items : []).find(
                    (item) => String(item.contactId) === String(focus.contactId)
                );
                if (located) {
                    instance.focusHandledContactId = focus.contactId;
                    selectExpert(located, { skipListReload: true, present: presentationEpoch === instance.paneEpoch });
                } else {
                    instance.focusMissedContactId = focus.contactId;
                    instance.focusHandledContactId = focus.contactId;
                    renderFocusMissed(focus);
                }
            }).catch(() => {
                instance.focusLocating = false;
                if (instance.disposed || mySeq !== instance.listSeq || selectionEpoch !== instance.convEpoch) return;
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
            closeExpertNoteDialog({ restoreFocus: false, force: true });
            invalidateExpertNote();
            closeContactTimingDialog({ restoreFocus: false });
            invalidateContactTiming();
            closeManageOverlay({ restoreFocus: false });
            closeFollowUpDialog({ restoreFocus: false });
            closeMaterialRequestDialog({ restoreFocus: false });
            closeTemplateReferenceDialog({ restoreFocus: false });
            if (instance.workbench.instance) {
                try { instance.workbench.instance.unmount(); } catch (e) { /* noop */ }
                instance.workbench.instance = null;
                instance.workbench.processingId = null;
            }
            teardownMeetingViews();
            const releaseMaterials = hostFn("unmountExpertMaterialsHosts");
            if (releaseMaterials && instance.host) releaseMaterials(instance.host);
        }

        function accountFilterFromOptions() {
            return filterAccountScope(instance.filters);
        }

        function selectExpert(item, options) {
            const opts = options || {};
            const sameOwner = Number(instance.selectedContactId) === Number(item.contactId)
                && String(instance.conversation.accountScope || "") === accountFilterFromOptions();
            if (sameOwner && !opts.force && !instance.conversation.error) {
                if (opts.present !== false) setMobilePane("detail", opts.trigger);
                return;
            }
            // DOM 草稿必须在切换旧 owner 之前采集。
            saveCurrentConversation();
            if (opts.present !== false) setMobilePane("detail", opts.trigger);
            teardownConversationSubViews();
            instance.pendingPosition = null;
            instance.clearedEditorSnapshot = null;
            instance.selectedContactId = Number(item.contactId);
            instance.selectedSummary = item;
            instance.seq += 1;
            instance.convEpoch += 1;
            const mySeq = instance.seq;
            const myEpoch = instance.convEpoch;
            instance.conversation.accountScope = accountFilterFromOptions();
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
            resetSuspensionState();
            renderConversationScaffold();

            const contactId = Number(item.contactId);
            loadExpertNote(contactId);
            loadSuspensionState(contactId);
            // c3（I-3）：换专家即作废旧 timing 代次，再按新 contact 取推荐。
            loadContactTiming(contactId);
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
                // 02（I-3）：时间线就绪后重算完成提示锚点位置。
                renderSuspensionDetail();
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
                renderSuspensionDetail();
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

        // I-4：只替换详情回复时间槽；不改选中对象、不重建 header、不触发草稿/锚点/请求。
        function renderLastReplyHeader(summary) {
            if (isUnmatchedChip()) return;
            if (instance.selectedContactId == null) return;
            if (!summary || String(summary.contactId) !== String(instance.selectedContactId)) return;
            const body = conversationBody();
            const slot = body && body.querySelector ? body.querySelector('[data-role="last-reply-time"]') : null;
            if (!slot) return;
            slot.innerHTML = lastReplyDetailInner(lastReplyDisplay(summary));
        }

        function renderHeader() {
            const body = conversationBody();
            if (!body) return;
            const summary = instance.selectedSummary || {};
            const contactId = Number(instance.selectedContactId);
            const materialCount = Number(summary.materialCount) || 0;
            const head = body.querySelector(".mc-header");
            if (!head) return;
            const identity = head.querySelector(".mc-identity");
            if (identity) {
                identity.innerHTML = `<h2>${escapeText(summary.name || summary.email || "-")}</h2><p>${escapeText(conversationSummaryInfo() || "-")}</p><span class="mailbox-reply-detail" data-role="last-reply-time">${lastReplyDetailInner({ kind: "unavailable", text: LAST_REPLY_UNAVAILABLE_TEXT })}</span><span class="calendar-summary" data-role="meeting-summary"></span>`;
            }
            // I-4：优先取当前页同一行，回退身份匹配的 selectedSummary；都不可用保留默认文案。
            const selectedRow = contactId ? findSummaryByContactId(contactId) : null;
            renderLastReplyHeader(selectedRow || summary);
            const actions = head.querySelector(".mc-actions");
            if (actions) {
                const suspendView = suspensionViewForContact(contactId, summary);
                actions.innerHTML = `
                    ${suspensionDetailButtonHtml(contactId, suspendView)}
                    <button class="button" type="button" data-action="mc-open-materials" data-contact-id="${escapeText(contactId)}">材料 ${materialCount}</button>
                    <button class="button" type="button" data-action="mc-manage-expert">管理</button>
                    <button class="button" type="button" data-action="mc-add-schedule">新增排期</button>
                    <button class="button" type="button" data-action="mc-edit-schedule">变更日期</button>
                    <button class="button danger" type="button" data-action="mc-cancel-schedule">取消排期</button>
                `;
            }
            renderHeaderMeetingSummary();
            ensureMeetingSummaryFor(contactId);
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
                <div class="mc-note-tags">${statusBadge}${levelBadge}${expertTagSpans}${orcid ? `<span>ORCID ${escapeText(orcid)}</span>` : ""}<button class="mc-text-button" type="button" data-action="mc-open-expert" data-contact-id="${escapeText(Number(instance.selectedContactId))}">查看专家详情 ↗</button></div>
                <div class="mc-note-row"><section class="mc-note-slot" aria-label="专家备注">${noteMarkup()}</section>${contactTimingMarkup()}</div>
            `;
        }

        function invalidateExpertNote() {
            const state = instance.expertNote;
            state.readSeq += 1;
            state.contactId = null;
            state.loaded = false;
            state.loading = false;
            state.error = "";
            state.data = null;
            state.saving = false;
        }

        function expertNoteContextAlive(contactId, epoch) {
            return !instance.disposed && Number(instance.selectedContactId) === contactId
                && instance.convEpoch === epoch && instance.expertNote.contactId === contactId;
        }

        function noteMarkup() {
            const state = instance.expertNote;
            const ready = state.loaded && state.contactId === Number(instance.selectedContactId);
            const note = ready && state.data ? state.data.note : "";
            const failed = !!state.error;
            const text = failed ? "读取失败 · 重试" : (!ready || state.loading ? "加载中…" : (note || "＋ 添加备注"));
            const label = failed ? "重试读取专家备注" : (note ? "查看或编辑专家备注" : "添加专家备注");
            return `<button class="mc-note-trigger" type="button" data-action="${failed ? "mc-note-retry" : "mc-note-open"}" aria-haspopup="dialog" aria-label="${label}"${state.saving || (!failed && (!ready || state.loading)) ? " disabled" : ""}><span class="mc-note-label">备注：</span><span class="mc-note-text">${escapeText(text)}</span>${ready && !state.loading && !failed && note ? '<span class="mc-note-edit">编辑</span>' : ""}</button>`;
        }

        function repaintExpertNote() {
            const slot = host.querySelector(".mc-note-slot");
            if (slot) slot.innerHTML = noteMarkup();
        }

        function loadExpertNote(contactId) {
            const id = Number(contactId);
            const state = instance.expertNote;
            if (instance.disposed || state.saving || id <= 0 || !Number.isFinite(id) || id !== Number(instance.selectedContactId)) return;
            state.contactId = id;
            state.loading = true;
            state.error = "";
            const epoch = instance.convEpoch;
            const seq = ++state.readSeq;
            repaintExpertNote();
            hostApi()(`/api/mail/contact-notes/${id}`).then((data) => {
                if (!expertNoteContextAlive(id, epoch) || seq !== state.readSeq) return;
                state.data = data;
                state.loaded = true;
                state.loading = false;
                repaintExpertNote();
            }).catch(() => {
                if (!expertNoteContextAlive(id, epoch) || seq !== state.readSeq) return;
                state.loaded = false;
                state.loading = false;
                state.error = "读取失败";
                repaintExpertNote();
            });
        }

        function closeExpertNoteDialog(options) {
            const opts = options || {};
            const state = instance.expertNote;
            const dialog = state.dialog;
            if (!dialog || (state.saving && !opts.force)) return;
            state.dialog = null;
            const node = dialog.node;
            dialog.listeners.forEach(([type, listener]) => node.removeEventListener(type, listener));
            if (typeof node.close === "function") node.close();
            if (node.parentNode) node.parentNode.removeChild(node);
            if (opts.restoreFocus !== false && !instance.disposed) {
                focusIfAvailable(host.querySelector('[data-action="mc-note-open"]'));
            }
        }

        function expertNoteMetadata(data) {
            if (!data || !data.note) return "";
            let time = "";
            if (data.updatedAt && /(?:Z|[+-]\d{2}:\d{2})$/.test(data.updatedAt)) {
                const date = new Date(data.updatedAt);
                if (!Number.isNaN(date.getTime())) {
                    const parts = new Intl.DateTimeFormat("en-CA", {
                        timeZone: "Asia/Shanghai", year: "numeric", month: "2-digit", day: "2-digit",
                        hour: "2-digit", minute: "2-digit", hourCycle: "h23"
                    }).formatToParts(date);
                    const part = (type) => parts.find((entry) => entry.type === type).value;
                    time = `${part("year")}-${part("month")}-${part("day")} ${part("hour")}:${part("minute")} 北京时间`;
                }
            }
            return [data.updatedBy, time].filter(Boolean).join(" · ");
        }

        function saveExpertNote(dialog) {
            const state = instance.expertNote;
            if (state.dialog !== dialog || state.saving || !expertNoteContextAlive(dialog.contactId, dialog.epoch)) return;
            const input = dialog.node.querySelector("textarea");
            const error = dialog.node.querySelector(".mc-note-error");
            const rawNote = input.value;
            if (rawNote.length > 2000) {
                error.textContent = "备注不能超过 2000 个字符";
                error.hidden = false;
                return;
            }
            state.saving = true;
            state.readSeq += 1;
            state.loading = false;
            error.hidden = true;
            input.disabled = true;
            dialog.node.querySelectorAll("button").forEach((button) => { button.disabled = true; });
            repaintExpertNote();
            hostApi()(`/api/mail/contact-notes/${dialog.contactId}`, {
                method: "PUT", body: JSON.stringify({ note: rawNote })
            }).then((data) => {
                if (!expertNoteContextAlive(dialog.contactId, dialog.epoch) || state.dialog !== dialog) return;
                state.data = data;
                state.loaded = true;
                state.error = "";
                state.saving = false;
                repaintExpertNote();
                closeExpertNoteDialog({ restoreFocus: true });
                hostShowStatus("备注已保存", "ok");
            }).catch((err) => {
                if (!expertNoteContextAlive(dialog.contactId, dialog.epoch) || state.dialog !== dialog) return;
                state.saving = false;
                input.disabled = false;
                dialog.node.querySelectorAll("button").forEach((button) => { button.disabled = false; });
                error.textContent = `备注保存失败，请重试${err && err.message ? `：${err.message}` : ""}`;
                error.hidden = false;
                repaintExpertNote();
            });
        }

        function openExpertNoteDialog() {
            const state = instance.expertNote;
            if (state.saving || state.loading || !state.loaded || state.error || state.contactId !== Number(instance.selectedContactId)) return;
            closeExpertNoteDialog({ restoreFocus: false });
            closeContactTimingDialog({ restoreFocus: false });
            closeManageOverlay({ restoreFocus: false });
            closeFollowUpDialog({ restoreFocus: false });
            closeMaterialRequestDialog({ restoreFocus: false });
            closeTemplateReferenceDialog({ restoreFocus: false });
            const doc = docRoot();
            const node = doc.createElement("dialog");
            // The existing module-wide sequence guarantees IDs do not collide across instances.
            const prefix = `mc-note-${++contactTimingInstanceSeq}`;
            node.setAttribute("class", "mc-note-dialog");
            node.setAttribute("aria-labelledby", `${prefix}-title`);
            node.innerHTML = `<header><strong id="${prefix}-title">专家备注</strong><button class="mc-note-close" type="button" data-note-action="close" aria-label="关闭专家备注">×</button></header>
                <div class="mc-note-body"><label for="${prefix}-input">仅内部可见</label><textarea id="${prefix}-input" aria-label="专家备注" maxlength="2000" placeholder="填写合作意向、沟通偏好、待办事项等…"></textarea><div class="mc-note-hint" data-note-meta></div><div class="mc-note-error" role="alert" hidden></div></div>
                <footer><span class="mc-note-hint">Ctrl / ⌘ + Enter 保存</span><div><button class="button" type="button" data-note-action="cancel">取消</button><button class="button primary" type="button" data-note-action="save">保存备注</button></div></footer>`;
            const dialog = { node, contactId: state.contactId, epoch: instance.convEpoch, listeners: [] };
            const input = node.querySelector("textarea");
            input.value = state.data.note;
            const metadata = expertNoteMetadata(state.data);
            const updateCount = () => { node.querySelector("[data-note-meta]").textContent = `${input.value.length} / 2000${metadata ? ` · ${metadata}` : ""}`; };
            const listen = (type, fn) => { node.addEventListener(type, fn); dialog.listeners.push([type, fn]); };
            listen("input", updateCount);
            listen("click", (event) => {
                if (state.dialog !== dialog) return;
                const button = event.target && event.target.closest("[data-note-action]");
                if (!button) return;
                if (button.dataset.noteAction === "save") saveExpertNote(dialog);
                else closeExpertNoteDialog({ restoreFocus: true });
            });
            listen("cancel", (event) => {
                event.preventDefault();
                if (state.dialog === dialog) closeExpertNoteDialog({ restoreFocus: true });
            });
            listen("keydown", (event) => {
                if (state.dialog !== dialog || event.isComposing) return;
                if (event.key === "Escape") {
                    event.preventDefault();
                    closeExpertNoteDialog({ restoreFocus: true });
                } else if (event.key === "Enter" && (event.ctrlKey || event.metaKey)) {
                    event.preventDefault();
                    saveExpertNote(dialog);
                }
            });
            state.dialog = dialog;
            updateCount();
            doc.body.appendChild(node);
            node.showModal();
            focusIfAvailable(input);
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

        // --------------------------------------------------------------
        // 联系时间（fast-p 2026-10-02 c3 · I-1..I-6；S-1/S-2/S-3）
        // 读：GET /api/mail/contact-locations/{id}/timing（主行 + 依据）、
        //     GET /api/mail/contact-locations/countries 与 GET /{id}（配置弹窗）；
        // 写：唯一写路径是配置弹窗的 PUT /{id}。
        // GET 代次由 timing.seq 固化；弹窗加载/保存由独立 dialogSeq 固化（I-3）。
        // --------------------------------------------------------------

        function contactTimingState() {
            return instance.contactTiming;
        }

        /** GET 代次失效：旧响应一律丢弃，且不留旧时间（I-3/I-4）。 */
        function forgetContactTiming() {
            const state = contactTimingState();
            state.seq += 1;
            state.data = null;
            state.loading = false;
            state.error = "";
        }

        function invalidateContactTiming() {
            forgetContactTiming();
            contactTimingState().contactId = null;
        }

        /** S-1：状态行末尾的紧凑组；只读实例状态，绝不在渲染里发请求（I-1）。 */
        function contactTimingMarkup() {
            const contactId = Number(instance.selectedContactId);
            if (!Number.isFinite(contactId) || contactId <= 0) return "";
            const state = contactTimingState();
            const fresh = state.contactId === contactId;
            const data = fresh ? state.data : null;
            const error = fresh ? state.error : "";
            const loading = fresh ? state.loading : true;
            const location = data && data.location ? data.location : null;
            const configured = !!(location && location.configured);
            const label = configured
                ? String(location.countryLabel || location.countryCode || "已配置")
                : "配置所在地";
            let extra = "";
            if (error) {
                extra = `<span class="contact-timing-note">${escapeText(error)}</span><button class="mc-text-button" type="button" data-action="mc-contact-timing-retry">重试</button>`;
            } else if (loading && !data) {
                extra = `<span class="contact-timing-note">${CONTACT_TIMING_LOADING_TEXT}</span>`;
            } else if (data && data.recommendation) {
                const rec = data.recommendation;
                extra = `<span class="contact-timing-recommend">建议北京 <strong title="${escapeText(contactBeijingRangeTitle(rec))}">${escapeText(contactBeijingRangeShort(rec))}</strong></span><button class="mc-text-button contact-timing-info" type="button" data-action="mc-contact-timing-evidence" aria-label="查看推荐依据" aria-haspopup="dialog">ⓘ</button>`;
            }
            return `<div class="contact-timing" data-role="contact-timing" aria-live="polite"><button class="mc-text-button contact-timing-location" type="button" data-action="mc-contact-location" aria-haspopup="dialog">${escapeText(label)} ▾</button>${extra}</div>`;
        }

        // ---- 时间格式化：显式时区，结果与设备时区无关（I-2） ----

        function contactZoneParts(iso, zoneId) {
            if (!iso || !zoneId) return null;
            const intl = global.Intl;
            if (!intl || typeof intl.DateTimeFormat !== "function") return null;
            let parts = null;
            try {
                parts = new intl.DateTimeFormat("zh-CN", {
                    timeZone: String(zoneId),
                    hourCycle: "h23",
                    year: "numeric",
                    month: "numeric",
                    day: "numeric",
                    hour: "2-digit",
                    minute: "2-digit"
                }).formatToParts(new Date(String(iso)));
            } catch (e) {
                return null;
            }
            const fields = {};
            (parts || []).forEach((part) => {
                if (part && part.type && part.type !== "literal") fields[part.type] = part.value;
            });
            if (!fields.year || !fields.month || !fields.day || !fields.hour || !fields.minute) return null;
            return {
                year: Number(fields.year),
                month: Number(fields.month),
                day: Number(fields.day),
                hour: String(Number(fields.hour) % 24).padStart(2, "0"),
                minute: String(fields.minute).padStart(2, "0")
            };
        }

        function contactClock(parts) {
            return parts ? `${parts.hour}:${parts.minute}` : "";
        }

        function contactDayLabel(parts, withYear) {
            if (!parts) return "";
            return `${withYear ? `${parts.year}年` : ""}${parts.month}月${parts.day}日`;
        }

        function contactSameDay(a, b) {
            return !!(a && b && a.year === b.year && a.month === b.month && a.day === b.day);
        }

        /** 主行只显示两位时:分；跨北京日期时末端写“次日”（I-2）。 */
        function contactBeijingRangeShort(rec) {
            const start = contactZoneParts(rec && rec.beijingStart, CONTACT_TIMING_BEIJING_ZONE);
            const end = contactZoneParts(rec && rec.beijingEnd, CONTACT_TIMING_BEIJING_ZONE);
            if (!start || !end) return "";
            return `${contactClock(start)}–${contactSameDay(start, end) ? "" : "次日"}${contactClock(end)}`;
        }

        /** 完整日期区间；起止不同日两端都带日期，跨年再带年份（I-2）。 */
        function contactRangeLabel(start, end) {
            if (!start || !end) return "";
            const withYear = start.year !== end.year;
            if (contactSameDay(start, end)) {
                return `${contactDayLabel(start, withYear)} ${contactClock(start)}–${contactClock(end)}`;
            }
            return `${contactDayLabel(start, withYear)} ${contactClock(start)}–${contactDayLabel(end, withYear)} ${contactClock(end)}`;
        }

        function contactBeijingRangeTitle(rec) {
            const start = contactZoneParts(rec && rec.beijingStart, CONTACT_TIMING_BEIJING_ZONE);
            const end = contactZoneParts(rec && rec.beijingEnd, CONTACT_TIMING_BEIJING_ZONE);
            const label = contactRangeLabel(start, end);
            return label ? `北京时间 ${label}` : "";
        }

        // ---- 推荐读取（主行 + 依据；失败不影响会话加载） ----

        function loadContactTiming(contactId, failureText) {
            const id = Number(contactId);
            if (!Number.isFinite(id) || id <= 0) return;
            const state = contactTimingState();
            state.contactId = id;
            state.seq += 1;
            state.loading = true;
            state.error = "";
            const mySeq = state.seq;
            hostApi()(`/api/mail/contact-locations/${id}/timing`).then((data) => {
                if (instance.disposed || mySeq !== state.seq) return;
                state.data = data || null;
                state.loading = false;
                state.error = "";
                repaintContactTiming();
            }).catch(() => {
                if (instance.disposed || mySeq !== state.seq) return;
                state.data = null;
                state.loading = false;
                state.error = failureText || CONTACT_TIMING_ERROR_TEXT;
                repaintContactTiming();
            });
        }

        /** 只重绘状态行：不重建时间线、不碰草稿与滚动（T-1）。 */
        function repaintContactTiming() {
            const body = conversationBody();
            if (!body || typeof body.querySelector !== "function") return;
            const meta = body.querySelector(".mc-header-meta");
            if (meta) renderHeaderMeta(meta);
        }

        // ---- 自有 dialog 生命周期（I-5：直接挂 body，只移除自己的节点） ----

        function contactDialogNode(dialog) {
            return dialog && dialog.node ? dialog.node : null;
        }

        function contactDialogField(dialog, selector) {
            const node = contactDialogNode(dialog);
            if (!node || typeof node.querySelector !== "function") return null;
            return node.querySelector(selector);
        }

        function contactDialogCurrent(seq) {
            if (instance.disposed) return null;
            const dialog = contactTimingState().dialog;
            return dialog && dialog.seq === seq ? dialog : null;
        }

        function contactDialogError(dialog, message) {
            const box = contactDialogField(dialog, ".contact-timing-error");
            if (!box) return;
            box.textContent = message ? String(message) : "";
            if (message) {
                if (typeof box.removeAttribute === "function") box.removeAttribute("hidden");
            } else if (typeof box.setAttribute === "function") {
                box.setAttribute("hidden", "");
            }
        }

        /** 保存中/未就绪时字段与主按钮禁用；关闭/取消始终可用（I-4）。 */
        function syncContactDialogForm(dialog) {
            const locked = !!dialog.busy || !dialog.ready;
            const save = contactDialogField(dialog, 'button[type="submit"]');
            if (save) {
                save.disabled = locked;
                save.textContent = dialog.busy ? "保存中…" : "保存";
            }
            const node = contactDialogNode(dialog);
            if (!node || typeof node.querySelectorAll !== "function") return;
            [...node.querySelectorAll("select"), ...node.querySelectorAll("input")].forEach((field) => {
                field.disabled = locked;
            });
        }

        function beginContactDialog(kind, contactId, trigger) {
            const state = contactTimingState();
            closeContactTimingDialog({ restoreFocus: false });
            state.dialogSeq += 1;
            contactTimingInstanceSeq += 1;
            state.prefix = `ct${contactTimingInstanceSeq}`;
            const doc = docRoot();
            if (!doc || typeof doc.createElement !== "function" || !doc.body) return null;
            const dialog = {
                kind,
                seq: state.dialogSeq,
                contactId: Number(contactId),
                node: null,
                trigger: trigger || null,
                ready: false,
                busy: false,
                catalog: state.catalog || null,
                location: null,
                error: ""
            };
            const node = doc.createElement("dialog");
            node.setAttribute("class", "contact-timing-dialog");
            node.setAttribute("data-kind", kind);
            node.setAttribute("aria-labelledby", `${state.prefix}-${kind === "evidence" ? "evidence" : "location"}-title`);
            dialog.node = node;
            // 自有节点自己绑事件，不借共享 portal 委托，也不进其 innerHTML（I-5）。
            if (typeof node.addEventListener === "function") {
                node.addEventListener("click", onContactDialogClick);
                node.addEventListener("change", onContactDialogChange);
                node.addEventListener("input", onContactDialogInput);
                node.addEventListener("submit", onContactDialogSubmit);
                node.addEventListener("cancel", onContactDialogCancel);
                node.addEventListener("keydown", onContactDialogKeyDown);
            }
            state.dialog = dialog;
            doc.body.appendChild(node);
            return dialog;
        }

        function showContactDialog(dialog) {
            const node = contactDialogNode(dialog);
            if (!node) return;
            if (typeof node.showModal === "function") {
                try {
                    node.showModal();
                    return;
                } catch (e) { /* fallback: 静态 open */ }
            }
            if (typeof node.setAttribute === "function") node.setAttribute("open", "");
        }

        /** 只移除本实例拥有的 dialog，绝不清空共享 portal（I-5）。 */
        function closeContactTimingDialog(options) {
            const opts = options || {};
            const state = contactTimingState();
            const dialog = state.dialog;
            state.dialogSeq += 1;
            state.dialog = null;
            if (!dialog) return;
            const node = dialog.node;
            if (node) {
                if (typeof node.close === "function") {
                    try {
                        node.close();
                    } catch (e) { /* noop */ }
                }
                if (typeof node.hasAttribute === "function" && node.hasAttribute("open")) node.removeAttribute("open");
                if (node.parentNode && typeof node.parentNode.removeChild === "function") {
                    node.parentNode.removeChild(node);
                } else if (typeof node.remove === "function") {
                    node.remove();
                }
            }
            if (opts.restoreFocus !== false) focusIfAvailable(dialog.trigger);
        }

        function onContactDialogCancel(event) {
            if (event && typeof event.preventDefault === "function") event.preventDefault();
            closeContactTimingDialog({ restoreFocus: true });
        }

        function onContactDialogKeyDown(event) {
            if (event && event.key === "Enter" && event.target && typeof event.target.getAttribute === "function") {
                const field = event.target.getAttribute("data-contact-filter");
                if (field === "countryCode" || field === "zoneId") {
                    if (typeof event.preventDefault === "function") event.preventDefault();
                    focusIfAvailable(contactDialogField(contactTimingState().dialog, `select[name="${field}"]`));
                    return;
                }
            }
            if (!event || event.key !== "Escape") return;
            if (typeof event.preventDefault === "function") event.preventDefault();
            closeContactTimingDialog({ restoreFocus: true });
        }

        function onContactDialogClick(event) {
            if (instance.disposed) return;
            const dialog = contactTimingState().dialog;
            if (!dialog) return;
            const target = event.target;
            const button = target && typeof target.closest === "function" ? target.closest("[data-action]") : null;
            const action = button && button.dataset ? button.dataset.action : "";
            if (action === "mc-contact-dialog-close") {
                closeContactTimingDialog({ restoreFocus: true });
                return;
            }
            if (action === "mc-contact-dialog-retry") {
                dialog.ready = false;
                contactDialogError(dialog, "");
                syncContactDialogForm(dialog);
                loadContactDialogData(dialog);
            }
        }

        /** 换国家只改草稿：zone 重置为空并重建选项，未提交前零 PUT（I-4）。 */
        function onContactDialogChange(event) {
            if (instance.disposed) return;
            const dialog = contactTimingState().dialog;
            if (!dialog || dialog.kind !== "location") return;
            const target = event.target;
            if (!target || typeof target.getAttribute !== "function") return;
            if (target.getAttribute("name") === "zoneId") {
                const country = contactDialogField(dialog, 'select[name="countryCode"]');
                renderContactDialogZoneOptions(dialog, country ? country.value : "", target.value);
                return;
            }
            if (target.getAttribute("name") !== "countryCode") return;
            const zoneSearch = contactDialogField(dialog, '[data-contact-filter="zoneId"]');
            if (zoneSearch) zoneSearch.value = "";
            renderContactDialogCountryOptions(dialog);
            renderContactDialogZoneOptions(dialog, String(target.value || ""), null);
        }

        function onContactDialogInput(event) {
            if (instance.disposed) return;
            const dialog = contactTimingState().dialog;
            if (!dialog || dialog.kind !== "location" || !dialog.ready || dialog.busy) return;
            const target = event.target;
            const field = target && typeof target.getAttribute === "function"
                ? target.getAttribute("data-contact-filter") : null;
            if (field === "countryCode") renderContactDialogCountryOptions(dialog);
            if (field === "zoneId") {
                const country = contactDialogField(dialog, 'select[name="countryCode"]');
                const zone = contactDialogField(dialog, 'select[name="zoneId"]');
                renderContactDialogZoneOptions(dialog, country ? country.value : "", zone ? zone.value : null);
            }
        }

        function onContactDialogSubmit(event) {
            if (event && typeof event.preventDefault === "function") event.preventDefault();
            if (instance.disposed) return;
            const dialog = contactTimingState().dialog;
            if (!dialog || dialog.kind !== "location") return;
            submitContactLocation(dialog);
        }

        // ---- 配置弹窗（目录 + 当前配置 + PUT） ----

        function contactCatalogCountries() {
            const catalog = contactTimingState().catalog;
            return catalog && Array.isArray(catalog.countries) ? catalog.countries : [];
        }

        function findContactCountry(code) {
            const wanted = String(code || "");
            if (!wanted) return null;
            return contactCatalogCountries().find((country) => country && String(country.code) === wanted) || null;
        }

        /** `{labelZh}（{id}）`；labelZh 已等于 id 时只显示一次（S-2）。 */
        function contactZoneOptionText(entry) {
            const id = entry && entry.id != null ? String(entry.id) : "";
            const label = entry && entry.labelZh ? String(entry.labelZh) : id;
            if (!id) return label;
            return label === id ? id : `${label}（${id}）`;
        }

        /** 搜索不改变草稿；未命中的已选项保留并标注，避免保存时误改所在地。 */
        function filterContactDialogOptions(dialog, field, entries, selectedValue) {
            const search = contactDialogField(dialog, `[data-contact-filter="${field}"]`);
            const query = String(search ? search.value : "").trim().toLowerCase();
            let matches = 0;
            const options = [];
            entries.forEach((entry) => {
                const matched = !query || `${entry.label} ${entry.value}`.toLowerCase().includes(query);
                if (matched) matches += 1;
                if (matched || entry.value === selectedValue) {
                    const label = `${entry.label}${matched ? "" : "（当前选择）"}`;
                    options.push(`<option value="${escapeText(entry.value)}">${escapeText(label)}</option>`);
                }
            });
            const hint = contactDialogField(dialog, `[data-contact-filter-hint="${field}"]`);
            if (hint) {
                hint.textContent = !query ? "" : matches ? `匹配 ${matches} 项，请在下方选择` : "无匹配结果，请更换关键词";
                hint.hidden = !query;
            }
            return options;
        }

        function renderContactDialogCountryOptions(dialog) {
            const select = contactDialogField(dialog, 'select[name="countryCode"]');
            if (!select) return;
            const selected = String(select.value || "");
            const options = ['<option value="">请选择国家 / 地区</option>'];
            const entries = contactCatalogCountries().filter((country) => country && country.code != null)
                .map((country) => ({ value: String(country.code), label: String(country.labelZh || country.code) }));
            options.push(...filterContactDialogOptions(dialog, "countryCode", entries, selected));
            select.innerHTML = options.join("");
            select.value = selected;
        }

        /** 多时区才展示具体时区字段；只有单个 zone 时 hidden 且提交 null（S-2/I-4）。 */
        function renderContactDialogZoneOptions(dialog, countryCode, selectedZoneId) {
            const country = findContactCountry(countryCode);
            const zones = country && Array.isArray(country.zones) ? country.zones : [];
            const multi = zones.length > 1;
            const field = contactDialogField(dialog, '[data-role="contact-zone-field"]');
            if (field) {
                if (multi) {
                    if (typeof field.removeAttribute === "function") field.removeAttribute("hidden");
                } else if (typeof field.setAttribute === "function") {
                    field.setAttribute("hidden", "");
                }
            }
            const help = contactDialogField(dialog, '[data-role="contact-default-zone"]');
            if (help) {
                if (country && country.defaultZoneId) {
                    help.textContent = `默认时区：${String(country.defaultZoneId)}`;
                    if (typeof help.removeAttribute === "function") help.removeAttribute("hidden");
                } else {
                    help.textContent = "";
                    if (typeof help.setAttribute === "function") help.setAttribute("hidden", "");
                }
            }
            const select = contactDialogField(dialog, 'select[name="zoneId"]');
            if (!select) return;
            if (!multi) {
                select.innerHTML = '<option value="">使用默认时区</option>';
                select.value = "";
                return;
            }
            const defaultEntry = zones.find((zone) => zone && String(zone.id) === String(country.defaultZoneId)) || null;
            const options = [`<option value="">${escapeText(`使用默认时区${defaultEntry ? ` · ${contactZoneOptionText(defaultEntry)}` : ""}`)}</option>`];
            const wanted = selectedZoneId == null ? "" : String(selectedZoneId);
            const entries = zones.filter((zone) => zone && zone.id != null)
                .map((zone) => ({ value: String(zone.id), label: contactZoneOptionText(zone) }));
            options.push(...filterContactDialogOptions(dialog, "zoneId", entries, wanted));
            select.innerHTML = options.join("");
            const present = wanted !== "" && zones.some((zone) => zone && String(zone.id) === wanted);
            select.value = present ? wanted : "";
        }

        function contactLocationDialogHtml(prefix) {
            const p = escapeText(prefix);
            return `
  <header>
    <div><h3 id="${p}-location-title">配置所在地</h3><p>仅用于联系时间推荐</p></div>
    <button class="button contact-timing-close" type="button" data-action="mc-contact-dialog-close" aria-label="关闭所在地配置">×</button>
  </header>
  <form data-role="contact-location-form">
    <div class="contact-timing-body">
      <div class="contact-timing-field">
        <label for="${p}-country">国家 / 地区</label>
        <input type="search" data-contact-filter="countryCode" aria-label="搜索国家 / 地区" aria-controls="${p}-country" placeholder="输入国家名称或代码，如：中国、US" autocomplete="off">
        <span class="contact-timing-help" data-contact-filter-hint="countryCode" role="status" hidden></span>
        <select id="${p}-country" name="countryCode" required><option value="">请选择国家 / 地区</option></select>
      </div>
      <div class="contact-timing-field" data-role="contact-zone-field" hidden>
        <label for="${p}-zone">具体时区（可选）</label>
        <input type="search" data-contact-filter="zoneId" aria-label="搜索时区" aria-controls="${p}-zone" placeholder="输入城市或时区，如：纽约、New_York" autocomplete="off">
        <span class="contact-timing-help" data-contact-filter-hint="zoneId" role="status" hidden></span>
        <select id="${p}-zone" name="zoneId"><option value="">使用默认时区</option></select>
      </div>
      <p class="contact-timing-help" data-role="contact-default-zone" hidden></p>
      <p class="contact-timing-help">样本不足时按当地 08:00–17:00 推荐，保存后显示北京时间。</p>
      <p class="contact-timing-error" role="alert" hidden></p>
    </div>
    <footer><button class="button" type="button" data-action="mc-contact-dialog-retry" hidden>重试</button><button class="button" type="button" data-action="mc-contact-dialog-close">取消</button><button class="button primary" type="submit">保存</button></footer>
  </form>
`;
        }

        function contactEvidenceDialogHtml(prefix, data) {
            const p = escapeText(prefix);
            const rec = (data && data.recommendation) || null;
            const location = (data && data.location) || {};
            const zoneId = String(location.effectiveZoneId || CONTACT_TIMING_BEIJING_ZONE);
            const beijing = contactRangeLabel(
                contactZoneParts(rec && rec.beijingStart, CONTACT_TIMING_BEIJING_ZONE),
                contactZoneParts(rec && rec.beijingEnd, CONTACT_TIMING_BEIJING_ZONE)
            );
            const local = contactRangeLabel(
                contactZoneParts(rec && rec.localStart, zoneId),
                contactZoneParts(rec && rec.localEnd, zoneId)
            );
            const samples = (rec && Array.isArray(rec.recentSamples)) ? rec.recentSamples.slice(0, 8) : [];
            const sampleItems = samples.map((sample) => {
                const beijingParts = contactZoneParts(sample && sample.receivedAtBeijing, CONTACT_TIMING_BEIJING_ZONE);
                const localParts = contactZoneParts(sample && sample.receivedAtLocal, zoneId);
                if (!beijingParts || !localParts) return "";
                return `<li><span>北京 ${escapeText(`${contactDayLabel(beijingParts, false)} ${contactClock(beijingParts)}`)}</span><span>当地 ${escapeText(`${contactDayLabel(localParts, false)} ${contactClock(localParts)}`)}</span></li>`;
            }).filter((item) => item).join("");
            const history = sampleItems ? `<ul class="contact-timing-history" aria-label="最近回复时间">${sampleItems}</ul>` : "";
            const sampleCount = Number(rec && rec.sampleCount) || 0;
            const replyDayCount = Number(rec && rec.replyDayCount) || 0;
            const modeLine = (rec && rec.mode === "WORK_HOURS")
                ? `样本不足，使用当地工作时间 08:00–17:00 · ${sampleCount} 次来信 · ${replyDayCount} 个回复日`
                : `结合历史回复 · ${sampleCount} 次来信 · ${replyDayCount} 个回复日`;
            const zoneText = location.usingDefaultZone === true ? `默认时区 ${zoneId}` : `手动时区 ${zoneId}`;
            const country = String(location.countryLabel || location.countryCode || "");
            const head = country ? `${country} · ${zoneText}` : zoneText;
            const limitAttr = rec && rec.historyTruncated ? "" : " hidden";
            return `
  <header><div><h3 id="${p}-evidence-title">推荐依据</h3><p>${escapeText(head)}</p></div><button class="button contact-timing-close" type="button" data-action="mc-contact-dialog-close" aria-label="关闭推荐依据">×</button></header>
  <div class="contact-timing-body">
    <div class="contact-timing-range"><strong>北京 ${escapeText(beijing)}</strong><span>当地 ${escapeText(local)}</span></div>
    <p class="contact-timing-help">${escapeText(modeLine)}</p>
    <p class="contact-timing-help">基于全部业务账号的已关联来信；最近 180 天，近期记录权重更高。</p>
    <p class="contact-timing-help" data-role="contact-history-limit"${limitAttr}>仅使用最近 1000 条范围内的去重来信。</p>
    ${history}
  </div>
  <footer><button class="button" type="button" data-action="mc-contact-dialog-close">关闭</button></footer>
`;
        }

        function contactLocationTrigger() {
            return host.querySelector ? host.querySelector('[data-action="mc-contact-location"]') : null;
        }

        /** 目录可在本实例内复用成功结果；失败可重试（T-2）。 */
        function loadContactDialogData(dialog) {
            const seq = dialog.seq;
            const contactId = dialog.contactId;
            const catalogPromise = dialog.catalog
                ? Promise.resolve(dialog.catalog)
                : hostApi()("/api/mail/contact-locations/countries").catch(() => null);
            const locationPromise = hostApi()(`/api/mail/contact-locations/${contactId}`).catch(() => null);
            Promise.all([catalogPromise, locationPromise]).then(([catalog, location]) => {
                const current = contactDialogCurrent(seq);
                if (!current || current.contactId !== contactId || current.kind !== "location") return;
                const catalogOk = !!(catalog && Array.isArray(catalog.countries));
                const locationOk = !!(location && typeof location.configured === "boolean");
                if (!catalogOk || !locationOk) {
                    current.ready = false;
                    syncContactDialogForm(current);
                    contactDialogError(current, CONTACT_TIMING_LOAD_ERROR_TEXT);
                    const retry = contactDialogField(current, '[data-action="mc-contact-dialog-retry"]');
                    if (retry && typeof retry.removeAttribute === "function") retry.removeAttribute("hidden");
                    return;
                }
                contactTimingState().catalog = catalog;
                current.catalog = catalog;
                current.location = location;
                current.ready = true;
                renderContactDialogCountryOptions(current);
                const countrySelect = contactDialogField(current, 'select[name="countryCode"]');
                const countryCode = location.configured ? String(location.countryCode || "") : "";
                if (countrySelect) countrySelect.value = countryCode;
                renderContactDialogZoneOptions(current, countryCode, location.configured ? location.zoneId : null);
                const retry = contactDialogField(current, '[data-action="mc-contact-dialog-retry"]');
                if (retry && typeof retry.setAttribute === "function") retry.setAttribute("hidden", "");
                contactDialogError(current, "");
                syncContactDialogForm(current);
            });
        }

        function submitContactLocation(dialog) {
            if (dialog.busy || !dialog.ready) return;
            const countrySelect = contactDialogField(dialog, 'select[name="countryCode"]');
            const countryCode = countrySelect ? String(countrySelect.value || "") : "";
            if (!countryCode) {
                contactDialogError(dialog, CONTACT_TIMING_COUNTRY_REQUIRED_TEXT);
                return;
            }
            const country = findContactCountry(countryCode);
            const zones = country && Array.isArray(country.zones) ? country.zones : [];
            const zoneSelect = contactDialogField(dialog, 'select[name="zoneId"]');
            const zoneRaw = zones.length > 1 && zoneSelect ? String(zoneSelect.value || "") : "";
            const payload = { countryCode, zoneId: zoneRaw ? zoneRaw : null };
            const seq = dialog.seq;
            const contactId = dialog.contactId;
            dialog.busy = true;
            contactDialogError(dialog, "");
            syncContactDialogForm(dialog);
            hostApi()(`/api/mail/contact-locations/${contactId}`, {
                method: "PUT",
                body: JSON.stringify(payload)
            }).then(() => {
                // A 的保存可以在服务器完成，但绝不能写 B 的界面或重开 A 的弹窗（I-3）。
                const current = contactDialogCurrent(seq);
                if (!current || current.contactId !== contactId) return;
                current.busy = false;
                closeContactTimingDialog({ restoreFocus: true });
                // 只有 PUT 成功后才重新读推荐；GET 失败不得把旧时间标成新推荐（I-4）。
                forgetContactTiming();
                loadContactTiming(contactId, CONTACT_TIMING_SAVED_NO_TIMING_TEXT);
            }).catch((err) => {
                const current = contactDialogCurrent(seq);
                if (!current || current.contactId !== contactId) return;
                current.busy = false;
                syncContactDialogForm(current);
                contactDialogError(current, (err && err.message) ? String(err.message) : CONTACT_TIMING_SAVE_ERROR_TEXT);
            });
        }

        function openContactLocationDialog() {
            const contactId = Number(instance.selectedContactId);
            if (!Number.isFinite(contactId) || contactId <= 0) return;
            const dialog = beginContactDialog("location", contactId, contactLocationTrigger());
            if (!dialog) return;
            dialog.node.innerHTML = contactLocationDialogHtml(contactTimingState().prefix);
            showContactDialog(dialog);
            syncContactDialogForm(dialog);
            focusIfAvailable(contactDialogField(dialog, "select"));
            loadContactDialogData(dialog);
        }

        /** 依据只展示已成功取得的该 contact 响应，不额外查正文、不发新请求（T-2）。 */
        function openContactEvidenceDialog() {
            const contactId = Number(instance.selectedContactId);
            if (!Number.isFinite(contactId) || contactId <= 0) return;
            const state = contactTimingState();
            const data = (state.contactId === contactId && state.data) ? state.data : null;
            if (!data || !data.recommendation) return;
            const trigger = host.querySelector ? host.querySelector('[data-action="mc-contact-timing-evidence"]') : null;
            const dialog = beginContactDialog("evidence", contactId, trigger);
            if (!dialog) return;
            dialog.node.innerHTML = contactEvidenceDialogHtml(state.prefix, data);
            showContactDialog(dialog);
            focusIfAvailable(contactDialogField(dialog, ".contact-timing-close"));
        }

        function retryContactTiming() {
            const contactId = Number(instance.selectedContactId);
            if (!Number.isFinite(contactId) || contactId <= 0) return;
            loadContactTiming(contactId);
        }

        // --------------------------------------------------------------
        // 会议排期（fast-p 03 · I-1/I-3/I-4 · S-3）
        // 摘要、表单与 API 一律经宿主 adapter（app.js 唯一实现），组件不落第二份
        // 排期真值、不从邮件正文推测；摘要请求按列表/会话 epoch 固化，旧响应丢弃。
        // --------------------------------------------------------------

        function meetingSummaryEntry(contactId) {
            const id = Number(contactId);
            if (!Number.isFinite(id)) return null;
            return instance.meeting.summaryByContact.get(id) || null;
        }

        /** S-3 文案：读失败显示“排期暂不可用”，绝不用 0 场冒充（I-4）。 */
        function meetingSummaryText(contactId) {
            const entry = meetingSummaryEntry(contactId);
            if (!entry) return "";
            if (entry.state === "error") return "排期暂不可用";
            const count = Number(entry.activeCount) || 0;
            if (count <= 0) return "暂无排期";
            const short = entry.next ? beijingMeetingShort(entry.next.startUtc) : "";
            const base = `已有排期 · ${count}场`;
            return short ? `${base} · ${short}` : base;
        }

        function beijingMeetingShort(value) {
            const fn = hostFn("formatBeijingMeetingShort");
            if (!fn) return "";
            try { return String(fn(value) || ""); } catch (e) { return ""; }
        }

        function meetingSummaryActiveCount(contactId) {
            const entry = meetingSummaryEntry(contactId);
            return entry && entry.state === "ok" ? (Number(entry.activeCount) || 0) : null;
        }

        function applyMeetingSummaryToCard(person) {
            if (!person || typeof person.querySelector !== "function") return;
            const el = person.querySelector('[data-role="meeting-summary"]');
            if (!el) return;
            const id = person.dataset ? person.dataset.contactId : null;
            el.textContent = meetingSummaryText(id);
        }

        /** 只更新排期区域（列表摘要 + 头部摘要/按钮），不重建人工回复或可信工作台（I-3）。 */
        function refreshMeetingSummaryChrome() {
            const root = expertsRoot();
            if (root && typeof root.querySelectorAll === "function") {
                root.querySelectorAll(".mc-person").forEach(applyMeetingSummaryToCard);
            }
            renderHeaderMeetingSummary();
        }

        function renderHeaderMeetingSummary() {
            const body = conversationBody();
            if (!body) return;
            const contactId = Number(instance.selectedContactId);
            const identity = body.querySelector(".mc-identity");
            if (identity) {
                const el = identity.querySelector('[data-role="meeting-summary"]');
                if (el) el.textContent = meetingSummaryText(contactId);
            }
            const actions = body.querySelector(".mc-actions");
            if (!actions) return;
            const count = meetingSummaryActiveCount(contactId);
            // 0 场隐藏改期/取消，保留新增（S-3）
            const hideActions = !(count !== null && count > 0);
            ["mc-edit-schedule", "mc-cancel-schedule"].forEach((action) => {
                const button = actions.querySelector(`[data-action="${action}"]`);
                if (button) button.hidden = hideActions;
            });
        }

        function loadMeetingSummaries() {
            const ids = (instance.list.items || [])
                .map((item) => Number(item.contactId))
                .filter((id) => Number.isFinite(id) && id > 0);
            if (ids.length === 0) return Promise.resolve();
            const fn = hostFn("mcHostGetMeetingSummaries");
            if (!fn) return Promise.resolve();
            const epoch = instance.listSeq;   // I-3：捕获列表 epoch
            return Promise.resolve().then(() => fn(ids)).then((rows) => {
                if (instance.disposed || epoch !== instance.listSeq) return;
                const map = new Map(instance.meeting.summaryByContact);
                const seen = new Set();
                (Array.isArray(rows) ? rows : []).forEach((row) => {
                    const id = Number(row && row.contactId);
                    if (!ids.includes(id)) return;
                    seen.add(id);
                    map.set(id, { state: "ok", activeCount: Number(row.activeCount) || 0, next: row.next || null });
                });
                ids.forEach((id) => {
                    if (!seen.has(id) && !map.has(id)) map.set(id, { state: "ok", activeCount: 0, next: null });
                });
                instance.meeting.summaryByContact = map;
                instance.meeting.summaryEpoch = epoch;
                refreshMeetingSummaryChrome();
            }).catch(() => {
                if (instance.disposed || epoch !== instance.listSeq) return;
                const map = new Map(instance.meeting.summaryByContact);
                ids.forEach((id) => map.set(id, { state: "error" }));
                instance.meeting.summaryByContact = map;
                refreshMeetingSummaryChrome();
            });
        }

        /** 当前专家头部摘要（切专家即回读；旧响应按 convEpoch 丢弃）。 */
        function ensureMeetingSummaryFor(contactId) {
            const id = Number(contactId);
            if (!Number.isFinite(id) || id <= 0) return;
            if (instance.meeting.summaryByContact.has(id)) return;
            const fn = hostFn("mcHostGetMeetingSummaries");
            if (!fn) return;
            const epoch = instance.convEpoch;
            Promise.resolve().then(() => fn([id])).then((rows) => {
                if (instance.disposed || epoch !== instance.convEpoch) return;
                const row = (Array.isArray(rows) ? rows : []).find((item) => Number(item && item.contactId) === id);
                const map = new Map(instance.meeting.summaryByContact);
                map.set(id, row
                    ? { state: "ok", activeCount: Number(row.activeCount) || 0, next: row.next || null }
                    : { state: "error" });
                instance.meeting.summaryByContact = map;
                refreshMeetingSummaryChrome();
            }).catch(() => {
                if (instance.disposed || epoch !== instance.convEpoch) return;
                const map = new Map(instance.meeting.summaryByContact);
                map.set(id, { state: "error" });
                instance.meeting.summaryByContact = map;
                refreshMeetingSummaryChrome();
            });
        }

        /** 宿主排期变更广播：只刷新自己当前 owner（当前页摘要 + 当前专家头部）。 */
        function onMeetingScheduleChanged(event) {
            if (instance.disposed) return;
            const detail = event && event.detail ? event.detail : null;
            const changedId = detail && detail.contactId != null ? Number(detail.contactId) : null;
            const map = new Map(instance.meeting.summaryByContact);
            if (changedId != null && Number.isFinite(changedId)) map.delete(changedId);
            instance.meeting.summaryByContact = map;
            refreshMeetingSummaryChrome();
            // 当前页批量回读已覆盖本页专家；仅当所选专家不在本页时单独回读（I-3 精确额度）
            loadMeetingSummaries();
            const selectedId = Number(instance.selectedContactId);
            const inPage = (instance.list.items || []).some((item) => Number(item.contactId) === selectedId);
            if (!inPage) ensureMeetingSummaryFor(selectedId);
        }

        /** 排期写成功/发信成功 → 通知宿主失效并回读；宿主缺席时本地刷新。 */
        function notifyMeetingScheduleChanged(contactId) {
            const fn = hostFn("mcHostMeetingScheduleChanged");
            if (fn) {
                try { fn(contactId); } catch (e) { /* 宿主通知失败不阻断发送结果 */ }
                return;
            }
            onMeetingScheduleChanged({ detail: { contactId } });
        }

        function openMeetingSchedule(mode) {
            const contactId = Number(instance.selectedContactId);
            if (!Number.isFinite(contactId) || contactId <= 0) return;
            const fn = hostFn("mcHostOpenMeetingSchedule");
            if (!fn) {
                hostShowStatus("排期功能暂不可用", "error");
                return;
            }
            const label = (instance.selectedSummary
                && (instance.selectedSummary.name || instance.selectedSummary.email)) || "";
            Promise.resolve(fn(contactId, { mode, expertLabel: label })).catch((error) => {
                hostShowStatus(error && error.message ? error.message : "打开排期失败", "error");
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

        function messageDisplayHtml(text) {
            if (typeof global.renderMailBody === "function") {
                return global.renderMailBody(text, true);
            }
            return escapeText(text);
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

        // S-5（fast-p 04）：仅新 calendarAttachment 非空的 SENT OUTBOUND MAIL_RECORD，
        // 在 bodyHtml 后、原 attachmentHtml 前插已发送日历卡；元数据只消费 03 响应。
        function contextPathValue() {
            return (instance.options && instance.options.contextPath)
                ? String(instance.options.contextPath)
                : "";
        }

        function sentMeetingAttachmentHtml(message) {
            const ca = message.calendarAttachment;
            if (!ca) return "";
            const sizeKb = (Number(ca.byteLength) || 0) / 1024;
            const metaText = "日历事件 · " + sizeKb.toFixed(1) + " KB";
            const href = contextPathValue() + String(ca.downloadUrl || "");
            return `
                <div class="${mcCls("file")}" data-role="sent-meeting-attachment">
                    <span class="${mcCls("file-icon")}" aria-hidden="true">ICS</span>
                    <div class="${mcCls("file-main")}">
                        <strong><span data-role="filename">${escapeText(ca.filename || "")}</span><span class="${mcCls("badge")}" data-state="sent">已发送日历</span></strong>
                        <small data-role="file-meta">${escapeText(metaText)}</small>
                        <div class="${mcCls("file-actions")}"><a class="${mcCls("link")}" data-role="calendar-download" data-action="mc-download-sent-meeting" href="${escapeText(href)}" download>下载 ICS</a></div>
                    </div>
                </div>
            `;
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
            const bodyHtml = displayBody ? `<div class="mc-body">${messageDisplayHtml(displayBody)}</div>` : "";
            const sentMeetingHtml = sentMeetingAttachmentHtml(message);
            // fast-p 07（I-4）：已发通用附件只消费 06 的 outboundAttachments 快照。
            const sentOutboundFilesHtml = outboundSentFilesHtml(message);
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
                if (instance.processConfirm.key === key) {
                    const processBusy = instance.processConfirm.busy;
                    footerButtons.push(`<span class="mailbox-suspend-process-confirm">
                        <span>确认标记为已处理？</span>
                        <button type="button" class="mailbox-suspend-inline-secondary" data-action="mc-process-cancel" data-message-key="${escapeText(key)}"${processBusy ? " disabled" : ""}>取消</button>
                        <button type="button" class="mailbox-suspend-inline-primary" data-action="mc-process-confirm" data-message-key="${escapeText(key)}"${processBusy ? " disabled" : ""}>${processBusy ? "处理中…" : "确认"}</button>
                    </span>`);
                } else {
                    const pendingDisabled = instance.auth.ready ? "" : " disabled";
                    footerButtons.push(`<button class="mc-text-button mc-process mailbox-suspend-pending-action" type="button" data-action="mc-mark-resolved" data-message-key="${escapeText(key)}"${pendingDisabled}>待处理</button>`);
                }
            } else if (processed) {
                const resolvedBy = instance.resolvedLabels.get(key);
                footerButtons.push(`<span class="mailbox-suspend-processed-label">已处理${resolvedBy ? ` · ${escapeText(resolvedBy)}` : ""}</span>`);
            }
            const footerHtml = footerButtons.length > 0
                ? `<footer>${footerButtons.join("")}</footer>`
                : "";
            const completionHtml = instance.completion.anchorKey === key ? completionLineHtml() : "";
            return `
                <article class="mc-message" data-direction="${direction}" data-source="${escapeText(message.source)}" data-id="${escapeText(message.id)}" data-message-key="${escapeText(key)}">
                    <header><span>${who}</span>${statusBadge ? `<span>${statusBadge}</span>` : ""}</header>
                    <h3>${escapeText(subject)}</h3>
                    ${bodyHtml}
                    ${sentMeetingHtml}
                    ${sentOutboundFilesHtml}
                    ${attachmentHtml}
                    ${translationHtml}
                    ${tagRow}
                    <p class="mc-inline-error" role="alert" hidden></p>
                    ${footerHtml}
                    ${completionHtml}
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
            const banner = suspensionBannerHtml(suspensionViewForContact(instance.selectedContactId));
            return { header, banner, timelineHead, scroll };
        }

        function renderConversationContent(options) {
            const opts = options || {};
            const body = conversationBody();
            if (!body) return;
            if (instance.selectedContactId == null) {
                renderConversationEmpty();
                return;
            }
            const { header, banner, timelineHead, scroll } = conversationContentHtml();
            body.innerHTML = header + (banner || "") + timelineHead + scroll;
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
            if (!conversationVisible()) {
                instance.pendingPosition = record || { scrollTopValid: false };
            } else if (record) {
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
            if (!conversationVisible()) return null;
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
            if (!anchor || !conversationVisible()) return;
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
            if (!conversationVisible()) return;
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
            if (instance.conversation.loading || isUnmatchedChip()) return;
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
            const geometry = conversationVisible() && !instance.pendingPosition && scroll ? {
                anchorKey: anchor ? anchor.anchorKey : null,
                anchorRelTop: anchor ? anchor.anchorRelTop : 0,
                scrollTop: getScrollTop(),
                scrollTopValid: true
            } : {};
            const drafts = currentDraftsMap();
            const rec = upsertConversationRecord(instance.user, instance.conversation.accountScope || "", contactId, Object.assign({
                items: items.slice(),
                nextBefore: instance.conversation.nextBefore,
                hasMore: instance.conversation.hasMore,
                drafts: drafts || new Map()
            }, geometry));
            instance.draftsRef = rec.drafts;
        }

        function restoreFromRecord(record) {
            if (!record || !conversationVisible()) return;
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
            const contactId = Number(instance.selectedContactId);
            const myEpoch = instance.convEpoch;
            if (instance.processConfirm.key === key) {
                instance.processConfirm.busy = true;
                renderTimeline();
            }
            hostApi()(`/api/mail/unmatched-inbound/${processingId}/mark-resolved`, {
                method: "POST",
                body: JSON.stringify({ note: null })
            }).then((data) => {
                if (instance.disposed || myEpoch !== instance.convEpoch) return;
                // I-7：回包必须与目标一致，账号只取服务端 resolvedBy。
                if (!data || Number(data.id) !== processingId || data.processStatus !== "PROCESSED") {
                    throw new Error("标记结果校验失败，请重试");
                }
                const resolvedBy = data.resolvedBy == null ? null : String(data.resolvedBy);
                instance.processConfirm.key = null;
                instance.processConfirm.busy = false;
                const index = (instance.conversation.items || []).findIndex((item) => `${item.source}:${item.id}` === key);
                if (index >= 0) {
                    const updated = Object.assign({}, instance.conversation.items[index], {
                        processStatus: "PROCESSED"
                    });
                    const items = instance.conversation.items.slice();
                    items[index] = updated;
                    instance.conversation.items = items;
                }
                if (resolvedBy) instance.resolvedLabels.set(key, resolvedBy);
                // I-3：本 mount 本次处理锚点；只有随后状态 GET 归零才真正落位。
                instance.completion.pendingAnchorKey = key;
                const refreshBadge = hostFn("refreshUnmatchedBadge");
                if (refreshBadge) refreshBadge();
                renderTimeline();
                focusArticle(key);
                // 服务端顺序重查当前页；空页回退
                refreshListWithFallback();
                // I-2：收到成功才 GET 挂起状态（跨账号真值）。
                if (Number.isFinite(contactId) && contactId > 0) loadSuspensionState(contactId);
                // 新来信检查（可能最新来信变化）但不打断编辑器
                checkInboundChangeQuiet();
                saveConversationState();
            }).catch((err) => {
                if (instance.disposed || myEpoch !== instance.convEpoch) return;
                instance.processConfirm.busy = false;
                renderTimeline();
                const article = messageElByKey(key);
                if (article) setInlineError(article, err && err.message ? err.message : "标记失败，请重试");
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
            // 三态（I-1/I-2）：来信（真实 processingId）→ inbound；无来信但至少 1 封真实
            // SENT 出站 → outbound（自由回信锚点资格由服务端再校验）；其余 → unavailable。
            const hasInbound = latestInbound && latestInbound.processingId != null;
            const sentCount = Number(summary.sentCount) || 0;
            const mode = hasInbound ? "inbound" : (sentCount > 0 ? "outbound" : "unavailable");
            const targetProcessingId = hasInbound ? Number(latestInbound.processingId) : null;
            const targetAccount = hasInbound ? (latestInbound.accountCode || "") : "";
            // outbound 草稿 key 固定为 contactId:OUTBOUND:<accountScope>，不依赖可能变化的
            // latest message id（草稿缓存恢复语义）。
            const scope = instance.conversation.accountScope || "";
            const targetKey = hasInbound
                ? `${Number(instance.selectedContactId)}:${targetProcessingId}:${targetAccount}`
                : (mode === "outbound" ? `${Number(instance.selectedContactId)}:OUTBOUND:${scope}` : null);
            const targetMsg = hasInbound
                ? latestInboundMessage()
                : (mode === "outbound" ? (summary.latestMessage || null) : null);
            // I-1：默认主题只在最新消息确为真实 SENT 出站时由其生成 Re:；失败消息不伪装成锚点。
            const defaultSubject = targetMsg && hasInbound
                ? chatSubjectPrefill(targetMsg.subject)
                : (mode === "outbound" && targetMsg && targetMsg.direction === "OUTBOUND" && targetMsg.sendStatus === "SENT"
                    ? chatSubjectPrefill(targetMsg.subject)
                    : "Re:");
            const draft = targetKey != null ? getDraft(targetKey) : null;

            instance.manual.mode = mode;
            instance.manual.targetProcessingId = targetProcessingId;
            instance.manual.targetAccountCode = targetAccount;
            instance.manual.targetKey = targetKey;
            instance.manual.qa = draft && draft.qa ? draft.qa : null;
            instance.manual.busy = false;

            let manualContent;
            if (mode === "inbound") {
                manualContent = manualComposeHtml(targetKey, targetProcessingId, targetAccount, draft, defaultSubject, false);
            } else if (mode === "outbound") {
                manualContent = manualComposeHtml(targetKey, null, scope, draft, defaultSubject, true);
            } else {
                manualContent = manualFollowUpHtml();
            }
            scroll.insertAdjacentHTML("beforeend", `
                <details class="mc-section" data-section="manual" open>
                    <summary>人工回复</summary>
                    <div class="mc-section-content">${manualContent}</div>
                </details>
            `);
            // 会议卡（组件缺席时无容器，refresh 为空操作）
            refreshMeetingAttachmentCard();
            // 通用附件卡：渲染后按草稿字段重建（含上传中/失败态）并刷新发送可用性
            refreshOutboundFilesCard();
        }

        function manualTargetInfoText(processingId, account) {
            const parts = [];
            if (account) parts.push(escapeText(account));
            if (processingId != null) parts.push(`目标来信 #${processingId}`);
            return parts.join(" · ");
        }

        // 人工回复正文恢复（T3）：组件在场且草稿含 html 时经 04 sanitizer 恢复富文本
        // （只接受本页捕获内容；script/样式/事件全部剥除）；组件缺席/无 html 时恢复 text。
        function meetingRestoreEditorHtml(html) {
            const lib = meetingLib();
            if (!lib || typeof lib.sanitizeDraftHtml !== "function") return "";
            const doc = docRoot();
            if (!doc) return "";
            try {
                return lib.sanitizeDraftHtml(html, doc);
            } catch (e) {
                return "";
            }
        }

        // S-3：触发按钮与附件卡容器。会议 class 运行时拼接（见模块注释）。
        function meetingTriggerHtml() {
            return `<button class="${mcCls("trigger")} button" type="button" data-action="mc-open-meeting">` +
                `<span class="${mcCls("icon")}" aria-hidden="true">▦</span>会议确认</button>`;
        }

        // S-2：材料索取入口复用 `.button`，固定位于会议按钮之后、跟进按钮之前。
        function materialRequestTriggerHtml() {
            return `<button class="button material-request-trigger" type="button" data-action="mc-open-material-request">材料索取</button>`;
        }

        // I-2：草稿卡时间改由 preview 的真实 UTC 值经统一中文北京 formatter 显示
        // （宿主 formatBeijingMeetingRange）；不再回显 input.zoneId 的 IANA 串或英文日期。
        function meetingCardMetaText(meeting) {
            return meetingCardMetaTextFor(
                meeting && meeting.preview ? meeting.preview : null,
                hostFn("formatBeijingMeetingRange")
            );
        }

        function meetingAttachmentFilename(meeting) {
            return (meeting && meeting.preview && meeting.preview.attachment && meeting.preview.attachment.filename)
                ? String(meeting.preview.attachment.filename)
                : "";
        }

        function meetingCardInnerHtml(meeting) {
            if (!meeting) return "";
            const stale = meeting.state === "stale";
            const stateAttr = stale ? "stale" : "ready";
            const badgeText = stale ? "待重新确认" : "待发送附件";
            const note = stale
                ? "会议正文或回复目标已变化，请编辑会议重新生成，或移除日历附件。"
                : "修改会议时间或链接请使用「编辑会议」，同步更新正文和附件。";
            return `
                <div class="${mcCls("file")}">
                    <span class="${mcCls("file-icon")}" aria-hidden="true">ICS</span>
                    <div class="${mcCls("file-main")}">
                        <strong><span data-role="filename"></span><span class="${mcCls("badge")}" data-state="${stateAttr}">${badgeText}</span></strong>
                        <small data-role="file-meta"></small>
                        <div class="${mcCls("file-actions")}">
                            <a class="${mcCls("link")}" data-action="mc-download-meeting">下载</a>
                            <button class="${mcCls("link")}" type="button" data-action="mc-edit-meeting">编辑会议</button>
                            <button class="${mcCls("link")}" type="button" data-action="mc-remove-meeting" aria-label="移除日历附件">移除</button>
                        </div>
                    </div>
                </div>
                <p class="${mcCls("draft-note")}">${escapeText(note)}</p>
            `;
        }

        function meetingCardContainerHtml(meeting) {
            const stateAttr = meeting && meeting.state === "stale" ? "stale" : "ready";
            const inner = meeting ? meetingCardInnerHtml(meeting) : "";
            return `<div class="${mcCls("attachment")}" data-role="meeting-attachment" data-state="${stateAttr}">${inner}</div>`;
        }

        function manualComposeHtml(targetKey, processingId, account, draft, defaultSubject, outbound) {
            const isOutbound = outbound === true;
            const subjectValue = draft ? draft.subject : defaultSubject;
            const editorText = draft ? draft.text : "";
            const targetInfo = isOutbound
                ? `${manualTargetInfoText(null, account)} · 回复最近成功发件线程`
                : manualTargetInfoText(processingId, account);
            const templateFollowButton = isOutbound
                ? `<button class="button" type="button" data-action="mc-template-follow" data-contact-id="${escapeText(instance.selectedContactId)}">选择模板发送跟进邮件</button>`
                : "";
            // 会议确认只用于真实来信目标（inbound）：组件在场且非 outbound 才渲染会议 UI。
            const ui = meetingEnabled() && !isOutbound;
            let editorContent = "";
            if (ui && draft && draft.html && String(draft.html).trim()) {
                editorContent = meetingRestoreEditorHtml(String(draft.html));
                if (!editorContent && editorText) editorContent = escapeText(editorText);
            } else if (editorText) {
                editorContent = escapeText(editorText);
            }
            const meetingTrigger = ui ? meetingTriggerHtml() : "";
            // S-2：材料索取紧随会议按钮之后，仍在跟进按钮之前。
            const materialTrigger = ui ? materialRequestTriggerHtml() : "";
            // S-1（fast-p 01 I-1）：引用模板入口只属于有真实来信的人工回复（outbound=false），
            // 不依赖会议组件；固定在材料索取之后、跟进按钮之前。
            const templateReferenceTrigger = isOutbound
                ? ""
                : `<button class="button reply-template-trigger" type="button" data-action="mc-open-template-reference">引用模板</button>`;
            // S-1：跟进按钮只在当前专家确有成功发件时渲染，固定紧随既有会议按钮之后，
            // class 严格为 button（不新增按钮 class、不改会议按钮顺序）。
            const followUpButton = Number(instance.selectedSummary && instance.selectedSummary.sentCount) > 0
                ? `<button class="button" type="button" data-action="mc-open-followup">↗ 跟进邮件</button>`
                : "";
            // S-3：草稿携带跟进锚点时，在 .mc-editor 之后、会议附件之前显示所选邮件提示。
            const anchorNote = draft && draft.followUpAnchorMailRecordId != null
                ? followupAnchorNoteHtml(draft.followUpAnchorMailRecordId)
                : "";
            const meetingAttachment = ui && meetingCardContainerHtml(draft && draft.meeting ? draft.meeting : null) || "";
            // fast-p 07（S-2）：通用附件草稿卡放在会议附件卡之后、发送 footer 之前。
            const outboundFiles = outboundDraftFilesHtml(draft ? outboundAttachmentDraftOf(draft).items : []);
            return `
                <div class="mc-compose" data-role="manual-compose" data-target-key="${escapeText(targetKey)}">
                    <label>主题<input aria-label="回复主题" value="${escapeText(subjectValue)}"></label>
                    <div class="mc-editor-tools">
                        <button class="button" type="button" data-action="mc-rich-command" data-command="bold">B</button>
                        <button class="button" type="button" data-action="mc-rich-command" data-command="italic">I</button>
                        <button class="button" type="button" data-action="mc-rich-command" data-command="insertUnorderedList">列表</button>
                        <button class="button" type="button" data-action="mc-rich-command" data-command="createLink">链接</button>
                        <button class="button outbound-upload" type="button" data-action="mc-upload-attachment" title="上传附件" aria-label="上传附件"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false"><path d="m21.44 11.05-9.19 9.19a6 6 0 0 1-8.49-8.49l10.6-10.6a4 4 0 0 1 5.66 5.66L9.41 17.41a2 2 0 0 1-2.83-2.83l9.19-9.19"/></svg></button>
                        <input type="file" data-role="outbound-file-input" multiple hidden>
                        ${meetingTrigger}${materialTrigger}${templateReferenceTrigger}${followUpButton}
                    </div>
                    <div class="mc-editor" contenteditable="true" role="textbox" aria-multiline="true" aria-label="人工回复正文" data-role="mc-editor">${editorContent}</div>
                    ${anchorNote}
                    ${meetingAttachment}
                    ${outboundFiles}
                    <div class="mc-compose-footer">
                        <span data-role="target-info">回复账号与目标来信信息：${targetInfo}</span>
                        ${templateFollowButton}
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
        // 02（S-2/I-1/I-2/I-3/I-5）：卡片三态状态菜单与真实保存。
        // 只请求新状态端点；不做乐观提前成功，不按状态在前端伪造分页。
        // --------------------------------------------------------------

        // 当前列表请求上下文指纹：仅当刷新期间上下文未变才把 list.error 归因本次动作。
        function listContextToken() {
            const filters = instance.filters || {};
            const keys = Object.keys(filters).sort();
            return [instance.user, instance.chip, instance.searchText.trim(), instance.list.page,
                keys.map((key) => `${key}=${filters[key]}`).join("&")].join("|");
        }

        function setProgressBusy(contactId, busy) {
            const key = String(contactId);
            if (busy) instance.progressBusy.add(key);
            else instance.progressBusy.delete(key);
        }

        function progressStatusForCard(contactId) {
            const item = findSummaryByContactId(contactId);
            if (item) return progressStatusOf(item);
            const summary = instance.selectedSummary;
            if (summary && String(summary.contactId) === String(contactId)) return progressStatusOf(summary);
            return PROGRESS_NONE;
        }

        function openProgressMenu(contactId, trigger) {
            closeProgressMenu({ restoreFocus: false });
            if (!trigger || !trigger.parentNode) return;
            const menu = trigger.parentNode.querySelector
                ? trigger.parentNode.querySelector(".mailbox-progress-menu")
                : null;
            if (!menu) return;
            const options = Array.from(menu.querySelectorAll ? menu.querySelectorAll(".mailbox-progress-option") : [])
                .filter((option) => !option.disabled);
            menu.hidden = false;
            if (typeof trigger.setAttribute === "function") trigger.setAttribute("aria-expanded", "true");
            instance.progressMenu = { contactId: String(contactId), el: menu, trigger, options, index: options.length ? 0 : -1 };
            focusProgressOption(0);
        }

        function focusProgressOption(index) {
            const menu = instance.progressMenu;
            if (!menu || !menu.options.length) return;
            const normalized = ((index % menu.options.length) + menu.options.length) % menu.options.length;
            menu.index = normalized;
            const option = menu.options[normalized];
            if (option.scrollIntoView) {
                try { option.scrollIntoView({ block: "nearest" }); } catch (e) { /* noop */ }
            }
            if (typeof option.focus === "function") option.focus();
        }

        function closeProgressMenu(options) {
            const menu = instance.progressMenu;
            const trigger = menu && menu.trigger;
            if (menu && menu.el) menu.el.hidden = true;
            if (trigger && typeof trigger.setAttribute === "function") trigger.setAttribute("aria-expanded", "false");
            const restore = options && options.restoreFocus;
            instance.progressMenu = { contactId: null, el: null, trigger: null, options: [], index: -1 };
            if (restore && trigger && trigger.isConnected !== false && typeof trigger.focus === "function") {
                trigger.focus();
            }
        }

        function toggleProgressMenu(contactId, trigger) {
            if (instance.progressMenu.contactId != null
                && String(instance.progressMenu.contactId) === String(contactId)) {
                closeProgressMenu({ restoreFocus: true });
                return;
            }
            openProgressMenu(contactId, trigger);
        }

        function progressSavedMessage(status) {
            if (status === PROGRESS_FOLLOWING) return "已标记为跟进中";
            if (status === PROGRESS_PROVIDED) return "已标记为已提供";
            return "已取消标记";
        }

        function updateProgressLocal(contactId, status) {
            const followed = progressFollowed(status);
            const item = findSummaryByContactId(contactId);
            if (item) {
                item.progressStatus = status;
                item.followed = followed;
            }
            const summary = instance.selectedSummary;
            if (summary && String(summary.contactId) === String(contactId)) {
                summary.progressStatus = status;
                summary.followed = followed;
            }
        }

        function setProgress(contactId, status) {
            const id = Number(contactId);
            if (!Number.isFinite(id) || id <= 0) return;
            const next = String(status || "");
            if (!isProgressStatus(next)) return;
            if (instance.progressBusy.has(String(id))) return;
            if (progressStatusForCard(id) === next) {
                closeProgressMenu({ restoreFocus: false });
                return;
            }
            closeProgressMenu({ restoreFocus: false });
            freezeDefaultProbe();
            setProgressBusy(id, true);
            renderList();
            const contextToken = listContextToken();
            hostApi()(`/api/mail/mailbox/conversations/${id}/progress-status`, {
                method: "PUT",
                body: JSON.stringify({ status: next })
            }).then((data) => {
                if (instance.disposed) return;
                const resultStatus = (data && typeof data === "object" && isProgressStatus(data.progressStatus))
                    ? data.progressStatus : next;
                updateProgressLocal(id, resultStatus);
                // 02（I-4）：同专家挂起读上下文失效并重查真值；不改草稿/锚点/处理确认。
                if (suspensionMatchesContact(id)) {
                    instance.suspension.seq += 1;
                    instance.suspension.progressStatus = resultStatus;
                    instance.suspension.followed = progressFollowed(resultStatus);
                    loadSuspensionState(id);
                }
                return refreshListWithFallback().then(() => {
                    if (instance.disposed) return;
                    if (instance.list.error && !instance.list.loading && contextToken === listContextToken()) {
                        hostShowStatus("状态已保存，列表刷新失败，请重试", "error");
                    } else if (!instance.list.error) {
                        hostShowStatus(progressSavedMessage(resultStatus), "ok");
                    }
                });
            }).catch((err) => {
                if (instance.disposed) return;
                hostShowStatus((err && err.message) ? `状态保存失败：${err.message}` : "状态保存失败，请重试", "error");
            }).then(() => {
                if (instance.disposed) return;
                setProgressBusy(id, false);
                renderList();
            });
        }

        function dismissReplied(contactId, button) {
            if (instance.chip !== CHIP_REPLIED || !button || button.disabled) return;
            const id = Number(contactId);
            if (!Number.isSafeInteger(id) || id <= 0) return;
            button.disabled = true;
            hostApi()(`/api/mail/mailbox/conversations/${id}/replied-dismissal`, { method: "PUT" }).then(() => {
                if (instance.disposed) return;
                hostShowStatus("已移出已回复，仍可在全部查看", "ok");
                refreshListWithFallback();
            }).catch((err) => {
                if (instance.disposed) return;
                button.disabled = false;
                hostShowStatus((err && err.message) ? `移出已回复失败：${err.message}` : "移出已回复失败", "error");
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
                // 唯一 portal 只承载一个 overlay：管理面板打开前关闭跟进弹窗与材料索取（不触碰草稿状态）。
                closeFollowUpDialog({ restoreFocus: false });
                closeMaterialRequestDialog({ restoreFocus: false });
                closeTemplateReferenceDialog({ restoreFocus: false });
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

        // ------------------------------------------------------------------
        // fast-p 07（I-1..I-5 / S-1/S-2）：人工回复通用附件。
        //
        // 唯一真值是草稿字段 outboundAttachmentDraft={revision,items}：item 以本地 key
        // 标识（单调序号，同草稿内永不重用），状态 uploading/ready/failed；原始 File 只在
        // 上传期间临时持有，落定后换成只含服务端 metadata 的新对象。仅 ready 条目按选择
        // 顺序作为 attachmentIds 提交；任一 uploading/failed 存在即禁止发送。
        // ------------------------------------------------------------------

        const OUTBOUND_STATE_UPLOADING = "uploading";
        const OUTBOUND_STATE_READY = "ready";
        const OUTBOUND_STATE_FAILED = "failed";
        const OUTBOUND_STATE_SENT = "sent";
        /** 与 04 `OutboundAttachmentModels` 同值：只作客户端预检查，服务端仍最终裁决。 */
        const OUTBOUND_MAX_FILE_BYTES = 10 * 1024 * 1024;
        const OUTBOUND_MAX_TOTAL_BYTES = 20 * 1024 * 1024;
        const OUTBOUND_MAX_FILES = 10;
        const OUTBOUND_TEXT_UPLOADING = "上传中…";
        const OUTBOUND_TEXT_FAILED = "上传失败，请重新选择文件";
        const OUTBOUND_TEXT_PENDING = "待发送";
        const OUTBOUND_TEXT_SENT = "已发送";

        let outboundFileSeq = 0;

        function nextOutboundFileKey() {
            outboundFileSeq += 1;
            return "of-" + outboundFileSeq;
        }

        function emptyOutboundAttachmentDraft() {
            return { revision: 0, items: [] };
        }

        /**
         * I-2：读草稿的附件字段。缺失/损坏视为空草稿（不抛、不另立真值）；items 为浅拷贝，
         * 增删后必须经 setOutboundAttachmentItems 写回。
         */
        function outboundAttachmentDraftOf(draft) {
            const field = draft ? draft.outboundAttachmentDraft : null;
            const items = field && Array.isArray(field.items) ? field.items : [];
            return {
                revision: field && Number.isFinite(Number(field.revision)) ? Number(field.revision) : 0,
                items: items.slice()
            };
        }

        /** I-3：ready 条目按选择顺序提交；空数组 = 旧接口形态（payload 省略 attachmentIds）。 */
        function outboundAttachmentIds(draft) {
            return outboundAttachmentDraftOf(draft).items
                .filter((item) => item && item.state === OUTBOUND_STATE_READY && item.id != null && String(item.id) !== "")
                .map((item) => String(item.id));
        }

        function formatOutboundFileSize(byteLength) {
            return ((Number(byteLength) || 0) / 1024).toFixed(1) + " KB";
        }

        function outboundFileMetaText(item) {
            const state = item && item.state ? String(item.state) : OUTBOUND_STATE_READY;
            if (state === OUTBOUND_STATE_UPLOADING) return OUTBOUND_TEXT_UPLOADING;
            if (state === OUTBOUND_STATE_FAILED) return OUTBOUND_TEXT_FAILED;
            const size = formatOutboundFileSize(item && item.byteLength);
            return size + " · " + (state === OUTBOUND_STATE_SENT ? OUTBOUND_TEXT_SENT : OUTBOUND_TEXT_PENDING);
        }

        /**
         * S-2 文件卡（草稿/已发同构骨架）：名称与 meta 只经 escapeText；下载锚点用该条自己的
         * downloadUrl + 宿主 contextPath（I-4），不借 Blob 重新合成原件；sent 卡不渲染移除。
         */
        function outboundFileCardHtml(item) {
            const state = item && item.state ? String(item.state) : OUTBOUND_STATE_READY;
            const downloadable = state === OUTBOUND_STATE_READY || state === OUTBOUND_STATE_SENT;
            const href = downloadable ? contextPathValue() + String(item && item.downloadUrl ? item.downloadUrl : "") : "";
            const download = downloadable
                ? `<a class="outbound-file-link" data-role="outbound-download" href="${escapeText(href)}" download>下载</a>`
                : "";
            const remove = state === OUTBOUND_STATE_SENT
                ? ""
                : `<button class="outbound-file-link" type="button" data-action="mc-remove-attachment" aria-label="移除附件">移除</button>`;
            const name = item && item.filename != null ? String(item.filename) : "";
            const key = item && item.key != null ? String(item.key) : "";
            return `
                      <div class="outbound-file" data-state="${escapeText(state)}" data-file-key="${escapeText(key)}">
                        <span class="outbound-file-icon" aria-hidden="true">↧</span>
                        <div class="outbound-file-main"><strong class="outbound-file-name">${escapeText(name)}</strong><small class="outbound-file-meta">${escapeText(outboundFileMetaText(item))}</small></div>
                        <div class="outbound-file-actions">${download}${remove}</div>
                      </div>`;
        }

        /** 草稿卡容器：无文件时 hidden，且不渲染空卡。 */
        function outboundDraftFilesHtml(items) {
            const list = Array.isArray(items) ? items.filter(Boolean) : [];
            const hidden = list.length === 0 ? " hidden" : "";
            return `<div class="outbound-files" data-role="outbound-draft-files" aria-live="polite"${hidden}>`
                + list.map(outboundFileCardHtml).join("")
                + `</div>`;
        }

        /**
         * I-4：已发卡只消费 06 的 outboundAttachments 快照，绝不从当前草稿或正文拼文件；无
         * 附件时整块不渲染（不出现空容器/假下载文本）。
         */
        function outboundSentFilesHtml(message) {
            const list = Array.isArray(message && message.outboundAttachments) ? message.outboundAttachments : [];
            if (list.length === 0) return "";
            const cards = list.map((item) => outboundFileCardHtml({
                key: item ? item.id : "",
                state: OUTBOUND_STATE_SENT,
                filename: item ? item.filename : "",
                byteLength: item ? item.byteLength : 0,
                downloadUrl: item ? item.downloadUrl : ""
            })).join("");
            return `<div class="outbound-files" data-role="outbound-sent-files" data-state="sent">${cards}</div>`;
        }

        // ---- 07：草稿写回、捕获与上传队列（I-1..I-3） ----

        function outboundFileInputEl() {
            const composeEl = manualComposeEl();
            return composeEl && composeEl.querySelector ? composeEl.querySelector('[data-role="outbound-file-input"]') : null;
        }

        function outboundDraftFilesContainerEl() {
            const composeEl = manualComposeEl();
            return composeEl && composeEl.querySelector ? composeEl.querySelector('[data-role="outbound-draft-files"]') : null;
        }

        function currentOutboundAttachmentItems() {
            const key = currentTargetKey();
            const draft = key ? getDraft(key) : null;
            return draft ? outboundAttachmentDraftOf(draft).items : [];
        }

        function outboundItemsIn(captured) {
            const draft = captured && captured.draftsMap ? captured.draftsMap.get(captured.targetKey) : null;
            return draft ? outboundAttachmentDraftOf(draft).items : [];
        }

        function outboundBytesIn(captured) {
            return outboundItemsIn(captured)
                .filter((item) => item && item.state !== OUTBOUND_STATE_FAILED)
                .reduce((sum, item) => sum + (Number(item.byteLength) || 0), 0);
        }

        /**
         * I-2 写回：只落到捕获的 owner Map + targetKey。草稿已被删除/已迁到新目标时返回
         * false —— 迟到的上传回包绝不重建已消失的 key（不复活、不串目标）。
         */
        function setOutboundAttachmentItems(captured, items) {
            if (!captured || !captured.draftsMap) return false;
            const existing = captured.draftsMap.get(captured.targetKey);
            if (!existing) return false;
            const current = outboundAttachmentDraftOf(existing);
            captured.draftsMap.set(captured.targetKey, Object.assign({}, existing, {
                outboundAttachmentDraft: { revision: current.revision + 1, items: items.slice() },
                updatedAt: new Date().toISOString()
            }));
            return true;
        }

        /** I-3：附件语义变化（选择/移除/替换）使会话 requestId 失效，与正文修改同款。 */
        function invalidateOutboundRequestId(captured) {
            if (!captured || !captured.draftsMap) return;
            const existing = captured.draftsMap.get(captured.targetKey);
            if (!existing || !existing.requestId) return;
            captured.draftsMap.set(captured.targetKey, Object.assign({}, existing, {
                requestId: null,
                updatedAt: new Date().toISOString()
            }));
        }

        /**
         * I-2：异步发起前捕获 sessionUser/accountScope/contactId/targetKey 与原 draftsMap。
         * 迟到回包只写这份捕获：切专家/切账号后它不再指向任何 live 草稿，故不会污染后来
         * 切换的专家，LRU 淘汰后也不会有任何 live 落点。
         */
        function captureOutboundOwner(targetKey) {
            const contactId = Number(instance.selectedContactId);
            if (!targetKey || !Number.isFinite(contactId) || contactId <= 0) return null;
            if (!getDraft(targetKey)) {
                saveDraftFromInputs();
                if (!getDraft(targetKey)) return null;
            }
            const draftsMap = ensureDraftsMap();
            if (!draftsMap || !draftsMap.get(targetKey)) return null;
            const accountScope = instance.conversation.accountScope || "";
            return {
                targetKey,
                contactId,
                accountScope,
                ownerKey: conversationCacheKey(instance.user, accountScope, contactId),
                draftsMap
            };
        }

        /** 捕获是否仍是当前 owner —— 只决定要不要动 DOM/提示，不决定要不要写草稿。 */
        function outboundOwnerIsCurrent(captured) {
            if (!captured || instance.disposed) return false;
            const contactId = Number(instance.selectedContactId);
            const scope = instance.conversation.accountScope || "";
            if (captured.ownerKey !== conversationCacheKey(instance.user, scope, contactId)) return false;
            return captured.draftsMap === currentDraftsMap();
        }

        function outboundAttachmentsBlockSend() {
            return currentOutboundAttachmentItems().some((item) => !item || item.state !== OUTBOUND_STATE_READY);
        }

        function refreshSendAvailability() {
            setSendButtonDisabled(!!instance.manual.busy || outboundAttachmentsBlockSend());
        }

        /** 附件卡重渲染：只消费当前目标的草稿字段；发送可用性随之刷新。 */
        function refreshOutboundFilesCard() {
            const container = outboundDraftFilesContainerEl();
            if (!container) return;
            const items = currentOutboundAttachmentItems();
            container.innerHTML = items.map(outboundFileCardHtml).join("");
            if (items.length === 0) container.setAttribute("hidden", "");
            else container.removeAttribute("hidden");
            refreshSendAvailability();
        }

        /** S-1：唯一图标入口只打开同一个隐藏 input（无 accept、multiple 不过滤类型）。 */
        function openOutboundFilePicker() {
            if (!currentTargetKey()) return;
            const input = outboundFileInputEl();
            if (!input || typeof input.click !== "function") return;
            input.click();
        }

        /** I-1：选择文件只入草稿队列；取消系统选择器 = 零请求、零草稿变化。 */
        function handleOutboundFileSelection(input) {
            const key = currentTargetKey();
            if (!key || !input) return;
            const picked = input.files && typeof input.files.length === "number"
                ? Array.prototype.slice.call(input.files)
                : [];
            // 立即释放选择器引用（同文件可移除后重选）；原件只在上传期间临时持有。
            input.value = "";
            if (picked.length === 0) return;
            const captured = captureOutboundOwner(key);
            if (!captured) return;
            if (outboundItemsIn(captured).length + picked.length > OUTBOUND_MAX_FILES) {
                hostShowStatus("最多 " + OUTBOUND_MAX_FILES + " 个通用附件", "error");
                return;
            }
            // 顺序队列：一次只上传一个文件，不同时持有多个原件。
            let chain = Promise.resolve();
            picked.map((file) => () => uploadOutboundFile(captured, file)).forEach((run) => {
                chain = chain.then(run, run);
            });
        }

        /** 单个文件：预检查 → multipart POST → 落定为 ready 或 failed。 */
        function uploadOutboundFile(captured, file) {
            const name = file && file.name != null ? String(file.name) : "";
            const size = Number(file && file.size) || 0;
            const item = {
                key: nextOutboundFileKey(),
                state: OUTBOUND_STATE_UPLOADING,
                filename: name,
                byteLength: size,
                file
            };
            const items = outboundItemsIn(captured);
            items.push(item);
            if (!setOutboundAttachmentItems(captured, items)) return Promise.resolve();
            invalidateOutboundRequestId(captured);
            if (outboundOwnerIsCurrent(captured)) refreshOutboundFilesCard();
            // 预检查（服务端仍最终裁决）：先落一张明确的失败卡，绝不静默丢掉这次选择。
            const tooLarge = size > OUTBOUND_MAX_FILE_BYTES;
            const overBudget = !tooLarge && outboundBytesIn(captured) > OUTBOUND_MAX_TOTAL_BYTES;
            if (tooLarge || overBudget) {
                failOutboundItem(captured, item.key, tooLarge
                    ? "单个附件不能超过 " + (OUTBOUND_MAX_FILE_BYTES / (1024 * 1024)) + "MiB"
                    : "通用附件总计不能超过 " + (OUTBOUND_MAX_TOTAL_BYTES / (1024 * 1024)) + "MiB");
                return Promise.resolve();
            }
            const form = typeof FormData === "function" ? new FormData() : null;
            if (!form) {
                failOutboundItem(captured, item.key, "当前浏览器不支持附件上传");
                return Promise.resolve();
            }
            form.append("file", file, name);
            return hostApi()(`/api/mail/conversations/${captured.contactId}/outbound-attachments`, {
                method: "POST",
                // headers 整体覆盖宿主的 JSON 默认值，让浏览器自己写 multipart 边界。
                headers: {},
                body: form
            }).then((uploaded) => {
                const id = uploaded && uploaded.id != null ? String(uploaded.id) : "";
                if (id === "") {
                    failOutboundItem(captured, item.key, OUTBOUND_TEXT_FAILED);
                    return;
                }
                applyOutboundItemChange(captured, item.key, () => ({
                    key: item.key,
                    state: OUTBOUND_STATE_READY,
                    filename: uploaded.filename != null ? String(uploaded.filename) : name,
                    contentType: uploaded.contentType != null ? String(uploaded.contentType) : "",
                    byteLength: Number(uploaded.byteLength) || size,
                    sha256: uploaded.sha256 != null ? String(uploaded.sha256) : "",
                    id,
                    downloadUrl: uploaded.downloadUrl != null ? String(uploaded.downloadUrl) : ""
                }));
            }).catch((err) => {
                failOutboundItem(captured, item.key, err && err.message ? String(err.message) : OUTBOUND_TEXT_FAILED);
            });
        }

        /**
         * 单条落定：整项换成新对象（ready 项不含 File，原件随落定释放）；key 已不在草稿里
         * （被移除/草稿被淘汰）时返回 false —— 迟到回包绝不重新插回。
         */
        function applyOutboundItemChange(captured, fileKey, build) {
            const items = outboundItemsIn(captured);
            const index = items.findIndex((item) => item && String(item.key) === String(fileKey));
            if (index === -1) return false;
            items[index] = build(items[index]);
            if (!setOutboundAttachmentItems(captured, items)) return false;
            if (outboundOwnerIsCurrent(captured)) refreshOutboundFilesCard();
            return true;
        }

        function failOutboundItem(captured, fileKey, message) {
            const text = message || OUTBOUND_TEXT_FAILED;
            const changed = applyOutboundItemChange(captured, fileKey, (item) => ({
                key: item.key,
                state: OUTBOUND_STATE_FAILED,
                filename: item.filename,
                byteLength: 0,
                error: text
            }));
            if (changed && outboundOwnerIsCurrent(captured)) hostShowStatus(text, "error");
            return changed;
        }

        /** I-2：移除只删本地 key（不发任何服务器删除请求）；迟到回包因 key 消失而作废。 */
        function removeOutboundAttachment(fileKey) {
            const key = currentTargetKey();
            if (!key || !fileKey) return;
            const items = currentOutboundAttachmentItems();
            const index = items.findIndex((item) => item && String(item.key) === String(fileKey));
            if (index === -1) return;
            const captured = captureOutboundOwner(key);
            if (!captured) return;
            items.splice(index, 1);
            if (!setOutboundAttachmentItems(captured, items)) return;
            invalidateOutboundRequestId(captured);
            refreshOutboundFilesCard();
        }

        /**
         * I-3：发送前捕获的草稿语义快照（主题/正文/有序附件 id）。成功回包只在草稿仍等于
         * 该快照时清除捕获 owner 的那份草稿 —— 发送期间的新编辑/新附件一律保留。
         */
        function outboundManualDraftSnapshot(draft) {
            return {
                subject: draft && draft.subject != null ? String(draft.subject) : "",
                html: draft && draft.html != null ? String(draft.html) : "",
                text: draft && draft.text != null ? String(draft.text) : "",
                attachmentIds: outboundAttachmentIds(draft).join("\u0000")
            };
        }

        function outboundDraftMatchesSnapshot(draft, snapshot) {
            if (!draft || !snapshot) return false;
            const now = outboundManualDraftSnapshot(draft);
            return now.subject === snapshot.subject
                && now.html === snapshot.html
                && now.text === snapshot.text
                && now.attachmentIds === snapshot.attachmentIds;
        }

        // --------------------------------------------------------------
        // 人工回复：草稿 / 采用 / 发送（I-7 保持原业务；T4 增加 outbound 会话回信）
        // --------------------------------------------------------------

        function currentTargetKey() {
            const mode = instance.manual.mode;
            return (mode === "inbound" || mode === "outbound") && instance.manual.targetKey
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
                // 草稿持久化 canonical 正文（仅换行收敛，标签与文本逐字保留）。
                html: normalizeManualRichHtmlLineBreaks(
                    typeof inputs.editor.innerHTML === "string" ? inputs.editor.innerHTML : ""
                ),
                text: normalizeManualTextLineBreaks(
                    typeof inputs.editor.innerText === "string" ? inputs.editor.innerText : String(inputs.editor.textContent || "")
                ),
                qa: instance.manual.qa ? snapshotQa(instance.manual.qa) : null
            };
        }

        function saveDraftFromInputs(extra) {
            const key = currentTargetKey();
            if (!key) return;
            const values = readManualValues();
            if (!values) return;
            const existing = getDraft(key);
            const patch = extra || {};
            // 跟进锚点（I-4）：patch 显式给值才改（null = 清除，如采用可信草稿/应用会议），
            // 其余保存沿用既有草稿值。
            const hasAnchorPatch = Object.prototype.hasOwnProperty.call(patch, "followUpAnchorMailRecordId");
            const anchor = hasAnchorPatch
                ? (patch.followUpAnchorMailRecordId == null ? null : Number(patch.followUpAnchorMailRecordId))
                : (existing && existing.followUpAnchorMailRecordId != null
                    ? Number(existing.followUpAnchorMailRecordId)
                    : null);
            // I-4/I-12：主题、正文或锚点相对上次保存有任何变化 → 旧 requestId 失效（置 null，
            // 下次发送生成新值）；逐字未变化（重挂载/程序性重存）保留，保证失败重试仍
            // 收敛到同一 attempt。成功删除草稿时 requestId 一并删除。
            const contentChanged = !existing
                || existing.subject !== values.subject
                || existing.html !== values.html
                || existing.text !== values.text
                || (existing.followUpAnchorMailRecordId != null
                    ? Number(existing.followUpAnchorMailRecordId)
                    : null) !== anchor;
            const requestId = contentChanged ? null : (existing.requestId || null);
            // 会议快照（T3/S-3）：输入保存时随草稿持久化 —— 无显式 meeting patch 则沿用
            // 既有草稿快照（不清除）；切专家/换 accountScope/target 时 targetKey 隔离，
            // 不会跨目标串会议数据；会议块被手改时调用方经 patch 把 state 置 stale。
            const hasMeetingPatch = Object.prototype.hasOwnProperty.call(patch, "meeting");
            const meeting = hasMeetingPatch
                ? deepCopyMeeting(patch.meeting)
                : (existing && existing.meeting ? deepCopyMeeting(existing.meeting) : null);
            const accountPatch = Object.prototype.hasOwnProperty.call(patch, "meetingAccountCode");
            const meetingAccountCode = accountPatch
                ? String(patch.meetingAccountCode || "")
                : (existing && existing.meetingAccountCode != null ? String(existing.meetingAccountCode) : "");
            setDraft(key, {
                subject: values.subject,
                html: values.html,
                text: values.text,
                qa: values.qa,
                requestId,
                updatedAt: new Date().toISOString(),
                meeting,
                meetingAccountCode,
                followUpAnchorMailRecordId: anchor,
                // fast-p 07（I-2）：通用附件是同一份草稿的字段，重建写点必须显式保留
                // （会议填入/采用草稿/程序性重存都不得清附件）。
                outboundAttachmentDraft: existing && existing.outboundAttachmentDraft
                    ? existing.outboundAttachmentDraft
                    : emptyOutboundAttachmentDraft()
            });
        }

        function snapshotQa(qa) {
            return {
                ragFactCodes: Array.isArray(qa.ragFactCodes) ? qa.ragFactCodes.slice() : [],
                ragCorpusFingerprint: qa.ragCorpusFingerprint || "",
                baselineText: qa.baselineText || ""
            };
        }

        // RFC 4122 v4（同 app.js createAiReplyGenerationId 语义）：crypto.randomUUID 可用
        // 时优先；否则回退纯 JS 实现。确定性只用于测试沙箱（无 crypto 时给出可解析 UUID）。
        function createRequestId() {
            if (globalThis && globalThis.crypto && typeof globalThis.crypto.randomUUID === "function") {
                return globalThis.crypto.randomUUID();
            }
            const bytes = new Uint8Array(16);
            if (globalThis && globalThis.crypto && typeof globalThis.crypto.getRandomValues === "function") {
                globalThis.crypto.getRandomValues(bytes);
            }
            bytes[6] = (bytes[6] & 0x0f) | 0x40;
            bytes[8] = (bytes[8] & 0x3f) | 0x80;
            const hex = Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");
            return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
        }

        // 会话回信发送前取/生成 requestId：草稿已有则复用（失败重试/安全取消收敛同一
        // attempt）；没有则生成并先写回草稿（I-4/I-12）。跟进草稿（I-4）在 inbound 目标上
        // 同样需要 requestId，故判定条件是「outbound 或草稿已带跟进锚点」。
        function ensureOutboundRequestId() {
            const key = currentTargetKey();
            if (!key) return null;
            const existing = getDraft(key);
            const anchored = !!(existing && existing.followUpAnchorMailRecordId != null);
            if (instance.manual.mode !== "outbound" && !anchored) return null;
            if (existing && existing.requestId) return existing.requestId;
            const requestId = createRequestId();
            const values = readManualValues();
            if (values) {
                setDraft(key, Object.assign({}, existing || {}, {
                    subject: values.subject,
                    html: values.html,
                    text: values.text,
                    qa: values.qa,
                    requestId,
                    updatedAt: new Date().toISOString(),
                    // fast-p 07（I-2）：程序性取 requestId 不丢附件字段。
                    outboundAttachmentDraft: existing && existing.outboundAttachmentDraft
                        ? existing.outboundAttachmentDraft
                        : emptyOutboundAttachmentDraft()
                }));
            }
            return requestId;
        }

        // --------------------------------------------------------------
        // 跟进邮件（followup 01 · I-1..I-8/S-1..S-3）：人工选择引用邮件 → 自然短正文 +
        // 同源完整引用 → 填入草稿。弹窗只列出当前已加载窗口的候选（不额外拉历史）；
        // 默认不选中；填入是全文替换并清 QA/会议；发送复用既有会话人工回信 adapter。
        // --------------------------------------------------------------

        function followupItemById(id) {
            const target = Number(id);
            if (!Number.isFinite(target)) return null;
            const items = instance.conversation.items || [];
            return items.find((item) => item
                && Number(item.id) === target
                && String(item.source) === "MAIL_RECORD") || null;
        }

        /** I-2：候选必须是当前账号已加载窗口内的真实 SENT 发件；按 eventAt DESC,id DESC。 */
        function followupCandidates() {
            const scope = String(instance.conversation.accountScope || "");
            return (instance.conversation.items || [])
                .filter((item) => item
                    && String(item.source) === "MAIL_RECORD"
                    && String(item.direction) === "OUTBOUND"
                    && String(item.sendStatus) === "SENT"
                    && String(item.accountCode || "").trim() !== ""
                    && String(item.accountCode) !== FOLLOWUP_SIMULATOR_ACCOUNT
                    && (!scope || String(item.accountCode) === scope))
                .slice()
                .sort((a, b) => {
                    const left = String(a.eventAt || "");
                    const right = String(b.eventAt || "");
                    if (left !== right) return left < right ? 1 : -1;
                    return Number(b.id) - Number(a.id);
                });
        }

        /** I-6：引用源优先非空 cleanedBody，否则 body。 */
        function followupSourceText(item) {
            const cleaned = item ? item.cleanedBody : null;
            if (cleaned != null && String(cleaned).trim() !== "") return String(cleaned);
            return item && item.body != null ? String(item.body) : "";
        }

        /** I-6：主题/引用头/引用正文同源；引用头固定 `On YYYY-MM-DD HH:mm, <accountCode> wrote:`。 */
        function followupQuoteText(item) {
            const when = `${datePart(item.eventAt)} ${timePart(item.eventAt)}`.trim();
            return `On ${when}, ${String(item.accountCode || "")} wrote:\n\n`
                + quotePlainTextFromSource(followupSourceText(item));
        }

        /** I-5：称呼优先复用所选邮件纯文本首个非空行（只接受 Dear/Hi 且 <=100 字符）。 */
        function followupGreeting(item) {
            const plain = quotePlainTextFromSource(followupSourceText(item));
            const first = plain.split("\n").map((line) => line.trim()).find((line) => line !== "") || "";
            if (/^(Dear|Hi)(\s|,)/.test(first) && first.length <= 100) return first;
            return "Dear Professor,";
        }

        function followupBodyText(item, copy) {
            const line = FOLLOWUP_BODY_LINES[copy];
            if (!line) return "";
            const getSenderName = hostFn("mcHostGetSenderName");
            const senderName = getSenderName ? String(getSenderName(item.accountCode) || "").trim() : "";
            return `${followupGreeting(item)}\n\n${line}\n\nBest regards,${senderName ? `\n${senderName}` : ""}`;
        }

        function followupAnchorNoteText(id) {
            const item = followupItemById(id);
            const parts = [`已引用邮件 #${Number(id)}`];
            if (item) {
                const when = `${datePart(item.eventAt)} ${timePart(item.eventAt)}`.trim();
                if (when) parts.push(when);
                if (item.subject) parts.push(String(item.subject));
            }
            return parts.join(" · ");
        }

        function followupAnchorNoteHtml(id) {
            return `<div class="mc-note" data-role="followup-anchor-note">${escapeText(followupAnchorNoteText(id))}</div>`;
        }

        function currentFollowUpAnchorId() {
            const key = currentTargetKey();
            if (!key) return null;
            const draft = getDraft(key);
            if (!draft || draft.followUpAnchorMailRecordId == null) return null;
            const id = Number(draft.followUpAnchorMailRecordId);
            return Number.isFinite(id) ? id : null;
        }

        /** S-3：draft 有锚点时把提示插入 `.mc-editor` 之后、会议附件之前；无则移除。 */
        function refreshFollowupAnchorNote() {
            const composeEl = manualComposeEl();
            if (!composeEl || !composeEl.querySelector) return;
            const editor = composeEl.querySelector('[data-role="mc-editor"]');
            if (!editor || !editor.parentNode) return;
            const existing = composeEl.querySelector('[data-role="followup-anchor-note"]');
            if (existing && existing.parentNode) existing.parentNode.removeChild(existing);
            const id = currentFollowUpAnchorId();
            if (id == null) return;
            const doc = docRoot();
            if (!doc || typeof doc.createElement !== "function") return;
            const holder = doc.createElement("div");
            holder.innerHTML = followupAnchorNoteHtml(id);
            const node = holder.firstChild;
            if (!node) return;
            editor.parentNode.insertBefore(node, editor.nextSibling);
        }

        function followupDialogEl() {
            const root = instance.elements.portalRoot;
            if (!root || !root.querySelector) return null;
            return root.querySelector(".followup-dialog");
        }

        function buildFollowupHtml(candidates) {
            const name = (instance.selectedSummary && instance.selectedSummary.name) || "-";
            const options = candidates.map((item) => `
                        <button class="followup-mail-option" type="button" role="radio" aria-checked="false" data-action="mc-select-followup" data-mail-record-id="${Number(item.id)}">
                            <small>${escapeText(`${datePart(item.eventAt)} ${timePart(item.eventAt)} · ${item.accountCode || ""}`)}</small>
                            <strong>${escapeText(item.subject || "(无主题)")}</strong>
                            <span>${escapeText(quotePlainTextFromSource(followupSourceText(item)).slice(0, 160))}</span>
                        </button>`).join("");
            const list = options || '<p class="followup-help">当前窗口没有可引用的已发送邮件。</p>';
            // 范围外第 4 项：只列已加载窗口，存在更早页时明确提示，不自动循环拉取。
            const more = instance.conversation.hasMore
                ? '<p class="followup-help" data-role="followup-more">仅显示当前已加载的最近邮件；如需更早邮件，请关闭本窗口并点击「加载更早信件」。</p>'
                : "";
            return `
                <dialog class="followup-dialog" aria-labelledby="followupTitle">
                    <header class="followup-head">
                        <div><h2 id="followupTitle">生成跟进邮件</h2><p>${escapeText(name)} · 请手动选择本次要引用的邮件</p></div>
                        <button class="followup-close" type="button" data-action="mc-close-followup" aria-label="关闭跟进邮件">×</button>
                    </header>
                    <div class="followup-grid">
                        <section class="followup-list-pane">
                            <h3 class="followup-pane-title">1. 选择引用邮件</h3>
                            <p class="followup-help">仅显示当前回复账号成功发出的已加载邮件。系统不会自动选择。</p>
                            ${more}
                            <div class="followup-mail-list" role="radiogroup" aria-label="可引用的已发送邮件">${list}</div>
                        </section>
                        <section class="followup-preview-pane">
                            <h3 class="followup-pane-title">2. 选择跟进文案</h3>
                            <p class="followup-help">请选择本次跟进重点；系统不会按专家状态自动选择。</p>
                            <div class="followup-field" role="group" aria-label="跟进文案">
                                <button class="button" type="button" data-action="mc-select-followup-copy" data-followup-copy="video" aria-pressed="false" disabled>视频会议</button>
                                <button class="button" type="button" data-action="mc-select-followup-copy" data-followup-copy="meetingReminder" aria-pressed="false" disabled>会议提醒</button>
                                <button class="button" type="button" data-action="mc-select-followup-copy" data-followup-copy="cv" aria-pressed="false" disabled>索取简历</button>
                                <button class="button" type="button" data-action="mc-select-followup-copy" data-followup-copy="generic" aria-pressed="false" disabled>通用跟进</button>
                            </div>
                            <h3 class="followup-pane-title">3. 跟进内容</h3>
                            <label class="followup-field">主题<input type="text" aria-label="跟进邮件主题" data-role="followup-subject"></label>
                            <label class="followup-field">跟进正文<textarea aria-label="跟进邮件正文" data-role="followup-body"></textarea></label>
                            <h3 class="followup-pane-title">4. 引用的原邮件</h3>
                            <div class="followup-quote" data-role="followup-quote"></div>
                        </section>
                    </div>
                    <footer class="followup-actions">
                        <p>选择只填入草稿，不会立即发送邮件</p>
                        <div><button class="button" type="button" data-action="mc-close-followup">取消</button><button class="button primary" type="button" data-action="mc-apply-followup" disabled>填入人工回复</button></div>
                    </footer>
                </dialog>`;
        }

        function openFollowUpDialog() {
            const key = currentTargetKey();
            if (!key) return;
            // 唯一 portal 只承载一个 overlay：先关管理面板，避免互相覆盖后状态失同步。
            closeManageOverlay({ restoreFocus: false });
            closeMaterialRequestDialog({ restoreFocus: false });
            closeTemplateReferenceDialog({ restoreFocus: false });
            const root = ensurePortalRoot();
            if (!root) {
                hostShowStatus("跟进邮件面板挂载失败", "error");
                return;
            }
            root.innerHTML = buildFollowupHtml(followupCandidates());
            const dialog = followupDialogEl();
            if (!dialog) return;
            instance.followup.open = true;
            instance.followup.targetKey = key;
            instance.followup.selectedId = null;
            instance.followup.selectedCopy = null;
            instance.followup.trigger = host.querySelector ? host.querySelector('[data-action="mc-open-followup"]') : null;
            if (typeof dialog.showModal === "function") {
                try {
                    dialog.showModal();
                } catch (e) {
                    dialog.setAttribute("open", "");
                }
            } else {
                dialog.setAttribute("open", "");
            }
            // I-1：打开即无选中项，apply 禁用（绝不默认选最新一封）。
            renderFollowUpSelection(null);
            if (typeof dialog.focus === "function") dialog.focus();
        }

        function renderFollowUpSelection(mailRecordId) {
            const dialog = followupDialogEl();
            if (!dialog) return;
            const id = mailRecordId == null ? null : Number(mailRecordId);
            const item = id == null ? null : followupItemById(id);
            instance.followup.selectedId = item ? id : null;
            const chosen = item ? String(id) : "";
            dialog.querySelectorAll('[data-action="mc-select-followup"]').forEach((node) => {
                node.setAttribute("aria-checked", node.dataset.mailRecordId === chosen ? "true" : "false");
            });
            const applyButton = dialog.querySelector('[data-action="mc-apply-followup"]');
            const subjectInput = dialog.querySelector('[data-role="followup-subject"]');
            const bodyInput = dialog.querySelector('[data-role="followup-body"]');
            const quoteNode = dialog.querySelector('[data-role="followup-quote"]');
            instance.followup.selectedCopy = null;
            dialog.querySelectorAll('[data-action="mc-select-followup-copy"]').forEach((button) => {
                button.disabled = !item;
                button.setAttribute("aria-pressed", "false");
            });
            if (subjectInput) subjectInput.value = item ? chatSubjectPrefill(item.subject) : "";
            if (bodyInput) bodyInput.value = "";
            // 引用预览只用 textContent（I-6：不把邮件正文当 HTML 插入）。
            if (quoteNode) quoteNode.textContent = item ? followupQuoteText(item) : "";
            if (applyButton) applyButton.disabled = true;
        }

        function renderFollowUpCopy(copy) {
            const dialog = followupDialogEl();
            if (!dialog) return;
            const item = instance.followup.selectedId == null
                ? null
                : followupItemById(instance.followup.selectedId);
            const selected = item && Object.prototype.hasOwnProperty.call(FOLLOWUP_BODY_LINES, copy) ? copy : null;
            instance.followup.selectedCopy = selected;
            dialog.querySelectorAll('[data-action="mc-select-followup-copy"]').forEach((button) => {
                const chosen = button.dataset.followupCopy === selected;
                button.disabled = !item;
                button.setAttribute("aria-pressed", chosen ? "true" : "false");
            });
            const bodyInput = dialog.querySelector('[data-role="followup-body"]');
            if (bodyInput) bodyInput.value = selected ? followupBodyText(item, selected) : "";
            const applyButton = dialog.querySelector('[data-action="mc-apply-followup"]');
            if (applyButton) applyButton.disabled = !(item && selected);
        }

        /** I-7：填入是全文替换（清 QA/会议快照与会议正文块），并写入所选锚点。 */
        function applyFollowUpFromDialog() {
            const key = currentTargetKey();
            const dialog = followupDialogEl();
            if (!key || !dialog || !instance.followup.open || instance.followup.targetKey !== key) return;
            const item = instance.followup.selectedId == null
                ? null
                : followupItemById(instance.followup.selectedId);
            if (!item || !Object.prototype.hasOwnProperty.call(FOLLOWUP_BODY_LINES, instance.followup.selectedCopy)) return;
            const composeEl = manualComposeEl();
            const inputs = manualInputs(composeEl);
            if (!inputs) return;
            const subjectInput = dialog.querySelector('[data-role="followup-subject"]');
            const bodyInput = dialog.querySelector('[data-role="followup-body"]');
            const typedSubject = subjectInput ? String(subjectInput.value || "").trim() : "";
            const typedBody = bodyInput ? String(bodyInput.value || "") : "";
            inputs.subjectInput.value = typedSubject || chatSubjectPrefill(item.subject);
            // 全文替换：原编辑器内容（含任何会议正文块）整体消失。
            inputs.editor.innerText = `${typedBody}\n\n${followupQuoteText(item)}`;
            instance.manual.qa = null;
            instance.meeting.editorRevision += 1;
            saveDraftFromInputs({
                meeting: null,
                meetingAccountCode: "",
                followUpAnchorMailRecordId: Number(item.id)
            });
            refreshMeetingAttachmentCard();
            refreshFollowupAnchorNote();
            closeFollowUpDialog({ restoreFocus: true });
            saveConversationState();
            const manualSection = composeEl && composeEl.closest
                ? composeEl.closest('.mc-section[data-section="manual"]')
                : null;
            if (manualSection && !manualSection.open && manualSection.setAttribute) {
                manualSection.setAttribute("open", "");
            }
            hostShowStatus("已填入跟进回复草稿，发送前请确认", "ok");
        }

        /** 只关闭弹窗：不触碰主题/正文/QA/会议/锚点（I-7）。 */
        function closeFollowUpDialog(options) {
            const opts = options || {};
            const wasOpen = instance.followup.open;
            instance.followup.open = false;
            instance.followup.selectedId = null;
            instance.followup.selectedCopy = null;
            instance.followup.targetKey = null;
            const dialog = followupDialogEl();
            if (dialog) {
                if (typeof dialog.close === "function") {
                    try {
                        dialog.close();
                    } catch (e) { /* noop */ }
                }
                if (dialog.hasAttribute && dialog.hasAttribute("open")) dialog.removeAttribute("open");
            }
            const root = instance.elements.portalRoot;
            if (root) root.innerHTML = "";
            if (wasOpen && opts.restoreFocus !== false) {
                const trigger = instance.followup.trigger;
                if (trigger && typeof trigger.focus === "function") trigger.focus();
            }
            instance.followup.trigger = null;
        }

        // --------------------------------------------------------------
        // 材料索取（fast-p 03 · I-2..I-4/S-2）：工具栏入口 → 原生 dialog 选择五项 →
        // 固定英文引言 + 无编号项目符号追加到当前人工草稿。只读 02 的五项接口、
        // 只写当前草稿；不改材料状态、不发邮件、不碰主题/QA/会议快照/附件。
        // --------------------------------------------------------------

        const MATERIAL_REQUEST_LEAD = "To proceed, please provide the following supporting materials:";

        // 状态说明的中文短标签与操作栏同一语义（S-1）；英文正文只取响应 requestText。
        const MATERIAL_REQUEST_STATE_LABELS = {
            PENDING: "待提供",
            PROVIDED: "已提供",
            DECLINED: "暂不愿提供"
        };

        function materialRequestTriggerHtml() {
            return `<button class="button material-request-trigger" type="button" data-action="mc-open-material-request">材料索取</button>`;
        }

        function materialRequestDialogEl() {
            const root = instance.elements.portalRoot;
            if (!root || !root.querySelector) return null;
            return root.querySelector(".material-request-dialog") || null;
        }

        function materialRequestOptionInputs(dialog) {
            const node = dialog || materialRequestDialogEl();
            if (!node || typeof node.querySelectorAll !== "function") return [];
            return Array.prototype.slice.call(node.querySelectorAll(".material-request-option input"));
        }

        function materialRequestDialogHtml() {
            return `
                <dialog class="material-request-dialog" aria-labelledby="materialRequestTitle">
                    <header class="material-request-head">
                        <h2 id="materialRequestTitle">选择需要提供的材料</h2>
                        <button class="material-request-close" type="button" data-action="mc-close-material-request" aria-label="关闭材料索取">×</button>
                    </header>
                    <div class="material-request-body">
                        <p class="material-request-error" role="alert" hidden></p>
                        <p class="material-request-heading">材料 <span data-role="material-request-count">已选 0 项</span></p>
                        <div class="material-request-options"></div>
                        <p class="material-request-heading">正文预览</p>
                        <div class="material-request-paper" aria-live="polite">
                            <p>${escapeText(MATERIAL_REQUEST_LEAD)}</p>
                            <ul data-role="material-request-preview-list"></ul>
                        </div>
                    </div>
                    <footer class="material-request-actions">
                        <button class="button" type="button" data-action="mc-close-material-request">取消</button>
                        <button class="button primary" type="button" data-action="mc-apply-material-request" disabled>确认并填入回复</button>
                    </footer>
                </dialog>`;
        }

        /** I-3：打开时捕获的会话身份（owner/contact/target/编辑器 revision）。 */
        function materialRequestIdentity() {
            const contactId = Number(instance.selectedContactId);
            return {
                ownerKey: conversationCacheKey(instance.user, instance.conversation.accountScope || "", contactId),
                contactId,
                targetKey: currentTargetKey(),
                editorRevision: Number(instance.meeting.editorRevision)
            };
        }

        function materialRequestIdentityMatches(captured) {
            if (!captured) return false;
            const now = materialRequestIdentity();
            return now.ownerKey === captured.ownerKey
                && now.contactId === captured.contactId
                && now.targetKey === captured.targetKey
                && now.editorRevision === captured.editorRevision;
        }

        function setMaterialRequestError(message) {
            const dialog = materialRequestDialogEl();
            const node = dialog ? dialog.querySelector(".material-request-error") : null;
            if (!node) return;
            if (message) {
                node.textContent = String(message);
                node.hidden = false;
            } else {
                node.textContent = "";
                node.hidden = true;
            }
        }

        /** 每项一个 checkbox：仅 PENDING 可勾且默认勾选；label/requestText 只作文本节点（I-4）。 */
        function renderMaterialRequestOptions(items) {
            const dialog = materialRequestDialogEl();
            const container = dialog ? dialog.querySelector(".material-request-options") : null;
            const doc = docRoot();
            if (!container || !doc) return;
            container.innerHTML = "";
            const list = Array.isArray(items) ? items : [];
            list.forEach((item, index) => {
                const status = String(item.status || "PENDING");
                const selectable = status === "PENDING";
                const label = doc.createElement("label");
                label.className = "material-request-option";
                const input = doc.createElement("input");
                input.setAttribute("type", "checkbox");
                input.setAttribute("aria-label", String(item.label == null ? "" : item.label));
                input.dataset.materialIndex = String(index);
                input.checked = selectable;
                input.disabled = !selectable;
                const name = doc.createElement("span");
                name.textContent = String(item.label == null ? "" : item.label);
                const state = doc.createElement("small");
                state.textContent = MATERIAL_REQUEST_STATE_LABELS[status] || status;
                label.appendChild(input);
                label.appendChild(name);
                label.appendChild(state);
                container.appendChild(label);
            });
            renderMaterialRequestPreview();
        }

        /** 选中项严格按接口目录顺序（DOM 顺序即目录顺序），只取本次勾选的项（I-2）。 */
        function selectedMaterialRequestItems() {
            const items = instance.materialRequest.items || [];
            return materialRequestOptionInputs()
                .filter((input) => input.checked === true)
                .map((input) => {
                    const index = Number(input.dataset ? input.dataset.materialIndex : NaN);
                    return Number.isInteger(index) ? items[index] : null;
                })
                .filter((item) => !!item);
        }

        function renderMaterialRequestPreview() {
            const dialog = materialRequestDialogEl();
            if (!dialog) return;
            const selected = selectedMaterialRequestItems();
            const count = dialog.querySelector('[data-role="material-request-count"]');
            if (count) count.textContent = `已选 ${selected.length} 项`;
            const list = dialog.querySelector('[data-role="material-request-preview-list"]');
            const doc = docRoot();
            if (list && doc) {
                list.innerHTML = "";
                selected.forEach((item) => {
                    const li = doc.createElement("li");
                    li.textContent = String(item.requestText == null ? "" : item.requestText);
                    list.appendChild(li);
                });
            }
            const apply = dialog.querySelector('[data-action="mc-apply-material-request"]');
            if (apply) apply.disabled = selected.length === 0;
        }

        async function openMaterialRequestDialog() {
            if (instance.manual.mode !== "inbound" || !currentTargetKey()) return;
            const composeEl = manualComposeEl();
            const inputs = manualInputs(composeEl);
            if (!inputs) return;
            // open 前先保存当前编辑器值；捕获 owner/contact/target/editorRevision（I-3）
            saveDraftFromInputs();
            const identity = materialRequestIdentity();
            if (!identity.targetKey) return;
            // 唯一 portal 只承载一个 overlay：先关管理面板与跟进弹窗，避免状态失同步。
            closeManageOverlay({ restoreFocus: false });
            closeFollowUpDialog({ restoreFocus: false });
            closeTemplateReferenceDialog({ restoreFocus: false });
            const root = ensurePortalRoot();
            if (!root) {
                hostShowStatus("材料索取面板挂载失败", "error");
                return;
            }
            const seq = instance.materialRequest.seq + 1;
            instance.materialRequest.open = true;
            instance.materialRequest.seq = seq;
            instance.materialRequest.identity = identity;
            instance.materialRequest.items = [];
            instance.materialRequest.trigger = host.querySelector
                ? host.querySelector('[data-action="mc-open-material-request"]')
                : null;
            root.innerHTML = materialRequestDialogHtml();
            const dialog = materialRequestDialogEl();
            if (!dialog) return;
            if (typeof dialog.showModal === "function") {
                try {
                    dialog.showModal();
                } catch (e) {
                    dialog.setAttribute("open", "");
                }
            } else {
                dialog.setAttribute("open", "");
            }
            renderMaterialRequestOptions([]);
            // I-2：每次打开都为当前 contact 重读五项；不做任何本地缓存复用。
            let data = null;
            let failure = "";
            try {
                data = await hostApi()(`/api/expert-contacts/${identity.contactId}/material-requests`);
            } catch (e) {
                failure = e && e.message ? e.message : "";
            }
            // 迟到响应：目标切换、弹窗关闭或身份/revision 变化一律不写（I-3）。
            if (instance.disposed || !instance.materialRequest.open || instance.materialRequest.seq !== seq) return;
            if (!materialRequestIdentityMatches(instance.materialRequest.identity)) {
                closeMaterialRequestDialog({ restoreFocus: false });
                return;
            }
            if (failure || !Array.isArray(data)) {
                instance.materialRequest.items = [];
                renderMaterialRequestOptions([]);
                setMaterialRequestError("材料状态加载失败: " + (failure || "响应格式异常"));
                return;
            }
            instance.materialRequest.items = data;
            renderMaterialRequestOptions(data);
        }

        /** I-3/I-4：确认只把本次选中项追加到当前编辑器，并走既有编辑器输入入口保存。 */
        function applyMaterialRequestFromDialog() {
            const dialog = materialRequestDialogEl();
            if (!dialog || !instance.materialRequest.open) return false;
            if (!materialRequestIdentityMatches(instance.materialRequest.identity)) {
                closeMaterialRequestDialog({ restoreFocus: false });
                return false;
            }
            const selected = selectedMaterialRequestItems();
            if (!selected.length) return false;
            const composeEl = manualComposeEl();
            const inputs = manualInputs(composeEl);
            const doc = docRoot();
            if (!inputs || !doc) return false;
            const lead = doc.createElement("p");
            lead.textContent = MATERIAL_REQUEST_LEAD;
            const list = doc.createElement("ul");
            selected.forEach((item) => {
                const li = doc.createElement("li");
                li.textContent = String(item.requestText == null ? "" : item.requestText);
                list.appendChild(li);
            });
            inputs.editor.appendChild(lead);
            inputs.editor.appendChild(list);
            // 只走既有保存入口（主题/QA/会议快照/附件原样保留），不发任何请求。
            handleManualComposeInput(inputs.editor);
            closeMaterialRequestDialog({ restoreFocus: false });
            if (typeof inputs.editor.focus === "function") inputs.editor.focus();
            return true;
        }

        /** 只关闭弹窗：不触碰草稿、材料状态、主题、QA、会议快照或附件（I-2）。 */
        function closeMaterialRequestDialog(options) {
            const opts = options || {};
            const wasOpen = instance.materialRequest.open;
            instance.materialRequest.open = false;
            // 使在途 GET 失效（迟到响应不得再渲染/填入）
            instance.materialRequest.seq += 1;
            instance.materialRequest.identity = null;
            instance.materialRequest.items = [];
            const dialog = materialRequestDialogEl();
            if (dialog) {
                if (typeof dialog.close === "function") {
                    try {
                        dialog.close();
                    } catch (e) { /* noop */ }
                }
                if (dialog.hasAttribute && dialog.hasAttribute("open")) dialog.removeAttribute("open");
            }
            const root = instance.elements.portalRoot;
            if (root) root.innerHTML = "";
            if (wasOpen && opts.restoreFocus !== false) {
                const trigger = instance.materialRequest.trigger;
                if (trigger && typeof trigger.focus === "function") trigger.focus();
            }
            instance.materialRequest.trigger = null;
        }

        function onMaterialRequestOptionChange(event) {
            if (instance.disposed) return;
            const target = event ? event.target : null;
            const dialog = materialRequestDialogEl();
            if (!dialog || !target || typeof dialog.contains !== "function" || !dialog.contains(target)) return;
            const tag = target.tagName ? String(target.tagName).toLowerCase() : "";
            if (tag !== "input") return;
            setMaterialRequestError("");
            renderMaterialRequestPreview();
        }

        // --------------------------------------------------------------
        // 引用邮件模板（fast-p 01 · I-1..I-8 / S-1..S-3）：人工回复工具栏入口 →
        // body portal 里的原生 dialog → 只读 GET /api/compose-templates 与只读
        // POST /api/compose-templates/preview-draft → 把当前预览快照以纯文本节点填入
        // 当前人工草稿。只调用这两个只读接口；不加草稿字段、不改发送路由。
        // --------------------------------------------------------------

        /** 与 MailVariableService.PREVIEW_UNSUBSCRIBE_URL 同值：预览专用示例退订链接。 */
        const TEMPLATE_PREVIEW_UNSUBSCRIBE_URL = "https://example.com/u/unsubscribe?token=preview";
        const TEMPLATE_ACCOUNT_UNRESOLVED_TEXT = "无法确认当前跟进邮件的发件账号，请重新选择跟进邮件";
        const TEMPLATE_STALE_TEXT = "回复目标或草稿已变化，请关闭后重新选择模板";
        const TEMPLATE_UNSUBSCRIBE_TEXT = "退订链接尚未配置，当前仅为示例链接，请先配置后重试";

        function templateReferenceDialogEl() {
            const root = instance.elements.portalRoot;
            if (!root || !root.querySelector) return null;
            return root.querySelector(".reply-template-dialog") || null;
        }

        function templateReferenceNode(role) {
            const dialog = templateReferenceDialogEl();
            if (!dialog || !dialog.querySelector) return null;
            return dialog.querySelector('[data-role="' + String(role) + '"]') || null;
        }

        function templateReferenceSearchInput() {
            return templateReferenceNode("template-search");
        }

        /** 只在节点仍挂在文档里时恢复焦点：切上下文后不把焦点拉回已卸载的按钮。 */
        function focusIfAvailable(node) {
            if (!node || typeof node.focus !== "function") return;
            if (node.isConnected === false) return;
            if (node.isConnected === undefined && !node.parentNode) return;
            node.focus();
        }

        /** S-2 静态层级合同：动态列表只替换 template-list 内部，动态文本只改 textContent。 */
        function templateReferenceDialogHtml() {
            return `
                <dialog class="reply-template-dialog" aria-labelledby="replyTemplateTitle">
                    <header class="reply-template-head">
                        <div><h2 id="replyTemplateTitle">引用邮件模板</h2><p>选择模板，预览后填入当前回复</p></div>
                        <button class="reply-template-close" type="button" data-action="mc-close-template-reference" aria-label="关闭模板弹框">×</button>
                    </header>
                    <div class="reply-template-context">
                        <span class="reply-template-avatar" aria-hidden="true">✉</span>
                        <div><strong data-role="template-contact-name"></strong><small data-role="template-contact-email"></small></div>
                        <span class="reply-template-account">回复账号 <b data-role="template-account"></b></span>
                    </div>
                    <div class="reply-template-layout">
                        <aside class="reply-template-sidebar">
                            <input class="reply-template-search" type="search" data-role="template-search" aria-label="搜索邮件模板" placeholder="搜索模板名称、描述">
                            <p class="reply-template-caption">已启用模板 <span data-role="template-count">0</span></p>
                            <div class="reply-template-list" data-role="template-list" aria-label="邮件模板列表"></div>
                            <p class="reply-template-source">模板来自「邮件模板」</p>
                        </aside>
                        <section class="reply-template-main" aria-label="模板预览" aria-busy="false">
                            <div class="reply-template-preview-head"><h3 data-role="template-name"></h3><span class="reply-template-badge" data-role="template-preview-badge" hidden>预览已生成</span></div>
                            <p class="reply-template-status" data-role="template-status" role="status">请选择邮件模板</p>
                            <button class="button" type="button" data-action="mc-retry-template-reference" hidden>重试</button>
                            <div class="reply-template-warning" data-role="template-warning" role="status" hidden></div>
                            <div class="reply-template-paper" data-role="template-paper" hidden>
                                <div class="reply-template-subject"><span>模板主题</span><strong data-role="template-subject"></strong><small>默认保留当前回复主题</small></div>
                                <div class="reply-template-body" data-role="template-body"></div>
                            </div>
                        </section>
                    </div>
                    <footer class="reply-template-footer">
                        <div class="reply-template-options">
                            <label class="checkbox-row"><input type="checkbox" data-role="template-replace-subject">同时替换回复主题</label>
                            <div class="reply-template-modes" data-role="template-modes" hidden>
                                <span>正文已有内容</span>
                                <label class="checkbox-row"><input type="radio" name="reply-template-mode" value="append" checked>追加到末尾</label>
                                <label class="checkbox-row"><input type="radio" name="reply-template-mode" value="replace">替换正文</label>
                            </div>
                            <p class="reply-template-hint" data-role="template-meeting-hint" hidden>含日历附件，仅支持追加；如需替换，请先移除日历附件</p>
                        </div>
                        <div class="reply-template-actions">
                            <button class="button" type="button" data-action="mc-close-template-reference">取消</button>
                            <button class="button primary" type="button" data-action="mc-apply-template-reference" disabled>填入回复</button>
                        </div>
                    </footer>
                </dialog>`;
        }

        /** I-6：打开时捕获的草稿身份（owner/contact/target/epoch + 主题与正文快照）。 */
        function templateReferenceIdentity() {
            const contactId = Number(instance.selectedContactId);
            const contact = instance.conversation.contact || null;
            const summary = instance.selectedSummary || {};
            return {
                ownerKey: conversationCacheKey(instance.user, instance.conversation.accountScope || "", contactId),
                contactId,
                expertEmail: contact ? String(contact.expertEmail || "") : "",
                expertName: String(summary.name || (contact && contact.expertName) || ""),
                targetKey: currentTargetKey(),
                convEpoch: Number(instance.convEpoch),
                editorRevision: Number(instance.meeting.editorRevision),
                anchorId: null,
                senderAccountCode: "",
                subject: "",
                html: "",
                text: ""
            };
        }

        /** I-2：普通来信取来信账号；有跟进锚点时必须找到相同 id 的成功发件，否则明确失败。 */
        function templateReferenceSenderAccount(anchorId) {
            if (anchorId == null) {
                return { ok: true, code: String(instance.manual.targetAccountCode || "") };
            }
            const match = followupCandidates().find((item) => Number(item.id) === Number(anchorId));
            if (!match) return { ok: false, code: "" };
            return { ok: true, code: String(match.accountCode || "") };
        }

        /** I-6：在途回包只承认「弹框仍打开 + seq 未变 + owner/target/epoch 仍匹配」。 */
        function templateReferenceSeqMatches(seq) {
            const state = instance.templateReference;
            if (instance.disposed || !state.open || state.seq !== seq) return false;
            const captured = state.identity;
            if (!captured) return false;
            const now = templateReferenceIdentity();
            return now.ownerKey === captured.ownerKey
                && now.contactId === captured.contactId
                && now.targetKey === captured.targetKey
                && now.convEpoch === captured.convEpoch;
        }

        function templateReferenceSelectedItem() {
            const state = instance.templateReference;
            if (state.selectedId == null) return null;
            return (state.items || []).find((item) => Number(item.id) === Number(state.selectedId)) || null;
        }

        function templateReferenceItemValid(item) {
            if (!item) return false;
            const id = Number(item.id);
            if (!Number.isInteger(id) || id <= 0) return false;
            if (typeof item.subject !== "string") return false;
            return Array.isArray(item.blocks);
        }

        function templateReferencePreviewValid(result) {
            if (!result || typeof result !== "object") return false;
            if (typeof result.subject !== "string" || typeof result.body !== "string") return false;
            return Array.isArray(result.blocks) && Array.isArray(result.fallbackKeys) && Array.isArray(result.variables);
        }

        function templateReferenceVisibleItems() {
            const items = instance.templateReference.items || [];
            const input = templateReferenceSearchInput();
            const query = input ? String(input.value || "").trim().toLowerCase() : "";
            if (!query) return items.slice();
            return items.filter((item) => {
                const name = String(item.templateName || "").toLowerCase();
                const description = item.description == null ? "" : String(item.description).toLowerCase();
                return name.indexOf(query) >= 0 || description.indexOf(query) >= 0;
            });
        }

        function templateReferenceStatusText() {
            const state = instance.templateReference;
            if (state.loading === "list") return "正在加载邮件模板…";
            if (state.error === "list") return "邮件模板加载失败，请重试";
            if (state.loading === "preview") return "正在生成预览…";
            if (state.error === "preview") return "模板预览失败，请重试";
            if (state.preview) return "";
            return "请选择邮件模板";
        }

        function templateReferenceApplyMode() {
            const dialog = templateReferenceDialogEl();
            if (!dialog || !dialog.querySelector) return "append";
            const replace = dialog.querySelector('input[name="reply-template-mode"][value="replace"]');
            return replace && replace.checked ? "replace" : "append";
        }

        function templateReferenceReplaceSubjectChecked() {
            const node = templateReferenceNode("template-replace-subject");
            return !!(node && node.checked);
        }

        function templateReferenceMeetingBlocked() {
            return !!manualMeetingSnapshot();
        }

        /** 唯一的「填入回复」可用性判断：渲染与点击复核共用，不实现两套分叉判断。 */
        function templateReferenceApplyState() {
            const state = instance.templateReference;
            if (instance.manual.busy) return { enabled: false, reason: "" };
            if (state.loading !== "" || state.error !== "") return { enabled: false, reason: "" };
            const item = templateReferenceSelectedItem();
            if (!item || !templateReferenceItemValid(item)) return { enabled: false, reason: "" };
            const preview = state.preview;
            if (!preview) return { enabled: false, reason: "" };
            if (templateReferenceApplyMode() === "replace" && templateReferenceMeetingBlocked()) {
                return { enabled: false, reason: "" };
            }
            const body = String(preview.body || "");
            if (!body.trim()) return { enabled: false, reason: "预览正文为空，无法填入" };
            const toEmail = preview.toEmail == null ? "" : String(preview.toEmail);
            const identity = state.identity || {};
            if (!toEmail || toEmail !== String(identity.expertEmail || "")) {
                return { enabled: false, reason: "预览联系人邮箱与当前专家邮箱不一致，请先核对专家资料" };
            }
            if (body.indexOf("${") >= 0) {
                return { enabled: false, reason: "正文仍含未替换变量（${…}），请先补齐模板变量" };
            }
            const subject = String(preview.subject || "");
            const replaceSubject = templateReferenceReplaceSubjectChecked();
            if (replaceSubject && subject.indexOf("${") >= 0) {
                return { enabled: false, reason: "主题仍含未替换变量（${…}），请先补齐模板变量" };
            }
            if (body.indexOf(TEMPLATE_PREVIEW_UNSUBSCRIBE_URL) >= 0
                || (replaceSubject && subject.indexOf(TEMPLATE_PREVIEW_UNSUBSCRIBE_URL) >= 0)) {
                return { enabled: false, reason: TEMPLATE_UNSUBSCRIBE_TEXT };
            }
            return { enabled: true, reason: "" };
        }

        /** I-3：默认值/缺值/被跳过块与禁填原因是人可读提示，不新建发送门禁。 */
        function templateReferenceWarningText() {
            const preview = instance.templateReference.preview;
            if (!preview) return "";
            const lines = [];
            const skipped = (preview.blocks || []).filter((block) => block && block.included === false);
            if (skipped.length) {
                lines.push("未包含正文块：" + skipped.map((block) => {
                    const label = String(block.refDisplayName || block.blockType || "");
                    return `${Number(block.blockOrder)}. ${label}（${String(block.skipReason || "已跳过")}）`;
                }).join("；"));
            }
            const fallbackKeys = Array.isArray(preview.fallbackKeys) ? preview.fallbackKeys : [];
            if (fallbackKeys.length) lines.push("使用默认值：" + fallbackKeys.join("、"));
            const missing = (Array.isArray(preview.variables) ? preview.variables : [])
                .filter((entry) => entry && entry.filled === false && entry.usedFallback === false)
                .map((entry) => String(entry.key || ""))
                .filter((key) => key !== "");
            if (missing.length) lines.push("缺少变量值：" + missing.join("、") + "；填入后请补齐");
            const blocked = templateReferenceApplyState().reason;
            if (blocked) lines.push(blocked);
            return lines.join("\n");
        }

        function renderTemplateReferenceList() {
            const state = instance.templateReference;
            const list = templateReferenceNode("template-list");
            const doc = docRoot();
            if (!list || !doc) return;
            const visible = templateReferenceVisibleItems();
            const count = templateReferenceNode("template-count");
            if (count) count.textContent = String(visible.length);
            list.innerHTML = "";
            if (!visible.length) {
                const empty = doc.createElement("p");
                empty.setAttribute("class", "reply-template-status");
                empty.textContent = (state.items || []).length
                    ? "未找到匹配模板"
                    : (state.loading === "list" ? "正在加载邮件模板…" : "暂无已启用的邮件模板");
                list.appendChild(empty);
                return;
            }
            visible.forEach((item) => {
                const option = doc.createElement("button");
                option.setAttribute("class", "reply-template-option");
                option.setAttribute("type", "button");
                option.setAttribute("data-action", "mc-select-template-reference");
                option.setAttribute("data-template-id", String(item.id));
                option.setAttribute("aria-pressed", Number(item.id) === Number(state.selectedId) ? "true" : "false");
                const title = doc.createElement("strong");
                title.textContent = String(item.templateName || "");
                option.appendChild(title);
                const description = item.description == null ? "" : String(item.description);
                if (description) {
                    const sub = doc.createElement("small");
                    sub.textContent = description;
                    option.appendChild(sub);
                }
                list.appendChild(option);
            });
        }

        function renderTemplateReferencePreview() {
            const state = instance.templateReference;
            if (!state.open) return;
            const dialog = templateReferenceDialogEl();
            if (!dialog) return;
            const item = templateReferenceSelectedItem();
            const main = dialog.querySelector(".reply-template-main");
            if (main) main.setAttribute("aria-busy", state.loading === "preview" ? "true" : "false");
            const nameNode = templateReferenceNode("template-name");
            if (nameNode) nameNode.textContent = item ? String(item.templateName || "") : "";
            const preview = state.preview;
            const badge = templateReferenceNode("template-preview-badge");
            if (badge) badge.hidden = !preview;
            const status = templateReferenceNode("template-status");
            if (status) status.textContent = templateReferenceStatusText();
            const retry = dialog.querySelector('[data-action="mc-retry-template-reference"]');
            if (retry) retry.hidden = !state.error;
            const warning = templateReferenceNode("template-warning");
            const warningText = templateReferenceWarningText();
            if (warning) {
                warning.textContent = warningText;
                warning.hidden = warningText === "";
            }
            const subjectNode = templateReferenceNode("template-subject");
            if (subjectNode) subjectNode.textContent = preview ? String(preview.subject || "") : "";
            const bodyNode = templateReferenceNode("template-body");
            if (bodyNode) bodyNode.textContent = preview ? String(preview.body || "") : "";
            const paper = templateReferenceNode("template-paper");
            if (paper) paper.hidden = !preview;
            const modes = templateReferenceNode("template-modes");
            const identity = state.identity || {};
            if (modes) modes.hidden = String(identity.text || "").trim() === "";
            const meetingBlocked = templateReferenceMeetingBlocked();
            const hint = templateReferenceNode("template-meeting-hint");
            if (hint) hint.hidden = !meetingBlocked;
            const replaceRadio = dialog.querySelector('input[name="reply-template-mode"][value="replace"]');
            if (replaceRadio) replaceRadio.disabled = meetingBlocked;
            if (meetingBlocked) {
                const appendRadio = dialog.querySelector('input[name="reply-template-mode"][value="append"]');
                if (appendRadio) appendRadio.checked = true;
            }
            const applyButton = dialog.querySelector('[data-action="mc-apply-template-reference"]');
            if (applyButton) applyButton.disabled = !templateReferenceApplyState().enabled;
        }

        function renderTemplateReference() {
            const state = instance.templateReference;
            if (!state.open) return;
            if (!templateReferenceDialogEl()) return;
            const identity = state.identity || {};
            const name = templateReferenceNode("template-contact-name");
            if (name) name.textContent = String(identity.expertName || "");
            const email = templateReferenceNode("template-contact-email");
            if (email) email.textContent = String(identity.expertEmail || "");
            const account = templateReferenceNode("template-account");
            if (account) account.textContent = String(identity.senderAccountCode || "");
            renderTemplateReferenceList();
            renderTemplateReferencePreview();
        }

        /** I-2：每次打开重读列表，只呈现 enabled === true 的模板，不使用宿主缓存。 */
        function loadTemplateReferenceList() {
            const state = instance.templateReference;
            if (!state.open || !state.identity) return;
            const seq = state.seq + 1;
            state.seq = seq;
            state.items = [];
            state.selectedId = null;
            state.preview = null;
            state.loading = "list";
            state.error = "";
            renderTemplateReference();
            hostApi()("/api/compose-templates").then((data) => {
                if (!templateReferenceSeqMatches(seq)) return;
                if (!Array.isArray(data)) {
                    state.loading = "";
                    state.error = "list";
                    renderTemplateReference();
                    return;
                }
                const items = data.filter((item) => item && item.enabled === true);
                state.items = items;
                state.loading = "";
                state.error = "";
                const first = items.length ? items[0] : null;
                state.selectedId = first ? Number(first.id) : null;
                renderTemplateReference();
                if (state.selectedId != null) loadTemplateReferencePreview();
            }).catch(() => {
                if (!templateReferenceSeqMatches(seq)) return;
                state.loading = "";
                state.error = "list";
                state.preview = null;
                renderTemplateReference();
            });
        }

        /** I-2：只读 preview-draft；回包经 seq/identity 校验后成为唯一填入快照。 */
        function loadTemplateReferencePreview() {
            const state = instance.templateReference;
            if (!state.open || !state.identity) return;
            const item = templateReferenceSelectedItem();
            const seq = state.seq + 1;
            state.seq = seq;
            state.preview = null;
            state.error = "";
            if (!item || !templateReferenceItemValid(item)) {
                state.loading = "";
                state.error = item ? "preview" : "";
                renderTemplateReference();
                return;
            }
            state.loading = "preview";
            renderTemplateReference();
            const identity = state.identity;
            const payload = {
                subject: String(item.subject),
                subjectSnippetId: item.subjectSnippetId != null ? item.subjectSnippetId : null,
                blocks: (item.blocks || []).map((block) => ({
                    blockOrder: block ? block.blockOrder : null,
                    blockType: block ? block.blockType : null,
                    refId: block && block.refId != null ? block.refId : null,
                    customText: block && block.customText != null ? block.customText : null
                })),
                contactId: identity.contactId,
                senderAccountCode: identity.senderAccountCode,
                strictPlaceholders: false,
                variantIndex: 0
            };
            hostApi()("/api/compose-templates/preview-draft", {
                method: "POST",
                body: JSON.stringify(payload)
            }).then((result) => {
                if (!templateReferenceSeqMatches(seq)) return;
                if (!templateReferencePreviewValid(result)) {
                    state.loading = "";
                    state.error = "preview";
                    state.preview = null;
                    renderTemplateReference();
                    return;
                }
                state.preview = result;
                state.loading = "";
                state.error = "";
                renderTemplateReference();
            }).catch(() => {
                if (!templateReferenceSeqMatches(seq)) return;
                state.loading = "";
                state.error = "preview";
                state.preview = null;
                renderTemplateReference();
            });
        }

        function retryTemplateReference() {
            const state = instance.templateReference;
            if (!state.open) return;
            if (state.error === "list") {
                loadTemplateReferenceList();
                return;
            }
            if (state.error === "preview") loadTemplateReferencePreview();
        }

        function selectTemplateReferenceItem(id) {
            const state = instance.templateReference;
            if (!state.open) return;
            const numeric = Number(id);
            const item = (state.items || []).find((entry) => Number(entry.id) === numeric) || null;
            if (!item) return;
            state.selectedId = numeric;
            state.preview = null;
            state.error = "";
            state.loading = "";
            renderTemplateReference();
            loadTemplateReferencePreview();
        }

        function openTemplateReferenceDialog() {
            if (instance.manual.mode !== "inbound" || instance.manual.busy) return;
            const contactId = Number(instance.selectedContactId);
            const processingId = Number(instance.manual.targetProcessingId);
            if (!Number.isInteger(contactId) || contactId <= 0) return;
            if (!Number.isInteger(processingId) || processingId <= 0) return;
            if (!currentTargetKey()) return;
            const composeEl = manualComposeEl();
            const inputs = manualInputs(composeEl);
            if (!inputs) return;
            const anchorId = currentFollowUpAnchorId();
            const account = templateReferenceSenderAccount(anchorId);
            if (!account.ok) {
                hostShowStatus(TEMPLATE_ACCOUNT_UNRESOLVED_TEXT, "error");
                return;
            }
            const values = readManualValues(composeEl);
            if (!values) return;
            const identity = templateReferenceIdentity();
            identity.anchorId = anchorId;
            identity.senderAccountCode = account.code;
            identity.subject = values.subject;
            identity.html = values.html;
            identity.text = values.text;
            // 唯一 portal 只承载一个 overlay：打开引用弹框前先关掉其它面板（不触碰草稿状态）。
            closeManageOverlay({ restoreFocus: false });
            closeFollowUpDialog({ restoreFocus: false });
            closeMaterialRequestDialog({ restoreFocus: false });
            closeTemplateReferenceDialog({ restoreFocus: false });
            const root = ensurePortalRoot();
            if (!root) {
                hostShowStatus("引用模板弹框挂载失败", "error");
                return;
            }
            const state = instance.templateReference;
            state.open = true;
            state.identity = identity;
            state.items = [];
            state.selectedId = null;
            state.preview = null;
            state.loading = "list";
            state.error = "";
            state.trigger = host.querySelector ? host.querySelector('[data-action="mc-open-template-reference"]') : null;
            root.innerHTML = templateReferenceDialogHtml();
            const dialog = templateReferenceDialogEl();
            if (!dialog) {
                closeTemplateReferenceDialog({ restoreFocus: false });
                return;
            }
            // I-7：原生 cancel（Esc）走同一个关闭函数。
            if (typeof dialog.addEventListener === "function") {
                dialog.addEventListener("cancel", () => { closeTemplateReferenceDialog({ restoreFocus: true }); });
            }
            if (typeof dialog.showModal === "function") {
                try {
                    dialog.showModal();
                } catch (e) {
                    dialog.setAttribute("open", "");
                }
            } else {
                dialog.setAttribute("open", "");
            }
            renderTemplateReference();
            loadTemplateReferenceList();
        }

        /** I-7：只移除自己拥有的 .reply-template-dialog，绝不 portalRoot.innerHTML=""。 */
        function closeTemplateReferenceDialog(options) {
            const opts = options || {};
            const state = instance.templateReference;
            const wasOpen = state.open;
            state.open = false;
            state.seq += 1;
            state.identity = null;
            state.items = [];
            state.selectedId = null;
            state.preview = null;
            state.loading = "";
            state.error = "";
            const dialog = templateReferenceDialogEl();
            if (dialog) {
                if (typeof dialog.close === "function") {
                    try {
                        dialog.close();
                    } catch (e) { /* noop */ }
                }
                if (dialog.hasAttribute && dialog.hasAttribute("open")) dialog.removeAttribute("open");
                if (dialog.parentNode && typeof dialog.parentNode.removeChild === "function") {
                    dialog.parentNode.removeChild(dialog);
                } else if (typeof dialog.remove === "function") {
                    dialog.remove();
                }
            }
            const trigger = state.trigger;
            state.trigger = null;
            if (wasOpen && opts.restoreFocus !== false) focusIfAvailable(trigger);
        }

        /** I-4：纯文本语义 —— 逐行 text node + <br>，包在无 class 的 div；不解析 HTML。 */
        function templateReferenceBodyNodes(doc, text) {
            const holder = doc.createElement("div");
            normalizeManualTextLineBreaks(text).split("\n").forEach((line, index) => {
                if (index > 0) holder.appendChild(doc.createElement("br"));
                if (line !== "") holder.appendChild(doc.createTextNode(line));
            });
            return holder;
        }

        function insertTemplateReferenceBody(editor, text, mode) {
            const doc = docRoot();
            if (!doc || !editor) return false;
            if (mode === "replace") {
                while (editor.firstChild) editor.removeChild(editor.firstChild);
            }
            editor.appendChild(templateReferenceBodyNodes(doc, text));
            return true;
        }

        /** I-5/I-6：应用前复核身份、正文/主题快照、busy 与会议替换禁用条件。 */
        function applyTemplateReference() {
            const state = instance.templateReference;
            if (!state.open || !state.identity) return;
            if (instance.manual.mode !== "inbound" || instance.manual.busy) return;
            if (!templateReferenceSeqMatches(state.seq)) {
                hostShowStatus(TEMPLATE_STALE_TEXT, "error");
                closeTemplateReferenceDialog({ restoreFocus: true });
                return;
            }
            if (!templateReferenceApplyState().enabled) {
                renderTemplateReferencePreview();
                return;
            }
            const composeEl = manualComposeEl();
            const inputs = manualInputs(composeEl);
            if (!inputs) return;
            const identity = state.identity;
            const values = readManualValues(composeEl);
            if (!values
                || values.subject !== identity.subject
                || values.html !== identity.html
                || values.text !== identity.text) {
                const dialog = templateReferenceDialogEl();
                const applyButton = dialog ? dialog.querySelector('[data-action="mc-apply-template-reference"]') : null;
                if (applyButton) applyButton.disabled = true;
                const status = templateReferenceNode("template-status");
                if (status) status.textContent = TEMPLATE_STALE_TEXT;
                hostShowStatus(TEMPLATE_STALE_TEXT, "error");
                return;
            }
            const mode = templateReferenceApplyMode();
            if (mode === "replace" && templateReferenceMeetingBlocked()) {
                renderTemplateReferencePreview();
                return;
            }
            const preview = state.preview;
            if (templateReferenceReplaceSubjectChecked()) {
                inputs.subjectInput.value = String(preview.subject == null ? "" : preview.subject);
            }
            if (mode === "replace") {
                // I-5：替换正文清除 QA/RAG 证据，由既有保存入口同步清 draft.qa。
                instance.manual.qa = null;
            }
            if (!insertTemplateReferenceBody(inputs.editor, String(preview.body || ""), mode)) return;
            // I-4/I-6：走既有编辑器输入入口保存，读取的是当前 draft（保留晚到的附件与锚点）。
            handleManualComposeInput(inputs.editor);
            saveConversationState();
            closeTemplateReferenceDialog({ restoreFocus: false });
            focusIfAvailable(inputs.editor);
            hostShowStatus("模板已填入回复，可继续编辑", "ok");
        }

        /** 纯本地搜索：保留仍在结果中的选择，否则回退首个，空结果清选中与快照。 */
        function onTemplateReferenceInput(event) {
            if (instance.disposed) return;
            const state = instance.templateReference;
            if (!state.open) return;
            const target = event ? event.target : null;
            const search = templateReferenceSearchInput();
            if (!search || target !== search) return;
            const visible = templateReferenceVisibleItems();
            if (visible.some((item) => Number(item.id) === Number(state.selectedId))) {
                renderTemplateReference();
                return;
            }
            state.preview = null;
            state.error = "";
            state.loading = "";
            const first = visible.length ? visible[0] : null;
            state.selectedId = first ? Number(first.id) : null;
            renderTemplateReference();
            if (state.selectedId != null) loadTemplateReferencePreview();
        }

        function onTemplateReferenceChange(event) {
            if (instance.disposed) return;
            const state = instance.templateReference;
            if (!state.open) return;
            const dialog = templateReferenceDialogEl();
            const target = event ? event.target : null;
            if (!dialog || !target || typeof dialog.contains !== "function" || !dialog.contains(target)) return;
            const role = target.getAttribute ? target.getAttribute("data-role") : null;
            const name = target.getAttribute ? target.getAttribute("name") : null;
            if (role === "template-replace-subject" || name === "reply-template-mode") {
                renderTemplateReferencePreview();
            }
        }

        // --------------------------------------------------------------
        // 会议确认宿主（fast-p 04 T3/T4/S-3/S-5；组件缺席自动降级）
        // --------------------------------------------------------------

        function manualMeetingSnapshot() {
            const key = currentTargetKey();
            if (!key) return null;
            const draft = getDraft(key);
            return draft && draft.meeting ? draft.meeting : null;
        }

        function deepCopyMeeting(meeting) {
            if (meeting == null) return null;
            try {
                return JSON.parse(JSON.stringify(meeting));
            } catch (e) {
                return null;
            }
        }

        function meetingRevisionNext() {
            meetingDraftSeq += 1;
            return meetingDraftSeq;
        }

        function meetingNormalizeText(text) {
            const lib = meetingLib();
            if (lib && typeof lib.normalizeMeetingText === "function") {
                try { return lib.normalizeMeetingText(text); } catch (e) { /* fallthrough */ }
            }
            return String(text == null ? "" : text)
                .normalize ? String(text).normalize("NFKC").replace(/\s+/g, " ").trim() : String(text == null ? "" : text).replace(/\s+/g, " ").trim();
        }

        function meetingBlocksIn(editor) {
            if (!editor || typeof editor.querySelectorAll !== "function") return [];
            return Array.prototype.slice.call(editor.querySelectorAll('[data-meeting-block="true"]'));
        }

        function meetingBlockIn(editor) {
            const blocks = meetingBlocksIn(editor);
            return blocks.length > 0 ? blocks[0] : null;
        }

        function meetingElementText(node) {
            if (!node) return "";
            if (typeof node.innerText === "string") return node.innerText;
            if (node.textContent != null) return String(node.textContent);
            return "";
        }

        /** 输入后检测：块数量/可见文本与基线比较；格式-only 更新 blockHtml 基线仍 ready。 */
        function detectMeetingChange(editor, meeting) {
            if (!meeting) return null;
            const blocks = meetingBlocksIn(editor);
            const block = blocks.length === 1 ? blocks[0] : null;
            const next = deepCopyMeeting(meeting);
            let changed = false;
            if (!block || blocks.length !== 1) {
                if (next.state !== "stale") { next.state = "stale"; changed = true; }
                return changed ? { meeting: next } : null;
            }
            const currentText = meetingNormalizeText(meetingElementText(block));
            const baselineText = meetingNormalizeText(next.blockText);
            if (currentText !== baselineText) {
                if (next.state !== "stale") { next.state = "stale"; changed = true; }
                return changed ? { meeting: next } : null;
            }
            // 文本相同：仅格式变化可刷新 blockHtml 基线
            const currentHtml = typeof block.outerHTML === "string" ? block.outerHTML : "";
            if (currentHtml && currentHtml !== next.blockHtml) {
                next.blockHtml = currentHtml;
                changed = true;
            }
            return changed ? { meeting: next } : null;
        }

        function manualEditorNode() {
            const inputs = manualInputs();
            return inputs ? inputs.editor : null;
        }

        /** open() 前把真实 DOM 判定结果作为瞬态 _flow 附在快照副本上（不落库）。 */
        function meetingSnapshotForDialog(meeting, editor) {
            if (!meeting) return null;
            const copy = deepCopyMeeting(meeting);
            const block = meetingBlockIn(editor);
            const textSame = !!(meeting.blockText && block &&
                meetingNormalizeText(meetingElementText(block)) === meetingNormalizeText(meeting.blockText));
            const hasText = meetingNormalizeText(meetingElementText(editor)) !== "";
            let flow;
            if (textSame) flow = "update";
            else if (!hasText) flow = "fill";
            else flow = "conflict";
            copy._flow = flow;
            return copy;
        }

        function insertMeetingBlock(editor, action, blockHtml) {
            const doc = docRoot();
            if (!doc || !editor || typeof editor.appendChild !== "function") return null;
            const block = doc.createElement("div");
            block.setAttribute("class", mcCls("body-block"));
            block.setAttribute("data-meeting-block", "true");
            block.innerHTML = String(blockHtml || "");
            if (action === "replace") {
                while (editor.firstChild) editor.removeChild(editor.firstChild);
                editor.appendChild(block);
                return block;
            }
            const oldBlock = meetingBlockIn(editor);
            if (action === "update" && oldBlock && oldBlock.parentNode) {
                oldBlock.parentNode.insertBefore(block, oldBlock);
                oldBlock.parentNode.removeChild(oldBlock);
                return block;
            }
            // append：br 分隔后追加
            editor.appendChild(doc.createElement("br"));
            editor.appendChild(block);
            return block;
        }

        function createMeetingBlobUrl(icsText) {
            if (typeof URL === "undefined" || typeof URL.createObjectURL !== "function") return "";
            const BlobCtor = (typeof Blob !== "undefined") ? Blob : null;
            if (!BlobCtor) return "";
            try {
                const blob = new BlobCtor([String(icsText || "")], { type: "text/calendar;charset=UTF-8" });
                return URL.createObjectURL(blob);
            } catch (e) {
                return "";
            }
        }

        function revokeMeetingBlob() {
            if (instance.meeting.lastBlobUrl) {
                if (typeof URL !== "undefined" && typeof URL.revokeObjectURL === "function") {
                    try { URL.revokeObjectURL(instance.meeting.lastBlobUrl); } catch (e) { /* noop */ }
                }
                instance.meeting.lastBlobUrl = "";
            }
        }

        function refreshMeetingAttachmentCard() {
            const composeEl = manualComposeEl();
            if (!composeEl) return;
            const container = composeEl.querySelector ? composeEl.querySelector('[data-role="meeting-attachment"]') : null;
            if (!container) return;
            const meeting = manualMeetingSnapshot();
            container.innerHTML = meeting ? meetingCardInnerHtml(meeting) : "";
            container.setAttribute("data-state", meeting && meeting.state === "stale" ? "stale" : "ready");
            if (!meeting) {
                revokeMeetingBlob();
                return;
            }
            const filenameNode = container.querySelector('[data-role="filename"]');
            if (filenameNode) filenameNode.textContent = meetingAttachmentFilename(meeting);
            const metaNode = container.querySelector('[data-role="file-meta"]');
            if (metaNode) metaNode.textContent = meetingCardMetaText(meeting);
            const download = container.querySelector('[data-action="mc-download-meeting"]');
            revokeMeetingBlob();
            if (download) {
                const ics = meeting.preview && meeting.preview.attachment
                    ? String(meeting.preview.attachment.icsText || "")
                    : "";
                const filename = meetingAttachmentFilename(meeting) || "meeting.ics";
                const url = createMeetingBlobUrl(ics);
                if (url) {
                    instance.meeting.lastBlobUrl = url;
                    download.setAttribute("href", url);
                    download.setAttribute("download", filename);
                } else {
                    download.removeAttribute("href");
                    download.removeAttribute("download");
                }
            }
        }

        function teardownMeetingViews() {
            const controller = instance.meeting.controller;
            if (controller) {
                try { controller.close({ restoreFocus: false }); } catch (e) { /* noop */ }
                try { controller.dispose(); } catch (e) { /* noop */ }
                instance.meeting.controller = null;
            }
            revokeMeetingBlob();
            instance.meeting.editorRevision = 0;
        }

        // T5：账号过滤变化 → close/dispose 会议并 abort 在途请求（草稿缓存不清）
        function meetingCloseDisposeOnAccountScopeChange(prevAccount, nextAccount) {
            if (instance.selectedContactId == null) return;
            if (String(prevAccount || "") === String(nextAccount || "")) return;
            closeExpertNoteDialog({ restoreFocus: false, force: true });
            invalidateExpertNote();
            // 跟进候选绑定账号范围：范围变化即关闭弹窗（草稿缓存不清，I-7）。
            closeFollowUpDialog({ restoreFocus: false });
            closeMaterialRequestDialog({ restoreFocus: false });
            closeTemplateReferenceDialog({ restoreFocus: false });
            teardownMeetingViews();
            // c3（I-3）：账号上下文变化同样作废推荐代次与自有弹窗。
            closeContactTimingDialog({ restoreFocus: false });
            invalidateContactTiming();
        }

        function meetingCardActionsDisabled(disabled) {
            const composeEl = manualComposeEl();
            if (!composeEl || !composeEl.querySelectorAll) return;
            ["mc-open-meeting", "mc-download-meeting", "mc-edit-meeting", "mc-remove-meeting"].forEach((action) => {
                composeEl.querySelectorAll(`[data-action="${action}"]`).forEach((node) => {
                    node.disabled = disabled;
                    if (disabled) node.setAttribute("aria-disabled", "true");
                    else node.removeAttribute("aria-disabled");
                });
            });
        }

        function setManualComposeSending(sending) {
            const composeEl = manualComposeEl();
            if (!composeEl) return;
            if (sending) composeEl.setAttribute("data-meeting-sending", "true");
            else composeEl.removeAttribute("data-meeting-sending");
            const inputs = manualInputs(composeEl);
            if (inputs) {
                inputs.editor.setAttribute("contenteditable", sending ? "false" : "true");
                inputs.subjectInput.disabled = sending;
            }
            if (composeEl.querySelectorAll) {
                composeEl.querySelectorAll(".mc-editor-tools .button").forEach((button) => {
                    button.disabled = sending;
                });
                // fast-p 07：发送期间当前目标的附件增删/下载一并禁用（其他专家不受影响）。
                composeEl.querySelectorAll('[data-action="mc-remove-attachment"]').forEach((node) => {
                    node.disabled = sending;
                    if (sending) node.setAttribute("aria-disabled", "true");
                    else node.removeAttribute("aria-disabled");
                });
                composeEl.querySelectorAll('[data-role="outbound-download"]').forEach((node) => {
                    if (sending) node.setAttribute("aria-disabled", "true");
                    else node.removeAttribute("aria-disabled");
                });
            }
            meetingCardActionsDisabled(sending);
            instance.meeting.sending = sending;
        }

        function openMeetingDialog() {
            if (instance.manual.mode !== "inbound" || !currentTargetKey()) return;
            const composeEl = manualComposeEl();
            const inputs = manualInputs(composeEl);
            if (!inputs) return;
            const lib = meetingLib();
            if (!lib || typeof lib.create !== "function") return;
            // open 前先保存当前编辑器值；捕获 owner/key/editorRevision（I-2）
            saveDraftFromInputs();
            const key = currentTargetKey();
            const draft = getDraft(key);
            const meeting = draft && draft.meeting ? draft.meeting : null;
            const contactId = Number(instance.selectedContactId);
            const scope = instance.conversation.accountScope || "";
            const ownerKey = conversationCacheKey(instance.user, scope, contactId);
            const summary = instance.selectedSummary || {};
            const expertLabel = summary && (summary.name || summary.email)
                ? String(summary.name || summary.email)
                : "";
            let controller = instance.meeting.controller;
            if (!controller) {
                controller = lib.create({
                    api: hostApi(),
                    contextPath: (instance.options && instance.options.contextPath)
                        ? String(instance.options.contextPath)
                        : "",
                    onApply: (capturedTargetKey, payload) => applyMeetingFromDialog(capturedTargetKey, payload),
                    onStatus: (message, type) => hostShowStatus(message, type)
                });
                instance.meeting.controller = controller;
            }
            try {
                controller.open({
                    ownerKey,
                    targetKey: key,
                    contactId,
                    processingId: Number(instance.manual.targetProcessingId),
                    senderAccountCode: instance.manual.targetAccountCode || "",
                    expertLabel,
                    editorHtml: typeof inputs.editor.innerHTML === "string" ? inputs.editor.innerHTML : "",
                    editorText: meetingElementText(inputs.editor),
                    editorRevision: instance.meeting.editorRevision,
                    savedMeeting: meetingSnapshotForDialog(meeting, inputs.editor)
                });
            } catch (e) {
                hostShowStatus("会议确认打开失败：" + (e && e.message ? e.message : ""), "error");
            }
        }

        function writeDraftWithMeeting(meeting, qaOverride) {
            const key = currentTargetKey();
            if (!key) return null;
            const existing = getDraft(key);
            const qa = qaOverride !== undefined ? qaOverride : (existing && existing.qa ? snapshotQa(existing.qa) : null);
            const next = {
                subject: existing ? existing.subject : "",
                html: existing ? existing.html : "",
                text: existing ? existing.text : "",
                qa,
                updatedAt: new Date().toISOString(),
                meeting: deepCopyMeeting(meeting),
                meetingAccountCode: meeting ? (instance.manual.targetAccountCode || "") : "",
                // I-7：应用会议即全文替换为会议正文，跟进锚点必须同时清除。
                followUpAnchorMailRecordId: null,
                // fast-p 07（I-2）：会议填入/全量重写正文时通用附件仍是同一份草稿的字段，
                // 显式保留（会议 ICS 与通用附件互不影响，I-5）。
                outboundAttachmentDraft: existing && existing.outboundAttachmentDraft
                    ? existing.outboundAttachmentDraft
                    : emptyOutboundAttachmentDraft()
            };
            setDraft(key, next);
            return next;
        }

        /** 组件 onApply：返回 false 表示目标/修订不匹配或禁止的追加冲突。 */
        function applyMeetingFromDialog(capturedTargetKey, payload) {
            const key = currentTargetKey();
            if (!key || capturedTargetKey !== key) return false;
            if (!payload || !payload.preview) return false;
            if (Number(payload.capturedEditorRevision) !== Number(instance.meeting.editorRevision)) return false;
            const composeEl = manualComposeEl();
            const inputs = manualInputs(composeEl);
            if (!inputs) return false;
            const lib = meetingLib();
            if (!lib || typeof lib.planMeetingInsertion !== "function") return false;
            const draft = getDraft(key);
            const saved = draft && draft.meeting ? draft.meeting : null;
            const mode = payload.mode === "replace" ? "replace" : "append";
            let plan = null;
            try {
                plan = lib.planMeetingInsertion(inputs.editor, saved, payload.preview, mode);
            } catch (e) {
                plan = null;
            }
            if (!plan || !plan.allowed) {
                if (plan && plan.reason === "hand-edited") {
                    hostShowStatus("会议正文已手动修改；请选择替换整篇正文，或取消后移除日历附件。", "error");
                } else {
                    hostShowStatus("回复目标已变化，请重新打开会议确认", "error");
                }
                return false;
            }
            const block = insertMeetingBlock(inputs.editor, plan.action, plan.blockHtml);
            if (!block) return false;
            if (plan.qaClear) instance.manual.qa = null;
            const preview = payload.preview || {};
            const attachment = preview.attachment || {};
            const revision = meetingRevisionNext();
            const meeting = {
                input: deepCopyMeeting(preview.meeting || null),
                preview: {
                    htmlBody: preview.htmlBody || "",
                    textBody: preview.textBody || "",
                    attachment: {
                        filename: attachment.filename || "",
                        contentType: attachment.contentType || "",
                        icsText: attachment.icsText || "",
                        byteLength: Number(attachment.byteLength) || 0,
                        sha256: attachment.sha256 || "",
                        semanticSha256: attachment.semanticSha256 || ""
                    },
                    startUtc: preview.startUtc || "",
                    endUtc: preview.endUtc || "",
                    meetingTime: preview.meetingTime || "",
                    chinaTime: preview.chinaTime || "",
                    durationMinutes: Number(preview.durationMinutes) || 0
                },
                blockHtml: typeof block.outerHTML === "string" ? block.outerHTML : "",
                blockText: meetingNormalizeText(meetingElementText(block)),
                state: "ready",
                revision
            };
            const qaValue = plan.qaClear ? null : (draft && draft.qa ? snapshotQa(draft.qa) : instance.manual.qa ? snapshotQa(instance.manual.qa) : null);
            writeDraftWithMeeting(meeting, qaValue);
            instance.meeting.editorRevision += 1;
            refreshMeetingAttachmentCard();
            refreshFollowupAnchorNote();
            saveConversationState();
            hostShowStatus(payload.mode === "replace" ? "会议正文已替换并填入回复" : "会议确认已填入回复草稿，发送前请确认", "ok");
            return true;
        }

        function removeMeetingFromDraft() {
            const key = currentTargetKey();
            if (!key) return;
            const meeting = manualMeetingSnapshot();
            if (!meeting) {
                hostShowStatus("当前没有日历附件", "error");
                return;
            }
            const composeEl = manualComposeEl();
            const inputs = manualInputs(composeEl);
            if (inputs) {
                saveDraftFromInputs({ meeting: null, meetingAccountCode: "" });
            } else {
                const existing = getDraft(key);
                setDraft(key, Object.assign({}, existing || {}, {
                    meeting: null,
                    meetingAccountCode: "",
                    updatedAt: new Date().toISOString()
                }));
            }
            refreshMeetingAttachmentCard();
            saveConversationState();
            hostShowStatus("已移除日历附件，正文保留", "ok");
        }

        /** 编辑器输入统一入口：editorRevision++、meeting stale 检测、草稿保存。 */
        function handleManualComposeInput(target) {
            const composeEl = manualComposeEl();
            const inputs = manualInputs(composeEl);
            const isEditor = !!(inputs && target === inputs.editor);
            let patch = null;
            if (isEditor) {
                instance.meeting.editorRevision += 1;
                const meeting = manualMeetingSnapshot();
                if (meeting) {
                    const change = detectMeetingChange(inputs.editor, meeting);
                    if (change) patch = change;
                }
            }
            saveDraftFromInputs(patch || {});
            if (patch) refreshMeetingAttachmentCard();
        }

        function downloadSentMeetingAttachment(button) {
            const article = (button && typeof button.closest === "function")
                ? button.closest(".mc-message")
                : null;
            const key = article && article.dataset ? article.dataset.messageKey : "";
            const message = key ? messageByKey(key) : null;
            const ca = message && message.calendarAttachment ? message.calendarAttachment : null;
            if (!ca) return;
            const adapter = hostFn("mcHostDownloadCalendar");
            if (!adapter) {
                hostShowStatus("日历下载能力不可用", "error");
                return;
            }
            adapter(String(ca.downloadUrl || ""), String(ca.filename || "")).catch((err) => {
                if (instance.disposed) return;
                hostShowStatus(err && err.message ? err.message : "日历附件下载失败", "error");
            });
        }

        function adoptAssembly(processingId, assembly) {
            if (instance.manual.mode !== "inbound" || Number(instance.manual.targetProcessingId) !== Number(processingId)) return;
            const composeEl = manualComposeEl();
            if (!composeEl) return;
            const inputs = manualInputs(composeEl);
            if (!inputs) return;
            // 全文替换采用前：先移除旧会议附件与快照（I-3/I-6），正文由 assembly 覆盖
            const hadMeeting = !!manualMeetingSnapshot();
            if (hadMeeting) revokeMeetingBlob();
            const assemblyText = normalizeManualTextLineBreaks(
                (assembly && (assembly.renderedDraftText || assembly.rawDraftText || assembly.text)) || ""
            );
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
            // I-7：采用可信回复草稿是全文替换 —— 同时清除跟进锚点与会议快照。
            saveDraftFromInputs({ meeting: null, meetingAccountCode: "", followUpAnchorMailRecordId: null });
            refreshMeetingAttachmentCard();
            refreshFollowupAnchorNote();
            hostShowStatus(hadMeeting
                ? "草稿已采用到人工回复区，请确认后发送；原日历附件已移除"
                : "草稿已采用到人工回复区，请确认后发送", "ok");
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
            const textBody = normalizeManualTextLineBreaks(
                typeof inputs.editor.innerText === "string" ? inputs.editor.innerText : String(inputs.editor.textContent || "")
            );
            // I-1/I-3：提交前规范化 HTML（折叠连续 <br> 与空 <p>/<div>），请求体、确认重提与
            // 服务端最终发送门使用同一份 canonical 正文；编辑器 DOM 不改写。
            const htmlBody = normalizeManualRichHtmlLineBreaks(
                typeof inputs.editor.innerHTML === "string" ? inputs.editor.innerHTML : ""
            );
            const mode = instance.manual.mode;
            // I-8/跟进（I-3）：草稿携带所选锚点时，无论当前 target 是来信还是无来信会话，
            // 都必须走会话级接口并把真实 id 交给服务端重新校验。
            const draftSnapshot = getDraft(key);
            const followUpAnchorId = draftSnapshot && draftSnapshot.followUpAnchorMailRecordId != null
                ? Number(draftSnapshot.followUpAnchorMailRecordId)
                : null;
            const conversationSend = mode === "outbound" || followUpAnchorId != null;
            // 会议快照发送（T4）：只属于来信人工回复（inbound）且未选跟进锚点 —— 快照存在且
            // state ready 且当前会议块文本与基线一致才允许带附件发送；outbound/跟进路径无 meeting。
            const meeting = mode === "inbound" && !conversationSend ? manualMeetingSnapshot() : null;
            if (meeting) {
                if (meeting.state !== "ready") {
                    hostShowStatus("会议正文或回复目标已变化，请编辑会议重新生成，或移除日历附件。", "error");
                    return;
                }
                const block = meetingBlockIn(inputs.editor);
                const blockText = block ? meetingNormalizeText(meetingElementText(block)) : "";
                if (!block || blockText !== meetingNormalizeText(meeting.blockText)) {
                    hostShowStatus("会议正文已被修改，请编辑会议重新生成后再发送", "error");
                    return;
                }
            }
            let requestBody = null;
            let processingId = null;
            // fast-p 07（I-3）：只有 ready 条目按选择顺序提交；任一 uploading/failed 禁止发送
            // —— 失败项必须重新选择或移除，绝不静默漏发。
            const attachmentItems = draftSnapshot ? outboundAttachmentDraftOf(draftSnapshot).items : [];
            if (attachmentItems.some((item) => !item || item.state !== OUTBOUND_STATE_READY)) {
                hostShowStatus("附件正在上传或上传失败，请等待上传完成或移除失败附件后再发送", "error");
                return;
            }
            const attachmentIds = outboundAttachmentIds(draftSnapshot);
            const sentDraftSnapshot = outboundManualDraftSnapshot(draftSnapshot);
            if (!conversationSend) {
                // 来信路径：既有 processingId adapter，保留 QA/RAG payload（I-8）。
                requestBody = {
                    senderAccountCode: null,
                    subject,
                    htmlBody,
                    textBody,
                    operatorName: operatorName()
                };
                const qa = instance.manual.qa;
                if (qa && qa.ragFactCodes && qa.ragFactCodes.length) {
                    requestBody.ragFactCodes = qa.ragFactCodes.slice();
                    requestBody.ragCorpusFingerprint = qa.ragCorpusFingerprint || "";
                    requestBody.edited = textBody.trim() !== normalizeManualTextLineBreaks(qa.baselineText || "").trim();
                }
                // 会议字段只进来信人工富文本请求（T4/S-3）
                if (meeting) {
                    const sha = meeting.preview && meeting.preview.attachment
                        ? String(meeting.preview.attachment.sha256 || "")
                        : "";
                    if (!sha) {
                        hostShowStatus("会议附件信息不完整，请重新预览", "error");
                        return;
                    }
                    requestBody.meeting = deepCopyMeeting(meeting.input);
                    requestBody.previewAttachmentSha256 = sha;
                }
                // 07（I-3）：空数组省略字段，兼容未带附件的旧请求形态。
                if (attachmentIds.length > 0) requestBody.attachmentIds = attachmentIds.slice();
                processingId = Number(instance.manual.targetProcessingId);
            } else {
                // 会话回信路径：body 只含 requestId/当前 accountScope/显式锚点/自由正文/确认
                // 字段；无 processingId/senderAccountCode/QA/RAG/meeting（I-3/I-4/I-6）。
                const requestId = ensureOutboundRequestId();
                if (!requestId) {
                    hostShowStatus("无法生成发送请求标识", "error");
                    return;
                }
                requestBody = {
                    requestId,
                    accountScope: instance.conversation.accountScope || null,
                    subject,
                    htmlBody,
                    textBody,
                    operatorName: operatorName()
                };
                if (followUpAnchorId != null) requestBody.anchorMailRecordId = followUpAnchorId;
                // 07（I-3）：空数组省略字段，兼容未带附件的旧请求形态。
                if (attachmentIds.length > 0) requestBody.attachmentIds = attachmentIds.slice();
            }
            // I-2：异步前捕获 draftsMap/owner/key/revision/requestBody；不回调里再取。
            const draftsMap = ensureDraftsMap();
            const contactId = Number(instance.selectedContactId);
            const ownerKey = conversationCacheKey(instance.user, instance.conversation.accountScope || "", contactId);
            const capturedRevision = meeting ? meeting.revision : null;
            const inFlightKey = meeting ? `${ownerKey}|${key}` : null;
            if (inFlightKey && meetingInFlight.has(inFlightKey)) {
                hostShowStatus("该回复目标已有发送中的会议回复，请稍候", "error");
                return;
            }
            if (inFlightKey) meetingInFlight.add(inFlightKey);
            // 07（I-3）：带已就绪附件的发送同样锁住当前 owner 的编辑/附件增删；其他专家不受影响。
            const lockedCompose = !!meeting || attachmentItems.length > 0;
            instance.manual.busy = true;
            setSendButtonDisabled(true);
            if (lockedCompose) setManualComposeSending(true);
            const adapter = conversationSend
                ? hostFn("mcHostSendConversationRichReply")
                : hostFn("mcHostSendRichReply");
            const request = adapter
                ? (conversationSend
                    ? adapter(contactId, requestBody)
                    : adapter(processingId, requestBody))
                : Promise.reject(new Error("发送能力不可用"));
            request.then((sent) => {
                if (instance.disposed) {
                    if (inFlightKey) meetingInFlight.delete(inFlightKey);
                    return;
                }
                const stillCurrent = currentTargetKey() === key;
                if (lockedCompose && stillCurrent) setManualComposeSending(false);
                instance.manual.busy = false;
                refreshSendAvailability();
                if (inFlightKey) meetingInFlight.delete(inFlightKey);
                if (!sent) return; // 失败/取消保留全部输入（不清草稿、不改 QA、不删附件）
                if (meeting) {
                    // 成功只清该份已发送快照（I-2）：captured map + revision 匹配才删；
                    // 已切目标/新草稿一律不动新目标的草稿与 QA。
                    const snapshot = draftsMap.get(key);
                    const currentMeeting = snapshot && snapshot.meeting ? snapshot.meeting : null;
                    if (currentMeeting && Number(currentMeeting.revision) === Number(capturedRevision)) {
                        draftsMap.delete(key);
                        if (stillCurrent) {
                            instance.clearedEditorSnapshot = { key, draftsMap, snapshot: sentDraftSnapshot };
                            instance.manual.qa = null;
                            refreshMeetingAttachmentCard();
                            // 07：该草稿连同通用附件一起被清，卡片同步重建（无文件 → hidden）。
                            refreshOutboundFilesCard();
                        }
                        if (stillCurrent) afterSuccessfulSend(key);
                    } else if (currentMeeting && stillCurrent) {
                        const nextDraft = Object.assign({}, snapshot, {
                            meeting: Object.assign({}, currentMeeting, { state: "stale" }),
                            updatedAt: new Date().toISOString()
                        });
                        draftsMap.set(key, nextDraft);
                        refreshMeetingAttachmentCard();
                    }
                    return;
                }
                // 07（I-3）：成功只清捕获 owner 里仍等于发送快照的草稿（主题/正文/有序
                // 附件 id 全等），发送期间的新编辑或新附件一律保留；已切目标/已换草稿
                // 绝不删当前 owner 的草稿。
                const capturedDraft = draftsMap.get(key);
                const cleared = outboundDraftMatchesSnapshot(capturedDraft, sentDraftSnapshot);
                if (cleared) draftsMap.delete(key);
                if (stillCurrent && cleared) {
                    instance.clearedEditorSnapshot = { key, draftsMap, snapshot: sentDraftSnapshot };
                    instance.manual.qa = null;
                    refreshFollowupAnchorNote();
                    refreshOutboundFilesCard();
                }
                afterSuccessfulSend(key);
            }).catch(() => {
                if (instance.disposed) {
                    if (inFlightKey) meetingInFlight.delete(inFlightKey);
                    return;
                }
                const stillCurrent = currentTargetKey() === key;
                if (lockedCompose && stillCurrent) setManualComposeSending(false);
                instance.manual.busy = false;
                refreshSendAvailability();
                if (inFlightKey) meetingInFlight.delete(inFlightKey);
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
            // fast-p 03（IP-3）：发送成功恰由服务端产生排期，按 contactId 使两端失效并回读
            notifyMeetingScheduleChanged(contactId);
        }

        // ---- quiet refresh（I-5：合并保留已加载窗口） ----

        function refreshConversationQuiet() {
            const contactId = Number(instance.selectedContactId);
            if (!Number.isFinite(contactId) || contactId <= 0) return;
            const myEpoch = instance.convEpoch;
            loadExpertNote(contactId);
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
                // c3（T-1）：现有刷新成功后按同一 contact 重读推荐（渲染只重绘状态行）。
                loadContactTiming(contactId);
                // 02（T3.4）：已有会话刷新成功也是挂起状态 GET 的统一入口。
                loadSuspensionState(contactId);
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
            // 目标切换：关闭会议弹窗并撤销 modal URL；meeting 标 stale、保留旧 input 供改
            const meetingController = instance.meeting.controller;
            if (meetingController) {
                try { meetingController.close({ restoreFocus: false }); } catch (e) { /* noop */ }
            }
            revokeMeetingBlob();
            // fast-p 01（I-7）：回复目标切换即关闭引用模板弹框（旧快照与旧身份全部作废）。
            closeTemplateReferenceDialog({ restoreFocus: false });
            if (draft) {
                let migrated = Object.assign({}, draft, { subject: "", updatedAt: new Date().toISOString() });
                if (draft.meeting) {
                    migrated = Object.assign({}, migrated, {
                        meeting: Object.assign({}, draft.meeting, { state: "stale" })
                    });
                }
                setDraft(newKey, migrated);
                if (oldKey && oldKey !== newKey) deleteDraft(oldKey);
            }
            instance.meeting.editorRevision += 1;
            instance.manual.targetProcessingId = Number(newProcessingId);
            instance.manual.targetAccountCode = newAccount || "";
            instance.manual.targetKey = newKey;
            instance.manual.qa = draft && draft.qa ? snapshotQa(draft.qa) : null;
            const composeEl = manualComposeEl();
            if (composeEl) {
                refreshMeetingAttachmentCard();
                // 07（I-2）：同专家换回复目标时草稿随 targetKey 迁移，已 ready 附件保留。
                refreshOutboundFilesCard();
            } else {
                return;
            }
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
            // 07（I-4）：发送中/已禁用时拦截已发/草稿附件下载锚点的默认跳转。
            const downloadAnchor = target && typeof target.closest === "function"
                ? target.closest('[data-role="outbound-download"]')
                : null;
            if (downloadAnchor && downloadAnchor.getAttribute && downloadAnchor.getAttribute("aria-disabled") === "true") {
                if (event && typeof event.preventDefault === "function") event.preventDefault();
                return;
            }
            if (action === "mobile-mailbox-back") {
                returnToMobileList();
                return;
            }
            if (action === "mc-select-expert") {
                closeProgressMenu({ restoreFocus: false });
                if (instance.options.focus) instance.focusHandledContactId = instance.options.focus.contactId;
                const contactId = Number(data.contactId);
                const item = findSummaryByContactId(contactId);
                if (item) selectExpert(item, { trigger: button });
                return;
            }
            if (action === "mc-progress-menu") {
                if (button && button.disabled) return;
                toggleProgressMenu(data.contactId, button);
                return;
            }
            if (action === "mc-set-progress") {
                if (button && button.disabled) return;
                setProgress(data.contactId, data.progress);
                return;
            }
            if (action === "mc-dismiss-replied") {
                dismissReplied(data.contactId, button);
                return;
            }
            if (action === "mc-select-unmatched") {
                const unmatchedId = Number(data.unmatchedId);
                if (Number.isFinite(unmatchedId) && unmatchedId > 0) selectUnmatched(unmatchedId);
                return;
            }
            if (action === "mc-filter") {
                const chip = data.chip || CHIP_ALL;
                const nextChip = FILTER_CHIPS.some((entry) => entry.key === chip) ? chip : CHIP_ALL;
                if (nextChip !== instance.chip) {
                    returnToMobileList();
                    if (instance.chip === CHIP_UNMATCHED) leaveUnmatchedMode();
                    if (nextChip === CHIP_UNMATCHED) {
                        // 离开专家会话前保存草稿/滚动，但邮件 id 绝不写入 selectedContactId（I-6）。
                        saveCurrentConversation();
                        clearSelectedConversation();
                    }
                }
                instance.chip = nextChip;
                instance.chipUserTouched = true;
                freezeDefaultProbe();
                instance.list.page = 0;
                syncChipButtons();
                syncSearchChrome();
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
            if (action === "mc-contact-location") {
                openContactLocationDialog();
                return;
            }
            if (action === "mc-contact-timing-evidence") {
                openContactEvidenceDialog();
                return;
            }
            if (action === "mc-contact-timing-retry") {
                retryContactTiming();
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
                if (item) selectExpert(item, { force: true });
                return;
            }
            if (action === "mc-load-older") {
                loadOlderMessages();
                return;
            }
            if (action === "mc-suspension") {
                onSuspensionClick(data.contactId, button);
                return;
            }
            if (action === "mc-suspension-edit-reason") {
                if (!instance.auth.ready || instance.reasonForm.busy) return;
                const view = suspensionViewForContact(data.contactId);
                if (view.suspended) openReasonForm(data.contactId, button, true);
                return;
            }
            if (action === "mc-suspension-reason-cancel") {
                cancelReasonForm();
                return;
            }
            if (action === "mc-suspension-reason-confirm") {
                confirmSuspensionReason(data.contactId);
                return;
            }
            if (action === "mc-process-cancel") {
                cancelProcessConfirm();
                return;
            }
            if (action === "mc-process-confirm") {
                markResolvedByKey(data.messageKey || "");
                return;
            }
            if (action === "mc-suspension-keep") {
                keepSuspension();
                return;
            }
            if (action === "mc-suspension-end") {
                endSuspension(data.contactId);
                return;
            }
            if (action === "mc-mark-resolved") {
                openProcessConfirm(data.messageKey || "");
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
            if (action === "mc-note-open") {
                openExpertNoteDialog();
                return;
            }
            if (action === "mc-note-retry") {
                loadExpertNote(instance.selectedContactId);
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
            if (action === "mc-add-schedule") {
                openMeetingSchedule("create");
                return;
            }
            if (action === "mc-edit-schedule") {
                openMeetingSchedule("edit");
                return;
            }
            if (action === "mc-cancel-schedule") {
                openMeetingSchedule("cancel");
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
            if (action === "mc-open-meeting" || action === "mc-edit-meeting") {
                openMeetingDialog();
                return;
            }
            if (action === "mc-open-material-request") {
                openMaterialRequestDialog();
                return;
            }
            if (action === "mc-open-template-reference") {
                openTemplateReferenceDialog();
                return;
            }
            if (action === "mc-open-followup") {
                openFollowUpDialog();
                return;
            }
            if (action === "mc-remove-meeting") {
                removeMeetingFromDraft();
                return;
            }
            if (action === "mc-download-meeting") {
                // 附件卡下载：默认 anchor 下载（href=blob）；sending/无快照时拦截
                if (button && button.getAttribute && button.getAttribute("aria-disabled") === "true") {
                    if (event && typeof event.preventDefault === "function") event.preventDefault();
                }
                return;
            }
            if (action === "mc-download-sent-meeting") {
                if (event && typeof event.preventDefault === "function") event.preventDefault();
                downloadSentMeetingAttachment(button);
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
            if (action === "mc-upload-attachment") {
                openOutboundFilePicker();
                return;
            }
            if (action === "mc-remove-attachment") {
                const card = typeof button.closest === "function" ? button.closest(".outbound-file") : null;
                removeOutboundAttachment(card && card.dataset ? card.dataset.fileKey : "");
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
            if (action === "mc-close-followup") {
                closeFollowUpDialog({ restoreFocus: true });
                return;
            }
            if (action === "mc-select-followup") {
                renderFollowUpSelection(button.dataset ? button.dataset.mailRecordId : null);
                return;
            }
            if (action === "mc-select-followup-copy") {
                renderFollowUpCopy(button.dataset ? button.dataset.followupCopy : null);
                return;
            }
            if (action === "mc-apply-followup") {
                applyFollowUpFromDialog();
                return;
            }
            if (action === "mc-close-material-request") {
                closeMaterialRequestDialog({ restoreFocus: true });
                return;
            }
            if (action === "mc-apply-material-request") {
                applyMaterialRequestFromDialog();
                return;
            }
            if (action === "mc-close-template-reference") {
                closeTemplateReferenceDialog({ restoreFocus: true });
                return;
            }
            if (action === "mc-select-template-reference") {
                selectTemplateReferenceItem(button.dataset ? button.dataset.templateId : null);
                return;
            }
            if (action === "mc-retry-template-reference") {
                retryTemplateReference();
                return;
            }
            if (action === "mc-apply-template-reference") {
                applyTemplateReference();
                return;
            }
        }

        function onPortalKeyDown(event) {
            if (instance.disposed) return;
            if (event.key === "Escape" && instance.templateReference.open) {
                event.preventDefault();
                closeTemplateReferenceDialog({ restoreFocus: true });
                return;
            }
            if (event.key === "Escape" && instance.materialRequest.open) {
                event.preventDefault();
                closeMaterialRequestDialog({ restoreFocus: true });
                return;
            }
            if (event.key === "Escape" && instance.followup.open) {
                event.preventDefault();
                closeFollowUpDialog({ restoreFocus: true });
                return;
            }
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

        // 02（S-3）：行内原因/处理确认的 Escape 取消必须在 document 层捕获——按钮被原位
        // 替换后焦点落到 body，host 委托收不到该键事件。
        function onDocumentKeyDown(event) {
            if (instance.disposed) return;
            if (event.key !== "Escape") return;
            if (reasonFormSection(null)) {
                if (event.preventDefault) event.preventDefault();
                cancelReasonForm();
                return;
            }
            if (instance.processConfirm.key != null) {
                if (event.preventDefault) event.preventDefault();
                cancelProcessConfirm();
            }
        }

        function onOutsideFilterClick(event) {
            if (instance.disposed) return;
            const target = event.target;
            if (!target) return;
            // 02（S-2/I-5）：状态菜单外部点击关闭；触发按钮自身的点击由 onClick 处理。
            if (instance.progressMenu.contactId != null) {
                const menu = instance.progressMenu.el;
                const trigger = instance.progressMenu.trigger;
                const inMenu = menu && menu.contains && menu.contains(target);
                const inTrigger = trigger && trigger.contains && trigger.contains(target);
                if (!inMenu && !inTrigger) closeProgressMenu({ restoreFocus: false });
            }
            if (!instance.popoverOpen) return;
            const popover = host.querySelector ? host.querySelector("#mcFilterPopover") : null;
            const toggle = host.querySelector ? host.querySelector('[data-action="mc-more-filters"]') : null;
            if (!popover) return;
            if (popover.contains && popover.contains(target)) return;
            if (toggle && toggle.contains && toggle.contains(target)) return;
            closeFilterPopover({ restore: true, focusButton: true });
        }

        function onKeyDown(event) {
            if (instance.disposed) return;
            const target = event.target;
            if (!target) return;
            // 02（S-2/I-5）：状态菜单键盘行为——打开焦点首项；方向键/Home/End 移动；
            // Enter/Space 使用按钮点击；Escape 关闭回触发按钮；Tab 关闭且焦点不留隐藏项。
            if (instance.progressMenu.contactId != null) {
                const menu = instance.progressMenu;
                const inMenu = menu.el && menu.el.contains && menu.el.contains(target);
                if (inMenu) {
                    if (event.key === "Escape") {
                        event.preventDefault();
                        closeProgressMenu({ restoreFocus: true });
                        return;
                    }
                    if (event.key === "ArrowDown") {
                        event.preventDefault();
                        focusProgressOption(menu.index + 1);
                        return;
                    }
                    if (event.key === "ArrowUp") {
                        event.preventDefault();
                        focusProgressOption(menu.index - 1);
                        return;
                    }
                    if (event.key === "Home") {
                        event.preventDefault();
                        focusProgressOption(0);
                        return;
                    }
                    if (event.key === "End") {
                        event.preventDefault();
                        focusProgressOption(menu.options.length - 1);
                        return;
                    }
                    if (event.key === "Enter" || event.key === " " || event.key === "Spacebar") {
                        event.preventDefault();
                        const option = target.closest ? target.closest(".mailbox-progress-option") : null;
                        if (option && !option.disabled) {
                            setProgress(option.dataset ? option.dataset.contactId : "", option.dataset ? option.dataset.progress : "");
                        }
                        return;
                    }
                }
                if (event.key === "Tab" && inMenu) {
                    const trigger = menu.trigger;
                    closeProgressMenu({ restoreFocus: false });
                    if (trigger && typeof trigger.focus === "function") trigger.focus();
                    return;
                }
            }
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
            const reasonSection = typeof target.closest === "function"
                ? target.closest(".mailbox-suspend-inline-reason")
                : null;
            if (reasonSection && target.tagName && String(target.tagName).toLowerCase() === "textarea") {
                updateReasonCount(reasonSection, target.value);
                return;
            }
            const searchInput = host.querySelector ? host.querySelector('.mc-search-row input[type="search"]') : null;
            if (searchInput && target === searchInput) {
                clearTimeout(instance.searchTimer);
                const value = target.value || "";
                instance.searchTimer = setTimeout(() => {
                    instance.searchTimer = null;
                    if (instance.disposed) return;
                    if (instance.searchText === value) return;
                    instance.searchText = value;
                    freezeDefaultProbe();
                    instance.list.page = 0;
                    loadList();
                }, SEARCH_DEBOUNCE_MS);
                return;
            }
            const composeEl = manualComposeEl();
            if (composeEl && composeEl.contains && composeEl.contains(target)) {
                handleManualComposeInput(target);
            }
        }

        function onChange(event) {
            if (instance.disposed) return;
            const target = event.target;
            if (!target) return;
            // S-1/I-1：隐藏 input 的选择结果只入草稿队列；取消选择（零文件）不改任何状态。
            if (target.getAttribute && target.getAttribute("data-role") === "outbound-file-input") {
                handleOutboundFileSelection(target);
                return;
            }
            if (!target.id) return;
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
            if (next.sessionUser && String(next.sessionUser) !== instance.user) {
                saveCurrentConversation();
                closeProgressMenu({ restoreFocus: false });
                instance.progressBusy.clear();
                clearSelectedConversation();
                clearUnmatchedState();
                resetSuspensionState();
                loadAuthenticatedUser();
                instance.listSeq += 1;
                instance.focusLocating = false;
                instance.focusHandledContactId = null;
                instance.focusMissedContactId = null;
                instance.options.focus = null;
                instance.user = String(next.sessionUser);
                setMobilePane("list");
                renderConversationEmpty();
            }
            if (next.focus && next.focus.contactId != null) {
                instance.options.focus = { contactId: next.focus.contactId, email: next.focus.email || "" };
                instance.focusHandledContactId = null;
                instance.focusMissedContactId = null;
                setMobilePane("detail");
                instance.focusPaneEpoch = instance.paneEpoch;
            }
            if (next.filters) {
                // 快照完整替换，不能合并残留旧值（I-2）
                const prevAccount = String(instance.filters.accountCode || "");
                saveBeforeScopeChange(prevAccount, next.filters.accountCode);
                instance.filters = Object.assign({}, next.filters);
                meetingCloseDisposeOnAccountScopeChange(prevAccount, String(next.filters.accountCode || ""));
                // 仅初次（用户尚未操作 tab 且默认探测未介入）允许外部 onlyPending 初始化
                if (typeof next.filters.pendingOnly === "boolean" && !instance.chipUserTouched
                    && !instance.initialized && !instance.defaultProbe.active) {
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
            saveCurrentConversation();
            if (instance.mobileMedia) {
                if (typeof instance.mobileMedia.removeEventListener === "function") instance.mobileMedia.removeEventListener("change", onMobileViewportChange);
                else if (typeof instance.mobileMedia.removeListener === "function") instance.mobileMedia.removeListener(onMobileViewportChange);
            }
            // 右栏/根节点清空前必须先归还详情面板 lease（I-5）。
            closeProgressMenu({ restoreFocus: false });
            clearUnmatchedState();
            resetSuspensionState();
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
                doc.removeEventListener("keydown", onDocumentKeyDown);
                doc.removeEventListener("meeting-calendar-changed", onMeetingScheduleChanged);
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
            bindMobileViewport();
            if (instance.options.focus && instance.options.focus.contactId != null) {
                setMobilePane("detail");
                instance.focusPaneEpoch = instance.paneEpoch;
            }
            rememberLegacyFilterTexts();
            ensureFilterFieldsPresent();
            renderFilterChrome();
            syncSearchChrome();
            const doc = docRoot();
            if (doc && typeof doc.addEventListener === "function") {
                doc.addEventListener("click", onOutsideFilterClick);
                doc.addEventListener("keydown", onDocumentKeyDown);
                // fast-p 03（I-1/I-3）：排期写成功后由宿主广播，组件只刷新自己当前 owner
                doc.addEventListener("meeting-calendar-changed", onMeetingScheduleChanged);
            }
            bindFilterEvents();
            loadAuthenticatedUser();
            ensurePortalRoot();
            listenPortal("click", onClickPortal);
            listenPortal("change", onMaterialRequestOptionChange);
            listenPortal("change", onTemplateReferenceChange);
            listenPortal("input", onTemplateReferenceInput);
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
