# Guide de compilation et de construction JTrac NG (Français)

[English](BUILD_en.md) | [繁體中文](BUILD_zh-TW.md) | [简体中文](BUILD_zh-CN.md) | [日本語](BUILD_ja.md) | [Tiếng Việt](BUILD_vi.md) | [Deutsch](BUILD_de.md) | [Español](BUILD_es.md) | [Français](BUILD_fr.md)

Ce guide fournit des instructions complètes sur la compilation et l'empaquetage du projet JTrac NG 3.0.0-beta avec Apache Maven, couvrant le cycle de vie Maven, la gestion des dépendances, la structure des archives WAR et portables autonomes, la matrice de déploiement et la résolution des pannes.

---

## 1. Prérequis

Avant de compiler, vérifiez que votre environnement local satisfait aux exigences suivantes :

- **Système d'exploitation** : Windows / Linux / macOS
- **Kit de développement Java (JDK)** : **Java 21 LTS** ([Eclipse Temurin JDK 21+](https://adoptium.net/temurin/releases/?version=21) recommandé, ex. `W:\developer\jdk-21.0.9`, minimum requis Java 21)
  > [!IMPORTANT]
  > L'architecture centrale a été entièrement modernisée vers **Spring 6.2.x**, **Jakarta EE 10 (Servlet 6.0)**, **Hibernate 5.6.x (Jakarta)** et **Apache Wicket 10.x**, ciblant le bytecode **Java 21**. **JDK 8 / 11 / 17 n'est plus pris en charge**. Utilisez obligatoirement JDK 21+.
- **Apache Maven** : **Maven 3.9+** (ex. `W:\developer\apache-maven-3.9.9`)

### Configuration des variables d'environnement

- **Windows (PowerShell)** :
  ```powershell
  $env:JAVA_HOME = "W:\developer\jdk-21.0.9"
  $env:PATH = "W:\developer\apache-maven-3.9.9\bin;$env:PATH"
  ```
- **Windows (CMD)** :
  ```cmd
  set "JAVA_HOME=W:\developer\jdk-21.0.9"
  set "PATH=W:\developer\apache-maven-3.9.9\bin;%PATH%"
  ```
- **Linux / macOS (Bash/Zsh)** :
  ```bash
  export JAVA_HOME="/usr/lib/jvm/java-21-openjdk"
  export PATH="$JAVA_HOME/bin:$PATH"
  ```

Vérification :
```bash
mvn -version
```
La sortie doit afficher Maven 3.9+ et Java 21.

---

## 2. Matrice des commandes de construction Maven

Exécutez les commandes suivantes depuis la racine du projet (contenant `pom.xml`) :

| Commande | Phase / Action | Description | Artefact produit |
|---|---|---|---|
| `mvn clean compile` | Compilation des sources | Nettoie et compile `src/main/java` avec filtrage UTF-8 | `target/classes/` |
| `mvn test-compile` | Compilation des tests | Compile toutes les classes de test de `src/test/java` | `target/test-classes/` |
| `mvn test` | Exécution des tests | Exécute les 198 tests unitaires JUnit 5 (HSQLDB en mémoire) | Rapports de test |
| `mvn package` | Empaquetage WAR standard | Exécute les tests et produit l'archive Web standard | `target/jtrac.war` |
| `mvn package -DskipTests` | Empaquetage WAR rapide | Ignore les tests pour une génération rapide du WAR | `target/jtrac.war` |
| `mvn package -Pportable` | Empaquetage portable complet | Exécute les tests et produit l'archive autonome avec Jetty 12.1 EE10 | `target/jtrac-ng-3.0.0-beta-portable.zip` |
| `mvn package -Pportable -DskipTests` | Empaquetage portable rapide | Ignore les tests pour générer l'archive portable ultra-rapidement | `target/jtrac-ng-3.0.0-beta-portable.zip` |
| `package-portable.bat` | Script Windows | Double-clic à la racine ; ignore les tests automatiquement | `target/jtrac-ng-3.0.0-beta-portable.zip` |
| `./package-portable.sh` | Script Linux/macOS | Script Bash pour générer l'archive portable | `target/jtrac-ng-3.0.0-beta-portable.zip` |
| `mvn clean` | Nettoyage | Supprime `target/` et les fichiers temporaires | - |
| `mvn clean package -f tools/jtrac-exporter/pom.xml -DskipTests` | Outil CLI | Compile l'outil d'exportation autonome (Fat JAR) | `tools/jtrac-exporter.jar` |

---

## 3. Gestion automatique des dépendances (`~/.m2/repository`)

JTrac NG est architecturé autour d'Apache Maven. Toutes les bibliothèques tierces sont déclarées dans [`pom.xml`](../../pom.xml).

---

## 4. Structure interne du WAR (`WEB-INF/lib/`)

L'exécution de `mvn package` produit : [`target/jtrac.war`](../../target/jtrac.war).

```text
jtrac.war
├── META-INF/
│   └── MANIFEST.MF
├── WEB-INF/
│   ├── classes/                 <-- Classes compilées et fichiers de ressources UTF-8
│   │   ├── info/jtrac/...
│   │   └── messages*.properties
│   ├── lib/                     <-- [Bibliothèques tierces modernes Jakarta EE 10]
│   │   ├── spring-core-6.2.19.jar
│   │   ├── wicket-core-10.12.0.jar
│   │   ├── hibernate-core-5.6.15.Final.jar
│   │   ├── spring-security-core-6.5.11.jar
│   │   ├── jakarta.servlet-api-6.0.0.jar
│   │   ├── hsqldb-2.7.4.jar
│   │   └── ... (toutes les autres dépendances)
│   └── web.xml                  <-- Descripteur Servlet 6.0
└── resources/                   <-- Ressources statiques (CSS, logo SVG vectoriel)
```

---

## 5. Matrice de compatibilité des conteneurs Web

JTrac NG 3.0.0-beta respecte la spécification **Jakarta EE 10 / Servlet 6.0** (`jakarta.servlet.*`) :

| Conteneur | Versions prises en charge | Méthode de déploiement |
|---|---|---|
| **Jetty 12.x** | 12.1.x+ (Recommandé) | **Support natif** : Activez le module `ee10` :<br/>`java -jar start.jar --add-modules=server,http,ee10-deploy,ee10-webapp`<br/>Ou lancez directement la version portable (`jtrac-ng-3.0.0-beta-portable.zip`). |
| **Tomcat 10.x / 11.x** | 10.1.x / 11.0.x | **Support natif** : Prise en charge native de Jakarta EE 10. Copiez `target/jtrac.war` sous `webapps/ROOT.war`. |
| **Conteneur Docker** | 3.0.0-beta | **Image officielle** : Basée sur `jetty:12-jdk21-eclipse-temurin`. |

---

## 6. Dépannage et vérification

### 6.1 Vérification
- [ ] `target/jtrac.war` (environ 50~56 Mo)
- [ ] `target/jtrac-ng-3.0.0-beta-portable.zip` (environ 98 Mo)
- [ ] `tools/jtrac-exporter.jar` (si l'outil CLI a été compilé)

### 6.2 Problèmes fréquents

1. **Erreur de version (`invalid target release: 21`)** :
   - Cause : Le JDK actif est 17 ou plus ancien.
   - Solution : Configurez `JAVA_HOME` sur JDK 21+ et vérifiez avec `java -version`.
2. **Erreurs d'encodage (`unmappable character for encoding`)** :
   - Solution : Définissez l'encodage Maven en UTF-8 :
     ```powershell
     $env:MAVEN_OPTS = "-Dfile.encoding=UTF-8"
     ```
3. **Dépendances corrompues** :
   - Solution : Forcez la mise à jour :
     ```bash
     mvn clean compile -U
     ```

---

## 7. Alternative : Construction Docker multi-étapes

Pour compiler dans un conteneur propre sans installer JDK ou Maven localement :

👉 **Consultez le guide dédié : [`docker/README.md`](../../docker/README.md)**
