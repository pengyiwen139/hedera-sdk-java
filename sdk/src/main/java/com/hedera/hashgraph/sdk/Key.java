package com.hedera.hashgraph.sdk;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.sec.SECNamedCurves;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.params.ECDomainParameters;

/**
 * A common base for the signing authority or key that entities in Hedera may have.
 *
 * @see KeyList
 * @see PublicKey
 */
public abstract class Key {
    static final ASN1ObjectIdentifier ID_ED25519 = new ASN1ObjectIdentifier("1.3.101.112");
    static final ASN1ObjectIdentifier ID_ECDSA_SECP256K1 = new ASN1ObjectIdentifier("1.3.132.0.10");

    static final X9ECParameters ECDSA_SECP256K1_CURVE = SECNamedCurves.getByName("secp256k1");
    static final ECDomainParameters ECDSA_SECP256K1_DOMAIN =
        new ECDomainParameters(
            ECDSA_SECP256K1_CURVE.getCurve(),
            ECDSA_SECP256K1_CURVE.getG(),
            ECDSA_SECP256K1_CURVE.getN(),
            ECDSA_SECP256K1_CURVE.getH()
        );

    static Key fromProtobufKey(com.hedera.hashgraph.sdk.proto.Key key) {
        switch (key.getKeyCase()) {
            case ED25519:
                return new PublicKeyED25519(key.getEd25519().toByteArray());

            case ECDSA_SECP256K1:
                return new PublicKeyECDSA(key.getECDSASecp256K1().toByteArray());

            case KEYLIST:
                return KeyList.fromProtobuf(key.getKeyList(), null);

            case THRESHOLDKEY:
                return KeyList.fromProtobuf(key.getThresholdKey().getKeys(), key.getThresholdKey().getThreshold());

            case CONTRACTID:
                return ContractId.fromProtobuf(key.getContractID());

            case DELEGATABLE_CONTRACT_ID:
                return DelegateContractId.fromProtobuf(key.getDelegatableContractId());

            default:
                throw new IllegalStateException("Key#fromProtobuf: unhandled key case: " + key.getKeyCase());
        }
    }

    /**
     * Serialize this key as a protobuf object
     */
    abstract com.hedera.hashgraph.sdk.proto.Key toProtobufKey();

    public byte[] toBytes() {
        return toProtobufKey().toByteArray();
    }
}
