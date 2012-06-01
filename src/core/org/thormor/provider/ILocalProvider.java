package org.thormor.provider;

import java.io.File;
import java.io.IOException;

/**
 * Implement this interface to let thormor read and write settings,
 *  pgp keys, configuration-related data and messages onto the device.
 */

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

    /**
     * Create a path to a new temporary file. This is an exact
     * drop-in for the semantics of the createTempFile method
     * in the stock java.io.File class.
     * @param  prefix     The prefix string to be used in generating the file's
     *                    name; must be at least three characters long
     *
     * @param  suffix     The suffix string to be used in generating the file's
     *                    name; may be <code>null</code>.
     * @see java.io.File#createTempFile(String, String)
     */
    public File createTempFile(String prefix, String suffix)
        throws IOException;
}
