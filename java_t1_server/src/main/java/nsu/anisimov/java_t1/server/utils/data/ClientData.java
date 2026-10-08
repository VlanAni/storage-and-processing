package nsu.anisimov.java_t1.server.utils.data;

public record ClientData(byte[] publicKey, byte[] privateKey, byte[] cert) {

    @Override
    public byte[] publicKey() {
        return publicKey.clone();
    }

    @Override
    public byte[] privateKey() {
        return privateKey.clone();
    }

    @Override
    public byte[] cert() {
        return cert.clone();
    }

}
