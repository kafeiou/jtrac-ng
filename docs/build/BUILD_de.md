# Deutscher Kompilierungs- und Build-Leitfaden (JTrac)

[English](BUILD_en.md) | [繁體中文](BUILD_zh-TW.md) | [简体中文](BUILD_zh-CN.md) | [日本語](BUILD_ja.md) | [Tiếng Việt](BUILD_vi.md) | [Deutsch](BUILD_de.md) | [Español](BUILD_es.md) | [Français](BUILD_fr.md)

Dieser Leitfaden beschreibt ausführlich die Kompilierung und das Packaging des JTrac NG 3.0.0-beta-Projekts mit Apache Maven. Er behandelt den Maven-Lebenszyklus, das Abhängigkeitscaching, die WAR- und CLI-Paketstrukturen, die Web-Container-Verifikationsmatrix sowie die Fehlerbehebung beim Build.

---

## 1. Voraussetzungen

Stellen Sie vor Beginn der Kompilierung sicher, dass Ihre lokale Umgebung die folgenden Anforderungen erfüllt:

- **Betriebssystem**: Windows / Linux / macOS
- **Java Development Kit (JDK)**: **Java 11+ / 17+** (JDK 17 LTS empfohlen, z. B. `W:\developer\jdk-17.0.9`, Mindestanforderung Java 11+)
  > [!IMPORTANT]
  > Die Kernarchitektur wurde auf Spring 5.3.x, Hibernate 5.6.x und Apache Wicket 9.x aktualisiert, mit dem Bytecode-Ziel **Java 11**. **JDK 8 wird nicht mehr unterstützt**.
- **Apache Maven**: **Maven 3.9+** (z. B. `W:\developer\apache-maven-3.9.9`)

### Umgebungsvariablen einrichten

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

Umgebung prüfen:
```bash
mvn -version
```
Die Ausgabe muss Maven 3.9+ und die entsprechende Java-Version (11 oder 17) anzeigen.

---

## 2. Maven-Befehlsübersicht

Führen Sie die folgenden Befehle im Projektstammverzeichnis aus (in dem sich die Datei `pom.xml` befindet):

| Befehl | Phase / Zweck | Beschreibung |
|---|---|---|
| `mvn clean compile` | Quellcode kompilieren | Bereinigt alte Artefakte und kompiliert `src/main/java` mit UTF-8-Ressourcenfilterung |
| `mvn test-compile` | Testcode kompilieren | Kompiliert die Testklassen unter `src/test/java` |
| `mvn test` | Tests ausführen | Führt JUnit 5-Tests aus (integrierte In-Memory-HSQLDB; keine externe Datenbank erforderlich) |
| `mvn package` | Produktiv-Packaging | Führt Tests aus und schnürt das fertige Web-WAR (`target/jtrac.war`) |
| `mvn package -DskipTests` | Schnelles Packaging | Überspringt Unit-Tests und erstellt zügig `target/jtrac.war` |
| `mvn clean` | Bereinigung | Entfernt alle temporären Build-Dateien und Caches in `target/` |
| `mvn clean package -f tools/jtrac-exporter/pom.xml -DskipTests` | CLI-Tool erstellen | Kompiliert das eigenständige HTML-Export-Tool (Fat JAR) nach `tools/jtrac-exporter.jar` |

---

## 3. Automatische Abhängigkeitsverwaltung (`~/.m2/repository`)

JTrac basiert auf standardmäßigem Apache Maven. Alle Bibliotheken von Drittanbietern (Spring 5.3.x, Wicket 9.x, Hibernate 5.6.x, Spring Security 5.8.x) sind in der Stammdatei [`pom.xml`](../../pom.xml) deklariert.

### Auflösungs- und Caching-Ablauf:
1. Beim ersten `mvn compile` oder `mvn package` lädt Maven alle Abhängigkeiten automatisch aus dem Maven Central Repository herunter.
2. Alle JAR-Dateien werden im lokalen Benutzerverzeichnis zwischengespeichert:
   - **Windows**: `%USERPROFILE%\.m2\repository\`
   - **Linux / macOS**: `~/.m2/repository/`
3. Nachfolgende Builds greifen vollständig offline auf diesen lokalen Cache zu. **Entwickler müssen niemals manuell JAR-Dateien herunterladen oder konfigurieren**.

---

## 4. WAR-Paketstruktur (`WEB-INF/lib/`)

Nach dem Ausführen von `mvn package` wird im Verzeichnis `target/` die standardisierte Java-Web-Archivdatei erzeugt: [`target/jtrac.war`](../../target/jtrac.war).

### Interne Ordnerstruktur:
```text
jtrac.war
├── META-INF/
│   └── MANIFEST.MF
├── WEB-INF/
│   ├── classes/                 <-- Kompilierte Klassen und UTF-8-Ressourcen
│   │   ├── info/jtrac/...
│   │   └── messages*.properties
│   ├── lib/                     <-- [Kern: Alle modernen Drittanbieter-JARs]
│   │   ├── spring-core-5.3.37.jar
│   │   ├── wicket-core-9.16.0.jar
│   │   ├── hibernate-core-5.6.15.Final.jar
│   │   ├── spring-security-core-5.8.14.jar
│   │   ├── hsqldb-2.7.2.jar
│   │   └── ... (alle weiteren Bibliotheken)
│   └── web.xml                  <-- Servlet 4.0-Bereitstellungsdeskriptor
└── resources/                   <-- Statische Ressourcen (CSS, Icons)
```

- **Klassenlader-Isolation**: Servlet-Container isolieren den Ordner `WEB-INF/lib/` jeder Webanwendung automatisch.
- **Einfachste Bereitstellung**: Auf dem Server müssen keine Bibliotheken manuell installiert werden; das Bereitstellen der `jtrac.war` genügt.

---

## 5. Web-Container-Kompatibilitätsmatrix

JTrac NG 3.0.0-beta entspricht der Servlet 4.0-Spezifikation (`javax.servlet`). Das erstellte WAR kann direkt auf modernen Web-Containern bereitgestellt werden:

| Web-Container | Unterstützte Versionen | Bereitstellungsmethode |
|---|---|---|
| **Jetty 10.x** | 10.0.x (Empfohlen) | **Sofort einsatzbereit**: `target/jtrac.war` als `webapps/ROOT.war` kopieren und starten. |
| **Jetty 12.x** | 12.0.x (Neueste) | **Native Unterstützung**: Modul `ee8` aktivieren:<br/>`java -jar start.jar --add-modules=server,http,ee8-deploy,ee8-webapp`. |
| **Tomcat 9.x** | 9.0.x (Empfohlen) | **Sofort einsatzbereit**: `target/jtrac.war` als `webapps/ROOT.war` kopieren und starten. |
| **Tomcat 10.x / 11.x** | 10.1.x / 11.0.x | **Automatische Migration**:<br/>1. **Methode A**: In `webapps-javaee/` ablegen zur automatischen Laufzeitkonvertierung.<br/>2. **Methode B**: Offizielles `jakartaee-migration`-Tool nutzen und `jtrac-jakarta.war` in `webapps/` ablegen. |

### Lokales Startbeispiel mit Jetty 10:
1. `target/jtrac.war` nach `W:\developer\jetty-10.0.26\webapps\ROOT.war` kopieren.
2. Jetty starten:
   ```powershell
   & "W:\developer\jdk-17.0.9\bin\java.exe" -jar W:\developer\jetty-10.0.26\start.jar
   ```
3. Im Browser öffnen: `http://localhost:8888/` (Standard-Login: `admin` / `admin`).

---

## 6. Build-Verifizierung & Fehlerbehebung (Troubleshooting)

### 6.1 Checkliste nach dem Build
Überprüfen Sie, ob folgende Artefakte vorhanden sind:
- [ ] `target/jtrac.war` (Dateigröße ca. 18~22 MB, deutlich verkleinert durch Entfernung veralteter POI-Bibliotheken)
- [ ] `tools/jtrac-exporter.jar` (falls das CLI-Tool gebaut wurde)

### 6.2 Häufige Fehlerursachen und Lösungen

1. **Zeichenkodierungsfehler (`unmappable character for encoding`)**:
   - Ursache: Die Windows-Standardzeichentabelle kann UTF-8-Dateien fehlerhaft einlesen.
   - Lösung: Vor der Maven-Ausführung Umgebungsvariable setzen:
     ```powershell
     $env:MAVEN_OPTS = "-Dfile.encoding=UTF-8"
     ```
2. **Ungültige Zielversion des Compilers (`invalid target release: 11`)**:
   - Ursache: Im Terminal ist JDK 8 aktiv.
   - Lösung: Wechseln Sie zu JDK 11 oder JDK 17 und prüfen Sie mit `java -version`.
3. **Unvollständige Abhängigkeiten im Cache**:
   - Lösung: Aktualisierung der Abhängigkeiten erzwingen:
     ```bash
     mvn clean compile -U
     ```
4. **Speichermangel (`java.lang.OutOfMemoryError`)**:
   - Lösung: Weisen Sie dem Maven-Prozess mehr Heap-Speicher zu:
     ```bash
     export MAVEN_OPTS="-Xmx1024m -XX:MaxMetaspaceSize=256m"
     ```

---

## 7. Installationsfreie Alternative: Docker Multi-Stage Build

Wenn Sie ohne lokale Installation von JDK oder Maven kompilieren möchten, bietet das Projekt ein standardisiertes Docker-Multi-Stage-Build-Verfahren:

👉 **Ausführliche Anleitung siehe: [`docker/README.md`](../../docker/README.md)**
