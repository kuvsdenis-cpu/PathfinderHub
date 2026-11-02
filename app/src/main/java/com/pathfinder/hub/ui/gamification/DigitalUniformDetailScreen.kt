package com.pathfinder.hub.ui.gamification

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil.compose.AsyncImage
import com.pathfinder.hub.R
import com.pathfinder.hub.ui.theme.PathfinderBlue
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.CornerRadius

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DigitalUniformDetailScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: DigitalUniformViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Детали формы") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PathfinderBlue,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                )
            )
        }
    ) { padding ->
        val uniform = state.uniform
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        if (uniform == null) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Text("Форма пока не сформирована")
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ---- Торс на весь верх экрана ----
            UniformWithRewards(
                badges = state.resolvedBadges,
                chevrons = state.resolvedChevrons,
                marks = state.resolvedMarks,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(8.dp),
            )

            // ---- Списки наград — нижняя половина, скроллом ----
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
            ) {
                item { DetailSection("Нашивки", "Специализации", uniform.earnedBadges.size, Icons.Default.Star) }
                if (uniform.earnedBadges.isEmpty()) {
                    item { DetailEmpty("Начни изучать специализации!") }
                } else {
                    state.resolvedBadges.forEach { b ->
                        item { BadgeDetailRow(b) }
                    }
                }

                item { DetailSection("Шевроны", "Ступени", uniform.earnedChevrons.size, Icons.Default.Person) }
                if (uniform.earnedChevrons.isEmpty()) {
                    item { DetailEmpty("Пройди первую ступень!") }
                } else {
                    state.resolvedChevrons.forEach { cv ->
                        item { ChevronDetailRow(cv) }
                    }
                }

                item { DetailSection("Особые знаки", "Достижения", uniform.specialMarks.size, Icons.Default.Star) }
                if (uniform.specialMarks.isEmpty()) {
                    item { DetailEmpty("Получи первое достижение!") }
                } else {
                    state.resolvedMarks.forEach { m ->
                        item { MarkDetailRow(m) }
                    }
                }
            }
        }
    }
}

@Composable
private fun UniformWithRewards(
    badges: List<ResolvedBadge>,
    chevrons: List<ResolvedChevron>,
    marks: List<ResolvedMark>,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val w = maxWidth
        val h = maxHeight

        // --- Схематичный торс ---
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cw = size.width
            val ch = size.height
            val cx = cw / 2f

            // Рубашка
            drawRoundRect(
                color = Color(0xFFD9C7A3),
                topLeft = Offset(cw * 0.22f, ch * 0.18f),
                size = Size(cw * 0.56f, ch * 0.78f),
                cornerRadius = CornerRadius(cw * 0.06f),
            )
            // Воротник
            val collar = Path().apply {
                moveTo(cx - cw * 0.10f, ch * 0.18f)
                lineTo(cx, ch * 0.30f)
                lineTo(cx - cw * 0.02f, ch * 0.18f)
                close()
            }
            drawPath(collar, Color.White)
            val collar2 = Path().apply {
                moveTo(cx + cw * 0.10f, ch * 0.18f)
                lineTo(cx, ch * 0.30f)
                lineTo(cx + cw * 0.02f, ch * 0.18f)
                close()
            }
            drawPath(collar2, Color.White)
            // Галстук
            val tie = Path().apply {
                moveTo(cx, ch * 0.30f)
                lineTo(cx + cw * 0.03f, ch * 0.34f)
                lineTo(cx + cw * 0.02f, ch * 0.62f)
                lineTo(cx, ch * 0.66f)
                lineTo(cx - cw * 0.02f, ch * 0.62f)
                lineTo(cx - cw * 0.03f, ch * 0.34f)
                close()
            }
            drawPath(tie, Color(0xFFFFC107))
            // Лента через плечо
            val ribbon = Path().apply {
                moveTo(cw * 0.30f, ch * 0.18f)
                lineTo(cw * 0.42f, ch * 0.18f)
                lineTo(cw * 0.58f, ch * 0.78f)
                lineTo(cw * 0.46f, ch * 0.78f)
                close()
            }
            drawPath(ribbon, Color(0xFF9E9E9E))
            drawPath(ribbon, Color(0xFF6B6B6B), style = Stroke(width = cw * 0.004f))
            // Пуговицы
            for (i in 0..2) {
                drawCircle(
                    color = Color(0xFF8D6E63),
                    radius = cw * 0.01f,
                    center = Offset(cx + cw * 0.12f, ch * (0.34f + i * 0.14f)),
                )
            }
            // Карман
            drawRect(
                color = Color(0xFFC9B48A),
                topLeft = Offset(cx + cw * 0.08f, ch * 0.40f),
                size = Size(cw * 0.14f, ch * 0.10f),
                style = Stroke(width = cw * 0.003f),
            )
        }

        // --- Нашивки: вдоль ленты (5 слотов) ---
        val badgeSize = w * 0.09f
        badges.take(5).forEachIndexed { index, b ->
            AvatarIcon(
                model = b.imageUrl,
                fallback = Icons.Default.Star,
                tint = PathfinderBlue,
                modifier = Modifier
                    .offset(
                        x = w * 0.32f + badgeSize * 0.6f * index,
                        y = h * 0.28f + h * 0.10f * index,
                    )
                    .size(badgeSize),
            )
        }

        // --- Шевроны: на левом рукаве (3 слота, столбик) ---
        val chevronSize = w * 0.09f
        chevrons.take(3).forEachIndexed { index, cv ->
            AvatarIcon(
                model = cv.icon,
                fallback = Icons.Default.Person,
                tint = PathfinderBlue,
                modifier = Modifier
                    .offset(
                        x = w * 0.18f,
                        y = h * 0.34f + chevronSize * 1.1f * index,
                    )
                    .size(chevronSize),
            )
        }
    }
}


@Composable
private fun AvatarIcon(
    model: String?,
    fallback: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    modifier: Modifier,
) {
    if (!model.isNullOrBlank()) {
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .background(Color.White.copy(alpha = 0.85f), CircleShape)
                .clip(CircleShape),
        )
    } else {
        Icon(
            imageVector = fallback,
            contentDescription = null,
            tint = tint,
            modifier = modifier
                .background(Color.White.copy(alpha = 0.85f), CircleShape)
                .clip(CircleShape),
        )
    }
}

@Composable
private fun DetailSection(
    title: String,
    subtitle: String,
    count: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = PathfinderBlue)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            "$count",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = PathfinderBlue
        )
    }
}

@Composable
private fun DetailEmpty(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun BadgeDetailRow(b: ResolvedBadge) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (b.imageUrl != null) {
                AsyncImage(
                    model = b.imageUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                )
            } else {
                Icon(Icons.Default.Star, null, tint = PathfinderBlue, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(b.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    "Нашивка",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                SimpleDateFormat("dd.MM.yyyy", Locale.forLanguageTag("ru")).format(b.earnedAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ChevronDetailRow(cv: ResolvedChevron) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Person, null, tint = PathfinderBlue, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(cv.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    "Шеврон",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                SimpleDateFormat("dd.MM.yyyy", Locale.forLanguageTag("ru")).format(cv.earnedAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MarkDetailRow(m: ResolvedMark) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Star, null, tint = PathfinderBlue, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(m.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    "Особый знак",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                SimpleDateFormat("dd.MM.yyyy", Locale.forLanguageTag("ru")).format(m.earnedAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}