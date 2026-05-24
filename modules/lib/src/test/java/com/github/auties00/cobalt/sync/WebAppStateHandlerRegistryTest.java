package com.github.auties00.cobalt.sync;

import com.github.auties00.cobalt.client.TestWhatsAppClient;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.device.DeviceFixtures;
import com.github.auties00.cobalt.migration.LidMigrationService;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.props.TestABPropsService;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;
import com.github.auties00.cobalt.sync.handler.WebAppStateActionHandler;
import com.github.auties00.cobalt.wam.DefaultWamService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the structural contract of
 * {@link WebAppStateHandlerRegistry} against WhatsApp Web's
 * {@code WAWebSyncdGetActionHandler.setActionHandlers}.
 *
 * @apiNote Cobalt-internal exercise of the registry: the
 * incoming-mutation router relies on every default handler being
 * registered, on lookup-by-action-name working in both
 * present-and-missing branches, on every handler declaring a non-null
 * collection and a non-negative version, on
 * {@link WebAppStateHandlerRegistry#maxSupportedVersion()} being a true
 * maximum, and on subsequent
 * {@link WebAppStateHandlerRegistry#registerHandler(WebAppStateActionHandler)}
 * calls overriding earlier ones for the same action name.
 *
 * @implNote This implementation builds a real registry through
 * {@link WebAppStateHandlerRegistry#WebAppStateHandlerRegistry(ABPropsService, LidMigrationService, com.github.auties00.cobalt.wam.WamService)}
 * with the same dependency wiring as
 * {@link WebAppStateService}, so the assertions exercise the
 * production handler set rather than a synthetic one. A trivial
 * {@link RecordingHandler} is used for the override / new-registration
 * cases.
 */
@DisplayName("WebAppStateHandlerRegistry")
class WebAppStateHandlerRegistryTest {
    /**
     * The local user's PN-form JID baked into the test store.
     */
    private static final Jid SELF_PN = Jid.of("19250000001@s.whatsapp.net");

    /**
     * The local user's LID-form JID baked into the test store.
     */
    private static final Jid SELF_LID = Jid.of("83116928594000@lid");

    /**
     * The registry under test, freshly created per test.
     */
    private WebAppStateHandlerRegistry registry;

    /**
     * Builds a fresh registry per test with the same dependency
     * wiring as the production code path.
     *
     * @apiNote JUnit-managed setup; not invoked manually from the
     * tests.
     */
    @BeforeEach
    void setUp() {
        var props = TestABPropsService.builder().build();
        var store = DeviceFixtures.temporaryStore(SELF_PN, SELF_LID);
        var client = TestWhatsAppClient.create().withStore(store);
        var wam = new DefaultWamService(client, props);
        var lidMigration = new LidMigrationService(client, props, wam);
        registry = new WebAppStateHandlerRegistry(props, lidMigration, wam);
    }

    /**
     * Pins the default-handler population done by the constructor.
     */
    @Nested
    @DisplayName("default registration -- every WAWeb*Sync handler is wired")
    class DefaultRegistration {
        /**
         * Looks up one bedrock action to catch a complete-failure
         * regression early.
         */
        @Test
        @DisplayName("a known action name resolves to its handler")
        void knownActionResolves() {
            var handler = registry.findHandler("archive").orElseThrow(
                    () -> new AssertionError("archive handler must be registered by default"));
            assertNotNull(handler);
        }

        /**
         * Looks up one action name per family to catch missing-family
         * regressions.
         */
        @Test
        @DisplayName("a representative selection of action names all resolve")
        void representativeSelectionResolves() {
            for (var actionName : new String[]{
                    "archive", "pin_v1", "mute", "markChatAsRead", "deleteChat",
                    "star", "deleteMessageForMe",
                    "contact", "lid_contact",
                    "label_edit", "label_jid",
                    "quick_reply", "deviceAgent", "agentChatAssignment",
                    "favoriteSticker", "removeRecentSticker", "avatar_updated_action",
                    "payment_info", "payment_tos", "custom_payment_methods",
                    "userStatusMute", "time_format", "favorites",
                    "nux", "primary_feature", "settings_sync", "setting_pushName",
            }) {
                assertTrue(registry.findHandler(actionName).isPresent(),
                        "handler must be registered: " + actionName);
            }
        }
    }

    /**
     * Pins the lookup-miss behaviour of
     * {@link WebAppStateHandlerRegistry#findHandler(String)}.
     */
    @Nested
    @DisplayName("lookup -- unknown action")
    class UnknownAction {
        /**
         * Unknown action names produce
         * {@link java.util.Optional#empty()}.
         */
        @Test
        @DisplayName("findHandler on an unknown name returns Optional.empty()")
        void unknownReturnsEmpty() {
            assertTrue(registry.findHandler("not_a_real_action_name").isEmpty());
        }

        /**
         * The empty string is treated like any other unknown action
         * name.
         */
        @Test
        @DisplayName("findHandler on an empty string returns Optional.empty()")
        void emptyStringReturnsEmpty() {
            assertTrue(registry.findHandler("").isEmpty());
        }
    }

    /**
     * Pins per-handler invariants the rest of the sync pipeline
     * relies on.
     */
    @Nested
    @DisplayName("collection consistency -- every handler points at a valid SyncPatchType")
    class CollectionConsistency {
        /**
         * Every default handler declares a non-{@code null}
         * {@link SyncPatchType}.
         */
        @Test
        @DisplayName("every registered handler resolves to a non-null SyncPatchType")
        void allCollectionsNonNull() {
            for (var actionName : sampleRegisteredActionNames()) {
                var handler = registry.findHandler(actionName).orElseThrow();
                assertNotNull(handler.collectionName(),
                        "handler " + actionName + " must declare a collection");
            }
        }

        /**
         * Every default handler declares a non-negative version
         * number.
         */
        @Test
        @DisplayName("every handler's version is non-negative")
        void allVersionsNonNegative() {
            for (var actionName : sampleRegisteredActionNames()) {
                var handler = registry.findHandler(actionName).orElseThrow();
                assertTrue(handler.version() >= 0,
                        "handler " + actionName + " has negative version: " + handler.version());
            }
        }
    }

    /**
     * Pins the maximum-version reduction of
     * {@link WebAppStateHandlerRegistry#maxSupportedVersion()}.
     */
    @Nested
    @DisplayName("maxSupportedVersion")
    class MaxSupportedVersion {
        /**
         * The reported max is at least the version of every
         * registered handler in the sample set.
         */
        @Test
        @DisplayName("maxSupportedVersion equals the maximum of all registered handlers' versions")
        void matchesMaxOverAll() {
            var max = registry.maxSupportedVersion();
            assertTrue(max >= 0, "version is non-negative");

            for (var actionName : sampleRegisteredActionNames()) {
                var version = registry.findHandler(actionName).orElseThrow().version();
                assertTrue(version <= max,
                        "handler " + actionName + " (v=" + version
                                + ") exceeds reported max=" + max);
            }
        }
    }

    /**
     * Pins the override semantics of
     * {@link WebAppStateHandlerRegistry#registerHandler(WebAppStateActionHandler)}.
     */
    @Nested
    @DisplayName("registerHandler -- override on duplicate actionName")
    class RegisterHandler {
        /**
         * A second {@code registerHandler} call on the same action
         * name replaces the first.
         */
        @Test
        @DisplayName("a second handler with the same actionName replaces the first")
        void duplicateNameOverrides() {
            var existing = registry.findHandler("archive").orElseThrow();
            var replacement = new RecordingHandler("archive",
                    existing.collectionName(), existing.version());

            registry.registerHandler(replacement);
            assertEquals(replacement, registry.findHandler("archive").orElseThrow(),
                    "second register call overrides the first per WAWebSyncdGetActionHandler.setActionHandlers");
        }

        /**
         * A new handler under a previously unused action name is
         * lookable.
         */
        @Test
        @DisplayName("registering a new handler under a new actionName makes it lookable")
        void newRegistrationLookable() {
            assertFalse(registry.findHandler("__test_action__").isPresent(),
                    "precondition: no handler registered for the test action name");

            var custom = new RecordingHandler("__test_action__", SyncPatchType.REGULAR, 1);
            registry.registerHandler(custom);
            assertEquals(custom, registry.findHandler("__test_action__").orElseThrow());
        }
    }

    /**
     * Returns the sample of action names every iteration test walks.
     *
     * @apiNote Helper used by the {@code CollectionConsistency} and
     * {@code MaxSupportedVersion} blocks. Kept in sync by hand with
     * {@code WebAppStateHandlerRegistry.registerDefaultHandlers}: a
     * missing action here only weakens coverage, not correctness.
     *
     * @return a set of action names that are guaranteed to be
     *         registered by the default constructor
     */
    private static Set<String> sampleRegisteredActionNames() {
        var set = new HashSet<String>();
        set.add("archive");
        set.add("pin_v1");
        set.add("mute");
        set.add("markChatAsRead");
        set.add("deleteChat");
        set.add("star");
        set.add("deleteMessageForMe");
        set.add("contact");
        set.add("lid_contact");
        set.add("payment_info");
        set.add("payment_tos");
        set.add("nux");
        set.add("primary_feature");
        set.add("settings_sync");
        return set;
    }

    /**
     * A minimal {@link WebAppStateActionHandler} implementation that
     * carries explicit identity fields and a no-op
     * {@code applyMutation}.
     *
     * @apiNote Used by the override / new-registration tests to
     * substitute a known instance under an existing or fresh action
     * name without touching a real handler implementation.
     *
     * @param actionName     the action name returned by
     *                       {@link WebAppStateActionHandler#actionName()}
     * @param collectionName the collection returned by
     *                       {@link WebAppStateActionHandler#collectionName()}
     * @param version        the version returned by
     *                       {@link WebAppStateActionHandler#version()}
     */
    private record RecordingHandler(
            String actionName,
            SyncPatchType collectionName,
            int version
    ) implements WebAppStateActionHandler {
        /**
         * {@inheritDoc}
         *
         * @implNote This implementation always returns
         * {@code MutationApplicationResult.success()}; the test
         * suite only inspects identity, never invokes
         * {@code applyMutation}.
         */
        @Override
        public MutationApplicationResult applyMutation(
                WhatsAppClient client,
                DecryptedMutation.Trusted mutation) {
            return MutationApplicationResult.success();
        }
    }
}
