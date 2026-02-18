package com.github.auties00.cobalt.wam;

import com.github.auties00.cobalt.wam.event.WamEventSpec;

/**
 * A pending event awaiting flush, paired with its commit timestamp.
 *
 * @param event             the WAM event
 * @param commitTimeSeconds the Unix epoch seconds when the event was committed
 */
record WamPendingEvent(WamEventSpec event, long commitTimeSeconds) {

}
