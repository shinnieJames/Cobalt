package com.github.auties00.cobalt.model.sync.action.setting;


import com.github.auties00.cobalt.model.sync.SyncActionArgs;

/**
 * Index arguments for {@link SettingsSyncAction}.
 *
 * @param category    the settings category
 * @param subcategory the settings subcategory
 * @param key         the settings key
 */
public record SettingsSyncActionArgs(String category, String subcategory, String key) implements SyncActionArgs {
    /**
     * {@inheritDoc}
     *
     * @return a three-element array encoding the settings sync key
     */
    @Override
    public String[] toIndexArgs() {
        return new String[]{category, subcategory, key};
    }
}
