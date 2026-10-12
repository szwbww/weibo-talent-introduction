/* Persistent meeting records. Metadata saves precede independently retryable raw video uploads. */
(() => {
    'use strict';
    const el = id => document.getElementById(id);
    const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
    const LIMIT = 1073741824;
    let current = null, editor = null, xhr = null;
    const bytes = n => n >= 1048576 ? (n / 1048576).toFixed(1) + ' MiB' : Math.ceil(n / 1024) + ' KiB';
    function url(raw) {
        try { const u = new URL(raw, location.href); return u.origin === location.origin && u.pathname.startsWith(contextPath + '/api/experts/meeting-videos/') ? u.href : ''; } catch { return ''; }
    }
    const live = state => current === state && !state.session.closed && state.host.isConnected;
    function message(text) { el('meetingError').textContent = text; el('meetingError').hidden = !text; }
    function videoHtml(file, recordId) {
        return `<div class="cr-recordfile"><span>${esc(file.displayFilename)} <small class="cr-muted">${bytes(file.byteLength)}</small></span><button class="button small" data-meeting-play="${esc(file.id)}" data-record-id="${esc(recordId)}">播放</button><a href="${esc(url(file.downloadUrl))}" download>下载</a></div>`;
    }
    function render(state) {
        if (!live(state)) return;
        state.host.innerHTML = `<section class="ec-section cr-meetings"><div class="ec-heading"><h3>会议记录 <span class="cr-count">${state.total} 条</span></h3><button class="button small cr-right" data-meeting-refresh>刷新</button><button class="button primary" data-meeting-add>＋ 添加会议记录</button></div>${state.error ? `<p class="cr-error" role="alert">${esc(state.error)}</p>` : ''}${state.loading ? '<p class="cr-muted">正在加载会议记录…</p>' : state.items.length ? state.items.map(record => `<details class="cr-meeting" open><summary><span class="cr-meeticon">▤</span><div class="cr-meetheading"><strong>${esc(record.title)}</strong><div class="cr-meetmeta">${record.time ? esc(record.time.replace('T',' ')) + ' · 北京时间' : '会议时间未填写'}</div></div><span class="cr-chevron">›</span></summary><div class="cr-meetbody"><div><h4>会议纪要</h4><p class="cr-minutes">${esc(record.minutes || '尚未填写会议纪要')}</p></div><div class="cr-meet-side"><h4>参会人</h4><div class="cr-participants">${record.participants.map(name => `<span class="cr-personchip">${esc(name)}</span>`).join('') || '<span class="cr-muted">未填写参会人</span>'}</div><h4>会议视频</h4>${record.videoError ? '<p class="cr-error">视频加载失败，请刷新</p>' : record.files === null ? '<p class="cr-muted">视频加载中…</p>' : record.files.map(file => videoHtml(file,record.id)).join('') || '<p class="cr-muted">暂无视频</p>'}<button class="button small" data-meeting-edit="${esc(record.id)}" ${record.files === null || record.videoError ? 'disabled' : ''}>编辑记录</button></div></div></details>`).join('') : '<div class="cr-empty">暂无会议记录。可添加会议纪要、参会人及视频。</div>'}<div class="cr-pager"><button class="button small" data-meeting-prev ${state.page === 0 || state.loading ? 'disabled' : ''}>上一页</button><span>第 ${state.page + 1} / ${Math.max(1,Math.ceil(state.total / 20))} 页</span><button class="button small" data-meeting-next ${(state.page + 1) * 20 >= state.total || state.loading ? 'disabled' : ''}>下一页</button></div></section>`;
    }
    async function videos(state, recordId) {
        const files = []; let page = 0, total;
        do {
            const data = await api(`/api/experts/meeting-videos?${new URLSearchParams({expertId:state.session.expertId,meetingRecordId:recordId,page:String(page++),size:'50'})}`);
            if (!Array.isArray(data.items)) throw Error('视频响应不可用');
            files.push(...data.items); total = data.total;
            if (!data.items.length) break;
        } while (files.length < total && live(state));
        return files;
    }
    async function load(state) {
        if (!live(state)) return;
        const seq = ++state.seq; state.loading = true; state.error = ''; render(state);
        try {
            const data = await api(`/api/experts/meeting-records?${new URLSearchParams({expertId:state.session.expertId,page:String(state.page),size:'20'})}`);
            if (!live(state) || seq !== state.seq) return;
            if (!Array.isArray(data.items)) throw Error('会议响应不可用');
            state.items = data.items.map(item => ({...item, files:null})); state.total = data.total; state.loading = false; render(state);
            let next = 0;
            async function worker() {
                while (next < state.items.length && live(state) && seq === state.seq) {
                    const item = state.items[next++];
                    try { item.files = await videos(state, item.id); } catch { item.videoError = true; }
                    if (live(state) && seq === state.seq) render(state);
                }
            }
            await Promise.all(Array.from({length:Math.min(4,state.items.length)}, worker));
        } catch (error) { if (live(state) && seq === state.seq) { state.loading = false; state.error = error.message || '会议加载失败'; render(state); } }
    }
    function queue() {
        el('fileCount').textContent = `${editor?.files.length || 0} 个`;
        el('fileQueue').innerHTML = (editor?.files || []).map((file,i) => `<div class="cr-fileitem"><label class="cr-file-name"><input data-record-file-name="${i}" aria-label="视频 ${i+1} 文件名" value="${esc(file.stem)}" ${editor.busy ? 'disabled' : ''}><span>.${esc(file.extension)}</span></label><small>${esc(file.originalFilename)} · ${bytes(file.byteLength)} · ${esc(file.status || (file.id ? '已上传' : '待上传'))}</small>${!file.id ? `<button class="button small" type="button" data-record-file-remove="${i}" ${editor.busy ? 'disabled' : ''}>移除</button>` : ''}</div>`).join('') || '<p class="cr-muted">可只保存会议纪要，也可添加多个视频。</p>';
    }
    function busy(value) {
        if (!editor) return; editor.busy = value;
        el('meetingForm').querySelectorAll('input,textarea,button').forEach(node => node.disabled = value);
        queue();
    }
    function open(record) {
        if (!current || !live(current)) return;
        editor = {state:current, id:record?.id || crypto.randomUUID(), version:record?.version || 0,
            files:(record?.files || []).map(file => ({...file, stem:file.displayFilename.replace(/\.[^.]+$/,'')})), busy:false};
        const form = el('meetingForm'); form.reset();
        form.elements.title.value = record?.title || ''; form.elements.time.value = record?.time || '';
        form.elements.participants.value = (record?.participants || []).join('、'); form.elements.minutes.value = record?.minutes || '';
        el('meetingExpert').textContent = `当前专家：${current.session.facts.expertName || current.session.expertId}`;
        el('meetingEditorTitle').textContent = record ? '编辑会议记录' : '添加会议记录'; message(''); busy(false); el('meetingEditor').showModal();
    }
    function addFiles(files) {
        if (!editor || editor.busy) return;
        const errors = [];
        for (const file of files) {
            const extension = file.name.split('.').pop().toLowerCase();
            if (!['mp4','mov','webm'].includes(extension) || file.size < 1 || file.size > LIMIT) { errors.push(`${file.name}：只支持 MP4/MOV/WebM，单文件须在 1 字节至 1 GiB 之间`); continue; }
            editor.files.push({file,extension,originalFilename:file.name,byteLength:file.size,stem:file.name.replace(/\.[^.]+$/,'')});
        }
        message(errors.join('；')); queue(); el('videoInput').value = '';
    }
    function upload(edit, file) {
        return new Promise((resolve,reject) => {
            const request = new XMLHttpRequest(); xhr = request;
            const params = new URLSearchParams({expertId:edit.state.session.expertId,level:edit.state.session.level || 'RAW',meetingRecordId:edit.id,originalName:file.originalFilename,nameStem:file.stem,expectedSize:String(file.byteLength)});
            request.open('POST',`${contextPath}/api/experts/meeting-videos?${params}`); request.setRequestHeader('Content-Type','application/octet-stream'); request.timeout = 0;
            request.upload.onprogress = event => { if (editor !== edit) return; file.status = event.lengthComputable ? (event.loaded >= event.total ? '上传完成，正在保存…' : `上传 ${Math.floor(event.loaded / event.total * 100)}%`) : '上传中…'; queue(); };
            request.onload = () => { xhr = null; let result; try { result = JSON.parse(request.responseText); } catch { reject(Error('上传响应无法确认，请刷新记录核对')); return; } if (request.status >= 200 && request.status < 300) resolve(result); else reject(Error(result.message || '视频上传失败')); };
            request.onerror = request.onabort = () => { xhr = null; reject(Error('上传中断；已保存的纪要和视频保留，请刷新核对后重试')); };
            request.send(file.file);
        });
    }
    async function save(event) {
        event.preventDefault(); const edit = editor; if (!edit || edit.busy) return;
        const form = el('meetingForm'), names = new Set();
        for (const file of edit.files) {
            file.stem = file.stem.normalize('NFC').trim(); const full = file.stem + '.' + file.extension;
            if (!file.stem || /[\x00-\x1f\x7f-\x9f/\\:*?"<>|]/.test(file.stem) || file.stem.endsWith('.') || Array.from(full).length > 100 || names.has(full)) { message('视频名称无效或重复：完整名称最多100字，不能含路径或控制字符'); return; }
            names.add(full);
        }
        const input = {expertId:edit.state.session.expertId,level:edit.state.session.level || 'RAW',expectedVersion:edit.version,
            title:form.elements.title.value.trim(), time:form.elements.time.value || null,
            participants:[...new Set(form.elements.participants.value.split(/[、,，;；\n]+/).map(s=>s.trim()).filter(Boolean))], minutes:form.elements.minutes.value.trim()};
        let metadataSaved = false;
        busy(true); message('正在保存会议记录…');
        try {
            const record = await api(`/api/experts/meeting-records/${edit.id}`,{method:'PUT',body:JSON.stringify(input)});
            edit.version = record.version; metadataSaved = true;
            if (editor !== edit || !live(edit.state)) return;
            for (const file of edit.files) {
                if (editor !== edit || !live(edit.state)) return;
                let saved;
                if (!file.id) {
                    // An interrupted response may already have persisted this exact upload. Reconcile before retrying.
                    if (file.attempted) {
                        const existing = (await videos(edit.state,edit.id)).find(v=>v.displayFilename===file.stem+'.'+file.extension && v.byteLength===file.byteLength && v.originalFilename===file.originalFilename);
                        if (existing) saved = existing;
                    }
                    if (!saved) { file.attempted = true; saved = await upload(edit,file); }
                } else if (file.displayFilename !== file.stem+'.'+file.extension) {
                    saved = await api(`/api/experts/meeting-videos/${file.id}?expertId=${encodeURIComponent(edit.state.session.expertId)}`,{method:'PATCH',body:JSON.stringify({nameStem:file.stem,expectedVersion:file.version})});
                }
                if (saved) { Object.assign(file,saved); delete file.file; file.status = '已上传'; }
            }
            if (editor === edit) { busy(false); el('meetingEditor').close(); }
            if (live(edit.state)) await load(edit.state);
        } catch (error) {
            if (editor === edit) { busy(false); message(`${metadataSaved ? '会议记录已保存；' : ''}${error.message || '保存失败'}。可修改后重试。`); }
            if (live(edit.state)) load(edit.state);
        }
    }
    document.addEventListener('click', event => {
        const button = event.target.closest('button,a'); if (!button) return;
        if (button.dataset.close === 'meetingEditor') { if (!editor?.busy) el('meetingEditor').close(); return; }
        if (button.dataset.close === 'playerDialog') { el('playerDialog').close(); return; }
        if (button.hasAttribute('data-meeting-add')) open();
        if (button.hasAttribute('data-meeting-refresh') && current) load(current);
        if (button.hasAttribute('data-meeting-prev') && current && current.page > 0) { current.page--; load(current); }
        if (button.hasAttribute('data-meeting-next') && current) { current.page++; load(current); }
        if (button.dataset.meetingEdit) open(current?.items.find(item=>item.id===button.dataset.meetingEdit));
        if (button.id === 'pickVideo') el('videoInput').click();
        if (button.dataset.recordFileRemove !== undefined && editor && !editor.busy) { editor.files.splice(Number(button.dataset.recordFileRemove),1); queue(); }
        if (button.dataset.meetingPlay) {
            const file = current?.items.find(item=>item.id===button.dataset.recordId)?.files?.find(file=>file.id===button.dataset.meetingPlay);
            if (file && url(file.previewUrl)) { el('playerTitle').textContent=file.displayFilename; el('player').src=url(file.previewUrl); el('playerHint').textContent='若浏览器不支持此编码，可下载原视频观看。'; el('playerDialog').showModal(); }
        }
    });
    el('fileQueue').addEventListener('input',event=>{if (editor && event.target.dataset.recordFileName !== undefined) editor.files[Number(event.target.dataset.recordFileName)].stem=event.target.value;});
    el('videoInput').addEventListener('change',event=>addFiles([...event.target.files]));
    el('meetingForm').addEventListener('submit',save);
    el('meetingEditor').addEventListener('cancel',event=>{if (editor?.busy) event.preventDefault();});
    el('meetingEditor').addEventListener('close',()=>{editor=null;});
    el('playerDialog').addEventListener('close',()=>{el('player').pause();el('player').removeAttribute('src');el('player').load();});
    el('player').addEventListener('error',()=>{el('playerHint').textContent='视频无法播放，可下载原文件观看。';});
    window.addEventListener('beforeunload',event=>{if(editor?.busy){event.preventDefault();event.returnValue='';}});
    window.ExpertMeetingRecords = {
        mount(session) { this.unmount(); const host=session.host.querySelector('[data-meeting-records]'); if (!host) return; current={session,host,items:[],page:0,total:0,seq:0}; load(current); },
        unmount() { current=null; xhr?.abort(); xhr=null; if(el('meetingEditor').open)el('meetingEditor').close();if(el('playerDialog').open)el('playerDialog').close(); }
    };
})();
