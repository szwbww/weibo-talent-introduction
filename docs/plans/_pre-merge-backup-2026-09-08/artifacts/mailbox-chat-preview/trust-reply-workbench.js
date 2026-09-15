(function (global) {
    "use strict";

    // 可信回复工作台（整封式 RAG）—— 计划 05 重写版。
    // 单一流程：compose() → 渲染三块（正文 / 用到了哪些事实 / 未识别的提问）
    // → 四个操作（重新生成 / 加事实 / 去事实 / 直接编辑正文）。
    // 所有生成请求指向 /api/rag-reply/compose（03）；旧版按条目工作台的端点
    // 一律不再调用。契约：I-24 ~ I-29、G-1..G-8（05-workbench-frontend-replace.md）。
    // 样式契约 S-1..S-5 见 styles.css；本文件不使用本契约之外的 class，不写 inline style。

    if (global.TrustReplyWorkbench) return;

    const COMPOSE_PATH = "/api/rag-reply/compose";
    const SNIPPET_PATH = "/api/reply-snippets";
    const MODES = Object.freeze({ SIMULATION: "SIMULATION", LIVE: "LIVE", AUTO_PREVIEW: "AUTO_PREVIEW" });
    const MODEL_LABELS = Object.freeze({
        DEEPSEEK_V4_FLASH: "DeepSeek V4 Flash",
        DEEPSEEK_V4_PRO: "DeepSeek V4 Pro"
    });
    // 回复框架四个槽位：首两段（尊语/开场白）与尾两段（致谢语/结束语）的正文
    // 由 compose 结果 frame 提供；请求侧 frameSelection 键与槽位一一对应。
    const FRAME_SLOTS = Object.freeze([
        { key: "salutationSnippetId", snippetType: "SALUTATION", label: "尊语" },
        { key: "greetingSnippetId", snippetType: "GREETING", label: "开场白" },
        { key: "ackSnippetId", snippetType: "ACK", label: "致谢语" },
        { key: "closingSnippetId", snippetType: "CLOSING", label: "结束语" }
    ]);
    const HEAD_SLOT_KEYS = Object.freeze(["salutationSnippetId", "greetingSnippetId"]);
    // I-29 / D-1：零强制门禁 —— 未识别提问数量、事实数量、REVIEW 事实都不禁用发送。
    const COMPOSE_ERROR_TEXT = Object.freeze({
        RAG_VERBATIM_MISSING: "生成结果缺少逐字原文，整次生成按失败处理，请重新生成。",
        RAG_LLM_UNAVAILABLE: "模型服务暂不可用，请稍后重试。",
        RAG_FACT_CODE_INVALID: "添加的事实不存在或已停用，请核对 fact_code 后重试。",
        RAG_REPLY_SOURCE_NOT_FOUND: "来信不存在，请刷新页面后重试。",
        RAG_REPLY_SOURCE_NOT_INBOUND: "所选邮件不是来信，请重新选择。",
        RAG_REPLY_SOURCE_CONTACT_REQUIRED: "来信未绑定联系人，无法生成，请先补全联系人。",
        RAG_REPLY_SOURCE_INVALID: "生成来源无效，请刷新页面后重试。"
    });

    function escapeText(value) {
        return String(value == null ? "" : value)
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;")
            .replace(/'/g, "&#039;");
    }

    function contextPathBase(contextPath) {
        return String(contextPath || "").replace(/\/+$/, "");
    }

    function contentFirstLine(content) {
        const first = String(content || "").split("\n").map((line) => line.trim()).find((line) => line.length > 0);
        const line = first == null ? "" : first;
        return line.length > 42 ? `${line.slice(0, 42)}…` : line;
    }

    function errorTextFor(code, message, status) {
        if (code && COMPOSE_ERROR_TEXT[code]) return COMPOSE_ERROR_TEXT[code];
        if (code && message) return `${code}：${message}`;
        return status ? `生成失败（HTTP ${status}），请重试。` : "生成失败，请重试。";
    }

    // —— 渲染：正文段落（I-26：逐字样式只来自 renderMode，绝不做正文文本比对）——

    function renderParagraph(paragraph) {
        const classes = ["trust-reply-para"];
        if (paragraph.kind === "frame") {
            classes.push("frame");
        } else if (paragraph.renderMode === "VERBATIM") {
            classes.push("verbatim");
        }
        if (paragraph.edited) classes.push("edited");
        let tagText = "";
        if (paragraph.kind === "frame") {
            tagText = `回复框架 · ${paragraph.slotLabel || ""}`;
        } else if (paragraph.renderMode === "VERBATIM") {
            tagText = paragraph.edited ? "逐字 · 已脱离事实原文" : "逐字";
        }
        const tag = tagText ? `<span class="trust-reply-para-tag">${escapeText(tagText)}</span>` : "";
        const indexAttr = paragraph.index == null ? "" : ` data-para-index="${paragraph.index}"`;
        const editableAttr = paragraph.editable === true ? ' contenteditable="true"' : "";
        return `<div class="${classes.join(" ")}"${indexAttr}${editableAttr}>${escapeText(paragraph.text)}${tag}</div>`;
    }

    function renderDraft(state) {
        const parts = [];
        if (state.error) {
            parts.push(`<div class="ai-reply-error" role="alert">${escapeText(state.error)}</div>`);
        }
        const head = state.frameParagraphs.filter((frame) => HEAD_SLOT_KEYS.indexOf(frame.slotKey) >= 0 && frame.text);
        const tail = state.frameParagraphs.filter((frame) => HEAD_SLOT_KEYS.indexOf(frame.slotKey) < 0 && frame.text);
        const items = [].concat(head, state.paragraphs, tail);
        if (items.length === 0) {
            parts.push('<span class="muted">点击「重新生成」生成整封草稿。</span>');
        } else {
            items.forEach((paragraph) => {
                const editable = state.editMode && paragraph.kind === "body";
                const copy = { ...paragraph, editable };
                parts.push(renderParagraph(copy));
            });
        }
        return parts.join("");
    }

    function renderFactsList(state) {
        if (state.usedFacts.length === 0) {
            return '<span class="muted">尚未生成。</span>';
        }
        return state.usedFacts.map((fact, index) => {
            const badges = [];
            if (fact.renderMode === "VERBATIM") badges.push('<span class="rag-badge verbatim">逐字</span>');
            if (fact.origin === "MANDATORY") badges.push('<span class="rag-badge">强制</span>');
            if (fact.status === "REVIEW") badges.push('<span class="rag-badge status-review">REVIEW</span>');
            if (fact.riskLevel === "HIGH") badges.push('<span class="rag-badge risk-high">HIGH</span>');
            const badgeLine = badges.length > 0 ? `<div>${badges.join(" ")}</div>` : "";
            const remove = fact.origin === "MANDATORY"
                ? ""
                : `<button type="button" class="trust-reply-fact-remove" data-action="remove-fact" data-code="${escapeText(fact.factCode)}" title="移除并重新生成"${state.busy ? " disabled" : ""}>×</button>`;
            return `<div class="trust-reply-fact"><span class="trust-reply-fact-index">${index + 1}</span><span class="trust-reply-fact-main"><div><span class="trust-reply-fact-code">${escapeText(fact.factCode)}</span> <span class="muted">${escapeText(fact.title)}</span></div>${badgeLine}</span>${remove}</div>`;
        }).join("");
    }

    function renderUnaddressedList(state) {
        return state.unaddressed.map((item) =>
            `<div class="trust-reply-unaddressed-item">来信问了<q>${escapeText(item.quote)}</q>，本封未作答。<span class="trust-reply-unaddressed-why">${escapeText(item.reason)}</span></div>`
        ).join("");
    }

    function renderFrameSelects(state) {
        const selection = state.frameSelection || {};
        return FRAME_SLOTS.map((slot) => {
            const options = state.frameOptions
                .filter((option) => option.snippetType === slot.snippetType)
                .sort((a, b) => (Number(a.displayOrder) - Number(b.displayOrder)) || (Number(a.id) - Number(b.id)))
                .map((option) => `<option value="${escapeText(option.id)}"${Number(selection[slot.key]) === Number(option.id) ? " selected" : ""}>${escapeText(contentFirstLine(option.content))}</option>`)
                .join("");
            return `<span class="muted">${escapeText(slot.label)}</span><select data-frame-slot="${slot.key}"${state.busy ? " disabled" : ""}><option value="">不使用</option>${options}</select>`;
        }).join("");
    }

    function renderModelOptions(state) {
        return Object.keys(MODEL_LABELS).map((key) =>
            `<option value="${key}"${state.model === key ? " selected" : ""}>${MODEL_LABELS[key]}</option>`
        ).join("");
    }

    function renderSendBar(state) {
        const hasDraft = state.assemblyReady || state.paragraphs.length > 0 || hasAnyFrame(state);
        const completeDisabled = !hasDraft || state.busy;
        const completeLabel = state.mode === MODES.SIMULATION ? "完成模拟并评估" : "采用到人工回复";
        const note = state.unaddressed.length > 0
            ? `<span class="muted" data-role="unaddressed-note">未识别提问 ${state.unaddressed.length} 项</span>`
            : "";
        const flag = state.dirty
            ? '<span class="muted" data-role="dirty-flag">已手工编辑</span>'
            : "";
        return `<div class="trust-reply-send" data-workbench-control>${note}${flag}<button type="button" class="button primary" data-action="complete"${completeDisabled ? " disabled" : ""}>${completeLabel}</button></div>`;
    }

    function renderShell(state) {
        const modeNote = state.mode === MODES.SIMULATION ? "模拟 · 不外发" : "正式回复";
        const toolbar = `<div class="trust-reply-toolbar" data-workbench-control><p class="trust-reply-mode-note" data-role="mode-description">${modeNote}</p><div><span class="muted">模型</span> <select data-role="model-select"${state.busy ? " disabled" : ""}>${renderModelOptions(state)}</select> <button type="button" class="button primary" data-action="regenerate"${state.busy ? " disabled" : ""}>重新生成</button> <button type="button" class="button" data-action="edit-body">${state.editMode ? "完成编辑" : "编辑正文"}</button> <button type="button" class="button" data-action="copy-draft">复制</button></div></div>`;
        const frameBar = `<div class="trust-reply-frame-bar" data-workbench-control><span class="muted">回复框架</span>${renderFrameSelects(state)}</div>`;
        const layout = `<div class="trust-reply-layout"><section class="panel"><div class="trust-reply-doc" data-role="draft">${renderDraft(state)}</div></section><div><section class="panel"><div class="panel-head"><h2>用到了哪些事实</h2><span class="muted" data-role="fact-count">${state.usedFacts.length} 条${state.forcedFactCount > 0 ? ` · ${state.forcedFactCount} 条强制` : ""}</span></div><div class="trust-reply-facts" data-role="facts">${renderFactsList(state)}<div class="trust-reply-fact-add"><input data-role="add-fact-input" placeholder="输入 fact_code 后点添加，如 KB-COMM-044"><button type="button" class="button" data-action="add-fact"${state.busy ? " disabled" : ""}>添加</button></div></div></section><section class="panel"><div class="panel-head"><h2>未识别的提问</h2><span class="muted" data-role="unaddressed-count">${state.unaddressed.length} 项</span></div><div class="trust-reply-unaddressed" data-role="unaddressed">${renderUnaddressedList(state)}</div></section></div></div>`;
        const busyOverlay = state.busy
            ? '<div class="trust-reply-busy-overlay" data-role="busy-overlay"><div class="trust-reply-busy-card"><div class="trust-reply-busy-text">正在生成回复…</div><div class="trust-reply-busy-hint">生成完成后会更新正文与事实列表。</div></div></div>'
            : "";
        return `<div class="trust-reply-workbench">${frameBar}${toolbar}${layout}${renderSendBar(state)}${busyOverlay}</div>`;
    }

    // —— 状态与请求（I-28：加/去事实只改请求参数并重新 compose）——

    function createState(options) {
        const source = options.source || {};
        return {
            mode: options.mode || MODES.LIVE,
            source: {
                sourceType: String(source.sourceType || ""),
                sourceId: source.sourceId == null ? null : Number(source.sourceId)
            },
            model: "DEEPSEEK_V4_FLASH",
            forcedFactCodes: [],
            excludedFactCodes: [],
            frameSelection: { salutationSnippetId: null, greetingSnippetId: null, ackSnippetId: null, closingSnippetId: null },
            frameOptions: [],
            frameParagraphs: FRAME_SLOTS.map((slot) => ({ kind: "frame", slotKey: slot.key, slotLabel: slot.label, text: null })),
            paragraphs: [],
            usedFacts: [],
            usedFactCodes: [],
            unaddressed: [],
            corpusFingerprint: "",
            forcedFactCount: 0,
            busy: false,
            dirty: false,
            editMode: false,
            error: "",
            assemblyReady: false
        };
    }

    function hasAnyFrame(state) {
        return state.frameParagraphs.some((frame) => !!frame.text);
    }

    // I-28：去事实 —— 追加 excludedFactCodes 并从 forcedFactCodes 中移除；
    // 绝不本地拼接/删除正文段落。
    function applyRemoveFact(state, factCode) {
        const code = String(factCode || "").trim();
        if (!code) return;
        state.excludedFactCodes = state.excludedFactCodes.filter((item) => item !== code);
        state.excludedFactCodes.push(code);
        state.forcedFactCodes = state.forcedFactCodes.filter((item) => item !== code);
    }

    // I-28：加事实 —— 追加 forcedFactCodes 并移出 excludedFactCodes（强制优先）。
    function applyAddFact(state, factCode) {
        const code = String(factCode || "").trim().toUpperCase();
        if (!code) return;
        state.forcedFactCodes = state.forcedFactCodes.filter((item) => item !== code);
        state.forcedFactCodes.push(code);
        state.excludedFactCodes = state.excludedFactCodes.filter((item) => item !== code);
    }

    function applyFrameSelect(state, slotKey, snippetId) {
        const slot = FRAME_SLOTS.find((item) => item.key === slotKey);
        if (!slot) return;
        const next = snippetId ? Number(snippetId) : null;
        state.frameSelection[slot.key] = next;
        const frame = state.frameParagraphs.find((item) => item.slotKey === slot.key);
        if (frame) {
            const chosen = next == null ? null : state.frameOptions.find((option) => Number(option.id) === next);
            frame.text = chosen ? String(chosen.content) : null;
        }
    }

    function buildComposeRequest(state) {
        return {
            sourceType: state.source.sourceType,
            sourceId: state.source.sourceId,
            model: state.model,
            forcedFactCodes: state.forcedFactCodes.slice(),
            excludedFactCodes: state.excludedFactCodes.slice(),
            frameSelection: {
                salutationSnippetId: state.frameSelection.salutationSnippetId,
                greetingSnippetId: state.frameSelection.greetingSnippetId,
                ackSnippetId: state.frameSelection.ackSnippetId,
                closingSnippetId: state.frameSelection.closingSnippetId
            }
        };
    }

    function documentText(state) {
        const parts = [];
        state.frameParagraphs.forEach((frame) => {
            if (frame.text) parts.push(frame.text);
        });
        state.paragraphs.forEach((paragraph) => {
            if (paragraph.text) parts.push(paragraph.text);
        });
        return parts.join("\n\n");
    }

    function buildAssembly(state) {
        return {
            text: documentText(state),
            usedFactCodes: state.usedFactCodes.slice(),
            ragCorpusFingerprint: state.corpusFingerprint,
            unaddressed: state.unaddressed.map((item) => ({ quote: item.quote, reason: item.reason }))
        };
    }

    function applyComposeResult(state, result) {
        const frame = result.frame || {};
        const selection = frame.selection || {};
        FRAME_SLOTS.forEach((slot) => {
            const value = selection[slot.key];
            state.frameSelection[slot.key] = value == null ? null : Number(value);
        });
        const frameTexts = {
            salutationSnippetId: frame.salutation || "",
            greetingSnippetId: frame.greeting || "",
            ackSnippetId: frame.acknowledgement || "",
            closingSnippetId: frame.closing || ""
        };
        state.frameParagraphs = FRAME_SLOTS.map((slot) => ({
            kind: "frame",
            slotKey: slot.key,
            slotLabel: slot.label,
            text: frameTexts[slot.key] ? frameTexts[slot.key] : null
        }));
        state.paragraphs = (result.bodyParagraphs || []).map((paragraph, index) => ({
            kind: "body",
            index,
            text: paragraph.text,
            originalText: paragraph.text,
            renderMode: paragraph.renderMode,
            edited: false
        }));
        state.usedFacts = (result.usedFacts || []).map((fact) => ({
            factCode: fact.factCode,
            title: fact.title || "",
            renderMode: fact.renderMode || "",
            riskLevel: fact.riskLevel || "",
            status: fact.status || "",
            origin: fact.origin || "MODEL"
        }));
        state.usedFactCodes = state.usedFacts.map((fact) => fact.factCode);
        state.forcedFactCount = state.usedFacts.filter((fact) => fact.origin === "MANDATORY").length;
        state.unaddressed = (result.unaddressed || []).map((item) => ({ quote: item.quote, reason: item.reason }));
        state.corpusFingerprint = result.corpusFingerprint || "";
        state.error = "";
        state.busy = false;
        state.dirty = false;
        state.assemblyReady = true;
    }

    // —— 实例（I-25：每个实例独立 requestSeq + AbortController 集合）——

    function createInstance(host, options) {
        const state = createState(options);
        const requestSeq = { value: 0 };
        const controllers = new Set();
        const disposed = { value: false };
        const contextPath = contextPathBase(options.contextPath);
        const listeners = [];

        function listen(type, handler) {
            host.addEventListener(type, handler);
            listeners.push([type, handler]);
        }

        function render() {
            if (disposed.value) return;
            host.innerHTML = renderShell(state);
        }

        function notifyChange() {
            if (disposed.value) return;
            if (typeof options.onChange === "function") options.onChange();
        }

        function compose() {
            if (disposed.value || state.busy) return;
            state.editMode = false;
            // I-25：新请求使旧请求失效；旧请求的 late response 一律不落地。
            requestSeq.value += 1;
            const mySeq = requestSeq.value;
            controllers.forEach((controller) => controller.abort());
            controllers.clear();
            const controller = new global.AbortController();
            controllers.add(controller);
            state.busy = true;
            state.error = "";
            render();
            const url = `${contextPath}${COMPOSE_PATH}`;
            const body = JSON.stringify(buildComposeRequest(state));
            global.fetch(url, {
                method: "POST",
                headers: { "Content-Type": "application/json", "Accept": "application/json" },
                body,
                signal: controller.signal
            }).then((response) => {
                if (response.ok) return response.json();
                return response.json().catch(() => ({})).then((payload) => {
                    if (response.status === 401 || response.status === 403) {
                        if (typeof options.onUnauthorized === "function") options.onUnauthorized(response);
                    }
                    const error = new Error(payload.code || `HTTP_${response.status}`);
                    error.code = payload.code || "";
                    error.status = response.status;
                    error.message = payload.message || "";
                    throw error;
                });
            }).then((result) => {
                if (disposed.value || mySeq !== requestSeq.value) return;
                applyComposeResult(state, result);
                render();
            }).catch((error) => {
                if (disposed.value || mySeq !== requestSeq.value) return;
                if (error && error.name === "AbortError") return;
                state.busy = false;
                state.error = errorTextFor(error.code, error.message, error.status);
                render();
            }).then(() => {
                controllers.delete(controller);
            });
        }

        function regenerate() {
            if (disposed.value || state.busy) return;
            // I-27：手改过正文后重新生成必须二次确认；取消则保留手改内容。
            if (state.dirty) {
                let confirmed = false;
                try {
                    confirmed = global.confirm ? global.confirm("正文有手工修改，重新生成将丢弃这些修改，确定继续吗？") : true;
                } catch (err) {
                    confirmed = true;
                }
                if (!confirmed) return;
            }
            state.editMode = false;
            state.dirty = false;
            notifyChange();
            compose();
        }

        function removeFact(factCode) {
            if (disposed.value || state.busy) return;
            applyRemoveFact(state, factCode);
            notifyChange();
            compose();
        }

        function addFact(factCode) {
            if (disposed.value || state.busy) return;
            const code = String(factCode || "").trim().toUpperCase();
            if (!code) return;
            applyAddFact(state, code);
            notifyChange();
            compose();
        }

        function complete() {
            if (disposed.value || state.busy) return;
            if (!state.assemblyReady && state.paragraphs.length === 0 && !hasAnyFrame(state)) return;
            // 采用时以当前正文为准（含手改），不再触发网络请求。
            const assembly = buildAssembly(state);
            if (typeof options.onComplete === "function") options.onComplete(assembly);
        }

        function loadSnippetOptions() {
            if (disposed.value) return;
            const controller = new global.AbortController();
            controllers.add(controller);
            const url = `${contextPath}${SNIPPET_PATH}`;
            global.fetch(url, {
                method: "GET",
                headers: { "Accept": "application/json" },
                signal: controller.signal
            }).then((response) => {
                if (!response.ok) return [];
                return response.json();
            }).then((list) => {
                if (disposed.value) return;
                state.frameOptions = (list || []).filter((option) => option && option.snippetType && option.enabled !== false)
                    .map((option) => ({
                        id: option.id,
                        snippetType: option.snippetType,
                        content: option.content || "",
                        displayOrder: option.displayOrder == null ? 100 : Number(option.displayOrder)
                    }));
                render();
            }).catch(() => {
                // 选项加载失败不阻断：compose 仍走服务端默认框架。
            }).then(() => {
                controllers.delete(controller);
            });
        }

        function unmount() {
            if (disposed.value) return;
            disposed.value = true;
            requestSeq.value += 1;
            controllers.forEach((controller) => controller.abort());
            controllers.clear();
            listeners.forEach((pair) => host.removeEventListener(pair[0], pair[1]));
            listeners.length = 0;
        }

        function onClick(event) {
            if (disposed.value) return;
            const button = event.target && typeof event.target.closest === "function"
                ? event.target.closest("[data-action]")
                : null;
            if (!button) return;
            const action = button.dataset ? button.dataset.action : "";
            if (action === "regenerate") {
                regenerate();
            } else if (action === "edit-body") {
                state.editMode = !state.editMode;
                render();
            } else if (action === "copy-draft") {
                const text = documentText(state);
                if (text && global.navigator && global.navigator.clipboard && global.navigator.clipboard.writeText) {
                    global.navigator.clipboard.writeText(text).catch(() => {});
                }
            } else if (action === "add-fact") {
                const input = host.querySelector ? host.querySelector('[data-role="add-fact-input"]') : null;
                const value = input && typeof input.value === "string" ? input.value : "";
                addFact(value);
            } else if (action === "remove-fact") {
                const code = button.dataset ? button.dataset.code : "";
                removeFact(code);
            } else if (action === "complete") {
                complete();
            }
        }

        function onChangeEvent(event) {
            if (disposed.value) return;
            const target = event.target;
            if (!target || typeof target.closest !== "function") return;
            const modelSelect = target.closest('[data-role="model-select"]');
            if (modelSelect && modelSelect.value !== undefined && !state.busy) {
                if (modelSelect.value && MODEL_LABELS[modelSelect.value]) {
                    state.model = modelSelect.value;
                    notifyChange();
                    render();
                }
                return;
            }
            const frameSelect = target.closest("select[data-frame-slot]");
            if (frameSelect && frameSelect.dataset && frameSelect.dataset.frameSlot && !state.busy) {
                applyFrameSelect(state, frameSelect.dataset.frameSlot, frameSelect.value);
                notifyChange();
                render();
            }
        }

        function onInputEvent(event) {
            if (disposed.value || !state.editMode) return;
            const target = event.target;
            if (!target || typeof target.closest !== "function") return;
            const paragraph = target.closest(".trust-reply-para");
            if (!paragraph || !paragraph.dataset || paragraph.dataset.paraIndex == null) return;
            const index = Number(paragraph.dataset.paraIndex);
            const current = state.paragraphs[index];
            if (!current) return;
            const text = paragraph.textContent || "";
            // 实时红框反馈；state 的提交统一在 focusout（避免打字中整段重绘丢光标）。
            if (text !== current.originalText && paragraph.classList && typeof paragraph.classList.add === "function") {
                paragraph.classList.add("edited");
            }
        }

        function onFocusOutEvent(event) {
            if (disposed.value || !state.editMode) return;
            const target = event.target;
            if (!target || typeof target.closest !== "function") return;
            const paragraph = target.closest(".trust-reply-para");
            if (!paragraph || !paragraph.dataset || paragraph.dataset.paraIndex == null) return;
            const index = Number(paragraph.dataset.paraIndex);
            const current = state.paragraphs[index];
            if (!current) return;
            const text = (paragraph.textContent || "").replace(/\u00a0/g, " ");
            if (text !== current.text) {
                current.text = text;
                if (text !== current.originalText) {
                    current.edited = current.renderMode === "VERBATIM";
                    state.dirty = true;
                }
                render();
            }
        }

        // I-24：宿主事件委托；unmount 时逐对解绑（I-25）。
        listen("click", onClick);
        listen("change", onChangeEvent);
        listen("input", onInputEvent);
        listen("focusout", onFocusOutEvent);

        render();
        loadSnippetOptions();
        if (options.autoBootstrap !== false) {
            compose();
        }

        return { unmount };
    }

    function mount(host, options) {
        return createInstance(host, options || {});
    }

    global.TrustReplyWorkbench = Object.freeze({
        mount,
        // 纯函数只读导出：供前端契约测试直接断言（先例：旧版导出 reorderFactIds）。
        renderParagraph,
        buildComposeRequest,
        applyRemoveFact,
        applyAddFact,
        applyFrameSelect,
        buildAssembly
    });
})(typeof window !== "undefined" ? window : globalThis);
