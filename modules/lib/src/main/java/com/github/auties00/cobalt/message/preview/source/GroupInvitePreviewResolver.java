package com.github.auties00.cobalt.message.preview.source;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.chat.group.GroupMetadata;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.text.ExtendedTextMessage;
import com.github.auties00.cobalt.message.preview.PreviewThumbnailFetcher;
import com.github.auties00.cobalt.message.preview.model.LinkDetails;
import com.github.auties00.cobalt.message.preview.model.LinkThumbnail;
import com.github.auties00.cobalt.message.preview.model.ResolvedPreview;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;

/**
 * Builds a {@link LinkDetails}/{@link LinkThumbnail} pair for
 * {@code chat.whatsapp.com} group invite links by querying the server
 * for the target group's metadata via
 * {@link WhatsAppClient#queryInviteGroupInfo(String)}.
 */
@WhatsAppWebModule(moduleName = "WAWebLinkPreviewGroupUtils")
public final class GroupInvitePreviewResolver {
    /**
     * Default description used when no community/sub-group hint is
     * available, mirroring WA Web's fbt-localised string.
     */
    @WhatsAppWebExport(moduleName = "WAWebLinkPreviewGroupUtils", exports = "GROUP_INVITE_DEFAULT_DESCRIPTION",
            adaptation = WhatsAppAdaptation.DIRECT)
    static final String GROUP_INVITE_DEFAULT_DESCRIPTION = "Group chat invite";

    /**
     * Hidden constructor for the utility class.
     *
     * @throws UnsupportedOperationException always
     */
    private GroupInvitePreviewResolver() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Resolves the preview for a group-invite URL.
     *
     * <p>Mirrors {@code WAWebLinkPreviewGroupUtils.getGroupInviteLinkPreview}:
     * queries the server for the target group's metadata and downloads
     * the group's profile picture as the inline JPEG thumbnail. WA Web
     * also resizes the thumbnail to 100×100; Cobalt embeds the
     * server-supplied bytes verbatim because there is no reliable
     * canvas equivalent in the JDK runtime when {@code java.desktop}
     * is excluded.
     *
     * @param client     the WhatsApp client used to query the server
     * @param code       the invite code parsed from the URL
     * @param httpClient the HTTP client used to download the group
     *                   profile picture
     * @param timeout    the per-download timeout, derived from
     *                   {@code link_preview_wait_time}
     * @return the preview details and inline thumbnail, or empty when
     *         the lookup failed
     */
    @WhatsAppWebExport(moduleName = "WAWebLinkPreviewGroupUtils", exports = "getGroupInviteLinkPreview",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static Optional<ResolvedPreview> resolve(WhatsAppClient client, String code, HttpClient httpClient, Duration timeout) {
        if (client == null || code == null || code.isEmpty()) {
            return Optional.empty();
        }
        try {
            var metadata = client.queryInviteGroupInfo(code).orElse(null);
            if (metadata == null) {
                return Optional.empty();
            }
            var details = new LinkDetails(
                    metadata.subject(),
                    inviteLinkDescription(client, metadata),
                    ExtendedTextMessage.PreviewType.NONE,
                    true);
            var thumbnailBytes = downloadGroupPicture(client, metadata.jid(), httpClient, timeout);
            var thumbnail = thumbnailBytes == null
                    ? null
                    : new LinkThumbnail(thumbnailBytes, null, null, null, null, null, null, null);
            return Optional.of(new ResolvedPreview(details, thumbnail));
        } catch (RuntimeException _) {
            return Optional.empty();
        }
    }

    /**
     * Resolves the group profile picture URL through
     * {@link WhatsAppClient#queryPicture(Jid)}
     * and downloads its bytes.
     *
     * @param client     the WhatsApp client used to resolve the URL
     * @param groupJid   the group whose picture is requested
     * @param httpClient the HTTP client used for the download
     * @param timeout    the per-download timeout
     * @return the downloaded JPEG bytes, or {@code null} when no
     *         picture was set or the download failed
     */
    private static byte[] downloadGroupPicture(WhatsAppClient client,
                                               Jid groupJid,
                                               HttpClient httpClient,
                                               Duration timeout) {
        if (groupJid == null) {
            return null;
        }
        try {
            var pictureUri = client.queryPicture(groupJid).orElse(null);
            if (pictureUri == null) {
                return null;
            }
            return PreviewThumbnailFetcher.download(httpClient, pictureUri, timeout);
        } catch (RuntimeException _) {
            return null;
        }
    }

    /**
     * Picks the preview description based on the group's type, mirroring
     * WhatsApp Web's {@code inviteLinkDescription(groupType, parentTitle)}
     * helper.
     *
     * @param client   the WhatsApp client used to resolve the parent
     *                 community's display title when the invite points
     *                 at a sub-group
     * @param metadata the resolved group metadata
     * @return the preview description string
     */
    @WhatsAppWebExport(moduleName = "WAWebLinkPreviewGroupUtils", exports = "getInviteLinkDescription",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private static String inviteLinkDescription(WhatsAppClient client,
                                                GroupMetadata metadata) {
        if (metadata.isDefaultSubgroup()) {
            return "Announcements";
        }
        var parentCommunity = metadata.parentCommunityJid().orElse(null);
        if (parentCommunity != null) {
            var parentTitle = client.store().findChatMetadata(parentCommunity)
                    .filter(GroupMetadata.class::isInstance)
                    .map(GroupMetadata.class::cast)
                    .map(GroupMetadata::subject)
                    .orElse(null);
            if (parentTitle != null && !parentTitle.isEmpty()) {
                return "Group in \"" + parentTitle + "\"";
            }
        }
        return GROUP_INVITE_DEFAULT_DESCRIPTION;
    }
}
