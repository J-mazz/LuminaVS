package com.lumina.engine

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Additional tests for GlassmorphicParams and UI-related data
 */
class GlassmorphicParamsTest {

    @Test
    fun `GlassmorphicParams default colors are correct`() {
        val params = GlassmorphicParams()
        
        // Background should be semi-transparent white
        assertThat(params.backgroundColor.r).isEqualTo(1f)
        assertThat(params.backgroundColor.g).isEqualTo(1f)
        assertThat(params.backgroundColor.b).isEqualTo(1f)
        assertThat(params.backgroundColor.a).isEqualTo(0.1f)
        
        // Border should be semi-transparent white
        assertThat(params.borderColor.r).isEqualTo(1f)
        assertThat(params.borderColor.g).isEqualTo(1f)
        assertThat(params.borderColor.b).isEqualTo(1f)
        assertThat(params.borderColor.a).isEqualTo(0.2f)
    }

    @Test
    fun `GlassmorphicParams copy works correctly`() {
        val original = GlassmorphicParams()
        val modified = original.copy(
            blurRadius = 50f,
            transparency = 0.9f,
            borderWidth = 3f
        )
        
        assertThat(modified.blurRadius).isEqualTo(50f)
        assertThat(modified.transparency).isEqualTo(0.9f)
        assertThat(modified.borderWidth).isEqualTo(3f)
        // Unchanged values
        assertThat(modified.cornerRadius).isEqualTo(original.cornerRadius)
        assertThat(modified.saturation).isEqualTo(original.saturation)
    }

    @Test
    fun `GlassmorphicParams with extreme values`() {
        val params = GlassmorphicParams(
            blurRadius = 0f,
            transparency = 0f,
            borderWidth = 0f,
            cornerRadius = 0f,
            saturation = 0f,
            brightness = 0f
        )
        
        assertThat(params.blurRadius).isEqualTo(0f)
        assertThat(params.transparency).isEqualTo(0f)
        assertThat(params.borderWidth).isEqualTo(0f)
    }

    @Test
    fun `GlassmorphicParams with custom colors`() {
        val customBg = ColorRGBA(0.5f, 0.3f, 0.8f, 0.5f)
        val customBorder = ColorRGBA(1f, 0.5f, 0f, 0.8f)
        
        val params = GlassmorphicParams(
            backgroundColor = customBg,
            borderColor = customBorder
        )
        
        assertThat(params.backgroundColor).isEqualTo(customBg)
        assertThat(params.borderColor).isEqualTo(customBorder)
    }
}

/**
 * Tests for state transitions
 */
class StateTransitionTest {

    @Test
    fun `ProcessingState transitions are valid`() {
        val validTransitions = mapOf(
            ProcessingState.IDLE to listOf(ProcessingState.PROCESSING),
            ProcessingState.PROCESSING to listOf(ProcessingState.RENDERING, ProcessingState.ERROR, ProcessingState.IDLE),
            ProcessingState.RENDERING to listOf(ProcessingState.IDLE, ProcessingState.ERROR),
            ProcessingState.ERROR to listOf(ProcessingState.IDLE)
        )
        
        // Verify all states have defined transitions
        ProcessingState.entries.forEach { state ->
            assertThat(validTransitions.containsKey(state)).isTrue()
        }
    }

    @Test
    fun `LuminaState processing state changes`() {
        var state = LuminaState(processingState = ProcessingState.IDLE)
        
        // IDLE -> PROCESSING
        state = state.copy(processingState = ProcessingState.PROCESSING)
        assertThat(state.processingState).isEqualTo(ProcessingState.PROCESSING)
        
        // PROCESSING -> RENDERING
        state = state.copy(processingState = ProcessingState.RENDERING)
        assertThat(state.processingState).isEqualTo(ProcessingState.RENDERING)
        
        // RENDERING -> IDLE
        state = state.copy(processingState = ProcessingState.IDLE)
        assertThat(state.processingState).isEqualTo(ProcessingState.IDLE)
    }

    @Test
    fun `LuminaState touch state changes`() {
        var state = LuminaState(touchState = TouchState.NONE)
        
        // Touch down
        state = state.copy(
            touchState = TouchState.DOWN,
            touchPosition = Vec2(100f, 200f),
            touchPressure = 0.5f
        )
        assertThat(state.touchState).isEqualTo(TouchState.DOWN)
        assertThat(state.touchPosition).isEqualTo(Vec2(100f, 200f))
        
        // Touch move
        state = state.copy(
            touchState = TouchState.MOVE,
            touchPosition = Vec2(150f, 250f),
            touchDelta = Vec2(50f, 50f)
        )
        assertThat(state.touchState).isEqualTo(TouchState.MOVE)
        assertThat(state.touchDelta).isEqualTo(Vec2(50f, 50f))
        
        // Touch up
        state = state.copy(touchState = TouchState.UP)
        assertThat(state.touchState).isEqualTo(TouchState.UP)
    }
}

/**
 * Tests for effect combinations
 */
class EffectCombinationTest {

    @Test
    fun `multiple effects can be combined`() {
        val blur = EffectParams(type = EffectType.BLUR, intensity = 0.3f)
        val bloom = EffectParams(type = EffectType.BLOOM, intensity = 0.5f)
        val vignette = EffectParams(type = EffectType.VIGNETTE, intensity = 0.4f)
        val colorGrade = EffectParams(type = EffectType.COLOR_GRADE, intensity = 0.8f)
        
        val state = LuminaState(
            effects = listOf(blur, bloom, vignette, colorGrade),
            activeEffectCount = 4
        )
        
        assertThat(state.effects[0].type).isEqualTo(EffectType.BLUR)
        assertThat(state.effects[1].type).isEqualTo(EffectType.BLOOM)
        assertThat(state.effects[2].type).isEqualTo(EffectType.VIGNETTE)
        assertThat(state.effects[3].type).isEqualTo(EffectType.COLOR_GRADE)
        assertThat(state.activeEffectCount).isEqualTo(4)
    }

    @Test
    fun `effect order matters`() {
        val blurFirst = listOf(
            EffectParams(type = EffectType.BLUR, intensity = 0.5f),
            EffectParams(type = EffectType.SHARPEN, intensity = 0.5f),
            EffectParams(),
            EffectParams()
        )
        
        val sharpenFirst = listOf(
            EffectParams(type = EffectType.SHARPEN, intensity = 0.5f),
            EffectParams(type = EffectType.BLUR, intensity = 0.5f),
            EffectParams(),
            EffectParams()
        )
        
        val state1 = LuminaState(effects = blurFirst, activeEffectCount = 2)
        val state2 = LuminaState(effects = sharpenFirst, activeEffectCount = 2)
        
        // Verify order is preserved
        assertThat(state1.effects[0].type).isEqualTo(EffectType.BLUR)
        assertThat(state2.effects[0].type).isEqualTo(EffectType.SHARPEN)
    }

    @Test
    fun `effect parameters are independent`() {
        val effects = listOf(
            EffectParams(type = EffectType.BLUR, intensity = 0.2f, param1 = 10f),
            EffectParams(type = EffectType.BLUR, intensity = 0.8f, param1 = 50f),
            EffectParams(),
            EffectParams()
        )
        
        val state = LuminaState(effects = effects, activeEffectCount = 2)
        
        // Same effect type but different parameters
        assertThat(state.effects[0].intensity).isNotEqualTo(state.effects[1].intensity)
        assertThat(state.effects[0].param1).isNotEqualTo(state.effects[1].param1)
    }
}

/**
 * Tests for camera state
 */
class CameraStateTest {

    @Test
    fun `CameraState custom values work`() {
        val camera = CameraState(
            position = Vec3(10f, 20f, 30f),
            lookAt = Vec3(0f, 5f, 0f),
            fov = 90f,
            nearPlane = 0.01f,
            farPlane = 5000f
        )
        
        assertThat(camera.position).isEqualTo(Vec3(10f, 20f, 30f))
        assertThat(camera.lookAt).isEqualTo(Vec3(0f, 5f, 0f))
        assertThat(camera.fov).isEqualTo(90f)
        assertThat(camera.nearPlane).isEqualTo(0.01f)
        assertThat(camera.farPlane).isEqualTo(5000f)
    }

    @Test
    fun `CameraState copy preserves unchanged values`() {
        val original = CameraState()
        val modified = original.copy(fov = 45f)
        
        assertThat(modified.fov).isEqualTo(45f)
        assertThat(modified.position).isEqualTo(original.position)
        assertThat(modified.lookAt).isEqualTo(original.lookAt)
        assertThat(modified.nearPlane).isEqualTo(original.nearPlane)
        assertThat(modified.farPlane).isEqualTo(original.farPlane)
    }

    @Test
    fun `LuminaState with custom camera`() {
        val camera = CameraState(
            position = Vec3(0f, 10f, -20f),
            fov = 75f
        )
        
        val state = LuminaState(camera = camera)
        
        assertThat(state.camera.position).isEqualTo(Vec3(0f, 10f, -20f))
        assertThat(state.camera.fov).isEqualTo(75f)
    }
}

/**
 * Tests for aspect ratio calculations
 */
class AspectRatioTest {

    @Test
    fun `16x9 aspect ratio is correct`() {
        val state = LuminaState(width = 1920, height = 1080)
        assertThat(state.aspectRatio).isWithin(0.001f).of(16f / 9f)
    }

    @Test
    fun `4x3 aspect ratio is correct`() {
        val state = LuminaState(
            width = 1600, 
            height = 1200,
            aspectRatio = 1600f / 1200f
        )
        assertThat(state.aspectRatio).isWithin(0.001f).of(4f / 3f)
    }

    @Test
    fun `1x1 aspect ratio is correct`() {
        val state = LuminaState(
            width = 1080,
            height = 1080,
            aspectRatio = 1f
        )
        assertThat(state.aspectRatio).isEqualTo(1f)
    }

    @Test
    fun `portrait aspect ratio works`() {
        val state = LuminaState(
            width = 1080,
            height = 1920,
            aspectRatio = 1080f / 1920f
        )
        assertThat(state.aspectRatio).isWithin(0.001f).of(9f / 16f)
    }
}

/**
 * Tests for intent handling
 */
class AIIntentTest {

    @Test
    fun `AIIntent parameter parsing`() {
        val intent = AIIntent(
            action = "add_effect",
            target = "blur",
            parameters = """{"intensity": 0.5, "radius": 10}"""
        )
        
        // Parse parameters as JSON
        val params = com.google.gson.Gson().fromJson(
            intent.parameters, 
            Map::class.java
        )
        
        assertThat(params["intensity"]).isEqualTo(0.5)
        assertThat(params["radius"]).isEqualTo(10.0)
    }

    @Test
    fun `AIIntent with empty parameters`() {
        val intent = AIIntent(
            action = "reset",
            target = "",
            parameters = "{}"
        )
        
        assertThat(intent.parameters).isEqualTo("{}")
    }

    @Test
    fun `AIIntent confidence ranges`() {
        // Low confidence
        val low = AIIntent(confidence = 0.3f)
        assertThat(low.confidence).isLessThan(0.5f)
        
        // Medium confidence
        val medium = AIIntent(confidence = 0.6f)
        assertThat(medium.confidence).isAtLeast(0.5f)
        assertThat(medium.confidence).isLessThan(0.8f)
        
        // High confidence
        val high = AIIntent(confidence = 0.95f)
        assertThat(high.confidence).isAtLeast(0.8f)
    }
}
