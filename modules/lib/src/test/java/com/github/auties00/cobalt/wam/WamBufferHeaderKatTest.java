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
 * Byte-identical agreement tests for the 8-byte WAM buffer header
 * against vectors captured from the live WhatsApp Web bundle's
 * {@code WAWebWamLibContext} module.
 *
 * <p>The header layout is fixed:
 *
 * <pre>{@code
 *     bytes 0..2  = "WAM" magic (ASCII)
 *     byte  3     = protocol version (currently 5)
 *     byte  4     = stream id (always 1 for the regular client stream)
 *     bytes 5..6  = sequence number, little-endian uint16
 *     byte  7     = channel byte (0=regular, 1=realtime, 2=private)
 * }</pre>
 *
 * <p>Each fixture row pins the bytes for a specific
 * {@code (channelByte, seqNum)} pair, covering the lower wrap boundary
 * ({@code 0xFFFF -> 1}) and the 256-boundary for big-endian byte
 * ordering bugs.
 *
 * <p>The test reconstructs the header inline using the same primitives
 * {@code WamService.writeHeader} uses (since the production method is
 * private). The end-to-end {@code WamService} path that writes this
 * header during a flush cycle is exercised separately by
 * {@code WamServiceTest}.
 *
 * <p>Vectors live in {@code fixtures/wam/wam-buffer-headers.json}; see
 * {@code tools/web/wam-fixtures/README.md} for the re-capture
 * procedure.
 */
@DisplayName("WAM buffer header KAT against live WhatsApp Web bundle")
class WamBufferHeaderKatTest {
    /**
     * Snapshot revision the vectors were captured against.
     */
    private static final long PINNED_SNAPSHOT_REVISION = 1039260921L;

    /**
     * Total header size in bytes.
     */
    private static final int HEADER_SIZE = 8;

    /**
     * Header magic bytes.
     */
    private static final byte[] WAM_MAGIC = {'W', 'A', 'M'};

    /**
     * Wire-format protocol version. Pinned to the
     * {@code WAM_PROTOCOL_VERSION} value captured at fixture creation;
     * the fixture header's {@code protocolVersion} entry is asserted
     * against this constant before per-vector tests run.
     */
    private static final int PROTOCOL_VERSION = 5;

    /**
     * Stream id, always {@code 1} for the regular client stream.
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
     * Builds the 8-byte header for the captured (channelByte, seqNum)
     * pair and asserts the produced hex matches the captured bytes.
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
