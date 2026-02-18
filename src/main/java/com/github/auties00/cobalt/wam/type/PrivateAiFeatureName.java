package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum PrivateAiFeatureName {
    @WamEnumConstant(0) SUMMARIZATION,
    @WamEnumConstant(1) WRITE_WITH_AI,
    @WamEnumConstant(2) PSI,
    @WamEnumConstant(3) QUICK_RECAP,
    @WamEnumConstant(4) INCONGNITO,
    @WamEnumConstant(5) SIDE_CHAT,
    @WamEnumConstant(6) GROUP_AI,
    @WamEnumConstant(7) PSI_SEARCH
}
