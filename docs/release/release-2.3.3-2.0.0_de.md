# JTrac NG Versionshinweise (Release Notes) - 2.3.3-2.0.0

[English](release-2.3.3-2.0.0_en.md) | [繁體中文](release-2.3.3-2.0.0_zh-TW.md) | [简体中文](release-2.3.3-2.0.0_zh-CN.md) | [日本語](release-2.3.3-2.0.0_ja.md) | [Tiếng Việt](release-2.3.3-2.0.0_vi.md) | [Deutsch](release-2.3.3-2.0.0_de.md) | [Español](release-2.3.3-2.0.0_es.md) | [Français](release-2.3.3-2.0.0_fr.md)

---

[![Java](https://img.shields.io/badge/Java-11%20%7C%2017-orange.svg)](https://adoptium.net/)
[![Maven](https://img.shields.io/badge/Maven-3.9+-blue.svg)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](../../license.txt)
[![Fork From](https://img.shields.io/badge/Fork%20From-JTrac%202.3.3-blue)](https://jtrac.info)
[![Version](https://img.shields.io/badge/Version-2.3.3--2.0.0-blue.svg)](../../pom.xml)
[![Wicket](https://img.shields.io/badge/Wicket-9.16.0-blue.svg)](https://wicket.apache.org/)
[![Spring](https://img.shields.io/badge/Spring-5.3.37-brightgreen.svg)](https://spring.io/)

Dieses Projekt basiert auf der Version [JTrac 2.3.3 (https://jtrac.info)](https://jtrac.info). Es widmet sich der Bereitstellung eines schlanken, hochkompatiblen Q&A-Textprotokollierungs- und Verfolgungssystems mit Offline-Archivierungsfunktionen und einer benutzerfreundlichen Oberfläche, das sich hervorragend für das Wissensmanagement eignet und komplexe Arbeitsabläufe durch Dateianhänge unterstützt.

---

## 🚀 Version 2.3.3-2.0.0 (Großes Architektur-Upgrade)

1. **Upgrade des Backend-Kerns (Spring 5.3 + Hibernate 5.6 + JUnit 5)**:
   - Upgrade auf Spring Framework 5.3.37; veraltetes `HibernateTemplate` und `TimerFactoryBean` entfernt.
   - Upgrade auf Hibernate ORM 5.6.15.Final mit nativer `SessionFactory`-Verwaltung und JPA-konformen Abfragen.
   - Volltextsuche auf native Lucene-API umgestellt; Abhängigkeit vom veralteten `spring-modules-lucene` entfernt.
   - Komplette Umstellung der Unit-Tests auf JUnit 5 (Jupiter).
2. **Sicherheitsarchitektur (Spring Security 5.8 + nahtlose BCrypt-Migration)**:
   - Acegi Security 1.0.7 durch Spring Security 5.8.14 ersetzt.
   - `JtracHybridPasswordEncoder`: Erkennt alte MD5-Passwort-Hashes und konvertiert sie beim erfolgreichen Benutzer-Login automatisch in sichere BCrypt-Hashes.
3. **Web-Präsentationsschicht (Apache Wicket 9.16.0)**:
   - Wicket 1.3.7 durch Wicket 9.16.0 ersetzt; Modelle und Komponenten vollständig typisiert (`IModel<T>`).
   - Kompatibel mit Servlet 4.0-Containern (Jetty 10.0.26, Jetty 12, Tomcat 9, Tomcat 10+).
4. **Paginierung für Benutzer und Projekte (Pagination & Config)**:
   - Dynamische Paginierung für `UserListPage` und `SpaceListPage` (10, 25, 50, 100, Alle).
   - Parameter `users.list.pageSize` und `spaces.list.pageSize` in der `config`-Tabelle registriert.
5. **Ajax-Fehlerbehebung bei Rollenzuweisung (Role Allocation Ajax Fix)**:
   - Event-Handler auf DOM-Standard `"change"` umgestellt und Schutzmechanismen implementiert.
6. **Globaler statischer Ressourcenfilter (StaticResourceFilter)**:
   - Behebt 404-Bildfehler bei `../resources/*` in verschachtelten URLs und ergänzt fehlende Icons.
7. **Modellbindung bei Datei-Uploads korrigiert**:
   - `FileUploadField` in `ItemFormPage` und `ItemViewFormPanel` an dedizierte `ListModel` gebunden; Laufzeit-Ausnahmen behoben.
8. **Datenbank-Upgrade & SQL-Leitfaden**:
   - Bereitstellung von [`etc/sql/upgrade-to-2.0.0.sql`](../../etc/sql/upgrade-to-2.0.0.sql) für MySQL, PostgreSQL, SQL Server, Oracle.
   - Integrierter `HsqldbDatabaseMigrator` für die automatische HSQLDB 1.8 -> 2.x Migration.
9. **Excel-Modul entfernt & WAR-Größe reduziert (Excel Module Removal & POI Deprecation)**:
   - Vollständige Entfernung des Excel-Imports/-Exports und der Apache-POI-Bibliothek; das WAR-Paket wurde um mehr als 3 MB verkleinert.
10. **Erweiterte Gesamtsystemsicherung (`jtrac-dump.sql`)**:
    - Das Sicherungs-ZIP-Archiv enthält nun eine vollständige ANSI-SQL-Dump-Datei `jtrac-dump.sql` (inklusive ANSI DDL, Dialektnotizen für MySQL/PostgreSQL/HSQLDB, fremdschlüsselgeordneten INSERT-Befehlen und Sequenz-Reset-Befehlen) für manuelle DBA-Wiederherstellungen und Datenbankmigrationen.
11. **Projekt-ID-basierte Anhangpartitionierung & Lucene-Volltextindizierung**:
    - **Partitionsstruktur nach numerischer Projekt-ID (Option C)**: Speicherung unter `${jtrac.home}/attachments/{spaceId}/{prefix}_{filename}`, vollständig immun gegen Projektumbenennungen.
    - **Dual-Read-Fallback-Sicherheitsnetz**: Automatischer Rückfall auf Stammverzeichnis und Quarantäne-Ordner (`attachments/0_ORPHAN/`), garantiert 0% 404-Fehler.
    - **Automatische Start-Migration**: Überprüfung und automatische Sortierung bestehender Anhänge in Projektunterverzeichnisse mit Fertigstellungsmarkierung (`.attachment_migrated`).
    - **Mehrformat-Textextraktion**: Unterstützung für `.xlsx`, `.docx` (reiner JDK-Streaming-OpenXML-Parser), `.pdf` (Apache PDFBox 2.0.31), `.txt`, `.csv`, `.md`, `.log` mit `SmartCharsetDetector`.
    - **Schutzgrenzen & Asynchrone Warteschlange**: Konfigurierbare Obergrenzen von 10 MB pro Datei und 50.000 Zeichen; asynchroner Hintergrund-Thread-Pool (`ExecutorService`) für sofortige Upload-Antworten.

---

## Technologien & Architektur

- **Sprache**: Java 11 / 17
- **Web-Framework**: Apache Wicket 9.16.0
- **IoC-Container**: Spring Framework 5.3.37
- **Sicherheit**: Spring Security 5.8.14 (BCrypt)
- **ORM & Persistenz**: Hibernate ORM 5.6.15.Final
- **Unterstützte Datenbanken**: HSQLDB 2.x, MySQL / MariaDB, PostgreSQL, Microsoft SQL Server, Oracle
- **Unterstützte Web-Container**:
  - **Jetty 10.x** (Nativ, verifiziert auf Jetty 10.0.26)
  - **Jetty 12.x** (Nativ über `ee8`-Modul)
  - **Tomcat 9.x** (Nativ)
  - **Tomcat 10.x / 11.x** (Über `webapps-javaee/` oder `jakartaee-migration`)
- **Build-Tool**: Apache Maven 3.9+

---

## 📜 Versionsverlauf (Release History)

- **Nächste Version (Vorschau)**: [JTrac NG Versionshinweise - 3.0.0-beta](release-3.0.0-beta_de.md)
- **Vorherige Version**: [JTrac NG Versionshinweise - 2.3.3-1.0.0](release-2.3.3-1.0.0_de.md)

---

## Lizenz

JTrac ist Open-Source-Software unter der [Apache Software License, Version 2.0](../../license.txt).
