#Command line shell

This code implements a very simple command line shell to manage
thormor vaults.

By default, it uses a provider that creates files on one of your
[Google Sites](http://www.google.com/sites/help/intl/en/overview.html
"Learn more about Google Sites"), and which stores local data under a
directory on your computer.

##Running

Use the following command to enter a shell where you can manage
vaults.

    $ java -jar thormor-cli.jar
    No vault is currently available.
    Create a new vault [Y/n]?

This is typically what you will see when you run the application for
the first time. You can pass in options to change the location where
local files are saved, please run `java -jar thormor-cli.jar --help`
for details.


**Creating a new vault**

If you answer `y` (or just hit return) to the above dialog, it will
first remind you to create a Google Site if you don't have one
already. Then, it will take you through the process of authenticating
access to your site, and finally ask for a passphrase to protect your
vault.

<pre><code>
Create a new vault [Y/n]: <b>y</b>

NOTE: You must already have a Google Site in order to place a vault in
it. If you have never done this before, please visit
https://sites.google.com and create a new site for your vault.
Yes, I have a site [y/N]: <b>y</b>

Please authorize access by visiting
https://google.com/....
Enter the authorization code: <b>xxxxxxxx</b>

Fetching list of available sites...
You have 2 sites available. Which one do you want to use?
[1] https://sites.google.com/site/kbsriram
[2] https://sites.google.com/site/thormorspace
Use this site to host my vault [1-2]: <b>2</b>

Your vault will be protected with a passphrase.
Select passphrase: <b>xxxxxxxxxxxx</b>
Reenter passphrase: <b>xxxxxxxxxxxx</b>

Creating keys...
Uploading vault...
Congratulations. Your new vault is located at
https://ntoaeusoeateu/ontoenu
Files are locally stored under
/Users/kbs/Vault
> <b>exit</b>
</code></pre>

At this point you have an empty vault. All the configuration data
(including your private keys) is saved locally under the `config/`
directory of your local vault directory. The next thing to do is to
link to someone else's vault in order to exchange messages with
them.

For demonstration purposes, let's just create a second vault under a
different root.

<pre><code>
java -jar thormor-cli.jar --local-root=/Users/kbs/Vault2
Local files will be saved under /Users/kbs/Vault2
No vault is currently available.
Create a new vault [Y/n]? <b>y</b>
...
</code></pre>

We'll place this vault in a different google site, and eventually get
to something like this.

<pre></code>
Congratulations. Your new vault is located at
https://ntoaeusoeateu/ontoenu
Files are locally stored under
/Users/kbs/Vault2
> 
</code></pre>

We can now link this vault to our first vault, and give it a name so
we can refer to it conveniently.
<pre><code>
> <b>link http://snote first-vault</b>
Fetching data from ...
Successfully linked http://snote as first-vault
The vault has the fingerprint
0910202030239052
Please verify this fingerprint to make sure you
have linked to the real owner.
> 
</code></pre>

The fingerprint identifies the owner (and signer) of content from the
linked vault. You should independently verify this fingerprint when
linking to a vault. The `fingerprint` command will list all the
fingerprints in the vault.

Let's proceed and send a simple status update message.

<pre><code>
> <b>share message</b>
Please type in your message, ending it with a "." on a line by itself.
<b>This is the second vault saying hello!
.</b>
Sending message...
Message sent.
> <b>exit</b>
</code></pre>

We can now load the first vault, link it to the second vault and fetch
messsages.

<pre><code>
$ java -jar thormor-cli.jar
Vault loaded from /Users/kbs/Vault
Passphrase: <b>xxxxxxxxx</b>
Vault unlocked.
> <b>link http://nsaotheu second-vault</b>
Fetching data from ....
Successfully linked http://nsaotheu as second-vault
The vault has the fingerprint
1234132423432
Please verify this fingerprint to make sure you have
linked to the real owner.
> <b>fetch</b>
Fetching messages...
Done.
> <b>list</b>
second-vault: May 12, 2001

Hello first vault.

This is the second vault saying hello!
> 
</code></pre>

You can also send files through the interface. For instance, the first
vault can send an image for the second.

<pre><code>
> <b>send file first-vault /Users/kbs/Pictures/test.jpg</b>
Please type in any message, ending it with a "." on a line by itself.
<b>Testing file share from first vault.
.</b>
Sending message...
Message sent.
> <b>exit</b>
</code></pre>

In turn, if we load up the second vault we can fetch messages for it.

<pre><code>
$ java -jar thormor-cli.jar --local-dir=/Users/kbs/Vault2
Vault loaded from /Users/kbs/Vault2
Passphrase: <b>xxxxxxxxx</b>
Vault unlocked.
> <b>fetch</b>
Fetching messages...
Done.
> <b>list</b>
first-vault: May 12, 2012

Testing file share from first vault.
Attached file: /Users/kbs/Vault2/inbox/first-vault/test.jpg
> <b>exit</b>
</code></pre>

You may notice that this particular client locally downloads messages
and files to the `inbox/` folder in the vault directory.
