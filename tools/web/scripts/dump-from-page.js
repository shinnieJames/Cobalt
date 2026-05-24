#!/usr/bin/env node
/**
 * Reads a staged JSON string from the page's window in chunks
 * via CDP and writes it to a file. Used to bypass MCP eval size
 * limits when draining captures.
 *
 * Usage: node dump-from-page.js [cdpPort] [globalVar] [outFile]
 */
'use strict';

const fs = require('fs');
const cdpPort = parseInt(process.argv[2] || '57545', 10);
const globalVar = process.argv[3] || '__mlowDumpJson';
const outFile = process.argv[4] || `dump-${Date.now()}.json`;
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

    const lenRes = await cdp.send('Runtime.evaluate', {
        expression: `(window.${globalVar} && window.${globalVar}.length) || 0`,
        returnByValue: true
    }, sid);
    const total = lenRes.result.value;
    if (!total) {
        console.log('no data staged on window.' + globalVar);
        ws.close();
        return;
    }
    console.log(`reading ${total} chars in ${CHUNK}-char chunks...`);
    let acc = '';
    for (let off = 0; off < total; off += CHUNK) {
        const end = Math.min(off + CHUNK, total);
        const r = await cdp.send('Runtime.evaluate', {
            expression: `window.${globalVar}.slice(${off}, ${end})`,
            returnByValue: true
        }, sid);
        if (r.result && typeof r.result.value === 'string') {
            acc += r.result.value;
            process.stdout.write(`  read ${off}-${end} (${acc.length}/${total})\r`);
        } else {
            console.log(`\nunexpected result @${off}:`, r);
            break;
        }
    }
    console.log(`\ngot ${acc.length} chars`);
    fs.writeFileSync(outFile, acc);
    console.log(`wrote ${outFile}`);
    ws.close();
}

main().catch(e => { console.error(e); process.exit(1); });
