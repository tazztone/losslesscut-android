package com.tazztone.losslesscut.viewmodel

import com.tazztone.losslesscut.data.AppPreferences
import com.tazztone.losslesscut.domain.cache.IAnalysisCache
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val mockPrefs = mockk<AppPreferences>(relaxed = true)
    private val mockCache = mockk<IAnalysisCache>(relaxed = true)

    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { mockPrefs.undoLimitFlow } returns flowOf(30)
        coEvery { mockPrefs.snapshotFormatFlow } returns flowOf("JPEG")
        coEvery { mockPrefs.jpgQualityFlow } returns flowOf(95)
        coEvery { mockPrefs.customOutputUriFlow } returns flowOf(null)
        coEvery { mockPrefs.accentColorFlow } returns flowOf("cyan")
        coEvery { mockPrefs.autoExtractWaveformsFlow } returns flowOf(true)
        coEvery { mockPrefs.defaultVisualFrameStepFlow } returns flowOf(5)
        coEvery { mockPrefs.cacheCapacityMBFlow } returns flowOf(250)
        coEvery { mockPrefs.cacheRetentionDaysFlow } returns flowOf(30)
        coEvery { mockPrefs.languageFlow } returns flowOf("system")
        coEvery { mockPrefs.deleteOriginalAfterExportFlow } returns flowOf(false)
        coEvery { mockCache.getCacheUsageBytes() } returns 1024L * 1024L * 10L

        viewModel = SettingsViewModel(mockPrefs, mockCache, testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialUiState() = runTest {
        val state = viewModel.uiState.first()
        assertEquals(30, state.undoLimit)
        assertEquals("JPEG", state.snapshotFormat)
        assertEquals("cyan", state.accentColor)
        assertEquals("system", state.language)
        assertEquals(false, state.deleteOriginalAfterExport)
    }

    @Test
    fun testSetLanguage() = runTest {
        viewModel.setLanguage("de")
        coVerify { mockPrefs.setLanguage("de") }
    }

    @Test
    fun testSetDeleteOriginalAfterExport() = runTest {
        viewModel.setDeleteOriginalAfterExport(true)
        coVerify { mockPrefs.setDeleteOriginalAfterExport(true) }
    }

    @Test
    fun testClearCache() = runTest {
        viewModel.clearCache()
        coVerify { mockCache.clearCache() }
    }
}
