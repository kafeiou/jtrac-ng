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
import info.jtrac.util.WebUtils;

import jakarta.servlet.http.Cookie;

import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.OnLoadHeaderItem;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.CheckBox;
import org.apache.wicket.markup.html.form.PasswordTextField;
import org.apache.wicket.markup.html.form.StatelessForm;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.request.cycle.RequestCycle;
import org.apache.wicket.request.http.WebRequest;
import org.apache.wicket.request.http.WebResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The JTrac login page.
 */
public class LoginPage extends WebPage {
    /**
     * Logger object
     */
    private static final Logger logger = LoggerFactory.getLogger(LoginPage.class);
    
    /**
     * Default constructor
     */
    public LoginPage() {
        setVersioned(false);
        add(new IndividualHeadPanel().setRenderBodyOnly(true));
        add(new Label("title", getLocalizer().getString("login.title", this)));
        add(new LoginForm("form"));
        String jtracVersion = JtracApplication.get().getJtrac().getReleaseVersion();
        add(new Label("version", jtracVersion));
    }

    @Override
    public void renderHead(IHeaderResponse response) {
        super.renderHead(response);
        String cp = getRequest().getContextPath();
        String version = JtracApplication.get().getJtrac().getReleaseVersion();
        String timestamp = (JtracApplication.get().getJtrac().getReleaseTimestamp() != null) ? JtracApplication.get().getJtrac().getReleaseTimestamp().replaceAll("[^0-9]", "") : "";
        String versionParam = timestamp.isEmpty() ? version : (version + "&b=" + timestamp);
        response.render(org.apache.wicket.markup.head.CssHeaderItem.forUrl((cp != null && !cp.isEmpty() ? cp : "") + "/resources/jtrac.css?v=" + versionParam));
        response.render(org.apache.wicket.markup.head.JavaScriptHeaderItem.forUrl((cp != null && !cp.isEmpty() ? cp : "") + "/resources/theme.js?v=" + versionParam));
    }
    
    /**
     * Wicket form for the login (inner class)
     */
    private class LoginForm extends StatelessForm {
        /**
         * The login name of the user.
         */
        private String loginName;
        
        /**
         * The password of the user.
         */
        private String password;
        
        /**
         * The flag indicating if the user wants to be remembered or not.
         */
        private boolean rememberMe;
        
        /**
         * This method will return the login name of the user.
         * 
         * @return The login name of the user.
         */
        public String getLoginName() {
            return loginName;
        }
        
        /**
         * This method allows to set the login name entered in the form.
         * 
         * @param loginName The login name to set.
         */
        public void setLoginName(String loginName) {
            this.loginName = loginName;
        }
        
        /**
         * This method will return the password of the user.
         * 
         * @return The password of the user.
         */
        public String getPassword() {
            return password;
        }
        
        /**
         * This method allows to set the password entered in the form.
         * 
         * @param password The password to set.
         */
        public void setPassword(String password) {
            this.password = password;
        }
        
        /**
         * This method will return the flag indicating if the user
         * want's to be remembered as chosen on the form.
         * 
         * @return The flag indicating if the user wants to be remembered
         * (<code>true</code>) or not (<code>false</code>).
         */
        public boolean isRememberMe() {
            return rememberMe;
        }
        
        /**
         * This method allows to set the boolean flag to be remembered as
         * checked on the form.
         * 
         * @param rememberMe The boolean flag to set.
         */
        public void setRememberMe(boolean rememberMe) {
            this.rememberMe = rememberMe;
        }
        
        /**
         * Constructor for the login form.
         * 
         * @param id
         */
        public LoginForm(String id) {
            super(id);
            add(new WebMarkupContainer("hide") {
                @Override
                public boolean isVisible() {
                    return !LoginForm.this.hasError();
                }
            });
            add(new FeedbackPanel("feedback"));
            setModel(new BoundCompoundPropertyModel(this));
            final TextField loginNameField = new TextField("loginName");
            loginNameField.setOutputMarkupId(true);
            add(loginNameField);
            final PasswordTextField passwordField = new PasswordTextField("password");
            passwordField.setRequired(false);
            passwordField.setOutputMarkupId(true);
            add(passwordField);
            
            /*
             * Intelligently set focus on the appropriate textbox.
             */
            add(new HeaderContributor(new IHeaderContributor() {
                public void renderHead(IHeaderResponse response) {
                    String markupId;
                    if(loginNameField.getConvertedInput() == null) {
                        markupId = loginNameField.getMarkupId();
                    } else {
                        markupId = passwordField.getMarkupId();
                    }
                    response.render(OnLoadHeaderItem.forScript("document.getElementById('" + markupId + "').focus()"));
                }
            }));
            add(new CheckBox("rememberMe"));
        } // end LoginForm(String)
        
        /**
         * This method will process the login after the user hits the submit
         * button to login.
         */
        @Override
        protected void onSubmit() {
            if(loginName == null || password == null) {
                logger.error("login failed - login name " + 
                        (loginName!=null ? "is set (trimmed length=" + 
                                loginName.trim().length()+")" : "is null") + 
                        " and password " + 
                        (password!=null ? "is set" : "is null") + ".");
                error(getLocalizer().getString("login.error", this));
                return;
            }
            
            User user = JtracApplication.get().authenticate(loginName, password);
            if (user == null) {
                /*
                 * ================================
                 * Login failed!
                 * ================================
                 */
                logger.error("login failed - Authentication for login name '"+
                        loginName + "' not successful");
                error(getLocalizer().getString("login.error", this));
            } else {
                /*
                 * ================================
                 * Login success!
                 * ================================
                 */
                
                /*
                 * Set Remember me cookie if checkbox is checked on page.
                 */
                if(rememberMe) {
                    Cookie cookie = new Cookie("jtrac", loginName + ":" + JtracApplication.get().getJtrac().encodeClearText(password));
                    cookie.setMaxAge(30 * 24 * 60 * 60); // 30 days in seconds 
                    WebRequest wr = (WebRequest) RequestCycle.get().getRequest();
                    WebResponse wrsp = (WebResponse) RequestCycle.get().getResponse();
                    cookie.setPath(wr.getContextPath());
                    wrsp.addCookie(cookie);
                    logger.debug("remember me requested, cookie added, " + WebUtils.getDebugStringForCookie(cookie));
                }
                
                /*
                 * Setup session with principal
                 */
                JtracSession.get().setUser(user);
                /*
                 * Proceed to bookmarkable page or default dashboard
                 */
                continueToOriginalDestination();
                setResponsePage(DashboardPage.class);
            }
        } // end onSubmit()
    } // end inner class LoginForm
}
