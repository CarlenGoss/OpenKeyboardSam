# OpenKeyboardSam

Teclado **Open Source** inspirado en el teclado oficial de Samsung (**One UI / Galaxy S22 Ultra**), diseñado desde cero para **reducir drásticamente el consumo de RAM**, **disparar la velocidad de respuesta** y mantener las **integraciones clave del ecosistema Samsung** sin necesidad de root.

---

## Características principales

- **Ultra-bajo consumo de RAM (`< 45–65 MB`):**
  - Renderizado directo en `Canvas` (`OneUiKeyboardView`) sin jerarquías pesadas de vistas ni asignaciones en `onDraw()`.
  - Motor de predicción y autocorrección en **C++17 + JNI (`:core-nlp`)** con diccionarios mapeados en disco mediante `mmap`.
  - Carga perezosa (*lazy inflation*) de paneles secundarios (Emoji, Portapapeles, Gemini IA) y liberación inmediata en `onFinishInputView()` / `onTrimMemory()`.
- **Estética One UI (Galaxy S22 Ultra):**
  - Temas **One UI Claro**, **One UI AMOLED Oscuro**, **Automático** y **Dynamic Color (Material You)**.
  - Fila numérica opcional, caracteres alternativos en teclas, pop-up de pulsación y háptica afinada para motor lineal (`VibrationEffect.EFFECT_TICK`).
- **Idiomas iniciales:** Español (con `ñ` y acentos en pulsación larga) e Inglés, con cambio rápido desde la barra superior.
- **Integraciones Samsung y Android:**
  - **Samsung Pass / Samsung Wallet / Bitwarden:** Compatible con **Autofill Inline Suggestions** (Android 11+).
  - **Portapapeles Persistente:** Historial en SQLite ligero con fijado (*pin*) y límite estricto de memoria.
  - **S Pen y Samsung DeX:** Detección de `TOOL_TYPE_STYLUS`, pantallas externas / modo escritorio y teclados físicos (`:feature-stylus`).
  - **Voz a Texto:** Acceso directo desde la barra superior.
  - **Gemini Writing Assist (`:feature-ai`):** Corrección ortográfica/gramatical, cambio de tono (Profesional/Casual) y traducción ES ↔ EN usando tu propia API Key de Gemini (BYOK).

---

## Arquitectura Modular

```text
OpenKeyboardSam/
├── app/                # InputMethodService, SettingsActivity, Autofill Inline
├── core-keyboard/      # Canvas OneUiKeyboardView, Layouts ES/EN, Temas One UI
├── core-nlp/           # Motor C++17 (mmap, predicción, fuzzy, swipe) + JNI
├── feature-clipboard/  # SQLiteOpenHelper persistente de bajo consumo
├── feature-ai/         # Cliente Gemini REST ligero (sin SDK pesado)
└── feature-stylus/     # Detector de S Pen, Samsung DeX y teclados físicos
```

---

## Compilación e Instalación

1. Clona el repositorio:
   ```bash
   git clone https://github.com/CarlenGoss/OpenKeyboardSam.git
   cd OpenKeyboardSam
   ```
2. Compila el APK Debug:
   ```bash
   ./gradlew assembleDebug
   ```
   O descarga el APK compilado automáticamente desde la pestaña **Actions** de GitHub.
3. Instala en tu Galaxy S22 Ultra / Android (`minSdk 26`), abre **OpenKeyboardSam**, actívalo en *Ajustes del sistema* y selecciónalo como teclado predeterminado.

---

## Licencia

Distribuido bajo licencia **Apache-2.0**. Consulta [LICENSE](LICENSE) para más detalles.