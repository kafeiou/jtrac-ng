# JTrac 编译与构建指南 (简体中文)

[English](BUILD_en.md) | [繁體中文](BUILD_zh-TW.md) | [简体中文](BUILD_zh-CN.md) | [日本語](BUILD_ja.md) | [Tiếng Việt](BUILD_vi.md) | [Deutsch](BUILD_de.md) | [Español](BUILD_es.md) | [Français](BUILD_fr.md)

本指南详细说明如何使用 Apache Maven 编译与打包 JTrac NG 3.0.0-beta 项目，深入解析 Maven 生命周期、依赖缓存管理、WAR 与 CLI 封装结构、Web 容器验证矩阵，以及常见编译问题之排查指引。

---

## 1. 前置环境需求

在开始编译之前，请确认您的开发环境符合以下条件：

- **操作系统**：Windows / Linux / macOS
- **Java 开发套件 (JDK)**：**Java 11+ / 17+**（推荐使用 JDK 17 LTS，例如 `W:\developer\jdk-17.0.9`，最低门槛为 Java 11+）
  > [!IMPORTANT]
  > 本现代化版本核心已升级至 Spring 5.3.x、Hibernate 5.6.x 与 Apache Wicket 9.x，编译目标字节码为 **Java 11**。**JDK 8 已不再支持**，请勿使用 JDK 8 进行编译。
- **Apache Maven**：**Maven 3.9+**（例如 `W:\developer\apache-maven-3.9.9`）

### 本机环境变量配置示例

- **Windows (PowerShell)**：
  ```powershell
  $env:JAVA_HOME = "W:\developer\jdk-17.0.9"
  $env:PATH = "W:\developer\apache-maven-3.9.9\bin;$env:PATH"
  ```
- **Windows (CMD)**：
  ```cmd
  set "JAVA_HOME=W:\developer\jdk-17.0.9"
  set "PATH=W:\developer\apache-maven-3.9.9\bin;%PATH%"
  ```
- **Linux / macOS (Bash/Zsh)**：
  ```bash
  export JAVA_HOME="/usr/lib/jvm/java-17-openjdk"
  export PATH="$JAVA_HOME/bin:$PATH"
  ```

验证环境指令：
```bash
mvn -version
```
输出应正确显示 Maven 3.9+ 以及对应的 Java 11 或 17 版本信息。

---

## 2. Maven 编译与构建指令矩阵

请在 JTrac 项目根目录（包含 `pom.xml` 的目录）下执行以下指令：

| 指令 | 阶段 / 作用 | 说明 |
|---|---|---|
| `mvn clean compile` | 编译主程序 | 清理旧产物并编译 `src/main/java`，自动处理资源过滤（UTF-8 `messages*.properties`） |
| `mvn test-compile` | 编译测试码 | 编译 `src/test/java` 下的所有单元测试类 |
| `mvn test` | 执行测试 | 执行 JUnit 5 单元测试（整合内置内存模式 HSQLDB，无需安装任何外部数据库） |
| `mvn package` | 正式打包 | 完整执行测试并打包为正式 Web 应用包（产出 `target/jtrac.war`） |
| `mvn package -DskipTests` | 快速打包 | 跳过单元测试快速打包生成 `target/jtrac.war` |
| `mvn clean` | 清理产物 | 清除 `target/` 目录下所有先前构建的缓存与编译暂存文件 |
| `mvn clean package -f tools/jtrac-exporter/pom.xml -DskipTests` | 打包独立工具 | 编译并组装独立命令行工单导出工具（Fat JAR），产出 `tools/jtrac-exporter.jar` |

---

## 3. Maven 依赖包自动下载机制 (`~/.m2/repository`)

JTrac 是基于标准 Maven 架构开发，其所有的第三方依赖库（包括 Spring 5.3.x、Apache Wicket 9.x、Hibernate 5.6.x、Spring Security 5.8.x 等）皆已声明于根目录 [`pom.xml`](../../pom.xml) 中。

### 自动下载与缓存流程：
1. 当您首次执行 `mvn compile` 或 `mvn package` 时，Maven 会自动连线至远程中央仓库（Maven Central）解析依赖树。
2. 下载的所有依赖包皆存放于用户本机缓存目录：
   - **Windows**：`%USERPROFILE%\.m2\repository\`
   - **Linux / macOS**：`~/.m2/repository/`
3. 后续无论进行多少次编译或离线打包，Maven 都会直接从本机 `.m2` 缓存读取包，**开发者完全不需要手动搜索、下载或配置任何第三方 JAR 文件**。

---

## 4. WAR 封装结构解析 (`WEB-INF/lib/`)

当您执行 `mvn package` 打包完成后，Maven 会在 `target/` 目录生成标准的 Java Web 应用封装包：[`target/jtrac.war`](../../target/jtrac.war)。

### WAR 内部层级结构：
```text
jtrac.war
├── META-INF/
│   └── MANIFEST.MF
├── WEB-INF/
│   ├── classes/                 <-- JTrac 编译后的 class 类与 UTF-8 资源文件
│   │   ├── info/jtrac/...
│   │   └── messages*.properties
│   ├── lib/                     <-- 【核心所在：所有现代化第三方 JAR 依赖库】
│   │   ├── spring-core-5.3.37.jar
│   │   ├── wicket-core-9.16.0.jar
│   │   ├── hibernate-core-5.6.15.Final.jar
│   │   ├── spring-security-core-5.8.14.jar
│   │   ├── hsqldb-2.7.2.jar
│   │   └── ... (其余所有依赖库)
│   └── web.xml                  <-- Servlet 4.0 规范配置文件
└── resources/                   <-- 静态资源（CSS、图标、样式表）
```

- **独立隔离性**：Servlet 容器（如 Jetty、Tomcat）启动时会自动读取并隔离每个 WAR 内部的 `WEB-INF/lib/`。
- **极简部署**：服务器本身无需手动放置任何第三方 JAR，只需部署单个 `jtrac.war` 即可开箱运行。

---

## 5. Web 容器兼容性与部署矩阵 (Web Container Matrix)

JTrac NG 3.0.0-beta 核心采用 Servlet 4.0 规范（`javax.servlet`），编译完成后的 WAR 包可直接部署至主流现代 Web 容器：

| Web 容器 | 版本支持 | 部署方式 |
|---|---|---|
| **Jetty 10.x** | 10.0.x（推荐首选） | **开箱即用**：直接将 `target/jtrac.war` 复制至 `webapps/ROOT.war` 即可启动。 |
| **Jetty 12.x** | 12.0.x（最新版） | **原生支持**：启用内置 `ee8` 模块：<br/>`java -jar start.jar --add-modules=server,http,ee8-deploy,ee8-webapp`，即可直接部署 `jtrac.war`。 |
| **Tomcat 9.x** | 9.0.x（推荐首选） | **开箱即用**：直接将 `target/jtrac.war` 复制至 `webapps/ROOT.war` 即可启动。 |
| **Tomcat 10.x / 11.x** | 10.1.x / 11.0.x | **自动转换支持**：<br/>1. **方式 A**：将 `jtrac.war` 放入 Tomcat 的 `webapps-javaee/` 目录，容器启动时会自动转换运行。<br/>2. **方式 B**：使用 Tomcat 官方 `jakartaee-migration` 工具转换为 `jtrac-jakarta.war` 后直接部署至 `webapps/`。 |

### 本地 Jetty 10 快速启动验证示例：
1. 将 `target/jtrac.war` 复制为 `W:\developer\jetty-10.0.26\webapps\ROOT.war`。
2. 启动 Jetty：
   ```powershell
   & "W:\developer\jdk-17.0.9\bin\java.exe" -jar W:\developer\jetty-10.0.26\start.jar
   ```
3. 打开浏览器访问：`http://localhost:8888/`（默认管理员账号：`admin` / 密码：`admin`）。

---

## 6. 构建产物验证与常见问题排查 (Build Troubleshooting)

### 6.1 构建成功检查清单
编译完成后，请确认以下产物是否已正确生成：
- [ ] `target/jtrac.war`（文件大小约 18~22 MB，已去除过时 POI 库显著瘦身）
- [ ] 若打包 CLI 工具，确认 `tools/jtrac-exporter.jar` 是否存在

### 6.2 常见构建问题排查

1. **编译时出现文字编码错误 (`unmappable character for encoding`)**：
   - 原因：Windows 环境下默认非 UTF-8 代码页可能导致注释或资源文件解析失败。
   - 解决方法：在执行 Maven 前设置全局编码参数：
     ```powershell
     $env:MAVEN_OPTS = "-Dfile.encoding=UTF-8"
     ```
2. **编译器报错版本不支持 (`Fatal error compiling: invalid target release: 11`)**：
   - 原因：本机使用了 JDK 8 或更低版本进行编译。
   - 解决方法：确认 `java -version` 与 `mvn -version` 显示的 JDK 为 11 或 17。
3. **Maven 依赖下载损坏或中断**：
   - 原因：网络不稳定导致本机缓存了不完整的 `.jar.lastUpdated` 文件。
   - 解决方法：强制 Maven 更新远程依赖缓存：
     ```bash
     mvn clean compile -U
     ```
4. **内存不足 (`java.lang.OutOfMemoryError`)**：
   - 解决方法：提高 Maven 可用内存上限：
     ```bash
     export MAVEN_OPTS="-Xmx1024m -XX:MaxMetaspaceSize=256m"
     ```

---

## 7. 免安装环境：Docker 多阶段自动构建

若您偏好在干净、隔离的容器环境中进行自动化编译与打包，项目提供了标准的 Docker 多阶段构建方案（无需在本机安装 JDK 或 Maven）：

👉 **详细构建与运行指引请参阅：[`docker/README.md`](../../docker/README.md)**
