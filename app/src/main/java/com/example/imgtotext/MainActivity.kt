package com.example.imgtotext
import android.content.Context
import android.content.Intent
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
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.russian.RussianTextRecognizerOptions
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    OcrScreen(
                        modifier = Modifier.padding(innerPadding)
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
        val recognizer = TextRecognition.getClient(RussianTextRecognizerOptions.Builder().build())
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
