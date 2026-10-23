package com.tx24.spicyplayer.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.View
import android.widget.RemoteViews
import com.tx24.spicyplayer.LyricsActivity
import com.tx24.spicyplayer.R

/**
 * The home screen widget: the song, the line being sung and the next one over the cover's colours,
 * with play/pause. A tap anywhere else opens the lyrics screen. [NowPlayingHolder] keeps it current.
 */
class LyricsWidget : AppWidgetProvider() {
    internal data class Content(
        val song: String,
        val lines: WidgetLines,
        val playing: Boolean,
        val backdrop: Bitmap?,
        val accent: Int,
    )

    override fun onEnabled(context: Context) = NowPlayingHolder.start(context)

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) = NowPlayingHolder.start(context)

    override fun onDisabled(context: Context) = NowPlayingHolder.stop()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_PLAY_PAUSE) {
            NowPlayingHolder.start(context)
            NowPlayingHolder.playPause()
            return
        }
        super.onReceive(context, intent)
    }

    internal companion object {
        private const val ACTION_PLAY_PAUSE = "com.tx24.spicyplayer.widget.PLAY_PAUSE"
        /** The backdrop without a cover: the app's near-black. */
        const val DEFAULT_ACCENT = 0xFF121212.toInt()

        fun placed(context: Context): Boolean = ids(context).isNotEmpty()

        private fun ids(context: Context): IntArray =
            AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, LyricsWidget::class.java))

        /** Sends [content] to every placed widget: only the lines unless [full]. */
        fun show(context: Context, content: Content, full: Boolean) {
            val ids = ids(context)
            if (ids.isEmpty()) return
            val manager = AppWidgetManager.getInstance(context)
            val views = RemoteViews(context.packageName, R.layout.widget_lyrics)
            views.setTextViewText(R.id.widget_current, content.lines.current)
            views.setTextViewText(R.id.widget_next, content.lines.next)
            views.setViewVisibility(R.id.widget_next, if (content.lines.next.isEmpty()) View.GONE else View.VISIBLE)
            if (!full) {
                manager.partiallyUpdateAppWidget(ids, views)
                return
            }
            views.setTextViewText(R.id.widget_song, content.song)
            views.setViewVisibility(R.id.widget_song, if (content.song.isEmpty()) View.GONE else View.VISIBLE)
            views.setInt(R.id.widget_backdrop, "setBackgroundColor", content.accent)
            if (content.backdrop != null) views.setImageViewBitmap(R.id.widget_backdrop, content.backdrop)
            else views.setImageViewResource(R.id.widget_backdrop, android.R.color.transparent)
            views.setImageViewResource(
                R.id.widget_play_pause,
                if (content.playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            )
            views.setContentDescription(R.id.widget_play_pause, if (content.playing) "Pause" else "Play")
            val open = Intent(context, LyricsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            views.setOnClickPendingIntent(
                R.id.widget_root,
                PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
            )
            val playPause = Intent(context, LyricsWidget::class.java).setAction(ACTION_PLAY_PAUSE)
            views.setOnClickPendingIntent(
                R.id.widget_play_pause,
                PendingIntent.getBroadcast(context, 0, playPause, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
            )
            manager.updateAppWidget(ids, views)
        }
    }
}
