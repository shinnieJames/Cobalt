// Capture: wam-events-multi-field
// Consumer:  WamGeneratedImplKatTest
// Source:    WAWebWamLibProtocol
//
// Representative multi-field events per channel. The Cobalt *EventBuilder +
// *Impl.encode for the matching event id and field set must produce identical
// bytes. Use cobaltClass to pick the right Cobalt builder; <synthetic> rows
// test the encoder under WIDE_ID / weight permutations that no shipping event
// happens to cover.

(() => {
  const Protocol = require('WAWebWamLibProtocol');
  const Binary = require('WABinary').Binary;

  const encodeEvent = (eventId, weight, fields) => {
    const buf = new Binary(undefined, true);
    Protocol.writeEvent(buf, eventId, weight, fields.length > 0);
    fields.forEach((f, idx) => Protocol.writeField(buf, f.index, f.value, idx < fields.length - 1));
    const arr = buf.peek(v => Array.from(v.readByteArrayView()));
    return arr.map(b => b.toString(16).padStart(2, '0')).join('');
  };

  const scenarios = [
    {
      // PsIdAction enum: CREATED=1, ROTATED=2, DELETED=3 (Cobalt PsIdAction wire values)
      name: 'PsIdUpdateEvent_created', cobaltClass: 'PsIdUpdateEvent', eventId: 2862,
      channel: 'REGULAR', weight: -1,
      fields: [
        { index: 1, value: 42, type: 'int' },
        { index: 2, value: 1,  type: 'enum' },
        { index: 3, value: 7,  type: 'int' }
      ]
    },
    {
      name: 'PsIdUpdateEvent_rotated', cobaltClass: 'PsIdUpdateEvent', eventId: 2862,
      channel: 'REGULAR', weight: -1,
      fields: [
        { index: 1, value: 999, type: 'int' },
        { index: 2, value: 2,   type: 'enum' },
        { index: 3, value: 30,  type: 'int' }
      ]
    },
    {
      name: 'WamClientErrorsEvent_drop_only', cobaltClass: 'WamClientErrorsEvent', eventId: 1144,
      channel: 'REGULAR', weight: -1,
      fields: [{ index: 28, value: 1, type: 'int' }]
    },
    {
      name: 'WamClientErrorsEvent_many_counters', cobaltClass: 'WamClientErrorsEvent', eventId: 1144,
      channel: 'REGULAR', weight: -1,
      fields: [
        { index: 2,  value: 5,   type: 'int' },
        { index: 3,  value: 200, type: 'int' },
        { index: 22, value: 1,   type: 'int' },
        { index: 23, value: 100, type: 'int' },
        { index: 28, value: 1,   type: 'int' },
        { index: 1,  value: 1,   type: 'bool' }
      ]
    },
    {
      name: 'WamClientErrorsEvent_int32_range_field', cobaltClass: 'WamClientErrorsEvent', eventId: 1144,
      channel: 'REGULAR', weight: -1,
      fields: [{ index: 3, value: 1500000, type: 'int' }]
    },
    {
      name: 'WamClientErrorsEvent_all_bool_flags', cobaltClass: 'WamClientErrorsEvent', eventId: 1144,
      channel: 'REGULAR', weight: -1,
      fields: [
        { index: 8,  value: 1, type: 'bool' }, { index: 11, value: 0, type: 'bool' },
        { index: 15, value: 1, type: 'bool' }, { index: 16, value: 1, type: 'bool' },
        { index: 17, value: 0, type: 'bool' }, { index: 18, value: 1, type: 'bool' },
        { index: 19, value: 0, type: 'bool' }, { index: 27, value: 1, type: 'bool' }
      ]
    },
    {
      name: 'SyntheticEvent_wide_field_ids', cobaltClass: '<synthetic>', eventId: 100,
      channel: 'REGULAR', weight: -1,
      fields: [
        { index: 5,     value: 'narrow', type: 'str' },
        { index: 256,   value: 1,        type: 'bool' },
        { index: 257,   value: 'wide1',  type: 'str' },
        { index: 65535, value: 42,       type: 'int' }
      ]
    },
    {
      name: 'SyntheticEvent_sampled_weight', cobaltClass: '<synthetic>', eventId: 100,
      channel: 'REGULAR', weight: -42,
      fields: [
        { index: 1, value: 'a', type: 'str' },
        { index: 2, value: 7,   type: 'int' }
      ]
    }
  ];

  const vectors = scenarios.map(s => ({
    name: s.name, cobaltClass: s.cobaltClass, eventId: s.eventId, channel: s.channel, weight: s.weight,
    fields: s.fields,
    bytes: encodeEvent(s.eventId, s.weight, s.fields.map(f => ({ index: f.index, value: f.value })))
  }));

  return JSON.stringify({
    fixture: 'wam-events-multi-field',
    description: 'Live JS encoding of representative multi-field events per channel. Cobalt *EventBuilder + *Impl.encode for the matching event id and field set must produce identical bytes. Use cobaltClass to pick the right Cobalt builder; <synthetic> rows test the encoder under WIDE_ID / weight permutations that no shipping event happens to cover.',
    snapshotRevision: 1039260921,
    liveRuntimeRevision: 1039260921,
    capturedAt: '2026-05-12',
    capturedVia: 'mcp__whatsapp__web_live_debug_eval_to_file',
    sourceModule: 'WAWebWamLibProtocol',
    vectors
  }, null, 2);
})()
