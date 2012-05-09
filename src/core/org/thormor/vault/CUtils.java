package org.thormor.vault;


// Grab bag of assorted utility methods.

import java.io.File;
import java.io.Reader;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.Writer;
import java.io.FileWriter;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

import org.json2012.JSONObject;
import org.json2012.JSONTokener;
import org.json2012.JSONArray;
import org.json2012.JSONException;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

class CUtils
{
    // Make parent directories of this file if necessary, returning
    // back the original file.
    static File makeParents(File f)
    {
        File p = f.getParentFile();
        if ((p != null) && !p.exists()) {
            p.mkdirs();
        }
        return f;
    }

    static void copy(InputStream in, OutputStream out)
        throws IOException
    {
        int nread;
        byte buf[] = new byte[8192];
        while ((nread = in.read(buf)) > 0) {
            out.write(buf, 0, nread);
        }
    }

    static String shasum(String in)
    {
        try { return shasum(in.getBytes("utf-8")); }
        catch (UnsupportedEncodingException ex1) {
            throw new RuntimeException(ex1);
        }
    }

    final static String shasum(byte buf[])
    {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            return toHex(md.digest(buf));
        }
        catch (NoSuchAlgorithmException ex) {
            throw new RuntimeException(ex);
        }
    }

    final static JSONObject readJSON(File f)
        throws IOException
    {
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(f));
            return readJSON(br);
        }
        finally {
            if (br != null) { br.close(); }
        }
    }

    final static JSONObject readJSON(Reader r)
        throws IOException
    {
        try {
            return new JSONObject
                (new JSONTokener(r));
        }
        catch (JSONException jse) {
            throw new IOException(jse);
        }
    }

    final static void writeJSON(File f, JSONObject js)
        throws IOException
    {
        BufferedWriter bw = null;
        boolean ok = false;
        try {
            makeParents(f);
            bw = new BufferedWriter(new FileWriter(f));
            writeJSON(bw, js);
            ok = true;
        }
        finally {
            if (bw != null) { bw.close(); }
            if (!ok) { f.delete(); }
        }
    }

    final static void writeJSON(Writer w, JSONObject js)
        throws IOException
    {
        try { js.write(w); }
        catch (JSONException jse) {
            throw new IOException(jse);
        }
    }

    // purely to avoid checked exceptions
    static JSONObject put(JSONObject j, String k, String v)
    {
        try { return j.put(k, v); }
        catch (JSONException jse) {
            throw new RuntimeException(jse);
        }
    }
    static JSONObject put(JSONObject j, String k, int v)
    {
        try { return j.put(k, v); }
        catch (JSONException jse) {
            throw new RuntimeException(jse);
        }
    }
    static JSONObject put(JSONObject j, String k, long v)
    {
        try { return j.put(k, v); }
        catch (JSONException jse) {
            throw new RuntimeException(jse);
        }
    }
    static JSONObject put(JSONObject j, String k, JSONArray v)
    {
        try { return j.put(k, v); }
        catch (JSONException jse) {
            throw new RuntimeException(jse);
        }
    }
    static JSONObject put(JSONObject j, String k, JSONObject v)
    {
        try { return j.put(k, v); }
        catch (JSONException jse) {
            throw new RuntimeException(jse);
        }
    }

    static String toHex(byte[] data)
    {
        StringBuilder sb = new StringBuilder();
        for (int i=0; i<data.length; i++) {
            int v = (0xff & data[i]);
            if (v < 0xf) {sb.append("0");}
            sb.append(Integer.toHexString(v));
        }
        return sb.toString();
    }

    static byte[] getBytes(String in)
    {
        try { return in.getBytes("utf-8"); }
        catch (UnsupportedEncodingException uee) {
            throw new RuntimeException(uee);
        }
    }
}
