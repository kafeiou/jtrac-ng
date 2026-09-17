# JTrac NG Docker-Paketierung & Bereitstellung (Jetty 12.x + Eclipse Temurin 17+)

[English](README.md) | [繁體中文](README_zh-TW.md) | [簡體中文](README_zh-CN.md) | [日本語](README_ja.md) | [Tiếng Việt](README_vi.md) | [Deutsch](README_de.md) | [Español](README_es.md) | [Français](README_fr.md)

Dieses Verzeichnis enthält native Docker-Multi-Stage-Build-Konfigurationen und containerisierte Laufzeitumgebungen für das modernisierte JTrac NG.

---

## Hauptmerkmale (Features)

- **Modernisierte Laufzeitumgebung**: Basiert auf offiziellem `jetty:12-jre17-eclipse-temurin` mit `--add-modules=ee8-deploy,ee8-webapp` und nativer Servlet-4.0-Unterstützung (`javax.servlet`).
- **Mehrstufiger Build (Multi-Stage Build)**: Automatische Kompilierung von `jtrac.war` aus dem Quellcode mittels `maven:3.9-eclipse-temurin-17`, ohne lokale Installation von JDK oder Maven.
- **Vollständige Schriftartenunterstützung**: Enthält `fontconfig`, `fonts-noto-cjk`, `fonts-noto-core` und `fonts-dejavu-core` zur Vermeidung von Darstellungsfehlern bei PDF-Indizierung und Diagrammerstellung.
- **Automatische Rechtekorrektur & sichere Ausführung**: Beim Start werden die Berechtigungen des Volumes `/jtrac-data` automatisch auf `jetty:jetty` (UID 999) angepasst und der Prozess mittels `gosu` sicher als Nicht-Root-Benutzer ausgeführt.

---

## Schnellstart (Quick Start)

### Methode 1: Native Docker-Befehle (Empfohlen)

Wechseln Sie in das Verzeichnis `docker` und führen Sie den Build mit dem Projektstammverzeichnis (`..`) als Kontext aus:

```bash
cd docker
docker build -f Dockerfile -t jtrac-ng:latest -t jtrac-ng:3.0.0-beta ..
docker run -d -p 8888:8080 -v jtrac_data:/jtrac-data --name jtrac-ng jtrac-ng:latest
```

Öffnen Sie `http://localhost:8888/` im Browser (Standardzugangsdaten: `admin` / `admin`).

---

### Methode 2: Plattformübergreifende Hilfsskripte

- **Windows**:
  ```cmd
  cd docker
  build.bat
  run.bat
  ```

- **Linux / macOS**:
  ```bash
  cd docker
  chmod +x *.sh
  ./build.sh
  ./run.sh
  ```

---

### Methode 3: Offizielles Docker-Hub-Image ausführen

**[https://hub.docker.com/r/kafeiou/jtrac-ng](https://hub.docker.com/r/kafeiou/jtrac-ng)**

```bash
docker run -d -p 8888:8080 -v jtrac_data:/jtrac-data --name jtrac-ng kafeiou/jtrac-ng:latest
```

---

## Fehlerbehebung: Quellcode-Synchronisierung & Tag-Konflikte auf Build-Maschinen (Troubleshooting)

Wenn `git pull` auf einer dedizierten Build- oder Testmaschine fehlschlägt, nutzen Sie folgende Befehle:

1. **Tag-Überschreibungsfehler (`would clobber existing tag`)**:
   Wurde ein Versions-Tag (z. B. `3.0.0-beta`) im Remote-Repository überschrieben, verweigert Git standardmäßig das Überschreiben. Verwenden Sie `-f`:
   ```bash
   git pull --tags -f
   ```

2. **Schnell-Reset auf Remote-Zustand (Empfohlen für Build-Maschinen)**:
   Um alle lokalen temporären Änderungen und Zeilenumbruchdifferenzen zu verwerfen und sauber zu synchronisieren:
   ```bash
   git fetch --tags -f && git reset --hard origin/master
   ```

3. **Ein-Klick-Synchronisierungsalias konfigurieren (Git Alias)**:
   Einmalig konfigurieren, um zukünftig mit `git sync` sauber zu synchronisieren:
   ```bash
   git config --global alias.sync "!git fetch --tags -f && git reset --hard origin/master"
   ```
