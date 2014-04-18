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

import org.jivesoftware.openfire.MessageRouter;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.interceptor.InterceptorManager;
import org.jivesoftware.openfire.interceptor.PacketInterceptor;
import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.jivesoftware.openfire.session.Session;
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

import org.jivesoftware.database.DbConnectionManager;

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
     * the hook into the inteceptor chain
     */
    private InterceptorManager interceptorManager;

    /**
     * used to send violation notifications
     */
    private MessageRouter messageRouter;

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

    public SUJMessageHandlerPlugin() {
        sujMessageHandler = new SUJMessageHandler();
        interceptorManager = InterceptorManager.getInstance();

        messageRouter = XMPPServer.getInstance().getMessageRouter();
    }

    /**
     * Restores the plugin defaults.
     */
    public void reset() {     
        setRegistHandlerEnabled(false);
    }

    public boolean isRegisterHandlerEnabled() {
        return registerHandlerEnabled;
    }

    public boolean isDateHandlerEnabled() {
        return dateHandlerEnabled;
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

        /**
         * Adds date to normal conversation packets, in the same fashion
         * as an offline packet
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
         * 
         */
        if (isValidMsgQueryPacket(packet, read, processed)) {
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

        /**
        * Register intention
        * <iq type="get" id="purple343f3f95" from="mediline-jabber01/68b64d0a">
        *   <query xmlns="jabber:iq:register"/>
        * </iq>
        * 
        * Registration
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
            IQ.Type type = ((IQ) packet).getType();

            if (uri.equals("jabber:iq:register") && qualifiedname.equals("query") && type.equals(IQ.Type.valueOf("set"))) {
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
                    while (fieldElems.hasNext()) {
                        Element cur = (Element) fieldElems.next();
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
    }

    private boolean isValidAddDataPacket(Packet packet, boolean read, boolean processed) {
        return  dateHandlerEnabled
                && !processed
                && read
                && (packet instanceof Message || packet instanceof Presence);
    }

    private boolean isValidMsgQueryPacket(Packet packet, boolean read, boolean processed) {
        return  !processed
                && read;
                //&& packet instanceof IQ 
                //&& packet.getType() == "get" 
                //&& packet.isRequest();
    }

    private boolean isValidRegisterPacket(Packet packet, boolean read, boolean processed) {
        return  registerHandlerEnabled
                && !processed
                && read
                && packet instanceof IQ;
                //&& packet.Type == IQ.Type.get;
                //&& packet.isRequest();
    }

}