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

import info.jtrac.domain.Item;
import info.jtrac.domain.Space;
import info.jtrac.domain.State;
import info.jtrac.domain.User;

import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.Cookie;

import org.springframework.security.core.context.SecurityContextHolder;

import org.apache.wicket.markup.ComponentTag;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.model.Model;
import org.apache.wicket.request.http.WebRequest;
import org.apache.wicket.request.http.WebResponse;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import info.jtrac.domain.ItemSearch;

/**
 * header navigation
 */
public class HeaderPanel extends BasePanel {    
    
    public HeaderPanel() {
        super("header");

        final User user = getPrincipal();
        Space currentSpace = getCurrentSpace();
        if (currentSpace != null && currentSpace.getId() > 0) {
            try {
                if (currentSpace.getMetadata() != null) {
                    currentSpace.getMetadata().getName();
                }
            } catch (Exception e) {
                logger.warn("space.metadata is detached/uninitialized proxy, reloading space: " + currentSpace.getId());
                currentSpace = getJtrac().loadSpace(currentSpace.getId());
                setCurrentSpace(currentSpace);
            }
        }
        final Space space = currentSpace;
        final List<Space> spaces;
        if (user != null && user.isSuperUser()) {
            spaces = getJtrac().findAllSpaces();
        } else {
            spaces = new ArrayList<Space>(user != null ? user.getSpaces() : java.util.Collections.<Space>emptyList());
        }

        add(new Link("dashboard") {
            public void onClick() {
                setCurrentSpace(null);
                setResponsePage(DashboardPage.class);
            }            
        });

        final Model<String> desktopSearchModel = new Model<String>();

        if (space == null) {
            add(new WebMarkupContainer("spaceName").setVisible(false));
            add(new WebMarkupContainer("new").setVisible(false));
            add(new Link("search") {
                public void onClick() {                    
                    // if only one space don't use generic search screen
                    if(spaces.size() == 1) {
                        Space current = spaces.get(0);
                        setCurrentSpace(current);                        
                    } else {
                        setCurrentSpace(null); // may have come here with back button!                        
                    }
                    String pendingSearch = desktopSearchModel.getObject();
                    if (pendingSearch != null && !pendingSearch.trim().isEmpty()) {
                        ItemSearch itemSearch = (getCurrentSpace() != null) ? new ItemSearch(getCurrentSpace()) : new ItemSearch(user);
                        itemSearch.setSearchText(pendingSearch.trim());
                        itemSearch.setShowHistory(true);
                        setResponsePage(new ItemSearchFormPage(itemSearch));
                    } else {
                        setResponsePage(ItemSearchFormPage.class);
                    }
                }  
                @Override
                public boolean isVisible() {
                    return spaces.size() > 0;
                }
            });            
        } else {
            add(new Label("spaceName", space.getName()));   
            add(new Label("prefixCode", space.getPrefixCode())); 
            if (user != null && user.getPermittedTransitions(space, State.NEW).size() > 0) {            
                add(new Link("new") {
                    public void onClick() {
                        setResponsePage(ItemFormPage.class);
                    }            
                });
            } else {
                add(new WebMarkupContainer("new").setVisible(false));       
            }
            
            add(new Link("search") {
                public void onClick() {
                    String pendingSearch = desktopSearchModel.getObject();
                    if (pendingSearch != null && !pendingSearch.trim().isEmpty()) {
                        ItemSearch itemSearch = new ItemSearch(space);
                        itemSearch.setSearchText(pendingSearch.trim());
                        itemSearch.setShowHistory(true);
                        setResponsePage(new ItemSearchFormPage(itemSearch));
                    } else {
                        setResponsePage(ItemSearchFormPage.class);
                    }
                }            
            });            
        }
        
        if(user == null || user.getId() == 0) {
            add(new WebMarkupContainer("export").setVisible(false));
            add(new WebMarkupContainer("options").setVisible(false));
            add(new WebMarkupContainer("logout").setVisible(false));
            add(new Link("login") {
                public void onClick() {
                    setResponsePage(LoginPage.class);
                }            
            });
            add(new WebMarkupContainer("user").setVisible(false));
        } else {
            add(new Link("export") {
                public void onClick() {
                    setResponsePage(HtmlExportPage.class);
                }
                @Override
                public boolean isVisible() {
                    return user.isSuperUser() || spaces.size() > 0;
                }
            });
            add(new Link("options") {
                public void onClick() {
                    JtracSession.get().setCurrentSpace(null); 
                    setResponsePage(OptionsPage.class);
                }            
            }); 
            add(new Link("logout") {
                public void onClick() {                                        
                    Cookie cookie = new Cookie("jtrac", "");                    
                    String path = ((WebRequest) getRequest()).getContextPath();
                    cookie.setPath(path);                    
                    ((WebResponse) getResponse()).clearCookie(cookie);                    
                    getSession().invalidate();
                    logger.debug("invalidated session and cleared cookie"); 
                    // clear security context and redirect to logout page
                    SecurityContextHolder.clearContext();
                    setResponsePage(LogoutPage.class, new PageParameters().set("locale", user.getLocale()));
                }            
            });
            add(new WebMarkupContainer("login").setVisible(false));
            add(new Label("user", user.getName()));
        }

        Form<Void> desktopSearchForm = new Form<Void>("desktopSearchForm") {
            @Override
            protected void onSubmit() {
                handleQuickSearch(desktopSearchModel.getObject(), space, spaces);
            }
        };
        desktopSearchForm.add(new TextField<String>("desktopSearchInput", desktopSearchModel));
        desktopSearchForm.setVisible(spaces.size() > 0);
        add(desktopSearchForm);

        final Model<String> mobileSearchModel = new Model<String>();
        Form<Void> mobileSearchForm = new Form<Void>("mobileSearchForm") {
            @Override
            protected void onSubmit() {
                handleQuickSearch(mobileSearchModel.getObject(), space, spaces);
            }
        };
        mobileSearchForm.add(new TextField<String>("mobileSearchInput", mobileSearchModel));
        mobileSearchForm.setVisible(spaces.size() > 0);
        add(mobileSearchForm);
    }

    private void handleQuickSearch(String searchText, Space space, List<Space> spaces) {
        if (searchText == null || searchText.trim().length() == 0) {
            return;
        }
        String trimmed = searchText.trim();
        User user = getPrincipal();

        // 1. Try smart RefID match
        List<Item> smartItems = getJtrac().findItemsBySmartRefId(trimmed, space);
        if (smartItems.size() == 1) {
            Item singleMatch = smartItems.get(0);
            if (user != null && (user.isSuperUser() || user.isAllocatedToSpace(singleMatch.getSpace().getId()))) {
                setCurrentSpace(singleMatch.getSpace());
                setResponsePage(ItemViewPage.class, new PageParameters().set("0", singleMatch.getRefId()));
                return;
            }
        }

        // 2. Ambiguous (multiple matches), nonexistent, or regular text/numbers:
        ItemSearch itemSearch;
        if (space != null) {
            itemSearch = new ItemSearch(space);
        } else if (spaces != null && spaces.size() == 1) {
            Space single = spaces.get(0);
            setCurrentSpace(single);
            itemSearch = new ItemSearch(single);
        } else {
            setCurrentSpace(null);
            itemSearch = new ItemSearch(user);
        }
        itemSearch.setSearchText(trimmed);
        itemSearch.setShowHistory(true);
        JtracSession.get().setItemSearch(itemSearch);
        setResponsePage(ItemListPage.class, itemSearch.getAsQueryString());
    }

    @Override
    public void renderHead(org.apache.wicket.markup.head.IHeaderResponse response) {
        super.renderHead(response);
        String cp = getRequest().getContextPath();
        String version = getJtrac() != null ? getJtrac().getReleaseVersion() : "2.3.3";
        String timestamp = (getJtrac() != null && getJtrac().getReleaseTimestamp() != null) ? getJtrac().getReleaseTimestamp().replaceAll("[^0-9]", "") : "";
        String versionParam = timestamp.isEmpty() ? version : (version + "&b=" + timestamp);
        response.render(org.apache.wicket.markup.head.JavaScriptHeaderItem.forUrl((cp != null && !cp.isEmpty() ? cp : "") + "/resources/theme.js?v=" + versionParam));
    }
}
