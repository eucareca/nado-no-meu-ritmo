/* SPDX-License-Identifier: Apache-2.0 */
'use strict';
const BACKUP_FORMAT = 'nado-no-meu-ritmo';
const BACKUP_LIMIT = 5 * 1024 * 1024;
const BACKUP_ROUNDS = 310000;
const EMAIL_KEY = 'nadoDiary.backupEmail.v1';
const isAndroidApp = () => navigator.userAgent.includes('NadoFoss/1');
let preparedBackup = null;

function backupText(value, max = 2000) {
    if (value === undefined || value === null) return '';
    if (typeof value !== 'string' || value.length > max) throw new Error('Texto inválido ou muito longo.');
    return value;
}
function backupNumber(value, min, max) {
    const n = Number(value);
    if (!Number.isFinite(n) || n < min || n > max) throw new Error('Número fora do intervalo permitido.');
    return n;
}
function backupDate(value) {
    if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value)) throw new Error('Data inválida.');
    const date = new Date(value + 'T12:00:00Z');
    if (!Number.isFinite(date.getTime()) || date.toISOString().slice(0, 10) !== value) throw new Error('Data inválida.');
    return value;
}
function backupPlan(plan) {
    if (!Array.isArray(plan) || !plan.length || plan.length > 64) throw new Error('Plano inválido.');
    return plan.map(b => ({name: backupText(b.name, 160), minutes: backupNumber(b.minutes, 1, 1440),
        type: ['nado', 'serie', 'descanso', 'leve'].includes(b.type) ? b.type : 'nado', detail: backupText(b.detail)}));
}
function validatedDiary(raw) {
    if (!raw || typeof raw !== 'object' || Array.isArray(raw)) throw new Error('Formato de diário inválido.');
    const list = name => {
        const rows = raw[name] || [];
        if (!Array.isArray(rows) || rows.length > 10000) throw new Error('Quantidade de registros inválida.');
        return rows;
    };
    return {
        plan: backupPlan(raw.plan), planHint: backupText(raw.planHint),
        sessions: list('sessions').map(s => ({id: crypto.randomUUID(), date: backupDate(s.date),
            duration: backupNumber(s.duration, 1, 1440), effort: ['Confortável', 'Alto', 'Muito alto'].includes(s.effort) ? s.effort : 'Confortável',
            notes: backupText(s.notes), next: backupText(s.next), actualBlocks: backupText(s.actualBlocks, 8000),
            planMinutes: backupNumber(s.planMinutes || 30, 1, 92160), plan: backupPlan(s.plan || raw.plan)})),
        weights: list('weights').map(w => ({id: crypto.randomUUID(), date: backupDate(w.date), kg: backupNumber(w.kg, 1, 1000), notes: backupText(w.notes)})),
        activities: list('activities').map(a => ({id: crypto.randomUUID(), date: backupDate(a.date), type: backupText(a.type, 120), minutes: backupNumber(a.minutes, 1, 1440), notes: backupText(a.notes)}))
    };
}
function base64Bytes(bytes) {
    let result = '';
    for (let i = 0; i < bytes.length; i += 32768) result += String.fromCharCode(...bytes.subarray(i, i + 32768));
    return btoa(result);
}
function fromBase64(value) {
    if (typeof value !== 'string' || value.length > BACKUP_LIMIT * 2) throw new Error('Backup inválido.');
    return Uint8Array.from(atob(value), c => c.charCodeAt(0));
}
async function backupKey(password, salt, usage) {
    const material = await crypto.subtle.importKey('raw', new TextEncoder().encode(password), 'PBKDF2', false, ['deriveKey']);
    return crypto.subtle.deriveKey({name: 'PBKDF2', hash: 'SHA-256', salt, iterations: BACKUP_ROUNDS}, material,
        {name: 'AES-GCM', length: 256}, false, usage);
}
async function encryptDiary(diary, password) {
    if (password.length < 12) throw new Error('Use uma senha de backup com pelo menos 12 caracteres.');
    const salt = crypto.getRandomValues(new Uint8Array(16));
    const iv = crypto.getRandomValues(new Uint8Array(12));
    const key = await backupKey(password, salt, ['encrypt']);
    const data = new TextEncoder().encode(JSON.stringify(diary));
    const encrypted = await crypto.subtle.encrypt({name: 'AES-GCM', iv, additionalData: new TextEncoder().encode(BACKUP_FORMAT)}, key, data);
    return {format: BACKUP_FORMAT, version: 1, encrypted: true, algorithm: 'AES-256-GCM', kdf: 'PBKDF2-SHA256',
        iterations: BACKUP_ROUNDS, salt: base64Bytes(salt), iv: base64Bytes(iv), data: base64Bytes(new Uint8Array(encrypted))};
}
async function decodeDiary(backup, password) {
    if (backup.format !== BACKUP_FORMAT) return validatedDiary(backup); // Earlier local exports.
    if (backup.version !== 1) throw new Error('Versão de backup não suportada.');
    if (!backup.encrypted) return validatedDiary(backup.diary);
    if (backup.algorithm !== 'AES-256-GCM' || backup.kdf !== 'PBKDF2-SHA256' || backup.iterations !== BACKUP_ROUNDS) throw new Error('Proteção de backup não suportada.');
    const salt = fromBase64(backup.salt), iv = fromBase64(backup.iv);
    if (salt.length !== 16 || iv.length !== 12) throw new Error('Backup protegido inválido.');
    try {
        const key = await backupKey(password, salt, ['decrypt']);
        const clear = await crypto.subtle.decrypt({name: 'AES-GCM', iv, additionalData: new TextEncoder().encode(BACKUP_FORMAT)}, key, fromBase64(backup.data));
        return validatedDiary(JSON.parse(new TextDecoder().decode(clear)));
    } catch (_) { throw new Error('Senha incorreta ou arquivo danificado.'); }
}
async function makeBackup() {
    const diary = {plan: state.plan, planHint: state.planHint || '', sessions: state.sessions, weights: state.weights, activities: state.activities};
    const protect = document.getElementById('protectBackup').checked;
    const password = document.getElementById('backupPassword').value;
    const result = protect ? await encryptDiary(diary, password) : {format: BACKUP_FORMAT, version: 1, encrypted: false, diary};
    const text = JSON.stringify(result, null, 2);
    if (new TextEncoder().encode(text).length > BACKUP_LIMIT) throw new Error('O backup excede o limite de 5 MB.');
    return text;
}
function exportDiary() {
    document.getElementById('settingsPage').classList.add('open');
    document.getElementById('backupPassword').focus();
}
async function prepareExport(share) {
    const message = document.getElementById('settingsMessage');
    try {
        const email = document.getElementById('backupEmail').value.trim();
        if (share && (!email || !document.getElementById('backupEmail').checkValidity())) throw new Error('Informe seu e-mail para receber o backup.');
        if (share && !document.getElementById('protectBackup').checked && !confirm('O arquivo contém seus registros em texto aberto. Continuar para escolher o aplicativo de e-mail?')) return;
        const text = await makeBackup();
        localStorage.setItem(EMAIL_KEY, email);
        document.getElementById('backupPassword').value = '';
        if (isAndroidApp()) {
            preparedBackup = {text, email};
            location.href = share ? 'nado://share-backup' : 'nado://save-backup';
            message.textContent = share ? 'Escolha seu aplicativo de e-mail, revise o destinatário e confirme o envio nele.' : 'Escolha onde salvar o arquivo.';
        } else {
            const file = new File([text], 'nado-backup.json', {type: 'application/json'});
            if (share && navigator.canShare && navigator.canShare({files: [file]})) {
                await navigator.share({files: [file], title: 'Backup do Nado no meu ritmo'});
            } else {
                const url = URL.createObjectURL(file), link = document.createElement('a');
                link.href = url; link.download = file.name; link.click();
                setTimeout(() => URL.revokeObjectURL(url), 1000);
                message.textContent = share ? 'Backup baixado. Anexe o arquivo em uma mensagem para seu e-mail.' : 'Backup baixado.';
            }
        }
    } catch (error) { message.textContent = error.message; }
}
// Only the bundled Android activity consumes this temporary payload; passwords are never stored here.
function takePreparedBackup() {
    const value = preparedBackup;
    preparedBackup = null;
    return value;
}
async function passwordForImport() {
    return new Promise(resolve => {
        const dialog = document.createElement('dialog');
        dialog.className = 'password-dialog';
        dialog.innerHTML = '<form method="dialog"><h2>Abrir backup protegido</h2><label>Senha do backup<input type="password" autocomplete="off" required></label><p>A senha não é enviada nem armazenada.</p><div class="actions"><button value="cancel" formnovalidate>Cancelar</button><button value="open">Abrir</button></div></form>';
        document.body.append(dialog);
        dialog.addEventListener('close', () => {
            const password = dialog.returnValue === 'open' ? dialog.querySelector('input').value : null;
            dialog.remove(); resolve(password);
        }, {once: true});
        dialog.showModal();
    });
}
async function importDiary(file) {
    const message = document.getElementById('settingsMessage');
    try {
        if (!file || file.size > BACKUP_LIMIT) throw new Error('Escolha um backup de até 5 MB.');
        const raw = JSON.parse(await file.text());
        let password = '';
        if (raw.encrypted) { password = await passwordForImport(); if (password === null) return; }
        const diary = await decodeDiary(raw, password);
        if (!confirm('Importar este backup vai substituir os registros locais. Deseja continuar?')) return;
        const db = diaryRecords(diary);
        // Replace the full diary atomically after validation, decryption, and confirmation.
        localStorage.setItem(RECORDS_KEY, JSON.stringify(db));
        await enterApp({id: 'local'});
        message.textContent = 'Backup importado.';
        toast('Backup importado.');
    } catch (error) { message.textContent = 'Não foi possível importar: ' + error.message; toast('Não foi possível importar o arquivo.'); }
}
function setupSettings() {
    const page = document.createElement('section');
    page.id = 'settingsPage'; page.className = 'settings-page';
    page.setAttribute('aria-label', 'Configurações');
    page.innerHTML = `<div class="settings-content"><div class="modal-head"><h1>Configurações</h1><button id="closeSettings" class="btn btn-secondary">Voltar ao diário</button></div>
      <section class="card"><h2>Backup dos seus registros</h2><p>Os registros ficam somente neste aparelho. Faça backups para não perdê-los ao desinstalar o aplicativo ou trocar de celular.</p>
      <div class="field"><label for="backupEmail">Seu e-mail</label><input id="backupEmail" type="email" autocomplete="email" placeholder="voce@exemplo.com"></div>
      <p><label><input id="protectBackup" type="checkbox" checked> Proteger o backup com senha</label></p>
      <div class="field"><label for="backupPassword">Senha do backup (mínimo de 12 caracteres)</label><input id="backupPassword" type="password" autocomplete="new-password"></div>
      <p class="sub">A senha não é guardada. Sem ela, um backup protegido não pode ser recuperado. Não envie a senha junto com o arquivo.</p>
      <div class="actions"><button id="saveBackup" class="btn btn-primary">Salvar backup</button><button id="emailBackup" class="btn btn-secondary">Enviar backup por e-mail</button><button id="restoreBackup" class="btn btn-secondary">Importar backup</button></div>
      <p id="settingsMessage" role="status" aria-live="polite"></p></section>
      <section class="card"><h2>Privacidade</h2><p>Este aplicativo não tem login, anúncios, rastreadores nem sincronização. Não tem permissão de internet no Android. Sessões, peso, atividades e plano ficam no armazenamento privado do aplicativo, sem criptografia própria em repouso. A proteção do aparelho e do bloqueio de tela é importante.</p><p>Ao compartilhar um backup, somente o arquivo e o e-mail informado são entregues ao aplicativo que você escolher. Ele passa a ser responsável pelo envio e pelo tratamento desses dados. O Nado no meu ritmo nunca envia mensagens automaticamente.</p><p>Os backups automáticos do sistema e a transferência automática entre aparelhos estão desativados. O backup manual pode ser criptografado com AES-256-GCM e uma senha escolhida por você.</p><p>Suporte: Tiago Assis Brasil · tiago@tabinformatica.com.br<br>tabinformatica.com.br/nadonoseuritmo<br>Licença Apache-2.0 · versão 1.4.0</p></section>
      <section class="card"><h2>Apagar registros locais</h2><p>Esta ação não apaga arquivos já exportados, e-mails ou anexos enviados.</p><button id="eraseDiary" class="btn btn-secondary">Apagar todos os registros deste aparelho</button></section></div>`;
    document.body.append(page);
    document.getElementById('backupEmail').value = localStorage.getItem(EMAIL_KEY) || '';
    document.getElementById('settingsBtn').onclick = () => page.classList.add('open');
    document.getElementById('closeSettings').onclick = () => { page.classList.remove('open'); document.getElementById('backupPassword').value = ''; };
    document.getElementById('saveBackup').onclick = () => prepareExport(false);
    document.getElementById('emailBackup').onclick = () => prepareExport(true);
    document.getElementById('restoreBackup').onclick = () => document.getElementById('importFile').click();
    document.getElementById('importBtn').onclick = () => { page.classList.add('open'); document.getElementById('importFile').click(); };
    document.getElementById('importFile').onchange = async event => { await importDiary(event.target.files[0]); event.target.value = ''; };
    document.getElementById('protectBackup').onchange = event => { document.getElementById('backupPassword').disabled = !event.target.checked; };
    document.getElementById('eraseDiary').onclick = async () => {
        if (!confirm('Apagar todos os registros locais? Faça um backup antes de continuar.')) return;
        localStorage.removeItem(RECORDS_KEY); localStorage.removeItem(activeCacheKey); localStorage.removeItem(EMAIL_KEY); localStorage.removeItem('nadoMeuRitmo.v1');
        document.getElementById('backupEmail').value = ''; document.getElementById('backupPassword').value = '';
        await enterApp({id: 'local'}); toast('Registros locais apagados.');
    };
    document.addEventListener('keydown', event => {
        if (event.key === 'Escape') { page.classList.remove('open'); document.getElementById('backupPassword').value = ''; }
    });
}

function diaryRecords(diary) {
    return {
            swim_settings: [{user_id: 'local', plan: diary.plan, plan_hint: diary.planHint}],
            swim_sessions: diary.sessions.map(s => ({id: s.id, user_id: 'local', session_date: s.date, duration_minutes: s.duration, effort: s.effort, notes: s.notes, next_progression: s.next, actual_blocks: s.actualBlocks, planned_minutes: s.planMinutes, planned_blocks: s.plan})),
            swim_weight_entries: diary.weights.map(w => ({id: w.id, user_id: 'local', measurement_date: w.date, weight_kg: w.kg, notes: w.notes})),
            swim_extra_activities: diary.activities.map(a => ({id: a.id, user_id: 'local', activity_date: a.date, activity_type: a.type, duration_minutes: a.minutes, notes: a.notes}))
        };

}

function migrateLegacyLocal() {
    const legacyKey = 'nadoMeuRitmo.v1';
    const legacy = localStorage.getItem(legacyKey);
    if (!localStorage.getItem(RECORDS_KEY) && legacy) {
        const diary = validatedDiary(JSON.parse(legacy));
        localStorage.setItem(RECORDS_KEY, JSON.stringify(diaryRecords(diary)));
        localStorage.removeItem(legacyKey);
    }
    // Old sign-in credentials are no longer used by this local-only application.
    for (const key of ['nadoMeuRitmo.nativeAuth.v1', 'nadoMeuRitmo.nativePkce.v1', 'nadoMeuRitmo.cloudMigrationDone']) localStorage.removeItem(key);
}
