package com.github.auties00.cobalt.model.signal;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

@ProtobufMessage(name = "CertChain")
public final class CertChain {
    @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
    NoiseCertificate leaf;

    @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
    NoiseCertificate intermediate;


    CertChain(NoiseCertificate leaf, NoiseCertificate intermediate) {
        this.leaf = leaf;
        this.intermediate = intermediate;
    }

    public Optional<NoiseCertificate> leaf() {
        return Optional.ofNullable(leaf);
    }

    public Optional<NoiseCertificate> intermediate() {
        return Optional.ofNullable(intermediate);
    }

    public CertChain setLeaf(NoiseCertificate leaf) {
        this.leaf = leaf;
        return this;
    }

    public CertChain setIntermediate(NoiseCertificate intermediate) {
        this.intermediate = intermediate;
        return this;
    }

    @ProtobufMessage(name = "CertChain.NoiseCertificate")
    public static final class NoiseCertificate {
        @ProtobufProperty(index = 1, type = ProtobufType.BYTES)
        byte[] details;

        @ProtobufProperty(index = 2, type = ProtobufType.BYTES)
        byte[] signature;


        NoiseCertificate(byte[] details, byte[] signature) {
            this.details = details;
            this.signature = signature;
        }

        public Optional<byte[]> details() {
            return Optional.ofNullable(details);
        }

        public Optional<byte[]> signature() {
            return Optional.ofNullable(signature);
        }

        public NoiseCertificate setDetails(byte[] details) {
            this.details = details;
            return this;
        }

        public NoiseCertificate setSignature(byte[] signature) {
            this.signature = signature;
            return this;
        }

        @ProtobufMessage(name = "CertChain.NoiseCertificate.Details")
        public static final class Details {
            @ProtobufProperty(index = 1, type = ProtobufType.UINT32)
            Integer serial;

            @ProtobufProperty(index = 2, type = ProtobufType.UINT32)
            Integer issuerSerial;

            @ProtobufProperty(index = 3, type = ProtobufType.BYTES)
            byte[] key;

            @ProtobufProperty(index = 4, type = ProtobufType.UINT64)
            Long notBefore;

            @ProtobufProperty(index = 5, type = ProtobufType.UINT64)
            Long notAfter;


            Details(Integer serial, Integer issuerSerial, byte[] key, Long notBefore, Long notAfter) {
                this.serial = serial;
                this.issuerSerial = issuerSerial;
                this.key = key;
                this.notBefore = notBefore;
                this.notAfter = notAfter;
            }

            public OptionalInt serial() {
                return serial == null ? OptionalInt.empty() : OptionalInt.of(serial);
            }

            public OptionalInt issuerSerial() {
                return issuerSerial == null ? OptionalInt.empty() : OptionalInt.of(issuerSerial);
            }

            public Optional<byte[]> key() {
                return Optional.ofNullable(key);
            }

            public OptionalLong notBefore() {
                return notBefore == null ? OptionalLong.empty() : OptionalLong.of(notBefore);
            }

            public OptionalLong notAfter() {
                return notAfter == null ? OptionalLong.empty() : OptionalLong.of(notAfter);
            }

            public Details setSerial(Integer serial) {
                this.serial = serial;
                return this;
            }

            public Details setsuerSerial(Integer issuerSerial) {
                this.issuerSerial = issuerSerial;
                return this;
            }

            public Details setKey(byte[] key) {
                this.key = key;
                return this;
            }

            public Details setNotBefore(Long notBefore) {
                this.notBefore = notBefore;
                return this;
            }

            public Details setNotAfter(Long notAfter) {
                this.notAfter = notAfter;
                return this;
            }
        }
    }
}
