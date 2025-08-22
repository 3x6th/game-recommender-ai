package ru.perevalov.gamerecommenderai.security;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum UserRole {
    GUEST_USER,
    SIMPLE_USER
}
