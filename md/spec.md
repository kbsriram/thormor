#Thormor Vault Specification#

This document describes the format and organization of thormor
vaults. Please read the
[introduction to vaults](index.html "Introduction to Thormor Vaults")
for additional background.

##General guidelines##

- Thormor Vaults use [OpenPGP](http://www.ietf.org/rfc/rfc4880.txt)
  for all encryption and signing.

- Data is signed with the sender's key, and clients *must* validate
  the signature.

- When data is also encrypted, the PGP anonymous mode is used (keyid
  in the header set to 0.) This prevents casually determining who can
  decrypt a given file.

- Unless stated, both ascii-armored and binary formatted PGP files
  may be used.

##File organization and details##

Unless otherwise noted, files are in JSON format. All content must be
publicly accessible through stable URLs.

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
        "public_key": "https://dl.dropbox.com/u/1234/thormor/root.pkr",
        "outbox_list": "https://dl.dropbox.com/u/1234/thormor/outbox_list.asc"
    }

####Required fields####

-  version: 1
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

####Required fields####

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

The outbox URL for a recipient points to an signed JSON file encrypted
with the recipients public key. The format is intended to be extended
by applications, and any unknown fields must be skipped. An example
file (before encryption) might be:

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

####Required fields####

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

####Optional fields####

-  next: URL to another outbox file, with the next set of messages for
        the recipient.
-  profile_image: URL to an encrypted image for the vault owner.
-  name: The name of the vault owner.

##Pre-defined types for messages##

**Note:** All types must have the standard required fields (type, id and
created.) The rest of this only describes additional requirements on
the fields.

###type: thormor/text###
This message type lets you share a simple text message.

####Required fields####

-  text: The contents of the text message.

###type: thormor/file###
This is a generic way to share digital content.

####Required fields####

-  src: URL to encrypted file

####Optional fields####

-  mime-type: mime type for the associated content.
-  name: Suggested file name.
-  text: A message to go along with this file.
-  size: size of file in bytes.
-  thumbnail: URL to an encrypted "small" image to represent the contents.

###type: thormor/comment###
Add a comment to a previous message (can also be a comment, leading to
threaded comments.)

####Required fields####

-  parent: id for the message being commented.
-  text: Contents of the comment.

###type: thormor/like###
'like' a particular message.

####Required fields####

-  parent: id for the message being liked.

##Detached Messages##

A detached message is just an encrypted (and signed) JSON file for an
individual message, and exists independently rather than being an
element in the `entries` array in an outbox.

Such messages do not need any of the outbox data structures, and may
be easier to create and maintain. URLs to such files are embedded
within pre-existing communication channels between the sharers. (eg:
in a status update to a set of people on twitter or facebook.) Client
applications for the recipients can directly access and decrypt such a
message, thereby providing a private, secure layer within that system.
