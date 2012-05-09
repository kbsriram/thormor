package org.thormor.provider;

/**
 * This class contains information about a file that needs to be uploaded.
 * @see IRemoteProvider#upload(info, mon)
 */

import java.io.File;
import java.net.URL;

public class  CUploadInfo
{
    /**
     * @return location of the file to be uploaded.
     */
    public File getSource()
    { return m_src; }

    /**
     * @return whether the URL must be publicly visible
     */
    public boolean isPublic()
    { return m_ispublic; }

    /**
     * @return a suggested pathname for the URL
     */
    public String getSuggestedName()
    { return m_name; }

    /**
     * @return a URL to update (this URL can be assumed to have
     * come from a prior call to IRemoteProvider.upload) or null
     * this is a new upload.
     */
    public URL getUpdateURL()
    { return m_update_url; }

    public CUploadInfo(File src, boolean ispublic, String suggested_name)
    { this(src, ispublic, suggested_name, null); }

    public CUploadInfo(File src, boolean ispublic, String suggested_name,
                       URL updateURL)
    {
        m_src = src;
        m_ispublic = ispublic;
        m_name = suggested_name;
        m_update_url = updateURL;
    }
    private final File m_src;
    private final boolean m_ispublic;
    private final String m_name;
    private final URL m_update_url;
}
