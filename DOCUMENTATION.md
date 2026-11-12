# ILS – IBM License Service Reporter / ServiceNow Integrator

## Vue d'ensemble

**ILS** (IBM License Service) est une application Java (Maven, Java 25) exécutée comme un **job cron** sur OpenShift/Kubernetes.  
Son rôle est de surveiller les **workloads Kubernetes** rapportés par l'**IBM License Service Reporter** afin de détecter les pods IBM dont les **annotations de licence** sont absentes ou incorrectes, et d'automatiser la création et le suivi des **tickets RITM ServiceNow** correspondants.

---

## Architecture générale

```
┌─────────────────────────────────────────────────────────┐
│                   IBM License Service Reporter           │
│   REST API : /workloads  ,  /custom_columns             │
└────────────────────────┬────────────────────────────────┘
                         │ HTTP (téléchargement JSON)
                         ▼
┌─────────────────────────────────────────────────────────┐
│                 CronWLs.java  (job principal)            │
│  • Lit workloads ligne par ligne (NDJSON)                │
│  • Détermine l'action à mener (6 étapes)                │
│  • Appelle CMDB (Starburst/Trino) pour enrichissement    │
│  • Crée / met à jour les RITM ServiceNow                │
│  • Écrit en retour les custom columns dans le Reporter  │
└─────┬────────────────────────────┬───────────────────────┘
      │ JDBC (Trino)               │ HTTPS (Apigee → ServiceNow)
      ▼                            ▼
┌────────────┐             ┌─────────────────┐
│  Starburst │             │   ServiceNow    │
│  (CMDB)    │             │   (RITM / Tasks)│
└────────────┘             └─────────────────┘
```

---

## Structure du projet

```
src/main/java/com/bnpp/
├── CronWLs.java              # Point d'entrée principal (main)
├── Util.java                 # Utilitaires HTTP, archivage, persistance
└── data/
    ├── Config.java           # Chargement de config.properties (constantes statiques)
    ├── CMDB/
    │   └── CMDB.java         # Requêtes Starburst/Trino + cache local
    ├── ITSM/
    │   ├── RITM.java         # Création/consultation/mise à jour RITM ServiceNow
    │   └── Task.java         # Représentation d'une tâche RITM (TPROJ / TSAM)
    ├── CustomColumn/
    │   ├── CustomColumn.java
    │   ├── CustomColumnID.java
    │   ├── CustomColumnIDs.java
    │   └── CustomColumns.java
    └── Workload/
        ├── Workload.java
        └── Container/
            ├── Container.java
            ├── Containers.java
            └── Component.java
```

---

## Composants clés

### `CronWLs` – Orchestrateur principal

Point d'entrée du job. À chaque exécution :

1. Télécharge (ou lit depuis le bouchon) :
   - `/workloads` → fichier NDJSON (une ligne JSON par workload)
   - `/custom_columns` → liste des colonnes personnalisées définies dans le Reporter
2. Parcourt chaque workload et applique la logique métier (voir ci-dessous).
3. Archive les fichiers de données + log dans `workloads/` avec purge des archives de plus de N jours.

### `Workload` – Représentation d'un workload Kubernetes

Chaque ligne JSON du fichier workloads représente un déploiement (Deployment, StatefulSet, etc.) avec :
- `id`, `kind`, `name`, `namespace`, `clusterId`, `clusterName`
- `containers` : liste de containers, chacun avec un flag `charged` (true/false/null)
- `annotations` : chaîne de texte des annotations IBM License Service
- `customColumns` : colonnes personnalisées du Reporter (utilisées comme état métier)

### `CMDB` – Enrichissement depuis Starburst/Trino

Requête la table `ba_kubernetes_namespaces` via JDBC Trino pour obtenir, à partir du namespace :
- `ba_id` : identifiant de l'application
- `auid` : code application (APP CODE)
- `owner_ou_business_unit_name` : unité métier (utilisée comme label RITM)
- `assignment_group_name` : groupe d'assignation ServiceNow
- `vs_environment` : identifiant d'environnement ServiceNow (Production / Pré-production / Autre)

Un **cache local** (fichier NDJSON `starburst/cacheStarburst.json`) évite les requêtes répétées pour un même namespace.

### `RITM` – Ticket ServiceNow

Représente un ticket RITM (Request Item) dans ServiceNow. Gère :
- La **création** d'un nouveau RITM via l'API Apigee → ServiceNow
- La **lecture** d'un RITM existant (statut, tâches TPROJ et TSAM)
- La **mise à jour** du statut des tâches (`Open`, `On Hold`, `Closed`)

L'authentification se fait via OAuth2 (token Apigee).

### `Util` – Utilitaires

- `download()` : télécharge via HTTPS avec authentification Bearer (token Reporter)
- `persist_data_customColumns()` : PUT vers `/workloads/{id}/custom_columns/{colId}` pour écrire une valeur
- `clear_data_customColumns()` : DELETE vers `/workloads/{id}/custom_columns/{colId}` pour effacer une valeur
- `archive()` : zippe les fichiers de la session dans le répertoire d'archives
- `getToken()` : lit un secret depuis le système de fichiers (montage Kubernetes secret)

### `Config` – Configuration

Toutes les constantes sont chargées au démarrage depuis `config.properties` (embarqué dans le JAR, surchargeable via montage ConfigMap OpenShift). Principales entrées :

| Clé | Rôle |
|-----|------|
| `REPORTER_API_URI` | URL de base de l'IBM License Service Reporter |
| `URL_APIGEE_AUTH` | Endpoint OAuth2 Apigee |
| `URL_APIGEE_SN` | URL API ServiceNow via Apigee |
| `TRINO_URL` | JDBC Starburst/Trino (CMDB) |
| `STARBURST_CACHE_FILE` | Fichier de cache local Starburst |
| `COLUMN_*` | Noms des custom columns dans le Reporter |
| `BOUCHON` | Mode bouchon (utilise des fichiers locaux au lieu des APIs) |
| `ARCHIVE_RETENTION_DAYS` | Rétention des archives en jours |

---

## Logique métier – Les 6 étapes

Pour chaque workload, l'algorithme décide de l'action à mener selon l'état courant des custom columns et de la présence d'annotations :

```
Workload
│
├── remediationNeeded = "yes" ?
│   ├── ritmID == null ?  ──────────────────────────────────► ÉTAPE 2
│   │     Crée un RITM, enrichit les custom columns,
│   │     efface remediationNeeded, SAM Status = "Open"
│   │
│   └── ritmID != null ?
│       ├── statusTProj = "On Hold" AND samStatus = "Not Resolved" ► ÉTAPE 5
│       │     Remet TPROJ et TSAM à "Open", SAM Status = "Open"
│       ├── statusTProj = "On Hold" AND samStatus = "Resolved" ──► ÉTAPE 4
│       │     Ferme TPROJ et TSAM, SAM Status = "Open"
│       └── statusTProj = "On Hold" AND samStatus = "Open" ──────► Vérifie annotations
│             annotations=null → SAM Status = "Not Resolved"
│             annotations présentes → SAM Status = "Resolved"
│
├── ritmID == null AND annotations == null AND charged != "false" ?
│   └──────────────────────────────────────────────────────────── ÉTAPE 1
│         Enrichit APP CODE, Assignment Group, Charged, RecId
│         SAM Status = "Open"
│
└── Sinon (pas de remédiation nécessaire)
    ├── Enrichit Charged, APP CODE, Assignment Group, RecId
    ├── ritmID != null ?
    │   ├── TPROJ et TSAM = "Closed" dans la console ? ────────► ÉTAPE 6
    │   │     Efface RITM, tâches, SAM Status (fin du cycle)
    │   ├── statTProjSN = "On Hold" (nouvelle transition) ? ───► ÉTAPE 3
    │   │     Vérifie annotations, met à jour SAM Status,
    │   │     synchronise numéros et statuts des tâches
    │   └── Sinon : synchronise simplement les statuts des tâches
    └── ritmID == null → efface SAM Status si présent
```

---

## Déploiement

### Docker / OpenShift

Le `Dockerfile` utilise un build multi-stage :
1. **Stage builder** : `maven:3.9.11-ibm-semeru-25-noble` – compile le projet (`mvn clean package`)
2. **Stage runtime** : `ibm-semeru-runtimes:open-25-jre-noble` – exécute le JAR

Les secrets (tokens, mots de passe) sont montés comme fichiers dans le conteneur et lus par `Util.getToken()`.  
Le truststore PKCS12 (`truststore.pkcs12`) est monté pour valider les certificats des APIs HTTPS.

### Exécution locale (développement)

```bash
mvn clean package
java -cp target/servicenowILS.jar:target/lib/* \
     -Djavax.net.ssl.trustStore=truststore.pkcs12 \
     -Djavax.net.ssl.trustStoreType=PKCS12 \
     -Djavax.net.ssl.trustStorePassword=password \
     com.bnpp.CronWLs
```

### Mode bouchon

Mettre `BOUCHON=true` dans `config.properties` (ou via variable d'environnement) pour utiliser des fichiers JSON locaux au lieu d'appeler les APIs. Utile pour les tests.

---

## Dépendances principales

| Dépendance | Version | Rôle |
|-----------|---------|------|
| `com.google.code.gson` | 2.13.2 | Parsing/sérialisation JSON |
| `io.trino:trino-jdbc` | 471 | Connexion JDBC Starburst/Trino (CMDB) |
| Java 25 (IBM Semeru) | 25 | Runtime |

---

## Flux de données résumé

```
Reporter API ──► workloads.json (NDJSON)
Reporter API ──► customColumns.json
Starburst    ──► (cache NDJSON) ──► enrichissement CMDB
ServiceNow   ◄──► création / MAJ RITM et tâches
Reporter API ◄── PUT / DELETE custom columns (retour état métier)
workloads/   ◄── archive ZIP horodatée (rétention 7 jours par défaut)
```
