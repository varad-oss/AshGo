package com.varad.unstoppableash.domain.model

data class ChatMessage(val id: String, val from: String, val text: String?, val mediaId: String?, val at: Long)
