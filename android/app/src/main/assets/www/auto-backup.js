/* SPDX-License-Identifier: Apache-2.0 */
'use strict';
// Automatic encrypted copies through the user-selected Android document provider.
let autoConfig=null,autoEnabled=false,autoTarget=null,autoTimer=null,autoRunning=false,autoAgain=false,autoPayload=null,autoLastSnapshot=null;
const autoRequests=new Map();
const isAutoBackupApp=()=>isAndroidApp() && window.nadoAutoBackupReady===true;
let autoBackupTransport=url=>{location.href=url;};
function autoMessage(text){document.getElementById('autoBackupMessage').textContent=text;}
function autoKeyStore(operation,value){
    return new Promise((resolve,reject)=>{
        const request=indexedDB.open('nado-auto-backup',1);
        request.onupgradeneeded=()=>request.result.createObjectStore('keys');
        request.onerror=()=>reject(new Error('Não foi possível acessar a chave local do backup.'));
        request.onsuccess=()=>{
            const db=request.result,transaction=db.transaction('keys',operation==='get'?'readonly':'readwrite'),store=transaction.objectStore('keys');
            const entry=operation==='get'?store.get('active'):operation==='put'?store.put(value,'active'):store.delete('active');
            let result;entry.onsuccess=()=>{result=entry.result;};
            transaction.oncomplete=()=>{db.close();resolve(result||null);};
            transaction.onerror=()=>{db.close();reject(new Error('Não foi possível guardar a chave local do backup.'));};
            transaction.onabort=transaction.onerror;
        };
    });
}
function autoNative(action,extra={}){
    return new Promise((resolve,reject)=>{
        if(!isAutoBackupApp()){reject(new Error('O backup automático está disponível no aplicativo Android.'));return;}
        const id=crypto.randomUUID(),timeout=setTimeout(()=>{autoRequests.delete(id);reject(new Error('A operação demorou demais. Confira o destino e tente novamente.'));},180000);
        autoRequests.set(id,{resolve,reject,timeout,action});
        autoBackupTransport('nado://auto-backup?action='+action+'&id='+id+Object.entries(extra).map(([key,value])=>'&'+key+'='+encodeURIComponent(value)).join(''));
    });
}
window.receiveAutoBackup=(id,result)=>{
    const request=autoRequests.get(id);if(!request)return;
    clearTimeout(request.timeout);autoRequests.delete(id);
    if(result.error)request.reject(new Error(result.message));else request.resolve(result);
};
window.autoBackupChunk=(id,offset,count)=>autoPayload?.id===id && Number.isInteger(offset) && offset>=0 && count>0 && count<=32768?autoPayload.text.slice(offset,offset+count):null;
function autoSnapshot(){
    return JSON.stringify({plan:state.plan,planHint:state.planHint||'',trainingSchedule:state.trainingSchedule||null,sessions:state.sessions,weights:state.weights,activities:state.activities});
}
async function autoEnvelope(snapshot,config){
    const iv=crypto.getRandomValues(new Uint8Array(12));
    const bytes=await crypto.subtle.encrypt({name:'AES-GCM',iv,additionalData:new TextEncoder().encode(BACKUP_FORMAT)},config.key,new TextEncoder().encode(snapshot));
    const text=JSON.stringify({format:BACKUP_FORMAT,version:1,encrypted:true,algorithm:'AES-256-GCM',kdf:'PBKDF2-SHA256',iterations:BACKUP_ROUNDS,salt:config.salt,iv:base64Bytes(iv),data:base64Bytes(new Uint8Array(bytes))});
    if(new TextEncoder().encode(text).length>BACKUP_LIMIT)throw new Error('O backup excedeu 5 MB. A cópia anterior foi mantida.');
    return text;
}
async function queueAutoBackup(force=false){
    if(!autoEnabled || !autoConfig || !currentUser || hydrating)return;
    if(autoRunning){autoAgain=true;return;}
    autoRunning=true;const config=autoConfig,target=autoTarget;
    try{
        const snapshot=autoSnapshot();
        if(!force && snapshot===autoLastSnapshot)return;
        const text=await autoEnvelope(snapshot,config);
        if(!autoEnabled || autoConfig!==config || autoTarget!==target)return;
        const transfer=crypto.randomUUID();autoPayload={id:transfer,text};
        const result=await autoNative('stage',{transfer,length:text.length,target});
        if(autoEnabled && autoTarget===target){autoLastSnapshot=snapshot;autoMessage(result.message);}
    }catch(error){autoMessage(error.message+' Seus registros locais continuam preservados.');}
    finally{autoPayload=null;autoRunning=false;if(autoAgain){autoAgain=false;scheduleAutoBackup();}}
}
function scheduleAutoBackup(){clearTimeout(autoTimer);autoTimer=setTimeout(()=>void queueAutoBackup(),1500);}
async function resumeAutoBackup(){
    if(!isAutoBackupApp() || autoRunning || autoRequests.size)return;
    try{
        const result=await autoNative('status');autoEnabled=result.enabled;autoTarget=result.target||null;
        document.getElementById('autoBackupDisable').disabled=!autoEnabled;
        document.getElementById('autoBackupNow').disabled=!autoEnabled;
        autoMessage(result.message);
        if(autoEnabled){autoConfig=await autoKeyStore('get');if(!autoConfig){await resetAutoBackup();autoMessage('Chave local indisponível. Ative novamente com uma senha e escolha um novo arquivo.');return;}scheduleAutoBackup();}
    }catch(error){autoMessage(error.message);}
}
window.resumeAutoBackup=resumeAutoBackup;
async function resetAutoBackup(){
    autoEnabled=false;autoTarget=null;autoConfig=null;autoLastSnapshot=null;autoAgain=false;clearTimeout(autoTimer);
    for(const [id,request] of autoRequests){if(request.action==='stage'){clearTimeout(request.timeout);autoRequests.delete(id);request.reject(new Error('Backup automático desativado.'));}}
    if(isAutoBackupApp())await autoNative('disable');
    if(isAutoBackupApp())await autoKeyStore('delete');autoPayload=null;
    document.getElementById('autoBackupDisable').disabled=true;document.getElementById('autoBackupNow').disabled=true;
    autoMessage('Backup automático desativado. Arquivos já salvos no Drive foram mantidos.');
}
window.resetAutoBackup=resetAutoBackup;
async function enableAutoBackup(){
    const button=document.getElementById('autoBackupEnable');button.disabled=true;
    try{
        if(!isAutoBackupApp())throw new Error('Use o aplicativo Android, com o Google Drive instalado e sua conta adicionada.');
        if(autoEnabled)throw new Error('Desative o destino atual antes de escolher outra conta ou senha.');
        const status=await autoNative('status');
        if(status.enabled){autoEnabled=true;autoTarget=status.target;autoConfig=await autoKeyStore('get');throw new Error('Já existe um destino autorizado. Desative-o antes de escolher outra conta ou senha.');}
        const field=document.getElementById('autoBackupPassword'),password=field.value;field.value='';
        if(password.length<12)throw new Error('Escolha uma senha de pelo menos 12 caracteres. Guarde-a para recuperar seus dados.');
        const salt=crypto.getRandomValues(new Uint8Array(16)),key=await backupKey(password,salt,['encrypt']);
        const config={key,salt:base64Bytes(salt)};await autoKeyStore('put',config);
        const result=await autoNative('select');
        autoConfig=config;autoEnabled=true;autoTarget=result.target;autoLastSnapshot=null;
        document.getElementById('autoBackupDisable').disabled=false;document.getElementById('autoBackupNow').disabled=false;
        autoMessage(result.message);await queueAutoBackup(true);
    }catch(error){if(!autoEnabled)await autoKeyStore('delete').catch(()=>{});autoMessage(error.message);}
    finally{button.disabled=false;}
}
function setupAutoBackup(){
    const card=document.createElement('section');card.className='card';card.id='autoBackupCard';
    card.innerHTML=`<h2>Backup automático no Google Drive</h2>
      <p>Ative se quiser manter uma cópia criptografada dos seus registros. No seletor do Android, escolha Google Drive, sua conta e a pasta. Outros destinos compatíveis também podem aparecer.</p>
      <p class="sub">O app criará um arquivo exclusivo e passará a atualizá-lo após salvar alterações. O Drive cuida do envio à nuvem; internet e economia de bateria podem atrasar. Use um arquivo diferente em cada celular.</p>
      <div class="field"><label for="autoBackupPassword">Senha para recuperar o backup (mínimo de 12 caracteres)</label><input id="autoBackupPassword" type="password" autocomplete="new-password"></div>
      <p class="sub">Guarde a senha: ela não pode ser recuperada. Uma chave de criptografia fica neste aparelho para gerar as cópias automáticas. Fotos não são incluídas.</p>
      <div class="actions"><button id="autoBackupEnable" class="btn btn-primary">Ativar e escolher conta/pasta</button><button id="autoBackupNow" class="btn btn-secondary" disabled>Atualizar backup agora</button><button id="autoBackupDisable" class="btn btn-secondary" disabled>Desativar backup automático</button></div>
      <p id="autoBackupMessage" role="status" aria-live="polite">Desativado. Disponível no aplicativo Android. A restauração é manual em Importar backup; não mescla dados entre celulares.</p>`;
    document.querySelector('#settingsPage .settings-content .card').after(card);
    document.getElementById('autoBackupEnable').onclick=()=>void enableAutoBackup();
    document.getElementById('autoBackupNow').onclick=()=>void queueAutoBackup(true);
    document.getElementById('autoBackupDisable').onclick=()=>{void resetAutoBackup().catch(error=>autoMessage(error.message));};
    document.addEventListener('diarySaved',scheduleAutoBackup);
    document.getElementById('settingsBtn').addEventListener('click',()=>void resumeAutoBackup());
    document.getElementById('closeSettings').addEventListener('click',()=>{document.getElementById('autoBackupPassword').value='';});
    document.addEventListener('keydown',event=>{if(event.key==='Escape')document.getElementById('autoBackupPassword').value='';});
    document.addEventListener('visibilitychange',()=>{document.getElementById('autoBackupPassword').value='';if(document.visibilityState==='visible')void resumeAutoBackup();else if(autoEnabled)void queueAutoBackup();});
    setTimeout(()=>void resumeAutoBackup(),1000);
}
setupAutoBackup();
