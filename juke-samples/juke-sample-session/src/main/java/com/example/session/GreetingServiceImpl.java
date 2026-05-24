package com.example.session;

import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Real upstream service. The session-aware proxy in
 * {@code SessionGreetingController} only delegates here when no Juke
 * session cookies are present on the request.
 */
@Service
public class GreetingServiceImpl implements IGreetingsService {

    private static final String TEMPLATE = "Hello, %s!";

    private final AtomicLong counter = new AtomicLong();

    @Override
    public Greeting greeting(String name) {
        return new Greeting(counter.incrementAndGet(), String.format(TEMPLATE, name));
    }
}
