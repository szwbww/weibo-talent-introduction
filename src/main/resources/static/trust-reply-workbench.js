(function (global) {
    "use strict";

    if (global.TrustReplyWorkbench) return;

    const MODES = Object.freeze({ SIMULATION: "SIMULATION", LIVE: "LIVE" });
    const SOURCES = Object.freeze({ TRAINING_MAIL: "TRAINING_MAIL", LIVE_INBOUND: "LIVE_INBOUND" });
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
        const expectedSource = options.mode === MODES.SIMULATION ? SOURCES.TRAINING_MAIL : SOURCES.LIVE_INBOUND;
        if (source.sourceType !== expectedSource || !Number.isInteger(Number(source.sourceId)) || Number(source.sourceId) <= 0) {
            return rejectMount(host, "工作台来源与页面模式不匹配");
        }
        if (typeof options.contextPath !== "string" || typeof options.onComplete !== "function") {
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
            source,
            contextPath: options.contextPath,
            onUnauthorized: typeof options.onUnauthorized === "function" ? options.onUnauthorized : null,
            onComplete: options.onComplete,
            onChange: typeof options.onChange === "function" ? options.onChange : null,
            sourceVersion: null,
            evidenceSetVersion: null,
            selectedFactIds: [],
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
            initialFullDraftSourceVersions: new Set(),
            assembly: null,
            bootSeq: 0,
            destroyed: false,
            completePending: false
        };
        const listeners = [];

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

        async function requestJson(path, payload, controller) {
            const response = await global.fetch(apiUrl(state.contextPath, path), {
                method: "POST",
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
            state.assembly = null;
            state.onChange && state.onChange();
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
            return !!version
                && version.requestKey === requestKey
                && typeof version.sourceVersion === "string"
                && version.sourceVersion === state.sourceVersion
                && typeof version.evidenceSetVersion === "string"
                && version.evidenceSetVersion === state.evidenceSetVersion;
        }

        function isStaleError(error) {
            const code = error && (error.code || error.message);
            return ["TRUST_REPLY_SOURCE_STALE", "TRUST_REPLY_EVIDENCE_STALE"].some((item) => String(code || "").includes(item));
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
                request.expanded = request.coverage === "UNSUPPORTED";
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

        function requestFromCoverage(coverage) {
            return (coverage || []).map((item, index) => ({
                requestKey: item.requestKey,
                requestText: item.requestText || "",
                index: item.index == null ? index : item.index,
                coverage: item.status || "",
                factRuleIds: [...(item.factRuleIds || [])],
                availableHandlings: [...(item.allowedHandlings || [])],
                draftHandling: item.recommendedHandling || item.allowedHandlings?.[0] || "OMIT",
                instruction: "",
                versions: [],
                activeVersionId: null,
                resolvedVersionId: null,
                expanded: item.status === "UNSUPPORTED",
                questionTranslation: { state: "idle", text: "" },
                answerTranslationsByVersionId: {},
                requestSeq: 0,
                pending: false,
                error: null
            }));
        }

        function applyBootstrap(data, seq) {
            if (!isLive(seq)) return;
            if (!data || data.source?.sourceType !== source.sourceType || Number(data.source?.sourceId) !== source.sourceId) {
                throw new Error("TRUST_REPLY_SOURCE_MISMATCH");
            }
            state.sourceVersion = data.sourceVersion;
            state.evidenceSetVersion = data.evidenceSetVersion;
            state.selectedFactIds = [...(data.canonicalFactIds || data.suggestedFactIds || [])];
            state.rules = data.rulesByCategory || [];
            state.availableModels = data.availableModels?.length ? [...data.availableModels] : [data.defaultModel || "DEEPSEEK_V4_FLASH"];
            state.selectedModel = state.availableModels.includes(data.defaultModel) ? data.defaultModel : state.availableModels[0];
            state.llmEnabled = data.llmEnabled !== false;
            state.requests = requestFromCoverage(data.requestCoverage);
            state.generation.pending = false;
            setStatus("", "READY");
            render();
            state.requests
                .filter((request) => request.coverage === "UNSUPPORTED")
                .forEach((request) => { void requestTranslation(request, null); });
            if (state.requests.some((request) => request.coverage === "GROUNDED" || request.coverage === "PARTIAL")
                && !state.initialFullDraftSourceVersions.has(state.sourceVersion)) {
                state.initialFullDraftSourceVersions.add(state.sourceVersion);
                void generateAll();
            }
        }

        async function bootstrap() {
            const seq = ++state.bootSeq;
            if (state.destroyed) return;
            resetVersions();
            renderShell("正在加载工作台…");
            try {
                const data = await requestJson("/api/trust-reply/workbench/bootstrap", {
                    source,
                    requestedFactIds: state.selectedFactIds.length ? state.selectedFactIds : null
                });
                applyBootstrap(data, seq);
            } catch (error) {
                if (!isLive(seq) || isAbort(error)) return;
                state.generation.pending = false;
                setStatus(error.message || "工作台加载失败", "ERROR");
                renderShell(error.message || "工作台加载失败");
            }
        }

        function makeGenerationPayload(requestKey, handling, instruction, generationId) {
            return {
                source,
                expectedSourceVersion: state.sourceVersion,
                expectedEvidenceSetVersion: state.evidenceSetVersion,
                requestedFactIds: [...state.selectedFactIds],
                requestKey: requestKey || null,
                handling: handling || null,
                operatorInstruction: instruction || null,
                model: state.selectedModel,
                generationId,
                operation: requestKey ? "ADJUST_ITEM" : "FULL_DRAFT",
                llmAttemptTimeoutSeconds: timeoutSeconds(state.attemptTimeout, 10, 600),
                llmTotalTimeoutSeconds: timeoutSeconds(state.totalTimeout, 10, 7200)
            };
        }

        async function generateAll() {
            if (state.generation.pending || state.requests.some((request) => request.pending)
                || !state.requests.length || !state.sourceVersion) return;
            const seq = state.bootSeq;
            const generationId = makeId();
            state.generation = { pending: true, stage: "QUEUED", message: "已提交完整生成", generationId, controller: null };
            state.requests.forEach((request) => { request.error = null; });
            render();
            let result = null;
            try {
                await requestSse("/api/trust-reply/workbench/generations/stream", makeGenerationPayload(null, null, null, generationId), "full", (event, data) => {
                    if (!isLive(seq)) return;
                    if (event === "ready" || event === "progress" || event === "heartbeat") {
                        const progress = data.progress || data;
                        state.generation.stage = progress.phase || event.toUpperCase();
                        state.generation.message = progress.message || progress.phase || event;
                        renderStatusOnly();
                    } else if (event === "result") {
                        result = data;
                    } else if (event === "error") {
                        throw errorFromStream(data, "AI 生成失败");
                    } else if (event === "cancelled") {
                        state.generation.message = "已取消生成";
                    }
                });
                if (!isLive(seq)) return;
                if (result && !applyGenerationResult(result, null, seq)) return;
                state.generation.pending = false;
                state.generation.controller = null;
                render();
            } catch (error) {
                if (isStaleError(error)) {
                    handleStaleGeneration(seq, error.message || "来源或事实已变化，请确认后刷新工作台");
                    return;
                }
                if (isAbort(error)) {
                    if (isLive(seq) && state.generation.generationId === generationId) {
                        state.generation.pending = false;
                        state.generation.controller = null;
                        state.generation.stage = "CANCELLED";
                        state.generation.message = "已取消生成，可重试";
                        render();
                    }
                    return;
                }
                if (!isLive(seq)) return;
                state.generation.pending = false;
                state.generation.message = error.message || "生成失败，可重试";
                state.generation.stage = "ERROR";
                render();
            }
        }

        async function adjustItem(request) {
            if (request.pending) return;
            if (state.generation.pending) {
                request.error = "正在生成其他回复，请稍后";
                request.expanded = true;
                render();
                return;
            }
            if (request.draftHandling === "ANSWER_FROM_OPERATOR_INPUT" && !request.instruction.trim()) {
                request.error = "请先填写回答说明";
                request.expanded = true;
                render();
                return;
            }
            const requestSeq = ++request.requestSeq;
            const sourceSeq = state.bootSeq;
            const generationId = makeId();
            request.pending = true;
            request.error = null;
            invalidateAssembly();
            render();
            let result = null;
            try {
                await requestSse("/api/trust-reply/workbench/generations/stream", makeGenerationPayload(request.requestKey, request.draftHandling, request.instruction, generationId), request.requestKey, (event, data) => {
                    if (!isLive(sourceSeq) || request.requestSeq !== requestSeq) return;
                    if (event === "progress" || event === "ready" || event === "heartbeat") {
                        setStatus((data.progress || data).message || (data.progress || data).phase || event, (data.progress || data).phase || event.toUpperCase());
                        renderStatusOnly();
                    } else if (event === "result") {
                        result = data;
                    } else if (event === "error") {
                        throw errorFromStream(data, "单项生成失败");
                    }
                });
                if (!isLive(sourceSeq) || request.requestSeq !== requestSeq) return;
                if (!hasGenerationIdentity(result)) {
                    handleStaleGeneration(sourceSeq, "来源或事实已变化，请确认后刷新工作台");
                    return;
                }
                const candidates = [result.version, ...(Array.isArray(result.itemVersions) ? result.itemVersions : [])].filter(Boolean);
                const version = candidates.find((item) => hasVersionIdentity(item, request.requestKey));
                if (candidates.some((item) => item.requestKey === request.requestKey && !hasVersionIdentity(item, request.requestKey))) {
                    handleStaleGeneration(sourceSeq, "生成版本的来源或事实已变化，请确认后刷新工作台");
                    return;
                }
                if (!version) throw new Error("单项生成未返回版本");
                request.versions = [...request.versions, version];
                request.activeVersionId = version.versionId;
                if (version.handling === "OMIT") {
                    request.resolvedVersionId = version.versionId;
                    request.expanded = false;
                }
                request.pending = false;
                state.itemControllers.delete(request.requestKey);
                render();
                return version;
            } catch (error) {
                if (isStaleError(error)) {
                    handleStaleGeneration(sourceSeq, error.message || "来源或事实已变化，请确认后刷新工作台");
                    return;
                }
                if (!isLive(sourceSeq) || request.requestSeq !== requestSeq || isAbort(error)) return;
                request.pending = false;
                request.error = error.message || "单项生成失败，可重试";
                render();
            }
        }

        function applyGenerationResult(result, targetRequestKey, seq) {
            if (!result || !isLive(seq)) return;
            if (!hasGenerationIdentity(result)) {
                handleStaleGeneration(seq, "来源或事实已变化，请确认后刷新工作台");
                return false;
            }
            const versions = Array.isArray(result.itemVersions) ? result.itemVersions : [];
            const requestKeys = new Set(state.requests.map((request) => request.requestKey));
            if (versions.some((version) => !requestKeys.has(version.requestKey) || !hasVersionIdentity(version, version.requestKey))) {
                handleStaleGeneration(seq, "生成版本身份无效，请确认刷新工作台");
                return false;
            }
            if (targetRequestKey) {
                const request = state.requests.find((item) => item.requestKey === targetRequestKey);
                const version = versions.find((item) => item.requestKey === targetRequestKey);
                if (request && version) {
                    request.versions = [...request.versions, version];
                    request.activeVersionId = version.versionId;
                }
                return !!request && !!version;
            }
            applyInitialVersions(versions);
            invalidateAssembly();
            return true;
        }

        function applyInitialVersions(versions) {
            state.requests.forEach((request) => {
                const matching = versions.filter((version) => version.requestKey === request.requestKey);
                request.versions = matching;
                const initialVersion = matching.find((version) => request.availableHandlings.includes(version.handling));
                request.activeVersionId = initialVersion ? initialVersion.versionId : null;
                if (initialVersion && (request.coverage === "GROUNDED" || request.coverage === "PARTIAL")) {
                    request.resolvedVersionId = initialVersion.versionId;
                    request.expanded = false;
                } else {
                    request.resolvedVersionId = null;
                    request.expanded = true;
                }
            });
        }

        async function cancelGeneration(generationId, controller) {
            cancelController(controller);
            if (!state.destroyed && state.generation.generationId === generationId) {
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

        async function assemble() {
            if (!canAssemble()) return;
            const lockedItems = state.requests.map(serializeResolvedVersion);
            const invalidRequest = state.requests.find((request, index) => !lockedItems[index]);
            if (invalidRequest) {
                invalidRequest.error = "已采用版本无效，请重新生成并采用";
                invalidRequest.expanded = true;
                render();
                return;
            }
            const seq = state.bootSeq;
            state.generation.pending = true;
            state.generation.stage = "ASSEMBLING";
            state.generation.message = "正在请求服务端整合…";
            render();
            try {
                const response = await requestJson("/api/trust-reply/workbench/assemble", {
                    source,
                    expectedSourceVersion: state.sourceVersion,
                    expectedEvidenceSetVersion: state.evidenceSetVersion,
                    requestedFactIds: [...state.selectedFactIds],
                    lockedItems
                });
                if (!isLive(seq)) return;
                if (response.sourceVersion !== state.sourceVersion || response.evidenceSetVersion !== state.evidenceSetVersion) {
                    throw new Error("TRUST_REPLY_ASSEMBLY_STALE");
                }
                state.assembly = { ...response, requestedFactIds: [...state.selectedFactIds] };
                state.generation.pending = false;
                state.generation.message = "服务端整合完成";
                state.generation.stage = "READY";
                render();
            } catch (error) {
                if (!isLive(seq) || isAbort(error)) return;
                state.generation.pending = false;
                state.generation.stage = "ERROR";
                state.generation.message = error.message || "整合失败，可重试";
                render();
            }
        }

        function canAssemble() {
            return !state.generation.pending && state.requests.length > 0 && state.requests.every((request) => {
                return !request.pending && !!serializeResolvedVersion(request);
            });
        }

        function activeVersion(request) {
            return request.versions.find((version) => version.versionId === request.activeVersionId) || null;
        }

        function resolvedVersion(request) {
            return request.versions.find((version) => version.versionId === request.resolvedVersionId) || null;
        }

        function invalidateDecision(request) {
            request.resolvedVersionId = null;
            invalidateAssembly();
        }

        function serializeResolvedVersion(request) {
            const version = resolvedVersion(request);
            if (!version || version.requestKey !== request.requestKey
                || !version.versionId || !version.handling || !version.generationKind
                || !version.sourceVersion || !version.evidenceSetVersion || !Array.isArray(version.claims)) {
                return null;
            }
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
                operatorInstruction: version.operatorInstruction || ""
            };
        }

        function onFactChange(checkbox) {
            const requested = [...host.querySelectorAll('[data-role="fact"]')]
                .filter((item) => item.checked)
                .map((item) => Number(item.value));
            if (typeof global.confirm === "function" && !global.confirm("事实变化会清空当前版本和整合结果，继续？")) {
                render();
                return;
            }
            state.selectedFactIds = requested;
            resetVersions();
            void bootstrap();
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
            if (action === "cancel-generation") void cancelGeneration(state.generation.generationId, state.generation.controller);
        }

        function onChange(event) {
            const target = event.target;
            if (target.matches && target.matches('[data-role="fact"]')) return onFactChange(target);
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
            const request = target.dataset?.requestKey ? findRequest(target.dataset.requestKey) : null;
            if (!request) return;
            if (target.dataset.role === "handling") {
                request.draftHandling = target.value;
                request.activeVersionId = null;
                invalidateDecision(request);
                render();
            } else if (target.dataset.role === "version") {
                request.activeVersionId = target.value || null;
                if (request.activeVersionId !== request.resolvedVersionId) invalidateDecision(request);
                render();
            }
        }

        function onInput(event) {
            const target = event.target;
            const request = target.dataset?.requestKey ? findRequest(target.dataset.requestKey) : null;
            if (target.dataset?.role === "instruction" && request) {
                const instruction = target.value.slice(0, 500);
                if (instruction !== request.instruction) {
                    request.instruction = instruction;
                    request.activeVersionId = null;
                    invalidateDecision(request);
                    syncInstructionUi(request);
                }
            }
            if (target.dataset?.role === "attempt-custom") state.attemptTimeout.customSeconds = target.value;
            if (target.dataset?.role === "total-custom") state.totalTimeout.customSeconds = target.value;
        }

        function findRequest(requestKey) {
            return state.requests.find((request) => request.requestKey === requestKey);
        }

        async function toggleResolve(requestKey) {
            const request = findRequest(requestKey);
            if (!request || request.pending) return;
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
                request.resolvedVersionId = version.versionId;
                request.expanded = false;
            }
            invalidateAssembly();
            render();
        }

        async function complete() {
            if (!state.assembly || state.completePending) return;
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

        function renderShell(message) {
            if (state.destroyed) return;
            const modeNote = state.mode === MODES.SIMULATION ? "模拟 · 不外发" : "正式回复";
            host.innerHTML = `<details class="detail-section reply-workflow-detail trust-reply-workbench" open>
                <summary class="reply-workflow-summary"><span class="reply-workflow-icon" aria-hidden="true">⌘</span><span class="reply-workflow-title"><strong>可信回复工作台</strong><small>${modeNote}</small></span><span class="reply-workflow-status" data-role="mode-note">${modeNote}</span><span class="reply-workflow-chevron" aria-hidden="true">⌄</span></summary>
                <div class="reply-workflow-content"><div class="trust-reply-toolbar" data-role="toolbar"><p class="trust-reply-mode-note" data-role="mode-description">${modeNote}</p></div><div class="ai-reply-feedback" data-role="status" role="status" aria-live="polite">${escapeText(message || "")}</div><div class="trust-reply-layout"><aside class="trust-reply-summary compose-panel" data-role="summary"></aside><div class="trust-reply-item-list" data-role="items"></div></div></div>
            </details>`;
        }

        function render() {
            if (state.destroyed) return;
            host.innerHTML = renderMarkup();
        }

        function renderMarkup() {
            const modeNote = state.mode === MODES.SIMULATION ? "模拟 · 不外发" : "正式回复";
            const itemMarkup = state.requests.map(renderRequest).join("") || `<div class="compose-panel"><p class="muted">暂无可处理请求</p></div>`;
            return `<details class="detail-section reply-workflow-detail trust-reply-workbench" open>
                <summary class="reply-workflow-summary"><span class="reply-workflow-icon" aria-hidden="true">⌘</span><span class="reply-workflow-title"><strong>可信回复工作台</strong><small>${modeNote}</small></span><span class="reply-workflow-status" data-role="mode-note">${modeNote}</span><span class="reply-workflow-chevron" aria-hidden="true">⌄</span></summary>
                <div class="reply-workflow-content"><div class="trust-reply-toolbar" data-role="toolbar">${renderToolbar()}</div><div class="ai-reply-feedback" data-role="status" role="status" aria-live="polite">${renderStatus()}</div><div class="trust-reply-layout"><aside class="trust-reply-summary compose-panel" data-role="summary">${renderSummary()}</aside><div class="trust-reply-item-list" data-role="items">${itemMarkup}</div></div></div>
            </details>`;
        }

        function renderToolbar() {
            const modeNote = state.mode === MODES.SIMULATION ? "模拟 · 不外发" : "正式回复";
            const cancelButton = state.generation.pending
                ? `<button type="button" class="button danger" data-action="cancel-generation">取消生成</button>`
                : "";
            const modelOptions = state.availableModels.map((model) => `<option value="${escapeText(model)}"${model === state.selectedModel ? " selected" : ""}>${escapeText(MODEL_LABELS[model] || model)}</option>`).join("");
            const factOptions = state.rules.map((rule) => {
                const id = rule.ruleId ?? rule.id;
                return `<label class="trust-reply-fact-option"><input data-role="fact" type="checkbox" value="${escapeText(id)}"${state.selectedFactIds.includes(Number(id)) ? " checked" : ""}><span>${escapeText(rule.displayName || "事实")}</span></label>`;
            }).join("");
            return `<p class="trust-reply-mode-note" data-role="mode-description">${modeNote}</p><div class="ai-reply-model-row ai-reply-generation-controls"><label>生成模型<select data-role="model" class="ai-reply-model-select"${state.llmEnabled ? "" : " disabled"}>${modelOptions}</select></label><label>单次 TTL<select data-role="attempt-timeout" class="ai-reply-model-select">${timeoutOptions(state.attemptTimeout, false)}</select></label>${customTimeout(state.attemptTimeout, "attempt")}<label>总 TTL<select data-role="total-timeout" class="ai-reply-model-select">${timeoutOptions(state.totalTimeout, true)}</select></label>${customTimeout(state.totalTimeout, "total")}${cancelButton}</div><div class="compose-rule-list" data-role="facts"><span class="muted">事实选择：</span>${factOptions || "<span class=muted>服务端未提供可选事实</span>"}</div>`;
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
            const badge = action.resolved?.handling === "OMIT" ? "已省略" : action.locked ? "已处理" : "待处理";
            return `<span class="trust-reply-item-index">${Number(request.index) + 1}</span><div class="trust-reply-item-title"><strong>${escapeText(request.requestText)}</strong>${coverage ? `<span class="trust-reply-coverage" data-coverage="${escapeText(coverage)}">${escapeText(COVERAGE_LABELS[coverage] || coverage)}</span>` : ""} <button type="button" class="button small secondary" data-action="toggle-item" data-request-key="${escapeText(request.requestKey)}" aria-expanded="${request.expanded}">${request.expanded ? "收起" : "展开"}</button></div><span class="badge ${action.locked ? "ok" : ""}">${badge}</span>`;
        }

        function renderItemActions(request) {
            const action = requestAction(request);
            return `<button type="button" class="button ${action.resolved ? "secondary" : "primary"}" aria-pressed="${action.locked}" data-action="${action.action}" data-request-key="${escapeText(request.requestKey)}"${action.disabled ? " disabled" : ""}>${request.pending ? "生成中…" : action.label}</button>`;
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
            return `<article class="compose-panel trust-reply-item" data-role="item" data-request-key="${escapeText(request.requestKey)}" data-coverage="${escapeText(request.coverage || "")}" data-locked="${locked}"><div class="trust-reply-item-head" data-role="item-header">${renderRequestHeader(request)}</div><div data-role="item-body"${request.expanded ? "" : " hidden"}>${questionTranslation}<div class="trust-reply-item-controls"><label class="trust-reply-field">处理方式<select data-role="handling" data-request-key="${escapeText(request.requestKey)}"${request.pending ? " disabled" : ""}>${options}</select></label><label class="trust-reply-field">版本<select class="trust-reply-version-select" data-role="version" data-request-key="${escapeText(request.requestKey)}"${request.pending ? " disabled" : ""}><option value="">请选择版本</option>${versions}</select></label></div><label class="trust-reply-field">${instructionLabel}<textarea data-role="instruction" data-request-key="${escapeText(request.requestKey)}" maxlength="500"${request.pending ? " disabled" : ""}>${escapeText(request.instruction)}</textarea></label>${answer}${error}<div class="trust-reply-item-actions" data-role="item-actions">${renderItemActions(request)}</div></div></article>`;
        }

        function renderSummary() {
            const locked = state.requests.filter((request) => request.resolvedVersionId).length;
            const total = state.requests.length;
            const assembly = state.assembly;
            const percent = total > 0 ? Math.round((locked / total) * 100) : 0;
            return `<h4>整合摘要</h4><p class="trust-reply-lock-hint">已处理 ${locked}/${total}</p><div class="trust-reply-progress" role="progressbar" aria-valuenow="${percent}" aria-valuemin="0" aria-valuemax="100"><span style="width:${percent}%"></span></div>${assembly ? `<div class="trust-reply-assembly"><div class="muted">服务端原始正文</div><pre class="pre" data-role="raw-preview">${escapeText(assembly.rawDraftText || "")}</pre></div>` : ""}<div class="trust-reply-final-actions"><button type="button" class="button primary" data-action="assemble"${canAssemble() ? "" : " disabled"}>${state.generation.pending && state.generation.stage === "ASSEMBLING" ? "整合中…" : "服务端整合"}</button><button type="button" class="button secondary" data-action="complete"${!assembly || state.completePending ? " disabled" : ""}>${state.mode === MODES.SIMULATION ? "完成模拟并评估" : "采用到人工回复"}</button></div>`;
        }

        function renderStatus() {
            if (!state.generation.message) return "";
            const cls = state.generation.stage === "ERROR" ? "ai-reply-error" : "ai-reply-coverage";
            return `<div class="${cls}" role="${state.generation.stage === "ERROR" ? "alert" : "status"}">${escapeText(state.generation.stage ? `${state.generation.stage}：` : "")}${escapeText(state.generation.message)}</div>`;
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

        listen("click", onClick);
        listen("change", onChange);
        listen("input", onInput);
        renderShell("正在加载工作台…");
        return { state, bootstrap, unmount };
    }

    global.TrustReplyWorkbench = Object.freeze({ mount });
})(typeof window !== "undefined" ? window : globalThis);
