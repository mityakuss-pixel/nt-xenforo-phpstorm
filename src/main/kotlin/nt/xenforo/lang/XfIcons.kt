package nt.xenforo.lang

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

object XfIcons {
    @JvmField
    val FILE: Icon = IconLoader.getIcon("/icons/xenforoTemplate.svg", XfIcons::class.java)

    @JvmField
    val TAG: Icon = IconLoader.getIcon("/icons/xfTag.svg", XfIcons::class.java)
}
