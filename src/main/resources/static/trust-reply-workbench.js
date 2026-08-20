(function (global) {
    "use strict";

    if (global.TrustReplyWorkbench) return;

    const MODES = Object.freeze({ SIMULATION: "SIMULATION", LIVE: "LIVE", AUTO_PREVIEW: "AUTO_PREVIEW" });
    const SOURCES = Object.freeze({ TRAINING_MAIL: "TRAINING_MAIL", LIVE_INBOUND: "LIVE_INBOUND" });
    // I-1: explicit mode -> source pairing table; AUTO_PREVIEW mirrors the LIVE
    // source (same inbound record) but is rendered read-only.
    const MODE_SOURCE = Object.freeze({
        SIMULATION: SOURCES.TRAINING_MAIL,
        LIVE: SOURCES.LIVE_INBOUND,
        AUTO_PREVIEW: SOURCES.LIVE_INBOUND
    });
    const MODEL_LABELS = Object.freeze({
        DEEPSEEK_V4_FLASH: "DeepSeek V4 Flash",
        DEEPSEEK_V4_PRO: "DeepSeek V4 Pro"
    });
    const HANDLING_LABELS = Object.freeze({
        ANSWER_WITH_EVIDENCE: "依据完整回答",
        ANSWER_SUPPORTED_PART: "回答有依据部分",
        ANSWER_FROM_OPERATOR_INPUT: "按回答说明生成",
        ACKNOWLEDGE_PENDING: "确认待补充",
        OMIT: "省略此项"
    });
    const GENERATION_KIND_LABELS = Object.freeze({
        AI_GENERATED: "AI 生成",
        SAFE_TEMPLATE: "安全模板",
        OMITTED: "已省略"
    });
    const COVERAGE_LABELS = Object.freeze({
        GROUNDED: "GROUNDED · 依据充分",
        PARTIAL: "PARTIAL · 部分有据",
        UNSUPPORTED: "UNSUPPORTED · 无依据"
    });
    // P0 (I-3): SSE / HTTP 错误码 → 运营可读中文。查不到时退回 error.message。
    const WORKBENCH_ERROR_TEXT = Object.freeze({
        TRUST_REPLY_SOURCE_STALE: "来信内容已变化，请刷新工作台后重试。",
        TRUST_REPLY_EVIDENCE_STALE: "本条的事实已变化，请刷新工作台后重试。",
        TRUST_REPLY_EVIDENCE_VERSION_REQUIRED: "缺少事实版本，请刷新工作台后重试。",
        TRUST_REPLY_REQUEST_KEY_INVALID: "摘要标识与来信对不上，请刷新工作台后重试。",
        TRUST_REPLY_FACT_SELECTION_INVALID: "事实选择与来信摘要对不上，请刷新工作台后重试。",
        TRUST_REPLY_FACT_SELECTION_AMBIGUOUS: "事实选择参数冲突，请刷新工作台后重试。",
        TRUST_REPLY_FACT_ALREADY_ASSIGNED: "同一条事实被多个摘要绑定，请先解除其中一处。",
        TRUST_REPLY_HANDLING_INVALID: "该处理方式不适用于本条摘要的当前状态。",
        TRUST_REPLY_OPERATOR_INSTRUCTION_INVALID: "回答说明为空或超过 500 字。",
        TRUST_REPLY_ITEM_GENERATION_FAILED: "AI 未能产出可用的回答，请重试或换一种处理方式。",
        TRUST_REPLY_CLAIM_INVALID: "生成的内容未通过内容安全校验。",
        TRUST_REPLY_ACKNOWLEDGEMENT_INVALID: "致意内容未通过内容安全校验。",
        TRUST_REPLY_LOCKED_ITEM_INVALID: "已锁定的回答与当前状态不一致，请重新生成本条。",
        TRUST_REPLY_ITEM_VERSION_INVALID: "版本身份校验未通过，请重新生成本条。",
        TRUST_REPLY_STATE_CONFLICT: "该封信的工作台状态已被其他页面修改，请刷新后重试。",
        AI_REPLY_GENERATION_FAILED: "AI 生成失败，请重试。"
    });
    const STATE_SCHEMA_VERSION = "trust-reply-workbench-state-v3";
    const FRAME_SLOTS = Object.freeze([
        { key: "salutationSnippetId", snippetType: "SALUTATION", label: "尊语" },
        { key: "greetingSnippetId", snippetType: "GREETING", label: "开场白" },
        { key: "ackSnippetId", snippetType: "ACK", label: "致谢语" },
        { key: "closingSnippetId", snippetType: "CLOSING", label: "结束语" }
    ]);
    const PREVIEW_STATE_LABELS = Object.freeze({
        LOCAL: "配置预览 · 尚未服务端整合",
        CURRENT: "服务端整合完成",
        STALE: "配置已变化 · 请重新整合"
    });

    function escapeText(value) {
        return String(value == null ? "" : value)
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;")
            .replaceAll('"', "&quot;")
            .replaceAll("'", "&#039;");
    }

    function makeId() {
        if (global.crypto && typeof global.crypto.randomUUID === "function") {
            return global.crypto.randomUUID();
        }
        const bytes = new Uint8Array(16);
        if (global.crypto && typeof global.crypto.getRandomValues === "function") {
            global.crypto.getRandomValues(bytes);
        } else {
            for (let index = 0; index < bytes.length; index += 1) {
                bytes[index] = Math.floor(Math.random() * 256);
            }
        }
        bytes[6] = (bytes[6] & 0x0f) | 0x40;
        bytes[8] = (bytes[8] & 0x3f) | 0x80;
        const hex = Array.from(bytes, (value) => value.toString(16).padStart(2, "0")).join("");
        return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
    }

    function apiUrl(contextPath, path) {
        const prefix = String(contextPath || "").replace(/\/$/, "");
        return `${prefix}${path}`;
    }

    function errorFromResponse(response, body) {
        const code = body && body.code ? body.code : `HTTP_${response.status}`;
        const error = new Error(code);
        error.code = code;
        error.status = response.status;
        return error;
    }

    function errorFromStream(data, fallback) {
        const code = data && typeof data === "object" ? (data.code || data.errorCode) : null;
        const message = data && typeof data === "object" ? data.message : data;
        const error = new Error(message || code || fallback);
        if (code) error.code = code;
        return error;
    }

    // P0 (I-3): 渲染层按 code 查中文表；查不到退回 error.message；再查不到用兜底串。
    function errorText(error, fallback) {
        const code = error && error.code ? String(error.code) : "";
        if (WORKBENCH_ERROR_TEXT[code]) return WORKBENCH_ERROR_TEXT[code];
        const message = error && error.message ? String(error.message) : "";
        return message || fallback || "请求失败，请重试";
    }

    function parseSseChunk(buffer, flush) {
        const events = [];
        let remainder = buffer;
        let boundary;
        while ((boundary = remainder.search(/\r?\n\r?\n/)) >= 0) {
            const raw = remainder.slice(0, boundary);
            remainder = remainder.slice(boundary).replace(/^\r?\n\r?\n/, "");
            const eventName = (raw.match(/^event:\s*(.+)$/m) || [null, "message"])[1].trim();
            const data = raw.split(/\r?\n/)
                .filter((line) => line.startsWith("data:"))
                .map((line) => line.slice(5).replace(/^ /, ""))
                .join("\n");
            if (!data) continue;
            let parsed = data;
            try { parsed = JSON.parse(data); } catch (_) { /* server error text remains readable */ }
            events.push({ event: eventName, data: parsed });
        }
        if (flush && remainder.trim()) {
            const parsed = parseSseChunk(`${remainder}\n\n`, false);
            events.push(...parsed.events);
            remainder = "";
        }
        return { events, remainder };
    }

    // I-2: pure array reorder shared by drag (drop target) and keyboard
    // (ArrowLeft/ArrowRight). `toIndex` is the insertion position in the array
    // AFTER removing `fromId`, clamped to [0, ids.length - 1]; the result keeps
    // the same length as the input (asserted below).
    function reorderFactIds(ids, fromId, toIndex) {
        const source = ids || [];
        const numericFrom = Number(fromId);
        const fromIndex = source.map(Number).indexOf(numericFrom);
        if (fromIndex < 0) return [...source];
        let target = Number(toIndex);
        if (!Number.isInteger(target)) target = fromIndex;
        target = Math.max(0, Math.min(source.length - 1, target));
        const next = [...source];
        const [moved] = next.splice(fromIndex, 1);
        next.splice(target, 0, moved);
        if (next.length !== source.length) {
            throw new Error("reorderFactIds: result length must equal input length");
        }
        return next;
    }

    function resolveFactDrop(ids, fromId, targetId, before) {
        const source = ids || [];
        const fromIndex = source.map(Number).indexOf(Number(fromId));
        const targetIndex = source.map(Number).indexOf(Number(targetId));
        if (fromIndex < 0 || targetIndex < 0 || fromIndex === targetIndex) return [...source];
        const targetRemainderIndex = targetIndex - (fromIndex < targetIndex ? 1 : 0);
        const toIndex = before ? targetRemainderIndex : targetRemainderIndex + 1;
        return reorderFactIds(source, fromId, toIndex);
    }

    function factActionBlockReason(flags) {
        if (flags && flags.requestPending) return "本摘要正在生成，完成后可调整事实";
        if (flags && flags.factChangePending) return "正在更新事实，完成后可继续调整";
        if (flags && flags.stateSavePending) return "正在保存工作台状态，完成后可调整事实";
        if (flags && flags.generationPending) return "正在生成回复，完成后可调整事实";
        if (flags && flags.frameSavePending) return "正在保存回复框架，完成后可调整事实";
        return "";
    }

    function factActionReasonFor(request, state) {
        return factActionBlockReason({
            requestPending: request.pending,
            factChangePending: state.factChangePending,
            stateSavePending: state.stateSavePending,
            generationPending: state.generation.pending,
            frameSavePending: state.frameSavePending
        });
    }

    function mount(host, options) {
        validateMount(host, options);
        const instance = createInstance(host, options);
        instance.bootstrap();
        return { unmount: instance.unmount };
    }

    function validateMount(host, options) {
        if (!host || typeof host !== "object" || typeof host.innerHTML !== "string") {
            throw new TypeError("TrustReplyWorkbench.mount requires a host element");
        }
        if (!options || !Object.values(MODES).includes(options.mode)) {
            return rejectMount(host, "工作台模式无效");
        }
        const source = options.source || {};
        const expectedSource = MODE_SOURCE[options.mode];
        if (!expectedSource) {
            return rejectMount(host, "工作台模式无效");
        }
        if (source.sourceType !== expectedSource || !Number.isInteger(Number(source.sourceId)) || Number(source.sourceId) <= 0) {
            return rejectMount(host, "工作台来源与页面模式不匹配");
        }
        // AUTO_PREVIEW never completes, so onComplete is optional there; the
        // remaining modes keep it mandatory (I-2).
        if (typeof options.contextPath !== "string" || (options.mode !== MODES.AUTO_PREVIEW && typeof options.onComplete !== "function")) {
            return rejectMount(host, "工作台宿主参数不完整");
        }
    }

    function rejectMount(host, message) {
        host.innerHTML = `<div class="ai-reply-error" role="alert">${escapeText(message)}</div>`;
        throw new TypeError(message);
    }

    function createInstance(host, options) {
        const source = {
            sourceType: options.source.sourceType,
            sourceId: Number(options.source.sourceId)
        };
        const state = {
            instanceId: makeId(),
            mode: options.mode,
            readOnly: options.mode === MODES.AUTO_PREVIEW,
            source,
            contextPath: options.contextPath,
            onUnauthorized: typeof options.onUnauthorized === "function" ? options.onUnauthorized : null,
            onComplete: options.onComplete,
            onChange: typeof options.onChange === "function" ? options.onChange : null,
            sourceVersion: null,
            evidenceSetVersion: null,
            // 03b (T6/I-4): the context fingerprint (training knowledge + mail
            // history) of the last bootstrap; per-item comparison against each
            // version's contextVersion drives the context-stale prompt.
            contextVersion: null,
            activePage: "facts",
            frameOptions: [],
            frameSnapshot: null,
            frameSavePending: false,
            assemblyStale: false,
            selectedModel: "DEEPSEEK_V4_FLASH",
            availableModels: ["DEEPSEEK_V4_FLASH"],
            llmEnabled: true,
            attemptTimeout: { mode: "30", seconds: 30, customSeconds: 30 },
            totalTimeout: { mode: "auto", seconds: 300, customSeconds: 300 },
            requests: [],
            rules: [],
            generation: { pending: false, stage: "", message: "", generationId: null, controller: null },
            itemControllers: new Map(),
            translationControllers: new Set(),
            assembly: null,
            // R-2: retained auto-reply preview evidence (decision + hard gates)
            // for the conclusion area; null = no evidence fetched yet.
            previewEvidence: null,
            bootSeq: 0,
            destroyed: false,
            completePending: false,
            savedStateVersion: 0,
            stateSavePending: false,
            factChangePending: false,
            sequenceCancelled: false,
            // I-3: grip keyboard moves re-render via bootstrap(); the next
            // render() restores focus to this fact's grip (consumed once).
            pendingFocusFactId: null
        };
        const listeners = [];

        if (state.readOnly) {
            if (typeof host.classList?.add === "function") {
                host.classList.add("trust-reply-readonly");
            } else {
                const existing = typeof host.getAttribute === "function" ? host.getAttribute("class") || "" : "";
                host.setAttribute("class", `${existing ? `${existing} ` : ""}trust-reply-readonly`);
            }
        }

        function isLive(seq) {
            return !state.destroyed && (seq == null || seq === state.bootSeq);
        }

        function listen(type, handler) {
            host.addEventListener(type, handler);
            listeners.push([type, handler]);
        }

        function trackController(controller, key) {
            if (key === "full") state.generation.controller = controller;
            else state.itemControllers.set(key, controller);
            return controller;
        }

        function untrackController(controller, key) {
            if (key === "full" && state.generation.controller === controller) state.generation.controller = null;
            if (key !== "full" && state.itemControllers.get(key) === controller) state.itemControllers.delete(key);
        }

        async function requestJson(path, payload, controller, method) {
            // I-2: fail-closed write gate — the read-only AUTO_PREVIEW host may
            // only call /bootstrap; every other path throws before any fetch.
            if (state.readOnly && path !== "/api/trust-reply/workbench/bootstrap") {
                throw new Error("AUTO_PREVIEW 模式禁止写操作");
            }
            const response = await global.fetch(apiUrl(state.contextPath, path), {
                method: method || "POST",
                headers: { "Content-Type": "application/json", Accept: "application/json" },
                body: JSON.stringify(payload),
                signal: controller && controller.signal
            });
            let body = null;
            try { body = await response.json(); } catch (_) { body = null; }
            if (response.status === 401 || response.status === 403) {
                state.onUnauthorized && state.onUnauthorized(response);
            }
            if (!response.ok) throw errorFromResponse(response, body);
            return body;
        }

        async function requestSse(path, payload, key, onEvent) {
            const controller = new AbortController();
            let reader = null;
            trackController(controller, key);
            try {
                const response = await global.fetch(apiUrl(state.contextPath, path), {
                    method: "POST",
                    headers: { "Content-Type": "application/json", Accept: "text/event-stream, application/json" },
                    body: JSON.stringify(payload),
                    signal: controller.signal
                });
                if (response.status === 401 || response.status === 403) {
                    state.onUnauthorized && state.onUnauthorized(response);
                }
                if (!response.ok) {
                    let body = null;
                    try { body = await response.json(); } catch (_) { body = null; }
                    throw errorFromResponse(response, body);
                }
                if (!response.body || typeof response.body.getReader !== "function") {
                    const body = await response.json();
                    onEvent("result", body);
                    return;
                }
                reader = response.body.getReader();
                const decoder = new TextDecoder();
                let buffer = "";
                while (true) {
                    const chunk = await reader.read();
                    if (chunk.done) {
                        const parsed = parseSseChunk(buffer, true);
                        parsed.events.forEach((event) => onEvent(event.event, event.data));
                        break;
                    }
                    buffer += decoder.decode(chunk.value, { stream: true });
                    const parsed = parseSseChunk(buffer, false);
                    buffer = parsed.remainder;
                    parsed.events.forEach((event) => onEvent(event.event, event.data));
                }
            } finally {
                if (reader && typeof reader.cancel === "function") {
                    try { await reader.cancel(); } catch (_) { /* stream already closed */ }
                }
                untrackController(controller, key);
            }
        }

        function cancelController(controller) {
            if (controller && typeof controller.abort === "function") controller.abort();
        }

        function invalidateAssembly() {
            const hadAssembly = !!state.assembly;
            state.assembly = null;
            if (hadAssembly) state.onChange && state.onChange();
            return hadAssembly;
        }

        function sameSource(candidate) {
            return candidate
                && candidate.sourceType === source.sourceType
                && Number(candidate.sourceId) === source.sourceId;
        }

        function hasGenerationIdentity(result) {
            return !!result
                && sameSource(result.source)
                && typeof result.sourceVersion === "string"
                && result.sourceVersion === state.sourceVersion
                && typeof result.evidenceSetVersion === "string"
                && result.evidenceSetVersion === state.evidenceSetVersion;
        }

        function hasVersionIdentity(version, requestKey) {
            // 03a (I-1): a generated version is only acceptable when its
            // evidence version matches THIS request's per-request value; the
            // whole-draft aggregate says nothing about a single item.
            const request = findRequest(requestKey);
            return !!version
                && version.requestKey === requestKey
                && typeof version.sourceVersion === "string"
                && version.sourceVersion === state.sourceVersion
                && !!request
                && typeof version.evidenceSetVersion === "string"
                && version.evidenceSetVersion === request.evidenceSetVersion;
        }

        function isStaleError(error) {
            const code = error && (error.code || error.message);
            return ["TRUST_REPLY_SOURCE_STALE", "TRUST_REPLY_EVIDENCE_STALE"].some((item) => String(code || "").includes(item));
        }

        function isFrameStaleError(error) {
            const code = error && (error.code || error.message);
            return String(code || "").includes("TRUST_REPLY_FRAME_STALE");
        }

        function handleStaleGeneration(seq, message) {
            if (!isLive(seq)) return;
            resetVersions();
            state.generation.generationId = null;
            setStatus(message || "来源或事实已变化，请确认后刷新工作台", "STALE");
            render();
            if (typeof global.confirm === "function" && global.confirm("来源或事实已变化，确认刷新工作台并重新生成？")) {
                void bootstrap();
            }
        }

        // Frame-only staleness (I-3): keep the locked answers, drop the stale
        // assembly, surface the frame page and ask the operator to refresh the
        // frame options before re-integrating. Unlike SOURCE/EVIDENCE stale it
        // must never reset versions.
        function handleFrameStale(seq, message) {
            if (!isLive(seq)) return;
            state.assembly = null;
            state.assemblyStale = true;
            setStatus(message || "框架配置已变化，请重新选择后整合", "FRAME_STALE");
            state.activePage = "frame";
            render();
        }

        function setStatus(message, type) {
            state.generation.message = message || "";
            state.generation.stage = type || state.generation.stage || "";
        }

        function resetVersions() {
            state.requests.forEach((request) => {
                request.versions = [];
                request.activeVersionId = null;
                request.resolvedVersionId = null;
                request.pending = false;
                request.error = null;
                request.expanded = request.coverage !== "GROUNDED";
                request.factPickerOpen = false;
                request.questionTranslation = { state: "idle", text: "" };
                request.answerTranslationsByVersionId = {};
                request.requestSeq += 1;
            });
            state.itemControllers.forEach((controller) => cancelController(controller));
            state.itemControllers.clear();
            cancelController(state.generation.controller);
            state.generation.controller = null;
            state.translationControllers.forEach((controller) => cancelController(controller));
            state.translationControllers.clear();
            state.generation.pending = false;
            invalidateAssembly();
        }

        function requestFromCoverage(coverage, fallbackEvidence) {
            return (coverage || []).map((item, index) => ({
                requestKey: item.requestKey,
                requestText: item.requestText || "",
                index: item.index == null ? index : item.index,
                coverage: item.status || "",
                factRuleIds: [...(item.factRuleIds || [])],
                // P1 (I-2): 服务端未采纳的绑定，仅用于提示，不参与任何请求载荷。
                droppedFactRuleIds: [...(item.droppedFactRuleIds || [])],
                // 03a (I-1): per-request evidence version from the server
                // coverage; the authority for this item's version identity.
                // The aggregate fallback only covers pre-03a servers whose
                // coverage lacks the per-request field; the current server
                // always fills it (T3), so the fallback never fires there.
                evidenceSetVersion: item.evidenceSetVersion || fallbackEvidence || "",
                // 03a (I-5/S-1): set when this item's facts changed and its
                // generated answer must be regenerated; renders the stale hint.
                evidenceStale: false,
                // 03b (T6/I-4): set when this item's locked version was
                // generated under a different context fingerprint (old
                // training knowledge / mail history); renders the per-item
                // hint and participates in the one-click rerun.
                contextStale: false,
                factPickerOpen: false,
                availableHandlings: [...(item.allowedHandlings || [])],
                recommendedHandling: item.recommendedHandling || item.allowedHandlings?.[0] || "OMIT",
                draftHandling: item.recommendedHandling || item.allowedHandlings?.[0] || "OMIT",
                instruction: "",
                suggestedInstruction: item.suggestedInstruction || "",
                autoFilled: false,
                instructionEditedByOperator: false,
                versions: [],
                activeVersionId: null,
                resolvedVersionId: null,
                expanded: item.status !== "GROUNDED",
                questionTranslation: { state: "idle", text: "" },
                answerTranslationsByVersionId: {},
                requestSeq: 0,
                pending: false,
                error: null
            }));
        }

        // I-1/G-1 canonical matrix: every canonical request in order, including
        // empty fact lists. This is the only fact payload sent to the server.
        function serializeRequestFactSelections() {
            return state.requests.map((request) => ({
                requestKey: request.requestKey,
                factRuleIds: [...(request.factRuleIds || [])]
            }));
        }

        function snapshotFrame(frame) {
            if (!frame) return null;
            return {
                selection: {
                    salutationSnippetId: frame.selection?.salutationSnippetId ?? null,
                    greetingSnippetId: frame.selection?.greetingSnippetId ?? null,
                    ackSnippetId: frame.selection?.ackSnippetId ?? null,
                    closingSnippetId: frame.selection?.closingSnippetId ?? null
                },
                version: frame.version || ""
            };
        }

        function sameFrameSnapshot(a, b) {
            if (!a && !b) return true;
            if (!a || !b) return false;
            const selA = a.selection || {};
            const selB = b.selection || {};
            return (a.version || "") === (b.version || "")
                && (selA.salutationSnippetId ?? null) === (selB.salutationSnippetId ?? null)
                && (selA.greetingSnippetId ?? null) === (selB.greetingSnippetId ?? null)
                && (selA.ackSnippetId ?? null) === (selB.ackSnippetId ?? null)
                && (selA.closingSnippetId ?? null) === (selB.closingSnippetId ?? null);
        }

        // I-6: preview/state/assemble read only the resolved versions.
        function currentResolvedVersions() {
            const versions = {};
            state.requests.forEach((request) => {
                if (request.resolvedVersionId) versions[request.requestKey] = request.resolvedVersionId;
            });
            return versions;
        }

        function factRuleById(factId) {
            const numeric = Number(factId);
            return state.rules.find((rule) => Number(rule.ruleId ?? rule.id) === numeric) || null;
        }

        // I-2: per-request ownership derived from the canonical matrix; a fact
        // may belong to at most one request (server keeps the final word).
        function factOwnerById() {
            const owners = new Map();
            state.requests.forEach((request) => {
                (request.factRuleIds || []).forEach((factId) => {
                    const numeric = Number(factId);
                    if (!owners.has(numeric)) owners.set(numeric, request);
                });
            });
            return owners;
        }

        function availableFactsFor(request) {
            const owners = factOwnerById();
            return state.rules.filter((rule) => {
                const id = Number(rule.ruleId ?? rule.id);
                const owner = owners.get(id);
                return !owner || owner.requestKey === request.requestKey;
            });
        }

        function applyBootstrap(data, seq) {
            if (!isLive(seq)) return;
            if (!data || data.source?.sourceType !== source.sourceType || Number(data.source?.sourceId) !== source.sourceId) {
                throw new Error("TRUST_REPLY_SOURCE_MISMATCH");
            }
            state.sourceVersion = data.sourceVersion;
            state.evidenceSetVersion = data.evidenceSetVersion;
            state.contextVersion = data.contextVersion || "";
            state.rules = data.rulesByCategory || [];
            state.availableModels = data.availableModels?.length ? [...data.availableModels] : [data.defaultModel || "DEEPSEEK_V4_FLASH"];
            state.selectedModel = state.availableModels.includes(data.defaultModel) ? data.defaultModel : state.availableModels[0];
            state.llmEnabled = data.llmEnabled !== false;
            state.requests = requestFromCoverage(data.requestCoverage, data.evidenceSetVersion);
            state.frameOptions = Array.isArray(data.frameOptions) ? data.frameOptions : [];
            state.frameSnapshot = snapshotFrame(data.frameSnapshot);
            // Fail closed when the server canonical matrix disagrees with the
            // per-request coverage instead of silently re-deriving a flat pool.
            if (Array.isArray(data.requestFactSelections) && data.requestFactSelections.length > 0) {
                const selectionsByKey = new Map(data.requestFactSelections.map((selection) => [selection.requestKey, selection.factRuleIds || []]));
                const inconsistent = state.requests.find((request) => {
                    const canonical = selectionsByKey.get(request.requestKey);
                    if (!canonical) return false;
                    return JSON.stringify([...canonical]) !== JSON.stringify([...(request.factRuleIds || [])]);
                });
                if (inconsistent) {
                    throw new Error("TRUST_REPLY_FACT_SELECTION_INVALID");
                }
            }
            state.generation.pending = false;
            state.sequenceCancelled = false;
            applySavedState(data.savedState || null);
            render();
            state.requests
                .filter((request) => request.coverage === "UNSUPPORTED")
                .forEach((request) => { void requestTranslation(request, null); });
        }

        function restoreLockedItem(locked) {
            const request = findRequest(locked.requestKey);
            if (!request) return false;
            const version = lockedToVersion(locked);
            if (!isVersionSerializable(version, request)) return false;
            request.versions = [...request.versions, version];
            request.activeVersionId = version.versionId;
            request.resolvedVersionId = version.versionId;
            request.draftHandling = version.handling;
            request.instruction = version.operatorInstruction || "";
            // V-3: machine-fill provenance is browser-local only; a
            // restored saved item is always unmarked.
            request.autoFilled = false;
            request.instructionEditedByOperator = false;
            request.expanded = false;
            request.error = null;
            request.evidenceStale = false;
            // 03b (T6/I-4): a restored lock generated under a different
            // context fingerprint is flagged for the per-item hint; it is
            // never dropped here (context changes never clear locks).
            request.contextStale = (locked.contextVersion || "") !== (state.contextVersion || "");
            return true;
        }

        function applySavedState(savedState) {
            state.savedStateVersion = savedState && Number.isInteger(savedState.stateVersion) ? savedState.stateVersion : 0;
            if (!savedState) {
                setStatus("", "READY");
                return;
            }
            if (savedState.status === "RESTORED" || savedState.status === "FRAME_STALE" || savedState.status === "PARTIALLY_RESTORED") {
                let restoredCount = 0;
                (Array.isArray(savedState.lockedItems) ? savedState.lockedItems : []).forEach((locked) => {
                    if (restoreLockedItem(locked)) restoredCount += 1;
                });
                if (savedState.status === "FRAME_STALE") {
                    setStatus(`FRAME_STALE：框架配置已变化，已保留 ${restoredCount} 项锁定回答，请刷新框架选项`, "FRAME_STALE");
                    state.activePage = "frame";
                } else if (savedState.status === "PARTIALLY_RESTORED") {
                    // 03a (I-4): per-request evidence drift dropped the stale
                    // items only; the kept locks are restored above.
                    const dropped = Number.isInteger(savedState.droppedItemCount) ? savedState.droppedItemCount : 0;
                    setStatus(`PARTIALLY_RESTORED：恢复了 ${restoredCount} 项锁定回答，丢弃 ${dropped} 项（事实已变化）`, "PARTIALLY_RESTORED");
                } else {
                    setStatus(`READY：已恢复 ${restoredCount} 项已锁定回答`, "READY");
                }
            } else if (savedState.status === "STALE") {
                setStatus("STALE：来源或依据已变化，旧锁定回答未恢复", "STALE");
            } else if (savedState.status === "INVALID") {
                setStatus("INVALID：旧锁定状态无效，未恢复", "INVALID");
            } else if (savedState.status === "EXPIRED") {
                setStatus("EXPIRED：旧锁定状态已过期，未恢复", "EXPIRED");
            } else {
                setStatus("", "READY");
            }
        }

        function lockedToVersion(locked) {
            return {
                versionId: locked.versionId,
                requestKey: locked.requestKey,
                handling: locked.handling,
                answerText: locked.answerText || "",
                claims: Array.isArray(locked.claims) ? locked.claims : [],
                model: locked.model || "",
                generationKind: locked.generationKind,
                evidenceSetVersion: locked.evidenceSetVersion,
                sourceVersion: locked.sourceVersion,
                operatorInstructionHash: locked.operatorInstructionHash || "",
                operatorInstruction: locked.operatorInstruction || "",
                contextVersion: locked.contextVersion || ""
            };
        }

        // 03a (I-5): when preserveVersions is true the per-request version
        // state is carried across the bootstrap so only items whose
        // per-request evidence version drifted are reset; the full-screen
        // loading skeleton is skipped so the fact change never looks like a
        // fresh workbench load.
        async function bootstrap({ preserveVersions = false } = {}) {
            const seq = ++state.bootSeq;
            if (state.destroyed) return;
            const preserved = preserveVersions ? captureVersionState() : null;
            if (!preserveVersions) {
                resetVersions();
                renderShell("正在加载工作台…");
            }
            try {
                const data = await requestJson("/api/trust-reply/workbench/bootstrap", {
                    source,
                    requestFactSelections: state.requests.length ? serializeRequestFactSelections() : null,
                    frameSnapshot: state.frameSnapshot
                });
                applyBootstrap(data, seq);
                if (preserveVersions) reconcilePreservedVersions(preserved, seq);
            } catch (error) {
                if (!isLive(seq) || isAbort(error)) return;
                state.generation.pending = false;
                setStatus(error.message || "工作台加载失败", "ERROR");
                renderShell(error.message || "工作台加载失败", true);
            }
        }

        // 03a (I-5): snapshots the version/decision state of every request so
        // a preserveVersions bootstrap can re-attach the untouched ones.
        function captureVersionState() {
            const snapshot = new Map();
            state.requests.forEach((request) => {
                snapshot.set(request.requestKey, {
                    versions: request.versions,
                    activeVersionId: request.activeVersionId,
                    resolvedVersionId: request.resolvedVersionId,
                    draftHandling: request.draftHandling,
                    instruction: request.instruction,
                    autoFilled: request.autoFilled,
                    instructionEditedByOperator: request.instructionEditedByOperator,
                    expanded: request.expanded,
                    factPickerOpen: request.factPickerOpen,
                    questionTranslation: request.questionTranslation,
                    answerTranslationsByVersionId: request.answerTranslationsByVersionId,
                    requestSeq: request.requestSeq,
                    pending: request.pending,
                    error: request.error
                });
            });
            return snapshot;
        }

        // 03a (I-5): after applyBootstrap rebuilt the request objects, keep
        // every request whose existing version still carries the fresh
        // per-request evidence version and clear only the drifted items.
        function reconcilePreservedVersions(snapshot, seq) {
            if (!isLive(seq)) return;
            state.requests.forEach((request) => {
                const old = snapshot && snapshot.get(request.requestKey);
                if (!old) return;
                const retained = old.versions.find((version) =>
                    version.versionId === (old.resolvedVersionId || old.activeVersionId)
                ) || old.versions[0];
                if (retained && retained.evidenceSetVersion === request.evidenceSetVersion) {
                    request.versions = old.versions;
                    request.activeVersionId = old.activeVersionId;
                    request.resolvedVersionId = old.resolvedVersionId;
                    request.draftHandling = old.draftHandling;
                    request.instruction = old.instruction;
                    request.autoFilled = old.autoFilled;
                    request.instructionEditedByOperator = old.instructionEditedByOperator;
                    request.expanded = old.expanded;
                    request.factPickerOpen = old.factPickerOpen;
                    request.questionTranslation = old.questionTranslation;
                    request.answerTranslationsByVersionId = old.answerTranslationsByVersionId;
                    request.requestSeq = old.requestSeq;
                    request.pending = old.pending;
                    request.error = old.error;
                    // 03b (T6/I-4): the evidence identity is still fresh, but
                    // the retained version may have been generated under a
                    // different context fingerprint — flag it, keep the lock.
                    request.contextStale = (retained.contextVersion || "") !== (state.contextVersion || "");
                } else if (old.versions.length > 0 || old.resolvedVersionId) {
                    // 03a (I-5): only this item lost its per-request evidence
                    // identity; the fresh request object already has no
                    // versions, so just mark it for the stale hint.
                    request.evidenceStale = true;
                    request.expanded = request.coverage !== "GROUNDED";
                }
            });
            // applyBootstrap already rendered the fresh (empty) requests; the
            // preserved versions were attached afterwards, so render again.
            render();
        }

        function makeGenerationPayload(requestKey, handling, instruction, generationId) {
            return {
                source,
                expectedSourceVersion: state.sourceVersion,
                // 03a (I-3): item generation is gated on THIS request's
                // per-request evidence version, not the whole-draft aggregate.
                expectedEvidenceSetVersion: findRequest(requestKey)?.evidenceSetVersion || "",
                requestFactSelections: serializeRequestFactSelections(),
                requestKey: requestKey || null,
                handling: handling || null,
                operatorInstruction: instruction || null,
                model: state.selectedModel,
                generationId,
                operation: "ADJUST_ITEM",
                llmAttemptTimeoutSeconds: timeoutSeconds(state.attemptTimeout, 10, 600),
                llmTotalTimeoutSeconds: timeoutSeconds(state.totalTimeout, 10, 7200)
            };
        }

        async function persistResolvedSnapshot() {
            const lockedItems = state.requests.map(serializeResolvedVersion).filter(Boolean);
            const response = await requestJson("/api/trust-reply/workbench/state", {
                source,
                expectedStateVersion: state.savedStateVersion,
                schemaVersion: STATE_SCHEMA_VERSION,
                sourceVersion: state.sourceVersion,
                evidenceSetVersion: state.evidenceSetVersion,
                requestFactSelections: serializeRequestFactSelections(),
                selectedModel: state.selectedModel,
                frameSnapshot: state.frameSnapshot,
                lockedItems
            }, null, "PUT");
            if (response && Number.isInteger(response.stateVersion)) {
                state.savedStateVersion = response.stateVersion;
            }
            return response;
        }

        async function deleteSavedState() {
            if (state.savedStateVersion <= 0) return true;
            try {
                await requestJson("/api/trust-reply/workbench/state", {
                    source,
                    expectedStateVersion: state.savedStateVersion,
                    schemaVersion: STATE_SCHEMA_VERSION,
                    sourceVersion: state.sourceVersion,
                    evidenceSetVersion: state.evidenceSetVersion,
                    requestFactSelections: serializeRequestFactSelections(),
                    selectedModel: state.selectedModel,
                    frameSnapshot: state.frameSnapshot,
                    lockedItems: []
                }, null, "PUT");
                state.savedStateVersion = 0;
                return true;
            } catch (_) {
                return false;
            }
        }

        // P0 (I-4b/I-6): 破坏性操作，必须二次确认；重置后走一次干净的 bootstrap，
        // 绝不复用失败前的 state.requests——那份内存矩阵正是把 bootstrap 打挂的输入。
        async function resetWorkbenchState() {
            if (state.readOnly) return;
            if (typeof global.confirm === "function" &&
                !global.confirm("重置会清空本封信已锁定的全部回答，且不可撤销。确认继续？")) {
                return;
            }
            try {
                await requestJson("/api/trust-reply/workbench/state/reset", { source });
                state.savedStateVersion = 0;
                state.requests = [];
                await bootstrap();
            } catch (error) {
                setStatus(errorText(error) || "重置失败，请刷新页面后重试", "ERROR");
                renderShell(errorText(error) || "重置失败，请刷新页面后重试", true);
            }
        }

        async function generateMissingGrounded(allowlist, seq) {
            return runItemSequence(allowlist, seq, {
                labelPrefix: "正在生成有据回答",
                doneMessage: "有据回答生成完成",
                handlingFor: () => "ANSWER_WITH_EVIDENCE"
            });
        }

        // Shared sequential per-item generation skeleton (cancel / stale /
        // failure branches). generateMissingGrounded and the one-click
        // autoRun() both drive this loop; the caller supplies the progress
        // wording and the per-item handling override, and mutates each request
        // (draftHandling / instruction) before the loop if the payload builder
        // must see it.
        async function runItemSequence(keys, seq, options) {
            const handlingFor = options.handlingFor || ((request) => request.draftHandling);
            const labelPrefix = options.labelPrefix || "正在生成";
            const doneMessage = options.doneMessage || "生成完成";
            // V-1: one-click runs pass persistEach:false so generated locks stay
            // in memory until the whole run succeeds; the default keeps the
            // established per-item durable save for ordinary paths.
            const persistEach = options.persistEach !== false;
            // 03b (T6/I-4): the context-stale rerun passes skipResolved:false
            // so already-locked items are regenerated instead of skipped.
            const skipResolved = options.skipResolved !== false;
            if (!keys.length || state.generation.pending || !state.sourceVersion) return false;
            const frozenKeys = [...keys];
            const total = frozenKeys.length;
            state.sequenceCancelled = false;
            state.generation = { pending: true, stage: "GENERATING", message: `${labelPrefix} 1/${total}`, generationId: null, controller: null };
            render();
            let index = 0;
            for (const requestKey of frozenKeys) {
                if (!isLive(seq) || state.sequenceCancelled) return false;
                const request = findRequest(requestKey);
                if (!request) return false;
                if (skipResolved && request.resolvedVersionId) continue;
                index += 1;
                state.generation.stage = "GENERATING";
                state.generation.message = `${labelPrefix} ${index}/${total}`;
                state.generation.generationId = makeId();
                state.generation.controller = null;
                render();
                const outcome = await requestItemVersion(request, seq, state.generation.generationId, "full", handlingFor(request));
                if (!isLive(seq) || state.sequenceCancelled) return false;
                if (outcome && outcome.stale) {
                    state.generation.pending = false;
                    state.generation.controller = null;
                    render();
                    return false;
                }
                if (outcome && outcome.cancelled) {
                    state.sequenceCancelled = true;
                    state.generation.pending = false;
                    state.generation.controller = null;
                    state.generation.stage = "CANCELLED";
                    state.generation.message = "已取消生成，可重试";
                    request.expanded = true;
                    render();
                    return false;
                }
                if (!outcome || !outcome.version) {
                    state.generation.pending = false;
                    state.generation.controller = null;
                    state.generation.stage = "ERROR";
                    state.generation.message = request.error || "生成失败，可重试";
                    request.expanded = true;
                    render();
                    return false;
                }
                request.resolvedVersionId = outcome.version.versionId;
                request.expanded = false;
                request.error = null;
                if (persistEach) {
                    state.stateSavePending = true;
                    render();
                    try {
                        await persistResolvedSnapshot();
                    } catch (error) {
                        if (isFrameStaleError(error)) {
                            state.stateSavePending = false;
                            handleFrameStale(seq, error.message || "框架配置已变化，请重新选择后整合");
                            return false;
                        }
                        if (isStaleError(error)) {
                            state.stateSavePending = false;
                            handleStaleGeneration(seq, error.message || "来源或事实已变化，请确认后刷新工作台");
                            return false;
                        }
                        if (!isLive(seq)) return false;
                        request.resolvedVersionId = null;
                        request.expanded = true;
                        request.error = error.message || "保存失败，请重试";
                        state.stateSavePending = false;
                        state.generation.pending = false;
                        state.generation.controller = null;
                        state.generation.stage = "ERROR";
                        state.generation.message = request.error;
                        render();
                        return false;
                    }
                    state.stateSavePending = false;
                }
                state.generation.controller = null;
                if (!isLive(seq)) return false;
                if (state.sequenceCancelled) {
                    state.generation.pending = false;
                    state.generation.stage = "CANCELLED";
                    state.generation.message = "已取消生成，可重试";
                    render();
                    return false;
                }
                render();
            }
            state.generation.pending = false;
            state.generation.controller = null;
            state.generation.stage = "READY";
            state.generation.message = doneMessage;
            render();
            return true;
        }

        async function requestItemVersion(request, seq, generationId, controllerKey, handlingOverride) {
            const requestSeq = ++request.requestSeq;
            request.pending = true;
            request.error = null;
            invalidateAssembly();
            render();
            let result = null;
            let cancelled = false;
            try {
                await requestSse("/api/trust-reply/workbench/generations/stream", makeGenerationPayload(request.requestKey, handlingOverride || request.draftHandling, request.instruction, generationId), controllerKey || request.requestKey, (event, data) => {
                    if (!isLive(seq) || request.requestSeq !== requestSeq) return;
                    if (event === "progress" || event === "ready" || event === "heartbeat") {
                        setStatus((data.progress || data).message || (data.progress || data).phase || event, (data.progress || data).phase || event.toUpperCase());
                        renderStatusOnly();
                    } else if (event === "result") {
                        result = data;
                    } else if (event === "cancelled") {
                        cancelled = true;
                    } else if (event === "error") {
                        throw errorFromStream(data, "单项生成失败");
                    }
                });
                if (!isLive(seq) || request.requestSeq !== requestSeq) return null;
                if (cancelled) {
                    request.pending = false;
                    return { cancelled: true };
                }
                if (!hasGenerationIdentity(result)) {
                    return failItemVersion(request, seq, "来源或事实已变化，请确认后刷新工作台");
                }
                const candidates = [result.version, ...(Array.isArray(result.itemVersions) ? result.itemVersions : [])].filter(Boolean);
                const matching = candidates.filter((item) => item.requestKey === request.requestKey && hasVersionIdentity(item, request.requestKey));
                if (candidates.some((item) => item.requestKey === request.requestKey && !hasVersionIdentity(item, request.requestKey))) {
                    return failItemVersion(request, seq, "生成版本身份无效，请确认刷新工作台");
                }
                const version = matching[0];
                if (!version) throw new Error("单项生成未返回版本");
                if (matching.some((item) => item.versionId !== version.versionId)) {
                    return failItemVersion(request, seq, "完整生成返回重复版本");
                }
                if (!isVersionSerializable(version, request)) {
                    return failItemVersion(request, seq, "生成版本处理方式无效");
                }
                request.versions = [...request.versions, version];
                request.activeVersionId = version.versionId;
                // 03a (S-1): a fresh version carries the current per-request
                // evidence, so the stale hint goes away.
                request.evidenceStale = false;
                // 03b (T6/I-4): a fresh version also carries the current
                // context fingerprint, so the context-stale hint goes away.
                request.contextStale = false;
                if (version.handling === "OMIT") {
                    request.resolvedVersionId = version.versionId;
                    request.expanded = false;
                }
                request.pending = false;
                render();
                return { version };
            } catch (error) {
                if (isFrameStaleError(error)) {
                    request.pending = false;
                    handleFrameStale(seq, error.message || "框架配置已变化，请重新选择后整合");
                    return { stale: true };
                }
                if (isStaleError(error)) {
                    handleStaleGeneration(seq, error.message || "来源或事实已变化，请确认后刷新工作台");
                    return { stale: true };
                }
                if (!isLive(seq) || request.requestSeq !== requestSeq) return null;
                if (isAbort(error)) {
                    request.pending = false;
                    return { cancelled: true };
                }
                request.pending = false;
                request.error = errorText(error, "单项生成失败，可重试");
                render();
                return null;
            }
        }

        function failItemVersion(request, seq, message) {
            if (!isLive(seq)) return null;
            request.pending = false;
            request.error = message;
            render();
            return { failed: true };
        }

        async function adjustItem(request) {
            if (state.generation.pending) {
                request.error = "正在生成其他回复，请稍后";
                request.expanded = true;
                render();
                return;
            }
            if (request.pending) return;
            if (request.draftHandling === "ANSWER_FROM_OPERATOR_INPUT" && !request.instruction.trim()) {
                request.error = "请先填写回答说明";
                request.expanded = true;
                render();
                return;
            }
            const sourceSeq = state.bootSeq;
            const generationId = makeId();
            const outcome = await requestItemVersion(request, sourceSeq, generationId, request.requestKey);
            if (!outcome || !outcome.version) return null;
            return outcome.version;
        }

        // 03b (T6/I-4): one-click rerun of every context-stale item. All
        // affected items run through the shared sequential generation loop
        // (reused, not a new pipeline); untouched items are never regenerated,
        // resetVersions()/handleStaleGeneration are never invoked, and no
        // re-bootstrap happens. After the whole run succeeds, one complete
        // snapshot persists the fresh context fingerprints durably.
        async function regenerateContextStale() {
            if (state.generation.pending || state.stateSavePending || state.frameSavePending) return;
            const seq = state.bootSeq;
            const keys = state.requests
                .filter((request) => request.contextStale === true)
                .map((request) => request.requestKey);
            if (!keys.length || !state.sourceVersion) return;
            const completed = await runItemSequence(keys, seq, {
                labelPrefix: "正在重新生成受影响条目",
                doneMessage: "受影响条目已重新生成",
                skipResolved: false,
                persistEach: false
            });
            if (!completed || !isLive(seq)) return;
            try {
                await persistResolvedSnapshot();
            } catch (error) {
                if (isFrameStaleError(error)) {
                    handleFrameStale(seq, error.message || "框架配置已变化，请重新选择后整合");
                    return;
                }
                if (isStaleError(error)) {
                    handleStaleGeneration(seq, error.message || "来源或事实已变化，请确认后刷新工作台");
                    return;
                }
                if (!isLive(seq)) return;
                setStatus(error.message || "保存失败，请重试", "ERROR");
                render();
                return;
            }
        }

        function isVersionSerializable(version, request) {
            return !!version
                && !!request
                && version.requestKey === request.requestKey
                && version.versionId
                && version.handling
                && version.generationKind
                && version.sourceVersion
                && version.evidenceSetVersion
                && Array.isArray(version.claims)
                && request.availableHandlings.includes(version.handling);
        }

        function isAdoptableActiveVersion(request) {
            return isVersionSerializable(activeVersion(request), request);
        }

        function computeReadiness() {
            const total = state.requests.length;
            const resolvedCount = state.requests.filter((request) => !!request.resolvedVersionId).length;
            const missingGroundedKeys = [];
            const adoptableGrounded = [];
            const unresolvedManualKeys = [];
            state.requests.forEach((request) => {
                if (request.coverage === "GROUNDED") {
                    if (request.resolvedVersionId) return;
                    if (isAdoptableActiveVersion(request)) adoptableGrounded.push(request);
                    else missingGroundedKeys.push(request.requestKey);
                } else if (request.coverage === "PARTIAL" || request.coverage === "UNSUPPORTED") {
                    if (!serializeResolvedVersion(request)) unresolvedManualKeys.push(request.requestKey);
                }
            });
            const pendingGeneration = missingGroundedKeys.length;
            const unresolvedManual = unresolvedManualKeys.length;
            const canStartAssembly = !state.generation.pending
                && !state.stateSavePending
                && !state.frameSavePending
                && total > 0
                && unresolvedManual === 0
                && !state.requests.some((request) => request.pending);
            return {
                resolvedCount,
                total,
                pendingGeneration,
                unresolvedManual,
                missingGroundedKeys,
                adoptableGrounded,
                unresolvedManualKeys,
                autoFillableKeys: unresolvedManualKeys,
                canStartAssembly
            };
        }

        function assemblyIdentityMatches(assembly) {
            return !!assembly
                && sameSource(assembly.source)
                && assembly.sourceVersion === state.sourceVersion
                && assembly.evidenceSetVersion === state.evidenceSetVersion
                && sameFrameSnapshot(assembly.frameSnapshot, state.frameSnapshot);
        }

        function previewState() {
            if (state.assembly && assemblyIdentityMatches(state.assembly)) return "CURRENT";
            if (state.assemblyStale) return "STALE";
            return "LOCAL";
        }

        async function assemble() {
            const readiness = computeReadiness();
            if (!readiness.canStartAssembly) return;
            const seq = state.bootSeq;
            const adoptable = [...readiness.adoptableGrounded];
            adoptable.forEach((request) => {
                request.resolvedVersionId = request.activeVersionId;
                request.expanded = false;
            });
            if (adoptable.length > 0) {
                state.stateSavePending = true;
                render();
                try {
                    await persistResolvedSnapshot();
                } catch (error) {
                    if (isFrameStaleError(error)) {
                        state.stateSavePending = false;
                        handleFrameStale(seq, error.message || "框架配置已变化，请重新选择后整合");
                        return;
                    }
                    if (isStaleError(error)) {
                        state.stateSavePending = false;
                        handleStaleGeneration(seq, error.message || "来源或事实已变化，请确认后刷新工作台");
                        return;
                    }
                    if (!isLive(seq)) return;
                    adoptable.forEach((request) => {
                        request.resolvedVersionId = null;
                        request.expanded = request.coverage !== "GROUNDED";
                    });
                    state.stateSavePending = false;
                    setStatus(error.message || "保存失败，请重试", "ERROR");
                    render();
                    return;
                }
                state.stateSavePending = false;
            }
            const missingKeys = computeReadiness().missingGroundedKeys;
            if (missingKeys.length > 0) {
                const generated = await generateMissingGrounded(missingKeys, seq);
                if (!generated || !isLive(seq)) return;
            }
            const lockedItems = state.requests.map(serializeResolvedVersion);
            const invalidRequest = state.requests.find((request, index) => !lockedItems[index]);
            if (invalidRequest) {
                invalidRequest.error = "已采用版本无效，请重新生成并采用";
                invalidRequest.expanded = true;
                render();
                return;
            }
            state.generation.pending = true;
            state.generation.stage = "ASSEMBLING";
            state.generation.message = "正在请求服务端整合…";
            render();
            try {
                const response = await requestJson("/api/trust-reply/workbench/assemble", {
                    source,
                    expectedSourceVersion: state.sourceVersion,
                    // 03a (I-3): the server no longer pre-checks this
                    // whole-draft value (the :1051 gate is gone); each locked
                    // item is validated against its own per-request evidence
                    // version. The field is kept on the wire because the
                    // controller DTO (unchanged, C-4) requires it.
                    expectedEvidenceSetVersion: state.evidenceSetVersion,
                    requestFactSelections: serializeRequestFactSelections(),
                    frameSnapshot: state.frameSnapshot,
                    lockedItems
                });
                if (!isLive(seq)) return;
                if (response.sourceVersion !== state.sourceVersion || response.evidenceSetVersion !== state.evidenceSetVersion) {
                    throw new Error("TRUST_REPLY_ASSEMBLY_STALE");
                }
                if (response.frameSnapshot) {
                    state.frameSnapshot = snapshotFrame(response.frameSnapshot);
                }
                state.assembly = {
                    ...response,
                    requestFactSelections: Array.isArray(response.requestFactSelections)
                        ? response.requestFactSelections.map((selection) => ({ requestKey: selection.requestKey, factRuleIds: [...(selection.factRuleIds || [])] }))
                        : [],
                    frameSnapshot: snapshotFrame(response.frameSnapshot)
                };
                state.assemblyStale = false;
                state.generation.pending = false;
                state.generation.message = "服务端整合完成";
                state.generation.stage = "READY";
                render();
            } catch (error) {
                if (!isLive(seq) || isAbort(error)) return;
                if (isFrameStaleError(error)) {
                    state.generation.pending = false;
                    handleFrameStale(seq, error.message || "框架配置已变化，请重新选择后整合");
                    return;
                }
                state.generation.pending = false;
                state.generation.stage = "ERROR";
                state.generation.message = error.message || "整合失败，可重试";
                render();
            }
        }

        function canAssemble() {
            return computeReadiness().canStartAssembly;
        }

        // R-2: read-only reuse of the existing auto-reply preview endpoint (the
        // page-2 preview). Fetching never mutates state and never sends; any
        // failure simply leaves the verdict in the explicit pending state.
        async function fetchAutoReplyPreview() {
            try {
                const response = await global.fetch(apiUrl(state.contextPath, `/api/mail/unmatched-inbound/${Number(state.source.sourceId)}/auto-reply-preview`), {
                    method: "GET",
                    headers: { Accept: "application/json" }
                });
                if (!response.ok) return null;
                const body = await response.json();
                return body && typeof body === "object" ? body : null;
            } catch (_) {
                return null;
            }
        }

        // R-2: the displayed decision comes only from the existing preview
        // result and its hard-gate evidence — never from the assembly. Gates
        // take precedence; an authoritative QA_AUTO_REPLIED preview without
        // gates is the only eligible decision.
        function derivePreviewEvidence(preview) {
            const gates = Array.isArray(preview.wouldBeBlockedBy) ? preview.wouldBeBlockedBy : [];
            const decision = gates.length === 0 && preview.previewKind === "QA_AUTO_REPLIED"
                ? "AUTO_SEND"
                : "MANUAL_HANDOFF";
            return {
                decision,
                gates,
                previewKind: preview.previewKind || "",
                reason: preview.reason || null
            };
        }

        // I-1: one-click orchestration. Unresolved manual items (PARTIAL /
        // UNSUPPORTED) are filled by the machine first — same ADJUST_ITEM path
        // an operator would use, with the server-suggested handling and
        // instruction — then the existing assemble() orchestration runs
        // unchanged. Nothing is sent and no external mail record is written.
        async function autoRun() {
            if (state.generation.pending || state.stateSavePending || state.frameSavePending) return;
            const seq = state.bootSeq;
            const readiness = computeReadiness();
            const manualKeys = readiness.autoFillableKeys;
            if (manualKeys.length > 0) {
                manualKeys.forEach((requestKey) => {
                    const request = findRequest(requestKey);
                    if (!request) return;
                    request.draftHandling = request.availableHandlings.includes(request.recommendedHandling)
                        ? request.recommendedHandling
                        : request.availableHandlings[0] || "OMIT";
                    request.instruction = request.suggestedInstruction || "";
                    request.autoFilled = true;
                    request.instructionEditedByOperator = false;
                });
                const filled = await runItemSequence(manualKeys, seq, {
                    labelPrefix: "正在生成",
                    doneMessage: "编排生成完成",
                    handlingFor: (request) => request.draftHandling,
                    persistEach: false
                });
                if (!filled || !isLive(seq)) return;
            }
            // V-1: all-or-nothing durable persistence, wholly inside the
            // one-click path. Manual items are generated above and grounded
            // items are generated/adopted here — both without per-item durable
            // writes — so the protected assemble() orchestration performs no
            // mid-run persistence and issues only the server assembly. Exactly
            // one complete snapshot is saved after everything succeeded; any
            // failure, cancellation or stale state before that leaves nothing
            // newly persisted.
            computeReadiness().adoptableGrounded.forEach((request) => {
                request.resolvedVersionId = request.activeVersionId;
                request.expanded = false;
            });
            const missingGrounded = computeReadiness().missingGroundedKeys;
            if (missingGrounded.length > 0) {
                const generated = await runItemSequence(missingGrounded, seq, {
                    labelPrefix: "正在生成有据回答",
                    doneMessage: "有据回答生成完成",
                    handlingFor: () => "ANSWER_WITH_EVIDENCE",
                    persistEach: false
                });
                if (!generated || !isLive(seq)) return;
            }
            await assemble();
            if (!isLive(seq)) return;
            if (previewState() === "CURRENT") {
                try {
                    await persistResolvedSnapshot();
                } catch (error) {
                    if (isFrameStaleError(error)) {
                        handleFrameStale(seq, error.message || "框架配置已变化，请重新选择后整合");
                        return;
                    }
                    if (isStaleError(error)) {
                        handleStaleGeneration(seq, error.message || "来源或事实已变化，请确认后刷新工作台");
                        return;
                    }
                    if (!isLive(seq)) return;
                    setStatus(error.message || "保存失败，请重试", "ERROR");
                    render();
                    return;
                }
                if (!isLive(seq)) return;
            }
            // R-2: after a completed LIVE one-click assembly, reuse the existing
            // auto-reply preview evidence for the conclusion. Simulation and
            // unavailable previews keep the explicit pending state; assembly
            // itself never decides send clearance.
            if (previewState() === "CURRENT" && state.mode === MODES.LIVE) {
                const preview = await fetchAutoReplyPreview();
                if (!isLive(seq)) return;
                state.previewEvidence = preview ? derivePreviewEvidence(preview) : null;
                render();
            }
        }

        // I-4: reset returns to the bootstrap default state. It deletes only the
        // workbench state row for this source; QA rules, snippets, mail records
        // and ES documents are untouched. After the DELETE the component
        // re-bootstraps, which rebuilds the request list from the server.
        async function autoReset() {
            if (state.generation.pending || state.stateSavePending || state.frameSavePending) return;
            if (typeof global.confirm === "function"
                && !global.confirm("重置将清空本次编排产生的所有采用与说明，回到初始状态。QA 规则与历史记录不受影响，继续？")) {
                render();
                return;
            }
            if (state.savedStateVersion > 0) {
                try {
                    await requestJson("/api/trust-reply/workbench/state", {
                        source,
                        expectedStateVersion: state.savedStateVersion
                    }, null, "DELETE");
                    state.savedStateVersion = 0;
                } catch (error) {
                    if (!isLive() || isAbort(error)) return;
                    setStatus(error.message || "重置失败，请重试", "ERROR");
                    render();
                    return;
                }
            }
            state.requests.forEach((request) => {
                request.versions = [];
                request.activeVersionId = null;
                request.resolvedVersionId = null;
                request.instruction = "";
                request.autoFilled = false;
                request.instructionEditedByOperator = false;
                request.pending = false;
                request.error = null;
                request.expanded = request.coverage !== "GROUNDED";
                request.questionTranslation = { state: "idle", text: "" };
                request.answerTranslationsByVersionId = {};
            });
            state.assembly = null;
            state.assemblyStale = false;
            state.previewEvidence = null;
            setStatus("正在重置工作台…", "RESETTING");
            render();
            await bootstrap();
        }

        async function cancelGeneration(generationId, controller) {
            cancelController(controller);
            if (!state.destroyed && state.generation.generationId === generationId) {
                state.sequenceCancelled = true;
                state.generation.pending = false;
                state.generation.controller = null;
                state.generation.stage = "CANCELLED";
                state.generation.message = "已取消生成，可重试";
                render();
            }
            if (!generationId || state.destroyed) return;
            try {
                await requestJson(`/api/trust-reply/workbench/generations/${encodeURIComponent(generationId)}/cancel`, { source });
            } catch (_) { /* cancellation is best effort */ }
        }

        function activeVersion(request) {
            return request.versions.find((version) => version.versionId === request.activeVersionId) || null;
        }

        function resolvedVersion(request) {
            return request.versions.find((version) => version.versionId === request.resolvedVersionId) || null;
        }

        function invalidateDecision(request) {
            const hadResolved = !!request.resolvedVersionId;
            if (hadResolved) request.resolvedVersionId = null;
            const hadAssembly = invalidateAssembly();
            return hadResolved || hadAssembly;
        }

        function captureDecision(request) {
            return {
                resolvedVersionId: request.resolvedVersionId,
                activeVersionId: request.activeVersionId,
                draftHandling: request.draftHandling,
                instruction: request.instruction,
                expanded: request.expanded
            };
        }

        function restoreDecision(request, previous) {
            request.resolvedVersionId = previous.resolvedVersionId;
            request.activeVersionId = previous.activeVersionId;
            request.draftHandling = previous.draftHandling;
            request.instruction = previous.instruction;
            request.expanded = previous.expanded;
        }

        async function persistDecisionUnlock(request, previous) {
            if (!previous.resolvedVersionId || state.destroyed) return;
            state.stateSavePending = true;
            syncInstructionUi(request);
            try {
                await persistResolvedSnapshot();
            } catch (error) {
                if (!isLive()) return;
                state.stateSavePending = false;
                if (isFrameStaleError(error)) {
                    handleFrameStale(state.bootSeq, error.message || "框架配置已变化，请重新选择后整合");
                    return;
                }
                if (isStaleError(error)) {
                    handleStaleGeneration(state.bootSeq, error.message || "来源或事实已变化，请确认后刷新工作台");
                    return;
                }
                restoreDecision(request, previous);
                request.error = error.message || "保存失败，请重试";
                render();
                return;
            }
            if (!state.destroyed) {
                state.stateSavePending = false;
                syncInstructionUi(request);
            }
        }

        function serializeResolvedVersion(request) {
            const version = resolvedVersion(request);
            if (!isVersionSerializable(version, request)) return null;
            return {
                requestKey: version.requestKey,
                versionId: version.versionId,
                handling: version.handling,
                answerText: version.answerText || "",
                claims: version.claims,
                model: version.model || "",
                generationKind: version.generationKind,
                evidenceSetVersion: version.evidenceSetVersion,
                sourceVersion: version.sourceVersion,
                operatorInstructionHash: version.operatorInstructionHash || "",
                operatorInstruction: version.operatorInstruction || "",
                contextVersion: version.contextVersion || ""
            };
        }

        // I-3/03a (I-5): fact add/remove invalidates the affected item only
        // (durable state, its versions, locks, assembly) and re-bootstraps the
        // canonical matrix with preserveVersions so untouched items keep their
        // answers; frame changes only clear the assembly (onFrameChange).
        async function changeRequestFacts(request, nextFactRuleIds) {
            // 03a (T7): the confirmation only asks when THIS item actually has
            // a generated or locked answer — other items' answers survive.
            const targetHasGeneratedState = request.versions.length > 0 || !!request.resolvedVersionId;
            if (targetHasGeneratedState && typeof global.confirm === "function" && !global.confirm("该摘要的事实变化会清空本条已生成回答，其余摘要保留，继续？")) {
                render();
                return;
            }
            state.factChangePending = true;
            render();
            try {
                if (state.savedStateVersion > 0) {
                    const deleted = await deleteSavedState();
                    if (!deleted) {
                        setStatus("旧锁定状态删除失败，未切换事实，请刷新后重试", "ERROR");
                        return;
                    }
                }
                request.factRuleIds = nextFactRuleIds;
                await bootstrap({ preserveVersions: true });
            } finally {
                if (!state.destroyed) {
                    state.factChangePending = false;
                    render();
                }
            }
        }

        async function addFact(requestKey, factId) {
            const request = findRequest(requestKey);
            if (!request) return;
            const id = Number(factId);
            const blockReason = factActionReasonFor(request, state);
            if (blockReason) {
                setStatus(blockReason, "BUSY");
                render();
                return;
            }
            const owner = factOwnerById().get(id);
            if (owner && owner.requestKey !== request.requestKey) {
                setStatus("该事实已被其他摘要使用", "ERROR");
                render();
                return;
            }
            if ((request.factRuleIds || []).map(Number).includes(id)) return;
            await changeRequestFacts(request, [...(request.factRuleIds || []), id]);
        }

        async function removeFact(requestKey, factId) {
            const request = findRequest(requestKey);
            if (!request) return;
            const blockReason = factActionReasonFor(request, state);
            if (blockReason) {
                setStatus(blockReason, "BUSY");
                render();
                return;
            }
            const id = Number(factId);
            await changeRequestFacts(request, (request.factRuleIds || []).filter((item) => Number(item) !== id));
        }

        // I-1/I-2: reorder goes through the exact same canonical path as add /
        // remove (changeRequestFacts -> confirm -> saved-state delete -> reset
        // versions -> bootstrap); an unchanged order short-circuits so the
        // confirmation dialog and state deletion never fire for a no-op.
        function commitFactOrder(request, next) {
            const blockReason = factActionReasonFor(request, state);
            if (blockReason) {
                setStatus(blockReason, "BUSY");
                render();
                return;
            }
            if (JSON.stringify(next) === JSON.stringify(request.factRuleIds)) return;
            void changeRequestFacts(request, next);
        }

        async function moveFact(requestKey, factId, toIndex) {
            const request = findRequest(requestKey);
            if (!request) return;
            const blockReason = factActionReasonFor(request, state);
            if (blockReason) {
                setStatus(blockReason, "BUSY");
                render();
                return;
            }
            const next = reorderFactIds(request.factRuleIds, factId, toIndex);
            commitFactOrder(request, next);
        }

        // I-3: keyboard reorder (ArrowLeft/ArrowRight on a focused grip). The
        // re-render triggered by changeRequestFacts would drop focus, so the
        // target fact is remembered and render() restores it.
        function onGripArrowKey(grip, key) {
            if (grip.getAttribute && grip.getAttribute("aria-disabled") === "true") return;
            const chip = grip.closest && grip.closest(".trust-reply-fact-chip");
            if (!chip || !chip.dataset) return;
            const request = findRequest(chip.dataset.requestKey);
            if (!request) return;
            const ids = request.factRuleIds || [];
            const currentIndex = ids.map(Number).indexOf(Number(chip.dataset.factId));
            if (currentIndex < 0) return;
            const toIndex = key === "ArrowLeft" ? currentIndex - 1 : currentIndex + 1;
            state.pendingFocusFactId = Number(chip.dataset.factId);
            void moveFact(chip.dataset.requestKey, chip.dataset.factId, toIndex);
        }

        // B-3: delegated drag handling on the chip list. Drop marks use the
        // chip's horizontal midline; box-shadow indicators keep chip size
        // stable so flex-wrap does not reflow while hovering.
        function clearDropMarks(list) {
            if (!list || typeof list.querySelectorAll !== "function") return;
            list.querySelectorAll('[data-dragging="true"], [data-drop-before="true"], [data-drop-after="true"]').forEach((element) => {
                delete element.dataset.dragging;
                delete element.dataset.dropBefore;
                delete element.dataset.dropAfter;
            });
        }

        function onDragStart(event) {
            const grip = event.target && event.target.closest && event.target.closest('[data-role="fact-grip"]');
            if (!grip || !grip.dataset || grip.dataset.role !== "fact-grip" || !host.contains(grip)) return;
            if (grip.getAttribute && grip.getAttribute("aria-disabled") === "true") return;
            const chip = grip.closest && grip.closest(".trust-reply-fact-chip");
            if (!chip || !chip.dataset) return;
            if (event.dataTransfer) {
                event.dataTransfer.setData("text/plain", String(chip.dataset.factId));
                event.dataTransfer.effectAllowed = "move";
            }
            chip.dataset.dragging = "true";
        }

        function onDragOver(event) {
            const list = event.target && event.target.closest && event.target.closest('[data-role="fact-chip-list"]');
            if (!list || !host.contains(list)) return;
            event.preventDefault && event.preventDefault();
            clearDropMarks(list);
            const chip = event.target.closest && event.target.closest(".trust-reply-fact-chip");
            if (!chip || !chip.dataset || typeof chip.getBoundingClientRect !== "function" || typeof event.clientX !== "number") return;
            const rect = chip.getBoundingClientRect();
            if (rect.width <= 0) return;
            if (event.clientX < rect.left + rect.width / 2) {
                chip.dataset.dropBefore = "true";
                delete chip.dataset.dropAfter;
            } else {
                chip.dataset.dropAfter = "true";
                delete chip.dataset.dropBefore;
            }
        }

        function onDrop(event) {
            const list = event.target && event.target.closest && event.target.closest('[data-role="fact-chip-list"]');
            if (!list || !host.contains(list)) return;
            // IP-3: a drop released over the remove button must reorder, never
            // fall through to the button's click handler.
            event.preventDefault && event.preventDefault();
            event.stopPropagation && event.stopPropagation();
            const chip = event.target.closest && event.target.closest(".trust-reply-fact-chip");
            const before = chip && chip.dataset && chip.dataset.dropBefore === "true";
            clearDropMarks(list);
            const factId = event.dataTransfer && event.dataTransfer.getData ? event.dataTransfer.getData("text/plain") : null;
            if (!chip || !chip.dataset || factId == null) return;
            const request = findRequest(chip.dataset.requestKey);
            if (!request) return;
            const next = resolveFactDrop(request.factRuleIds, factId, chip.dataset.factId, before);
            commitFactOrder(request, next);
        }

        function onDragEnd(event) {
            const list = event.target && event.target.closest && event.target.closest('[data-role="fact-chip-list"]');
            if (!list || !host.contains(list)) return;
            clearDropMarks(list);
        }

        function toggleFactPicker(requestKey) {
            const request = findRequest(requestKey);
            if (!request) return;
            request.factPickerOpen = !request.factPickerOpen;
            render();
        }

        function tabId(page) {
            return `${state.instanceId}-tab-${page}`;
        }

        function panelId(page) {
            return `${state.instanceId}-panel-${page}`;
        }

        // I-1/I-7: switching pages only toggles DOM visibility; business state
        // (requests, matrix, frame, versions, locks, assembly) is shared.
        function setActivePage(page, focusTarget) {
            if (page !== "facts" && page !== "frame") return;
            state.activePage = page;
            render();
            if (!focusTarget || state.destroyed) return;
            // I-1: state.instanceId is a UUID v4 — 62.5% of mounts start with a
            // digit, and a CSS identifier may not start with a digit, so
            // `#${tabId(page)}` throws SyntaxError. Query by the stable
            // role/data attributes instead; the id attributes stay on the
            // elements for aria-controls / aria-labelledby (I-2).
            const selector = focusTarget === "tab"
                ? `[role="tab"][data-page="${page}"]`
                : focusTarget === "panel"
                    ? `[data-page-panel="${page}"]`
                    : null;
            if (!selector) return;
            const element = host.querySelector ? host.querySelector(selector) : null;
            if (element && typeof element.focus === "function") element.focus();
        }

        function onKeydown(event) {
            const key = event.key || (event.target && event.target.key);
            if (key === "ArrowLeft" || key === "ArrowRight") {
                const grip = event.target && event.target.closest && event.target.closest('[data-role="fact-grip"]');
                if (grip && grip.dataset && grip.dataset.role === "fact-grip" && host.contains(grip)) {
                    event.preventDefault && event.preventDefault();
                    onGripArrowKey(grip, key);
                    return;
                }
            }
            if (!["ArrowLeft", "ArrowRight", "Home", "End"].includes(key)) return;
            const tab = event.target && event.target.closest && event.target.closest('[role="tab"]');
            if (!tab || !host.contains(tab)) return;
            event.preventDefault && event.preventDefault();
            const tabs = host.querySelectorAll ? [...host.querySelectorAll('[role="tab"]')] : [];
            const index = tabs.indexOf(tab);
            if (index < 0) return;
            const next = key === "ArrowRight"
                ? Math.min(tabs.length - 1, index + 1)
                : key === "ArrowLeft"
                    ? Math.max(0, index - 1)
                    : key === "Home"
                        ? 0
                        : tabs.length - 1;
            const page = tabs[next] && tabs[next].dataset && tabs[next].dataset.page;
            if (page) setActivePage(page, "tab");
        }

        function renderPageTabs() {
            const pages = [
                { key: "facts", step: "1", label: "摘要与事实" },
                { key: "frame", step: "2", label: "回复框架与整合" }
            ];
            return pages.map((page) => {
                const selected = state.activePage === page.key;
                return `<button type="button" class="trust-reply-page-tab" role="tab" data-action="set-page" data-page="${page.key}" id="${tabId(page.key)}" aria-controls="${panelId(page.key)}" aria-selected="${selected ? "true" : "false"}" tabindex="${selected ? "0" : "-1"}"><span class="trust-reply-page-step">${page.step}</span><span>${escapeText(page.label)}</span></button>`;
            }).join("");
        }

        function renderPageActions(page) {
            if (page === "facts") {
                return `<button type="button" class="button primary" data-action="next-page" data-page="frame">下一页：回复框架与整合</button>`;
            }
            return `<button type="button" class="button secondary" data-action="prev-page" data-page="facts">上一页：摘要与事实</button>`;
        }

        // I-2: per-card fact chips and picker. Owners, pending saves and server
        // P1 (S-1): maps dropped binding ids to display names for the muted
        // hint. Reuses the chips' state.rules lookup (factRuleById) and the
        // same `事实 <id>` fallback — never a second id-lookup path.
        function droppedFactLabels(request) {
            return (request.droppedFactRuleIds || []).map((factId) => {
                const id = Number(factId);
                const rule = factRuleById(id);
                return rule ? rule.displayName || `事实 ${id}` : `事实 ${id}`;
            }).join("、");
        }

        // re-validation keep the matrix canonical; disabled states are UX only.
        function renderFactSection(request) {
            const factActionReason = factActionReasonFor(request, state);
            const factActionsDisabled = !!factActionReason;
            const owners = factOwnerById();
            // S-3: hint id derived from the request key (unique per summary,
            // instance-scoped so multiple workbenches on one page never clash).
            const gripHintId = `${state.instanceId}-fact-grip-hint-${request.requestKey}`;
            const chips = (request.factRuleIds || []).map((factId) => {
                const id = Number(factId);
                const rule = factRuleById(id);
                const label = rule ? rule.displayName || `事实 ${id}` : `事实 ${id}`;
                const body = rule && rule.answerBody ? String(rule.answerBody) : "";
                const title = body ? ` title="${escapeText(body)}"` : "";
                // S-1: draggable lives on the grip only (not the chip), so the
                // remove button never participates in the drag (IP-3).
                const gripDraggable = factActionsDisabled ? "" : ` draggable="true"`;
                const gripDisabled = factActionsDisabled ? ` aria-disabled="true"` : "";
                return `<span class="trust-reply-fact-chip" data-fact-id="${id}" data-request-key="${escapeText(request.requestKey)}"${title}><span class="trust-reply-fact-grip" data-role="fact-grip"${gripDraggable} tabindex="0" role="button" aria-label="拖动或用左右方向键调整「${escapeText(label)}」的顺序" aria-describedby="${gripHintId}"${gripDisabled}>⋮⋮</span><span>${escapeText(label)}</span><button type="button" data-action="remove-fact" data-request-key="${escapeText(request.requestKey)}" data-fact-id="${id}" aria-label="移除事实 ${escapeText(label)}"${factActionsDisabled ? " disabled" : ""}>×</button></span>`;
            }).join("");
            const pickerOptions = state.rules.map((rule) => {
                const id = Number(rule.ruleId ?? rule.id);
                const owner = owners.get(id);
                const selected = (request.factRuleIds || []).map(Number).includes(id);
                const used = !!owner && owner.requestKey !== request.requestKey;
                let optionState = "available";
                let label = "可添加";
                let disabled = false;
                if (selected) {
                    optionState = "selected";
                    label = "已选择";
                    disabled = true;
                } else if (used) {
                    optionState = "used";
                    label = `已用于摘要 ${Number(owner.index) + 1}`;
                    disabled = true;
                } else if (factActionsDisabled) {
                    optionState = "pending";
                    label = "保存中";
                    disabled = true;
                }
                const searchText = `${rule.displayName || ""} ${rule.answerBody || ""}`.trim().toLowerCase();
                return `<button type="button" class="trust-reply-fact-picker-option" data-action="add-fact" data-request-key="${escapeText(request.requestKey)}" data-fact-id="${id}" data-state="${optionState}" data-search="${escapeText(searchText)}"${disabled ? " disabled" : ""}><span class="trust-reply-fact-picker-main"><strong>${escapeText(rule.displayName || `事实 ${id}`)}</strong><span>${escapeText(rule.answerBody || "")}</span></span><span class="trust-reply-fact-state" data-state="${optionState}">${escapeText(label)}</span></button>`;
            }).join("");
            const factCount = (request.factRuleIds || []).length;
            const pickerSearch = pickerOptions
                ? `<div class="trust-reply-fact-picker-search"><input type="text" data-role="fact-search" data-request-key="${escapeText(request.requestKey)}" placeholder="搜索事实标题 / 正文…" aria-label="搜索事实"${factActionsDisabled ? " disabled" : ""}></div>`
                : "";
            // 03a (S-1): only when this item's per-request evidence drifted is
            // the verbatim stale hint appended after the fact section; no new
            // class, no inline style, no output when the condition is false.
            // 03b (S-1): the verbatim context-stale hint is appended beside it
            // (never replacing it) when the item's locked version was
            // generated under a different context fingerprint.
            const staleMarkup = (request.evidenceStale === true
                ? `<span class="muted" data-role="item-evidence-stale">事实已变化，本条回答需重新生成</span>`
                : "") + (request.contextStale === true
                ? `<span class="muted" data-role="item-context-stale">本条在旧训练知识/对话历史下生成</span>`
                : "");
            // P1 (S-1): only when this item's explicit bindings were dropped by
            // the server is the verbatim muted hint appended after the stale
            // hints; no new class, no inline style, no output when the
            // condition is false.
            const droppedMarkup = (Array.isArray(request.droppedFactRuleIds) && request.droppedFactRuleIds.length > 0)
                ? `<span class="muted" data-role="item-facts-dropped">以下事实已绑定但不会作为本条回答的依据：${escapeText(droppedFactLabels(request))}。该问题未识别出可支持的意图，绑定会保留，但 AI 不会引用它们的正文。</span>`
                : "";
            const factActionStatus = factActionReason
                ? `<span class="trust-reply-fact-action-status" data-role="fact-action-status">${escapeText(factActionReason)}</span>`
                : "";
            return `<div class="trust-reply-fact-section" data-role="fact-section" data-request-key="${escapeText(request.requestKey)}"><div class="trust-reply-fact-head"><strong>对应事实</strong><span class="trust-reply-fact-count">${factCount}</span><span class="trust-reply-fact-grip-hint" id="${gripHintId}">拖动 ⋮⋮ 或用 ← → 调整顺序</span>${factActionStatus}<button type="button" class="button small secondary" data-action="toggle-fact-picker" data-request-key="${escapeText(request.requestKey)}" aria-expanded="${request.factPickerOpen ? "true" : "false"}"${factActionsDisabled ? ` disabled title="${escapeText(factActionReason)}"` : ""}>${request.factPickerOpen ? "收起事实选择" : "+ 添加事实"}</button></div><div class="trust-reply-fact-chip-list" data-role="fact-chip-list">${chips || `<span class="muted">未绑定事实</span>`}</div><div class="trust-reply-fact-picker" data-role="fact-picker" data-request-key="${escapeText(request.requestKey)}"${request.factPickerOpen ? "" : " hidden"}>${pickerSearch}${pickerOptions || `<span class="muted">暂无可添加事实</span>`}</div></div>${staleMarkup}${droppedMarkup}`;
        }

        function renderFrameSelects() {
            const selection = state.frameSnapshot?.selection || {};
            return FRAME_SLOTS.map((slot) => {
                const options = state.frameOptions
                    .filter((option) => option.snippetType === slot.snippetType)
                    .sort((a, b) => (Number(a.displayOrder) - Number(b.displayOrder)) || (Number(a.id) - Number(b.id)))
                    .map((option) => `<option value="${escapeText(option.id)}"${Number(selection[slot.key]) === Number(option.id) ? " selected" : ""}>${escapeText(option.content)}</option>`)
                    .join("");
                return `<label class="trust-reply-field">${escapeText(slot.label)}<select data-role="frame-select" data-frame-slot="${slot.key}"${state.frameSavePending ? " disabled" : ""}><option value="">不使用</option>${options}</select></label>`;
            }).join("");
        }

        // I-3: frame-only invalidation. Locked versions survive; the assembly is
        // dropped; with locks present the new frame is persisted immediately.
        function onFrameChange(slotKey, value) {
            const previous = snapshotFrame(state.frameSnapshot);
            const selection = { ...(state.frameSnapshot?.selection || {}) };
            selection[slotKey] = value ? Number(value) : null;
            state.frameSnapshot = { selection, version: "" };
            const hadAssembly = invalidateAssembly();
            if (hadAssembly) state.assemblyStale = true;
            const hasLocks = state.requests.some((request) => !!request.resolvedVersionId);
            if (!hasLocks) {
                state.frameSavePending = false;
                render();
                return;
            }
            state.frameSavePending = true;
            render();
            void persistResolvedSnapshot().then((response) => {
                if (state.destroyed) return;
                state.frameSavePending = false;
                if (response && response.frameSnapshot) state.frameSnapshot = snapshotFrame(response.frameSnapshot);
                setStatus("回复框架已保存", "READY");
                render();
            }).catch((error) => {
                if (state.destroyed) return;
                state.frameSavePending = false;
                state.frameSnapshot = previous;
                if (isFrameStaleError(error)) {
                    handleFrameStale(state.bootSeq, error.message || "框架配置已变化，请重新选择后整合");
                    return;
                }
                setStatus(error.message || "框架保存失败，请重试", "ERROR");
                render();
            });
        }

        function renderPreviewState() {
            const stateKey = previewState();
            return `<span class="trust-reply-preview-state" data-state="${stateKey}">${escapeText(PREVIEW_STATE_LABELS[stateKey])}</span>`;
        }

        // I-4: display-only preview derived from server frame option content and
        // resolved answers. Never written into state.assembly or the adopt path.
        function renderFrameLocalPreview() {
            const selection = state.frameSnapshot?.selection || {};
            const frameParts = FRAME_SLOTS.map((slot) => {
                const id = selection[slot.key];
                if (id == null) return null;
                const option = state.frameOptions.find((item) => Number(item.id) === Number(id));
                return option && String(option.content || "").trim() ? option.content : null;
            }).filter((text) => text != null);
            const resolvedAnswers = state.requests.map((request) => resolvedVersion(request)?.answerText)
                .filter((text) => text && String(text).trim());
            return [...frameParts, ...resolvedAnswers].join("\n\n");
        }

        async function complete() {
            if (!state.assembly || state.completePending) return;
            if (previewState() !== "CURRENT") return;
            state.completePending = true;
            renderStatusOnly();
            try {
                await state.onComplete(state.assembly);
            } finally {
                if (!state.destroyed) {
                    state.completePending = false;
                    render();
                }
            }
        }

        // S-2: the read-only AUTO_PREVIEW zone. The banner is static; the gate
        // list starts empty (:empty hides it) and the host adapter fills it.
        function renderReadOnlyZone() {
            if (!state.readOnly) return "";
            return `<div class="trust-reply-readonly-banner">只读预览：此处不生成、不采用、不发送</div><ul class="trust-reply-gate-list"></ul>`;
        }

        // S-5: the former single-pane .trust-reply-layout shell (layout aside +
        // item list) is retired and replaced by two .trust-reply-page panels.
        function renderShell(message, allowRecovery) {
            if (state.destroyed) return;
            const modeNote = state.mode === MODES.SIMULATION ? "模拟 · 不外发" : "正式回复";
            const recoveryZone = allowRecovery && !state.readOnly ? `<div class="trust-reply-item-actions" data-role="shell-recovery"><button type="button" class="button secondary" data-action="reset-workbench-state">重置本封信的工作台状态</button></div>` : "";
            host.innerHTML = `<details class="detail-section reply-workflow-detail trust-reply-workbench" open>
                <summary class="reply-workflow-summary"><span class="reply-workflow-icon" aria-hidden="true">⌘</span><span class="reply-workflow-title"><strong>可信回复工作台</strong><small>${modeNote}</small></span><span class="reply-workflow-status" data-role="mode-note">${modeNote}</span><span class="reply-workflow-chevron" aria-hidden="true">⌄</span></summary>
                <div class="reply-workflow-content">${renderReadOnlyZone()}<div class="trust-reply-toolbar" data-role="toolbar"><p class="trust-reply-mode-note" data-role="mode-description">${modeNote}</p></div><nav class="trust-reply-page-nav" role="tablist" aria-label="工作台页面">${renderPageTabs()}</nav><div class="ai-reply-feedback" data-role="status" role="status" aria-live="polite">${escapeText(message || "")}</div>${recoveryZone}<section class="trust-reply-page" role="tabpanel" data-page-panel="facts" id="${panelId("facts")}" aria-labelledby="${tabId("facts")}"${state.activePage === "facts" ? "" : " hidden"}><div class="trust-reply-item-list" data-role="items"></div></section><section class="trust-reply-page" role="tabpanel" data-page-panel="frame" id="${panelId("frame")}" aria-labelledby="${tabId("frame")}" hidden></section></div>
            </details>`;
        }

        function render() {
            if (state.destroyed) return;
            host.innerHTML = renderMarkup();
            // I-3: keyboard reorder re-renders via bootstrap(); hand focus back
            // to the same fact's grip (consumed once, kept until found so an
            // intermediate loading render cannot drop it).
            const focusFactId = state.pendingFocusFactId;
            if (focusFactId != null && typeof host.querySelector === "function") {
                const grip = host.querySelector(`[data-fact-id="${Number(focusFactId)}"] [data-role="fact-grip"]`);
                if (grip && typeof grip.focus === "function") {
                    state.pendingFocusFactId = null;
                    grip.focus();
                }
            }
        }

        function renderMarkup() {
            const modeNote = state.mode === MODES.SIMULATION ? "模拟 · 不外发" : "正式回复";
            const itemMarkup = state.requests.map(renderRequest).join("") || `<div class="compose-panel"><p class="muted">暂无可处理请求</p></div>`;
            return `<details class="detail-section reply-workflow-detail trust-reply-workbench" open>
                <summary class="reply-workflow-summary"><span class="reply-workflow-icon" aria-hidden="true">⌘</span><span class="reply-workflow-title"><strong>可信回复工作台</strong><small>${modeNote}</small></span><span class="reply-workflow-status" data-role="mode-note">${modeNote}</span><span class="reply-workflow-chevron" aria-hidden="true">⌄</span></summary>
                <div class="reply-workflow-content">${renderReadOnlyZone()}<div class="trust-reply-toolbar" data-role="toolbar">${renderToolbar()}</div><nav class="trust-reply-page-nav" role="tablist" aria-label="工作台页面">${renderPageTabs()}</nav><div class="ai-reply-feedback" data-role="status" role="status" aria-live="polite">${renderStatus()}</div><section class="trust-reply-page" role="tabpanel" data-page-panel="facts" id="${panelId("facts")}" aria-labelledby="${tabId("facts")}"${state.activePage === "facts" ? "" : " hidden"}><div class="trust-reply-page-head"><h3>摘要与事实</h3><small>按原邮件顺序展示摘要卡片，每张卡片绑定对应事实；可添加或删除事实。</small></div><div class="trust-reply-item-list" data-role="items">${itemMarkup}</div><div class="trust-reply-page-actions">${renderPageActions("facts")}</div></section><section class="trust-reply-page" role="tabpanel" data-page-panel="frame" id="${panelId("frame")}" aria-labelledby="${tabId("frame")}"${state.activePage === "frame" ? "" : " hidden"}><div class="trust-reply-page-head"><h3>回复框架与整合</h3><small>选择尊语、开场白、致谢语与结束语；只有服务端整合完成的结果才能完成本页。</small></div><div class="trust-reply-frame-panel compose-panel"><div class="trust-reply-frame-grid">${renderFrameSelects()}</div><div class="trust-reply-frame-preview">${renderPreviewState()}<div class="trust-reply-summary" data-role="summary">${renderSummary()}</div></div></div><div class="trust-reply-page-actions">${renderPageActions("frame")}</div></section></div>
            </details>`;
        }

        function renderToolbar() {
            const modeNote = state.mode === MODES.SIMULATION ? "模拟 · 不外发" : "正式回复";
            const cancelButton = state.generation.pending
                ? `<button type="button" class="button danger" data-action="cancel-generation">取消生成</button>`
                : "";
            const modelOptions = state.availableModels.map((model) => `<option value="${escapeText(model)}"${model === state.selectedModel ? " selected" : ""}>${escapeText(MODEL_LABELS[model] || model)}</option>`).join("");
            // S-1: the one-click orchestration bar (T4-3: never rendered on the
            // read-only AUTO_PREVIEW host).
            const autoRunBar = state.readOnly ? "" : `<div class="trust-reply-autorun"><button type="button" class="button primary" data-action="auto-run">一键预判</button><button type="button" class="button secondary" data-action="auto-reset">重置</button><span class="trust-reply-autorun-hint">有据项自动生成，无据项由系统代填回答说明；汇总后仍可逐项调整。不发送、不写外发记录。</span></div>`;
            return `<p class="trust-reply-mode-note" data-role="mode-description">${modeNote}</p>${autoRunBar}<div class="ai-reply-model-row ai-reply-generation-controls"><label>生成模型<select data-role="model" class="ai-reply-model-select"${state.llmEnabled ? "" : " disabled"}>${modelOptions}</select></label><label>单次 TTL<select data-role="attempt-timeout" class="ai-reply-model-select">${timeoutOptions(state.attemptTimeout, false)}</select></label>${customTimeout(state.attemptTimeout, "attempt")}<label>总 TTL<select data-role="total-timeout" class="ai-reply-model-select">${timeoutOptions(state.totalTimeout, true)}</select></label>${customTimeout(state.totalTimeout, "total")}${cancelButton}</div>`;
        }

        function translationEntry(request, versionId) {
            if (!versionId) return request.questionTranslation;
            if (!request.answerTranslationsByVersionId[versionId]) {
                request.answerTranslationsByVersionId[versionId] = { state: "idle", text: "" };
            }
            return request.answerTranslationsByVersionId[versionId];
        }

        async function requestTranslation(request, versionId) {
            const version = versionId ? request.versions.find((item) => item.versionId === versionId) : null;
            const text = versionId ? version?.answerText : request.requestText;
            const entry = translationEntry(request, versionId);
            if (!String(text || "").trim() || entry.state === "pending") return;
            const sourceVersion = state.sourceVersion;
            const requestKey = request.requestKey;
            const controller = new AbortController();
            entry.state = "pending";
            entry.text = "";
            state.translationControllers.add(controller);
            render();
            try {
                const response = await requestJson("/api/translate", { text }, controller);
                if (!isLive() || state.sourceVersion !== sourceVersion || request.requestKey !== requestKey
                    || (versionId && !request.versions.some((item) => item.versionId === versionId))) return;
                entry.state = response?.ok && String(response.translatedText || "").trim() ? "expanded" : "error";
                entry.text = entry.state === "expanded" ? response.translatedText : "";
            } catch (error) {
                if (!isLive() || isAbort(error) || state.sourceVersion !== sourceVersion || request.requestKey !== requestKey) return;
                entry.state = "error";
                entry.text = "";
            } finally {
                state.translationControllers.delete(controller);
                if (isLive() && state.sourceVersion === sourceVersion && request.requestKey === requestKey) render();
            }
        }

        async function toggleTranslation(request, versionId) {
            const entry = translationEntry(request, versionId);
            if (entry.state === "expanded") {
                entry.state = "collapsed";
                render();
                return;
            }
            if (entry.state === "collapsed" && entry.text) {
                entry.state = "expanded";
                render();
                return;
            }
            await requestTranslation(request, versionId);
        }

        function renderTranslation(request, versionId, entry) {
            const translation = entry || { state: "idle", text: "" };
            const action = versionId ? "translate-answer" : "translate-question";
            const buttonLabel = translation.state === "pending" ? "翻译中…"
                : translation.state === "expanded" ? "收起译文"
                    : translation.state === "error" ? "翻译失败，重试" : "🌐 翻译为中文";
            const source = versionId ? "" : `<div class="pre" data-role="question-text">${escapeText(request.requestText)}</div>`;
            const text = translation.text && translation.state === "expanded"
                ? `<div class="translation-text pre" data-role="translation-text">${escapeText(translation.text)}</div>` : "";
            return `<div class="translatable-body-block" data-role="${versionId ? "answer-translation" : "question-translation"}">${source}<button type="button" class="btn-translate" data-action="${action}" data-request-key="${escapeText(request.requestKey)}"${versionId ? ` data-version-id="${escapeText(versionId)}"` : ""}${translation.state === "pending" ? " disabled" : ""}>${buttonLabel}</button>${text}</div>`;
        }

        function requestAction(request) {
            const resolved = resolvedVersion(request);
            const version = activeVersion(request);
            const isOmit = request.draftHandling === "OMIT";
            const needsOperatorInstruction = request.draftHandling === "ANSWER_FROM_OPERATOR_INPUT";
            const generateDisabled = request.pending;
            const resolveDisabled = request.pending || (!resolved && !version && !isOmit);
            const action = resolved ? "resolve-item" : (version || isOmit ? "resolve-item" : "adjust-item");
            return {
                resolved,
                locked: !!resolved,
                action,
                disabled: resolved ? request.pending : (action === "adjust-item" ? generateDisabled : resolveDisabled),
                label: resolved
                    ? (resolved.handling === "OMIT" ? "取消省略" : "取消采用")
                    : isOmit ? "确认省略"
                        : version ? "采用此版本"
                            : request.error ? "重试 AI 调整" : "AI 生成回答"
            };
        }

        function renderRequestHeader(request) {
            const action = requestAction(request);
            const coverage = request.coverage || "";
            const badge = action.resolved?.handling === "OMIT"
                ? "已省略"
                : action.locked
                    ? "已处理"
                    : coverage === "GROUNDED"
                        ? "待生成"
                        : "待处理";
            // I-3: machine-filled items stay fully editable; the badge is a
            // visible marker only and disappears as soon as the operator edits
            // the handling or the instruction.
            const autoFilledBadge = request.autoFilled === true && !request.instructionEditedByOperator
                ? `<span class="trust-reply-autofilled">机器代填</span>`
                : "";
            return `<span class="trust-reply-item-index">${Number(request.index) + 1}</span><div class="trust-reply-item-title"><strong>${escapeText(request.requestText)}</strong>${coverage ? `<span class="trust-reply-coverage" data-coverage="${escapeText(coverage)}">${escapeText(COVERAGE_LABELS[coverage] || coverage)}</span>` : ""} <button type="button" class="button small secondary" data-action="toggle-item" data-request-key="${escapeText(request.requestKey)}" aria-expanded="${request.expanded}">${request.expanded ? "收起" : "展开"}</button></div><span class="badge ${action.locked ? "ok" : ""}">${badge}</span>${autoFilledBadge}`;
        }

        function renderItemActions(request) {
            const action = requestAction(request);
            const label = request.pending ? "生成中…" : state.stateSavePending ? "保存中…" : action.label;
            const disabled = action.disabled || state.stateSavePending;
            return `<button type="button" class="button ${action.resolved ? "secondary" : "primary"}" aria-pressed="${action.locked}" data-action="${action.action}" data-request-key="${escapeText(request.requestKey)}"${disabled ? " disabled" : ""}>${label}</button>`;
        }

        function renderRequestAnswerContents(request) {
            const version = activeVersion(request);
            return version
                ? `<div class="trust-reply-answer-head"><span>${escapeText(GENERATION_KIND_LABELS[version.generationKind] || "版本正文")}</span></div><div class="trust-reply-answer-body pre">${escapeText(version.answerText || "")}</div>${version.answerText?.trim() ? renderTranslation(request, version.versionId, request.answerTranslationsByVersionId[version.versionId]) : ""}`
                : `<div class="trust-reply-answer-body muted">尚未生成版本</div>`;
        }

        function renderRequestAnswer(request) {
            return `<div class="trust-reply-answer" data-role="answer">${renderRequestAnswerContents(request)}</div>`;
        }

        function findRenderedElement(container, role, requestKey) {
            return [...container.querySelectorAll("[data-role]")].find((element) => {
                return element.dataset?.role === role && (requestKey == null || element.dataset.requestKey === requestKey);
            }) || null;
        }

        function syncInstructionUi(request) {
            const item = findRenderedElement(host, "item", request.requestKey);
            if (item) {
                const action = requestAction(request);
                item.dataset.locked = String(action.locked);
                const header = findRenderedElement(item, "item-header");
                const version = findRenderedElement(item, "version", request.requestKey);
                const answer = findRenderedElement(item, "answer");
                const actions = findRenderedElement(item, "item-actions");
                if (header) header.innerHTML = renderRequestHeader(request);
                if (version) version.value = request.activeVersionId || "";
                if (answer) answer.innerHTML = renderRequestAnswerContents(request);
                if (actions) actions.innerHTML = renderItemActions(request);
            }
            const summary = findRenderedElement(host, "summary");
            if (summary) summary.innerHTML = renderSummary();
        }

        function renderRequest(request) {
            const action = requestAction(request);
            const resolved = action.resolved;
            const locked = action.locked;
            const needsOperatorInstruction = request.draftHandling === "ANSWER_FROM_OPERATOR_INPUT";
            const options = request.availableHandlings.map((handling) => `<option value="${escapeText(handling)}"${handling === request.draftHandling ? " selected" : ""}>${escapeText(HANDLING_LABELS[handling] || handling)}</option>`).join("");
            const versions = request.versions.map((item, index) => `<option value="${escapeText(item.versionId)}"${item.versionId === request.activeVersionId ? " selected" : ""}>版本 ${index + 1} · ${escapeText(GENERATION_KIND_LABELS[item.generationKind] || item.generationKind || "版本")}</option>`).join("");
            const error = request.error ? `<div class="ai-reply-error" data-role="item-error" role="alert">${escapeText(request.error)}</div>` : "";
            const questionTranslation = renderTranslation(request, null, request.questionTranslation);
            const answer = renderRequestAnswer(request);
            const instructionLabel = needsOperatorInstruction ? "回答说明（AI 将仅据此生成）" : "AI 调整要求（仅调整表达，可留空）";
            return `<article class="compose-panel trust-reply-item" data-role="item" data-request-key="${escapeText(request.requestKey)}" data-coverage="${escapeText(request.coverage || "")}" data-locked="${locked}"><div class="trust-reply-item-head" data-role="item-header">${renderRequestHeader(request)}</div>${renderFactSection(request)}<div data-role="item-body"${request.expanded ? "" : " hidden"}>${questionTranslation}<div class="trust-reply-item-controls"><label class="trust-reply-field">处理方式<select data-role="handling" data-request-key="${escapeText(request.requestKey)}"${request.pending ? " disabled" : ""}>${options}</select></label><label class="trust-reply-field">版本<select class="trust-reply-version-select" data-role="version" data-request-key="${escapeText(request.requestKey)}"${request.pending ? " disabled" : ""}><option value="">请选择版本</option>${versions}</select></label></div><label class="trust-reply-field">${instructionLabel}<textarea data-role="instruction" data-request-key="${escapeText(request.requestKey)}" maxlength="500"${request.pending ? " disabled" : ""}>${escapeText(request.instruction)}</textarea></label>${answer}${error}<div class="trust-reply-item-actions" data-role="item-actions">${renderItemActions(request)}</div></div></article>`;
        }

        // I-5/R-2: a finished assembly is never presented as send clearance.
        // The verdict renders three independent lines: assembly completion, the
        // decision, and each failed hard gate. The decision is derived only
        // from the retained auto-reply preview evidence; without evidence the
        // verdict stays in the explicit pending state, never blank and never
        // implying clearance.
        function renderVerdict() {
            const assembled = previewState() === "CURRENT" && !!state.assembly;
            const evidence = state.previewEvidence;
            const decision = evidence
                ? (evidence.decision === "AUTO_SEND" && evidence.gates.length === 0 ? "可自动发" : "转人工")
                : "尚未预判";
            const gateLine = evidence
                ? (evidence.gates.length > 0
                    ? evidence.gates.map((code) => `<li>${escapeText(code)}</li>`).join("")
                    : `<li class="muted">无未通过硬性闸门</li>`)
                : `<li class="muted">硬性闸门：尚未预判</li>`;
            return `<div data-role="verdict"><p class="muted">${escapeText(assembled ? "汇总已完成" : "尚未汇总")}</p><p class="muted">判定：${escapeText(decision)}</p><ul>${gateLine}</ul></div>`;
        }

        function renderSummary() {
            const readiness = computeReadiness();
            const locked = readiness.resolvedCount;
            const total = readiness.total;
            const assembly = state.assembly;
            const percent = total > 0 ? Math.round((locked / total) * 100) : 0;
            const countParts = [`已处理 ${locked}/${total}`];
            if (readiness.pendingGeneration > 0) countParts.push(`待生成 ${readiness.pendingGeneration} 项`);
            if (readiness.unresolvedManual > 0) countParts.push(`待人工处理 ${readiness.unresolvedManual} 项`);
            const lockHint = countParts.join(" · ");
            const assembleLabel = state.generation.pending && state.generation.stage === "ASSEMBLING"
                ? "整合中…"
                : state.generation.pending && readiness.pendingGeneration > 0
                    ? "生成并整合中…"
                    : readiness.unresolvedManual > 0
                        ? "服务端整合"
                        : readiness.pendingGeneration > 0
                            ? "生成有据回答并整合"
                            : "服务端整合";
            const assembleHint = readiness.unresolvedManual > 0
                ? `尚有 ${readiness.unresolvedManual} 项待人工处理`
                : "";
            const stateKey = previewState();
            const previewBlock = stateKey === "CURRENT" && assembly
                ? `<div class="trust-reply-assembly"><div class="muted">服务端原始正文</div><pre class="pre" data-role="raw-preview">${escapeText(assembly.rawDraftText || "")}</pre></div>`
                : `<div class="trust-reply-assembly"><div class="muted">配置预览 · 未整合</div><pre class="pre" data-role="local-preview">${escapeText(renderFrameLocalPreview())}</pre></div>`;
            const completeDisabled = !assembly || stateKey !== "CURRENT" || state.completePending;
            return `<h4>整合摘要</h4>${renderVerdict()}<p class="trust-reply-lock-hint">${lockHint}${assembleHint ? ` · ${assembleHint}` : ""}</p><div class="trust-reply-progress" role="progressbar" aria-valuenow="${percent}" aria-valuemin="0" aria-valuemax="100"><span style="width:${percent}%"></span></div>${previewBlock}<div class="trust-reply-final-actions"><button type="button" class="button primary" data-action="assemble"${canAssemble() ? "" : " disabled"}>${assembleLabel}</button><button type="button" class="button secondary" data-action="complete"${completeDisabled ? " disabled" : ""}>${state.mode === MODES.SIMULATION ? "完成模拟并评估" : "采用到人工回复"}</button></div>`;
        }

        function renderStatus() {
            const parts = [];
            if (state.generation.message) {
                const cls = state.generation.stage === "ERROR" ? "ai-reply-error" : "ai-reply-coverage";
                parts.push(`<div class="${cls}" role="${state.generation.stage === "ERROR" ? "alert" : "status"}">${escapeText(state.generation.stage ? `${state.generation.stage}：` : "")}${escapeText(state.generation.message)}</div>`);
            }
            // 03b (S-2): one-click rerun for every context-stale item,
            // appended at the end of the status area; verbatim contract DOM,
            // only when at least one item is context-stale (I-4).
            const contextStaleCount = state.requests.filter((request) => request.contextStale === true).length;
            if (contextStaleCount >= 1) {
                parts.push(`<button type="button" class="button small secondary" data-action="regenerate-context-stale">重新生成受影响条目</button>`);
            }
            return parts.join("");
        }

        function renderStatusOnly() {
            if (!state.destroyed) host.innerHTML = renderMarkup();
        }

        function timeoutOptions(timeout, total) {
            const values = total ? ["auto", "300", "600", "900", "1800", "custom"] : ["30", "60", "90", "180", "custom"];
            return values.map((value) => {
                const label = value === "auto" ? "自动（300 秒）" : value === "custom" ? "自定义" : `${value} 秒${value === (total ? "300" : "30") ? "（默认）" : ""}`;
                return `<option value="${value}"${timeout.mode === value ? " selected" : ""}>${label}</option>`;
            }).join("");
        }

        function customTimeout(timeout, name) {
            const visible = timeout.mode === "custom" ? "" : " hidden";
            return `<label class="ai-reply-timeout-custom-wrap"${visible}><input data-role="${name}-custom" class="ai-reply-timeout-custom-input" type="number" min="10" max="${name === "attempt" ? 600 : 7200}" step="1" value="${escapeText(timeout.customSeconds)}" aria-label="自定义${name === "attempt" ? "单次" : "总"}生成超时秒数"><span>秒</span></label>`;
        }

        function timeoutSeconds(timeout, min, max) {
            if (timeout.mode === "auto") return 300;
            const number = Number(timeout.mode === "custom" ? timeout.customSeconds : timeout.mode);
            return Number.isFinite(number) ? Math.min(max, Math.max(min, Math.trunc(number))) : min;
        }

        function isAbort(error) {
            return error && (error.name === "AbortError" || error.code === 20);
        }

        function onClick(event) {
            const button = event.target.closest && event.target.closest("[data-action]");
            if (!button || !host.contains(button)) return;
            const action = button.dataset.action;
            if (action === "adjust-item") {
                const request = findRequest(button.dataset.requestKey);
                if (request) void adjustItem(request);
            }
            if (action === "resolve-item") void toggleResolve(button.dataset.requestKey);
            if (action === "toggle-item") {
                const request = findRequest(button.dataset.requestKey);
                if (request) {
                    request.expanded = !request.expanded;
                    render();
                }
            }
            if (action === "toggle-fact-picker") toggleFactPicker(button.dataset.requestKey);
            if (action === "add-fact") void addFact(button.dataset.requestKey, button.dataset.factId);
            if (action === "remove-fact") void removeFact(button.dataset.requestKey, button.dataset.factId);
            if (action === "set-page") setActivePage(button.dataset.page, "tab");
            if (action === "next-page") setActivePage(button.dataset.page || "frame", "tab");
            if (action === "prev-page") setActivePage(button.dataset.page || "facts", "tab");
            if (action === "translate-question") {
                const request = findRequest(button.dataset.requestKey);
                if (request) void toggleTranslation(request, null);
            }
            if (action === "translate-answer") {
                const request = findRequest(button.dataset.requestKey);
                if (request) void toggleTranslation(request, button.dataset.versionId);
            }
            if (action === "assemble") void assemble();
            if (action === "complete") void complete();
            if (action === "auto-run") void autoRun();
            if (action === "auto-reset") void autoReset();
            if (action === "reset-workbench-state") void resetWorkbenchState();
            if (action === "regenerate-context-stale") void regenerateContextStale();
            if (action === "cancel-generation") void cancelGeneration(state.generation.generationId, state.generation.controller);
        }

        function onChange(event) {
            const target = event.target;
            if (target.dataset?.role === "model") {
                state.selectedModel = target.value;
                return;
            }
            if (target.dataset?.role === "attempt-timeout") {
                state.attemptTimeout.mode = target.value;
                state.attemptTimeout.seconds = timeoutSeconds(state.attemptTimeout, 10, 600);
                render();
                return;
            }
            if (target.dataset?.role === "total-timeout") {
                state.totalTimeout.mode = target.value;
                state.totalTimeout.seconds = timeoutSeconds(state.totalTimeout, 10, 7200);
                render();
                return;
            }
            if (target.dataset?.role === "frame-select" && target.dataset.frameSlot) {
                onFrameChange(target.dataset.frameSlot, target.value);
                return;
            }
            const request = target.dataset?.requestKey ? findRequest(target.dataset.requestKey) : null;
            if (!request) return;
            if (target.dataset.role === "handling") {
                const previous = captureDecision(request);
                request.draftHandling = target.value;
                request.autoFilled = false;
                request.activeVersionId = null;
                const invalidated = invalidateDecision(request);
                render();
                if (previous.resolvedVersionId && invalidated) void persistDecisionUnlock(request, previous);
            } else if (target.dataset.role === "version") {
                const previous = captureDecision(request);
                request.activeVersionId = target.value || null;
                const invalidated = request.activeVersionId !== request.resolvedVersionId && invalidateDecision(request);
                render();
                if (previous.resolvedVersionId && invalidated) void persistDecisionUnlock(request, previous);
            }
        }

        function onInput(event) {
            const target = event.target;
            const request = target.dataset?.requestKey ? findRequest(target.dataset.requestKey) : null;
            if (target.dataset?.role === "instruction" && request) {
                const instruction = target.value.slice(0, 500);
                if (instruction !== request.instruction) {
                    const previous = captureDecision(request);
                    request.instruction = instruction;
                    const wasAutoFilled = request.autoFilled;
                    request.autoFilled = false;
                    request.instructionEditedByOperator = true;
                    if (!previous.activeVersionId && !previous.resolvedVersionId && !state.assembly) {
                        if (wasAutoFilled) syncInstructionUi(request);
                        return;
                    }
                    request.activeVersionId = null;
                    const invalidated = invalidateDecision(request);
                    syncInstructionUi(request);
                    if (previous.resolvedVersionId && invalidated) void persistDecisionUnlock(request, previous);
                }
            }
            if (target.dataset?.role === "attempt-custom") state.attemptTimeout.customSeconds = target.value;
            if (target.dataset?.role === "total-custom") state.totalTimeout.customSeconds = target.value;
            if (target.dataset?.role === "fact-search") {
                const picker = typeof target.closest === "function" ? target.closest('[data-role="fact-picker"]') : null;
                if (picker && typeof picker.querySelectorAll === "function") {
                    const query = (target.value || "").trim().toLowerCase();
                    picker.querySelectorAll(".trust-reply-fact-picker-option").forEach((option) => {
                        option.hidden = !!query && !(option.dataset?.search || "").includes(query);
                    });
                }
            }
        }

        function findRequest(requestKey) {
            return state.requests.find((request) => request.requestKey === requestKey);
        }

        async function toggleResolve(requestKey) {
            const request = findRequest(requestKey);
            if (!request || request.pending || state.stateSavePending) return;
            const previousResolved = request.resolvedVersionId;
            const previousExpanded = request.expanded;
            if (request.resolvedVersionId) {
                request.resolvedVersionId = null;
                request.expanded = true;
            } else {
                let version = activeVersion(request);
                if (!version && request.draftHandling === "OMIT") {
                    version = await adjustItem(request);
                    if (!version || findRequest(requestKey) !== request) return;
                }
                if (!version) {
                    request.error = "请先生成并选择一个版本";
                    render();
                    return;
                }
                if (!isVersionSerializable(version, request)) {
                    request.error = "当前版本无效，请重新生成并采用";
                    render();
                    return;
                }
                request.resolvedVersionId = version.versionId;
                request.expanded = false;
            }
            invalidateAssembly();
            state.stateSavePending = true;
            render();
            try {
                await persistResolvedSnapshot();
            } catch (error) {
                if (!isLive()) return;
                state.stateSavePending = false;
                if (isFrameStaleError(error)) {
                    handleFrameStale(state.bootSeq, error.message || "框架配置已变化，请重新选择后整合");
                    return;
                }
                if (isStaleError(error)) {
                    handleStaleGeneration(state.bootSeq, error.message || "来源或事实已变化，请确认后刷新工作台");
                    return;
                }
                request.resolvedVersionId = previousResolved;
                request.expanded = previousExpanded;
                request.error = error.message || "保存失败，请重试";
                render();
                return;
            }
            if (!state.destroyed) {
                state.stateSavePending = false;
                render();
            }
        }

        function unmount() {
            if (state.destroyed) return;
            state.destroyed = true;
            state.bootSeq += 1;
            cancelController(state.generation.controller);
            state.itemControllers.forEach((controller) => cancelController(controller));
            state.itemControllers.clear();
            state.translationControllers.forEach((controller) => cancelController(controller));
            state.translationControllers.clear();
            listeners.forEach(([type, handler]) => host.removeEventListener(type, handler));
            host.innerHTML = "";
        }

        // I-2: the read-only host registers no interaction listeners at all, so
        // generate/adopt/lock/integrate actions can never fire (the requestJson
        // gate above is the second line of defense); onComplete never runs.
        if (!state.readOnly) {
            listen("click", onClick);
            listen("change", onChange);
            listen("input", onInput);
            listen("keydown", onKeydown);
            listen("dragstart", onDragStart);
            listen("dragover", onDragOver);
            listen("drop", onDrop);
            listen("dragend", onDragEnd);
        }
        renderShell("正在加载工作台…");
        return { state, bootstrap, unmount };
    }

    global.TrustReplyWorkbench = Object.freeze({ mount, reorderFactIds, resolveFactDrop, factActionBlockReason });
})(typeof window !== "undefined" ? window : globalThis);
