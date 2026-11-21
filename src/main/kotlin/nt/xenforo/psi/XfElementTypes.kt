package nt.xenforo.psi

import com.intellij.psi.tree.IElementType
import nt.xenforo.lang.XfLanguage
import org.jetbrains.annotations.NonNls

class XfTokenType(@NonNls debugName: String) : IElementType(debugName, XfLanguage) {
    override fun toString(): String = "XfTokenType." + super.toString()
}

class XfElementType(@NonNls debugName: String) : IElementType(debugName, XfLanguage)
