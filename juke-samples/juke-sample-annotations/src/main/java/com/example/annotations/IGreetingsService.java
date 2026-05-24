package com.example.annotations;

/**
 * One of two example service interfaces used to demonstrate multi-service
 * composition under {@code @Juke}.
 */
public interface IGreetingsService {
    Greeting greeting(String name);
}
