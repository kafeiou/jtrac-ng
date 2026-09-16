# JTrac NG Release Notes - 2.3.3-2.0.0

[English](release-2.3.3-2.0.0_en.md) | [繁體中文](release-2.3.3-2.0.0_zh-TW.md) | [简体中文](release-2.3.3-2.0.0_zh-CN.md) | [日本語](release-2.3.3-2.0.0_ja.md) | [Tiếng Việt](release-2.3.3-2.0.0_vi.md) | [Deutsch](release-2.3.3-2.0.0_de.md) | [Español](release-2.3.3-2.0.0_es.md) | [Français](release-2.3.3-2.0.0_fr.md)

---

[![Java](https://img.shields.io/badge/Java-11%20%7C%2017-orange.svg)](https://adoptium.net/)
[![Maven](https://img.shields.io/badge/Maven-3.9+-blue.svg)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](../../license.txt)
[![Fork From](https://img.shields.io/badge/Fork%20From-JTrac%202.3.3-blue)](https://jtrac.info)
[![Version](https://img.shields.io/badge/Version-2.3.3--2.0.0-blue.svg)](../../pom.xml)
[![Wicket](https://img.shields.io/badge/Wicket-9.16.0-blue.svg)](https://wicket.apache.org/)
[![Spring](https://img.shields.io/badge/Spring-5.3.37-brightgreen.svg)](https://spring.io/)

This project is derived from [JTrac 2.3.3 (https://jtrac.info)](https://jtrac.info). It is dedicated to providing a lightweight, highly compatible Q&A text record and tracking system with offline static archiving capabilities and an intuitive user interface, making it exceptionally well-suited for knowledge management, supported by attachments for complex workflows.

---

## 🚀 Version 2.3.3-2.0.0 (Major Architecture & Core Upgrade)

1. **Backend Framework Upgrade (Spring 5.3 + Hibernate 5.6 + JUnit 5)**:
   - Upgraded to Spring Framework 5.3.37, eliminating deprecated `HibernateTemplate` and `TimerFactoryBean`.
   - Upgraded to Hibernate ORM 5.6.15.Final with native `SessionFactory` and JPA compliant queries.
   - Refactored full-text indexing to native Lucene API, decoupling from unmaintained `spring-modules-lucene`.
   - Unit tests upgraded to JUnit 5 (Jupiter).
2. **Security Overhaul (Spring Security 5.8 + Transparent BCrypt Migration)**:
   - Replaced legacy Acegi Security 1.0.7 with standard Spring Security 5.8.14.
   - Implemented `JtracHybridPasswordEncoder`: verifies legacy MD5 password hashes and seamlessly upgrades them to BCrypt upon successful user login without database disruption.
3. **Web Presentation Upgrade (Apache Wicket 9.16.0)**:
   - Migrated from Wicket 1.3.7 to Wicket 9.16.0 with full generics support across all models and components (`IModel<T>`).
   - Standardized on Servlet 4.0 containers (Jetty 10.0.26, Jetty 12, Tomcat 9, Tomcat 10+).
4. **User and Space List Pagination & Custom Settings**:
   - `UserListPage` and `SpaceListPage` now support configurable page size (10, 25, 50, 100, All).
   - Added `users.list.pageSize` and `spaces.list.pageSize` configuration keys in `config` table.
5. **Role Allocation Ajax Event Fixes**:
   - Fixed Ajax event binding from `"onChange"` to standard DOM `"change"` event, with proper selection clearing guardrails.
6. **Global Static Resource Filter (`StaticResourceFilter`)**:
   - Resolved 404 broken image issues for `../resources/*` across nested URL structures, and restored missing icons.
7. **Form FileUpload Model Binding Fix**:
   - Explicitly bound `FileUploadField` to a dedicated `ListModel` in `ItemFormPage` and `ItemViewFormPanel`, eliminating runtime property resolution exceptions.
8. **Smooth Database Upgrade & SQL Guide**:
   - Provided [`etc/sql/upgrade-to-2.0.0.sql`](../../etc/sql/upgrade-to-2.0.0.sql) for MySQL, PostgreSQL, SQL Server, and Oracle.
   - Integrated `HsqldbDatabaseMigrator` to automatically backup and migrate legacy HSQLDB 1.8 databases to 2.x on startup.
9. **Excel Module Removal & WAR Footprint Reduction**:
   - Completely removed legacy Excel import/export functionality and eliminated Apache POI dependencies, reducing the WAR package size by >3 MB.
10. **Enhanced Full-System Backup Bundle (`jtrac-dump.sql`)**:
    - Full-system backup ZIP bundle now contains a comprehensive, cross-database ANSI SQL dump file (`jtrac-dump.sql`), complete with ANSI DDL, MySQL/PostgreSQL/HSQLDB dialect notes, foreign-key ordered INSERT statements, and sequence reset hints for DBA disaster recovery and database migration.
11. **Space-Partitioned Attachments & Lucene Full-Text Indexing**:
    - **Space-Partitioned Directory Structure (Option C)**: Stored by pure numeric space ID (`attachments/{spaceId}/{filePrefix}_{fileName}`), completely immune to space code or name changes.
    - **Dual-Read Fallback**: Automated fallback to flat root and orphan quarantine directory (`attachments/0_ORPHAN/`), guaranteeing 0% 404 broken download links during and after migration.
    - **Automated Startup Migration**: Automatically scans legacy flat attachments on server startup and partitions them into space subdirectories with a completion marker (`.attachment_migrated`).
    - **Lucene Full-Text Search**: Whitelist text extraction for `.xlsx`, `.docx` (pure JDK streaming OpenXML parser), `.pdf` (Apache PDFBox 2.0.31), `.txt`, `.csv`, `.md`, `.log` with `SmartCharsetDetector` (BOM detection and scoring heuristic).
    - **Guardrails & Async Processing**: 10MB file limit and 50,000 character limit stored in `config`; async queue indexing for instant upload response; full attachment support in index rebuilds.

---

## Technologies & Architecture

- **Core Language**: Java 11 / 17
- **Web Framework**: Apache Wicket 9.16.0
- **Backend IoC**: Spring Framework 5.3.37
- **Security**: Spring Security 5.8.14 (BCrypt password hashing)
- **ORM & Persistence**: Hibernate ORM 5.6.15.Final
- **Supported Databases**: HSQLDB 2.x (embedded default), MySQL / MariaDB, PostgreSQL, Microsoft SQL Server, Oracle
- **Supported Web Containers**:
  - **Jetty 10.x** (Native support, out-of-the-box, verified on Jetty 10.0.26)
  - **Jetty 12.x** (Native support via `ee8` module)
  - **Tomcat 9.x** (Native support, out-of-the-box)
  - **Tomcat 10.x / 11.x** (Supported via `webapps-javaee/` auto-converter or `jakartaee-migration` tool)
- **Build Tool**: Apache Maven 3.9+

---

## 📜 Release History

- **Next Release (Preview)**: [JTrac NG Release Notes - 3.0.0-beta](release-3.0.0-beta_en.md)
- **Previous Release**: [JTrac NG Release Notes - 2.3.3-1.0.0](release-2.3.3-1.0.0_en.md)

---

## License

JTrac is open-source software licensed under the [Apache Software License, Version 2.0](../../license.txt).
