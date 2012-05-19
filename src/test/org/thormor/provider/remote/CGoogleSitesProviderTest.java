package org.thormor.provider.remote;

import org.thormor.provider.remote.googlesites.CGoogleSitesProvider;
import org.thormor.provider.remote.googlesites.CSiteInfo;
import org.thormor.vault.CVault;
import org.thormor.provider.CUploadInfo;
import org.thormor.provider.CDownloadInfo;
import org.thormor.provider.IRemoteProvider;

import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.net.URL;

public class CGoogleSitesProviderTest
{
    /*@Test*/ public void checkSaveTokens()
        throws IOException
    {
        CGoogleSitesProvider rprov =
            new CGoogleSitesProvider("vault", "Thormor Vault");

        // This local provider has saved credentials and so on.
        CLocalProvider lprov = new CLocalProvider("prov-a");

        // First-time initialization of the provider.
        if (false) {
            System.out.println(rprov.getAuthorizationURL());
            rprov.setAuthorizationCode("XXX", null);
            for (CSiteInfo si: rprov.getSiteList(null)) {
                if (si.getId().equals("thormorspace")) {
                    rprov.useSite(si, null);
                }
            }

            assertTrue(rprov.hasCredentials());

            // Init vault now, so provider can stuff back tokens.
            CVault vault = new CVault(rprov, lprov);
            assertTrue(vault.unlock(PASS));
            return;
        }

        if (true) {
            // tokens have been stored already.
            // Initialize the vault so we can test whether the rprov gets
            // back its saved tokens.
            CVault vault = new CVault(rprov, lprov);

            // The vault should be able to read, but not unlock configuration.
            assertEquals(vault.getState(), CVault.State.LOCKED);

            // Unlocking should now retrieve tokens as well.
            assertTrue(vault.unlock(PASS));
            assertTrue(rprov.hasCredentials());
        }
    }

    @Test public void checkDownload()
        throws IOException
    {
        // We should always be able to download arbitrary
        // files.
        CGoogleSitesProvider rprov =
            new CGoogleSitesProvider("vault", "Thormor Vault");
        File tmp = File.createTempFile("thormortest", null);
        URL src = new URL("http://www.google.com/favicon.ico");
        CDownloadInfo di = new CDownloadInfo(src, tmp, true, 0, null);

        assertEquals(IRemoteProvider.DownloadStatus.FULL_DOWNLOAD,
                     rprov.download(di, null));

        // the timestamp should be set after download.
        assertTrue(di.getTimestamp() > 0);

        // second time, expect no changes.
        assertEquals(IRemoteProvider.DownloadStatus.NO_UPDATES,
                     rprov.download(di, null));
    }

    /*@Test*/ public void checkUpload()
        throws IOException
    {
        CGoogleSitesProvider rprov =
            new CGoogleSitesProvider("vault", "Thormor Vault");
        CLocalProvider lprov = new CLocalProvider("prov-a");

        CVault vault = new CVault(rprov, lprov);
        assertTrue(vault.unlock(PASS));
        assertTrue(rprov.hasCredentials());

        // Try to upload the root pkr.
        File pkr = lprov.getFileFor("my/vault.pkr");
        CUploadInfo ui = new CUploadInfo(pkr, true, "root.pkr");
        URL ret = rprov.upload(ui, null);
        System.out.println(ret);

        // Modify the info (just re-upload it again.)
        ui = new CUploadInfo(pkr, true, null, ret);
        assertEquals(ret, rprov.upload(ui, null));

        // Finally, delete the uploaded file.
        rprov.delete(ret, null);
    }

    private final static String PASS = "Should this ?password be used now?";
}
