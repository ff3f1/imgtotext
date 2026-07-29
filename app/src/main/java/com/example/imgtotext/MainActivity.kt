import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
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
                    OcrScreen(
                        modifier = Modifier.padding(innerPadding),
                        context = this
                    )
                }
            }
        }
    }
}

// Функция для копирования языкового файла из APK в память телефона
fun prepareTessData(context: Context): String {
    val dir = File(context.filesDir, "tesseract/tessdata")
    if (!dir.exists()) dir.mkdirs()
    val dataFile = File(dir, "rus.traineddata")
    if (!dataFile.exists()) {
        context.assets.open("tessdata/rus.traineddata").use { input ->
            FileOutputStream(dataFile).use { output ->
                input.copyTo(output)
            }
        }
    }
    return File(context.filesDir, "tesseract").absolutePath
}

// Асинхронная функция распознавания
suspend fun processImageWithTesseract(context: Context, uri: Uri): String {
    return withContext(Dispatchers.IO) {
        try {
            val dataPath = prepareTessData(context)
            val inputStream = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)

            val tess = TessBaseAPI()
            // Инициализируем русский язык
            val success = tess.init(dataPath, "rus")
            if (!success) {
                return@withContext "Ошибка: Не удалось инициализировать движок Tesseract"
            }

            tess.setImage(bitmap)
            val text = tess.utF8Text
            tess.end() // Освобождаем память

            if (text.isNullOrBlank()) "Текст на фото не найден." else text
        } catch (e: Exception) {
            e.printStackTrace()
            "Ошибка обработки: ${e.localizedMessage}"
        }
    }
}

@Composable
fun OcrScreen(modifier: Modifier = Modifier, context: Context) {
    var recognizedText by remember { mutableStateOf("Выберите изображение с русским текстом") }
    val coroutineScope = rememberCoroutineScope()

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            recognizedText = "Идет распознавание (это может занять пару секунд)..."
            // Запускаем тяжелый процесс в фоне, чтобы интерфейс не завис
            coroutineScope.launch {
                recognizedText = processImageWithTesseract(context, uri)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = { galleryLauncher.launch("image/*") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Text("Загрузить фото")
        }

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