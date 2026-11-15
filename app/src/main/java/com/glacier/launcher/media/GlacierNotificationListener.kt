package com.glacier.launcher.media

import android.service.notification.NotificationListenerService

/**
 * Intentionally empty. Android only lets an app read other apps' media sessions
 * (MediaSessionManager.getActiveSessions) if it owns an enabled NotificationListenerService.
 * We never read, store or forward any notification content.
 */
class GlacierNotificationListener : NotificationListenerService()
