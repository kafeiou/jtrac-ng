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

import info.jtrac.domain.BatchInfo;
import org.apache.wicket.ajax.AbstractAjaxTimerBehavior;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.form.AjaxButton;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.link.Link;

import java.time.Duration;

/**
 * rebuild indexes admin option
 */
public class IndexRebuildPage extends BasePage {      
    
    public IndexRebuildPage(boolean success) {
        addOptionsBreadcrumb();
        if(success) {
            add(new Label("heading", localize("index_rebuild_success.message")));
            add(new WebMarkupContainer("form").setVisible(false));
        } else {
            add(new Label("heading", localize("index_rebuild.heading")));
            add(new RebuildIndexesForm("form"));
        }
    }        
    
    /**
     * wicket form
     */    
    private class RebuildIndexesForm extends Form<Void> {
        
        public RebuildIndexesForm(String id) {
            super(id);

            final WebMarkupContainer actions = new WebMarkupContainer("actions");
            actions.setOutputMarkupId(true);
            actions.setOutputMarkupPlaceholderTag(true);
            add(actions);

            final Label progress = new Label("progress", new AbstractReadOnlyModel<String>() {
                @Override
                public String getObject() {
                    BatchInfo status = getJtrac().getIndexRebuildStatus();
                    if (status == null) {
                        return "";
                    }
                    return status.getProgressText();
                }
            });
            progress.setOutputMarkupId(true);
            progress.setOutputMarkupPlaceholderTag(true);
            add(progress);

            final AbstractAjaxTimerBehavior timer = new AbstractAjaxTimerBehavior(Duration.ofSeconds(1)) {
                @Override
                protected void onTimer(AjaxRequestTarget target) {
                    BatchInfo status = getJtrac().getIndexRebuildStatus();
                    if (status != null && status.isComplete()) {
                        stop(target);
                        if (status.getErrorMessage() != null) {
                            setResponsePage(new ErrorPage(status.getErrorMessage()));
                        } else {
                            setResponsePage(new IndexRebuildPage(true));
                        }
                    } else {
                        target.add(progress);
                    }
                }
            };

            BatchInfo currentStatus = getJtrac().getIndexRebuildStatus();
            boolean isRunning = currentStatus != null && !currentStatus.isComplete();

            if (isRunning) {
                actions.setVisible(false);
                progress.add(timer);
            }

            AjaxButton button = new AjaxButton("start", this) {
                @Override
                protected void onSubmit(AjaxRequestTarget target) {
                    getJtrac().startRebuildIndexes();
                    actions.setVisible(false);
                    target.add(actions);

                    progress.add(timer);
                    target.add(progress);
                }
            };
            actions.add(button);

            actions.add(new Link<Void>("cancel") {
                @Override
                public void onClick() {
                    setResponsePage(OptionsPage.class);
                }
            });
        }
        
    }
    
}
