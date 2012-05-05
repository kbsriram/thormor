package org.thormor.vault;

import org.thormor.vault.CAnonymousPublicKeyKeyEncryptionMethodGenerator;
import org.bouncyrattle.jce.provider.BouncyCastleProvider;

import org.bouncyrattle.openpgp.PGPPublicKeyRing;
import org.bouncyrattle.openpgp.PGPPublicKeyRingCollection;
import org.bouncyrattle.openpgp.PGPSecretKeyRing;
import org.bouncyrattle.openpgp.PGPSecretKeyRingCollection;
import org.bouncyrattle.openpgp.PGPUtil;
import org.bouncyrattle.openpgp.PGPObjectFactory;
import org.bouncyrattle.openpgp.PGPException;
import org.bouncyrattle.openpgp.PGPKeyRingGenerator;
import org.bouncyrattle.openpgp.PGPKeyPair;
import org.bouncyrattle.openpgp.PGPPublicKey;
import org.bouncyrattle.openpgp.PGPSecretKey;
import org.bouncyrattle.openpgp.PGPPrivateKey;
import org.bouncyrattle.openpgp.PGPSignatureSubpacketGenerator;
import org.bouncyrattle.openpgp.PGPSignatureGenerator;
import org.bouncyrattle.openpgp.PGPSignature;
import org.bouncyrattle.openpgp.PGPOnePassSignature;
import org.bouncyrattle.openpgp.PGPEncryptedData;
import org.bouncyrattle.openpgp.PGPEncryptedDataList;
import org.bouncyrattle.openpgp.PGPEncryptedDataGenerator;
import org.bouncyrattle.openpgp.PGPPublicKeyEncryptedData;
import org.bouncyrattle.openpgp.PGPCompressedData;
import org.bouncyrattle.openpgp.PGPCompressedDataGenerator;
import org.bouncyrattle.openpgp.PGPLiteralData;
import org.bouncyrattle.openpgp.PGPLiteralDataGenerator;
import org.bouncyrattle.openpgp.PGPOnePassSignatureList;
import org.bouncyrattle.openpgp.PGPSignatureList;
import org.bouncyrattle.openpgp.operator.PGPDigestCalculator;
import org.bouncyrattle.openpgp.operator.bc.BcPGPDigestCalculatorProvider;
import org.bouncyrattle.openpgp.operator.bc.BcPGPContentSignerBuilder;
import org.bouncyrattle.openpgp.operator.bc.BcPBESecretKeyEncryptorBuilder;
import org.bouncyrattle.openpgp.operator.bc.BcPGPDataEncryptorBuilder;
import org.bouncyrattle.openpgp.operator.bc.BcPBESecretKeyDecryptorBuilder;
import org.bouncyrattle.openpgp.operator.bc.BcPublicKeyDataDecryptorFactory;
import org.bouncyrattle.openpgp.operator.bc.BcPGPContentVerifierBuilderProvider;
import org.bouncyrattle.bcpg.SymmetricKeyAlgorithmTags;
import org.bouncyrattle.bcpg.HashAlgorithmTags;
import org.bouncyrattle.bcpg.sig.Features;
import org.bouncyrattle.crypto.RuntimeCryptoException;

import java.security.Security;
import java.security.SignatureException;
import java.security.KeyPairGenerator;


import java.util.Iterator;
import java.util.Date;
import java.util.List;
import java.io.*;

import java.util.logging.Logger;
import java.util.logging.Level;

@SuppressWarnings({"unchecked","deprecation"})
class CPGPUtils
{
    // Unpack signed content, and optionally verify the
    // signature at the end.
    final static void verify
        (InputStream inp, StreamFactory sfac, PGPPublicKey from_pubkey)
        throws IOException
    {
        try { verifySignedContent(inp, from_pubkey, sfac); }
        catch (PGPException pgpe) {
            throw new IOException(pgpe);
        }
        catch (SignatureException sige) {
            throw new IOException(sige);
        }
    }

    // Decrypt contents with the provided private key, and
    // verify the signature at the end as well.
    final static void decrypt
        (InputStream inp, StreamFactory sfac,
         PGPPrivateKey privkey, PGPPublicKey from_pubkey)
        throws IOException
    {
        boolean ok = false;
        try {
            PGPObjectFactory pgpf =
                new PGPObjectFactory
                (PGPUtil.getDecoderStream(inp));

            Object x = pgpf.nextObject();
            PGPEncryptedDataList enclist;
            if (x instanceof PGPEncryptedDataList) {
                enclist = (PGPEncryptedDataList)x;
            }
            else {
                enclist = (PGPEncryptedDataList)(pgpf.nextObject());
            }
            Iterator<PGPPublicKeyEncryptedData> pkedi =
                (Iterator<PGPPublicKeyEncryptedData>)
                (enclist.getEncryptedDataObjects());
            if (pkedi == null) {
                throw new IOException("no encrypted data found!");
            }
            while (pkedi.hasNext()) {
                PGPPublicKeyEncryptedData pked = pkedi.next();
                // We may have other recipients, of whom we know nothing.
                // This results in a PGPException, ignore them.
                try {
                    decryptSignedContent(pked, privkey, from_pubkey, sfac);
                    ok = true;
                    break;
                }
                catch (PGPException pge) {
                    s_logger.info("Skip session for someone else");
                }
                catch (RuntimeCryptoException rce) {
                    s_logger.info("Skip session for someone else");
                }
            }
            if (!ok) {
                throw new IOException("No data encrypted for us!");
            }
        }
        catch (SignatureException sige) {
            throw new IOException(sige);
        }
        finally {
        }
    }

    private final static void decryptSignedContent
        (PGPPublicKeyEncryptedData pked, PGPPrivateKey privkey,
         PGPPublicKey from_pubkey, StreamFactory sfac)
        throws IOException, PGPException, SignatureException
    {
        InputStream clear = pked.getDataStream
            (new BcPublicKeyDataDecryptorFactory(privkey));

        verifySignedContent(clear, from_pubkey, sfac);
        // Also check the message integrity
        if (!pked.isIntegrityProtected()) {
            throw new IOException("Sorry -- don't read messages without integrity checks");
        }
        if (!pked.verify()) {
            throw new IOException("Integrity check failed");
        }
    }

    private final static void verifySignedContent
        (InputStream inp, PGPPublicKey from_pubkey,
         StreamFactory sfac)
        throws IOException, PGPException, SignatureException
    {
        PGPObjectFactory plainFact = new PGPObjectFactory(inp);
        Object msg = plainFact.nextObject();

        // swap in uncompressed data if necessary
        if (msg instanceof PGPCompressedData) {
            PGPCompressedData cData = (PGPCompressedData)msg;
            plainFact = new PGPObjectFactory(cData.getDataStream());
            msg = plainFact.nextObject();
        }
        if (!(msg instanceof PGPOnePassSignatureList)) {
            throw new IOException
                ("Sorry, only accept signed messages ("+
                 msg.getClass().getName()+")");
        }
        PGPOnePassSignatureList sig_head = (PGPOnePassSignatureList)msg;
        PGPOnePassSignature onepass_sig = sig_head.get(0);
        if (from_pubkey != null) {
            onepass_sig.init
                (new BcPGPContentVerifierBuilderProvider(), from_pubkey);
        }
        PGPLiteralData ldata = (PGPLiteralData)plainFact.nextObject();
        OutputStream out =
            sfac.createOutputStream
            (ldata.getFileName(), ldata.getModificationTime());
        InputStream lin = ldata.getInputStream();
        byte buf[] = new byte[8192];
        int nread;
        while ((nread = lin.read(buf)) > 0) {
            out.write(buf, 0, nread);
            if (from_pubkey != null) {
                onepass_sig.update(buf, 0, nread);
            }
        }
        out.close();

        if (from_pubkey != null) {
            PGPSignatureList sig_list = (PGPSignatureList)plainFact.nextObject();
            if (!onepass_sig.verify(sig_list.get(0))) {
                throw new IOException("This message is not signed by the sender we expected!");
            }
        }

    }

    // Sign content with the provided key
    final static void sign
        (InputStream inp, long inlen, OutputStream out,
         PGPPublicKey sign_pubkey, PGPPrivateKey sign_privkey,
         String srcname, Date modtime)
        throws IOException
    {
        try {
            PGPSignatureGenerator sGen =
                new PGPSignatureGenerator
                (new BcPGPContentSignerBuilder
                 (sign_pubkey.getAlgorithm(), HashAlgorithmTags.SHA1));
            sGen.init(PGPSignature.BINARY_DOCUMENT, sign_privkey);

            // Set up stream chain
            // compress -> output
            PGPCompressedDataGenerator comGen =
                new PGPCompressedDataGenerator(PGPCompressedData.ZIP);
            OutputStream comOut = comGen.open(out, new byte[1<<16]);

            // Data now gets stuffed into compressed out.
            // 1) signature header
            sGen.generateOnePassVersion(false).encode(comOut);

            // 2) Stuff literaldata with file contents.
            PGPLiteralDataGenerator ldGen = new PGPLiteralDataGenerator();
            OutputStream ldOut =
                ldGen.open
                (comOut, PGPLiteralData.BINARY,
                 srcname, inlen, modtime);

            byte buf[] = new byte[1<<16];
            int nread;
            while ((nread = inp.read(buf)) > 0) {
                ldOut.write(buf, 0, nread);

                // update signature at the same time.
                sGen.update(buf, 0, nread);
            }

            ldGen.close();

            // dump signature.
            sGen.generate().encode(comOut);

            // close everything internal.
            comOut.close();
            comGen.close();
        }
        catch (PGPException pge) {
            throw new IOException(pge);
        }
        catch (SignatureException sge) {
            throw new IOException(sge);
        }
    }

    // Encrypt content for a set of recipients, and sign with
    // the provided key.
    final static void encrypt
        (InputStream inp, long inlen, OutputStream out,
         List<PGPPublicKey> recipients,
         PGPPublicKey sign_pubkey, PGPPrivateKey sign_privkey,
         String srcname, Date modtime)
        throws IOException
    {
        try {
            PGPSignatureGenerator sGen =
                new PGPSignatureGenerator
                (new BcPGPContentSignerBuilder
                 (sign_pubkey.getAlgorithm(), HashAlgorithmTags.SHA1));
            sGen.init(PGPSignature.BINARY_DOCUMENT, sign_privkey);

            // Set up stream chain
            // compress -> encrypt -> output
            PGPEncryptedDataGenerator encGen =
                new PGPEncryptedDataGenerator
                (new BcPGPDataEncryptorBuilder(PGPEncryptedData.AES_256)
                 .setWithIntegrityPacket(true));
            for (PGPPublicKey recp_key: recipients) {
                encGen.addMethod
                    (new CAnonymousPublicKeyKeyEncryptionMethodGenerator
                     (recp_key));
            }
            OutputStream encOut = encGen.open(out, new byte[1<<16]);

            // enclose with a compressed stream.

            PGPCompressedDataGenerator comGen =
                new PGPCompressedDataGenerator(PGPCompressedData.ZIP);
            OutputStream comOut = comGen.open(encOut);

            // Data now gets stuffed into compressed out.
            // 1) signature header
            sGen.generateOnePassVersion(false).encode(comOut);

            // 2) Stuff literaldata with file contents.
            PGPLiteralDataGenerator ldGen = new PGPLiteralDataGenerator();
            OutputStream ldOut =
                ldGen.open
                (comOut, PGPLiteralData.BINARY,
                 srcname, inlen, modtime);

            byte buf[] = new byte[1<<16];
            int nread;
            while ((nread = inp.read(buf)) > 0) {
                ldOut.write(buf, 0, nread);

                // update signature at the same time.
                sGen.update(buf, 0, nread);
            }

            ldGen.close();

            // dump signature.
            sGen.generate().encode(comOut);

            // close everything internal.
            comOut.close();
            comGen.close();
            
            encOut.close();
            encGen.close();
        }
        catch (PGPException pge) {
            throw new IOException(pge);
        }
        catch (SignatureException sge) {
            throw new IOException(sge);
        }
    }

    final static PGPKeyRingGenerator generateKeyRingGenerator
        (String id, String extra_id, String pass)
    {
        try {
            KeyPairGenerator  kpg = KeyPairGenerator.getInstance("RSA", "BR");
            kpg.initialize(2048);

            // Create a signing key and an encryption subkey. 
            PGPKeyPair rsakp_sign =
                new PGPKeyPair
                (PGPPublicKey.RSA_SIGN, kpg.generateKeyPair(), new Date());
            PGPKeyPair rsakp_enc =
                new PGPKeyPair
                (PGPPublicKey.RSA_ENCRYPT, kpg.generateKeyPair(), new Date());

            // Add some boilerplate preferences and properties. These are
            // optional, but might make it easier to interoperate.
            PGPSignatureSubpacketGenerator hashgen =
                new PGPSignatureSubpacketGenerator();
            hashgen.setPreferredSymmetricAlgorithms
                (false, new int[] { SymmetricKeyAlgorithmTags.AES_256 });
            hashgen.setFeature(false, Features.FEATURE_MODIFICATION_DETECTION);


            // the id is added by the KeyRingGenerator. We add an additional
            // id to the signing key if present.
            if (extra_id != null) {
                PGPPrivateKey sign_privkey = rsakp_sign.getPrivateKey();
                PGPPublicKey sign_pubkey = rsakp_sign.getPublicKey();

                PGPSignatureGenerator sGen =
                    new PGPSignatureGenerator
                    (new BcPGPContentSignerBuilder
                     (sign_pubkey.getAlgorithm(),
                      HashAlgorithmTags.SHA1));

                sGen.init
                    (PGPSignature.POSITIVE_CERTIFICATION, sign_privkey);
                sGen.setHashedSubpackets(hashgen.generate());
                sign_pubkey = PGPPublicKey.addCertification
                    (sign_pubkey, extra_id,
                     sGen.generateCertification
                     (extra_id, sign_pubkey));
                rsakp_sign = new PGPKeyPair(sign_pubkey, sign_privkey);
            }

            PGPDigestCalculator sha1Calc =
                new BcPGPDigestCalculatorProvider()
                .get(HashAlgorithmTags.SHA1);

            // Create the keyring.
            PGPKeyRingGenerator keyRingGen =
                new PGPKeyRingGenerator
                (PGPSignature.POSITIVE_CERTIFICATION, rsakp_sign,
                 id, sha1Calc, hashgen.generate(), null,
                 new BcPGPContentSignerBuilder
                 (rsakp_sign.getPublicKey().getAlgorithm(),
                  HashAlgorithmTags.SHA1),
                 new BcPBESecretKeyEncryptorBuilder
                 (PGPEncryptedData.AES_256, sha1Calc)
                 .build(pass.toCharArray()));

            keyRingGen.addSubKey(rsakp_enc);
            return keyRingGen;
        }
        catch (Exception pge) {
            throw new RuntimeException(pge);
        }
    }

    final static void writePublicKeyRing(PGPPublicKeyRing pkr, File target)
        throws IOException
    {
        BufferedOutputStream bout = null;
        CUtils.makeParents(target);
        try {
            bout = new BufferedOutputStream(new FileOutputStream(target));
            pkr.encode(bout);
        }
        finally {
            if (bout != null) {
                try { bout.close(); }
                catch (IOException ign){}
            }
        }
    }

    final static void writeSecretKeyRing(PGPSecretKeyRing skr, File target)
        throws IOException
    {
        BufferedOutputStream bout = null;
        CUtils.makeParents(target);
        try {
            bout = new BufferedOutputStream(new FileOutputStream(target));
            skr.encode(bout);
        }
        finally {
            if (bout != null) {
                try { bout.close(); }
                catch (IOException ign){}
            }
        }
    }

    final static PGPPublicKeyRing readPublicKeyRing(File pub)
        throws IOException
    {
        BufferedInputStream bin = null;
        try {
            bin =
                new BufferedInputStream
                (new FileInputStream(pub));

            return readPublicKeyRing(bin);
        }
        finally {
            if (bin != null) { bin.close(); }
        }
    }

    final static PGPPublicKeyRing readPublicKeyRing(InputStream in)
        throws IOException
    {
        try {
            PGPPublicKeyRingCollection pgpPub =
                new PGPPublicKeyRingCollection
                (PGPUtil.getDecoderStream(in));

            Iterator<PGPPublicKeyRing> keyRingIter =
                (Iterator<PGPPublicKeyRing>) pgpPub.getKeyRings();

            if (keyRingIter.hasNext()) {
                return keyRingIter.next();
            }
            return null;
        }
        catch (PGPException pgpe) {
            throw new IOException(pgpe);
        }
    }

    final static PGPSecretKey getSigningKey
        (PGPSecretKeyRing pskr)
    {
        Iterator<PGPSecretKey> ski =
            (Iterator<PGPSecretKey>) pskr.getSecretKeys();
        while (ski.hasNext()) {
            PGPSecretKey ret = ski.next();
            if (ret.isSigningKey()) { return ret; }
        }
        throw new RuntimeException("no signing keys found!");
    }

    // there are two keys: A general (Sign+encrypt) and a
    // specific encrypt key.
    final static PGPPublicKey getEncryptionKey
        (PGPPublicKeyRing pskr)
    {
        Iterator<PGPPublicKey> ski =
            (Iterator<PGPPublicKey>) pskr.getPublicKeys();
        while (ski.hasNext()) {
            PGPPublicKey ret = ski.next();
            if (ret.isMasterKey()) { continue; }
            if (ret.isEncryptionKey()) {
                return ret;
            }
        }
        throw new RuntimeException("no encryption keys found!");
    }

    final static PGPPublicKey getMasterKey
        (PGPPublicKeyRing pskr)
    {
        Iterator<PGPPublicKey> ski =
            (Iterator<PGPPublicKey>) pskr.getPublicKeys();
        while (ski.hasNext()) {
            PGPPublicKey ret = ski.next();
            if (ret.isMasterKey()) {
                return ret;
            }
        }
        throw new RuntimeException("no master keys found!");
    }

    final static PGPPrivateKey extractPrivateKey
        (PGPSecretKey psk, String pass)
        throws PGPException
    {
        return psk.extractPrivateKey
            (new BcPBESecretKeyDecryptorBuilder
             (new BcPGPDigestCalculatorProvider())
             .build
             (pass.toCharArray()));
    }

    final static PGPSecretKeyRing readSecretKeyRing(File sec)
        throws IOException
    {
        BufferedInputStream bin = null;
        try {
            bin =
                new BufferedInputStream
                (new FileInputStream(sec));

            PGPSecretKeyRingCollection pgpSec =
                new PGPSecretKeyRingCollection
                (PGPUtil.getDecoderStream(bin));

            Iterator<PGPSecretKeyRing> keyRingIter =
                (Iterator<PGPSecretKeyRing>) pgpSec.getKeyRings();

            if (keyRingIter.hasNext()) {
                return keyRingIter.next();
            }
            return null;
        }
        catch (PGPException pgpe) {
            throw new IOException(pgpe);
        }
        finally {
            if (bin != null) { bin.close(); }
        }
    }

    interface StreamFactory
    {
        public OutputStream createOutputStream
            (String filename, Date modifydate)
            throws IOException;
        public void fail();
    }

    static
    {
        Security.addProvider(new BouncyCastleProvider());
    }
    private final static Logger s_logger =
        Logger.getLogger(CPGPUtils.class.getName());
}
