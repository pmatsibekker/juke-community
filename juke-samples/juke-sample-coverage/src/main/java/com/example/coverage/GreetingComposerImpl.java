package com.example.coverage;

import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;

/**
 * "Real" implementation of {@link IGreetingComposer}. Sits behind a {@code @Juke}
 * seam in {@link GreetingDao}, so {@code juke-coverage} excludes this class
 * (and any nested classes) from the coverage figure — it would otherwise show
 * as 0% in replay mode through no fault of the developer.
 *
 * <p>This class appears in {@code excludedSeams} on the
 * {@code /service/coverage/server} response, formatted as
 * {@code "com.example.coverage.IGreetingComposer -> com.example.coverage.GreetingComposerImpl"}.
 */
@Service
public class GreetingComposerImpl implements IGreetingComposer {

    private final AtomicLong counter = new AtomicLong();

    @Override
    public Greeting compose(String name, String style) {
        return new Greeting(counter.incrementAndGet(),
                "[" + style + "] greeting #" + counter.get() + " for " + name);
    }
}
