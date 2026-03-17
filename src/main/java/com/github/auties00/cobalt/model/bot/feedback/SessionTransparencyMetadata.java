package com.github.auties00.cobalt.model.bot.feedback;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

/**
 * Metadata describing the transparency disclosure shown to users during
 * an AI bot session.
 *
 * <p>Certain jurisdictions require AI interactions to include safety
 * disclaimers. This metadata carries the {@linkplain #disclaimerText() disclaimer text},
 * an optional {@linkplain #hcaId() human content analyst identifier} for
 * sessions reviewed by humans, and the {@linkplain #sessionTransparencyType() type}
 * of transparency notice being displayed.
 */
@ProtobufMessage(name = "SessionTransparencyMetadata")
public final class SessionTransparencyMetadata {
    /**
     * The disclaimer text shown to the user, for example
     * {@code "Responses are AI-generated. Don't share sensitive info."}.
     */
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String disclaimerText;

    /**
     * The identifier of the human content analyst reviewing this session,
     * for example {@code "hca_12345"}.
     */
    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    String hcaId;

    /**
     * The type of session transparency notice being displayed.
     */
    @ProtobufProperty(index = 3, type = ProtobufType.ENUM)
    SessionTransparencyType sessionTransparencyType;


    /**
     * Constructs a new {@code SessionTransparencyMetadata} with the specified values.
     *
     * @param disclaimerText          the disclaimer text, or {@code null}
     * @param hcaId                   the human content analyst identifier, or {@code null}
     * @param sessionTransparencyType the transparency type, or {@code null}
     */
    SessionTransparencyMetadata(String disclaimerText, String hcaId, SessionTransparencyType sessionTransparencyType) {
        this.disclaimerText = disclaimerText;
        this.hcaId = hcaId;
        this.sessionTransparencyType = sessionTransparencyType;
    }

    /**
     * Returns the disclaimer text shown to the user.
     *
     * @return an {@code Optional} describing the disclaimer text, or an empty
     *         {@code Optional} if not set
     */
    public Optional<String> disclaimerText() {
        return Optional.ofNullable(disclaimerText);
    }

    /**
     * Returns the identifier of the human content analyst reviewing this session.
     *
     * @return an {@code Optional} describing the analyst identifier, or an empty
     *         {@code Optional} if not set
     */
    public Optional<String> hcaId() {
        return Optional.ofNullable(hcaId);
    }

    /**
     * Returns the type of session transparency notice being displayed.
     *
     * @return an {@code Optional} describing the transparency type, or an empty
     *         {@code Optional} if not set
     */
    public Optional<SessionTransparencyType> sessionTransparencyType() {
        return Optional.ofNullable(sessionTransparencyType);
    }

    /**
     * Sets the disclaimer text shown to the user.
     *
     * @param disclaimerText the new disclaimer text, or {@code null}
     */
    public void setDisclaimerText(String disclaimerText) {
        this.disclaimerText = disclaimerText;
    }

    /**
     * Sets the identifier of the human content analyst reviewing this session.
     *
     * @param hcaId the new analyst identifier, or {@code null}
     */
    public void setHcaId(String hcaId) {
        this.hcaId = hcaId;
    }

    /**
     * Sets the type of session transparency notice being displayed.
     *
     * @param sessionTransparencyType the new transparency type, or {@code null}
     */
    public void setSessionTransparencyType(SessionTransparencyType sessionTransparencyType) {
        this.sessionTransparencyType = sessionTransparencyType;
    }
}
