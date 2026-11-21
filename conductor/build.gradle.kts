// :conductor – svansdirigenten. Ren Kotlin. Händelser -> Illogical Engine -> SafetyGate -> uppspelning.
// Avvikelse E4-D2 (etapp4-protokollet): 03 lägger sammankopplingen i :app. Här ligger den i en egen
// ren modul så att den omfattas av beroendegrafstestet och kan testas på JVM.
dependencies {
    "implementation"(project(":truth:api"))
    "implementation"(project(":engine:api"))
    "implementation"(project(":safetygate"))
    "implementation"(project(":archive:api"))
    "testImplementation"(libs.junit)
    "testImplementation"(libs.coroutines.test)
    "testImplementation"(project(":engine"))
    "testImplementation"(project(":archive:core"))
}
