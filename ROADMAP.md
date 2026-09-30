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
| **S3** | Boucle utilisateur complete, navigation type-safe | Parcours seance -> bloc -> prises -> beta execute sur emulateur, donnees retrouvees apres relancement |
| **S4** | Carnet du grimpeur, migration v1 -> v2 | 3 tests de migration sur une base v1 authentique ; migration executee sur la base reelle |
| **S5** | Identite : nom Topo, icone vectorielle, splash | Renommage sans regression ; icone verifiee au lanceur sur appareil |
| **S6** | Rejeu anime du beta | 50 mouvements a **60 i/s soutenues**, 0,05 % de trames saccadees (`dumpsys gfxinfo`) |
| **S7** | Cotation : echelles ordonnees et circuit de couleurs | Migration v2 -> v3 executee sur la base reelle du Xiaomi |
| **S8** | Statistiques et progression | Regles d'agregation couvertes par les tests JVM du domaine |
| **S9** | Export et partage d'une image du beta | PNG relu via le `FileProvider` ; selecteur systeme ouvert sur emulateur, export complet sur appareil |
| **S10** | Detection automatique des prises | mAP50 **0,879** sur le jeu de test ; **60,7 ms** de bout en bout sur Xiaomi ARM64 en release |

134 tests verts au total : 55 instrumentes, 79 unitaires JVM.

**La detection automatique est en place.** Un appui propose les prises d'une photo, qui se
corrigent avec les gestes de la pose manuelle. Elle detecte toutes les prises du mur : ne
garder que celles d'un bloc est l'objet de S11.

**L'application est complete sans modele.** La boucle seance -> bloc -> prises -> beta ->
historique fonctionne de bout en bout sur appareil ; chaque bloc porte son statut, sa cotation et
son ressenti, le beta se rejoue et se partage en image. La suite est la detection automatique des
prises, seule phase qui depende d'un livrable externe.

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

### S8 — Statistiques et progression *(livre)*

**Objectif.** Donner une raison de revenir dans l'application.

**Livrables.**
- Volume par seance, repartition par cotation, evolution du meilleur niveau.
- Taux de flash, blocs en projet.
- Agregations calculees dans le domaine, en Kotlin — et non en SQL comme le prevoyait le plan
  initial : voir le journal des decisions.

**Dependance.** S4, dont cette phase consomme les statuts et les essais.

---

### S9 — Export et partage d'une image *(livre)*

**Objectif.** Envoyer un beta a un partenaire de grimpe.

**Livrables.**
- Rendu du canevas annote en PNG (`GraphicsLayer.toImageBitmap`).
- Partage par `FileProvider` et `ACTION_SEND`.
- Aucune synchronisation, aucun compte : l'image quitte l'application par le selecteur systeme,
  et rien d'autre ne sort du telephone.

**Le point d'architecture.** Le canevas enregistre son propre rendu dans un `GraphicsLayer`, puis
s'affiche depuis lui : l'image exportee sort du meme code de dessin que l'ecran, sans second
moteur. Consequence voulue, l'image est ce que l'ecran montre — en rejeu, une sequence arretee a
mi-parcours se partage telle quelle, pour montrer le mouvement qui coince.

**Critere d'acceptation — atteint.** Sur emulateur API 36 : bouton « Partager », selecteur
systeme ouvert avec l'apercu de l'image, PNG de 1080 x 1440 ecrit dans `cacheDir/shared`. Sur le
Xiaomi, un export de 1280 x 1707 relu complet. Trois tests instrumentes relisent le PNG par le
`ContentResolver`, comme le ferait l'application receptrice, et un quatrieme verifie que le calque
d'export contient le trace jusqu'au bord inferieur de l'image.

**A surveiller.** Sur emulateur, 2 exports sur 6 ont ete tronques : contenu correct au-dessus
d'une ligne horizontale, transparent en dessous, a une hauteur differente a chaque fois. Le
defaut n'a pas ete reproduit en isolement (40 captures sur 40 completes) ni observe sur appareil
physique. Hypothese non verifiee : une relecture du tampon materiel concurrente d'un rendu en
cours, propre au GPU emule. A reverifier sur appareil si un utilisateur le signale.

---

### S10 — Detection automatique des prises *(livre)*

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

**Audit prealable — conforme (ONNX Runtime 1.30.0).** AAR precompile de Maven Central inspecte a
`llvm-objdump -p` avant tout code :

```
arm64-v8a  libonnxruntime.so        3 LOAD, tous en 2**14
arm64-v8a  libonnxruntime4j_jni.so  2 LOAD, tous en 2**14
x86_64     libonnxruntime.so        3 LOAD, tous en 2**14
x86_64     libonnxruntime4j_jni.so  2 LOAD, tous en 2**14
```

Le repli par recompilation n'est donc pas necessaire pour l'alignement. XNNPACK est compile dans
le binaire. L'AAR ne fournit pas de module Prefab, mais embarque les en-tetes C : l'import CMake
se fait par cible importee.

**Point ouvert revele par l'audit : le poids.** `libonnxruntime.so` pese **33 Mo** en arm64-v8a
(11,8 Mo compresse), soit six fois le modele vise, et nos `.so` sont stockees non compressees
dans l'APK — c'est la condition de l'alignement 16 Ko. Une construction reduite aux seuls
operateurs du modele (build minimal d'ONNX Runtime, format ORT) ramenerait ce poids a quelques
Mo, mais impose de compiler depuis les sources : elle releve de S11.

**Modele de substitution.** Faute de modele entraine, le pipeline est construit sur un YOLO nano
generique (COCO). Ultralytics est sous licence AGPL-3.0 : ce modele reste confine aux ressources
de TEST et n'entre jamais dans l'APK livre. La licence du modele definitif est un point bloquant
avant toute publication.

**Livre — la chaine complete, sur modele de substitution.**
- Session ONNX Runtime sur XNNPACK, modele charge depuis un buffer direct (asset projete en
  memoire, jamais recopie dans le heap Java) et format de sortie verifie au chargement.
- Letterbox ecrit directement dans le tenseur CHW depuis les pixels verrouilles du `Bitmap`.
- Decodage et NMS natifs, une seule traversee JNI par inference.
- `HoldDetector` fourni par Hilt : sans modele sous `assets/models/holds.onnx`, il reste non
  pret et ne retourne rien — **aucune couche superieure n'a change**.

**Preuves.** 12 tests instrumentes verts sur le noyau 16 Ko (API 37), dont les 6 historiques du
pont JNI. La reference de detection est calculee par un miroir NumPy du code natif
(`ml/export_placeholder.py`) : 5 detections sur 5 retrouvees, IoU > 0,95. Valide par mutation —
inverser les canaux RGB fait echouer le test. L'audit 16 Ko de l'APK final reste vert sur les
14 bibliotheques embarquees, `libonnxruntime.so` comprise.

**Une correction trouvee par les tests.** ONNX Runtime signale un modele corrompu par une
exception generique (`ORT_INVALID_PROTOBUF`) : sans traduction de son code d'erreur, un modele
invalide aurait ete presente comme une panne du moteur.

**Le modele de prises.** YOLO11n affine depuis COCO sur trois jeux publics (CC BY 4.0, voir
`ml/README.md`), fusionnes en une seule classe « prise » : 4 202 images d'entrainement, 154 de
test. 32 minutes sur RTX 4070 SUPER. Sur le jeu de TEST, jamais vu pendant l'entrainement ni la
selection du meilleur epoch : **mAP50 0,879**, precision 0,906, rappel 0,815.

**Critere d'acceptation — atteint.** Sur le Xiaomi (ARM64), build release : **60,7 ms** de
mediane de bout en bout — 3 ms de pretraitement, 56 ms d'inference. Detection validee a la main
sur une photo de salle reelle : 115 prises proposees, posees sur les prises affichees.

**FP32 et non INT8 — mesure, pas intuition.** La quantification INT8, prevue au plan, est
ecartee : a la fois plus LENTE (97 ms contre 61) et moins precise (mAP50 0,819 contre 0,879).
ONNX Runtime ne confie pas les noeuds de quantification a XNNPACK, et leur execution generique
coute plus qu'elle ne fait gagner. Quatre configurations ont ete essayees : quantifier tout le
graphe donnait une mAP de **zero**, la sortie concatenant coordonnees et scores sous une seule
echelle.

**Reste ouvert.**
- **Distribution du modele** : `*.onnx` est hors git, un clone du depot construit donc une
  application sans detection. A publier comme artefact de release (S13).
- **Le poids** : APK release de 84 Mo avec les deux ABI. Build reduit d'ONNX Runtime en S13.

---

### S11 — Ne garder que les prises du bloc *(livre)*

**Le constat.** La detection trouve TOUTES les prises du mur — 115 sur la photo de test — alors
qu'un bloc n'en utilise qu'une quinzaine. Retirer les autres une a une annule le gain.

**La contrainte, venue du terrain.** On ne peut pas se fier a la couleur de difficulte : Climb Up
n'ouvre plus ses blocs par couleur de prises mais par couleur de DEPART. La difficulte est portee
par des fleches (violettes, par exemple), et les prises du bloc peuvent etre d'une autre couleur
(blanches). La couleur a conserver est donc celle des prises, designee par l'utilisateur, jamais
deduite du circuit.

**Livrables.**
- **Pipette** : un appui sur une prise du bloc designe sa couleur, plutot qu'une liste de noms
  de couleurs — « blanc » et « gris clair » ne se departagent pas par un nom.
- **Option « garder les prises grises »** : les bacs et volumes gris, communs a tous les blocs.
- Couleur de chaque prise mesuree a la detection, dans le code natif, sur le centre de la boite :
  les bords debordent sur le mur. Espace Lab plutot que RVB, ou la distance suit la perception.
- Prises neutres (blanc, gris, noir) distinguees par leur luminosite, leur teinte n'ayant pas de
  sens.
- Migration v3 -> v4 : couleur persistee avec la prise.
- Modele a deux classes, prise et volume : un volume reste quelle que soit sa couleur, une
  petite prise grise de desescalade part avec le filtre. La taille ne suffit pas a les separer,
  un macro colore etant aussi gros qu'un petit volume.
- Relancer la detection COMPLETE les prises deja posees (couleur, nature) au lieu de les ignorer.
  Sans cela, les prises detectees avant ce sprint restent sans couleur, et les recuperer
  oblige a tout effacer — beta compris.

**Livre, en attente de validation sur appareil.**
- Couleur CIELAB mesuree en natif sur le centre de chaque boite, persistee en base (v4, migration
  executee sur la base reelle du Xiaomi : 76 prises conservees).
- Modele a deux classes : sur le meme jeu de test que le modele a une classe, mAP50 0,883 contre
  0,879 ; par classe, prises 0,876 et volumes 0,892.
- Pipette « Garder une couleur », et relance de la detection qui complete les prises existantes.

**Calibre sur une photo reelle, pas a l'intuition.**
- Les prises VIOLETTES n'y sont saturees qu'a 7-9, les grises a 3-4 : le seuil de neutralite
  intuitif (12) aurait pris le violet pour du gris. Il est a 6, et le test casse s'il remonte.
- Deux petites prises grises y etaient prises pour des volumes, a 1 et 1,2 fois la prise
  mediane de la photo ; les vrais volumes font 5 a 34 fois. Un volume sous 3 fois la mediane
  redevient une prise.

**Un defaut trouve sur appareil.** Le passage detection -> prise enregistree omettait nature et
couleur ; leurs valeurs par defaut ont masque l'oubli a la compilation, et le filtre repondait
« pas de couleur mesuree » partout. Corrige, avec un test sur la vraie base qui echoue sans le
correctif.

**Valide sur appareil.** Sur les blocs de la seance Villenave, le filtre ne garde que les prises
de la couleur designee — vertes sur l'un, rouges sur l'autre — plus les volumes gris ; les
petites prises grises et blanches sont retirees. La detection se trompe parfois, et c'est
acceptable : corriger au doigt les quelques prises en trop ou manquantes suffit.

**Un second defaut trouve sur appareil : le cadrage.** L'apercu remplissait l'ecran (20:9) et
rognait les cotes d'une photo 4:3 : l'utilisateur cadrait un bloc, la photo embarquait les blocs
voisins, et la detection proposait leurs prises. Contourne sur le terrain en photographiant avec
l'appareil photo systeme. L'EXIF a ecarte l'objectif : toutes les photos sont au 23 mm equivalent,
zoom 1,0. L'apercu montre desormais toute la photo, et un pincement zoome — au-dela de 2x environ,
la camera logique bascule d'elle-meme sur le teleobjectif.

**Zone de detection tracee au doigt — livree.** « Cadrer le bloc », puis un glissement d'un coin
a l'autre : hors du cadre s'assombrit, et la detection ne regarde que lui. Seule la zone est
DECODEE (BitmapRegionDecoder) : c'est elle que le modele recoit a 640 pixels, et chaque prise y
gagne en resolution — environ trois fois sur un bloc qui couvre un tiers de la photo. La zone est
tracee sur la photo redressee alors que le decodeur lit le fichier brut : la rotation EXIF inverse
est verifiee contre une rotation simulee (inverser 90 et 270 degres fait echouer le test) et sur
un vrai JPEG couche etiquete ROTATE_90. Verifie dans l'application : 437 x 476 pixels analyses,
prises proposees sur les prises de la zone seulement.

**Reste.**
- La zone vit le temps de l'edition ; la persister avec le bloc si le besoin s'en fait sentir.
- La grande prise grise en croissant de la photo de test n'est detectee par aucun des deux
  modeles.

**Latence du modele a deux classes — mesuree sur le Xiaomi, en release : 58,2 ms de mediane**
(max 61 ms), soit celle du modele a une classe : seule la derniere couche change. Une premiere
passe a 104 ms s'expliquait par l'application Topo ouverte en parallele : ses inferences
alternaient entre 55 ms et 100-137 ms, signature de threads XNNPACK disputes. Refaite
application fermee, la mesure est stable.

---

### S12 — Textes en francais correct *(livre)*

**Le constat.** Toute l'interface est ecrite sans accents (« Detecter les prises », « Annuler la
derniere prise ») : une application en francais doit s'ecrire en francais.

**Livrables.**
- Tous les textes visibles extraits dans `strings.xml`, accents compris : c'est la pratique
  Android, et le prealable a toute autre langue.
- Pluriels geres par `plurals` : « 1 prise proposee », pas « 1 prises ».
- Un test qui echoue si un texte visible est de nouveau code en dur dans un composable.

**Livre.** Environ 150 textes deplaces dans `strings.xml`, module par module, accents,
espaces insecables et vrais pluriels compris. Les libelles d'enumeration que portait le domaine
(`GradeScale`, `DifficultyDisplay`) passent dans `core/ui` : un module JVM pur ne peut ni
accentuer ni traduire. Les ViewModel ne portent plus de texte : leurs erreurs sont des etats
types que l'ecran met en phrase. La verification `checkHardcodedUiText`, branchee sur `check`,
fait echouer le build sur tout texte visible code en dur ; une exception exige une raison ecrite.

**Reste.** L'erreur « flash apres plusieurs essais » est reconnue a son type d'exception : le
domaine devrait renvoyer une erreur typee. Un message de flux introuvable a l'import peut encore
atteindre l'utilisateur sans traduction, cas tres rare.

**Pourquoi avant l'industrialisation.** Chaque ecran est concerne, le cout est faible, et
l'industrialisation produit le build de publication : il ne doit pas partir avec ces textes.
Les commentaires et la documentation du code restent hors perimetre.

---

### S13 — Industrialisation *(livre, sauf ONNX Runtime reduit)*



**Objectif.** Transformer un code fonctionnel en produit livrable.

**Livrables.**
- Profilage memoire : fuites Java, liberation des structures C++ allouees par ONNX Runtime.
- R8 : minification, elimination du code mort, obscurcissement.
- Baseline profile pour reduire le temps de demarrage.
- Restauration d'etat apres terminaison du processus (`SavedStateHandle`).
- Audit final d'alignement 16 Ko et production de l'Android App Bundle.

**Resultats, tous mesures.**
- **R8** : le build de publication tourne — les 8 methodes JNI s'enregistrent (la regle de
  conservation de `NativeBridge` tient), detection, partage, reglages et statistiques
  parcourus sans plantage sur emulateur.
- **Terminaison du processus** : constatee avant correction — l'editeur revenait du mode
  « Tracer » au mode « Prises ». Mode, membre, zone de detection et filtre de la grille vivent
  desormais dans le `SavedStateHandle` ; textes en cours de saisie et dialogues ouverts dans
  `rememberSaveable`. Verifie par `am kill` : mode, zone et texte saisi reviennent. La pipette et
  le cadrage en cours ne sont PAS restaures, a dessein.
- **Memoire native** : pas de fuite. 30 detections a la suite : 27 Mo avant, 106 Mo au
  chargement du modele, puis stable autour de 130 Mo. Desactiver l'arena memoire d'ONNX Runtime
  ne change rien (142 contre 143 Mo sur photos reelles) : reglage par defaut conserve.
- **Fuites Java** : LeakCanary en debug. Six recreations de l'activite apres une detection :
  rien de retenu. Il ne surveille pas les ViewModel des destinations Compose.
- **Baseline profile** : 17 400 regles, generees sur le parcours demarrage -> seances -> seance.
  Sur emulateur, 10 demarrages a froid par mode : mediane 1188 -> **1077 ms**, pire cas 6165 ->
  **1248 ms**. Sur le Xiaomi, meme test : mediane 257 -> **237 ms** (-8 %). Gain modeste mais
  reel sur un demarrage deja rapide ; la passe avec profil est bruitee (un minimum a 43 ms trahit
  une iteration ou MIUI n'a pas vraiment tue le processus), d'ou une lecture prudente.
- **Android App Bundle** : 67,3 Mo, 14 bibliotheques natives conformes 16 Ko. Non signe : la cle
  d'envoi Play reste a creer par l'auteur.
- **Modele** : un build de publication refuse de se construire sans lui.

**Reste.**
- **ONNX Runtime reduit aux operateurs du modele** : 33 Mo en arm64 aujourd'hui, quelques Mo
  attendus. Impose de compiler ORT depuis les sources : a decider.
- Signature de publication.

---

### S14 — Mettre en valeur les prises du bloc *(livre)*

**Le constat, venu du terrain.** La difficulte n'est portee que par les fleches de depart : une
fois au pied du mur, rien sur la photo ne dit a quel niveau appartiennent les prises gardees.

**Pistes.**
- **Surligner les prises du bloc** d'un halo de la couleur de difficulte du bloc — celle de ses
  fleches, deja saisissable comme « couleur du circuit » — plutot que du cercle blanc neutre
  d'aujourd'hui. Le rendu du canevas et l'export PNG en profitent ensemble, puisqu'ils partagent
  le meme code de dessin.
- **Porter la difficulte dans le nom du bloc**, pour la retrouver dans la grille d'une seance sans
  ouvrir le bloc.

**Livre.** Les prises du bloc portent un halo de sa couleur de circuit — celle de ses fleches —
a l'ecran comme sur l'image partagee, qui sort du meme code de dessin : disque translucide,
anneau plein, et lisere sombre qui le detache d'un mur de la meme couleur. Les volumes restent en
blanc neutre. Le titre du bloc se lit comme au mur : « Violet · 6B ». Verifie sur les photos
reelles de Villenave : halo violet lisible sur des prises rouges. Un test lit la couleur de
l'anneau dans le rendu capture.

**Suite, venue du terrain — livree et validee sur le Xiaomi.**
- **La vignette montre le bloc.** En parcourant une seance, rien ne disait de quelles prises
  etait fait chaque bloc : il fallait l'ouvrir. Le cadre trace au doigt est desormais PERSISTE
  avec le bloc (schema v5), et la vignette zoome dessus en dessinant ses prises avec le halo de
  sa difficulte — le meme dessin que dans l'editeur, partage depuis `core/ui`. Forme constante,
  celle de la photo : suivre la forme du cadre faisait qu'un cadre etroit et haut etirait toute
  la ligne de la grille, constate sur emulateur. Photo decodee a 1024 pixels au plus.
- **La difficulte d'abord.** Un bloc tout juste photographie ou importe s'ouvre sur sa
  difficulte (couleur des fleches, cotation facultative) : elle se lit sur la photo avant tout
  essai, et c'est elle qui colore le halo des prises detectees ensuite. Statut et essais restent
  dans la fiche de la seance. L'import depuis la galerie suit desormais le meme chemin.
- **A la charge du grimpeur : cadrer ses blocs.** Sans cadre, la vignette montre toutes les
  prises gardees par la detection globale. C'est assume : le cadre est un geste de l'utilisateur,
  aucune heuristique ne devine quel bloc l'interesse sur un pan.

**Pourquoi apres l'industrialisation.** C'est un confort de lecture : l'application est
complete sans. La zone de detection (S11), elle, retire du travail a chaque bloc.

---

### S15 — Vitrine : relief 3D et scan de detection *(phases 1 et 2 livrees)*

**Objectif.** Une demonstration visuelle du travail d'image et d'IA pour le portfolio, sans
alourdir l'application : utile a montrer plus qu'a l'usage quotidien.

**Phase 1 — demo 3D sur le portfolio (livree).** Une chaine hors ligne (`ml/scene3d`) exporte un
bloc en paquet de scene d'environ 265 Ko : photo recadree, relief estime par Depth Anything V2
Small, contour de chaque prise par SAM 2.1 tiny, a partir des prises gardees et corrigees dans
l'application. La visionneuse WebGL du portfolio n'execute aucun modele. Deux corrections tirees
des images de controle : le masque SAM est borne a la boite de detection (il debordait sur le mur
et la fleche voisine), et une prise posee a la main — boite arbitraire, doigt a cote de la prise —
recoit une boite de taille typique et le masque credible le plus sur, ou aucun contour.

**Phase 2 — scan de detection dans l'application (livree).** Apres une detection, une ligne
monte la zone analysee et chaque prise proposee s'allume a son passage, avec son score. Sur le
Xiaomi a 120 Hz : 242 trames, 2 saccadees (0,8 %), aucune vsync manquee, 7 ms de mediane et
2,4 ms de GPU par trame. L'emulateur affiche des trames de 200 ms passees cote GPU : sa couche de
traduction GL, pas le dessin.

**Phase 3 — 3D dans l'application (livree, validee sur le Xiaomi).** Depth Anything V2 Small en
INT8 (27 Mo, embarque, aucune dependance reseau), choisi sur mesure : ~400 ms sur le Xiaomi contre
~800 ms en FP16 et q4f16 ; XNNPACK refuse le Resize du modele, la session reste sur le CPU d'ORT.
Correlation >= 0,997 avec le FP32 sur six photos de Villenave. Relief garde en cache : seule la
premiere ouverture paie l'inference. Rendu OpenGL ES 3 (`:feature:relief`) : mur texture et
eclaire, halos a la couleur de difficulte, volumes en blanc, arcs du beta rejoues, rotation,
zoom et balancement au repos. Halos seulement, pas de contours SAM sur l'appareil.

Surface GL : une `SurfaceView` ajoutee sur un ecran immobile attendait la traversee suivante de
la fenetre (~3 s sur le Xiaomi, indefiniment sur l'emulateur API 37) ; la vue la demande
desormais a son attachement. Mesure sur le Xiaomi : premiere trame 17 a 35 ms apres la scene,
calcul frais compris (chargement du modele et inference a froid : ~1,5 s a l'oeil, 690 ms
d'inference). Reste : mesurer les trames du fil GL (`gfxinfo` ne couvre que l'interface
Compose) ; eventuellement precharger le modele a l'ouverture de l'annotation.

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
| `Topo` plutot que `BetaClimb` | Un compose anglais descriptif ne tient pas la serie ouverte par `Kalos` et `Popote` : des mots courts, chaleureux, avec un clin d'oeil d'initie. `Topo` est le mot des grimpeurs pour le guide de voies, et « c'est quoi le topo ? » decrit exactement ce qu'est un beta. |
| Icone entierement vectorielle | Kalos utilise un PNG pre-padde a 64 % du canvas. Un VectorDrawable reste net a toutes les densites, pese quelques kilo-octets au lieu d'une famille de bitmaps, et se relit : une couleur se change dans le XML, pas dans un exporteur. |
| Aucun mipmap PNG herite | `minSdk = 26` garantit les icones adaptatives sur tout le parc vise. Les familles `mipmap-hdpi` a `mipmap-xxxhdpi` que Kalos doit embarquer sont ici inutiles : l'icone entiere tient dans trois fichiers vectoriels, 18 Ko au total. |
| Couleurs et cotations comptees SEPAREMENT | Un rang de couleur (0 a 4) et un rang de cotation (0 a 23) ne mesurent pas sur la meme regle. Les melanger donnerait un « niveau moyen » sans signification. |
| Taux de flash rapporte aux reussites, pas a tous les blocs | Rapporte au total, le taux chuterait a chaque projet ouvert et penaliserait le fait d'essayer des blocs durs. |
| Export par le calque du canevas, pas par un rendu dedie | Une seule implementation du dessin : l'image partagee ne peut pas diverger de l'ecran. Le prix est une resolution d'export egale a celle de l'affichage, suffisante pour un ecran de messagerie. |
| PNG plutot que JPEG pour l'export | Le trace est fait d'aplats et d'aretes franches, exactement ce que JPEG degrade en halos. |
| Fichier partage purge a l'export SUIVANT | L'application receptrice lit le fichier a un moment inconnu : le supprimer juste apres le selecteur ferait une course. Le repertoire ne contient ainsi jamais plus d'une image. |
| Bitmaps materiels de Coil desactives sous l'API 28 | La capture d'un calque y rejoue le dessin sur un canevas logiciel, qui refuse de lire un bitmap materiel. |
| Arena memoire d'ONNX Runtime conservee | Mesure : la desactiver ne reduit pas le tas natif (142 contre 143 Mo apres 10 detections sur photos reelles). La memoire tient au modele et aux tampons de XNNPACK, pas a l'arena. |
| Etats a moitie faits non restaures apres terminaison | Retrouver la pipette ou le cadrage actifs a froid ferait qu'un appui ne fasse pas ce que l'ecran laisse croire. |
| Build de publication refuse sans modele | Le modele est hors git : sans verrou, un clone produirait en silence une application privee de detection. |
| Modele FP32 plutot qu'INT8 | Mesure sur le Xiaomi : l'INT8 est a la fois plus lent (97 ms contre 61) et moins precis (mAP50 0,819 contre 0,879), ONNX Runtime ne confiant pas ses noeuds de quantification a XNNPACK. |
| Detection qui propose sans rien remplacer | Une prise posee a la main peut porter des mouvements : la remplacer emporterait le beta. Les propositions se corrigent avec le geste deja connu. |
| Seuil de proposition a 0,5, au-dessus de celui du moteur | Mesure sur une photo reelle : sous 0,5, le corps d'un grimpeur et des taches sur le mur. Trente fausses prises a retirer rendraient la detection plus lente que la pose manuelle. |
| Correction manuelle des erreurs de detection, pas de modele plus gros | Valide sur le terrain : quelques prises en trop ou manquantes se corrigent au doigt, avec le geste deja connu. Un modele plus lourd couterait en latence et en poids d'APK pour un gain que l'utilisateur ne demande pas. |
| Apercu au format de la photo, pas plein ecran | Un apercu qui remplit l'ecran rogne la photo : on cadre un bloc et la photo embarque ses voisins. Ce qui est vu doit etre ce qui est capture. |
| Filtre par couleur de prise designee, pas par couleur de difficulte | Climb Up ouvre desormais par couleur de depart : la difficulte est portee par des fleches, les prises du bloc peuvent etre d'une autre couleur. |
| NMS toutes classes confondues | Le modele de prises n'a qu'une classe ; sur le modele de substitution, deux boites superposees de classes differentes restent une seule prise pour le grimpeur. |
| XNNPACK avec le pool d'ORT reduit a un thread | XNNPACK gere ses propres threads : deux pools concurrents se disputeraient les coeurs. C'est la configuration prescrite par la documentation d'ONNX Runtime. |
| Chemin `DirectByteBuffer` en RGBA_8888, plus en YUV | CameraX sait livrer du RGBA directement ; un plan de luminance seul ne porte aucune couleur sur laquelle inferer. |
| Modele de substitution jamais versionne | Sous AGPL-3.0. Regenere par script ; les tests qui en dependent sont signales ignores, jamais verts, quand il manque. |
| AAR precompile d'ONNX Runtime plutot qu'une recompilation | Audite a l'objdump avant adoption : conforme 16 Ko sur les deux ABI. Recompiler couterait une chaine de build C++ entiere pour une propriete deja acquise ; la question se reposera pour le poids, pas pour l'alignement. |
| Agregation en Kotlin, pas en SQL | Contrairement aux compteurs de seances, ces regles sont metier et changeront plus souvent que le schema ; en SQL elles seraient dispersees dans des requetes intestables. |
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
  L'export d'une image (S9) couvre le besoin reel de montrer une sequence a quelqu'un.
- **Reconnaissance de cotation par OCR.** Envisageable un jour, mais sans valeur tant que la
  detection des prises n'est pas fiable.
- **Segmentation fine des prises (masques polygonaux).** Les boites englobantes suffisent a la
  magnetisation. Un modele de segmentation coute nettement plus cher a l'inference pour un gain
  visuel, pas fonctionnel.
