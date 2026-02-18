package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum ChatsFolderType {
    @WamEnumConstant(1) INBOX,
    @WamEnumConstant(2) ARCHIVED
}
