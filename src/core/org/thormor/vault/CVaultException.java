package org.thormor.vault;

/**
 * This exception is thrown when an unexpected issue
 * with a vault is discovered.
 */
public class CVaultException
    extends RuntimeException
{
    static final long serialVersionUID = 5112316278268912069L;
    public CVaultException(String msg)
    { super(msg); }
    public CVaultException(Throwable cause)
    { super(cause); }
    public CVaultException(String msg, Throwable cause)
    { super(msg, cause); }
}
