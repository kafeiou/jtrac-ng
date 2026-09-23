# JTrac NG Release Notes - 3.0.0-beta

[English](release-3.0.0-beta_en.md) | [繁體中文](release-3.0.0-beta_zh-TW.md) | [简体中文](release-3.0.0-beta_zh-CN.md) | [日本語](release-3.0.0-beta_ja.md) | [Tiếng Việt](release-3.0.0-beta_vi.md) | [Deutsch](release-3.0.0-beta_de.md) | [Español](release-3.0.0-beta_es.md) | [Français](release-3.0.0-beta_fr.md)

---

[![Java](https://img.shields.io/badge/Java-11%20%7C%2017-orange.svg)](https://adoptium.net/)
[![Maven](https://img.shields.io/badge/Maven-3.9+-blue.svg)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](../../license.txt)
[![Version](https://img.shields.io/badge/Version-3.0.0--beta-orange.svg)](../../pom.xml)
[![Status](https://img.shields.io/badge/Status-Beta%20Preview-yellow.svg)](release-3.0.0-beta_en.md)
[![Wicket](https://img.shields.io/badge/Wicket-9.16.0-blue.svg)](https://wicket.apache.org/)
[![Spring](https://img.shields.io/badge/Spring-5.3.37-brightgreen.svg)](https://spring.io/)

> [!NOTE]
> **Current Status: Pre-release / Beta Preview - Living Release Notes**  
> This document is a living release log. Throughout the Beta verification phase, any subsequent enhancements, configuration adjustments, and bug fixes will be continuously appended here.

---

## Table of Contents
1. [Core Highlights Overview](#1-core-highlights-overview)
2. [🤖 AI Query Copilot (Ollama Integration)](#2--ai-query-copilot-ollama-integration)
3. [📦 Dependency Upgrade & Java 11 Warning Elimination](#3--dependency-upgrade--java-11-warning-elimination)
4. [🎨 UI/UX Modernization & Accessibility Enhancements](#4--uiux-modernization--accessibility-enhancements)
5. [🛡️ Production Security Hardening & Guardrails](#5--production-security-hardening--guardrails)
6. [⚙️ System Configuration & Stability Fixes](#6--system-configuration--stability-fixes)
7. [Upgrade & Compatibility Guidance](#7-upgrade--compatibility-guidance)

---

## 1. Core Highlights Overview

Building upon the core modernization of 2.0.0, JTrac NG 3.0.0-beta introduces the revolutionary **AI Query Copilot (powered by Ollama)**, upgrades the foundational XML engine to eliminate Java 11 reflective access warnings, dramatically enhances UI accessibility (4-stage font scaling with A+++ mode and 3-state dark/light themes), and reinforces production security.

---

## 2. 🤖 AI Query Copilot (Ollama Integration)

1. **Two-Phase Query Expansion with Anti-Injection Safeguards**:
   - Integrates local or server-side Ollama LLMs to parse user email subjects and bodies, automatically extracting bilingual entity keywords and technical synonyms.
   - Enforces strict `<untrusted_user_query>` sandboxing to prevent Prompt Injection and unauthorized data leakage.
2. **Hybrid Weighted Retrieval & Scoring**:
   - Scores tickets across Summary (+3), Detail (+1), Comments (+1), and Attachments (+1), with a +5 bonus for cross-lingual English/localized matches.
   - Registers configurable global parameter `llm.retrieval.max_tickets` (default 50).
3. **Map-Reduce Ingestion Pipeline**:
   - **Map Phase**: Analyzes candidate tickets and document attachments (up to 100,000 characters per file; supports PDF, Word, Excel, TXT, LOG, CSV) into intermediate staging Markdown.
   - **Reduce Phase**: Synthesizes the staged summaries into three structured sections:
     1. Executive Summary
     2. Key Findings & Resolution
     3. Next Actions & Recommendations
   - Guaranteed `finally` cleanup of temporary staging files, ensuring zero disk leaks.
4. **14-Day Expiring Web Report Links & Offline HTML Download Support**:
   - **Zero Email Gateway Blocking**: Emails no longer carry `.html` attachments that are prone to corporate email firewall rejections (Exchange/Outlook/Gmail), replaced by safe direct links.
   - **14-Day Lifecycle & Automated Hourly Cleanup (TTL Auto-Pruning)**: The server retains reports for 14 days and automatically cleans up expired files via an hourly scheduler with zero maintenance overhead.
   - **100% Offline Usable with One-Click Download**: The report features a sticky action bar supporting one-click download (`JTrac-AI-Report-[Date].html`), embedding the full Mermaid.js engine for complete air-gapped offline use.
5. **Multilingual Prompt Engineering Guide & 4 Real-world Examples**:
   - Published comprehensive practical guides [`docs/llm/PROMPT_EXAMPLES_*.md`](../llm/PROMPT_EXAMPLES_en.md) across 8 languages.
6. **Ticket Space Grouping, ID DESC Sorting & 100% Air-gapped Offline Mermaid.js Integration**:
   - **Space-Grouped Sub-tables & Newest-First ID DESC Ordering**: Overhauled email summary tables and HTML dossier reports to group candidate tickets into individual sub-tables per authorized Space with ticket count headers. Tickets inside each Space are strictly sorted by ticket ID descending (`ID DESC`); email notifications remain clean and professional without noisy Mermaid disclaimers.
   - **100% Self-Contained Offline Mermaid.js Engine**: Packaged the full UMD bundle of Mermaid.js (v10.9.1) into the Java Classpath and directly embedded it inside generated HTML reports, completely eliminating external CDN network dependencies. Includes automatic dark/light theme switching (`prefers-color-scheme`) and try-catch syntax fallback rendering.
   - **Two-Tier Flowchart Prompts with Double-Quote Guardrails**: Enforced Mermaid `flowchart TD/LR` generation across both the Map phase (per-ticket troubleshooting workflows) and Reduce phase (executive summary & next actions), mandating double-quoted node labels (e.g. `A["Node text"]`) to prevent syntax breaks.
7. **Strict JTrac Data Grounding, Mandatory External Knowledge Tagging, Zero-Hit Notice & Secret Masking**:
   - **Strict JTrac Context Grounding**: Enforced that LLM responses must be grounded strictly and primarily on authorized JTrac tickets and document attachments. Individual ticket analysis (Map phase) and Key Findings (Reduce Section 2) are 100% prohibited from introducing outside speculation.
   - **Mandatory External Knowledge Tagging**: Only in general synthesis (Section 1 Executive Summary) and Section 3 Next Actions may the model supplement with industry best practices if internal tickets lack full remediation steps, and it **MUST explicitly append the tag `(Note: Recommended based on external reference knowledge)`** (`（參考外部資訊給予建議）`), making knowledge provenance completely transparent.
   - **Zero-Hit Safe Notice**: If a user's inquiry matches zero tickets or attachments across authorized spaces, the system intercepts immediately and sends a helpful zero-hit notice email with authorized spaces and data grounding policies, avoiding any LLM hallucination and purging the mailbox.
   - **Confidential Credentials Masking**: Implemented `SensitiveDataMasker` to automatically redact passwords, Bearer tokens, API keys, private key blocks, and URL credentials in generated HTML reports with `***`, while strictly preserving usernames and ticket IDs.
   - **Recommended Hardware & 200K Long Context (Hardware & Model Recommendations)**: Documented official hardware and model deployment guidelines recommending the flagship NVIDIA RTX 5090 (32GB VRAM) and `qwen2.5:32b`, with mandatory 200K context window configuration (`num_ctx 200000`) via Ollama Modelfile; strongly warned against underpowered or short-context models to eliminate truncation and analysis failure.

---

## 3. 📦 Dependency Upgrade & Java 11 Warning Elimination

1. **Upgrade `dom4j` to `2.1.4`**:
   - Upgraded legacy `dom4j:1.6.1` (from 2005) to `org.dom4j:dom4j:2.1.4`.
   - Fixed generic node casting in [`Metadata.java`](../../src/main/java/info/jtrac/domain/Metadata.java).
   - Completely resolved `WARNING: An illegal reflective access operation has occurred (org.dom4j.io.SAXContentHandler)` under Tomcat 9 / Java 11.

---

## 4. 🎨 UI/UX Modernization & Accessibility Enhancements

1. **4-Stage Font Scaling Cycle**:
   - Supports 100% (Standard), 115% (Comfortable), 130% (Crisp), and **A+++ Extra Large Mode (145%)**.
   - Includes Anti-FOUC script and table structure protections, persisting preferences via `localStorage`.
2. **3-State Theme Switcher**:
   - One-click seamless toggle between Auto, Light, and Dark modes using a single clean icon.
3. **Unified Search Box & Smart Navigation**:
   - Unified input box with embedded submit button, divider line, and enlarged tap target.
   - Smart RefId jump (typing `PROJ-123` navigates directly to the ticket), with superuser global search support.
4. **Mobile Responsive Experience (RWD)**:
   - Navigation drawer for mobile devices.
   - Explicit ticket IDs in summaries and Bottom-Sheet history detail modal.
   - Centered capsule pagination on mobile; desktop jump to first/last page and total page count.

---

## 5. 🛡️ Production Security Hardening & Guardrails

1. **Global Security Headers Filter**:
   - Injects `X-Frame-Options: SAMEORIGIN`, `X-Content-Type-Options: nosniff`, `Strict-Transport-Security`, `Content-Security-Policy`, and `Referrer-Policy`.
2. **Search Engine Shield (`robots.txt`)**:
   - Deploys default `robots.txt` disallowing search crawlers from indexing sensitive ticket data.
3. **Guardrails & Tamper Resistance**:
   - Space guest role warning.
   - Query parameter whitelist filtering.
   - Form double-click submission prevention.

---

## 6. ⚙️ System Configuration & Stability Fixes

1. **Wicket i18n Debug Warning Suppression**:
   - Added `status.nullValid = ` across all 8 language bundles.
2. **Config Page Boolean Control Modernization**:
   - Refactored brittle checkbox switches to robust `IndicatingDropDownChoice`.
3. **Explicit Database Driver Registration**:
   - Explicitly registers JDBC drivers for single-connection and lightweight test datasources.
4. **Automatic UTF-8 Text Attachment Detection**:
   - Detects text attachment encoding and injects charset headers to prevent garbled text.
5. **Context-Relative Logo Resolution**:
   - Resolves system header logo paths correctly behind reverse proxies.
6. **Docker Build Script Guidance & Git Tag Conflict Prevention**:
   - Added startup troubleshooting tips to `docker/build.bat` and `docker/build.sh` with one-click sync instructions (`git fetch --tags -f && git reset --hard origin/master`) for build machines encountering tag clobbering or local build diffs.
   - Expanded `docker/` documentation across all 8 supported languages with troubleshooting guides for build environment synchronization.
7. **JTrac NG Rebranding & Semantic Versioning Leap (v3.0.0-beta)**:
   - Officially rebranded the project to **JTrac NG** (Next Generation) and bumped version to **3.0.0-beta**, eliminating 15-year-old search collisions with legacy JTrac 2.1.0/2.3.x and dramatically boosting Google SEO indexation.
   - Migrated official repository to [https://github.com/kafeiou/jtrac-ng](https://github.com/kafeiou/jtrac-ng), synchronizing web footers, mobile navigation bar, and Maven POM descriptors.
8. **Lucene Search & History Inspection Modernization (Subtokens, Wildcard Expansion & Show History Default)**:
   - **Sub-token Analyzer (`SubTokenFilter`)**: Splits emails (e.g. `user@gmail.com`) and compound filenames (e.g. `thunderbird_gmail.pdf`) into sub-tokens (`user`, `gmail`, `com`, `thunderbird`, `pdf`), enabling single-word searches like `gmail` to seamlessly match email addresses and attachment filenames.
   - **Smart Query Expansion & Leading Wildcard**: Automatically expands terms to `(term OR term*)`, activates `allowLeadingWildcard = true` for `*keyword*` queries, and enables Chinese phrase slop (`phraseSlop = 2`) so separated phrases like `申請帳號` match `申請開放帳號`.
   - **Clean Space Browsing & Search-Driven History Expansion**: Space browsing defaults to `showHistory = false` to present clean ticket lists (each ticket appears once as `EFC-109`), eliminating clutter from unsearched revision expansion. History view (`showHistory = true`) is dynamically enabled when users search keywords or open the advanced search form, and collapses back to false upon clearing search.
   - **Chronological History Ordering (Parent Ticket Before Revisions)**: Fixed sorting order when history view is active to sort by ticket ID descending and revision ID ascending (`parent.id DESC, id ASC`). This guarantees the main ticket (e.g. `xxx-109`) always precedes subsequent revision comments (e.g. `xxx-109(1)`), and maintains grouped chronological ordering when sorting by other columns.
   - **Smart History Filtering on Keyword Search**: When history view is enabled and a text search is performed, the system intelligently filters results to return only the primary ticket snapshot or specific revision comments that actually match the keyword, automatically hiding irrelevant revisions (e.g. status changes without matching text).
   - **Automated Startup Index Rebuild**: Detects analyzer version upgrade (`lucene.analyzer.version = 3.0.0-subtoken-v1`) and triggers asynchronous background reindexing upon container startup.
   - **Docker Hub Migration**: Updated Docker Hub image documentation across 8 languages to `kafeiou/jtrac-ng:latest`.
9. **Ollama Bidirectional Traditional/Simplified Chinese & Cross-Strait Synonym Search Expansion**:
   - **Complete Coverage of Literal Variants and Cross-Strait Terminology**: When searching with Chinese queries, the system automatically invokes Ollama for bidirectional expansion covering both literal character variants (Traditional "專案" <-> Simplified "专案") and cross-strait IT/business technical synonyms ("專案" <-> Mainland "项目"; "程式碼" <-> "代码"; "記憶體" <-> "内存"; "網路" <-> "网络"; "軟體" <-> "软件"; "伺服器" <-> "服务器"; "預設" <-> "默认"; "使用者" <-> "用户"; "登入" <-> "登录"), combining into composite Lucene queries (e.g. `((專案 OR 專案*) OR (专案 OR 专案*) OR (项目 OR 项目*))`) to achieve seamless bilingual retrieval.
   - **Zero Overhead for Alphanumeric Queries**: Queries without Chinese characters (e.g., `EFC-109`, `login`) bypass Ollama completely (0ms latency).
   - **Ultra-Fast LRU Memory Cache**: A thread-safe LRU cache (capacity: 1,000 items) ensures repeat queries resolve instantly (< 1ms).
   - **Timeout Control (Default: 6s) & Fail-Safe Circuit Breaker**: Introduced `llm.search.expansion.enabled` (default: true) and `llm.search.expansion.timeout` (default: 6 seconds); if Ollama is offline or times out, the search gracefully falls back without interruption, and a 30s circuit breaker prevents subsequent queries from waiting.
10. **Markdown Windows UNC Path & Backslash Preservation**:
    - **Root Cause Fix**: Resolved an issue where entering Windows UNC network share paths (e.g. `\\hlmt.com.tw\SysVol\hlmt.com.tw\Policies\{9CECF8CB-B752-4E7C-A1FB-90CB3B021A07}\User\Scripts\Logon` or `"\\hlmt.com.tw\..."`) in item details or comments caused leading double backslashes `\\` to be collapsed into a single backslash `\` and backslashes before braces `\{` to be stripped due to CommonMark ASCII punctuation escaping rules.
    - **100% Natural Text Rendering**: Rendered as normal body text without forcing inline code chip styles or font variations, ensuring seamless visual harmony with surrounding content.
    - **Comprehensive Coverage & Code Block Immunity**: Full coverage for UNC network shares (`\\server\share`), local drive paths (`C:\...`), relative paths, and standalone double backslashes (`\\`); automatically bypasses code spans (`` `...` ``) and fenced code blocks (```` ```...``` ````) to avoid double-escaping, while preserving standard Markdown punctuation escaping.
11. **JTrac NG Capsule Vector Logo & "Lightweight Knowledge Query System" Header Modernization**:
    - **Full-Vector "JTrac [NG]" Brand Logo**: Retired the 15-year-old legacy raster GIF and introduced a crisp vector SVG brand identity featuring deep royal blue `JTrac` paired with an electric cyan-to-azure pill capsule badge encasing bold white `NG`. Crisp on Retina displays, theme-aware for light and dark modes, with transparent PNG and legacy GIF fallbacks for 100% backward compatibility.
    - **Header Purpose Clarification**: Upgraded the default header text from the legacy "JTrac - Open Source Issue Tracking System" to "`Lightweight Knowledge Query System`", eliminating cumbersome `&` entity escaping while establishing JTrac NG's identity as an AI-powered knowledge discovery and query engine.
    - **Mobile Compact Logo & Tight Left-Aligned Header**: For mobile devices (`<= 768px` and `<= 480px`), the logo scales responsively to a compact height of 26px (22px on small phones, max-width 90-110px). Obsolete table constraints (`width="20%"` and fixed `height="55px"`) are removed, placing the header subtitle snugly against the logo to maximize horizontal screen real estate and prevent unnatural vertical text wrapping.

---

## 7. Upgrade & Compatibility Guidance

- **Database Upgrade**: Fully compatible with 2.3.3-2.0.0; **no schema migration script is required**.
- **WAR Deployment**: Replace the existing `ROOT.war` with `target/jtrac.war`.
- **Related Links**:
  - [JTrac AI Email Query & Prompt Writing Practical Guide](../llm/PROMPT_EXAMPLES_en.md)
  - [Previous Release Notes (2.3.3-2.0.0)](release-2.3.3-2.0.0_en.md)
