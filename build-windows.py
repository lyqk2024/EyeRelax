"""Build the Windows x64 installer from a source checkout (Python 3.9+)."""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile


ROOT = Path(__file__).resolve().parent
MODULES = (
    "java.se", "jdk.accessibility", "jdk.charsets", "jdk.crypto.ec",
    "jdk.crypto.mscapi", "jdk.localedata", "jdk.management", "jdk.unsupported", "jdk.zipfs",
)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jdk", type=Path, help="OpenJDK 17 for Windows x64 (defaults to JAVA_HOME)")
    parser.add_argument("--makensis", type=Path, help="Path to makensis.exe if NSIS is installed in a custom location")
    parser.add_argument("--output", type=Path, help="Destination installer filename")
    args = parser.parse_args()
    if sys.platform != "win32":
        parser.error("Build the Windows installer on Windows x64.")
    jdk_path = args.jdk or os.environ.get("JAVA_HOME")
    if not jdk_path:
        parser.error("Set JAVA_HOME or pass --jdk pointing to a Windows x64 OpenJDK 17.")
    jdk = Path(jdk_path).resolve()
    release = dict(line.split("=", 1) for line in (jdk / "release").read_text(encoding="utf-8").splitlines() if "=" in line)
    if (release.get("JAVA_VERSION", "").strip('"').split(".")[0] != "17"
            or release.get("OS_ARCH", "").strip('"') not in {"amd64", "x86_64"}
            or release.get("OS_NAME", "").strip('"') != "Windows"):
        parser.error("A full Windows x64 OpenJDK 17 distribution is required.")
    for tool in ("javac.exe", "jlink.exe", "jpackage.exe"):
        if not (jdk / "bin" / tool).is_file():
            parser.error(f"Missing {tool} under {jdk / 'bin'}")

    env = os.environ.copy()
    env.update(JAVA_HOME=str(jdk), PATH=str(jdk / "bin") + os.pathsep + env["PATH"])
    def run(*command):
        subprocess.run([str(part) for part in command], cwd=ROOT, env=env, check=True)

    properties = (ROOT / "src/main/resources/app-version.properties").read_text(encoding="utf-8-sig")
    version = next(line.split("=", 1)[1].strip() for line in properties.splitlines() if line.startswith("version="))
    output = (args.output or ROOT / "build/distributions" / f"EyeRelax-Setup-{version}.exe").resolve()
    run(ROOT / "gradlew.bat", "--console=plain", "check", "installDist")
    jars = ROOT / "build/install/rest/lib"
    with tempfile.TemporaryDirectory(prefix="windows-", dir=ROOT / "build") as temporary:
        work = Path(temporary)
        nsis = work / "nsis"
        shutil.copytree(ROOT / "installer/nsis", nsis,
                        ignore=shutil.ignore_patterns("build", "obj", "payload", "__pycache__", "*.exe"))
        payload = work / "payload"
        app = payload / "app"
        app.mkdir(parents=True)
        # Existing installations and the updater use this stable internal JAR name.
        main_jar = jars / f"rest-{version}.jar"
        shutil.copy2(main_jar, app / "rest-1.0.0.jar")
        for dependency in jars.glob("*.jar"):
            if dependency != main_jar:
                shutil.copy2(dependency, app / dependency.name)
        runtime = payload / "jre"
        run(jdk / "bin/jlink.exe", "--module-path", jdk / "jmods", "--add-modules", ",".join(MODULES),
            "--no-header-files", "--no-man-pages", "--strip-debug", "--output", runtime)
        run(sys.executable, ROOT / "installer/launcher/build-launcher.py", "--payload", payload,
            "--jpackage", jdk / "bin/jpackage.exe", "--out", nsis / "assets/EyeRelax.exe")
        run(sys.executable, nsis / "modern/build-ui.py")

        legal = app / "licenses"
        legal.mkdir()
        for document in ("LICENSE", "THIRD_PARTY_NOTICES.md"):
            shutil.copy2(ROOT / document, legal)
        shutil.copytree(ROOT / "notices", legal / "notices")
        shutil.copy2(jdk / "release", legal / "jdk-release.txt")
        for document in ("NOTICE", "DISCLAIMER"):
            if (jdk / document).is_file():
                shutil.copy2(jdk / document, legal / ("jdk-" + document))

        nsis_args = ["--makensis", args.makensis.resolve()] if args.makensis else []
        run(sys.executable, nsis / "build-installer.py", "--payload", payload, "--output", output, *nsis_args)
    print(f"Installer: {output}")


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, subprocess.CalledProcessError) as error:
        sys.exit(str(error))
