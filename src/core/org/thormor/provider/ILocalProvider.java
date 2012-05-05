package org.thormor.provider;

/**
 * Implement this interface to let thormor read and write settings,
 *  pgp keys, configuration-related data and messages onto the device.
 */

import java.io.File;

public interface ILocalProvider
{
    /**
     * Return a file where data may be locally written or read for the
     * provided path.
     * @param path can be treated as a key to the file.
     */
    public File getFileFor(String path);

    /**
     * Return a cache file. Such files can be deleted to save space if
     * necessary.
     * @param path is a key to the cache file.
     */
    public File getCacheFileFor(String path);
}
