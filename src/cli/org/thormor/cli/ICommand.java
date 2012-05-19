package org.thormor.cli;

/**
 * This interface identifies a verb that can do something
 * interesting with the provided arguments.
 */

import java.io.IOException;

interface ICommand
{
    void process(String args[])
        throws IOException;

    String getName();

    String getUsage();
}
