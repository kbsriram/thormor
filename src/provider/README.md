#Provider implementations#

This directory contains a set of sample local and remote provider
implementations.

##Home directory based local provider##

This local provider may be used for desktops. It saves files under a
directory called ".thormorvault" in the user's home directory.

##Google Sites remote provider##

This remote provider hosts remote content on a user's Google Sites
page. The actual content in files under a file cabinet named "Thormor
Vault."

**Note:** The user must first create a google site before using the
  provider, and provide OAuth mediated access to manage it. The code
  currently uses (an installed app-style) key to identify the app
  name. If you reuse this code, you should register your app and
  replace the keys in
  org/thormor/provider/remote/googlesites/keys.properties
