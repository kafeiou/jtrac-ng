# JTrac NG

This project is derived from [JTrac 2.3.3](https://sourceforge.net/projects/j-trac/). **JTrac NG** is dedicated to providing a lightweight, modern, and AI-powered Q&A record and issue tracking system with offline static archiving capabilities, an intuitive responsive user interface, and seamless attachment support for complex workflows.

Repository: **[https://github.com/kafeiou/jtrac-ng](https://github.com/kafeiou/jtrac-ng)**

---

## Quick Start Options

### Option 1: Standalone Portable Bundle (Zero-Installation)

A pre-configured, self-contained standalone portable distribution bundling Jetty 12.1 (EE10) and JTrac NG is available on [GitHub Releases](https://github.com/kafeiou/jtrac-ng/releases) or can be built locally:

1. Download or build `jtrac-ng-3.0.0-beta-portable.zip`.
2. Unzip the archive to any directory (requires Java 21+).
3. Start the server:
   - **Windows**: Double-click `start.bat` (automatically launches browser at `http://localhost:8080/`).
     * To use a custom port, run from command prompt: `start.bat 8888`
   - **Linux / macOS**: Run `./start.sh` (or `./start.sh 8888`)
4. Stop the server:
   - **Windows**: Run `stop.bat` or press `Ctrl+C`.
   - **Linux / macOS**: Run `./stop.sh` or press `Ctrl+C`.
5. Access the application at `http://localhost:8080/` (default credentials: `admin` / `admin`).

> [!TIP]
> All application data (HSQLDB database, attachments, Lucene search indexes) is stored in the local `./data` folder with zero system residue.
> To build the portable package from source:
> ```bash
> mvn package -Pportable
> # or run: package-portable.bat (Windows) / ./package-portable.sh (Linux/macOS)
> ```

---

### Option 2: Run with Docker Container

A ready-to-run container image based on Eclipse Temurin 21+, Jetty 12.1 (EE10), and full multilingual fonts can be built or run directly via Docker:

Run directly with Docker:
```bash
docker run -d -p 8888:8080 -v jtrac_data:/jtrac-data --name jtrac-ng jtrac-ng:latest
```

Access the application at `http://localhost:8888/`.

> [!NOTE]
> To build the Docker image locally from source code, refer to the [`docker/README.md`](docker/README.md) guide.

---

## Building from Source

### Prerequisites
- **JDK 21 LTS** (recommended: [Eclipse Temurin 21+](https://adoptium.net/temurin/releases/?version=21))
- **Apache Maven 3.9+**

### Compilation & Packaging Commands

| Target | Command | Output Artifact |
|---|---|---|
| **Standard WAR** | `mvn clean package -DskipTests` | `target/jtrac.war` |
| **Portable Standalone ZIP** | `mvn package -Pportable -DskipTests` | `target/jtrac-ng-3.0.0-beta-portable.zip` |
| **Run Full Test Suite** | `mvn test` | 198 JUnit 5 tests |
| **Fast Portable Script (Windows)** | `package-portable.bat` | Standalone ZIP & unzipped folder |
| **Fast Portable Script (Linux/macOS)** | `./package-portable.sh` | Standalone ZIP & unzipped folder |

> [!NOTE]
> For complete build details, IDE setup, and troubleshooting, refer to the [Build & Compilation Guides (8 Languages)](docs/build/BUILD_en.md).

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
