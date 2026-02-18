package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum CtwaChatCreationMode {
    @WamEnumConstant(0) JID,
    @WamEnumConstant(1) LID
}
