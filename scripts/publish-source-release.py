#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Publish audited source and the unsigned production APK from a successful main build.
Never publishes a preview APK, generates a signing key, or changes an existing tag.
"""
import hashlib
import io
import json
import os
from pathlib import Path
import re
import subprocess
from urllib.error import HTTPError
from urllib.parse import quote, urlparse
from urllib.request import Request, HTTPRedirectHandler, build_opener
import zipfile

ROOT=Path(__file__).resolve().parents[1]
class SafeRedirect(HTTPRedirectHandler):
    def redirect_request(self, request, response, code, message, headers, newurl):
        if urlparse(newurl).scheme!='https':raise RuntimeError('Refusing an insecure artifact redirect.')
        redirected=super().redirect_request(request,response,code,message,headers,newurl)
        if urlparse(request.full_url).netloc!=urlparse(newurl).netloc:redirected.remove_header('Authorization')
        return redirected
OPENER=build_opener(SafeRedirect())
def main():
    event=json.loads(Path(os.environ['GITHUB_EVENT_PATH']).read_text())
    run=event['workflow_run'];repo=os.environ['GITHUB_REPOSITORY']
    if run['conclusion']!='success' or run['event']!='push' or run['head_branch']!='main' or run['head_repository']['full_name']!=repo:
        raise RuntimeError('Only a successful push build of this repository main branch can publish.')
    sha=run['head_sha']
    if subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()!=sha:raise RuntimeError('Checkout differs from tested commit.')
    gradle=(ROOT/'android/app/build.gradle').read_text()
    version=re.search(r'versionName "([0-9]+\.[0-9]+\.[0-9]+)"',gradle).group(1)
    code=int(re.search(r'versionCode ([0-9]+)',gradle).group(1));tag='v'+version
    token=os.environ['GH_TOKEN'];api='https://api.github.com/repos/'+repo
    def call(path,method='GET',payload=None,raw=None):
        data=raw if raw is not None else json.dumps(payload).encode() if payload is not None else None
        url=path if path.startswith('https://') else api+path
        if not (url.startswith(api+'/') or url.startswith('https://uploads.github.com/repos/'+repo+'/')):raise RuntimeError('Unexpected API destination.')
        headers={'Authorization':'Bearer '+token,'Accept':'application/vnd.github+json','X-GitHub-Api-Version':'2022-11-28','Content-Type':'application/octet-stream' if raw is not None else 'application/json'}
        with OPENER.open(Request(url,data=data,headers=headers,method=method),timeout=120) as response:
            value=response.read();return json.loads(value) if 'json' in response.headers.get('Content-Type','') else value
    def optional(path):
        try:return call(path)
        except HTTPError as error:
            if error.code==404:return None
            raise
    existing=optional('/releases/tags/'+tag)
    if existing:
        print('Release already exists; kept unchanged: '+existing['html_url']);return
    ref=optional('/git/ref/tags/'+tag)
    if ref and (ref['object']['type']!='commit' or ref['object']['sha']!=sha):raise RuntimeError('Existing tag differs from tested commit; refusing to move it.')
    artifacts=call('/actions/runs/'+str(run['id'])+'/artifacts')['artifacts']
    matches=[a for a in artifacts if a['name']=='nado-apks-'+str(run['run_number']) and not a['expired'] and a['workflow_run']['head_sha']==sha]
    if len(matches)!=1:raise RuntimeError('Expected exactly one APK artifact from the tested commit.')
    artifact=matches[0];archive=call('/actions/artifacts/'+str(artifact['id'])+'/zip')
    digest=artifact.get('digest','')
    if not digest.startswith('sha256:') or hashlib.sha256(archive).hexdigest()!=digest[7:]:raise RuntimeError('Artifact digest mismatch.')
    out=ROOT/'artifacts/production';out.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(io.BytesIO(archive)) as z:
        apk=z.read('nado-release-unsigned.apk');lines=z.read('SHA256SUMS.txt').decode().splitlines()
        expected=next(line.split()[0] for line in lines if line.split()[-1]=='nado-release-unsigned.apk')
        if hashlib.sha256(apk).hexdigest()!=expected:raise RuntimeError('Production APK checksum mismatch.')
        (out/'nado-release-unsigned.apk').write_bytes(apk)
    subprocess.run(['python3','scripts/package-source.py','--output',str(out)],cwd=ROOT,check=True)
    (out/'br.com.tabinformatica.nadonomeritmo.yml').write_bytes((ROOT/'fdroid/br.com.tabinformatica.nadonomeritmo.yml').read_bytes())
    (out/'PEDIDO-F-DROID.md').write_bytes((ROOT/'docs/PEDIDO-F-DROID.md').read_bytes())
    files=sorted(p for p in out.iterdir() if p.is_file())
    (out/'SHA256SUMS.txt').write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.name+'\n' for p in files))
    if not ref:call('/git/refs','POST',{'ref':'refs/tags/'+tag,'sha':sha})
    body=f'''Versão {version} · versionCode {code} · Apache-2.0

Treinos guiados, planejamento e lembretes locais, importação opcional pelo Health Connect, agenda opcional, temas claro/escuro e fotos com a marca do app. Inclui backup automático criptografado para um arquivo autorizado no Drive ou outro provedor do Android.

O código-fonte, os arquivos de empacotamento do F-Droid e o APK **sem assinatura** estão disponíveis abaixo. **nado-release-unsigned.apk não é instalável diretamente.** A atualização do aplicativo principal exige assinatura com a mesma chave original; não foi gerada uma nova chave. APK de teste .preview não é distribuído como produção nesta release.

Build e verificações: {run['html_url']}
As integrações de conta, Drive e dispositivos ainda precisam de validação física. O envio e a inclusão no F-Droid dependem de submissão e revisão; esta release não significa que o app já está na loja.
'''
    release=call('/releases','POST',{'tag_name':tag,'target_commitish':sha,'name':'Nado no meu ritmo '+version,'body':body,'draft':True,'prerelease':False})
    upload=release['upload_url'].split('{')[0]
    for path in sorted(out.iterdir()):
        if path.is_file():call(upload+'?name='+quote(path.name),method='POST',raw=path.read_bytes())
    result=call('/releases/'+str(release['id']),'PATCH',{'draft':False,'make_latest':'true'})
    print('Published source release: '+result['html_url'])
    with open(os.environ['GITHUB_STEP_SUMMARY'],'a') as summary:summary.write('Published source release: '+result['html_url']+'\n\nThe production APK is unsigned and requires the original signing key before installation.\n')

if __name__=='__main__':main()
