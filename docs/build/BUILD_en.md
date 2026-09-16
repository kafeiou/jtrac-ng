# JTrac Build & Compilation Guide (English)

[English](BUILD_en.md) | [繁體中文](BUILD_zh-TW.md) | [简体中文](BUILD_zh-CN.md) | [日本語](BUILD_ja.md) | [Tiếng Việt](BUILD_vi.md) | [Deutsch](BUILD_de.md) | [Español](BUILD_es.md) | [Français](BUILD_fr.md)

This guide provides comprehensive instructions on compiling and packaging the JTrac NG 3.0.0-beta project with Apache Maven, covering the Maven lifecycle, dependency caching, WAR and CLI archive internal structures, web container deployment verification, and build troubleshooting.

---

## 1. Prerequisites

Before building, verify that your local environment satisfies the following requirements:

- **Operating System**: Windows / Linux / macOS
- **Java Development Kit (JDK)**: **Java 11+ / 17+** (JDK 17 LTS recommended, e.g., `W:\developer\jdk-17.0.9`, minimum requirement Java 11+)
  > [!IMPORTANT]
  > The core architecture has been modernized to Spring 5.3.x, Hibernate 5.6.x, and Apache Wicket 9.x, targeting bytecode **Java 11**. **JDK 8 is no longer supported**; do not attempt to compile with JDK 8.
- **Apache Maven**: **Maven 3.9+** (e.g., `W:\developer\apache-maven-3.9.9`)

### Environment Variable Setup Examples

- **Windows (PowerShell)**:
  ```powershell
  $env:JAVA_HOME = "W:\developer\jdk-17.0.9"
  $env:PATH = "W:\developer\apache-maven-3.9.9\bin;$env:PATH"
  ```
- **Windows (CMD)**:
  ```cmd
  set "JAVA_HOME=W:\developer\jdk-17.0.9"
  set "PATH=W:\developer\apache-maven-3.9.9\bin;%PATH%"
  ```
- **Linux / macOS (Bash/Zsh)**:
  ```bash
  export JAVA_HOME="/usr/lib/jvm/java-17-openjdk"
  export PATH="$JAVA_HOME/bin:$PATH"
  ```

Verify the environment:
```bash
mvn -version
```
The output should correctly display Maven 3.9+ along with Java 11 or 17.

---

## 2. Maven Build Commands Matrix

Run the following commands from the project root directory (where `pom.xml` resides):

| Command | Phase / Action | Description |
|---|---|---|
| `mvn clean compile` | Compile Sources | Cleans build output and compiles `src/main/java` with UTF-8 resource filtering |
| `mvn test-compile` | Compile Tests | Compiles all test classes in `src/test/java` |
| `mvn test` | Run Tests | Executes JUnit 5 unit tests (using embedded in-memory HSQLDB; no external database required) |
| `mvn package` | Production Packaging | Runs test suite and packages the application into production WAR (`target/jtrac.war`) |
| `mvn package -DskipTests` | Fast Packaging | Skips unit tests and quickly builds `target/jtrac.war` |
| `mvn clean` | Clean Artifacts | Removes all build caches and temporary files in `target/` |
| `mvn clean package -f tools/jtrac-exporter/pom.xml -DskipTests` | Package CLI Tool | Compiles and shades the standalone CLI issue exporter (Fat JAR) into `tools/jtrac-exporter.jar` |

---

## 3. Dependency Caching Mechanics (`~/.m2/repository`)

JTrac is built upon standard Apache Maven. All third-party libraries (Spring 5.3.x, Wicket 9.x, Hibernate 5.6.x, Spring Security 5.8.x, etc.) are declared in the root [`pom.xml`](../../pom.xml).

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
│   ├── lib/                     <-- [Core: All modern third-party JAR dependencies]
│   │   ├── spring-core-5.3.37.jar
│   │   ├── wicket-core-9.16.0.jar
│   │   ├── hibernate-core-5.6.15.Final.jar
│   │   ├── spring-security-core-5.8.14.jar
│   │   ├── hsqldb-2.7.2.jar
│   │   └── ... (all remaining libraries)
│   └── web.xml                  <-- Servlet 4.0 configuration descriptor
└── resources/                   <-- Static web resources (CSS, icons, images)
```

- **Classloader Isolation**: Servlet containers automatically isolate each web application's `WEB-INF/lib/`.
- **Zero-Dependency Server Deployment**: No external libraries need to be installed in the servlet container; deploying `jtrac.war` is completely self-contained.

---

## 5. Web Container Compatibility & Deployment Matrix

JTrac NG 3.0.0-beta conforms to the Servlet 4.0 specification (`javax.servlet`). The built WAR can be deployed directly onto modern containers:

| Container | Supported Versions | Deployment Method |
|---|---|---|
| **Jetty 10.x** | 10.0.x (Recommended) | **Out-of-the-box**: Copy `target/jtrac.war` to `webapps/ROOT.war` and start. |
| **Jetty 12.x** | 12.0.x (Latest) | **Native Support**: Enable the `ee8` module:<br/>`java -jar start.jar --add-modules=server,http,ee8-deploy,ee8-webapp` and deploy `jtrac.war`. |
| **Tomcat 9.x** | 9.0.x (Recommended) | **Out-of-the-box**: Copy `target/jtrac.war` to `webapps/ROOT.war` and start. |
| **Tomcat 10.x / 11.x** | 10.1.x / 11.0.x | **Automatic Migration**:<br/>1. **Method A**: Place `jtrac.war` in Tomcat's `webapps-javaee/` folder for runtime auto-conversion.<br/>2. **Method B**: Use Tomcat's official `jakartaee-migration` tool to produce `jtrac-jakarta.war` and deploy to `webapps/`. |

### Local Jetty 10 Quick Verification:
1. Copy `target/jtrac.war` to `W:\developer\jetty-10.0.26\webapps\ROOT.war`.
2. Start Jetty:
   ```powershell
   & "W:\developer\jdk-17.0.9\bin\java.exe" -jar W:\developer\jetty-10.0.26\start.jar
   ```
3. Open `http://localhost:8888/` in your browser (default credentials: `admin` / `admin`).

---

## 6. Build Verification & Troubleshooting

### 6.1 Build Success Checklist
After build execution, verify that the following files exist:
- [ ] `target/jtrac.war` (file size approximately 18~22 MB, streamlined without legacy POI libraries)
- [ ] `tools/jtrac-exporter.jar` (if the CLI tool was built)

### 6.2 Common Troubleshooting Scenarios

1. **Character Encoding Errors during Compilation (`unmappable character for encoding`)**:
   - Cause: Non-UTF-8 console code page on Windows may fail to decode UTF-8 comments or resources.
   - Fix: Set global Maven encoding before building:
     ```powershell
     $env:MAVEN_OPTS = "-Dfile.encoding=UTF-8"
     ```
2. **Compiler Target Version Error (`Fatal error compiling: invalid target release: 11`)**:
   - Cause: Active JDK in terminal is JDK 8 or older.
   - Fix: Switch `JAVA_HOME` to JDK 11 or JDK 17 and verify with `java -version`.
3. **Corrupted or Incomplete Dependency Downloads**:
   - Cause: Network drops can result in corrupted `.jar.lastUpdated` files.
   - Fix: Force Maven to re-check and re-download dependencies:
     ```bash
     mvn clean compile -U
     ```
4. **Out of Memory (`java.lang.OutOfMemoryError`)**:
   - Fix: Allocate more memory to the Maven JVM:
     ```bash
     export MAVEN_OPTS="-Xmx1024m -XX:MaxMetaspaceSize=256m"
     ```

---

## 7. Zero-Install Alternative: Docker Multi-Stage Build

If you prefer building in a clean, isolated container environment without installing JDK or Maven locally, use the project's native Docker multi-stage build:

👉 **Refer to the dedicated guide: [`docker/README.md`](../../docker/README.md)**
