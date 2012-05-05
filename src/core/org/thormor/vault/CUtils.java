package org.thormor.vault;


// Grab bag of assorted utility methods.

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

import org.json2012.JSONObject;
import org.json2012.JSONArray;
import org.json2012.JSONException;

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
