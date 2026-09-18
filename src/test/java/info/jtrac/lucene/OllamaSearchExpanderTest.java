package info.jtrac.lucene;

import info.jtrac.mail.OllamaClient;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class OllamaSearchExpanderTest {

    private OllamaSearchExpander expander;
    private Map<String, String> config;

    @Before
    public void setUp() {
        config = new HashMap<String, String>();
        config.put("llm.search.expansion.enabled", "true");
        config.put("llm.search.expansion.timeout", "6");
        config.put("llm.ollama.url", "http://localhost:11434");
        config.put("llm.ollama.model", "llama3.2");

        expander = new OllamaSearchExpander(() -> config);
    }

    @Test
    public void testContainsChinese() {
        assertTrue(OllamaSearchExpander.containsChinese("專案"));
        assertTrue(OllamaSearchExpander.containsChinese("项目"));
        assertTrue(OllamaSearchExpander.containsChinese("Bug 專案管理 123"));
        assertTrue(OllamaSearchExpander.containsChinese("網路"));
        assertTrue(OllamaSearchExpander.containsChinese("网络"));

        assertFalse(OllamaSearchExpander.containsChinese(null));
        assertFalse(OllamaSearchExpander.containsChinese(""));
        assertFalse(OllamaSearchExpander.containsChinese("windows"));
        assertFalse(OllamaSearchExpander.containsChinese("EFC-109"));
        assertFalse(OllamaSearchExpander.containsChinese("NullPointerException"));
    }

    @Test
    public void testEnglishQueryBypassesOllama() {
        final AtomicInteger callCount = new AtomicInteger(0);
        OllamaClient mockClient = new OllamaClient(null, null, null, 6) {
            @Override
            public String chat(String systemPrompt, String userPrompt) {
                callCount.incrementAndGet();
                return "anything";
            }
        };
        expander.setOllamaClient(mockClient);

        List<String> result = expander.expandChineseVariants("windows deployment");
        assertTrue(result.isEmpty());
        assertEquals(0, callCount.get());
    }

    @Test
    public void testParseVariantsTraditionalToSimplifiedAndSynonyms() {
        String original = "專案";
        String mockResponse = "专案 项目 項目";

        List<String> variants = expander.parseVariants(original, mockResponse);
        assertEquals(3, variants.size());
        assertTrue(variants.contains("专案")); // Direct Simplified character conversion
        assertTrue(variants.contains("项目")); // Cross-strait terminology synonym
        assertTrue(variants.contains("項目"));
        assertFalse(variants.contains("專案")); // Original word excluded
    }

    @Test
    public void testParseVariantsSimplifiedToTraditionalAndSynonyms() {
        String original = "项目";
        String mockResponse = "項目 專案 专案";

        List<String> variants = expander.parseVariants(original, mockResponse);
        assertEquals(3, variants.size());
        assertTrue(variants.contains("項目")); // Direct Traditional character conversion
        assertTrue(variants.contains("專案")); // Cross-strait terminology synonym
        assertTrue(variants.contains("专案"));
        assertFalse(variants.contains("项目"));
    }

    @Test
    public void testCacheHitAvoidsRedundantCalls() {
        final AtomicInteger callCount = new AtomicInteger(0);
        OllamaClient mockClient = new OllamaClient(null, null, null, 6) {
            @Override
            public String chat(String systemPrompt, String userPrompt) {
                callCount.incrementAndGet();
                return "专案 项目";
            }
        };
        expander.setOllamaClient(mockClient);

        List<String> res1 = expander.expandChineseVariants("專案");
        assertEquals(2, res1.size());
        assertEquals(1, callCount.get());

        // Second call for the same query resolves directly from in-memory cache
        List<String> res2 = expander.expandChineseVariants("專案");
        assertEquals(2, res2.size());
        assertEquals(1, callCount.get());
    }

    @Test
    public void testCircuitBreakerGracefullyHandlesOfflineOrTimeout() {
        final AtomicInteger callCount = new AtomicInteger(0);
        OllamaClient mockClient = new OllamaClient(null, null, null, 6) {
            @Override
            public String chat(String systemPrompt, String userPrompt) throws IOException {
                callCount.incrementAndGet();
                throw new IOException("Connection refused to Ollama server at http://localhost:11434");
            }
        };
        expander.setOllamaClient(mockClient);
        expander.setCircuitBreakerBackoffMs(5000); // 5s backoff for testing

        // First call fails, returns empty list without error
        List<String> res1 = expander.expandChineseVariants("專案");
        assertTrue(res1.isEmpty());
        assertEquals(1, callCount.get());

        // Second call immediately within backoff window is blocked by circuit breaker
        List<String> res2 = expander.expandChineseVariants("網絡");
        assertTrue(res2.isEmpty());
        assertEquals(1, callCount.get()); // Still 1, did not attempt to call Ollama
    }

    @Test
    public void testDisabledViaConfiguration() {
        config.put("llm.search.expansion.enabled", "false");
        final AtomicInteger callCount = new AtomicInteger(0);
        OllamaClient mockClient = new OllamaClient(null, null, null, 6) {
            @Override
            public String chat(String systemPrompt, String userPrompt) {
                callCount.incrementAndGet();
                return "专案 项目";
            }
        };
        expander.setOllamaClient(mockClient);

        List<String> result = expander.expandChineseVariants("專案");
        assertTrue(result.isEmpty());
        assertEquals(0, callCount.get());
    }
}
