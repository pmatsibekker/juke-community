package com.example.statusgrid;

import org.springframework.stereotype.Service;

/**
 * The real upstream. Under replay it is never invoked — Juke serves the
 * recorded responses, and each replayed call advances the session's
 * lastCall / percentComplete.
 */
@Service
public class GreetingServiceImpl implements GreetingService {
    @Override
    public String greet(String name) {
        return "Hello, " + name + "!";
    }
}
