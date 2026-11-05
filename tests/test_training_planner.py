"""Local planning/attendance/calendar tests with synthetic records and a fixed phone timezone."""
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
        context=browser.new_context(viewport={'width':390,'height':844},timezone_id='America/Sao_Paulo',service_workers='block',accept_downloads=True)
        external,errors=[],[]
        def route(r):
            parsed=urlparse(r.request.url)
            if parsed.netloc!='appassets.androidplatform.net':external.append(r.request.url);r.abort();return
            path=ROOT/parsed.path.removeprefix('/assets/www/')
            if path.is_file():r.fulfill(path=path,content_type={'.js':'application/javascript','.html':'text/html','.css':'text/css'}.get(path.suffix))
            else:r.fulfill(status=404,body='Not found')
        context.route('**/*',route)
        page=context.new_page();page.on('pageerror',lambda error:errors.append(str(error)))
        page.goto(ORIGIN+'index.html');page.wait_for_function('()=>currentUser && !hydrating')
        page.clock.set_fixed_time('2026-11-06T15:00:00Z')
        page.locator('#trainingForm button[type=submit]').click()
        assert 'dia válido' in page.locator('#trainingMessage').inner_text()
        for day in [1,3,5]:page.locator(f'[data-training-day="{day}"]').check()
        page.locator('#trainingTime').fill('18:30')
        page.locator('#trainingDuration').fill('45')
        page.locator('#trainingAdvance').fill('30')
        page.locator('#trainingForm button[type=submit]').click()
        page.wait_for_function('()=>state.trainingSchedule?.revisions.length===1')
        assert page.evaluate('state.trainingSchedule.remindersEnabled') is False
        assert '0/1' in page.locator('#trainingAttendance').inner_text()  # Only future Friday; no retroactive Mon/Wed plan.
        page.locator('#trainingForm button[type=submit]').click()
        assert page.evaluate('state.trainingSchedule.revisions.length')==1
        page.reload();page.wait_for_function('()=>!hydrating && state.trainingSchedule?.revisions.length===1')
        assert page.locator('#trainingTime').input_value()=='18:30'
        assert page.locator('[data-training-day]:checked').count()==3
        # Historical plans and attendance are based on distinct recorded days, never calendar events.
        data=page.evaluate('''()=>{
            const schedule=validateTrainingSchedule({revisions:[{effectiveAt:'2026-11-02T03:00:00Z',days:[1,3,5],time:'18:00',durationMinutes:45,reminderMinutes:30}],remindersEnabled:false});
            const sessions=[{date:'2026-11-02'},{date:'2026-11-02'},{date:'2026-11-03'},{date:'2026-11-09'}];
            const stats=trainingAttendance(schedule,sessions,new Date('2026-11-06T15:00:00Z'));
            return {planned:stats.planned,done:stats.done,waiting:stats.waiting,extra:stats.extra};}''')
        assert data['planned']==['2026-11-02','2026-11-04','2026-11-06']
        assert data['done']==['2026-11-02'] and data['waiting']==['2026-11-04']
        assert data['extra']==['2026-11-03']
        changed=page.evaluate('''()=>{const schedule=validateTrainingSchedule({revisions:[{effectiveAt:'2026-11-02T03:00:00Z',days:[1,3,5],time:'18:00',durationMinutes:45,reminderMinutes:30},{effectiveAt:'2026-11-05T03:00:00Z',days:[2,4],time:'19:00',durationMinutes:60,reminderMinutes:20}]});return trainingAttendance(schedule,[],new Date('2026-11-06T15:00:00Z')).planned;}''')
        assert changed==['2026-11-02','2026-11-04','2026-11-05']
        # Export is an explicit user action; content includes weekly recurrence and a real next date.
        with page.expect_download() as downloaded:page.locator('#trainingIcs').click()
        text=Path(downloaded.value.path()).read_text()
        assert 'RRULE:FREQ=WEEKLY;BYDAY=MO,WE,FR;WKST=MO' in text
        assert 'DTSTART:20261106T183000' in text and 'DTEND:20261106T191500' in text
        assert 'TRIGGER:-PT30M' in text
        page.locator('#trainingEnable').click()
        assert 'APK Android' in page.locator('#trainingMessage').inner_text()
        # Restore preserves revision history but never silently reactivates phone reminders.
        page.evaluate('state.trainingSchedule.remindersEnabled=true')
        encrypted=page.evaluate('encryptDiary(state,"synthetic-planner-password")')
        diary=page.evaluate('value=>decodeDiary(value,"synthetic-planner-password")',encrypted)
        assert diary['trainingSchedule']['revisions'][0]['days']==[1,3,5]
        assert diary['trainingSchedule']['remindersEnabled'] is False
        page.evaluate('async diary=>{localStorage.setItem(RECORDS_KEY,JSON.stringify(diaryRecords(diary)));await enterApp({id:"local"});}',diary)
        assert page.locator('#trainingTime').input_value()=='18:30'
        # Old backups with no schedule remain valid.
        assert page.evaluate('()=>{const old={...state};delete old.trainingSchedule;return validatedDiary(old).trainingSchedule===null;}')
        assert page.evaluate('''()=>{try{validateTrainingSchedule({revisions:[{days:[1,1],time:'18:00',effectiveAt:'2026-11-01T00:00:00Z',durationMinutes:45,reminderMinutes:30}]});return false}catch(e){return true}}''')
        before=page.evaluate('JSON.stringify(state.trainingSchedule)')
        page.clock.set_fixed_time('2026-11-06T16:00:00Z')
        page.locator('#trainingTime').fill('19:00')
        page.evaluate('''()=>{window.originalPlannerSet=Storage.prototype.setItem;Storage.prototype.setItem=function(key,value){if(key===RECORDS_KEY)throw new Error('Synthetic quota failure');return window.originalPlannerSet.call(this,key,value);};}''')
        page.locator('#trainingForm button[type=submit]').click()
        page.wait_for_function('()=>document.getElementById("trainingMessage").textContent.includes("Não foi possível salvar")')
        assert page.evaluate('JSON.stringify(state.trainingSchedule)')==before
        page.evaluate('()=>{Storage.prototype.setItem=window.originalPlannerSet;}')
        for width in [320,390,800,1280]:
            page.set_viewport_size({'width':width,'height':844})
            assert page.locator('body').evaluate('(el)=>el.scrollWidth<=innerWidth'),width
        assert not external,external
        assert not errors,errors
        print('PASS: schedule validation and restart, no retroactive planning, revision history, distinct-day attendance, extras/future records, explicit recurring calendar export, browser fallback, encrypted backup with reminders disabled, legacy backups, failed-write rollback, responsive layout, no external requests.')
        browser.close()

if __name__=='__main__':main()
