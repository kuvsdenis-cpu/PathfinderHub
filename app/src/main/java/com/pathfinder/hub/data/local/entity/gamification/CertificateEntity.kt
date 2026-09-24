package com.pathfinder.hub.data.local.entity.gamification
import androidx.room.*
import java.util.Date

@Entity(tableName = "certificates")
data class CertificateEntity(
    @PrimaryKey val id: String, val userId: String, val type: String,
    val targetId: String, val issuedAt: Date, val pdfUrl: String, val imageUrl: String
)
