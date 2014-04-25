<%@ page import="java.util.*,
                 org.jivesoftware.openfire.XMPPServer,
                 org.jivesoftware.openfire.user.*,
				 net.skillupjapan.openfire.plugin.SUJMessageHandlerPlugin,
                 org.jivesoftware.util.*"
%>
<%@ page import="java.util.regex.Pattern"%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<%
    boolean save = request.getParameter("save") != null;
    boolean reset = request.getParameter("reset") !=null;
    boolean success = request.getParameter("success") != null;
    
    //handler options
    String [] registerHandlerChecked = ParamUtils.getParameters(request, "registerhandler");
    String [] dateHandlerChecked = ParamUtils.getParameters(request, "datehandler");
    String [] unreadHandlerChecked = ParamUtils.getParameters(request, "unreadhandler");
    String [] outOfMUCHandlerChecked = ParamUtils.getParameters(request, "outofmuchandler");
    String [] offlineMUCHandlerChecked = ParamUtils.getParameters(request, "offlinemuchandler");
    String [] secondDeviceHandlerChecked = ParamUtils.getParameters(request, "seconddevicehandler");
    boolean registerHandlerEnabled = registerHandlerChecked.length > 0;
    boolean dateHandlerEnabled = dateHandlerChecked.length > 0;
    boolean unreadHandlerEnabled = unreadHandlerChecked.length > 0;
    boolean outOfMUCHandlerEnabled = outOfMUCHandlerChecked.length > 0;
    boolean offlineMUCHandlerEnabled = offlineMUCHandlerChecked.length > 0;
    boolean secondDeviceHandlerEnabled = secondDeviceHandlerChecked.length > 0;
    
    //get handle to plugin
	SUJMessageHandlerPlugin plugin = (SUJMessageHandlerPlugin) XMPPServer.getInstance().getPluginManager().getPlugin("sujmessagehandler");

    //input validation
    Map<String, String> errors = new HashMap<String, String>();
    if (save) {     	    	    
	    if (errors.size() == 0) {
		    plugin.setRegistHandlerEnabled(registerHandlerEnabled);
            plugin.setDateHandlerEnabled(dateHandlerEnabled);
            plugin.setUnreadHandlerEnabled(unreadHandlerEnabled);
            plugin.setOutOfMUCHandlerEnabled(outOfMUCHandlerEnabled);
            plugin.setOfflineMUCHandlerEnabled(offlineMUCHandlerEnabled);
            plugin.setSecondDeviceHandlerEnabled(secondDeviceHandlerEnabled);
	        response.sendRedirect("sujmessagehandler-props-edit-form.jsp?success=true");
	        return;
	    }
    } else if (reset) {
      plugin.reset();
      response.sendRedirect("sujmessagehandler-props-edit-form.jsp?success=true");
    } else {
        //
    }
    
    registerHandlerEnabled = plugin.isRegisterHandlerEnabled();
    dateHandlerEnabled = plugin.isDateHandlerEnabled();
    unreadHandlerEnabled = plugin.isUnreadHandlerEnabled();
    outOfMUCHandlerEnabled = plugin.isOutOfMUCHandlerEnabled();
    offlineMUCHandlerEnabled = plugin.isOfflineMUCHandlerEnabled();
    secondDeviceHandlerEnabled = plugin.isSecondDeviceHandlerEnabled();

%>

<html>
    <head>
        <title>SUJ Message Handler</title>
        <meta name="pageID" content="sujmessagehandler-props-edit-form"/>
    </head>
    <body>

<p>
Use the form below to edit message handler settings.<br>
</p>

<%  if (success) { %>

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr>
	        <td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0" alt=""></td>
	        <td class="jive-icon-label">Settings updated successfully.</td>
        </tr>
    </tbody>
    </table>
    </div><br>

<%  } else if (errors.size() > 0) { %>

    <div class="jive-error">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr>
        	<td class="jive-icon"><img src="images/error-16x16.gif" width="16" height="16" border="0" alt=""></td>
        	<td class="jive-icon-label">Error saving the settings.</td>
        </tr>
    </tbody>
    </table>
    </div><br>

<%  } %>

<form action="sujmessagehandler-props-edit-form.jsp" method="post">

<fieldset>
    <legend>Message Handler</legend>
    <div>
    
    <p>
    Choose the handlers you want to enable.
    </p>
    
    <table cellpadding="3" cellspacing="0" border="0" width="100%">
    <tbody>
	<tr>
		<td>&nbsp;</td>
        <td><input type="checkbox" name="registerhandler" value="registerhandler" <%= registerHandlerEnabled ? "checked" : "" %>/>Handles special fields on user registration packets.</td>
    </tr>
    <tr>
        <td>&nbsp;</td>
        <td><input type="checkbox" name="unreadhandler" value="unreadhandler" <%= unreadHandlerEnabled ? "checked" : "" %>/>Handles IQ packets for unread message queries.</td>
    </tr>
    <tr>
        <td>&nbsp;</td>
        <td><input type="checkbox" name="outofmuchandler" value="outofmuchandler" <%= outOfMUCHandlerEnabled ? "checked" : "" %>/>Handles forwarding of messages for ONLINE out-of-MUC users.</td>
    </tr>
    <tr>
        <td>&nbsp;</td>
        <td><input type="checkbox" name="offlinemuchandler" value="offlinemuchandler" <%= offlineMUCHandlerEnabled ? "checked" : "" %> <%= outOfMUCHandlerEnabled ? "" : "disabled" %>/>Enables push notifications to OFFLINE users (Needs out-of-MUC enabled!).</td>
    </tr>
    <tr>
        <td>&nbsp;</td>
        <td><input type="checkbox" name="datehandler" value="datehandler" <%= dateHandlerEnabled ? "checked" : "" %>/>Adds date fields in user packets.</td>
	</tr>
    <tr>
        <td>&nbsp;</td>
        <td><input type="checkbox" name="seconddevicehandler" value="seconddevicehandler" <%= secondDeviceHandlerEnabled ? "checked" : "" %>/>Enables handling of second device registration messages.</td>
    </tr>
    </tbody>
    </table>
    </div>
</fieldset>

<br><br>

<fieldset>
    <legend>Other matchers</legend>
    <div>
    
    <p>
    Get your ass to work and create more functionalities.
    </p>
    
    </div>
</fieldset>

<br><br>

<input type="submit" name="save" value="Save settings">
<input type="submit" name="reset" value="Restore factory settings*">
</form>

<br><br>

<em>*Restores the plugin to its factory state, you will lose all changes ever made to this plugin!</em>
</body>
</html>