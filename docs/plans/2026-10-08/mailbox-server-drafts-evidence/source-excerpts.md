
## src/main/resources/static/mailbox-chat.js
```text
550:     function sessionUserFromOptions(options) {
551:         const value = options && options.sessionUser ? String(options.sessionUser) : "";
552:         return value || operatorName();
553:     }
554: 
555:     function filterAccountScope(filters) {
556:         const value = filters && filters.accountCode ? String(filters.accountCode) : "";
557:         return value;
558:     }
559: 
560:     // ------------------------------------------------------------------
561:     // 会话缓存（位置/窗口/草稿）
562:     // ------------------------------------------------------------------
563: 
564:     function conversationCacheKey(user, accountScope, contactId) {
565:         return `${user}|${accountScope || ""}|${contactId}`;
566:     }
567: 
568:     function touchSession(key) {
569:         const rec = sessionStore.get(key);
570:         if (!rec) return;
571:         sessionStore.delete(key);
572:         sessionStore.set(key, rec);
573:     }
574: 
575:     function getConversationRecord(user, accountScope, contactId) {
576:         const key = conversationCacheKey(user, accountScope, contactId);
577:         const rec = sessionStore.get(key);
578:         if (rec) {
579:             sessionStore.delete(key);
580:             sessionStore.set(key, rec);
581:             rec.lastUsed = Date.now();
582:         }
583:         return rec || null;
584:     }
585: 
586:     function upsertConversationRecord(user, accountScope, contactId, patch) {
587:         const key = conversationCacheKey(user, accountScope, contactId);
588:         let rec = sessionStore.get(key);
589:         if (!rec) {
590:             rec = {
591:                 key,
592:                 contactId,
593:                 accountScope: accountScope || "",
594:                 items: [],
595:                 nextBefore: null,
596:                 hasMore: false,
597:                 anchorKey: null,
598:                 anchorRelTop: 0,
599:                 scrollTop: 0,
600:                 scrollTopValid: false,
601:                 drafts: new Map(),
602:                 lastUsed: Date.now()
603:             };
604:         }
605:         if (patch) {
606:             if (patch.items) rec.items = patch.items;
607:             if (patch.nextBefore !== undefined) rec.nextBefore = patch.nextBefore;
608:             if (patch.hasMore !== undefined) rec.hasMore = patch.hasMore;
609:             if (patch.anchorKey !== undefined) rec.anchorKey = patch.anchorKey;
610:             if (patch.anchorRelTop !== undefined) rec.anchorRelTop = patch.anchorRelTop;
611:             if (patch.scrollTop !== undefined) rec.scrollTop = patch.scrollTop;
612:             if (patch.scrollTopValid !== undefined) rec.scrollTopValid = patch.scrollTopValid;
613:             if (patch.drafts) rec.drafts = patch.drafts;
614:         }
615:         rec.lastUsed = Date.now();
616:         sessionStore.delete(key);
617:         sessionStore.set(key, rec);
618:         // LRU：超出上限淘汰最旧
619:         if (sessionStore.size > SESSION_CACHE_LIMIT) {
620:             let oldestKey = null;
621:             let oldestTs = Infinity;
622:             sessionStore.forEach((entry, entryKey) => {
623:                 if (entry.lastUsed < oldestTs) {
624:                     oldestTs = entry.lastUsed;
625:                     oldestKey = entryKey;
626:                 }
627:             });
628:             if (oldestKey !== null && oldestKey !== key) sessionStore.delete(oldestKey);
629:         }
630:         return rec;
631:     }
632: 
633:     function dropConversationRecord(user, accountScope, contactId) {
634:         sessionStore.delete(conversationCacheKey(user, accountScope, contactId));
```
```text
826:         function currentDraftsMap() {
827:             if (instance.draftsRef) return instance.draftsRef;
828:             const record = getConversationRecord(instance.user, instance.conversation.accountScope || "", Number(instance.selectedContactId || 0));
829:             if (record) {
830:                 instance.draftsRef = record.drafts;
831:                 return record.drafts;
832:             }
833:             return null;
834:         }
835: 
836:         function ensureDraftsMap() {
837:             let drafts = currentDraftsMap();
838:             if (drafts) return drafts;
839:             const contactId = Number(instance.selectedContactId);
840:             if (!Number.isFinite(contactId) || contactId <= 0) return null;
841:             const scope = instance.conversation.accountScope || "";
842:             const record = upsertConversationRecord(instance.user, scope, contactId, {});
843:             instance.draftsRef = record.drafts;
844:             return record.drafts;
845:         }
846: 
847:         function getDraft(targetKey) {
848:             const drafts = currentDraftsMap();
849:             if (!drafts || !targetKey) return null;
850:             return drafts.get(targetKey) || null;
851:         }
852: 
853:         function setDraft(targetKey, draft) {
854:             const drafts = ensureDraftsMap();
855:             if (!drafts || !targetKey) return;
856:             drafts.set(targetKey, draft);
857:         }
858: 
859:         function deleteDraft(targetKey) {
860:             const drafts = currentDraftsMap();
861:             if (drafts && targetKey) drafts.delete(targetKey);
862:         }
863: 
864:         function setRefined(on) {
865:             const view = viewRoot();
```
```text
1105:             const searchBtn = filterControl("mailboxSearchBtn");
1106:             instance.legacyFilterState = instance.legacyFilterState || {};
1107:             if (tagSelect) instance.legacyFilterState.tagSelectOriginalHtml = tagSelect.innerHTML;
1108:             if (searchBtn) instance.legacyFilterState.searchBtnText = searchBtn.textContent;
1109:             instance.legacyFilterState.textsCaptured = true;
1110:         }
1111: 
1112:         // --------------------------------------------------------------
1113:         // 渲染骨架（S-1 两栏 + S-2 搜索/筛选）
1114:         // --------------------------------------------------------------
1115: 
1116:         function skeletonHtml() {
1117:             const chipButtons = FILTER_CHIPS.map((chip) => {
1118:                 const countSpan = CHIP_COUNT_KEYS.indexOf(chip.key) >= 0
1119:                     ? `<span class="mailbox-suspend-count" data-chip-count="${chip.key}" hidden></span>`
1120:                     : "";
1121:                 return `<button class="mc-filter" type="button" data-action="mc-filter" data-chip="${chip.key}" aria-pressed="${instance.chip === chip.key ? "true" : "false"}">${escapeText(chip.label)}${countSpan}</button>`;
1122:             }).join("");
1123:             return `
1124:                 <div class="mail-chat mobile-core-mailbox" data-mobile-pane="list">
1125:                     <aside class="mc-experts" aria-label="专家会话列表">
1126:                         <div class="mc-list-tools">
1127:                             <div class="mc-search-row">
1128:                                 <input type="search" aria-label="搜索专家" placeholder="搜索专家姓名、邮箱">
1129:                                 <button class="mc-icon" type="button" data-action="mc-more-filters" title="更多筛选" aria-label="更多筛选" aria-expanded="false" aria-controls="mcFilterPopover">⋯<span class="mc-filter-count" hidden></span></button>
1130:                                 <div class="mc-filter-popover" id="mcFilterPopover" role="dialog" aria-label="更多筛选" hidden>
1131:                                     <header><strong>更多筛选</strong><button class="mc-close" type="button" data-action="mc-close-filters" aria-label="关闭筛选">×</button></header>
1132:                                     <div class="mc-filter-fields" id="mailboxFilterFields"></div>
1133:                                     <p class="mc-inline-error" role="alert" hidden></p>
1134:                                     <footer><button class="mc-text-button" type="button" data-action="mc-reset-filters">重置</button><button class="button primary" type="button" id="mailboxSearchBtn">应用筛选</button></footer>
1135:                                 </div>
1136:                             </div>
1137:                             <div class="mc-filters">
1138:                                 ${chipButtons}
1139:                             </div>
1140:                             <div class="mc-filter-summary" hidden></div>
1141:                         </div>
1142:                         <div class="mc-expert-list" aria-live="polite"></div>
1143:                         <div class="mc-pager"></div>
1144:                     </aside>
1145:                     <button type="button" class="button mobile-mailbox-back" data-action="mobile-mailbox-back">返回会话列表</button>
1146:                     <section class="mc-conversation" aria-label="专家往来信件"></section>
1147:                 </div>
1148:             `;
1149:         }
1150: 
1151:         function expertsRoot() {
1152:             return host.querySelector ? host.querySelector(".mc-expert-list") : null;
1153:         }
1154: 
1155:         function pagerRoot() {
1156:             return host.querySelector ? host.querySelector(".mc-pager") : null;
1157:         }
1158: 
1159:         function conversationBody() {
1160:             return host.querySelector ? host.querySelector(".mc-conversation") : null;
1161:         }
1162: 
1163:         function renderSkeleton() {
1164:             host.innerHTML = skeletonHtml();
1165:         }
1166: 
1167:         function conversationVisible() {
1168:             if (instance.mobileMedia && instance.mobileMedia.matches && instance.mobilePane === "list") return false;
1169:             const scroll = scrollEl();
1170:             return !scroll || typeof scroll.getClientRects !== "function" || scroll.getClientRects().length > 0;
1171:         }
1172: 
1173:         function saveCurrentConversation() {
1174:             const cleared = instance.clearedEditorSnapshot;
1175:             const values = readManualValues();
1176:             const key = currentTargetKey();
1177:             // 已发送且已删的草稿仍可能留在 DOM；生命周期采集不能复活它。
1178:             const unchangedSent = cleared && cleared.key === key && cleared.draftsMap === currentDraftsMap()
1179:                 && !getDraft(key) && values && values.subject === cleared.snapshot.subject
1180:                 && values.html === cleared.snapshot.html && values.text === cleared.snapshot.text;
1181:             if (!unchangedSent) saveDraftFromInputs();
1182:             saveConversationState();
1183:         }
1184: 
1185:         function afterPaneFrame(callback) {
1186:             const paneEpoch = instance.paneEpoch;
1187:             const convEpoch = instance.convEpoch;
1188:             const run = () => {
1189:                 if (!instance.disposed && paneEpoch === instance.paneEpoch && convEpoch === instance.convEpoch) callback();
1190:             };
```
```text
1605:                             <button class="mailbox-progress-status" type="button" data-action="mc-progress-menu" data-contact-id="${escapeText(item.contactId)}" data-progress="${def ? escapeText(status) : ""}" aria-haspopup="menu" aria-expanded="false" aria-label="${escapeText(aria)}"${disabled ? " disabled" : ""}>${escapeText(label)}</button>${menuHtml}
1606:                         </span>`;
1607:         }
1608: 
1609:         function renderPerson(item) {
1610:             const pendingCount = Number(item.pendingCount) || 0;
1611:             const latest = item.latestMessage || null;
1612:             const latestLine = latest
1613:                 ? `${latest.direction === "INBOUND" ? "最近来信" : "最近发件"}：${latest.subject || "(无主题)"}`
1614:                 : "暂无往来";
1615:             const accounts = Array.isArray(item.accountCodes) && item.accountCodes.length
1616:                 ? item.accountCodes.join("、")
1617:                 : (item.email || "-");
1618:             const cardName = item.name || item.email || "-";
1619:             const active = instance.selectedContactId != null
1620:                 && String(item.contactId) === String(instance.selectedContactId);
1621:             const tagNames = personTagNames(item);
1622:             const tagLine = tagNames === null
1623:                 ? `<span class="mc-person-tags-unavailable" title="标签暂不可用">标签暂不可用</span>`
1624:                 : (tagNames.length === 0 ? "" : `<span class="mc-person-tags" title="专家标签：${escapeText(tagNames.join("、"))}">${tagNames.map((name) => `<span class="mc-person-tag">${escapeText(name)}</span>`).join("")}</span>`);
1625:             const ariaLabel = (tagNames !== null && tagNames.length > 0
1626:                 ? `查看${item.name || item.email || ""}往来邮件；专家标签：${tagNames.join("、")}`
1627:                 : `查看${item.name || item.email || ""}往来邮件`)
1628:                 + `；${lastReplyAriaSuffix(lastReplyDisplay(item))}`;
1629:             return `
1630:                 <div class="mc-person mailbox-progress-card" data-replied="${instance.chip === CHIP_REPLIED ? "true" : "false"}" data-active="${active ? "true" : "false"}" data-contact-id="${escapeText(item.contactId)}">
1631:                     <button class="mc-person-main" type="button" data-action="mc-select-expert" data-contact-id="${escapeText(item.contactId)}" aria-label="${escapeText(ariaLabel)}"${active ? ' aria-current="true"' : ""}>
1632:                         <span class="mc-person-heading"><strong title="${escapeText(cardName)}">${escapeText(cardName)}</strong></span>
1633:                         <small>${escapeText(accounts)}</small>
1634:                         <small>${escapeText(latestLine)}</small>
1635:                         ${lastReplyListMarkup(item)}
1636:                         <span class="mc-person-meta">
1637:                             <span class="mc-person-counts">收 ${Number(item.receivedCount) || 0} · 发 ${Number(item.sentCount) || 0}</span>
1638:                             ${tagLine}
1639:                         </span>
1640:                         <span class="calendar-summary" data-role="meeting-summary"></span>
1641:                     </button>
1642:                     <span data-role="person-actions">
1643:                         ${progressActionsHtml(item)}
1644:                         ${instance.chip === CHIP_REPLIED ? `<button class="mc-text-button" type="button" data-action="mc-dismiss-replied" data-contact-id="${escapeText(item.contactId)}" aria-label="将${escapeText(item.name || item.email || "该专家")}移出已回复" title="移出已回复，仍可在全部查看">移出</button>` : ""}
1645:                     </span>${suspensionCardFooterHtml(item)}
1646:                 </div>
1647:             `;
1648:         }
1649: 
1650:         function renderExpertList() {
1651:             closeProgressMenu({ restoreFocus: false });
1652:             const root = expertsRoot();
1653:             if (!root) return;
1654:             if (instance.list.error) {
1655:                 root.innerHTML = `<div class="mc-error" role="alert">加载失败，请重试。<button class="button" type="button" data-action="mc-retry-list">重试</button></div>`;
1656:                 return;
1657:             }
1658:             const items = instance.list.items || [];
1659:             if (items.length === 0) {
1660:                 root.innerHTML = `<div class="mc-empty">没有符合条件的专家</div>`;
1661:                 return;
1662:             }
1663:             root.innerHTML = items.map(renderPerson).join("");
1664:             root.querySelectorAll(".mc-person").forEach(applyMeetingSummaryToCard);
1665:         }
1666: 
1667:         // S-2：待匹配邮件卡片（邮件级，无状态菜单/专家标签/收发计数）。
1668:         function renderUnmatchedPerson(item) {
```
```text
1746: 
1747:         function renderPager() {
1748:             const root = pagerRoot();
1749:             if (!root) return;
1750:             const total = Number(instance.list.total) || 0;
1751:             const maxPage = Math.max(0, Math.ceil(total / PAGE_SIZE) - 1);
1752:             const page = instance.list.page;
1753:             const unit = isUnmatchedChip() ? "封" : "位";
1754:             root.innerHTML = `
1755:                 <span>第 ${page + 1}/${maxPage + 1} 页 · 共 ${total} ${unit}</span>
1756:                 <button class="button" type="button" data-action="mc-page-prev"${page <= 0 ? " disabled" : ""}>上一页</button>
1757:                 <button class="button" type="button" data-action="mc-page-next"${page >= maxPage ? " disabled" : ""}>下一页</button>
1758:             `;
1759:         }
1760: 
1761:         function fetchList(extra) {
1762:             const options = extra || {};
1763:             const page = options.page != null ? options.page : instance.list.page;
1764:             instance.listSeq += 1;
1765:             const mySeq = instance.listSeq;
1766:             instance.list.loading = true;
1767:             instance.list.error = "";
1768:             // 请求模式在发出时固化；晚到的另一模式响应由 listSeq 拒绝（I-3）。
1769:             const unmatched = isUnmatchedChip();
1770:             const url = unmatched
1771:                 ? `/api/mail/unmatched-inbound?${unmatchedParams(page).toString()}`
1772:                 : `/api/mail/mailbox/conversations?${conversationsParams(page).toString()}`;
1773:             return hostApi()(url).then((data) => {
1774:                 if (instance.disposed || mySeq !== instance.listSeq) return null;
1775:                 instance.list.items = unmatched
1776:                     ? ((data && Array.isArray(data.records)) ? data.records : [])
1777:                     : ((data && Array.isArray(data.items)) ? data.items : []);
1778:                 instance.list.total = unmatched
1779:                     ? (Number(data && data.totalCount) || 0)
1780:                     : (Number(data && data.total) || 0);
1781:                 instance.list.page = page;
1782:                 instance.list.loading = false;
1783:                 renderList();
1784:                 renderPager();
1785:                 // 02（I-1）：首次 mount 默认 Tab 探测。仅普通（非 focus）新实例、用户尚未
1786:                 // 点击 Tab/筛选、且本次就是暂定的待处理首查时生效；total=0 才切跟进中。
1787:                 instance.initialized = true;
1788:                 if (instance.defaultProbe.active && !instance.defaultProbe.decided
1789:                     && !unmatched && instance.chip === CHIP_PENDING) {
1790:                     instance.defaultProbe.decided = true;
1791:                     instance.defaultProbe.active = false;
1792:                     if ((Number(instance.list.total) || 0) === 0) {
1793:                         instance.chip = CHIP_FOLLOWED;
1794:                         syncChipButtons();
1795:                         instance.list.page = 0;
1796:                         loadChipCounts();
1797:                         return fetchList({ page: 0 });
1798:                     }
1799:                 }
1800:                 if (unmatched) resolveUnmatchedSelection();
1801:                 else {
1802:                     // I-4：列表与已选专家详情取同一份当前行，只替换详情回复时间槽。
1803:                     const selectedId = instance.selectedContactId;
1804:                     if (selectedId != null) {
1805:                         const selectedRow = findSummaryByContactId(selectedId);
1806:                         if (selectedRow) renderLastReplyHeader(selectedRow);
1807:                     }
1808:                     // fast-p 03（I-3）：批量摘要按当前页专家 id，epoch = 本次列表请求
1809:                     loadMeetingSummaries();
1810:                 }
1811:                 loadChipCounts();
1812:                 return data;
1813:             }).catch((err) => {
1814:                 if (instance.disposed || mySeq !== instance.listSeq) return null;
1815:                 instance.list.loading = false;
1816:                 instance.list.error = err && err.message ? err.message : "加载失败";
1817:                 renderList();
1818:                 renderPager();
1819:                 hostShowStatus(
1820:                     unmatched
1821:                         ? `获取待匹配来信失败: ${instance.list.error}`
1822:                         : `获取邮件记录失败: ${instance.list.error}`,
1823:                     "error"
1824:                 );
1825:                 if (unmatched) {
1826:                     clearUnmatchedState();
1827:                     renderUnmatchedEmpty();
1828:                 }
1829:                 return null;
1830:             });
1831:         }
1832: 
1833:         function loadList() {
1834:             return fetchList({ page: instance.list.page }).then((data) => {
1835:                 if (instance.disposed) return data;
1836:                 if (isUnmatchedChip()) resolveUnmatchedSelection();
1837:                 else resolveFocusAndSelection();
1838:                 return data;
1839:             });
1840:         }
1841: 
1842:         // --------------------------------------------------------------
1843:         // 02（T2/T3/I-2/I-3/I-4/I-7）：挂起状态、行内原因、取消/结束、完成提示。
1844:         // 真值与原因只来自 01 GET/PUT/DELETE；状态变更后一律重查，绝不本地自减推断。
```
```text
2800:             return filterAccountScope(instance.filters);
2801:         }
2802: 
2803:         function selectExpert(item, options) {
2804:             const opts = options || {};
2805:             const sameOwner = Number(instance.selectedContactId) === Number(item.contactId)
2806:                 && String(instance.conversation.accountScope || "") === accountFilterFromOptions();
2807:             if (sameOwner && !opts.force && !instance.conversation.error) {
2808:                 if (opts.present !== false) setMobilePane("detail", opts.trigger);
2809:                 return;
2810:             }
2811:             // DOM 草稿必须在切换旧 owner 之前采集。
2812:             saveCurrentConversation();
2813:             if (opts.present !== false) setMobilePane("detail", opts.trigger);
2814:             teardownConversationSubViews();
2815:             instance.pendingPosition = null;
2816:             instance.clearedEditorSnapshot = null;
2817:             instance.selectedContactId = Number(item.contactId);
2818:             instance.selectedSummary = item;
2819:             instance.seq += 1;
2820:             instance.convEpoch += 1;
2821:             const mySeq = instance.seq;
2822:             const myEpoch = instance.convEpoch;
2823:             instance.conversation.accountScope = accountFilterFromOptions();
2824:             instance.conversation.loading = true;
2825:             instance.conversation.error = "";
2826:             instance.conversation.items = [];
2827:             instance.conversation.nextBefore = null;
2828:             instance.conversation.hasMore = false;
2829:             instance.conversation.contact = null;
2830:             instance.manual = {
2831:                 mode: "none",
2832:                 targetProcessingId: null,
2833:                 targetAccountCode: "",
2834:                 targetKey: null,
2835:                 qa: null,
2836:                 busy: false
2837:             };
2838:             instance.logs = { loaded: false };
2839:             instance.dismissedNewInbound = null;
2840:             instance.pendingPrompt = null;
2841:             instance.translations = new Map();
2842:             instance.loadOlderBusy = false;
2843:             instance.draftsRef = null;
2844:             resetSuspensionState();
2845:             renderConversationScaffold();
2846: 
2847:             const contactId = Number(item.contactId);
2848:             loadExpertNote(contactId);
2849:             loadSuspensionState(contactId);
2850:             // c3（I-3）：换专家即作废旧 timing 代次，再按新 contact 取推荐。
2851:             loadContactTiming(contactId);
2852:             const accountFilter = accountFilterFromOptions();
2853:             const msgParams = new URLSearchParams();
2854:             msgParams.set("limit", String(MESSAGE_LIMIT));
2855:             if (accountFilter) msgParams.set("accountCode", accountFilter);
2856:             const contactPromise = hostApi()(`/api/expert-contacts/${contactId}`).catch(() => null);
2857:             const messagesPromise = hostApi()(`/api/mail/mailbox/conversations/${contactId}/messages?${msgParams.toString()}`)
2858:                 .catch(() => null);
2859: 
2860:             Promise.all([contactPromise, messagesPromise]).then(([contact, msgData]) => {
2861:                 if (instance.disposed || mySeq !== instance.seq || myEpoch !== instance.convEpoch) return;
2862:                 instance.conversation.contact = contact && contact.contact ? contact.contact : (contact || null);
2863:                 instance.conversation.accountScope = accountFilter;
2864:                 const serverItems = (msgData && Array.isArray(msgData.items)) ? msgData.items : [];
2865:                 instance.conversation.nextBefore = (msgData && msgData.nextBefore) || null;
2866:                 instance.conversation.hasMore = !!(msgData && msgData.hasMore);
2867:                 const cached = getConversationRecord(instance.user, accountFilter, contactId);
2868:                 if (cached && cached.items && cached.items.length > 0) {
2869:                     // 恢复缓存窗口（保留已加载历史），服务端同 key 状态胜
2870:                     instance.conversation.items = mergeServerIntoWindow(cached.items, serverItems);
2871:                     if (cached.nextBefore && (!instance.conversation.nextBefore || cached.items.length > serverItems.length)) {
2872:                         instance.conversation.nextBefore = cached.nextBefore;
2873:                     }
2874:                     if (cached.hasMore && cached.items.length > serverItems.length) {
2875:                         instance.conversation.hasMore = cached.hasMore;
2876:                     }
2877:                     instance.draftsRef = cached.drafts;
2878:                     instance.conversation.loading = false;
2879:                     renderConversationContent({ restoreRecord: cached });
2880:                 } else {
2881:                     instance.conversation.items = serverItems;
2882:                     instance.conversation.loading = false;
2883:                     renderConversationContent({ locateLatest: true });
2884:                 }
2885:                 // 02（I-3）：时间线就绪后重算完成提示锚点位置。
2886:                 renderSuspensionDetail();
2887:                 if (!opts.skipListReload) {
2888:                     fetchList({ page: instance.list.page });
2889:                 }
2890:             }).catch(() => {
2891:                 if (instance.disposed || mySeq !== instance.seq || myEpoch !== instance.convEpoch) return;
2892:                 const cached = getConversationRecord(instance.user, accountFilter, contactId);
2893:                 if (cached && cached.items && cached.items.length > 0) {
2894:                     instance.conversation.items = cached.items;
2895:                     instance.conversation.nextBefore = cached.nextBefore;
2896:                     instance.conversation.hasMore = cached.hasMore;
2897:                     instance.conversation.loading = false;
2898:                     instance.draftsRef = cached.drafts;
2899:                     renderConversationContent({ restoreRecord: cached });
2900:                 } else {
2901:                     instance.conversation.loading = false;
2902:                     instance.conversation.error = "加载失败";
2903:                     renderConversationContent({});
2904:                 }
2905:                 renderSuspensionDetail();
2906:             });
2907:         }
2908: 
2909:         function mergeServerIntoWindow(windowItems, serverItems) {
2910:             const ordered = [];
```
```text
4780:             const latestInbound = summary.latestInbound || null;
4781:             // 三态（I-1/I-2）：来信（真实 processingId）→ inbound；无来信但至少 1 封真实
4782:             // SENT 出站 → outbound（自由回信锚点资格由服务端再校验）；其余 → unavailable。
4783:             const hasInbound = latestInbound && latestInbound.processingId != null;
4784:             const sentCount = Number(summary.sentCount) || 0;
4785:             const mode = hasInbound ? "inbound" : (sentCount > 0 ? "outbound" : "unavailable");
4786:             const targetProcessingId = hasInbound ? Number(latestInbound.processingId) : null;
4787:             const targetAccount = hasInbound ? (latestInbound.accountCode || "") : "";
4788:             // outbound 草稿 key 固定为 contactId:OUTBOUND:<accountScope>，不依赖可能变化的
4789:             // latest message id（草稿缓存恢复语义）。
4790:             const scope = instance.conversation.accountScope || "";
4791:             const targetKey = hasInbound
4792:                 ? `${Number(instance.selectedContactId)}:${targetProcessingId}:${targetAccount}`
4793:                 : (mode === "outbound" ? `${Number(instance.selectedContactId)}:OUTBOUND:${scope}` : null);
4794:             const targetMsg = hasInbound
4795:                 ? latestInboundMessage()
4796:                 : (mode === "outbound" ? (summary.latestMessage || null) : null);
4797:             // I-1：默认主题只在最新消息确为真实 SENT 出站时由其生成 Re:；失败消息不伪装成锚点。
4798:             const defaultSubject = targetMsg && hasInbound
4799:                 ? chatSubjectPrefill(targetMsg.subject)
4800:                 : (mode === "outbound" && targetMsg && targetMsg.direction === "OUTBOUND" && targetMsg.sendStatus === "SENT"
4801:                     ? chatSubjectPrefill(targetMsg.subject)
4802:                     : "Re:");
4803:             const draft = targetKey != null ? getDraft(targetKey) : null;
4804: 
4805:             instance.manual.mode = mode;
4806:             instance.manual.targetProcessingId = targetProcessingId;
4807:             instance.manual.targetAccountCode = targetAccount;
4808:             instance.manual.targetKey = targetKey;
4809:             instance.manual.qa = draft && draft.qa ? draft.qa : null;
4810:             instance.manual.busy = false;
4811: 
4812:             let manualContent;
```
```text
4910:         function manualComposeHtml(targetKey, processingId, account, draft, defaultSubject, outbound) {
4911:             const isOutbound = outbound === true;
4912:             const subjectValue = draft ? draft.subject : defaultSubject;
4913:             const editorText = draft ? draft.text : "";
4914:             const targetInfo = isOutbound
4915:                 ? `${manualTargetInfoText(null, account)} · 回复最近成功发件线程`
4916:                 : manualTargetInfoText(processingId, account);
4917:             const templateFollowButton = isOutbound
4918:                 ? `<button class="button" type="button" data-action="mc-template-follow" data-contact-id="${escapeText(instance.selectedContactId)}">选择模板发送跟进邮件</button>`
4919:                 : "";
4920:             // 会议确认只用于真实来信目标（inbound）：组件在场且非 outbound 才渲染会议 UI。
4921:             const ui = meetingEnabled() && !isOutbound;
4922:             let editorContent = "";
4923:             if (ui && draft && draft.html && String(draft.html).trim()) {
4924:                 editorContent = meetingRestoreEditorHtml(String(draft.html));
4925:                 if (!editorContent && editorText) editorContent = escapeText(editorText);
4926:             } else if (editorText) {
4927:                 editorContent = escapeText(editorText);
4928:             }
4929:             const meetingTrigger = ui ? meetingTriggerHtml() : "";
4930:             // S-2：材料索取紧随会议按钮之后，仍在跟进按钮之前。
4931:             const materialTrigger = ui ? materialRequestTriggerHtml() : "";
4932:             // S-1（fast-p 01 I-1）：引用模板入口只属于有真实来信的人工回复（outbound=false），
4933:             // 不依赖会议组件；固定在材料索取之后、跟进按钮之前。
4934:             const templateReferenceTrigger = isOutbound
4935:                 ? ""
4936:                 : `<button class="button reply-template-trigger" type="button" data-action="mc-open-template-reference">引用模板</button>`;
4937:             // S-1：跟进按钮只在当前专家确有成功发件时渲染，固定紧随既有会议按钮之后，
4938:             // class 严格为 button（不新增按钮 class、不改会议按钮顺序）。
4939:             const followUpButton = Number(instance.selectedSummary && instance.selectedSummary.sentCount) > 0
4940:                 ? `<button class="button" type="button" data-action="mc-open-followup">↗ 跟进邮件</button>`
4941:                 : "";
4942:             // S-3：草稿携带跟进锚点时，在 .mc-editor 之后、会议附件之前显示所选邮件提示。
4943:             const anchorNote = draft && draft.followUpAnchorMailRecordId != null
4944:                 ? followupAnchorNoteHtml(draft.followUpAnchorMailRecordId)
4945:                 : "";
4946:             const meetingAttachment = ui && meetingCardContainerHtml(draft && draft.meeting ? draft.meeting : null) || "";
4947:             // fast-p 07（S-2）：通用附件草稿卡放在会议附件卡之后、发送 footer 之前。
4948:             const outboundFiles = outboundDraftFilesHtml(draft ? outboundAttachmentDraftOf(draft).items : []);
4949:             return `
4950:                 <div class="mc-compose" data-role="manual-compose" data-target-key="${escapeText(targetKey)}">
4951:                     <label>主题<input aria-label="回复主题" value="${escapeText(subjectValue)}"></label>
4952:                     <div class="mc-editor-tools">
4953:                         <button class="button" type="button" data-action="mc-rich-command" data-command="bold">B</button>
4954:                         <button class="button" type="button" data-action="mc-rich-command" data-command="italic">I</button>
4955:                         <button class="button" type="button" data-action="mc-rich-command" data-command="insertUnorderedList">列表</button>
4956:                         <button class="button" type="button" data-action="mc-rich-command" data-command="createLink">链接</button>
4957:                         <button class="button outbound-upload" type="button" data-action="mc-upload-attachment" title="上传附件" aria-label="上传附件"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false"><path d="m21.44 11.05-9.19 9.19a6 6 0 0 1-8.49-8.49l10.6-10.6a4 4 0 0 1 5.66 5.66L9.41 17.41a2 2 0 0 1-2.83-2.83l9.19-9.19"/></svg></button>
4958:                         <input type="file" data-role="outbound-file-input" multiple hidden>
4959:                         ${meetingTrigger}${materialTrigger}${templateReferenceTrigger}${followUpButton}
4960:                     </div>
4961:                     <div class="mc-editor" contenteditable="true" role="textbox" aria-multiline="true" aria-label="人工回复正文" data-role="mc-editor">${editorContent}</div>
4962:                     ${anchorNote}
4963:                     ${meetingAttachment}
4964:                     ${outboundFiles}
4965:                     <div class="mc-compose-footer">
4966:                         <span data-role="target-info">回复账号与目标来信信息：${targetInfo}</span>
4967:                         ${templateFollowButton}
4968:                         <button class="button primary" type="button" data-action="mc-send-manual">发送人工回复</button>
4969:                     </div>
4970:                 </div>
4971:             `;
4972:         }
4973: 
4974:         function manualFollowUpHtml() {
4975:             return `
4976:                 <div data-role="manual-followup">
4977:                     <div class="mc-note">该专家暂无来信。请使用既有模板发送跟进邮件；系统不会在没有真实来信时伪造可生成的人工富文本回复。</div>
4978:                     <button class="button primary" type="button" data-action="mc-template-follow" data-contact-id="${escapeText(instance.selectedContactId)}">选择模板发送跟进邮件</button>
4979:                 </div>
4980:             `;
4981:         }
4982: 
4983:         function renderLogsBlockInto(scroll) {
4984:             if (!scroll) return;
4985:             scroll.insertAdjacentHTML("beforeend", `
4986:                 <details class="mc-section" data-section="logs">
4987:                     <summary>操作日志</summary>
4988:                     <div class="mc-section-content"><div class="mc-empty">正在加载操作日志…</div></div>
4989:                 </details>
4990:             `);
4991:         }
4992: 
4993:         function loadLogs() {
4994:             if (instance.logs.loaded) return;
4995:             instance.logs.loaded = true;
4996:             const scroll = scrollEl();
4997:             const content = scroll ? scroll.querySelector('.mc-section[data-section="logs"] .mc-section-content') : null;
4998:             if (!content) return;
```
```text
5564:         // ------------------------------------------------------------------
5565:         // fast-p 07（I-1..I-5 / S-1/S-2）：人工回复通用附件。
5566:         //
5567:         // 唯一真值是草稿字段 outboundAttachmentDraft={revision,items}：item 以本地 key
5568:         // 标识（单调序号，同草稿内永不重用），状态 uploading/ready/failed；原始 File 只在
5569:         // 上传期间临时持有，落定后换成只含服务端 metadata 的新对象。仅 ready 条目按选择
5570:         // 顺序作为 attachmentIds 提交；任一 uploading/failed 存在即禁止发送。
5571:         // ------------------------------------------------------------------
5572: 
5573:         const OUTBOUND_STATE_UPLOADING = "uploading";
5574:         const OUTBOUND_STATE_READY = "ready";
5575:         const OUTBOUND_STATE_FAILED = "failed";
5576:         const OUTBOUND_STATE_SENT = "sent";
5577:         /** 与 04 `OutboundAttachmentModels` 同值：只作客户端预检查，服务端仍最终裁决。 */
5578:         const OUTBOUND_MAX_FILE_BYTES = 10 * 1024 * 1024;
5579:         const OUTBOUND_MAX_TOTAL_BYTES = 20 * 1024 * 1024;
5580:         const OUTBOUND_MAX_FILES = 10;
5581:         const OUTBOUND_TEXT_UPLOADING = "上传中…";
5582:         const OUTBOUND_TEXT_FAILED = "上传失败，请重新选择文件";
5583:         const OUTBOUND_TEXT_PENDING = "待发送";
5584:         const OUTBOUND_TEXT_SENT = "已发送";
5585: 
5586:         let outboundFileSeq = 0;
5587: 
5588:         function nextOutboundFileKey() {
5589:             outboundFileSeq += 1;
5590:             return "of-" + outboundFileSeq;
5591:         }
5592: 
5593:         function emptyOutboundAttachmentDraft() {
5594:             return { revision: 0, items: [] };
5595:         }
5596: 
5597:         /**
5598:          * I-2：读草稿的附件字段。缺失/损坏视为空草稿（不抛、不另立真值）；items 为浅拷贝，
5599:          * 增删后必须经 setOutboundAttachmentItems 写回。
5600:          */
5601:         function outboundAttachmentDraftOf(draft) {
5602:             const field = draft ? draft.outboundAttachmentDraft : null;
5603:             const items = field && Array.isArray(field.items) ? field.items : [];
5604:             return {
5605:                 revision: field && Number.isFinite(Number(field.revision)) ? Number(field.revision) : 0,
5606:                 items: items.slice()
5607:             };
5608:         }
5609: 
5610:         /** I-3：ready 条目按选择顺序提交；空数组 = 旧接口形态（payload 省略 attachmentIds）。 */
5611:         function outboundAttachmentIds(draft) {
5612:             return outboundAttachmentDraftOf(draft).items
5613:                 .filter((item) => item && item.state === OUTBOUND_STATE_READY && item.id != null && String(item.id) !== "")
5614:                 .map((item) => String(item.id));
5615:         }
5616: 
5617:         function formatOutboundFileSize(byteLength) {
5618:             return ((Number(byteLength) || 0) / 1024).toFixed(1) + " KB";
5619:         }
5620: 
5621:         function outboundFileMetaText(item) {
5622:             const state = item && item.state ? String(item.state) : OUTBOUND_STATE_READY;
5623:             if (state === OUTBOUND_STATE_UPLOADING) return OUTBOUND_TEXT_UPLOADING;
5624:             if (state === OUTBOUND_STATE_FAILED) return OUTBOUND_TEXT_FAILED;
5625:             const size = formatOutboundFileSize(item && item.byteLength);
```
```text
5709:          * I-2 写回：只落到捕获的 owner Map + targetKey。草稿已被删除/已迁到新目标时返回
5710:          * false —— 迟到的上传回包绝不重建已消失的 key（不复活、不串目标）。
5711:          */
5712:         function setOutboundAttachmentItems(captured, items) {
5713:             if (!captured || !captured.draftsMap) return false;
5714:             const existing = captured.draftsMap.get(captured.targetKey);
5715:             if (!existing) return false;
5716:             const current = outboundAttachmentDraftOf(existing);
5717:             captured.draftsMap.set(captured.targetKey, Object.assign({}, existing, {
5718:                 outboundAttachmentDraft: { revision: current.revision + 1, items: items.slice() },
5719:                 updatedAt: new Date().toISOString()
5720:             }));
5721:             return true;
5722:         }
5723: 
5724:         /** I-3：附件语义变化（选择/移除/替换）使会话 requestId 失效，与正文修改同款。 */
5725:         function invalidateOutboundRequestId(captured) {
5726:             if (!captured || !captured.draftsMap) return;
5727:             const existing = captured.draftsMap.get(captured.targetKey);
5728:             if (!existing || !existing.requestId) return;
5729:             captured.draftsMap.set(captured.targetKey, Object.assign({}, existing, {
5730:                 requestId: null,
5731:                 updatedAt: new Date().toISOString()
5732:             }));
5733:         }
5734: 
5735:         /**
5736:          * I-2：异步发起前捕获 sessionUser/accountScope/contactId/targetKey 与原 draftsMap。
5737:          * 迟到回包只写这份捕获：切专家/切账号后它不再指向任何 live 草稿，故不会污染后来
5738:          * 切换的专家，LRU 淘汰后也不会有任何 live 落点。
5739:          */
5740:         function captureOutboundOwner(targetKey) {
5741:             const contactId = Number(instance.selectedContactId);
5742:             if (!targetKey || !Number.isFinite(contactId) || contactId <= 0) return null;
5743:             if (!getDraft(targetKey)) {
5744:                 saveDraftFromInputs();
5745:                 if (!getDraft(targetKey)) return null;
5746:             }
5747:             const draftsMap = ensureDraftsMap();
5748:             if (!draftsMap || !draftsMap.get(targetKey)) return null;
5749:             const accountScope = instance.conversation.accountScope || "";
5750:             return {
5751:                 targetKey,
5752:                 contactId,
5753:                 accountScope,
5754:                 ownerKey: conversationCacheKey(instance.user, accountScope, contactId),
5755:                 draftsMap
5756:             };
5757:         }
5758: 
5759:         /** 捕获是否仍是当前 owner —— 只决定要不要动 DOM/提示，不决定要不要写草稿。 */
5760:         function outboundOwnerIsCurrent(captured) {
5761:             if (!captured || instance.disposed) return false;
5762:             const contactId = Number(instance.selectedContactId);
5763:             const scope = instance.conversation.accountScope || "";
5764:             if (captured.ownerKey !== conversationCacheKey(instance.user, scope, contactId)) return false;
5765:             return captured.draftsMap === currentDraftsMap();
5766:         }
```
```text
5950:         function manualComposeEl() {
5951:             return host.querySelector ? host.querySelector('[data-role="manual-compose"]') : null;
5952:         }
5953: 
5954:         function manualInputs(composeEl) {
5955:             const root = composeEl || manualComposeEl();
5956:             if (!root || !root.querySelector) return null;
5957:             const subjectInput = root.querySelector('input[aria-label="回复主题"]');
5958:             const editor = root.querySelector('[aria-label="人工回复正文"]');
5959:             if (!subjectInput || !editor) return null;
5960:             return { subjectInput, editor };
5961:         }
5962: 
5963:         function readManualValues(composeEl) {
5964:             const inputs = manualInputs(composeEl);
5965:             if (!inputs) return null;
5966:             return {
5967:                 subject: inputs.subjectInput.value || "",
5968:                 // 草稿持久化 canonical 正文（仅换行收敛，标签与文本逐字保留）。
5969:                 html: normalizeManualRichHtmlLineBreaks(
5970:                     typeof inputs.editor.innerHTML === "string" ? inputs.editor.innerHTML : ""
5971:                 ),
5972:                 text: normalizeManualTextLineBreaks(
5973:                     typeof inputs.editor.innerText === "string" ? inputs.editor.innerText : String(inputs.editor.textContent || "")
5974:                 ),
5975:                 qa: instance.manual.qa ? snapshotQa(instance.manual.qa) : null
5976:             };
5977:         }
5978: 
5979:         function saveDraftFromInputs(extra) {
5980:             const key = currentTargetKey();
5981:             if (!key) return;
5982:             const values = readManualValues();
5983:             if (!values) return;
5984:             const existing = getDraft(key);
5985:             const patch = extra || {};
5986:             // 跟进锚点（I-4）：patch 显式给值才改（null = 清除，如采用可信草稿/应用会议），
5987:             // 其余保存沿用既有草稿值。
5988:             const hasAnchorPatch = Object.prototype.hasOwnProperty.call(patch, "followUpAnchorMailRecordId");
5989:             const anchor = hasAnchorPatch
5990:                 ? (patch.followUpAnchorMailRecordId == null ? null : Number(patch.followUpAnchorMailRecordId))
5991:                 : (existing && existing.followUpAnchorMailRecordId != null
5992:                     ? Number(existing.followUpAnchorMailRecordId)
5993:                     : null);
5994:             // I-4/I-12：主题、正文或锚点相对上次保存有任何变化 → 旧 requestId 失效（置 null，
5995:             // 下次发送生成新值）；逐字未变化（重挂载/程序性重存）保留，保证失败重试仍
5996:             // 收敛到同一 attempt。成功删除草稿时 requestId 一并删除。
5997:             const contentChanged = !existing
5998:                 || existing.subject !== values.subject
5999:                 || existing.html !== values.html
6000:                 || existing.text !== values.text
6001:                 || (existing.followUpAnchorMailRecordId != null
6002:                     ? Number(existing.followUpAnchorMailRecordId)
6003:                     : null) !== anchor;
6004:             const requestId = contentChanged ? null : (existing.requestId || null);
6005:             // 会议快照（T3/S-3）：输入保存时随草稿持久化 —— 无显式 meeting patch 则沿用
6006:             // 既有草稿快照（不清除）；切专家/换 accountScope/target 时 targetKey 隔离，
6007:             // 不会跨目标串会议数据；会议块被手改时调用方经 patch 把 state 置 stale。
6008:             const hasMeetingPatch = Object.prototype.hasOwnProperty.call(patch, "meeting");
6009:             const meeting = hasMeetingPatch
6010:                 ? deepCopyMeeting(patch.meeting)
6011:                 : (existing && existing.meeting ? deepCopyMeeting(existing.meeting) : null);
6012:             const accountPatch = Object.prototype.hasOwnProperty.call(patch, "meetingAccountCode");
6013:             const meetingAccountCode = accountPatch
6014:                 ? String(patch.meetingAccountCode || "")
6015:                 : (existing && existing.meetingAccountCode != null ? String(existing.meetingAccountCode) : "");
6016:             setDraft(key, {
6017:                 subject: values.subject,
6018:                 html: values.html,
6019:                 text: values.text,
6020:                 qa: values.qa,
6021:                 requestId,
6022:                 updatedAt: new Date().toISOString(),
6023:                 meeting,
6024:                 meetingAccountCode,
6025:                 followUpAnchorMailRecordId: anchor,
6026:                 // fast-p 07（I-2）：通用附件是同一份草稿的字段，重建写点必须显式保留
6027:                 // （会议填入/采用草稿/程序性重存都不得清附件）。
6028:                 outboundAttachmentDraft: existing && existing.outboundAttachmentDraft
6029:                     ? existing.outboundAttachmentDraft
6030:                     : emptyOutboundAttachmentDraft()
6031:             });
6032:         }
6033: 
6034:         function snapshotQa(qa) {
6035:             return {
6036:                 ragFactCodes: Array.isArray(qa.ragFactCodes) ? qa.ragFactCodes.slice() : [],
6037:                 ragCorpusFingerprint: qa.ragCorpusFingerprint || "",
6038:                 baselineText: qa.baselineText || ""
6039:             };
6040:         }
6041: 
6042:         // RFC 4122 v4（同 app.js createAiReplyGenerationId 语义）：crypto.randomUUID 可用
6043:         // 时优先；否则回退纯 JS 实现。确定性只用于测试沙箱（无 crypto 时给出可解析 UUID）。
6044:         function createRequestId() {
6045:             if (globalThis && globalThis.crypto && typeof globalThis.crypto.randomUUID === "function") {
6046:                 return globalThis.crypto.randomUUID();
6047:             }
6048:             const bytes = new Uint8Array(16);
6049:             if (globalThis && globalThis.crypto && typeof globalThis.crypto.getRandomValues === "function") {
6050:                 globalThis.crypto.getRandomValues(bytes);
6051:             }
6052:             bytes[6] = (bytes[6] & 0x0f) | 0x40;
6053:             bytes[8] = (bytes[8] & 0x3f) | 0x80;
6054:             const hex = Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");
6055:             return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
6056:         }
6057: 
6058:         // 会话回信发送前取/生成 requestId：草稿已有则复用（失败重试/安全取消收敛同一
6059:         // attempt）；没有则生成并先写回草稿（I-4/I-12）。跟进草稿（I-4）在 inbound 目标上
6060:         // 同样需要 requestId，故判定条件是「outbound 或草稿已带跟进锚点」。
6061:         function ensureOutboundRequestId() {
6062:             const key = currentTargetKey();
6063:             if (!key) return null;
6064:             const existing = getDraft(key);
6065:             const anchored = !!(existing && existing.followUpAnchorMailRecordId != null);
6066:             if (instance.manual.mode !== "outbound" && !anchored) return null;
6067:             if (existing && existing.requestId) return existing.requestId;
6068:             const requestId = createRequestId();
6069:             const values = readManualValues();
6070:             if (values) {
6071:                 setDraft(key, Object.assign({}, existing || {}, {
6072:                     subject: values.subject,
6073:                     html: values.html,
6074:                     text: values.text,
6075:                     qa: values.qa,
6076:                     requestId,
6077:                     updatedAt: new Date().toISOString(),
6078:                     // fast-p 07（I-2）：程序性取 requestId 不丢附件字段。
6079:                     outboundAttachmentDraft: existing && existing.outboundAttachmentDraft
```
```text
7640: 
7641:         function writeDraftWithMeeting(meeting, qaOverride) {
7642:             const key = currentTargetKey();
7643:             if (!key) return null;
7644:             const existing = getDraft(key);
7645:             const qa = qaOverride !== undefined ? qaOverride : (existing && existing.qa ? snapshotQa(existing.qa) : null);
7646:             const next = {
7647:                 subject: existing ? existing.subject : "",
7648:                 html: existing ? existing.html : "",
7649:                 text: existing ? existing.text : "",
7650:                 qa,
7651:                 updatedAt: new Date().toISOString(),
7652:                 meeting: deepCopyMeeting(meeting),
7653:                 meetingAccountCode: meeting ? (instance.manual.targetAccountCode || "") : "",
7654:                 // I-7：应用会议即全文替换为会议正文，跟进锚点必须同时清除。
7655:                 followUpAnchorMailRecordId: null,
7656:                 // fast-p 07（I-2）：会议填入/全量重写正文时通用附件仍是同一份草稿的字段，
7657:                 // 显式保留（会议 ICS 与通用附件互不影响，I-5）。
7658:                 outboundAttachmentDraft: existing && existing.outboundAttachmentDraft
7659:                     ? existing.outboundAttachmentDraft
7660:                     : emptyOutboundAttachmentDraft()
7661:             };
7662:             setDraft(key, next);
7663:             return next;
7664:         }
7665: 
7666:         /** 组件 onApply：返回 false 表示目标/修订不匹配或禁止的追加冲突。 */
7667:         function applyMeetingFromDialog(capturedTargetKey, payload) {
7668:             const key = currentTargetKey();
7669:             if (!key || capturedTargetKey !== key) return false;
7670:             if (!payload || !payload.preview) return false;
7671:             if (Number(payload.capturedEditorRevision) !== Number(instance.meeting.editorRevision)) return false;
7672:             const composeEl = manualComposeEl();
7673:             const inputs = manualInputs(composeEl);
7674:             if (!inputs) return false;
7675:             const lib = meetingLib();
7676:             if (!lib || typeof lib.planMeetingInsertion !== "function") return false;
7677:             const draft = getDraft(key);
7678:             const saved = draft && draft.meeting ? draft.meeting : null;
7679:             const mode = payload.mode === "replace" ? "replace" : "append";
7680:             let plan = null;
7681:             try {
7682:                 plan = lib.planMeetingInsertion(inputs.editor, saved, payload.preview, mode);
7683:             } catch (e) {
7684:                 plan = null;
7685:             }
7686:             if (!plan || !plan.allowed) {
7687:                 if (plan && plan.reason === "hand-edited") {
7688:                     hostShowStatus("会议正文已手动修改；请选择替换整篇正文，或取消后移除日历附件。", "error");
7689:                 } else {
7690:                     hostShowStatus("回复目标已变化，请重新打开会议确认", "error");
7691:                 }
7692:                 return false;
7693:             }
7694:             const block = insertMeetingBlock(inputs.editor, plan.action, plan.blockHtml);
7695:             if (!block) return false;
7696:             if (plan.qaClear) instance.manual.qa = null;
7697:             const preview = payload.preview || {};
7698:             const attachment = preview.attachment || {};
7699:             const revision = meetingRevisionNext();
7700:             const meeting = {
7701:                 input: deepCopyMeeting(preview.meeting || null),
7702:                 preview: {
7703:                     htmlBody: preview.htmlBody || "",
7704:                     textBody: preview.textBody || "",
7705:                     attachment: {
7706:                         filename: attachment.filename || "",
7707:                         contentType: attachment.contentType || "",
7708:                         icsText: attachment.icsText || "",
7709:                         byteLength: Number(attachment.byteLength) || 0,
7710:                         sha256: attachment.sha256 || "",
7711:                         semanticSha256: attachment.semanticSha256 || ""
7712:                     },
7713:                     startUtc: preview.startUtc || "",
7714:                     endUtc: preview.endUtc || "",
7715:                     meetingTime: preview.meetingTime || "",
7716:                     chinaTime: preview.chinaTime || "",
7717:                     durationMinutes: Number(preview.durationMinutes) || 0
7718:                 },
7719:                 blockHtml: typeof block.outerHTML === "string" ? block.outerHTML : "",
7720:                 blockText: meetingNormalizeText(meetingElementText(block)),
7721:                 state: "ready",
7722:                 revision
7723:             };
7724:             const qaValue = plan.qaClear ? null : (draft && draft.qa ? snapshotQa(draft.qa) : instance.manual.qa ? snapshotQa(instance.manual.qa) : null);
7725:             writeDraftWithMeeting(meeting, qaValue);
7726:             instance.meeting.editorRevision += 1;
7727:             refreshMeetingAttachmentCard();
7728:             refreshFollowupAnchorNote();
7729:             saveConversationState();
7730:             hostShowStatus(payload.mode === "replace" ? "会议正文已替换并填入回复" : "会议确认已填入回复草稿，发送前请确认", "ok");
7731:             return true;
7732:         }
7733: 
7734:         function removeMeetingFromDraft() {
7735:             const key = currentTargetKey();
7736:             if (!key) return;
7737:             const meeting = manualMeetingSnapshot();
7738:             if (!meeting) {
7739:                 hostShowStatus("当前没有日历附件", "error");
```
```text
7835:         function sendManualReply() {
7836:             const key = currentTargetKey();
7837:             if (!key || instance.manual.busy) return;
7838:             const composeEl = manualComposeEl();
7839:             const inputs = manualInputs(composeEl);
7840:             if (!inputs) return;
7841:             const subject = (inputs.subjectInput.value || "").trim();
7842:             if (!subject) {
7843:                 hostShowStatus("请输入邮件主题", "error");
7844:                 return;
7845:             }
7846:             const hasBodyHtml = typeof inputs.editor.innerHTML === "string" && inputs.editor.innerHTML.trim();
7847:             if (!hasBodyHtml) {
7848:                 hostShowStatus("请输入邮件正文", "error");
7849:                 return;
7850:             }
7851:             const textBody = normalizeManualTextLineBreaks(
7852:                 typeof inputs.editor.innerText === "string" ? inputs.editor.innerText : String(inputs.editor.textContent || "")
7853:             );
7854:             // I-1/I-3：提交前规范化 HTML（折叠连续 <br> 与空 <p>/<div>），请求体、确认重提与
7855:             // 服务端最终发送门使用同一份 canonical 正文；编辑器 DOM 不改写。
7856:             const htmlBody = normalizeManualRichHtmlLineBreaks(
7857:                 typeof inputs.editor.innerHTML === "string" ? inputs.editor.innerHTML : ""
7858:             );
7859:             const mode = instance.manual.mode;
7860:             // I-8/跟进（I-3）：草稿携带所选锚点时，无论当前 target 是来信还是无来信会话，
7861:             // 都必须走会话级接口并把真实 id 交给服务端重新校验。
7862:             const draftSnapshot = getDraft(key);
7863:             const followUpAnchorId = draftSnapshot && draftSnapshot.followUpAnchorMailRecordId != null
7864:                 ? Number(draftSnapshot.followUpAnchorMailRecordId)
7865:                 : null;
7866:             const conversationSend = mode === "outbound" || followUpAnchorId != null;
7867:             // 会议快照发送（T4）：只属于来信人工回复（inbound）且未选跟进锚点 —— 快照存在且
7868:             // state ready 且当前会议块文本与基线一致才允许带附件发送；outbound/跟进路径无 meeting。
7869:             const meeting = mode === "inbound" && !conversationSend ? manualMeetingSnapshot() : null;
7870:             if (meeting) {
7871:                 if (meeting.state !== "ready") {
7872:                     hostShowStatus("会议正文或回复目标已变化，请编辑会议重新生成，或移除日历附件。", "error");
7873:                     return;
7874:                 }
7875:                 const block = meetingBlockIn(inputs.editor);
7876:                 const blockText = block ? meetingNormalizeText(meetingElementText(block)) : "";
7877:                 if (!block || blockText !== meetingNormalizeText(meeting.blockText)) {
7878:                     hostShowStatus("会议正文已被修改，请编辑会议重新生成后再发送", "error");
7879:                     return;
7880:                 }
7881:             }
7882:             let requestBody = null;
7883:             let processingId = null;
7884:             // fast-p 07（I-3）：只有 ready 条目按选择顺序提交；任一 uploading/failed 禁止发送
7885:             // —— 失败项必须重新选择或移除，绝不静默漏发。
7886:             const attachmentItems = draftSnapshot ? outboundAttachmentDraftOf(draftSnapshot).items : [];
7887:             if (attachmentItems.some((item) => !item || item.state !== OUTBOUND_STATE_READY)) {
7888:                 hostShowStatus("附件正在上传或上传失败，请等待上传完成或移除失败附件后再发送", "error");
7889:                 return;
7890:             }
7891:             const attachmentIds = outboundAttachmentIds(draftSnapshot);
7892:             const sentDraftSnapshot = outboundManualDraftSnapshot(draftSnapshot);
7893:             if (!conversationSend) {
7894:                 // 来信路径：既有 processingId adapter，保留 QA/RAG payload（I-8）。
7895:                 requestBody = {
7896:                     senderAccountCode: null,
7897:                     subject,
7898:                     htmlBody,
7899:                     textBody,
7900:                     operatorName: operatorName()
7901:                 };
7902:                 const qa = instance.manual.qa;
7903:                 if (qa && qa.ragFactCodes && qa.ragFactCodes.length) {
7904:                     requestBody.ragFactCodes = qa.ragFactCodes.slice();
7905:                     requestBody.ragCorpusFingerprint = qa.ragCorpusFingerprint || "";
7906:                     requestBody.edited = textBody.trim() !== normalizeManualTextLineBreaks(qa.baselineText || "").trim();
7907:                 }
7908:                 // 会议字段只进来信人工富文本请求（T4/S-3）
7909:                 if (meeting) {
7910:                     const sha = meeting.preview && meeting.preview.attachment
7911:                         ? String(meeting.preview.attachment.sha256 || "")
7912:                         : "";
7913:                     if (!sha) {
7914:                         hostShowStatus("会议附件信息不完整，请重新预览", "error");
7915:                         return;
7916:                     }
7917:                     requestBody.meeting = deepCopyMeeting(meeting.input);
7918:                     requestBody.previewAttachmentSha256 = sha;
7919:                 }
7920:                 // 07（I-3）：空数组省略字段，兼容未带附件的旧请求形态。
7921:                 if (attachmentIds.length > 0) requestBody.attachmentIds = attachmentIds.slice();
7922:                 processingId = Number(instance.manual.targetProcessingId);
7923:             } else {
7924:                 // 会话回信路径：body 只含 requestId/当前 accountScope/显式锚点/自由正文/确认
7925:                 // 字段；无 processingId/senderAccountCode/QA/RAG/meeting（I-3/I-4/I-6）。
7926:                 const requestId = ensureOutboundRequestId();
7927:                 if (!requestId) {
7928:                     hostShowStatus("无法生成发送请求标识", "error");
7929:                     return;
7930:                 }
7931:                 requestBody = {
7932:                     requestId,
7933:                     accountScope: instance.conversation.accountScope || null,
7934:                     subject,
7935:                     htmlBody,
7936:                     textBody,
7937:                     operatorName: operatorName()
7938:                 };
7939:                 if (followUpAnchorId != null) requestBody.anchorMailRecordId = followUpAnchorId;
7940:                 // 07（I-3）：空数组省略字段，兼容未带附件的旧请求形态。
7941:                 if (attachmentIds.length > 0) requestBody.attachmentIds = attachmentIds.slice();
7942:             }
7943:             // I-2：异步前捕获 draftsMap/owner/key/revision/requestBody；不回调里再取。
7944:             const draftsMap = ensureDraftsMap();
7945:             const contactId = Number(instance.selectedContactId);
7946:             const ownerKey = conversationCacheKey(instance.user, instance.conversation.accountScope || "", contactId);
7947:             const capturedRevision = meeting ? meeting.revision : null;
7948:             const inFlightKey = meeting ? `${ownerKey}|${key}` : null;
7949:             if (inFlightKey && meetingInFlight.has(inFlightKey)) {
7950:                 hostShowStatus("该回复目标已有发送中的会议回复，请稍候", "error");
7951:                 return;
7952:             }
7953:             if (inFlightKey) meetingInFlight.add(inFlightKey);
7954:             // 07（I-3）：带已就绪附件的发送同样锁住当前 owner 的编辑/附件增删；其他专家不受影响。
7955:             const lockedCompose = !!meeting || attachmentItems.length > 0;
7956:             instance.manual.busy = true;
7957:             setSendButtonDisabled(true);
7958:             if (lockedCompose) setManualComposeSending(true);
7959:             const adapter = conversationSend
7960:                 ? hostFn("mcHostSendConversationRichReply")
7961:                 : hostFn("mcHostSendRichReply");
7962:             const request = adapter
7963:                 ? (conversationSend
7964:                     ? adapter(contactId, requestBody)
7965:                     : adapter(processingId, requestBody))
7966:                 : Promise.reject(new Error("发送能力不可用"));
7967:             request.then((sent) => {
7968:                 if (instance.disposed) {
7969:                     if (inFlightKey) meetingInFlight.delete(inFlightKey);
7970:                     return;
7971:                 }
7972:                 const stillCurrent = currentTargetKey() === key;
7973:                 if (lockedCompose && stillCurrent) setManualComposeSending(false);
7974:                 instance.manual.busy = false;
7975:                 refreshSendAvailability();
7976:                 if (inFlightKey) meetingInFlight.delete(inFlightKey);
7977:                 if (!sent) return; // 失败/取消保留全部输入（不清草稿、不改 QA、不删附件）
7978:                 if (meeting) {
7979:                     // 成功只清该份已发送快照（I-2）：captured map + revision 匹配才删；
7980:                     // 已切目标/新草稿一律不动新目标的草稿与 QA。
7981:                     const snapshot = draftsMap.get(key);
7982:                     const currentMeeting = snapshot && snapshot.meeting ? snapshot.meeting : null;
7983:                     if (currentMeeting && Number(currentMeeting.revision) === Number(capturedRevision)) {
7984:                         draftsMap.delete(key);
7985:                         if (stillCurrent) {
7986:                             instance.clearedEditorSnapshot = { key, draftsMap, snapshot: sentDraftSnapshot };
7987:                             instance.manual.qa = null;
7988:                             refreshMeetingAttachmentCard();
7989:                             // 07：该草稿连同通用附件一起被清，卡片同步重建（无文件 → hidden）。
7990:                             refreshOutboundFilesCard();
7991:                         }
7992:                         if (stillCurrent) afterSuccessfulSend(key);
7993:                     } else if (currentMeeting && stillCurrent) {
7994:                         const nextDraft = Object.assign({}, snapshot, {
7995:                             meeting: Object.assign({}, currentMeeting, { state: "stale" }),
7996:                             updatedAt: new Date().toISOString()
7997:                         });
7998:                         draftsMap.set(key, nextDraft);
7999:                         refreshMeetingAttachmentCard();
8000:                     }
8001:                     return;
8002:                 }
8003:                 // 07（I-3）：成功只清捕获 owner 里仍等于发送快照的草稿（主题/正文/有序
8004:                 // 附件 id 全等），发送期间的新编辑或新附件一律保留；已切目标/已换草稿
8005:                 // 绝不删当前 owner 的草稿。
8006:                 const capturedDraft = draftsMap.get(key);
8007:                 const cleared = outboundDraftMatchesSnapshot(capturedDraft, sentDraftSnapshot);
8008:                 if (cleared) draftsMap.delete(key);
8009:                 if (stillCurrent && cleared) {
8010:                     instance.clearedEditorSnapshot = { key, draftsMap, snapshot: sentDraftSnapshot };
8011:                     instance.manual.qa = null;
8012:                     refreshFollowupAnchorNote();
8013:                     refreshOutboundFilesCard();
8014:                 }
8015:                 afterSuccessfulSend(key);
8016:             }).catch(() => {
8017:                 if (instance.disposed) {
8018:                     if (inFlightKey) meetingInFlight.delete(inFlightKey);
8019:                     return;
8020:                 }
8021:                 const stillCurrent = currentTargetKey() === key;
8022:                 if (lockedCompose && stillCurrent) setManualComposeSending(false);
8023:                 instance.manual.busy = false;
8024:                 refreshSendAvailability();
8025:                 if (inFlightKey) meetingInFlight.delete(inFlightKey);
8026:             });
8027:         }
8028: 
8029:         function setSendButtonDisabled(disabled) {
8030:             const composeEl = manualComposeEl();
```
```text
8060:         function refreshConversationQuiet() {
8061:             const contactId = Number(instance.selectedContactId);
8062:             if (!Number.isFinite(contactId) || contactId <= 0) return;
8063:             const myEpoch = instance.convEpoch;
8064:             loadExpertNote(contactId);
8065:             const params = new URLSearchParams();
8066:             params.set("limit", String(MESSAGE_LIMIT));
8067:             const scopeAccount = instance.conversation.accountScope || "";
8068:             if (scopeAccount) params.set("accountCode", scopeAccount);
8069:             hostApi()(`/api/mail/mailbox/conversations/${contactId}/messages?${params.toString()}`).then((msgData) => {
8070:                 if (instance.disposed || myEpoch !== instance.convEpoch) return;
8071:                 const serverItems = (msgData && Array.isArray(msgData.items)) ? msgData.items : [];
8072:                 if (serverItems.length === 0 && (instance.conversation.items || []).length === 0) {
8073:                     instance.conversation.nextBefore = (msgData && msgData.nextBefore) || null;
8074:                     instance.conversation.hasMore = !!(msgData && msgData.hasMore);
8075:                     return;
8076:                 }
8077:                 instance.conversation.items = mergeServerIntoWindow(instance.conversation.items, serverItems);
8078:                 instance.conversation.nextBefore = (msgData && msgData.nextBefore) || null;
8079:                 instance.conversation.hasMore = !!(msgData && msgData.hasMore);
8080:                 renderTimeline();
8081:                 checkInboundChangeQuiet();
8082:                 saveConversationState();
8083:                 // c3（T-1）：现有刷新成功后按同一 contact 重读推荐（渲染只重绘状态行）。
8084:                 loadContactTiming(contactId);
8085:                 // 02（T3.4）：已有会话刷新成功也是挂起状态 GET 的统一入口。
8086:                 loadSuspensionState(contactId);
8087:             }).catch(() => {});
8088:         }
8089: 
8090:         function checkInboundChangeQuiet() {
8091:             const summary = instance.selectedSummary || findSummaryByContactId(instance.selectedContactId);
8092:             if (!summary) return;
8093:             const latest = summary.latestInbound || null;
8094:             const mode = instance.manual.mode;
8095:             if (mode !== "inbound") return;
8096:             const currentProcessing = instance.manual.targetProcessingId;
8097:             const newestProcessing = latest && latest.processingId != null ? Number(latest.processingId) : null;
8098:             if (newestProcessing == null || newestProcessing === currentProcessing) return;
8099:             if (instance.dismissedNewInbound && instance.dismissedNewInbound === `${currentProcessing}:${newestProcessing}`) return;
8100:             const draft = currentTargetKey() ? getDraft(currentTargetKey()) : null;
8101:             const hasEditedDraft = draft && (draft.subject || draft.html || draft.text);
8102:             if (!hasEditedDraft) {
8103:                 // 无已编辑草稿：静默跟随新目标
8104:                 retargetManual(newestProcessing, latest.accountCode || "");
8105:                 return;
8106:             }
8107:             const message = `该专家收到新的来信（#${newestProcessing}，${latest.receivedAt || ""}）。当前草稿仍基于来信 #${currentProcessing}。是否将回复目标切换到新来信？新来信主题将重新预填，正文与已采用回复事实保留；保留原目标请选「取消」。`;
8108:             openDialog("confirm", { message }).then((confirmed) => {
8109:                 if (instance.disposed) return;
8110:                 if (confirmed) {
8111:                     instance.dismissedNewInbound = null;
8112:                     retargetManual(newestProcessing, latest.accountCode || "", { keepBody: true });
8113:                 } else {
8114:                     instance.dismissedNewInbound = `${currentProcessing}:${newestProcessing}`;
8115:                 }
8116:             });
8117:         }
8118: 
8119:         function retargetManual(newProcessingId, newAccount, options) {
8120:             const opts = options || {};
8121:             const oldKey = currentTargetKey();
8122:             const draft = oldKey ? getDraft(oldKey) : null;
8123:             const contactId = Number(instance.selectedContactId);
8124:             const newKey = `${contactId}:${newProcessingId}:${newAccount}`;
8125:             // 目标切换：关闭会议弹窗并撤销 modal URL；meeting 标 stale、保留旧 input 供改
8126:             const meetingController = instance.meeting.controller;
8127:             if (meetingController) {
8128:                 try { meetingController.close({ restoreFocus: false }); } catch (e) { /* noop */ }
8129:             }
8130:             revokeMeetingBlob();
8131:             // fast-p 01（I-7）：回复目标切换即关闭引用模板弹框（旧快照与旧身份全部作废）。
8132:             closeTemplateReferenceDialog({ restoreFocus: false });
8133:             if (draft) {
8134:                 let migrated = Object.assign({}, draft, { subject: "", updatedAt: new Date().toISOString() });
8135:                 if (draft.meeting) {
8136:                     migrated = Object.assign({}, migrated, {
8137:                         meeting: Object.assign({}, draft.meeting, { state: "stale" })
8138:                     });
8139:                 }
8140:                 setDraft(newKey, migrated);
8141:                 if (oldKey && oldKey !== newKey) deleteDraft(oldKey);
8142:             }
8143:             instance.meeting.editorRevision += 1;
8144:             instance.manual.targetProcessingId = Number(newProcessingId);
8145:             instance.manual.targetAccountCode = newAccount || "";
8146:             instance.manual.targetKey = newKey;
8147:             instance.manual.qa = draft && draft.qa ? snapshotQa(draft.qa) : null;
8148:             const composeEl = manualComposeEl();
8149:             if (composeEl) {
8150:                 refreshMeetingAttachmentCard();
8151:                 // 07（I-2）：同专家换回复目标时草稿随 targetKey 迁移，已 ready 附件保留。
8152:                 refreshOutboundFilesCard();
```
```text
8820:             }).catch((err) => {
8821:                 if (instance.disposed) return;
8822:                 instance.loadOlderBusy = false;
8823:                 hostShowStatus(`加载更早信件失败：${err && err.message ? err.message : ""}`, "error");
8824:             });
8825:         }
8826: 
8827:         // --------------------------------------------------------------
8828:         // 实例 API / options
8829:         // --------------------------------------------------------------
8830: 
8831:         function applyOptions(options) {
8832:             const next = options || {};
8833:             if (next.sessionUser && String(next.sessionUser) !== instance.user) {
8834:                 saveCurrentConversation();
8835:                 closeProgressMenu({ restoreFocus: false });
8836:                 instance.progressBusy.clear();
8837:                 clearSelectedConversation();
8838:                 clearUnmatchedState();
8839:                 resetSuspensionState();
8840:                 loadAuthenticatedUser();
8841:                 instance.listSeq += 1;
8842:                 instance.focusLocating = false;
8843:                 instance.focusHandledContactId = null;
8844:                 instance.focusMissedContactId = null;
8845:                 instance.options.focus = null;
8846:                 instance.user = String(next.sessionUser);
8847:                 setMobilePane("list");
8848:                 renderConversationEmpty();
8849:             }
8850:             if (next.focus && next.focus.contactId != null) {
8851:                 instance.options.focus = { contactId: next.focus.contactId, email: next.focus.email || "" };
8852:                 instance.focusHandledContactId = null;
8853:                 instance.focusMissedContactId = null;
8854:                 setMobilePane("detail");
8855:                 instance.focusPaneEpoch = instance.paneEpoch;
8856:             }
8857:             if (next.filters) {
8858:                 // 快照完整替换，不能合并残留旧值（I-2）
8859:                 const prevAccount = String(instance.filters.accountCode || "");
8860:                 saveBeforeScopeChange(prevAccount, next.filters.accountCode);
8861:                 instance.filters = Object.assign({}, next.filters);
8862:                 meetingCloseDisposeOnAccountScopeChange(prevAccount, String(next.filters.accountCode || ""));
8863:                 // 仅初次（用户尚未操作 tab 且默认探测未介入）允许外部 onlyPending 初始化
8864:                 if (typeof next.filters.pendingOnly === "boolean" && !instance.chipUserTouched
8865:                     && !instance.initialized && !instance.defaultProbe.active) {
8866:                     if (next.filters.pendingOnly && instance.chip !== CHIP_PENDING) {
8867:                         instance.chip = CHIP_PENDING;
8868:                         syncChipButtons();
8869:                     } else if (!next.filters.pendingOnly && instance.chip === CHIP_PENDING) {
8870:                         instance.chip = CHIP_ALL;
8871:                         syncChipButtons();
8872:                     }
8873:                 }
8874:                 renderFilterChrome();
8875:             }
8876:             return next;
8877:         }
8878: 
8879:         function unmount() {
8880:             if (instance.disposed) return;
8881:             saveCurrentConversation();
8882:             if (instance.mobileMedia) {
8883:                 if (typeof instance.mobileMedia.removeEventListener === "function") instance.mobileMedia.removeEventListener("change", onMobileViewportChange);
8884:                 else if (typeof instance.mobileMedia.removeListener === "function") instance.mobileMedia.removeListener(onMobileViewportChange);
8885:             }
8886:             // 右栏/根节点清空前必须先归还详情面板 lease（I-5）。
8887:             closeProgressMenu({ restoreFocus: false });
8888:             clearUnmatchedState();
8889:             resetSuspensionState();
8890:             instance.disposed = true;
8891:             clearTimeout(instance.searchTimer);
8892:             clearTimeout(instance.saveTimer);
8893:             teardownConversationSubViews();
8894:             handlers.forEach((pair) => host.removeEventListener(pair[0], pair[1]));
8895:             handlers.length = 0;
8896:             portalHandlers.forEach((pair) => {
8897:                 const [root, type, fn] = pair;
8898:                 if (root && typeof root.removeEventListener === "function") root.removeEventListener(type, fn);
8899:             });
8900:             portalHandlers.length = 0;
8901:             const doc = docRoot();
8902:             if (doc && typeof doc.removeEventListener === "function") {
8903:                 doc.removeEventListener("click", onOutsideFilterClick);
8904:                 doc.removeEventListener("keydown", onDocumentKeyDown);
8905:                 doc.removeEventListener("meeting-calendar-changed", onMeetingScheduleChanged);
8906:             }
8907:             restoreRefreshButton();
8908:             restoreLegacyFilterNodes();
```

## src/main/resources/static/meeting-confirmation.js
```text
245:     // ------------------------------------------------------------------
246:     // 草稿/正文 html 白名单清洗（I-6：template.content 遍历；无正则清 html）
247:     // ------------------------------------------------------------------
248: 
249:     var ALLOWED_TAGS = {
250:         div: true, p: true, br: true, b: true, strong: true,
251:         i: true, em: true, u: true, ul: true, ol: true, li: true, a: true, span: true
252:     };
253:     var REMOVE_TAGS = { script: true, style: true, iframe: true, object: true };
254: 
255:     function allowedLinkHref(href) {
256:         var value = String(href == null ? "" : href).trim();
257:         if (/^https?:\/\//i.test(value) || /^mailto:/i.test(value)) return value;
258:         return null;
259:     }
260: 
261:     /** 递归清洗：把 node 清洗后的节点 append 进 out。script/style/iframe/object
262:      *  连内容删除；其它非白名单标签 unwrap；a 只保留校验过协议 href + target/rel；
263:      *  div 只保留 meeting-body-block + data-meeting-block=true，其余属性全删。 */
264:     function sanitizeNode(node, doc, out) {
265:         if (!node) return;
266:         if (node.nodeType === 3) {
267:             if (node.data) out.push(doc.createTextNode(String(node.data)));
268:             return;
269:         }
270:         if (node.nodeType !== 1) return;
271:         var tag = String(node.tagName || "").toLowerCase();
272:         if (REMOVE_TAGS[tag]) return;
273:         var children = node.childNodes ? Array.prototype.slice.call(node.childNodes) : [];
274:         if (!ALLOWED_TAGS[tag]) {
275:             for (var i = 0; i < children.length; i += 1) sanitizeNode(children[i], doc, out);
276:             return;
277:         }
278:         var el = doc.createElement(tag);
279:         if (tag === "a") {
280:             var href = allowedLinkHref(node.getAttribute ? node.getAttribute("href") : "");
281:             if (href) {
282:                 el.setAttribute("href", href);
283:                 el.setAttribute("target", "_blank");
284:                 el.setAttribute("rel", "noopener noreferrer");
285:             }
286:         } else if (tag === "div") {
287:             var cls = node.getAttribute ? node.getAttribute("class") || "" : "";
288:             var blockAttr = node.getAttribute ? node.getAttribute("data-meeting-block") || "" : "";
289:             if (cls.split(/\s+/).indexOf("meeting-body-block") !== -1 && String(blockAttr) === "true") {
290:                 el.setAttribute("class", "meeting-body-block");
291:                 el.setAttribute("data-meeting-block", "true");
292:             }
293:         }
294:         for (var j = 0; j < children.length; j += 1) {
295:             var bucket = [];
296:             sanitizeNode(children[j], doc, bucket);
297:             for (var k = 0; k < bucket.length; k += 1) el.appendChild(bucket[k]);
298:         }
299:         out.push(el);
300:     }
301: 
302:     /** 清洗整段 draft html；返回可安全 innerHTML 的字符串。 */
303:     function sanitizeDraftHtml(html, documentRef) {
304:         var doc = documentRef || docRoot();
305:         var input = String(html == null ? "" : html);
306:         if (!doc || !input) return "";
307:         var template = doc.createElement("template");
308:         template.innerHTML = input;
309:         var root = (template.content && template.content.childNodes) ? template.content : template;
310:         var out = doc.createElement("div");
311:         var children = root.childNodes ? Array.prototype.slice.call(root.childNodes) : [];
312:         for (var i = 0; i < children.length; i += 1) {
313:             var bucket = [];
314:             sanitizeNode(children[i], doc, bucket);
315:             for (var k = 0; k < bucket.length; k += 1) out.appendChild(bucket[k]);
316:         }
317:         return out.innerHTML;
318:     }
319: 
320:     /**
321:      * 插入决策（T1 纯导出）：依据编辑器现状 + 保存快照 + 模式返回动作计划。
322:      *  - action "replace"：整篇替换为会议块（空正文或显式 replace；清 QA）
323:      *  - action "update"：原位更新唯一未手改的会议块（保留其余内容与 QA）
324:      *  - action "append"：正文尾部追加会议块（保留内容与 QA）
325:      *  - allowed=false + reason "hand-edited"：已手改块禁止追加/静默覆盖
```

## src/main/resources/static/app.js
```text
18772:             }
18773:         }
18774:     };
18775: }
18776: 
18777: // I-3/I-4：聊天人工回复发送 —— 沿用 submitManualRichReply 的服务端校验/QA 审计与
18778: // 安全确认文案；不触碰 #unmatchedDetailPanel / manualReplyQaContext 等原流程状态。
18779: async function mcHostSendRichReply(processingId, requestBody) {
18780:     return submitManualRichReply(`/api/mail/unmatched-inbound/${processingId}/manual-rich-reply`, requestBody);
18781: }
18782: 
18783: // T4 (I-3/I-4/I-6)：无来信会话自由回信 —— 同一 URL 提交 + 两级安全确认，只换 endpoint。
18784: // body 只含 requestId/accountScope/自由正文；服务端决定真实锚点与发件账号。
18785: async function mcHostSendConversationRichReply(contactId, requestBody) {
18786:     const id = Number(contactId);
18787:     if (!Number.isFinite(id) || id <= 0) return false;
18788:     return submitManualRichReply(`/api/mail/mailbox/conversations/${id}/manual-rich-reply`, requestBody);
18789: }
18790: 
18791: // 按 URL 提交人工富文本回复并处理两级安全确认（I-9/I-10：失败提示不泄露 SMTP 诊断，
18792: // 安全确认取消返回 false —— 调用方保留草稿与 requestId）。成功提示与归档状态提示
18793: // 对来信/会话两条路径保持同一口径（I-8）。
18794: async function submitManualRichReply(url, requestBody) {
18795:     const submitWithConfirmation = async (body) => {
18796:         try {
18797:             const result = await api(url, {
18798:                 method: "POST",
18799:                 body: JSON.stringify(body)
18800:             });
18801:             const archiveStatus = result?.unsupportedAnswerArchiveStatus || "NOT_APPLICABLE";
18802:             const archivedCount = Number(result?.unsupportedAnswerArchivedCount) || 0;
18803:             if (archiveStatus === "SAVED") {
18804:                 const suffix = archivedCount > 0 ? `，已记录 ${archivedCount} 条无依据回答` : "";
18805:                 alert(`人工回复邮件发送成功${suffix}`);
18806:                 showStatus(`人工回复邮件发送成功${suffix}`, "ok");
18807:             } else if (archiveStatus === "PARTIAL" || archiveStatus === "FAILED") {
18808:                 alert("人工回复邮件发送成功\n无依据回答索引未完整写入，请勿重复发送");
18809:                 showStatus("人工回复邮件发送成功；无依据回答索引未完整写入，请勿重复发送", "warn");
18810:             } else {
18811:                 alert("人工回复邮件发送成功");
18812:             }
18813:             return true;
18814:         } catch (e) {
18815:             const canConfirmSafety = !body.safetyWarningConfirmed
18816:                 && e.data?.code === "MANUAL_SEND_SAFETY_BLOCKED"
18817:                 && Array.isArray(e.data.findings)
18818:                 && e.data.findings.length > 0;
18819:             if (canConfirmSafety) {
18820:                 const findings = e.data.findings;
18821:                 const renderFindings = (list) => list.map((finding) => {
18822:                     const severityClass = finding.severity === "STRONG" ? "ai-reply-error" : "ai-reply-warning";
18823:                     const label = AI_REPLY_WARNING_LABELS[finding.code] || "正文包含需人工核对的风险声明";
18824:                     const coverage = finding.sentence
18825:                         ? `<div class="ai-reply-coverage">命中原句：${escapeHtml(finding.sentence)}</div>`
18826:                         : "";
18827:                     return `<div class="${severityClass}">${escapeHtml(label)}</div>${coverage}`;
18828:                 }).join("");
18829:                 const firstConfirmed = await openActionDialog("confirm", {
18830:                     message: `<p>本次发送命中 ${findings.length} 项内容安全门禁，请逐条核对后确认：</p><div class="ai-reply-feedback">${renderFindings(findings)}</div><p>确认已人工核对，仍要发送吗？</p>`
18831:                 });
18832:                 if (!firstConfirmed) {
18833:                     alert("人工回复发送失败: " + e.message);
18834:                     return false;
18835:                 }
18836:                 let strongConfirmationText = null;
18837:                 if (e.data.requiresStrongConfirmation === true) {
18838:                     const strongFindings = findings.filter((finding) => finding.severity === "STRONG");
18839:                     const secondConfirmed = await openActionDialog("confirm-typed", {
18840:                         message: `<div class="ai-reply-error">高风险：本封邮件正文向专家索取护照 / 身份证 / 在职证明 / 银行流水一类敏感证件材料。此类索取存在合规与信任风险，一经发出不可撤回。</div><div class="ai-reply-feedback">${renderFindings(strongFindings)}</div><p>确认要发送，请在下方输入框中逐字输入「确认发送」四个字。</p>`
18841:                     });
18842:                     if (!secondConfirmed) {
18843:                         alert("人工回复发送失败: " + e.message);
18844:                         return false;
18845:                     }
18846:                     strongConfirmationText = "确认发送";
18847:                 }
18848:                 const retryBody = { ...body, safetyWarningConfirmed: true };
18849:                 if (strongConfirmationText !== null) {
18850:                     retryBody.strongConfirmationText = strongConfirmationText;
18851:                 }
18852:                 return submitWithConfirmation(retryBody);
18853:             }
18854:             alert("人工回复发送失败: " + e.message);
18855:             return false;
18856:         }
18857:     };
18858:     return submitWithConfirmation(requestBody);
18859: }
18860: 
18861: // I-4：无来信专家「选择模板发送跟进邮件」→ 既有专家模板发件流程
18862: // （ManualMailOptionType 仅 COMPOSE_TEMPLATE；command 无自由 subject/body，
18863: //  因此绝不在此伪造自由富文本编辑器或 processingId）。
18864: async function mcHostOpenFollowUp(contactId) {
18865:     const id = Number(contactId);
18866:     if (!Number.isFinite(id) || id <= 0) return;
18867:     await openContactInList(id);
18868: }
18869: 
18870: // fast-p 04 (T4/S-5)：已发送日历原件二进制下载宿主适配。
18871: // relativePath 必须命中 03 固定路由（/api/mail/conversations/{contactId}/
18872: // messages/{mailRecordId}/calendar-attachment，两段 id 均为整数）；filename 必须
18873: // 符合 01 安全文件名（meeting-YYYY-MM-DD-[A-Za-z0-9-]{1,60}.ics，空称呼回退
18874: // expert）。fetch 二进制响应后先 await handleAuthResponse（与 app.js api 同款
18875: // 401/403 处理，任务钻取/登录态语义一致），非 ok 从 JSON.message 读取错误并
18876: // throw，由调用方 hostShowStatus(error.message,"error")；成功用 response.blob()
18877: // 建瞬时 <a hidden download> 节点点击下载，setTimeout(1000) 移除节点并 revoke
18878: // ObjectURL。不用 window.open、不离开当前草稿页、不调用现有 JSON api 解析 ICS。
18879: const CALENDAR_DOWNLOAD_ROUTE = /^\/api\/mail\/conversations\/\d+\/messages\/\d+\/calendar-attachment$/;
18880: const CALENDAR_FILENAME_SAFE = /^meeting-\d{4}-\d{2}-\d{2}-(expert|[A-Za-z0-9-]{1,60})\.ics$/;
18881: 
18882: async function mcHostDownloadCalendar(relativePath, filename) {
18883:     const route = String(relativePath || "");
18884:     const name = String(filename || "");
18885:     if (!CALENDAR_DOWNLOAD_ROUTE.test(route)) {
18886:         throw new Error("日历附件下载地址无效");
18887:     }
18888:     if (!CALENDAR_FILENAME_SAFE.test(name)) {
18889:         throw new Error("日历附件文件名无效");
18890:     }
18891:     const response = await fetch(`${contextPath}${route}`);
18892:     await handleAuthResponse(response);
18893:     if (!response.ok) {
18894:         let message = `${response.status} ${response.statusText}`;
18895:         try {
18896:             const data = await response.json();
18897:             if (data && typeof data.message === "string" && data.message) message = data.message;
18898:         } catch (e) {
18899:             // 非 JSON 错误体：保留 status text
18900:         }
18901:         throw new Error(message);
18902:     }
18903:     const blob = await response.blob();
18904:     const url = URL.createObjectURL(blob);
18905:     const anchor = document.createElement("a");
18906:     anchor.setAttribute("href", url);
18907:     anchor.setAttribute("download", name);
18908:     anchor.hidden = true;
18909:     document.body.appendChild(anchor);
18910:     anchor.click();
```

## src/main/resources/static/mailbox-chat.css
```text
1: .mail-chat{display:grid;grid-template-columns:306px minmax(0,1fr);gap:16px;min-height:480px;height:calc(100dvh - 216px);color:#475569;font-size:12px;line-height:1.6}
2: .mail-chat *{box-sizing:border-box}
3: .mail-chat [hidden]{display:none!important}
4: .mail-chat :is(h2,h3,p){margin:0}
5: .mail-chat .mc-experts,.mail-chat .mc-conversation{display:flex;flex-direction:column;min-width:0;min-height:0;border:1px solid rgba(15,23,42,.11);border-radius:14px;background:#f8faff;overflow:hidden}
6: .mail-chat .mc-list-tools{display:flex;flex-direction:column;gap:10px;padding:14px;border-bottom:1px solid #e2e8f0}
7: .mail-chat .mc-list-tools input{width:100%;height:32px;min-height:32px;margin:0;padding:0 10px;border:1px solid #dce4ef;border-radius:7px;background:#fff;color:#475569;font:inherit}
8: .mail-chat .mc-list-tools input::placeholder{color:#94a3b8}
9: .mail-chat .mc-filters{display:flex;flex-wrap:wrap;gap:6px}
10: .mail-chat .mc-filter{min-height:28px;padding:3px 8px;border:1px solid #dce4ef;border-radius:7px;background:#f8faff;color:#64748b;font:inherit;cursor:pointer}
11: .mail-chat .mc-filter:hover{border-color:#93b4ec;background:#eff5ff}
12: .mail-chat .mc-filter:active{background:#dbeafe}
13: .mail-chat .mc-filter[aria-pressed=true]{border-color:#1e40af;background:#eff5ff;color:#1e40af;font-weight:600}
14: .mail-chat .mc-filter:disabled{opacity:.45;cursor:not-allowed}
15: .mail-chat .mc-expert-list{flex:1;min-height:0;overflow:auto;padding:8px;overscroll-behavior:contain}
16: .mail-chat .mc-person{position:relative;display:grid;grid-template-columns:minmax(0,1fr) 28px;gap:8px;margin-bottom:6px;border:1px solid transparent;border-left:3px solid transparent;border-radius:10px;background:transparent}
17: .mail-chat .mc-person:hover{background:#eff5ff}
18: .mail-chat .mc-person[data-active=true]{border-color:#c2d3f2;border-left-color:#3c65cd;background:#eaf1ff}
19: .mail-chat .mc-person-main{display:flex;flex-direction:column;align-items:stretch;min-width:0;gap:5px;padding:12px 0 12px 10px;border:0;background:transparent;color:#475569;text-align:left;font:inherit;cursor:pointer}
20: .mail-chat .mc-person-main:active{opacity:.85}
21: .mail-chat .mc-person-main strong{font-size:13px;font-weight:600;color:#1e293b;overflow:hidden;white-space:nowrap;text-overflow:ellipsis}
22: .mail-chat .mc-person-main small{font-size:11px;color:#64748b;overflow:hidden;white-space:nowrap;text-overflow:ellipsis}
23: .mail-chat .mc-person-meta{display:flex;align-items:center;flex-wrap:wrap;gap:6px;font-size:11px}
24: .mail-chat .mc-follow{align-self:start;margin:10px 4px 0 0;padding:0;width:24px;height:28px;border:0;border-radius:7px;background:transparent;color:#94a3b8;font-size:20px;line-height:1;cursor:pointer}
25: .mail-chat .mc-follow:hover{background:#fef3c7;color:#b45309}
26: .mail-chat .mc-follow:active{background:#fde68a}
27: .mail-chat .mc-follow[aria-pressed=true]{color:#d97706}
28: .mail-chat .mc-follow:disabled{opacity:.45;cursor:not-allowed}
29: .mail-chat .mc-badge{display:inline-flex;align-items:center;padding:1px 7px;border:1px solid #dce4ef;border-radius:12px;background:#f1f5f9;color:#64748b;font-size:11px;white-space:nowrap}
30: .mail-chat .mc-badge[data-tone=pending]{background:#fff7ed;border-color:#fed7aa;color:#b45309}
31: .mail-chat .mc-badge[data-tone=success]{background:#ecfdf5;border-color:#a7f3d0;color:#059669}
32: .mail-chat .mc-badge[data-tone=error]{background:#fff1f2;border-color:#fecdd3;color:#e11d48}
33: .mail-chat .mc-pager{display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:6px;padding:10px 12px;border-top:1px solid #e2e8f0;color:#64748b;font-size:11px}
34: .mail-chat .mc-header{display:flex;align-items:flex-start;justify-content:space-between;flex-wrap:wrap;gap:12px;padding:16px 18px;border-bottom:1px solid #e2e8f0}
35: .mail-chat .mc-identity{flex:1;min-width:180px}
36: .mail-chat .mc-identity h2{font-size:16px;font-weight:600;color:#1e293b;overflow-wrap:anywhere}
37: .mail-chat .mc-identity p{margin-top:4px;color:#64748b;font-size:11px;overflow-wrap:anywhere}
38: .mail-chat .mc-actions{display:flex;align-items:center;flex-wrap:wrap;gap:8px}
39: .mail-chat .mc-scroll{flex:1;min-height:0;overflow:auto;padding:16px 18px;overscroll-behavior:contain;scrollbar-gutter:stable}
40: .mail-chat .mc-timeline{display:flex;flex-direction:column;gap:14px;margin-bottom:16px}
41: .mail-chat .mc-load-older{align-self:center}
42: .mail-chat .mc-day{align-self:center;color:#94a3b8;font-size:11px;padding:2px 8px}
43: .mail-chat .mc-message{align-self:flex-start;width:min(88%,820px);min-width:0;padding:12px 14px;border:1px solid #dce4ef;border-radius:10px;background:#fff}
44: .mail-chat .mc-message[data-direction=OUTBOUND]{align-self:flex-end;background:#eff5ff;border-color:#cbdcf7}
45: .mail-chat .mc-message header{display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:8px;margin-bottom:8px;color:#64748b;font-size:11px}
46: .mail-chat .mc-message h3{font-size:13px;color:#334155;font-weight:600;margin-bottom:8px;overflow-wrap:anywhere}
47: .mail-chat .mc-body{white-space:pre-wrap;overflow-wrap:anywhere;font-size:12px;line-height:1.8;color:#334155}
48: .mail-chat .mc-message footer{display:flex;justify-content:flex-end;align-items:center;gap:8px;margin-top:10px;color:#64748b;font-size:11px}
49: .mail-chat .mc-mail-extras{margin-top:10px;padding-top:8px;border-top:1px solid #e2e8f0;color:#64748b;font-size:11px}
50: .mail-chat .mc-mail-extras summary{cursor:pointer}
51: .mail-chat .mc-attachment-names{padding-top:6px;overflow-wrap:anywhere;line-height:1.8}
52: .mail-chat .mc-attachment-names>div{overflow:hidden;white-space:nowrap;text-overflow:ellipsis}
53: .mail-chat .mc-section{margin-top:14px;border:1px solid #dce4ef;border-radius:10px;background:#f8faff;overflow:hidden}
54: .mail-chat .mc-section>summary{padding:12px 14px;cursor:pointer;color:#334155;font-size:13px;font-weight:600}
55: .mail-chat .mc-section[open]>summary{border-bottom:1px solid #e2e8f0}
56: .mail-chat .mc-section-content{padding:14px;min-width:0}
57: .mail-chat .mc-note{padding:12px;border:1px solid #dbe7fa;border-radius:7px;background:#eff5ff;color:#64748b;font-size:12px;line-height:1.7}
58: .mail-chat .mc-empty{padding:40px 20px;color:#64748b;text-align:center}
59: .mail-chat .mc-error{padding:12px;border:1px solid #fecdd3;border-radius:7px;background:#fff1f2;color:#be123c}
60: .mail-chat .mc-compose{display:flex;flex-direction:column;gap:10px;min-width:0}
61: .mail-chat .mc-compose label{display:flex;flex-direction:column;gap:6px;color:#64748b;font-size:12px}
62: .mail-chat .mc-compose input{width:100%;height:32px;min-height:32px;padding:0 10px;border:1px solid #dce4ef;border-radius:7px;background:#fff;color:#334155;font:inherit}
63: .mail-chat .mc-editor-tools{display:flex;flex-wrap:wrap;gap:6px}
64: .mail-chat .mc-editor{min-height:160px;max-height:360px;overflow:auto;padding:12px;border:1px solid #dce4ef;border-radius:7px;background:#fff;color:#334155;font-size:12px;line-height:1.8;overflow-wrap:anywhere}
65: .mail-chat .mc-compose-footer{display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:10px}
66: .mail-chat .button{white-space:nowrap}
67: .mail-chat .button:disabled{opacity:.45;cursor:not-allowed;transform:none;box-shadow:none}
68: .mail-chat :is(button,a,input,select,summary,[contenteditable=true]):focus-visible{outline:2px solid #3b82f6;outline-offset:2px}
```
```text
80: .mail-chat .mc-icon{display:inline-flex;align-items:center;justify-content:center;flex:none;min-width:36px;height:36px;padding:0 8px;border:1px solid #d8e1ef;border-radius:8px;background:#fff;color:#6482b2;font:inherit;font-size:20px;cursor:pointer;gap:4px}
81: .mail-chat .mc-icon:hover{background:#edf3ff;border-color:#93b4ec}
82: .mail-chat .mc-icon:active{background:#dbeafe}
83: .mail-chat .mc-icon[aria-expanded=true]{background:#eaf1ff;border-color:#7396df;color:#2451b9}
84: .mail-chat .mc-icon:disabled{opacity:.45;cursor:not-allowed}
85: .mail-chat .mc-filter-count{padding:0 4px;border-radius:8px;background:#2553c5;color:#fff;font-size:10px;line-height:16px}
86: .mail-chat .mc-filters{gap:12px;flex-wrap:nowrap}
87: .mail-chat .mc-filter{border:0;border-bottom:2px solid transparent;border-radius:0;background:transparent;padding:6px 5px 10px}
88: .mail-chat .mc-filter[aria-pressed=true]{border-bottom-color:#2e59c7;background:transparent;color:#244ca9}
89: .mail-chat .mc-filter-summary{display:flex;align-items:center;gap:8px;color:#7890b3;font-size:11px}
90: .mail-chat .mc-filter-popover{position:absolute;top:44px;left:0;z-index:var(--z-dropdown);width:430px;max-width:calc(100vw - 48px);max-height:calc(100dvh - 210px);overflow:auto;background:#fff;border:1px solid #dce5f1;border-radius:12px;box-shadow:0 14px 50px #20395d26}
91: .mail-chat .mc-filter-popover header{display:flex;align-items:center;justify-content:space-between;padding:16px 20px;border-bottom:1px solid #edf1f7;font-size:14px;color:#475d79}
92: .mail-chat .mc-filter-popover .mc-filter-fields{display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1fr);gap:14px;padding:18px 20px}
93: .mail-chat .mc-field{display:flex;flex-direction:column;gap:7px;min-width:0;color:#8494aa;font-size:11px}
94: .mail-chat .mc-field-wide{grid-column:1/-1}
95: .mail-chat .mc-field input,.mail-chat .mc-field select{width:100%;min-width:0;height:34px;min-height:34px;margin:0;padding:0 9px;border:1px solid #dce4ef;border-radius:7px;background:#fcfdff;color:#5f7390;font:inherit;font-size:12px}
96: .mail-chat .mc-filter-popover footer{display:flex;justify-content:flex-end;align-items:center;gap:8px;padding:14px 20px;border-top:1px solid #edf1f7}
97: .mail-chat .mc-filter-popover footer .mc-text-button{margin-right:auto}
98: .mail-chat .mc-close{display:inline-flex;align-items:center;justify-content:center;width:28px;height:28px;border:0;border-radius:5px;background:transparent;color:#91a1b7;font-size:21px;cursor:pointer}
99: .mail-chat .mc-close:hover{background:#edf3ff;color:#2451b9}
100: .mail-chat .mc-close:active{background:#dbeafe}
101: .mail-chat .mc-close:disabled{opacity:.45;cursor:not-allowed}
102: .mail-chat .mc-header{flex:none;background:#fff;padding:17px 22px 14px}
103: .mail-chat .mc-header-meta{display:flex;align-items:center;flex-wrap:wrap;gap:8px;flex-basis:100%;font-size:11px;color:#8a9bb2}
104: .mail-chat .mc-identity h2{font-size:17px;font-weight:600;letter-spacing:-.25px}
105: .mail-chat .mc-badge{border-radius:5px;font-size:10px;line-height:1.6;padding:2px 7px}
106: .mail-chat .mc-badge[data-tone=pending]{background:#fff5e9;border-color:#f6dfc6;color:#bb7838}
107: .mail-chat .mc-timeline-head{flex:none;display:flex;align-items:center;justify-content:space-between;gap:12px;padding:10px 22px;color:#8b9bb1;font-size:11px}
108: .mail-chat .mc-position-hint{margin-left:auto;font-size:10px;color:#8b9bb1}
109: .mail-chat .mc-text-button{display:inline-flex;align-items:center;gap:4px;border:0;border-radius:4px;background:transparent;color:#6482b2;font:inherit;font-size:11px;line-height:1.5;padding:3px 0;cursor:pointer}
110: .mail-chat .mc-text-button:hover{color:#244ca9;background:#edf3ff}
111: .mail-chat .mc-text-button:active{background:#dbeafe}
112: .mail-chat .mc-text-button:disabled{opacity:.45;cursor:not-allowed}
113: .mail-chat .mc-message{width:min(92%,820px);padding:14px 17px;border-radius:11px;box-shadow:0 2px 6px #334b7210}
114: .mail-chat .mc-message h3{font-size:12px;color:#4d617d;font-weight:600;margin-bottom:11px;line-height:1.55}
115: .mail-chat .mc-body{font-size:13px;line-height:1.55;color:#465974}
116: .mail-chat .mc-message footer{justify-content:flex-start;gap:12px;margin-top:12px;padding-top:10px;border-top:1px solid #e7edf5}
117: .mail-chat .mc-process{margin-left:auto;border:1px solid #dce4ef;border-radius:7px;padding:3px 9px;white-space:nowrap}
118: .mail-chat .mc-done{margin-left:auto;color:#4c927b;font-size:11px}
119: .mail-chat .mc-translation{white-space:pre-wrap;overflow-wrap:anywhere;padding:12px 14px;margin-top:12px;border-left:2px solid #b8ccef;border-radius:0 7px 7px 0;background:#f3f7ff;color:#59708f;font-size:12px;line-height:1.85}
120: .mail-chat .mc-tag-row{display:flex;align-items:center;flex-wrap:wrap;gap:6px;margin-top:12px}
121: .mail-chat .mc-tag-row .inbound-tag-chip,.mail-chat .mc-header-meta .expert-tag{font-size:10px;line-height:1.6;padding:2px 6px;border-radius:5px;margin:0}
122: .mail-chat .mc-inline-error{padding:8px 0;color:#be123c;font-size:11px;line-height:1.6}
123: .mail-chat .mc-section{background:#fff;border-radius:10px}
124: .mail-chat .mc-section>summary{display:flex;align-items:center;gap:8px;list-style:none;font-size:12px;font-weight:600;color:#506783}
125: .mail-chat .mc-section>summary::-webkit-details-marker{display:none}
126: .mail-chat .mc-section>summary::after{content:'⌄';margin-left:auto;color:#91a1b7}
127: .mail-chat .mc-section[open]>summary::after{content:'⌃'}
128: .mail-chat .mc-section>summary:hover{background:#f5f8ff}
129: .mail-chat .mc-section>summary:active{background:#edf3ff}
130: .mail-chat .mc-editor{min-height:100px;max-height:240px}
131: .mail-chat .mc-manage-overlay{position:fixed;inset:0;z-index:990;display:flex;align-items:center;justify-content:center;padding:16px;background:#172c4738;backdrop-filter:blur(2px)}
132: .mail-chat .mc-manage-dialog{width:480px;max-width:100%;max-height:85dvh;overflow:auto;background:#fff;color:#475d79;border:1px solid #d9e3f1;border-radius:14px;box-shadow:0 20px 90px #17325730}
```

## src/main/resources/static/styles.css
```text
1: :root {
2:     /* Brand — modern business blue */
3:     --primary: #1e40af;
4:     --primary-hover: #1e3a8a;
5:     --primary-active: #172554;
6:     --primary-rgb: 30, 64, 175;
7:     --primary-light: rgba(var(--primary-rgb), 0.07);
8:     --primary-tint: rgba(var(--primary-rgb), 0.1);
9: 
10:     --bg-main: #f5f7fb;
11:     --bg-sidebar: #ffffff;
12:     --bg-sidebar-hover: rgba(var(--primary-rgb), 0.06);
13:     --bg-sidebar-active: rgba(var(--primary-rgb), 0.09);
14: 
15:     --panel-bg: rgba(255, 255, 255, 0.55);
16:     --panel-border: rgba(15, 23, 42, 0.08);
17:     --line: rgba(15, 23, 42, 0.055);
18:     --border: rgba(15, 23, 42, 0.11);
19:     --surface: rgba(15, 23, 42, 0.022);
20: 
21:     --text-main: #1e293b;
22:     --text-muted: #94a3b8;
23:     --text-sidebar: #64748b;
24:     --text-sidebar-active: #1e293b;
25:     --text-secondary: #475569;
26:     --text-strong: #334155;
27:     --ink: #1e293b;
28:     --bg-subtle: #f8fafc;
29:     --border-strong: #cbd5e1;
30:     --primary-bright: #3b82f6;
31: 
32:     --success: #059669;
33:     --success-rgb: 5, 150, 105;
34:     --success-bg: rgba(var(--success-rgb), 0.08);
35:     --success-border: rgba(var(--success-rgb), 0.18);
36:     --green: var(--success);
37: 
38:     --error: #e11d48;
39:     --error-rgb: 225, 29, 72;
40:     --error-bg: rgba(var(--error-rgb), 0.07);
41:     --error-border: rgba(var(--error-rgb), 0.16);
42:     --error-strong: #be123c;
43:     --red: var(--error);
44: 
45:     --warning: #d97706;
46:     --warning-rgb: 217, 119, 6;
47:     --warning-bg: rgba(var(--warning-rgb), 0.08);
48:     --warning-border: rgba(var(--warning-rgb), 0.2);
49:     --warning-strong: #b45309;
50:     --warning-bright: #f59e0b;
51:     --amber: var(--warning);
52: 
53:     --info: #0ea5e9;
54:     --info-rgb: 14, 165, 233;
55:     --info-bg: rgba(var(--info-rgb), 0.08);
56:     --info-border: rgba(var(--info-rgb), 0.2);
57: 
58:     --z-sticky: 10;
59:     --z-dropdown: 20;
60:     --z-overlay: 50;
61:     --z-drawer: 60;
62:     --z-modal: 1000;
63:     --z-confirm: 1200;
64:     --z-toast: 9999;
65: 
66:     --glass-border: rgba(255, 255, 255, 0.5);
67:     --glass-shadow: 0 8px 32px rgba(var(--primary-rgb), 0.1);
68:     --glass-blur: blur(16px);
69: 
70:     --radius-sm: 7px;
71:     --radius-md: 10px;
72:     --radius-lg: 18px;
73: 
74:     --shadow-sm: 0 1px 2px rgba(15, 23, 42, 0.04);
75:     --shadow-md: 0 1px 3px rgba(15, 23, 42, 0.06), 0 1px 2px rgba(15, 23, 42, 0.03);
```
```text
801: /* Buttons */
802: .button {
803:     display: inline-flex;
804:     align-items: center;
805:     justify-content: center;
806:     gap: 6px;
807:     min-height: 32px;
808:     height: 32px;
809:     padding: 0 12px;
810:     border-radius: var(--radius-sm);
811:     font-weight: 500;
812:     font-size: 12px;
813:     cursor: pointer;
814:     border: 1px solid var(--border);
815:     background-color: transparent;
816:     color: var(--text-main);
817:     transition: transform 0.12s ease, box-shadow 0.15s ease, background-color 0.15s ease, border-color 0.15s ease, opacity 0.1s ease;
818:     outline: none;
819:     user-select: none;
820:     font-family: var(--font-body);
821:     position: relative;
822:     overflow: hidden;
823: }
824: 
825: .button:hover {
826:     border-color: rgba(15, 23, 42, 0.2);
827:     background-color: var(--surface);
828:     transform: translateY(-1px);
829:     box-shadow: 0 2px 6px rgba(15, 23, 42, 0.08);
830: }
831: 
832: .button:active {
833:     transform: translateY(0) scale(0.97);
834:     box-shadow: none;
835:     opacity: 0.85;
836: }
837: 
838: .button.primary {
839:     background-image: linear-gradient(180deg, var(--primary-bright), var(--primary));
840:     background-color: var(--primary);
841:     border-color: transparent;
842:     color: #ffffff;
843:     font-weight: 600;
844:     box-shadow: 0 1px 2px rgba(var(--primary-rgb), 0.4), inset 0 1px 0 rgba(255,255,255,0.18);
845: }
846: 
847: .button.primary:hover {
848:     background-image: linear-gradient(180deg, #2f7bff, var(--primary-hover));
849:     box-shadow: 0 4px 14px rgba(var(--primary-rgb), 0.35), inset 0 1px 0 rgba(255,255,255,0.18);
850: }
851: 
852: .button.secondary {
853:     background-color: var(--primary-light);
854:     border-color: rgba(var(--primary-rgb), 0.12);
855:     color: var(--primary);
856: }
857: 
858: .button.secondary:hover {
859:     background-color: rgba(var(--primary-rgb), 0.1);
860: }
861: 
862: .button.danger {
863:     background-color: var(--error-bg);
864:     border-color: var(--error-border);
865:     color: var(--error);
866: }
867: 
868: .button.danger:hover {
869:     background-color: rgba(var(--error-rgb), 0.1);
870: }
871: 
872: .icon-button {
873:     width: 32px;
874:     height: 32px;
875:     min-width: 32px;
876:     border: 1px solid var(--border);
877:     border-radius: var(--radius-sm);
878:     background-color: transparent;
879:     color: var(--text-muted);
880:     font-size: 18px;
881:     line-height: 1;
882:     display: inline-flex;
883:     align-items: center;
884:     justify-content: center;
885:     cursor: pointer;
886:     transition: var(--transition);
887: }
888: 
889: .icon-button:hover {
890:     color: var(--text-main);
```

## src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt
```text
27: class ManualReplySendAttemptService(
28:     private val attemptRepository: MailSendAttemptRepository,
29:     private val mailRecordRepository: MailRecordRepository,
30:     private val mailRecordQaRuleRepository: MailRecordQaRuleRepository,
31:     private val operatorActionLogService: OperatorActionLogService,
32:     // fast-p 02 (I-2): 成功事务内创建排期的唯一协作件（01 createFromSentMail 要求
33:     // 调用方已在事务中，本方法即调用方）。
34:     private val meetingCalendarService: MeetingCalendarService
35: ) {
36:     companion object {
37:         private val log = LoggerFactory.getLogger(ManualReplySendAttemptService::class.java)
38:         private const val SCHEMA_VERSION = 1
39:         private const val FINGERPRINT_CONTENT_TYPE = "application/x-manual-rich-fingerprint-v1"
40:         private const val MANUAL_RICH_MAIL_TYPE_PREFIX = "MANUAL_RICH:"
41:         private const val MESSAGE_ID_TEMPLATE = "<manual-rich-%s@weibo.com>"
42:         private const val MAX_ERROR_SUMMARY_LENGTH = 500
43:         /** 通用附件指纹段域标记（fast-p 05，I-2）：独立版本段，不复用 calendar 段。 */
44:         private const val OUTBOUND_ATTACHMENTS_SEGMENT = "outbound-attachments-v1"
45:         // 无来信会话自由回信的 attempt 短键：requestId 派生、内容无关（I-4）。
46:         // mail_type 列宽 VARCHAR(50)：前缀 20 字符 + sha256 前 30 位 = 恰好 50。
47:         const val CONVERSATION_REQUEST_PREFIX = "MANUAL_RICH_REQUEST:"
48:         const val CONVERSATION_REQUEST_HEX_LENGTH = 30
49:         /** 会话回信线程/审计锚点类型前缀（真实 mail_record，绝不伪造 inbound id）。 */
50:         const val SOURCE_ANCHOR_PREFIX = "MAIL_RECORD:"
51:     }
52: 
53:     data class SendPayload(
54:         val orcidId: String,
55:         val contactId: Long,
56:         /** 来信路径 = 真实 inbound_mail_processing.id；会话回信路径 = null（I-3）。 */
57:         val inboundProcessingId: Long?,
58:         val accountCode: String,
59:         val normalizedRecipient: String,
60:         val subject: String,
61:         val finalText: String,
62:         val finalHtml: String,
63:         val inReplyTo: String?,
64:         val canonicalQaRuleIds: List<Long>,
65:         val primaryRuleId: Long?,
66:         /**
67:          * 会话回信路径的真实线程锚点（"MAIL_RECORD:<mailRecord.id>"，I-3）。
68:          * 来信路径必须为 null；与 inboundProcessingId 恰有一个非空。
69:          */
70:         val sourceAnchor: String? = null,
71:         /** 会话回信幂等 requestId（可被 UUID.fromString 解析，I-4）；来信路径恒 null。 */
72:         val idempotencyRequestId: String? = null,
73:         /** fast-p 02 (I-2/I-3)：会议日历附件快照；null=无日历（原发送身份字节流
74:          *  完全不变）。非 null 时把语义指纹并入发送身份：同配置不重复发，
75:          *  改时间/链接等语义获得不同发送身份。 */
76:         val calendarAttachment: CalendarAttachmentSnapshot? = null,
77:         /**
78:          * fast-p 02 (I-1/I-2)：与 calendarAttachment 同一次校验产物派生的结构化排期
79:          * 输入（只含已校验 startUtc/endUtc/zoomUrl，不接收浏览器排期对象）。
80:          * 默认 null：普通发送与无会议回信的身份、行为逐字不变；非 null 时
81:          * finalizeSuccess 在成功事务内创建排期，二者同有同无。
82:          * 不进入指纹（时间/链接语义已由 calendarAttachment.semanticSha256 覆盖）。
83:          */
84:         val meetingEvent: MeetingCalendarInput? = null,
85:         /**
86:          * fast-p 05 (I-1/I-2)：本次发送的有序通用附件快照（04
87:          * `OutboundAttachmentService.resolveForSend` 产物，选取顺序；不含上传 UUID/
88:          * 磁盘路径/上传时间）。默认空：不进入指纹、不写新列，普通发送身份与行为逐字
89:          * 不变；非空时追加 outbound-attachments-v1 段，并在两个 finalize 的四分支
90:          * 显式写入本次快照（空即显式 null）。
91:          */
92:         val outboundAttachments: List<OutboundAttachmentSnapshot> = emptyList()
93:     )
94: 
95:     /** findCompletedByRequestId 命中的已完成会话回信（attempt SENT + 唯一 mail_record）。 */
96:     data class CompletedOutboundReply(
97:         val attemptId: Long,
98:         val attemptMessageId: String,
99:         val attemptAccountCode: String,
100:         val mailRecord: MailRecord
101:     )
102: 
103:     data class Fingerprint(
104:         val fullHex: String,
105:         val shortKey: String,
```
```text
215:      * 会话回信幂等收敛（I-4）：按 requestId 短键读取已完成 attempt；仅当 attempt 为 SENT
216:      * 且其唯一 mail_record 存在时返回。其余状态（IN_PROGRESS/UNKNOWN/FAILED…）返回空，
217:      * 由调用方继续既有 claim/碰撞/fail-closed 逻辑（I-10）。
218:      */
219:     fun findCompletedByRequestId(orcidId: String, requestId: String): CompletedOutboundReply? {
220:         val shortKey = conversationRequestShortKey(requestId)
221:         val attempt = attemptRepository.findByOrcidIdAndMailType(orcidId, shortKey) ?: return null
222:         if (attempt.status != MailSendAttemptStatus.SENT) return null
223:         val attemptId = attempt.id ?: return null
224:         val record = mailRecordRepository.findByMailSendAttemptId(attemptId) ?: return null
225:         return CompletedOutboundReply(
226:             attemptId = attemptId,
227:             attemptMessageId = attempt.messageId,
228:             attemptAccountCode = attempt.accountCode,
229:             mailRecord = record
230:         )
231:     }
232: 
233:     @Transactional(propagation = Propagation.REQUIRES_NEW)
234:     fun prepareAndClaim(payload: SendPayload): ClaimedAttempt {
235:         val fingerprint = computeFingerprint(payload)
236:         val now = LocalDateTime.now()
237: 
238:         attemptRepository.insertIgnore(
239:             orcidId = payload.orcidId,
240:             mailType = fingerprint.shortKey,
241:             accountCode = payload.accountCode,
242:             messageId = fingerprint.messageId,
243:             status = MailSendAttemptStatus.PREPARED,
244:             recipient = payload.normalizedRecipient,
245:             subject = payload.subject,
246:             body = fingerprint.fullHex,
247:             contentType = FINGERPRINT_CONTENT_TYPE,
248:             createdAt = now,
249:             updatedAt = now
250:         )
251: 
252:         val attempt = attemptRepository.findByOrcidIdAndMailTypeForUpdate(
253:             payload.orcidId, fingerprint.shortKey
254:         ) ?: throw IllegalStateException(
255:             "Mail send attempt not found after reservation: orcidId=${payload.orcidId} mailType=${fingerprint.shortKey}"
256:         )
257: 
258:         if (attempt.contentType != FINGERPRINT_CONTENT_TYPE) {
259:             throw IllegalArgumentException(
260:                 "Mail send attempt fingerprint collision: unexpected contentType=${attempt.contentType}"
261:             )
262:         }
263:         if (attempt.body != fingerprint.fullHex) {
264:             throw IllegalArgumentException(
265:                 "Mail send attempt fingerprint collision: full hash mismatch"
266:             )
267:         }
268:         if (attempt.recipient != payload.normalizedRecipient) {
269:             throw IllegalArgumentException(
270:                 "Mail send attempt fingerprint collision: recipient mismatch"
271:             )
272:         }
273: 
274:         return when (attempt.status) {
275:             MailSendAttemptStatus.PREPARED -> {
276:                 val affected = attemptRepository.claimStatus(
277:                     requireNotNull(attempt.id),
278:                     MailSendAttemptStatus.PREPARED,
279:                     MailSendAttemptStatus.DELIVERY_IN_PROGRESS,
280:                     now
281:                 )
282:                 if (affected <= 0) {
283:                     throw IllegalStateException("CAS claim failed for attempt ${attempt.id}")
284:                 }
285:                 ClaimedAttempt(
286:                     attemptId = requireNotNull(attempt.id),
287:                     messageId = attempt.messageId,
288:                     result = ClaimResult.CLAIMED
289:                 )
290:             }
291: 
292:             MailSendAttemptStatus.SENT ->
293:                 ClaimedAttempt(
294:                     attemptId = requireNotNull(attempt.id),
295:                     messageId = attempt.messageId,
296:                     result = ClaimResult.DEDUP_SENT
297:                 )
298: 
299:             MailSendAttemptStatus.FAILED_SAFE_TO_RETRY -> {
300:                 val affected = attemptRepository.claimStatus(
301:                     requireNotNull(attempt.id),
302:                     MailSendAttemptStatus.FAILED_SAFE_TO_RETRY,
303:                     MailSendAttemptStatus.DELIVERY_IN_PROGRESS,
304:                     now
305:                 )
306:                 if (affected <= 0) {
307:                     throw IllegalStateException("CAS claim for safe retry failed for attempt ${attempt.id}")
308:                 }
309:                 ClaimedAttempt(
310:                     attemptId = requireNotNull(attempt.id),
311:                     messageId = attempt.messageId,
312:                     result = ClaimResult.SAFE_RETRY_CLAIMED
313:                 )
314:             }
315: 
316:             MailSendAttemptStatus.DELIVERY_IN_PROGRESS ->
317:                 ClaimedAttempt(
318:                     attemptId = requireNotNull(attempt.id),
319:                     messageId = attempt.messageId,
320:                     result = ClaimResult.IN_PROGRESS
321:                 )
322: 
323:             MailSendAttemptStatus.DELIVERY_UNKNOWN ->
324:                 ClaimedAttempt(
325:                     attemptId = requireNotNull(attempt.id),
326:                     messageId = attempt.messageId,
327:                     result = ClaimResult.UNKNOWN
328:                 )
329: 
330:             else ->
331:                 ClaimedAttempt(
332:                     attemptId = requireNotNull(attempt.id),
333:                     messageId = attempt.messageId,
334:                     result = ClaimResult.PERMANENT_FAILED
335:                 )
336:         }
337:     }
338: 
339:     @Transactional(propagation = Propagation.REQUIRES_NEW)
340:     fun finalizeSuccess(payload: SendPayload, attemptId: Long, messageId: String): Long {
341:         val attempt = attemptRepository.findById(attemptId).orElseThrow {
342:             IllegalStateException("Mail send attempt not found: $attemptId")
343:         }
344:         require(attempt.status == MailSendAttemptStatus.DELIVERY_IN_PROGRESS) {
345:             "Cannot finalize success: attempt $attemptId is not DELIVERY_IN_PROGRESS (current: ${attempt.status})"
346:         }
347: 
348:         val now = LocalDateTime.now()
349:         val bodyText = payload.finalText.ifBlank { null }
350:         val mailBody = bodyText ?: payload.finalHtml
351:         // fast-p 02 (I-1/I-4)：与本次发送同一实例的规范快照 JSON；null 显式清空
352:         // （不沿用安全失败记录的旧附件）。
353:         val snapshotJson = payload.calendarAttachment?.let { CalendarAttachmentCodec.serialize(it) }
354:         // fast-p 05 (I-1/I-4)：本次发送的有序通用附件快照 JSON；空列表显式 null
355:         // （唯一 absence 形态，绝不写 []/空串，也不沿用上一次尝试的旧快照）。
356:         val outboundSnapshotJson = payload.outboundAttachments
357:             .takeIf { it.isNotEmpty() }
358:             ?.let { OutboundAttachmentSnapshotCodec.serialize(it) }
359:         val existingRecord = mailRecordRepository.findByMailSendAttemptId(attemptId)
360: 
361:         val mailRecord = if (existingRecord != null) {
362:             existingRecord.copy(
363:                 senderAccountCode = payload.accountCode,
364:                 messageId = messageId,
365:                 inReplyTo = payload.inReplyTo,
366:                 subject = payload.subject,
367:                 body = mailBody,
368:                 matchedQaRuleId = payload.primaryRuleId,
369:                 sendStatus = "SENT",
370:                 sentAt = now,
371:                 errorSummary = null,
372:                 calendarAttachmentJson = snapshotJson,
373:                 outboundAttachmentsJson = outboundSnapshotJson
374:             )
375:         } else {
376:             MailRecord(
377:                 expertContactId = payload.contactId,
378:                 direction = "OUTBOUND",
379:                 mailType = "MANUAL_RICH_REPLY",
380:                 senderAccountCode = payload.accountCode,
381:                 triggeredBy = TriggeredBy.OPERATOR,
382:                 sourceInboundId = null,
383:                 messageId = messageId,
384:                 inReplyTo = payload.inReplyTo,
385:                 subject = payload.subject,
386:                 body = mailBody,
387:                 matchedQaRuleId = payload.primaryRuleId,
388:                 sendStatus = "SENT",
389:                 receivedAt = null,
390:                 sentAt = now,
391:                 mailSendAttemptId = attemptId,
392:                 createdAt = existingRecord?.createdAt ?: now,
393:                 calendarAttachmentJson = snapshotJson,
394:                 outboundAttachmentsJson = outboundSnapshotJson
395:             )
396:         }
397:         val savedRecord = mailRecordRepository.save(mailRecord)
398:         val mailRecordId = requireNotNull(savedRecord.id)
399: 
400:         if (payload.canonicalQaRuleIds.isNotEmpty()) {
401:             payload.canonicalQaRuleIds.forEachIndexed { ordinal, qaRuleId ->
402:                 mailRecordQaRuleRepository.save(
403:                     MailRecordQaRule(
404:                         mailRecordId = mailRecordId,
405:                         qaRuleId = qaRuleId,
406:                         ordinal = ordinal
407:                     )
408:                 )
409:             }
410:         }
411: 
412:         // fast-p 02 (I-2)：真实 SENT mail_record 取得 id 之后、attempt 标 SENT 之前创建
413:         // 排期 —— 与成功落库同一 REQUIRES_NEW 事务提交，二者同成功或同回滚（排期写失败
414:         // 绝不返回发送成功）。普通发送/无会议回信 meetingEvent 为 null，完全跳过。
415:         // 01 createFromSentMail 校验来源邮件（OUTBOUND/MANUAL_RICH_REPLY/SENT/带日历附件）
416:         // 与结构化输入；校验失败同样回滚整个成功事务（不静默吞掉排期写入错误）。
417:         payload.meetingEvent?.let { event ->
418:             meetingCalendarService.createFromSentMail(savedRecord, event)
419:         }
420: 
421:         attemptRepository.updateStatusAndError(
422:             id = attemptId,
423:             status = MailSendAttemptStatus.SENT,
424:             errorSummary = null,
425:             now = now
426:         )
427: 
428:         return mailRecordId
429:     }
430: 
```
```text

```

## src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt
```text
159:     fun sendManualRichReply(
160:         inboundProcessingId: Long,
161:         senderAccountCode: String?,
162:         subject: String,
163:         htmlBody: String,
164:         textBody: String?,
165:         operatorName: String?,
166:         qaRuleIds: List<Long>? = null,
167:         suggestedRuleIds: List<Long>? = null,
168:         ackSnippetId: Long? = null,
169:         edited: Boolean? = null,
170:         freeTextPreview: String? = null,
171:         useVariants: Boolean = false,
172:         templateTextBody: String? = null,
173:         templateHtmlBody: String? = null,
174:         trustReplyAssembly: TrustReplyAssembleRequest? = null,
175:         safetyWarningConfirmed: Boolean = false,
176:         strongConfirmationText: String? = null,
177:         // 03b (I-39~I-43): RAG 证据 —— fact_code 字符串列表 + 生成草稿时下发的语料指纹。
178:         // 追加在参数末尾，既有调用点零改动；与 trustReplyAssembly 互斥（I-39）。
179:         ragFactCodes: List<String>? = null,
180:         ragCorpusFingerprint: String? = null,
181:         // 03 (T1/I-1/I-2): 已预览会议配置与预览快照 sha256。meeting 与
182:         // previewAttachmentSha256 必须同时出现/同时为空（否则 400）；非空时在最终变量
183:         // 渲染后用真实 processing/contact/account 走 01 validateAndBuild 重算核对。
184:         meeting: MeetingInput? = null,
185:         previewAttachmentSha256: String? = null,
186:         // 06 (T1/I-1): 通用附件 id（用户选择顺序）与本次请求的真实会话身份。默认空/空身份
187:         // 保持既有内部调用点零改动；非空 id 时身份必须真实（空身份在 claim 前 400），
188:         // 绝不回退请求体里的 operatorName。
189:         attachmentIds: List<String> = emptyList(),
190:         authenticatedUsername: String? = null
191:     ): PendingMailSendResult {
192:         val record = inboundMailProcessingRepository.findById(inboundProcessingId)
193:             .orElseThrow { error("Inbound mail processing not found: $inboundProcessingId") }
194:         val contactId = record.expertContactId
195:             ?: error("Inbound mail not bound to a contact")
196:         val contact = expertContactRepository.findById(contactId)
197:             .orElseThrow { error("Expert contact not found: $contactId") }
198: 
199:         require(subject.isNotBlank()) { "Subject is required" }
200:         require(htmlBody.isNotBlank()) { "HTML body is required" }
201:         val trimmedSubject = subject.trim()
202:         require(trimmedSubject.length <= 255) { "Subject exceeds 255 characters" }
203: 
204:         // 03 (T1/I-1): meeting 与 previewAttachmentSha256 必须同时出现或同时为空。
```
```text
371:     fun sendConversationManualRichReply(
372:         contactId: Long,
373:         requestId: String,
374:         accountScope: String?,
375:         anchorMailRecordId: Long? = null,
376:         subject: String,
377:         htmlBody: String,
378:         textBody: String?,
379:         operatorName: String?,
380:         safetyWarningConfirmed: Boolean = false,
381:         strongConfirmationText: String? = null,
382:         // 06 (T1/I-2)：通用附件 id 与真实会话身份。空/空身份保持既有调用点零改动。
383:         attachmentIds: List<String> = emptyList(),
384:         authenticatedUsername: String? = null
385:     ): PendingMailSendResult {
386:         val contact = expertContactRepository.findById(contactId)
387:             .orElseThrow { error("Expert contact not found: $contactId") }
388:         require(subject.isNotBlank()) { "Subject is required" }
389:         require(htmlBody.isNotBlank()) { "HTML body is required" }
390:         require(subject.trim().length <= 255) { "Subject exceeds 255 characters" }
391:         require(requestId.isNotBlank()) { "requestId is required" }
392:         val canonicalRequestId = try {
393:             manualReplySendAttemptService.canonicalConversationRequestId(requestId)
394:         } catch (ex: IllegalArgumentException) {
395:             throw IllegalArgumentException("requestId must be a valid UUID", ex)
396:         }
397: 
398:         // I-4：已完成 attempt 先收敛 —— SENT 时直接返回原结果，绝不重查锚点/再次投递
399:         // （刚发出的信已成为最新成功发件也不得再次 SMTP）。
400:         // 06 (I-2)：本次或原记录任何一侧带通用附件时，先用 04 批量元数据校验归属
401:         // （同专家 + 同上传者，不要求原件仍在线），再按 filename/type/size/hash 有序语义
402:         // 比较：相同返回原 SENT；不同固定 409，绝不谎报新附件已发送、也不换 requestId。
403:         val completed = manualReplySendAttemptService.findCompletedByRequestId(
404:             contact.orcidId, canonicalRequestId
405:         )
406:         if (completed != null) {
407:             val record = completed.mailRecord
408:             val originalSnapshots = OutboundAttachmentSnapshotCodec.parseOrThrow(
409:                 record.outboundAttachmentsJson
410:             )
411:             val requestedSnapshots = resolveRequestedAttachmentSnapshots(
412:                 contactId = contactId,
413:                 attachmentIds = attachmentIds,
414:                 authenticatedUsername = authenticatedUsername
415:             )
416:             if ((originalSnapshots != null || requestedSnapshots.isNotEmpty()) &&
417:                 !sameOutboundAttachmentSet(originalSnapshots.orEmpty(), requestedSnapshots)
418:             ) {
419:                 throw OutboundAttachmentException.conflict(CONVERSATION_SENT_ATTACHMENTS_CHANGED)
420:             }
421:             return PendingMailSendResult(
422:                 contactId = contactId,
423:                 senderAccountCode = record.senderAccountCode ?: completed.attemptAccountCode,
424:                 mailType = "MANUAL_RICH_REPLY",
425:                 subject = record.subject ?: subject,
426:                 sendStatus = "SENT",
427:                 messageId = record.messageId ?: completed.attemptMessageId
428:             )
429:         }
430: 
431:         // I-1：真实 SENT 出站锚点（排除空账号/模拟器）；accountScope 非空时只在该账号内找，
432:         // scope 下无成功发件不回退其他账号（I-6）。锚点查询在任何 claim/SMTP 之前。
433:         // 跟进（I-2）：显式 id 必须重读同一行并全校验，前端只作提示、绝不作为权限边界。
434:         val anchor = if (anchorMailRecordId != null) {
435:             mailRecordRepository.findById(anchorMailRecordId)
436:                 .orElse(null)
437:                 ?.takeIf { isConversationSentAnchor(it, contactId, accountScope) }
438:         } else {
439:             mailRecordRepository.findLatestSentOutboundAnchor(
440:                 contactId = contactId,
441:                 accountScope = accountScope?.takeIf { it.isNotBlank() },
442:                 excludedAccountCode = MailSenderAccountService.SIMULATOR_ACCOUNT_CODE
443:             )
444:         } ?: throw ResponseStatusException(
445:             HttpStatus.UNPROCESSABLE_ENTITY,
446:             "CONVERSATION_SENT_ANCHOR_NOT_FOUND"
447:         )
448:         val accountCode = requireNotNull(anchor.senderAccountCode) {
449:             "Conversation anchor mail record has no sender account"
450:         }
451:         val account = mailSenderAccountService.getManualSendAccount(accountCode)
452: 
453:         // I-7：Message-ID 仅在 trim 后非空且 <=255 时进入落库 inReplyTo 与 SMTP 线程头；
454:         // 缺失/超长不取消资格 —— 线程头为空仍发送（I-3：引用链只来自真实锚点）。
455:         val anchorMessageId = anchor.messageId
456:             ?.trim()
457:             ?.takeIf { it.isNotBlank() && it.length <= 255 }
458:         val smtpReferences = if (anchorMessageId != null) {
459:             listOfNotNull(anchor.inReplyTo?.trim()?.takeIf { it.isNotBlank() }, anchorMessageId)
460:                 .joinToString(" ")
461:         } else null
462: 
463:         val source = ManualRichSendSource(
464:             contact = contact,
465:             contactId = contactId,
466:             inboundProcessingId = null,
467:             inboundRecord = null,
468:             account = account,
469:             accountCode = accountCode,
470:             persistInReplyTo = anchorMessageId,
471:             smtpInReplyTo = anchorMessageId,
472:             smtpReferences = smtpReferences,
473:             anchorMailRecordId = requireNotNull(anchor.id) { "Conversation anchor mail record has no id" },
474:             requestId = canonicalRequestId
475:         )
476:         return executeManualRichSend(
477:             source = source,
478:             rawSubject = subject,
479:             htmlBody = htmlBody,
480:             textBody = textBody,
481:             operatorName = operatorName,
482:             safetyWarningConfirmed = safetyWarningConfirmed,
483:             strongConfirmationText = strongConfirmationText,
484:             evidence = ManualReplyEvidenceContext(runInboundSemanticChecks = false),
485:             attachmentIds = attachmentIds,
486:             authenticatedUsername = authenticatedUsername
487:         )
488:     }
489: 
490:     /**
```
```text
516:     private fun executeManualRichSend(
517:         source: ManualRichSendSource,
518:         rawSubject: String,
519:         htmlBody: String,
520:         textBody: String?,
521:         operatorName: String?,
522:         safetyWarningConfirmed: Boolean,
523:         strongConfirmationText: String?,
524:         evidence: ManualReplyEvidenceContext,
525:         // 03 (T1/I-1/I-2): 已预览会议配置与预览快照 sha256（仅来信路径携带；会话回信
526:         // 路径不传，保持默认 null）。非空时在最终变量渲染后用真实 processing/contact/
527:         // account 走 01 validateAndBuild 重算核对，两者必须同时出现/同时为空。
528:         meeting: MeetingInput? = null,
529:         previewAttachmentSha256: String? = null,
530:         // 06 (I-1)：通用附件 id（选择顺序）与真实会话身份；两条入口都传到这里，附件在
531:         // claim 之前解析（同一文件集合同时喂 SendPayload 快照与 ComposedMail 载荷）。
532:         attachmentIds: List<String> = emptyList(),
533:         authenticatedUsername: String? = null
534:     ): PendingMailSendResult {
535:         val contact = source.contact
536:         require(rawSubject.isNotBlank()) { "Subject is required" }
537:         require(htmlBody.isNotBlank()) { "HTML body is required" }
538:         val trimmedSubject = rawSubject.trim()
539:         require(trimmedSubject.length <= 255) { "Subject exceeds 255 characters" }
540: 
541:         mailVariableService.requireValidPlaceholders(trimmedSubject)
542:         val renderedSubject = mailVariableService.renderForContact(trimmedSubject, source.account, contact)
543:         require(renderedSubject.isNotBlank()) { "Rendered subject is empty" }
544:         require(renderedSubject.length <= 255) { "Rendered subject exceeds 255 characters: ${renderedSubject.length}" }
545: 
546:         val rawText = evidence.templateTextBody?.takeIf { it.isNotBlank() }
547:             ?: textBody?.takeIf { it.isNotBlank() }
548:             ?: mailBodyCleaner.clean(htmlBody)
549:         val rawHtmlFromTemplate = evidence.templateHtmlBody?.takeIf { it.isNotBlank() }
550:         mailVariableService.requireValidPlaceholders(rawText)
551:         if (rawHtmlFromTemplate != null) {
552:             mailVariableService.requireValidPlaceholders(rawHtmlFromTemplate)
553:         } else if (evidence.templateTextBody.isNullOrBlank()) {
554:             mailVariableService.requireValidPlaceholders(htmlBody)
555:         }
556: 
557:         val renderedText = mailVariableService.renderForContact(rawText, source.account, contact)
558:         // I-1/I-2/I-3：人工富文本最终正文必须在变量渲染之后、最终校验/安全确认/幂等指纹/
559:         // SMTP/落库/审计之前规范化一次；本方法后续所有读路径（finalValidationText、会议正文
560:         // 核对、SendPayload 指纹、ComposedMail 两个 MIME alternative、预览与归档）全部引用这
561:         // 一份 canonical final body，禁止检查原文却发送另一份正文（I-4）。
562:         val finalTextBody = mailContentService.normalizeManualTextLineBreaks(renderedText)
563:         val finalHtmlBody = when {
564:             rawHtmlFromTemplate != null ->
565:                 mailVariableService.renderHtmlForContact(rawHtmlFromTemplate, source.account, contact)
566:             !evidence.templateTextBody.isNullOrBlank() ->
```
```text
712:             throw ResponseStatusException(
713:                 HttpStatus.BAD_REQUEST,
714:                 "收件人已退订，禁止外发：${contact.expertEmail}"
715:             )
716:         }
717: 
718:         val claim = manualReplySendAttemptService.prepareAndClaim(payload)
719: 
720:         return when (claim.result) {
721:             ManualReplySendAttemptService.ClaimResult.CLAIMED,
722:             ManualReplySendAttemptService.ClaimResult.SAFE_RETRY_CLAIMED -> {
723:                 val mail = ComposedMail(
724:                     to = contact.expertEmail,
725:                     subject = renderedSubject,
726:                     body = finalHtmlBody,
727:                     html = true,
728:                     text = finalTextBody,
729:                     messageId = claim.messageId,
730:                     // 03 (I-2/I-3): 带会议日历的新分支用真实来信 processing.messageId 作
731:                     // SMTP 线程头（inReplyTo/references 同一来源）；会议只由来信路径产生，
```
```text
747:                 try {
748:                     val delivered = mailDeliveryService.send(source.account, mail)
749:                     val classification = classifyDelivery(delivered)
750: 
751:                     if (classification.isSent) {
752:                         val mailRecordId = try {
753:                             val id = manualReplySendAttemptService.finalizeSuccess(
754:                                 payload = payload,
755:                                 attemptId = claim.attemptId,
756:                                 messageId = claim.messageId
757:                             )
758:                             if (source.inboundProcessingId != null) {
759:                                 manualReplySendAttemptService.recordSendAudit(
760:                                     inboundProcessingId = source.inboundProcessingId,
761:                                     contactId = source.contactId,
762:                                     mailRecordId = id,
763:                                     canonicalFactIds = evidence.canonicalFactIds,
764:                                     carriesQa = evidence.carriesQa,
765:                                     delivered = delivered,
766:                                     sendSubject = renderedSubject,
767:                                     bodyPreviewText = bodyPreviewText,
768:                                     operatorName = operatorName,
769:                                     inboundRecord = requireNotNull(source.inboundRecord) {
770:                                         "Inbound rich reply audit requires the inbound processing record"
771:                                     },
772:                                     serverSuggestedFactIds = evidence.serverSuggestedFactIds,
773:                                     edited = evidence.edited,
774:                                     // 04 (I-1/I-7): 仅在该次发送存在 verified assembly 时附加诊断。
775:                                     trustReplyDiagnostics = evidence.verifiedAssembly?.response?.diagnostics,
776:                                     note = auditNote(inboundProcessingId = source.inboundProcessingId, contactId = source.contactId, findings = findings, requiresStrong = requiresStrong)
777:                                 )
778:                             } else {
779:                                 manualReplySendAttemptService.recordConversationSendAudit(
780:                                     contactId = source.contactId,
781:                                     anchorMailRecordId = requireNotNull(source.anchorMailRecordId) {
782:                                         "Conversation rich reply audit requires the anchor mail record"
783:                                     },
784:                                     mailRecordId = id,
785:                                     delivered = delivered,
786:                                     sendSubject = renderedSubject,
787:                                     bodyPreviewText = bodyPreviewText,
788:                                     operatorName = operatorName,
789:                                     note = auditNote(inboundProcessingId = null, contactId = source.contactId, findings = findings, requiresStrong = requiresStrong)
790:                                 )
791:                             }
792:                             // 03b (I-42): 发送成功后按请求中 ragFactCodes 的原始顺序写入
793:                             // mail_record_rag_fact 存证；RAG 路径 canonicalFactIds 恒为空，
794:                             // 绝不写 mail_record_qa_rule。
795:                             if (evidence.ragFactCodes != null) {
796:                                 val ragEvidenceRepo = requireNotNull(mailRecordRagFactRepository) {
797:                                     "MailRecordRagFactRepository is not wired for RAG send"
798:                                 }
799:                                 val fingerprintAtSend = requireNotNull(evidence.ragFingerprintAtSend) {
800:                                     "RAG fingerprint must be present after gate validation"
801:                                 }
802:                                 ragEvidenceRepo.saveAll(
803:                                     evidence.ragFactCodes.mapIndexed { ordinal, factCode ->
804:                                         MailRecordRagFact(
805:                                             mailRecordId = id,
806:                                             factCode = factCode,
807:                                             ordinal = ordinal,
808:                                             corpusFingerprint = fingerprintAtSend
809:                                         )
810:                                     }
811:                                 )
812:                             }
813:                             id
814:                         } catch (finalizeEx: Exception) {
815:                             log.warn("finalizeSuccess failed for attempt {}: {}", claim.attemptId, finalizeEx.message)
816:                             try {
817:                                 manualReplySendAttemptService.finalizeFailure(
818:                                     payload = payload,
819:                                     attemptId = claim.attemptId,
820:                                     messageId = claim.messageId,
821:                                     resultStatus = MailSendAttemptStatus.DELIVERY_UNKNOWN,
822:                                     errorSummary = "finalize_failure:${finalizeEx.message?.take(400).orEmpty()}"
823:                                 )
824:                             } catch (finalizeFailEx: Exception) {
825:                                 log.error("finalizeFailure to UNKNOWN also failed for attempt {}: {}",
826:                                     claim.attemptId, finalizeFailEx.message)
827:                             }
828:                             throw ResponseStatusException(
829:                                 HttpStatus.CONFLICT,
830:                                 "发送状态未知，请勿重复发送 (Message-ID: ${claim.messageId})"
831:                             )
832:                         }
833:                         // 归档只属来信语义链（verifiedAssembly 非空才可能产生样本）；会话回信
834:                         // 路径 verifiedAssembly 恒 null，直接返回默认 NOT_APPLICABLE（I-8/I-11）。
835:                         val archive = if (source.inboundProcessingId != null) {
836:                             archiveLiveUnsupportedAnswers(
837:                                 inboundProcessingId = source.inboundProcessingId,
838:                                 templateTextBody = evidence.templateTextBody,
839:                                 finalTextBody = finalTextBody,
840:                                 operatorName = operatorName,
841:                                 outboundMailRecordId = mailRecordId,
842:                                 verifiedAssembly = evidence.verifiedAssembly
843:                             )
844:                         } else {
845:                             UnsupportedAnswerIndexArchiveResult()
846:                         }
847:                         PendingMailSendResult(
848:                             contactId = source.contactId,
849:                             senderAccountCode = source.accountCode,
850:                             mailType = "MANUAL_RICH_REPLY",
851:                             subject = renderedSubject,
852:                             sendStatus = "SENT",
853:                             messageId = claim.messageId,
```
```text
909:             ManualReplySendAttemptService.ClaimResult.DEDUP_SENT -> {
910:                 val existingRecord = mailRecordRepository.findByMailSendAttemptId(claim.attemptId)
911:                 val archive = if (source.inboundProcessingId != null) {
912:                     archiveLiveUnsupportedAnswers(
913:                         inboundProcessingId = source.inboundProcessingId,
914:                         templateTextBody = evidence.templateTextBody,
915:                         finalTextBody = finalTextBody,
916:                         operatorName = operatorName,
917:                         outboundMailRecordId = existingRecord?.id,
918:                         verifiedAssembly = evidence.verifiedAssembly
919:                     )
920:                 } else {
921:                     UnsupportedAnswerIndexArchiveResult()
922:                 }
923:                 PendingMailSendResult(
924:                     contactId = source.contactId,
925:                     senderAccountCode = payload.accountCode,
926:                     mailType = "MANUAL_RICH_REPLY",
927:                     subject = renderedSubject,
928:                     sendStatus = "SENT",
929:                     messageId = existingRecord?.messageId ?: claim.messageId,
930:                     unsupportedAnswerArchiveStatus = archive.status,
931:                     unsupportedAnswerArchivedCount = archive.archivedCount,
932:                     unsupportedAnswerArchiveFailedCount = archive.failedCount
933:                 )
934:             }
935: 
936:             ManualReplySendAttemptService.ClaimResult.IN_PROGRESS ->
937:                 throw ResponseStatusException(
938:                     HttpStatus.CONFLICT,
939:                     "发送状态未知，请勿重复发送 (Message-ID: ${claim.messageId})"
940:                 )
941: 
942:             ManualReplySendAttemptService.ClaimResult.UNKNOWN ->
943:                 throw ResponseStatusException(
944:                     HttpStatus.CONFLICT,
945:                     "发送状态未知，请勿重复发送 (Message-ID: ${claim.messageId})"
946:                 )
947: 
948:             ManualReplySendAttemptService.ClaimResult.PERMANENT_FAILED ->
949:                 throw ResponseStatusException(
950:                     HttpStatus.UNPROCESSABLE_ENTITY,
951:                     "该内容已发送失败，请修改内容后重试"
952:                 )
953:         }
954:     }
955: 
956:     /**
957:      * 06 (I-1)：04 通用附件原件解析 —— 元数据/原件读取与归属（同专家 + 同上传者）、容量校验
958:      * 全部发生在调用方（最终发送门）的幂等 claim 之前。空 id 列表是「无通用附件」的既有
959:      * 形态，不触碰 04 服务、不要求会话身份。[authenticatedUsername] 为 null 时传空身份，
```
```text
1813: data class PendingMailSendResult(
1814:     val contactId: Long,
1815:     val senderAccountCode: String,
1816:     val mailType: String,
1817:     val subject: String,
1818:     val sendStatus: String,
1819:     val messageId: String?,
1820:     val unsupportedAnswerArchiveStatus: UnsupportedAnswerArchiveStatus = UnsupportedAnswerArchiveStatus.NOT_APPLICABLE,
1821:     val unsupportedAnswerArchivedCount: Int = 0,
1822:     val unsupportedAnswerArchiveFailedCount: Int = 0
1823: )
1824: 
1825: data class PendingQaReplyRequest(
1826:     val qaRuleId: Long,
1827:     val senderAccountCode: String?,
1828:     val operatorName: String?,
1829:     val useVariants: Boolean = false
1830: )
1831: 
1832: data class PendingManualRichReplyRequest(
1833:     val senderAccountCode: String?,
1834:     val subject: String,
1835:     val htmlBody: String,
1836:     val textBody: String?,
1837:     val operatorName: String?,
1838:     val qaRuleIds: List<Long>? = null,
1839:     val suggestedRuleIds: List<Long>? = null,
1840:     val ackSnippetId: Long? = null,
1841:     val edited: Boolean? = null,
1842:     val freeTextPreview: String? = null,
1843:     val useVariants: Boolean = false,
1844:     val templateTextBody: String? = null,
1845:     val templateHtmlBody: String? = null,
1846:     val trustReplyAssembly: TrustReplyAssembleRequest? = null,
1847:     // 03b (I-39~I-43): RAG 证据字段 —— 与 trustReplyAssembly 互斥（服务端 400
1848:     // SEND_EVIDENCE_SOURCE_CONFLICT）；既有 qaRuleIds 字段与类型不动。
1849:     val ragFactCodes: List<String>? = null,
1850:     val ragCorpusFingerprint: String? = null,
1851:     val safetyWarningConfirmed: Boolean = false,
1852:     val strongConfirmationText: String? = null,
1853:     // 03 (T1/I-1): 已预览会议配置 + 预览快照 sha256；服务端要求两者同时出现/同时为空。
1854:     val meeting: MeetingInput? = null,
1855:     val previewAttachmentSha256: String? = null,
1856:     // 06 (T1/I-1): 通用附件 id（用户选择顺序，04 上传产物）。默认空 = 既有无附件形态逐字
1857:     // 不变；非空时服务端按 (专家 + 会话身份) 重读 04 元数据与原件，越权/缺失在 claim 前失败。
1858:     val attachmentIds: List<String> = emptyList()
1859: )
1860: 
1861: data class ComposedReplyRequest(
1862:     val qaRuleIds: List<Long>,
1863:     val overrideTextBody: String?,
1864:     val freeTextBody: String? = null,
1865:     val ackSnippetId: Long? = null,
1866:     val senderAccountCode: String?,
1867:     val operatorName: String?,
1868:     val useVariants: Boolean = false
1869: )
1870: 
1871: data class ComposedReplyEvaluateRequest(
1872:     val factRuleIds: List<Long>
1873: )
1874: 
1875: data class TrustWorkbenchSuggestResult(
1876:     val suggestedRuleIds: List<Long>,
1877:     val suggestedRules: List<SuggestQaRule>,
1878:     val rulesByCategory: List<CategoryRulesGroup>,
1879:     val gapItems: List<GapItem>,
1880:     val gapDetected: Boolean,
1881:     val matchedCategoryIds: List<Long>,
1882:     val draftReadiness: String,
1883:     val requestCoverage: List<RequestCoverageItem>,
1884:     val inboundText: String
1885: )
1886: 
1887: data class TrustWorkbenchEvaluateResult(
1888:     val canonicalFactIds: List<Long>,
1889:     val suggestedFactIds: List<Long>,
1890:     val draftReadiness: String,
1891:     val requestCoverage: List<RequestCoverageItem>,
1892:     val gapDetected: Boolean
1893: )
1894: 
1895: data class AiReplyPreflightRequest(
1896:     val factRuleIds: List<Long> = emptyList(),
1897:     val expectedEvidenceSetVersion: String = "",
1898:     val textBody: String
1899: )
```
