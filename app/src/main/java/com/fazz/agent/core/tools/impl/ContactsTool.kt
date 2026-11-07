package com.fazz.agent.core.tools.impl

import android.Manifest
import android.content.Context
import android.provider.ContactsContract
import com.fazz.agent.core.common.bool
import com.fazz.agent.core.common.jsonOf
import com.fazz.agent.core.common.string
import com.fazz.agent.core.tools.AgentTool
import com.fazz.agent.core.tools.ConfirmationRequest
import com.fazz.agent.core.tools.ErrorCodes
import com.fazz.agent.core.tools.ToolResult
import com.fazz.agent.core.tools.schema
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

/**
 * TOOL 4: look up contacts by name. Data minimization: it only searches by a name the user gave,
 * returns at most 5 matches, and includes phone numbers only when asked. Requires READ_CONTACTS
 * and an explicit confirmation every time, because results are sent to Gemini.
 */
class ContactsTool(private val context: Context) : AgentTool {
    override val name = "search_contacts"
    override val description = "Find a contact on the phone by (part of) their name. Returns up to 5 matches. " +
        "Only set include_phone when the user actually needs the number."
    override val activityLabel = "Mencari kontak"
    override val requiredPermissions = listOf(Manifest.permission.READ_CONTACTS)
    override val permissionRationale = "Fazz butuh akses kontak untuk mencari orang yang kamu tanyakan. Hanya nama yang kamu sebut yang dicari."
    override val parameters = schema {
        string("name_query", "Name or part of the name to search for.", required = true, maxLength = 60)
        boolean("include_phone", "Whether to include phone numbers in the result.")
    }

    override fun validate(args: JsonObject): String? =
        if ((args.string("name_query")?.trim()?.length ?: 0) < 2) "name_query must have at least 2 characters" else null

    override fun confirmation(args: JsonObject): ConfirmationRequest {
        val phone = args.bool("include_phone") == true
        return ConfirmationRequest(
            title = "Bagikan info kontak ke Gemini?",
            message = "Fazz akan mencari \"${args.string("name_query")}\" di kontakmu dan mengirim maksimal 5 nama yang cocok" +
                (if (phone) " beserta nomor teleponnya" else "") + " ke Gemini.",
        )
    }

    override suspend fun execute(args: JsonObject): ToolResult = withContext(Dispatchers.IO) {
        val query = args.string("name_query")!!.trim()
        val includePhone = args.bool("include_phone") == true
        val resolver = context.contentResolver
        val results = mutableListOf<Map<String, Any?>>()
        val like = "%" + query.replace("%", "").replace("_", "") + "%"

        resolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY, ContactsContract.Contacts.HAS_PHONE_NUMBER),
            "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} LIKE ?",
            arrayOf(like),
            "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC",
        )?.use { c ->
            while (c.moveToNext() && results.size < 5) {
                val id = c.getLong(0)
                val name = c.getString(1) ?: continue
                val phones = if (includePhone && c.getInt(2) > 0) phonesFor(id) else emptyList()
                results += buildMap {
                    put("name", name)
                    if (includePhone) put("phone_numbers", phones)
                }
            }
        } ?: return@withContext ToolResult.error(ErrorCodes.UNAVAILABLE, "Kontak tidak tersedia di HP ini.")

        if (results.isEmpty()) ToolResult.error(ErrorCodes.NOT_FOUND, "Tidak ada kontak bernama '$query'.")
        else ToolResult.ok("${results.size} kontak ditemukan", jsonOf("contacts" to results))
    }

    private fun phonesFor(contactId: Long): List<String> {
        val out = mutableListOf<String>()
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId.toString()),
            null,
        )?.use { c -> while (c.moveToNext() && out.size < 3) c.getString(0)?.let { out += it } }
        return out.distinct()
    }
}
