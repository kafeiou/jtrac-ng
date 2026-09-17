/*
 * Copyright 2002-2005 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package info.jtrac;

import info.jtrac.domain.Attachment;
import info.jtrac.domain.BatchInfo;
import info.jtrac.domain.Config;
import info.jtrac.domain.Counts;
import info.jtrac.domain.CountsHolder;
import info.jtrac.domain.Field;
import info.jtrac.domain.History;
import info.jtrac.domain.Item;
import info.jtrac.domain.ItemItem;
import info.jtrac.domain.ItemRefId;
import info.jtrac.domain.ItemSearch;
import info.jtrac.domain.ItemUser;
import info.jtrac.domain.Metadata;
import info.jtrac.domain.Role;
import info.jtrac.domain.Space;
import info.jtrac.domain.SpaceSequence;
import info.jtrac.domain.State;
import info.jtrac.domain.StoredSearch;
import info.jtrac.domain.User;
import info.jtrac.domain.UserSpaceRole;
import info.jtrac.lucene.IndexSearcher;
import info.jtrac.lucene.Indexer;
import info.jtrac.lucene.SearchResultHits;
import info.jtrac.mail.InboundMailReceiver;
import info.jtrac.mail.MailSender;
import info.jtrac.tools.HsqldbDatabaseMigrator;
import info.jtrac.util.AttachmentStorageMigrator;
import info.jtrac.util.AttachmentTextExtractor;
import info.jtrac.util.AttachmentUtils;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.sql.DataSource;
import info.jtrac.backup.model.BackupManifest;
import info.jtrac.backup.model.SystemBackupData;
import info.jtrac.backup.service.BackupExportService;
import info.jtrac.backup.service.BackupRestoreService;
import info.jtrac.backup.service.ZipBundleService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import org.springframework.context.ApplicationListener;
import org.springframework.context.MessageSource;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import org.apache.wicket.markup.html.form.upload.FileUpload;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Jtrac Service Layer implementation
 * This is where all the business logic is
 * For data persistence this delegates to JtracDao
 */
public class JtracImpl implements Jtrac, org.springframework.context.ApplicationContextAware,
        ApplicationListener<ContextRefreshedEvent> {

    private static final Logger logger = LoggerFactory.getLogger(JtracImpl.class);

    private JtracDao dao;
    private PasswordEncoder passwordEncoder;
    private MailSender mailSender;
    private Indexer indexer;
    private IndexSearcher indexSearcher;
    private MessageSource messageSource;
    private DataSource dataSource;
    private BackupExportService backupExportService;
    private ZipBundleService zipBundleService;
    private BackupRestoreService backupRestoreService;
    private ExecutorService attachmentIndexExecutor;
    private org.springframework.context.ApplicationContext applicationContext;
    private PlatformTransactionManager transactionManager;
    private volatile boolean needsIndexRebuildAfterStartup;

    @Override
    public void setApplicationContext(org.springframework.context.ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    public void setTransactionManager(PlatformTransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    public PlatformTransactionManager getTransactionManager() {
        return transactionManager;
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        if (event.getApplicationContext().getParent() == null) {
            if (needsIndexRebuildAfterStartup) {
                needsIndexRebuildAfterStartup = false;
                logger.info("Application context fully refreshed. Launching background Lucene index rebuild for migrated legacy data...");
                startRebuildIndexes();
            }
        }
    }

    private Jtrac getJtracProxy() {
        if (applicationContext != null && applicationContext.containsBean("jtrac")) {
            try {
                return (Jtrac) applicationContext.getBean("jtrac");
            } catch (Exception ignored) {}
        }
        return this;
    }

    private Map<String, String> locales;
    private String defaultLocale = "en";
    private String releaseVersion;
    private String releaseTimestamp;
    private String jtracHome;
    private int attachmentMaxSizeInMb = 5;
    private int sessionTimeoutInMinutes = 30;

    public void setLocaleList(String[] array) {
        locales = new LinkedHashMap<String, String>();
        for(String localeString : array) {
            Locale locale = StringUtils.parseLocaleString(localeString);
            locales.put(localeString, localeString + " - " + locale.getDisplayName());
        }
        logger.info("available locales configured " + locales);
    }

    public void setDao(JtracDao dao) {
        this.dao = dao;
    }

    public void setPasswordEncoder(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    public void setIndexSearcher(IndexSearcher indexSearcher) {
        this.indexSearcher = indexSearcher;
    }

    public void setIndexer(Indexer indexer) {
        this.indexer = indexer;
    }

    public void setMessageSource(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public void setReleaseTimestamp(String releaseTimestamp) {
        this.releaseTimestamp = releaseTimestamp;
    }

    public void setReleaseVersion(String releaseVersion) {
        this.releaseVersion = releaseVersion;
    }

    public void setJtracHome(String jtracHome) {
        this.jtracHome = jtracHome;
    }

    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void setBackupExportService(BackupExportService backupExportService) {
        this.backupExportService = backupExportService;
    }

    public void setZipBundleService(ZipBundleService zipBundleService) {
        this.zipBundleService = zipBundleService;
    }

    public void setBackupRestoreService(BackupRestoreService backupRestoreService) {
        this.backupRestoreService = backupRestoreService;
    }

    public String getJtracHome() {
        return jtracHome;
    }

    public int getAttachmentMaxSizeInMb() {
        return attachmentMaxSizeInMb;
    }

    public int getAttachmentIndexMaxSizeInMb() {
        String val = loadConfig("attachment.index.maxSizeMb");
        if (val != null) {
            try {
                return Integer.parseInt(val.trim());
            } catch (NumberFormatException ignored) {}
        }
        return 10;
    }

    public int getAttachmentIndexMaxChars() {
        String val = loadConfig("attachment.index.maxChars");
        if (val != null) {
            try {
                return Integer.parseInt(val.trim());
            } catch (NumberFormatException ignored) {}
        }
        return 50000;
    }

    public void setAttachmentIndexExecutor(ExecutorService attachmentIndexExecutor) {
        this.attachmentIndexExecutor = attachmentIndexExecutor;
    }

    public ExecutorService getAttachmentIndexExecutor() {
        return attachmentIndexExecutor;
    }

    public void destroy() {
        logger.info("Shutting down JTrac service lifecycle...");
        if (attachmentIndexExecutor != null && !attachmentIndexExecutor.isShutdown()) {
            attachmentIndexExecutor.shutdown();
        }
    }

    public void indexHistoryAttachmentAsync(History history, long spaceId, Attachment attachment) {
        if (indexer == null || attachment == null) {
            return;
        }
        if (attachmentIndexExecutor == null || attachmentIndexExecutor.isShutdown()) {
            extractAndIndexAttachment(history, spaceId, attachment);
            return;
        }
        attachmentIndexExecutor.submit(() -> {
            try {
                extractAndIndexAttachment(history, spaceId, attachment);
            } catch (Throwable t) {
                logger.error("Error in async attachment indexer for history id: " + history.getId(), t);
            }
        });
    }

    private void extractAndIndexAttachment(History history, long spaceId, Attachment attachment) {
        try {
            File file = AttachmentUtils.getFile(attachment, spaceId, jtracHome);
            if (file != null && file.exists()) {
                int maxSizeMb = getAttachmentIndexMaxSizeInMb();
                int maxChars = getAttachmentIndexMaxChars();
                String text = AttachmentTextExtractor.extractText(file, maxSizeMb, maxChars);
                history.setAttachmentText(text);
            }
            indexer.index(history);
        } catch (Throwable t) {
            logger.error("Error extracting and indexing attachment for history id: " + history.getId(), t);
        }
    }

    public int getSessionTimeoutInMinutes() {
        return sessionTimeoutInMinutes;
    }

    /**
     * this has not been factored into the util package or a helper class
     * because it depends on the PasswordEncoder configured
     */
    public String generatePassword() {
        return java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    /**
     * this has not been factored into the util package or a helper class
     * because it depends on the PasswordEncoder configured
     */
    public String encodeClearText(String clearText) {
        return passwordEncoder.encode(clearText);
    }

    public Map<String, String> getLocales() {
        return locales;
    }

    public String getDefaultLocale() {
        return defaultLocale;
    }

    /**
     * this is automatically called by spring init-method hook on
     * startup, also called whenever config is edited to refresh
     * TODO move config into a settings class to reduce service clutter
     */
    public void init() {
        boolean attachmentsMigrated = false;
        if (jtracHome != null && dao != null) {
            attachmentsMigrated = AttachmentStorageMigrator.migrate(jtracHome, dao);
        }
        if (attachmentIndexExecutor == null || attachmentIndexExecutor.isShutdown()) {
            attachmentIndexExecutor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "attachment-indexer-thread");
                t.setDaemon(true);
                return t;
            });
        }
        Map<String, String> config = loadAllConfig();
        initDefaultLocale(config.get("locale.default"));
        initMailSender(config);
        initAttachmentMaxSize(config.get("attachment.maxsize"));
        initSessionTimeout(config.get("session.timeout"));
        initBackupServices();

        // Proactively rebuild Lucene indexes in background if legacy data was migrated or analyzer upgraded
        boolean dbMigrated = HsqldbDatabaseMigrator.isDatabaseMigrated();
        String analyzerVersion = config.get("lucene.analyzer.version");
        boolean analyzerUpgraded = !"3.0.0-subtoken-v1".equals(analyzerVersion);
        if (attachmentsMigrated || dbMigrated || analyzerUpgraded) {
            logger.info("Lucene index rebuild required (attachmentsMigrated={}, dbMigrated={}, analyzerUpgraded={}). Rebuild will be scheduled upon container startup completion.",
                    attachmentsMigrated, dbMigrated, analyzerUpgraded);
            this.needsIndexRebuildAfterStartup = true;
        }
    }

    private void initBackupServices() {
        if (backupExportService == null) {
            backupExportService = new BackupExportService(dao, releaseVersion, jtracHome);
        }
        if (zipBundleService == null) {
            zipBundleService = new ZipBundleService();
        }
        if (backupRestoreService == null && dataSource != null) {
            backupRestoreService = new BackupRestoreService(dataSource, dao, getJtracProxy(), backupExportService, zipBundleService, jtracHome);
        }
    }

    private void initMailSender(Map<String, String> config) {
        this.mailSender = new MailSender(config, messageSource, defaultLocale, jtracHome);
    }

    private void initDefaultLocale(String localeString) {
        if (localeString == null || !locales.containsKey(localeString)) {
            logger.warn("invalid default locale configured = '" + localeString + "', using " + this.defaultLocale);
        } else {
            this.defaultLocale = localeString;
        }
        logger.info("default locale set to '" + this.defaultLocale + "'");
    }

    private void initAttachmentMaxSize(String s) {
        try {
            this.attachmentMaxSizeInMb = Integer.parseInt(s);
        } catch(Exception e) {
            logger.warn("invalid attachment max size '" + s + "', using " + attachmentMaxSizeInMb);
        }
        logger.info("attachment max size set to " + this.attachmentMaxSizeInMb + " MB");
    }

    private void initSessionTimeout(String s) {
        try {
            this.sessionTimeoutInMinutes = Integer.parseInt(s);
        } catch(Exception e) {
            logger.warn("invalid session timeout '" + s + "', using " + this.sessionTimeoutInMinutes);
        }
        logger.info("session timeout set to " + this.sessionTimeoutInMinutes + " minutes");
    }

    //==========================================================================

    private Attachment getAttachment(FileUpload fileUpload) {
        if(fileUpload == null) {
            return null;
        }
        logger.debug("fileUpload not null");
        String fileName = AttachmentUtils.cleanFileName(fileUpload.getClientFileName());
        Attachment attachment = new Attachment();
        attachment.setFileName(fileName);
        dao.storeAttachment(attachment);
        attachment.setFilePrefix(attachment.getId());
        return attachment;
    }

    private void writeToFile(FileUpload fileUpload, Attachment attachment) {
        writeToFile(fileUpload, attachment, 0L);
    }

    private void writeToFile(FileUpload fileUpload, Attachment attachment, long spaceId) {
        if(fileUpload == null) {
            return;
        }
        File file = AttachmentUtils.getAttachmentFileForWrite(attachment, spaceId, jtracHome);
        try {
            fileUpload.writeTo(file);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    public synchronized void storeItem(Item item, FileUpload fileUpload) {
        History history = new History(item);
        Attachment attachment = getAttachment(fileUpload);
        if(attachment != null) {
            item.add(attachment);
            history.setAttachment(attachment);
        }
        // timestamp can be set by import, then retain
        Date now = item.getTimeStamp();
        if(now == null) {
            now = new Date();
        }
        item.setTimeStamp(now);
        history.setTimeStamp(now);
        item.add(history);
        item.setSequenceNum(dao.loadNextSequenceNum(item.getSpace().getId()));
        // this will at the moment execute unnecessary updates (bug in Hibernate handling of "version" property)
        // se http://opensource.atlassian.com/projects/hibernate/browse/HHH-1401
        // TODO confirm if above does not happen anymore
        dao.storeItem(item);
        writeToFile(fileUpload, attachment, item.getSpace().getId());
        if(indexer != null) {
            indexer.index(item);
            if(attachment != null) {
                indexHistoryAttachmentAsync(history, item.getSpace().getId(), attachment);
            } else {
                indexer.index(history);
            }
        }
        if (item.isSendNotifications()) {
            mailSender.send(item);
        }
    }

    public synchronized void storeItems(List<Item> items) {
        for(Item item : items) {
            item.setSendNotifications(false);
            if(item.getStatus() == State.CLOSED) {
                // we support CLOSED items for import also but for consistency
                // simulate the item first created OPEN and then being CLOSED
                item.setStatus(State.OPEN);
                History history = new History();
                history.setTimeStamp(item.getTimeStamp());
                // need to do this as storeHistoryForItem does some role checks
                // and so to avoid lazy initialization exception
                history.setLoggedBy(loadUser(item.getLoggedBy().getId()));
                history.setAssignedTo(item.getAssignedTo());
                history.setComment("-");
                history.setStatus(State.CLOSED);
                history.setSendNotifications(false);
                storeItem(item, null);
                storeHistoryForItem(item.getId(), history, null);
            } else {
                storeItem(item, null);
            }
        }
    }

    public synchronized void updateItem(Item item, User user) {
        logger.debug("update item called");
        History history = new History(item);
        history.setAssignedTo(null);
        history.setStatus(null);
        history.setLoggedBy(user);
        history.setComment(item.getEditReason());
        history.setTimeStamp(new Date());
        item.add(history);
        dao.storeItem(item);  // merge edits + history
        // TODO index?
        if (item.isSendNotifications()) {
            mailSender.send(item);
        }
    }

    public synchronized void storeHistoryForItem(long itemId, History history, FileUpload fileUpload) {
        Item item = dao.loadItem(itemId);
        // first apply edits onto item record before we change the item status
        // the item.getEditableFieldList routine depends on the current State of the item
        for(Field field : item.getEditableFieldList(history.getLoggedBy())) {
            Object value = history.getValue(field.getName());
            if (value != null) {
                item.setValue(field.getName(), value);
            }
        }
        if (history.getStatus() != null) {
            item.setStatus(history.getStatus());
            item.setAssignedTo(history.getAssignedTo()); // this may be null, when closing
        }
        item.setItemUsers(history.getItemUsers());
        // may have been set if this is an import
        if(history.getTimeStamp() == null) {
            history.setTimeStamp(new Date());
        }
        Attachment attachment = getAttachment(fileUpload);
        if(attachment != null) {
            item.add(attachment);
            history.setAttachment(attachment);
        }
        item.add(history);
        dao.storeItem(item);
        writeToFile(fileUpload, attachment, item.getSpace().getId());
        if(indexer != null) {
            if(attachment != null) {
                indexHistoryAttachmentAsync(history, item.getSpace().getId(), attachment);
            } else {
                indexer.index(history);
            }
        }
        if (history.isSendNotifications()) {
            mailSender.send(item);
        }
    }

    public Item loadItem(long id) {
        return dao.loadItem(id);
    }

    public Item loadItemByRefId(String refId) {
        ItemRefId itemRefId = new ItemRefId(refId); // throws runtime exception if invalid id
        List<Item> items = dao.findItems(itemRefId.getSequenceNum(), itemRefId.getPrefixCode());
        if (items.size() == 0) {
            return null;
        }
        return items.get(0);
    }

    public List<Item> findItemsBySmartRefId(String input, Space preferredSpace) {
        if (input == null) {
            return Collections.emptyList();
        }
        String trimmed = input.trim();
        int sepPos = trimmed.indexOf('-');
        if (sepPos <= 0) {
            sepPos = trimmed.indexOf('#');
        }
        if (sepPos <= 0 || sepPos == trimmed.length() - 1) {
            return Collections.emptyList();
        }
        String prefixPart = trimmed.substring(0, sepPos).trim();
        String seqPart = trimmed.substring(sepPos + 1).trim();
        if (prefixPart.isEmpty() || seqPart.isEmpty()) {
            return Collections.emptyList();
        }
        long seqNum;
        try {
            seqNum = Long.parseLong(seqPart);
        } catch (NumberFormatException e) {
            return Collections.emptyList();
        }
        if (seqNum < 0) {
            return Collections.emptyList();
        }

        String upperPrefix = prefixPart.toUpperCase();
        List<Item> results = new ArrayList<Item>();
        Set<Long> seenItemIds = new HashSet<Long>();

        // 1. If preferredSpace is specified, check if its prefix matches
        if (preferredSpace != null && preferredSpace.getPrefixCode() != null) {
            String spacePrefix = preferredSpace.getPrefixCode().toUpperCase();
            if (spacePrefix.equals(upperPrefix) || spacePrefix.startsWith(upperPrefix)) {
                try {
                    Item item = loadItemByRefId(preferredSpace.getPrefixCode() + "-" + seqNum);
                    if (item != null) {
                        results.add(item);
                        seenItemIds.add(item.getId());
                        return results;
                    }
                } catch (Exception ignored) {
                }
            }
        }

        // 2. Try exact match on prefix across all spaces
        List<Space> allSpaces = findAllSpaces();
        for (Space s : allSpaces) {
            if (s.getPrefixCode() != null && s.getPrefixCode().equalsIgnoreCase(upperPrefix)) {
                try {
                    Item item = loadItemByRefId(s.getPrefixCode() + "-" + seqNum);
                    if (item != null && !seenItemIds.contains(item.getId())) {
                        results.add(item);
                        seenItemIds.add(item.getId());
                    }
                } catch (Exception ignored) {
                }
            }
        }
        if (!results.isEmpty()) {
            return results;
        }

        // 3. Smart prefix matching (e.g. NET matches NETWORK)
        for (Space s : allSpaces) {
            if (s.getPrefixCode() != null && s.getPrefixCode().toUpperCase().startsWith(upperPrefix)) {
                try {
                    Item item = loadItemByRefId(s.getPrefixCode() + "-" + seqNum);
                    if (item != null && !seenItemIds.contains(item.getId())) {
                        results.add(item);
                        seenItemIds.add(item.getId());
                    }
                } catch (Exception ignored) {
                }
            }
        }

        return results;
    }

    public History loadHistory(long id) {
        return dao.loadHistory(id);
    }

    public List<Item> findItems(ItemSearch itemSearch) {
        String searchText = itemSearch.getSearchText();
        if (searchText != null) {
            SearchResultHits hits = indexSearcher.findHitsContainingText(searchText);
            List<Long> itemIds = new ArrayList<Long>(hits.getItemIds());
            List<Long> historyIds = new ArrayList<Long>(hits.getHistoryIds());

            // If an item document matched (e.g. summary or detail contains keyword), ensure its initial history is included
            if (!hits.getItemLevelHitItemIds().isEmpty()) {
                List<Long> firstHistoryIds = dao.findFirstHistoryIdsForItems(hits.getItemLevelHitItemIds());
                for (Long fId : firstHistoryIds) {
                    if (!historyIds.contains(fId)) {
                        historyIds.add(fId);
                    }
                }
            }

            // Smart refId matching (e.g. searching '339' or 'DEMO-339')
            List<Item> smartItems = findItemsBySmartRefId(searchText, itemSearch.getSpace());
            for (Item si : smartItems) {
                if (!itemIds.contains(si.getId())) {
                    itemIds.add(si.getId());
                }
                for (History h : si.getHistory()) {
                    if (!historyIds.contains(h.getId())) {
                        historyIds.add(h.getId());
                    }
                }
            }

            if (itemIds.isEmpty() && historyIds.isEmpty()) {
                itemSearch.setResultCount(0);
                return Collections.<Item>emptyList();
            }
            itemSearch.setItemIds(itemIds);
            itemSearch.setHistoryIds(historyIds);
        }
        return dao.findItems(itemSearch);
    }

    public int loadCountOfAllItems() {
        return dao.loadCountOfAllItems();
    }

    public List<Item> findAllItems(int firstResult, int batchSize) {
        return dao.findAllItems(firstResult, batchSize);
    }

    public void removeItem(Item item) {
        if(item.getRelatingItems() != null) {
            for(ItemItem itemItem : item.getRelatingItems()) {
                removeItemItem(itemItem);
            }
        }
        if(item.getRelatedItems() != null) {
            for(ItemItem itemItem : item.getRelatedItems()) {
                removeItemItem(itemItem);
            }
        }
        dao.removeItem(item);
    }

    public void removeItemItem(ItemItem itemItem) {
        dao.removeItemItem(itemItem);
    }

    public int loadCountOfRecordsHavingFieldNotNull(Space space, Field field) {
        return dao.loadCountOfRecordsHavingFieldNotNull(space, field);
    }

    public int bulkUpdateFieldToNull(Space space, Field field) {
        return dao.bulkUpdateFieldToNull(space, field);
    }

    public int loadCountOfRecordsHavingFieldWithValue(Space space, Field field, int optionKey) {
        return dao.loadCountOfRecordsHavingFieldWithValue(space, field, optionKey);
    }

    public int bulkUpdateFieldToNullForValue(Space space, Field field, int optionKey) {
        return dao.bulkUpdateFieldToNullForValue(space, field, optionKey);
    }

    public int loadCountOfRecordsHavingStatus(Space space, int status) {
        return dao.loadCountOfRecordsHavingStatus(space, status);
    }

    public int bulkUpdateStatusToOpen(Space space, int status) {
        return dao.bulkUpdateStatusToOpen(space, status);
    }

    public int bulkUpdateRenameSpaceRole(Space space, String oldRoleKey, String newRoleKey) {
        return dao.bulkUpdateRenameSpaceRole(space, oldRoleKey, newRoleKey);
    }

    public int bulkUpdateDeleteSpaceRole(Space space, String roleKey) {
        return dao.bulkUpdateDeleteSpaceRole(space, roleKey);
    }

    // =========  Acegi UserDetailsService implementation ==========
    public UserDetails loadUserByUsername(String loginName) {
        List<User> users = null;
        if (loginName.indexOf("@") != -1) {
            users = dao.findUsersByEmail(loginName);
        } else {
            users = dao.findUsersByLoginName(loginName);
        }
        if (users.size() == 0) {
            throw new UsernameNotFoundException("User not found for '" + loginName + "'");
        }
        logger.debug("loadUserByUserName success for '" + loginName + "'");
        User user = users.get(0);
        // if some spaces have guest access enabled, allocate these spaces as well
        Set<Space> userSpaces = user.getSpaces();
        logger.debug("user spaces: " + userSpaces);
        for(Space s : findSpacesWhereGuestAllowed()) {
            if(!userSpaces.contains(s)) {
                user.addSpaceWithRole(s, Role.ROLE_GUEST);

            }
        }
        for(UserSpaceRole usr : user.getSpaceRoles()) {
            logger.debug("UserSpaceRole: " + usr);
            // this is a hack, the effect of the next line would be to
            // override hibernate lazy loading and get the space and associated metadata.
            // since this only happens only once on authentication and simplifies a lot of
            // code later because the security principal is "fully prepared",
            // this is hopefully pardonable.  The downside is that there may be as many extra db hits
            // as there are spaces allocated for the user.  Hibernate caching should alleviate this
            usr.isAbleToCreateNewItem();
        }
        return user;
    }

    public User loadUser(long id) {
        return dao.loadUser(id);
    }

    public User loadUser(String loginName) {
        List<User> users = dao.findUsersByLoginName(loginName);
        if (users.size() == 0) {
            return null;
        }
        return users.get(0);
    }

    public User findUserByEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return null;
        }
        List<User> users = dao.findUsersByEmail(email.trim());
        if (users == null || users.isEmpty()) {
            return null;
        }
        User user = users.get(0);
        for (Space s : findSpacesWhereGuestAllowed()) {
            if (!user.getSpaces().contains(s)) {
                user.addSpaceWithRole(s, Role.ROLE_GUEST);
            }
        }
        return user;
    }

    public void storeUser(User user) {
        user.clearNonPersistentRoles();
        dao.storeUser(user);
    }

    public void storeUser(User user, String password, boolean sendNotifications) {
        if (password == null) {
            password = generatePassword();
        }
        user.setPassword(encodeClearText(password));
        storeUser(user);
        if(sendNotifications) {
            mailSender.sendUserPassword(user, password);
        }
    }

    public void removeUser(User user) {
        for(ItemUser iu : dao.findItemUsersByUser(user)) {
            dao.removeItemUser(iu);
        }
        dao.removeUser(user);
    }

    public List<User> findAllUsers() {
        return dao.findAllUsers();
    }

    public List<User> findUsersWhereIdIn(List<Long> ids) {
        return dao.findUsersWhereIdIn(ids);
    }

    public List<User> findUsersMatching(String searchText, String searchOn) {
        return dao.findUsersMatching(searchText, searchOn);
    }

    public List<User> findUsersForSpace(long spaceId) {
        return dao.findUsersForSpace(spaceId);
    }

    public List<UserSpaceRole> findUserRolesForSpace(long spaceId) {
        return dao.findUserRolesForSpace(spaceId);
    }

    public Map<Long, List<UserSpaceRole>> loadUserRolesMapForSpace(long spaceId) {
        List<UserSpaceRole> list = dao.findUserRolesForSpace(spaceId);
        Map<Long, List<UserSpaceRole>> map = new LinkedHashMap<Long, List<UserSpaceRole>>();
        for(UserSpaceRole usr : list) {
            long userId = usr.getUser().getId();
            List<UserSpaceRole> value = map.get(userId);
            if(value == null) {
                value = new ArrayList<UserSpaceRole>();
                map.put(userId, value);
            }
            value.add(usr);
        }
        return map;
    }

    public Map<Long, List<UserSpaceRole>> loadSpaceRolesMapForUser(long userId) {
        List<UserSpaceRole> list = dao.findSpaceRolesForUser(userId);
        Map<Long, List<UserSpaceRole>> map = new LinkedHashMap<Long, List<UserSpaceRole>>();
        for(UserSpaceRole usr : list) {
            long spaceId = usr.getSpace() == null ? 0 : usr.getSpace().getId();
            List<UserSpaceRole> value = map.get(spaceId);
            if(value == null) {
                value = new ArrayList<UserSpaceRole>();
                map.put(spaceId, value);
            }
            value.add(usr);
        }
        return map;
    }

    public List<User> findUsersWithRoleForSpace(long spaceId, String roleKey) {
        return dao.findUsersWithRoleForSpace(spaceId, roleKey);
    }

    public List<User> findUsersForUser(User user) {
        Set<Space> spaces = user.getSpaces();
        if(spaces.size() == 0) {
            // this will happen when a user has no spaces allocated
            return Collections.emptyList();
        }
        // must be a better way to make this unique?
        List<User> users = dao.findUsersForSpaceSet(spaces);
        Set<User> userSet = new LinkedHashSet<User>(users);
        return new ArrayList<User>(userSet);
    }

    public List<User> findUsersNotFullyAllocatedToSpace(long spaceId) {
        // trying to reduce database hits and lazy loading as far as possible
        List<User> notAtAllAllocated = dao.findUsersNotAllocatedToSpace(spaceId);
        List<UserSpaceRole> usrs = dao.findUserRolesForSpace(spaceId);
        List<User> notFullyAllocated = new ArrayList<User>(notAtAllAllocated);
        if(usrs.size() == 0) {
            return notFullyAllocated;
        }
        Space space = usrs.get(0).getSpace();
        Set<UserSpaceRole> allocated = new HashSet(usrs);
        Set<String> roleKeys = new HashSet(space.getMetadata().getAllRoleKeys());
        Set<User> processed = new HashSet<User>(usrs.size());
        Set<User> superUsers = new HashSet(dao.findSuperUsers());
        for(UserSpaceRole usr : usrs) {
            User user = usr.getUser();
            if(processed.contains(user)) {
                continue;
            }
            processed.add(user);
            // not using the user object as it is db intensive
            boolean isSuperUser = superUsers.contains(user);
            for(String roleKey : roleKeys) {
                if(isSuperUser && Role.isAdmin(roleKey)) {
                    continue;
                }
                UserSpaceRole temp = new UserSpaceRole(user, space, roleKey);
                if(!allocated.contains(temp)) {
                    notFullyAllocated.add(user);
                    break;
                }
            }
        }
        Collections.sort(notFullyAllocated);
        return notFullyAllocated;
    }

    public int loadCountOfHistoryInvolvingUser(User user) {
        return dao.loadCountOfHistoryInvolvingUser(user);
    }

    //==========================================================================

    public CountsHolder loadCountsForUser(User user) {
        return dao.loadCountsForUser(user);
    }

    public Counts loadCountsForUserSpace(User user, Space space) {
        return dao.loadCountsForUserSpace(user, space);
    }

    //==========================================================================

    public void storeUserSpaceRole(User user, Space space, String roleKey) {
        user.addSpaceWithRole(space, roleKey);
        storeUser(user);
    }

    public void removeUserSpaceRole(UserSpaceRole userSpaceRole) {
        User user = userSpaceRole.getUser();
        user.removeSpaceWithRole(userSpaceRole.getSpace(), userSpaceRole.getRoleKey());
        // dao.storeUser(user);
        dao.removeUserSpaceRole(userSpaceRole);
    }

    public UserSpaceRole loadUserSpaceRole(long id) {
        return dao.loadUserSpaceRole(id);
    }

    //==========================================================================

    public Space loadSpace(long id) {
        return dao.loadSpace(id);
    }

    public Space loadSpace(String prefixCode) {
        List<Space> spaces = dao.findSpacesByPrefixCode(prefixCode);
        if (spaces.size() == 0) {
            return null;
        }
        return spaces.get(0);
    }

    public void storeSpace(Space space) {
        boolean newSpace = space.getId() == 0;
        dao.storeSpace(space);
        if(newSpace) {
            SpaceSequence ss = new SpaceSequence();
            ss.setNextSeqNum(1);
            ss.setId(space.getId());
            dao.storeSpaceSequence(ss);
        }
    }

    public List<Space> findAllSpaces() {
        return dao.findAllSpaces();
    }

    public List<Space> findSpacesWhereIdIn(List<Long> ids) {
        return dao.findSpacesWhereIdIn(ids);
    }

    public List<Space> findSpacesWhereGuestAllowed() {
        return dao.findSpacesWhereGuestAllowed();
    }

    public List<Space> findSpacesNotFullyAllocatedToUser(long userId) {
        // trying to reduce database hits and lazy loading as far as possible
        List<Space> notAtAllAllocated = dao.findSpacesNotAllocatedToUser(userId);
        List<UserSpaceRole> usrs = dao.findSpaceRolesForUser(userId);
        List<Space> notFullyAllocated = new ArrayList(notAtAllAllocated);
        if(usrs.size() == 0) {
            return notFullyAllocated;
        }
        Set<UserSpaceRole> allocated = new HashSet(usrs);
        Set<Space> processed = new HashSet<Space>(usrs.size());
        User user = usrs.get(0).getUser();
        boolean isSuperUser = user.isSuperUser();
        for(UserSpaceRole usr : usrs) {
            Space space = usr.getSpace();
            if(space == null || processed.contains(space)) {
                continue;
            }
            processed.add(space);
            for(String roleKey : space.getMetadata().getAllRoleKeys()) {
                if(isSuperUser && Role.isAdmin(roleKey)) {
                    continue;
                }
                UserSpaceRole temp = new UserSpaceRole(user, space, roleKey);
                if(!allocated.contains(temp)) {
                    notFullyAllocated.add(space);
                    break;
                }
            }
        }
        Collections.sort(notFullyAllocated);
        return notFullyAllocated;
    }

    public void removeSpace(Space space) {
        logger.info("proceeding to delete space: " + space);
        dao.bulkUpdateDeleteSpaceRole(space, null);
        dao.bulkUpdateDeleteItemsForSpace(space);
        dao.removeSpace(space);
        logger.info("successfully deleted space");
    }

    //==========================================================================

    public void storeMetadata(Metadata metadata) {
        dao.storeMetadata(metadata);
    }

    public Metadata loadMetadata(long id) {
        return dao.loadMetadata(id);
    }

    //==========================================================================

    public Map<String, String> loadAllConfig() {
        List<Config> list = dao.findAllConfig();
        Map<String, String> allConfig = new HashMap<String, String>(list.size());
        for (Config c : list) {
            allConfig.put(c.getParam(), c.getValue());
        }
        return allConfig;
    }

    // TODO must be some nice generic way to do this
    public void storeConfig(Config config) {
        dao.storeConfig(config);
        if(config.isMailConfig()) {
            initMailSender(loadAllConfig());
        } else if(config.isLocaleConfig()) {
            initDefaultLocale(config.getValue());
        } else if(config.isAttachmentConfig()) {
            initAttachmentMaxSize(config.getValue());
        } else if(config.isSessionTimeoutConfig()) {
            initSessionTimeout(config.getValue());
        }
    }

    public String loadConfig(String param) {
        Config config = dao.loadConfig(param);
        if (config == null) {
            return null;
        }
        String value = config.getValue();
        if (value == null || value.trim().equals("")) {
            return null;
        }
        return value;
    }

    public String loadConfig(String param, String defaultValue) {
		String val = loadConfig(param);
        if (val == null) {
            return defaultValue;
        } else {
			return val;
		}
    }

    //========================================================

    private volatile BatchInfo indexRebuildStatus;
    private final Object indexRebuildLock = new Object();

    public BatchInfo getIndexRebuildStatus() {
        return indexRebuildStatus;
    }

    public void startRebuildIndexes() {
        synchronized (indexRebuildLock) {
            if (indexRebuildStatus != null && !indexRebuildStatus.isComplete()) {
                logger.warn("rebuildIndexes is already in progress");
                return;
            }
            final BatchInfo batchInfo = new BatchInfo();
            indexRebuildStatus = batchInfo;
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        rebuildIndexes(batchInfo);
                    } catch (Exception e) {
                        logger.error("indexing error", e);
                        batchInfo.setErrorMessage(e.getMessage());
                    } finally {
                        batchInfo.setComplete(true);
                    }
                }
            }, "JTrac-IndexRebuild");
            thread.setDaemon(true);
            thread.start();
        }
    }

    public void rebuildIndexes(final BatchInfo batchInfo) {
        File file = new File(jtracHome + "/indexes");
        if (file.exists() && file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File f : files) {
                    logger.debug("deleting file: " + f);
                    f.delete();
                }
            }
        }
        logger.info("existing index files deleted successfully");

        final TransactionTemplate txTemplate = transactionManager != null
                ? new TransactionTemplate(transactionManager)
                : null;
        if (txTemplate != null) {
            txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        }

        int totalSize = 0;
        if (txTemplate != null) {
            totalSize = txTemplate.execute(status -> dao.loadCountOfAllItems());
        } else {
            totalSize = dao.loadCountOfAllItems();
        }
        batchInfo.setTotalSize(totalSize);
        logger.info("total items to index: " + totalSize);
        int firstResult = 0;
        while (true) {
            logger.info("processing batch starting from: " + firstResult + ", current: " + batchInfo.getCurrentPosition());
            final int currentFirst = firstResult;
            final int batchSize = batchInfo.getBatchSize();
            final List<Item> items;
            if (txTemplate != null) {
                items = txTemplate.execute(status -> indexBatch(currentFirst, batchSize, batchInfo));
            } else {
                items = indexBatch(currentFirst, batchSize, batchInfo);
            }
            if (logger.isDebugEnabled()) {
                logger.debug("size of current batch: " + (items != null ? items.size() : 0));
            }
            firstResult += batchInfo.getBatchSize();
            if (logger.isDebugEnabled()) {
                logger.debug("setting firstResult to: " + firstResult);
            }
            if (items == null || items.isEmpty() || batchInfo.getCurrentPosition() >= batchInfo.getTotalSize()) {
                logger.info("batch completed at position: " + batchInfo.getCurrentPosition());
                break;
            }
        }
        batchInfo.setComplete(true);
        logger.info("indexing completed successfully, total indexed: " + batchInfo.getCurrentPosition());
        try {
            storeConfig(new Config("lucene.analyzer.version", "3.0.0-subtoken-v1"));
            logger.info("updated lucene.analyzer.version to 3.0.0-subtoken-v1 upon successful index rebuild");
        } catch (Exception e) {
            logger.warn("failed to update lucene.analyzer.version config after rebuild: " + e.getMessage(), e);
        }
    }

    private List<Item> indexBatch(int currentFirst, int batchSize, BatchInfo batchInfo) {
        List<Item> items = dao.findAllItems(currentFirst, batchSize);
        int maxSizeMb = getAttachmentIndexMaxSizeInMb();
        int maxChars = getAttachmentIndexMaxChars();
        for (Item item : items) {
            indexer.index(item);

            int historyCount = 0;
            for (History history : item.getHistory()) {
                if (history.getAttachment() != null) {
                    try {
                        File attFile = AttachmentUtils.getFile(history.getAttachment(), item.getSpace().getId(), jtracHome);
                        if (attFile != null && attFile.exists()) {
                            String text = AttachmentTextExtractor.extractText(attFile, maxSizeMb, maxChars);
                            history.setAttachmentText(text);
                        }
                    } catch (Exception e) {
                        logger.warn("Could not extract attachment text for history id: " + history.getId(), e);
                    }
                }
                indexer.index(history);
                historyCount++;
            }
            if (logger.isDebugEnabled()) {
                logger.debug("indexed item: " + item.getId()
                        + " : " + item.getRefId() + ", history: " + historyCount);
            }
            batchInfo.incrementPosition();
        }
        return items;
    }

    public boolean validateTextSearchQuery(String text) {
        return indexSearcher.validateQuery(text);
    }

    //==========================================================================

    public void executeHourlyTask() {
        logger.debug("hourly task called");
        cleanExpiredAiReports();
    }

    private void cleanExpiredAiReports() {
        if (jtracHome == null) {
            return;
        }
        try {
            File reportsDir = new File(jtracHome, "reports");
            if (!reportsDir.exists() || !reportsDir.isDirectory()) {
                return;
            }
            long cutoff = System.currentTimeMillis() - (14L * 24 * 60 * 60 * 1000); // 14 days
            File[] files = reportsDir.listFiles();
            if (files != null) {
                int cleanedCount = 0;
                for (File f : files) {
                    if (f.isFile() && f.getName().endsWith(".html") && f.lastModified() < cutoff) {
                        if (f.delete()) {
                            cleanedCount++;
                        }
                    }
                }
                if (cleanedCount > 0) {
                    logger.info("Hourly task: Cleaned up " + cleanedCount + " expired AI report file(s) (>14 days old)");
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to clean up expired AI reports in hourly task: " + e.getMessage());
        }
    }

    private InboundMailReceiver inboundMailReceiver;

    public void setInboundMailReceiver(InboundMailReceiver inboundMailReceiver) {
        this.inboundMailReceiver = inboundMailReceiver;
    }

    public InboundMailReceiver getInboundMailReceiver() {
        if (inboundMailReceiver == null) {
            inboundMailReceiver = new InboundMailReceiver(getJtracProxy(), mailSender);
        }
        return inboundMailReceiver;
    }

    /* configured to be called every five minutes */
    public void executePollingTask() {
        logger.debug("polling task called");
        try {
            String inboundEnabled = loadConfig("mail.inbound.enabled");
            if ("true".equalsIgnoreCase(inboundEnabled)) {
                logger.info("Executing inbound mail AI query polling task...");
                int processed = getInboundMailReceiver().receiveAndProcess();
                if (processed > 0) {
                    logger.info("Inbound mail polling task processed {} messages", processed);
                }
            }
        } catch (Throwable t) {
            logger.error("Error executing inbound mail polling task: " + t.getMessage(), t);
        }
    }

    //==========================================================================

    public String getReleaseVersion() {
        return releaseVersion;
    }

    public String getReleaseTimestamp() {
        return releaseTimestamp;
    }

    //==========================================================================
	//
    @Override
    public List<StoredSearch>  loadAllStoredSearch() {

        return dao.findAllStoredSearch();
    }

    @Override
    public void storeStoredSearch(StoredSearch storedSearch) {
        dao.storeStoredSearch(storedSearch);
    }

    @Override
    public void removeStoredSearch(Long id) {
        StoredSearch storedSearchToDel = dao.loadStoredSearch(id);
        dao.removeStoredSearch(storedSearchToDel);
    }

    //==========================================================================

    @Override
    public BackupExportService getBackupExportService() {
        if (backupExportService == null) {
            backupExportService = new BackupExportService(dao, releaseVersion, jtracHome, dataSource);
        } else if (backupExportService.getDataSource() == null) {
            backupExportService.setDataSource(dataSource);
        }
        return backupExportService;
    }

    @Override
    public ZipBundleService getZipBundleService() {
        if (zipBundleService == null) {
            zipBundleService = new ZipBundleService();
        }
        return zipBundleService;
    }

    @Override
    public BackupRestoreService getBackupRestoreService() {
        if (backupRestoreService == null) {
            backupRestoreService = new BackupRestoreService(dataSource, dao, getJtracProxy(), getBackupExportService(), getZipBundleService(), jtracHome);
        } else {
            backupRestoreService.setJtrac(getJtracProxy());
        }
        return backupRestoreService;
    }

    @Override
    public SystemBackupData exportSystemData() {
        return getBackupExportService().exportSystemData();
    }

    @Override
    public void exportBackupZip(OutputStream out, String operatorLoginName) throws Exception {
        SystemBackupData data = exportSystemData();
        BackupManifest manifest = getBackupExportService().createManifest(data, operatorLoginName);
        String sqlDump = getBackupExportService().generateSqlDump(data);
        getZipBundleService().createBackupZip(manifest, data, sqlDump, jtracHome, out);
    }

    @Override
    public void performFullRestore(InputStream zipIn, User currentOperator) throws Exception {
        getBackupRestoreService().performFullRestore(zipIn, currentOperator);
    }
}
