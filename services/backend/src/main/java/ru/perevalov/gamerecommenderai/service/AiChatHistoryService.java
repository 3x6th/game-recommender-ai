package ru.perevalov.gamerecommenderai.service;

import static ru.perevalov.gamerecommenderai.message.MessageMetaFields.CARDS_ITEMS;
import static ru.perevalov.gamerecommenderai.message.MessageMetaFields.FIELD_PAYLOAD;
import static ru.perevalov.gamerecommenderai.message.MessageMetaFields.FIELD_TYPE;
import static ru.perevalov.gamerecommenderai.message.MessageMetaFields.ITEM_KIND;
import static ru.perevalov.gamerecommenderai.message.MessageMetaFields.ITEM_KIND_GAME;
import static ru.perevalov.gamerecommenderai.message.MessageMetaFields.ITEM_TEXT;
import static ru.perevalov.gamerecommenderai.message.MessageMetaFields.REPLY_TEXT;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import ru.perevalov.gamerecommenderai.dto.AiChatHistoryMessage;
import ru.perevalov.gamerecommenderai.entity.ChatMessage;
import ru.perevalov.gamerecommenderai.repository.ChatMessageRepository;

/**
 * Builds a bounded, provider-neutral short-term chat history for an AI request.
 */
@Slf4j
@Service
public class AiChatHistoryService {

    private final ChatMessageRepository chatMessageRepository;
    private final int maxMessages;
    private final int maxChars;

    public AiChatHistoryService(
            ChatMessageRepository chatMessageRepository,
            @Value("${app.recommender.history.max-messages:10}") int maxMessages,
            @Value("${app.recommender.history.max-chars:8000}") int maxChars
    ) {
        this.chatMessageRepository = chatMessageRepository;
        this.maxMessages = Math.max(0, maxMessages);
        this.maxChars = Math.max(0, maxChars);
    }

    public Mono<List<AiChatHistoryMessage>> load(UUID chatId, UUID currentUserMessageId) {
        if (chatId == null || currentUserMessageId == null || maxMessages == 0 || maxChars == 0) {
            return Mono.just(List.of());
        }

        return chatMessageRepository.findAiHistoryBeforeMessage(chatId, currentUserMessageId, maxMessages)
                .mapNotNull(this::toHistoryMessage)
                .collectList()
                .map(this::applyCharBudgetAndOrder)
                .doOnNext(history -> log.debug(
                        "Built AI chat history chatId={} messageCount={} charCount={}",
                        chatId,
                        history.size(),
                        history.stream().mapToInt(message -> message.text().length()).sum()
                ));
    }

    private AiChatHistoryMessage toHistoryMessage(ChatMessage message) {
        AiChatHistoryMessage.Role role = switch (message.getRole()) {
            case USER -> AiChatHistoryMessage.Role.USER;
            case ASSISTANT -> AiChatHistoryMessage.Role.ASSISTANT;
            default -> null;
        };
        if (role == null) {
            return null;
        }

        String text = role == AiChatHistoryMessage.Role.USER
                ? normalize(message.getContent())
                : assistantText(message);
        return text.isBlank() ? null : new AiChatHistoryMessage(role, text);
    }

    private String assistantText(ChatMessage message) {
        String content = normalize(message.getContent());
        JsonNode meta = message.getMeta();
        if (meta == null || meta.isNull()) {
            return content;
        }

        String type = meta.path(FIELD_TYPE).asText("");
        JsonNode payload = meta.path(FIELD_PAYLOAD);
        if ("reply".equals(type)) {
            return firstNonBlank(content, payload.path(REPLY_TEXT).asText(""));
        }
        if (!"cards".equals(type)) {
            return content;
        }

        List<String> narrative = new ArrayList<>();
        List<String> games = new ArrayList<>();
        JsonNode items = payload.path(CARDS_ITEMS);
        if (items.isArray()) {
            for (JsonNode item : items) {
                String kind = item.path(ITEM_KIND).asText("");
                if (ITEM_KIND_GAME.equals(kind)) {
                    String title = normalize(item.path("title").asText(""));
                    String genre = normalize(item.path("genre").asText(""));
                    String why = normalize(item.path("whyRecommended").asText(""));
                    if (!title.isBlank()) {
                        StringBuilder game = new StringBuilder(title);
                        if (!genre.isBlank()) {
                            game.append(" (").append(genre).append(')');
                        }
                        if (!why.isBlank()) {
                            game.append(": ").append(why);
                        }
                        games.add(game.toString());
                    }
                } else {
                    String text = normalize(item.path(ITEM_TEXT).asText(""));
                    if (!text.isBlank()) {
                        narrative.add(text);
                    }
                }
            }
        }

        StringBuilder result = new StringBuilder(content);
        appendSection(result, String.join(" ", narrative));
        if (!games.isEmpty()) {
            appendSection(result, "Recommended games: " + String.join("; ", games));
        }
        return result.toString();
    }

    private List<AiChatHistoryMessage> applyCharBudgetAndOrder(List<AiChatHistoryMessage> newestFirst) {
        List<AiChatHistoryMessage> selected = new ArrayList<>();
        int usedChars = 0;
        for (AiChatHistoryMessage message : newestFirst) {
            int remaining = maxChars - usedChars;
            if (remaining <= 0) {
                break;
            }
            String text = message.text();
            if (text.length() > remaining) {
                if (!selected.isEmpty()) {
                    break;
                }
                text = text.substring(0, remaining);
            }
            selected.add(new AiChatHistoryMessage(message.role(), text));
            usedChars += text.length();
        }
        Collections.reverse(selected);
        return List.copyOf(selected);
    }

    private static void appendSection(StringBuilder target, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!target.isEmpty()) {
            target.append('\n');
        }
        target.append(value);
    }

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : normalize(second);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
