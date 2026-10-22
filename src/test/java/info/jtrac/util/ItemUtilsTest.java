package info.jtrac.util;

import info.jtrac.domain.History;
import info.jtrac.domain.Item;
import info.jtrac.domain.ItemSearch;
import info.jtrac.domain.Space;
import info.jtrac.hibernate.HibernateJtracDao;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.*;

public class ItemUtilsTest {

	@Test
    public void testHtmlEscaping() {
        Assert.assertEquals("&nbsp;&nbsp;&nbsp;&nbsp;", ItemUtils.fixWhiteSpace("    "));
        Assert.assertEquals("&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;", ItemUtils.fixWhiteSpace(" \t"));
        Assert.assertEquals("Hello World", ItemUtils.fixWhiteSpace("Hello World"));
        Assert.assertEquals("", ItemUtils.fixWhiteSpace(""));
        Assert.assertEquals("", ItemUtils.fixWhiteSpace(null));
        Assert.assertEquals("Hello<br/>World", ItemUtils.fixWhiteSpace("Hello\nWorld"));
        Assert.assertEquals("Hello<br/>&nbsp;&nbsp;World", ItemUtils.fixWhiteSpace("Hello\n  World"));
        Assert.assertEquals("Hello<br/>&nbsp;World<br/>&nbsp;&nbsp;&nbsp;&nbsp;Everyone", ItemUtils.fixWhiteSpace("Hello\n World\n\tEveryone"));
        Assert.assertEquals("Hello&nbsp;&nbsp;&nbsp;&nbsp;World", ItemUtils.fixWhiteSpace("Hello\tWorld"));
    }

	@Test
    public void testMarkdown() {
        Assert.assertEquals(null, ItemUtils.renderMarkdown(null));
        Assert.assertEquals("", ItemUtils.renderMarkdown(""));
        Assert.assertEquals("<p>Hello World</p>", ItemUtils.renderMarkdown("Hello World"));
        Assert.assertEquals("<p><em>Hello</em> <strong>World</strong></p>", ItemUtils.renderMarkdown("*Hello* **World**"));
        Assert.assertEquals("<h1>Hello World</h1>", ItemUtils.renderMarkdown("# Hello World"));
        Assert.assertEquals("<h2>Hello World</h2>", ItemUtils.renderMarkdown("## Hello World"));
        Assert.assertEquals("<blockquote>\n<p>Hello World</p>\n</blockquote>", ItemUtils.renderMarkdown("> Hello World"));
        Assert.assertEquals("<p>Line 1<br/>Line 2<br/>Line 3</p>", ItemUtils.renderMarkdown("Line 1\nLine 2\r\nLine 3"));
    }

	@Test
    public void testRenderMarkdownWindowsPaths() {
        // 1. User's exact unquoted UNC path with GUID in braces
        String unc = "\\\\hlmt.com.tw\\SysVol\\hlmt.com.tw\\Policies\\{9CECF8CB-B752-4E7C-A1FB-90CB3B021A07}\\User\\Scripts\\Logon";
        Assert.assertEquals("<p>" + unc + "</p>", ItemUtils.renderMarkdown(unc));

        // 2. User's exact quoted UNC path
        String quotedUnc = "\"" + unc + "\"";
        Assert.assertEquals("<p>&quot;" + unc + "&quot;</p>", ItemUtils.renderMarkdown(quotedUnc));

        // 3. Local drive path with braces, underscores, parentheses
        String drivePath = "C:\\SysVol\\Policies\\{9CECF8CB-B752-4E7C-A1FB-90CB3B021A07}\\_temp\\(1)\\test.bat";
        Assert.assertEquals("<p>" + drivePath + "</p>", ItemUtils.renderMarkdown(drivePath));

        // 4. Standalone double backslash in text
        Assert.assertEquals("<p>Prefix \\\\ suffix</p>", ItemUtils.renderMarkdown("Prefix \\\\ suffix"));

        // 5. Code span: backslashes should NOT be doubled inside `...`
        Assert.assertEquals("<p><code>" + unc + "</code></p>", ItemUtils.renderMarkdown("`" + unc + "`"));

        // 6. Fenced code block: backslashes should NOT be doubled inside ```...```
        String fenced = "```\n" + unc + "\n```";
        Assert.assertEquals("<pre><code>" + unc + "\n</code></pre>", ItemUtils.renderMarkdown(fenced));

        // 7. Regular markdown punctuation escape (non-path) should work normally
        Assert.assertEquals("<p>*not italic*</p>", ItemUtils.renderMarkdown("\\*not italic\\*"));
    }

    @Test
    public void testSortByLastChangedOption3() {
        // Item 1: created at t=1000, updated at t=5000 (effective lastChanged = 5000)
        Item item1 = new Item();
        item1.setId(1L);
        item1.setTimeStamp(new Date(1000L));

        // Item 2: created at t=3000, NO subsequent updates (displays "-", effective lastChanged = creation time 3000)
        Item item2 = new Item();
        item2.setId(2L);
        item2.setTimeStamp(new Date(3000L));

        // Item 3: created at t=2000, updated at t=4000 (effective lastChanged = 4000)
        Item item3 = new Item();
        item3.setId(3L);
        item3.setTimeStamp(new Date(2000L));

        Map<Long, Date> latestTimeMap = new HashMap<Long, Date>();
        latestTimeMap.put(1L, new Date(5000L));
        latestTimeMap.put(3L, new Date(4000L));

        List<Item> items = new ArrayList<Item>();
        items.add(item2);
        items.add(item1);
        items.add(item3);

        // Ascending: item2 (3000) -> item3 (4000) -> item1 (5000)
        HibernateJtracDao.sortByLastChanged(items, false, latestTimeMap);
        Assert.assertEquals(2L, items.get(0).getId());
        Assert.assertEquals(3L, items.get(1).getId());
        Assert.assertEquals(1L, items.get(2).getId());

        // Descending: item1 (5000) -> item3 (4000) -> item2 (3000)
        HibernateJtracDao.sortByLastChanged(items, true, latestTimeMap);
        Assert.assertEquals(1L, items.get(0).getId());
        Assert.assertEquals(3L, items.get(1).getId());
        Assert.assertEquals(2L, items.get(2).getId());

        // History rows (showHistory = true): same parent ticket keeps chronological order (h1a before h1b)
        History h1a = new History();
        h1a.setId(10L);
        h1a.setParent(item1);
        History h1b = new History();
        h1b.setId(11L);
        h1b.setParent(item1);
        History h2 = new History();
        h2.setId(20L);
        h2.setParent(item2);

        List<History> histories = new ArrayList<History>();
        histories.add(h1b);
        histories.add(h2);
        histories.add(h1a);

        HibernateJtracDao.sortByLastChanged(histories, true, latestTimeMap);
        Assert.assertEquals(10L, histories.get(0).getId());
        Assert.assertEquals(11L, histories.get(1).getId());
        Assert.assertEquals(20L, histories.get(2).getId());

        // Verify ItemSearch.getCriteria() preserves sortFieldName = "lastChanged"
        Space space = new Space();
        space.setPrefixCode("TEST");
        ItemSearch search = new ItemSearch(space);
        search.setSortFieldName("lastChanged");
        search.getCriteria();
        Assert.assertEquals("lastChanged", search.getSortFieldName());
    }

}
