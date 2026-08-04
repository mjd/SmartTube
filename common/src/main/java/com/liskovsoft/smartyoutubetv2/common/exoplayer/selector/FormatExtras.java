package com.liskovsoft.smartyoutubetv2.common.exoplayer.selector;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.metadata.Metadata;

/**
 * Carries SmartTube-specific format attributes that {@link com.google.android.exoplayer2.Format} has
 * no field for.
 *
 * <p>Currently just {@code isDrc} (dynamic range compressed audio). The vendored player added it as
 * an extra public field on {@code Format}, which cannot be done to a published artifact.
 *
 * <p>Rewriting {@code Format.id} to encode DRC — the way the MPD builder does, with an
 * {@code <itag>-drc} suffix — is not an option either: the id is field 3 of the persisted
 * preference string, so changing it would invalidate every saved audio selection. {@link Metadata}
 * is the supported extension point, it travels with the format through track selection, and it
 * touches neither the id nor the wire format.
 */
public final class FormatExtras implements Metadata.Entry {
    public final boolean isDrc;

    public FormatExtras(boolean isDrc) {
        this.isDrc = isDrc;
    }

    private FormatExtras(Parcel in) {
        this.isDrc = in.readInt() != 0;
    }

    /** Whether {@code metadata} carries a DRC marker. */
    public static boolean isDrc(@Nullable Metadata metadata) {
        if (metadata == null) {
            return false;
        }

        for (int i = 0; i < metadata.length(); i++) {
            Metadata.Entry entry = metadata.get(i);

            if (entry instanceof FormatExtras) {
                return ((FormatExtras) entry).isDrc;
            }
        }

        return false;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) {
            return true;
        }

        if (!(obj instanceof FormatExtras)) {
            return false;
        }

        return isDrc == ((FormatExtras) obj).isDrc;
    }

    @Override
    public int hashCode() {
        return isDrc ? 1 : 0;
    }

    @Override
    public String toString() {
        return "FormatExtras: isDrc=" + isDrc;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(isDrc ? 1 : 0);
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
