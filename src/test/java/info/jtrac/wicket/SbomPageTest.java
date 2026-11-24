package info.jtrac.wicket;

import info.jtrac.Jtrac;
import info.jtrac.domain.CountsHolder;
import info.jtrac.domain.Space;
import info.jtrac.domain.User;
import org.apache.wicket.Page;
import org.apache.wicket.Session;
import org.apache.wicket.request.Request;
import org.apache.wicket.request.Response;
import org.apache.wicket.resource.loader.BundleStringResourceLoader;
import org.apache.wicket.util.tester.WicketTester;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.InputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Verifies SuperUser-only access control, OptionsPage visibility, Wicket markup rendering,
 * CycloneDX 1.5 JSON / UTF-8 BOM CSV download actions, and 8-language i18n keys for SbomPage.
 */
public class SbomPageTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private WicketTester tester;
    private User activeUser;

    private static final String[] MESSAGE_BUNDLES = new String[]{
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

    private static final String[] REQUIRED_SBOM_KEYS = new String[]{
            "options.sbom",
            "sbom.title",
            "sbom.subtitle",
            "sbom.refresh",
            "sbom.export.cyclonedx",
            "sbom.export.csv",
            "sbom.search.placeholder",
            "sbom.env.title",
            "sbom.env.ollama",
            "sbom.section.subsystems",
            "sbom.section.frontend",
            "sbom.section.backend"
    };

    @Before
    public void setUp() throws Exception {
        final String homeDir = tempFolder.newFolder("jtrac-home").getAbsolutePath();
        final Jtrac stubJtrac = createStubJtrac(homeDir);

        JtracApplication testApp = new JtracApplication() {
            @Override
            public void init() {
                getResourceSettings().getStringResourceLoaders().add(0, new BundleStringResourceLoader("messages"));
            }

            @Override
            public Jtrac getJtrac() {
                return stubJtrac;
            }

            @Override
            public Class<? extends Page> getHomePage() {
                return DashboardPage.class;
            }

            @Override
            public Session newSession(Request request, Response response) {
                JtracSession session = new JtracSession(request);
                if (activeUser != null) {
                    session.setUser(activeUser);
                }
                return session;
            }
        };

        tester = new WicketTester(testApp);
    }

    @After
    public void tearDown() {
        if (tester != null) {
            tester.destroy();
        }
    }

    private Jtrac createStubJtrac(final String homeDir) {
        final Map<String, String> config = new HashMap<String, String>();
        config.put("search.ai.enabled", "true");
        config.put("mail.ai.enabled", "false");

        return (Jtrac) Proxy.newProxyInstance(
                Jtrac.class.getClassLoader(),
                new Class<?>[]{Jtrac.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        String name = method.getName();
                        if ("getJtracHome".equals(name)) return homeDir;
                        if ("getReleaseVersion".equals(name)) return "3.0.0-beta";
                        if ("getReleaseTimestamp".equals(name)) return "20261124";
                        if ("getDefaultLocale".equals(name)) return "en";
                        if ("getSessionTimeoutInMinutes".equals(name)) return 30;
                        if ("loadAllConfig".equals(name)) return config;
                        if ("findAllSpaces".equals(name)) return Collections.emptyList();
                        if ("findSpacesWhereGuestAllowed".equals(name)) return Collections.emptyList();
                        if ("loadAllStoredSearch".equals(name)) return Collections.emptyList();
                        if ("loadCountsForUser".equals(name)) return new CountsHolder();
                        if ("isAttachmentOcrEnabled".equals(name)) return false;
                        if ("getAttachmentOcrModel".equals(name)) return "gemma4:26b";
                        if ("getDataSource".equals(name)) return null;
                        if ("loadConfig".equals(name)) {
                            if (args != null && args.length == 2) {
                                String v = config.get(args[0]);
                                return v != null ? v : args[1];
                            }
                            return args != null && args.length > 0 ? config.get(args[0]) : null;
                        }
                        Class<?> rt = method.getReturnType();
                        if (rt == boolean.class) return false;
                        if (rt == int.class) return 0;
                        if (rt == long.class) return 0L;
                        return null;
                    }
                });
    }

    private User createSuperUser() {
        User u = new User();
        u.setId(1L);
        u.setLoginName("admin");
        u.setName("Super Admin");
        u.setLocale("en");
        u.addSpaceWithRole(null, "ROLE_ADMIN");
        return u;
    }

    private User createSpaceAdminUser() {
        Space s = new Space();
        s.setId(10L);
        s.setPrefixCode("PRJ");
        s.setName("Project");

        User u = new User();
        u.setId(2L);
        u.setLoginName("spaceadmin");
        u.setName("Space Admin");
        u.setLocale("en");
        u.addSpaceWithRole(s, "ROLE_ADMIN");
        return u;
    }

    @Test
    public void testAllLanguageBundlesContainSbomKeys() throws Exception {
        for (String bundlePath : MESSAGE_BUNDLES) {
            Properties props = new Properties();
            try (InputStream is = getClass().getResourceAsStream(bundlePath)) {
                Assert.assertNotNull("Bundle should exist: " + bundlePath, is);
                props.load(is);
            }
            for (String key : REQUIRED_SBOM_KEYS) {
                String val = props.getProperty(key);
                Assert.assertNotNull(key + " missing in " + bundlePath, val);
                Assert.assertFalse(key + " blank in " + bundlePath, val.trim().isEmpty());
            }
        }
    }

    @Test
    public void testOptionsPageShowsSbomLinkOnlyForSuperUser() {
        activeUser = createSpaceAdminUser();
        JtracSession.get().setUser(activeUser);
        tester.startPage(OptionsPage.class);
        tester.assertRenderedPage(OptionsPage.class);
        tester.assertInvisible("sbom");

        activeUser = createSuperUser();
        JtracSession.get().setUser(activeUser);
        tester.startPage(OptionsPage.class);
        tester.assertRenderedPage(OptionsPage.class);
        tester.assertVisible("sbom");
    }

    @Test
    public void testNonSuperUserIsInterceptedAwayFromSbomPage() {
        activeUser = createSpaceAdminUser();
        JtracSession.get().setUser(activeUser);
        tester.startPage(SbomPage.class);
        tester.assertRenderedPage(DashboardPage.class);
    }

    @Test
    public void testSuperUserRendersSbomPageAndExportsCycloneDxAndCsv() {
        activeUser = createSuperUser();
        JtracSession.get().setUser(activeUser);

        tester.startPage(SbomPage.class);
        tester.assertRenderedPage(SbomPage.class);

        String html = tester.getLastResponseAsString();
        Assert.assertTrue("Page must display Apache Lucene", html.contains("Apache Lucene"));
        Assert.assertTrue("Page must display OpenCC", html.contains("OpenCC"));
        Assert.assertTrue("Page must display Mermaid.js", html.contains("Mermaid.js"));

        // Click CycloneDX 1.5 JSON export
        tester.clickLink("exportCycloneDxBtn");
        String json = new String(tester.getLastResponse().getBinaryContent(), StandardCharsets.UTF_8);
        Assert.assertTrue(json.contains("CycloneDX"));
        Assert.assertTrue(json.contains("1.5"));
        Assert.assertTrue(json.contains("bomFormat"));

        // Re-open SbomPage and click CSV export
        tester.startPage(SbomPage.class);
        tester.clickLink("exportCsvBtn");
        String csv = new String(tester.getLastResponse().getBinaryContent(), StandardCharsets.UTF_8);
        Assert.assertTrue(csv.startsWith("\uFEFFCategory,Group,Name,Version,Status,PURL"));
        Assert.assertTrue(csv.contains("Apache Lucene"));

        // Verify heading breadcrumb navigates cleanly back to OptionsPage
        tester.startPage(SbomPage.class);
        tester.clickLink("optionsLink");
        tester.assertRenderedPage(OptionsPage.class);
    }
}
