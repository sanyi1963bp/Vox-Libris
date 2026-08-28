package hu.konyvtar.tts

import android.app.Application
import hu.konyvtar.tts.data.AppDb

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        AppDb.init(this)
    }
}
