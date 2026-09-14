# Conventions du projet BetaClimb

## Commits

Format **Conventional Commits**, **en anglais**, sans exception. Le choix de la langue est
fige : on ne melange pas francais et anglais dans l'historique.

```
<type>(<scope optionnel>): <description a l'imperatif, minuscule, sans point final>

[corps optionnel : le POURQUOI, pas le QUOI — le diff dit deja le quoi]
```

**Types autorises** : `feat`, `fix`, `refactor`, `perf`, `test`, `docs`, `build`, `ci`, `chore`.

**Scopes** : le module concerne, sans le prefixe `:` — `vision`, `data`, `domain`, `camera`,
`annotator`, `session`, `ui`, `app`, `build-logic`.

Exemples :

```
chore: init project and S0 native baseline
feat(data): add Room schema with WAL journaling
fix(vision): reject non-direct ByteBuffer instead of dereferencing null
build(build-logic): share supported ABIs between app and vision
```

**Regles strictes :**

- Un commit = un changement coherent. Pas de commit fourre-tout.
- **Aucune ligne de co-auteur, d'attribution ou de signature d'outil.** Pas de
  `Co-Authored-By`, pas de `Generated with`, pas d'emoji de robot. L'historique ne mentionne
  aucun assistant, ni dans les commits, ni dans les descriptions de pull request.
- Pas de `--no-verify`, pas de contournement de hook.

## Code

- Commentaires et documentation : **en francais**.
- Identifiants, noms de types, de fonctions et de fichiers : **en anglais**.
- Un commentaire explique un choix ou un piege, jamais ce que le code dit deja.
- Les versions de dependances vivent **uniquement** dans `gradle/libs.versions.toml`.
- Les regles de build partagees vivent dans `build-logic/`, jamais dupliquees dans les modules.
