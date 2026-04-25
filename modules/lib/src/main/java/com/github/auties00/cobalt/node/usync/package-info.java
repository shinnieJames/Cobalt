/**
 * USync query support for the {@code <iq xmlns="usync">} flow.
 *
 * <p>USync is WhatsApp's multi-user, multi-protocol query mechanism.
 * Given a list of peers, the relay returns per-protocol metadata for each
 * one — the canonical example being a device-list refresh.
 *
 * <p>Cobalt mirrors WhatsApp Web's structure 1-to-1:
 * <ul>
 *   <li>{@link com.github.auties00.cobalt.node.usync.UsyncQuery} —
 *       the builder/executor (constructed via static factories that
 *       require a starting protocol so a query is always well-formed at
 *       compile time),</li>
 *   <li>{@link com.github.auties00.cobalt.node.usync.UsyncUser} —
 *       per-peer entry; constructed via static factories
 *       ({@code byId}, {@code byPhoneNumber}, {@code byUsername},
 *       {@code byPhoneJid}) that guarantee at least one addressing
 *       slot is set,</li>
 *   <li>{@link com.github.auties00.cobalt.node.usync.UsyncProtocol}
 *       (sealed) — the protocol contract,</li>
 *   <li>{@link com.github.auties00.cobalt.node.usync.UsyncProtocolResult}
 *       (sealed) — the union of every per-user, per-protocol parser
 *       result,</li>
 *   <li>{@link com.github.auties00.cobalt.node.usync.UsyncProtocolError}
 *       — shared error variant of the result union,</li>
 *   <li>{@link com.github.auties00.cobalt.node.usync.UsyncBackoff} —
 *       the per-protocol backoff registry,</li>
 *   <li>{@link com.github.auties00.cobalt.node.usync.UsyncResult} +
 *       {@link com.github.auties00.cobalt.node.usync.UsyncUserResult} +
 *       {@link com.github.auties00.cobalt.node.usync.UsyncTopLevelError}
 *       — the aggregated parse output,</li>
 *   <li>{@link com.github.auties00.cobalt.node.usync.protocol} — the
 *       eleven concrete protocol implementations,</li>
 *   <li>{@link com.github.auties00.cobalt.node.usync.result} — the
 *       eleven concrete success-result types.</li>
 * </ul>
 *
 * @implNote WAWebUsync, WAWebUsyncBackoff, WAWebUsyncUser, and the eleven
 *     {@code WAWebUsync*Protocol} JS modules.
 */
@WhatsAppWebModule(moduleName = "WAWebUsync")
package com.github.auties00.cobalt.node.usync;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
