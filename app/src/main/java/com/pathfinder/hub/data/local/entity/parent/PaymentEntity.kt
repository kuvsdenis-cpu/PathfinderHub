package com.pathfinder.hub.data.local.entity.parent
import androidx.room.*
import java.util.Date

@Entity(tableName = "payments",
    indices = [Index("childId"), Index("parentId"), Index("status")])
data class PaymentEntity(
    @PrimaryKey val id: String, val clubId: String, val childId: String,
    val parentId: String, val type: String, val title: String,
    val amount: Double, val currency: String, val dueDate: Date,
    val status: String, val paymentMethod: String?, val paidAt: Date?,
    val confirmedBy: String?, val confirmedAt: Date?, val receiptUrl: String?,
    val createdBy: String, val createdAt: Date
)
