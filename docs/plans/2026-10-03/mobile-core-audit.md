# 移动端核心流程：审计回执

日期：2026-10-03。静态代码审计；未登录线上、未进行设备实测。

基线提交：`b6be5f4604867f78c26fe9b4b1a32847068ad21e`。工作区已有其他改动，执行时重新核对文件哈希及 git diff。

## src/main/resources/static/index.html

SHA256: `b07b84f1ba155b17f1fffe60ecccade6437e55445b10ec916bcfbc6c5b6283be`；2352 行。

### L5–L16
```text
5:     <meta charset="utf-8">
6:     <meta name="viewport" content="width=device-width, initial-scale=1">
7:     <title>Weibo Talent Introduction Console — 人才引进系统</title>
8:     <link rel="preconnect" href="https://fonts.googleapis.com">
9:     <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
10:     <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600&family=JetBrains+Mono:wght@400;500;600;700&display=swap" rel="stylesheet">
11:     <link rel="stylesheet" href="styles.css?v=20261002-mailbox-last-reply">
12:     <link rel="stylesheet" href="expert-materials.css?v=20261002-mailbox-last-reply">
13:     <link rel="stylesheet" href="mailbox-chat.css?v=20261002-mailbox-last-reply">
14:     <link rel="stylesheet" href="meeting-confirmation.css?v=20261002-mailbox-last-reply">
15:     <link rel="stylesheet" href="world-clock.css?v=20261002-mailbox-last-reply">
16: </head>
```

### L62–L82
```text
62:     <header class="topnav task-center-nav">
63:         <div class="brand">
64:             <div class="brand-mark">
65:                 <svg viewBox="0 0 24 24" width="20" height="20" stroke="currentColor" stroke-width="2.5" fill="none" stroke-linecap="round" stroke-linejoin="round">
66:                     <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/>
67:                     <circle cx="9" cy="7" r="4"/>
68:                     <path d="M22 21v-2a4 4 0 0 0-3-3.87"/>
69:                     <path d="M16 3.13a4 4 0 0 1 0 7.75"/>
70:                 </svg>
71:             </div>
72:             <div>
73:                 <div class="brand-title">Talent Console</div>
74:                 <div class="brand-subtitle">专家引进自动化</div>
75:             </div>
76:         </div>
77:         <nav class="nav-tabs" aria-label="Main">
78:             <button class="nav-tab" data-view="monitoring">
79:                 <svg viewBox="0 0 24 24" width="18" height="18" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round">
80:                     <path d="M3 3v18h18"/><path d="M7 14l4-4 4 4 6-6"/>
81:                 </svg>
82:                 <span>邮件监控</span>
```

### L145–L182
```text
145:                     <span id="taskActiveNavCount"></span>
146:                 </span>
147:             </button>
148:         </nav>
149:         <div class="topnav-side">
150:             <div class="user-info">
151:                 当前登录: <span id="currentUserDisplay">admin</span>
152:             </div>
153:             <button class="nav-tab logout-btn" id="logoutBtn">
154:                 <svg viewBox="0 0 24 24" width="18" height="18" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round">
155:                     <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/>
156:                     <polyline points="16 17 21 12 16 7"/>
157:                     <line x1="21" y1="12" x2="9" y2="12"/>
158:                 </svg>
159:                 <span>退出登录</span>
160:             </button>
161:         </div>
162:     </header>
163: 
164:     <!-- Main Content Panel -->
165:     <main class="main">
166:         <header class="topbar">
167:             <div>
168:                 <h1 id="viewTitle">邮箱账号</h1>
169:                 <p id="viewSubtitle">维护发送账号、权重、限额和连通性。</p>
170:             </div>
171:             <div class="topbar-actions task-center-actions">
172:                 <button type="button" id="taskActiveGlobalBtn" class="task-center-pill task-center-global" hidden
173:                         title="按任务记录统计；实时进度仅显示与执行 ID 匹配的数据">
174:                     <i class="task-center-dot" aria-hidden="true"></i>
175:                     <span id="taskActiveGlobalText"></span>
176:                     <span aria-hidden="true">↗</span>
177:                 </button>
178:                 <button class="button secondary" id="refreshBtn">
179:                     <svg viewBox="0 0 24 24" width="16" height="16" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round" style="margin-right: 4px;">
180:                         <path d="M21.5 2v6h-6M21.34 15.57a10 10 0 1 1-.57-8.38l5.67-5.67"/>
181:                     </svg>
182:                     刷新
```

### L482–L505
```text
482:         <section class="view" id="view-contacts">
483:             <div class="toolbar contacts-toolbar">
484:                 <button class="button filter-toggle" id="filterToggleBtn" aria-expanded="false">
485:                     <svg viewBox="0 0 24 24" width="13" height="13" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><polygon points="22 3 2 3 10 12.46 10 19 14 21 14 12.46 22 3"/></svg>
486:                     筛选
487:                     <span class="filter-count-badge" id="filterActiveCount" hidden></span>
488:                 </button>
489:                 <div class="toolbar-group toolbar-filters" id="contactsFilterGroup">
490:                     <div class="expert-filter-row expert-filter-row-primary">
491:                     <label class="toolbar-label">
492:                         排序:
493:                         <select id="expertSortBy">
494:                             <option value="">默认排序</option>
495:                             <option value="updatedAt">按修改时间</option>
496:                         </select>
497:                     </label>
498:                     <label class="toolbar-label">
499:                         漏斗层级:
500:                         <select id="expertIndexLevel">
501:                             <option value="RAW">原始</option>
502:                             <option value="CANDIDATE" selected>筛选</option>
503:                             <option value="APPLICATION">有效</option>
504:                         </select>
505:                     </label>
```

### L690–L738
```text
690: 
691:             <div class="split-layout contacts-layout">
692:                 <!-- Left panel: candidate list -->
693:                 <section class="panel contacts-list-panel">
694:                     <div class="panel-head">
695:                         <h2>专家列表</h2>
696:                         <div class="layout-preset-group" title="调整左右分栏比例（也可拖拽中缝，双击恢复默认）">
697:                             <button class="layout-preset-btn" id="btnLayoutDefault" title="默认分栏 (500px)">▏</button>
698:                             <button class="layout-preset-btn" id="btnLayoutWideList" title="宽列表 (500px)">▎</button>
699:                             <button class="layout-preset-btn" id="btnLayoutSplit" title="左右等宽 (1:1)">▌</button>
700:                         </div>
701:                     </div>
702:                     <div id="contactCountInfo" class="contact-count-info text-muted"></div>
703:                     <div id="contactList" class="list"></div>
704:                     <div id="contactPager" class="list-pager">
705:                         <select id="expertIndexSize" aria-label="每页行数">
706:                             <option value="10">10 条/页</option>
707:                             <option value="20">20 条/页</option>
708:                             <option value="50" selected>50 条/页</option>
709:                             <option value="100">100 条/页</option>
710:                         </select>
711:                         <button class="button small" id="contactPrevPage">上一页</button>
712:                         <span id="contactPageInfo" class="list-pager-info"></span>
713:                         <button class="button small" id="contactNextPage">下一页</button>
714:                     </div>
715:                 </section>
716: 
717:                 <!-- Resizer Divider -->
718:                 <div class="layout-resizer" id="contactsLayoutResizer" title="拖拽调整宽度，双击恢复默认">
719:                     <div class="resizer-handle"></div>
720:                 </div>
721: 
722:                 <!-- Right panel: detail & timeline view -->
723:                 <section class="panel contact-detail-panel">
724:                     <div class="panel-head contact-detail-head">
725:                         <div style="display: flex; align-items: center; gap: 8px; width: 100%; margin-bottom: 8px;">
726:                             <h2 style="flex: 1; margin: 0;">专家引进状态与联系详情</h2>
727:                         </div>
728:                         <div class="contact-head-actions" id="contactHeadActions" hidden></div>
729:                     </div>
730:                     <div id="contactDetail" class="detail-empty">
731:                         <svg viewBox="0 0 24 24" width="48" height="48" stroke="currentColor" stroke-width="1.5" fill="none" stroke-linecap="round" stroke-linejoin="round" style="color: var(--text-muted);">
732:                             <circle cx="12" cy="12" r="10"/><path d="M12 16v-4"/><path d="M12 8h.01"/>
733:                         </svg>
734:                         <span>请在左侧列表中选择一位专家以查看详细往来记录和操作状态。</span>
735:                     </div>
736:                 </section>
737:             </div>
738:         </section>
```

### L741–L815
```text
741:         <section class="view" id="view-mailbox">
742:             <div class="toolbar" id="mailboxLegacyToolbar">
743:                 <button class="button primary" id="mailboxRefreshBtn">刷新</button>
744:                 <div class="mailbox-view-controls">
745:                     <div class="mailbox-segmented-control mailbox-view-mode" role="radiogroup" aria-label="收发件箱展示方式">
746:                         <label><input type="radio" name="mailboxViewMode" value="MAIL" checked><span>按邮件</span></label>
747:                         <label><input type="radio" name="mailboxViewMode" value="EXPERT"><span>按专家聚合</span></label>
748:                     </div>
749:                     <div class="mailbox-segmented-control mailbox-scope-mode" role="radiogroup" aria-label="收发件箱邮件范围">
750:                         <label><input type="radio" name="mailboxMailScope" value="ALL" checked><span>全部邮件</span></label>
751:                         <label><input type="radio" name="mailboxMailScope" value="PENDING"><span>仅待处理</span></label>
752:                     </div>
753:                 </div>
754:                 <select id="mailboxFilterAccountCode">
755:                     <option value="">全部邮箱账号</option>
756:                 </select>
757:                 <select id="mailboxFilterDirection">
758:                     <option value="">全部收发方向</option>
759:                     <option value="INBOUND">收件 (INBOUND)</option>
760:                     <option value="OUTBOUND">发件 (OUTBOUND)</option>
761:                 </select>
762:                 <select id="mailboxFilterTag">
763:                     <option value="">全部标签</option>
764:                     <option value="专家">专家</option>
765:                     <option value="待匹配">待匹配</option>
766:                     <option value="自动回复">自动回复</option>
767:                     <option value="手动回复">手动回复</option>
768:                     <option value="首发">首发</option>
769:                     <option value="待处理">待处理</option>
770:                     <option value="收件">收件</option>
771:                     <option value="发件">发件</option>
772:                 </select>
773:                 <input id="mailboxFilterRecipient" placeholder="输入邮箱关键词">
774:                 <input id="mailboxFilterKeyword" placeholder="搜索邮件主题或正文">
775:                 <input type="date" id="mailboxFilterStartDate">
776:                 <span>至</span>
777:                 <input type="date" id="mailboxFilterEndDate">
778:                 <button class="button primary" id="mailboxSearchBtn">查询</button>
779:             </div>
780: 
781:             <!-- B4 (S2b-3): 按任务执行过滤的提示条；位于既有 .toolbar 之下、不与标题栏「批量发送」按钮同行 -->
782:             <div id="mailboxExecutionFilterBar" class="toolbar" hidden>
783:                 <span class="text-muted" id="mailboxExecutionFilterText"></span>
784:                 <button type="button" class="button small" id="mailboxExecutionFilterClear">清除过滤</button>
785:             </div>
786: 
787:             <section class="panel" id="mailboxConversationPanel">
788:                 <div class="panel-head">
789:                     <h2>已激活账号收发邮件记录</h2>
790:                     <div class="panel-head-actions">
791:                         <button class="button" id="checkRepliesBtn" onclick="handleCheckReplies()">检查回复</button>
792:                         <button class="button primary" id="bulkOutreachBtn" onclick="handleBulkOutreach()">批量发送</button>
793:                         <button class="button" id="bulkAutoReplyBtn">自动回复：加载中...</button>
794:                     </div>
795:                 </div>
796:                 <div class="mailbox-list" id="mailboxList"></div>
797:                 <div class="pagination" id="mailboxPagination" style="padding: 16px 24px; display: flex; justify-content: flex-end; gap: 8px;"></div>
798:             </section>
799: 
800:             <section class="panel" id="unmatchedDetailPanel" hidden style="margin-top: 16px;">
801:                 <div class="panel-head">
802:                     <h2>工单详情与专家关系映射</h2>
803:                     <button class="button secondary" id="closeUnmatchedDetailBtn">收起面板</button>
804:                 </div>
805:                 <div id="unmatchedDetailContent" class="detail" style="padding: 24px;"></div>
806:             </section>
807:         </section>
808: 
809:         <!-- View: Meeting Calendar (fast-p 03 · S-1) -->
810:         <section class="view" id="view-meeting-calendar"><div id="meetingCalendarRoot" class="calendar-root"></div></section>
811: 
812:         <!-- View: Inbound Mail Summary -->
813:         <section class="view" id="view-inbound-summary">
814:             <div class="toolbar">
815:                 <input type="date" id="inboundFrom">
```

### L1024–L1108
```text
1024:         <section class="view" id="view-tasks">
1025:             <section id="taskActiveSection" aria-labelledby="taskActiveHeading">
1026:                 <div class="task-center-section-head">
1027:                     <h2 id="taskActiveHeading">正在执行 <span id="taskActiveCount" class="task-center-count">—</span></h2>
1028:                     <span id="taskActiveUpdated" class="task-center-note" role="status" aria-live="polite">正在读取任务状态…</span>
1029:                 </div>
1030:                 <div id="taskActiveEmpty" class="task-center-empty" hidden>暂无执行中的任务</div>
1031:                 <div id="taskActiveCards" class="task-center-grid"></div>
1032:                 <div id="taskActivePager" class="list-pager" hidden>
1033:                     <button type="button" class="button small" id="taskActivePrevPage">上一页</button>
1034:                     <span id="taskActivePageInfo" class="list-pager-info"></span>
1035:                     <button type="button" class="button small" id="taskActiveNextPage">下一页</button>
1036:                 </div>
1037:             </section>
1038: 
1039:             <section id="taskActiveDetail" class="task-center-detail" aria-labelledby="taskActiveDetailTitle" hidden>
1040:                 <div class="task-center-detail-head">
1041:                     <h2 id="taskActiveDetailTitle"></h2>
1042:                     <button type="button" id="taskActiveDetailClose" class="task-center-link">收起详情 ↑</button>
1043:                 </div>
1044:                 <div id="taskActiveDetailStatus" class="task-center-note" role="status" aria-live="polite"></div>
1045:                 <div id="taskActiveDetailBody" class="task-center-detail-body"></div>
1046:                 <div class="task-center-detail-actions">
1047:                     <button type="button" id="taskActiveLoadLogs" class="task-center-link" hidden>加载批次日志</button>
1048:                     <button type="button" id="taskActiveOpenControl" class="button small secondary" hidden>打开任务控制</button>
1049:                 </div>
1050:                 <div id="taskActiveInterruptPanel" class="task-center-interrupt" hidden>
1051:                     <label for="taskActiveInterruptReason">人工异常处理：运行记录须失联超过 5 分钟；已中断记录可补充一次原因</label>
1052:                     <select id="taskActiveInterruptReason">
1053:                         <option value="SERVICE_RESTART">服务重启或发布中断</option>
1054:                         <option value="PROCESS_EXIT">进程异常退出</option>
1055:                         <option value="DEPENDENCY_FAILURE">数据库、网络或外部服务故障</option>
1056:                         <option value="QUEUE_INTERRUPTED">消息队列或消费中断</option>
1057:                         <option value="TIMEOUT_STALLED">执行超时或长期无进展</option>
1058:                         <option value="VERIFIED_STOPPED">人工确认执行已停止</option>
1059:                         <option value="UNKNOWN">原因待排查</option>
1060:                         <option value="OTHER">其他原因（填写说明）</option>
1061:                     </select>
1062:                     <textarea id="taskActiveInterruptDetail" maxlength="1000" rows="2" placeholder="补充处理原因；选择“其他原因”时必填"></textarea>
1063:                     <button type="button" id="taskActiveInterruptSubmit" class="button small secondary">标记已中断</button>
1064:                 </div>
1065:                 <pre id="taskActiveLogs" class="task-center-log" hidden></pre>
1066:             </section>
1067: 
1068:             <button type="button" id="taskHistoryRefreshHint" class="task-center-link" hidden>任务状态有变化，刷新执行记录</button>
1069: 
1070:             <div class="toolbar">
1071:                 <select id="taskTypeFilter">
1072:                     <option value="">全部自动化任务</option>
1073:                 </select>
1074:                 <select id="taskStatusFilter">
1075:                     <option value="">全部执行状态</option>
1076:                     <option value="RUNNING">执行中</option>
1077:                     <option value="SUCCESS">执行成功</option>
1078:                     <option value="PARTIAL_SUCCESS">部分成功</option>
1079:                     <option value="FAILED">执行失败</option>
1080:                     <option value="CANCELLED">已取消</option>
1081:                     <option value="INTERRUPTED">已中断</option>
1082:                 </select>
1083:                 <button class="button primary" id="loadTasksBtn">查询任务执行记录</button>
1084:             </div>
1085: 
1086:             <section class="panel">
1087:                 <div class="panel-head"><h2>执行记录</h2></div>
1088:                 <div class="table-wrap">
1089:                     <table>
1090:                         <thead>
1091:                         <tr>
1092:                             <th>审计 ID</th>
1093:                             <th>任务类型</th>
1094:                             <th>触发方式</th>
1095:                             <th>当前状态</th>
1096:                             <th>发信统计/成功数</th>
1097:                             <th>开始时间</th>
1098:                             <th>异常堆栈/错误原因</th>
1099:                         </tr>
1100:                         </thead>
1101:                         <tbody id="tasksTable"></tbody>
1102:                     </table>
1103:                 </div>
1104:                 <div id="taskPager" class="list-pager" hidden>
1105:                     <button class="button small" id="taskPrevPage">上一页</button>
1106:                     <span id="taskPageInfo" class="list-pager-info"></span>
1107:                     <button class="button small" id="taskNextPage">下一页</button>
1108:                 </div>
```

### L2343–L2351
```text
2343: <!-- App Core controller -->
2344: <script src="task-modal-runtime.js"></script>
2345: <script src="trust-reply-workbench.js?v=20261002-mailbox-last-reply"></script>
2346: <script src="expert-materials.js?v=20261002-mailbox-last-reply"></script>
2347: <script src="meeting-confirmation.js?v=20261002-mailbox-last-reply"></script>
2348: <script src="mailbox-chat.js?v=20261002-mailbox-last-reply"></script>
2349: <script src="app.js?v=20261002-mailbox-last-reply"></script>
2350: <script src="world-clock.js?v=20261002-mailbox-last-reply"></script>
2351: </body>
```

## src/main/resources/static/styles.css

SHA256: `07ad9ceb0493a1ea46f636bee2a7d7573f2d7887cd1d1893d947ff176a6bb4ab`；12473 行。

### L1–L96
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
76:     --shadow-lg: 0 10px 28px -8px rgba(15, 23, 42, 0.14), 0 2px 6px rgba(15, 23, 42, 0.05);
77:     --shadow-xl: 0 20px 48px -12px rgba(15, 23, 42, 0.2), 0 4px 12px rgba(15, 23, 42, 0.06);
78:     --shadow: var(--shadow-md);
79: 
80:     --transition: all 0.15s ease;
81: 
82:     --font-mono: 'SF Mono', ui-monospace, Menlo, monospace;
83:     --font-body: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Helvetica Neue', sans-serif;
84: 
85:     --verbatim: #7c3aed;
86:     --verbatim-bg: rgba(124, 58, 237, 0.06);
87:     --verbatim-border: rgba(124, 58, 237, 0.24);
88: }
89: 
90: * {
91:     box-sizing: border-box;
92:     margin: 0;
93:     padding: 0;
94: }
95: 
96: [hidden] {
```

### L133–L174
```text
133: .app-shell {
134:     display: grid;
135:     grid-template-rows: auto minmax(0, 1fr);
136:     height: 100vh;
137: }
138: 
139: /* Topnav */
140: .topnav {
141:     display: flex;
142:     align-items: center;
143:     gap: 20px;
144:     padding: 10px 24px;
145:     background: var(--panel-bg);
146:     backdrop-filter: blur(20px) saturate(1.3);
147:     -webkit-backdrop-filter: blur(20px) saturate(1.3);
148:     border-bottom: 1px solid var(--glass-border);
149:     box-shadow: 0 1px 12px rgba(var(--primary-rgb), 0.06);
150:     position: relative;
151:     z-index: var(--z-overlay);
152: }
153: 
154: .topnav .brand {
155:     margin-bottom: 0;
156:     padding: 0;
157:     flex-shrink: 0;
158: }
159: 
160: .topnav-side {
161:     margin-left: auto;
162:     display: flex;
163:     align-items: center;
164:     gap: 4px;
165:     flex-shrink: 0;
166: }
167: 
168: .topnav-side .user-info {
169:     padding-left: 0;
170:     margin-right: 8px;
171:     white-space: nowrap;
172: }
173: 
174: .brand {
```

### L261–L285
```text
261: .main {
262:     padding: 20px 28px 28px;
263:     display: flex;
264:     flex-direction: column;
265:     gap: 12px;
266:     overflow-y: auto;
267:     width: 100%;
268:     max-width: 1400px;
269:     margin: 0 auto;
270:     min-width: 0;
271:     position: relative;
272:     z-index: 0;
273: }
274: 
275: /* contacts view fills remaining viewport, inner panels scroll individually */
276: #view-contacts.active {
277:     flex: 1 1 auto;
278:     min-height: 0;
279:     gap: 8px;
280: }
281: 
282: .topbar {
283:     display: flex;
284:     justify-content: space-between;
285:     align-items: center;
```

### L338–L380
```text
338: .view {
339:     display: none;
340: }
341: 
342: .view.active {
343:     display: flex;
344:     flex-direction: column;
345:     gap: 16px;
346:     animation: viewFadeIn 0.3s ease;
347: }
348: 
349: @keyframes viewFadeIn {
350:     from { opacity: 0; transform: translateY(8px); }
351:     to   { opacity: 1; transform: translateY(0); }
352: }
353: 
354: /* Toolbar */
355: .toolbar {
356:     display: flex;
357:     align-items: center;
358:     gap: 8px;
359:     flex-wrap: wrap;
360:     background-color: var(--panel-bg);
361:     padding: 10px 14px;
362:     border-radius: var(--radius-md);
363:     border: 1px solid var(--panel-border);
364: }
365: 
366: .toolbar input,
367: .toolbar select {
368:     width: auto;
369:     min-width: 120px;
370:     height: 32px;
371:     min-height: 32px;
372: }
373: 
374: .mailbox-view-controls {
375:     display: inline-flex;
376:     align-items: center;
377:     gap: 8px;
378:     flex: 0 0 auto;
379: }
380: 
```

### L539–L570
```text
539: .contacts-toolbar .toolbar-filters {
540:     display: none;
541:     width: 100%;
542:     padding-top: 8px;
543:     border-top: 1px solid var(--line);
544: }
545: 
546: .contacts-toolbar .toolbar-filters.open {
547:     display: flex;
548:     flex-wrap: wrap;
549:     align-items: center;
550:     gap: 6px 8px;
551: }
552: 
553: .contacts-toolbar .toolbar-filters.open .toolbar-label {
554:     font-size: 11px;
555: }
556: 
557: .contacts-toolbar .toolbar-filters.open .toolbar-label select,
558: .contacts-toolbar .toolbar-filters.open .toolbar-label input {
559:     min-width: 80px;
560:     height: 28px;
561:     min-height: 28px;
562: }
563: 
564: /* Expert filter layout: three deliberate rows on desktop. */
565: .contacts-toolbar .toolbar-filters.open {
566:     flex-direction: column;
567:     align-items: stretch;
568:     gap: 10px;
569: }
570: 
```

### L802–L870
```text
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
```

### L906–L916
```text
906: .contacts-layout {
907:     grid-template-columns: 500px 6px minmax(0, 1fr);
908:     align-items: stretch;
909:     flex: 1 1 auto;
910:     min-height: 320px;
911: }
912: 
913: /* Resizer Divider */
914: .layout-resizer {
915:     width: 6px;
916:     cursor: col-resize;
```

### L1236–L1258
```text
1236: .contacts-list-panel {
1237:     min-height: 0;
1238:     display: flex;
1239:     flex-direction: column;
1240: }
1241: 
1242: .contacts-list-panel .panel-head {
1243:     flex-shrink: 0;
1244: }
1245: 
1246: .contacts-layout .list {
1247:     flex: 1 1 auto;
1248:     min-height: 0;
1249:     max-height: none;
1250: }
1251: 
1252: .contact-count-info {
1253:     flex-shrink: 0;
1254:     padding: 4px 12px;
1255:     font-size: 12px;
1256: }
1257: 
1258: /* Pagination */
```

### L1483–L1523
```text
1483: .detail {
1484:     padding: 16px;
1485:     display: flex;
1486:     flex-direction: column;
1487:     gap: 16px;
1488: }
1489: 
1490: .contact-detail-panel {
1491:     min-height: 0;
1492:     display: flex;
1493:     flex-direction: column;
1494: }
1495: 
1496: .contact-detail-panel .panel-head {
1497:     flex-shrink: 0;
1498: }
1499: 
1500: .contact-detail-head {
1501:     position: relative;
1502:     z-index: 1;
1503:     align-items: stretch;
1504:     flex-direction: column;
1505:     background-color: rgba(15, 23, 42, 0.012);
1506: }
1507: 
1508: .contact-detail-head h2 {
1509:     width: 100%;
1510: }
1511: 
1512: .contact-head-actions {
1513:     display: flex;
1514:     flex-direction: column;
1515:     align-items: stretch;
1516:     gap: 10px;
1517:     width: 100%;
1518: }
1519: 
1520: .contact-head-status-row,
1521: .contact-head-mail-row {
1522:     display: flex;
1523:     align-items: center;
```

### L4254–L4323
```text
4254: @media (max-width: 1024px) {
4255:     .back-to-list {
4256:         display: inline-flex;
4257:     }
4258: 
4259:     .split-layout,
4260:     .qa-layout,
4261:     .contacts-layout {
4262:         grid-template-columns: 1fr !important;
4263:     }
4264: 
4265:     .inbound-charts-row {
4266:         grid-template-columns: 1fr;
4267:     }
4268: 
4269:     .inbound-mail-list,
4270:     .inbound-thread {
4271:         height: auto;
4272:         max-height: none;
4273:     }
4274: 
4275:     .inbound-expert-group-header {
4276:         grid-template-columns: 22px minmax(0, 1fr) auto;
4277:     }
4278: 
4279:     .inbound-expert-group-email {
4280:         grid-column: 2 / -1;
4281:     }
4282: 
4283:     .tag-pie {
4284:         grid-template-columns: 1fr;
4285:     }
4286: 
4287:     .contact-detail-panel {
4288:         height: auto;
4289:         min-height: 400px;
4290:     }
4291: 
4292:     #contactDetail {
4293:         overflow-y: visible;
4294:     }
4295: 
4296:     .layout-resizer {
4297:         display: none !important;
4298:     }
4299: 
4300:     .layout-preset-group {
4301:         display: none !important;
4302:     }
4303: 
4304:     #collapseListBtn,
4305:     #expandListBtn {
4306:         display: none !important;
4307:     }
4308: 
4309:     .contacts-list-panel {
4310:         display: block !important;
4311:         height: auto;
4312:     }
4313: 
4314:     .contacts-layout .list {
4315:         max-height: 50vh;
4316:     }
4317: 
4318:     .form-grid,
4319:     .form-grid-2,
4320:     .form-grid-3,
4321:     .form-grid-host-port,
4322:     .form-section-pair {
4323:         grid-template-columns: 1fr;
```

### L5161–L5172
```text
5161: .auth-card {
5162:     background-color: rgba(255, 255, 255, 0.82);
5163:     backdrop-filter: blur(18px) saturate(1.4);
5164:     -webkit-backdrop-filter: blur(18px) saturate(1.4);
5165:     border: 1px solid rgba(255, 255, 255, 0.6);
5166:     border-radius: var(--radius-lg);
5167:     padding: 36px 40px;
5168:     width: 100%;
5169:     max-width: 420px;
5170:     box-shadow: var(--shadow-xl);
5171: }
5172: .auth-card h2 {
```

### L8583–L8608
```text
8583: .detail-sub-tabs {
8584:     display: flex;
8585:     gap: 0;
8586:     border-bottom: 1px solid var(--border);
8587:     margin-bottom: 12px;
8588: }
8589: .detail-sub-tab {
8590:     padding: 8px 16px;
8591:     font-size: 13px;
8592:     font-weight: 500;
8593:     color: var(--text-muted);
8594:     background: none;
8595:     border: none;
8596:     border-bottom: 2px solid transparent;
8597:     cursor: pointer;
8598:     transition: color 0.15s, border-color 0.15s;
8599: }
8600: .detail-sub-tab:hover {
8601:     color: var(--text-main);
8602: }
8603: .detail-sub-tab.active {
8604:     color: var(--primary);
8605:     border-bottom-color: var(--primary);
8606: }
8607: 
8608: /* === 学术指标行 === */
```

### L10102–L10125
```text
10102: .contact-head-main-row {
10103:     display: flex;
10104:     align-items: center;
10105:     gap: 8px;
10106:     min-width: 0;
10107:     flex-wrap: wrap;
10108:     flex: 1 1 auto;
10109: }
10110: 
10111: .contact-head-divider {
10112:     width: 1px;
10113:     height: 18px;
10114:     background: var(--border);
10115:     flex-shrink: 0;
10116:     margin: 0 2px;
10117: }
10118: 
10119: .sender-binding-pill {
10120:     height: 28px;
10121:     min-height: 28px;
10122:     display: inline-flex;
10123:     align-items: center;
10124:     gap: 6px;
10125:     padding: 0 9px;
```

### L11463–L11555
```text
11463: /* meeting-mail-03: meeting calendar */
11464: .calendar-root{display:flex;flex-direction:column;gap:16px;color:#475569;font-size:13px;line-height:1.6}
11465: .calendar-toolbar{display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap;gap:12px}
11466: .calendar-actions{display:flex;align-items:center;flex-wrap:wrap;gap:8px}
11467: .calendar-scroll{overflow:auto;border:1px solid #dce4ef;border-radius:14px;background:#fff}
11468: .calendar-grid{display:grid;grid-template-columns:repeat(7,minmax(0,1fr));min-width:700px}
11469: .calendar-weekday{padding:10px;text-align:center;background:#f8faff;border-bottom:1px solid #dce4ef;color:#64748b}
11470: .calendar-day{min-height:132px;padding:8px;border-right:1px solid #e2e8f0;border-bottom:1px solid #e2e8f0}
11471: .calendar-day[data-outside=true]{background:#f8fafc;color:#94a3b8}
11472: .calendar-day[data-today=true]{background:#eff5ff}
11473: .calendar-event{display:flex;flex-direction:column;gap:2px;width:100%;margin-top:6px;padding:7px 9px;border:1px solid #cbdcf7;border-radius:7px;background:#eff5ff;color:#1e40af;font:inherit;text-align:left;overflow-wrap:anywhere;cursor:pointer}
11474: .calendar-event:hover{border-color:#93b4ec;background:#eaf1ff}
11475: .calendar-event:active{background:#dbeafe}
11476: .calendar-event[data-cancelled=true]{border-color:#dce4ef;background:#f1f5f9;color:#64748b}
11477: .calendar-list{display:flex;flex-direction:column;gap:8px}
11478: .calendar-summary{display:flex;align-items:center;flex-wrap:wrap;gap:8px;margin-top:8px;color:#1e40af;font-size:12px}
11479: .calendar-dialog{inset:0;margin:auto;width:min(640px,calc(100vw - 32px));max-height:calc(100dvh - 32px);padding:24px;border:1px solid #dce4ef;border-radius:18px;background:#fff;color:#334155;overflow:auto;box-shadow:0 24px 64px rgba(15,23,42,.2)}
11480: .calendar-dialog::backdrop{background:rgba(15,23,42,.35)}
11481: .calendar-form{display:grid;grid-template-columns:1fr 1fr;gap:16px;margin:16px 0}
11482: .calendar-field{display:flex;flex-direction:column;gap:6px;min-width:0;font-size:12px;color:#64748b}
11483: .calendar-field input,.calendar-field select,.calendar-field textarea{width:100%;min-height:36px;padding:8px 10px;border:1px solid #dce4ef;border-radius:7px;background:#fff;color:#334155;font:inherit}
11484: .calendar-field textarea{min-height:72px;resize:vertical}
11485: .calendar-wide{grid-column:1/-1}
11486: .calendar-note{margin:8px 0;color:#64748b;font-size:12px}
11487: .calendar-error{margin:8px 0;color:#be123c;font-size:12px}
11488: .calendar-root :is(button,input,select,a):focus-visible,.calendar-dialog :is(button,input,textarea,select,a):focus-visible{outline:2px solid #3b82f6;outline-offset:2px}
11489: .calendar-root :is(button,input,select):disabled,.calendar-dialog :is(button,input,textarea,select):disabled{opacity:.45;cursor:not-allowed;transform:none;box-shadow:none}
11490: .calendar-root [hidden],.calendar-dialog [hidden]{display:none!important}
11491: @media(max-width:760px){.calendar-form{grid-template-columns:1fr}.calendar-dialog{padding:16px}.calendar-day{min-height:112px}}
11492: @media(prefers-reduced-motion:reduce){.calendar-root *,.calendar-dialog *{transition:none!important;scroll-behavior:auto!important}}
11493: 
11494: /* Calendar preview alignment: scoped to the calendar page, not the shared dialog. */
11495: .app-shell:has(#view-meeting-calendar.active){grid-template-columns:minmax(0,1fr)}
11496: .app-shell:has(#view-meeting-calendar.active)>.topnav{min-width:0}
11497: .calendar-root{gap:20px}
11498: .calendar-root .calendar-overview{display:flex;align-items:center;gap:42px;padding:19px 25px;border:1px solid #dce4ef;border-radius:14px;background:#f8faff}
11499: .calendar-root .calendar-metric{display:flex;align-items:center;gap:13px;min-width:150px;color:#7d8ea7;font-size:12px}
11500: .calendar-root .calendar-metric strong{display:block;margin-top:4px;font-size:27px;line-height:1.2;font-weight:600;color:#334664}
11501: .calendar-root .calendar-metric small{margin-left:7px;font-size:12px;font-weight:400;color:#8f9fb7}
11502: .calendar-root .calendar-overview-icon{display:grid;place-items:center;width:45px;height:48px;border-radius:12px;background:#e7eeff;color:#476ed1;font-size:26px}
11503: .calendar-root .calendar-dot{display:inline-block;flex:none;width:8px;height:8px;border-radius:50%;background:#6589e6}
11504: .calendar-root .calendar-dot.green{background:#58b49c}
11505: .calendar-root .calendar-overview>p{margin:0 0 0 auto;color:#7f91ac;font-size:12px;line-height:1.9}
11506: .calendar-root .calendar-overview>p span{color:#8a9bb3}
11507: .calendar-root .calendar-layout{display:grid;grid-template-columns:minmax(0,1fr) 290px;gap:20px;align-items:start}
11508: .calendar-root .calendar-panel,.calendar-root .calendar-agenda{min-width:0;border:1px solid #dce4ef;border-radius:16px;background:#fff;box-shadow:0 3px 14px rgba(48,75,107,.04);overflow:hidden}
11509: .calendar-root .calendar-toolbar{padding:20px;border-bottom:1px solid #e2e8f0;gap:16px}
11510: .calendar-root .calendar-month-controls{display:flex;align-items:center;gap:7px}
11511: .calendar-root .calendar-month-controls h2{margin:0 12px 0 0;font-size:20px;font-weight:600;white-space:nowrap;color:#334155}
11512: .calendar-root .button{min-height:32px;height:auto;white-space:nowrap}
11513: .calendar-root .calendar-month-controls .button{padding:4px 11px;background:#fff}
11514: .calendar-root .calendar-month-controls [aria-label]{font-size:20px;line-height:1}
11515: .calendar-root .calendar-segmented{display:flex;gap:2px;border:1px solid #d9e3f3;border-radius:8px;padding:3px;background:#f5f8fe}
11516: .calendar-root .calendar-segmented .button{border:0;background:transparent;box-shadow:none;padding:5px 12px;min-height:26px;font-size:12px;color:#7d8da5}
11517: .calendar-root .calendar-segmented [aria-pressed=true]{background:#fff;color:#355dbe;box-shadow:0 1px 5px rgba(48,75,107,.08)}
11518: .calendar-root .calendar-filter-bar{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:8px 20px;border-bottom:1px solid #e2e8f0;background:#fcfdff}
11519: .calendar-root .calendar-filter-bar .calendar-note{margin:0;font-size:11px;color:#8291a7}
11520: .calendar-root .calendar-filter-bar .checkbox-row{flex-direction:row;white-space:nowrap;font-size:11px;letter-spacing:0;color:#7d8da5}
11521: .calendar-root .calendar-filter-bar input[type=checkbox]{width:14px;height:14px;min-height:14px;flex:none;padding:0;margin:0;box-shadow:none;accent-color:#3e68d3}
11522: .calendar-root .calendar-scroll{border:0;border-radius:0}
11523: .calendar-root .calendar-grid{min-width:560px}
11524: .calendar-root .calendar-weekday{padding:11px 6px;font-size:12px;color:#8595ad;background:#f9fbff}
11525: .calendar-root .calendar-day{min-height:115px;padding:9px 6px;border-color:#e8edf5;background:#fff}
11526: .calendar-root .calendar-day:nth-child(7n){border-right:0}
11527: .calendar-root .calendar-day[data-outside=true]{background:#fafbfd}
11528: .calendar-root .calendar-day[data-today=true]{background:#f5f8ff}
11529: .calendar-root [data-role=day-number]{display:grid;place-items:center;width:26px;height:26px;margin:0 0 7px 3px;border-radius:50%;font-size:12px;color:#77879e}
11530: .calendar-root [data-outside=true] [data-role=day-number]{color:#b2bdcd}
11531: .calendar-root [data-today=true] [data-role=day-number]{background:#3e68d3;color:#fff}
11532: .calendar-root .calendar-event{padding:7px 6px;border:0;border-left:3px solid #7093e9;border-radius:5px;background:#eef3ff;color:#4d6eaf;font-size:11px;line-height:1.5}
11533: .calendar-root .calendar-event:hover{background:#e5edff}
11534: .calendar-root .calendar-event strong{font-weight:600}
11535: .calendar-root .calendar-event[data-cancelled=true]{border-left-color:#a5b0c1;background:#f1f5f9;color:#64748b}
11536: .calendar-root .calendar-list{padding:16px 20px;min-height:220px}
11537: .calendar-root .calendar-list .calendar-event{font-size:13px;padding:12px}
11538: .calendar-root .calendar-footer{display:flex;justify-content:space-between;gap:10px;padding:14px 18px;color:#8796ac;font-size:11px}
11539: .calendar-root .calendar-footer .calendar-dot{width:6px;height:6px;margin-right:6px}
11540: .calendar-root .calendar-agenda{background:#f8faff}
11541: .calendar-root .calendar-agenda h2{margin:0;padding:21px 19px;border-bottom:1px solid #e2e8f0;font-size:15px;font-weight:600;color:#334155}
11542: .calendar-root [data-role=calendar-agenda]{padding:0 16px;max-height:740px;overflow:auto}
11543: .calendar-root .calendar-agenda-card{padding:17px 0;border-bottom:1px solid #e2e9f3;overflow-wrap:anywhere}
11544: .calendar-root .calendar-agenda-card time{color:#7d8da5;font-size:12px;line-height:1.8}
11545: .calendar-root .calendar-agenda-card h3{margin:7px 0;font-size:14px;font-weight:600;color:#334155}
11546: .calendar-root .calendar-agenda-card p{margin:0 0 10px;color:#8193ad;font-size:11px}
11547: .calendar-root .calendar-agenda-card .button{display:block;margin-left:auto;min-height:26px;padding:3px 0;border:0;background:transparent;color:#355dbe;font-size:12px;box-shadow:none}
11548: .calendar-root .calendar-agenda-empty{padding:32px 0;text-align:center;color:#8a9bb3;font-size:12px}
11549: .calendar-root .calendar-agenda-note{margin:16px;padding:11px 5px;border-radius:7px;background:#edf3fc;color:#7d93b5;text-align:center;font-size:11px}
11550: .calendar-root .calendar-grid>.calendar-error{grid-column:1/-1;padding:20px}
11551: @media(min-width:1650px){.calendar-root .calendar-layout{grid-template-columns:minmax(0,1fr) 325px}.calendar-root .calendar-day{min-height:133px}}
11552: @media(max-width:1200px){.calendar-root .calendar-layout{grid-template-columns:minmax(0,1fr) 250px}.calendar-root .calendar-overview{gap:22px}.calendar-root .calendar-metric{min-width:125px}.calendar-root .calendar-toolbar{padding:16px 12px}.calendar-root .calendar-month-controls h2{font-size:18px;margin-right:5px}}
11553: @media(max-width:900px){.calendar-root .calendar-layout{grid-template-columns:minmax(0,1fr)}.calendar-root .calendar-overview>p{display:none}.calendar-root .calendar-agenda{display:none}}
11554: @media(max-width:600px){.calendar-root .calendar-overview{padding:16px;gap:20px;justify-content:space-between}.calendar-root .calendar-metric{min-width:0;gap:7px;font-size:11px}.calendar-root .calendar-overview-icon{display:none}.calendar-root .calendar-metric strong{font-size:23px}.calendar-root .calendar-toolbar{gap:12px}.calendar-root .calendar-filter-bar{padding:8px 12px;flex-wrap:wrap}.calendar-root .calendar-footer{flex-wrap:wrap}}
11555: /* Manual material request: mailbox composer only. */
```

### L12034–L12048
```text
12034: .topnav.task-center-nav {
12035:     flex-wrap: wrap;
12036:     row-gap: 8px;
12037: }
12038: .task-center-nav .nav-tabs {
12039:     flex: 1 1 auto;
12040:     flex-wrap: wrap;
12041: }
12042: .task-center-actions {
12043:     display: flex;
12044:     align-items: center;
12045:     flex-wrap: wrap;
12046:     gap: 10px;
12047: }
12048: .task-center-pill {
```

### L12104–L12140
```text
12104: .task-center-section-head {
12105:     display: flex;
12106:     align-items: center;
12107:     justify-content: space-between;
12108:     flex-wrap: wrap;
12109:     gap: 8px;
12110:     margin: 7px 0 13px;
12111: }
12112: .task-center-section-head h2 {
12113:     display: flex;
12114:     align-items: center;
12115:     gap: 9px;
12116:     font-size: 14px;
12117: }
12118: .task-center-count {
12119:     padding: 2px 7px;
12120:     border-radius: 6px;
12121:     background: #e8eef9;
12122:     color: #34548b;
12123:     font-size: 11px;
12124: }
12125: .task-center-note {
12126:     color: #64748b;
12127:     font-size: 11px;
12128:     line-height: 18px;
12129:     overflow-wrap: anywhere;
12130: }
12131: .task-center-grid {
12132:     display: grid;
12133:     grid-template-columns: repeat(3, minmax(0, 1fr));
12134:     gap: 14px;
12135:     margin-bottom: 18px;
12136: }
12137: .task-center-card {
12138:     min-width: 0;
12139:     padding: 18px;
12140:     border: 1px solid #dce5f3;
```

### L12250–L12323
```text
12250: .task-center-link:disabled,
12251: .task-center-detail .button:disabled,
12252: #taskActivePager .button:disabled {
12253:     opacity: 0.5;
12254:     cursor: not-allowed;
12255:     transform: none;
12256:     box-shadow: none;
12257: }
12258: .task-center-empty {
12259:     padding: 24px 16px;
12260:     margin-bottom: 18px;
12261:     border: 1px dashed #dce5f3;
12262:     border-radius: 14px;
12263:     color: #64748b;
12264:     background: rgba(255, 255, 255, 0.55);
12265:     font-size: 12px;
12266:     text-align: center;
12267: }
12268: .task-center-detail {
12269:     min-width: 0;
12270:     padding: 17px 20px;
12271:     margin-bottom: 18px;
12272:     border: 1px solid #d9e4f6;
12273:     border-radius: 12px;
12274:     background: #f8faff;
12275: }
12276: .task-center-detail-head {
12277:     display: flex;
12278:     align-items: center;
12279:     justify-content: space-between;
12280:     flex-wrap: wrap;
12281:     gap: 8px;
12282:     margin-bottom: 12px;
12283: }
12284: .task-center-detail-body {
12285:     min-width: 0;
12286:     overflow-wrap: anywhere;
12287: }
12288: .task-center-log {
12289:     max-height: 240px;
12290:     margin-top: 12px;
12291:     overflow: auto;
12292:     color: #586b87;
12293:     font-family: var(--font-mono);
12294:     font-size: 12px;
12295:     line-height: 24px;
12296:     white-space: pre-wrap;
12297:     overflow-wrap: anywhere;
12298: }
12299: .task-center-detail-actions {
12300:     display: flex;
12301:     align-items: center;
12302:     flex-wrap: wrap;
12303:     gap: 12px;
12304:     margin-top: 12px;
12305: }
12306: @media (max-width: 1100px) {
12307:     .task-center-nav .nav-tabs {
12308:         flex-basis: 100%;
12309:         flex-wrap: nowrap;
12310:     }
12311:     .task-center-grid {
12312:         grid-template-columns: repeat(2, minmax(0, 1fr));
12313:     }
12314: }
12315: @media (max-width: 760px) {
12316:     .task-center-grid {
12317:         grid-template-columns: minmax(0, 1fr);
12318:     }
12319:     .task-center-card {
12320:         padding: 13px;
12321:     }
12322: }
12323: @media (prefers-color-scheme: dark) {
```

## src/main/resources/static/app.js

SHA256: `26ce14cec084e53a52918a918971c9260608f5025dc08e6e890ac9dcbf55638a`；22082 行。

### L554–L570
```text
554: const viewMeta = {
555:     monitoring: ["邮件监控", "当日活动概览、自动回复全链路、发件账号健康。"],
556:     accounts: ["邮箱账号", "维护发送账号、权重、限额和连通性。"],
557:     "mail-templates": ["邮件模板", "统一管理 QA 规则、回复片段与邮件模板。"],
558:     suppressions: ["退订名单", "查看和管理退订抑制邮箱，手动加入或移除。"],
559:     contacts: ["专家列表", "查看联系状态、邮件时间线和人工处理。"],
560:     mailbox: ["收发件箱", "查看所有已激活邮箱账号的收发记录，含待处理来信与标签筛选。"],
561:     "meeting-calendar": ["会议日历", "按北京时间的会议排期月历与列表，支持新增、改期与取消。"],
562:     "inbound-summary": ["来信汇总", "按标签汇总来信、查看往来记录与标签统计。"],
563:     "ai-training": ["AI 回复训练", "导入提炼 QA、配置提示词与约束，用历史邮件模拟 AI 回复效果。"],
564:     tasks: ["任务记录", "统一查看后台任务进度、执行结果和日志。"]
565: };
566: 
567: const statusLabels = {
568:     NEW: "新建",
569:     INTRO_SENT: "首封已发送",
570:     WAITING_REPLY: "等待回复",
```

### L3214–L3260
```text
3214: function setView(view) {
3215:     if (view !== "ai-training") unmountAiTrainingTrustReply();
3216:     if (view !== "mailbox") unmountMailboxTrustReplyHosts();
3217:     // child 10（I-2）：离开收发件箱即销毁聊天 mount（草稿为内存态、随销毁清空，
3218:     // 避免跨会话/跨专家残留目标与 QA 上下文）。
3219:     if (view !== "mailbox" && typeof unmountMailboxChatHosts === "function") {
3220:         unmountMailboxChatHosts();
3221:     }
3222:     if (state.monitoring.autoRefreshTimer && view !== "monitoring") {
3223:         clearTimeout(state.monitoring.autoRefreshTimer);
3224:         state.monitoring.autoRefreshTimer = null;
3225:     }
3226:     if (view !== "monitoring") {
3227:         ++state.monitoring.loadSeq;
3228:         ++state.monitoring.activitySeq;
3229:         ++state.monitoring.openTracking.requestSeq;
3230:         ++state.monitoring.openTracking.settingsSeq;
3231:         closeOpenTrackingDetail();
3232:     }
3233:     state.view = view;
3234:     if (view === "mail-templates") {
3235:         state.mailSendOptions = [];
3236:     }
3237:     $$(".nav-tab").forEach((tab) => tab.classList.toggle("active", tab.dataset.view === view));
3238:     $$(".view").forEach((section) => section.classList.toggle("active", section.id === `view-${view}`));
3239:     $("#viewTitle").textContent = viewMeta[view][0];
3240:     $("#viewSubtitle").textContent = viewMeta[view][1];
3241:     refreshCurrentView();
3242:     // T-3：进入/离开任务页只失效观察器请求版本；手动 watcher 的其余语义不动。
3243:     if (view === "tasks") {
3244:         // 进入任务页：后台（size=1）响应即刻作废，按当前 activePage 立即取一次卡片。
3245:         taskActivityState.listRequestSequence += 1;
3246:         if (taskActivityState.started) refreshTaskActivity();
3247:     } else {
3248:         // 离开任务页：收起页内详情并使详情与列表的迟到响应失效（I-6）。
3249:         taskActivityState.listRequestSequence += 1;
3250:         closeTaskActivityDetail();
3251:     }
3252:     if (view === "contacts") {
3253:         resumeProgressPollingIfNeeded();
3254:     } else {
3255:         ["EXPERT_REVALIDATION", "RAW_PROMOTION_SCAN", "EXPERT_DISCOVERY"].forEach(t => stopTaskWatcher(t, true));
3256:     }
3257: }
3258: 
3259: async function refreshCurrentView() {
3260:     try {
```

### L7046–L7065
```text
7046: async function loadContacts() {
7047:     const level = $("#expertIndexLevel").value;
7048:     const size = Number($("#expertIndexSize").value || "50");
7049:     const operatorStatus = $("#contactStatusFilter")?.value || "";
7050:     const needsAttention = $("#contactNeedsAttentionFilter")?.value || "";
7051:     const replyMode = $("#contactReplyModeFilter")?.value || "";
7052:     const emailDomain = $("#expertEmailDomainFilter")?.value || "";
7053:     const region = $("#expertRegionFilter")?.value || "";
7054:     const discipline = $("#expertDisciplineFilter")?.value || "";
7055:     // I1-5: typeof 兜底 —— vm 沙箱单测以函数为单位抽取源码，未注册本函数时退化为空数组
7056:     const expertTypes = typeof expertTypeActiveValues === "function" ? expertTypeActiveValues() : [];
7057:     let tag = $("#expertTagFilter")?.value || "";
7058:     const useDbContactPath = needsAttention || replyMode;
7059:     renderContactListSkeleton();
7060: 
7061:     const tagFilterEl = $("#expertTagFilter");
7062:     const regionFilterEl = $("#expertRegionFilter");
7063:     const disciplineFilterEl = $("#expertDisciplineFilter");
7064:     const academicFilterIds = ["expertHIndexMinFilter", "expertCitationMinFilter", "expertRecentYearsFilter", "expertHasFieldFilter"];
7065:     if (useDbContactPath) {
```

### L9210–L9230
```text
9210: 
9211: function backToListBtnHtml() {
9212:     return `
9213:         <button class="button small back-to-list" onclick="scrollBackToContactsList()">
9214:             <svg viewBox="0 0 24 24" width="13" height="13" stroke="currentColor" stroke-width="2.5" fill="none" stroke-linecap="round" stroke-linejoin="round"><polyline points="15 18 9 12 15 6"/></svg>
9215:             返回列表
9216:         </button>`;
9217: }
9218: 
9219: function scrollBackToContactsList() {
9220:     document.querySelector(".contacts-list-panel")?.scrollIntoView({ behavior: "smooth", block: "start" });
9221: }
9222: 
9223: function scrollBackToContactsList() {
9224:     document.querySelector(".contacts-list-panel")?.scrollIntoView({ behavior: "smooth", block: "start" });
9225: }
9226: 
9227: function renderDetailSubTabs(activeTab = "academic") {
9228:     const tabs = [
9229:         { key: "academic", label: "学术档案" },
9230:         { key: "contact", label: "联系详情" },
```

### L9357–L9382
```text
9357: async function showExpertDetail(expert) {
9358:     const name = expert.displayName || expert.email || expert.orcidId || "?";
9359:     const initial = name.charAt(0).toUpperCase();
9360:     const contactDetail = $("#contactDetail");
9361:     // 切到 ES 原始专家视图前同样释放上一个联系人的材料组件视图
9362:     if (typeof unmountExpertMaterialsHosts === "function") {
9363:         unmountExpertMaterialsHosts(contactDetail);
9364:     }
9365:     const tagLevel = expert.indexLevel || $("#expertIndexLevel").value || "CANDIDATE";
9366:     let expertTags = { found: false, tags: [] };
9367:     if (expert.orcidId) {
9368:         try {
9369:             expertTags = await fetchExpertTagsFromEs(expert.orcidId, tagLevel);
9370:         } catch (error) {
9371:             showStatus(error.message, "error");
9372:         }
9373:     }
9374:     $("#contactHeadActions").hidden = true;
9375:     $("#contactHeadActions").innerHTML = "";
9376:     contactDetail.classList.remove("detail-empty");
9377:     contactDetail.scrollTop = 0;
9378:     const noContactMaterialsHtml = typeof renderNoContactMaterialsEmpty === "function" ? renderNoContactMaterialsEmpty() : "";
9379:     contactDetail.innerHTML = `
9380:         ${backToListBtnHtml()}
9381:         <div class="detail">
9382:             <div class="expert-profile-header">
```

### L9786–L9808
```text
9786: async function loadContactDetail(contactId) {
9787:     const [detail, options, documents, logs, materials, mailSummary] = await Promise.all([
9788:         api(`/api/expert-contacts/${contactId}`),
9789:         loadMailSendOptions(),
9790:         api(`/api/expert-contacts/${contactId}/documents`).catch(() => []),
9791:         api(`/api/operator-action-logs?expertContactId=${contactId}&pageSize=50&pageOffset=0`).catch(() => ({ records: [] })),
9792:         api(`/api/expert-contacts/${contactId}/material-requests`).catch((error) => {
9793:             showStatus("材料状态加载失败: " + error.message, "error");
9794:             return null;
9795:         }),
9796:         api(`/api/mail/mailbox/by-expert?expertContactId=${contactId}&page=0&size=1`).catch((error) => {
9797:             showStatus("邮件统计加载失败: " + error.message, "error");
9798:             return null;
9799:         })
9800:     ]);
9801:     const contact = detail.contact;
9802:     const expert = state.contacts.find(item => item.orcidId === state.selectedExpertOrcid) || {};
9803:     const name = contact.expertName || contact.expertEmail || expert.displayName || "?";
9804:     const initial = name.charAt(0).toUpperCase();
9805:     const boundSenderAccountCode = (contact.boundSenderAccountCode || "").trim();
9806:     $("#contactHeadActions").hidden = false;
9807:     $("#contactHeadActions").innerHTML = `
9808:         <div class="contact-head-main-row">
```

### L10095–L10120
```text
10095:     }
10096:     return contact;
10097: }
10098: 
10099: async function openContactInList(contactId) {
10100:     setView("contacts");
10101:     if (!state.contacts || state.contacts.length === 0) {
10102:         await loadContacts();
10103:     }
10104:     const contact = await loadContactDetail(contactId);
10105:     state.selectedExpertOrcid = contact?.orcidId || null;
10106:     if (contact && !state.contacts.some(item => item.orcidId === contact.orcidId)) {
10107:         state.contacts.unshift({
10108:             orcidId: contact.orcidId,
10109:             email: contact.expertEmail,
10110:             displayName: contact.expertName,
10111:             indexLevel: contact.currentIndexLevel,
10112:             indexLevelName: indexLevelLabels[contact.currentIndexLevel] || contact.currentIndexLevel,
10113:             contactId: contact.id,
10114:             contactStatus: contact.currentStatus,
10115:             operatorStatus: contact.operatorStatus,
10116:             needsManualAttention: contact.needsManualAttention,
10117:             country: "",
10118:             employment: "",
10119:             keyword: "",
10120:             tags: contact.tags || [],
```

### L12098–L12117
```text
12098:     if (action === "select-expert") {
12099:         const orcidId = element.dataset.orcid;
12100:         const expert = state.contacts.find((item) => item.orcidId === orcidId);
12101:         state.selectedExpertOrcid = orcidId;
12102:         $$("#contactList .list-item").forEach((item) => {
12103:             item.classList.toggle("active", item.dataset.orcid === orcidId);
12104:         });
12105:         if (expert?.contactId) {
12106:             await loadContactDetail(expert.contactId);
12107:         } else if (expert) {
12108:             await showExpertDetail(expert);
12109:         }
12110:         return;
12111:     }
12112:     if (action === "select-contact") {
12113:         await loadContactDetail(id);
12114:         return;
12115:     }
12116:     if (action === "open-contact-mailbox") {
12117:         openExpertMailbox(id, element.dataset.email);
```

### L14860–L14873
```text
14860: }
14861: 
14862: function bindEvents() {
14863:     ensureTranslateClickHandler();
14864:     $$(".nav-tab").forEach((tab) => tab.addEventListener("click", () => {
14865:         if (tab.dataset.view === "mailbox") clearMailboxExpertFocus();
14866:         setView(tab.dataset.view);
14867:     }));
14868:     $("#refreshBtn").addEventListener("click", () => {
14869:         refreshCurrentView();
14870:         // "刷新"同时补一轮运行卡片；查询参数与历史页码不动（T-3）。
14871:         if (taskActivityState.started) refreshTaskActivity();
14872:     });
14873:     initTaskActivityObserver();
```

### L15597–L15605
```text
15597:     });
15598:     updateFilterBadge();
15599:     $("#filterToggleBtn").addEventListener("click", () => {
15600:         const group = $("#contactsFilterGroup");
15601:         const open = group.classList.toggle("open");
15602:         $("#filterToggleBtn").setAttribute("aria-expanded", String(open));
15603:     });
15604: 
15605:     /* ── Tag-chip multi-select for 数据完整度 ── */
```

### L16339–L16423
```text
16339: function initLayoutResizer() {
16340:     const resizer = document.getElementById("contactsLayoutResizer");
16341:     const container = document.querySelector(".contacts-layout");
16342:     const listPanel = document.querySelector(".contacts-list-panel");
16343: 
16344: 
16345:     if (!resizer || !container || !listPanel) return;
16346: 
16347:     let isDragging = false;
16348: 
16349:     // Load saved layout width or default
16350:     const savedWidth = localStorage.getItem("contacts-list-width");
16351: 
16352:     function setListWidth(width, updateStorage = true) {
16353:         // Ensure within reasonable boundaries: min 200px, max 60% window width
16354:         const maxWidth = Math.min(800, window.innerWidth * 0.6);
16355:         const targetWidth = Math.max(200, Math.min(maxWidth, width));
16356: 
16357:         container.style.gridTemplateColumns = `${targetWidth}px 6px minmax(0, 1fr)`;
16358:         listPanel.style.display = "";
16359:         resizer.style.display = "";
16360: 
16361:         if (updateStorage) {
16362:             localStorage.setItem("contacts-list-width", targetWidth);
16363:         }
16364:     }
16365: 
16366:     function resetToDefault() {
16367:         setListWidth(500);
16368:     }
16369: 
16370:     // Double-click resizer to reset
16371:     resizer.addEventListener("dblclick", resetToDefault);
16372: 
16373:     // Pointer events: 同时支持鼠标和触屏拖拽
16374:     resizer.addEventListener("pointerdown", (e) => {
16375:         isDragging = true;
16376:         resizer.setPointerCapture(e.pointerId);
16377:         resizer.classList.add("dragging");
16378:         document.body.style.cursor = "col-resize";
16379:         document.body.style.userSelect = "none";
16380:     });
16381: 
16382:     resizer.addEventListener("pointermove", (e) => {
16383:         if (!isDragging) return;
16384:         const containerRect = container.getBoundingClientRect();
16385:         const newWidth = e.clientX - containerRect.left;
16386:         setListWidth(newWidth);
16387:     });
16388: 
16389:     const endDrag = (e) => {
16390:         if (isDragging) {
16391:             isDragging = false;
16392:             if (resizer.hasPointerCapture?.(e.pointerId)) {
16393:                 resizer.releasePointerCapture(e.pointerId);
16394:             }
16395:             resizer.classList.remove("dragging");
16396:             document.body.style.cursor = "";
16397:             document.body.style.userSelect = "";
16398:         }
16399:     };
16400:     resizer.addEventListener("pointerup", endDrag);
16401:     resizer.addEventListener("pointercancel", endDrag);
16402: 
16403:     // Preset buttons
16404:     document.getElementById("btnLayoutDefault")?.addEventListener("click", resetToDefault);
16405:     document.getElementById("btnLayoutWideList")?.addEventListener("click", () => setListWidth(500));
16406:     document.getElementById("btnLayoutSplit")?.addEventListener("click", () => {
16407:         const containerWidth = container.getBoundingClientRect().width;
16408:         setListWidth(Math.floor(containerWidth / 2) - 3);
16409:     });
16410: 
16411:     // Initialize state
16412:     if (savedWidth) {
16413:         setListWidth(parseInt(savedWidth), false);
16414:     } else {
16415:         resetToDefault();
16416:     }
16417: }
16418: 
16419: let appStarted = false;
16420: 
16421: function startAuthenticatedApp(username) {
16422:     $("#loginOverlay").hidden = true;
16423:     $("#changePasswordOverlay").hidden = true;
```

### L21006–L21030
```text
21006: const meetingCalendarState = {
21007:     month: null,            // {year, month}：北京月份锚点（月初/月末/今天均按北京日期）
21008:     viewMode: "month",      // "month" | "list"
21009:     showCancelled: false,
21010:     events: [],
21011:     loading: false,
21012:     error: "",
21013:     seq: 0,                 // I-3 列表请求序号：旧响应不得覆盖新月份/新筛选
21014:     contacts: null,
21015:     contactsPromise: null,
21016:     dialog: {
21017:         open: false,
21018:         mode: "create",     // "create" | "edit" | "cancel" | "pick" | "view"
21019:         contactId: null,
21020:         event: null,
21021:         expertLabel: "",
21022:         pickEvents: [],
21023:         pickMode: "",
21024:         returnFocus: null,
21025:         saving: false
21026:     }
21027: };
21028: 
21029: // ── 时间：唯一 formatter + 显式北京/UTC 转换（I-2） ──────────────────────
21030: 
```

### L21090–L21148
```text
21090:     for (let page = 0; page < MEETING_CALENDAR_MAX_PAGES; page += 1) {
21091:         const params = new URLSearchParams();
21092:         params.set("from", query.from);
21093:         params.set("to", query.to);
21094:         params.set("showCancelled", query.showCancelled ? "true" : "false");
21095:         params.set("limit", String(MEETING_CALENDAR_PAGE_LIMIT));
21096:         if (cursor) params.set("cursor", cursor);
21097:         const data = await api(`/api/meeting-calendar/events?${params.toString()}`);
21098:         ((data && Array.isArray(data.items)) ? data.items : []).forEach((item) => items.push(item));
21099:         cursor = (data && data.nextCursor) ? String(data.nextCursor) : "";
21100:         if (!cursor) break;
21101:     }
21102:     return items;
21103: }
21104: 
21105: async function meetingCalendarFetchEvent(id) {
21106:     return api(`/api/meeting-calendar/events/${encodeURIComponent(String(id))}`);
21107: }
21108: 
21109: async function meetingCalendarFetchSummaries(contactIds) {
21110:     const ids = (Array.isArray(contactIds) ? contactIds : [])
21111:         .map((id) => Number(id))
21112:         .filter((id) => Number.isFinite(id) && id > 0);
21113:     if (ids.length === 0) return [];
21114:     const data = await api(`/api/meeting-calendar/summaries?contactIds=${encodeURIComponent(ids.join(","))}`);
21115:     return Array.isArray(data) ? data : [];
21116: }
21117: 
21118: async function meetingCalendarCreateEvent(payload) {
21119:     return api("/api/meeting-calendar/events", { method: "POST", body: JSON.stringify(payload) });
21120: }
21121: 
21122: async function meetingCalendarUpdateEvent(id, payload) {
21123:     return api(`/api/meeting-calendar/events/${encodeURIComponent(String(id))}`, {
21124:         method: "PUT",
21125:         body: JSON.stringify(payload)
21126:     });
21127: }
21128: 
21129: async function meetingCalendarCancelEvent(id, payload) {
21130:     return api(`/api/meeting-calendar/events/${encodeURIComponent(String(id))}/cancel`, {
21131:         method: "POST",
21132:         body: JSON.stringify(payload)
21133:     });
21134: }
21135: 
21136: // ── 月历模型（I-2：周一至周日、固定 42 格、北京日期） ─────────────────────
21137: 
21138: function meetingCalendarMonthAnchor() {
21139:     if (!meetingCalendarState.month) {
21140:         const today = meetingCalendarBeijingParts(new Date());
21141:         meetingCalendarState.month = { year: Number(today.year), month: Number(today.month) };
21142:     }
21143:     return meetingCalendarState.month;
21144: }
21145: 
21146: function meetingCalendarShiftMonth(delta) {
21147:     const anchor = meetingCalendarMonthAnchor();
21148:     const shifted = new Date(Date.UTC(anchor.year, anchor.month - 1 + Number(delta || 0), 1));
```

### L21254–L21274
```text
21254:     + '<div class="calendar-metric"><i class="calendar-dot green" aria-hidden="true"></i><div>今日会议<strong><span data-role="today-count">—</span><small>场</small></strong></div></div>'
21255:     + '<p>支持邮件邀请与手动新增排期<br><span>时间统一显示为北京时间（UTC+08:00）</span></p></div>'
21256:     + '<div class="calendar-layout"><section class="calendar-panel" aria-label="会议排期">'
21257:     + '<div class="calendar-toolbar"><div class="calendar-month-controls"><h2 data-role="calendar-month"></h2>'
21258:     + '<button class="button" data-calendar-action="previous" aria-label="上个月">‹</button>'
21259:     + '<button class="button" data-calendar-action="next" aria-label="下个月">›</button>'
21260:     + '<button class="button" data-calendar-action="today">今天</button>'
21261:     + '</div><div class="calendar-actions"><button class="button primary" data-calendar-action="create">＋ 手动新增排期</button>'
21262:     + '<div class="calendar-segmented" role="group" aria-label="日历显示方式">'
21263:     + '<button class="button" data-calendar-action="month" aria-pressed="true">月视图</button>'
21264:     + '<button class="button" data-calendar-action="list" aria-pressed="false">排期列表</button></div>'
21265:     + '</div></div>'
21266:     + '<div class="calendar-filter-bar"><p class="calendar-note" data-role="calendar-status" role="status"></p>'
21267:     + '<label class="checkbox-row"><input type="checkbox" data-role="show-cancelled">显示已取消排期</label></div>'
21268:     + '<div class="calendar-scroll"><div class="calendar-grid" data-role="calendar-grid"></div></div>'
21269:     + '<div class="calendar-list" data-role="calendar-list" hidden></div>'
21270:     + '<div class="calendar-footer"><span><i class="calendar-dot" aria-hidden="true"></i>已有排期</span><span>点击会议查看详情或修改日期</span></div></section>'
21271:     + '<aside class="calendar-agenda"><h2>本月会议安排</h2><div data-role="calendar-agenda"></div>'
21272:     + '<p class="calendar-agenda-note">排期与收发件箱同步</p></aside></div>';
21273: 
21274: // 日期格骨架：格内先放日期号，再循环追加事件按钮骨架（S-2 数据循环只重复同一骨架）。
```

### L21334–L21357
```text
21334: function renderMeetingCalendarList(listEl, events) {
21335:     if (!listEl) return;
21336:     if (meetingCalendarState.error) {
21337:         listEl.innerHTML = '<p class="calendar-error"></p>';
21338:         listEl.querySelector(".calendar-error").textContent = `排期加载失败：${meetingCalendarState.error}`;
21339:         return;
21340:     }
21341:     if (!events || events.length === 0) {
21342:         listEl.innerHTML = '<p class="calendar-note"></p>';
21343:         listEl.querySelector(".calendar-note").textContent =
21344:             `${meetingCalendarMonthLabel()}暂无排期${meetingCalendarState.showCancelled ? "（含已取消）" : ""}`;
21345:         return;
21346:     }
21347:     listEl.innerHTML = MEETING_CALENDAR_EVENT_SKELETON.repeat(events.length);
21348:     const buttons = listEl.querySelectorAll(".calendar-event");
21349:     events.forEach((event, index) => meetingCalendarFillEventButton(buttons[index], event, "list"));
21350: }
21351: 
21352: function meetingCalendarStatusText() {
21353:     if (meetingCalendarState.loading) return "排期加载中…";
21354:     if (meetingCalendarState.error) return "排期加载失败";
21355:     const count = meetingCalendarMonthEvents(meetingCalendarState.events).length;
21356:     return `${meetingCalendarMonthLabel()}共 ${count} 场排期${meetingCalendarState.showCancelled ? "（含已取消）" : ""}`;
21357: }
```

### L21409–L21466
```text
21409: function renderMeetingCalendar() {
21410:     const root = meetingCalendarRootEl();
21411:     if (!root) return;
21412:     if (root.dataset.meetingCalendarSkeleton !== "1") {
21413:         root.innerHTML = MEETING_CALENDAR_CHROME_HTML;
21414:         root.dataset.meetingCalendarSkeleton = "1";
21415:     }
21416:     const monthEl = root.querySelector('[data-role="calendar-month"]');
21417:     if (monthEl) monthEl.textContent = meetingCalendarMonthLabel();
21418:     const monthButton = root.querySelector('[data-calendar-action="month"]');
21419:     if (monthButton) monthButton.setAttribute("aria-pressed", meetingCalendarState.viewMode === "month" ? "true" : "false");
21420:     const listButton = root.querySelector('[data-calendar-action="list"]');
21421:     if (listButton) listButton.setAttribute("aria-pressed", meetingCalendarState.viewMode === "list" ? "true" : "false");
21422:     const cancelledBox = root.querySelector('[data-role="show-cancelled"]');
21423:     if (cancelledBox) cancelledBox.checked = !!meetingCalendarState.showCancelled;
21424:     const statusEl = root.querySelector('[data-role="calendar-status"]');
21425:     if (statusEl) statusEl.textContent = meetingCalendarStatusText();
21426:     const monthView = meetingCalendarState.viewMode === "month";
21427:     const scroll = root.querySelector(".calendar-scroll");
21428:     meetingCalendarSetHidden(scroll, !monthView);
21429:     const gridEl = root.querySelector('[data-role="calendar-grid"]');
21430:     const listEl = root.querySelector('[data-role="calendar-list"]');
21431:     meetingCalendarSetHidden(listEl, monthView);
21432:     const monthEvents = meetingCalendarMonthEvents(meetingCalendarState.events);
21433:     renderMeetingCalendarOverview(root, monthEvents);
21434:     if (monthView) renderMeetingCalendarGrid(gridEl, meetingCalendarGridCells(), meetingCalendarState.events);
21435:     else renderMeetingCalendarList(listEl, monthEvents);
21436: }
21437: 
21438: /** I-1/I-3：进入 Tab 或写成功后才回读；旧响应按 seq 丢弃。 */
21439: async function loadMeetingCalendar() {
21440:     const root = meetingCalendarRootEl();
21441:     if (!root) return;
21442:     ensureMeetingCalendarBound();
21443:     meetingCalendarState.seq += 1;
21444:     const mySeq = meetingCalendarState.seq;
21445:     meetingCalendarState.loading = true;
21446:     meetingCalendarState.error = "";
21447:     renderMeetingCalendar();
21448:     const range = meetingCalendarGridRange();
21449:     try {
21450:         const items = await meetingCalendarFetchEvents({
21451:             from: range.from,
21452:             to: range.to,
21453:             showCancelled: !!meetingCalendarState.showCancelled
21454:         });
21455:         if (meetingCalendarState.seq !== mySeq) return;   // I-3：旧回包不得覆盖新筛选/新月份
21456:         meetingCalendarState.events = items;
21457:         meetingCalendarState.loading = false;
21458:     } catch (error) {
21459:         if (meetingCalendarState.seq !== mySeq) return;
21460:         meetingCalendarState.events = [];
21461:         meetingCalendarState.loading = false;
21462:         meetingCalendarState.error = (error && error.message) ? error.message : "未知错误";
21463:     }
21464:     renderMeetingCalendar();
21465: }
21466: 
```

### L21888–L21915
```text
21888: 
21889: function onMeetingCalendarClick(event) {
21890:     const target = event && event.target;
21891:     if (!target || typeof target.closest !== "function") return;
21892:     const actionButton = target.closest("[data-calendar-action]");
21893:     if (actionButton) {
21894:         const action = actionButton.getAttribute("data-calendar-action") || "";
21895:         if (action === "previous" || action === "next") {
21896:             meetingCalendarShiftMonth(action === "next" ? 1 : -1);
21897:             loadMeetingCalendar();
21898:             return;
21899:         }
21900:         if (action === "today") {
21901:             meetingCalendarResetToToday();
21902:             loadMeetingCalendar();
21903:             return;
21904:         }
21905:         if (action === "month" || action === "list") {
21906:             meetingCalendarState.viewMode = action;
21907:             renderMeetingCalendar();
21908:             return;
21909:         }
21910:         if (action === "create") {
21911:             openMeetingCalendarDialog({ mode: "create" });
21912:             return;
21913:         }
21914:         return;
21915:     }
```

## src/main/resources/static/mailbox-chat.js

SHA256: `dd1f18a7bdabae906aa2599a508b107883e3ae384966d010b1ad6e0505d117d9`；7551 行。

### L590–L676
```text
590:     function createInstance(host, options) {
591:         const instance = {
592:             host,
593:             root: null,
594:             elements: {},
595:             options: options || {},
596:             seq: 0,
597:             listSeq: 0,
598:             msgSeq: 0,
599:             convEpoch: 0,
600:             searchTimer: null,
601:             saveTimer: null,
602:             searchText: "",
603:             user: sessionUserFromOptions(options),
604:             filters: Object.assign({}, (options && options.filters) || {}),
605:             chip: (options && options.filters && typeof options.filters.pendingOnly === "boolean" && options.filters.pendingOnly) ? CHIP_PENDING : CHIP_ALL,
606:             chipUserTouched: false,
607:             legacyFilterState: null,
608:             tagOptions: { loading: false, loaded: false, failed: false, items: [] },
609:             list: { page: 0, total: 0, items: [], loading: false, error: "" },
610:             selectedContactId: null,
611:             selectedSummary: null,
612:             /** 待匹配（邮件级）选择键；与 selectedContactId/sessionStore 完全独立（I-6）。 */
613:             selectedUnmatchedId: null,
614:             /** 当前承载 #unmatchedDetailPanel 的 .mc-scroll 宿主；null = 未持有 lease（I-5）。 */
615:             unmatchedDetailHost: null,
616:             focusHandledContactId: null,
617:             focusLocating: false,
618:             focusMissedContactId: null,
619:             conversation: {
620:                 loading: false,
621:                 error: "",
622:                 items: [],
623:                 nextBefore: null,
624:                 hasMore: false,
625:                 contact: null,
626:                 accountScope: null
627:             },
628:             manual: {
629:                 mode: "none", // "none" | "inbound" | "outbound" | "unavailable"
630:                 targetProcessingId: null,
631:                 targetAccountCode: "",
632:                 targetKey: null,
633:                 qa: null,
634:                 busy: false
635:             },
636:             draftsRef: null,
637:             workbench: { instance: null, processingId: null },
638:             logs: { loaded: false },
639:             translations: new Map(),
640:             tagAdapter: null,
641:             manage: { open: false, trigger: null },
642:             meeting: {
643:                 controller: null,
644:                 editorRevision: 0,
645:                 sending: false,
646:                 lastBlobUrl: "",
647:                 // fast-p 03：contactId → { state: "ok"|"error", activeCount, next }（只读缓存，非真值）
648:                 summaryByContact: new Map(),
649:                 summaryEpoch: -1
650:             },
651:             followup: { open: false, targetKey: null, selectedId: null, selectedCopy: null, trigger: null },
652:             materialRequest: { open: false, identity: null, items: [], seq: 0, trigger: null },
653:             // fast-p 01（I-6）：引用模板弹框的单次临时状态；不是持久 store，不加 draft 字段。
654:             templateReference: {
655:                 open: false,
656:                 seq: 0,
657:                 identity: null,
658:                 items: [],
659:                 selectedId: null,
660:                 preview: null,
661:                 loading: "",
662:                 error: "",
663:                 trigger: null
664:             },
665:             // fast-p 2026-10-02 c3（I-1..I-6）：所在地/推荐时间的单次临时状态；
666:             // 不是持久 store，不写草稿 Map/localStorage/sessionStorage。
667:             // data 只属于 contactId 对应的 timing 响应；dialog 为当前打开的弹窗（多则一）。
668:             contactTiming: {
669:                 contactId: null,
670:                 seq: 0,
671:                 data: null,
672:                 loading: false,
673:                 error: "",
674:                 dialog: null,
675:                 dialogSeq: 0,
676:                 prefix: "",
```

### L735–L773
```text
735:             if (record) {
736:                 instance.draftsRef = record.drafts;
737:                 return record.drafts;
738:             }
739:             return null;
740:         }
741: 
742:         function ensureDraftsMap() {
743:             let drafts = currentDraftsMap();
744:             if (drafts) return drafts;
745:             const contactId = Number(instance.selectedContactId);
746:             if (!Number.isFinite(contactId) || contactId <= 0) return null;
747:             const scope = instance.conversation.accountScope || "";
748:             const record = upsertConversationRecord(instance.user, scope, contactId, {});
749:             instance.draftsRef = record.drafts;
750:             return record.drafts;
751:         }
752: 
753:         function getDraft(targetKey) {
754:             const drafts = currentDraftsMap();
755:             if (!drafts || !targetKey) return null;
756:             return drafts.get(targetKey) || null;
757:         }
758: 
759:         function setDraft(targetKey, draft) {
760:             const drafts = ensureDraftsMap();
761:             if (!drafts || !targetKey) return;
762:             drafts.set(targetKey, draft);
763:         }
764: 
765:         function deleteDraft(targetKey) {
766:             const drafts = currentDraftsMap();
767:             if (drafts && targetKey) drafts.delete(targetKey);
768:         }
769: 
770:         function setRefined(on) {
771:             const view = viewRoot();
772:             if (!view || !view.classList) return;
773:             if (on) view.classList.add("mc-refined");
```

### L1018–L1051
```text
1018:         // --------------------------------------------------------------
1019:         // 渲染骨架（S-1 两栏 + S-2 搜索/筛选）
1020:         // --------------------------------------------------------------
1021: 
1022:         function skeletonHtml() {
1023:             const chipButtons = FILTER_CHIPS.map((chip) =>
1024:                 `<button class="mc-filter" type="button" data-action="mc-filter" data-chip="${chip.key}" aria-pressed="${instance.chip === chip.key ? "true" : "false"}">${escapeText(chip.label)}</button>`
1025:             ).join("");
1026:             return `
1027:                 <div class="mail-chat">
1028:                     <aside class="mc-experts" aria-label="专家会话列表">
1029:                         <div class="mc-list-tools">
1030:                             <div class="mc-search-row">
1031:                                 <input type="search" aria-label="搜索专家" placeholder="搜索专家姓名、邮箱">
1032:                                 <button class="mc-icon" type="button" data-action="mc-more-filters" title="更多筛选" aria-label="更多筛选" aria-expanded="false" aria-controls="mcFilterPopover">⋯<span class="mc-filter-count" hidden></span></button>
1033:                                 <div class="mc-filter-popover" id="mcFilterPopover" role="dialog" aria-label="更多筛选" hidden>
1034:                                     <header><strong>更多筛选</strong><button class="mc-close" type="button" data-action="mc-close-filters" aria-label="关闭筛选">×</button></header>
1035:                                     <div class="mc-filter-fields" id="mailboxFilterFields"></div>
1036:                                     <p class="mc-inline-error" role="alert" hidden></p>
1037:                                     <footer><button class="mc-text-button" type="button" data-action="mc-reset-filters">重置</button><button class="button primary" type="button" id="mailboxSearchBtn">应用筛选</button></footer>
1038:                                 </div>
1039:                             </div>
1040:                             <div class="mc-filters">
1041:                                 ${chipButtons}
1042:                             </div>
1043:                             <div class="mc-filter-summary" hidden></div>
1044:                         </div>
1045:                         <div class="mc-expert-list" aria-live="polite"></div>
1046:                         <div class="mc-pager"></div>
1047:                     </aside>
1048:                     <section class="mc-conversation" aria-label="专家往来信件"></section>
1049:                 </div>
1050:             `;
1051:         }
```

### L1580–L1657
```text
1580: 
1581:         function clearUnmatchedState() {
1582:             releaseUnmatchedDetailNode();
1583:             instance.selectedUnmatchedId = null;
1584:         }
1585: 
1586:         // 宿主内是否仍真实持有唯一的 #unmatchedDetailPanel（宿主被外部归还/清空时自愈）。
1587:         function unmatchedDetailMounted() {
1588:             const host = instance.unmatchedDetailHost;
1589:             if (!host) return false;
1590:             const panel = host.querySelector ? host.querySelector("#unmatchedDetailPanel") : null;
1591:             if (!panel) {
1592:                 instance.unmatchedDetailHost = null;
1593:                 return false;
1594:             }
1595:             return true;
1596:         }
1597: 
1598:         function resolveUnmatchedSelection() {
1599:             const items = instance.list.items || [];
1600:             if (instance.selectedUnmatchedId == null) {
1601:                 releaseUnmatchedDetailNode();
1602:                 renderUnmatchedEmpty();
1603:                 return;
1604:             }
1605:             const stillPresent = items.some((item) => String(item.id) === String(instance.selectedUnmatchedId));
1606:             if (!stillPresent) {
1607:                 // 选中记录经服务端刷新消失（绑定成功/已标记处理/切页）：先归还，再回空态。
1608:                 clearUnmatchedState();
1609:                 renderUnmatchedEmpty();
1610:                 return;
1611:             }
1612:             if (!unmatchedDetailMounted()) mountUnmatchedDetail(instance.selectedUnmatchedId);
1613:         }
1614: 
1615:         function selectUnmatched(id) {
1616:             const items = instance.list.items || [];
1617:             const item = items.find((entry) => String(entry.id) === String(id));
1618:             if (!item) return;
1619:             const changed = String(instance.selectedUnmatchedId) !== String(item.id);
1620:             instance.selectedUnmatchedId = Number(item.id);
1621:             renderUnmatchedList();
1622:             if (!changed && unmatchedDetailMounted()) return;
1623:             mountUnmatchedDetail(Number(item.id));
1624:         }
1625: 
1626:         function mountUnmatchedDetail(id) {
1627:             releaseUnmatchedDetailNode();
1628:             const body = conversationBody();
1629:             if (!body) return;
1630:             const mount = hostFn("mcHostMountUnmatchedDetail");
1631:             body.setAttribute("aria-label", "待匹配来信处理");
1632:             body.innerHTML = `
1633:                 <div class="mc-scroll" tabindex="0" aria-label="待匹配来信详情"><div class="mc-empty">正在加载来信详情…</div></div>
1634:             `;
1635:             const scrollHost = body.querySelector ? body.querySelector(".mc-scroll") : null;
1636:             if (!mount || !scrollHost) {
1637:                 body.innerHTML = `
1638:                     <div class="mc-scroll" tabindex="0" aria-label="待匹配来信详情"><div class="mc-empty" role="alert">来信处理面板不可用，请刷新页面重试</div></div>
1639:                 `;
1640:                 return;
1641:             }
1642:             instance.unmatchedDetailHost = scrollHost;
1643:             let started = null;
1644:             try {
1645:                 started = mount(scrollHost, id);
1646:             } catch (e) {
1647:                 instance.unmatchedDetailHost = null;
1648:                 scrollHost.innerHTML = `<div class="mc-empty" role="alert">来信处理面板不可用，请刷新页面重试</div>`;
1649:                 return;
1650:             }
1651:             if (started && typeof started.catch === "function") {
1652:                 started.catch(() => {
1653:                     if (instance.disposed || instance.unmatchedDetailHost !== scrollHost) return;
1654:                     instance.unmatchedDetailHost = null;
1655:                     scrollHost.innerHTML = `<div class="mc-empty" role="alert">来信处理面板不可用，请刷新页面重试</div>`;
1656:                 });
1657:             }
```

### L1699–L1778
```text
1699:         function renderConversationEmpty() {
1700:             const body = conversationBody();
1701:             if (!body) return;
1702:             body.innerHTML = '<div class="mc-empty">请选择左侧专家查看往来信件</div>';
1703:         }
1704: 
1705:         function resolveFocusAndSelection() {
1706:             const items = instance.list.items || [];
1707:             const focus = instance.options.focus;
1708:             if (focus && focus.contactId != null
1709:                 && String(instance.focusHandledContactId || "") !== String(focus.contactId)) {
1710:                 if (instance.selectedContactId == null
1711:                     || String(instance.selectedContactId) !== String(focus.contactId)) {
1712:                     const found = items.find((item) => String(item.contactId) === String(focus.contactId));
1713:                     if (found) {
1714:                         selectExpert(found, { skipListReload: true });
1715:                         return;
1716:                     }
1717:                     locateFocusExpert(focus);
1718:                     return;
1719:                 }
1720:                 instance.focusHandledContactId = focus.contactId;
1721:             }
1722:             if (instance.selectedContactId != null && instance.list.items && instance.list.items.length > 0) {
1723:                 const stillPresent = instance.list.items.some(
1724:                     (item) => String(item.contactId) === String(instance.selectedContactId)
1725:                 );
1726:                 if (stillPresent) {
1727:                     const freshSummary = findSummaryByContactId(instance.selectedContactId);
1728:                     if (freshSummary) instance.selectedSummary = freshSummary;
1729:                     refreshConversationQuiet();
1730:                 } else {
1731:                     // 当前筛选不再包含该专家：先保存，再回到“请选择专家”空态
1732:                     saveConversationState();
1733:                     clearSelectedConversation();
1734:                     renderConversationEmpty();
1735:                 }
1736:                 return;
1737:             }
1738:             if (instance.selectedContactId == null && items.length === 0 && !focus) {
1739:                 renderConversationEmpty();
1740:             }
1741:         }
1742: 
1743:         function locateFocusExpert(focus) {
1744:             if (!focus || focus.contactId == null) return;
1745:             if (instance.focusLocating) return;
1746:             if (String(instance.focusMissedContactId || "") === String(focus.contactId)) {
1747:                 renderFocusMissed(focus);
1748:                 return;
1749:             }
1750:             if (!focus.email) {
1751:                 instance.focusMissedContactId = focus.contactId;
1752:                 renderFocusMissed(focus);
1753:                 return;
1754:             }
1755:             instance.focusLocating = true;
1756:             instance.listSeq += 1;
1757:             const mySeq = instance.listSeq;
1758:             const params = new URLSearchParams();
1759:             params.set("page", "0");
1760:             params.set("size", String(PAGE_SIZE));
1761:             params.set("q", String(focus.email).trim());
1762:             hostApi()(`/api/mail/mailbox/conversations?${params.toString()}`).then((data) => {
1763:                 instance.focusLocating = false;
1764:                 if (instance.disposed || mySeq !== instance.listSeq) return;
1765:                 const located = (data && Array.isArray(data.items) ? data.items : []).find(
1766:                     (item) => String(item.contactId) === String(focus.contactId)
1767:                 );
1768:                 if (located) {
1769:                     instance.focusHandledContactId = focus.contactId;
1770:                     selectExpert(located, { skipListReload: true });
1771:                 } else {
1772:                     instance.focusMissedContactId = focus.contactId;
1773:                     instance.focusHandledContactId = focus.contactId;
1774:                     renderFocusMissed(focus);
1775:                 }
1776:             }).catch(() => {
1777:                 instance.focusLocating = false;
1778:                 if (instance.disposed || mySeq !== instance.listSeq) return;
```

### L1815–L1876
```text
1815:         function selectExpert(item, options) {
1816:             const opts = options || {};
1817:             // 切换前保存旧会话
1818:             saveConversationState();
1819:             teardownConversationSubViews();
1820:             instance.selectedContactId = Number(item.contactId);
1821:             instance.selectedSummary = item;
1822:             instance.seq += 1;
1823:             instance.convEpoch += 1;
1824:             const mySeq = instance.seq;
1825:             const myEpoch = instance.convEpoch;
1826:             instance.conversation.loading = true;
1827:             instance.conversation.error = "";
1828:             instance.conversation.items = [];
1829:             instance.conversation.nextBefore = null;
1830:             instance.conversation.hasMore = false;
1831:             instance.conversation.contact = null;
1832:             instance.manual = {
1833:                 mode: "none",
1834:                 targetProcessingId: null,
1835:                 targetAccountCode: "",
1836:                 targetKey: null,
1837:                 qa: null,
1838:                 busy: false
1839:             };
1840:             instance.logs = { loaded: false };
1841:             instance.dismissedNewInbound = null;
1842:             instance.pendingPrompt = null;
1843:             instance.translations = new Map();
1844:             instance.loadOlderBusy = false;
1845:             instance.draftsRef = null;
1846:             renderConversationScaffold();
1847: 
1848:             const contactId = Number(item.contactId);
1849:             // c3（I-3）：换专家即作废旧 timing 代次，再按新 contact 取推荐。
1850:             loadContactTiming(contactId);
1851:             const accountFilter = accountFilterFromOptions();
1852:             const msgParams = new URLSearchParams();
1853:             msgParams.set("limit", String(MESSAGE_LIMIT));
1854:             if (accountFilter) msgParams.set("accountCode", accountFilter);
1855:             const contactPromise = hostApi()(`/api/expert-contacts/${contactId}`).catch(() => null);
1856:             const messagesPromise = hostApi()(`/api/mail/mailbox/conversations/${contactId}/messages?${msgParams.toString()}`)
1857:                 .catch(() => null);
1858: 
1859:             Promise.all([contactPromise, messagesPromise]).then(([contact, msgData]) => {
1860:                 if (instance.disposed || mySeq !== instance.seq || myEpoch !== instance.convEpoch) return;
1861:                 instance.conversation.contact = contact && contact.contact ? contact.contact : (contact || null);
1862:                 instance.conversation.accountScope = accountFilter;
1863:                 const serverItems = (msgData && Array.isArray(msgData.items)) ? msgData.items : [];
1864:                 instance.conversation.nextBefore = (msgData && msgData.nextBefore) || null;
1865:                 instance.conversation.hasMore = !!(msgData && msgData.hasMore);
1866:                 const cached = getConversationRecord(instance.user, accountFilter, contactId);
1867:                 if (cached && cached.items && cached.items.length > 0) {
1868:                     // 恢复缓存窗口（保留已加载历史），服务端同 key 状态胜
1869:                     instance.conversation.items = mergeServerIntoWindow(cached.items, serverItems);
1870:                     if (cached.nextBefore && (!instance.conversation.nextBefore || cached.items.length > serverItems.length)) {
1871:                         instance.conversation.nextBefore = cached.nextBefore;
1872:                     }
1873:                     if (cached.hasMore && cached.items.length > serverItems.length) {
1874:                         instance.conversation.hasMore = cached.hasMore;
1875:                     }
1876:                     instance.draftsRef = cached.drafts;
```

### L1924–L1946
```text
1924:         function renderConversationScaffold() {
1925:             const body = conversationBody();
1926:             if (!body) return;
1927:             const name = (instance.selectedSummary && (instance.selectedSummary.name || instance.selectedSummary.email)) || "…";
1928:             body.innerHTML = `
1929:                 <header class="mc-header"><div class="mc-identity"><h2>${escapeText(name)}</h2><p>正在加载往来信件…</p></div><div class="mc-actions"></div></header>
1930:                 <div class="mc-timeline-head"><span>往来信件</span><span class="mc-position-hint"></span><button class="mc-text-button" type="button" data-action="mc-latest">↓ 最新消息</button></div>
1931:                 <div class="mc-scroll" tabindex="0" aria-label="往来信件滚动区"><div class="mc-empty">正在加载往来信件…</div></div>
1932:             `;
1933:             bindScrollListener();
1934:         }
1935: 
1936:         function conversationSummaryInfo() {
1937:             const summary = instance.selectedSummary || {};
1938:             const parts = [];
1939:             if (summary.email) parts.push(summary.email);
1940:             const accounts = Array.isArray(summary.accountCodes) && summary.accountCodes.length
1941:                 ? summary.accountCodes.join("、")
1942:                 : "";
1943:             if (accounts) parts.push(`账号 ${accounts}`);
1944:             return parts.join(" · ");
1945:         }
1946: 
```

### L3279–L3328
```text
3279:         function saveConversationState() {
3280:             const contactId = Number(instance.selectedContactId);
3281:             if (!Number.isFinite(contactId) || contactId <= 0) return;
3282:             const items = instance.conversation.items || [];
3283:             if (items.length > MESSAGE_CACHE_LIMIT) {
3284:                 // 超限：不再保存，下次进入定位最新
3285:                 dropConversationRecord(instance.user, instance.conversation.accountScope || "", contactId);
3286:                 return;
3287:             }
3288:             const scroll = scrollEl();
3289:             const anchor = captureAnchor();
3290:             const drafts = currentDraftsMap();
3291:             const rec = upsertConversationRecord(instance.user, instance.conversation.accountScope || "", contactId, {
3292:                 items: items.slice(),
3293:                 nextBefore: instance.conversation.nextBefore,
3294:                 hasMore: instance.conversation.hasMore,
3295:                 anchorKey: anchor ? anchor.anchorKey : null,
3296:                 anchorRelTop: anchor ? anchor.anchorRelTop : 0,
3297:                 scrollTop: scroll ? getScrollTop() : 0,
3298:                 scrollTopValid: !!scroll,
3299:                 drafts: drafts || new Map()
3300:             });
3301:             instance.draftsRef = rec.drafts;
3302:         }
3303: 
3304:         function restoreFromRecord(record) {
3305:             if (!record) return;
3306:             if (record.anchorKey) {
3307:                 const scroll = scrollEl();
3308:                 if (scroll) {
3309:                     const top = contentOffsetTopForMessageKey(record.anchorKey, scroll);
3310:                     if (top !== null) {
3311:                         setScrollTop(top - (Number(record.anchorRelTop) || 0));
3312:                         return;
3313:                     }
3314:                 }
3315:             }
3316:             if (record.scrollTopValid) {
3317:                 setScrollTop(Number(record.scrollTop) || 0);
3318:                 return;
3319:             }
3320:             locateLatestTop();
3321:         }
3322: 
3323:         function onScroll() {
3324:             if (instance.disposed) return;
3325:             clearTimeout(instance.saveTimer);
3326:             const scroll = scrollEl();
3327:             if (!scroll) return;
3328:             instance.saveTimer = setTimeout(() => {
```

### L3730–L3757
```text
3730:             const meetingAttachment = ui && meetingCardContainerHtml(draft && draft.meeting ? draft.meeting : null) || "";
3731:             // fast-p 07（S-2）：通用附件草稿卡放在会议附件卡之后、发送 footer 之前。
3732:             const outboundFiles = outboundDraftFilesHtml(draft ? outboundAttachmentDraftOf(draft).items : []);
3733:             return `
3734:                 <div class="mc-compose" data-role="manual-compose" data-target-key="${escapeText(targetKey)}">
3735:                     <label>主题<input aria-label="回复主题" value="${escapeText(subjectValue)}"></label>
3736:                     <div class="mc-editor-tools">
3737:                         <button class="button" type="button" data-action="mc-rich-command" data-command="bold">B</button>
3738:                         <button class="button" type="button" data-action="mc-rich-command" data-command="italic">I</button>
3739:                         <button class="button" type="button" data-action="mc-rich-command" data-command="insertUnorderedList">列表</button>
3740:                         <button class="button" type="button" data-action="mc-rich-command" data-command="createLink">链接</button>
3741:                         <button class="button outbound-upload" type="button" data-action="mc-upload-attachment" title="上传附件" aria-label="上传附件"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false"><path d="m21.44 11.05-9.19 9.19a6 6 0 0 1-8.49-8.49l10.6-10.6a4 4 0 0 1 5.66 5.66L9.41 17.41a2 2 0 0 1-2.83-2.83l9.19-9.19"/></svg></button>
3742:                         <input type="file" data-role="outbound-file-input" multiple hidden>
3743:                         ${meetingTrigger}${materialTrigger}${templateReferenceTrigger}${followUpButton}
3744:                     </div>
3745:                     <div class="mc-editor" contenteditable="true" role="textbox" aria-multiline="true" aria-label="人工回复正文" data-role="mc-editor">${editorContent}</div>
3746:                     ${anchorNote}
3747:                     ${meetingAttachment}
3748:                     ${outboundFiles}
3749:                     <div class="mc-compose-footer">
3750:                         <span data-role="target-info">回复账号与目标来信信息：${targetInfo}</span>
3751:                         ${templateFollowButton}
3752:                         <button class="button primary" type="button" data-action="mc-send-manual">发送人工回复</button>
3753:                     </div>
3754:                 </div>
3755:             `;
3756:         }
3757: 
```

### L4676–L4728
```text
4676:         function saveDraftFromInputs(extra) {
4677:             const key = currentTargetKey();
4678:             if (!key) return;
4679:             const values = readManualValues();
4680:             if (!values) return;
4681:             const existing = getDraft(key);
4682:             const patch = extra || {};
4683:             // 跟进锚点（I-4）：patch 显式给值才改（null = 清除，如采用可信草稿/应用会议），
4684:             // 其余保存沿用既有草稿值。
4685:             const hasAnchorPatch = Object.prototype.hasOwnProperty.call(patch, "followUpAnchorMailRecordId");
4686:             const anchor = hasAnchorPatch
4687:                 ? (patch.followUpAnchorMailRecordId == null ? null : Number(patch.followUpAnchorMailRecordId))
4688:                 : (existing && existing.followUpAnchorMailRecordId != null
4689:                     ? Number(existing.followUpAnchorMailRecordId)
4690:                     : null);
4691:             // I-4/I-12：主题、正文或锚点相对上次保存有任何变化 → 旧 requestId 失效（置 null，
4692:             // 下次发送生成新值）；逐字未变化（重挂载/程序性重存）保留，保证失败重试仍
4693:             // 收敛到同一 attempt。成功删除草稿时 requestId 一并删除。
4694:             const contentChanged = !existing
4695:                 || existing.subject !== values.subject
4696:                 || existing.html !== values.html
4697:                 || existing.text !== values.text
4698:                 || (existing.followUpAnchorMailRecordId != null
4699:                     ? Number(existing.followUpAnchorMailRecordId)
4700:                     : null) !== anchor;
4701:             const requestId = contentChanged ? null : (existing.requestId || null);
4702:             // 会议快照（T3/S-3）：输入保存时随草稿持久化 —— 无显式 meeting patch 则沿用
4703:             // 既有草稿快照（不清除）；切专家/换 accountScope/target 时 targetKey 隔离，
4704:             // 不会跨目标串会议数据；会议块被手改时调用方经 patch 把 state 置 stale。
4705:             const hasMeetingPatch = Object.prototype.hasOwnProperty.call(patch, "meeting");
4706:             const meeting = hasMeetingPatch
4707:                 ? deepCopyMeeting(patch.meeting)
4708:                 : (existing && existing.meeting ? deepCopyMeeting(existing.meeting) : null);
4709:             const accountPatch = Object.prototype.hasOwnProperty.call(patch, "meetingAccountCode");
4710:             const meetingAccountCode = accountPatch
4711:                 ? String(patch.meetingAccountCode || "")
4712:                 : (existing && existing.meetingAccountCode != null ? String(existing.meetingAccountCode) : "");
4713:             setDraft(key, {
4714:                 subject: values.subject,
4715:                 html: values.html,
4716:                 text: values.text,
4717:                 qa: values.qa,
4718:                 requestId,
4719:                 updatedAt: new Date().toISOString(),
4720:                 meeting,
4721:                 meetingAccountCode,
4722:                 followUpAnchorMailRecordId: anchor,
4723:                 // fast-p 07（I-2）：通用附件是同一份草稿的字段，重建写点必须显式保留
4724:                 // （会议填入/采用草稿/程序性重存都不得清附件）。
4725:                 outboundAttachmentDraft: existing && existing.outboundAttachmentDraft
4726:                     ? existing.outboundAttachmentDraft
4727:                     : emptyOutboundAttachmentDraft()
4728:             });
```

### L6748–L6786
```text
6748:             notifyMeetingScheduleChanged(contactId);
6749:         }
6750: 
6751:         // ---- quiet refresh（I-5：合并保留已加载窗口） ----
6752: 
6753:         function refreshConversationQuiet() {
6754:             const contactId = Number(instance.selectedContactId);
6755:             if (!Number.isFinite(contactId) || contactId <= 0) return;
6756:             const myEpoch = instance.convEpoch;
6757:             const params = new URLSearchParams();
6758:             params.set("limit", String(MESSAGE_LIMIT));
6759:             const scopeAccount = instance.conversation.accountScope || "";
6760:             if (scopeAccount) params.set("accountCode", scopeAccount);
6761:             hostApi()(`/api/mail/mailbox/conversations/${contactId}/messages?${params.toString()}`).then((msgData) => {
6762:                 if (instance.disposed || myEpoch !== instance.convEpoch) return;
6763:                 const serverItems = (msgData && Array.isArray(msgData.items)) ? msgData.items : [];
6764:                 if (serverItems.length === 0 && (instance.conversation.items || []).length === 0) {
6765:                     instance.conversation.nextBefore = (msgData && msgData.nextBefore) || null;
6766:                     instance.conversation.hasMore = !!(msgData && msgData.hasMore);
6767:                     return;
6768:                 }
6769:                 instance.conversation.items = mergeServerIntoWindow(instance.conversation.items, serverItems);
6770:                 instance.conversation.nextBefore = (msgData && msgData.nextBefore) || null;
6771:                 instance.conversation.hasMore = !!(msgData && msgData.hasMore);
6772:                 renderTimeline();
6773:                 checkInboundChangeQuiet();
6774:                 saveConversationState();
6775:                 // c3（T-1）：现有刷新成功后按同一 contact 重读推荐（渲染只重绘状态行）。
6776:                 loadContactTiming(contactId);
6777:             }).catch(() => {});
6778:         }
6779: 
6780:         function checkInboundChangeQuiet() {
6781:             const summary = instance.selectedSummary || findSummaryByContactId(instance.selectedContactId);
6782:             if (!summary) return;
6783:             const latest = summary.latestInbound || null;
6784:             const mode = instance.manual.mode;
6785:             if (mode !== "inbound") return;
6786:             const currentProcessing = instance.manual.targetProcessingId;
```

### L6882–L6928
```text
6882:         function onClick(event) {
6883:             if (instance.disposed) return;
6884:             const target = event.target;
6885:             const button = target && typeof target.closest === "function"
6886:                 ? target.closest("[data-action]")
6887:                 : null;
6888:             const data = button ? (button.dataset || {}) : {};
6889:             const action = button ? data.action : "";
6890:             // 07（I-4）：发送中/已禁用时拦截已发/草稿附件下载锚点的默认跳转。
6891:             const downloadAnchor = target && typeof target.closest === "function"
6892:                 ? target.closest('[data-role="outbound-download"]')
6893:                 : null;
6894:             if (downloadAnchor && downloadAnchor.getAttribute && downloadAnchor.getAttribute("aria-disabled") === "true") {
6895:                 if (event && typeof event.preventDefault === "function") event.preventDefault();
6896:                 return;
6897:             }
6898:             if (action === "mc-select-expert") {
6899:                 const contactId = Number(data.contactId);
6900:                 const item = findSummaryByContactId(contactId);
6901:                 if (item) selectExpert(item);
6902:                 return;
6903:             }
6904:             if (action === "mc-toggle-follow") {
6905:                 toggleFollow(data.contactId);
6906:                 return;
6907:             }
6908:             if (action === "mc-dismiss-replied") {
6909:                 dismissReplied(data.contactId, button);
6910:                 return;
6911:             }
6912:             if (action === "mc-select-unmatched") {
6913:                 const unmatchedId = Number(data.unmatchedId);
6914:                 if (Number.isFinite(unmatchedId) && unmatchedId > 0) selectUnmatched(unmatchedId);
6915:                 return;
6916:             }
6917:             if (action === "mc-filter") {
6918:                 const chip = data.chip || CHIP_ALL;
6919:                 const nextChip = FILTER_CHIPS.some((entry) => entry.key === chip) ? chip : CHIP_ALL;
6920:                 if (nextChip !== instance.chip) {
6921:                     if (instance.chip === CHIP_UNMATCHED) leaveUnmatchedMode();
6922:                     if (nextChip === CHIP_UNMATCHED) {
6923:                         // 离开专家会话前保存草稿/滚动，但邮件 id 绝不写入 selectedContactId（I-6）。
6924:                         saveConversationState();
6925:                         clearSelectedConversation();
6926:                     }
6927:                 }
6928:                 instance.chip = nextChip;
```

### L7411–L7476
```text
7411:         function unmount() {
7412:             if (instance.disposed) return;
7413:             saveConversationState();
7414:             // 右栏/根节点清空前必须先归还详情面板 lease（I-5）。
7415:             clearUnmatchedState();
7416:             instance.disposed = true;
7417:             clearTimeout(instance.searchTimer);
7418:             clearTimeout(instance.saveTimer);
7419:             teardownConversationSubViews();
7420:             handlers.forEach((pair) => host.removeEventListener(pair[0], pair[1]));
7421:             handlers.length = 0;
7422:             portalHandlers.forEach((pair) => {
7423:                 const [root, type, fn] = pair;
7424:                 if (root && typeof root.removeEventListener === "function") root.removeEventListener(type, fn);
7425:             });
7426:             portalHandlers.length = 0;
7427:             const doc = docRoot();
7428:             if (doc && typeof doc.removeEventListener === "function") {
7429:                 doc.removeEventListener("click", onOutsideFilterClick);
7430:                 doc.removeEventListener("meeting-calendar-changed", onMeetingScheduleChanged);
7431:             }
7432:             restoreRefreshButton();
7433:             restoreLegacyFilterNodes();
7434:             removePortalRoot();
7435:             setRefined(false);
7436:             if (instance.host) instance.host.innerHTML = "";
7437:             instances.delete(instance.host);
7438:         }
7439: 
7440:         function refreshFromHost() {
7441:             if (instance.disposed) return;
7442:             saveConversationState();
7443:             return fetchList({ page: instance.list.page }).then((data) => {
7444:                 if (instance.disposed) return data;
7445:                 if (instance.selectedContactId != null) refreshConversationQuiet();
7446:                 return data;
7447:             });
7448:         }
7449: 
7450:         function refresh() {
7451:             if (instance.disposed) return Promise.resolve();
7452:             instance.list.page = 0;
7453:             return loadList();
7454:         }
7455: 
7456:         // --------------------------------------------------------------
7457:         // 组装
7458:         // --------------------------------------------------------------
7459: 
7460:         function attach() {
7461:             renderSkeleton();
7462:             rememberLegacyFilterTexts();
7463:             ensureFilterFieldsPresent();
7464:             renderFilterChrome();
7465:             syncSearchChrome();
7466:             const doc = docRoot();
7467:             if (doc && typeof doc.addEventListener === "function") {
7468:                 doc.addEventListener("click", onOutsideFilterClick);
7469:                 // fast-p 03（I-1/I-3）：排期写成功后由宿主广播，组件只刷新自己当前 owner
7470:                 doc.addEventListener("meeting-calendar-changed", onMeetingScheduleChanged);
7471:             }
7472:             bindFilterEvents();
7473:             ensurePortalRoot();
7474:             listenPortal("click", onClickPortal);
7475:             listenPortal("change", onMaterialRequestOptionChange);
7476:             listenPortal("change", onTemplateReferenceChange);
```

## src/main/resources/static/mailbox-chat.css

SHA256: `0fd354027e54ae69f0a2ba76451801b85c6b74cd2792e98a3852cbb14bade17d`；164 行。

### L1–L16
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
```

### L38–L71
```text
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
69: @media(max-width:1100px){.mail-chat{grid-template-columns:280px minmax(0,1fr);gap:12px}.mail-chat .mc-header{padding:14px}.mail-chat .mc-scroll{padding:14px}.mail-chat .mc-message{width:94%}}
70: @media(max-width:760px){.mail-chat{display:flex;flex-direction:column;height:auto;min-height:0;gap:12px}.mail-chat .mc-experts{max-height:320px;min-height:240px}.mail-chat .mc-expert-list{min-height:100px}.mail-chat .mc-conversation{min-height:560px}.mail-chat .mc-scroll{max-height:none;overflow:visible;padding:12px}.mail-chat .mc-message{width:100%}.mail-chat .mc-header{padding:12px}.mail-chat .mc-identity{min-width:0;width:100%;flex-basis:100%}.mail-chat .mc-section-content{padding:12px}.mail-chat .mc-actions{width:100%}}
71: @media(prefers-reduced-motion:reduce){.mail-chat .button{transition:none}.mail-chat .button:hover,.mail-chat .button:active{transform:none}}
```

### L133–L164
```text
133: .mail-chat .mc-manage-dialog header{display:flex;align-items:flex-start;justify-content:space-between;padding:18px 20px;border-bottom:1px solid #edf1f7}
134: .mail-chat .mc-manage-dialog h3{font-size:16px;font-weight:600}
135: .mail-chat .mc-manage-dialog header p{margin-top:6px;color:#96a6ba;font-size:11px}
136: .mail-chat .mc-status-grid{display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1fr);gap:16px;padding:22px 20px}
137: .mail-chat .mc-settings-tags{padding:0 20px 20px}
138: .mail-chat .mc-settings-tags .expert-tag-editor{margin:0;padding:0;border:0;background:transparent;box-shadow:none}
139: .mail-chat .mc-settings-tags .tag-editor-loading{min-height:0}
140: .mail-chat .mc-settings-tags .inbound-tag-editor-head{display:flex;align-items:center;justify-content:space-between;gap:8px;margin:0 0 12px}
141: .mail-chat .mc-settings-tags .inbound-tag-editor-head h3{font-size:12px}
142: .mail-chat .mc-settings-tags .inbound-tag-editor-chips{display:flex;flex-wrap:wrap;gap:6px}
143: .mail-chat .mc-settings-tags .expert-tag{font-size:11px;line-height:1.6;padding:2px 7px;border-radius:5px;margin:0}
144: .mail-chat .mc-manage-note{padding:0 20px 16px;color:#8b9bb1;font-size:11px;line-height:1.7}
145: .mail-chat .mc-dialog-actions{display:flex;justify-content:flex-end;gap:8px;padding:14px 20px;border-top:1px solid #edf1f7}
146: .mail-chat .mc-dialog-actions .button{width:auto;flex:none}
147: .mail-chat .button{height:32px;min-height:32px;padding:0 11px;font-size:12px;border-radius:7px}
148: .mail-chat .button:not(.primary){box-shadow:none}
149: .mail-chat .mc-manage-dialog .mc-inline-error,.mail-chat .mc-filter-popover .mc-inline-error{padding:0 20px 12px}
150: @media(max-width:1100px){.mail-chat{grid-template-columns:275px minmax(0,1fr);gap:12px}.mail-chat .mc-header{padding:15px}.mail-chat .mc-timeline-head{padding:10px 15px}.mail-chat .mc-message{width:96%}.mail-chat .mc-header .button{font-size:11px;padding:0 8px}}
151: @media(max-width:760px){#view-mailbox.mc-refined #mailboxList{padding:12px}.mail-chat{height:auto;min-height:0}.mail-chat .mc-search-row{position:static}.mail-chat .mc-filter-popover{position:fixed;top:100px;left:16px;width:calc(100vw - 32px);max-width:none;max-height:calc(100dvh - 116px)}.mail-chat .mc-scroll{max-height:65dvh;overflow:auto}.mail-chat .mc-message{width:100%}.mail-chat .mc-timeline-head{padding:10px 12px}.mail-chat .mc-position-hint{display:none}.mail-chat .mc-status-grid{grid-template-columns:1fr}.mail-chat .mc-manage-dialog{max-height:calc(100dvh - 32px)}}
152: @media(prefers-reduced-motion:reduce){.mail-chat *{scroll-behavior:auto!important;transition:none!important}}
153: 
154: .mail-chat.mc-overlay-root{display:contents}
155: 
156: /* S-7: expert tags in the list, one line only. */
157: .mail-chat .mc-person-heading{display:flex;align-items:center;gap:6px;min-width:0}
158: .mail-chat .mc-person-heading strong{flex:1;min-width:0}
159: .mail-chat .mc-person-meta{flex-wrap:nowrap;min-width:0;gap:6px;max-width:100%}
160: .mail-chat .mc-person-counts{flex:none;white-space:nowrap;font-size:11px;color:#79899f}
161: .mail-chat .mc-person-tags{display:block;flex:1;min-width:0;overflow:hidden;white-space:nowrap;text-overflow:ellipsis;line-height:20px;color:#6482b2;cursor:help}
162: .mail-chat .mc-person-tag{display:inline;padding:2px 5px;margin-right:4px;border:1px solid #d5e2fb;border-radius:5px;background:#edf3ff;color:#5476ba;font-size:10px;line-height:16px;white-space:nowrap}
163: .mail-chat .mc-person-tags:hover .mc-person-tag{background:#e6efff;border-color:#b8cef3}
164: .mail-chat .mc-person-tags-unavailable{color:#94a3b8;font-size:10px}
```

## 相关选择器全部使用位置

```text
src/main/resources/static/mailbox-chat.css:5:.mail-chat .mc-experts,.mail-chat .mc-conversation{display:flex;flex-direction:column;min-width:0;min-height:0;border:1px solid rgba(15,23,42,.11);border-radius:14px;background:#f8faff;overflow:hidden}
src/main/resources/static/mailbox-chat.css:70:@media(max-width:760px){.mail-chat{display:flex;flex-direction:column;height:auto;min-height:0;gap:12px}.mail-chat .mc-experts{max-height:320px;min-height:240px}.mail-chat .mc-expert-list{min-height:100px}.mail-chat .mc-conversation{min-height:560px}.mail-chat .mc-scroll{max-height:none;overflow:visible;padding:12px}.mail-chat .mc-message{width:100%}.mail-chat .mc-header{padding:12px}.mail-chat .mc-identity{min-width:0;width:100%;flex-basis:100%}.mail-chat .mc-section-content{padding:12px}.mail-chat .mc-actions{width:100%}}
src/main/resources/static/mailbox-chat.css:77:.mail-chat .mc-experts{overflow:visible;position:relative}
src/main/resources/static/styles.css:795:.back-to-list {
src/main/resources/static/styles.css:906:.contacts-layout {
src/main/resources/static/styles.css:1236:.contacts-list-panel {
src/main/resources/static/styles.css:1242:.contacts-list-panel .panel-head {
src/main/resources/static/styles.css:1246:.contacts-layout .list {
src/main/resources/static/styles.css:1490:.contact-detail-panel {
src/main/resources/static/styles.css:1496:.contact-detail-panel .panel-head {
src/main/resources/static/styles.css:4219:    .contacts-layout {
src/main/resources/static/styles.css:4255:    .back-to-list {
src/main/resources/static/styles.css:4261:    .contacts-layout {
src/main/resources/static/styles.css:4287:    .contact-detail-panel {
src/main/resources/static/styles.css:4309:    .contacts-list-panel {
src/main/resources/static/styles.css:4314:    .contacts-layout .list {
src/main/resources/static/styles.css:11467:.calendar-scroll{overflow:auto;border:1px solid #dce4ef;border-radius:14px;background:#fff}
src/main/resources/static/styles.css:11522:.calendar-root .calendar-scroll{border:0;border-radius:0}
src/main/resources/static/styles.css:12131:.task-center-grid {
src/main/resources/static/styles.css:12288:.task-center-log {
src/main/resources/static/styles.css:12311:    .task-center-grid {
src/main/resources/static/styles.css:12316:    .task-center-grid {
src/main/resources/static/styles.css:12337:    .task-center-log {
src/main/resources/static/index.html:20:    <div class="auth-card">
src/main/resources/static/index.html:39:    <div class="auth-card">
src/main/resources/static/index.html:62:    <header class="topnav task-center-nav">
src/main/resources/static/index.html:77:        <nav class="nav-tabs" aria-label="Main">
src/main/resources/static/index.html:149:        <div class="topnav-side">
src/main/resources/static/index.html:691:            <div class="split-layout contacts-layout">
src/main/resources/static/index.html:693:                <section class="panel contacts-list-panel">
src/main/resources/static/index.html:723:                <section class="panel contact-detail-panel">
src/main/resources/static/index.html:1031:                <div id="taskActiveCards" class="task-center-grid"></div>
src/main/resources/static/index.html:1065:                <pre id="taskActiveLogs" class="task-center-log" hidden></pre>
src/main/resources/static/index.html:2239:<p class="calendar-note" data-role="time-summary"></p><p class="calendar-note" data-role="source-summary"></p><p class="calendar-note">排期操作不会自动发送通知邮件。</p><p class="calendar-error" role="alert" data-role="calendar-error" hidden></p><div class="calendar-list" data-role="expert-events" hidden></div><div class="calendar-actions"><button class="button" type="button" data-calendar-action="close">关闭</button><button class="button danger" type="button" data-calendar-action="cancel-event">取消排期</button><button class="button primary" type="submit">保存排期</button></div>
src/main/resources/static/mailbox-chat.js:122:    // 直接挂 document.body（.mc-conversation overflow:hidden、.panel backdrop-filter
src/main/resources/static/mailbox-chat.js:1027:                <div class="mail-chat">
src/main/resources/static/mailbox-chat.js:1028:                    <aside class="mc-experts" aria-label="专家会话列表">
src/main/resources/static/mailbox-chat.js:1048:                    <section class="mc-conversation" aria-label="专家往来信件"></section>
src/main/resources/static/mailbox-chat.js:1062:            return host.querySelector ? host.querySelector(".mc-conversation") : null;
src/main/resources/static/app.js:9213:        <button class="button small back-to-list" onclick="scrollBackToContactsList()">
src/main/resources/static/app.js:9220:    document.querySelector(".contacts-list-panel")?.scrollIntoView({ behavior: "smooth", block: "start" });
src/main/resources/static/app.js:9224:    document.querySelector(".contacts-list-panel")?.scrollIntoView({ behavior: "smooth", block: "start" });
src/main/resources/static/app.js:9484:            document.querySelector(".contact-detail-panel")?.scrollIntoView({ behavior: "smooth" });
src/main/resources/static/app.js:10086:            document.querySelector(".contact-detail-panel")?.scrollIntoView({ behavior: "smooth" });
src/main/resources/static/app.js:16341:    const container = document.querySelector(".contacts-layout");
src/main/resources/static/app.js:16342:    const listPanel = document.querySelector(".contacts-list-panel");
src/main/resources/static/app.js:21268:    + '<div class="calendar-scroll"><div class="calendar-grid" data-role="calendar-grid"></div></div>'
src/main/resources/static/app.js:21269:    + '<div class="calendar-list" data-role="calendar-list" hidden></div>'
src/main/resources/static/app.js:21427:    const scroll = root.querySelector(".calendar-scroll");
```

## 草稿与位置读写入口

```text
src/main/resources/static/mailbox-chat.js:524:        sessionStore.delete(key);
src/main/resources/static/mailbox-chat.js:525:        sessionStore.set(key, rec);
src/main/resources/static/mailbox-chat.js:532:            sessionStore.delete(key);
src/main/resources/static/mailbox-chat.js:533:            sessionStore.set(key, rec);
src/main/resources/static/mailbox-chat.js:569:        sessionStore.delete(key);
src/main/resources/static/mailbox-chat.js:570:        sessionStore.set(key, rec);
src/main/resources/static/mailbox-chat.js:581:            if (oldestKey !== null && oldestKey !== key) sessionStore.delete(oldestKey);
src/main/resources/static/mailbox-chat.js:587:        sessionStore.delete(conversationCacheKey(user, accountScope, contactId));
src/main/resources/static/mailbox-chat.js:753:        function getDraft(targetKey) {
src/main/resources/static/mailbox-chat.js:759:        function setDraft(targetKey, draft) {
src/main/resources/static/mailbox-chat.js:765:        function deleteDraft(targetKey) {
src/main/resources/static/mailbox-chat.js:1615:        function selectUnmatched(id) {
src/main/resources/static/mailbox-chat.js:1815:        function selectExpert(item, options) {
src/main/resources/static/mailbox-chat.js:3279:        function saveConversationState() {
src/main/resources/static/mailbox-chat.js:3304:        function restoreFromRecord(record) {
src/main/resources/static/mailbox-chat.js:4409:        function setOutboundAttachmentItems(captured, items) {
src/main/resources/static/mailbox-chat.js:4414:            captured.draftsMap.set(captured.targetKey, Object.assign({}, existing, {
src/main/resources/static/mailbox-chat.js:4422:        function invalidateOutboundRequestId(captured) {
src/main/resources/static/mailbox-chat.js:4426:            captured.draftsMap.set(captured.targetKey, Object.assign({}, existing, {
src/main/resources/static/mailbox-chat.js:4676:        function saveDraftFromInputs(extra) {
src/main/resources/static/mailbox-chat.js:6679:                        draftsMap.delete(key);
src/main/resources/static/mailbox-chat.js:6692:                        draftsMap.set(key, nextDraft);
src/main/resources/static/mailbox-chat.js:6702:                if (cleared) draftsMap.delete(key);
src/main/resources/static/mailbox-chat.js:6753:        function refreshConversationQuiet() {
src/main/resources/static/mailbox-chat.js:7411:        function unmount() {
src/main/resources/static/mailbox-chat.js:7529:    function unmount(host) {
src/main/resources/static/app.js:70:        viewMode: "MAIL",
src/main/resources/static/app.js:181:function unmountAiTrainingTrustReply() {
src/main/resources/static/app.js:194:function unmountLiveTrustReply() {
src/main/resources/static/app.js:203:function unmountMailboxTrustReplyHosts() {
src/main/resources/static/app.js:10189:function unmountExpertMaterialsHosts(rootEl) {
src/main/resources/static/app.js:12073:    state.mailbox.viewMode = "EXPERT";
src/main/resources/static/app.js:16350:    const savedWidth = localStorage.getItem("contacts-list-width");
src/main/resources/static/app.js:16362:            localStorage.setItem("contacts-list-width", targetWidth);
src/main/resources/static/app.js:16615:    state.mailbox.viewMode = expertMode ? "EXPERT" : "MAIL";
src/main/resources/static/app.js:16636:function unmountMailboxChatHosts() {
src/main/resources/static/app.js:16959:    const expertMode = state.mailbox.viewMode === "EXPERT";
src/main/resources/static/app.js:17159:    const unitLabel = state.mailbox.viewMode === "EXPERT" ? "位专家" : "条";
src/main/resources/static/app.js:21008:    viewMode: "month",      // "month" | "list"
src/main/resources/static/app.js:21419:    if (monthButton) monthButton.setAttribute("aria-pressed", meetingCalendarState.viewMode === "month" ? "true" : "false");
src/main/resources/static/app.js:21421:    if (listButton) listButton.setAttribute("aria-pressed", meetingCalendarState.viewMode === "list" ? "true" : "false");
src/main/resources/static/app.js:21426:    const monthView = meetingCalendarState.viewMode === "month";
src/main/resources/static/app.js:21906:            meetingCalendarState.viewMode = action;
```

## 导航和详情入口

```text
src/main/resources/static/index.html:78:            <button class="nav-tab" data-view="monitoring">
src/main/resources/static/index.html:84:            <button class="nav-tab active" data-view="accounts">
src/main/resources/static/index.html:90:            <button class="nav-tab" data-view="mail-templates">
src/main/resources/static/index.html:99:            <button class="nav-tab" data-view="suppressions">
src/main/resources/static/index.html:105:            <button class="nav-tab" data-view="contacts">
src/main/resources/static/index.html:112:            <button class="nav-tab" data-view="mailbox">
src/main/resources/static/index.html:121:            <button class="nav-tab" data-view="meeting-calendar"><svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><rect x="3" y="5" width="18" height="16" rx="2"/><path d="M16 3v4M8 3v4M3 11h18"/></svg><span>会议日历</span></button>
src/main/resources/static/index.html:122:            <button class="nav-tab" data-view="inbound-summary">
src/main/resources/static/index.html:129:            <button class="nav-tab" data-view="ai-training">
src/main/resources/static/index.html:137:            <button class="nav-tab" data-view="tasks">
src/main/resources/static/app.js:1556:        globalBtn.addEventListener("click", () => setView("tasks"));
src/main/resources/static/app.js:3214:function setView(view) {
src/main/resources/static/app.js:3259:async function refreshCurrentView() {
src/main/resources/static/app.js:9219:function scrollBackToContactsList() {
src/main/resources/static/app.js:9223:function scrollBackToContactsList() {
src/main/resources/static/app.js:9357:async function showExpertDetail(expert) {
src/main/resources/static/app.js:9660:    await loadContactDetail(contactId);
src/main/resources/static/app.js:9786:async function loadContactDetail(contactId) {
src/main/resources/static/app.js:10099:async function openContactInList(contactId) {
src/main/resources/static/app.js:10100:    setView("contacts");
src/main/resources/static/app.js:10104:    const contact = await loadContactDetail(contactId);
src/main/resources/static/app.js:11448:    setView("mail-templates");
src/main/resources/static/app.js:12084:    setView("mailbox");
src/main/resources/static/app.js:12106:            await loadContactDetail(expert.contactId);
src/main/resources/static/app.js:12108:            await showExpertDetail(expert);
src/main/resources/static/app.js:12113:        await loadContactDetail(id);
src/main/resources/static/app.js:12136:        await loadContactDetail(id);
src/main/resources/static/app.js:12147:        await loadContactDetail(id);   // I-4
src/main/resources/static/app.js:12157:        await loadContactDetail(id);
src/main/resources/static/app.js:12236:        await loadContactDetail(id);
src/main/resources/static/app.js:12243:        await loadContactDetail(id);
src/main/resources/static/app.js:12250:        await loadContactDetail(id);
src/main/resources/static/app.js:12259:        await loadContactDetail(id);
src/main/resources/static/app.js:12279:        await loadContactDetail(id);
src/main/resources/static/app.js:12298:        await loadContactDetail(contactId);
src/main/resources/static/app.js:12310:        await loadContactDetail(contactId);
src/main/resources/static/app.js:12321:        await loadContactDetail(contactId);
src/main/resources/static/app.js:12555:    setView("mailbox");
src/main/resources/static/app.js:14848:            setView("mailbox");
src/main/resources/static/app.js:14866:        setView(tab.dataset.view);
src/main/resources/static/app.js:15265:                await loadContactDetail(contactId);
src/main/resources/static/app.js:15686:            setView("mailbox");
src/main/resources/static/app.js:15717:                loadContactDetail(contactId);
src/main/resources/static/app.js:15729:                loadContactDetail(contactId);
```

## 缓存键反查

键 `20261002-mailbox-last-reply`；src/test 反查 exit=1。
```text
无匹配
```

## 补充：scope 切换、登录导航、既有浮层 CSS

### src/main/resources/static/mailbox-chat.js:L7387–L7411
```text
7387:             const next = options || {};
7388:             if (next.focus && next.focus.contactId != null) {
7389:                 instance.options.focus = { contactId: next.focus.contactId, email: next.focus.email || "" };
7390:             }
7391:             if (next.filters) {
7392:                 // 快照完整替换，不能合并残留旧值（I-2）
7393:                 const prevAccount = String(instance.filters.accountCode || "");
7394:                 instance.filters = Object.assign({}, next.filters);
7395:                 meetingCloseDisposeOnAccountScopeChange(prevAccount, String(next.filters.accountCode || ""));
7396:                 // 仅初次（用户尚未操作 tab）允许外部 onlyPending 初始化
7397:                 if (typeof next.filters.pendingOnly === "boolean" && !instance.chipUserTouched) {
7398:                     if (next.filters.pendingOnly && instance.chip !== CHIP_PENDING) {
7399:                         instance.chip = CHIP_PENDING;
7400:                         syncChipButtons();
7401:                     } else if (!next.filters.pendingOnly && instance.chip === CHIP_PENDING) {
7402:                         instance.chip = CHIP_ALL;
7403:                         syncChipButtons();
7404:                     }
7405:                 }
7406:                 renderFilterChrome();
7407:             }
7408:             return next;
7409:         }
7410: 
7411:         function unmount() {
```

### src/main/resources/static/app.js:L14862–L14872
```text
14862: function bindEvents() {
14863:     ensureTranslateClickHandler();
14864:     $$(".nav-tab").forEach((tab) => tab.addEventListener("click", () => {
14865:         if (tab.dataset.view === "mailbox") clearMailboxExpertFocus();
14866:         setView(tab.dataset.view);
14867:     }));
14868:     $("#refreshBtn").addEventListener("click", () => {
14869:         refreshCurrentView();
14870:         // "刷新"同时补一轮运行卡片；查询参数与历史页码不动（T-3）。
14871:         if (taskActivityState.started) refreshTaskActivity();
14872:     });
```

### src/main/resources/static/styles.css:L12392–L12460
```text
12392: .task-center-interrupt {
12393:     display: grid;
12394:     gap: 8px;
12395:     max-width: 560px;
12396:     margin-top: 12px;
12397: }
12398: .task-center-interrupt[hidden] { display: none; }
12399: .task-center-interrupt label { color: var(--text-muted); font-size: 12px; }
12400: .task-center-interrupt select,
12401: .task-center-interrupt textarea {
12402:     width: 100%;
12403:     padding: 8px 10px;
12404:     border: 1px solid var(--border);
12405:     border-radius: 8px;
12406:     background: var(--surface);
12407:     color: var(--text-main);
12408:     font: inherit;
12409: }
12410: .task-center-interrupt button { justify-self: start; }
12411: /* task-center-contract:end */
12412: 
12413: /* contact-timing S-1 */
12414: .mail-chat .contact-timing{display:inline-flex;align-items:center;flex-wrap:wrap;gap:9px;margin-left:auto;padding-left:10px;border-left:1px solid #e1e8f2;min-height:22px;max-width:100%;font-size:11px;line-height:1.6}
12415: .mail-chat .contact-timing-location{max-width:140px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;color:#61748e}
12416: .mail-chat .contact-timing-recommend{display:inline-flex;align-items:center;gap:5px;white-space:nowrap;color:#8494aa;font-size:11px}
12417: .mail-chat .contact-timing-recommend strong{font-size:12px;font-weight:600;color:#285ac0;font-variant-numeric:tabular-nums}
12418: .mail-chat .contact-timing-info{justify-content:center;width:20px;height:22px;padding:0;font-size:14px;color:#8a9bb2}
12419: .mail-chat .contact-timing-note{color:#8494aa;font-size:11px}
12420: .mail-chat .contact-timing .mc-text-button:hover{color:#244ca9;background:#edf3ff}
12421: .mail-chat .contact-timing .mc-text-button:active{background:#dbeafe}
12422: .mail-chat .contact-timing .mc-text-button:disabled{opacity:.45;cursor:not-allowed}
12423: .mail-chat .contact-timing .mc-text-button:focus-visible{outline:2px solid #6389d3;outline-offset:2px}
12424: 
12425: /* contact-timing S-2 */
12426: .contact-timing-dialog{position:fixed;inset:0;margin:auto;padding:0;width:440px;max-width:calc(100vw - 32px);height:fit-content;max-height:calc(100dvh - 48px);overflow:auto;border:1px solid #d9e3f1;border-radius:14px;background:#fff;color:#475d79;box-shadow:0 20px 90px #17325730;font-family:var(--font-body);font-size:12px;line-height:1.6}
12427: .contact-timing-dialog[open]{display:flex;flex-direction:column}
12428: .contact-timing-dialog[data-kind=evidence]{width:360px}
12429: .contact-timing-dialog,.contact-timing-dialog *{box-sizing:border-box}
12430: .contact-timing-dialog [hidden]{display:none!important}
12431: .contact-timing-dialog::backdrop{background:#172c4738;backdrop-filter:blur(2px)}
12432: .contact-timing-dialog header{display:flex;align-items:flex-start;justify-content:space-between;gap:12px;padding:18px 20px;border-bottom:1px solid #edf1f7}
12433: .contact-timing-dialog h3{margin:0;color:#475d79;font-size:16px;font-weight:600;line-height:1.6}
12434: .contact-timing-dialog header p{margin:6px 0 0;color:#8799b0;font-size:11px;line-height:1.6;overflow-wrap:anywhere}
12435: .contact-timing-dialog form{margin:0;min-width:0}
12436: .contact-timing-body{display:flex;flex-direction:column;gap:12px;padding:20px;min-width:0}
12437: .contact-timing-field{display:flex;flex-direction:column;gap:8px;color:#61748e;font-size:12px}
12438: .contact-timing-field select{width:100%;min-width:0;height:37px;min-height:37px;margin:0;padding:0 10px;border:1px solid #dce4ef;border-radius:7px;background:#fcfdff;color:#475d79;font:inherit;font-size:12px}
12439: .contact-timing-field select:hover{border-color:#93b4ec}
12440: .contact-timing-field select:focus-visible{outline:2px solid #6389d3;outline-offset:2px}
12441: .contact-timing-field select:disabled{opacity:.55;cursor:not-allowed}
12442: .contact-timing-dialog .contact-timing-help{margin:0;color:#71849f;font-size:11px;line-height:1.7;overflow-wrap:anywhere}
12443: .contact-timing-dialog .contact-timing-error{margin:0;color:#be123c;font-size:12px;line-height:1.6;overflow-wrap:anywhere}
12444: .contact-timing-range{display:flex;flex-direction:column;gap:4px;color:#61748e;font-size:12px;font-variant-numeric:tabular-nums}
12445: .contact-timing-range strong{color:#285ac0;font-size:13px;font-weight:600}
12446: .contact-timing-history{display:flex;flex-direction:column;gap:6px;margin:0;padding:10px 0 0;list-style:none;border-top:1px solid #edf1f7;color:#71849f;font-size:11px;font-variant-numeric:tabular-nums}
12447: .contact-timing-history li{display:flex;justify-content:space-between;flex-wrap:wrap;gap:4px 12px}
12448: .contact-timing-dialog footer{display:flex;justify-content:flex-end;gap:9px;padding:14px 20px;border-top:1px solid #edf1f7}
12449: .contact-timing-dialog .button{min-height:33px;height:33px;padding:0 13px;font-size:12px}
12450: .contact-timing-dialog .button:focus-visible{outline:2px solid #6389d3;outline-offset:2px}
12451: .contact-timing-dialog .button:disabled{opacity:.45;cursor:not-allowed;transform:none;box-shadow:none}
12452: .contact-timing-dialog .contact-timing-close{width:28px;height:28px;min-height:28px;padding:0;border:0;background:transparent;color:#91a1b7;font-size:23px}
12453: .contact-timing-dialog .contact-timing-close:hover{background:#edf3ff;color:#2451b9}
12454: .contact-timing-dialog .contact-timing-close:active{background:#dbeafe}
12455: 
12456: /* contact-timing S-3 */
12457: @media(max-width:760px){.mail-chat .contact-timing{margin-left:0;padding-left:0;border-left:0}.contact-timing-dialog{max-height:calc(100dvh - 24px)}.contact-timing-dialog header{padding:14px 16px}.contact-timing-body{padding:16px}.contact-timing-dialog footer{padding:12px 16px}}
12458: 
12459: /* 所在地选项搜索 */
12460: .contact-timing-field input[type=search]{width:100%;min-width:0;height:37px;min-height:37px;margin:0;padding:0 10px;border:1px solid #dce4ef;border-radius:7px;background:#fcfdff;color:#475d79;font:inherit;font-size:12px}
```

### src/main/resources/static/meeting-confirmation.css:L1–L103
```text
1: /* 专家会议确认：只限新组件及新生成正文块。 */
2: .mail-chat .button.meeting-trigger{color:#1e40af;border-color:#b8cef5;background:#eef4ff;gap:7px;margin-left:3px}
3: .mail-chat .button.meeting-trigger:hover{background:#e5eeff;border-color:#93b4ec}
4: .mail-chat .button.meeting-trigger:active{background:#dbeafe}
5: .meeting-icon{display:inline-flex;font-size:16px;line-height:1}
6: .meeting-dialog{margin:auto;inset:0;width:min(1080px,calc(100vw - 40px));max-height:calc(100dvh - 40px);padding:0;border:1px solid #d5dfed;border-radius:14px;background:#fff;color:#334155;font-family:var(--font-body);font-size:12px;line-height:1.6;box-shadow:0 24px 100px #172c473d;overflow:auto;overscroll-behavior:contain}
7: .meeting-dialog::backdrop{background:#182a464f;backdrop-filter:blur(2px)}
8: .meeting-dialog *{box-sizing:border-box}
9: .meeting-dialog [hidden],.mail-chat .meeting-attachment[hidden]{display:none!important}
10: .meeting-dialog form{margin:0;padding:0}
11: .meeting-head{display:flex;align-items:center;justify-content:space-between;padding:20px 24px;border-bottom:1px solid #e2e8f0;gap:16px;position:sticky;top:0;background:#fff;z-index:2}
12: .meeting-head h2{margin:0;font-size:19px;font-weight:600;color:#334155;line-height:1.4}
13: .meeting-head p{margin:6px 0 0;font-size:12px;color:#64748b;line-height:1.6;overflow-wrap:anywhere}
14: .meeting-close{display:inline-flex;align-items:center;justify-content:center;flex:none;width:28px;height:28px;padding:0;border:0;border-radius:5px;background:transparent;color:#91a1b7;font:inherit;font-size:21px;line-height:1;cursor:pointer}
15: .meeting-close:hover{background:#edf3ff;color:#2451b9}
16: .meeting-close:active{background:#dbeafe}
17: .meeting-grid{display:grid;grid-template-columns:45% 55%}
18: .meeting-form{padding:18px 24px;border-right:1px solid #e2e8f0;min-width:0}
19: .meeting-preview{padding:22px 24px;background:#f8faff;min-width:0}
20: .meeting-form label{display:flex;flex-direction:column;gap:6px;font-size:12px;line-height:18px;color:#52647e;margin-bottom:12px;font-weight:500;letter-spacing:0;text-transform:none;min-width:0}
21: .meeting-form input,.meeting-form select,.meeting-form textarea{min-width:0;width:100%;height:36px;min-height:36px;margin:0;padding:7px 10px;font:inherit;font-weight:400;color:#334155;border:1px solid #d7e0ed;border-radius:7px;background:#fff;box-shadow:none}
22: .meeting-form textarea{height:70px;resize:vertical;line-height:1.6}
23: .meeting-form input::placeholder,.meeting-form textarea::placeholder{color:#94a3b8;opacity:1}
24: .meeting-form small{font-size:11px;font-weight:400;color:#76859b;line-height:16px}
25: .meeting-form :is(input,textarea,select):hover:not(:disabled){border-color:#93b4ec}
26: .meeting-form :is(input,textarea,select)[aria-invalid=true]{border-color:#e11d48;background:#fff8fa}
27: .meeting-form :is(input,textarea):read-only{background:#f8faff}
28: .meeting-form :is(input,textarea,select):disabled{opacity:.55;cursor:not-allowed;background:#f1f5f9}
29: .meeting-fields{display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1fr);gap:12px}
30: .meeting-clock{margin:-3px 0 14px;padding:11px 12px;border:1px solid #dce7fa;border-radius:7px;background:#f0f5ff;font-size:12px;line-height:1.65;color:#47658b}
31: .meeting-clock b{font-weight:500;color:#234f99}
32: .meeting-preview h3{font-size:12px;font-weight:600;margin:0 0 12px;color:#536680;display:flex;align-items:center;justify-content:space-between;gap:8px;line-height:1.6}
33: .meeting-preview h3>span:last-child{font-weight:400;color:#73859c;font-size:11px}
34: .meeting-step{display:inline-block;color:#1e40af;background:#eff5ff;border:1px solid #d6e3f8;border-radius:5px;padding:3px 7px;font-size:11px;margin-right:9px}
35: .meeting-paper{background:#fff;border:1px solid #dce4ef;border-radius:9px;padding:20px 22px;font-size:13px;line-height:1.85;overflow-wrap:anywhere;min-height:342px;color:#334155}
36: .meeting-paper p{margin:0 0 14px;color:inherit;font-size:inherit;line-height:inherit}
37: .meeting-paper p:last-child{margin:0}
38: .meeting-paper a,.mail-chat .meeting-body-block a{color:#2563b1;text-decoration:underline;overflow-wrap:anywhere}
39: .meeting-paper a:hover,.mail-chat .meeting-body-block a:hover{color:#1e40af}
40: .meeting-paper a:active,.mail-chat .meeting-body-block a:active{color:#172554}
41: .meeting-file{display:flex;align-items:flex-start;gap:11px;padding:13px 14px;margin-top:14px;border:1px solid #d8e3f2;border-radius:8px;background:#fff;min-width:0}
42: .meeting-file-icon{flex:none;display:grid;place-items:center;width:36px;height:42px;background:#edf4ff;border:1px solid #cbdcf6;border-radius:6px;font-size:11px;color:#3964a4;font-weight:600}
43: .meeting-file-main{flex:1;min-width:0}
44: .meeting-file-main strong{display:block;font-size:12px;font-weight:500;overflow-wrap:anywhere;color:#435b7c;line-height:1.6}
45: .meeting-file-main small{display:block;font-size:11px;color:#788ba5;margin-top:5px;line-height:1.6}
46: .meeting-file-actions{display:flex;flex-wrap:wrap;gap:14px;align-items:center;margin-top:9px}
47: .meeting-link{display:inline-flex;align-items:center;border:0;border-radius:3px;background:none;color:#315fa7;font-family:inherit;font-size:12px;line-height:1.6;cursor:pointer;padding:0;text-decoration:none;white-space:nowrap}
48: .meeting-link:hover{color:#244ca9;text-decoration:underline}
49: .meeting-link:active{color:#172554;background:#edf3ff}
50: .meeting-raw{font-family:ui-monospace,monospace;font-size:11px;line-height:1.6;color:#62768e;white-space:pre-wrap;overflow-wrap:anywhere;max-height:160px;overflow:auto;padding:12px;border:1px solid #dae3ef;border-radius:7px;background:#fff;margin:12px 0 0}
51: .meeting-bottom{display:flex;align-items:center;justify-content:space-between;gap:14px;padding:16px 24px;border-top:1px solid #e2e8f0;background:#fff;position:sticky;bottom:0;z-index:1}
52: .meeting-bottom p{font-size:12px;line-height:1.6;color:#76859b;margin:0}
53: .meeting-bottom>div{display:flex;gap:9px;flex:none}
54: .meeting-dialog .button{min-height:36px;height:36px;font-size:12px;padding:0 15px}
55: .meeting-error{font-size:12px;line-height:1.6;padding:10px 14px;color:#be123c;border:1px solid #fecdd3;background:#fff1f2;border-radius:7px;margin:12px 0;overflow-wrap:anywhere}
56: .meeting-status{font-size:12px;line-height:1.6;padding:10px 14px;color:#47658b;border:1px solid #dce7fa;background:#f0f5ff;border-radius:7px;margin:0 0 12px}
57: .meeting-status .meeting-link{margin-left:10px}
58: .meeting-note{font-size:11px;line-height:1.7;color:#73859d;margin:12px 0 0}
59: .meeting-template{margin:0 0 14px;font-size:12px}
60: .meeting-template summary{color:#466795;cursor:pointer;font-size:12px;line-height:1.6}
61: .meeting-template summary:hover{color:#1e40af}
62: .meeting-template summary:active{color:#172554}
63: .meeting-form .meeting-template textarea{height:245px;margin:12px 0 8px;font-family:ui-monospace,monospace;font-size:11px}
64: .meeting-template code{font-size:11px;color:#466795}
65: .meeting-template p{font-size:11px;color:#73859d;line-height:1.8;margin:8px 0}
66: .meeting-badge{display:inline-flex;align-items:center;margin-left:6px;padding:2px 6px;border:1px solid #d6e3f8;border-radius:5px;background:#eff5ff;color:#1e40af;font-size:10px;line-height:1.6;font-weight:400;vertical-align:middle}
67: .meeting-badge[data-state=stale]{border-color:#fed7aa;background:#fff7ed;color:#b45309}
68: .meeting-badge[data-state=sent]{border-color:#a7f3d0;background:#ecfdf5;color:#059669}
69: .mail-chat .meeting-attachment:empty{display:none}
70: .mail-chat .meeting-attachment .meeting-file{margin:0}
71: .mail-chat .meeting-draft-note{font-size:11px;color:#70829a;margin:7px 0 0;line-height:1.6}
72: .mail-chat .meeting-body-block{font-size:13px;line-height:1.85;color:#334155;overflow-wrap:anywhere}
73: .mail-chat .meeting-body-block p{font-size:inherit;line-height:inherit;color:inherit;margin:0 0 14px}
74: .mail-chat .meeting-body-block p:last-child{margin:0}
75: .mail-chat .meeting-attachment[data-state=stale] .meeting-file{border-color:#fed7aa;background:#fffcf7}
76: .meeting-zone-field{position:relative;margin-bottom:14px}
77: .meeting-zone-field>label{margin-bottom:6px}
78: .meeting-zone-control{display:flex;position:relative}
79: .meeting-zone-control input{padding-right:40px}
80: .meeting-zone-control>button{position:absolute;right:1px;top:1px;width:34px;height:34px;padding:0;border:0;background:#f7faff;color:#627ca5;border-radius:0 6px 6px 0;font:inherit;cursor:pointer}
81: .meeting-zone-control>button:hover{background:#edf3ff;color:#1e40af}
82: .meeting-zone-control>button:active{background:#dbeafe}
83: .meeting-zone-field>small{display:block;margin-top:6px}
84: .meeting-zone-options{position:absolute;top:64px;left:0;right:0;max-height:252px;overflow:auto;overscroll-behavior:contain;z-index:10;border:1px solid #cbd9ed;border-radius:8px;background:#fff;box-shadow:0 10px 25px #223c6226;padding:5px}
85: .meeting-zone-options>button{width:100%;display:flex;align-items:center;justify-content:space-between;gap:10px;padding:9px 10px;background:#fff;border:0;border-radius:5px;text-align:left;color:#334155;font:inherit;font-size:12px;line-height:1.6;cursor:pointer}
86: .meeting-zone-options>button:hover,.meeting-zone-options>button.focused{background:#eff5ff}
87: .meeting-zone-options>button:active{background:#dbeafe}
88: .meeting-zone-options>button[aria-selected=true]{background:#eaf1ff;color:#1e40af}
89: .meeting-zone-options small{display:block;margin-top:2px;font-size:11px;color:#7b8ba2;line-height:1.5}
90: .meeting-zone-options>button>span:last-child{white-space:nowrap;color:#5b769e}
91: .meeting-zone-empty{padding:15px;color:#718198;font-size:12px;line-height:1.6}
92: .meeting-dialog :is(button,a,input,select,textarea,summary):focus-visible,.mail-chat :is(.meeting-trigger,.meeting-link):focus-visible{outline:2px solid #82a8e8;outline-offset:2px}
93: .meeting-dialog :is(button,.button):disabled,.meeting-link:disabled,.meeting-link[aria-disabled=true],.mail-chat .meeting-trigger:disabled{opacity:.45;cursor:not-allowed;transform:none;box-shadow:none;text-decoration:none}
94: .meeting-dialog .button:disabled::after,.mail-chat .meeting-trigger:disabled::after{display:none}
95: .meeting-dialog :is(button,.button):disabled:hover,.meeting-link:disabled:hover,.meeting-link[aria-disabled=true]:hover{filter:none;transform:none;box-shadow:none}
96: .meeting-dialog .button:not(.primary):disabled,.meeting-dialog .button:not(.primary):disabled:hover{background-color:transparent;color:#1e293b;border-color:rgba(15,23,42,.11)}
97: .meeting-dialog .button.primary:disabled,.meeting-dialog .button.primary:disabled:hover{background-image:linear-gradient(180deg,#3b82f6,#1e40af);background-color:#1e40af;border-color:transparent;color:#fff}
98: .meeting-link:disabled,.meeting-link:disabled:hover,.meeting-link[aria-disabled=true],.meeting-link[aria-disabled=true]:hover{color:#315fa7;background:none}
99: .mail-chat .button.meeting-trigger:disabled,.mail-chat .button.meeting-trigger:disabled:hover{color:#1e40af;border-color:#b8cef5;background:#eef4ff}
100: .mail-chat [data-role=manual-compose][data-meeting-sending=true] .mc-editor{background:#f8faff;cursor:wait}
101: @media(max-width:800px){.meeting-grid{grid-template-columns:minmax(0,1fr)}.meeting-form{padding:18px;border-right:0;border-bottom:1px solid #e2e8f0}.meeting-preview{padding:18px}.meeting-dialog{width:calc(100vw - 20px);max-height:calc(100dvh - 20px)}.meeting-bottom{flex-wrap:wrap;padding:12px 18px}.meeting-head{padding:16px 18px}.meeting-head h2{font-size:17px}.meeting-bottom>div{margin-left:auto}.meeting-fields{gap:9px}.meeting-file{flex-wrap:wrap}}
102: @media(max-width:420px){.meeting-head,.meeting-form,.meeting-preview{padding:14px}.meeting-paper{padding:16px}.meeting-bottom{padding:12px 14px}.meeting-bottom>div{width:100%;justify-content:flex-end}.meeting-bottom .button{padding:0 10px}.meeting-zone-options{max-height:220px}}
103: @media(prefers-reduced-motion:reduce){.meeting-dialog *,.mail-chat .meeting-trigger,.mail-chat .meeting-link{transition:none!important;scroll-behavior:auto!important}.meeting-dialog .button:hover,.meeting-dialog .button:active,.mail-chat .meeting-trigger:hover,.mail-chat .meeting-trigger:active{transform:none}}
```

## 补充选择器使用位置
```text
src/main/resources/static/meeting-confirmation.css:6:.meeting-dialog{margin:auto;inset:0;width:min(1080px,calc(100vw - 40px));max-height:calc(100dvh - 40px);padding:0;border:1px solid #d5dfed;border-radius:14px;background:#fff;color:#334155;font-family:var(--font-body);font-size:12px;line-height:1.6;box-shadow:0 24px 100px #172c473d;overflow:auto;overscroll-behavior:contain}
src/main/resources/static/meeting-confirmation.css:7:.meeting-dialog::backdrop{background:#182a464f;backdrop-filter:blur(2px)}
src/main/resources/static/meeting-confirmation.css:8:.meeting-dialog *{box-sizing:border-box}
src/main/resources/static/meeting-confirmation.css:9:.meeting-dialog [hidden],.mail-chat .meeting-attachment[hidden]{display:none!important}
src/main/resources/static/meeting-confirmation.css:10:.meeting-dialog form{margin:0;padding:0}
src/main/resources/static/meeting-confirmation.css:54:.meeting-dialog .button{min-height:36px;height:36px;font-size:12px;padding:0 15px}
src/main/resources/static/meeting-confirmation.css:92:.meeting-dialog :is(button,a,input,select,textarea,summary):focus-visible,.mail-chat :is(.meeting-trigger,.meeting-link):focus-visible{outline:2px solid #82a8e8;outline-offset:2px}
src/main/resources/static/meeting-confirmation.css:93:.meeting-dialog :is(button,.button):disabled,.meeting-link:disabled,.meeting-link[aria-disabled=true],.mail-chat .meeting-trigger:disabled{opacity:.45;cursor:not-allowed;transform:none;box-shadow:none;text-decoration:none}
src/main/resources/static/meeting-confirmation.css:94:.meeting-dialog .button:disabled::after,.mail-chat .meeting-trigger:disabled::after{display:none}
src/main/resources/static/meeting-confirmation.css:95:.meeting-dialog :is(button,.button):disabled:hover,.meeting-link:disabled:hover,.meeting-link[aria-disabled=true]:hover{filter:none;transform:none;box-shadow:none}
src/main/resources/static/meeting-confirmation.css:96:.meeting-dialog .button:not(.primary):disabled,.meeting-dialog .button:not(.primary):disabled:hover{background-color:transparent;color:#1e293b;border-color:rgba(15,23,42,.11)}
src/main/resources/static/meeting-confirmation.css:97:.meeting-dialog .button.primary:disabled,.meeting-dialog .button.primary:disabled:hover{background-image:linear-gradient(180deg,#3b82f6,#1e40af);background-color:#1e40af;border-color:transparent;color:#fff}
src/main/resources/static/meeting-confirmation.css:101:@media(max-width:800px){.meeting-grid{grid-template-columns:minmax(0,1fr)}.meeting-form{padding:18px;border-right:0;border-bottom:1px solid #e2e8f0}.meeting-preview{padding:18px}.meeting-dialog{width:calc(100vw - 20px);max-height:calc(100dvh - 20px)}.meeting-bottom{flex-wrap:wrap;padding:12px 18px}.meeting-head{padding:16px 18px}.meeting-head h2{font-size:17px}.meeting-bottom>div{margin-left:auto}.meeting-fields{gap:9px}.meeting-file{flex-wrap:wrap}}
src/main/resources/static/meeting-confirmation.css:103:@media(prefers-reduced-motion:reduce){.meeting-dialog *,.mail-chat .meeting-trigger,.mail-chat .meeting-link{transition:none!important;scroll-behavior:auto!important}.meeting-dialog .button:hover,.meeting-dialog .button:active,.mail-chat .meeting-trigger:hover,.mail-chat .meeting-trigger:active{transform:none}}
src/main/resources/static/mailbox-chat.js:22: *   .mail-chat.mc-overlay-root（生产 .panel 带 backdrop-filter，fixed 不能嵌套其内）；
src/main/resources/static/mailbox-chat.js:3874:            root.setAttribute("class", "mail-chat mc-overlay-root");
src/main/resources/static/mailbox-chat.js:3875:            root.setAttribute("data-role", "mc-overlay-root");
src/main/resources/static/mailbox-chat.js:5390:            return root.querySelector(".reply-template-dialog") || null;
src/main/resources/static/mailbox-chat.js:5414:                <dialog class="reply-template-dialog" aria-labelledby="replyTemplateTitle">
src/main/resources/static/mailbox-chat.js:5902:        /** I-7：只移除自己拥有的 .reply-template-dialog，绝不 portalRoot.innerHTML=""。 */
src/main/resources/static/styles.css:11465:.calendar-toolbar{display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap;gap:12px}
src/main/resources/static/styles.css:11481:.calendar-form{display:grid;grid-template-columns:1fr 1fr;gap:16px;margin:16px 0}
src/main/resources/static/styles.css:11491:@media(max-width:760px){.calendar-form{grid-template-columns:1fr}.calendar-dialog{padding:16px}.calendar-day{min-height:112px}}
src/main/resources/static/styles.css:11509:.calendar-root .calendar-toolbar{padding:20px;border-bottom:1px solid #e2e8f0;gap:16px}
src/main/resources/static/styles.css:11552:@media(max-width:1200px){.calendar-root .calendar-layout{grid-template-columns:minmax(0,1fr) 250px}.calendar-root .calendar-overview{gap:22px}.calendar-root .calendar-metric{min-width:125px}.calendar-root .calendar-toolbar{padding:16px 12px}.calendar-root .calendar-month-controls h2{font-size:18px;margin-right:5px}}
src/main/resources/static/styles.css:11554:@media(max-width:600px){.calendar-root .calendar-overview{padding:16px;gap:20px;justify-content:space-between}.calendar-root .calendar-metric{min-width:0;gap:7px;font-size:11px}.calendar-root .calendar-overview-icon{display:none}.calendar-root .calendar-metric strong{font-size:23px}.calendar-root .calendar-toolbar{gap:12px}.calendar-root .calendar-filter-bar{padding:8px 12px;flex-wrap:wrap}.calendar-root .calendar-footer{flex-wrap:wrap}}
src/main/resources/static/styles.css:11972:.reply-template-dialog{margin:auto;inset:0;box-sizing:border-box;width:min(1010px,calc(100vw - 48px));max-width:none;max-height:calc(100dvh - 48px);padding:0;border:1px solid #dce4ef;border-radius:18px;box-shadow:0 30px 100px #12274b45;background:#fff;color:#334155;overflow:hidden;font-family:var(--font-body);font-size:12px;line-height:1.6}
src/main/resources/static/styles.css:11973:.reply-template-dialog[open]{display:flex;flex-direction:column}
src/main/resources/static/styles.css:11974:.reply-template-dialog::backdrop{background:rgba(23,38,66,.32);backdrop-filter:blur(2px)}
src/main/resources/static/styles.css:11975:.reply-template-dialog *{box-sizing:border-box}
src/main/resources/static/styles.css:11976:.reply-template-dialog h2,.reply-template-dialog h3,.reply-template-dialog p{margin:0}
src/main/resources/static/styles.css:11996:.reply-template-dialog .reply-template-caption{display:flex;justify-content:space-between;color:#94a3b8;font-size:11px;padding:18px 9px 10px}
src/main/resources/static/styles.css:12006:.reply-template-dialog .reply-template-source{font-size:10px;color:#94a3b8;margin-top:auto;padding:12px 9px 3px}
src/main/resources/static/styles.css:12011:.reply-template-dialog .reply-template-status{font-size:12px;line-height:1.7;color:#64748b;margin-bottom:12px;white-space:pre-wrap;overflow-wrap:anywhere}
src/main/resources/static/styles.css:12026:.reply-template-dialog .reply-template-hint{font-size:11px;line-height:1.6;color:#92400e}
src/main/resources/static/styles.css:12029:.reply-template-dialog :is(button,input):focus-visible{outline:2px solid #3b82f6;outline-offset:2px}
src/main/resources/static/styles.css:12030:.reply-template-dialog :is(button,.button):disabled{opacity:.45;cursor:not-allowed;transform:none;box-shadow:none}
src/main/resources/static/styles.css:12031:@media(max-width:760px){.reply-template-dialog{width:calc(100vw - 24px);max-height:calc(100dvh - 24px)}.reply-template-layout{grid-template-columns:1fr}.reply-template-sidebar{border-right:0;border-bottom:1px solid #e6ecf4}.reply-template-list{max-height:170px;overflow:auto}.reply-template-head,.reply-template-context{padding:15px 18px}.reply-template-main{padding:16px}.reply-template-footer{padding:15px;align-items:flex-end;flex-wrap:wrap}.reply-template-actions{margin-left:auto}.reply-template-badge{display:none}.reply-template-account{max-width:120px}.reply-template-modes{gap:7px}}
src/main/resources/static/styles.css:12032:@media(prefers-reduced-motion:reduce){.reply-template-dialog *,.mail-chat .reply-template-trigger{transition:none!important;scroll-behavior:auto!important}}
src/main/resources/static/styles.css:12392:.task-center-interrupt {
src/main/resources/static/styles.css:12398:.task-center-interrupt[hidden] { display: none; }
src/main/resources/static/styles.css:12399:.task-center-interrupt label { color: var(--text-muted); font-size: 12px; }
src/main/resources/static/styles.css:12400:.task-center-interrupt select,
src/main/resources/static/styles.css:12401:.task-center-interrupt textarea {
src/main/resources/static/styles.css:12410:.task-center-interrupt button { justify-self: start; }
src/main/resources/static/app.js:21257:    + '<div class="calendar-toolbar"><div class="calendar-month-controls"><h2 data-role="calendar-month"></h2>'
src/main/resources/static/meeting-confirmation.js:1263:            dialog.setAttribute("class", "meeting-dialog");
src/main/resources/static/mailbox-chat.css:154:.mail-chat.mc-overlay-root{display:contents}
src/main/resources/static/index.html:1050:                <div id="taskActiveInterruptPanel" class="task-center-interrupt" hidden>
src/main/resources/static/index.html:2237:<dialog id="meetingCalendarDialog" class="calendar-dialog" aria-labelledby="meetingCalendarDialogTitle"><form id="meetingCalendarForm"><div class="calendar-toolbar"><h2 id="meetingCalendarDialogTitle">新增排期</h2><button class="button icon-button" type="button" data-calendar-action="close" aria-label="关闭">×</button></div><div class="calendar-form"><label class="calendar-field calendar-wide" data-role="expert-picker">选择专家<input type="search" data-role="expert-search" placeholder="搜索姓名或邮箱"><select name="contactId" required></select></label><p class="calendar-note calendar-wide" data-role="expert-name"></p><label class="calendar-field">开始时间（北京时间）<input name="startBeijing" type="datetime-local" required></label><label class="calendar-field">结束时间（北京时间）<input name="endBeijing" type="datetime-local" required></label><label class="calendar-field calendar-wide">会议链接（选填）<input name="meetingLink" type="url" maxlength="1024"></label><label class="calendar-field calendar-wide">备注（选填）<textarea name="note" maxlength="200"></textarea></label></div>
```
