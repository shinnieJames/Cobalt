package com.github.auties00.cobalt.model.sync.action.media;

import com.github.auties00.cobalt.model.sync.SyncAction;

import java.util.Collections;
import java.util.List;
import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "SyncActionValue.FavoritesAction")
public final class FavoritesAction implements SyncAction {
    @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
    List<Favorite> favorites;


    FavoritesAction(List<Favorite> favorites) {
        this.favorites = favorites;
    }

    public List<Favorite> favorites() {
        return favorites == null ? List.of() : Collections.unmodifiableList(favorites);
    }

    public FavoritesAction setFavorites(List<Favorite> favorites) {
        this.favorites = favorites;
        return this;
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

        public Favorite setId(String id) {
            this.id = id;
            return this;
        }
    }
}
