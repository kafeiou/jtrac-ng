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

package info.jtrac.domain;


import info.jtrac.Jtrac;
import static info.jtrac.domain.ColumnHeading.Name.*;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Restrictions;

/**
 * Object that holds filter criteria when searching for Items
 * and also creates a Hibernate Criteria query to pass to the DAO
 */
public class ItemSearch implements Serializable {

    private Space space; // if null, means aggregate across all spaces
    private User user; // this will be set in the case space is null

    private int pageSize = 25;
    private int currentPage;
    private long resultCount;
    private String sortFieldName = "id";
    private boolean sortDescending = true;
    private boolean showHistory = true;    
    private boolean batchMode;

    private long selectedItemId;
    private String relatingItemRefId;
    private Collection<Long> itemIds;

    private List<ColumnHeading> columnHeadings;
    private Map<String, FilterCriteria> filterCriteriaMap = new LinkedHashMap<String, FilterCriteria>();

    private String defaultVisibleFlags;

    public ItemSearch(User user) {
        this.user = user;
        this.columnHeadings = ColumnHeading.getColumnHeadings();
        this.defaultVisibleFlags = getVisibleFlags();
    }

    public ItemSearch(Space space) {        
        this.space = space;        
        this.columnHeadings = ColumnHeading.getColumnHeadings(space);
        this.defaultVisibleFlags = getVisibleFlags();
    }      

    public void initFromPageParameters(PageParameters params, User user, Jtrac jtrac) {       
        showHistory = params.get("showHistory").toBoolean(true);
		try {
			pageSize = params.get("pageSize").toInt(Integer.parseInt(jtrac.loadConfig("items.search.num")));
		} catch (RuntimeException rtex) { /* ignore, the default is fine */ }
        sortDescending = !params.get("sortAscending").toBoolean(false);
        String candidateSortField = params.get("sortFieldName").toString("id");
        sortFieldName = ColumnHeading.isValidFieldOrColumnName(candidateSortField) ? candidateSortField : "id";
        for(String name : params.getNamedKeys()) {
            if(ColumnHeading.isValidFieldOrColumnName(name)) {
                ColumnHeading ch = getColumnHeading(name);
                ch.loadFromQueryString(params.get(name).toString(), user, jtrac);
            }
        }
        if (!params.get("searchText").isNull()) {
            setSearchText(params.get("searchText").toString());
        }
        // Backward compatibility: if searchText was not provided, but legacy summary was passed
        ColumnHeading summaryCh = getColumnHeading(SUMMARY);
        if (getSearchText() == null && summaryCh != null && summaryCh.getFilterCriteria().getValue() != null) {
            String summaryVal = (String) summaryCh.getFilterCriteria().getValue();
            if (summaryVal != null && summaryVal.trim().length() > 0) {
                setSearchText(summaryVal.trim());
                summaryCh.getFilterCriteria().setExpression(null);
                summaryCh.getFilterCriteria().setValue(null);
            }
        }
        relatingItemRefId = params.get("relatingItemRefId").toOptionalString();
        String visibleFlags = params.get("cols").toOptionalString();
        if(visibleFlags != null) {
            int i = 0;
            for(ColumnHeading ch : columnHeadings) {
                if(i >= visibleFlags.length()) {
                    break;
                }
                char flag = visibleFlags.charAt(i);
                if(flag == '1') {
                    ch.setVisible(true);
                } else {
                    ch.setVisible(false);
                }
                i++;                
            }
        }
    }
    
    private String getVisibleFlags() {
        StringBuilder visibleFlags = new StringBuilder();
        for(ColumnHeading ch : columnHeadings) {
            if(ch.isVisible()) {
                visibleFlags.append("1");                
            } else  {
                visibleFlags.append("0");
            }            
        } 
        return visibleFlags.toString();
    }
    
    public PageParameters getAsQueryString() {
        PageParameters params = new PageParameters();
        if(space != null) {
            params.set("s", space.getId() + "");
        }                
        for(ColumnHeading ch : columnHeadings) {
            String s = ch.getAsQueryString();
            if(s != null) {
                params.set(ch.getNameText(), s);
            }           
        }   
        if(getSearchText() != null) {
            params.set("searchText", getSearchText());
        }   
        String visibleFlags = getVisibleFlags();
        if(!visibleFlags.equals(defaultVisibleFlags)) {
            params.set("cols", visibleFlags.toString());
        }        
        if(showHistory) {
            params.set("showHistory", "true");
        } else {
            params.set("showHistory", "false");
        }
        if(pageSize != 25) {
            params.set("pageSize", pageSize + "");
        }
        if(!sortDescending) {
            params.set("sortAscending", "true");
        }
        if(!sortFieldName.equals("id")) {
            params.set("sortFieldName", sortFieldName);
        }
        if(relatingItemRefId != null) {
            params.set("relatingItemRefId", relatingItemRefId);
        }
        return params;
    }    
        
    private DetachedCriteria parent; // temp working variable hack
    
    // have to do this two step process as "order by" clause conflicts with "count (*)" clause
    // so the DAO has to use getCriteriaForCount() separately
    public DetachedCriteria getCriteria() {
        DetachedCriteria criteria = getCriteriaForCount();
        if (sortFieldName == null || "lastChanged".equals(sortFieldName)) { // lastChanged is a computed column
            sortFieldName = "id"; // effectively is a sort on created date
        }
        if(sortFieldName.equals("id") || sortFieldName.equals("space")) {
            if(showHistory) {
                // if showHistory: sort by item.id and then history.id
                if(sortDescending) {
                    if(space == null) {
                        DetachedCriteria parentSpace = parent.createCriteria("space");
                        parentSpace.addOrder(Order.desc("name"));                        
                    }                    
                    criteria.addOrder(Order.desc("parent.id"));
                    criteria.addOrder(Order.desc("id"));
                } else {
                    if(space == null) {
                        DetachedCriteria parentSpace = parent.createCriteria("space");
                        parentSpace.addOrder(Order.asc("name"));                        
                    }                   
                    criteria.addOrder(Order.asc("parent.id"));
                    criteria.addOrder(Order.asc("id"));                
                }
            } else {
                if (sortDescending) {
                    if(space == null) {
                        DetachedCriteria parentSpace = criteria.createCriteria("space");
                        parentSpace.addOrder(Order.desc("name"));
                    }
                    criteria.addOrder(Order.desc("id"));
                } else {
                    if(space == null) {
                        DetachedCriteria parentSpace = criteria.createCriteria("space");
                        parentSpace.addOrder(Order.asc("name"));
                    }                    
                    criteria.addOrder(Order.asc("id"));
                }                 
            }
        } else {        
            if (sortDescending) {
                criteria.addOrder(Order.desc(sortFieldName));
            } else {
                criteria.addOrder(Order.asc(sortFieldName));
            } 
        }
        return criteria;
    }    
    
    public DetachedCriteria getCriteriaForCount() {               
        DetachedCriteria criteria = null;        
        if (showHistory) {
            criteria = DetachedCriteria.forClass(History.class);           
            // apply restrictions to parent, this is an inner join =============
            parent = criteria.createCriteria("parent");
            if(space == null) {
                Collection<Space> spaces = getSelectedSpaces();
                if (user != null && user.isSuperUser() && (spaces == null || spaces.isEmpty())) {
                    // Superuser searching without specific space filter: do not restrict by space
                } else if (spaces != null && !spaces.isEmpty()) {
                    parent.add(Restrictions.in("space", spaces));
                } else {
                    parent.add(Restrictions.sqlRestriction("1=0"));
                }
            } else {
                parent.add(Restrictions.eq("space", space));
            } 
            if (itemIds != null) {
                parent.add(Restrictions.in("id", itemIds));
            }             
        } else {
            criteria = DetachedCriteria.forClass(Item.class);
            if(space == null) {
                Collection<Space> spaces = getSelectedSpaces();
                if (user != null && user.isSuperUser() && (spaces == null || spaces.isEmpty())) {
                    // Superuser searching without specific space filter: do not restrict by space
                } else if (spaces != null && !spaces.isEmpty()) {
                    criteria.add(Restrictions.in("space", spaces));
                } else {
                    criteria.add(Restrictions.sqlRestriction("1=0"));
                }
            } else {
                criteria.add(Restrictions.eq("space", space));
            } 
            if (itemIds != null) {
                criteria.add(Restrictions.in("id", itemIds));
            }             
        }        
        for(ColumnHeading ch : columnHeadings) {
            ch.addRestrictions(criteria);
        }
        return criteria;
    }
    
    public List<Field> getFields() {
        if(space == null) {
            List<Field> list = new ArrayList<Field>(2);
            Field severity = new Field(Field.Name.SEVERITY);
            severity.initOptions();
            list.add(severity);
            Field priority = new Field(Field.Name.PRIORITY);
            priority.initOptions();
            list.add(priority);
            return list;
        } else {
            return space.getMetadata().getFieldList();
        }        
    }    
    
    public ColumnHeading getColumnHeading(ColumnHeading.Name name) {
        for(ColumnHeading ch : columnHeadings) {
            if(ch.getName() == name) {
                return ch;                
            }
        }
        return null;                
    }
    
    private ColumnHeading getColumnHeading(String name) {
        for(ColumnHeading ch : columnHeadings) {
            if(ch.getNameText().equals(name)) {
                return ch;                
            }
        }
        return null;                
    }    
    
    private String getStringValue(ColumnHeading ch) {
        String s = (String) ch.getFilterCriteria().getValue();
        if(s == null || s.trim().length() == 0) {            
            ch.getFilterCriteria().setExpression(null);
            return null;
        }       
        return s;        
    }
    
    public String getRefId() {
        ColumnHeading ch = getColumnHeading(ID);
        return getStringValue(ch);
    }
    
    public String getSearchText() {
        ColumnHeading ch = getColumnHeading(DETAIL);
        return getStringValue(ch);
    }

    public void setSearchText(String text) {
        ColumnHeading ch = getColumnHeading(DETAIL);
        if (ch != null) {
            if (text != null && text.trim().length() > 0) {
                ch.getFilterCriteria().setExpression(FilterCriteria.Expression.CONTAINS);
                ch.getFilterCriteria().setValue(text.trim());
            } else {
                ch.getFilterCriteria().setExpression(null);
                ch.getFilterCriteria().setValue(null);
            }
        }
    }
    
    public Collection<Space> getSelectedSpaces() {
        ColumnHeading ch = getColumnHeading(SPACE);
        if (ch == null) {
            if (user != null && user.isSuperUser()) {
                return Collections.emptyList();
            }
            return user != null ? user.getSpaces() : Collections.<Space>emptyList();
        }
        List values = ch.getFilterCriteria().getValues();
        if(values == null || values.size() == 0) {
            ch.getFilterCriteria().setExpression(null);
            if (user != null && user.isSuperUser()) {
                return Collections.emptyList();
            }
            return user != null ? user.getSpaces() : Collections.<Space>emptyList();
        }
        return values;
    }
           
    public void toggleSortDirection() {
        sortDescending = !sortDescending;
    }      
    
    private List getSingletonList(Object o) {
        List list = new ArrayList(1);
        list.add(o);
        return list;
    }
    
    public void setLoggedBy(User loggedBy) {
        ColumnHeading ch = getColumnHeading(LOGGED_BY);
        ch.getFilterCriteria().setExpression(FilterCriteria.Expression.IN);
        ch.getFilterCriteria().setValues(getSingletonList(loggedBy));
    }
    
    public void setAssignedTo(User assignedTo) {
        ColumnHeading ch = getColumnHeading(ASSIGNED_TO);
        ch.getFilterCriteria().setExpression(FilterCriteria.Expression.IN);
        ch.getFilterCriteria().setValues(getSingletonList(assignedTo));
    }
    
    public void setStatus(int i) {
        ColumnHeading ch = getColumnHeading(STATUS);
        ch.getFilterCriteria().setExpression(FilterCriteria.Expression.IN);
        ch.getFilterCriteria().setValues(getSingletonList(i));
    }

    public List<ColumnHeading> getColumnHeadingsToRender() {
        List<ColumnHeading> list = new ArrayList<ColumnHeading>(columnHeadings.size());
        for(ColumnHeading ch : columnHeadings) {
            if(ch.isVisible()) {
                list.add(ch);
            }
        }
        return list;
    }    

    public List<ColumnHeading> getColumnHeadingsToSearch() {
        List<ColumnHeading> list = new ArrayList<ColumnHeading>(columnHeadings.size());
        for (ColumnHeading ch : columnHeadings) {
			if (!ch.getNameText().equals("lastChanged")
                    && !ch.getNameText().equals("summary")
                    && !ch.getNameText().equals("detail")) {
                list.add(ch);
            }
        }
        return list;
    }

    //==========================================================================
    
    public boolean isBatchMode() {
        return batchMode;
    }

    public void setBatchMode(boolean batchMode) {
        this.batchMode = batchMode;
    }
    
    public Space getSpace() {
        return space;
    }

    public void setSpace(Space space) {
        this.space = space;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public void setCurrentPage(int currentPage) {
        this.currentPage = currentPage;
    }

    public long getResultCount() {
        return resultCount;
    }

    public void setResultCount(long resultCount) {
        this.resultCount = resultCount;
    }

    public String getSortFieldName() {
        return sortFieldName;
    }

    public void setSortFieldName(String sortFieldName) {
        if (sortFieldName != null && ColumnHeading.isValidFieldOrColumnName(sortFieldName)) {
            this.sortFieldName = sortFieldName;
        } else {
            this.sortFieldName = "id";
        }
    }

    public boolean isSortDescending() {
        return sortDescending;
    }

    public void setSortDescending(boolean sortDescending) {
        this.sortDescending = sortDescending;
    }

    public boolean isShowHistory() {
        return showHistory;
    }

    public void setShowHistory(boolean showHistory) {
        this.showHistory = showHistory;
    }

    public long getSelectedItemId() {
        return selectedItemId;
    }

    public void setSelectedItemId(long selectedItemId) {
        this.selectedItemId = selectedItemId;
    }

    public String getRelatingItemRefId() {
        return relatingItemRefId;
    }

    public void setRelatingItemRefId(String relatingItemRefId) {
        this.relatingItemRefId = relatingItemRefId;
    }

    public Collection<Long> getItemIds() {
        return itemIds;
    }

    public void setItemIds(Collection<Long> itemIds) {
        this.itemIds = itemIds;
    }

    public List<ColumnHeading> getColumnHeadings() {
        return columnHeadings;
    }

    public void setColumnHeadings(List<ColumnHeading> columnHeadings) {
        this.columnHeadings = columnHeadings;
    }

    public Map<String, FilterCriteria> getFilterCriteriaMap() {
        return filterCriteriaMap;
    }

    public void setFilterCriteriaMap(Map<String, FilterCriteria> filterCriteriaMap) {
        this.filterCriteriaMap = filterCriteriaMap;
    }
    
}
