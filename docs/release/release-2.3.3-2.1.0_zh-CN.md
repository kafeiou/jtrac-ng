# JTrac 发布说明 (Release Notes) - 2.3.3-2.1.0-beta

[English](release-2.3.3-2.1.0_en.md) | [繁體中文](release-2.3.3-2.1.0_zh-TW.md) | [简体中文](release-2.3.3-2.1.0_zh-CN.md) | [日本語](release-2.3.3-2.1.0_ja.md) | [Tiếng Việt](release-2.3.3-2.1.0_vi.md) | [Deutsch](release-2.3.3-2.1.0_de.md) | [Español](release-2.3.3-2.1.0_es.md) | [Français](release-2.3.3-2.1.0_fr.md)

---

[![Java](https://img.shields.io/badge/Java-11%20%7C%2017-orange.svg)](https://adoptium.net/)
[![Maven](https://img.shields.io/badge/Maven-3.9+-blue.svg)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](../../license.txt)
[![Version](https://img.shields.io/badge/Version-2.3.3--2.1.0--beta-orange.svg)](../../pom.xml)
[![Status](https://img.shields.io/badge/Status-Beta%20Preview-yellow.svg)](release-2.3.3-2.1.0_zh-CN.md)
[![Wicket](https://img.shields.io/badge/Wicket-9.16.0-blue.svg)](https://wicket.apache.org/)
[![Spring](https://img.shields.io/badge/Spring-5.3.37-brightgreen.svg)](https://spring.io/)

> [!NOTE]
> **当前状态：Beta 测试预览版 (Pre-release / Beta Preview) - 尚未正式封版**  
> 本文档为动态发布日志（Living Release Notes）。在 Beta 实测期间，所有后续的新增功能、参数微调与 Bug 修复将自动即时追加于此文档。

---

## 目录
1. [版本核心亮点概述](#一版本核心亮点概述)
2. [🤖 AI 智慧邮件查询秘书 (AI Query Copilot)](#二-ai-智慧邮件查询秘书-ai-query-copilot)
3. [📦 核心依赖组件升级与 Java 11 警告消除](#三-核心依赖组件升级与-java-11-警告消除)
4. [🎨 界面现代化、字号无障碍与主题切换](#四-界面现代化字号无障碍与主题切换)
5. [🛡️ 生产环境安全防护与防呆机制](#五-生产环境安全防护与防呆机制)
6. [⚙️ 系统配置与稳定性修复](#六-系统配置与稳定性修复)
7. [版本升级与兼容性指引](#七版本升级与兼容性指引)

---

## 一、版本核心亮点概述

JTrac 2.3.3-2.1.0-beta 在 2.0.0 核心现代化架构的基础上，引进了革命性的 **AI 智慧邮件查询秘书 (AI Query Copilot with Ollama)**、升级底层 XML 解析器彻底消除 Java 11 反射访问警告、大幅强化用户界面无障碍体验（四段式字号缩放与 A+++ 模式、三态深浅主题）、以及全方位的生产安全防护网。

---

## 二、🤖 AI 智慧邮件查询秘书 (AI Query Copilot)

1. **双阶段查询扩展与防注入隔离 (Two-Phase Query Expansion)**：
   - 整合本地或服务器端 Ollama LLM，自动分析用户来信主题与正文，提取中英文实体词汇与技术同义词。
   - 建立严格的 `<untrusted_user_query>` 安全沙箱隔离，防范 Prompt Injection 与恶意指令窃取。
2. **混合加权检索与双语命中加分 (Hybrid Weighted Retrieval)**：
   - 检索算法依据摘要（+3）、详情（+1）、留言（+1）、附件（+1）精准评分，并提供跨语言匹配加分（+5）。
   - 在 `config` 表注册全局参数 `llm.retrieval.max_tickets`（默认 50 条）。
3. **Map-Reduce 两阶段分治消化机制 (Map-Reduce Pipeline)**：
   - **Map 阶段（单工单消化）**：逐张消化候选工单历史讨论与附件内容（单文件抽取上限 10 万字符，支持 PDF, Word, Excel, TXT, LOG, CSV），输出中继分析至安全暂存区。
   - **Reduce 阶段（大局总结）**：统整所有工单精炼摘要，输出结构化三大区块：
     1. 核心解答摘要 (Executive Summary)
     2. 各工单关键发现与解法 (Key Findings & Resolution)
     3. 建议行动方案 (Next Actions & Recommendations)
   - 具备防呆机制与 `finally` 暂存目录保证销毁，零磁盘泄露风险。
4. **线上 14 天安全 Web 报告链接与离线 HTML 一键下载 (14-Day Expiring Web Report & Offline Download)**：
   - **彻底避开企业邮件网关拦截**：邮件正文不再夹带易被企业邮件网关（Exchange/Outlook/Gmail）阻挡之 `.html` 附件，改为提供安全超链接直连开启。
   - **14 天生命周期与每小时自动清理 (TTL Auto-Pruning)**：服务器安全存储报告 14 天，利用每小时调度自动销毁过期文件，维护成本为 0，磁盘容量恒定。
   - **100% 离线可用与一键下载**：报告顶部常驻操作栏，支持收件人一键下载离线 HTML 文件（`JTrac-AI-Report-[Date].html`）；下载之文件内嵌完整 Mermaid.js 引擎，断网隔离环境 100% 原生可用。
5. **完整多语言 Prompt 指南与 4 大实战范例**：
   - 建立 8 种语言实战指南 [`docs/llm/PROMPT_EXAMPLES_*.md`](../llm/PROMPT_EXAMPLES_zh-CN.md)。
6. **工单空间分组、ID 倒序排列与 100% 离线 Mermaid 流程图引擎 (Space Grouping, ID DESC & Offline Mermaid.js)**：
   - **空间分组与新到旧倒序 (Space-Grouped Sub-tables & ID DESC)**：全面重构 AI 邮件回复速览与 HTML 诊断报告，依授权项目空间（Space）划分独立子表格展示（附工单总数），各空间内部按工单编号（ID DESC）严格由新到旧倒序排列；邮件正文保持极简专业，不追加多余 Mermaid 警语。
   - **100% 离线纯本地 Mermaid.js 流程图引擎 (Air-gapped Offline Mermaid Rendering)**：将完整版 Mermaid.js (v10.9.1) 内嵌至 Classpath 并直接注入 HTML 报告，彻底摆脱外部 CDN 网络依赖；具备系统深浅色主题自适应（`prefers-color-scheme`）与语法错误容错降级机制，保证封闭隔离内网环境皆能完美浏览。
   - **双层级 Prompt 流程图硬约束 (Two-Tier Flowchart Prompts with Quote Guardrails)**：于 Map 阶段（单工单深入排查）及 Reduce 阶段（全局核心解答与行动方案）明确要求输出标准 `flowchart TD/LR` 流程图，并强制所有节点文本加上双引号防呆，避免特殊符号导致渲染中断。
7. **JTrac 封闭领域接地、综合评论外部知识标注与零命中防呆 (Data Grounding, External Knowledge Tagging & Secret Masking)**：
   - **JTrac 封闭领域接地原则 (Strict JTrac Context Grounding)**：全面约束 LLM 回答必须以授权 JTrac 工单与附件内容为唯一真实依据；单工单分析（Map）与工单关键发现（Reduce Section 2）100% 严禁引入外部未经佐证的推测。
   - **综合评论外部知识强制标注 (Mandatory External Knowledge Tag)**：仅在全局总结（Reduce Section 1 核心解答摘要）与 Section 3 建议行动方案中，允许在工单事证不足时辅以业界常识或通用指引，但**强制要求显式标注「（参考外部信息给予建议）」**（英文标签：`(Note: Recommended based on external reference knowledge)`、繁体中文：`（參考外部資訊給予建議）`），让用户一目了然建议的来源背景。
   - **零命中安全通知 (Zero-Hit Safe Notice)**：当用户查询在获授权的 Space 内查无任何匹配工单或附件时，系统立即拦截并发送结构化零命中通知邮件，列出当前授权 Space 清单与接地原则说明，绝不转交模型进行空想臆测，并将原始邮件自动清空。
   - **机敏凭证安全遮罩 (Confidential Secrets Masking)**：实现 `SensitiveDataMasker`，在 HTML 诊断报告组装时自动过滤密码（password）、Bearer Token、API Key、私钥区块及 URL 连接密码，全面替换为 `***` 遮罩保护，同时完整保留用户账号与工单 ID。
   - **推荐部署硬件与长上下文配置 (Recommended Hardware & 200K Context)**：于 Prompt 实战指南中明确规范硬件选型，推荐旗舰 GPU NVIDIA RTX 5090 (32GB VRAM) 与 `qwen2.5:32b`，并强制要求通过 Ollama Modelfile 配置 200K 长上下文窗口（`num_ctx 200000`）；严厉示警勿采用短上下文或小参数模型，杜绝数据截断导致分析失效。

---

## 三、📦 核心依赖组件升级与 Java 11 警告消除

1. **升级 `dom4j` 至 `2.1.4`**：
   - 将旧有 `dom4j:1.6.1`（发布于 2005 年）升级为最新版 `org.dom4j:dom4j:2.1.4`。
   - 修复 [`Metadata.java`](../../src/main/java/info/jtrac/domain/Metadata.java) 中泛型转换引起的编译警告。
   - 彻底消除了在 Apache Tomcat 9 与 JDK 11 环境下运行时的 `WARNING: An illegal reflective access operation has occurred` 告警。

---

## 四、🎨 界面现代化、字号无障碍与主题切换

1. **四段式字号循环无障碍模式 (Font Scaling)**：
   - 支持 100%（标准）、115%（舒适）、130%（清晰）以及全新的 **A+++ 超大字模式（145%）**。
   - 具备防闪烁 (Anti-FOUC) 机制与表格防破版安全保护，持久化至 `localStorage`。
2. **三态深浅主题切换 (Theme Switcher)**：
   - 支持系统跟随 (Auto)、浅色模式 (Light) 与深色模式 (Dark) 单图标一键循环切换。
3. **统一文字搜索栏与智慧跳转**：
   - 整合内嵌搜索提交按钮与分隔线，支持智慧 RefId 识别跳转与超级管理员全域跨项目搜索。
4. **移动端 (Mobile RWD) 深度适配**：
   - 新增移动端抽屉导航栏 (Drawer)。
   - 工单摘要显式携带工单编号，并提供工单历史详情 Bottom-Sheet 底部抽屉弹窗。
   - 移动端居中胶囊分页器，桌面端提供首末页跳转与总页数统计。

---

## 五、🛡️ 生产环境安全防护与防呆机制

1. **全局安全响应头防护 (Security Headers Filter)**：
   - 注入 `X-Frame-Options: SAMEORIGIN`、`X-Content-Type-Options: nosniff`、`Strict-Transport-Security` 等响应头。
2. **搜索引擎阻断指令 (`robots.txt`)**：
   - 部署默认 `robots.txt`，防止网络爬虫抓取内部敏感工单。
3. **安全防护与防呆机制**：
   - 项目空间 Guest 权限提示警告、查询参数白名单校验、表单防重复提交保护。

---

## 六、⚙️ 系统配置与稳定性修复

1. **Wicket i18n Debug 警告消除**：全语言补齐 `status.nullValid = `。
2. **配置页面布尔开关重构**：重构为高兼容性的 `IndicatingDropDownChoice`。
3. **数据库驱动显式注册**：强化特定轻量环境下的数据库连通稳定性。
4. **附件 UTF-8 编码自动检测**：注入 Charset Header，杜绝中文乱码。
5. **Context-Relative Logo 路径解析**：修复反向代理环境下的 Logo 路径显示。
6. **Docker 构建脚本防呆与 Git 同步指引 (Docker Build Tips & Sync Guidance)**：
   - 于 `docker/build.bat` 与 `docker/build.sh` 终端加入启动提示，提醒编译前遇到 Tag 冲突或文件变动时之一键同步命令（`git fetch --tags -f && git reset --hard origin/master`）。
   - 于 `docker/` 提供完整 8 语系构建说明文件，追加常见问题与 Tag 冲突排除指引。

---

## 七、版本升级与兼容性指引

- **数据库升级**：本版本完全兼容 2.3.3-2.0.0 数据库结构，**无需执行任何数据库迁移脚本**。
- **WAR 部署**：直接使用 `target/jtrac.war` 覆盖现有容器之 `ROOT.war` 即可。
- **相关链接**：
  - [JTrac AI 邮件查询与 Prompt 实战范例指南](../llm/PROMPT_EXAMPLES_zh-CN.md)
  - [上一版本发布说明 (2.3.3-2.0.0)](release-2.3.3-2.0.0_zh-CN.md)
