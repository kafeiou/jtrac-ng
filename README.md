# JTrac NG

This project is derived from [JTrac 2.3.3](https://sourceforge.net/projects/j-trac/). **JTrac NG** is dedicated to providing a lightweight, modern, and AI-powered Q&A record and issue tracking system with offline static archiving capabilities, an intuitive responsive user interface, and seamless attachment support for complex workflows.

Repository: **[https://github.com/kafeiou/jtrac-ng](https://github.com/kafeiou/jtrac-ng)**

---

## Quick Start Options

### Option 1: Pre-packaged Standalone Bundle (GitHub Releases)

Pre-built standalone distributions bundling Jetty 12.x and JTrac NG are available on [GitHub Releases](https://github.com/kafeiou/jtrac-ng/releases):

1. Download the latest `jtrac-ng-3.0.0-beta.zip` from GitHub Releases.
2. Unzip the downloaded archive to your preferred directory.
3. Start the server:
   - **Windows**: Double-click or run `start.bat`
   - **Linux / macOS**: Run `./start.sh` (ensure execute permissions: `chmod +x *.sh`)
4. Stop the server:
   - **Windows**: Run `stop.bat`
   - **Linux / macOS**: Run `./stop.sh`
5. Open `http://localhost:8888/` in your browser (default credentials: `admin` / `admin`).

> [!TIP]
> The default port is `8888`. To change the HTTP port, refer to `changePortListener.html` or edit `jetty.http.port` in `start.ini`.

---

### Option 2: Run with Docker Container

A ready-to-run container image based on Eclipse Temurin 17+, Jetty 12.x, and full multilingual fonts can be built or run directly via Docker:

Run directly with Docker:
```bash
docker run -d -p 8888:8080 -v jtrac_data:/jtrac-data --name jtrac-ng jtrac-ng:latest
```

Access the application at `http://localhost:8888/`.

> [!NOTE]
> To build the Docker image locally from source code, refer to the [`docker/README.md`](docker/README.md) guide.

---

## Documentation

All detailed project specifications, technical guides, and multilingual documentation are organized in the [`docs/`](docs/) directory:

- **Release Notes (3.0.0-beta)**: [`docs/release/release-3.0.0-beta_en.md`](docs/release/release-3.0.0-beta_en.md)
- **Release Notes (2.3.3-2.0.0)**: [`docs/release/release-2.3.3-2.0.0_en.md`](docs/release/release-2.3.3-2.0.0_en.md)
- **Release Notes (2.3.3-1.0.0)**: [`docs/release/release-2.3.3-1.0.0_en.md`](docs/release/release-2.3.3-1.0.0_en.md)
- **AI Query Copilot & Prompt Guide (8 Languages)**: [`docs/llm/PROMPT_EXAMPLES_en.md`](docs/llm/PROMPT_EXAMPLES_en.md)
- **Build & Compilation Guides (8 Languages)**: [`docs/build/BUILD_en.md`](docs/build/BUILD_en.md)
- **System Administrator Guides (8 Languages)**: [`docs/admin/ADMIN_GUIDE_en.md`](docs/admin/ADMIN_GUIDE_en.md)
- **Docker Deployment & Volume Guide**: [`docker/README.md`](docker/README.md)
