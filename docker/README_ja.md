# JTrac NG Docker パッケージングとデプロイ (Jetty 12.x + Eclipse Temurin 17+)

[English](README.md) | [繁體中文](README_zh-TW.md) | [簡體中文](README_zh-CN.md) | [日本語](README_ja.md) | [Tiếng Việt](README_vi.md) | [Deutsch](README_de.md) | [Español](README_es.md) | [Français](README_fr.md)

本ディレクトリは、近代化された JTrac NG のネイティブ Docker マルチステージビルドおよびコンテナ実行環境を提供します。

---

## 主な機能 (Features)

- **近代化された実行環境**：公式 `jetty:12-jre17-eclipse-temurin` をベースとし、`--add-modules=ee8-deploy,ee8-webapp` により Servlet 4.0 (`javax.servlet`) をネイティブサポート。
- **マルチステージビルド**：`maven:3.9-eclipse-temurin-17` を用いてソースコードから `jtrac.war` を自動コンパイル。ローカル環境への JDK や Maven のインストールは不要です。
- **多言語フォントの完全サポート**：`fontconfig`、`fonts-noto-cjk`（日中韓）、`fonts-noto-core`（ベトナム語等の記号）、`fonts-dejavu-core`（欧州言語アクセント）を標準装備し、文字化けや豆腐化（□）を防止します。
- **権限自動修復とセキュア実行**：起動時にマウントボリューム `/jtrac-data` の所有権を `jetty:jetty` (UID 999) に自動修復し、`gosu` により非 root ユーザーで安全に実行します。

---

## クイックスタート (Quick Start)

### 方法 1: ネイティブ Docker コマンド (推奨)

`docker` ディレクトリに移動し、プロジェクトルート（`..`）をビルドコンテキストとして実行します：

```bash
cd docker
docker build -f Dockerfile -t jtrac-ng:latest -t jtrac-ng:3.0.0-beta ..
docker run -d -p 8888:8080 -v jtrac_data:/jtrac-data --name jtrac-ng jtrac-ng:latest
```

ブラウザで `http://localhost:8888/` にアクセス（初期管理者: `admin` / `admin`）。

---

### 方法 2: クロスプラットフォーム補助スクリプト

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

### 方法 3: Docker Hub 公式イメージの実行

**[https://hub.docker.com/r/inmethod/jtrac](https://hub.docker.com/r/inmethod/jtrac)**

```bash
docker run -d -p 8888:8080 -v jtrac_data:/jtrac-data --name jtrac inmethod/jtrac:latest
```

---

## トラブルシューティング：ビルド環境のコード同期と Tag 競合の解消

Docker ビルドマシンやテスト環境で `git pull` が失敗する場合の解決策：

1. **Tag 上書き拒否エラー (`would clobber existing tag`)**：
   リモート側でバージョン Tag（例: `3.0.0-beta`）が強制更新された場合、Git の保護機能により更新が拒否されます。`-f` を付けて強制更新します：
   ```bash
   git pull --tags -f
   ```

2. **ビルドマシンの高速リモート同期（推奨）**：
   ビルド機の一時ファイルや改行コード差分を破棄し、100% 確実に最新状態へリセットします：
   ```bash
   git fetch --tags -f && git reset --hard origin/master
   ```

3. **ワンクリック同期エイリアスの登録 (Git Alias)**：
   以下の設定を行うことで、次回以降は `git sync` と入力するだけで完全同期が可能です：
   ```bash
   git config --global alias.sync "!git fetch --tags -f && git reset --hard origin/master"
   ```
