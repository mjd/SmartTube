package com.liskovsoft.smartyoutubetv2.tv.ui.playback.other;

import android.os.Bundle;
import android.os.ResultReceiver;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector.QueueNavigator;

/**
 * Minimal queue navigator: every method is a no-op and the reported action set is empty, so the
 * media session advertises no queue navigation at all.
 *
 * <p>{@code ControlDispatcher} was removed upstream — commands now go straight to the {@link Player}
 * — so the transport callbacks lose that parameter, and {@code onCommand} disappears with it.
 * {@code onCurrentWindowIndexChanged} became {@link #onCurrentMediaItemIndexChanged(Player)}, part
 * of the window/media-item rename.
 */
public class BackboneQueueNavigator implements QueueNavigator {
    @Override
    public long getSupportedQueueNavigatorActions(Player player) {
        return 0;
    }

    @Override
    public void onTimelineChanged(Player player) {

    }

    @Override
    public void onCurrentMediaItemIndexChanged(Player player) {

    }

    @Override
    public long getActiveQueueItemId(@Nullable Player player) {
        return 0;
    }

    @Override
    public void onSkipToPrevious(Player player) {

    }

    @Override
    public void onSkipToQueueItem(Player player, long id) {

    }

    @Override
    public void onSkipToNext(Player player) {

    }

    /** Still required by CommandReceiver, just without the ControlDispatcher argument. */
    @Override
    public boolean onCommand(Player player, String command, Bundle extras, ResultReceiver cb) {
        return false;
    }
}
