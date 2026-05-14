// Capture: wam-tags-roundtrip
// Consumer:  WamTagsKatTest
// Source:    WAWebWamLibProtocol.{writeGlobalAttribute,writeEvent,writeField}
//
// Snippet to pass as `expression` to mcp__whatsapp__web_live_debug_eval_to_file
// (topic=wam-tags-roundtrip, outputPath=modules/lib/src/test/resources/fixtures/wam/wam-tags-roundtrip.json).
//
// Update snapshotRevision / liveRuntimeRevision below to match the live bundle when re-capturing.

(async () => {
  const Protocol = require('WAWebWamLibProtocol');
  const Binary = require('WABinary').Binary;

  const PREFIX_BYTES = 32;

  const captureBytes = (fn, args) => {
    const buf = new Binary(undefined, true);
    fn(buf, ...args);
    return buf.peek(v => Array.from(v.readByteArrayView()));
  };

  const sha256Hex = async bytes => {
    const ab = new Uint8Array(bytes).buffer;
    const digest = await crypto.subtle.digest('SHA-256', ab);
    return Array.from(new Uint8Array(digest)).map(b => b.toString(16).padStart(2, '0')).join('');
  };

  const G = Protocol.writeGlobalAttribute;
  const E = (b, id, weight, hasFields) => Protocol.writeEvent(b, id, weight, hasFields);
  const F = (b, id, value, hasFollowing) => Protocol.writeField(b, id, value, hasFollowing);

  const tinyId = 5;
  const wideId = 300;

  const rows = [
    { name: 'global_null_tiny',  fn: G, args: [tinyId, null] },
    { name: 'global_null_wide',  fn: G, args: [wideId, null] },
    { name: 'global_int0_tiny',  fn: G, args: [tinyId, 0] },
    { name: 'global_int1_tiny',  fn: G, args: [tinyId, 1] },
    { name: 'global_int8_pos',   fn: G, args: [tinyId, 5] },
    { name: 'global_int8_neg',   fn: G, args: [tinyId, -7] },
    { name: 'global_int8_min',   fn: G, args: [tinyId, -128] },
    { name: 'global_int8_max',   fn: G, args: [tinyId, 127] },
    { name: 'global_int16_pos',  fn: G, args: [tinyId, 200] },
    { name: 'global_int16_neg',  fn: G, args: [tinyId, -200] },
    { name: 'global_int16_min',  fn: G, args: [tinyId, -32768] },
    { name: 'global_int16_max',  fn: G, args: [tinyId, 32767] },
    { name: 'global_int32_pos',  fn: G, args: [tinyId, 70000] },
    { name: 'global_int32_neg',  fn: G, args: [tinyId, -70000] },
    { name: 'global_int32_min',  fn: G, args: [tinyId, -2147483648] },
    { name: 'global_int32_max',  fn: G, args: [tinyId, 2147483647] },
    { name: 'global_float_pi',   fn: G, args: [tinyId, 3.14] },
    { name: 'global_float_neg',  fn: G, args: [tinyId, -2.5] },
    { name: 'global_float_big',  fn: G, args: [tinyId, 1e15] },
    { name: 'global_float_small',fn: G, args: [tinyId, 1.5e-10] },
    { name: 'global_float_half', fn: G, args: [tinyId, 0.5] },
    { name: 'global_str_empty',  fn: G, args: [tinyId, ''] },
    { name: 'global_str_ascii',  fn: G, args: [tinyId, 'hi'] },
    { name: 'global_str_utf8',   fn: G, args: [tinyId, 'héllo' + String.fromCodePoint(128512)] },
    { name: 'global_str_255',    fn: G, args: [tinyId, 'a'.repeat(255)] },
    { name: 'global_str_256',    fn: G, args: [tinyId, 'a'.repeat(256)] },
    { name: 'global_str_65535',  fn: G, args: [tinyId, 'a'.repeat(65535)] },
    { name: 'global_str_65536',  fn: G, args: [tinyId, 'a'.repeat(65536)] },

    { name: 'event_no_fields_tiny',  fn: E, args: [tinyId, -100, false] },
    { name: 'event_has_fields_tiny', fn: E, args: [tinyId, -100, true] },
    { name: 'event_no_fields_wide',  fn: E, args: [wideId, -1, false] },
    { name: 'event_has_fields_wide', fn: E, args: [wideId, -1, true] },

    { name: 'field_null_last',      fn: F, args: [tinyId, null, false] },
    { name: 'field_null_continues', fn: F, args: [tinyId, null, true] },
    { name: 'field_int0_last',      fn: F, args: [tinyId, 0, false] },
    { name: 'field_int1_last',      fn: F, args: [tinyId, 1, false] },
    { name: 'field_int8_last',      fn: F, args: [tinyId, 7, false] },
    { name: 'field_int16_last',     fn: F, args: [tinyId, 200, false] },
    { name: 'field_int32_last',     fn: F, args: [tinyId, 70000, false] },
    { name: 'field_float_last',     fn: F, args: [tinyId, 3.14, false] },
    { name: 'field_str8_last',      fn: F, args: [tinyId, 'hi', false] },
    { name: 'field_str16_last',     fn: F, args: [tinyId, 'a'.repeat(300), false] },
    { name: 'field_str8_continues', fn: F, args: [tinyId, 'x', true] },
    { name: 'field_wide_int8_last', fn: F, args: [wideId, 7, false] },
    { name: 'field_wide_str8_last', fn: F, args: [wideId, 'hi', false] }
  ];

  const INLINE_LIMIT = 256;
  const vectors = [];
  for (const row of rows) {
    const bytes = captureBytes(row.fn, row.args);
    const hex = bytes.map(b => b.toString(16).padStart(2, '0')).join('');
    const summarizedArgs = row.args.map(a => typeof a === 'string' && a.length > 16
        ? { kind: 'str', length: a.length, head: a.slice(0, 8) }
        : a);
    if (bytes.length <= INLINE_LIMIT) {
      vectors.push({ name: row.name, role: row.fn.name || null, args: summarizedArgs, byteLength: bytes.length, bytes: hex });
    } else {
      const prefix = hex.slice(0, PREFIX_BYTES * 2);
      const sha256 = await sha256Hex(bytes);
      vectors.push({ name: row.name, role: row.fn.name || null, args: summarizedArgs, byteLength: bytes.length, prefix, sha256 });
    }
  }

  return JSON.stringify({
    fixture: 'wam-tags-roundtrip',
    description: 'Byte output of WAWebWamLibProtocol.{writeGlobalAttribute,writeEvent,writeField} across role bits, LAST/WIDE_ID flags, and every value-type mask. Vectors with byteLength > 256 store prefix + sha256 instead of full bytes.',
    snapshotRevision: 1039260921,
    liveRuntimeRevision: 1039260921,
    capturedAt: '2026-05-12',
    capturedVia: 'mcp__whatsapp__web_live_debug_eval_to_file',
    sourceModule: 'WAWebWamLibProtocol',
    encoding: { inlineLimitBytes: INLINE_LIMIT, prefixBytes: PREFIX_BYTES, hashAlgorithm: 'sha-256' },
    vectors
  }, null, 2);
})()
