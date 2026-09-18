package info.jtrac.lucene;

import info.jtrac.domain.Item;

import java.io.File;
import java.util.List;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import org.junit.*;

public class IndexSearcherTest {

    private ApplicationContext context;

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
    }

	@Test
    public void testFindItemIdsBySearchingWithinSummaryAndDetailFields() throws Exception {
        Item item = new Item();
        item.setId(1);
        item.setSummary("this is a test summary");
        item.setDetail("the quick brown fox jumped over the lazy dogs");
        Indexer indexer = (Indexer) context.getBean("indexer");
        indexer.index(item);
        IndexSearcher searcher = (IndexSearcher) context.getBean("indexSearcher");
        List list = searcher.findItemIdsContainingText("lazy");
        Assert.assertEquals(1, list.size());
        list = searcher.findItemIdsContainingText("foo");
        Assert.assertEquals(0, list.size());
        list = searcher.findItemIdsContainingText("summary");
        Assert.assertEquals(1, list.size());
    }

	@Test
    public void testIfUmlautsCanBeIndexedAndSearchedFor() {
        Item item = new Item();
        item.setId(1);
        item.setSummary("this does not contain an umlaut");
        item.setDetail("there is an umlaut right here --> \u00fcmlaut");
        Indexer indexer = (Indexer) context.getBean("indexer");
        indexer.index(item);
        IndexSearcher searcher = (IndexSearcher) context.getBean("indexSearcher");
        List list = searcher.findItemIdsContainingText("\u00fcmlaut");
        Assert.assertEquals(1, list.size());
    }

	@Test
    public void testEnglishStemmingWindowsMatchesWindow() {
        Item item = new Item();
        item.setId(2);
        item.setSummary("Docx attachment details");
        item.setDetail("Deployment on Windows Server environment");
        Indexer indexer = (Indexer) context.getBean("indexer");
        indexer.index(item);
        IndexSearcher searcher = (IndexSearcher) context.getBean("indexSearcher");

        // Searching singular 'window' matches plural 'Windows'
        List list1 = searcher.findItemIdsContainingText("window");
        Assert.assertEquals(1, list1.size());

        // Searching plural 'Windows' matches
        List list2 = searcher.findItemIdsContainingText("Windows");
        Assert.assertEquals(1, list2.size());

        // Searching lowercase 'windows' matches
        List list3 = searcher.findItemIdsContainingText("windows");
        Assert.assertEquals(1, list3.size());
    }

	@Test
    public void testAutoPrefixFallback() {
        Item item = new Item();
        item.setId(3);
        item.setSummary("System config");
        item.setDetail("Configuring Microsoft Windows services");
        Indexer indexer = (Indexer) context.getBean("indexer");
        indexer.index(item);
        IndexSearcher searcher = (IndexSearcher) context.getBean("indexSearcher");

        // Prefix 'win' automatically falls back to 'win*' and finds 'Windows'
        List list = searcher.findItemIdsContainingText("win");
        Assert.assertEquals(1, list.size());
    }

	@Test
    public void testChineseUnigramSearch() {
        Item item = new Item();
        item.setId(4);
        item.setSummary("繁體中文標題");
        item.setDetail("系統測試與附件檢索支援多語系運作");
        Indexer indexer = (Indexer) context.getBean("indexer");
        indexer.index(item);
        IndexSearcher searcher = (IndexSearcher) context.getBean("indexSearcher");

        List list1 = searcher.findItemIdsContainingText("測試");
        Assert.assertEquals(1, list1.size());

        List list2 = searcher.findItemIdsContainingText("多語系");
        Assert.assertEquals(1, list2.size());
    }

	@Test
    public void testMultiKeywordOrMode() {
        Item item1 = new Item();
        item1.setId(5);
        item1.setSummary("Database connection failure");
        item1.setDetail("Network socket timeout on port 3306");

        Item item2 = new Item();
        item2.setId(6);
        item2.setSummary("UI styling glitch");
        item2.setDetail("Navbar alignment issue on mobile");

        Indexer indexer = (Indexer) context.getBean("indexer");
        indexer.index(item1);
        indexer.index(item2);
        IndexSearcher searcher = (IndexSearcher) context.getBean("indexSearcher");

        // "timeout styling" matches item1 (has timeout) AND item2 (has styling) in OR mode
        List list = searcher.findItemIdsContainingText("timeout styling");
        Assert.assertEquals(2, list.size());
    }

	@Test
    public void testSpecialCharactersEscapedFallback() {
        Item item = new Item();
        item.setId(7);
        item.setSummary("[CRITICAL] NullPointerException in Service:Auth");
        item.setDetail("Error code (500) during c++ module invocation");

        Indexer indexer = (Indexer) context.getBean("indexer");
        indexer.index(item);
        IndexSearcher searcher = (IndexSearcher) context.getBean("indexSearcher");

        // Query with brackets and colons: parser will catch ParseException and fallback to escaped query
        List list1 = searcher.findItemIdsContainingText("[CRITICAL]");
        Assert.assertFalse(list1.isEmpty());

        List list2 = searcher.findItemIdsContainingText("Service:Auth");
        Assert.assertFalse(list2.isEmpty());

        List list3 = searcher.findItemIdsContainingText("(500)");
        Assert.assertFalse(list3.isEmpty());
    }

	@Test
    public void testValidateQueryWithSpecialCharacters() {
        IndexSearcher searcher = (IndexSearcher) context.getBean("indexSearcher");
        Assert.assertTrue(searcher.validateQuery("[CRITICAL]"));
        Assert.assertTrue(searcher.validateQuery("foo:bar"));
        Assert.assertTrue(searcher.validateQuery("(500)"));
        Assert.assertTrue(searcher.validateQuery(""));
        Assert.assertTrue(searcher.validateQuery(null));
    }

	@Test
    public void testTraditionalChineseSearchMatchesSimplifiedAndCrossStraitViaOllama() {
        Item item1 = new Item();
        item1.setId(101);
        item1.setSummary("這是繁體專案管理");
        item1.setDetail("記錄了各項研發細節");

        Item item2 = new Item();
        item2.setId(102);
        item2.setSummary("这是简体字面的专案");
        item2.setDetail("包含所有专案计划");

        Item item3 = new Item();
        item3.setId(103);
        item3.setSummary("这是大陆用语的项目跟踪");
        item3.setDetail("负责项目推进与上线");

        Indexer indexer = (Indexer) context.getBean("indexer");
        indexer.index(item1);
        indexer.index(item2);
        indexer.index(item3);

        IndexSearcher searcher = (IndexSearcher) context.getBean("indexSearcher");

        // Mock OllamaSearchExpander translating 專案 -> 专案, 项目
        OllamaSearchExpander expander = new OllamaSearchExpander();
        info.jtrac.mail.OllamaClient mockClient = new info.jtrac.mail.OllamaClient(null, null, null, 6) {
            @Override
            public String chat(String systemPrompt, String userPrompt) {
                if ("專案".equals(userPrompt)) {
                    return "专案 项目 項目";
                }
                return "";
            }
        };
        expander.setOllamaClient(mockClient);
        searcher.setSearchExpander(expander);

        // Searching Traditional '專案' matches all 3 items (Traditional 專案, Simplified 专案, and synonym 项目)!
        List hits = searcher.findItemIdsContainingText("專案");
        Assert.assertEquals(3, hits.size());
        Assert.assertTrue(hits.contains(101L));
        Assert.assertTrue(hits.contains(102L));
        Assert.assertTrue(hits.contains(103L));
    }

	@Test
    public void testSimplifiedChineseSearchMatchesTraditionalAndSynonymsViaOllama() {
        Item item1 = new Item();
        item1.setId(201);
        item1.setSummary("這是繁體專案管理");
        item1.setDetail("繁體紀錄");

        Item item2 = new Item();
        item2.setId(202);
        item2.setSummary("这是简体项目");
        item2.setDetail("简体记录");

        Indexer indexer = (Indexer) context.getBean("indexer");
        indexer.index(item1);
        indexer.index(item2);

        IndexSearcher searcher = (IndexSearcher) context.getBean("indexSearcher");

        // Mock OllamaSearchExpander translating 项目 -> 項目, 專案, 专案
        OllamaSearchExpander expander = new OllamaSearchExpander();
        info.jtrac.mail.OllamaClient mockClient = new info.jtrac.mail.OllamaClient(null, null, null, 6) {
            @Override
            public String chat(String systemPrompt, String userPrompt) {
                if ("项目".equals(userPrompt)) {
                    return "項目 專案 专案";
                }
                return "";
            }
        };
        expander.setOllamaClient(mockClient);
        searcher.setSearchExpander(expander);

        // Searching Simplified '项目' matches both items
        List hits = searcher.findItemIdsContainingText("项目");
        Assert.assertEquals(2, hits.size());
        Assert.assertTrue(hits.contains(201L));
        Assert.assertTrue(hits.contains(202L));
    }

}
