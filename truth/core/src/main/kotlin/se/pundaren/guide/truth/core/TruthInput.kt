package se.pundaren.guide.truth.core

/**
 * Märker allt som matar in data i sanningskärnan. Bara `:truth:device` får slå på den här
 * markeringen (kompilatorflaggan `-opt-in`), så ingen annan modul kan av misstag skriva sanningsdata.
 * Motsvarar G-2 i 03 ("allt muterande är internt") nu när logiken ligger i en egen ren modul.
 * Arkitekturtestet kontrollerar att ingen annan modul än `:truth:device` slår på markeringen.
 */
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "Bara :truth:device får mata sanningskärnan (03, G-2).",
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS, AnnotationTarget.CONSTRUCTOR)
annotation class TruthInput
