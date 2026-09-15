package info.jtrac.mail;

import info.jtrac.domain.Item;
import info.jtrac.domain.Space;
import org.junit.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

public class MailSenderAiTest {

    @Test
    public void testSendAiQueryResponseDoesNotThrow() {
        Map<String, String> config = new HashMap<>();
        config.put("mail.server.host", "localhost");
        config.put("mail.from", "jtrac@example.com");
        config.put("jtrac.url.base", "http://localhost/jtrac/");

        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");

        MailSender mailSender = new MailSender(config, messageSource, "en");

        Space space = new Space();
        space.setId(1L);
        space.setPrefixCode("PROJ");
        space.setName("Project Alpha");

        Item item = new Item();
        item.setSpace(space);
        item.setSequenceNum(42);
        item.setSummary("Critical DB deadlock");
        item.setStatus(1);

        List<Item> items = new ArrayList<>();
        items.add(item);

        Set<Space> spaces = new HashSet<>();
        spaces.add(space);

        Map<String, String> perTicketSummaries = new HashMap<>();
        perTicketSummaries.put("PROJ-42", "LLM Staged summary for ticket PROJ-42");

        // Test with perTicketSummaries overload
        mailSender.sendAiQueryResponse("user@example.com", "Help needed",
                "Based on the analysis, [PROJ-42] caused the deadlock.", items, perTicketSummaries, Locale.ENGLISH, spaces);

        // Test Traditional Chinese
        mailSender.sendAiQueryResponse("user@example.com", "請問資料庫狀況",
                "根據分析，工單 [PROJ-42] 造成鎖死情況。", items, perTicketSummaries, Locale.TAIWAN, spaces);

        // Test null safety
        mailSender.sendAiQueryResponse(null, "Subject", "Content", Collections.emptyList(), Locale.ENGLISH, Collections.emptySet());
        mailSender.sendAiQueryResponse("no", "Subject", "Content", Collections.emptyList(), Locale.ENGLISH, Collections.emptySet());
    }

    @Test
    public void testBuildStandaloneHtmlReportContentAndStyles() {
        Map<String, String> config = new HashMap<>();
        config.put("mail.server.host", "localhost");
        config.put("mail.from", "jtrac@example.com");
        config.put("jtrac.url.base", "http://localhost/jtrac/");

        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");

        MailSender mailSender = new MailSender(config, messageSource, "en");

        Space space = new Space();
        space.setId(1L);
        space.setPrefixCode("PROJ");
        space.setName("Project Alpha");

        Item item = new Item();
        item.setSpace(space);
        item.setSequenceNum(42);
        item.setSummary("Database Connection Timeout");
        item.setDetail("PostgreSQL connection pool exhausted under load.");
        item.setStatus(1);

        info.jtrac.domain.History h = new info.jtrac.domain.History();
        h.setTimeStamp(new java.util.Date());
        h.setComment("Increased max_connections to 200.");
        h.setStatus(2);
        Set<info.jtrac.domain.History> historySet = new HashSet<>();
        historySet.add(h);
        item.setHistory(historySet);

        info.jtrac.domain.Attachment att = new info.jtrac.domain.Attachment();
        att.setFileName("postgres-log-dump.txt");
        Set<info.jtrac.domain.Attachment> attSet = new HashSet<>();
        attSet.add(att);
        item.setAttachments(attSet);

        List<Item> items = Collections.singletonList(item);
        Set<Space> spaces = Collections.singleton(space);

        Map<String, String> summaries = new HashMap<>();
        summaries.put("PROJ-42", "The DB connection pool was exhausted due to unclosed sessions.");

        String report = mailSender.buildStandaloneHtmlReport("DB Performance Issues",
                "### Root Cause\nPool exhaustion caused timeouts.\n\n| Param | Value |\n|---|---|\n| Pool | HikariCP |",
                items, summaries, Locale.TAIWAN, spaces, new java.util.Date());

        assertNotNull(report);
        assertTrue("Must have HTML5 doctype", report.contains("<!DOCTYPE html>"));
        assertTrue("Must have table border CSS", report.contains("border-collapse: collapse"));
        assertTrue("Must have border rule", report.contains("border: 1px solid"));
        assertTrue("Must support dark mode", report.contains("@media (prefers-color-scheme: dark)"));
        assertTrue("Must support print media", report.contains("@media print"));
        assertTrue("Must render markdown table", report.contains("<th>Param</th>") || report.contains("HikariCP"));
        assertTrue("Must include ticket summary", report.contains("Database Connection Timeout"));
        assertTrue("Must include ticket ref id", report.contains("PROJ-42"));
        assertTrue("Must include ticket description", report.contains("PostgreSQL connection pool exhausted"));
        assertTrue("Must include history comments", report.contains("Increased max_connections to 200."));
        assertTrue("Must include attachment name", report.contains("postgres-log-dump.txt"));
        assertTrue("Must include per-ticket digest", report.contains("The DB connection pool was exhausted"));
        assertTrue("Must use details accordion", report.contains("<details class='ticket-card'>"));
        assertTrue("Must have space block styling", report.contains(".space-block"));
        assertTrue("Must have space header styling", report.contains(".space-header"));
        assertTrue("Must have mermaid wrapper styling", report.contains(".mermaid-wrapper"));
        assertTrue("Must embed mermaid script tag", report.contains("<script id=\"mermaid-core-js\">"));
        assertTrue("Must initialize mermaid with theme detection", report.contains("prefers-color-scheme: dark"));
        assertTrue("Must call mermaid.render", report.contains("mermaid.render"));
        assertTrue("Must have action bar styling", report.contains(".report-action-bar"));
        assertTrue("Must have download function", report.contains("downloadReportHtml"));
        assertTrue("Must have 14-day retention notice", report.contains("14-Day Retention"));
    }

    @Test
    public void testGetMermaidJsContentLoadedFromClasspath() {
        String js = MailSender.getMermaidJsContent();
        assertNotNull("Mermaid JS content must not be null", js);
        assertTrue("Mermaid JS should be non-empty", js.length() > 100000);
        assertTrue("Mermaid JS must contain mermaid definition", js.contains("mermaid"));
    }

    @Test
    public void testGroupItemsBySpaceAndIdDesc() {
        Space s1 = new Space();
        s1.setId(1L);
        s1.setName("Beta Project");
        s1.setPrefixCode("BETA");

        Space s2 = new Space();
        s2.setId(2L);
        s2.setName("Alpha Project");
        s2.setPrefixCode("ALPHA");

        Item i1 = new Item();
        i1.setId(10L);
        i1.setSequenceNum(10);
        i1.setSpace(s1);

        Item i2 = new Item();
        i2.setId(25L);
        i2.setSequenceNum(25);
        i2.setSpace(s1);

        Item i3 = new Item();
        i3.setId(5L);
        i3.setSequenceNum(5);
        i3.setSpace(s2);

        Item i4 = new Item();
        i4.setId(100L);
        i4.setSequenceNum(100);
        i4.setSpace(s2);

        List<Item> items = Arrays.asList(i1, i2, i3, i4);
        Map<Space, List<Item>> grouped = MailSender.groupItemsBySpace(items);

        assertEquals("Should have 2 spaces", 2, grouped.size());

        List<Space> spaceKeys = new ArrayList<>(grouped.keySet());
        // Alpha Project must come before Beta Project (alphabetical)
        assertEquals("ALPHA", spaceKeys.get(0).getPrefixCode());
        assertEquals("BETA", spaceKeys.get(1).getPrefixCode());

        // In ALPHA: id 100 before id 5 (DESC)
        List<Item> alphaItems = grouped.get(spaceKeys.get(0));
        assertEquals(2, alphaItems.size());
        assertEquals(100L, alphaItems.get(0).getId());
        assertEquals(5L, alphaItems.get(1).getId());

        // In BETA: id 25 before id 10 (DESC)
        List<Item> betaItems = grouped.get(spaceKeys.get(1));
        assertEquals(2, betaItems.size());
        assertEquals(25L, betaItems.get(0).getId());
        assertEquals(10L, betaItems.get(1).getId());

        // Flat sorted list test
        List<Item> sorted = MailSender.sortItemsBySpaceAndIdDesc(items);
        assertEquals(4, sorted.size());
        assertEquals(100L, sorted.get(0).getId());
        assertEquals(5L, sorted.get(1).getId());
        assertEquals(25L, sorted.get(2).getId());
        assertEquals(10L, sorted.get(3).getId());
    }

    @Test
    public void testBuildStandaloneHtmlReportWithMermaidBlock() {
        Map<String, String> config = new HashMap<>();
        config.put("mail.server.host", "localhost");
        config.put("mail.from", "jtrac@example.com");
        config.put("jtrac.url.base", "http://localhost/jtrac/");

        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");

        MailSender mailSender = new MailSender(config, messageSource, "en");

        Space spaceA = new Space();
        spaceA.setId(1L);
        spaceA.setPrefixCode("PROJA");
        spaceA.setName("Project Alpha");

        Item itemA = new Item();
        itemA.setId(101L);
        itemA.setSequenceNum(101);
        itemA.setSpace(spaceA);
        itemA.setSummary("Payment gateway timeout");
        itemA.setStatus(1);

        String mermaidMarkdown = "### 流程分析\n\n```mermaid\nflowchart TD\n  A[\"Client Request\"] --> B[\"Gateway\"]\n  B --> C[\"Backend Timeout\"]\n```\n";
        Map<String, String> summaries = new HashMap<>();
        summaries.put("PROJA-101", "Gateway timeout occurred under heavy load.");

        String report = mailSender.buildStandaloneHtmlReport("Gateway Troubleshooting", mermaidMarkdown,
                Collections.singletonList(itemA), summaries, Locale.TAIWAN, Collections.singleton(spaceA), new java.util.Date());

        assertNotNull(report);
        assertTrue("Report must contain language-mermaid code block", report.contains("language-mermaid"));
        assertTrue("Report must contain mermaid-wrapper in script replacement", report.contains("wrapper.className = 'mermaid-wrapper'"));
        assertTrue("Report must contain mermaid diagram code", report.contains("flowchart TD"));
        assertTrue("Report must contain Client Request node", report.contains("Client Request"));
        assertTrue("Report must contain Space header for Alpha", report.contains("Project Alpha (PROJA)"));
    }

    @Test
    public void testSendAiOfflineNoticeDoesNotThrow() {
        Map<String, String> config = new HashMap<>();
        config.put("mail.server.host", "localhost");
        config.put("mail.from", "jtrac@example.com");

        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");

        MailSender mailSender = new MailSender(config, messageSource, "en");

        mailSender.sendAiOfflineNotice("user@example.com", "Original question", Locale.ENGLISH);
        mailSender.sendAiOfflineNotice("user@example.com", "原始問題", Locale.TAIWAN);

        // Test null/empty recipient safety
        mailSender.sendAiOfflineNotice(null, "Subject", Locale.ENGLISH);
        mailSender.sendAiOfflineNotice("no", "Subject", Locale.ENGLISH);
    }

    @Test
    public void testSendAiZeroHitNoticeDoesNotThrow() {
        Map<String, String> config = new HashMap<>();
        config.put("mail.server.host", "localhost");
        config.put("mail.from", "jtrac@example.com");

        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");

        MailSender mailSender = new MailSender(config, messageSource, "en");

        Space s = new Space();
        s.setName("Documentation");
        s.setPrefixCode("DOC");
        Set<Space> spaces = Collections.singleton(s);

        mailSender.sendAiZeroHitNotice("user@example.com", "Unknown Query", Locale.ENGLISH, spaces);
        mailSender.sendAiZeroHitNotice("user@example.com", "查無工單的問題", Locale.TAIWAN, spaces);

        // Defensive checks for null or disabled recipient
        mailSender.sendAiZeroHitNotice(null, "Subject", Locale.ENGLISH, spaces);
        mailSender.sendAiZeroHitNotice("no", "Subject", Locale.ENGLISH, spaces);
        mailSender.sendAiZeroHitNotice("user@example.com", null, null, null);
    }
}
