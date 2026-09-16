# Guía de Compilación y Construcción de JTrac (Español)

[English](BUILD_en.md) | [繁體中文](BUILD_zh-TW.md) | [简体中文](BUILD_zh-CN.md) | [日本語](BUILD_ja.md) | [Tiếng Việt](BUILD_vi.md) | [Deutsch](BUILD_de.md) | [Español](BUILD_es.md) | [Français](BUILD_fr.md)

Esta guía detalla cómo compilar y empaquetar el proyecto JTrac NG 3.0.0-beta utilizando Apache Maven, cubriendo el ciclo de vida de Maven, la caché de dependencias, las estructuras internas de los paquetes WAR y CLI, la matriz de verificación en contenedores Web y la resolución de problemas de compilación.

---

## 1. Requisitos Previos del Entorno

Antes de comenzar la compilación, asegúrese de que su entorno cumpla con las siguientes condiciones:

- **Sistema Operativo**: Windows / Linux / macOS
- **Kit de Desarrollo de Java (JDK)**: **Java 11+ / 17+** (se recomienda JDK 17 LTS, p. ej. `W:\developer\jdk-17.0.9`, requisito mínimo Java 11+)
  > [!IMPORTANT]
  > La arquitectura central se ha modernizado a Spring 5.3.x, Hibernate 5.6.x y Apache Wicket 9.x, con bytecode orientado a **Java 11**. **JDK 8 ya no es compatible**; no intente compilar con JDK 8.
- **Apache Maven**: **Maven 3.9+** (p. ej. `W:\developer\apache-maven-3.9.9`)

### Configuración de Variables de Entorno

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

Verificación del entorno:
```bash
mvn -version
```
La salida debe mostrar correctamente Maven 3.9+ junto con Java 11 o 17.

---

## 2. Matriz de Comandos de Construcción con Maven

Ejecute los siguientes comandos en el directorio raíz del proyecto (donde se encuentra `pom.xml`):

| Comando | Fase / Acción | Descripción |
|---|---|---|
| `mvn clean compile` | Compilar Código Fuente | Limpia artefactos anteriores y compila `src/main/java` con filtrado de recursos UTF-8 |
| `mvn test-compile` | Compilar Pruebas | Compila las clases de prueba en `src/test/java` |
| `mvn test` | Ejecutar Pruebas | Ejecuta las pruebas unitarias JUnit 5 (con HSQLDB en memoria integrado, sin BD externa) |
| `mvn package` | Empaquetado Formal | Ejecuta pruebas y genera el archivo WAR final (`target/jtrac.war`) |
| `mvn package -DskipTests` | Empaquetado Rápido | Omite pruebas unitarias y genera rápidamente `target/jtrac.war` |
| `mvn clean` | Limpieza | Elimina todos los archivos temporales y cachés en `target/` |
| `mvn clean package -f tools/jtrac-exporter/pom.xml -DskipTests` | Construir Herramienta CLI | Compila la herramienta independiente de exportación de incidencias en `tools/jtrac-exporter.jar` |

---

## 3. Caché y Resolución Automática de Dependencias (`~/.m2/repository`)

JTrac se basa en la arquitectura estándar de Apache Maven. Todas las bibliotecas externas (Spring 5.3.x, Wicket 9.x, Hibernate 5.6.x, Spring Security 5.8.x, etc.) están declaradas en [`pom.xml`](../../pom.xml).

### Flujo de Descarga y Almacenamiento en Caché:
1. En la primera ejecución de `mvn compile` o `mvn package`, Maven se conecta al repositorio central (Maven Central) para resolver el árbol de dependencias.
2. Todos los archivos JAR se guardan en la caché local:
   - **Windows**: `%USERPROFILE%\.m2\repository\`
   - **Linux / macOS**: `~/.m2/repository/`
3. Las construcciones posteriores se ejecutan completamente fuera de línea utilizando esta caché. **Los desarrolladores no necesitan descargar ni configurar archivos JAR manualmente**.

---

## 4. Estructura Interna del Archivo WAR (`WEB-INF/lib/`)

Al ejecutar `mvn package`, se genera el archivo web estándar: [`target/jtrac.war`](../../target/jtrac.war).

### Distribución de Directorios Internos:
```text
jtrac.war
├── META-INF/
│   └── MANIFEST.MF
├── WEB-INF/
│   ├── classes/                 <-- Clases compiladas de JTrac y paquetes de recursos UTF-8
│   │   ├── info/jtrac/...
│   │   └── messages*.properties
│   ├── lib/                     <-- [Núcleo: Todas las dependencias JAR de terceros]
│   │   ├── spring-core-5.3.37.jar
│   │   ├── wicket-core-9.16.0.jar
│   │   ├── hibernate-core-5.6.15.Final.jar
│   │   ├── spring-security-core-5.8.14.jar
│   │   ├── hsqldb-2.7.2.jar
│   │   └── ... (todas las demás bibliotecas)
│   └── web.xml                  <-- Descriptor de configuración Servlet 4.0
└── resources/                   <-- Recursos estáticos (CSS, iconos, hojas de estilo)
```

- **Aislamiento de Classloader**: Los contenedores de servlets aíslan automáticamente el directorio `WEB-INF/lib/` de cada aplicación.
- **Despliegue Autónomo**: No se requiere instalar librerías adicionales en el servidor; basta con desplegar `jtrac.war`.

---

## 5. Matriz de Compatibilidad con Contenedores Web

JTrac NG 3.0.0-beta cumple con la especificación Servlet 4.0 (`javax.servlet`). El archivo WAR puede desplegarse directamente en contenedores modernos:

| Contenedor Web | Versiones Compatibles | Método de Despliegue |
|---|---|---|
| **Jetty 10.x** | 10.0.x (Recomendado) | **Listo para usar**: Copie `target/jtrac.war` como `webapps/ROOT.war` e inicie. |
| **Jetty 12.x** | 12.0.x (Última) | **Soporte nativo**: Active el módulo `ee8`:<br/>`java -jar start.jar --add-modules=server,http,ee8-deploy,ee8-webapp`. |
| **Tomcat 9.x** | 9.0.x (Recomendado) | **Listo para usar**: Copie `target/jtrac.war` como `webapps/ROOT.war` e inicie. |
| **Tomcat 10.x / 11.x** | 10.1.x / 11.0.x | **Migración automática**:<br/>1. **Método A**: Colocar en `webapps-javaee/` para conversión automática.<br/>2. **Método B**: Convertir con la herramienta oficial `jakartaee-migration` e instalar en `webapps/`. |

### Verificación Rápida en Jetty 10 Local:
1. Copie `target/jtrac.war` a `W:\developer\jetty-10.0.26\webapps\ROOT.war`.
2. Inicie Jetty:
   ```powershell
   & "W:\developer\jdk-17.0.9\bin\java.exe" -jar W:\developer\jetty-10.0.26\start.jar
   ```
3. Abra `http://localhost:8888/` en el navegador (credenciales: `admin` / `admin`).

---

## 6. Verificación de Artefactos y Resolución de Problemas (Troubleshooting)

### 6.1 Lista de Verificación
Tras compilar, confirme la existencia de los siguientes archivos:
- [ ] `target/jtrac.war` (tamaño aprox. 18~22 MB, optimizado tras eliminar bibliotecas POI antiguas)
- [ ] `tools/jtrac-exporter.jar` (si construyó la herramienta CLI)

### 6.2 Problemas Habituales y Soluciones

1. **Error de Codificación de Caracteres (`unmappable character for encoding`)**:
   - Causa: La página de códigos predeterminada de la consola en Windows no interpreta caracteres UTF-8.
   - Solución: Defina la codificación de Maven antes de compilar:
     ```powershell
     $env:MAVEN_OPTS = "-Dfile.encoding=UTF-8"
     ```
2. **Error de Versión de Destino del Compilador (`invalid target release: 11`)**:
   - Causa: Se está utilizando JDK 8 en la terminal.
   - Solución: Cambie `JAVA_HOME` a JDK 11 o JDK 17.
3. **Descarga de Dependencias Interrumpida**:
   - Solución: Fuerce a Maven a revalidar y descargar las dependencias:
     ```bash
     mvn clean compile -U
     ```
4. **Memoria Insuficiente (`java.lang.OutOfMemoryError`)**:
   - Solución: Asigne más memoria al proceso Maven:
     ```bash
     export MAVEN_OPTS="-Xmx1024m -XX:MaxMetaspaceSize=256m"
     ```

---

## 7. Alternativa sin Instalación Local: Construcción Multietapa con Docker

Si prefiere compilar en un entorno aislado sin instalar JDK ni Maven localmente, utilice la compilación multietapa oficial de Docker:

👉 **Consulte la guía dedicada: [`docker/README.md`](../../docker/README.md)**
