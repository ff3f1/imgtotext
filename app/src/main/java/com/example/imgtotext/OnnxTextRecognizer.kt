package com.example.imgtotext

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer

class OnnxTextRecognizer(context: Context) {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession

    // Алфавит (CTC Blank [0] + кириллица, цифры и знаки)
    private val alphabet = listOf(
        "", " ", "!", "\"", "#", "$", "%", "&", "'", "(", ")", "*", "+", ",", "-", ".", "/",
        "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", ":", ";", "<", "=", ">", "?", "@",
        "А", "Б", "В", "Г", "Д", "Е", "Ё", "Ж", "З", "И", "Й", "К", "Л", "М", "Н", "О", "П",
        "Р", "С", "Т", "У", "Ф", "Х", "Ц", "Ч", "Ш", "Щ", "Ъ", "Ы", "Ь", "Э", "Ю", "Я",
        "а", "б", "в", "г", "д", "е", "ё", "ж", "з", "и", "й", "к", "л", "м", "н", "о", "п",
        "р", "с", "т", "у", "ф", "х", "ц", "ч", "ш", "щ", "ъ", "ы", "ь", "э", "ю", "я"
    )

    init {
        // Загрузка модели из assets/model.onnx
        val modelBytes = context.assets.open("model.onnx").readBytes()
        session = env.createSession(modelBytes, OrtSession.SessionOptions())
    }

    suspend fun recognize(bitmap: Bitmap): String = withContext(Dispatchers.Default) {
        try {
            val targetWidth = 256
            val targetHeight = 32
            val resized = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)

            val floatBuffer = FloatBuffer.allocate(1 * 1 * targetHeight * targetWidth)
            for (y in 0 until targetHeight) {
                for (x in 0 until targetWidth) {
                    val pixel = resized.getPixel(x, y)
                    val r = Color.red(pixel)
                    val g = Color.green(pixel)
                    val b = Color.blue(pixel)
                    val gray = (0.299f * r + 0.587f * g + 0.114f * b) / 255.0f
                    floatBuffer.put(gray)
                }
            }
            floatBuffer.rewind()

            val inputShape = longArrayOf(1, 1, targetHeight.toLong(), targetWidth.toLong())
            val inputTensor = OnnxTensor.createTensor(env, floatBuffer, inputShape)

            inputTensor.use {
                val inputName = session.inputNames.iterator().next()
                val results = session.run(mapOf(inputName to inputTensor))

                results.use {
                    @Suppress("UNCHECKED_CAST")
                    val outputArray = results[0].value as Array<Array<FloatArray>>
                    return@withContext decodeCtcOutput(outputArray[0])
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext "Ошибка: ${e.localizedMessage}"
        }
    }

    private fun decodeCtcOutput(timesteps: Array<FloatArray>): String {
        val result = StringBuilder()
        var lastClassIdx = -1

        for (probs in timesteps) {
            var maxIdx = 0
            var maxProb = probs[0]
            for (i in probs.indices) {
                if (probs[i] > maxProb) {
                    maxProb = probs[i]
                    maxIdx = i
                }
            }

            if (maxIdx != 0 && maxIdx != lastClassIdx) {
                if (maxIdx < alphabet.size) {
                    result.append(alphabet[maxIdx])
                }
            }
            lastClassIdx = maxIdx
        }

        return result.toString().trim()
    }
}