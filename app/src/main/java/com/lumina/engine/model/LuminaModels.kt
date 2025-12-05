package com.lumina.engine

import com.google.gson.Gson

data class ColorRGBA(
	val r: Float = 0f,
	val g: Float = 0f,
	val b: Float = 0f,
	val a: Float = 1f
) {
	companion object {
		fun fromHex(hex: Long): ColorRGBA {
			return ColorRGBA(
				r = ((hex shr 24) and 0xFF) / 255f,
				g = ((hex shr 16) and 0xFF) / 255f,
				b = ((hex shr 8) and 0xFF) / 255f,
				a = (hex and 0xFF) / 255f
			)
		}

		val Transparent = ColorRGBA(0f, 0f, 0f, 0f)
		val White = ColorRGBA(1f, 1f, 1f, 1f)
		val Black = ColorRGBA(0f, 0f, 0f, 1f)
	}

	fun toComposeColor(): androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color(r, g, b, a)
}

data class Vec2(
	val x: Float = 0f,
	val y: Float = 0f
)

data class Vec3(
	val x: Float = 0f,
	val y: Float = 0f,
	val z: Float = 0f
)

enum class RenderMode(val value: Int) {
	PASSTHROUGH(0),
	STYLIZED(1),
	SEGMENTED(2),
	DEPTH_MAP(3),
	NORMAL_MAP(4)
}

enum class EffectType(val value: Int) {
	NONE(0),
	BLUR(1),
	BLOOM(2),
	COLOR_GRADE(3),
	VIGNETTE(4),
	CHROMATIC_ABERRATION(5),
	NOISE(6),
	SHARPEN(7)
}

enum class ProcessingState(val value: Int) {
	IDLE(0),
	PROCESSING(1),
	RENDERING(2),
	ERROR(3)
}

data class EffectParams(
	val type: EffectType = EffectType.NONE,
	val intensity: Float = 1f,
	val param1: Float = 0f,
	val param2: Float = 0f,
	val tintColor: ColorRGBA = ColorRGBA(),
	val center: Vec2 = Vec2(0.5f, 0.5f),
	val scale: Vec2 = Vec2(1f, 1f)
)

data class CameraState(
	val position: Vec3 = Vec3(0f, 0f, 5f),
	val lookAt: Vec3 = Vec3(0f, 0f, 0f),
	val fov: Float = 60f,
	val nearPlane: Float = 0.1f,
	val farPlane: Float = 1000f
)

data class GlassmorphicParams(
	val backgroundColor: ColorRGBA = ColorRGBA(1f, 1f, 1f, 0.1f),
	val borderColor: ColorRGBA = ColorRGBA(1f, 1f, 1f, 0.2f),
	val blurRadius: Float = 20f,
	val transparency: Float = 0.7f,
	val borderWidth: Float = 1f,
	val cornerRadius: Float = 16f,
	val saturation: Float = 1.2f,
	val brightness: Float = 1.1f
)

data class AIIntent(
	val action: String = "",
	val target: String = "",
	val parameters: String = "",
	val confidence: Float = 0f,
	val timestamp: Long = 0L
)

data class FrameTiming(
	val deltaTime: Float = 0f,
	val totalTime: Float = 0f,
	val frameCount: Long = 0L,
	val fps: Float = 0f,
	val gpuTime: Float = 0f,
	val cpuTime: Float = 0f
)

enum class TouchState(val value: Int) {
	NONE(0),
	DOWN(1),
	MOVE(2),
	UP(3)
}

data class LuminaState(
	val version: Int = 1,
	val stateId: Int = 0,
	val processingState: ProcessingState = ProcessingState.IDLE,
	val flags: Int = 0,
	val renderMode: RenderMode = RenderMode.PASSTHROUGH,
	val width: Int = 1920,
	val height: Int = 1080,
	val aspectRatio: Float = 16f / 9f,
	val camera: CameraState = CameraState(),
	val effects: List<EffectParams> = listOf(
		EffectParams(), EffectParams(), EffectParams(), EffectParams()
	),
	val activeEffectCount: Int = 0,
	val uiStyle: GlassmorphicParams = GlassmorphicParams(),
	val currentIntent: AIIntent = AIIntent(),
	val pendingIntent: AIIntent = AIIntent(),
	val timing: FrameTiming = FrameTiming(),
	val touchPosition: Vec2 = Vec2(),
	val touchDelta: Vec2 = Vec2(),
	val touchPressure: Float = 0f,
	val touchState: TouchState = TouchState.NONE
) {
	fun toJson(): String = gsonInstance.toJson(this)

	companion object {
		private val gsonInstance: Gson by lazy { Gson() }

		fun fromJson(json: String): LuminaState = gsonInstance.fromJson(json, LuminaState::class.java)
	}
}
