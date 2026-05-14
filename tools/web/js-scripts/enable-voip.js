/**
 * WhatsApp Web VoIP Enablement Script.
 */
(async function enableVoip() {
    console.log('[Cobalt] Starting VoIP Enablement...');

    // Helper to safely set AB props
    const setProp = (name, value) => {
        try {
            require('WAWebABProps').setABPropConfigValue(name, value);
            console.log(`[Cobalt] Prop set: ${name} = ${value}`);
        } catch (e) {
            console.warn(`[Cobalt] Failed to set prop ${name}:`, e.message);
        }
    };

    // Configure Core Call Props
    setProp('enable_web_calling', true);
    setProp('enable_web_group_calling', true);
    setProp('enable_web_voip_p2p', true);
    setProp('enable_wds_calling_dropdown', true);
    setProp('web_calling_enable_on_windows', false); // Ensure we use Web implementation
    setProp('enable_unified_call_buttons_in_chat', true);

    // Force load the lazy bundles
    console.log('[Cobalt] Loading VoIP bundles...');
    try {
        await require('JSResourceForInteraction')('WAWebVoipStackInterfaceImpl').load();
        await require('JSResourceForInteraction')('WAWebVoipStackInterfaceWeb').load();
        await require('JSResourceForInteraction')('WAWebVoipInit').load();
        console.log('[Cobalt] VoIP bundles loaded successfully.');
    } catch (e) {
        console.error('[Cobalt] Bundle load failed:', e);
        return;
    }

    // Override Gating Utils
    const gating = require('WAWebVoipGatingUtils');
    gating.isCallingEnabled = () => true;
    gating.isGroupCallingEnabled = () => true;
    gating.isVoipDownloadEnabled = () => true;
    gating.callLinksEnabled = () => true;
    gating.isUnsupportedBrowserForWebCalling = () => false;
    gating.getUnsupportedBrowserReason = () => null;
    console.log('[Cobalt] Gating overridden.');

    // Ensure we use the Web Stack Implementation
    // This prevents the app from trying to use the Windows Native Bridge
    const impl = require('WAWebVoipStackInterfaceImpl');
    const webStack = require('WAWebVoipStackInterfaceWeb');
    impl.getVoipStackInterfaceImpl = () => webStack.createWAWebVoipStackInterface();
    console.log('[Cobalt] Stack implementation set to Web/WASM.');

    // Initialize VoIP Engine
    console.log('[Cobalt] Initializing VoIP WASM engine...');
    try {
        const init = require('WAWebVoipInit');
        await init.initWAWebVoip();
        console.log('[Cobalt] VoIP engine initialized and ready.');
    } catch (e) {
        console.error('[Cobalt] VoIP engine initialization failed:', e);
    }

    // Force UI Refresh (if in a chat)
    try {
        const chat = require('WAWebChatCollection').ChatCollection.getActive();
        if (chat) {
            await require('WAWebCmd').Cmd.openChatAt(chat);
            console.log('[Cobalt] UI Refresh triggered for active chat.');
        }
    } catch (e) {
        // Not critical
    }

    console.log('[Cobalt] VoIP Enablement Complete.');
})();
