package com.liskovsoft.smartyoutubetv2.common.exoplayer.dash;

import com.liskovsoft.mediaserviceinterfaces.data.MediaFormat;

import java.util.List;

/**
 * A single adaptive format as the YouTube player response describes one, for
 * {@link DashManifestParser2} tests.
 *
 * <p>Hand-written rather than mocked: the module has no mocking library, and the defaults here are
 * themselves the interesting part. A real live format arrives with {@code initRange} and
 * {@code indexRange} absent and a url carrying {@code live=1}, and it is precisely that shape which
 * the parser has to handle without inventing an initialization request. Encoding it as the default
 * keeps every test honest about what the server actually sends.
 *
 * <p>Only the fields the parser reads are settable. Everything else returns a type default, which is
 * also what the response provides for a live format.
 */
final class TestMediaFormat implements MediaFormat {
    /** A live media url. MediaFormatUtils.isLiveMedia keys off the live=1 parameter. */
    static final String LIVE_URL = "https://rr3---sn-vgqs.googlevideo.com/videoplayback?itag=299&live=1";
    static final String VOD_URL = "https://rr3---sn-vgqs.googlevideo.com/videoplayback?itag=299";

    private String mITag = "299";
    private String mUrl = LIVE_URL;
    private String mMimeType = "video/mp4;+codecs=\"avc1.640033\"";
    private String mBitrate = "6690000";
    private int mWidth = 1920;
    private int mHeight = 1080;
    private String mFps = "60";
    private String mQualityLabel = "1080p60";
    private int mTargetDurationSec = 5;
    private String mInit;
    private String mIndex;

    static TestMediaFormat liveVideo() {
        return new TestMediaFormat();
    }

    static TestMediaFormat liveAudio() {
        TestMediaFormat format = new TestMediaFormat();
        format.mITag = "140";
        format.mUrl = LIVE_URL.replace("itag=299", "itag=140");
        format.mMimeType = "audio/mp4;+codecs=\"mp4a.40.2\"";
        format.mBitrate = "144000";
        format.mWidth = 0;
        format.mHeight = 0;
        format.mFps = null;
        format.mQualityLabel = null;
        return format;
    }

    /** A regular video: not live, and carrying the byte ranges a static manifest is built from. */
    static TestMediaFormat vodVideo() {
        TestMediaFormat format = new TestMediaFormat();
        format.mUrl = VOD_URL;
        format.mInit = "0-741";
        format.mIndex = "742-1725";
        return format;
    }

    TestMediaFormat withInit(String initRange) {
        mInit = initRange;
        return this;
    }

    @Override
    public String getITag() {
        return mITag;
    }

    @Override
    public String getUrl() {
        return mUrl;
    }

    @Override
    public String getSourceUrl() {
        return mUrl;
    }

    @Override
    public String getMimeType() {
        return mMimeType;
    }

    @Override
    public String getBitrate() {
        return mBitrate;
    }

    @Override
    public int getWidth() {
        return mWidth;
    }

    @Override
    public int getHeight() {
        return mHeight;
    }

    @Override
    public String getFps() {
        return mFps;
    }

    @Override
    public String getQualityLabel() {
        return mQualityLabel;
    }

    @Override
    public int getTargetDurationSec() {
        return mTargetDurationSec;
    }

    @Override
    public String getInit() {
        return mInit;
    }

    @Override
    public String getIndex() {
        return mIndex;
    }

    @Override
    public int compareTo(MediaFormat other) {
        return getITag().compareTo(other.getITag());
    }

    // Everything below is absent from a live adaptive format and unused by the parser.

    @Override
    public int getFormatType() {
        return FORMAT_TYPE_DASH;
    }

    @Override
    public boolean isDrc() {
        return false;
    }

    @Override
    public String getClen() {
        return null;
    }

    @Override
    public String getProjectionType() {
        return null;
    }

    @Override
    public String getXtags() {
        return null;
    }

    @Override
    public String getLmt() {
        return null;
    }

    @Override
    public String getFormat() {
        return null;
    }

    @Override
    public boolean isOtf() {
        return false;
    }

    @Override
    public String getOtfInitUrl() {
        return null;
    }

    @Override
    public String getOtfTemplateUrl() {
        return null;
    }

    @Override
    public String getLanguage() {
        return null;
    }

    @Override
    public int getMaxDvrDurationSec() {
        return 0;
    }

    @Override
    public int getApproxDurationMs() {
        return 0;
    }

    @Override
    public String getQuality() {
        return null;
    }

    @Override
    public String getSignature() {
        return null;
    }

    @Override
    public String getAudioSamplingRate() {
        return null;
    }

    @Override
    public List<String> getSegmentUrlList() {
        return null;
    }

    @Override
    public List<String> getGlobalSegmentList() {
        return null;
    }
}
