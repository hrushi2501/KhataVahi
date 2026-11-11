#!/usr/bin/env python3
"""Debug-emulator-only cache/force-stop smoke test. Never clears app storage."""
import subprocess
import sys
import time

SERIAL = sys.argv[1] if len(sys.argv)>1 else 'emulator-5554'
PACKAGE = 'com.farmeraccounts'
if not SERIAL.startswith('emulator-'):
    sys.exit('Only emulator serials are accepted.')

def adb(*args, allow_missing=False):
    try:
        result=subprocess.run(['adb','-s',SERIAL,*args],capture_output=True,text=True,timeout=120 if '--cache-only' in args else 30)
    except subprocess.TimeoutExpired:
        sys.exit('Android did not complete the operation in time; no retention success claimed.')
    if result.returncode and not allow_missing:
        sys.exit('Emulator command failed; app storage was not cleared.')
    return result.stdout

help_text=adb('shell','pm','help')
if '--cache-only' not in help_text:
    sys.exit('Android does not expose a cache-only clear; refusing any storage clear.')
adb('shell','am','force-stop',PACKAGE)
# Hash persistent files only. No record/session content is printed or exported.
command="run-as "+PACKAGE+" sh -c 'find databases files shared_prefs -type f ! -name \"*-shm\" -exec sha256sum {} \\; 2>/dev/null'"
before=sorted(adb('shell',command).splitlines())
if not any('farmer_accounts.db' in line for line in before):
    sys.exit('Persistent Room database not found; no retention claim made.')
adb('shell','run-as',PACKAGE,'sh','-c',"'mkdir -p cache; echo probe > cache/khatavahi-qa-probe'")
if '--simulate-cache-only' in sys.argv[2:]:
    adb('shell','run-as',PACKAGE,'sh','-c',"'rm -rf cache/* code_cache/*'")
    result='Success'
else:
    result=adb('shell','pm','clear','--cache-only',PACKAGE)
if 'Success' not in result:
    sys.exit('Cache-only clear was not confirmed.')
after=sorted(adb('shell',command).splitlines())
if before!=after:
    sys.exit('Persistent file hashes changed; inspect before claiming cache safety.')
print(('Cache directory clear simulation' if '--simulate-cache-only' in sys.argv[2:] else 'Android cache-only clear')+': persistent Room/preferences files unchanged ('+str(len(before))+' files).')
adb('shell','am','start','-n',PACKAGE+'/.presentation.main.MainActivity')
deadline=time.monotonic()+30
while time.monotonic()<deadline:
    if adb('shell','pidof',PACKAGE,allow_missing=True).strip():
        print('Force-stop/reopen: app process resumed. No app storage clear performed.')
        break
    time.sleep(0.5)
else:
    sys.exit('App did not resume in time.')
