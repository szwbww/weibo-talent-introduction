const fs = require("fs");
const path = require("path");
const vm = require("vm");
const assert = require("assert");
const { describe, it } = require("node:test");

const appJsPath = path.join(__dirname, "..", "..", "main", "resources", "static", "app.js");
const stylesCssPath = path.join(__dirname, "..", "..", "main", "resources", "static", "styles.css");
const indexHtmlPath = path.join(__dirname, "..", "..", "main", "resources", "static", "index.html");
const appJsSource = fs.readFileSync(appJsPath, "utf-8");
const workbenchJsPath = path.join(__dirname, "..", "..", "main", "resources", "static", "trust-reply-workbench.js");
const workbenchJsSource = fs.readFileSync(workbenchJsPath, "utf-8");

function extractFn(name) {
    const regex = new RegExp("(?:async\\s+)?function\\s+" + name + "\\s*\\([^)]*\\)\\s*\\{[\\s\\S]*?\\n\\}");
    const match = appJsSource.match(regex);
    if (!match) throw new Error("Could not find " + name + " in app.js");
    return match[0];
}

function extractConst(name) {
    const regex = new RegExp("const\\s+" + name + "\\s*=\\s*\\{[\\s\\S]*?\\n\\};");
    const match = appJsSource.match(regex);
    if (!match) throw new Error("Could not find const " + name + " in app.js");
    return match[0];
}

function extractHandler(name, nextFunction) {
    const start = appJsSource.indexOf(`async function ${name}(`);
    const end = appJsSource.indexOf(`\n}\n\nfunction ${nextFunction}`, start);
    if (start < 0 || end < 0) throw new Error("Could not find handler " + name);
    return appJsSource.slice(start, end + 2);
}

function createSandbox() {
    const sandbox = {
        AI_REPLY_MODEL_LABELS: {
            DEEPSEEK_V4_FLASH: "DeepSeek V4 Flash",
            DEEPSEEK_V4_PRO: "DeepSeek V4 Pro"
        },
        escapeHtml: (value) => String(value ?? "")
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;")
            .replaceAll('"', "&quot;")
            .replaceAll("'", "&#039;"),
        UNNAMED_FACT_LABEL: "未命名事实",
        findSuggestRule: () => null,
        document: {
            getElementById: () => ({ textContent: "" })
        }
    };
    vm.createContext(sandbox);
    vm.runInContext(extractConst("AI_REPLY_WARNING_LABELS"), sandbox);
    vm.runInContext("this.AI_REPLY_WARNING_LABELS = AI_REPLY_WARNING_LABELS;", sandbox);
    vm.runInContext("this.AI_REPLY_FAILURE_WARNING_CODES = new Set([\"AI_REPLY_LLM_TIMEOUT\",\"AI_REPLY_LLM_RATE_LIMITED\",\"AI_REPLY_LLM_NETWORK_ERROR\",\"AI_REPLY_LLM_PROVIDER_ERROR\",\"AI_REPLY_LLM_EMPTY_RESPONSE\",\"AI_REPLY_TRUST_REPAIR_EXHAUSTED\"]);", sandbox);
    vm.runInContext(extractFn("aiReplyModelLabel"), sandbox);
    vm.runInContext(extractFn("aiReplyGenerationStateLabel"), sandbox);
    vm.runInContext(extractFn("formatUnsupportedRequests"), sandbox);
    vm.runInContext(extractFn("collapseAiReplyRequestText"), sandbox);
    vm.runInContext(extractFn("summarizeAiReplyCoverage"), sandbox);
    vm.runInContext(extractFn("formatAiReplyReviewWarnings"), sandbox);
    vm.runInContext(extractFn("resolveAiDraftReadiness"), sandbox);
    vm.runInContext(extractFn("isAiReplyGenerationSuccess"), sandbox);
    vm.runInContext(extractFn("resolveAiReplyFailureReason"), sandbox);
    vm.runInContext(extractFn("resolveAiReplyFailureReasonFromResult"), sandbox);
    vm.runInContext(extractFn("aiReplyFailureReasonLabel"), sandbox);
    vm.runInContext(extractFn("resolveFactDisplayName"), sandbox);
    vm.runInContext(extractFn("renderAiReplyFeedback"), sandbox);
    return sandbox;
}

describe("aiReplyGenerationStateLabel", () => {
    it("maps fixed Chinese labels for all four states", () => {
        const { aiReplyGenerationStateLabel } = createSandbox();
        assert.strictEqual(aiReplyGenerationStateLabel("LLM_USED"), "模型已生成");
        assert.strictEqual(
            aiReplyGenerationStateLabel("FALLBACK_LLM_DISABLED"),
            "LLM 已关闭—结构化规则草稿"
        );
        assert.strictEqual(
            aiReplyGenerationStateLabel("FALLBACK_CLIENT_UNAVAILABLE"),
            "模型客户端不可用—结构化规则草稿"
        );
        assert.strictEqual(
            aiReplyGenerationStateLabel("FALLBACK_NO_RESPONSE"),
            "模型无有效响应—结构化规则草稿"
        );
        assert.strictEqual(aiReplyGenerationStateLabel("UNKNOWN"), "");
    });
});

describe("AI_REPLY_WARNING_LABELS", () => {
    it("maps UNAUTHORIZED_ACTION_REMOVED to Chinese without raw code", () => {
        const { AI_REPLY_WARNING_LABELS } = createSandbox();
        const label = AI_REPLY_WARNING_LABELS.UNAUTHORIZED_ACTION_REMOVED;
        assert.ok(label);
        assert.notStrictEqual(label, "UNAUTHORIZED_ACTION_REMOVED");
        assert.match(label, /未授权/);
    });
});

describe("renderAiReplyFeedback generationState", () => {
    it("renders LLM_USED with coverage class and fallback with warning class", () => {
        const sandbox = createSandbox();
        const container = { hidden: true, innerHTML: "" };

        sandbox.renderAiReplyFeedback(container, {
            generationState: "LLM_USED",
            requestCount: 0,
            groundedRequestCount: 0,
            contextWarnings: [],
            unsupportedRequests: []
        });
        assert.strictEqual(container.hidden, false);
        assert.match(container.innerHTML, /class="ai-reply-coverage"/);
        assert.match(container.innerHTML, /模型已生成/);
        assert.doesNotMatch(container.innerHTML, /class="pre"/);

        sandbox.renderAiReplyFeedback(container, {
            generationState: "FALLBACK_LLM_DISABLED",
            usedLlm: false,
            requestCount: 0,
            groundedRequestCount: 0,
            contextWarnings: [],
            unsupportedRequests: []
        });
        assert.match(container.innerHTML, /class="ai-reply-failure-banner"/);
        assert.match(container.innerHTML, /LLM 生成失败/);
        assert.doesNotMatch(container.innerHTML, /class="pre"/);
    });

    it("draft bubble and adopt handler include generationState for failure guard", () => {
        const draftBubble = extractFn("appendAiChatDraftBubble");
        assert.match(draftBubble, /generationState/);

        const adoptIdx = appJsSource.indexOf('if (action === "ai-adopt-draft")');
        assert.ok(adoptIdx > 0);
        const adoptBlock = appJsSource.slice(adoptIdx, adoptIdx + 1500);
        assert.match(adoptBlock, /generationState/);
        assert.match(adoptBlock, /usedLlm/);
        assert.match(adoptBlock, /不可采用/);
        assert.match(
            adoptBlock,
            /entry\.usedLlm !== true \|\| entry\.generationState !== "LLM_USED"/,
            "legacy adopt must fail closed when either success field is missing"
        );

        const turnPayloadIdx = appJsSource.indexOf("turns: turnsToSend");
        assert.ok(turnPayloadIdx > 0);
        const turnPayload = appJsSource.slice(turnPayloadIdx - 200, turnPayloadIdx + 400);
        assert.doesNotMatch(turnPayload, /generationState/);
    });

    it("does not require new CSS classes or index.html markup for generationState", () => {
        const styles = fs.readFileSync(stylesCssPath, "utf-8");
        const indexHtml = fs.readFileSync(indexHtmlPath, "utf-8");
        assert.match(styles, /\.ai-reply-generation-controls/);
        assert.doesNotMatch(styles, /generationState/);
        assert.doesNotMatch(indexHtml, /generationState|ai-reply-generation/);
        assert.match(styles, /\.ai-reply-coverage/);
        assert.match(styles, /\.ai-reply-warning/);
        assert.match(styles, /\.ai-meta-chip/);
    });

    it("replaces DeepSeek unavailable toast with shared generationState label", () => {
        assert.doesNotMatch(appJsSource, /DeepSeek 不可用/);
        assert.match(appJsSource, /aiReplyGenerationStateLabel\(result\.generationState\)/);
        assert.match(appJsSource, /模型已生成/);
    });

    it("shows a distinct warning for total TTL exhaustion", () => {
        const sandbox = createSandbox();
        const container = { hidden: true, innerHTML: "" };
        sandbox.renderAiReplyFeedback(container, {
            generationState: "FALLBACK_NO_RESPONSE",
            usedLlm: false,
            requestCount: 0,
            groundedRequestCount: 0,
            contextWarnings: ["AI_REPLY_LLM_TOTAL_TIMEOUT"],
            unsupportedRequests: []
        });
        assert.match(container.innerHTML, /DeepSeek 生成总时限已用尽/);
        assert.match(appJsSource, /AI_REPLY_LLM_TOTAL_TIMEOUT/);
    });

    it("parses split and multi-frame SSE payloads without losing event order", () => {
        const sandbox = {};
        vm.createContext(sandbox);
        vm.runInContext(extractFn("parseAiReplySseFrames"), sandbox);
        const first = sandbox.parseAiReplySseFrames("event: ready\ndata: {\"ok\":1}\n\nevent: pro");
        assert.strictEqual(JSON.stringify(first.events), JSON.stringify([{ event: "ready", data: { ok: 1 } }]));
        const second = sandbox.parseAiReplySseFrames(
            first.remainder + "gress\ndata: {\"seq\":2}\n\nevent: result\ndata: {\"ok\":true}\n\n",
            true
        );
        assert.strictEqual(JSON.stringify(second.events), JSON.stringify([
            { event: "progress", data: { seq: 2 } },
            { event: "result", data: { ok: true } }
        ]));
    });

    it("resolves attempt and total TTL with auto and custom validation", () => {
        const sandbox = {
            aiReplyState: {
                attemptTimeoutMode: "60",
                attemptCustomSeconds: 30,
                totalTimeoutMode: "auto",
                totalCustomSeconds: 300,
                attemptTimeoutSeconds: 30,
                totalTimeoutSeconds: 300
            }
        };
        vm.createContext(sandbox);
        vm.runInContext(extractFn("integerSeconds"), sandbox);
        vm.runInContext(extractFn("resolveAiReplyTimeoutSelection"), sandbox);
        assert.strictEqual(JSON.stringify(sandbox.resolveAiReplyTimeoutSelection()), JSON.stringify({
            attemptTimeoutSeconds: 60,
            totalTimeoutSeconds: 600,
            totalPayload: null
        }));
        sandbox.aiReplyState.totalTimeoutMode = "custom";
        sandbox.aiReplyState.totalCustomSeconds = 59;
        assert.throws(() => sandbox.resolveAiReplyTimeoutSelection(), /总 TTL/);
    });

    it("accepts timeout endpoints and emits custom total TTL in payload selection", () => {
        const sandbox = {
            aiReplyState: {
                attemptTimeoutMode: "custom", attemptCustomSeconds: 10,
                totalTimeoutMode: "custom", totalCustomSeconds: 10
            }
        };
        vm.createContext(sandbox);
        vm.runInContext(extractFn("integerSeconds"), sandbox);
        vm.runInContext(extractFn("resolveAiReplyTimeoutSelection"), sandbox);
        for (const [attempt, total] of [[10, 10], [600, 600], [60, 601], [600, 7200]]) {
            sandbox.aiReplyState.attemptCustomSeconds = attempt;
            sandbox.aiReplyState.totalCustomSeconds = total;
            const selection = sandbox.resolveAiReplyTimeoutSelection();
            assert.strictEqual(selection.attemptTimeoutSeconds, attempt);
            assert.strictEqual(selection.totalTimeoutSeconds, total);
            assert.strictEqual(selection.totalPayload, total);
        }
        sandbox.aiReplyState.attemptCustomSeconds = 601;
        assert.throws(() => sandbox.resolveAiReplyTimeoutSelection(), /10–600/);
        sandbox.aiReplyState.attemptCustomSeconds = 60;
        sandbox.aiReplyState.totalCustomSeconds = 7201;
        assert.throws(() => sandbox.resolveAiReplyTimeoutSelection(), /7200/);
    });

    it("syncs timeout DOM controls, automatic total text, and custom validation", () => {
        const auto = { textContent: "" };
        const attemptSelect = { value: "custom" };
        const totalSelect = {
            value: "custom",
            querySelector: (selector) => selector === "option[value='auto']" ? auto : null
        };
        const attemptCustom = { value: "45" };
        const totalCustom = { value: "450" };
        const attemptWrap = { hidden: false };
        const totalWrap = { hidden: false };
        const elements = {
            "#trustReplyAttemptTimeout": attemptSelect,
            "#trustReplyTotalTimeout": totalSelect,
            "#trustReplyAttemptTimeoutCustom": attemptCustom,
            "#trustReplyTotalTimeoutCustom": totalCustom,
            "#trustReplyAttemptTimeoutCustomWrap": attemptWrap,
            "#trustReplyTotalTimeoutCustomWrap": totalWrap
        };
        const sandbox = {
            aiReplyState: {
                attemptTimeoutMode: "custom", totalTimeoutMode: "custom",
                attemptCustomSeconds: 45, totalCustomSeconds: 450
            },
            $: (selector) => elements[selector]
        };
        vm.createContext(sandbox);
        vm.runInContext(extractFn("integerSeconds"), sandbox);
        vm.runInContext(extractFn("resolveAiReplyTimeoutSelection"), sandbox);
        vm.runInContext(extractFn("syncAiReplyTimeoutControls"), sandbox);
        sandbox.syncAiReplyTimeoutControls();
        assert.strictEqual(auto.textContent, "自动（450 秒）");
        assert.strictEqual(JSON.stringify(sandbox.resolveAiReplyTimeoutSelection()), JSON.stringify({
            attemptTimeoutSeconds: 45, totalTimeoutSeconds: 450, totalPayload: 450
        }));
        sandbox.aiReplyState.attemptCustomSeconds = "bad";
        assert.throws(() => sandbox.resolveAiReplyTimeoutSelection(), /10–600/);
    });

    it("renders phase, activity, total TTL bar and accessible title", () => {
        let progress;
        const children = {
            ".ai-reply-progress-phase": { textContent: "" },
            ".ai-reply-progress-detail": { textContent: "" },
            ".ai-reply-progress-activity": { textContent: "" },
            ".ai-reply-progress-track": {
                value: 0,
                attrs: new Map(),
                setAttribute(name, value) { this.attrs.set(name, value); }
            }
        };
        const sandbox = {
            AI_REPLY_PROGRESS_PHASE_LABELS: { CALLING: "调用中" },
            AI_REPLY_PROVIDER_ACTIVITY_LABELS: { WRITING: "输出中" },
            $: () => progress,
            integerSeconds: (value) => Number.isInteger(Number(value)) ? Number(value) : null
        };
        progress = { querySelector: (selector) => children[selector] };
        vm.createContext(sandbox);
        vm.runInContext(extractFn("renderAiReplyProgress"), sandbox);
        sandbox.renderAiReplyProgress({
            phase: "CALLING", providerActivity: "WRITING", providerCallIndex: 1,
            attemptElapsedSeconds: 3, attemptTimeoutSeconds: 30,
            totalElapsedSeconds: 150, totalTimeoutSeconds: 300,
            providerEventCount: 4, contentChars: 12, secondsSinceProviderActivity: 2
        });
        assert.strictEqual(children[".ai-reply-progress-phase"].textContent, "调用中");
        assert.match(children[".ai-reply-progress-detail"].textContent, /本次 3\/30 秒/);
        assert.match(children[".ai-reply-progress-activity"].textContent, /输出中/);
        assert.strictEqual(children[".ai-reply-progress-track"].value, 50);
        assert.strictEqual(children[".ai-reply-progress-track"].attrs.get("aria-valuenow"), "50");
        assert.match(children[".ai-reply-progress-track"].title, /150\/300/);
    });

    it("creates a generation id through the UUID seam", () => {
        const sandbox = { crypto: { randomUUID: () => "uuid-from-browser" } };
        vm.createContext(sandbox);
        vm.runInContext(extractFn("createAiReplyGenerationId"), sandbox);
        assert.strictEqual(sandbox.createAiReplyGenerationId(), "uuid-from-browser");
    });

    it("loading overlay creates a stoppable control and restores controls on cleanup", () => {
        const control = {
            disabled: false,
            attrs: new Map(),
            hasAttribute(name) { return this.attrs.has(name); },
            setAttribute(name, value) { this.attrs.set(name, value); },
            getAttribute(name) { return this.attrs.get(name); },
            removeAttribute(name) { this.attrs.delete(name); }
        };
        const text = { textContent: "" };
        const stop = { disabled: true };
        const overlay = {
            attrs: new Map(),
            innerHTML: "",
            remove() { panel.overlay = null; },
            setAttribute(name, value) { this.attrs.set(name, value); },
            insertAdjacentHTML(_position, html) {
                this.hasProgress = true;
                this.hasStop = true;
            },
            querySelector(selector) {
                if (selector === ".ai-reply-loading-text") return text;
                if (selector === "[data-action='ai-reply-stop']") return this.hasStop ? stop : null;
                return null;
            }
        };
        const panel = {
            attrs: new Map(),
            overlay: null,
            setAttribute(name, value) { this.attrs.set(name, value); },
            querySelectorAll() { return [control]; },
            querySelector(selector) { return selector.startsWith(":scope") ? this.overlay : null; },
            appendChild(value) { this.overlay = value; },
        };
        const sandbox = {
            aiReplyState: { latestProgress: null },
            document: {
                createElement: () => {
                    overlay.hasStop = false;
                    overlay.hasProgress = false;
                    return overlay;
                }
            }
        };
        vm.createContext(sandbox);
        vm.runInContext(extractFn("setAiReplyLoading"), sandbox);
        sandbox.setAiReplyLoading(panel, true, "生成中", { stoppable: true, totalTimeoutSeconds: 600 });
        assert.strictEqual(panel.attrs.get("aria-busy"), "true");
        assert.strictEqual(control.disabled, true);
        assert.strictEqual(text.textContent, "生成中");
        assert.strictEqual(stop.disabled, false);
        sandbox.setAiReplyLoading(panel, false);
        assert.strictEqual(panel.attrs.get("aria-busy"), "false");
        assert.strictEqual(control.disabled, false);
        sandbox.setAiReplyLoading(panel, true, "训练中");
        assert.strictEqual(panel.overlay.hasProgress, false);
        assert.strictEqual(panel.overlay.hasStop, false);
        sandbox.setAiReplyLoading(panel, false);
    });

    it("posts generation id and both TTL values, then consumes terminal SSE", async () => {
        let request;
        let terminal;
        const sandbox = {
            contextPath: "",
            aiReplyState: { activeGeneration: { generationId: "generation-1" } },
            AbortController: class {
                constructor() { this.signal = {}; this.aborted = false; }
                abort() { this.aborted = true; }
            },
            setTimeout: () => 1,
            clearTimeout: () => {},
            TextDecoder,
            TextEncoder,
            handleAuthResponse: async () => {},
            fetch: async (url, options) => {
                request = { url, options };
                let index = 0;
                const chunks = [
                    new TextEncoder().encode('event: ready\ndata: {"generationId":"generation-1"}\n\n'),
                    new TextEncoder().encode('event: result\ndata: {"draftText":"done"}\n\n')
                ];
                return {
                    ok: true,
                    body: {
                        getReader() {
                            return {
                                async read() {
                                    if (index < chunks.length) return { done: false, value: chunks[index++] };
                                    return { done: true, value: new Uint8Array() };
                                },
                                async cancel() {}
                            };
                        }
                    }
                };
            },
            acceptAiReplyProgressSnapshot: () => {}
        };
        vm.createContext(sandbox);
        vm.runInContext(extractFn("parseAiReplySseFrames"), sandbox);
        vm.runInContext(extractFn("postAiReplySse"), sandbox);
        await sandbox.postAiReplySse(7, {
            generationId: "generation-1",
            llmAttemptTimeoutSeconds: 60,
            llmTotalTimeoutSeconds: 600,
            _resolvedTotalTimeoutSeconds: 600
        }, {
            onTerminal: (event, data) => { terminal = { event, data }; }
        });
        assert.match(request.url, /\/api\/mail\/unmatched-inbound\/7\/ai-reply\/turn-stream$/);
        assert.strictEqual(request.options.method, "POST");
        assert.strictEqual(request.options.headers.Accept, "text/event-stream");
        const body = JSON.parse(request.options.body);
        assert.strictEqual(body.generationId, "generation-1");
        assert.strictEqual(body.llmAttemptTimeoutSeconds, 60);
        assert.strictEqual(body.llmTotalTimeoutSeconds, 600);
        assert.strictEqual(Object.hasOwn(body, "_resolvedTotalTimeoutSeconds"), false);
        assert.strictEqual(terminal.event, "result");
        assert.strictEqual(terminal.data.draftText, "done");
    });

    it("consumes result, cancelled, and error terminal events and always cancels the reader", async () => {
        for (const event of ["result", "cancelled", "error"]) {
            let cancelled = 0;
            let terminal;
            const sandbox = {
                contextPath: "",
                aiReplyState: { activeGeneration: { generationId: "generation-1" } },
                AbortController: class { constructor() { this.signal = {}; } abort() {} },
                TextDecoder,
                TextEncoder,
                setTimeout: () => 1,
                clearTimeout: () => {},
                handleAuthResponse: async () => {},
                fetch: async () => ({
                    ok: true,
                    body: {
                        getReader() {
                            let read = false;
                            return {
                                async read() {
                                    if (read) return { done: true, value: new Uint8Array() };
                                    read = true;
                                    return {
                                        done: false,
                                        value: new TextEncoder().encode(`event: ${event}\ndata: {"generationId":"generation-1"}\n\n`)
                                    };
                                },
                                async cancel() { cancelled += 1; }
                            };
                        }
                    }
                }),
                acceptAiReplyProgressSnapshot: () => {}
            };
            vm.createContext(sandbox);
            vm.runInContext(extractFn("parseAiReplySseFrames"), sandbox);
            vm.runInContext(extractFn("postAiReplySse"), sandbox);
            await sandbox.postAiReplySse(7, { generationId: "generation-1" }, {
                onTerminal: (name, data) => { terminal = { name, data }; }
            });
            assert.strictEqual(terminal.name, event);
            assert.strictEqual(cancelled, 1);
        }
    });

    it("stop route cancels the active generation and clears progress state", async () => {
        let cancelledUrl;
        let aborted = false;
        let cleared = false;
        const sandbox = {
            aiReplyState: {
                activeGeneration: {
                    recordId: 7,
                    generationId: "generation-1",
                    controller: { abort: () => { aborted = true; } }
                },
                latestProgress: { progressSeq: 1 },
                progressTimerId: 1
            },
            api: async (url) => { cancelledUrl = url; return { status: "CANCEL_REQUESTED" }; },
            clearInterval: () => { cleared = true; }
        };
        vm.createContext(sandbox);
        vm.runInContext(extractFn("stopAiReplyProgressTicker"), sandbox);
        vm.runInContext(extractFn("requestAiReplyCancellation"), sandbox);
        vm.runInContext(extractFn("cancelActiveAiReplyGeneration"), sandbox);
        const result = await sandbox.cancelActiveAiReplyGeneration();
        assert.strictEqual(result.status, "CANCEL_REQUESTED");
        assert.match(cancelledUrl, /generations\/generation-1\/cancel$/);
        assert.strictEqual(aborted, true);
        assert.strictEqual(cleared, true);
        assert.strictEqual(sandbox.aiReplyState.activeGeneration, null);
        assert.strictEqual(sandbox.aiReplyState.latestProgress, null);
    });

    it("reset clears active generation, progress, draft entry, and adopt context through module cancel", () => {
        let cancelled = 0;
        let stopped = 0;
        const sandbox = {
            aiReplyState: {
                activeGeneration: { generationId: "g" }, latestProgress: { progressSeq: 3 },
                progressTimerId: 1, requestSeq: 2, recordId: 7, turns: [{ assistantDraft: "x" }],
                lastDraftTemplate: "raw", lastRenderedDraft: "rendered", lastQaRuleIds: [1],
                mode: "FREE_FORM", firstTurnDone: true, drafts: { 1: { raw: "x" } },
                nextDraftId: 1, adoptContext: { rawTemplate: "x" }, inFlight: true,
                progressReceivedAt: 10
            },
            cancelActiveAiReplyGeneration: () => { cancelled += 1; return Promise.resolve(); },
            stopAiReplyProgressTicker: () => { stopped += 1; },
            resetPreflightState: () => {}
        };
        vm.createContext(sandbox);
        vm.runInContext(extractFn("resetAiReplyState"), sandbox);
        sandbox.resetAiReplyState(9);
        assert.strictEqual(cancelled, 1);
        assert.strictEqual(stopped, 1);
        assert.strictEqual(sandbox.aiReplyState.recordId, 9);
        assert.strictEqual(sandbox.aiReplyState.activeGeneration, null);
        assert.strictEqual(sandbox.aiReplyState.latestProgress, null);
        assert.strictEqual(JSON.stringify(sandbox.aiReplyState.drafts), "{}");
        assert.strictEqual(sandbox.aiReplyState.adoptContext, null);
        assert.strictEqual(sandbox.aiReplyState.inFlight, false);
    });

    it("cancel state takeover preserves the newer generation and TOO_LATE preserves the current stream", async () => {
        let resolveCancel;
        let aborted = 0;
        const sandbox = {
            aiReplyState: {
                activeGeneration: { recordId: 7, generationId: "old", controller: { abort: () => { aborted += 1; } } },
                latestProgress: { progressSeq: 1 },
                progressTimerId: 1,
                drafts: { 1: { raw: "old" } },
                adoptContext: { rawTemplate: "old" }
            },
            api: () => new Promise((resolve) => { resolveCancel = resolve; }),
            clearInterval: () => {}
        };
        vm.createContext(sandbox);
        vm.runInContext(extractFn("stopAiReplyProgressTicker"), sandbox);
        vm.runInContext(extractFn("requestAiReplyCancellation"), sandbox);
        vm.runInContext(extractFn("cancelActiveAiReplyGeneration"), sandbox);
        const pending = sandbox.cancelActiveAiReplyGeneration();
        sandbox.aiReplyState.activeGeneration = {
            recordId: 8, generationId: "new", controller: { abort: () => { aborted += 1; } }
        };
        sandbox.aiReplyState.latestProgress = { progressSeq: 9 };
        const draftsBeforeTakeover = JSON.stringify(sandbox.aiReplyState.drafts);
        const adoptBeforeTakeover = JSON.stringify(sandbox.aiReplyState.adoptContext);
        resolveCancel({ status: "CANCEL_REQUESTED" });
        await pending;
        assert.strictEqual(sandbox.aiReplyState.activeGeneration.generationId, "new");
        assert.strictEqual(JSON.stringify(sandbox.aiReplyState.drafts), draftsBeforeTakeover);
        assert.strictEqual(JSON.stringify(sandbox.aiReplyState.adoptContext), adoptBeforeTakeover);
        assert.strictEqual(aborted, 1);

        const tooLate = createSandbox();
        let tooLateAborts = 0;
        tooLate.aiReplyState = {
            activeGeneration: { recordId: 7, generationId: "current", controller: { abort: () => { tooLateAborts += 1; } } },
            latestProgress: { progressSeq: 2 },
            progressTimerId: 1
        };
        tooLate.api = async () => ({ status: "TOO_LATE" });
        tooLate.clearInterval = () => {};
        vm.runInContext(extractFn("stopAiReplyProgressTicker"), tooLate);
        vm.runInContext(extractFn("requestAiReplyCancellation"), tooLate);
        vm.runInContext(extractFn("cancelActiveAiReplyGeneration"), tooLate);
        const result = await tooLate.cancelActiveAiReplyGeneration();
        assert.strictEqual(result.status, "TOO_LATE");
        assert.strictEqual(tooLate.aiReplyState.activeGeneration.generationId, "current");
        assert.deepStrictEqual(tooLate.aiReplyState.latestProgress, { progressSeq: 2 });
        assert.strictEqual(tooLateAborts, 0);
    });

    it("shared workbench owns timeout controls, SSE status, and fixed completion actions", () => {
        assert.match(workbenchJsSource, /data-role=\"attempt-timeout\"/);
        assert.match(workbenchJsSource, /data-role=\"total-timeout\"/);
        assert.doesNotMatch(workbenchJsSource, /data-action=\"generate-all\"/);
        assert.match(workbenchJsSource, /data-action=\"complete\"/);
        assert.doesNotMatch(workbenchJsSource, /完成率|百分比/);
    });

    it("syncs timeout inputs and rejects invalid custom values before any request", () => {
        const autoOption = { textContent: "" };
        const elements = {
            "#trustReplyAttemptTimeout": { value: "custom" },
            "#trustReplyTotalTimeout": { value: "auto", querySelector: () => autoOption },
            "#trustReplyAttemptTimeoutCustom": { value: "45" },
            "#trustReplyTotalTimeoutCustom": { value: "300" },
            "#trustReplyAttemptTimeoutCustomWrap": { hidden: true },
            "#trustReplyTotalTimeoutCustomWrap": { hidden: true }
        };
        const sandbox = {
            aiReplyState: {
                attemptTimeoutMode: "custom", attemptCustomSeconds: 45,
                totalTimeoutMode: "auto", totalCustomSeconds: 300
            },
            integerSeconds: (value) => Number.isInteger(Number(value)) ? Number(value) : null,
            $: (selector) => elements[selector]
        };
        vm.createContext(sandbox);
        vm.runInContext(extractFn("syncAiReplyTimeoutControls"), sandbox);
        vm.runInContext(extractFn("resolveAiReplyTimeoutSelection"), sandbox);
        sandbox.syncAiReplyTimeoutControls();
        assert.strictEqual(elements["#trustReplyAttemptTimeoutCustomWrap"].hidden, false);
        assert.strictEqual(autoOption.textContent, "自动（450 秒）");
        const resolved = sandbox.resolveAiReplyTimeoutSelection();
        assert.strictEqual(resolved.attemptTimeoutSeconds, 45);
        assert.strictEqual(resolved.totalTimeoutSeconds, 450);
        assert.strictEqual(resolved.totalPayload, null);
        sandbox.aiReplyState.attemptCustomSeconds = 9;
        assert.throws(() => sandbox.resolveAiReplyTimeoutSelection(), /10–600/);
    });

    it("executes stop action and preserves TOO_LATE stream state", async () => {
        const stopButton = { dataset: { action: "ai-reply-stop" }, disabled: false, textContent: "停止生成" };
        const panel = {};
        let loadingReset = 0;
        let status;
        const sandbox = {
            aiReplyState: { activeGeneration: { generationId: "g" }, inFlight: true },
            $: () => panel,
            cancelActiveAiReplyGeneration: async () => ({ status: "TOO_LATE" }),
            setAiReplyLoading: () => { loadingReset += 1; },
            updateAiReplyLoadingMessage: (message) => { status = message; },
            showStatus: (message) => { status = message; }
        };
        vm.createContext(sandbox);
        vm.runInContext(extractHandler("handleUnmatchedAction", "renderEmailAliasSection"), sandbox);
        await sandbox.handleUnmatchedAction(stopButton);
        assert.strictEqual(stopButton.disabled, false);
        assert.strictEqual(stopButton.textContent, "停止生成");
        assert.strictEqual(loadingReset, 0);
        assert.strictEqual(status, "生成已进入完成阶段，无法停止");
    });

    it("executes close-detail action against the actual detail panel state", async () => {
        const panel = { hidden: false };
        const sandbox = {
            state: { mailbox: { detailContext: { id: 7 } } },
            $: () => panel,
            unmountMailboxTrustReplyHosts: () => {}
        };
        vm.createContext(sandbox);
        vm.runInContext(extractHandler("handleUnmatchedAction", "renderEmailAliasSection"), sandbox);
        await sandbox.handleUnmatchedAction({ dataset: { action: "close-unmatched-detail" } });
        assert.strictEqual(panel.hidden, true);
        assert.strictEqual(sandbox.state.mailbox.detailContext, null);
    });

    it("parses every terminal SSE event without collapsing event names", () => {
        const sandbox = {};
        vm.createContext(sandbox);
        vm.runInContext(extractFn("parseAiReplySseFrames"), sandbox);
        const result = sandbox.parseAiReplySseFrames([
            ["ready", { generationId: "g" }],
            ["progress", { progressSeq: 1 }],
            ["heartbeat", { generationId: "g" }],
            ["result", { draftText: "done" }],
            ["error", { message: "failed" }],
            ["cancelled", { generationId: "g" }]
        ].map(([event, data]) => `event: ${event}\ndata: ${JSON.stringify(data)}\n\n`).join(""), true);
        assert.strictEqual(
            [...result.events].map((item) => item.event).join(","),
            "ready,progress,heartbeat,result,error,cancelled"
        );
    });

    it("extrapolates recent activity while attempt elapsed stays idle", () => {
        let rendered;
        const sandbox = {
            aiReplyState: {
                latestProgress: {
                    generationId: "g",
                    progressSeq: 1,
                    phase: "VALIDATING",
                    providerActivity: "IDLE",
                    providerCallIndex: 1,
                    attemptElapsedSeconds: 3,
                    attemptTimeoutSeconds: 30,
                    totalElapsedSeconds: 4,
                    totalTimeoutSeconds: 300,
                    providerEventCount: 2,
                    contentChars: 8,
                    secondsSinceProviderActivity: 5
                },
                progressReceivedAt: 1000,
                progressTimerId: null
            },
            performance: { now: () => 4000 },
            setInterval: (callback) => {
                sandbox.tick = callback;
                return 1;
            },
            clearInterval: () => {},
            renderAiReplyProgress: (snapshot) => { rendered = snapshot; }
        };
        vm.createContext(sandbox);
        vm.runInContext(extractFn("stopAiReplyProgressTicker"), sandbox);
        vm.runInContext(extractFn("startAiReplyProgressTicker"), sandbox);
        sandbox.startAiReplyProgressTicker();
        sandbox.tick();
        assert.strictEqual(rendered.attemptElapsedSeconds, 3);
        assert.strictEqual(rendered.secondsSinceProviderActivity, 8);
        assert.strictEqual(rendered.totalElapsedSeconds, 7);
    });

    it("rejects stale or foreign progress while accepting the active sequence", () => {
        let renders = 0;
        const sandbox = {
            aiReplyState: {
                activeGeneration: { generationId: "active" },
                lastProgressSeq: 0,
                latestProgress: null,
                progressReceivedAt: 0
            },
            AI_REPLY_PROGRESS_PHASE_LABELS: { CALLING: "calling" },
            AI_REPLY_PROVIDER_ACTIVITY_LABELS: { WAITING: "waiting" },
            performance: { now: () => 1000 },
            renderAiReplyProgress: () => { renders += 1; }
        };
        vm.createContext(sandbox);
        vm.runInContext(extractFn("integerSeconds"), sandbox);
        vm.runInContext(extractFn("normalizeAiReplyProgressSnapshot"), sandbox);
        vm.runInContext(extractFn("acceptAiReplyProgressSnapshot"), sandbox);
        const snapshot = {
            generationId: "active", progressSeq: 1, phase: "CALLING", providerActivity: "WAITING",
            providerCallIndex: 1, attemptElapsedSeconds: 1, attemptTimeoutSeconds: 30,
            totalElapsedSeconds: 1, totalTimeoutSeconds: 300, providerEventCount: 1,
            contentChars: 2, secondsSinceProviderActivity: 0
        };
        assert.strictEqual(sandbox.acceptAiReplyProgressSnapshot(snapshot), true);
        assert.strictEqual(sandbox.acceptAiReplyProgressSnapshot({ ...snapshot, progressSeq: 1 }), false);
        assert.strictEqual(sandbox.acceptAiReplyProgressSnapshot({ ...snapshot, progressSeq: 2, generationId: "stale" }), false);
        assert.strictEqual(renders, 1);
    });

    it("restores pre-existing disabled state when loading ends", () => {
        const button = {
            disabled: true,
            attrs: new Map([["data-ai-reply-was-disabled", "true"]]),
            hasAttribute(name) { return this.attrs.has(name); },
            getAttribute(name) { return this.attrs.get(name); },
            removeAttribute(name) { this.attrs.delete(name); }
        };
        const panel = {
            attrs: new Map(),
            setAttribute(name, value) { this.attrs.set(name, value); },
            querySelectorAll() { return [button]; },
            querySelector() { return null; }
        };
        const sandbox = { document: {} };
        vm.createContext(sandbox);
        vm.runInContext(extractFn("setAiReplyLoading"), sandbox);
        sandbox.setAiReplyLoading(panel, false);
        assert.strictEqual(panel.attrs.get("aria-busy"), "false");
        assert.strictEqual(button.disabled, true);
        assert.strictEqual(button.hasAttribute("data-ai-reply-was-disabled"), false);
    });
});

describe("ai reply loading helpers source contracts", () => {
    it("uses shared setAiReplyLoading and restores was-disabled markers", () => {
        assert.ok(appJsSource.includes("function setAiReplyLoading(panel, loading"));
        assert.ok(appJsSource.includes("data-ai-reply-was-disabled"));
        assert.ok(appJsSource.includes("setAiReplyLoading(panel, true)"));
        assert.ok(workbenchJsSource.includes("正在加载工作台"));
        assert.ok(workbenchJsSource.includes("requestSse"));
        assert.ok(workbenchJsSource.includes('aria-live="polite"'));
        assert.ok(workbenchJsSource.includes("state.generation.pending = true"));
    });

    it("keeps feedback and draft text isolated", () => {
        assert.ok(appJsSource.includes("function renderAiReplyFeedback("));
        assert.ok(appJsSource.includes("依据覆盖：完整"));
        assert.ok(!/已回答\s*\$\{/.test(appJsSource) && !appJsSource.includes("已回答 "));
        assert.ok(workbenchJsSource.includes("rawDraftText"));
        assert.ok(workbenchJsSource.includes('data-role="raw-preview"'));
        assert.ok(fs.readFileSync(indexHtmlPath, "utf-8").includes('id="aiTrainingTrustReplyHost"'));
        assert.ok(fs.readFileSync(indexHtmlPath, "utf-8").includes('id="aiTrainingEvaluationPanel"'));
        assert.ok(appJsSource.includes("mountAiTrainingTrustReply"));
    });

    it("routes display/copy/adopt to rendered and turns to raw template", () => {
        assert.match(appJsSource, /lastDraftTemplate:\s*""/);
        assert.match(appJsSource, /lastRenderedDraft:\s*""/);
        assert.doesNotMatch(appJsSource, /lastDraft:\s*""/);
        assert.match(
            appJsSource,
            /renderedDraftText \|\| assembly\.rawDraftText/
        );
        assert.match(appJsSource, /assistantDraft:\s*aiReplyState\.lastDraftTemplate/);
        assert.match(appJsSource, /aiReplyState\.lastDraftTemplate\s*=\s*rawDraft/);
        assert.match(appJsSource, /aiReplyState\.lastRenderedDraft\s*=\s*renderedDraft/);
        assert.match(appJsSource, /aiReplyState\.drafts\[draftId\]\s*=\s*\{[\s\S]*?needsGroundingReview/);
        assert.match(appJsSource, /entry\?\.rendered\s*\?\?\s*aiReplyState\.lastRenderedDraft/);
        assert.match(appJsSource, /editor\.innerText\s*=\s*rendered/);
        assert.doesNotMatch(appJsSource, /assistantDraft:\s*aiReplyState\.lastRenderedDraft/);
        assert.doesNotMatch(appJsSource, /assistantDraft:\s*aiReplyState\.lastDraft[^T]/);
    });

    it("preserves raw template across adopt→send only when editor matches baseline", () => {
        assert.match(appJsSource, /adoptContext:\s*null/);
        assert.match(appJsSource, /aiReplyState\.adoptContext\s*=\s*null/);
        assert.match(appJsSource, /rawTemplate:\s*raw\s*\|\|\s*""/);
        assert.match(appJsSource, /renderedBaselineHtml:\s*editor\s*\?\s*editor\.innerHTML/);
        assert.match(appJsSource, /templateTextBody\s*=\s*adopt\.rawTemplate/);
        const sendIdx = appJsSource.indexOf('if (action === "send-manual-rich-reply")');
        assert.ok(sendIdx > 0);
        const sendBlock = appJsSource.slice(sendIdx, sendIdx + 2200);
        assert.match(sendBlock, /editor\.innerText\.trim\(\)\s*===\s*\(adopt\.renderedBaseline/);
        assert.match(sendBlock, /editor\.innerHTML\s*===\s*\(adopt\.renderedBaselineHtml/);
        assert.match(sendBlock, /htmlBody:\s*editor\.innerHTML/);
        assert.match(sendBlock, /textBody:\s*editor\.innerText/);
        assert.doesNotMatch(fs.readFileSync(stylesCssPath, "utf-8"), /templateTextBody|adoptContext/);
        assert.doesNotMatch(fs.readFileSync(indexHtmlPath, "utf-8"), /templateTextBody|adoptContext/);
    });

    it("omits raw template when rich-format HTML changes without text change", () => {
        const adoptIdx = appJsSource.indexOf('if (action === "ai-adopt-draft")');
        assert.ok(adoptIdx > 0);
        const adoptBlock = appJsSource.slice(adoptIdx, adoptIdx + 1600);
        assert.match(adoptBlock, /renderedBaselineHtml/);
        const sendIdx = appJsSource.indexOf('if (action === "send-manual-rich-reply")');
        const sendBlock = appJsSource.slice(sendIdx, sendIdx + 2200);
        // Both text and HTML must match — HTML-only format edits must not pass raw.
        assert.match(sendBlock, /innerText\.trim\(\)\s*===\s*\(adopt\.renderedBaseline/);
        assert.match(sendBlock, /innerHTML\s*===\s*\(adopt\.renderedBaselineHtml/);
    });

    it("maps preview warning codes to Chinese labels", () => {
        const { AI_REPLY_WARNING_LABELS } = createSandbox();
        assert.strictEqual(
            AI_REPLY_WARNING_LABELS.AI_REPLY_PREVIEW_ACCOUNT_NOT_FOUND,
            "无法确定回信账号，变量预览未完全渲染"
        );
        assert.strictEqual(
            AI_REPLY_WARNING_LABELS.AI_REPLY_PREVIEW_INVALID_PLACEHOLDER,
            "草稿含未知变量占位符，已保留原文"
        );
    });

    it("sends mailRecordId with expertContactId when available", () => {
        assert.ok(appJsSource.includes("selectedSimulateMailRecordId"));
        assert.match(appJsSource, /source:\s*\{\s*sourceType:\s*"TRAINING_MAIL",\s*sourceId:\s*Number\(mail\.mailRecordId\)/);
        assert.ok(appJsSource.includes("selectedSimulateMailRecordId"));
        assert.ok(workbenchJsSource.includes("sourceId: Number(options.source.sourceId)"));
    });

    it("keeps S-1/S-2 CSS classes without tag-editor reuse", () => {
        const stylesSource = fs.readFileSync(stylesCssPath, "utf-8");
        assert.ok(stylesSource.includes(".ai-reply-loading-overlay"));
        assert.ok(stylesSource.includes(".ai-reply-loading-spinner"));
        assert.ok(stylesSource.includes("@keyframes ai-reply-spin"));
        assert.ok(stylesSource.includes(".ai-reply-feedback"));
        assert.ok(stylesSource.includes(".ai-reply-coverage"));
        assert.ok(stylesSource.includes(".ai-reply-warning"));
        assert.ok(stylesSource.includes(".ai-reply-error"));
        assert.ok(/\.ai-reply-section \.ai-chat-panel \{[\s\S]*?position:\s*relative;/.test(stylesSource));
        assert.ok(workbenchJsSource.includes("trust-reply-layout"));
        assert.ok(workbenchJsSource.includes("ai-reply-generation-controls"));
        assert.strictEqual((stylesSource.match(/\.ai-reply-loading-overlay\s*\{/g) || []).length, 1);
        assert.strictEqual((stylesSource.match(/\.ai-reply-progress-track\s*\{/g) || []).length, 1);
        assert.strictEqual((stylesSource.match(/\.ai-reply-stop-button\s*\{/g) || []).length, 1);
        assert.match(stylesSource, /\.ai-reply-loading-overlay\s*\{[\s\S]*?position:\s*absolute;[\s\S]*?inset:\s*0;/);
        assert.match(stylesSource, /\.ai-reply-progress-track\s*\{[\s\S]*?appearance:\s*none;[\s\S]*?background:/);
        assert.match(stylesSource, /\.ai-reply-stop-button\s*\{[\s\S]*?margin-top:\s*2px;/);
    });
});

describe("requestCoverage feedback helpers", () => {
    it("summarizes 4 grounded 1 partial 2 unsupported and formats fixed Chinese warnings", () => {
        const sandbox = createSandbox();
        const coverage = [
            { index: 1, requestText: "Q1", status: "GROUNDED" },
            { index: 2, requestText: "Q2", status: "GROUNDED" },
            { index: 3, requestText: "Q3", status: "GROUNDED" },
            { index: 4, requestText: "Q4", status: "GROUNDED" },
            { index: 5, requestText: "Deliverables?", status: "PARTIAL" },
            { index: 6, requestText: "Unknown A?", status: "UNSUPPORTED" },
            { index: 7, requestText: "Unknown B?", status: "UNSUPPORTED" }
        ];
        const summary = sandbox.summarizeAiReplyCoverage(coverage);
        assert.strictEqual(summary.grounded, 4);
        assert.strictEqual(summary.partial, 1);
        assert.strictEqual(summary.unsupported, 2);
        assert.strictEqual(summary.needsGroundingReview, true);
        const warnings = sandbox.formatAiReplyReviewWarnings(summary);
        assert.strictEqual(warnings.length, 3);
        assert.strictEqual(warnings[0], "第 5 项仅部分有已审核依据：Deliverables?；请人工补充后再发送。");
        assert.strictEqual(warnings[1], "第 6 项缺少已审核依据：Unknown A?；草稿未回答该项。");
        assert.strictEqual(warnings[2], "第 7 项缺少已审核依据：Unknown B?；草稿未回答该项。");
    });

    it("collapses whitespace truncates to 240 and escapes HTML in feedback", () => {
        const sandbox = createSandbox();
        const long = `  hello   ${"x".repeat(300)} <script>  `;
        const collapsed = sandbox.collapseAiReplyRequestText(long);
        assert.ok(!collapsed.includes("  "));
        assert.strictEqual(collapsed.length, 240);
        assert.ok(!collapsed.includes("<script>"));
        const container = { hidden: true, innerHTML: "" };
        sandbox.renderAiReplyFeedback(container, {
            requestCoverage: [
                { index: 1, requestText: "A <b>bold</b>", status: "UNSUPPORTED" }
            ],
            contextWarnings: [],
            unsupportedRequests: ["should-not-appear"]
        });
        assert.match(container.innerHTML, /依据覆盖：完整 0 项 · 部分 0 项 · 缺失 1 项/);
        assert.match(container.innerHTML, /第 1 项缺少已审核依据：A &lt;b&gt;bold&lt;\/b&gt;/);
        assert.doesNotMatch(container.innerHTML, /should-not-appear/);
        assert.doesNotMatch(container.innerHTML, /<b>bold<\/b>/);
    });

    it("falls back to unsupportedRequests when requestCoverage missing", () => {
        const sandbox = createSandbox();
        const container = { hidden: true, innerHTML: "" };
        sandbox.renderAiReplyFeedback(container, {
            requestCount: 2,
            groundedRequestCount: 1,
            contextWarnings: [],
            unsupportedRequests: ["No coverage field"]
        });
        assert.match(container.innerHTML, /事实覆盖 1\/2 项/);
        assert.match(container.innerHTML, /以下请求缺少已审核依据：No coverage field/);
    });

    it("maps structured invalid warning without leaking raw status tokens as body", () => {
        const sandbox = createSandbox();
        assert.strictEqual(
            sandbox.AI_REPLY_WARNING_LABELS.AI_REPLY_STRUCTURED_RESPONSE_INVALID,
            "模型返回格式无效，已使用审核依据生成结构化草稿。"
        );
        const container = { hidden: true, innerHTML: "" };
        sandbox.renderAiReplyFeedback(container, {
            requestCoverage: [{ index: 1, requestText: "Q", status: "WEIRD" }],
            contextWarnings: ["AI_REPLY_STRUCTURED_RESPONSE_INVALID"],
            unsupportedRequests: []
        });
        assert.match(container.innerHTML, /部分请求覆盖状态未知/);
        assert.doesNotMatch(container.innerHTML, />WEIRD</);
        assert.match(container.innerHTML, /模型返回格式无效/);
    });
});

describe("coverage isolation and send guard contracts", () => {
    it("binds review state per draft including draftReadiness and copies it on adopt", () => {
        const draftBubble = extractFn("appendAiChatDraftBubble");
        assert.match(draftBubble, /needsGroundingReview/);
        assert.match(draftBubble, /reviewItems/);
        assert.match(draftBubble, /draftReadiness/);
        assert.match(draftBubble, /采用此草稿/);
        assert.doesNotMatch(draftBubble, /采用并人工补充/);
        assert.doesNotMatch(draftBubble, /依据覆盖/);
        assert.doesNotMatch(draftBubble, /ai-reply-warning/);
        const adoptIdx = appJsSource.indexOf('if (action === "ai-adopt-draft")');
        const adoptBlock = appJsSource.slice(adoptIdx, adoptIdx + 1800);
        assert.match(adoptBlock, /needsGroundingReview:\s*!!entry\?\.needsGroundingReview/);
        assert.match(adoptBlock, /reviewItems:\s*Array\.isArray\(entry\?\.reviewItems\)/);
        assert.match(adoptBlock, /draftReadiness:\s*entry\?\.draftReadiness/);
        assert.match(adoptBlock, /requestCount:\s*Number\(entry\?\.requestCount\)/);
        assert.doesNotMatch(adoptBlock, /依据覆盖/);
    });

    it("send-manual-rich-reply submits directly without review modal or numbering gate", () => {
        const sendIdx = appJsSource.indexOf('if (action === "send-manual-rich-reply")');
        assert.ok(sendIdx > 0);
        const sendBlock = appJsSource.slice(sendIdx, sendIdx + 2200);
        assert.match(sendBlock, /submitManualRichReply/);
        assert.doesNotMatch(sendBlock, /openReviewModal/);
        assert.doesNotMatch(sendBlock, /requestBody\.replySource\s*=\s*"AI_DRAFT"/);
        assert.doesNotMatch(sendBlock, /ai-reply\/review-event/);
        assert.doesNotMatch(sendBlock, /validateSectionNumbering/);
        assert.doesNotMatch(sendBlock, /正文编号/);
        assert.doesNotMatch(appJsSource, /function validateSectionNumbering/);
    });

    it("quality panel omits deprecated review metric cards", () => {
        const panelFn = appJsSource.match(/function renderQaAuditPanel\([\s\S]*?\n\}/)?.[0] || "";
        assert.doesNotMatch(panelFn, /直发拦截/);
        assert.doesNotMatch(panelFn, /人工确认/);
        assert.match(panelFn, /遗漏率 \(BLOCKED\)/);
    });

    it("keeps coverage and readiness out of clipboard continuation and payload text paths", () => {
        assert.doesNotMatch(
            appJsSource,
            /sim\?\.renderedDraftText[\s\S]{0,80}依据覆盖/
        );
        assert.doesNotMatch(
            appJsSource,
            /assistantDraft:[\s\S]{0,80}requestCoverage/
        );
        assert.doesNotMatch(
            appJsSource,
            /templateTextBody[\s\S]{0,120}needsGroundingReview/
        );
        assert.doesNotMatch(
            appJsSource,
            /templateTextBody[\s\S]{0,120}draftReadiness/
        );
        assert.doesNotMatch(fs.readFileSync(stylesCssPath, "utf-8"), /needsGroundingReview|reviewItems/);
        assert.doesNotMatch(fs.readFileSync(indexHtmlPath, "utf-8"), /needsGroundingReview|reviewItems/);
        assert.match(fs.readFileSync(stylesCssPath, "utf-8"), /\.ai-reply-coverage/);
        assert.match(fs.readFileSync(indexHtmlPath, "utf-8"), /id="aiTrainingTrustReplyHost"/);
    });

    it("readiness text does not appear in body or template payloads", () => {
        assert.doesNotMatch(
            appJsSource,
            /templateTextBody[\s\S]{0,150}草稿状态/
        );
        assert.doesNotMatch(
            appJsSource,
            /draftText[\s\S]{0,150}草稿状态/
        );
    });
});

describe("draft readiness resolution and feedback", () => {
    it("prefers backend draftReadiness over local derivation", () => {
        const sandbox = createSandbox();
        assert.strictEqual(sandbox.resolveAiDraftReadiness({ draftReadiness: "READY" }, null), "READY");
        assert.strictEqual(sandbox.resolveAiDraftReadiness({ draftReadiness: "NEEDS_REVIEW" }, null), "NEEDS_REVIEW");
        assert.strictEqual(sandbox.resolveAiDraftReadiness({ draftReadiness: "BLOCKED" }, null), "BLOCKED");
    });

    it("falls back to coverageSummary when draftReadiness absent", () => {
        const sandbox = createSandbox();
        assert.strictEqual(
            sandbox.resolveAiDraftReadiness({}, { hasCoverage: true, grounded: 3, partial: 0, unsupported: 0, reviewItems: [] }),
            "READY"
        );
        assert.strictEqual(
            sandbox.resolveAiDraftReadiness({}, { hasCoverage: true, grounded: 0, partial: 2, unsupported: 0, reviewItems: [{ index: 1, status: "PARTIAL" }] }),
            "NEEDS_REVIEW"
        );
        assert.strictEqual(
            sandbox.resolveAiDraftReadiness({}, { hasCoverage: true, grounded: 0, partial: 0, unsupported: 1, reviewItems: [{ index: 1, status: "UNSUPPORTED" }] }),
            "BLOCKED"
        );
    });

    it("returns NEEDS_REVIEW when reviewItems exist but no hasCoverage", () => {
        const sandbox = createSandbox();
        assert.strictEqual(
            sandbox.resolveAiDraftReadiness({}, { hasCoverage: false, reviewItems: [{ index: 1, status: "UNKNOWN" }] }),
            "NEEDS_REVIEW"
        );
    });

    it("defaults to READY when no result and no coverage", () => {
        const sandbox = createSandbox();
        assert.strictEqual(sandbox.resolveAiDraftReadiness(null, null), "READY");
        assert.strictEqual(sandbox.resolveAiDraftReadiness({}, null), "READY");
    });

    it("shows readiness text in renderAiReplyFeedback for all three states", () => {
        const sandbox = createSandbox();

        const readyContainer = { hidden: true, innerHTML: "" };
        sandbox.renderAiReplyFeedback(readyContainer, {
            draftReadiness: "READY",
            requestCoverage: [{ index: 1, requestText: "Q", status: "GROUNDED" }],
            contextWarnings: [],
            unsupportedRequests: []
        });
        assert.match(readyContainer.innerHTML, /草稿状态：依据完整/);

        const needsReviewContainer = { hidden: true, innerHTML: "" };
        sandbox.renderAiReplyFeedback(needsReviewContainer, {
            draftReadiness: "NEEDS_REVIEW",
            requestCoverage: [{ index: 1, requestText: "Q", status: "PARTIAL" }],
            contextWarnings: [],
            unsupportedRequests: []
        });
        assert.match(needsReviewContainer.innerHTML, /草稿状态：部分问题需人工补充/);

        const blockedContainer = { hidden: true, innerHTML: "" };
        sandbox.renderAiReplyFeedback(blockedContainer, {
            draftReadiness: "BLOCKED",
            requestCoverage: [{ index: 1, requestText: "Q", status: "UNSUPPORTED" }],
            contextWarnings: [],
            unsupportedRequests: []
        });
        assert.match(blockedContainer.innerHTML, /草稿状态：存在缺少审核依据的问题，不可原样发送/);
    });

    it("falls back readiness from requestCoverage when draftReadiness absent in feedback", () => {
        const sandbox = createSandbox();
        const container = { hidden: true, innerHTML: "" };
        sandbox.renderAiReplyFeedback(container, {
            requestCoverage: [{ index: 1, requestText: "Q", status: "UNSUPPORTED" }],
            contextWarnings: [],
            unsupportedRequests: []
        });
        assert.match(container.innerHTML, /草稿状态：存在缺少审核依据的问题/);
    });

    // ── Failure behavior matrix (Phase 08 I-3/I-4/I-6) ──

    const transportWarnings = [
        { code: "AI_REPLY_LLM_TIMEOUT", text: "DeepSeek 请求超时" },
        { code: "AI_REPLY_LLM_RATE_LIMITED", text: "DeepSeek 请求过于频繁" },
        { code: "AI_REPLY_LLM_NETWORK_ERROR", text: "无法连接 DeepSeek" },
        { code: "AI_REPLY_LLM_PROVIDER_ERROR", text: "DeepSeek 服务异常" },
        { code: "AI_REPLY_LLM_EMPTY_RESPONSE", text: "DeepSeek 返回空内容" }
    ];

    transportWarnings.forEach(function(w) {
        it("shows failure banner for " + w.code, function() {
            var sandbox = createSandbox();
            var container = { hidden: true, innerHTML: "" };
            sandbox.renderAiReplyFeedback(container, {
                generationState: "FALLBACK_NO_RESPONSE",
                usedLlm: false,
                requestCount: 1,
                groundedRequestCount: 0,
                contextWarnings: [w.code],
                unsupportedRequests: []
            });
            assert.match(container.innerHTML, /class="ai-reply-failure-banner"/);
            assert.match(container.innerHTML, new RegExp(w.text));
            assert.match(container.innerHTML, /不可直接采用或发送/);
        });
    });

    it("shows failure banner for TRUST_REPAIR_EXHAUSTED", function() {
        var sandbox = createSandbox();
        var container = { hidden: true, innerHTML: "" };
        sandbox.renderAiReplyFeedback(container, {
            generationState: "FALLBACK_NO_RESPONSE",
            usedLlm: false,
            requestCount: 1,
            groundedRequestCount: 0,
            contextWarnings: ["AI_REPLY_TRUST_REPAIR_EXHAUSTED"],
            unsupportedRequests: []
        });
        assert.match(container.innerHTML, /class="ai-reply-failure-banner"/);
        assert.match(container.innerHTML, /结构与可信边界校验/);
    });

    it("uses fixed transport-before-trust warning priority regardless of input order", function() {
        var sandbox = createSandbox();
        assert.strictEqual(
            sandbox.resolveAiReplyFailureReasonFromResult({
                generationState: "FALLBACK_NO_RESPONSE",
                usedLlm: false,
                contextWarnings: ["AI_REPLY_TRUST_REPAIR_EXHAUSTED", "AI_REPLY_LLM_TIMEOUT"]
            }),
            "AI_REPLY_LLM_TIMEOUT"
        );
    });

    it("shows failure banner for FALLBACK_LLM_DISABLED without transport warning", function() {
        var sandbox = createSandbox();
        var container = { hidden: true, innerHTML: "" };
        sandbox.renderAiReplyFeedback(container, {
            generationState: "FALLBACK_LLM_DISABLED",
            usedLlm: false,
            requestCount: 1,
            groundedRequestCount: 0,
            contextWarnings: [],
            unsupportedRequests: []
        });
        assert.match(container.innerHTML, /class="ai-reply-failure-banner"/);
        assert.match(container.innerHTML, /LLM 生成失败.*LLM 功能未启用/);
    });

    it("shows failure banner for FALLBACK_CLIENT_UNAVAILABLE without transport warning", function() {
        var sandbox = createSandbox();
        var container = { hidden: true, innerHTML: "" };
        sandbox.renderAiReplyFeedback(container, {
            generationState: "FALLBACK_CLIENT_UNAVAILABLE",
            usedLlm: false,
            requestCount: 1,
            groundedRequestCount: 0,
            contextWarnings: [],
            unsupportedRequests: []
        });
        assert.match(container.innerHTML, /class="ai-reply-failure-banner"/);
        assert.match(container.innerHTML, /LLM 生成失败.*客户端不可用/);
    });

    it("shows failure banner for FALLBACK_NO_RESPONSE with no warnings", function() {
        var sandbox = createSandbox();
        var container = { hidden: true, innerHTML: "" };
        sandbox.renderAiReplyFeedback(container, {
            generationState: "FALLBACK_NO_RESPONSE",
            usedLlm: false,
            requestCount: 1,
            groundedRequestCount: 0,
            contextWarnings: [],
            unsupportedRequests: []
        });
        assert.match(container.innerHTML, /class="ai-reply-failure-banner"/);
        assert.match(container.innerHTML, /LLM 生成失败.*未返回有效内容/);
    });

    it("LLM_USED shows no failure banner and shows success label", function() {
        var sandbox = createSandbox();
        var container = { hidden: true, innerHTML: "" };
        sandbox.renderAiReplyFeedback(container, {
            generationState: "LLM_USED",
            usedLlm: true,
            requestCount: 1,
            groundedRequestCount: 1,
            contextWarnings: [],
            unsupportedRequests: []
        });
        assert.doesNotMatch(container.innerHTML, /class="ai-reply-failure-banner"/);
        assert.doesNotMatch(container.innerHTML, /LLM 生成失败/);
        assert.match(container.innerHTML, /模型已生成/);
    });

    it("success after failure clears banner", function() {
        var sandbox = createSandbox();
        var container = { hidden: true, innerHTML: "" };
        sandbox.renderAiReplyFeedback(container, {
            generationState: "FALLBACK_NO_RESPONSE",
            usedLlm: false,
            requestCount: 1,
            groundedRequestCount: 0,
            contextWarnings: ["AI_REPLY_LLM_TIMEOUT"],
            unsupportedRequests: []
        });
        assert.match(container.innerHTML, /class="ai-reply-failure-banner"/);

        sandbox.renderAiReplyFeedback(container, {
            generationState: "LLM_USED",
            usedLlm: true,
            requestCount: 1,
            groundedRequestCount: 1,
            contextWarnings: [],
            unsupportedRequests: []
        });
        assert.doesNotMatch(container.innerHTML, /class="ai-reply-failure-banner"/);
        assert.match(container.innerHTML, /模型已生成/);
    });
});
