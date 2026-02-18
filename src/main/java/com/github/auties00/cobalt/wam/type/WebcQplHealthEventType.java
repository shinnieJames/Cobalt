package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum WebcQplHealthEventType {
    @WamEnumConstant(1) ANNOTATION_SIZE_LIMIT_EXCEEDED,
    @WamEnumConstant(2) MAX_POINT_COUNT_EXCEEDED,
    @WamEnumConstant(3) MAX_MARKER_COUNT_EXCEEDED,
    @WamEnumConstant(4) TOO_MANY_OPEN_MARKERS_TO_WRITE,
    @WamEnumConstant(5) POINT_TO_END_AT_NOT_FOUND,
    @WamEnumConstant(6) JSON_FORMAT_ERROR,
    @WamEnumConstant(7) MAX_STORAGE_EVENT_COUNT_REACHED,
    @WamEnumConstant(8) ERROR_UPLOADING_CHUNK,
    @WamEnumConstant(9) POINT_NAME_TOO_LONG,
    @WamEnumConstant(10) ANNOTATION_KEY_TOO_LONG,
    @WamEnumConstant(11) POINT_DATA_TOO_LONG,
    @WamEnumConstant(12) ERROR_PARSING_CONFIG
}
