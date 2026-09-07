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

object AiSummaryUtils {

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
            .map { it.trim() }
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

    suspend fun generateAiSummary(cleanedCaptionText: String): Result<String> {
        return withContext(Dispatchers.IO) {
            if (cleanedCaptionText.isBlank()) {
                return@withContext Result.failure(Exception("সারসংক্ষেপ তৈরি করার জন্য কোনো ক্যাপশন লেখা পাওয়া যায়নি।"))
            }

            // Limit input size to prevent payload issues if text is ultra massive (~60,000 chars)
            val truncatedText = if (cleanedCaptionText.length > 60000) {
                cleanedCaptionText.substring(0, 60000) + "..."
            } else {
                cleanedCaptionText
            }

            val prompt = "অনুগ্রহ করে নিচের বাংলা ভিডিও ক্যাপশনটি থেকে প্রধান গুরুত্বপূর্ণ পয়েন্টগুলো সংক্ষেপে ও সহজ ভাষায় বুলেট পয়েন্ট আকারে বাংলায় সারসংক্ষেপ (Summary) তৈরি করে দাও:\n\n$truncatedText"

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val endpoints = listOf(
                "https://text.pollinations.ai/openai/chat/completions"
            )
            val models = listOf("openai", "openai-fast")

            var lastError: Exception? = null

            for (model in models) {
                val jsonBody = JSONObject().apply {
                    put("model", model)
                    val messagesArray = JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", prompt)
                        })
                    }
                    put("messages", messagesArray)
                }

                val body = jsonBody.toString().toRequestBody(mediaType)

                for (endpoint in endpoints) {
                    try {
                        val request = Request.Builder()
                            .url(endpoint)
                            .post(body)
                            .addHeader("Content-Type", "application/json")
                            .build()

                        val response = client.newCall(request).execute()
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
                        } else {
                            val cleanErrorMsg = if (isHtml || !response.isSuccessful) {
                                "সারসংক্ষেপ সার্ভারে সমস্যা হয়েছে (HTTP ${response.code})। অনুগ্রহ করে পরে আবার চেষ্টা করুন।"
                            } else {
                                "সারসংক্ষেপ প্রতিক্রিয়া সঠিক নয়।"
                            }
                            lastError = Exception(cleanErrorMsg)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        lastError = e
                    }
                }
            }

            Result.failure(lastError ?: Exception("সারসংক্ষেপ তৈরি করার সময় কোনো সাড়া পাওয়া যায়নি।"))
        }
    }
}
