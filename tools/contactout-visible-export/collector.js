// Injected on user action. Highlighting reads visible titles; Capture may expand details.
(function () {
  'use strict';
  const api = globalThis.ContactOutExport;
  const selector = 'input[type="checkbox"], [role="checkbox"]';
  const ignored = 'script,style,noscript,template,textarea,input,select,[hidden],[aria-hidden="true"],[data-contactout-review-badge]';
  function rendered(element) {
    if (!element || element.closest(ignored)) return false;
    for (let current = element; current; current = current.parentElement) {
      if (current.tagName === 'DETAILS' && !current.open) {
        const summary = [...current.children].find(child => child.tagName === 'SUMMARY');
        if (!summary?.contains(element)) return false;
      }
      const style = getComputedStyle(current);
      if (style.display === 'none' || style.visibility === 'hidden' || style.visibility === 'collapse' || Number(style.opacity) === 0) return false;
    }
    return !!element.getClientRects().length;
  }
  function visibleText(element) {
    const parts = [];
    const walker = document.createTreeWalker(element, NodeFilter.SHOW_TEXT);
    while (walker.nextNode()) {
      const node = walker.currentNode;
      if (!node.textContent.trim() || !rendered(node.parentElement)) continue;
      // Excludes unrendered nodes, including closed details and hidden descendants.
      const range = document.createRange();
      range.selectNodeContents(node);
      if (range.getClientRects().length) parts.push(node.textContent.trim());
    }
    return parts.join('\n');
  }
  function linksIn(element) {
    return [...element.querySelectorAll('a[href]')].filter(rendered).filter(anchor => {
      try {
        const url = new URL(anchor.href, location.href);
        return /(^|\.)linkedin\.com$/i.test(url.hostname) && /^\/in\/[^/]+/.test(url.pathname);
      } catch { return false; }
    });
  }
  function checkboxCount(element) {
    // Some components contain both role=checkbox and a nested native checkbox.
    return [...element.querySelectorAll(selector)].filter(box => !box.parentElement.closest(selector)).length;
  }
  function isCardText(text) {
    const lines = text.split('\n').filter(Boolean);
    return lines.length >= 3 && lines.some(line => !/@/.test(line) && /[a-z\u3400-\u9fff]{2}/i.test(line));
  }
  function findCard(seed, requireEmail = true) {
    for (let node = seed.parentElement; node && node !== document.body; node = node.parentElement) {
      const count = checkboxCount(node);
      if (count > 1) return null; // Never combine multiple people into one row.
      const text = visibleText(node);
      if ((requireEmail && !api.emailsFromText(text).length) || !isCardText(text)) continue;
      if (count === 1 && (linksIn(node).length === 1 || /\bat\b[\s\S]{0,240}\b(?:19|20)\d{2}/i.test(text))) return node;
      if ((node.matches('article,li,[role="listitem"]') || /(?:profile|person|contact)[-_]?card/i.test(node.className || '')) && linksIn(node).length === 1) return node;
    }
    return null;
  }
  function parseCard(card, index) {
    const profileText = visibleText(card);
    const emails = api.emailsFromText(profileText);
    const anchors = linksIn(card);
    const linkedNames = anchors.map(anchor => visibleText(anchor)).filter(text => text && !/^(in|linkedin|profile|view.*)$/i.test(text) && text.length < 100);
    const fields = api.parseProfileText(profileText, linkedNames[0]);
    const linkedin = anchors.length ? (() => { const url = new URL(anchors[0].href); return url.origin + url.pathname.replace(/\/$/, ''); })() : '';
    const now = new Date().toISOString();
    return { ...fields, linkedin, emails, source: 'ContactOut', sourceUrl: location.origin + location.pathname, firstCapturedAt: now, capturedAt: now, needsReview: true, profileText, cardIndex: index + 1 };
  }
  function findVisibleCards() {
    if (!/(^|\.)contactout\.com$/i.test(location.hostname)) throw new Error('请在 ContactOut 搜索结果页使用。');
    const seeds = [];
    const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
    while (walker.nextNode()) {
      const node = walker.currentNode;
      if (api.emailsFromText(node.textContent).length && rendered(node.parentElement)) seeds.push(node);
    }
    const cards = new Set();
    let unmatched = 0;
    for (const seed of seeds) {
      const card = findCard(seed);
      if (card) cards.add(card); else unmatched++;
    }
    return { cards: [...cards], unmatched };
  }
  function moreButton(card) {
    return [...card.querySelectorAll('button, [role="button"]')].find(button =>
      rendered(button) && !button.disabled && button.getAttribute('aria-disabled') !== 'true' &&
      /^(?:[.\u2026\s]*more|show\s+more)[\s\u2304\u25be\u25bc]*$/i.test(visibleText(button).trim()));
  }
  globalThis.collectContactOutVisible = function () {
    const { cards, unmatched } = findVisibleCards();
    const rows = cards.map(parseCard).filter(row => row.emails.length);
    return { rows, unmatched, scannedAt: new Date().toISOString() };
  };
  globalThis.expandAndCollectContactOutVisible = async function () {
    const targets = findVisibleCards().cards.map(card => ({
      emails: api.emailsFromText(visibleText(card)),
      linkedin: linksIn(card)[0]?.href || ''
    }));
    let expanded = 0;
    let expansionFailed = 0;
    const pause = ms => new Promise(resolve => setTimeout(resolve, ms));
    for (const target of targets) {
      // Re-resolve after each click: React may replace the original card nodes.
      const resolveCard = () => findVisibleCards().cards.find(card => target.linkedin
        ? linksIn(card).some(link => link.href === target.linkedin)
        : api.emailsFromText(visibleText(card)).some(email => target.emails.includes(email)));
      const card = resolveCard();
      if (!card) { expansionFailed++; continue; }
      const button = moreButton(card);
      if (!button) continue; // Already expanded, or no supported detail control.
      const before = visibleText(card);
      try {
        button.click();
        let complete = false;
        let previousText = before;
        let stableSince = Date.now();
        const deadline = Date.now() + 2000;
        while (Date.now() < deadline) {
          await pause(100);
          const current = resolveCard();
          if (!current) continue;
          const text = visibleText(current);
          if (text !== previousText) { previousText = text; stableSince = Date.now(); }
          if (!moreButton(current) && text !== before && Date.now() - stableSince >= 300) {
            complete = true;
            break;
          }
        }
        if (complete) expanded++; else expansionFailed++;
      } catch { expansionFailed++; }
    }
    return { ...globalThis.collectContactOutVisible(), expanded, expansionFailed };
  };
  function profileCardsForReview() {
    const cards = new Set();
    for (const seed of document.querySelectorAll(selector + ',a[href*="linkedin.com/in/"]')) {
      // Native checkboxes may be visually hidden inside a rendered custom control.
      if (!rendered(seed) && !rendered(seed.parentElement)) continue;
      const card = findCard(seed, false);
      if (card) cards.add(card);
    }
    return [...cards].filter(card => ![...cards].some(other => other !== card && card.contains(other)));
  }
  // Analysis may expand visible employment history, but never email/phone or any other card control.
  globalThis.expandContactOutReviewDetails = async function () {
    const targets = profileCardsForReview().map(card => {
      const link = linksIn(card)[0];
      const text = visibleText(card);
      return { linkedin: link?.href || '', name: link ? visibleText(link) : '', fingerprint: text.slice(0, 240) };
    });
    let expanded = 0;
    let expansionFailed = 0;
    const pause = ms => new Promise(resolve => setTimeout(resolve, ms));
    for (const target of targets) {
      const resolveCard = () => profileCardsForReview().find(card => {
        if (target.linkedin) return linksIn(card).some(link => link.href === target.linkedin);
        const text = visibleText(card);
        return target.name ? text.includes(target.name) : text.includes(target.fingerprint);
      });
      const card = resolveCard();
      if (!card) { expansionFailed++; continue; }
      const button = moreButton(card);
      if (!button) continue; // Already expanded, or no supported detail control.
      const before = visibleText(card);
      try {
        button.click();
        let complete = false;
        let previousText = before;
        let stableSince = Date.now();
        const deadline = Date.now() + 2000;
        while (Date.now() < deadline) {
          await pause(100);
          const current = resolveCard();
          if (!current) continue;
          const text = visibleText(current);
          if (text !== previousText) { previousText = text; stableSince = Date.now(); }
          if (!moreButton(current) && text !== before && Date.now() - stableSince >= 300) {
            complete = true;
            break;
          }
        }
        if (complete) expanded++; else expansionFailed++;
      } catch { expansionFailed++; }
      await pause(80);
    }
    return { scanned: targets.length, expanded, expansionFailed };
  };
  globalThis.readContactOutCompanyFilters = function () {
    if (!/(^|\.)contactout\.com$/i.test(location.hostname)) throw new Error('请在 ContactOut 搜索结果页使用。');
    const names = new Set();
    // A company filter label, never names inferred from expert result cards.
    const labels = [...document.querySelectorAll('label,legend,h3,h4,div,span,p')]
      .filter(node => rendered(node) && /^(Company|Companies|企业|公司)$/i.test(node.textContent.trim()) &&
        ![...node.children].some(child => /^(Company|Companies|企业|公司)$/i.test(child.textContent.trim())));
    for (const label of labels) {
      let group = label.parentElement;
      for (let depth = 0; group && depth < 3; depth++, group = group.parentElement) {
        const otherField = [...group.querySelectorAll('label,legend,h3,h4,div,span,p')].some(node => node !== label && /^(Job title|Name|Seniority|Location|Skills|Job Function|岗位|姓名|地点)$/i.test(node.textContent.trim()));
        if (otherField) break;
        if (group.querySelector('a[href*="linkedin.com/in/"],h3:not(:first-child)') || group.querySelectorAll(selector).length > 1) break;
        const tokens = [...group.querySelectorAll('[class*="multiValue"],[class*="MultiValue"],[class*="chip"],[class*="Chip"],[data-value],[aria-selected="true"]')]
          .filter(rendered).filter(node => !node.closest('[role="listbox"]'));
        const textTokens = tokens.filter(node => !tokens.some(other => other !== node && other.contains(node)));
        for (const token of textTokens) {
          const clone = token.cloneNode(true);
          clone.querySelectorAll('button,[role="button"],svg,[aria-label*="remove" i]').forEach(n => n.remove());
          const name = clone.textContent.replace(/\s*[×✕]\s*$/, '').replace(/\s+/g, ' ').trim();
          if (name.length >= 2 && name.length <= 200 && !/@|select company|current or past/i.test(name)) names.add(name);
        }
        if (names.size) break;
      }
    }
    return { companies: [...names], url: location.href };
  };
  globalThis.markContactOutForCompanySnapshot = function (snapshot, roles, enterprises) {
    const current = globalThis.readContactOutCompanyFilters();
    if (current.url !== snapshot.url || JSON.stringify(current.companies) !== JSON.stringify(snapshot.companies)) return { stale: true };
    return globalThis.markContactOutIgnored(roles, enterprises);
  };
  function visibleJobs(card) {
    const candidates = [card, ...card.querySelectorAll('div,p,li,span')]
      .filter(rendered).map(node => ({ node, job: api.parseEmploymentText(visibleText(node)) }))
      .filter(item => item.job);
    // Keep the smallest complete employment blocks. Never search skills or biographies.
    return candidates.filter(item => !candidates.some(other => other !== item && item.node.contains(other.node)))
      .map(item => item.job);
  }
  function hasUnparsedEmployment(card) {
    const candidates = [card, ...card.querySelectorAll('div,p,li,span')].filter(rendered)
      .map(node => ({ node, text: visibleText(node).replace(/\s+/g, ' ').trim() }))
      .filter(({ text }) => !/^skills?\s*:/i.test(text) && (/\bat\b/i.test(text) ||
        (/\b(engineer|scientist|chemist|research|manager|director|operator|intern|trainee|technician|fellow|officer|developer|recruiter|assistant|technologist|head)\b|工程|研发|研究|经理|总监|实习|技术/i.test(text) &&
        /\bPresent\b|[-–—]\s*(?:19|20)\d{2}\b/i.test(text))));
    return candidates.filter(item => !candidates.some(other => other !== item && item.node.contains(other.node)))
      .some(item => !api.parseEmploymentText(item.text));
  }
  function clearReviewMarks() {
    for (const badge of document.querySelectorAll('[data-contactout-review-badge]')) badge.remove();
    for (const card of document.querySelectorAll('[data-contactout-ignore]')) card.removeAttribute('data-contactout-ignore');
    for (const card of document.querySelectorAll('[data-contactout-review]')) card.removeAttribute('data-contactout-review');
  }
  function clearCardReviewMark(card) {
    for (const badge of card.querySelectorAll('[data-contactout-review-badge]')) badge.remove();
    card.removeAttribute('data-contactout-ignore');
    card.removeAttribute('data-contactout-review');
  }
  function ensureReviewStyle() {
    if (document.getElementById('contactout-review-style')) return;
    const style = document.createElement('style');
    style.id = 'contactout-review-style';
    style.textContent = '[data-contactout-ignore="yes"]{background-color:#fff1f2!important;outline:2px solid #dc2626!important;outline-offset:-2px!important}' +
      '[data-contactout-review-badge]{display:block!important;box-sizing:border-box!important;margin:8px!important;padding:8px 10px!important;border-radius:6px!important;background:#fee2e2!important;color:#991b1b!important;font:600 13px/1.5 system-ui,sans-serif!important;white-space:normal!important}' +
      '[data-contactout-review="review"]{background-color:#fffbeb!important;outline:2px solid #d97706!important;outline-offset:-2px!important}[data-contactout-review="review"] [data-contactout-review-badge]{background:#fef3c7!important;color:#92400e!important}';
    document.head.append(style);
  }
  globalThis.contactOutReviewSignature = function () {
    return profileCardsForReview().map(card => {
      const linkedin = linksIn(card)[0]?.href || '';
      return linkedin + '\u0001' + visibleText(card);
    }).join('\u0002');
  };
  globalThis.clearContactOutIgnoreMarks = function () {
    const state = globalThis.__contactoutReviewState;
    if (state?.observer) state.observer.disconnect();
    if (state?.timer) clearTimeout(state.timer);
    globalThis.__contactoutReviewState = null;
    clearReviewMarks();
    document.getElementById('contactout-review-style')?.remove();
    return { cleared: true };
  };
  globalThis.markContactOutIgnored = function (input = api.defaultIgnoreConfig(), enterpriseInput) {
    if (!/(^|\.)contactout\.com$/i.test(location.hostname)) throw new Error('请在 ContactOut 搜索结果页使用。');
    const config = api.validateIgnoreConfig(input);
    const enterpriseConfig = enterpriseInput === undefined ? undefined : api.validateEnterpriseConfig(enterpriseInput);
    const previousState = globalThis.__contactoutReviewState;
    if (previousState?.observer) previousState.observer.disconnect();
    if (previousState?.timer) clearTimeout(previousState.timer);
    ensureReviewStyle();
    const cards = profileCardsForReview();
    const visibleCards = new Set(cards);
    for (const card of document.querySelectorAll('[data-contactout-ignore],[data-contactout-review]')) {
      if (!visibleCards.has(card)) clearCardReviewMark(card);
    }
    let marked = 0;
    let unknown = 0;
    let kept = 0;
    for (const card of cards) {
      const jobs = visibleJobs(card);
      const current = jobs.filter(job => job.current);
      const visiblePresentCount = (visibleText(card).match(/\bPresent\b/gi) || []).length;
      const incomplete = !current.length || current.length < visiblePresentCount ||
        (config.schemaVersion >= 2 && (Boolean(moreButton(card)) || hasUnparsedEmployment(card)));
      const result = api.classifyProfileJobs(jobs, config, { incomplete, ...(enterpriseConfig ? { enterpriseConfig } : {}) });
      if (result.status === 'keep') {
        kept++;
        clearCardReviewMark(card);
        continue;
      }
      if (result.status === 'review') unknown++; else marked++;
      if (result.status === 'review' && config.schemaVersion === 1) {
        clearCardReviewMark(card);
        continue;
      }
      const label = result.status === 'review' ? '待核实' : config.schemaVersion === 1 ? '本轮可忽略' : '可忽略候选';
      const text = label + '（' + config.scope + '）：' + result.reason;
      const attribute = result.status === 'review' ? 'data-contactout-review' : 'data-contactout-ignore';
      const value = result.status === 'review' ? 'review' : 'yes';
      const existing = card.querySelector(':scope > [data-contactout-review-badge]');
      if (card.getAttribute(attribute) === value && existing?.textContent === text &&
        !card.hasAttribute(attribute === 'data-contactout-review' ? 'data-contactout-ignore' : 'data-contactout-review')) continue;
      clearCardReviewMark(card);
      card.setAttribute(attribute, value);
      const badge = document.createElement('div');
      badge.setAttribute('data-contactout-review-badge', '');
      badge.textContent = text;
      card.prepend(badge);
    }
    globalThis.__contactoutReviewState = {};
    return { scanned: cards.length, marked, unknown, kept };
  };
})();
