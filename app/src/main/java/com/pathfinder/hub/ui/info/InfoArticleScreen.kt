package com.pathfinder.hub.ui.info

import android.content.Intent
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.repository.KnowledgeBaseRepository
import com.pathfinder.hub.domain.model.InfoArticle
import com.pathfinder.hub.ui.theme.PathfinderBlue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class InfoArticleViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: KnowledgeBaseRepository
) : ViewModel() {
    private val categoryId: String = savedStateHandle.get<String>("categoryId") ?: ""
    private val articleId: String = savedStateHandle.get<String>("articleId") ?: ""

    val article: StateFlow<InfoArticle?> = repository.getCategories()
        .map { list ->
            list.find { it.id == categoryId }?.articles?.find { it.id == articleId }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoArticleScreen(
    onBack: () -> Unit,
    viewModel: InfoArticleViewModel = hiltViewModel()
) {
    val article by viewModel.article.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(article?.title ?: "Статья", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PathfinderBlue,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    ) { padding ->
        val art = article ?: return@Scaffold

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = art.title,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            art.imageRes.forEach { resId ->
                ZoomableImage(resId)
            }
            
            art.pdfAsset?.let { pdf ->
                Button(
                    onClick = {
                        try {
                            if (pdf.startsWith("http://") || pdf.startsWith("https://")) {
                                // Ссылка из облака
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(android.net.Uri.parse(pdf), "application/pdf")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(intent)
                            } else {
                                // Локальный файл из assets
                                val file = java.io.File(context.cacheDir, pdf)
                                if (!file.exists()) {
                                    context.assets.open(pdf).use { input ->
                                        file.outputStream().use { output -> input.copyTo(output) }
                                    }
                                }
                                val uri = androidx.core.content.FileProvider.getUriForFile(
                                    context,
                                    context.packageName + ".fileprovider",
                                    file
                                )
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, "application/pdf")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(intent)
                            }
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, "Не удалось открыть PDF: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Icon(Icons.Default.Description, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Открыть PDF")
                }
            }
            
            Text(
                text = art.content,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun ZoomableImage(resId: Int) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 200.dp, max = 600.dp)
            .padding(bottom = 16.dp)
            .clip(RoundedCornerShape(8.dp))
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    if (scale > 1f) {
                        offset = Offset(offset.x + pan.x, offset.y + pan.y)
                    } else {
                        offset = Offset.Zero
                    }
                }
            }
    ) {
        Image(
            painter = painterResource(id = resId),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y,
                ),
            contentScale = ContentScale.Fit,
        )
    }
}
