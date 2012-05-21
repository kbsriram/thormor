package org.thormor.cli;

import org.thormor.vault.CLinkedVault;
import org.thormor.vault.CVault;
import org.thormor.provider.IProgressMonitor;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Calendar;

import java.text.SimpleDateFormat;

import java.io.IOException;
import java.io.File;

import org.json2012.JSONObject;
import org.json2012.JSONArray;
import org.json2012.JSONException;

class CShowCommand
    implements ICommand
{
    public void process(String args[])
    {
        List<CLinkedVault> recip = U.getRecipients(args, 1);
        if (recip == null) { return; }
        ArrayList<JSONObject> messages = new ArrayList<JSONObject>();
        for (CLinkedVault lv: recip) {
            try { mergeMessages(messages, lv); }
            catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }
        Collections.sort(messages, new Comparator<JSONObject>() {
                public int compare(JSONObject a, JSONObject b) {
                    long delta =
                        a.optLong("created", 0) - b.optLong("created", 0);
                    if (delta == 0) { return 0; }
                    if (delta < 0) { return -1; }
                    return 1;
                }
            });
        Date cur = new Date();
        for (JSONObject entry: messages) {
            formatMessage(entry, cur);
        }
    }

    public String getName()
    { return "show"; }

    public String getUsage()
    {
        return "\tshow [names]\n"+
            "\t\tShow saved content shared from all your linked vaults, or\n"+
            "\t\tonly from a specific list of people.";
    }

    static void formatMessage(JSONObject entry, Date cur)
    {
        formatDate(entry.optLong("created"), cur);
        System.out.print(" <");
        System.out.print(entry.optString("alias"));
        System.out.print("> ");
        String type = entry.optString("type");
        if ("thormor/text".equals(type)) {
            System.out.println(formatText(entry.optString("text")));
        }
        else if ("thormor/file".equals(type)) {
            System.out.println(formatText(entry.optString("text")));
            String src = entry.optString("local_src");
            if (src != null) {
                String name = entry.optString("name");
                System.out.print("\tFilename: ");
                if (name != null) { System.out.print(name); }
                System.out.println();
                System.out.print("\tDownloaded: ");
                System.out.println(src);
            }
        }
        else {
            System.out.println("Unknown type");
            try { System.out.println(entry.toString(2)); } 
            catch (JSONException ign) {ign.printStackTrace();}
        }
    }

    private final void mergeMessages
        (ArrayList<JSONObject> messages, CLinkedVault lv)
        throws IOException
    {
        JSONObject inbox = lv.readLocalInbox();
        if (inbox == null) { return; }

        String alias = lv.getAlias();
        if (alias == null) {
            alias = lv.getId().toString();
        }

        JSONArray entries = inbox.optJSONArray("entries");
        if (entries == null) { return; }
        for (int i=0; i<entries.length(); i++) {
            JSONObject entry = entries.optJSONObject(i);
            if (entry == null) { continue; }

            // augment object with misc info.
            U.put(entry, "alias", alias);
            if ("thormor/file".equals(entry.optString("type"))) {
                File f =
                    CFetchCommand.getDownloadLocation
                    (lv, entry.optString("src"), entry.optString("name"));
                if (f.canRead()) {
                    U.put(entry, "local_src", f.toString());
                }
            }
            messages.add(entry);
        }
    }

    private static String formatText(String s)
    {
        if (s == null) { return ""; }
        return s.replaceAll("\\n", "\n\t");
    }

    private static void formatDate(long ts, Date cur)
    {
        Date tsdate = new Date(ts);
        s_calendar.setTime(tsdate);
        int tsday = s_calendar.get(Calendar.DAY_OF_WEEK);
        s_calendar.setTime(cur);
        int curday = s_calendar.get(Calendar.DAY_OF_WEEK);
        if (tsday == curday) {
            System.out.print(s_short_format.format(tsdate));
        }
        else {
            System.out.print(s_long_format.format(tsdate));
        }
    }

    private final static SimpleDateFormat s_short_format =
        new SimpleDateFormat("HH:mm");
    private final static SimpleDateFormat s_long_format =
        new SimpleDateFormat("MMM d, yyyy HH:mm");
    private final static Calendar s_calendar =
        Calendar.getInstance();
}
