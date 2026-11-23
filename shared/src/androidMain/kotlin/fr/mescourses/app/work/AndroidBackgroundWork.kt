package fr.mescourses.app.work

import android.content.Context
import android.os.Looper
import fr.mescourses.app.platform.BackgroundWork
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Service au premier plan pendant les travaux du chef (notification « Le chef au travail »). */
class AndroidBackgroundWork(private val context: Context, private val scope: CoroutineScope) : BackgroundWork {
    override fun start(text: String) = MenuWorkService.start(context, text)

    override fun update(text: String) {
        // La notification ne se met pas à jour depuis le fil principal (accès système bloquant).
        if (Looper.myLooper() == Looper.getMainLooper()) {
            scope.launch(Dispatchers.IO) { MenuWorkService.update(context, text) }
        } else {
            MenuWorkService.update(context, text)
        }
    }

    override fun stop() = MenuWorkService.stop(context)
}
