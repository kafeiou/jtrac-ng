package info.jtrac.lucene;

import info.jtrac.domain.AbstractItem;
import info.jtrac.domain.History;
import info.jtrac.domain.Item;
import info.jtrac.domain.ItemSearch;
import info.jtrac.domain.Metadata;
import info.jtrac.domain.Space;
import info.jtrac.domain.User;
import info.jtrac.hibernate.HibernateJtracDao;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Verifies:
 * 1. Query-time non-splitting of email/filenames (app.hlmt@gmail.com matches precisely, while sub-tokens match at index time)
 * 2. Offline OpenCC + Cross-Strait phrase-level Chinese conversion WITHOUT AI (< 1ms)
 * 3. Three-Tier Exact-Match-First + Latest-Timestamp-First search ranking
 * 4. Transparent banner display terms (ItemSearch.getDisplayExpandedTerms)
 */
public class SearchRelevanceRankingTest {

    private ApplicationContext context;
    private Indexer indexer;
    private IndexSearcher searcher;

    @Before
    public void setUp() {
        File home = new File("target/home");
        if (!home.exists()) {
            home.mkdir();
        }
        File file = new File("target/home/indexes");
        if (!file.exists()) {
            file.mkdir();
        } else {
            File[] files = file.listFiles();
            if (files != null) {
                for (File f : files) {
                    f.delete();
                }
            }
        }
        System.setProperty("jtrac.home", home.getAbsolutePath());
        context = new FileSystemXmlApplicationContext("src/main/webapp/WEB-INF/applicationContext-lucene.xml");
        indexer = (Indexer) context.getBean("indexer");
        searcher = (IndexSearcher) context.getBean("indexSearcher");
    }

    private Date ts(String iso) throws Exception {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(iso);
    }

    @Test
    public void testEmailExactQueryDoesNotPullUnrelatedGmailOrComNoiseWhileShortTokensStillWork() {
        Item exactTarget = new Item();
        exactTarget.setId(1L);
        exactTarget.setSummary("正式環境通知信箱 app.hlmt@gmail.com");
        exactTarget.setDetail("此為唯一包含完整信箱之工單");
        indexer.index(exactTarget);

        Item unrelatedGmail = new Item();
        unrelatedGmail.setId(2L);
        unrelatedGmail.setSummary("其他人的信箱 someone.else@gmail.com");
        unrelatedGmail.setDetail("測試一般網站 https://example.com");
        indexer.index(unrelatedGmail);

        // 1. Searching full email "app.hlmt@gmail.com" ONLY matches item 1 (no longer polluted by 'gmail' or 'com'!)
        SearchResultHits fullEmailHits = searcher.findHitsContainingText("app.hlmt@gmail.com");
        Assert.assertEquals(1, fullEmailHits.getItemIds().size());
        Assert.assertEquals(Long.valueOf(1L), fullEmailHits.getItemIds().get(0));
        Assert.assertTrue("Full email should not emit noisy fragments in expandedTerms",
                fullEmailHits.getExpandedTerms().isEmpty());

        // 2. Searching short sub-token "hlmt" or local-part "app.hlmt" still finds item 1 via index-time SubTokenFilter!
        Assert.assertEquals(Collections.singletonList(1L), searcher.findItemIdsContainingText("hlmt"));
        Assert.assertEquals(Collections.singletonList(1L), searcher.findItemIdsContainingText("app.hlmt"));

        // 3. Searching "gmail" finds both item 1 and item 2
        List<Long> gmailHits = searcher.findItemIdsContainingText("gmail");
        Assert.assertEquals(2, gmailHits.size());
        Assert.assertTrue(gmailHits.contains(1L));
        Assert.assertTrue(gmailHits.contains(2L));

        // 4. Searching partial slice "hlmt@gmail" (no wildcard *) finds ONLY item 1, NOT item 2!
        Assert.assertEquals(Collections.singletonList(1L), searcher.findItemIdsContainingText("hlmt@gmail"));
    }

    @Test
    public void testLegacyUnrebuiltIndexStillFindsAppHlmtWithoutWildcardAndAllSpecialSymbolsWork() throws Exception {
        // Simulate a LEGACY v1 index entry where ONLY full email + individual sub-tokens (app, hlmt, gmail, com)
        // were indexed (WITHOUT the new 'app.hlmt' localPart token!)
        org.apache.lucene.store.Directory dir = (org.apache.lucene.store.Directory) context.getBean("indexDirectory");
        org.apache.lucene.index.IndexWriterConfig legacyCfg = new org.apache.lucene.index.IndexWriterConfig(
                new org.apache.lucene.analysis.core.WhitespaceAnalyzer());
        legacyCfg.setOpenMode(org.apache.lucene.index.IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
        try (org.apache.lucene.index.IndexWriter legacyWriter = new org.apache.lucene.index.IndexWriter(dir, legacyCfg)) {
            org.apache.lucene.document.Document legacyDoc = new org.apache.lucene.document.Document();
            legacyDoc.add(new org.apache.lucene.document.StringField("docId", "item:901",
                    org.apache.lucene.document.Field.Store.YES));
            legacyDoc.add(new org.apache.lucene.document.StoredField("id", "901"));
            legacyDoc.add(new org.apache.lucene.document.StoredField("type", "item"));
            // Legacy tokens: NO 'app.hlmt' token!
            legacyDoc.add(new org.apache.lucene.document.TextField("text", "app.hlmt@gmail.com app hlmt gmail com",
                    org.apache.lucene.document.Field.Store.NO));
            legacyWriter.addDocument(legacyDoc);

            org.apache.lucene.document.Document noiseDoc = new org.apache.lucene.document.Document();
            noiseDoc.add(new org.apache.lucene.document.StringField("docId", "item:902",
                    org.apache.lucene.document.Field.Store.YES));
            noiseDoc.add(new org.apache.lucene.document.StoredField("id", "902"));
            noiseDoc.add(new org.apache.lucene.document.StoredField("type", "item"));
            noiseDoc.add(new org.apache.lucene.document.TextField("text", "other@gmail.com other gmail com app",
                    org.apache.lucene.document.Field.Store.NO));
            legacyWriter.addDocument(noiseDoc);
            legacyWriter.commit();
        }

        // Plain "app.hlmt" (NO '*' wildcard!) MUST hit 901 even on the legacy un-rebuilt index, and NOT hit 902!
        Assert.assertEquals(Collections.singletonList(901L), searcher.findItemIdsContainingText("app.hlmt"));

        // Now index real items containing tricky IT symbols: UNC paths, C:\ paths, IP:Port, URLs, GUIDs, minus sign, C++
        Item pathItem = new Item();
        pathItem.setId(910L);
        pathItem.setSummary("GPO 路徑 \\\\hlmt.com.tw\\SysVol\\Policies\\{9CECF8CB-B752-4E7C}");
        pathItem.setDetail("本地日誌 C:\\Users\\admin\\jtrac.log 連線至 https://192.168.1.10:8080/app/item/form 發生 ERROR: connection - timeout (500) C++");
        indexer.index(pathItem);

        Item unrelatedAppItem = new Item();
        unrelatedAppItem.setId(911L);
        unrelatedAppItem.setSummary("一般 app 畫面微調");
        unrelatedAppItem.setDetail("無關的 http://example.com 測試");
        indexer.index(unrelatedAppItem);

        // 1. Windows UNC path
        Assert.assertEquals(Collections.singletonList(910L),
                searcher.findItemIdsContainingText("\\\\hlmt.com.tw\\SysVol\\Policies"));
        // 2. Windows drive path (with ':' and trailing '\')
        Assert.assertEquals(Collections.singletonList(910L),
                searcher.findItemIdsContainingText("C:\\Users\\admin\\"));
        // 3. GPO GUID with curly braces '{}' and hyphens '-' (must NOT throw RangeQuery ParseException!)
        Assert.assertEquals(Collections.singletonList(910L),
                searcher.findItemIdsContainingText("{9CECF8CB-B752-4E7C}"));
        // 4. IP:Port (':' must NOT be treated as Lucene field name!)
        Assert.assertEquals(Collections.singletonList(910L),
                searcher.findItemIdsContainingText("192.168.1.10:8080"));
        // 5. URL / Servlet path (must NOT match 911 just because of 'app'!)
        Assert.assertEquals(Collections.singletonList(910L),
                searcher.findItemIdsContainingText("/app/item/form"));
        // 6. Log prefix with ':' and standalone '-' ("ERROR: connection - timeout" must NOT exclude 'timeout'!)
        Assert.assertTrue(searcher.findItemIdsContainingText("ERROR: connection - timeout").contains(910L));
        // 7. C++ with trailing '+'
        Assert.assertTrue(searcher.findItemIdsContainingText("C++").contains(910L));
    }

    @Test
    public void testOfflinePhraseLevelChineseConversionWithoutAi() {
        // Ensure NO Ollama AI expander is configured (100% offline mode)
        searcher.setSearchExpander(null);

        // 1. Phrase-level conversion unit checks (OpenCC tw2sp / s2twp + Cross-Strait IT phrases)
        List<String> memVariants = ChineseVariantConverter.expandOfflineVariants("記憶體洩漏");
        Assert.assertTrue("Must convert phrase 記憶體 -> 内存", memVariants.contains("内存泄漏"));
        Assert.assertTrue("Must also include direct simplified 记忆体泄漏", memVariants.contains("记忆体泄漏"));

        List<String> swVariants = ChineseVariantConverter.expandOfflineVariants("軟體預設設定");
        Assert.assertTrue("Must convert 軟體預設設定 -> 软件默认设置", swVariants.contains("软件默认设置"));

        List<String> projVariants = ChineseVariantConverter.expandOfflineVariants("專案程式碼");
        Assert.assertTrue("Must convert 專案程式碼 -> 项目代码", projVariants.contains("项目代码"));

        List<String> revVariants = ChineseVariantConverter.expandOfflineVariants("软件默认设置");
        Assert.assertTrue("Reverse Mainland Simplified -> Taiwan Traditional phrase conversion",
                revVariants.contains("軟體預設設定"));

        // 2. End-to-end Lucene search WITHOUT AI
        Item mainlandTicket = new Item();
        mainlandTicket.setId(801L);
        mainlandTicket.setSummary("生产环境服务器内存泄漏排查");
        mainlandTicket.setDetail("修改软件默认设置与定时任务");
        indexer.index(mainlandTicket);

        Item taiwanTicket = new Item();
        taiwanTicket.setId(802L);
        taiwanTicket.setSummary("正式機伺服器記憶體洩漏紀錄");
        taiwanTicket.setDetail("調整軟體預設設定與發票排程");
        indexer.index(taiwanTicket);

        // Searching Taiwan Traditional "記憶體洩漏" without AI finds BOTH Taiwan (802) and Mainland (801) tickets!
        SearchResultHits hitsFromTw = searcher.findHitsContainingText("記憶體洩漏");
        Assert.assertTrue(hitsFromTw.getItemIds().contains(801L));
        Assert.assertTrue(hitsFromTw.getItemIds().contains(802L));
        Assert.assertTrue(hitsFromTw.getExpandedTerms().contains("内存泄漏"));

        // Searching Mainland Simplified "软件默认设置" without AI finds BOTH Mainland (801) and Taiwan (802) tickets!
        SearchResultHits hitsFromCn = searcher.findHitsContainingText("软件默认设置");
        Assert.assertTrue(hitsFromCn.getItemIds().contains(801L));
        Assert.assertTrue(hitsFromCn.getItemIds().contains(802L));
        Assert.assertTrue(hitsFromCn.getExpandedTerms().contains("軟體預設設定"));
    }

    @Test
    public void testEmailExactMatchPinnedAboveNewerPartialMatchesAndSortedByLatestTimestampWithoutAi() throws Exception {
        Item item1 = new Item();
        item1.setId(1L);
        item1.setSummary("帳號設定: app.hlmt@gmail.com 權限開通");
        item1.setDetail("一般說明");
        item1.setTimeStamp(ts("2026-01-10 09:00:00"));
        History h1_0 = new History(item1);
        h1_0.setId(101L);
        h1_0.setParent(item1);
        h1_0.setTimeStamp(ts("2026-01-10 09:00:00"));
        item1.add(h1_0);

        Item item2 = new Item();
        item2.setId(2L);
        item2.setSummary("外部信箱轉寄異常 gmail");
        item2.setDetail("請協助排查");
        item2.setTimeStamp(ts("2026-05-01 10:00:00"));
        History h2_0 = new History(item2);
        h2_0.setId(201L);
        h2_0.setParent(item2);
        h2_0.setTimeStamp(ts("2026-05-01 10:00:00"));
        item2.add(h2_0);

        History h2_1 = new History();
        h2_1.setId(202L);
        h2_1.setParent(item2);
        h2_1.setComment("已確認請改寄至 App.HLMT@gmail.com 測試");
        h2_1.setTimeStamp(ts("2026-11-20 15:30:00"));
        item2.add(h2_1);

        Item item3 = new Item();
        item3.setId(3L);
        item3.setSummary("Mobile app login with test@gmail.com");
        item3.setDetail("最新建立但並非完整吻合");
        item3.setTimeStamp(ts("2026-11-24 08:00:00"));
        History h3_0 = new History(item3);
        h3_0.setId(301L);
        h3_0.setParent(item3);
        h3_0.setTimeStamp(ts("2026-11-24 08:00:00"));
        item3.add(h3_0);

        List<AbstractItem> historyRows = new ArrayList<AbstractItem>(Arrays.<AbstractItem>asList(
                h3_0, h1_0, h2_1, h2_0
        ));
        HibernateJtracDao.sortBySearchRelevance(historyRows, "  app.hlmt@gmail.com  ", null, null, null);

        Assert.assertEquals(201L, historyRows.get(0).getId());
        Assert.assertEquals(202L, historyRows.get(1).getId());
        Assert.assertEquals(101L, historyRows.get(2).getId());
        Assert.assertEquals(301L, historyRows.get(3).getId());

        List<Item> itemRows = new ArrayList<Item>(Arrays.asList(item3, item1, item2));
        HibernateJtracDao.sortBySearchRelevance(itemRows, "app.hlmt@gmail.com", Collections.<String>emptyList(), null, null);

        Assert.assertEquals(2L, itemRows.get(0).getId());
        Assert.assertEquals(1L, itemRows.get(1).getId());
        Assert.assertEquals(3L, itemRows.get(2).getId());
    }

    @Test
    public void testThreeTierTraditionalSimplifiedChineseAndAiVariantsRanking() throws Exception {
        Item itemA = new Item();
        itemA.setId(10L);
        itemA.setSummary("客戶未收到發票通知信件");
        itemA.setTimeStamp(ts("2026-03-01 10:00:00"));

        Item itemB = new Item();
        itemB.setId(11L);
        itemB.setSummary("電子發票通知排程調整");
        itemB.setTimeStamp(ts("2026-09-15 14:00:00"));

        Item itemC = new Item();
        itemC.setId(12L);
        itemC.setSummary("大陆子公司电子发票通知延迟");
        itemC.setTimeStamp(ts("2026-11-22 11:00:00"));

        Item itemD = new Item();
        itemD.setId(13L);
        itemD.setSummary("發票系統異常通知處理");
        itemD.setTimeStamp(ts("2026-11-24 09:00:00"));

        List<Item> list = new ArrayList<Item>(Arrays.asList(itemD, itemC, itemA, itemB));
        List<String> variants = ChineseVariantConverter.expandOfflineVariants("發票通知");

        HibernateJtracDao.sortBySearchRelevance(list, "發票通知", variants, null, null);

        // B (Tier 1, newer) -> A (Tier 1, older) -> C (Tier 2, Simplified) -> D (Tier 3, partial)
        Assert.assertEquals(11L, list.get(0).getId());
        Assert.assertEquals(10L, list.get(1).getId());
        Assert.assertEquals(12L, list.get(2).getId());
        Assert.assertEquals(13L, list.get(3).getId());
    }

    @Test
    public void testLuceneExactPhraseBoostSurvivesOver1000MultiWordNoiseHits() {
        Item exactItem = new Item();
        exactItem.setId(1L);
        exactItem.setSummary("客戶連線異常 app.hlmt@gmail.com 認證失敗");
        exactItem.setDetail("唯一同時完整包含整句之工單");
        indexer.index(exactItem);

        for (long i = 2; i <= 1051; i++) {
            Item noise = new Item();
            noise.setId(i);
            noise.setSummary("一般日常認證失敗紀錄 " + i);
            noise.setDetail("routine auth log " + i);
            indexer.index(noise);
        }

        SearchResultHits hits = searcher.findHitsContainingText("app.hlmt@gmail.com 認證失敗");
        Assert.assertTrue("Exact match item #1 must never be truncated out of TopDocs(1000)",
                hits.getItemIds().contains(1L));
        Assert.assertEquals("Exact match item #1 must be ranked #1 by Lucene boost",
                Long.valueOf(1L), hits.getItemIds().get(0));
    }

    @Test
    public void testDisplayExpandedTermsCleanForEmailAndPopulatedForChineseAndMultiWord() {
        Space space = new Space();
        space.setMetadata(new Metadata());

        // 1. Single email -> clean empty list (no noisy 'com' / 'gmail' fragments!)
        ItemSearch emailSearch = new ItemSearch(space);
        emailSearch.setSearchText("app.hlmt@gmail.com");
        emailSearch.setExpandedSearchTerms(Collections.<String>emptyList());
        Assert.assertTrue(emailSearch.getDisplayExpandedTerms().isEmpty());

        // 2. Chinese phrase -> shows offline OpenCC / cross-strait expanded terms
        ItemSearch zhSearch = new ItemSearch(space);
        zhSearch.setSearchText("記憶體洩漏");
        zhSearch.setExpandedSearchTerms(ChineseVariantConverter.expandOfflineVariants("記憶體洩漏"));
        List<String> zhDisplay = zhSearch.getDisplayExpandedTerms();
        Assert.assertTrue(zhDisplay.contains("内存泄漏"));
        Assert.assertFalse(zhDisplay.contains("記憶體洩漏"));

        // 3. Space-separated mixed query -> shows space tokens + Chinese variants
        ItemSearch mixedSearch = new ItemSearch(space);
        mixedSearch.setSearchText("app.hlmt@gmail.com 發票通知");
        mixedSearch.setExpandedSearchTerms(ChineseVariantConverter.expandOfflineVariants("發票通知"));
        List<String> mixedDisplay = mixedSearch.getDisplayExpandedTerms();
        Assert.assertTrue(mixedDisplay.contains("app.hlmt@gmail.com"));
        Assert.assertTrue(mixedDisplay.contains("發票通知"));
        Assert.assertTrue(mixedDisplay.contains("发票通知"));
    }

    @Test
    public void testExplicitColumnSortDisablesRelevanceOverride() {
        Space space = new Space();
        space.setMetadata(new Metadata());
        ItemSearch search = new ItemSearch(space);

        Assert.assertFalse(search.isSearchRelevanceSortActive());

        search.setSearchText("app.hlmt@gmail.com");
        Assert.assertTrue(search.isSearchRelevanceSortActive());

        search.setExplicitSort(true);
        search.setSortFieldName("status");
        Assert.assertFalse(search.isSearchRelevanceSortActive());

        search.setExplicitSort(false);
        search.setSortFieldName("id");
        search.setSortDescending(true);
        Assert.assertTrue(search.isSearchRelevanceSortActive());

        ItemSearch fromSearchUrl = new ItemSearch(space);
        PageParameters p1 = new PageParameters().set("searchText", "app.hlmt@gmail.com");
        fromSearchUrl.initFromPageParameters(p1, new User(), null);
        Assert.assertTrue(fromSearchUrl.isSearchRelevanceSortActive());

        ItemSearch fromSortedUrl = new ItemSearch(space);
        PageParameters p2 = new PageParameters().set("searchText", "app.hlmt@gmail.com").set("sortAscending", "true");
        fromSortedUrl.initFromPageParameters(p2, new User(), null);
        Assert.assertFalse(fromSortedUrl.isSearchRelevanceSortActive());
    }
}
