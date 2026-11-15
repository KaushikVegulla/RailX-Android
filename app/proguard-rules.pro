# Amplify 2.27 probes for the optional Smithy OkHttp4 engine with Class.forName
# and catches ClassNotFoundException before using its default engine. The
# optional adapter is not packaged by this app; all other missing classes fail R8.
-dontwarn aws.smithy.kotlin.runtime.http.engine.okhttp4.OkHttp4Engine
