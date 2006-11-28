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

package org.hyperic.hq.events.server.session;

import java.util.ArrayList;
import java.util.List;

import org.hyperic.hibernate.PersistedObject;

/**
 * Escalation generated by hbm2java
 */
public class Escalation extends PersistedObject
{

    // Fields

    private String name;

    /**
     * If true, then wait for "waitTime" before escalating up the chain.
     */
    private boolean allowPause;

    /**
     * waitTime is specified in milliseconds.
     */
    private long maxWaitTime;

    /**
     * if true, then send notification to all the people on the escalation
     * chain.
     * Else, send notification to only those people who have been previously
     * notified.
     */
    private boolean notifyAll;
    private long creationTime;
    private long modifiedTime;
    private List actions = new ArrayList(0);

    public static Escalation newInstance(String name) {
        Escalation e = new Escalation();
        e.setName(name);
        return e;
    }

    public static Escalation newInstance() {
        return new Escalation();
    }
    
    // Constructors

    /**
     * default constructor
     */
    public Escalation() {
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * If true, then wait for "waitTime" before escalating up the chain.
     */
    public boolean isAllowPause() {
        return this.allowPause;
    }

    public void setAllowPause(boolean pause) {
        this.allowPause = pause;
    }

    /**
     * waitTime is specified in milliseconds.
     */
    public long getMaxWaitTime() {
        return this.maxWaitTime;
    }

    public void setMaxWaitTime(long waitTime) {
        this.maxWaitTime = waitTime;
    }

    /**
     * if true, then send notification to all the people on the escalation
     * chain.
     * Else, send notification to only those people who have been previously
     * notified.
     */
    public boolean isNotifyAll() {
        return this.notifyAll;
    }

    public void setNotifyAll(boolean notifyAll) {
        this.notifyAll = notifyAll;
    }

    public long getCreationTime() {
        return this.creationTime;
    }

    public void setCreationTime(long creationTime) {
        this.creationTime = creationTime;
    }

    public long getModifiedTime() {
        return this.modifiedTime;
    }

    public void setModifiedTime(long modifiedTime) {
        this.modifiedTime = modifiedTime;
    }

    public List getActions() {
        return actions;
    }

    protected void setActions(List actions) {
        this.actions = actions;
    }

    public EscalationAction getCurrentAction(int curlevel) {
        if (curlevel >= getActions().size()) {
            throw new IndexOutOfBoundsException(
                "curlevel="+curlevel + ", actions="+getActions().size());
        }
        return (EscalationAction)getActions().get(curlevel);
    }
/*
    public JSONObject toJSON () throws JSONException
    {
        JSONArray actionArray = new JSONArray();
        for (Iterator i = actions.iterator(); i.hasNext(); ) {
            EscalationAction action = (EscalationAction)i.next();
            actionArray.put(action.toJSON());
        }
        return new JSONObject()
                .put("name", name)
                .put("allowPause", allowPause)
                .put("maxWaitTime", maxWaitTime)
                .put("notifyAll", notifyAll)
                .put("creationTime", creationTime)
                .put("modifiedTime", modifiedTime)
                .put("actions", actionArray);
    }
*/
    public boolean equals(Object obj) {
        if (!(obj instanceof Escalation) || !super.equals(obj)) {
            return false;
        }
        Escalation o = (Escalation)obj;
        return (getName() == o.getName() ||
                (getName()!=null && o.getName()!=null &&
                 getName().equals(o.getName())));
    }

    public int hashCode() {
        int result = super.hashCode();

        result = 37*result + (getName()!=null ? getName().hashCode() : 0);

        return result;
    }

    public String toString() {
        return "(id=" + getId() + ", name=" + name + ", allowPause=" +
               allowPause + ", maxWaitTime="+ maxWaitTime + ", notifyAll=" +
               notifyAll + ", created=" + creationTime + ")";
    }
}


