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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Date;

import org.dom4j.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;

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
}