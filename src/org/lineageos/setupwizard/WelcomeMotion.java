/*
 * SPDX-FileCopyrightText: 2026 The ArkUI Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.setupwizard;

import android.view.animation.PathInterpolator;

/** Continuous spatial motion and separate, non-overshooting effect motion. */
final class WelcomeMotion {
    private static final PathInterpolator EFFECT = new PathInterpolator(.2f, 0, 0, 1);
    private static final double FREQUENCY = Math.sqrt(180);
    private static final double DAMPING = .82;
    private static final double DECAY = FREQUENCY * DAMPING;
    private static final double OSCILLATION = FREQUENCY * Math.sqrt(1 - DAMPING * DAMPING);

    private WelcomeMotion() {}

    static float effect(float progress) {
        return EFFECT.getInterpolation(Math.max(0, Math.min(1, progress)));
    }

    static float entrance(float seconds) {
        double decay = Math.sqrt(500) * .85;
        double oscillation = Math.sqrt(500 * (1 - .85 * .85));
        return (float) (1 - Math.exp(-decay * Math.max(0, seconds))
                * (Math.cos(oscillation * seconds)
                + decay / oscillation * Math.sin(oscillation * seconds)));
    }

    /** Exact damped-spring integration. Retargeting preserves both position and velocity. */
    static void advance(float[] position, float[] velocity, float[] target, float seconds) {
        if (seconds <= 0) return;
        double decay = Math.exp(-DECAY * seconds);
        double cos = Math.cos(OSCILLATION * seconds);
        double sin = Math.sin(OSCILLATION * seconds);
        double a = decay * (cos + DECAY / OSCILLATION * sin);
        double b = decay * sin / OSCILLATION;
        double c = -decay * FREQUENCY * FREQUENCY / OSCILLATION * sin;
        double d = decay * (cos - DECAY / OSCILLATION * sin);
        for (int i = 0; i < position.length; i++) {
            float offset = position[i] - target[i];
            float speed = velocity[i];
            position[i] = target[i] + (float) (a * offset + b * speed);
            velocity[i] = (float) (c * offset + d * speed);
        }
    }
}
