package com.example.greeting;

import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;

/**
 * "Real" upstream greeting implementation. In record mode, calls to
 * {@link #greeting(String)} are captured into the active track; in
 * replay mode, this implementation is bypassed entirely and the
 * recorded response is returned instead.
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
