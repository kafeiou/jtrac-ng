package info.jtrac.sbom;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import info.jtrac.Jtrac;
import jakarta.servlet.ServletContext;
import org.apache.lucene.util.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runtime-aware Software Bill of Materials (SBOM) collector, persistent snapshot manager,
 * and standard format exporter (CycloneDX 1.5 JSON & UTF-8 BOM CSV).
 * Operates 100% in-process from real deployed JARs and bundled web assets without hardcoded versions.
 */
public final class SbomService {

    private static final Logger logger = LoggerFactory.getLogger(SbomService.class);
    private static final int CURRENT_SCHEMA_VERSION = 2;
    private static final String CACHE_FILENAME = "sbom-cache.json";
    private static final Object CACHE_LOCK = new Object();
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final DateTimeFormatter DISPLAY_TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z").withZone(ZoneId.systemDefault());

    private static final Pattern JAR_FILENAME_PATTERN =
            Pattern.compile("^(.*?)-(\\d+\\.\\d+(?:\\.\\d+)*(?:[-._][A-Za-z0-9._-]+)?)$");
    private static final Pattern MAVEN_GROUP_PATTERN =
            Pattern.compile("^[a-zA-Z0-9_.-]+$");
    private static final Pattern JQUERY_VER_PATTERN =
            Pattern.compile("jQuery\\s+v(\\d+\\.\\d+(?:\\.\\d+)?(?:[-A-Za-z0-9.]+)?)");
    private static final Pattern YUI_VER_PATTERN =
            Pattern.compile("version:\\s*(\\d+\\.\\d+(?:\\.\\d+)?)");
    private static final Pattern MERMAID_VER_PATTERN =
            Pattern.compile("const\\s+[A-Za-z0-9_$]+=\"(\\d+\\.\\d+\\.\\d+)\",[A-Za-z0-9_$]+=Object\\.freeze");
    private static final Pattern GENERIC_JS_VER_PATTERN =
            Pattern.compile("(?i)(?:@version|version)\\s*[:=]?\\s*v?(\\d+\\.\\d+(?:\\.\\d+)?)");

    private static final Map<String, String> KNOWN_ARTIFACT_GROUPS = new HashMap<String, String>();
    static {
        KNOWN_ARTIFACT_GROUPS.put("antlr", "antlr");
        KNOWN_ARTIFACT_GROUPS.put("asm", "org.ow2.asm");
        KNOWN_ARTIFACT_GROUPS.put("dom4j", "org.dom4j");
        KNOWN_ARTIFACT_GROUPS.put("hsqldb", "org.hsqldb");
        KNOWN_ARTIFACT_GROUPS.put("javasvn", "org.tmate");
        KNOWN_ARTIFACT_GROUPS.put("junrar", "com.github.junrar");
    }

    private static volatile SbomSnapshot cachedSnapshot;
    private static volatile Path cachedSnapshotPath;

    private SbomService() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class ComponentItem implements Serializable {
        public String category = "";
        public String group = "";
        public String name = "";
        public String version = "";
        public String status = "";
        public String purl = "";

        public ComponentItem() {
        }

        public ComponentItem(String category, String group, String name, String version, String status, String purl) {
            this.category = safe(category);
            this.group = safe(group);
            this.name = safe(name);
            this.version = safe(version);
            this.status = safe(status);
            this.purl = safe(purl);
        }

        public String getCategory() { return category; }
        public String getGroup() { return group; }
        public String getName() { return name; }
        public String getVersion() { return version; }
        public String getStatus() { return status; }
        public String getPurl() { return purl; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class EnvironmentInfo implements Serializable {
        public String appVersion = "";
        public String osName = "";
        public String osVersion = "";
        public String osArch = "";
        public String javaVersion = "";
        public String javaVendor = "";
        public String jvmName = "";
        public String serverInfo = "";
        public String serverVersion = "";
        public String dbProduct = "";
        public String dbVersion = "";
        public String jdbcDriver = "";
        public String jdbcDriverVersion = "";
        public boolean ollamaConfigured;
        public String ollamaModel = "";

        public EnvironmentInfo() {
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class SbomSnapshot implements Serializable {
        public int schemaVersion = CURRENT_SCHEMA_VERSION;
        public String generatedAt = "";
        public String generatedAtDisplay = "";
        public EnvironmentInfo environment = new EnvironmentInfo();
        public List<ComponentItem> subsystems = new ArrayList<ComponentItem>();
        public List<ComponentItem> frontendLibraries = new ArrayList<ComponentItem>();
        public List<ComponentItem> backendLibraries = new ArrayList<ComponentItem>();

        public SbomSnapshot() {
        }
    }

    /**
     * Returns the cached SBOM snapshot if present on memory/disk (matching current schema version);
     * scans and persists on first access, or forces a live re-scan and overwrites
     * `${jtrac.home}/sbom-cache.json` when `refresh` is true.
     */
    public static SbomSnapshot getSnapshot(Jtrac jtrac, DataSource dataSource, ServletContext servletContext, boolean refresh) {
        Path cacheFile = resolveCacheFile(jtrac);
        synchronized (CACHE_LOCK) {
            if (!refresh) {
                if (cachedSnapshot != null && cachedSnapshot.schemaVersion >= CURRENT_SCHEMA_VERSION
                        && (cacheFile == null || cacheFile.equals(cachedSnapshotPath))) {
                    return cachedSnapshot;
                }
                SbomSnapshot disk = readCacheFromDisk(cacheFile);
                if (disk != null) {
                    cachedSnapshot = disk;
                    cachedSnapshotPath = cacheFile;
                    return disk;
                }
            }

            SbomSnapshot fresh = buildSnapshot(jtrac, dataSource, servletContext);
            writeCacheToDisk(cacheFile, fresh);
            cachedSnapshot = fresh;
            cachedSnapshotPath = cacheFile;
            return fresh;
        }
    }

    public static void clearMemoryCache() {
        synchronized (CACHE_LOCK) {
            cachedSnapshot = null;
            cachedSnapshotPath = null;
        }
    }

    private static Path resolveCacheFile(Jtrac jtrac) {
        try {
            String home = jtrac != null ? jtrac.getJtracHome() : System.getProperty("jtrac.home");
            if (home != null && !home.trim().isEmpty()) {
                return Paths.get(home.trim()).resolve(CACHE_FILENAME);
            }
        } catch (Exception e) {
            logger.debug("Unable to resolve jtrac.home for SBOM cache", e);
        }
        return null;
    }

    private static SbomSnapshot readCacheFromDisk(Path cacheFile) {
        if (cacheFile == null || !Files.isRegularFile(cacheFile)) {
            return null;
        }
        try {
            byte[] bytes = Files.readAllBytes(cacheFile);
            SbomSnapshot snap = MAPPER.readValue(bytes, SbomSnapshot.class);
            if (snap != null && snap.schemaVersion >= CURRENT_SCHEMA_VERSION
                    && snap.environment != null && !safe(snap.generatedAt).isEmpty()) {
                return snap;
            }
        } catch (Exception e) {
            logger.debug("Unable to read SBOM cache from {}: {}", cacheFile, e.getMessage());
        }
        return null;
    }

    private static void writeCacheToDisk(Path cacheFile, SbomSnapshot snap) {
        if (cacheFile == null || snap == null) {
            return;
        }
        try {
            Path parent = cacheFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path tmp = cacheFile.resolveSibling(CACHE_FILENAME + ".tmp");
            Files.write(tmp, MAPPER.writeValueAsBytes(snap));
            Files.move(tmp, cacheFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            logger.debug("Unable to persist SBOM cache to {}: {}", cacheFile, e.getMessage());
        }
    }

    public static SbomSnapshot buildSnapshot(Jtrac jtrac, DataSource dataSource, ServletContext servletContext) {
        Instant now = Instant.now();
        SbomSnapshot snap = new SbomSnapshot();
        snap.schemaVersion = CURRENT_SCHEMA_VERSION;
        snap.generatedAt = now.toString();
        snap.generatedAtDisplay = DISPLAY_TIME_FMT.format(now);
        snap.environment = collectEnvironment(jtrac, dataSource, servletContext);
        snap.backendLibraries = collectBackendLibraries(servletContext);
        snap.subsystems = collectSubsystems(jtrac, snap.backendLibraries);
        snap.frontendLibraries = collectFrontendLibraries(servletContext);
        return snap;
    }

    public static String exportCycloneDxJson(SbomSnapshot snap) {
        try {
            EnvironmentInfo env = snap.environment != null ? snap.environment : new EnvironmentInfo();
            Map<String, Object> root = new LinkedHashMap<String, Object>();
            root.put("bomFormat", "CycloneDX");
            root.put("specVersion", "1.5");
            root.put("serialNumber", "urn:uuid:" + UUID.randomUUID().toString());
            root.put("version", Integer.valueOf(1));

            Map<String, Object> metadata = new LinkedHashMap<String, Object>();
            metadata.put("timestamp", safe(snap.generatedAt).isEmpty() ? Instant.now().toString() : snap.generatedAt);
            Map<String, Object> mainComp = new LinkedHashMap<String, Object>();
            mainComp.put("type", "application");
            mainComp.put("group", "info.jtrac");
            mainComp.put("name", "jtrac-ng");
            if (!safe(env.appVersion).isEmpty()) {
                mainComp.put("version", env.appVersion);
            }
            metadata.put("component", mainComp);
            root.put("metadata", metadata);

            List<Map<String, Object>> components = new ArrayList<Map<String, Object>>();
            addCdxComponent(components, "platform", "java", env.javaVendor + " / " + env.jvmName, env.javaVersion, "");
            if (!safe(env.serverInfo).isEmpty()) {
                addCdxComponent(components, "application", "server", env.serverInfo, env.serverVersion, "");
            }
            if (!safe(env.dbProduct).isEmpty()) {
                addCdxComponent(components, "application", "database", env.dbProduct, env.dbVersion, "");
            }
            if (env.ollamaConfigured) {
                addCdxComponent(components, "service", "ollama", "Ollama LLM", env.ollamaModel, "");
            }
            for (ComponentItem fe : snap.frontendLibraries) {
                addCdxComponent(components, "library", fe.group, fe.name, fe.version, fe.purl);
            }
            for (ComponentItem be : snap.backendLibraries) {
                addCdxComponent(components, "library", be.group, be.name, be.version, be.purl);
            }
            root.put("components", components);
            return MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize CycloneDX 1.5 SBOM JSON", e);
        }
    }

    public static String exportCsv(SbomSnapshot snap) {
        EnvironmentInfo env = snap.environment != null ? snap.environment : new EnvironmentInfo();
        StringBuilder sb = new StringBuilder("\uFEFF");
        sb.append("Category,Group,Name,Version,Status,PURL\n");

        String appPurl = safe(env.appVersion).isEmpty() ? "" : "pkg:maven/info.jtrac/jtrac-ng@" + env.appVersion;
        appendCsvRow(sb, "Environment", "application", "JTrac NG", env.appVersion, "ACTIVE", appPurl);
        appendCsvRow(sb, "Environment", "os", env.osName + " (" + env.osArch + ")", env.osVersion, "ACTIVE", "");
        appendCsvRow(sb, "Environment", "java", env.jvmName, env.javaVersion, env.javaVendor, "");
        appendCsvRow(sb, "Environment", "server", env.serverInfo, env.serverVersion, "ACTIVE", "");
        appendCsvRow(sb, "Environment", "database", env.dbProduct, env.dbVersion,
                (env.jdbcDriver + " " + env.jdbcDriverVersion).trim(), "");
        appendCsvRow(sb, "Environment", "ollama", "Ollama LLM", env.ollamaModel,
                env.ollamaConfigured ? "ENABLED" : "DISABLED", "");

        for (ComponentItem sub : snap.subsystems) {
            appendCsvRow(sb, "Subsystem", sub.group, sub.name, sub.version, sub.status, sub.purl);
        }
        for (ComponentItem fe : snap.frontendLibraries) {
            appendCsvRow(sb, "Frontend", fe.group, fe.name, fe.version, fe.status, fe.purl);
        }
        for (ComponentItem be : snap.backendLibraries) {
            appendCsvRow(sb, "Backend", be.group, be.name, be.version, be.status, be.purl);
        }
        return sb.toString();
    }

    private static void addCdxComponent(List<Map<String, Object>> list, String type, String group, String name, String version, String purl) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("type", type);
        if (!safe(group).isEmpty()) {
            m.put("group", safe(group));
        }
        m.put("name", safe(name));
        if (!safe(version).isEmpty()) {
            m.put("version", safe(version));
        }
        if (!safe(purl).isEmpty()) {
            m.put("purl", safe(purl));
        }
        list.add(m);
    }

    private static void appendCsvRow(StringBuilder sb, String... cols) {
        for (int i = 0; i < cols.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            String v = cols[i] == null ? "" : cols[i];
            if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
                sb.append('"').append(v.replace("\"", "\"\"")).append('"');
            } else {
                sb.append(v);
            }
        }
        sb.append('\n');
    }

    private static EnvironmentInfo collectEnvironment(Jtrac jtrac, DataSource dataSource, ServletContext servletContext) {
        EnvironmentInfo env = new EnvironmentInfo();
        if (jtrac != null && jtrac.getReleaseVersion() != null && !jtrac.getReleaseVersion().trim().isEmpty()) {
            env.appVersion = jtrac.getReleaseVersion().trim();
        }
        env.osName = System.getProperty("os.name", "");
        env.osVersion = System.getProperty("os.version", "");
        env.osArch = System.getProperty("os.arch", "");
        env.javaVersion = System.getProperty("java.version", "");
        env.javaVendor = System.getProperty("java.vendor", "");
        env.jvmName = System.getProperty("java.vm.name", "");

        if (servletContext != null && servletContext.getServerInfo() != null && !servletContext.getServerInfo().trim().isEmpty()) {
            String rawInfo = servletContext.getServerInfo().trim();
            env.serverInfo = rawInfo;
            int slash = rawInfo.indexOf('/');
            env.serverVersion = slash >= 0 ? rawInfo.substring(slash + 1).trim() : "";
        } else {
            try {
                Class<?> jettyClass = Class.forName("org.eclipse.jetty.util.Jetty");
                Object ver = jettyClass.getField("VERSION").get(null);
                if (ver != null) {
                    env.serverInfo = "Eclipse Jetty/" + ver;
                    env.serverVersion = ver.toString();
                }
            } catch (Throwable ignored) {
            }
        }

        if (dataSource != null) {
            try (Connection conn = dataSource.getConnection()) {
                DatabaseMetaData md = conn.getMetaData();
                if (md != null) {
                    env.dbProduct = safe(md.getDatabaseProductName());
                    env.dbVersion = safe(md.getDatabaseProductVersion());
                    env.jdbcDriver = safe(md.getDriverName());
                    env.jdbcDriverVersion = safe(md.getDriverVersion());
                }
            } catch (Exception e) {
                logger.debug("Unable to inspect JDBC DatabaseMetaData for SBOM: {}", e.getMessage());
            }
        }

        Map<String, String> cfg = jtrac != null ? jtrac.loadAllConfig() : Collections.<String, String>emptyMap();
        if (cfg == null) {
            cfg = Collections.emptyMap();
        }
        String ollamaUrl = safe(cfg.get("llm.ollama.url"));
        String ollamaModel = safe(cfg.get("llm.ollama.model"));
        env.ollamaConfigured = !ollamaUrl.isEmpty() && !ollamaModel.isEmpty();
        env.ollamaModel = env.ollamaConfigured ? ollamaModel : "";
        return env;
    }

    private static List<ComponentItem> collectSubsystems(Jtrac jtrac, List<ComponentItem> backendLibraries) {
        List<ComponentItem> list = new ArrayList<ComponentItem>();
        Map<String, ComponentItem> byName = new HashMap<String, ComponentItem>();
        for (ComponentItem item : backendLibraries) {
            byName.put(item.name, item);
        }

        Map<String, String> cfg = jtrac != null ? jtrac.loadAllConfig() : Collections.<String, String>emptyMap();
        if (cfg == null) {
            cfg = Collections.emptyMap();
        }

        // 1. Apache Lucene (real version from lucene-core JAR or Version.LATEST)
        ComponentItem luceneJar = byName.get("lucene-core");
        String luceneVer = luceneJar != null && !luceneJar.version.isEmpty()
                ? luceneJar.version
                : (Version.LATEST != null ? Version.LATEST.toString() : "");
        list.add(new ComponentItem("subsystem", "org.apache.lucene",
                "Apache Lucene Full-Text Search Engine", luceneVer, "ENABLED",
                luceneVer.isEmpty() ? "" : "pkg:maven/org.apache.lucene/lucene-core@" + luceneVer));

        // 2. OpenCC (real version from opencc4j JAR)
        ComponentItem openccJar = byName.get("opencc4j");
        if (openccJar != null) {
            list.add(new ComponentItem("subsystem", openccJar.group,
                    "OpenCC (opencc4j)", openccJar.version, "ENABLED", openccJar.purl));
        }

        // 3. CommonMark (real version from commonmark JAR)
        ComponentItem commonmarkJar = byName.get("commonmark");
        if (commonmarkJar != null) {
            boolean markdownOn = "true".equalsIgnoreCase(cfg.get("markdown.enabled"));
            list.add(new ComponentItem("subsystem", commonmarkJar.group,
                    "CommonMark Markdown Renderer", commonmarkJar.version,
                    markdownOn ? "ENABLED" : "DISABLED", commonmarkJar.purl));
        }

        // 4. Ollama LLM (simple configured status + model name)
        String ollamaUrl = safe(cfg.get("llm.ollama.url"));
        String ollamaModel = safe(cfg.get("llm.ollama.model"));
        boolean hasOllama = !ollamaUrl.isEmpty() && !ollamaModel.isEmpty();
        list.add(new ComponentItem("subsystem", "ollama",
                "Ollama LLM", hasOllama ? ollamaModel : "",
                hasOllama ? "ENABLED" : "DISABLED", ""));

        return list;
    }

    private static List<ComponentItem> collectFrontendLibraries(ServletContext servletContext) {
        List<ComponentItem> list = new ArrayList<ComponentItem>();
        Map<String, String> jsHeaders = collectBundledJsHeaders(servletContext);

        // 1. jQuery (real bundled file under /resources/)
        for (Map.Entry<String, String> entry : jsHeaders.entrySet()) {
            String relPath = entry.getKey();
            String fileName = relPath.substring(relPath.lastIndexOf('/') + 1);
            String lower = fileName.toLowerCase();
            if (lower.startsWith("jquery-") && lower.endsWith(".js")) {
                String ver = extractPattern(entry.getValue(), JQUERY_VER_PATTERN);
                if (ver.isEmpty()) {
                    Matcher fnm = JAR_FILENAME_PATTERN.matcher(fileName.replace(".min.js", "").replace(".js", ""));
                    if (fnm.matches()) {
                        ver = fnm.group(2);
                    }
                }
                String purl = ver.isEmpty() ? "" : "pkg:npm/jquery@" + ver;
                list.add(new ComponentItem("frontend", "jquery", "jQuery (" + fileName + ")", ver, "BUNDLED", purl));
            } else if ("jquery.enhanced-switch.js".equals(lower)) {
                String ver = extractPattern(entry.getValue(), GENERIC_JS_VER_PATTERN);
                list.add(new ComponentItem("frontend", "jquery-plugin", fileName, ver, "BUNDLED", ""));
            }
        }

        // 2. YUI (real bundled modules under /resources/yui/)
        Set<String> yuiModules = new TreeSet<String>();
        String yuiVer = "";
        for (Map.Entry<String, String> entry : jsHeaders.entrySet()) {
            String relPath = entry.getKey();
            if (relPath.startsWith("/resources/yui/") && relPath.endsWith(".js")) {
                String fileName = relPath.substring(relPath.lastIndexOf('/') + 1);
                String modName = fileName.replace("-min.js", "").replace(".js", "");
                yuiModules.add(modName);
                if (yuiVer.isEmpty()) {
                    yuiVer = extractPattern(entry.getValue(), YUI_VER_PATTERN);
                }
            }
        }
        if (!yuiModules.isEmpty()) {
            String label = "YUI (" + String.join(", ", yuiModules) + ")";
            list.add(new ComponentItem("frontend", "yahoo", label, yuiVer, "BUNDLED", ""));
        }

        // 3. Mermaid.js (real bundled classpath resource info/jtrac/mail/mermaid.min.js)
        try (InputStream is = SbomService.class.getResourceAsStream("/info/jtrac/mail/mermaid.min.js")) {
            if (is != null) {
                byte[] bytes = is.readAllBytes();
                String content = new String(bytes, StandardCharsets.UTF_8);
                String ver = extractPattern(content, MERMAID_VER_PATTERN);
                String purl = ver.isEmpty() ? "" : "pkg:npm/mermaid@" + ver;
                list.add(new ComponentItem("frontend", "mermaid", "Mermaid.js (mermaid.min.js)", ver, "BUNDLED", purl));
            }
        } catch (Exception e) {
            logger.debug("Unable to inspect bundled mermaid.min.js: {}", e.getMessage());
        }

        Collections.sort(list, new Comparator<ComponentItem>() {
            public int compare(ComponentItem a, ComponentItem b) {
                return a.name.toLowerCase().compareTo(b.name.toLowerCase());
            }
        });
        return list;
    }

    private static Map<String, String> collectBundledJsHeaders(ServletContext servletContext) {
        Map<String, String> headers = new LinkedHashMap<String, String>();
        if (servletContext != null) {
            scanServletJsResources(servletContext, "/resources/", headers);
        }
        // Fallback for unit tests / dev mode when ServletContext has no /resources/ mounted
        if (headers.isEmpty()) {
            Path devRes = Paths.get("src/main/webapp/resources");
            if (Files.isDirectory(devRes)) {
                try {
                    Files.walk(devRes)
                            .filter(p -> Files.isRegularFile(p) && p.getFileName().toString().toLowerCase().endsWith(".js"))
                            .forEach(p -> {
                                String rel = "/resources/" + devRes.relativize(p).toString().replace('\\', '/');
                                try (InputStream is = Files.newInputStream(p)) {
                                    headers.put(rel, readHeadText(is, 2048));
                                } catch (Exception ignored) {
                                }
                            });
                } catch (Exception e) {
                    logger.debug("Unable to scan dev webapp resources: {}", e.getMessage());
                }
            }
        }
        return headers;
    }

    private static void scanServletJsResources(ServletContext sc, String dirPath, Map<String, String> out) {
        try {
            Set<String> paths = sc.getResourcePaths(dirPath);
            if (paths == null) {
                return;
            }
            for (String p : paths) {
                if (p == null) {
                    continue;
                }
                if (p.endsWith("/")) {
                    scanServletJsResources(sc, p, out);
                } else if (p.toLowerCase().endsWith(".js")) {
                    try (InputStream is = sc.getResourceAsStream(p)) {
                        if (is != null) {
                            out.put(p, readHeadText(is, 2048));
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Unable to enumerate servlet resources under {}: {}", dirPath, e.getMessage());
        }
    }

    private static String readHeadText(InputStream is, int maxBytes) throws Exception {
        byte[] buf = new byte[maxBytes];
        int total = 0;
        int n;
        while (total < maxBytes && (n = is.read(buf, total, maxBytes - total)) != -1) {
            total += n;
        }
        return new String(buf, 0, total, StandardCharsets.UTF_8);
    }

    private static String extractPattern(String text, Pattern pattern) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        Matcher m = pattern.matcher(text);
        return m.find() ? safe(m.group(1)) : "";
    }

    private static List<ComponentItem> collectBackendLibraries(ServletContext servletContext) {
        Map<String, ComponentItem> byFileName = new LinkedHashMap<String, ComponentItem>();

        // 1. Prefer deployed WEB-INF/lib/*.jar inside WAR container (1-to-1 exact inventory)
        if (servletContext != null) {
            try {
                Set<String> paths = servletContext.getResourcePaths("/WEB-INF/lib/");
                if (paths != null && !paths.isEmpty()) {
                    for (String path : paths) {
                        if (path == null || !path.endsWith(".jar")) {
                            continue;
                        }
                        String fileName = path.substring(path.lastIndexOf('/') + 1);
                        try (InputStream is = servletContext.getResourceAsStream(path)) {
                            if (is != null) {
                                ComponentItem item = inspectJarStream(fileName, is);
                                byFileName.putIfAbsent(fileName.toLowerCase(), item);
                            }
                        } catch (Exception e) {
                            logger.debug("Unable to inspect WAR JAR {}: {}", fileName, e.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                logger.debug("Unable to enumerate /WEB-INF/lib/: {}", e.getMessage());
            }
        }

        // 2. Fallback to java.class.path JARs (embedded/dev/test mode)
        if (byFileName.isEmpty()) {
            String cp = System.getProperty("java.class.path", "");
            for (String entry : cp.split(Pattern.quote(File.pathSeparator))) {
                if (!entry.endsWith(".jar")) {
                    continue;
                }
                File f = new File(entry);
                if (!f.isFile()) {
                    continue;
                }
                try (JarFile jf = new JarFile(f)) {
                    ComponentItem item = inspectJarFile(f.getName(), jf);
                    byFileName.putIfAbsent(f.getName().toLowerCase(), item);
                } catch (Exception e) {
                    logger.debug("Unable to inspect classpath JAR {}: {}", f.getName(), e.getMessage());
                }
            }
        }

        List<ComponentItem> list = new ArrayList<ComponentItem>(byFileName.values());
        Collections.sort(list, new Comparator<ComponentItem>() {
            public int compare(ComponentItem a, ComponentItem b) {
                int c = a.group.toLowerCase().compareTo(b.group.toLowerCase());
                return c != 0 ? c : a.name.toLowerCase().compareTo(b.name.toLowerCase());
            }
        });
        return list;
    }

    static ComponentItem inspectJarStream(String fileName, InputStream is) throws Exception {
        String group = "";
        String name = "";
        String version = "";
        Manifest manifest;
        try (JarInputStream jis = new JarInputStream(is)) {
            manifest = jis.getManifest();
            JarEntry entry;
            while ((entry = jis.getNextJarEntry()) != null) {
                String entryName = entry.getName();
                if (entryName.startsWith("META-INF/maven/") && entryName.endsWith("/pom.properties")) {
                    Properties props = new Properties();
                    props.load(jis);
                    group = safe(props.getProperty("groupId"));
                    name = safe(props.getProperty("artifactId"));
                    version = safe(props.getProperty("version"));
                    if (!name.isEmpty() && !version.isEmpty()) {
                        break;
                    }
                }
            }
        }
        return finalizeJarComponent(fileName, group, name, version, manifest);
    }

    static ComponentItem inspectJarFile(String fileName, JarFile jf) throws Exception {
        String group = "";
        String name = "";
        String version = "";
        Manifest manifest = jf.getManifest();
        for (JarEntry entry : Collections.list(jf.entries())) {
            String entryName = entry.getName();
            if (entryName.startsWith("META-INF/maven/") && entryName.endsWith("/pom.properties")) {
                try (InputStream is = jf.getInputStream(entry)) {
                    Properties props = new Properties();
                    props.load(is);
                    group = safe(props.getProperty("groupId"));
                    name = safe(props.getProperty("artifactId"));
                    version = safe(props.getProperty("version"));
                    if (!name.isEmpty() && !version.isEmpty()) {
                        break;
                    }
                }
            }
        }
        return finalizeJarComponent(fileName, group, name, version, manifest);
    }

    private static ComponentItem finalizeJarComponent(String fileName, String group, String name, String version, Manifest manifest) {
        // Parse artifactId and version cleanly from JAR filename if pom.properties was absent
        String fnName = "";
        String fnVer = "";
        if (fileName.endsWith(".jar")) {
            String base = fileName.substring(0, fileName.length() - 4);
            Matcher m = JAR_FILENAME_PATTERN.matcher(base);
            if (m.matches()) {
                fnName = safe(m.group(1));
                fnVer = safe(m.group(2));
            } else {
                fnName = base;
            }
        } else {
            fnName = fileName;
        }

        if (name.isEmpty()) {
            name = fnName;
        }
        if (version.isEmpty()) {
            version = fnVer;
        }

        // Only consult MANIFEST.MF if filename still lacked version or groupId
        if (manifest != null) {
            Attributes attrs = manifest.getMainAttributes();
            if (version.isEmpty()) {
                String rawVer = firstNonEmpty(
                        attrs.getValue("Implementation-Version"),
                        attrs.getValue("Bundle-Version"),
                        attrs.getValue("Specification-Version"));
                version = cleanManifestVersion(rawVer);
            }
            if (group.isEmpty()) {
                String vendorId = safe(attrs.getValue("Implementation-Vendor-Id"));
                if (!vendorId.isEmpty() && MAVEN_GROUP_PATTERN.matcher(vendorId).matches() && vendorId.contains(".")) {
                    group = vendorId;
                }
            }
        }

        if (group.isEmpty()) {
            group = inferKnownGroupId(name);
        }

        String purl = (!group.isEmpty() && !name.isEmpty() && !version.isEmpty())
                ? "pkg:maven/" + group + "/" + name + "@" + version
                : "";
        return new ComponentItem("backend", group, name, version, "LOADED", purl);
    }

    private static String cleanManifestVersion(String raw) {
        String s = safe(raw);
        if (s.isEmpty()) {
            return "";
        }
        int space = s.indexOf(' ');
        if (space > 0) {
            s = s.substring(0, space).trim();
        }
        return s;
    }

    private static String inferKnownGroupId(String artifactName) {
        String lower = safe(artifactName).toLowerCase();
        if (lower.isEmpty()) {
            return "";
        }
        String exact = KNOWN_ARTIFACT_GROUPS.get(lower);
        if (exact != null) {
            return exact;
        }
        if (lower.startsWith("lucene-")) {
            return "org.apache.lucene";
        }
        if (lower.startsWith("spring-security-")) {
            return "org.springframework.security";
        }
        if (lower.startsWith("spring-")) {
            return "org.springframework";
        }
        if (lower.startsWith("micrometer-")) {
            return "io.micrometer";
        }
        if (lower.startsWith("bcprov-") || lower.startsWith("bcpkix-") || lower.startsWith("bcutil-")) {
            return "org.bouncycastle";
        }
        return "";
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private static String firstNonEmpty(String... values) {
        for (String v : values) {
            if (v != null && !v.trim().isEmpty()) {
                return v.trim();
            }
        }
        return "";
    }
}
