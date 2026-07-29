package com.example.imgtotext

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    OcrScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

suspend fun prepareTessData(context: Context): String {
    return withContext(Dispatchers.IO) {
        val tessDir = File(context.filesDir, "tesseract")
        val tessDataDir = File(tessDir, "tessdata")
        if (!tessDataDir.exists()) tessDataDir.mkdirs()

        val trainedDataFile = File(tessDataDir, "rus.traineddata")
        if (!trainedDataFile.exists()) {
            try {
                context.assets.open("rus.traineddata").use { input ->
                    FileOutputStream(trainedDataFile).use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        tessDir.absolutePath
    }
}

suspend fun processImageWithTesseract(context: Context, uri: Uri, dataPath: String): String {
    return withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (bitmap == null) return@withContext "Ошибка: Не удалось загрузить фото"

            val tessApi = TessBaseAPI()
            val initSuccess = tessApi.init(dataPath, "rus")
            if (!initSuccess) {
                tessApi.recycle()
                return@withContext "Ошибка: Не удалось инициализировать Tesseract"
            }

            // Автоматический поиск блоков текста
            tessApi.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO
            tessApi.setImage(bitmap)
            val text = tessApi.utF8Text
            tessApi.recycle()

            if (text.isNullOrBlank()) "Текст не найден. Попробуйте другое фото." else text
        } catch (e: Exception) {
            e.printStackTrace()
            "Ошибка: ${e.localizedMessage}"
        }
    }
}

@Composable
fun OcrScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var recognizedText by remember { mutableStateOf("Выберите фото для распознавания текста...") }
    var isProcessing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isProcessing = true
            recognizedText = "Идет распознавание (Tesseract)..."
            coroutineScope.launch {
                val dataPath = prepareTessData(context)
                recognizedText = processImageWithTesseract(context, uri, dataPath)
                isProcessing = false
            }
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = { galleryLauncher.launch("image/*") },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            enabled = !isProcessing
        ) {
            Text("Выбрать фото")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(
                onClick = {
                    clipboardManager.setText(AnnotatedString(recognizedText))
                    Toast.makeText(context, "Текст скопирован!", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f),
                enabled = recognizedText.isNotBlank() && !isProcessing
            ) {
                Text("Копировать")
            }

            FilledTonalButton(
                onClick = {
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, recognizedText)
                        type = "text/plain"
                    }
                    val shareIntent = Intent.createChooser(sendIntent, "Отправить текст")
                    context.startActivity(shareIntent)
                },
                modifier = Modifier.weight(1f),
                enabled = recognizedText.isNotBlank() && !isProcessing
            ) {
                Text("Поделиться")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Surface(
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                text = recognizedText,
                modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

// Асинхронная функция распознавания через ML Kit
suspend fun processImageWithMLKit(context: Context, uri: Uri): String {
    return try {
        val image = InputImage.fromFilePath(context, uri)
        val recognizer = TextRecognition.getClient(CyrillicTextRecognizerOptions.Builder().build())
        val result = recognizer.process(image).await()
        
        if (result.text.isBlank()) "Текст не найден. Попробуйте другое фото." else result.text
    } catch (e: Exception) {
        e.printStackTrace()
        "Ошибка: ${e.localizedMessage}"
    }
}

@Composable
fun OcrScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var recognizedText by remember { mutableStateOf("Выберите фото для распознавания текста...") }
    var isProcessing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isProcessing = true
            recognizedText = "Нейросеть обрабатывает изображение..."
            coroutineScope.launch {
                recognizedText = processImageWithMLKit(context, uri)
                isProcessing = false
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Главная кнопка загрузки
        Button(
            onClick = { galleryLauncher.launch("image/*") },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = !isProcessing
        ) {
            Text("Выбрать фото")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Блок кнопок управления текстом
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(
                onClick = {
                    clipboardManager.setText(AnnotatedString(recognizedText))
                    Toast.makeText(context, "Текст скопирован!", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f),
                enabled = recognizedText.isNotBlank() && !isProcessing
            ) {
                Text("Копировать")
            }

            FilledTonalButton(
                onClick = {
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, recognizedText)
                        type = "text/plain"
                    }
                    val shareIntent = Intent.createChooser(sendIntent, "Отправить текст")
                    context.startActivity(shareIntent)
                },
                modifier = Modifier.weight(1f),
                enabled = recognizedText.isNotBlank() && !isProcessing
            ) {
                Text("Поделиться")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Окно с результатом
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                text = recognizedText,
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
