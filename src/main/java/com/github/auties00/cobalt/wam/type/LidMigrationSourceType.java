package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum LidMigrationSourceType {
    @WamEnumConstant(1) PEER,
    @WamEnumConstant(2) HISTORY
}
