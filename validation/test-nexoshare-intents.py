from pathlib import Path
import re
import xml.etree.ElementTree as ET

root=Path(__file__).resolve().parents[1]
manifest=root/'app/src/main/AndroidManifest.xml'
main=root/'app/src/main/java/com/jruiz/nexo/MainActivity.java'
exchange=root/'app/src/main/java/com/jruiz/nexo/exchange/NexoExchangeManager.java'

ET.parse(manifest)
xml=manifest.read_text(encoding='utf-8')
java=main.read_text(encoding='utf-8')
ex=exchange.read_text(encoding='utf-8')

checks={
    'MIME propio SEND': re.search(r'<intent-filter[^>]*>\s*<action android:name="android.intent.action.SEND".*?<data android:mimeType="application/x-nexo-share"', xml, re.S) is not None,
    'MIME propio VIEW': re.search(r'<intent-filter[^>]*>\s*<action android:name="android.intent.action.VIEW".*?<data android:mimeType="application/x-nexo-share"', xml, re.S) is not None,
    'octet-stream SEND': re.search(r'<intent-filter[^>]*>\s*<action android:name="android.intent.action.SEND".*?<data android:mimeType="application/octet-stream"', xml, re.S) is not None,
    'octet-stream VIEW': re.search(r'<intent-filter[^>]*>\s*<action android:name="android.intent.action.VIEW".*?<data android:mimeType="application/octet-stream"', xml, re.S) is not None,
    'detección genérica': '"application/octet-stream".equals(m)' in java,
    'lectura segura Nexo': 'payload = NexoExchangeManager.read(this, uri);' in java,
    'validación de formato': 'if(!"nexo-share".equals(root.optString("format")))' in ex,
    'validación de versión': 'if(version<1 || version>FORMAT_VERSION)' in ex,
    'límite de tamaño': 'MAX_IMPORT_BYTES = 512 * 1024' in ex,
    'extensión de exportación': '".nexoshare"' in ex,
}
missing=[name for name,ok in checks.items() if not ok]
if missing:
    raise SystemExit('Fallos de recepción .nexoshare: '+', '.join(missing))
print(f'Recepción .nexoshare OK: {len(checks)} comprobaciones')
