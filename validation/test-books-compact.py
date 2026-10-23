from pathlib import Path

root = Path(__file__).resolve().parents[1]
books = (root / "app/src/main/assets/books.js").read_text()
html = (root / "app/src/main/assets/index.html").read_text()
css = (root / "app/src/main/assets/styles.css").read_text()
repo = (root / "app/src/main/java/com/jruiz/nexo/data/BookRepository.java").read_text()
card = books.split("  function card(b) {", 1)[1].split("  function refreshMatches()", 1)[0]
for forbidden in ("book-card-actions", "book-excerpt", "data-book-edit", "data-book-start", "data-book-delete"):
    assert forbidden not in card, f"Acción o resumen en la tarjeta compacta: {forbidden}"
for expected in ("data-book-open", "book-card-symbol--", "book-card-copy", "book-card-title", "book-card-percent"):
    assert expected in card, f"Falta contenido compacto: {expected}"
for state in ("want", "reading", "finished", "abandoned"):
    assert f"'{state}'" in books or f"{state}:" in books, state
    assert f'id="nx-icon-book-{state if state != "finished" else "finished"}"' in html, state
    assert f".book-card-symbol--{state}" in css, state
    assert f'"{state}".equals(status)' in repo, state
for needed in ("data-books-filter", "'all'", "function openDetails(id)", "bookDetailEdit", "bookDetailDelete",
               "bookDetailStart", "bookStatus", "bookReadingFields", "hadReadData", "confirmGeneric(",
               "normal(b.title).includes(q)", "normal(b.author).includes(q)"):
    assert needed in books, f"Falta lógica de biblioteca: {needed}"
for needed in (".books-status-filters", ".book-card-main", ".book-detail-summary", ".book-detail-actions"):
    assert needed in css, f"Falta estilo de biblioteca: {needed}"
assert "3.7.59" in (root / "app/build.gradle").read_text()
print("OK: Libros: cuatro categorías e iconos, filtros, tarjetas compactas y ficha detallada")
