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

package info.jtrac.hibernate;

import info.jtrac.JtracDao;
import info.jtrac.domain.Attachment;
import info.jtrac.domain.Config;
import info.jtrac.domain.Counts;
import info.jtrac.domain.CountsHolder;
import info.jtrac.domain.Field;
import info.jtrac.domain.History;
import info.jtrac.domain.Item;
import info.jtrac.domain.ItemItem;
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


import org.hibernate.CacheMode;
import org.hibernate.Criteria;
import org.hibernate.FetchMode;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.MatchMode;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DAO Implementation using native Hibernate 5 SessionFactory
 */
public class HibernateJtracDao implements JtracDao {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private SessionFactory sessionFactory;

    public void setSessionFactory(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    protected Session getCurrentSession() {
        return sessionFactory.getCurrentSession();
    }

    public void storeItem(Item item) {
        Item merged = (Item) getCurrentSession().merge(item);
        item.setId(merged.getId());
    }

    public Item loadItem(long id) {
        return getCurrentSession().get(Item.class, id);
    }

    public void storeHistory(History history) {
        History merged = (History) getCurrentSession().merge(history);
        history.setId(merged.getId());
    }

    public History loadHistory(long id) {
        return getCurrentSession().get(History.class, id);
    }

    public List<Item> findItems(long sequenceNum, String prefixCode) {
        return getCurrentSession().createQuery("from Item item where item.sequenceNum = :sequenceNum and item.space.prefixCode = :prefixCode", Item.class)
                .setParameter("sequenceNum", sequenceNum)
                .setParameter("prefixCode", prefixCode)
                .getResultList();
    }

    public List<Item> findItems(ItemSearch itemSearch) {
        int pageSize = itemSearch.getPageSize();
        Field.Name sortFieldName = Field.isValidName(itemSearch.getSortFieldName()) ? Field.convertToName(itemSearch.getSortFieldName()) : null;
        boolean doInMemorySort = sortFieldName != null && sortFieldName.isDropDownType() && itemSearch.getSpace() != null;
        if (pageSize == -1 || doInMemorySort) {
            Criteria criteria = itemSearch.getCriteria().getExecutableCriteria(getCurrentSession());
            @SuppressWarnings("unchecked")
            List<Item> list = criteria.list();
            if (!list.isEmpty() && doInMemorySort) {
                doInMemorySort(list, itemSearch);
            }
            itemSearch.setResultCount(list.size());
            if (pageSize != -1) {
                int start = pageSize * itemSearch.getCurrentPage();
                int end = Math.min(start + itemSearch.getPageSize(), list.size());
                return list.subList(start, end);
            }
            return list;
        } else {
            if (itemSearch.isBatchMode()) {
                getCurrentSession().clear();
            }
            int firstResult = pageSize * itemSearch.getCurrentPage();
            Criteria criteria = itemSearch.getCriteria().getExecutableCriteria(getCurrentSession());
            criteria.setFirstResult(firstResult);
            criteria.setMaxResults(pageSize);
            @SuppressWarnings("unchecked")
            List<Item> list = criteria.list();
            if (!itemSearch.isBatchMode()) {
                DetachedCriteria countCriteria = itemSearch.getCriteriaForCount();
                countCriteria.setProjection(Projections.rowCount());
                Criteria executableCountCriteria = countCriteria.getExecutableCriteria(getCurrentSession());
                Number count = (Number) executableCountCriteria.uniqueResult();
                itemSearch.setResultCount(count != null ? count.longValue() : 0);
            }
            return list;
        }
    }

    private void doInMemorySort(List<Item> list, ItemSearch itemSearch) {
        final Field field = itemSearch.getSpace().getMetadata().getField(itemSearch.getSortFieldName());
        final ArrayList<String> valueList = new ArrayList<String>(field.getOptions().keySet());
        Comparator<Item> comp = new Comparator<Item>() {
            public int compare(Item left, Item right) {
                Object leftVal = left.getValue(field.getName());
                String leftValString = leftVal == null ? null : leftVal.toString();
                int leftInd = valueList.indexOf(leftValString);
                Object rightVal = right.getValue(field.getName());
                String rightValString = rightVal == null ? null : rightVal.toString();
                int rightInd = valueList.indexOf(rightValString);
                return leftInd - rightInd;
            }
        };
        Collections.sort(list, itemSearch.isSortDescending() ? comp.reversed() : comp);
    }

    public int loadCountOfAllItems() {
        Number count = (Number) getCurrentSession().createQuery("select count(item) from Item item").uniqueResult();
        return count != null ? count.intValue() : 0;
    }

    @SuppressWarnings("unchecked")
    public List<Item> findAllItems(final int firstResult, final int batchSize) {
        Session session = getCurrentSession();
        session.clear();
        Criteria criteria = session.createCriteria(Item.class);
        criteria.setCacheMode(CacheMode.IGNORE);
        criteria.setResultTransformer(Criteria.DISTINCT_ROOT_ENTITY);
        criteria.setFetchMode("history", FetchMode.JOIN);
        criteria.add(Restrictions.ge("id", (long) firstResult));
        criteria.add(Restrictions.lt("id", (long) firstResult + batchSize));
        return criteria.list();
    }

    public void removeItem(Item item) {
        getCurrentSession().delete(item);
    }

    public void removeItemItem(ItemItem itemItem) {
        getCurrentSession().delete(itemItem);
    }

    public List<ItemUser> findItemUsersByUser(User user) {
        return getCurrentSession().createQuery("from ItemUser iu where iu.user = :user", ItemUser.class)
                .setParameter("user", user)
                .getResultList();
    }

    public void removeItemUser(ItemUser itemUser) {
        getCurrentSession().delete(itemUser);
    }

    public void storeAttachment(Attachment attachment) {
        Attachment merged = (Attachment) getCurrentSession().merge(attachment);
        attachment.setId(merged.getId());
    }

    public void storeMetadata(Metadata metadata) {
        Metadata merged = (Metadata) getCurrentSession().merge(metadata);
        metadata.setId(merged.getId());
    }

    public Metadata loadMetadata(long id) {
        return getCurrentSession().get(Metadata.class, id);
    }

    public void storeSpace(Space space) {
        Space merged = (Space) getCurrentSession().merge(space);
        space.setId(merged.getId());
    }

    public Space loadSpace(long id) {
        return getCurrentSession().get(Space.class, id);
    }

    public UserSpaceRole loadUserSpaceRole(long id) {
        return getCurrentSession().get(UserSpaceRole.class, id);
    }

    public long loadNextSequenceNum(final long spaceSequenceId) {
        Session session = getCurrentSession();
        session.flush();
        session.setCacheMode(CacheMode.IGNORE);
        SpaceSequence ss = session.get(SpaceSequence.class, spaceSequenceId);
        long next = ss.getAndIncrement();
        session.update(ss);
        session.flush();
        return next;
    }

    public void storeSpaceSequence(SpaceSequence spaceSequence) {
        getCurrentSession().saveOrUpdate(spaceSequence);
    }

    public List<Space> findSpacesByPrefixCode(String prefixCode) {
        return getCurrentSession().createQuery("from Space space where space.prefixCode = :prefixCode", Space.class)
                .setParameter("prefixCode", prefixCode)
                .getResultList();
    }

    public List<Space> findAllSpaces() {
        return getCurrentSession().createQuery("from Space space order by space.prefixCode", Space.class)
                .getResultList();
    }

    public List<Space> findSpacesNotAllocatedToUser(long userId) {
        return getCurrentSession().createQuery("from Space space where space not in"
                + " (select usr.space from UserSpaceRole usr where usr.user.id = :userId) order by space.name", Space.class)
                .setParameter("userId", userId)
                .getResultList();
    }

    public List<Space> findSpacesWhereIdIn(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return getCurrentSession().createQuery("from Space space where space.id in (:ids)", Space.class)
                .setParameterList("ids", ids)
                .getResultList();
    }

    public List<Space> findSpacesWhereGuestAllowed() {
        return getCurrentSession().createQuery("from Space space join fetch space.metadata where space.guestAllowed = true", Space.class)
                .getResultList();
    }

    public void removeSpace(Space space) {
        getCurrentSession().delete(space);
    }

    public void storeUser(User user) {
        User merged = (User) getCurrentSession().merge(user);
        user.setId(merged.getId());
    }

    public User loadUser(long id) {
        return getCurrentSession().get(User.class, id);
    }

    public void removeUser(User user) {
        getCurrentSession().createQuery("delete UserSpaceRole usr where usr.user.id = :userId")
                .setParameter("userId", user.getId())
                .executeUpdate();
        if (user.getUserSpaceRoles() != null) {
            user.getUserSpaceRoles().clear();
        }
        getCurrentSession().delete(user);
    }

    public List<User> findAllUsers() {
        return getCurrentSession().createQuery("from User user order by user.name", User.class)
                .getResultList();
    }

    public List<User> findUsersWhereIdIn(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return getCurrentSession().createQuery("from User user where user.id in (:ids)", User.class)
                .setParameterList("ids", ids)
                .getResultList();
    }

    @SuppressWarnings("unchecked")
    public List<User> findUsersMatching(final String searchText, final String searchOn) {
        Criteria criteria = getCurrentSession().createCriteria(User.class);
        criteria.add(Restrictions.ilike(searchOn, searchText, MatchMode.ANYWHERE));
        criteria.addOrder(Order.asc("name"));
        return criteria.list();
    }

    public List<User> findUsersByLoginName(String loginName) {
        return getCurrentSession().createQuery("from User user where user.loginName = :loginName", User.class)
                .setParameter("loginName", loginName)
                .getResultList();
    }

    public List<User> findUsersByEmail(String email) {
        return getCurrentSession().createQuery("from User user where user.email = :email", User.class)
                .setParameter("email", email)
                .getResultList();
    }

    public List<User> findUsersNotAllocatedToSpace(long spaceId) {
        return getCurrentSession().createQuery("from User user where user not in"
                + " (select usr.user from UserSpaceRole usr where usr.space.id = :spaceId) order by user.name", User.class)
                .setParameter("spaceId", spaceId)
                .getResultList();
    }

    public List<UserSpaceRole> findUserRolesForSpace(long spaceId) {
        return getCurrentSession().createQuery("select usr from UserSpaceRole usr join fetch usr.user"
                + " where usr.space.id = :spaceId order by usr.user.name", UserSpaceRole.class)
                .setParameter("spaceId", spaceId)
                .getResultList();
    }

    public List<User> findUsersWithRoleForSpace(long spaceId, String roleKey) {
        return getCurrentSession().createQuery("from User user"
                + " join user.userSpaceRoles as usr where usr.space.id = :spaceId"
                + " and usr.roleKey = :roleKey order by user.name", User.class)
                .setParameter("spaceId", spaceId)
                .setParameter("roleKey", roleKey)
                .getResultList();
    }

    public List<UserSpaceRole> findSpaceRolesForUser(long userId) {
        return getCurrentSession().createQuery("select usr from UserSpaceRole usr"
                + " left join fetch usr.space as space"
                + " left join fetch space.metadata"
                + " where usr.user.id = :userId order by usr.space.name", UserSpaceRole.class)
                .setParameter("userId", userId)
                .getResultList();
    }

    public List<User> findSuperUsers() {
        return getCurrentSession().createQuery("select usr.user from UserSpaceRole usr"
                + " where usr.space is null and usr.roleKey = :roleKey", User.class)
                .setParameter("roleKey", Role.ROLE_ADMIN)
                .getResultList();
    }

    public int loadCountOfHistoryInvolvingUser(User user) {
        Number count = (Number) getCurrentSession().createQuery("select count(history) from History history where "
                + " history.loggedBy = :user or history.assignedTo = :user")
                .setParameter("user", user)
                .uniqueResult();
        return count != null ? count.intValue() : 0;
    }

    public CountsHolder loadCountsForUser(User user) {
        Collection<Space> spaces = user.getSpaces();
        if (spaces == null || spaces.isEmpty()) {
            return null;
        }
        CountsHolder ch = new CountsHolder();
        Session session = getCurrentSession();
        List<Object[]> loggedByList = session.createQuery("select item.space.id, count(item) from Item item"
                + " where item.loggedBy.id = :userId group by item.space.id", Object[].class)
                .setParameter("userId", user.getId())
                .getResultList();
        List<Object[]> assignedToList = session.createQuery("select item.space.id, count(item) from Item item"
                + " where item.assignedTo.id = :userId group by item.space.id", Object[].class)
                .setParameter("userId", user.getId())
                .getResultList();
        List<Object[]> statusList = session.createQuery("select item.space.id, count(item) from Item item"
                + " where item.space in (:spaces) group by item.space.id", Object[].class)
                .setParameterList("spaces", spaces)
                .getResultList();
        for (Object[] oa : loggedByList) {
            ch.addLoggedByMe((Long) oa[0], (Long) oa[1]);
        }
        for (Object[] oa : assignedToList) {
            ch.addAssignedToMe((Long) oa[0], (Long) oa[1]);
        }
        for (Object[] oa : statusList) {
            ch.addTotal((Long) oa[0], (Long) oa[1]);
        }
        return ch;
    }

    public Counts loadCountsForUserSpace(User user, Space space) {
        Session session = getCurrentSession();
        List<Object[]> loggedByList = session.createQuery("select status, count(item) from Item item"
                + " where item.loggedBy.id = :userId and item.space.id = :spaceId group by item.status", Object[].class)
                .setParameter("userId", user.getId())
                .setParameter("spaceId", space.getId())
                .getResultList();
        List<Object[]> assignedToList = session.createQuery("select status, count(item) from Item item"
                + " where item.assignedTo.id = :userId and item.space.id = :spaceId group by item.status", Object[].class)
                .setParameter("userId", user.getId())
                .setParameter("spaceId", space.getId())
                .getResultList();
        List<Object[]> statusList = session.createQuery("select status, count(item) from Item item"
                + " where item.space.id = :spaceId group by item.status", Object[].class)
                .setParameter("spaceId", space.getId())
                .getResultList();
        Counts c = new Counts(true);
        for (Object[] oa : loggedByList) {
            c.addLoggedByMe((Integer) oa[0], (Long) oa[1]);
        }
        for (Object[] oa : assignedToList) {
            c.addAssignedToMe((Integer) oa[0], (Long) oa[1]);
        }
        for (Object[] oa : statusList) {
            c.addTotal((Integer) oa[0], (Long) oa[1]);
        }
        return c;
    }

    public List<User> findUsersForSpace(long spaceId) {
        return getCurrentSession().createQuery("select distinct u from User u join u.userSpaceRoles usr"
                + " where usr.space.id = :spaceId order by u.name", User.class)
                .setParameter("spaceId", spaceId)
                .getResultList();
    }

    public List<User> findUsersForSpaceSet(Collection<Space> spaces) {
        if (spaces == null || spaces.isEmpty()) {
            return Collections.emptyList();
        }
        return getCurrentSession().createQuery("select u from User u join u.userSpaceRoles usr"
                + " where usr.space in (:spaces) order by u.name", User.class)
                .setParameterList("spaces", spaces)
                .getResultList();
    }

    public void removeUserSpaceRole(UserSpaceRole userSpaceRole) {
        getCurrentSession().delete(userSpaceRole);
    }

    @Override
    public List<StoredSearch> findAllStoredSearch() {
        return getCurrentSession().createQuery("from StoredSearch order by name", StoredSearch.class).getResultList();
    }

    @Override
    public void storeStoredSearch(StoredSearch storedSearch) {
        StoredSearch merged = (StoredSearch) getCurrentSession().merge(storedSearch);
        storedSearch.setId(merged.getId());
    }

    @Override
    public StoredSearch loadStoredSearch(Long id) {
        return getCurrentSession().get(StoredSearch.class, id);
    }

    @Override
    public void removeStoredSearch(StoredSearch storedSearchToDel) {
        getCurrentSession().delete(storedSearchToDel);
    }

    public List<Config> findAllConfig() {
        Session session;
        boolean isNew = false;
        try {
            session = sessionFactory.getCurrentSession();
        } catch (Exception e) {
            session = sessionFactory.openSession();
            isNew = true;
        }
        try {
            return session.createQuery("from Config", Config.class).getResultList();
        } finally {
            if (isNew) {
                session.close();
            }
        }
    }

    public void storeConfig(Config config) {
        Session session;
        boolean isNew = false;
        Transaction tx = null;
        try {
            session = sessionFactory.getCurrentSession();
        } catch (org.hibernate.HibernateException e) {
            session = sessionFactory.openSession();
            tx = session.beginTransaction();
            isNew = true;
        }
        try {
            session.merge(config);
            if (tx != null) {
                tx.commit();
            }
        } catch (Exception e) {
            if (tx != null) {
                tx.rollback();
            }
            throw (e instanceof RuntimeException) ? (RuntimeException) e : new RuntimeException(e);
        } finally {
            if (isNew && session != null) {
                session.close();
            }
        }
    }

    public Config loadConfig(String param) {
        Session session;
        boolean isNew = false;
        try {
            session = sessionFactory.getCurrentSession();
        } catch (Exception e) {
            session = sessionFactory.openSession();
            isNew = true;
        }
        try {
            return session.get(Config.class, param);
        } finally {
            if (isNew) {
                session.close();
            }
        }
    }

    public int loadCountOfRecordsHavingFieldNotNull(Space space, Field field) {
        Criteria criteria = getCurrentSession().createCriteria(Item.class);
        criteria.add(Restrictions.eq("space", space));
        criteria.add(Restrictions.isNotNull(field.getName().toString()));
        criteria.setProjection(Projections.rowCount());
        int itemCount = ((Number) criteria.list().get(0)).intValue();

        criteria = getCurrentSession().createCriteria(History.class);
        criteria.createCriteria("parent").add(Restrictions.eq("space", space));
        criteria.add(Restrictions.isNotNull(field.getName().toString()));
        criteria.setProjection(Projections.rowCount());
        return itemCount + ((Number) criteria.list().get(0)).intValue();
    }

    public int bulkUpdateFieldToNull(Space space, Field field) {
        int itemCount = getCurrentSession().createQuery("update Item item set item." + field.getName() + " = null"
                + " where item.space.id = :spaceId")
                .setParameter("spaceId", space.getId())
                .executeUpdate();
        logger.info("no of Item rows where " + field.getName() + " set to null = " + itemCount);
        int historyCount = getCurrentSession().createQuery("update History history set history." + field.getName() + " = null"
                + " where history.parent in ( from Item item where item.space.id = :spaceId )")
                .setParameter("spaceId", space.getId())
                .executeUpdate();
        logger.info("no of History rows where " + field.getName() + " set to null = " + historyCount);
        return itemCount;
    }

    public int loadCountOfRecordsHavingFieldWithValue(Space space, Field field, int optionKey) {
        Criteria criteria = getCurrentSession().createCriteria(Item.class);
        criteria.add(Restrictions.eq("space", space));
        criteria.add(Restrictions.eq(field.getName().toString(), optionKey));
        criteria.setProjection(Projections.rowCount());
        int itemCount = ((Number) criteria.list().get(0)).intValue();

        criteria = getCurrentSession().createCriteria(History.class);
        criteria.createCriteria("parent").add(Restrictions.eq("space", space));
        criteria.add(Restrictions.eq(field.getName().toString(), optionKey));
        criteria.setProjection(Projections.rowCount());
        return itemCount + ((Number) criteria.list().get(0)).intValue();
    }

    public int bulkUpdateFieldToNullForValue(Space space, Field field, int optionKey) {
        int itemCount = getCurrentSession().createQuery("update Item item set item." + field.getName() + " = null"
                + " where item.space.id = :spaceId"
                + " and item." + field.getName() + " = :optionKey")
                .setParameter("spaceId", space.getId())
                .setParameter("optionKey", optionKey)
                .executeUpdate();
        logger.info("no of Item rows where " + field.getName() + " value '" + optionKey + "' replaced with null = " + itemCount);
        int historyCount = getCurrentSession().createQuery("update History history set history." + field.getName() + " = null"
                + " where history." + field.getName() + " = :optionKey"
                + " and history.parent in ( from Item item where item.space.id = :spaceId )")
                .setParameter("optionKey", optionKey)
                .setParameter("spaceId", space.getId())
                .executeUpdate();
        logger.info("no of History rows where " + field.getName() + " value '" + optionKey + "' replaced with null = " + historyCount);
        return itemCount;
    }

    public int loadCountOfRecordsHavingStatus(Space space, int status) {
        Criteria criteria = getCurrentSession().createCriteria(Item.class);
        criteria.add(Restrictions.eq("space", space));
        criteria.add(Restrictions.eq("status", status));
        criteria.setProjection(Projections.rowCount());
        int itemCount = ((Number) criteria.list().get(0)).intValue();

        criteria = getCurrentSession().createCriteria(History.class);
        criteria.createCriteria("parent").add(Restrictions.eq("space", space));
        criteria.add(Restrictions.eq("status", status));
        criteria.setProjection(Projections.rowCount());
        return itemCount + ((Number) criteria.list().get(0)).intValue();
    }

    public int bulkUpdateStatusToOpen(Space space, int status) {
        int itemCount = getCurrentSession().createQuery("update Item item set item.status = :openStatus"
                + " where item.status = :status and item.space.id = :spaceId")
                .setParameter("openStatus", State.OPEN)
                .setParameter("status", status)
                .setParameter("spaceId", space.getId())
                .executeUpdate();
        logger.info("no of Item rows where status changed from " + status + " to " + State.OPEN + " = " + itemCount);
        int historyCount = getCurrentSession().createQuery("update History history set history.status = :openStatus"
                + " where history.status = :status"
                + " and history.parent in ( from Item item where item.space.id = :spaceId )")
                .setParameter("openStatus", State.OPEN)
                .setParameter("status", status)
                .setParameter("spaceId", space.getId())
                .executeUpdate();
        logger.info("no of History rows where status changed from " + status + " to " + State.OPEN + " = " + historyCount);
        return itemCount;
    }

    public int bulkUpdateRenameSpaceRole(Space space, String oldRoleKey, String newRoleKey) {
        return getCurrentSession().createQuery("update UserSpaceRole usr set usr.roleKey = :newRoleKey"
                + " where usr.roleKey = :oldRoleKey and usr.space.id = :spaceId")
                .setParameter("newRoleKey", newRoleKey)
                .setParameter("oldRoleKey", oldRoleKey)
                .setParameter("spaceId", space.getId())
                .executeUpdate();
    }

    public int bulkUpdateDeleteSpaceRole(Space space, String roleKey) {
        if (roleKey == null) {
            return getCurrentSession().createQuery("delete UserSpaceRole usr where usr.space.id = :spaceId")
                    .setParameter("spaceId", space.getId())
                    .executeUpdate();
        } else {
            return getCurrentSession().createQuery("delete UserSpaceRole usr"
                    + " where usr.space.id = :spaceId and usr.roleKey = :roleKey")
                    .setParameter("spaceId", space.getId())
                    .setParameter("roleKey", roleKey)
                    .executeUpdate();
        }
    }

    public int bulkUpdateDeleteItemsForSpace(Space space) {
        int historyCount = getCurrentSession().createQuery("delete History history where history.parent in"
                + " ( from Item item where item.space.id = :spaceId )")
                .setParameter("spaceId", space.getId())
                .executeUpdate();
        logger.debug("deleted " + historyCount + " records from history");
        int itemItemCount = getCurrentSession().createQuery("delete ItemItem itemItem where itemItem.item in"
                + " ( from Item item where item.space.id = :spaceId )")
                .setParameter("spaceId", space.getId())
                .executeUpdate();
        logger.debug("deleted " + itemItemCount + " records from item_items");
        int itemCount = getCurrentSession().createQuery("delete Item item where item.space.id = :spaceId")
                .setParameter("spaceId", space.getId())
                .executeUpdate();
        logger.debug("deleted " + itemCount + " records from items");
        return historyCount + itemItemCount + itemCount;
    }

    /**
     * Automatically configured to run on startup as a spring bean "init-method"
     */
    public void createSchema() {
        Session session = sessionFactory.openSession();
        Transaction tx = session.beginTransaction();
        try {
            List<User> users = session.createQuery("from User user where user.loginName = :loginName", User.class)
                    .setParameter("loginName", "admin")
                    .getResultList();
            if (users.isEmpty()) {
                logger.info("expected database schema does not have admin user, inserting default admin user into database");
                User admin = new User();
                admin.setLoginName("admin");
                admin.setName("Admin");
                admin.setEmail("admin");
                admin.setPassword("21232f297a57a5a743894a0e4a801fc3");
                admin.addSpaceWithRole(null, Role.ROLE_ADMIN);
                session.save(admin);
                logger.info("schema creation complete");
            } else {
                logger.info("database schema exists with admin user, normal startup");
            }

            session.createQuery("update Space space set space.isActive = true where space.isActive is null").executeUpdate();
            session.createQuery("update StoredSearch search set search.newWindow = true where search.newWindow is null").executeUpdate();
            session.createQuery("update User user set user.prettyDates = true where user.prettyDates is null").executeUpdate();

            ensureDefaultConfig(session, "attachment.index.maxSizeMb", "10");
            ensureDefaultConfig(session, "attachment.index.maxChars", "50000");
            ensureDefaultConfig(session, "security.privacy.headers.enabled", "true");
            ensureDefaultConfig(session, "mail.inbound.enabled", "false");
            ensureDefaultConfig(session, "mail.inbound.server.port", "993");
            ensureDefaultConfig(session, "mail.inbound.ssl.enable", "false");
            ensureDefaultConfig(session, "mail.inbound.starttls.enable", "false");
            ensureDefaultConfig(session, "mail.inbound.ssl.trust.all", "false");
            ensureDefaultConfig(session, "llm.ollama.url", "http://localhost:11434");
            ensureDefaultConfig(session, "llm.ollama.model", "llama3.2");
            ensureDefaultConfig(session, "llm.ollama.timeout", "60");
            ensureDefaultConfig(session, "llm.retrieval.max_tickets", "50");
            ensureDefaultConfig(session, "lucene.analyzer.version", "3.0.0-subtoken-v1");

            List<SpaceSequence> ssList = session.createQuery("from SpaceSequence", SpaceSequence.class).getResultList();
            Map<Long, SpaceSequence> ssMap = new HashMap<Long, SpaceSequence>(ssList.size());
            for (SpaceSequence ss : ssList) {
                ssMap.put(ss.getId(), ss);
            }
            List<Object[]> list = session.createQuery("select item.space.id, max(item.sequenceNum) from Item item group by item.space.id", Object[].class).getResultList();
            for (Object[] oa : list) {
                Long spaceId = (Long) oa[0];
                Long maxSeqNum = (Long) oa[1];
                SpaceSequence ss = ssMap.get(spaceId);
                if (ss != null) {
                    logger.info(String.format("checking space sequence id %s, max: %d, next: %d", spaceId, maxSeqNum, ss.getNextSeqNum()));
                    if (ss.getNextSeqNum() <= maxSeqNum) {
                        logger.warn(String.format("fixing sequence number for space id %s was: %d, should be: %d", spaceId, ss.getNextSeqNum(), (maxSeqNum + 1)));
                        ss.setNextSeqNum(maxSeqNum + 1);
                        session.update(ss);
                    }
                }
            }
            tx.commit();
        } catch (Exception e) {
            if (tx != null) {
                tx.rollback();
            }
            logger.error("Failed to initialize schema data: " + e.getMessage(), e);
        } finally {
            session.close();
        }
    }

    private void ensureDefaultConfig(Session session, String param, String defaultValue) {
        Config cfg = session.get(Config.class, param);
        if (cfg == null) {
            session.save(new Config(param, defaultValue));
        }
    }

    @Override
    public List<Metadata> findAllMetadata() {
        return getCurrentSession().createQuery("from Metadata metadata order by metadata.id", Metadata.class).getResultList();
    }

    @Override
    public List<SpaceSequence> findAllSpaceSequences() {
        return getCurrentSession().createQuery("from SpaceSequence ss order by ss.id", SpaceSequence.class).getResultList();
    }

    @Override
    public List<UserSpaceRole> findAllUserSpaceRoles() {
        return getCurrentSession().createQuery("from UserSpaceRole usr order by usr.id", UserSpaceRole.class).getResultList();
    }

    @Override
    public List<info.jtrac.domain.Tag> findAllTags() {
        return getCurrentSession().createQuery("from Tag tag order by tag.id", info.jtrac.domain.Tag.class).getResultList();
    }

    @Override
    public List<Item> findAllItems() {
        return getCurrentSession().createQuery("from Item item order by item.id", Item.class).getResultList();
    }

    @Override
    public List<ItemItem> findAllItemItems() {
        return getCurrentSession().createQuery("from ItemItem ii order by ii.id", ItemItem.class).getResultList();
    }

    @Override
    public List<ItemUser> findAllItemUsers() {
        return getCurrentSession().createQuery("from ItemUser iu order by iu.id", ItemUser.class).getResultList();
    }

    @Override
    public List<info.jtrac.domain.ItemTag> findAllItemTags() {
        return getCurrentSession().createQuery("from ItemTag it order by it.id", info.jtrac.domain.ItemTag.class).getResultList();
    }

    @Override
    public List<Attachment> findAllAttachments() {
        return getCurrentSession().createQuery("from Attachment att order by att.id", Attachment.class).getResultList();
    }

    @Override
    public List<History> findAllHistories() {
        return getCurrentSession().createQuery("from History h order by h.id", History.class).getResultList();
    }

    @Override
    public Map<Long, Long> findAttachmentFilePrefixToSpaceIdMap() {
        Session session;
        boolean closeSession = false;
        try {
            session = sessionFactory.getCurrentSession();
        } catch (org.hibernate.HibernateException e) {
            session = sessionFactory.openSession();
            closeSession = true;
        }
        try {
            List<Object[]> list = session.createQuery(
                    "select h.attachment.filePrefix, h.parent.space.id from History h where h.attachment is not null",
                    Object[].class).getResultList();
            Map<Long, Long> map = new HashMap<Long, Long>(list.size());
            for (Object[] row : list) {
                Long filePrefix = (Long) row[0];
                Long spaceId = (Long) row[1];
                if (filePrefix != null && spaceId != null) {
                    map.put(filePrefix, spaceId);
                }
            }
            return map;
        } finally {
            if (closeSession && session != null) {
                session.close();
            }
        }
    }

    @Override
    public void clearSession() {
        try {
            getCurrentSession().clear();
        } catch (org.hibernate.HibernateException e) {
            // No current session bound to thread, safe to ignore
        }
    }
}

