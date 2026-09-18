package info.jtrac.domain;

import org.junit.Test;

import static org.junit.Assert.*;

public class InboundConfigTest {

    @Test
    public void testInboundAndOllamaParamsRegistered() {
        assertTrue(Config.getParams().contains("mail.inbound.enabled"));
        assertTrue(Config.getParams().contains("mail.inbound.server.host"));
        assertTrue(Config.getParams().contains("mail.inbound.server.port"));
        assertTrue(Config.getParams().contains("mail.inbound.username"));
        assertTrue(Config.getParams().contains("mail.inbound.password"));
        assertTrue(Config.getParams().contains("mail.inbound.ssl.enable"));
        assertTrue(Config.getParams().contains("mail.inbound.starttls.enable"));
        assertTrue(Config.getParams().contains("mail.inbound.ssl.trust.all"));

        assertTrue(Config.getParams().contains("llm.ollama.url"));
        assertTrue(Config.getParams().contains("llm.ollama.model"));
        assertTrue(Config.getParams().contains("llm.ollama.api.key"));
        assertTrue(Config.getParams().contains("llm.ollama.timeout"));
        assertTrue(Config.getParams().contains("llm.retrieval.max_tickets"));
        assertTrue(Config.getParams().contains("llm.search.expansion.enabled"));
        assertTrue(Config.getParams().contains("llm.search.expansion.timeout"));
    }

    @Test
    public void testBooleanAndNumberTypes() {
        assertTrue(Config.isBoolean("mail.inbound.enabled"));
        assertTrue(Config.isBoolean("mail.inbound.ssl.enable"));
        assertTrue(Config.isBoolean("mail.inbound.starttls.enable"));
        assertTrue(Config.isBoolean("mail.inbound.ssl.trust.all"));
        assertTrue(Config.isBoolean("llm.search.expansion.enabled"));
        assertFalse(Config.isBoolean("mail.inbound.server.host"));

        assertTrue(Config.isNumber("mail.inbound.server.port"));
        assertTrue(Config.isNumber("llm.ollama.timeout"));
        assertTrue(Config.isNumber("llm.retrieval.max_tickets"));
        assertTrue(Config.isNumber("llm.search.expansion.timeout"));
        assertFalse(Config.isNumber("llm.ollama.model"));
    }

    @Test
    public void testConfigCategoryHelpers() {
        Config mailInbound = new Config("mail.inbound.server.host", "imap.test.com");
        assertTrue(mailInbound.isMailConfig());
        assertFalse(mailInbound.isLlmConfig());

        Config ollamaUrl = new Config("llm.ollama.url", "http://localhost:11434");
        assertFalse(ollamaUrl.isMailConfig());
        assertTrue(ollamaUrl.isLlmConfig());

        Config retrievalLimit = new Config("llm.retrieval.max_tickets", "50");
        assertFalse(retrievalLimit.isMailConfig());
        assertTrue(retrievalLimit.isLlmConfig());
    }

    @Test
    public void testMultilingualPropertiesPresent() throws Exception {
        String[] bundles = new String[] {
            "/messages.properties",
            "/messages_en.properties",
            "/messages_zh_TW.properties",
            "/messages_zh_CN.properties",
            "/messages_es.properties",
            "/messages_de.properties",
            "/messages_fr.properties",
            "/messages_ja.properties",
            "/messages_vi.properties"
        };
        String[] requiredKeys = new String[] {
            "config.mail.inbound.enabled",
            "config.mail.inbound.server.host",
            "config.mail.inbound.server.port",
            "config.mail.inbound.username",
            "config.mail.inbound.password",
            "config.mail.inbound.ssl.enable",
            "config.mail.inbound.starttls.enable",
            "config.mail.inbound.ssl.trust.all",
            "config.llm.ollama.url",
            "config.llm.ollama.model",
            "config.llm.ollama.api.key",
            "config.llm.ollama.timeout",
            "config.llm.retrieval.max_tickets",
            "config.llm.search.expansion.enabled",
            "config.llm.search.expansion.timeout",
            "config.security.privacy.headers.enabled",
            "config.attachment.index.maxSizeMb",
            "config.attachment.index.maxChars",
            "mail.ai_query.subject_prefix",
            "mail.ai_query.offline_notice_subject",
            "mail.ai_query.offline_notice_body"
        };
        for (String bundle : bundles) {
            java.util.Properties props = new java.util.Properties();
            try (java.io.InputStream is = getClass().getResourceAsStream(bundle)) {
                assertNotNull("Bundle not found: " + bundle, is);
                props.load(is);
            }
            for (String key : requiredKeys) {
                assertTrue("Missing key '" + key + "' in " + bundle, props.containsKey(key));
                assertFalse("Empty key '" + key + "' in " + bundle, props.getProperty(key).trim().isEmpty());
            }
        }
    }
}

