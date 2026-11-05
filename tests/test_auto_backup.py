"""Encrypted automatic backup bridge. Synthetic data only; no account, Drive or cloud accessed."""
from pathlib import Path
from urllib.parse import urlparse
import os
import shutil
from playwright.sync_api import sync_playwright
ROOT=Path(__file__).resolve().parents[1]
ORIGIN='https://appassets.androidplatform.net/assets/www/'
MOCK='''()=>{
 window.nadoAutoBackupReady=true;
 window.nativeBackup={enabled:false,target:null};window.staged=[];window.autoCalls=[];window.autoFail=false;window.autoCancel=false;
 autoBackupTransport=url=>{
  const u=new URL(url),action=u.searchParams.get('action'),id=u.searchParams.get('id');autoCalls.push(action);
  let result;
  if(action==='select'){
   if(autoCancel)result={error:true,message:'Escolha de destino cancelada.'};
   else{nativeBackup={enabled:true,target:crypto.randomUUID(),message:'Destino autorizado.'};result=nativeBackup;}
  }else if(action==='disable'){nativeBackup={enabled:false,message:'Desativado.'};result=nativeBackup;}
  else if(action==='stage'){
   if(autoFail)result={error:true,message:'Synthetic storage failure'};
   else{
    const transfer=u.searchParams.get('transfer'),length=Number(u.searchParams.get('length'));let text='';
    for(let i=0;i<length;i+=32768)text+=autoBackupChunk(transfer,i,Math.min(32768,length-i));
    if(text.length!==length || !nativeBackup.enabled || u.searchParams.get('target')!==nativeBackup.target)throw new Error('Invalid transfer');
    staged.push(JSON.parse(text));result={message:'Cópia criptografada preparada.'};
   }
  }else result=nativeBackup;
  setTimeout(()=>receiveAutoBackup(id,result),action==='status' && window.autoStatusDelay?window.autoStatusDelay:10);
 };
}'''

def main():
 with sync_playwright() as p:
  browser=p.chromium.launch(executable_path=os.environ.get('CHROME_PATH') or shutil.which('chromium') or shutil.which('google-chrome'),args=['--no-sandbox'])
  context=browser.new_context(user_agent='NadoFoss/1',viewport={'width':390,'height':844},service_workers='block')
  external,errors=[],[]
  def route(r):
   parsed=urlparse(r.request.url)
   if parsed.netloc!='appassets.androidplatform.net':external.append(r.request.url);r.abort();return
   path=ROOT/parsed.path.removeprefix('/assets/www/')
   if path.is_file():r.fulfill(path=path,content_type={'.js':'application/javascript','.html':'text/html','.css':'text/css'}.get(path.suffix))
   else:r.fulfill(status=404,body='Not found')
  context.route('**/*',route)
  page=context.new_page();page.on('pageerror',lambda e:errors.append(str(e)))
  page.goto(ORIGIN+'index.html');page.wait_for_function('()=>currentUser && !hydrating')
  page.locator('#settingsBtn').click();page.locator('#autoBackupEnable').click()
  assert 'Android' in page.locator('#autoBackupMessage').inner_text()
  assert page.evaluate('autoEnabled') is False
  page.evaluate(MOCK)
  page.locator('#autoBackupPassword').fill('short');page.locator('#autoBackupEnable').click()
  page.wait_for_function('()=>!document.getElementById("autoBackupEnable").disabled')
  assert page.evaluate('autoCalls.filter(a=>a!=="status").length')==0
  password='synthetic-drive-password-123'
  # Canceling consent leaves no encryption key and no transfer.
  page.evaluate('autoCancel=true');page.locator('#autoBackupPassword').fill(password);page.locator('#autoBackupEnable').click()
  page.wait_for_function('()=>!document.getElementById("autoBackupEnable").disabled')
  assert page.evaluate('autoKeyStore("get")') is None
  assert page.evaluate('staged.length')==0
  page.evaluate('autoCancel=false');page.locator('#autoBackupPassword').fill(password);page.locator('#autoBackupEnable').click()
  page.wait_for_function('()=>staged.length===1 && !autoRunning')
  assert page.locator('#autoBackupPassword').input_value()==''
  assert page.evaluate('autoEnabled') is True
  assert page.evaluate('autoPayload') is None
  assert page.evaluate('autoBackupChunk("unknown",0,32768)') is None
  assert page.evaluate('''async()=>{const c=await autoKeyStore('get');try{await crypto.subtle.exportKey('raw',c.key);return false;}catch(e){return c.key.extractable===false;}}''')
  assert page.evaluate('p=>decodeDiary(staged[0],p).then(d=>d.plan.length>0)',password)
  assert page.evaluate('JSON.stringify(staged).includes("synthetic-drive-password")') is False
  # Changes queue a new encrypted snapshot; unchanged snapshots are skipped.
  page.locator('#closeSettings').click();page.locator('#weightKg').fill('71.2');page.locator('#weightForm button[type=submit]').click()
  page.wait_for_function('()=>staged.length===2 && !autoRunning')
  assert page.evaluate('p=>decodeDiary(staged.at(-1),p).then(d=>d.weights[0].kg)',password)==71.2
  page.evaluate('queueAutoBackup()');assert page.evaluate('staged.length')==2
  page.evaluate('queueAutoBackup(true)');assert page.evaluate('staged.length')==3
  assert page.evaluate('staged[1].iv!==staged[2].iv')
  assert page.evaluate('staged[1].salt===staged[2].salt')
  # A failed queue does not mark the changed data as backed up; retry uses latest records.
  page.evaluate('autoFail=true;state.planHint="Synthetic pending update";save()')
  page.wait_for_function('()=>document.getElementById("autoBackupMessage").textContent.includes("Synthetic storage failure")')
  assert page.evaluate('staged.length')==3
  page.evaluate('autoFail=false;queueAutoBackup()');page.wait_for_function('()=>staged.length===4 && !autoRunning')
  assert page.evaluate('p=>decodeDiary(staged.at(-1),p).then(d=>d.planHint)',password)=='Synthetic pending update'
  # The stored nonextractable key survives page restarts; restoration remains manual.
  native=page.evaluate('nativeBackup');before=page.evaluate('JSON.stringify(readRecords())')
  page.reload();page.wait_for_function('()=>currentUser && !hydrating');page.evaluate(MOCK)
  page.evaluate('v=>{nativeBackup=v;}',native);page.evaluate('resumeAutoBackup()')
  page.wait_for_function('()=>autoEnabled && autoConfig && staged.length===1 && !autoRunning')
  assert page.evaluate('JSON.stringify(readRecords())')==before
  assert page.evaluate('p=>decodeDiary(staged[0],p).then(d=>d.weights.length)',password)==1
  # Oversized envelopes are rejected before native staging; prior copy and local records remain.
  count=page.evaluate('staged.length')
  page.evaluate('window.oldHint=state.planHint;state.planHint="x".repeat(5*1024*1024);queueAutoBackup()')
  page.wait_for_function('()=>!autoRunning');assert page.evaluate('staged.length')==count
  assert '5 MB' in page.locator('#autoBackupMessage').inner_text()
  page.evaluate('state.planHint=window.oldHint')
  # Losing the local key disables the target; it never uploads a cleartext or empty replacement.
  page.evaluate('autoKeyStore("delete")');page.evaluate('resumeAutoBackup()')
  page.wait_for_function('()=>!nativeBackup.enabled && !autoEnabled')
  assert page.evaluate('staged.length')==count
  assert page.evaluate('JSON.stringify(readRecords())')==before
  # A late status reply cannot reactivate a target after explicit disconnection.
  page.locator('#settingsBtn').click();page.locator('#autoBackupPassword').fill(password);page.locator('#autoBackupEnable').click()
  page.wait_for_function('()=>autoEnabled && !autoRunning && !autoManaging')
  count=page.evaluate('staged.length')
  page.evaluate('autoStatusDelay=300;resumeAutoBackup();resetAutoBackup()')
  page.wait_for_timeout(400)
  assert page.evaluate('!autoEnabled && !nativeBackup.enabled')
  assert page.evaluate('staged.length')==count
  page.evaluate('autoStatusDelay=0');page.locator('#closeSettings').click()
  # Disable/erase disconnect before the local diary is cleared, preserving the existing cloud copy.
  page.locator('#settingsBtn').click();page.locator('#autoBackupPassword').fill(password);page.locator('#autoBackupEnable').click()
  page.wait_for_function('()=>autoEnabled && !autoRunning && !document.getElementById("autoBackupEnable").disabled')
  count=page.evaluate('staged.length');page.on('dialog',lambda d:d.accept());page.locator('#eraseDiary').click()
  page.wait_for_function('()=>state.weights.length===0 && !autoEnabled')
  assert page.evaluate('staged.length')==count
  assert page.evaluate('autoKeyStore("get")') is None
  assert page.evaluate('autoCalls.at(-1)')=='disable'
  for theme in ['light','dark']:
   page.evaluate('v=>setAppearance(v)',theme)
   for width in [320,390,800,1280]:
    page.set_viewport_size({'width':width,'height':844})
    assert page.locator('body').evaluate('(el)=>el.scrollWidth<=innerWidth'),(theme,width)
  assert not external,external
  assert not errors,errors
  print('PASS: explicit consent/cancel, Android fallback, persistent nonextractable key, compatible encrypted restore, automatic changes, nonce uniqueness, deduplication, failed-stage retry, size limit, restart, missing-key disconnect, erase preservation, responsive UI, no cloud requests.')
  browser.close()

if __name__=='__main__':main()
