package com.liskovsoft.smartyoutubetv2.player.extras;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.metadata.Metadata;

/**
 * Carries the SmartTube-specific format attributes that {@link Format} has no field for.
 *
 * <p>Two of them:
 * <ul>
 *   <li>{@code isDrc} — dynamic range compressed audio, used to prefer or avoid DRC tracks.
 *   <li>{@code lastModified} — YouTube's {@code lmt}, which SABR echoes back to the server in the
 *       {@code FormatId} of every playback request.
 * </ul>
 *
 * <p>The vendored player carried both as extra public fields on {@code Format}. That cannot be done
 * to a published artifact, and neither can be smuggled through {@code Format.id}: the id is field 3
 * of the persisted preference string, so rewriting it (to add a {@code -drc} suffix, say) would
 * invalidate every saved audio selection. {@link Metadata} is the supported extension point and,
 * critically, it travels with the format through track selection — which is where both values are
 * actually read.
 *
 * <p>It lives in the {@code :sabr} module for a boring reason: the DASH parser and track selector
 * are in {@code :common}, the SABR parser is here, and {@code :common} depends on {@code :sabr} —
 * so this is the only place both can reach. The package name is deliberately neutral rather than
 * SABR-flavoured, since the DASH path uses it just as much.
 */
public final class FormatExtras implements Metadata.Entry {
    public final boolean isDrc;
    public final long lastModified;

    public FormatExtras(boolean isDrc, long lastModified) {
        this.isDrc = isDrc;
        this.lastModified = lastModified;
    }

    private FormatExtras(Parcel in) {
        this.isDrc = in.readInt() != 0;
        this.lastModified = in.readLong();
    }

    @Nullable
    private static FormatExtras find(@Nullable Metadata metadata) {
        if (metadata == null) {
            return null;
        }

        for (int i = 0; i < metadata.length(); i++) {
            Metadata.Entry entry = metadata.get(i);

            if (entry instanceof FormatExtras) {
                return (FormatExtras) entry;
            }
        }

        return null;
    }

    /** Whether {@code metadata} marks the format as DRC. */
    public static boolean isDrc(@Nullable Metadata metadata) {
        FormatExtras extras = find(metadata);
        return extras != null && extras.isDrc;
    }

    /** The format's {@code lmt}, or {@code defaultValue} if it carries none. */
    public static long getLastModified(@Nullable Metadata metadata, long defaultValue) {
        FormatExtras extras = find(metadata);
        return extras != null ? extras.lastModified : defaultValue;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) {
            return true;
        }

        if (!(obj instanceof FormatExtras)) {
            return false;
        }

        FormatExtras other = (FormatExtras) obj;

        return isDrc == other.isDrc && lastModified == other.lastModified;
    }

    @Override
    public int hashCode() {
        return (isDrc ? 1 : 0) * 31 + (int) (lastModified ^ (lastModified >>> 32));
    }

    @Override
    public String toString() {
        return "FormatExtras: isDrc=" + isDrc + ", lastModified=" + lastModified;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(isDrc ? 1 : 0);
        dest.writeLong(lastModified);
    }

    public static final Parcelable.Creator<FormatExtras> CREATOR =
            new Parcelable.Creator<FormatExtras>() {
                @Override
                public FormatExtras createFromParcel(Parcel in) {
                    return new FormatExtras(in);
                }

                @Override
                public FormatExtras[] newArray(int size) {
                    return new FormatExtras[size];
                }
            };
}
