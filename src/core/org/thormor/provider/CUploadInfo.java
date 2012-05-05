package org.thormor.provider;

/**
 * This class contains information about a file that needs to be uploaded.
 * @see IRemoteProvider#upload(info, mon)
 */

import java.io.File;

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

    public CUploadInfo(File src, boolean ispublic, String suggested_name)
    {
        m_src = src;
        m_ispublic = ispublic;
        m_name = suggested_name;
    }
    private final File m_src;
    private final boolean m_ispublic;
    private final String m_name;
}
