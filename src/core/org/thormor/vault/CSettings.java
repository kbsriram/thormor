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
import org.json2012.JSONTokener;
import org.json2012.JSONException;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.InputStreamReader;
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

import java.net.URL;
import java.net.MalformedURLException;

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

    CLinkedVault addLinkedVault(URL url, JSONObject root, PGPPublicKeyRing pkr)
        throws IOException
    {
        String outbox_list = root.optString("outbox_list");
        if (outbox_list == null) {
            throw new IOException("Missing outbox_list from "+url);
        }
        CLinkedVault ret =
            new CLinkedVault
            (url, new URL(outbox_list),
             new URL(root.optString("public_key")), pkr);

        m_linked.put(url, ret);

        // update vault settings.
        saveVaultSettings();
        return ret;
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

    void saveVaultSettings()
        throws IOException
    {
        JSONObject settings = new JSONObject();
        CUtils.put(settings, "self", m_self_url.toString());
        CUtils.put(settings, "outbox_list", m_outbox_list_url.toString());
        CUtils.put(settings, "public_key", m_public_key_url.toString());

        JSONObject linked_vaults = new JSONObject();
        CUtils.put(settings, "linked_vaults", linked_vaults);
        for (CLinkedVault lv: m_linked.values()) {
            JSONObject vault_info = new JSONObject();
            CUtils.put
                (vault_info,"outbox_list",lv.getOutboxListURL().toString());
            CUtils.put
                (vault_info,"public_key",lv.getPublicKeyURL().toString());
            CUtils.put
                (vault_info,"public_key_data",
                 ring2String(lv.getPublicKeyRing()));
            CUtils.put(linked_vaults, lv.getId().toString(), vault_info);
        }

        ByteArrayInputStream bis =
            new ByteArrayInputStream(CUtils.getBytes(settings.toString()));
        m_vault.writeFileSecurely(VAULT_SETTINGS_PATH, bis);
        bis.close();
    }

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
        throws IOException, JSONException, MalformedURLException
    {
        // Now initialize the rest of the settings
        // from this object.
        m_self_url = new URL(settings.getString("self"));
        m_outbox_list_url = new URL(settings.getString("outbox_list"));
        m_public_key_url = new URL(settings.getString("public_key"));

        ILocalProvider lp = m_vault.getLocalProvider();

        JSONObject linked_vaults = settings.optJSONObject("linked_vaults");
        if (linked_vaults != null) {
            for (Iterator it=linked_vaults.keys(); it.hasNext();) {
                String id = (String)(it.next());
                JSONObject vault_info = linked_vaults.getJSONObject(id);
                URL vaultid = new URL(id);
                URL outbox_list = new URL(vault_info.getString("outbox_list"));
                URL pubkey = new URL(vault_info.getString("public_key"));
                PGPPublicKeyRing pkr = string2Ring
                    (vault_info.getString("public_key_data"));

                m_linked.put
                    (vaultid, new CLinkedVault
                     (vaultid, outbox_list, pubkey, pkr));
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

    boolean isLocked()
    { return (m_signkey == null) || (m_enckey == null); }

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
    private Map<URL, CLinkedVault> m_linked =
        Collections.synchronizedMap(new HashMap<URL, CLinkedVault>());

    private final static String SETTINGS_PFX = "my/";
    private final static String PUBKEY_PATH=SETTINGS_PFX+"vault.pkr";
    private final static String SECKEY_PATH=SETTINGS_PFX+"vault.skr";
    private final static String VAULT_SETTINGS_PATH=SETTINGS_PFX+"vault.json";

    private final static Logger s_logger =
        Logger.getLogger(CPGPUtils.class.getName());
}