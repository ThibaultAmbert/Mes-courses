package fr.mescourses.core

import fr.mescourses.core.recipes.RecipeJsonLdExtractor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RecipeJsonLdExtractorTest {
    private val html = javaClass.getResource("/marmiton_like.html")!!.readText()

    @Test fun extrait_la_recette_du_graph() {
        val r = assertNotNull(RecipeJsonLdExtractor.extract(html, "https://exemple.fr/quiche"))
        assertEquals("Quiche lorraine & facile", r.title)
        assertEquals(6, r.servings)
        assertEquals(6, r.ingredients.size)
        assertEquals(200.0, r.ingredients[1].quantity)
        assertEquals("lardons", r.ingredients[1].name)
        assertEquals(4, r.steps.size)
        assertEquals("Battre les œufs avec la crème et le lait, saler, poivrer.", r.steps[2])
        assertEquals(15, r.prepMinutes)
        assertEquals(45, r.cookMinutes)
        assertEquals("https://exemple.fr/quiche.jpg", r.imageUrl)
        assertEquals(listOf("Plat principal", "quiche", "lardons", "facile"), r.tags)
    }

    @Test fun page_sans_recette() {
        assertNull(RecipeJsonLdExtractor.extract("<html><body>rien</body></html>"))
    }

    @Test fun durees() {
        assertEquals(90, RecipeJsonLdExtractor.parseIsoDurationMinutes("PT1H30M"))
        assertEquals(null, RecipeJsonLdExtractor.parseIsoDurationMinutes("PT0M"))
    }

    /** Fiche Picnic : pas de JSON-LD, la recette est dans le flux Next.js (chaîne JavaScript échappée). */
    @Test fun extrait_la_recette_du_flux_nextjs() {
        val recipe = """{"@context":"https://schema.org","@type":"Recipe","name":"Piña Colada","image":"https://img.fr/a.webp","totalTime":"PT5M","recipeIngredient":["160 ml rhum blanc","960 ml jus ananas","BLANK noix de coco"],"recipeInstructions":[{"@type":"HowToStep","text":"Dans un shaker, verser le rhum, l'ananas et la crème de coco."},{"@type":"HowToStep","text":"Secouer \"énergiquement\"."}]}"""
        val literal = recipe.replace("\\", "\\\\").replace("\"", "\\\"")
        val page = "<html><body><script>self.__next_f.push([1,\"1d:T783,\"])</script><script>self.__next_f.push([1,\"$literal\"])</script></body></html>"
        val r = assertNotNull(RecipeJsonLdExtractor.extract(page, "https://picnic.app/fr/recettes/67f5341b94bb9a40e1a07be1/pia-colada"))
        assertEquals("Piña Colada", r.title)
        assertEquals(3, r.ingredients.size)
        assertFalse(r.ingredients[2].name.contains("BLANK", ignoreCase = true))
        assertEquals(2, r.steps.size)
        assertEquals("Secouer \"énergiquement\".", r.steps[1])
        assertEquals("https://img.fr/a.webp", r.imageUrl)
        assertEquals(5, RecipeJsonLdExtractor.extractDetails(page)?.totalMinutes)
    }

    @Test fun recupere_categorie_depuis_fil_d_ariane_ou_flux_nextjs() {
        val breadcrumbJsonLd = """<html><head><script type="application/ld+json">{
            "@context":"https://schema.org","@graph":[
                {"@type":"BreadcrumbList","itemListElement":[
                    {"@type":"ListItem","position":1,"name":"Accueil","item":"https://ex.fr/"},
                    {"@type":"ListItem","position":2,"name":"Desserts","item":"https://ex.fr/desserts"},
                    {"@type":"ListItem","position":3,"name":"Velouté glacé"}
                ]},
                {"@type":"Recipe","name":"Velouté glacé","recipeIngredient":["200 g de chocolat","2 œufs"]}
            ]}</script></head></html>"""
        assertEquals(
            listOf("Desserts"),
            RecipeJsonLdExtractor.extractDetails(breadcrumbJsonLd, "https://ex.fr/r/1")?.categories,
        )

        // Le dernier élément (titre) ne doit pas provoquer de déduction même sans recipeCategory.
        val titleInBreadcrumb = """<html><head><script type="application/ld+json">{
            "@context":"https://schema.org","@graph":[
                {"@type":"BreadcrumbList","itemListElement":[
                    {"@type":"ListItem","position":1,"name":"Accueil","item":"https://ex.fr/"},
                    {"@type":"ListItem","position":2,"name":"Recettes","item":"https://ex.fr/recettes"},
                    {"@type":"ListItem","position":3,"name":"Soupe de potiron"}
                ]},
                {"@type":"Recipe","name":"Soupe de potiron","recipeIngredient":["1 potiron","1 oignon"]}
            ]}</script></head></html>"""
        assertEquals(
            emptyList(),
            RecipeJsonLdExtractor.extractDetails(titleInBreadcrumb, "https://ex.fr/r/2")?.categories,
        )

        val picnicCourse = """{"@context":"https://schema.org","@type":"Recipe","name":"Wok de boeuf","recipeIngredient":["200 g de boeuf","2 carottes"]}"""
        val literal = picnicCourse.replace("\\", "\\\\").replace("\"", "\\\"")
        val nextPage = """<html><body>
            <script>self.__next_f.push([1,"1d:{\"mealType\":\"Plat\",\"title\":\"Wok\"}"])</script>
            <script>self.__next_f.push([1,"$literal"])</script>
        </body></html>"""
        assertEquals(
            listOf("Plat"),
            RecipeJsonLdExtractor.extractDetails(nextPage, "https://picnic.app/fr/recettes/abc/wok")?.categories,
        )

        val htmlCrumb = """<html><head><script type="application/ld+json">
            {"@context":"https://schema.org","@type":"Recipe","name":"Brick au thon","recipeIngredient":["2 feuilles","1 boîte"]}
            </script></head><body><nav aria-label="Breadcrumb"><a href="/">Accueil</a><a href="/entrees">Entrées</a><span>Brick au thon</span></nav></body></html>"""
        assertEquals(
            listOf("Entrées"),
            RecipeJsonLdExtractor.extractDetails(htmlCrumb, "https://ex.fr/brick")?.categories,
        )
    }
}
