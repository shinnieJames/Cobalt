package com.github.auties00.cobalt.graphql.whatsapp.business;

import com.alibaba.fastjson2.JSONWriter;
import com.github.auties00.cobalt.graphql.whatsapp.WhatsAppGraphQlOperation;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;

/**
 * Builds the relay mutation that updates which chats trigger the business AI assistant's automatic
 * replies.
 *
 * <p>The mutation takes a single {@code input} GraphQL variable. WhatsApp Web's
 * {@code WAWebBizAiReplyChatTriggerUpdateMutation.updateChatTrigger(triggerChatType)} wraps the
 * caller's trigger selection into the shape {@code {input: {trigger_chat_type: triggerChatType}}}
 * before handing it to the relay, so the only modelled field is the {@code trigger_chat_type}
 * naming the chat set the assistant replies to. The relay returns the update outcome under
 * {@code xfb_meta_ai_biz_agent_wa_update_reply_chat_trigger}; the reply is consumed through
 * {@link BizAiReplyChatTriggerUpdateWhatsAppGraphQlResponse}.
 *
 * @implNote This implementation keeps {@code trigger_chat_type} as a {@link String} rather than a
 * Java enum: the field is a server-side GraphQL enum and its full value set is not present in the JS
 * bundle of snapshot {@code 1040120866} (only the wire key {@code trigger_chat_type} appears, with no
 * confirmable constants), so the closed set cannot be reproduced without inventing values.
 *
 * @see BizAiReplyChatTriggerUpdateWhatsAppGraphQlResponse
 */
@WhatsAppWebModule(moduleName = "WAWebBizAiReplyChatTriggerUpdateMutation")
public final class BizAiReplyChatTriggerUpdateWhatsAppGraphQlRequest implements WhatsAppGraphQlOperation.Request {
    /**
     * The persisted document identifier the relay maps to the server-side compiled GraphQL document
     * for this operation.
     *
     * <p>Emitted as the {@code doc_id} field of the url-encoded request body.
     */
    @WhatsAppWebExport(moduleName = "WAWebBizAiReplyChatTriggerUpdateMutation.graphql", exports = "params.id",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String DOC_ID = "25994322200250984";

    /**
     * The GraphQL operation name carried by this request.
     *
     * <p>Used as the persisted-query lookup key and as the perf-telemetry tag.
     */
    @WhatsAppWebExport(moduleName = "WAWebBizAiReplyChatTriggerUpdateMutation.graphql", exports = "params.name",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String OPERATION_NAME = "WAWebBizAiReplyChatTriggerUpdateMutation";

    /**
     * The {@code trigger_chat_type} field of the {@code input} object naming the chat set the
     * assistant replies to, or {@code null} to omit it.
     *
     * <p>Kept as a {@link String} because the underlying server-side enum's value set is not
     * confirmable from the current snapshot.
     */
    private final String triggerChatType;

    /**
     * Constructs an update-reply-chat-trigger mutation request.
     *
     * <p>The {@code triggerChatType} populates the {@code trigger_chat_type} field of the
     * {@code input} GraphQL object; a {@code null} value omits the field from the serialized object.
     *
     * @param triggerChatType the chat set the assistant replies to, or {@code null} to omit the field
     */
    public BizAiReplyChatTriggerUpdateWhatsAppGraphQlRequest(String triggerChatType) {
        this.triggerChatType = triggerChatType;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String docId() {
        return DOC_ID;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String name() {
        return OPERATION_NAME;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote This implementation emits {@code {"input": {"trigger_chat_type": <triggerChatType>}}},
     * writing the {@code trigger_chat_type} field only when its value is non-null and emitting
     * {@code {"input": {}}} when it is {@code null}.
     */
    @WhatsAppWebExport(moduleName = "WAWebBizAiReplyChatTriggerUpdateMutation", exports = "updateChatTrigger",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @Override
    public String variables() {
        try (var writer = JSONWriter.ofUTF8()) {
            writer.startObject();
            writer.writeName("input");
            writer.writeColon();
            writer.startObject();
            if (triggerChatType != null) {
                writer.writeName("trigger_chat_type");
                writer.writeColon();
                writer.writeString(triggerChatType);
            }
            writer.endObject();
            writer.endObject();
            try (var output = new StringWriter()) {
                writer.flushTo(output);
                return output.toString();
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
