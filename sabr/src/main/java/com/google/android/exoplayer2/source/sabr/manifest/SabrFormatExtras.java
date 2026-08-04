package com.google.android.exoplayer2.source.sabr.manifest;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.metadata.Metadata;

/**
 * Carries the SABR-specific bits of a format that {@link com.google.android.exoplayer2.Format} has
 * no field for.
 *
 * <p>Currently just {@code lastModified} (YouTube's {@code lmt}), which SABR echoes back to the
 * server in the {@code FormatId} of every playback request. The vendored player carried it as an
 * extra public field on {@code Format}; that is not possible against a published artifact, and
 * adding it would mean forking the player again for the sake of one long.
 *
 * <p>{@link Metadata} is the supported extension point and, critically, it travels with the
 * {@code Format} through track selection — which is where the value is actually needed, since
 * {@code FormatSelector} receives a {@code Format} from {@code ExoTrackSelection} and has no route
 * back to the manifest.
 */
public final class SabrFormatExtras implements Metadata.Entry {
    public final long lastModified;

    public SabrFormatExtras(long lastModified) {
        this.lastModified = lastModified;
    }

    private SabrFormatExtras(Parcel in) {
        this.lastModified = in.readLong();
    }

    /** Returns the value carried by {@code format}, or {@code defaultValue} if it carries none. */
    public static long getLastModified(@Nullable Metadata metadata, long defaultValue) {
        if (metadata == null) {
            return defaultValue;
        }

        for (int i = 0; i < metadata.length(); i++) {
            Metadata.Entry entry = metadata.get(i);

            if (entry instanceof SabrFormatExtras) {
                return ((SabrFormatExtras) entry).lastModified;
            }
        }

        return defaultValue;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) {
            return true;
        }

        if (!(obj instanceof SabrFormatExtras)) {
            return false;
        }

        return lastModified == ((SabrFormatExtras) obj).lastModified;
    }

    @Override
    public int hashCode() {
        return (int) (lastModified ^ (lastModified >>> 32));
    }

    @Override
    public String toString() {
        return "SabrFormatExtras: lastModified=" + lastModified;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(lastModified);
    }

    public static final Parcelable.Creator<SabrFormatExtras> CREATOR =
            new Parcelable.Creator<SabrFormatExtras>() {
                @Override
                public SabrFormatExtras createFromParcel(Parcel in) {
                    return new SabrFormatExtras(in);
                }

                @Override
                public SabrFormatExtras[] newArray(int size) {
                    return new SabrFormatExtras[size];
                }
            };
}
