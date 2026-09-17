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

import java.io.Serializable;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Simple name value pair to hold configuration parameters
 * in the database for JTrac, e.g. SMTP e-mail server, etc.
 * TODO better validation, type-safety
 */
public class Config implements Serializable {
    
    private String param;  // someone reported that "key" is a reserved word in MySQL
    private String value;

    private static final Set<String> PARAMS;
    private static final Set<String> BOOLEAN_PARAMS;
    private static final Set<String> NUMBER_PARAMS;
    private static final Set<String> COLOR_PARAMS;

    // set up a static set of valid config key names
    static {
        PARAMS = new LinkedHashSet<String>();
        PARAMS.add("mail.server.host");
        PARAMS.add("mail.server.port");
        PARAMS.add("mail.server.username");
        PARAMS.add("mail.server.password");
        PARAMS.add("mail.server.starttls.enable");
		PARAMS.add("mail.server.ssl.enable");
        PARAMS.add("mail.subject.prefix");
        PARAMS.add("mail.from");
        PARAMS.add("mail.smtp.localhost");
        PARAMS.add("mail.session.jndiname");
        PARAMS.add("mail.dkim.signingDomain");
        PARAMS.add("mail.dkim.selector");
        PARAMS.add("mail.dkim.derFile");
        PARAMS.add("mail.dkim.identity");
        PARAMS.add("jtrac.url.base");
        PARAMS.add("jtrac.header.picture");
        PARAMS.add("jtrac.header.text");
        PARAMS.add("jtrac.edit.item");
        PARAMS.add("jtrac.comment.closed");
        PARAMS.add("locale.default");
        PARAMS.add("session.timeout");
        PARAMS.add("attachment.maxsize");
        PARAMS.add("pwd.minLength");
        PARAMS.add("markdown.enabled");
        PARAMS.add("attachments.openNewWindow");
        PARAMS.add("items.search.num");
        PARAMS.add("users.list.pageSize");
        PARAMS.add("spaces.list.pageSize");
        PARAMS.add("attachment.index.maxSizeMb");
        PARAMS.add("attachment.index.maxChars");
        PARAMS.add("security.privacy.headers.enabled");
        PARAMS.add("mail.inbound.enabled");
        PARAMS.add("mail.inbound.server.host");
        PARAMS.add("mail.inbound.server.port");
        PARAMS.add("mail.inbound.username");
        PARAMS.add("mail.inbound.password");
        PARAMS.add("mail.inbound.ssl.enable");
        PARAMS.add("mail.inbound.starttls.enable");
        PARAMS.add("mail.inbound.ssl.trust.all");
        PARAMS.add("llm.ollama.url");
        PARAMS.add("llm.ollama.model");
        PARAMS.add("llm.ollama.api.key");
        PARAMS.add("llm.ollama.timeout");
        PARAMS.add("llm.retrieval.max_tickets");
        PARAMS.add("lucene.analyzer.version");

        BOOLEAN_PARAMS = new LinkedHashSet<String>();
        BOOLEAN_PARAMS.add("mail.server.starttls.enable");
		BOOLEAN_PARAMS.add("mail.server.ssl.enable");
        BOOLEAN_PARAMS.add("mail.inbound.enabled");
        BOOLEAN_PARAMS.add("mail.inbound.ssl.enable");
        BOOLEAN_PARAMS.add("mail.inbound.starttls.enable");
        BOOLEAN_PARAMS.add("mail.inbound.ssl.trust.all");
        BOOLEAN_PARAMS.add("jtrac.edit.item");
        BOOLEAN_PARAMS.add("markdown.enabled");
        BOOLEAN_PARAMS.add("attachments.openNewWindow");
        BOOLEAN_PARAMS.add("jtrac.comment.closed");
        BOOLEAN_PARAMS.add("security.privacy.headers.enabled");

        NUMBER_PARAMS = new LinkedHashSet<String>();
        NUMBER_PARAMS.add("mail.server.port");
        NUMBER_PARAMS.add("mail.inbound.server.port");
        NUMBER_PARAMS.add("llm.ollama.timeout");
        NUMBER_PARAMS.add("llm.retrieval.max_tickets");
        NUMBER_PARAMS.add("attachment.maxsize");
        NUMBER_PARAMS.add("pwd.minLength");
        NUMBER_PARAMS.add("items.search.num");
        NUMBER_PARAMS.add("users.list.pageSize");
        NUMBER_PARAMS.add("spaces.list.pageSize");
        NUMBER_PARAMS.add("attachment.index.maxSizeMb");
        NUMBER_PARAMS.add("attachment.index.maxChars");

        COLOR_PARAMS = new LinkedHashSet<String>();
    }

    public static Set<String> getParams() {
        return PARAMS;
    }

    public static boolean isBoolean (String param) {
        return BOOLEAN_PARAMS.contains(param);
    }

    public static boolean isNumber (String param) {
        return NUMBER_PARAMS.contains(param);
    }

    public static boolean isColor (String param) {
        return COLOR_PARAMS.contains(param);
    }

    public Config() {
        // zero arg constructor
    }
    
    public Config(String param, String value) {
        this.param = param;
        this.value = value;
    }
 
    public boolean isMailConfig() {
        return param.startsWith("mail.") || param.startsWith("jtrac.url.");
    }
    
    public boolean isAttachmentConfig() {
        return param.startsWith("attachment.");
    }
    
    public boolean isSessionTimeoutConfig() {
        return param.startsWith("session.");
    }
    
    public boolean isLocaleConfig() {
        return param.startsWith("locale.");
    }

    public boolean isLlmConfig() {
        return param != null && param.startsWith("llm.");
    }

    //==========================================================================
    
    public String getParam() {
        return param;
    }

    public void setParam(String param) {
        this.param = param;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
    
}
