package fr.mescourses.core

import fr.mescourses.core.carrefour.CarrefourClient
import fr.mescourses.core.carrefour.CarrefourParsers
import fr.mescourses.core.carrefour.HttpResponse
import fr.mescourses.core.carrefour.HttpTransport
import fr.mescourses.core.catalog.CatalogSource
import fr.mescourses.core.catalog.CatalogUpdater
import fr.mescourses.core.ingredients.IngredientParser
import fr.mescourses.core.ingredients.PairingGraph
import fr.mescourses.core.ingredients.PairingGraph.Pairing
import fr.mescourses.core.leclerc.LeclercParsers
import fr.mescourses.core.menu.MenuValidator
import fr.mescourses.core.menu.TextGenerator
import fr.mescourses.core.model.ItemSource
import fr.mescourses.core.model.MenuDish
import fr.mescourses.core.model.Portions
import fr.mescourses.core.model.Recipe
import fr.mescourses.core.model.ShoppingItem
import fr.mescourses.core.recipes.RecipeFilter
import fr.mescourses.core.recipes.RecipeFilters
import fr.mescourses.core.retail.Scrape
import fr.mescourses.core.retail.TtlCache
import fr.mescourses.core.shopping.Aisle
import fr.mescourses.core.visual.DishVisuals
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuditFixesTest {

    @Test fun menu_validator_garde_la_meilleure_tentative_et_son_manque_exact() = runTest {
        var calls = 0
        val llm = TextGenerator { _, _ ->
            calls++
            when (calls) {
                1 -> """{"ingredients":["300 g de pommes de terre","2 carottes"],"etapes":["Cuire"]}""" // manque protéine (1)
                else -> """{"ingredients":["2 carottes"],"etapes":["Cuire"]}""" // manque protéine + féculent (2)
            }
        }
        val recipes = InMemoryRecipes()
        val draft = InMemoryDraft().apply { set(listOf(MenuDish("Poêlée rustique", "", Portions(2)))) }
        MenuValidator(llm, recipes, InMemoryShopping(), InMemoryPlan(), draft, { 0.5 }).validate()

        val saved = recipes.state.value.single()
        assertEquals(2, saved.ingredients.size)
        val notes = assertNotNull(saved.notes)
        assertTrue("protéine" in notes && "féculent" !in notes, notes)
    }

    @Test fun menu_validator_doublon_de_titre_ne_garde_que_l_indice_echoue() = runTest {
        var calls = 0
        val llm = TextGenerator { _, _ ->
            calls++
            if (calls == 1) """{"ingredients":["1 poulet","2 carottes","300 g de riz"],"etapes":["Rôtir"]}"""
            else "réponse invalide"
        }
        val draft = InMemoryDraft().apply {
            set(
                listOf(
                    MenuDish("Poulet rôti", "", Portions(2), null),
                    MenuDish("Poulet rôti", "", Portions(2), null),
                ),
            )
        }
        val res = MenuValidator(llm, InMemoryRecipes(), InMemoryShopping(), InMemoryPlan(), draft, { 0.5 }).validate()
        assertEquals(1, res.newRecipes)
        assertEquals(listOf("Poulet rôti"), res.failures)
        assertEquals(1, draft.state.value.size)
    }

    @Test fun menu_validator_propage_cancellation_exception() = runTest {
        val llm = TextGenerator { _, _ -> throw CancellationException("annulé") }
        val draft = InMemoryDraft().apply { set(listOf(MenuDish("Velouté", "", Portions(2)))) }
        assertFailsWith<CancellationException> {
            MenuValidator(llm, InMemoryRecipes(), InMemoryShopping(), InMemoryPlan(), draft, { 0.5 }).validate()
        }
    }

    @Test fun carrefour_collect_cart_lines_n_est_pas_court_circuite_par_compteur_parent() {
        val json = """
            {
              "counter": 3,
              "items": [
                {"ean": "3560070000011", "title": "Lait bio", "counter": 2, "totalItemPrice": 2.4},
                {"ean": "3560070000028", "title": "Beurre doux", "counter": 1, "totalItemPrice": 2.1}
              ]
            }
        """.trimIndent()
        val cart = CarrefourParsers.parseCart(json)
        assertEquals(listOf("3560070000011", "3560070000028"), cart.lines.map { it.ean })
        assertEquals(listOf(2, 1), cart.lines.map { it.quantity })
    }

    @Test fun scrape_decode_entities_et_enclosing_object_proteges() {
        assertEquals("&#9999999; &#x110000; A é", Scrape.decodeEntities("&#9999999; &#x110000; &#65; &#xE9;"))

        val raw = """prefix {"label":"accolade } et { et \"echap }\"","iIdProduit":42,"sLibelleLigne1":"Lait"} suffix"""
        val pos = raw.indexOf("\"iIdProduit\"")
        val obj = assertNotNull(Scrape.enclosingObject(raw, pos))
        assertTrue("\"iIdProduit\":42" in obj && "\"label\":" in obj, obj)
    }

    @Test fun leclerc_parse_cart_page_conserve_image_url() {
        val html = """
            <script>var x = {"lstProduits":[
                {"iIdProduit":"991","sLibelleLigne1":"Crème fraîche","iQuantitePanier":2,"nrPVUnitaireTTC":1.5,"sUrlVignetteProduit":"https://img.example/creme.jpg"}
            ]};</script>
        """.trimIndent()
        val cart = LeclercParsers.parseCartPage(html)
        assertEquals(1, cart.lines.size)
        assertEquals("https://img.example/creme.jpg", cart.lines[0].imageUrl)
    }

    @Test fun ttl_cache_expire_evince_lru_et_gere_null() {
        var now = 1_000L
        val cache = TtlCache<String?>(ttlMs = 500L, maxEntries = 2, now = { now })
        cache.put("n", null)
        val hitNull = cache.get("n")
        assertNotNull(hitNull)
        assertNull(hitNull.value)

        cache.put("a", "A") // cache: n, a
        cache.put("b", "B") // évince n -> a, b
        assertNull(cache.get("n"))
        assertEquals("A", cache.get("a")?.value) // a devient récent -> b LRU
        cache.put("c", "C") // évince b -> a, c
        assertNull(cache.get("b"))
        assertEquals("A", cache.get("a")?.value)

        now += 500L
        assertNull(cache.get("a"))
        assertNull(cache.get("c"))
    }

    @Test fun catalog_updater_ne_marque_pas_last_update_si_tout_echoue() = runTest {
        val store = InMemoryCatalog()
        val src = CatalogSource("cuisineaz", listOf("https://x.fr/s.xml"), null, Regex("""recette-\d+\.aspx$"""))
        val report = CatalogUpdater(store, { error("réseau indisponible") }, listOf(src), now = { 9_000L }).update()
        assertEquals(listOf("Cuisine AZ"), report.failedSites)
        assertNull(store.meta(CatalogUpdater.LAST_UPDATE))
    }

    @Test fun run_catching_cancellable_et_client_carrefour_propage_annulation_et_vide_cache() = runTest {
        assertFailsWith<CancellationException> {
            runCatchingCancellable { throw CancellationException("stop") }
        }
        assertTrue(runCatchingCancellable { error("boom") }.isFailure)

        val cancelTransport = object : HttpTransport {
            override suspend fun request(method: String, path: String, headers: Map<String, String>, body: String?): HttpResponse {
                throw CancellationException("stop")
            }
        }
        assertFailsWith<CancellationException> {
            CarrefourClient(cancelTransport).search("lait")
        }

        var searchCalls = 0
        val cacheTransport = object : HttpTransport {
            override suspend fun request(method: String, path: String, headers: Map<String, String>, body: String?): HttpResponse {
                if (path.startsWith("/s?")) searchCalls++
                return HttpResponse(200, emptyMap(), """{"data":[]}""")
            }
        }
        val client = CarrefourClient(cacheTransport)
        client.search("lait")
        client.search("lait")
        assertEquals(1, searchCalls)
        client.forgetContext()
        client.search("lait")
        assertEquals(2, searchCalls)
    }

    @Test fun pairing_graph_affinity_with_base_identique_a_affinity_score() {
        val g = PairingGraph(
            listOf(
                Pairing("tomate", "basilic", 0.9),
                Pairing("tomate", "mozzarella", 0.7),
                Pairing("basilic", "mozzarella", 0.5),
                Pairing("ail", "basilic", 0.6),
                Pairing("ail", "tomate", 0.4),
            ),
        )
        val base = setOf("tomate", "basilic", "sel")
        val baseList = base.toList()
        val totals = g.pairTotals(baseList)
        for (cand in listOf(emptySet(), setOf("mozzarella", "ail"), setOf("tomate", "ail"), setOf("poivre"))) {
            assertEquals(
                g.affinityScore(cand + base),
                g.affinityWithBase(cand, baseList, base, totals),
                1e-12,
            )
        }
    }

    @Test fun rayons_visuels_filtres_parseur_et_suppression_lot() = runTest {
        assertEquals(Aisle.EPICERIE, Aisle.of("Lait de coco"))
        assertEquals(Aisle.CREMERIE, Aisle.of("Crème de marrons"))
        assertEquals(Aisle.POISSONNERIE, Aisle.of("Pavé de saumon"))
        assertEquals("🥧", DishVisuals.of("Tarte aux poireaux").emoji)
        assertEquals(RecipeFilter.POISSON, RecipeFilters.badge("Saumon grillé au citron"))
        assertEquals(RecipeFilter.SOUPE, RecipeFilters.badge("Gaspacho maison"))
        val ing = IngredientParser.parse("200 g d'oignons (environ)")
        assertEquals(200.0, ing.quantity)
        assertEquals("g", ing.unit)
        assertEquals("oignons", ing.name)

        val repo = InMemoryShopping()
        repo.add(
            listOf(
                ShoppingItem(name = "Lait", source = ItemSource.MANUEL),
                ShoppingItem(name = "Pain", source = ItemSource.MANUEL),
                ShoppingItem(name = "Beurre", source = ItemSource.MANUEL),
            ),
        )
        val ids = repo.all().map { it.id }
        repo.deleteByIds(listOf(ids[0], ids[2]))
        assertEquals(listOf("Pain"), repo.all().map { it.name })
    }
}
