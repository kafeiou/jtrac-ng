package info.jtrac.backup.service;

import info.jtrac.JtracDao;
import info.jtrac.backup.model.BackupManifest;
import info.jtrac.backup.model.SystemBackupData;
import info.jtrac.domain.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class BackupExportAndZipTest {

    @TempDir
    Path tempDir;

    @Test
    public void testExportAndZipRoundtrip() throws Exception {
        // 1. Setup mock/stub DAO
        StubJtracDao dao = new StubJtracDao();

        Config cfg = new Config("sys.theme", "default");
        dao.configs.add(cfg);

        Metadata md = new Metadata();
        md.setId(1L);
        md.setName("Default Meta");
        md.setXmlString("<metadata/>");
        dao.metadatas.add(md);

        Space space = new Space();
        space.setId(10L);
        space.setPrefixCode("PROJ");
        space.setName("Project Alpha");
        space.setMetadata(md);
        dao.spaces.add(space);

        User user = new User();
        user.setId(100L);
        user.setLoginName("admin");
        user.setName("Administrator");
        user.setPassword("hashedPass");
        dao.users.add(user);

        UserSpaceRole usr = new UserSpaceRole(user, space, Role.ROLE_ADMIN);
        usr.setId(200L);
        dao.userSpaceRoles.add(usr);

        Item item = new Item();
        item.setId(500L);
        item.setSpace(space);
        item.setSequenceNum(1L);
        item.setLoggedBy(user);
        item.setSummary("Initial ticket");
        item.setDetail("Ticket details");
        item.setTimeStamp(new Date());
        dao.items.add(item);

        History history = new History();
        history.setId(600L);
        history.setParent(item);
        history.setLoggedBy(user);
        history.setComment("First comment");
        history.setTimeStamp(new Date());
        dao.histories.add(history);

        Attachment att = new Attachment();
        att.setId(700L);
        att.setFilePrefix(700L);
        att.setFileName("sample.txt");
        dao.attachments.add(att);

        // 2. Setup mock jtracHome with attachments/
        File mockHome = tempDir.resolve("jtracHome").toFile();
        File attachDir = new File(mockHome, "attachments");
        attachDir.mkdirs();

        File sampleFile = new File(attachDir, "700_sample.txt");
        Files.write(sampleFile.toPath(), "Hello JTrac Backup!".getBytes(StandardCharsets.UTF_8));

        // 3. Export data & manifest
        BackupExportService exportService = new BackupExportService(dao, "2.3.3-test", mockHome.getAbsolutePath());
        SystemBackupData data = exportService.exportSystemData();
        assertEquals(1, data.getConfigs().size());
        assertEquals(1, data.getSpaces().size());
        assertEquals(1, data.getUsers().size());
        assertEquals(1, data.getItems().size());
        assertEquals(1, data.getHistories().size());
        assertEquals(1, data.getAttachments().size());

        BackupManifest manifest = exportService.createManifest(data, "admin");
        assertEquals("admin", manifest.getOperatorLoginName());
        assertEquals(1, manifest.getTotalAttachmentFiles());
        assertTrue(manifest.getTotalAttachmentBytes() > 0);

        // 4. Bundle into ZIP
        ZipBundleService zipService = new ZipBundleService();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        zipService.createBackupZip(manifest, data, mockHome.getAbsolutePath(), baos);

        byte[] zipBytes = baos.toByteArray();
        assertTrue(zipBytes.length > 0);

        // 5. Extract and verify roundtrip
        File extractDir = tempDir.resolve("extracted").toFile();
        ZipBundleService.ZipExtractResult result = zipService.extractAndParseZip(
                new ByteArrayInputStream(zipBytes), extractDir);

        assertNotNull(result.getManifest());
        assertEquals("admin", result.getManifest().getOperatorLoginName());
        assertEquals("2.3.3-test", result.getManifest().getJtracVersion());

        assertNotNull(result.getData());
        assertEquals("PROJ", result.getData().getSpaces().get(0).getPrefixCode());
        assertEquals("Initial ticket", result.getData().getItems().get(0).getSummary());

        // Verify attachment file extraction
        File extractedFile = new File(extractDir, "attachments/700_sample.txt");
        assertTrue(extractedFile.exists());
        assertEquals("Hello JTrac Backup!", new String(Files.readAllBytes(extractedFile.toPath()), StandardCharsets.UTF_8));
    }

    @Test
    public void testSqlDumpGenerationAndZipPackaging() throws Exception {
        StubJtracDao dao = new StubJtracDao();

        Config cfg = new Config("users.list.pageSize", "25");
        dao.configs.add(cfg);

        Tag tag = new Tag();
        tag.setId(5L);
        tag.setName("backend");
        tag.setDescription("Backend tickets");
        dao.tags.add(tag);

        Metadata md = new Metadata();
        md.setId(1L);
        md.setName("Default Meta");
        md.setXmlString("<metadata/>");
        dao.metadatas.add(md);

        Space space = new Space();
        space.setId(10L);
        space.setPrefixCode("TEST");
        space.setName("Test Project");
        space.setMetadata(md);
        space.setGuestAllowed(true);
        space.setIsActive(true);
        dao.spaces.add(space);

        User user = new User();
        user.setId(20L);
        user.setLoginName("john_doe");
        user.setName("John O'Connor");
        user.setPassword("hashedPass");
        user.setEmail("john@example.com");
        user.setLocked(false);
        user.setPrettyDates(true);
        dao.users.add(user);

        UserSpaceRole usr = new UserSpaceRole(user, space, Role.ROLE_ADMIN);
        usr.setId(30L);
        dao.userSpaceRoles.add(usr);

        Item item = new Item();
        item.setId(100L);
        item.setSpace(space);
        item.setSequenceNum(1L);
        item.setLoggedBy(user);
        item.setSummary("Fix user's profile bug");
        item.setDetail("Quotes in detail: 'O'Reilly' and \"double quotes\"");
        item.setStatus(1);
        item.setTimeStamp(new Date());
        dao.items.add(item);

        History history = new History();
        history.setId(200L);
        history.setParent(item);
        history.setLoggedBy(user);
        history.setComment("Fixed user's issue in rev 123");
        history.setTimeStamp(new Date());
        dao.histories.add(history);

        File mockHome = tempDir.resolve("jtracHomeSql").toFile();
        mockHome.mkdirs();

        BackupExportService exportService = new BackupExportService(dao, "2.3.3-2.0.0", mockHome.getAbsolutePath());
        SystemBackupData data = exportService.exportSystemData();
        BackupManifest manifest = exportService.createManifest(data, "admin");

        // 1. Generate SQL dump
        String sql = exportService.generateSqlDump(data, "HSQL Database Engine 2.7.2");
        assertNotNull(sql);

        // Header assertions
        assertTrue(sql.contains("JTrac Database Dump (jtrac-dump.sql)"));
        assertTrue(sql.contains("HSQL Database Engine 2.7.2"));
        assertTrue(sql.contains("2.3.3-2.0.0"));

        // DDL assertions
        assertTrue(sql.contains("CREATE TABLE config ("));
        assertTrue(sql.contains("CREATE TABLE tags ("));
        assertTrue(sql.contains("CREATE TABLE storedsearch ("));
        assertTrue(sql.contains("CREATE TABLE metadata ("));
        assertTrue(sql.contains("CREATE TABLE space_sequence ("));
        assertTrue(sql.contains("CREATE TABLE spaces ("));
        assertTrue(sql.contains("CREATE TABLE users ("));
        assertTrue(sql.contains("CREATE TABLE user_space_roles ("));
        assertTrue(sql.contains("CREATE TABLE attachments ("));
        assertTrue(sql.contains("CREATE TABLE items ("));
        assertTrue(sql.contains("CREATE TABLE item_items ("));
        assertTrue(sql.contains("CREATE TABLE item_users ("));
        assertTrue(sql.contains("CREATE TABLE item_tags ("));
        assertTrue(sql.contains("CREATE TABLE history ("));

        // Dialect reference comments assertions
        assertTrue(sql.contains("-- MySQL:"));
        assertTrue(sql.contains("-- PostgreSQL:"));
        assertTrue(sql.contains("-- HSQLDB:"));

        // Single quote escaping assertions
        assertTrue(sql.contains("'John O''Connor'"));
        assertTrue(sql.contains("'Fix user''s profile bug'"));
        assertTrue(sql.contains("'Quotes in detail: ''O''Reilly'' and \"double quotes\"'"));
        assertTrue(sql.contains("'Fixed user''s issue in rev 123'"));

        // Sequence / Auto-Increment assertions
        assertTrue(sql.contains("-- ALTER TABLE items AUTO_INCREMENT = 101;"));
        assertTrue(sql.contains("-- ALTER TABLE history AUTO_INCREMENT = 201;"));
        assertTrue(sql.contains("-- SELECT setval(pg_get_serial_sequence('items', 'id'), 100, true);"));
        assertTrue(sql.contains("-- ALTER TABLE items ALTER COLUMN id RESTART WITH 101;"));

        // 2. Package into ZIP with SQL dump
        ZipBundleService zipService = new ZipBundleService();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        zipService.createBackupZip(manifest, data, sql, mockHome.getAbsolutePath(), baos);

        byte[] zipBytes = baos.toByteArray();
        assertTrue(zipBytes.length > 0);

        // 3. Extract and verify jtrac-dump.sql is present
        File extractDir = tempDir.resolve("extractedSql").toFile();
        ZipBundleService.ZipExtractResult result = zipService.extractAndParseZip(
                new ByteArrayInputStream(zipBytes), extractDir);

        assertNotNull(result.getManifest());
        assertNotNull(result.getData());

        File extractedSqlFile = new File(extractDir, "jtrac-dump.sql");
        assertTrue(extractedSqlFile.exists(), "jtrac-dump.sql must exist in extracted backup root");
        String extractedSqlContent = new String(Files.readAllBytes(extractedSqlFile.toPath()), StandardCharsets.UTF_8);
        assertEquals(sql, extractedSqlContent, "Extracted SQL content must match generated SQL dump");
    }

    // Minimal DAO Stub for testing export
    private static class StubJtracDao implements JtracDao {
        List<Config> configs = new ArrayList<>();
        List<StoredSearch> storedSearches = new ArrayList<>();
        List<Metadata> metadatas = new ArrayList<>();
        List<Space> spaces = new ArrayList<>();
        List<SpaceSequence> spaceSequences = new ArrayList<>();
        List<User> users = new ArrayList<>();
        List<UserSpaceRole> userSpaceRoles = new ArrayList<>();
        List<Tag> tags = new ArrayList<>();
        List<Item> items = new ArrayList<>();
        List<ItemItem> itemItems = new ArrayList<>();
        List<ItemUser> itemUsers = new ArrayList<>();
        List<ItemTag> itemTags = new ArrayList<>();
        List<Attachment> attachments = new ArrayList<>();
        List<History> histories = new ArrayList<>();

        @Override public List<Config> findAllConfig() { return configs; }
        @Override public List<StoredSearch> findAllStoredSearch() { return storedSearches; }
        @Override public List<Metadata> findAllMetadata() { return metadatas; }
        @Override public List<Space> findAllSpaces() { return spaces; }
        @Override public List<SpaceSequence> findAllSpaceSequences() { return spaceSequences; }
        @Override public List<User> findAllUsers() { return users; }
        @Override public List<UserSpaceRole> findAllUserSpaceRoles() { return userSpaceRoles; }
        @Override public List<Tag> findAllTags() { return tags; }
        @Override public List<Item> findAllItems() { return items; }
        @Override public List<ItemItem> findAllItemItems() { return itemItems; }
        @Override public List<ItemUser> findAllItemUsers() { return itemUsers; }
        @Override public List<ItemTag> findAllItemTags() { return itemTags; }
        @Override public List<Attachment> findAllAttachments() { return attachments; }
        @Override public List<History> findAllHistories() { return histories; }

        // Unused methods in export
        @Override public void storeItem(Item item) {}
        @Override public Item loadItem(long id) { return null; }
        @Override public History loadHistory(long id) { return null; }
        @Override public void storeHistory(History history) {}
        @Override public List<Item> findItems(long sequenceNum, String prefixCode) { return Collections.emptyList(); }
        @Override public List<Item> findItems(ItemSearch itemSearch) { return Collections.emptyList(); }
        @Override public int loadCountOfAllItems() { return items.size(); }
        @Override public List<Item> findAllItems(int firstResult, int batchSize) { return items; }
        @Override public void removeItem(Item item) {}
        @Override public void removeItemItem(ItemItem itemItem) {}
        @Override public List<ItemUser> findItemUsersByUser(User user) { return Collections.emptyList(); }
        @Override public void removeItemUser(ItemUser itemUser) {}
        @Override public int loadCountOfRecordsHavingFieldNotNull(Space space, Field field) { return 0; }
        @Override public int bulkUpdateFieldToNull(Space space, Field field) { return 0; }
        @Override public int loadCountOfRecordsHavingFieldWithValue(Space space, Field field, int optionKey) { return 0; }
        @Override public int bulkUpdateFieldToNullForValue(Space space, Field field, int optionKey) { return 0; }
        @Override public int loadCountOfRecordsHavingStatus(Space space, int status) { return 0; }
        @Override public int bulkUpdateStatusToOpen(Space space, int status) { return 0; }
        @Override public int bulkUpdateRenameSpaceRole(Space space, String oldRoleKey, String newRoleKey) { return 0; }
        @Override public int bulkUpdateDeleteSpaceRole(Space space, String roleKey) { return 0; }
        @Override public int bulkUpdateDeleteItemsForSpace(Space space) { return 0; }
        @Override public void storeAttachment(Attachment attachment) {}
        @Override public void storeMetadata(Metadata metadata) {}
        @Override public Metadata loadMetadata(long id) { return null; }
        @Override public void storeSpace(Space space) {}
        @Override public Space loadSpace(long id) { return null; }
        @Override public List<Space> findSpacesByPrefixCode(String prefixCode) { return Collections.emptyList(); }
        @Override public List<Space> findSpacesNotAllocatedToUser(long userId) { return Collections.emptyList(); }
        @Override public List<Space> findSpacesWhereIdIn(List<Long> ids) { return Collections.emptyList(); }
        @Override public List<Space> findSpacesWhereGuestAllowed() { return Collections.emptyList(); }
        @Override public void removeSpace(Space space) {}
        @Override public long loadNextSequenceNum(long spaceSequenceId) { return 1; }
        @Override public void storeSpaceSequence(SpaceSequence spaceSequence) {}
        @Override public void storeUser(User user) {}
        @Override public User loadUser(long id) { return null; }
        @Override public void removeUser(User user) {}
        @Override public List<User> findUsersWhereIdIn(List<Long> ids) { return Collections.emptyList(); }
        @Override public List<User> findUsersMatching(String searchText, String searchOn) { return Collections.emptyList(); }
        @Override public List<User> findUsersByLoginName(String loginName) { return Collections.emptyList(); }
        @Override public List<User> findUsersByEmail(String email) { return Collections.emptyList(); }
        @Override public List<User> findUsersForSpace(long spaceId) { return Collections.emptyList(); }
        @Override public List<User> findUsersNotAllocatedToSpace(long spaceId) { return Collections.emptyList(); }
        @Override public List<UserSpaceRole> findUserRolesForSpace(long spaceId) { return Collections.emptyList(); }
        @Override public List<UserSpaceRole> findSpaceRolesForUser(long userId) { return Collections.emptyList(); }
        @Override public List<User> findUsersWithRoleForSpace(long spaceId, String roleKey) { return Collections.emptyList(); }
        @Override public List<User> findUsersForSpaceSet(Collection<Space> spaces) { return Collections.emptyList(); }
        @Override public List<User> findSuperUsers() { return Collections.emptyList(); }
        @Override public int loadCountOfHistoryInvolvingUser(User user) { return 0; }
        @Override public UserSpaceRole loadUserSpaceRole(long id) { return null; }
        @Override public void removeUserSpaceRole(UserSpaceRole userSpaceRole) {}
        @Override public CountsHolder loadCountsForUser(User user) { return null; }
        @Override public Counts loadCountsForUserSpace(User user, Space space) { return null; }
        @Override public void storeConfig(Config config) {}
        @Override public Config loadConfig(String key) { return null; }
        @Override public void storeStoredSearch(StoredSearch storedSearch) {}
        @Override public StoredSearch loadStoredSearch(Long id) { return null; }
        @Override public void removeStoredSearch(StoredSearch storedSearchToDel) {}
        @Override public void clearSession() {}
        @Override public Map<Long, Long> findAttachmentFilePrefixToSpaceIdMap() { return Collections.emptyMap(); }
        @Override public List<Long> findFirstHistoryIdsForItems(java.util.Collection<Long> itemIds) { return Collections.emptyList(); }
    }
}
