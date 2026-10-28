#!/usr/bin/env python3
"""Read/write only a local public-demo QA session; never calls a model provider."""
import argparse
import json
import os
from pathlib import Path
import re
import secrets
import urllib.request
import urllib.error

parser=argparse.ArgumentParser()
parser.add_argument('--base-url',default='http://127.0.0.1:8083')
parser.add_argument('--after-restart',action='store_true')
args=parser.parse_args()
if not re.fullmatch(r'http://(?:127\.0\.0\.1|localhost):\d+',args.base_url):raise SystemExit('This rehearsal script only targets an explicit local server.')
root=Path(__file__).resolve().parents[1]
file=root/'.local/public-demo/restart-check.json'
if args.after_restart:
    state=json.loads(file.read_text())
else:
    state={'session':secrets.token_hex(32)}
def request(path,body=None,method=None):
    payload=None if body is None else json.dumps(body).encode()
    req=urllib.request.Request(args.base_url+path,data=payload,method=method,headers={'X-Studio-Session':state['session'],**({'Content-Type':'application/json'} if payload is not None else {})})
    return urllib.request.urlopen(req,timeout=10)
def api(path,body=None,method=None):
    with request('/api/studio'+path,body,method) as response:return json.loads(response.read())
status=api('/status')
assert status['publicDemo'] and not status['imageConfigured'] and not status['visionConfigured']
concepts=api('/discover');assert len(concepts)==7
with request('/') as response:
    html=response.read().decode()
    assert 'Content-Security-Policy' in response.headers
    assert re.search(r'/assets/[^" ]+\.js',html)
    script=re.search(r'/assets/[^" ]+\.js',html).group()
with request(script) as response:assert response.status==200 and len(response.read())>1000
with request('/favicon.svg') as response:assert response.status==200
for concept in concepts:
    image=concept['revisions'][-1]['data']['image'];assert image.startswith('/concepts/')
    with request(image) as response:assert response.status==200 and response.headers['Content-Type'].startswith('image/')
for method,path in [('POST','/api/studio/items/'+concepts[0]['id']+'/generate'),('POST','/api/studio/uploads'),('GET','/api/studio/budget'),('GET','/actuator/env')]:
    try:request(path,{} if method=='POST' else None,method);raise AssertionError('Privileged route exposed')
    except urllib.error.HTTPError as error:assert error.code==403
try:request('/api/studio/find',{'message':'x'*20000},'POST');raise AssertionError('Oversized body accepted')
except urllib.error.HTTPError as error:assert error.code==413
if not args.after_restart:
    anchor=concepts[0]
    outfit=api('/outfits',{'anchorId':anchor['id'],'message':'campus'})
    prefs=api('/preferences',{'aesthetics':['Pastel Cozy'],'colors':['blue'],'saved':[anchor['id'],outfit['id']],'hidden':[],'lifestyle':'','budget':''},'PUT')
    state.update({'anchorId':anchor['id'],'outfitId':outfit['id'],'conceptIds':sorted(c['id'] for c in concepts)})
    file.parent.mkdir(exist_ok=True,parents=True)
    fd=os.open(file,os.O_WRONLY|os.O_CREAT|os.O_TRUNC,0o600)
    with os.fdopen(fd,'w') as stream:json.dump(state,stream)
else:
    assert sorted(c['id'] for c in concepts)==state['conceptIds']
    prefs=api('/preferences');assert state['anchorId'] in prefs['saved'] and state['outfitId'] in prefs['saved']
    assert api('/items/'+state['outfitId'])['data']['anchorId']==state['anchorId']
print(json.dumps({'publicDemo':True,'concepts':len(concepts),'bundledImages':True,'paidRoutesBlocked':True,'oversizedBodyBlocked':True,'persistenceAfterRestart':args.after_restart,'providerCalls':0}))
