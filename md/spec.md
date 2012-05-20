#Thormor Vault#

A **Thormor vault** is a way to organize files online to form a
distributed, secure and private message exchange system.

##Why##

Current ways to post updates or share content lets the service
provider peek into what's being shared (Facebook, Google+, Gmail,
Dropbox, Path etc.)  Providers often track and analyze the user's
actions and content, and monetize the resultant profile. This raises
concerns when sharing sensitive material through such services --
anything from pictures of kids to financial or medical data.

However, the technology to let us share content privately has been
around for a long time. By first encrypting content with the
recipient's public key, we can exchange sensitive data without
revealing the contents anyone except the recipient.

If we formalize this idea, we can write client applications that let
users exchange private, secure messages using just online storage. The
user gets full control over who can read their messages, without
needing to expose the contents to a provider.

##Summary##

Users store messages online, PGP encrypted with the recipient's public
key. Recipients access the sender's storage to fetch messages, and
decrypt them locally with their private key.

##Assumptions and goals##

0. Any online service may inspect, modify or reveal messages to anyone.

1. Protect all messages transmitted or stored online, so only the
   intended recipients can understand the message.

2. Open specification, anyone should be able to write an application
   to host and share messages.

##Not goals##

1. Discovering vaults. How do you find the thormor vault for a
   particular person?

2. Traffic analysis and anonymity. It may be possible for storage
   providers to use data access patterns to identify connections
   between individuals even if the content being exchanged is
   encrypted.

3. Securing content locally. Once a message is decrypted on a user's
   device, the contents are visible to other applications running
   locally.

##High-level structure##

<img src="images/vault.png" alt="Structure of a Thormor Vault"
     style="height:29.875em;"/>

0. Encrypted data is stored online, publicly downloadable by anyone who
   has a link to it.

1. A vault is identified by a URL that points to a signed JSON file in
   the cloud. This is the **root** file, which points to other files
   of interest.

2. The root file contains a URL to the user's PGP public key and a
   URL to an **outbox list** file.

3. The outbox list is a signed JSON file with a list of URLs, each
   pointing to an **outbox** file. There may be one or more outbox
   files for each recipient who can access the vault.

4. Each outbox file is an encrypted JSON file with entries, one for
   each message. A message may internally refer to other encrypted
   files, depending on the type of message. For instance, a message
   sharing a picture contains metadata about the picture as well as a
   URL to the image itself.

   The outbox file and any referenced files are encrypted with the
   recipient's public key, so only the recipient can read this outbox.

5. To send a message to a list of recipients, the sender first adds an
   outbox entry, one for each recipient. The sender then updates the
   outbox file for each recipient (after encrypting the outbox file
   with the recipient's public key and signing it.)

6. To fetch messages, the receiver polls the sender's outbox URL for
   that recipient, and decrypts it locally to recover the message
   entries.

7. Senders may also choose to create **detached** messages. Such
   messages do not require the outbox mechanism. A detached message is
   stored as a separate file, and the URL to this file is directly
   used to retrieve the message.

8. URLs to detached messages may be used to send secure messages over
   an existing communication channel with a recipient. For instance,
   the URL can be sent as a private message on twitter or facebook
   with a hashtag to identify it as a detached message. Client
   applications can detect such messages, fetch the URL and decrypt it
   locally.

##Details##

###General guidelines###

- Data is typically signed with the sender's key, and clients *must*
  validate the signature.

- When data is also encrypted, the PGP anonymous mode is used (keyid
  in the header set to 0.) This prevents casually determining who can
  decrypt a given file.

- Unless stated, both ascii-armored and binary formatted PGP files
  can be used.

###The root file###

  A thormor vault is identified by a unique URL, say
  <tt>https://dl.dropbox.com/u/1234/thormor/root.asc</tt>

This URL must return a signed JSON file. For example, an ascii-armored
file may look like:

    -----BEGIN PGP MESSAGE-----
    Version: GnuPG v1.4.11 (Darwin)
    
    owGbwMvMwMRsvMCfg/GWwyXGNe+S2Ir0sorz8/xn7j5TzaUABEplqUXFmfl5SlYK
    hjoQkeLUnDQgVymjpKSg2EpfPyVHL6UovyApv0IvOT9Xv1Tf0MjYRL8kI78oN79I
    vyg/v0QvsThZCaK5oDQpJzM5Pju1kkQjCrKLlKAOyC8tAaqMz8ksLiHeECRNEOfU
    cnUyyrAwMDMxsLEygTzMwMUpAA8LE/b/xSJ33wgc7zDpLv/kcW+/9MZtKbX/9OL4
    5x4U+u2iNn3lE5O5jJ/S5G695d/zPX4Sc3lHRQJjxQR5jntqororFojrbxAxsznc
    1q/420p23mTBlr/n5A9lF+Q8tG5cfuBcw2Xj04vPV7cdvHlIaKbLm3s/D89q+b9b
    yyb/S7bWjJLofxkC/vYvLOuY+XLrLPiFlm38tFl6mrlLe9WC0yL6x5eHHZmf2DxB
    59FWxUO7fnySzz5v92P/DOnt81ZzyAq4nzJbeHNt5NO1PNs+HlzVxPznwasnfKxH
    AtwCd0cILNtXvvPp338WTMmejuL3/Vm/zM2We/b47YtPHW4TdiecNCtzkXm9zft/
    zu3pYo+MTwMA
    =VCPz
    -----END PGP MESSAGE-----

The contents must be a JSON file, in the above example it is:

    {
        "version": 1,
        "self": "https://dl.dropbox.com/u/1234/thormor/root.asc"
        "public_key": "https://dl.dropbox.com/u/1234/thormor/root.pkr",
        "outbox_list": "https://dl.dropbox.com/u/1234/thormor/outbox_list.asc"
    }

**Required fields**

-  version: 1
-  self: URL to the root file.
-  public_key: URL to the public key for the user who owns the vault.
-  outbox_list: URL to a signed JSON file containing a list of outbox urls.


###The public_key file###

This is a standard PGP public key.

**Note:** Keys should be independently verified using the standard
mechanisms that PGP provides, ie: directly verifying key fingerprints.
A thormor vault itself provides no guarantees that the public key
actually belongs to any given individual.

###The outbox_list file###

The outbox_list URL must return a signed JSON file. The contents must
be a JSON file with a list of outbox entries, for example:

    {
      "version": 1,
      "outbox_list": [
         { "outbox": "-----BEGIN PGP MESSAGE-----\\n...END PGP MESSAGE-----" },
         { "outbox": "-----BEGIN PGP MESSAGE-----\\n...END PGP MESSAGE-----" },
         ...
      ]
    }

Outbox URLs are not directly represented in the outbox list. Instead,
each outbox URL is encrypted with that recipient's public key, and the
ascii-armored version is used as the outbox value.

This is mainly to prevent casually obtaining a list of recipient ids
who can access the vault, but serves no other cryptographic purpose.

Recipients download the outbox list from a sender's thormor vault and
try to decrypt each entry with the recipient's key. If an entry is
successfully decrypted, it uses the decrypted content as a URL
pointing to the outbox file for that recipient.

**Note:** there may be multiple entries for the same recipient in the
outbox file. This is not an error -- the recipient is expected to look
for messages in all outboxes it can find. The reason for this is to
simplify storing messages from multiple devices to a single
vault. Each device may choose to add an outbox file unique to messages
originating from that device, which makes it a little easier to avoid
clobbering messages from other devices.

**Required fields**

- version: 1
- outbox_list: An array of JSON objects, one for each recipient who
   can fetch content from this thormor vault.<br/>
   Each JSON object has one field.
       - outbox:
         An ascii-armored string with encrypted content. The content
         is simply the URL to the outbox file for the recipient. The
         URL string is first encrypted with the recipient's public
         key, then ascii-armored to create the entry. It is not
         necessary to sign it (as the entire file is already signed.)

**Note:** implementations should maintain the same outbox URL for a
recipient as new messages are added, and update the contents of the
outbox file rather than changing the location of the outbox file.


###The outbox file###

The outbox URL for a recipient points to an signed, encrypted JSON
file. The format is intended to be extended by applications, and any
unknown fields must be skipped. An example file might be:

    {
      "version": 1,
      "name": "KB Sriram",
      "profile_image": "https://box.net/santoehu/content/2983.pgp",
      "entries": [
         {
            "id": "fdfbdd42877c6e2bab0a73531caeed173172ceec",
            "type": "thormor/file",
            "mime-type": "application/pdf",
            "src": "https://box.net/santoehu/content/82837.pgp",
            "text": "Mortgage details",
            "name": "home_mortgage.pdf",
            "size": 988284,
            "created": 13848992828,
            "tags": ["finance"]
         },
         {
            "id": "e2e3b086c3f3c674e161bb02841a44a8b346dd7c",
            "type": "thormor/file",
            "mime-type": "image/jpeg",
            "src": "https://box.net/santoehu/content/a9oeu7.pgp",
            "text": "Kids at the ballpark!",
            "name": "IMG_0402.JPG",
            "size":  947520,
            "created": 13848992828,
            "tags": ["kids", "family"]
         }
       ]
    }

**Required fields**

-  version: 1
-  entries:
    an array of JSON objects, one for each message. Each message
    object must have these fields:
      - type: the type of message. Some pre-defined types are
             available, and implementations may define additional
             ones. Pre-defined types are described later.
      - id: A unique string for this entry. Other entries may refer
           to this id.
      - created: milliseconds from the unix epoch, indicating when the
           message was created. Entries *should* be listed in reverse
           chronological order.
      - tags: (optional) An array of strings, each string is a
           free-form tag for that message.

**Optional fields**

-  next: URL to another outbox file, with the next set of messages for
        the recipient.
-  profile_image: URL to an encrypted image for the vault owner.
-  name: The name of the vault owner.

##Pre-defined types for the outbox##

**Note:** All types must have the standard required fields (type, id and
created.) The rest of this only describes additional requirements on
the fields.

###type: thormor/file###
This is a generic way to share digital content.

**Required fields**

-  src: URL to encrypted file

**Optional fields**

-  mime-type: mime type for the associated content.
-  name: Suggested file name.
-  text: A message to go along with this file.
-  size: size of file in bytes.
-  thumbnail: URL to an encrypted "small" image to represent the contents.

###type: thormor/comment###
Add a comment to a previous message (can also be a comment, leading to
threaded comments.)

**Required fields**

-  parent: id for the message being commented.
-  text: Contents of the comment.

###type: thormor/like###
'like' a particular message.

**Required fields**

-  parent: id for the message being liked.
