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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Share
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
            // Переключатель темы (по умолчанию берем системную, но даем пользователю выбор)
            var isDarkTheme by remember { mutableStateOf<Boolean?>(null) }
            val useDark = isDarkTheme ?: isSystemInDarkTheme()

            MaterialTheme(
                colorScheme = if (useDark) darkColorScheme() else lightColorScheme()
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    OcrScreen(
                        isDarkTheme = useDark,
                        onThemeToggle = { isDarkTheme = !useDark }
                    )
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

            if (bitmap == null) return@withContext "Ошибка: Не удалось загрузить изображение"

            val tessApi = TessBaseAPI()
            val initSuccess = tessApi.init(dataPath, "rus")
            if (!initSuccess) {
                tessApi.recycle()
                return@withContext "Ошибка: Не удалось инициализировать языковую модель Tesseract"
            }

            tessApi.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO
            tessApi.setImage(bitmap)
            val text = tessApi.utF8Text
            tessApi.recycle()

            if (text.isNullOrBlank()) "Текст на изображении не обнаружен." else text.trim()
        } catch (e: Exception) {
            e.printStackTrace()
            "Ошибка обработки: ${e.localizedMessage}"
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrScreen(
    isDarkTheme: Boolean,
    onThemeToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var recognizedText by remember { mutableStateOf("Выберите или сделайте фото, чтобы извлечь из него текст...") }
    var isProcessing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isProcessing = true
            recognizedText = "Распознавание текста (работает Tesseract)..."
            coroutineScope.launch {
                val dataPath = prepareTessData(context)
                recognizedText = processImageWithTesseract(context, uri, dataPath)
                isProcessing = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI OCR Распознавание") },
                actions = {
                    // Кнопка переключения темы в шапке
                    IconButton(onClick = onThemeToggle) {
                        Icon(
                            imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Сменить тему"
                        )
                    }
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Главная кнопка выбора фото
            Button(
                onClick = { galleryLauncher.launch("image/*") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !isProcessing
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Обработка...")
                } else {
                    Text("Выбрать фото из галереи")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Панель дополнительных действий (Копировать, Поделиться, Очистить)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(recognizedText))
                        Toast.makeText(context, "Скопировано в буфер обмена", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f),
                    enabled = recognizedText.isNotBlank() && !isProcessing
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Копировать")
                }

                OutlinedButton(
                    onClick = {
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, recognizedText)
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Поделиться текстом"))
                    },
                    modifier = Modifier.weight(1f),
                    enabled = recognizedText.isNotBlank() && !isProcessing
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Поделиться")
                }

                IconButton(
                    onClick = { recognizedText = "" },
                    enabled = recognizedText.isNotBlank() && !isProcessing
                ) {
                    Icon(Icons.Default.Clear, contentDescription = "Очистить")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Окно вывода распознанного текста с прокруткой
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 2.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Text(
                        text = recognizedText,
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
