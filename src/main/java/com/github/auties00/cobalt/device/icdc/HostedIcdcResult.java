package com.github.auties00.cobalt.device.icdc;

/**
 * Result of processing hosted ICDC metadata inline during message handling.
 *
 * <p>Contains flags indicating whether a hosted business encryption mismatch
 * was detected and whether the sender or recipient account type is hosted.
 * These flags influence how the message is further processed and displayed.
 *
 * @implNote WAWebIcdcHandlerApi.handleHostedIcdcMetadataInline: returns an object
 * with {@code hostedBizEncMismatch} and {@code senderOrRecipientAccountTypeHosted} fields.
 * @param hostedBizEncMismatch              {@code true} if there is a mismatch between the local
 *                                          ADV account type and the incoming HOSTED type, indicating
 *                                          a device list needs to be refreshed
 * @param senderOrRecipientAccountTypeHosted {@code true} if the sender or recipient account
 *                                          type in the message metadata is HOSTED
 */
public record HostedIcdcResult(
        boolean hostedBizEncMismatch,
        boolean senderOrRecipientAccountTypeHosted
) {
    /**
     * Default result indicating no hosted involvement.
     *
     * @implNote WAWebIcdcHandlerApi.handleHostedIcdcMetadataInline: the default return value
     * {@code {hostedBizEncMismatch: false, senderOrRecipientAccountTypeHosted: false}}
     * is returned when hosted devices are not enabled, the JID is self, or the JID is not a user.
     */
    public static final HostedIcdcResult DEFAULT = new HostedIcdcResult(false, false);
}
