package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;
import com.github.auties00.cobalt.wam.type.PttResultType;
import com.github.auties00.cobalt.wam.type.PttSourceType;

import java.time.Instant;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

@WamEvent(id = 458)
public interface PttEvent extends WamEventSpec {
    @WamProperty(index = 15, type = WamType.BOOLEAN)
    Optional<Boolean> isMetaAiThread();

    @WamProperty(index = 17, type = WamType.TIMER)
    Optional<Instant> pttAuddevRecorderAvgCbT();

    @WamProperty(index = 18, type = WamType.TIMER)
    Optional<Instant> pttAuddevRecorderInitT();

    @WamProperty(index = 19, type = WamType.TIMER)
    Optional<Instant> pttAuddevRecorderStartT();

    @WamProperty(index = 20, type = WamType.TIMER)
    Optional<Instant> pttAuddevRecorderStopT();

    @WamProperty(index = 16, type = WamType.INTEGER)
    OptionalInt pttAudioEngine();

    @WamProperty(index = 11, type = WamType.FLOAT)
    OptionalDouble pttAvgNoiseLoudness();

    @WamProperty(index = 12, type = WamType.FLOAT)
    OptionalDouble pttAvgNoiseLoudnessReduction();

    @WamProperty(index = 13, type = WamType.FLOAT)
    OptionalDouble pttAvgSpeechLoudness();

    @WamProperty(index = 14, type = WamType.FLOAT)
    OptionalDouble pttAvgSpeechLoudnessReduction();

    @WamProperty(index = 7, type = WamType.INTEGER)
    OptionalInt pttDraftPlayCnt();

    @WamProperty(index = 8, type = WamType.INTEGER)
    OptionalInt pttDraftSeekCnt();

    @WamProperty(index = 5, type = WamType.TIMER)
    Optional<Instant> pttDuration();

    @WamProperty(index = 4, type = WamType.BOOLEAN)
    Optional<Boolean> pttLock();

    @WamProperty(index = 9, type = WamType.INTEGER)
    OptionalInt pttPauseCnt();

    @WamProperty(index = 21, type = WamType.FLOAT)
    OptionalDouble pttRecorderCbBucketGte20msPct();

    @WamProperty(index = 22, type = WamType.FLOAT)
    OptionalDouble pttRecorderCbBucketLt10msPct();

    @WamProperty(index = 23, type = WamType.FLOAT)
    OptionalDouble pttRecorderCbBucketLt15msPct();

    @WamProperty(index = 24, type = WamType.FLOAT)
    OptionalDouble pttRecorderCbBucketLt20msPct();

    @WamProperty(index = 25, type = WamType.FLOAT)
    OptionalDouble pttRecorderCbBucketLt5msPct();

    @WamProperty(index = 26, type = WamType.TIMER)
    Optional<Instant> pttRecorderNoiseDurationMs();

    @WamProperty(index = 27, type = WamType.TIMER)
    Optional<Instant> pttRecorderSpeechDurationMs();

    @WamProperty(index = 1, type = WamType.ENUM)
    Optional<PttResultType> pttResult();

    @WamProperty(index = 3, type = WamType.FLOAT)
    OptionalDouble pttSize();

    @WamProperty(index = 2, type = WamType.ENUM)
    Optional<PttSourceType> pttSource();

    @WamProperty(index = 6, type = WamType.BOOLEAN)
    Optional<Boolean> pttStop();

    @WamProperty(index = 10, type = WamType.INTEGER)
    OptionalInt pttStopTapCnt();
}
