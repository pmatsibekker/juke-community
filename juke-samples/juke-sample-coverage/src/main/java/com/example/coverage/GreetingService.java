package com.example.coverage;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Application logic — picks a greeting branch based on style. Three branches
 * (formal / casual / royal) means JaCoCo counts three separate code paths;
 * the demo flow exposes only two of them in the typical happy path, so the
 * server line and branch percentages stay below 100% and the threshold gate
 * has something meaningful to evaluate.
 *
 * <p>The deliberately less-traveled "royal" branch shows up red in the JaCoCo
 * drill-down — click it in the dashboard to watch that branch fill in.
 */
@Service
public class GreetingService {

    @Autowired
    private GreetingDao dao;

    @Autowired
    private Auditor auditor;

    public List<String> styles() {
        return List.of("formal", "casual", "royal");
    }

    public Greeting greet(String name, String style) {
        // Validation branch — exercised by the happy path indirectly.
        if (name == null || name.isBlank()) {
            return new Greeting(0, "Please tell us your name first.");
        }

        // Three style branches. The demo flow lights up formal + casual on
        // a typical run; the "royal" branch stays red until you pick it.
        String normalised = style == null ? "casual" : style.toLowerCase();
        Greeting greeting;
        switch (normalised) {
            case "formal" -> {
                greeting = dao.compose(name, "Formal");
                auditor.audit("formal", name);
            }
            case "royal" -> {
                greeting = dao.compose(name, "Royal");
                auditor.audit("royal", name);
            }
            default -> {
                // "casual" or anything unrecognised falls through to here.
                greeting = dao.compose(name, "Casual");
                auditor.audit("casual", name);
            }
        }
        return greeting;
    }
}
