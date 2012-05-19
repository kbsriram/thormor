package org.thormor.cli;

/**
 * This code implements a simple command-line shell that uses the
 * thormor library, and lets the user manage a thormor vault.
 */

import org.thormor.provider.local.homedir.CHomeDirectoryProvider;
import org.thormor.provider.remote.googlesites.CGoogleSitesProvider;
import org.thormor.provider.remote.googlesites.CSiteInfo;
import org.thormor.vault.CVault;
import org.thormor.vault.CLinkedVault;
import org.thormor.provider.IProgressMonitor;

import java.util.ArrayList;
import java.util.List;
import java.io.Console;
import java.io.IOException;
import java.io.File;

public class CMain
{
    public static void main(String args[])
        throws IOException
    {
        parseArgs(args);
        s_console = System.console();

        // Initialize providers.
        s_lprovider = new CHomeDirectoryProvider
            (new File(s_local_root, "config"),
             new File(s_local_root, "cache"));
        s_rprovider = new CGoogleSitesProvider("vault", "Thormor Vault");

        // Initialize the vault.
        s_vault = new CVault(s_rprovider, s_lprovider);

        // Check whether we need to initialize the vault.
        // sequence.
        if (s_vault.getState() == CVault.State.UNINITIALIZED) {
            initializeVault();
        }
        else if (s_vault.getState() == CVault.State.LOCKED) {
            unlockVault();
        }

        runCommandLoop();
    }

    // protected
    static ICommand[] getCommands()
    { return s_commands; }
    static CVault getVault()
    { return s_vault; }
    static IProgressMonitor getProgressMonitor()
    { return s_monitor; }
    static Console getConsole()
    { return s_console; }
    final static void dumpVaultInfo()
    {
        System.out.println("Vault Information");
        System.out.println("=================");
        System.out.print("  Location: ");
        System.out.println(s_vault.getId());
        System.out.print("  Fingerprint: ");
        System.out.println(asFingerprint(s_vault.getFingerprint()));
        System.out.print("  Local files at: ");
        System.out.println(s_local_root);        
    }

    final static void dumpLinkedVault(CLinkedVault lv)
    {
        if (lv.getAlias() == null) {
            System.out.println("Linked Vault: Unknown");
        }
        else {
            System.out.println("Linked Vault: "+lv.getAlias());
        }
        System.out.print("    Location: ");
        System.out.println(lv.getId());
        System.out.print("    Fingerprint: ");
        System.out.println
            (asFingerprint
             (lv.getSigningKey().getFingerprint()));
    }

    final static ICommand findCommand(String name)
    {
        for (int i=0; i<s_commands.length; i++) {
            ICommand command = s_commands[i];
            if (command.getName().equals(name)) {
                return command;
            }
        }
        return null;
    }


    // private
    private final static void unlockVault()
    {
        int count = 0;
        do {
            char[] pass = s_console.readPassword("Passphrase: ");
            if (pass == null) { System.exit(0); }
            String pass_str = new String(pass);
            if (s_vault.unlock(pass_str)) {
                System.out.println("Vault unlocked.");
                dumpVaultInfo();
                return;
            }
            if (count++ < 4) {
                System.out.println("Please try again.");
            }
            else {
                System.out.println("Unable to unlock vault, quitting.");
                System.exit(0);
            }
        } while (true);
    }

    private final static void initializeVault()
    {
        System.out.println("The vault has not been created.");
        if (!yes("Create a new vault", true)) {
            System.out.println("No vault available.");
            System.exit(0);
        }

        // Warning.
        System.out.println
            ("NOTE: You must already have a Google Site in order to");
        System.out.println
            ("place a vault in it. If you have never done this before,");
        System.out.println
            ("please visit https://sites.google.com and create a new site");
        System.out.println
            ("for your vault.");
        if (!yes("Do you have a Google Site available", false)) {
            System.out.println("Please create a new Google Site by visiting");
            System.out.println("https://sites.google.com");
            System.exit(0);
        }

        // Initiate oauth dance.
        int count = 0;
        do {
            System.out.println
                ("Please obtain an authorization code by visiting");
            System.out.println
                (s_rprovider.getAuthorizationURL());
            String code = stringResponse("Authorization code: ", false);
            try {
                s_rprovider.setAuthorizationCode(code, s_monitor);
                break;
            }
            catch (IOException ioe) {
                ioe.printStackTrace();
                if (count++ < 4) {
                    System.out.println();
                    System.out.println("Unable to verify code, try again.");
                }
                else {
                    System.out.println("Too many failures, exit.");
                    System.exit(0);
                }
            }
        } while (true);

        // Determine site list.
        CSiteInfo site = null;
        while (true) {
            List<CSiteInfo> sites;
            try { sites = s_rprovider.getSiteList(s_monitor); }
            catch (IOException ioe) {
                sites = null;
                ioe.printStackTrace();
                System.exit(0);
            }
            if (sites.size() == 0) {
                System.out.println
                    ("You have not created any Google Site. Please visit");
                System.out.println
                    ("https://sites.google.com");
                if (yes("Check again", true)) {
                    continue;
                }
                else {
                    break;
                }
            }
            else if (sites.size() == 1) {
                CSiteInfo si = sites.get(0);
                System.out.println
                    ("You have one Google site called '"+si.getId()+"'");
                if (yes("Host your vault here", true)) {
                    site = sites.get(0);
                }
                break;
            }
            else {
                System.out.println("You have "+sites.size()+" sites:");
                int idx = 1;
                for (CSiteInfo si: sites) {
                    System.out.println("["+idx+"] "+si.getId());
                    idx++;
                }
                int selected=numeric("Host vault at site number", sites.size());
                if (selected > 0) {
                    site = sites.get(selected-1);
                }
                break;
            }
        }
        if (site == null) {
            System.out.println("No site available.");
            System.exit(0);
        }
        try { s_rprovider.useSite(site, s_monitor); }
        catch (IOException ioe) {
            ioe.printStackTrace();
            System.exit(0);
        }

        // finally, create keys.
        System.out.println
            ("Your vault will be protected with a passphrase.");
        String pass = null;
        while (true) {
            pass = stringResponse("Select passphrase: ", true);
            String check = stringResponse("Reenter passphrase: ", true);
            if (!pass.equals(check)) {
                System.out.println("passphrases do not match.");
                System.out.println();
            }
            else {
                break;
            }
        }
        try { s_vault.createVault(pass, s_monitor); }
        catch (IOException ioe) {
            ioe.printStackTrace();
            System.exit(0);
        }
        System.out.println("Created vault.");
        dumpVaultInfo();
    }

    private final static String asFingerprint(byte[] b)
    {
        StringBuilder sb = new StringBuilder();
        for (int i=0; i<b.length; i++) {
            if ((i > 0) && ((i % 4) == 0)) {
                sb.append(" ");
            }
            int v = (b[i] & 0xff);
            if (v < 0xf) { sb.append("0"); }
            sb.append(Integer.toHexString(v));
        }
        return sb.toString();
    }

    private final static int numeric(String msg, int max)
    {
        do {
            String resp = s_console.readLine(msg+" [1-"+max+"] : ");
            if (resp == null) { System.exit(0); }
            resp = resp.trim();
            if (resp.length() == 0) { continue; }
            int ret;
            try { ret = Integer.parseInt(resp); }
            catch (NumberFormatException nfe) {
                System.out.println
                    ("Please enter a number between 1 and "+max+
                     " (or 0 to cancel)");
                continue;
            }
            if ((ret < 0) || (ret > max)) {
                System.out.println
                    ("Please enter a number between 1 and "+max+
                     " (or 0 to cancel)");
                continue;
            }
            return ret;
        } while (true);
    }

    private final static String stringResponse(String msg, boolean ispass)
    {
        do {
            String resp;
            if (ispass) {
                char[] chars = s_console.readPassword(msg);
                if (chars == null) { resp = null; }
                else { resp = new String(chars); }
            }
            else {
                resp = s_console.readLine(msg);
            }
            if (resp == null) { System.exit(0); }
            resp = resp.trim();
            if (resp.length() > 0) {
                return resp;
            }
        } while (true);
    }

    private final static boolean yes(String msg, boolean dflt_yes)
    {
        do {
            String resp = s_console.readLine
                (msg+ (dflt_yes?" [Y/n]? ":" [y/N]? "));
            if (resp == null) {
                System.exit(0);
            }
            resp = resp.trim();
            if (resp.startsWith("y") ||
                resp.startsWith("Y")) {
                return true;
            }
            if (resp.startsWith("n") ||
                resp.startsWith("N")) {
                return false;
            }
            if (resp.length() == 0) {
                return dflt_yes;
            }
            System.out.println("Please answer 'y' or 'n'");
        }
        while (true);
    }

    private final static void runCommandLoop()
    {
        String line;
        while ((line = s_console.readLine("> ")) != null) {
            try { processLine(line); }
            catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }
    }

    private final static void processLine(String line)
        throws IOException
    {
        String[] args = tokenize(line);
        if (args.length == 0) { return; }

        ICommand command = findCommand(args[0]);
        if (command != null) {
            command.process(args);
        }
        else {
            System.out.println("Unknown command '"+args[0]+"'");
        }
    }

    private final static String[] tokenize(String line)
    {
        ArrayList<String> tokens = new ArrayList<String>();
        char chars[] = line.toCharArray();
        int state = 0;
        StringBuilder sb = null;
        for (int i=0; i<chars.length; i++) {
            char cur = chars[i];
            if (state == 0) { // in whitespace
                if (Character.isWhitespace(cur)) {
                    continue;
                }
                // start a new word.
                sb = new StringBuilder();
                if (cur == '"') {
                    state = 2; // in string
                }
                else {
                    state = 1; // in word
                    sb.append(cur);
                }
            }
            else if (state == 1) { // in word
                if (Character.isWhitespace(cur)) {
                    // terminate word.
                    tokens.add(sb.toString());
                    sb = null;
                    state = 0;
                }
                else { // keep appending.
                    sb.append(cur);
                }
            }
            else { // in string
                if (cur == '"') {
                    // terminate string
                    tokens.add(sb.toString());
                    sb = null;
                    state = 0;
                }
                else { // keep appending
                    sb.append(cur);
                }
            }
        }
        if (sb != null) { tokens.add(sb.toString()); }
        return tokens.toArray(new String[0]);
    }

    private final static void parseArgs(String args[])
    {
        for (int i=0; i<args.length; i++) {
            String curarg = args[i];

            if (curarg.equals("--help")) {
                usage();
                System.exit(0);
            }
            else if (curarg.equals("--progress")) {
                s_monitor = new Monitor();
            }
            else if (curarg.startsWith("--local-dir=")) {
                s_local_root = new File(getParam(curarg));
            }

            else {
                System.out.println("Unknown argument: "+curarg);
                usage();
                System.exit(1);
            }
        }
        if (s_local_root == null) {
            s_local_root =
                new File(System.getProperty("user.home"),
                         "Vault");
        }
    }
    private final static String getParam(String s)
    {
        int idx = s.indexOf('=');
        if (idx < 0) {
            System.out.println("Unknown argument: "+s);
            usage();
            System.exit(1);
        }
        return s.substring(idx+1);
    }
    private final static void usage()
    {
        System.out.println
            ("Usage: java -jar thormor-cli.jar [options]");
        System.out.println("Options");
        System.out.println("--help\tShow usage");
        System.out.println("--local-dir=<path>\tUse this directory to store configuration and downloaded messages");
        System.out.println("--progress\tShow messages about network progress");
    }

    private static class Monitor
        implements IProgressMonitor
    {
        public void status(String msg)
        {
            if (m_progress) { System.out.println(msg); }
        }
        public void update(long completed, long total)
        {
            if (m_progress) {
                System.out.print("completed: "+completed);
                if (total > 0) {
                    System.out.print("/"+total);
                }
                System.out.println();
            }
        }
        private boolean m_progress = true;
    }

    private static File s_local_root = null;
    private static CVault s_vault;
    private static CGoogleSitesProvider s_rprovider;
    private static CHomeDirectoryProvider s_lprovider;
    private static Console s_console;
    private static Monitor s_monitor = null;
    private final static ICommand s_commands[] = {
        new CExitCommand(),
        new CFetchCommand(),
        new CHelpCommand(),
        new CInfoCommand(),
        new CLinkCommand(),
        new CShareCommand(),
        new CShowCommand()
    };
}
