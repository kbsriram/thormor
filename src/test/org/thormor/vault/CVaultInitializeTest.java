package org.thormor.vault;

import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import static org.junit.Assert.*;

import java.io.IOException;

public class CVaultInitializeTest
{
    @Before public void start()
    {
        m_provider = new CProviderImpl(null, "init_test");
        m_vault = new CVault(m_provider, m_provider);
    }

    @After public void stop()
    {
        m_provider.cleanup();
    }

    @Test public void createVault()
        throws IOException
    {
        assertEquals(m_vault.getState(), CVault.State.UNINITIALIZED);
        m_vault.createVault("A pass phrase for testing", null);

        // The vault must now be unlocked, and we expect to see
        // a few files uploaded.
        assertEquals(m_vault.getState(), CVault.State.UNLOCKED);
        assertTrue(m_provider.uploadedFile("root.pkr").exists());
        assertTrue(m_provider.uploadedFile("root.json.pgp").exists());
        assertTrue(m_provider.uploadedFile("outbox_list.json.pgp").exists());

        // New vaults should now be able to get to the locked state.
        CVault test = new CVault(m_provider, m_provider);
        assertEquals(test.getState(), CVault.State.LOCKED);

        // and become unlocked with the same password.
        assertTrue(test.unlock("A pass phrase for testing"));
        assertEquals(test.getState(), CVault.State.UNLOCKED);

        // and the id must be identical with our newly created vault.
        assertEquals(test.getId(), m_vault.getId());
    }
    private CProviderImpl m_provider;
    private CVault m_vault;
}
