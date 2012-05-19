package org.thormor.vault;

import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import static org.junit.Assert.*;

import org.json2012.JSONObject;
import org.json2012.JSONArray;
import org.json2012.JSONException;

import java.io.File;
import java.io.FileWriter;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.IOException;
import java.util.List;
import java.net.URL;

public class CVaultLinkTest
{
    @Test public void linkVaults()
        throws IOException, JSONException
    {
        // Create two providers, sharing the same root.
        File proot = File.createTempFile("thormor_test", null);

        CProviderImpl prov_a = new CProviderImpl(proot, "prov-a");
        CProviderImpl prov_b = new CProviderImpl(proot, "prov-b");

        CVault vault_a = new CVault(prov_a, prov_a);
        vault_a.createVault("A vault password", null);

        CVault vault_b = new CVault(prov_b, prov_b);
        vault_b.createVault("B vault password", null);


        // Subscribe vault-b to vault-a
        vault_b.linkVault(vault_a.getId(), null, null);

        // Reload vault-b
        vault_b = new CVault(prov_b, prov_b);
        assertEquals(vault_b.getState(), CVault.State.LOCKED);
        assertTrue(vault_b.unlock("B vault password"));
        assertEquals(vault_b.getState(), CVault.State.UNLOCKED);

        // We should still be able to see the linked vault.
        assertNotNull(vault_b.getLinkedVaultById(vault_a.getId()));

        // Subscribe vault-a to vault-b
        vault_a.linkVault(vault_b.getId(), null, null);

        // For kicks, vault_b links to itself as well.
        vault_b.linkVault(vault_b.getId(), "self", null);

        // vault-b uploads random content for itself and vault-a
        File tmp = File.createTempFile("thormor_test", null);
        PrintWriter pw = null;
        URL ref = null;
        try {
            pw = new PrintWriter
                (new BufferedWriter
                 (new FileWriter(tmp)));
            pw.println("hello, message");
            pw.close();
            pw = null;
            ref = vault_b.postContent
                (vault_b.getLinkedVaults(), tmp, null);
        }
        finally {
            if (pw != null) { pw.close(); }
            tmp.delete();
        }

        // vault-a fetches this content from vault-b
        tmp = File.createTempFile("thormor_test", null);
        try {
            vault_a.fetchContent(ref, vault_a.getLinkedVaults().get(0),
                                 tmp, null);
            BufferedReader br = new BufferedReader(new FileReader(tmp));
            assertEquals(br.readLine(), "hello, message");
            br.close();
        }
        finally {
            tmp.delete();
        }

        // vault-b posts a message for vault-a
        JSONObject message = new JSONObject();
        message
            .put("id", "a")
            .put("type", "test/type")
            .put("created", 1)
            .put("hello", "world");
        vault_b.postMessage(vault_b.getLinkedVaults(), message, null);

        // vault-a updates message store from vault-b
        vault_a.fetchMessages(null);

        // test that messages are persisted only after
        // delivery by making the provider fail on uploads.
        prov_b.failUploads(true);
        message.put("id", "b");
        try {
            vault_b.postMessage(vault_b.getLinkedVaults(), message, null);
            fail("How did this message get through?");
        }
        catch (IOException ioe) {
        }
        // verify that our outbox only has the one message.
        CLinkedVault linkedvault_a = vault_b.getLinkedVaults().get(0);
        JSONObject outbox_a = linkedvault_a.readLocalOutbox();
        JSONArray entries = outbox_a.getJSONArray("entries");
        assertEquals(1, entries.length());
        JSONObject entry = entries.getJSONObject(0);
        assertEquals("a", entry.getString("id"));
        assertEquals("world", entry.getString("hello"));
    }
}
