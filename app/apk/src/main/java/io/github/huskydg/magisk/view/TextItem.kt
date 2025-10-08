package io.github.huskydg.magisk.view

import io.github.huskydg.magisk.R
import io.github.huskydg.magisk.databinding.DiffItem
import io.github.huskydg.magisk.databinding.ItemWrapper
import io.github.huskydg.magisk.databinding.RvItem

class TextItem(override val item: Int) : RvItem(), DiffItem<TextItem>, ItemWrapper<Int> {
    override val layoutRes = R.layout.item_text
}
