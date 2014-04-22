Openfire
========

This is a fork for in-house usage.

For help on the usage of this work in specific, go to the <a href="src/plugins/suJoin/">src/plugins/suJoin</a> directory.

Hacking OpenFire
----------------

Luckily, since March 31st 2014 they migrated from SVN to GitHub! I made a fork which can be clones with:

	git clone https://github.com/straypacket/Openfire.git

or

	git clone git@odin.suj.jp:d.pereira/sujopenfire.git

The plugins section, which is what we're omre interested in, are in `src/plugins/`. The base of our API plug is the directory `userservice`.

To compile plugins, just compile the whole project with from within the root of the project:
	
	make clean
	cd build
	ant plugins
	
Make sure you build the project with the same architecture as the one used in the server (i.e. both are 32 or 64 bit versions).

This process will compile the binary .jar file into `../work/plugins`
	
If the plugin works as expected, push your changes to git.

If you want to create a new plugin, you should copy one of the existing plugins and carefully refactor the names of the classes, packages and URLs. After that, all the above commands should work without any problems.

SUJoin API
----------

To install self-developped plugins, use the webservice upload feature, in `Plugins\Upload Plugin` to upload the generated .jar file.

Alternatively, **and in a less safe manner**, copy the .jar file from the previous compilation to the `plugins` directory from the root openfire directory:

	scp -i AWS_KEY.pem work/plugins/suJoin.jar ubuntu@EC2-INSTANCE:openfire/plugins/
	
To install, unpack the .jar file with:

	unzip suJoin.jar
	
This will install the plugin and you'll be able to see the plugin in action from OpenFire's web interface, at `Server/Server Settings`. No need to restart the servers.

Usage
-----

To use the plugin, go to the `Server/Server Settings/SUJ Join` and enable it. Copy the `Secret key` and use it according to the documentation. A Ruby client that can be adapted makes use of the same API at [2].
For obvious safety reasons, we only support POST requests.

**[EXAMPLE]** Add user with wget:

	wget -S -O - -T 1 -t 1 -nv -q --no-check-certificate --post-data="type=add&secret=secretkey&username=kafka&password=drowssap&name=franz&email=franz@kafka.com" "https://example.com:9091/plugins/suJoin/sujoin"

Reply should be:

	HTTP/1.1 200 OK
	Set-Cookie: JSESSIONID=kjhgkjguyf;Path=/
  	Expires: Thu, 01 Jan 1970 00:00:00 GMT
  	Content-Type: text/xml;charset=UTF-8
	Transfer-Encoding: chunked
	<result>ok</result>	

You can use any other tool, such as curl.

API Requests
------------

API calls implemented, according to functionality. Take a look at the Usage example, these parameter calls should replace those in above the `--post-data` example.

Authentication parameters:

- secret=STRING [REQUIRED]

User management parameters:

- type=add

- type=delete

- type=update

- type=enable

- type=disable

User properties:

- username=STRING [REQUIRED for user operations]

- password=STRING [REQUIRED only for user creation]

- name=STRING

- groups=STRING

- email=STRING


Roster management parameters:

- type=add_roster

- type=update_roster

- type=delete_roster

- item_jid=STRING|JID [REQUIRED for roster operations]


**API calls _not yet_ implemented:**

Chat management parameters:

- type=create_chat

- type=update_chat

- type=delete_chat

- type=adduser_chat

- type=deleteuser_chat

- type=getuserlist_chat

- group_jid=STRING|JID [REQUIRED for chats]


Chat properties:

- topic=STRING

- pass=STRING

- maxusers=INTEGER

- public=TRUE|FALSE

- moderated=TRUE|FALSE

- members_only=TRUE|FALSE



SUJ Message Handler
-------------------

To install self-developped plugins, use the webservice upload feature, in `Plugins\Upload Plugin` to upload the generated .jar file.

Alternatively, **and in a less safe manner**, copy the .jar file from the previous compilation to the `plugins` directory from the root openfire directory:

        scp -i AWS_KEY.pem work/plugins/sujMessageHandler.jar ubuntu@EC2-INSTANCE:openfire/plugins/

To install, unpack the .jar file with:

        unzip sujMessageHandler.jar

This will install the plugin and you'll be able to see the plugin in action from OpenFire's web interface, at `Server/Server Settings`. No need to restart the servers.

About
-----

[Openfire] is a XMPP server licensed under the Open Source Apache License.

[Openfire] - an [Ignite Realtime] community project.

[Openfire]: http://www.igniterealtime.org/projects/openfire/index.jsp
[Ignite Realtime]: http://www.igniterealtime.org
[XMPP (Jabber)]: http://xmpp.org/
