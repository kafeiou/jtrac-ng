<%@ page pageEncoding="UTF-8" contentType="text/html; charset=UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="my" tagdir="/WEB-INF/tags" %> 
<fmt:setLocale value="${locale}" />
<fmt:setBundle basename="messages" />
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html><head>
	<title><fmt:message key="edit"/> ${page.name}</title>
	<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
	<link rel="stylesheet" href="${pageContext.request.contextPath}/resources/jtrac.css" type="text/css"/>
	<script type="text/javascript" src="//code.jquery.com/jquery-3.6.0.min.js"></script>
	<script type="text/javascript">
		function previewPage() {
			var dataString = $("#edit").serialize();
			$.ajax({
				type: "POST",
				url: "preview",
				data: dataString,
				success: function(msg) {
					$("#previewArea").html(msg);
					$("#previewArea").show();
					window.scrollBy(0,1000);
				},
				error: function(msg) {
					alert("Can't show preview");  
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
	<tr><td align="center">
		<H1 class="wikiTitle"> <fmt:message key="edit"/> ${pageName} </H1>
		<br>
		<form method="post" action="update" id="edit">
			<input type="hidden" name="page" value="${escapedName}"/>
			<textarea name="content" rows="40" cols="120" wrap="virtual" id="pageContent">${CONTENT}</textarea>
	</td></tr>
	<tr> <td align="center">
		<a href="view?SandBox" target="_blank">SandBox</a>
		&nbsp; &nbsp; &nbsp; &nbsp; &nbsp; 
		<a href="https://commonmark.org/help/" target="_blank">Markdown syntax</a>
		&nbsp; &nbsp; &nbsp; &nbsp; &nbsp; 
		<input type="button" value="<fmt:message key="wiki.preview"/>" onclick="previewPage()" />
		&nbsp; &nbsp; &nbsp; &nbsp; &nbsp; 
		<input type="button" value="<fmt:message key="cancel"/>" onclick="window.location='view?${pageName}'"/>
		&nbsp; &nbsp; &nbsp; &nbsp; &nbsp; 
		<input type="submit" value="<fmt:message key="save"/>"/>
	</td> </tr>
	<tr><td align="center"><table cellpadding="5">
		<tr><td><fmt:message key="wiki.whatsChanged"/></td><td><input type="text" name="comment" value="-" size="60"/></td></tr>
		<tr><td><fmt:message key="wiki.altTitle"/></td><td><input type="text" name="seo.name" value="${seoName}" size="40"/></td></tr>
		<tr><td><fmt:message key="wiki.redirect"/></td><td><input type="text" name="redirect.to" value="${redirectTo}" size="40"/></td></tr>
		</form>
		</table>
	</td> </tr>
</table>
</DIV>
<div id="previewArea"
	style="margin:10px auto; width:90%; border:1px solid #000000; display:none; max-height:500px; overflow:scroll; padding:5px;">
</div>
<table width="100%" class="jtrac">
	<tr class="header">
		<td align="right">
			<i><fmt:message key="wiki.poweredBy"/> <a href="https://sourceforge.net/projects/j-trac/" target="_blank">JTrac</a> ${jtracVersion}</span></i>
		</td>
	</tr>
</table>
</body></html>

