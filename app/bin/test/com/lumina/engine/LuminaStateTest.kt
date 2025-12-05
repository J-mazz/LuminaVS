package com.lumina.engine

import com.google.common.truth.Truth.assertThat
import com.google.gson.Gson
import org.junit.Test

/**
 * Unit tests for Lumina data classes
 */
class LuminaStateTest {

    private val gson = Gson()

    // =========================================================================
    // ColorRGBA Tests
    // =========================================================================

    @Test
    fun `ColorRGBA default constructor creates transparent black`() {
        val color = ColorRGBA()
        assertThat(color.r).isEqualTo(0f)
        assertThat(color.g).isEqualTo(0f)
        assertThat(color.b).isEqualTo(0f)
        assertThat(color.a).isEqualTo(1f)
    }

    @Test
    fun `ColorRGBA fromHex parses correctly`() {
        // 0xFF0000FF = Red with full alpha
        val red = ColorRGBA.fromHex(0xFF0000FFL)
        assertThat(red.r).isEqualTo(1f)
        assertThat(red.g).isEqualTo(0f)
        assertThat(red.b).isEqualTo(0f)
        assertThat(red.a).isEqualTo(1f)

        // 0x00FF00FF = Green with full alpha
        val green = ColorRGBA.fromHex(0x00FF00FFL)
        assertThat(green.r).isEqualTo(0f)
        assertThat(green.g).isEqualTo(1f)
        assertThat(green.b).isEqualTo(0f)
        assertThat(green.a).isEqualTo(1f)

        // 0x0000FFFF = Blue with full alpha
        val blue = ColorRGBA.fromHex(0x0000FFFFL)
        assertThat(blue.r).isEqualTo(0f)
        assertThat(blue.g).isEqualTo(0f)
        assertThat(blue.b).isEqualTo(1f)
        assertThat(blue.a).isEqualTo(1f)
    }

    @Test
    fun `ColorRGBA fromHex handles alpha channel`() {
        // 0x80808080 = Gray with 50% alpha
        val semiTransparent = ColorRGBA.fromHex(0x80808080L)
        assertThat(semiTransparent.r).isWithin(0.01f).of(0.5f)
        assertThat(semiTransparent.g).isWithin(0.01f).of(0.5f)
        assertThat(semiTransparent.b).isWithin(0.01f).of(0.5f)
        assertThat(semiTransparent.a).isWithin(0.01f).of(0.5f)
    }

    @Test
    fun `ColorRGBA companion constants are correct`() {
        assertThat(ColorRGBA.Transparent).isEqualTo(ColorRGBA(0f, 0f, 0f, 0f))
        assertThat(ColorRGBA.White).isEqualTo(ColorRGBA(1f, 1f, 1f, 1f))
        assertThat(ColorRGBA.Black).isEqualTo(ColorRGBA(0f, 0f, 0f, 1f))
    }

    @Test
    fun `ColorRGBA toComposeColor converts correctly`() {
        val color = ColorRGBA(0.5f, 0.25f, 0.75f, 1f)
        val composeColor = color.toComposeColor()
        
        assertThat(composeColor.red).isWithin(0.01f).of(0.5f)
        assertThat(composeColor.green).isWithin(0.01f).of(0.25f)
        assertThat(composeColor.blue).isWithin(0.01f).of(0.75f)
        assertThat(composeColor.alpha).isEqualTo(1f)
    }

    // =========================================================================
    // Vec2 Tests
    // =========================================================================

    @Test
    fun `Vec2 default constructor creates zero vector`() {
        val vec = Vec2()
        assertThat(vec.x).isEqualTo(0f)
        assertThat(vec.y).isEqualTo(0f)
    }

    @Test
    fun `Vec2 custom constructor sets values`() {
        val vec = Vec2(3.5f, -2.1f)
        assertThat(vec.x).isEqualTo(3.5f)
        assertThat(vec.y).isEqualTo(-2.1f)
    }

    // =========================================================================
    // Vec3 Tests
    // =========================================================================

    @Test
    fun `Vec3 default constructor creates zero vector`() {
        val vec = Vec3()
        assertThat(vec.x).isEqualTo(0f)
        assertThat(vec.y).isEqualTo(0f)
        assertThat(vec.z).isEqualTo(0f)
    }

    @Test
    fun `Vec3 custom constructor sets values`() {
        val vec = Vec3(1f, 2f, 3f)
        assertThat(vec.x).isEqualTo(1f)
        assertThat(vec.y).isEqualTo(2f)
        assertThat(vec.z).isEqualTo(3f)
    }

    // =========================================================================
    // Enum Tests
    // =========================================================================

    @Test
    fun `RenderMode enum values are correct`() {
        assertThat(RenderMode.PASSTHROUGH.value).isEqualTo(0)
        assertThat(RenderMode.STYLIZED.value).isEqualTo(1)
        assertThat(RenderMode.SEGMENTED.value).isEqualTo(2)
        assertThat(RenderMode.DEPTH_MAP.value).isEqualTo(3)
        assertThat(RenderMode.NORMAL_MAP.value).isEqualTo(4)
    }

    @Test
    fun `EffectType enum values are correct`() {
        assertThat(EffectType.NONE.value).isEqualTo(0)
        assertThat(EffectType.BLUR.value).isEqualTo(1)
        assertThat(EffectType.BLOOM.value).isEqualTo(2)
        assertThat(EffectType.COLOR_GRADE.value).isEqualTo(3)
        assertThat(EffectType.VIGNETTE.value).isEqualTo(4)
        assertThat(EffectType.CHROMATIC_ABERRATION.value).isEqualTo(5)
        assertThat(EffectType.NOISE.value).isEqualTo(6)
        assertThat(EffectType.SHARPEN.value).isEqualTo(7)
    }

    @Test
    fun `ProcessingState enum values are correct`() {
        assertThat(ProcessingState.IDLE.value).isEqualTo(0)
        assertThat(ProcessingState.PROCESSING.value).isEqualTo(1)
        assertThat(ProcessingState.RENDERING.value).isEqualTo(2)
        assertThat(ProcessingState.ERROR.value).isEqualTo(3)
    }

    @Test
    fun `TouchState enum values are correct`() {
        assertThat(TouchState.NONE.value).isEqualTo(0)
        assertThat(TouchState.DOWN.value).isEqualTo(1)
        assertThat(TouchState.MOVE.value).isEqualTo(2)
        assertThat(TouchState.UP.value).isEqualTo(3)
    }

    // =========================================================================
    // EffectParams Tests
    // =========================================================================

    @Test
    fun `EffectParams default constructor has correct values`() {
        val params = EffectParams()
        assertThat(params.type).isEqualTo(EffectType.NONE)
        assertThat(params.intensity).isEqualTo(1f)
        assertThat(params.param1).isEqualTo(0f)
        assertThat(params.param2).isEqualTo(0f)
        assertThat(params.center).isEqualTo(Vec2(0.5f, 0.5f))
        assertThat(params.scale).isEqualTo(Vec2(1f, 1f))
    }

    @Test
    fun `EffectParams custom constructor sets values`() {
        val tint = ColorRGBA(1f, 0f, 0f, 1f)
        val params = EffectParams(
            type = EffectType.BLOOM,
            intensity = 0.8f,
            param1 = 0.5f,
            param2 = 0.3f,
            tintColor = tint,
            center = Vec2(0.25f, 0.75f),
            scale = Vec2(2f, 2f)
        )
        
        assertThat(params.type).isEqualTo(EffectType.BLOOM)
        assertThat(params.intensity).isEqualTo(0.8f)
        assertThat(params.param1).isEqualTo(0.5f)
        assertThat(params.param2).isEqualTo(0.3f)
        assertThat(params.tintColor).isEqualTo(tint)
        assertThat(params.center).isEqualTo(Vec2(0.25f, 0.75f))
        assertThat(params.scale).isEqualTo(Vec2(2f, 2f))
    }

    // =========================================================================
    // CameraState Tests
    // =========================================================================

    @Test
    fun `CameraState default constructor has correct values`() {
        val camera = CameraState()
        assertThat(camera.position).isEqualTo(Vec3(0f, 0f, 5f))
        assertThat(camera.lookAt).isEqualTo(Vec3(0f, 0f, 0f))
        assertThat(camera.fov).isEqualTo(60f)
        assertThat(camera.nearPlane).isEqualTo(0.1f)
        assertThat(camera.farPlane).isEqualTo(1000f)
    }

    // =========================================================================
    // GlassmorphicParams Tests
    // =========================================================================

    @Test
    fun `GlassmorphicParams default constructor has correct values`() {
        val params = GlassmorphicParams()
        assertThat(params.blurRadius).isEqualTo(20f)
        assertThat(params.transparency).isEqualTo(0.7f)
        assertThat(params.borderWidth).isEqualTo(1f)
        assertThat(params.cornerRadius).isEqualTo(16f)
        assertThat(params.saturation).isEqualTo(1.2f)
        assertThat(params.brightness).isEqualTo(1.1f)
    }

    @Test
    fun `GlassmorphicParams custom values work`() {
        val params = GlassmorphicParams(
            blurRadius = 30f,
            transparency = 0.5f,
            borderWidth = 2f,
            cornerRadius = 24f
        )
        assertThat(params.blurRadius).isEqualTo(30f)
        assertThat(params.transparency).isEqualTo(0.5f)
        assertThat(params.borderWidth).isEqualTo(2f)
        assertThat(params.cornerRadius).isEqualTo(24f)
    }

    // =========================================================================
    // AIIntent Tests
    // =========================================================================

    @Test
    fun `AIIntent default constructor creates empty intent`() {
        val intent = AIIntent()
        assertThat(intent.action).isEmpty()
        assertThat(intent.target).isEmpty()
        assertThat(intent.parameters).isEmpty()
        assertThat(intent.confidence).isEqualTo(0f)
        assertThat(intent.timestamp).isEqualTo(0L)
    }

    @Test
    fun `AIIntent custom constructor sets values`() {
        val intent = AIIntent(
            action = "add_effect",
            target = "blur",
            parameters = """{"intensity": 0.5}""",
            confidence = 0.95f,
            timestamp = 1234567890L
        )
        
        assertThat(intent.action).isEqualTo("add_effect")
        assertThat(intent.target).isEqualTo("blur")
        assertThat(intent.parameters).isEqualTo("""{"intensity": 0.5}""")
        assertThat(intent.confidence).isEqualTo(0.95f)
        assertThat(intent.timestamp).isEqualTo(1234567890L)
    }

    // =========================================================================
    // FrameTiming Tests
    // =========================================================================

    @Test
    fun `FrameTiming default constructor creates zero timing`() {
        val timing = FrameTiming()
        assertThat(timing.deltaTime).isEqualTo(0f)
        assertThat(timing.totalTime).isEqualTo(0f)
        assertThat(timing.frameCount).isEqualTo(0L)
        assertThat(timing.fps).isEqualTo(0f)
        assertThat(timing.gpuTime).isEqualTo(0f)
        assertThat(timing.cpuTime).isEqualTo(0f)
    }

    @Test
    fun `FrameTiming custom values work`() {
        val timing = FrameTiming(
            deltaTime = 0.016f,
            totalTime = 10.5f,
            frameCount = 630L,
            fps = 60f,
            gpuTime = 0.008f,
            cpuTime = 0.005f
        )
        
        assertThat(timing.deltaTime).isEqualTo(0.016f)
        assertThat(timing.totalTime).isEqualTo(10.5f)
        assertThat(timing.frameCount).isEqualTo(630L)
        assertThat(timing.fps).isEqualTo(60f)
        assertThat(timing.gpuTime).isEqualTo(0.008f)
        assertThat(timing.cpuTime).isEqualTo(0.005f)
    }

    // =========================================================================
    // LuminaState Tests
    // =========================================================================

    @Test
    fun `LuminaState default constructor has correct values`() {
        val state = LuminaState()
        
        assertThat(state.version).isEqualTo(1)
        assertThat(state.stateId).isEqualTo(0)
        assertThat(state.processingState).isEqualTo(ProcessingState.IDLE)
        assertThat(state.flags).isEqualTo(0)
        assertThat(state.renderMode).isEqualTo(RenderMode.PASSTHROUGH)
        assertThat(state.width).isEqualTo(1920)
        assertThat(state.height).isEqualTo(1080)
        assertThat(state.aspectRatio).isWithin(0.01f).of(16f / 9f)
        assertThat(state.effects).hasSize(4)
        assertThat(state.activeEffectCount).isEqualTo(0)
        assertThat(state.touchState).isEqualTo(TouchState.NONE)
    }

    @Test
    fun `LuminaState toJson produces valid JSON`() {
        val state = LuminaState(
            version = 1,
            stateId = 42,
            renderMode = RenderMode.STYLIZED,
            width = 1280,
            height = 720
        )
        
        val json = state.toJson()
        
        assertThat(json).contains("\"version\":1")
        assertThat(json).contains("\"stateId\":42")
        assertThat(json).contains("\"width\":1280")
        assertThat(json).contains("\"height\":720")
    }

    @Test
    fun `LuminaState fromJson parses correctly`() {
        val original = LuminaState(
            version = 1,
            stateId = 100,
            renderMode = RenderMode.DEPTH_MAP,
            width = 800,
            height = 600
        )
        
        val json = original.toJson()
        val parsed = LuminaState.fromJson(json)
        
        assertThat(parsed.version).isEqualTo(original.version)
        assertThat(parsed.stateId).isEqualTo(original.stateId)
        assertThat(parsed.width).isEqualTo(original.width)
        assertThat(parsed.height).isEqualTo(original.height)
    }

    @Test
    fun `LuminaState copy function works`() {
        val original = LuminaState()
        val modified = original.copy(
            stateId = 5,
            renderMode = RenderMode.SEGMENTED,
            activeEffectCount = 2
        )
        
        assertThat(modified.stateId).isEqualTo(5)
        assertThat(modified.renderMode).isEqualTo(RenderMode.SEGMENTED)
        assertThat(modified.activeEffectCount).isEqualTo(2)
        // Unchanged values
        assertThat(modified.version).isEqualTo(original.version)
        assertThat(modified.width).isEqualTo(original.width)
    }

    @Test
    fun `LuminaState effects list is mutable via copy`() {
        val state = LuminaState()
        val blurEffect = EffectParams(type = EffectType.BLUR, intensity = 0.5f)
        
        val newEffects = state.effects.toMutableList()
        newEffects[0] = blurEffect
        
        val newState = state.copy(effects = newEffects, activeEffectCount = 1)
        
        assertThat(newState.effects[0].type).isEqualTo(EffectType.BLUR)
        assertThat(newState.activeEffectCount).isEqualTo(1)
    }

    // =========================================================================
    // JSON Serialization Round-trip Tests
    // =========================================================================

    @Test
    fun `ColorRGBA serializes and deserializes correctly`() {
        val original = ColorRGBA(0.2f, 0.4f, 0.6f, 0.8f)
        val json = gson.toJson(original)
        val parsed = gson.fromJson(json, ColorRGBA::class.java)
        
        assertThat(parsed).isEqualTo(original)
    }

    @Test
    fun `EffectParams serializes and deserializes correctly`() {
        val original = EffectParams(
            type = EffectType.VIGNETTE,
            intensity = 0.6f,
            center = Vec2(0.5f, 0.5f)
        )
        val json = gson.toJson(original)
        val parsed = gson.fromJson(json, EffectParams::class.java)
        
        assertThat(parsed.type).isEqualTo(original.type)
        assertThat(parsed.intensity).isEqualTo(original.intensity)
        assertThat(parsed.center).isEqualTo(original.center)
    }

    @Test
    fun `AIIntent serializes and deserializes correctly`() {
        val original = AIIntent(
            action = "set_render_mode",
            target = "depth_map",
            parameters = "{}",
            confidence = 0.9f,
            timestamp = System.currentTimeMillis()
        )
        val json = gson.toJson(original)
        val parsed = gson.fromJson(json, AIIntent::class.java)
        
        assertThat(parsed.action).isEqualTo(original.action)
        assertThat(parsed.target).isEqualTo(original.target)
        assertThat(parsed.confidence).isEqualTo(original.confidence)
    }
}
