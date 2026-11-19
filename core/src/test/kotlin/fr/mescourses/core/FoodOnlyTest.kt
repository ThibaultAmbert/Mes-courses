package fr.mescourses.core

import fr.mescourses.core.cart.CartPlanner
import fr.mescourses.core.carrefour.Product
import fr.mescourses.core.model.ItemSource
import fr.mescourses.core.model.ShoppingItem
import fr.mescourses.core.shopping.ShoppingListMerger
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
    fun `article ajoute a la main - aucun filtre`() {
        val item = ShoppingItem(name = "Crème", source = ItemSource.MANUEL)
        assertEquals(listOf(creme, visage), CartPlanner.foodOnly(item, listOf(creme, visage)))
        val l = ShoppingItem(name = "Lessive", source = ItemSource.MANUEL)
        assertEquals(listOf(lessive), CartPlanner.foodOnly(l, listOf(lessive)))
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
}
