/**
 * A library that provides convenient ways to manage a
 * <a href="http://kbsriram.github.com/thormor">Thormor Vault</a>.
 *
 * <p>You must implement an {@link org.thormor.provider.IRemoteProvider}
 * which stores data online, as well as an
 * {@link org.thormor.provider.ILocalProvider} which stores data locally.
 * </p>
 *
 * <p>Once you have written these two classes, you can create a new
 * {@link org.thormor.vault.CVault} instance, and use the methods in
 * it to create, fetch and post messages to a vault.</p>
 *
 * <p>All the methods that potentially access the network run
 * synchronously, and take an {@link org.thormor.provider.IProgressMonitor}
 * that may be used to monitor the progress of any network calls.
 * </p>
 *
 * <p>Please also refer to the
 * <a href="https://github.com/kbsriram/thormor/docs/libapi.md">Library
 * Documentation</a> for more information.</p>
 */

package org.thormor.vault;
