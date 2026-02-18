package ru.perevalov.gamerecommenderai.dto.message;

import lombok.RequiredArgsConstructor;

import java.util.Arrays;

@RequiredArgsConstructor
public enum MessageMetaType {
    REPLY("reply"),
    CARDS("cards"),
    MIXED("mixed"),
    STATUS("status"),
    ERROR("error"),
    TOOL_CALL("tool_call"),
    TOOL_RESULT("tool_result");

    private final String wireName;

    public String wireName() {
        return wireName;
    }

    public static MessageMetaType fromWireName(String wireName) {
        return Arrays.stream(values())
                .filter(type -> type.wireName.equals(wireName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown MessageMetaType wireName: '" + wireName + "'. Expected one of: "
                                + Arrays.toString(Arrays.stream(values()).map(MessageMetaType::wireName).toArray())
                ));
    }
}
