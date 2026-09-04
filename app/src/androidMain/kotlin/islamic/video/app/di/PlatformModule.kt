package islamic.video.app.di

import org.koin.core.module.Module
import org.koin.dsl.module
import islamic.video.app.util.PlatformBridge
import islamic.video.app.util.AndroidPlatformBridge

actual fun platformModule(): Module = module {
    single<PlatformBridge> { AndroidPlatformBridge(get()) }
}
