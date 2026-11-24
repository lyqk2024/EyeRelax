"""Build the offline WPF installer frontend with the Windows .NET compiler."""
from pathlib import Path
import os
import subprocess
import sys


def main() -> int:
    root = Path(__file__).resolve().parent
    assets = root.parent / "assets"
    windows_dir = os.environ.get("WINDIR") or os.environ.get("SystemRoot")
    if not windows_dir:
        raise SystemExit("The Windows system directory could not be located.")
    framework = Path(windows_dir) / "Microsoft.NET" / "Framework64" / "v4.0.30319"
    if not (framework / "csc.exe").is_file():
        framework = framework.parent.parent / "Framework" / "v4.0.30319"
    compiler = framework / "csc.exe"
    if not compiler.is_file():
        raise SystemExit("Windows .NET Framework 4 compiler was not found.")
    generated = root / "obj"
    generated.mkdir(exist_ok=True)
    manifest = generated / "SetupWindow.manifest"
    manifest.write_text('''<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<assembly xmlns="urn:schemas-microsoft-com:asm.v1" manifestVersion="1.0">
 <assemblyIdentity version="1.0.0.0" processorArchitecture="*" name="EyeRelax.SetupWindow" type="win32"/>
 <trustInfo xmlns="urn:schemas-microsoft-com:asm.v3"><security><requestedPrivileges><requestedExecutionLevel level="asInvoker" uiAccess="false"/></requestedPrivileges></security></trustInfo>
 <application xmlns="urn:schemas-microsoft-com:asm.v3"><windowsSettings><dpiAware xmlns="http://schemas.microsoft.com/SMI/2005/WindowsSettings">true/pm</dpiAware><dpiAwareness xmlns="http://schemas.microsoft.com/SMI/2016/WindowsSettings">PerMonitorV2,PerMonitor</dpiAwareness></windowsSettings></application>
 <compatibility xmlns="urn:schemas-microsoft-com:compatibility.v1"><application><supportedOS Id="{8e0f7a12-bfb3-4fe8-b9a5-48fd50a15a9a}"/></application></compatibility>
</assembly>
''', encoding="utf-8")
    output = assets / "SetupWindow.exe"
    references = [framework / "System.dll", framework / "System.Core.dll", framework / "System.Xaml.dll"]
    references += [framework / "WPF" / name for name in ("WindowsBase.dll", "PresentationCore.dll", "PresentationFramework.dll")]
    command = [str(compiler), "/nologo", "/utf8output", "/target:winexe", "/platform:anycpu", "/langversion:5", "/optimize+", "/warn:4", "/out:" + str(output), "/win32manifest:" + str(manifest), "/win32icon:" + str(assets / "EyeRelax.ico"), "/resource:" + str(assets / "EyeRelax.ico") + ",EyeRelax.ico"]
    command += ["/reference:" + str(path) for path in references]
    command.append(str(root / "SetupWindow.cs"))
    result = subprocess.run(command, check=False)
    if result.returncode == 0:
        print("Built " + str(output))
        print('Preview: "' + str(output) + '" --preview')
    return result.returncode


if __name__ == "__main__":
    sys.exit(main())
