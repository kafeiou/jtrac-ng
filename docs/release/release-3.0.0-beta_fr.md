# Notes de Version de JTrac NG (Release Notes) - 3.0.0-beta

[English](release-3.0.0-beta_en.md) | [繁體中文](release-3.0.0-beta_zh-TW.md) | [简体中文](release-3.0.0-beta_zh-CN.md) | [日本語](release-3.0.0-beta_ja.md) | [Tiếng Việt](release-3.0.0-beta_vi.md) | [Deutsch](release-3.0.0-beta_de.md) | [Español](release-3.0.0-beta_es.md) | [Français](release-3.0.0-beta_fr.md)

---

[![Java](https://img.shields.io/badge/Java-11%20%7C%2017-orange.svg)](https://adoptium.net/)
[![Maven](https://img.shields.io/badge/Maven-3.9+-blue.svg)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](../../license.txt)
[![Version](https://img.shields.io/badge/Version-3.0.0--beta-orange.svg)](../../pom.xml)
[![Status](https://img.shields.io/badge/Status-Beta%20Preview-yellow.svg)](release-3.0.0-beta_fr.md)
[![Wicket](https://img.shields.io/badge/Wicket-9.16.0-blue.svg)](https://wicket.apache.org/)
[![Spring](https://img.shields.io/badge/Spring-5.3.37-brightgreen.svg)](https://spring.io/)

> [!NOTE]
> **État actuel : Version préliminaire de test Beta (Pre-release / Beta Preview)**  
> Ce document constitue un journal des modifications dynamique (Living Release Notes). Tout au long de la période de test Beta, chaque ajout de fonctionnalité, ajustement et correctif sera consigné ici de manière continue.

---

## Sommaire
1. [Vue d'ensemble des points clés](#1-vue-densemble-des-points-clés)
2. [🤖 Assistant de requête par e-mail IA (AI Query Copilot avec Ollama)](#2--assistant-de-requête-par-e-mail-ia-ai-query-copilot-avec-ollama)
3. [📦 Mise à niveau des dépendances et suppression des alertes Java 11](#3--mise-à-niveau-des-dépendances-et-suppression-des-alertes-java-11)
4. [🎨 Modernisation de l'interface, Accessibilité et Thèmes](#4--modernisation-de-linterface-accessibilité-et-thèmes)
5. [🛡️ Renforcement de la sécurité en production et garde-fous](#5--renforcement-de-la-sécurité-en-production-et-garde-fous)
6. [⚙️ Paramètres système et améliorations de stabilité](#6--paramètres-système-et-améliorations-de-stabilité)
7. [Guide de mise à niveau et de compatibilité](#7-guide-de-mise-à-niveau-et-de-compatibilité)

---

## 1. Vue d'ensemble des points clés

Dans la continuité de la modernisation architecturale 2.0.0, JTrac NG 3.0.0-beta introduit le nouvel **Assistant de requête par e-mail IA (AI Query Copilot via Ollama)**, met à niveau le moteur XML pour éradiquer les avertissements de réflexion illégale sous Java 11, optimise l'accessibilité de l'interface (zoom du texte à 4 niveaux avec mode A+++ et sélecteur de 3 thèmes) et consolide la sécurité en production.

---

## 2. 🤖 Assistant de requête par e-mail IA (AI Query Copilot avec Ollama)

1. **Expansion de requête en 2 phases & Protection anti-injection** :
   - Analyse l'objet et le corps des messages via Ollama LLM pour extraire les termes techniques bilingues et synonymes.
   - Isole les entrées utilisateur dans la balise sécurisée `<untrusted_user_query>` pour contrer les attaques de type Prompt Injection.
2. **Recherche pondérée hybride & Bonus de correspondance bilingue** :
   - Évalue la pertinence selon Résumé (+3), Détail (+1), Commentaires (+1) et Pièces jointes (+1), avec un bonus de +5 points pour les correspondances bilingues.
   - Paramètre configurable `llm.retrieval.max_tickets` dans la table `config` (50 par défaut).
3. **Pipeline distribué Map-Reduce** :
   - **Phase Map** : Analyse individuelle de chaque ticket et pièce jointe (jusqu'à 100 000 caractères par fichier ; PDF, Word, Excel, TXT, LOG, CSV) dans des synthèses temporaires.
   - **Phase Reduce** : Consolidation finale en 3 volets clairs (Synthèse de direction, Causes racines & solutions, Actions recommandées).
   - Nettoyage rigoureux garanti via des blocs `finally` sans encombrement disque.
4. **Liens de rapport Web valides 14 jours & Téléchargement HTML hors ligne** :
   - **Élimination totale des blocages par les passerelles e-mail** : Suppression des pièces jointes `.html` souvent bloquées par les passerelles de messagerie d'entreprise (Exchange/Outlook/Gmail), remplacées par des liens Web sécurisés.
   - **Cycle de vie de 14 jours et purge horaire automatisée (TTL Auto-Pruning)** : Le serveur conserve les rapports pendant 14 jours et détruit automatiquement les fichiers expirés via une tâche horaire sans frais de maintenance.
   - **100% utilisable hors ligne et téléchargement en un clic** : Barre d'action permanente permettant de télécharger le rapport HTML (`JTrac-AI-Report-[Date].html`), avec moteur Mermaid.js autonome pour une utilisation sans connexion.
5. **Guide de Prompts en 8 langues** : Consulter [`docs/llm/PROMPT_EXAMPLES_*.md`](../llm/PROMPT_EXAMPLES_fr.md).
6. **Regroupement par Space, tri ID décroissant et intégration de Mermaid.js 100% hors ligne** :
   - **Sous-tableaux regroupés par Space & Tri du plus récent au plus ancien (ID DESC)** : Refonte des tableaux de synthèse par e-mail et des rapports HTML pour regrouper les tickets par espace de projet (Space) avec décompte ; au sein de chaque Space, les tickets sont triés par ID décroissant (`ID DESC`). Le corps de l'e-mail demeure épuré sans avertissement superflu sur Mermaid.
   - **Moteur Mermaid.js autonome 100% hors ligne** : Intégration du bundle complet de Mermaid.js (v10.9.1) dans le Classpath et injection directe dans le rapport HTML, éliminant toute dépendance aux CDN externes. Comprend l'adaptation automatique aux thèmes sombre/clair et un mécanisme de repli tolérant aux erreurs de syntaxe.
   - **Directives de diagrammes Mermaid à 2 niveaux avec protection par guillemets** : Production obligatoire de diagrammes `flowchart TD/LR` lors de la phase Map (flux de dépannage) et de la phase Reduce (synthèse et recommandations), avec mise entre guillemets doubles stricte des libellés de nœuds.
7. **Ancrage strict aux données JTrac, Marquage obligatoire des connaissances externes, Notification de zéro résultat et Masquage des secrets** :
   - **Ancrage strict au contexte fermé de JTrac (Strict JTrac Context Grounding)** : Obligation absolue pour le LLM de fonder ses réponses uniquement sur les tickets et pièces jointes autorisés de JTrac. L'analyse individuelle des tickets (phase Map) et les constats majeurs (Reduce Section 2) interdisent à 100% toute spéculation externe.
   - **Marquage obligatoire des connaissances externes dans la synthèse (Mandatory External Knowledge Tagging)** : Ce n'est que dans la synthèse générale (Reduce Section 1 Résumé exécutif) et la Section 3 Actions recommandées que le modèle peut compléter avec de bonnes pratiques si les tickets internes manquent de solutions, mais il **DOIT obligatoirement apposer la mention `(Note: Recommended based on external reference knowledge)`** (`（參考外部資訊給予建議）`), garantissant une traçabilité totale des conseils.
   - **Notification sécurisée de zéro résultat (Zero-Hit Safe Notice)** : Si la recherche d'un utilisateur ne correspond à aucun ticket ou pièce jointe dans ses Spaces autorisés, le système intercepte immédiatement la requête et envoie un e-mail d'information de zéro résultat détaillant les Spaces autorisés et les règles d'ancrage, évitant toute hallucination du modèle et purgeant le message entrant.
   - **Masquage des informations d'identification confidentielles (Confidential Credentials Masking)** : Intégration de `SensitiveDataMasker` pour expurger automatiquement les mots de passe, tokens Bearer, clés d'API, blocs de clés privées et identifiants dans les URLs dans les rapports HTML en les remplaçant par `***`, tout en préservant fidèlement les identifiants d'utilisateurs et les numéros de tickets.
   - **Matériel recommandé et contexte étendu 200K (Recommended Hardware & 200K Context)** : Définition des spécifications matérielles et logicielles recommandant le GPU phare NVIDIA RTX 5090 (32 Go VRAM) et `qwen2.5:32b`, avec configuration obligatoire d'une fenêtre de contexte de 200K (`num_ctx 200000`) via le Modelfile d'Ollama ; mise en garde formelle contre les modèles sous-dimensionnés afin d'éliminer toute troncature et défaillance de diagnostic.

---

## 3. 📦 Mise à niveau des dépendances et suppression des alertes Java 11

1. **Mise à niveau de `dom4j` vers `2.1.4`** :
   - Remplacement de la bibliothèque historique `dom4j:1.6.1` par `org.dom4j:dom4j:2.1.4`.
   - Éradication totale de l'alerte `WARNING: An illegal reflective access operation has occurred` sous Tomcat 9 et Java 11.

---

## 4. 🎨 Modernisation de l'interface, Accessibilité et Thèmes

1. **Cycle d'agrandissement de police à 4 niveaux** :
   - Supporte 100% (Standard), 115% (Confort), 130% (Clair) et **Mode Géant A+++ (145%)** avec protection anti-scintillement (Anti-FOUC).
2. **Sélecteur de thème à 3 états** :
   - Bascule immédiate entre Auto (Système), Clair et Sombre en un seul clic.
3. **Barre de recherche unifiée et navigation intuitive** :
   - Bouton de recherche intégré, accès direct par identifiant RefId (ex. `PROJ-123`) et recherche globale pour les administrateurs.
4. **Optimisation mobile (RWD)** :
   - Menu tiroir latéral (Drawer), panneau Bottom-Sheet pour les historiques et pagination en capsule centrée.

---

## 5. 🛡️ Renforcement de la sécurité en production et garde-fous

1. **Filtre global des en-têtes de sécurité** : Intégration de `X-Frame-Options`, `X-Content-Type-Options`, `Strict-Transport-Security` et `Content-Security-Policy`.
2. **Protection contre les moteurs de recherche (`robots.txt`)** : Blocage de l'indexation web des tickets confidentiels.
3. **Prévention des abus** : Alerte pour les rôles invités, filtrage par liste blanche des paramètres et protection contre le double envoi de formulaires.

---

## 6. ⚙️ Paramètres système et améliorations de stabilité

1. Suppression des logs de debug Wicket via l'ajout de `status.nullValid = ` sur l'ensemble des 8 langues.
2. Refactorisation des bascules booléennes vers `IndicatingDropDownChoice`.
3. Enregistrement explicite des pilotes JDBC pour les sources de données mono-connexion.
4. Détection automatique du jeu de caractères UTF-8 pour les fichiers textes joints et chemins de logo relatifs.
5. **Guidage dans les scripts Docker Build et prévention des conflits de Tag Git** :
   - Ajout d'invites au lancement dans `docker/build.bat` et `docker/build.sh` avec commande de synchronisation rapide (`git fetch --tags -f && git reset --hard origin/master`) en cas de conflit d'écrasement de Tag ou de résidus de build locaux.
   - Déploiement de la documentation `docker/` complète dans les 8 langues avec section de dépannage pour la synchronisation des serveurs de compilation.
7. **Rebranding vers JTrac NG & Saut vers le versionnement sémantique (v3.0.0-beta)** :
   - Évolution officielle du projet vers **JTrac NG** (Next Generation) et passage de la version à **3.0.0-beta**, éliminant les collisions de recherche vieilles de 15 ans avec l'ancien JTrac 2.1.0/2.3.x et optimisant le référencement Google SEO.
   - Migration complète du dépôt officiel vers [https://github.com/kafeiou/jtrac-ng](https://github.com/kafeiou/jtrac-ng), avec mise à jour du pied de page, de la barre de navigation mobile et du POM Maven.
8. **Modernisation de la recherche Lucene et affichage de l'historique (Sous-tokens, jokers et affichage de l'historique par défaut)** :
   - **Filtre de sous-tokens (`SubTokenFilter`)** : Décompose les e-mails (ex: `user@gmail.com`) et noms de fichiers (ex: `thunderbird_gmail.pdf`) en sous-tokens (`user`, `gmail`, `com`, `thunderbird`, `pdf`), permettant aux recherches `gmail` de cibler avec précision e-mails et pièces jointes.
   - **Expansion intelligente et jokers en début de mot** : Étend automatiquement les termes en `(term OR term*)`, active `allowLeadingWildcard = true` pour `*terme*` et tolère les séparations de phrases (`phraseSlop = 2`).
   - **Affichage de l'historique par défaut (`showHistory = true`)** : Affiche toutes les révisions et commentaires directement dans les listes de tickets.
   - **Filtrage intelligent de l'historique lors de la recherche par mot-clé (Smart History Filtering)** : Lorsque l'historique est activé et qu'une recherche par mot-clé est effectuée, le système filtre intelligemment pour n'afficher que le ticket initial ou les commentaires de révision contenant le terme, masquant automatiquement les modifications d'état non pertinentes.
   - **Réindexation automatique en arrière-plan au démarrage** : Détecte la mise à jour de l'analyseur (`lucene.analyzer.version = 3.0.0-subtoken-v1`) et réindexe de manière asynchrone.
   - **Migration Docker Hub** : Documentation mise à jour dans les 8 langues vers `kafeiou/jtrac-ng:latest`.

---

## 7. Guide de mise à niveau et de compatibilité

- **Base de données** : Entièrement compatible avec la version 2.3.3-2.0.0 ; **aucun script de migration requis**.
- **Déploiement** : Remplacez simplement le fichier `ROOT.war` du serveur par `target/jtrac.war`.
- **Liens utiles** :
  - [Guide pratique des requêtes par e-mail et Prompts](../llm/PROMPT_EXAMPLES_fr.md)
  - [Notes de version de la version précédente (2.3.3-2.0.0)](release-2.3.3-2.0.0_fr.md)
