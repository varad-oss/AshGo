package com.varad.unstoppableash.data.firebase

enum class Receipt(val node: String) { DELIVERED("deliveredTo"), SEEN("seenBy"), ACK("ackBy") }

/** The only place database paths are spelled (ARCHITECTURE_PROPOSAL §5). */
object DbPaths {
    const val CONNECTED = ".info/connected"
    const val SERVER_TIME_OFFSET = ".info/serverTimeOffset"

    fun userPair(uid: String) = "userPairs/$uid"
    fun invites(emailKey: String) = "invites/$emailKey"
    fun invite(emailKey: String, pairId: String) = "invites/$emailKey/$pairId"

    fun pair(pairId: String) = "pairs/$pairId"
    fun meta(pairId: String) = "${pair(pairId)}/meta"
    fun profiles(pairId: String) = "${pair(pairId)}/profiles"
    fun profile(pairId: String, uid: String) = "${profiles(pairId)}/$uid"
    fun presence(pairId: String, uid: String) = "${pair(pairId)}/presence/$uid"
    fun location(pairId: String, uid: String) = "${pair(pairId)}/location/$uid"
    fun trip(pairId: String, uid: String) = "${pair(pairId)}/trips/$uid"
    fun chat(pairId: String) = "${pair(pairId)}/chat"
    fun chatMessage(pairId: String, id: String) = "${chat(pairId)}/$id"
    fun media(pairId: String, id: String) = "${pair(pairId)}/media/$id"
    fun events(pairId: String) = "${pair(pairId)}/events"
    fun event(pairId: String, id: String) = "${events(pairId)}/$id"
    fun eventReceipt(pairId: String, id: String, receipt: Receipt, uid: String) = "${event(pairId, id)}/${receipt.node}/$uid"
    fun sosAll(pairId: String) = "${pair(pairId)}/sos"
    fun sos(pairId: String, id: String) = "${sosAll(pairId)}/$id"
    fun sosReceipt(pairId: String, id: String, receipt: Receipt, uid: String) = "${sos(pairId, id)}/${receipt.node}/$uid"
}

/** Must match the rules: `auth.token.email.toLowerCase().replace('.', ',')`. */
fun emailKey(email: String): String = email.trim().lowercase().replace(".", ",")
