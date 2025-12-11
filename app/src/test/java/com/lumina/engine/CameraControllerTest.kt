package com.lumina.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)

class CameraControllerTest {
    @Test
    fun hasFlashUnit_returnsFalse_beforeBindingCamera() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cc = CameraController(context)
        assertThat(cc.hasFlashUnit()).isFalse()
    }

    @Test
    fun setTorch_returnsFailure_beforeBindingCamera() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cc = CameraController(context)
        val result = cc.setTorch(true)
        assertThat(result.isFailure).isTrue()
    }
}
