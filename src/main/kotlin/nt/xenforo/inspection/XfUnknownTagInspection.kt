package nt.xenforo.inspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import nt.xenforo.XfBundle
import nt.xenforo.meta.XfTags
import nt.xenforo.psi.XfBranchTag
import nt.xenforo.psi.XfStartTag
import nt.xenforo.psi.XfTagHeader
import nt.xenforo.psi.XfVisitor

class XfUnknownTagInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = object : XfVisitor() {
        override fun visitStartTag(o: XfStartTag) = check(o)

        override fun visitBranchTag(o: XfBranchTag) = check(o)

        private fun check(header: XfTagHeader) {
            val nameElement = header.tagNameElement ?: return
            val name = header.tagName
            if (name.isEmpty() || XfTags.isKnown(name)) return
            holder.registerProblem(nameElement, XfBundle.message("inspection.unknown.tag.message", name))
        }
    }
}
