/*******************************************************************************
 * * Copyright (c) 2001-2005 quickfixengine.org All rights reserved. * * This
 * file is part of the QuickFIX FIX Engine * * This file may be distributed
 * under the terms of the quickfixengine.org * license as defined by
 * quickfixengine.org and appearing in the file * LICENSE included in the
 * packaging of this file. * * This file is provided AS IS with NO WARRANTY OF
 * ANY KIND, INCLUDING THE * WARRANTY OF DESIGN, MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE. * * See http://www.quickfixengine.org/LICENSE for
 * licensing information. * * Contact ask@quickfixengine.org if any conditions
 * of this licensing are * not clear to you. *
 ******************************************************************************/

package quickfix.netty;

import net.gleamynode.netty2.Message;
import quickfix.Application;
import quickfix.ConfigError;
import quickfix.LogFactory;
import quickfix.MessageFactory;
import quickfix.MessageStoreFactory;
import quickfix.ScreenLogFactory;
import quickfix.Session;
import quickfix.SessionFactory;
import quickfix.SessionSettings;
import edu.emory.mathcs.backport.java.util.concurrent.BlockingQueue;
import edu.emory.mathcs.backport.java.util.concurrent.LinkedBlockingQueue;

public class SocketInitiator extends AbstractSocketInitiator {
    private BlockingQueue eventQueue;

    public SocketInitiator(Application application, MessageStoreFactory messageStoreFactory,
            SessionSettings settings, MessageFactory messageFactory) throws ConfigError {
        super(application, messageStoreFactory, settings, new ScreenLogFactory(settings),
                messageFactory);
        // This exception is thrown for compatibility reasons
        if (settings == null) {
            throw new ConfigError("no settings");
        }
    }

    public SocketInitiator(Application application, MessageStoreFactory messageStoreFactory,
            SessionSettings settings, LogFactory logFactory, MessageFactory messageFactory)
            throws ConfigError {
        super(application, messageStoreFactory, settings, logFactory, messageFactory);
        // This exception is thrown for compatibility reasons
        if (settings == null) {
            throw new ConfigError("no settings");
        }
    }

    public SocketInitiator(SessionFactory sessionFactory, SessionSettings settings)
            throws ConfigError {
        super(sessionFactory, settings);
        if (settings == null) {
            throw new ConfigError("no settings");
        }
    }

    protected void onInitialize(boolean handleMessageInCaller) {
        if (handleMessageInCaller) {
            eventQueue = new LinkedBlockingQueue();
        }
    }

    protected void onBlock() {
        while (!isStopRequested()) {
            try {
                Object event = eventQueue.take();
                processEvent(event);
            } catch (InterruptedException e) {
                return;
            }
        }
        
        for (int i = 0; i < 5 && isLoggedOn(); i++) {
            try {
                poll();
                Thread.sleep(1000);
            } catch (Exception e) {
                return;
            }
        }
    }

    protected void onStart() {
        // empty
    }

    protected boolean onPoll() {
        if (isStopRequested()) {
            if (!isLoggedOn()) {
                return false;
            }
            if (System.currentTimeMillis() - getStopRequestTimestamp() > 5000L) {
                return false;
            }
        }

        if (eventQueue.peek() != null) {
            processEvent(eventQueue.poll());
        }

        return true;
    }

    private void processEvent(Object event) {
        if (event instanceof Message) {
            processMessage((Message) event);
        } else if (event instanceof Session) {
            processTimerEvent((Session) event);
        }
    }

    protected void onStop() {
        // during stop, process events (like logout) in timer thread
        eventQueue = null;
    }

    protected void onMessage(Message message) {
        if (eventQueue != null) {
            eventQueue.add(message);
        } else {
            processMessage(message);
        }
    }

    protected void onTimerEvent(Session quickfixSession) {
        if (eventQueue != null) {
            eventQueue.add(quickfixSession);
        } else {
            processTimerEvent(quickfixSession);
        }
    }

    protected boolean isHandlingMessageInCallingThread() {
        return true;
    }
}