package org.thormor.provider.remote.googlesites;

/**
 * This remote provider stores files within a filecabinet in
 * a particular user's google site.
 *
 * The user must have an existing google site, and provide
 * oauth mediated access to this app.
 *
 * This code assumes that the key is setup as an installed
 * application.
 */

import org.thormor.provider.IRemoteProvider;
import org.thormor.provider.IProgressMonitor;
import org.thormor.provider.CDownloadInfo;
import org.thormor.provider.CUploadInfo;
import org.thormor.vault.CVault;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;

import java.util.Properties;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.net.URL;
import java.net.MalformedURLException;

import org.json2012.JSONObject;
import org.json2012.JSONTokener;
import org.json2012.JSONException;

import org.w3c.dom.Element;

public class CGoogleSitesProvider
    implements IRemoteProvider
{
    /**
     * Create an unauthenticated provider. The provider will
     * still need to obtain authorization tokens in order to
     * actually store files to storage.
     * @see #setAuthorizationCode(code)
     *
     * @param vault_path is the path of the filecabinet where
     * uploaded files will be placed.
     * @param vault_name is the page name of the filecabinet
     */
    public CGoogleSitesProvider(String vault_path, String vault_name)
    {
        m_vaultpath = vault_path;
        m_vaultname = vault_name;
        // Load up our keys, or fail right away.
        InputStream inp = null;
        try {
            inp = getClass().getResourceAsStream(PROPS_PATH);
            if (inp == null) {
                throw new IllegalStateException
                    ("Unable to find "+PROPS_PATH);
            }
            Properties p = new Properties();
            p.load(inp);
            m_clientid = getProperty(p, "client.id");
            m_clientsecret = getProperty(p, "client.secret");
        }
        catch (IOException ioe) {
            throw new IllegalStateException
                ("Unable to load "+PROPS_PATH, ioe);
        }
        finally {
            if (inp != null) {
                try { inp.close(); }
                catch (IOException ign) {}
            }
        }       
    }

    /**
     * @return a URL where you can send the user to
     * start an OAuth authorization dance.
     */
    public URL getAuthorizationURL()
    {
        try { return new URL(AUTH_PFX+m_clientid); }
        catch (MalformedURLException mfe) {
            throw new RuntimeException(mfe);
        }
    }

    /**
     * Set an authcode that is sent after the user visits
     * the authorization url and approves app access. You will
     * still need to select a domain before the provider is
     * fully initialized, using the getSiteList() and useSite()
     * methods.
     * @see #getAuthorizationURL
     * @see #getSiteList
     * @see #useSite
     * @param authorization code sent over by google.
     * @param mon can monitor the progress of the request.
     */
    public void setAuthorizationCode(String code, IProgressMonitor mon)
        throws IOException
    {
        Map<String,String> params = new HashMap<String,String>();
        params.put("code", code);
        params.put("client_id", m_clientid);
        params.put("client_secret", m_clientsecret);
        params.put("redirect_uri", REDIRECT);
        params.put("grant_type", "authorization_code");
        if (mon != null) {
            mon.status("Obtaining authorization tokens");
        }
        JSONObject resp = U.getJSON(U.post(new URL(TOKEN_URL), params, mon));
        m_access_token = resp.optString("access_token");
        m_refresh_token = resp.optString("refresh_token");
        if ((m_access_token == null) || (m_refresh_token == null)) {
            throw new IOException("missing tokens from response");
        }
        int limit = resp.optInt("expires_in", 3600);
        if (limit > 600) {
            limit = limit - 300;
        }
        m_valid_until = System.currentTimeMillis()+limit*1000;
    }

    /**
     * Return a list of domains that the user has created.
     */
    public List<CSiteInfo> getSiteList(IProgressMonitor mon)
        throws IOException
    {
        maybeRefreshTokens(mon);

        List<CSiteInfo> ret = new ArrayList<CSiteInfo>();
        Element feed = U.getRoot
            (U.authGet
             (new URL("https://sites.google.com/feeds/site/site"),
              m_access_token, mon));
        for (Element entry: U.getChildren(feed, "entry")) {
            ret.add(new CSiteInfo
                    (U.textUnder(U.getChild(entry, "sites:siteName"))));
        }
        return ret;
    }

    /**
     * Use this site to host the vault. This will also create a
     * file cabinet called "Thormor Vault" on the site.
     */
    public void useSite(CSiteInfo info, IProgressMonitor mon)
        throws IOException
    {
        maybeRefreshTokens(mon);

        // 1. List existing file cabinets.
        Element feed =
            U.getRoot
            (U.authGet
             (new URL
              (SITES_CONTENT_PFX+info.getId()+"?kind=filecabinet"),
              m_access_token, mon));

        String vaultid = null;
        for (Element entry: U.getChildren(feed, "entry")) {
            String pagename = U.textUnder(U.getChild(entry,"sites:pageName"));
            if ("vault".equals(pagename)) {
                vaultid = U.textUnder(U.getChild(entry, "id"));
            }
        }

        // 2. Create a new vault if necessary.
        if (U.isEmpty(vaultid)) {
            Element entry =
                U.getRoot
                (U.authPostString
                 (new URL(SITES_CONTENT_PFX+info.getId()),
                  m_access_token,
                  "<entry xmlns='http://www.w3.org/2005/Atom' "+
                  "xmlns:sites='http://schemas.google.com/sites/2008'>"+
                  "<category scheme='http://schemas.google.com/g/2005#kind' "+
                  "term='http://schemas.google.com/sites/2008#filecabinet' "+
                  "label='filecabinet'/>"+
                  "<title>Thormor Vault</title>"+
                  "<content type='xhtml'>"+
                  "<div xmlns='http://www.w3.org/1999/xhtml'>"+
                  "This cabinet contains files for a Thormor vault</div>"+
                  "</content><sites:pageName>vault</sites:pageName></entry>",
                  mon));
            vaultid = U.textUnder(U.getChild(entry, "id"));
        }

        if (U.isEmpty(vaultid)) {
            throw new IOException("Unable to find vaultid");
        }

        // initialize values.
        m_site_id = info.getId();
        m_vault_id = vaultid;
    }

    public void postUnlockHook(CVault vault)
        throws IOException
    {
        // We use this hook to either save credentials, or try
        // to read saved credentials.
        if (hasCredentials()) {
            JSONObject creds = new JSONObject();
            U.put(creds, "access_token", m_access_token);
            U.put(creds, "refresh_token", m_refresh_token);
            U.put(creds, "site_id", m_site_id);
            U.put(creds, "vault_id", m_vault_id);
            U.put(creds, "expires", m_valid_until);
            vault.writeFileSecurely
                (CREDS_PATH, new ByteArrayInputStream
                 (U.getBytes(creds.toString())));
        }
        else {
            // See if we can read saved credentials
            InputStream inp = vault.readFileSecurely(CREDS_PATH);
            if (inp != null) {
                try {
                    JSONObject creds =
                        new JSONObject
                        (new JSONTokener
                         (new InputStreamReader(inp)));
                    m_access_token = creds.getString("access_token");
                    m_refresh_token = creds.getString("refresh_token");
                    m_site_id = creds.getString("site_id");
                    m_vault_id = creds.getString("vault_id");
                    m_valid_until = creds.getLong("expires");
                }
                catch (JSONException jse) {
                    throw new IOException(jse);
                }
                finally {
                    if (inp != null) {
                        try { inp.close(); }
                        catch (IOException ign){}
                    }
                }
            }
        }
    }

    public URL upload(CUploadInfo info, IProgressMonitor mon)
        throws IOException
    {
        maybeRefreshTokens(mon);
        if (!hasCredentials()) {
            throw new IllegalStateException("missing vault info");
        }

        URL prior = info.getUpdateURL();
        if (prior != null) {
            URL editurl = new URL(findEditURL(prior, "edit-media", mon));
            update(editurl, info.getSource(), mon);
            return prior;
        }
        else {
            return uploadNew(info, mon);
        }
    }

    public void remove(CUploadInfo info, IProgressMonitor mon)
        throws IOException
    {
        maybeRefreshTokens(mon);
        if (!hasCredentials()) {
            throw new IllegalStateException("missing vault info");
        }

        URL prior = info.getUpdateURL();
        URL editurl = new URL(findEditURL(prior, "edit", mon));
        delete(editurl, mon);
    }

    public DownloadStatus download(CDownloadInfo info, IProgressMonitor mon)
        throws IOException
    {
        throw new IOException("tbd");
    }

    /**
     * @return true if the provider has obtained user credentials and
     * has selected a domain for the vault.
     */
    public boolean hasCredentials()
    {
        return ((m_access_token != null) &&
                (m_refresh_token != null) &&
                (m_site_id != null) &&
                (m_vault_id != null));
    }

    // private helpers
    private final static String getProperty(Properties p, String k)
    {
        String ret = p.getProperty(k);
        if (ret == null) {
            throw new IllegalStateException
                ("Missing property '"+k+"' in "+PROPS_PATH);
        }
        return ret;
    }

    private final void maybeRefreshTokens(IProgressMonitor mon)
        throws IOException
    {
        if (m_refresh_token == null) {
            throw new CredentialsException
                ("Please authenticate with Google Sites");
        }
        if ((m_access_token != null) &&
            (System.currentTimeMillis() < m_valid_until)) {
            return;
        }

        // attempt to fetch a new access token.
        Map<String,String> params = new HashMap<String,String>();
        params.put("refresh_token", m_refresh_token);
        params.put("client_id", m_clientid);
        params.put("client_secret", m_clientsecret);
        params.put("grant_type", "refresh_token");
        JSONObject resp = U.getJSON(U.post(new URL(REFRESH_URL), params, mon));
        m_access_token = resp.optString("access_token");
        int delta = resp.optInt("expires_in", 3600);
        if (m_access_token == null) {
            throw new IOException("Unable to refresh token");
        }
        if (delta > 600) { delta -= 300; }
        m_valid_until = System.currentTimeMillis()+delta*1000;
    }

    private String findEditURL(URL orig, String rel, IProgressMonitor mon)
        throws IOException
    {
        // Call to list all the elements in the vault.
        String pid = m_vault_id.substring(m_vault_id.lastIndexOf('/')+1);
        URL url = new URL(SITES_CONTENT_PFX+m_site_id+"?parent="+pid);
        final String key = orig.toString();

        do {
            Element feed = U.getRoot
                (U.authGet(url, m_access_token, mon));
            for (Element entry: U.getChildren(feed, "entry")) {
                Element content = U.getChild(entry, "content");
                if (key.equals(content.getAttribute("src"))) {
                    for (Element link: U.getChildren(entry, "link")) {
                        if (rel.equals(link.getAttribute("rel"))) {
                            return link.getAttribute("href");
                        }
                    }
                    throw new IOException("Unexpected -- no "+rel+" link");
                }
            }

            // continue searching if there is a next entry.
            url = null;
            for (Element flink: U.getChildren(feed, "link")) {
                if ("next".equals(flink.getAttribute("rel"))) {
                    url = new URL(flink.getAttribute("href"));
                    break;
                }
            }
            if (url == null) {
                throw new IOException("Failed to find url "+orig);
            }
        } while (true);
    }

    private URL uploadNew(CUploadInfo info, IProgressMonitor mon)
        throws IOException
    {
        String xml =
            "<entry xmlns='http://www.w3.org/2005/Atom'>"+
            "<category scheme='http://schemas.google.com/g/2005#kind' "+
            "term='http://schemas.google.com/sites/2008#attachment' "+
            "label='attachment'/>"+
            "<link rel='http://schemas.google.com/sites/2008#parent' "+
            "type='application/atom+xml' "+
            "href='"+m_vault_id+"'/>"+
            "<title>"+U.htmlEncode(info.getSuggestedName())+"</title>"+
            "</entry>";

        Element entry =
            U.getRoot
            (U.authPostMulti
              (new URL(SITES_CONTENT_PFX+m_site_id), m_access_token,
               xml, info.getSource(), mon));

        Element content = U.getChild(entry, "content");
        if (content == null) {
            throw new IOException("Unexpected -- no content url returned");
        }

        String urls = content.getAttribute("src");
        if (urls == null) {
            throw new IOException("Unexpected -- no upload src url found");
        }
        return new URL(urls);
    }

    private void update(URL url, File payload, IProgressMonitor mon)
        throws IOException
    { discardStream(U.authPutFile(url, m_access_token, payload, mon), mon); }

    private static void discardStream(InputStream inp, IProgressMonitor mon)
        throws IOException
    {
        byte buf[] = new byte[8192];
        try {
            while (inp.read(buf) > 0) {
            }
        }
        finally {
            try { inp.close(); }
            catch (IOException ioe) {}
        }
    }

    private void delete(URL url, IProgressMonitor mon)
        throws IOException
    { discardStream(U.authDelete(url, m_access_token, mon), mon); }

    private final String m_vaultname;
    private final String m_vaultpath;
    private final String m_clientid;
    private final String m_clientsecret;
    private String m_access_token;
    private String m_refresh_token;
    private String m_site_id;
    private String m_vault_id;
    private long m_valid_until;

    private final static String PROPS_PATH =
        "/org/thormor/provider/remote/googlesites/key.properties";
    private final static String REDIRECT = "urn:ietf:wg:oauth:2.0:oob";
    private final static String SCOPE =
        "https%3A%2F%2Fsites.google.com%2Ffeeds%2F";
    private final static String AUTH_PFX =
        "https://accounts.google.com/o/oauth2/auth?response_type=code"+
        "&redirect_uri="+REDIRECT+
        "&scope="+SCOPE+
        "&client_id=";
    private final static String TOKEN_URL =
        "https://accounts.google.com/o/oauth2/token";
    private final static String REFRESH_URL =
        "https://accounts.google.com/o/oauth2/token";
    private final static String CREDS_PATH = "gsites/credentials.json";
    private final static String SITES_CONTENT_PFX =
        "https://sites.google.com/feeds/content/site/";

    /**
     * This exception is thrown if the provider has not managed
     * to get (or fetch saved) credentials.
     */
    public static class CredentialsException
        extends IOException
    {
        static final long serialVersionUID = -3978564992459198545L;
        public CredentialsException(String s)
        { super(s); }
    }
}
