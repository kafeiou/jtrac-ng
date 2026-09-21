package info.jtrac.util;

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

}
