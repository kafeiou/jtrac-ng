<%@ page pageEncoding="UTF-8" contentType="text/html; charset=UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="my" tagdir="/WEB-INF/tags" %> 
<fmt:setLocale value="${locale}" />
<fmt:setBundle basename="messages" />
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
    <title>${TITLE}</title>
	<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
	<link rel="stylesheet" href="${pageContext.request.contextPath}/resources/jtrac.css" type="text/css"/>
	<script type="text/javascript" src="//code.jquery.com/jquery-3.6.0.min.js"></script>
	<script type="text/javascript">
		function toggleWatch () {
			$.ajax({
				url: $("#toggleWatchLinkID")[0].href,
				type: 'POST',
				dataType: 'json',
				success: function (data, status) {
					if (data.result == "error") {
						alert(data.error);
					} else {
						if (data.result == "watch") {
							$("#toggleWatchLinkID")[0].href = "rest/unwatch?${escapedName}";
							$("#toggleWatchLinkID")[0].text = '<fmt:message key="wiki.unwatch"/>';
						} else {
							$("#toggleWatchLinkID")[0].href = "rest/watch?${escapedName}";
							$("#toggleWatchLinkID")[0].text = '<fmt:message key="wiki.watch"/>';
						}
					}
				},
				failure: function (data, status) {
					alert(status+"-"+data);
				}
			});
		}
	</script>

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
	<table width="100%" cellpadding="10">
		<tr valign="top"><td width="200px">
			<div style="margin-top: 30px">&nbsp;</div>
			<div class="menu">
				<form method="GET" action="search">
					<div align="right">
						<input type="text" name="q"><br>
						<input type="submit" value="<fmt:message key="search"/>">
					</div>
				</form>
				<HR>
				<b><fmt:message key="wiki.thisPage"/></b><P>
				<fmt:message key="wiki.lastEdited"/>: <a href="view?${previousVersion}">${lastEdit}</a><P>
				<a href="diff?${escapedName}"><fmt:message key="wiki.whatsChanged"/></a><P>
				<c:if test="${escapedName.startsWith('old') && escapedName.contains('.')}">
					<%-- 6 because the name is like "old%2FHomePage" --%>
					<a href="view?${escapedName.substring(6, escapedName.indexOf('.'))}"><fmt:message key="wiki.currentVersion"/></a>
					(<a href="diff?${escapedName}&cur=1">diff</a>)<P>
				</c:if>
				<c:if test="${not empty user}">
					<a href="edit?${escapedName}"><fmt:message key="edit"/></a><P>
					<c:if test="${isWatching}">
						<a id="toggleWatchLinkID" href="rest/unwatch?${escapedName}" onClick="toggleWatch(); return false"><fmt:message key="wiki.unwatch"/></a><P>
					</c:if>
					<c:if test="${! isWatching}">
						<a id="toggleWatchLinkID" href="rest/watch?${escapedName}" onClick="toggleWatch(); return false"><fmt:message key="wiki.watch"/></a><P>
					</c:if>
				</c:if>
				<HR>
				<b><fmt:message key="wiki.otherPages"/></b><P>
				<a href="${pageContext.request.contextPath}/app"><fmt:message key="wiki.dashboard"/></a><br><br>
				<a href="view?HomePage">HomePage</a><br>
				<a href="view?RecentChanges">RecentChanges</a><br>
				<a href="view?SandBox">SandBox</a><br>
			</div>
		</td>
		<td>
			<H1 class="wikiTitle"> ${TITLE} </H1>
			<div class="mainContent">
			   ${CONTENT}
			 </div>
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

