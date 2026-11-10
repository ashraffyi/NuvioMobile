package com.nuvio.app.features.player

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.features.watchprogress.WatchProgressCodec
import com.nuvio.app.features.watchprogress.WatchProgressPlaybackSession
import com.nuvio.app.features.watchprogress.WatchProgressRepository
import com.nuvio.app.features.watchprogress.WatchProgressStorage
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ExternalPlaybackProgressTest {
    private val source = "https://example.com/movie.mkv"
    private fun session(profileId: Int = ProfileRepository.activeProfileId) = WatchProgressPlaybackSession(
        profileId = profileId, contentType = "movie", parentMetaId = "tt123", parentMetaType = "movie",
        videoId = "tt123", title = "Movie", lastSourceUrl = source,
    )

    @Before
    fun initialize() {
        val context = RuntimeEnvironment.getApplication()
        clearPreferences()
        WatchProgressStorage.initialize(context)
        PlayerSettingsStorage.initialize(context)
        ExternalPlayerPlatform.initialize(context)
        WatchProgressRepository.clearLocalState()
    }

    @After
    fun cleanup() {
        WatchProgressRepository.clearLocalState()
        clearPreferences()
    }

    @Test
    fun androidStillSendsResumePositionAndRecordsCompatiblePlayerResults() = runBlocking {
        val request = ExternalPlayerPlaybackRequest(sourceUrl = source, title = "Movie", resumePositionMs = 125_000L)
        val built = assertIs<ExternalPlayerIntentResult.Success>(ExternalPlayerPlatform.buildIntent(request, "android_system"))
        val intent = assertIs<Intent>(built.intent)
        assertEquals(125_000, intent.getIntExtra("position", 0))
        assertEquals(125_000, intent.getIntExtra("startfrom", 0))
        assertTrue(intent.getBooleanExtra("return_result", false))
        val returned = Intent().apply {
            putExtra("position", 1_800_000)
            putExtra("duration", 3_600_000)
            putExtra("end_by", "user")
        }
        val result = assertNotNull(ExternalPlayerActivityContract().parseResult(Activity.RESULT_OK, returned))
        recordExternalPlaybackProgress(
            ExternalPlaybackResult(result.positionMs, result.durationMs, result.endedByUser),
            fallbackSession = session(),
        )
        val entry = assertNotNull(WatchProgressRepository.progressForVideo("tt123"))
        assertEquals(1_800_000L, entry.lastPositionMs)
        assertEquals(3_600_000L, entry.durationMs)
        assertFalse(entry.isCompleted)
    }

    @Test
    fun mxPlayerCompletionWithoutPositionIsRecordedAsWatched() = runBlocking {
        // MX Player omits "position" and "duration" when playback runs to the end.
        val returned = Intent().apply { putExtra("end_by", "playback_completion") }
        val result = assertNotNull(ExternalPlayerActivityContract().parseResult(Activity.RESULT_OK, returned))
        assertFalse(result.endedByUser)
        recordExternalPlaybackProgress(
            ExternalPlaybackResult(result.positionMs, result.durationMs, result.endedByUser),
            fallbackSession = session(),
        )
        val entry = assertNotNull(WatchProgressRepository.progressForVideo("tt123"))
        assertTrue(entry.isCompleted)
    }

    @Test
    fun mxPlayerCompletionWithoutPositionIsDroppedOnErrorResult() {
        val returned = Intent().apply { putExtra("end_by", "playback_completion") }
        assertNull(ExternalPlayerActivityContract().parseResult(Activity.RESULT_FIRST_USER, returned))
    }

    private fun clearPreferences() {
        val context = RuntimeEnvironment.getApplication()
        listOf("nuvio_watch_progress", "nuvio_player_settings").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }
}
