# Empaquetage et Déploiement Docker de JTrac NG (Jetty 12.x + Eclipse Temurin 17+)

[English](README.md) | [繁體中文](README_zh-TW.md) | [簡體中文](README_zh-CN.md) | [日本語](README_ja.md) | [Tiếng Việt](README_vi.md) | [Deutsch](README_de.md) | [Español](README_es.md) | [Français](README_fr.md)

Ce répertoire propose un environnement natif de build multi-étapes (Multi-stage Build) et de déploiement en conteneur Docker pour JTrac NG modernisé.

---

## Points forts (Features)

- **Environnement modernisé** : Basé sur l'image officielle `jetty:12-jre17-eclipse-temurin` avec les modules `ee8-deploy` et `ee8-webapp`, prenant en charge nativement Servlet 4.0 (`javax.servlet`).
- **Build multi-étapes (Multi-stage Build)** : Compilation automatique de `jtrac.war` depuis les sources via `maven:3.9-eclipse-temurin-17`, sans nécessiter l'installation préalable de JDK ou Maven sur l'hôte.
- **Support typographique multilingue complet** : Intégration de `fontconfig`, `fonts-noto-cjk`, `fonts-noto-core` et `fonts-dejavu-core`, éliminant toute altération de caractères lors de l'indexation et de la génération des rapports.
- **Correction automatique des permissions et exécution sécurisée** : Au démarrage, l'Entrypoint ajuste la propriété du volume `/jtrac-data` vers `jetty:jetty` (UID 999) et abaisse les privilèges de manière sécurisée via `gosu`.

---

## Démarrage Rapide (Quick Start)

### Méthode 1 : Commande Docker native (Recommandée)

Depuis le répertoire `docker`, lancez le build en utilisant la racine du projet (`..`) comme contexte :

```bash
cd docker
docker build -f Dockerfile -t jtrac-ng:latest -t jtrac-ng:3.0.0-beta ..
docker run -d -p 8888:8080 -v jtrac_data:/jtrac-data --name jtrac-ng jtrac-ng:latest
```

Ouvrez `http://localhost:8888/` dans votre navigateur (identifiants par défaut : `admin` / `admin`).

---

### Méthode 2 : Scripts utilitaires multiplateformes

- **Windows** :
  ```cmd
  cd docker
  build.bat
  run.bat
  ```

- **Linux / macOS** :
  ```bash
  cd docker
  chmod +x *.sh
  ./build.sh
  ./run.sh
  ```

---

### Méthode 3 : Exécuter l'image officielle de Docker Hub

**[https://hub.docker.com/r/kafeiou/jtrac-ng](https://hub.docker.com/r/kafeiou/jtrac-ng)**

```bash
docker run -d -p 8888:8080 -v jtrac_data:/jtrac-data --name jtrac-ng kafeiou/jtrac-ng:latest
```

---

## Dépannage : Synchronisation du code et résolution des conflits de Tag (Troubleshooting)

Si la commande `git pull` échoue sur votre serveur de build ou environnement de test :

1. **Rejet d'écrasement de Tag (`would clobber existing tag`)** :
   Lorsqu'un Tag de version (ex. `3.0.0-beta`) est mis à jour de force sur le dépôt distant, Git bloque son écrasement local par sécurité. Ajoutez `-f` :
   ```bash
   git pull --tags -f
   ```

2. **Réinitialisation rapide sur l'état distant (Recommandé pour les machines de Build)** :
   Pour éliminer les modifications temporaires et écarts de fin de ligne, et synchroniser avec 100% de succès :
   ```bash
   git fetch --tags -f && git reset --hard origin/master
   ```

3. **Configurer un alias Git de synchronisation en un clic (Git Alias)** :
   Configurez cet alias une seule fois pour synchroniser proprement en tapant simplement `git sync` :
   ```bash
   git config --global alias.sync "!git fetch --tags -f && git reset --hard origin/master"
   ```
