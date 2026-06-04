const contextPath = (() => {
  const scriptPath = new URL(document.currentScript.src).pathname;
  return scriptPath.endsWith('/simulator.js')
    ? scriptPath.slice(0, -'/simulator.js'.length)
    : '';
})();
const API = `${contextPath}/api/simulator`;
let currentContactId = null;
let presets = [];
let stats = { pass: 0, fail: 0, neutral: 0 };

const STATUS_NODES = [
  { id: 'NEW', x: 60, y: 40 },
  { id: 'INTRO_SENT', x: 180, y: 40 },
  { id: 'WAITING_REPLY', x: 320, y: 40 },
  { id: 'QA_AUTO_REPLIED', x: 480, y: 40 },
  { id: 'MEETING_SCHEDULING', x: 320, y: 140 },
  { id: 'MEETING_SCHEDULED', x: 480, y: 140 },
  { id: 'MEETING_DONE', x: 640, y: 140 },
  { id: 'MANUAL_HANDOFF', x: 320, y: 240 }
];

async function init() {
  try {
    const resp = await fetch(`${API}/presets`);
    if (!resp.ok) { showUnavailable(); return; }
    presets = await resp.json();
  } catch (e) {
    showUnavailable();
    return;
  }
  renderPresetBar(presets);
  fillEnumSelects(presets);
  await refreshContactList();
  bindEvents();
}

function showUnavailable() {
  document.querySelector('#sim-center').innerHTML =
    '<h2>模拟器未启用</h2><p>请联系运维启用 simulator profile。</p>';
  document.querySelector('#sim-contacts').innerHTML = '';
  document.querySelector('#sim-right').innerHTML = '';
}

async function refreshContactList() {
  const list = await (await fetch(`${API}/contacts`)).json();
  document.querySelector('#contact-list').innerHTML = list
    .map(c => `<li data-id="${c.id}">${esc(c.expertName ?? c.expertEmail)} <small>${c.currentStatus}</small></li>`)
    .join('');
  document.querySelectorAll('#contact-list li').forEach(li => {
    li.onclick = () => selectContact(Number(li.dataset.id));
  });
}

async function selectContact(id) {
  currentContactId = id;
  document.querySelector('#btn-reset').disabled = false;
  [...document.querySelectorAll('#contact-list li')].forEach(li => {
    li.style.background = Number(li.dataset.id) === id ? '#dbeafe' : '';
  });
  await refreshSnapshot();
}

async function refreshSnapshot() {
  if (!currentContactId) return;
  const snap = await (await fetch(`${API}/contacts/${currentContactId}/snapshot`)).json();
  renderStateMachine(snap.contact.currentStatus);
  renderTimeline(snap);
}

function renderStateMachine(currentStatus) {
  const svg = document.querySelector('#state-machine-svg');
  const w = 780, h = 300;
  let html = `<svg width="${w}" height="${h}" viewBox="0 0 ${w} ${h}">`;
  STATUS_NODES.forEach(n => {
    const cls = `sim-state-node${n.id === currentStatus ? ' current' : ''}`;
    html += `<rect x="${n.x}" y="${n.y}" rx="6" ry="6" width="130" height="30" class="${cls}"/>`;
    html += `<text x="${n.x + 65}" y="${n.y + 20}" text-anchor="middle" font-size="11">${n.id}</text>`;
  });
  html += '</svg>';
  svg.innerHTML = html;
}

function renderTimeline(snap) {
  const items = [];
  snap.statusHistory.forEach(h => items.push({
    ts: h.createdAt, kind: 'status',
    text: `${h.fromStatus ?? '—'} &rarr; ${h.toStatus} (${h.source}: ${h.reason})`
  }));
  snap.mails.forEach(m => items.push({
    ts: m.createdAt, kind: m.direction.toLowerCase(),
    text: `[${m.mailType}] ${m.subject ?? ''}`,
    body: m.cleanedBody ?? m.body
  }));
  snap.inboundIntents.forEach(i => items.push({
    ts: i.createdAt, kind: 'inbound',
    text: `intent=${i.intentCode} action=${i.autoAction} kw=${i.matchedKeywords ?? '-'}`
  }));
  if (snap.latestHandoff) items.push({
    ts: snap.latestHandoff.updatedAt, kind: 'handoff',
    text: `${snap.latestHandoff.handoffStatus} - ${snap.latestHandoff.reason}`
  });
  snap.meetingSchedules.forEach(ms => items.push({
    ts: ms.updatedAt, kind: 'meeting',
    text: `${ms.meetingStatus} ${ms.chinaTime ?? ''} ${ms.meetingTool ?? ''}`
  }));
  items.sort((a, b) => (a.ts ?? '').localeCompare(b.ts ?? ''));
  document.querySelector('#timeline').innerHTML = items.map(it =>
    `<div class="sim-timeline-item ${it.kind}"><time>${it.ts}</time> ${it.text}${it.body ? `<details><summary>body</summary><pre>${esc(it.body)}</pre></details>` : ''}</div>`
  ).join('');
}

function renderPresetBar(presets) {
  document.querySelector('#preset-bar').innerHTML = presets.map(p =>
    `<button class="preset-btn" data-key="${p.key}" title="${p.label}">${p.label}</button>`
  ).join('');
}

function fillEnumSelects(presets) {
  const intentSet = new Set(), actionSet = new Set(), statusSet = new Set();
  presets.forEach(p => {
    intentSet.add(p.expectedIntent);
    actionSet.add(p.expectedAutoAction);
    statusSet.add(p.expectedNewStatus);
  });
  fillSelect('#exp-intent', intentSet);
  fillSelect('#exp-action', actionSet);
  fillSelect('#exp-status', statusSet);
}

function fillSelect(selId, values) {
  const sel = document.querySelector(selId);
  sel.innerHTML = '<option value="">（不检查）</option>' +
    [...values].sort().map(v => `<option value="${v}">${v}</option>`).join('');
}

async function sendInbound() {
  if (!currentContactId) return alert('请先选择联系人');
  await applyPreSwitches(currentContactId);

  const attachments = await Promise.all(
    [...document.querySelector('#sim-attachments').files].map(f => fileToBase64Attachment(f))
  );
  const body = {
    subject: document.querySelector('#sim-subject').value,
    body: document.querySelector('#sim-body').value,
    attachments,
    overrideFromEmail: document.querySelector('#opt-from').value || null,
    expectedIntent: valueOrNull('#exp-intent'),
    expectedAutoAction: valueOrNull('#exp-action'),
    expectedNewStatus: valueOrNull('#exp-status')
  };
  const res = await fetch(`${API}/contacts/${currentContactId}/inbound`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify(body)
  }).then(r => r.json());

  renderLastResult(res);
  updateStats(res.assertion);
  await refreshSnapshot();
  await refreshContactList();
}

async function applyPreSwitches(id) {
  if (document.querySelector('#opt-pause-before').checked) {
    await fetch(`${contextPath}/api/expert-contacts/${id}/pause-auto-reply`, { method: 'POST' });
  }
  if (document.querySelector('#opt-handoff-before').checked) {
    await fetch(`${contextPath}/api/expert-contacts/${id}/switch-to-manual`, {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ reason: 'SIMULATOR_TOGGLE', note: 'preset switch' })
    });
  }
}

async function resetContact() {
  if (!currentContactId) return;
  if (!confirm(`重置联系人 #${currentContactId}？将删除所有相关数据。`)) return;
  const body = { initialStatus: 'INTRO_SENT', createIntroductionMailRecord: true };
  await fetch(`${API}/contacts/${currentContactId}/reset`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify(body)
  });
  await refreshSnapshot();
  await refreshContactList();
}

function renderLastResult(res) {
  document.querySelector('#last-result').textContent =
    JSON.stringify(res, null, 2);
}

function updateStats(a) {
  if (a.passed) stats.pass++;
  else if (Object.values(a).some(v => v === false)) stats.fail++;
  else stats.neutral++;
  document.querySelector('#stats').textContent =
    `${stats.pass} / ${stats.fail} / ${stats.neutral} (通过/失败/无断言)`;
}

async function seedContact() {
  await fetch(`${API}/contacts`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({})
  });
  await refreshContactList();
}

function bindEvents() {
  document.querySelector('#btn-seed').onclick = seedContact;
  document.querySelector('#btn-reset').onclick = resetContact;
  document.querySelector('#btn-send-inbound').onclick = sendInbound;
  document.querySelector('#preset-bar').addEventListener('click', e => {
    const btn = e.target.closest('.preset-btn');
    if (!btn) return;
    const p = presets.find(p => p.key === btn.dataset.key);
    if (!p) return;
    document.querySelector('#sim-subject').value = p.subject;
    document.querySelector('#sim-body').value = p.body;
    if (p.expectedIntent) document.querySelector('#exp-intent').value = p.expectedIntent;
    if (p.expectedAutoAction) document.querySelector('#exp-action').value = p.expectedAutoAction;
    if (p.expectedNewStatus) document.querySelector('#exp-status').value = p.expectedNewStatus;
    if (p.requiresAttachment) alert('此预设需要上传附件');
  });
}

function valueOrNull(sel) {
  const v = document.querySelector(sel).value;
  return v || null;
}

async function fileToBase64Attachment(file) {
  const b64 = await new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => {
      const dataUrl = reader.result;
      const base64 = dataUrl.split(',')[1];
      resolve(base64);
    };
    reader.onerror = reject;
    reader.readAsDataURL(file);
  });
  return { fileName: file.name, contentType: file.type || null, contentBase64: b64 };
}

function esc(s) {
  if (!s) return '';
  const div = document.createElement('div');
  div.textContent = s;
  return div.innerHTML;
}

document.addEventListener('DOMContentLoaded', init);
