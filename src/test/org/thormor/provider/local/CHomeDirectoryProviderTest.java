package org.thormor.provider.local;

import org.thormor.provider.local.homedir.CHomeDirectoryProvider;

import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import static org.junit.Assert.*;

import java.io.File;

public class CHomeDirectoryProviderTest
{
    @Test public void checkDefaultLocations()
    {
        CHomeDirectoryProvider dft = new CHomeDirectoryProvider();

        File check = new File
            (System.getProperty("user.home"), ".thormorvault/config/a/b");

        assertEquals(dft.getFileFor("a/b"), check);
        check = new File
            (System.getProperty("user.home"), ".thormorvault/cache/c/d");
        assertEquals(dft.getCacheFileFor("c/d"), check);
    }

    @Test public void checkLocations()
    {
        File config_root = new File("/tmp/config");
        File cache_root = new File("/tmp/cache");
        CHomeDirectoryProvider prov =
            new CHomeDirectoryProvider(config_root, cache_root);

        File check = new File(config_root, "a/b/c");
        assertEquals(prov.getFileFor("a/b/c"), check);
        check = new File(cache_root, "d/e");
        assertEquals(prov.getCacheFileFor("d/e"), check);

    }
}
