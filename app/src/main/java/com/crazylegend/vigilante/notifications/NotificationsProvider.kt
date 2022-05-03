package com.crazylegend.vigilante.notifications

import android.app.Notification
import android.os.Build
import android.text.SpannableString
import android.view.accessibility.AccessibilityEvent
import androidx.lifecycle.ServiceLifecycleDispatcher
import com.crazylegend.common.currentTimeMillis
import com.crazylegend.common.tryOrElse
import com.crazylegend.vigilante.BuildConfig
import com.crazylegend.vigilante.contracts.service.ServiceLifecycle
import com.crazylegend.vigilante.di.modules.coroutines.dispatchers.IoDispatcher
import com.crazylegend.vigilante.di.providers.prefs.defaultPrefs.DefaultPreferencessProvider
import com.crazylegend.vigilante.di.providers.prefs.logging.LoggingPrefs
import com.crazylegend.vigilante.notifications.db.NotificationsDAO
import com.crazylegend.vigilante.notifications.db.NotificationsModel
import dagger.hilt.android.scopes.ServiceScoped
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch

/**
 * Created by crazy on 10/20/20 to long live and prosper !
 */
@ServiceScoped
class NotificationsProvider @Inject constructor(
    private val prefsProvider: DefaultPreferencessProvider,
    private val loggingPrefs: LoggingPrefs,
    private val notificationsRepo: NotificationsDAO,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ServiceLifecycle {

    fun processEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            val notification = event.parcelableData as? Notification
            val extras = notification?.extras
            val title = extras?.getString("android.title", null)

            val bigText = tryOrElse(extras?.get("android.bigText") as? SpannableString) {
                extras?.getString("android.bigText", null)
            }
            val text = tryOrElse(extras?.get("android.text") as? SpannableString) {
                extras?.getString("android.text", null)
            }
            val visibility = notification?.visibility
            val category = notification?.category
            val color = notification?.color
            val flags = notification?.flags
            val group = notification?.group
            val channelId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                notification?.channelId
            } else {
                null
            }
            val sentByPackage = event.packageName?.toString()
            val notificationModel = NotificationsModel(
                title,
                bigText?.toString(),
                text?.toString(),
                visibility,
                category,
                color,
                flags,
                group,
                channelId,
                sentByPackage,
                Date(currentTimeMillis)
            )

            if (prefsProvider.isVigilanteExcludedFromNotifications && sentByPackage == BuildConfig.APPLICATION_ID) {
                //DO SOMETHING IN THE FUTURE MAYBE, like separate sections idk
            } else {
                //check if user has enabled discarding empty notifications
                if (!loggingPrefs.isEmptyNotificationsLoggingEnabled
                    && (notificationModel.title.isNullOrBlank() or notificationModel.bigText.isNullOrBlank())
                ) return

                saveNotification(notificationModel)
            }
        }
    }

    private fun saveNotification(notificationModel: NotificationsModel) {
        scope.launch(ioDispatcher) {
            notificationsRepo.insertNotification(notificationModel)
        }
    }

    override val serviceLifecycleDispatcher: ServiceLifecycleDispatcher =
        ServiceLifecycleDispatcher(this)

    override fun initVars() {}

    override fun registerCallbacks() {}

    override fun disposeResources() {}


}