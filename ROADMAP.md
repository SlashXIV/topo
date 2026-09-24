# Feuille de route — BetaClimb

Document de reference du projet. Il consolide les decisions prises au fil des sprints et detaille
chaque phase. Mis a jour a la fin de chaque sprint.

---

## Vision

Un carnet de bloc **hors-ligne**, pour grimpeurs de salle : photographier un pan de mur, y poser
les prises, tracer sa sequence de mouvements au doigt, et retrouver tout cela plus tard pour
progresser.

Trois criteres decident si une fonctionnalite entre ou non :

- **Utile au pied du mur.** L'application s'utilise debout, une main occupee, parfois sans reseau.
- **Sans compte, sans serveur.** Les donnees appartiennent a l'utilisateur et restent sur son
  telephone.
- **Instantanee.** Aucune attente perceptible entre un geste et son effet.

---

## Principes d'architecture

Cinq regles qui ne bougent pas. Toute proposition qui les contredit est refusee ou justifiee
explicitement dans le journal des decisions.

1. **Hors-ligne par construction.** SQLite est l'unique source de verite. Aucune dependance
   reseau, meme optionnelle.
2. **Le domaine est du JVM pur.** `:core:domain` ne peut pas dependre d'Android — la regle est
   appliquee par le plugin de convention Gradle, pas par discipline. Consequence : la logique
   metier se teste en millisecondes, sans emulateur.
3. **Un module, une responsabilite.** Les modules de fonctionnalite ne se connaissent pas entre
   eux ; ils communiquent par les interfaces du domaine.
4. **La base pilote l'interface.** Les ecrans s'abonnent a des `Flow` ; aucune notification
   manuelle, donc aucun affichage perime possible.
5. **Une affirmation de performance sans mesure est une opinion.** Chaque choix justifie par la
   performance est accompagne d'un test qui echouerait s'il cessait d'etre vrai.

---

## Etat actuel

| Sprint | Livre | Preuve |
|---|---|---|
| **S0** | Socle multi-modules, pont JNI, conformite 16 Ko | Tous les segments LOAD en `2**14` ; 6 tests sur noyau `PAGE_SIZE=16384` |
| **S1** | Room en mode WAL, cascades, CameraX, stockage interne | `PRAGMA journal_mode` = `wal` ; 18 tests instrumentes ; capture JPEG reelle sur appareil |
| **S2** | Canevas Compose, prises manuelles, magnetisation | Magnetisation mesuree a **0,62 us** sur 150 prises ; 7 tests gestuels, dont un valide par mutation |

55 tests verts au total : 37 instrumentes sur deux niveaux d'API, 18 unitaires JVM.

**L'application est utilisable.** La boucle seance -> bloc -> prises -> beta -> historique
fonctionne de bout en bout sur appareil. La suite porte sur ce qui la rend utile dans la duree :
le suivi des essais, le rejeu et les statistiques.

---

## Phases

### S3 — Boucle utilisateur complete *(livre)*

**Objectif.** Rendre l'application utilisable de bout en bout.

**Livrables.**
- Graphe de navigation type-safe (routes `@Serializable`).
- `:feature:session` : liste des seances, detail d'une seance avec ses blocs.
- Ajout d'un bloc par **appareil photo** ou par **galerie** (Photo Picker, sans permission).
- Composants partages dans `:core:ui` : etats vide, chargement, erreur ; vignette de bloc.
- Disparition de l'ecran de diagnostic du Sprint 0 : sa valeur de preuve appartient aux tests.

**Critere d'acceptation — atteint.** Parcours execute sur emulateur API 36 : seance creee,
photo importee depuis la galerie (copiee sous UUID dans le stockage prive, dimensions 1080x2400
lues sans decodage), quatre prises posees au doigt, beta magnetise trace et enregistre
(`sequence_order=0`, `limb_type=1`, source et cible pointant sur deux prises reelles), retour
arriere et donnees retrouvees apres relancement.

Deux defauts trouves par ce parcours et corriges : la barre d'outils d'annotation passait sous la
barre d'etat en affichage bord a bord, et l'ecran d'annotation n'avait aucun retour.

---

### S4 — Carnet du grimpeur

**Objectif.** Passer de « j'ai photographie un bloc » a « je suis ma progression dessus ».

**Livrables.**
- Statut d'un bloc : projet, travaille, **flash**, reussi.
- Compteur d'essais, date de reussite, ressenti libre.
- Filtres et tri dans le detail d'une seance.
- **Migration de schema v1 → v2**, avec test de migration rejouant une base v1 reelle.

**Pourquoi avant le rejeu et les statistiques.** Cette phase touche le schema. Une migration se
paie d'autant moins cher qu'elle arrive tot, et les statistiques (S6) consomment directement ces
donnees. Le schema exporte en JSON depuis le Sprint 1 rend cette migration verifiable plutot que
declarative.

**Critere d'acceptation.** Une base creee en v1 s'ouvre en v2 sans perte, prouve par un test de
migration.

---

### S5 — Rejeu anime du beta

**Objectif.** Rejouer la sequence mouvement par mouvement, comme une video du passage.

**Livrables.**
- Lecture sequentielle : chaque mouvement apparait a son tour, avec une interpolation du trait.
- Controles : lecture, pause, vitesse, image par image.
- Reutilisation integrale du canevas du Sprint 2 — aucun nouveau moteur de rendu.

**Contrainte.** L'animation s'appuie sur `Animatable` et reste confinee a la phase de dessin,
comme le trace : le rejeu ne doit pas recomposer l'arbre a chaque image.

**Critere d'acceptation.** Un rejeu de cinquante mouvements sans saccade, profile au moniteur GPU.

---

### S6 — Statistiques et progression

**Objectif.** Donner une raison de revenir dans l'application.

**Livrables.**
- Volume par seance, repartition par cotation, evolution du meilleur niveau.
- Taux de flash, blocs en projet.
- Agregations **calculees en SQL**, jamais en chargeant toutes les lignes pour les compter en
  Kotlin — le patron est deja pose par `observeSessionsWithCounts`.

**Dependance.** S4, dont cette phase consomme les statuts et les essais.

---

### S7 — Export et partage d'une image

**Objectif.** Envoyer un beta a un partenaire de grimpe.

**Livrables.**
- Rendu du canevas annote en PNG (`GraphicsLayer.toImageBitmap`).
- Partage par `FileProvider` et `ACTION_SEND`.
- Aucune synchronisation, aucun compte : l'image quitte l'application par le selecteur systeme,
  et rien d'autre ne sort du telephone.

---

### S8 — Detection automatique des prises

**Objectif.** Pre-remplir les prises au lieu de les poser une a une.

**Livrables.**
- Modele YOLO nano affine sur des prises d'escalade, elague et quantifie en INT8 (~5 Mo).
- Inference ONNX Runtime via l'Execution Provider XNNPACK, en C++ natif.
- Suppression des non-maxima cote natif.
- Branchement derriere l'interface `HoldDetector`, **sans qu'aucune couche superieure ne change**.

**Dependance externe, et seule du projet.** Le modele n'existe pas. Le placement manuel des prises
est donc le chemin **nominal** du MVP, pas un repli : l'application est complete sans modele, et
la detection automatique n'est qu'un accelerateur.

**Point de controle a ne pas oublier.** L'exigence d'alignement 16 Ko porte sur **toutes** les
bibliotheques natives de l'APK, dependances tierces comprises. L'AAR precompile d'ONNX Runtime a
un historique documente de non-conformite. A verifier a l'objdump **avant** de s'engager, avec
pour repli une recompilation depuis les sources.

**Critere d'acceptation.** Inference de bout en bout sous 100 ms sur appareil physique ARM64, et
audit d'alignement toujours vert.

---

### S9 — Industrialisation

**Objectif.** Transformer un code fonctionnel en produit livrable.

**Livrables.**
- Profilage memoire : fuites Java, liberation des structures C++ allouees par ONNX Runtime.
- R8 : minification, elimination du code mort, obscurcissement.
- Baseline profile pour reduire le temps de demarrage.
- Restauration d'etat apres terminaison du processus (`SavedStateHandle`).
- Audit final d'alignement 16 Ko et production de l'Android App Bundle.

---

## Journal des decisions

Choix tranches, avec leur raison. Les quatre premiers **corrigent** les specifications initiales.

| Decision | Raison |
|---|---|
| `common-page-size=16384`, pas `4096` | La valeur `4096` circule largement mais est incorrecte : le NDK met les deux a 4096 par defaut, la documentation Google prescrit 16384 pour les deux. |
| Deux cles etrangeres au lieu d'une table d'association pour les mouvements | Un mouvement a au plus une prise source et exactement une cible : relation 1-1, pas N-N. La cle primaire composee de la table proposee interdisait de toute facon plusieurs cibles. |
| Magnetisation calculee **en pixels** | L'espace normalise est anisotrope des que l'image n'est pas carree : un rayon exprime en normalise serait une ellipse a l'ecran. |
| `rememberUpdatedState` protege du **redemarrage**, pas de la capture perimee | Verifie par mutation : Compose reinitialise le gestionnaire de pointeur des que la lambda change d'instance, meme a cle constante. Le danger est cette reinitialisation, qui annule le geste en cours. |
| XNNPACK plutot que NNAPI | NNAPI est depreciee et le parc materiel est fragmente. Sur un micro-modele INT8, le CPU vectorise est souvent plus rapide que le NPU, sans surcout d'initialisation. |
| Recherche lineaire plutot que KD-Tree | Mesure : 0,62 us pour 150 prises, soit 0,004 % d'une trame a 60 FPS. Un arbre couterait plus a construire et invalider que la recherche qu'il remplace. |
| Tables classiques plutot que l'extension spatiale R-Tree | L'implementation SQLite d'Android n'active pas systematiquement R-Tree, et un mur depasse rarement 200 prises. Des index B-Tree suffisent. |
| Chemins d'image **relatifs** en base | Un chemin absolu change lors d'une restauration d'appareil ; une `content://` externe peut voir sa permission revoquee, ce qui casserait le fonctionnement hors ligne. |
| Deux emulateurs dans la matrice de test | Espresso 3.7.0, derniere version publiee, est incompatible avec l'API 37 (`NoSuchMethodException: InputManager.getInstance`). Les tests Compose tournent donc en API 36, le natif reste sur l'AVD 16 Ko. |
| Kotlin integre d'AGP 9 | AGP 9 refuse le plugin autonome `org.jetbrains.kotlin.android` avec son nouveau DSL. Effet de bord favorable : `jvmTarget` suit `compileOptions`, une valeur de moins a maintenir. |

---

## Hors perimetre

Ce que le projet ne fera pas, et pourquoi.

- **Synchronisation cloud, comptes utilisateurs.** Contredit le principe hors-ligne et imposerait
  une infrastructure, une politique de confidentialite et une authentification — sans rien
  apporter a l'usage au pied du mur.
- **Reseau social, partage de betas entre utilisateurs.** Autre produit, autres contraintes.
  L'export d'une image (S7) couvre le besoin reel de montrer une sequence a quelqu'un.
- **Reconnaissance de cotation par OCR.** Envisageable un jour, mais sans valeur tant que la
  detection des prises n'est pas fiable.
- **Segmentation fine des prises (masques polygonaux).** Les boites englobantes suffisent a la
  magnetisation. Un modele de segmentation coute nettement plus cher a l'inference pour un gain
  visuel, pas fonctionnel.
