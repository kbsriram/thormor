#Java Library#

The thormor java library provides convenient methods to fetch and
store messages to thormor vaults.

You supply provider code to store data in the cloud, the library does
the rest.

The api is divided into two groups -- one when using the library, and
the other for the provider.

Many apis may take a long time to run (especially from mobile
devices.) Long-running methods take a progress monitor argument, so
you can run such methods from a separate thread and also get notified
about its progress.

##Using the library##

**Initializing**

The library api is available by instantiating a Vault object.

    CVault vault = new CVault(remote_provider, local_provider);

The `remote_provider` is used when data must be stored online, and the
`local_provider` is used when storing configuration data locally (like
settings, key data and so on.)

A vault is in one of three states:

- uninitialized: the user has not set up their vault.

- locked: the user has set up their vault, but hasn't provided a
          password to unlock it.

- unlocked: the vault is unlocked and ready for use.

Always check whether a vault is unlocked before using methods that
request data from the vault, or you'll get an IllegalStateException.

**Create a new vault**

The library can create a new vault with the 
`createVault(pass_phrase, progress_monitor)` method.

This call generates a new PGP key and uploads the various files needed
to initialize an empty thormor vault. It also stores the private key
locally (encrypted with the provided password via standard PGP.)

**Unlocking a vault**

A locked vault can be unlocked with the `unlock(pass_phrase)`
method. This also reads the local configuration data for a vault and
prepares it for use.

**Link to another vault**

In order to fetch updates from another vault (or to store messages for
it) you must first link to the other vault with the
`linkVault(vault_url, alias, progress_monitor)` method.

This call downloads the public key for the thormor vault at the
provided url, and also updates a locally stored list of linked
vaults. You can get this list with the `getLinkedVaults()` method.

_tbd: remove linked vault_

**Fetching messages**

The library locally stores messages fetched from linked vaults. You
can get a merged, time sorted list of stored messages from all linked
vaults with the `fetchMessages()` method.

You can poll for messages from all linked vaults with the
`fetchMessages(progress_monitor)` method, or just a particular vault
with the `fetchMessagesFrom(linkedvault, progress_monitor)` method.

**Note:** The library fetches _only_ the message, which is a generic
json object. It does not fetch any referenced images or images. You
should use the `fetchContent(source, creator, target, progress_monitor)`
method to locally store additional data as appropriate.

**Sending messages**

The library can create a message for a set of linked vaults. (A linked
vault represents a recipient for whom you can post a message.)

To post a message, use the
`postMessage(List<linkedvault>, json, monitor)` method.

The json object is encrypted, and files in the online vault are
appropriately updated.

**Note:** You must first post any referenced content using the
`postContent(List<linkedvault>, source, monitor)` method,
and use the url returned to the monitor within your json message.

##Implementing remote storage providers##

The remote storage provider is a set of apis that you implement to store
data in the cloud.

Many remote providers will require creating and accessing things like
OAuth tokens to write to cloud storage. The library doesn't provide
any support here; the provider is expected to be initialized
independently. However, you may choose to use the `writeFileSecurely()`
api in CVault to save data encrypted with the vault's key.

To create a provider, you must implement two primary methods:

    upload(upload_info, monitor)
    download(download_info, monitor)

An upload is always from a file stored on the device, and returns a
URL to the online content.

`upload_info` contains

- file to be uploaded
- an ispublic flag to identify content that MUST be publicly
   accessible (eg: without needing OAuth tokens.) If the ispublic flag
   is false, you should store the content under a private URL if
   possible (eg: needs OAuth tokens.)
- either:
    a previously uploaded URL to update<br/>
       or<br/>
    suggested_path is a string that you may use to create a "nice"
    path for the eventual URL, if you can support it.

A download is always to a file stored on the device, and may refer to
content that has been previously downloaded. It returns a status
indicating whether a full download was performed, or there were no
changes, or that the content was not available.

`download_info` contains

- source url
- target local file
- any last_modified timestamp (or -1)
- any etag (or null)

##Implementing local storage providers##

A local storage provider is used to let you specify real `File`
locations on the device for various stored messages, configurations
and other data. You implement two methods:

    File getFileFor(path)
    File getCacheFileFor(path)

The path argument is effectively is a key for a location on the
device, and implementations must return the same underlying file if
the same path is used.
