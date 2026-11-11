package com.farmeraccounts.presentation.base

import android.view.View
import android.widget.PopupMenu
import androidx.fragment.app.Fragment
import com.farmeraccounts.R

fun Fragment.showRecordActions(anchor: View, kind: RecordKind, id: String, label: String) {
    PopupMenu(requireContext(), anchor).apply {
        menu.add(R.string.btn_delete_record).setOnMenuItemClickListener {
            DeleteRecordDialog.show(this@showRecordActions, kind, id, label)
            true
        }
    }.show()
}
