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

class CDetachCommand
    extends AMessageCommand
{
    public String getName()
    { return "detach"; }

    public String getUsage()
    {
        return "\tdetach message [recipients]\n"+
            "\t\tCreate a detached message shared with all your linked recipients,\n"+
            "\t\tor only with a specified list of recipients.\n"+
            getFileUsage();
    }
    protected String getFileUsage()
    {
        return
            "\tdetach file <path_to_file> [recipients]\n"+
            "\t\tCreate a detached file shared with all your linked recipients,\n"
            +"\t\tor only with a specific list of recipients.";
    }

    protected void postMessage
        (List<CLinkedVault> recip, JSONObject message)
        throws IOException
    {
        URL result = CMain.getVault().postDetachedMessage
            (recip, message, CMain.getProgressMonitor());
        System.out.println("Detached message location:");
        System.out.println("\t"+result);
    }
}
