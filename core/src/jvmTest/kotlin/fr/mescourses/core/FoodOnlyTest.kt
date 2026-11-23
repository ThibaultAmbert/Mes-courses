package fr.mescourses.core

import fr.mescourses.core.cart.CartPlanner
import fr.mescourses.core.carrefour.Product
import fr.mescourses.core.model.ItemSource
import fr.mescourses.core.model.ShoppingItem
import fr.mescourses.core.shopping.ShoppingListMerger
import kotlinx.coroutines.flow.toList
import kotlin.test.Test
import kotlin.test.assertEquals

class FoodOnlyTest {
    private val creme = Product(id = "1", name = "Crème fraîche épaisse 30 %")
    private val visage = Product(id = "2", name = "Crème hydratante visage peaux sèches")
    private val croquettes = Product(id = "3", name = "Croquettes pour chat au poulet")
    private val lessive = Product(id = "4", name = "Lessive liquide 2 L")

    @Test
    fun `article de recette - les produits non alimentaires sont ecartes`() {
        val item = ShoppingItem(name = "Crème fraîche", source = ItemSource.RECETTE)
        assertEquals(listOf(creme), CartPlanner.foodOnly(item, listOf(creme, visage)))
        val poulet = ShoppingItem(name = "Poulet", source = ItemSource.RECETTE)
        val filet = Product(id = "5", name = "Filet de poulet x2")
        assertEquals(listOf(filet), CartPlanner.foodOnly(poulet, listOf(croquettes, filet)))
    }

    @Test
    fun `aliment ajoute a la main - filtre aussi`() {
        val item = ShoppingItem(name = "Crème", source = ItemSource.MANUEL)
        assertEquals(listOf(creme), CartPlanner.foodOnly(item, listOf(creme, visage)))
    }

    @Test
    fun `article non alimentaire ajoute a la main - aucun filtre`() {
        val l = ShoppingItem(name = "Lessive", source = ItemSource.MANUEL)
        assertEquals(listOf(lessive, creme), CartPlanner.foodOnly(l, listOf(lessive, creme)))
        val c = ShoppingItem(name = "Croquettes pour chat", source = ItemSource.MANUEL)
        assertEquals(listOf(croquettes), CartPlanner.foodOnly(c, listOf(croquettes)))
        val s = ShoppingItem(name = "Shampooing", source = ItemSource.MANUEL)
        val sh = Product(id = "6", name = "Shampooing doux 250 ml")
        assertEquals(listOf(sh, creme), CartPlanner.foodOnly(s, listOf(sh, creme)))
    }

    @Test
    fun `rayon donne par l'enseigne - produit d'hygiene ecarte`() {
        val item = ShoppingItem(name = "Huile d'amande douce")
        val soin = Product(id = "7", name = "Huile d'amande douce bio 100 ml", category = "Hygiène et beauté > Soin du corps")
        val cuisine = Product(id = "8", name = "Huile d'amande douce 50 cl", category = "Épicerie salée > Huiles")
        assertEquals(listOf(cuisine), CartPlanner.foodOnly(item, listOf(soin, cuisine)))
    }

    @Test
    fun `choix memorise non alimentaire - oublie et recherche relancee`() = kotlinx.coroutines.test.runTest {
        val memory = object : fr.mescourses.core.cart.ProductMemory {
            override suspend fun get(key: String) = visage
            override suspend fun remember(key: String, product: Product) {}
        }
        val backend = FakeFoodBackend(listOf(visage, creme))
        val line = CartPlanner.propose(backend, listOf(ShoppingItem(name = "Crème fraîche")), memory = memory, pauseMs = 0).toList().single()
        assertEquals(false, line.remembered)
        assertEquals(creme, line.product)
    }

    @Test
    fun `tout ecarte - la liste d'origine est gardee`() {
        val item = ShoppingItem(name = "Poulet", source = ItemSource.RECETTE)
        assertEquals(listOf(croquettes), CartPlanner.foodOnly(item, listOf(croquettes)))
    }

    @Test
    fun `fusion recette + main - l'article reste manuel`() {
        val merged = ShoppingListMerger.merge(
            listOf(
                ShoppingItem(name = "Crème fraîche", quantity = 20.0, unit = "cl", source = ItemSource.RECETTE),
                ShoppingItem(name = "Crème fraîche", quantity = 20.0, unit = "cl", source = ItemSource.MANUEL),
            ),
        )
        assertEquals(ItemSource.MANUEL, merged.single().source)
    }

    private class FakeFoodBackend(val results: List<Product>) : fr.mescourses.core.cart.RetailerBackend {
        override val retailer = fr.mescourses.core.retail.Retailer.AUCHAN
        override suspend fun search(query: String, limit: Int) = results.take(limit)
        override suspend fun searchWithUnavailable(query: String, limit: Int) = results.take(limit)
        override suspend fun cart() = error("")
        override suspend fun addToCart(product: Product, quantity: Int) = error("")
        override suspend fun setQuantity(ean: String, quantity: Int) = error("")
        override suspend fun timeslots(max: Int) = emptyList<fr.mescourses.core.carrefour.TimeSlot>()
        override suspend fun sessionProbe() = null
    }
}
