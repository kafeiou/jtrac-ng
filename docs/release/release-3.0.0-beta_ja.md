# JTrac NG リリースノート (Release Notes) - 3.0.0-beta

[English](release-3.0.0-beta_en.md) | [繁體中文](release-3.0.0-beta_zh-TW.md) | [简体中文](release-3.0.0-beta_zh-CN.md) | [日本語](release-3.0.0-beta_ja.md) | [Tiếng Việt](release-3.0.0-beta_vi.md) | [Deutsch](release-3.0.0-beta_de.md) | [Español](release-3.0.0-beta_es.md) | [Français](release-3.0.0-beta_fr.md)

---

[![Java](https://img.shields.io/badge/Java-11%20%7C%2017-orange.svg)](https://adoptium.net/)
[![Maven](https://img.shields.io/badge/Maven-3.9+-blue.svg)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](../../license.txt)
[![Version](https://img.shields.io/badge/Version-3.0.0--beta-orange.svg)](../../pom.xml)
[![Status](https://img.shields.io/badge/Status-Beta%20Preview-yellow.svg)](release-3.0.0-beta_ja.md)
[![Wicket](https://img.shields.io/badge/Wicket-9.16.0-blue.svg)](https://wicket.apache.org/)
[![Spring](https://img.shields.io/badge/Spring-5.3.37-brightgreen.svg)](https://spring.io/)

> [!NOTE]
> **現在のステータス：Beta テストプレビュー版 (Pre-release / Beta Preview) - 未確定版**  
> 本ドキュメントは動的リリースノート（Living Release Notes）です。Beta 実装検証期間中に行われる今後の機能追加、パラメータ調整、不具合修正は随時ここに自動追記されます。

---

## 目次
1. [バージョン概要と主要ハイライト](#1-バージョン概要と主要ハイライト)
2. [🤖 AI メール問い合わせアシスタント (AI Query Copilot)](#2--ai-メール問い合わせアシスタント-ai-query-copilot)
3. [📦 依存ライブラリ刷新と Java 11 警告の解消](#3--依存ライブラリ刷新と-java-11-警告の解消)
4. [🎨 UI モダン化・アクセシビリティ強化・テーマ切替](#4--ui-モダン化アクセシビリティ強化テーマ切替)
5. [🛡️ 本番セキュリティ強化とフェイルセーフ機構](#5--本番セキュリティ強化とフェイルセーフ機構)
6. [⚙️ システム設定・互換性・安定性の改善](#6--システム設定互換性安定性の改善)
7. [アップグレードと互換性ガイド](#7-アップグレードと互換性ガイド)

---

## 1. バージョン概要と主要ハイライト

JTrac NG 3.0.0-beta は、2.0.0 のアーキテクチャ刷新を土台として、革新的な **AI メール問い合わせアシスタント (Ollama 連携 AI Query Copilot)** を導入。基盤 XML パーサーを更新して Java 11 環境での非推奨リフレクション警告を完全に解消し、UI アクセシビリティ（4段階フォント拡大と A+++ モード、3ステートテーマ切替）および本番セキュリティを大幅に強化しました。

---

## 2. 🤖 AI メール問い合わせアシスタント (AI Query Copilot)

1. **二段階クエリ拡張とインジェクション防御 (Two-Phase Query Expansion)**：
   - ローカルまたはサーバ上の Ollama LLM と連携し、メールの件名・本文から日英のキーワードと技術類義語を自動抽出。
   - `<untrusted_user_query>` による厳格なサンドボックスを形成し、プロンプトインジェクションを遮断。
2. **ハイブリッド重み付け検索と日英マッチ加点**：
   - 要約（+3）、詳細（+1）、コメント（+1）、添付ファイル（+1）の重み付けと、日英双方一致ボーナス（+5点）で高精度にスコアリング。
   - `config` テーブルに `llm.retrieval.max_tickets`（既定 50 件）を登録。
3. **Map-Reduce 2フェーズ分散処理パイプライン**：
   - **Map フェーズ（チケット精読）**：各チケットの履歴と添付ファイル（1ファイル上限 10万文字、PDF/Word/Excel/TXT/LOG/CSV 対応）を個別に分析し、中間サマリーを生成。
   - **Reduce フェーズ（総括レポート）**：中間サマリーを統合し、要約・根本原因・推奨アクションの3部構成でレポートを出力。
   - 処理完了後は `finally` で一時ファイルを確実に削除し、ディスク漏洩を防止。
4. **14日間保持 Web レポートリンクとオフライン HTML ダウンロード対応**:
   - **メールゲートウェイでの遮断を完全回避**：セキュリティゲートウェイで遮断されやすい `.html` 添付ファイルを廃止し、安全な Web リンクによる閲覧へ移行。
   - **14日間のライフサイクルと毎時自動クリーンアップ (TTL Auto-Pruning)**：サーバー上で14日間安全に保管し、毎時実行タスクで期限切れファイルを自動削除。メンテナンスコストゼロを実現。
   - **100% オフライン完全自立型とワンクリックダウンロード**：レポート上部に操作バーを常駐させ、端末へのオフライン HTML 保存を支援。Mermaid.js エンジン内蔵により完全オフライン環境で閲覧可能。
5. **多言語プロンプトガイドと 4 大実践サンプル**：
   - 8言語対応の [`docs/llm/PROMPT_EXAMPLES_*.md`](../llm/PROMPT_EXAMPLES_ja.md) を整備。
6. **スペース別グループ化・ID 降順ソート・100% 完全オフライン Mermaid.js 統合**：
   - **スペース別サブテーブルと ID 降順表示**：AI メール通知の要約表および HTML レポートを所属スペース（Space）ごとに独立したサブテーブルに分割し、スペース内では最新のチケット番号（ID DESC）順で表示。メール本文は簡潔を保ち、余分な Mermaid 警告は一切排他。
   - **完全オフライン Mermaid.js エンジン内蔵**：Mermaid.js (v10.9.1) を Classpath に内蔵し、HTML レポートへ直接埋め込み。外部 CDN へのアクセスが不要となり、エアギャップ環境でも安全にレンダリング可能。ダークモード連動および構文エラー時のフォールバック処理を搭載。
   - **2階層プロンプトの Mermaid フローチャート強制と引用符ガード**：Map フェーズ（個別チケット調査）および Reduce フェーズ（総括と推奨アクション）の両方で `flowchart TD/LR` の出力を義務付け、特殊記号エラーを防ぐダブルクォーテーション防護を徹底。
7. **JTrac 閉域コンテキスト接地・総合所見における外部知見タグ付け・ゼロヒット通知・機密情報マスキング**：
   - **JTrac 閉域コンテキストの厳格な接地 (Strict JTrac Context Grounding)**：LLM の回答は認可された JTrac チケットおよび添付ファイルの内容のみを絶対的な事実の根拠として拘束。個別チケット分析（Map フェーズ）および重要所見（Reduce 第2セクション）では外部の推測知見の混入を 100% 禁止。
   - **総合所見における外部知見の明記義務 (Mandatory External Knowledge Tagging)**：チケット内の情報で解決策が完結しない場合に限り、全体総括（Reduce 第1セクション「要約」）および第3セクション「推奨アクション」にて一般的な知見による補足を許可。ただし、その際は**「`(Note: Recommended based on external reference knowledge)`」（または「`（參考外部資訊給予建議）`」）の注記を明示的に付与することを義務付け**、情報の出所を完全に可視化。
   - **ゼロヒット時の安全通知 (Zero-Hit Safe Notice)**：認可されたスペース内に対象チケットや添付ファイルが存在しない場合、モデルに架空の推測を行わせず即座に遮断し、認可スペース一覧と接地ポリシーを記載したゼロヒット案内メールを返信して元メールを自動消去。
   - **機密情報の自動マスキング (Confidential Credentials Masking)**：`SensitiveDataMasker` を実装し、HTML 診断レポート内のパスワード（password）、Bearer トークン、API キー、秘密鍵ブロック、URL 内認証情報を自動的に `***` に置換して保護（ユーザー名やチケット ID は維持）。
   - **推奨ハードウェア仕様と 200K 長文コンテキスト (Recommended Hardware & 200K Context)**：Prompt 実戦ガイドに推奨構成を明記。フラッグシップ GPU である NVIDIA RTX 5090 (32GB VRAM) および `qwen2.5:32b` の組み合わせを推奨し、Ollama Modelfile による 200K コンテキスト（`num_ctx 200000`）の設定を義務化。切り捨てによる分析破綻を防ぐため、短コンテキストや低パラメータモデルの使用を厳禁と警告。

---

## 3. 📦 依存ライブラリ刷新と Java 11 警告の解消

1. **`dom4j` を `2.1.4` へアップデート**：
   - 従来の `dom4j:1.6.1` から最新の `org.dom4j:dom4j:2.1.4` へ移行。
   - Apache Tomcat 9 および JDK 11 環境で発生していた `WARNING: An illegal reflective access operation has occurred` を完全解消。

---

## 4. 🎨 UI モダン化・アクセシビリティ強化・テーマ切替

1. **4段階フォントスケーリング機能**：
   - 100%（標準）、115%（快適）、130%（明瞭）、**A+++ 超特大モード（145%）** をサポート。Anti-FOUC スクリプトと表崩れ保護を内蔵。
2. **3ステートテーマスイッチャー**：
   - Auto（OS連動）、Light（明）、Dark（暗）を単一アイコンでワンクリック切替。
3. **統一検索バーとスマートジャンプ**：
   - 検索ボタンと区切り線を内蔵し、RefId（例：`PROJ-123`）入力で該当チケットへ即時移動。特権ユーザーの全体横断検索に対応。
4. **モバイル (RWD) 最適化**：
   - モバイル向けドロワーメニュー、チケット要約先頭への ID 明記、Bottom-Sheet 履歴詳細モーダル、センタリングカプセル型ページネーションを装備。

---

## 5. 🛡️ 本番セキュリティ強化とフェイルセーフ機構

1. **グローバルセキュリティヘッダーフィルタ**：
   - `X-Frame-Options`、`X-Content-Type-Options: nosniff`、`Strict-Transport-Security`、`Content-Security-Policy` 等を自動付与。
2. **検索エンジン遮断 (`robots.txt`)**：
   - 内部チケット情報のクローラー収集を防止する `robots.txt` を配備。
3. **安全機能と誤操作防止**：
   - スペース Guest 権限警告、クエリパラメータのホワイトリスト検証、フォーム二重送信防止を実装。

---

## 6. ⚙️ システム設定・互換性・安定性の改善

1. **Wicket i18n 警告解消**：全言語に `status.nullValid = ` を追加。
2. **設定画面の Boolean コントロール改善**：安定した `IndicatingDropDownChoice` に刷新。
3. **JDBC ドライバの明示的登録**：単一接続データソース等の安定性を向上。
4. **UTF-8 テキスト添付の自動判別**：文字化け防止ヘッダーを注入。
5. **Context-Relative ロゴパス解決**：リバースプロキシ環境での表示を修正。
6. **Docker ビルドスクリプトのガイダンスと Git Tag 競合防止**:
   - `docker/build.bat` および `docker/build.sh` に起動時ヒントを追加し、ビルドマシンで Tag の上書きやローカル差分が発生した際の一発同期コマンド（`git fetch --tags -f && git reset --hard origin/master`）を案内。
   - `docker/` 配下に 8 言語対応のビルドガイドを整備し、トラブルシューティング章を追加。
7. **JTrac NG へのリブランディングおよびセマンティックバージョニング躍進 (v3.0.0-beta)**：
   - プロジェクト名称を **JTrac NG** (Next Generation) に一新し、バージョンを **3.0.0-beta** に繰り上げ。15年前の旧 JTrac 2.1.0/2.3.x との検索競合を根本解消し、Google SEO での独立した露出を飛躍的に強化。
   - 公式リポジトリ URL を [https://github.com/kafeiou/jtrac-ng](https://github.com/kafeiou/jtrac-ng) へ完全移行し、Web フッター、モバイルナビゲーション、Maven POM を同期更新。
8. **Lucene 全文検索および履歴表示の包括的近代化 (サブトークン分解・ワイルドカード拡張・履歴表示デフォルト化)**：
   - **サブトークン分詞器 (`SubTokenFilter`)**：メールアドレス（例: `user@gmail.com`）や複合ファイル名（例: `thunderbird_gmail.pdf`）を構成要素トークン（`user`, `gmail`, `com`, `thunderbird`, `pdf`）へ自動分割。単一単語 `gmail` の検索でメールや添付ファイル名を自然かつ確実にヒット。
   - **スマートクエリ拡張と先頭ワイルドカード対応**：単語を自動的に `(term OR term*)` に拡張、`allowLeadingWildcard = true` により任意位置の一致に対応、さらに日本語・中国語のフレーズスロップ `phraseSlop = 2` を有効化し、間隔の空いた語句も柔軟にマッチ。
   - **クリーンなスペース閲覧と検索時の動的展開 (Clean Space Browsing & Search-Driven History Expansion)**：スペース閲覧時はデフォルトで `showHistory = false` となり、1チケット1件（`EFC-109`）の整然としたリストを表示。未検索時に膨大な履歴コメントが展開されるノイズを解消。検索語句入力時または高度な検索画面で自動的に `showHistory = true` が有効化され、検索解除で自動折りたたみ。
   - **チケットと改訂履歴の時系列ソート最適化 (Chronological Order: Parent Ticket Before Revisions)**：履歴展開時の並び順をチケット番号降順・履歴ID昇順（`parent.id DESC, id ASC`）に修正。親チケット（例: `xxx-109`）が改訂コメント（例: `xxx-109(1)`）より必ず先頭に表示され、他カラムソート時も同一チケットの履歴が時系列順にグループ化されます。
   - **キーワード検索時のスマート履歴フィルタリング (Smart History Filtering)**：履歴表示が有効な状態でテキスト検索を行う際、検索キーワードが実際に含まれるチケット先頭レコードまたは該当の改訂コメントのみを表示し、無関係なステータス変更履歴を自動的に除外・非表示化。
   - **起動時のインデックス自動非同期再構築**：分詞器バージョン更新（`lucene.analyzer.version = 3.0.0-subtoken-v1`）を検出し、コンテナ起動完了後にバックグラウンドで既存チケットのインデックスを自動再構築。
   - **Docker Hub イメージの移行**：8言語のビルドガイドで Docker Hub 参照先を `kafeiou/jtrac-ng:latest` に更新。

---

## 7. アップグレードと互換性ガイド

- **データベース移行**：2.3.3-2.0.0 と完全互換であり、**SQL 移行スクリプトの実行は不要**です。
- **WAR デプロイ**：`target/jtrac.war` を既存の `ROOT.war` に上書き配置するだけで完了します。
- **関連ドキュメント**：
  - [JTrac AI メール問い合わせ＆プロンプト作成 実践ガイド](../llm/PROMPT_EXAMPLES_ja.md)
  - [前バージョン リリースノート (2.3.3-2.0.0)](release-2.3.3-2.0.0_ja.md)
