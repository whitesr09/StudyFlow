package app.studyflow.intro;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * Custom in-app variant of the StudyFlow "Path of Progress" animation.
 *
 * <p>Unlike the native splash (single solid background colour, masked icon,
 * no gradient, no blur, no audio), this View renders the authentic full logo:
 * gradient tile, progressive route drawing via {@link PathMeasure}, and a
 * real radial-gradient glow. It runs the SAME 980 ms phase timeline as the
 * AnimatedVectorDrawable in res/drawable/avd_studyflow_splash.xml:
 * <pre>
 *   A   0-100   dot fades in, scale 0.84 -> 1.0 (ease-out)
 *   B 100-300   journey trim 0 -> 0.32
 *   C 300-533   journey -> 0.75; check starts 420 ms -> 0.42
 *   D 533-683   journey -> 1.0; check -> 1.0
 *   E 683-817   sparkle scale 0.90 -> 1.08 -> 1.0, alpha -> 1, glow pulse
 *   F 817-980   settled hold, zero motion
 * </pre>
 *
 * <p>Opt-in only: the host decides if/when to show it. It is skippable on
 * touch, reports completion exactly once through
 * {@link OnIntroFinishedListener}, and never blocks startup. Optional sound
 * effects play only when the host enables them AND the
 * {@code studyflow_launch_sound} preference is on (default OFF); see
 * {@link StudyFlowIntroSounds}.
 */
public class StudyFlowIntroView extends View {

    public interface OnIntroFinishedListener {
        void onIntroFinished();
    }

    private final Path tile = StudyFlowLogoPaths.tilePath();
    private final Path dot = StudyFlowLogoPaths.dotPath();
    private final Path journey = StudyFlowLogoPaths.journeyPath();
    private final Path check = StudyFlowLogoPaths.checkPath();
    private final Path sparkle = StudyFlowLogoPaths.sparklePath();
    private final PathMeasure journeyMeasure = new PathMeasure(journey, false);
    private final PathMeasure checkMeasure = new PathMeasure(check, false);
    private final Path segment = new Path();

    private final Paint tilePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sparklePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private ValueAnimator animator;
    private OnIntroFinishedListener listener;
    private StudyFlowIntroSounds sounds;
    private boolean launchSoundsEnabled;
    private boolean started;
    private boolean finished;
    private boolean whooshPlayed;
    private boolean chimePlayed;
    private int elapsedMs;

    public StudyFlowIntroView(Context context) {
        super(context);
        init();
    }

    public StudyFlowIntroView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        tilePaint.setStyle(Paint.Style.FILL);
        tilePaint.setShader(new LinearGradient(0, 0, 1000, 1000,
                new int[]{StudyFlowLogoPaths.GRADIENT_START, StudyFlowLogoPaths.GRADIENT_MID,
                        StudyFlowLogoPaths.GRADIENT_END},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));

        glowPaint.setStyle(Paint.Style.FILL);
        glowPaint.setShader(new RadialGradient(StudyFlowLogoPaths.SPARKLE_CX,
                StudyFlowLogoPaths.SPARKLE_CY, 150f,
                StudyFlowLogoPaths.GLOW_COLOR, 0x00000000, Shader.TileMode.CLAMP));

        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setColor(StudyFlowLogoPaths.MARK_WHITE);
        strokePaint.setStrokeWidth(StudyFlowLogoPaths.STROKE_WIDTH);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        strokePaint.setStrokeJoin(Paint.Join.ROUND);

        dotPaint.setStyle(Paint.Style.FILL);
        dotPaint.setColor(StudyFlowLogoPaths.MARK_WHITE);

        sparklePaint.setStyle(Paint.Style.FILL);
        sparklePaint.setShader(new LinearGradient(0, 112f, 0, 329f,
                StudyFlowLogoPaths.SPARKLE_TOP, StudyFlowLogoPaths.SPARKLE_BOTTOM,
                Shader.TileMode.CLAMP));

        setClickable(true);
    }

    public void setOnIntroFinishedListener(OnIntroFinishedListener l) {
        listener = l;
    }

    /** Host feature flag for launch sounds; the user preference must also be on. */
    public void setLaunchSoundsEnabled(boolean enabled) {
        launchSoundsEnabled = enabled;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        start();
    }

    /** Begins the 980 ms timeline. Safe to call more than once. */
    public void start() {
        if (started || finished) return;
        started = true;
        if (launchSoundsEnabled && StudyFlowIntroSounds.isEnabled(getContext())) {
            sounds = new StudyFlowIntroSounds(getContext());
        }
        animator = ValueAnimator.ofInt(0, StudyFlowLogoPaths.TOTAL_MS);
        animator.setDuration(StudyFlowLogoPaths.TOTAL_MS);
        animator.setInterpolator(new LinearInterpolator()); // easing lives in the phase functions
        animator.addUpdateListener(a -> {
            elapsedMs = (int) a.getAnimatedValue();
            maybePlaySounds();
            invalidate();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                elapsedMs = StudyFlowLogoPaths.TOTAL_MS;
                invalidate();
                finishOnce();
            }
        });
        animator.start();
    }

    /** Skips straight to the settled final logo and finishes. */
    public void skip() {
        if (finished) return;
        if (animator != null) animator.cancel();
        elapsedMs = StudyFlowLogoPaths.TOTAL_MS;
        invalidate();
        finishOnce();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            skip();
            return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        if (sounds != null) sounds.release();
        super.onDetachedFromWindow();
    }

    private void maybePlaySounds() {
        if (sounds == null) return;
        if (!whooshPlayed && elapsedMs >= 200) { whooshPlayed = true; sounds.playWhoosh(); }
        if (!chimePlayed && elapsedMs >= 683) { chimePlayed = true; sounds.playChime(); }
    }

    private void finishOnce() {
        if (finished) return;
        finished = true;
        if (sounds != null) sounds.release();
        if (listener != null) listener.onIntroFinished();
    }

    // ---- Phase functions: t in ms, mirroring the AVD animator segments ----

    private static float clamp01(float v) { return v < 0 ? 0 : (v > 1 ? 1 : v); }
    private static float easeOutCubic(float x) { float u = 1 - clamp01(x); return 1 - u * u * u; }
    private static float lerp(float a, float b, float f) { return a + (b - a) * f; }

    private static float journeyTrim(int t) {
        if (t <= 100) return 0f;
        if (t < 300) return lerp(0f, 0.32f, (t - 100) / 200f);
        if (t < 533) return lerp(0.32f, 0.75f, (t - 300) / 233f);
        if (t < 683) return lerp(0.75f, 1f, easeOutCubic((t - 533) / 150f));
        return 1f;
    }

    private static float checkTrim(int t) {
        if (t <= 420) return 0f;
        if (t < 533) return lerp(0f, 0.42f, (t - 420) / 113f);
        if (t < 683) return lerp(0.42f, 1f, easeOutCubic((t - 533) / 150f));
        return 1f;
    }

    private static float sparkleScale(int t) {
        if (t < 683) return 0.9f;
        if (t < 763) return lerp(0.9f, 1.08f, easeOutCubic((t - 683) / 80f));
        if (t < 817) return lerp(1.08f, 1f, easeOutCubic((t - 763) / 54f));
        return 1f;
    }

    private static float sparkleAlpha(int t) {
        if (t < 683) return 0.3f;
        if (t < 817) return lerp(0.3f, 1f, easeOutCubic((t - 683) / 134f));
        return 1f;
    }

    private static float glowAlpha(int t) {
        if (t < 683) return 0f;
        if (t < 750) return lerp(0f, 0.16f, easeOutCubic((t - 683) / 67f));
        if (t < 817) return lerp(0.16f, 0.05f, (t - 750) / 67f);
        if (t < 900) return lerp(0.05f, 0f, (t - 817) / 83f);
        return 0f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float side = Math.min(getWidth(), getHeight());
        if (side <= 0) return;
        int t = elapsedMs;
        canvas.save();
        canvas.translate((getWidth() - side) / 2f, (getHeight() - side) / 2f);
        canvas.scale(side / 1000f, side / 1000f);

        canvas.drawPath(tile, tilePaint);

        float glow = glowAlpha(t);
        if (glow > 0.001f) {
            glowPaint.setAlpha(Math.round(140f * glow / 0.16f));
            canvas.drawCircle(StudyFlowLogoPaths.SPARKLE_CX, StudyFlowLogoPaths.SPARKLE_CY,
                    150f, glowPaint);
        }

        drawTrimmed(canvas, journeyMeasure, journeyTrim(t));
        drawTrimmed(canvas, checkMeasure, checkTrim(t));

        // Start dot: scale about its own centre, fade in (Phase A).
        float dotScale = t < 100 ? lerp(0.84f, 1f, easeOutCubic(t / 100f)) : 1f;
        float dotAlpha = t < 100 ? easeOutCubic(t / 100f) : 1f;
        canvas.save();
        canvas.translate(StudyFlowLogoPaths.DOT_CX, StudyFlowLogoPaths.DOT_CY);
        canvas.scale(dotScale, dotScale);
        canvas.translate(-StudyFlowLogoPaths.DOT_CX, -StudyFlowLogoPaths.DOT_CY);
        dotPaint.setAlpha(Math.round(255f * dotAlpha));
        canvas.drawPath(dot, dotPaint);
        canvas.restore();

        // Sparkle: scale about its own centre + alpha (Phase E).
        canvas.save();
        canvas.translate(StudyFlowLogoPaths.SPARKLE_CX, StudyFlowLogoPaths.SPARKLE_CY);
        float ss = sparkleScale(t);
        canvas.scale(ss, ss);
        canvas.translate(-StudyFlowLogoPaths.SPARKLE_CX, -StudyFlowLogoPaths.SPARKLE_CY);
        sparklePaint.setAlpha(Math.round(255f * sparkleAlpha(t)));
        canvas.drawPath(sparkle, sparklePaint);
        canvas.restore();

        canvas.restore();
    }

    private void drawTrimmed(Canvas canvas, PathMeasure measure, float trim) {
        if (trim <= 0f) return;
        segment.reset();
        measure.getSegment(0f, measure.getLength() * clamp01(trim), segment, true);
        canvas.drawPath(segment, strokePaint);
    }
}
