package durus.salafi.bangladesh.di

import org.koin.core.module.Module
import org.koin.dsl.module
import durus.salafi.bangladesh.util.PlatformBridge
import durus.salafi.bangladesh.util.AndroidPlatformBridge

actual fun platformModule(): Module = module {
    single<PlatformBridge> { AndroidPlatformBridge(get()) }
}
