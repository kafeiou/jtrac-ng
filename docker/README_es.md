# Empaquetado y Despliegue con Docker de JTrac NG (Jetty 12.x + Eclipse Temurin 17+)

[English](README.md) | [繁體中文](README_zh-TW.md) | [簡體中文](README_zh-CN.md) | [日本語](README_ja.md) | [Tiếng Việt](README_vi.md) | [Deutsch](README_de.md) | [Español](README_es.md) | [Français](README_fr.md)

Este directorio proporciona un entorno nativo de construcción multietapa (Multi-stage Build) y despliegue en contenedores para JTrac NG modernizado.

---

## Características principales (Features)

- **Entorno modernizado**: Basado en la imagen oficial `jetty:12-jre17-eclipse-temurin` con módulos `ee8-deploy` y `ee8-webapp`, con soporte nativo para Servlet 4.0 (`javax.servlet`).
- **Construcción multietapa (Multi-stage Build)**: Compilación automática de `jtrac.war` desde el código fuente mediante `maven:3.9-eclipse-temurin-17`, sin requerir JDK ni Maven en el host.
- **Soporte multilingüe de fuentes completo**: Incluye `fontconfig`, `fonts-noto-cjk`, `fonts-noto-core` y `fonts-dejavu-core`, evitando caracteres faltantes o corruptos durante la indexación y generación de informes.
- **Corrección automática de permisos y ejecución segura**: En el arranque, el Entrypoint corrige la propiedad de `/jtrac-data` a `jetty:jetty` (UID 999) y desescala privilegios mediante `gosu`.

---

## Inicio Rápido (Quick Start)

### Método 1: Comando nativo de Docker (Recomendado)

Desde el directorio `docker`, ejecute la compilación utilizando la raíz del proyecto (`..`) como contexto:

```bash
cd docker
docker build -f Dockerfile -t jtrac-ng:latest -t jtrac-ng:3.0.0-beta ..
docker run -d -p 8888:8080 -v jtrac_data:/jtrac-data --name jtrac-ng jtrac-ng:latest
```

Abra `http://localhost:8888/` en su navegador (credenciales por defecto: `admin` / `admin`).

---

### Método 2: Scripts auxiliares multiplataforma

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

### Método 3: Ejecutar la imagen oficial de Docker Hub

**[https://hub.docker.com/r/inmethod/jtrac](https://hub.docker.com/r/inmethod/jtrac)**

```bash
docker run -d -p 8888:8080 -v jtrac_data:/jtrac-data --name jtrac inmethod/jtrac:latest
```

---

## Resolución de problemas: Sincronización de código y conflictos de Tag (Troubleshooting)

Si el comando `git pull` falla en su servidor de compilación o máquina de pruebas:

1. **Rechazo por sobrescritura de Tag (`would clobber existing tag`)**:
   Cuando un Tag de versión (ej. `3.0.0-beta`) se actualiza forzadamente en el repositorio remoto, Git bloquea la sobreescritura local. Añada `-f`:
   ```bash
   git pull --tags -f
   ```

2. **Reinicio rápido al estado remoto (Recomendado para máquinas de Build)**:
   Para descartar modificaciones temporales y diferencias de fin de línea, sincronizando con 100% de éxito:
   ```bash
   git fetch --tags -f && git reset --hard origin/master
   ```

3. **Configuración de alias de sincronización rápida (Git Alias)**:
   Configure el alias una sola vez para sincronizar simplemente con `git sync`:
   ```bash
   git config --global alias.sync "!git fetch --tags -f && git reset --hard origin/master"
   ```
