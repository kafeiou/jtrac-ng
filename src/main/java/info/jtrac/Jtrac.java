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

import info.jtrac.domain.BatchInfo;
import info.jtrac.domain.Config;
import info.jtrac.domain.Counts;
import info.jtrac.domain.CountsHolder;
import info.jtrac.domain.Field;
import info.jtrac.domain.History;
import info.jtrac.domain.Item;
import info.jtrac.domain.ItemItem;
import info.jtrac.domain.ItemSearch;
import info.jtrac.domain.Metadata;
import info.jtrac.domain.Space;
import info.jtrac.domain.StoredSearch;
import info.jtrac.domain.User;
import info.jtrac.domain.UserSpaceRole;

import java.util.List;
import java.util.Map;

import org.springframework.security.core.userdetails.UserDetailsService;
import org.apache.wicket.markup.html.form.upload.FileUpload;
import info.jtrac.backup.model.SystemBackupData;
import info.jtrac.backup.service.BackupExportService;
import info.jtrac.backup.service.BackupRestoreService;
import info.jtrac.backup.service.ZipBundleService;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Jtrac main business interface (Service Layer)
 */
public interface Jtrac extends UserDetailsService {

    // TODO remove Wicket dep with FileUpload
    void storeItem(Item item, FileUpload fileUpload);
    void storeItems(List<Item> items);
    void updateItem(Item item, User user);
    void storeHistoryForItem(long itemId, History history, FileUpload fileUpload);
    Item loadItem(long id);
    Item loadItemByRefId(String refId);
    List<Item> findItemsBySmartRefId(String input, Space preferredSpace);
    History loadHistory(long id);
    List<Item> findItems(ItemSearch itemSearch);
    int loadCountOfAllItems();
    List<Item> findAllItems(int firstResult, int batchSize);
    void removeItem(Item item);
    void removeItemItem(ItemItem itemItem);
    //========================================================
    int loadCountOfRecordsHavingFieldNotNull(Space space, Field field);
    int bulkUpdateFieldToNull(Space space, Field field);
    int loadCountOfRecordsHavingFieldWithValue(Space space, Field field, int optionKey);
    int bulkUpdateFieldToNullForValue(Space space, Field field, int optionKey);
    int loadCountOfRecordsHavingStatus(Space space, int status);
    int bulkUpdateStatusToOpen(Space space, int status);
    int bulkUpdateRenameSpaceRole(Space space, String oldRoleKey, String newRoleKey);
    int bulkUpdateDeleteSpaceRole(Space space, String roleKey);
    //========================================================
    void storeUser(User user);
    void storeUser(User user, String password, boolean sendNotifications);
    void removeUser(User user);
    User loadUser(long id);
    User loadUser(String loginName);
    User findUserByEmail(String email);
    List<User> findAllUsers();
    List<User> findUsersWhereIdIn(List<Long> ids);
    List<User> findUsersMatching(String searchText, String searchOn);
    List<User> findUsersForSpace(long spaceId);
    List<UserSpaceRole> findUserRolesForSpace(long spaceId);
    Map<Long, List<UserSpaceRole>> loadUserRolesMapForSpace(long spaceId);
    Map<Long, List<UserSpaceRole>> loadSpaceRolesMapForUser(long userId);
    List<User> findUsersWithRoleForSpace(long spaceId, String roleKey);
    List<User> findUsersForUser(User user);
    List<User> findUsersNotFullyAllocatedToSpace(long spaceId);
    int loadCountOfHistoryInvolvingUser(User user);
    //========================================================
    CountsHolder loadCountsForUser(User user);
    Counts loadCountsForUserSpace(User user, Space space);
    //========================================================
    void storeSpace(Space space);
    Space loadSpace(long id);
    Space loadSpace(String prefixCode);
    List<Space> findAllSpaces();
    List<Space> findSpacesWhereIdIn(List<Long> ids);
    List<Space> findSpacesWhereGuestAllowed();
    List<Space> findSpacesNotFullyAllocatedToUser(long userId);
    void removeSpace(Space space);
    //========================================================
    void storeUserSpaceRole(User user, Space space, String roleKey);
    UserSpaceRole loadUserSpaceRole(long id);
    void removeUserSpaceRole(UserSpaceRole userSpaceRole);
    //========================================================
    void storeMetadata(Metadata metadata);
    Metadata loadMetadata(long id);
    //========================================================
    String generatePassword();
    String encodeClearText(String clearText);
    Map<String, String> getLocales();
    String getDefaultLocale();
    String getJtracHome();
    int getAttachmentMaxSizeInMb();
    int getAttachmentIndexMaxSizeInMb();
    int getAttachmentIndexMaxChars();
    int getSessionTimeoutInMinutes();
    //========================================================
    Map<String, String> loadAllConfig();
    void storeConfig(Config config);
    String loadConfig(String param);
    String loadConfig(String param, String defaultValue);
    //========================================================
    void rebuildIndexes(BatchInfo batchInfo);
    BatchInfo getIndexRebuildStatus();
    void startRebuildIndexes();
    boolean validateTextSearchQuery(String text);
    //========================================================
    void executeHourlyTask();
    void executePollingTask();
    //========================================================
    String getReleaseVersion();
    String getReleaseTimestamp();
    //========================================================
    List<StoredSearch> loadAllStoredSearch();
    void storeStoredSearch(StoredSearch storedSearch);
    void removeStoredSearch(Long id);
    //========================================================
    BackupExportService getBackupExportService();
    ZipBundleService getZipBundleService();
    BackupRestoreService getBackupRestoreService();
    SystemBackupData exportSystemData();
    void exportBackupZip(OutputStream out, String operatorLoginName) throws Exception;
    void performFullRestore(InputStream zipIn, User currentOperator) throws Exception;
}
