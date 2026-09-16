# JTrac NG 發布說明 (Release Notes) - 2.3.3-2.0.0

[English](release-2.3.3-2.0.0_en.md) | [繁體中文](release-2.3.3-2.0.0_zh-TW.md) | [简体中文](release-2.3.3-2.0.0_zh-CN.md) | [日本語](release-2.3.3-2.0.0_ja.md) | [Tiếng Việt](release-2.3.3-2.0.0_vi.md) | [Deutsch](release-2.3.3-2.0.0_de.md) | [Español](release-2.3.3-2.0.0_es.md) | [Français](release-2.3.3-2.0.0_fr.md)

---

[![Java](https://img.shields.io/badge/Java-11%20%7C%2017-orange.svg)](https://adoptium.net/)
[![Maven](https://img.shields.io/badge/Maven-3.9+-blue.svg)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](../../license.txt)
[![Fork From](https://img.shields.io/badge/Fork%20From-JTrac%202.3.3-blue)](https://jtrac.info)
[![Version](https://img.shields.io/badge/Version-2.3.3--2.0.0-blue.svg)](../../pom.xml)
[![Wicket](https://img.shields.io/badge/Wicket-9.16.0-blue.svg)](https://wicket.apache.org/)
[![Spring](https://img.shields.io/badge/Spring-5.3.37-brightgreen.svg)](https://spring.io/)

本專案為源自 [JTrac 2.3.3 (https://jtrac.info)](https://jtrac.info) 版本。致力於提供更輕量、高相容性、具備離線靜態歸檔、良好使用者介面的 Q&A 文字紀錄追蹤系統，非常適合知識管理使用，若有複雜的流程則佐以附件。

---

## 🚀 版本 2.3.3-2.0.0 重大升級 (Major Architecture Upgrade)

1. **後端核心架構全面升級 (Spring 5.3 + Hibernate 5.6 + JUnit 5)**：
   - 全面升級至 Spring Framework 5.3.37，移除已廢棄的 `HibernateTemplate` 與 `TimerFactoryBean`。
   - 升級至 Hibernate ORM 5.6.15.Final，原生 SessionFactory 管理與 JPA 規範查詢。
   - 全文檢索脫離已停護的 `spring-modules-lucene`，改用原生輕量 Lucene 檢索實作。
   - 單元測試全面升級至 JUnit 5 (Jupiter)。
2. **安全性架構全面重構 (Spring Security 5.8 + BCrypt 平滑遷移)**：
   - 徹底移除過時且存在安全隱患的 Acegi Security 1.0.7，引進標準 Spring Security 5.8.14。
   - 實作雙模相容 `JtracHybridPasswordEncoder`：相容舊有 MD5 密碼雜湊，並於使用者登入成功時無感自動重新雜湊升級為強安全的 BCrypt，資料庫升級無需人為介入重設密碼。
3. **Web 表現層升級至 Apache Wicket 9.16.0**：
   - 升級至 Wicket 9.16.0，元件與模型全面泛型化（`IModel<T>`）。
   - 支援 Servlet 4.0 容器（如 Jetty 10.0.26、Jetty 12、Tomcat 9 與 Tomcat 10+）。
4. **使用者管理與專案空間分頁導覽 (Pagination & System Config)**：
   - 使用者列表 (`UserListPage`) 與專案列表 (`SpaceListPage`) 升級支援自訂分頁（10, 25, 50, 100, 全部），避免大量資料效能瓶頸。
   - 於 `config` 表註冊全域參數 `users.list.pageSize` 與 `spaces.list.pageSize`，支援預設值自訂。
5. **成員指派與角色授權事件修復 (Role Allocation Ajax Fix)**：
   - 修正專案分配頁面的 Ajax 事件為標準 DOM 原生 `"change"` 事件，修復選取未即時更新及清空防呆。
6. **全域靜態資源過濾器 (Static Resource Filter)**：
   - 引入 `StaticResourceFilter`，根治多層路徑（如 `/app/space/allocate/...`）下 `../resources/*` 破圖 404 問題，並補齊缺漏圖示。
7. **表單附件上傳模型綁定修復 (FileUpload Model Binding)**：
   - 為 `ItemFormPage` 與 `ItemViewFormPanel` 的 `FileUploadField` 顯式綁定獨立 Model，根除向實體類別反射查無 `file` 屬性的 Wicket 執行期例外。
8. **資料庫平滑升級與 SQL 指南**：
   - 提供專屬升級腳本 [`etc/sql/upgrade-to-2.0.0.sql`](../../etc/sql/upgrade-to-2.0.0.sql)，支援 MySQL、PostgreSQL、SQL Server、Oracle。
   - 內建 `HsqldbDatabaseMigrator`，於啟動時自動備份並無痛升級 HSQLDB 1.8 至 2.x。
9. **Excel 模組清理與 WAR 產物瘦身 (Excel Module Removal & POI Deprecation)**：
   - 徹底移除過時的 Excel 匯入與匯出模組，並完全刪除 Apache POI 相關依賴，使 WAR 封裝檔大小大幅縮減超過 3 MB。
10. **全系統備份包升級 (Integrated SQL Dump in Backup Bundle)**：
    - 全系統備份 ZIP 壓縮檔內新增單一整合 SQL 傾印檔 `jtrac-dump.sql`，包含通用 ANSI DDL、主流資料庫方言註解、14 張資料表依外鍵拓撲排序之 ANSI INSERT 敘述與 Sequence 自增重置指令，提供 DBA 離線手動災難復原與跨庫資料遷移。
11. **純專案 ID 附件目錄分區儲存與 Lucene 全文檢索 (Attachment Partitioning & Lucene Indexing)**：
    - **純專案 ID 目錄結構 (選項 C)**：附件全面依純數字專案 ID 分區儲存（`${jtrac.home}/attachments/{spaceId}/{prefix}_{filename}`），徹底免疫專案更名風險。
    - **雙軌查檔安全網 (Dual-Read Fallback)**：讀取時自動 Fallback 至根目錄與孤兒隔離目錄（`attachments/0_ORPHAN/`），確保升級過渡期 0% 下載 404 斷鏈。
    - **啟動全自動搬移升級**：伺服器啟動時自動掃描平鋪附件並歸類至專案子目錄，完成後建立標記檔（`.attachment_migrated`）避免重複掃描。
    - **多格式文字抽取與全文檢索**：支援 `.xlsx`、`.docx`（純 JDK 串流 OpenXML 解析）、`.pdf`（Apache PDFBox 2.0.31）、`.txt`、`.csv`、`.md`、`.log`，整合 `SmartCharsetDetector` 智慧偵測編碼防止中文亂碼。
    - **防護網與背景非同步佇列**：內建單檔 10MB 與 50,000 字元截斷防護，新檔上傳採背景執行緒池（`ExecutorService`）非同步索引，並支援「重建索引」全量抽取。

---

## 開發技術與架構 (Technologies & Architecture)

- **核心語言**：Java 11 / 17
- **Web 框架**：Apache Wicket 9.16.0
- **後端 IoC**：Spring Framework 5.3.37
- **安全防護**：Spring Security 5.8.14 (BCrypt 密碼加密)
- **ORM 與持久層**：Hibernate ORM 5.6.15.Final
- **支援資料庫**：HSQLDB 2.x（內建預設）、MySQL / MariaDB、PostgreSQL、Microsoft SQL Server、Oracle
- **支援 Web 容器**：
  - **Jetty 10.x**（原生支援，開箱即用，實機驗證於 Jetty 10.0.26）
  - **Jetty 12.x**（啟用 `ee8` 模組原生運行）
  - **Tomcat 9.x**（原生支援，開箱即用）
  - **Tomcat 10.x / 11.x**（透過 `webapps-javaee/` 自動轉換或 `jakartaee-migration` 轉檔）
- **建置工具**：Apache Maven 3.9+

---

## 📜 歷史版本 (Release History)

- **下一版本 (預覽)**：[JTrac NG 發布說明 - 3.0.0-beta](release-3.0.0-beta_zh-TW.md)
- **前一版本**：[JTrac NG 發布說明 - 2.3.3-1.0.0](release-2.3.3-1.0.0_zh-TW.md)

---

## 授權條款 (License)

JTrac 為開源軟體，遵循 [Apache Software License, Version 2.0](../../license.txt)。
