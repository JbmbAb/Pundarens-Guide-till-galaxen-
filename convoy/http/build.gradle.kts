// :convoy:http – transporten mot reläet (relay/convoy-relay.mjs). Hämtar och lämnar förseglade
// kuvert med vanliga HTTP-anrop ur JDK:n. Inget nätverksbibliotek, och ingen nyckel passerar här.
dependencies {
    "api"(project(":convoy:api"))
    "testImplementation"(libs.junit)
}
