package com.example.plugindemo;

import org.juke.plugin.api.capability.transformer.TransformRequest;
import org.juke.plugin.api.capability.transformer.TransformResult;
import org.juke.plugin.sdk.annotation.PluginCapability;
import org.juke.plugin.sdk.annotation.PluginEndpoint;

/**
 * A minimal third-party plugin capability. {@code @PluginCapability} marks this
 * as a Juke plugin bean (the SDK discovers it on startup and includes
 * {@code RECORDING_TRANSFORMER} in the registration it sends to the agent), and
 * the {@code @PluginEndpoint} methods are the capability's hooks.
 *
 * <p>This demo transformer is a pass-through (it never mutates bytes) — the
 * point of the sample is the SDK registration contract, not a real
 * transformation.
 */
@PluginCapability(org.juke.plugin.api.PluginCapability.RECORDING_TRANSFORMER)
public class DemoRecordingTransformer {

    /** Hook invoked before remix persists a recording. */
    @PluginEndpoint("before-write")
    public TransformResult beforeWrite(TransformRequest request) {
        return passthrough();
    }

    /** Hook invoked after remix loads a recording for replay. */
    @PluginEndpoint("after-read")
    public TransformResult afterRead(TransformRequest request) {
        return passthrough();
    }

    private static TransformResult passthrough() {
        return new TransformResult();   // mutated defaults to false -> passthrough
    }
}
