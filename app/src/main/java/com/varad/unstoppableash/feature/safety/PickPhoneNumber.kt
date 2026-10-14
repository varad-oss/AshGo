package com.varad.unstoppableash.feature.safety

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.provider.ContactsContract
import androidx.activity.result.contract.ActivityResultContract

data class PickedNumber(val name: String, val number: String)

/**
 * The system contact picker filtered to phone numbers. Android grants read access to the one row the
 * person picks, so AshGo never needs READ_CONTACTS (REDESIGN_SPEC §EmergencyContacts).
 */
class PickPhoneNumber : ActivityResultContract<Unit, PickedNumber?>() {
    private lateinit var context: Context

    override fun createIntent(context: Context, input: Unit): Intent {
        this.context = context.applicationContext
        return Intent(Intent.ACTION_PICK).setType(ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): PickedNumber? {
        val uri = intent?.data?.takeIf { resultCode == Activity.RESULT_OK } ?: return null
        val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER)
        return runCatching {
            context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
                if (!c.moveToFirst()) null else PickedNumber(c.getString(0).orEmpty(), c.getString(1).orEmpty()).takeIf { it.number.isNotBlank() }
            }
        }.getOrNull()
    }
}
