package com.github.auties00.cobalt.model.bot.ai;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

/**
 * Information about an AI conversation thread within a WhatsApp chat.
 *
 * <p>AI threads allow users to have separate, named conversations with Meta AI.
 * Each thread has both {@linkplain #serverInfo() server-side metadata} (such as
 * the thread title) and {@linkplain #clientInfo() client-side metadata} (such as
 * the thread privacy type).
 */
@ProtobufMessage(name = "AIThreadInfo")
public final class AIThreadInfo {
    /**
     * The server-side metadata for this AI thread, including the thread title.
     */
    @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
    AIThreadServerInfo serverInfo;

    /**
     * The client-side metadata for this AI thread, including the privacy type.
     */
    @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
    AIThreadClientInfo clientInfo;


    /**
     * Constructs a new {@code AIThreadInfo} with the specified values.
     *
     * @param serverInfo the server-side thread metadata, or {@code null}
     * @param clientInfo the client-side thread metadata, or {@code null}
     */
    AIThreadInfo(AIThreadServerInfo serverInfo, AIThreadClientInfo clientInfo) {
        this.serverInfo = serverInfo;
        this.clientInfo = clientInfo;
    }

    /**
     * Returns the server-side metadata for this AI thread.
     *
     * @return an {@code Optional} describing the server info, or an empty
     *         {@code Optional} if not set
     */
    public Optional<AIThreadServerInfo> serverInfo() {
        return Optional.ofNullable(serverInfo);
    }

    /**
     * Returns the client-side metadata for this AI thread.
     *
     * @return an {@code Optional} describing the client info, or an empty
     *         {@code Optional} if not set
     */
    public Optional<AIThreadClientInfo> clientInfo() {
        return Optional.ofNullable(clientInfo);
    }

    /**
     * Sets the server-side metadata for this AI thread.
     *
     * @param serverInfo the new server info, or {@code null}
     */
    public void setServerInfo(AIThreadServerInfo serverInfo) {
        this.serverInfo = serverInfo;
    }

    /**
     * Sets the client-side metadata for this AI thread.
     *
     * @param clientInfo the new client info, or {@code null}
     */
    public void setClientInfo(AIThreadClientInfo clientInfo) {
        this.clientInfo = clientInfo;
    }

    /**
     * Client-side metadata for an AI conversation thread, specifying the
     * privacy type chosen by the user when the thread was created.
     */
    @ProtobufMessage(name = "AIThreadInfo.AIThreadClientInfo")
    public static final class AIThreadClientInfo {
        /**
         * The privacy type of this AI thread.
         */
        @ProtobufProperty(index = 1, type = ProtobufType.ENUM)
        AIThreadClientInfo.AIThreadType type;


        /**
         * Constructs a new {@code AIThreadClientInfo} with the specified type.
         *
         * @param type the thread privacy type, or {@code null}
         */
        AIThreadClientInfo(AIThreadType type) {
            this.type = type;
        }

        /**
         * Returns the privacy type of this AI thread.
         *
         * @return an {@code Optional} describing the thread type, or an empty
         *         {@code Optional} if not set
         */
        public Optional<AIThreadType> type() {
            return Optional.ofNullable(type);
        }

        /**
         * Sets the privacy type of this AI thread.
         *
         * @param type the new thread type, or {@code null}
         */
        public void setType(AIThreadType type) {
            this.type = type;
    }

        /**
         * The privacy type of an AI conversation thread.
         */
        @ProtobufEnum(name = "AIThreadInfo.AIThreadClientInfo.AIThreadType")
        public static enum AIThreadType {
            /**
             * The thread type is unknown or unspecified.
             */
            UNKNOWN(0),

            /**
             * A standard AI thread where conversation history is retained and may
             * be used to personalize future responses.
             */
            DEFAULT(1),

            /**
             * An incognito AI thread where conversation history is not persisted
             * and is not used to train or personalize the AI model.
             */
            INCOGNITO(2);

            AIThreadType(@ProtobufEnumIndex int index) {
                this.index = index;
            }

            /**
             * The protobuf index of this enum constant.
             */
            final int index;

            /**
             * Returns the protobuf index of this enum constant.
             *
             * @return the protobuf index
             */
            public int index() {
                return this.index;
            }
        }
    }

    /**
     * Server-side metadata for an AI conversation thread.
     */
    @ProtobufMessage(name = "AIThreadInfo.AIThreadServerInfo")
    public static final class AIThreadServerInfo {
        /**
         * The server-assigned title for this AI thread, for example
         * {@code "Trip planning to Rome"}.
         */
        @ProtobufProperty(index = 1, type = ProtobufType.STRING)
        String title;


        /**
         * Constructs a new {@code AIThreadServerInfo} with the specified title.
         *
         * @param title the thread title, or {@code null}
         */
        AIThreadServerInfo(String title) {
            this.title = title;
        }

        /**
         * Returns the server-assigned title for this AI thread.
         *
         * @return an {@code Optional} describing the title, or an empty
         *         {@code Optional} if not set
         */
        public Optional<String> title() {
            return Optional.ofNullable(title);
        }

        /**
         * Sets the server-assigned title for this AI thread.
         *
         * @param title the new title, or {@code null}
         */
        public void setTitle(String title) {
            this.title = title;
    }
    }
}
