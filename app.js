/* SPDX-License-Identifier: Apache-2.0 */
const KEY = 'nadoDiary.cache.v2.local';
const NEXT_PROGRESSION = 'Observe como se sente e ajuste uma coisa de cada vez, respeitando seus limites.';
const defaultPlan = [{
    name: 'Adaptação e aquecimento',
    minutes: 5,
    type: 'leve',
    detail: 'Movimente-se na água e encontre um ritmo tranquilo.'
}, {
    name: 'Série principal: 10 × 25 m',
    minutes: 19,
    type: 'serie',
    detail: 'Cada repetição é uma ida única de 25 m. Termine na borda e descanse 1 min antes da próxima; são 9 pausas entre as 10 idas. A volta começa uma nova repetição, sem emendar.'
}, {
    name: 'Volta à calma',
    minutes: 6,
    type: 'leve',
    detail: 'Finalize com movimento leve e sem pressa.'
}]
const store = createLocalStore();
let activeCacheKey = KEY + '.local';
let state = load(),
    currentUser = null,
    installPrompt = null,
    hydrating = false;

function load() {
    try {
        const x = JSON.parse(localStorage.getItem(activeCacheKey)) || {};
        return {
            ...x,
            plan: x.plan || defaultPlan,
            sessions: Array.isArray(x.sessions) ? x.sessions : [],
            weights: Array.isArray(x.weights) ? x.weights : [],
            activities: Array.isArray(x.activities) ? x.activities : []
        }
    } catch {
        return {
            plan: defaultPlan,
            sessions: [],
            weights: [],
            activities: []
        }
    }
}

function save() {
    localStorage.setItem(activeCacheKey, JSON.stringify(state))
}

function fmtDate(v) {
    if (!v) return '';
    return new Date(v + 'T12:00:00').toLocaleDateString('pt-BR', {
        day: '2-digit',
        month: 'short',
        year: 'numeric'
    })
}

function total(plan) {
    return plan.reduce((n, b) => n + (+b.minutes || 0), 0)
}

function escapeHtml(s = '') {
    return String(s).replace(/[&<>"']/g, c => ({
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        '"': '&quot;',
        "'": '&#39;'
    } [c]))
}

function setSync(label, stateName = '') {
    const el = document.getElementById('syncStatus');
    el.textContent = label;
    el.dataset.state = stateName
}

function remoteToLocal(row) {
    return {
        id: row.id,
        date: row.session_date,
        duration: row.duration_minutes,
        effort: row.effort,
        notes: row.notes || '',
        next: row.next_progression || '',
        actualBlocks: row.actual_blocks || '',
        planMinutes: row.planned_minutes,
        plan: Array.isArray(row.planned_blocks) ? row.planned_blocks : []
    }
}
async function enterApp(user) {
    if (!user || hydrating) return;
    hydrating = true;
    currentUser = user;
    activeCacheKey = KEY + '.' + user.id;
    state = {
        plan: structuredClone(defaultPlan),
        sessions: [],
        weights: [],
        activities: []
    };

    setSync('Carregando…', 'busy');
    try {
        const [{
            data: setting,
            error: settingErr
        }, {
            data: rows,
            error: rowsErr
        }] = await Promise.all([store.from('swim_settings').select('*').eq('user_id', user.id).maybeSingle(), store.from('swim_sessions').select('*').eq('user_id', user.id).order('session_date', {
            ascending: false
        })]);
        if (settingErr) throw settingErr;
        if (rowsErr) throw rowsErr;
        const [{
            data: weightRows,
            error: weightErr
        }, {
            data: activityRows,
            error: activityErr
        }] = await Promise.all([store.from('swim_weight_entries').select('*').eq('user_id', user.id).order('measurement_date', {
            ascending: false
        }), store.from('swim_extra_activities').select('*').eq('user_id', user.id).order('activity_date', {
            ascending: false
        })]);
        if (weightErr) throw weightErr;
        if (activityErr) throw activityErr;
        state.weights = weightRows.map(x => ({
            id: x.id,
            date: x.measurement_date,
            kg: x.weight_kg,
            notes: x.notes || ''
        }));
        state.activities = activityRows.map(x => ({
            id: x.id,
            date: x.activity_date,
            type: x.activity_type,
            minutes: x.duration_minutes,
            notes: x.notes || ''
        }));
        state.sessions = rows.map(remoteToLocal);
        state.plan = setting && Array.isArray(setting.plan) && setting.plan.length ? setting.plan : structuredClone(defaultPlan);
        state.planHint = setting ? setting.plan_hint || '' : '';
        save();
        document.getElementById('weightDate').value = new Date().toLocaleDateString('en-CA');
        document.getElementById('activityDate').value = new Date().toLocaleDateString('en-CA');
        document.getElementById('appShell').style.display = 'block';
        document.getElementById('today').textContent = new Date().toLocaleDateString('pt-BR', {
            weekday: 'long',
            day: 'numeric',
            month: 'long'
        });
        setSync('Neste aparelho');
        render()
    } catch (err) {
        setSync('Falha ao salvar', 'error');
        toast('Não foi possível abrir os registros: ' + err.message)
    } finally {
        hydrating = false
    }
}
async function persistPlan() {
    if (!currentUser) throw new Error('Abra o diário antes de salvar.');
    const {
        error
    } = await store.from('swim_settings').upsert({
        user_id: currentUser.id,
        plan: state.plan,
        plan_hint: state.planHint || '',
        updated_at: new Date().toISOString()
    });
    if (error) throw error;
    save();
    setSync('Neste aparelho')
}

function renderSchedule() {
    document.getElementById('schedule').innerHTML = state.plan.map((b, i) => `<div class="step"><div class="step-num">${String(i+1).padStart(2,'0')}</div><div><div class="step-name">${escapeHtml(b.name)}</div><div class="step-detail">${escapeHtml(b.detail||'')}</div></div><div class="step-time"><span class="${b.type==='descanso'?'rest-chip':b.type==='leve'?'easy-chip':b.type==='serie'?'series-chip':'swim-chip'}">${b.type==='descanso'?'PAUSA':b.type==='leve'?'LEVE':b.type==='serie'?'SÉRIE':'NADO'}</span> &nbsp;${+b.minutes} min</div></div>`).join('') + `<div class="sub" style="text-align:right;margin-top:3px">Total planejado: <b style="color:var(--text)">${total(state.plan)} min</b></div>`
}

function drawChart() {
    const el = document.getElementById('chartWrap'),
        s = [...state.sessions].sort((a, b) => a.date.localeCompare(b.date)).slice(-8);
    if (!s.length) {
        el.innerHTML = '<div class="chart-empty">Após as primeiras sessões, sua progressão aparecerá aqui.</div>';
        return
    }
    const vals = s.map(x => +x.duration || 0),
        max = Math.max(...vals, 35),
        min = Math.min(...vals, 0),
        w = 640,
        h = 130,
        p = 22,
        step = (w - 2 * p) / Math.max(vals.length - 1, 1),
        pts = vals.map((v, i) => `${p+i*step},${h-p-((v-min)/(max-min||1))*(h-2*p)}`).join(' ');
    el.innerHTML = `<svg class="chart" viewBox="0 0 ${w} ${h}" preserveAspectRatio="none" aria-label="Gráfico da duração de cada sessão"><defs><linearGradient id="area" x1="0" x2="0" y1="0" y2="1"><stop offset="0" stop-color="#61e3d1" stop-opacity=".28"/><stop offset="1" stop-color="#61e3d1" stop-opacity="0"/></linearGradient></defs><line x1="${p}" y1="${h-p}" x2="${w-p}" y2="${h-p}" stroke="#315366"/><polyline points="${pts}" fill="none" stroke="#61e3d1" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/>${vals.map((v,i)=>`<circle cx="${p+i*step}" cy="${h-p-((v-min)/(max-min||1))*(h-2*p)}" r="4" fill="#61e3d1"/>`).join('')}</svg><div class="chart-labels"><span>${fmtDate(s[0].date)}</span><span>${fmtDate(s.at(-1).date)}</span></div>`
}

function renderHistory() {
    const host = document.getElementById('history'),
        sessions = [...state.sessions].sort((a, b) => b.date.localeCompare(a.date));
    if (!sessions.length) {
        host.innerHTML = '<div class="history-empty"><strong>Seu histórico começa com a primeira prática.</strong>Registre a sessão para guardar o que fez, como se sentiu e o próximo passo.</div>'
    } else {
        host.innerHTML = '<div class="history-list">' + sessions.map((s, i) => `<div class="session-row"><div class="row-date">${fmtDate(s.date)}<small>${Number(s.planMinutes)||0} min planejados</small></div><div><b>${Number(s.duration)||0} min</b><span class="row-label">realizados</span></div><div class="effort" data-level="${escapeHtml(s.effort)}"><i class="effort-dot"></i><span>${escapeHtml(s.effort)}</span></div><div><span class="tag">${(s.actualBlocks||'').split('\n').filter(Boolean).length} bloco(s) registrado(s)</span><span class="row-label" style="margin-top:5px">${escapeHtml(s.notes||s.next||'Sem observações')}</span></div><button class="delete" title="Excluir sessão" aria-label="Excluir sessão de ${fmtDate(s.date)}" data-delete="${escapeHtml(s.id)}">×</button></div>`).join('') + '</div>'
    }
    document.getElementById('countMetric').textContent = sessions.length;
    document.getElementById('durationMetric').textContent = sessions.length ? Math.round(sessions.reduce((n, x) => n + (+x.duration || 0), 0) / sessions.length) + ' min' : '—';
    drawChart();
    const last = sessions[0];
    document.getElementById('nextPlan').innerHTML = last && last.next ? `<b>Próxima evolução:</b> ${escapeHtml(last.next)}` : `<b>Próxima evolução:</b> ${escapeHtml(state.planHint||NEXT_PROGRESSION)}`;
    host.querySelectorAll('[data-delete]').forEach(b => b.onclick = async () => {
        if (confirm('Excluir este registro de sessão?')) {
            setSync('Excluindo…', 'busy');
            const {
                error
            } = await store.from('swim_sessions').delete().eq('id', b.dataset.delete).eq('user_id', currentUser.id);
            if (error) {
                toast('Não foi possível excluir: ' + error.message);
                setSync('Falha ao salvar', 'error');
                return
            }
            state.sessions = state.sessions.filter(x => x.id !== b.dataset.delete);
            save();
            renderHistory();
            setSync('Neste aparelho')
        }
    })
}

function render() {
    renderSchedule();
    renderHistory();
    renderWellness()
}

function renderWellness() {
    const weights = [...state.weights].sort((a, b) => b.date.localeCompare(a.date)),
        activities = [...state.activities].sort((a, b) => b.date.localeCompare(a.date)),
        wl = document.getElementById('weightList'),
        al = document.getElementById('activityList');
    if (!weights.length) wl.innerHTML = '<div class="wellness-empty">Seus registros de peso aparecerão aqui.</div>';
    else {
        const previous = weights.slice(1).find(x => x.date !== weights[0].date),
            delta = previous ? +weights[0].kg - (+previous.kg) : null;
        wl.innerHTML = (delta === null ? '' : '<div class="plan-next"><b>Variação desde a medição anterior:</b> ' + (delta > 0 ? '+' : '') + delta.toFixed(1).replace('.', ',') + ' kg</div>') + weights.slice(0, 8).map(w => '<div class="wellness-row"><div><b>' + escapeHtml(fmtDate(w.date)) + '</b><small>' + escapeHtml(w.notes || 'Peso registrado') + '</small></div><strong>' + Number(w.kg).toLocaleString('pt-BR', {
            minimumFractionDigits: 1,
            maximumFractionDigits: 2
        }) + ' kg</strong></div>').join('')
    }
    if (!activities.length) al.innerHTML = '<div class="wellness-empty">Suas atividades extras aparecerão aqui.</div>';
    else al.innerHTML = activities.slice(0, 8).map(a => '<div class="wellness-row"><div><b>' + escapeHtml(a.type) + '</b><small>' + escapeHtml(fmtDate(a.date)) + (a.notes ? ' · ' + escapeHtml(a.notes) : '') + '</small></div><strong>' + Number(a.minutes) + ' min</strong></div>').join('')
}

function setupWellness() {
    const weightForm = document.getElementById('weightForm'),
        activityForm = document.getElementById('activityForm');
    weightForm.onsubmit = async e => {
        e.preventDefault();
        if (!currentUser) return toast('Abra o diário antes de salvar.');
        const date = document.getElementById('weightDate').value,
            kg = +document.getElementById('weightKg').value,
            notes = document.getElementById('weightNotes').value.trim();
        setSync('Salvando…', 'busy');
        const {
            data,
            error
        } = await store.from('swim_weight_entries').insert({
            user_id: currentUser.id,
            measurement_date: date,
            weight_kg: kg,
            notes
        }).select().single();
        if (error) {
            setSync('Falha ao salvar', 'error');
            return toast('Não foi possível salvar: ' + error.message)
        }
        state.weights.unshift({
            id: data.id,
            date: data.measurement_date,
            kg: data.weight_kg,
            notes: data.notes || ''
        });
        save();
        weightForm.reset();
        document.getElementById('weightDate').value = new Date().toLocaleDateString('en-CA');
        renderWellness();
        setSync('Neste aparelho');
        toast('Peso salvo.')
    };
    activityForm.onsubmit = async e => {
        e.preventDefault();
        if (!currentUser) return toast('Abra o diário antes de salvar.');
        const date = document.getElementById('activityDate').value,
            type = document.getElementById('activityType').value,
            minutes = +document.getElementById('activityMinutes').value,
            notes = document.getElementById('activityNotes').value.trim();
        setSync('Salvando…', 'busy');
        const {
            data,
            error
        } = await store.from('swim_extra_activities').insert({
            user_id: currentUser.id,
            activity_date: date,
            activity_type: type,
            duration_minutes: minutes,
            notes
        }).select().single();
        if (error) {
            setSync('Falha ao salvar', 'error');
            return toast('Não foi possível salvar: ' + error.message)
        }
        state.activities.unshift({
            id: data.id,
            date: data.activity_date,
            type: data.activity_type,
            minutes: data.duration_minutes,
            notes: data.notes || ''
        });
        save();
        activityForm.reset();
        document.getElementById('activityDate').value = new Date().toLocaleDateString('en-CA');
        renderWellness();
        setSync('Neste aparelho');
        toast('Atividade salva.')
    }
}
let timerInterval = null,
    timerEndsAt = 0,
    timerRemaining = 0,
    audioContext = null;

function displayTimer(seconds) {
    const m = Math.floor(Math.max(0, seconds) / 60),
        s = Math.max(0, seconds) % 60;
    document.getElementById('timerDisplay').textContent = String(m).padStart(2, '0') + ':' + String(s).padStart(2, '0')
}

function beep() {
    try {
        audioContext = audioContext || new(window.AudioContext || window.webkitAudioContext)();
        if (audioContext.state === 'suspended') audioContext.resume();
        const now = audioContext.currentTime;
        [0, .28, .56].forEach((delay, i) => {
            const osc = audioContext.createOscillator(),
                gain = audioContext.createGain();
            osc.type = 'sine';
            osc.frequency.value = i === 1 ? 880 : 660;
            gain.gain.setValueAtTime(.0001, now + delay);
            gain.gain.exponentialRampToValueAtTime(.25, now + delay + .02);
            gain.gain.exponentialRampToValueAtTime(.0001, now + delay + .2);
            osc.connect(gain);
            gain.connect(audioContext.destination);
            osc.start(now + delay);
            osc.stop(now + delay + .21)
        });
        if (navigator.vibrate) navigator.vibrate([180, 100, 180])
    } catch {
        toast('Não foi possível tocar o aviso neste navegador.')
    }
}

function timerTick() {
    const seconds = Math.max(0, Math.ceil((timerEndsAt - Date.now()) / 1000));
    timerRemaining = seconds;
    displayTimer(seconds);
    if (seconds === 0) {
        clearInterval(timerInterval);
        timerInterval = null;
        document.getElementById('timerStart').textContent = 'Iniciar timer';
        document.getElementById('timerStatus').textContent = 'Tempo concluído: ' + (document.getElementById('timerActivity').value.trim() || 'atividade') + '.';
        beep();
        toast('Timer concluído! ' + (document.getElementById('timerActivity').value.trim() || 'Atividade') + ' finalizada.')
    }
}

function setupTimer() {
    const mins = document.getElementById('timerMinutes'),
        start = document.getElementById('timerStart'),
        android = document.getElementById('timerAndroid'),
        nativeApp = navigator.userAgent.includes('NadoFoss/1');
    android.style.display = nativeApp ? 'inline-flex' : 'none';
    android.onclick = () => {
        const seconds = Math.max(1, Math.min(180, +mins.value || 10)) * 60,
            label = document.getElementById('timerActivity').value.trim() || 'Atividade';
        document.getElementById('timerStatus').textContent = 'Enviando ' + label + ' ao cronômetro do Android…';
        window.location.href = 'nado://timer?seconds=' + seconds + '&label=' + encodeURIComponent(label)
    };
    mins.addEventListener('input', () => {
        if (!timerInterval) {
            timerRemaining = Math.max(1, +mins.value || 1) * 60;
            displayTimer(timerRemaining)
        }
    });
    start.onclick = () => {
        if (timerInterval) {
            clearInterval(timerInterval);
            timerInterval = null;
            timerRemaining = Math.max(0, Math.ceil((timerEndsAt - Date.now()) / 1000));
            start.textContent = 'Continuar timer';
            document.getElementById('timerStatus').textContent = 'Timer pausado.';
            return
        }
        if (!timerRemaining) timerRemaining = Math.max(1, Math.min(180, +mins.value || 10)) * 60;
        try {
            audioContext = audioContext || new(window.AudioContext || window.webkitAudioContext)();
            if (audioContext.state === 'suspended') audioContext.resume()
        } catch {}
        timerEndsAt = Date.now() + timerRemaining * 1000;
        start.textContent = 'Pausar';
        document.getElementById('timerStatus').textContent = 'Contando: ' + (document.getElementById('timerActivity').value.trim() || 'atividade') + '.';
        timerTick();
        timerInterval = setInterval(timerTick, 250)
    };
    document.getElementById('timerReset').onclick = () => {
        clearInterval(timerInterval);
        timerInterval = null;
        timerRemaining = Math.max(1, +mins.value || 10) * 60;
        displayTimer(timerRemaining);
        start.textContent = 'Iniciar timer';
        document.getElementById('timerStatus').textContent = 'O aviso sonoro toca quando a contagem termina.'
    };
    document.getElementById('timerTest').onclick = () => {
        beep();
        toast('Teste do aviso sonoro.')
    }
}
const backdrop = document.getElementById('modalBackdrop'),
    form = document.getElementById('sessionForm');

function openModal() {
    document.getElementById('modalTitle').textContent = 'Registrar sessão';
    document.getElementById('sessionDate').value = new Date().toLocaleDateString('en-CA');
    document.getElementById('durationActual').value = total(state.plan) || 30;
    document.getElementById('effort').value = 'Confortável';
    document.getElementById('progressNote').value = '';
    document.getElementById('notes').value = '';
    document.getElementById('actualBlocks').value = state.plan.map(b => `${b.minutes} min · ${b.name}`).join('\n');
    backdrop.classList.add('open')
}

function openPlanEditor() {
    document.getElementById('modalTitle').textContent = 'Ajustar treino planejado';
    form.innerHTML = `<div class="form-grid"><div class="block-editor"><div class="block-editor-title"><span>Blocos do treino · total atual: <b id="planTotal">${total(state.plan)} min</b></span><button class="btn btn-quiet" type="button" id="addBlock">＋ Bloco</button></div><div id="blockRows">${state.plan.map((b,i)=>blockRow(b,i)).join('')}</div></div><div class="field full"><label for="planHint">Nota para a próxima sessão</label><input id="planHint" placeholder="Ex.: repetir o treino e observar como se sente"></div></div><div class="modal-footer"><span class="sub">O plano pode ser mudado a qualquer momento.</span><button class="btn btn-primary" type="submit">Salvar treino</button></div>`;
    backdrop.classList.add('open');
    document.getElementById('addBlock').onclick = () => {
        state.plan.push({
            name: 'Novo bloco',
            minutes: 2,
            type: 'nado',
            detail: ''
        });
        openPlanEditor()
    };
    document.getElementById('blockRows').addEventListener('input', e => {
        if (e.target.matches('[data-min]')) {
            const mins = [...document.querySelectorAll('[data-min]')].reduce((n, x) => n + (+x.value || 0), 0);
            document.getElementById('planTotal').textContent = mins + ' min'
        }
    });
    document.getElementById('blockRows').addEventListener('click', e => {
        const btn = e.target.closest('[data-remove]');
        if (btn) {
            state.plan.splice(+btn.dataset.remove, 1);
            openPlanEditor()
        }
    });
    form.onsubmit = async e => {
        e.preventDefault();
        const rows = [...document.querySelectorAll('.block-line')];
        const plan = rows.map(r => ({
            name: r.querySelector('[data-name]').value.trim() || 'Bloco',
            minutes: Math.max(1, +r.querySelector('[data-min]').value || 1),
            type: r.querySelector('[data-type]').value,
            detail: ''
        }));
        if (!plan.length) {
            toast('Adicione pelo menos um bloco.');
            return
        }
        state.plan = plan;
        state.planHint = document.getElementById('planHint').value.trim();
        setSync('Salvando…', 'busy');
        try {
            await persistPlan();
            closeModal();
            form.innerHTML = originalForm;
            attachForm();
            render();
            toast('Treino atualizado.')
        } catch (err) {
            toast('Falha ao salvar treino: ' + err.message);
            setSync('Falha ao salvar', 'error')
        }
    }
}

function blockRow(b, i) {
    return `<div class="block-line"><input data-name value="${escapeHtml(b.name)}" aria-label="Nome do bloco"><input data-min type="number" min="1" max="180" value="${+b.minutes}" aria-label="Minutos"><select data-type aria-label="Tipo"><option value="nado" ${b.type==='nado'?'selected':''}>Nado</option><option value="serie" ${b.type==='serie'?'selected':''}>Série</option><option value="descanso" ${b.type==='descanso'?'selected':''}>Descanso</option><option value="leve" ${b.type==='leve'?'selected':''}>Leve</option></select><button type="button" data-remove="${i}" aria-label="Remover bloco">×</button></div>`
}
const originalForm = form.innerHTML;

function closeModal() {
    backdrop.classList.remove('open')
}
async function saveSession(e) {
    e.preventDefault();
    if (!currentUser) {
        toast('Abra o diário antes de salvar.');
        return
    }
    const s = {
        date: document.getElementById('sessionDate').value,
        duration: +document.getElementById('durationActual').value,
        effort: document.getElementById('effort').value,
        notes: document.getElementById('notes').value.trim(),
        next: document.getElementById('progressNote').value.trim(),
        actualBlocks: document.getElementById('actualBlocks').value.trim(),
        planMinutes: total(state.plan),
        plan: JSON.parse(JSON.stringify(state.plan))
    };
    setSync('Salvando…', 'busy');
    const {
        data,
        error
    } = await store.from('swim_sessions').insert({
        user_id: currentUser.id,
        session_date: s.date,
        duration_minutes: s.duration,
        effort: s.effort,
        actual_blocks: s.actualBlocks,
        notes: s.notes,
        next_progression: s.next,
        planned_blocks: s.plan,
        planned_minutes: s.planMinutes
    }).select().single();
    if (error) {
        toast('Não foi possível salvar: ' + error.message);
        setSync('Falha ao salvar', 'error');
        return
    }
    state.sessions.unshift(remoteToLocal(data));
    save();
    setSync('Neste aparelho');
    closeModal();
    render();
    toast('Sessão salva.')
}

function attachForm() {
    form.onsubmit = saveSession
}
document.getElementById('startBtn').onclick = () => {
    form.innerHTML = originalForm;
    attachForm();
    openModal()
};
document.getElementById('editPlanBtn').onclick = openPlanEditor;
document.getElementById('editPlanBtn2').onclick = openPlanEditor;
document.getElementById('closeModal').onclick = closeModal;
backdrop.addEventListener('click', e => {
    if (e.target === backdrop) closeModal()
});
document.addEventListener('keydown', e => {
    if (e.key === 'Escape') closeModal()
});
document.getElementById('exportBtn').onclick = exportDiary;

function toast(msg) {
    const t = document.getElementById('toast');
    t.textContent = msg;
    t.classList.add('show');
    setTimeout(() => t.classList.remove('show'), 2300)
}
document.getElementById('today').textContent = new Date().toLocaleDateString('pt-BR', {
    weekday: 'long',
    day: 'numeric',
    month: 'long'
});
document.getElementById('weightDate').value = new Date().toLocaleDateString('en-CA');
document.getElementById('activityDate').value = new Date().toLocaleDateString('en-CA');
setupWellness();
setupTimer();
attachForm();
setupLocal();

function setupLocal() {
    setupSettings();
    try { migrateLegacyLocal(); } catch (error) { toast('Os dados da versão anterior foram preservados. Importe um backup para recuperá-los.'); }
    void enterApp({
        id: 'local'
    });
    setupInstall();
}

function setupInstall() {
    window.addEventListener('beforeinstallprompt', e => {
        e.preventDefault();
        installPrompt = e;
        document.getElementById('installBtn').style.display = 'inline-flex'
    });
    document.getElementById('installBtn').onclick = async () => {
        if (!installPrompt) return;
        installPrompt.prompt();
        await installPrompt.userChoice;
        installPrompt = null;
        document.getElementById('installBtn').style.display = 'none'
    };
    if (!navigator.userAgent.includes('NadoFoss/1') && 'serviceWorker' in navigator) navigator.serviceWorker.register('service-worker.js').catch(() => {})
}
