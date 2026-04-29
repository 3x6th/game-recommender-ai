package ru.perevalov.gamerecommenderai.message;

import java.util.Arrays;

import lombok.RequiredArgsConstructor;

/**
 * Типы meta-сообщений, сериализуемые в поле {@code type}.
 */
@RequiredArgsConstructor
public enum MessageMetaType {
    REPLY("reply"),
    CARDS("cards"),
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
