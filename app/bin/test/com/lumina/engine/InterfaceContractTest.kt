package com.lumina.engine

import com.google.common.truth.Truth.assertThat
import com.google.gson.Gson
import io.mockk.*
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Tests for NativeBridge interface contract
 */
class NativeBridgeContractTest {

    private lateinit var mockBridge: NativeBridge

    @Before
    fun setup() {
        mockBridge = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `NativeBridge initialize returns boolean`() {
        every { mockBridge.initialize() } returns true
        assertThat(mockBridge.initialize()).isTrue()

        every { mockBridge.initialize() } returns false
        assertThat(mockBridge.initialize()).isFalse()
    }

    @Test
    fun `NativeBridge updateState accepts JSON string`() {
        val state = LuminaState()
        val json = state.toJson()
        
        mockBridge.updateState(json)
        
        verify { mockBridge.updateState(json) }
    }

    @Test
    fun `NativeBridge setRenderMode accepts all modes`() {
        RenderMode.entries.forEach { mode ->
            mockBridge.setRenderMode(mode.value)
            verify { mockBridge.setRenderMode(mode.value) }
        }
    }

    @Test
    fun `NativeBridge getFrameTiming returns FrameTiming`() {
        val expected = FrameTiming(fps = 60f, deltaTime = 0.016f)
        every { mockBridge.getFrameTiming() } returns expected
        
        val result = mockBridge.getFrameTiming()
        
        assertThat(result.fps).isEqualTo(60f)
        assertThat(result.deltaTime).isEqualTo(0.016f)
    }

    @Test
    fun `NativeBridge shutdown is callable`() {
        mockBridge.shutdown()
        verify { mockBridge.shutdown() }
    }
}

/**
 * Tests for PythonOrchestrator interface contract
 */
class PythonOrchestratorContractTest {

    private lateinit var mockOrchestrator: PythonOrchestrator

    @Before
    fun setup() {
        mockOrchestrator = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `PythonOrchestrator initialize returns boolean`() {
        every { mockOrchestrator.initialize(any()) } returns true
        assertThat(mockOrchestrator.initialize("/test/path")).isTrue()
    }

    @Test
    fun `PythonOrchestrator parseIntent returns AIIntent`() {
        val expectedIntent = AIIntent(
            action = "add_effect",
            target = "blur",
            confidence = 0.9f
        )
        every { mockOrchestrator.parseIntent("add blur") } returns expectedIntent
        
        val result = mockOrchestrator.parseIntent("add blur")
        
        assertThat(result.action).isEqualTo("add_effect")
        assertThat(result.target).isEqualTo("blur")
    }

    @Test
    fun `PythonOrchestrator handles various inputs`() {
        val testCases = listOf(
            "add blur" to AIIntent(action = "add_effect", target = "blur"),
            "show depth" to AIIntent(action = "set_render_mode", target = "depth_map"),
            "reset" to AIIntent(action = "reset", target = ""),
            "" to AIIntent(action = "unknown", target = "")
        )
        
        testCases.forEach { (input, expected) ->
            every { mockOrchestrator.parseIntent(input) } returns expected
            val result = mockOrchestrator.parseIntent(input)
            assertThat(result.action).isEqualTo(expected.action)
        }
    }

    @Test
    fun `PythonOrchestrator shutdown is callable`() {
        mockOrchestrator.shutdown()
        verify { mockOrchestrator.shutdown() }
    }
}

/**
 * JSON serialization edge case tests
 */
class JsonSerializationTest {

    private val gson = Gson()

    @Test
    fun `LuminaState serializes with all fields`() {
        val state = LuminaState(
            version = 2,
            stateId = 100,
            processingState = ProcessingState.RENDERING,
            flags = 255,
            renderMode = RenderMode.STYLIZED,
            width = 2560,
            height = 1440
        )
        
        val json = state.toJson()
        
        assertThat(json).contains("\"version\":2")
        assertThat(json).contains("\"stateId\":100")
        assertThat(json).contains("\"flags\":255")
        assertThat(json).contains("\"width\":2560")
        assertThat(json).contains("\"height\":1440")
    }

    @Test
    fun `nested objects serialize correctly`() {
        val state = LuminaState(
            camera = CameraState(
                position = Vec3(1f, 2f, 3f),
                fov = 90f
            ),
            uiStyle = GlassmorphicParams(
                blurRadius = 25f,
                transparency = 0.8f
            )
        )
        
        val json = state.toJson()
        val parsed = LuminaState.fromJson(json)
        
        assertThat(parsed.camera.position).isEqualTo(Vec3(1f, 2f, 3f))
        assertThat(parsed.camera.fov).isEqualTo(90f)
        assertThat(parsed.uiStyle.blurRadius).isEqualTo(25f)
        assertThat(parsed.uiStyle.transparency).isEqualTo(0.8f)
    }

    @Test
    fun `effects array serializes correctly`() {
        val effects = listOf(
            EffectParams(type = EffectType.BLUR, intensity = 0.5f),
            EffectParams(type = EffectType.BLOOM, intensity = 0.3f),
            EffectParams(),
            EffectParams()
        )
        
        val state = LuminaState(effects = effects, activeEffectCount = 2)
        val json = state.toJson()
        val parsed = LuminaState.fromJson(json)
        
        assertThat(parsed.effects).hasSize(4)
        assertThat(parsed.activeEffectCount).isEqualTo(2)
    }

    @Test
    fun `ColorRGBA edge values serialize correctly`() {
        val testColors = listOf(
            ColorRGBA(0f, 0f, 0f, 0f),
            ColorRGBA(1f, 1f, 1f, 1f),
            ColorRGBA(0.5f, 0.5f, 0.5f, 0.5f),
            ColorRGBA(-0.1f, 1.1f, 0f, 1f) // Out of range (should still serialize)
        )
        
        testColors.forEach { color ->
            val json = gson.toJson(color)
            val parsed = gson.fromJson(json, ColorRGBA::class.java)
            assertThat(parsed).isEqualTo(color)
        }
    }

    @Test
    fun `Vec2 and Vec3 edge values serialize correctly`() {
        val vec2Cases = listOf(
            Vec2(0f, 0f),
            Vec2(Float.MAX_VALUE, Float.MIN_VALUE),
            Vec2(-1000f, 1000f)
        )
        
        vec2Cases.forEach { vec ->
            val json = gson.toJson(vec)
            val parsed = gson.fromJson(json, Vec2::class.java)
            assertThat(parsed).isEqualTo(vec)
        }
        
        val vec3Cases = listOf(
            Vec3(0f, 0f, 0f),
            Vec3(1f, -1f, 0.5f)
        )
        
        vec3Cases.forEach { vec ->
            val json = gson.toJson(vec)
            val parsed = gson.fromJson(json, Vec3::class.java)
            assertThat(parsed).isEqualTo(vec)
        }
    }
}

/**
 * Enum coverage tests
 */
class EnumCoverageTest {

    @Test
    fun `all RenderMode values are accessible`() {
        val modes = RenderMode.entries
        assertThat(modes).hasSize(5)
        
        modes.forEachIndexed { index, mode ->
            assertThat(mode.value).isEqualTo(index)
        }
    }

    @Test
    fun `all EffectType values are accessible`() {
        val effects = EffectType.entries
        assertThat(effects).hasSize(8)
        
        effects.forEachIndexed { index, effect ->
            assertThat(effect.value).isEqualTo(index)
        }
    }

    @Test
    fun `all ProcessingState values are accessible`() {
        val states = ProcessingState.entries
        assertThat(states).hasSize(4)
    }

    @Test
    fun `all TouchState values are accessible`() {
        val states = TouchState.entries
        assertThat(states).hasSize(4)
    }

    @Test
    fun `RenderMode can be looked up by value`() {
        val modeByValue = RenderMode.entries.associateBy { it.value }
        
        assertThat(modeByValue[0]).isEqualTo(RenderMode.PASSTHROUGH)
        assertThat(modeByValue[1]).isEqualTo(RenderMode.STYLIZED)
        assertThat(modeByValue[2]).isEqualTo(RenderMode.SEGMENTED)
        assertThat(modeByValue[3]).isEqualTo(RenderMode.DEPTH_MAP)
        assertThat(modeByValue[4]).isEqualTo(RenderMode.NORMAL_MAP)
    }

    @Test
    fun `EffectType can be looked up by value`() {
        val effectByValue = EffectType.entries.associateBy { it.value }
        
        assertThat(effectByValue[0]).isEqualTo(EffectType.NONE)
        assertThat(effectByValue[1]).isEqualTo(EffectType.BLUR)
        assertThat(effectByValue[2]).isEqualTo(EffectType.BLOOM)
        assertThat(effectByValue[3]).isEqualTo(EffectType.COLOR_GRADE)
        assertThat(effectByValue[4]).isEqualTo(EffectType.VIGNETTE)
        assertThat(effectByValue[5]).isEqualTo(EffectType.CHROMATIC_ABERRATION)
        assertThat(effectByValue[6]).isEqualTo(EffectType.NOISE)
        assertThat(effectByValue[7]).isEqualTo(EffectType.SHARPEN)
    }
}

/**
 * Data class equality tests
 */
class DataClassEqualityTest {

    @Test
    fun `ColorRGBA equality works`() {
        val a = ColorRGBA(0.5f, 0.5f, 0.5f, 1f)
        val b = ColorRGBA(0.5f, 0.5f, 0.5f, 1f)
        val c = ColorRGBA(0.5f, 0.5f, 0.5f, 0.9f)
        
        assertThat(a).isEqualTo(b)
        assertThat(a).isNotEqualTo(c)
        assertThat(a.hashCode()).isEqualTo(b.hashCode())
    }

    @Test
    fun `Vec2 equality works`() {
        val a = Vec2(1f, 2f)
        val b = Vec2(1f, 2f)
        val c = Vec2(1f, 3f)
        
        assertThat(a).isEqualTo(b)
        assertThat(a).isNotEqualTo(c)
    }

    @Test
    fun `Vec3 equality works`() {
        val a = Vec3(1f, 2f, 3f)
        val b = Vec3(1f, 2f, 3f)
        val c = Vec3(1f, 2f, 4f)
        
        assertThat(a).isEqualTo(b)
        assertThat(a).isNotEqualTo(c)
    }

    @Test
    fun `EffectParams equality works`() {
        val a = EffectParams(type = EffectType.BLUR, intensity = 0.5f)
        val b = EffectParams(type = EffectType.BLUR, intensity = 0.5f)
        val c = EffectParams(type = EffectType.BLUR, intensity = 0.6f)
        
        assertThat(a).isEqualTo(b)
        assertThat(a).isNotEqualTo(c)
    }

    @Test
    fun `AIIntent equality works`() {
        val a = AIIntent(action = "test", target = "blur", confidence = 0.9f)
        val b = AIIntent(action = "test", target = "blur", confidence = 0.9f)
        val c = AIIntent(action = "test", target = "bloom", confidence = 0.9f)
        
        assertThat(a).isEqualTo(b)
        assertThat(a).isNotEqualTo(c)
    }

    @Test
    fun `LuminaState equality includes all fields`() {
        val a = LuminaState(stateId = 1, width = 1920)
        val b = LuminaState(stateId = 1, width = 1920)
        val c = LuminaState(stateId = 2, width = 1920)
        
        assertThat(a).isEqualTo(b)
        assertThat(a).isNotEqualTo(c)
    }
}
