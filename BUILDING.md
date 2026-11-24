# 构建 EyeRelax Windows 桌面版

## 准备工具

在 64 位 Windows 上安装以下工具。

- Windows x64 的完整 OpenJDK 17。正式版 1.0.0 使用 Eclipse Temurin 17.0.21.1+2。将 `JAVA_HOME` 设为解压后的 JDK 目录，也可以在构建命令里传入 `--jdk`。[对应版本的 JDK 和源码](https://github.com/adoptium/temurin17-binaries/releases/tag/jdk-17.0.21.1%2B2)
- Python 3.9 或更高版本。构建脚本只使用标准库。
- NSIS 3，正式版使用 3.12。默认安装位置可自动识别，自定义位置可用 `--makensis` 指定编译器。[NSIS 下载](https://nsis.sourceforge.io/Download)
- Windows 的 .NET Framework 4.8，供编译 WPF 安装界面使用。可检查 `%WINDIR%\Microsoft.NET\Framework64\v4.0.30319\csc.exe` 是否存在。

首次构建时，Gradle Wrapper 会下载 Gradle 8.10 和 Maven Central 的 JNA 5.14.0 依赖。之后可以复用 Gradle 用户缓存。

## 生成安装程序

在源码根目录打开 PowerShell，运行：

```powershell
python .\build-windows.py
```

如果需要指定工具路径：

```powershell
python .\build-windows.py --jdk "C:\Tools\jdk-17" --makensis "C:\Tools\NSIS\makensis.exe"
```

安装程序位于 `build\distributions\EyeRelax-Setup-1.0.0.exe`。构建会运行桌面端回归检查、生成精简 Java 运行时和本机启动器、编译安装界面，再通过 NSIS 打包。安装后的程序自带 Java，使用者无需另装 JDK。该命令生成的安装程序未经代码签名。

## 开发与测试

```powershell
.\gradlew.bat run
.\gradlew.bat check
```

应用版本来自 `src/main/resources/app-version.properties`。安装程序和卸载注册信息使用同一版本号。安装目录中的 `app\rest-1.0.0.jar` 是现有安装和更新程序识别的内部文件名。

安装与卸载的端到端测试应在独立的 Windows 测试账户或虚拟机中进行。安装程序会检测正在运行的 EyeRelax，并使用当前账户的安装注册信息。
