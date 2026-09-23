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

import java.util.Map;

import org.apache.wicket.AttributeModifier;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.image.Image;
import org.apache.wicket.model.IModel;

import org.springframework.util.StringUtils;

/**
 * header navigation
 */

public class IndividualHeadPanel extends BasePanel {    
    
    /**
	 * Default serialVersionID.
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * Constructor.
	 */
	public IndividualHeadPanel() {
        super("individuel");
        
        final Map<String, String> configMap = getJtrac().loadAllConfig();

		WebMarkupContainer img = new WebMarkupContainer("icon");
		img.add(AttributeModifier.replace("src", (IModel<String>) () -> {
			String url = configMap.get("jtrac.header.picture");
			String cp = getRequest().getContextPath();
			String base = (cp == null || cp.isEmpty() || "/".equals(cp)) ? "" : cp;
			if (StringUtils.hasText(url)) {
				if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("//")) {
					return url;
				}
				return base + (url.startsWith("/") ? url : "/" + url);
			} else {
				return base + "/resources/jtrac-logo.svg";
			}
		}));
		add(img);

		String message = configMap.get("jtrac.header.text");
		if (! StringUtils.hasText(message))
   		    add(new Label("message", "Lightweight Knowledge Query System"));
		else if ((message != null) && ("no".equals(message)))
   		    add(new Label("message", ""));
		else
			add(new Label("message", message));
    }
}
