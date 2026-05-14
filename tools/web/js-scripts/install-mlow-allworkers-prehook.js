#!/usr/bin/env node
/**
 * Per-worker MLow capture installer. Unlike the page-only variant
 * (install-mlow-prehook.js), this tool attaches to the BROWSER-level
 * CDP endpoint, enables `Target.setAutoAttach` with
 * `waitForDebuggerOnStart: true`, and installs hook scripts in every
 * spawned worker before its main script executes. Necessary because
 * WhatsApp Web's Voip codec lives entirely in
 * `WAWebVoipWebWasmWorkerBundle` workers — main-thread hooks see
 * nothing of the audio bytestream.
 *
 * After the page reloads under this installer, every worker target
 * exposes its own `__capturedSends` / `__mlowDumpCorpus` global, and
 * the page exposes `__mlowAggregateCorpus()` which messages all
 * workers and concatenates their results.
 *
 * Usage: node install-mlow-allworkers-prehook.js [cdpPort]
 */
'use strict';

const cdpPort = process.argv[2] ? parseInt(process.argv[2], 10) : 55006;

// Hook source shared between page and workers. In the page, it
// installs the WebRTC + WhatsApp-bundle hooks plus an aggregator
// that broadcasts a "dump" request to all workers via
// BroadcastChannel and concatenates their replies. In workers, the
// same script wires the WebRTC primitives + the WASM env-import
// path the codec actually uses (call_sendto), and exposes a per-
// worker dump helper that listens on the same BroadcastChannel.
const HOOK = `
(function preHook() {
    const isWorker = typeof window === 'undefined';
    const G = isWorker ? self : window;
    if (G.__mlowPrehookInstalled) return;
    G.__mlowPrehookInstalled = true;
    G.__mlowSide = isWorker ? 'worker' : 'page';
    G.__capturedSends = [];
    G.__capturedAudioIn = [];

    // ----- SharedArrayBuffer ring (lock-free, no event-loop dep) ----
    // The codec workers spend their whole life inside one long WASM
    // call, so the page can never read JS state from them via CDP.
    // Solution: at worker pre-hook time (BEFORE WASM starts), allocate
    // a SAB. Inside our wrapped env imports we synchronously write
    // capture entries into the SAB using Atomics. The page-side hook
    // wraps the Worker constructor and snags the SAB reference at worker creation
    // (the worker posts {sab,hdr} as the very first message), then
    // can drain the ring at any time — even while the worker is
    // 100% busy in WASM.
    const SAB_SLOTS = 4096;
    const SLOT_SIZE = 512; // 4 (taglen) + 16 (tag) + 4 (paylen) + 8 (ts) + up to 480 bytes payload
    // Header layout (i32-indexed):
    //   [0..7]    base ring stats (slotsUsed, dropped, version,
    //             slotSize, slots).
    //   [8]       replay-control magic (0xC0BACAFE) — set once by
    //             the worker on hook install so the page can verify
    //             before issuing requests.
    //   [9]       request_seq  — page atomic-increments this when
    //             writing a new replay request.
    //   [10]      response_seq — worker atomic-increments this when
    //             writing the response. Page polls for change.
    //   [11]      decoder_slot (e.g. 5891).
    //   [12]      encoded_len  (bytes in the request payload).
    //   [13]      sample_rate_hz.
    //   [14]      response_status (0=ok, 1=worker-error,
    //             2=no-refs).
    //   [15]      response_samples (int16 sample count returned).
    //   [16..79]  encoded request payload (256 bytes — covers the
    //             18..224-byte MLow SPEECH cluster with margin).
    //   [80..199] response PCM payload (480 bytes — 240 int16
    //             samples; matches the 242-sample wideband output
    //             with the 2-sample padding stripped).
    //   [200]     sweep_mode: 0=idle, 1=running, 2=done.
    //   [201]     sweep_bit_index: next bit to flip.
    //   [202]     sweep_total_bits: encoded_len * 8.
    //   [203]     sweep_state_ptr: WASM-heap address where the
    //             decoder's state was snapshotted before sweep
    //             began. Worker restores from here on completion.
    //   [204]     sweep_state_size: bytes snapshotted.
    const HDR_INTS = 256;
    const HDR_CTRL_MAGIC = 0xC0BACAFE | 0;
    const HDR_OFF_MAGIC = 8;
    const HDR_OFF_REQ_SEQ = 9;
    const HDR_OFF_RESP_SEQ = 10;
    const HDR_OFF_DECODER_SLOT = 11;
    const HDR_OFF_ENC_LEN = 12;
    const HDR_OFF_SWEEP_MODE = 200;
    const HDR_OFF_SWEEP_BIT_INDEX = 201;
    const HDR_OFF_SWEEP_TOTAL_BITS = 202;
    // Sweep tuning: K mutations per cb_pre6 invocation. Higher
    // K = faster sweep, but more orig() calls per real audio
    // frame. K=2 empirically captured 1156 bits of a 1744-bit
    // frame before the decoder migrated; K=1 only 64 bits in
    // testing because the migration window stays the same so
    // halving the rate just halves coverage.
    const SWEEP_K_PER_CYCLE = 2;
    const HDR_OFF_SAMPLE_RATE = 13;
    const HDR_OFF_RESP_STATUS = 14;
    const HDR_OFF_RESP_SAMPLES = 15;
    const HDR_OFF_ENC_PAYLOAD = 16;     // 64 i32s = 256 bytes
    const HDR_ENC_PAYLOAD_SIZE = 256;
    const HDR_OFF_PCM_PAYLOAD = 80;     // 120 i32s = 480 bytes
    const HDR_PCM_PAYLOAD_SIZE = 480;
    let sab = null, hdr = null, ringU8 = null, ringHdrI32 = null;
    G.__mlowSabErr = null;
    G.__mlowSabAvail = typeof SharedArrayBuffer;
    G.__mlowCrossOriginIsolated = (typeof crossOriginIsolated !== 'undefined') ? crossOriginIsolated : 'undef';
    // Try direct SAB first; fall back to WebAssembly.Memory({shared:true})
    // whose .buffer is a SAB even when the SharedArrayBuffer ctor is gated.
    if (typeof SharedArrayBuffer !== 'undefined') {
        try {
            sab = new SharedArrayBuffer(SAB_SLOTS * SLOT_SIZE);
            hdr = new SharedArrayBuffer(HDR_INTS * 4);
        } catch (e) { sab = null; G.__mlowSabErr = 'sab-direct: ' + e.message; }
    }
    if (!sab && typeof WebAssembly !== 'undefined' && WebAssembly.Memory) {
        try {
            // 64KiB pages. Need ceil((SAB_SLOTS*SLOT_SIZE)/65536) = 32 pages for 2MiB
            const ringPages = Math.ceil((SAB_SLOTS * SLOT_SIZE) / 65536);
            const ringMem = new WebAssembly.Memory({initial: ringPages, maximum: ringPages, shared: true});
            const hdrMem = new WebAssembly.Memory({initial: 1, maximum: 1, shared: true});
            sab = ringMem.buffer;
            hdr = hdrMem.buffer;
            G.__mlowRingMem = ringMem;
            G.__mlowHdrMem = hdrMem;
            G.__mlowSabAvail = 'wasm-memory';
        } catch (e) {
            sab = null;
            G.__mlowSabErr = (G.__mlowSabErr || '') + ' | wasm-mem: ' + e.message;
        }
    }
    if (sab && hdr) {
        ringU8 = new Uint8Array(sab);
        ringHdrI32 = new Int32Array(hdr);
        ringHdrI32[2] = 1;
        ringHdrI32[3] = SLOT_SIZE;
        ringHdrI32[4] = SAB_SLOTS;
        // Stamp the replay-control magic so the page-side
        // request helper can verify the worker's prehook
        // version supports SAB-based replay before writing.
        Atomics.store(ringHdrI32, HDR_OFF_MAGIC, HDR_CTRL_MAGIC);
        G.__mlowSab = sab;
        G.__mlowHdr = hdr;
    }
    const TE = new TextEncoder();
    const ringPush = (tag, bytes) => {
        if (!sab || !ringHdrI32) return;
        // While a bit-flip sweep is active, suppress every push
        // that is not a sweep mutation result so the 4096-slot
        // ring stays available for the up-to-totalBits mut@/
        // muterr@ entries the sweep state machine needs to
        // deposit. Cipher 4820 in/out and the Decode trampoline's
        // env-cb_pre6/orig/post + enc/pcm pushes would otherwise
        // overflow the ring well before the sweep can finish.
        if (Atomics.load(ringHdrI32, HDR_OFF_SWEEP_MODE) === 1
                && tag.charCodeAt(0) !== 109 /* 'm' */) {
            return;
        }
        const slot = Atomics.add(ringHdrI32, 0, 1);
        if (slot >= SAB_SLOTS) {
            Atomics.add(ringHdrI32, 1, 1); // dropped
            return;
        }
        const off = slot * SLOT_SIZE;
        const tagBytes = TE.encode(tag);
        const tagLen = Math.min(tagBytes.length, 16);
        const payLen = Math.min(bytes.length, SLOT_SIZE - 28);
        const ts = Date.now();
        // Layout: [taglen:u32][tag:16][paylen:u32][ts_lo:u32][ts_hi:u32][payload:up to 480]
        const dv = new DataView(sab, off, SLOT_SIZE);
        dv.setUint32(0, tagLen, true);
        for (let i = 0; i < tagLen; i++) ringU8[off + 4 + i] = tagBytes[i];
        dv.setUint32(20, payLen, true);
        dv.setUint32(24, ts & 0xffffffff, true);
        dv.setUint32(28, Math.floor(ts / 0x100000000), true);
        for (let i = 0; i < payLen; i++) ringU8[off + 32 + i] = bytes[i];
    };
    G.__mlowRingPush = ringPush;
    const b64 = (u8) => { let s = ''; for (let i = 0; i < u8.length; i++) s += String.fromCharCode(u8[i]); return btoa(s); };
    const toBytes = (data) => {
        if (data instanceof Uint8Array) return data;
        if (data instanceof ArrayBuffer) return new Uint8Array(data);
        if (ArrayBuffer.isView(data)) return new Uint8Array(data.buffer, data.byteOffset, data.byteLength);
        if (typeof data === 'string') return new TextEncoder().encode(data);
        return new Uint8Array(0);
    };
    const recordSend = (data, ip, port, source) => {
        try {
            const bytes = toBytes(data);
            if (bytes.length === 0) return;
            const copy = new Uint8Array(bytes.length); copy.set(bytes);
            G.__capturedSends.push({ts: Date.now(), ip: ip || 0, port: port || 0, source, bytes: copy});
            // Mirror to SAB ring so the page-side drainer can read
            // captures even when the worker JS event loop is stuck
            // inside a long-running WASM call. Audio frames at ~20–
            // 200 B fit in our 480-B-payload slots; everything
            // above SLOT_SIZE-32 is truncated in the slot.
            try { if (typeof ringPush === 'function') ringPush(source, copy); } catch (e) {}
        } catch (e) {}
    };

    // ----- Browser-primitive hooks (sync, before any other script). -----
    // Synthesised-mic injection. Replaces navigator.mediaDevices
    // .getUserMedia for audio:true requests with a deterministic
    // synthesised stream so the codec's encoder reads our chosen
    // input. This lets us probe how specific input shapes (sine
    // tones, formant patterns, impulses) map to encoded MLow
    // bits — direct inverse of the bitflip-on-decoder oracle.
    //
    // Default synth: 440 Hz sine at gain 0.5. Reconfigurable
    // from the page via window.__mlowSetSynth({...}). Only the
    // OUTGOING audio is replaced; the page's other capabilities
    // are unaffected.
    //
    // The synth state is per-page because Web Audio lives on the
    // page main thread; workers cannot inject mic input.
    if (!isWorker && G.navigator && G.navigator.mediaDevices) {
        G.__mlowSynthSpec = {type: 'sine', frequency: 440, gain: 0.5};
        G.__mlowSynthState = null;
        G.__mlowSetSynth = function (spec) {
            G.__mlowSynthSpec = Object.assign({}, G.__mlowSynthSpec, spec || {});
            // If a stream is already live, retune the existing
            // oscillator so the next capture cycle picks up the
            // change without needing to re-issue getUserMedia.
            const st = G.__mlowSynthState;
            if (st && st.osc) {
                try {
                    if (G.__mlowSynthSpec.type === 'sine'
                            || G.__mlowSynthSpec.type === 'square'
                            || G.__mlowSynthSpec.type === 'sawtooth'
                            || G.__mlowSynthSpec.type === 'triangle') {
                        st.osc.type = G.__mlowSynthSpec.type;
                    }
                    if (typeof G.__mlowSynthSpec.frequency === 'number') {
                        st.osc.frequency.setValueAtTime(
                                G.__mlowSynthSpec.frequency,
                                st.ctx.currentTime);
                    }
                    if (typeof G.__mlowSynthSpec.gain === 'number'
                            && st.gainNode) {
                        st.gainNode.gain.setValueAtTime(
                                G.__mlowSynthSpec.gain,
                                st.ctx.currentTime);
                    }
                } catch (e) {}
            }
            return G.__mlowSynthSpec;
        };
        const buildSynthStream = () => {
            try {
                const ctx = new (G.AudioContext || G.webkitAudioContext)({
                    sampleRate: 48000
                });
                const osc = ctx.createOscillator();
                const spec = G.__mlowSynthSpec;
                osc.type = (spec.type === 'square' || spec.type === 'sawtooth'
                        || spec.type === 'triangle') ? spec.type : 'sine';
                osc.frequency.value = spec.frequency || 440;
                const gainNode = ctx.createGain();
                gainNode.gain.value = typeof spec.gain === 'number' ? spec.gain : 0.5;
                const dest = ctx.createMediaStreamDestination();
                osc.connect(gainNode).connect(dest);
                osc.start();
                G.__mlowSynthState = {ctx, osc, gainNode, dest};
                return dest.stream;
            } catch (e) {
                G.__mlowSynthErr = 'buildSynthStream: ' + e.message;
                return null;
            }
        };
        const md = G.navigator.mediaDevices;
        const origGUM = md.getUserMedia ? md.getUserMedia.bind(md) : null;
        md.getUserMedia = function (constraints) {
            // Only intercept audio-only / audio-and-video calls
            // when audio is enabled. Video tracks pass through
            // to the original (we don't synthesize video).
            if (!constraints || !constraints.audio) {
                if (origGUM) return origGUM(constraints);
                return Promise.reject(new Error('no original getUserMedia'));
            }
            const synthStream = buildSynthStream();
            if (!synthStream) {
                if (origGUM) return origGUM(constraints);
                return Promise.reject(new Error('synth failed and no fallback'));
            }
            // If video was also requested, splice in a real
            // video track from the original.
            if (constraints.video && origGUM) {
                return origGUM({video: constraints.video}).then(function (videoStream) {
                    videoStream.getVideoTracks().forEach(function (t) {
                        synthStream.addTrack(t);
                    });
                    return synthStream;
                }).catch(function () {
                    return synthStream;
                });
            }
            return Promise.resolve(synthStream);
        };
    }

    if (G.RTCDataChannel) {
        const proto = G.RTCDataChannel.prototype;
        const orig = proto.send;
        proto.send = function (data) { recordSend(data, 0, 0, 'rtcdc'); return orig.apply(this, arguments); };
    }
    if (G.WebSocket) {
        const proto = G.WebSocket.prototype;
        const orig = proto.send;
        proto.send = function (data) {
            if (data && data.byteLength > 60) recordSend(data, 0, 0, 'ws');
            return orig.apply(this, arguments);
        };
    }

    // WebTransport — the only outbound network primitive available
    // in WhatsApp's Voip workers (RTCPeerConnection and
    // RTCRtpScriptTransform are NOT exposed in those worker
    // contexts; verified by inspection). Audio almost certainly
    // ships through WebTransport.datagrams.writable or a
    // bidirectional stream's writer.
    if (G.WebTransport) {
        const OrigWT = G.WebTransport;
        G.__mlowWtInstances = [];
        const Wrapped = function (url, opts) {
            const wt = new OrigWT(url, opts);
            G.__mlowWtInstances.push({url: String(url), createdAt: Date.now()});
            try {
                // Wrap datagrams.writable getter so any send goes through us.
                const origDatagrams = wt.datagrams;
                if (origDatagrams && origDatagrams.writable) {
                    const origWritable = origDatagrams.writable;
                    const tap = new TransformStream({
                        transform(chunk, controller) {
                            try {
                                const u = chunk instanceof Uint8Array ? chunk
                                    : (chunk && chunk.buffer) ? new Uint8Array(chunk.buffer, chunk.byteOffset || 0, chunk.byteLength)
                                    : chunk instanceof ArrayBuffer ? new Uint8Array(chunk)
                                    : null;
                                if (u && u.length > 0) {
                                    ringPush('wt-datagram', u);
                                    G.__capturedSends.push({ts: Date.now(), source: 'wt-datagram', bytes: new Uint8Array(u)});
                                }
                            } catch {}
                            controller.enqueue(chunk);
                        }
                    });
                    tap.readable.pipeTo(origWritable).catch(() => {});
                    Object.defineProperty(origDatagrams, 'writable', {get: () => tap.writable, configurable: true});
                }
                // Also wrap stream creation paths
                ['createBidirectionalStream', 'createUnidirectionalStream'].forEach((method) => {
                    if (typeof wt[method] === 'function') {
                        const orig = wt[method].bind(wt);
                        wt[method] = function (...args) {
                            const p = orig(...args);
                            return Promise.resolve(p).then((stream) => {
                                if (stream && stream.writable) {
                                    const origWritable = stream.writable;
                                    const tap = new TransformStream({
                                        transform(chunk, controller) {
                                            try {
                                                const u = chunk instanceof Uint8Array ? chunk
                                                    : chunk instanceof ArrayBuffer ? new Uint8Array(chunk)
                                                    : null;
                                                if (u && u.length > 0) {
                                                    ringPush('wt-stream', u);
                                                    G.__capturedSends.push({ts: Date.now(), source: 'wt-stream', bytes: new Uint8Array(u)});
                                                }
                                            } catch {}
                                            controller.enqueue(chunk);
                                        }
                                    });
                                    tap.readable.pipeTo(origWritable).catch(() => {});
                                    Object.defineProperty(stream, 'writable', {get: () => tap.writable, configurable: true});
                                }
                                return stream;
                            });
                        };
                    }
                });
            } catch {}
            return wt;
        };
        Wrapped.prototype = OrigWT.prototype;
        G.WebTransport = Wrapped;
    }

    // fetch — fallback if audio is shipped via HTTP/3
    if (typeof G.fetch === 'function') {
        const orig = G.fetch.bind(G);
        G.fetch = function (input, init) {
            try {
                if (init && init.body && (init.body.byteLength > 0)) {
                    const u = init.body instanceof Uint8Array ? init.body
                        : init.body instanceof ArrayBuffer ? new Uint8Array(init.body)
                        : null;
                    if (u && u.length >= 20 && u.length <= 4096) {
                        ringPush('fetch-body', u);
                    }
                }
            } catch {}
            return orig(input, init);
        };
    }

    // Worker→page postMessage. The MLow codec runs in a worker;
    // encoded packets are likely ferried back to the page over
    // postMessage to be fed into RTCRtpSender. Capture every
    // small binary payload — frame-sized packets (40–400 B) are
    // strong codec-frame candidates.
    const recordPostMessage = (msg, source) => {
        try {
            if (!msg) return;
            const tryOne = (v) => {
                if (v instanceof ArrayBuffer && v.byteLength > 0 && v.byteLength < 4096) {
                    recordSend(v, 0, 0, source);
                } else if (ArrayBuffer.isView(v) && v.byteLength > 0 && v.byteLength < 4096) {
                    recordSend(v, 0, 0, source);
                }
            };
            tryOne(msg);
            if (msg && typeof msg === 'object') {
                for (const k of Object.keys(msg)) {
                    const v = msg[k];
                    tryOne(v);
                    if (v && typeof v === 'object' && !ArrayBuffer.isView(v) && !(v instanceof ArrayBuffer)) {
                        for (const k2 of Object.keys(v)) tryOne(v[k2]);
                    }
                }
            }
        } catch (e) {}
    };
    if (isWorker && typeof G.postMessage === 'function') {
        const origSelf = G.postMessage.bind(G);
        G.postMessage = function (m, ...rest) {
            recordPostMessage(m, 'worker-self-postMessage');
            return origSelf(m, ...rest);
        };
    }
    if (G.MessagePort && G.MessagePort.prototype && G.MessagePort.prototype.postMessage) {
        const orig = G.MessagePort.prototype.postMessage;
        G.__mlowVoipInitInjections = G.__mlowVoipInitInjections || [];
        G.MessagePort.prototype.postMessage = function (m) {
            // Inject postfilter-enabling AB-props into the WorkerProxy's
            // voipInit RPC payload before it crosses to the worker.
            // The codec gates LPC postfilter on the WebRTC field trial
            // WebRTC-MLowDecoder-lpcPostfilterMode; without flipping
            // p->mlow_enable_lpc_postfilter via setABPropBool, the
            // codec never calls get_bwe_ml_model_path_js to fetch the
            // ExecuTorch postfilter model, and capture stays empty.
            // The wasmKey list below covers all candidate names from
            // the WASM strings table (mlow_enable_lpc_postfilter,
            // mlow_post_filter, lpcPostfilterMode); unknown keys
            // no-op silently in setABPropsOnWasm.
            try {
                if (!isWorker && m && typeof m === 'object'
                        && m.method === 'voipInit'
                        && m.args && m.args.abProps) {
                    m.args.abProps.mlow_enable_lpc_postfilter =
                            {value: true, type: 'bool'};
                    m.args.abProps.mlow_post_filter =
                            {value: true, type: 'bool'};
                    m.args.abProps.lpcPostfilterMode =
                            {value: 1, type: 'int'};
                    m.args.abProps.mlow_lpc_postfilter_mode =
                            {value: 1, type: 'int'};
                    G.__mlowVoipInitInjections.push({ts: Date.now(),
                            keysCount: Object.keys(m.args.abProps).length});
                }
            } catch (e) {}
            recordPostMessage(m, isWorker ? 'mport-worker' : 'mport-page');
            return orig.apply(this, arguments);
        };
    }
    if (!isWorker && G.Worker && G.Worker.prototype && G.Worker.prototype.postMessage) {
        const orig = G.Worker.prototype.postMessage;
        G.Worker.prototype.postMessage = function (m) {
            recordPostMessage(m, 'page-to-worker');
            return orig.apply(this, arguments);
        };
    }
    if (G.WritableStreamDefaultWriter) {
        const orig = G.WritableStreamDefaultWriter.prototype.write;
        G.WritableStreamDefaultWriter.prototype.write = function (chunk) {
            recordSend(chunk, 0, 0, 'wt-stream');
            return orig.apply(this, arguments);
        };
    }
    const captureFrame = (frame, source) => {
        if (!frame || !frame.data) return;
        try {
            const buf = frame.data;
            const bytes = buf instanceof ArrayBuffer ? new Uint8Array(buf) : new Uint8Array(buf.buffer, buf.byteOffset, buf.byteLength);
            const copy = new Uint8Array(bytes.length); copy.set(bytes);
            const ts = (typeof frame.timestamp === 'number') ? frame.timestamp : Date.now();
            G.__capturedSends.push({ts: Date.now(), rtpTs: ts, ip: 0, port: 0, source, bytes: copy});
        } catch (e) {}
    };
    if (G.RTCRtpSender && typeof G.RTCRtpSender.prototype.createEncodedStreams === 'function') {
        const orig = G.RTCRtpSender.prototype.createEncodedStreams;
        G.RTCRtpSender.prototype.createEncodedStreams = function () {
            const streams = orig.apply(this, arguments);
            try {
                const tap = new TransformStream({ transform(frame, controller) { captureFrame(frame, 'rtp-out'); controller.enqueue(frame); } });
                tap.readable.pipeTo(streams.writable).catch(() => {});
                return {readable: streams.readable, writable: tap.writable};
            } catch (e) { return streams; }
        };
    }
    // MediaStreamTrackProcessor/Generator — workers use these to
    // read PCM frames in / write decoded PCM frames out. Wrap the
    // ReadableStream readable side and the WritableStream writable
    // side so we can fingerprint sample rate / channel count / frame
    // bytes flowing through.
    const tapAudioFrame = (frame, source) => {
        try {
            if (!frame) return;
            // AudioData: numberOfChannels, sampleRate, format, numberOfFrames
            const meta = {
                source,
                ts: Date.now(),
                rate: frame.sampleRate || 0,
                channels: frame.numberOfChannels || 0,
                format: frame.format || '',
                samples: frame.numberOfFrames || 0
            };
            G.__capturedAudioIn.push(meta);
        } catch (e) {}
    };
    if (isWorker && G.MediaStreamTrackProcessor) {
        const Orig = G.MediaStreamTrackProcessor;
        G.MediaStreamTrackProcessor = function (init) {
            const inst = new Orig(init);
            try {
                const origReadable = inst.readable;
                const tap = new TransformStream({ transform(chunk, controller) { tapAudioFrame(chunk, 'mstp-in'); controller.enqueue(chunk); } });
                origReadable.pipeTo(tap.writable).catch(() => {});
                Object.defineProperty(inst, 'readable', { get: () => tap.readable });
            } catch (e) {}
            return inst;
        };
    }
    if (isWorker && G.MediaStreamTrackGenerator) {
        const Orig = G.MediaStreamTrackGenerator;
        G.MediaStreamTrackGenerator = function (init) {
            const inst = new Orig(init);
            try {
                const origWritable = inst.writable;
                const tap = new TransformStream({ transform(chunk, controller) { tapAudioFrame(chunk, 'mstg-out'); controller.enqueue(chunk); } });
                tap.readable.pipeTo(origWritable).catch(() => {});
                Object.defineProperty(inst, 'writable', { get: () => tap.writable });
            } catch (e) {}
            return inst;
        };
    }
    if (G.RTCRtpReceiver && typeof G.RTCRtpReceiver.prototype.createEncodedStreams === 'function') {
        const orig = G.RTCRtpReceiver.prototype.createEncodedStreams;
        G.RTCRtpReceiver.prototype.createEncodedStreams = function () {
            const streams = orig.apply(this, arguments);
            try {
                const tap = new TransformStream({ transform(frame, controller) { captureFrame(frame, 'rtp-in'); controller.enqueue(frame); } });
                streams.readable.pipeTo(tap.writable).catch(() => {});
                return {readable: tap.readable, writable: streams.writable};
            } catch (e) { return streams; }
        };
    }

    // ----- Worker-only: hook WebAssembly.instantiate so we wrap the
    //                    env imports the codec asks for. The codec's
    //                    call_sendto goes via env.call_sendto — wrap
    //                    it so we capture every outbound packet
    //                    before it leaves the WASM.
    if (isWorker && G.WebAssembly && G.WebAssembly.instantiate) {
        G.__mlowWaSeen = 0;
        G.__mlowEnvKeys = [];
        G.__mlowEnvSendCandidates = [];
        G.WebAssembly.__mlowWrapped = true;
        const wrapEnv = (importObject) => {
            G.__mlowWaSeen = (G.__mlowWaSeen || 0) + 1;
            try {
                if (importObject && importObject.env) {
                    // Snapshot env.memory so the cipher table-swap
                    // hook (which runs after instantiate completes)
                    // can read WASM linear memory. WhatsApp's WASM
                    // uses an imported memory rather than exporting it.
                    if (importObject.env.memory) {
                        G.__lastMemory = importObject.env.memory;
                    }
                    const keys = Object.keys(importObject.env);
                    G.__mlowEnvKeys.push({total: keys.length, sample: keys});
                    // Wrap EVERY function in env. We'll filter sources
                    // post-hoc by buffer-size signature (40-300 B is
                    // MLow audio-frame range). Skip emscripten internals
                    // we know aren't audio paths to avoid noise/ overhead.
                    const skip = /^(__cxa_|invoke_|llvm_|__syscall_|_embind_|_emval_|emscripten_(get|date_|console_|num_|exit|asm_|check|resize|console))/;
                    // Wrapping every env import was observed to break the
                    // WebTransport relay handshake on rev 1039171941 — calls
                    // get stuck at "Connecting...". The slot 5891 / 4820
                    // vtable swaps below give us all the audio capture we
                    // need, so the broad env wrap is intentionally disabled.
                    // Re-enable by changing this to the original filter when
                    // diagnosing env-import audio paths on a snapshot where
                    // calls do connect through it.
                    const interesting = [];
                    G.__mlowEnvRef = G.__mlowEnvRef || importObject.env;
                    G.__mlowEnvSendCandidates.push(interesting);
                    G.__mlowEnvCallCounts = G.__mlowEnvCallCounts || {};
                    // Wrap every interesting one. Capture (ptr, len) pairs
                    // matching MLow audio-frame range (20-400 B). Maintain
                    // per-name call count so we can rank likely audio paths
                    // by call frequency (~50/s for 20ms frames).
                    for (const name of interesting) {
                        const fn = importObject.env[name];
                        if (typeof fn !== 'function') continue;
                        importObject.env[name] = function () {
                            G.__mlowEnvCallCounts[name] = (G.__mlowEnvCallCounts[name] || 0) + 1;
                            try {
                                const args = Array.from(arguments);
                                const memory = importObject.env.memory || G.__lastMemory;
                                if (memory && memory.buffer) {
                                    for (let i = 0; i < args.length - 1; i++) {
                                        const ptr = args[i] | 0;
                                        const len = args[i + 1] | 0;
                                        // Only capture audio-frame-sized buffers.
                                        if (ptr > 0 && len >= 20 && len <= 400 && ptr + len <= memory.buffer.byteLength) {
                                            const view = new Uint8Array(memory.buffer, ptr, len);
                                            // Push to SAB ring (synchronous,
                                            // event-loop-free) AND to JS array
                                            // for legacy paths.
                                            ringPush('env-' + name + '@' + i, view);
                                            const copy = new Uint8Array(view.length); copy.set(view);
                                            G.__capturedSends.push({ts: Date.now(), ip: 0, port: 0, source: 'env-' + name + '@' + i, bytes: copy});
                                        }
                                    }
                                }
                            } catch (e) {}
                            return fn.apply(this, arguments);
                        };
                    }
                }
            } catch (e) {}
            if (!importObject || typeof importObject !== 'object') return importObject;
            const env = importObject.env;
            if (!env || env.__mlowEnvWrapped) return importObject;
            if (typeof env.call_sendto === 'function') {
                const orig = env.call_sendto;
                env.call_sendto = function (handle, bufPtr, bufLen, addrPtr, addrLen) {
                    try {
                        // Read the buffer from WASM linear memory.
                        // We do not have a direct memory ref here —
                        // grab it from the importObject's memory if
                        // present, otherwise from the most recently
                        // instantiated module's memory. Cached on env.
                        const memory = env.memory || G.__lastMemory;
                        if (memory && memory.buffer && bufLen > 0 && bufPtr > 0) {
                            const view = new Uint8Array(memory.buffer, bufPtr, bufLen);
                            const copy = new Uint8Array(view.length); copy.set(view);
                            G.__capturedSends.push({ts: Date.now(), ip: addrPtr, port: 0, source: 'env-call_sendto', bytes: copy});
                        }
                    } catch (e) {}
                    return orig.apply(this, arguments);
                };
            }
            // Targeted wrap on env.get_bwe_ml_model_path_js — the
            // codec asks for the ExecuTorch postfilter model URL via
            // this env import. We want the URL string, not buffer
            // bytes. The wrap reads the returned char* from WASM
            // linear memory as a NUL-terminated UTF-8 string and
            // stores distinct hits on G.__mlowPostfilterUrls. The
            // SAB ring isn't used because URLs are page-side
            // observable and lock-free isn't needed at low
            // frequency.
            if (typeof env.get_bwe_ml_model_path_js === 'function') {
                const orig = env.get_bwe_ml_model_path_js;
                G.__mlowPostfilterUrlsSeen = G.__mlowPostfilterUrlsSeen || new Set();
                env.get_bwe_ml_model_path_js = function () {
                    const ret = orig.apply(this, arguments);
                    try {
                        const memory = env.memory || G.__lastMemory;
                        if (memory && memory.buffer && typeof ret === 'number' && ret > 0) {
                            const u8 = new Uint8Array(memory.buffer);
                            let end = ret;
                            const cap = Math.min(memory.buffer.byteLength, ret + 4096);
                            while (end < cap && u8[end] !== 0) end++;
                            const url = new TextDecoder('utf-8').decode(u8.slice(ret, end));
                            if (url && !G.__mlowPostfilterUrlsSeen.has(url)) {
                                G.__mlowPostfilterUrlsSeen.add(url);
                                // Push URL string bytes through the SAB
                                // ring tagged 'url@' so the page-side
                                // __mlowDrainAll picks it up like any
                                // other capture. URLs are typically
                                // 100-300 bytes — fits the SAB slot.
                                ringPush('url@bwe', new TextEncoder().encode(url));
                            }
                        }
                    } catch (e) {}
                    return ret;
                };
            }
            // Postfilter field-trial flip via env.invoke_iii
            // intercept. The codec gates the LPC postfilter on
            // a FindFullName lookup of the trial name string
            // at linear-mem 0x82E51. Static analysis pins the
            // only call site at function #3466 body offset
            // 0x2D8D, encoded as
            //   i32.const 263       table-slot of FindFullName
            //   local.get 4 + 180   std::string out param
            //   i32.const 0x82E51   trial-name pointer
            //   call 103            env.invoke_iii trampoline
            // and the result feeds a second invoke_iii with
            // table-slot 3045 that consumes the parsed string.
            // Wrapping env.invoke_iii globally was observed to
            // break the WebTransport relay handshake. To stay
            // SCOPED to the codec WASM, we only wrap when the
            // env carries the codec's own signature import
            // ({@code get_bwe_ml_model_path_js}) — so neither
            // the WebTransport worker's WASM nor any other
            // module's invoke_iii is affected. After the call
            // returns we rewrite the dest std::string to ASCII
            // "Enabled" in libc++ short-string layout (12-byte
            // struct, bytes 0..10 = inline data, byte 11 =
            // size with high bit clear for short mode), so the
            // upstream consumer reads "Enabled" and the codec
            // activates the postfilter, which then fires
            // get_bwe_ml_model_path_js for the postfilter .pte
            // URL — the URL hook above captures the result.
            // The postfilter trial-name string's linear-memory
            // address shifts per WASM revision (0x82E51 on rev
            // 1039137065, 0x82EE2 on rev 1039176331, etc.).
            // Detect it stateless on each invoke_iii call: if
            // a2 points at a string starting with the trial
            // name's first 8 bytes, treat it as the postfilter
            // trial. O(1) per call — no scan, no caching.
            // Only the postfilter trial-name string starts
            // with these bytes in the WASM's data section, so
            // false positives are negligible.
            const TRIAL_PREFIX_8 = new TextEncoder().encode(
                    'WebRTC-M');                              // 8 bytes
            const ENABLED_BYTES = new TextEncoder().encode('Enabled');
            const isCodecEnv = (typeof env.get_bwe_ml_model_path_js
                    === 'function');
            if (isCodecEnv && typeof env.invoke_iii === 'function') {
                const orig = env.invoke_iii;
                G.__mlowPostfilterFlips = G.__mlowPostfilterFlips || 0;
                env.invoke_iii = function (index, a1, a2) {
                    const ret = orig.call(this, index, a1, a2);
                    try {
                        const memory = env.memory || G.__lastMemory;
                        if (memory && memory.buffer
                                && typeof a2 === 'number' && a2 > 0
                                && a2 + 64 < memory.buffer.byteLength) {
                            const probe = new Uint8Array(
                                    memory.buffer, a2, 8);
                            let isWebRtc = true;
                            for (let i = 0; i < 8; i++) {
                                if (probe[i] !== TRIAL_PREFIX_8[i]) {
                                    isWebRtc = false;
                                    break;
                                }
                            }
                            if (isWebRtc) {
                                // Read full string to check it
                                // is the LPC postfilter trial.
                                const tail = new Uint8Array(
                                        memory.buffer, a2, 40);
                                let s = '';
                                for (let i = 0; i < 40
                                        && tail[i] !== 0; i++) {
                                    s += String.fromCharCode(tail[i]);
                                }
                                if (s === 'WebRTC-MLowDecoder-lpcPostfilterMode'
                                        && a1 > 0) {
                                    const u8 = new Uint8Array(
                                            memory.buffer, a1, 12);
                                    u8.fill(0);
                                    u8.set(ENABLED_BYTES);
                                    u8[11] = ENABLED_BYTES.length;
                                    G.__mlowPostfilterFlips++;
                                    ringPush('flip@postfilter',
                                            new TextEncoder().encode(
                                                    'wrote-Enabled@0x'
                                                            + a1.toString(16)
                                                            + ' trial@0x'
                                                            + a2.toString(16)));
                                }
                            }
                        }
                    } catch (e) {
                        ringPush('flip@error',
                                new TextEncoder().encode(
                                        String(e && e.message || e)));
                    }
                    return ret;
                };
            }
            env.__mlowEnvWrapped = true;
            return importObject;
        };
        // Function-table swap on the cipher virtual methods. Once
        // the WASM has instantiated, swap entries 4819 and 4820
        // (the SFrame CipherInterface vtable for the cached snapshot;
        // try-catch tolerates index drift across snapshots) with
        // JS wrappers that read the in_ptr argument from linear
        // memory before forwarding to the original. This bypasses
        // V8's WASM-JIT-bypasses-breakpoints limitation and gives
        // us pre-encryption plaintext at audio rate without ever
        // pausing the worker.
        const swapCipherVtable = (instance) => {
            G.__mlowSwapEntered = (G.__mlowSwapEntered || 0) + 1;
            try {
                const exports = instance && instance.exports;
                if (!exports) {
                    G.__mlowSwapBail = 'no-exports';
                    return;
                }
                const exportNames = Object.keys(exports);
                G.__mlowExportSample = exportNames.slice(0, 30);
                const table = exports.__indirect_function_table;
                const memory = exports.memory || G.__lastMemory;
                if (!table) {
                    G.__mlowSwapBail = 'no-table; exports include: '
                            + exportNames.filter(n => n.includes('table')).join(',');
                    return;
                }
                if (!memory || !memory.buffer) {
                    G.__mlowSwapBail = 'no-memory';
                    return;
                }
                G.__mlowFnTableHookDiag = {
                    waFunctionAvail: typeof G.WebAssembly.Function,
                    tableLength: table.length,
                    memoryBytes: memory.buffer.byteLength
                };
                // Runtime vtable discovery: scan linear memory for
                // the AudioDecoderMLowImpl typeinfo string, walk back
                // to the vtable, and report the slot numbers in
                // declaration order. Lets us pin the correct decoder
                // slot when the snapshot drifts (slot 5891 was right
                // for KwJJIha0f3H/YQjmnxbJlry but wedged on a newer
                // build, indicating drift).
                G.__mlowVtableDiscovery = (() => {
                    try {
                        const view = new Uint8Array(memory.buffer);
                        // typeinfo name as bytes (no null terminator):
                        // "N8facebook3rtc20AudioDecoderMLowImplE"
                        const needle = [78, 56, 102, 97, 99, 101, 98,
                            111, 111, 107, 51, 114, 116, 99, 50, 48,
                            65, 117, 100, 105, 111, 68, 101, 99, 111,
                            100, 101, 114, 77, 76, 111, 119, 73, 109,
                            112, 108, 69];
                        let nameOff = -1;
                        outer: for (let i = 0; i + needle.length <= view.length; i++) {
                            for (let j = 0; j < needle.length; j++) {
                                if (view[i + j] !== needle[j]) continue outer;
                            }
                            nameOff = i;
                            break;
                        }
                        if (nameOff < 0) return {error: 'typeinfo-not-found'};
                        // Find typeinfo struct base (32-bit LE word == nameOff).
                        const dv = new DataView(memory.buffer);
                        const typeInfoBases = [];
                        for (let i = 0; i + 4 <= view.length; i += 4) {
                            if (dv.getUint32(i, true) === nameOff) {
                                typeInfoBases.push(i - 4);
                            }
                        }
                        const vtables = [];
                        for (const tb of typeInfoBases) {
                            // Find vtable typeinfo slot (word == tb).
                            for (let i = 0; i + 4 <= view.length; i += 4) {
                                if (dv.getUint32(i, true) !== tb) continue;
                                // Read up to 18 vtable method slots.
                                const slots = [];
                                for (let k = 0; k < 18; k++) {
                                    const at = i + 4 + k * 4;
                                    if (at + 4 > view.length) break;
                                    const ts = dv.getUint32(at, true);
                                    if (ts <= 0 || ts > 200000) break;
                                    slots.push(ts);
                                }
                                vtables.push({typeInfoBase: tb, vtBase: i, slots: slots});
                            }
                        }
                        return {nameOff, typeInfoBases, vtables};
                    } catch (e) { return {error: e.message}; }
                })();
                // Slot 4820: SFrame cipher encrypt/decrypt vtable
                //   method (i32x3)->i32 — captures plaintext MLow on
                //   both inbound and outbound legs. Verified working
                //   in Phase 3.2.5.
                // Slot 5891: AudioDecoderMLowImpl::DecodeInternal
                //   (i32x6)->i32 — args: (this, encoded, encoded_len,
                //   sample_rate_hz, decoded_pcm, speech_type_out).
                //   Pre-call captures the encoded buffer; post-call
                //   captures the PCM at arg4 with length=ret_val*2
                //   bytes (ret_val = sample count). Slot is stable
                //   across recent snapshots (verified on KwJJIha0f3H
                //   and YQjmnxbJlry).
                // Slot 5892: AudioDecoderMLowImpl::DecodeRedundant-
                //   Internal — same shape as 5891, used for
                //   redundancy/concealment frames.
                // Per-slot config: the trampoline shape is pinned by
                // slot to avoid relying on funcref-import type-check
                // fallback (V8 in Chrome doesn't always reject
                // mismatched funcref types at instantiate time, so
                // the original "try 6-arg, fall back to 3-arg" probe
                // silently picked tramp6 for the 3-arg cipher slot
                // and wedged the codec on call_indirect).
                //
                // Slot 4820: AES-CTR cipher encrypt/decrypt vtable
                //   method, sig (i32x3)->i32. Cipher captures.
                // Slot 5891: AudioDecoderMLowImpl::DecodeInternal,
                //   sig (i32x6)->i32. PCM captures.
                const candidateConfigs = [
                    {slot: 4820, sig: '3arg'},
                    {slot: 5891, sig: '6arg'},
                    // Inner decode_icdf primitive: (this, icdf_ptr, ftb) -> i32.
                    // Static analysis (MLowDecoderCallGraphTest) ranks fn#10432
                    // at slot 6918 as the highest-confidence candidate (depth 6
                    // from fn#10652 multiframe parser, sig (i32x3)->i32). The
                    // cb_pre special-cases this slot to push a 4-byte LE
                    // 'cdf@6918' record per call so the post-prefix CDF chain
                    // can be reconstructed for MLowCdfChainDiscoveryTest.
                    {slot: 6918, sig: '3arg'}
                ];
                G.__mlowFnTableHookSlots = G.__mlowFnTableHookSlots || [];
                G.__mlowFnTableHookErrs = G.__mlowFnTableHookErrs || [];
                // V8 rejects JS functions in funcref tables and
                // doesn't expose WebAssembly.Function. Solution:
                // instantiate a small companion WASM module whose
                // sole job is to expose two trampolines matching
                // the cipher's signatures — the trampolines call
                // an env.cb (capture function) and an env.origN
                // (the original cipher function passed as a
                // funcref import). The result of
                // companionInstance.exports.trampN IS a
                // WasmExportedFunction that V8 accepts in
                // table.set across instances.
                //
                // Trampoline module bytes are hand-encoded inline
                // (~140 B). For each cipher slot:
                //   1. Save table.get(slot) as 'orig'.
                //   2. Instantiate the trampoline with env.{cb, origN}.
                //   3. table.set(slot, tramp.exports.trampN).
                // The codec's call_indirect at slot now lands in
                // our trampoline, which calls env.cb (records
                // plaintext to SAB ring), then forwards to orig.
                // Encode a non-negative i32 as signed-LEB128.
                // Result is 1..5 bytes.
                const encodeS32 = (v) => {
                    const out = [];
                    while (true) {
                        const b = v & 0x7F;
                        v >>= 7;
                        const last = (v === 0 && (b & 0x40) === 0)
                                  || (v === -1 && (b & 0x40) !== 0);
                        if (last) { out.push(b); return out; }
                        out.push(b | 0x80);
                    }
                };
                // Per-slot trampoline. Captures the input args via
                // env.cb_pre BEFORE the original runs and the
                // (potentially decoded) output via env.cb_post AFTER.
                // Three trampoline shapes are exported: tramp2,
                // tramp3, tramp6 — matching original signatures
                // (i32x2)->i32, (i32x3)->i32, (i32x6)->i32. The
                // installer picks the shape that the live function
                // table accepts for the slot. Both callbacks share a
                // single (i32x8)->() signature: (slot, a0..a5,
                // padding/ret_val). Shorter-arity trampolines pad
                // unused arg slots with zero. cb_post additionally
                // receives the original's return value as the 8th
                // argument so post-capture can read N=ret_val*2 bytes
                // out of the PCM buffer pointer.
                const buildTrampoline = (slotId) => {
                    const sb = encodeS32(slotId);
                    // Type section: 4 types. Original 4-arg callback
                    // type retained (the 8-arg variant wedged the
                    // codec). Decoder uses a separate set of 4-arg
                    // callbacks (cb_pre6/cb_post6) that pack the
                    // relevant decoder args (encoded_buf, encoded_len,
                    // decoded_buf, ret_samples) into 4 slots.
                    const typeSec = [
                        0x04,
                        0x60, 0x02, 0x7f, 0x7f, 0x01, 0x7f,                            // 0: (i32x2)->i32
                        0x60, 0x03, 0x7f, 0x7f, 0x7f, 0x01, 0x7f,                      // 1: (i32x3)->i32
                        0x60, 0x06, 0x7f, 0x7f, 0x7f, 0x7f, 0x7f, 0x7f, 0x01, 0x7f,    // 2: (i32x6)->i32
                        0x60, 0x04, 0x7f, 0x7f, 0x7f, 0x7f, 0x00                       // 3: (i32x4)->()
                    ];
                    // Imports (7): cb_pre, cb_post (cipher 3-arg
                    // callbacks); cb_pre6, cb_post6 (decoder 6-arg
                    // callbacks); orig2, orig3, orig6 (passthroughs).
                    // All callbacks share type 3 (i32x4)->().
                    const impEntry = (name, type) => [
                        0x03, 0x65, 0x6e, 0x76,                                        // "env"
                        name.length, ...[...name].map(c => c.charCodeAt(0)),
                        0x00, type
                    ];
                    const impSec = [
                        0x07,
                        ...impEntry('cb_pre',   0x03),                                 // import 0
                        ...impEntry('cb_post',  0x03),                                 // import 1
                        ...impEntry('cb_pre6',  0x03),                                 // import 2
                        ...impEntry('cb_post6', 0x03),                                 // import 3
                        ...impEntry('orig2',    0x00),                                 // import 4
                        ...impEntry('orig3',    0x01),                                 // import 5
                        ...impEntry('orig6',    0x02)                                  // import 6
                    ];
                    // Function section: 3 defined funcs, types 0/1/2.
                    const fnSec = [0x03, 0x00, 0x01, 0x02];
                    // Defined-function indices: tramp2=7, tramp3=8,
                    // tramp6=9.
                    const expSec = [
                        0x03,
                        0x06, ...[...'tramp2'].map(c => c.charCodeAt(0)), 0x00, 0x07,
                        0x06, ...[...'tramp3'].map(c => c.charCodeAt(0)), 0x00, 0x08,
                        0x06, ...[...'tramp6'].map(c => c.charCodeAt(0)), 0x00, 0x09
                    ];
                    // tramp2 body — locals: 1 i32 (save_ret at idx 2).
                    // Calls cb_pre(slot,a0,a1,0), orig2, cb_post(...).
                    const tramp2Code = [
                        0x01, 0x01, 0x7f,                                              // 1 decl of 1 i32
                        0x41, ...sb,                                                   // slot
                        0x20, 0x00, 0x20, 0x01, 0x41, 0x00,                            // a0, a1, 0
                        0x10, 0x00,                                                    // call cb_pre
                        0x20, 0x00, 0x20, 0x01,                                        // a0, a1
                        0x10, 0x04,                                                    // call orig2
                        0x21, 0x02,                                                    // local.set 2
                        0x41, ...sb,                                                   // slot
                        0x20, 0x00, 0x20, 0x01, 0x41, 0x00,                            // a0, a1, 0
                        0x10, 0x01,                                                    // call cb_post
                        0x20, 0x02,                                                    // return ret
                        0x0b
                    ];
                    // tramp3 body — locals: 1 i32 (save_ret at idx 3).
                    // Calls cb_pre(slot,a0,a1,a2), orig3, cb_post.
                    const tramp3Code = [
                        0x01, 0x01, 0x7f,
                        0x41, ...sb,                                                   // slot
                        0x20, 0x00, 0x20, 0x01, 0x20, 0x02,                            // a0, a1, a2
                        0x10, 0x00,                                                    // call cb_pre
                        0x20, 0x00, 0x20, 0x01, 0x20, 0x02,                            // a0, a1, a2
                        0x10, 0x05,                                                    // call orig3
                        0x21, 0x03,                                                    // local.set 3
                        0x41, ...sb,                                                   // slot
                        0x20, 0x00, 0x20, 0x01, 0x20, 0x02,                            // a0, a1, a2
                        0x10, 0x01,                                                    // call cb_post
                        0x20, 0x03,                                                    // return ret
                        0x0b
                    ];
                    // tramp6 body — locals: 1 i32 (save_ret at idx 6).
                    // Decoder calling convention:
                    //   a0 = this
                    //   a1 = encoded_buf, a2 = encoded_len
                    //   a3 = sample_rate_hz
                    //   a4 = decoded_pcm_buf, a5 = speech_type_out
                    //   ret = decoded sample count (PCM is ret*2 bytes)
                    // cb_pre6(slot, a0, a1, a2)  - this, encoded
                    //                              ptr, encoded len.
                    //   The this ptr is captured here for the
                    //   modify-and-observe replay path; cb_post6
                    //   gets decoded_ptr.
                    // cb_post6(slot, a4, ret, a5) - decoded ptr,
                    //                               samples,
                    //                               speech_type
                    //                               ptr (replay
                    //                               uses this).
                    const tramp6Code = [
                        0x01, 0x01, 0x7f,                                              // 1 decl of 1 i32
                        0x41, ...sb,                                                   // slot
                        0x20, 0x00, 0x20, 0x01, 0x20, 0x02,                            // a0, a1, a2
                        0x10, 0x02,                                                    // call cb_pre6
                        0x20, 0x00, 0x20, 0x01, 0x20, 0x02,                            // a0, a1, a2
                        0x20, 0x03, 0x20, 0x04, 0x20, 0x05,                            // a3, a4, a5
                        0x10, 0x06,                                                    // call orig6
                        0x21, 0x06,                                                    // local.set 6
                        0x41, ...sb,                                                   // slot
                        0x20, 0x04,                                                    // a4 (decoded ptr)
                        0x20, 0x06,                                                    // ret (samples)
                        0x20, 0x05,                                                    // a5 (speech_type_ptr)
                        0x10, 0x03,                                                    // call cb_post6
                        0x20, 0x06,                                                    // return ret
                        0x0b
                    ];
                    // varuint32 LEB128 — needed once any section
                    // body or function body exceeds 127 bytes (the
                    // 6-arg trampoline pushes the code section past
                    // that threshold).
                    const varU32 = (v) => {
                        const out = [];
                        v = v >>> 0;
                        do {
                            let b = v & 0x7F;
                            v >>>= 7;
                            if (v !== 0) b |= 0x80;
                            out.push(b);
                        } while (v !== 0);
                        return out;
                    };
                    const codeSec = [
                        0x03,
                        ...varU32(tramp2Code.length), ...tramp2Code,
                        ...varU32(tramp3Code.length), ...tramp3Code,
                        ...varU32(tramp6Code.length), ...tramp6Code
                    ];
                    const buildSection = (id, body) => [id, ...varU32(body.length), ...body];
                    const bytes = [
                        0x00, 0x61, 0x73, 0x6d, 0x01, 0x00, 0x00, 0x00,
                        ...buildSection(1, typeSec),
                        ...buildSection(2, impSec),
                        ...buildSection(3, fnSec),
                        ...buildSection(7, expSec),
                        ...buildSection(10, codeSec)
                    ];
                    return new Uint8Array(bytes);
                };
                // Capture function for INPUT side (pre-call). Args
                // are (slot, a0, a1, a2). For cipher slot 4820:
                //   a0 = this, a1 = in_buf, a2 = in_len.
                const cbPre = (slot, a0, a1, a2) => {
                    try {
                        // Slot 6918 is the inner decode_icdf primitive
                        // ((this, icdf_ptr, ftb) -> i32). a1 is the CDF
                        // table's linear-memory base address and is the
                        // single piece of state we need to deduce the
                        // post-prefix CDF chain. Push a 4-byte LE payload
                        // tagged 'cdf@<slot>' so the page-side drainer
                        // can reconstruct the per-frame chain in order.
                        if (slot === 6918) {
                            const buf = new Uint8Array(4);
                            buf[0] =  a1 & 0xFF;
                            buf[1] = (a1 >> 8) & 0xFF;
                            buf[2] = (a1 >> 16) & 0xFF;
                            buf[3] = (a1 >> 24) & 0xFF;
                            ringPush('cdf@' + slot, buf);
                            return;
                        }
                        if (a1 > 0 && a1 < memory.buffer.byteLength) {
                            const len = (a2 > 0 && a2 < 4096) ? a2 : 256;
                            const end = Math.min(a1 + len, memory.buffer.byteLength);
                            const view = new Uint8Array(memory.buffer, a1, end - a1);
                            const copy = new Uint8Array(view.length);
                            copy.set(view);
                            ringPush('in@' + slot, copy);
                        }
                    } catch (e) {}
                };
                // Capture function for OUTPUT side (post-call). The
                // cipher writes in-place; snapshot a0/a1/a2 ptrs
                // post-call to support buffer-layout analysis.
                const cbPost = (slot, a0, a1, a2) => {
                    try {
                        for (const [tag, ptr] of [['out0', a0], ['out1', a1], ['out2', a2]]) {
                            if (ptr > 0 && ptr < memory.buffer.byteLength - 480) {
                                const view = new Uint8Array(memory.buffer, ptr, 480);
                                const copy = new Uint8Array(view.length);
                                copy.set(view);
                                ringPush(tag + '@' + slot, copy);
                            }
                        }
                    } catch (e) {}
                };
                // Per-worker last-seen sequence for the SAB
                // replay-request channel. cb_pre6 acts as the
                // poll point: every real decoder call also
                // checks for pending replay requests.
                var lastReplayReqSeq = 0;
                // Decoder pre-call: (slot, thisPtr, encodedPtr,
                // encodedLen). Snapshot the encoded MLow frame
                // for pairing with the PCM at cb_post6 time, AND
                // record the this decoder pointer + encoded buf
                // pointer for the modify-and-observe replay path
                // (G.__mlowDecoderRefs). Also poll the SAB
                // request channel and serve any pending replay.
                const cbPre6 = (slot, thisPtr, encodedPtr, encodedLen) => {
                    try {
                        // Update modify-and-observe scratch refs.
                        // Always overwritten with the most-recent
                        // decoder call so the replay function uses
                        // up-to-date pointers (decoder state has
                        // already advanced past this frame, but
                        // the buffer addresses themselves stay
                        // valid for the call's duration).
                        var refs = G.__mlowDecoderRefs || (G.__mlowDecoderRefs = {});
                        refs[slot] = {
                            thisPtr: thisPtr | 0,
                            encodedPtr: encodedPtr | 0,
                            encodedLen: encodedLen | 0,
                            // decodedPtr / samples / speechTypePtr
                            // get filled in by cb_post6.
                            decodedPtr: refs[slot] ? refs[slot].decodedPtr : 0,
                            samples: refs[slot] ? refs[slot].samples : 0,
                            speechTypePtr: refs[slot] ? refs[slot].speechTypePtr : 0,
                            updatedAt: Date.now()
                        };
                        if (encodedPtr > 0 && encodedPtr < memory.buffer.byteLength) {
                            const len = (encodedLen > 0 && encodedLen < 4096) ? encodedLen : 64;
                            const end = Math.min(encodedPtr + len, memory.buffer.byteLength);
                            const view = new Uint8Array(memory.buffer, encodedPtr, end - encodedPtr);
                            const copy = new Uint8Array(view.length);
                            copy.set(view);
                            ringPush('enc@' + slot, copy);
                        }
                        // Serve any pending SAB replay request.
                        // The request channel is single-shot:
                        // whichever cb_pre6 call (across all
                        // workers) sees a higher seq than its
                        // last-seen value claims it via atomic
                        // CAS and processes. Other workers see
                        // the same seq next time and skip.
                        // Bit-flip sweep mode: process K mutations
                        // per cb_pre6 invocation. State is preserved
                        // across calls in the SAB hdr (mode + bit
                        // index). Worker pushes each PCM result
                        // into the SAB ring tagged 'mut@5891' with
                        // a 4-byte LE bit-index prefix on the
                        // payload; page drains after sweep_mode
                        // transitions to 2 (done). To keep live
                        // audio playable, the codec's encoded
                        // scratch buffer is restored to the live
                        // frame's bytes after each mutation cycle,
                        // and the codec's reset() between
                        // mutations puts the LPC predictor /
                        // range-coder context in a clean state per
                        // replay (already proven deterministic
                        // in Phase 3.3.8).
                        if (ringHdrI32) {
                            var sweepMode = Atomics.load(ringHdrI32, HDR_OFF_SWEEP_MODE);
                            if (sweepMode === 1) {
                                var sweepRefs = (G.__mlowDecoderRefs || {})[slot];
                                var sweepOrig = (G.__mlowDecoderOrigBySlot || {})[slot];
                                var sweepReset = (G.__mlowDecoderResetBySlot || {})[slot];
                                var sweepEncLen = Atomics.load(ringHdrI32, HDR_OFF_ENC_LEN);
                                var sweepBitIndex = Atomics.load(ringHdrI32, HDR_OFF_SWEEP_BIT_INDEX);
                                var sweepTotalBits = Atomics.load(ringHdrI32, HDR_OFF_SWEEP_TOTAL_BITS);
                                if (sweepRefs && sweepOrig && sweepReset
                                        && sweepEncLen > 0 && sweepEncLen <= HDR_ENC_PAYLOAD_SIZE) {
                                    var sweepHeap = new Uint8Array(memory.buffer);
                                    var sweepHdrEnc = new Uint8Array(hdr,
                                            HDR_OFF_ENC_PAYLOAD * 4, HDR_ENC_PAYLOAD_SIZE);
                                    // On first sweep cycle, copy
                                    // base encoded into the scratch
                                    // buffer so all mutations
                                    // start from the requested
                                    // base, not whatever the
                                    // codec's last decoded frame
                                    // was.
                                    if (sweepBitIndex === 0) {
                                        for (var sb = 0; sb < sweepEncLen; sb++) {
                                            sweepHeap[sweepRefs.encodedPtr + sb] = sweepHdrEnc[sb];
                                        }
                                    }
                                    // Process up to K mutations.
                                    var sweepProcessed = 0;
                                    while (sweepBitIndex < sweepTotalBits
                                            && sweepProcessed < SWEEP_K_PER_CYCLE) {
                                        var swByte = (sweepBitIndex / 8) | 0;
                                        var swBit = sweepBitIndex - swByte * 8;
                                        var swMask = (1 << (7 - swBit)); // MSB-first
                                        // Apply mutation.
                                        sweepHeap[sweepRefs.encodedPtr + swByte] ^= swMask;
                                        try {
                                            sweepReset(sweepRefs.thisPtr);
                                            var swRet = sweepOrig(sweepRefs.thisPtr,
                                                    sweepRefs.encodedPtr, sweepEncLen,
                                                    16000, sweepRefs.decodedPtr,
                                                    sweepRefs.speechTypePtr | 0);
                                            // Push PCM with bit-index
                                            // prefix.
                                            var swSamples = (swRet > 0 && swRet < 4096) ? swRet : 0;
                                            var swCopyBytes = Math.min(swSamples * 2, 480 - 4);
                                            var swPayload = new Uint8Array(4 + swCopyBytes);
                                            swPayload[0] = sweepBitIndex & 0xff;
                                            swPayload[1] = (sweepBitIndex >> 8) & 0xff;
                                            swPayload[2] = (sweepBitIndex >> 16) & 0xff;
                                            swPayload[3] = (sweepBitIndex >> 24) & 0xff;
                                            for (var sk = 0; sk < swCopyBytes; sk++) {
                                                swPayload[4 + sk] = sweepHeap[sweepRefs.decodedPtr + sk];
                                            }
                                            ringPush('mut@' + slot, swPayload);
                                        } catch (eSw) {
                                            // Push a 4-byte error
                                            // marker so the page can
                                            // see which bit failed.
                                            var errPayload = new Uint8Array(4);
                                            errPayload[0] = sweepBitIndex & 0xff;
                                            errPayload[1] = (sweepBitIndex >> 8) & 0xff;
                                            errPayload[2] = (sweepBitIndex >> 16) & 0xff;
                                            errPayload[3] = (sweepBitIndex >> 24) & 0xff;
                                            ringPush('muterr@' + slot, errPayload);
                                        }
                                        // Restore the encoded byte
                                        // (XOR back) so the next
                                        // mutation starts from the
                                        // base.
                                        sweepHeap[sweepRefs.encodedPtr + swByte] ^= swMask;
                                        sweepBitIndex++;
                                        sweepProcessed++;
                                    }
                                    Atomics.store(ringHdrI32, HDR_OFF_SWEEP_BIT_INDEX, sweepBitIndex);
                                    if (sweepBitIndex >= sweepTotalBits) {
                                        // Final reset so the live
                                        // decoder is in a clean
                                        // state for whatever frame
                                        // is about to come in.
                                        try { sweepReset(sweepRefs.thisPtr); } catch (e) {}
                                        Atomics.store(ringHdrI32, HDR_OFF_SWEEP_MODE, 2);
                                        Atomics.add(ringHdrI32, HDR_OFF_RESP_SEQ, 1);
                                    }
                                }
                            }
                            var reqSeq = Atomics.load(ringHdrI32, HDR_OFF_REQ_SEQ);
                            if (reqSeq !== lastReplayReqSeq) {
                                lastReplayReqSeq = reqSeq;
                                var reqDecoderSlot = Atomics.load(ringHdrI32, HDR_OFF_DECODER_SLOT);
                                if (reqDecoderSlot === slot) {
                                    var encLen = Atomics.load(ringHdrI32, HDR_OFF_ENC_LEN);
                                    var sampleRate = Atomics.load(ringHdrI32, HDR_OFF_SAMPLE_RATE);
                                    var refs = (G.__mlowDecoderRefs || {})[slot];
                                    var orig = (G.__mlowDecoderOrigBySlot || {})[slot];
                                    if (!refs || !orig || encLen <= 0
                                            || encLen > HDR_ENC_PAYLOAD_SIZE) {
                                        Atomics.store(ringHdrI32, HDR_OFF_RESP_STATUS, 2);
                                        Atomics.store(ringHdrI32, HDR_OFF_RESP_SAMPLES, 0);
                                        Atomics.add(ringHdrI32, HDR_OFF_RESP_SEQ, 1);
                                    } else {
                                        // Copy encoded request payload
                                        // from HDR into the codec's
                                        // captured encoded scratch
                                        // buffer.
                                        var heapU8 = new Uint8Array(memory.buffer);
                                        var hdrU8 = new Uint8Array(hdr,
                                                HDR_OFF_ENC_PAYLOAD * 4, HDR_ENC_PAYLOAD_SIZE);
                                        for (var i = 0; i < encLen; i++) {
                                            heapU8[refs.encodedPtr + i] = hdrU8[i];
                                        }
                                        try {
                                            // Reset the decoder's
                                            // state (vtable[6]) so
                                            // the replay output is
                                            // deterministic for a
                                            // given input — without
                                            // this, predictor +
                                            // range-coder context
                                            // accumulates across
                                            // replays and bit-flip
                                            // diffs are buried in
                                            // state drift.
                                            var resetFn = (G.__mlowDecoderResetBySlot
                                                    || {})[slot];
                                            if (typeof resetFn === 'function') {
                                                resetFn(refs.thisPtr);
                                            }
                                            var ret = orig(refs.thisPtr, refs.encodedPtr,
                                                    encLen, sampleRate | 0,
                                                    refs.decodedPtr, refs.speechTypePtr | 0);
                                            var samples = (ret > 0 && ret < 4096) ? ret : 0;
                                            // Write decoded PCM (up to
                                            // PCM_PAYLOAD_SIZE bytes)
                                            // back into HDR.
                                            var copyBytes = Math.min(samples * 2,
                                                    HDR_PCM_PAYLOAD_SIZE);
                                            var hdrPcm = new Uint8Array(hdr,
                                                    HDR_OFF_PCM_PAYLOAD * 4, HDR_PCM_PAYLOAD_SIZE);
                                            for (var j = 0; j < copyBytes; j++) {
                                                hdrPcm[j] = heapU8[refs.decodedPtr + j];
                                            }
                                            Atomics.store(ringHdrI32,
                                                    HDR_OFF_RESP_SAMPLES, samples);
                                            Atomics.store(ringHdrI32,
                                                    HDR_OFF_RESP_STATUS, 0);
                                            Atomics.add(ringHdrI32,
                                                    HDR_OFF_RESP_SEQ, 1);
                                        } catch (eReplay) {
                                            Atomics.store(ringHdrI32,
                                                    HDR_OFF_RESP_STATUS, 1);
                                            Atomics.store(ringHdrI32,
                                                    HDR_OFF_RESP_SAMPLES, 0);
                                            Atomics.add(ringHdrI32,
                                                    HDR_OFF_RESP_SEQ, 1);
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e) {}
                };
                // Decoder post-call: (slot, decodedPtr, samples,
                // speechTypePtr). Read samples*2 bytes (int16 PCM)
                // from decodedPtr; record decodedPtr +
                // speechTypePtr for replay.
                const cbPost6 = (slot, decodedPtr, samples, speechTypePtr) => {
                    try {
                        var refs = G.__mlowDecoderRefs || (G.__mlowDecoderRefs = {});
                        if (refs[slot]) {
                            refs[slot].decodedPtr = decodedPtr | 0;
                            refs[slot].samples = samples | 0;
                            refs[slot].speechTypePtr = speechTypePtr | 0;
                        }
                        if (decodedPtr > 0 && decodedPtr < memory.buffer.byteLength) {
                            const sampleCount = (samples > 0 && samples < 4096) ? samples : 480;
                            const bytes = sampleCount * 2;
                            const end = Math.min(decodedPtr + bytes, memory.buffer.byteLength);
                            const view = new Uint8Array(memory.buffer, decodedPtr, end - decodedPtr);
                            const copy = new Uint8Array(view.length);
                            copy.set(view);
                            ringPush('pcm@' + slot, copy);
                        }
                    } catch (e) {}
                };
                for (const cfg of candidateConfigs) {
                    const slot = cfg.slot;
                    const sig = cfg.sig;
                    if (slot >= table.length) {
                        G.__mlowFnTableHookErrs.push({slot, err: 'slot-out-of-range', tableLen: table.length});
                        continue;
                    }
                    const orig = table.get(slot);
                    if (!orig || typeof orig !== 'function') {
                        G.__mlowFnTableHookErrs.push({slot, err: 'no-orig'});
                        continue;
                    }
                    try {
                        const trampBytes = buildTrampoline(slot);
                        const stub2 = () => 0;
                        const stub3 = () => 0;
                        const stub6 = () => 0;
                        const importObj = {
                            env: {
                                cb_pre: cbPre,
                                cb_post: cbPost,
                                cb_pre6: cbPre6,
                                cb_post6: cbPost6,
                                orig2: sig === '2arg' ? orig : stub2,
                                orig3: sig === '3arg' ? orig : stub3,
                                orig6: sig === '6arg' ? orig : stub6
                            }
                        };
                        let trampInst = null, tramExport = null, usedSig = sig;
                        try {
                            trampInst = new G.WebAssembly.Instance(
                                new G.WebAssembly.Module(trampBytes), importObj);
                            tramExport = trampInst.exports['tramp' +
                                (sig === '6arg' ? '6' : sig === '3arg' ? '3' : '2')];
                        } catch (eInst) {
                            G.__mlowFnTableHookErrs.push({
                                slot, sig, strategy: 'instantiate',
                                err: eInst.message});
                            continue;
                        }
                        try {
                            table.set(slot, tramExport);
                            G.__mlowFnTableHookSlots.push({slot, sig: usedSig});
                            // Save the original WASM-exported
                            // function reference for the modify-
                            // and-observe replay path. Calling
                            // orig(this, encoded, len, sample_rate,
                            // decoded, speech_type) directly from
                            // JS bypasses the trampoline, letting
                            // us drive the decoder with synthetic
                            // input.
                            G.__mlowDecoderOrigBySlot = G.__mlowDecoderOrigBySlot || {};
                            G.__mlowDecoderOrigBySlot[slot] = orig;
                            // Also grab the AudioDecoderMLowImpl
                            // Reset method (vtable[6] = table slot
                            // 5883, signature (i32)->void) so the
                            // SAB replay handler can clear the
                            // decoder's LPC + range-coder state
                            // between mutations. Without this,
                            // back-to-back replays produce wildly
                            // varying output (verified: 3 replays
                            // of the same input gave 1920/1920/960
                            // samples with RMS 0.25/0/4052).
                            //
                            // Slot 5883 is stable across the
                            // KwJJIha0f3H / YQjmnxbJlry / current
                            // snapshots — see
                            // LiveSnapshotAesLocatorTest#locate-
                            // LiveSnapshotDecoderVtable.
                            if (slot === 5891 && table.length > 5883) {
                                try {
                                    var resetFn = table.get(5883);
                                    if (typeof resetFn === 'function') {
                                        G.__mlowDecoderResetBySlot = G.__mlowDecoderResetBySlot || {};
                                        G.__mlowDecoderResetBySlot[slot] = resetFn;
                                    }
                                } catch (eReset) {
                                    G.__mlowFnTableHookErrs.push({slot,
                                            strategy: 'reset-fetch',
                                            err: eReset.message});
                                }
                            }
                        } catch (eSet) {
                            G.__mlowFnTableHookErrs.push({
                                slot, strategy: 'table.set',
                                err: eSet.message, sig: usedSig});
                        }
                    } catch (eOuter) {
                        G.__mlowFnTableHookErrs.push({
                            slot, strategy: 'outer', err: eOuter.message});
                    }
                }
                // Modify-and-observe replay: write synthetic
                // encoded bytes into the codec's encoded buffer,
                // call the original decoder fn with the saved
                // (this, decoded, speech_type) refs, read back the
                // produced PCM. Returns the decoded samples as a
                // base64 string of LE int16, plus the original's
                // return value (sample count).
                //
                // Caller must have a captured set of refs in
                // G.__mlowDecoderRefs[slot] from at least one
                // real decoder call — i.e., this only works
                // during an active call after audio has flowed.
                //
                // The decoder is stateful: each replay advances
                // internal predictor state, so two replays with
                // identical inputs may produce different PCM. For
                // bit-flip experiments, expect baseline drift on
                // top of the targeted bit's effect.
                G.__mlowReplay = function (slot, encodedB64, sampleRateHz) {
                    try {
                        var refs = (G.__mlowDecoderRefs || {})[slot];
                        var orig = (G.__mlowDecoderOrigBySlot || {})[slot];
                        if (!refs || !orig) {
                            return {error: 'no-refs-or-orig: have refs=' + !!refs
                                    + ' orig=' + !!orig};
                        }
                        if (!refs.encodedPtr || !refs.decodedPtr || !refs.thisPtr) {
                            return {error: 'incomplete-refs', refs: refs};
                        }
                        var raw = atob(encodedB64);
                        var bytes = new Uint8Array(raw.length);
                        for (var i = 0; i < raw.length; i++) bytes[i] = raw.charCodeAt(i) & 0xFF;
                        if (bytes.length > 4096) {
                            return {error: 'frame-too-large', length: bytes.length};
                        }
                        var heapU8 = new Uint8Array(memory.buffer);
                        // Write encoded bytes to the codec's
                        // captured encoded scratch buffer.
                        for (var j = 0; j < bytes.length; j++) {
                            heapU8[refs.encodedPtr + j] = bytes[j];
                        }
                        // Reset state so the output is determined
                        // purely by the input bytes (and not by
                        // accumulated predictor / range-coder
                        // context from prior calls).
                        var resetFn = (G.__mlowDecoderResetBySlot || {})[slot];
                        if (typeof resetFn === 'function') {
                            resetFn(refs.thisPtr);
                        }
                        // Invoke the WASM-exported decoder.
                        var ret = orig(refs.thisPtr, refs.encodedPtr,
                                bytes.length, sampleRateHz | 0,
                                refs.decodedPtr, refs.speechTypePtr | 0);
                        // Read back PCM. Cap at the captured
                        // sample count or the returned count,
                        // whichever is non-zero & within sanity.
                        var samples = (ret > 0 && ret < 4096) ? ret : 0;
                        if (samples === 0) {
                            return {ret: ret, error: 'no-samples'};
                        }
                        var pcmBytes = new Uint8Array(samples * 2);
                        for (var k = 0; k < samples * 2; k++) {
                            pcmBytes[k] = heapU8[refs.decodedPtr + k];
                        }
                        var b64 = '';
                        var chunkSize = 8192;
                        for (var off = 0; off < pcmBytes.length; off += chunkSize) {
                            var end2 = Math.min(off + chunkSize, pcmBytes.length);
                            b64 += String.fromCharCode.apply(null,
                                    pcmBytes.subarray(off, end2));
                        }
                        return {ret: ret, samples: samples, pcmB64: btoa(b64)};
                    } catch (e) {
                        return {error: 'exception: ' + e.message};
                    }
                };
            } catch (e) {
                G.__mlowFnTableHookErr = 'top: ' + e.message;
            }
        };
        G.__mlowWrapPath = G.__mlowWrapPath || [];
        const origInstantiate = G.WebAssembly.instantiate;
        G.WebAssembly.instantiate = function (bytes, importObject) {
            G.__mlowWrapPath.push('instantiate');
            const p = origInstantiate.call(this, bytes, wrapEnv(importObject));
            return Promise.resolve(p).then((res) => {
                G.__mlowWrapPath.push('instantiate-then');
                try {
                    const inst = res && res.instance ? res.instance : res;
                    swapCipherVtable(inst);
                } catch (e) { G.__mlowWrapPath.push('instantiate-catch:' + e.message); }
                return res;
            });
        };
        if (typeof G.WebAssembly.instantiateStreaming === 'function') {
            const origStream = G.WebAssembly.instantiateStreaming;
            G.WebAssembly.instantiateStreaming = function (sourcePromise, importObject) {
                G.__mlowWrapPath.push('instantiateStreaming');
                const p = origStream.call(this, sourcePromise, wrapEnv(importObject));
                return Promise.resolve(p).then((res) => {
                    G.__mlowWrapPath.push('instantiateStreaming-then');
                    try { swapCipherVtable(res && res.instance ? res.instance : res); }
                    catch (e) { G.__mlowWrapPath.push('iS-catch:' + e.message); }
                    return res;
                });
            };
        }
        if (G.WebAssembly.Instance) {
            const origInst = G.WebAssembly.Instance;
            G.WebAssembly.Instance = function (mod, importObject) {
                G.__mlowWrapPath.push('Instance-ctor');
                const inst = new origInst(mod, wrapEnv(importObject));
                try { swapCipherVtable(inst); }
                catch (e) { G.__mlowWrapPath.push('Inst-catch:' + e.message); }
                return inst;
            };
            G.WebAssembly.Instance.prototype = origInst.prototype;
        }
    }

    // ----- Worker side: announce SAB to the page immediately.
    // The page must have wrapped the Worker constructor before instantiation
    // so its onmessage listener is registered. The pre-hook runs
    // before the WASM init, so postMessage executes on the worker's
    // event loop right after this function returns — well before the
    // codec enters its long-running WASM call.
    if (isWorker && sab && hdr) {
        try {
            G.postMessage({__mlowSabInit: true, sab: sab, hdr: hdr});
        } catch (e) {}
    }

    // ----- Page side: wrap Worker constructor to capture SAB refs
    // from each newly-spawned worker. Map workerName→{sab,hdr}.
    if (!isWorker && G.Worker) {
        G.__mlowWorkerSabs = G.__mlowWorkerSabs || {};
        const OrigWorker = G.Worker;
        const Wrapped = function (url, opts) {
            const w = new OrigWorker(url, opts);
            const name = (opts && opts.name) || (typeof url === 'string' ? url.split('/').pop() : 'worker');
            const idx = ((G.__mlowWorkerSeq = (G.__mlowWorkerSeq||0)+1));
            const tag = name + '#' + idx;
            const onMsg = (ev) => {
                if (ev.data && ev.data.__mlowSabInit) {
                    G.__mlowWorkerSabs[tag] = {sab: ev.data.sab, hdr: ev.data.hdr};
                    w.removeEventListener('message', onMsg);
                }
            };
            w.addEventListener('message', onMsg);
            return w;
        };
        Wrapped.prototype = OrigWorker.prototype;
        G.Worker = Wrapped;

        // Page-side ring drainer. Reads each worker's SAB ring and
        // returns base64-encoded entries. Independent of any worker
        // event loop.
        G.__mlowDrainAll = () => {
            const out = [];
            for (const tag of Object.keys(G.__mlowWorkerSabs)) {
                const {sab, hdr} = G.__mlowWorkerSabs[tag];
                if (!sab || !hdr) continue;
                const hi32 = new Int32Array(hdr);
                const slotsUsed = Atomics.load(hi32, 0);
                const slotSize = hi32[3] || 512;
                const maxSlots = hi32[4] || 4096;
                const u8 = new Uint8Array(sab);
                const dv = new DataView(sab);
                const n = Math.min(slotsUsed, maxSlots);
                for (let i = 0; i < n; i++) {
                    const off = i * slotSize;
                    const tagLen = dv.getUint32(off, true);
                    if (tagLen === 0 || tagLen > 16) continue;
                    let srcTag = '';
                    for (let j = 0; j < tagLen; j++) srcTag += String.fromCharCode(u8[off + 4 + j]);
                    const payLen = dv.getUint32(off + 20, true);
                    const tsLo = dv.getUint32(off + 24, true);
                    const tsHi = dv.getUint32(off + 28, true);
                    const ts = tsHi * 0x100000000 + tsLo;
                    let s = '';
                    for (let j = 0; j < payLen; j++) s += String.fromCharCode(u8[off + 32 + j]);
                    out.push({worker: tag, slot: i, source: srcTag, ts: ts, len: payLen, bytesB64: btoa(s)});
                }
            }
            return out;
        };
        G.__mlowDrainStats = () => {
            const out = {};
            for (const tag of Object.keys(G.__mlowWorkerSabs)) {
                const {hdr} = G.__mlowWorkerSabs[tag];
                if (!hdr) continue;
                const hi32 = new Int32Array(hdr);
                out[tag] = {slotsUsed: Atomics.load(hi32, 0), dropped: Atomics.load(hi32, 1)};
            }
            return out;
        };

        // Page-side modify-and-observe replay. Writes a request
        // into a specific worker's SAB hdr control region, atomic-
        // increments req_seq, polls resp_seq for change, and reads
        // back the decoded PCM. Uses the existing per-worker SAB
        // pair captured by the Worker-constructor wrapper.
        //
        //   workerTag      e.g. 'WAWebVoipWebWasmWorkerBundle#5'
        //                  or null to broadcast to all workers
        //                  hosting the codec.
        //   decoderSlot    typically 5891 (DecodeInternal).
        //   encodedB64     base64 of the encoded MLow frame
        //                  (≤256 bytes when decoded).
        //   sampleRateHz   passed to the decoder as arg3.
        //   timeoutMs      poll deadline; default 200 ms covers
        //                  the audio-frame cadence (20 ms × 10).
        //
        // Returns an object: {ok, samples, pcmB64, status, worker,
        // err?}. Status values: 0=ok, 1=worker-error, 2=no-refs.
        G.__mlowReplayCache = G.__mlowReplayCache || {lastWorker: null};
        G.__mlowSendReplay = function (workerTag, decoderSlot,
                                       encodedB64, sampleRateHz, timeoutMs) {
            timeoutMs = timeoutMs | 0 || 50;
            sampleRateHz = sampleRateHz | 0 || 16000;
            decoderSlot = decoderSlot | 0;
            var raw = atob(encodedB64);
            if (raw.length > 256) {
                return {ok: false, err: 'encoded too large: ' + raw.length};
            }
            var allVoip = Object.keys(G.__mlowWorkerSabs).filter(function (k) {
                return k.indexOf('Voip') >= 0;
            });
            var candidates;
            if (workerTag) {
                candidates = [workerTag];
            } else if (G.__mlowReplayCache.lastWorker
                    && allVoip.indexOf(G.__mlowReplayCache.lastWorker) >= 0) {
                // Cache hit: try the last-responding worker first;
                // fall back to the rest only on failure. Cuts the
                // amortised per-request cost from ~125 ms (probe
                // every Voip worker, busy-wait timeoutMs each) to
                // <2 ms during steady-state operation.
                var cached = G.__mlowReplayCache.lastWorker;
                candidates = [cached].concat(allVoip.filter(function (k) {
                    return k !== cached;
                }));
            } else {
                candidates = allVoip;
            }
            for (var ci = 0; ci < candidates.length; ci++) {
                var tag = candidates[ci];
                var sabHdr = G.__mlowWorkerSabs[tag];
                if (!sabHdr || !sabHdr.hdr) continue;
                var hi32 = new Int32Array(sabHdr.hdr);
                if (Atomics.load(hi32, 8) !== (0xC0BACAFE | 0)) continue;
                // Stage encoded payload in the HDR.
                var hdrU8 = new Uint8Array(sabHdr.hdr, 16 * 4, 256);
                for (var i = 0; i < raw.length; i++) {
                    hdrU8[i] = raw.charCodeAt(i) & 0xFF;
                }
                // Write request fields.
                Atomics.store(hi32, 11, decoderSlot);
                Atomics.store(hi32, 12, raw.length);
                Atomics.store(hi32, 13, sampleRateHz);
                var prevResp = Atomics.load(hi32, 10);
                // Bump req_seq — the worker's cb_pre6 polls.
                Atomics.add(hi32, 9, 1);
                // Poll for response. Busy-loop on the main thread
                // is acceptable here only for small timeouts; the
                // async variant below is preferred for large sweeps.
                var deadline = Date.now() + timeoutMs;
                var newResp;
                while (Date.now() < deadline) {
                    newResp = Atomics.load(hi32, 10);
                    if (newResp !== prevResp) break;
                }
                if (newResp === prevResp) continue; // Try next worker.
                var status = Atomics.load(hi32, 14);
                var samples = Atomics.load(hi32, 15);
                G.__mlowReplayCache.lastWorker = tag;
                if (status !== 0) {
                    return {ok: false, status: status, samples: samples,
                            worker: tag};
                }
                // Read PCM.
                var pcmU8 = new Uint8Array(sabHdr.hdr, 80 * 4, 480);
                var bytesToRead = Math.min(samples * 2, 480);
                var s = '';
                for (var j = 0; j < bytesToRead; j++) {
                    s += String.fromCharCode(pcmU8[j]);
                }
                return {ok: true, status: 0, samples: samples,
                        pcmB64: btoa(s), worker: tag};
            }
            return {ok: false, err: 'no-worker-served-request',
                    candidates: candidates};
        };

        // Page-side bit-flip sweep. Triggers a worker-internal
        // sweep that runs entirely in cb_pre6 cycles (K mutations
        // per real audio frame), pushes per-bit PCM results to
        // the SAB ring tagged 'mut@<slot>' with a 4-byte LE
        // bit-index prefix on each payload. Page-side just polls
        // for sweep_mode == 2 (done) then drains the SAB ring.
        //
        //   workerTag      target worker (must be a Voip worker
        //                  with active decoder refs). null →
        //                  pick the cached responding worker.
        //   decoderSlot    typically 5891.
        //   encodedB64     base encoded MLow frame.
        //   timeoutMs      poll deadline; rough guideline
        //                  encoded_len * 8 * 30 ms (sweep takes
        //                  ~30 ms per bit at 50 Hz audio frame
        //                  cadence with K=2).
        //
        // Returns {ok, totalBits, elapsedMs, results} where
        // results is an array of {bitIndex, samples, pcmB64,
        // err?} sorted by bit index.
        G.__mlowRunSweep = async function (workerTag, decoderSlot,
                                            encodedB64, timeoutMs) {
            decoderSlot = decoderSlot | 0;
            var raw = atob(encodedB64);
            if (raw.length === 0 || raw.length > 256) {
                return {ok: false, err: 'bad encoded length: ' + raw.length};
            }
            var totalBits = raw.length * 8;
            timeoutMs = timeoutMs | 0
                    || Math.max(60000, totalBits * 25);
            var allVoip = Object.keys(G.__mlowWorkerSabs).filter(function (k) {
                return k.indexOf('Voip') >= 0;
            });
            var tag = workerTag
                    || (G.__mlowReplayCache.lastWorker
                            && allVoip.indexOf(G.__mlowReplayCache.lastWorker) >= 0
                            ? G.__mlowReplayCache.lastWorker
                            : allVoip[0]);
            if (!tag) return {ok: false, err: 'no Voip worker'};
            var sabHdr = G.__mlowWorkerSabs[tag];
            if (!sabHdr || !sabHdr.hdr) return {ok: false, err: 'no hdr for ' + tag};
            var hi32 = new Int32Array(sabHdr.hdr);
            if (Atomics.load(hi32, 8) !== (0xC0BACAFE | 0)) {
                return {ok: false, err: 'magic mismatch on ' + tag};
            }
            // Stage encoded into HDR.
            var hdrU8 = new Uint8Array(sabHdr.hdr, 16 * 4, 256);
            for (var i = 0; i < raw.length; i++) {
                hdrU8[i] = raw.charCodeAt(i) & 0xFF;
            }
            Atomics.store(hi32, 11, decoderSlot);
            Atomics.store(hi32, 12, raw.length);
            Atomics.store(hi32, 13, 16000);
            Atomics.store(hi32, 201, 0); // bit_index
            Atomics.store(hi32, 202, totalBits);
            // Snapshot ring slotsUsed so we can drain only the
            // 'mut@' entries pushed during this sweep.
            var ringStartSlots = Atomics.load(hi32, 0);
            // Trigger sweep.
            Atomics.store(hi32, 200, 1); // sweep_mode = running
            // Bump req_seq so cb_pre6 also sees state-changed.
            Atomics.add(hi32, 9, 1);
            var t0 = Date.now();
            var deadline = t0 + timeoutMs;
            // Yield-poll for sweep done.
            while (Date.now() < deadline) {
                if (Atomics.load(hi32, 200) === 2) break;
                await new Promise(function (r) { setTimeout(r, 25); });
            }
            var elapsedMs = Date.now() - t0;
            var doneMode = Atomics.load(hi32, 200);
            var doneBitIndex = Atomics.load(hi32, 201);
            // Reset sweep_mode for next time.
            Atomics.store(hi32, 200, 0);
            // Drain 'mut@<slot>' ring entries pushed since
            // ringStartSlots. We don't use __mlowDrainAll because
            // it returns ALL workers; we want this worker only.
            var ringSab = sabHdr.sab;
            var ringEndSlots = Atomics.load(hi32, 0);
            var slotSize = hi32[3] || 512;
            var ringU8 = new Uint8Array(ringSab);
            var ringDv = new DataView(ringSab);
            var results = [];
            var errors = [];
            for (var ri = ringStartSlots; ri < ringEndSlots; ri++) {
                var off = ri * slotSize;
                var tagLen = ringDv.getUint32(off, true);
                if (tagLen === 0 || tagLen > 16) continue;
                var srcTag = '';
                for (var t = 0; t < tagLen; t++) srcTag += String.fromCharCode(ringU8[off + 4 + t]);
                if (srcTag !== ('mut@' + decoderSlot)
                        && srcTag !== ('muterr@' + decoderSlot)) continue;
                var payLen = ringDv.getUint32(off + 20, true);
                if (payLen < 4) continue;
                var bitIndex = ringDv.getUint32(off + 32, true);
                if (srcTag.indexOf('muterr') === 0) {
                    errors.push(bitIndex);
                    continue;
                }
                var pcmLen = payLen - 4;
                var s = '';
                for (var pp = 0; pp < pcmLen; pp++) {
                    s += String.fromCharCode(ringU8[off + 32 + 4 + pp]);
                }
                results.push({bitIndex: bitIndex, samples: pcmLen / 2,
                        pcmB64: btoa(s)});
            }
            results.sort(function (a, b) { return a.bitIndex - b.bitIndex; });
            return {ok: doneMode === 2, doneBitIndex: doneBitIndex,
                    totalBits: totalBits, elapsedMs: elapsedMs,
                    worker: tag, results: results, errors: errors};
        };

        // Async, non-blocking replay. Uses setTimeout(0) between
        // SAB polls so the page main thread stays responsive
        // through long sweep experiments. Same return shape as
        // __mlowSendReplay; awaits a Promise.
        G.__mlowSendReplayAsync = async function (workerTag, decoderSlot,
                                                   encodedB64, sampleRateHz, timeoutMs) {
            timeoutMs = timeoutMs | 0 || 50;
            sampleRateHz = sampleRateHz | 0 || 16000;
            decoderSlot = decoderSlot | 0;
            var raw = atob(encodedB64);
            if (raw.length > 256) {
                return {ok: false, err: 'encoded too large: ' + raw.length};
            }
            var allVoip = Object.keys(G.__mlowWorkerSabs).filter(function (k) {
                return k.indexOf('Voip') >= 0;
            });
            var candidates;
            if (workerTag) {
                candidates = [workerTag];
            } else if (G.__mlowReplayCache.lastWorker
                    && allVoip.indexOf(G.__mlowReplayCache.lastWorker) >= 0) {
                var cached = G.__mlowReplayCache.lastWorker;
                candidates = [cached].concat(allVoip.filter(function (k) {
                    return k !== cached;
                }));
            } else {
                candidates = allVoip;
            }
            for (var ci = 0; ci < candidates.length; ci++) {
                var tag = candidates[ci];
                var sabHdr = G.__mlowWorkerSabs[tag];
                if (!sabHdr || !sabHdr.hdr) continue;
                var hi32 = new Int32Array(sabHdr.hdr);
                if (Atomics.load(hi32, 8) !== (0xC0BACAFE | 0)) continue;
                var hdrU8 = new Uint8Array(sabHdr.hdr, 16 * 4, 256);
                for (var i = 0; i < raw.length; i++) {
                    hdrU8[i] = raw.charCodeAt(i) & 0xFF;
                }
                Atomics.store(hi32, 11, decoderSlot);
                Atomics.store(hi32, 12, raw.length);
                Atomics.store(hi32, 13, sampleRateHz);
                var prevResp = Atomics.load(hi32, 10);
                Atomics.add(hi32, 9, 1);
                var deadline = Date.now() + timeoutMs;
                var newResp = prevResp;
                while (Date.now() < deadline) {
                    newResp = Atomics.load(hi32, 10);
                    if (newResp !== prevResp) break;
                    // Yield between checks so the page main
                    // thread can service UI events / Worker
                    // postMessage callbacks. setTimeout(0)
                    // takes ~1-4 ms in Chromium under load,
                    // which is the natural floor for our
                    // worker reply round-trip anyway
                    // (cb_pre6 fires every ~20 ms during
                    // active audio).
                    await new Promise(function (resolve) { setTimeout(resolve, 0); });
                }
                if (newResp === prevResp) continue;
                var status = Atomics.load(hi32, 14);
                var samples = Atomics.load(hi32, 15);
                G.__mlowReplayCache.lastWorker = tag;
                if (status !== 0) {
                    return {ok: false, status: status, samples: samples,
                            worker: tag};
                }
                var pcmU8 = new Uint8Array(sabHdr.hdr, 80 * 4, 480);
                var bytesToRead = Math.min(samples * 2, 480);
                var s = '';
                for (var j = 0; j < bytesToRead; j++) {
                    s += String.fromCharCode(pcmU8[j]);
                }
                return {ok: true, status: 0, samples: samples,
                        pcmB64: btoa(s), worker: tag};
            }
            return {ok: false, err: 'no-worker-served-request',
                    candidates: candidates};
        };
    }

    // ----- Aggregation across page + workers via BroadcastChannel.
    try {
        const channel = new BroadcastChannel('mlow-corpus');
        channel.onmessage = (ev) => {
            if (ev.data === 'dump-request' && isWorker) {
                channel.postMessage({type: 'dump-reply', side: 'worker',
                    sends: G.__capturedSends.map(e => ({ts: e.ts, ip: e.ip, port: e.port, source: e.source, bytesB64: b64(e.bytes)}))});
            }
        };
        G.__mlowChannel = channel;
    } catch (e) {}

    G.__mlowDumpCorpus = () => JSON.stringify({
        side: G.__mlowSide,
        sends: G.__capturedSends.map(e => ({ts: e.ts, ip: e.ip, port: e.port, source: e.source, bytesB64: b64(e.bytes)})),
        audioIn: G.__capturedAudioIn.map(e => ({ts: e.ts, sampleRate: e.sampleRate, channels: e.channels, bytesB64: b64(e.bytes)}))
    });
    G.__mlowResetCapture = () => {
        G.__capturedSends.length = 0;
        G.__capturedAudioIn.length = 0;
        return 'reset';
    };
    if (!isWorker) {
        // Page-side aggregator: ask all workers via the channel,
        // collect replies for 1 s, return concatenated corpus.
        G.__mlowAggregateCorpus = function (timeoutMs) {
            timeoutMs = timeoutMs || 1500;
            return new Promise((resolve) => {
                const replies = [];
                if (!G.__mlowChannel) {
                    resolve(JSON.stringify({sends: G.__capturedSends.map(e => ({ts: e.ts, ip: e.ip, port: e.port, source: e.source, bytesB64: b64(e.bytes)})), workerSends: []}));
                    return;
                }
                const ch = G.__mlowChannel;
                const handler = (ev) => {
                    if (ev.data && ev.data.type === 'dump-reply') replies.push(ev.data);
                };
                ch.addEventListener('message', handler);
                ch.postMessage('dump-request');
                setTimeout(() => {
                    ch.removeEventListener('message', handler);
                    const merged = [].concat(...replies.map(r => r.sends));
                    resolve(JSON.stringify({
                        pageSends: G.__capturedSends.map(e => ({ts: e.ts, ip: e.ip, port: e.port, source: e.source, bytesB64: b64(e.bytes)})),
                        workerCount: replies.length,
                        workerSends: merged
                    }));
                }, timeoutMs);
            });
        };
    }
})();
`;

// CDP wrapper. Connects to browser-level WS, multiplexes commands
// across the root + per-target sessions via the `sessionId` envelope.
class CDP {
    constructor(ws) {
        this.ws = ws;
        this.nextId = 1;
        this.pending = new Map();
        this.handlers = [];
        ws.onmessage = (ev) => {
            const msg = JSON.parse(ev.data);
            if (msg.id && this.pending.has(msg.id)) {
                const {resolve, reject} = this.pending.get(msg.id);
                this.pending.delete(msg.id);
                if (msg.error) reject(new Error(msg.error.message));
                else resolve(msg.result);
            } else if (msg.method) {
                this.handlers.forEach((h) => h(msg));
            }
        };
    }
    send(method, params, sessionId) {
        const id = this.nextId++;
        return new Promise((resolve, reject) => {
            this.pending.set(id, {resolve, reject});
            const msg = {id, method, params: params || {}};
            if (sessionId) msg.sessionId = sessionId;
            this.ws.send(JSON.stringify(msg));
        });
    }
    on(handler) { this.handlers.push(handler); }
}

async function main() {
    const verRes = await fetch(`http://127.0.0.1:${cdpPort}/json/version`);
    const ver = await verRes.json();
    console.log('browser:', ver.Browser, 'protocol:', ver['Protocol-Version']);
    const ws = new WebSocket(ver.webSocketDebuggerUrl);
    await new Promise((resolve, reject) => {
        ws.onopen = resolve;
        setTimeout(() => reject(new Error('ws open timeout')), 5000);
    });
    const cdp = new CDP(ws);
    console.log('attached to browser session.');

    const handled = new Set();
    cdp.on(async (msg) => {
        if (msg.method !== 'Target.attachedToTarget') return;
        const sessionId = msg.params.sessionId;
        const ti = msg.params.targetInfo;
        if (handled.has(sessionId)) return;
        handled.add(sessionId);
        const isWa = (ti.url || '').includes('whatsapp.com')
                    || (ti.title || '').includes('WAWeb');
        if (!isWa) {
            // Resume immediately so non-WA targets aren't stuck.
            if (msg.params.waitingForDebugger) {
                cdp.send('Runtime.runIfWaitingForDebugger', {}, sessionId).catch(() => {});
            }
            return;
        }
        console.log(`auto-attach: type=${ti.type} title=${(ti.title || '').slice(0, 40)} waitingForDebugger=${msg.params.waitingForDebugger}`);
        try {
            if (ti.type === 'page') {
                await cdp.send('Page.enable', {}, sessionId);
                await cdp.send('Page.addScriptToEvaluateOnNewDocument', {source: HOOK}, sessionId);
                console.log(`page hook installed: ${ti.title}`);
            } else if (ti.type === 'worker' || ti.type === 'service_worker' || ti.type === 'shared_worker') {
                await cdp.send('Runtime.evaluate', {expression: HOOK, awaitPromise: false}, sessionId);
                // Propagate auto-attach into THIS worker so any nested
                // workers it spawns (Emscripten pthread workers, the
                // SCTP DCThread, etc.) also pause-on-start and get our
                // hook installed before their first script runs. The
                // codec's actual DataChannel.send happens in such a
                // pthread.
                await cdp.send('Target.setAutoAttach', {
                    autoAttach: true,
                    waitForDebuggerOnStart: true,
                    flatten: true
                }, sessionId).catch(() => {});
                console.log(`worker hook installed: ${ti.title || ti.type}`);
            }
        } catch (e) {
            console.warn(`hook install failed for ${ti.title}: ${e.message}`);
        }
        if (msg.params.waitingForDebugger) {
            await cdp.send('Runtime.runIfWaitingForDebugger', {}, sessionId).catch(() => {});
        }
    });

    // Discover + auto-attach. waitForDebuggerOnStart pauses each new
    // target before its first script runs, giving the handler above
    // time to install our hook.
    await cdp.send('Target.setDiscoverTargets', {discover: true});
    await cdp.send('Target.setAutoAttach', {
        autoAttach: true,
        waitForDebuggerOnStart: true,
        flatten: true
    });

    // Find the page and reload so the pre-hook lands on a fresh
    // document (and the new spawned workers all get their hook
    // installed via the auto-attach handler above).
    const targetsRes = await cdp.send('Target.getTargets', {});
    const page = targetsRes.targetInfos.find(
        (t) => t.type === 'page' && (t.url || '').startsWith('https://web.whatsapp.com'));
    if (!page) {
        console.error('no whatsapp page target found');
        process.exit(1);
    }
    // Manually attach to the page (auto-attach normally only catches
    // children of the root browser session — the page itself needs
    // attachToTarget when we connect mid-session).
    const attach = await cdp.send('Target.attachToTarget',
            {targetId: page.targetId, flatten: true});
    const pageSession = attach.sessionId;
    handled.add(pageSession);
    await cdp.send('Page.enable', {}, pageSession);
    await cdp.send('Page.addScriptToEvaluateOnNewDocument', {source: HOOK}, pageSession);
    // Also set auto-attach on the PAGE session — workers are
    // children of the page, not of the browser, so without this
    // they spawn without waitForDebuggerOnStart honoured and our
    // hook lands too late (after the WASM has already
    // instantiated).
    await cdp.send('Target.setAutoAttach', {
        autoAttach: true,
        waitForDebuggerOnStart: true,
        flatten: true
    }, pageSession);
    console.log(`page hook seeded on existing page: ${page.title}`);

    console.log('reloading page; pre-hook will run on every new document and worker.');
    await cdp.send('Page.reload', {}, pageSession);

    // Wait for the page to settle, then trigger voipInit so the
    // Voip workers spawn (they're lazy — without an explicit init
    // request they never come up). Auto-attach is already in place
    // and will catch each worker as it starts.
    await new Promise((r) => setTimeout(r, 6000));
    console.log('triggering voipInit on the page so workers spawn...');
    await cdp.send('Runtime.evaluate', {
        expression: `(async () => {
            try {
                // Force the AB props that gate web calling.
                // Required for WhatsApp Web revisions
                // 1039067946+ which tightened the calling
                // gates — without these, startWAWebVoipCall
                // silently aborts before the call state
                // machine fires (no exception, no state
                // change). See enable-voip.js for the
                // canonical list.
                const abp = require('WAWebABProps');
                const setProp = (n, v) => {
                    try { abp.setABPropConfigValue(n, v); } catch (e) {}
                };
                setProp('enable_web_calling', true);
                setProp('enable_web_group_calling', true);
                setProp('enable_web_voip_p2p', true);
                setProp('enable_wds_calling_dropdown', true);
                setProp('web_calling_enable_on_windows', false);
                setProp('enable_unified_call_buttons_in_chat', true);
                await require('JSResourceForInteraction')('WAWebVoipStackInterfaceImpl').load();
                await require('JSResourceForInteraction')('WAWebVoipStackInterfaceWeb').load();
                await require('JSResourceForInteraction')('WAWebVoipInit').load();
                await require('JSResourceForInteraction')('WAWebVoipGatingUtils').load();
                const gating = require('WAWebVoipGatingUtils');
                gating.isCallingEnabled = () => true;
                gating.isGroupCallingEnabled = () => true;
                gating.isVoipDownloadEnabled = () => true;
                gating.callLinksEnabled = () => true;
                gating.isUnsupportedBrowserForWebCalling = () => false;
                gating.getUnsupportedBrowserReason = () => null;
                // Force the web stack so the impl singleton
                // doesn't fall back to a Windows / native
                // bridge on Windows hosts.
                const impl = require('WAWebVoipStackInterfaceImpl');
                const webStack = require('WAWebVoipStackInterfaceWeb');
                impl.getVoipStackInterfaceImpl =
                        () => webStack.createWAWebVoipStackInterface();
                await require('WAWebVoipInit').initWAWebVoip();
                window.__voipInitState = 'done';
            } catch (e) { window.__voipInitState = 'err: ' + e.message; }
        })()`,
        awaitPromise: false
    }, pageSession);

    // Now stay attached so the worker auto-attaches happening
    // *during* voipInit get our hook before the codec touches any
    // primitive.
    await new Promise((r) => setTimeout(r, 20000));

    // Belt-and-suspenders: also enumerate every current target and
    // explicitly attach to anything we missed (e.g. workers spawned
    // before our auto-attach handler was wired, or workers whose
    // attachedToTarget event landed on the root rather than a
    // child session).
    const finalTargets = await cdp.send('Target.getTargets', {});
    const voipWorkers = finalTargets.targetInfos.filter(
        (t) => (t.type === 'worker' || t.type === 'shared_worker')
                && (t.title || '').includes('WAWebVoip'));
    console.log(`final pass: ${voipWorkers.length} Voip workers to ensure-hook.`);
    for (const w of voipWorkers) {
        try {
            const attach = await cdp.send('Target.attachToTarget',
                    {targetId: w.targetId, flatten: true});
            if (handled.has(attach.sessionId)) continue;
            handled.add(attach.sessionId);
            await cdp.send('Runtime.evaluate', {expression: HOOK}, attach.sessionId);
            console.log(`  manually hooked worker ${w.targetId}`);
        } catch (e) {
            console.warn(`  attach failed for ${w.targetId}: ${e.message}`);
        }
    }

    console.log('all setup done; STAYING CONNECTED to catch workers spawned during a call.');
    console.log('press Ctrl+C to disconnect.');
    // Hold the connection open so any workers spawned later (e.g.,
    // the SCTP DCThread pthread which is created when an actual call
    // starts) flow through our auto-attach handler and get the hook
    // installed before their first script runs.
    await new Promise(() => {}); // never resolve
}

main().catch((e) => {
    console.error('failed:', e);
    process.exit(1);
});
