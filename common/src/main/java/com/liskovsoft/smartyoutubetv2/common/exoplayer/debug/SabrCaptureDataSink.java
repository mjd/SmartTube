package com.liskovsoft.smartyoutubetv2.common.exoplayer.debug;

import android.content.Context;

import com.google.android.exoplayer2.upstream.DataSink;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.liskovsoft.sharedutils.mylogger.Log;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.io.FileWriter;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Debug-only {@link DataSink} that records raw SABR response bodies to disk, one file per request,
 * so they can be replayed through the parser offline.
 *
 * <p>SABR is SmartTube's own implementation of a YouTube protocol that exists in no upstream
 * ExoPlayer or Media3 release, has no tests, and tracks a server-side format we do not control.
 * Porting it to a new player is therefore the highest-risk part of any player migration: there is
 * nothing that says whether the port still parses real traffic identically.
 *
 * <p>These captures are that missing baseline. They must be taken on the CURRENT player, BEFORE any
 * porting starts — once the parser has changed there is no way to reconstruct what "correct" was.
 * Each capture is the exact byte stream the parser consumed, so a replay harness can assert the
 * ported parser produces the same result from the same input.
 *
 * <p>Writes to the app's external files dir (`Android/data/<pkg>/files/sabr-golden/`), which is
 * pullable over adb without root. Off unless {@link SabrCapture#isEnabled()}.
 */
public class SabrCaptureDataSink implements DataSink {
    private static final String TAG = SabrCaptureDataSink.class.getSimpleName();
    private static final AtomicInteger sIndex = new AtomicInteger();

    private final File mDir;
    private OutputStream mOut;
    private long mBytes;
    private String mName;

    public SabrCaptureDataSink(Context context) {
        mDir = SabrCapture.getCaptureDir(context);
    }

    @Override
    public void open(DataSpec dataSpec) throws IOException {
        if (mDir == null) {
            return;
        }

        int index = sIndex.getAndIncrement();
        mName = String.format("sabr-%04d", index);
        mBytes = 0;

        mOut = new BufferedOutputStream(new FileOutputStream(new File(mDir, mName + ".bin")));

        // Sidecar: what the bytes were a response to. Not needed to replay the parser, but without
        // it a capture directory is an unlabelled pile of binaries.
        try (Writer w = new FileWriter(new File(mDir, mName + ".meta"))) {
            w.write("uri=" + dataSpec.uri + "\n");
            w.write("position=" + dataSpec.position + "\n");
            w.write("length=" + dataSpec.length + "\n");
            w.write("httpMethod=" + dataSpec.httpMethod + "\n");
            w.write("httpBodyLength=" + (dataSpec.httpBody != null ? dataSpec.httpBody.length : 0) + "\n");
        } catch (IOException e) {
            Log.e(TAG, "Cannot write capture sidecar: %s", e.getMessage());
        }
    }

    @Override
    public void write(byte[] buffer, int offset, int length) throws IOException {
        if (mOut == null) {
            return;
        }

        mOut.write(buffer, offset, length);
        mBytes += length;
    }

    @Override
    public void close() throws IOException {
        if (mOut == null) {
            return;
        }

        mOut.close();
        mOut = null;
        Log.d(TAG, "Captured %s (%s bytes)", mName, mBytes);
    }
}
