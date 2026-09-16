# Notas de la Versión de JTrac NG (Release Notes) - 2.3.3-2.0.0

[English](release-2.3.3-2.0.0_en.md) | [繁體中文](release-2.3.3-2.0.0_zh-TW.md) | [简体中文](release-2.3.3-2.0.0_zh-CN.md) | [日本語](release-2.3.3-2.0.0_ja.md) | [Tiếng Việt](release-2.3.3-2.0.0_vi.md) | [Deutsch](release-2.3.3-2.0.0_de.md) | [Español](release-2.3.3-2.0.0_es.md) | [Français](release-2.3.3-2.0.0_fr.md)

---

[![Java](https://img.shields.io/badge/Java-11%20%7C%2017-orange.svg)](https://adoptium.net/)
[![Maven](https://img.shields.io/badge/Maven-3.9+-blue.svg)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](../../license.txt)
[![Fork From](https://img.shields.io/badge/Fork%20From-JTrac%202.3.3-blue)](https://jtrac.info)
[![Version](https://img.shields.io/badge/Version-2.3.3--2.0.0-blue.svg)](../../pom.xml)
[![Wicket](https://img.shields.io/badge/Wicket-9.16.0-blue.svg)](https://wicket.apache.org/)
[![Spring](https://img.shields.io/badge/Spring-5.3.37-brightgreen.svg)](https://spring.io/)

Este proyecto se deriva de la versión [JTrac 2.3.3 (https://jtrac.info)](https://jtrac.info). Está dedicado a proporcionar un sistema de registro y seguimiento de texto Q&A ligero, altamente compatible, con capacidades de archivado estático fuera de línea y una interfaz de usuario intuitiva, ideal para la gestión del conocimiento y respaldado con archivos adjuntos para flujos de trabajo complejos.

---

## 🚀 Versión 2.3.3-2.0.0 (Gran Actualización Arquitectónica)

1. **Actualización del Núcleo Backend (Spring 5.3 + Hibernate 5.6 + JUnit 5)**:
   - Actualización a Spring Framework 5.3.37; eliminación de `HibernateTemplate` y `TimerFactoryBean`.
   - Migración a Hibernate ORM 5.6.15.Final con gestión nativa de `SessionFactory` y consultas JPA estándar.
   - Búsqueda de texto completo con API Lucene nativa, desacoplada de `spring-modules-lucene`.
   - Pruebas unitarias actualizadas a JUnit 5 (Jupiter).
2. **Reestructuración de Seguridad (Spring Security 5.8 + Migración Transparente a BCrypt)**:
   - Reemplazo completo de Acegi Security 1.0.7 por Spring Security 5.8.14.
   - `JtracHybridPasswordEncoder`: Valida contraseñas MD5 antiguas y las convierte automáticamente a BCrypt tras el inicio de sesión exitoso del usuario sin tiempos de inactividad.
3. **Capa Web (Apache Wicket 9.16.0)**:
   - Migración de Wicket 1.3.7 a Wicket 9.16.0 con soporte tipado completo (`IModel<T>`).
   - Compatible con contenedores Servlet 4.0 (Jetty 10.0.26, Jetty 12, Tomcat 9, Tomcat 10+).
4. **Paginación en Listas de Usuarios y Proyectos (Pagination & Config)**:
   - Soporte para paginación configurable en `UserListPage` y `SpaceListPage` (10, 25, 50, 100, Todos).
   - Incorporación de parámetros `users.list.pageSize` y `spaces.list.pageSize` en la tabla `config`.
5. **Corrección de Eventos Ajax en Asignación de Roles**:
   - Ajuste de eventos a estándar DOM `"change"` y protección contra deselección.
6. **Filtro Global de Recursos Estáticos (StaticResourceFilter)**:
   - Resuelve el problema de imágenes 404 en `../resources/*` en rutas URL anidadas y restaura iconos faltantes.
7. **Corrección de Enlace de Modelo en Carga de Archivos**:
   - Asignación explícita de `ListModel` independiente en `FileUploadField` en `ItemFormPage` y `ItemViewFormPanel`.
8. **Actualización de Base de Datos y Guía SQL**:
   - Script oficial de actualización [`etc/sql/upgrade-to-2.0.0.sql`](../../etc/sql/upgrade-to-2.0.0.sql) para MySQL, PostgreSQL, SQL Server y Oracle.
   - Herramienta integrada `HsqldbDatabaseMigrator` para migración automática de HSQLDB 1.8 a 2.x con respaldo preventivo.
9. **Eliminación del Módulo Excel y Reducción del Paquete WAR (Excel Module Removal & POI Deprecation)**:
   - Eliminación total de la importación y exportación de Excel y de la biblioteca Apache POI, reduciendo el tamaño del archivo WAR en más de 3 MB.
10. **Paquete de Copia de Seguridad Mejorado (`jtrac-dump.sql`)**:
    - El archivo ZIP de copia de seguridad completa incluye ahora un volcado SQL ANSI integral (`jtrac-dump.sql`), con DDL ANSI, notas de dialectos MySQL/PostgreSQL/HSQLDB, instrucciones INSERT ordenadas por dependencias de claves foráneas y comandos de restablecimiento de secuencias para recuperación ante desastres y migración de BD por DBA.
11. **Partición de Archivos Adjuntos e Indexación de Texto Completo con Lucene**:
    - **Estructura Particionada por ID de Proyecto (Opción C)**: Almacenamiento organizado por ID numérico puro (`attachments/{spaceId}/{prefix}_{filename}`), eliminando riesgos por cambios de nombre de proyecto.
    - **Mecanismo de Doble Lectura de Respaldo (Dual-Read Fallback)**: Respaldo automático al directorio raíz y a la carpeta de aislamiento de huérfanos (`attachments/0_ORPHAN/`), garantizando 0% de enlaces rotos (404).
    - **Migración Automática en Inicio**: Detección y migración automática de archivos adjuntos antiguos a subcarpetas de proyectos al arrancar el servidor, con marca de finalización (`.attachment_migrated`).
    - **Extracción de Texto Multiformato**: Compatible con `.xlsx`, `.docx` (analizador OpenXML streaming nativo de JDK), `.pdf` (Apache PDFBox 2.0.31), `.txt`, `.csv`, `.md`, `.log`, con detector inteligente `SmartCharsetDetector` para evitar caracteres corruptos.
    - **Límites de Seguridad y Cola Asíncrona**: Límite de 10MB por archivo y 50,000 caracteres; cola en segundo plano (`ExecutorService`) para respuestas instantáneas de carga.

---

## Tecnologías y Arquitectura

- **Lenguaje**: Java 11 / 17
- **Framework Web**: Apache Wicket 9.16.0
- **Contenedor IoC**: Spring Framework 5.3.37
- **Seguridad**: Spring Security 5.8.14 (BCrypt)
- **ORM y Persistencia**: Hibernate ORM 5.6.15.Final
- **Bases de Datos Soportadas**: HSQLDB 2.x (embebida), MySQL / MariaDB, PostgreSQL, Microsoft SQL Server, Oracle
- **Contenedores Web Soportados**:
  - **Jetty 10.x** (Nativo, verificado en Jetty 10.0.26)
  - **Jetty 12.x** (Nativo habilitando módulo `ee8`)
  - **Tomcat 9.x** (Nativo)
  - **Tomcat 10.x / 11.x** (Mediante `webapps-javaee/` o herramienta `jakartaee-migration`)
- **Herramienta de Construcción**: Apache Maven 3.9+

---

## 📜 Historial de Versiones (Release History)

- **Próxima Versión (Vista previa)**: [Notas de la Versión de JTrac NG - 3.0.0-beta](release-3.0.0-beta_es.md)
- **Versión Anterior**: [Notas de la Versión de JTrac NG - 2.3.3-1.0.0](release-2.3.3-1.0.0_es.md)

---

## Licencia

JTrac es software de código abierto bajo la [Apache Software License, Version 2.0](../../license.txt).
