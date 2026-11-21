package nt.xenforo.lang

import com.intellij.lang.Language
import com.intellij.psi.templateLanguages.TemplateLanguage
import nt.xenforo.XfBundle

object XfLanguage : Language("XenForoTemplate"), TemplateLanguage {
    private fun readResolve(): Any = XfLanguage

    override fun getDisplayName(): String = XfBundle.message("language.display.name")
}
