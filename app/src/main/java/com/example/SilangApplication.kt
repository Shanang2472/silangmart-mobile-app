package com.example

import android.app.Application
import com.example.data.ai.AiRepository
import com.example.data.ai.PreviewAiRepository
import com.example.data.local.AppDatabase
import com.example.data.repository.SilangRepository

class SilangApplication : Application() {
    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val repository: SilangRepository by lazy { SilangRepository(database) }
    val aiRepository: AiRepository by lazy { PreviewAiRepository() }
}
