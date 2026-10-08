package nsu.anisimov.java_t1.server.utils;

import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidParameterException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import nsu.anisimov.java_t1.server.utils.errors.ClientDataGenerationError;
import nsu.anisimov.java_t1.server.utils.data.ClientData;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;


public class KeyCertGenerator {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";
    private static final Duration VALIDITY = Duration.ofDays(365);


    public static ClientData generateKeyPairAndCertificate(
            String algo,
            int keySize,
            String issuerName,
            String clientName,
            PrivateKey signKey
    ) throws ClientDataGenerationError {
        KeyPairGenerator generator = newKeyPairGenerator(algo, keySize);

        X500Name issuer = new X500Name(issuerName);
        X500Name subject = new X500NameBuilder(BCStyle.INSTANCE)
                .addRDN(BCStyle.CN, clientName)
                .build();

        KeyPair pair = generator.generateKeyPair();
        SubjectPublicKeyInfo publicKeyInfo =
                SubjectPublicKeyInfo.getInstance(pair.getPublic().getEncoded());

        Instant now = Instant.now();
        X509v3CertificateBuilder certBuilder = new X509v3CertificateBuilder(
                issuer,
                BigInteger.ONE.add(new BigInteger(127, RANDOM)),
                Date.from(now),
                Date.from(now.plus(VALIDITY)),
                subject,
                publicKeyInfo
        );

        try {
            ContentSigner signer = new JcaContentSignerBuilder(SIGNATURE_ALGORITHM).build(signKey);
            X509CertificateHolder holder = certBuilder.build(signer);
            return new ClientData(
                    pair.getPublic().getEncoded(),
                    pair.getPrivate().getEncoded(),
                    holder.getEncoded()
            );
        } catch (OperatorCreationException | IOException e) {
            throw new ClientDataGenerationError("failed to sign certificate for " + clientName, e);
        }
    }

    private static KeyPairGenerator newKeyPairGenerator(String algo, int keySize) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(algo);
            generator.initialize(keySize);
            return generator;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("wrong algorithm: " + algo, e);
        } catch (InvalidParameterException e) {
            throw new IllegalArgumentException("invalid keySize: " + keySize, e);
        }
    }
}