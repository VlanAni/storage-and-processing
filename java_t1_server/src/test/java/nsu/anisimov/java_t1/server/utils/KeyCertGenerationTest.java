package nsu.anisimov.java_t1.server.utils;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.concurrent.TimeUnit;

import nsu.anisimov.java_t1.server.utils.errors.ClientDataGenerationError;
import nsu.anisimov.java_t1.server.utils.data.ClientData;
import org.bouncycastle.asn1.ASN1String;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 60, unit = TimeUnit.SECONDS)
class KeyCertGenerationTest {
    private static final String ISSUER = "CN=Test Issuer";
    private static final int KEY_SIZE = 2048;

    private static KeyPair issuerPair;

    @BeforeAll
    static void setUp() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(KEY_SIZE);
        issuerPair = kpg.generateKeyPair();
    }

    private static X509Certificate parse(byte[] der) throws Exception {
        return (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(der));
    }

    private static String commonName(X509Certificate cert) throws Exception {
        X500Name subject = new JcaX509CertificateHolder(cert).getSubject();
        RDN[] rdns = subject.getRDNs();
        assertEquals(1, rdns.length, "Subject must contain exactly one RDN");
        assertEquals(1, rdns[0].getTypesAndValues().length);
        assertEquals(BCStyle.CN, rdns[0].getFirst().getType());
        return ((ASN1String) rdns[0].getFirst().getValue()).getString();
    }

    @Test
    void certificateIsSignedByIssuerAndHasExpectedNames() throws Exception {
        ClientData data = KeyCertGenerator.generateKeyPairAndCertificate(
                "RSA", KEY_SIZE, ISSUER, "alice", issuerPair.getPrivate());

        X509Certificate cert = parse(data.cert());
        cert.verify(issuerPair.getPublic());

        assertEquals("alice", commonName(cert));
        assertEquals(new X500Name(ISSUER), new JcaX509CertificateHolder(cert).getIssuer());
    }

    @Test
    void certificateIsSignedOnlyByIssuerKey() throws Exception {
        ClientData data = KeyCertGenerator.generateKeyPairAndCertificate(
                "RSA", KEY_SIZE, ISSUER, "alice", issuerPair.getPrivate());
        X509Certificate cert = parse(data.cert());

        PublicKey clientPublic = cert.getPublicKey();
        assertThrows(Exception.class, () -> cert.verify(clientPublic));
    }

    @Test
    void certificateContainsMatchingPublicKeyAndPrivateKeyWorks() throws Exception {
        ClientData data = KeyCertGenerator.generateKeyPairAndCertificate(
                "RSA", KEY_SIZE, ISSUER, "bob", issuerPair.getPrivate());
        PublicKey certKey = parse(data.cert()).getPublicKey();

        assertArrayEquals(data.publicKey(), certKey.getEncoded());

        PrivateKey privateKey = KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(data.privateKey()));
        byte[] message = "hello".getBytes();
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(privateKey);
        signer.update(message);
        byte[] signature = signer.sign();

        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(certKey);
        verifier.update(message);
        assertTrue(verifier.verify(signature), "private key must match public key in certificate");
    }

    @Test
    void nameWithoutRdnSyntaxIsAccepted() throws Exception {
        ClientData data = KeyCertGenerator.generateKeyPairAndCertificate(
                "RSA", KEY_SIZE, ISSUER, "plain name 42", issuerPair.getPrivate());

        assertEquals("plain name 42", commonName(parse(data.cert())));
    }


    @Test
    void repeatedCallsProduceDifferentKeysAndSerials() throws Exception {
        X509Certificate c1 = parse(KeyCertGenerator.generateKeyPairAndCertificate(
                "RSA", KEY_SIZE, ISSUER, "dave", issuerPair.getPrivate()).cert());
        X509Certificate c2 = parse(KeyCertGenerator.generateKeyPairAndCertificate(
                "RSA", KEY_SIZE, ISSUER, "dave", issuerPair.getPrivate()).cert());

        assertNotEquals(c1.getSerialNumber(), c2.getSerialNumber());
        assertNotEquals(c1.getPublicKey(), c2.getPublicKey());
        assertTrue(c1.getSerialNumber().signum() > 0);
    }

    @Test
    void invalidIssuerNameIsRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                KeyCertGenerator.generateKeyPairAndCertificate("RSA", KEY_SIZE, "not a dn", "x", issuerPair.getPrivate()));
    }

    @Test
    void signingFailureIsReportedAsException() throws Exception {
        KeyPairGenerator dsa = KeyPairGenerator.getInstance("DSA");
        dsa.initialize(2048);
        PrivateKey wrongKey = dsa.generateKeyPair().getPrivate();

        assertThrows(ClientDataGenerationError.class, () ->
                KeyCertGenerator.generateKeyPairAndCertificate("RSA", KEY_SIZE, ISSUER, "x", wrongKey));
    }

}