#!/usr/bin/env python3
"""Build the official jpackage native launcher while retaining the installed app/jre layout.

EyeRelax.exe hosts the bundled JVM in an EyeRelax process. It does not launch javaw.exe.
"""
from pathlib import Path
import argparse
import shutil
import struct
import subprocess
import tempfile
import zipfile

ROOT=Path(__file__).resolve().parents[2]

def pe_info(path):
    data=path.read_bytes();pe=struct.unpack_from('<I',data,0x3c)[0]
    if data[:2]!=b'MZ' or data[pe:pe+4]!=b'PE\0\0' or struct.unpack_from('<H',data,pe+24)[0]!=0x20b:
        raise ValueError('Expected a Windows PE32+ native launcher')
    return struct.unpack_from('<H',data,pe+4)[0],struct.unpack_from('<H',data,pe+24+68)[0]

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--payload',type=Path,required=True)
    parser.add_argument('--jpackage',default=shutil.which('jpackage'))
    parser.add_argument('--icon',type=Path,default=ROOT/'assets/logo/EyeRelax.ico')
    parser.add_argument('--out',type=Path,default=ROOT/'build/launcher/EyeRelax.exe')
    args=parser.parse_args()
    if not args.jpackage:raise FileNotFoundError('JDK 17+ jpackage is required')
    payload=args.payload.resolve();out=args.out.resolve();out.parent.mkdir(parents=True,exist_ok=True)
    for required in [payload/'app/rest-1.0.0.jar',payload/'jre/bin/server/jvm.dll']:
        if not required.is_file():raise FileNotFoundError(required)
    with zipfile.ZipFile(payload/'app/rest-1.0.0.jar') as jar:
        properties=jar.read('app-version.properties').decode('utf-8-sig')
    version=next(line.split('=',1)[1].strip() for line in properties.splitlines() if line.startswith('version='))
    numeric_version=version.split('-',1)[0].split('+',1)[0]
    with tempfile.TemporaryDirectory(prefix='native-package-',dir=out.parent) as work:
        work=Path(work).resolve()
        if not work.is_relative_to(out.parent):raise ValueError('Unexpected temporary build directory')
        input_dir=work/'input';input_dir.mkdir()
        for file in (payload/'app').iterdir():
            if file.is_file() and file.name not in {'EyeRelax.cfg','.jpackage.xml'}:shutil.copy2(file,input_dir/file.name)
        command=[args.jpackage,'--type','app-image','--name','EyeRelax','--app-version',numeric_version,
                 '--description','EyeRelax','--vendor','EyeRelax','--input',str(input_dir),
                 '--main-jar','rest-1.0.0.jar','--main-class','com.eye.app.Main',
                 '--runtime-image',str(payload/'jre'),'--icon',str(args.icon.resolve()),'--dest',str(work/'image')]
        for option in ['-Xms32m','-Xmx384m','-XX:+UseG1GC','-XX:G1PeriodicGCInterval=60000','-Dfile.encoding=UTF-8','-Dsun.jnu.encoding=UTF-8','-Djava.library.path=$APPDIR','-Djna.library.path=$APPDIR']:
            command+=['--java-options',option]
        subprocess.run(command,check=True)
        image=work/'image/EyeRelax'
        shutil.copy2(image/'EyeRelax.exe',out)
        if pe_info(out)!=(0x8664,2):raise ValueError('Expected x64 GUI launcher')
        cfg=(image/'app/EyeRelax.cfg').read_text(encoding='utf-8')
        cfg=cfg.replace('[Application]','[Application]\napp.runtime=$ROOTDIR\\jre',1)
        (payload/'app/EyeRelax.cfg').write_text(cfg,encoding='utf-8')
    print(f'Built official native launcher: {out} ({out.stat().st_size} bytes)')
    return 0

if __name__=='__main__':raise SystemExit(main())
