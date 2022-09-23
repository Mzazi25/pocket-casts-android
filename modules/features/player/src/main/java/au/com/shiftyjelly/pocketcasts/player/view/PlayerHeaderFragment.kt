package au.com.shiftyjelly.pocketcasts.player.view

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import au.com.shiftyjelly.pocketcasts.models.entity.Episode
import au.com.shiftyjelly.pocketcasts.models.to.Chapter
import au.com.shiftyjelly.pocketcasts.models.type.EpisodeStatusEnum
import au.com.shiftyjelly.pocketcasts.player.R
import au.com.shiftyjelly.pocketcasts.player.databinding.AdapterPlayerHeaderBinding
import au.com.shiftyjelly.pocketcasts.player.view.video.VideoActivity
import au.com.shiftyjelly.pocketcasts.player.viewmodel.PlayerViewModel
import au.com.shiftyjelly.pocketcasts.preferences.Settings
import au.com.shiftyjelly.pocketcasts.repositories.chromecast.CastManager
import au.com.shiftyjelly.pocketcasts.repositories.images.into
import au.com.shiftyjelly.pocketcasts.repositories.playback.PlaybackManager
import au.com.shiftyjelly.pocketcasts.repositories.playback.PlaybackManager.PlaybackSource
import au.com.shiftyjelly.pocketcasts.ui.helper.FragmentHostListener
import au.com.shiftyjelly.pocketcasts.ui.images.PodcastImageLoaderThemed
import au.com.shiftyjelly.pocketcasts.ui.images.ThemedImageTintTransformation
import au.com.shiftyjelly.pocketcasts.ui.theme.ThemeColor
import au.com.shiftyjelly.pocketcasts.utils.extensions.dpToPx
import au.com.shiftyjelly.pocketcasts.utils.log.LogBuffer
import au.com.shiftyjelly.pocketcasts.views.extensions.updateColor
import au.com.shiftyjelly.pocketcasts.views.fragments.BaseFragment
import au.com.shiftyjelly.pocketcasts.views.helper.UiUtil
import au.com.shiftyjelly.pocketcasts.views.helper.WarningsHelper
import coil.load
import coil.request.ImageRequest
import coil.size.Size
import coil.transform.RoundedCornersTransformation
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.SimpleColorFilter
import com.airbnb.lottie.model.KeyPath
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.material.bottomsheet.BottomSheetBehavior
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.max
import au.com.shiftyjelly.pocketcasts.images.R as IR
import au.com.shiftyjelly.pocketcasts.localization.R as LR

private const val UPNEXT_DRAG_DISTANCE_MULTIPLIER = 1.85f // Open up next at a different rate than we are dragging
private const val UPNEXT_HEIGHT_OPEN_THRESHOLD = 0.15f // We only have an open threshold because we only control swipe up, swipe down is the standard bottom sheet behaviour
private const val UPNEXT_OUTLIER_THRESHOLD = 150.0f // Sometimes we get a random large delta, it seems better to filter them out or else you get random jumps

@AndroidEntryPoint
class PlayerHeaderFragment : BaseFragment(), PlayerClickListener {
    @Inject lateinit var castManager: CastManager
    @Inject lateinit var playbackManager: PlaybackManager
    @Inject lateinit var settings: Settings
    @Inject lateinit var warningsHelper: WarningsHelper

    lateinit var imageLoader: PodcastImageLoaderThemed
    private val viewModel: PlayerViewModel by activityViewModels()
    private var binding: AdapterPlayerHeaderBinding? = null
    private val playbackSource = PlaybackSource.PLAYER

    var skippedFirstTouch: Boolean = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = AdapterPlayerHeaderBinding.inflate(inflater, container, false)
        return binding?.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val binding = binding ?: return

        imageLoader = PodcastImageLoaderThemed(view.context)
        imageLoader.radiusPx = 8.dpToPx(view.context)
        imageLoader.shouldScale = false

        binding.viewModel = PlayerViewModel.PlayerHeader()

        binding.skipBack.setOnClickListener {
            onSkipBack()
            playbackManager.playbackSource = playbackSource
            (it as LottieAnimationView).playAnimation()
        }
        binding.skipForward.setOnClickListener {
            onSkipForward()
            playbackManager.playbackSource = playbackSource
            (it as LottieAnimationView).playAnimation()
        }
        binding.skipForward.setOnLongClickListener {
            onSkipForwardLongPress()
            (it as LottieAnimationView).playAnimation()
            true
        }
        binding.largePlayButton.setOnPlayClicked {
            onPlayClicked()
        }
        binding.seekBar.changeListener = object : PlayerSeekBar.OnUserSeekListener {
            override fun onSeekPositionChangeStop(progress: Int, seekComplete: () -> Unit) {
                viewModel.seekToMs(progress, seekComplete)
            }

            override fun onSeekPositionChanging(progress: Int) {}

            override fun onSeekPositionChangeStart() {
            }
        }

        val shelfViews = mapOf(
            ShelfItem.Effects.id to binding.effects,
            ShelfItem.Sleep.id to binding.sleep,
            ShelfItem.Star.id to binding.star,
            ShelfItem.Share.id to binding.share,
            ShelfItem.Podcast.id to binding.podcast,
            ShelfItem.Cast.id to binding.cast,
            ShelfItem.Played.id to binding.played,
            ShelfItem.Archive.id to binding.archive,
            ShelfItem.Download.id to binding.download
        )
        viewModel.trimmedShelfLive.observe(viewLifecycleOwner) {
            val visibleItems = it.first.subList(0, 4).map { it.id }
            binding.shelf.removeAllViews()
            visibleItems.forEach { id ->
                shelfViews[id]?.let { binding.shelf.addView(it) }
            }

            binding.shelf.addView(binding.playerActions)
        }

        binding.effects.setOnClickListener { onEffectsClick() }
        binding.previousChapter.setOnClickListener { onPreviousChapter() }
        binding.nextChapter.setOnClickListener { onNextChapter() }
        binding.sleep.setOnClickListener { onSleepClick() }
        binding.star.setOnClickListener { onStarClick() }
        binding.share.setOnClickListener { onShareClick() }
        binding.playerActions.setOnClickListener { onMoreClicked() }
        binding.podcast.setOnClickListener { showPodcast() }
        binding.played.setOnClickListener { viewModel.markCurrentlyPlayingAsPlayed(requireContext())?.show(childFragmentManager, "mark_as_played") }
        binding.archive.setOnClickListener { viewModel.archiveCurrentlyPlaying(resources)?.show(childFragmentManager, "archive") }
        binding.download.setOnClickListener { viewModel.downloadCurrentlyPlaying() }
        binding.videoView.playbackManager = playbackManager
        binding.videoView.setOnClickListener { onFullScreenVideoClick() }

        CastButtonFactory.setUpMediaRouteButton(binding.root.context, binding.castButton)
        binding.castButton.setAlwaysVisible(true)
        binding.castButton.updateColor(ThemeColor.playerContrast03(theme.activeTheme))

        setupUpNextDrag(view)

        viewModel.listDataLive.observe(viewLifecycleOwner) {
            val headerViewModel = it.podcastHeader
            binding.viewModel = headerViewModel

            binding.largePlayButton.setPlaying(isPlaying = headerViewModel.isPlaying, animate = false)

            val playerContrast1 = ThemeColor.playerContrast01(headerViewModel.theme)
            binding.largePlayButton.setCircleTintColor(playerContrast1)
            binding.skipBackText.setTextColor(playerContrast1)
            binding.jumpForwardText.setTextColor(playerContrast1)
            binding.skipBack.post { // this only works the second time it's called unless it's in a post
                binding.skipBack.addValueCallback(KeyPath("**"), LottieProperty.COLOR_FILTER) { SimpleColorFilter(playerContrast1) }
                binding.skipForward.addValueCallback(KeyPath("**"), LottieProperty.COLOR_FILTER) { SimpleColorFilter(playerContrast1) }
            }
            binding.episodeTitle.setTextColor(playerContrast1)

            if (headerViewModel.embeddedArtwork == PlayerViewModel.Artwork.None && headerViewModel.podcastUuid != null) {
                loadArtwork(headerViewModel.podcastUuid, binding.artwork)
            } else {
                loadEpisodeArtwork(headerViewModel.embeddedArtwork, binding.artwork)
            }

            loadChapterArtwork(headerViewModel.chapter, binding.chapterArtwork)
            binding.videoView.show = headerViewModel.isVideo
            binding.videoView.updatePlayerPrepared(headerViewModel.isPrepared)

            val highlightTint = playerHighlightColor(headerViewModel)
            val unhighlightTint = ThemeColor.playerContrast03(headerViewModel.theme)

            val effectTint = if (headerViewModel.isEffectsOn) highlightTint else unhighlightTint
            val effectResource = if (headerViewModel.isEffectsOn) R.drawable.ic_effects_on_32 else R.drawable.ic_effects_off_32
            binding.effects.setImageResource(effectResource)
            binding.effects.imageTintList = ColorStateList.valueOf(effectTint)

            val starTint = if (headerViewModel.isStarred) highlightTint else unhighlightTint
            val starResource = if (headerViewModel.isStarred) R.drawable.ic_star_filled_32 else R.drawable.ic_star_32
            binding.star.setImageResource(starResource)
            binding.star.imageTintList = ColorStateList.valueOf(starTint)

            val downloadResource = if (headerViewModel.downloadStatus == EpisodeStatusEnum.NOT_DOWNLOADED) {
                IR.drawable.ic_download
            } else if (headerViewModel.downloadStatus == EpisodeStatusEnum.DOWNLOADED) {
                IR.drawable.ic_downloaded
            } else {
                IR.drawable.ic_cancel
            }
            binding.download.setImageResource(downloadResource)

            if (headerViewModel.isChaptersPresent) {
                headerViewModel.chapter?.let {
                    binding.episodeTitle.setOnClickListener {
                        onHeaderChapterClick(headerViewModel.chapter)
                    }
                }
            } else {
                binding.episodeTitle.setOnClickListener(null)
            }

            binding.share.isVisible = !headerViewModel.isUserEpisode
            binding.star.isVisible = !headerViewModel.isUserEpisode

            val sleepTint = if (headerViewModel.isSleepRunning) highlightTint else unhighlightTint
            binding.sleep.alpha = if (headerViewModel.isSleepRunning) 1F else Color.alpha(sleepTint) / 255F * 2F
            binding.sleep.post { // this only works the second time it's called unless it's in a post
                binding.sleep.addValueCallback(KeyPath("**"), LottieProperty.COLOR, { sleepTint })
            }

            if (headerViewModel.chapter != null) {
                binding.chapterUrl.setOnClickListener {
                    headerViewModel.chapter.url?.let {
                        val intent = Intent(Intent.ACTION_VIEW)
                        intent.data = Uri.parse(it.toString())
                        try {
                            startActivity(intent)
                        } catch (e: ActivityNotFoundException) {
                            UiUtil.displayAlertError(requireContext(), getString(LR.string.player_open_url_failed, it), null)
                        }
                    }
                }
            } else {
                binding.chapterUrl.setOnClickListener(null)
            }

            binding.archive.setImageResource(if (headerViewModel.isUserEpisode) R.drawable.ic_delete_32 else R.drawable.ic_archive_32)

            binding.executePendingBindings()
        }
    }

    private fun setupUpNextDrag(view: View) {
        val context = context ?: return
        val swipeGesture = GestureDetectorCompat(
            context,
            object : GestureDetector.OnGestureListener {
                override fun onShowPress(e: MotionEvent?) {
                }

                override fun onSingleTapUp(e: MotionEvent?): Boolean {
                    return false
                }

                override fun onDown(e: MotionEvent?): Boolean {
                    return true
                }

                override fun onFling(e1: MotionEvent?, e2: MotionEvent?, velocityX: Float, velocityY: Float): Boolean {
                    return false
                }

                override fun onScroll(e1: MotionEvent?, e2: MotionEvent?, distanceX: Float, distanceY: Float): Boolean {
                    val upNextBottomSheetBehavior = (parentFragment as? PlayerContainerFragment)?.upNextBottomSheetBehavior
                        ?: return false
                    if (!skippedFirstTouch) {
                        // The first call is where the finger went down so like 600, after that its a delta.
                        // We only want the delta.
                        upNextBottomSheetBehavior.setPeekHeight(0, false)
                        skippedFirstTouch = true
                        return false
                    }

                    if ((upNextBottomSheetBehavior.peekHeight == 0 && distanceY < 0) || abs(distanceY) > UPNEXT_OUTLIER_THRESHOLD) { // Dragging down when we are at the bottom already or random large outlier
                        return false
                    }

                    val newPeekHeight = max(upNextBottomSheetBehavior.peekHeight + (distanceY * UPNEXT_DRAG_DISTANCE_MULTIPLIER).toInt(), 0)
                    if (newPeekHeight != upNextBottomSheetBehavior.peekHeight) {
                        upNextBottomSheetBehavior.peekHeight = newPeekHeight // Expensive call

                        (parentFragment as? PlayerContainerFragment)?.updateUpNextVisibility(true)
                    }

                    return upNextBottomSheetBehavior.peekHeight != 0
                }

                override fun onLongPress(e: MotionEvent?) {
                }
            }
        )

        view.setOnTouchListener { _, event ->
            if ((activity as? FragmentHostListener)?.getPlayerBottomSheetState() != BottomSheetBehavior.STATE_EXPANDED) {
                return@setOnTouchListener false
            }

            val action = event.actionMasked
            if (action == MotionEvent.ACTION_UP) {
                skippedFirstTouch = false

                val playerContainerFragment = parentFragment as? PlayerContainerFragment
                val upNextBottomSheetBehavior = playerContainerFragment?.upNextBottomSheetBehavior
                if (upNextBottomSheetBehavior != null) {
                    val peekHeight = upNextBottomSheetBehavior.peekHeight
                    val cutOff = Resources.getSystem().displayMetrics.heightPixels * UPNEXT_HEIGHT_OPEN_THRESHOLD
                    if (peekHeight > cutOff) {
                        upNextBottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
                    } else {
                        upNextBottomSheetBehavior.setPeekHeight(0, true)
                        upNextBottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED

                        if (peekHeight == 0) { // If we are already collapsed the state of the sheet won't change so the listener needs to be called manually
                            (parentFragment as? PlayerContainerFragment)?.updateUpNextVisibility(false)
                        }
                    }
                }
            }

            swipeGesture.onTouchEvent(event)
        }
    }

    private fun playerHighlightColor(viewModel: PlayerViewModel.PlayerHeader): Int {
        if (viewModel.isUserEpisode) {
            return theme.getUserFilePlayerHighlightColor()
        }

        return ThemeColor.playerHighlight01(viewModel.theme, viewModel.iconTintColor)
    }

    private var lastLoadedUuid: String? = null
    private fun loadArtwork(podcastUuid: String, imageView: ImageView) {
        if (lastLoadedUuid == podcastUuid) return
        imageLoader.largePlaceholder().onlyDarkTheme().loadPodcastUuid(podcastUuid).into(imageView)
        lastLoadedUuid = podcastUuid
        lastLoadedEmbedded = null
    }

    private var lastLoadedEmbedded: PlayerViewModel.Artwork? = null
    private fun loadEpisodeArtwork(embeddedArtwork: PlayerViewModel.Artwork, imageView: ImageView) {
        if (embeddedArtwork == PlayerViewModel.Artwork.None || lastLoadedEmbedded == embeddedArtwork) return

        if (embeddedArtwork is PlayerViewModel.Artwork.Url || embeddedArtwork is PlayerViewModel.Artwork.Path) {
            imageView.imageTintList = null

            val imageBuilder: ImageRequest.Builder.() -> Unit = {
                error(IR.drawable.defaultartwork_dark)
                size(Size.ORIGINAL)
                transformations(RoundedCornersTransformation(imageLoader.radiusPx.toFloat()), ThemedImageTintTransformation(imageView.context))
            }

            if (embeddedArtwork is PlayerViewModel.Artwork.Path) {
                imageView.load(data = File(embeddedArtwork.path), builder = imageBuilder)
            } else if (embeddedArtwork is PlayerViewModel.Artwork.Url) {
                imageView.load(data = embeddedArtwork.url, builder = imageBuilder)
            }

            lastLoadedEmbedded = embeddedArtwork
            lastLoadedUuid = null
        }
    }

    private fun loadChapterArtwork(chapter: Chapter?, imageView: ImageView) {
        chapter?.imagePath?.let {
            imageView.load(File(it)) {
                size(Size.ORIGINAL)
                transformations(RoundedCornersTransformation(imageLoader.radiusPx.toFloat()), ThemedImageTintTransformation(imageView.context))
            }
        } ?: run {
            imageView.setImageDrawable(null)
        }
    }

    override fun onShowNotesClick(episodeUuid: String) {
        val fragment = NotesFragment.newInstance(episodeUuid)
        openBottomSheet(fragment)
    }

    override fun onSkipBack() {
        viewModel.skipBackward()
    }

    override fun onSkipForward() {
        viewModel.skipForward()
    }

    override fun onSkipForwardLongPress() {
        viewModel.longSkipForwardOptionsDialog().show(parentFragmentManager, "longpressoptions")
    }

    override fun onEffectsClick() {
        EffectsFragment().show(parentFragmentManager, "effects_sheet")
    }

    override fun onSleepClick() {
        SleepFragment().show(parentFragmentManager, "effects_sheet")
    }

    override fun onStarClick() {
        viewModel.starToggle()
    }

    override fun onShareClick() {
        viewModel.shareDialog(context, childFragmentManager)?.show()
    }

    private fun showPodcast() {
        val podcast = viewModel.podcast
        (activity as FragmentHostListener).closePlayer()
        if (podcast != null) {
            (activity as? FragmentHostListener)?.openPodcastPage(podcast.uuid)
        } else {
            (activity as? FragmentHostListener)?.openCloudFiles()
        }
    }

    override fun onPreviousChapter() {
        viewModel.previousChapter()
    }

    override fun onNextChapter() {
        viewModel.nextChapter()
    }

    override fun onPlayingEpisodeActionsClick() {
        viewModel.episode?.let { episode ->
            (activity as? FragmentHostListener)?.openEpisodeDialog(episode.uuid, (episode as? Episode)?.podcastUuid, forceDark = true)
        }
    }

    fun onMoreClicked() {
        // stop double taps
        if (childFragmentManager.fragments.firstOrNull() is ShelfBottomSheet) {
            return
        }
        ShelfBottomSheet().show(childFragmentManager, "shelf_bottom_sheet")
    }

    override fun onPlayClicked() {
        playbackManager.playbackSource = playbackSource
        if (playbackManager.isPlaying()) {
            LogBuffer.i(LogBuffer.TAG_PLAYBACK, "Pause clicked in player")
            playbackManager.pause()
        } else {
            if (playbackManager.shouldWarnAboutPlayback()) {
                launch {
                    // show the stream warning if the episode isn't downloaded
                    playbackManager.getCurrentEpisode()?.let { episode ->
                        launch(Dispatchers.Main) {
                            if (episode.isDownloaded) {
                                viewModel.play()
                                warningsHelper.showBatteryWarningSnackbarIfAppropriate(snackbarParentView = view)
                            } else {
                                warningsHelper.streamingWarningDialog(episode, snackbarParentView = view)
                                    .show(parentFragmentManager, "streaming dialog")
                            }
                        }
                    }
                }
            } else {
                viewModel.play()
                warningsHelper.showBatteryWarningSnackbarIfAppropriate(snackbarParentView = view)
            }
        }
    }

    override fun onClosePlayer() {
        (activity as FragmentHostListener).closePlayer()
    }

    override fun onPictureInPictureClick() {
        val context = context ?: return
        context.startActivity(VideoActivity.buildIntent(enterPictureInPicture = true, context = context))
    }

    override fun onFullScreenVideoClick() {
        val context = context ?: return
        context.startActivity(VideoActivity.buildIntent(context = context))
    }

    private fun openBottomSheet(fragment: Fragment) {
        (activity as FragmentHostListener).showBottomSheet(fragment)
    }

    override fun onSeekPositionChangeStop(progress: Int, seekComplete: () -> Unit) {
        viewModel.seekToMs(progress, seekComplete)
    }

    override fun onHeaderChapterClick(chapter: Chapter) {
        (parentFragment as? PlayerContainerFragment)?.openChaptersAt(chapter)
    }
}
