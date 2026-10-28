# Somna 🌙

Appli Android native (Kotlin, Jetpack Compose, **Material 3 Expressive**) qui calcule les meilleures heures de coucher et de réveil selon les **cycles de sommeil**, avec un **réveil progressif**. 100 % locale : aucun compte, aucun serveur, aucune donnée ne quitte le téléphone (la seule requête réseau sert à vérifier les mises à jour sur GitHub).

Package : `com.karelisio.somna` · minSdk 26 · targetSdk 37

## Fonctionnalités

| | |
|---|---|
| **Calculateur** | « Je veux me lever à… » → heures de coucher ; « Je me couche maintenant » → heures de réveil. Cycles de 90 min (80–110), endormissement de 14 min (réglable), 4 à 6 cycles avec 5–6 mis en avant. Un tap crée le réveil. |
| **Réveil fiable** | `AlarmManager.setAlarmClock`, notification plein écran (`USE_FULL_SCREEN_INTENT`), `USAGE_ALARM` (audio + `VibrationAttributes`) pour sonner en silencieux, ré-armement après redémarrage **même avant le déverrouillage** (Direct Boot), changement d'heure, mise à jour. |
| **Réveil progressif** | Écran « lever de soleil » de 0 à 100 % de luminosité (10 min par défaut), sons doux synthétisés en direct (oiseaux, carillon, vagues) en crescendo, vibration progressive. |
| **Réveil intelligent** | Fenêtre de 20 à 30 min : service au premier plan + accéléromètre (téléphone posé sur le matelas), sonne dès qu'un sommeil léger est détecté. |
| **Snooze & missions** | Snooze réglable, mission anti-snooze (2 calculs ou puzzle 1→9). |
| **Routine** | Rappel « va te coucher » à l'heure idéale, sons d'endormissement (pluie, bruit blanc, feu, vagues) avec minuterie et fondu, journal (coucher, lever, note 😴→😄) et statistiques 7/30 jours (durée moyenne, régularité). Room + DataStore. |
| **Widget** | Glance, couleurs dynamiques : « Si tu te couches maintenant → réveil à 6h50 · 8h20 » (toucher une heure règle le réveil). |
| **Mises à jour** | Vérification de `releases/latest` au lancement et via WorkManager (6 h max), bottom sheet avec changelog, téléchargement avec barre ondulée, installation via `PackageInstaller`, « ignorer cette version ». |
| **Onboarding** | Pager animé, une autorisation par page, retour automatique depuis les réglages système, coche verte animée, rappel discret des autorisations passées. |

## Build local

Prérequis : JDK 17+ (21 recommandé), Android SDK avec `platforms;android-37.0` et `build-tools;37.0.0`.

```bash
./gradlew testDebugUnitTest                # tests unitaires + Robolectric (flux de réveil, démarrage)
./gradlew assembleDebug                    # APK de debug (id com.karelisio.somna.debug)
./gradlew testDebugUnitTest -Pscreenshots  # captures de tous les écrans (clair/sombre) dans build/screenshots
```

## Signature et CI

Le workflow `.github/workflows/android.yml` :

- à chaque push sur `main` et chaque PR : tests + APK release en **artefact** ;
- **release automatique** : quand un push sur `main` contient dans le fichier `VERSION` une version qui n'a pas encore de tag, la CI crée le tag `vX.Y.Z` et la **GitHub Release** avec l'APK **signé** et le changelog (section `## X.Y.Z` de `CHANGELOG.md`, sinon les commits depuis le tag précédent). Pousser un tag `vX.Y.Z` à la main marche aussi ;
- `versionCode` = `github.run_number`, `versionName` = `VERSION` (`X.Y.Z-dev.N` hors release).

Aucun secret n'est commité. Crée le keystore **une seule fois** et garde-le précieusement : toutes les versions doivent être signées avec la même clé, sinon Android refuse la mise à jour.

```bash
keytool -genkeypair -v -keystore somna-release.jks -alias somna \
  -keyalg RSA -keysize 4096 -validity 36500
base64 -w 0 somna-release.jks > somna-release.jks.b64   # macOS : base64 -i somna-release.jks
```

Secrets à créer dans *Settings › Secrets and variables › Actions* :

| Secret | Valeur |
|---|---|
| `KEYSTORE_BASE64` | contenu de `somna-release.jks.b64` |
| `KEYSTORE_PASSWORD` | mot de passe du keystore |
| `KEY_ALIAS` | `somna` |
| `KEY_PASSWORD` | mot de passe de la clé |

Publier une version : mettre `X.Y.Z` dans `VERSION`, ajouter une section `## X.Y.Z` à `CHANGELOG.md`, pousser sur `main`.

> La mise à jour intégrée interroge `https://api.github.com/repos/karelisio/somna/releases/latest` sans authentification : les releases doivent être accessibles publiquement (dépôt public, ou dépôt public dédié aux releases — à changer dans `UPDATE_REPO` de `app/build.gradle.kts`).

## Honor / MagicOS et autres surcouches

Réglages › Fiabilité › **Optimisation de la batterie** guide pas à pas : exclusion de l'optimisation Android, « Lancement des applications » en gestion manuelle (lancement auto, secondaire, arrière-plan) et verrouillage dans les applis récentes. « Tester le réveil » permet de vérifier écran éteint.

## Architecture

```
app/src/main/java/com/karelisio/somna/
├── core/         calcul des cycles, formats d'heure
├── data/         DataStore (réglages), Room (réveils, journal), statistiques
├── alarm/        planification, receivers, service au premier plan, lecteur crescendo,
│                 vibration progressive, réveil intelligent, écran de réveil
├── audio/        synthèse procédurale des sons (aucun fichier audio embarqué)
├── routine/      rappel du coucher, sons d'endormissement
├── update/       GitHub Releases, WorkManager, PackageInstaller
├── permissions/  autorisations, retour automatique, surcouches fabricant
├── widget/       widget Glance
└── ui/           thème Material 3 Expressive, navigation (transitions partagées,
                  retour prédictif), écrans
```

Polices : [Outfit](https://fonts.google.com/specimen/Outfit) et [Figtree](https://fonts.google.com/specimen/Figtree), licence SIL Open Font License 1.1.
