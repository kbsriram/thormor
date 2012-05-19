package org.thormor.cli;

import org.thormor.vault.CVault;
import org.thormor.vault.CLinkedVault;

class CInfoCommand
    implements ICommand
{
    public void process(String args[])
    {
        if (CMain.getVault().getState() != CVault.State.UNLOCKED) {
            System.out.println("Please unlock this vault first.");
            return;
        }
        CMain.dumpVaultInfo();
        System.out.println();
        System.out.println("Linked Vaults");
        System.out.println("-------------");
        for (CLinkedVault lv: CMain.getVault().getLinkedVaults()) {
            CMain.dumpLinkedVault(lv);
            System.out.println();
        }
    }

    public String getName()
    { return "info"; }

    public String getUsage()
    {
        return "\tList information about this vault and linked vaults.";
    }
}
