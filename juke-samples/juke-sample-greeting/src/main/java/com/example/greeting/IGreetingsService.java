package com.example.greeting;

/**
 * Service interface for greeting generation. Juke intercepts calls
 * through this interface — methods on it are what get recorded and
 * replayed.
 */
public interface IGreetingsService {
    Greeting greeting(String name);
}
