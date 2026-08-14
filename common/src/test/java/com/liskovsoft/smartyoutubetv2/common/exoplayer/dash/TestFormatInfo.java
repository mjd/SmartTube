package com.liskovsoft.smartyoutubetv2.common.exoplayer.dash;

import com.liskovsoft.mediaserviceinterfaces.data.MediaFormat;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemStoryboard;
import com.liskovsoft.mediaserviceinterfaces.data.MediaSubtitle;

import io.reactivex.Observable;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A YouTube player response as {@link DashManifestParser2} sees one.
 *
 * <p>The live defaults are taken from a real capture of a 24/7 channel: no {@code lengthSeconds}, a
 * derived segment duration a few microseconds short of five seconds, and a start segment number in
 * the millions because the stream has been running for months. Those three together are what drive
 * every interesting branch in the parser, so they are the defaults rather than round numbers.
 */
final class TestFormatInfo implements MediaItemFormatInfo {
    /** Observed value: streamDurationMs / lastSegmentNum, so not an exact 5_000_000. */
    static final int SEGMENT_DURATION_US = 4_999_985;
    /** Observed value: the live edge minus the 24h DVR window, i.e. media time zero. */
    static final int START_SEGMENT_NUM = 2_662_708;

    private final List<MediaFormat> mAdaptiveFormats;
    private boolean mIsLive = true;
    private String mLengthSeconds = "0";
    private int mSegmentDurationUs = SEGMENT_DURATION_US;
    private int mStartSegmentNum = START_SEGMENT_NUM;

    private TestFormatInfo(MediaFormat... formats) {
        mAdaptiveFormats = new ArrayList<>(Arrays.asList(formats));
    }

    static TestFormatInfo live(MediaFormat... formats) {
        return new TestFormatInfo(formats);
    }

    /** A regular video: a real length, and no live segment numbering. */
    static TestFormatInfo vod(MediaFormat... formats) {
        TestFormatInfo info = new TestFormatInfo(formats);
        info.mIsLive = false;
        info.mLengthSeconds = "600";
        info.mSegmentDurationUs = 0;
        info.mStartSegmentNum = 0;
        return info;
    }

    TestFormatInfo withLengthSeconds(String lengthSeconds) {
        mLengthSeconds = lengthSeconds;
        return this;
    }

    @Override
    public List<MediaFormat> getAdaptiveFormats() {
        return mAdaptiveFormats;
    }

    @Override
    public boolean isLive() {
        return mIsLive;
    }

    @Override
    public String getLengthSeconds() {
        return mLengthSeconds;
    }

    @Override
    public int getSegmentDurationUs() {
        return mSegmentDurationUs;
    }

    @Override
    public int getStartSegmentNum() {
        return mStartSegmentNum;
    }

    @Override
    public String getVideoId() {
        return "YDvsBbKfLPA";
    }

    @Override
    public List<MediaSubtitle> getSubtitles() {
        return null;
    }

    // Nothing below is read while building a manifest.

    @Override
    public List<MediaFormat> getUrlFormats() {
        return null;
    }

    @Override
    public String getHlsManifestUrl() {
        return null;
    }

    @Override
    public String getDashManifestUrl() {
        return null;
    }

    @Override
    public String getTitle() {
        return null;
    }

    @Override
    public String getAuthor() {
        return null;
    }

    @Override
    public String getViewCount() {
        return null;
    }

    @Override
    public String getDescription() {
        return null;
    }

    @Override
    public String getChannelId() {
        return null;
    }

    @Override
    public boolean isLiveContent() {
        return mIsLive;
    }

    @Override
    public boolean containsMedia() {
        return true;
    }

    @Override
    public boolean containsSabrFormats() {
        return false;
    }

    @Override
    public boolean containsDashFormats() {
        return true;
    }

    @Override
    public boolean containsHlsUrl() {
        return false;
    }

    @Override
    public boolean containsDashUrl() {
        return false;
    }

    @Override
    public boolean containsUrlFormats() {
        return false;
    }

    @Override
    public boolean hasExtendedHlsFormats() {
        return false;
    }

    @Override
    public float getVolumeLevel() {
        return 1;
    }

    @Override
    public InputStream createMpdStream() {
        return null;
    }

    @Override
    public Observable<InputStream> createMpdStreamObservable() {
        return null;
    }

    @Override
    public List<String> createUrlList() {
        return null;
    }

    @Override
    public MediaItemStoryboard createStoryboard() {
        return null;
    }

    @Override
    public boolean isUnplayable() {
        return false;
    }

    @Override
    public boolean isUnknownError() {
        return false;
    }

    @Override
    public String getPlayabilityReason() {
        return null;
    }

    @Override
    public boolean isStreamSeekable() {
        return true;
    }

    @Override
    public String getStartTimestamp() {
        return null;
    }

    @Override
    public String getUploadDate() {
        return null;
    }

    @Override
    public long getStartTimeMs() {
        return 0;
    }

    @Override
    public String getPaidContentText() {
        return null;
    }

    @Override
    public String getVideoPlaybackUstreamerConfig() {
        return null;
    }

    @Override
    public String getServerAbrStreamingUrl() {
        return null;
    }

    @Override
    public String getPoToken() {
        return null;
    }

    @Override
    public String getVisitorCookie() {
        return null;
    }

    @Override
    public ClientInfo getClientInfo() {
        return null;
    }

    @Override
    public boolean isSynced() {
        return true;
    }

    @Override
    public boolean isAuth() {
        return false;
    }

    @Override
    public String getEventId() {
        return null;
    }

    @Override
    public String getVisitorMonitoringData() {
        return null;
    }

    @Override
    public String getOfParam() {
        return null;
    }

    @Override
    public String getClickTrackingParams() {
        return null;
    }

    @Override
    public void setClickTrackingParams(String clickTrackingParams) {
    }

    @Override
    public boolean isCacheActual() {
        return true;
    }

    @Override
    public void sync(MediaItemFormatInfo formatInfo) {
    }
}
