package fr.mescourses.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.mescourses.app.AppContainer
import fr.mescourses.app.MesCoursesApp

@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as MesCoursesApp).container

/** Crée un ViewModel à partir du conteneur de l'application. */
@Composable
inline fun <reified VM : ViewModel> containerViewModel(key: String? = null, crossinline create: (AppContainer) -> VM): VM {
    val container = appContainer()
    return viewModel(key = key, factory = viewModelFactory { initializer { create(container) } })
}

/** Type de la recette (entrée, plat, dessert…) : uniquement d'après les catégories du site. */
fun recipeCourse(r: fr.mescourses.core.model.Recipe): fr.mescourses.core.catalog.Course =
    fr.mescourses.core.catalog.Course.of(r.tags)

/** Place d'une recette dans le menu : entrée ou dessert ; null = plat principal. */
fun menuCourseOf(course: fr.mescourses.core.catalog.Course): fr.mescourses.core.catalog.Course? = when (course) {
    fr.mescourses.core.catalog.Course.DESSERT -> fr.mescourses.core.catalog.Course.DESSERT
    fr.mescourses.core.catalog.Course.ENTREE, fr.mescourses.core.catalog.Course.APERITIF -> fr.mescourses.core.catalog.Course.ENTREE
    else -> null
}
