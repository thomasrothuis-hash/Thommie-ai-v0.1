package nl.thommie.ai;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

import java.util.Arrays;

final class AudioWaveformView extends View {

    static final int MODE_IDLE = 0;
    static final int MODE_USER = 1;
    static final int MODE_ASSISTANT = 2;

    private static final int BAR_COUNT = 29;

    private final Paint paint =
            new Paint(Paint.ANTI_ALIAS_FLAG);

    private final float[] current =
            new float[BAR_COUNT];
    private final float[] target =
            new float[BAR_COUNT];

    private int mode = MODE_IDLE;
    private boolean animating = false;

    AudioWaveformView(Context context) {
        super(context);
        setWillNotDraw(false);
        paint.setStrokeCap(Paint.Cap.ROUND);
    }

    void setMode(int mode) {
        post(() -> {
            this.mode = mode;

            if (mode == MODE_IDLE) {
                Arrays.fill(target, 0.035f);
            }

            ensureAnimation();
        });
    }

    void pushPcm16(short[] samples, int length) {
        if (samples == null || length <= 0) {
            return;
        }

        int safeLength =
                Math.min(length, samples.length);

        float[] levels =
                analyzeShorts(samples, safeLength);

        postLevels(levels);
    }

    void pushPcm16(byte[] pcm) {
        if (pcm == null || pcm.length < 2) {
            return;
        }

        int sampleCount = pcm.length / 2;
        short[] samples = new short[sampleCount];

        for (int i = 0; i < sampleCount; i++) {
            int lo = pcm[i * 2] & 0xff;
            int hi = pcm[i * 2 + 1];
            samples[i] =
                    (short) ((hi << 8) | lo);
        }

        float[] levels =
                analyzeShorts(
                        samples,
                        samples.length
                );

        postLevels(levels);
    }

    private void postLevels(float[] levels) {
        post(() -> {
            System.arraycopy(
                    levels,
                    0,
                    target,
                    0,
                    BAR_COUNT
            );
            ensureAnimation();
        });
    }

    private float[] analyzeShorts(
            short[] samples,
            int length
    ) {
        float[] levels =
                new float[BAR_COUNT];

        int chunk =
                Math.max(1, length / BAR_COUNT);

        for (int bar = 0; bar < BAR_COUNT; bar++) {
            int start = bar * chunk;
            int end =
                    bar == BAR_COUNT - 1
                            ? length
                            : Math.min(
                                    length,
                                    start + chunk
                            );

            if (start >= length) {
                levels[bar] = 0.03f;
                continue;
            }

            double sum = 0.0;
            int count = 0;

            for (int i = start; i < end; i++) {
                double v =
                        samples[i] / 32768.0;
                sum += v * v;
                count++;
            }

            double rms =
                    count == 0
                            ? 0.0
                            : Math.sqrt(sum / count);

            float boosted =
                    (float) Math.min(
                            1.0,
                            Math.pow(
                                    rms * 5.2,
                                    0.62
                            )
                    );

            levels[bar] =
                    0.035f + boosted * 0.965f;
        }

        return levels;
    }

    private void ensureAnimation() {
        if (animating) {
            return;
        }

        animating = true;
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();

        if (width <= 0 || height <= 0) {
            animating = false;
            return;
        }

        int baseColor;

        if (mode == MODE_ASSISTANT) {
            baseColor =
                    Color.rgb(116, 255, 160);
        } else if (mode == MODE_USER) {
            baseColor =
                    Color.rgb(54, 220, 104);
        } else {
            baseColor =
                    Color.rgb(54, 120, 75);
        }

        paint.setColor(baseColor);

        float gap = dp(3f);
        float totalGap =
                gap * (BAR_COUNT - 1);
        float barWidth =
                Math.max(
                        dp(2f),
                        (width - totalGap)
                                / (float) BAR_COUNT
                );

        paint.setStrokeWidth(
                Math.max(dp(2f), barWidth)
        );

        float centerY = height / 2f;
        boolean moving = false;

        for (int i = 0; i < BAR_COUNT; i++) {
            float desired =
                    mode == MODE_IDLE
                            ? 0.035f
                            : target[i];

            current[i] +=
                    (desired - current[i]) * 0.34f;

            if (mode != MODE_IDLE) {
                target[i] *= 0.89f;
                target[i] =
                        Math.max(
                                target[i],
                                0.035f
                        );
            }

            if (Math.abs(
                    current[i] - desired
            ) > 0.006f
                    || (mode != MODE_IDLE
                    && current[i] > 0.05f)) {
                moving = true;
            }

            float x =
                    barWidth / 2f
                            + i * (barWidth + gap);

            float barHeight =
                    Math.max(
                            dp(3f),
                            current[i]
                                    * height
                                    * 0.92f
                    );

            float top =
                    centerY - barHeight / 2f;
            float bottom =
                    centerY + barHeight / 2f;

            canvas.drawLine(
                    x,
                    top,
                    x,
                    bottom,
                    paint
            );
        }

        if (moving || mode != MODE_IDLE) {
            postInvalidateOnAnimation();
        } else {
            animating = false;
        }
    }

    private float dp(float value) {
        return value
                * getResources()
                .getDisplayMetrics()
                .density;
    }
}
