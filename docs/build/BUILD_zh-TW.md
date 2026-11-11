# JTrac NG 編譯與建置指南 (繁體中文)

[English](BUILD_en.md) | [繁體中文](BUILD_zh-TW.md) | [简体中文](BUILD_zh-CN.md) | [日本語](BUILD_ja.md) | [Tiếng Việt](BUILD_vi.md) | [Deutsch](BUILD_de.md) | [Español](BUILD_es.md) | [Français](BUILD_fr.md)

本指南詳細說明如何使用 Apache Maven 編譯與打包 JTrac NG 3.0.0-beta 專案，深入解析 Maven 生命週期、依賴快取管理、WAR 與綠色免安裝可攜版 (Portable) 封裝結構、Web 容器相容性矩陣，以及常見編譯問題之排除指引。

---

## 1. 前置環境需求

在開始編譯之前，請確認您的開發環境符合以下條件：

- **作業系統**：Windows / Linux / macOS
- **Java 開發套件 (JDK)**：**Java 21 LTS**（推薦使用 [Eclipse Temurin JDK 21+](https://adoptium.net/temurin/releases/?version=21)，例如 `W:\developer\jdk-21.0.9`，最低門檻為 Java 21）
  > [!IMPORTANT]
  > 本現代化版本核心已全面躍升至 **Spring 6.2.x**、**Jakarta EE 10 (Servlet 6.0)**、**Hibernate 5.6.x (Jakarta)** 與 **Apache Wicket 10.x**，編譯目標位元組碼為 **Java 21**。**JDK 8 / 11 / 17 已不再支援**，請務必使用 JDK 21+ 進行編譯。
- **Apache Maven**：**Maven 3.9+**（例如 `W:\developer\apache-maven-3.9.9`）

### 本機環境變數設定範例

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

驗證環境指令：
```bash
mvn -version
```
輸出應正確顯示 Maven 3.9+ 以及對應之 Java 21 版本資訊。

---

## 2. Maven 編譯與建置指令矩陣

請在 JTrac NG 專案根目錄（包含 `pom.xml` 的目錄）下執行以下指令：

| 指令 | 階段 / 作用 | 說明 | 產物路徑 |
|---|---|---|---|
| `mvn clean compile` | 編譯主程式 | 清理舊產物並編譯 `src/main/java`，自動處理資源過濾 | `target/classes/` |
| `mvn test-compile` | 編譯測試碼 | 編譯 `src/test/java` 下之所有單元測試類別 | `target/test-classes/` |
| `mvn test` | 執行測試 | 執行 198 項 JUnit 5 單元測試（整合內建記憶體模式 HSQLDB） | 控制台測試報表 |
| `mvn package` | 正式 WAR 打包 | 完整執行測試並封裝為標準 Web 應用檔案 | `target/jtrac.war` |
| `mvn package -DskipTests` | 快速 WAR 打包 | 略過單元測試快速打包生成 WAR | `target/jtrac.war` |
| `mvn package -Pportable` | 可攜版完整打包 | 執行測試並組裝內建 Jetty 12.1 EE10 之綠色免安裝 ZIP | `target/jtrac-ng-3.0.0-beta-portable.zip` |
| `mvn package -Pportable -DskipTests` | 可攜版快速打包 | 略過測試極速組裝綠色免安裝 ZIP | `target/jtrac-ng-3.0.0-beta-portable.zip` |
| `package-portable.bat` | Windows 腳本打包 | 根目錄雙擊執行，自動略過測試極速產出可攜版 | `target/jtrac-ng-3.0.0-beta-portable.zip` |
| `./package-portable.sh` | Linux/macOS 腳本打包 | 終端機執行 Bash 腳本極速產出可攜版 | `target/jtrac-ng-3.0.0-beta-portable.zip` |
| `mvn clean` | 清理產物 | 清除 `target/` 目錄下所有先前建置之快取與編譯暫存檔 | - |
| `mvn clean package -f tools/jtrac-exporter/pom.xml -DskipTests` | 打包獨立工具 | 編譯並組裝獨立命令列匯出工具（Fat JAR） | `tools/jtrac-exporter.jar` |

---

## 3. Maven 依賴套件自動下載機制 (`~/.m2/repository`)

JTrac NG 是基於標準 Maven 架構開發，其所有的第三方依賴函式庫（包括 Spring 6.2.x、Apache Wicket 10.x、Hibernate 5.6.x Jakarta、Spring Security 6.5.x、Jetty 12.1.x 等）皆已宣告於根目錄 [`pom.xml`](../../pom.xml) 中。

### 自動下載與快取流程：
1. 當您首次執行 `mvn compile` 或 `mvn package` 時，Maven 會自動連線至遠端中央倉庫（Maven Central）解析相依樹。
2. 下載的所有相依套件皆存放於使用者本機快取目錄：
   - **Windows**：`%USERPROFILE%\.m2\repository\`
   - **Linux / macOS**：`~/.m2/repository/`
3. 後續無論進行多少次編譯或離線打包，Maven 皆會直接自本機 `.m2` 快取讀取套件，**開發者完全不需要手動搜尋、下載或配置任何第三方 JAR 檔案**。

---

## 4. WAR 封裝結構解析 (`WEB-INF/lib/`)

當您執行 `mvn package` 打包完成後，Maven 會在 `target/` 目錄產出標準的 Java Web 應用封裝檔：[`target/jtrac.war`](../../target/jtrac.war)。

### WAR 內部階層結構：
```text
jtrac.war
├── META-INF/
│   └── MANIFEST.MF
├── WEB-INF/
│   ├── classes/                 <-- JTrac 編譯後的 class 類別與 UTF-8 資源檔
│   │   ├── info/jtrac/...
│   │   └── messages*.properties
│   ├── lib/                     <-- 【核心所在：所有現代化第三方 Jakarta EE 10 JAR 依賴庫】
│   │   ├── spring-core-6.2.19.jar
│   │   ├── wicket-core-10.12.0.jar
│   │   ├── hibernate-core-5.6.15.Final.jar
│   │   ├── spring-security-core-6.5.11.jar
│   │   ├── jakarta.servlet-api-6.0.0.jar
│   │   ├── hsqldb-2.7.4.jar
│   │   └── ... (其餘所有依賴庫)
│   └── web.xml                  <-- Servlet 6.0 規範設定檔
└── resources/                   <-- 靜態資源（CSS、向量 Logo SVG、樣式表）
```

- **獨立隔離性**：Servlet 容器啟動時會自動讀取並隔離每個 WAR 內部的 `WEB-INF/lib/`。
- **極簡部署**：伺服器本身無需手動放置任何第三方 JAR，只需部署單一 `jtrac.war` 即可開箱運行。

---

## 5. Web 容器相容性與部署矩陣 (Web Container Matrix)

JTrac NG 3.0.0-beta 核心採用 **Jakarta EE 10 / Servlet 6.0** 規範（`jakarta.servlet.*`），編譯完成後之 WAR 檔可直接部署至主流現代 Web 容器：

| Web 容器 | 版本支援 | 部署方式 |
|---|---|---|
| **Jetty 12.x** | 12.1.x+（推薦首選） | **原生支援**：啟用內建 `ee10` 模組：<br/>`java -jar start.jar --add-modules=server,http,ee10-deploy,ee10-webapp`<br/>亦可直接使用內建之綠色可攜版（`jtrac-ng-3.0.0-beta-portable.zip`）雙擊執行。 |
| **Tomcat 10.x / 11.x** | 10.1.x / 11.0.x | **原生支援**：原生採用 Jakarta EE 10，直接將 `target/jtrac.war` 複製至 `webapps/ROOT.war` 即可開箱啟動。 |
| **Docker 映像** | 3.0.0-beta | **官方推薦**：基於 `jetty:12.1-jre21-eclipse-temurin`，支援一鍵容器化部署。 |

---

## 6. 建置產物驗證與常見問題排除 (Build Troubleshooting)

### 6.1 建置成功檢查清單
編譯完成後，請確認以下產物是否已正確生成：
- [ ] `target/jtrac.war`（檔案大小約 50~56 MB，內含完整 Jakarta EE 10 函式庫）
- [ ] 若執行可攜版打包，確認 `target/jtrac-ng-3.0.0-beta-portable.zip`（約 98 MB）已生成
- [ ] 若打包 CLI 工具，確認 `tools/jtrac-exporter.jar` 是否存在

### 6.2 常見建置問題排查

1. **編譯器回報版本不支援 (`Fatal error compiling: invalid target release: 21`)**：
   - 原因：本機使用了 JDK 17 或更低版本進行編譯。
   - 解決方法：確認 `java -version` 與 `mvn -version` 顯示之 JDK 為 21+，並將 `JAVA_HOME` 指向 JDK 21 安裝路徑。
2. **編譯時出現文字編碼錯誤 (`unmappable character for encoding`)**：
   - 原因：Windows 環境下預設非 UTF-8 代碼頁可能導致註解或資源檔解析失敗。
   - 解決方法：在執行 Maven 前設定全域編碼參數：
     ```powershell
     $env:MAVEN_OPTS = "-Dfile.encoding=UTF-8"
     ```
3. **Maven 依賴下載損毀或中斷**：
   - 原因：網路不穩定導致本機快取了不完整的 `.jar.lastUpdated` 檔案。
   - 解決方法：強制 Maven 更新遠端依賴快取：
     ```bash
     mvn clean compile -U
     ```
4. **記憶體不足 (`java.lang.OutOfMemoryError`)**：
   - 解決方法：提高 Maven 可用記憶體上限：
     ```bash
     export MAVEN_OPTS="-Xmx2048m -XX:MaxMetaspaceSize=512m"
     ```

---

## 7. 免安裝環境：Docker 多階段自動建置

若您偏好在乾淨、隔離的容器環境中進行自動化編譯與打包，專案提供了標準的 Docker 多階段建置方案（無需在本機安裝 JDK 或 Maven）：

👉 **詳細建置與運行指引請參閱：[`docker/README.md`](../../docker/README.md)**
