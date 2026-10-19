package info.jtrac.wicket;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import org.junit.Assert;
import org.junit.Test;

public class SearchLoadingModalTest {

    private static final String[] MESSAGE_BUNDLES = new String[] {
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

    @Test
    public void testAllLanguageBundlesContainSearchLoadingKeys() throws Exception {
        for (String bundlePath : MESSAGE_BUNDLES) {
            Properties props = new Properties();
            try (InputStream is = getClass().getResourceAsStream(bundlePath)) {
                Assert.assertNotNull("Bundle should exist: " + bundlePath, is);
                props.load(is);
            }
            String title = props.getProperty("search.loading.title");
            String subtitle = props.getProperty("search.loading.subtitle");
            Assert.assertNotNull("search.loading.title missing in " + bundlePath, title);
            Assert.assertFalse("search.loading.title blank in " + bundlePath, title.trim().isEmpty());
            Assert.assertNotNull("search.loading.subtitle missing in " + bundlePath, subtitle);
            Assert.assertFalse("search.loading.subtitle blank in " + bundlePath, subtitle.trim().isEmpty());
        }
    }

    @Test
    public void testBasePageHtmlAndFrontendAssetsContainSearchLoadingOverlay() throws Exception {
        try (InputStream is = BasePage.class.getResourceAsStream("BasePage.html")) {
            Assert.assertNotNull("BasePage.html should be on classpath", is);
            String html = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            Assert.assertTrue(html.contains("id=\"jtrac-search-loading-overlay\""));
            Assert.assertTrue(html.contains("class=\"search-loading-spinner\""));
            Assert.assertTrue(html.contains("key=\"search.loading.title\""));
            Assert.assertTrue(html.contains("key=\"search.loading.subtitle\""));
        }

        Path cssPath = Paths.get("src/main/webapp/resources/jtrac.css");
        Assert.assertTrue(Files.exists(cssPath));
        String css = Files.readString(cssPath, StandardCharsets.UTF_8);
        Assert.assertTrue(css.contains(".search-loading-overlay"));
        Assert.assertTrue(css.contains(".search-loading-spinner"));
        Assert.assertTrue(css.contains("@keyframes jtracSpin"));

        Path jsPath = Paths.get("src/main/webapp/resources/theme.js");
        Assert.assertTrue(Files.exists(jsPath));
        String js = Files.readString(jsPath, StandardCharsets.UTF_8);
        Assert.assertTrue(js.contains("jtracShowSearchLoading"));
        Assert.assertTrue(js.contains("jtracHideSearchLoading"));
        Assert.assertTrue(js.contains("150"));
        Assert.assertTrue(js.contains("15000"));
        Assert.assertTrue(js.contains(".page-links"));
        Assert.assertTrue(js.contains("pageshow"));
    }
}
