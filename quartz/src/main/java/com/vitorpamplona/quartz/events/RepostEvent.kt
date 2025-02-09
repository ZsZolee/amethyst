package com.vitorpamplona.quartz.events

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.encoders.HexKey

@Immutable
class RepostEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : Event(id, pubKey, createdAt, kind, tags, content, sig) {

    fun boostedPost() = taggedEvents()
    fun originalAuthor() = taggedUsers()

    fun containedPost() = try {
        fromJson(content)
    } catch (e: Exception) {
        null
    }

    companion object {
        const val kind = 6

        fun create(boostedPost: EventInterface, privateKey: ByteArray, createdAt: Long = TimeUtils.now()): RepostEvent {
            val content = boostedPost.toJson()

            val replyToPost = listOf("e", boostedPost.id())
            val replyToAuthor = listOf("p", boostedPost.pubKey())

            val pubKey = CryptoUtils.pubkeyCreate(privateKey).toHexKey()
            var tags: List<List<String>> = listOf(replyToPost, replyToAuthor)

            if (boostedPost is AddressableEvent) {
                tags = tags + listOf(listOf("a", boostedPost.address().toTag()))
            }

            val id = generateId(pubKey, createdAt, kind, tags, content)
            val sig = CryptoUtils.sign(id, privateKey)
            return RepostEvent(id.toHexKey(), pubKey, createdAt, tags, content, sig.toHexKey())
        }
    }
}
