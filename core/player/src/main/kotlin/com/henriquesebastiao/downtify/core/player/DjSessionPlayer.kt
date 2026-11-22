package com.henriquesebastiao.downtify.core.player

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.util.UnstableApi

/** A DJ has track markers inside one media item. BasePlayer's physical last-item checks
 * must not swallow Next/Previous before they reach that marker transport. */
@UnstableApi
internal class DjSessionPlayer(val deck: LocalDjPlayer) : ForwardingPlayer(deck) {
    override fun seekToNext() {
        if (deck.nextInsideDj() == null) super.seekToNext()
    }

    override fun seekToNextMediaItem() {
        if (deck.nextInsideDj() == null) super.seekToNextMediaItem()
    }

    override fun seekToPrevious() {
        if (deck.previousInsideDj() == null) super.seekToPrevious()
    }

    override fun seekToPreviousMediaItem() {
        if (deck.previousInsideDj() == null) super.seekToPreviousMediaItem()
    }
}
