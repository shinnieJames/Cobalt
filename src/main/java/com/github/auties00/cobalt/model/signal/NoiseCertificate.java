package com.github.auties00.cobalt.model.signal;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

@ProtobufMessage(name = "NoiseCertificate")
public final class NoiseCertificate {
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

    @ProtobufMessage(name = "NoiseCertificate.Details")
    public static final class Details {
        @ProtobufProperty(index = 1, type = ProtobufType.UINT32)
        Integer serial;

        @ProtobufProperty(index = 2, type = ProtobufType.STRING)
        String issuer;

        @ProtobufProperty(index = 3, type = ProtobufType.UINT64)
        Long expires;

        @ProtobufProperty(index = 4, type = ProtobufType.STRING)
        String subject;

        @ProtobufProperty(index = 5, type = ProtobufType.BYTES)
        byte[] key;


        Details(Integer serial, String issuer, Long expires, String subject, byte[] key) {
            this.serial = serial;
            this.issuer = issuer;
            this.expires = expires;
            this.subject = subject;
            this.key = key;
        }

        public OptionalInt serial() {
            return serial == null ? OptionalInt.empty() : OptionalInt.of(serial);
        }

        public Optional<String> issuer() {
            return Optional.ofNullable(issuer);
        }

        public OptionalLong expires() {
            return expires == null ? OptionalLong.empty() : OptionalLong.of(expires);
        }

        public Optional<String> subject() {
            return Optional.ofNullable(subject);
        }

        public Optional<byte[]> key() {
            return Optional.ofNullable(key);
        }

        public Details setSerial(Integer serial) {
            this.serial = serial;
            return this;
        }

        public Details setsuer(String issuer) {
            this.issuer = issuer;
            return this;
        }

        public Details setExpires(Long expires) {
            this.expires = expires;
            return this;
        }

        public Details setSubject(String subject) {
            this.subject = subject;
            return this;
        }

        public Details setKey(byte[] key) {
            this.key = key;
            return this;
        }
    }
}
