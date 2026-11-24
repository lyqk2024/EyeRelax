# EyeRelax for Windows · 许可证与来源

EyeRelax 自有代码使用 GNU General Public License 第 3 版（`GPL-3.0-only`）。完整条款见 [LICENSE](LICENSE)。

## 安装包中的组件

| 组件 | 用途及许可 |
| --- | --- |
| JNA 和 JNA Platform 5.14.0 | EyeRelax 使用它们调用 Windows API。本次分发选用 Apache License 2.0。JNA 的原生调用库使用 libffi，其许可为 MIT。见 [JNA 原版声明](notices/JNA-LICENSE.txt)、[Apache License 2.0](notices/Apache-2.0.txt)、[libffi 作者与许可](notices/JNA-libffi-LICENSE.txt)和 [JNA 对应源码](https://github.com/java-native-access/jna/tree/5.14.0)。 |
| Eclipse Temurin OpenJDK 17.0.21.1+2（Windows x64） | 为 EyeRelax 提供 Java 运行时及原生启动器。OpenJDK 使用 GPL 第 2 版，带有 Classpath Exception 的部分按各自声明适用。运行时模块的版权与第三方条款随程序保存在 `jre/legal/`。本次发行配套的完整 JDK 源码包为 `OpenJDK17U-jdk-sources_17.0.21.1_2.tar.gz`。见 [Temurin 官方发行记录](https://github.com/adoptium/temurin17-binaries/releases/tag/jdk-17.0.21.1%2B2)和 [对应源码下载](https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.21.1%2B2/OpenJDK17U-jdk-sources_17.0.21.1_2.tar.gz)。 |
| NSIS 3.12 | 安装和卸载程序使用 NSIS 的原生安装引擎、标准插件及 zlib 压缩模块。见 [NSIS 原版许可声明](notices/NSIS-COPYING.txt)和 [上游许可页](https://nsis.sourceforge.io/License)。 |

## 构建与数据生成

- 源码包含 Gradle Wrapper，配置下载 Gradle 8.10。Wrapper 采用 Apache License 2.0。Gradle 的完整上游声明见 [Gradle LICENSE](notices/Gradle-LICENSE.txt)、[Gradle NOTICE](notices/Gradle-NOTICE.txt)和 [Gradle 源码](https://github.com/gradle/gradle/tree/v8.10.0)。
- 应用内 2023–2028 年的公历、农历与节气对照表由维护脚本使用 lunar_python 1.4.9 生成。生成器的库作者为 6tail，采用 MIT 许可。桌面应用直接读取随附的逐日数据。见 [MIT 原文](notices/lunar-python-MIT.txt)和 [对应版本](https://pypi.org/project/lunar-python/1.4.9/)。
