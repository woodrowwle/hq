/*
 * NOTE: This copyright does *not* cover user programs that use HQ
 * program services by normal system calls through the application
 * program interfaces provided as part of the Hyperic Plug-in Development
 * Kit or the Hyperic Client Development Kit - this is merely considered
 * normal use of the program, and does *not* fall under the heading of
 * "derived work".
 * 
 * Copyright (C) [2004-2008], Hyperic, Inc.
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

package org.hyperic.hq.grouping.critters;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.hibernate.Query;
import org.hibernate.type.IntegerType;
import org.hyperic.hq.appdef.shared.AppdefEntityConstants;
import org.hyperic.hq.authz.shared.MixedGroupType;
import org.hyperic.hq.grouping.Critter;
import org.hyperic.hq.grouping.CritterDump;
import org.hyperic.hq.grouping.CritterTranslationContext;
import org.hyperic.hq.grouping.CritterType;
import org.hyperic.hq.grouping.GroupException;
import org.hyperic.hq.grouping.prop.CritterPropType;
import org.hyperic.hq.grouping.prop.EnumCritterProp;

public class MixedGroupTypeCritterType extends BaseCritterType {
    
    private static final String ENUM_PROP_NAME = "mixedTypeId";
    
    public MixedGroupTypeCritterType() {
        super();
        initialize("org.hyperic.hq.grouping.Resources", "mixedGroupType"); 
        addPropDescription(ENUM_PROP_NAME, CritterPropType.ENUM);
    }

    public Critter compose(CritterDump dump) throws GroupException {
        throw new GroupException("compose is not supported");
    }

    public void decompose(Critter critter, CritterDump dump)
        throws GroupException {
        throw new GroupException("decompose is not supported");
    }

    public boolean isSystem() {
        return true;
    }

    public Critter newInstance(List props) throws GroupException {
        validate(props);
        EnumCritterProp prop = (EnumCritterProp)props.get(0);
        MixedGroupType type = (MixedGroupType)prop.getEnum();
        return new MixedGroupTypeCritter(type, this);
    }

    public Critter newInstance(MixedGroupType type) throws GroupException {
        return new MixedGroupTypeCritter(type, this);
    }
    
    /**
     * Fetches all Groups (not members) from the EAM_RESOURCE table
     * joined by instance_id from EAM_RESOURCE_GROUP
     */
    class MixedGroupTypeCritter implements Critter {
        
        private final List _groupTypes = new ArrayList();
        private final List _props; 
        private final MixedGroupTypeCritterType _type;
        
        /**
         * @param groupTypes List of Integers which represent grouptypes
         */
        public MixedGroupTypeCritter(MixedGroupType groupType,
                                     MixedGroupTypeCritterType type) {
            setIdList(groupType.getAppdefEntityTypes());
            _type = type;
            List props = new ArrayList();
            props.add(new EnumCritterProp(
                type.getComponentName(ENUM_PROP_NAME), groupType));
            _props = Collections.unmodifiableList(props);
        }
        
        private void setIdList(List groupTypes) {
            for (Iterator i=groupTypes.iterator(); i.hasNext(); ) {
                Integer type = (Integer)i.next();
                _groupTypes.add(type);
            }
        }

        public List getProps() {
            return _props;
        }

        public void bindSqlParams(CritterTranslationContext ctx, Query q) {
            q.setParameterList(ctx.escape("groupTypes"), _groupTypes,
                new IntegerType());
        }
        
        public String getConfig() {
            Object[] args = { _groupTypes };
            return _type.getInstanceConfig().format(args);
        }

        public CritterType getCritterType() {
            return _type;
        }

        public String getSql(CritterTranslationContext ctx, String resourceAlias) {
            return "(@grp@.grouptype in (:@groupTypes@))";
        }

        public String getSqlJoins(CritterTranslationContext ctx,
                                  String resourceAlias) {
            return new StringBuilder()
                .append("JOIN EAM_RESOURCE_GROUP @grp@ on ")
                .append(resourceAlias)
                .append(".instance_id = @grp@.id ").toString();
        }

        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof MixedGroupTypeCritter)) return false;
            
            // make assumptions explicit
            assert _groupTypes != null;
            
            MixedGroupTypeCritter critter = (MixedGroupTypeCritter) other;
            if (!_groupTypes.equals(critter._groupTypes)) return false;
            return true;
        }

        public int hashCode() {
            int rtn = 0;
            if (_groupTypes == null) {
                return 0;
            }
            for (Iterator i=_groupTypes.iterator(); i.hasNext(); ) {
                rtn += i.next().hashCode();
            }
            return rtn;
        }
    }
}
