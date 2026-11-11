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

import info.jtrac.Jtrac;
import info.jtrac.domain.Role;
import info.jtrac.domain.Space;
import info.jtrac.domain.User;
import info.jtrac.util.WebUtils;

import java.util.List;
import java.util.Locale;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.Cookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.apache.wicket.Application;
import org.springframework.context.ApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;
import org.apache.wicket.Component;
import org.apache.wicket.Page;
import org.apache.wicket.RestartResponseAtInterceptPageException;
import org.apache.wicket.Session;
import org.apache.wicket.authorization.Action;
import org.apache.wicket.authorization.IAuthorizationStrategy;
import org.apache.wicket.protocol.http.WebApplication;
import org.apache.wicket.request.Request;
import org.apache.wicket.request.Response;
import org.apache.wicket.request.component.IRequestableComponent;
import org.apache.wicket.request.cycle.RequestCycle;
import org.apache.wicket.request.http.WebRequest;
import org.apache.wicket.resource.loader.IStringResourceLoader;
import org.apache.wicket.spring.injection.annot.SpringComponentInjector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main Wicket application for jtrac.
 */
public class JtracApplication extends WebApplication {
    private final static Logger logger = LoggerFactory.getLogger(JtracApplication.class);
    
    private Jtrac jtrac;
    private ApplicationContext applicationContext;
    
    public Jtrac getJtrac() {
        return jtrac;
    }
    
    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }
    
    public String getCasLoginUrl() {
        return null;
    }
    
    public String getCasLogoutUrl() {
        return null;
    }
    public static JtracApplication get() {
        return (JtracApplication) Application.get();
    }
    
    /* (non-Javadoc)
     * @see org.apache.wicket.protocol.http.WebApplication#init()
     */
    @Override
    public void init() {
        super.init();
        
        /*
         * Get hold of spring managed service layer (see BasePage, BasePanel, 
         * etc. for how it is used).
         */
        ServletContext sc = getServletContext();
        applicationContext = WebApplicationContextUtils
                .getWebApplicationContext(sc);
        jtrac = (Jtrac) applicationContext.getBean("jtrac");
        
        getComponentInstantiationListeners().add(new SpringComponentInjector(this, applicationContext));

        /*
         * Delegate Wicket i18n support to spring i18n
         */
        getResourceSettings().getStringResourceLoaders().add(
                new IStringResourceLoader() {
                    @Override
                    public String loadStringResource(Class<?> clazz, String key,
                            Locale locale, String style, String variation) {
                        try {
                            return applicationContext.getMessage(key, null,
                                    locale == null ? Session.get().getLocale() : locale);
                        } catch (Exception e) {
                            /*
                             * For performance, Wicket expects null instead of
                             * throwing an exception and Wicket may try to
                             * re-resolve using prefixed variants of the key.
                             */
                            return null;
                        }
                    }
                    
                    @Override
                    public String loadStringResource(Component component,
                            String key, Locale locale, String style, String variation) {
                        String value = loadStringResource((Class<?>) null, key,
                                locale != null ? locale : (component == null ? null : component.getLocale()), style, variation);
                        if (logger.isDebugEnabled() && value == null) {
                            logger.debug("i18n failed for key: '{}', component: {}", key, component);
                        }
                        return value;
                    }
                });
        getResourceSettings().setThrowExceptionOnMissingResource(false);
        getCspSettings().blocking().disabled();
        
        getSecuritySettings().setUnauthorizedComponentInstantiationListener(
                new org.apache.wicket.authorization.IUnauthorizedComponentInstantiationListener() {
                    @Override
                    public void onUnauthorizedInstantiation(Component component) {
                        throw new RestartResponseAtInterceptPageException(LoginPage.class);
                    }
                });

        getSecuritySettings().setAuthorizationStrategy(
                new IAuthorizationStrategy() {
                    @Override
                    public boolean isActionAuthorized(Component c, Action a) {
                        return true;
                    }

                    @Override
                    public boolean isResourceAuthorized(org.apache.wicket.request.resource.IResource resource, org.apache.wicket.request.mapper.parameter.PageParameters parameters) {
                        return true;
                    }
                    
                    @Override
                    public <T extends IRequestableComponent> boolean isInstantiationAuthorized(Class<T> clazz) {
                        if (BasePage.class.isAssignableFrom(clazz)) {
                            if (JtracSession.get().isAuthenticated()) {
                                return true;
                            }
                            /*
                             * ================================================
                             * Attempt remember-me auto login
                             * ================================================
                             */
                            if (attemptRememberMeAutoLogin()) {
                                return true;
                            }
                            
                            /*
                             * =================================================
                             * Attempt guest access if there are "public" spaces
                             * =================================================
                             */
                            List<Space> spaces = getJtrac().findSpacesWhereGuestAllowed();
                            if (spaces.size() > 0) {
                                logger.debug(spaces.size() +
                                        " public space(s) available, initializing guest user");
                                User guestUser = new User();
                                guestUser.setLoginName("guest");
                                guestUser.setName("Guest");
                                for (Space space : spaces) {
                                    guestUser.addSpaceWithRole(space, Role.ROLE_GUEST);
                                }
                                
                                JtracSession.get().setUser(guestUser);
                                // and proceed
                                return true;
                            }
                            
                            /*
                             * Not authenticated, return false to trigger unauthorized listener redirect
                             */
                            logger.debug("not authenticated, forcing login, page requested was " + clazz.getName());
                            return false;
                        }
                        return true;
                    }
                });
        
        /*
         * Friendly URLs for selected pages
         */
        mountPage("/login", LoginPage.class);
        mountPage("/logout", LogoutPage.class);
        mountPage("/dashboard", DashboardPage.class);
        mountPage("/svn", SvnStatsPage.class);
        mountPage("/options", OptionsPage.class);
        mountPage("/item/form", ItemFormPage.class);
        mountPage("/item/search", ItemSearchFormPage.class);
        mountPage("/item/list", ItemListPage.class);
        mountPage("/item/#{0}", ItemViewPage.class);
    }
    
    /* (non-Javadoc)
     * @see org.apache.wicket.Application#getHomePage()
     */
    public Class<? extends Page> getHomePage() {
        return DashboardPage.class;
    }

    @Override
    public Session newSession(Request request, Response response) {
        return new JtracSession(request);
    }
    
    /**
     * The method handles the user authentication using the login name and
     * password.
     * 
     * @param loginName The login name of the user
     * @param password The password of the user
     * @return Returns the User object or null in case of an Exception.
     */
    public User authenticate(String loginName, String password) {
        UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(
                loginName, password);
        AuthenticationManager am = (AuthenticationManager) applicationContext.getBean("authenticationManager");
        try {
            Authentication authentication = am.authenticate(token);
            User user = (User) authentication.getPrincipal();
            
            // 若為舊版 MD5 雜湊，在成功登入時自動升級為 BCrypt
            try {
                org.springframework.security.crypto.password.PasswordEncoder pe = 
                        (org.springframework.security.crypto.password.PasswordEncoder) applicationContext.getBean("passwordEncoder");
                if (pe instanceof info.jtrac.util.JtracHybridPasswordEncoder) {
                    info.jtrac.util.JtracHybridPasswordEncoder hybridEncoder = (info.jtrac.util.JtracHybridPasswordEncoder) pe;
                    if (hybridEncoder.isUpgradeRequired(user.getPassword())) {
                        user.setPassword(hybridEncoder.encode(password));
                        getJtrac().storeUser(user);
                        logger.info("Successfully upgraded password to BCrypt for user: {}", user.getLoginName());
                    }
                }
            } catch (Exception e) {
                logger.warn("Could not auto-upgrade password for user: {}, reason: {}", user.getLoginName(), e.getMessage());
            }
            
            return user;
        } catch (AuthenticationException ae) {
            logger.debug("Spring Security authentication failed: " + ae);
            return null;
        }
    }
    
    /**
     * This method will try to identify the user by using cookies and tries to
     * auto login the user.
     * 
     * @return Returns <code>true</code> on successful identification otherwise <code>false</code>.
     */
    private boolean attemptRememberMeAutoLogin() {
        logger.debug("checking cookies for remember-me auto login");
        WebRequest wr = (WebRequest) RequestCycle.get().getRequest();
        List<Cookie> cookies = wr.getCookies();
        if (cookies == null || cookies.isEmpty()) {
            logger.debug("no cookies found");
            return false;
        }
        
        for (Cookie c : cookies) {
            if (logger.isDebugEnabled()) {
                logger.debug("examining cookie: " + WebUtils.getDebugStringForCookie(c));
            }
            if (!c.getName().equals("jtrac")) {
                continue;
            }
            String value = c.getValue();
            logger.debug("found jtrac cookie: " + value);
            if (value == null) {
                continue;
            }
            int index = value.indexOf(':');
            if (index == -1) {
                continue;
            }
            String loginName = value.substring(0, index);
            String encodedPassword = value.substring(index + 1);
            logger.debug("valid cookie, attempting authentication");
            User user = (User) getJtrac().loadUserByUsername(loginName);
            if (encodedPassword.equals(user.getPassword())) {
                JtracSession.get().setUser(user);
                logger.debug("remember me login success");
                return true;
            }
        } // end for
        // no valid cookies were found
        return false;
    }
}