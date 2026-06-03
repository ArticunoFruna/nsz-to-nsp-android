package com.nszconverter.domain.usecase

import com.nszconverter.data.repository.ConversionRepository
import javax.inject.Inject

class CancelJobUseCase @Inject constructor(
    private val repo: ConversionRepository,
) {
    suspend operator fun invoke(jobId: String) = repo.cancelJob(jobId)
}
