package com.tazztone.losslesscut.di

import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Test

class DispatchersModuleTest {

    @Test
    fun testProvideIoDispatcher() {
        val dispatcher = DispatchersModule.provideIoDispatcher()
        assertEquals(Dispatchers.IO, dispatcher)
    }

}
