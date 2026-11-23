package fr.mescourses.core.shopping

import fr.mescourses.core.ingredients.IngredientMapper
import fr.mescourses.core.menu.IngredientOverlap

/** Rayons du magasin, dans l'ordre d'un parcours habituel. */
enum class Aisle(val label: String, val emoji: String) {
    FRUITS_LEGUMES("Fruits & légumes", "🥕"),
    BOULANGERIE("Boulangerie", "🥖"),
    BOUCHERIE("Boucherie", "🥩"),
    POISSONNERIE("Poissonnerie", "🐟"),
    CREMERIE("Crèmerie & œufs", "🧀"),
    EPICERIE("Épicerie", "🥫"),
    BOISSONS("Boissons", "🧃"),
    MAISON("Hygiène & maison", "🧴"),
    AUTRES("Autres", "📦"),
    ;

    companion object {
        private val keywords: List<Pair<Aisle, List<String>>> = listOf(
            MAISON to listOf("lessive", "papier toilette", "essuie tout", "savon", "shampo", "dentifrice", "eponge", "liquide vaisselle", "sac poubelle", "couche", "mouchoir", "gel douche", "deodorant"),
            BOISSONS to listOf("jus", "eau gazeuse", "eau minerale", "biere", "vin", "sirop", "soda", "cafe", "the"),
            POISSONNERIE to listOf("saumon", "cabillaud", "colin", "merlu", "lieu", "thon frais", "sardine", "maquereau", "truite", "dorade", "crevette", "moule", "poisson", "saint jacque"),
            BOUCHERIE to listOf("poulet", "dinde", "boeuf", "veau", "porc", "agneau", "canard", "lapin", "jambon", "lardon", "saucisse", "viande", "steak", "escalope", "magret", "chorizo", "bacon"),
            CREMERIE to listOf("lait", "creme", "beurre", "fromage", "comte", "parmesan", "chevre", "feta", "mozzarella", "ricotta", "emmental", "gruyere", "yaourt", "skyr", "oeuf", "mascarpone", "reblochon", "tofu"),
            BOULANGERIE to listOf("pain", "baguette", "brioche", "pate brisee", "pate feuilletee", "pate a pizza", "tortilla", "wrap"),
            FRUITS_LEGUMES to listOf(
                "carotte", "courgette", "poireau", "chou", "epinard", "tomate", "brocoli", "haricot vert", "poivron", "aubergine",
                "champignon", "salade", "laitue", "potiron", "potimarron", "butternut", "courge", "navet", "panais", "betterave",
                "fenouil", "celeri", "endive", "blette", "radis", "concombre", "artichaut", "asperge", "mache", "roquette",
                "pomme de terre", "patate douce", "oignon", "echalote", "ail", "persil", "coriandre", "basilic", "ciboulette",
                "menthe", "thym", "romarin", "gingembre", "citron", "orange", "pomme", "poire", "banane", "kiwi", "fraise",
                "framboise", "raisin", "clementine", "avocat", "mangue", "abricot", "peche", "prune", "cerise", "melon", "legume", "fruit",
            ),
            EPICERIE to listOf(
                "lait de coco", "riz", "pate", "spaghetti", "farine", "sucre", "sel", "poivre", "huile", "vinaigre", "lentille", "pois chiche",
                "haricot", "semoule", "couscous", "boulgour", "quinoa", "conserve", "bouillon", "epice", "cumin", "curry",
                "paprika", "moutarde", "miel", "chocolat", "levure", "noix", "amande", "noisette", "olive",
                "sauce", "concentre", "coulis", "flocon", "cereale", "biscuit", "confiture",
            ),
        )

        private val compiledKeywords: List<Pair<String, Aisle>> by lazy {
            keywords.flatMap { (aisle, kws) ->
                kws.map { kw -> (" " + ShoppingListMerger.key(kw) + " ") to aisle }
            }
        }

        /** Mots d'un produit non alimentaire que le rayon seul ne repère pas (« crème hydratante visage »…). */
        private val NON_FOOD = listOf(
            "visage", "corps", "mains", "cheveux", "levres", "soin", "hydratant", "hydratante", "cosmetique", "maquillage",
            "demaquillant", "creme solaire", "apres soleil", "rasage", "epilation", "shampooing", "apres shampooing",
            "nettoyant", "detergent", "desinfectant", "detartrant", "adoucissant", "javel", "vaisselle", "lave vaisselle",
            "parfum d ambiance", "desodorisant", "bougie", "insecticide",
            "chat", "chien", "chaton", "chiot", "croquette", "litiere", "animal", "animaux", "oiseau", "rongeur",
            "lingette", "coton", "pansement", "dentaire", "bain de bouche",
        ).map { " " + ShoppingListMerger.key(it) + " " }

        /** Produit non alimentaire (hygiène, entretien, cosmétique, animaux) d'après son nom. */
        fun isNonFood(productName: String): Boolean {
            if (of(productName) == MAISON) return true
            val k = " " + ShoppingListMerger.key(productName) + " "
            return NON_FOOD.any { k.contains(it) }
        }

        /**
         * Le mot-clé trouvé le plus tôt dans le nom décide, le plus long en cas d'égalité :
         * « lait de coco » → épicerie (pas crèmerie), « crème de marrons » → crèmerie… à corriger à la main au besoin.
         */
        fun of(name: String): Aisle = IngredientMapper.aisleOverride(IngredientOverlap.key(name)) ?: computed(name)

        private fun computed(name: String): Aisle {
            val k = " " + ShoppingListMerger.key(name) + " "
            var bestPos = Int.MAX_VALUE
            var bestLen = -1
            var bestAisle = AUTRES
            for ((w, aisle) in compiledKeywords) {
                val pos = k.indexOf(w)
                if (pos < 0) continue
                if (pos < bestPos || (pos == bestPos && w.length > bestLen)) {
                    bestPos = pos
                    bestLen = w.length
                    bestAisle = aisle
                }
            }
            return bestAisle
        }
    }
}
