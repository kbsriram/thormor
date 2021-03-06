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

class CShareCommand
    extends AMessageCommand
{
    public String getName()
    { return "share"; }

    public String getUsage()
    {
        return "\tshare message [recipients]\n"+
            "\t\tShare a message with all your linked recipients, or share\n"+
            "\t\tit only with a specified list of recipients.\n"+
            getFileUsage();
    }
    protected String getFileUsage()
    {
        return
            "\tshare file <path_to_file> [recipients]\n"+
            "\t\tShare a file with all your linked recipients, or share\n"+
            "\t\tit only with a specific list of recipients.";
    }
    protected void postMessage
        (List<CLinkedVault>recip, JSONObject message)
        throws IOException
    {
        CMain.getVault().postMessage
            (recip, message, CMain.getProgressMonitor());
    }
}
