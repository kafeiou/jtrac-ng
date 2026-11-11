# Guía de compilación y construcción de JTrac NG (Español)

[English](BUILD_en.md) | [繁體中文](BUILD_zh-TW.md) | [简体中文](BUILD_zh-CN.md) | [日本語](BUILD_ja.md) | [Tiếng Việt](BUILD_vi.md) | [Deutsch](BUILD_de.md) | [Español](BUILD_es.md) | [Français](BUILD_fr.md)

Esta guía detalla las instrucciones completas para compilar y empaquetar el proyecto JTrac NG 3.0.0-beta con Apache Maven, incluyendo el ciclo de vida de Maven, gestión de caché de dependencias, estructura interna de archivos WAR y paquetes portables independientes, compatibilidad con contenedores y resolución de problemas.

---

## 1. Requisitos previos

Antes de compilar, confirme que su entorno local cumple con los siguientes requisitos:

- **Sistema operativo**: Windows / Linux / macOS
- **Kit de desarrollo Java (JDK)**: **Java 21 LTS** ([Eclipse Temurin JDK 21+](https://adoptium.net/temurin/releases/?version=21) recomendado, p. ej. `W:\developer\jdk-21.0.9`, requisito mínimo Java 21)
  > [!IMPORTANT]
  > La arquitectura central se ha actualizado por completo a **Spring 6.2.x**, **Jakarta EE 10 (Servlet 6.0)**, **Hibernate 5.6.x (Jakarta)** y **Apache Wicket 10.x**, con código de bytes de destino **Java 21**. **JDK 8 / 11 / 17 ya no son compatibles**. Utilice obligatoriamente JDK 21+.
- **Apache Maven**: **Maven 3.9+** (p. ej. `W:\developer\apache-maven-3.9.9`)

### Configuración de variables de entorno

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

Verificación del entorno:
```bash
mvn -version
```
La salida debe mostrar Maven 3.9+ y Java 21.

---

## 2. Matriz de comandos de construcción de Maven

Ejecute los siguientes comandos en el directorio raíz del proyecto (donde se encuentra `pom.xml`):

| Comando | Fase / Acción | Descripción | Artefacto generado |
|---|---|---|---|
| `mvn clean compile` | Compilar fuentes | Limpia y compila `src/main/java` con filtrado UTF-8 | `target/classes/` |
| `mvn test-compile` | Compilar pruebas | Compila las clases de prueba en `src/test/java` | `target/test-classes/` |
| `mvn test` | Ejecutar pruebas | Ejecuta 198 pruebas unitarias JUnit 5 (HSQLDB en memoria) | Informes de consola |
| `mvn package` | Empaquetado WAR estándar | Ejecuta pruebas y genera el archivo Web estándar | `target/jtrac.war` |
| `mvn package -DskipTests` | Empaquetado WAR rápido | Omite las pruebas para generar rápidamente el WAR | `target/jtrac.war` |
| `mvn package -Pportable` | Empaquetado portable completo | Ejecuta pruebas y empaqueta distribución ZIP con Jetty 12.1 EE10 | `target/jtrac-ng-3.0.0-beta-portable.zip` |
| `mvn package -Pportable -DskipTests` | Empaquetado portable rápido | Omite pruebas para empaquetado ultra rápido en ZIP | `target/jtrac-ng-3.0.0-beta-portable.zip` |
| `package-portable.bat` | Script de Windows | Doble clic en raíz; omite pruebas automáticamente | `target/jtrac-ng-3.0.0-beta-portable.zip` |
| `./package-portable.sh` | Script Linux/macOS | Script Bash para empaquetado portable rápido | `target/jtrac-ng-3.0.0-beta-portable.zip` |
| `mvn clean` | Limpieza | Elimina `target/` y archivos temporales | - |
| `mvn clean package -f tools/jtrac-exporter/pom.xml -DskipTests` | Empaquetar herramienta CLI | Compila la herramienta independiente de exportación (Fat JAR) | `tools/jtrac-exporter.jar` |

---

## 3. Mecanismo de descarga automática de dependencias (`~/.m2/repository`)

JTrac NG está basado en Apache Maven. Todas las bibliotecas de terceros están declaradas en [`pom.xml`](../../pom.xml).

---

## 4. Estructura interna del paquete WAR (`WEB-INF/lib/`)

Al ejecutar `mvn package` se produce el archivo: [`target/jtrac.war`](../../target/jtrac.war).

```text
jtrac.war
├── META-INF/
│   └── MANIFEST.MF
├── WEB-INF/
│   ├── classes/                 <-- Clases compiladas y recursos UTF-8
│   │   ├── info/jtrac/...
│   │   └── messages*.properties
│   ├── lib/                     <-- [Bibliotecas modernas Jakarta EE 10]
│   │   ├── spring-core-6.2.19.jar
│   │   ├── wicket-core-10.12.0.jar
│   │   ├── hibernate-core-5.6.15.Final.jar
│   │   ├── spring-security-core-6.5.11.jar
│   │   ├── jakarta.servlet-api-6.0.0.jar
│   │   ├── hsqldb-2.7.4.jar
│   │   └── ... (resto de dependencias)
│   └── web.xml                  <-- Descriptor Servlet 6.0
└── resources/                   <-- Recursos estáticos (CSS, logo SVG vectorial)
```

---

## 5. Matriz de compatibilidad de contenedores Web

JTrac NG 3.0.0-beta cumple con la especificación **Jakarta EE 10 / Servlet 6.0** (`jakarta.servlet.*`):

| Contenedor | Versiones compatibles | Método de despliegue |
|---|---|---|
| **Jetty 12.x** | 12.1.x+ (Recomendado) | **Soporte nativo**: Active el módulo `ee10`:<br/>`java -jar start.jar --add-modules=server,http,ee10-deploy,ee10-webapp`<br/>O ejecute directamente la versión portable (`jtrac-ng-3.0.0-beta-portable.zip`). |
| **Tomcat 10.x / 11.x** | 10.1.x / 11.0.x | **Soporte nativo**: Nativo en Jakarta EE 10. Copie `target/jtrac.war` a `webapps/ROOT.war`. |
| **Contenedor Docker** | 3.0.0-beta | **Imagen oficial**: Basada en `jetty:12.1-jre21-eclipse-temurin`. |

---

## 6. Verificación de construcción y resolución de problemas

### 6.1 Lista de verificación
- [ ] `target/jtrac.war` (aproximadamente 50~56 MB)
- [ ] `target/jtrac-ng-3.0.0-beta-portable.zip` (aproximadamente 98 MB)
- [ ] `tools/jtrac-exporter.jar` (si se compiló la herramienta CLI)

### 6.2 Problemas comunes

1. **Error de versión de compilador (`invalid target release: 21`)**:
   - Causa: El JDK activo es 17 o inferior.
   - Solución: Configure `JAVA_HOME` apuntando a JDK 21+ y verifique con `java -version`.
2. **Errores de codificación (`unmappable character for encoding`)**:
   - Solución: Establezca codificación UTF-8 en Maven:
     ```powershell
     $env:MAVEN_OPTS = "-Dfile.encoding=UTF-8"
     ```
3. **Descargas dañadas**:
   - Solución: Fuerza la actualización de dependencias:
     ```bash
     mvn clean compile -U
     ```

---

## 7. Alternativa sin instalación: Construcción multietapa con Docker

Si prefiere compilar sin instalar JDK o Maven en su equipo local:

👉 **Consulte la guía completa: [`docker/README.md`](../../docker/README.md)**
