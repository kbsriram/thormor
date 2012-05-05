package org.thormor.vault;

/**
 * A linked vault represents a thormor vault which the
 * library user can poll for updates, or post messages
 * for the owner of the linked vault.
 *
 */

import org.bouncyrattle.openpgp.PGPPublicKeyRing;
import org.bouncyrattle.openpgp.PGPPublicKey;

import java.net.URL;

public class CLinkedVault
{
    CLinkedVault(URL vaultid, URL outbox_list, URL pubkeyurl,
                 PGPPublicKeyRing pkr)
    {
        m_id = vaultid;
        m_outbox_list_url = outbox_list;
        m_pubkey_url = pubkeyurl;
        m_pubkeyring = pkr;
        m_signkey = CPGPUtils.getMasterKey(pkr);
        m_enckey = CPGPUtils.getEncryptionKey(pkr);
    }

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

    private final URL m_id;
    private final URL m_outbox_list_url;
    private final URL m_pubkey_url;
    private final PGPPublicKeyRing m_pubkeyring;
    private final PGPPublicKey m_enckey;
    private final PGPPublicKey m_signkey;
}
