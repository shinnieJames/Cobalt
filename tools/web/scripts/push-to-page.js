#!/usr/bin/env node
/**
 * Pushes a binary file to a running page's window in chunks
 * via CDP. The page receives the file as a base64 string at
 * window[globalVar]; a follow-up eval can decode + use it.
 *
 * Usage: node push-to-page.js [cdpPort] [globalVar] [filePath]
 */
'use strict';

const fs = require('fs');
const cdpPort = parseInt(process.argv[2] || '57545', 10);
const globalVar = process.argv[3] || '__mlowSynthWavB64';
const filePath = process.argv[4];
if (!filePath) { console.error('usage: push-to-page.js <port> <var> <path>'); process.exit(1); }
const CHUNK = 65536;

class CDP {
    constructor(ws) {
        this.ws = ws; this.nextId = 1; this.pending = new Map();
        ws.onmessage = (ev) => {
            const m = JSON.parse(ev.data);
            if (m.id && this.pending.has(m.id)) {
                const {resolve, reject} = this.pending.get(m.id);
                this.pending.delete(m.id);
                if (m.error) reject(new Error(m.error.message)); else resolve(m.result);
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
}

async function main() {
    const bytes = fs.readFileSync(filePath);
    const b64 = bytes.toString('base64');
    const total = b64.length;
    console.log(`pushing ${bytes.length} bytes (${total} b64 chars) → window.${globalVar}`);

    const ver = await (await fetch(`http://127.0.0.1:${cdpPort}/json/version`)).json();
    const ws = new WebSocket(ver.webSocketDebuggerUrl);
    await new Promise(r => { ws.onopen = r; });
    const cdp = new CDP(ws);

    const targets = await cdp.send('Target.getTargets', {});
    const page = targets.targetInfos.find(
        t => t.type === 'page' && (t.url || '').startsWith('https://web.whatsapp.com'));
    if (!page) throw new Error('no whatsapp page');
    const att = await cdp.send('Target.attachToTarget', {targetId: page.targetId, flatten: true});
    const sid = att.sessionId;

    // Initialise the global to an empty string.
    await cdp.send('Runtime.evaluate', {
        expression: `window.${globalVar} = '';`,
        returnByValue: true
    }, sid);

    for (let off = 0; off < total; off += CHUNK) {
        const slice = b64.slice(off, Math.min(off + CHUNK, total));
        // Append. Wrap in a JSON string literal so embedded
        // characters are safely escaped.
        await cdp.send('Runtime.evaluate', {
            expression: `window.${globalVar} += ${JSON.stringify(slice)};`,
            returnByValue: true
        }, sid);
        process.stdout.write(`  pushed ${off + slice.length}/${total}\r`);
    }
    console.log(`\npushed ${total} chars to window.${globalVar}`);
    ws.close();
}

main().catch(e => { console.error(e); process.exit(1); });
