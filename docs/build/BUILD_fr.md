# Guide de Compilation et de Construction JTrac (Français)

[English](BUILD_en.md) | [繁體中文](BUILD_zh-TW.md) | [简体中文](BUILD_zh-CN.md) | [日本語](BUILD_ja.md) | [Tiếng Việt](BUILD_vi.md) | [Deutsch](BUILD_de.md) | [Español](BUILD_es.md) | [Français](BUILD_fr.md)

Ce guide détaille la compilation et le packaging du projet JTrac NG 3.0.0-beta avec Apache Maven, en couvrant le cycle de vie Maven, la mise en cache des dépendances, la structure interne des archives WAR et CLI, la matrice de validation des conteneurs Web et le dépannage de compilation.

---

## 1. Prérequis Environnementaux

Avant de commencer la compilation, assurez-vous que votre environnement respecte les conditions suivantes :

- **Système d'exploitation** : Windows / Linux / macOS
- **Kit de développement Java (JDK)** : **Java 11+ / 17+** (JDK 17 LTS recommandé, ex: `W:\developer\jdk-17.0.9`, prérequis minimal Java 11+)
  > [!IMPORTANT]
  > L'architecture a été modernisée vers Spring 5.3.x, Hibernate 5.6.x et Apache Wicket 9.x avec pour cible de bytecode **Java 11**. **JDK 8 n'est plus supporté** ; ne tentez pas de compiler avec JDK 8.
- **Apache Maven** : **Maven 3.9+** (ex: `W:\developer\apache-maven-3.9.9`)

### Configuration des Variables d'Environnement

- **Windows (PowerShell)** :
  ```powershell
  $env:JAVA_HOME = "W:\developer\jdk-17.0.9"
  $env:PATH = "W:\developer\apache-maven-3.9.9\bin;$env:PATH"
  ```
- **Windows (CMD)** :
  ```cmd
  set "JAVA_HOME=W:\developer\jdk-17.0.9"
  set "PATH=W:\developer\apache-maven-3.9.9\bin;%PATH%"
  ```
- **Linux / macOS (Bash/Zsh)** :
  ```bash
  export JAVA_HOME="/usr/lib/jvm/java-17-openjdk"
  export PATH="$JAVA_HOME/bin:$PATH"
  ```

Vérification de l'environnement :
```bash
mvn -version
```
La sortie doit afficher correctement Maven 3.9+ ainsi que Java 11 ou 17.

---

## 2. Matrice des Commandes de Construction Maven

Exécutez les commandes suivantes depuis le répertoire racine du projet (contenant `pom.xml`) :

| Commande | Phase / Action | Description |
|---|---|---|
| `mvn clean compile` | Compiler le Code Source | Nettoie les artefacts précédents et compile `src/main/java` avec filtrage des ressources UTF-8 |
| `mvn test-compile` | Compiler les Tests | Compile les classes de test situées sous `src/test/java` |
| `mvn test` | Exécuter les Tests | Exécute les tests unitaires JUnit 5 (avec HSQLDB en mémoire intégrée, sans base de données externe) |
| `mvn package` | Packaging de Production | Exécute les tests et génère l'archive Web officielle (`target/jtrac.war`) |
| `mvn package -DskipTests` | Packaging Rapide | Ignore les tests unitaires et génère rapidement `target/jtrac.war` |
| `mvn clean` | Nettoyage | Supprime tous les fichiers temporaires et caches sous `target/` |
| `mvn clean package -f tools/jtrac-exporter/pom.xml -DskipTests` | Packager l'Outil CLI | Compile et assemble l'outil d'exportation de tickets autonome en `tools/jtrac-exporter.jar` |

---

## 3. Gestion Automatique et Cache des Dépendances (`~/.m2/repository`)

JTrac repose sur l'architecture standard Apache Maven. Toutes les bibliothèques tierces (Spring 5.3.x, Wicket 9.x, Hibernate 5.6.x, Spring Security 5.8.x...) sont déclarées dans le fichier racine [`pom.xml`](../../pom.xml).

### Processus de Téléchargement et de Mise en Cache :
1. Lors de la première exécution de `mvn compile` ou `mvn package`, Maven se connecte au dépôt central (Maven Central) pour résoudre l'arbre des dépendances.
2. Tous les fichiers JAR téléchargés sont conservés dans le cache local de l'utilisateur :
   - **Windows** : `%USERPROFILE%\.m2\repository\`
   - **Linux / macOS** : `~/.m2/repository/`
3. Les compilations ultérieures s'exécutent entièrement hors ligne à partir de ce cache. **Les développeurs n'ont jamais besoin de télécharger ou configurer manuellement des fichiers JAR**.

---

## 4. Structure de l'Archive WAR (`WEB-INF/lib/`)

L'exécution de `mvn package` génère l'archive Java Web standard dans le dossier `target/` : [`target/jtrac.war`](../../target/jtrac.war).

### Organisation Interne des Dossiers :
```text
jtrac.war
├── META-INF/
│   └── MANIFEST.MF
├── WEB-INF/
│   ├── classes/                 <-- Classes compilées de JTrac et bundles de ressources UTF-8
│   │   ├── info/jtrac/...
│   │   └── messages*.properties
│   ├── lib/                     <-- [Cœur : Toutes les dépendances JAR tierces modernes]
│   │   ├── spring-core-5.3.37.jar
│   │   ├── wicket-core-9.16.0.jar
│   │   ├── hibernate-core-5.6.15.Final.jar
│   │   ├── spring-security-core-5.8.14.jar
│   │   ├── hsqldb-2.7.2.jar
│   │   └── ... (toutes les autres bibliothèques)
│   └── web.xml                  <-- Descripteur de déploiement Servlet 4.0
└── resources/                   <-- Ressources statiques (CSS, icônes, feuilles de style)
```

- **Isolation Classloader** : Les conteneurs de servlets isolent automatiquement le répertoire `WEB-INF/lib/` de chaque application.
- **Déploiement Autonome** : Aucun fichier JAR n'a besoin d'être installé sur le serveur hôte ; il suffit de déployer `jtrac.war`.

---

## 5. Matrice de Compatibilité des Conteneurs Web

JTrac NG 3.0.0-beta est conforme à la spécification Servlet 4.0 (`javax.servlet`). Le fichier WAR peut être directement déployé sur les conteneurs modernes :

| Conteneur Web | Versions Supportées | Méthode de Déploiement |
|---|---|---|
| **Jetty 10.x** | 10.0.x (Recommandé) | **Prêt à l'emploi** : Copiez `target/jtrac.war` en `webapps/ROOT.war` et démarrez. |
| **Jetty 12.x** | 12.0.x (Dernière) | **Support natif** : Activez le module `ee8` :<br/>`java -jar start.jar --add-modules=server,http,ee8-deploy,ee8-webapp`. |
| **Tomcat 9.x** | 9.0.x (Recommandé) | **Prêt à l'emploi** : Copiez `target/jtrac.war` en `webapps/ROOT.war` et démarrez. |
| **Tomcat 10.x / 11.x** | 10.1.x / 11.0.x | **Migration automatique** :<br/>1. **Méthode A** : Placer dans `webapps-javaee/` pour conversion automatique au démarrage.<br/>2. **Méthode B** : Convertir avec l'outil officiel `jakartaee-migration` et placer dans `webapps/`. |

### Exemple de Vérification Locale avec Jetty 10 :
1. Copiez `target/jtrac.war` vers `W:\developer\jetty-10.0.26\webapps\ROOT.war`.
2. Démarrez Jetty :
   ```powershell
   & "W:\developer\jdk-17.0.9\bin\java.exe" -jar W:\developer\jetty-10.0.26\start.jar
   ```
3. Ouvrez `http://localhost:8888/` dans votre navigateur (identifiants : `admin` / `admin`).

---

## 6. Vérification des Artefacts et Dépannage (Build Troubleshooting)

### 6.1 Liste de Contrôle Post-Compilation
À la fin de la compilation, vérifiez la présence des fichiers suivants :
- [ ] `target/jtrac.war` (taille d'environ 18~22 Mo, allégé après suppression des bibliothèques POI obsolètes)
- [ ] `tools/jtrac-exporter.jar` (si l'outil CLI a été généré)

### 6.2 Problèmes Courants et Solutions

1. **Erreur d'Encodage de Caractères (`unmappable character for encoding`)** :
   - Cause : La page de code par défaut de Windows console ne lit pas les commentaires UTF-8.
   - Solution : Définissez l'encodage Maven avant de lancer la compilation :
     ```powershell
     $env:MAVEN_OPTS = "-Dfile.encoding=UTF-8"
     ```
2. **Version Cible Invalide (`Fatal error compiling: invalid target release: 11`)** :
   - Cause : La version active dans le terminal est JDK 8 ou antérieure.
   - Solution : Basculez `JAVA_HOME` vers JDK 11 ou JDK 17.
3. **Téléchargement de Dépendances Interrompu** :
   - Solution : Forcez Maven à actualiser le cache :
     ```bash
     mvn clean compile -U
     ```
4. **Mémoire Insuffisante (`java.lang.OutOfMemoryError`)** :
   - Solution : Augmentez la mémoire allouée à Maven :
     ```bash
     export MAVEN_OPTS="-Xmx1024m -XX:MaxMetaspaceSize=256m"
     ```

---

## 7. Alternative sans Installation Locale : Build Multi-Étapes Docker

Si vous préférez compiler dans un environnement conteneurisé propre et isolé sans installer JDK ni Maven localement, utilisez le build multi-étapes officiel :

👉 **Consultez le guide dédié : [`docker/README.md`](../../docker/README.md)**
