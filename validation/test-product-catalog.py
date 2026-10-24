#!/usr/bin/env python3
"""Release gate: About inventory must match this APK and its verified modules."""
import json
import re
from pathlib import Path

root=Path(__file__).resolve().parents[1]
read=lambda path:(root/path).read_text(encoding="utf-8")
gradle=read("app/build.gradle")
html=read("app/src/main/assets/index.html")
js=read("app/src/main/assets/app.js")
catalog_js=read("app/src/main/assets/nexo-product-catalog.js")
summary=json.loads(read(".github/nexo-update-summary.json"))
match=re.search(r"versionName\s+'([^']+)'",gradle)
assert match,"Missing app version"
version=match.group(1)
assert catalog_js.startswith("/* Catálogo funcional público")
payload=catalog_js.split("window.NexoProductCatalog = ",1)[1].strip()
assert payload.endswith(";")
catalog=json.loads(payload[:-1])
assert catalog["schemaVersion"]==1
assert catalog["versionName"]==version==summary["versionName"],"Update product inventory for every release"
assert isinstance(catalog.get("releaseNotes"),str) and len(catalog["releaseNotes"])>=50
assert catalog["releaseNotes"]==summary["notes"],"Release notes on screen and Updates must match exactly"
assert catalog["developer"]=="Carlos Javier Ruiz Bermúdez"
assert catalog["copyrightYear"]==2026
assert "Desarrollado por Carlos Javier Ruiz Bermúdez" in html
assert "© 2026" in html
assert 'id="brandInfoBtn"' in html and 'data-screen="about"' in html
assert 'id="aboutProductCatalog"' in html and 'id="aboutCatalogTitle"' in html
assert 'id="aboutReleaseNotes"' in html and 'Novedades de esta versión' in html
assert 'Tus datos permanecen en el dispositivo.' not in html
assert 'Gmail y Nexo Familia solo se utilizan' not in html
assert html.index('src="nexo-product-catalog.js"') < html.index('src="app.js"')
assert "function renderProductCatalog()" in js and "renderProductCatalog();" in js
assert "esc(module.title)" in js and "esc(feature)" in js
assert "releaseNotes.textContent=String(catalog.releaseNotes" in js
assert "releaseNotes.innerHTML" not in js
sections=catalog["sections"]
assert len(sections)>=5
modules=[module for group in sections for module in group["modules"]]
ids=[module["id"] for module in modules]
assert len(ids)==len(set(ids))>=26,"Missing or duplicated product modules"
for group in sections:
    assert group["title"] and group["icon"] and group["modules"]
for module in modules:
    assert len(module["title"])>=3 and len(module["description"])>=16
    assert len(module["features"])>=1
    assert all(len(feature)>=12 for feature in module["features"])
# The catalog must represent every active module tile in Tools.
tools=html.split('data-screen="tools"',1)[1].split('data-screen="save"',1)[0]
panels=set(re.findall(r'data-panel="([a-z]+)"',tools))
mapped={
    "notes":"notas","recordings":"grabaciones","lists":"listas",
    "documents":"documentos","journal":"cuaderno","books":"libros",
    "media":"media","quotes":"frases","medications":"medicacion",
    "vehicles":"vehiculos","places":"lugares","parking":"aparcamiento"
}
assert panels==set(mapped),"Update catalog/coverage when Tools modules change: "+str(panels^set(mapped))
assert set(mapped.values()).issubset(ids)
assert set(("nexo-connect","chat","compartir","inbox","familia","calendario","captura","actualizaciones")).issubset(ids)
assert any("extremo a extremo" in " ".join(m["features"]) for m in modules if m["id"]=="chat")
assert any("desvincular" in " ".join(m["features"]) for m in modules if m["id"]=="nexo-connect")
print(f"Catálogo Nexo v{version}: {len(sections)} grupos, {len(modules)} módulos, "+
      f"{sum(len(m['features']) for m in modules)} funciones; autoría y versión OK")
