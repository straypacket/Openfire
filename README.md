Openfire
========

This is a fork for in-house usage.

For help on the usage of this work in specific, go to the <a href="src/plugins/suJoin/">src/plugins/suJoin</a> directory.

Hacking OpenFire
----------------

Luckily, since March 31st 2014 they migrated from SVN to GitHub! I made a fork which can be clones with:

	git clone https://github.com/straypacket/Openfire.git

The plugins section, which is what we're omre interested in, are in `src/plugins/`. The base of our API plug is the directory `userservice`.

To compile plugins, just compile the whole project with from within the root of the project:
	
	make clean
	cd build
	ant plugins
	
Make sure you build the project with the same architecture as the one used in the server (i.e. both are 32 or 64 bit versions).

This process will compile the binary .jar file into `../work/plugins`
	
Now just copy the resulting .jar file into the servers. If the plugin works as expected, push your changes to git.

You should copy the userservice plugin (in our repo it's already called `suJoin`) and all the above commands should work without any problems.

SUJoin API
----------

To install self-developped plugins, that do not appear in the official plugin list, we need to copy the .jar file from the previous compilation to the `plugins` directory from the root openfire directory:

	scp -i AWS_KEY.pem work/plugins/suJoin.jar ubuntu@EC2-INSTANCE:openfire/plugins/
	
To install, unpack the .jar file with:

	unzip suJoin.jar
	
This will install the plugin and you'll be able to see the plugin in action from OpenFire's web interface, at `Server/Server Settings`. No need to restart the servers.


About
-----

[Openfire] is a XMPP server licensed under the Open Source Apache License.

[Openfire] - an [Ignite Realtime] community project.

[Openfire]: http://www.igniterealtime.org/projects/openfire/index.jsp
[Ignite Realtime]: http://www.igniterealtime.org
[XMPP (Jabber)]: http://xmpp.org/
