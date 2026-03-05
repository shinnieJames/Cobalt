package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum SnackbarActionType {
    @WamEnumConstant(0) SNACKBAR_SHOWN,
    @WamEnumConstant(1) MESSAGE_UNDELETE
}
