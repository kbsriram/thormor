package org.thormor.provider;

/**
 * This class contains information about a URL that needs to be downloaded.
 * @see IRemoteProvider#download(info, mon)
 */

import java.io.File;
import java.net.URL;

public class  CDownloadInfo
{
    /**
     * @return location to download the file.
     */
    public File getTarget()
    { return m_target; }

    /**
     * @return whether the URL is publicly visible
     */
    public boolean isPublic()
    { return m_ispublic; }

    /**
     * @return location to fetch the content
     */
    public URL getSource()
    { return m_src; }

    /**
     * @return any timestamp from previously downloaded content,
     * or -1 if not present.
     */
    public long getTimestamp()
    { return m_ts; }

    /**
     * @param ts is the timestamp for downloaded content.
     */
    public CDownloadInfo setTimestamp(long ts)
    { m_ts = ts; return this; }

    /**
     * @return any etag from previously downloaded content, or null.
     */
    public String getEtag()
    { return m_etag; }

    /**
     * @param etag is the etag for downloaded content.
     */
    public CDownloadInfo setEtag(String etag)
    { m_etag = etag; return this; }

    public CDownloadInfo(URL src, File target, boolean ispublic,
                         long timestamp, String etag)
    {
        m_src = src;
        m_target = target;
        m_ispublic = ispublic;
        m_ts = timestamp;
        m_etag = etag;
    }
    private final URL m_src;
    private final File m_target;
    private final boolean m_ispublic;
    private long m_ts;
    private String m_etag;
}
