package com.liskovsoft.smartyoutubetv2.common.exoplayer.debug;

import android.content.Context;

import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.TeeDataSource;
import com.liskovsoft.sharedutils.mylogger.Log;

import java.io.File;

/**
 * Switch and plumbing for SABR golden-file capture. See {@link SabrCaptureDataSink} for why these
 * captures exist and why they must be taken before any player port begins.
 *
 * <p>Deliberately a compile-time constant rather than a user setting: this writes every byte of
 * every SABR response to disk, which is neither cheap nor something a user should be able to switch
 * on by accident. Flip {@link #ENABLED}, build, capture, flip it back.
 */
public final class SabrCapture {
    private static final String TAG = SabrCapture.class.getSimpleName();
    private static final String DIR_NAME = "sabr-golden";

    /** Flip to true to record. Must be false in anything shipped. */
    private static final boolean ENABLED = false;

    private SabrCapture() {
    }

    public static boolean isEnabled() {
        return ENABLED;
    }

    /**
     * Wraps a data source factory so every byte read is also written to disk. Returns the factory
     * unchanged when capture is off, so the normal playback path is untouched.
     */
    public static DataSource.Factory wrap(Context context, DataSource.Factory upstream) {
        if (!ENABLED) {
            return upstream;
        }

        File dir = getCaptureDir(context);

        if (dir == null) {
            Log.e(TAG, "Capture enabled but no writable capture dir; recording nothing.");
            return upstream;
        }

        Log.d(TAG, "SABR capture ENABLED, writing to %s", dir);

        return () -> new TeeDataSource(upstream.createDataSource(), new SabrCaptureDataSink(context));
    }

    /** {@code Android/data/<pkg>/files/sabr-golden/} — readable over adb without root. */
    public static File getCaptureDir(Context context) {
        File base = context.getExternalFilesDir(null);

        if (base == null) {
            return null;
        }

        File dir = new File(base, DIR_NAME);

        if (!dir.exists() && !dir.mkdirs()) {
            return null;
        }

        return dir;
    }
}
