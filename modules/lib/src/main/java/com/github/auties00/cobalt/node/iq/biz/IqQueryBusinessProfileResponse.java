package com.github.auties00.cobalt.node.iq.biz;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.iq.IqOperation;
import com.github.auties00.cobalt.node.smax.util.SmaxBaseServerErrorMixin;
import com.github.auties00.cobalt.node.smax.util.SmaxIqResultResponseMixin;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Sealed family of inbound reply variants the relay produces in response
 * to an {@link IqQueryBusinessProfileRequest}.
 *
 * @apiNote
 * Pattern-match the returned variant to drive merchant-profile
 * rendering: {@link Success} carries one {@link Success.Profile} entry
 * per echoed merchant (with the cart UI, contact details, business
 * hours, linked accounts, direct-connection block, bot prompts and
 * commands all already decoded), {@link ClientError} surfaces a
 * rejected request and {@link ServerError} surfaces a transient
 * internal failure.
 */
@WhatsAppWebModule(moduleName = "WAWebQueryBusinessProfileJob")
public sealed interface IqQueryBusinessProfileResponse extends IqOperation.Response
        permits IqQueryBusinessProfileResponse.Success, IqQueryBusinessProfileResponse.ClientError, IqQueryBusinessProfileResponse.ServerError {

    /**
     * Tries each variant in priority order until one matches.
     *
     * @apiNote
     * Use this entry point on every IQ stanza tagged with the
     * {@code <business_profile/>} payload; the order is
     * {@link Success}, then {@link ClientError}, then
     * {@link ServerError}.
     *
     * @param node    the inbound IQ stanza; never {@code null}
     * @param request the original outbound stanza; never {@code null}
     * @return an {@link Optional} carrying the parsed variant, or empty
     *         when no documented variant matched
     * @throws NullPointerException if either argument is {@code null}
     */
    static Optional<? extends IqQueryBusinessProfileResponse> of(Node node, Node request) {
        Objects.requireNonNull(node, "node cannot be null");
        Objects.requireNonNull(request, "request cannot be null");
        var success = Success.of(node, request);
        if (success.isPresent()) {
            return success;
        }
        var clientError = ClientError.of(node, request);
        if (clientError.isPresent()) {
            return clientError;
        }
        return ServerError.of(node, request);
    }

    /**
     * The {@code Success} variant carrying one {@link Profile} entry
     * per echoed {@code <profile/>} child.
     *
     * @apiNote
     * Use {@link #profiles()} to refresh the
     * business-profile collection; the entries preserve the wire
     * order, which matches the caller-supplied entry order on the
     * {@link IqQueryBusinessProfileRequest}.
     */
    final class Success implements IqQueryBusinessProfileResponse {
        /**
         * One typed business-profile entry fully decoded from a
         * {@code <profile/>} child of the {@code <business_profile/>}
         * payload.
         *
         * @apiNote
         * Use this projection to drive every merchant-rendering
         * surface: the chat opener uses {@link #description()} and
         * {@link #email()}, the merchant directory uses
         * {@link #categories()} and {@link #coverPhoto()}, the cart
         * UI uses {@link #profileOptions()} and
         * {@link #directConnection()}, and the bot interaction
         * surface uses {@link #prompts()} and {@link #commands()}.
         */
        public static final class Profile {
            /**
             * The business JID this entry belongs to.
             */
            private final Jid businessJid;

            /**
             * The version tag echoed by the relay; callers cache
             * profile bodies and re-query with the same tag for
             * conditional fetches.
             */
            private final String tag;

            /**
             * The optional postal address (free text).
             */
            private final String address;

            /**
             * The optional self-description body.
             */
            private final String description;

            /**
             * The optional contact email address.
             */
            private final String email;

            /**
             * The optional latitude of the merchant's pinned location.
             */
            private final Double latitude;

            /**
             * The optional longitude of the merchant's pinned location.
             */
            private final Double longitude;

            /**
             * The list of website URLs (zero, one, or two entries on
             * the wire).
             */
            private final List<String> websites;

            /**
             * The list of category entries (id + display name).
             */
            private final List<Category> categories;

            /**
             * The optional business-hours schedule.
             */
            private final BusinessHours businessHours;

            /**
             * The optional catalog status (e.g. {@code "PENDING"},
             * {@code "APPROVED"}).
             */
            private final String catalogStatus;

            /**
             * The optional profile-options block (commerce experience,
             * cart-enabled flag, shop URL, ...).
             */
            private final ProfileOptions profileOptions;

            /**
             * The optional Facebook page link block.
             */
            private final FacebookPage facebookPage;

            /**
             * The optional Instagram-professional block.
             */
            private final InstagramProfessional instagramProfessional;

            /**
             * Whether the merchant has any linked-account block at all
             * (FB or IG).
             */
            private final boolean profileIsLinked;

            /**
             * The optional direct-connection block (enabled flag and
             * default postcode).
             */
            private final DirectConnection directConnection;

            /**
             * The list of service-area entries (radius + center).
             */
            private final List<ServiceArea> serviceAreas;

            /**
             * The list of offering categories.
             */
            private final List<OfferingCategory> offerings;

            /**
             * The optional cover photo (id + URL).
             */
            private final CoverPhoto coverPhoto;

            /**
             * The optional custom merchant URL.
             */
            private final String customUrl;

            /**
             * The optional bot welcome prompts.
             */
            private final List<Prompt> prompts;

            /**
             * The optional bot commands list.
             */
            private final List<Command> commands;

            /**
             * The optional commands-section description text.
             */
            private final String commandsDescription;

            /**
             * The optional automated-bot type marker.
             */
            private final String automatedType;

            /**
             * The optional welcome-message protocol-mode marker.
             */
            private final String welcomeMessageProtocolMode;

            /**
             * The optional "merchant since" display text.
             */
            private final String memberSinceText;

            /**
             * The optional price-tier id.
             */
            private final String priceTierId;

            /**
             * Whether this profile is an authorised agent (only
             * populated when the gating flag is on).
             */
            private final Boolean isAuthorizedAgent;

            /**
             * The optional parent company name (authorised agent only).
             */
            private final String parentCompanyName;

            /**
             * The optional parent company logo URL (authorised agent
             * only).
             */
            private final String parentCompanyLogoUrl;

            /**
             * The optional OBA phone number (authorised agent only).
             */
            private final String obaPhoneNumber;

            /**
             * Constructs a profile entry.
             *
             * @apiNote
             * Use this constructor only from the response parser; the
             * field-by-field optional shape matches the wire echo so
             * the business-profile collection can render the entry
             * without further normalisation.
             *
             * @param businessJid                 see
             *                                    {@link #businessJid()}
             * @param tag                         see {@link #tag()}
             * @param address                     see {@link #address()}
             * @param description                 see
             *                                    {@link #description()}
             * @param email                       see {@link #email()}
             * @param latitude                    see
             *                                    {@link #latitude()}
             * @param longitude                   see
             *                                    {@link #longitude()}
             * @param websites                    see
             *                                    {@link #websites()}
             * @param categories                  see
             *                                    {@link #categories()}
             * @param businessHours               see
             *                                    {@link #businessHours()}
             * @param catalogStatus               see
             *                                    {@link #catalogStatus()}
             * @param profileOptions              see
             *                                    {@link #profileOptions()}
             * @param facebookPage                see
             *                                    {@link #facebookPage()}
             * @param instagramProfessional       see
             *                                    {@link #instagramProfessional()}
             * @param profileIsLinked             see
             *                                    {@link #profileIsLinked()}
             * @param directConnection            see
             *                                    {@link #directConnection()}
             * @param serviceAreas                see
             *                                    {@link #serviceAreas()}
             * @param offerings                   see
             *                                    {@link #offerings()}
             * @param coverPhoto                  see
             *                                    {@link #coverPhoto()}
             * @param customUrl                   see {@link #customUrl()}
             * @param prompts                     see {@link #prompts()}
             * @param commands                    see {@link #commands()}
             * @param commandsDescription         see
             *                                    {@link #commandsDescription()}
             * @param automatedType               see
             *                                    {@link #automatedType()}
             * @param welcomeMessageProtocolMode  see
             *                                    {@link #welcomeMessageProtocolMode()}
             * @param memberSinceText             see
             *                                    {@link #memberSinceText()}
             * @param priceTierId                 see
             *                                    {@link #priceTierId()}
             * @param isAuthorizedAgent           see
             *                                    {@link #isAuthorizedAgent()}
             * @param parentCompanyName           see
             *                                    {@link #parentCompanyName()}
             * @param parentCompanyLogoUrl        see
             *                                    {@link #parentCompanyLogoUrl()}
             * @param obaPhoneNumber              see
             *                                    {@link #obaPhoneNumber()}
             * @throws NullPointerException if {@code businessJid},
             *                              {@code websites},
             *                              {@code categories},
             *                              {@code serviceAreas},
             *                              {@code offerings},
             *                              {@code prompts}, or
             *                              {@code commands} is
             *                              {@code null}
             */
            public Profile(Jid businessJid,
                           String tag,
                           String address,
                           String description,
                           String email,
                           Double latitude,
                           Double longitude,
                           List<String> websites,
                           List<Category> categories,
                           BusinessHours businessHours,
                           String catalogStatus,
                           ProfileOptions profileOptions,
                           FacebookPage facebookPage,
                           InstagramProfessional instagramProfessional,
                           boolean profileIsLinked,
                           DirectConnection directConnection,
                           List<ServiceArea> serviceAreas,
                           List<OfferingCategory> offerings,
                           CoverPhoto coverPhoto,
                           String customUrl,
                           List<Prompt> prompts,
                           List<Command> commands,
                           String commandsDescription,
                           String automatedType,
                           String welcomeMessageProtocolMode,
                           String memberSinceText,
                           String priceTierId,
                           Boolean isAuthorizedAgent,
                           String parentCompanyName,
                           String parentCompanyLogoUrl,
                           String obaPhoneNumber) {
                this.businessJid = Objects.requireNonNull(businessJid, "businessJid cannot be null");
                this.tag = tag;
                this.address = address;
                this.description = description;
                this.email = email;
                this.latitude = latitude;
                this.longitude = longitude;
                Objects.requireNonNull(websites, "websites cannot be null");
                this.websites = List.copyOf(websites);
                Objects.requireNonNull(categories, "categories cannot be null");
                this.categories = List.copyOf(categories);
                this.businessHours = businessHours;
                this.catalogStatus = catalogStatus;
                this.profileOptions = profileOptions;
                this.facebookPage = facebookPage;
                this.instagramProfessional = instagramProfessional;
                this.profileIsLinked = profileIsLinked;
                this.directConnection = directConnection;
                Objects.requireNonNull(serviceAreas, "serviceAreas cannot be null");
                this.serviceAreas = List.copyOf(serviceAreas);
                Objects.requireNonNull(offerings, "offerings cannot be null");
                this.offerings = List.copyOf(offerings);
                this.coverPhoto = coverPhoto;
                this.customUrl = customUrl;
                Objects.requireNonNull(prompts, "prompts cannot be null");
                this.prompts = List.copyOf(prompts);
                Objects.requireNonNull(commands, "commands cannot be null");
                this.commands = List.copyOf(commands);
                this.commandsDescription = commandsDescription;
                this.automatedType = automatedType;
                this.welcomeMessageProtocolMode = welcomeMessageProtocolMode;
                this.memberSinceText = memberSinceText;
                this.priceTierId = priceTierId;
                this.isAuthorizedAgent = isAuthorizedAgent;
                this.parentCompanyName = parentCompanyName;
                this.parentCompanyLogoUrl = parentCompanyLogoUrl;
                this.obaPhoneNumber = obaPhoneNumber;
            }

            /**
             * Returns the merchant JID.
             *
             * @apiNote
             * Use this getter as the business-profile collection key;
             * the value is taken verbatim from the {@code jid}
             * attribute of the {@code <profile/>} child.
             *
             * @return the JID; never {@code null}
             */
            public Jid businessJid() {
                return businessJid;
            }

            /**
             * Returns the version tag, when supplied.
             *
             * @apiNote
             * Use this getter to cache the profile body and replay
             * the tag on a future conditional fetch; an empty
             * optional means the relay returned the full body
             * unconditionally.
             *
             * @return an {@link Optional} carrying the tag
             */
            public Optional<String> tag() {
                return Optional.ofNullable(tag);
            }

            /**
             * Returns the postal address.
             *
             * @apiNote
             * Use this getter to render the address line of the
             * merchant profile sheet; the relay echoes the
             * merchant-supplied free-text address verbatim.
             *
             * @return an {@link Optional} carrying the address
             */
            public Optional<String> address() {
                return Optional.ofNullable(address);
            }

            /**
             * Returns the self-description body.
             *
             * @apiNote
             * Use this getter to render the merchant description
             * line of the chat opener and merchant directory.
             *
             * @return an {@link Optional} carrying the description
             */
            public Optional<String> description() {
                return Optional.ofNullable(description);
            }

            /**
             * Returns the contact email.
             *
             * @apiNote
             * Use this getter to render the email line of the
             * merchant profile sheet and surface a mailto: link in
             * the chat opener.
             *
             * @return an {@link Optional} carrying the email
             */
            public Optional<String> email() {
                return Optional.ofNullable(email);
            }

            /**
             * Returns the pinned-location latitude.
             *
             * @apiNote
             * Use this getter together with {@link #longitude()} to
             * drop a pin on the merchant location surface.
             *
             * @return an {@link Optional} carrying the latitude
             */
            public Optional<Double> latitude() {
                return Optional.ofNullable(latitude);
            }

            /**
             * Returns the pinned-location longitude.
             *
             * @apiNote
             * Use this getter together with {@link #latitude()} to
             * drop a pin on the merchant location surface.
             *
             * @return an {@link Optional} carrying the longitude
             */
            public Optional<Double> longitude() {
                return Optional.ofNullable(longitude);
            }

            /**
             * Returns the list of website URLs.
             *
             * @apiNote
             * Use this getter to render the website links on the
             * merchant profile sheet; the relay echoes zero, one or
             * two entries in wire order.
             *
             * @return an unmodifiable list; never {@code null}
             */
            public List<String> websites() {
                return websites;
            }

            /**
             * Returns the list of category entries.
             *
             * @apiNote
             * Use this getter to render the category chips on the
             * chat opener and merchant directory; each entry pairs
             * an opaque id with a localised display name.
             *
             * @return an unmodifiable list; never {@code null}
             */
            public List<Category> categories() {
                return categories;
            }

            /**
             * Returns the business-hours schedule.
             *
             * @apiNote
             * Use this getter to render the opening hours block on
             * the merchant profile sheet; an empty optional means
             * the merchant has not configured hours.
             *
             * @return an {@link Optional} carrying the schedule
             */
            public Optional<BusinessHours> businessHours() {
                return Optional.ofNullable(businessHours);
            }

            /**
             * Returns the catalog status string.
             *
             * @apiNote
             * Use this getter to dispatch on the catalog approval
             * state (e.g. {@code "PENDING"}, {@code "APPROVED"});
             * the value is read from the {@code status} attribute
             * of the {@code <catalog_status/>} grandchild.
             *
             * @return an {@link Optional} carrying the status
             */
            public Optional<String> catalogStatus() {
                return Optional.ofNullable(catalogStatus);
            }

            /**
             * Returns the profile-options block.
             *
             * @apiNote
             * Use this getter to read the cart-enabled flag, shop
             * URL and other catalog-related markers in one place;
             * an empty optional means the relay omitted the
             * {@code <profile_options/>} grandchild.
             *
             * @return an {@link Optional} carrying the block
             */
            public Optional<ProfileOptions> profileOptions() {
                return Optional.ofNullable(profileOptions);
            }

            /**
             * Returns the Facebook page link block.
             *
             * @apiNote
             * Use this getter to render the FB-page surface of the
             * linked-accounts panel.
             *
             * @return an {@link Optional} carrying the block
             */
            public Optional<FacebookPage> facebookPage() {
                return Optional.ofNullable(facebookPage);
            }

            /**
             * Returns the Instagram-professional link block.
             *
             * @apiNote
             * Use this getter to render the IG-professional surface
             * of the linked-accounts panel.
             *
             * @return an {@link Optional} carrying the block
             */
            public Optional<InstagramProfessional> instagramProfessional() {
                return Optional.ofNullable(instagramProfessional);
            }

            /**
             * Returns whether the profile carries any linked-account
             * block at all (Facebook or Instagram).
             *
             * @apiNote
             * Use this flag to gate the visibility of the
             * linked-accounts panel before reading the per-platform
             * blocks; a {@code true} value means at least one of
             * {@link #facebookPage()} or
             * {@link #instagramProfessional()} is populated.
             *
             * @return {@code true} when at least one linkage is present
             */
            public boolean profileIsLinked() {
                return profileIsLinked;
            }

            /**
             * Returns the direct-connection block.
             *
             * @apiNote
             * Use this getter to drive the cart-postcode entry
             * surface; an empty optional means the merchant has not
             * enrolled in the buyer-side direct-connection flow.
             *
             * @return an {@link Optional} carrying the block
             */
            public Optional<DirectConnection> directConnection() {
                return Optional.ofNullable(directConnection);
            }

            /**
             * Returns the service-area entries.
             *
             * @apiNote
             * Use this getter to render the merchant's service-area
             * map; each entry pairs a center lat/long with a radius
             * in meters and a free-text description.
             *
             * @return an unmodifiable list; never {@code null}
             */
            public List<ServiceArea> serviceAreas() {
                return serviceAreas;
            }

            /**
             * Returns the offering categories.
             *
             * @apiNote
             * Use this getter to render the merchant's offering
             * grid; each category groups one or more typed
             * {@link Offering} entries.
             *
             * @return an unmodifiable list; never {@code null}
             */
            public List<OfferingCategory> offerings() {
                return offerings;
            }

            /**
             * Returns the cover photo.
             *
             * @apiNote
             * Use this getter to render the banner of the merchant
             * profile sheet; an empty optional means the merchant
             * has not uploaded a cover photo.
             *
             * @return an {@link Optional} carrying the cover photo
             */
            public Optional<CoverPhoto> coverPhoto() {
                return Optional.ofNullable(coverPhoto);
            }

            /**
             * Returns the custom merchant URL.
             *
             * @apiNote
             * Use this getter to render the wa.me-style shareable
             * link on the merchant profile sheet; an empty optional
             * means the merchant has not configured a custom URL.
             *
             * @return an {@link Optional} carrying the URL
             */
            public Optional<String> customUrl() {
                return Optional.ofNullable(customUrl);
            }

            /**
             * Returns the bot welcome prompts.
             *
             * @apiNote
             * Use this getter to render the welcome-prompts surface
             * the bot-enabled merchant uses to seed the conversation;
             * each entry pairs an emoji with a body text.
             *
             * @return an unmodifiable list; never {@code null}
             */
            public List<Prompt> prompts() {
                return prompts;
            }

            /**
             * Returns the bot commands.
             *
             * @apiNote
             * Use this getter to render the slash-command palette
             * the bot-enabled merchant exposes; each entry pairs a
             * command name with a description.
             *
             * @return an unmodifiable list; never {@code null}
             */
            public List<Command> commands() {
                return commands;
            }

            /**
             * Returns the commands-section description.
             *
             * @apiNote
             * Use this getter to render the header text above the
             * slash-command palette.
             *
             * @return an {@link Optional} carrying the description
             */
            public Optional<String> commandsDescription() {
                return Optional.ofNullable(commandsDescription);
            }

            /**
             * Returns the automated-bot type marker.
             *
             * @apiNote
             * Use this getter to dispatch on the bot's automation
             * type marker (consumed by
             * {@code WAWebBotTypes.BizBotAutomatedType.cast});
             * an empty optional means the merchant is not a bot.
             *
             * @return an {@link Optional} carrying the type marker
             */
            public Optional<String> automatedType() {
                return Optional.ofNullable(automatedType);
            }

            /**
             * Returns the welcome-message protocol mode.
             *
             * @apiNote
             * Use this getter to dispatch on the welcome-message
             * protocol mode the merchant has enrolled in; bots use
             * the mode to decide whether to surface the welcome
             * message as a system stanza or a normal chat message.
             *
             * @return an {@link Optional} carrying the mode marker
             */
            public Optional<String> welcomeMessageProtocolMode() {
                return Optional.ofNullable(welcomeMessageProtocolMode);
            }

            /**
             * Returns the "merchant since" display text.
             *
             * @apiNote
             * Use this getter to render the merchant-tenure line
             * on the merchant profile sheet; the relay echoes a
             * pre-localised string.
             *
             * @return an {@link Optional} carrying the text
             */
            public Optional<String> memberSinceText() {
                return Optional.ofNullable(memberSinceText);
            }

            /**
             * Returns the price-tier id.
             *
             * @apiNote
             * Use this getter to render the price-tier symbol on
             * the merchant profile sheet; the id keys into
             * {@code WAWebBizGetPriceTiersQuery.getCachedPriceTierById}
             * to resolve the symbol and description.
             *
             * @return an {@link Optional} carrying the id
             */
            public Optional<String> priceTierId() {
                return Optional.ofNullable(priceTierId);
            }

            /**
             * Returns the authorised-agent flag.
             *
             * @apiNote
             * Use this getter to gate the rendering of the
             * parent-company block; an empty optional means the
             * relay did not include the
             * {@code <authorized_agent/>} grandchild.
             *
             * @return an {@link Optional} carrying the flag
             */
            public Optional<Boolean> isAuthorizedAgent() {
                return Optional.ofNullable(isAuthorizedAgent);
            }

            /**
             * Returns the parent company name.
             *
             * @apiNote
             * Use this getter together with {@link #parentCompanyLogoUrl()}
             * to render the parent-company block; only populated
             * when {@link #isAuthorizedAgent()} resolves to true.
             *
             * @return an {@link Optional} carrying the name
             */
            public Optional<String> parentCompanyName() {
                return Optional.ofNullable(parentCompanyName);
            }

            /**
             * Returns the parent company logo URL.
             *
             * @apiNote
             * Use this getter together with {@link #parentCompanyName()}
             * to render the parent-company block; only populated
             * when {@link #isAuthorizedAgent()} resolves to true.
             *
             * @return an {@link Optional} carrying the URL
             */
            public Optional<String> parentCompanyLogoUrl() {
                return Optional.ofNullable(parentCompanyLogoUrl);
            }

            /**
             * Returns the official-business-account phone number.
             *
             * @apiNote
             * Use this getter to display the OBA disclosure line
             * when {@link #isAuthorizedAgent()} resolves to true;
             * the value is the underlying business's contact
             * number.
             *
             * @return an {@link Optional} carrying the number
             */
            public Optional<String> obaPhoneNumber() {
                return Optional.ofNullable(obaPhoneNumber);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public boolean equals(Object obj) {
                if (obj == this) {
                    return true;
                }
                if (obj == null || obj.getClass() != this.getClass()) {
                    return false;
                }
                var that = (Profile) obj;
                return this.profileIsLinked == that.profileIsLinked
                        && Objects.equals(this.businessJid, that.businessJid)
                        && Objects.equals(this.tag, that.tag)
                        && Objects.equals(this.address, that.address)
                        && Objects.equals(this.description, that.description)
                        && Objects.equals(this.email, that.email)
                        && Objects.equals(this.latitude, that.latitude)
                        && Objects.equals(this.longitude, that.longitude)
                        && Objects.equals(this.websites, that.websites)
                        && Objects.equals(this.categories, that.categories)
                        && Objects.equals(this.businessHours, that.businessHours)
                        && Objects.equals(this.catalogStatus, that.catalogStatus)
                        && Objects.equals(this.profileOptions, that.profileOptions)
                        && Objects.equals(this.facebookPage, that.facebookPage)
                        && Objects.equals(this.instagramProfessional, that.instagramProfessional)
                        && Objects.equals(this.directConnection, that.directConnection)
                        && Objects.equals(this.serviceAreas, that.serviceAreas)
                        && Objects.equals(this.offerings, that.offerings)
                        && Objects.equals(this.coverPhoto, that.coverPhoto)
                        && Objects.equals(this.customUrl, that.customUrl)
                        && Objects.equals(this.prompts, that.prompts)
                        && Objects.equals(this.commands, that.commands)
                        && Objects.equals(this.commandsDescription, that.commandsDescription)
                        && Objects.equals(this.automatedType, that.automatedType)
                        && Objects.equals(this.welcomeMessageProtocolMode, that.welcomeMessageProtocolMode)
                        && Objects.equals(this.memberSinceText, that.memberSinceText)
                        && Objects.equals(this.priceTierId, that.priceTierId)
                        && Objects.equals(this.isAuthorizedAgent, that.isAuthorizedAgent)
                        && Objects.equals(this.parentCompanyName, that.parentCompanyName)
                        && Objects.equals(this.parentCompanyLogoUrl, that.parentCompanyLogoUrl)
                        && Objects.equals(this.obaPhoneNumber, that.obaPhoneNumber);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public int hashCode() {
                var h = Objects.hash(businessJid, tag, address, description, email,
                        latitude, longitude, websites, categories, businessHours,
                        catalogStatus, profileOptions, facebookPage, instagramProfessional,
                        profileIsLinked, directConnection, serviceAreas, offerings,
                        coverPhoto, customUrl);
                return 31 * h + Objects.hash(prompts, commands, commandsDescription,
                        automatedType, welcomeMessageProtocolMode, memberSinceText,
                        priceTierId, isAuthorizedAgent, parentCompanyName,
                        parentCompanyLogoUrl, obaPhoneNumber);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String toString() {
                return "IqQueryBusinessProfileResponse.Success.Profile[businessJid=" + businessJid
                        + ", tag=" + tag + ", address=" + address + ", description=" + description
                        + ", email=" + email + ", websites=" + websites
                        + ", categories=" + categories + ']';
            }
        }

        /**
         * One business category entry: opaque id plus localised
         * display name.
         *
         * @apiNote
         * Use this entry to render the category chips on the chat
         * opener and merchant directory; the same id is also used as
         * the lookup key against the
         * {@link IqQueryBusinessCategoriesResponse} cache when the
         * merchant directory needs the parent / sibling chain.
         */
        public static final class Category {
            /**
             * The opaque category id.
             */
            private final String id;

            /**
             * The localised display name.
             */
            private final String localizedDisplayName;

            /**
             * Constructs a category.
             *
             * @apiNote
             * Use this constructor only from the response parser.
             *
             * @param id                   the id; never {@code null}
             * @param localizedDisplayName the display name; never
             *                             {@code null}
             * @throws NullPointerException if either argument is
             *                              {@code null}
             */
            public Category(String id, String localizedDisplayName) {
                this.id = Objects.requireNonNull(id, "id cannot be null");
                this.localizedDisplayName = Objects.requireNonNull(
                        localizedDisplayName, "localizedDisplayName cannot be null");
            }

            /**
             * Returns the opaque category id.
             *
             * @apiNote
             * Use this getter to dispatch on the category id; the
             * same id keys into the business-categories cache.
             *
             * @return the id; never {@code null}
             */
            public String id() {
                return id;
            }

            /**
             * Returns the localised display name.
             *
             * @apiNote
             * Use this getter to render the category chip label;
             * the relay echoes a pre-localised string.
             *
             * @return the display name; never {@code null}
             */
            public String localizedDisplayName() {
                return localizedDisplayName;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public boolean equals(Object obj) {
                if (obj == this) {
                    return true;
                }
                if (obj == null || obj.getClass() != this.getClass()) {
                    return false;
                }
                var that = (Category) obj;
                return Objects.equals(this.id, that.id)
                        && Objects.equals(this.localizedDisplayName, that.localizedDisplayName);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public int hashCode() {
                return Objects.hash(id, localizedDisplayName);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String toString() {
                return "IqQueryBusinessProfileResponse.Success.Category[id=" + id
                        + ", localizedDisplayName=" + localizedDisplayName + ']';
            }
        }

        /**
         * The merchant's business-hours schedule.
         *
         * @apiNote
         * Use this block to render the opening-hours surface on the
         * merchant profile sheet; the timezone is the merchant's
         * configured IANA zone and the config list carries one
         * window per (day of week, mode) tuple.
         */
        public static final class BusinessHours {
            /**
             * The optional IANA timezone identifier.
             */
            private final String timezone;

            /**
             * The per-(day-of-week) opening windows.
             */
            private final List<BusinessHoursConfig> config;

            /**
             * Constructs a schedule.
             *
             * @apiNote
             * Use this constructor only from the response parser.
             *
             * @param timezone the optional timezone; may be {@code null}
             * @param config   the per-day windows; never {@code null}
             * @throws NullPointerException if {@code config} is
             *                              {@code null}
             */
            public BusinessHours(String timezone, List<BusinessHoursConfig> config) {
                this.timezone = timezone;
                Objects.requireNonNull(config, "config cannot be null");
                this.config = List.copyOf(config);
            }

            /**
             * Returns the IANA timezone identifier.
             *
             * @apiNote
             * Use this getter to render the opening-hours surface
             * in the merchant's local time; an empty optional means
             * the relay omitted the timezone attribute.
             *
             * @return an {@link Optional} carrying the timezone
             */
            public Optional<String> timezone() {
                return Optional.ofNullable(timezone);
            }

            /**
             * Returns the per-day opening windows.
             *
             * @apiNote
             * Use this getter to render the daily opening-hours
             * rows; the list may contain multiple entries for the
             * same day when the merchant configures multiple
             * windows (e.g. lunch break).
             *
             * @return an unmodifiable list; never {@code null}
             */
            public List<BusinessHoursConfig> config() {
                return config;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public boolean equals(Object obj) {
                if (obj == this) {
                    return true;
                }
                if (obj == null || obj.getClass() != this.getClass()) {
                    return false;
                }
                var that = (BusinessHours) obj;
                return Objects.equals(this.timezone, that.timezone)
                        && Objects.equals(this.config, that.config);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public int hashCode() {
                return Objects.hash(timezone, config);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String toString() {
                return "IqQueryBusinessProfileResponse.Success.BusinessHours[timezone="
                        + timezone + ", config=" + config + ']';
            }
        }

        /**
         * One business-hours window on a {@link BusinessHours}:
         * day of week, mode, optional open and close times.
         *
         * @apiNote
         * Use this row to drive one line of the opening-hours
         * surface; the mode dispatches the rendering (specific
         * hours, appointment only, closed).
         */
        public static final class BusinessHoursConfig {
            /**
             * The day of week (e.g. {@code "mon"}).
             */
            private final String dayOfWeek;

            /**
             * The mode (e.g. {@code "open_specific_hours"},
             * {@code "appointment_only"}, {@code "closed"}).
             */
            private final String mode;

            /**
             * The opening time in minutes since midnight.
             */
            private final int openTime;

            /**
             * The closing time in minutes since midnight.
             */
            private final int closeTime;

            /**
             * Constructs a window.
             *
             * @apiNote
             * Use this constructor only from the response parser.
             *
             * @param dayOfWeek the day of week; never {@code null}
             * @param mode      the mode; never {@code null}
             * @param openTime  the opening time
             * @param closeTime the closing time
             * @throws NullPointerException if either string is
             *                              {@code null}
             */
            public BusinessHoursConfig(String dayOfWeek, String mode, int openTime, int closeTime) {
                this.dayOfWeek = Objects.requireNonNull(dayOfWeek, "dayOfWeek cannot be null");
                this.mode = Objects.requireNonNull(mode, "mode cannot be null");
                this.openTime = openTime;
                this.closeTime = closeTime;
            }

            /**
             * Returns the day of week.
             *
             * @apiNote
             * Use this getter as the row label; the relay echoes a
             * short day name (e.g. {@code "mon"}, {@code "tue"}).
             *
             * @return the day of week; never {@code null}
             */
            public String dayOfWeek() {
                return dayOfWeek;
            }

            /**
             * Returns the rendering mode.
             *
             * @apiNote
             * Use this getter to dispatch on the row mode (e.g.
             * {@code "open_specific_hours"} gates rendering of the
             * open / close times; {@code "appointment_only"} and
             * {@code "closed"} short-circuit the row).
             *
             * @return the mode; never {@code null}
             */
            public String mode() {
                return mode;
            }

            /**
             * Returns the opening time in minutes since midnight.
             *
             * @apiNote
             * Use this getter together with {@link #closeTime()} to
             * render the open window; meaningful only when
             * {@link #mode()} is {@code "open_specific_hours"}.
             *
             * @return the opening time
             */
            public int openTime() {
                return openTime;
            }

            /**
             * Returns the closing time in minutes since midnight.
             *
             * @apiNote
             * Use this getter together with {@link #openTime()} to
             * render the open window; meaningful only when
             * {@link #mode()} is {@code "open_specific_hours"}.
             *
             * @return the closing time
             */
            public int closeTime() {
                return closeTime;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public boolean equals(Object obj) {
                if (obj == this) {
                    return true;
                }
                if (obj == null || obj.getClass() != this.getClass()) {
                    return false;
                }
                var that = (BusinessHoursConfig) obj;
                return this.openTime == that.openTime
                        && this.closeTime == that.closeTime
                        && Objects.equals(this.dayOfWeek, that.dayOfWeek)
                        && Objects.equals(this.mode, that.mode);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public int hashCode() {
                return Objects.hash(dayOfWeek, mode, openTime, closeTime);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String toString() {
                return "IqQueryBusinessProfileResponse.Success.BusinessHoursConfig[dayOfWeek="
                        + dayOfWeek + ", mode=" + mode + ", openTime=" + openTime
                        + ", closeTime=" + closeTime + ']';
            }
        }

        /**
         * The catalog and cart options block on a {@link Profile}:
         * commerce experience marker, cart-enabled flag, shop URL,
         * commerce-manager URL, banned and direct-connection markers,
         * profile-edit lock.
         *
         * @apiNote
         * Use this block to drive the catalog-grid affordance,
         * commerce-manager deep link and ban-state badge on the
         * merchant profile sheet; every field is optional and an
         * empty optional means the relay omitted the corresponding
         * grandchild.
         */
        public static final class ProfileOptions {
            /**
             * The commerce experience marker (e.g. {@code "NONE"},
             * {@code "PURPLE_MOON"}).
             */
            private final String commerceExperience;

            /**
             * Whether the cart is enabled.
             */
            private final Boolean cartEnabled;

            /**
             * The optional shop URL.
             */
            private final String shopUrl;

            /**
             * The optional commerce-manager URL.
             */
            private final String commerceManagerUrl;

            /**
             * Whether the merchant has been banned.
             */
            private final Boolean banned;

            /**
             * Whether direct-connection is enabled.
             */
            private final Boolean directConnection;

            /**
             * Whether profile editing is disabled.
             */
            private final Boolean profileEditDisabled;

            /**
             * Constructs an options block.
             *
             * @apiNote
             * Use this constructor only from the response parser;
             * each field is independently optional on the wire.
             *
             * @param commerceExperience  see
             *                            {@link #commerceExperience()}
             * @param cartEnabled         see {@link #cartEnabled()}
             * @param shopUrl             see {@link #shopUrl()}
             * @param commerceManagerUrl  see
             *                            {@link #commerceManagerUrl()}
             * @param banned              see {@link #banned()}
             * @param directConnection    see
             *                            {@link #directConnection()}
             * @param profileEditDisabled see
             *                            {@link #profileEditDisabled()}
             */
            public ProfileOptions(String commerceExperience,
                                  Boolean cartEnabled,
                                  String shopUrl,
                                  String commerceManagerUrl,
                                  Boolean banned,
                                  Boolean directConnection,
                                  Boolean profileEditDisabled) {
                this.commerceExperience = commerceExperience;
                this.cartEnabled = cartEnabled;
                this.shopUrl = shopUrl;
                this.commerceManagerUrl = commerceManagerUrl;
                this.banned = banned;
                this.directConnection = directConnection;
                this.profileEditDisabled = profileEditDisabled;
            }

            /**
             * Returns the commerce experience marker.
             *
             * @apiNote
             * Use this getter to dispatch on the commerce-experience
             * track the merchant is enrolled in (e.g. {@code "NONE"},
             * {@code "PURPLE_MOON"}); the value gates which catalog
             * surface the cart UI surfaces.
             *
             * @return an {@link Optional} carrying the marker
             */
            public Optional<String> commerceExperience() {
                return Optional.ofNullable(commerceExperience);
            }

            /**
             * Returns the cart-enabled flag.
             *
             * @apiNote
             * Use this getter to gate the "add to cart" affordance
             * on the catalog card; an empty optional means the
             * relay omitted the flag and the caller should default
             * to disabled.
             *
             * @return an {@link Optional} carrying the flag
             */
            public Optional<Boolean> cartEnabled() {
                return Optional.ofNullable(cartEnabled);
            }

            /**
             * Returns the shop URL.
             *
             * @apiNote
             * Use this getter to drive the "open shop" CTA on the
             * merchant profile sheet.
             *
             * @return an {@link Optional} carrying the URL
             */
            public Optional<String> shopUrl() {
                return Optional.ofNullable(shopUrl);
            }

            /**
             * Returns the commerce-manager URL.
             *
             * @apiNote
             * Use this getter to drive the deep link to Meta's
             * commerce-manager surface when the calling user owns
             * the merchant.
             *
             * @return an {@link Optional} carrying the URL
             */
            public Optional<String> commerceManagerUrl() {
                return Optional.ofNullable(commerceManagerUrl);
            }

            /**
             * Returns the banned flag.
             *
             * @apiNote
             * Use this getter to render the ban-state badge on the
             * merchant profile sheet; an empty optional means the
             * relay omitted the flag.
             *
             * @return an {@link Optional} carrying the flag
             */
            public Optional<Boolean> banned() {
                return Optional.ofNullable(banned);
            }

            /**
             * Returns the direct-connection flag.
             *
             * @apiNote
             * Use this getter to gate the cart-postcode entry
             * surface; an empty optional means the relay omitted
             * the flag and the caller should default to disabled.
             *
             * @return an {@link Optional} carrying the flag
             */
            public Optional<Boolean> directConnection() {
                return Optional.ofNullable(directConnection);
            }

            /**
             * Returns the profile-edit-disabled flag.
             *
             * @apiNote
             * Use this getter to gate write affordances on the
             * merchant-profile edit surface; the relay sets the
             * flag when the merchant has lost edit capability.
             *
             * @return an {@link Optional} carrying the flag
             */
            public Optional<Boolean> profileEditDisabled() {
                return Optional.ofNullable(profileEditDisabled);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public boolean equals(Object obj) {
                if (obj == this) {
                    return true;
                }
                if (obj == null || obj.getClass() != this.getClass()) {
                    return false;
                }
                var that = (ProfileOptions) obj;
                return Objects.equals(this.commerceExperience, that.commerceExperience)
                        && Objects.equals(this.cartEnabled, that.cartEnabled)
                        && Objects.equals(this.shopUrl, that.shopUrl)
                        && Objects.equals(this.commerceManagerUrl, that.commerceManagerUrl)
                        && Objects.equals(this.banned, that.banned)
                        && Objects.equals(this.directConnection, that.directConnection)
                        && Objects.equals(this.profileEditDisabled, that.profileEditDisabled);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public int hashCode() {
                return Objects.hash(commerceExperience, cartEnabled, shopUrl,
                        commerceManagerUrl, banned, directConnection, profileEditDisabled);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String toString() {
                return "IqQueryBusinessProfileResponse.Success.ProfileOptions[commerceExperience="
                        + commerceExperience + ", cartEnabled=" + cartEnabled
                        + ", shopUrl=" + shopUrl + ']';
            }
        }

        /**
         * The Facebook page link block on a {@link Profile}.
         *
         * @apiNote
         * Use this block to render the FB-page row of the
         * linked-accounts panel; every field is optional and an
         * empty optional means the relay omitted the corresponding
         * grandchild.
         */
        public static final class FacebookPage {
            /**
             * The optional FB page id.
             */
            private final String id;

            /**
             * The optional display name.
             */
            private final String displayName;

            /**
             * The optional page-likes count.
             */
            private final Integer likes;

            /**
             * Constructs a block.
             *
             * @apiNote
             * Use this constructor only from the response parser.
             *
             * @param id          the id; may be {@code null}
             * @param displayName the display name; may be {@code null}
             * @param likes       the likes count; may be {@code null}
             */
            public FacebookPage(String id, String displayName, Integer likes) {
                this.id = id;
                this.displayName = displayName;
                this.likes = likes;
            }

            /**
             * Returns the FB page id.
             *
             * @apiNote
             * Use this getter to drive the deep link from the
             * linked-accounts panel into the Facebook app.
             *
             * @return an {@link Optional} carrying the id
             */
            public Optional<String> id() {
                return Optional.ofNullable(id);
            }

            /**
             * Returns the display name.
             *
             * @apiNote
             * Use this getter to render the FB-page label of the
             * linked-accounts panel.
             *
             * @return an {@link Optional} carrying the name
             */
            public Optional<String> displayName() {
                return Optional.ofNullable(displayName);
            }

            /**
             * Returns the page-likes count.
             *
             * @apiNote
             * Use this getter to render the like-count badge of
             * the linked-accounts panel.
             *
             * @return an {@link Optional} carrying the count
             */
            public Optional<Integer> likes() {
                return Optional.ofNullable(likes);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public boolean equals(Object obj) {
                if (obj == this) {
                    return true;
                }
                if (obj == null || obj.getClass() != this.getClass()) {
                    return false;
                }
                var that = (FacebookPage) obj;
                return Objects.equals(this.id, that.id)
                        && Objects.equals(this.displayName, that.displayName)
                        && Objects.equals(this.likes, that.likes);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public int hashCode() {
                return Objects.hash(id, displayName, likes);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String toString() {
                return "IqQueryBusinessProfileResponse.Success.FacebookPage[id=" + id
                        + ", displayName=" + displayName + ", likes=" + likes + ']';
            }
        }

        /**
         * The Instagram-professional link block on a {@link Profile}.
         *
         * @apiNote
         * Use this block to render the IG-professional row of the
         * linked-accounts panel.
         */
        public static final class InstagramProfessional {
            /**
             * The optional IG handle.
             */
            private final String igHandle;

            /**
             * The optional follower count.
             */
            private final Integer followers;

            /**
             * Constructs a block.
             *
             * @apiNote
             * Use this constructor only from the response parser.
             *
             * @param igHandle  the IG handle; may be {@code null}
             * @param followers the follower count; may be {@code null}
             */
            public InstagramProfessional(String igHandle, Integer followers) {
                this.igHandle = igHandle;
                this.followers = followers;
            }

            /**
             * Returns the IG handle.
             *
             * @apiNote
             * Use this getter to render the IG-professional label
             * of the linked-accounts panel and drive the deep link
             * into the Instagram app.
             *
             * @return an {@link Optional} carrying the handle
             */
            public Optional<String> igHandle() {
                return Optional.ofNullable(igHandle);
            }

            /**
             * Returns the follower count.
             *
             * @apiNote
             * Use this getter to render the follower-count badge of
             * the linked-accounts panel.
             *
             * @return an {@link Optional} carrying the count
             */
            public Optional<Integer> followers() {
                return Optional.ofNullable(followers);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public boolean equals(Object obj) {
                if (obj == this) {
                    return true;
                }
                if (obj == null || obj.getClass() != this.getClass()) {
                    return false;
                }
                var that = (InstagramProfessional) obj;
                return Objects.equals(this.igHandle, that.igHandle)
                        && Objects.equals(this.followers, that.followers);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public int hashCode() {
                return Objects.hash(igHandle, followers);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String toString() {
                return "IqQueryBusinessProfileResponse.Success.InstagramProfessional[igHandle="
                        + igHandle + ", followers=" + followers + ']';
            }
        }

        /**
         * The direct-connection block on a {@link Profile}: enabled
         * flag plus the optional default-postcode echo.
         *
         * @apiNote
         * Use this block to drive the cart-postcode entry surface;
         * the default postcode is the address the buyer previously
         * verified against the merchant, so the surface can
         * pre-populate the chip.
         */
        public static final class DirectConnection {
            /**
             * Whether direct-connection is enabled.
             */
            private final boolean enabled;

            /**
             * The optional default-postcode echo.
             */
            private final DefaultPostcode defaultPostcode;

            /**
             * Constructs a block.
             *
             * @apiNote
             * Use this constructor only from the response parser.
             *
             * @param enabled         the enabled flag
             * @param defaultPostcode the default postcode; may be
             *                        {@code null}
             */
            public DirectConnection(boolean enabled, DefaultPostcode defaultPostcode) {
                this.enabled = enabled;
                this.defaultPostcode = defaultPostcode;
            }

            /**
             * Returns the enabled flag.
             *
             * @apiNote
             * Use this getter to gate the cart-postcode entry
             * surface; a {@code true} value unlocks the surface for
             * the merchant.
             *
             * @return {@code true} when direct-connection is enabled
             */
            public boolean enabled() {
                return enabled;
            }

            /**
             * Returns the default-postcode echo.
             *
             * @apiNote
             * Use this getter to pre-populate the postcode chip on
             * the cart-postcode entry surface; an empty optional
             * means the buyer has not yet verified an address with
             * the merchant.
             *
             * @return an {@link Optional} carrying the block
             */
            public Optional<DefaultPostcode> defaultPostcode() {
                return Optional.ofNullable(defaultPostcode);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public boolean equals(Object obj) {
                if (obj == this) {
                    return true;
                }
                if (obj == null || obj.getClass() != this.getClass()) {
                    return false;
                }
                var that = (DirectConnection) obj;
                return this.enabled == that.enabled
                        && Objects.equals(this.defaultPostcode, that.defaultPostcode);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public int hashCode() {
                return Objects.hash(enabled, defaultPostcode);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String toString() {
                return "IqQueryBusinessProfileResponse.Success.DirectConnection[enabled="
                        + enabled + ", defaultPostcode=" + defaultPostcode + ']';
            }
        }

        /**
         * The default-postcode block on a {@link DirectConnection}:
         * postcode value plus the location-name label.
         *
         * @apiNote
         * Use this block to pre-populate the postcode chip on the
         * cart-postcode entry surface; the location-name label is
         * the resolved address the UI surfaces above the chip.
         */
        public static final class DefaultPostcode {
            /**
             * The postcode value.
             */
            private final String code;

            /**
             * The location-name label resolved from the postcode.
             */
            private final String locationName;

            /**
             * Constructs a block.
             *
             * @apiNote
             * Use this constructor only from the response parser.
             *
             * @param code         the postcode; never {@code null}
             * @param locationName the location label; never {@code null}
             * @throws NullPointerException if either argument is
             *                              {@code null}
             */
            public DefaultPostcode(String code, String locationName) {
                this.code = Objects.requireNonNull(code, "code cannot be null");
                this.locationName = Objects.requireNonNull(locationName, "locationName cannot be null");
            }

            /**
             * Returns the postcode value.
             *
             * @apiNote
             * Use this getter to pre-populate the postcode chip.
             *
             * @return the postcode; never {@code null}
             */
            public String code() {
                return code;
            }

            /**
             * Returns the location-name label.
             *
             * @apiNote
             * Use this getter to render the resolved address line
             * above the postcode chip.
             *
             * @return the label; never {@code null}
             */
            public String locationName() {
                return locationName;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public boolean equals(Object obj) {
                if (obj == this) {
                    return true;
                }
                if (obj == null || obj.getClass() != this.getClass()) {
                    return false;
                }
                var that = (DefaultPostcode) obj;
                return Objects.equals(this.code, that.code)
                        && Objects.equals(this.locationName, that.locationName);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public int hashCode() {
                return Objects.hash(code, locationName);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String toString() {
                return "IqQueryBusinessProfileResponse.Success.DefaultPostcode[code=" + code
                        + ", locationName=" + locationName + ']';
            }
        }

        /**
         * One service-area entry on a {@link Profile}: radius in
         * meters around a fixed lat/long center plus a free-text
         * description.
         *
         * @apiNote
         * Use this entry to render one circle on the merchant's
         * service-area map.
         */
        public static final class ServiceArea {
            /**
             * The service radius in meters.
             */
            private final double radius;

            /**
             * The center latitude.
             */
            private final double latitude;

            /**
             * The center longitude.
             */
            private final double longitude;

            /**
             * The free-text description.
             */
            private final String areaDescription;

            /**
             * Constructs an entry.
             *
             * @apiNote
             * Use this constructor only from the response parser.
             *
             * @param radius          the radius in meters
             * @param latitude        the center latitude
             * @param longitude       the center longitude
             * @param areaDescription the free-text description;
             *                        never {@code null}
             * @throws NullPointerException if {@code areaDescription} is
             *                              {@code null}
             */
            public ServiceArea(double radius, double latitude, double longitude, String areaDescription) {
                this.radius = radius;
                this.latitude = latitude;
                this.longitude = longitude;
                this.areaDescription = Objects.requireNonNull(
                        areaDescription, "areaDescription cannot be null");
            }

            /**
             * Returns the service radius in meters.
             *
             * @apiNote
             * Use this getter to size the rendered circle on the
             * service-area map.
             *
             * @return the radius
             */
            public double radius() {
                return radius;
            }

            /**
             * Returns the center latitude.
             *
             * @apiNote
             * Use this getter together with {@link #longitude()} to
             * place the rendered circle on the service-area map.
             *
             * @return the latitude
             */
            public double latitude() {
                return latitude;
            }

            /**
             * Returns the center longitude.
             *
             * @apiNote
             * Use this getter together with {@link #latitude()} to
             * place the rendered circle on the service-area map.
             *
             * @return the longitude
             */
            public double longitude() {
                return longitude;
            }

            /**
             * Returns the free-text description.
             *
             * @apiNote
             * Use this getter to render the merchant-supplied label
             * next to the rendered circle (e.g.
             * {@code "Greater Mumbai"}).
             *
             * @return the description; never {@code null}
             */
            public String areaDescription() {
                return areaDescription;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public boolean equals(Object obj) {
                if (obj == this) {
                    return true;
                }
                if (obj == null || obj.getClass() != this.getClass()) {
                    return false;
                }
                var that = (ServiceArea) obj;
                return Double.compare(this.radius, that.radius) == 0
                        && Double.compare(this.latitude, that.latitude) == 0
                        && Double.compare(this.longitude, that.longitude) == 0
                        && Objects.equals(this.areaDescription, that.areaDescription);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public int hashCode() {
                return Objects.hash(radius, latitude, longitude, areaDescription);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String toString() {
                return "IqQueryBusinessProfileResponse.Success.ServiceArea[radius=" + radius
                        + ", latitude=" + latitude + ", longitude=" + longitude
                        + ", areaDescription=" + areaDescription + ']';
            }
        }

        /**
         * One offering category on a {@link Profile}: id, name and
         * list of typed offering entries.
         *
         * @apiNote
         * Use this category to render one section of the merchant's
         * offering grid; each section groups one or more
         * {@link Offering} entries.
         */
        public static final class OfferingCategory {
            /**
             * The category id.
             */
            private final String id;

            /**
             * The category name.
             */
            private final String name;

            /**
             * The list of offerings.
             */
            private final List<Offering> offerings;

            /**
             * Constructs a category.
             *
             * @apiNote
             * Use this constructor only from the response parser.
             *
             * @param id        the id; never {@code null}
             * @param name      the name; never {@code null}
             * @param offerings the offerings; never {@code null}
             * @throws NullPointerException if any argument is
             *                              {@code null}
             */
            public OfferingCategory(String id, String name, List<Offering> offerings) {
                this.id = Objects.requireNonNull(id, "id cannot be null");
                this.name = Objects.requireNonNull(name, "name cannot be null");
                Objects.requireNonNull(offerings, "offerings cannot be null");
                this.offerings = List.copyOf(offerings);
            }

            /**
             * Returns the category id.
             *
             * @apiNote
             * Use this getter as a stable key for the offering-grid
             * section.
             *
             * @return the id; never {@code null}
             */
            public String id() {
                return id;
            }

            /**
             * Returns the category name.
             *
             * @apiNote
             * Use this getter as the offering-grid section header.
             *
             * @return the name; never {@code null}
             */
            public String name() {
                return name;
            }

            /**
             * Returns the typed offerings.
             *
             * @apiNote
             * Use this getter to render the offering-grid rows
             * inside the section.
             *
             * @return an unmodifiable list; never {@code null}
             */
            public List<Offering> offerings() {
                return offerings;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public boolean equals(Object obj) {
                if (obj == this) {
                    return true;
                }
                if (obj == null || obj.getClass() != this.getClass()) {
                    return false;
                }
                var that = (OfferingCategory) obj;
                return Objects.equals(this.id, that.id)
                        && Objects.equals(this.name, that.name)
                        && Objects.equals(this.offerings, that.offerings);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public int hashCode() {
                return Objects.hash(id, name, offerings);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String toString() {
                return "IqQueryBusinessProfileResponse.Success.OfferingCategory[id=" + id
                        + ", name=" + name + ", offerings=" + offerings + ']';
            }
        }

        /**
         * One typed offering entry on an {@link OfferingCategory}.
         *
         * @apiNote
         * Use this entry to render one row of the offering grid;
         * the offered flag dispatches between an active row and a
         * dimmed "not currently offered" row.
         */
        public static final class Offering {
            /**
             * The offering id.
             */
            private final String id;

            /**
             * The localised display name.
             */
            private final String localizedDisplayName;

            /**
             * Whether the offering is currently offered.
             */
            private final boolean offered;

            /**
             * Constructs an offering.
             *
             * @apiNote
             * Use this constructor only from the response parser.
             *
             * @param id                   the id; never {@code null}
             * @param localizedDisplayName the name; never {@code null}
             * @param offered              the offered flag
             * @throws NullPointerException if either string is
             *                              {@code null}
             */
            public Offering(String id, String localizedDisplayName, boolean offered) {
                this.id = Objects.requireNonNull(id, "id cannot be null");
                this.localizedDisplayName = Objects.requireNonNull(
                        localizedDisplayName, "localizedDisplayName cannot be null");
                this.offered = offered;
            }

            /**
             * Returns the offering id.
             *
             * @apiNote
             * Use this getter as a stable key for the offering-grid
             * row.
             *
             * @return the id; never {@code null}
             */
            public String id() {
                return id;
            }

            /**
             * Returns the localised display name.
             *
             * @apiNote
             * Use this getter to render the row label; the relay
             * echoes a pre-localised string.
             *
             * @return the name; never {@code null}
             */
            public String localizedDisplayName() {
                return localizedDisplayName;
            }

            /**
             * Returns the offered flag.
             *
             * @apiNote
             * Use this getter to dispatch between an active row and
             * a dimmed "not currently offered" row.
             *
             * @return {@code true} when currently offered
             */
            public boolean offered() {
                return offered;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public boolean equals(Object obj) {
                if (obj == this) {
                    return true;
                }
                if (obj == null || obj.getClass() != this.getClass()) {
                    return false;
                }
                var that = (Offering) obj;
                return this.offered == that.offered
                        && Objects.equals(this.id, that.id)
                        && Objects.equals(this.localizedDisplayName, that.localizedDisplayName);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public int hashCode() {
                return Objects.hash(id, localizedDisplayName, offered);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String toString() {
                return "IqQueryBusinessProfileResponse.Success.Offering[id=" + id
                        + ", localizedDisplayName=" + localizedDisplayName
                        + ", offered=" + offered + ']';
            }
        }

        /**
         * The cover photo block on a {@link Profile}: id plus URL.
         *
         * @apiNote
         * Use this block to render the banner of the merchant
         * profile sheet; the URL is signed and short-lived.
         */
        public static final class CoverPhoto {
            /**
             * The cover photo id.
             */
            private final String id;

            /**
             * The signed cover photo URL.
             */
            private final String url;

            /**
             * Constructs a block.
             *
             * @apiNote
             * Use this constructor only from the response parser.
             *
             * @param id  the id; never {@code null}
             * @param url the URL; never {@code null}
             * @throws NullPointerException if either argument is
             *                              {@code null}
             */
            public CoverPhoto(String id, String url) {
                this.id = Objects.requireNonNull(id, "id cannot be null");
                this.url = Objects.requireNonNull(url, "url cannot be null");
            }

            /**
             * Returns the cover photo id.
             *
             * @apiNote
             * Use this getter as the upload-artefact id when
             * deleting or refreshing the cover photo via
             * {@link IqDeleteCoverPhotoRequest} or
             * {@link IqSendCoverPhotoRequest}.
             *
             * @return the id; never {@code null}
             */
            public String id() {
                return id;
            }

            /**
             * Returns the signed cover photo URL.
             *
             * @apiNote
             * Use this getter to render the banner of the merchant
             * profile sheet.
             *
             * @return the URL; never {@code null}
             */
            public String url() {
                return url;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public boolean equals(Object obj) {
                if (obj == this) {
                    return true;
                }
                if (obj == null || obj.getClass() != this.getClass()) {
                    return false;
                }
                var that = (CoverPhoto) obj;
                return Objects.equals(this.id, that.id)
                        && Objects.equals(this.url, that.url);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public int hashCode() {
                return Objects.hash(id, url);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String toString() {
                return "IqQueryBusinessProfileResponse.Success.CoverPhoto[id=" + id
                        + ", url=" + url + ']';
            }
        }

        /**
         * One bot welcome prompt on a {@link Profile}: emoji plus
         * body text.
         *
         * @apiNote
         * Use this entry to render one tappable prompt on the
         * welcome-prompts surface; the emoji is rendered as a small
         * badge next to the body text.
         */
        public static final class Prompt {
            /**
             * The emoji glyph (empty when the merchant omitted it).
             */
            private final String emoji;

            /**
             * The body text.
             */
            private final String text;

            /**
             * Constructs a prompt.
             *
             * @apiNote
             * Use this constructor only from the response parser;
             * the emoji defaults to an empty string when the
             * merchant did not supply one.
             *
             * @param emoji the emoji; never {@code null} (empty when
             *              omitted)
             * @param text  the body; never {@code null}
             * @throws NullPointerException if either argument is
             *                              {@code null}
             */
            public Prompt(String emoji, String text) {
                this.emoji = Objects.requireNonNull(emoji, "emoji cannot be null");
                this.text = Objects.requireNonNull(text, "text cannot be null");
            }

            /**
             * Returns the emoji glyph.
             *
             * @apiNote
             * Use this getter to render the small badge next to the
             * prompt body; an empty string means the merchant
             * omitted the emoji.
             *
             * @return the emoji; never {@code null}
             */
            public String emoji() {
                return emoji;
            }

            /**
             * Returns the body text.
             *
             * @apiNote
             * Use this getter to render the prompt label on the
             * welcome-prompts surface.
             *
             * @return the body; never {@code null}
             */
            public String text() {
                return text;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public boolean equals(Object obj) {
                if (obj == this) {
                    return true;
                }
                if (obj == null || obj.getClass() != this.getClass()) {
                    return false;
                }
                var that = (Prompt) obj;
                return Objects.equals(this.emoji, that.emoji)
                        && Objects.equals(this.text, that.text);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public int hashCode() {
                return Objects.hash(emoji, text);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String toString() {
                return "IqQueryBusinessProfileResponse.Success.Prompt[emoji=" + emoji
                        + ", text=" + text + ']';
            }
        }

        /**
         * One bot command on a {@link Profile}: name plus description.
         *
         * @apiNote
         * Use this entry to render one row of the slash-command
         * palette the bot-enabled merchant exposes.
         */
        public static final class Command {
            /**
             * The command name.
             */
            private final String name;

            /**
             * The command description.
             */
            private final String description;

            /**
             * Constructs a command.
             *
             * @apiNote
             * Use this constructor only from the response parser.
             *
             * @param name        the name; never {@code null}
             * @param description the description; never {@code null}
             * @throws NullPointerException if either argument is
             *                              {@code null}
             */
            public Command(String name, String description) {
                this.name = Objects.requireNonNull(name, "name cannot be null");
                this.description = Objects.requireNonNull(description, "description cannot be null");
            }

            /**
             * Returns the command name.
             *
             * @apiNote
             * Use this getter to render the slash-command label
             * (e.g. {@code "menu"}, {@code "order"}).
             *
             * @return the name; never {@code null}
             */
            public String name() {
                return name;
            }

            /**
             * Returns the command description.
             *
             * @apiNote
             * Use this getter to render the slash-command help text
             * next to the label.
             *
             * @return the description; never {@code null}
             */
            public String description() {
                return description;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public boolean equals(Object obj) {
                if (obj == this) {
                    return true;
                }
                if (obj == null || obj.getClass() != this.getClass()) {
                    return false;
                }
                var that = (Command) obj;
                return Objects.equals(this.name, that.name)
                        && Objects.equals(this.description, that.description);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public int hashCode() {
                return Objects.hash(name, description);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String toString() {
                return "IqQueryBusinessProfileResponse.Success.Command[name=" + name
                        + ", description=" + description + ']';
            }
        }

        /**
         * The list of typed profile entries in wire order.
         */
        private final List<Profile> profiles;

        /**
         * Constructs a successful reply.
         *
         * @apiNote
         * Use this constructor only from {@link #of(Node, Node)};
         * the supplied list is defensively copied so the caller
         * may mutate the source freely after construction.
         *
         * @param profiles the profile entries; never {@code null}
         * @throws NullPointerException if {@code profiles} is
         *                              {@code null}
         */
        public Success(List<Profile> profiles) {
            Objects.requireNonNull(profiles, "profiles cannot be null");
            this.profiles = List.copyOf(profiles);
        }

        /**
         * Returns the typed profile entries.
         *
         * @apiNote
         * Use this getter to refresh the business-profile
         * collection; the order matches the wire order, which
         * matches the caller-supplied entry order on the
         * {@link IqQueryBusinessProfileRequest}.
         *
         * @return an unmodifiable list; never {@code null}
         */
        public List<Profile> profiles() {
            return profiles;
        }

        /**
         * Tries to parse a {@link Success} variant.
         *
         * @apiNote
         * Call this from {@link #of(Node, Node)}; the method
         * validates the {@code <iq type="result">} envelope and
         * iterates over every {@code <profile/>} child of the
         * {@code <business_profile/>} payload, dispatching to
         * {@link #parseProfile(Node, Jid, String)} for each entry
         * whose {@code jid} attribute resolves.
         *
         * @implNote
         * This implementation matches the
         * {@code WAWebQueryBusinessProfileJob} default export and
         * its parser dispatch through
         * {@code WAWebCommonParsersParseBusinessProfile.default};
         * entries with missing or unparseable {@code jid}
         * attributes are silently skipped, matching the WA Web
         * behaviour.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or
         *         empty when the stanza does not match the success
         *         schema
         */
        @WhatsAppWebExport(moduleName = "WAWebQueryBusinessProfileJob",
                exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<Success> of(Node node, Node request) {
            if (!SmaxIqResultResponseMixin.validate(node, request)) {
                return Optional.empty();
            }
            var businessProfileNode = node.getChild("business_profile").orElse(null);
            if (businessProfileNode == null) {
                return Optional.of(new Success(Collections.emptyList()));
            }
            var profiles = new ArrayList<Profile>();
            for (var profileNode : businessProfileNode.getChildren("profile")) {
                var jid = profileNode.getAttributeAsJid("jid").orElse(null);
                if (jid == null) {
                    continue;
                }
                var tag = profileNode.getAttributeAsString("tag").orElse(null);
                profiles.add(parseProfile(profileNode, jid, tag));
            }
            return Optional.of(new Success(profiles));
        }

        /**
         * Decodes a single {@code <profile/>} child into the typed
         * {@link Profile} projection.
         *
         * @apiNote
         * Helper for {@link #of(Node, Node)}; reads every supported
         * grandchild and attribute of the profile node and
         * assembles a fully populated entry.
         *
         * @implNote
         * This implementation mirrors
         * {@code WAWebCommonParsersParseBusinessProfile.default};
         * fields the relay omits resolve to {@link Optional#empty()}
         * or an empty list rather than throwing, matching the WA
         * Web behaviour.
         *
         * @param profileNode the {@code <profile/>} child
         * @param jid         the parsed JID
         * @param tag         the parsed tag
         * @return the decoded profile entry; never {@code null}
         */
        private static Profile parseProfile(Node profileNode, Jid jid, String tag) {
            var address = profileNode.getChild("address")
                    .flatMap(Node::toContentString).orElse(null);
            var description = profileNode.getChild("description")
                    .flatMap(Node::toContentString).orElse(null);
            var email = profileNode.getChild("email")
                    .flatMap(Node::toContentString).orElse(null);
            var priceTierId = profileNode.getChild("price_tier")
                    .flatMap(p -> p.getAttributeAsString("id")).orElse(null);
            var latitude = profileNode.getChild("latitude")
                    .flatMap(Node::toContentString)
                    .map(Double::parseDouble).orElse(null);
            var longitude = profileNode.getChild("longitude")
                    .flatMap(Node::toContentString)
                    .map(Double::parseDouble).orElse(null);
            var websites = new ArrayList<String>();
            for (var w : profileNode.getChildren("website")) {
                var url = w.toContentString().orElse(null);
                if (url != null) {
                    websites.add(url);
                }
            }
            var memberSinceText = profileNode.getChild("member_since_text")
                    .flatMap(Node::toContentString).orElse(null);
            Boolean isAuthorizedAgent = null;
            String parentCompanyName = null;
            String parentCompanyLogoUrl = null;
            String obaPhoneNumber = null;
            var authorizedAgentNode = profileNode.getChild("authorized_agent").orElse(null);
            if (authorizedAgentNode != null) {
                isAuthorizedAgent = authorizedAgentNode
                        .getAttributeAsString("is_authorized_agent")
                        .map("true"::equals).orElse(null);
                parentCompanyName = authorizedAgentNode.getChild("parent_company_name")
                        .flatMap(Node::toContentString).orElse(null);
                parentCompanyLogoUrl = authorizedAgentNode.getChild("parent_company_logo_url")
                        .flatMap(Node::toContentString).orElse(null);
                obaPhoneNumber = authorizedAgentNode.getChild("oba_phone_number")
                        .flatMap(Node::toContentString).orElse(null);
            }
            var categories = new ArrayList<Success.Category>();
            profileNode.getChild("categories").ifPresent(cats -> {
                for (var cat : cats.getChildren("category")) {
                    var id = cat.getAttributeAsString("id").orElse("");
                    var name = cat.toContentString().orElse("");
                    categories.add(new Success.Category(id, name));
                }
            });
            BusinessHours businessHours = null;
            var businessHoursNode = profileNode.getChild("business_hours").orElse(null);
            if (businessHoursNode != null) {
                var timezone = businessHoursNode.getAttributeAsString("timezone").orElse(null);
                var configs = new ArrayList<BusinessHoursConfig>();
                for (var c : businessHoursNode.getChildren("business_hours_config")) {
                    var dow = c.getAttributeAsString("day_of_week").orElse("");
                    var mode = c.getAttributeAsString("mode").orElse("");
                    var openTime = c.getAttributeAsInt("open_time").orElse(0);
                    var closeTime = c.getAttributeAsInt("close_time").orElse(0);
                    configs.add(new BusinessHoursConfig(dow, mode, openTime, closeTime));
                }
                businessHours = new BusinessHours(timezone, configs);
            }
            ProfileOptions profileOptions = null;
            var profileOptionsNode = profileNode.getChild("profile_options").orElse(null);
            if (profileOptionsNode != null) {
                var commerceExperience = profileOptionsNode.getChild("commerce_experience")
                        .flatMap(Node::toContentString).orElse(null);
                var cartEnabled = profileOptionsNode.getChild("cart_enabled")
                        .flatMap(Node::toContentString)
                        .map("true"::equals).orElse(null);
                var shopUrl = profileOptionsNode.getChild("shop_url")
                        .flatMap(Node::toContentString).orElse(null);
                var commerceManagerUrl = profileOptionsNode.getChild("commerce_manager_url")
                        .flatMap(Node::toContentString).orElse(null);
                var banned = profileOptionsNode.getChild("is_banned")
                        .flatMap(Node::toContentString)
                        .map("true"::equals).orElse(null);
                var dc = profileOptionsNode.getChild("direct_connection")
                        .flatMap(Node::toContentString)
                        .map("true"::equals).orElse(null);
                var profileEditDisabled = profileOptionsNode.getChild("is_profile_edit_disabled")
                        .flatMap(Node::toContentString)
                        .map("true"::equals).orElse(null);
                profileOptions = new ProfileOptions(commerceExperience, cartEnabled, shopUrl,
                        commerceManagerUrl, banned, dc, profileEditDisabled);
            }
            DirectConnection directConnection = null;
            var directConnectionNode = profileNode.getChild("direct_connection").orElse(null);
            if (directConnectionNode != null) {
                var enabled = directConnectionNode.getAttributeAsString("enabled")
                        .map("true"::equals).orElse(false);
                DefaultPostcode defaultPostcode = null;
                var defaultPostcodeNode = directConnectionNode.getChild("default_postcode").orElse(null);
                if (defaultPostcodeNode != null) {
                    var code = defaultPostcodeNode.getAttributeAsString("code").orElse("");
                    var locName = defaultPostcodeNode.getAttributeAsString("location_name").orElse("");
                    defaultPostcode = new DefaultPostcode(code, locName);
                }
                directConnection = new DirectConnection(enabled, defaultPostcode);
            }
            var serviceAreas = new ArrayList<ServiceArea>();
            profileNode.getChild("service_areas").ifPresent(sas -> {
                for (var sa : sas.getChildren("service_area")) {
                    var radiusNode = sa.getChild("area_radius_meters").orElse(null);
                    var centerNode = sa.getChild("area_center").orElse(null);
                    if (radiusNode == null || centerNode == null) {
                        continue;
                    }
                    var lat = centerNode.getChild("latitude").flatMap(Node::toContentString).orElse(null);
                    var lon = centerNode.getChild("longitude").flatMap(Node::toContentString).orElse(null);
                    if (lat == null || lon == null) {
                        continue;
                    }
                    var radiusValue = radiusNode.toContentString().map(Double::parseDouble).orElse(0.0);
                    var areaDescription = sa.getChild("area_description")
                            .flatMap(Node::toContentString).orElse("");
                    serviceAreas.add(new ServiceArea(radiusValue, Double.parseDouble(lat),
                            Double.parseDouble(lon), areaDescription));
                }
            });
            var catalogStatus = profileNode.getChild("catalog_status")
                    .flatMap(c -> c.getAttributeAsString("status")).orElse(null);
            var offerings = new ArrayList<OfferingCategory>();
            profileNode.getChild("offerings").ifPresent(o -> {
                for (var category : o.getChildren("category")) {
                    var id = category.getAttributeAsString("id").orElse("");
                    var name = category.getAttributeAsString("name").orElse("");
                    var entries = new ArrayList<Offering>();
                    for (var entry : category.getChildren("offering")) {
                        var entryId = entry.getAttributeAsString("id").orElse("");
                        var entryName = entry.toContentString().orElse("");
                        var offered = entry.getAttributeAsString("is_offered")
                                .map("true"::equals).orElse(false);
                        entries.add(new Offering(entryId, entryName, offered));
                    }
                    offerings.add(new OfferingCategory(id, name, entries));
                }
            });
            FacebookPage facebookPage = null;
            InstagramProfessional instagramProfessional = null;
            var profileIsLinked = false;
            var linkedAccountsNode = profileNode.getChild("linked_accounts").orElse(null);
            if (linkedAccountsNode != null) {
                profileIsLinked = true;
                var fbNode = linkedAccountsNode.getChild("fb_page").orElse(null);
                if (fbNode != null) {
                    var id = fbNode.getAttributeAsString("id").orElse(null);
                    var displayName = fbNode.getChild("display_name")
                            .flatMap(Node::toContentString).orElse(null);
                    var likes = fbNode.getChild("likes")
                            .flatMap(Node::toContentInt).orElse(null);
                    facebookPage = new FacebookPage(id, displayName, likes);
                }
                var igNode = linkedAccountsNode.getChild("ig_professional").orElse(null);
                if (igNode != null) {
                    var igHandle = igNode.getChild("ig_handle")
                            .flatMap(Node::toContentString).orElse(null);
                    var followers = igNode.getChild("followers")
                            .flatMap(Node::toContentInt).orElse(null);
                    instagramProfessional = new InstagramProfessional(igHandle, followers);
                }
            }
            CoverPhoto coverPhoto = null;
            var coverPhotoNode = profileNode.getChild("cover_photo").orElse(null);
            if (coverPhotoNode != null) {
                var id = coverPhotoNode.getAttributeAsString("id").orElse("");
                var url = coverPhotoNode.toContentString().orElse("");
                coverPhoto = new CoverPhoto(id, url);
            }
            var customUrl = profileNode.getChild("custom_url")
                    .flatMap(Node::toContentString).orElse(null);
            var automatedType = profileNode.getChild("automated_type")
                    .flatMap(Node::toContentString).orElse(null);
            var welcomeMessageProtocolMode = profileNode.getChild("welcome_message_protocol_mode")
                    .flatMap(Node::toContentString).orElse(null);
            var prompts = new ArrayList<Prompt>();
            profileNode.getChild("prompts").ifPresent(ps -> {
                for (var p : ps.getChildren("prompt")) {
                    var emoji = p.getChild("emoji")
                            .flatMap(Node::toContentString).orElse("");
                    var text = p.getChild("text")
                            .flatMap(Node::toContentString).orElse("");
                    prompts.add(new Prompt(emoji, text));
                }
            });
            var commands = new ArrayList<Command>();
            String commandsDescription = null;
            var commandsNode = profileNode.getChild("commands").orElse(null);
            if (commandsNode != null) {
                commandsDescription = commandsNode.getChild("description")
                        .flatMap(Node::toContentString).orElse(null);
                for (var c : commandsNode.getChildren("command")) {
                    var name = c.getChild("name")
                            .flatMap(Node::toContentString).orElse("");
                    var desc = c.getChild("description")
                            .flatMap(Node::toContentString).orElse("");
                    commands.add(new Command(name, desc));
                }
            }
            return new Profile(jid, tag, address, description, email, latitude, longitude,
                    websites, categories, businessHours, catalogStatus, profileOptions,
                    facebookPage, instagramProfessional, profileIsLinked, directConnection,
                    serviceAreas, offerings, coverPhoto, customUrl, prompts, commands,
                    commandsDescription, automatedType, welcomeMessageProtocolMode,
                    memberSinceText, priceTierId, isAuthorizedAgent, parentCompanyName,
                    parentCompanyLogoUrl, obaPhoneNumber);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (Success) obj;
            return Objects.equals(this.profiles, that.profiles);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int hashCode() {
            return Objects.hash(profiles);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return "IqQueryBusinessProfileResponse.Success[profiles=" + profiles + ']';
        }
    }

    /**
     * The {@code ClientError} variant emitted when the relay rejects
     * the request as malformed or referencing an unknown merchant.
     *
     * @apiNote
     * Use this variant to surface a user-facing 4xx-class error to
     * the merchant-profile rendering surface.
     */
    final class ClientError implements IqQueryBusinessProfileResponse {
        /**
         * The numeric error code echoed by the {@code <error/>} child.
         */
        private final int errorCode;

        /**
         * The optional human-readable error text echoed by the
         * {@code <error/>} child.
         */
        private final String errorText;

        /**
         * Constructs a client-error reply.
         *
         * @apiNote
         * Use this constructor only from {@link #of(Node, Node)}; the
         * (code, text) pair comes from the relay's {@code <error/>}
         * envelope.
         *
         * @param errorCode the numeric error code
         * @param errorText the optional human-readable text; may be
         *                  {@code null}
         */
        public ClientError(int errorCode, String errorText) {
            this.errorCode = errorCode;
            this.errorText = errorText;
        }

        /**
         * Returns the numeric error code.
         *
         * @apiNote
         * Use this getter to dispatch on the relay-side error code
         * when surfacing a localised message to the merchant-profile
         * rendering surface.
         *
         * @return the error code
         */
        public int errorCode() {
            return errorCode;
        }

        /**
         * Returns the human-readable error text, when supplied.
         *
         * @apiNote
         * Use this getter for logging; the text is server-localised
         * and not stable across snapshots.
         *
         * @return an {@link Optional} carrying the error text
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Tries to parse a {@link ClientError} variant.
         *
         * @apiNote
         * Call this from {@link #of(Node, Node)}; the method delegates
         * to {@link SmaxBaseServerErrorMixin#parseClientError(Node, Node)}
         * to extract the (code, text) envelope.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or
         *         empty when the stanza does not match the
         *         client-error schema
         */
        public static Optional<ClientError> of(Node node, Node request) {
            var envelope = SmaxBaseServerErrorMixin.parseClientError(node, request).orElse(null);
            if (envelope == null) {
                return Optional.empty();
            }
            return Optional.of(new ClientError(envelope.code(), envelope.text()));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (ClientError) obj;
            return this.errorCode == that.errorCode && Objects.equals(this.errorText, that.errorText);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int hashCode() {
            return Objects.hash(errorCode, errorText);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return "IqQueryBusinessProfileResponse.ClientError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }

    /**
     * The {@code ServerError} variant emitted when the relay returns
     * a transient internal-failure status while processing the
     * request.
     *
     * @apiNote
     * Use this variant to drive a backoff-and-retry path in the
     * merchant-profile rendering surface; the relay returns this
     * shape when the business-profile backend is temporarily
     * unavailable.
     */
    final class ServerError implements IqQueryBusinessProfileResponse {
        /**
         * The numeric error code echoed by the {@code <error/>} child.
         */
        private final int errorCode;

        /**
         * The optional human-readable error text echoed by the
         * {@code <error/>} child.
         */
        private final String errorText;

        /**
         * Constructs a server-error reply.
         *
         * @apiNote
         * Use this constructor only from {@link #of(Node, Node)}; the
         * (code, text) pair comes from the relay's {@code <error/>}
         * envelope.
         *
         * @param errorCode the numeric error code
         * @param errorText the optional human-readable text; may be
         *                  {@code null}
         */
        public ServerError(int errorCode, String errorText) {
            this.errorCode = errorCode;
            this.errorText = errorText;
        }

        /**
         * Returns the numeric error code.
         *
         * @apiNote
         * Use this getter to log the relay-side error code; a
         * 5xx-class value is the canonical retry trigger.
         *
         * @return the error code
         */
        public int errorCode() {
            return errorCode;
        }

        /**
         * Returns the human-readable error text, when supplied.
         *
         * @apiNote
         * Use this getter for logging only; the text is
         * server-localised and not stable across snapshots.
         *
         * @return an {@link Optional} carrying the error text
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Tries to parse a {@link ServerError} variant.
         *
         * @apiNote
         * Call this from {@link #of(Node, Node)}; the method delegates
         * to {@link SmaxBaseServerErrorMixin#parseServerError(Node, Node)}
         * to extract the (code, text) envelope.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or
         *         empty when the stanza does not match the
         *         server-error schema
         */
        public static Optional<ServerError> of(Node node, Node request) {
            var envelope = SmaxBaseServerErrorMixin.parseServerError(node, request).orElse(null);
            if (envelope == null) {
                return Optional.empty();
            }
            return Optional.of(new ServerError(envelope.code(), envelope.text()));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (ServerError) obj;
            return this.errorCode == that.errorCode && Objects.equals(this.errorText, that.errorText);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int hashCode() {
            return Objects.hash(errorCode, errorText);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return "IqQueryBusinessProfileResponse.ServerError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }
}
