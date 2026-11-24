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

import info.jtrac.domain.User;
import java.util.Arrays;
import java.util.List;
import org.apache.wicket.AttributeModifier;
import org.apache.wicket.markup.head.OnLoadHeaderItem;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.IChoiceRenderer;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.PageableListView;
import org.apache.wicket.markup.html.navigation.paging.PagingNavigator;
import org.apache.wicket.model.CompoundPropertyModel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.model.PropertyModel;

/**
 * user management page
 */
public class UserListPage extends BasePage {

    private long selectedUserId;

    private String searchText = "";
    private String searchOn = "name";
    private int pageSize = 25;
    private PageableListView<User> listView;

    public void setSelectedUserId(long selectedUserId) {
        this.selectedUserId = selectedUserId;
    }

    public UserListPage() {
        addOptionsBreadcrumb();
        String configured = getJtrac().loadConfig("users.list.pageSize");
        if (configured != null) {
            try {
                int p = Integer.parseInt(configured.trim());
                if (p > 0 || p == -1) {
                    pageSize = p;
                }
            } catch (Exception ignored) { }
        }

        add(new Link("create") {
            public void onClick() {
                UserFormPage page = new UserFormPage();
                page.setPrevious(UserListPage.this);
                setResponsePage(page);
            }            
        });

        LoadableDetachableModel<List<User>> userListModel = new LoadableDetachableModel<List<User>>() {
            @Override
            protected List<User> load() {                
                if (searchText == null || searchText.trim().isEmpty()) {
                    return getJtrac().findAllUsers();
                } else {
                    return getJtrac().findUsersMatching(searchText.trim(), searchOn);
                }
            }
        };        

        final SimpleAttributeModifier sam = new SimpleAttributeModifier("class", "alt");

        int rows = (pageSize == -1) ? Integer.MAX_VALUE : pageSize;
        listView = new PageableListView<User>("users", userListModel, rows) {
            @Override
            protected void populateItem(ListItem<User> listItem) {                
                final User user = listItem.getModelObject();                
                if (selectedUserId == user.getId()) {
                    listItem.add(new SimpleAttributeModifier("class", "selected"));
                } else if(listItem.getIndex() % 2 == 1) {
                    listItem.add(sam);
                }                                 
                listItem.add(new Label("name", new PropertyModel<>(user, "name")));
                listItem.add(new Label("loginName", new PropertyModel<>(user, "loginName")));                                               
                listItem.add(new Label("email", new PropertyModel<>(user, "email")));
                listItem.add(new Label("info", new PropertyModel<>(user, "info")));
                listItem.add(new Label("locale", new PropertyModel<>(user, "locale")));
                listItem.add(new WebMarkupContainer("locked").setVisible(user.isLocked()));
                listItem.add(new Link("edit") {
                    public void onClick() {
                        UserFormPage page = new UserFormPage(user);
                        page.setPrevious(UserListPage.this);
                        setResponsePage(page);
                    }                    
                });                 
                listItem.add(new Link("allocate") {
                    public void onClick() {
                        setResponsePage(new UserAllocatePage(user.getId(), UserListPage.this));
                    }                    
                });
            }            
        };
        add(listView);

        PagingNavigator navigator = new PagingNavigator("navigator", listView) {
            @Override
            public boolean isVisible() {
                return listView.getPageCount() > 1;
            }
        };
        add(navigator);

        add(new SearchForm("form"));
    }
    
    /**
     * wicket form
     */
    private class SearchForm extends Form {        

        public String getSearchText() {
            return searchText;
        }

        public void setSearchText(String searchText) {
            UserListPage.this.searchText = searchText;
        }
        
        public String getSearchOn() {
            return searchOn;
        }

        public void setSearchOn(String searchOn) {
            UserListPage.this.searchOn = searchOn;
        }

        public Integer getPageSize() {
            return pageSize;
        }

        public void setPageSize(Integer pageSize) {
            if (pageSize != null) {
                UserListPage.this.pageSize = pageSize;
            }
        }

        public SearchForm(String id) {
            super(id);
            setModel(new CompoundPropertyModel(this));
            List<String> searchOnOptions = Arrays.asList(new String[] {"name", "loginName", "email"});
            DropDownChoice searchOnChoice = new DropDownChoice("searchOn", searchOnOptions, new IChoiceRenderer() {
                public Object getDisplayValue(Object o) {
                    String s = (String) o;
                    if(s.equals("name")) {
                        s = "userName"; // to match i18 key
                    }
                    return localize("user_list." + s);
                }
                public String getIdValue(Object o, int i) {
                    return o.toString();
                }                
            });
            add(searchOnChoice);

            final TextField searchTextField = new TextField("searchText");
            searchTextField.setOutputMarkupId(true);
            add(searchTextField);

            List<Integer> sizes = Arrays.asList(new Integer[] {10, 25, 50, 100, -1});
            DropDownChoice<Integer> pageSizeChoice = new DropDownChoice<Integer>("pageSize", sizes, new IChoiceRenderer<Integer>() {
                public Object getDisplayValue(Integer o) {
                    return o == -1 ? localize("item_search_form.noLimit") : o.toString();
                }
                public String getIdValue(Integer o, int i) {
                    return o.toString();
                }
            });
            pageSizeChoice.add(AttributeModifier.replace("onchange", "this.form.submit()"));
            add(pageSizeChoice);

            add(new HeaderContributor(response -> {
                response.render(OnLoadHeaderItem.forScript("document.getElementById('" + searchTextField.getMarkupId() + "').focus()"));
            }));            
        }

        @Override
        protected void onSubmit() {
            long rows = (pageSize == -1) ? Integer.MAX_VALUE : pageSize;
            listView.setItemsPerPage(rows);
            listView.setCurrentPage(0);
        }
    }
}
