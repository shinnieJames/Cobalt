package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.business.NoteStateBuilder;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncActionState;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.media.NoteEditAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.logging.Logger;

/**
 * Handles note edit sync actions.
 *
 * <p>Per WhatsApp Web {@code WAWebNoteSync}, this handler extends
 * {@code AccountSyncdActionBase} and processes the {@code "note_edit"} action.
 * It supports only SET operations. The per-mutation flow:
 * <ol>
 *   <li>Extract {@code indexParts[1]} as the note id ({@code s})</li>
 *   <li>Read the {@code noteEditAction} from the sync action value</li>
 *   <li>If {@code deleted === true}, remove the note by id and return {@code Success}</li>
 *   <li>Validate {@code type}, {@code chatJid} are non-null</li>
 *   <li>Validate {@code chatJid} via {@code validateChatJid}</li>
 *   <li>Resolve the chat via {@code resolveChatForMutationIndex}; if missing, return {@code Orphan}</li>
 *   <li>Resolve the canonical chat jid {@code L = widToChatJid(createWid(chat.id))}</li>
 *   <li>Resolve the note id {@code E = resolveNoteId(validatedChatJid, L, s)}:
 *       the original {@code s} when {@code L === validatedChatJid}, otherwise
 *       {@code sha256Str(L)} (SHA-256 hex encoded via Base64)</li>
 *   <li>Build the note record and call {@code addOrEditNote}</li>
 *   <li>Return {@code Success}</li>
 * </ol>
 *
 * <p>After processing the batch, WA Web calls
 * {@code frontendFireAndForget("removeNotes", ...)} and
 * {@code frontendFireAndForget("upsertNotesFromSyncd", ...)}. Cobalt's store
 * mutations are applied directly, so the frontend dispatch is omitted.
 */
@WhatsAppWebModule(moduleName = "WAWebNoteSync")
public final class NoteEditHandler implements WebAppStateActionHandler {
    /**
     * Logger for this handler.
     */
    private static final Logger LOGGER = Logger.getLogger(NoteEditHandler.class.getName());

    /**
     * Constructs the singleton note edit handler.
     */
    public NoteEditHandler() {

    }

    /**
     * Returns the action type name this handler processes.
     * @return the action type name
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebNoteSync", exports = "getAction", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return NoteEditAction.ACTION_NAME;
    }

    /**
     * Returns the sync collection this handler's action belongs to.
     * @return the sync patch type / collection name
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebNoteSync", exports = "collectionName", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return NoteEditAction.COLLECTION_NAME;
    }

    /**
     * Returns the mutation format version for this handler.
     * @return the handler's supported mutation version
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebNoteSync", exports = "getVersion", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return NoteEditAction.ACTION_VERSION;
    }

    /**
     * Applies a note edit mutation and returns a detailed result.
     *
     * <p>Per WhatsApp Web {@code WAWebNoteSync.applyMutations}, the per-mutation
     * logic performs the following steps, wrapped in a try/catch that yields
     * {@link SyncActionState#FAILED} on any exception:
     * <ol>
     *   <li>If operation is not SET, return {@code Unsupported}</li>
     *   <li>Extract {@code s = indexParts[1]} and validate it is non-empty;
     *       otherwise return {@code malformedActionIndex()}</li>
     *   <li>Read {@code u = value.noteEditAction}; if missing, return
     *       {@code malformedActionValue(collectionName)}</li>
     *   <li>If {@code u.deleted === true}, remove the note by id and return {@code Success}</li>
     *   <li>Validate {@code u.type}, {@code u.chatJid} are non-null;
     *       {@code validateChatJid(u.chatJid)} is non-null</li>
     *   <li>Resolve the chat via {@code resolveChatForMutationIndex}; if missing,
     *       return {@code Orphan} with the orphan model</li>
     *   <li>Compute the resolved canonical chat jid {@code L = widToChatJid(createWid(chat.id))}</li>
     *   <li>Compute the resolved note id {@code E = resolveNoteId(validatedChatJid, L, s)}</li>
     *   <li>Build the note record and call {@code addOrEditNote}</li>
     *   <li>Return {@code Success}</li>
     * </ol>
     * @param client   the WhatsApp client instance
     * @param mutation the mutation to apply
     * @return the detailed application result
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebNoteSync", exports = "applyMutations", adaptation = WhatsAppAdaptation.ADAPTED)
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        try {
            if (mutation.operation() != SyncdOperation.SET) {
                return MutationApplicationResult.unsupported();
            }

            var indexArray = JSON.parseArray(mutation.index());
            if (indexArray.size() < 2) {
                return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName());
            }
            var noteId = indexArray.getString(1);
            if (noteId == null || noteId.isEmpty()) {
                return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName());
            }

            if (!(mutation.value().action().orElse(null) instanceof NoteEditAction action)) {
                return SyncdIndexUtils.malformedActionValue(collectionName().name());
            }

            //     yield getNoteTable().remove(s); v.push(s); return {actionState: Success}
            // }
            if (action.deleted()) {
                client.store().removeNoteState(noteId);
                return MutationApplicationResult.success();
            }

            var type = action.type().orElse(null);
            var rawChatJid = action.chatJid().orElse(null);
            var content = action.unstructuredContent().orElse(null);
            var createdAtOpt = action.createdAt();

            if (type == null) {
                return SyncdIndexUtils.malformedActionValue(collectionName().name());
            }
            if (rawChatJid == null) {
                return SyncdIndexUtils.malformedActionValue(collectionName().name());
            }
            // ADAPTED: WAWebNoteSync.applyMutations — validateChatJid(c); Cobalt uses Jid.of which is more lenient than validateChatJid
            var validatedChatJid = rawChatJid;

            if (createdAtOpt.isEmpty()) {
                LOGGER.warning("noteEditAction.createdAt is empty");
            }
            //   var f = maybeNumber(d); d != null && f == null && C++
            // ADAPTED: WAWebNoteSync.applyMutations — WALongInt.maybeNumber converts BigInt-safe numbers;
            // Cobalt's Long is already 64-bit so no conversion / safe-int check is needed.
            if (content == null) {
                LOGGER.warning("noteEditAction.unstructuredContent is empty");
            }

            // if (!R.success) return {actionState: Orphan, orphanModel: R.orphanModel}
            // ADAPTED: WAWebNoteSync.applyMutations — Cobalt uses findChatByJid
            var chat = client.store().findChatByJid(validatedChatJid);
            if (chat.isEmpty()) {
                return MutationApplicationResult.orphan(validatedChatJid.toString(), "Chat");
            }

            // ADAPTED: Cobalt's Chat.jid() already returns the canonical chat JID
            var resolvedChatJid = chat.get().jid();

            var resolvedNoteId = resolveNoteId(validatedChatJid, resolvedChatJid, noteId);

            //     id: E,
            //     type: m === UNSTRUCTURED ? "unstructured" : "structured",
            //     chatJid: L,
            //     content: p != null ? p : "",
            //     modifiedAt: Math.floor(e.timestamp / 1000),
            //     createdAt: Math.floor((f != null ? f : 0) / 1000)
            // }
            // ADAPTED: WAWebNoteSync.applyMutations — WA Web stores a flattened note record
            // ({id, type, chatJid, content, modifiedAt, createdAt}) in the NoteTable IDB. Cobalt
            // stores the mirrored fields directly on the NoteState wrapper keyed by the resolved
            // note id, preserving the action's type enum and createdAt as a native Instant.
            client.store().putNoteState(new NoteStateBuilder()
                    .id(resolvedNoteId)
                    .type(type)
                    .chatJid(resolvedChatJid)
                    .createdAt(createdAtOpt.orElse(Instant.EPOCH))
                    .deleted(false) // ADAPTED: explicit false for the non-deleted branch
                    .unstructuredContent(content != null ? content : "")
                    .build());

            return MutationApplicationResult.success();
        } catch (Exception e) {
            LOGGER.warning("Note edit mutation failed: " + e.getMessage());
            return MutationApplicationResult.failed();
        }
    }

    /**
     * Resolves the note id used as the store key for a note edit mutation.
     *
     * <p>Per WhatsApp Web {@code WAWebNoteSync.resolveNoteId}:
     * <pre>{@code
     * resolveNoteId(e, t, n) {
     *     return t === e ? n : sha256Str(t);
     * }
     * }</pre>
     * where {@code e} is the action's validated chat jid, {@code t} is the
     * resolved canonical chat jid from the store, and {@code n} is the note id
     * from the mutation index. If both jids match, the original note id is used;
     * otherwise the note id is derived from the SHA-256 of the resolved chat jid
     * (base64-encoded).
     * @param actionChatJid the validated chat jid from the action payload
     * @param resolvedChatJid the canonical chat jid resolved from the store
     * @param indexNoteId the note id from the mutation index
     * @return the resolved note id used as the store key
     */
    @WhatsAppWebExport(moduleName = "WAWebNoteSync", exports = "resolveNoteId", adaptation = WhatsAppAdaptation.DIRECT)
    private String resolveNoteId(Jid actionChatJid, Jid resolvedChatJid, String indexNoteId) {
        if (resolvedChatJid.equals(actionChatJid)) {
            return indexNoteId;
        }
        return generateNoteId(resolvedChatJid.toString());
    }

    /**
     * Generates a note id by computing the SHA-256 hash of the given string.
     *
     * <p>Per WhatsApp Web {@code WAWebNotesIdUtils.generateNoteId}:
     * <pre>{@code
     * generateNoteId(e) { return sha256Str(e); }
     * }</pre>
     * where {@code sha256Str} produces SHA-256 bytes of the UTF-8 code points and
     * then base64-encodes them via {@code WABase64.encodeB64}.
     * @param input the string to hash
     * @return the base64-encoded SHA-256 hash of the input
     * @throws IllegalStateException if the SHA-256 algorithm is unavailable on the JVM
     */
    @WhatsAppWebExport(moduleName = "WAWebNotesIdUtils", exports = "generateNoteId", adaptation = WhatsAppAdaptation.ADAPTED)
    private String generateNoteId(String input) {
        try {
            var digest = MessageDigest.getInstance("SHA-256"); // WACryptoSha256.sha256
            var hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash); // WABase64.encodeB64
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

}
