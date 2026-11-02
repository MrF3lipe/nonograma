# 🧩 Nonograma

![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?logo=kotlin&logoColor=white) ![Android](https://img.shields.io/badge/Android-3DDC84?logo=android&logoColor=white) ![ML Kit](https://img.shields.io/badge/ML%20Kit-4285F4?logo=google&logoColor=white)

¿Atascado en un nonograma? Abre la burbuja, deja que lea las pistas y mira la solución flotando encima del juego. Sí, es trampa. Sí, es divertidísimo. 😎

## ✨ Qué trae

- 🫧 Burbuja flotante siempre a mano
- 🪟 Overlay semitransparente que puedes mover y redimensionar
- 🔒 Modo bloqueo: los toques pasan al juego de abajo
- 👀 OCR de las pistas con ML Kit
- 🎨 Soporta nonogramas a color
- ⚡ Solver rapidísimo (line-solving + backtracking)

## 📦 Descarga

Pilla el APK en [Releases](https://github.com/MrF3lipe/nonograma/releases/latest).

## 🆕 1.0.1

- 🧭 La pantalla principal es un tutorial corto con permisos y botón «Iniciar burbuja»
- 📐 Detección automática del tamaño y posición de la rejilla (el cuadro se ajusta solo)
- 🎨 Botón de modo en la barra: Auto / Color / B/N
- 🩺 Errores visibles (mensaje corto) y registro en `Android/data/com.felipe.nonograma/files/errores.log`
- 🔄 Auto-actualización (`updates/version.json`, parche bsdiff o APK completo, verificado con sha256)
