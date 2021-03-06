/*
 * NOTE: This copyright does *not* cover user programs that use HQ
 * program services by normal system calls through the application
 * program interfaces provided as part of the Hyperic Plug-in Development
 * Kit or the Hyperic Client Development Kit - this is merely considered
 * normal use of the program, and does *not* fall under the heading of
 * "derived work".
 * 
 * Copyright (C) [2004, 2005, 2006], Hyperic, Inc.
 * This file is part of HQ.
 * 
 * HQ is free software; you can redistribute it and/or modify
 * it under the terms version 2 of the GNU General Public License as
 * published by the Free Software Foundation. This program is distributed
 * in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307
 * USA.
 */

/*
 * Created on Aug 6, 2003
 *
 */
package org.hyperic.hq.ui.action.resource.common.inventory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.hyperic.hq.bizapp.shared.AppdefBoss;
import org.hyperic.hq.bizapp.shared.AuthzBoss;
import org.hyperic.hq.bizapp.shared.ControlBoss;
import org.hyperic.hq.ui.Constants;
import org.hyperic.hq.ui.Portal;
import org.hyperic.hq.ui.action.resource.ResourceController;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

public abstract class ResourceInventoryPortalAction
    extends ResourceController {

    public ResourceInventoryPortalAction(AppdefBoss appdefBoss, AuthzBoss authzBoss, ControlBoss controlBoss) {
        super(appdefBoss, authzBoss, controlBoss);
    }

    public ActionForward viewResource(ActionMapping mapping, ActionForm form, HttpServletRequest request,
                                      HttpServletResponse response) throws Exception {
        super.setNavMapLocation(request, mapping, Constants.INVENTORY_LOC_TYPE);
        return null;
    }

    protected void editConfig(HttpServletRequest request, HttpServletResponse response, Portal portal) throws Exception {
        setResource(request, response, true);
        portal.setDialog(true);
        request.setAttribute(Constants.PORTAL_KEY, portal);
    }
}
