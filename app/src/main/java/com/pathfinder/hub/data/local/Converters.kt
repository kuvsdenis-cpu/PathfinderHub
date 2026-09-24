package com.pathfinder.hub.data.local

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pathfinder.hub.data.local.entity.gamification.*
import com.pathfinder.hub.data.local.entity.learning.*
import com.pathfinder.hub.data.local.entity.newspaper.*
import com.pathfinder.hub.data.local.entity.parent.*
import com.pathfinder.hub.data.local.entity.planning.*
import java.util.Date

class Converters {
    private val gson = Gson()

    // ===== Date =====
    @TypeConverter fun fromTimestamp(v: Long?): Date? = v?.let { Date(it) }
    @TypeConverter fun dateToTimestamp(d: Date?): Long? = d?.time

    // ===== Примитивные списки и map =====
    @TypeConverter fun fromStringList(v: String?): List<String> =
        v?.let { gson.fromJson(it, object : TypeToken<List<String>>() {}.type) } ?: emptyList()
    @TypeConverter fun fromStringListToString(l: List<String>?): String =
        gson.toJson(l ?: emptyList<String>())

    @TypeConverter fun fromIntList(v: String?): List<Int> =
        v?.let { gson.fromJson(it, object : TypeToken<List<Int>>() {}.type) } ?: emptyList()
    @TypeConverter fun fromIntListToString(l: List<Int>?): String =
        gson.toJson(l ?: emptyList<Int>())

    @TypeConverter fun fromMap(v: String?): Map<String, Any> =
        v?.let { gson.fromJson(it, object : TypeToken<Map<String, Any>>() {}.type) } ?: emptyMap()
    @TypeConverter fun fromMapToString(m: Map<String, Any>?): String =
        gson.toJson(m ?: emptyMap<String, Any>())

    // ===== Learning =====
    @TypeConverter fun fromChecklistItems(v: String?): List<ChecklistItem> =
        v?.let { gson.fromJson(it, object : TypeToken<List<ChecklistItem>>() {}.type) } ?: emptyList()
    @TypeConverter fun fromChecklistItemsToString(l: List<ChecklistItem>?): String =
        gson.toJson(l ?: emptyList<ChecklistItem>())

    // ===== Gamification =====
    @TypeConverter fun fromBadgeItems(v: String?): List<BadgeItem> =
        v?.let { gson.fromJson(it, object : TypeToken<List<BadgeItem>>() {}.type) } ?: emptyList()
    @TypeConverter fun fromBadgeItemsToString(l: List<BadgeItem>?): String =
        gson.toJson(l ?: emptyList<BadgeItem>())

    @TypeConverter fun fromChevronItems(v: String?): List<ChevronItem> =
        v?.let { gson.fromJson(it, object : TypeToken<List<ChevronItem>>() {}.type) } ?: emptyList()
    @TypeConverter fun fromChevronItemsToString(l: List<ChevronItem>?): String =
        gson.toJson(l ?: emptyList<ChevronItem>())

    @TypeConverter fun fromSpecialMarkItems(v: String?): List<SpecialMarkItem> =
        v?.let { gson.fromJson(it, object : TypeToken<List<SpecialMarkItem>>() {}.type) } ?: emptyList()
    @TypeConverter fun fromSpecialMarkItemsToString(l: List<SpecialMarkItem>?): String =
        gson.toJson(l ?: emptyList<SpecialMarkItem>())

    @TypeConverter fun fromTeamParticipants(v: String?): List<TeamParticipant> =
        v?.let { gson.fromJson(it, object : TypeToken<List<TeamParticipant>>() {}.type) } ?: emptyList()
    @TypeConverter fun fromTeamParticipantsToString(l: List<TeamParticipant>?): String =
        gson.toJson(l ?: emptyList<TeamParticipant>())

    @TypeConverter fun fromActivityHistoryItems(v: String?): List<ActivityHistoryItem> =
        v?.let { gson.fromJson(it, object : TypeToken<List<ActivityHistoryItem>>() {}.type) } ?: emptyList()
    @TypeConverter fun fromActivityHistoryItemsToString(l: List<ActivityHistoryItem>?): String =
        gson.toJson(l ?: emptyList<ActivityHistoryItem>())

    // ===== Parent =====
    @TypeConverter fun fromParentAttendees(v: String?): List<ParentAttendee> =
        v?.let { gson.fromJson(it, object : TypeToken<List<ParentAttendee>>() {}.type) } ?: emptyList()
    @TypeConverter fun fromParentAttendeesToString(l: List<ParentAttendee>?): String =
        gson.toJson(l ?: emptyList<ParentAttendee>())

    // ===== Newspaper =====
    @TypeConverter fun fromNewspaperSections(v: String?): List<NewspaperSection> =
        v?.let { gson.fromJson(it, object : TypeToken<List<NewspaperSection>>() {}.type) } ?: emptyList()
    @TypeConverter fun fromNewspaperSectionsToString(l: List<NewspaperSection>?): String =
        gson.toJson(l ?: emptyList<NewspaperSection>())

    @TypeConverter fun fromTemplateSections(v: String?): List<TemplateSection> =
        v?.let { gson.fromJson(it, object : TypeToken<List<TemplateSection>>() {}.type) } ?: emptyList()
    @TypeConverter fun fromTemplateSectionsToString(l: List<TemplateSection>?): String =
        gson.toJson(l ?: emptyList<TemplateSection>())

    // ===== Planning =====
    @TypeConverter fun fromNewspaperBlocks(v: String?): List<NewspaperBlock> =
        v?.let { gson.fromJson(it, object : TypeToken<List<NewspaperBlock>>() {}.type) } ?: emptyList()
    @TypeConverter fun fromNewspaperBlocksToString(l: List<NewspaperBlock>?): String =
        gson.toJson(l ?: emptyList<NewspaperBlock>())
}