package nt.xenforo.meta

import nt.xenforo.model.XfValueType

class XfGlobal(val name: String, val typeText: String, val type: XfValueType? = null, val source: XfGlobalSource = XfGlobalSource.APP)

enum class XfGlobalSource(val className: String, val methodName: String) {
    APP("\\XF\\App", "getGlobalTemplateData"),
    MAILER("\\XF\\Mail\\Mailer", "getDefaultTemplateParams"),
}

object XfGlobals {
    const val ROOT: String = "xf"

    val VARIABLES: List<String> = listOf(ROOT, "__globals", "__vars")

    private val USER = XfValueType.PhpObject(listOf("\\XF\\Entity\\User"))

    val ALL: List<XfGlobal> = listOf(
        XfGlobal("versionVisible", "string"),
        XfGlobal("versionId", "int"),
        XfGlobal("version", "string"),
        XfGlobal("app", "App", XfValueType.PhpObject(listOf("\\XF\\App"))),
        XfGlobal("appType", "string"),
        XfGlobal("request", "Request", XfValueType.PhpObject(listOf("\\XF\\Http\\Request"))),
        XfGlobal("uri", "string"),
        XfGlobal("fullUri", "string"),
        XfGlobal("time", "int"),
        XfGlobal("timeDetails", "array", XfValueType.Keys(listOf("tomorrow", "now", "today", "todayDow", "yesterday", "week", "month", "year"))),
        XfGlobal("debug", "bool"),
        XfGlobal("development", "bool"),
        XfGlobal("designer", "bool"),
        XfGlobal("visitor", "User", USER),
        XfGlobal("session", "Session", XfValueType.PhpObject(listOf("\\XF\\Session\\Session"))),
        XfGlobal("cookie", "array", XfValueType.Keys(listOf("prefix", "path", "domain", "secure"))),
        XfGlobal("cookieConsent", "CookieConsent", XfValueType.PhpObject(listOf("\\XF\\CookieConsent"))),
        XfGlobal("enableRtnProtect", "bool"),
        XfGlobal("serviceWorkerPath", "string"),
        XfGlobal("language", "Language", XfValueType.PhpObject(listOf("\\XF\\Language"))),
        XfGlobal("style", "Style", XfValueType.PhpObject(listOf("\\XF\\Style"))),
        XfGlobal("isRtl", "bool"),
        XfGlobal("options", "Options", XfValueType.Options),
        XfGlobal("reactions", "array"),
        XfGlobal("reactionsActive", "array"),
        XfGlobal("addOns", "array", XfValueType.AddOns),
        XfGlobal("runJobs", "bool"),
        XfGlobal("simpleCache", "SimpleCache", XfValueType.PhpObject(listOf("\\XF\\SimpleCache"))),
        XfGlobal("livePayments", "bool"),
        XfGlobal("fullJs", "bool"),
        XfGlobal("contactUrl", "string"),
        XfGlobal("privacyPolicyUrl", "string"),
        XfGlobal("tosUrl", "string"),
        XfGlobal("homePageUrl", "string"),
        XfGlobal("helpPageCount", "int"),
        XfGlobal("uploadMaxFilesize", "int"),
        XfGlobal("allowedVideoExtensions", "array"),
        XfGlobal("allowedAudioExtensions", "array"),
        XfGlobal("searchAutoComplete", "bool"),
        XfGlobal("reply", "array", XfValueType.Keys(listOf("controller", "action", "section", "containerKey", "contentKey", "view", "template"))),
        XfGlobal("toUser", "User", USER, XfGlobalSource.MAILER),
    )

    private val byName = ALL.associateBy { it.name }

    fun find(name: String): XfGlobal? = byName[name]
}
