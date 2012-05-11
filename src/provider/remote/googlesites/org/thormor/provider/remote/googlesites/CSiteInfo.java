package org.thormor.provider.remote.googlesites;

/**
 * Encapsulates information about a particular site editable
 * via the sites api.
 */

public class CSiteInfo
{
    /**
     * This is the unique name for this site.
     */
    public String getId()
    { return m_id; }

    CSiteInfo(String id)
    {
        m_id = id;
    }
    private final String m_id;
}
