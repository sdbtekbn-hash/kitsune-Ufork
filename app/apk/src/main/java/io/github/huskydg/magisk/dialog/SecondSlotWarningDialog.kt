package io.github.huskydg.magisk.dialog

import io.github.huskydg.magisk.core.R
import io.github.huskydg.magisk.events.DialogBuilder
import io.github.huskydg.magisk.view.MagiskDialog

class SecondSlotWarningDialog : DialogBuilder {

    override fun build(dialog: MagiskDialog) {
        dialog.apply {
            setTitle(android.R.string.dialog_alert_title)
            setMessage(R.string.install_inactive_slot_msg)
            setButton(MagiskDialog.ButtonType.POSITIVE) {
                text = android.R.string.ok
            }
            setCancelable(true)
        }
    }
}
