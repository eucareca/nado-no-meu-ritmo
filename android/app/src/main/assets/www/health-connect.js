/* SPDX-License-Identifier: Apache-2.0 */
'use strict';
let healthPending = null;
let healthCandidates = [];
let healthSaving = false;
let healthTimeout = null;

function normalizeHealthRecord(raw) {
    if (!raw || typeof raw !== 'object') throw new Error('Treino inválido.');
    const record = validatedHealthData(raw);
    if (!record || !['pool','openWater'].includes(raw.type)) throw new Error('Tipo de treino inválido.');
    return {...record, date:backupDate(raw.date), title:backupText(raw.title,160) || 'Natação', type:raw.type};
}
function isHealthDuplicate(a, b) {
    return !!a && !!b && ((a.source === b.source && a.sourceId === b.sourceId) || (a.start === b.start && a.end === b.end));
}
function healthAlreadySaved(record) {
    return state.sessions.some(s => isHealthDuplicate(s.health, record));
}
function healthMessage(text) { document.getElementById('healthMessage').textContent = text; }
function renderHealthCandidates() {
    const host = document.getElementById('healthCandidates');
    host.innerHTML = healthCandidates.map((row,index) => {
        const duplicate = healthAlreadySaved(row);
        const metrics = [fmtDate(row.date), new Date(row.start).toLocaleTimeString('pt-BR',{hour:'2-digit',minute:'2-digit'}), Math.max(1,Math.round((Date.parse(row.end)-Date.parse(row.start))/60000)) + ' min', row.type === 'pool' ? 'Piscina' : 'Águas abertas'];
        if (row.distanceMeters != null) metrics.push(Number(row.distanceMeters).toLocaleString('pt-BR') + ' m');
        if (row.heartRateAverage != null) metrics.push('FC média: ' + row.heartRateAverage + ' bpm');
        return `<label class="health-row"><input type="checkbox" data-health-index="${index}" ${duplicate ? 'disabled' : ''}><span><b>${escapeHtml(row.title)}</b><small>${escapeHtml(healthSourceLabel(row.source))}</small><small>${escapeHtml(metrics.join(' · '))}</small>${duplicate ? '<small>Já salvo no diário</small>' : ''}</span></label>`;
    }).join('');
    document.getElementById('healthSave').hidden = !healthCandidates.some(row => !healthAlreadySaved(row));
}
function requestHealthRead() {
    if (!isAndroidApp()) return healthMessage('A importação está disponível no aplicativo Android, a partir do Android 14.');
    healthPending = crypto.randomUUID();
    healthCandidates = []; renderHealthCandidates();
    healthMessage('Autorize a leitura no Android e mantenha este aplicativo aberto durante a consulta…');
    document.getElementById('healthRead').disabled = true;
    clearTimeout(healthTimeout);
    healthTimeout = setTimeout(() => {
        if (healthPending) { cancelHealthRead(); healthMessage('A consulta demorou demais. Tente novamente com o aplicativo aberto.'); }
    }, 65000);
    window.location.href = 'nado://health?action=read&id=' + encodeURIComponent(healthPending);
}
function cancelHealthRead() {
    if (healthPending && isAndroidApp()) window.location.href = 'nado://health?action=cancel&id=' + encodeURIComponent(healthPending);
    healthPending = null;
    clearTimeout(healthTimeout);
    document.getElementById('healthRead').disabled = false;
}
window.onNativeHealthPaused = () => {
    if (!healthPending) return;
    healthPending = null; clearTimeout(healthTimeout);
    document.getElementById('healthRead').disabled = false;
    healthMessage('Consulta interrompida ao sair do aplicativo. Toque em buscar para tentar novamente.');
};
window.receiveHealthConnect = (id, payload) => {
    if (!healthPending || id !== healthPending || !document.getElementById('healthDialog').open) return;
    healthPending = null; clearTimeout(healthTimeout);
    document.getElementById('healthRead').disabled = false;
    if (payload && payload.error) return healthMessage(String(payload.error).slice(0,500));
    try {
        if (!payload || !Array.isArray(payload.sessions) || payload.sessions.length > 100) throw new Error('Resposta de treinos inválida.');
        const rows = payload.sessions.map(normalizeHealthRecord);
        healthCandidates = rows.filter((r,i) => !rows.slice(0,i).some(other => isHealthDuplicate(r,other)));
        renderHealthCandidates();
        healthMessage(healthCandidates.length ? `${healthCandidates.length} treino(s) encontrado(s). Marque apenas os que deseja importar. Dados ausentes não serão estimados.${payload.partial ? ' O limite de consulta foi atingido; alguns treinos podem não aparecer.' : ''}${payload.metricsMissing ? ' Algumas métricas não puderam ser consultadas.' : ''}` : 'Nenhum treino de natação foi encontrado nos últimos 28 dias. Sincronize a pulseira com o Mi Fitness ou Samsung Health e autorize esse aplicativo a compartilhar exercícios no Health Connect.');
    } catch (error) { healthCandidates = []; renderHealthCandidates(); healthMessage(error.message); }
};
async function saveHealthCandidates() {
    if (healthSaving || hydrating || !currentUser) return;
    const selected = [...document.querySelectorAll('[data-health-index]:checked')].map(input => healthCandidates[Number(input.dataset.healthIndex)]).filter(Boolean);
    const unique = [];
    for (const row of selected) if (!healthAlreadySaved(row) && !unique.some(other => isHealthDuplicate(row,other))) unique.push(row);
    if (!unique.length) return healthMessage('Marque pelo menos um treino ainda não importado.');
    const rows = unique.map(health => ({user_id:currentUser.id, session_date:health.date,
        duration_minutes:Math.max(1, Math.round((Date.parse(health.end)-Date.parse(health.start))/60000)),
        effort:'Não informado', notes:'Importado do Health Connect · ' + healthSourceLabel(health.source),
        next_progression:'', actual_blocks:health.title, planned_blocks:[], planned_minutes:0, health_data:health}));
    healthSaving = true;
    document.getElementById('healthSave').disabled = true;
    document.getElementById('healthRead').disabled = true;
    const {data,error} = await store.from('swim_sessions').insert(rows).select();
    healthSaving = false;
    document.getElementById('healthSave').disabled = false;
    document.getElementById('healthRead').disabled = false;
    if (error) return healthMessage('Não foi possível salvar: ' + error.message);
    state.sessions.unshift(...data.map(remoteToLocal));
    save(); renderHistory(); renderHealthCandidates();
    healthMessage(`${data.length} treino(s) salvo(s) no diário. Nenhum dado foi alterado no Health Connect.`);
}
function setupHealthConnect() {
    const button = document.createElement('button');
    button.id = 'healthConnectBtn'; button.className = 'btn btn-quiet'; button.type = 'button'; button.textContent = 'Relógios';
    document.querySelector('.top-actions').prepend(button);
    const dialog = document.createElement('dialog');
    dialog.id = 'healthDialog'; dialog.className = 'health-dialog';
    dialog.setAttribute('aria-labelledby','healthTitle');
    dialog.innerHTML = `<div class="modal-head"><h2 id="healthTitle">Xiaomi e Samsung</h2><button class="btn btn-quiet" type="button" id="healthClose">Fechar</button></div>
      <p>Importe os treinos de natação que o <b>Mi Fitness</b> ou <b>Samsung Health</b> compartilhar com o <b>Health Connect</b>.</p>
      <ol><li>Sincronize o relógio com o aplicativo dele.</li><li>No Mi Fitness ou Samsung Health, ative o compartilhamento de exercícios com o Health Connect.</li><li>Busque os treinos e escolha quais guardar neste diário.</li></ol>
      <p class="sub">Requer Android 14 ou superior. Consulta os últimos 28 dias. Lemos exercícios e, se você autorizar, distância e frequência cardíaca média. Cada métrica usa o horário e o aplicativo de origem da sessão; ela pode não ser fornecida pelo dispositivo. Não enviamos planos ou comandos ao relógio.</p>
      <div class="actions"><button class="btn btn-primary" type="button" id="healthRead">Autorizar e buscar treinos</button><button class="btn btn-secondary" type="button" id="healthSettings">Gerenciar acesso</button></div>
      <p id="healthMessage" role="status" aria-live="polite">Nada é importado sem sua seleção. Os dados salvos ficam no aparelho e entram no backup manual. Se o mesmo treino aparece nos dois aplicativos com horários diferentes, revise para não salvá-lo duas vezes.</p>
      <div id="healthCandidates"></div><button class="btn btn-primary" type="button" id="healthSave" hidden>Importar selecionados</button>`;
    document.body.append(dialog);
    button.onclick = () => { healthCandidates = []; renderHealthCandidates(); dialog.showModal(); };
    document.getElementById('healthClose').onclick = () => dialog.close();
    dialog.addEventListener('close', () => { cancelHealthRead(); healthCandidates = []; renderHealthCandidates(); });
    document.getElementById('healthRead').onclick = requestHealthRead;
    document.getElementById('healthSettings').onclick = () => {
        if (!isAndroidApp()) return healthMessage('Gerencie o acesso nas configurações do seu Android.');
        window.location.href = 'nado://health?action=settings&id=' + crypto.randomUUID();
    };
    document.getElementById('healthSave').onclick = saveHealthCandidates;
}
setupHealthConnect();
