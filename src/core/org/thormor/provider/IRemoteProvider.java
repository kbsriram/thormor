package org.thormor.provider;

/**
 * Implement this interface to let thormor access your cloud storage.
 */

import java.io.IOException;
import java.net.URL;

public interface IRemoteProvider
{
    /**
     * Upload a file to cloud storage, and update the monitor as the
     * upload proceeds.
     *
     * @param info data about file to be uploaded
     * @param mon if not null, update with upload progress.
     *
     * @return URL to the final location of content.
     */
    public URL upload(CUploadInfo info, IProgressMonitor mon)
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

    public enum DownloadStatus {
        MISSING, NO_UPDATES, FULL_DOWNLOAD
    };
}
