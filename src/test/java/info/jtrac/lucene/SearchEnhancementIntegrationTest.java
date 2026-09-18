package info.jtrac.lucene;

import info.jtrac.domain.Item;
import info.jtrac.domain.ItemSearch;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import java.io.File;
import java.util.List;

public class SearchEnhancementIntegrationTest {

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
            for (File f : file.listFiles()) {
                f.delete();
            }
        }
        System.setProperty("jtrac.home", home.getAbsolutePath());
        context = new FileSystemXmlApplicationContext("src/main/webapp/WEB-INF/applicationContext-lucene.xml");
        indexer = (Indexer) context.getBean("indexer");
        searcher = (IndexSearcher) context.getBean("indexSearcher");
    }

    @Test
    public void testEmailAndCompoundFilenameSubTokenSearch() {
        Item item1 = new Item();
        item1.setId(101);
        item1.setSummary("資材處連絡人設定");
        item1.setDetail("請聯絡 pmc.healthandlife@gmail.com 處理");
        indexer.index(item1);

        Item item2 = new Item();
        item2.setId(102);
        item2.setSummary("Thunderbird行事曆設定");
        item2.setDetail("相關操作說明請參閱 thunderbird_gmail.pdf 檔案");
        indexer.index(item2);

        Item item3 = new Item();
        item3.setId(103);
        item3.setSummary("業務申請開放帳號");
        item3.setDetail("業務申請開放GMAIL相關帳號與伺服器設定");
        indexer.index(item3);

        // 1. Searching for "gmail" must match item1 (from email), item2 (from attachment name), and item3 (from detail)
        List<Long> hitsGmail = searcher.findItemIdsContainingText("gmail");
        Assert.assertTrue("item1 must be found by 'gmail' via email subtoken", hitsGmail.contains(101L));
        Assert.assertTrue("item2 must be found by 'gmail' via filename subtoken", hitsGmail.contains(102L));
        Assert.assertTrue("item3 must be found by 'gmail'", hitsGmail.contains(103L));

        // 2. Searching for email username part "pmc" or domain "healthandlife"
        List<Long> hitsPmc = searcher.findItemIdsContainingText("pmc");
        Assert.assertTrue("item1 must be found by 'pmc'", hitsPmc.contains(101L));

        List<Long> hitsHal = searcher.findItemIdsContainingText("healthandlife");
        Assert.assertTrue("item1 must be found by 'healthandlife'", hitsHal.contains(101L));

        // 3. Searching for attachment prefix "thunderbird"
        List<Long> hitsTb = searcher.findItemIdsContainingText("thunderbird");
        Assert.assertTrue("item2 must be found by 'thunderbird'", hitsTb.contains(102L));
    }

    @Test
    public void testChinesePhraseSlopAndWildcards() {
        Item item = new Item();
        item.setId(201);
        item.setSummary("業務申請開放帳號流程");
        item.setDetail("伺服器故障排除指南");
        indexer.index(item);

        // 1. Chinese phrase slop: "申請帳號" should match "申請開放帳號" (slop = 2)
        List<Long> hitsSlop = searcher.findItemIdsContainingText("申請帳號");
        Assert.assertTrue("Phrase with 1 word in between must match with phrase slop", hitsSlop.contains(201L));

        // 2. Leading wildcard: "*帳號*" or "*伺服器*"
        List<Long> hitsWildcard = searcher.findItemIdsContainingText("*帳號*");
        Assert.assertTrue("Leading wildcard '*帳號*' must match", hitsWildcard.contains(201L));

        // 3. Exact quoted phrase
        List<Long> hitsQuoted = searcher.findItemIdsContainingText("\"伺服器\"");
        Assert.assertTrue("Exact quoted phrase '\"伺服器\"' must match", hitsQuoted.contains(201L));
    }

    @Test
    public void testShowHistoryDefaultRules() {
        ItemSearch search = new ItemSearch((info.jtrac.domain.User) null);
        Assert.assertFalse("ItemSearch.showHistory must default to false for clean browsing", search.isShowHistory());

        PageParameters params = new PageParameters();
        search.initFromPageParameters(params, null, null);
        Assert.assertFalse("initFromPageParameters without showHistory and without searchText must remain false", search.isShowHistory());

        PageParameters searchParams = new PageParameters();
        searchParams.set("searchText", "crash");
        search.initFromPageParameters(searchParams, null, null);
        Assert.assertTrue("initFromPageParameters with searchText must default showHistory to true", search.isShowHistory());

        PageParameters falseParams = new PageParameters();
        falseParams.set("showHistory", "false");
        falseParams.set("searchText", "crash");
        search.initFromPageParameters(falseParams, null, null);
        Assert.assertFalse("initFromPageParameters with explicit showHistory=false must be false even with searchText", search.isShowHistory());

        PageParameters trueParams = new PageParameters();
        trueParams.set("showHistory", "true");
        search.initFromPageParameters(trueParams, null, null);
        Assert.assertTrue("initFromPageParameters with explicit showHistory=true must be true", search.isShowHistory());

        PageParameters queryString = search.getAsQueryString();
        Assert.assertEquals("true", queryString.get("showHistory").toString());
    }

    @Test
    public void testSmartHistoryFilteringWithHits() {
        Item item = new Item();
        item.setId(301);
        item.setSummary("設定工單");
        item.setDetail("請聯繫 service@gmail.com 處理");
        indexer.index(item);

        info.jtrac.domain.History h1 = new info.jtrac.domain.History(item);
        h1.setId(1001);
        h1.setParent(item);
        item.add(h1);
        indexer.index(h1);

        info.jtrac.domain.History h2 = new info.jtrac.domain.History();
        h2.setId(1002);
        h2.setParent(item);
        h2.setComment("狀態更新為已完成");
        indexer.index(h2);

        SearchResultHits hits = searcher.findHitsContainingText("gmail");
        Assert.assertTrue("item 301 must be in itemIds", hits.getItemIds().contains(301L));
        Assert.assertTrue("history 1001 must be in historyIds", hits.getHistoryIds().contains(1001L));
        Assert.assertFalse("history 1002 without keyword must NOT be in historyIds", hits.getHistoryIds().contains(1002L));

        ItemSearch search = new ItemSearch((info.jtrac.domain.User) null);
        search.setShowHistory(true);
        search.setHistoryIds(hits.getHistoryIds());
        Assert.assertNotNull("Criteria must be generated with historyIds", search.getCriteriaForCount());
    }
}
