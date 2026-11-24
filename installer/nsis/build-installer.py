#!/usr/bin/env python3
"""Prepare or compile the standard NSIS installer. Does NOT run the generated setup.

Python 3.9+; no third-party Python dependencies. Production Java classes are never changed.
All archive paths are checked. The uninstall manifest contains only shipped, relative files.
"""
from __future__ import annotations
import argparse
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import zipfile

HERE = Path(__file__).resolve().parent


# The Windows JRE vendor archive contains app-local copies of the MSVC/UCRT
# runtime. EyeRelax intentionally does not redistribute these private copies.
# The NSIS installer verifies a sufficiently new system Visual C++ v14 runtime
# and, only when needed, installs Microsoft's official x64 Redistributable.
PRIVATE_VC_RUNTIME_FILES = {
    Path("jre/bin/msvcp140.dll"),
    Path("jre/bin/vcruntime140.dll"),
    Path("jre/bin/vcruntime140_1.dll"),
    Path("jre/bin/ucrtbase.dll"),
}


def strip_private_vc_runtime(payload: Path) -> None:
    """Remove vendor-bundled app-local MSVC/UCRT DLLs from a prepared payload."""
    candidates = [payload / item for item in PRIVATE_VC_RUNTIME_FILES]
    candidates.extend((payload / "jre/bin").glob("api-ms-win-*.dll"))
    for path in candidates:
        if path.exists():
            if not path.is_file() or path.is_symlink():
                raise ValueError(f"Unexpected private VC runtime path: {path}")
            path.unlink()




def validate_launcher(path: Path) -> None:
    data = path.read_bytes()
    if len(data) < 4096 or data[:2] != b"MZ":
        raise ValueError(f"Missing/invalid native launcher: {path}")
    pe = int.from_bytes(data[0x3C:0x40], "little")
    if pe + 96 >= len(data) or data[pe:pe+4] != b"PE\0\0":
        raise ValueError(f"Invalid PE launcher: {path}")
    machine = int.from_bytes(data[pe+4:pe+6], "little")
    optional = pe + 24
    if machine != 0x8664 or int.from_bytes(data[optional:optional+2], "little") != 0x20B:
        raise ValueError("EyeRelax.exe must be Windows x64 PE32+")
    subsystem = int.from_bytes(data[optional+68:optional+70], "little")
    if subsystem != 2:
        raise ValueError("EyeRelax.exe must use the Windows GUI subsystem")

def relative_files(payload: Path) -> list[Path]:
    result = sorted(p.relative_to(payload) for p in payload.rglob("*") if p.is_file())
    for item in result:
        if item.parts[0] not in {"app", "jre"} or any(p in {".", ".."} for p in item.parts):
            raise ValueError(f"Unexpected payload path: {item}")
        if any(c in str(item) for c in '$"\n\r:'):
            raise ValueError(f"Unsafe NSIS manifest filename: {item}")
        if payload.joinpath(item).is_symlink():
            raise ValueError(f"Symlink not supported: {item}")
    return result


def validate_payload(payload: Path) -> list[Path]:
    present_private = [p.as_posix() for p in PRIVATE_VC_RUNTIME_FILES if (payload / p).exists()]
    if present_private:
        raise ValueError(
            "Private VC runtime DLLs must not be shipped in payload; "
            "use the official Visual C++ Redistributable instead: " + ", ".join(present_private)
        )
    required = ["app/EyeRelax.cfg", "app/rest-1.0.0.jar", "app/jna-5.14.0.jar", "app/jna-platform-5.14.0.jar",
                "jre/bin/javaw.exe", "jre/bin/java.exe",
                "jre/bin/jli.dll", "jre/bin/server/jvm.dll", "jre/lib/modules", "jre/release"]
    for item in required:
        path = payload / item
        if not path.is_file() or path.stat().st_size == 0:
            raise FileNotFoundError(f"Missing bundled dependency: {path}")
    with zipfile.ZipFile(payload / "app/rest-1.0.0.jar") as z:
        manifest = z.read("META-INF/MANIFEST.MF").decode("utf-8")
        if "Main-Class: com.eye.app.Main" not in manifest:
            raise ValueError("Application entry point missing from manifest")
        if "Class-Path:" not in manifest or "jna-platform-5.14.0.jar" not in manifest:
            raise ValueError("Direct -jar launcher dependencies missing")
        if any(x.startswith("com/eye/win/") for x in z.namelist() if "Test" in x):
            raise ValueError("Test classes must not be included in the production JAR")
    for item in ["jre/bin/javaw.exe", "jre/bin/server/jvm.dll"]:
        with (payload / item).open("rb") as f:
            if f.read(2) != b"MZ":
                raise ValueError(f"Not a Windows PE: {item}")
    return relative_files(payload)


def generate_manifest(payload: Path, dest: Path, files: list[Path]) -> None:
    lines = ['; Generated; do not hand edit. Exact owned-file manifest.',
             '!define PAYLOAD_KIB ' + str((sum((payload / p).stat().st_size for p in files) + 1023)//1024),
             '!macro InstallPayload']
    last = None
    for p in files:
        parent = str(p.parent).replace('/', '\\')
        if parent != last:
            lines.append(f'  SetOutPath "$INSTDIR\\{parent}"'); last = parent
        win = str(p).replace('/', '\\')
        lines.append(f'  File "${{PAYLOAD}}\\{win}"')
    lines.extend(['!macroend', '', '!macro CheckOwnedPayload PREFIX'])
    for p in files:
        win = str(p).replace('/', '\\')
        lines.extend([f'  StrCpy $OwnedFile "$INSTDIR\\{win}"', '  Call ${PREFIX}CheckFileUnlocked'])
    lines.extend(['!macroend', '', '!macro DeletePayload PREFIX'])
    for p in files:
        win = str(p).replace('/', '\\')
        lines.extend([f'  StrCpy $OwnedFile "$INSTDIR\\{win}"', '  Call ${PREFIX}DeleteOwnedFile'])
    parents = {parent for p in files for parent in p.parents if str(parent) != '.'}
    for p in sorted(parents, key=lambda p: (len(p.parts), str(p)), reverse=True):
        win = str(p).replace('/', '\\')
        lines.append(f'  RMDir "$INSTDIR\\{win}"')
    lines.extend(['!macroend', '', '!macro VerifyPayloadRemoved'])
    for i, p in enumerate(files):
        win = str(p).replace('/', '\\')
        lines.extend([f'  IfFileExists "$INSTDIR\\{win}" 0 verify_removed_{i}',
                      f'    StrCpy $ModernMessage "应用文件尚未删除：{win}。卸载未完成，请检查文件占用或权限后重试。"',
                      '    Call un.ShowOperationError',
                      '    SetErrorLevel 2', '    Abort', f'  verify_removed_{i}:'])
    lines.append('!macroend')
    # Exact files shipped by the previous Windows edition that are absent from this
    # release. Keep unknown files in an existing application directory intact.
    legacy = (HERE / "legacy-payload-files.txt").read_text(encoding="utf-8").splitlines()
    retired = sorted(set(map(Path, legacy)).difference(files))
    lines.append('!macro RetiredFiles ACTION')
    for p in retired:
        if not re.fullmatch(r"(app|jre)/[\w./-]+", p.as_posix()) or ".." in p.parts:
            raise ValueError(f"Invalid historical payload filename: {p}")
        lines.extend([f'  StrCpy $OwnedFile "$INSTDIR\\{p}"', '  Call ${ACTION}'])
    lines.extend(['!macroend', '!macro RemoveRetiredDirectories'])
    retired_folders = {parent for p in retired for parent in p.parents} - parents - {Path(".")}
    for p in sorted(retired_folders, key=lambda p: (len(p.parts), str(p)), reverse=True):
        lines.append(f'  RMDir "$INSTDIR\\{p}"')
    lines.append('!macroend')
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_text('\n'.join(lines) + '\n', encoding='utf-8-sig')


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--payload', type=Path, default=HERE / 'payload')

    parser.add_argument('--output', type=Path)
    parser.add_argument('--makensis', type=Path)
    parser.add_argument('--prepare-only', action='store_true', help='Generate and validate manifests without claiming a compiled installer')
    args = parser.parse_args()
    payload = args.payload.resolve()
    with zipfile.ZipFile(payload / "app/rest-1.0.0.jar") as jar:
        properties = jar.read("app-version.properties").decode("utf-8-sig")
    match = re.search(r"^version=(\d+\.\d+\.\d+)([-+][\w.+-]+)?\s*$", properties, re.MULTILINE)
    if not match:
        raise ValueError("Invalid application version in app-version.properties")
    version = match.group(0).strip().removeprefix("version=")
    pe_version = match.group(1) + ".0"
    output = (args.output or HERE / f'EyeRelax-Setup-{version}.exe').resolve()
    for path in (payload, output):
        if any(c in str(path) for c in '$"\n\r'):
            raise ValueError('Build paths cannot contain quotes, dollar signs, or newlines')
    strip_private_vc_runtime(payload)
    files = validate_payload(payload)
    subprocess.run([str(payload / "jre/bin/java.exe"), "-version"], check=True)
    validate_launcher(HERE / "assets/EyeRelax.exe")
    generated = HERE / 'build/owned-files.nsh'
    generate_manifest(payload, generated, files)
    print(f'Validated {len(files)} bundled files.')
    if args.prepare_only:
        print('PREPARED ONLY: no Setup.exe was compiled or tested.')
        return 0
    tool = str(args.makensis) if args.makensis else shutil.which('makensis') or shutil.which('makensis.exe')
    if not tool and os.name == 'nt':
        # Never assume a drive letter or a particular Program Files location.
        # Windows can be installed on a non-C: drive and NSIS can live elsewhere.
        for env in ('ProgramFiles(x86)', 'ProgramFiles'):
            base = os.environ.get(env)
            if not base:
                continue
            candidate = Path(base) / 'NSIS' / 'makensis.exe'
            if candidate.is_file():
                tool = str(candidate)
                break
    if not tool:
        raise FileNotFoundError('Official NSIS 3 compiler (makensis) not found. No Setup.exe was produced. Install NSIS or supply --makensis.')
    output.parent.mkdir(parents=True, exist_ok=True)
    prefix = '/' if os.name == 'nt' else '-'
    command = [tool, prefix+'INPUTCHARSET', 'UTF8', prefix+'V2',
               prefix+'DVERSION='+version, prefix+'DPE_VERSION='+pe_version,
               prefix+'DPAYLOAD='+str(payload), prefix+'DGENERATED='+str(generated),
               prefix+'DOUTPUT='+str(output.with_name(output.stem+'.building.exe')), str(HERE / 'EyeRelax.nsi')]
    temp_output = output.with_name(output.stem+'.building.exe')
    if temp_output.exists():
        temp_output.unlink()
    try:
        subprocess.run(command, cwd=HERE, check=True)
    except subprocess.CalledProcessError:
        if temp_output.exists():
            temp_output.unlink()
        raise
    if not temp_output.is_file() or temp_output.stat().st_size < 10_000_000:
        raise RuntimeError('Compiler did not produce a complete bundled installer')
    temp_output.replace(output)
    print(f'Compiled {output}.')
    return 0


if __name__ == '__main__':
    try:
        raise SystemExit(main())
    except (OSError, ValueError, RuntimeError, subprocess.CalledProcessError, zipfile.BadZipFile) as exc:
        print(f'BUILD FAILED: {exc}', file=sys.stderr)
        raise SystemExit(1)
