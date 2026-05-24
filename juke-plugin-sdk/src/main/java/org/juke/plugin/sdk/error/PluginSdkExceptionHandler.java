package org.juke.plugin.sdk.error;

import org.juke.plugin.api.error.PluginErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * Default {@code @ControllerAdvice} the SDK contributes — translates uncaught exceptions
 * thrown from plugin endpoints into a {@link PluginErrorResponse} so remix and the admin UI
 * can render them consistently. Plugin authors can replace this by declaring their own
 * higher-priority {@code ControllerAdvice}; the auto-config flags this one as conditional on
 * a missing bean of the same type.
 */
@ControllerAdvice
public class PluginSdkExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(PluginSdkExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<PluginErrorResponse> illegalArg(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(PluginErrorResponse.of("invalid_request", e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<PluginErrorResponse> illegalState(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(PluginErrorResponse.of("conflict", e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<PluginErrorResponse> generic(Exception e) {
        LOG.warn("plugin endpoint threw an uncaught exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(PluginErrorResponse.of("plugin_error",
                        e.getClass().getSimpleName() + ": " + e.getMessage()));
    }
}
