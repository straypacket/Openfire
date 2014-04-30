/**
 * $RCSfile$
 * $Revision: 1710 $
 * $Date: 2005-07-26 11:56:14 -0700 (Tue, 26 Jul 2005) $
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

package net.skillupjapan.openfire.plugin.SUJoin;

import gnu.inet.encoding.Stringprep;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.sql.SQLException;

import org.jivesoftware.admin.AuthCheckFilter;
import org.jivesoftware.openfire.SharedGroupException;
import org.jivesoftware.openfire.XMPPServer;
import net.skillupjapan.openfire.plugin.SUJoinPlugin;
import org.jivesoftware.openfire.user.UserAlreadyExistsException;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.openfire.group.GroupNotFoundException;
import org.jivesoftware.openfire.muc.ConflictException;
import org.jivesoftware.util.Log;
import org.xmpp.packet.JID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Servlet that addition/deletion/modification of the users info in the system.
 * Use the <b>type</b>
 * parameter to specify the type of action. Possible values are <b>add</b>,<b>delete</b> and
 * <b>update</b>. <p>
 * <p/>
 * The request <b>MUST</b> include the <b>secret</b> parameter. This parameter will be used
 * to authenticate the request. If this parameter is missing from the request then
 * an error will be logged and no action will occur.
 *
 * @author Justin Hunt, Daniel Pereira
 */
public class SUJoinServlet extends HttpServlet {

    private SUJoinPlugin plugin;
    private static final Logger Log = LoggerFactory.getLogger(SUJoinServlet.class);

    @Override
	public void init(ServletConfig servletConfig) throws ServletException {
        super.init(servletConfig);
        plugin = (SUJoinPlugin) XMPPServer.getInstance().getPluginManager().getPlugin("sujoin");
 
        // Exclude this servlet from requiring the user to login
        AuthCheckFilter.addExclude("suJoin/sujoin");
    }

    @Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException
    {
        // Printwriter for writing out responses to browser
        PrintWriter out = response.getWriter();

        if (!plugin.getAllowedIPs().isEmpty()) {
            // Get client's IP address
            String ipAddress = request.getHeader("x-forwarded-for");
            if (ipAddress == null) {
                ipAddress = request.getHeader("X_FORWARDED_FOR");
                if (ipAddress == null) {
                    ipAddress = request.getHeader("X-Forward-For");
                    if (ipAddress == null) {
                        ipAddress = request.getRemoteAddr();
                    }
                }
            }
            if (!plugin.getAllowedIPs().contains(ipAddress)) {
                Log.warn("SUJoin service rejected service to IP address: " + ipAddress);
                replyError("RequestNotAuthorised",response, out);
                return;
            }
        }

        /**
         * API parameters
         */
        String secret = request.getParameter("secret");
        /**
         * User parameters
         */
        String username = request.getParameter("username");
        String password = request.getParameter("password");
        String user_code = request.getParameter("usercode");
        String tenant_code = request.getParameter("tenantcode");
        String dept_code = request.getParameter("deptcode");
        String group_code = request.getParameter("groupcode");
        String phone = request.getParameter("phone");
        String pre_register = request.getParameter("pre_register");
        String devices = request.getParameter("devices");

        //username=pratchett&password=ankmorpork&usercode=u1&tenantcode=t1&deptcode=d1&groupcode=g1&phone=123123123&pre_register=yes&devices=device1,device2,device3

        String name = request.getParameter("name");
        String desc = request.getParameter("description");
        String email = request.getParameter("email");
        String type = request.getParameter("type");
        String tenantNames = request.getParameter("tenants");
        String tenant = request.getParameter("tenant");
        String item_jid = request.getParameter("item_jid");
        String sub = request.getParameter("subscription");
        //No defaults, add, delete, update only
        //type = type == null ? "image" : type;
       
       // Check that our plugin is enabled.
        if (!plugin.isEnabled()) {
            Log.warn("SUJoin service plugin is disabled: " + request.getQueryString());
            replyError("SUJoinDisabled",response, out);
            return;
        }
       
        // Check this request is authorised
        if (secret == null || !secret.equals(plugin.getSecret())){
            Log.warn("An unauthorised user service request was received: " + request.getQueryString());
            replyError("RequestNotAuthorised",response, out);
            return;
         }

        // Some checking is required on the username
        if (username == null && !type.equals("get_all_users") && !type.equals("delete_group") && !type.equals("get_all_groups") && !type.equals("get_all_groups") ){
            replyError("IllegalArgumentException",response, out);
            return;
        }

        if ((type.equals("add_roster") || type.equals("update_roster") || type.equals("delete_roster")) &&
        	(item_jid == null || !(sub == null || sub.equals("-1") || sub.equals("0") ||
        	sub.equals("1") || sub.equals("2") || sub.equals("3")))) {
            replyError("IllegalArgumentException",response, out);
            return;
        }

        // Check the request type and process accordingly
        try {
            if (username != null) {
                username = username.trim().toLowerCase();
                username = JID.escapeNode(username);
                username = Stringprep.nodeprep(username);
            }
            /**
             * User management
             */
            if ("add_user".equals(type)) {
                plugin.createUser(username, password, name, email, tenantNames, devices, user_code, group_code, tenant_code, dept_code, phone, pre_register, false);
                replyMessage("ok",response, out);
                //imageProvider.sendInfo(request, response, presence);
            }
            else if ("delete_user".equals(type)) {
                plugin.deleteUser(username);
                replyMessage("ok",response,out);
                //xmlProvider.sendInfo(request, response, presence);
            }
            else if ("enable_user".equals(type)) {
                plugin.enableUser(username);
                replyMessage("ok",response,out);
            }
            else if ("disable_user".equals(type)) {
                plugin.disableUser(username);
                replyMessage("ok",response,out);
            }
            else if ("edit_user".equals(type)) {
                plugin.editUser(username, password, name, email, tenantNames, devices, user_code, group_code, tenant_code, dept_code, phone, pre_register);
                replyMessage("ok",response,out);
            }
            else if ("search_user".equals(type)) {
                String result = plugin.searchUser(username);
                replyMessage(result,response,out);
                //xmlProvider.sendInfo(request, response, presence);
            }
            else if ("get_all_users".equals(type)) {
                String result = plugin.getAllUsers();
                replyMessage(result,response,out);
            }
            /**
             * MUC management
             */
            else if ("add_group".equals(type)) {
                plugin.addRosterItem(username, item_jid, name, sub, tenantNames);
                replyMessage("ok",response, out);
            }
            else if ("edit_group".equals(type)) {
                plugin.updateRosterItem(username, item_jid, name, sub, tenantNames);
                replyMessage("ok",response, out);
            }
            else if ("delete_group".equals(type)) {
                plugin.deleteRosterItem(username, item_jid);
                replyMessage("ok",response, out);
            }
            else if ("get_all_groups".equals(type)) {
                plugin.getAllMUCs();
                replyMessage("ok",response, out);
            }
            else if ("search_group".equals(type)) {
                plugin.searchMUCs();
                replyMessage("ok",response, out);
            }
            else {
                Log.warn("The SUJoin servlet received an invalid request of type: " + type);
                // TODO Do something
            }
        }
        catch (UserAlreadyExistsException e) {
            replyError("UserAlreadyExistsException",response, out);
        }
        catch (UserNotFoundException e) {
            replyError("UserNotFoundException",response, out);
        }
        catch (IllegalArgumentException e) {
            replyError("IllegalArgumentException",response, out);
        }
        catch (SharedGroupException e) {
        	replyError("SharedGroupException",response, out);
        }
        catch (GroupNotFoundException e) {
            replyError("GroupNotFoundException", response, out);
        }
        catch (ConflictException e) {
            replyError("ConflictException", response, out);
        }
        catch (SQLException sqle) {
            replyError("SQLException: " + sqle.getMessage(),response, out);
        }
        catch (Exception e) {
            replyError("Exception: " + e.toString(),response, out);
        }
    }

    private void replyMessage(String message,HttpServletResponse response, PrintWriter out){
        response.setContentType("text/xml");        
        out.println("<result>" + message + "</result>");
        out.flush();
    }

    private void replyError(String error,HttpServletResponse response, PrintWriter out){
        response.setContentType("text/xml");        
        out.println("<error>" + error + "</error>");
        out.flush();
    }

    @Override
	public void destroy() {
        super.destroy();
        // Release the excluded URL
        AuthCheckFilter.removeExclude("suJoin/sujoin");
    }
}
