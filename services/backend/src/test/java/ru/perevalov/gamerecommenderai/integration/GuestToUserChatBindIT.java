package ru.perevalov.gamerecommenderai.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import ru.perevalov.gamerecommenderai.entity.Chats;
import ru.perevalov.gamerecommenderai.entity.User;
import ru.perevalov.gamerecommenderai.entity.enums.ChatStatus;
import ru.perevalov.gamerecommenderai.repository.ChatsRepository;
import ru.perevalov.gamerecommenderai.repository.UserRepository;
import ru.perevalov.gamerecommenderai.security.model.UserRole;
import ru.perevalov.gamerecommenderai.service.ChatsService;

/**
 * Интеграционный тест биндинга гостевых чатов к пользователю.
 */
class GuestToUserChatBindIT extends IntegrationTestBase {

    @Autowired
    private ChatsService chatsService;

    @Autowired
    private ChatsRepository chatsRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void bindGuestChatsToUser_movesSessionChatsToUser() {
        String sessionId = UUID.randomUUID().toString();
        User savedUser = saveUser(76561198000000000L);

        Chats savedChat = saveGuestChat(sessionId);

        Integer updated = chatsService.bindGuestChatsToUser(sessionId, savedUser.getId()).block();
        assertThat(updated).isNotNull();
        assertThat(updated).isGreaterThanOrEqualTo(1);

        Chats updatedChat = chatsRepository.findById(savedChat.getId()).block();
        assertThat(updatedChat).isNotNull();
        assertThat(updatedChat.getUserId()).isEqualTo(savedUser.getId());
        assertThat(updatedChat.getSessionId()).isNull();
    }

    @Test
    void bindGuestChatsToUser_noChats_returnsZero() {
        String sessionId = UUID.randomUUID().toString();
        User savedUser = saveUser(76561198000000001L);

        Integer updated = chatsService.bindGuestChatsToUser(sessionId, savedUser.getId()).block();

        assertThat(updated).isNotNull();
        assertThat(updated).isZero();
    }

    @Test
    void bindGuestChatsToUser_multipleChats_movesAll() {
        String sessionId = UUID.randomUUID().toString();
        User savedUser = saveUser(76561198000000002L);

        List<Chats> chats = List.of(
                saveGuestChat(sessionId),
                saveGuestChat(sessionId),
                saveGuestChat(sessionId)
        );

        Integer updated = chatsService.bindGuestChatsToUser(sessionId, savedUser.getId()).block();

        assertThat(updated).isNotNull();
        assertThat(updated).isEqualTo(3);

        for (Chats chat : chats) {
            Chats updatedChat = chatsRepository.findById(chat.getId()).block();
            assertThat(updatedChat).isNotNull();
            assertThat(updatedChat.getUserId()).isEqualTo(savedUser.getId());
            assertThat(updatedChat.getSessionId()).isNull();
        }
    }

    @Test
    void bindGuestChatsToUser_repeat_isIdempotent() {
        String sessionId = UUID.randomUUID().toString();
        User savedUser = saveUser(76561198000000003L);

        saveGuestChat(sessionId);

        Integer first = chatsService.bindGuestChatsToUser(sessionId, savedUser.getId()).block();
        Integer second = chatsService.bindGuestChatsToUser(sessionId, savedUser.getId()).block();

        assertThat(first).isNotNull();
        assertThat(first).isGreaterThanOrEqualTo(1);
        assertThat(second).isNotNull();
        assertThat(second).isZero();
    }

    @Test
    void bindGuestChatsToUser_nullSessionId_returnsZero() {
        User savedUser = saveUser(76561198000000004L);

        Integer updated = chatsService.bindGuestChatsToUser(null, savedUser.getId()).block();

        assertThat(updated).isNotNull();
        assertThat(updated).isZero();
    }

    private User saveUser(long steamId) {
        User user = new User(steamId, UserRole.USER);
        user.setActive(true);
        return userRepository.save(user).block();
    }

    private Chats saveGuestChat(String sessionId) {
        Chats chat = new Chats();
        chat.setSessionId(sessionId);
        chat.setStatus(ChatStatus.ACTIVE);
        return chatsRepository.save(chat).block();
    }
}
