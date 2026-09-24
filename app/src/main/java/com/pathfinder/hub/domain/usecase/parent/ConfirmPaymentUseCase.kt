package com.pathfinder.hub.domain.usecase.parent
import com.pathfinder.hub.data.repository.ParentRepository
import javax.inject.Inject

class ConfirmPaymentUseCase @Inject constructor(private val repo: ParentRepository) {
    suspend operator fun invoke(paymentId: String, confirmedBy: String): Result<Unit> = try {
        repo.confirmPayment(paymentId, "confirmed", confirmedBy, System.currentTimeMillis())
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }
}
