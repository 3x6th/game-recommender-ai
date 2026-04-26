package ru.perevalov.gamerecommenderai.service;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.perevalov.gamerecommenderai.entity.Chats;
import ru.perevalov.gamerecommenderai.entity.enums.ChatStatus;
import ru.perevalov.gamerecommenderai.exception.ErrorType;
import ru.perevalov.gamerecommenderai.exception.GameRecommenderException;
import ru.perevalov.gamerecommenderai.repository.ChatsRepository;

@ExtendWith(MockitoExtension.class)
class ChatsServiceTest {

    @Mock
    private ChatsRepository chatsRepository;

    @Mock
    private ChatPageService chatPageService;

    @Test
    void getOrCreateChatId_whenOwnedUserChat_thenReturnsSameId() {
        UUID chatId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        RequestContext ctx = RequestContext.forUser(userId, 123L, null);
        Chats chat = new Chats();
        chat.setId(chatId);
        chat.setUserId(userId);
        chat.setStatus(ChatStatus.ACTIVE);

        when(chatsRepository.findByIdAndUserId(chatId, userId)).thenReturn(Mono.just(chat));

        ChatsService service = new ChatsService(chatsRepository, chatPageService);

        StepVerifier.create(service.getOrCreateChatId(chatId, ctx))
                .assertNext(id -> assertThat(id).isEqualTo(chatId))
                .verifyComplete();

        verify(chatsRepository).findByIdAndUserId(chatId, userId);
    }

    @Test
    void requireOwnership_whenNotOwned_thenFails() {
        UUID chatId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        RequestContext ctx = RequestContext.forUser(userId, 123L, null);

        when(chatsRepository.findByIdAndUserId(chatId, userId)).thenReturn(Mono.empty());

        ChatsService service = new ChatsService(chatsRepository, chatPageService);

        StepVerifier.create(service.requireOwnership(chatId, ctx))
                .expectErrorSatisfies(ex -> {
                    assertThat(ex).isInstanceOf(GameRecommenderException.class);
                    GameRecommenderException gre = (GameRecommenderException) ex;
                    assertThat(gre.getErrorType()).isEqualTo(ErrorType.CHAT_NOT_FOUND);
                })
                .verify();
    }
}
