<%@ page pageEncoding="UTF-8" contentType="text/html; charset=UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="my" tagdir="/WEB-INF/tags" %> 
<fmt:setLocale value="${locale}" />
<fmt:setBundle basename="messages" />
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html><head>
    <title><fmt:message key="wiki.searchResults"/> '${pattern}'</title>
	<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
	<link rel="stylesheet" href="${pageContext.request.contextPath}/resources/jtrac.css" type="text/css"/>

	<my:cssOverride/>
</head>
<body>
<table width="100%" class="jtrac header" padding="4">
	<tr>
		<td width="20%" align="left"><a href="${pageContext.request.contextPath}/app"><img height="55px" src="${pageContext.request.contextPath}/resources/jtrac-logo.svg"/></a></td>
		<td align="left"><b>Lightweight Knowledge Query System</b></td>
	</tr>
</table>

<DIV id="mainarea">
	<table width="100%" cellpadding="20">
		<tr> <td align="center" colspan="2">
			<H1 class="wikiTitle"><fmt:message key="wiki.searchResults"/> '${pattern}'</H1>
		</td></tr>
		<tr valign="top"><td width="200px">
			<div class="menu">
				<form method="GET" action="search">
					<input type="text" name="q" value="${pattern}">
					<div align="right">
						<input type="submit" value="<fmt:message key="search"/>">
					</div>
				</form>
				<HR>
				<b><fmt:message key="wiki.otherPages"/></b><P>
				<a href="${pageContext.request.contextPath}/app"><fmt:message key="wiki.dashboard"/></a><br><br>
				<a href="view?HomePage">HomePage</a><BR>
				<a href="view?RecentChanges">RecentChanges</a><BR>
				<a href="view?SandBox">SandBox</a><BR>
			</div>
		</td><td>
			<c:if test="${empty results}">
				<fmt:message key="wiki.noResults"/> '${pattern}'
			</c:if>
			<c:if test="${not empty results}">
				<c:forEach items="${results}" var="result">
					<a href="view?${result.name}">${empty result.altTitle ? result.name : result.altTitle}</a><P>
					<c:if test="${not empty result.fragments}">
						<ul>
							<c:forEach items="${result.fragments}" var="fragment" varStatus="status">
								<c:if test="${status.count eq 4}">
									</ul>
									<details><summary><fmt:message key="wiki.moreOccurences"/></summary>
									<ul>
								</c:if>
								<li>${fragment}
							</c:forEach>
							<c:if test="${fn:length(result.fragments) gt 3}">
									</details><p>
							</c:if>
						</ul>
					</c:if>
				</c:forEach>
			</c:if>
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
