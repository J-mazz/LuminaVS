package com.lumina.engine.viewmodel

import com.lumina.engine.LuminaState
import com.lumina.engine.LuminaViewModel
import com.lumina.engine.FrameTiming
import com.lumina.engine.NativeBridge
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LuminaViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `processUserInput sends state to nativeBridge`() {
        val mockBridge = mockk<NativeBridge>(relaxed = true)
        val viewModel = LuminaViewModel(ioDispatcher = testDispatcher)
        viewModel.nativeBridge = mockBridge

        viewModel.setUserInput("hello")
        viewModel.processUserInput("hello")

        // The ViewModel should call updateState on the bridge eventually.
        verify(atLeast = 1) { mockBridge.updateState(any()) }
    }

    @Test
    fun `refreshTiming calls getFrameTiming`() {
        val mockBridge = mockk<NativeBridge>(relaxed = true)
        every { mockBridge.getFrameTiming() } returns FrameTiming()

        val viewModel = LuminaViewModel(ioDispatcher = testDispatcher)
        viewModel.nativeBridge = mockBridge

        viewModel.refreshTiming()

        verify(atLeast = 1) { mockBridge.getFrameTiming() }
    }

    @Test
    fun `setRenderMode forwards to bridge`() {
        val mockBridge = mockk<NativeBridge>(relaxed = true)
        val viewModel = LuminaViewModel(ioDispatcher = testDispatcher)
        viewModel.nativeBridge = mockBridge

        viewModel.setRenderMode(com.lumina.engine.RenderMode.PASSTHROUGH)

        verify { mockBridge.setRenderMode(any()) }
    }

    @Test
    fun `addEffect increments activeEffectCount`() {
        val viewModel = LuminaViewModel(ioDispatcher = testDispatcher)
        val before = viewModel.luminaState.value.activeEffectCount
        viewModel.addEffect(com.lumina.engine.EffectParams())
        assert(viewModel.luminaState.value.activeEffectCount == before + 1)
    }
}
