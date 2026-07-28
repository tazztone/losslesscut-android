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

    @Test
    fun testProvideMainDispatcher() {
        val dispatcher = DispatchersModule.provideMainDispatcher()
        assertEquals(Dispatchers.Main, dispatcher)
    }

    @Test
    fun testProvideDefaultDispatcher() {
        val dispatcher = DispatchersModule.provideDefaultDispatcher()
        assertEquals(Dispatchers.Default, dispatcher)
    }
}
