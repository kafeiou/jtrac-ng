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
 * Verifies Three-Tier Exact-Match-First + Latest-Timestamp-First search ranking
 * both WITH AI (TC/SC & synonym expansion) and WITHOUT AI, including CJK safety
 * and >1000 noise resilience.
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
    public void testEmailExactMatchPinnedAboveNewerPartialMatchesAndSortedByLatestTimestampWithoutAi() throws Exception {
        // Ticket 1: Older exact match in summary (2026-01-10)
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

        // Ticket 2: Initial ticket only mentions gmail (2026-05-01), but has a newer comment (2026-11-20) with 100% exact case-insensitive match!
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

        // Ticket 3: Brand-new ticket (2026-11-24), highest ID, but ONLY partial sub-token matches ("gmail.com" + "app")
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

        // 1. Test showHistory = true (Ticket-grouped, adjacent rows, chronological within ticket)
        List<AbstractItem> historyRows = new ArrayList<AbstractItem>(Arrays.<AbstractItem>asList(
                h3_0, h1_0, h2_1, h2_0
        ));
        HibernateJtracDao.sortBySearchRelevance(historyRows, "  app.hlmt@gmail.com  ", null, null, null);

        // Expected:
        // 1st group: Ticket 2 (Tier 1 via comment h2_1, latest ts 2026-11-20) -> h2_0 (id=201) then h2_1 (id=202)
        // 2nd group: Ticket 1 (Tier 1 via summary h1_0, latest ts 2026-01-10) -> h1_0 (id=101)
        // 3rd group: Ticket 3 (Tier 3 partial match, even though ts is 2026-11-24!) -> h3_0 (id=301)
        Assert.assertEquals(201L, historyRows.get(0).getId());
        Assert.assertEquals(202L, historyRows.get(1).getId());
        Assert.assertEquals(101L, historyRows.get(2).getId());
        Assert.assertEquals(301L, historyRows.get(3).getId());

        // 2. Test showHistory = false (Item mode, inspecting item summary/detail + child history comments)
        List<Item> itemRows = new ArrayList<Item>(Arrays.asList(item3, item1, item2));
        HibernateJtracDao.sortBySearchRelevance(itemRows, "app.hlmt@gmail.com", Collections.<String>emptyList(), null, null);

        Assert.assertEquals(2L, itemRows.get(0).getId());
        Assert.assertEquals(1L, itemRows.get(1).getId());
        Assert.assertEquals(3L, itemRows.get(2).getId());
    }

    @Test
    public void testThreeTierTraditionalSimplifiedChineseAndAiVariantsRanking() throws Exception {
        // Query: Traditional Chinese "發票通知"
        // AI expanded variants: ["发票通知", "开票通知"]

        // Ticket A: Exact Traditional match, older (2026-03-01) -> Tier 1
        Item itemA = new Item();
        itemA.setId(10L);
        itemA.setSummary("客戶未收到發票通知信件");
        itemA.setTimeStamp(ts("2026-03-01 10:00:00"));

        // Ticket B: Exact Traditional match, newer (2026-09-15) -> Tier 1 (must rank before A)
        Item itemB = new Item();
        itemB.setId(11L);
        itemB.setSummary("電子發票通知排程調整");
        itemB.setTimeStamp(ts("2026-09-15 14:00:00"));

        // Ticket C: Simplified Chinese variant exact match, newer than B (2026-11-22) -> Tier 2 (must rank after Tier 1, before Tier 3)
        Item itemC = new Item();
        itemC.setId(12L);
        itemC.setSummary("大陆子公司电子发票通知延迟");
        itemC.setTimeStamp(ts("2026-11-22 11:00:00"));

        // Ticket D: Partial / slop CJK match ("發票系統異常通知"), newest timestamp (2026-11-24) -> Tier 3
        Item itemD = new Item();
        itemD.setId(13L);
        itemD.setSummary("發票系統異常通知處理");
        itemD.setTimeStamp(ts("2026-11-24 09:00:00"));

        List<Item> list = new ArrayList<Item>(Arrays.asList(itemD, itemC, itemA, itemB));
        List<String> aiVariants = Arrays.asList("发票通知", "开票通知");

        HibernateJtracDao.sortBySearchRelevance(list, "發票通知", aiVariants, null, null);

        // Expected: B (Tier 1, newer) -> A (Tier 1, older) -> C (Tier 2, Simplified) -> D (Tier 3, partial)
        Assert.assertEquals(11L, list.get(0).getId());
        Assert.assertEquals(10L, list.get(1).getId());
        Assert.assertEquals(12L, list.get(2).getId());
        Assert.assertEquals(13L, list.get(3).getId());
    }

    @Test
    public void testLuceneExactMatchBoostSurvivesOver1000PartialSubTokenNoiseHits() {
        // Index target ticket #1 with 100% exact email
        Item exactItem = new Item();
        exactItem.setId(1L);
        exactItem.setSummary("正式環境通知信箱 app.hlmt@gmail.com");
        exactItem.setDetail("此為唯一 100% 完全吻合工單");
        indexer.index(exactItem);

        // Index 1,050 noise tickets that share sub-tokens "gmail" and "com"
        for (long i = 2; i <= 1051; i++) {
            Item noise = new Item();
            noise.setId(i);
            noise.setSummary("一般外部使用者 user" + i + "@gmail.com 連線紀錄");
            noise.setDetail("gmail com routine log entry " + i);
            indexer.index(noise);
        }

        SearchResultHits hits = searcher.findHitsContainingText("app.hlmt@gmail.com");
        Assert.assertFalse(hits.isEmpty());
        Assert.assertTrue("Exact match item #1 must never be truncated out of TopDocs(1000)",
                hits.getItemIds().contains(1L));
        Assert.assertEquals("Exact match item #1 must be ranked #1 by Lucene boost",
                Long.valueOf(1L), hits.getItemIds().get(0));
    }

    @Test
    public void testTraditionalChineseCjkSearchAndExpandedTermsCapture() {
        Item tcItem = new Item();
        tcItem.setId(501L);
        tcItem.setSummary("繁體中文：申請開放帳號與發票通知設定");
        tcItem.setDetail("確認【緊急】特殊符號與繁體檢索完全正常");
        indexer.index(tcItem);

        Item scItem = new Item();
        scItem.setId(502L);
        scItem.setSummary("简体中文：发票通知设置说明");
        scItem.setDetail("跨语系检索验证");
        indexer.index(scItem);

        // 1. Without AI: Traditional Chinese unigram & phrase slop works accurately
        SearchResultHits noAiHits = searcher.findHitsContainingText("發票通知");
        Assert.assertTrue(noAiHits.getItemIds().contains(501L));
        Assert.assertTrue(noAiHits.getExpandedTerms().isEmpty());

        // Phrase slop ("申請帳號" -> "申請開放帳號") still works
        Assert.assertTrue(searcher.findItemIdsContainingText("申請帳號").contains(501L));

        // CJK with brackets ("【緊急】特殊符號") works without ParseException
        Assert.assertTrue(searcher.findItemIdsContainingText("【緊急】特殊符號").contains(501L));

        // 2. With Mock AI Expander: captures expanded terms into SearchResultHits
        OllamaSearchExpander expander = new OllamaSearchExpander();
        info.jtrac.mail.OllamaClient mockClient = new info.jtrac.mail.OllamaClient(null, null, null, 6) {
            @Override
            public String chat(String systemPrompt, String userPrompt) {
                if ("發票通知".equals(userPrompt)) {
                    return "发票通知 开票通知";
                }
                return "";
            }
        };
        expander.setOllamaClient(mockClient);
        searcher.setSearchExpander(expander);

        SearchResultHits aiHits = searcher.findHitsContainingText("發票通知");
        Assert.assertTrue(aiHits.getItemIds().contains(501L));
        Assert.assertTrue(aiHits.getItemIds().contains(502L));
        Assert.assertTrue(aiHits.getExpandedTerms().contains("发票通知"));
    }

    @Test
    public void testExplicitColumnSortDisablesRelevanceOverride() {
        Space space = new Space();
        space.setMetadata(new Metadata());
        ItemSearch search = new ItemSearch(space);

        // No searchText -> relevance sort inactive
        Assert.assertFalse(search.isSearchRelevanceSortActive());

        // Default search with searchText -> relevance sort active
        search.setSearchText("app.hlmt@gmail.com");
        Assert.assertTrue(search.isSearchRelevanceSortActive());

        // User manually clicks 'status' column
        search.setExplicitSort(true);
        search.setSortFieldName("status");
        Assert.assertFalse(search.isSearchRelevanceSortActive());

        // Resetting explicitSort on new search re-enables relevance sort
        search.setExplicitSort(false);
        search.setSortFieldName("id");
        search.setSortDescending(true);
        Assert.assertTrue(search.isSearchRelevanceSortActive());

        // URL PageParameters with only searchText -> relevance sort active
        ItemSearch fromSearchUrl = new ItemSearch(space);
        PageParameters p1 = new PageParameters().set("searchText", "app.hlmt@gmail.com");
        fromSearchUrl.initFromPageParameters(p1, new User(), null);
        Assert.assertTrue(fromSearchUrl.isSearchRelevanceSortActive());

        // URL PageParameters with explicit sortAscending=true -> relevance sort inactive
        ItemSearch fromSortedUrl = new ItemSearch(space);
        PageParameters p2 = new PageParameters().set("searchText", "app.hlmt@gmail.com").set("sortAscending", "true");
        fromSortedUrl.initFromPageParameters(p2, new User(), null);
        Assert.assertFalse(fromSortedUrl.isSearchRelevanceSortActive());
    }
}
