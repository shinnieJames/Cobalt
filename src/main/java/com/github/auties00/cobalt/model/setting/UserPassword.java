package com.github.auties00.cobalt.model.setting;

import java.util.Collections;
import java.util.List;
import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "UserPassword")
public final class UserPassword {
    @ProtobufProperty(index = 1, type = ProtobufType.ENUM)
    Encoding encoding;

    @ProtobufProperty(index = 2, type = ProtobufType.ENUM)
    Transformer transformer;

    @ProtobufProperty(index = 3, type = ProtobufType.MESSAGE)
    List<TransformerArg> transformerArg;

    @ProtobufProperty(index = 4, type = ProtobufType.BYTES)
    byte[] transformedData;


    UserPassword(Encoding encoding, Transformer transformer, List<TransformerArg> transformerArg, byte[] transformedData) {
        this.encoding = encoding;
        this.transformer = transformer;
        this.transformerArg = transformerArg;
        this.transformedData = transformedData;
    }

    public Optional<Encoding> encoding() {
        return Optional.ofNullable(encoding);
    }

    public Optional<Transformer> transformer() {
        return Optional.ofNullable(transformer);
    }

    public List<TransformerArg> transformerArg() {
        return transformerArg == null ? List.of() : Collections.unmodifiableList(transformerArg);
    }

    public Optional<byte[]> transformedData() {
        return Optional.ofNullable(transformedData);
    }

    public void setEncoding(Encoding encoding) {
        this.encoding = encoding;
    }

    public void setTransformer(Transformer transformer) {
        this.transformer = transformer;
    }

    public void setTransformerArg(List<TransformerArg> transformerArg) {
        this.transformerArg = transformerArg;
    }

    public void setTransformedData(byte[] transformedData) {
        this.transformedData = transformedData;
    }

    @ProtobufEnum(name = "UserPassword.Encoding")
    public static enum Encoding {
        UTF8(0),
        UTF8_BROKEN(1);

        Encoding(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        public int index() {
            return this.index;
        }
    }

    @ProtobufEnum(name = "UserPassword.Transformer")
    public static enum Transformer {
        NONE(0),
        PBKDF2_HMAC_SHA512(1),
        PBKDF2_HMAC_SHA384(2);

        Transformer(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        public int index() {
            return this.index;
        }
    }

    @ProtobufMessage(name = "UserPassword.TransformerArg")
    public static final class TransformerArg {
        @ProtobufProperty(index = 1, type = ProtobufType.STRING)
        String key;

        @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
        TransformerArg.Value value;


        TransformerArg(String key, Value value) {
            this.key = key;
            this.value = value;
        }

        public Optional<String> key() {
            return Optional.ofNullable(key);
        }

        public Optional<Value> value() {
            return Optional.ofNullable(value);
        }

        public void setKey(String key) {
            this.key = key;
    }

        public void setValue(Value value) {
            this.value = value;
    }

        public sealed interface ValueSpec permits ValueSpec.AsBlob, ValueSpec.AsUnsignedInteger {

            final class AsBlob implements ValueSpec {
                byte[] asBlob;

                AsBlob(byte[] asBlob) {
                    this.asBlob = asBlob;
                }

                @ProtobufSerializer
                public byte[] asBlob() {
                    return asBlob;
                }

                @ProtobufDeserializer
                public static AsBlob of(byte[] asBlob) {
                    return new AsBlob(asBlob);
                }
            }

            final class AsUnsignedInteger implements ValueSpec {
                Integer asUnsignedInteger;

                AsUnsignedInteger(Integer asUnsignedInteger) {
                    this.asUnsignedInteger = asUnsignedInteger;
                }

                @ProtobufSerializer
                public Integer asUnsignedInteger() {
                    return asUnsignedInteger;
                }

                @ProtobufDeserializer
                public static AsUnsignedInteger of(Integer asUnsignedInteger) {
                    return new AsUnsignedInteger(asUnsignedInteger);
                }
            }
        }

        @ProtobufMessage(name = "UserPassword.TransformerArg.Value")
        public static final class Value {
            @ProtobufProperty(index = 1, type = ProtobufType.BYTES)
            byte[] asBlob;

            @ProtobufProperty(index = 2, type = ProtobufType.UINT32)
            Integer asUnsignedInteger;


            Value(byte[] asBlob, Integer asUnsignedInteger) {
                this.asBlob = asBlob;
                this.asUnsignedInteger = asUnsignedInteger;
            }

            public Optional<? extends ValueSpec> value() {
                if (asBlob != null) return Optional.of(ValueSpec.AsBlob.of(asBlob));
                if (asUnsignedInteger != null) return Optional.of(ValueSpec.AsUnsignedInteger.of(asUnsignedInteger));
                return Optional.empty();
            }

            public void setAsBlob(byte[] asBlob) {
                this.asBlob = asBlob;
    }

            public void setAsUnsignedInteger(Integer asUnsignedInteger) {
                this.asUnsignedInteger = asUnsignedInteger;
    }
        }
    }
}
