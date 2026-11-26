package fr.prisme.player.player

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import fr.prisme.player.R
import fr.prisme.player.ui.theme.PrismPalette
import kotlinx.coroutines.delay

/**
 * Lets the player activity drive the web layer from remote keys. Views are only held
 * while the layer is on screen; nothing here exists for ordinary streams.
 */
class WebChannelController {
    internal var webView: WebView? = null
    internal var youTube: YouTubePlayer? = null
    internal var youTubeSecond = 0f
    internal var youTubePlaying = false
    internal var leaveFullscreen: (() -> Unit)? = null

    /** Leaves a page's video fullscreen, then steps back in its history; false if neither. */
    fun handleBack(): Boolean {
        leaveFullscreen?.let { it(); return true }
        val web = webView ?: return false
        if (!web.canGoBack()) return false
        web.goBack()
        return true
    }

    fun togglePlayPause() {
        val player = youTube ?: return
        if (youTubePlaying) player.pause() else player.play()
    }

    fun seekBy(seconds: Float) {
        youTube?.seekTo((youTubeSecond + seconds).coerceAtLeast(0f))
    }

    fun focusPage() {
        webView?.requestFocus()
    }
}

/**
 * Full-screen YouTube player or web page for playlist entries that are not streams.
 * Created only while such a channel is playing, and torn down on zap or when the
 * activity stops, so ordinary channels pay nothing for it.
 */
@Composable
internal fun WebChannelLayer(
    target: WebChannel,
    controller: WebChannelController,
    onYouTubeError: (message: String) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        when (target) {
            is WebChannel.YouTube -> key(target.videoId) { YouTubeChannel(target.videoId, controller, onYouTubeError) }
            is WebChannel.Page -> key(target.url) { PageChannel(target.url, controller) }
        }

        var hintVisible by remember(target) { mutableStateOf(true) }
        LaunchedEffect(target) {
            delay(HINT_DURATION_MS)
            hintVisible = false
        }
        AnimatedVisibility(
            visible = hintVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = stringResource(
                    if (target is WebChannel.YouTube) R.string.web_hint_youtube else R.string.web_hint_page,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = PrismPalette.TextPrimary,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(PrismPalette.SurfaceControl)
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun YouTubeChannel(
    videoId: String,
    controller: WebChannelController,
    onError: (String) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    var view by remember { mutableStateOf<YouTubePlayerView?>(null) }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val options = IFramePlayerOptions.Builder()
                .controls(0)
                .rel(0)
                .ivLoadPolicy(3)
                // YouTube now rejects embeds without an identifying origin/referrer; the
                // library's 12.x default (youtube.com) no longer passes. 13.x uses this value.
                .origin("https://${ctx.packageName}")
                .build()
            YouTubePlayerView(ctx).apply {
                enableAutomaticInitialization = false
                // Remote keys go to the activity, which maps them to play/pause and seek.
                isFocusable = false
                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                lifecycleOwner.lifecycle.addObserver(this)
                initialize(object : AbstractYouTubePlayerListener() {
                    override fun onReady(youTubePlayer: YouTubePlayer) {
                        controller.youTube = youTubePlayer
                        youTubePlayer.loadVideo(videoId, 0f)
                    }

                    override fun onStateChange(youTubePlayer: YouTubePlayer, state: PlayerConstants.PlayerState) {
                        controller.youTubePlaying = state == PlayerConstants.PlayerState.PLAYING ||
                            state == PlayerConstants.PlayerState.BUFFERING
                    }

                    override fun onCurrentSecond(youTubePlayer: YouTubePlayer, second: Float) {
                        controller.youTubeSecond = second
                    }

                    override fun onError(youTubePlayer: YouTubePlayer, error: PlayerConstants.PlayerError) {
                        android.util.Log.w("WebChannelLayer", "YouTube error $error for $videoId")
                        onError(
                            context.getString(
                                when (error) {
                                    PlayerConstants.PlayerError.VIDEO_NOT_PLAYABLE_IN_EMBEDDED_PLAYER ->
                                        R.string.web_youtube_not_embeddable
                                    PlayerConstants.PlayerError.VIDEO_NOT_FOUND -> R.string.web_youtube_not_found
                                    else -> R.string.web_youtube_error
                                },
                            ),
                        )
                    }
                }, options)
                view = this
            }
        },
    )

    DisposableEffect(videoId) {
        onDispose {
            controller.youTube = null
            controller.youTubePlaying = false
            controller.youTubeSecond = 0f
            view?.let {
                lifecycleOwner.lifecycle.removeObserver(it)
                it.release()
            }
            view = null
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun PageChannel(url: String, controller: WebChannelController) {
    // A page's <video> asking for fullscreen hands us its own view to show on top.
    var fullscreenView by remember { mutableStateOf<View?>(null) }
    var fullscreenCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                setBackgroundColor(android.graphics.Color.BLACK)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                webViewClient = object : WebViewClient() {
                    // Only web links stay in the page; app or store links are ignored.
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val scheme = request.url.scheme?.lowercase()
                        return scheme != "http" && scheme != "https"
                    }

                    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                        view.requestFocus()
                    }
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                        fullscreenView = view
                        fullscreenCallback = callback
                    }

                    override fun onHideCustomView() {
                        fullscreenView = null
                        fullscreenCallback = null
                    }
                }
                isFocusable = true
                isFocusableInTouchMode = true
                controller.webView = this
                loadUrl(url)
                requestFocus()
            }
        },
        onRelease = { web ->
            if (controller.webView === web) controller.webView = null
            web.stopLoading()
            web.destroy()
        },
    )

    fullscreenView?.let { video ->
        key(video) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    FrameLayout(ctx).apply {
                        setBackgroundColor(android.graphics.Color.BLACK)
                        (video.parent as? ViewGroup)?.removeView(video)
                        addView(video, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                    }
                },
            )
        }
    }

    DisposableEffect(fullscreenCallback) {
        controller.leaveFullscreen = fullscreenCallback?.let { callback ->
            {
                callback.onCustomViewHidden()
                fullscreenView = null
                fullscreenCallback = null
            }
        }
        onDispose { controller.leaveFullscreen = null }
    }
}

private const val HINT_DURATION_MS = 6_000L
