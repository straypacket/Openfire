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

package net.skillupjapan.openfire.plugin;

import java.io.File;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

import org.jivesoftware.openfire.MessageRouter;
import org.jivesoftware.openfire.IQRouter;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.interceptor.InterceptorManager;
import org.jivesoftware.openfire.interceptor.PacketInterceptor;
import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.jivesoftware.openfire.muc.MultiUserChatManager;
import org.jivesoftware.openfire.muc.MultiUserChatService;
import org.jivesoftware.openfire.muc.MUCRoom;
import org.jivesoftware.openfire.muc.MUCRole;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.openfire.PresenceManager;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.util.JiveGlobals;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;
import org.xmpp.packet.IQ;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;

import org.dom4j.Element;
import org.dom4j.Namespace;

/**
 * Packet handler plugin.
 * 
 * @author Daniel Pereira
 */
public class SUJMessageHandlerPlugin implements Plugin, PacketInterceptor {

	private static final Logger Log = LoggerFactory.getLogger(SUJMessageHandlerPlugin.class);

    /**
     * The expected value is a boolean
     */
    public static final String REGISTER_HANDLER_ENABLED_PROPERTY = "plugin.sujMessageHandler.register.handler.enabled";

    /**
     * The expected value is a boolean
     */
    public static final String DATE_HANDLER_ENABLED_PROPERTY = "plugin.sujMessageHandler.date.handler.enabled";

    /**
     * The expected value is a boolean
     */
    public static final String UNREAD_HANDLER_ENABLED_PROPERTY = "plugin.sujMessageHandler.unread.handler.enabled";

    /**
     * The expected value is a boolean
     */
    public static final String OUT_OF_MUC_HANDLER_ENABLED_PROPERTY = "plugin.sujMessageHandler.outofmuc.handler.enabled";

    /**
     * The expected value is a boolean
     */
    public static final String OFFLINE_MUC_HANDLER_ENABLED_PROPERTY = "plugin.sujMessageHandler.offlinemuc.handler.enabled";


    /**
     * The expected value is a boolean
     */
    public static final String SECOND_DEVICE_HANDLER_ENABLED_PROPERTY = "plugin.sujMessageHandler.2nd_device.handler.enabled";

    /**
     * the hook into the inteceptor chain
     */
    private InterceptorManager interceptorManager;

    /**
     * the hook used for the group manager
     */
    private MultiUserChatManager mucManager;

    /**
     * the hook used for the presence manager
     */
    private PresenceManager presenceManager;

    /**
     * the hook used for the user manager
     */
    private UserManager userManager;

    /**
     * the hook used to handle groups
     */
    private  MultiUserChatService mucService;

    /**
     * used to send messages
     */
    private MessageRouter messageRouter;

    /**
     * used to send iq packets
     */
    private IQRouter iqRouter;

    /**
     * delegate that does the real work of this plugin
     */
    private SUJMessageHandler sujMessageHandler;

    /**
     * flag if Registration parameters should be handled.
     */
    private boolean registerHandlerEnabled;

    /**
     * flag if Date appending should be handled.
     */
    private boolean dateHandlerEnabled;

    /**
     * flag if Unread queries should be handled.
     */
    private boolean unreadHandlerEnabled;

    /**
     * flag if MUC messages should be handled.
     */
    private boolean outOfMucHandlerEnabled;

    /**
     * flag if MUC messages should be handled.
     */
    private boolean offlineMucHandlerEnabled;

    /**
     * flag if second device messages should be handled.
     */
    private boolean secondDeviceHandlerEnabled;

    /**
     * Hash with all the rooms
     */
    private Map<JID, MUCRoom> rooms = new ConcurrentHashMap<JID, MUCRoom>();

    public SUJMessageHandlerPlugin() {
        sujMessageHandler = new SUJMessageHandler();
        interceptorManager = InterceptorManager.getInstance();
        mucManager = XMPPServer.getInstance().getMultiUserChatManager();
        presenceManager = XMPPServer.getInstance().getPresenceManager();
        userManager = XMPPServer.getInstance().getUserManager();

        messageRouter = XMPPServer.getInstance().getMessageRouter();
        iqRouter = XMPPServer.getInstance().getIQRouter();
        mucService = mucManager.getMultiUserChatService("conference");

        // Initialy populate the hash of rooms in the server
        makeHashByJID();
    }

    /**
     * Restores the plugin defaults.
     */
    public void reset() {     
        setRegistHandlerEnabled(false);
        setDateHandlerEnabled(false);
        setUnreadHandlerEnabled(false);
        setOutOfMUCHandlerEnabled(false);
        setOfflineMUCHandlerEnabled(false);
        setSecondDeviceHandlerEnabled(false);
    }

    public boolean isRegisterHandlerEnabled() {
        return registerHandlerEnabled;
    }

    public boolean isDateHandlerEnabled() {
        return dateHandlerEnabled;
    }

    public boolean isUnreadHandlerEnabled() {
        return unreadHandlerEnabled;
    }

    public boolean isOutOfMUCHandlerEnabled() {
        return outOfMucHandlerEnabled;
    }

    public boolean isOfflineMUCHandlerEnabled() {
        return offlineMucHandlerEnabled;
    }

    public boolean isSecondDeviceHandlerEnabled() {
        return secondDeviceHandlerEnabled;
    }

    public void setRegistHandlerEnabled(boolean enabled) {
        registerHandlerEnabled = enabled;
        JiveGlobals.setProperty(REGISTER_HANDLER_ENABLED_PROPERTY,
                enabled ? "true" : "false");
    }

    public void setDateHandlerEnabled(boolean enabled) {
        dateHandlerEnabled = enabled;
        JiveGlobals.setProperty(DATE_HANDLER_ENABLED_PROPERTY,
                enabled ? "true" : "false");
    }

    public void setUnreadHandlerEnabled(boolean enabled) {
        unreadHandlerEnabled = enabled;
        JiveGlobals.setProperty(UNREAD_HANDLER_ENABLED_PROPERTY,
                enabled ? "true" : "false");
    }

    public void setOutOfMUCHandlerEnabled(boolean enabled) {
        outOfMucHandlerEnabled = enabled;
        JiveGlobals.setProperty(OUT_OF_MUC_HANDLER_ENABLED_PROPERTY,
                enabled ? "true" : "false");
    }

    public void setOfflineMUCHandlerEnabled(boolean enabled) {
        offlineMucHandlerEnabled = enabled;
        JiveGlobals.setProperty(OFFLINE_MUC_HANDLER_ENABLED_PROPERTY,
                enabled ? "true" : "false");
    }

    public void setSecondDeviceHandlerEnabled(boolean enabled) {
        secondDeviceHandlerEnabled = enabled;
        JiveGlobals.setProperty(SECOND_DEVICE_HANDLER_ENABLED_PROPERTY,
                enabled ? "true" : "false");
    }

    public void initializePlugin(PluginManager pManager, File pluginDirectory) {
        // configure this plugin
        initHandlers();

        // register with interceptor manager
        interceptorManager.addInterceptor(this);
    }

    private void initHandlers() {
        // default to false
        registerHandlerEnabled = JiveGlobals.getBooleanProperty(
                REGISTER_HANDLER_ENABLED_PROPERTY, false);   
        dateHandlerEnabled = JiveGlobals.getBooleanProperty(
                DATE_HANDLER_ENABLED_PROPERTY, false);
        unreadHandlerEnabled = JiveGlobals.getBooleanProperty(
                UNREAD_HANDLER_ENABLED_PROPERTY, false); 
        outOfMucHandlerEnabled = JiveGlobals.getBooleanProperty(
                OUT_OF_MUC_HANDLER_ENABLED_PROPERTY, false); 
        offlineMucHandlerEnabled = JiveGlobals.getBooleanProperty(
                OFFLINE_MUC_HANDLER_ENABLED_PROPERTY, false);
        secondDeviceHandlerEnabled = JiveGlobals.getBooleanProperty(
                SECOND_DEVICE_HANDLER_ENABLED_PROPERTY, false); 
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

        //if ((packet instanceof Message)) {
        //    Log.warn("Got message: " + packet.toString());
        //}

        /**
         * Ignore forwarded messages
         */
        if ((packet instanceof Message) && (((Message) packet).getElement().attributeValue("forwarded") != null)) {
            if (Log.isDebugEnabled()) {
                Log.warn("Ignoring forwarded message: " + packet.toString());
            }
            return;
        }

        /**
         * Adds date to all conversation packets, in the same fashion
         * as an offline packet, as described below.
         *
         * Returned message:
         * <message from='romeo@montague.net/orchard' to='juliet@capulet.com' type='chat'>
         *    <body>
         *        O blessed, blessed night! I am afeard.
         *        Being in night, all this is but a dream,
         *        Too flattering-sweet to be substantial.
         *    </body>
         *    <delay xmlns='urn:xmpp:delay' from='capulet.com' stamp='2002-09-10T23:08:25Z'/>
         * </message>
         */
        if (isValidAddDataPacket(packet, read, processed)) {
            Packet original = packet;

            // Add date to message packets
            Packet dated_packet = sujMessageHandler.addDate(packet);
            original = dated_packet;

            if (Log.isDebugEnabled()) {
                Log.debug("SUJ Message Handler: modified packet:"
                        + original.toString());
            }
        }
        
        /**
        * An IQ packet can send a query for the count of messages for the given rooms
        *
        * Example query:
        * <iq type='get' id='xdfw-1'>
        *   <query xmlns='xmpp:join:msgq'>
        *      <room room_jid='rm_015551123@conference.mediline-jabber01' since='CCYY-MM-DDThh:mm:ss[.sss]TZD' />
        *      <room room_jid='rm_515156520@conference.mediline-jabber01' since='2014-04-11T09:10:59.303+0000' />
        *      <room room_jid='pc_845169551@conference.mediline-jabber01' since='1970-01-01T00:00:00Z' />
        *      ...
        *   </query>
        * </iq>
        *
        * Replied query:
        * <iq type='result' id='xdfw-1'>
        *    <query xmlns='xmpp:join:msgq'>
        *       <room room_jid='rm_015551123@conference.mediline-jabber01'>
        *           <msg_count>3</msg_count>
        *       </room>
        *       <room room_jid='rm_515156520@conference.mediline-jabber01'>
        *           <msg_count>0</msg_count>
        *       </room>
        *       ...
        *    </query>
        * </iq>
        */
        if (isValidMsgQueryPacket(packet, read, processed)) {
            Element child = ((IQ) packet).getChildElement();
            if (child != null) {
                String uri = ((Element) child).getNamespaceURI().toString();
                String qualifiedname = ((Element) child).getQualifiedName().toString();

                if (uri.equals("xmpp:join:msgq") && qualifiedname.equals("query")) {
                    //Get the fields we are interested in (all the "room" requests)
                    List children = ((IQ) packet).getChildElement().elements("room");
                    if (!children.isEmpty()) {
                        Iterator fieldElems = children.iterator();
                        int rescount = 0;

                        // Create reply
                        IQ reply = ((IQ) packet).createResultIQ(((IQ) packet)).createCopy();
                        reply.setChildElement("query", "xmpp:join:msgq");

                        Element cur;
                        String qroom;
                        String qdate;
                        while (fieldElems.hasNext()) {
                            cur = (Element) fieldElems.next();
                            qroom = cur.attribute("room_jid").getValue();
                            qdate = cur.attribute("since").getValue();
                            rescount = sujMessageHandler.getArchivedMessageCount(qroom, qdate);

                            // Choo choo, makes the train
                            ((IQ) reply).getChildElement().addElement("room").addAttribute("room_jid",qroom).addElement("msg_count").addText(Integer.toString(rescount));

                            if (Log.isDebugEnabled()) {
                                Log.warn("I got the query token! Search for messages in " + qroom + " older than " + qdate + ": " + rescount + " messages ");
                            }
                        }

                        if (Log.isDebugEnabled()) {
                            Log.warn("Sending IQ reply: "
                                + reply.toString());
                        }
                        // Send packet
                        try {
                            iqRouter.route(reply);
                        }
                        catch (Exception rf) {
                            Log.error ("Routing failed for IQ packet: " 
                                + reply.toString());
                        }

                    }
                    else {
                        Log.warn("Empty MsgQueryPacket!");
                    }
                }
            }
        }

        /**
        * Hijack the registration request and parse information related to the JOIN service
        * This information is sent in the body of the register query.
        *
        * Example registration packet:
        * <iq type="set" id="purple343f3f96" from="mediline-jabber01/68b64d0a">
        *   <query xmlns="jabber:iq:register">
        *     <x xmlns="jabber:x:data" type="submit">
        *       <field var="FORM_TYPE">
        *         <value>jabber:iq:register</value>
        *       </field>
        *       ...
        *     </x>
        *   </query>
        * </iq>
        */
        if (isValidRegisterPacket(packet, read, processed)) {
            String uri = ((IQ) packet).getChildElement().getNamespaceURI().toString();
            String qualifiedname = ((IQ) packet).getChildElement().getQualifiedName().toString();

            if (uri.equals("jabber:iq:register") && qualifiedname.equals("query")) {
                String innerUri = ((IQ) packet).getChildElement().element("x").getNamespaceURI().toString();
                String innerQualifiedname = ((IQ) packet).getChildElement().element("x").getQualifiedName().toString();
                String innerType = ((IQ) packet).getChildElement().element("x").attribute("type").getValue().toString();
                if (Log.isDebugEnabled()) {
                    Log.warn("Possible register packet... ");
                }

                if ( innerUri.equals("jabber:x:data") && innerQualifiedname.equals("x") && innerType.equals("submit") ) {
                    if (Log.isDebugEnabled()) {
                        Log.warn("We got a registration submission!!");
                        Log.warn("packet:" + packet.toString());
                    }

                    //Get the field we are interested in, in this example we use the "email" attribute as the token
                    Iterator fieldElems = ((IQ) packet).getChildElement().element("x").elementIterator();
                    Element cur;
                    while (fieldElems.hasNext()) {
                        cur = (Element) fieldElems.next();
                        if (("email").equals(cur.attribute("var").getValue())) {
                            Log.warn("I got the token! It's " + cur.getStringValue());
                        }
                    }
                    
                    try {
                        if (Log.isDebugEnabled()) {
                            Log.warn("Sleeping ...");
                        }

                        //Thread.sleep(1000);
                        sujMessageHandler.googleReq();
                        
                        if (Log.isDebugEnabled()) {
                            Log.warn("Slept");
                        }
                    } catch (Exception ie) {
                        if (Log.isDebugEnabled()) {
                            Log.warn("Problem sleeping: " + ie.toString());
                        }
                    }
                }
            }
        }

        /**
        * Hijack the MUC requests and check if notifications should be sent when:
        * - user is online but there's ongoing activity on a MUC the user belongs to (live notifications/IQ messages)
        * - user if offline and there are unread messages (push notifications)
        *
        * Example MUC activity packet:
        * <message content="text" to="gc_930f3e070d7@conference.mediline" type="groupchat" from="test2@mediline/7e3">
        *   <body>Message</body>
        *   <delay xmlns="urn:xmpp:delay" from="test2@mediline/7e3" stamp="2010-02-12T13:36:22.715Z"/>
        * </message>
        */
        if (isValidOutOfMUCPacket(packet, read, processed)){
            //Log.warn("We got a MUC packet!" + packet.toString());

            // This needs to be improved!!!
            // Right now, I'm only remaking the map if new chat messages from an unknown (new) room appear
            // A listener for new MUC rooms should be handling this
            MUCRoom room = rooms.get(new JID(packet.getElement().attribute("to").getValue()));
            if (room != null) {
                makeHashByJID();
                room = rooms.get(new JID(packet.getElement().attribute("to").getValue()));
            }

            Iterator room_users = room.getMembers().iterator();
            JID user;
            while (room_users.hasNext()){
                user = (JID) room_users.next();
                MUCRole role = room.getOccupantByFullJID(user);

                // User not in chatroom
                if (role == null){
                    if (Log.isDebugEnabled()) {
                        Log.warn("User " + user.toString() + " not in chatroom!");
                    }
                    try {
                        // If user is online still send the message (soft-forward)
                        if (presenceManager.isAvailable(userManager.getUser(user.getNode()))) {
                            if (Log.isDebugEnabled()) {
                                Log.warn("Forwarding message to user " + user.toString() );
                            }
                            Message forwardMsg = (Message) packet.createCopy();
                            forwardMsg.setFrom(new JID(packet.getElement().attribute("to").getValue()));
                            forwardMsg.setTo(user);
                            forwardMsg.getElement().addAttribute("forwarded","1");
                            messageRouter.route(forwardMsg);
                        }
                        else {
                            // If push notifications are enabled
                            if (offlineMucHandlerEnabled) {
                                // Send push notification to user, call you favouritest APN
                                // ... Good luck! :D
                                if (Log.isDebugEnabled()) {
                                    Log.warn("Sending push notification to user " + user.toString() );
                                }
                            }

                        }
                    } catch(Exception e) {
                        Log.warn("User " + user.getNode() + " not found: " + e);
                    }
                }
            }
        }

        /**
         * Handles the second device check requests. This request is received
         * without the client being authenticated with the server.
         *
         * Received message:
         * <iq type="get" id="purple3436">
         *   <query xmlns="jabber:join:2nd_device">
         *     <id>username</id>
         *     <password>drowssap</password>
         *   </query>
         * </iq>
         * 
         * A successful reply is:
         * <iq type="result" id="purple343f3f96">
         *   <query xmlns="jabber:join:2nd_device">
         *     <jid>test2@mediline</jid>
         *   </query>
         * </iq>
         *
         * A failed password reply is:
         * <iq type="error" id="purple343f3f96">
         *   <error type="cancel">
         *     <not-allowed xmlns="jabber:join:2nd_device"/>
         *   </error>
         * </iq>
         *
         * An empty JID and secondID matchs reply is:
         * <iq type="error" id="purple343f3f96">
         *   <error type="cancel">
         *     <not-acceptable xmlns="jabber:join:2nd_device"/>
         *   </error>
         * </iq>
         */
        if (isValidCheck2ndDevicePacket(packet, read, processed)) {
            String uri = ((IQ) packet).getChildElement().getNamespaceURI().toString();
            String qualifiedname = ((IQ) packet).getChildElement().getQualifiedName().toString();
            String jid = null;

            if (uri.equals("jabber:join:2nd_device") && qualifiedname.equals("query")) {
                
                //Get the id and password fields
                String id = ((IQ) packet).getChildElement().element("id").getStringValue();
                String password = ((IQ) packet).getChildElement().element("password").getStringValue();

                Log.warn("Just got a 2nd device registration IQ with username " + id + " and password " + password);
                
                try {
                    jid = sujMessageHandler.getSecondDeviceJID(id, password);
                } catch (Exception ie) {
                    if (Log.isDebugEnabled()) {
                        Log.warn("Problem on request: " + ie.toString());
                    }
                }

                // Build reply
                IQ replyPacket = ((IQ) packet).createResultIQ(((IQ) packet)).createCopy();

                if (jid == null) {
                    ((IQ) replyPacket).setType(IQ.Type.error);
                    ((IQ) replyPacket).getElement().addElement("error").addAttribute("type", "cancel").addElement("not-acceptable").addNamespace("", "urn:ietf:params:xml:ns:xmpp-stanzas");
                }
                else if (jid == "invalid username or password") {
                    ((IQ) replyPacket).setType(IQ.Type.error);
                    ((IQ) replyPacket).getElement().addElement("error").addAttribute("type", "cancel").addElement("not-allowed").addNamespace("", "urn:ietf:params:xml:ns:xmpp-stanzas");
                }
                else {
                    ((IQ) replyPacket).setChildElement("query", "xmpp:join:2nd_device");
                    ((IQ) replyPacket).getChildElement().addElement("jid").addText(jid);
                }

                Log.warn("Reply: " + replyPacket.toString());

                // Send packet
                try {
                    iqRouter.route(replyPacket);
                }
                catch (Exception rf) {
                    Log.error ("Routing failed for IQ getting 2nd device packet: " 
                        + replyPacket.toString());
                }
            }
        }

        /**
         * Handles the second device setup requests.
         *
         * Received message:
         * <iq type="set" id="purple3436">
         *   <query xmlns="jabber:join:2nd_device">
         *     <id>username</id>
         *     <password>drowssap</password>
         *     <jid>aa@bbb.com<jid>
         *   </query>
         * </iq>
         *
         * On ok, send:
         * <iq type="result" id="purple343f3f96">
         *   <result>
         *     ok
         *   </result>
         * </iq>    
         *
         * An empty JID and secondID matchs reply is:
         * <iq type="error" id="purple343f3f96">
         *   <error type="cancel">
         *     <not-acceptable xmlns="jabber:join:2nd_device"/>
         *   </error>
         * </iq>         
         */
        if (isValidCreate2ndDevicePacket(packet, read, processed)) {
            String uri = ((IQ) packet).getChildElement().getNamespaceURI().toString();
            String qualifiedname = ((IQ) packet).getChildElement().getQualifiedName().toString();

            if (uri.equals("jabber:join:2nd_device") && qualifiedname.equals("query")) {
                
                //Get the id and password fields
                String id = ((IQ) packet).getChildElement().element("id").getStringValue();
                String password = ((IQ) packet).getChildElement().element("password").getStringValue();
                String jid = ((IQ) packet).Element().attribute("from").getValue();
                String query = null;

                Log.warn("Just got a 2nd device registration IQ with username " + id + " password " + password + " and jid " + jid);
                
                try {
                    query = sujMessageHandler.setSecondDeviceJID(id, password, jid);
                } catch (Exception ie) {
                    if (Log.isDebugEnabled()) {
                        Log.warn("Problem on request: " + ie.toString());
                    }
                }

                // Build reply
                IQ replyPacket = ((IQ) packet).createResultIQ(((IQ) packet)).createCopy();

                if (query == null) {
                    ((IQ) replyPacket).setType(IQ.Type.error);
                    ((IQ) replyPacket).getElement().addElement("error").addAttribute("type", "cancel").addElement("conflict").addNamespace("", "urn:ietf:params:xml:ns:xmpp-stanzas");
                }
                else {
                    ((IQ) replyPacket).setChildElement("query", "xmpp:join:2nd_device");
                }

                Log.warn("Reply: " + replyPacket.toString());

                // Send packet
                try {
                    iqRouter.route(replyPacket);
                }
                catch (Exception rf) {
                    Log.error ("Routing failed for IQ setting 2nd device packet: " 
                        + replyPacket.toString());
                }
            }
        }
    }

    /**
     * Build a map with JID, MUCRoom from all chatrooms in the system
     */
    private void makeHashByJID() {
        Iterator mucs = mucService.getChatRooms().iterator();
        MUCRoom cr;
        while (mucs.hasNext()) {
            cr = (MUCRoom) mucs.next();
            rooms.put((JID) cr.getJID(), (MUCRoom) cr);
        }
    }

    private boolean isValidAddDataPacket(Packet packet, boolean read, boolean processed) {
        return  dateHandlerEnabled
                && !processed
                && read
                && packet instanceof Message;
    }

    private boolean isValidMsgQueryPacket(Packet packet, boolean read, boolean processed) {
        return  unreadHandlerEnabled
                && !processed
                && read
                && packet instanceof IQ
                && ((IQ)packet).getType().equals(IQ.Type.get);
    }

    private boolean isValidRegisterPacket(Packet packet, boolean read, boolean processed) {
        return  registerHandlerEnabled
                && !processed
                && read
                && packet instanceof IQ
                && ((IQ)packet).getType().equals(IQ.Type.set);
    }

    private boolean isValidOutOfMUCPacket(Packet packet, boolean read, boolean processed) {
        return  outOfMucHandlerEnabled
                && !processed
                && read
                && packet instanceof Message
                && ((Message)packet).getType().equals(Message.Type.groupchat);
    }

    private boolean isValidCheck2ndDevicePacket(Packet packet, boolean read, boolean processed) {
        return  secondDeviceHandlerEnabled
                && !processed
                && read
                && packet instanceof IQ
                && ((IQ)packet).getType().equals(IQ.Type.get);
    }

    private boolean isValidCreate2ndDevicePacket(Packet packet, boolean read, boolean processed) {
        return  secondDeviceHandlerEnabled
                && !processed
                && read
                && packet instanceof IQ
                && ((IQ)packet).getType().equals(IQ.Type.set);
    }
}