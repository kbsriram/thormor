/**
 * Interfaces you must implement to initialize a
 * {@link org.thormor.vault.CVault}
 *
 * <p>This package contains two interfaces that you must implement
 * in order to use a {@link org.thormor.vault.CVault}.</p>
 * <p>The first
 * is {@link org.thormor.provider.IRemoteProvider}, which connects
 * the vault to online storage of your choice. The primary functionality
 * is that it should be able to create files accessible through
 * publicly visible URLs. If you only create
 * {@link org.thormor.vault.CVault.DetachedMessage}s, then that is
 * all it needs to do. If you want to be able to do regular
 * {@link org.thormor.vault.CVault#postMessage}
 * calls which end up in the outbox, the provider must also be able to update
 * previously created content.</p>
 *<p>The second is
 * {@link org.thormor.provider.ILocalProvider}, which is a way to
 * let the library save configuration files and messages locally.</p>
 * <p>Please also refer to the
 * <a href="https://github.com/kbsriram/thormor/docs/libapi.md">Library
 * Documentation</a> for more information.</p>
 */

package org.thormor.provider;
