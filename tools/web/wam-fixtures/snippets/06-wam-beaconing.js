// Capture: wam-beaconing
// Consumer:  WamBeaconingTest
// Source:    WAWebWamBeaconing
//
// Captured results of maybeGetEventSequenceNumber under controlled
// (Math.random, WATimeUtils.unixTime, UserPrefs) stubs. Each scenario runs an
// isolated sequence of calls. Cobalt DefaultWamBeaconing under matching
// deterministic stubs must produce the same OptionalInt-equivalent results.

(() => {
  const Beaconing = require('WAWebWamBeaconing');
  const TimeUtils = require('WATimeUtils');
  const UserPrefs = require('WAWebUserPrefsGeneral');

  const origUnixTime = TimeUtils.unixTime;
  const origMathRandom = Math.random;
  const origGetSettings = UserPrefs.getWamBeaconingSettings;
  const origSetSettings = UserPrefs.setWamBeaconingSettings;

  try {
    let state = [];
    UserPrefs.getWamBeaconingSettings = () => state;
    UserPrefs.setWamBeaconingSettings = next => { state = next.slice(); };

    const DAY = TimeUtils.DAY_SECONDS;
    const T0 = 100 * DAY;

    let currentTime = T0;
    TimeUtils.unixTime = () => currentTime;

    const randomQueue = [];
    Math.random = () => randomQueue.length > 0 ? randomQueue.shift() : 0.5;

    const scenarios = [];

    const runScenario = (name, description, steps) => {
      state = [];
      currentTime = T0;
      randomQueue.length = 0;
      const results = [];
      for (const step of steps) {
        if (step.advanceDays) currentTime = T0 + step.advanceDays * DAY;
        if (step.advanceSeconds) currentTime += step.advanceSeconds;
        if (step.pushRandom !== undefined) randomQueue.push(step.pushRandom);
        const r = Beaconing.maybeGetEventSequenceNumber(step.bufferKey);
        results.push({
          step: step.label, bufferKey: step.bufferKey, unixTime: currentTime,
          dayIndex: Math.floor(currentTime / DAY),
          pushedRandom: step.pushRandom === undefined ? null : step.pushRandom,
          result: r
        });
      }
      scenarios.push({ name, description, results });
    };

    runScenario('activate_then_increment_regular',
      'Day0 roll under 0.01 → activates "regular" key; subsequent calls within same day return monotonically increasing sequence',
      [
        { label: 'first_call_activates', bufferKey: 'regular', pushRandom: 0.005 },
        { label: 'same_day_increments_1', bufferKey: 'regular' },
        { label: 'same_day_increments_2', bufferKey: 'regular' },
        { label: 'same_day_increments_3', bufferKey: 'regular' }
      ]
    );

    runScenario('reject_then_remain_inactive',
      'Day0 roll above 0.01 → "regular" key stored as null; further same-day calls keep returning null',
      [
        { label: 'first_call_rejects', bufferKey: 'regular', pushRandom: 0.5 },
        { label: 'same_day_still_null_1', bufferKey: 'regular' },
        { label: 'same_day_still_null_2', bufferKey: 'regular' }
      ]
    );

    runScenario('rerolls_at_day_boundary',
      'After a UTC day passes, the state for that key re-rolls. Day0 activates, Day1 deactivates.',
      [
        { label: 'day0_activates',  bufferKey: 'regular', pushRandom: 0.005 },
        { label: 'day0_increment',  bufferKey: 'regular' },
        { label: 'day1_deactivate', bufferKey: 'regular', advanceDays: 1, pushRandom: 0.5 },
        { label: 'day1_still_null', bufferKey: 'regular' },
        { label: 'day2_reactivate', bufferKey: 'regular', advanceDays: 2, pushRandom: 0.001 },
        { label: 'day2_increment',  bufferKey: 'regular' }
      ]
    );

    runScenario('independent_keys',
      'Different buffer keys roll independently; activation of "regular" does not influence "realtime".',
      [
        { label: 'regular_activates',   bufferKey: 'regular',  pushRandom: 0.005 },
        { label: 'realtime_rejects',    bufferKey: 'realtime', pushRandom: 0.5 },
        { label: 'regular_increments',  bufferKey: 'regular' },
        { label: 'realtime_still_null', bufferKey: 'realtime' }
      ]
    );

    runScenario('threshold_boundary',
      'The activation cutoff is 0.01 inclusive: random==0.01 activates, random==0.01000001 rejects',
      [
        { label: 'random_eq_threshold', bufferKey: 'regular', pushRandom: 0.01 },
        { label: 'reset_day_for_next',  bufferKey: 'realtime', pushRandom: 0.01000001, advanceDays: 1 }
      ]
    );

    return JSON.stringify({
      fixture: 'wam-beaconing',
      description: 'Captured results of WAWebWamBeaconing.maybeGetEventSequenceNumber under controlled (Math.random, WATimeUtils.unixTime, UserPrefs) stubs.',
      snapshotRevision: 1039260921,
      liveRuntimeRevision: 1039260921,
      capturedAt: '2026-05-12',
      capturedVia: 'mcp__whatsapp__web_live_debug_eval_to_file',
      sourceModule: 'WAWebWamBeaconing',
      activationThreshold: 0.01,
      daySeconds: TimeUtils.DAY_SECONDS,
      scenarios
    }, null, 2);
  } finally {
    TimeUtils.unixTime = origUnixTime;
    Math.random = origMathRandom;
    UserPrefs.getWamBeaconingSettings = origGetSettings;
    UserPrefs.setWamBeaconingSettings = origSetSettings;
  }
})()
