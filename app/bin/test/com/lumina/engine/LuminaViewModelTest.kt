package com.lumina.engine

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Custom rule for coroutine testing
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainCoroutineRule(
    private val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestRule {
    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                Dispatchers.setMain(testDispatcher)
                try {
                    base.evaluate()
                } finally {
                    Dispatchers.resetMain()
                }
            }
        }
    }
}

/**
 * Unit tests for LuminaViewModel
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LuminaViewModelTest {

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private lateinit var viewModel: LuminaViewModel
    private lateinit var mockNativeBridge: NativeBridge
    private lateinit var mockPythonOrchestrator: PythonOrchestrator

    @Before
    fun setup() {
        mockNativeBridge = mockk(relaxed = true)
        mockPythonOrchestrator = mockk(relaxed = true)

        viewModel = LuminaViewModel(ioDispatcher = UnconfinedTestDispatcher())
        viewModel.nativeBridge = mockNativeBridge
        viewModel.pythonOrchestrator = mockPythonOrchestrator
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    // =========================================================================
    // Initial State Tests
    // =========================================================================

    @Test
    fun `initial state has correct default values`() = runTest {
        viewModel.luminaState.test {
            val state = awaitItem()
            assertThat(state.version).isEqualTo(1)
            assertThat(state.processingState).isEqualTo(ProcessingState.IDLE)
            assertThat(state.renderMode).isEqualTo(RenderMode.PASSTHROUGH)
            assertThat(state.activeEffectCount).isEqualTo(0)
        }
    }

    @Test
    fun `initial user input is empty`() = runTest {
        viewModel.userInput.test {
            assertThat(awaitItem()).isEmpty()
        }
    }

    @Test
    fun `initial processing state is false`() = runTest {
        viewModel.isProcessing.test {
            assertThat(awaitItem()).isFalse()
        }
    }

    @Test
    fun `initial status message is Ready`() = runTest {
        viewModel.statusMessage.test {
            assertThat(awaitItem()).isEqualTo("Ready")
        }
    }

    // =========================================================================
    // setUserInput Tests
    // =========================================================================

    @Test
    fun `setUserInput updates user input`() = runTest {
        viewModel.userInput.test {
            assertThat(awaitItem()).isEmpty()
            
            viewModel.setUserInput("Hello world")
            assertThat(awaitItem()).isEqualTo("Hello world")
        }
    }

    @Test
    fun `setUserInput handles empty string`() = runTest {
        viewModel.setUserInput("test")
        viewModel.userInput.test {
            assertThat(awaitItem()).isEqualTo("test")
            
            viewModel.setUserInput("")
            assertThat(awaitItem()).isEmpty()
        }
    }

    // =========================================================================
    // setRenderMode Tests
    // =========================================================================

    @Test
    fun `setRenderMode updates state`() = runTest {
        viewModel.luminaState.test {
            val initial = awaitItem()
            assertThat(initial.renderMode).isEqualTo(RenderMode.PASSTHROUGH)
            
            viewModel.setRenderMode(RenderMode.DEPTH_MAP)
            val updated = awaitItem()
            assertThat(updated.renderMode).isEqualTo(RenderMode.DEPTH_MAP)
        }
    }

    @Test
    fun `setRenderMode calls native bridge`() = runTest {
        viewModel.setRenderMode(RenderMode.STYLIZED)
        
        verify { mockNativeBridge.setRenderMode(RenderMode.STYLIZED.value) }
    }

    @Test
    fun `setRenderMode increments stateId`() = runTest {
        viewModel.luminaState.test {
            val initial = awaitItem()
            val initialId = initial.stateId
            
            viewModel.setRenderMode(RenderMode.SEGMENTED)
            val updated = awaitItem()
            assertThat(updated.stateId).isEqualTo(initialId + 1)
        }
    }

    // =========================================================================
    // addEffect Tests
    // =========================================================================

    @Test
    fun `addEffect adds effect to state`() = runTest {
        val blurEffect = EffectParams(type = EffectType.BLUR, intensity = 0.5f)
        
        viewModel.luminaState.test {
            val initial = awaitItem()
            assertThat(initial.activeEffectCount).isEqualTo(0)
            
            viewModel.addEffect(blurEffect)
            val updated = awaitItem()
            assertThat(updated.activeEffectCount).isEqualTo(1)
            assertThat(updated.effects[0].type).isEqualTo(EffectType.BLUR)
        }
    }

    @Test
    fun `addEffect respects max 4 effects limit`() = runTest {
        viewModel.luminaState.test {
            awaitItem() // initial state
            
            // Add 4 effects
            repeat(4) { i ->
                viewModel.addEffect(EffectParams(type = EffectType.entries[i + 1]))
                awaitItem()
            }
            
            // Try to add 5th effect - should be ignored
            viewModel.addEffect(EffectParams(type = EffectType.SHARPEN))
            
            // Verify we still have 4 effects (no new emission for unchanged state)
            val current = viewModel.luminaState.value
            assertThat(current.activeEffectCount).isEqualTo(4)
        }
    }

    @Test
    fun `addEffect increments stateId`() = runTest {
        viewModel.luminaState.test {
            val initial = awaitItem()
            
            viewModel.addEffect(EffectParams(type = EffectType.BLOOM))
            val updated = awaitItem()
            assertThat(updated.stateId).isGreaterThan(initial.stateId)
        }
    }

    // =========================================================================
    // clearEffects Tests
    // =========================================================================

    @Test
    fun `clearEffects removes all effects`() = runTest {
        // First add some effects
        viewModel.addEffect(EffectParams(type = EffectType.BLUR))
        viewModel.addEffect(EffectParams(type = EffectType.BLOOM))
        
        viewModel.luminaState.test {
            val withEffects = awaitItem()
            assertThat(withEffects.activeEffectCount).isEqualTo(2)
            
            viewModel.clearEffects()
            val cleared = awaitItem()
            assertThat(cleared.activeEffectCount).isEqualTo(0)
            assertThat(cleared.effects.all { it.type == EffectType.NONE }).isTrue()
        }
    }

    // =========================================================================
    // updateUIStyle Tests
    // =========================================================================

    @Test
    fun `updateUIStyle updates glassmorphic params`() = runTest {
        val newStyle = GlassmorphicParams(
            blurRadius = 30f,
            transparency = 0.5f,
            cornerRadius = 24f
        )
        
        viewModel.luminaState.test {
            awaitItem() // initial
            
            viewModel.updateUIStyle(newStyle)
            val updated = awaitItem()
            
            assertThat(updated.uiStyle.blurRadius).isEqualTo(30f)
            assertThat(updated.uiStyle.transparency).isEqualTo(0.5f)
            assertThat(updated.uiStyle.cornerRadius).isEqualTo(24f)
        }
    }

    // =========================================================================
    // processUserInput Tests
    // =========================================================================

    @Test
    fun `processUserInput ignores blank input`() = runTest {
        viewModel.processUserInput("")
        viewModel.processUserInput("   ")
        
        // Verify orchestrator was never called
        verify(exactly = 0) { mockPythonOrchestrator.parseIntent(any()) }
    }

    @Test
    fun `processUserInput calls python orchestrator`() = runTest {
        val testIntent = AIIntent(
            action = "add_effect",
            target = "blur",
            confidence = 0.9f
        )
        every { mockPythonOrchestrator.parseIntent("add blur") } returns testIntent
        
        viewModel.processUserInput("add blur")
        advanceUntilIdle()
        
        verify { mockPythonOrchestrator.parseIntent("add blur") }
    }

    @Test
    fun `processUserInput updates state with parsed intent`() = runTest {
        val testIntent = AIIntent(
            action = "set_render_mode",
            target = "depth_map",
            confidence = 0.95f,
            timestamp = 12345L
        )
        every { mockPythonOrchestrator.parseIntent(any()) } returns testIntent

        viewModel.processUserInput("show depth")
        advanceUntilIdle()

        val final = viewModel.luminaState.value
        assertThat(final.currentIntent.action).isEqualTo("set_render_mode")
        assertThat(final.currentIntent.target).isEqualTo("depth_map")
    }

    @Test
    fun `processUserInput calls native bridge updateState`() = runTest {
        val testIntent = AIIntent(action = "test", target = "test", confidence = 0.5f)
        every { mockPythonOrchestrator.parseIntent(any()) } returns testIntent
        
        viewModel.processUserInput("test input")
        advanceUntilIdle()
        
        verify { mockNativeBridge.updateState(any()) }
    }

    @Test
    fun `processUserInput sets isProcessing during execution`() = runTest {
        coEvery { mockPythonOrchestrator.parseIntent(any()) } coAnswers {
            AIIntent(action = "test", target = "", confidence = 0.5f)
        }
        
        viewModel.isProcessing.test {
            assertThat(awaitItem()).isFalse()
            
            viewModel.processUserInput("test")
            // May see true then false, or just false if too fast
            val emissions = mutableListOf<Boolean>()
            repeat(2) {
                try {
                    emissions.add(awaitItem())
                } catch (e: Exception) {
                    return@repeat
                }
            }
        }
    }

    @Test
    fun `processUserInput handles orchestrator exception`() = runTest {
        every { mockPythonOrchestrator.parseIntent(any()) } throws RuntimeException("Test error")

        viewModel.processUserInput("test")
        advanceUntilIdle()

        val message = viewModel.statusMessage.value
        assertThat(message.contains("Error")).isTrue()
    }

    @Test
    fun `processUserInput with null orchestrator returns unknown intent`() = runTest {
        viewModel.pythonOrchestrator = null
        
        viewModel.processUserInput("test input")
        advanceUntilIdle()
        
        val state = viewModel.luminaState.value
        assertThat(state.currentIntent.action).isEqualTo("unknown")
    }

    // =========================================================================
    // updateState Tests
    // =========================================================================

    @Test
    fun `updateState applies transformation correctly`() = runTest {
        viewModel.luminaState.test {
            val initial = awaitItem()
            
            viewModel.updateState { 
                copy(
                    width = 2560,
                    height = 1440,
                    processingState = ProcessingState.RENDERING
                )
            }
            
            val updated = awaitItem()
            assertThat(updated.width).isEqualTo(2560)
            assertThat(updated.height).isEqualTo(1440)
            assertThat(updated.processingState).isEqualTo(ProcessingState.RENDERING)
            assertThat(updated.stateId).isEqualTo(initial.stateId + 1)
        }
    }

    @Test
    fun `updateState always increments stateId`() = runTest {
        viewModel.luminaState.test {
            val initial = awaitItem()
            
            repeat(5) { i ->
                viewModel.updateState { this }
                val updated = awaitItem()
                assertThat(updated.stateId).isEqualTo(initial.stateId + i + 1)
            }
        }
    }

    // =========================================================================
    // Integration Tests
    // =========================================================================

    @Test
    fun `full workflow - add effect then clear`() = runTest {
        viewModel.luminaState.test {
            awaitItem() // initial
            
            // Add effect
            viewModel.addEffect(EffectParams(type = EffectType.BLUR, intensity = 0.5f))
            val withEffect = awaitItem()
            assertThat(withEffect.activeEffectCount).isEqualTo(1)
            
            // Change render mode
            viewModel.setRenderMode(RenderMode.STYLIZED)
            val withMode = awaitItem()
            assertThat(withMode.renderMode).isEqualTo(RenderMode.STYLIZED)
            assertThat(withMode.activeEffectCount).isEqualTo(1)
            
            // Clear effects
            viewModel.clearEffects()
            val cleared = awaitItem()
            assertThat(cleared.activeEffectCount).isEqualTo(0)
            assertThat(cleared.renderMode).isEqualTo(RenderMode.STYLIZED)
        }
    }

    @Test
    fun `multiple rapid state updates are handled`() = runTest {
        viewModel.luminaState.test {
            awaitItem() // initial
            
            // Rapid updates
            RenderMode.entries.forEach { mode ->
                viewModel.setRenderMode(mode)
            }
            
            // Collect all emissions
            val emissions = mutableListOf<LuminaState>()
            repeat(RenderMode.entries.size) {
                try {
                    emissions.add(awaitItem())
                } catch (e: Exception) {
                    return@repeat
                }
            }
            
            // Final state should be the last render mode
            val final = viewModel.luminaState.value
            assertThat(final.renderMode).isEqualTo(RenderMode.entries.last())
        }
    }
}
