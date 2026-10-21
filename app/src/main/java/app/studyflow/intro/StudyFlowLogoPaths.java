package app.studyflow.intro;

import android.graphics.Path;
import android.graphics.RectF;

/**
 * Shared StudyFlow logo geometry, in the normalized 1000x1000 artboard space.
 *
 * <p>All values were MEASURED from the original logo artwork (pixel analysis
 * of the source image), not eyeballed:
 * <ul>
 *   <li>Tile: full 1000x1000 artboard, corner radius 248 (circle fit, max err ~11).</li>
 *   <li>Stroke width: 41 (cross-sections 41.0-42.7).</li>
 *   <li>Start dot: centre (228, 808), radius 52 (template fit against the
 *       artwork, IoU 0.99; diameter 104 vs stroke 41).</li>
 *   <li>Journey: skeletonized centreline, 31 anchors, total arc ~1565 units
 *       after Catmull-Rom smoothing.</li>
 *   <li>Check: (424, 532) -> vertex (496, 586) -> (624, 482), template fit IoU 0.91.</li>
 *   <li>Sparkle: centre (770, 220), bbox x[672,868] y[112,329], waist ~27.</li>
 * </ul>
 * The same anchors drive the vector drawables / AVD in res/, so the native
 * splash and this in-app rendering agree as closely as the formats allow.
 */
public final class StudyFlowLogoPaths {
    private StudyFlowLogoPaths() {}

    public static final float ARTBOARD = 1000f;
    public static final float TILE_RADIUS = 248f;
    public static final float STROKE_WIDTH = 41f;

    public static final float DOT_CX = 228f, DOT_CY = 808f, DOT_R = 52f;
    public static final float SPARKLE_CX = 770f, SPARKLE_CY = 220f;

    /** Tile gradient stops, sampled from the original tile (diagonal TL -> BR). */
    public static final int GRADIENT_START = 0xFF1466FB;
    public static final int GRADIENT_MID   = 0xFF2A48FA;
    public static final int GRADIENT_END   = 0xFF7036FD;
    public static final int SPARKLE_FILL   = 0xFF7DF0EE;
    public static final int SPARKLE_TOP    = 0xFFEAFBFA;
    public static final int SPARKLE_BOTTOM = 0xFF5FE3F0;
    public static final int GLOW_COLOR     = 0xFF4DE8F5;
    public static final int MARK_WHITE     = 0xFFFFFFFF;

    /** Total "Path of Progress" duration, identical to the AVD. */
    public static final int TOTAL_MS = 980;

    /** Measured journey centreline anchors, in drawing order (dot -> sparkle). */
    private static final float[][] JOURNEY_ANCHORS = {
        {228f, 808f}, {297.8f, 809.7f}, {352.4f, 809.7f}, {407.0f, 809.7f},
        {461.6f, 809.7f}, {517.9f, 809.7f}, {572.5f, 809.7f}, {625.4f, 804.6f},
        {673.2f, 787.5f}, {709.0f, 748.3f}, {714.2f, 698.8f}, {686.9f, 654.4f},
        {639.1f, 635.7f}, {586.2f, 630.5f}, {531.6f, 630.5f}, {477.0f, 630.5f},
        {422.4f, 628.8f}, {372.9f, 616.9f}, {331.9f, 582.8f}, {314.8f, 535.0f},
        {325.1f, 485.5f}, {359.2f, 446.2f}, {405.3f, 422.4f}, {456.5f, 413.8f},
        {511.1f, 413.8f}, {565.7f, 413.8f}, {620.3f, 408.7f}, {669.8f, 396.8f},
        {715.9f, 374.6f}, {753.4f, 338.7f}, {780.7f, 326.8f},
    };

    public static Path tilePath() {
        Path p = new Path();
        p.addRoundRect(new RectF(0, 0, ARTBOARD, ARTBOARD), TILE_RADIUS, TILE_RADIUS, Path.Direction.CW);
        return p;
    }

    public static Path dotPath() {
        Path p = new Path();
        p.addCircle(DOT_CX, DOT_CY, DOT_R, Path.Direction.CW);
        return p;
    }

    /** Journey route as smooth cubics through the measured anchors (Catmull-Rom). */
    public static Path journeyPath() {
        int n = JOURNEY_ANCHORS.length;
        Path p = new Path();
        p.moveTo(JOURNEY_ANCHORS[0][0], JOURNEY_ANCHORS[0][1]);
        for (int i = 0; i < n - 1; i++) {
            float[] pm = JOURNEY_ANCHORS[Math.max(0, i - 1)];
            float[] p1 = JOURNEY_ANCHORS[i];
            float[] p2 = JOURNEY_ANCHORS[i + 1];
            float[] pp = JOURNEY_ANCHORS[Math.min(n - 1, i + 2)];
            float c1x = p1[0] + (p2[0] - pm[0]) / 6f, c1y = p1[1] + (p2[1] - pm[1]) / 6f;
            float c2x = p2[0] - (pp[0] - p1[0]) / 6f, c2y = p2[1] - (pp[1] - p1[1]) / 6f;
            p.cubicTo(c1x, c1y, c2x, c2y, p2[0], p2[1]);
        }
        return p;
    }

    /** Check mark: down into the vertex first, then up - the natural draw order. */
    public static Path checkPath() {
        Path p = new Path();
        p.moveTo(424f, 532f);
        p.lineTo(496f, 586f);
        p.lineTo(624f, 482f);
        return p;
    }

    /** Four-point sparkle silhouette (quadratic edges pinched at the waist). */
    public static Path sparklePath() {
        Path p = new Path();
        p.moveTo(SPARKLE_CX, 112f);
        p.quadTo(797f, 193f, 868f, SPARKLE_CY);
        p.quadTo(797f, 247f, SPARKLE_CX, 329f);
        p.quadTo(743f, 247f, 672f, SPARKLE_CY);
        p.quadTo(743f, 193f, SPARKLE_CX, 112f);
        p.close();
        return p;
    }
}
