package org.thormor.provider.remote;

import org.thormor.provider.ILocalProvider;

import java.io.File;
import java.io.IOException;

// This is a local provider that provides access to a pre-defined set
// of data for testing.

class CLocalProvider
    implements ILocalProvider
{
    public File getFileFor(String path)
    { return new File(m_root, "config/"+path); }

    public File getCacheFileFor(String path)
    { return new File(m_root, "cache/"+path); }

    CLocalProvider(String pid)
    { m_root = "src/test/org/thormor/provider/remote/data/"+pid; }

    private final String m_root;
}
