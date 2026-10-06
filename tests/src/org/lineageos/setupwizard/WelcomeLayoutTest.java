/*
 * SPDX-FileCopyrightText: 2026 The ArkUI Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.setupwizard;

import static org.junit.Assert.*;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.SystemClock;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsetsController;
import android.widget.Button;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.activity.result.ActivityResult;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class WelcomeLayoutTest {
    private Instrumentation mInstrumentation;
    private WelcomeActivity mActivity;
    private float mDurationScale;

    @Before
    public void launch() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mDurationScale = ValueAnimator.getDurationScale();
        mActivity = (WelcomeActivity) mInstrumentation.startActivitySync(new Intent(
                mInstrumentation.getTargetContext(), WelcomeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
    }

    @After
    public void finish() {
        mInstrumentation.runOnMainSync(() -> {
            ValueAnimator.setDurationScale(mDurationScale);
            if (mActivity != null) mActivity.finish();
        });
    }

    private WelcomeLayout scene() {
        return mActivity.findViewById(R.id.welcome_scene);
    }

    @Test
    public void introCanBeSkippedWithoutLeavingSetup() {
        mInstrumentation.runOnMainSync(() -> {
            mActivity.findViewById(R.id.skip_intro).performClick();
            assertFalse(mActivity.isFinishing());
            assertTrue(scene().getElapsed() >= WelcomeLayout.MAIN_START);
            assertEquals(View.VISIBLE, mActivity.findViewById(R.id.start).getVisibility());
            assertEquals(View.GONE, mActivity.findViewById(R.id.skip_intro).getVisibility());
            assertEquals(mActivity.getString(R.string.start),
                    mActivity.findViewById(R.id.start).getContentDescription());
        });
    }

    @Test
    public void backCannotExitTheWelcomeStep() {
        mInstrumentation.runOnMainSync(() -> {
            mActivity.getOnBackPressedDispatcher().onBackPressed();
            mActivity.getOnBackPressedDispatcher().onBackPressed();
            assertFalse(mActivity.isFinishing());
            assertTrue(scene().getElapsed() >= WelcomeLayout.MAIN_START);
        });
    }

    @Test
    public void animationDisabledLeavesAnImmediateUsableWelcome() {
        mInstrumentation.runOnMainSync(() -> {
            ValueAnimator.setDurationScale(0);
            scene().resume();
            assertFalse(scene().isAnimating());
            assertEquals(1f, mActivity.findViewById(R.id.start).getAlpha(), 0);
            assertEquals(View.VISIBLE, mActivity.findViewById(R.id.start).getVisibility());
            assertEquals(View.GONE, mActivity.findViewById(R.id.skip_intro).getVisibility());
            assertTrue(mActivity.findViewById(R.id.start).isEnabled());
        });
    }

    @Test
    public void returningFromUtilitiesDoesNotFinishOrAdvanceSetup() {
        mInstrumentation.runOnMainSync(() -> {
            scene().skipIntro();
            mActivity.onSubactivityResult(new ActivityResult(Activity.RESULT_CANCELED, null));
            mActivity.onSubactivityResult(new ActivityResult(Activity.RESULT_OK, new Intent()));
            assertFalse(mActivity.isFinishing());
            assertEquals(View.VISIBLE, mActivity.findViewById(R.id.start).getVisibility());
            assertTrue(mActivity.findViewById(R.id.start).isEnabled());
        });
    }

    @Test
    public void pausingFreezesTheTimelineAndResumingAdvancesIt() {
        long[] before = new long[1];
        mInstrumentation.runOnMainSync(() -> {
            scene().pause();
            before[0] = scene().getElapsed();
            assertFalse(scene().isAnimating());
        });
        SystemClock.sleep(300);
        mInstrumentation.runOnMainSync(() -> {
            assertEquals(before[0], scene().getElapsed());
            scene().resume();
        });
        SystemClock.sleep(350);
        mInstrumentation.runOnMainSync(() -> assertTrue(scene().getElapsed() > before[0]));
    }

    @Test
    public void repeatedScaleNotificationsDoNotSlowTheTimeline() {
        long[] before = new long[1];
        mInstrumentation.runOnMainSync(() -> {
            ValueAnimator.setDurationScale(1);
            before[0] = scene().getElapsed();
        });
        for (int i = 0; i < 30; i++) {
            SystemClock.sleep(40);
            mInstrumentation.runOnMainSync(() -> ValueAnimator.setDurationScale(1));
        }
        mInstrumentation.runOnMainSync(() ->
                assertTrue(scene().getElapsed() - before[0] >= 800));
    }

    @Test
    public void durationScaleChangesAlterSpeedWithoutRestartingTheScene() {
        long[] times = new long[3];
        mInstrumentation.runOnMainSync(() -> {
            scene().restoreElapsed(WelcomeLayout.MAIN_START + 1000);
            ValueAnimator.setDurationScale(2);
            times[0] = scene().getElapsed();
        });
        SystemClock.sleep(500);
        mInstrumentation.runOnMainSync(() -> {
            times[1] = scene().getElapsed();
            ValueAnimator.setDurationScale(1);
        });
        SystemClock.sleep(500);
        mInstrumentation.runOnMainSync(() -> times[2] = scene().getElapsed());
        long slow = times[1] - times[0], normal = times[2] - times[1];
        assertTrue(slow >= 180 && slow <= 340);
        assertTrue(normal > slow * 1.5f);
        assertTrue(times[2] > WelcomeLayout.MAIN_START + 1000);
    }

    @Test
    public void spatialMotionHasTheSameRateAtDifferentFrameIntervals() {
        float[] coarse = {0}, fine = {0}, coarseSpeed = {0}, fineSpeed = {0}, target = {1};
        for (int i = 0; i < 24; i++) {
            WelcomeMotion.advance(coarse, coarseSpeed, target, 1 / 30f);
        }
        for (int i = 0; i < 96; i++) {
            WelcomeMotion.advance(fine, fineSpeed, target, 1 / 120f);
        }
        assertEquals(fine[0], coarse[0], .0001f);
        assertEquals(fineSpeed[0], coarseSpeed[0], .0001f);
        assertEquals(1, fine[0], .002f);
    }

    @Test
    public void spatialRetargetingCarriesTheExistingVelocity() {
        float[] position = {0}, velocity = {0};
        WelcomeMotion.advance(position, velocity, new float[]{1}, .12f);
        float before = position[0], speed = velocity[0];
        assertTrue(speed > 1);
        WelcomeMotion.advance(position, velocity, new float[]{-1}, .0001f);
        assertEquals(speed, (position[0] - before) / .0001f, .05f);
    }

    @Test
    public void transientSystemBarsMatchTheSceneContrast() {
        mInstrumentation.runOnMainSync(() -> {
            scene().pause();
            scene().restoreElapsed(0);
            WindowInsetsController controller = mActivity.getWindow().getInsetsController();
            int light = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS;
            assertEquals(0, controller.getSystemBarsAppearance() & light);
            assertEquals(Color.TRANSPARENT, mActivity.getWindow().getStatusBarColor());
            scene().restoreElapsed(6500);
            int surface = mActivity.getColor(com.android.internal.R.color.materialColorSurface);
            assertEquals(Color.luminance(surface) > .5f ? light : 0,
                    controller.getSystemBarsAppearance() & light);
        });
    }

    @Test
    public void safeAreaPaddingDoesNotClipTheFullScreenBackground() {
        mInstrumentation.runOnMainSync(() -> {
            WelcomeLayout layout = new WelcomeLayout(mActivity, null);
            layout.restoreElapsed(WelcomeLayout.MAIN_START + 1200);
            layout.setBackgroundColor(Color.BLACK);
            layout.setPadding(0, 72, 0, 48);
            layout.measure(View.MeasureSpec.makeMeasureSpec(540, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.EXACTLY));
            layout.layout(0, 0, 540, 1200);
            Bitmap bitmap = Bitmap.createBitmap(540, 1200, Bitmap.Config.ARGB_8888);
            try {
                layout.draw(new Canvas(bitmap));
                assertNotEquals(Color.BLACK, bitmap.getPixel(10, 10));
                assertNotEquals(Color.BLACK, bitmap.getPixel(10, 1190));
            } finally {
                bitmap.recycle();
            }
        });
    }

    @Test
    public void recreationDoesNotRestartTheIntro() {
        mInstrumentation.runOnMainSync(() -> {
            scene().restoreElapsed(WelcomeLayout.MAIN_START + 7100);
            mActivity.recreate();
        });
        WelcomeActivity original = mActivity;
        long deadline = SystemClock.uptimeMillis() + 5000;
        while (mActivity == original && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(50);
            mInstrumentation.runOnMainSync(() -> {
                for (var activity : ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(Stage.RESUMED)) {
                    if (activity instanceof WelcomeActivity welcome) mActivity = welcome;
                }
            });
        }
        assertNotSame(original, mActivity);
        mInstrumentation.runOnMainSync(() -> {
            assertTrue(scene().getElapsed() >= WelcomeLayout.MAIN_START + 7100);
            assertEquals(View.GONE, mActivity.findViewById(R.id.skip_intro).getVisibility());
            assertEquals(View.VISIBLE, mActivity.findViewById(R.id.start).getVisibility());
        });
    }

    @Test
    public void detachingStopsFrameCallbacks() {
        WelcomeLayout[] old = new WelcomeLayout[1];
        mInstrumentation.runOnMainSync(() -> {
            old[0] = scene();
            mActivity.finish();
        });
        SystemClock.sleep(400);
        mInstrumentation.runOnMainSync(() -> assertFalse(old[0].isAnimating()));
    }

    @Test
    public void largeFontsKeepPortraitActionsSeparated() {
        checkLayout(411, 914, 2f, View.LAYOUT_DIRECTION_LTR);
    }

    @Test
    public void landscapeAndRtlKeepActionsWithinTheViewport() {
        checkLayout(914, 411, 1.3f, View.LAYOUT_DIRECTION_RTL);
    }

    private void checkLayout(int widthDp, int heightDp, float fontScale, int direction) {
        mInstrumentation.runOnMainSync(() -> {
            Context target = mInstrumentation.getTargetContext();
            Configuration configuration = new Configuration(target.getResources().getConfiguration());
            configuration.fontScale = fontScale;
            Context context = new ContextThemeWrapper(target.createConfigurationContext(configuration),
                    R.style.Theme_Setup_Welcome);
            WelcomeLayout layout = new WelcomeLayout(context, null);
            layout.setLayoutDirection(direction);
            layout.findViewById(R.id.skip).setVisibility(View.VISIBLE);
            layout.restoreElapsed(WelcomeLayout.MAIN_START + 1200);
            float density = context.getResources().getDisplayMetrics().density;
            int width = Math.round(widthDp * density), height = Math.round(heightDp * density);
            layout.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            layout.layout(0, 0, width, height);
            View next = layout.findViewById(R.id.start);
            View greeting = layout.findViewById(R.id.welcome_title);
            View accessibility = layout.findViewById(R.id.launch_accessibility);
            assertTrue(next.getWidth() >= 48 * density);
            assertTrue(next.getHeight() >= 48 * density);
            assertTrue(greeting.getBottom() < next.getTop());
            for (int id : new int[]{R.id.start, R.id.launch_accessibility, R.id.emerg_dialer, R.id.skip}) {
                View view = layout.findViewById(id);
                assertTrue(view.getLeft() >= 0);
                assertTrue(view.getTop() >= 0);
                assertTrue(view.getRight() <= width);
                assertTrue(view.getBottom() <= height);
                if (id != R.id.start) assertTrue(next.getBottom() < view.getTop());
                if (id != R.id.start) {
                    assertEquals(accessibility.getWidth(), view.getWidth());
                    assertEquals(accessibility.getHeight(), view.getHeight());
                    assertEquals(Gravity.CENTER, ((Button) view).getGravity());
                    assertTrue(view.getHeight() >= 48 * density);
                }
            }
            View emergency = layout.findViewById(R.id.emerg_dialer);
            assertTrue(direction == View.LAYOUT_DIRECTION_RTL
                    ? emergency.getRight() < accessibility.getLeft()
                    : accessibility.getRight() < emergency.getLeft());
        });
    }
}
