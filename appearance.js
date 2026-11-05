/* SPDX-License-Identifier: Apache-2.0 */
'use strict';
const APPEARANCE_KEY='nadoDiary.appearance.v1';
let appearanceMode='auto';
let nativeColorScheme=null;
try { const saved=localStorage.getItem(APPEARANCE_KEY);if(['auto','light','dark'].includes(saved))appearanceMode=saved; } catch(error) { }
const appearanceMedia=window.matchMedia('(prefers-color-scheme: dark)');
function applyAppearance(notifyNative=true){
    const resolved=appearanceMode==='auto'?(nativeColorScheme || (appearanceMedia.matches?'dark':'light')):appearanceMode;
    document.documentElement.dataset.theme=resolved;
    document.documentElement.dataset.appearance=appearanceMode;
    const meta=document.querySelector('meta[name="theme-color"]');if(meta)meta.content=resolved==='light'?'#f1f7fa':'#071b2a';
    const select=document.getElementById('appearanceSelect');if(select)select.value=appearanceMode;
    if(notifyNative && window.nadoNativeReady)window.location.href='nado://appearance?theme='+resolved;
    return resolved;
}
window.setNativeColorScheme=dark=>{nativeColorScheme=dark?'dark':'light';return applyAppearance(false);};
function setAppearance(mode){
    if(!['auto','light','dark'].includes(mode))return;
    appearanceMode=mode;
    try{localStorage.setItem(APPEARANCE_KEY,mode);}catch(error){if(window.toast)toast('Tema aplicado; não foi possível guardar a preferência.');}
    applyAppearance();
}
if(appearanceMedia.addEventListener)appearanceMedia.addEventListener('change',()=>{if(appearanceMode==='auto')applyAppearance();});
else if(appearanceMedia.addListener)appearanceMedia.addListener(()=>{if(appearanceMode==='auto')applyAppearance();});
applyAppearance(false);
document.addEventListener('DOMContentLoaded',()=>{
    const label=document.createElement('label');label.className='appearance-control';
    label.innerHTML='<span class="sr">Tema do aplicativo</span><select id="appearanceSelect" aria-label="Tema do aplicativo"><option value="auto">Tema: automático</option><option value="light">Tema: claro</option><option value="dark">Tema: escuro</option></select>';
    document.querySelector('.top-actions').prepend(label);
    label.querySelector('select').onchange=event=>setAppearance(event.target.value);
    applyAppearance(false);
});
