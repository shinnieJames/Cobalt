package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

/**
 * Enumerates the user-initiated actions reported by WAM telemetry for the
 * community-creation journey on WhatsApp.
 *
 * <p>Each constant carries the fixed integer identifier transmitted on the
 * wire and tags a specific step the user took while navigating the community
 * NUX, group linking, and community creation flows (for example, tapping a
 * call-to-action, dismissing a surface, or observing the success or failure of
 * a creation or link operation). Values must never be renumbered or reused;
 * note the intentional gap at {@code 18}, preserved from the WA Web source.
 *
 * @implNote WAWebWamEnumCommunityCreationActionTakenType: the module
 *     default-exports a single frozen object
 *     {@code COMMUNITY_CREATION_ACTION_TAKEN_TYPE} whose keys are the action
 *     names and whose values are the integer identifiers; Cobalt mirrors the
 *     full enumeration with {@link WamEnumConstant} preserving each numeric
 *     value, including the skipped index {@code 18}.
 */
@WamEnum
@WhatsAppWebModule(moduleName = "WAWebWamEnumCommunityCreationActionTakenType")
public enum CommunityCreationActionTakenType {
    /**
     * User entered the community-creation surface.
     *
     * @implNote WAWebWamEnumCommunityCreationActionTakenType.COMMUNITY_CREATION_ACTION_TAKEN_TYPE: {@code ENTER = 1}.
     */
    @WamEnumConstant(1)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumCommunityCreationActionTakenType",
            exports = "COMMUNITY_CREATION_ACTION_TAKEN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    ENTER,

    /**
     * User tapped the "Get started" call-to-action on the community NUX.
     *
     * @implNote WAWebWamEnumCommunityCreationActionTakenType.COMMUNITY_CREATION_ACTION_TAKEN_TYPE: {@code GET_STARTED = 2}.
     */
    @WamEnumConstant(2)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumCommunityCreationActionTakenType",
            exports = "COMMUNITY_CREATION_ACTION_TAKEN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    GET_STARTED,

    /**
     * User dismissed the community-creation surface without completing it.
     *
     * @implNote WAWebWamEnumCommunityCreationActionTakenType.COMMUNITY_CREATION_ACTION_TAKEN_TYPE: {@code DISMISS = 3}.
     */
    @WamEnumConstant(3)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumCommunityCreationActionTakenType",
            exports = "COMMUNITY_CREATION_ACTION_TAKEN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    DISMISS,

    /**
     * User advanced to the next step in the community-creation wizard.
     *
     * @implNote WAWebWamEnumCommunityCreationActionTakenType.COMMUNITY_CREATION_ACTION_TAKEN_TYPE: {@code NEXT = 4}.
     */
    @WamEnumConstant(4)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumCommunityCreationActionTakenType",
            exports = "COMMUNITY_CREATION_ACTION_TAKEN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    NEXT,

    /**
     * User created a new group from within the community-creation flow.
     *
     * @implNote WAWebWamEnumCommunityCreationActionTakenType.COMMUNITY_CREATION_ACTION_TAKEN_TYPE: {@code CREATE_GROUP = 5}.
     */
    @WamEnumConstant(5)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumCommunityCreationActionTakenType",
            exports = "COMMUNITY_CREATION_ACTION_TAKEN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    CREATE_GROUP,

    /**
     * User linked an existing group to the community being created or edited.
     *
     * @implNote WAWebWamEnumCommunityCreationActionTakenType.COMMUNITY_CREATION_ACTION_TAKEN_TYPE: {@code LINK_GROUP = 6}.
     */
    @WamEnumConstant(6)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumCommunityCreationActionTakenType",
            exports = "COMMUNITY_CREATION_ACTION_TAKEN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    LINK_GROUP,

    /**
     * User triggered creation of the community itself.
     *
     * @implNote WAWebWamEnumCommunityCreationActionTakenType.COMMUNITY_CREATION_ACTION_TAKEN_TYPE: {@code CREATE_COMMUNITY = 7}.
     */
    @WamEnumConstant(7)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumCommunityCreationActionTakenType",
            exports = "COMMUNITY_CREATION_ACTION_TAKEN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    CREATE_COMMUNITY,

    /**
     * User exited the community-creation flow.
     *
     * @implNote WAWebWamEnumCommunityCreationActionTakenType.COMMUNITY_CREATION_ACTION_TAKEN_TYPE: {@code EXIT = 8}.
     */
    @WamEnumConstant(8)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumCommunityCreationActionTakenType",
            exports = "COMMUNITY_CREATION_ACTION_TAKEN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    EXIT,

    /**
     * User unlinked a previously linked group from the community.
     *
     * @implNote WAWebWamEnumCommunityCreationActionTakenType.COMMUNITY_CREATION_ACTION_TAKEN_TYPE: {@code UNLINK_GROUP = 9}.
     */
    @WamEnumConstant(9)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumCommunityCreationActionTakenType",
            exports = "COMMUNITY_CREATION_ACTION_TAKEN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    UNLINK_GROUP,

    /**
     * The community-creation operation completed successfully.
     *
     * @implNote WAWebWamEnumCommunityCreationActionTakenType.COMMUNITY_CREATION_ACTION_TAKEN_TYPE: {@code CREATE_COMMUNITY_SUCCESS = 10}.
     */
    @WamEnumConstant(10)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumCommunityCreationActionTakenType",
            exports = "COMMUNITY_CREATION_ACTION_TAKEN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    CREATE_COMMUNITY_SUCCESS,

    /**
     * The community-creation operation failed.
     *
     * @implNote WAWebWamEnumCommunityCreationActionTakenType.COMMUNITY_CREATION_ACTION_TAKEN_TYPE: {@code CREATE_COMMUNITY_FAIL = 11}.
     */
    @WamEnumConstant(11)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumCommunityCreationActionTakenType",
            exports = "COMMUNITY_CREATION_ACTION_TAKEN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    CREATE_COMMUNITY_FAIL,

    /**
     * User clicked the help-icon affordance on the community-creation surface.
     *
     * @implNote WAWebWamEnumCommunityCreationActionTakenType.COMMUNITY_CREATION_ACTION_TAKEN_TYPE: {@code HELP_ICON_CLICK = 12}.
     */
    @WamEnumConstant(12)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumCommunityCreationActionTakenType",
            exports = "COMMUNITY_CREATION_ACTION_TAKEN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    HELP_ICON_CLICK,

    /**
     * User confirmed the link-group action from the confirmation dialog.
     *
     * @implNote WAWebWamEnumCommunityCreationActionTakenType.COMMUNITY_CREATION_ACTION_TAKEN_TYPE: {@code LINK_GROUP_CONFIRMATION_OK = 13}.
     */
    @WamEnumConstant(13)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumCommunityCreationActionTakenType",
            exports = "COMMUNITY_CREATION_ACTION_TAKEN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    LINK_GROUP_CONFIRMATION_OK,

    /**
     * User cancelled the link-group action from the confirmation dialog.
     *
     * @implNote WAWebWamEnumCommunityCreationActionTakenType.COMMUNITY_CREATION_ACTION_TAKEN_TYPE: {@code LINK_GROUP_CONFIRMATION_CANCEL = 14}.
     */
    @WamEnumConstant(14)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumCommunityCreationActionTakenType",
            exports = "COMMUNITY_CREATION_ACTION_TAKEN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    LINK_GROUP_CONFIRMATION_CANCEL,

    /**
     * User chose to create a brand-new community in the link-group flow.
     *
     * @implNote WAWebWamEnumCommunityCreationActionTakenType.COMMUNITY_CREATION_ACTION_TAKEN_TYPE: {@code NEW_COMMUNITY = 15}.
     */
    @WamEnumConstant(15)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumCommunityCreationActionTakenType",
            exports = "COMMUNITY_CREATION_ACTION_TAKEN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    NEW_COMMUNITY,

    /**
     * User chose to attach the group to an existing community in the
     * link-group flow.
     *
     * @implNote WAWebWamEnumCommunityCreationActionTakenType.COMMUNITY_CREATION_ACTION_TAKEN_TYPE: {@code EXISTING_COMMUNITY = 16}.
     */
    @WamEnumConstant(16)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumCommunityCreationActionTakenType",
            exports = "COMMUNITY_CREATION_ACTION_TAKEN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    EXISTING_COMMUNITY,

    /**
     * User added a subgroup to the community and was promoted to community
     * admin for it.
     *
     * @implNote WAWebWamEnumCommunityCreationActionTakenType.COMMUNITY_CREATION_ACTION_TAKEN_TYPE: {@code ADD_SUBGROUP_AS_CADMIN = 17}.
     */
    @WamEnumConstant(17)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumCommunityCreationActionTakenType",
            exports = "COMMUNITY_CREATION_ACTION_TAKEN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    ADD_SUBGROUP_AS_CADMIN,

    /**
     * Member user suggested a subgroup be added to the community.
     *
     * @implNote WAWebWamEnumCommunityCreationActionTakenType.COMMUNITY_CREATION_ACTION_TAKEN_TYPE: {@code SUGGEST_SUBGROUP_AS_MEMBER = 19}. Index {@code 18} is intentionally skipped by the WA Web source.
     */
    @WamEnumConstant(19)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumCommunityCreationActionTakenType",
            exports = "COMMUNITY_CREATION_ACTION_TAKEN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    SUGGEST_SUBGROUP_AS_MEMBER,

    /**
     * User cancelled the current step of the community-creation flow.
     *
     * @implNote WAWebWamEnumCommunityCreationActionTakenType.COMMUNITY_CREATION_ACTION_TAKEN_TYPE: {@code CANCEL = 20}.
     */
    @WamEnumConstant(20)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumCommunityCreationActionTakenType",
            exports = "COMMUNITY_CREATION_ACTION_TAKEN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    CANCEL,

    /**
     * Attempt to add a group to an existing community failed.
     *
     * @implNote WAWebWamEnumCommunityCreationActionTakenType.COMMUNITY_CREATION_ACTION_TAKEN_TYPE: {@code ADD_GROUP_TO_EXISTING_COMMUNITY_FAIL = 21}.
     */
    @WamEnumConstant(21)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumCommunityCreationActionTakenType",
            exports = "COMMUNITY_CREATION_ACTION_TAKEN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    ADD_GROUP_TO_EXISTING_COMMUNITY_FAIL,

    /**
     * Adding a group to an existing community succeeded.
     *
     * @implNote WAWebWamEnumCommunityCreationActionTakenType.COMMUNITY_CREATION_ACTION_TAKEN_TYPE: {@code ADD_GROUP_TO_EXISTING_COMMUNITY_SUCCESS = 22}.
     */
    @WamEnumConstant(22)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumCommunityCreationActionTakenType",
            exports = "COMMUNITY_CREATION_ACTION_TAKEN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    ADD_GROUP_TO_EXISTING_COMMUNITY_SUCCESS
}
