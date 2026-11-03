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

## 1.0.5
- Reintento automático de la lectura: si las pistas no son válidas (sumas distintas, una línea no cabe, línea vacía con dígitos, sin solución o solución contradictoria), vuelve a leer con otra escala, otro umbral de tinta y otro recorte de celda, y prueba combinaciones de dígitos dudosos (6/8, 3/8, 1/7, 0/8…) de la más a la menos probable hasta que el solucionador valide. Con tope de intentos y de tiempo.
- Si aun así no lo consigue, no muestra pistas inválidas como buenas: marca las líneas dudosas en el editor y lo anota en errores.log.
- Dígitos pegados (`16`) separados, números que se salen de la celda (`20` en la última columna) y columnas con `0` (vacías) reconocidos.
- Autoresolver (▶): con permiso de accesibilidad, toca una a una las casillas rellenas de la solución sobre la cuadrícula detectada; intervalo configurable (ms) en el editor y se detiene en cualquier momento tocando la burbuja (■). Si las pistas no se resuelven, no toca nada.

## 1.0.6
- Captura limpia: solo se leen fotogramas compuestos después de ocultar la superposición (en el teléfono a veces salían los marcos y las asas encima de las pistas, y eso daba pistas inválidas).
- Si las pistas siguen sin ser válidas, repite la captura sola (hasta 3) y deduce líneas ilegibles cuando la solución es única.
- errores.log guarda las pistas tal como se leyeron y el tipo de captura.
- Actualización: reintenta la descarga si github.com no responde y, si el parche falla, baja la APK completa.

## 1.0.7
- Captura sucia descartada: si en el fotograma aún se ven las asas de los marcos (F/C) o del cuadro de solución, no se lee y se espera a otro. Con la superposición encima las pistas de filas salían mal (un `4` pegado al borde del marco se leía `2`, las dos últimas filas quedaban bajo el asa y la 18 se corría).
- errores.log indica cuántos fotogramas sucios se descartaron.

## 1.0.8
- Nonogramas B/N con los números ≥10 resaltados en amarillo también en las columnas (como el juego de las capturas): ya no se toman como "color". Antes salía «Auto·Color» y la validación con colores daba pistas inválidas.
- Celdas grandes (5x5, 10x10) con números pequeños: ahora se encuentran los dígitos.
- Probado con nonogramas generados capturados en emulador a 1080x2400, 720x1600 y 1440x3200.

## 1.0.9
- Auto-acople: al soltar el cuadro o los marcos F/C cerca del nonograma, se detecta la rejilla en la captura y se pegan solos a sus bordes exactos (también al pulsar «Leer»).
- Autoresolver más rápido: botón 1x / 2x / 3x en la barra (hasta ~3 veces más rápido).
- Arreglado: el autoresolver omitía casi siempre el primer bloque (el primer toque se lo tragaba la propia superposición, que aún no había pasado a no táctil).
- Tutorial actualizado.

## 1.0.10
- Grabación de pantalla integrada (⏺/⏹ en la barra): usa la misma captura de la app, así no choca con la grabadora del sistema. Vídeo H.264 MP4 en Galería › Movies/Nonograma (sin audio). Leer pistas sigue funcionando mientras se graba (el vídeo se detiene un instante durante la lectura).

## 1.0.11
- El auto-acople solo ocurre al pulsar «Leer» (ya no al soltar el cuadro o los marcos).
- Barra en varias filas: en 1.0.10 era una sola fila y, con ⏺ y 1x/2x/3x, «Ocultar», la velocidad y ✕ se salían de la pantalla.
- Nuevo ⟲: cuadro y marcos a su posición inicial y sin pistas ni solución.

## 1.0.12
- El selector de velocidad (1x/2x/3x) vuelve a verse: la barra se repartía midiendo el botón de modo como «Auto» y luego pasaba a «Auto·B/N»/«Auto·Color», la primera fila se salía y los últimos botones quedaban recortados. Ahora cada botón se mide con su texto más largo, hay margen y, si algo no cabe, la fila se desplaza en vez de recortar.
