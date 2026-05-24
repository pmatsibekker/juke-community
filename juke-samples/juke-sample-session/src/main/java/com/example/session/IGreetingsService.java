package com.example.session;

/**
 * Service interface that the session-aware Juke proxy wraps.
 */
public interface IGreetingsService {
    Greeting greeting(String name);
}
