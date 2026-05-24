package com.example.session;

import org.juke.framework.annotation.Juke;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cookie-session-aware greeting endpoint.
 *
 * <p>Uses {@code @Juke("none")} on the injected service field — a
 * <em>lazy per-request delegating proxy</em> (created by
 * {@code JukeBeanPostProcessor}) rather than a fixed startup proxy. On
 * every method invocation the proxy calls {@code JukeFactory.newInstance()}
 * from within the live HTTP request thread, where
 * {@code JukeCookieFilter} has already populated the request-scoped
 * {@code JukeSessionContext}.
 *
 * <p>Routing logic (evaluated per request inside the proxy):
 * <ol>
 *   <li>{@code JUKE_SESSION_ID} + {@code JUKE_TRACK} cookies present and valid →
 *       {@code JukeFactory} returns a {@code SessionAwareReplayHandler};
 *       responses are served from the session-specific ZIP recording.</li>
 *   <li>No cookies (or invalid/expired session) → "none" opts out of global
 *       replay; {@code JukeFactory} returns the unwrapped real service.</li>
 * </ol>
 *
 * <p>The Playwright cookie-isolation specs in {@code src/test/playwright/}
 * exercise this contract: incognito contexts (no cookies) hit the live
 * service while contexts holding Juke session cookies replay deterministic
 * recordings.
 */
@RestController
public class SessionGreetingController {

    @Autowired
    @Juke("none")
    private IGreetingsService greetingsService;

    @GetMapping("/session-greeting")
    public Greeting sessionGreeting(
            @RequestParam(value = "name", defaultValue = "World") String name) {
        return greetingsService.greeting(name);
    }
}
