package com.lumina.engine

import android.util.Log
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.google.gson.Gson

/**
 * Python Bridge - Kotlin wrapper for Chaquopy Python orchestrator
 */
class PythonBridge : PythonOrchestrator {

    companion object {
        private const val TAG = "PythonBridge"
    }

    private var orchestratorModule: PyObject? = null
    private var isInitialized = false
    private val gson = Gson()

    override fun initialize(assetsPath: String): Boolean {
        if (isInitialized) {
            Log.w(TAG, "Python orchestrator already initialized")
            return true
        }

        return try {
            val py = Python.getInstance()
            orchestratorModule = py.getModule("orchestrator")
            
            // Initialize the orchestrator with assets path
            val result = orchestratorModule?.callAttr("initialize", assetsPath)
            isInitialized = result?.toBoolean() ?: false
            
            if (isInitialized) {
                Log.i(TAG, "Python orchestrator initialized")
            } else {
                Log.e(TAG, "Failed to initialize Python orchestrator")
            }
            
            isInitialized
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Python: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    override fun parseIntent(userInput: String): AIIntent {
        if (!isInitialized) {
            Log.w(TAG, "Orchestrator not initialized, initializing now...")
            val filesDir = LuminaApplication.instance.filesDir.absolutePath
            initialize(filesDir)
        }

        return try {
            val result = orchestratorModule?.callAttr("parse_intent", userInput)
            val json = result?.toString() ?: "{}"
            
            Log.d(TAG, "Intent result: $json")
            
            gson.fromJson(json, AIIntent::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing intent: ${e.message}")
            AIIntent(
                action = "error",
                target = userInput,
                parameters = """{"error": "${e.message}"}""",
                confidence = 0f,
                timestamp = System.currentTimeMillis()
            )
        }
    }

    override fun shutdown() {
        if (!isInitialized) return

        try {
            orchestratorModule?.callAttr("shutdown")
            Log.i(TAG, "Python orchestrator shut down")
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down Python: ${e.message}")
        }

        orchestratorModule = null
        isInitialized = false
    }

    /**
     * Get the intent history from the orchestrator
     */
    fun getHistory(): List<AIIntent> {
        if (!isInitialized) return emptyList()

        return try {
            val result = orchestratorModule?.callAttr("get_history")
            val json = result?.toString() ?: "[]"
            
            // Parse as array of AIIntent
            val type = object : com.google.gson.reflect.TypeToken<List<AIIntent>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting history: ${e.message}")
            emptyList()
        }
    }
}
