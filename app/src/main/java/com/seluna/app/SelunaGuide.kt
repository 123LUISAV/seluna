package com.seluna.app

import android.content.Context
import java.text.Normalizer
import java.util.Locale
import org.json.JSONArray

data class GuideArticle(
    val title: String,
    val question: String,
    val answer: String,
    val sourceLabel: String?,
    val sourceUrl: String?,
    val wellness: Boolean,
)

class SelunaGuide(context: Context) {
    private val byQuestion = mutableMapOf<String, GuideArticle>()

    val articles: List<GuideArticle> =
        context.assets.open("seluna-guide.json").bufferedReader().use { reader ->
            val data = JSONArray(reader.readText())
            List(data.length()) { index ->
                val item = data.getJSONObject(index)
                val article =
                    GuideArticle(
                        title = item.getString("title"),
                        question = item.getString("question"),
                        answer = item.getString("answer"),
                        sourceLabel =
                            item.takeUnless { it.isNull("sourceLabel") }?.getString("sourceLabel"),
                        sourceUrl =
                            item.takeUnless { it.isNull("sourceUrl") }?.getString("sourceUrl"),
                        wellness = item.optBoolean("wellness"),
                    )
                byQuestion[normalize(article.question)] = article
                val aliases = item.getJSONArray("aliases")
                for (alias in 0 until aliases.length()) {
                    byQuestion[normalize(aliases.getString(alias))] = article
                }
                article
            }
        }

    fun match(text: String): GuideArticle? = byQuestion[normalize(text)]

    private fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "")
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
}
