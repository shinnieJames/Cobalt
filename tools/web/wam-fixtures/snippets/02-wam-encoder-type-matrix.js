// Capture: wam-encoder-type-matrix
// Consumer:  WamEventEncoderKatTest
// Source:    WAWebWamLibProtocol
//
// Full event-with-fields sequences across (WamType x edge-value) combinations.
// Each row mirrors what a Cobalt *Impl.encode emits for the same eventId,
// weight, and field list.

(async () => {
  const Protocol = require('WAWebWamLibProtocol');
  const Binary = require('WABinary').Binary;

  const sha256Hex = async bytes => {
    const ab = new Uint8Array(bytes).buffer;
    const digest = await crypto.subtle.digest('SHA-256', ab);
    return Array.from(new Uint8Array(digest)).map(b => b.toString(16).padStart(2, '0')).join('');
  };

  const encodeSequence = (eventId, weight, fields) => {
    const buf = new Binary(undefined, true);
    Protocol.writeEvent(buf, eventId, weight, fields.length > 0);
    fields.forEach((f, idx) => Protocol.writeField(buf, f.id, f.value, idx < fields.length - 1));
    return buf.peek(v => Array.from(v.readByteArrayView()));
  };

  const cases = [
    { name: 'event_no_fields_negative_weight',  eventId: 100,  weight: -1,    fields: [] },
    { name: 'event_no_fields_wide_id',          eventId: 5000, weight: -100,  fields: [] },
    { name: 'event_single_int_field',           eventId: 100,  weight: -1,    fields: [{ id: 1, value: 42, type: 'int' }] },
    { name: 'event_single_str_field',           eventId: 100,  weight: -1,    fields: [{ id: 1, value: 'hello', type: 'str' }] },
    { name: 'event_single_bool_true_field',     eventId: 100,  weight: -1,    fields: [{ id: 1, value: 1, type: 'bool' }] },
    { name: 'event_single_bool_false_field',    eventId: 100,  weight: -1,    fields: [{ id: 1, value: 0, type: 'bool' }] },
    { name: 'event_single_null_field',          eventId: 100,  weight: -1,    fields: [{ id: 1, value: null, type: 'null' }] },
    { name: 'event_single_float_field',         eventId: 100,  weight: -1,    fields: [{ id: 1, value: 3.14, type: 'float' }] },
    { name: 'event_int_boundary_int0',          eventId: 100,  weight: -1,    fields: [{ id: 7, value: 0, type: 'int' }] },
    { name: 'event_int_boundary_int1',          eventId: 100,  weight: -1,    fields: [{ id: 7, value: 1, type: 'int' }] },
    { name: 'event_int_boundary_int8_max',      eventId: 100,  weight: -1,    fields: [{ id: 7, value: 127, type: 'int' }] },
    { name: 'event_int_boundary_int16_min',     eventId: 100,  weight: -1,    fields: [{ id: 7, value: -32768, type: 'int' }] },
    { name: 'event_int_boundary_int32_min',     eventId: 100,  weight: -1,    fields: [{ id: 7, value: -2147483648, type: 'int' }] },
    { name: 'event_int_boundary_int32_max',     eventId: 100,  weight: -1,    fields: [{ id: 7, value: 2147483647, type: 'int' }] },
    { name: 'event_str_empty',                  eventId: 100,  weight: -1,    fields: [{ id: 9, value: '', type: 'str' }] },
    { name: 'event_str_utf8',                   eventId: 100,  weight: -1,    fields: [{ id: 9, value: 'héllo' + String.fromCodePoint(128512), type: 'str' }] },
    { name: 'event_str_boundary_255',           eventId: 100,  weight: -1,    fields: [{ id: 9, value: 'a'.repeat(255), type: 'str' }] },
    { name: 'event_str_boundary_256',           eventId: 100,  weight: -1,    fields: [{ id: 9, value: 'a'.repeat(256), type: 'str' }] },
    { name: 'event_mixed_int_str_bool_null',    eventId: 100,  weight: -1,    fields: [
        { id: 1, value: 42, type: 'int' }, { id: 2, value: 'hello', type: 'str' },
        { id: 3, value: 1, type: 'bool' }, { id: 4, value: null, type: 'null' }
    ] },
    { name: 'event_dense_low_field_ids',        eventId: 100,  weight: -1,    fields: [
        { id: 1, value: 10, type: 'int' }, { id: 2, value: 20, type: 'int' },
        { id: 3, value: 30, type: 'int' }, { id: 4, value: 40, type: 'int' },
        { id: 5, value: 50, type: 'int' }
    ] },
    { name: 'event_wide_and_narrow_field_ids',  eventId: 100,  weight: -1,    fields: [
        { id: 5, value: 'narrow', type: 'str' },
        { id: 500, value: 'wide1', type: 'str' },
        { id: 50000, value: 'wide2', type: 'str' }
    ] },
    { name: 'event_many_null_fields',           eventId: 100,  weight: -1,    fields: [
        { id: 1, value: null, type: 'null' },
        { id: 2, value: null, type: 'null' },
        { id: 3, value: null, type: 'null' }
    ] },
    { name: 'event_weight_default',             eventId: 100,  weight: 0,     fields: [{ id: 1, value: 1, type: 'int' }] },
    { name: 'event_negative_weight_high_magnitude', eventId: 100, weight: -100000, fields: [{ id: 1, value: 1, type: 'int' }] }
  ];

  const INLINE_LIMIT = 256;
  const PREFIX_BYTES = 32;
  const vectors = [];
  for (const c of cases) {
    const bytes = encodeSequence(c.eventId, c.weight, c.fields);
    const hex = bytes.map(b => b.toString(16).padStart(2, '0')).join('');
    const fieldsSummary = c.fields.map(f => typeof f.value === 'string' && f.value.length > 16
      ? { ...f, value: { kind: 'str', length: f.value.length, head: f.value.slice(0, 8) } }
      : f);
    const base = { name: c.name, eventId: c.eventId, weight: c.weight, fields: fieldsSummary, byteLength: bytes.length };
    if (bytes.length <= INLINE_LIMIT) {
      vectors.push({ ...base, bytes: hex });
    } else {
      vectors.push({ ...base, prefix: hex.slice(0, PREFIX_BYTES * 2), sha256: await sha256Hex(bytes) });
    }
  }

  return JSON.stringify({
    fixture: 'wam-encoder-type-matrix',
    description: 'Live JS encoding of complete event-with-fields sequences across (WamType x edge-value) combinations. Each row reproduces what Cobalt *Impl.encode emits for the same eventId, weight, and field list.',
    snapshotRevision: 1039260921,
    liveRuntimeRevision: 1039260921,
    capturedAt: '2026-05-12',
    capturedVia: 'mcp__whatsapp__web_live_debug_eval_to_file',
    sourceModule: 'WAWebWamLibProtocol',
    encoding: { inlineLimitBytes: INLINE_LIMIT, prefixBytes: PREFIX_BYTES, hashAlgorithm: 'sha-256' },
    vectors
  }, null, 2);
})()
