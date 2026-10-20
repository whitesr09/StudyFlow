package app.studyflow.intro;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.SoundPool;

import app.studyflow.R;

/**
 * Optional launch sounds for the in-app intro. This is a presentation layer
 * only: Android's native system splash has no audio mechanism, sounds are
 * NEVER played from it, and playback here is gated by the
 * {@code studyflow_launch_sound} preference, which defaults to OFF.
 * Every method is null-safe; if audio is unavailable the animation simply
 * continues silently. Nothing here can block or delay app startup.
 */
public final class StudyFlowIntroSounds {
    public static final String PREFS_NAME = "studyflow_intro";
    public static final String PREF_LAUNCH_SOUND = "studyflow_launch_sound";

    private SoundPool pool;
    private int whooshId;
    private int chimeId;

    /** The one gate: default false, user-controllable via settings. */
    public static boolean isEnabled(Context context) {
        try {
            SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            return sp.getBoolean(PREF_LAUNCH_SOUND, false);
        } catch (Exception e) {
            return false;
        }
    }

    public static void setEnabled(Context context, boolean enabled) {
        try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putBoolean(PREF_LAUNCH_SOUND, enabled).apply();
        } catch (Exception ignored) {
        }
    }

    /** Loads the two effects. Safe to construct even if sounds are disabled. */
    public StudyFlowIntroSounds(Context context) {
        try {
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            pool = new SoundPool.Builder().setMaxStreams(2).setAudioAttributes(attrs).build();
            whooshId = pool.load(context, R.raw.studyflow_path_whoosh, 1);
            chimeId = pool.load(context, R.raw.studyflow_sparkle_chime, 1);
        } catch (Exception e) {
            pool = null;
        }
    }

    public void playWhoosh() { play(whooshId); }

    public void playChime() { play(chimeId); }

    private void play(int id) {
        if (pool == null || id == 0) return;
        try {
            pool.play(id, 0.7f, 0.7f, 1, 0, 1.0f);
        } catch (Exception ignored) {
        }
    }

    public void release() {
        if (pool != null) {
            try { pool.release(); } catch (Exception ignored) {}
            pool = null;
        }
    }
}
