# JTrac ビルド・コンパイル詳細ガイド (日本語)

[English](BUILD_en.md) | [繁體中文](BUILD_zh-TW.md) | [简体中文](BUILD_zh-CN.md) | [日本語](BUILD_ja.md) | [Tiếng Việt](BUILD_vi.md) | [Deutsch](BUILD_de.md) | [Español](BUILD_es.md) | [Français](BUILD_fr.md)

本ガイドでは、Apache Maven を使用した JTrac NG 3.0.0-beta のコンパイルおよびパッケージング手順、Maven ライフサイクル、依存関係キャッシュ機構、WAR および CLI の内部構造、Web コンテナ適合マトリクス、トラブルシューティングについて詳細に解説します。

---

## 1. 前提環境要件

コンパイルを開始する前に、ローカル環境が以下の要件を満たしていることを確認してください：

- **OS**：Windows / Linux / macOS
- **JDK (Java Development Kit)**：**Java 11+ / 17+**（JDK 17 LTS 推奨。例: `W:\developer\jdk-17.0.9`、最低要件は Java 11+）
  > [!IMPORTANT]
  > 本バージョンは Spring 5.3.x、Hibernate 5.6.x、Apache Wicket 9.x へ刷新され、ターゲットバイトコードは **Java 11** です。**JDK 8 はサポート対象外**となりましたのでご注意ください。
- **Apache Maven**：**Maven 3.9+**（例: `W:\developer\apache-maven-3.9.9`）

### 環境変数の設定例

- **Windows (PowerShell)**：
  ```powershell
  $env:JAVA_HOME = "W:\developer\jdk-17.0.9"
  $env:PATH = "W:\developer\apache-maven-3.9.9\bin;$env:PATH"
  ```
- **Windows (CMD)**：
  ```cmd
  set "JAVA_HOME=W:\developer\jdk-17.0.9"
  set "PATH=W:\developer\apache-maven-3.9.9\bin;%PATH%"
  ```
- **Linux / macOS (Bash/Zsh)**：
  ```bash
  export JAVA_HOME="/usr/lib/jvm/java-17-openjdk"
  export PATH="$JAVA_HOME/bin:$PATH"
  ```

環境の検証：
```bash
mvn -version
```
Maven 3.9+ および Java 11 または 17 のバージョン情報が正しく出力されることを確認します。

---

## 2. Maven ビルドコマンド一覧

プロジェクトのルートディレクトリ（`pom.xml` が存在する場所）で実行します：

| コマンド | フェーズ / 目的 | 説明 |
|---|---|---|
| `mvn clean compile` | ソースコードのコンパイル | 過去の生成物を削除し `src/main/java` をコンパイル。UTF-8 リソース処理を自動実行 |
| `mvn test-compile` | テストコードのコンパイル | `src/test/java` 配下のテストクラスをコンパイル |
| `mvn test` | テスト実行 | JUnit 5 単体テストを実行（インメモリ HSQLDB 連携、外部 DB 不要） |
| `mvn package` | 本番パッケージング | テストを実行し、本番用 Web アーカイブ（`target/jtrac.war`）を生成 |
| `mvn package -DskipTests` | 高速パッケージング | テストをスキップして高速に `target/jtrac.war` を生成 |
| `mvn clean` | 生成物のクリーンアップ | `target/` 配下のすべてのビルドキャッシュおよび中間ファイルを削除 |
| `mvn clean package -f tools/jtrac-exporter/pom.xml -DskipTests` | CLI ツールのビルド | スタンドアロン型 HTML エクスポートツール（Fat JAR）を `tools/jtrac-exporter.jar` に出力 |

---

## 3. 依存関係の自動解決とキャッシュ機構 (`~/.m2/repository`)

JTrac は標準的な Maven 構成を採用しており、すべてのライブラリ（Spring 5.3.x、Wicket 9.x、Hibernate 5.6.x、Spring Security 5.8.x など）はルート [`pom.xml`](../../pom.xml) に定義されています。

### ダウンロードとキャッシュの流れ：
1. 初回ビルド時（`mvn compile` または `mvn package`）、Maven は Maven Central リポジトリから自動的に依存ライブラリを取得します。
2. 取得した JAR ファイルはローカルキャッシュに保存されます：
   - **Windows**：`%USERPROFILE%\.m2\repository\`
   - **Linux / macOS**：`~/.m2/repository/`
3. 以降のビルドはローカルキャッシュから直接読み込まれるため、**開発者が手動で JAR をダウンロード・配置する必要は一切ありません**。

---

## 4. WAR パッケージ構造の解析 (`WEB-INF/lib/`)

`mvn package` を実行すると、`target/` 配下に自己完結型の Web アーカイブ [`target/jtrac.war`](../../target/jtrac.war) が生成されます。

### 内部ディレクトリレイアウト：
```text
jtrac.war
├── META-INF/
│   └── MANIFEST.MF
├── WEB-INF/
│   ├── classes/                 <-- JTrac のコンパイル済クラスおよび UTF-8 リソースファイル
│   │   ├── info/jtrac/...
│   │   └── messages*.properties
│   ├── lib/                     <-- 【中核：最新のサードパーティ依存 JAR 群】
│   │   ├── spring-core-5.3.37.jar
│   │   ├── wicket-core-9.16.0.jar
│   │   ├── hibernate-core-5.6.15.Final.jar
│   │   ├── spring-security-core-5.8.14.jar
│   │   ├── hsqldb-2.7.2.jar
│   │   └── ... (その他すべての依存ライブラリ)
│   └── web.xml                  <-- Servlet 4.0 準拠のデプロイ記述子
└── resources/                   <-- 静的アセット（CSS、アイコン、スタイルシート）
```

- **クラスローダー分離**：Servlet コンテナ（Jetty、Tomcat など）は WAR 内部の `WEB-INF/lib/` を自動的に独立した環境として読み込みます。
- **ゼロ依存デプロイ**：サーバー側にライブラリを追加導入する必要はなく、`jtrac.war` を配置するだけで直ちに動作します。

---

## 5. Web コンテナ適合マトリクス (Web Container Matrix)

JTrac NG 3.0.0-beta は Servlet 4.0 仕様（`javax.servlet`）に準拠しており、生成された WAR ファイルは主要なコンテナへそのままデプロイ可能です：

| Web コンテナ | 対応バージョン | デプロイ方式 |
|---|---|---|
| **Jetty 10.x** | 10.0.x（推奨） | **即時稼働**：`target/jtrac.war` を `webapps/ROOT.war` にコピーして起動。 |
| **Jetty 12.x** | 12.0.x（最新） | **ネイティブ対応**：`ee8` モジュールを有効化して起動：<br/>`java -jar start.jar --add-modules=server,http,ee8-deploy,ee8-webapp`。 |
| **Tomcat 9.x** | 9.0.x（推奨） | **即時稼働**：`target/jtrac.war` を `webapps/ROOT.war` にコピーして起動。 |
| **Tomcat 10.x / 11.x** | 10.1.x / 11.0.x | **自動移行対応**：<br/>1. **方式 A**：`webapps-javaee/` フォルダへ配置して自動変換。<br/>2. **方式 B**：公式 `jakartaee-migration` ツールで変換後、`webapps/` に配置。 |

### ローカル Jetty 10 での動作検証手順：
1. `target/jtrac.war` を `W:\developer\jetty-10.0.26\webapps\ROOT.war` にコピー。
2. Jetty を起動：
   ```powershell
   & "W:\developer\jdk-17.0.9\bin\java.exe" -jar W:\developer\jetty-10.0.26\start.jar
   ```
3. ブラウザでアクセス：`http://localhost:8888/`（初期アカウント：`admin` / パスワード：`admin`）。

---

## 6. ビルド検証とトラブルシューティング (Build Troubleshooting)

### 6.1 ビルド成功チェックリスト
ビルド完了後、以下のファイルが正しく生成されていることを確認してください：
- [ ] `target/jtrac.war`（サイズ約 18〜22 MB、不要な POI ライブラリを削除して軽量化済）
- [ ] `tools/jtrac-exporter.jar`（CLI ツールをビルドした場合）

### 6.2 よくあるビルドエラーと対処法

1. **文字エンコーディングエラー (`unmappable character for encoding`)**：
   - 原因：Windows コンソールのデフォルト文字コードによって UTF-8 のコメントが正しく読み取れない場合があります。
   - 対策：Maven 実行前に環境変数を設定してください：
     ```powershell
     $env:MAVEN_OPTS = "-Dfile.encoding=UTF-8"
     ```
2. **コンパイラのターゲットバージョンエラー (`invalid target release: 11`)**：
   - 原因：端末環境で JDK 8 などの古いバージョンが有効になっています。
   - 対策：`JAVA_HOME` を JDK 11 または 17 に切り替えてください。
3. **依存関係のダウンロード破損・中断**：
   - 原因：ネットワーク切断等により `.jar.lastUpdated` ファイルが残っている場合があります。
   - 対策：強制更新オプションを付けてビルドを実行します：
     ```bash
     mvn clean compile -U
     ```
4. **メモリ不足 (`java.lang.OutOfMemoryError`)**：
   - 対策：Maven JVM の最大ヒープサイズを拡大します：
     ```bash
     export MAVEN_OPTS="-Xmx1024m -XX:MaxMetaspaceSize=256m"
     ```

---

## 7. 環境構築不要：Docker マルチステージ自動ビルド

ローカルに JDK や Maven をインストールせず、クリーンで隔離されたコンテナ内で自動ビルドしたい場合は、Docker マルチステージビルドをご利用ください：

👉 **詳細は専用ガイドを参照：[`docker/README.md`](../../docker/README.md)**
