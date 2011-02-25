/**
 * NOTE: This copyright does *not* cover user programs that use HQ
 * program services by normal system calls through the application
 * program interfaces provided as part of the Hyperic Plug-in Development
 * Kit or the Hyperic Client Development Kit - this is merely considered
 * normal use of the program, and does *not* fall under the heading of
 *  "derived work".
 *
 *  Copyright (C) [2009-2010], VMware, Inc.
 *  This file is part of HQ.
 *
 *  HQ is free software; you can redistribute it and/or modify
 *  it under the terms version 2 of the GNU General Public License as
 *  published by the Free Software Foundation. This program is distributed
 *  in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 *  even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more
 *  details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307
 *  USA.
 *
 */

package org.hyperic.hq.bizapp.server.session;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Version;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.GenericGenerator;

@Entity
@Table(name="EAM_UPDATE_STATUS")
@Cache(usage=CacheConcurrencyStrategy.READ_WRITE)
public class UpdateStatus implements Serializable
{
    @Id
    @GenericGenerator(name = "mygen1", strategy = "increment")  
    @GeneratedValue(generator = "mygen1")  
    @Column(name = "ID")
    private Integer id;

    @Column(name="VERSION_COL")
    @Version
    private Long version;
    
    @Column(name="REPORT",length=4000)
    private String  report;
    
    @Column(name="UPMODE",nullable=false)
    private int     updateModeEnum;
    
    @Column(name="IGNORED",nullable=false)
    private boolean ignored;

    protected UpdateStatus() {
    }
    
    UpdateStatus(String report, UpdateStatusMode mode) {
        this.report         = report;
        updateModeEnum = mode.getCode();
        ignored        = false;
    }
    
    public String getReport() {
        return report;
    }
    
    protected void setReport(String report) {
        this.report = report;
    }
    
    protected int getUpdateModeEnum() {
        return updateModeEnum;
    }
    
    protected void setUpdateModeEnum(int mode) {
        updateModeEnum = mode;
    }
    
    public UpdateStatusMode getMode() {
        return UpdateStatusMode.findByCode(updateModeEnum);
    }
    
    void setMode(UpdateStatusMode mode) {
        updateModeEnum = mode.getCode();
    }
    
    public boolean isIgnored() {
        return ignored;
    }
    
    protected void setIgnored(boolean ignored) {
        this.ignored = ignored;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || !(obj instanceof UpdateStatus)) {
            return false;
        }
        Integer objId = ((UpdateStatus)obj).getId();
  
        return getId() == objId ||
        (getId() != null && 
         objId != null && 
         getId().equals(objId));     
    }

    public int hashCode() {
        int result = 17;
        result = 37*result + (getId() != null ? getId().hashCode() : 0);
        return result;      
    }
    
}
