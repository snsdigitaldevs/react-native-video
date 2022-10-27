package com.brentvatne.media.play

import android.os.Bundle
import android.support.v4.media.session.PlaybackStateCompat
import com.brentvatne.react.R
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector

private const val TEN_SECONDS = 10 * 1000
abstract class BaseSkipActionProvider(private val player: Player) :
  MediaSessionConnector.CustomActionProvider {
  override fun onCustomAction(action: String?, extras: Bundle?) {
    if (player.isCurrentWindowSeekable) executeSkipAction(player)
  }

  abstract fun executeSkipAction(player: Player)
  abstract fun getActionName(): String
  abstract fun getResourceId(): Int
  abstract fun getActionLabel(): String

  override fun getCustomAction(): PlaybackStateCompat.CustomAction {
    return PlaybackStateCompat.CustomAction.Builder(
      getActionName(),
      getActionLabel(),
      getResourceId()
    ).build()
  }
}

private const val ACTION_SKIP_FORWARD_TEN_SECONDS = "action_skip_forward_10_seconds"

class SkipForwardTenActionProvider(player: Player) : BaseSkipActionProvider(player) {

  override fun executeSkipAction(player: Player) {
    player.seekTo(player.duration.coerceAtMost(player.currentPosition + TEN_SECONDS))
  }

  override fun getActionName(): String = ACTION_SKIP_FORWARD_TEN_SECONDS

  override fun getResourceId(): Int = R.drawable.skip_forward_10

  override fun getActionLabel(): String = "SkipForwardTenSeconds"

}

private const val ACTION_SKIP_BACKWARD_TEN_SECONDS = "action_skip_backward_10_seconds"

class SkipBackwardTenActionProvider(player: Player) : BaseSkipActionProvider(player) {

  override fun executeSkipAction(player: Player) {
    player.seekTo((player.currentPosition - TEN_SECONDS).coerceAtLeast(0))
  }

  override fun getActionName(): String = ACTION_SKIP_BACKWARD_TEN_SECONDS

  override fun getResourceId(): Int = R.drawable.skip_backward_10

  override fun getActionLabel(): String = "SkipBackwardTenSeconds"

}
