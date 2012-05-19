package org.thormor.provider;

import org.thormor.vault.CVault;
import java.io.IOException;
import java.net.URL;

/**
 * Implement this interface to let thormor access your cloud storage.
 */

public interface IRemoteProvider
{
    /**
     * Upload a file to cloud storage, and update the monitor as the
     * upload proceeds.
     *
     * @param info data about file to be uploaded, and a URL to upload
     * or to update.
     * @param mon if not null, update with upload progress.
     *
     * @return URL to the final location of content.
     */
    public URL upload(CUploadInfo info, IProgressMonitor mon)
        throws IOException;

    /**
     * Delete a previously uploaded URL. The URL passed in is one
     * that was previously returned via the upload() method.
     * @param url is a previously uploaded URL
     * @param mon if not not null, update with delete progress
     * @see #upload(CUploadInfo, IProgressMonitor)
     */
    public void delete(URL url, IProgressMonitor mon)
        throws IOException;

    /**
     * Download a remote file, updating the monitor as the download
     * proceeds. You can update the last_modified and etag value in
     * the info parameter if necessary, for use in subsequent
     * requests.
     *
     * @param info data about file to be downloaded
     * @param mon if not null, update with upload progress.
     */
    public DownloadStatus download(CDownloadInfo info, IProgressMonitor mon)
        throws IOException;


    /**
     * The vault calls this method every time it is unlocked. This is an
     * opportunity to save or read secured access tokens and so on.
     */
    public void postUnlockHook(CVault vault)
        throws IOException;

    public enum DownloadStatus {
        NO_UPDATES, FULL_DOWNLOAD
    };
}
