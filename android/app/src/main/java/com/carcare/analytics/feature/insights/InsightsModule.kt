package com.carcare.analytics.feature.insights

import dagger.Module
import dagger.Binds
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module @InstallIn(SingletonComponent::class)
abstract class InsightsModule { @Binds abstract fun source(repository: InsightsRepository): InsightsDataSource }
