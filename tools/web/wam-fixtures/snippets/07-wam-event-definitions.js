// Capture: wam-event-definitions
// Consumer:  WamEventRegistryAuditTest
// Source:    WAWebWamCodegenUtils
//
// Full enumeration of every registered WAM event and its fields from
// WAWebWamCodegenUtils.metrics, plus every named global. The audit test
// cross-checks that every (eventId, fieldId, channel) tuple has a matching
// Cobalt @WamEvent impl, and that Cobalt does not declare events that no
// longer exist in WA Web.

(() => {
  const codegen = require('WAWebWamCodegenUtils');
  const ev = codegen.events;
  const m = codegen.metrics;

  const allFieldKeys = Object.keys(m.$1);
  const fieldsByEvent = {};
  for (const k of allFieldKeys) {
    const idx = k.indexOf('::');
    if (idx < 0) continue;
    const eventName = k.slice(0, idx);
    const fieldName = k.slice(idx + 2);
    const def = m.$1[k];
    (fieldsByEvent[eventName] = fieldsByEvent[eventName] || []).push({
      name: fieldName, id: def.id,
      type: typeof def.type === 'string' ? def.type : 'enum',
      enumValues: typeof def.type === 'object' ? def.type : null
    });
  }

  const events = [];
  for (const name of Object.keys(ev)) {
    let id = null, channel = null, falcoName = null;
    try {
      const inst = new ev[name]({});
      id = inst.id;
      channel = inst.wamChannel;
      falcoName = inst.$falcoEventName || null;
    } catch (e) { /* skip */ }
    const fields = (fieldsByEvent[name] || []).sort((a, b) => a.id - b.id);
    events.push({ name, id, channel, falcoEventName: falcoName, fields });
  }
  events.sort((a, b) => (a.id || 0) - (b.id || 0));

  const wellKnownGlobals = [
    'mnc','mcc','platform','deviceName','osVersion','appVersion','appIsBetaRelease','networkIsWifi',
    'commitTime','browserVersion','webcEnv','memClass','yearClass','webcPhonePlatform','browser',
    'webcPhoneCharging','webcPhoneDeviceManufacturer','webcPhoneDeviceModel','webcPhoneOsBuildNumber',
    'webcPhoneOsVersion','webcBucket','webcWebPlatform','webcPhoneAppVersion','webcNativeBetaUpdates',
    'webcNativeAutolaunch','appBuild','yearClass2016','datacenter','beaconSessionId','streamId',
    'webcTabId','abKey2','deviceVersion','expoKey','psId','ocVersion','webcWebDeviceManufacturer',
    'webcWebDeviceModel','webcWebOsReleaseNumber','webcWebArch','psCountryCode','numCpu',
    'serviceImprovementOptOut','deviceClassification','wametaLoggerTestFilter','webcRevision','isInCohort'
  ];
  const globals = [];
  for (const name of wellKnownGlobals) {
    try {
      const g = m.getGlobal(name);
      globals.push({
        name, id: g.id,
        type: typeof g.type === 'string' ? g.type : 'enum',
        channels: g.channels || null,
        enumValues: typeof g.type === 'object' ? g.type : null
      });
    } catch (e) {
      globals.push({ name, error: String(e) });
    }
  }

  return JSON.stringify({
    fixture: 'wam-event-definitions',
    description: 'Full enumeration of every registered WAM event and its fields from WAWebWamCodegenUtils.metrics. WamEventRegistryAuditTest cross-checks Cobalt @WamEvent impls against the WA Web registry.',
    snapshotRevision: 1039260921,
    liveRuntimeRevision: 1039260921,
    capturedAt: '2026-05-12',
    capturedVia: 'mcp__whatsapp__web_live_debug_eval_to_file',
    sourceModule: 'WAWebWamCodegenUtils',
    eventCount: events.length,
    events, globals
  }, null, 2);
})()
