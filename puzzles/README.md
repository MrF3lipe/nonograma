# Puzzles de prueba

Los nonogramas de [webpbn.com](https://webpbn.com/) y de la
[encuesta de solvers de Jan Wolter](https://webpbn.com/survey/puzzles/)
conservan el copyright de sus autores. Se pueden descargar para uso
personal, pero **no se redistribuyen** salvo los que el propio archivo
de la encuesta marca como de redistribución libre.

## Fixtures en el repo

`app/src/test/resources/puzzles/` incluye:

- Puzzles de redistribución libre de la encuesta (Jan Wolter y otros,
  con su atribución original): desde el 5×10 *Dancer* hasta el 55×60
  *Merka*, el 25×25 difícil *Forever*, y dos a color (*Map*, *Onions*).
- Dos sintéticos de dominio público (`synthetic/`): una pista vacía (`0`)
  y un 2×2 con dos soluciones.

No se incluyen *Faase* (80×95) ni *Knotty* (40×40), aunque también son de
redistribución libre: son casos límite que el arnés debe bajar aparte.

## Descargar el lote completo

```bash
scripts/download-puzzles.sh
```

Deja la encuesta (Simpson `.non` y Wolter `.xml`, incl. color) en
`puzzles/downloaded/` (está en `.gitignore`). El script no vuelve a
bajar el archivo si ya está.

Para exportar un id suelto de webpbn:

```bash
scripts/download-puzzles.sh --id 8098
```

## Correr el arnés

```bash
scripts/run-puzzles.sh                      # fixtures del repo
scripts/run-puzzles.sh puzzles/downloaded   # lote completo
scripts/run-puzzles.sh --timeout 40000 puzzles/downloaded
```

Hace falta JDK 17+ y `kotlinc` en el PATH (o en `/home/box/tools/kotlinc`).
