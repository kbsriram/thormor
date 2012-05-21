package org.thormor.cli;

import org.thormor.vault.CVault;
import org.thormor.vault.CLinkedVault;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Random;
import java.io.Console;
import java.io.File;
import java.io.IOException;
import java.net.URL;

import org.json2012.JSONObject;
import org.json2012.JSONException;

// put some code shared between CShareCommand and CDetachCommand here.

abstract class AMessageCommand
    implements ICommand
{
    public void process(String args[])
        throws IOException
    {
        if (args.length < 2) {
            System.out.println("Usage:\n"+getUsage());
            return;
        }

        if (args[1].equals("message")) {
            doMessage(args);
        }
        else if (args[1].equals("file")) {
            doFile(args);
        }
        else {
            System.out.println("Unknown option '"+args[1]+"'");
            System.out.println("Usage:\n"+getUsage());
        }
    }

    protected abstract String getFileUsage();
    protected abstract void postMessage
        (List<CLinkedVault> recipients, JSONObject message)
        throws IOException;

    // private
    private void doFile(String args[])
        throws IOException
    {
        // Get file to share.
        if (args.length < 3) {
            System.out.println("Missing file path");
            System.out.println("Usage:\n"+getFileUsage());
            return;
        }
        File source = new File(args[2]);
        if (!source.canRead()) {
            System.out.println("Cannot find file: '"+source+"'");
            return;
        }
        List<CLinkedVault> recip = U.getRecipients(args, 3);
        if (recip == null) {
            return;
        }

        // 1. Read an optional message to go along with this file.
        String text = readMessage("Please type in an optional message");
        if (text == null) { return; }

        // 2. Upload the file so we can get a URL to use.
        CVault vault = CMain.getVault();
        URL target = vault.postContent
            (recip, source, CMain.getProgressMonitor());

        // 3. Construct file message and post that.
        JSONObject message = genMessage("file", "thormor/file");
        String fname = source.getName();

        U.put(message, "src", target.toString());
        U.put(message, "size", source.length());
        U.put(message, "name", fname);
        U.put(message, "mime-type", mimeTypeFor(fname));
        if (text.length() > 0) {
            U.put(message, "text", text);
        }
        postMessage(recip, message);
    }

    private void doMessage(String args[])
        throws IOException
    {
        List<CLinkedVault> recip = U.getRecipients(args, 2);
        if (recip == null) {
            return;
        }
        // Get any message.
        String text = readMessage("Please type in your message");
        if (text == null) { return; }
        if ((text.length() == 0)) {
            System.out.println("Cancelled (empty message.)");
            return;
        }

        // Construct json for the message.
        JSONObject message = genMessage("txt", "thormor/text");
        U.put(message, "text", text);

        postMessage(recip, message);
    }

    private JSONObject genMessage(String idseed, String type)
    {
        JSONObject message = new JSONObject();
        U.put(message, CVault.MESSAGE_ID, genId(idseed));
        U.put(message, CVault.MESSAGE_CREATED, System.currentTimeMillis());
        U.put(message, CVault.MESSAGE_TYPE, type);
        return message;
    }

    private String genId(String t)
    { return t+(s_random.nextLong()); }

    // Prompt user for a message.
    private String readMessage(String prompt)
        throws IOException
    {
        System.out.println
            (prompt+", ending it with a \".\" on a line by itself.");
        StringBuilder sb = new StringBuilder();
        String line;
        boolean first = true;
        Console console = CMain.getConsole();
        while ((line = console.readLine()) != null) {
            if (line.equals(".")) {
                return sb.toString();
            }
            if (first) { first = false; }
            else { sb.append("\n"); }
            sb.append(line);
        }
        return null;
    }

    private final static String mimeTypeFor(String n)
    {
        int idx = n.lastIndexOf('.');
        if ((idx < 0) ||
            (idx == (n.length()-1))) {
            return "application/octet-stream";
        }
        String sfx = n.substring(idx+1).toLowerCase();
        String ret = s_suffix2mime.get(sfx);
        if (ret != null) { return ret; }
        return "application/octet-stream";
    }

    private final static Random s_random = new Random();
    private final static Map<String,String> s_suffix2mime;
    static
    {
        s_suffix2mime = new HashMap<String,String>();
        s_suffix2mime.put("jpg", "image/jpeg");
        s_suffix2mime.put("jpeg", "image/jpeg");
        s_suffix2mime.put("gif", "image/gif");
        s_suffix2mime.put("png", "image/png");
        s_suffix2mime.put("doc", "application/msword");
        s_suffix2mime.put("dot", "application/msword");
        s_suffix2mime.put("pdf", "application/pdf");
        s_suffix2mime.put("zip", "application/zip");
        s_suffix2mime.put("gz", "application/x-gzip");
        s_suffix2mime.put("htm", "text/html");
        s_suffix2mime.put("html", "text/html");
        s_suffix2mime.put("txt", "text/plain");
        s_suffix2mime.put("jpeg", "image/jpg");
        s_suffix2mime.put("jpg", "image/jpg");
        s_suffix2mime.put("jpeg", "image/jpg");
        
    }
}
