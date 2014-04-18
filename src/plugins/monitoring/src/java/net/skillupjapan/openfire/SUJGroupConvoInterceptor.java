/**
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2008 Jive Software. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.skillupjapan.openfire;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.cluster.ClusterManager;
import org.jivesoftware.openfire.muc.MUCEventDispatcher;
import org.jivesoftware.openfire.muc.MUCEventListener;
import org.jivesoftware.openfire.muc.MUCRoom;
import org.picocontainer.Startable;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.jivesoftware.openfire.archive.ConversationManager;
import org.jivesoftware.openfire.archive.ConversationEvent;
import org.jivesoftware.openfire.archive.ConversationEventsQueue;

import java.util.Date;

/**
 * Interceptor of MUC events of the local conferencing service. The interceptor is responsible
 * for reacting to messages being sent to rooms.
 *
 * @author Daniel Pereira
 */
public class SUJGroupConvoInterceptor implements MUCEventListener, Startable {

    private static final Logger Log = LoggerFactory.getLogger(SUJArchiveHandler.class);
    private ConversationManager conversationManager;

    public SUJGroupConvoInterceptor(ConversationManager conversationManager) {
        this.conversationManager = conversationManager;
    }

    public void messageReceived(JID roomJID, JID user, String nickname, Message message) {
        Log.warn("SUJGroupConvoInterceptor just got: " + message.toString());
        // // Process this event in the senior cluster member or local JVM when not in a cluster
        // if (ClusterManager.isSeniorClusterMember()) {
        //     conversationManager.processRoomMessage(roomJID, user, nickname, message.getBody(), new Date());
        // }
        // else {
        //     boolean withBody = conversationManager.isRoomArchivingEnabled() && (
        //             conversationManager.getRoomsArchived().isEmpty() ||
        //                     conversationManager.getRoomsArchived().contains(roomJID.getNode()));

        //     ConversationEventsQueue eventsQueue = conversationManager.getConversationEventsQueue();
        //     eventsQueue.addGroupChatEvent(roomJID.toString(),
        //             ConversationEvent.roomMessageReceived(roomJID, user, nickname, withBody ? message.getBody() : null, new Date()));
        // }
    }

    public void roomCreated(JID roomJID) {
        //Do nothing
    }

    public void roomDestroyed(JID roomJID) {
        // Do nothing
    }

    public void occupantJoined(JID roomJID, JID user, String nickname) {
        // Do nothing
    }

    public void occupantLeft(JID roomJID, JID user) {
        // Do nothing
    }

    public void nicknameChanged(JID roomJID, JID user, String oldNickname, String newNickname) {
        // Do nothing
    }
    public void privateMessageRecieved(JID toJID, JID fromJID, Message message) {
        // Do nothing
    }

    public void roomSubjectChanged(JID roomJID, JID user, String newSubject) {
        // Do nothing
    }

    public void start() {
        MUCEventDispatcher.addListener(this);
    }

    public void stop() {
        MUCEventDispatcher.removeListener(this);
        conversationManager = null;
    }
}
