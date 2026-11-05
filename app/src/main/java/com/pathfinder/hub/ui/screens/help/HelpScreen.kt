package com.pathfinder.hub.ui.screens.help

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pathfinder.hub.ui.components.CollapsibleSection
import com.pathfinder.hub.ui.theme.PathfinderBlue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class FaqItem(val question: String, val answer: String)
data class GuideItem(val role: String, val title: String, val steps: List<String>)
data class AppMapSection(val title: String, val items: List<String>)

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var faq by remember { mutableStateOf<List<FaqItem>>(emptyList()) }
    var guides by remember { mutableStateOf<List<GuideItem>>(emptyList()) }
    var appMap by remember { mutableStateOf<List<AppMapSection>>(emptyList()) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val faqJson = context.assets.open("help/faq_ru.json")
                    .bufferedReader().use { it.readText() }
                val arr = JSONObject(faqJson).getJSONArray("faq")
                faq = (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    FaqItem(o.getString("question"), o.getString("answer"))
                }
            } catch (_: Exception) {}
            try {
                val guidesJson = context.assets.open("help/guides_ru.json")
                    .bufferedReader().use { it.readText() }
                val arr = JSONObject(guidesJson).getJSONArray("guides")
                guides = (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    val stepsArr = o.getJSONArray("steps")
                    GuideItem(
                        role = o.getString("role"),
                        title = o.getString("title"),
                        steps = (0 until stepsArr.length()).map { j -> stepsArr.getString(j) }
                    )
                }
            } catch (_: Exception) {}
            try {
                val appMapJson = context.assets.open("help/app_map_ru.json")
                    .bufferedReader().use { it.readText() }
                val arr = JSONObject(appMapJson).getJSONArray("sections")
                appMap = (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    val itemsArr = o.getJSONArray("items")
                    AppMapSection(
                        title = o.getString("title"),
                        items = (0 until itemsArr.length()).map { j -> itemsArr.getString(j) }
                    )
                }
            } catch (_: Exception) {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Справка") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PathfinderBlue,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (faq.isNotEmpty()) {
                item {
                    Text("Часто задаваемые вопросы",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold)
                }
                items(faq) { item ->
                    CollapsibleSection(title = item.question, initiallyExpanded = false) {
                        Text(item.answer, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (guides.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Text("Инструкции по ролям",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold)
                }
                items(guides) { guide ->
                    CollapsibleSection(title = guide.title, initiallyExpanded = false) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            guide.steps.forEachIndexed { idx, step ->
                                Row(verticalAlignment = Alignment.Top) {
                                    Text("${idx + 1}. ", style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold)
                                    Text(step, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
            if (appMap.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Text("Карта приложения",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold)
                }
                items(appMap) { section ->
                    CollapsibleSection(title = section.title, initiallyExpanded = false) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            section.items.forEach { line ->
                                Text("• $line", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:support@pathfinder.hub")
                            putExtra(Intent.EXTRA_SUBJECT, "Pathfinder Hub — вопрос из справки")
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) { Text("Связаться с поддержкой") }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
