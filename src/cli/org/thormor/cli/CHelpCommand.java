package org.thormor.cli;

class CHelpCommand
    implements ICommand
{
    public void process(String args[])
    {
        ICommand[] list = CMain.getCommands();
        for (int i=0; i<list.length; i++) {
            ICommand command = list[i];
            System.out.println(command.getName()+":");
            System.out.println(command.getUsage());
        }
    }

    public String getName()
    { return "help"; }

    public String getUsage()
    {
        return "\tList commands available in this shell.";
    }
}
