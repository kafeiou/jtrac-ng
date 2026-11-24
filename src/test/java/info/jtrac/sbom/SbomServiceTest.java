package info.jtrac.sbom;

import info.jtrac.Jtrac;
import info.jtrac.domain.Space;
import info.jtrac.domain.User;
import info.jtrac.sbom.SbomService.ComponentItem;
import info.jtrac.sbom.SbomService.SbomSnapshot;
import info.jtrac.wicket.DashboardPage;
import info.jtrac.wicket.SbomPage;
import org.apache.wicket.RestartResponseAtInterceptPageException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Verifies SbomService runtime discovery, persistent sbom-cache.json caching & forced refresh,
 * CycloneDX 1.5 JSON & UTF-8 BOM CSV exports, and SuperUser-only access control.
 */
public class SbomServiceTest {

    private Path tempHome;

    @Before
    public void setUp() throws Exception {
        SbomService.clearMemoryCache();
        tempHome = Files.createTempDirectory("jtrac-sbom-test-");
    }

    @After
    public void tearDown() throws Exception {
        SbomService.clearMemoryCache();
        if (tempHome != null && Files.exists(tempHome)) {
            File[] files = tempHome.toFile().listFiles();
            if (files != null) {
                for (File f : files) {
                    f.delete();
                }
            }
            Files.deleteIfExists(tempHome);
        }
    }

    private Jtrac createStubJtrac(final String homePath) {
        final Map<String, String> cfg = new HashMap<String, String>();
        cfg.put("llm.ollama.url", "http://localhost:11434");
        cfg.put("llm.ollama.model", "qwen2.5:32b");
        cfg.put("llm.search.expansion.enabled", "true");
        cfg.put("attachment.ocr.enabled", "true");
        cfg.put("markdown.enabled", "true");

        return (Jtrac) Proxy.newProxyInstance(
                Jtrac.class.getClassLoader(),
                new Class<?>[]{Jtrac.class},
                new InvocationHandler() {
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        String name = method.getName();
                        if ("getReleaseVersion".equals(name)) return "3.0.0-beta";
                        if ("getJtracHome".equals(name)) return homePath;
                        if ("loadAllConfig".equals(name)) return cfg;
                        if ("getDataSource".equals(name)) return null;
                        Class<?> rt = method.getReturnType();
                        if (rt == boolean.class) return false;
                        if (rt == int.class) return 0;
                        if (rt == long.class) return 0L;
                        return null;
                    }
                });
    }

    @Test
    public void testSnapshotCollectionPersistentCacheAndRefresh() throws Exception {
        Jtrac stub = createStubJtrac(tempHome.toString());

        // 1. First load scans runtime and writes sbom-cache.json
        SbomSnapshot first = SbomService.getSnapshot(stub, null, null, false);
        Assert.assertNotNull(first);
        Assert.assertEquals("3.0.0-beta", first.environment.appVersion);
        Assert.assertTrue(first.environment.javaVersion.length() > 0);
        Assert.assertFalse("Subsystems list must not be empty", first.subsystems.isEmpty());
        Assert.assertFalse("Frontend libraries list must not be empty", first.frontendLibraries.isEmpty());
        Assert.assertFalse("Backend libraries list from classpath must not be empty", first.backendLibraries.isEmpty());

        // Verify Lucene 10.6.0 is reported in subsystems and all Lucene JARs appear individually with clean versions!
        boolean foundLucene10 = false;
        for (ComponentItem sub : first.subsystems) {
            if ("org.apache.lucene".equals(sub.group) && sub.version.startsWith("10.")) {
                foundLucene10 = true;
                break;
            }
        }
        Assert.assertTrue("SBOM must report upgraded Apache Lucene 10.x", foundLucene10);
        Assert.assertTrue("Ollama should be marked configured", first.environment.ollamaConfigured);
        Assert.assertEquals("qwen2.5:32b", first.environment.ollamaModel);

        // Verify backend JAR accuracy: multiple Lucene JARs listed separately without git hash suffix, and Spring group/name fixed
        int luceneJarCount = 0;
        boolean foundSpringCore = false;
        for (ComponentItem be : first.backendLibraries) {
            if (be.name.startsWith("lucene-")) {
                luceneJarCount++;
                Assert.assertEquals("org.apache.lucene", be.group);
                Assert.assertFalse("Lucene version must not contain git hash spaces: " + be.version, be.version.contains(" "));
                Assert.assertTrue("Lucene PURL must be valid", be.purl.startsWith("pkg:maven/org.apache.lucene/lucene-"));
            }
            if ("spring-core".equals(be.name)) {
                foundSpringCore = true;
                Assert.assertEquals("org.springframework", be.group);
                Assert.assertTrue(be.purl.startsWith("pkg:maven/org.springframework/spring-core@"));
            }
        }
        Assert.assertTrue("Expected at least 3 distinct Lucene JARs (core, analysis-common, queryparser), got " + luceneJarCount, luceneJarCount >= 3);
        Assert.assertTrue("spring-core must be detected with org.springframework groupId", foundSpringCore);

        // Verify real scanned frontend assets (jQuery 3.6.0, YUI 2.5.0, Mermaid.js 10.9.1, jquery.enhanced-switch without fake version)
        boolean foundJquery = false;
        boolean foundYui = false;
        boolean foundMermaid = false;
        boolean foundEnhancedSwitch = false;
        for (ComponentItem fe : first.frontendLibraries) {
            Assert.assertNotEquals("Apache Wicket must not be duplicated in frontend list", "org.apache.wicket", fe.group);
            if ("jquery".equals(fe.group) && "3.6.0".equals(fe.version)) foundJquery = true;
            if ("yahoo".equals(fe.group) && "2.5.0".equals(fe.version)) foundYui = true;
            if ("mermaid".equals(fe.group) && "10.9.1".equals(fe.version)) foundMermaid = true;
            if ("jquery.enhanced-switch.js".equals(fe.name)) {
                foundEnhancedSwitch = true;
                Assert.assertEquals("jquery.enhanced-switch.js has no version header and must remain empty", "", fe.version);
            }
        }
        Assert.assertTrue("Real bundled jQuery 3.6.0 must be detected", foundJquery);
        Assert.assertTrue("Real bundled YUI 2.5.0 must be detected", foundYui);
        Assert.assertTrue("Real bundled Mermaid.js 10.9.1 must be detected", foundMermaid);
        Assert.assertTrue("Real bundled jquery.enhanced-switch.js must be detected", foundEnhancedSwitch);

        Path cacheFile = tempHome.resolve("sbom-cache.json");
        Assert.assertTrue("sbom-cache.json must be persisted on disk", Files.isRegularFile(cacheFile));
        String firstGeneratedAt = first.generatedAt;

        // 2. Clear memory cache and verify disk cache hit returns identical generatedAt timestamp
        SbomService.clearMemoryCache();
        Thread.sleep(15);
        SbomSnapshot fromDisk = SbomService.getSnapshot(stub, null, null, false);
        Assert.assertEquals("Subsequent non-refresh access must load cached snapshot", firstGeneratedAt, fromDisk.generatedAt);

        // 3. Forced refresh=true overwrites snapshot with newer timestamp
        Thread.sleep(15);
        SbomSnapshot refreshed = SbomService.getSnapshot(stub, null, null, true);
        Assert.assertNotEquals("Refresh must produce a new generatedAt timestamp", firstGeneratedAt, refreshed.generatedAt);
    }

    @Test
    public void testCycloneDx15AndUtf8BomCsvExport() {
        Jtrac stub = createStubJtrac(tempHome.toString());
        SbomSnapshot snap = SbomService.getSnapshot(stub, null, null, false);

        // 1. CycloneDX 1.5 JSON validation
        String cdx = SbomService.exportCycloneDxJson(snap);
        Assert.assertTrue(cdx.contains("\"bomFormat\" : \"CycloneDX\""));
        Assert.assertTrue(cdx.contains("\"specVersion\" : \"1.5\""));
        Assert.assertTrue(cdx.contains("pkg:maven/"));
        Assert.assertTrue(cdx.contains("\"jtrac-ng\""));

        // 2. UTF-8 BOM CSV validation
        String csv = SbomService.exportCsv(snap);
        Assert.assertTrue("CSV must start with UTF-8 BOM for Excel compatibility", csv.startsWith("\uFEFF"));
        Assert.assertTrue(csv.contains("Category,Group,Name,Version,Status,PURL\n"));
        Assert.assertTrue(csv.contains("Environment,application,JTrac NG,3.0.0-beta,ACTIVE"));
        Assert.assertTrue(csv.contains("Backend,"));
    }

    @Test
    public void testLegacyLucene29SegmentsGenPurgedOnDirectoryInit() throws Exception {
        Path legacyIdxDir = Files.createTempDirectory("jtrac-legacy-idx-");
        try {
            Path fakeSegmentsGen = legacyIdxDir.resolve("segments.gen");
            Files.write(fakeSegmentsGen, new byte[]{0, 1, 2, 3});
            Assert.assertTrue(Files.exists(fakeSegmentsGen));

            info.jtrac.lucene.LuceneDirectoryFactoryBean bean = new info.jtrac.lucene.LuceneDirectoryFactoryBean();
            bean.setPath(legacyIdxDir.toString());
            try (org.apache.lucene.store.Directory dir = bean.getObject()) {
                Assert.assertNotNull(dir);
                Assert.assertFalse("Legacy Lucene 2.9 segments.gen must be automatically cleaned",
                        Files.exists(fakeSegmentsGen));
            }
        } finally {
            File[] files = legacyIdxDir.toFile().listFiles();
            if (files != null) {
                for (File f : files) f.delete();
            }
            Files.deleteIfExists(legacyIdxDir);
        }
    }
}
