# Notas de la Versión de JTrac NG (Release Notes) - 3.0.0-beta

[English](release-3.0.0-beta_en.md) | [繁體中文](release-3.0.0-beta_zh-TW.md) | [简体中文](release-3.0.0-beta_zh-CN.md) | [日本語](release-3.0.0-beta_ja.md) | [Tiếng Việt](release-3.0.0-beta_vi.md) | [Deutsch](release-3.0.0-beta_de.md) | [Español](release-3.0.0-beta_es.md) | [Français](release-3.0.0-beta_fr.md)

---

[![Java](https://img.shields.io/badge/Java-11%20%7C%2017-orange.svg)](https://adoptium.net/)
[![Maven](https://img.shields.io/badge/Maven-3.9+-blue.svg)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](../../license.txt)
[![Version](https://img.shields.io/badge/Version-3.0.0--beta-orange.svg)](../../pom.xml)
[![Status](https://img.shields.io/badge/Status-Beta%20Preview-yellow.svg)](release-3.0.0-beta_es.md)
[![Wicket](https://img.shields.io/badge/Wicket-9.16.0-blue.svg)](https://wicket.apache.org/)
[![Spring](https://img.shields.io/badge/Spring-5.3.37-brightgreen.svg)](https://spring.io/)

> [!NOTE]
> **Estado actual: Versión previa de pruebas Beta (Pre-release / Beta Preview)**  
> Este documento es un registro de cambios dinámico (Living Release Notes). Durante la fase de pruebas Beta, cualquier mejora, ajuste de parámetros y corrección de errores posterior se agregará automáticamente aquí.

---

## Índice
1. [Resumen de características principales](#1-resumen-de-características-principales)
2. [🤖 Asistente de consultas por correo con IA (AI Query Copilot con Ollama)](#2--asistente-de-consultas-por-correo-con-ia-ai-query-copilot-con-ollama)
3. [📦 Actualización de dependencias y eliminación de advertencias en Java 11](#3--actualización-de-dependencias-y-eliminación-de-advertencias-en-java-11)
4. [🎨 Modernización de interfaz, Accesibilidad de fuentes y Temas](#4--modernización-de-interfaz-accesibilidad-de-fuentes-y-temas)
5. [🛡️ Fortalecimiento de seguridad en producción y protecciones](#5--fortalecimiento-de-seguridad-en-producción-y-protecciones)
6. [⚙️ Configuración del sistema y mejoras de estabilidad](#6--configuración-del-sistema-y-mejoras-de-estabilidad)
7. [Guía de actualización y compatibilidad](#7-guía-de-actualización-y-compatibilidad)

---

## 1. Resumen de características principales

Sobre la base de la modernización estructural de la versión 2.0.0, JTrac NG 3.0.0-beta incorpora el innovador **Asistente de consultas por correo con IA (AI Query Copilot con Ollama)**, actualiza el motor XML para eliminar por completo las advertencias de acceso reflectivo en Java 11, refuerza la accesibilidad de la interfaz (escala de 4 niveles con modo A+++ y selector de 3 temas) y robustece la seguridad.

---

## 2. 🤖 Asistente de consultas por correo con IA (AI Query Copilot con Ollama)

1. **Expansión de consultas en dos fases y protección contra inyección de Prompts**:
   - Analiza el asunto y cuerpo de los correos mediante Ollama LLM para extraer términos técnicos bilingües y sinónimos.
   - Aplica el aislamiento seguro `<untrusted_user_query>` para evitar secuestro de instrucciones o fugas de datos.
2. **Búsqueda ponderada híbrida y puntos de coincidencia bilingüe**:
   - Puntúa según Resumen (+3), Detalle (+1), Comentarios (+1) y Adjuntos (+1), con una bonificación de +5 puntos por coincidencias bilingües.
   - Registra el parámetro `llm.retrieval.max_tickets` en la tabla `config` (por defecto 50).
3. **Procesamiento distribuido Map-Reduce**:
   - **Fase Map**: Analiza tickets individuales y documentos adjuntos (hasta 100.000 caracteres por archivo; PDF, Word, Excel, TXT, LOG, CSV) generando resúmenes intermedios.
   - **Fase Reduce**: Elabora un informe estructurado final (Resumen ejecutivo, Causas y soluciones, Recomendaciones).
   - Limpieza garantizada mediante bloques `finally` sin archivos temporales residuales.
4. **Enlaces de informe Web con retención de 14 días y descarga de HTML offline**:
   - **Eliminación total del bloqueo por pasarelas de correo**: Se suprimen los adjuntos `.html` propensos a bloqueos por gateways empresariales (Exchange/Outlook/Gmail), sustituyéndolos por enlaces seguros.
   - **Ciclo de vida de 14 días y limpieza horaria automática (TTL Auto-Pruning)**: El servidor conserva los informes 14 días y elimina automáticamente los archivos vencidos mediante una tarea programada cada hora.
   - **100% utilizable sin conexión y descarga con un clic**: Barra de acciones persistente en el informe web para descargar el archivo HTML (`JTrac-AI-Report-[Date].html`), con el motor Mermaid.js integrado para funcionamiento autónomo offline.
5. **Guía de Prompts en 8 idiomas**: Documento práctico disponible en [`docs/llm/PROMPT_EXAMPLES_*.md`](../llm/PROMPT_EXAMPLES_es.md).
6. **Agrupación por Space, orden descendente por ID e integración de Mermaid.js 100% offline**:
   - **Subtablas agrupadas por Space y orden descendente de ID (ID DESC)**: Reestructuración de la tabla de resumen por correo y del informe HTML para agrupar los tickets por espacio de proyecto (Space) con encabezado de recuento; dentro de cada Space los tickets se ordenan de más reciente a más antiguo (`ID DESC`). El cuerpo del correo se mantiene limpio sin avisos innecesarios de Mermaid.
   - **Motor Mermaid.js autónomo 100% fuera de línea**: Inclusión del paquete completo de Mermaid.js (v10.9.1) en el Classpath e inyección directa en el informe HTML, eliminando cualquier dependencia de CDN externas. Incluye sincronización automática de modo oscuro/claro y manejo de degradación ante errores de sintaxis.
   - **Reglas estrictas de diagramas de flujo en dos niveles con comillas protectoras**: Exigencia obligatoria de diagramas `flowchart TD/LR` en la fase Map (flujos de resolución paso a paso) y en la fase Reduce (resumen ejecutivo y acciones recomendadas), obligando al uso de comillas dobles en las etiquetas de los nodos.
7. **Estricta vinculación a datos de JTrac, Etiquetado obligatorio de conocimiento externo, Aviso de cero coincidencias y Enmascaramiento de credenciales**:
   - **Vinculación estricta al contexto cerrado de JTrac (Strict JTrac Context Grounding)**: Se restringe que las respuestas del LLM se basen única y exclusivamente en los tickets y archivos adjuntos autorizados de JTrac. El análisis individual de tickets (fase Map) y los hallazgos clave (Reduce Sección 2) prohíben al 100% la inclusión de especulaciones externas.
   - **Etiquetado obligatorio de conocimiento externo en la síntesis (Mandatory External Knowledge Tagging)**: Únicamente en el resumen ejecutivo global (Reduce Sección 1) y en las acciones recomendadas (Sección 3), el modelo puede complementar con mejores prácticas de la industria si los tickets internos no contienen una remediación completa, pero **DEBE añadir obligatoriamente la etiqueta `(Note: Recommended based on external reference knowledge)`** (`（參考外部資訊給予建議）`), asegurando una procedencia totalmente transparente.
   - **Aviso seguro de cero coincidencias (Zero-Hit Safe Notice)**: Si la consulta del usuario no arroja ningún ticket ni adjunto en los Spaces autorizados, el sistema intercepta inmediatamente la petición y envía un correo informativo de cero coincidencias con los Spaces autorizados y las políticas de datos, evitando alucinaciones del modelo y purgando el buzón.
   - **Enmascaramiento de credenciales confidenciales (Confidential Credentials Masking)**: Implementación de `SensitiveDataMasker` para ocultar automáticamente contraseñas, Bearer tokens, claves API, bloques de clave privada y credenciales en URLs en los informes HTML reemplazándolos por `***`, preservando íntegramente los nombres de usuario y los IDs de ticket.
   - **Hardware recomendado y contexto extendido de 200K (Recommended Hardware & 200K Context)**: Incorporación de directrices oficiales de hardware y modelo en la guía de Prompts, recomendando la GPU insignia NVIDIA RTX 5090 (32GB VRAM) y `qwen2.5:32b`, con configuración obligatoria de 200K de contexto (`num_ctx 200000`) mediante Modelfile en Ollama; severa advertencia contra modelos reducidos para evitar truncamientos y fallos en el análisis.

---

## 3. 📦 Actualización de dependencias y eliminación de advertencias en Java 11

1. **Actualización de `dom4j` a `2.1.4`**:
   - Reemplaza la biblioteca histórica `dom4j:1.6.1` por `org.dom4j:dom4j:2.1.4`.
   - Elimina la advertencia `WARNING: An illegal reflective access operation has occurred` en Tomcat 9 y Java 11.

---

## 4. 🎨 Modernización de interfaz, Accesibilidad de fuentes y Temas

1. **Escalado de fuentes en 4 fases**:
   - Soporta 100% (Estándar), 115% (Cómodo), 130% (Claro) y **Modo Gigante A+++ (145%)** con protección contra parpadeo (Anti-FOUC).
2. **Selector de temas de 3 estados**:
   - Alternancia ágil entre Auto (Sistema), Claro y Oscuro con un solo clic.
3. **Barra de búsqueda unificada y navegación inteligente**:
   - Botón de búsqueda integrado, salto directo por RefId (ej. `PROJ-123`) y búsqueda global para administradores.
4. **Optimización móvil (RWD)**:
   - Menú lateral deslizable (Drawer), panel Bottom-Sheet para historial y paginación en cápsula centrada.

---

## 5. 🛡️ Fortalecimiento de seguridad en producción y protecciones

1. **Filtro global de cabeceras de seguridad**: Inyección de `X-Frame-Options`, `X-Content-Type-Options`, `Strict-Transport-Security` y `Content-Security-Policy`.
2. **Bloqueo para motores de búsqueda (`robots.txt`)**: Protección de tickets internos frente a indexación web.
3. **Protección contra manipulaciones**: Advertencia de rol invitado, lista blanca de parámetros y prevención de doble envío de formularios.

---

## 6. ⚙️ Configuración del sistema y mejoras de estabilidad

1. Supresión de registros debug de Wicket mediante `status.nullValid = ` en todos los idiomas.
2. Modernización de controles booleanos a `IndicatingDropDownChoice`.
3. Registro explícito de controladores JDBC para conexiones de base de datos.
4. Detección automática de codificación UTF-8 para adjuntos de texto y rutas de logotipo relativas.
5. **Guía en scripts de Docker Build y prevención de conflictos de Tag en Git**:
   - Se añadieron mensajes informativos al iniciar `docker/build.bat` y `docker/build.sh` con instrucciones de sincronización rápida (`git fetch --tags -f && git reset --hard origin/master`) ante sobrescrituras de etiquetas o modificaciones locales.
   - Actualización de la documentación en `docker/` en los 8 idiomas estándar con sección de resolución de problemas.
7. **Rebranding a JTrac NG y salto a versionado semántico (v3.0.0-beta)**:
   - Cambio oficial de identidad del proyecto a **JTrac NG** (Next Generation) y salto de versión a **3.0.0-beta**, eliminando colisiones de búsqueda de hace 15 años con el antiguo JTrac 2.1.0/2.3.x y maximizando el SEO en Google.
   - Migración completa del repositorio oficial a [https://github.com/kafeiou/jtrac-ng](https://github.com/kafeiou/jtrac-ng), sincronizando el pie de página, la barra de navegación móvil y el descriptor Maven POM.
8. **Modernización de la búsqueda Lucene y vista del historial (Sub-tokens, comodines y visualización de historial por defecto)**:
   - **Filtro de subtokens (`SubTokenFilter`)**: Divide direcciones de correo (ej. `user@gmail.com`) y nombres de archivo compuestos (ej. `thunderbird_gmail.pdf`) en subtokens (`user`, `gmail`, `com`, `thunderbird`, `pdf`), permitiendo que búsquedas de `gmail` encuentren correos y adjuntos.
   - **Expansión inteligente y comodines iniciales**: Expande términos a `(term OR term*)`, activa `allowLeadingWildcard = true` para `*palabra*` y soporta tolerancia de frases (`phraseSlop = 2`).
   - **Navegación limpia de espacios y expansión de historial por búsqueda (Clean Space Browsing & Search-Driven History Expansion)**: La navegación de espacios mantiene por defecto `showHistory = false` para mostrar listas limpias (cada ticket aparece una vez como `EFC-109`), eliminando la sobrecarga visual de revisiones no buscadas. La vista de historial (`showHistory = true`) se activa dinámicamente al buscar palabras clave o en el formulario de búsqueda avanzada, y se contrae automáticamente al limpiar la búsqueda.
   - **Orden cronológico del historial (Ticket principal antes de las revisiones)**: Se corrigió el orden con historial activo para ordenar por número de ticket descendente y ID de historial ascendente (`parent.id DESC, id ASC`). Esto garantiza que el ticket principal (ej. `xxx-109`) siempre preceda a sus comentarios de revisión (ej. `xxx-109(1)`), manteniendo agrupadas las revisiones al ordenar por otras columnas.
   - **Filtrado inteligente de historial en búsqueda por palabras clave (Smart History Filtering)**: Cuando el historial está activado y se realiza una búsqueda, el sistema filtra inteligentemente los resultados para mostrar únicamente el registro principal o los comentarios de revisión que realmente coinciden con la palabra clave, ocultando cambios de estado irrelevantes.
   - **Reindexación automática en segundo plano al iniciar**: Detecta la actualización del analizador (`lucene.analyzer.version = 3.0.0-subtoken-v1`) y reindexa de forma asíncrona.
   - **Migración a Docker Hub**: Documentación en 8 idiomas actualizada a `kafeiou/jtrac-ng:latest`.

---

## 7. Guía de actualización y compatibilidad

- **Base de datos**: Totalmente compatible con la versión 2.3.3-2.0.0; **no requiere scripts de migración**.
- **Despliegue**: Sustituya el archivo `ROOT.war` existente en el servidor por `target/jtrac.war`.
- **Documentación relacionada**:
  - [Guía práctica de consultas por correo y Prompts](../llm/PROMPT_EXAMPLES_es.md)
  - [Notas de la versión anterior (2.3.3-2.0.0)](release-2.3.3-2.0.0_es.md)
