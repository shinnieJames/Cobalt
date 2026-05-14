// Capture: wam-global-encoder
// Consumer:  WamGlobalEncoderKatTest
// Source:    WAWebWamLibProtocol
//
// Encodes every named WamGlobalEncoder field id with a representative value
// (plus a few boundary cases). Cobalt's WamGlobalEncoder.writeXxx must produce
// identical bytes for the same fieldId+value pair.

(() => {
  const Protocol = require('WAWebWamLibProtocol');
  const Binary = require('WABinary').Binary;

  const enc = (fieldId, value) => {
    const buf = new Binary(undefined, true);
    Protocol.writeGlobalAttribute(buf, fieldId, value);
    const arr = buf.peek(v => Array.from(v.readByteArrayView()));
    return arr.map(b => b.toString(16).padStart(2, '0')).join('');
  };

  const rows = [
    ['mnc',                          3,     'int',   310],
    ['mcc',                          5,     'int',   001],
    ['platform',                     11,    'int',   8],
    ['deviceName',                   13,    'str',   'Chrome'],
    ['osVersion',                    15,    'str',   'macOS 14.3'],
    ['appVersion',                   17,    'str',   '2.3001.1039248090'],
    ['appIsBetaRelease',             21,    'bool',  false],
    ['networkIsWifi',                23,    'bool',  true],
    ['commitTime',                   47,    'int',   1747000000],
    ['browserVersion',               295,   'str',   '120.0.6099.109'],
    ['webcEnv',                      633,   'int',   0],
    ['memClass',                     655,   'int',   8192],
    ['yearClass',                    689,   'int',   2023],
    ['webcPhonePlatform',            707,   'int',   2],
    ['browser',                      779,   'str',   'Chrome'],
    ['webcPhoneCharging',            783,   'bool',  true],
    ['webcPhoneDeviceManufacturer',  829,   'str',   'Apple'],
    ['webcPhoneDeviceModel',         831,   'str',   'iPhone 14'],
    ['webcPhoneOsBuildNumber',       833,   'str',   '21D50'],
    ['webcPhoneOsVersion',           835,   'str',   '17.3.1'],
    ['webcBucket',                   875,   'str',   'bucket42'],
    ['webcWebPlatform',              899,   'int',   1],
    ['webcPhoneAppVersion',          1005,  'str',   '2.24.1.78'],
    ['webcNativeBetaUpdates',        1007,  'bool',  false],
    ['webcNativeAutolaunch',         1009,  'bool',  true],
    ['appBuild',                     1657,  'int',   4],
    ['yearClass2016',                2617,  'int',   2016],
    ['datacenter',                   2795,  'str',   'ash'],
    ['beaconSessionId',              3433,  'int',   42],
    ['streamId',                     3543,  'int',   1],
    ['webcTabId',                    3727,  'str',   '8c1d2f3e-1234-4567-89ab-cdef01234567'],
    ['abKey2',                       4473,  'str',   'experiment_key_42'],
    ['deviceVersion',                4505,  'str',   'macOS 14.3'],
    ['expoKey',                      5029,  'str',   'expo_test_alpha'],
    ['psId',                         6005,  'str',   '4f3e2d1c0b0a09080706050403020100'],
    ['ocVersion',                    6251,  'int',   1],
    ['webcWebDeviceManufacturer',    6599,  'str',   'Apple'],
    ['webcWebDeviceModel',           6601,  'str',   'MacBookPro18,2'],
    ['webcWebOsReleaseNumber',       6603,  'str',   '23D40'],
    ['webcWebArch',                  6605,  'str',   'arm64'],
    ['psCountryCode',                6833,  'str',   'us'],
    ['numCpu',                       10317, 'int',   12],
    ['serviceImprovementOptOut',     13293, 'bool',  false],
    ['deviceClassification',         14507, 'int',   4],
    ['wametaLoggerTestFilter',       15881, 'str',   'unit_test'],
    ['webcRevision',                 18491, 'int',   1039248090],
    ['isInCohort',                   19129, 'bool',  true],

    ['null_tinyId',                  47,    'null',  null],
    ['null_wideId',                  18491, 'null',  null],
    ['int_zero',                     11,    'int',   0],
    ['int_one',                      11,    'int',   1],
    ['int_neg',                      655,   'int',   -1],
    ['bool_false',                   21,    'bool',  false],
    ['bool_true',                    21,    'bool',  true],
    ['str_empty',                    13,    'str',   ''],
    ['str_utf8',                     13,    'str',   'Olá ' + String.fromCodePoint(128512)]
  ];

  const valueForCall = (type, v) => type === 'bool' ? (v ? 1 : 0) : v;

  const vectors = rows.map(([name, fieldId, type, sampleValue]) => ({
    name, fieldId, type, sampleValue,
    bytes: enc(fieldId, valueForCall(type, sampleValue))
  }));

  return JSON.stringify({
    fixture: 'wam-global-encoder',
    description: 'writeGlobalAttribute output for every named global field id in WamGlobalEncoder.java, plus boundary cases (null, zero, neg, empty, UTF-8). Cobalt WamGlobalEncoder.writeXxx must produce identical bytes for the same fieldId+value pair.',
    snapshotRevision: 1039260921,
    liveRuntimeRevision: 1039260921,
    capturedAt: '2026-05-12',
    capturedVia: 'mcp__whatsapp__web_live_debug_eval_to_file',
    sourceModule: 'WAWebWamLibProtocol',
    encoding: { booleansEmittedAsInt: true, hashAlgorithm: 'none' },
    vectors
  }, null, 2);
})()
