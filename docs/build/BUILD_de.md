# JTrac NG Build- & Kompilierungsleitfaden (Deutsch)

[English](BUILD_en.md) | [繁體中文](BUILD_zh-TW.md) | [简体中文](BUILD_zh-CN.md) | [日本語](BUILD_ja.md) | [Tiếng Việt](BUILD_vi.md) | [Deutsch](BUILD_de.md) | [Español](BUILD_es.md) | [Français](BUILD_fr.md)

Dieser Leitfaden beschreibt die Kompilierung und Paketierung des JTrac NG 3.0.0-beta Projekts mit Apache Maven, einschließlich Maven-Lebenszyklus, Abhängigkeits-Caching, interner Struktur von WAR- und eigenständigen Portable-Archiven sowie Web-Container-Bereitstellung und Fehlerbehebung.

---

## 1. Voraussetzungen

Stellen Sie vor dem Kompilieren sicher, dass Ihre lokale Umgebung folgende Anforderungen erfüllt:

- **Betriebssystem**: Windows / Linux / macOS
- **Java Development Kit (JDK)**: **Java 21 LTS** ([Eclipse Temurin JDK 21+](https://adoptium.net/temurin/releases/?version=21) empfohlen, z. B. `W:\developer\jdk-21.0.9`, Mindestanforderung Java 21)
  > [!IMPORTANT]
  > Die Kernarchitektur wurde vollständig auf **Spring 6.2.x**, **Jakarta EE 10 (Servlet 6.0)**, **Hibernate 5.6.x (Jakarta)** und **Apache Wicket 10.x** aktualisiert (Bytecode-Ziel **Java 21**). **JDK 8 / 11 / 17 wird nicht mehr unterstützt**. Verwenden Sie zwingend JDK 21+.
- **Apache Maven**: **Maven 3.9+** (z. B. `W:\developer\apache-maven-3.9.9`)

### Umgebungsvariablen-Konfiguration

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

Umgebungsprüfung:
```bash
mvn -version
```
Die Ausgabe muss Maven 3.9+ und Java 21 anzeigen.

---

## 2. Maven-Befehlsmatrix

Führen Sie die folgenden Befehle im Projektstammverzeichnis (Verzeichnis mit `pom.xml`) aus:

| Befehl | Phase / Aktion | Beschreibung | Ausgabedatei |
|---|---|---|---|
| `mvn clean compile` | Quellcode kompilieren | Bereinigt das Build-Verzeichnis und kompiliert `src/main/java` mit UTF-8-Filterung | `target/classes/` |
| `mvn test-compile` | Tests kompilieren | Kompiliert alle Testklassen in `src/test/java` | `target/test-classes/` |
| `mvn test` | Tests ausführen | Führt 198 JUnit-5-Tests aus (integrierte In-Memory-HSQLDB) | Konsolen-Testberichte |
| `mvn package` | Standard-WAR-Paketierung | Führt Tests aus und baut die Standard-WAR-Webanwendung | `target/jtrac.war` |
| `mvn package -DskipTests` | Schnelle WAR-Paketierung | Überspringt Tests für schnellen WAR-Build | `target/jtrac.war` |
| `mvn package -Pportable` | Vollständiger Portable-Build | Führt Tests aus und baut die eigenständige ZIP-Distribution mit Jetty 12.1 EE10 | `target/jtrac-ng-3.0.0-beta-portable.zip` |
| `mvn package -Pportable -DskipTests` | Schneller Portable-Build | Überspringt Tests für ultraschnellen Portable-ZIP-Build | `target/jtrac-ng-3.0.0-beta-portable.zip` |
| `package-portable.bat` | Windows-Skript-Build | Doppelklick im Stammverzeichnis; überspringt Tests automatisch | `target/jtrac-ng-3.0.0-beta-portable.zip` |
| `./package-portable.sh` | Linux/macOS-Skript-Build | Bash-Skript für schnellen Portable-Build | `target/jtrac-ng-3.0.0-beta-portable.zip` |
| `mvn clean` | Bereinigung | Entfernt `target/` und alle temporären Build-Dateien | - |
| `mvn clean package -f tools/jtrac-exporter/pom.xml -DskipTests` | CLI-Tool paketieren | Baut das eigenständige Fat-JAR-Export-Tool | `tools/jtrac-exporter.jar` |

---

## 3. Automatische Abhängigkeitsverwaltung (`~/.m2/repository`)

JTrac NG basiert auf Apache Maven. Alle Drittanbieter-Bibliotheken (Spring 6.2.x, Wicket 10.x, Hibernate 5.6.x Jakarta, Spring Security 6.5.x, Jetty 12.1.x etc.) sind in der zentralen [`pom.xml`](../../pom.xml) deklariert.

### Auflösungs- und Cache-Ablauf:
1. Beim ersten Aufruf lädt Maven die erforderlichen Abhängigkeiten aus dem Maven Central Repository.
2. Alle JARs werden im lokalen Cache gespeichert:
   - **Windows**: `%USERPROFILE%\.m2\repository\`
   - **Linux / macOS**: `~/.m2/repository/`
3. Nachfolgende Builds greifen offline direkt auf diesen Cache zu. **Entwickler müssen keine JAR-Dateien manuell herunterladen oder konfigurieren**.

---

## 4. WAR-Paketstruktur (`WEB-INF/lib/`)

Die Ausführung von `mvn package` erzeugt das produktionsreife Web-Archiv: [`target/jtrac.war`](../../target/jtrac.war).

### Interne Struktur:
```text
jtrac.war
├── META-INF/
│   └── MANIFEST.MF
├── WEB-INF/
│   ├── classes/                 <-- Kompilierte Klassen und UTF-8-Ressourcen
│   │   ├── info/jtrac/...
│   │   └── messages*.properties
│   ├── lib/                     <-- [Moderne Jakarta EE 10 JAR-Abhängigkeiten]
│   │   ├── spring-core-6.2.19.jar
│   │   ├── wicket-core-10.12.0.jar
│   │   ├── hibernate-core-5.6.15.Final.jar
│   │   ├── spring-security-core-6.5.11.jar
│   │   ├── jakarta.servlet-api-6.0.0.jar
│   │   ├── hsqldb-2.7.4.jar
│   │   └── ... (weitere Bibliotheken)
│   └── web.xml                  <-- Servlet 6.0 Konfigurationsdeskriptor
└── resources/                   <-- Statische Webinhalte (CSS, Vektor-SVG-Logo)
```

---

## 5. Web-Container-Kompatibilitätsmatrix

JTrac NG 3.0.0-beta implementiert die **Jakarta EE 10 / Servlet 6.0**-Spezifikation (`jakarta.servlet.*`):

| Container | Unterstützte Versionen | Bereitstellungsmethode |
|---|---|---|
| **Jetty 12.x** | 12.1.x+ (Empfohlen) | **Native Unterstützung**: Aktivieren Sie das `ee10`-Modul:<br/>`java -jar start.jar --add-modules=server,http,ee10-deploy,ee10-webapp`<br/>Oder nutzen Sie das fertige Portable-Paket (`jtrac-ng-3.0.0-beta-portable.zip`). |
| **Tomcat 10.x / 11.x** | 10.1.x / 11.0.x | **Native Unterstützung**: Entwickelt für Jakarta EE 10. Kopieren Sie `target/jtrac.war` als `webapps/ROOT.war`. |
| **Docker-Container** | 3.0.0-beta | **Offizielles Image**: Basiert auf `jetty:12-jdk21-eclipse-temurin` für sofortige Container-Bereitstellung. |

---

## 6. Build-Verifizierung & Fehlerbehebung

### 6.1 Checkliste
- [ ] `target/jtrac.war` (ca. 50~56 MB mit vollständigen Jakarta EE 10-Bibliotheken)
- [ ] `target/jtrac-ng-3.0.0-beta-portable.zip` (ca. 98 MB, falls Portable-Build gewählt wurde)
- [ ] `tools/jtrac-exporter.jar` (falls CLI-Tool gebaut wurde)

### 6.2 Häufige Fehler

1. **Compiler-Versionsfehler (`invalid target release: 21`)**:
   - Ursache: Die im Terminal aktive JDK-Version ist 17 oder älter.
   - Lösung: Setzen Sie `JAVA_HOME` auf JDK 21+ und prüfen Sie dies mit `java -version`.
2. **Zeichenkodierungsfehler (`unmappable character for encoding`)**:
   - Lösung: Vorab globale Maven-Kodierung setzen:
     ```powershell
     $env:MAVEN_OPTS = "-Dfile.encoding=UTF-8"
     ```
3. **Beschädigte Abhängigkeiten**:
   - Lösung: Erzwingen Sie die Neuüberprüfung:
     ```bash
     mvn clean compile -U
     ```
4. **Speichermangel (`OutOfMemoryError`)**:
   - Lösung: Maven mehr Speicher zuweisen:
     ```bash
     export MAVEN_OPTS="-Xmx2048m -XX:MaxMetaspaceSize=512m"
     ```

---

## 7. Alternative: Docker Multi-Stage Build

Wenn Sie es vorziehen, ohne lokale JDK- oder Maven-Installation zu bauen:

👉 **Siehe vollständigen Leitfaden: [`docker/README.md`](../../docker/README.md)**
