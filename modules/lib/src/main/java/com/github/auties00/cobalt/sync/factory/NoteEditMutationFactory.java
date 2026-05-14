package com.github.auties00.cobalt.sync.factory;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.action.media.NoteEditAction;
import com.github.auties00.cobalt.model.sync.action.media.NoteEditActionBuilder;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.SyncPendingMutation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

import java.time.Instant;
import java.util.List;

/**
 * Builds outgoing note-edit sync mutations.
 *
 * <p>Mirrors the {@code getNoteMutation} export of WhatsApp Web's
 * {@code WAWebNoteSync} module. The factory is the outgoing-mutation
 * counterpart of
 * {@link com.github.auties00.cobalt.sync.handler.NoteEditHandler}.
 */
public final class NoteEditMutationFactory {
    /**
     * Constructs a note-edit mutation factory.
     */
    public NoteEditMutationFactory() {

    }

    /**
     * Builds a pending outgoing mutation that creates, edits, or deletes a
     * chat-scoped note.
     *
     * <p>Per WhatsApp Web {@code WAWebNoteSync}: emits a SET mutation at
     * {@code ["note_edit", noteId]} in the REGULAR_LOW collection with
     * {@code version = 7} and a {@code noteEditAction} sub-message carrying
     * the note content, chat jid, creation timestamp, and deletion flag.
     *
     * <p>When {@code deleted} is {@code true} the {@code unstructuredContent}
     * and {@code chatJid} fields are still populated because WA Web's inbound
     * handler reads them before branching on the deletion flag.
     *
     * @param noteId  the note identifier used as the mutation index
     * @param chatJid the chat the note is attached to
     * @param content the note text, may be {@code null} for deletions
     * @param deleted {@code true} when the mutation deletes the note
     * @return the pending mutation ready to be pushed via
     *         {@link com.github.auties00.cobalt.sync.WebAppStateService#pushPatches}
     */
    @WhatsAppWebExport(moduleName = "WAWebNoteSync", exports = "getNoteMutation", adaptation = WhatsAppAdaptation.ADAPTED)
    public SyncPendingMutation getNoteEditMutation(String noteId, Jid chatJid, String content, boolean deleted) {
        return getNoteEditMutation(noteId, chatJid, content, deleted, Instant.now());
    }

    /**
     * Overload accepting an explicit {@link Instant} for the mutation
     * timestamp and the {@code createdAt} field on the action. The
     * public {@link #getNoteEditMutation(String, Jid, String, boolean)} call
     * delegates here with {@link Instant#now()} so production callers behave
     * unchanged; the explicit overload exists so tests can pin a
     * deterministic timestamp.
     *
     * @param noteId    the note identifier used as the mutation index
     * @param chatJid   the chat the note is attached to
     * @param content   the note text, may be {@code null} for deletions
     * @param deleted   {@code true} when the mutation deletes the note
     * @param timestamp the timestamp to seed into the action value and the
     *                  pending mutation
     * @return the pending mutation
     */
    public SyncPendingMutation getNoteEditMutation(String noteId, Jid chatJid, String content, boolean deleted, Instant timestamp) {
        var action = new NoteEditActionBuilder()
                .type(NoteEditAction.NoteType.UNSTRUCTURED)
                .chatJid(chatJid)
                .createdAt(timestamp)
                .deleted(deleted) // ADAPTED: WAWebNoteSync.getNoteMutation does not set deleted; Cobalt overloads this builder for the deletion path consumed by applyMutations (u.deleted === true)
                .unstructuredContent(content == null ? "" : content)
                .build();
        var value = new SyncActionValueBuilder()
                .timestamp(timestamp)
                .noteEditAction(action)
                .build();
        var index = JSON.toJSONString(List.of(NoteEditAction.ACTION_NAME, noteId)); // ["note_edit", noteId]
        var mutation = new DecryptedMutation.Trusted(
                index,
                value,
                SyncdOperation.SET,
                timestamp,
                NoteEditAction.ACTION_VERSION
        );
        return new SyncPendingMutation(mutation, 0);
    }
}
