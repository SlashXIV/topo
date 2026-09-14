# ---------------------------------------------------------------------------
#  BetaClimb - regles R8
# ---------------------------------------------------------------------------
# Le pont JNI s'enregistre par RegisterNatives sur le NOM de la classe et les
# NOMS des methodes (cf. jni_bridge.cpp, tableau kMethods). R8 doit donc laisser
# NativeBridge intact : sans cette regle, l'obfuscation renomme la classe, le
# FindClass echoue dans JNI_OnLoad et System.loadLibrary plante - uniquement en
# build release, c'est-a-dire le jour de la mise en production.
-keep class com.betaclimb.vision.NativeBridge { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
