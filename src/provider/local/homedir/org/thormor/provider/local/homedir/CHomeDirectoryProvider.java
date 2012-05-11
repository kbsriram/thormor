package org.thormor.provider.local.homedir;

/**
 * This local provider implementation saves files under
 * a given directory (by default, both cache and config
 * files go under ".thormorvault/config" and
 * ".thormorvault/cache" in the user's home directory.
 */

import java.io.File;
import org.thormor.provider.ILocalProvider;

public class CHomeDirectoryProvider
    implements ILocalProvider
{
    /**
     * Create a provider that places config and cache files under
     * the provided directories.
     *
     * @param configroot is where all config files reside.
     * @param cacheroot is where all the cache files will reside.
     */
    public CHomeDirectoryProvider
        (File configroot, File cacheroot)
    {
        m_configroot = configroot;
        m_cacheroot = cacheroot;
    }

    /**
     * Create a provider that places config files under
     * the user's home directory (&lt;homedir&gt;/.thormorvault)
     */
    public CHomeDirectoryProvider()
    {
        m_configroot =
            new File(System.getProperty("user.home"),
                     ".thormorvault/config");
        m_cacheroot =
            new File(System.getProperty("user.home"),
                     ".thormorvault/cache");
    }

    public File getFileFor(String path)
    { return new File(m_configroot, path); };

    public File getCacheFileFor(String path)
    { return new File(m_cacheroot, path); }

    private final File m_configroot;
    private final File m_cacheroot;
}
