# JTrac NG 發布說明 (Release Notes) - 3.0.0-beta

[English](release-3.0.0-beta_en.md) | [繁體中文](release-3.0.0-beta_zh-TW.md) | [简体中文](release-3.0.0-beta_zh-CN.md) | [日本語](release-3.0.0-beta_ja.md) | [Tiếng Việt](release-3.0.0-beta_vi.md) | [Deutsch](release-3.0.0-beta_de.md) | [Español](release-3.0.0-beta_es.md) | [Français](release-3.0.0-beta_fr.md)

---

[![Java](https://img.shields.io/badge/Java-11%20%7C%2017-orange.svg)](https://adoptium.net/)
[![Maven](https://img.shields.io/badge/Maven-3.9+-blue.svg)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](../../license.txt)
[![Version](https://img.shields.io/badge/Version-3.0.0--beta-orange.svg)](../../pom.xml)
[![Status](https://img.shields.io/badge/Status-Beta%20Preview-yellow.svg)](release-3.0.0-beta_zh-TW.md)
[![Wicket](https://img.shields.io/badge/Wicket-9.16.0-blue.svg)](https://wicket.apache.org/)
[![Spring](https://img.shields.io/badge/Spring-5.3.37-brightgreen.svg)](https://spring.io/)

> [!NOTE]
> **當前狀態：Beta 測試預覽版 (Pre-release / Beta Preview) - 尚未正式封版**  
> 本文件為動態發布日誌（Living Release Notes）。在 Beta 實測期間，所有後續的新增功能、參數微調與 Bug 修正將自動即時追加於此文件。

---

## 目錄
1. [版本核心亮點概述](#一版本核心亮點概述)
2. [🤖 AI 智慧郵件查詢秘書 (AI Query Copilot)](#二-ai-智慧郵件查詢秘書-ai-query-copilot)
3. [📦 核心相依套件升級與 Java 11 警示消除](#三-核心相依套件升級與-java-11-警示消除)
4. [🎨 介面現代化、字級無障礙與主題切換](#四-介面現代化字級無障礙與主題切換)
5. [🛡️ 生產環境安全防護與防呆機制](#五-生產環境安全防護與防呆機制)
6. [⚙️ 系統設定與相容性修復](#六-系統設定與相容性修復)
7. [版本升級與相容性指引](#七版本升級與相容性指引)

---

## 一、版本核心亮點概述

JTrac NG 3.0.0-beta 在 2.0.0 核心現代化架構的基礎上，引進了革命性的 **AI 智慧郵件查詢秘書 (AI Query Copilot with Ollama)**、升級底層 XML 解析器徹底消除 Java 11 反射存取警告、大幅強化使用者介面無障礙體驗（四段式字級縮放與 A+++ 模式、三態深淺主題）、以及全方位的生產安全防護網。

---

## 二、🤖 AI 智慧郵件查詢秘書 (AI Query Copilot)

1. **雙階段查詢擴展與防注入圍籬 (Two-Phase Query Expansion)**：
   - 整合本地或伺服器端 Ollama LLM，自動分析使用者來信主旨與內文，萃取中英文實體詞彙與技術同義詞。
   - 建立嚴格的 `<untrusted_user_query>` 安全沙盒圍籬，阻絕 Prompt Injection 與惡意指令竊取。
2. **混合加權檢索與雙語命中加分 (Hybrid Weighted Retrieval)**：
   - 檢索演算法依摘要（+3）、詳情（+1）、留言（+1）、附件（+1）精準評分，並提供跨語言匹配加分（+5）。
   - 於 `config` 表註冊全域參數 `llm.retrieval.max_tickets`（預設 50 張）。
3. **Map-Reduce 兩階段分治消化機制 (Map-Reduce Pipeline)**：
   - **Map 階段（單工單消化）**：逐張消化候選工單歷史討論與附件內容（單檔抽取上限 10 萬字元，支援 PDF, Word, Excel, TXT, LOG, CSV），輸出中繼分析至安全暫存區。
   - **Reduce 階段（大局總結）**：統整所有工單精煉摘要，輸出結構化三大區塊：
     1. 核心解答摘要 (Executive Summary)
     2. 各工單關鍵發現與解法 (Key Findings & Resolution)
     3. 建議行動方案 (Next Actions & Recommendations)
   - 具備防呆機制與 `finally` 暫存目錄保證銷毀，零磁碟洩漏風險。
4. **線上 14 天安全 Web 報告連結與離線 HTML 一鍵下載 (14-Day Expiring Web Report & Offline Download)**：
   - **徹底避開企業郵件閘道攔截**：郵件內文不再夾帶易被企業郵件閘道（Exchange/Outlook/Gmail）阻擋之 `.html` 附件，改為提供安全超連結直連開啟。
   - **14 天生命週期與每小時自動清理 (TTL Auto-Pruning)**：伺服器安全儲存報告 14 天，利用每小時排程自動銷毀過期檔案，維護成本為 0，磁碟容量恆定。
   - **100% 離線可用與一鍵下載**：報告頂部常駐操作列，支援收件人一鍵下載離線 HTML 檔案（`JTrac-AI-Report-[Date].html`）；下載之檔案內嵌完整 Mermaid.js 引擎，斷網隔離環境 100% 原生可用。
5. **完整多語系 Prompt 指南與 4 大實戰範例**：
   - 建立 8 種語系之實戰指南 [`docs/llm/PROMPT_EXAMPLES_*.md`](../llm/PROMPT_EXAMPLES_zh-TW.md)，涵蓋障礙排查、工單追蹤、架構規範與版本升級等場景。
6. **工單空間分組、ID 倒序排列與 100% 離線 Mermaid 流程圖引擎 (Space Grouping, ID DESC & Offline Mermaid.js)**：
   - **空間分組與新到舊倒序 (Space-Grouped Sub-tables & ID DESC)**：全面重構 AI 郵件回覆速覽與 HTML 診斷報告，依授權專案空間（Space）劃分獨立子表格展示（附工單總數），各空間內部依工單編號（ID DESC）嚴格由新到舊倒序排列；信件保持極簡專業，不追加多餘 Mermaid 警語。
   - **100% 離線純本地 Mermaid.js 流程圖引擎 (Air-gapped Offline Mermaid Rendering)**：將完整版 Mermaid.js (v10.9.1) 內嵌至 Classpath 並直接注入 HTML 報告，徹底擺脫外部 CDN 網路依賴；具備系統深淺色主題自適應（`prefers-color-scheme`）與語法錯誤容錯降級機制，保證封閉隔離內網環境皆能完美瀏覽。
   - **雙層級 Prompt 流程圖硬約束 (Two-Tier Flowchart Prompts with Quote Guardrails)**：於 Map 階段（單工單深入排查）及 Reduce 階段（全域核心解答與行動方案）明確要求輸出標準 `flowchart TD/LR` 流程圖，並強制所有節點文字加上雙引號防呆，避免特殊符號導致渲染中斷。
7. **JTrac 封閉領域接地、綜合評論外部知識標註與零命中防呆 (Data Grounding, External Knowledge Tagging & Secret Masking)**：
   - **JTrac 封閉領域接地原則 (Strict JTrac Context Grounding)**：全面約束 LLM 回答必須以授權 JTrac 工單與附件內容為唯一真實依歸；單工單分析（Map）與工單關鍵發現（Reduce Section 2）100% 嚴禁引入外部未驗證推測。
   - **綜合評論外部知識強制標註 (Mandatory External Knowledge Tag)**：僅在全域總結（Reduce Section 1 核心解答摘要）與 Section 3 建議行動方案中，允許於工單事證不足時輔以業界常識或通用指引，但**強制要求顯式標註「（參考外部資訊給予建議）」**（英文標籤：`(Note: Recommended based on external reference knowledge)`、簡體中文：`（参考外部信息给予建议）`），讓使用者一目了然建議之來源背景。
   - **零命中安全通知 (Zero-Hit Safe Notice)**：當使用者查詢在獲授權之 Space 內查無任何匹配工單或附件時，系統立即攔截並寄送結構化零命中通知信件，列出當前授權 Space 清單與接地原則說明，絕不轉交模型進行空想臆測，並將原始信件自動清空。
   - **機敏憑證安全遮罩 (Confidential Secrets Masking)**：實作 `SensitiveDataMasker`，於 HTML 診斷報告組裝時自動過濾密碼（password）、Bearer Token、API Key、私鑰區塊及 URL 連線密碼，全面替換為 `***` 遮罩保護，同時完整保留使用者帳號與工單 ID。
   - **推薦部署硬體與長上下文配置 (Recommended Hardware & 200K Context)**：於 Prompt 實戰指南中明確規範硬體選型，推薦旗艦 GPU NVIDIA RTX 5090 (32GB VRAM) 與 `qwen2.5:32b`，並強制要求透過 Ollama Modelfile 配置 200K 長上下文視窗（`num_ctx 200000`）；嚴厲示警勿採用短上下文或小參數模型，杜絕資料截斷導致分析失效。
8. **檢索時間窗口過濾與顯式單號豁免機制 (Time-Window Retrieval Filter & Exact RefId Exemption)**：
   - **動態時間窗口過濾**：於 `config` 表註冊全域參數 `llm.retrieval.max_age_days`（預設 365 天），以工單最後活動時間 $\max(\text{item.timeStamp}, \text{latestHistory.timeStamp})$ 作為判定基準，自動過濾超過 1 年未更新之老舊工單，避免過時歷史資料污染 AI 分析脈絡；若設定為 0 或負數則停用過濾並檢索全歷史。
   - **顯式工單號（Exact RefId）全面豁免**：當使用者於郵件主旨或內文中明確指定工單編號（如 `PROJ-108`）時，系統視為精確調閱意圖，完全豁免時間窗口限制並優先納入候選分析。
9. **檢索停用詞過濾、兩岸專業詞彙互轉與關鍵字膠囊報告呈現 (Stopwords Filtering, Cross-Strait Terminology Mapping & Keyword Dossier Badges)**：
   - **檢索停用詞過濾 (Retrieval Stopwords Filtering)**：於 `config` 表註冊全域參數 `llm.retrieval.stopwords`（預設值為「`通知,工單`」），在 Prompt 萃取指引與 Java 前後端管線中硬性排除泛用干擾詞（並自動關聯排除同義詞 `notice`、`notification`、`ticket` 與 `issue`）；大幅杜絕如查詢「發票通知」或「發票工單」時因「通知」或「工單」一詞導致 Lucene 檢索被無關系統通知或普通工單淹沒的問題。
   - **兩岸專業習慣詞彙雙向展開 (Cross-Strait Vocabulary & Terminology Mapping)**：建立常駐雙向 IT 與業務術語對照表（涵蓋「專案 ⇄ 项目」、「發票 ⇄ 发票/开票」、「程式碼 ⇄ 代码」、「記憶體 ⇄ 内存」、「欄位 ⇄ 字段」、「排程 ⇄ 定时任务/计划任务」、「檔案 ⇄ 文件」、「資料庫 ⇄ 数据库」、「使用者 ⇄ 用户」等），結合 Prompt 指令強化，實現深度術語互補而非單字替換，確保跨兩岸用語皆能精準召回。
   - **全透明關鍵字膠囊標籤呈現 (Transparent Keyword Badges in Dossier & Email)**：於 AI HTML 深度診斷報告 Header 與回覆郵件內文中顯式展示所有實際採納與擴展之關鍵字膠囊標籤（包含繁體、簡體、兩岸用語與英文對應詞），並同步記錄於中繼 Staging Markdown 摘要，搜尋條件與擴展歷程 100% 透明可稽核。
   - **CJK 短語雙引號檢索與相關度門檻防呆**：Lucene 檢索時自動為多字中文字詞包裹雙引號以防單字 Unigram 拆解產生雜訊，且嚴格過濾相關度分數 $\le 0$ 之無關候選票券，徹底杜絕無關工單干擾。
10. **嚴格零幻覺 RAG 問答機制與 Email「回答問題」卡片 (Strict Zero-Hallucination RAG & Direct Question-Answering Email Card)**：
   - **嚴格零幻覺封閉域限制 (Strict Closed-Domain RAG Grounding)**：全面移除模型補充外部常識之條款，硬性規定所有回答必須 100% 嚴格基於檢索出的工單歷史記錄與附件文檔，並強制標註引用工單編號 `[REF-ID]`。若工單中查無解答資訊，模型嚴禁憑空猜測，必須明確聲明「（現有工單資料中查無此項資訊 / Not documented in retrieved tickets）」。
   - **智慧提問意圖偵測 (Question Inquiry Detection)**：自動分析郵件主旨與內文之問號標點（`?`、`？`）及多語系疑問詞（`請問`、`如何`、`怎麼`、`為什麼`、`在哪`、`是否`、`能不能`、`how`、`what`、`why`、`where` 等），自動切換至專屬提問增強模式。
   - **Email 內文直覺卡片呈現 (Direct Answer Card in Email Body)**：郵件正文直接渲染醒目的「💬 回答問題 / Answer to Inquiry」卡片（非提問則為「💡 核心解答摘要」；查無工單則呈現橘紅警示防呆卡片），支援機密自動遮蔽、工單超連結生成及 Mermaid 流程圖自動過濾保護。
11. **壓縮檔案 (.zip, .rar) 全文文字索引與 Ollama 多模態 Vision OCR 圖檔辨識 (Archive Full-Text Indexing & Ollama Multimodal Vision OCR)**：
    - **壓縮檔案內部文檔深度文字索引 (`.zip`, `.rar`)**：新增壓縮檔案附件之內部文檔全文檢索抽取能力，支援 `.zip`（純 Java 串流解壓）與 `.rar`（Junrar 純 Java 核心），自動解析解壓後之文檔（`.pdf`, `.docx`, `.xlsx`, `.txt`, `.csv`, `.md`, `.log`）並依來源路徑結構化標註 `[Archive: folder/document.ext]`，供 Lucene 建立索引及 AI RAG 檢索。
    - **嚴格解壓縮安全圍籬 (Zip Slip & Zip Bomb Defense)**：
      - 阻絕路徑穿越（Zip Slip）：自動偵測並跳過包含 `..` 或開頭絕對路徑之惡意項目。
      - 阻絕解壓炸彈（Zip Bomb）：單一壓縮檔最多處理 100 個檔案項目，累計解壓大小上限 30MB，單檔上限 10MB。
      - 阻絕遞迴解壓攻擊：巢狀壓縮檔（壓縮檔內含壓縮檔）自動跳過，絕不遞迴解開。
    - **Ollama 多模態視覺 OCR 圖檔文字辨識 (Ollama Multimodal Vision OCR)**：
      - 在配置 Ollama 環境下，全面支援獨立上傳圖檔（`.png`, `.jpg`, `.jpeg`, `.bmp`, `.webp`, `.gif`）以及壓縮檔解開之圖檔進行自動 OCR 文字辨識與 Lucene 索引。
      - 透過 Ollama `/api/chat` 的 `images` Base64 協定進行純文字轉錄（Transcribe），自動將辨識文字納入工單附件全文搜尋與 AI 檢索脈絡。
      - **高解析度自動等比縮圖與 GIF 畫面正規化 (Auto-Downscaling & GIF Normalization Defense)**：針對長寬超過 1280px 之高解析度圖檔自動於傳送給 Ollama 前等比例縮放至安全尺寸（最大 1280px），並針對透明度與 GIF 動態圖自動進行白色背景平坦化處理；大幅降低 70% 以上之 GPU 顯存與 Token 消耗，徹底防範 Ollama `CUDA error: out of memory` 顯存耗盡問題。
      - 系統參數支援 `attachment.ocr.enabled`（預設 true）、`attachment.ocr.model`（可自訂如 `llama3.2-vision`、`minicpm-v` 或 `moondream`，預設為空時自動沿用預設 LLM 模型）及 `attachment.ocr.max_image_size_mb`（預設 5MB）。
      - 具備優雅降級機制：若 Ollama 離線、逾時或模型不支援圖片，自動跳過 OCR 絕不中斷工單建立或檢索作業。

---

## 三、📦 核心相依套件升級與 Java 11 警示消除

1. **升級 `dom4j` 至 `2.1.4`**：
   - 將舊有 `dom4j:1.6.1`（發布於 2005 年）升級為最新版 `org.dom4j:dom4j:2.1.4`。
   - 修復 [`Metadata.java`](../../src/main/java/info/jtrac/domain/Metadata.java) 中因泛型轉換引起的編譯警告。
   - 徹底消除了在 Apache Tomcat 9 與 JDK 11 環境下執行時出現的 `WARNING: An illegal reflective access operation has occurred (org.dom4j.io.SAXContentHandler)` 警示訊息。

---

## 四、🎨 介面現代化、字級無障礙與主題切換

1. **四段式字級循環無障礙模式 (Font Scaling)**：
   - 支援 100%（標準）、115%（舒適）、130%（清晰）以及全新的 **A+++ 超大字模式（145%）**。
   - 具備防閃爍 (Anti-FOUC) 機制與表格防破版安全保護，支援 localStorage 狀態記憶。
2. **三態深淺主題切換 (Theme Switcher)**：
   - 支援系統跟隨 (Auto)、淺色模式 (Light) 與深色模式 (Dark) 單圖示一鍵無縫循環切換。
3. **統一文字搜尋列與智慧跳轉**：
   - 整合內嵌搜尋送出按鈕與分隔線，大幅擴大輸入框寬度與點擊熱區。
   - 支援智慧 RefId 辨識（如輸入 `PROJ-123` 直接導航至工單詳情頁），並支援超級管理員全域跨專案搜尋。
4. **行動端 (Mobile RWD) 體驗全面優化**：
   - 新增行動端漢堡選單抽屜 (Navigation Drawer)。
   - 工單摘要開頭顯式帶出工單編號，並提供工單歷程詳情 Bottom-Sheet 底部抽屜彈窗。
   - 行動端置中膠囊分頁器，桌面端提供首頁、末頁跳轉與總頁數統計。
5. **搜尋與清單重載之「正在搜尋中」動態遮罩視窗與 150ms 防閃爍機制**：
   - 全面涵蓋四大搜尋入口（桌面端頂部快速搜尋、行動端滑出搜尋抽屜、進階搜尋頁面與清單頁篩選框）以及搜尋結果清單之分頁頁碼切換、欄位排序點擊與清除搜尋（`✕`）。
   - 採用 **150ms 延遲顯示防閃爍機制**：極速回應時無縫跳轉不閃屏，當啟用 Ollama 同義詞擴展耗時較長時自動浮現置中半透明遮罩卡片與純 CSS 轉圈動畫（「正在搜尋中，請稍候... / 正在執行全文檢索與同義詞分析」），並內建空白關鍵字攔截、Ctrl/Cmd 開新分頁排除、`pageshow` 上一頁快取自動關閉及 15 秒安全超時自動解除保護。

---

## 五、🛡️ 生產環境安全防護與防呆機制

1. **全域安全標頭防護 (Security Headers Filter)**：
   - 注入 `X-Frame-Options: SAMEORIGIN`、`X-Content-Type-Options: nosniff`、`Strict-Transport-Security`、`Content-Security-Policy` 與 `Referrer-Policy`。
2. **搜尋引擎阻擋指令 (`robots.txt`)**：
   - 部署預設 `robots.txt`，嚴禁網路搜尋引擎爬取內部工單敏感資料。
3. **安全防護與防呆機制**：
   - 專案空間 Guest 權限提示警告。
   - 查詢參數白名單過濾機制，防範惡意參數篡改。
   - 登入卡片與表單防重複提交阻擋（Double-click submission protection）。

---

## 六、⚙️ 系統設定與相容性修復

1. **Wicket i18n Debug 警告消除**：
   - 於全語系屬性檔補齊 `status.nullValid = `，消除下拉選單找不到語系 key 的除錯日誌。
2. **設定頁面布林開關重構**：
   - 將原本可能因 Ajax 綁定失效的 CheckBox 開關重構為高相容性的 `IndicatingDropDownChoice`。
3. **資料庫連線驅動顯式註冊**：
   - 顯式載入並註冊 JDBC Driver，強化在特定輕量環境（如單一連線資料來源）下的穩定度。
4. **附件 UTF-8 文字編碼自動偵測**：
   - 針對文字型附件自動判斷 UTF-8 編碼並注入 Charset Header，避免瀏覽器預覽產生亂碼。
5. **Context-Relative Logo 路徑解析**：
   - 修復系統 Header Logo 在反向代理環境下的相對路徑解析。
6. **Docker 建置腳本防呆與 Git 同步指引 (Docker Build Tips & Sync Guidance)**：
   - 於 `docker/build.bat` 與 `docker/build.sh` 終端機加入啟動提示，提醒編譯前遇 Tag 衝突或檔案異動時之一鍵同步指令（`git fetch --tags -f && git reset --hard origin/master`）。
   - 於 `docker/` 提供完整 8 語系建置說明文件，追加常見問題與 Tag 衝突排除指引。
7. **JTrac NG 品牌重塑與語義化版本躍升 (JTrac NG Rebranding & v3.0.0-beta)**：
   - 專案全面升級為 **JTrac NG**（Next Generation），版本號躍升為 **3.0.0-beta**，徹底告別 15 年前老舊 JTrac 2.1.0/2.3.x 歷史搜尋衝突，大幅提升 Google SEO 獨立識別度與曝光度。
   - 官方倉庫網址全面遷移至 [https://github.com/kafeiou/jtrac-ng](https://github.com/kafeiou/jtrac-ng)，網頁頁尾、行動端導覽列與 Maven POM 描述檔同步更新。
8. **Lucene 全文檢索與歷史歷程全面現代化 (Subtokens, Wildcard Expansion & Show History Default)**：
   - **子詞分詞器 (`SubTokenFilter`)**：自動將 Email 信箱（如 `user@gmail.com`）與複合檔名（如 `thunderbird_gmail.pdf`）拆解出子詞 Token（`user`、`gmail`、`com`、`thunderbird`、`pdf`），使搜尋單詞 `gmail` 時能自然精準命中信箱與附件。
   - **智慧查詢萬用字元擴展與前置星號支援**：單詞自動展開為 `(term OR term*)`，啟用 `allowLeadingWildcard = true` 支援 `*關鍵字*` 任意位置比對，並配置中文片語容錯 `phraseSlop = 2`，使「申請帳號」能順利比對「申請開放帳號」。
   - **專案空間瀏覽純工單呈現與搜尋動態展開 (Clean Space Browsing & Search-Driven History Expansion)**：專案空間瀏覽預設 `showHistory = false`，每張工單僅顯示一筆乾淨記錄（`EFC-109`），徹底消除未搜尋即展開大量歷史修訂留言之混亂現象；僅在使用者輸入關鍵字檢索或進入進階搜尋表單時，系統自動啟用 `showHistory = true` 展開命中之歷程與留言，點選清除搜尋時自動收合還原。
   - **工單與修訂歷程時序排序優化 (Chronological Order: Parent Ticket Before Revisions)**：修正歷史記錄展開時之排序規則，依工單單號降序、歷程流水號升序（`parent.id DESC, id ASC`），確保主工單（如 `xxx-109`）永遠排在修訂歷程（如 `xxx-109(1)`）之前，若依其他欄位排序亦維持工單分組時序相鄰呈現。
   - **關鍵字搜尋之智慧歷程過濾 (Smart History Filtering)**：當開啟歷史記錄並執行文字搜尋時，自動實施智慧過濾，僅回傳並展示真正包含該搜尋關鍵字之首筆工單或留言修訂列，自動隱藏未含關鍵字之無關狀態修改歷程。
   - **系統啟動自動非同步重建索引**：自動偵測分詞器升級版本（`lucene.analyzer.version = 3.0.0-subtoken-v1`），於容器啟動後在背景非同步重建歷史工單 Lucene 索引。
   - **Docker Hub 映像檔遷移**：同步將 8 國語系建置手冊中的 Docker Hub 映像檔指向 `kafeiou/jtrac-ng:latest`。
9. **Ollama 雙向繁簡轉換與兩岸同義詞檢索擴展 (Ollama Chinese Variant & Synonym Search Expansion)**：
   - **雙向字面繁簡與慣用術語全覆蓋**：檢索中文關鍵字時，自動透過 Ollama 進行雙向轉換與擴展，同時涵蓋字面繁簡字體（繁體「專案」<-> 簡體字面「专案」）與兩岸慣用軟體/商業同義詞（「專案」<-> 大陸慣用「项目」；「程式碼」<->「代码」；「記憶體」<->「内存」；「網路」<->「网络」；「軟體」<->「软件」；「伺服器」<->「服务器」；「預設」<->「默认」；「使用者」<->「用户」；「登入」<->「登录」），組合成複合 Lucene 查詢（如 `((專案 OR 專案*) OR (专案 OR 专案*) OR (项目 OR 项目*))`），徹底打通繁簡工單跨語系無縫檢索。
   - **純英數字零負擔放行**：檢索英數字詞（如 `EFC-109`、`login`）時中文偵測為 false，完全略過 Ollama 呼叫（0ms 額外耗時）。
   - **記憶體 LRU 快取極速命中**：針對常用詞彙建立執行緒安全 LRU 快取（預設容量 1,000 筆），重複查詢時從快取直接取回（< 1ms）。
   - **逾時控制 (預設 6 秒) 與斷路器靜默降級**：新增系統參數 `llm.search.expansion.enabled`（預設 true）與 `llm.search.expansion.timeout`（預設 6 秒）；若 Ollama 離線或逾時，自動靜默降級至原始關鍵字檢索絕不中斷搜尋，並啟動 30 秒斷路器退避，避免後續檢索持續等待。
10. **Markdown 渲染 Windows UNC 路徑與反斜線完整保護 (Markdown Windows UNC Path & Backslash Preservation)**：
    - **問題根因修復**：修復在工單細節或留言輸入 Windows UNC 網路共享路徑（例如 `\\hlmt.com.tw\SysVol\hlmt.com.tw\Policies\{9CECF8CB-B752-4E7C-A1FB-90CB3B021A07}\User\Scripts\Logon` 或 `"\\hlmt.com.tw\..."`）時，因 CommonMark 原生標點符號跳脫機制導致開頭雙反斜線 `\\` 被吞噬縮減為單反斜線 `\`、大括號前反斜線 `\{` 遺失之問題。
    - **文字外觀 100% 保持自然排版**：維持一般純文字呈現，不強制包覆為程式碼晶片樣式，字體外觀與前後內文自然融合。
    - **全方位路徑覆蓋與程式碼防護**：全面支援 UNC 共享路徑（`\\server\share`）、磁碟路徑（`C:\...`）、相對路徑及單獨之雙反斜線（`\\`）；同時具備代碼區塊感知，對行內代碼（`` `...` ``）與圍欄程式碼區塊（```` ```...``` ````）主動繞過保護，絕不產生雙重跳脫，原生 Markdown 標點跳脫亦正常運作。
11. **JTrac NG 全新膠囊標籤向量 Logo 與「輕量級知識查詢系統」頁首升級 (JTrac NG Capsule Logo & Lightweight Knowledge Query System)**：
    - **全向量「JTrac [NG]」品牌 Logo**：正式退役 15 年前老舊之點陣 GIF，換上全新向量 SVG 品牌識別（深藍色 `JTrac` 搭配電光青藍漸層膠囊底色之白色 `NG` 標章），零鋸齒、支援 Retina 高解析螢幕與暗亮色主題自適應，並同步產出高解析透明 PNG 與相容 GIF 確保舊版歷史路徑 100% 向後相容。
    - **頁首說明文字精準定位**：將原預設之「JTrac - Open Source Issue Tracking System」全面升級為「`Lightweight Knowledge Query System`」（輕量級知識查詢系統），徹底消除繁瑣之 `&` 實體跳脫問題，左側 Logo 醒目標記品牌、右側文字專注系統價值，零冗餘且大氣俐落。
    - **行動端緊湊響應式排版與左靠間距優化 (Mobile Compact Logo & Tight Left-Aligned Header)**：針對智慧型手機與行動裝置（`<= 768px` 與 `<= 480px`），將 Logo 尺寸自適應縮減為高度 26px（小型手機 22px，最大寬度 90-110px），徹底清除舊式 `width="20%"` 與硬編碼 `height="55px"` 限制，標頭文字緊臨 Logo 左靠排列，大幅釋放手機螢幕橫向可用寬度，徹底根除標題文字被過度擠壓或縱向折行之問題。

---

## 七、版本升級與相容性指引

- **資料庫升級**：本版本完全相容 2.3.3-2.0.0 資料庫結構，**無需執行任何資料庫結構遷移腳本**。
- **WAR 部署**：直接使用 `target/jtrac.war` 覆蓋現有伺服器之 `ROOT.war` 即可無縫升級。
- **相關文件**：
  - [JTrac AI 郵件查詢與 Prompt 實戰範例指南](../llm/PROMPT_EXAMPLES_zh-TW.md)
  - [上一版本發布說明 (2.3.3-2.0.0)](release-2.3.3-2.0.0_zh-TW.md)
