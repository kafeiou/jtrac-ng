package info.jtrac.mail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.mail.util.MailSSLSocketFactory;
import info.jtrac.Jtrac;
import info.jtrac.domain.History;
import info.jtrac.domain.Item;
import info.jtrac.domain.ItemSearch;
import info.jtrac.domain.User;
import info.jtrac.util.AttachmentTextExtractor;
import info.jtrac.util.AttachmentUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import javax.mail.Address;
import javax.mail.BodyPart;
import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.internet.InternetAddress;
import javax.mail.search.FlagTerm;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service to poll and receive inbound inquiry emails from an IMAP mailbox,
 * authenticate sender permissions, query authorized tickets and attachments,
 * synthesize answers via local/cloud Ollama, send HTML replies, and purge processed messages.
 */
public class InboundMailReceiver {

    private static final Logger logger = LoggerFactory.getLogger(InboundMailReceiver.class);
    private static final Pattern REF_ID_PATTERN = Pattern.compile("(?i)\\b([A-Z0-9]+)-([0-9]+)\\b");
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Set<String> INJECTION_STOPWORDS = new HashSet<>(Arrays.asList(
            "system", "prompt", "instruction", "instructions", "ignore", "jailbreak",
            "untrusted_user_query", "untrusted_ticket_context", "roleplay", "assistant",
            "password", "secret", "override", "bypass", "drop", "delete", "insert", "update"
    ));

    private static final Map<String, List<String>> CROSS_STRAIT_TERM_MAP = new HashMap<>();

    static {
        // High-frequency cross-strait IT and business terminology bidirectional dictionary
        addCrossStraitTermPair(CROSS_STRAIT_TERM_MAP, "發票", "发票", "开票", "invoice");
        addCrossStraitTermPair(CROSS_STRAIT_TERM_MAP, "專案", "项目", "专案", "project");
        addCrossStraitTermPair(CROSS_STRAIT_TERM_MAP, "程式碼", "程式", "代码", "程序", "code");
        addCrossStraitTermPair(CROSS_STRAIT_TERM_MAP, "記憶體", "内存", "memory", "ram");
        addCrossStraitTermPair(CROSS_STRAIT_TERM_MAP, "伺服器", "服务器", "server");
        addCrossStraitTermPair(CROSS_STRAIT_TERM_MAP, "網路", "网络", "network");
        addCrossStraitTermPair(CROSS_STRAIT_TERM_MAP, "欄位", "字段", "field");
        addCrossStraitTermPair(CROSS_STRAIT_TERM_MAP, "檔案", "文件", "file");
        addCrossStraitTermPair(CROSS_STRAIT_TERM_MAP, "資料夾", "文件夹", "folder", "directory");
        addCrossStraitTermPair(CROSS_STRAIT_TERM_MAP, "排程", "定时任务", "计划任务", "schedule", "cron");
        addCrossStraitTermPair(CROSS_STRAIT_TERM_MAP, "使用者", "用户", "user");
        addCrossStraitTermPair(CROSS_STRAIT_TERM_MAP, "登入", "登录", "login", "signin");
        addCrossStraitTermPair(CROSS_STRAIT_TERM_MAP, "預設", "默认", "default");
        addCrossStraitTermPair(CROSS_STRAIT_TERM_MAP, "資訊", "信息", "info", "information");
        addCrossStraitTermPair(CROSS_STRAIT_TERM_MAP, "軟體", "软件", "software");
        addCrossStraitTermPair(CROSS_STRAIT_TERM_MAP, "硬體", "硬件", "hardware");
        addCrossStraitTermPair(CROSS_STRAIT_TERM_MAP, "訊息", "消息", "message");
        addCrossStraitTermPair(CROSS_STRAIT_TERM_MAP, "權限", "权限", "permission", "privilege");
        addCrossStraitTermPair(CROSS_STRAIT_TERM_MAP, "資料庫", "数据库", "database");
        addCrossStraitTermPair(CROSS_STRAIT_TERM_MAP, "開立", "开具", "issue");
        addCrossStraitTermPair(CROSS_STRAIT_TERM_MAP, "報表", "报表", "report");
        addCrossStraitTermPair(CROSS_STRAIT_TERM_MAP, "測試", "测试", "test");
        addCrossStraitTermPair(CROSS_STRAIT_TERM_MAP, "異常", "异常", "exception");
        addCrossStraitTermPair(CROSS_STRAIT_TERM_MAP, "錯誤", "错误", "error", "bug");
        addCrossStraitTermPair(CROSS_STRAIT_TERM_MAP, "查詢", "查询", "query");
        addCrossStraitTermPair(CROSS_STRAIT_TERM_MAP, "搜尋", "搜索", "search");
        addCrossStraitTermPair(CROSS_STRAIT_TERM_MAP, "匯入", "导入", "import");
        addCrossStraitTermPair(CROSS_STRAIT_TERM_MAP, "匯出", "导出", "export");
        addCrossStraitTermPair(CROSS_STRAIT_TERM_MAP, "更新", "更新", "update");
        addCrossStraitTermPair(CROSS_STRAIT_TERM_MAP, "刪除", "删除", "delete");
        addCrossStraitTermPair(CROSS_STRAIT_TERM_MAP, "新增", "添加", "create", "add");
        addCrossStraitTermPair(CROSS_STRAIT_TERM_MAP, "備份", "备份", "backup");
        addCrossStraitTermPair(CROSS_STRAIT_TERM_MAP, "還原", "还原", "restore");
        addCrossStraitTermPair(CROSS_STRAIT_TERM_MAP, "快取", "缓存", "cache");
        addCrossStraitTermPair(CROSS_STRAIT_TERM_MAP, "連結", "链接", "link");
        addCrossStraitTermPair(CROSS_STRAIT_TERM_MAP, "設定", "设置", "config", "setting");
        addCrossStraitTermPair(CROSS_STRAIT_TERM_MAP, "憑證", "证书", "certificate");
        addCrossStraitTermPair(CROSS_STRAIT_TERM_MAP, "金鑰", "密钥", "key");
        addCrossStraitTermPair(CROSS_STRAIT_TERM_MAP, "工單", "工单", "ticket", "issue");
        addCrossStraitTermPair(CROSS_STRAIT_TERM_MAP, "電子發票", "电子发票", "einvoice");
    }

    private static void addCrossStraitTermPair(Map<String, List<String>> map, String... terms) {
        for (String t1 : terms) {
            String k = t1.trim().toLowerCase();
            List<String> list = map.computeIfAbsent(k, key -> new ArrayList<>());
            for (String t2 : terms) {
                if (!t1.equalsIgnoreCase(t2) && !list.contains(t2)) {
                    list.add(t2);
                }
            }
        }
    }

    public static Set<String> parseStopwords(String stopwordsStr) {
        Set<String> set = new HashSet<>();
        if (StringUtils.hasText(stopwordsStr)) {
            String[] tokens = stopwordsStr.split("[,;\\s]+");
            for (String t : tokens) {
                if (StringUtils.hasText(t)) {
                    String trimmed = t.trim().toLowerCase();
                    set.add(trimmed);
                    if ("通知".equals(trimmed)) {
                        set.add("notice");
                        set.add("notification");
                    }
                    if ("工單".equals(trimmed) || "工单".equals(trimmed)) {
                        set.add("工單");
                        set.add("工单");
                        set.add("ticket");
                        set.add("issue");
                    }
                }
            }
        }
        return set;
    }

    public static List<String> expandCrossStraitTerms(Collection<String> inputKeywords, Set<String> stopwords) {
        List<String> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (inputKeywords == null) {
            return result;
        }
        for (String kw : inputKeywords) {
            if (!StringUtils.hasText(kw)) {
                continue;
            }
            String trimmed = kw.trim();
            String lower = trimmed.toLowerCase();
            if (stopwords != null && stopwords.contains(lower)) {
                continue;
            }
            if (seen.add(lower)) {
                result.add(trimmed);
            }
            List<String> synonyms = CROSS_STRAIT_TERM_MAP.get(lower);
            if (synonyms != null) {
                for (String syn : synonyms) {
                    String synLower = syn.trim().toLowerCase();
                    if ((stopwords == null || !stopwords.contains(synLower)) && seen.add(synLower)) {
                        result.add(syn);
                    }
                }
            }
        }
        return result;
    }

    private final Jtrac jtrac;
    private final MailSender mailSender;

    public InboundMailReceiver(Jtrac jtrac, MailSender mailSender) {
        this.jtrac = jtrac;
        this.mailSender = mailSender;
    }

    /**
     * Poll the IMAP inbox for unread inquiry messages and process them.
     *
     * @return count of processed messages
     */
    public int receiveAndProcess() {
        Map<String, String> config = jtrac.loadAllConfig();
        if (!"true".equalsIgnoreCase(config.get("mail.inbound.enabled"))) {
            logger.debug("Inbound mail query service is disabled (mail.inbound.enabled != true)");
            return 0;
        }

        String host = config.get("mail.inbound.server.host");
        String username = config.get("mail.inbound.username");
        String password = config.get("mail.inbound.password");
        if (!StringUtils.hasText(host) || !StringUtils.hasText(username)) {
            logger.warn("Inbound mail service is enabled, but server host or username is not configured.");
            return 0;
        }

        boolean sslEnable = "true".equalsIgnoreCase(config.get("mail.inbound.ssl.enable"));
        boolean starttlsEnable = "true".equalsIgnoreCase(config.get("mail.inbound.starttls.enable"));
        boolean sslTrustAll = "true".equalsIgnoreCase(config.get("mail.inbound.ssl.trust.all"));

        int port = 993;
        String portStr = config.get("mail.inbound.server.port");
        if (StringUtils.hasText(portStr)) {
            try {
                port = Integer.parseInt(portStr.trim());
            } catch (NumberFormatException e) {
                port = sslEnable ? 993 : 143;
            }
        } else {
            port = sslEnable ? 993 : 143;
        }

        String protocol = sslEnable ? "imaps" : "imap";
        Properties props = new Properties();
        props.put("mail.store.protocol", protocol);
        props.put("mail." + protocol + ".host", host);
        props.put("mail." + protocol + ".port", String.valueOf(port));
        props.put("mail." + protocol + ".timeout", "30000");
        props.put("mail." + protocol + ".connectiontimeout", "30000");

        if (starttlsEnable) {
            props.put("mail." + protocol + ".starttls.enable", "true");
        }

        if (sslTrustAll) {
            props.put("mail." + protocol + ".ssl.trust", "*");
            props.put("mail." + protocol + ".ssl.checkserveridentity", "false");
            try {
                MailSSLSocketFactory sf = new MailSSLSocketFactory();
                sf.setTrustAllHosts(true);
                props.put("mail." + protocol + ".ssl.socketFactory", sf);
            } catch (GeneralSecurityException gse) {
                logger.warn("Could not set MailSSLSocketFactory for trusting all certificates: " + gse.getMessage());
            }
        }

        Store store = null;
        Folder inbox = null;
        int processedCount = 0;

        try {
            logger.debug("Connecting to IMAP mailbox at {}:{} ({}) as {}", host, port, protocol, username);
            Session session = Session.getInstance(props);
            store = session.getStore(protocol);
            store.connect(host, port, username, password != null ? password : "");

            inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_WRITE);

            Message[] messages = inbox.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false));
            logger.info("Found {} unread messages in IMAP mailbox", messages.length);

            for (Message msg : messages) {
                try {
                    processSingleMessage(msg, config);
                    processedCount++;
                } catch (Exception e) {
                    logger.error("Error processing inbound message: " + e.getMessage(), e);
                }
            }

            // Close and purge messages marked DELETED
            inbox.close(true);
            inbox = null;
            store.close();
            store = null;
        } catch (Exception e) {
            logger.error("IMAP mail receiver connection error: " + e.getMessage(), e);
        } finally {
            if (inbox != null && inbox.isOpen()) {
                try {
                    inbox.close(false);
                } catch (Exception ignored) {
                }
            }
            if (store != null && store.isConnected()) {
                try {
                    store.close();
                } catch (Exception ignored) {
                }
            }
        }

        return processedCount;
    }

    /**
     * Processes a single inbound email message.
     */
    protected void processSingleMessage(Message msg, Map<String, String> config) throws Exception {
        Address[] froms = msg.getFrom();
        if (froms == null || froms.length == 0) {
            logger.warn("Inbound email has no sender, marking as SEEN");
            msg.setFlag(Flags.Flag.SEEN, true);
            return;
        }

        String senderEmail = null;
        if (froms[0] instanceof InternetAddress) {
            senderEmail = ((InternetAddress) froms[0]).getAddress();
        } else {
            senderEmail = froms[0].toString();
        }

        if (!StringUtils.hasText(senderEmail)) {
            logger.warn("Could not determine sender email address, marking SEEN");
            msg.setFlag(Flags.Flag.SEEN, true);
            return;
        }
        senderEmail = senderEmail.trim().toLowerCase();

        // 1. Authenticate sender against JTrac user database
        User user = jtrac.findUserByEmail(senderEmail);
        if (user == null || user.isLocked()) {
            logger.warn("Inbound email from unregistered or locked sender [{}], marking SEEN and ignoring.", senderEmail);
            msg.setFlag(Flags.Flag.SEEN, true);
            return;
        }

        Locale userLocale = (user.getLocale() != null)
                ? StringUtils.parseLocaleString(user.getLocale())
                : StringUtils.parseLocaleString(jtrac.getDefaultLocale());

        String subject = msg.getSubject() != null ? msg.getSubject() : "";
        String body = extractMessageContent(msg);

        logger.info("Processing authorized inquiry from user {} [{}] with subject '{}'", user.getLoginName(), senderEmail, subject);

        // Prepare Ollama client configuration
        String ollamaUrl = config.get("llm.ollama.url");
        String ollamaModel = config.get("llm.ollama.model");
        String ollamaApiKey = config.get("llm.ollama.api.key");
        int timeout = 60;
        String timeoutStr = config.get("llm.ollama.timeout");
        if (StringUtils.hasText(timeoutStr)) {
            try {
                timeout = Integer.parseInt(timeoutStr.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        OllamaClient ollamaClient = new OllamaClient(ollamaUrl, ollamaModel, ollamaApiKey, timeout);

        int maxTickets = 50;
        String maxTicketsStr = config.get("llm.retrieval.max_tickets");
        if (StringUtils.hasText(maxTicketsStr)) {
            try {
                maxTickets = Integer.parseInt(maxTicketsStr.trim());
            } catch (NumberFormatException ignored) {
            }
        }

        int maxAgeDays = 365;
        String maxAgeDaysStr = config.get("llm.retrieval.max_age_days");
        if (StringUtils.hasText(maxAgeDaysStr)) {
            try {
                maxAgeDays = Integer.parseInt(maxAgeDaysStr.trim());
            } catch (NumberFormatException ignored) {
            }
        }

        // Read stopwords from config (default "通知,工單")
        String stopwordsStr = config.get("llm.retrieval.stopwords");
        if (stopwordsStr == null) {
            stopwordsStr = "通知,工單";
        }
        Set<String> stopwords = parseStopwords(stopwordsStr);

        // 2. Phase 1: LLM-driven query expansion (with graceful heuristic fallback)
        List<String> keywords = extractKeywordsWithLlm(ollamaClient, subject, body, stopwords);
        logger.info("Extracted query expansion keywords (excluding stopwords {}): {}", stopwords, keywords);

        // 3. Phase 2: Programmatic hybrid weighted retrieval & ranking (select top 10~15 candidates)
        List<Item> contextItems = retrieveAuthorizedTickets(user, subject, body, keywords, maxTickets, maxAgeDays);
        contextItems = MailSender.sortItemsBySpaceAndIdDesc(contextItems);

        // Guardrail: If no matching tickets found in authorized spaces, send zero-hit notice and do NOT hallucinate
        if (contextItems == null || contextItems.isEmpty()) {
            logger.info("No matching tickets found for query from {} in authorized spaces. Sending zero-hit notice.", senderEmail);
            mailSender.sendAiZeroHitNotice(senderEmail, subject, userLocale, user.getSpaces());
            msg.setFlag(Flags.Flag.DELETED, true);
            return;
        }

        // 4. Phase 3: Map Phase - Per-ticket deep ingestion and intermediate staging
        File stagingFile = null;
        String stagedDigest = null;
        Map<String, String> perTicketSummaries = new LinkedHashMap<>();
        try {
            File stagingDir = new File(jtrac.getJtracHome(), "temp/ai_staging");
            if (!stagingDir.exists()) {
                stagingDir.mkdirs();
            }
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS").format(new Date());
            stagingFile = new File(stagingDir, "jtrac_staging_" + user.getLoginName() + "_" + timestamp + ".md");

            stagedDigest = processTicketsToStaging(ollamaClient, contextItems, subject, body, stagingFile, perTicketSummaries, keywords);
        } catch (Exception e) {
            logger.warn("Failed during ticket staging creation: " + e.getMessage() + ", proceeding with raw context");
        }

        // 5. Phase 4: Reduce Phase - Final synthesis & beautification
        String aiResponse = null;
        try {
            String reduceSysPrompt = OllamaPromptBuilder.buildFinalSynthesisSystemPrompt();
            String reduceUserPrompt = OllamaPromptBuilder.buildFinalSynthesisUserPrompt(subject, body, stagedDigest);
            aiResponse = ollamaClient.chat(reduceSysPrompt, reduceUserPrompt);
        } catch (Exception e) {
            logger.error("Ollama query failed for sender " + senderEmail + ": " + e.getMessage(), e);
            mailSender.sendAiOfflineNotice(senderEmail, subject, userLocale);
            msg.setFlag(Flags.Flag.DELETED, true);
            return;
        } finally {
            // Clean up intermediate staging file
            if (stagingFile != null && stagingFile.exists()) {
                try {
                    stagingFile.delete();
                } catch (Exception ignored) {
                }
            }
        }

        // 6. Send formatted HTML response and mark original message for deletion
        mailSender.sendAiQueryResponse(senderEmail, subject, aiResponse, contextItems, perTicketSummaries, userLocale, user.getSpaces(), keywords);
        msg.setFlag(Flags.Flag.DELETED, true);
        logger.info("Successfully answered query from {} and marked original message for deletion", senderEmail);
    }

    /**
     * Phase 3 (Map Phase): Individually digests each retrieved ticket (along with full comment history
     * and extracted attachment contents), writes structured analysis into the intermediate staging file,
     * and returns the complete staged digest content.
     */
    protected String processTicketsToStaging(OllamaClient ollamaClient, List<Item> items, String querySubject, String queryBody, File stagingFile) {
        return processTicketsToStaging(ollamaClient, items, querySubject, queryBody, stagingFile, null, null);
    }

    protected String processTicketsToStaging(OllamaClient ollamaClient, List<Item> items, String querySubject, String queryBody, File stagingFile, Map<String, String> perTicketSummaries) {
        return processTicketsToStaging(ollamaClient, items, querySubject, queryBody, stagingFile, perTicketSummaries, null);
    }

    protected String processTicketsToStaging(OllamaClient ollamaClient, List<Item> items, String querySubject, String queryBody, File stagingFile, Map<String, String> perTicketSummaries, List<String> keywords) {
        StringBuilder stagingBuffer = new StringBuilder();
        stagingBuffer.append("# JTrac AI Query Staging Digest\n\n");
        stagingBuffer.append("- Inquiry Subject: ").append(querySubject != null ? querySubject : "").append("\n");
        if (keywords != null && !keywords.isEmpty()) {
            stagingBuffer.append("- Extracted & Expanded Keywords: ").append(keywords).append("\n");
        }
        stagingBuffer.append("- Total Analyzed Tickets: ").append(items != null ? items.size() : 0).append("\n\n");

        if (items != null && !items.isEmpty()) {
            int index = 1;
            for (Item item : items) {
                enrichItemWithAttachmentText(item);

                logger.debug("Ingesting ticket [{}] for Map phase...", item.getRefId());
                String singleTicketSysPrompt = OllamaPromptBuilder.buildSingleTicketSummarySystemPrompt();
                String singleTicketUserPrompt = OllamaPromptBuilder.buildSingleTicketSummaryUserPrompt(item, querySubject, queryBody);

                String summaryResult = null;
                try {
                    summaryResult = ollamaClient.chat(singleTicketSysPrompt, singleTicketUserPrompt);
                } catch (Exception ex) {
                    logger.warn("Per-ticket LLM analysis failed for [{}], falling back to raw fields: {}", item.getRefId(), ex.getMessage());
                    summaryResult = buildFallbackTicketSummary(item);
                }

                if (perTicketSummaries != null && item.getRefId() != null && summaryResult != null) {
                    perTicketSummaries.put(item.getRefId(), summaryResult.trim());
                }

                stagingBuffer.append("## Ticket #").append(index++).append(": [").append(item.getRefId()).append("] - ");
                stagingBuffer.append(item.getSummary() != null ? item.getSummary() : "").append("\n");
                if (item.getSpace() != null) {
                    stagingBuffer.append("Space: ").append(item.getSpace().getName()).append(" (").append(item.getSpace().getPrefixCode()).append(") | ");
                }
                stagingBuffer.append("Status: ").append(OllamaPromptBuilder.safeGetStatusValue(item)).append("\n\n");
                stagingBuffer.append(summaryResult != null ? summaryResult.trim() : "No summary generated.").append("\n\n");
                stagingBuffer.append("---\n\n");
            }
        } else {
            stagingBuffer.append("No matching tickets found within the user's authorized spaces.\n");
        }

        String stagedContent = stagingBuffer.toString();

        if (stagingFile != null) {
            try {
                File parent = stagingFile.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                Files.write(stagingFile.toPath(), stagedContent.getBytes(StandardCharsets.UTF_8));
            } catch (Exception ex) {
                logger.warn("Failed writing staging file [{}]: {}", stagingFile.getAbsolutePath(), ex.getMessage());
            }
        }

        return stagedContent;
    }

    /**
     * Resilient fallback summarizing basic ticket fields if single-ticket LLM analysis fails.
     */
    protected String buildFallbackTicketSummary(Item item) {
        StringBuilder sb = new StringBuilder();
        sb.append("- **Core Problem / Subject**: ").append(item.getSummary() != null ? item.getSummary() : "N/A").append("\n");
        sb.append("- **Resolution / Progress**: Current status is ").append(OllamaPromptBuilder.safeGetStatusValue(item)).append(". ");
        if (item.getDetail() != null && !item.getDetail().trim().isEmpty()) {
            sb.append(item.getDetail().trim().replaceAll("\\s+", " "));
        }
        sb.append("\n");
        sb.append("- **Attachment Findings**: ");
        if (item.getHistory() != null) {
            StringBuilder attNames = new StringBuilder();
            for (History h : item.getHistory()) {
                if (h.getAttachment() != null) {
                    if (attNames.length() > 0) {
                        attNames.append(", ");
                    }
                    attNames.append(h.getAttachment().getFileName());
                }
            }
            if (attNames.length() > 0) {
                sb.append("Attached files: ").append(attNames).append("\n");
            } else {
                sb.append("None\n");
            }
        } else {
            sb.append("None\n");
        }
        sb.append("- **Relevance & Key Takeaway**: Relevant ticket retrieved by search.\n");
        return sb.toString();
    }

    /**
     * Phase 1: Invokes LLM to extract keywords in the query's original language and English translation/synonyms.
     * Defensively parses JSON, applies stopword filtering, expands cross-strait vocabulary,
     * and falls back to heuristic tokenization if LLM is unavailable or fails.
     */
    protected List<String> extractKeywordsWithLlm(OllamaClient ollamaClient, String subject, String body) {
        return extractKeywordsWithLlm(ollamaClient, subject, body, parseStopwords("通知,工單"));
    }

    protected List<String> extractKeywordsWithLlm(OllamaClient ollamaClient, String subject, String body, Set<String> stopwords) {
        try {
            String systemPrompt = OllamaPromptBuilder.buildKeywordExtractionSystemPrompt(stopwords);
            String userPrompt = OllamaPromptBuilder.buildKeywordExtractionUserPrompt(subject, body);
            String response = ollamaClient.chat(systemPrompt, userPrompt);
            List<String> rawKeywords = parseKeywordsFromJson(response, stopwords);
            if (rawKeywords != null && !rawKeywords.isEmpty()) {
                List<String> expanded = expandCrossStraitTerms(rawKeywords, stopwords);
                if (!expanded.isEmpty()) {
                    return expanded;
                }
            }
        } catch (Exception e) {
            logger.warn("LLM keyword extraction failed or timed out, falling back to heuristic extraction: {}", e.getMessage());
        }
        List<String> heuristicRaw = extractHeuristicKeywords(subject, body, stopwords);
        return expandCrossStraitTerms(heuristicRaw, stopwords);
    }

    /**
     * Parses the LLM JSON response {"keywords": ["kw1", "kw2", ...]} safely with Jackson.
     */
    protected List<String> parseKeywordsFromJson(String response) {
        return parseKeywordsFromJson(response, null);
    }

    protected List<String> parseKeywordsFromJson(String response, Set<String> stopwords) {
        if (!StringUtils.hasText(response)) {
            return Collections.emptyList();
        }
        try {
            String jsonStr = response.trim();
            if (jsonStr.contains("```json")) {
                int start = jsonStr.indexOf("```json") + 7;
                int end = jsonStr.indexOf("```", start);
                if (end > start) {
                    jsonStr = jsonStr.substring(start, end).trim();
                }
            } else if (jsonStr.contains("```")) {
                int start = jsonStr.indexOf("```") + 3;
                int end = jsonStr.indexOf("```", start);
                if (end > start) {
                    jsonStr = jsonStr.substring(start, end).trim();
                }
            }

            int firstBrace = jsonStr.indexOf('{');
            int lastBrace = jsonStr.lastIndexOf('}');
            if (firstBrace >= 0 && lastBrace > firstBrace) {
                jsonStr = jsonStr.substring(firstBrace, lastBrace + 1);
            }

            JsonNode root = objectMapper.readTree(jsonStr);
            JsonNode keywordsNode = root.get("keywords");
            if (keywordsNode != null && keywordsNode.isArray()) {
                List<String> result = new ArrayList<>();
                Set<String> seen = new HashSet<>();
                for (JsonNode item : keywordsNode) {
                    if (item != null && item.isTextual()) {
                        String kw = item.asText().trim();
                        kw = kw.replaceAll("^[\\\"'\\s.,;]+|[\\\"'\\s.,;]+$", "").trim();
                        if (isValidKeyword(kw, stopwords) && seen.add(kw.toLowerCase())) {
                            result.add(kw);
                        }
                    }
                }
                return result;
            }
        } catch (Exception e) {
            logger.debug("Failed to parse keywords JSON from LLM: {}", e.getMessage());
        }
        return Collections.emptyList();
    }

    /**
     * Validates and sanitizes individual keyword candidate.
     */
    protected static boolean isValidKeyword(String kw) {
        return isValidKeyword(kw, null);
    }

    protected static boolean isValidKeyword(String kw, Set<String> stopwords) {
        if (!StringUtils.hasText(kw)) {
            return false;
        }
        String trimmed = kw.trim();
        if (trimmed.length() < 1 || trimmed.length() > 50) {
            return false;
        }
        if (trimmed.contains("\n") || trimmed.contains("\r") || trimmed.contains("<") || trimmed.contains(">") || trimmed.contains("{") || trimmed.contains("}")) {
            return false;
        }
        String lower = trimmed.toLowerCase();
        if (INJECTION_STOPWORDS.contains(lower)) {
            return false;
        }
        if (stopwords != null && stopwords.contains(lower)) {
            return false;
        }
        return true;
    }

    /**
     * Heuristic tokenization fallback extracting keywords from subject and body.
     */
    protected List<String> extractHeuristicKeywords(String subject, String body) {
        return extractHeuristicKeywords(subject, body, parseStopwords("通知,工單"));
    }

    protected List<String> extractHeuristicKeywords(String subject, String body, Set<String> stopwords) {
        List<String> keywords = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        String cleanSubject = "";
        if (StringUtils.hasText(subject)) {
            cleanSubject = subject.replaceAll("(?i)\\b(re|fwd|fw):\\s*", "").trim();
        }

        if (StringUtils.hasText(cleanSubject)) {
            for (String token : cleanSubject.split("[\\s,;:.!?，。！？\\[\\]()（）/\\\\]+")) {
                String t = token.replaceAll("^[\\\"'\\s.,;]+|[\\\"'\\s.,;]+$", "").trim();
                if (isValidKeyword(t, stopwords) && seen.add(t.toLowerCase())) {
                    keywords.add(t);
                }
                if (stopwords != null) {
                    for (String sw : stopwords) {
                        if (t.length() > sw.length() && t.contains(sw)) {
                            String stripped = t.replace(sw, "").trim();
                            if (isValidKeyword(stripped, stopwords) && seen.add(stripped.toLowerCase())) {
                                keywords.add(stripped);
                            }
                        }
                    }
                }
            }
        }

        if (StringUtils.hasText(body)) {
            String[] lines = body.split("\n");
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.length() > 2 && !trimmed.startsWith(">") && !trimmed.startsWith("--")) {
                    for (String token : trimmed.split("[\\s,;:.!?，。！？\\[\\]()（）/\\\\]+")) {
                        String t = token.replaceAll("^[\\\"'\\s.,;]+|[\\\"'\\s.,;]+$", "").trim();
                        if (isValidKeyword(t, stopwords) && seen.add(t.toLowerCase())) {
                            keywords.add(t);
                            if (keywords.size() >= 10) {
                                break;
                            }
                        }
                        if (stopwords != null) {
                            for (String sw : stopwords) {
                                if (t.length() > sw.length() && t.contains(sw)) {
                                    String stripped = t.replace(sw, "").trim();
                                    if (isValidKeyword(stripped, stopwords) && seen.add(stripped.toLowerCase())) {
                                        keywords.add(stripped);
                                    }
                                }
                            }
                        }
                    }
                    if (keywords.size() >= 10) {
                        break;
                    }
                }
            }
        }

        return keywords;
    }

    /**
     * Backward-compatible ticket retrieval delegating to expanded retrieval.
     */
    public List<Item> retrieveAuthorizedTickets(User user, String subject, String body) {
        return retrieveAuthorizedTickets(user, subject, body, extractHeuristicKeywords(subject, body), 50);
    }

    /**
     * Backward-compatible ticket retrieval delegating to retrieval with configured maxAgeDays.
     */
    public List<Item> retrieveAuthorizedTickets(User user, String subject, String body, List<String> keywords, int maxTickets) {
        int maxAgeDays = 365;
        if (jtrac != null) {
            try {
                String maxAgeStr = jtrac.loadConfig("llm.retrieval.max_age_days");
                if (StringUtils.hasText(maxAgeStr)) {
                    maxAgeDays = Integer.parseInt(maxAgeStr.trim());
                }
            } catch (Exception ignored) {
            }
        }
        return retrieveAuthorizedTickets(user, subject, body, keywords, maxTickets, maxAgeDays);
    }

    /**
     * Phase 2: Programmatic hybrid weighted retrieval & ranking.
     * Searches up to maxTickets via Lucene, filters candidates older than maxAgeDays (exempting exact RefIds),
     * scores candidates based on keyword hits, rewards bilingual matches, and returns the top 15 tickets enriched with attachment content.
     * When maxAgeDays <= 0, time-window filtering is disabled.
     */
    public List<Item> retrieveAuthorizedTickets(User user, String subject, String body, List<String> keywords, int maxTickets, int maxAgeDays) {
        if (maxTickets <= 0) {
            maxTickets = 50;
        }

        Set<Long> matchedIds = new HashSet<>();
        Set<Long> exactRefIds = new HashSet<>();
        List<ItemCandidate> candidates = new ArrayList<>();

        // 1. Match explicit ticket reference IDs in subject or body (e.g. PROJ-123)
        // Explicit RefId matches bypass time-window filtering
        String combinedText = subject + " " + (body != null ? body : "");
        Matcher matcher = REF_ID_PATTERN.matcher(combinedText);
        while (matcher.find()) {
            String refId = matcher.group(0).toUpperCase();
            try {
                Item item = jtrac.loadItemByRefId(refId);
                if (item != null && (user.isSuperUser() || user.isAllocatedToSpace(item.getSpace().getId()))) {
                    if (matchedIds.add(item.getId())) {
                        exactRefIds.add(item.getId());
                        candidates.add(new ItemCandidate(item, 100)); // Base 100 for exact RefId match
                    }
                }
            } catch (Exception ignored) {
            }
        }

        // 2. Lucene full-text search across authorized spaces using expanded keywords
        String searchQuery;
        if (keywords != null && !keywords.isEmpty()) {
            List<String> queryTerms = new ArrayList<>();
            for (String kw : keywords) {
                if (!StringUtils.hasText(kw)) {
                    continue;
                }
                String t = kw.trim();
                if (info.jtrac.lucene.OllamaSearchExpander.containsChinese(t) && t.length() >= 2 && !t.startsWith("\"")) {
                    queryTerms.add("\"" + t + "\"");
                } else {
                    queryTerms.add(t);
                }
            }
            searchQuery = String.join(" ", queryTerms);
        } else {
            searchQuery = extractSearchKeywords(subject, body);
        }
        if (StringUtils.hasText(searchQuery)) {
            ItemSearch itemSearch = new ItemSearch(user);
            itemSearch.setPageSize(maxTickets);
            itemSearch.setSearchText(searchQuery);

            List<Item> searchItems = jtrac.findItems(itemSearch);
            if (searchItems != null) {
                long cutoffMillis = (maxAgeDays > 0) ? (System.currentTimeMillis() - ((long) maxAgeDays * 86400000L)) : 0L;
                for (Item item : searchItems) {
                    if (maxAgeDays > 0 && !exactRefIds.contains(item.getId())) {
                        Date lastActivity = getLastActivityTimestamp(item);
                        if (lastActivity == null || lastActivity.getTime() < cutoffMillis) {
                            logger.debug("Filtered out ticket [{}] due to maxAgeDays constraint ({} days)", item.getRefId(), maxAgeDays);
                            continue;
                        }
                    }
                    if (matchedIds.add(item.getId())) {
                        candidates.add(new ItemCandidate(item, 0));
                    }
                }
            }
        }

        // 3. Score and rank candidates
        List<String> effectiveKeywords = (keywords != null && !keywords.isEmpty())
                ? keywords
                : extractHeuristicKeywords(subject, body);

        for (ItemCandidate candidate : candidates) {
            calculateRelevanceScore(candidate, effectiveKeywords);
        }

        // Sort descending by score
        candidates.sort((a, b) -> Integer.compare(b.score, a.score));

        // Filter out candidates with score <= 0 (unless exact RefId match)
        List<ItemCandidate> scoredCandidates = new ArrayList<>();
        for (ItemCandidate candidate : candidates) {
            if (candidate.score > 0 || exactRefIds.contains(candidate.item.getId())) {
                scoredCandidates.add(candidate);
            }
        }

        // 4. Select top candidates (up to 15) for LLM context, and enrich with attachment text
        int topLimit = Math.min(15, scoredCandidates.size());
        List<Item> resultItems = new ArrayList<>(topLimit);
        for (int i = 0; i < topLimit; i++) {
            Item item = scoredCandidates.get(i).item;
            enrichItemWithAttachmentText(item);
            resultItems.add(item);
        }

        return resultItems;
    }

    /**
     * Calculates the latest activity timestamp of an item, taking the maximum of
     * the item's creation timestamp and all history/comment timestamps.
     */
    public Date getLastActivityTimestamp(Item item) {
        if (item == null) {
            return null;
        }
        Date last = item.getTimeStamp();
        if (item.getHistory() != null) {
            for (History h : item.getHistory()) {
                if (h != null && h.getTimeStamp() != null) {
                    if (last == null || h.getTimeStamp().after(last)) {
                        last = h.getTimeStamp();
                    }
                }
            }
        }
        return last;
    }

    protected void calculateRelevanceScore(ItemCandidate candidate, List<String> keywords) {
        if (candidate == null || candidate.item == null || keywords == null || keywords.isEmpty()) {
            return;
        }
        Item item = candidate.item;
        String summary = item.getSummary() != null ? item.getSummary().toLowerCase() : "";
        String detail = item.getDetail() != null ? item.getDetail().toLowerCase() : "";

        StringBuilder commentsBuilder = new StringBuilder();
        StringBuilder attachmentTextBuilder = new StringBuilder();
        if (item.getHistory() != null) {
            for (History h : item.getHistory()) {
                if (h.getComment() != null) {
                    commentsBuilder.append(" ").append(h.getComment().toLowerCase());
                }
                if (h.getAttachmentText() != null) {
                    attachmentTextBuilder.append(" ").append(h.getAttachmentText().toLowerCase());
                }
            }
        }
        String comments = commentsBuilder.toString();
        String attachments = attachmentTextBuilder.toString();

        boolean hasAsciiMatch = false;
        boolean hasNonAsciiMatch = false;

        for (String kw : keywords) {
            if (!StringUtils.hasText(kw)) {
                continue;
            }
            String kwLower = kw.trim().toLowerCase();
            boolean matched = false;

            if (summary.contains(kwLower)) {
                candidate.score += 3;
                matched = true;
            }
            if (detail.contains(kwLower)) {
                candidate.score += 1;
                matched = true;
            }
            if (comments.contains(kwLower)) {
                candidate.score += 1;
                matched = true;
            }
            if (attachments.contains(kwLower)) {
                candidate.score += 1;
                matched = true;
            }

            if (matched) {
                if (isPureAscii(kwLower)) {
                    hasAsciiMatch = true;
                } else {
                    hasNonAsciiMatch = true;
                }
            }
        }

        // Mixed-language bonus: reward cross-lingual matches across original language and English
        if (hasAsciiMatch && hasNonAsciiMatch) {
            candidate.score += 5;
        }
    }

    private static boolean isPureAscii(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) > 127) {
                return false;
            }
        }
        return true;
    }

    protected static class ItemCandidate {
        final Item item;
        int score;

        public ItemCandidate(Item item, int score) {
            this.item = item;
            this.score = score;
        }

        public Item getItem() {
            return item;
        }

        public int getScore() {
            return score;
        }
    }

    private void enrichItemWithAttachmentText(Item item) {
        if (jtrac == null || item.getHistory() == null || item.getHistory().isEmpty()) {
            return;
        }
        boolean hasAttachmentNeedingText = false;
        for (History h : item.getHistory()) {
            if (h.getAttachment() != null && (!StringUtils.hasText(h.getAttachmentText()))) {
                hasAttachmentNeedingText = true;
                break;
            }
        }
        if (!hasAttachmentNeedingText) {
            return;
        }

        OllamaClient ocrClient = null;
        boolean ocrEnabled = false;
        try {
            ocrEnabled = jtrac.isAttachmentOcrEnabled();
        } catch (Throwable ignored) {}

        if (ocrEnabled) {
            String ollamaUrl = jtrac.loadConfig("llm.ollama.url", "http://localhost:11434");
            String ollamaModel = jtrac.loadConfig("llm.ollama.model", "llama3.2");
            String ollamaApiKey = jtrac.loadConfig("llm.ollama.api.key");
            int timeout = 60;
            String timeoutStr = jtrac.loadConfig("llm.ollama.timeout");
            if (StringUtils.hasText(timeoutStr)) {
                try {
                    timeout = Integer.parseInt(timeoutStr.trim());
                } catch (NumberFormatException ignored) {}
            }
            ocrClient = new OllamaClient(ollamaUrl, ollamaModel, ollamaApiKey, timeout);
        }
        for (History h : item.getHistory()) {
            if (h.getAttachment() != null && (!StringUtils.hasText(h.getAttachmentText()))) {
                try {
                    File file = AttachmentUtils.getFile(h.getAttachment(), item.getSpace().getId(), jtrac.getJtracHome());
                    if (file != null && file.exists()) {
                        String ocrModel = null;
                        try {
                            ocrModel = jtrac.getAttachmentOcrModel();
                        } catch (Throwable ignored) {}
                        int maxSizeMb = 10;
                        int maxChars = 50000;
                        try {
                            maxSizeMb = jtrac.getAttachmentIndexMaxSizeInMb();
                            maxChars = jtrac.getAttachmentIndexMaxChars();
                        } catch (Throwable ignored) {}
                        String text = AttachmentTextExtractor.extractText(file,
                                maxSizeMb,
                                maxChars,
                                ocrClient,
                                ocrModel);
                        h.setAttachmentText(text);
                    }
                } catch (Throwable t) {
                    logger.debug("Failed extracting text for attachment {} on ticket {}", h.getAttachment().getFileName(), item.getRefId());
                }
            }
        }
    }

    private String extractSearchKeywords(String subject, String body) {
        if (StringUtils.hasText(subject)) {
            String clean = subject.replaceAll("(?i)\\b(re|fwd):\\s*", "").trim();
            if (clean.length() > 0) {
                return clean;
            }
        }
        if (StringUtils.hasText(body)) {
            String[] lines = body.split("\n");
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.length() > 2 && !trimmed.startsWith(">") && !trimmed.startsWith("--")) {
                    return trimmed;
                }
            }
        }
        return "";
    }

    /**
     * Extracts text content from a JavaMail Message Part.
     */
    protected String extractMessageContent(Part part) throws Exception {
        Object content = part.getContent();
        if (content instanceof String) {
            String str = (String) content;
            if (part.isMimeType("text/html") || str.toLowerCase().contains("<html") || str.toLowerCase().contains("<body")) {
                return str.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();
            }
            return str;
        } else if (content instanceof Multipart) {
            Multipart mp = (Multipart) content;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < mp.getCount(); i++) {
                BodyPart bp = mp.getBodyPart(i);
                String partText = extractMessageContent(bp);
                if (partText != null && !partText.trim().isEmpty()) {
                    sb.append(partText).append("\n");
                }
            }
            return sb.toString().trim();
        }
        return "";
    }
}
