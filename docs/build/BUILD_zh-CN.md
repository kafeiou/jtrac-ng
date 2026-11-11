# JTrac NG 编译与构建指南 (简体中文)

[English](BUILD_en.md) | [繁體中文](BUILD_zh-TW.md) | [简体中文](BUILD_zh-CN.md) | [日本語](BUILD_ja.md) | [Tiếng Việt](BUILD_vi.md) | [Deutsch](BUILD_de.md) | [Español](BUILD_es.md) | [Français](BUILD_fr.md)

本指南详细说明如何使用 Apache Maven 编译与打包 JTrac NG 3.0.0-beta 项目，深入解析 Maven 生命周期、依赖缓存管理、WAR 与绿色免安装便携版 (Portable) 封装结构、Web 容器兼容性矩阵，以及常见编译问题之排查指引。

---

## 1. 前置环境需求

在开始编译之前，请确认您的开发环境符合以下条件：

- **操作系统**：Windows / Linux / macOS
- **Java 开发套件 (JDK)**：**Java 21 LTS**（推荐使用 [Eclipse Temurin JDK 21+](https://adoptium.net/temurin/releases/?version=21)，例如 `W:\developer\jdk-21.0.9`，最低门槛为 Java 21）
  > [!IMPORTANT]
  > 本现代化版本核心已全面跃升至 **Spring 6.2.x**、**Jakarta EE 10 (Servlet 6.0)**、**Hibernate 5.6.x (Jakarta)** 与 **Apache Wicket 10.x**，编译目标字节码为 **Java 21**。**JDK 8 / 11 / 17 已不再支持**，请务必使用 JDK 21+ 进行编译。
- **Apache Maven**：**Maven 3.9+**（例如 `W:\developer\apache-maven-3.9.9`）

### 本机环境变量配置示例

- **Windows (PowerShell)**：
  ```powershell
  $env:JAVA_HOME = "W:\developer\jdk-21.0.9"
  $env:PATH = "W:\developer\apache-maven-3.9.9\bin;$env:PATH"
  ```
- **Windows (CMD)**：
  ```cmd
  set "JAVA_HOME=W:\developer\jdk-21.0.9"
  set "PATH=W:\developer\apache-maven-3.9.9\bin;%PATH%"
  ```
- **Linux / macOS (Bash/Zsh)**：
  ```bash
  export JAVA_HOME="/usr/lib/jvm/java-21-openjdk"
  export PATH="$JAVA_HOME/bin:$PATH"
  ```

验证环境命令：
```bash
mvn -version
```
输出应正确显示 Maven 3.9+ 以及对应的 Java 21 版本信息。

---

## 2. Maven 编译与构建命令矩阵

请在 JTrac NG 项目根目录（包含 `pom.xml` 的目录）下执行以下命令：

| 命令 | 阶段 / 作用 | 说明 | 产物路径 |
|---|---|---|---|
| `mvn clean compile` | 编译主程序 | 清理旧产物并编译 `src/main/java`，自动处理资源过滤 | `target/classes/` |
| `mvn test-compile` | 编译测试码 | 编译 `src/test/java` 下的所有单元测试类 | `target/test-classes/` |
| `mvn test` | 执行测试 | 执行 198 项 JUnit 5 单元测试（整合内置内存模式 HSQLDB） | 控制台测试报告 |
| `mvn package` | 正式 WAR 打包 | 完整执行测试并封装为标准 Web 应用文件 | `target/jtrac.war` |
| `mvn package -DskipTests` | 快速 WAR 打包 | 跳过单元测试快速打包生成 WAR | `target/jtrac.war` |
| `mvn package -Pportable` | 便携版完整打包 | 执行测试并组装内置 Jetty 12.1 EE10 之绿色免安装 ZIP | `target/jtrac-ng-3.0.0-beta-portable.zip` |
| `mvn package -Pportable -DskipTests` | 便携版快速打包 | 跳过测试极速组装绿色免安装 ZIP | `target/jtrac-ng-3.0.0-beta-portable.zip` |
| `package-portable.bat` | Windows 脚本打包 | 根目录双击执行，自动跳过测试极速产出便携版 | `target/jtrac-ng-3.0.0-beta-portable.zip` |
| `./package-portable.sh` | Linux/macOS 脚本打包 | 终端执行 Bash 脚本极速产出便携版 | `target/jtrac-ng-3.0.0-beta-portable.zip` |
| `mvn clean` | 清理产物 | 清除 `target/` 目录下所有先前构建之缓存与编译暂存文件 | - |
| `mvn clean package -f tools/jtrac-exporter/pom.xml -DskipTests` | 打包独立工具 | 编译并组装独立命令行导出工具（Fat JAR） | `tools/jtrac-exporter.jar` |

---

## 3. Maven 依赖包自动下载机制 (`~/.m2/repository`)

JTrac NG 是基于标准 Maven 架构开发，其所有的第三方依赖库（包括 Spring 6.2.x、Apache Wicket 10.x、Hibernate 5.6.x Jakarta、Spring Security 6.5.x、Jetty 12.1.x 等）皆已声明于根目录 [`pom.xml`](../../pom.xml) 中。

### 自动下载与缓存流程：
1. 当您首次执行 `mvn compile` 或 `mvn package` 时，Maven 会自动连接至远程中央仓库（Maven Central）解析依赖树。
2. 下载的所有依赖包皆存放于用户本机缓存目录：
   - **Windows**：`%USERPROFILE%\.m2\repository\`
   - **Linux / macOS**：`~/.m2/repository/`
3. 后续无论进行多少次编译或离线打包，Maven 皆会直接从本机 `.m2` 缓存读取组件，**开发者完全不需要手动搜索、下载或配置任何第三方 JAR 文件**。

---

## 4. WAR 封装结构解析 (`WEB-INF/lib/`)

当您执行 `mvn package` 打包完成后，Maven 会在 `target/` 目录产出标准的 Java Web 应用封装文件：[`target/jtrac.war`](../../target/jtrac.war)。

### WAR 内部层级结构：
```text
jtrac.war
├── META-INF/
│   └── MANIFEST.MF
├── WEB-INF/
│   ├── classes/                 <-- JTrac 编译后的 class 类与 UTF-8 资源文件
│   │   ├── info/jtrac/...
│   │   └── messages*.properties
│   ├── lib/                     <-- 【核心所在：所有现代化第三方 Jakarta EE 10 JAR 依赖库】
│   │   ├── spring-core-6.2.19.jar
│   │   ├── wicket-core-10.12.0.jar
│   │   ├── hibernate-core-5.6.15.Final.jar
│   │   ├── spring-security-core-6.5.11.jar
│   │   ├── jakarta.servlet-api-6.0.0.jar
│   │   ├── hsqldb-2.7.4.jar
│   │   └── ... (其余所有依赖库)
│   └── web.xml                  <-- Servlet 6.0 规范配置文件
└── resources/                   <-- 静态资源（CSS、矢量 Logo SVG、样式表）
```

- **独立隔离性**：Servlet 容器启动时会自动读取并隔离每个 WAR 内部的 `WEB-INF/lib/`。
- **极简部署**：服务器本身无需手动放置任何第三方 JAR，只需部署单一 `jtrac.war` 即可开箱运行。

---

## 5. Web 容器兼容性与部署矩阵 (Web Container Matrix)

JTrac NG 3.0.0-beta 核心采用 **Jakarta EE 10 / Servlet 6.0** 规范（`jakarta.servlet.*`），编译完成后的 WAR 文件可直接部署至主流现代 Web 容器：

| Web 容器 | 版本支持 | 部署方式 |
|---|---|---|
| **Jetty 12.x** | 12.1.x+（推荐首选） | **原生支持**：启用内置 `ee10` 模块：<br/>`java -jar start.jar --add-modules=server,http,ee10-deploy,ee10-webapp`<br/>亦可直接使用内置的绿色便携版（`jtrac-ng-3.0.0-beta-portable.zip`）双击运行。 |
| **Tomcat 10.x / 11.x** | 10.1.x / 11.0.x | **原生支持**：原生采用 Jakarta EE 10，直接将 `target/jtrac.war` 复制至 `webapps/ROOT.war` 即可开箱启动。 |
| **Docker 镜像** | 3.0.0-beta | **官方推荐**：基于 `jetty:12.1-jre21-eclipse-temurin`，支持一键容器化部署。 |

---

## 6. 构建产物验证与常见问题排查 (Build Troubleshooting)

### 6.1 构建成功检查清单
编译完成后，请确认以下产物是否已正确生成：
- [ ] `target/jtrac.war`（文件大小约 50~56 MB，内含完整 Jakarta EE 10 类库）
- [ ] 若执行便携版打包，确认 `target/jtrac-ng-3.0.0-beta-portable.zip`（约 98 MB）已生成
- [ ] 若打包 CLI 工具，确认 `tools/jtrac-exporter.jar` 是否存在

### 6.2 常见构建问题排查

1. **编译器报错版本不支持 (`Fatal error compiling: invalid target release: 21`)**：
   - 原因：本机使用了 JDK 17 或更低版本进行编译。
   - 解决方法：确认 `java -version` 与 `mvn -version` 显示之 JDK 为 21+，并将 `JAVA_HOME` 指向 JDK 21 安装路径。
2. **编译时出现文字编码错误 (`unmappable character for encoding`)**：
   - 原因：Windows 环境下默认非 UTF-8 代码页可能导致注释或资源文件解析失败。
   - 解决方法：在执行 Maven 前设置全局编码参数：
     ```powershell
     $env:MAVEN_OPTS = "-Dfile.encoding=UTF-8"
     ```
3. **Maven 依赖下载损坏或中断**：
   - 原因：网络不稳定导致本机缓存了不完整的 `.jar.lastUpdated` 文件。
   - 解决方法：强制 Maven 更新远程依赖缓存：
     ```bash
     mvn clean compile -U
     ```
4. **内存不足 (`java.lang.OutOfMemoryError`)**：
   - 解决方法：调高 Maven 可用内存上限：
     ```bash
     export MAVEN_OPTS="-Xmx2048m -XX:MaxMetaspaceSize=512m"
     ```

---

## 7. 免安装环境：Docker 多阶段自动构建

若您偏好在干净、隔离的容器环境中进行自动化编译与打包，项目提供了标准的 Docker 多阶段构建方案（无需在本机安装 JDK 或 Maven）：

👉 **详细构建与运行指引请参阅：[`docker/README.md`](../../docker/README.md)**
