package com.github.auties00.cobalt.node.mex.json.misc;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.mex.MexOperation;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.Optional;

/**
 * Inbound parsed response of the {@link FetchIntegritySignalsMexRequest}
 * query, exposing the {@code is_new_account} and
 * {@code is_suspicious_start_chat} flags from the first
 * {@code xwa2_fetch_wa_users} entry's {@code integrity_signals_info}
 * sub-object.
 *
 * @apiNote Drives the FMX safety nudges in WA Web's chat composer; the
 * matching call-site stores both flags in the chat row through
 * {@code WAWebBackendApi.frontendSendAndReceive("chatCollectionUpdate", ...)}
 * and {@code WAWebDBUpdateChatTable.updateChatTable}. Cobalt callers may
 * map the booleans onto their own chat model without invoking the
 * WA Web backend pipeline.
 *
 * @implNote This implementation surfaces the two boolean scalars as
 * {@link Optional} containers; WA Web folds {@code undefined} values to
 * {@code null} in the original {@code (x != null ? x : null)} JS guard so
 * that {@link #of(byte[])} returning {@link Optional#empty()} for the
 * outer envelope and {@code Optional} per-scalar keeps the absence
 * semantics identical to the JS pipeline.
 */
@WhatsAppWebModule(moduleName = "WAWebMexFetchIntegritySignals")
public final class FetchIntegritySignalsMexResponse implements MexOperation.Response.Json {
    /**
     * The {@code is_new_account} scalar projected from
     * {@code xwa2_fetch_wa_users[0].integrity_signals_info.is_new_account}.
     */
    private final Boolean isNewAccount;

    /**
     * The {@code is_suspicious_start_chat} scalar projected from
     * {@code xwa2_fetch_wa_users[0].integrity_signals_info.is_suspicious_start_chat}.
     */
    private final Boolean isSuspicious;

    /**
     * Constructs a new response wrapping the two boolean scalars parsed from
     * the {@code integrity_signals_info} sub-object.
     *
     * @apiNote Private; instances are produced by the {@link #of(Node)}
     * parser.
     *
     * @param isNewAccount  the {@code is_new_account} scalar, may be {@code null}
     * @param isSuspicious  the {@code is_suspicious_start_chat} scalar, may be {@code null}
     */
    private FetchIntegritySignalsMexResponse(Boolean isNewAccount, Boolean isSuspicious) {
        this.isNewAccount = isNewAccount;
        this.isSuspicious = isSuspicious;
    }

    /**
     * Parses the MEX response carried by an inbound IQ stanza.
     *
     * @apiNote Reads the {@code <result>} child's byte content and routes it
     * through the private byte-level parser. Returns
     * {@link Optional#empty()} when the stanza carries no result, when the
     * envelope's {@code xwa2_fetch_wa_users} array is missing or empty, or
     * when the first user entry's {@code integrity_signals_info} is absent;
     * WA Web mirrors the same absence by logging a {@code "no integrity
     * signals"} entry and returning {@code null}.
     *
     * @param node the inbound IQ stanza carrying the {@code <result>} child
     * @return an {@link Optional} wrapping the parsed response, or
     *         {@link Optional#empty()} if the expected JSON shape is absent
     */
    @WhatsAppWebExport(moduleName = "WAWebMexFetchIntegritySignals", exports = "fetchIntegritySignals",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static Optional<FetchIntegritySignalsMexResponse> of(Node node) {
        return node.getChild("result")
                .flatMap(Node::toContentBytes)
                .flatMap(FetchIntegritySignalsMexResponse::of);
    }

    /**
     * Returns the {@code is_new_account} scalar reflecting whether the
     * relay considers the target user to be a freshly registered account.
     *
     * @return an {@link Optional} containing the new-account flag, or
     *         {@link Optional#empty()} if the relay omitted the scalar
     */
    public Optional<Boolean> isNewAccount() {
        return Optional.ofNullable(isNewAccount);
    }

    /**
     * Returns the {@code is_suspicious_start_chat} scalar reflecting
     * whether the relay considers starting a chat with the target user
     * to be suspicious.
     *
     * @return an {@link Optional} containing the suspicious-start flag, or
     *         {@link Optional#empty()} if the relay omitted the scalar
     */
    public Optional<Boolean> isSuspicious() {
        return Optional.ofNullable(isSuspicious);
    }

    /**
     * Parses the JSON payload carried by the {@code <result>} child into a
     * {@link FetchIntegritySignalsMexResponse}.
     *
     * @apiNote Private; routed through {@link #of(Node)} after the byte
     * content of the {@code <result>} child is extracted. Returns
     * {@link Optional#empty()} when the envelope, the
     * {@code xwa2_fetch_wa_users} array (empty array included), the first
     * user entry, or the {@code integrity_signals_info} sub-object is
     * absent.
     *
     * @param json the UTF-8 encoded JSON payload
     * @return an {@link Optional} wrapping the parsed response, or
     *         {@link Optional#empty()} if the envelope is missing
     */
    private static Optional<FetchIntegritySignalsMexResponse> of(byte[] json) {
        var jsonObject = JSON.parseObject(json);
        if (jsonObject == null) {
            return Optional.empty();
        }

        var data = jsonObject.getJSONObject("data");
        if (data == null) {
            return Optional.empty();
        }

        var rootArr = data.getJSONArray("xwa2_fetch_wa_users");
        if (rootArr == null || rootArr.isEmpty()) {
            return Optional.empty();
        }

        var first = rootArr.getJSONObject(0);
        if (first == null) {
            return Optional.empty();
        }

        var info = first.getJSONObject("integrity_signals_info");
        if (info == null) {
            return Optional.empty();
        }

        var isNewAccount = info.getBoolean("is_new_account");
        var isSuspicious = info.getBoolean("is_suspicious_start_chat");

        return Optional.of(new FetchIntegritySignalsMexResponse(isNewAccount, isSuspicious));
    }
}
