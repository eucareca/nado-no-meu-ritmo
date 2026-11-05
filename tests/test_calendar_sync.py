"""Synthetic native calendar bridge tests. No Google account or actual calendar is read or changed."""
from pathlib import Path
from urllib.parse import urlparse
import os
import shutil
from playwright.sync_api import sync_playwright
ROOT=Path(__file__).resolve().parents[1]
ORIGIN='https://appassets.androidplatform.net/assets/www/'


def main():
 with sync_playwright() as p:
  browser=p.chromium.launch(executable_path=os.environ.get('CHROME_PATH') or shutil.which('chromium') or shutil.which('google-chrome'),args=['--no-sandbox'])
  context=browser.new_context(viewport={'width':390,'height':844},timezone_id='America/Sao_Paulo',service_workers='block',user_agent='NadoFoss/1')
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
  page.locator('#calendarConnect').click()
  assert 'APK Android' in page.locator('#calendarSyncMessage').inner_text()
  for day in [1,3,5]:page.locator(f'[data-training-day="{day}"]').check()
  page.locator('#trainingForm button[type=submit]').click()
  page.wait_for_function('()=>state.trainingSchedule?.revisions.length===1')
  page.evaluate('''()=>{window.calendarRequests=[];window.trainingRequests=[];window.nadoNativeReady=true;calendarSyncTransport=url=>window.calendarRequests.push(url);sendTrainingNative=action=>window.trainingRequests.push(action);}''')
  def reply(value):
   page.evaluate('value=>receiveCalendarSync(calendarSyncPending,value)',value)
  page.locator('#calendarConnect').click()
  assert 'action=connect' in page.evaluate('window.calendarRequests.at(-1)')
  reply({'state':'error','message':'Acesso ao calendário não autorizado.'})
  assert page.evaluate('calendarSyncConnected') is False
  assert page.evaluate('state.trainingSchedule.revisions.length')==1
  page.locator('#calendarConnect').click()
  reply({'state':'connected','message':'Agenda conectada e série criada.'})
  assert page.evaluate('calendarSyncConnected') is True
  # Automatic check only after permission and a connection already exist.
  page.evaluate('syncCalendarOnResume()')
  assert 'action=status' in page.evaluate('window.calendarRequests.at(-1)')
  reply({'state':'status','connected':True,'granted':True,'message':'Conferindo'})
  assert 'action=sync' in page.evaluate('window.calendarRequests.at(-1)')
  reply({'state':'synced','message':'Em dia'})
  # Remote edits are stored as a revision; only after the local write succeeds is an ack sent.
  page.locator('#calendarSyncNow').click()
  remote={'days':[2,4],'time':'19:00','durationMinutes':60,'reminderMinutes':999,'notBefore':1794333600000}
  reply({'state':'pulled','schedule':remote,'message':'Recebido da agenda'})
  page.wait_for_function('()=>calendarSyncAction==="ack"')
  assert page.locator('#trainingTime').input_value()=='19:00'
  assert page.evaluate('state.trainingSchedule.revisions.at(-1).days')==[2,4]
  assert page.evaluate('state.trainingSchedule.revisions.at(-1).reminderMinutes')==30  # Independent local reminders.
  assert page.evaluate('state.trainingSchedule.revisions.length')==2
  assert page.evaluate('state.trainingSchedule.revisions.at(-1).startsAt')
  reply({'state':'synced','message':'Recebido e salvo'})
  page.locator('#calendarSyncNow').click()
  reply({'state':'conflict','message':'Horário mudou nos dois lados'})
  assert page.locator('#calendarConflict').is_visible()
  assert not page.evaluate('window.calendarRequests.at(-1).includes("action=push")')
  page.locator('#calendarUseApp').click()
  assert 'action=push' in page.evaluate('window.calendarRequests.at(-1)')
  reply({'state':'synced','message':'Plano do diário enviado'})
  # Failed local storage must never acknowledge remote changes or send an old plan back.
  previous=page.evaluate('JSON.stringify(state.trainingSchedule)')
  page.evaluate('''()=>{window.calendarOriginalSet=Storage.prototype.setItem;Storage.prototype.setItem=function(key,value){if(key===RECORDS_KEY)throw new Error('Synthetic storage failure');return window.calendarOriginalSet.call(this,key,value);};}''')
  page.locator('#calendarSyncNow').click()
  reply({'state':'pulled','schedule':dict(remote,time='20:00'),'message':'Recebido'})
  page.wait_for_function('()=>!calendarSyncBusy')
  assert page.evaluate('JSON.stringify(state.trainingSchedule)')==previous
  assert page.evaluate('calendarSyncPending') is None
  assert 'action=sync' in page.evaluate('window.calendarRequests.at(-1)')
  assert 'Não foi possível salvar' in page.locator('#calendarSyncMessage').inner_text()
  page.evaluate('()=>{Storage.prototype.setItem=window.calendarOriginalSet;}')
  page.locator('#calendarSyncNow').click()
  reply({'state':'deleted','message':'A série foi excluída. Plano pausado.'})
  page.wait_for_function('()=>!calendarSyncBusy')
  assert page.evaluate('calendarSyncConnected') is False
  assert page.evaluate('state.trainingSchedule.revisions.at(-1).days')==[]
  assert page.evaluate('state.trainingSchedule.remindersEnabled') is False
  assert page.locator('[data-training-day]:checked').count()==0
  # An import/erasure disconnects local linking without requesting external event deletion.
  page.evaluate('resetCalendarSync()')
  assert 'action=disconnect' in page.evaluate('window.calendarRequests.at(-1)')
  before=page.evaluate('window.calendarRequests.length')
  page.evaluate('syncCalendarOnResume()')
  assert page.evaluate('window.calendarRequests.length')==before
  assert not page.evaluate('window.calendarRequests.some(url=>url.includes("action=delete"))')
  # Existing historical/future scheduling rules continue to respect a series start date.
  assert page.evaluate('''()=>{const s={revisions:[{days:[1,2,3,4,5,6,0],time:'18:00',durationMinutes:45,reminderMinutes:30,effectiveAt:'2026-11-01T00:00:00Z',startsAt:'2026-11-10T00:00:00Z'}]};return trainingSlots(s,new Date('2026-11-02T00:00:00Z'),new Date('2026-11-09T00:00:00Z')).length===0;}''')
  for width in [320,390,800,1280]:
   page.set_viewport_size({'width':width,'height':844})
   assert page.locator('body').evaluate('(el)=>el.scrollWidth<=innerWidth'),width
  assert not external,external
  assert not errors,errors
  print('PASS: optional calendar consent, connection lifecycle, foreground sync, remote revision/ack, future start dates, independent reminders, explicit conflicts, failed-write protection, deletion pause, unlink without deletion, responsive layout, no external requests.')
  browser.close()

if __name__=='__main__':main()
