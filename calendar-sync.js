/* SPDX-License-Identifier: Apache-2.0 */
'use strict';
let calendarSyncTransport=url=>{window.location.href=url;};
let calendarSyncPending=null;
let calendarSyncAction='';
let calendarSyncConnected=false;
let calendarSyncTimeout=null;
let calendarSyncBusy=false;
let calendarSyncSuspended=false;
function calendarSyncMessage(message){document.getElementById('calendarSyncMessage').textContent=message;}
function calendarSyncConfig(){
    const period=state.trainingSchedule?.revisions?.at(-1);
    if(!period || !period.days.length)throw new Error('Escolha e salve os dias e horários de natação antes de conectar ou sincronizar.');
    return {...period,notBefore:period.startsAt?Date.parse(period.startsAt):0};
}
function sendCalendarSync(action,automatic=false){
    if(!isAndroidApp() || !window.nadoNativeReady){if(!automatic)calendarSyncMessage('A sincronização é feita no APK Android com uma conta Google cadastrada no aparelho.');return;}
    if(calendarSyncPending || calendarSyncBusy || hydrating)return;
    if(action==='connect')calendarSyncSuspended=false;
    if(trainingDirty && !['status','disconnect'].includes(action)){if(!automatic)calendarSyncMessage('Salve as alterações no planejamento antes de sincronizar.');return;}
    let data={};
    try{if(!['status','disconnect'].includes(action))data=calendarSyncConfig();}catch(error){if(!automatic)calendarSyncMessage(error.message);return;}
    calendarSyncPending=crypto.randomUUID();calendarSyncAction=action;
    clearTimeout(calendarSyncTimeout);
    calendarSyncTimeout=setTimeout(()=>{calendarSyncPending=null;calendarSyncMessage('A resposta não chegou. Conclua a autorização/escolha de agenda no Android e toque em Sincronizar.');},120000);
    calendarSyncTransport('nado://calendar-sync?action='+action+'&id='+calendarSyncPending+'&data='+encodeURIComponent(JSON.stringify(data)));
}
function importedCalendarRevision(remote,now=new Date()){
    const current=state.trainingSchedule || {revisions:[],remindersEnabled:false};
    const previous=current.revisions.at(-1);
    const startsAt=remote.notBefore?new Date(remote.notBefore).toISOString():null;
    const revision={days:remote.days,time:remote.time,durationMinutes:remote.durationMinutes,reminderMinutes:previous?.reminderMinutes ?? 30,
        effectiveAt:new Date(Math.max(now.getTime(),previous?Date.parse(previous.effectiveAt)+1:0)).toISOString(),startsAt};
    const same=previous && JSON.stringify([previous.days,previous.time,previous.durationMinutes,previous.startsAt||null])===JSON.stringify([revision.days,revision.time,revision.durationMinutes,revision.startsAt]);
    return validateTrainingSchedule({...current,revisions:same?current.revisions:[...current.revisions,revision]});
}
window.receiveCalendarSync=async(id,result)=>{
    if(!calendarSyncPending || id!==calendarSyncPending)return;
    const action=calendarSyncAction;calendarSyncPending=null;calendarSyncAction='';clearTimeout(calendarSyncTimeout);
    const message=String(result.message||'').slice(0,800);
    if(result.state==='status'){
        calendarSyncConnected=result.connected===true;
        calendarSyncMessage(message);
        if(calendarSyncConnected && result.granted===true)sendCalendarSync('sync',true);
        return;
    }
    if(result.state==='connected'||result.state==='synced')calendarSyncConnected=true;
    if(result.state==='disconnected'||result.state==='deleted')calendarSyncConnected=false;
    document.getElementById('calendarConflict').hidden=result.state!=='conflict';
    if(result.state==='pulled'||result.state==='deleted'){
        calendarSyncBusy=true;
        const previous=state.trainingSchedule;
        try{
            const remote=result.state==='deleted'?{days:[],time:previous?.revisions.at(-1)?.time||'18:00',durationMinutes:previous?.revisions.at(-1)?.durationMinutes||45,notBefore:0}:result.schedule;
            if(!remote || !Array.isArray(remote.days))throw new Error('Horário recebido inválido.');
            state.trainingSchedule=importedCalendarRevision(remote);
            if(result.state==='deleted')state.trainingSchedule.remindersEnabled=false;
            await persistPlan();loadTrainingForm();renderTrainingAttendance();calendarSyncMessage(message);
            calendarSyncBusy=false;
            if(result.state==='pulled'){
                if(state.trainingSchedule.remindersEnabled)sendTrainingNative('apply');
                sendCalendarSync('ack');
            }
        }catch(error){state.trainingSchedule=previous;calendarSyncBusy=false;calendarSyncMessage('Não foi possível salvar o horário recebido. Nenhum evento foi sobrescrito. '+error.message);}
        return;
    }
    calendarSyncMessage(message);
    if(action==='disconnect')document.getElementById('calendarConflict').hidden=true;
};
window.syncCalendarOnResume=()=>{
    if(!calendarSyncSuspended && !calendarSyncPending && !calendarSyncBusy && !hydrating && !trainingDirty)sendCalendarSync('status',true);
};
window.resetCalendarSync=()=>{
    calendarSyncSuspended=true;calendarSyncConnected=false;calendarSyncPending=null;calendarSyncBusy=false;clearTimeout(calendarSyncTimeout);
    if(isAndroidApp() && window.nadoNativeReady)calendarSyncTransport('nado://calendar-sync?action=disconnect&id='+crypto.randomUUID());
    calendarSyncMessage('Conexão com a agenda desativada. Eventos já criados permanecem na Agenda Google; reconecte somente se desejar criar uma nova série.');
};
function setupCalendarSync(){
    const box=document.createElement('section');box.className='calendar-sync-box';
    box.innerHTML=`<h3>Sincronização com a Agenda Google</h3><p class="sub">Opcional e sem serviço pago do aplicativo. Cada usuário autoriza o calendário no Android e escolhe uma agenda Google já cadastrada. Ao escolher a agenda, será criada uma série semanal de natação; o aplicativo gerenciará apenas essa série.</p>
      <div class="actions"><button id="calendarConnect" class="btn btn-secondary" type="button">Conectar Agenda Google</button><button id="calendarSyncNow" class="btn btn-primary" type="button">Sincronizar agora</button><button id="calendarDisconnect" class="btn btn-quiet" type="button">Desconectar</button></div>
      <p id="calendarSyncMessage" class="sub" role="status" aria-live="polite">Desativada até sua autorização. Nenhuma conta é conectada automaticamente.</p>
      <div id="calendarConflict" class="plan-next" hidden><p>O plano mudou nos dois lados. Escolha qual horário usar para atualizar a outra versão.</p><div class="actions"><button id="calendarUseApp" class="btn btn-secondary" type="button">Usar plano do diário</button><button id="calendarUseGoogle" class="btn btn-secondary" type="button">Usar horário da agenda</button></div></div>
      <p class="sub">Sincroniza dias, horário e duração da série ao abrir o aplicativo ou tocar em Sincronizar. A conta Google do Android faz o envio/recebimento pela nuvem. Alertas da agenda e lembretes do diário são independentes. Eventos isolados, séries com data de término/exceções ou fusos diferentes exigem ajuste manual. Excluir a série na agenda pausa o plano ao sincronizar. Desconectar não apaga eventos.</p>`;
    document.getElementById('trainingPlanner').insertBefore(box,document.getElementById('trainingCalendar').parentElement);
    document.getElementById('trainingCalendar').textContent='Adicionar evento sem sincronizar';
    document.getElementById('calendarConnect').onclick=()=>sendCalendarSync('connect');
    document.getElementById('calendarSyncNow').onclick=()=>sendCalendarSync('sync');
    document.getElementById('calendarDisconnect').onclick=()=>sendCalendarSync('disconnect');
    document.getElementById('calendarUseApp').onclick=()=>sendCalendarSync('push');
    document.getElementById('calendarUseGoogle').onclick=()=>sendCalendarSync('pull');
    document.addEventListener('diaryLoaded',window.syncCalendarOnResume);
}
setupCalendarSync();
