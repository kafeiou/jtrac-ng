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
import info.jtrac.domain.ColumnHeading.Name;
import info.jtrac.domain.Space;
import info.jtrac.domain.User;
import info.jtrac.util.WebUtils;

import java.util.EnumMap;
import java.util.Map;

import org.apache.wicket.Component;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.model.StringResourceModel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * base class for all wicket pages, this provides a way to access the spring managed service layer
 * as well as other convenience common methods also takes care of the standard template for all
 * pages which are using wicket markup inheritance
 */
public abstract class BasePage extends WebPage {
    
    protected static final Logger logger = LoggerFactory.getLogger(BasePage.class);        
    
    // helper to retrieve localized column labels using Wicket i18n
    public static Map<Name, String> getLocalizedLabels(Component c) {
        Map<Name, String> map = new EnumMap<Name, String>(Name.class);
        for(Name name : Name.values()) {
            map.put(name, c.getLocalizer().getString("item_list." + name.getText(), c));
        }
        return map;
    }     
    
    protected Jtrac getJtrac() {
        return JtracApplication.get().getJtrac();
    }          
    
    protected User getPrincipal() {
        return JtracSession.get().getUser();
    }
    
    protected void setCurrentSpace(Space space) {
        JtracSession.get().setCurrentSpace(space);
    }      
    
    protected Space getCurrentSpace() {
        return JtracSession.get().getCurrentSpace();
    }                
    
    protected String localize(String key) {
        return getLocalizer().getString(key, this);
    }
    
    protected String localize(String key, Object... params) {
        return new StringResourceModel(key, this).setParameters(params).getString();
    } 

    public BasePage() { 
        add(new IndividualHeadPanel().setRenderBodyOnly(true));
        add(new HeaderPanel().setRenderBodyOnly(true));
        String jtracVersion = getJtrac().getReleaseVersion();
        add(new Label("version", jtracVersion));
        add(new Label("title", "JTrac NG"));
    }

    @Override
    public void renderHead(org.apache.wicket.markup.head.IHeaderResponse response) {
        super.renderHead(response);
        String cp = getRequest().getContextPath();
        String version = getJtrac() != null ? getJtrac().getReleaseVersion() : "2.3.3";
        String timestamp = (getJtrac() != null && getJtrac().getReleaseTimestamp() != null) ? getJtrac().getReleaseTimestamp().replaceAll("[^0-9]", "") : "";
        String versionParam = timestamp.isEmpty() ? version : (version + "&b=" + timestamp);
        response.render(org.apache.wicket.markup.head.CssHeaderItem.forUrl((cp != null && !cp.isEmpty() ? cp : "") + "/resources/jtrac.css?v=" + versionParam));
        response.render(org.apache.wicket.markup.head.JavaScriptHeaderItem.forUrl((cp != null && !cp.isEmpty() ? cp : "") + "/resources/theme.js?v=" + versionParam));
        
        String doubleSubmitScript = 
            "(function() {\n" +
            "    if (window._jtracDoubleSubmitGuardInstalled) return;\n" +
            "    window._jtracDoubleSubmitGuardInstalled = true;\n" +
            "    function resetFormState(form) {\n" +
            "        if (!form) return;\n" +
            "        form.removeAttribute('data-submitting');\n" +
            "        var btns = form.querySelectorAll('input[type=\"submit\"], button[type=\"submit\"]');\n" +
            "        for (var i = 0; i < btns.length; i++) {\n" +
            "            btns[i].style.pointerEvents = '';\n" +
            "            btns[i].style.opacity = '';\n" +
            "            btns[i].style.cursor = '';\n" +
            "        }\n" +
            "    }\n" +
            "    document.addEventListener('submit', function(e) {\n" +
            "        var form = e.target;\n" +
            "        if (!form || form.tagName.toLowerCase() !== 'form') return;\n" +
            "        if (form.checkValidity && !form.checkValidity()) return;\n" +
            "        if (form.getAttribute('data-submitting') === 'true') {\n" +
            "            e.preventDefault();\n" +
            "            e.stopPropagation();\n" +
            "            return false;\n" +
            "        }\n" +
            "        form.setAttribute('data-submitting', 'true');\n" +
            "        var btns = form.querySelectorAll('input[type=\"submit\"], button[type=\"submit\"]');\n" +
            "        for (var i = 0; i < btns.length; i++) {\n" +
            "            btns[i].style.pointerEvents = 'none';\n" +
            "            btns[i].style.opacity = '0.6';\n" +
            "            btns[i].style.cursor = 'not-allowed';\n" +
            "        }\n" +
            "        setTimeout(function() { resetFormState(form); }, 1500);\n" +
            "        setTimeout(function() { resetFormState(form); }, 5000);\n" +
            "    }, true);\n" +
            "    window.addEventListener('pageshow', function() {\n" +
            "        var forms = document.querySelectorAll('form[data-submitting=\"true\"]');\n" +
            "        for (var i = 0; i < forms.length; i++) {\n" +
            "            resetFormState(forms[i]);\n" +
            "        }\n" +
            "    });\n" +
            "})();";
        response.render(org.apache.wicket.markup.head.JavaScriptHeaderItem.forScript(doubleSubmitScript, "jtrac-double-submit-guard"));
    }
}
