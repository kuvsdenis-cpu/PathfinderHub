package com.pathfinder.hub.ui.bible

import com.pathfinder.hub.data.local.entity.bible.BibleBookEntity
import com.pathfinder.hub.data.local.entity.bible.BibleBookmarkEntity
import com.pathfinder.hub.data.local.entity.bible.BibleVerseEntity

data class BibleBooksUiState(
    val isLoading: Boolean = true,
    val oldTestament: List<BibleBookEntity> = emptyList(),
    val newTestament: List<BibleBookEntity> = emptyList(),
    val bookmarkCounts: Map<String, Int> = emptyMap(),
)

data class BibleBookUiState(
    val isLoading: Boolean = true,
    val book: BibleBookEntity? = null,
)

data class BibleChapterUiState(
    val isLoading: Boolean = true,
    val verses: List<BibleVerseEntity> = emptyList(),
    val bookmarkedVerseIds: Set<String> = emptySet(),
    val notes: Map<String, String> = emptyMap(),
    val bookName: String = "",
    val chapterNumber: Int = 0,
    val hasNextChapter: Boolean = false,
    val verseId: String? = null,
)

data class BibleBookmarksUiState(
    val isLoading: Boolean = true,
    val bookmarks: List<BibleBookmarkEntity> = emptyList(),
    val sections: List<BookmarkSection> = emptyList(),
)


data class BookmarkSection(
    val bookId: String,
    val bookName: String,
    val bookmarks: List<BibleBookmarkEntity>,
)
