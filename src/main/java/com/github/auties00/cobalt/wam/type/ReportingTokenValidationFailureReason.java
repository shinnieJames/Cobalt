package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum ReportingTokenValidationFailureReason {
    @WamEnumConstant(0) MISSING_MESSAGE_SECRET,
    @WamEnumConstant(1) EMPTY_REPORTING_TOKEN_CONTENT,
    @WamEnumConstant(2) MISMATCH_REPORTING_TOKEN,
    @WamEnumConstant(3) UNSUPPORTED_VERSION
}
