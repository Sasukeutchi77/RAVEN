# Architecture des Extensions & Plugins RAVEN

## 1. Objectif et Principes de Sécurité

Le système d'extensions de **RAVEN** permet d'ajouter des fonctionnalités modulaires (outils de productivité, radars de mots-clés, hygiène de liens, commandes slash `/...`, automatisations réactives) sans modifier en permanence le cœur de l'application (`ChatRepository`, UI principale, couche WebRTC).

L'architecture repose sur quatre piliers de sécurité stricts :

1. **Zéro exécution arbitraire de code distant (Anti-DCL / Conformité Google Play)** :
   - Aucun chargement dynamique de `.dex`, `.jar`, `.so`, `.apk`, `DexClassLoader`, ni évaluation de scripts distants (`javascript:`, `eval(...)`).
   - Toutes les extensions exécutables sont des modules Kotlin compilés et vérifiés dans l'application, enregistrés dans `RavenPluginRegistry`.
   - `PluginSecurityGuard` inspecte et rejette tout manifeste, déclencheur ou configuration contenant des motifs d'injection ou de DCL.

2. **Moindre privilège absolu (Least Privilege)** :
   - Un plugin **n'obtient jamais** l'accès automatique à l'ensemble des données utilisateur (`User.email`, `User.phone`, UID privé Firebase, tokens, `ChatRepository`, `FirebaseFirestore`).
   - Les données accessibles passent uniquement par des DTOs expurgés (`PluginPublicUserSummary`, `PluginServerSummary`, `PluginMessagePreview`).
   - Lors de l'activation, l'utilisateur approuve explicitement chaque permission demandée dans `PluginConsentActivationDialog` (les permissions `SENSITIVE` telles que `READ_MESSAGES_SUMMARY` et `POST_CHANNEL_MESSAGE` sont **décochées par défaut**).

3. **API interne contrôlée (`ScopedPluginApi`) & Limitation de débit** :
   - Chaque appel passe par un proxy `ScopedPluginApi` propre au plugin.
   - Si le plugin appelle une méthode sans disposer de la permission accordée *et* déclarée dans son manifeste, une `PluginPermissionDeniedException` (`SecurityException`) est levée et un log `PluginLogLevel.SECURITY` est enregistré.
   - Les actions d'écriture/notification sont protégées par une fenêtre glissante de limitation de débit (`maxActionsPerMinute`).
   - Les messages sortants sont nettoyés (masquage des jetons, emails, secrets potentiels).

4. **Isolation des pannes & Quarantaine automatique** :
   - Tous les hooks (`onLoad`, `onEnable`, `onConfigUpdated`, `onEvent`, `onCommand`) s'exécutent dans `runGuarded(...)`.
   - Une exception levée par un plugin n'impacte ni RAVEN ni les autres extensions.
   - Après **3 erreurs consécutives** (`MAX_CONSECUTIVE_ERRORS = 3`), le plugin bascule en état `FAULTED`, `onDisable()` est appelé et une alerte utilisateur est émise jusqu'à réinitialisation manuelle.

---

## 2. Composants de l'Architecture

### 2.1 `PluginManifest` & Identifiant (`PluginModels.kt`)
Chaque plugin déclare un `PluginManifest` immuable :
- `id` : identifiant reverse-domain strict (`^[a-z][a-z0-9_]*(\.[a-z0-9_]+)+$`, ex. `com.raven.ext.pomodoro`).
- `version` : version sémantique `MAJOR.MINOR.PATCH` (`PluginSemVer`).
- `minRavenVersion` / `maxRavenVersion` : bornes de compatibilité comparées à `RAVEN_HOST_VERSION` (`"1.0.0"`).
- `requestedPermissions` : ensemble borné de `PluginPermission` (`LOW`, `MEDIUM`, `SENSITIVE`).
- `subscribedEvents` : événements typés écoutés (`PluginEventType`).
- `slashCommands` : commandes `/...` déclarées (`PluginSlashCommandSpec`).
- `configSchema` : schéma typé de configuration utilisateur (`BOOLEAN`, `INT`, `STRING`, `SELECT`).

### 2.2 Compatibilité & Validation (`PluginSecurityAndCompatibility.kt`)
- `PluginCompatibilityChecker.checkCompatibility(manifest, hostVersion)` :
  - Valide l'identifiant, les versions SemVer et vérifie `minRavenVersion <= hostVersion <= maxRavenVersion`.
  - Si incompatible, le plugin reste en état `PluginLifecycleState.INCOMPATIBLE` et ne peut être activé.
- `PluginSecurityGuard` :
  - Valide les déclencheurs `/command`, les valeurs de configuration (`minInt`/`maxInt`, `options`, `maxLength`), et bloque toute tentative DCL/script.
  - Redacte les emails, clés/tokens (`sk_...`, `AIza...`, `Bearer ...`) dans les sorties.
- `PluginPermissionMatrix` :
  - Calcule l'intersection entre `requestedPermissions` et `grantedPermissions` (un plugin ne peut jamais obtenir une permission qu'il n'a pas déclarée).

### 2.3 Cycle de vie (`PluginLifecycleState` & `RavenPlugin`)
États du cycle de vie :
- `REGISTERED` -> enregistré dans le registre
- `INCOMPATIBLE` -> version hôte insuffisante ou manifeste non conforme
- `DISABLED` -> inactif (état initial sûr)
- `ACTIVE` -> activé par l'utilisateur avec permissions explicites
- `FAULTED` -> mis en quarantaine après 3 erreurs consécutives

Hooks `RavenPlugin` :
- `onLoad(context: PluginContext)`
- `onEnable(api: RavenPluginApi)`
- `onConfigUpdated(newConfig: Map<String, String>, api: RavenPluginApi)`
- `onEvent(event: RavenPluginEvent, api: RavenPluginApi)`
- `onCommand(trigger: String, args: String, targetChannelId: String, api: RavenPluginApi): Boolean`
- `onDisable()`
- `onUnload()`

### 2.4 Bus d'événements (`RavenPluginEvent`)
Événements typés acheminés depuis `ChatRepository` uniquement vers les plugins `ACTIVE` ayant souscrit au type d'événement (et ayant `READ_MESSAGES_SUMMARY` pour `MessagePosted`) :
- `MessagePosted` (aperçu expurgé, sans UID)
- `ChannelChanged`
- `ServerSelected`
- `VoiceRoomJoined`
- `VoiceRoomLeft`
- `EventScheduled`

### 2.5 Persistance & Règles Firestore (`PluginFirestoreSync.kt`, `firestore.rules`)
- Stockage strictement isolé par utilisateur :
  - `/users/{userId}/plugins/{pluginId}` : état d'activation, permissions accordées/refusées, configuration, compteur d'erreurs.
  - `/users/{userId}/plugin_logs/{logId}` : journaux d'exécution (`INFO`, `WARN`, `ERROR`, `SECURITY`), immuables (`allow update: if false`).
- Les règles Firestore rejettent tout accès inter-utilisateurs, les identifiants invalides, les états incohérents (`isEnabled == true` avec `FAULTED`/`INCOMPATIBLE`), les permissions inconnues et les motifs `.dex`/`.jar`/`.so`/`javascript:`.

---

## 3. Extensions Intégrées de Référence (`BuiltInRavenPlugins`)

| ID | Nom | Rôle | Permissions demandées |
|---|---|---|---|
| `com.raven.ext.pomodoro` | Pomodoro Focus | Démarre un cycle focus via `/focus [min]` | `EXECUTE_SLASH_COMMAND`, `POST_CHANNEL_MESSAGE`, `EMIT_LOCAL_NOTIFICATION`, `READ_VOICE_STATE` |
| `com.raven.ext.keyword_radar` | Radar Mots-Clés | Alerte sur des mots-clés configurés | `READ_MESSAGES_SUMMARY`, `EMIT_LOCAL_NOTIFICATION` |
| `com.raven.ext.standup` | Assistant Standup | Génère un modèle d'avancement via `/standup` | `EXECUTE_SLASH_COMMAND`, `POST_CHANNEL_MESSAGE`, `READ_ACTIVE_SERVER` |
| `com.raven.ext.link_hygiene` | Hygiène des Liens | Signale les paramètres de suivi ou liens risqués | `READ_MESSAGES_SUMMARY`, `POST_CHANNEL_MESSAGE`, `EMIT_LOCAL_NOTIFICATION` |
| `com.raven.ext.spatial_audio_pro` | Spatial Audio Pro *(démo incompatibilité)* | Requiert `minRavenVersion = 2.5.0` (bloqué sur hôte `1.0.0`) | `READ_ACTIVE_SERVER`, `READ_VOICE_STATE` |

---

## 4. Ajouter une Nouvelle Extension Vérifiée

1. Créer une classe implémentant `RavenPlugin` dans le module applicatif.
2. Définir un `PluginManifest` avec `id` reverse-domain, `version` SemVer, `minRavenVersion`, `requestedPermissions` minimales, `subscribedEvents`, `slashCommands` et `configSchema`.
3. Ne jamais importer `ChatRepository`, `FirebaseFirestore` ou `FirebaseAuth` : utiliser exclusivement les méthodes fournies par `RavenPluginApi`.
4. Enregistrer l'instance dans `BuiltInRavenPlugins.all()` (ou un `RavenPluginRegistry` injecté).
5. Ajouter les tests unitaires vérifiant permissions, configuration, cycle de vie et compatibilité.
