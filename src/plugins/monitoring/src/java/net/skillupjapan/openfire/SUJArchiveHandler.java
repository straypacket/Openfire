/**
 * $RCSfile$
 * $Revision: 1594 $
 * $Date: 2005-07-04 18:08:42 +0100 (Mon, 04 Jul 2005) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.io.File;
import java.util.Date;
import java.util.Collection;
import java.util.Collections;
import java.util.regex.PatternSyntaxException;

import org.jivesoftware.openfire.MessageRouter;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.interceptor.InterceptorManager;
import org.jivesoftware.openfire.interceptor.PacketInterceptor;
import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;

import org.jivesoftware.openfire.plugin.MonitoringPlugin;
import org.jivesoftware.openfire.archive.ArchiveSearch;
import org.jivesoftware.openfire.archive.ArchiveSearcher;
import org.jivesoftware.openfire.archive.Conversation;
import org.jivesoftware.openfire.archive.MonitoringConstants;

import org.jivesoftware.database.DbConnectionManager;
/**
 * SUJ Archive Handler plugin.
 * 
 * @author Daniel Pereira
 */
public class SUJArchiveHandler implements Plugin, PacketInterceptor {

    private static final Logger Log = LoggerFactory.getLogger(SUJArchiveHandler.class);

    /**
     * the hook into the inteceptor chain
     */
    private InterceptorManager interceptorManager;

    /**
     * used to send violation notifications
     */
    private MessageRouter messageRouter;

    /**
     * The expected value is a boolean, if true the value of #PATTERNS_PROPERTY
     * will be used for pattern matching.
     */
    public static final String PATTERNS_ENABLED_PROPERTY = "plugin.sujMessageHandler.patterns.enabled";

    /**
     * flag if patterns should be used
     */
    private boolean patternsEnabled;
    
    /**
     * violation notification messages will be from this JID
     */
    private JID violationNotificationFrom;

    /**
     * Use for searching archives
     */
    private static final String MESSAGE_COUNT = "SELECT COUNT(*) FROM ofMessageArchive WHERE conversationID=? AND jidResource=?";

    public void SUJcountConversations(){
    	Date now = new Date();
        Date maxAgeDate = new Date(now.getTime());
        MonitoringPlugin monitPlugin = (MonitoringPlugin) XMPPServer.getInstance().getPluginManager().getPlugin(MonitoringConstants.NAME);
        ArchiveSearcher archiveSearcher = (ArchiveSearcher) monitPlugin.getModule(ArchiveSearcher.class);
        ArchiveSearch search = new ArchiveSearch();
		search.setDateRangeMax(maxAgeDate);
        Collection<Conversation> conversations = archiveSearcher.search(search);
        int conversationDeleted = 0;
        RoomParticipant participant = null;
        Connection con = null;

        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            //participant = SOMETHING;
            pstmt = con.prepareStatement(MESSAGE_COUNT);

            for (Conversation conversation : conversations) {
                Log.debug("Just got: " + conversation.getConversationID() + " with date: " + conversation.getStartDate()
                        + " older than: " + maxAgeDate);
                pstmt.setLong(1, participant.conversationID);
                pstmt.setString(2, participant.user.getResource() == null ? " " : participant.user.getResource());
                pstmt.execute();
            }
        }
        catch (Exception e) {
            Log.error(e.getMessage(), e);
        } finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }

    public SUJArchiveHandler() {
        interceptorManager = InterceptorManager.getInstance();
        violationNotificationFrom = new JID(XMPPServer.getInstance()
                .getServerInfo().getXMPPDomain());
        messageRouter = XMPPServer.getInstance().getMessageRouter();
    }

    /**
     * Restores the plugin defaults.
     */
    public void reset() {
		//
    }

    public void setPatternsEnabled(boolean enabled) {
        patternsEnabled = enabled;
        JiveGlobals.setProperty(PATTERNS_ENABLED_PROPERTY, enabled ? "true"
                : "false");
    }

    public boolean isPatternsEnabled() {
        return patternsEnabled;
    }

    public void initializePlugin(PluginManager pManager, File pluginDirectory) {
        // configure this plugin
        initFilter();

        // register with interceptor manager
        interceptorManager.addInterceptor(this);
    }

    private void initFilter() {

        // default to true
        patternsEnabled = JiveGlobals.getBooleanProperty(
                PATTERNS_ENABLED_PROPERTY, true);  
    }

    /**
     * @see org.jivesoftware.openfire.container.Plugin#destroyPlugin()
     */
    public void destroyPlugin() {
        // unregister with interceptor manager
        interceptorManager.removeInterceptor(this);
    }

    public void interceptPacket(Packet packet, Session session, boolean read,
            boolean processed) throws PacketRejectedException {

        if (isValidUnreadQueryPacket(packet, read, processed)) {

            Packet original = packet;

            if (Log.isDebugEnabled()) {
                Log.debug("SUJ Archive Handler: modified packet:"
                        + original.toString());
            }
        }
        
        if (isValidHistoryQueryPacket(packet, read, processed)) {
            /*
            IQ original = packet;

            Log.error("SUJ Message Handler: MSG Query packet"
                        + original.toString());
            Log.error("Extension:"
                        + original.getExtension());

            PacketExtension pkt_ex = original.getExtension();
            boolean res = original.deleteExtension();

            // Same?
            Element elem = pkt_ex.getElement();
            Element child_elem = original.getChildElement();

            */


            // Create reply
            //IQ reply = original.createResultIQ();

        }

    }

    private boolean isValidUnreadQueryPacket(Packet packet, boolean read, boolean processed) {
        return  !processed
                && read
                && (packet instanceof Message || (packet instanceof Presence));
    }

    private boolean isValidHistoryQueryPacket(Packet packet, boolean read, boolean processed) {
        return  !processed
                && read;
                //&& packet instanceof IQ 
                //&& packet.getType() == "get" 
                //&& packet.isRequest();
    }

	private static class RoomParticipant {
		private long conversationID = -1;
		private JID user;
		private Date joined;
		private Date left;
	}
}