package com.github.auties00.cobalt.model.device.pairing;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.OptionalInt;

import static java.lang.Integer.parseInt;

@ProtobufMessage(name = "DeviceProps.AppVersion")
public final class ClientAppVersion {
    @ProtobufProperty(index = 1, type = ProtobufType.UINT32)
    Integer primary;

    @ProtobufProperty(index = 2, type = ProtobufType.UINT32)
    Integer secondary;

    @ProtobufProperty(index = 3, type = ProtobufType.UINT32)
    Integer tertiary;

    @ProtobufProperty(index = 4, type = ProtobufType.UINT32)
    Integer quaternary;

    @ProtobufProperty(index = 5, type = ProtobufType.UINT32)
    Integer quinary;

    ClientAppVersion(Integer primary, Integer secondary, Integer tertiary, Integer quaternary, Integer quinary) {
        this.primary = primary;
        this.secondary = secondary;
        this.tertiary = tertiary;
        this.quaternary = quaternary;
        this.quinary = quinary;
    }

    public ClientAppVersion(int primary) {
        this(primary, null, null, null, null);
    }

    public ClientAppVersion(int primary, int secondary, int tertiary) {
        this(primary, secondary, tertiary, null, null);
    }

    public static ClientAppVersion of(String version) {
        var tokens = version.split("\\.", 5);
        if (tokens.length > 5) {
            throw new IllegalArgumentException("Invalid value of tokens for version %s: %s".formatted(version, tokens));
        }

        var primary = tokens.length > 0 ? parseInt(tokens[0]) : null;
        var secondary = tokens.length > 1 ? parseInt(tokens[1]) : null;
        var tertiary = tokens.length > 2 ? parseInt(tokens[2]) : null;
        var quaternary = tokens.length > 3 ? parseInt(tokens[3]) : null;
        var quinary = tokens.length > 4 ? parseInt(tokens[4]) : null;
        return new ClientAppVersion(primary, secondary, tertiary, quaternary, quinary);
    }

    public byte[] toHash() {
        try {
            var digest = MessageDigest.getInstance("MD5");
            digest.update(toString().getBytes());
            return digest.digest();
        } catch (NoSuchAlgorithmException exception) {
            throw new InternalError("Missing md5 implementation", exception);
        }
    }

    @Override
    public String toString() {
        var result = new StringBuilder();
        if(primary != null) {
            result.append(primary);
        }
        if(secondary != null) {
            if(!result.isEmpty()) {
                result.append('.');
            }
            result.append(secondary);
        }
        if(tertiary != null) {
            if(!result.isEmpty()) {
                result.append('.');
            }
            result.append(tertiary);
        }
        if(quaternary != null) {
            if(!result.isEmpty()) {
                result.append('.');
            }
            result.append(quaternary);
        }
        if(quinary != null) {
            if(!result.isEmpty()) {
                result.append('.');
            }
            result.append(quinary);
        }
        return result.toString();
    }

    public OptionalInt primary() {
        return primary == null ? OptionalInt.empty() : OptionalInt.of(primary);
    }

    public OptionalInt secondary() {
        return secondary == null ? OptionalInt.empty() : OptionalInt.of(secondary);
    }

    public OptionalInt tertiary() {
        return tertiary == null ? OptionalInt.empty() : OptionalInt.of(tertiary);
    }

    public OptionalInt quaternary() {
        return quaternary == null ? OptionalInt.empty() : OptionalInt.of(quaternary);
    }

    public OptionalInt quinary() {
        return quinary == null ? OptionalInt.empty() : OptionalInt.of(quinary);
    }

    public void setPrimary(Integer primary) {
        this.primary = primary;
    }

    public void setSecondary(Integer secondary) {
        this.secondary = secondary;
    }

    public void setTertiary(Integer tertiary) {
        this.tertiary = tertiary;
    }

    public void setQuaternary(Integer quaternary) {
        this.quaternary = quaternary;
    }

    public void setQuinary(Integer quinary) {
        this.quinary = quinary;
    }
}
