package fr.mescourses.app.data

import fr.mescourses.core.model.Ingredient
import fr.mescourses.core.model.ItemSource
import fr.mescourses.core.model.MenuDish
import fr.mescourses.core.model.MenuHistoryEntry
import fr.mescourses.core.model.PlannedMeal
import fr.mescourses.core.model.Portions
import fr.mescourses.core.model.Recipe
import fr.mescourses.core.model.RecipeStatus
import fr.mescourses.core.model.ShoppingItem
import fr.mescourses.core.model.stripAccents
import fr.mescourses.core.repository.MealPlanRepository
import fr.mescourses.core.repository.MenuDraftRepository
import fr.mescourses.core.repository.MenuHistoryRepository
import fr.mescourses.core.repository.RecipeRepository
import fr.mescourses.core.repository.ShoppingRepository
import fr.mescourses.core.currentTimeMillis
import fr.mescourses.core.plusYears
import fr.mescourses.core.todayLocal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }
private val ingredientsSer = ListSerializer(Ingredient.serializer())
private val stringsSer = ListSerializer(String.serializer())

fun RecipeEntity.toModel() = Recipe(
    id = id,
    title = title,
    servings = servings,
    ingredients = runCatching { json.decodeFromString(ingredientsSer, ingredientsJson) }.getOrDefault(emptyList()),
    steps = runCatching { json.decodeFromString(stringsSer, stepsJson) }.getOrDefault(emptyList()),
    sourceUrl = sourceUrl,
    imageUrl = imageUrl,
    prepMinutes = prepMinutes,
    cookMinutes = cookMinutes,
    tags = runCatching { json.decodeFromString(stringsSer, tagsJson) }.getOrDefault(emptyList()),
    status = runCatching { RecipeStatus.valueOf(status) }.getOrDefault(RecipeStatus.HISTORIQUE),
    timesCooked = timesCooked,
    lastCookedAt = lastCookedAt,
    description = description,
    notes = notes,
)

fun Recipe.toEntity() = RecipeEntity(
    id = id,
    title = title,
    servings = servings,
    ingredientsJson = json.encodeToString(ingredientsSer, ingredients),
    stepsJson = json.encodeToString(stringsSer, steps),
    sourceUrl = sourceUrl,
    imageUrl = imageUrl,
    prepMinutes = prepMinutes,
    cookMinutes = cookMinutes,
    tagsJson = json.encodeToString(stringsSer, tags),
    status = status.name,
    timesCooked = timesCooked,
    lastCookedAt = lastCookedAt,
    description = description,
    notes = notes,
)

class RoomRecipeRepository(private val dao: RecipeDao) : RecipeRepository {
    override fun observeAll(): Flow<List<Recipe>> = dao.observeAll().map { l -> l.map { it.toModel() } }.flowOn(Dispatchers.IO)
    fun observe(id: Long): Flow<Recipe?> = dao.observe(id).map { it?.toModel() }.flowOn(Dispatchers.IO)
    override suspend fun all(): List<Recipe> = withContext(Dispatchers.IO) { dao.all().map { it.toModel() } }
    override suspend fun get(id: Long): Recipe? = withContext(Dispatchers.IO) { dao.get(id)?.toModel() }

    /** Recherche insensible aux accents dans le titre, les tags et les ingrédients. */
    override suspend fun search(query: String): List<Recipe> = withContext(Dispatchers.IO) {
        val words = query.lowercase().stripAccents().split(' ').filter { it.isNotBlank() }
        val all = dao.all().map { it.toModel() }
        if (words.isEmpty()) return@withContext all
        all.filter { r ->
            val haystack = (r.title + " " + r.tags.joinToString(" ") + " " + r.ingredients.joinToString(" ") { it.name })
                .lowercase().stripAccents()
            words.all { it in haystack }
        }
    }

    override suspend fun save(recipe: Recipe): Long = withContext(Dispatchers.IO) {
        val entity = recipe.toEntity()
        if (recipe.id == 0L) dao.upsert(entity) else if (dao.update(entity) > 0) recipe.id else dao.upsert(entity)
    }

    override suspend fun delete(id: Long) = withContext(Dispatchers.IO) { dao.delete(id) }
}

private fun PlannedMealEntity.toModel() =
    PlannedMeal(id, date, recipeId, recipeTitle, Portions(adults, children), cooked, course.toCourse())

private fun PlannedMeal.toEntity() =
    PlannedMealEntity(id, date, recipeId, recipeTitle, portions.adults, portions.children, cooked, course?.name)

private fun String?.toCourse(): fr.mescourses.core.catalog.Course? =
    this?.let { n -> fr.mescourses.core.catalog.Course.entries.firstOrNull { it.name == n } }

class RoomMealPlanRepository(private val dao: MealDao) : MealPlanRepository {
    override fun observeAll(): Flow<List<PlannedMeal>> = dao.observeAll().map { l -> l.map { it.toModel() } }.flowOn(Dispatchers.IO)
    override suspend fun all() = withContext(Dispatchers.IO) { dao.all().map { it.toModel() } }
    override suspend fun add(meal: PlannedMeal): Long = withContext(Dispatchers.IO) { dao.insert(meal.copy(id = 0).toEntity()) }
    override suspend fun update(meal: PlannedMeal) = withContext(Dispatchers.IO) { dao.update(meal.toEntity()) }
    override suspend fun delete(id: Long) = withContext(Dispatchers.IO) { dao.delete(id) }
    override suspend fun clear() = withContext(Dispatchers.IO) { dao.clear() }
}

class RoomMenuDraftRepository(private val dao: MenuDraftDao) : MenuDraftRepository {
    private fun MenuDraftEntity.toModel() = MenuDish(
        title, description, Portions(adults, children), recipeId,
        runCatching { json.decodeFromString(stringsSer, mainIngredients) }.getOrDefault(emptyList()),
        sourceUrl, imageUrl, totalMinutes, invented, course.toCourse(),
    )

    override fun observe(): Flow<List<MenuDish>> = dao.observe().map { l -> l.map { it.toModel() } }.flowOn(Dispatchers.IO)
    override suspend fun get(): List<MenuDish> = withContext(Dispatchers.IO) { dao.all().map { it.toModel() } }
    override suspend fun set(dishes: List<MenuDish>) = withContext(Dispatchers.IO) {
        dao.replace(
            // Entrées, plats puis desserts : l'ordre de la liste est celui de l'écran, et les numéros (« plat 3 ») y correspondent.
            dishes.sortedBy { it.courseRank }.mapIndexed { i, d ->
                MenuDraftEntity(
                    i, d.title, d.description, d.portions.adults, d.portions.children, d.recipeId,
                    json.encodeToString(stringsSer, d.mainIngredients),
                    d.sourceUrl, d.imageUrl, d.totalMinutes, d.invented, d.course?.name,
                )
            },
        )
    }
}

class RoomMenuHistoryRepository(private val dao: MenuHistoryDao) : MenuHistoryRepository {
    override suspend fun all(): List<MenuHistoryEntry> = withContext(Dispatchers.IO) {
        dao.all().map {
            MenuHistoryEntry(
                it.id, it.date, it.title, it.sourceUrl, it.kind,
                runCatching { json.decodeFromString(stringsSer, it.mainIngredients) }.getOrDefault(emptyList()),
            )
        }
    }

    override suspend fun add(entries: List<MenuHistoryEntry>) = withContext(Dispatchers.IO) {
        if (entries.isEmpty()) return@withContext
        dao.insertAll(entries.map { MenuHistoryEntity(0, it.date, it.title, it.sourceUrl, it.kind, json.encodeToString(stringsSer, it.mainIngredients)) })
        // Un an suffit largement (la variété se joue sur quelques semaines).
        dao.deleteBefore(todayLocal().plusYears(-1).toString())
    }
}

private fun ShoppingItemEntity.toModel() = ShoppingItem(
    id, name, quantity, unit, runCatching { ItemSource.valueOf(source) }.getOrDefault(ItemSource.MANUEL), origin, checked,
)

private fun ShoppingItem.toEntity() = ShoppingItemEntity(
    id = id, name = name, quantity = quantity, unit = unit, source = source.name, origin = origin, checked = checked,
)

class RoomShoppingRepository(private val dao: ShoppingDao) : ShoppingRepository {
    override fun observeAll(): Flow<List<ShoppingItem>> = dao.observeAll().map { l -> l.map { it.toModel() } }.flowOn(Dispatchers.IO)
    override suspend fun all() = withContext(Dispatchers.IO) { dao.all().map { it.toModel() } }
    override suspend fun add(items: List<ShoppingItem>) = withContext(Dispatchers.IO) { dao.insertAll(items.map { it.copy(id = 0).toEntity() }) }
    override suspend fun update(item: ShoppingItem) = withContext(Dispatchers.IO) {
        // conserve la date de création (ordre d'affichage)
        val existing = dao.get(item.id)
        dao.update(item.toEntity().copy(createdAt = existing?.createdAt ?: currentTimeMillis()))
    }
    override suspend fun delete(id: Long) = withContext(Dispatchers.IO) { dao.delete(id) }
    // Par paquets : SQLite limite le nombre de paramètres d'une requête.
    override suspend fun deleteByIds(ids: List<Long>) = withContext(Dispatchers.IO) { ids.chunked(500).forEach { dao.deleteByIds(it) } }
    override suspend fun clearChecked() = withContext(Dispatchers.IO) { dao.clearChecked() }
    override suspend fun setChecked(ids: List<Long>, checked: Boolean) = withContext(Dispatchers.IO) { ids.chunked(500).forEach { dao.setChecked(it, checked) } }
}

/** Mémoire des choix produit (présélection lors des envois suivants au panier). */
class RoomProductMemory(private val dao: IngredientProductDao) : fr.mescourses.core.cart.ProductMemory {
    override suspend fun get(key: String): fr.mescourses.core.carrefour.Product? = dao.get(key)?.let {
        fr.mescourses.core.carrefour.Product(
            id = it.ean, name = it.name, brand = it.brand, price = it.price, identifiers = mapOf("ean" to it.ean),
        )
    }

    override suspend fun remember(key: String, product: fr.mescourses.core.carrefour.Product) =
        dao.upsert(IngredientProductEntity(key, product.ean, product.name, product.brand, product.price))
}
