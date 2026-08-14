package com.google.android.exoplayer2.source.sabr.parser.adapter;

import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.extractor.mkv.MatroskaExtractor;
import com.google.android.exoplayer2.source.sabr.parser.SabrStream;
import com.google.android.exoplayer2.source.sabr.parser.misc.SabrExtractorInput;
import com.liskovsoft.sharedutils.mylogger.Log;

import java.io.IOException;

/**
 * Feeds a {@link MatroskaExtractor} from a SABR stream by substituting the {@link ExtractorInput}.
 *
 * <p>Wraps the extractor rather than extending it: {@code MatroskaExtractor.read} is {@code final}
 * upstream, so the previous subclass-and-override approach is no longer possible. Composition is the
 * better shape anyway — it depends only on the public {@link Extractor} interface rather than the
 * extractor's internals, so it will not break again the next time those change.
 */
public class SabrMatroskaAdapter implements Extractor {
    private static final String TAG = SabrMatroskaAdapter.class.getSimpleName();
    private final MatroskaExtractor delegate;
    private final SabrExtractorInput extractorInput;

    public SabrMatroskaAdapter(SabrStream sabrStream) {
        this.delegate = new MatroskaExtractor();
        this.extractorInput = new SabrExtractorInput(sabrStream);
    }

    public SabrMatroskaAdapter(int flags, SabrStream sabrStream) {
        this.delegate = new MatroskaExtractor(flags);
        this.extractorInput = new SabrExtractorInput(sabrStream);
    }

    @Override
    public int read(ExtractorInput input, PositionHolder seekPosition) throws IOException {
        int result = RESULT_END_OF_INPUT;

        try {
            extractorInput.init(input);
            result = delegate.read(extractorInput, seekPosition);
        } catch (Exception e) {
            Log.e(TAG, "User doing seek? %s: %s", e.getClass().getSimpleName(), e.getMessage());
            e.printStackTrace();
        } finally {
            if (result != RESULT_CONTINUE) {
                Log.e(TAG, "MatroskaAdapter: disposing, result=%s", result);
                extractorInput.dispose();
            }
        }

        return result;
    }

    @Override
    public boolean sniff(ExtractorInput input) throws IOException {
        return delegate.sniff(input);
    }

    @Override
    public void init(ExtractorOutput output) {
        delegate.init(output);
    }

    @Override
    public void seek(long position, long timeUs) {
        delegate.seek(position, timeUs);
    }

    @Override
    public void release() {
        delegate.release();
    }
}
