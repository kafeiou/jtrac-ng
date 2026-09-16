# JTrac NG Docker 容器化构建与部署 (Jetty 12.x + Eclipse Temurin 17+)

[English](README.md) | [繁體中文](README_zh-TW.md) | [簡體中文](README_zh-CN.md) | [日本語](README_ja.md) | [Tiếng Việt](README_vi.md) | [Deutsch](README_de.md) | [Español](README_es.md) | [Français](README_fr.md)

本目录提供 JTrac NG 现代化版本之原生 Docker 多阶段构建与容器化运行环境。

---

## 特性亮点 (Features)

- **现代化基础环境**：采用官方 `jetty:12-jre17-eclipse-temurin` 搭配 `ee8-deploy` 与 `ee8-webapp` 模块，原生支持 Servlet 4.0 (`javax.servlet`)。
- **多阶段构建 (Multi-stage Build)**：使用 `maven:3.9-eclipse-temurin-17` 自动从源代码编译 `jtrac.war`，无需在主机预先安装 JDK 或 Maven。
- **完整多国语言字体支持**：内置 `fontconfig`、`fonts-noto-cjk`（中日韩）、`fonts-noto-core`（越南语等音标字符）、`fonts-dejavu-core`（欧系重音字符），确保全文检索字符抽取与报表绘图 0% 缺字或乱码。
- **权限自动校正与安全降权**：Entrypoint 启动时自动检查并修复挂载卷 `/jtrac-data` 为 `jetty:jetty`，并通过 `gosu` 降权至非 root 账号（UID 999）运行，安全且免手动 chown。

---

## 快速上手 (Quick Start)

### 方式一：原生 Docker 命令 (推荐)

进入 `docker` 目录，以项目根目录（`..`）作为构建上下文进行构建：

```bash
cd docker
docker build -f Dockerfile -t jtrac-ng:latest -t jtrac-ng:3.0.0-beta ..
docker run -d -p 8888:8080 -v jtrac_data:/jtrac-data --name jtrac-ng jtrac-ng:latest
```

服务启动后，使用浏览器打开：`http://localhost:8888/`
默认管理员帐密为：`admin` / `admin`

---

### 方式二：跨平台辅助脚本

本目录提供针对不同操作系统之一键脚本：

- **Windows**：
  ```cmd
  cd docker
  build.bat
  run.bat
  ```

- **Linux / macOS**：
  ```bash
  cd docker
  chmod +x *.sh
  ./build.sh
  ./run.sh
  ```

---

### 方式三：直接运行 Docker Hub 官方镜像

亦可直接拉取并运行已发布于 Docker Hub 之官方镜像：
**[https://hub.docker.com/r/inmethod/jtrac](https://hub.docker.com/r/inmethod/jtrac)**

```bash
docker run -d -p 8888:8080 -v jtrac_data:/jtrac-data --name jtrac inmethod/jtrac:latest
```

---

## 常见问题：编译环境代码同步与 Tag 冲突排除 (Troubleshooting)

若您在专用的 Docker 编译机器或测试服务器上执行 `git pull` 遇到失败，常见原因与排除命令如下：

1. **Tag 遭到远端覆盖导致报错 (`would clobber existing tag`)**：
   当版本 Tag（如 `3.0.0-beta`）在远端重新指定到新 Commit 时，Git 基于保护机制会拒绝自动覆盖本地旧 Tag。请加上 `-f` 强制拉取：
   ```bash
   git pull --tags -f
   ```

2. **编译机器快速重置对齐远端（抛弃本地暂存与冲突，推荐）**：
   编译机若产生了未提交的暂存修改或换行符差异，最干净且 100% 成功的一键同步方式为：
   ```bash
   git fetch --tags -f && git reset --hard origin/master
   ```

3. **设置一键同步 Git 别名 (Git Alias)**：
   在该机器上执行一次配置，日后只要输入 `git sync` 即可自动排除一切 Tag 与暂存冲突完成同步：
   ```bash
   git config --global alias.sync "!git fetch --tags -f && git reset --hard origin/master"
   ```
