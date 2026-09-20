// Runs only as a ContactOut content script. It never reveals contact details or changes pagination.
(() => {
  'use strict';
  if (globalThis.__contactOutManualAutopilot || !globalThis.ContactOutExport || !globalThis.expandContactOutReviewDetails || !globalThis.markContactOutIgnored) return;
  globalThis.__contactOutManualAutopilot = true;
  const RULES_KEY = 'contactout-ignore-rules-v1';
  const ENTERPRISES_KEY = 'contactout-enterprise-ratings-v1';
  const ONLINE_KEY = 'contactout-online-rating-v1';
  const api = globalThis.ContactOutExport;
  let timer = null;
  let running = false;
  let suppressUntil = 0;
  let lastSignature = '';

  function resultSignature() {
    if (typeof globalThis.contactOutReviewSignature === 'function') return globalThis.contactOutReviewSignature();
    return [...document.querySelectorAll('a[href*="linkedin.com/in/"]')]
      .filter(anchor => anchor.getClientRects().length)
      .map(anchor => anchor.href).join('|');
  }
  function schedule(delay = 900, force = false) {
    clearTimeout(timer);
    timer = setTimeout(() => run(force), delay);
  }
  async function run(force = false) {
    if (running) { schedule(250, force); return; }
    if (Date.now() < suppressUntil) { schedule(suppressUntil - Date.now() + 30, force); return; }
    const signature = resultSignature();
    if (!signature || (!force && signature === lastSignature)) return;
    running = true;
    try {
      const saved = await chrome.storage.local.get([RULES_KEY, ENTERPRISES_KEY, ONLINE_KEY]);
      // Direct online scoring owns its own explicit popup workflow and must not race manual rules.
      if (saved[ONLINE_KEY]?.enabled === true) {
        globalThis.clearContactOutIgnoreMarks();
        lastSignature = signature;
        return;
      }
      const rules = api.validateIgnoreConfig(saved[RULES_KEY] || api.defaultIgnoreConfig());
      const enterprises = api.validateEnterpriseConfig(saved[ENTERPRISES_KEY] || api.defaultEnterpriseConfig());
      await globalThis.expandContactOutReviewDetails();
      globalThis.markContactOutIgnored(rules, enterprises);
      lastSignature = resultSignature();
    } catch {
      // Do not surface background errors or retry automatically; manual popup controls remain available.
    } finally {
      running = false;
      suppressUntil = Date.now() + 1000;
    }
  }

  const observer = new MutationObserver(() => {
    if (running) { schedule(250); return; }
    if (Date.now() < suppressUntil) { schedule(suppressUntil - Date.now() + 30); return; }
    const signature = resultSignature();
    if (signature && signature !== lastSignature) schedule();
  });
  function start() {
    observer.observe(document.documentElement, { childList: true, subtree: true });
    chrome.storage.onChanged.addListener((changes, area) => {
      if (area !== 'local') return;
      if (changes[ONLINE_KEY]?.newValue?.enabled === true) {
        clearTimeout(timer);
        globalThis.clearContactOutIgnoreMarks();
        lastSignature = resultSignature();
        suppressUntil = Date.now() + 1000;
        return;
      }
      if ([RULES_KEY, ENTERPRISES_KEY, ONLINE_KEY].some(key => changes[key])) schedule(80, true);
    });
    schedule(700);
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', start, { once: true });
  else start();
})();
