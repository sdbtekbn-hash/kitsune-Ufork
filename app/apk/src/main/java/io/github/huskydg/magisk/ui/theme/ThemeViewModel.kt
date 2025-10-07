package io.github.huskydg.magisk.ui.theme

import io.github.huskydg.magisk.arch.BaseViewModel
import io.github.huskydg.magisk.core.Config
import io.github.huskydg.magisk.dialog.DarkThemeDialog
import io.github.huskydg.magisk.events.RecreateEvent
import io.github.huskydg.magisk.view.TappableHeadlineItem

class ThemeViewModel : BaseViewModel(), TappableHeadlineItem.Listener {

    val themeHeadline = TappableHeadlineItem.ThemeMode

    override fun onItemPressed(item: TappableHeadlineItem) = when (item) {
        is TappableHeadlineItem.ThemeMode -> DarkThemeDialog().show()
    }

    fun saveTheme(theme: Theme) {
        if (!theme.isSelected) {
            Config.themeOrdinal = theme.ordinal
            RecreateEvent().publish()
        }
    }
}
