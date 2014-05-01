/**
 * $Revision: 1722 $
 * $Date: 2005-07-28 15:19:16 -0700 (Thu, 28 Jul 2005) $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import org.jivesoftware.openfire.SharedGroupException;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.group.Group;
import org.jivesoftware.openfire.group.GroupManager;
import org.jivesoftware.openfire.group.GroupNotFoundException;
import org.jivesoftware.openfire.group.GroupAlreadyExistsException;
import org.jivesoftware.openfire.SharedGroupException;
import org.jivesoftware.openfire.lockout.LockOutManager;
import org.jivesoftware.openfire.roster.Roster;
import org.jivesoftware.openfire.roster.RosterItem;
import org.jivesoftware.openfire.roster.RosterManager;
import org.jivesoftware.openfire.roster.RosterItem;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.user.UserAlreadyExistsException;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.openfire.muc.MUCRoom;
import org.jivesoftware.openfire.muc.MUCRole;
import org.jivesoftware.openfire.muc.ConflictException;
import org.jivesoftware.openfire.muc.ForbiddenException;
import org.jivesoftware.openfire.muc.NotAllowedException;
import org.jivesoftware.openfire.muc.CannotBeInvitedException;
import org.jivesoftware.openfire.muc.MultiUserChatService;
import org.jivesoftware.openfire.muc.MultiUserChatManager;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.PropertyEventDispatcher;
import org.jivesoftware.util.PropertyEventListener;
import org.jivesoftware.util.StringUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.database.DbConnectionManager;

import org.xmpp.packet.JID;
import org.xmpp.packet.IQ;
import org.dom4j.Element;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Plugin that allows the administration of users via HTTP requests.
 *
 * @author Justin Hunt, Daniel Pereira
 */
public class SUJoinPlugin implements Plugin, PropertyEventListener {

    private static final Logger Log = LoggerFactory.getLogger(SUJoinPlugin.class);

    private UserManager userManager;
    private RosterManager rosterManager;
    private XMPPServer server;
    private MultiUserChatManager mucManager;
    private MultiUserChatService mucService;

    private String secret;
    private boolean enabled;
    private Collection<String> allowedIPs;

    // User management queries
    private static final String ADD_USER = "INSERT INTO ofUserMetadata (user_code, group_code, tenant_code, user_name, dept_code, phone, pre_register, joined) VALUES (?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE user_name=VALUES(user_name)";
    private static final String GET_USER = "SELECT user_code, tenant_code, user_name, dept_code, phone, pre_register, joined FROM ofUserMetadata WHERE user_name LIKE ?";
    private static final String DELETE_USER = "DELETE FROM ofUserMetadata WHERE user_name=?";
    private static final String ADD_USER_DEVICE = "INSERT INTO ofUserDevices (username, device) VALUES (?,?)";
    private static final String REMOVE_USER_DEVICES = "DELETE FROM ofUserDevices WHERE username=?";
    private static final String USERS_BY_TENANT = "SELECT user_name FROM ofUserMetadata WHERE tenant_code=?";
    private static final String GET_USER_TENANT_CODE = "SELECT tenant_code FROM ofUserMetadata WHERE user_name=?";

    // MUC management queries
    private static final String ADD_GROUP = "INSERT INTO ofGroupMetadata (group_code, group_name, tenant_code, muc_jid) VALUES (?,?,?,?) ON DUPLICATE KEY UPDATE group_code=VALUES(group_code)";
    private static final String DELETE_GROUP = "DELETE FROM ofGroupMetadata WHERE group_code=?";
    private static final String GET_GROUP_BY_NAME = "SELECT * from ofGroupMetadata WHERE group_code=? GROUP BY muc_jid";


    public void initializePlugin(PluginManager manager, File pluginDirectory) {
        server = XMPPServer.getInstance();
        userManager = server.getUserManager();
        rosterManager = server.getRosterManager();
        mucManager = server.getMultiUserChatManager();
        mucService = mucManager.getMultiUserChatService("conference"); 

        secret = JiveGlobals.getProperty("plugin.sujoin.secret", "");
        // If no secret key has been assigned to the user service yet, assign a random one.
        if (secret.equals("")){
            secret = StringUtils.randomString(8);
            setSecret(secret);
        }

        // See if the service is enabled or not.
        enabled = JiveGlobals.getBooleanProperty("plugin.sujoin.enabled", false);

        // Get the list of IP addresses that can use this service. An empty list means that this filter is disabled.
        allowedIPs = StringUtils.stringToCollection(JiveGlobals.getProperty("plugin.sujoin.allowedIPs", ""));

        // Listen to system property events
        PropertyEventDispatcher.addListener(this);
    }

    public void destroyPlugin() {
        userManager = null;
        // Stop listening to system property events
        PropertyEventDispatcher.removeListener(this);
    }

    /*
     *
     */
    public void editUser(String username, String password, String name, String email, String tenantNames, String devices,
        String user_code, String group_code, String tenant_code, String dept_code, String phone, String pre_register)
            throws UserAlreadyExistsException, GroupAlreadyExistsException, UserNotFoundException, GroupNotFoundException, SQLException, SharedGroupException
    {
        Connection con = null;
        PreparedStatement pstmt = null;

        try {
            con = DbConnectionManager.getConnection();

            pstmt = con.prepareStatement(REMOVE_USER_DEVICES);
            pstmt.setString(1, username);

            Log.warn("REMOVE_USER_DEVICES query: " + pstmt);
            pstmt.executeUpdate();
        } finally {
            DbConnectionManager.closeConnection(con);
        }

        // Update the user, since it has an ON CONFLICT clause
        createUser(username, password, name, email, tenantNames, devices, user_code, group_code, tenant_code, dept_code, phone, pre_register, true);
    }

    /**
     * Creates a new user
     *
     * @param username - the username of the local user to delete.
     * @param update - this flag is used to differentiate from update to create
     *
     */
    public void createUser(String username, String password, String name, String email, String tenantNames, String devices,
        String user_code, String group_code, String tenant_code, String dept_code, String phone, String pre_register, Boolean update)
            throws UserAlreadyExistsException, GroupAlreadyExistsException, UserNotFoundException, GroupNotFoundException, SQLException, SharedGroupException
    {
        if (!update){
            userManager.createUser(username, password, name, email);
        }

        User affectedUser = userManager.getUser(username);

        // Begin JOIN Metadata
        Connection con = null;
        PreparedStatement pstmt = null;
        PreparedStatement pstmt1 = null;
        PreparedStatement pstmt2 = null;

        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(ADD_USER);
            pstmt.setString(1, user_code);
            pstmt.setString(2, group_code);
            pstmt.setString(3, tenant_code);
            pstmt.setString(4, username);
            pstmt.setString(5, dept_code);
            pstmt.setString(6, phone);
            pstmt.setString(7, pre_register);
            pstmt.setString(8, "no");

            Log.warn("ADD_USER query: " + pstmt);
            pstmt.executeUpdate();

            if (devices != null) {
                StringTokenizer tkn = new StringTokenizer(devices, ",");

                while (tkn.hasMoreTokens())
                {
                    String device = tkn.nextToken();

                    pstmt1 = con.prepareStatement(ADD_USER_DEVICE);
                    pstmt1.setString(1, username);
                    pstmt1.setString(2, device);

                    Log.warn("ADD_USER_DEVICE query: " + pstmt1);
                    pstmt1.executeUpdate();
                }
            }

            // Update users rosters
            ResultSet rs = null;
            Roster newUserRoster = rosterManager.getRoster(username);
            JID newUserJID = server.createJID(username, null);
            pstmt2 = con.prepareStatement(USERS_BY_TENANT);
            pstmt2.setString(1, tenant_code);

            Log.warn("USERS_BY_TENANT query: " + pstmt2);

            rs = pstmt2.executeQuery();
            if (rs.next()) {
                String tenantUser = rs.getString(1);
                Roster tenantRoster = rosterManager.getRoster(tenantUser);
                JID tenantJID = server.createJID(tenantUser, null);

                // Add user to newly created user roster
                //newUserRoster.createRosterItem(tenantJID, true, true);
                addRosterItem(username, tenantJID.toString(), username, "3", "");

                // Add newly create user to existing user roster
                //tenantRoster.createRosterItem(newUserJID, true, true);
                addRosterItem(tenantUser, newUserJID.toString(), tenantUser, "3", "");
            }

        } finally {
            DbConnectionManager.closeConnection(con);
        }
    }

    /**
     * Deletes a given username
     *
     * @param username the username of the local user to delete.
     * @throws UserNotFoundException if the requested user
     *         does not exist in the local server.
     * @throws SharedGroupException
     * @throws SQLException if the SQL query user fails
     */
    public void deleteUser(String username) throws UserNotFoundException, SharedGroupException, SQLException
    {
        Connection con = null;
        PreparedStatement pstmt = null;
        PreparedStatement pstmt1 = null;
        PreparedStatement pstmt2 = null;
        PreparedStatement pstmt3 = null;
        ResultSet rs = null;
        String tenant_code = null;

        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(GET_USER_TENANT_CODE);
            pstmt.setString(1, username);

            Log.warn("GET_USER_TENANT_CODE query: " + pstmt1);
            rs = pstmt.executeQuery();
            if (rs.next()) {
                tenant_code = rs.getString(1);
            }

            pstmt1 = con.prepareStatement(DELETE_USER);
            pstmt1.setString(1, username);

            Log.warn("DELETE_USER query: " + pstmt1);
            pstmt.executeUpdate();

            pstmt2 = con.prepareStatement(REMOVE_USER_DEVICES);
            pstmt2.setString(1, username);

            Log.warn("REMOVE_USER_DEVICES query: " + pstmt2);
            pstmt2.executeUpdate();

            // Update users rosters
            Roster newUserRoster = rosterManager.getRoster(username);
            JID newUserJID = server.createJID(username, null);
            pstmt3 = con.prepareStatement(USERS_BY_TENANT);
            pstmt3.setString(1, tenant_code);

            Log.warn("USERS_BY_TENANT query: " + pstmt3);

            rs = pstmt3.executeQuery();
            if (rs.next()) {
                String tenantUser = rs.getString(1);
                Roster tenantRoster = rosterManager.getRoster(tenantUser);
                JID tenantJID = server.createJID(tenantUser, null);

                // Remove user from tenant roster
                tenantRoster.deleteRosterItem(newUserJID, true);
            }

        } finally {
            DbConnectionManager.closeConnection(con);
        }

        // Delete from openfire
        User user = getUser(username);
        userManager.deleteUser(user);

        rosterManager.deleteRoster(server.createJID(username, null));
    }

    /**
     * Returns all users
     *
     * @param None
     */
    public String getAllUsers()
    {
        String result = "users=";

        Collection<User>users = userManager.getUsers();
        for (User user: users) {
            result += user.getUsername() + ",";
        }

        return result;
    }

    /**
     * Search a given username
     *
     * @param username the username of the local user to disable.
     * @throws UserNotFoundException if the requested user
     *         does not exist in the local server.
     * @throws SQLException if the SQL query user fails
     */
    public String searchUser(String username) throws UserNotFoundException, SQLException
    {
        User user = getUser(username);
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        String result = "";

        result += "name=" + user.getName();
        result += ";email=" + user.getEmail();
        result += ";create_date=" + user.getCreationDate().toString();
        result += ";user_jid=" + server.createJID(username, null).toString();

        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(GET_USER);
            pstmt.setString(1, "%"+username+"%");

            Log.warn("GET_USER query: " + pstmt);

            rs = pstmt.executeQuery();
            if (rs.next()) {
                //user_code, tenant_code, user_name, dept_code, phone, pre_register, joined
                result += ";tenant_code=" + rs.getString(2) + ";user_code=" + rs.getString(1) + ";dept_code=" + rs.getString(4) + ";username=" + rs.getString(3);
                result += ";phone=" + rs.getString(5) + ";pre_register=" + rs.getString(6) + ";not_joined=" + rs.getString(7);
            }

        } finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }

        return result;
    }

    /**
     * Lock Out on a given username
     *
     * @param username the username of the local user to disable.
     * @throws UserNotFoundException if the requested user
     *         does not exist in the local server.
     */
    public void disableUser(String username) throws UserNotFoundException
    {
        User user = getUser(username);
        LockOutManager.getInstance().disableAccount(username, null, null);
    }

    /**
     * Remove the lockout on a given username
     *
     * @param username the username of the local user to enable.
     * @throws UserNotFoundException if the requested user
     *         does not exist in the local server.
     */
    public void enableUser(String username) throws UserNotFoundException
    {
        User user = getUser(username);
        LockOutManager.getInstance().enableAccount(username);
    }

    public void updateUser(String username, String password, String name, String email, String tenantNames)
            throws UserNotFoundException, GroupAlreadyExistsException
    {
        User user = getUser(username);
        if (password != null) user.setPassword(password);
        if (name != null) user.setName(name);
        if (email != null) user.setEmail(email);

        if (tenantNames != null) {
            Collection<Group> newTenants = new ArrayList<Group>();
            StringTokenizer tkn = new StringTokenizer(tenantNames, ",");

            while (tkn.hasMoreTokens())
            {
				String tenantName = tkn.nextToken();
				Group tenant = null;

                try {
                    tenant = GroupManager.getInstance().getGroup(tenantName);
                } catch (GroupNotFoundException e) {
                    // Create this tenant
					tenant = GroupManager.getInstance().createGroup(tenantName);
                	tenant.getProperties().put("sharedRoster.showInRoster", "onlyGroup");
                	tenant.getProperties().put("sharedRoster.displayName", tenantName);
                	tenant.getProperties().put("sharedRoster.groupList", "");
                }

                newTenants.add(tenant);
            }

            Collection<Group> existingTenants = GroupManager.getInstance().getGroups(user);
            // Get the list of tenants to add to the user
            Collection<Group> tenantsToAdd =  new ArrayList<Group>(newTenants);
            tenantsToAdd.removeAll(existingTenants);
            // Get the list of tenants to remove from the user
            Collection<Group> tenantsToDelete =  new ArrayList<Group>(existingTenants);
            tenantsToDelete.removeAll(newTenants);

            // Add the user to the new tenants
            for (Group tenant : tenantsToAdd) {
                tenant.getMembers().add(server.createJID(username, null));
            }
            // Remove the user from the old s
            for (Group tenant : tenantsToDelete) {
                tenant.getMembers().remove(server.createJID(username, null));
            }
        }
    }

    /**
     * Add new roster item for specified user
     *
     * @param username the username of the local user to add roster item to.
     * @param itemJID the JID of the roster item to be added.
     * @param itemName the nickname of the roster item.
     * @param subscription the type of subscription of the roster item. Possible values are: -1(remove), 0(none), 1(to), 2(from), 3(both).
     * @param tenantNames the name of a group to place contact into.
     * @throws UserNotFoundException if the user does not exist in the local server.
     * @throws UserAlreadyExistsException if roster item with the same JID already exists.
     * @throws SharedGroupException if roster item cannot be added to a shared group.
     */
    public void addRosterItem(String username, String itemJID, String itemName, String subscription, String tenantNames)
            throws UserNotFoundException, UserAlreadyExistsException, SharedGroupException
    {
        getUser(username);
        Roster r = rosterManager.getRoster(username);
        JID j = new JID(itemJID);

        if (r != null) {
            List<String> tenants = new ArrayList<String>();
            if (tenantNames != null) {
                StringTokenizer tkn = new StringTokenizer(tenantNames, ",");
                while (tkn.hasMoreTokens()) {
                    tenants.add(tkn.nextToken());
                }
            }
            RosterItem ri = r.createRosterItem(j, itemName, tenants, false, true);
            if (subscription == null) {
                subscription = "0";
            }
            ri.setSubStatus(RosterItem.SubType.getTypeFromInt(Integer.parseInt(subscription)));
            r.updateRosterItem(ri);
        }
    }

    /**
     * Update roster item for specified user
     *
     * @param username the username of the local user to update roster item for.
     * @param itemJID the JID of the roster item to be updated.
     * @param itemName the nickname of the roster item.
     * @param subscription the type of subscription of the roster item. Possible values are: -1(remove), 0(none), 1(to), 2(from), 3(both).
     * @param tenantNames the name of a group.
     * @throws UserNotFoundException if the user does not exist in the local server or roster item does not exist.
     * @throws SharedGroupException if roster item cannot be added to a shared group.
     */
    public void updateRosterItem(String username, String itemJID, String itemName, String subscription, String tenantNames)
            throws UserNotFoundException, SharedGroupException
    {
        getUser(username);
        Roster r = rosterManager.getRoster(username);
        JID j = new JID(itemJID);

        RosterItem ri = r.getRosterItem(j);

        List<String> tenants = new ArrayList<String>();
        if (tenantNames != null) {
            StringTokenizer tkn = new StringTokenizer(tenantNames, ",");
            while (tkn.hasMoreTokens()) {
                tenants.add(tkn.nextToken());
            }
        }

        ri.setGroups(tenants);
        ri.setNickname(itemName);

        if (subscription == null) {
            subscription = "0";
        }
        ri.setSubStatus(RosterItem.SubType.getTypeFromInt(Integer.parseInt(subscription)));
        r.updateRosterItem(ri);
    }

    /**
     * Delete roster item for specified user. No error returns if nothing to delete.
     *
     * @param username the username of the local user to add roster item to.
     * @param itemJID the JID of the roster item to be deleted.
     * @throws UserNotFoundException if the user does not exist in the local server.
     * @throws SharedGroupException if roster item cannot be deleted from a shared group.
     */
    public void deleteRosterItem(String username, String itemJID)
            throws UserNotFoundException, SharedGroupException
    {
        getUser(username);
        Roster r = rosterManager.getRoster(username);
        JID j = new JID(itemJID);

        // No roster item is found. Uncomment the following line to throw UserNotFoundException.
        //r.getRosterItem(j);

        r.deleteRosterItem(j, true);
    }

    /**
     * Tenant management utility functions
     */
    public void addTenantItem(String tenant) 
            throws GroupAlreadyExistsException, SharedGroupException
    {
        if (tenant != null) {
            try {
                // Tenant already exists, do nothing
                GroupManager.getInstance().getGroup(tenant);
            } catch (GroupNotFoundException e) {
                // Otherwise create this tenant
                GroupManager.getInstance().createGroup(tenant);
            }
        }
    }

    public void removeTenantItem(String tenant) 
            throws GroupAlreadyExistsException, SharedGroupException 
    {
        if (tenant != null) {
            try {
                // Delete tenant
                GroupManager.getInstance().deleteGroup(GroupManager.getInstance().getGroup(tenant));
            } catch (GroupNotFoundException e) {
                // If it exists, do nothing/notify
            }
        }
    }

    public void updateTenantItem(String tenant, String name, String description)
            throws GroupAlreadyExistsException, SharedGroupException 
    {
        if (tenant != null) {
            Group t = null;
            try {
                // Tenant already exists, update
                t = GroupManager.getInstance().getGroup(tenant);
            } catch (GroupNotFoundException e) {
                // Otherwise create this tenant
                t = GroupManager.getInstance().createGroup(tenant);
            }

            if (name != null) {
                t.setName(name);
            }

            if (description != null) {
                t.setDescription(description);
            }
        }
    }

    /**
     * Search a given MUC
     *
     * @param muc the MUC of the server.
     * @throws MUCNotFoundException if the requested MUC
     *         does not exist in the local server.
     */
    public void searchMUCs() throws ConflictException
    {
        //User user = getUser(username);
        //LockOutManager.getInstance().disableAccount(username, null, null);
    }

    /**
     * Returns all MUCs 
     *
     */
    public String getAllMUCs()
    {
        String result = "rooms=";
        List<MUCRoom> mucs = mucService.getChatRooms();
        for (MUCRoom room: mucs) {
            result += room.getName() + ",";
        }

        return result;
    }

    /*
     * Removes a MUC 
     *
     */
    public void removeMUC(String group_code)
            throws NotAllowedException, ConflictException, ForbiddenException, CannotBeInvitedException, 
            UserNotFoundException, UserAlreadyExistsException, SharedGroupException, SQLException
    {

        // Add metadata
        Connection con = null;
        PreparedStatement pstmt = null;
        PreparedStatement pstmt1 = null;
        ResultSet rs = null;
        Collection<String> mucNames = new ArrayList<String>();

        try {
            con = DbConnectionManager.getConnection();

            // Get JIDs before deleting metadata
            pstmt = con.prepareStatement(GET_GROUP_BY_NAME);
            pstmt.setString(1, group_code);

            Log.warn("GET_GROUP_BY_NAME query: " + pstmt);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                 mucNames.add(rs.getString(2));
            }

            Log.warn("mucNames: " + mucNames.toString());

            // Delete metadata
            pstmt1 = con.prepareStatement(DELETE_GROUP);
            pstmt1.setString(1, group_code);

            Log.warn("DELETE_GROUP query: " + pstmt1);
            //pstmt1.executeUpdate();

        } finally {
            DbConnectionManager.closeConnection(con);
        }

        // Delete group from the involved users rosters
        for (String muc: mucNames) {
            MUCRoom newMUC = mucService.getChatRoom(muc);
            if (newMUC != null) {
                // get all MUC users
                Collection<JID> membersJIDs = newMUC.getMembers();
                Collection<JID> ownersJIDs = newMUC.getOwners();
                Collection<JID> outcastsJIDs = newMUC.getOutcasts();
                Collection<JID> adminsJIDs = newMUC.getAdmins();
                String roomService = muc + "@" + mucService.getServiceDomain();

                for (JID mJID : membersJIDs) {
                    Log.warn("Removing " + roomService + " from " + mJID.getNode());
                    deleteRosterItem(mJID.getNode(), roomService);
                }

                for (JID mJID : ownersJIDs) {
                    Log.warn("Removing " + roomService + " from " + mJID.getNode());
                    deleteRosterItem(mJID.getNode(), roomService);
                }

                for (JID mJID : outcastsJIDs) {
                    Log.warn("Removing " + roomService + " from " + mJID.getNode());
                    deleteRosterItem(mJID.getNode(), roomService);
                }

                for (JID mJID : adminsJIDs) {
                    Log.warn("Removing " + roomService + " from " + mJID.getNode());
                    deleteRosterItem(mJID.getNode(), roomService);                
                }

                // Delete MUC
                newMUC.destroyRoom(null, null); 
            }
        }
    }
    
    /**
     * Adds a new MUC 
     *
     */
    public void addMUC(String tenant_code, String group_name, String group_users, String owner_name, String group_code)
            throws NotAllowedException, ConflictException, ForbiddenException, CannotBeInvitedException, 
            UserNotFoundException, UserAlreadyExistsException, SharedGroupException, SQLException
    {
        JID ownerJID = server.createJID(owner_name, null);
        MUCRoom newMUC = mucService.getChatRoom(group_name, ownerJID);

        // Add room JID to owner roster
        addRosterItem(owner_name, newMUC.getJID().toString(), group_name, "3", "");

        // Add users to MUC
        if (group_users != null && newMUC.getRole() != null) {
            StringTokenizer tkn = new StringTokenizer(group_users, ",");

            while (tkn.hasMoreTokens())
            {
                String user = tkn.nextToken();
                JID userJID = server.createJID(user, null);
                newMUC.addMember(userJID, user, newMUC.getRole());

                // Add room JID to user roster
                addRosterItem(user, newMUC.getJID().toString(), group_name, "3", "");
            }
        }

        // Add metadata
        Connection con = null;
        PreparedStatement pstmt = null;

        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(ADD_GROUP);
            pstmt.setString(1, group_code);
            pstmt.setString(2, group_name);
            pstmt.setString(3, tenant_code);
            pstmt.setString(4, newMUC.getJID().toString());

            Log.warn("ADD_GROUP query: " + pstmt);
            pstmt.executeUpdate();

        } finally {
            DbConnectionManager.closeConnection(con);
        }
    }

    /**
     * Returns the the requested user or <tt>null</tt> if there are any
     * problems that don't throw an error.
     *
     * @param username the username of the local user to retrieve.
     * @return the requested user.
     * @throws UserNotFoundException if the requested user
     *         does not exist in the local server.
     */
    private User getUser(String username) throws UserNotFoundException {
        JID targetJID = server.createJID(username, null);
        // Check that the sender is not requesting information of a remote server entity
        if (targetJID.getNode() == null) {
            // Sender is requesting presence information of an anonymous user
            throw new UserNotFoundException("Username is null");
        }
        return userManager.getUser(targetJID.getNode());
    }

    /**
     * Returns the secret key that only valid requests should know.
     *
     * @return the secret key.
     */
    public String getSecret() {
        return secret;
    }

    /**
     * Sets the secret key that grants permission to use the sujoin.
     *
     * @param secret the secret key.
     */
    public void setSecret(String secret) {
        JiveGlobals.setProperty("plugin.sujoin.secret", secret);
        this.secret = secret;
    }

    public Collection<String> getAllowedIPs() {
        return allowedIPs;
    }

    public void setAllowedIPs(Collection<String> allowedIPs) {
        JiveGlobals.setProperty("plugin.sujoin.allowedIPs", StringUtils.collectionToString(allowedIPs));
        this.allowedIPs = allowedIPs;
    }

    /**
     * Returns true if the user service is enabled. If not enabled, it will not accept
     * requests to create new accounts.
     *
     * @return true if the user service is enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Enables or disables the user service. If not enabled, it will not accept
     * requests to create new accounts.
     *
     * @param enabled true if the user service should be enabled.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        JiveGlobals.setProperty("plugin.sujoin.enabled",  enabled ? "true" : "false");
    }

    public void propertySet(String property, Map<String, Object> params) {
        if (property.equals("plugin.sujoin.secret")) {
            this.secret = (String)params.get("value");
        }
        else if (property.equals("plugin.sujoin.enabled")) {
            this.enabled = Boolean.parseBoolean((String)params.get("value"));
        }
        else if (property.equals("plugin.sujoin.allowedIPs")) {
            this.allowedIPs = StringUtils.stringToCollection((String)params.get("value"));
        }
    }

    public void propertyDeleted(String property, Map<String, Object> params) {
        if (property.equals("plugin.sujoin.secret")) {
            this.secret = "";
        }
        else if (property.equals("plugin.sujoin.enabled")) {
            this.enabled = false;
        }
        else if (property.equals("plugin.sujoin.allowedIPs")) {
            this.allowedIPs = Collections.emptyList();
        }
    }

    public void xmlPropertySet(String property, Map<String, Object> params) {
        // Do nothing
    }

    public void xmlPropertyDeleted(String property, Map<String, Object> params) {
        // Do nothing
    }
}
