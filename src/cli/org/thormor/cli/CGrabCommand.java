package org.thormor.cli;

import org.thormor.vault.CLinkedVault;
import org.thormor.vault.CVault;
import org.thormor.provider.IProgressMonitor;

import java.util.List;
import java.util.Date;
import java.io.IOException;
import java.io.File;
import java.net.URL;
import java.net.MalformedURLException;

import org.json2012.JSONObject;
import org.json2012.JSONArray;

class CGrabCommand
    implements ICommand
{
    public void process(String args[])
        throws IOException
    {
        if (args.length != 2) {
            System.out.println(getUsage());
            return;
        }
        URL source;
        try { source = new URL(args[1]);}
        catch (MalformedURLException mfe) {
            System.out.println("Bad url: '"+args[1]+"'");
            return;
        }

        CVault vault = CMain.getVault();
        IProgressMonitor mon = CMain.getProgressMonitor();

        CVault.DetachedMessage dm = vault.fetchDetachedMessage(source, mon);

        // Also download any referenced files from our messages.
        JSONObject content = dm.getContent();
        CLinkedVault sender = dm.getSender();
        if (sender.getAlias() != null) {
            U.put(content, "alias", sender.getAlias());
        }

        File ref = CFetchCommand.downloadReference
            (content, vault, dm.getSender(), mon);
        if (ref != null) {
            U.put(content, "local_src", ref.toString());
        }

        CShowCommand.formatMessage(content, new Date());
    }

    public String getName()
    { return "grab"; }

    public String getUsage()
    {
        return "\tgrab URL\n"+
            "\t\tFetch a detached message for us from the provided URL.";
    }
}
