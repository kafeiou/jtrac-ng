# JTrac NG Docker Packaging & Deployment (Jetty 12.x + Eclipse Temurin 17+)

[English](README.md) | [繁體中文](README_zh-TW.md) | [簡體中文](README_zh-CN.md) | [日本語](README_ja.md) | [Tiếng Việt](README_vi.md) | [Deutsch](README_de.md) | [Español](README_es.md) | [Français](README_fr.md)

This directory provides native multi-stage Docker packaging and containerized runtime deployment environments for modernized JTrac NG.

---

## Features

- **Modernized Runtime Environment**: Based on official `jetty:12-jre17-eclipse-temurin` with `--add-modules=ee8-deploy,ee8-webapp`, natively supporting Servlet 4.0 (`javax.servlet`).
- **Multi-stage Build**: Automatically compiles `jtrac.war` from source using `maven:3.9-eclipse-temurin-17` with `-DskipTests`, requiring no local JDK or Maven installation.
- **Full Multilingual Font Support**: Pre-installed `fontconfig`, `fonts-noto-cjk` (CJK characters), `fonts-noto-core` (Vietnamese and diacritics), and `fonts-dejavu-core` (European accents), preventing missing glyphs (tofu) and garbled characters during PDF/Office full-text indexing and report generation.
- **Dynamic Volume Permission Fix & Secure Step-down**: The entrypoint runs as root on boot to automatically repair `/jtrac-data` ownership to `jetty:jetty` (UID 999), and then steps down using `gosu` to run Jetty securely without manual host `chown`.

---

## Quick Start

### Method 1: Native Docker Command (Recommended)

From the `docker/` directory, build the image using the project root (`..`) as the build context:

```bash
cd docker
docker build -f Dockerfile -t jtrac-ng:latest -t jtrac-ng:3.0.0-beta ..
docker run -d -p 8888:8080 -v jtrac_data:/jtrac-data --name jtrac-ng jtrac-ng:latest
```

Open `http://localhost:8888/` in your browser (default credentials: `admin` / `admin`).

---

### Method 2: Cross-Platform Helper Scripts

Convenient helper scripts are provided in this directory:

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

### Method 3: Run Pre-built Image from Docker Hub

You can also run the official pre-built image directly from Docker Hub:
**[https://hub.docker.com/r/kafeiou/jtrac-ng](https://hub.docker.com/r/kafeiou/jtrac-ng)**

```bash
docker run -d -p 8888:8080 -v jtrac_data:/jtrac-data --name jtrac-ng kafeiou/jtrac-ng:latest
```

---

## Troubleshooting: Build Environment Code Sync & Tag Conflicts

If `git pull` fails on your dedicated Docker build machine or test server, use the following quick solutions:

1. **Tag Overwrite Rejection (`would clobber existing tag`)**:
   When a release tag (such as `3.0.0-beta`) is force-updated on the remote repository, Git protects existing local tags by default. Force update local tags with `-f`:
   ```bash
   git pull --tags -f
   ```

2. **Quick Reset to Remote (Recommended for Build Machines)**:
   Build machines do not need to preserve untracked build diffs or CRLF/LF line ending changes. The cleanest way to sync with 100% success is:
   ```bash
   git fetch --tags -f && git reset --hard origin/master
   ```

3. **Configure One-Click Sync Alias**:
   Set up a global Git shortcut once, and simply run `git sync` anytime to cleanly sync remote master and tags:
   ```bash
   git config --global alias.sync "!git fetch --tags -f && git reset --hard origin/master"
   ```
