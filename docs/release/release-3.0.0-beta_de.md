# JTrac NG Versionshinweise (Release Notes) - 3.0.0-beta

[English](release-3.0.0-beta_en.md) | [繁體中文](release-3.0.0-beta_zh-TW.md) | [简体中文](release-3.0.0-beta_zh-CN.md) | [日本語](release-3.0.0-beta_ja.md) | [Tiếng Việt](release-3.0.0-beta_vi.md) | [Deutsch](release-3.0.0-beta_de.md) | [Español](release-3.0.0-beta_es.md) | [Français](release-3.0.0-beta_fr.md)

---

[![Java](https://img.shields.io/badge/Java-11%20%7C%2017-orange.svg)](https://adoptium.net/)
[![Maven](https://img.shields.io/badge/Maven-3.9+-blue.svg)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](../../license.txt)
[![Version](https://img.shields.io/badge/Version-3.0.0--beta-orange.svg)](../../pom.xml)
[![Status](https://img.shields.io/badge/Status-Beta%20Preview-yellow.svg)](release-3.0.0-beta_de.md)
[![Wicket](https://img.shields.io/badge/Wicket-9.16.0-blue.svg)](https://wicket.apache.org/)
[![Spring](https://img.shields.io/badge/Spring-5.3.37-brightgreen.svg)](https://spring.io/)

> [!NOTE]
> **Aktueller Status: Beta-Vorschauversion (Pre-release / Beta Preview)**  
> Dieses Dokument ist ein lebendiges Änderungsprotokoll (Living Release Notes). Während der Beta-Testphase werden alle nachfolgenden Funktionserweiterungen, Parameteranpassungen und Bugfixes kontinuierlich hier ergänzt.

---

## Inhaltsverzeichnis
1. [Wichtigste Highlights im Überblick](#1-wichtigste-highlights-im-überblick)
2. [🤖 KI-E-Mail-Abfrageassistent (AI Query Copilot mit Ollama)](#2--ki-e-mail-abfrageassistent-ai-query-copilot-mit-ollama)
3. [📦 Abhängigkeiten-Upgrade & Beseitigung von Java-11-Warnungen](#3--abhängigkeiten-upgrade--beseitigung-von-java-11-warnungen)
4. [🎨 UI-Modernisierung, Barrierefreiheit & Design-Umschalter](#4--ui-modernisierung-barrierefreiheit--design-umschalter)
5. [🛡️ Produktionssicherheit & Schutzmechanismen](#5--produktionssicherheit--schutzmechanismen)
6. [⚙️ Systemkonfiguration & Stabilitätsverbesserungen](#6--systemkonfiguration--stabilitätsverbesserungen)
7. [Upgrade- und Kompatibilitätshinweise](#7-upgrade--und-kompatibilitätshinweise)

---

## 1. Wichtigste Highlights im Überblick

Aufbauend auf der Architekturmodernisierung von Version 2.0.0 führt JTrac NG 3.0.0-beta den innovativen **KI-E-Mail-Abfrageassistenten (AI Query Copilot via Ollama)** ein, aktualisiert die zugrundeliegende XML-Engine zur vollständigen Behebung von Java-11-Warnungen, verbessert die Barrierefreiheit der Benutzeroberfläche (4-stufige Schriftgrößenskalierung mit A+++-Modus, 3-Zustands-Theme-Umschalter) und verstärkt die Systemsicherheit.

---

## 2. 🤖 KI-E-Mail-Abfrageassistent (AI Query Copilot mit Ollama)

1. **Zweistufige Abfrageerweiterung & Schutz vor Prompt Injection**:
   - Analysiert E-Mail-Betreff und Inhalt über lokale oder entfernte Ollama-LLMs zur Extraktion von Fachbegriffen und Synonymen.
   - Schützt durch die Sandkasten-Struktur `<untrusted_user_query>` vor schädlichen Anweisungen.
2. **Hybrides gewichtetes Retrieval & zweisprachige Trefferpunkte**:
   - Präzise Bewertung nach Zusammenfassung (+3), Details (+1), Kommentaren (+1) und Anhängen (+1) mit +5 Punkten Bonus für deutsch-englische Übereinstimmungen.
   - Einstellbarer Parameter `llm.retrieval.max_tickets` in Tabelle `config` (Standard: 50).
3. **Map-Reduce-Verarbeitungspipeline**:
   - **Map-Phase**: Analysiert Tickets und Dateianhänge (bis zu 100.000 Zeichen pro Datei; PDF, Word, Excel, TXT, LOG, CSV) in temporäre Zwischenberichte.
   - **Reduce-Phase**: Erstellt eine strukturierte Gesamtsynthese (Management-Summary, Ursachen & Lösungen, Handlungsempfehlungen).
   - Saubere Bereinigung über `finally`-Block ohne Rückstände auf der Festplatte.
4. **14-Tage-Web-Berichtslinks & Offline-HTML-Download**:
   - **Verlässliche Umgehung von E-Mail-Sicherheitsblockaden**: Keine `.html`-Dateianhänge mehr, die von E-Mail-Gateways (Exchange/Outlook/Gmail) blockiert werden; stattdessen Bereitstellung sicherer Direktlinks.
   - **14 Tage Aufbewahrung mit stündlicher automatischer Bereinigung (TTL Auto-Pruning)**: Der Server speichert Berichte für 14 Tage und löscht abgelaufene Dateien stündlich ohne manuellen Verwaltungsaufwand.
   - **100% offline nutzbar mit Ein-Klick-Download**: Feste Aktionsleiste im Web-Bericht zum Herunterladen der HTML-Datei (`JTrac-AI-Report-[Date].html`), inklusive eingebetteter Mermaid.js-Engine für autarken Offline-Betrieb.
5. **Mehrsprachiger Prompt-Leitfaden mit Praxisbeispielen**: Bereitstellung von [`docs/llm/PROMPT_EXAMPLES_*.md`](../llm/PROMPT_EXAMPLES_de.md) in 8 Sprachen.
6. **Ticket-Gruppierung nach Space, ID-Abwärtssortierung & 100% Offline-Mermaid.js-Integration**:
   - **Space-spezifische Untertabellen & Neueste-zuerst-Sortierung (ID DESC)**: Überarbeitung der E-Mail-Vorschautabelle und des HTML-Berichts zur Gruppierung der Tickets nach Projektbereich (Space) mit Ticketanzahl. Innerhalb jedes Spaces werden Tickets strikt nach Ticket-ID absteigend sortiert (`ID DESC`). Der E-Mail-Text bleibt aufgeräumt ohne störende Mermaid-Warnhinweise.
   - **Vollständig autarke Offline-Mermaid.js-Engine**: Einbettung des vollständigen Mermaid.js-Bundles (v10.9.1) in den Classpath und direkte Injektion in den HTML-Bericht, wodurch externe CDN-Aufrufe komplett entfallen. Inklusive automatischer Theme-Anpassung (Dark/Light) und fehlertoleranter Syntax-Behandlung.
   - **Zweistufige Flowchart-Prompt-Regeln mit Anführungszeichen-Absicherung**: Verbindliche Vorgabe zur Erstellung von `flowchart TD/LR`-Diagrammen sowohl in der Map-Phase (Fehlerbehebungsabläufe) als auch in der Reduce-Phase (Gesamtfazit & Handlungsempfehlungen), mit zwingender Kapselung von Knotentexten in doppelte Anführungszeichen.
7. **Strikte JTrac-Datenbindung, Verpflichtende Kennzeichnung externen Wissens, Null-Treffer-Benachrichtigung & Secrets-Maskierung**:
   - **Strikte Bindung an den JTrac-Kontext (Strict JTrac Context Grounding)**: Verbindliche Festlegung, dass LLM-Antworten ausschließlich auf den autorisierten JTrac-Tickets und Anhängen basieren müssen. Die Einzelticket-Analyse (Map-Phase) und die Haupterkenntnisse (Reduce Abschnitt 2) verbieten externe Spekulationen zu 100%.
   - **Verpflichtende Kennzeichnung externen Wissens in der Synthese (Mandatory External Knowledge Tagging)**: Nur in der Gesamtzusammenfassung (Reduce Abschnitt 1) und bei Handlungsempfehlungen (Abschnitt 3) darf das Modell bewährte Branchenpraktiken ergänzen, falls interne Tickets keine vollständige Lösung enthalten. Dies **MUSS zwingend mit dem Hinweis `(Note: Recommended based on external reference knowledge)`** (`（參考外部資訊給予建議）`) gekennzeichnet werden, um den Ursprung der Empfehlung transparent zu machen.
   - **Sichere Null-Treffer-Benachrichtigung (Zero-Hit Safe Notice)**: Findet eine Anfrage in den autorisierten Spaces keinerlei passende Tickets oder Anhänge, greift das System sofort ein und sendet eine strukturierte Null-Treffer-Benachrichtigung mit den autorisierten Bereichen und Richtlinien, ohne das Modell fabulieren zu lassen, und löscht die E-Mail.
   - **Maskierung vertraulicher Zugangsdaten (Confidential Credentials Masking)**: Implementierung von `SensitiveDataMasker`, um Passwörter, Bearer-Tokens, API-Keys, Private-Key-Blöcke und URL-Zugangsdaten in HTML-Berichten automatisch durch `***` zu ersetzen, während Benutzernamen und Ticket-IDs unverändert erhalten bleiben.
   - **Empfohlene Hardware & 200K-Langkontext (Recommended Hardware & 200K Context)**: Offizielle Hardware- und Modellempfehlungen im Prompt-Leitfaden mit Empfehlung der Flaggschiff-GPU NVIDIA RTX 5090 (32GB VRAM) und `qwen2.5:32b` sowie obligatorischer 200K-Kontextfenster-Konfiguration (`num_ctx 200000`) via Ollama-Modelfile; dringende Warnung vor unterdimensionierten Modellen zur Vermeidung von Truncation und fehlerhaften Analysen.

---

## 3. 📦 Abhängigkeiten-Upgrade & Beseitigung von Java-11-Warnungen

1. **Aktualisierung von `dom4j` auf `2.1.4`**:
   - Ersetzt das veraltete `dom4j:1.6.1` durch `org.dom4j:dom4j:2.1.4`.
   - Beseitigt die Warnung `WARNING: An illegal reflective access operation has occurred` unter Tomcat 9 / Java 11 restlos.

---

## 4. 🎨 UI-Modernisierung, Barrierefreiheit & Design-Umschalter

1. **4-stufige Schriftgrößenskalierung**:
   - 100% (Standard), 115% (Angenehm), 130% (Klar) und **A+++ Riesenmodus (145%)** mit Anti-FOUC-Schutz und `localStorage`-Speicherung.
2. **3-Zustands-Theme-Umschalter**:
   - Umschaltung zwischen Auto (System), Hell und Dunkel mit einem einzigen Klick.
3. **Einheitliche Suchleiste mit Schnellnavigation**:
   - Integrierter Suchbutton, RefId-Erkennung (z. B. `PROJ-123`) zur direkten Ticketnavigation und globale Suche für Administratoren.
4. **Mobile Optimierung (RWD)**:
   - Seitliches Drawer-Menü, Bottom-Sheet für Verlaufsdetails und zentrierte Kapsel-Paginierung.

---

## 5. 🛡️ Produktionssicherheit & Schutzmechanismen

1. **Sicherheits-Header**: Automatische Injektion von `X-Frame-Options`, `X-Content-Type-Options`, `Strict-Transport-Security` und `Content-Security-Policy`.
2. **Suchmaschinensperre (`robots.txt`)**: Schutz sensibler Ticketdaten vor Web-Crawlern.
3. **Manipulationsschutz**: Warnhinweis für Gastrollen, Whitelist-Filterung von Parametern und Schutz vor doppeltem Formularversand.

---

## 6. ⚙️ Systemkonfiguration & Stabilitätsverbesserungen

1. Beseitigung von Wicket-Debug-Warnungen durch Hinzufügen von `status.nullValid = ` in allen Sprachen.
2. Modernisierung von Boolean-Schaltern auf `IndicatingDropDownChoice`.
3. Explizite Registrierung von JDBC-Treibern für vereinfachte Datenbankumgebungen.
4. Automatische UTF-8-Erkennung für Textanhänge und korrigierte Logo-Pfade.
5. **Docker-Build-Skripte & Git-Tag-Konfliktbehebung**:
   - Startmeldungen in `docker/build.bat` und `docker/build.sh` mit Schnell-Synchronisierungsanweisungen (`git fetch --tags -f && git reset --hard origin/master`) bei Tag-Überschreibungen oder lokalen Build-Artefakten hinzugefügt.
   - Vollständige 8-sprachige `docker/`-Dokumentation mit Anleitungen zur Fehlerbehebung bei der Quellcodesynchronisierung bereitgestellt.
7. **Rebranding zu JTrac NG & Versionssprung auf Semantic Versioning (v3.0.0-beta)**:
   - Offizielles Rebranding zu **JTrac NG** (Next Generation) und Versionssprung auf **3.0.0-beta**, wodurch 15 Jahre alte Suchkollisionen mit Legacy-JTrac 2.1.0/2.3.x eliminiert und die Google-SEO-Auffindbarkeit maximiert werden.
   - Vollständige Migration des offiziellen Repositorys nach [https://github.com/kafeiou/jtrac-ng](https://github.com/kafeiou/jtrac-ng), inklusive Synchronisation der Web-Footer, mobilen Navigationsleiste und Maven-POMs.
8. **Modernisierung der Lucene-Volltextsuche & Historienanzeige (Sub-Tokens, Wildcard-Erweiterung & Standard-Historienansicht)**:
   - **Sub-Token-Filter (`SubTokenFilter`)**: Teilt E-Mail-Adressen (z. B. `user@gmail.com`) und zusammengesetzte Dateinamen (z. B. `thunderbird_gmail.pdf`) in Teil-Tokens (`user`, `gmail`, `com`, `thunderbird`, `pdf`) auf, sodass Suchen nach `gmail` E-Mails und Anhänge zuverlässig finden.
   - **Intelligente Wildcard-Erweiterung & Führende Platzhalter**: Erweitert Suchbegriffe automatisch zu `(term OR term*)`, aktiviert `allowLeadingWildcard = true` für `*begriff*` und unterstützt Phrasen-Toleranz (`phraseSlop = 2`).
   - **Globaler Standard für Historienanzeige (`showHistory = true`)**: Zeigt in Ticketlisten standardmäßig alle Revisionskommentare direkt an.
   - **Automatischer Hintergrund-Reindex beim Start**: Erkennt Aktualisierungen der Analyzer-Version (`lucene.analyzer.version = 3.0.0-subtoken-v1`) und stößt eine asynchrone Neuindizierung an.
   - **Docker-Hub-Migration**: Dokumentation in allen 8 Sprachen auf `kafeiou/jtrac-ng:latest` aktualisiert.

---

## 7. Upgrade- und Kompatibilitätshinweise

- **Datenbank**: Vollständig abwärtskompatibel zu Version 2.3.3-2.0.0; **kein Migrationsskript erforderlich**.
- **Bereitstellung**: Ersetzen Sie die vorhandene `ROOT.war` durch `target/jtrac.war`.
- **Weiterführende Links**:
  - [JTrac AI E-Mail-Abfragen & Prompt-Leitfaden](../llm/PROMPT_EXAMPLES_de.md)
  - [Vorherige Versionshinweise (2.3.3-2.0.0)](release-2.3.3-2.0.0_de.md)
