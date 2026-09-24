package com.pathfinder.hub.domain.usecase.learning
import com.pathfinder.hub.data.repository.HonorRepository
import com.pathfinder.hub.data.local.entity.learning.HonorEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SearchHonorsUseCase @Inject constructor(private val honorRepo: HonorRepository) {
    operator fun invoke(query: String): Flow<List<HonorEntity>> = honorRepo.searchHonors(query)
}
