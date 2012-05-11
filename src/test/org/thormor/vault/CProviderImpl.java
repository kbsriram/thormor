package org.thormor.vault;

import org.thormor.provider.IRemoteProvider;
import org.thormor.provider.ILocalProvider;
import org.thormor.provider.IProgressMonitor;
import org.thormor.provider.CUploadInfo;
import org.thormor.provider.CDownloadInfo;

import static org.junit.Assert.assertTrue;

import java.util.Set;
import java.util.HashSet;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.net.MalformedURLException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

// Small provider implementation to use in various tests
public class CProviderImpl
    implements IRemoteProvider, ILocalProvider
{
    public CProviderImpl(File root, String lid)
    {
        if (root == null) {
            try { m_root = File.createTempFile("thormor_test", null); }
            catch (IOException ioe) { throw new RuntimeException(ioe); }
        }
        else {
            m_root = root;
        }
        m_lid = lid;
        if (!m_root.isDirectory()) {
            m_root.delete();
            m_root.mkdirs();
        }
        (new File(m_root, lid+"/cache")).mkdirs();
        (new File(m_root, lid+"/config")).mkdirs();
        (new File(m_root, lid+"/upload")).mkdirs();
    }

    public void cleanup()
    { cleanup(m_root); }

    private static void cleanup(File root)
    {
        if (root.isDirectory()) {
            File[] f = root.listFiles();
            if (f != null) {
                for (int i=0; i<f.length; i++) {
                    cleanup(f[i]);
                }
            }
        }
        root.delete();
    }

    public File getFileFor(String path)
    {
        // local files are prefixed with the lid.
        File ret = new File(m_root, m_lid+"/config/"+path);
        System.out.println("config-file: "+path+" -> "+ret);
        return ret;
    }

    public File getCacheFileFor(String path)
    {
        // local files are prefixed with the lid.
        File ret = new File(m_root, m_lid+"/cache/"+path);
        System.out.println("cache-file: "+path+" -> "+ret);
        return ret;
    }

    public void postUnlockHook(CVault vault)
    {
        System.out.println("post-upload-hook called");
    }

    public URL upload(CUploadInfo info, IProgressMonitor mon)
        throws IOException
    {
        // copy src to a temp location
        File src = info.getSource();
        assertTrue(src.exists());

        String path;
        if (info.getUpdateURL() != null) {
            path = info.getUpdateURL().getPath();
        }
        else {
            // uploads locations are prefixed with local id
            path = m_lid+"/upload/"+info.getSuggestedName();
        }

        File target = new File(m_root, path);

        System.out.println("upload: "+info.getSuggestedName()+
                           " ("+src+") -> "+target);
        copy(src, target);
        return new URL("http://www.example.com/"+path);
    }

    File uploadedFile(String path)
    {
        return new File(m_root, m_lid+"/upload/"+path);
    }

    public DownloadStatus download
        (CDownloadInfo info, IProgressMonitor mon)
        throws IOException
    {
        File src = new File(m_root, info.getSource().getPath());
        File target = info.getTarget();

        System.out.println("download: "+src+" -> "+target);
        copy(src, target);
        return DownloadStatus.FULL_DOWNLOAD;
    }

    private final File m_root;
    private final String m_lid;

    private final static void copy(File src, File target)
        throws IOException
    {
        CUtils.makeParents(target);
        FileInputStream in = null;
        FileOutputStream out = null;
        try {
            int nread;
            byte buf[] = new byte[8192];
            in = new FileInputStream(src);
            out = new FileOutputStream(target);
            while ((nread = in.read(buf)) > 0) {
                out.write(buf, 0, nread);
            }
        }
        finally {
            if (in != null) { in.close(); }
            if (out != null) { out.close(); }
        }
    }

    private final static String md5(String s)
    {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            digest.update(s.getBytes());
            byte md[] = digest.digest();

            // Create Hex String
            StringBuilder sb = new StringBuilder();
            for (int i=0; i<md.length; i++) {
                int v = (0xff & md[i]);
                if (v < 0x10) {sb.append("0");}
                sb.append(Integer.toHexString(v));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
