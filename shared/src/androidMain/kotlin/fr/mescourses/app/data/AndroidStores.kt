package fr.mescourses.app.data

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

/** Même DataStore qu'avant le passage en multiplateforme (fichier datastore/settings.preferences_pb). */
val Context.settingsDataStore by preferencesDataStore(name = "settings")
