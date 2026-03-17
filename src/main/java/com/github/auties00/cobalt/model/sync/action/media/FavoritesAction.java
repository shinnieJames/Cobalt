package com.github.auties00.cobalt.model.sync.action.media;

import com.github.auties00.cobalt.model.sync.SyncActionEmptyArgs;
import com.github.auties00.cobalt.model.sync.SyncAction;
import com.github.auties00.cobalt.model.sync.SyncPatchType;

import java.util.Collections;
import java.util.List;
import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "SyncActionValue.FavoritesAction")
public final class FavoritesAction implements SyncAction<SyncActionEmptyArgs> {
    /**
     * Canonical WhatsApp Web action name for this action type.
     */
    public static final String ACTION_NAME = "favorites";

    /**
     * Canonical WhatsApp Web action version for this action type.
     */
    public static final int ACTION_VERSION = 1;

    /**
     * Canonical WhatsApp Web collection name for this action type.
     */
    public static final SyncPatchType COLLECTION_NAME = SyncPatchType.REGULAR_HIGH;

    /**
     * {@inheritDoc}
     */
    @Override
    public String actionName() {
        return ACTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int actionVersion() {
        return ACTION_VERSION;
    }


    @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
    List<Favorite> favorites;


    FavoritesAction(List<Favorite> favorites) {
        this.favorites = favorites;
    }

    public List<Favorite> favorites() {
        return favorites == null ? List.of() : Collections.unmodifiableList(favorites);
    }

    public void setFavorites(List<Favorite> favorites) {
        this.favorites = favorites;
    }

    @ProtobufMessage(name = "SyncActionValue.FavoritesAction.Favorite")
    public static final class Favorite {
        @ProtobufProperty(index = 1, type = ProtobufType.STRING)
        String id;


        Favorite(String id) {
            this.id = id;
        }

        public Optional<String> id() {
            return Optional.ofNullable(id);
        }

        public void setId(String id) {
            this.id = id;
    }
    }
}
