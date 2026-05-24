package org.juke.remix.bundle;

/**
 * Thrown by {@link BundlePackager#unpack(byte[])} when an uploaded bundle is malformed,
 * incomplete, or fails checksum verification. Mapped to HTTP 400 by the controller's
 * exception handler so callers see the validation message.
 */
public class BundleFormatException extends RuntimeException {

    public BundleFormatException(String message) {
        super(message);
    }

    public BundleFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
