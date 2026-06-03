package com.nszconverter.domain.usecase

import android.net.Uri
import com.nszconverter.data.repository.ConversionRepository
import com.nszconverter.domain.model.KeysStatus
import javax.inject.Inject

class ImportKeysUseCase @Inject constructor(
    private val repo: ConversionRepository,
) {
    suspend operator fun invoke(uri: Uri): KeysStatus = repo.importKeysFromUri(uri)
}
