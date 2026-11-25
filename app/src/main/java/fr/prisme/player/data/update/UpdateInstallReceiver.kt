package fr.prisme.player.data.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import fr.prisme.player.PrismApp

/**
 * Receives PackageInstaller session callbacks. PENDING_USER_ACTION means Android wants to
 * show its confirmation screen: we launch it. Everything else is a final outcome.
 */
class UpdateInstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val confirm = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_INTENT)
            }
            if (confirm != null) {
                context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            }
        }
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        PrismApp.get().updater.onInstallResult(status, message)
    }
}
