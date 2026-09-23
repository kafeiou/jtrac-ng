<%@ page pageEncoding="UTF-8" contentType="text/html; charset=UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="my" tagdir="/WEB-INF/tags" %> 
<fmt:setLocale value="${locale}" />
<fmt:setBundle basename="messages" />
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html><head>
	<title><fmt:message key="wiki.whatsChangedIn"/> ${TITLE}</title>
	<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
	<link rel="stylesheet" href="${pageContext.request.contextPath}/resources/jtrac.css" type="text/css"/>
	<script type="text/javascript" src="//code.jquery.com/jquery-3.6.0.min.js"></script>

	<my:cssOverride/>
</head>
<body>
<table width="100%" class="jtrac header jtrac-brand-header">
	<tr>
		<td class="jtrac-brand-logo-col" align="left"><a href="${pageContext.request.contextPath}/app"><img class="jtrac-brand-logo" src="${pageContext.request.contextPath}/resources/jtrac-logo.svg" alt="JTrac NG"/></a></td>
		<td class="jtrac-brand-text-col" align="left"><b><span class="jtrac-brand-message">Lightweight Knowledge Query System</span></b></td>
	</tr>
</table>

<DIV id="mainarea">
	<table width="100%" cellpadding="20">
	<tr><td colspan="2">
		<H1 class="wikiTitle">
			<fmt:message key="wiki.whatsChangedIn"/> ${pageName}?
		</H1>
        <br><i><fmt:message key="wiki.changedBy"/> ${editUser}: ${editComment}</i><br>
		${diff}
	</td></tr>
	<tr valign="top"><td width="200px">
		<div class="menu">
			<form method="GET" action="search">
				<div align="right">
					<input type="text" name="q">
					<input type="submit" value="<fmt:message key="search"/>">
				</div>
			</form>
			<HR>
			<b><fmt:message key="wiki.thisPage"/></b><P>
			<fmt:message key="wiki.lastEdited"/>: <a href="view?${previousVersion}">${lastEdit}</a><P>
			<a href='diff?${previousVersion}'><fmt:message key="wiki.previousChange"/></a><P>
			<a href="view?${pageName}"><fmt:message key="wiki.currentVersion"/></a><P>
			<HR>
			<b><fmt:message key="wiki.otherPages"/></b><P>
			<a href="${pageContext.request.contextPath}/app"><fmt:message key="wiki.dashboard"/></a><br><br>
			<a href="view?HomePage">HomePage</a><BR>
			<a href="view?RecentChanges">RecentChanges</a><BR>
			<a href="view?SandBox">SandBox</a><BR>
		</div>
	</td><td>
		${CONTENT}
	</td></tr>
	</table>
</DIV>

<table width="100%" class="jtrac">
	<tr class="header">
		<td align="right">
			<i><fmt:message key="wiki.poweredBy"/> <a href="https://sourceforge.net/projects/j-trac/" target="_blank">JTrac</a> ${jtracVersion}</span></i>
		</td>
	</tr>
</table>
</body></html>

