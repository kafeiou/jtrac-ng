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

package info.jtrac.mail;

import info.jtrac.Jtrac;
import info.jtrac.domain.Item;
import info.jtrac.domain.ItemUser;
import info.jtrac.domain.Space;
import info.jtrac.domain.User;
import info.jtrac.util.ItemUtils;
import info.jtrac.wicket.JtracApplication;
import org.apache.wicket.Application;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

import javax.mail.Header;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;

import net.markenwerk.utils.mail.dkim.DkimMessage;
import net.markenwerk.utils.mail.dkim.DkimSigner;

import org.springframework.context.MessageSource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jndi.JndiObjectFactoryBean;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.util.StringUtils;

import info.jtrac.domain.AbstractItem;
import info.jtrac.domain.Attachment;
import info.jtrac.domain.History;
import info.jtrac.util.SensitiveDataMasker;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class to handle sending of E-mail and pre-formatted messages
 */
public class MailSender {

	private static final Logger logger = LoggerFactory.getLogger(MailSender.class);

	private JavaMailSenderImpl sender;
	private String prefix;
	private String from;
	private String url;
	private MessageSource messageSource;
	private Locale defaultLocale;

	// for DKIM
	private String signingDomain;
	private String selector;
	private String derFile;
	private String identity;

	private String jtracHome;

	public String getJtracHome() {
		return jtracHome;
	}

	public void setJtracHome(String jtracHome) {
		this.jtracHome = jtracHome;
	}

	public MailSender(Map<String, String> config, MessageSource messageSource, String defaultLocale) {
		this(config, messageSource, defaultLocale, null);
	}

	public MailSender(Map<String, String> config, MessageSource messageSource, String defaultLocale, String jtracHome) {
		this.jtracHome = jtracHome;
		// initialize email sender
		this.messageSource = messageSource;
		this.defaultLocale = StringUtils.parseLocaleString(defaultLocale);
		String mailSessionJndiName = config.get("mail.session.jndiname");
		if (StringUtils.hasText(mailSessionJndiName)) {
			initMailSenderFromJndi(mailSessionJndiName);
		}
		if (sender == null) {
			initMailSenderFromConfig(config);
		}
		// if sender is still null the send* methods will not
		// do anything when called and will just return immediately
		String tempUrl = config.get("jtrac.url.base");
		if (tempUrl == null) {
			tempUrl = "http://localhost/jtrac/";
		}
		if (!tempUrl.endsWith("/")) {
			tempUrl = tempUrl + "/";
		}
		this.url = tempUrl;
		logger.info("email hyperlink base url set to '" + this.url + "'");
	}

	/**
	 * we bend the rules a little and fire off a new thread for sending
	 * an email message.  This has the advantage of not slowing down the item
	 * create and update screens, i.e. the system returns the next screen
	 * after "submit" without blocking. This has been used in production
	 * (and now I guess in many JTrac installations worldwide)
	 * for quite a while now, on Tomcat without any problems. This helps a lot
	 * especially when the SMTP server is slow to respond, etc.
	 */
	private void sendInNewThread(final MimeMessage message) {
		new Thread() {
			@Override
			public void run() {
				logger.debug("send mail thread start");
				try {
					try {
						MimeMessage signedMessage = message;

						// no point in trying DKIM if the relevant properties have not been set
						if (StringUtils.hasText(signingDomain) && StringUtils.hasText(derFile)) {
							try {
								DkimSigner dkimSigner = new DkimSigner(signingDomain, selector, new File(derFile));
								dkimSigner.setIdentity(identity);
								/*
								dkimSigner.setHeaderCanonicalization(Canonicalization.SIMPLE);
								dkimSigner.setBodyCanonicalization(Canonicalization.RELAXED);
								dkimSigner.setSigningAlgorithm(SigningAlgorithm.SHA256_WITH_RSA);
								dkimSigner.setLengthParam(true);
								dkimSigner.setCopyHeaderFields(false);
								*/
								signedMessage = new DkimMessage(signedMessage, dkimSigner);
							} catch (Exception ex) {
								logger.warn("Can't use DKIM: "+ex.getMessage());
							}
						}

						sender.send(signedMessage);
						logger.info(String.format("sent mail '%s' to %s", message.getSubject(), message.getRecipients(MimeMessage.RecipientType.TO)[0]));
					} catch (Exception e) {
						logger.error("send mail thread failed", e);
						logger.error("mail headers dump start");
						Enumeration headers = message.getAllHeaders();
						while (headers.hasMoreElements()) {
							Header h = (Header) headers.nextElement();
							logger.info(h.getName() + ": " + h.getValue());
						}
						logger.error("mail headers dump end");
					}
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		}.start();
	}

	private String fmt(String key, Locale locale, String... parameters) {
		try {
			return messageSource.getMessage("mail_sender." + key, parameters, locale);
		} catch (Exception e) {
			logger.debug(e.getMessage());
			return "???mail_sender." + key + "???";
		}
	}

	private String addHeaderAndFooter(StringBuffer html) {
		StringBuffer sb = new StringBuffer();
		// additional cosmetic tweaking of e-mail layout
		// style just after the body tag does not work for a minority of clients // like gmail, thunderbird etc.
		// ItemUtils adds the main inline CSS when generating the email content, // so we gracefully degrade
		sb.append("<html><body><style type='text/css'>table.jtrac th, table.jtrac td { padding-left: 0.2em; padding-right: 0.2em; }</style>");
		sb.append(html);
		sb.append("</html>");
		return sb.toString();
	}

	private String getItemViewAnchor(Item item, Locale locale) {
		String itemUrl = url + "app/item/" + item.getRefId();
		return "<p style='font-family: Arial; font-size: 75%'><a href='" + itemUrl + "'>" + itemUrl + "</a></p>";
	}

	private String getSubject(Item item) {
		String summary = null;
		if (item.getSummary() == null) {
			summary = "";
		} else if (item.getSummary().length() > 80) {
			summary = item.getSummary().substring(0, 80);
		} else {
			summary = item.getSummary();
		}
		return prefix + " #" + item.getRefId() + " " + summary;
	}

	public void send(Item item) {
		if (sender == null) {
			logger.debug("mail sender is null, not sending notifications");
			return;
		}
		// TODO make this locale sensitive per recipient
		logger.debug("attempting to send mail for item update");
		// prepare message content
		StringBuffer sb = new StringBuffer();
		String anchor = getItemViewAnchor(item, defaultLocale);
		sb.append(anchor);
		sb.append(ItemUtils.getAsHtml(item, messageSource, defaultLocale));
		sb.append(anchor);
		if (logger.isDebugEnabled()) {
			logger.debug("html content: " + sb);
		}
		// prepare message
		MimeMessage message = sender.createMimeMessage();
		MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");

		// Remember the TO person email to prevent duplicate mails
		String toPersonEmail;
		try {
			helper.setText(addHeaderAndFooter(sb), true);
			helper.setSubject(getSubject(item));
			helper.setSentDate(new Date());
			helper.setFrom(from);
			// set TO
			if (item.getAssignedTo() != null) {
				helper.setTo(item.getAssignedTo().getEmail());
				toPersonEmail = item.getAssignedTo().getEmail();
			} else {
				helper.setTo(item.getLoggedBy().getEmail());
				toPersonEmail = item.getLoggedBy().getEmail();
			}
			// set CC
			List<String> cclist = new ArrayList<String>();
			if (item.getItemUsers() != null) {
				for (ItemUser itemUser : item.getItemUsers()) {
					// Send only, if person is not the TO assignee AND user is not locked
					if (!toPersonEmail.equals(itemUser.getUser().getEmail())
							&& ! itemUser.getUser().isLocked()) {
						cclist.add(itemUser.getUser().getEmail());
					}
				}

				// sounds complicated but we have to ensure that no null
				// item will be set in setCC(). So we collect the cc items
				// in the cclist and transform it to an stringarray.
				if (cclist.size() > 0) {
					String[] cc = cclist.toArray(new String[0]); 
					helper.setCc(cc);
				}
			}
			// send message
			// workaround: Some PSEUDO user has no email address. Because email address
			// is mandatory, you can enter "no" in email address and the mail will not be sent.
			if (!"no".equals(toPersonEmail))
				sendInNewThread(message);
		} catch (Exception e) {
			logger.error("failed to prepare e-mail", e);
		}
	}

	public void sendUserPassword(User user, String clearText) {
		if (sender == null) {
			logger.debug("mail sender is null, not sending new user / password change notification");
			return;
		}
		logger.debug("attempting to send mail for user password");
		String localeString = user.getLocale();
		Locale locale = null;
		if (localeString == null) {
			locale = defaultLocale;
		} else {
			locale = StringUtils.parseLocaleString(localeString);
		}
		MimeMessage message = sender.createMimeMessage();
		MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
		final String colorGray = "#CCCCCC";
		try {
			helper.setTo(user.getEmail());
			helper.setSubject(prefix + " " + fmt("loginMailSubject", locale));
			StringBuffer sb = new StringBuffer();
			sb.append("<p>" + fmt("loginMailGreeting", locale) + " " + user.getName() + ",</p>");
			sb.append("<p>" + fmt("loginMailLine1", locale) + "</p>");
			sb.append("<table class='jtrac'>");
			sb.append("<tr><th style='background: "+colorGray+"'>"
					+ fmt("loginName", locale)
					+ "</th><td style='border: 1px solid black'>"
					+ user.getLoginName() + "</td></tr>");
			sb.append("<tr><th style='background: "+colorGray+"'>"
					+ fmt("password", locale)
					+ "</th><td style='border: 1px solid black'>" + clearText
					+ "</td></tr>");
			sb.append("</table>");
			sb.append("<p>" + fmt("loginMailLine2", locale) + "</p>");
			sb.append("<p><a href='" + url + "'>" + url + "</a></p>");
			helper.setText(addHeaderAndFooter(sb), true);
			helper.setSentDate(new Date());
			// helper.setCc(from);
			helper.setFrom(from);
			sendInNewThread(message);
		} catch (Exception e) {
			logger.error("failed to prepare e-mail", e);
		}
	}

	private void initMailSenderFromJndi(String mailSessionJndiName) {
		logger.info("attempting to initialize mail sender from jndi name = '" + mailSessionJndiName + "'");
		JndiObjectFactoryBean factoryBean = new JndiObjectFactoryBean();
		factoryBean.setJndiName(mailSessionJndiName);
		// "java:comp/env/" will be prefixed if the JNDI name doesn't already have it
		factoryBean.setResourceRef(true);
		try {
			// this step actually does the JNDI lookup
			factoryBean.afterPropertiesSet();
		} catch (Exception e) {
			logger.warn("failed to locate mail session : " + e);
			return;
		}
		Session session = (Session) factoryBean.getObject();
		sender = new JavaMailSenderImpl();
		sender.setSession(session);
		logger.info("email sender initialized from jndi name = '" + mailSessionJndiName + "'");
	}

	private void initMailSenderFromConfig(Map<String, String> config) {
		String host = config.get("mail.server.host");
		if (host == null) {
			logger.warn("'mail.server.host' config is null, mail sender not initialized");
			return;
		}
		String port = config.get("mail.server.port");
		String localhost = config.get("mail.smtp.localhost");
		from = config.get("mail.from");
		prefix = config.get("mail.subject.prefix");
		String userName = config.get("mail.server.username");
		String password = config.get("mail.server.password");
		String startTls = config.get("mail.server.starttls.enable");
		String sslEnable = config.get("mail.server.ssl.enable");
		logger.info("initializing email adapter: host = '" + host
				+ "', port = '" + port + "', from = '" + from + "', prefix = '" + prefix + "'");
		this.prefix = prefix == null ? "[jtrac]" : prefix;
		this.from = from == null ? "jtrac" : from;
		int p = 25;
		if (port != null) {
			try {
				p = Integer.parseInt(port);
			} catch (NumberFormatException e) {
				logger.warn("mail.server.port not an integer : '" + port + "', defaulting to 25");
			}
		}
		sender = new JavaMailSenderImpl();
		sender.setHost(host);
		sender.setPort(p);
		Properties props = null;

		if (userName != null) {
			// authentication requested
			props = new Properties();
			props.put("mail.smtp.auth", "true");
			props.put("mail.smtp.starttls.enable", 
				(startTls != null && startTls.toLowerCase().equals("true")) ? "true" : "false");
			props.put("mail.smtp.ssl.enable", 
				(sslEnable != null && sslEnable.toLowerCase().equals("true")) ? "true" : "false");
			sender.setUsername(userName);
			sender.setPassword(password);
		}

		if (localhost != null) {
			if (props == null) {
				props = new Properties();
			}
			props.put("mail.smtp.localhost", localhost);
		}

		if (props != null) {
			sender.setJavaMailProperties(props);
		}

		signingDomain = config.get("mail.dkim.signingDomain");
		selector = config.get("mail.dkim.selector");
		if (! StringUtils.hasText(selector))
			selector = "default";
		derFile = config.get("mail.dkim.derFile");
		identity = config.get("mail.dkim.identity");
		if (! StringUtils.hasText(identity))
			identity = from;

		logger.info("email sender initialized from config: host = '" + host + "', port = '" + p + "'");
	}

	public void sendAiQueryResponse(String toEmail, String originalSubject, String aiContent, List<Item> referencedItems, Locale locale, Set<Space> spaces) {
		sendAiQueryResponse(toEmail, originalSubject, aiContent, referencedItems, null, locale, spaces);
	}

	public void sendAiQueryResponse(String toEmail, String originalSubject, String aiContent, List<Item> referencedItems, Map<String, String> perTicketSummaries, Locale locale, Set<Space> spaces) {
		if (sender == null) {
			logger.debug("mail sender is null, not sending AI query response");
			return;
		}
		if (toEmail == null || toEmail.trim().isEmpty() || "no".equalsIgnoreCase(toEmail.trim())) {
			logger.warn("Invalid recipient email for AI query response: " + toEmail);
			return;
		}
		if (locale == null) {
			locale = defaultLocale;
		}

		logger.debug("Preparing AI query response email to " + toEmail);
		try {
			MimeMessage message = sender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
			helper.setTo(toEmail);
			helper.setFrom(from);
			Date now = new Date();
			helper.setSentDate(now);

			String prefix = messageSource.getMessage("mail.ai_query.subject_prefix", null, "Re: ", locale);
			String cleanSub = (originalSubject != null) ? originalSubject.trim() : "";
			String subject = cleanSub.toLowerCase().startsWith("re:") ? cleanSub : prefix + cleanSub;
			helper.setSubject(subject);

			// 1. Generate standalone offline HTML report & save to server disk with 14-day retention
			String token = UUID.randomUUID().toString();
			SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd-HHmm");
			String reportFilename = "JTrac-AI-Report-" + sdf.format(now) + ".html";
			String standaloneHtml = buildStandaloneHtmlReport(cleanSub, aiContent, referencedItems, perTicketSummaries, locale, spaces, now, token);
			saveReportToDisk(token, standaloneHtml);
			String reportUrl = url + "flow/report?token=" + token;

			// 2. Build streamlined email body (Notice + Link Button + Ticket list only, completely preventing client formatting breakage)
			StringBuilder sb = new StringBuilder();
			sb.append("<div style='font-family: -apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, Helvetica, Arial, sans-serif; max-width: 760px; margin: 0 auto; padding: 24px; color: #24292e; line-height: 1.6;'>");

			sb.append("<div style='border-bottom: 2px solid #0969da; padding-bottom: 12px; margin-bottom: 20px;'>");
			sb.append("<h2 style='margin: 0; color: #0969da; font-size: 20px;'>\uD83E\uDD16 JTrac AI \u67e5\u8a62\u79d8\u66f8\u56de\u8986 / AI Query Copilot Response</h2>");
			sb.append("</div>");

			sb.append("<div style='font-size: 14px; color: #57606a; margin-bottom: 18px;'>");
			sb.append("<strong>\u67e5\u8a62\u4e3b\u65e8 / Query:</strong> ").append(escapeHtml(cleanSub));
			sb.append("</div>");

			// Web Link & 14-Day Retention Action Callout
			sb.append("<div style='background-color: #f0f7ff; border: 1px solid #cce3ff; border-left: 4px solid #0969da; border-radius: 8px; padding: 20px 24px; margin-bottom: 24px; text-align: center;'>");
			sb.append("<div style='font-weight: 600; color: #0969da; font-size: 16px; margin-bottom: 8px;'>");
			sb.append("\uD83D\uDCCA AI \u6df1\u5ea6\u8a3a\u65b7\u5831\u544a\uff08\u542b Mermaid \u6d41\u7a0b\u5716\uff09\u5df2\u751f\u6210");
			sb.append("</div>");
			sb.append("<div style='font-size: 13px; color: #57606a; margin-bottom: 16px; line-height: 1.6;'>");
			sb.append("\u5305\u542b\u9ad8\u968e\u7e3d\u7d50\u5206\u6790\u3001\u554f\u984c\u6839\u56e0\u8a3a\u65b7\u3001\u5efa\u8b70\u884c\u52d5\u65b9\u6848\u8207\u5de5\u55ae\u6b77\u7a0b\u7d00\u9304\u3002\u672c\u7dda\u4e0a\u5831\u544a\u4fdd\u7559 <strong>14 \u5929</strong>\u3002<br/>");
			sb.append("(Full analysis with offline Mermaid flowcharts is ready. This online link is valid for <strong>14 days</strong>.)");
			sb.append("</div>");
			sb.append("<div style='margin-bottom: 14px;'>");
			sb.append("<a href='").append(reportUrl).append("' style='background-color: #0969da; color: #ffffff !important; padding: 12px 28px; text-decoration: none; border-radius: 6px; font-size: 15px; font-weight: 600; display: inline-block; box-shadow: 0 2px 4px rgba(9,105,218,0.2);'>");
			sb.append("\uD83D\uDCCA \u9ede\u6b64\u958b\u555f\u5b8c\u6574\u5831\u544a / Open Full Report");
			sb.append("</a>");
			sb.append("</div>");
			sb.append("<div style='font-size: 12px; color: #656d76;'>");
			sb.append("\uD83D\uDCE5 \u7db2\u9801\u9802\u90e8\u5177\u5099\u300c\u4e0b\u8f09\u96e2\u7dda HTML \u5831\u544a\u300d\u6309\u9215\uff0c\u53ef\u96a8\u6642\u4e0b\u8f09\u65bc\u672c\u6a5f\u6c38\u4e45\u7559\u5b58\u3002<br/>");
			sb.append("(You can download the self-contained offline HTML report directly from the webpage for permanent keeping.)");
			sb.append("</div></div>");

			// Referenced Tickets List (Grouped by Space and ID DESC)
			if (referencedItems != null && !referencedItems.isEmpty()) {
				Map<Space, List<Item>> grouped = groupItemsBySpace(referencedItems);
				sb.append("<div style='margin-top: 24px;'>");
				sb.append("<h3 style='font-size: 15px; color: #24292e; margin-bottom: 12px;'>");
				sb.append("\uD83D\uDCCB \u95dc\u806f\u5de5\u55ae\u901f\u89bd / Referenced Tickets (").append(referencedItems.size()).append(")");
				sb.append("</h3>");

				for (Map.Entry<Space, List<Item>> entry : grouped.entrySet()) {
					Space sp = entry.getKey();
					List<Item> spItems = entry.getValue();
					String spName = sp.getName() != null ? sp.getName() : sp.getPrefixCode();
					String spPrefix = sp.getPrefixCode() != null ? sp.getPrefixCode() : "";

					sb.append("<div style='margin-bottom: 20px;'>");
					sb.append("<div style='font-size: 13px; font-weight: 600; color: #0969da; margin-bottom: 8px; padding: 6px 12px; background-color: #f0f7ff; border-left: 3px solid #0969da; border-radius: 4px;'>");
					sb.append("\uD83D\uDCC1 \u5c08\u6848\u7a7a\u9593 (Space): ").append(escapeHtml(spName));
					if (!spPrefix.isEmpty() && !spPrefix.equalsIgnoreCase(spName)) {
						sb.append(" (").append(escapeHtml(spPrefix)).append(")");
					}
					sb.append(" \u2014 \u5171 ").append(spItems.size()).append(" \u5f35\u5de5\u55ae (Tickets)");
					sb.append("</div>");

					sb.append("<table style='width: 100%; border-collapse: collapse; font-size: 13px; text-align: left; margin-bottom: 12px;'>");
					sb.append("<thead><tr style='background-color: #f6f8fa;'>");
					sb.append("<th style='padding: 8px 12px; border: 1px solid #d0d7de; width: 120px;'>ID</th>");
					sb.append("<th style='padding: 8px 12px; border: 1px solid #d0d7de;'>Summary</th>");
					sb.append("<th style='padding: 8px 12px; border: 1px solid #d0d7de; width: 90px;'>Status</th>");
					sb.append("<th style='padding: 8px 12px; border: 1px solid #d0d7de; width: 110px;'>Logged By</th>");
					sb.append("</tr></thead><tbody>");

					for (Item item : spItems) {
						String itemUrl = url + "app/item/" + item.getRefId();
						sb.append("<tr>");
						sb.append("<td style='padding: 8px 12px; border: 1px solid #d0d7de; font-weight: bold;'>");
						sb.append("<a href='").append(itemUrl).append("' style='color: #0969da; text-decoration: none;'>").append(escapeHtml(item.getRefId())).append("</a>");
						sb.append("</td>");
						sb.append("<td style='padding: 8px 12px; border: 1px solid #d0d7de;'>").append(escapeHtml(item.getSummary())).append("</td>");
						sb.append("<td style='padding: 8px 12px; border: 1px solid #d0d7de;'><span style='display: inline-block; padding: 2px 6px; font-size: 11px; font-weight: 500; border-radius: 10px; background-color: #ddf4ff; color: #0969da; border: 1px solid #54aeff66;'>").append(escapeHtml(safeGetStatus(item))).append("</span></td>");
						sb.append("<td style='padding: 8px 12px; border: 1px solid #d0d7de;'>").append(item.getLoggedBy() != null ? escapeHtml(item.getLoggedBy().getName()) : "").append("</td>");
						sb.append("</tr>");
					}
					sb.append("</tbody></table></div>");
				}
				sb.append("</div>");
			} else {
				sb.append("<p style='font-size: 14px; color: #666;'>\u67e5\u7121\u7b26\u5408\u60a8\u6388\u6b0a\u5c08\u6848\u7a7a\u9593\u5167\u7684\u76f8\u95dc\u5de5\u55ae\u3002 / No matching tickets found within your authorized spaces.</p>");
			}

			sb.append("<div style='margin-top: 36px; padding-top: 14px; border-top: 1px solid #eaecef; font-size: 12px; color: #8c959f; text-align: center;'>");
			sb.append("\u672c\u90f5\u4ef6\u7531 JTrac \u90f5\u4ef6 AI \u67e5\u8a62\u79d8\u66f8\u81ea\u52d5\u7522\u751f\u8207\u56de\u8986\u3002<br>");
			sb.append("<a href='").append(url).append("' style='color: #0969da; text-decoration: none;'>").append(url).append("</a>");
			sb.append("</div>");
			sb.append("</div>");

			helper.setText(addHeaderAndFooter(new StringBuffer(sb.toString())), true);
			sendInNewThread(message);
		} catch (Exception e) {
			logger.error("Failed to prepare and send AI query response e-mail", e);
		}
	}

	private void saveReportToDisk(String token, String htmlContent) {
		try {
			String home = (jtracHome != null && !jtracHome.trim().isEmpty()) ? jtracHome : (System.getProperty("user.home") + "/.jtrac");
			File reportsDir = new File(home, "reports");
			if (!reportsDir.exists()) {
				reportsDir.mkdirs();
			}
			File reportFile = new File(reportsDir, token + ".html");
			java.nio.file.Files.write(reportFile.toPath(), htmlContent.getBytes(StandardCharsets.UTF_8));
			logger.info("Saved AI diagnostic report to " + reportFile.getAbsolutePath() + " (size: " + reportFile.length() + " bytes)");
		} catch (Exception e) {
			logger.error("Failed to save AI report to disk for token " + token, e);
		}
	}

	public String buildStandaloneHtmlReport(String originalSubject, String aiContent, List<Item> referencedItems,
										    Map<String, String> perTicketSummaries, Locale locale, Set<Space> spaces, Date generatedDate) {
		return buildStandaloneHtmlReport(originalSubject, aiContent, referencedItems, perTicketSummaries, locale, spaces, generatedDate, null);
	}

	public String buildStandaloneHtmlReport(String originalSubject, String aiContent, List<Item> referencedItems,
										    Map<String, String> perTicketSummaries, Locale locale, Set<Space> spaces, Date generatedDate, String token) {
		SimpleDateFormat sdfFull = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		String genDateStr = sdfFull.format(generatedDate != null ? generatedDate : new Date());
		int ticketCount = (referencedItems != null) ? referencedItems.size() : 0;

		// Render AI content from Markdown to HTML (with confidential credentials masked)
		String renderedAi = ItemUtils.renderMarkdown(SensitiveDataMasker.maskSecrets(aiContent));
		if (spaces != null && !spaces.isEmpty() && renderedAi != null) {
			renderedAi = ItemUtils.autolinkTickets(url, renderedAi, spaces);
		}

		StringBuilder html = new StringBuilder(16384);
		html.append("<!DOCTYPE html>\n");
		html.append("<html lang='").append(locale != null && locale.getLanguage().startsWith("zh") ? "zh-TW" : "en").append("'>\n");
		html.append("<head>\n");
		html.append("<meta charset='UTF-8'>\n");
		html.append("<meta name='viewport' content='width=device-width, initial-scale=1.0'>\n");
		html.append("<title>JTrac AI Report - ").append(escapeHtml(originalSubject)).append("</title>\n");
		html.append("<style>\n");
		html.append(":root {\n");
		html.append("  --bg-color: #f6f8fa;\n");
		html.append("  --card-bg: #ffffff;\n");
		html.append("  --text-color: #1f2328;\n");
		html.append("  --text-muted: #656d76;\n");
		html.append("  --border-color: #d0d7de;\n");
		html.append("  --border-light: #eaeef2;\n");
		html.append("  --primary-color: #0969da;\n");
		html.append("  --primary-hover: #0550ae;\n");
		html.append("  --badge-bg: #ddf4ff;\n");
		html.append("  --badge-border: #54aeff66;\n");
		html.append("  --table-header-bg: #f6f8fa;\n");
		html.append("  --table-stripe-bg: #fcfcfd;\n");
		html.append("  --code-bg: #f6f8fa;\n");
		html.append("  --details-bg: #fbfcfd;\n");
		html.append("}\n");
		html.append("@media (prefers-color-scheme: dark) {\n");
		html.append("  :root {\n");
		html.append("    --bg-color: #0d1117;\n");
		html.append("    --card-bg: #161b22;\n");
		html.append("    --text-color: #e6edf3;\n");
		html.append("    --text-muted: #8b949e;\n");
		html.append("    --border-color: #30363d;\n");
		html.append("    --border-light: #21262d;\n");
		html.append("    --primary-color: #4493f8;\n");
		html.append("    --primary-hover: #79c0ff;\n");
		html.append("    --badge-bg: #1f3552;\n");
		html.append("    --badge-border: #388bfd4d;\n");
		html.append("    --table-header-bg: #21262d;\n");
		html.append("    --table-stripe-bg: #161b22;\n");
		html.append("    --code-bg: #1f242c;\n");
		html.append("    --details-bg: #1b2028;\n");
		html.append("  }\n");
		html.append("}\n");
		html.append("* { box-sizing: border-box; }\n");
		html.append("body {\n");
		html.append("  margin: 0; padding: 32px 16px;\n");
		html.append("  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;\n");
		html.append("  background-color: var(--bg-color);\n");
		html.append("  color: var(--text-color);\n");
		html.append("  line-height: 1.6;\n");
		html.append("  font-size: 14px;\n");
		html.append("}\n");
		html.append(".container {\n");
		html.append("  max-width: 980px; margin: 0 auto;\n");
		html.append("  background-color: var(--card-bg);\n");
		html.append("  border: 1px solid var(--border-color);\n");
		html.append("  border-radius: 10px;\n");
		html.append("  padding: 36px 40px;\n");
		html.append("  box-shadow: 0 4px 18px rgba(0, 0, 0, 0.04);\n");
		html.append("}\n");
		html.append(".header {\n");
		html.append("  border-bottom: 2px solid var(--border-color);\n");
		html.append("  padding-bottom: 20px; margin-bottom: 28px;\n");
		html.append("}\n");
		html.append(".header-badge {\n");
		html.append("  display: inline-block; font-size: 12px; font-weight: 600;\n");
		html.append("  color: var(--primary-color); background-color: var(--badge-bg);\n");
		html.append("  border: 1px solid var(--badge-border); border-radius: 20px;\n");
		html.append("  padding: 3px 12px; margin-bottom: 12px;\n");
		html.append("}\n");
		html.append(".header h1 { margin: 0 0 12px 0; font-size: 22px; color: var(--text-color); }\n");
		html.append(".header-meta { display: flex; flex-wrap: wrap; gap: 20px; font-size: 13px; color: var(--text-muted); }\n");
		html.append(".section { margin-bottom: 36px; }\n");
		html.append(".section-title {\n");
		html.append("  font-size: 17px; font-weight: 600; color: var(--text-color);\n");
		html.append("  border-bottom: 1px solid var(--border-light); padding-bottom: 8px; margin-bottom: 16px;\n");
		html.append("}\n");
		html.append("table { width: 100%; border-collapse: collapse; margin: 16px 0; font-size: 13px; }\n");
		html.append("table, th, td { border: 1px solid var(--border-color); }\n");
		html.append("th {\n");
		html.append("  background-color: var(--table-header-bg); font-weight: 600;\n");
		html.append("  color: var(--text-color); padding: 10px 14px; text-align: left;\n");
		html.append("}\n");
		html.append("td { padding: 10px 14px; color: var(--text-color); vertical-align: top; }\n");
		html.append("tbody tr:nth-child(even) { background-color: var(--table-stripe-bg); }\n");
		html.append("tbody tr:hover { background-color: var(--badge-bg); }\n");
		html.append("a { color: var(--primary-color); text-decoration: none; }\n");
		html.append("a:hover { text-decoration: underline; }\n");
		html.append(".status-pill {\n");
		html.append("  display: inline-block; padding: 2px 8px; font-size: 12px; font-weight: 500;\n");
		html.append("  border-radius: 12px; background-color: var(--badge-bg);\n");
		html.append("  color: var(--primary-color); border: 1px solid var(--badge-border);\n");
		html.append("}\n");
		html.append(".meta-grid {\n");
		html.append("  display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));\n");
		html.append("  gap: 10px; padding: 12px 16px; background-color: var(--table-header-bg);\n");
		html.append("  border-radius: 6px; margin-bottom: 16px; font-size: 13px;\n");
		html.append("}\n");
		html.append("details {\n");
		html.append("  border: 1px solid var(--border-color); border-radius: 6px;\n");
		html.append("  margin-bottom: 14px; background-color: var(--details-bg); overflow: hidden;\n");
		html.append("}\n");
		html.append("details summary {\n");
		html.append("  padding: 12px 16px; font-weight: 600; cursor: pointer; list-style: none;\n");
		html.append("  display: flex; justify-content: space-between; align-items: center; user-select: none;\n");
		html.append("  background-color: var(--table-header-bg);\n");
		html.append("}\n");
		html.append("details summary::-webkit-details-marker { display: none; }\n");
		html.append("details summary:after { content: '+'; font-size: 16px; font-weight: bold; color: var(--text-muted); }\n");
		html.append("details[open] summary:after { content: '\u2212'; }\n");
		html.append(".details-content { padding: 20px; border-top: 1px solid var(--border-color); background-color: var(--card-bg); }\n");
		html.append(".sub-heading {\n");
		html.append("  font-size: 13px; font-weight: 600; color: var(--text-color);\n");
		html.append("  margin: 18px 0 8px 0; padding-bottom: 4px; border-bottom: 1px dashed var(--border-color);\n");
		html.append("}\n");
		html.append(".markdown-body { line-height: 1.65; }\n");
		html.append(".markdown-body p { margin: 8px 0; }\n");
		html.append(".markdown-body ul, .markdown-body ol { padding-left: 24px; margin: 8px 0; }\n");
		html.append(".markdown-body li { margin-bottom: 4px; }\n");
		html.append(".markdown-body blockquote {\n");
		html.append("  margin: 12px 0; padding: 4px 16px; color: var(--text-muted);\n");
		html.append("  border-left: 4px solid var(--primary-color); background-color: var(--badge-bg);\n");
		html.append("  border-radius: 0 4px 4px 0;\n");
		html.append("}\n");
		html.append(".markdown-body pre {\n");
		html.append("  background-color: var(--code-bg); border: 1px solid var(--border-color);\n");
		html.append("  border-radius: 6px; padding: 12px; overflow-x: auto;\n");
		html.append("  font-family: ui-monospace, SFMono-Regular, Consolas, monospace; font-size: 12px;\n");
		html.append("}\n");
		html.append(".markdown-body code {\n");
		html.append("  background-color: var(--code-bg); padding: 2px 5px; border-radius: 4px;\n");
		html.append("  font-family: ui-monospace, SFMono-Regular, Consolas, monospace; font-size: 85%;\n");
		html.append("}\n");
		html.append(".space-block { margin-bottom: 24px; }\n");
		html.append(".space-header {\n");
		html.append("  font-size: 14px; font-weight: 600; color: var(--primary-color);\n");
		html.append("  background-color: var(--table-header-bg); padding: 8px 14px;\n");
		html.append("  border-left: 4px solid var(--primary-color); border-radius: 4px;\n");
		html.append("  margin-bottom: 8px; border: 1px solid var(--border-color); border-left-width: 4px;\n");
		html.append("}\n");
		html.append(".mermaid-wrapper {\n");
		html.append("  margin: 18px 0; padding: 16px; background-color: var(--details-bg);\n");
		html.append("  border: 1px solid var(--border-color); border-radius: 8px;\n");
		html.append("  overflow-x: auto; text-align: center;\n");
		html.append("}\n");
		html.append(".mermaid-wrapper svg { max-width: 100%; height: auto; }\n");
		html.append(".footer {\n");
		html.append("  margin-top: 40px; padding-top: 16px; border-top: 1px solid var(--border-color);\n");
		html.append("  font-size: 12px; color: var(--text-muted); text-align: center;\n");
		html.append("}\n");
		html.append("@media print {\n");
		html.append("  body { background-color: #fff; color: #000; padding: 0; }\n");
		html.append("  .container { border: none; box-shadow: none; padding: 0; max-width: 100%; }\n");
		html.append("  details { border: 1px solid #ccc; margin-bottom: 20px; }\n");
		html.append("  details[open], details { display: block !important; }\n");
		html.append("  details > .details-content { display: block !important; }\n");
		html.append("  details summary:after { display: none; }\n");
		html.append("  .report-action-bar { display: none !important; }\n");
		html.append("}\n");
		html.append(".report-action-bar {\n");
		html.append("  background-color: var(--card-bg); border: 1px solid var(--border-color); border-radius: 8px;\n");
		html.append("  padding: 12px 20px; margin-bottom: 24px; display: flex; justify-content: space-between;\n");
		html.append("  align-items: center; flex-wrap: wrap; gap: 12px; box-shadow: 0 1px 3px rgba(0,0,0,0.05);\n");
		html.append("}\n");
		html.append(".action-bar-left { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }\n");
		html.append(".action-badge {\n");
		html.append("  display: inline-block; padding: 3px 10px; font-size: 12px; font-weight: 600;\n");
		html.append("  border-radius: 12px; background-color: #fff8c5; color: #9a6700; border: 1px solid #d4a72c66;\n");
		html.append("}\n");
		html.append(".action-desc { font-size: 13px; color: var(--text-muted); }\n");
		html.append(".download-btn {\n");
		html.append("  background-color: var(--primary-color); color: #ffffff !important; padding: 8px 18px;\n");
		html.append("  border-radius: 6px; font-size: 13px; font-weight: 600; text-decoration: none;\n");
		html.append("  display: inline-flex; align-items: center; gap: 6px; box-shadow: 0 1px 2px rgba(0,0,0,0.1);\n");
		html.append("  cursor: pointer; transition: background-color 0.2s;\n");
		html.append("}\n");
		html.append(".download-btn:hover { background-color: var(--primary-hover); text-decoration: none !important; }\n");
		html.append("</style>\n");
		html.append("</head>\n");
		html.append("<body>\n");
		html.append("<div class='container'>\n");
		html.append("\n");
		html.append("<!-- Action Bar (14-Day Retention & Offline Download) -->\n");
		html.append("<div class='report-action-bar'>\n");
		html.append("  <div class='action-bar-left'>\n");
		html.append("    <span class='action-badge'>⏰ 線上保留 14 天 / 14-Day Retention</span>\n");
		html.append("    <span class='action-desc'>此報告將於 14 天後自動過期清理。如需永久保存，可隨時點擊右側下載離線檔。</span>\n");
		html.append("  </div>\n");
		html.append("  <div class='action-bar-right'>\n");
		String downloadUrl = (token != null) ? url + "flow/report?token=" + token + "&download=true" : "javascript:void(0)";
		html.append("    <a href='").append(downloadUrl).append("' onclick='downloadReportHtml(this)' class='download-btn' id='dl-report-btn'>\n");
		html.append("      📥 下載離線 HTML 報告 (Download Report)\n");
		html.append("    </a>\n");
		html.append("  </div>\n");
		html.append("</div>\n");

		// Header
		html.append("<header class='header'>\n");
		html.append("  <div class='header-badge'>🤖 JTrac AI Copilot</div>\n");
		html.append("  <h1>AI 查詢分析報告 / AI Query Analysis Report</h1>\n");
		html.append("  <div class='header-meta'>\n");
		html.append("    <span><strong>查詢主旨 / Query:</strong> ").append(escapeHtml(originalSubject)).append("</span>\n");
		html.append("    <span><strong>生成時間 / Generated:</strong> ").append(genDateStr).append("</span>\n");
		html.append("    <span><strong>分析工單數 / Tickets:</strong> ").append(ticketCount).append("</span>\n");
		html.append("  </div>\n");
		html.append("</header>\n");

		// Section 1: Executive Synthesis
		html.append("<section class='section'>\n");
		html.append("  <h2 class='section-title'>🤖 高階總結分析 / Executive Synthesis</h2>\n");
		html.append("  <div class='markdown-body'>\n");
		html.append(renderedAi != null && !renderedAi.trim().isEmpty() ? renderedAi : "<p>無分析內容</p>");
		html.append("  </div>\n");
		html.append("</section>\n");

		// Section 2: Referenced Tickets Table (Grouped by Space)
		html.append("<section class='section'>\n");
		html.append("  <h2 class='section-title'>📋 關聯工單清單速覽 / Referenced Tickets Overview (").append(ticketCount).append(")</h2>\n");
		if (referencedItems != null && !referencedItems.isEmpty()) {
			Map<Space, List<Item>> grouped = groupItemsBySpace(referencedItems);
			for (Map.Entry<Space, List<Item>> entry : grouped.entrySet()) {
				Space sp = entry.getKey();
				List<Item> spItems = entry.getValue();
				String spName = sp.getName() != null ? sp.getName() : sp.getPrefixCode();
				String spPrefix = sp.getPrefixCode() != null ? sp.getPrefixCode() : "";

				html.append("  <div class='space-block'>\n");
				html.append("    <div class='space-header'>📁 專案空間 (Space): <strong>").append(escapeHtml(spName));
				if (!spPrefix.isEmpty() && !spPrefix.equalsIgnoreCase(spName)) {
					html.append(" (").append(escapeHtml(spPrefix)).append(")");
				}
				html.append("</strong> — 共 ").append(spItems.size()).append(" 張工單</div>\n");
				html.append("    <table>\n");
				html.append("      <thead>\n");
				html.append("        <tr>\n");
				html.append("          <th style='width: 120px;'>ID</th>\n");
				html.append("          <th>Summary</th>\n");
				html.append("          <th style='width: 100px;'>Status</th>\n");
				html.append("          <th style='width: 120px;'>Logged By</th>\n");
				html.append("          <th style='width: 120px;'>Assigned To</th>\n");
				html.append("        </tr>\n");
				html.append("      </thead>\n");
				html.append("      <tbody>\n");
				for (Item item : spItems) {
					String itemUrl = url + "app/item/" + item.getRefId();
					html.append("        <tr>\n");
					html.append("          <td style='font-weight: bold;'><a href='").append(itemUrl).append("' target='_blank'>").append(escapeHtml(item.getRefId())).append("</a></td>\n");
					html.append("          <td>").append(escapeHtml(item.getSummary())).append("</td>\n");
					html.append("          <td><span class='status-pill'>").append(escapeHtml(safeGetStatus(item))).append("</span></td>\n");
					html.append("          <td>").append(item.getLoggedBy() != null ? escapeHtml(item.getLoggedBy().getName()) : "").append("</td>\n");
					html.append("          <td>").append(item.getAssignedTo() != null ? escapeHtml(item.getAssignedTo().getName()) : "").append("</td>\n");
					html.append("        </tr>\n");
				}
				html.append("      </tbody>\n");
				html.append("    </table>\n");
				html.append("  </div>\n");
			}
		} else {
			html.append("  <p style='color: var(--text-muted);'>查無符合您授權專案空間內的相關工單。</p>\n");
		}
		html.append("</section>\n");

		// Section 3: Per-Ticket Detailed Dossier (<details> accordion grouped by Space)
		html.append("<section class='section'>\n");
		html.append("  <h2 class='section-title'>🔍 各工單獨立深入診斷 / Per-Ticket Detailed Dossier</h2>\n");
		if (referencedItems != null && !referencedItems.isEmpty()) {
			Map<Space, List<Item>> grouped = groupItemsBySpace(referencedItems);
			for (Map.Entry<Space, List<Item>> entry : grouped.entrySet()) {
				Space sp = entry.getKey();
				List<Item> spItems = entry.getValue();
				String spName = sp.getName() != null ? sp.getName() : sp.getPrefixCode();
				String spPrefix = sp.getPrefixCode() != null ? sp.getPrefixCode() : "";

				html.append("  <div class='space-header' style='margin-top: 24px; margin-bottom: 12px;'>📁 專案空間 (Space): <strong>").append(escapeHtml(spName));
				if (!spPrefix.isEmpty() && !spPrefix.equalsIgnoreCase(spName)) {
					html.append(" (").append(escapeHtml(spPrefix)).append(")");
				}
				html.append("</strong> — ").append(spItems.size()).append(" 項工單診斷</div>\n");

				for (Item item : spItems) {
					String itemUrl = url + "app/item/" + item.getRefId();
					html.append("  <details class='ticket-card'>\n");
					html.append("    <summary>\n");
					html.append("      <span><strong>[").append(escapeHtml(item.getRefId())).append("]</strong> ").append(escapeHtml(item.getSummary())).append("</span>\n");
					html.append("      <span class='status-pill'>").append(escapeHtml(safeGetStatus(item))).append("</span>\n");
					html.append("    </summary>\n");
					html.append("    <div class='details-content'>\n");

					// Meta grid
					html.append("      <div class='meta-grid'>\n");
					html.append("        <div><strong>工單連結 (Link):</strong> <a href='").append(itemUrl).append("' target='_blank'>").append(escapeHtml(item.getRefId())).append("</a></div>\n");
					html.append("        <div><strong>專案空間 (Space):</strong> ").append(item.getSpace() != null ? escapeHtml(item.getSpace().getName()) : "").append("</div>\n");
					html.append("        <div><strong>提出者 (Logged By):</strong> ").append(item.getLoggedBy() != null ? escapeHtml(item.getLoggedBy().getName()) : "").append("</div>\n");
					html.append("        <div><strong>指派者 (Assigned To):</strong> ").append(item.getAssignedTo() != null ? escapeHtml(item.getAssignedTo().getName()) : "").append("</div>\n");
					if (item.getTimeStamp() != null) {
						html.append("        <div><strong>建立時間 (Created):</strong> ").append(sdfFull.format(item.getTimeStamp())).append("</div>\n");
					}
					html.append("      </div>\n");

					// Staged AI Digest for this ticket
					if (perTicketSummaries != null && perTicketSummaries.containsKey(item.getRefId())) {
						String ticketDigest = perTicketSummaries.get(item.getRefId());
						if (ticketDigest != null && !ticketDigest.trim().isEmpty()) {
							html.append("      <div class='sub-heading'>💡 AI 單張精煉分析摘要 / AI Ticket Digest</div>\n");
							html.append("      <div class='markdown-body'>\n");
							String renderedDigest = ItemUtils.renderMarkdown(SensitiveDataMasker.maskSecrets(ticketDigest));
							if (spaces != null && !spaces.isEmpty() && renderedDigest != null) {
								renderedDigest = ItemUtils.autolinkTickets(url, renderedDigest, spaces);
							}
							html.append(renderedDigest != null ? renderedDigest : "");
							html.append("      </div>\n");
						}
					}

					// Raw description (with confidential credentials masked)
					if (item.getDetail() != null && !item.getDetail().trim().isEmpty()) {
						html.append("      <div class='sub-heading'>📝 工單原始描述 / Ticket Description</div>\n");
						html.append("      <div class='markdown-body'>\n");
						String renderedDetail = ItemUtils.renderMarkdown(SensitiveDataMasker.maskSecrets(item.getDetail()));
						if (spaces != null && !spaces.isEmpty() && renderedDetail != null) {
							renderedDetail = ItemUtils.autolinkTickets(url, renderedDetail, spaces);
						}
						html.append(renderedDetail != null ? renderedDetail : escapeHtml(SensitiveDataMasker.maskSecrets(item.getDetail())));
						html.append("      </div>\n");
					}

					// History & Comments (with confidential credentials masked)
					if (item.getHistory() != null && !item.getHistory().isEmpty()) {
						List<History> historyList = new ArrayList<>(item.getHistory());
						Collections.sort(historyList, (h1, h2) -> {
							if (h1.getTimeStamp() == null || h2.getTimeStamp() == null) return 0;
							return h1.getTimeStamp().compareTo(h2.getTimeStamp());
						});
						html.append("      <div class='sub-heading'>💬 歷程與留言紀錄 / History & Comments (").append(historyList.size()).append(")</div>\n");
						html.append("      <table>\n");
						html.append("        <thead>\n");
						html.append("          <tr>\n");
						html.append("            <th style='width: 150px;'>時間 (Time)</th>\n");
						html.append("            <th style='width: 120px;'>執行者 (Logged By)</th>\n");
						html.append("            <th style='width: 100px;'>狀態 (Status)</th>\n");
						html.append("            <th>說明與留言 (Comment)</th>\n");
						html.append("          </tr>\n");
						html.append("        </thead>\n");
						html.append("        <tbody>\n");
						for (History h : historyList) {
							html.append("          <tr>\n");
							html.append("            <td>").append(h.getTimeStamp() != null ? sdfFull.format(h.getTimeStamp()) : "").append("</td>\n");
							html.append("            <td>").append(h.getLoggedBy() != null ? escapeHtml(h.getLoggedBy().getName()) : "").append("</td>\n");
							html.append("            <td><span class='status-pill'>").append(escapeHtml(safeGetStatus(h))).append("</span></td>\n");
							String renderedComment = h.getComment() != null ? ItemUtils.renderMarkdown(SensitiveDataMasker.maskSecrets(h.getComment())) : "";
							html.append("            <td class='markdown-body'>").append(renderedComment != null ? renderedComment : "").append("</td>\n");
							html.append("          </tr>\n");
						}
						html.append("        </tbody>\n");
						html.append("      </table>\n");
					}

					// Attachments
					if (item.getAttachments() != null && !item.getAttachments().isEmpty()) {
						html.append("      <div class='sub-heading'>📎 附加檔案清單 / Attachments (").append(item.getAttachments().size()).append(")</div>\n");
						html.append("      <table>\n");
						html.append("        <thead>\n");
						html.append("          <tr>\n");
						html.append("            <th>檔案名稱 (File Name)</th>\n");
						html.append("          </tr>\n");
						html.append("        </thead>\n");
						html.append("        <tbody>\n");
						for (Attachment att : item.getAttachments()) {
							html.append("          <tr>\n");
							html.append("            <td>").append(escapeHtml(att.getFileName())).append("</td>\n");
							html.append("          </tr>\n");
						}
						html.append("        </tbody>\n");
						html.append("      </table>\n");
					}

					html.append("    </div>\n");
					html.append("  </details>\n");
				}
			}
		} else {
			html.append("  <p style='color: var(--text-muted);'>無工單資料。</p>\n");
		}
		html.append("</section>\n");

		// Footer
		html.append("<footer class='footer'>\n");
		html.append("  <p>本報告由 JTrac 郵件 AI 查詢秘書自動產生與匯出。<br>\n");
		html.append("  <a href='").append(url).append("' target='_blank'>").append(url).append("</a></p>\n");
		html.append("</footer>\n");

		// Inject Mermaid JS and Initialization for offline SVG diagrams
		String mermaidJs = getMermaidJsContent();
		if (mermaidJs != null && !mermaidJs.isEmpty()) {
			html.append("<script id=\"mermaid-core-js\">\n");
			html.append(mermaidJs).append("\n");
			html.append("</script>\n");
			html.append("<script>\n");
			html.append("document.addEventListener('DOMContentLoaded', function() {\n");
			html.append("  try {\n");
			html.append("    var isDark = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;\n");
			html.append("    if (typeof mermaid !== 'undefined') {\n");
			html.append("      mermaid.initialize({\n");
			html.append("        startOnLoad: false,\n");
			html.append("        theme: isDark ? 'dark' : 'default',\n");
			html.append("        securityLevel: 'loose'\n");
			html.append("      });\n");
			html.append("      var codeBlocks = document.querySelectorAll('pre code.language-mermaid, pre.mermaid, code.language-mermaid');\n");
			html.append("      codeBlocks.forEach(function(codeEl, index) {\n");
			html.append("        try {\n");
			html.append("          var preEl = (codeEl.parentElement && codeEl.parentElement.tagName === 'PRE') ? codeEl.parentElement : codeEl;\n");
			html.append("          var graphDef = codeEl.textContent.trim();\n");
			html.append("          if (!graphDef) return;\n");
			html.append("          var graphId = 'mermaid-svg-' + index + '-' + Math.floor(Math.random() * 10000);\n");
			html.append("          var wrapper = document.createElement('div');\n");
			html.append("          wrapper.className = 'mermaid-wrapper';\n");
			html.append("          mermaid.render(graphId, graphDef).then(function(res) {\n");
			html.append("            wrapper.innerHTML = res.svg;\n");
			html.append("            preEl.parentNode.replaceChild(wrapper, preEl);\n");
			html.append("          }).catch(function(err) {\n");
			html.append("            console.warn('Mermaid rendering syntax fallback for block ' + index + ':', err);\n");
			html.append("          });\n");
			html.append("        } catch (innerEx) {\n");
			html.append("          console.warn('Mermaid element conversion error:', innerEx);\n");
			html.append("        }\n");
			html.append("      });\n");
			html.append("    }\n");
			html.append("  } catch (e) {\n");
			html.append("    console.warn('Mermaid global init fallback:', e);\n");
			html.append("  }\n");
			html.append("});\n");
			html.append("</script>\n");
		}

		html.append("<script>\n");
		html.append("function downloadReportHtml(btn) {\n");
		html.append("  if (window.location.protocol === 'file:' || !window.location.href.includes('token=')) {\n");
		html.append("    var docClone = document.documentElement.cloneNode(true);\n");
		html.append("    var blob = new Blob([docClone.outerHTML], { type: 'text/html;charset=utf-8' });\n");
		html.append("    var a = document.createElement('a');\n");
		html.append("    a.href = URL.createObjectURL(blob);\n");
		html.append("    var rawTitle = document.title ? document.title.replace(/[^a-zA-Z0-9_\\-\\u4e00-\\u9fa5]/g, '_') : 'JTrac-AI-Report';\n");
		html.append("    a.download = rawTitle + '.html';\n");
		html.append("    document.body.appendChild(a);\n");
		html.append("    a.click();\n");
		html.append("    document.body.removeChild(a);\n");
		html.append("  }\n");
		html.append("}\n");
		html.append("document.addEventListener('DOMContentLoaded', function() {\n");
		html.append("  if (window.location.protocol === 'file:') {\n");
		html.append("    var desc = document.querySelector('.action-desc');\n");
		html.append("    if (desc) desc.textContent = '您正在瀏覽已下載之本機離線封裝檔案，包含完整離線 Mermaid 引擎，永久可用。';\n");
		html.append("    var badge = document.querySelector('.action-badge');\n");
		html.append("    if (badge) {\n");
		html.append("      badge.textContent = '✓ 本機離線封存檔 (Offline Archive)';\n");
		html.append("      badge.style.backgroundColor = '#dafbe1';\n");
		html.append("      badge.style.color = '#1a7f37';\n");
		html.append("      badge.style.borderColor = '#4ac26b66';\n");
		html.append("    }\n");
		html.append("    var dlBtn = document.getElementById('dl-report-btn');\n");
		html.append("    if (dlBtn) dlBtn.style.display = 'none';\n");
		html.append("  }\n");
		html.append("});\n");
		html.append("</script>\n");

		html.append("</div>\n");
		html.append("</body>\n");
		html.append("</html>\n");

		return html.toString();
	}

	private String escapeHtml(String text) {
		if (text == null) {
			return "";
		}
		StringBuilder sb = new StringBuilder(text.length() + 16);
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			switch (c) {
				case '&': sb.append("&amp;"); break;
				case '<': sb.append("&lt;"); break;
				case '>': sb.append("&gt;"); break;
				case '"': sb.append("&quot;"); break;
				case '\'': sb.append("&#39;"); break;
				default: sb.append(c); break;
			}
		}
		return sb.toString();
	}

	public static Map<Space, List<Item>> groupItemsBySpace(List<Item> items) {
		if (items == null || items.isEmpty()) {
			return Collections.emptyMap();
		}
		Map<Space, List<Item>> map = new LinkedHashMap<>();
		for (Item item : items) {
			Space space = item.getSpace();
			if (space == null) {
				space = new Space();
				space.setName("Other / Unassigned");
				space.setPrefixCode("OTHER");
			}
			List<Item> spaceList = map.get(space);
			if (spaceList == null) {
				spaceList = new ArrayList<>();
				map.put(space, spaceList);
			}
			spaceList.add(item);
		}

		List<Map.Entry<Space, List<Item>>> entries = new ArrayList<>(map.entrySet());
		entries.sort((e1, e2) -> {
			String n1 = e1.getKey().getName() != null ? e1.getKey().getName() : "";
			String n2 = e2.getKey().getName() != null ? e2.getKey().getName() : "";
			int cmp = n1.compareToIgnoreCase(n2);
			if (cmp != 0) {
				return cmp;
			}
			String p1 = e1.getKey().getPrefixCode() != null ? e1.getKey().getPrefixCode() : "";
			String p2 = e2.getKey().getPrefixCode() != null ? e2.getKey().getPrefixCode() : "";
			return p1.compareToIgnoreCase(p2);
		});

		Map<Space, List<Item>> sortedMap = new LinkedHashMap<>();
		for (Map.Entry<Space, List<Item>> entry : entries) {
			List<Item> spaceItems = entry.getValue();
			spaceItems.sort((i1, i2) -> {
				long id1 = i1.getId() > 0 ? i1.getId() : (long) i1.getSequenceNum();
				long id2 = i2.getId() > 0 ? i2.getId() : (long) i2.getSequenceNum();
				if (id1 != id2) {
					return Long.compare(id2, id1); // DESC
				}
				if (i1.getTimeStamp() != null && i2.getTimeStamp() != null) {
					return i2.getTimeStamp().compareTo(i1.getTimeStamp());
				}
				return 0;
			});
			sortedMap.put(entry.getKey(), spaceItems);
		}
		return sortedMap;
	}

	public static List<Item> sortItemsBySpaceAndIdDesc(List<Item> items) {
		if (items == null || items.isEmpty()) {
			return Collections.emptyList();
		}
		Map<Space, List<Item>> grouped = groupItemsBySpace(items);
		List<Item> result = new ArrayList<>(items.size());
		for (List<Item> spaceItems : grouped.values()) {
			result.addAll(spaceItems);
		}
		return result;
	}

	private static volatile String cachedMermaidJs = null;

	public static String getMermaidJsContent() {
		if (cachedMermaidJs == null) {
			synchronized (MailSender.class) {
				if (cachedMermaidJs == null) {
					try (InputStream is = MailSender.class.getResourceAsStream("/info/jtrac/mail/mermaid.min.js")) {
						if (is != null) {
							ByteArrayOutputStream baos = new ByteArrayOutputStream();
							byte[] buffer = new byte[16384];
							int len;
							while ((len = is.read(buffer)) != -1) {
								baos.write(buffer, 0, len);
							}
							cachedMermaidJs = baos.toString(StandardCharsets.UTF_8.name());
						} else {
							logger.warn("mermaid.min.js resource not found on classpath: /info/jtrac/mail/mermaid.min.js");
							cachedMermaidJs = "";
						}
					} catch (Exception e) {
						logger.error("Failed to load mermaid.min.js: " + e.getMessage(), e);
						cachedMermaidJs = "";
					}
				}
			}
		}
		return cachedMermaidJs;
	}

	private String safeGetStatus(AbstractItem item) {
		if (item == null) {
			return "";
		}
		try {
			String val = item.getStatusValue();
			return val != null ? val : "";
		} catch (Exception e) {
			return item.getStatus() != null ? String.valueOf(item.getStatus()) : "";
		}
	}

	public void sendAiOfflineNotice(String toEmail, String originalSubject, Locale locale) {
		if (sender == null) {
			logger.debug("mail sender is null, not sending AI offline notice");
			return;
		}
		if (toEmail == null || toEmail.trim().isEmpty() || "no".equalsIgnoreCase(toEmail.trim())) {
			logger.warn("Invalid recipient email for AI offline notice: " + toEmail);
			return;
		}
		if (locale == null) {
			locale = defaultLocale;
		}

		logger.debug("Preparing AI offline notice email to " + toEmail);
		try {
			MimeMessage message = sender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
			helper.setTo(toEmail);
			helper.setFrom(from);
			helper.setSentDate(new Date());

			String subject = messageSource.getMessage("mail.ai_query.offline_notice_subject", null,
					"[JTrac AI] Service Offline or Request Timed Out", locale);
			helper.setSubject(subject);

			String noticeBody = messageSource.getMessage("mail.ai_query.offline_notice_body", null,
					"The local AI query service is currently offline or timed out. Please try again later or contact your system administrator.", locale);

			StringBuilder sb = new StringBuilder();
			sb.append("<div style='font-family: -apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, Helvetica, Arial, sans-serif; max-width: 650px; margin: 0 auto; padding: 20px; color: #24292e;'>");
			sb.append("<div style='background-color: #fff3cd; border: 1px solid #ffeeba; border-radius: 6px; padding: 18px; color: #856404;'>");
			sb.append("<h3 style='margin-top: 0; color: #856404;'>\u26A0\uFE0F ").append(subject).append("</h3>");
			sb.append("<p style='font-size: 14px;'>").append(noticeBody).append("</p>");
			if (originalSubject != null && !originalSubject.trim().isEmpty()) {
				sb.append("<hr style='border: 0; border-top: 1px solid #ffeeba; margin: 15px 0;'>");
				sb.append("<p style='font-size: 12px; color: #6c757d; margin-bottom: 0;'>Original Subject: ").append(originalSubject.trim()).append("</p>");
			}
			sb.append("</div>");
			sb.append("</div>");

			helper.setText(addHeaderAndFooter(new StringBuffer(sb.toString())), true);
			sendInNewThread(message);
		} catch (Exception e) {
			logger.error("Failed to prepare and send AI offline notice e-mail", e);
		}
	}

	public void sendAiZeroHitNotice(String toEmail, String originalSubject, Locale locale, Set<info.jtrac.domain.Space> authorizedSpaces) {
		if (sender == null) {
			logger.debug("mail sender is null, not sending AI zero-hit notice");
			return;
		}
		if (toEmail == null || toEmail.trim().isEmpty() || "no".equalsIgnoreCase(toEmail.trim())) {
			logger.warn("Invalid recipient email for AI zero-hit notice: " + toEmail);
			return;
		}
		if (locale == null) {
			locale = defaultLocale;
		}

		logger.debug("Preparing AI zero-hit notice email to " + toEmail);
		try {
			MimeMessage message = sender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
			helper.setTo(toEmail);
			helper.setFrom(from);
			helper.setSentDate(new Date());

			String subject = messageSource.getMessage("mail.ai_query.zero_hit_subject", null,
					"[JTrac AI] No Matching Tickets Found", locale);
			helper.setSubject(subject);

			String noticeBody = messageSource.getMessage("mail.ai_query.zero_hit_body", null,
					"In your authorized project spaces, no relevant historical tickets or attachment records matched your inquiry.", locale);
			String spacesHeading = messageSource.getMessage("mail.ai_query.zero_hit_spaces", null,
					"Your currently authorized project spaces:", locale);
			String noticeTip = messageSource.getMessage("mail.ai_query.zero_hit_tip", null,
					"In accordance with our strict data grounding policy, JTrac AI Query Copilot does not speculate without internal issue evidence. We suggest refining your search keywords or contacting your space administrator.", locale);

			StringBuilder sb = new StringBuilder();
			sb.append("<div style='font-family: -apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, Helvetica, Arial, sans-serif; max-width: 650px; margin: 0 auto; padding: 20px; color: #24292e;'>");
			sb.append("<div style='background-color: #f8f9fa; border: 1px solid #e1e4e8; border-radius: 8px; padding: 24px;'>");
			sb.append("<h3 style='margin-top: 0; color: #24292e;'>\uD83D\uDD0D ").append(escapeHtml(subject)).append("</h3>");
			sb.append("<p style='font-size: 14px; line-height: 1.6; color: #444;'>").append(escapeHtml(noticeBody)).append("</p>");

			if (authorizedSpaces != null && !authorizedSpaces.isEmpty()) {
				sb.append("<div style='margin: 18px 0; padding: 14px; background-color: #ffffff; border: 1px solid #e1e4e8; border-radius: 6px;'>");
				sb.append("<strong style='font-size: 13px; color: #0366d6;'>").append(escapeHtml(spacesHeading)).append("</strong>");
				sb.append("<ul style='margin: 8px 0 0 0; padding-left: 20px; font-size: 13px; color: #555;'>");
				for (info.jtrac.domain.Space sp : authorizedSpaces) {
					String spName = sp.getName() != null ? sp.getName() : sp.getPrefixCode();
					sb.append("<li>").append(escapeHtml(spName));
					if (sp.getPrefixCode() != null && !sp.getPrefixCode().equalsIgnoreCase(spName)) {
						sb.append(" (").append(escapeHtml(sp.getPrefixCode())).append(")");
					}
					sb.append("</li>");
				}
				sb.append("</ul>");
				sb.append("</div>");
			}

			sb.append("<div style='background-color: #e7f3fe; border-left: 4px solid #0366d6; padding: 12px 16px; border-radius: 4px; margin-top: 16px;'>");
			sb.append("<p style='margin: 0; font-size: 13px; color: #0c5460; line-height: 1.5;'>\uD83D\uDCA1 ").append(escapeHtml(noticeTip)).append("</p>");
			sb.append("</div>");

			if (originalSubject != null && !originalSubject.trim().isEmpty()) {
				sb.append("<hr style='border: 0; border-top: 1px solid #e1e4e8; margin: 20px 0 10px 0;'>");
				sb.append("<p style='font-size: 12px; color: #6c757d; margin-bottom: 0;'>Original Subject: ").append(escapeHtml(originalSubject.trim())).append("</p>");
			}
			sb.append("</div>");
			sb.append("</div>");

			helper.setText(addHeaderAndFooter(new StringBuffer(sb.toString())), true);
			sendInNewThread(message);
		} catch (Exception e) {
			logger.error("Failed to prepare and send AI zero-hit notice e-mail", e);
		}
	}

}
