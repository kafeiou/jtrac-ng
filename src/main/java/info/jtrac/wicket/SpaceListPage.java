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

import info.jtrac.domain.Space;
import info.jtrac.domain.User;
import java.util.Arrays;
import java.util.List;
import org.apache.wicket.AttributeModifier;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.IChoiceRenderer;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.PageableListView;
import org.apache.wicket.markup.html.navigation.paging.PagingNavigator;
import org.apache.wicket.model.CompoundPropertyModel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.model.PropertyModel;

/**
 * space management page
 */
public class SpaceListPage extends BasePage {
    
    private long selectedSpaceId;
    private int pageSize = 25;
    private PageableListView<Space> listView;
    
    public void setSelectedSpaceId(long selectedSpaceId) {
        this.selectedSpaceId = selectedSpaceId;
    }
      
    public SpaceListPage() {
        addOptionsBreadcrumb();
        String configured = getJtrac().loadConfig("spaces.list.pageSize");
        if (configured != null) {
            try {
                int p = Integer.parseInt(configured.trim());
                if (p > 0 || p == -1) {
                    pageSize = p;
                }
            } catch (Exception ignored) { }
        }
        
        final User principal = getPrincipal();
        
        // since this admin screen can be seen by space-admins,
        // only allow super users to create new space
        add(new Link("create") {
            public void onClick() {
                SpaceFormPage page = new SpaceFormPage();
                page.setPrevious(SpaceListPage.this);
                setResponsePage(page);
            }            
        }.setVisible(principal.isSuperUser()));
        
        LoadableDetachableModel<List<Space>> spaceListModel = new LoadableDetachableModel<List<Space>>() {
            @Override
            protected List<Space> load() {                
                if (principal.isSuperUser()) {                    
                    return getJtrac().findAllSpaces();
                } else {
                    return principal.getSpacesWhereRoleIsAdmin();
                }
            }
        };
        
        final SimpleAttributeModifier sam = new SimpleAttributeModifier("class", "alt");
        
        int rows = (pageSize == -1) ? Integer.MAX_VALUE : pageSize;
        listView = new PageableListView<Space>("spaces", spaceListModel, rows) {
            @Override
            protected void populateItem(ListItem<Space> listItem) {                
                final Space space = listItem.getModelObject();                
                if (selectedSpaceId == space.getId()) {
                    listItem.add(new SimpleAttributeModifier("class", "selected"));
                } else if (listItem.getIndex() % 2 == 1) {
                    listItem.add(sam);
                }                                 
                listItem.add(new Label("prefixCode", new PropertyModel<>(space, "prefixCode")));
                listItem.add(new Label("name", new PropertyModel<>(space, "name")));
                Link edit = new Link("edit") {
                    public void onClick() {
                        Space temp = getJtrac().loadSpace(space.getId());
                        temp.getMetadata().getXmlString();  // hack to override lazy loading
                        SpaceFormPage page = new SpaceFormPage(temp);
                        page.setPrevious(SpaceListPage.this);
                        setResponsePage(page);                        
                    }                    
                };
                listItem.add(edit);
                listItem.add(new Label("description", new PropertyModel<>(space, "description")));
                listItem.add(new Link("allocate") {
                    public void onClick() {                                                                     
                        setResponsePage(new SpaceAllocatePage(space.getId(), SpaceListPage.this));
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

        add(new PageSizeForm("form"));
    }

    private class PageSizeForm extends Form {

        public Integer getPageSize() {
            return pageSize;
        }

        public void setPageSize(Integer pageSize) {
            if (pageSize != null) {
                SpaceListPage.this.pageSize = pageSize;
            }
        }

        public PageSizeForm(String id) {
            super(id);
            CompoundPropertyModel model = new CompoundPropertyModel(this);
            setModel(model);

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
        }

        @Override
        protected void onSubmit() {
            long rows = (pageSize == -1) ? Integer.MAX_VALUE : pageSize;
            listView.setItemsPerPage(rows);
            listView.setCurrentPage(0);
        }
    }
}
