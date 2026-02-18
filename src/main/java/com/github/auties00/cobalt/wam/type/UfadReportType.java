package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum UfadReportType {
    @WamEnumConstant(1) NSUSERDEFAULT,
    @WamEnumConstant(2) MMAP,
    @WamEnumConstant(3) NSUSERDEFAULT_AND_MMAP
}
