package com.nszconverter.domain.usecase

import com.nszconverter.data.repository.ConversionRepository
import com.nszconverter.domain.model.KeysStatus
import javax.inject.Inject

class ValidateKeysUseCase @Inject constructor(
    private val repo: ConversionRepository,
) {
    suspend operator fun invoke(localPath: String): KeysStatus = repo.validateKeys(localPath)
}
