package com.github.auties00.cobalt.model.device.pairing;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "HandshakeMessage")
public final class HandshakeMessage {
    @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
    ClientHello clientHello;

    @ProtobufProperty(index = 3, type = ProtobufType.MESSAGE)
    ServerHello serverHello;

    @ProtobufProperty(index = 4, type = ProtobufType.MESSAGE)
    ClientFinish clientFinish;


    HandshakeMessage(ClientHello clientHello, ServerHello serverHello, ClientFinish clientFinish) {
        this.clientHello = clientHello;
        this.serverHello = serverHello;
        this.clientFinish = clientFinish;
    }

    public Optional<ClientHello> clientHello() {
        return Optional.ofNullable(clientHello);
    }

    public Optional<ServerHello> serverHello() {
        return Optional.ofNullable(serverHello);
    }

    public Optional<ClientFinish> clientFinish() {
        return Optional.ofNullable(clientFinish);
    }

    public void setClientHello(ClientHello clientHello) {
        this.clientHello = clientHello;
    }

    public void setServerHello(ServerHello serverHello) {
        this.serverHello = serverHello;
    }

    public void setClientFinish(ClientFinish clientFinish) {
        this.clientFinish = clientFinish;
    }

    @ProtobufMessage(name = "HandshakeMessage.ClientFinish")
    public static final class ClientFinish {
        @ProtobufProperty(index = 1, type = ProtobufType.BYTES)
        byte[] _static;

        @ProtobufProperty(index = 2, type = ProtobufType.BYTES)
        byte[] payload;

        @ProtobufProperty(index = 3, type = ProtobufType.BYTES)
        byte[] extendedCiphertext;


        ClientFinish(byte[] _static, byte[] payload, byte[] extendedCiphertext) {
            this._static = _static;
            this.payload = payload;
            this.extendedCiphertext = extendedCiphertext;
        }

        public Optional<byte[]> _static() {
            return Optional.ofNullable(_static);
        }

        public Optional<byte[]> payload() {
            return Optional.ofNullable(payload);
        }

        public Optional<byte[]> extendedCiphertext() {
            return Optional.ofNullable(extendedCiphertext);
        }

        public void setStatic(byte[] _static) {
            this._static = _static;
    }

        public void setPayload(byte[] payload) {
            this.payload = payload;
    }

        public void setExtendedCiphertext(byte[] extendedCiphertext) {
            this.extendedCiphertext = extendedCiphertext;
    }
    }

    @ProtobufMessage(name = "HandshakeMessage.ClientHello")
    public static final class ClientHello {
        @ProtobufProperty(index = 1, type = ProtobufType.BYTES)
        byte[] ephemeral;

        @ProtobufProperty(index = 2, type = ProtobufType.BYTES)
        byte[] _static;

        @ProtobufProperty(index = 3, type = ProtobufType.BYTES)
        byte[] payload;

        @ProtobufProperty(index = 4, type = ProtobufType.BOOL)
        Boolean useExtended;

        @ProtobufProperty(index = 5, type = ProtobufType.BYTES)
        byte[] extendedCiphertext;


        ClientHello(byte[] ephemeral, byte[] _static, byte[] payload, Boolean useExtended, byte[] extendedCiphertext) {
            this.ephemeral = ephemeral;
            this._static = _static;
            this.payload = payload;
            this.useExtended = useExtended;
            this.extendedCiphertext = extendedCiphertext;
        }

        public Optional<byte[]> ephemeral() {
            return Optional.ofNullable(ephemeral);
        }

        public Optional<byte[]> _static() {
            return Optional.ofNullable(_static);
        }

        public Optional<byte[]> payload() {
            return Optional.ofNullable(payload);
        }

        public boolean useExtended() {
            return useExtended != null && useExtended;
        }

        public Optional<byte[]> extendedCiphertext() {
            return Optional.ofNullable(extendedCiphertext);
        }

        public void setEphemeral(byte[] ephemeral) {
            this.ephemeral = ephemeral;
    }

        public void setStatic(byte[] _static) {
            this._static = _static;
    }

        public void setPayload(byte[] payload) {
            this.payload = payload;
    }

        public void setUseExtended(Boolean useExtended) {
            this.useExtended = useExtended;
    }

        public void setExtendedCiphertext(byte[] extendedCiphertext) {
            this.extendedCiphertext = extendedCiphertext;
    }
    }

    @ProtobufMessage(name = "HandshakeMessage.ServerHello")
    public static final class ServerHello {
        @ProtobufProperty(index = 1, type = ProtobufType.BYTES)
        byte[] ephemeral;

        @ProtobufProperty(index = 2, type = ProtobufType.BYTES)
        byte[] _static;

        @ProtobufProperty(index = 3, type = ProtobufType.BYTES)
        byte[] payload;

        @ProtobufProperty(index = 4, type = ProtobufType.BYTES)
        byte[] extendedStatic;


        ServerHello(byte[] ephemeral, byte[] _static, byte[] payload, byte[] extendedStatic) {
            this.ephemeral = ephemeral;
            this._static = _static;
            this.payload = payload;
            this.extendedStatic = extendedStatic;
        }

        public Optional<byte[]> ephemeral() {
            return Optional.ofNullable(ephemeral);
        }

        public Optional<byte[]> _static() {
            return Optional.ofNullable(_static);
        }

        public Optional<byte[]> payload() {
            return Optional.ofNullable(payload);
        }

        public Optional<byte[]> extendedStatic() {
            return Optional.ofNullable(extendedStatic);
        }

        public void setEphemeral(byte[] ephemeral) {
            this.ephemeral = ephemeral;
    }

        public void setStatic(byte[] _static) {
            this._static = _static;
    }

        public void setPayload(byte[] payload) {
            this.payload = payload;
    }

        public void setExtendedStatic(byte[] extendedStatic) {
            this.extendedStatic = extendedStatic;
    }
    }
}
