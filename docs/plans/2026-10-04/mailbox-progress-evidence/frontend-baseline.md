### src/main/resources/static/mailbox-chat.js:50-72
```

    const CHIP_ALL = "all";
    const CHIP_FOLLOWED = "followed";
    const CHIP_REPLIED = "replied";
    const CHIP_PENDING = "pending";
    // 02（I-1/S-1）：新增「已挂起」Tab；固定顺序 全部/关注/待处理/已挂起/已回复/待匹配。
    const CHIP_SUSPENDED = "suspended";
    const CHIP_UNMATCHED = "unmatched";

    const FILTER_CHIPS = [
        { key: CHIP_ALL, label: "全部" },
        { key: CHIP_FOLLOWED, label: "关注" },
        { key: CHIP_PENDING, label: "待处理" },
        { key: CHIP_SUSPENDED, label: "已挂起" },
        { key: CHIP_REPLIED, label: "已回复" },
        { key: CHIP_UNMATCHED, label: "待匹配" }
    ];

    // 02（S-1）：待处理/已挂起各有专家数计数 span；不可用时隐藏，绝不伪造 0。
    const CHIP_COUNT_KEYS = [CHIP_PENDING, CHIP_SUSPENDED];

    const SOURCE_LABELS = {
        INBOUND_PROCESSING: "专家来信",
```

### src/main/resources/static/mailbox-chat.js:1558-1582
```
                ? `查看${item.name || item.email || ""}往来邮件；专家标签：${tagNames.join("、")}`
                : `查看${item.name || item.email || ""}往来邮件`)
                + `；${lastReplyAriaSuffix(lastReplyDisplay(item))}`;
            return `
                <div class="mc-person" data-replied="${instance.chip === CHIP_REPLIED ? "true" : "false"}" data-active="${active ? "true" : "false"}" data-contact-id="${escapeText(item.contactId)}">
                    <button class="mc-person-main" type="button" data-action="mc-select-expert" data-contact-id="${escapeText(item.contactId)}" aria-label="${escapeText(ariaLabel)}"${active ? ' aria-current="true"' : ""}>
                        <span class="mc-person-heading"><strong>${escapeText(item.name || item.email || "-")}</strong></span>
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
                        <button class="mc-follow" type="button" data-action="mc-toggle-follow" data-contact-id="${escapeText(item.contactId)}" aria-label="${item.followed ? "取消关注该专家" : "关注该专家"}" aria-pressed="${item.followed ? "true" : "false"}">${item.followed ? "★" : "☆"}</button>
                        ${instance.chip === CHIP_REPLIED ? `<button class="mc-text-button" type="button" data-action="mc-dismiss-replied" data-contact-id="${escapeText(item.contactId)}" aria-label="将${escapeText(item.name || item.email || "该专家")}移出已回复" title="移出已回复，仍可在全部查看">移出</button>` : ""}
                    </span>${suspensionCardFooterHtml(item)}
                </div>
            `;
        }

        function renderExpertList() {
```

### src/main/resources/static/mailbox-chat.js:2888-2904
```
            const actions = head.querySelector(".mc-actions");
            if (actions) {
                const suspendView = suspensionViewForContact(contactId, summary);
                actions.innerHTML = `
                    ${suspensionDetailButtonHtml(contactId, suspendView)}
                    <button class="button" type="button" data-action="mc-toggle-follow" data-contact-id="${escapeText(contactId)}">${followed ? "★ 已关注" : "☆ 关注"}</button>
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
```

### src/main/resources/static/mailbox-chat.css:1-28
```
.mail-chat{display:grid;grid-template-columns:306px minmax(0,1fr);gap:16px;min-height:480px;height:calc(100dvh - 216px);color:#475569;font-size:12px;line-height:1.6}
.mail-chat *{box-sizing:border-box}
.mail-chat [hidden]{display:none!important}
.mail-chat :is(h2,h3,p){margin:0}
.mail-chat .mc-experts,.mail-chat .mc-conversation{display:flex;flex-direction:column;min-width:0;min-height:0;border:1px solid rgba(15,23,42,.11);border-radius:14px;background:#f8faff;overflow:hidden}
.mail-chat .mc-list-tools{display:flex;flex-direction:column;gap:10px;padding:14px;border-bottom:1px solid #e2e8f0}
.mail-chat .mc-list-tools input{width:100%;height:32px;min-height:32px;margin:0;padding:0 10px;border:1px solid #dce4ef;border-radius:7px;background:#fff;color:#475569;font:inherit}
.mail-chat .mc-list-tools input::placeholder{color:#94a3b8}
.mail-chat .mc-filters{display:flex;flex-wrap:wrap;gap:6px}
.mail-chat .mc-filter{min-height:28px;padding:3px 8px;border:1px solid #dce4ef;border-radius:7px;background:#f8faff;color:#64748b;font:inherit;cursor:pointer}
.mail-chat .mc-filter:hover{border-color:#93b4ec;background:#eff5ff}
.mail-chat .mc-filter:active{background:#dbeafe}
.mail-chat .mc-filter[aria-pressed=true]{border-color:#1e40af;background:#eff5ff;color:#1e40af;font-weight:600}
.mail-chat .mc-filter:disabled{opacity:.45;cursor:not-allowed}
.mail-chat .mc-expert-list{flex:1;min-height:0;overflow:auto;padding:8px;overscroll-behavior:contain}
.mail-chat .mc-person{position:relative;display:grid;grid-template-columns:minmax(0,1fr) 28px;gap:8px;margin-bottom:6px;border:1px solid transparent;border-left:3px solid transparent;border-radius:10px;background:transparent}
.mail-chat .mc-person:hover{background:#eff5ff}
.mail-chat .mc-person[data-active=true]{border-color:#c2d3f2;border-left-color:#3c65cd;background:#eaf1ff}
.mail-chat .mc-person-main{display:flex;flex-direction:column;align-items:stretch;min-width:0;gap:5px;padding:12px 0 12px 10px;border:0;background:transparent;color:#475569;text-align:left;font:inherit;cursor:pointer}
.mail-chat .mc-person-main:active{opacity:.85}
.mail-chat .mc-person-main strong{font-size:13px;font-weight:600;color:#1e293b;overflow:hidden;white-space:nowrap;text-overflow:ellipsis}
.mail-chat .mc-person-main small{font-size:11px;color:#64748b;overflow:hidden;white-space:nowrap;text-overflow:ellipsis}
.mail-chat .mc-person-meta{display:flex;align-items:center;flex-wrap:wrap;gap:6px;font-size:11px}
.mail-chat .mc-follow{align-self:start;margin:10px 4px 0 0;padding:0;width:24px;height:28px;border:0;border-radius:7px;background:transparent;color:#94a3b8;font-size:20px;line-height:1;cursor:pointer}
.mail-chat .mc-follow:hover{background:#fef3c7;color:#b45309}
.mail-chat .mc-follow:active{background:#fde68a}
.mail-chat .mc-follow[aria-pressed=true]{color:#d97706}
.mail-chat .mc-follow:disabled{opacity:.45;cursor:not-allowed}
```

### src/main/resources/static/styles.css:12646-12662
```
/* mailbox-suspension-followup: full tabs and optional editable reason */
@media(min-width:761px){#view-mailbox.mc-refined .mail-chat{grid-template-columns:380px minmax(0,1fr)}}
#view-mailbox.mc-refined .mail-chat .mc-filters{flex-wrap:wrap;overflow-x:visible;row-gap:0}
[data-role=suspension-reason-actions]{display:flex;flex:none;align-items:center;margin-left:auto}
.mailbox-suspend-banner-content strong{overflow-wrap:anywhere}
@media(max-width:760px){.mailbox-suspend-banner{flex-wrap:wrap}[data-role=suspension-reason-actions]{margin-left:auto}.mailbox-suspend-banner-content{flex-basis:calc(100% - 40px)}}

/* mailbox-suspension-contract:start */
#view-mailbox.mc-refined .mc-filters{display:flex;flex-wrap:nowrap;gap:4px;overflow-x:auto;scrollbar-width:thin}
#view-mailbox.mc-refined .mc-filter{flex:0 0 auto;min-height:36px;padding:7px 5px 10px;border:0;border-bottom:2px solid transparent;border-radius:0;background:transparent;color:#7d8ca2;font-size:12px;line-height:1.5;white-space:nowrap}
#view-mailbox.mc-refined .mc-filter:hover{background:#f4f7ff;color:#3762d8}
#view-mailbox.mc-refined .mc-filter:active{background:#e9efff}
#view-mailbox.mc-refined .mc-filter[aria-pressed=true]{background:transparent;border-bottom-color:#3762d8;color:#3762d8;font-weight:600}
#view-mailbox.mc-refined .mc-filter:disabled{opacity:.45;cursor:not-allowed}
#view-mailbox.mc-refined .mc-filter:focus-visible{outline:2px solid #9eb9ff;outline-offset:-2px}
.mailbox-suspend-count{display:inline-flex;align-items:center;justify-content:center;min-width:16px;height:16px;margin-left:3px;padding:0 4px;border-radius:5px;background:#f0f3f8;color:#8a98ab;font-size:10px;font-weight:600;line-height:16px}
#view-mailbox.mc-refined .mc-filter[aria-pressed=true] .mailbox-suspend-count{background:#e9efff;color:#3762d8}
```

## 自查补充：列表失败与后续协调

以下为当前源码逐字摘录；fetchList失败/过期返回null，refreshListWithFallback原实现未先检查null。

`mailbox-chat.js:1737–1760`
```javascript
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
```

`mailbox-chat.js:2428–2445`
```javascript
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

```
