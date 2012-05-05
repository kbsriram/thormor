package org.thormor.provider;

/**
 * A generic way to be notified about the progress of long-running
 * operations.
 */

public interface IProgressMonitor
{
    /**
     * @param message contains a human readable progress message
     */
    public void status(String message);

    /**
     * This is used to indicate percentage-style progress.
     *
     * @param completed is a number that is a measure of progress of
     * the operation (usually, number of bytes uploaded/downloaded.)

     * @param total is a number that is a measure of the total length
     * of the operation (usually, the total number of bytes to
     * upload/download.) This can be -1 to indicate an unknown value.
     */
    public void update(long completed, long total);
}
