# JTrac NG Build & Compilation Guide (English)

[English](BUILD_en.md) | [繁體中文](BUILD_zh-TW.md) | [简体中文](BUILD_zh-CN.md) | [日本語](BUILD_ja.md) | [Tiếng Việt](BUILD_vi.md) | [Deutsch](BUILD_de.md) | [Español](BUILD_es.md) | [Français](BUILD_fr.md)

This guide provides comprehensive instructions on compiling and packaging the JTrac NG 3.0.0-beta project with Apache Maven, covering the Maven lifecycle, dependency caching, WAR and standalone Portable distribution packaging structures, web container deployment verification, and build troubleshooting.

---

## 1. Prerequisites

Before building, verify that your local environment satisfies the following requirements:

- **Operating System**: Windows / Linux / macOS
- **Java Development Kit (JDK)****: **Java 21 LTS** ([Eclipse Temurin JDK 21+](https://adoptium.net/temurin/releases/?version=21) recommended, e.g., `W:\developer\jdk-21.0.9`, minimum requirement Java 21)
  > [!IMPORTANT]
  > The core architecture has been fully upgraded to **Spring 6.2.x**, **Jakarta EE 10 (Servlet 6.0)**, **Hibernate 5.6.x (Jakarta)**, and **Apache Wicket 10.x**, targeting bytecode **Java 21**. **JDK 8 / 11 / 17 is no longer supported**; you must compile using JDK 21+.
- **Apache Maven**: **Maven 3.9+** (e.g., `W:\developer\apache-maven-3.9.9`)

### Environment Variable Setup Examples

- **Windows (PowerShell)**:
  ```powershell
  $env:JAVA_HOME = "W:\developer\jdk-21.0.9"
  $env:PATH = "W:\developer\apache-maven-3.9.9\bin;$env:PATH"
  ```
- **Windows (CMD)**:
  ```cmd
  set "JAVA_HOME=W:\developer\jdk-21.0.9"
  set "PATH=W:\developer\apache-maven-3.9.9\bin;%PATH%"
  ```
- **Linux / macOS (Bash/Zsh)**:
  ```bash
  export JAVA_HOME="/usr/lib/jvm/java-21-openjdk"
  export PATH="$JAVA_HOME/bin:$PATH"
  ```

Verify the environment:
```bash
mvn -version
```
The output should correctly display Maven 3.9+ along with Java 21.

---

## 2. Maven Build Commands Matrix

Run the following commands from the project root directory (where `pom.xml` resides):

| Command | Phase / Action | Description | Output Artifact |
|---|---|---|---|
| `mvn clean compile` | Compile Sources | Cleans build output and compiles `src/main/java` with UTF-8 resource filtering | `target/classes/` |
| `mvn test-compile` | Compile Tests | Compiles all test classes in `src/test/java` | `target/test-classes/` |
| `mvn test` | Run Tests | Executes 198 JUnit 5 unit tests (using embedded in-memory HSQLDB) | Console test reports |
| `mvn package` | Production WAR Packaging | Runs test suite and packages standard Java Web application | `target/jtrac.war` |
| `mvn package -DskipTests` | Fast WAR Packaging | Skips unit tests and quickly builds WAR | `target/jtrac.war` |
| `mvn package -Pportable` | Full Portable Packaging | Runs tests and packages standalone distribution with Jetty 12.1 EE10 | `target/jtrac-ng-3.0.0-beta-portable.zip` |
| `mvn package -Pportable -DskipTests` | Fast Portable Packaging | Skips tests and quickly builds standalone portable ZIP | `target/jtrac-ng-3.0.0-beta-portable.zip` |
| `package-portable.bat` | Windows Script Packaging | Double-click from root; automatically skips tests for fast build | `target/jtrac-ng-3.0.0-beta-portable.zip` |
| `./package-portable.sh` | Linux/macOS Script Packaging | Bash script for fast portable packaging | `target/jtrac-ng-3.0.0-beta-portable.zip` |
| `mvn clean` | Clean Artifacts | Removes all build caches and temporary files in `target/` | - |
| `mvn clean package -f tools/jtrac-exporter/pom.xml -DskipTests` | Package CLI Tool | Compiles and shades the standalone CLI issue exporter (Fat JAR) | `tools/jtrac-exporter.jar` |

---

## 3. Dependency Caching Mechanics (`~/.m2/repository`)

JTrac NG is built upon standard Apache Maven. All third-party libraries (Spring 6.2.x, Wicket 10.x, Hibernate 5.6.x Jakarta, Spring Security 6.5.x, Jetty 12.1.x, etc.) are declared in the root [`pom.xml`](../../pom.xml).

### Resolution & Caching Workflow:
1. On your first `mvn compile` or `mvn package`, Maven connects to Maven Central to resolve the complete dependency tree.
2. All downloaded JARs are stored in the user's local cache:
   - **Windows**: `%USERPROFILE%\.m2\repository\`
   - **Linux / macOS**: `~/.m2/repository/`
3. Subsequent compilations and packaging operations run entirely offline from this local cache. **Developers never need to manually download or configure external JAR files**.

---

## 4. WAR Package Structure Analysis (`WEB-INF/lib/`)

Executing `mvn package` produces the production-ready Java Web archive: [`target/jtrac.war`](../../target/jtrac.war).

### Internal Directory Layout:
```text
jtrac.war
├── META-INF/
│   └── MANIFEST.MF
├── WEB-INF/
│   ├── classes/                 <-- JTrac compiled classes and UTF-8 resource bundles
│   │   ├── info/jtrac/...
│   │   └── messages*.properties
│   ├── lib/                     <-- [Core: All modern third-party Jakarta EE 10 JAR dependencies]
│   │   ├── spring-core-6.2.19.jar
│   │   ├── wicket-core-10.12.0.jar
│   │   ├── hibernate-core-5.6.15.Final.jar
│   │   ├── spring-security-core-6.5.11.jar
│   │   ├── jakarta.servlet-api-6.0.0.jar
│   │   ├── hsqldb-2.7.4.jar
│   │   └── ... (all remaining libraries)
│   └── web.xml                  <-- Servlet 6.0 configuration descriptor
└── resources/                   <-- Static web resources (CSS, vector SVG logo, stylesheets)
```

- **Classloader Isolation**: Servlet containers automatically isolate each web application's `WEB-INF/lib/`.
- **Zero-Dependency Server Deployment**: No external libraries need to be installed in the servlet container; deploying `jtrac.war` is completely self-contained.

---

## 5. Web Container Compatibility & Deployment Matrix

JTrac NG 3.0.0-beta conforms to the **Jakarta EE 10 / Servlet 6.0** specification (`jakarta.servlet.*`). The built WAR can be deployed directly onto modern containers:

| Container | Supported Versions | Deployment Method |
|---|---|---|
| **Jetty 12.x** | 12.1.x+ (Recommended) | **Native Support**: Enable `ee10` module:<br/>`java -jar start.jar --add-modules=server,http,ee10-deploy,ee10-webapp`<br/>Or run the pre-packaged portable bundle (`jtrac-ng-3.0.0-beta-portable.zip`). |
| **Tomcat 10.x / 11.x** | 10.1.x / 11.0.x | **Native Support**: Built for Jakarta EE 10. Simply copy `target/jtrac.war` to `webapps/ROOT.war` and start. |
| **Docker Container** | 3.0.0-beta | **Official Image**: Based on `jetty:12.1-jre21-eclipse-temurin` for one-click containerization. |

---

## 6. Build Verification & Troubleshooting

### 6.1 Build Success Checklist
After build execution, verify that the following files exist:
- [ ] `target/jtrac.war` (file size approximately 50~56 MB, contains full Jakarta EE 10 libraries)
- [ ] `target/jtrac-ng-3.0.0-beta-portable.zip` (approximately 98 MB, if portable build was triggered)
- [ ] `tools/jtrac-exporter.jar` (if the CLI tool was built)

### 6.2 Common Troubleshooting Scenarios

1. **Compiler Target Version Error (`Fatal error compiling: invalid target release: 21`)**:
   - Cause: Active JDK in terminal is JDK 17 or older.
   - Fix: Switch `JAVA_HOME` to JDK 21+ and verify with `java -version`.
2. **Character Encoding Errors during Compilation (`unmappable character for encoding`)**:
   - Cause: Non-UTF-8 console code page on Windows may fail to decode UTF-8 comments or resources.
   - Fix: Set global Maven encoding before building:
     ```powershell
     $env:MAVEN_OPTS = "-Dfile.encoding=UTF-8"
     ```
3. **Corrupted or Incomplete Dependency Downloads**:
   - Cause: Network drops can result in corrupted `.jar.lastUpdated` files.
   - Fix: Force Maven to re-check and re-download dependencies:
     ```bash
     mvn clean compile -U
     ```
4. **Out of Memory (`java.lang.OutOfMemoryError`)**:
   - Fix: Allocate more memory to the Maven JVM:
     ```bash
     export MAVEN_OPTS="-Xmx2048m -XX:MaxMetaspaceSize=512m"
     ```

---

## 7. Zero-Install Alternative: Docker Multi-Stage Build

If you prefer building in a clean, isolated container environment without installing JDK or Maven locally, use the project's native Docker multi-stage build:

👉 **Refer to the dedicated guide: [`docker/README.md`](../../docker/README.md)**
