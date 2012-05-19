package org.thormor.cli;

import org.thormor.vault.CLinkedVault;
import org.thormor.vault.CVault;
import org.thormor.provider.IProgressMonitor;

import java.util.List;
import java.io.IOException;
import java.io.File;
import java.net.URL;

import org.json2012.JSONObject;
import org.json2012.JSONArray;

class CFetchCommand
    implements ICommand
{
    public void process(String args[])
        throws IOException
    {
        List<CLinkedVault> recip = U.getRecipients(args, 1);
        if (recip == null) { return; }
        CVault vault = CMain.getVault();
        IProgressMonitor mon = CMain.getProgressMonitor();
        for (CLinkedVault lv: recip) {
            try { vault.fetchMessagesFrom(lv, mon); }
            catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }

        // Also download any referenced files from our messages.
        for (CLinkedVault lv: recip) {
            try { downloadReferences(vault, lv, mon);  }
            catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }

        // forward to the show command.
        args[0] = "show";
        CMain.findCommand(args[0]).process(args);
    }

    public String getName()
    { return "fetch"; }

    public String getUsage()
    {
        return "\tfetch [recipients]\n"+
            "\t\tFetch content shared from all your linked vaults, or\n"+
            "\t\tonly from a specific list of people.";
    }

    private final void downloadReferences
        (CVault vault, CLinkedVault lv, IProgressMonitor mon)
        throws IOException
    {
        JSONObject inbox = lv.readLocalInbox();
        if (inbox == null) { return; }
        JSONArray entries = inbox.optJSONArray("entries");
        if (entries == null) { return; }
        for (int i=0; i<entries.length(); i++) {
            JSONObject entry = entries.optJSONObject(i);
            if (entry == null) { continue; }
            String type = entry.optString("type");
            if (type == null) { continue; }
            if ("thormor/file".equals(type)) {
                String src = entry.optString("src");
                String name = entry.optString("name");
                if (src != null) {
                    try { downloadFile(src, name, vault, lv, mon); }
                    catch (IOException ioe) {
                        ioe.printStackTrace();
                    }
                }
            }
        }
    }

    final static File getDownloadLocation
        (CLinkedVault lv, String url_src, String name)
    {
        // Make up a filename for this content. We use the shasum of
        // the source to figure out if we've seen this content before.
        File target = new File(lv.getInboxRoot(), U.shasum(url_src));
        if (name != null) {
            target = new File(target, name);
        }
        return target;
    }
    private final void downloadFile
        (String src, String name, CVault vault, CLinkedVault lv,
         IProgressMonitor mon)
        throws IOException
    {
        File target = getDownloadLocation(lv, src, name);
        if (target.canRead()) { return; }
        vault.fetchContent(new URL(src), lv, target, mon);
    }
}
