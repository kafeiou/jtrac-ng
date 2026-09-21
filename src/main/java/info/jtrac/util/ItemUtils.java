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

package info.jtrac.util;

import info.jtrac.Jtrac;
import info.jtrac.domain.Attachment;
import info.jtrac.domain.Field;
import info.jtrac.domain.History;
import info.jtrac.domain.Item;
import info.jtrac.domain.ItemItem;
import info.jtrac.domain.ItemSearch;
import info.jtrac.domain.Space;
import info.jtrac.domain.User;
import info.jtrac.exception.JtracSecurityException;
import info.jtrac.wicket.JtracApplication;

import java.io.BufferedReader;
import java.io.StringReader;
import java.io.Writer;
import java.util.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.wicket.request.mapper.parameter.PageParameters;

import org.dom4j.Element;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.context.MessageSource;
import org.springframework.web.servlet.support.RequestContextUtils;
import org.springframework.web.util.HtmlUtils;

import org.commonmark.Extension;
import org.commonmark.node.*;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.*;
import org.commonmark.ext.autolink.AutolinkExtension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.ins.InsExtension;

/**
 * Utilities to convert an Item into HTML etc.
 * The getAsHtml() routine is used to diplay an item - within a tag lib for JSP
 * And we are able to re-use this to send HTML e-mail etc.
 */
public final class ItemUtils {    
    
    private static final Logger logger = LoggerFactory.getLogger(ItemUtils.class);

	public static final String URL_PATTERN = "http(s?)://[:\\p{L}\\p{Digit}\\.\\-_\\/=#\\?%]+";

	private static Parser parser;

	private static HtmlRenderer renderer;

	static {
		Set<Extension> extensions = new HashSet<>();
		extensions.add(AutolinkExtension.create());
		extensions.add(StrikethroughExtension.create());
		extensions.add(InsExtension.create());
		extensions.add(TablesExtension.create());

		parser = Parser.builder()
				.extensions(extensions)
				.build();
		renderer = HtmlRenderer.builder()
				.extensions(extensions)
				.attributeProviderFactory(new AttributeProviderFactory() {
					@Override
					public AttributeProvider create (AttributeProviderContext context) {
						return new AttributeProvider() {
							@Override
							public void setAttributes (Node node, String tagName, Map<String, String> attributes) {
								// if (tagName.equals("a")) would probably work as well
								if (node instanceof Link) {
									attributes.put("target", "_blank");
								}
							}
						};
					}
				})
				.escapeHtml(true)
				.sanitizeUrls(true)
				.build();
	}

    /** 
     * does HTML escaping, converts tabs to spaces and converts leading 
     * spaces (for each multi-line) to as many '&nbsp;' sequences as required
     */
    public static String fixWhiteSpace(String text) {
        if(text == null) {
            return "";
        }
        String temp = HtmlUtils.htmlEscape(text);
        BufferedReader reader = new BufferedReader(new StringReader(temp));
        StringBuilder sb = new StringBuilder();
        String s;
        boolean first = true;
        try {
            while((s = reader.readLine()) != null) {                          
                if(first) {
                    first = false;
                } else {
                    sb.append("<br/>");
                }
                if(s.startsWith(" ")) {
                    int i;
                    for(i = 0; i < s.length(); i++) {                    
                        if(s.charAt(i) == ' ') {
                            sb.append("&nbsp;");
                        } else {
                            break;
                        }                        
                    }
                    s = s.substring(i);
                }                
                sb.append(s);
            }
        } catch(Exception e) {
            throw new RuntimeException(e);
        }
        return sb.toString().replaceAll("\t", "&nbsp;&nbsp;&nbsp;&nbsp;");
    }

	private static final java.util.regex.Pattern CODE_BLOCK_OR_SPAN_PATTERN =
			java.util.regex.Pattern.compile("(?s)(```[^\n]*\n.*?```|~~~[^\n]*\n.*?~~~|`[^`\n]+`)");

	private static final java.util.regex.Pattern WINDOWS_PATH_UNIFIED_PATTERN =
			java.util.regex.Pattern.compile("([\"'“「])((?:\\\\\\\\[^\"'”」\r\n]+|[a-zA-Z]:\\\\[^\"'”」\r\n]+))([\"'”」])"
					+ "|(\\\\\\\\[^\\s<>\"'“”「」\r\n]+)"
					+ "|(\\b[a-zA-Z]:\\\\[^\\s<>\"'“”「」\r\n]*)"
					+ "|((?:^|(?<=\\s))[a-zA-Z0-9_.\\-]+\\\\[a-zA-Z0-9_.\\-]+(?:\\\\[^\\s<>\"'“”「」\r\n]+)*)"
					+ "|((?<!\\\\)\\\\\\\\(?!\\\\))");

	private static final java.util.regex.Pattern TRAILING_PUNCTUATION_PATTERN =
			java.util.regex.Pattern.compile("(?<![})\\]])[.,;:!?。，、！？]+$");

	/**
	 * Pre-processes markdown text to protect Windows UNC paths (e.g. \\server\share),
	 * Windows local drive paths (e.g. C:\path), and double backslashes (\\) from
	 * having their backslashes stripped by CommonMark escape rules.
	 *
	 * Content inside code blocks (```...```) and inline code spans (`...`) is left untouched.
	 */
	public static String protectWindowsPaths(String text) {
		if (text == null || text.indexOf('\\') == -1) {
			return text;
		}

		java.util.regex.Matcher codeMatcher = CODE_BLOCK_OR_SPAN_PATTERN.matcher(text);
		StringBuilder sb = new StringBuilder();
		int lastEnd = 0;

		while (codeMatcher.find()) {
			String nonCode = text.substring(lastEnd, codeMatcher.start());
			sb.append(protectWindowsPathsInNonCode(nonCode));
			sb.append(codeMatcher.group());
			lastEnd = codeMatcher.end();
		}
		if (lastEnd < text.length()) {
			String nonCode = text.substring(lastEnd);
			sb.append(protectWindowsPathsInNonCode(nonCode));
		}
		return sb.toString();
	}

	private static String protectWindowsPathsInNonCode(String text) {
		if (text == null || text.indexOf('\\') == -1) {
			return text;
		}

		java.util.regex.Matcher m = WINDOWS_PATH_UNIFIED_PATTERN.matcher(text);
		StringBuffer sb = new StringBuffer();
		while (m.find()) {
			if (m.group(1) != null) {
				// Quoted path: group(1) left quote, group(2) path, group(3) right quote
				String leftQuote = m.group(1);
				String path = m.group(2);
				String rightQuote = m.group(3);
				String escaped = path.replace("\\", "\\\\");
				m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(leftQuote + escaped + rightQuote));
			} else {
				// Unquoted match: group(4) UNC, group(5) Drive, group(6) Relative, or group(7) standalone \\
				String match = m.group();
				java.util.regex.Matcher punctMatcher = TRAILING_PUNCTUATION_PATTERN.matcher(match);
				String pathPart = match;
				String punctPart = "";
				if (punctMatcher.find()) {
					punctPart = punctMatcher.group();
					pathPart = match.substring(0, match.length() - punctPart.length());
				}
				String escaped = pathPart.replace("\\", "\\\\") + punctPart;
				m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(escaped));
			}
		}
		m.appendTail(sb);
		return sb.toString();
	}

	public static String renderMarkdown (String text) {
		if (text == null) {
			return null;
		} else {
			text = protectWindowsPaths(text);
			Node document = parser.parse(text);
			String html = renderer.render(document).trim();
			return html;
		}
	}

	public static String autolinkUrls (String text) {
		if (text == null) {
			return null;
		} else {
			text = fixWhiteSpace(text);
			return text.replaceAll(URL_PATTERN, "<a href='$0' target='_blank'>$0</a>");
		}
	}

	// don't link if there's a slash immediately before the item; ignore case, so item-44 is the same as ITEM-44
	public static String autolinkTickets (String baseUrl, String text, Set<Space> spaces) {
		if (text == null) {
			return null;
		} else {
			if (baseUrl == null)
				baseUrl = "/";
			if (! baseUrl.endsWith("/"))
				baseUrl += "/";

			for (Space space : spaces) {
				text = text.replaceAll("(?i)(?<!/)"+space.getPrefixCode()+"-[0-9]+",
										"<a href='"+baseUrl+"app/item/$0' target='_blank'>$0</a>");
			}
			return text;
		}
	}

    private static String fmt(String key, MessageSource messageSource, Locale locale) {
        try {
            return messageSource.getMessage("item_view." + key, null, locale);
        } catch (Exception e) {
            return "???item_view." + key + "???";
        }
    }

    public static String getAsHtml(Item item, MessageSource messageSource, Locale locale) {
        return getAsHtml(item, null, null, messageSource, locale);
    }

    public static String getAsHtml(Item item, HttpServletRequest request, HttpServletResponse response) {
        Locale locale = RequestContextUtils.getLocale(request);
        MessageSource messageSource = RequestContextUtils.findWebApplicationContext(request);        
        return getAsHtml(item, request, response, messageSource, locale);
    }    
    
    private static String getAsHtml (Item item, HttpServletRequest request, HttpServletResponse response, MessageSource ms, Locale loc) {        
        final String colorLightBlue = "#E1ECFE";
        final String colorGray = "#CCCCCC";

        String tableStyle = " class='jtrac'";
        String tdStyle = "";
        String thStyle = "";
        String altStyle = " class='alt'";
        String labelStyle = " class='label'";

        boolean isWeb = (request != null);

        if (!isWeb) {
            // inline CSS so that HTML mail works across most mail-reader clients
            String tdCommonStyle = "border: 1px solid black";
            tableStyle = " class='jtrac' style='border-collapse: collapse; font-family: Arial; font-size: 75%'";
            tdStyle = " style='" + tdCommonStyle + "'";
            thStyle = " style='" + tdCommonStyle + "; background: "+colorGray+"'";
            altStyle = " style='background: "+colorLightBlue+"'";
            labelStyle = " style='" + tdCommonStyle + "; background: "+colorGray+"; font-weight: bold; text-align: right'";
        }

        StringBuffer sb = new StringBuffer();
        sb.append("<table width='100%'" + tableStyle + ">");
        sb.append("<tr" + altStyle + ">");
        sb.append("  <td" + labelStyle + ">" + fmt("id", ms, loc) + "</td>");
        sb.append("  <td" + tdStyle + ">" + item.getRefId() + "</td>");
        sb.append("  <td" + labelStyle + ">" + fmt("relatedItems", ms, loc) + "</td>");
        sb.append("  <td colspan='3'" + tdStyle + ">");
        if (item.getRelatedItems() != null || item.getRelatingItems() != null) {
            String flowUrlParam = null;
            String flowUrl = null;
            if (isWeb) {
                flowUrlParam = "_flowExecutionKey=" + request.getAttribute("flowExecutionKey");
                flowUrl = "/flow?" + flowUrlParam;
            }
            if (item.getRelatedItems() != null) {
                // ItemViewForm itemViewForm = null;
                if (isWeb) {
                    // itemViewForm = (ItemViewForm) request.getAttribute("itemViewForm");
                    sb.append("<input type='hidden' name='_removeRelated'/>");
                }
                for(ItemItem itemItem : item.getRelatedItems()) {                    
                    String refId = itemItem.getRelatedItem().getRefId();
                    if (isWeb) {
                        String checked = "";
                        Set<Long> set = null; // itemViewForm.getRemoveRelated();
                        if (set != null && set.contains(itemItem.getId())) {
                            checked = " checked='true'";
                        }
                        String url = flowUrl + "&_eventId=viewRelated&itemId=" + itemItem.getRelatedItem().getId();
                        refId = "<a href='" + response.encodeURL(request.getContextPath() + url) + "'>" + refId + "</a>"
                                + "<input type='checkbox' name='removeRelated' value='" 
                                + itemItem.getId() + "' title='" + fmt("remove", ms, loc) + "'" + checked + "/>";
                    }
                    sb.append(fmt(itemItem.getRelationText(), ms, loc) + " " + refId + " ");
                }
            }
            if (item.getRelatingItems() != null) {
                for(ItemItem itemItem : item.getRelatingItems()) {
                    String refId = itemItem.getItem().getRefId();
                    if (isWeb) {
                        String url = flowUrl + "&_eventId=viewRelated&itemId=" + itemItem.getItem().getId();
                        refId = "<a href='" + response.encodeURL(request.getContextPath() + url) + "'>" + refId + "</a>";
                    }
                    sb.append(refId + " " + fmt(itemItem.getRelationText() + "This", ms, loc) + ". ");
                }
            }
        }
        sb.append("  </td>");
        sb.append("</tr>");
        sb.append("<tr>");
        sb.append("  <td width='15%'" + labelStyle + ">" + fmt("status", ms, loc) + "</td>");
        sb.append("  <td" + tdStyle + ">" + item.getStatusValue() + "</td>");
        sb.append("  <td" + labelStyle + ">" + fmt("loggedBy", ms, loc) + "</td>");
        sb.append("  <td" + tdStyle + ">" + item.getLoggedBy().getName() + "</td>");
        sb.append("  <td" + labelStyle + ">" + fmt("assignedTo", ms, loc) + "</td>");
        sb.append("  <td width='15%'" + tdStyle + ">" + (item.getAssignedTo() == null ? "" : item.getAssignedTo().getName()) + "</td>");
        sb.append("</tr>");
        sb.append("<tr" + altStyle + ">");
        sb.append("  <td" + labelStyle + ">" + fmt("summary", ms, loc) + "</td>");
        sb.append("  <td colspan='5'" + tdStyle + ">" + (item.getSummary() == null ? "" : HtmlUtils.htmlEscape(item.getSummary())) + "</td>");
        sb.append("</tr>");
        sb.append("<tr>");
        sb.append("  <td valign='top'" + labelStyle + ">" + fmt("detail", ms, loc) + "</td>");
        sb.append("  <td colspan='5'" + tdStyle + ">" + fixWhiteSpace(item.getDetail()) + "</td>");
        sb.append("</tr>");
        
        int row = 0;
        Map<Field.Name, Field> fields = item.getSpace().getMetadata().getFields();
        for(Field.Name fieldName : item.getSpace().getMetadata().getFieldOrder()) {
            Field field = fields.get(fieldName);
            sb.append("<tr" + (row % 2 == 0 ? altStyle : "") + ">");
            sb.append("  <td" + labelStyle + ">" + field.getLabel() + "</td>");
            sb.append("  <td colspan='5'" + tdStyle + ">" + item.getCustomValue(fieldName) + "</td>");
            sb.append("</tr>");
            row++;
        }
        sb.append("</table>");
        
        //=========================== HISTORY ==================================
        sb.append("<br/>&nbsp;<b" + tableStyle + ">" + fmt("history", ms, loc) + "</b>");
        sb.append("<table width='100%'" + tableStyle + ">");
        sb.append("<tr>");
        sb.append("  <th" + thStyle + ">" + fmt("loggedBy", ms, loc) + "</th><th" + thStyle + ">" + fmt("status", ms, loc) + "</th>"
                + "<th" + thStyle + ">" + fmt("assignedTo", ms, loc) + "</th><th" + thStyle + ">" + fmt("comment", ms, loc) + "</th><th" + thStyle + ">" + fmt("timeStamp", ms, loc) + "</th>");
        List<Field> editable = item.getSpace().getMetadata().getEditableFields();
        for(Field field : editable) {
            sb.append("<th" + thStyle + ">" + field.getLabel() + "</th>");
        }
        sb.append("</tr>");
        
        if (item.getHistory() != null) {
            row = 1;
            for(History history : item.getHistory()) {
                sb.append("<tr valign='top'" + (row % 2 == 0 ? altStyle : "") + ">");
                sb.append("  <td" + tdStyle + ">" + history.getLoggedBy().getName() + "</td>");
                sb.append("  <td" + tdStyle + ">" + history.getStatusValue() +"</td>");
                sb.append("  <td" + tdStyle + ">" + (history.getAssignedTo() == null ? "" : history.getAssignedTo().getName()) + "</td>");
                sb.append("  <td" + tdStyle + ">");
                Attachment attachment = history.getAttachment();
                if (attachment != null) {
                    if (request != null && response != null) {
                        String href = response.encodeURL(request.getContextPath() + "/app/attachments/" + attachment.getFileName() +"?filePrefix=" + attachment.getFilePrefix());
                        sb.append("<a target='_blank' href='" + href + "'>" + attachment.getFileName() + "</a>&nbsp;");
                    } else {
                        sb.append("(attachment:&nbsp;" + attachment.getFileName() + ")&nbsp;");
                    }
                }
                sb.append(fixWhiteSpace(history.getComment()));
                sb.append("  </td>");
                sb.append("  <td" + tdStyle + ">" + history.getTimeStamp() + "</td>");
                for(Field field : editable) {
                    sb.append("<td" + tdStyle + ">" + history.getCustomValue(field.getName()) + "</td>");
                }
                sb.append("</tr>");
                row++;
            }
        }
        sb.append("</table>");
        return sb.toString();
    }
    
    public static Element getAsXml(Item item) {
        // root
        Element root = XmlUtils.getNewElement("item");        
        root.addAttribute("refId", item.getRefId());
        // related items
        if (item.getRelatedItems() != null && item.getRelatedItems().size() > 0) {
            Element relatedItems = root.addElement("relatedItems");
            for(ItemItem itemItem : item.getRelatedItems()) {
                Element relatedItem = relatedItems.addElement("relatedItem");
                relatedItem.addAttribute("refId", itemItem.getItem().getRefId());
                relatedItem.addAttribute("linkType", itemItem.getRelationText());
            }           
        }
        // relating items
        if (item.getRelatingItems() != null && item.getRelatingItems().size() > 0) {
            Element relatingItems = root.addElement("relatingItems");
            for(ItemItem itemItem : item.getRelatingItems()) {
                Element relatingItem = relatingItems.addElement("relatingItem");
                relatingItem.addAttribute("refId", itemItem.getItem().getRefId());
                relatingItem.addAttribute("linkType", itemItem.getRelationText());
            }
        }
        // summary
        if (item.getSummary() != null) {
            root.addElement("summary").addText(item.getSummary());
        }
        // detail
        if (item.getDetail() != null) {
            root.addElement("detail").addText(item.getDetail());
        }
        // logged by
        Element loggedBy = root.addElement("loggedBy");
        // loggedBy.addAttribute("userId", item.getLoggedBy().getId() + "");
        loggedBy.addText(item.getLoggedBy().getName());
        // assigned to
        if (item.getAssignedTo() != null) {
            Element assignedTo = root.addElement("assignedTo");
            // assignedTo.addAttribute("userId", item.getAssignedTo().getId() + "");
            assignedTo.addText(item.getAssignedTo().getName());
        }
        // status
        Element status = root.addElement("status");
        status.addAttribute("statusId", item.getStatus() + "");
        status.addText(item.getStatusValue());
        // custom fields
        Map<Field.Name, Field> fields = item.getSpace().getMetadata().getFields();
        for(Field.Name fieldName : item.getSpace().getMetadata().getFieldOrder()) {
            Object value = item.getValue(fieldName);
            if(value != null) {
                Field field = fields.get(fieldName);
                Element customField = root.addElement(fieldName.getText());
                customField.addAttribute("label", field.getLabel());
                if(field.isDropDownType()) {
                    customField.addAttribute("optionId", value + "");
                }
                customField.addText(item.getCustomValue(fieldName)); 
            }
        }
        // timestamp
        Element timestamp = root.addElement("timestamp");
        timestamp.addText(DateUtils.formatTimeStamp(item.getTimeStamp()));        
        // history
        if (item.getHistory() != null) {  
            Element historyRoot = root.addElement("history");
            for(History history : item.getHistory()) {   
                Element event = historyRoot.addElement("event");
                // index
                event.addAttribute("eventId", (history.getIndex() + 1) + "");
                // logged by
                Element historyLoggedBy = event.addElement("loggedBy");
                // historyLoggedBy.addAttribute("userId", history.getLoggedBy().getId() + "");
                historyLoggedBy.addText(history.getLoggedBy().getName());
                // status
                if(history.getStatus() != null) {
                    Element historyStatus = event.addElement("status");
                    historyStatus.addAttribute("statusId", history.getStatus() + "");
                    historyStatus.addText(history.getStatusValue());
                }
                // assigned to
                if(history.getAssignedTo() != null) {
                    Element historyAssignedTo = event.addElement("assignedTo");
                    // historyAssignedTo.addAttribute("userId", history.getAssignedTo().getId() + "");
                    historyAssignedTo.addText(history.getAssignedTo().getName());
                }
                // attachment
                if(history.getAttachment() != null) {
                    Element historyAttachment = event.addElement("attachment");
                    historyAttachment.addAttribute("attachmentId", history.getAttachment().getId() + "");
                    historyAttachment.addText(history.getAttachment().getFileName());
                }
                // comment
                if(history.getComment() != null) {
                    Element historyComment = event.addElement("comment");
                    historyComment.addText(history.getComment());
                }
                // timestamp
                Element historyTimestamp = event.addElement("timestamp");
                historyTimestamp.addText(DateUtils.formatTimeStamp(history.getTimeStamp()));
                // custom fields
                List<Field> editable = item.getSpace().getMetadata().getEditableFields();
                for(Field field : editable) {     
                    Object value = history.getValue(field.getName());
                    if(value != null) {
                        Element historyCustomField = event.addElement(field.getName().getText());
                        historyCustomField.addAttribute("label", field.getLabel());
                        if(field.isDropDownType()) {
                            historyCustomField.addAttribute("optionId", value + "");
                        }                        
                        historyCustomField.addText(history.getCustomValue(field.getName()));
                    }
                }                
            }   
        }        
        return root;
    }        
    
    public static ItemSearch getItemSearch(User user, PageParameters params, Jtrac jtrac) throws JtracSecurityException {
        long spaceId = params.get("s").toLong(-1L);        
        ItemSearch itemSearch = null;
        if(spaceId > 0) {            
            Space space = jtrac.loadSpace(spaceId);
            if(space == null) {
                throw new JtracSecurityException("Space not found: " + spaceId + " in URL: " + params);
            }
            if(user == null || !user.isAllocatedToSpace(space.getId())) {
                throw new JtracSecurityException("User not allocated to space: " + spaceId + " in URL: " + params);
            }
            itemSearch = new ItemSearch(space);            
        } else {
            itemSearch = new ItemSearch(user);
        }
        itemSearch.initFromPageParameters(params, user, jtrac);
        return itemSearch;        
    }
    
}
