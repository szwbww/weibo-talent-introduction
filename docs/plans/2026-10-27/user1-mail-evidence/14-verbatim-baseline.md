## src/main/resources/static/styles.css:1
```
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
```

## src/main/resources/static/styles.css:680
```
680:     .expert-filter-row-metrics {
681:         grid-template-columns: 1fr;
682:     }
683: 
684:     .expert-filter-tag-group > .tag-select {
685:         flex-wrap: wrap;
686:         overflow: visible;
687:     }
688: }
689: 
690: .filter-count-badge {
691:     display: inline-flex;
692:     align-items: center;
693:     justify-content: center;
694:     min-width: 16px;
695:     height: 16px;
696:     padding: 0 4px;
697:     border-radius: var(--radius-md);
698:     background: var(--primary);
699:     color: #fff;
700:     font-size: 11px;
701:     font-weight: 700;
702:     line-height: 1;
703: }
704: 
705: /* Tag-chip multi-select (数据完整度) */
706: .tag-select {
707:     display: inline-flex;
708:     align-items: center;
709:     gap: 4px;
710:     flex-wrap: wrap;
711: }
712: 
713: .tag-chip {
714:     display: inline-flex;
715:     align-items: center;
716:     height: 26px;
717:     padding: 0 10px;
718:     border: 1px solid var(--border);
719:     border-radius: var(--radius-lg);
720:     background: transparent;
721:     color: var(--text-muted);
722:     font-size: 11px;
723:     font-weight: 500;
724:     font-family: var(--font-body);
725:     cursor: pointer;
726:     white-space: nowrap;
727:     transition: all 0.15s ease;
728:     user-select: none;
729:     line-height: 1;
730: }
731: 
732: .tag-chip:hover {
733:     border-color: var(--primary);
734:     color: var(--primary);
735:     background: var(--primary-light);
736: }
737: 
738: .tag-chip.active {
739:     background: var(--primary);
740:     border-color: var(--primary);
741:     color: #fff;
742:     box-shadow: 0 1px 3px rgba(var(--primary-rgb), 0.3);
743: }
744: .toolbar-chip-row {
```

## src/main/resources/static/styles.css:830
```
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
```

## src/main/resources/static/mailbox-chat.css:1
```
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
69: @media(max-width:1100px){.mail-chat{grid-template-columns:280px minmax(0,1fr);gap:12px}.mail-chat .mc-header{padding:14px}.mail-chat .mc-scroll{padding:14px}.mail-chat .mc-message{width:94%}}
70: @media(max-width:760px){.mail-chat{display:flex;flex-direction:column;height:auto;min-height:0;gap:12px}.mail-chat .mc-experts{max-height:320px;min-height:240px}.mail-chat .mc-expert-list{min-height:100px}.mail-chat .mc-conversation{min-height:560px}.mail-chat .mc-scroll{max-height:none;overflow:visible;padding:12px}.mail-chat .mc-message{width:100%}.mail-chat .mc-header{padding:12px}.mail-chat .mc-identity{min-width:0;width:100%;flex-basis:100%}.mail-chat .mc-section-content{padding:12px}.mail-chat .mc-actions{width:100%}}
71: @media(prefers-reduced-motion:reduce){.mail-chat .button{transition:none}.mail-chat .button:hover,.mail-chat .button:active{transform:none}}
72: /* S-1..S-6: mailbox only; retain production shell. */
73: #view-mailbox.mc-refined #mailboxLegacyToolbar{display:none}
74: #view-mailbox.mc-refined #mailboxConversationPanel{overflow:visible}
75: #view-mailbox.mc-refined #mailboxList{padding:16px}
76: #view-mailbox:not(.mc-refined) .mc-filter-fields{display:contents}
77: .mail-chat .mc-experts{overflow:visible;position:relative}
78: .mail-chat .mc-search-row{position:relative;display:flex;align-items:center;gap:7px}
79: .mail-chat .mc-search-row input{flex:1;min-width:0;height:36px;min-height:36px}
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
165: 
166: /* mailbox-expert-note:S1 */
167: .mail-chat .mc-header .mc-header-meta{min-width:0}
168: .mail-chat .mc-note-tags{display:flex;align-items:center;gap:8px;flex-wrap:wrap;width:100%;min-width:0}
169: .mail-chat .mc-note-row{display:flex;align-items:center;gap:12px;width:100%;min-width:0}
170: .mail-chat .mc-note-slot{flex:1;min-width:0}
```

## src/main/resources/static/index.html:79
```
79:         <nav class="nav-tabs" aria-label="Main">
80:             <button class="nav-tab" data-view="monitoring">
81:                 <svg viewBox="0 0 24 24" width="18" height="18" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round">
82:                     <path d="M3 3v18h18"/><path d="M7 14l4-4 4 4 6-6"/>
83:                 </svg>
84:                 <span>邮件监控</span>
85:             </button>
86:             <button class="nav-tab active" data-view="accounts">
87:                 <svg viewBox="0 0 24 24" width="18" height="18" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round">
88:                     <rect x="2" y="4" width="20" height="16" rx="2"/><path d="m22 7-8.97 5.7a1.94 1.94 0 0 1-2.06 0L2 7"/>
89:                 </svg>
90:                 <span>邮箱账号</span>
91:             </button>
92:             <button class="nav-tab" data-view="mail-templates">
93:                 <svg viewBox="0 0 24 24" width="18" height="18" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round">
94:                     <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
95:                     <polyline points="14 2 14 8 20 8"/>
96:                     <line x1="16" y1="13" x2="8" y2="13"/>
97:                     <line x1="16" y1="17" x2="8" y2="17"/>
98:                 </svg>
99:                 <span>邮件模板</span>
100:             </button>
101:             <button class="nav-tab" data-view="suppressions">
102:                 <svg viewBox="0 0 24 24" width="18" height="18" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round">
103:                     <circle cx="12" cy="12" r="10"/><line x1="4.93" y1="4.93" x2="19.07" y2="19.07"/>
104:                 </svg>
105:                 <span>退订名单</span>
106:             </button>
107:             <button class="nav-tab" data-view="contacts">
108:                 <svg viewBox="0 0 24 24" width="18" height="18" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round">
109:                     <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/>
110:                     <path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/>
111:                 </svg>
112:                 <span>专家列表</span>
113:             </button>
114:             <button class="nav-tab" data-view="mailbox">
115:                 <svg viewBox="0 0 24 24" width="18" height="18" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round">
116:                     <path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z"/>
117:                     <polyline points="22,6 12,13 2,6"/>
118:                 </svg>
119:                 <span>收发件箱</span>
120:                 <span id="unmatchedBadgeHigh" class="nav-badge nav-badge-high" hidden>0</span>
121:                 <span id="unmatchedBadgeNormal" class="nav-badge nav-badge-normal" hidden>0</span>
122:             </button>
123:             <button class="nav-tab" data-view="meeting-calendar"><svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><rect x="3" y="5" width="18" height="16" rx="2"/><path d="M16 3v4M8 3v4M3 11h18"/></svg><span>会议日历</span></button>
124:             <button class="nav-tab" data-view="inbound-summary">
125:                 <svg viewBox="0 0 24 24" width="18" height="18" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round">
126:                     <path d="M20.59 13.41l-7.17 7.17a2 2 0 0 1-2.83 0L2 12V2h10l8.59 8.59a2 2 0 0 1 0 2.82z"/>
127:                     <line x1="7" y1="7" x2="7.01" y2="7"/>
128:                 </svg>
129:                 <span>来信汇总</span>
130:             </button>
131:             <button class="nav-tab" data-view="ai-training">
132:                 <svg viewBox="0 0 24 24" width="18" height="18" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round">
133:                     <path d="M12 2a4 4 0 0 1 4 4v2h2a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V10a2 2 0 0 1 2-2h2V6a4 4 0 0 1 4-4z"/>
134:                     <circle cx="12" cy="13" r="2"/>
135:                     <path d="M9 2h6"/>
136:                 </svg>
137:                 <span>AI 训练</span>
138:             </button>
139:             <button class="nav-tab" data-view="tasks">
140:                 <svg viewBox="0 0 24 24" width="18" height="18" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round">
141:                     <rect x="3" y="4" width="18" height="18" rx="2" ry="2"/><line x1="16" y1="2" x2="16" y2="6"/>
142:                     <line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/>
143:                 </svg>
144:                 <span>任务记录</span>
145:                 <span id="taskActiveNavBadge" class="task-center-pill task-center-nav-count" hidden>
146:                     <i class="task-center-dot" aria-hidden="true"></i>
147:                     <span id="taskActiveNavCount"></span>
148:                 </span>
149:             </button>
150:         </nav>
151:         <label class="mobile-core-nav" for="mobileCoreView">切换页面
152:             <select id="mobileCoreView" aria-label="切换页面"></select>
153:         </label>
154:         <div class="topnav-side">
155:             <div class="user-info">
156:                 当前登录: <span id="currentUserDisplay">admin</span>
157:             </div>
158:             <button class="nav-tab logout-btn" id="logoutBtn">
159:                 <svg viewBox="0 0 24 24" width="18" height="18" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round">
160:                     <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/>
161:                     <polyline points="16 17 21 12 16 7"/>
162:                     <line x1="21" y1="12" x2="9" y2="12"/>
163:                 </svg>
```

## src/main/resources/static/index.html:750
```
750:         <section class="view" id="view-mailbox">
751:             <div class="toolbar" id="mailboxLegacyToolbar">
752:                 <button class="button primary" id="mailboxRefreshBtn">刷新</button>
753:                 <div class="mailbox-view-controls">
754:                     <div class="mailbox-segmented-control mailbox-view-mode" role="radiogroup" aria-label="收发件箱展示方式">
755:                         <label><input type="radio" name="mailboxViewMode" value="MAIL" checked><span>按邮件</span></label>
756:                         <label><input type="radio" name="mailboxViewMode" value="EXPERT"><span>按专家聚合</span></label>
757:                     </div>
758:                     <div class="mailbox-segmented-control mailbox-scope-mode" role="radiogroup" aria-label="收发件箱邮件范围">
759:                         <label><input type="radio" name="mailboxMailScope" value="ALL" checked><span>全部邮件</span></label>
760:                         <label><input type="radio" name="mailboxMailScope" value="PENDING"><span>仅待处理</span></label>
761:                     </div>
762:                 </div>
763:                 <select id="mailboxFilterAccountCode">
764:                     <option value="">全部邮箱账号</option>
765:                 </select>
766:                 <select id="mailboxFilterDirection">
767:                     <option value="">全部收发方向</option>
768:                     <option value="INBOUND">收件 (INBOUND)</option>
769:                     <option value="OUTBOUND">发件 (OUTBOUND)</option>
770:                 </select>
771:                 <select id="mailboxFilterTag">
772:                     <option value="">全部标签</option>
773:                     <option value="专家">专家</option>
774:                     <option value="待匹配">待确认</option>
775:                     <option value="自动回复">自动回复</option>
776:                     <option value="手动回复">手动回复</option>
777:                     <option value="首发">首发</option>
778:                     <option value="待处理">待处理</option>
779:                     <option value="收件">收件</option>
780:                     <option value="发件">发件</option>
781:                 </select>
782:                 <input id="mailboxFilterRecipient" placeholder="输入邮箱关键词">
783:                 <input id="mailboxFilterKeyword" placeholder="搜索邮件主题或正文">
784:                 <input type="date" id="mailboxFilterStartDate">
785:                 <span>至</span>
786:                 <input type="date" id="mailboxFilterEndDate">
787:                 <button class="button primary" id="mailboxSearchBtn">查询</button>
788:             </div>
789: 
790:             <!-- B4 (S2b-3): 按任务执行过滤的提示条；位于既有 .toolbar 之下、不与标题栏「批量发送」按钮同行 -->
791:             <div id="mailboxExecutionFilterBar" class="toolbar" hidden>
792:                 <span class="text-muted" id="mailboxExecutionFilterText"></span>
793:                 <button type="button" class="button small" id="mailboxExecutionFilterClear">清除过滤</button>
794:             </div>
795: 
796:             <section class="panel" id="mailboxConversationPanel">
797:                 <div class="panel-head">
798:                     <h2>已激活账号收发邮件记录</h2>
799:                     <div class="panel-head-actions">
800:                         <button class="button" id="checkRepliesBtn" onclick="handleCheckReplies()">检查回复</button>
801:                         <button class="button primary" id="bulkOutreachBtn" onclick="handleBulkOutreach()">批量发送</button>
802:                         <button class="button" id="bulkAutoReplyBtn">自动回复：加载中...</button>
803:                         <button class="button" id="mailboxGroupPushBtn" type="button"
804:                                 role="switch" aria-checked="false" aria-label="企业微信群消息推送" disabled>
805:                             <span>群消息</span><span id="mailboxGroupPushState">加载中…</span>
806:                             <span class="mailbox-push-track" aria-hidden="true"></span>
807:                         </button>
808:                     </div>
809:                 </div>
810:                 <div class="mailbox-list" id="mailboxList"></div>
811:                 <div class="pagination" id="mailboxPagination" style="padding: 16px 24px; display: flex; justify-content: flex-end; gap: 8px;"></div>
812:             </section>
813: 
814:             <section class="panel" id="unmatchedDetailPanel" hidden style="margin-top: 16px;">
815:                 <div class="panel-head">
```

## src/main/resources/static/index.html:2518
```
2518: <script src="trust-reply-workbench.js?v=20261026-discovery-recheck-recovery"></script>
2519: <script src="expert-materials.js?v=20261026-discovery-recheck-recovery"></script>
2520: <script src="meeting-confirmation.js?v=20261026-discovery-recheck-recovery"></script>
2521: <script src="mailbox-chat.js?v=20261026-discovery-recheck-recovery"></script>
2522: <script src="contact-history.js?v=20261026-discovery-recheck-recovery"></script>
2523: <script src="meeting-records.js?v=20261026-discovery-recheck-recovery"></script>
2524: <script src="contact-details.js?v=20261026-discovery-recheck-recovery"></script>
2525: <script src="discovery-papers.js?v=20261026-discovery-recheck-recovery"></script>
2526: <script src="discovery-recheck.js?v=20261026-discovery-recheck-recovery"></script>
2527: <script src="app.js?v=20261026-discovery-recheck-recovery"></script>
2528: <script src="world-clock.js?v=20261026-discovery-recheck-recovery"></script>
2529: </body>
```

## src/main/resources/static/mailbox-chat.js:1674
```
1674:         function renderSkeleton() {
1675:             host.innerHTML = skeletonHtml();
1676:         }
1677: 
1678:         function conversationVisible() {
1679:             if (instance.mobileMedia && instance.mobileMedia.matches && instance.mobilePane === "list") return false;
1680:             const scroll = scrollEl();
1681:             return !scroll || typeof scroll.getClientRects !== "function" || scroll.getClientRects().length > 0;
1682:         }
1683: 
1684:         function saveCurrentConversation() {
```

## src/main/resources/static/mailbox-chat.js:2116
```
2116:         function renderPerson(item) {
2117:             const pendingCount = Number(item.pendingCount) || 0;
2118:             const latest = item.latestMessage || null;
2119:             const latestLine = latest
2120:                 ? `${latest.direction === "INBOUND" ? "最近来信" : "最近发件"}：${latest.subject || "(无主题)"}`
2121:                 : "暂无往来";
2122:             const accounts = Array.isArray(item.accountCodes) && item.accountCodes.length
2123:                 ? item.accountCodes.join("、")
2124:                 : (item.email || "-");
2125:             const cardName = item.name || item.email || "-";
2126:             const active = instance.selectedContactId != null
2127:                 && String(item.contactId) === String(instance.selectedContactId);
2128:             const tagNames = personTagNames(item);
2129:             const tagLine = tagNames === null
2130:                 ? `<span class="mc-person-tags-unavailable" title="标签暂不可用">标签暂不可用</span>`
2131:                 : (tagNames.length === 0 ? "" : `<span class="mc-person-tags" title="专家标签：${escapeText(tagNames.join("、"))}">${tagNames.map((name) => `<span class="mc-person-tag">${escapeText(name)}</span>`).join("")}</span>`);
2132:             const ariaLabel = (tagNames !== null && tagNames.length > 0
2133:                 ? `查看${item.name || item.email || ""}往来邮件；专家标签：${tagNames.join("、")}`
2134:                 : `查看${item.name || item.email || ""}往来邮件`)
2135:                 + `；${lastReplyAriaSuffix(lastReplyDisplay(item))}`;
2136:             return `
2137:                 <div class="mc-person mailbox-progress-card" data-replied="${instance.chip === CHIP_REPLIED ? "true" : "false"}" data-active="${active ? "true" : "false"}" data-contact-id="${escapeText(item.contactId)}">
2138:                     <button class="mc-person-main" type="button" data-action="mc-select-expert" data-contact-id="${escapeText(item.contactId)}" aria-label="${escapeText(ariaLabel)}"${active ? ' aria-current="true"' : ""}>
2139:                         <span class="mc-person-heading"><strong title="${escapeText(cardName)}">${escapeText(cardName)}</strong></span>
2140:                         <small>${escapeText(accounts)}</small>
2141:                         <small>${escapeText(latestLine)}</small>
2142:                         ${lastReplyListMarkup(item)}
2143:                         <span class="mc-person-meta">
2144:                             <span class="mc-person-counts">收 ${Number(item.receivedCount) || 0} · 发 ${Number(item.sentCount) || 0}</span>
2145:                             ${tagLine}
2146:                             ${Number(instance.draftCounts.get(Number(item.contactId))) > 0 ? `<span class="mailbox-draft-count" data-role="contact-draft-count">草稿 ${Number(instance.draftCounts.get(Number(item.contactId)))}</span>` : ""}
2147:                         </span>
2148:                         <span class="calendar-summary" data-role="meeting-summary"></span>
2149:                     </button>
2150:                     <span data-role="person-actions">
2151:                         ${progressActionsHtml(item)}
2152:                         ${instance.chip === CHIP_REPLIED ? `<button class="mc-text-button" type="button" data-action="mc-dismiss-replied" data-contact-id="${escapeText(item.contactId)}" aria-label="将${escapeText(item.name || item.email || "该专家")}移出已回复" title="移出已回复，仍可在全部查看">移出</button>` : ""}
2153:                     </span>${suspensionCardFooterHtml(item)}
2154:                 </div>
2155:             `;
2156:         }
2157: 
```

## src/main/resources/static/mailbox-chat.js:3587
```
3587:         function renderConversationScaffold() {
3588:             const body = conversationBody();
3589:             if (!body) return;
3590:             const name = (instance.selectedSummary && (instance.selectedSummary.name || instance.selectedSummary.email)) || "…";
3591:             body.innerHTML = `
3592:                 <header class="mc-header"><div class="mc-identity"><h2>${escapeText(name)}</h2><p>正在加载往来信件…</p></div><div class="mc-actions"></div></header>
3593:                 <div class="mc-timeline-head"><span>往来信件</span><span class="mc-position-hint"></span><button class="mc-text-button" type="button" data-action="mc-latest">↓ 最新消息</button></div>
3594:                 <div class="mc-scroll" tabindex="0" aria-label="往来信件滚动区"><div class="mc-empty">正在加载往来信件…</div></div>
3595:             `;
3596:             bindScrollListener();
3597:         }
3598: 
3599:         function conversationSummaryInfo() {
3600:             const summary = instance.selectedSummary || {};
3601:             const parts = [];
3602:             if (summary.email) parts.push(summary.email);
3603:             const accounts = Array.isArray(summary.accountCodes) && summary.accountCodes.length
3604:                 ? summary.accountCodes.join("、")
3605:                 : "";
3606:             if (accounts) parts.push(`账号 ${accounts}`);
3607:             return parts.join(" · ");
3608:         }
3609: 
3610:         // I-4：只替换详情回复时间槽；不改选中对象、不重建 header、不触发草稿/锚点/请求。
```

## src/main/resources/static/mailbox-chat.js:3621
```
3621:         function renderHeader() {
3622:             const body = conversationBody();
3623:             if (!body) return;
3624:             const summary = instance.selectedSummary || {};
3625:             const contactId = Number(instance.selectedContactId);
3626:             const materialCount = Number(summary.materialCount) || 0;
3627:             const head = body.querySelector(".mc-header");
3628:             if (!head) return;
3629:             const identity = head.querySelector(".mc-identity");
3630:             if (identity) {
3631:                 identity.innerHTML = `<h2>${escapeText(summary.name || summary.email || "-")}</h2><p>${escapeText(conversationSummaryInfo() || "-")}</p><span class="mailbox-reply-detail" data-role="last-reply-time">${lastReplyDetailInner({ kind: "unavailable", text: LAST_REPLY_UNAVAILABLE_TEXT })}</span><span class="calendar-summary" data-role="meeting-summary"></span>`;
3632:             }
3633:             // I-4：优先取当前页同一行，回退身份匹配的 selectedSummary；都不可用保留默认文案。
3634:             const selectedRow = contactId ? findSummaryByContactId(contactId) : null;
3635:             renderLastReplyHeader(selectedRow || summary);
3636:             const actions = head.querySelector(".mc-actions");
3637:             if (actions) {
3638:                 const suspendView = suspensionViewForContact(contactId, summary);
3639:                 actions.innerHTML = `
3640:                     ${suspensionDetailButtonHtml(contactId, suspendView)}
3641:                     <button class="button" type="button" data-action="mc-open-materials" data-contact-id="${escapeText(contactId)}">材料 ${materialCount}</button>
3642:                     <button class="button" type="button" data-action="mc-manage-expert">管理</button>
3643:                     <button class="button" type="button" data-action="mc-add-schedule">新增排期</button>
3644:                     <button class="button" type="button" data-action="mc-edit-schedule">变更日期</button>
3645:                     <button class="button danger" type="button" data-action="mc-cancel-schedule">取消排期</button>
3646:                 `;
3647:             }
3648:             renderHeaderMeetingSummary();
3649:             ensureMeetingSummaryFor(contactId);
3650:             const meta = head.querySelector(".mc-header-meta");
3651:             if (meta) {
3652:                 renderHeaderMeta(meta);
3653:             }
3654:         }
3655: 
```

## src/main/resources/static/mailbox-chat.js:4790
```
4790:         function renderMessage(message) {
4791:             const direction = message.direction === "OUTBOUND" ? "OUTBOUND" : "INBOUND";
4792:             const isInboundProcessing = message.source === "INBOUND_PROCESSING";
4793:             const key = `${message.source}:${message.id}`;
4794:             const account = message.accountCode || "";
4795:             const who = isInboundProcessing
4796:                 ? `${SOURCE_LABELS.INBOUND_PROCESSING || "专家来信"} · ${timePart(message.eventAt)}${account ? ` · ${escapeText(account)}` : ""}`
4797:                 : `${direction === "OUTBOUND" ? "发出邮件" : "往来邮件"} · ${timePart(message.eventAt)}${account ? ` · ${escapeText(account)}` : ""}`;
4798:             const subject = message.subject || "(无主题)";
4799:             const displayBody = messageDisplayText(message);
4800:             const bodyHtml = displayBody ? `<div class="mc-body">${messageDisplayHtml(displayBody)}</div>` : "";
4801:             const sentMeetingHtml = sentMeetingAttachmentHtml(message);
4802:             // fast-p 07（I-4）：已发通用附件只消费 06 的 outboundAttachments 快照。
4803:             const sentOutboundFilesHtml = outboundSentFilesHtml(message);
4804:             const attachmentHtml = Number(message.attachmentCount) > 0 ? renderAttachmentSummary(message) : "";
4805:             const statusBadge = renderStatusBadge(message, direction);
4806:             const pending = isInboundProcessing && message.processStatus === "MANUAL_REVIEW";
4807:             const processed = isInboundProcessing && message.processStatus === "PROCESSED";
4808:             const tagRow = isInboundProcessing ? renderTagRow(message, key) : "";
4809:             const translationStateFor = translationState(key, displayBody);
4810:             const translationHtml = renderTranslationBlock(translationStateFor);
4811:             const canTranslate = !!displayBody;
4812:             const footerButtons = [];
4813:             if (canTranslate) {
4814:                 footerButtons.push(`<button class="mc-text-button" type="button" data-action="mc-translate" data-message-key="${escapeText(key)}">${escapeText(translateButtonLabel(translationStateFor))}</button>`);
4815:             }
4816:             if (isInboundProcessing) {
4817:                 footerButtons.push(`<button class="mc-text-button" type="button" data-action="mc-add-mail-tag" data-message-key="${escapeText(key)}">＋ 添加标签</button>`);
4818:             }
4819:             if (pending) {
4820:                 if (instance.processConfirm.key === key) {
4821:                     const processBusy = instance.processConfirm.busy;
4822:                     footerButtons.push(`<span class="mailbox-suspend-process-confirm">
4823:                         <span>确认标记为已处理？</span>
4824:                         <button type="button" class="mailbox-suspend-inline-secondary" data-action="mc-process-cancel" data-message-key="${escapeText(key)}"${processBusy ? " disabled" : ""}>取消</button>
4825:                         <button type="button" class="mailbox-suspend-inline-primary" data-action="mc-process-confirm" data-message-key="${escapeText(key)}"${processBusy ? " disabled" : ""}>${processBusy ? "处理中…" : "确认"}</button>
4826:                     </span>`);
4827:                 } else {
4828:                     const pendingDisabled = instance.auth.ready ? "" : " disabled";
4829:                     footerButtons.push(`<button class="mc-text-button mc-process mailbox-suspend-pending-action" type="button" data-action="mc-mark-resolved" data-message-key="${escapeText(key)}"${pendingDisabled}>待处理</button>`);
4830:                 }
4831:             } else if (processed) {
4832:                 const resolvedBy = instance.resolvedLabels.get(key);
4833:                 footerButtons.push(`<span class="mailbox-suspend-processed-label">已处理${resolvedBy ? ` · ${escapeText(resolvedBy)}` : ""}</span>`);
4834:             }
4835:             const footerHtml = footerButtons.length > 0
4836:                 ? `<footer>${footerButtons.join("")}</footer>`
4837:                 : "";
4838:             const completionHtml = instance.completion.anchorKey === key ? completionLineHtml() : "";
4839:             return `
4840:                 <article class="mc-message" data-direction="${direction}" data-source="${escapeText(message.source)}" data-id="${escapeText(message.id)}" data-message-key="${escapeText(key)}">
4841:                     <header><span>${who}</span>${statusBadge ? `<span>${statusBadge}</span>` : ""}</header>
4842:                     <h3>${escapeText(subject)}</h3>
4843:                     ${bodyHtml}
4844:                     ${sentMeetingHtml}
4845:                     ${sentOutboundFilesHtml}
4846:                     ${attachmentHtml}
4847:                     ${translationHtml}
4848:                     ${tagRow}
4849:                     <p class="mc-inline-error" role="alert" hidden></p>
4850:                     ${footerHtml}
4851:                     ${completionHtml}
4852:                 </article>
4853:             `;
4854:         }
4855: 
```

## src/main/resources/db/migration/V25__create_admin_user.sql:1
```
1: CREATE TABLE admin_user (
2:     id              BIGINT AUTO_INCREMENT PRIMARY KEY,
3:     username        VARCHAR(64)  NOT NULL,
4:     password_hash   VARCHAR(100) NOT NULL,
5:     must_change_password TINYINT(1) NOT NULL DEFAULT 1,
6:     last_login_at   DATETIME     NULL,
7:     created_at      DATETIME     NOT NULL,
8:     updated_at      DATETIME     NOT NULL,
9:     CONSTRAINT uk_admin_user_username UNIQUE (username)
10: );
```

## src/main/resources/static/styles.css:802
```
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
```

## src/main/resources/static/styles.css:3616
```
3616: .button::after {
3617:     content: '';
3618:     position: absolute;
3619:     inset: 0;
3620:     background: radial-gradient(circle, rgba(255,255,255,0.35) 10%, transparent 70%);
3621:     opacity: 0;
3622:     transform: scale(0);
3623:     transition: transform 0.4s ease, opacity 0.4s ease;
3624:     pointer-events: none;
3625:     border-radius: inherit;
3626: }
3627: 
3628: .button:active::after {
3629:     opacity: 1;
3630:     transform: scale(2.5);
3631:     transition: transform 0s, opacity 0s;
3632: }
```

## src/main/resources/db/migration/V121__create_expert_follow.sql:1
```
1: -- ============================================================================
2: -- V121 expert_follow：专家会话关注（fast-p 07，最终 schema 迁移）
3: --
4: -- 复合主键 (username, expert_contact_id)：同一用户对同一专家至多一行。
5: -- username = 登录会话 AUTH_USERNAME（仅当前 admin 单用户体系；不新增用户表，
6: -- 也不加 admin_user 外键）。created_at = 首次关注时间；幂等重复 PUT 不刷新。
7: -- 唯一写者 ExpertFollowService（参数化 JDBC）；读者 = 会话 summary 的 join /
8: -- followed 过滤。关注不写 ES / expert_contact / 来信处理状态（I-3）。
9: -- ============================================================================
10: CREATE TABLE expert_follow (
11:     username           VARCHAR(64)  NOT NULL COMMENT '登录用户名（Session AUTH_USERNAME）',
12:     expert_contact_id  BIGINT       NOT NULL,
13:     created_at         DATETIME     NOT NULL,
14:     PRIMARY KEY (username, expert_contact_id),
15:     CONSTRAINT fk_expert_follow_contact
16:         FOREIGN KEY (expert_contact_id) REFERENCES expert_contact(id)
17: ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
18:   COMMENT = '专家关注（fast-p 07；复合主键；仅当前 admin 使用）';
```

## src/main/resources/static/app.js:20664
```
20664: async function refreshMailboxGroupPush() {
20665:     if (state.view !== "mailbox" || !appStarted) return;
20666:     const push = mailboxGroupPushState;
20667:     // A refresh during a save must wait for its outcome, not race the write.
20668:     if (push.phase === "saving") {
20669:         push.pendingRefresh = true;
20670:         return;
20671:     }
20672:     const seq = ++push.requestSeq;
20673:     push.phase = "loading";
20674:     renderMailboxGroupPush();
20675:     try {
20676:         const settings = await api("/api/expert-inbound-notifications/settings", { cache: "no-store", timeoutMs: 10000 });
20677:         if (seq !== push.requestSeq) return;
20678:         push.confirmedEnabled = settings.enabled;
20679:         push.configured = settings.configured;
20680:         push.phase = "ready";
20681:         push.uncertain = false;
20682:     } catch (error) {
20683:         if (seq !== push.requestSeq) return;
20684:         push.phase = push.uncertain ? "unknown" : "error";
20685:         showStatus("群消息加载失败: " + error.message, "error");
20686:     }
20687:     if (seq === push.requestSeq) renderMailboxGroupPush();
20688: }
20689: 
20690: async function saveMailboxGroupPush() {
20691:     const push = mailboxGroupPushState;
20692:     if (state.view !== "mailbox" || !appStarted || push.phase === "loading" || push.phase === "saving") return;
20693:     if (push.phase === "error" || push.phase === "unknown") {
20694:         await refreshMailboxGroupPush();
20695:         return;
20696:     }
20697:     if (!push.configured && !push.confirmedEnabled) return;
20698:     const seq = ++push.requestSeq;
20699:     const confirmedEnabled = push.confirmedEnabled;
20700:     push.phase = "saving";
20701:     renderMailboxGroupPush();
20702:     try {
20703:         const settings = await api("/api/expert-inbound-notifications/settings", {
20704:             method: "PUT",
20705:             body: JSON.stringify({ enabled: !confirmedEnabled }),
20706:             timeoutMs: 10000
20707:         });
20708:         if (seq !== push.requestSeq) return;
20709:         push.confirmedEnabled = settings.enabled;
20710:         push.configured = settings.configured;
20711:         push.phase = "ready";
20712:         push.uncertain = false;
20713:         renderMailboxGroupPush();
20714:         showStatus(`群消息已${settings.enabled ? "开启" : "关闭"}`, "ok");
20715:         if (push.pendingRefresh) {
20716:             push.pendingRefresh = false;
20717:             await refreshMailboxGroupPush();
20718:         }
20719:     } catch (error) {
20720:         if (seq !== push.requestSeq) return;
20721:         push.confirmedEnabled = confirmedEnabled;
20722:         push.phase = "unknown";
20723:         push.uncertain = true;
20724:         push.pendingRefresh = false;
20725:         renderMailboxGroupPush();
20726:         showStatus("群消息保存失败: " + error.message, "error");
20727:         await refreshMailboxGroupPush();
20728:     }
20729: }
20730: 
20731: function initMailboxGroupPush() {
20732:     const btn = $("#mailboxGroupPushBtn");
20733:     if (!btn || mailboxGroupPushState.initialized) return;
20734:     mailboxGroupPushState.initialized = true;
20735:     // Native button activation supplies both Enter and Space without a second handler.
20736:     btn.addEventListener("click", saveMailboxGroupPush);
20737:     $("#mailboxRefreshBtn")?.addEventListener("click", () => refreshMailboxGroupPush());
20738:     $("#logoutBtn")?.addEventListener("click", invalidateMailboxGroupPush);
20739:     document.addEventListener("visibilitychange", () => {
20740:         if (!document.hidden && state.view === "mailbox" && appStarted) refreshMailboxGroupPush();
20741:     });
20742:     renderMailboxGroupPush();
20743: }
```

## src/main/resources/static/world-clock.js:656
```
656:             if (state.destroyed || !state.sessionVisible) return;
657:             if (!state.selectedInitialized) {
658:                 var nowParts = beijingParts(Date.now());
659:                 state.selectedDate = dateText(nowParts);
660:                 state.selectedTime = timeText(nowParts, false);
661:                 state.selectedInitialized = true;
662:             }
663:             nodes.date.value = state.selectedDate;
664:             nodes.time.value = state.selectedTime;
665:             state.open = true;
666:             panel.hidden = false;
667:             trigger.setAttribute("aria-expanded", "true");
668:             nodes.chevron.textContent = "⌃";
669:             applySelection();
670:             if (state.catalogStatus === "idle") loadCatalog();
671:             render();
672:             focusNode(nodes.date);
673:         }
674: 
```
