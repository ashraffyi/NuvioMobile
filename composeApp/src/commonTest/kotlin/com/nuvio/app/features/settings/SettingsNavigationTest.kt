package com.nuvio.app.features.settings

import com.nuvio.app.features.player.AndroidPlaybackEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsNavigationTest {
    @Test
    fun layoutChildrenReturnToLayout() {
        listOf(
            SettingsPage.Homescreen,
            SettingsPage.MetaScreen,
            SettingsPage.ContinueWatching,
            SettingsPage.PosterCustomization,
            SettingsPage.Streams,
        ).forEach { page ->
            assertEquals(SettingsPage.Layout, page.previousPage())
            assertEquals(SettingsCategory.Look, page.category)
        }
        assertEquals(SettingsPage.Root, SettingsPage.Layout.previousPage())
    }

    @Test
    fun playbackChildrenReturnToPlaybackInTheWatchGroup() {
        PlaybackSection.entries.forEach { section ->
            assertEquals(SettingsPage.Playback, section.page.previousPage())
            assertEquals(SettingsCategory.Watch, section.page.category)
        }
        assertEquals(SettingsPage.Root, SettingsPage.Playback.previousPage())
    }

    @Test
    fun bufferOptionsFollowThePlaybackEngine() {
        AndroidPlaybackEngine.entries.forEach { engine ->
            val sections = visiblePlaybackSections(engine = engine, p2pVisible = false)
            assertEquals(engine != AndroidPlaybackEngine.Libmpv, PlaybackSection.Buffer in sections)
            assertFalse(PlaybackSection.P2p in sections)
            assertTrue(PlaybackSection.Interface in sections)
        }
        assertTrue(PlaybackSection.P2p in visiblePlaybackSections(AndroidPlaybackEngine.Auto, true))
    }

    @Test
    fun oldTabletCategoriesRestoreToTheirNewGroups() {
        assertEquals(SettingsCategory.You, settingsCategoryForName("Account"))
        assertEquals(SettingsCategory.Look, settingsCategoryForName("General"))
        assertEquals(SettingsCategory.System, settingsCategoryForName("About"))
        assertEquals(SettingsCategory.System, settingsCategoryForName("Advanced"))
        assertEquals(SettingsCategory.Services, settingsCategoryForName("Services"))
        assertEquals(SettingsCategory.Look, settingsCategoryForName("unknown"))
    }
}
