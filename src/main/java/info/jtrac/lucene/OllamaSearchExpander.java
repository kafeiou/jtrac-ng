package info.jtrac.lucene;

import info.jtrac.mail.OllamaClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Service providing bidirectional Traditional/Simplified Chinese conversion
 * and cross-strait IT/business technical synonym expansion via Ollama LLM.
 *
 * Features:
 * 1. Fast Hanzi detection: pure Latin/alphanumeric queries bypass Ollama with 0ms latency.
 * 2. In-memory LRU Cache: common search terms resolve instantly (&lt; 1ms).
 * 3. Circuit Breaker / Fail-Safe: If Ollama is offline or times out (default 6s),
 *    search queries transparently fall back without errors, and a 30s backoff prevents
 *    blocking subsequent queries.
 */
public class OllamaSearchExpander {

    private static final Logger logger = LoggerFactory.getLogger(OllamaSearchExpander.class);

    public static final String SYSTEM_PROMPT =
            "You are a professional Chinese language and cross-strait terminology converter for search query expansion.\n" +
            "For any given Chinese search query:\n" +
            "1. Convert it directly between Traditional and Simplified Chinese characters (e.g., 專案 <-> 专案, 項目 <-> 项目, 網路 <-> 网络, 記憶體 <-> 内存/記憶體, 程式碼 <-> 程式码/代码).\n" +
            "2. Provide common cross-strait technical and business synonym equivalents (e.g., 專案 <-> 项目, 程式碼 <-> 代码, 記憶體 <-> 内存, 網路 <-> 网络, 軟體 <-> 软件, 伺服器 <-> 服务器, 螢幕 <-> 屏幕, 預設 <-> 默认, 使用者 <-> 用户, 登入 <-> 登录, 支援 <-> 支持, 資訊 <-> 信息, 權限 <-> 权限).\n" +
            "Output ONLY the converted terms and synonyms separated by single spaces.\n" +
            "Do NOT include any explanations, notes, punctuation, quotes, or Markdown formatting.";

    private Supplier<Map<String, String>> configSupplier;
    private OllamaClient ollamaClient;
    private int maxCacheSize = 1000;
    private volatile long lastFailureTime = 0;
    private long circuitBreakerBackoffMs = 30000; // 30s

    private final Map<String, List<String>> cache = Collections.synchronizedMap(
            new LinkedHashMap<String, List<String>>(128, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, List<String>> eldest) {
                    return size() > maxCacheSize;
                }
            }
    );

    public OllamaSearchExpander() {
    }

    public OllamaSearchExpander(Supplier<Map<String, String>> configSupplier) {
        this.configSupplier = configSupplier;
    }

    public void setConfigSupplier(Supplier<Map<String, String>> configSupplier) {
        this.configSupplier = configSupplier;
    }

    public Supplier<Map<String, String>> getConfigSupplier() {
        return configSupplier;
    }

    public void setOllamaClient(OllamaClient ollamaClient) {
        this.ollamaClient = ollamaClient;
    }

    public OllamaClient getOllamaClient() {
        return ollamaClient;
    }

    public void setMaxCacheSize(int maxCacheSize) {
        this.maxCacheSize = maxCacheSize > 0 ? maxCacheSize : 1000;
    }

    public int getMaxCacheSize() {
        return maxCacheSize;
    }

    public void setCircuitBreakerBackoffMs(long circuitBreakerBackoffMs) {
        this.circuitBreakerBackoffMs = circuitBreakerBackoffMs;
    }

    public long getCircuitBreakerBackoffMs() {
        return circuitBreakerBackoffMs;
    }

    public void clearCache() {
        cache.clear();
    }

    public int getCacheSize() {
        return cache.size();
    }

    public static boolean containsChinese(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            Character.UnicodeBlock ub = Character.UnicodeBlock.of(c);
            if (ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                    || ub == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                    || ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                    || ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B) {
                return true;
            }
        }
        return false;
    }

    public List<String> expandChineseVariants(String query) {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String trimmed = query.trim();
        if (!containsChinese(trimmed)) {
            return Collections.emptyList();
        }

        List<String> cached = cache.get(trimmed);
        if (cached != null) {
            return cached;
        }

        long now = System.currentTimeMillis();
        if (now - lastFailureTime < circuitBreakerBackoffMs) {
            logger.debug("Ollama circuit breaker active, skipping expansion for '{}'", trimmed);
            return Collections.emptyList();
        }

        Map<String, String> config = (configSupplier != null) ? configSupplier.get() : Collections.emptyMap();
        String enabledStr = config.get("llm.search.expansion.enabled");
        if ("false".equalsIgnoreCase(enabledStr)) {
            return Collections.emptyList();
        }

        OllamaClient client = this.ollamaClient;
        if (client == null) {
            String ollamaUrl = config.get("llm.ollama.url");
            String ollamaModel = config.get("llm.ollama.model");
            String ollamaApiKey = config.get("llm.ollama.api.key");
            int timeout = 6;
            String timeoutStr = config.get("llm.search.expansion.timeout");
            if (timeoutStr != null && !timeoutStr.trim().isEmpty()) {
                try {
                    timeout = Integer.parseInt(timeoutStr.trim());
                } catch (NumberFormatException ignored) {
                }
            }
            client = new OllamaClient(ollamaUrl, ollamaModel, ollamaApiKey, timeout);
        }

        try {
            String response = client.chat(SYSTEM_PROMPT, trimmed);
            List<String> variants = parseVariants(trimmed, response);
            cache.put(trimmed, variants);
            lastFailureTime = 0;
            return variants;
        } catch (Exception e) {
            lastFailureTime = System.currentTimeMillis();
            logger.warn("Ollama search query expansion failed for '{}': {}", trimmed, e.getMessage());
            return Collections.emptyList();
        }
    }

    public List<String> parseVariants(String originalQuery, String response) {
        if (response == null || response.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String cleaned = response.replaceAll("[`'\"\\*\\[\\]\\(\\)\\.,;:?\\!/\n\r\t]+", " ");
        String[] tokens = cleaned.split("\\s+");
        Set<String> set = new LinkedHashSet<String>();
        String lowerOriginal = originalQuery.trim().toLowerCase();

        for (String token : tokens) {
            String t = token.trim();
            if (t.isEmpty() || t.equalsIgnoreCase(lowerOriginal)) {
                continue;
            }
            if (containsChinese(t)) {
                set.add(t);
            }
        }
        return new ArrayList<String>(set);
    }
}
