package org.thormor.vault;

import org.bouncyrattle.openpgp.operator.bc.BcPublicKeyKeyEncryptionMethodGenerator;
import org.bouncyrattle.openpgp.PGPPublicKey;
import org.bouncyrattle.openpgp.PGPException;
import org.bouncyrattle.bcpg.ContainedPacket;
import org.bouncyrattle.bcpg.PublicKeyEncSessionPacket;

class CAnonymousPublicKeyKeyEncryptionMethodGenerator
    extends BcPublicKeyKeyEncryptionMethodGenerator
{
    CAnonymousPublicKeyKeyEncryptionMethodGenerator(PGPPublicKey key)
    {
        super(key);
        m_pubkey = key;
    }

    // generate a wildcard keyid rather than embedding it into
    // the content.

    @Override
    public ContainedPacket generate(int encAlg, byte[] sessionInfo)
        throws PGPException
    {
        return new PublicKeyEncSessionPacket
            (0l, m_pubkey.getAlgorithm(),
             processSessionInfo
             (encryptSessionInfo
              (m_pubkey, sessionInfo)));
    }

    private final PGPPublicKey m_pubkey;
}