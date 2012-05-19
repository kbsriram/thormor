package org.thormor.cli;

class CExitCommand
    implements ICommand
{
    public void process(String args[])
    { System.exit(0); }

    public String getName()
    { return "exit"; }

    public String getUsage()
    {
        return "\tExit this shell. (Control-D also works.)";
    }
}
