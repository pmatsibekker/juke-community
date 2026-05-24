package org.juke.remix.service;

/**
 * Tuner half of the formerly combined {@code RemixService}. Schedules
 * per-invocation tunings (injected delays, synthetic exceptions) against
 * a specific {@code class.method.sequence} signature.
 *
 * <p>Phase 6 (Single Responsibility): extracted from {@code RemixService} so
 * recording, replay, and tuner concerns can evolve independently.
 */
public interface TunerService {

    /**
     * Schedules a wall-clock delay to be applied when the given signature
     * is invoked during replay.
     *
     * @param classAndMethodSequence signature in the form
     *        {@code fqcn.$method.sequence} (e.g.
     *        {@code com.example.IGreetingsService.$greeting.1})
     * @param waitTimeInMillis       delay in milliseconds
     */
    void scheduleDelay(String classAndMethodSequence, long waitTimeInMillis);

    /**
     * Schedules a synthetic exception to be thrown when the given signature
     * is invoked during replay.
     *
     * @param classAndMethodSequence  signature (see {@link #scheduleDelay})
     * @param exceptionType           simple or fully-qualified exception class
     * @param exceptionMessages       message to pass to the exception
     *                                constructor (currently informational)
     */
    void scheduleException(String classAndMethodSequence, String exceptionType, String exceptionMessages);

    /**
     * Clears every scheduled tuning (delays and exceptions) so a subsequent
     * replay starts from a clean slate. Tuner schedules live in global runtime
     * state and otherwise persist across replay sessions, so repeatable
     * fault-injection demos call this between runs to stop one run's injected
     * delay or exception bleeding into the next.
     */
    void clearSchedules();
}
