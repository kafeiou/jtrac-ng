# JTrac NG ビルド＆コンパイルガイド (日本語)

[English](BUILD_en.md) | [繁體中文](BUILD_zh-TW.md) | [简体中文](BUILD_zh-CN.md) | [日本語](BUILD_ja.md) | [Tiếng Việt](BUILD_vi.md) | [Deutsch](BUILD_de.md) | [Español](BUILD_es.md) | [Français](BUILD_fr.md)

本ガイドでは、Apache Maven を使用して JTrac NG 3.0.0-beta プロジェクトをコンパイルおよびパッケージングする包括的な手順を説明します。Maven ライフサイクル、依存関係キャッシュ管理、WAR およびスタンドアロン・ポータブル配布パッケージの内部構造、Web コンテナの互換性マトリクス、トラブルシューティングを網羅しています。

---

## 1. 事前環境要件

ビルドを開始する前に、ローカル環境が以下の要件を満たしていることを確認してください：

- **オペレーティングシステム**：Windows / Linux / macOS
- **Java 開発キット (JDK)**：**Java 21 LTS**（[Eclipse Temurin JDK 21+](https://adoptium.net/temurin/releases/?version=21) 推奨、例: `W:\developer\jdk-21.0.9`、必須最低要件は Java 21）
  > [!IMPORTANT]
  > コアアーキテクチャは **Spring 6.2.x**、**Jakarta EE 10 (Servlet 6.0)**、**Hibernate 5.6.x (Jakarta)**、**Apache Wicket 10.x** に刷新され、対象バイトコードは **Java 21** です。**JDK 8 / 11 / 17 はサポートされていません**。必ず JDK 21+ でコンパイルしてください。
- **Apache Maven**：**Maven 3.9+**（例: `W:\developer\apache-maven-3.9.9`）

### 環境変数の設定例

- **Windows (PowerShell)**：
  ```powershell
  $env:JAVA_HOME = "W:\developer\jdk-21.0.9"
  $env:PATH = "W:\developer\apache-maven-3.9.9\bin;$env:PATH"
  ```
- **Windows (CMD)**：
  ```cmd
  set "JAVA_HOME=W:\developer\jdk-21.0.9"
  set "PATH=W:\developer\apache-maven-3.9.9\bin;%PATH%"
  ```
- **Linux / macOS (Bash/Zsh)**：
  ```bash
  export JAVA_HOME="/usr/lib/jvm/java-21-openjdk"
  export PATH="$JAVA_HOME/bin:$PATH"
  ```

環境の検証：
```bash
mvn -version
```
出力に Maven 3.9+ および Java 21 の情報が正しく表示されていることを確認してください。

---

## 2. Maven ビルドコマンドマトリクス

`pom.xml` が存在するプロジェクトのルートディレクトリで以下のコマンドを実行します：

| コマンド | フェーズ / アクション | 説明 | 生成物パス |
|---|---|---|---|
| `mvn clean compile` | ソースコンパイル | ビルド生成物をクリーンし `src/main/java` をコンパイル（UTF-8 フィルタリング） | `target/classes/` |
| `mvn test-compile` | テストコンパイル | `src/test/java` の全ユニットテストクラスをコンパイル | `target/test-classes/` |
| `mvn test` | テスト実行 | 198 件の JUnit 5 単体テストを実行（内蔵インメモリ HSQLDB 連携） | コンソールレポート |
| `mvn package` | 正式 WAR パッケージング | テストを実行し標準 Web アプリケーションアーカイブを生成 | `target/jtrac.war` |
| `mvn package -DskipTests` | 高速 WAR パッケージング | テストをスキップして高速に WAR を生成 | `target/jtrac.war` |
| `mvn package -Pportable` | ポータブル版フルビルド | テストを実行し Jetty 12.1 EE10 内蔵のポータブル ZIP を生成 | `target/jtrac-ng-3.0.0-beta-portable.zip` |
| `mvn package -Pportable -DskipTests` | ポータブル版高速ビルド | テストをスキップして高速にポータブル ZIP を生成 | `target/jtrac-ng-3.0.0-beta-portable.zip` |
| `package-portable.bat` | Windows スクリプト実行 | ルートでダブルクリック実行。自動でテストをスキップして高速生成 | `target/jtrac-ng-3.0.0-beta-portable.zip` |
| `./package-portable.sh` | Linux/macOS スクリプト実行 | ターミナルで Bash スクリプトを実行しポータブル版を高速生成 | `target/jtrac-ng-3.0.0-beta-portable.zip` |
| `mvn clean` | クリーン | `target/` ディレクトリ配下のビルドキャッシュと中間ファイルを削除 | - |
| `mvn clean package -f tools/jtrac-exporter/pom.xml -DskipTests` | CLI ツールビルド | 独立した課題エクスポートツール（Fat JAR）をビルド | `tools/jtrac-exporter.jar` |

---

## 3. Maven 依存関係自動取得メカニズム (`~/.m2/repository`)

JTrac NG は標準的な Apache Maven アーキテクチャに基づいています。すべてのサードパーティライブラリ（Spring 6.2.x、Wicket 10.x、Hibernate 5.6.x Jakarta、Spring Security 6.5.x、Jetty 12.1.x など）は [`pom.xml`](../../pom.xml) で定義されています。

### 自動取得とキャッシュフロー：
1. 初回 `mvn compile` または `mvn package` 実行時、Maven はリモートの中央リポジトリ（Maven Central）に接続して依存関係ツリーを自動解決します。
2. ダウンロードされた JAR はローカルキャッシュに保存されます：
   - **Windows**：`%USERPROFILE%\.m2\repository\`
   - **Linux / macOS**：`~/.m2/repository/`
3. 以降のビルドはローカルキャッシュからオフラインで直接読み込まれるため、**開発者が手動で外部 JAR を探して配置する必要はありません**。

---

## 4. WAR パッケージ構造解析 (`WEB-INF/lib/`)

`mvn package` を実行すると、標準的な Java Web アプリケーションアーカイブ [`target/jtrac.war`](../../target/jtrac.war) が生成されます。

### WAR 内部ディレクトリ構成：
```text
jtrac.war
├── META-INF/
│   └── MANIFEST.MF
├── WEB-INF/
│   ├── classes/                 <-- JTrac のコンパイル済みクラスと UTF-8 リソース
│   │   ├── info/jtrac/...
│   │   └── messages*.properties
│   ├── lib/                     <-- 【コア：モダンな Jakarta EE 10 依存ライブラリ群】
│   │   ├── spring-core-6.2.19.jar
│   │   ├── wicket-core-10.12.0.jar
│   │   ├── hibernate-core-5.6.15.Final.jar
│   │   ├── spring-security-core-6.5.11.jar
│   │   ├── jakarta.servlet-api-6.0.0.jar
│   │   ├── hsqldb-2.7.4.jar
│   │   └── ... (その他すべての依存 JAR)
│   └── web.xml                  <-- Servlet 6.0 記述子
└── resources/                   <-- 静的リソース（CSS、ベクター SVG ロゴ、スタイルシート）
```

- **クラスローダーの完全分離**：Servlet コンテナ起動時、各 WAR 内部の `WEB-INF/lib/` が安全に分離されます。
- **ゼロ設定デプロイ**：サーバー側に事前 JAR の配置は不要で、単一の `jtrac.war` を配置するだけで即座に稼働します。

---

## 5. Web コンテナ互換性＆デプロイマトリクス

JTrac NG 3.0.0-beta は **Jakarta EE 10 / Servlet 6.0** 仕様（`jakarta.servlet.*`）に準拠しており、モダンな Web コンテナへ直接デプロイ可能です：

| Web コンテナ | サポートバージョン | デプロイ方式 |
|---|---|---|
| **Jetty 12.x** | 12.1.x+（推奨首位） | **ネイティブ対応**：`ee10` モジュールを有効化：<br/>`java -jar start.jar --add-modules=server,http,ee10-deploy,ee10-webapp`<br/>または同梱のポータブル版（`jtrac-ng-3.0.0-beta-portable.zip`）で即座に実行可能。 |
| **Tomcat 10.x / 11.x** | 10.1.x / 11.0.x | **ネイティブ対応**：Jakarta EE 10 ネイティブ。`target/jtrac.war` を `webapps/ROOT.war` にコピーして起動するだけです。 |
| **Docker コンテナ** | 3.0.0-beta | **公式推奨**：`jetty:12.1-jre21-eclipse-temurin` ベースのコンテナイメージでワンクリック運用。 |

---

## 6. ビルド検証＆トラブルシューティング

### 6.1 ビルド成功チェックリスト
ビルド完了後、以下のファイルが正しく生成されていることを確認してください：
- [ ] `target/jtrac.war`（サイズ約 50〜56 MB、完全な Jakarta EE 10 ライブラリを内包）
- [ ] `target/jtrac-ng-3.0.0-beta-portable.zip`（サイズ約 98 MB、ポータブルビルド実行時）
- [ ] `tools/jtrac-exporter.jar`（CLI ツールビルド時）

### 6.2 よくあるビルド問題と解決策

1. **コンパイラのターゲットバージョンエラー (`Fatal error compiling: invalid target release: 21`)**：
   - 原因：端末のアクティブな JDK が JDK 17 以下です。
   - 解決策：`JAVA_HOME` を JDK 21+ に切り替え、`java -version` で確認してください。
2. **コンパイル時の文字コードエラー (`unmappable character for encoding`)**：
   - 原因：Windows の標準コードページが非 UTF-8 であるためコメント等の解析に失敗します。
   - 解決策：Maven 実行前にグローバルエンコーディングを設定してください：
     ```powershell
     $env:MAVEN_OPTS = "-Dfile.encoding=UTF-8"
     ```
3. **依存関係のダウンロード破損・中断**：
   - 原因：通信切断により破損した `.jar.lastUpdated` がローカルキャッシュに残っています。
   - 解決策：依存関係の再取得を強制実行します：
     ```bash
     mvn clean compile -U
     ```
4. **メモリ不足 (`java.lang.OutOfMemoryError`)**：
   - 解決策：Maven JVM のメモリ上限を引き上げます：
     ```bash
     export MAVEN_OPTS="-Xmx2048m -XX:MaxMetaspaceSize=512m"
     ```

---

## 7. インストール不要環境：Docker マルチステージビルド

ローカルに JDK や Maven をインストールせず、隔離されたクリーンな環境でビルドしたい場合は、Docker マルチステージビルドをご利用ください：

👉 **詳細な手順については以下をご参照ください：[`docker/README.md`](../../docker/README.md)**
