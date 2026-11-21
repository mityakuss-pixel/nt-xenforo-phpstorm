package nt.xenforo.meta

object XfGlobals {
    val VARIABLES: List<String> = listOf("xf", "__globals", "__vars")

    val XF_KEYS: List<String> = listOf(
        "versionVisible", "versionId", "version", "app", "appType", "request", "uri", "fullUri", "time",
        "timeDetails", "debug", "development", "designer", "visitor", "session", "cookie", "cookieConsent",
        "enableRtnProtect", "serviceWorkerPath", "language", "style", "isRtl", "options", "reactions",
        "reactionsActive", "addOns", "runJobs", "simpleCache", "livePayments", "fullJs", "contactUrl",
        "privacyPolicyUrl", "tosUrl", "homePageUrl", "helpPageCount", "uploadMaxFilesize", "allowedVideoExtensions",
    )
}
