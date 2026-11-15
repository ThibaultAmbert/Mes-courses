package fr.mescourses.core.recipes

import fr.mescourses.core.catalog.Course
import fr.mescourses.core.ingredients.IngredientParser
import fr.mescourses.core.model.Recipe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * Extrait une recette d'une page web grâce aux données structurées schema.org/Recipe (JSON-LD),
 * présentes sur Marmiton, 750g, Cuisine AZ, Journal des Femmes, etc.
 */
object RecipeJsonLdExtractor {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun extract(html: String, sourceUrl: String? = null): Recipe? = extractFromJsonLd(jsonLdBlocks(html), sourceUrl)

    /**
     * Fiche `Recipe` de Next.js (Picnic) : pas de balise JSON-LD, la fiche est dans le flux
     * `self.__next_f.push([1,"{\"@context\":…"])`, en chaîne JavaScript échappée.
     */
    private val flightRecipe = Regex("""\{\\"@context\\":\\"https://schema\.org\\",\\"@type\\":\\"Recipe\\"(?:[^"\\]++|\\.)*+""")

    /** Contenu des balises JSON-LD de la page, puis des fiches Recipe du flux Next.js. */
    private fun jsonLdBlocks(html: String, doc: Document = Jsoup.parse(html)): List<String> {
        val scripts = doc.select("script[type=application/ld+json]").map { it.data() }
        val flight = flightRecipe.findAll(html).mapNotNull { m ->
            val literal = m.value
            // La fin de la chaîne JavaScript est le guillemet non échappé ; on la décode comme une chaîne JSON.
            runCatching { (json.parseToJsonElement("\"" + literal + "\"") as? JsonPrimitive)?.contentOrNull }.getOrNull()
        }.toList()
        return scripts + flight
    }

    /** Même chose à partir du contenu des balises JSON-LD (lues par le navigateur intégré). */
    fun extractFromJsonLd(blocks: List<String>, sourceUrl: String? = null): Recipe? {
        for (block in blocks) {
            val element = runCatching { json.parseToJsonElement(block.trim()) }.getOrNull() ?: continue
            val recipeNode = findRecipe(element) ?: continue
            return toRecipe(recipeNode, sourceUrl)
        }
        return null
    }

    /** Une fiche lue pour le catalogue : la recette et ce que le site en dit (note, catégorie, durée totale). */
    data class Details(
        val recipe: Recipe,
        val rating: Double? = null,
        val ratingCount: Int? = null,
        val categories: List<String> = emptyList(),
        val totalMinutes: Int? = null,
    )

    fun extractDetails(html: String, sourceUrl: String? = null): Details? {
        val doc = Jsoup.parse(html)
        val parsed = jsonLdBlocks(html, doc).mapNotNull { block ->
            runCatching { json.parseToJsonElement(block.trim()) }.getOrNull()
        }
        val node = parsed.firstNotNullOfOrNull { findRecipe(it) } ?: return null
        val rating = node["aggregateRating"] as? JsonObject
        val recipe = toRecipe(node, sourceUrl)
        val rawCategories = strings(node["recipeCategory"]).map(::clean).filter { it.isNotBlank() }
        val categories = if (Course.of(rawCategories) != Course.AUTRE) {
            rawCategories
        } else {
            (fallbackCategories(parsed, doc, recipe.title, sourceUrl) + rawCategories).distinct()
        }
        return Details(
            recipe = recipe,
            rating = rating?.string("ratingValue")?.replace(',', '.')?.toDoubleOrNull(),
            ratingCount = (rating?.string("ratingCount") ?: rating?.string("reviewCount"))?.toDoubleOrNull()?.toInt(),
            categories = categories,
            totalMinutes = node.string("totalTime")?.let(::parseIsoDurationMinutes)
                ?: listOfNotNull(recipe.prepMinutes, recipe.cookMinutes).takeIf { it.isNotEmpty() }?.sum(),
        )
    }

    private fun fallbackCategories(
        parsedJsonLd: List<JsonElement>, doc: Document, recipeTitle: String, sourceUrl: String?,
    ): List<String> {
        val fromJsonLdBreadcrumbs = parsedJsonLd.flatMap { breadcrumbCategories(it, recipeTitle, sourceUrl) }
        val fromHtmlBreadcrumbs = htmlBreadcrumbCategories(doc, recipeTitle, sourceUrl)
        val fromScripts = embeddedCategories(doc)
        return (fromJsonLdBreadcrumbs + fromHtmlBreadcrumbs + fromScripts)
            .filter { Course.of(listOf(it)) != Course.AUTRE }
            .distinct()
    }

    private fun breadcrumbCategories(element: JsonElement, recipeTitle: String, sourceUrl: String?): List<String> {
        val lists = ArrayList<JsonObject>()
        collectType(element, "BreadcrumbList", lists)
        val out = ArrayList<String>()
        for (list in lists) {
            val items = list["itemListElement"] as? JsonArray ?: continue
            for ((idx, item) in items.withIndex()) {
                val obj = item as? JsonObject
                val rawName = obj?.string("name")
                    ?: (obj?.get("item") as? JsonObject)?.string("name")
                    ?: (item as? JsonPrimitive)?.contentOrNull
                    ?: continue
                val name = clean(rawName)
                val itemUrl = (obj?.get("item") as? JsonPrimitive)?.contentOrNull
                    ?: (obj?.get("item") as? JsonObject)?.string("@id")
                    ?: (obj?.get("item") as? JsonObject)?.string("url")
                val isLastItem = idx == items.lastIndex && (items.size >= 3 || (items.size >= 2 && itemUrl == null))
                if (name.isBlank() || isIgnoredCrumb(name, itemUrl, recipeTitle, sourceUrl) || isLastItem) continue
                out += name
            }
        }
        return out
    }

    private fun htmlBreadcrumbCategories(doc: Document, recipeTitle: String, sourceUrl: String?): List<String> =
        doc.select("nav[aria-label*=breadcrumb i] a[href], .breadcrumb a[href], .breadcrumbs a[href], [itemtype*=BreadcrumbList] a[href]")
            .mapNotNull { link ->
                val text = clean(link.text())
                val href = link.attr("href").takeIf { it.isNotBlank() }
                text.takeUnless { it.isBlank() || isIgnoredCrumb(it, href, recipeTitle, sourceUrl) }
            }

    private val flightCategoryProp =
        Regex("""\\?"(?:recipeCategory|course|dishType|mealType|category)\\?"\s*:\s*\\?"((?:[^"\\]|\\u[0-9a-fA-F]{4}|\\/){2,48})\\?"""")
    private val flightCategoryArray =
        Regex("""\\?"(?:recipeCategory|categories|courses|dishTypes|mealTypes)\\?"\s*:\s*\[([^\]]{2,200})\]""")
    private val flightStringItem =
        Regex("""\\?"((?:[^"\\]|\\u[0-9a-fA-F]{4}|\\/){2,48})\\?"""")

    private fun embeddedCategories(doc: Document): List<String> {
        val scripts = doc.select("script").filter {
            val data = it.data()
            "self.__next_f" in data || it.id() == "__NEXT_DATA__"
        }
        if (scripts.isEmpty()) return emptyList()
        val out = ArrayList<String>()
        for (script in scripts) {
            val data = script.data()
            for (m in flightCategoryProp.findAll(data)) {
                decodeJsonString(m.groupValues[1])?.let { out += it }
            }
            for (m in flightCategoryArray.findAll(data)) {
                for (item in flightStringItem.findAll(m.groupValues[1])) {
                    decodeJsonString(item.groupValues[1])?.let { out += it }
                }
            }
        }
        return out
    }

    private fun decodeJsonString(raw: String): String? =
        runCatching { (json.parseToJsonElement("\"$raw\"") as? JsonPrimitive)?.contentOrNull }
            .getOrNull()?.trim()?.takeIf { it.isNotBlank() && '<' !in it && !it.startsWith("http") }

    private fun isIgnoredCrumb(name: String, itemUrl: String?, recipeTitle: String, sourceUrl: String?): Boolean {
        val low = name.lowercase()
        if (low in setOf("accueil", "recettes", "recette", "home")) return true
        if (low == recipeTitle.lowercase()) return true
        if (itemUrl != null && sourceUrl != null) {
            val p1 = itemUrl.substringAfter("://").substringBefore('?').substringBefore('#').trimEnd('/')
            val p2 = sourceUrl.substringAfter("://").substringBefore('?').substringBefore('#').trimEnd('/')
            if (p1 == p2 || (itemUrl.startsWith("/") && p2.endsWith(itemUrl.trimEnd('/')))) return true
        }
        return false
    }

    private fun collectType(element: JsonElement, type: String, out: MutableList<JsonObject>) {
        when (element) {
            is JsonArray -> element.forEach { collectType(it, type, out) }
            is JsonObject -> {
                if (hasType(element, type)) out += element
                element.values.forEach { if (it is JsonObject || it is JsonArray) collectType(it, type, out) }
            }
            else -> Unit
        }
    }

    private fun findRecipe(element: JsonElement): JsonObject? = when (element) {
        is JsonArray -> element.firstNotNullOfOrNull { findRecipe(it) }
        is JsonObject -> when {
            isRecipe(element) -> element
            element["@graph"] != null -> findRecipe(element["@graph"]!!)
            element["mainEntity"] != null -> findRecipe(element["mainEntity"]!!)
            else -> null
        }
        else -> null
    }

    private fun isRecipe(obj: JsonObject): Boolean = hasType(obj, "Recipe")

    private fun hasType(obj: JsonObject, expected: String): Boolean = when (val type = obj["@type"]) {
        is JsonPrimitive -> type.contentOrNull.equals(expected, ignoreCase = true)
        is JsonArray -> type.any { (it as? JsonPrimitive)?.contentOrNull.equals(expected, ignoreCase = true) }
        else -> false
    }

    private fun toRecipe(obj: JsonObject, sourceUrl: String?): Recipe {
        val title = obj.string("name")?.let(::clean) ?: "Recette sans titre"
        // Picnic écrit « BLANK » à la place d'une quantité absente (« BLANK noix de coco »).
        val ingredientLines = strings(obj["recipeIngredient"] ?: obj["ingredients"]).map(::clean).map { it.replace(Regex("^BLANK\\s+"), "") }
        val steps = instructions(obj["recipeInstructions"]).map(::clean).filter { it.isNotBlank() }
        val prep = obj.string("prepTime")?.let(::parseIsoDurationMinutes)
        val cook = obj.string("cookTime")?.let(::parseIsoDurationMinutes)
        val keywords = obj.string("keywords")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        val categories = strings(obj["recipeCategory"]) + strings(obj["recipeCuisine"])
        return Recipe(
            title = title,
            servings = parseServings(obj["recipeYield"]) ?: 4,
            ingredients = IngredientParser.parseAll(ingredientLines),
            steps = steps,
            sourceUrl = sourceUrl ?: obj.string("url"),
            imageUrl = image(obj["image"]),
            prepMinutes = prep,
            cookMinutes = cook,
            tags = (categories + keywords).distinct().take(8),
        )
    }

    private fun JsonObject.string(key: String): String? = when (val v = this[key]) {
        is JsonPrimitive -> v.contentOrNull
        is JsonArray -> (v.firstOrNull() as? JsonPrimitive)?.contentOrNull
        else -> null
    }

    private fun strings(element: JsonElement?): List<String> = when (element) {
        null, JsonNull -> emptyList()
        is JsonPrimitive -> listOfNotNull(element.contentOrNull)
        is JsonArray -> element.flatMap { strings(it) }
        is JsonObject -> listOfNotNull((element["name"] as? JsonPrimitive)?.contentOrNull)
    }

    private fun instructions(element: JsonElement?): List<String> = when (element) {
        null, JsonNull -> emptyList()
        is JsonPrimitive -> element.contentOrNull.orEmpty()
            .split(Regex("\\n+|(?<=\\.)\\s+(?=\\d+[.)] )")).map { it.trim() }.filter { it.isNotEmpty() }
        is JsonArray -> element.flatMap { instructions(it) }
        is JsonObject -> when {
            element["itemListElement"] != null -> instructions(element["itemListElement"])
            else -> listOfNotNull(
                (element["text"] as? JsonPrimitive)?.contentOrNull ?: (element["name"] as? JsonPrimitive)?.contentOrNull,
            )
        }
    }

    private fun image(element: JsonElement?): String? = when (element) {
        is JsonPrimitive -> element.contentOrNull
        is JsonArray -> element.firstNotNullOfOrNull { image(it) }
        is JsonObject -> (element["url"] as? JsonPrimitive)?.contentOrNull
        else -> null
    }

    fun parseServings(element: JsonElement?): Int? = when (element) {
        is JsonPrimitive -> element.contentOrNull?.let { Regex("\\d+").find(it)?.value?.toIntOrNull() }
        is JsonArray -> element.firstNotNullOfOrNull { parseServings(it) }
        else -> null
    }

    /** « PT1H30M » → 90. */
    fun parseIsoDurationMinutes(value: String): Int? {
        val m = Regex("""P(?:(\d+)D)?T?(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?""", RegexOption.IGNORE_CASE)
            .matchEntire(value.trim()) ?: return null
        val (d, h, min, _) = m.destructured
        val total = (d.toIntOrNull() ?: 0) * 1440 + (h.toIntOrNull() ?: 0) * 60 + (min.toIntOrNull() ?: 0)
        return total.takeIf { it > 0 }
    }

    private fun clean(text: String): String =
        Jsoup.parse(text).text().replace(' ', ' ').replace(Regex("\\s+"), " ").trim()
}
