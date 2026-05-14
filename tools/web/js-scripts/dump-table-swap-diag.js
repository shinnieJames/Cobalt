#!/usr/bin/env node
/**
 * Reads __mlowFnTableHookDiag / Slots / Errs from every responsive
 * Voip worker via CDP Debugger.pause + evaluateOnCallFrame.
 */
'use strict';

const cdpPort = parseInt(process.argv[2] || '63757', 10);
const PER_CALL_TIMEOUT_MS = 4000;

class CDP {
    constructor(ws) {
        this.ws = ws; this.nextId = 1; this.pending = new Map(); this.evHandlers = [];
        ws.onmessage = (ev) => {
            const m = JSON.parse(ev.data);
            if (m.id && this.pending.has(m.id)) {
                const {resolve, reject} = this.pending.get(m.id);
                this.pending.delete(m.id);
                if (m.error) reject(new Error(m.error.message)); else resolve(m.result);
            } else if (m.method) this.evHandlers.forEach(h => h(m));
        };
    }
    send(method, params, sessionId) {
        const id = this.nextId++;
        const p = new Promise((res, rej) => {
            this.pending.set(id, {resolve: res, reject: rej});
            const msg = {id, method, params: params || {}};
            if (sessionId) msg.sessionId = sessionId;
            this.ws.send(JSON.stringify(msg));
        });
        return Promise.race([p, new Promise((_, rej) =>
            setTimeout(() => { this.pending.delete(id); rej(new Error('timeout ' + method)); }, PER_CALL_TIMEOUT_MS))]);
    }
    waitFor(method, sessionId, ms) {
        return new Promise((resolve, reject) => {
            const t = setTimeout(() => reject(new Error('wait timeout')), ms);
            const h = (m) => { if (m.method === method && (!sessionId || m.sessionId === sessionId)) {
                this.evHandlers = this.evHandlers.filter(x => x !== h); clearTimeout(t); resolve(m); } };
            this.evHandlers.push(h);
        });
    }
}

async function main() {
    const ver = await (await fetch(`http://127.0.0.1:${cdpPort}/json/version`)).json();
    const ws = new WebSocket(ver.webSocketDebuggerUrl);
    await new Promise(r => { ws.onopen = r; });
    const cdp = new CDP(ws);

    const targets = await cdp.send('Target.getTargets', {});
    const workers = targets.targetInfos.filter(
        t => (t.type === 'worker' || t.type === 'shared_worker') &&
             (t.title || '').includes('WAWebVoip'));
    console.log(`probing ${workers.length} Voip workers...`);
    for (const w of workers) {
        const tag = w.targetId.slice(0, 8);
        let sid = null;
        try {
            const att = await cdp.send('Target.attachToTarget', {targetId: w.targetId, flatten: true});
            sid = att.sessionId;
            await cdp.send('Debugger.enable', {}, sid);
            const wait = cdp.waitFor('Debugger.paused', sid, PER_CALL_TIMEOUT_MS);
            await cdp.send('Debugger.pause', {}, sid);
            const paused = await wait;
            const cf = paused.params.callFrames && paused.params.callFrames[0];
            if (!cf) { await cdp.send('Debugger.resume', {}, sid).catch(()=>{}); continue; }
            const r = await cdp.send('Debugger.evaluateOnCallFrame', {
                callFrameId: cf.callFrameId,
                expression: 'JSON.stringify({diag: self.__mlowFnTableHookDiag, slots: self.__mlowFnTableHookSlots, errs: self.__mlowFnTableHookErrs, waSeen: self.__mlowWaSeen, swapEntered: self.__mlowSwapEntered, swapBail: self.__mlowSwapBail, exportSample: self.__mlowExportSample, wrapPath: self.__mlowWrapPath})',
                returnByValue: true
            }, sid);
            await cdp.send('Debugger.resume', {}, sid).catch(()=>{});
            if (r.result && r.result.value) {
                const v = JSON.parse(r.result.value);
                if (v.diag || (v.slots && v.slots.length) || (v.errs && v.errs.length) || v.waSeen) {
                    console.log(`  ${tag}: ${JSON.stringify(v)}`);
                }
            }
        } catch (e) {
            console.warn(`  ${tag}: ${e.message}`);
        } finally {
            if (sid) try { await cdp.send('Target.detachFromTarget', {sessionId: sid}); } catch {}
        }
    }
    ws.close();
}

main().catch(e => { console.error(e); process.exit(1); });
