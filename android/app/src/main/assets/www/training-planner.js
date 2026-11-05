/* SPDX-License-Identifier: Apache-2.0 */
'use strict';
const TRAINING_DAYS = ['Dom','Seg','Ter','Qua','Qui','Sex','Sáb'];
let trainingPending = null;
let trainingTimeout = null;
let trainingDirty = false;
let trainingNativeMessage = '';
function localDay(date) { return `${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,'0')}-${String(date.getDate()).padStart(2,'0')}`; }
function trainingPeriodAt(schedule, instant) {
    return [...(schedule?.revisions || [])].reverse().find(period => Date.parse(period.effectiveAt) <= instant.getTime()) || null;
}
function trainingSlots(schedule, start, end) {
    const slots = [];
    for (let day = new Date(start.getFullYear(), start.getMonth(), start.getDate()); day < end; day.setDate(day.getDate()+1)) {
        for (const period of schedule?.revisions || []) {
            if (!period.days.includes(day.getDay())) continue;
            const [hour,minute] = period.time.split(':').map(Number);
            const date = new Date(day.getFullYear(),day.getMonth(),day.getDate(),hour,minute);
            if (date < start || date >= end || trainingPeriodAt(schedule,date) !== period || (period.startsAt && date.getTime()<Date.parse(period.startsAt))) continue;
            slots.push({date:localDay(date), start:date.getTime(), end:date.getTime()+period.durationMinutes*60000, time:period.time});
        }
    }
    return slots.sort((a,b)=>a.start-b.start);
}
function trainingAttendance(schedule, sessions, now = new Date()) {
    const week = new Date(now.getFullYear(),now.getMonth(),now.getDate());
    week.setDate(week.getDate()-((week.getDay()+6)%7));
    const end = new Date(week); end.setDate(end.getDate()+7);
    const slots = trainingSlots(schedule,week,end);
    const realDays = new Set(sessions.filter(s=>s.date<=localDay(now)).map(s=>s.date));
    const planned = [...new Set(slots.map(s=>s.date))];
    const elapsed = [...new Set(slots.filter(s=>s.end<=now.getTime()).map(s=>s.date))];
    const done = planned.filter(date=>realDays.has(date));
    const waiting = elapsed.filter(date=>!realDays.has(date));
    return {slots,planned,elapsed,done,waiting, realDays, extra:[...realDays].filter(date=>date>=localDay(week)&&date<localDay(end)&&!planned.includes(date))};
}
function renderTrainingAttendance() {
    const host = document.getElementById('trainingAttendance');
    if (!host) return;
    const schedule = state.trainingSchedule;
    if (!schedule?.revisions?.length) { host.innerHTML = '<div class="chart-empty">Escolha os dias para começar a acompanhar sua regularidade.</div>'; return; }
    const now = new Date(), stats = trainingAttendance(schedule,state.sessions,now);
    const last = [...state.sessions].filter(s=>s.date<=localDay(now)).sort((a,b)=>b.date.localeCompare(a.date))[0];
    host.innerHTML = `<div class="progress"><div class="metric"><div class="metric-value">${stats.done.length}/${stats.planned.length}</div><div class="metric-label">dias com registro / previstos nesta semana</div></div><div class="metric"><div class="metric-value">${stats.waiting.length}</div><div class="metric-label">dias passados aguardando registro</div></div></div>
      <div class="training-week">${stats.planned.map(date=>{const slot=stats.slots.find(s=>s.date===date);const status=stats.realDays.has(date)?'Registrado':stats.waiting.includes(date)?'Sem registro':'Previsto';return `<div class="wellness-row"><span>${escapeHtml(fmtDate(date))} · ${slot.time}</span><strong>${status}</strong></div>`;}).join('') || '<p class="sub">Nenhum dia previsto nesta semana.</p>'}</div>
      <p class="sub">${last?'Última sessão registrada: '+escapeHtml(fmtDate(last.date))+'.':'Ainda não há sessões registradas.'} ${stats.extra.length?stats.extra.length+' dia(s) extra(s) com registro nesta semana.':''} Um evento na agenda não confirma presença. “Sem registro” não significa que você faltou; registre ou importe a sessão se treinou.</p>`;
}
function trainingFormValue() {
    return {days:[...document.querySelectorAll('[data-training-day]:checked')].map(input=>+input.dataset.trainingDay), time:document.getElementById('trainingTime').value,
        durationMinutes:+document.getElementById('trainingDuration').value, reminderMinutes:+document.getElementById('trainingAdvance').value};
}
function loadTrainingForm() {
    const period = state.trainingSchedule?.revisions?.at(-1);
    document.querySelectorAll('[data-training-day]').forEach(input=>input.checked=!!period?.days.includes(+input.dataset.trainingDay));
    document.getElementById('trainingTime').value = period?.time || '18:00';
    document.getElementById('trainingDuration').value = period?.durationMinutes || 45;
    document.getElementById('trainingAdvance').value = period?.reminderMinutes ?? 30;
    trainingDirty = false;
}
function trainingMessage(message) { document.getElementById('trainingMessage').textContent=message; }
async function saveTrainingPlan(event) {
    event.preventDefault();
    if (hydrating || !currentUser) return;
    let next;
    try {
        const current = state.trainingSchedule || {revisions:[],remindersEnabled:false};
        const candidate = {...trainingFormValue(),effectiveAt:new Date().toISOString()};
        if(!candidate.days.length) throw new Error('Escolha pelo menos um dia válido da semana.');
        const previous = current.revisions.at(-1);
        const key = period => JSON.stringify([[...period.days].sort((a,b)=>a-b),period.time,period.durationMinutes,period.reminderMinutes]);
        const changed = !previous || key(previous)!==key(candidate);
        next = validateTrainingSchedule({...current,revisions:changed?[...current.revisions,candidate]:current.revisions});
    } catch(error) { return trainingMessage(error.message); }
    const before = state.trainingSchedule;
    state.trainingSchedule=next;
    try {
        await persistPlan(); trainingDirty=false; renderTrainingAttendance();
        trainingMessage('Dias e horários salvos. As alterações valem a partir de agora e preservam os planos anteriores no acompanhamento.');
        if(next.remindersEnabled) sendTrainingNative('apply');
        if(window.sendCalendarSync && calendarSyncConnected) sendCalendarSync('sync',true);
    } catch(error) { state.trainingSchedule=before; trainingMessage('Não foi possível salvar: '+error.message); }
}
function trainingNativePayload(enabled) {
    const period = state.trainingSchedule?.revisions?.at(-1);
    if (!period || !period.days.length) throw new Error('Escolha e salve os dias e horários primeiro.');
    return {...period,enabled,notBefore:period.startsAt?Date.parse(period.startsAt):0};
}
function sendTrainingNative(action) {
    if (trainingDirty && action!=='disable' && action!=='status') return trainingMessage('Salve as alterações dos dias e horários antes de continuar.');
    if (!isAndroidApp()) return trainingMessage('Os lembretes no celular ficam disponíveis no APK Android. Você pode baixar o evento de calendário nesta página.');
    if (trainingPending) return trainingMessage('Conclua a solicitação atual do Android antes de continuar.');
    let payload;
    try { payload=action==='apply'||action==='calendar'?trainingNativePayload(true):{}; } catch(error) { return trainingMessage(error.message); }
    trainingPending=crypto.randomUUID();
    clearTimeout(trainingTimeout);
    trainingTimeout=setTimeout(()=>{trainingPending=null;trainingMessage('A resposta do Android não chegou. Confira a autorização de notificações e tente novamente.');},60000);
    window.location.href='nado://training?action='+action+'&id='+trainingPending+'&data='+encodeURIComponent(JSON.stringify(payload));
}
window.receiveTrainingResult = async (id,result) => {
    if(id!==trainingPending) return;
    clearTimeout(trainingTimeout);trainingPending=null;
    trainingNativeMessage=String(result.message||'').slice(0,500);
    if(typeof result.enabled==='boolean' && state.trainingSchedule) {
        state.trainingSchedule.remindersEnabled=result.enabled;
        try { await persistPlan(); } catch(error) { trainingNativeMessage+=' Não foi possível guardar o status do lembrete no diário.'; }
    }
    trainingMessage(trainingNativeMessage);
};
window.resetTrainingNative = () => {
    trainingPending=null;clearTimeout(trainingTimeout);
    if(isAndroidApp() && window.nadoNativeReady) window.location.href='nado://training?action=disable&id='+crypto.randomUUID();
    if(window.resetCalendarSync) window.resetCalendarSync();
    loadTrainingForm();renderTrainingAttendance();
    trainingMessage('Lembretes desativados. Para reativá-los após restaurar o backup, use o botão Ativar lembretes.');
};
function calendarEventData(schedule, now = new Date()) {
    if (!schedule?.revisions?.length) throw new Error('Salve os dias e horários primeiro.');
    const period=schedule.revisions.at(-1), end=new Date(now);end.setDate(end.getDate()+9);
    const next=trainingSlots(schedule,now,end)[0];
    if (!next) throw new Error('Não foi encontrado um próximo treino.');
    return {period,start:new Date(next.start),end:new Date(next.end),rule:'FREQ=WEEKLY;BYDAY='+period.days.map(day=>['SU','MO','TU','WE','TH','FR','SA'][day]).join(',')+';WKST=MO'};
}
function trainingCalendarFile() {
    if(trainingDirty) return trainingMessage('Salve as alterações antes de exportar o calendário.');
    try {
        const event=calendarEventData(state.trainingSchedule);
        const stamp=date=>localDay(date).replaceAll('-','')+'T'+String(date.getHours()).padStart(2,'0')+String(date.getMinutes()).padStart(2,'0')+'00';
        const text=['BEGIN:VCALENDAR','VERSION:2.0','PRODID:-//Nado no meu ritmo//Treinos//PT-BR','BEGIN:VEVENT','UID:'+crypto.randomUUID()+'@nadonomeuritmo.local',
            'DTSTAMP:'+new Date().toISOString().replaceAll('-','').replaceAll(':','').replace(/\.\d{3}Z$/,'Z'),'DTSTART:'+stamp(event.start),'DTEND:'+stamp(event.end),'RRULE:'+event.rule,
            'SUMMARY:Natação','DESCRIPTION:Treino planejado. Registre a sessão no diário após nadar.','BEGIN:VALARM','ACTION:DISPLAY','DESCRIPTION:Natação',
            'TRIGGER:-PT'+event.period.reminderMinutes+'M','END:VALARM','END:VEVENT','END:VCALENDAR',''].join('\r\n');
        const url=URL.createObjectURL(new Blob([text],{type:'text/calendar;charset=utf-8'}));
        const link=document.createElement('a');link.href=url;link.download='natacao.ics';link.click();setTimeout(()=>URL.revokeObjectURL(url),1000);
        trainingMessage('Arquivo de calendário preparado. Importe-o na agenda desejada e confira o horário, a repetição e o lembrete. Reimportar pode duplicar o evento.');
    }catch(error){trainingMessage(error.message);}
}
function setupTrainingPlanner() {
    const section=document.createElement('section');section.className='card history-card training-card';section.id='trainingPlanner';
    section.innerHTML=`<div class="card-head"><div><h2>Dias de natação e frequência</h2><div class="sub">Organize a semana e acompanhe os dias com sessões registradas</div></div></div>
      <form id="trainingForm"><fieldset class="training-days"><legend>Dias da semana</legend>${[1,2,3,4,5,6,0].map(day=>`<label><input type="checkbox" data-training-day="${day}">${TRAINING_DAYS[day]}</label>`).join('')}</fieldset>
      <div class="form-grid"><div class="field"><label for="trainingTime">Horário</label><input type="time" id="trainingTime" required value="18:00"></div><div class="field"><label for="trainingDuration">Duração prevista (min)</label><input type="number" id="trainingDuration" min="1" max="240" required value="45"></div><div class="field"><label for="trainingAdvance">Avisar antes (min)</label><input type="number" id="trainingAdvance" min="0" max="1440" required value="30"></div></div>
      <div class="actions training-actions"><button class="btn btn-primary" type="submit">Salvar dias e horários</button></div></form>
      <div class="actions training-actions"><button class="btn btn-secondary" id="trainingEnable" type="button">Ativar lembretes</button><button class="btn btn-quiet" id="trainingDisable" type="button">Desativar lembretes</button><button class="btn btn-quiet" id="trainingTest" type="button">Testar notificação</button><button class="btn btn-quiet" id="trainingStatus" type="button">Consultar lembretes</button></div>
      <div class="actions training-actions"><button class="btn btn-secondary" id="trainingCalendar" type="button">Adicionar à Agenda Google</button><button class="btn btn-quiet" id="trainingIcs" type="button">Baixar evento (.ics)</button></div>
      <p id="trainingMessage" role="status" aria-live="polite" class="sub">Nada é agendado antes de você escolher os dias e ativar os lembretes.</p>
      <p class="sub">São notificações locais no Android. Economia de bateria e permissões podem atrasar ou impedir os avisos. Na Agenda Google, revise e salve o evento no calendário da sua conta. Alterações e cancelamentos feitos na agenda não voltam para o diário; reenviar pode criar eventos duplicados.</p><div id="trainingAttendance"></div>`;
    document.querySelector('main.grid').prepend(section);
    document.getElementById('trainingForm').onsubmit=saveTrainingPlan;
    document.getElementById('trainingForm').addEventListener('input',()=>trainingDirty=true);
    document.getElementById('trainingEnable').onclick=()=>sendTrainingNative('apply');
    document.getElementById('trainingDisable').onclick=()=>sendTrainingNative('disable');
    document.getElementById('trainingTest').onclick=()=>sendTrainingNative('test');
    document.getElementById('trainingStatus').onclick=()=>sendTrainingNative('status');
    document.getElementById('trainingCalendar').onclick=()=>isAndroidApp()?sendTrainingNative('calendar'):trainingCalendarFile();
    document.getElementById('trainingIcs').onclick=trainingCalendarFile;
    document.getElementById('trainingIcs').hidden=isAndroidApp();
    document.addEventListener('diaryChanged',renderTrainingAttendance);
    window.addEventListener('focus',renderTrainingAttendance);
    loadTrainingForm();renderTrainingAttendance();
    document.addEventListener('diaryLoaded',()=>{loadTrainingForm();renderTrainingAttendance();});
}
setupTrainingPlanner();
