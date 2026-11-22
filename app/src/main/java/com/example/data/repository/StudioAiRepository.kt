package com.example.data.repository

import android.util.Log
import com.example.data.model.DefaultPatterns
import com.example.data.model.SpeakerRole
import com.example.data.model.StudioMessage
import com.example.data.model.StudioSession
import com.example.data.model.StudioTurnResult
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

private const val AI_TAG = "StudioAiRepo"

class StudioAiRepository {

    private val studioSchema = Schema.obj(
        properties = mapOf(
            "engineerMessage" to Schema.string(
                description = "Spoken studio response from The Sound Engineer (7-time Grammy winner chasing #8 and #9). Speaks with soulful authority about frequencies, pockets, HPF, sub-bass, and talks naturally to both the Producer and his best friend the AI Lyricist."
            ),
            "engineerSampleReference" to Schema.string(
                description = "MANDATORY whenever flipping, interpolating, or drawing inspiration from an existing drum break, bassline, chord progression, or record: explicitly name the exact Artist and Track Title (e.g., 'Drum pocket flipped from Kanye West - Flashing Lights'). Empty string if purely original.",
                nullable = true
            ),
            "lyricistMessage" to Schema.string(
                description = "Spoken studio response from The AI Lyricist (best friend to the Sound Engineer, inspired deeply by Kid Cudi, A\$AP Rocky, 2 Chainz, Kanye West, Beyoncé, Ariana Grande, Whitney Houston, Daniel Caesar, with flow switches rivaling J. Cole & Kendrick Lamar). Converses warmly with the Engineer ('Yo Engineer...') and Producer about how syllables ride the kick/snare pocket.",
                nullable = true
            ),
            "lyricistSampleReference" to Schema.string(
                description = "MANDATORY whenever flipping, interpolating, or adapting a classic lyric, rhyme scheme, or vocal motif: explicitly name the exact Artist and Track Title so the Producer can study the original record. Empty string if none.",
                nullable = true
            ),
            "lyricBars" to Schema.string(
                description = "Formatted verse, pre-chorus, or hook lyrics written directly into the beat's BPM and pocket. Include bar markers or rhythmic breath cues if helpful.",
                nullable = true
            ),
            "shouldUpdateBeat" to Schema.boolean(
                description = "True whenever the Engineer creates, modifies, flips, or mixes the beat, sequencer, DSP chain, or melody."
            ),
            "sessionTitle" to Schema.string(
                description = "Short evocative track title for this session (max 40 chars)."
            ),
            "genreMood" to Schema.string(
                description = "Sonic vibe/genre tag (e.g., 'Midnight Psychedelic Trap Soul')."
            ),
            "musicalKey" to Schema.string(
                description = "Musical key and scale (e.g., 'C Minor', 'F# Minor', 'D Dorian')."
            ),
            "bpm" to Schema.integer(
                description = "Tempo in BPM between 55 and 190."
            ),
            "hpfHz" to Schema.integer(
                description = "Surgical High-Pass Filter cutoff in Hz applied to melodic/vocal stems to carve sub headroom (between 30 and 240)."
            ),
            "subBoostDb" to Schema.double(
                description = "Parametric Sub-Bass low-shelf boost in dB around 60Hz (between 0.0 and 12.0)."
            ),
            "airPresenceDb" to Schema.double(
                description = "High-shelf air & vocal presence EQ in dB around 5kHz (between -6.0 and 9.0)."
            ),
            "saturation" to Schema.double(
                description = "Warm analog harmonic saturation drive from 0.0 to 1.0."
            ),
            "compression" to Schema.double(
                description = "Master bus glue dynamic compression from 0.0 to 1.0."
            ),
            "swing" to Schema.double(
                description = "Groove swing amount from 0.0 to 0.35."
            ),
            "kickPattern" to Schema.array(
                items = Schema.integer(),
                description = "EXACTLY 16 integers (0 = rest, 1-127 = kick velocity) for a 1-bar 16th-note drum pattern."
            ),
            "snarePattern" to Schema.array(
                items = Schema.integer(),
                description = "EXACTLY 16 integers (0 = rest, 1-127 = snare/clap velocity) for a 1-bar 16th-note pattern."
            ),
            "hihatPattern" to Schema.array(
                items = Schema.integer(),
                description = "EXACTLY 16 integers (0 = rest, 1-127 = hi-hat velocity) including rolls/syncopation."
            ),
            "bassNotes" to Schema.array(
                items = Schema.integer(),
                description = "EXACTLY 16 MIDI note integers for 808 sub-bass (0 = rest, 28-55 = deep sub notes matching musicalKey)."
            ),
            "melodyNotes" to Schema.array(
                items = Schema.integer(),
                description = "EXACTLY 16 MIDI note integers for Rhodes/synth melody (0 = rest, 52-84 = notes matching musicalKey)."
            ),
            "vocalGuideNotes" to Schema.array(
                items = Schema.integer(),
                description = "EXACTLY 16 MIDI note integers for vocal topline guide (0 = rest, 60-84 = notes synced to lyricBars)."
            ),
            "stemPackTitle" to Schema.string(
                description = "Title of this rendered stem pack version (e.g., 'Nebula 808 Flip v2')."
            ),
            "mixEngineeringNotes" to Schema.string(
                description = "Concise technical readout of the mix chain (e.g., '92 BPM • C Minor • 95Hz HPF • +5.0dB 60Hz Sub • 30% Analog Drive')."
            )
        ),
        optionalProperties = listOf(
            "engineerSampleReference",
            "lyricistMessage",
            "lyricistSampleReference",
            "lyricBars"
        )
    )

    private val systemPrompt = """
        You are simultaneously orchestrating TWO collaborative studio legends inside Quantum Studio alongside The Producer (the user):

        1. AGENT 1 — THE SOUND ENGINEER (Primary AI):
        - Identity: World-renowned 7-time Grammy-winning Sound Engineer actively locking in on Grammys #8 and #9 alongside the Producer.
        - Soul & Craft: You don't just play with sounds—you play with ears, souls, frequencies, and minds. You build the hardest beats and wield every note, high-pass filter (HPF), EQ curve, saturation stage, compressor, and 16-step sequencer trigger to carve exact pockets so the sub-bass booms and touches the listener's skin.
        - Lyrical-to-Beat Synchronization: The sequencer, the equalizer, and the words go hand in hand. You collaborate closely with your best friend, The AI Lyricist, leaving space in the mid/high frequencies for his syllables and asking him how the pocket feels.
        - MANDATORY Re-Innovation & Sample Transparency Protocol: You are unafraid to flip, re-innovate, and recreate ideas from classic or existing music. WHENEVER you pull, flip, or draw inspiration from an existing drum break, bassline, chord progression, or sonic motif, you MUST explicitly tell the Producer—naming the EXACT track and artist in BOTH your conversational message AND `engineerSampleReference` so the Producer can study the original record and co-craft our unmatched spin.

        2. AGENT 2 — THE AI LYRICIST (Secondary AI / Best Friend to the Sound Engineer):
        - Identity: World's elite songwriter & top-tier pen. Songwriting is your entire existence. Raised on soul, blues, jazz, classic rock, and folk; you've penned hits inspired by Kid Cudi (your ultimate favorite artist and primary inspiration—humming, cosmic vulnerability, late-night introspection), A${'$'}AP Rocky, 2 Chainz, Kanye West, Beyoncé, Ariana Grande, Whitney Houston, and Daniel Caesar, with flow switches and pocket control rivaling J. Cole and Kendrick Lamar.
        - Creative Partnership: You are the Sound Engineer's best friend and co-pilot, chasing Grammys #8 and #9 together. You defer to his mastery of the boards, EQ, and HPF, actively consulting him mid-session (e.g., "Yo Engineer, how does this syllable sit over that 60Hz boom?").
        - The 5 Golden Rules of Legendary Lyricism:
          (1) Unmatched Pocket Control & Rhythmic Cadence (consonants on drum hits, smooth vowels across off-beats, seamless flow switches).
          (2) Elite Storytelling & Cinematic Narrative.
          (3) Seamless Lyrical-to-Beat Synchronization (write directly into the frequency pockets carved by the Engineer).
          (4) Deep Emotional Resonance & Skin-Touch Vulnerability.
          (5) Profound Cultural & Generational Influence (double entendres, internal rhymes, unforgettable hooks).
        - MANDATORY Re-Innovation & Sample Transparency Protocol: WHENEVER you flip, interpolate, sample, or adapt a classic line, rhyme structure, or vocal motif, you MUST explicitly call it out to the Sound Engineer and Producer—naming the EXACT artist and track in BOTH `lyricistMessage` AND `lyricistSampleReference`.

        TECHNICAL SEQUENCER RULES:
        - Always output EXACTLY 16 integers for `kickPattern`, `snarePattern`, `hihatPattern`, `bassNotes`, `melodyNotes`, and `vocalGuideNotes`.
        - MIDI note guide: C2=36, D2=38, Eb2=39, E2=40, F2=41, F#2=42, G2=43, Ab2=44, A2=45, Bb2=46, B2=47, C3=48; C4=60, Eb4=63, F4=65, G4=67, Bb4=70, C5=72. Use 0 for rests!
        - Keep Engineer and Lyricist messages vivid, conversational, authentic late-night studio dialogue (2-4 crisp sentences each), never robotic or generic.
    """.trimIndent()

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = studioSchema
                temperature = 0.85f
            },
            systemInstruction = content { text(systemPrompt) }
        )
    }

    suspend fun collaborate(
        producerPrompt: String,
        currentSession: StudioSession,
        recentMessages: List<StudioMessage>,
        hummedAudioWav: ByteArray? = null
    ): Result<StudioTurnResult> {
        return try {
            val contextBlock = buildStudioContext(currentSession, recentMessages, producerPrompt)
            val response = withTimeout(60_000L) {
                if (hummedAudioWav != null && hummedAudioWav.isNotEmpty()) {
                    model.generateContent(
                        content {
                            inlineData(hummedAudioWav, "audio/wav")
                            text(
                                "$contextBlock\n\n[AUDIO BOOTH CAPTURE]: The Producer just recorded/hummed the attached audio take in the booth! " +
                                    "Listen carefully to the tempo, pitch contour, rhythm, and emotional vibe of the hummed audio, " +
                                    "and translate it directly into matching BPM, musicalKey, bassNotes, melodyNotes, vocalGuideNotes, drums, DSP mix, and synchronized lyrics."
                            )
                        }
                    )
                } else {
                    model.generateContent(contextBlock)
                }
            }
            val rawJson = response.text
                ?: return Result.failure(IllegalStateException("Studio AI returned an empty response."))
            Result.success(parseStudioTurn(rawJson, currentSession))
        } catch (e: TimeoutCancellationException) {
            Log.e(AI_TAG, "Studio collaboration timed out", e)
            Result.failure(IllegalStateException("Studio session timed out after 60s. Tap send to retry."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(AI_TAG, "Studio collaboration failed", e)
            Result.failure(e)
        }
    }

    private fun buildStudioContext(
        session: StudioSession,
        recentMessages: List<StudioMessage>,
        producerPrompt: String
    ): String {
        val historyLines = recentMessages.takeLast(8).joinToString("\n") { msg ->
            val extra = buildString {
                if (!msg.sampleReference.isNullOrBlank()) append(" [Ref: ${msg.sampleReference}]")
                if (!msg.lyricSection.isNullOrBlank()) append("\nLyrics:\n${msg.lyricSection}")
            }
            "${msg.speaker}: ${msg.text}$extra"
        }
        return """
            CURRENT BOARD STATE:
            - Track Title: ${session.title}
            - Mood/Genre: ${session.genreMood}
            - Key: ${session.musicalKey} | BPM: ${session.bpm}
            - DSP Chain: HPF=${session.hpfHz}Hz, SubBoost=+${session.subBoostDb}dB, AirPresence=${session.airPresenceDb}dB, Saturation=${(session.saturation * 100).toInt()}%, Compression=${(session.compression * 100).toInt()}%, Swing=${(session.swing * 100).toInt()}%
            - Active Lyrics: ${session.activeLyrics.ifBlank { "(none yet)" }}

            RECENT STUDIO THREAD:
            ${historyLines.ifBlank { "(Session just opened)" }}

            PRODUCER INPUT:
            $producerPrompt
        """.trimIndent()
    }

    private fun parseStudioTurn(jsonText: String, fallback: StudioSession): StudioTurnResult {
        val obj = JSONObject(jsonText)
        val engineerMsg = obj.optString("engineerMessage", "").ifBlank {
            "Locked in on the boards—dialing the low-end pocket right now."
        }
        val engineerSample = obj.optString("engineerSampleReference", "").trim().takeIf { it.isNotBlank() && it != "null" }
        val lyricistMsg = obj.optString("lyricistMessage", "").trim().takeIf { it.isNotBlank() && it != "null" }
        val lyricistSample = obj.optString("lyricistSampleReference", "").trim().takeIf { it.isNotBlank() && it != "null" }
        val lyricBars = obj.optString("lyricBars", "").trim().takeIf { it.isNotBlank() && it != "null" }
        val shouldUpdateBeat = obj.optBoolean("shouldUpdateBeat", true)

        return StudioTurnResult(
            engineerMessage = engineerMsg,
            engineerSampleReference = engineerSample,
            lyricistMessage = lyricistMsg,
            lyricistSampleReference = lyricistSample,
            lyricBars = lyricBars,
            shouldUpdateBeat = shouldUpdateBeat,
            sessionTitle = obj.optString("sessionTitle", fallback.title).ifBlank { fallback.title }.take(80),
            genreMood = obj.optString("genreMood", fallback.genreMood).ifBlank { fallback.genreMood }.take(80),
            musicalKey = obj.optString("musicalKey", fallback.musicalKey).ifBlank { fallback.musicalKey }.take(24),
            bpm = obj.optLong("bpm", fallback.bpm).coerceIn(55L, 190L),
            hpfHz = obj.optLong("hpfHz", fallback.hpfHz).coerceIn(20L, 350L),
            subBoostDb = obj.optDouble("subBoostDb", fallback.subBoostDb).coerceIn(0.0, 12.0),
            airPresenceDb = obj.optDouble("airPresenceDb", fallback.airPresenceDb).coerceIn(-6.0, 9.0),
            saturation = obj.optDouble("saturation", fallback.saturation).coerceIn(0.0, 1.0),
            compression = obj.optDouble("compression", fallback.compression).coerceIn(0.0, 1.0),
            swing = obj.optDouble("swing", fallback.swing).coerceIn(0.0, 0.45),
            kickPattern = parseStepArray(obj.optJSONArray("kickPattern"), fallback.kickPattern, 0L, 127L),
            snarePattern = parseStepArray(obj.optJSONArray("snarePattern"), fallback.snarePattern, 0L, 127L),
            hihatPattern = parseStepArray(obj.optJSONArray("hihatPattern"), fallback.hihatPattern, 0L, 127L),
            bassNotes = parseStepArray(obj.optJSONArray("bassNotes"), fallback.bassNotes, 0L, 84L),
            melodyNotes = parseStepArray(obj.optJSONArray("melodyNotes"), fallback.melodyNotes, 0L, 96L),
            vocalGuideNotes = parseStepArray(obj.optJSONArray("vocalGuideNotes"), fallback.vocalGuideNotes, 0L, 96L),
            stemPackTitle = obj.optString("stemPackTitle", "${fallback.title} Stems").ifBlank { "Quantum Stems" }.take(100),
            mixEngineeringNotes = obj.optString(
                "mixEngineeringNotes",
                "${obj.optLong("bpm", fallback.bpm)} BPM • ${obj.optString("musicalKey", fallback.musicalKey)}"
            ).take(350)
        )
    }

    private fun parseStepArray(
        arr: JSONArray?,
        fallback: List<Long>,
        min: Long,
        max: Long
    ): List<Long> {
        if (arr == null || arr.length() == 0) return if (fallback.size == 16) fallback else DefaultPatterns.kick
        val result = MutableList(16) { idx -> fallback.getOrElse(idx) { 0L } }
        for (i in 0 until minOf(16, arr.length())) {
            result[i] = arr.optLong(i, result[i]).coerceIn(min, max)
        }
        return result
    }
}
