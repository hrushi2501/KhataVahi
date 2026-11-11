#!/usr/bin/env python3
"""Opt-in live emulator QA. Adds marked fixtures and soft-deletes them; never clears storage.
Sign in to the owner account on the emulator first. Test audit/tombstone history is retained.
"""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
TEST = 'com.farmeraccounts.data.sync.LiveSyncWorkflowTest'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('serial', help='emulator serial, for example emulator-5554')
    parser.add_argument('--stage', choices=('all', 'offline', 'online'), default='all')
    args = parser.parse_args()
    if not args.serial.startswith('emulator-'):
        parser.error('Only Android emulators are supported; physical phones are refused.')
    sdk = Path(os.environ.get('ANDROID_HOME', Path.home() / 'Library/Android/sdk'))
    adb = shutil.which('adb') or str(sdk / 'platform-tools/adb')
    prefix = [adb, '-s', args.serial]
    result_dir = Path(tempfile.mkdtemp(prefix='khatavahi-live-sync-'))
    os.chmod(result_dir, 0o700)

    def run(command):
        subprocess.run(command, cwd=ROOT, check=True, timeout=300)

    def shell(*arguments):
        run(prefix + ['shell', *arguments])

    def stage(method, name):
        log = result_dir / (name + '.log')
        with log.open('w') as output:
            os.chmod(log, 0o600)
            subprocess.run(prefix + ['shell', 'am', 'instrument', '-w', '-e', 'liveSync', 'true',
                           '-e', 'class', TEST + '#' + method,
                           'com.farmeraccounts.test/androidx.test.runner.AndroidJUnitRunner'],
                           stdout=output, stderr=subprocess.STDOUT, check=True, timeout=300)
        # Instrumentation can return shell exit 0 even when JUnit failed. Do not print
        # raw SDK exceptions: HTTP error text may contain authentication headers.
        if 'OK (1 test)' not in log.read_text():
            raise RuntimeError(f'{name} failed. Inspect private log locally: {log}. Do not publish raw HTTP errors.')
        print(f'PASS: {name}')

    try:
        if args.stage in ('all', 'offline'):
            run(['./gradlew', 'assembleDebug', 'assembleDebugAndroidTest'])
            run(prefix + ['install', '-r', 'app/build/outputs/apk/debug/app-debug.apk'])
            run(prefix + ['install', '-r', 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'])
            stage('stageOfflineSave', 'offline-save')
        if args.stage in ('all', 'online'):
            shell('am', 'force-stop', 'com.farmeraccounts')
            shell('svc', 'wifi', 'enable')
            shell('svc', 'data', 'enable')
            shell('am', 'start', '-n', 'com.farmeraccounts/.presentation.main.MainActivity')
            stage('stageOnlineRestoreAndDelete', 'automatic-upload-restore-delete')
    finally:
        shell('svc', 'wifi', 'enable')
        shell('svc', 'data', 'enable')
    print(f'Private results: {result_dir}')


if __name__ == '__main__':
    main()
