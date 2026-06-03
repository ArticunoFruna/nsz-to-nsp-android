package com.nszconverter.domain.usecase

import com.nszconverter.data.repository.ConversionRepository
import com.nszconverter.domain.model.ConversionJob
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveHistoryUseCase @Inject constructor(
    private val repo: ConversionRepository,
) {
    operator fun invoke(): Flow<List<ConversionJob>> = repo.observeHistory()
}
