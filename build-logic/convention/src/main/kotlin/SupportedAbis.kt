/**
 * ABI supportees par l'application.
 *
 * Definies UNE fois et partagees entre le module natif et le module application :
 * si l'APK declarait une ABI que le moteur natif ne fournit pas (armeabi-v7a,
 * x86), l'application s'installerait sur ces appareils puis planterait au premier
 * System.loadLibrary. Une bibliotheque tierce embarquant plus d'ABI que nous
 * suffit a creer cette situation, donc le filtre est pose des deux cotes.
 *
 * Uniquement du 64 bits : c'est ce qu'exige Google Play, et la contrainte des
 * pages de 16 Ko ne concerne de toute facon que les architectures 64 bits.
 */
internal val SUPPORTED_ABIS = listOf("arm64-v8a", "x86_64")

/**
 * ABI servies aux emulateurs seulement, retirees de l'APK de publication.
 *
 * ONNX Runtime x86_64 pese 39 Mo a lui seul. Un AAB est de toute facon decoupe par ABI par
 * le magasin ; c'est l'APK installe a la main qui les payait. Sans bibliotheque x86_64, un
 * appareil x86_64 refuse l'installation (INSTALL_FAILED_NO_MATCHING_ABIS) au lieu de
 * planter au chargement : l'invariant ci-dessus tient toujours.
 */
internal val EMULATOR_ONLY_ABIS = listOf("x86_64")
