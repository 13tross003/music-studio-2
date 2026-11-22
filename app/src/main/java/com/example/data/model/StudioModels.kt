package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class SpeakerRole {
    PRODUCER,
    ENGINEER,
    LYRICIST;

    companion object {
        fun fromRaw(raw: String?): SpeakerRole =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: PRODUCER
    }
}

enum class StemType(val label: String, val fileSuffix: String) {
    FULL_MIX("Master Mix", "master"),
    DRUMS("Drums Stem", "drums"),
    SUB_BASS("Sub-Bass 808", "sub_bass"),
    MELODY("Melody & Keys", "melody"),
    VOCAL_GUIDE("Vocal Guide", "vocal_guide")
}

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

object DefaultPatterns {
    val kick = listOf(124L, 0L, 0L, 0L, 0L, 0L, 96L, 0L, 0L, 0L, 118L, 0L, 0L, 84L, 0L, 0L)
    val snare = listOf(0L, 0L, 0L, 0L, 122L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 124L, 0L, 0L, 48L)
    val hihat = listOf(96L, 52L, 84L, 50L, 98L, 54L, 88L, 70L, 96L, 52L, 84L, 50L, 98L, 76L, 108L, 64L)
    val bass = listOf(36L, 0L, 0L, 0L, 0L, 0L, 36L, 0L, 43L, 0L, 41L, 0L, 39L, 0L, 36L, 0L)
    val melody = listOf(60L, 0L, 63L, 0L, 67L, 0L, 0L, 63L, 70L, 0L, 67L, 0L, 63L, 0L, 60L, 0L)
    val vocalGuide = listOf(72L, 0L, 0L, 75L, 0L, 72L, 0L, 0L, 79L, 0L, 75L, 0L, 72L, 0L, 0L, 0L)
}

@Keep
@IgnoreExtraProperties
data class StudioSession(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "Midnight Hologram",
    val genreMood: String = "Atmospheric Trap Soul",
    val musicalKey: String = "C Minor",
    val bpm: Long = 92L,
    val hpfHz: Long = 90L,
    val subBoostDb: Double = 4.5,
    val airPresenceDb: Double = 3.0,
    val saturation: Double = 0.25,
    val compression: Double = 0.45,
    val swing: Double = 0.08,
    val kickPattern: List<Long> = DefaultPatterns.kick,
    val snarePattern: List<Long> = DefaultPatterns.snare,
    val hihatPattern: List<Long> = DefaultPatterns.hihat,
    val bassNotes: List<Long> = DefaultPatterns.bass,
    val melodyNotes: List<Long> = DefaultPatterns.melody,
    val vocalGuideNotes: List<Long> = DefaultPatterns.vocalGuide,
    val activeLyrics: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun normalized(): StudioSession = copy(
        title = title.trim().ifBlank { "Untitled Session" }.take(100),
        genreMood = genreMood.trim().ifBlank { "Quantum Soul" }.take(100),
        musicalKey = musicalKey.trim().ifBlank { "C Minor" }.take(30),
        bpm = bpm.coerceIn(50L, 220L),
        hpfHz = hpfHz.coerceIn(20L, 350L),
        subBoostDb = subBoostDb.coerceIn(0.0, 12.0),
        airPresenceDb = airPresenceDb.coerceIn(-6.0, 9.0),
        saturation = saturation.coerceIn(0.0, 1.0),
        compression = compression.coerceIn(0.0, 1.0),
        swing = swing.coerceIn(0.0, 0.5),
        kickPattern = normalizeStepList(kickPattern, 0L, 127L),
        snarePattern = normalizeStepList(snarePattern, 0L, 127L),
        hihatPattern = normalizeStepList(hihatPattern, 0L, 127L),
        bassNotes = normalizeStepList(bassNotes, 0L, 84L),
        melodyNotes = normalizeStepList(melodyNotes, 0L, 96L),
        vocalGuideNotes = normalizeStepList(vocalGuideNotes, 0L, 96L),
        activeLyrics = activeLyrics.take(6000)
    )

    fun toCreateMap(ownerUid: String): Map<String, Any> {
        val clean = normalized()
        return mapOf(
            "userId" to ownerUid,
            "title" to clean.title,
            "genreMood" to clean.genreMood,
            "musicalKey" to clean.musicalKey,
            "bpm" to clean.bpm,
            "hpfHz" to clean.hpfHz,
            "subBoostDb" to clean.subBoostDb,
            "airPresenceDb" to clean.airPresenceDb,
            "saturation" to clean.saturation,
            "compression" to clean.compression,
            "swing" to clean.swing,
            "kickPattern" to clean.kickPattern,
            "snarePattern" to clean.snarePattern,
            "hihatPattern" to clean.hihatPattern,
            "bassNotes" to clean.bassNotes,
            "melodyNotes" to clean.melodyNotes,
            "vocalGuideNotes" to clean.vocalGuideNotes,
            "activeLyrics" to clean.activeLyrics,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }

    fun toUpdateMixMap(): Map<String, Any> {
        val clean = normalized()
        return mapOf(
            "title" to clean.title,
            "genreMood" to clean.genreMood,
            "musicalKey" to clean.musicalKey,
            "bpm" to clean.bpm,
            "hpfHz" to clean.hpfHz,
            "subBoostDb" to clean.subBoostDb,
            "airPresenceDb" to clean.airPresenceDb,
            "saturation" to clean.saturation,
            "compression" to clean.compression,
            "swing" to clean.swing,
            "kickPattern" to clean.kickPattern,
            "snarePattern" to clean.snarePattern,
            "hihatPattern" to clean.hihatPattern,
            "bassNotes" to clean.bassNotes,
            "melodyNotes" to clean.melodyNotes,
            "vocalGuideNotes" to clean.vocalGuideNotes,
            "activeLyrics" to clean.activeLyrics,
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }

    companion object {
        private fun normalizeStepList(input: List<Long>, min: Long, max: Long): List<Long> {
            val out = MutableList(16) { 0L }
            for (i in 0 until minOf(16, input.size)) {
                out[i] = input[i].coerceIn(min, max)
            }
            return out
        }
    }
}

@Keep
@IgnoreExtraProperties
data class StudioMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val speaker: String = SpeakerRole.PRODUCER.name,
    val text: String = "",
    val sampleReference: String? = null,
    val lyricSection: String? = null,
    val mixSummary: String? = null,
    val hasStemAttachment: Boolean = false,
    val stemTitle: String? = null,
    val stemBpm: Long? = null,
    val stemKey: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val role: SpeakerRole get() = SpeakerRole.fromRaw(speaker)

    fun toCreateMap(ownerUid: String): Map<String, Any> {
        val cleanSpeaker = SpeakerRole.fromRaw(speaker).name
        val cleanText = text.trim().ifBlank { "..." }.take(6000)
        val raw = mutableMapOf<String, Any?>(
            "userId" to ownerUid,
            "speaker" to cleanSpeaker,
            "text" to cleanText,
            "sampleReference" to sampleReference?.trim()?.takeIf { it.isNotBlank() }?.take(300),
            "lyricSection" to lyricSection?.trim()?.takeIf { it.isNotBlank() }?.take(4000),
            "mixSummary" to mixSummary?.trim()?.takeIf { it.isNotBlank() }?.take(400),
            "hasStemAttachment" to hasStemAttachment,
            "stemTitle" to stemTitle?.trim()?.takeIf { it.isNotBlank() }?.take(140),
            "stemBpm" to stemBpm?.coerceIn(50L, 220L),
            "stemKey" to stemKey?.trim()?.takeIf { it.isNotBlank() }?.take(30),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        @Suppress("UNCHECKED_CAST")
        return raw.filterValues { it != null } as Map<String, Any>
    }
}

data class StudioTurnResult(
    val engineerMessage: String,
    val engineerSampleReference: String?,
    val lyricistMessage: String?,
    val lyricistSampleReference: String?,
    val lyricBars: String?,
    val shouldUpdateBeat: Boolean,
    val sessionTitle: String,
    val genreMood: String,
    val musicalKey: String,
    val bpm: Long,
    val hpfHz: Long,
    val subBoostDb: Double,
    val airPresenceDb: Double,
    val saturation: Double,
    val compression: Double,
    val swing: Double,
    val kickPattern: List<Long>,
    val snarePattern: List<Long>,
    val hihatPattern: List<Long>,
    val bassNotes: List<Long>,
    val melodyNotes: List<Long>,
    val vocalGuideNotes: List<Long>,
    val stemPackTitle: String,
    val mixEngineeringNotes: String
)
