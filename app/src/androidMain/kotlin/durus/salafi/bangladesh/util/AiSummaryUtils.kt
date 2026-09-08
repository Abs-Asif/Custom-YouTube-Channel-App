package durus.salafi.bangladesh.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.stream.StreamExtractor
import org.schabi.newpipe.extractor.stream.SubtitlesStream
import java.util.concurrent.TimeUnit

enum class AiSummaryStep(val stepNumber: Int, val description: String) {
    FETCHING_TRANSCRIPT(1, "ভিডিও ট্রান্সক্রিপ্ট লোড করা হচ্ছে..."),
    CLEANING_TRANSCRIPT(2, "ক্যাপশন পরিশোধিত ও সাজানো হচ্ছে..."),
    SENDING_REQUEST(3, "এআই সার্ভারে অনুরোধ পাঠানো হচ্ছে..."),
    PROCESSING_RESPONSE(4, "এআই প্রতিক্রিয়া সুবিন্যস্ত করা হচ্ছে...")
}

object AiSummaryUtils {

    private val MASK_BYTES = intArrayOf(
        55, 30, 95, 26, 1, 126, 23, 93, 76, 86, 91, 113, 116, 127, 1, 31,
        2, 86, 1, 3, 114, 65, 20, 71, 69, 102, 7, 95, 86, 87, 8, 35, 37,
        115, 93, 72, 10, 83, 87, 13, 38, 68, 16, 67, 69, 106, 81, 95, 5, 85,
        91, 117, 117, 114, 83, 75, 10, 83, 84, 12, 118, 22, 70, 69, 66, 55,
        83, 85, 85, 82, 13, 114, 32
    )

    private const val SECRET = "DurusSalafiBDKey2025"

    private fun getDecryptedApiKey(): String {
        val sb = StringBuilder()
        for (i in MASK_BYTES.indices) {
            val secretChar = SECRET[i % SECRET.length].code
            val decryptedChar = (MASK_BYTES[i] xor secretChar).toChar()
            sb.append(decryptedChar)
        }
        return sb.toString()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun getBengaliSubtitleStream(videoUrl: String, existingExtractor: StreamExtractor? = null): SubtitlesStream? {
        return withContext(Dispatchers.IO) {
            try {
                val extractor = existingExtractor ?: run {
                    val service = NewPipe.getServiceByUrl(videoUrl)
                    val ext = service.getStreamExtractor(videoUrl)
                    ext.fetchPage()
                    ext
                }

                val subtitles = extractor.subtitlesDefault
                subtitles.firstOrNull { sub ->
                    val lang = sub.languageTag ?: ""
                    val localeLang = sub.locale?.language ?: ""
                    lang.startsWith("bn", ignoreCase = true) ||
                            localeLang.equals("bn", ignoreCase = true) ||
                            lang.contains("bn", ignoreCase = true)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    suspend fun fetchAndCleanSubtitle(subtitleStream: SubtitlesStream): String {
        return withContext(Dispatchers.IO) {
            try {
                val contentUrl = subtitleStream.content
                if (contentUrl.isNullOrBlank()) return@withContext ""
                val request = Request.Builder().url(contentUrl).build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) return@withContext ""
                val rawText = response.body?.string() ?: ""
                cleanSubtitleText(rawText)
            } catch (e: Exception) {
                e.printStackTrace()
                ""
            }
        }
    }

    fun cleanSubtitleText(raw: String): String {
        if (raw.isBlank()) return ""

        var text = raw

        // Remove XML/HTML tags
        text = text.replace(Regex("<[^>]+>"), " ")

        // Remove VTT / SRT headers and metadata
        text = text.replace(Regex("WEBVTT.*"), "")
        text = text.replace(Regex("Kind:.*"), "")
        text = text.replace(Regex("Language:.*"), "")
        text = text.replace(Regex("STYLE[\\s\\S]*?::cue.*"), "")

        // Remove Timestamps
        text = text.replace(Regex("\\d{2}:\\d{2}:\\d{2}[\\.,]\\d{3}\\s*-->\\s*\\d{2}:\\d{2}:\\d{2}[\\.,]\\d{3}"), "")
        text = text.replace(Regex("\\d{2}:\\d{2}[\\.,]\\d{3}\\s*-->\\s*\\d{2}:\\d{2}[\\.,]\\d{3}"), "")
        text = text.replace(Regex("\\d{2}:\\d{2}:\\d{2}\\s*-->\\s*\\d{2}:\\d{2}:\\d{2}"), "")

        // Remove line numbers
        text = text.replace(Regex("(?m)^\\d+$"), "")

        // Unescape HTML entities
        text = text.replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")

        // Clean lines and remove empty lines
        val lines = text.lines()
            .map { it.replace(Regex("\\s+"), " ").trim() }
            .filter { it.isNotBlank() }

        // Deduplicate consecutive identical lines
        val deduplicated = mutableListOf<String>()
        for (line in lines) {
            if (deduplicated.isEmpty() || deduplicated.last() != line) {
                deduplicated.add(line)
            }
        }

        return deduplicated.joinToString(" ").replace(Regex("\\s+"), " ").trim()
    }

    suspend fun generateAiSummary(
        cleanedCaptionText: String,
        onStepUpdate: ((AiSummaryStep) -> Unit)? = null
    ): Result<String> {
        return withContext(Dispatchers.IO) {
            if (cleanedCaptionText.isBlank()) {
                return@withContext Result.failure(Exception("সারসংক্ষেপ তৈরি করার জন্য কোনো ক্যাপশন লেখা পাওয়া যায়নি।"))
            }

            withContext(Dispatchers.Main) {
                onStepUpdate?.invoke(AiSummaryStep.SENDING_REQUEST)
            }

            // Limit input size to prevent payload issues if text is ultra massive (~60,000 chars)
            val truncatedText = if (cleanedCaptionText.length > 60000) {
                cleanedCaptionText.substring(0, 60000) + "..."
            } else {
                cleanedCaptionText
            }

            val prompt = "অনুগ্রহ করে নিচের বাংলা ভিডিও ক্যাপশনটি থেকে প্রধান গুরুত্বপূর্ণ পয়েন্টগুলো সংক্ষেপে ও সহজ ভাষায় বুলেট পয়েন্ট আকারে বাংলায় সারসংক্ষেপ (Summary) তৈরি করে দাও:\n\n$truncatedText"

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val apiKey = getDecryptedApiKey()

            val jsonBody = JSONObject().apply {
                put("model", "inclusionai/ling-3.0-flash-sante:free")
                val messagesArray = JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    })
                }
                put("messages", messagesArray)
            }

            val body = jsonBody.toString().toRequestBody(mediaType)

            try {
                val request = Request.Builder()
                    .url("https://openrouter.ai/api/v1/chat/completions")
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("HTTP-Referer", "https://github.com/durus-salafi/bangladesh")
                    .addHeader("X-Title", "Durus App")
                    .build()

                val response = client.newCall(request).execute()

                withContext(Dispatchers.Main) {
                    onStepUpdate?.invoke(AiSummaryStep.PROCESSING_RESPONSE)
                }

                val responseStr = response.body?.string() ?: ""

                val trimmedResponse = responseStr.trim()
                val isHtml = trimmedResponse.startsWith("<html", ignoreCase = true) ||
                        trimmedResponse.startsWith("<!DOCTYPE", ignoreCase = true)

                if (response.isSuccessful && !isHtml && responseStr.isNotBlank()) {
                    val json = JSONObject(responseStr)
                    val choices = json.optJSONArray("choices")
                    if (choices != null && choices.length() > 0) {
                        val firstChoice = choices.getJSONObject(0)
                        val messageObj = firstChoice.optJSONObject("message")
                        val content = messageObj?.optString("content")
                        if (!content.isNullOrBlank()) {
                            return@withContext Result.success(content.trim())
                        }
                    }
                    Result.failure(Exception("সারসংক্ষেপ প্রতিক্রিয়া সঠিক নয়।"))
                } else {
                    val cleanErrorMsg = when {
                        response.code == 401 -> "এআই সার্ভারের অথেনটিকেশন ব্যর্থ হয়েছে (HTTP 401)। API key সঠিক নয়।"
                        response.code == 402 -> "এআই সার্ভারের ব্যবহারের কোটা/লিমিট শেষ হয়ে গেছে (HTTP 402)। অনুগ্রহ করে পরে আবার চেষ্টা করুন।"
                        response.code == 429 -> "অনেক অনুরোধ পাঠানো হয়েছে (HTTP 429)। অনুগ্রহ করে কিছুক্ষণ পর আবার চেষ্টা করুন।"
                        isHtml || !response.isSuccessful -> "সারসংক্ষেপ সার্ভারে সমস্যা হয়েছে (HTTP ${response.code})। অনুগ্রহ করে পরে আবার চেষ্টা করুন।"
                        else -> "সারসংক্ষেপ প্রতিক্রিয়া সঠিক নয়।"
                    }
                    Result.failure(Exception(cleanErrorMsg))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Result.failure(e)
            }
        }
    }
}
