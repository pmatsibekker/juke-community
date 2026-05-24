package org.juke.framework.session;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationContext;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link JukeCookieFilter}.
 */
class JukeCookieFilterTest {

    private ApplicationContext applicationContext;
    private JukeSessionRegistry registry;
    private JukeCookieFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        applicationContext = mock(ApplicationContext.class);
        registry = new JukeSessionRegistry();
        filter = new JukeCookieFilter(applicationContext, registry);
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        chain = mock(FilterChain.class);
    }

    @Test
    void noCookies_contextNotPopulated() throws Exception {
        when(request.getCookies()).thenReturn(null);

        JukeSessionContext ctx = new JukeSessionContext();
        when(applicationContext.getBean(JukeSessionContext.class)).thenReturn(ctx);

        filter.doFilterInternal(request, response, chain);

        assertFalse(ctx.isPlaybackActive());
        assertNull(ctx.getSessionId());
        assertNull(ctx.getTrackName());
        verify(chain).doFilter(request, response);
    }

    @Test
    void validCookies_contextPopulated() throws Exception {
        // Create a real session entry in the registry
        // We need to mock the registry for this test since we can't create real ZIP files easily
        SessionRegistry mockRegistry = mock(SessionRegistry.class);
        when(mockRegistry.isValid("test-session-123", "my-track")).thenReturn(true);

        JukeCookieFilter testFilter = new JukeCookieFilter(applicationContext, mockRegistry);

        Cookie[] cookies = new Cookie[]{
                new Cookie(JukeCookieFilter.COOKIE_SESSION_ID, "test-session-123"),
                new Cookie(JukeCookieFilter.COOKIE_TRACK, "my-track")
        };
        when(request.getCookies()).thenReturn(cookies);

        JukeSessionContext ctx = new JukeSessionContext();
        when(applicationContext.getBean(JukeSessionContext.class)).thenReturn(ctx);

        testFilter.doFilterInternal(request, response, chain);

        assertTrue(ctx.isPlaybackActive());
        assertEquals("test-session-123", ctx.getSessionId());
        assertEquals("my-track", ctx.getTrackName());
        verify(chain).doFilter(request, response);
    }

    @Test
    void invalidSessionId_contextNotPopulated() throws Exception {
        SessionRegistry mockRegistry = mock(SessionRegistry.class);
        when(mockRegistry.isValid("invalid-id", "some-track")).thenReturn(false);

        JukeCookieFilter testFilter = new JukeCookieFilter(applicationContext, mockRegistry);

        Cookie[] cookies = new Cookie[]{
                new Cookie(JukeCookieFilter.COOKIE_SESSION_ID, "invalid-id"),
                new Cookie(JukeCookieFilter.COOKIE_TRACK, "some-track")
        };
        when(request.getCookies()).thenReturn(cookies);

        JukeSessionContext ctx = new JukeSessionContext();
        when(applicationContext.getBean(JukeSessionContext.class)).thenReturn(ctx);

        testFilter.doFilterInternal(request, response, chain);

        assertFalse(ctx.isPlaybackActive());
        assertNull(ctx.getSessionId());
        verify(chain).doFilter(request, response);
    }

    @Test
    void onlySessionCookie_noTrackCookie_contextNotPopulated() throws Exception {
        Cookie[] cookies = new Cookie[]{
                new Cookie(JukeCookieFilter.COOKIE_SESSION_ID, "test-session-123")
        };
        when(request.getCookies()).thenReturn(cookies);

        JukeSessionContext ctx = new JukeSessionContext();
        when(applicationContext.getBean(JukeSessionContext.class)).thenReturn(ctx);

        filter.doFilterInternal(request, response, chain);

        assertFalse(ctx.isPlaybackActive());
        verify(chain).doFilter(request, response);
    }

    @Test
    void filterAlwaysCallsChain() throws Exception {
        when(request.getCookies()).thenReturn(null);

        filter.doFilterInternal(request, response, chain);

        verify(chain, times(1)).doFilter(request, response);
    }
}
