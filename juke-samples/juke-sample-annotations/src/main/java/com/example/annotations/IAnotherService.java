package com.example.annotations;

/**
 * Second example service interface — paired with {@link IGreetingsService}
 * so {@link JukeConstructorExample} can demonstrate multi-service
 * composition through Juke.
 */
public interface IAnotherService {
    String processRequest();
}
