package org.thormor.cli;

// grab bag of utilities.

import org.thormor.vault.CVault;
import org.thormor.vault.CLinkedVault;

import java.util.List;
import java.util.ArrayList;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import java.io.UnsupportedEncodingException;

import org.json2012.JSONObject;
import org.json2012.JSONArray;
import org.json2012.JSONException;

class U
{
    // return a list of recipients (or null) from a list
    // of vault aliases.
    static List<CLinkedVault> getRecipients(String args[], int start)
    {
        CVault vault = CMain.getVault();
        List<CLinkedVault> ret;
        if (args.length == start) {
            ret = vault.getLinkedVaults();
        }
        else {
            ret = new ArrayList<CLinkedVault>();
            for (int i=start; i<args.length; i++) {
                CLinkedVault lv = vault.getLinkedVaultByAlias(args[i]);
                if (lv == null) {
                    System.out.println("No such recipient: '"+args[i]+"'");
                    return null;
                }
                ret.add(lv);
            }
        }
        if (ret.size() == 0) {
            System.out.println("You don't have any linked recipients.");
            System.out.println("Please link to a vault first.");
            return null;
        }
        return ret;
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
    static void put(JSONObject js, String k, String v)
    {
        try { js.put(k, v); }
        catch (JSONException jse) { throw new RuntimeException(jse); }
    }
    static void put(JSONObject js, String k, long v)
    {
        try { js.put(k, v); }
        catch (JSONException jse) { throw new RuntimeException(jse); }
    }

}
