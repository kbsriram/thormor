package org.thormor.vault;

/**
 * A vault represents secured online storage; and you can fetch or add
 * content to it.
 */

import org.thormor.provider.IRemoteProvider;
import org.thormor.provider.ILocalProvider;
import org.thormor.provider.IProgressMonitor;
import org.thormor.provider.CUploadInfo;
import org.thormor.provider.CDownloadInfo;

import org.bouncyrattle.openpgp.PGPPublicKey;
import org.bouncyrattle.openpgp.PGPPublicKeyRing;
import org.bouncyrattle.openpgp.PGPSecretKey;
import org.bouncyrattle.openpgp.PGPSecretKeyRing;
import org.bouncyrattle.openpgp.PGPKeyRingGenerator;

import org.json2012.JSONObject;
import org.json2012.JSONArray;
import org.json2012.JSONException;

import java.io.File;
import java.io.IOException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;
import java.net.URL;
import java.util.UUID;

import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class CVault
{
    public enum State {
        UNINITIALIZED, LOCKED, UNLOCKED
    };

    public CVault(IRemoteProvider rp, ILocalProvider lp)
        throws CVaultException
    {
        m_rprovider = rp;
        m_lprovider = lp;
        m_settings = CSettings.maybeLoad(this);
    }

    public State getState()
    {
        if (m_settings == null) {
            return State.UNINITIALIZED;
        }
        if (m_settings.isLocked()) {
            return State.LOCKED;
        }
        return State.UNLOCKED;
    }

    /**
     * Return the vault id (which is also the URL to the root json file.)
     * This method may be called only when the vault is unlocked.
     */
    public URL getId()
    {
        check(State.UNLOCKED);
        return m_settings.getSelfURL();
    }

    /**
     * This method will create a brand new vault, possibly destroying
     * any existing vault data. As a safety measure, a new vault can
     * only be created if it is in the UNINITIALIZED state.
     *
     * @param phrase to use to lock the vault
     * @param monitor is a progress monitor to recieve status updates.
     */
    public void createVault(String passphrase, IProgressMonitor monitor)
        throws IOException
    {
        check(State.UNINITIALIZED);

        if (monitor != null) {
            monitor.status("Generating keys");
        }

        // Create a new keypair with the provided password.
        PGPKeyRingGenerator krgen = CPGPUtils.generateKeyRingGenerator
            ("thormor-vault", null, passphrase);

        PGPPublicKeyRing pkr = krgen.generatePublicKeyRing();
        PGPSecretKeyRing skr = krgen.generateSecretKeyRing();

        // 0. Create a temporary settings to handle key-details
        CSettings tmpsettings = CSettings.createEmpty
            (null, null, pkr, skr, passphrase);

        // 1. Dump public key to temp file and upload.
        if (monitor != null) {
            monitor.status("Upload public key to vault");
        }
        File tmpf = File.createTempFile("thormor", "pkr");
        CPGPUtils.writePublicKeyRing(pkr, tmpf);
        URL pk_url = m_rprovider.upload
            (new CUploadInfo(tmpf, true, "root.pkr"), monitor);
        tmpf.delete();

        // 2. Create and upload empty outbox list.
        JSONObject outbox_list_json = new JSONObject();
        CUtils.put(outbox_list_json, "version", 1);
        CUtils.put(outbox_list_json, "outbox_list", new JSONArray());

        URL outbox_list_url = signAndUpload
            (tmpsettings, outbox_list_json, "outbox_list.json", null, monitor);

        // 4. Create and upload root json
        JSONObject root_json = new JSONObject();
        CUtils.put(root_json, "version", 1);
        CUtils.put(root_json, "public_key", pk_url.toString());
        CUtils.put(root_json, "outbox_list", outbox_list_url.toString());

        URL root_url =
            signAndUpload
            (tmpsettings, root_json, "root.json", null, monitor);

        // 5. All done. Initialize settings and save it.
        CUtils.put(root_json, "self", root_url.toString());
        CUtils.put(root_json, "guid", UUID.randomUUID().toString());
        m_settings =
            CSettings.createEmpty(this, root_json, pkr, skr, passphrase);
        m_settings.saveKeys();
        m_settings.saveVaultSettings();
    }

    /**
     * Use this pass phrase to unlock the vault. This method can
     * only be used when the vault is in the LOCKED state.
     *
     * @return true if the vault was successfully unlocked.
     */
    public boolean unlock(String passphrase)
        throws CVaultException
    {
        check(State.LOCKED);
        return m_settings.unlock(passphrase);
    }

    /**
     * Subscribe to a thormor vault located at the provided URL,
     * and save its data locally.
     *
     * @param vaultid is the id of the vault to be linked.
     * @param monitor to follow the progress of the download.
     * @return the CLinkedVault instance added to the list of
     * linked vaults.
     */
    public CLinkedVault linkVault(URL vaultid, IProgressMonitor monitor)
        throws IOException
    {
        check(State.UNLOCKED);

        // immediately return if the vault has been linked
        // before.
        CLinkedVault ret = getLinkedVaultById(vaultid);
        if (ret != null) { return ret; }

        // 1. Download root json
        File tmp_rootf = File.createTempFile("thormor", "pgp");
        m_rprovider.download
            (new CDownloadInfo(vaultid, tmp_rootf, true, -1, null),
             monitor);

        // 2. Decode into memory
        JSONObject rootjs = verifyJSON(vaultid.toString(), tmp_rootf, null);
        if (rootjs.optString("public_key") == null) {
            throw new IOException("Missing public key in "+vaultid);
        }
        if (rootjs.optString("outbox_list") == null) {
            throw new IOException("Missing outbox_list in "+vaultid);
        }
        URL pubkey_url = new URL(rootjs.optString("public_key"));
        URL outbox_list_url = new URL(rootjs.optString("outbox_list"));

        // 2. Download public key
        File tmp_pubkey = File.createTempFile("thormor", "pgp");
        PGPPublicKeyRing pkr;
        try {
            m_rprovider.download
                (new CDownloadInfo(pubkey_url, tmp_pubkey, true, -1, null),
                 monitor);
            pkr = CPGPUtils.readPublicKeyRing(tmp_pubkey);

            // 3. Recheck root json
            rootjs = verifyJSON
                (vaultid.toString(), tmp_rootf, CPGPUtils.getMasterKey(pkr));
        }
        finally {
            tmp_rootf.delete();
            tmp_pubkey.delete();
        }

        // 3. Create an empty local outbox and upload it
        File local_outbox = CLinkedVault.createLocalOutboxFor(this, vaultid);
        URL outbox_url = m_rprovider.upload
            (new CUploadInfo
             (local_outbox, true,
              "outbox/"+m_settings.getGUID()+"/"+local_outbox.getName()),
             monitor);

        // 4. Create a new linked vault
        CLinkedVault lv = new CLinkedVault
            (this, vaultid, outbox_list_url, pubkey_url, pkr, outbox_url);
        m_settings.addLinkedVault(lv);

        // 5. Publish new outboxlist to vault
        JSONObject olist = m_settings.publishOutboxList();
        boolean ok = false;
        try {
            signAndUpload(m_settings, olist, "outbox_list.json",
                          m_settings.getOutboxListURL(), monitor);
            ok = true;
        }
        finally {
            if (!ok) { m_settings.removeLinkedVault(lv); }
        }

        // 6. Save local settings
        m_settings.saveVaultSettings();
        return lv;
    }

    /**
     * Given a thormor vault id, fetch the linked vault if available.
     *
     * @param vaultid is the id for the vault
     * @return null if vault is not linked
     */
    public CLinkedVault getLinkedVaultById(URL vaultid)
    { return m_settings.getLinkedVaultById(vaultid); }

    /**
     * Fetch a list of all vaults linked to this one.
     */
    public List<CLinkedVault> getLinkedVaults()
    { return m_settings.getLinkedVaults(); }

    /**
     * Post a json message that only the provided list of linked vaults
     * can read. The message must have a minimum set of fields, in
     * particular "id" (a unique message string), "type" (a string naming
     * the type of message) and "created" (a long milliseconds from epoch)
     *
     * @param recipents is the list of linked vaults who can read the message
     * @param message is an generic json, subject to the above restrictions.
     * @param monitor to monitor the status of the request.
     * @throws IllegalArgumentException if the json object is missing any
     * of the required fields.
     */
    public void postMessage
        (List<CLinkedVault> recipients, JSONObject message,
         IProgressMonitor monitor)
        throws IOException
    {
        // 0. Verify message has basic types.
        if (message.optString("id") == null) {
            throw new IllegalArgumentException("Missing 'id'");
        }
        if (message.optLong("created", -5) == -5) {
            throw new IllegalArgumentException("Missing 'created'");
        }
        if (message.optString("type") == null) {
            throw new IllegalArgumentException("Missing 'type'");
        }

        for (CLinkedVault recipient: recipients) {
            // 1. Splice message into recipient outbox.
            JSONObject outbox = recipient.mergeLocalOutbox(message);

            // 2. If there were updates
            if (outbox != null) {
                byte[] buf = CUtils.getBytes(outbox.toString());
                ByteArrayInputStream bin = new ByteArrayInputStream(buf);
                long inlen = buf.length;
                buf = null;

                File tmp = File.createTempFile("thormor", "pgp");
                BufferedOutputStream bout = null;
                try {
                    // 1. Encrypt new outbox to temp file
                    List<PGPPublicKey> key = new ArrayList<PGPPublicKey>();
                    key.add(recipient.getEncryptionKey());
                    bout = new BufferedOutputStream
                        (new FileOutputStream(tmp));
                    CPGPUtils.encrypt
                        (bin, inlen, bout, key,
                         m_settings.getPublicSigningKey(),
                         m_settings.getPrivateSigningKey(),
                         "outbox.json", new Date());
                    bout.close();
                    bout = null;

                    // 2. Upload encrypted outbox to target.
                    m_rprovider.upload
                        (new CUploadInfo
                         (tmp, true, null, recipient.getPublishOutboxURL()),
                         monitor);
                }
                finally {
                    if (bout != null) {
                        try { bout.close(); } 
                        catch (IOException ioe) {}
                    }
                    tmp.delete();
                }
            }
        }
    }

    /**
     * Fetch messages from all vaults, and update the local cache with
     * any new content.
     */
    public void fetchMessages(IProgressMonitor monitor)
        throws IOException
    {
        for (CLinkedVault lv: m_settings.getLinkedVaults()) {
            fetchMessagesFrom(lv, monitor);
        }
    }

    /**
     * Fetch messages from a specific linked vault, and update the local
     * cache with any new content.
     */
    public void fetchMessagesFrom(CLinkedVault lv, IProgressMonitor monitor)
        throws IOException
    {
        // 1. Poll vault for any changes in the outbox_list
        File f = updateCache(lv.getOutboxListURL(), monitor, true);

        // 2. Extract our list of outboxes, if any; and check that it
        // was correctly signed.
        JSONObject outbox_json = verifyJSON
            (lv.getOutboxListURL().toString(), f, lv.getSigningKey());

        JSONArray outbox_array = outbox_json.optJSONArray("outbox_list");
        if (outbox_array == null) { return; }

        List<URL> outboxes = new ArrayList<URL>();
        for (int i=0; i<outbox_array.length(); i++) {
            JSONObject outbox = outbox_array.optJSONObject(i);
            if (outbox == null) { continue; }
            String urlkey = outbox.optString("outbox");
            if (urlkey == null) { continue; }

            // Attempt to decode content as a URL if possible.
            URL url = null;
            try { url = new URL(decryptString(urlkey, lv.getSigningKey())); }
            catch (Throwable ign) {}
            if (url != null) { outboxes.add(url); }
        }

        // 2. For each outbox, poll for any changes.
        for (URL outbox_url: outboxes) {
            f = updateCache(outbox_url, monitor, false);
            if (f == null) {
                // no changes, skip.
                continue;
            }
            // 3. Decrypt and store outbox contents
            decryptOutbox(f, lv);
        }
    }

    /**
     * Fetch an arbitrary URL that was supposed to be created by
     * the given linked vault. The contents are fetched, decrypted,
     * and its signature verified.
     *
     * @param source URL to fetch the content
     * @param creator is the linked vault expected to have created the
     * data.
     * @param target is a file to dump the contents.
     * @param monitor to track the progress.
     */
    public void fetchContent
        (URL source, CLinkedVault creator, File target,
         IProgressMonitor monitor)
        throws IOException
    {
        boolean ok = false;
        File tmp = File.createTempFile("thormor", "pgp");
        BufferedOutputStream bout = null;
        BufferedInputStream bin = null;
        try {
            // 1. Download data to temporary file.
            CDownloadInfo di = new CDownloadInfo
                (source, target, true, -1, null);
            m_rprovider.download
                (new CDownloadInfo(source, tmp, true, -1, null),
                 monitor);

            // 2. Decrypt, verify and dump to target file.
            bin = new BufferedInputStream
                (new FileInputStream(tmp));
            bout = new BufferedOutputStream
                (new FileOutputStream(target));
            SingleStreamFactory ssf = new SingleStreamFactory(bout);
            CPGPUtils.decrypt(bin, ssf, 
                              m_settings.getPrivateEncryptionKey(),
                              creator.getSigningKey());
            bout = null;
            if (ssf.hasFailed()) {
                throw new IOException("Unable to decrypt "+source);
            }

            ok = true;
        }
        finally {
            tmp.delete();
            if (bout != null) {
                try { bout.close(); } 
                catch (IOException ign) {}
            }
            if (bin != null) {
                try { bin.close(); }
                catch (IOException ioe) {}
            }
            if (!ok) { target.delete(); }
        }
    }

    /**
     * Store an arbitrary file in the vault that can only be read by
     * the specified linked vaults. If you post a json message that
     * refers to additional files, you must first upload those files
     * with this method, and use the returned URLs in the json message.
     *
     * @param repicients are a list of linked vaults who can read this
     * file.
     * @param source is a file to be stored.
     * @param monitor tracks the progress of the operation.
     * @return a public URL to the stored content.
     */
    public URL postContent
        (List<CLinkedVault> recipients, File source, IProgressMonitor monitor)
        throws IOException
    {
        check(State.UNLOCKED);

        // 1. Encrypt to temporary file
        long inlen = source.length();
        List<PGPPublicKey> keys = getKeys(recipients);
        File tmp = File.createTempFile("thormor", "pgp");
        BufferedInputStream bin = null;
        DigestOutputStream dout = null;
        String sha_out;
        try {
            bin = new BufferedInputStream
                (new FileInputStream(source));
            dout = new DigestOutputStream
                (new BufferedOutputStream
                 (new FileOutputStream(tmp)),
                 MessageDigest.getInstance("SHA-1"));
            CPGPUtils.encrypt
                (bin, inlen, dout, keys,
                 m_settings.getPublicSigningKey(),
                 m_settings.getPrivateSigningKey(),
                 source.getName(), new Date());
            dout.close();
            sha_out = CUtils.toHex
                (dout.getMessageDigest().digest());
        }
        catch (NoSuchAlgorithmException nse) {
            throw new IOException(nse);
        }
        finally {
            if (bin != null) {
                try { bin.close(); } catch (IOException ign){};
            }
            if (dout != null) {
                try { dout.close(); } catch (IOException ign){};
            }
        }

        // 2. Upload
        try {
            return m_rprovider.upload
                (new CUploadInfo(tmp,true,"content/"+sha_out+".pgp"), monitor);
        }
        finally {
            tmp.delete();
        }
    }

    /**
     * Read files that were previously written with the writeFileSecurely
     * implementation.
     *
     * Note: The vault must be unlocked, or this method will raise an
     * IllegalStateException
     *
     * @return null if the file does not exist.
     */
    public InputStream readFileSecurely(String path)
        throws IOException
    {
        check(State.UNLOCKED);

        File inf = getSecureFile(path);
        if (!inf.exists()) { return null; }

        return decryptStream(path, inf, m_settings.getPublicSigningKey());
    }

    /**
     * Return the path to a file that is written when writeFileSecurely
     * as called with the provided path.
     *
     */
    public File getSecureFile(String path)
    {
        check(State.UNLOCKED);
        return m_lprovider.getFileFor(path+".pgp");
    }

    /**
     * Save files locally, encrypted with the user's private
     * key. Remote providers may use this for instance, to store
     * authentication tokens.
     *
     * Note 1: The file is encrypted in memory, so don't save huge files
     * here.
     * Note 2: The vault must be unlocked, or this method will raise
     * an IllegalStateException
     */
    public void writeFileSecurely(String path, InputStream inp)
        throws IOException
    {
        check(State.UNLOCKED);

        // First save bytes in memory.
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        CUtils.copy(inp, bout);
        bout.close();
        byte buf[] = bout.toByteArray();
        bout = null;
        writeFileSecurely(path, buf);
    }

    /**
     * Save files locally, encrypted with the user's private
     * key. Remote providers may use this for instance, to store
     * authentication tokens.
     *
     * Note: The vault must be unlocked, or this method will raise
     * an IllegalStateException
     */
    public void writeFileSecurely(String path, byte buf[])
        throws IOException
    {
        check(State.UNLOCKED);
        ByteArrayInputStream bin = new ByteArrayInputStream(buf);

        // Write out encrypted file with myself as the recipient.
        File outf = CUtils.makeParents(getSecureFile(path));
        List<PGPPublicKey> me = new ArrayList<PGPPublicKey>();
        me.add(m_settings.getPublicEncryptionKey());

        FileOutputStream out = null;
        try {
            out = new FileOutputStream(outf);
            CPGPUtils.encrypt
                (bin, buf.length, out, me,
                 m_settings.getPublicSigningKey(),
                 m_settings.getPrivateSigningKey(),
                 path, new Date());
            out.close();
            out = null;
        }
        finally {
            // delete output if there were problems.
            if (out != null) {
                out.close();
                outf.delete();
            }
        }
    }


    // package protected
    ILocalProvider getLocalProvider()
    { return m_lprovider; }
    IRemoteProvider getRemoteProvider()
    { return m_rprovider; }

    // private helpers
    private void check(State state)
    {
        if (getState() != state) {
            throw new IllegalStateException
                ("This method can only be used when the vault is "+state);
        }
    }

    // gather encryption keys from recipients.
    private List<PGPPublicKey> getKeys(List<CLinkedVault> targets)
    {
        List<PGPPublicKey> ret = new ArrayList<PGPPublicKey>();
        for (CLinkedVault lv: targets) {
            ret.add(lv.getEncryptionKey());
        }
        return ret;
    }

    // Create a unique file from a URL. etags and timestamps
    // are potentially recorded in a file with the same name,
    // but with a ".meta" extension.
    private File getCacheFileFor(URL url)
    {
        return m_lprovider.getCacheFileFor
            ("my/cached/"+CUtils.shasum(url.toString()));
    }

    // cache response from this url locally if possible, and return
    // location of cached file.

    private File updateCache(URL url, IProgressMonitor monitor,
                             boolean always_return_file)
        throws IOException
    {
        File file = getCacheFileFor(url);

        String etag = null;
        long timestamp = -1;

        // Check if we have any metadata for this file.
        File meta = new File(file.getParent(), file.getName()+".meta");
        if (file.canRead() && meta.canRead()) {
            JSONObject metajs = CUtils.readJSON(meta);
            etag = metajs.optString("etag");
            timestamp = metajs.optLong("timestamp", -1);
        }

        // Now download the file
        CDownloadInfo di = new CDownloadInfo(url, file, true, timestamp, etag);
        IRemoteProvider.DownloadStatus status =
            m_rprovider.download(di, monitor);

        // Update etag and timestamp if appropriate.
        if ((timestamp != di.getTimestamp()) ||
            ((etag == null) && (di.getEtag() != null)) ||
            ((etag != null) && !etag.equals(di.getEtag()))) {
            JSONObject metajs = new JSONObject();
            CUtils.put(metajs, "timestamp", di.getTimestamp());
            if (di.getEtag() != null) {
                CUtils.put(metajs, "etag", di.getEtag());
            }
            CUtils.writeJSON(meta, metajs);
        }

        if (always_return_file ||
            (status == IRemoteProvider.DownloadStatus.FULL_DOWNLOAD)) {
            return file;
        }
        return null;
    }

    // attempt to decrypt signed string
    private String decryptString(String src, PGPPublicKey from_pubkey)
        throws IOException
    {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        SingleStreamFactory ssf = new SingleStreamFactory(bout);
        ByteArrayInputStream bin =
            new ByteArrayInputStream(CUtils.getBytes(src));
        CPGPUtils.decrypt(bin, ssf, m_settings.getPrivateEncryptionKey(),
                          from_pubkey);
        bin.close(); bin = null;
        if (ssf.hasFailed()) { return null; }
        return new String(bout.toByteArray(), "utf-8");
    }

    // verify signed file as json
    private JSONObject verifyJSON(String src, File f, PGPPublicKey from_pubkey)
        throws IOException
    {
        BufferedInputStream bin = null;
        MemoryStreamFactory memfact = new MemoryStreamFactory();
        try {
            bin =
                new BufferedInputStream(new FileInputStream(f));
            CPGPUtils.verify(bin, memfact, from_pubkey);
        }
        finally {
            if (bin != null) { bin.close(); }
        }
        ByteArrayOutputStream bout = memfact.getStream();
        if (bout == null) {
            throw new IOException("failed to verify "+src);
        }
        String rootjs_s = bout.toString("utf-8");
        try { return new JSONObject(rootjs_s); }
        catch (JSONException jse) {
            throw new IOException("bad root json: '"+rootjs_s+"'");
        }
    }

    // decrypt and verify signed file as json
    private JSONObject decryptJSON(String src, File f, PGPPublicKey from_pubkey)
        throws IOException
    {
        String rootjs_s = new String(decryptBytes(src, f, from_pubkey),"utf-8");
        try { return new JSONObject(rootjs_s); }
        catch (JSONException jse) {
            throw new IOException("bad root json: '"+rootjs_s+"' from "+src);
        }
    }

    private URL signAndUpload
        (CSettings settings, JSONObject json, String name, URL target,
         IProgressMonitor monitor)
        throws IOException
    {
        // Stuff json object into a memory stream.
        byte[] jsbuf = CUtils.getBytes(json.toString());
        ByteArrayInputStream bin = new ByteArrayInputStream(jsbuf);

        // Sign contents into a temporary file.
        File tmp = File.createTempFile("thormor", "pgp");
        BufferedOutputStream bout =
            new BufferedOutputStream
            (new FileOutputStream(tmp));
        CPGPUtils.sign(bin, jsbuf.length, bout,
                       settings.getPublicSigningKey(),
                       settings.getPrivateSigningKey(),
                       name, new Date());
        bout.close();

        if (monitor != null) {
            monitor.status("Uploading "+name);
        }

        try {
            return m_rprovider.upload
                (new CUploadInfo(tmp, true, name+".pgp", target), monitor);
        }
        finally {
            tmp.delete();
        }
    }

    // Given an encrypted outbox file, decrypt and save it in our
    // message store.
    private void decryptOutbox(File f, CLinkedVault lv)
        throws IOException
    {
        // 1. Attempt to decrypt the file into memory
        JSONObject inbox = decryptJSON
            ("outbox from "+lv.getId(), f, lv.getSigningKey());

        // 2. Save it under our message store under the key for this vault.
        File inboxf = m_lprovider.getFileFor(lv.makeInboxRootPath());
        CUtils.writeJSON(inboxf, inbox);
    }

    // Decrypt as memory stream
    private InputStream decryptStream
        (String msg, File inf, PGPPublicKey from_pubkey)
        throws IOException
    { return new ByteArrayInputStream(decryptBytes(msg, inf, from_pubkey)); }

    // Decrypt into memory
    private byte[] decryptBytes(String msg, File inf, PGPPublicKey from_pubkey)
        throws IOException
    {
        BufferedInputStream inp = null;
        MemoryStreamFactory memfact = new MemoryStreamFactory();
        try {
            inp =
                new BufferedInputStream(new FileInputStream(inf));
            CPGPUtils.decrypt
                (inp, memfact,m_settings.getPrivateEncryptionKey(),from_pubkey);
        }
        finally {
            if (inp != null) { inp.close(); }
        }

        // Check if we got satisfactory data in our memory.
        ByteArrayOutputStream bout = memfact.getStream();
        if (bout == null) {
            throw new IOException("Failed to decrypt "+msg);
        }
        return bout.toByteArray();
    }


    private final IRemoteProvider m_rprovider;
    private final ILocalProvider m_lprovider;
    private CSettings m_settings;

    // helper classes
    private final static class SingleStreamFactory
        implements CPGPUtils.StreamFactory
    {
        public OutputStream createOutputStream(String filename, Date d)
            throws IOException
        {
            if (m_first) { m_first = false; }
            else { throw new IOException("Unexpected -- multiple streams"); }
            return m_stream;
        }
        public void fail()
        { m_failed = true; }
        private OutputStream getStream()
        { return m_stream; }
        private boolean hasFailed()
        { return m_failed; }
        private SingleStreamFactory(OutputStream out)
        { m_stream = out; }

        private final OutputStream m_stream;
        private boolean m_first = true;
        private boolean m_failed = false;
    }
            
    private final static class MemoryStreamFactory
        implements CPGPUtils.StreamFactory
    {
        public OutputStream createOutputStream
            (String filename, Date d)
            throws IOException
        {
            if (m_stream != null) {
                throw new IOException("Unexpected -- multiple streams created");
            }
            m_stream = new ByteArrayOutputStream();
            return m_stream;
        }

        public void fail()
        { m_stream = null; }

        private ByteArrayOutputStream getStream()
        { return m_stream; }

        private ByteArrayOutputStream m_stream;
    }
}
