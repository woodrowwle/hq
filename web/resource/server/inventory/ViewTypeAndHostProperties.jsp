<%@ page language="java" %>
<%@ page errorPage="/common/Error.jsp" %>
<%@ taglib uri="struts-html-el" prefix="html" %>
<%@ taglib uri="struts-tiles" prefix="tiles" %>
<%@ taglib uri="jstl-fmt" prefix="fmt" %>
<%@ taglib uri="jstl-c" prefix="c" %>
<%--
  NOTE: This copyright does *not* cover user programs that use HQ
  program services by normal system calls through the application
  program interfaces provided as part of the Hyperic Plug-in Development
  Kit or the Hyperic Client Development Kit - this is merely considered
  normal use of the program, and does *not* fall under the heading of
  "derived work".
  
  Copyright (C) [2004, 2005, 2006], Hyperic, Inc.
  This file is part of HQ.
  
  HQ is free software; you can redistribute it and/or modify
  it under the terms version 2 of the GNU General Public License as
  published by the Free Software Foundation. This program is distributed
  in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
  even the implied warranty of MERCHANTABILITY or FITNESS FOR A
  PARTICULAR PURPOSE. See the GNU General Public License for more
  details.
  
  You should have received a copy of the GNU General Public License
  along with this program; if not, write to the Free Software
  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307
  USA.
 --%>
<!--  /  -->

<tiles:importAttribute name="serviceCount"/>
<tiles:importAttribute name="serviceTypeMap"/>

<!--  TYPE AND HOST PROPERTIES CONTENTS -->
<table width="100%" cellpadding="0" cellspacing="0" border="0">
	<tr>
		<td width="20%" class="BlockLabel"><fmt:message key="resource.server.inventory.type.InstallPath"/></td>
		<td width="30%" class="BlockContent"><c:out value="${Resource.installPath}"/></td>
		<td width="20%" class="BlockLabel"><fmt:message key="resource.server.inventory.type.HostPlatform"/></td>
		<td width="30%" class="BlockContent"><html:link page="/resource/platform/Inventory.do?mode=view&type=${Resource.platform.entityId.type}&rid=${Resource.platform.id}"><c:out value="${Resource.platform.name}"/></html:link></td>
	</tr>
	<tr>
      <td colspan="4" class="BlockBottomLine"><html:img page="/images/spacer.gif" width="1" height="1" border="0"/></td>
    </tr>
</table>

<!--  SERVICE COUNTS CONTENTS -->
<table width="100%" cellpadding="0" cellspacing="0" border="0">
  <tr>
    <td width="20%" class="BlockLabel"><fmt:message key="resource.server.inventory.serviceCounts.TotalServices"/></td>
		<td width="30%" class="BlockContent"><c:out value="${serviceCount}"/></td>
    <td width="20%" class="BlockLabel">&nbsp;</td>
    <td width="30%" class="BlockContent">&nbsp;</td>
  </tr>
  <tr valign="top">
    <td width="20%" class="BlockLabel"><fmt:message key="resource.server.inventory.serviceCounts.ServicesByType"/></td>
    <td width="30%" class="BlockContentNoPadding" colspan="3">
      <table width="66%" cellpadding="0" cellspacing="0" border="0" class="BlockContent">
        <tr valign="top">
<c:forEach var="entry" varStatus="status" items="${serviceTypeMap}">
          <td width="50%"><c:out value="${entry.key}"/> (<c:out value="${entry.value}"/>)</td>
  <c:choose>
    <c:when test="${status.count % 2 == 0}">
        </tr>
        <tr>
    </c:when>
    <c:otherwise>
      <c:if test="${status.last}">
        <c:forEach begin="${(status.count % 2) + 1}" end="2">
          <td width="50%">&nbsp;</td>
        </c:forEach>
      </c:if>
    </c:otherwise>
  </c:choose>
</c:forEach>
        </tr>
      </table> 
    </td>
  </tr>
  <tr>
    <td colspan="4" class="BlockBottomLine"><html:img page="/images/spacer.gif" width="1" height="1" border="0"/></td>
  </tr>
</table>
<!--  /  --><c:set var="editUrl" value="/resource/server/Inventory.do?mode=editType&rid=${Resource.id}&type=${Resource.entityId.type}"/>

<c:if test="${useroperations['modifyServer']}">
<!--  GENERAL PROPERTIES TOOLBAR -->
<tiles:insert definition=".toolbar.edit">
  <tiles:put name="editUrl" beanName="editUrl"/>
</tiles:insert>
<!--  /  -->
</c:if>
