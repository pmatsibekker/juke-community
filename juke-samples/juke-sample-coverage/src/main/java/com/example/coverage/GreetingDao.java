package com.example.coverage;

import org.juke.framework.annotation.Juke;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Field-level {@code @Juke} usage — Spring autowires the real
 * {@link IGreetingComposer} bean here, then {@code JukeBeanPostProcessor}
 * replaces it with a record/replay proxy before this DAO is exposed to
 * consumers. {@link GreetingComposerImpl} is therefore the displaced class
 * that {@code juke-coverage} excludes from the coverage figure.
 */
@Service
public class GreetingDao {

    @Juke("juke")
    IGreetingComposer composer;

    @Autowired
    public GreetingDao(IGreetingComposer composer) {
        this.composer = composer;
    }

    public Greeting compose(String name, String style) {
        return composer.compose(name, style);
    }
}
