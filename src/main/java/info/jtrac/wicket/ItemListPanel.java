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

package info.jtrac.wicket;

import info.jtrac.domain.AbstractItem;
import info.jtrac.domain.ColumnHeading;
import info.jtrac.domain.ColumnHeading.Name;
import static info.jtrac.domain.ColumnHeading.Name.*;
import info.jtrac.domain.History;
import info.jtrac.domain.Item;
import info.jtrac.domain.ItemSearch;
import info.jtrac.util.DateUtils;
import info.jtrac.util.ItemUtils;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.request.IRequestCycle;
import org.apache.wicket.request.IRequestHandler;
import org.apache.wicket.request.http.WebResponse;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.markup.html.panel.Fragment;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.model.ResourceModel;

/**
 * item list panel
 */
public class ItemListPanel extends BasePanel {
    
    private ItemSearch itemSearch;
    
    private void doSort(String sortFieldName) {
        if ("lastChanged".equals(sortFieldName)) {
            return;
        }
        itemSearch.setCurrentPage(0);
        if (itemSearch.getSortFieldName().equals(sortFieldName)) {
            itemSearch.toggleSortDirection();
        } else {
            itemSearch.setSortFieldName(sortFieldName);
            itemSearch.setSortDescending(false);
        }
    }
    
    public ItemListPanel(final String id, ItemSearch is) {
        super(id);
        // this.itemSearch = getCurrentItemSearch();
        this.itemSearch = is;
        LoadableDetachableModel itemListModel = new LoadableDetachableModel() {
            protected Object load() {
                logger.debug("loading item list from database");
                return getJtrac().findItems(itemSearch);
            }
        };
        
        // hack - ensure that wicket model "attach" happens NOW before pagination logic sp that
        // itemSearch is properly initialized in the LoadableDetachableModel#load() above
        itemListModel.getObject();

        //======================== PAGINATION ==================================
        
        int pageCount = 1;
        final int pageSize = itemSearch.getPageSize();
        long resultCount = itemSearch.getResultCount();
        if (pageSize != -1) {
            pageCount = (int) Math.ceil((double) resultCount / pageSize);
        }
        final int currentPage = itemSearch.getCurrentPage();
        final int totalPages = pageCount;
        
        Link link = new Link("count") {
            public void onClick() {
                // return to item search form
                itemSearch.setCurrentPage(0);
                setResponsePage(new ItemSearchFormPage(itemSearch));                
            }
        };
        link.add(new Label("count", resultCount + ""));
        String resultCountMessage = resultCount == 1 ? "item_list.recordFound" : "item_list.recordsFound";
        link.add(new Label("recordsFound", new ResourceModel(resultCountMessage)));        
        add(link);        
        
        WebMarkupContainer pagination = new WebMarkupContainer("pagination");
        
        if(pageCount > 1) {
            Link firstOn = new Link("firstOn") {
                public void onClick() {
                    itemSearch.setCurrentPage(0);
                    setResponsePage(new ItemListPage(itemSearch));
                }
            };
            firstOn.add(new Label("firstOn", "|<<"));
            Label firstOff = new Label("firstOff", "|<<");
            firstOff.setVisible(false);
            if(currentPage == 0) {
                firstOn.setVisible(false);
            }
            pagination.add(firstOn);
            pagination.add(firstOff);

            Link prevOn = new Link("prevOn") {
                public void onClick() {
                    itemSearch.setCurrentPage(currentPage - 1);                    
                    // TODO avoid next line, refresh pagination only
                    setResponsePage(new ItemListPage(itemSearch));                    
                }
            };
            prevOn.add(new Label("prevOn", "<"));
            Label prevOff = new Label("prevOff", "<");
            prevOff.setVisible(false);
            if(currentPage == 0) {
                prevOn.setVisible(false);
            }
            pagination.add(prevOn);
            pagination.add(prevOff);
            
            int windowSize = 10;
            int startPage = 0;
            int endPage = pageCount - 1;
            if (pageCount > windowSize) {
                startPage = Math.max(0, currentPage - (windowSize / 2));
                endPage = startPage + windowSize - 1;
                if (endPage >= pageCount) {
                    endPage = pageCount - 1;
                    startPage = Math.max(0, endPage - windowSize + 1);
                }
            }
            List<Integer> pageNumbers = new ArrayList<Integer>(endPage - startPage + 1);
            for(int i = startPage; i <= endPage; i++) {
                pageNumbers.add(Integer.valueOf(i));
            }
            
            ListView pages = new ListView("pages", pageNumbers) {
                protected void populateItem(ListItem listItem) {
                    final Integer i = (Integer) listItem.getModelObject();
                    String pageNumber = i + 1 + "";
                    Link pageOn = new Link("pageOn") {
                        public void onClick() {
                            itemSearch.setCurrentPage(i);                            
                            // TODO avoid next line, refresh pagination only
                            setResponsePage(new ItemListPage(itemSearch));
                        }
                    };
                    pageOn.add(new Label("pageOn", pageNumber));
                    Label pageOff = new Label("pageOff", pageNumber);
                    if(i == currentPage) {
                        pageOn.setVisible(false);
                    } else {
                        pageOff.setVisible(false);
                    }
                    listItem.add(pageOn);
                    listItem.add(pageOff);
                }
            };
            pagination.add(pages);
            
            Link nextOn = new Link("nextOn") {
                public void onClick() {
                    itemSearch.setCurrentPage(currentPage + 1);                    
                    // TODO avoid next line, refresh pagination only
                    setResponsePage(new ItemListPage(itemSearch));                    
                }
            };
            nextOn.add(new Label("nextOn", ">"));
            Label nextOff = new Label("nextOff", ">");
            nextOff.setVisible(false);
            if(currentPage == pageCount - 1) {
                nextOn.setVisible(false);
            }
            pagination.add(nextOn);
            pagination.add(nextOff);

            Link lastOn = new Link("lastOn") {
                public void onClick() {
                    itemSearch.setCurrentPage(totalPages - 1);
                    setResponsePage(new ItemListPage(itemSearch));
                }
            };
            lastOn.add(new Label("lastOn", ">>|"));
            Label lastOff = new Label("lastOff", ">>|");
            lastOff.setVisible(false);
            if(currentPage == totalPages - 1) {
                lastOn.setVisible(false);
            }
            pagination.add(lastOn);
            pagination.add(lastOff);

            WebMarkupContainer pageInfo = new WebMarkupContainer("pageInfo");
            pageInfo.add(new Label("pageCurrent", String.valueOf(currentPage + 1)));
            pageInfo.add(new Label("pageTotal", String.valueOf(totalPages)));
            pagination.add(pageInfo);
        } else { // if pageCount == 1
            pagination.setVisible(false);
        }
        
        add(pagination);

        final Model<String> filterModel = new Model<String>(itemSearch.getSearchText());
        Form<Void> filterForm = new Form<Void>("filterForm") {
            @Override
            protected void onSubmit() {
                String val = filterModel.getObject();
                itemSearch.setCurrentPage(0);
                itemSearch.setSearchText(val);
                if (val != null && !val.trim().isEmpty()) {
                    itemSearch.setShowHistory(true);
                } else {
                    itemSearch.setShowHistory(false);
                }
                JtracSession.get().setItemSearch(itemSearch);
                setResponsePage(new ItemListPage(itemSearch));
            }
        };
        filterForm.add(new TextField<String>("filterInput", filterModel));
        add(filterForm);

        WebMarkupContainer searchKeywordAlert = new WebMarkupContainer("searchKeywordAlert");
        final String currentSearchText = itemSearch.getSearchText();
        if (currentSearchText != null && currentSearchText.trim().length() > 0) {
            searchKeywordAlert.add(new Label("searchKeywordValue", currentSearchText));
            searchKeywordAlert.add(new Link("clearSearchLink") {
                @Override
                public void onClick() {
                    itemSearch.setCurrentPage(0);
                    itemSearch.setSearchText(null);
                    itemSearch.setShowHistory(false);
                    JtracSession.get().setItemSearch(itemSearch);
                    setResponsePage(new ItemListPage(itemSearch));
                }
            });
        } else {
            searchKeywordAlert.setVisible(false);
            searchKeywordAlert.add(new WebMarkupContainer("searchKeywordValue"));
            searchKeywordAlert.add(new WebMarkupContainer("clearSearchLink"));
        }
        add(searchKeywordAlert);
        
        //====================== HEADER ========================================        

        final List<ColumnHeading> columnHeadings = itemSearch.getColumnHeadingsToRender();

        ListView headings = new ListView("headings", columnHeadings) {
            protected void populateItem(ListItem listItem) {
                final ColumnHeading ch = (ColumnHeading) listItem.getModelObject();
                Link headingLink = new Link("heading") {
                    public void onClick() {
                        if (!"lastChanged".equals(ch.getNameText())) {
                            doSort(ch.getNameText());
                        }
                    }
                };
                if (ch.getNameText().equals("lastChanged")) {
                    headingLink.setEnabled(false);
                }
                listItem.add(headingLink); 
                String label = ch.isField() ? ch.getLabel() : localize("item_list." + ch.getName());
                headingLink.add(new Label("heading", label));
                String headingClass = "col-" + ch.getNameText();
                if (ch.getNameText().equals(itemSearch.getSortFieldName())) {
                    headingClass += (itemSearch.isSortDescending() ? " order-down" : " order-up");
                }
                listItem.add(new SimpleAttributeModifier("class", headingClass));
            }
        };

        add(headings);

        //======================== ITEMS =======================================
        
        final long selectedItemId = itemSearch.getSelectedItemId();
        
        final SimpleAttributeModifier sam = new SimpleAttributeModifier("class", "alt");
        
        ListView itemList = new ListView("itemList", itemListModel) {
            protected void populateItem(ListItem listItem) {
                // cast to AbstactItem - show history may be == true
                final AbstractItem item = (AbstractItem) listItem.getModelObject();
                
                if (selectedItemId == item.getId()) {
                    listItem.add(new SimpleAttributeModifier("class", "selected"));
                } else if(listItem.getIndex() % 2 == 1) {
                    listItem.add(sam);
                }                
                
                final boolean showHistory = itemSearch.isShowHistory();
                
                ListView fieldValues = new ListView("columns", columnHeadings) {
                    protected void populateItem(ListItem listItem) {
                        ColumnHeading ch = (ColumnHeading) listItem.getModelObject();
                        listItem.add(new SimpleAttributeModifier("class", "col-" + ch.getNameText()));
                        IModel value = null;
						boolean dontEscape = false;
                        if(ch.isField()) {
                            value = new Model(item.getCustomValue(ch.getField().getName()));
                        } else {
                            switch(ch.getName()) {
                                case ID:
                                    String refId = item.getRefId();
                                    Fragment refIdFrag = new Fragment("column", "refId", ItemListPanel.this);
                                    refIdFrag.setRenderBodyOnly(true);
                                    listItem.add(refIdFrag);
                                    Link refIdLink = new BookmarkablePageLink("refId", ItemViewPage.class, new PageParameters().set("0", refId));                                
                                    refIdFrag.add(refIdLink);
                                    refIdLink.add(new Label("refId", refId));
                                    if (showHistory) {                                                                                                            
                                        int index = ((History) item).getIndex();
                                        if (index > 0) {
                                            refIdFrag.add(new Label("index", " (" + index + ")"));
                                        } else {
                                            refIdFrag.add(new WebMarkupContainer("index").setVisible(false));
                                        }
                                    } else {                                                                           
                                        refIdFrag.add(new WebMarkupContainer("index").setVisible(false));
                                    }
                                    // the first column ID is a special case, where we add a fragment.
                                    // since we have already added a fragment return, instead of "break" 
                                    // so avoid going to the new Label("column", value) after the switch case                                    
                                    return;                                    
                                case SUMMARY:
                                    value = new PropertyModel(item, "summary");
                                    break;
                                case DETAIL:                                
                                    if(showHistory) {
                                        Fragment detailFrag = new Fragment("column", "detail", ItemListPanel.this);
                                        final History history = (History) item;
                                        detailFrag.add(new AttachmentLinkPanel("attachment", history.getAttachment()));
                                        if (history.getIndex() > 0) {
                                            detailFrag.add(new Label("detail", new PropertyModel(history, "comment")));
                                        } else {
                                            detailFrag.add(new Label("detail", new PropertyModel(history, "detail")));
                                        }
                                        listItem.add(detailFrag);
                                        return;
                                    } else {                                    
										if (renderMarkdown()) {
											item.setDetail(ItemUtils.renderMarkdown(item.getDetail()));
											dontEscape = true;
										}
                                        value = new PropertyModel(item, "detail");                                    
                                    } 
                                    break;
                                case LOGGED_BY:
                                    value = new PropertyModel(item, "loggedBy.name");
                                    break;
                                case STATUS:
                                    value = new PropertyModel(item, "statusValue");
                                    break;
                                case ASSIGNED_TO:
                                    value = new PropertyModel(item, "assignedTo.name");
                                    break;
                                case TIME_STAMP:
                                    value = new Model(DateUtils.formatTimeStamp(item.getTimeStamp()));
                                    break;
                                case LAST_CHANGED:
									if (item instanceof Item) {
										History history = ((Item) item).getLatestHistory();
										if (history == null)
											value = new Model(DateUtils.formatTimeStamp(item.getTimeStamp()));
										else
											value = new Model(DateUtils.formatTimeStamp(((Item) item).getLatestHistory().getTimeStamp()));
									} else {
										value = new Model(DateUtils.formatTimeStamp(item.getTimeStamp()));
									}
                                    break;
                                case SPACE:
                                    if(showHistory) {
                                        value = new PropertyModel(item, "parent.space.name");
                                    } else {
                                        value = new PropertyModel(item, "space.name");
                                    }
                                    break;
                                default:
                                    throw new RuntimeException("Unexpected name: '" + ch.getName() + "'");                                
                            }
                        }
                        Label label = new Label("column", value);
                        label.setRenderBodyOnly(true);
						if (dontEscape)
							label.setEscapeModelStrings(false);
                        listItem.add(label);
                    }
                };
                
                listItem.add(fieldValues);
                
            }
        };
        
        add(itemList);
        
    }
    
}
