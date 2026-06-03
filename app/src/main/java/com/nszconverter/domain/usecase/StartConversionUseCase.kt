package com.nszconverter.domain.usecase

import com.nszconverter.data.repository.ConversionRepository
import com.nszconverter.domain.model.ConversionJob
import javax.inject.Inject

class StartConversionUseCase @Inject constructor(
    private val repo: ConversionRepository,
) {
    suspend operator fun invoke(jobs: List<ConversionJob>) = repo.enqueueConversions(jobs)
}
