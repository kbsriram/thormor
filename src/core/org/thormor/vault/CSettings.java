package org.thormor.vault;

// Host various user settings like public, private keys,
// keys to linked vaults, etc.

import org.thormor.provider.ILocalProvider;

import org.bouncyrattle.openpgp.PGPPublicKeyRing;
import org.bouncyrattle.openpgp.PGPPublicKey;
import org.bouncyrattle.openpgp.PGPSecretKeyRing;
import org.bouncyrattle.openpgp.PGPSecretKey;
import org.bouncyrattle.openpgp.PGPPrivateKey;
import org.bouncyrattle.openpgp.PGPException;
import org.bouncyrattle.bcpg.ArmoredOutputStream;

import org.json2012.JSONObject;
import org.json2012.JSONArray;
import org.json2012.JSONTokener;
import org.json2012.JSONException;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.InputStreamReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import java.util.Iterator;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;

import java.net.URL;
import java.net.MalformedURLException;

//
// Settings effectively represent a local datamodel for the
// remote vault. The datamodel looks roughly like this.
//
// settings: --> pubkey
//           --> privkey
//           --> outboxinfolist
//                   --> outboxinfo
//                           |
//                           +-----> linkedvault
//                                        |
//                                        +-->pubkey
//                                        +-->local_outbox_url

// Note that the outboxinfo may not always contain a linked vault.
// The reason is that the outboxlist is potentially updated by
// multiple clients, so only outboxinfos that were created on the
// current device are present. However, the encoded URL string for all
// outboxes are always present in the outboxinfo.

class CSettings
{
    static CSettings createEmpty
        (CVault vault, JSONObject rootjson,
         PGPPublicKeyRing pkr, PGPSecretKeyRing skr, String passphrase)
        throws CVaultException
    {
        CSettings ret = new CSettings(vault, pkr, skr);
        if (!ret.extractPrivateKeys(passphrase)) {
            throw new CVaultException("Unexpected -- cannot unlock");
        }
        if (rootjson != null) {
            try { ret.loadVaultSettings(rootjson); }
            catch (JSONException jse) {
                throw new CVaultException("Unexpected -- cannot load", jse);
            }
            catch (IOException ioe) {
                throw new CVaultException("Unexpected -- cannot load", ioe);
            }
        }
        return ret;
    }

    static CSettings maybeLoad(CVault vault)
        throws CVaultException
    {
        // Create settings from from saved keys
        ILocalProvider lp = vault.getLocalProvider();

        File pubkeyf = lp.getFileFor(PUBKEY_PATH);
        if (!pubkeyf.exists()) { return null; }
        File seckeyf = lp.getFileFor(SECKEY_PATH);
        if (!seckeyf.exists()) { return null; }

        try {
            return new CSettings
                (vault,
                 CPGPUtils.readPublicKeyRing(pubkeyf),
                 CPGPUtils.readSecretKeyRing(seckeyf));
        }
        catch (IOException ioe) {
            throw new CVaultException(ioe);
        }
    }

    private CSettings
        (CVault vault, PGPPublicKeyRing pkr, PGPSecretKeyRing skr)
    {
        m_vault = vault;
        m_pubkeyring = pkr;
        m_seckeyring = skr;
        m_pubenckey = CPGPUtils.getEncryptionKey(pkr);
        m_pubsignkey = CPGPUtils.getMasterKey(pkr);
    }

    boolean unlock(String password)
        throws CVaultException
    {
        // 1. First see if we can in fact get
        // private keys from the password.
        if (!extractPrivateKeys(password)) {
            return false;
        }

        // 2.
        loadVaultSettings();
        return true;
    }

    String getGUID()
    { return m_guid; }

    PGPPrivateKey getPrivateSigningKey()
    { return m_signkey; }
    PGPPrivateKey getPrivateEncryptionKey()
    { return m_enckey; }
    PGPPublicKey getPublicEncryptionKey()
    { return m_pubenckey; }
    PGPPublicKey getPublicSigningKey()
    { return m_pubsignkey; }

    CLinkedVault getLinkedVaultById(URL url)
    { return m_linked.get(url); }
    List<CLinkedVault> getLinkedVaults()
    { return new ArrayList<CLinkedVault>(m_linked.values()); }
    CLinkedVault getLinkedVaultByAlias(String alias)
    {
        for (CLinkedVault lv: m_linked.values()) {
            if (alias.equals(lv.getAlias())) {
                return lv;
            }
        }
        return null;
    }

    void addLinkedVault(CLinkedVault lv)
        throws IOException
    {
        // 1. create a new URL key, and add it in outboxinfo
        m_outboxinfo.add(new OutboxInfo(makeURLKey(lv), lv));

        // 2. also add it to our local linked vault
        m_linked.put(lv.getId(), lv);
    }

    void removeLinkedVault(CLinkedVault lv)
    {
        // first remove from m_outboxinfo
        synchronized (m_outboxinfo) {
            Iterator<OutboxInfo> it = m_outboxinfo.iterator();
            while (it.hasNext()) {
                OutboxInfo cur = it.next();
                CLinkedVault curlv = cur.getLinkedVault();
                if (curlv == null) { continue; }
                if (lv.getId().equals(curlv.getId())) {
                    it.remove();
                }
            }
        }

        // and also from m_linked
        m_linked.remove(lv.getId());
    }

    void saveKeys()
        throws IOException
    {
        ILocalProvider lp = m_vault.getLocalProvider();

        File pubkeyf = lp.getFileFor(PUBKEY_PATH);
        File seckeyf = lp.getFileFor(SECKEY_PATH);
        boolean ok = false;
        try {
            CPGPUtils.writePublicKeyRing(m_pubkeyring, pubkeyf);
            CPGPUtils.writeSecretKeyRing(m_seckeyring, seckeyf);
            ok = true;
        }
        finally {
            if (!ok) {
                pubkeyf.delete();
                seckeyf.delete();
            }
        }
    }

    URL getSelfURL()
    { return m_self_url; }

    URL getOutboxListURL()
    { return m_outbox_list_url; }

    // Create a JSONObject that can be used as an outboxlist on
    // the vault.
    JSONObject publishOutboxList()
    {
        JSONObject js = new JSONObject();
        CUtils.put(js, "version", 1);
        JSONArray outbox_list = new JSONArray();
        CUtils.put(js, "outbox_list", outbox_list);

        synchronized (m_outboxinfo) {
            for (OutboxInfo oi: m_outboxinfo) {
                JSONObject outbox = new JSONObject();
                CUtils.put(outbox, "outbox", oi.getURLKey());
                outbox_list.put(outbox);
            }
        }

        return js;
    }

    void saveVaultSettings()
        throws IOException
    {
        JSONObject settings = new JSONObject();
        CUtils.put(settings, "self", m_self_url.toString());
        CUtils.put(settings, "outbox_list", m_outbox_list_url.toString());
        CUtils.put(settings, "public_key", m_public_key_url.toString());
        CUtils.put(settings, "guid", m_guid);

        JSONObject outbox_list_info = new JSONObject();
        CUtils.put(settings, "outbox_list_info", outbox_list_info);

        // linked-vaults/outboxes are stored by their encrypted url
        // value. This allows merging outboxes created from other
        // devices. If the outbox comes from a vault linked on
        // the current device, extra info about the linked vault
        // is added.
        for (OutboxInfo oi: m_outboxinfo) {
            JSONObject outbox_info = new JSONObject();
            CUtils.put(outbox_list_info, oi.getURLKey(), outbox_info);
            // If we have an associated linked vault, put that data here.
            CLinkedVault lv = oi.getLinkedVault();
            if (lv != null) {
                CUtils.put
                    (outbox_info,"outbox_list",
                     lv.getOutboxListURL().toString());
                CUtils.put
                    (outbox_info,"public_key",lv.getPublicKeyURL().toString());
                CUtils.put
                    (outbox_info,"public_key_data",
                     ring2String(lv.getPublicKeyRing()));
                CUtils.put
                    (outbox_info,"vault_id",lv.getId().toString());
                CUtils.put
                    (outbox_info,"publish_url",
                     lv.getPublishOutboxURL().toString());
                String alias = lv.getAlias();
                if (alias != null) {
                    CUtils.put(outbox_info, "alias", alias);
                }
            }
        }

        m_vault.writeFileSecurely
            (VAULT_SETTINGS_PATH, CUtils.getBytes(settings.toString()));
    }

    boolean isLocked()
    { return (m_signkey == null) || (m_enckey == null); }

    // private helper methods

    private void loadVaultSettings()
        throws CVaultException
    {
        Reader r = null;
        try {
            r = new InputStreamReader
                (m_vault.readFileSecurely
                 (VAULT_SETTINGS_PATH));
            loadVaultSettings(new JSONObject(new JSONTokener(r)));
        }
        catch (IOException ioe) {
            throw new CVaultException(ioe);
        }
        catch (JSONException jse) {
            throw new CVaultException(jse);
        }
        finally {
            if (r != null) {
                try { r.close(); }
                catch (Throwable th) {}
            }
        }
    }

    private void loadVaultSettings(JSONObject settings)
        throws IOException, JSONException
    {
        // Now initialize the rest of the settings
        // from this object.
        m_self_url = new URL(settings.getString("self"));
        m_outbox_list_url = new URL(settings.getString("outbox_list"));
        m_public_key_url = new URL(settings.getString("public_key"));
        m_guid = settings.getString("guid");

        ILocalProvider lp = m_vault.getLocalProvider();

        JSONObject outbox_list_info=settings.optJSONObject("outbox_list_info");
        if (outbox_list_info != null) {
            for (Iterator it=outbox_list_info.keys(); it.hasNext();) {
                String urlkey = (String)(it.next());
                JSONObject outbox_info = outbox_list_info.getJSONObject(urlkey);
                CLinkedVault lv = null;
                if (outbox_info.optString("vault_id") != null) {
                    URL vaultid = new URL(outbox_info.optString("vault_id"));
                    String alias = outbox_info.optString("alias");
                    URL outbox_list =
                        new URL(outbox_info.getString("outbox_list"));
                    URL pubkey = new URL(outbox_info.getString("public_key"));
                    URL outbox_url =
                        new URL(outbox_info.getString("publish_url"));
                    PGPPublicKeyRing pkr = string2Ring
                        (outbox_info.getString("public_key_data"));
                    lv = new CLinkedVault
                        (m_vault,vaultid,alias,outbox_list,pubkey,pkr,outbox_url);
                    m_linked.put(vaultid, lv);
                }
                m_outboxinfo.add(new OutboxInfo(urlkey, lv));
            }
        }
    }

    private String ring2String(PGPPublicKeyRing pkr)
        throws IOException
    {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        ArmoredOutputStream aout = new ArmoredOutputStream(bout);
        pkr.encode(aout);
        aout.close();
        return new String(bout.toByteArray(), "utf-8");
    }

    private PGPPublicKeyRing string2Ring(String s)
        throws IOException
    {
        ByteArrayInputStream bin =
            new ByteArrayInputStream(s.getBytes("utf-8"));
        return CPGPUtils.readPublicKeyRing(bin);
    }

    @SuppressWarnings({"unchecked"})
    private boolean extractPrivateKeys(String password)
        throws CVaultException
    {
        Iterator<PGPSecretKey> pski =
            (Iterator<PGPSecretKey>)
            (m_seckeyring.getSecretKeys());
        PGPPrivateKey enc_key = null;
        PGPPrivateKey sign_key = null;

        while (pski.hasNext()) {
            PGPSecretKey skey = pski.next();
            PGPPrivateKey pkey;
            try { pkey = CPGPUtils.extractPrivateKey(skey, password); }
            catch (PGPException pe) {
                return false;
            }

            PGPPublicKey pubkey = skey.getPublicKey();

            // NB: the master key is used for signing, while
            // the encryption-only key is used for decryption
            if (pubkey.isMasterKey()) { sign_key = pkey; }
            else if (pubkey.isEncryptionKey()) { enc_key = pkey; }
            else {
                s_logger.info
                    ("hmm: "+pubkey.getAlgorithm()+" not master, not signer");
            }
        }
        if (sign_key == null) {
            throw new CVaultException("no master signing key found!");
        }
        if (enc_key == null) {
            throw new CVaultException("no encryption subkey found");
        }
        m_signkey = sign_key;
        m_enckey = enc_key;
        return true;
    }

    // the URL key is an ascii-armored encrypted, signed message
    // for the recipient pointing to the outbox url
    private String makeURLKey(CLinkedVault lv)
        throws IOException
    {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        ArmoredOutputStream aout = new ArmoredOutputStream(bout);
        List<PGPPublicKey> recip = new ArrayList<PGPPublicKey>();
        recip.add(lv.getEncryptionKey());
        byte[] buf = lv.getPublishOutboxURL().toString().getBytes();
        ByteArrayInputStream bin = new ByteArrayInputStream(buf);
        CPGPUtils.encrypt
            (bin, buf.length, aout, recip,
             getPublicSigningKey(), getPrivateSigningKey(),
             "outbox.json", new Date());
        aout.close();
        return new String(bout.toByteArray(), "utf-8");
    }

    private final CVault m_vault;
    private final PGPPublicKeyRing m_pubkeyring;
    private final PGPSecretKeyRing m_seckeyring;
    private final PGPPublicKey m_pubenckey;
    private final PGPPublicKey m_pubsignkey;
    private PGPPrivateKey m_signkey = null;
    private PGPPrivateKey m_enckey = null;
    private URL m_self_url = null;
    private URL m_outbox_list_url = null;
    private URL m_public_key_url = null;
    private String m_guid = null;
    private Map<URL, CLinkedVault> m_linked =
        Collections.synchronizedMap(new HashMap<URL, CLinkedVault>());
    private List<OutboxInfo> m_outboxinfo =
        Collections.synchronizedList(new ArrayList<OutboxInfo>());

    private final static String SETTINGS_PFX = "my/";
    private final static String PUBKEY_PATH=SETTINGS_PFX+"vault.pkr";
    private final static String SECKEY_PATH=SETTINGS_PFX+"vault.skr";
    private final static String VAULT_SETTINGS_PATH=SETTINGS_PFX+"vault.json";

    // static helper class
    private final static class OutboxInfo
    {
        private OutboxInfo(String urlkey, CLinkedVault lv)
        {
            m_urlkey = urlkey;
            m_lv = lv;
        }
        private String getURLKey()
        { return m_urlkey; }
        private CLinkedVault getLinkedVault()
        { return m_lv; }
        private final String m_urlkey;
        private final CLinkedVault m_lv;
    }

    private final static Logger s_logger =
        Logger.getLogger(CPGPUtils.class.getName());
}