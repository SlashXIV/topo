# Feuille de route — Topo

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

103 tests verts au total : 38 instrumentes, 65 unitaires JVM.

**L'application est utilisable, tient un carnet et rejoue les sequences.** La boucle seance ->
bloc -> prises -> beta -> historique fonctionne de bout en bout sur appareil, chaque bloc porte
son statut, ses essais et son ressenti, et le beta se rejoue mouvement par mouvement. La suite
porte sur la lecture de la progression, puis le partage.

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

### S4 — Carnet du grimpeur *(livre)*

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

**Critere d'acceptation — atteint.** Trois tests de migration rejouent une base v1 authentique,
creee par Room depuis le schema JSON versionne au Sprint 1. La migration a en plus ete executee
sur la vraie base de l'emulateur, remplie au sprint precedent : `user_version` 1 -> 2, et les
6 prises et le mouvement ont survecu au remplacement de la table.

La regle du flash a ete verifiee a la main : deux essais enregistres, le statut bascule seul en
« en travail », et declarer un flash est refuse avec un message explicite sans corrompre l'etat.

---

### S5 — Identite : nom et icone *(livre)*

**Objectif.** Donner au projet une identite coherente avec les autres applications de son auteur,
avant que l'`applicationId` ne devienne impossible a changer.

**Le nom.** `Topo` est un compose anglais descriptif, la ou `Kalos` et `Popote` sont des mots
courts, chaleureux, porteurs d'un clin d'oeil que seul un initie decode. L'application devient
**Topo** : c'est le mot des grimpeurs pour le guide de voies, et l'application fabrique
litteralement des topos. Le double sens francais — « c'est quoi le topo ? », la marche a suivre —
decrit exactement ce qu'est un beta. Deux syllabes, finale vocalique : la serie se tient.

**Livrables.**

1. **Renommage complet**, et non cosmetique : `applicationId`, packages `com.topo.*`,
   namespaces de modules, bibliotheque native `libtopo_vision.so`, nom de base
   `topo.db`, identifiants des plugins de convention. Un `applicationId` ne se change plus
   une fois l'application publiee : le faire maintenant coute une heure, le faire au sprint 10
   coute un chantier.
2. **Icone vectorielle**, dessinee a la main en SVG puis convertie en `VectorDrawable` :
   - squircle ardoise `#3A4048`, lettre `T` blanche en graisse lourde, barre orange sous la
     lettre — la meme orange que le membre « main droite » du trace, pour que l'icone et l'ecran
     d'annotation parlent la meme langue ;
   - **prises d'escalade en fond**, en ardoise legerement plus claire : presentes sans etre
     lisibles, c'est ce qui distingue l'icone d'un simple monogramme.
3. **Couche `monochrome`** pour les icones thematiques d'Android 13+. Kalos ne l'a pas : c'est
   l'occasion de faire mieux, et cela coute une variante de la meme geometrie.
4. **Splash** aligne sur l'icone, comme Kalos le fait deja.

**Choix technique.** Tout en vectoriel, la ou Kalos utilise un PNG pre-padde a 64 % du canvas. Un
`VectorDrawable` reste net a toutes les densites, pese quelques kilo-octets au lieu d'une famille
de bitmaps, et se relit — une couleur se change dans le XML, pas dans un exporteur.

**Critere d'acceptation — atteint pour le nom et l'icone.** Renommage complet livre : les 67
tests passent sans modification autre que les imports, et l'audit 16 Ko reste conforme sur la
bibliotheque devenue `libtopo_vision.so`. L'icone a ete verifiee au lanceur sur appareil.

Deux corrections viennent de cette verification, et d'elle seule :
- les prises etaient des galets aux contours lisses ; a la taille d'une icone elles se lisaient
  comme de simples pastilles percees. Redessinees en triangles arrondis ;
- elles etaient placees trop pres des bords et le masque circulaire les avalait entierement —
  le fond apparaissait uni. Rapprochees a environ 31 du centre.

**Ecran de demarrage** livre avec le sprint suivant : meme avant-plan sur le meme fond que
l'icone, porte jusqu'a l'API 26 par `androidx.core:core-splashscreen`.

**Reste a faire.** La couche `monochrome` n'a pas pu etre verifiee visuellement : activer les
icones thematiques passe par un reglage du lanceur, hors de portee d'`adb`. Sa geometrie est
celle de l'avant-plan, qui lui est verifie.

---

### S6 — Rejeu anime du beta *(livre)*

**Objectif.** Rejouer la sequence mouvement par mouvement, comme une video du passage.

**Livrables.**
- Lecture sequentielle : chaque mouvement apparait a son tour, avec une interpolation du trait.
- Controles : lecture, pause, vitesse, image par image.
- Reutilisation integrale du canevas du Sprint 2 — aucun nouveau moteur de rendu.

**Contrainte.** L'animation s'appuie sur `Animatable` et reste confinee a la phase de dessin,
comme le trace : le rejeu ne doit pas recomposer l'arbre a chaque image.

**Critere d'acceptation — atteint.** Rejeu de 50 mouvements profile sur appareil via
`dumpsys gfxinfo` : **2102 trames en 35 s, soit 60 images par seconde soutenues, une seule trame
saccadee (0,05 %) et aucune vsync manquee**.

Mesure faite sur emulateur : les durees de trame absolues y sont grevees par le rendu logiciel,
mais « vsync manquee » et « trame saccadee » sont evalues contre la frequence reelle de
l'appareil et restent donc significatifs.

---

### S7 — Cotation : couleurs et niveaux *(livre)*

**Objectif.** Rendre la difficulte enregistrable et surtout COMPARABLE, condition sans laquelle
les statistiques de progression sont impossibles.

**Le constat de depart.** La colonne `grade` existait depuis le Sprint 1, `GradeChip` savait
l'afficher, mais `createBloc` passait toujours `null` et aucun ecran ne permettait de la saisir :
un champ mort. Aucun bloc ne pouvait porter de difficulte.

**Livrables.**
- Deux echelles chiffrees ORDONNEES, francaise et V, au choix dans les reglages.
- Un circuit de couleurs dont l'ordre, defini par l'utilisateur, EST la difficulte.
- Un bloc porte les deux : la salle etiquette une couleur, le grimpeur estime un niveau.
- Ecran de reglages avec editeur de circuit.
- Migration v2 -> v3, amorcant un circuit par defaut et la ligne de reglages.

**Le point d'architecture.** Une cotation en texte libre est inexploitable : « 7A » et « 7a »
deviennent deux niveaux et rien ne se trie. Les deux systemes se ramenent a la meme abstraction —
une liste ordonnee — et un bloc en tire un RANG, seule notion que les statistiques auront a
consommer, quel que soit le systeme affiche.

**Ergonomie, tiree du test appareil.** Reordonnancement par fleches et non par glisser-deposer :
le glissement necessaire au trace d'un beta etait passe inapercu jusqu'a ce qu'une ligne de texte
l'annonce. Un geste que rien ne signale est un geste que personne ne fait. Chaque ligne porte son
rang, les fleches sont desactivees aux extremites, et un second appui sur une valeur deja choisie
la retire.

**Critere d'acceptation — atteint.** Migration executee sur la base reelle du Xiaomi :
`user_version` 2 -> 3, seance, bloc, 9 prises et 7 mouvements conserves, essais et ressenti
intacts, circuit et reglages amorces. Trois tests de migration, dont un verifiant que supprimer
une couleur met `color_id` a NULL **sans** supprimer le bloc.

---

### S8 — Statistiques et progression

**Objectif.** Donner une raison de revenir dans l'application.

**Livrables.**
- Volume par seance, repartition par cotation, evolution du meilleur niveau.
- Taux de flash, blocs en projet.
- Agregations **calculees en SQL**, jamais en chargeant toutes les lignes pour les compter en
  Kotlin — le patron est deja pose par `observeSessionsWithCounts`.

**Dependance.** S4, dont cette phase consomme les statuts et les essais.

---

### S9 — Export et partage d'une image

**Objectif.** Envoyer un beta a un partenaire de grimpe.

**Livrables.**
- Rendu du canevas annote en PNG (`GraphicsLayer.toImageBitmap`).
- Partage par `FileProvider` et `ACTION_SEND`.
- Aucune synchronisation, aucun compte : l'image quitte l'application par le selecteur systeme,
  et rien d'autre ne sort du telephone.

---

### S10 — Detection automatique des prises

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

### S11 — Industrialisation

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
| `Topo` plutot que `Topo` | Un compose anglais descriptif ne tient pas la serie ouverte par `Kalos` et `Popote` : des mots courts, chaleureux, avec un clin d'oeil d'initie. `Topo` est le mot des grimpeurs pour le guide de voies, et « c'est quoi le topo ? » decrit exactement ce qu'est un beta. |
| Icone entierement vectorielle | Kalos utilise un PNG pre-padde a 64 % du canvas. Un VectorDrawable reste net a toutes les densites, pese quelques kilo-octets au lieu d'une famille de bitmaps, et se relit : une couleur se change dans le XML, pas dans un exporteur. |
| Aucun mipmap PNG herite | `minSdk = 26` garantit les icones adaptatives sur tout le parc vise. Les familles `mipmap-hdpi` a `mipmap-xxxhdpi` que Kalos doit embarquer sont ici inutiles : l'icone entiere tient dans trois fichiers vectoriels, 18 Ko au total. |
| Cotation tiree d'une liste figee, jamais saisie librement | « 7A » et « 7a » deviendraient deux niveaux et rien ne se trierait : une cotation libre rend toute statistique de progression impossible. |
| Reglages en base plutot qu'en DataStore | SQLite est l'unique source de verite du projet. Un second magasin apporterait sa propre histoire de sauvegarde et de migration pour deux enumerations, la ou une table suit le mecanisme deja teste. |
| Reordonnancement par fleches, pas par glisser-deposer | Le test sur appareil a montre qu'un geste que rien ne signale n'est pas decouvert : le glissement du trace etait reste inconnu jusqu'a ce qu'une ligne de texte l'annonce. |
| Progression du rejeu comptee en mouvements | Une fraction de 0 a 1 aurait oblige a remultiplier par le nombre de mouvements partout, et aurait rendu le pas a pas dependant de la longueur de la sequence. |
| Prises dessinees en triangles arrondis | Un contour ferme et lisse se lit comme un cercle a la taille d'une icone de lanceur. Verifie sur appareil : la premiere version ressemblait a des pastilles percees. |
| Statut a quatre etats plutot qu'un booleen `is_completed` | Un booleen ne distingue pas « jamais essaye » de « travaille sans succes ». Garder les deux cote a cote aurait cree deux sources de verite pour la meme question. |
| Table reconstruite a la migration plutot qu'un `ALTER` | `DROP COLUMN` date de SQLite 3.35 ; l'API 26 visee embarque la 3.19. Les cles etrangeres sont desactivees pendant l'echange, sinon le `DROP TABLE` emporterait prises et mouvements en cascade. |
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
