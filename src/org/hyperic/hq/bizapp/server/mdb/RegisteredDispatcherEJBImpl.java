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

package org.hyperic.hq.bizapp.server.mdb;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.ejb.MessageDrivenBean;
import javax.ejb.MessageDrivenContext;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.ObjectMessage;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hyperic.hq.bizapp.server.trigger.conditional.MultiConditionTrigger;
import org.hyperic.hq.common.util.Messenger;
import org.hyperic.hq.events.AbstractEvent;
import org.hyperic.hq.events.ActionExecuteException;
import org.hyperic.hq.events.EventConstants;
import org.hyperic.hq.events.EventTypeException;
import org.hyperic.hq.events.FlushStateEvent;
import org.hyperic.hq.events.TriggerInterface;
import org.hyperic.hq.events.ext.RegisteredTriggers;


/** The RegisteredDispatcher Message-Drive Bean registers Triggers and
 * dispatches events to them
 * <p>
 *
 * </p>
 * @ejb:bean name="RegisteredDispatcher"
 *      jndi-name="ejb/event/RegisteredDispatcher"
 *      local-jndi-name="LocalRegisteredDispatcher"
 *      transaction-type="Container"
 *      acknowledge-mode="Auto-acknowledge"
 *      destination-type="javax.jms.Topic"
 *
 * @jboss:destination-jndi-name name="topic/eventsTopic"
 *
 */

public class RegisteredDispatcherEJBImpl 
    implements MessageDrivenBean, MessageListener 
{
    private final Log log =
        LogFactory.getLog(RegisteredDispatcherEJBImpl.class);

    /**
     * Dispatch the event to interested triggers.
     * 
     * @param event The event.
     * @param visitedMCTriggers The set of visited multicondition triggers 
     *                          that will be updated if a trigger of this type 
     *                          processes this event.
     */
    private void dispatchEvent(AbstractEvent event, Set visitedMCTriggers) 
        throws InterruptedException {        
        // Get interested triggers
        Collection triggers =
            RegisteredTriggers.getInterestedTriggers(event);
        
        //log.debug("There are " + triggers.size() + " registered for event");

        // Dispatch to each trigger
        for (Iterator i = triggers.iterator(); i.hasNext(); ) {
            TriggerInterface trigger = (TriggerInterface) i.next();
            try {
                updateVisitedMCTriggersSet(visitedMCTriggers, trigger);
                trigger.processEvent(event);                
            } catch (ActionExecuteException e) {
                // Log error
                log.error("ProcessEvent failed to execute action", e);
            } catch (EventTypeException e) {
                // The trigger was not meant to process this event
                log.debug("dispatchEvent dispatched to trigger (" +
                        trigger.getClass() + " that's not " +
                        "configured to handle this type of event: " +
                        event.getClass());
            }
        }            
        
    }

    private void updateVisitedMCTriggersSet(Set visitedMCTriggers,
                                            TriggerInterface trigger) 
        throws InterruptedException {
        
        if (trigger instanceof MultiConditionTrigger) {
            boolean firstTimeVisited = visitedMCTriggers.add(trigger);

            try {
                if (firstTimeVisited) {
                    ((MultiConditionTrigger)trigger).acquireSharedLock();                        
                }                            
            } catch (InterruptedException e) {
                // failed to acquire shared lock - we will not visit this trigger
                visitedMCTriggers.remove(trigger);
                throw e;
            }
        }
    } 
    
    /**
     * The onMessage method
     */
    public void onMessage(Message inMessage) {
        if (!(inMessage instanceof ObjectMessage)) {
            return;
        }
        
        // Just to be safe, start with a fresh queue.
        Messenger.resetThreadLocalQueue();
        final Set visitedMCTriggers = new HashSet();

        try {
            ObjectMessage om = (ObjectMessage) inMessage;
            Object obj = om.getObject();
                       
            if (obj instanceof AbstractEvent) {
                AbstractEvent event = (AbstractEvent) obj;
                dispatchEvent(event, visitedMCTriggers);
            } else if (obj instanceof Collection) {
                Collection events = (Collection) obj;
                for (Iterator it = events.iterator(); it.hasNext(); ) {
                    AbstractEvent event = (AbstractEvent) it.next();
                    dispatchEvent(event, visitedMCTriggers);
                }
            }
        } catch (JMSException e) {
            log.error("Cannot open message object", e);
        } catch (InterruptedException e) {
            log.info("Thread was interrupted while processing events.");
        } finally {
            try {
                flushStateForVisitedMCTriggers(visitedMCTriggers);
            } catch (Exception e) {
                log.error("Failed to flush state for multi conditional trigger", e);
            }
            
            publishEnqueuedEvents();
        }
    }
    
    private void flushStateForVisitedMCTriggers(Set visitedMCTriggers) 
        throws EventTypeException, ActionExecuteException {
        
        if (!visitedMCTriggers.isEmpty()) {
            FlushStateEvent event = new FlushStateEvent();
            
            for (Iterator it = visitedMCTriggers.iterator(); it.hasNext();) {
                MultiConditionTrigger trigger = (MultiConditionTrigger) it.next();
                                
                try {
                    boolean lockAcquired = false;
                    
                    try {
                        lockAcquired = trigger.upgradeSharedLockToExclusiveLock();
                        
                        if (lockAcquired) {
                            trigger.processEvent(event);                    
                        } else {
                            if (log.isDebugEnabled()) {
                                log.debug("There must be more interesting events "+
                                     "to multicondition alert with trigger id="+
                                	 trigger.getId()+" since it failed to upgrade "+
                                	 "shared lock on flushing state.");    
                            }                            
                        }                        
                    } finally {
                        if (lockAcquired) {
                            trigger.releaseExclusiveLock();
                        }
                    }
                } catch (InterruptedException e) {
                    // move on
                }                 
            }            
        }
        
    }
    
    private void publishEnqueuedEvents() {
        List enqueuedEvents = Messenger.drainEnqueuedMessages();
        
        if (!enqueuedEvents.isEmpty()) {
            final ArrayList eventsToPublish = new ArrayList(enqueuedEvents);

            Messenger sender = new Messenger();
            sender.publishMessage(EventConstants.EVENTS_TOPIC, 
                                  eventsToPublish);   
        }        
    }

    /**
     * @ejb:create-method
     */
    public void ejbCreate() {}
    public void ejbPostCreate() {}
    public void ejbActivate() {}
    public void ejbPassivate() {}

    /**
     * @ejb:remove-method
     */
    public void ejbRemove() {}

    public void setMessageDrivenContext(MessageDrivenContext ctx) {}
}
