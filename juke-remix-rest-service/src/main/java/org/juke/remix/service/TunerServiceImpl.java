package org.juke.remix.service;

import org.juke.framework.tuner.DelayTunerTask;
import org.juke.framework.tuner.ExceptionTunerTask;
import org.juke.framework.tuner.TunerTask;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Default implementation of {@link TunerService}. Tuner tasks register
 * themselves with the global tuner registry at build-time, so each method
 * here is a thin builder invocation.
 *
 * <p>Profile-gated to {@code record} and {@code replay} so production builds
 * don't instantiate tuner scheduling.
 */
@Service
@ConditionalOnProperty(name = "juke.enabled", havingValue = "true")
public class TunerServiceImpl implements TunerService {

    @Override
    public void scheduleDelay(String classAndMethodSequence, long waitTimeInMillis) {
        new DelayTunerTask.Builder(classAndMethodSequence, waitTimeInMillis).build();
    }

    @Override
    public void scheduleException(String classAndMethodSequence, String exceptionType, String exceptionMessages) {
        new ExceptionTunerTask.Builder(classAndMethodSequence, exceptionType).build();
    }

    @Override
    public void clearSchedules() {
        // Empties the per-tuner participant sets in the runtime-scoped map, so
        // no previously registered delay/exception matches any replayed entry.
        TunerTask.clear();
    }
}
