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

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.util.StringUtils;
import org.jivesoftware.util.XMPPDateTimeFormat;
import org.jivesoftware.database.DbConnectionManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Iterator;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.dom4j.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.JID;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Handling functions
 *
 * @author Daniel Pereira
 */
public class SUJMessageHandler {

    private static final Logger Log = LoggerFactory.getLogger(SUJMessageHandler.class);

    private static final String MESSAGE_COUNT = "SELECT COUNT(*) FROM ofMessageArchive INNER JOIN (SELECT conversationID FROM ofConversation WHERE room=?) as t1 ON ofMessageArchive.conversationID=t1.conversationID AND sentDate>? AND fromJID!=?";

    private static final String RETRIEVE_SECOND_DEVICE_JID = "SELECT jid from ofSecondDevice WHERE secondID=? AND secondPass=?";

    private static final String STORE_SECOND_DEVICE_JID = "INSERT INTO ofSecondDevice (secondID, secondPass, jid) VALUES (?,?,?) ON DUPLICATE KEY UPDATE jid=VALUES(jid)";

    /**
     * A default instance will allow all message content.
     *
     */
    public SUJMessageHandler() {
    }
    
    /**
     * Adds date to packet.
     *
     * @param packet the packet to modify
     * @return packet
     */
    public Packet addDate(Packet p) {        
        Message message = (Message) p;
        Date creationDate = new Date(Long.parseLong(StringUtils.dateToMillis(new java.util.Date()), 10));

        // Add a delayed delivery (XEP-0203) element to the message.
        Element delay = message.addChildElement("delay", "urn:xmpp:delay");
        delay.addAttribute("from", p.getFrom().toString());
        delay.addAttribute("stamp", XMPPDateTimeFormat.format(creationDate));

        return (Packet) message;
    }

    /**
     * Returns the total number of messages that have been archived to the database.
     * 
     * @return the total number of archived messages.
     */
    public int getArchivedMessageCount(String chatgroup, String date, String jid) {
        int messageCount = 0;
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        XMPPDateTimeFormat rd = new XMPPDateTimeFormat();

        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(MESSAGE_COUNT);
            pstmt.setString(1, chatgroup);
            if (date.length() == 0) {
                pstmt.setLong(2, 0);
            }
            else{
                // Adding half a second buffer to smooth differences between write times in the DB tables
                pstmt.setLong(2, rd.parseString(date).getTime()+500);
            }
            pstmt.setString(3, jid);

            Log.warn("Query: " + pstmt.toString());

            rs = pstmt.executeQuery();
            if (rs.next()) {
                messageCount = rs.getInt(1);
            }

            Log.warn("Resulting count: " + messageCount);

        } catch (SQLException sqle) {
            Log.error(sqle.getMessage(), sqle);
        } catch (ParseException pd) {
            Log.error("Error parsing date!");
        } finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        return messageCount;
    }

    /**
     * Makes an HTTP request.
     *
     * @param none
     * @return boolean
     */
    public static boolean googleReq() {
        String url = "http://www.google.com";
        String USER_AGENT = "Mozilla/5.0";
 
        try {
            URL obj = new URL(url);
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();
     
            // optional default is GET
            con.setRequestMethod("GET");
     
            //add request header
            con.setRequestProperty("User-Agent", USER_AGENT);
     
            int responseCode = con.getResponseCode();
            Log.warn("Sending 'GET' request to URL : " + url);
            Log.warn("Response Code : " + responseCode);
     
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(con.getInputStream()));
            String inputLine;
            StringBuffer response = new StringBuffer();
     
            // Do nothing with this, for the moment
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            if (responseCode == 200) {
                return true;
            }
            else{
                return false;
            }
        }
        catch (Exception e) {
            Log.warn(e.toString());

            return false;
        }
    }

    /**
     * Sets the JID for the second device
     *
     */
    public int setSecondDeviceJID(String user, String pass, String jid) {
        Connection con = null;
        PreparedStatement pstmt = null;
        int rs = 0;

        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(STORE_SECOND_DEVICE_JID);
            pstmt.setString(1, user);
            pstmt.setString(2, pass);
            pstmt.setString(3, jid);

            rs = pstmt.executeUpdate();
        } catch (SQLException sqle) {
            Log.error(sqle.getMessage(), sqle);
        } finally {
            DbConnectionManager.closeConnection(con);
        }
        return rs;
    }

    /**
     * Returns the JID for the second device that have been archived to the database.
     *
     */
    public String getSecondDeviceJID(String user, String pass) {
        String jid = null;
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;

        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(RETRIEVE_SECOND_DEVICE_JID);
            pstmt.setString(1, user);
            pstmt.setString(2, pass);

            rs = pstmt.executeQuery();
            if (rs.next()) {
                jid = rs.getString(1);
            }
            else{
                jid = "invalid username or password";
            }
        } catch (SQLException sqle) {
            Log.error(sqle.getMessage(), sqle);
        } finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        return jid;
    }
}

