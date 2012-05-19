package org.thormor.cli;

import org.thormor.vault.CVault;
import org.thormor.vault.CLinkedVault;

import java.net.URL;
import java.net.MalformedURLException;
import java.io.IOException;

class CLinkCommand
    implements ICommand
{
    public void process(String args[])
    {
        CVault vault = CMain.getVault();
        if (vault.getState() == CVault.State.UNINITIALIZED) {
            System.out.println("Please create a vault first.");
            return;
        }
        if (vault.getState() == CVault.State.LOCKED) {
            System.out.println("Please unlock this vault first.");
            return;
        }
        if (args.length != 3) {
            System.out.println
                ("Usage: link http://url.to/other/vault local-name-for-vault");
            return;
        }

        URL url;
        try { url = new URL(args[1]); }
        catch (MalformedURLException mfe) {
            System.out.println("Bad url: "+args[1]);
            return;
        }

        CLinkedVault other;
        try {
            other = CMain.getVault().linkVault
                (url, args[2], CMain.getProgressMonitor());
            CMain.dumpLinkedVault(other);
            System.out.println
                ("Please verify this fingerprint to make sure you");
            System.out.println
                ("have linked to the real owner.");
        }
        catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    public String getName()
    { return "link"; }

    public String getUsage()
    {
        return "\tlink <url_to_vault> <a_name_for_person>\n"+
            "\t\tLink to another person's vault, and give it a name.";
    }
}
