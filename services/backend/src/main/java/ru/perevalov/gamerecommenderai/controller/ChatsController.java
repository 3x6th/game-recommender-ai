package ru.perevalov.gamerecommenderai.controller;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import ru.perevalov.gamerecommenderai.dto.chat.ChatPaginationResponse;
import ru.perevalov.gamerecommenderai.entity.User;
import ru.perevalov.gamerecommenderai.service.ChatsService;


@Slf4j
@RestController
@RequestMapping("/api/v1/chats")
@RequiredArgsConstructor
public class ChatsController {

    private final ChatsService chatsService;


    @GetMapping
    public Flux<ChatPaginationResponse> getUserChats(@RequestParam(defaultValue = "1") Integer page,
                                                     @RequestParam(defaultValue = "2") Integer size, @AuthenticationPrincipal User user) {
        return chatsService.getUserChats(page,size,user.getId());
    }

}