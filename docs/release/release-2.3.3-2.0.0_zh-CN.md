# JTrac NG 发布说明 (Release Notes) - 2.3.3-2.0.0

[English](release-2.3.3-2.0.0_en.md) | [繁體中文](release-2.3.3-2.0.0_zh-TW.md) | [简体中文](release-2.3.3-2.0.0_zh-CN.md) | [日本語](release-2.3.3-2.0.0_ja.md) | [Tiếng Việt](release-2.3.3-2.0.0_vi.md) | [Deutsch](release-2.3.3-2.0.0_de.md) | [Español](release-2.3.3-2.0.0_es.md) | [Français](release-2.3.3-2.0.0_fr.md)

---

[![Java](https://img.shields.io/badge/Java-11%20%7C%2017-orange.svg)](https://adoptium.net/)
[![Maven](https://img.shields.io/badge/Maven-3.9+-blue.svg)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](../../license.txt)
[![Fork From](https://img.shields.io/badge/Fork%20From-JTrac%202.3.3-blue)](https://jtrac.info)
[![Version](https://img.shields.io/badge/Version-2.3.3--2.0.0-blue.svg)](../../pom.xml)
[![Wicket](https://img.shields.io/badge/Wicket-9.16.0-blue.svg)](https://wicket.apache.org/)
[![Spring](https://img.shields.io/badge/Spring-5.3.37-brightgreen.svg)](https://spring.io/)

本项目为源自 [JTrac 2.3.3 (https://jtrac.info)](https://jtrac.info) 版本。致力于提供更轻量、高兼容性、具备离线静态归档、良好用户界面的 Q&A 文字记录追踪系统，非常适合知识管理使用，若有复杂的流程则佐以附件。

---

## 🚀 版本 2.3.3-2.0.0 重大升级 (Major Architecture Upgrade)

1. **后端核心架构全面升级 (Spring 5.3 + Hibernate 5.6 + JUnit 5)**：
   - 全面升级至 Spring Framework 5.3.37，移除废弃的 `HibernateTemplate` 与 `TimerFactoryBean`。
   - 升级至 Hibernate ORM 5.6.15.Final，原生 SessionFactory 管理与 JPA 规范查询。
   - 全文检索脱离已停止维护的 `spring-modules-lucene`，改用轻量原生 Lucene API。
   - 单元测试全面升级至 JUnit 5 (Jupiter)。
2. **安全性架构全面重构 (Spring Security 5.8 + BCrypt 平滑迁移)**：
   - 彻底移除过时且存在已知安全风险的 Acegi Security 1.0.7，引入标准 Spring Security 5.8.14。
   - 实现双模兼容 `JtracHybridPasswordEncoder`：兼容旧版 MD5 密码哈希，并在用户成功登录时自动重新哈希升级为高强度的 BCrypt，数据库平滑过渡无需人工重置密码。
3. **Web 表现层升级至 Apache Wicket 9.16.0**：
   - 升级至 Wicket 9.16.0，组件与模型全面泛型化（`IModel<T>`）。
   - 完美适配 Servlet 4.0 容器（Jetty 10.0.26、Jetty 12、Tomcat 9、Tomcat 10+）。
4. **用户管理与项目空间分页导航 (Pagination & System Config)**：
   - 用户列表 (`UserListPage`) 与项目空间列表 (`SpaceListPage`) 支持自定义分页（10, 25, 50, 100, 全部），消除大量数据加载性能瓶颈。
   - 于 `config` 表注册全局参数 `users.list.pageSize` 与 `spaces.list.pageSize`，支持默认分页大小配置。
5. **项目成员分配与角色授权事件修复 (Role Allocation Ajax Fix)**：
   - 修正项目分配页面 Ajax 监听事件为原生 `"change"` 事件，修复选择未实时更新及清空防呆。
6. **全局静态资源过滤器 (Static Resource Filter)**：
   - 引入 `StaticResourceFilter`，根治多层路径（如 `/app/space/allocate/...`）下 `../resources/*` 破图 404 问题，并补齐缺漏图标。
7. **表单附件上传模型绑定修复 (FileUpload Model Binding)**：
   - 为 `ItemFormPage` 与 `ItemViewFormPanel` 的 `FileUploadField` 显式绑定独立 Model，消除向实体类反射查找 `file` 属性的运行时异常。
8. **数据库平滑升级与 SQL 指南**：
   - 提供专用升级脚本 [`etc/sql/upgrade-to-2.0.0.sql`](../../etc/sql/upgrade-to-2.0.0.sql)，支持 MySQL、PostgreSQL、SQL Server、Oracle。
   - 内置 `HsqldbDatabaseMigrator`，启动时自动备份并平滑升级 HSQLDB 1.8 至 2.x。
9. **Excel 模块清理与 WAR 产物体积瘦身 (Excel Module Removal & POI Deprecation)**：
   - 彻底移除过时的 Excel 导入与导出模块，并完全删除 Apache POI 相关依赖，使 WAR 打包体积显著缩减超过 3 MB。
10. **全系统备份包升级 (Integrated SQL Dump in Backup Bundle)**：
    - 全系统备份 ZIP 压缩包内新增单文件整合 SQL 转储脚本 `jtrac-dump.sql`，包含通用 ANSI DDL、主流数据库方言注释、14 张数据表依外键拓扑排序之 ANSI INSERT 语句与 Sequence 自增重置指令，供 DBA 离线手动灾难恢复与跨库迁移。
11. **纯项目 ID 附件目录分区存储与 Lucene 全文检索 (Attachment Partitioning & Lucene Indexing)**：
    - **纯项目 ID 目录结构 (选项 C)**：附件全面依纯数字项目 ID 分区存储（`${jtrac.home}/attachments/{spaceId}/{prefix}_{filename}`），彻底规避项目更名风险。
    - **双轨查档安全网 (Dual-Read Fallback)**：读取时自动 Fallback 至根目录与孤儿隔离目录（`attachments/0_ORPHAN/`），确保升级过渡期 0% 下载 404 断链。
    - **启动全自动迁移升级**：服务器启动时自动扫描平铺附件并归类至项目子目录，完成后建立标记文件（`.attachment_migrated`）避免重复扫描。
    - **多格式文本抽取与全文检索**：支持 `.xlsx`、`.docx`（纯 JDK 流式 OpenXML 解析）、`.pdf`（Apache PDFBox 2.0.31）、`.txt`、`.csv`、`.md`、`.log`，整合 `SmartCharsetDetector` 智能探测编码防止中文乱码。
    - **防护网与后台异步队列**：内置单文件 10MB 与 50,000 字符截断防护，新文件上传采用后台线程池（`ExecutorService`）异步索引，并支持“重建索引”全量抽取。

---

## 开发技术与架构 (Technologies & Architecture)

- **核心语言**：Java 11 / 17
- **Web 框架**：Apache Wicket 9.16.0
- **后端 IoC**：Spring Framework 5.3.37
- **安全防护**：Spring Security 5.8.14 (BCrypt)
- **ORM 与持久层**：Hibernate ORM 5.6.15.Final
- **支持数据库**：HSQLDB 2.x（内置默认）、MySQL / MariaDB、PostgreSQL、Microsoft SQL Server、Oracle
- **支持 Web 容器**：
  - **Jetty 10.x**（原生支持，开箱即用，实机验证于 Jetty 10.0.26）
  - **Jetty 12.x**（启用 `ee8` 模块原生运行）
  - **Tomcat 9.x**（原生支持，开箱即用）
  - **Tomcat 10.x / 11.x**（支持通过 `webapps-javaee/` 自动转换或 `jakartaee-migration` 转档）
- **构建工具**：Apache Maven 3.9+

---

## 📜 历史版本 (Release History)

- **下一版本 (预览)**：[JTrac NG 发布说明 - 3.0.0-beta](release-3.0.0-beta_zh-CN.md)
- **前一版本**：[JTrac NG 发布说明 - 2.3.3-1.0.0](release-2.3.3-1.0.0_zh-CN.md)

---

## 授权条款 (License)

JTrac 为开源软件，遵循 [Apache Software License, Version 2.0](../../license.txt)。
