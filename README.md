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

## 🆕 1.0.2

- 🔢 Lectura de pistas con segmentación propia: dígitos pegados (`132123` → 1 3 2 1 2 3), números destacados en color (`10` amarillo) y columnas apiladas en texto claro sobre fondo oscuro
- 📐 Detección de rejilla más robusta (bordes que se extienden sobre la zona de pistas)

## 🆕 1.0.3

- 🩹 Ninguna línea queda vacía si hay dígitos: reintento de OCR por línea con otro preprocesado y, como último recurso, reconocimiento por parecido con los dígitos ya leídos de la misma captura
- 🟡 Números destacados (`11`, `10` en amarillo) detectados aunque la fila tenga mayoría de color
- 📐 Rejilla detectada también en capturas de baja resolución

## 1.0.4
- Reconocedor de dígitos propio (sin ML Kit como principal): plantillas de fuentes libres + glifos reales, agujeros, perfiles y consistencia dentro de la captura. ML Kit queda como voto secundario.
- Corrige confusiones 6/8 y 3/8 y filas que quedaban vacías.
- Reparación automática: si las pistas no dan un puzzle resoluble, prueba las lecturas alternativas más dudosas.
- Detección de rejilla más robusta en capturas reescaladas o con JPEG fuerte.
