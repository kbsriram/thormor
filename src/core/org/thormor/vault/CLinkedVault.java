package org.thormor.vault;

/**
 * A linked vault represents a thormor vault which the
 * library user can poll for updates, or post messages
 * for the owner of the linked vault.
 *
 */

import org.bouncyrattle.openpgp.PGPPublicKeyRing;
import org.bouncyrattle.openpgp.PGPPublicKey;

import org.json2012.JSONObject;
import org.json2012.JSONTokener;
import org.json2012.JSONException;
import org.json2012.JSONArray;

import java.net.URL;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.File;

public class CLinkedVault
{

    /**
     * The id of the vault -- this is the URL to the root json for the vault
     */
    public URL getId()
    { return m_id; }

    /**
     * The url to the outbox_list hosted by the vault.
     */
    public URL getOutboxListURL()
    { return m_outbox_list_url; }

    /**
     * The location of the public key hosted by the vault.
     */
    public URL getPublicKeyURL()
    { return m_pubkey_url; }

    /**
     * The public key for this vault.
     */
    public PGPPublicKeyRing getPublicKeyRing()
    { return m_pubkeyring; }

    /**
     * Key to use when encrypting content for this vault.
     */
    public PGPPublicKey getEncryptionKey()
    { return m_enckey; }

    /**
     * Key used when the linked vault signs content.
     */
    public PGPPublicKey getSigningKey()
    { return m_signkey; }

    /**
     * The URL to the outbox where we publish messages for
     * this linked vault
     */
    public URL getPublishOutboxURL()
    { return m_publish_outbox_url; }

    // package methods
    CLinkedVault
        (CVault root, URL vaultid, URL outbox_list, URL pubkeyurl,
         PGPPublicKeyRing pkr, URL publish_outbox_url)
    {
        m_root = root;
        m_id = vaultid;
        m_outbox_list_url = outbox_list;
        m_pubkey_url = pubkeyurl;
        m_pubkeyring = pkr;
        m_publish_outbox_url = publish_outbox_url;
        m_signkey = CPGPUtils.getMasterKey(pkr);
        m_enckey = CPGPUtils.getEncryptionKey(pkr);

        // key used to store files with the inbox and outbox for this
        // recipient.
        m_msg_key = CUtils.shasum(vaultid.toString());
    }


    // take the provided message and merge it into the current
    // outbox message store. Return back the merged message outbox
    // if there were changes, or null.
    JSONObject mergeLocalOutbox(JSONObject msg)
        throws IOException
    {
        // 1. load the outbox from file.
        JSONObject outbox = readLocalOutbox();

        // 2. Check the entries array (by id) to see if we
        // have already seen this message.
        JSONArray entries = outbox.optJSONArray("entries");
        int len = entries.length();
        for (int i=0; i<len; i++) {
            JSONObject entry = entries.optJSONObject(i);
            if (msg.optString("id").equals(entry.optString("id"))) {
                // already present, flag no changes.
                return null;
            }
        }
        // splice message into head of our entries.
        entries.put(msg, 0);
        // save outbox
        return writeLocalOutbox(m_root, m_msg_key, outbox);
    }

    JSONObject readLocalOutbox()
        throws IOException
    {
        InputStream inp = m_root.readFileSecurely
            (makeLocalOutboxPath(m_msg_key));

        try {
            JSONTokener tok  =
                new JSONTokener
                (new InputStreamReader(inp));
            return new JSONObject(tok);
        }
        catch (JSONException jse) {
            throw new IOException(jse);
        }
        finally {
            inp.close();
        }
    }

    // package protected static
    static File createLocalOutboxFor(CVault root, URL vaultid)
        throws IOException
    {
        String msg_key = CUtils.shasum(vaultid.toString());

        File local_outbox = root.getSecureFile(makeLocalOutboxPath(msg_key));

        if (!local_outbox.exists()) {
            // 1. Create an empty one
            JSONObject empty = new JSONObject();
            CUtils.put(empty, "version", 1);
            CUtils.put(empty, "entries", new JSONArray());
            writeLocalOutbox(root, msg_key, empty);
        }
        return local_outbox;
    }

    String makeInboxRootPath()
    { return "my/inbox/"+m_msg_key+"/inbox.json"; }

    // private methods
    private static JSONObject writeLocalOutbox
        (CVault root, String key, JSONObject json)
        throws IOException
    {
        root.writeFileSecurely
            (makeLocalOutboxPath(key), CUtils.getBytes(json.toString()));
        return json;
    }

    private static String makeLocalOutboxPath(String key)
    { return "my/outbox/"+key; }

    private final CVault m_root;
    private final URL m_id;
    private final URL m_outbox_list_url;
    private final URL m_pubkey_url;
    private final URL m_publish_outbox_url;
    private final String m_msg_key;
    private final PGPPublicKeyRing m_pubkeyring;
    private final PGPPublicKey m_enckey;
    private final PGPPublicKey m_signkey;
}
