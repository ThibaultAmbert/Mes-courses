package fr.mescourses.core

import fr.mescourses.core.llm.PromptTools
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PromptToolsTest {
    @Test fun appel_simple() {
        val c = PromptTools.parse("""{"outil": "add_dish", "arguments": {"numero": 3}}""")!!
        assertEquals("add_dish", c.name)
        assertEquals("3", c.arguments["numero"].toString())
    }

    @Test fun appel_dans_un_bloc_de_code_et_cles_anglaises() {
        val c = PromptTools.parse("```json\n{\"tool\":\"search\",\"args\":{\"q\":\"poulet\"}}\n```")!!
        assertEquals("search", c.name)
        assertEquals("\"poulet\"", c.arguments["q"].toString())
    }

    @Test fun sans_arguments() {
        assertEquals("summary", PromptTools.parse("""{"outil":"summary"}""")!!.name)
    }

    @Test fun texte_normal_n_est_pas_un_appel() {
        assertNull(PromptTools.parse("Voici ton menu de la semaine."))
        assertNull(PromptTools.parse("""Je propose {"outil":"x"} puis autre chose"""))
        assertNull(PromptTools.parse("""{"autre": 1}"""))
    }

    @Test fun debut_de_reponse() {
        assertTrue(PromptTools.mayBeCall(""))
        assertTrue(PromptTools.mayBeCall("  {\"ou"))
        assertTrue(PromptTools.mayBeCall("``"))
        assertTrue(PromptTools.mayBeCall("```json"))
        assertFalse(PromptTools.mayBeCall("Bonjour"))
        assertFalse(PromptTools.mayBeCall("`code`"))
    }

    @Test fun consigne_liste_les_outils() {
        val text = PromptTools.instructions(listOf("""{"name":"a"}""", """{"name":"b"}"""))
        assertTrue("- {\"name\":\"a\"}" in text && "- {\"name\":\"b\"}" in text && PromptTools.RESULT_PREFIX in text)
    }
}
