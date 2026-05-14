// Capture: wam-buffer-headers
// Consumer:  WamBufferHeaderKatTest
// Source:    WAWebWamLibContext + WAWebWamConstants
//
// 8-byte WAM buffer header for every (channelByte, seqNum) combination
// Cobalt cares about. Cobalt WamService.writeHeader must produce identical
// bytes for the same channelByte+seqNum pair.

(() => {
  const Binary = require('WABinary').Binary;
  const constants = require('WAWebWamConstants');

  const buildHeader = (seqNum, channelByte) => {
    const buf = new Binary(undefined, true);
    buf.writeString('WAM');
    buf.writeUint8(constants.WAM_PROTOCOL_VERSION);
    buf.writeUint8(1);
    buf.writeUint16(seqNum);
    buf.writeUint8(channelByte);
    const arr = buf.peek(v => Array.from(v.readByteArrayView()));
    return arr.map(b => b.toString(16).padStart(2, '0')).join('');
  };

  const channelByteName = b => ({ 0: 'REGULAR', 1: 'REALTIME', 2: 'PRIVATE' })[b];

  const rows = [];
  for (const ch of [0, 1, 2]) {
    for (const seq of [1, 2, 256, 0xFF, 0x100, 0xFFFE, 0xFFFF]) {
      rows.push({ channelByte: ch, channelName: channelByteName(ch), seqNum: seq, bytes: buildHeader(seq, ch) });
    }
  }

  return JSON.stringify({
    fixture: 'wam-buffer-headers',
    description: 'The 8-byte WAM buffer header for every (channel, seqNum) combination Cobalt cares about. WAM_PROTOCOL_VERSION pinned to ' + constants.WAM_PROTOCOL_VERSION + ' at capture time.',
    snapshotRevision: 1039260921,
    liveRuntimeRevision: 1039260921,
    capturedAt: '2026-05-12',
    capturedVia: 'mcp__whatsapp__web_live_debug_eval_to_file',
    sourceModule: 'WAWebWamLibContext + WAWebWamConstants',
    protocolVersion: constants.WAM_PROTOCOL_VERSION,
    vectors: rows
  }, null, 2);
})()
