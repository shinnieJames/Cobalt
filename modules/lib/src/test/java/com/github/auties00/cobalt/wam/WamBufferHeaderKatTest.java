package com.github.auties00.cobalt.wam;

import com.alibaba.fastjson2.JSONObject;
import com.github.auties00.cobalt.util.DataUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * Byte-identical KAT for the 8-byte WAM buffer header against vectors
 * captured from {@code WAWebWamLibContext}.
 *
 * @apiNote
 * Pins the on-the-wire layout {@code WamService} produces at the head
 * of every upload buffer: {@code "WAM"} magic, protocol version,
 * stream id, little-endian sequence number, channel byte. Vectors live
 * in {@code fixtures/wam/wam-buffer-headers.json} and pin snapshot
 * revision {@code 1039260921}. The end-to-end {@code WamService} flush
 * path that emits this header sits in {@code WamServiceTest}; this
 * file checks the bytes only.
 *
 * @implNote
 * Reconstructs the header in-line via {@link DataUtils#putShort} (the
 * same primitive {@code WamService.writeHeader} uses), because the
 * production method is private and exercising it from outside the
 * package would require a permission edit. Vector ordering covers the
 * {@code 0xFFFF -> 1} lower wrap and the 256-boundary, which are the
 * two cases a big-endian regression would surface first.
 */
@DisplayName("WAM buffer header KAT against live WhatsApp Web bundle")
class WamBufferHeaderKatTest {
    /**
     * The snapshot revision the KAT vectors were captured against;
     * compared against the fixture header so revision drift fails
     * loudly.
     */
    private static final long PINNED_SNAPSHOT_REVISION = 1039260921L;

    /**
     * The fixed 8-byte header length.
     */
    private static final int HEADER_SIZE = 8;

    /**
     * The {@code "WAM"} magic bytes at the start of every buffer.
     */
    private static final byte[] WAM_MAGIC = {'W', 'A', 'M'};

    /**
     * The wire protocol version pinned at fixture capture; cross-checked
     * against the fixture's own {@code protocolVersion} entry before
     * any vector runs so a server-side bump fails loudly rather than
     * surfacing as a per-vector byte mismatch.
     */
    private static final int PROTOCOL_VERSION = 5;

    /**
     * The stream id; always {@code 1} for the regular client stream.
     */
    private static final int STREAM_ID = 1;

    /**
     * Returns one dynamic test per captured (channelByte, seqNum)
     * combination.
     *
     * @return the test factory stream
     */
    @TestFactory
    List<DynamicTest> headerBytesAgreeWithLiveBundle() {
        var fixture = WamFixtures.loadOracle("wam-buffer-headers");
        WamFixtures.requireSnapshotRevision(fixture, PINNED_SNAPSHOT_REVISION);
        var protocolVersion = fixture.getIntValue("protocolVersion");
        assertEquals(PROTOCOL_VERSION, protocolVersion,
                "protocol version drift: fixture pinned to " + protocolVersion
                        + " but Cobalt declares " + PROTOCOL_VERSION);

        var vectors = fixture.getJSONArray("vectors");
        var tests = new ArrayList<DynamicTest>(vectors.size());
        for (var entry : vectors) {
            var vector = (JSONObject) entry;
            var label = vector.getString("channelName") + "_seq" + vector.getIntValue("seqNum");
            tests.add(dynamicTest(label, () -> assertVectorAgrees(vector)));
        }
        return tests;
    }

    /**
     * Builds the 8-byte header for the captured
     * (channelByte, seqNum) pair and asserts the produced hex matches
     * the captured bytes.
     *
     * @param vector the captured header descriptor
     */
    private static void assertVectorAgrees(JSONObject vector) {
        var channelByte = vector.getIntValue("channelByte");
        var seqNum = vector.getIntValue("seqNum");
        var expectedHex = vector.getString("bytes");

        var headerBytes = new byte[HEADER_SIZE];
        System.arraycopy(WAM_MAGIC, 0, headerBytes, 0, WAM_MAGIC.length);
        headerBytes[3] = (byte) PROTOCOL_VERSION;
        headerBytes[4] = (byte) STREAM_ID;
        DataUtils.putShort(headerBytes, 5, (short) seqNum, ByteOrder.LITTLE_ENDIAN);
        headerBytes[7] = (byte) channelByte;

        var actualHex = HexFormat.of().formatHex(headerBytes);
        assertEquals(expectedHex, actualHex,
                () -> "header bytes mismatch for channelByte=" + channelByte + " seqNum=" + seqNum);
    }
}
