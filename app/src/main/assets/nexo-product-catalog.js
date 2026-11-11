/* Catálogo funcional público, versionado y validado en cada Release. */
window.NexoProductCatalog = {
  "schemaVersion": 1,
  "versionName": "3.7.88",
  "releaseNotes": "Los avisos de medicación añaden «Posponer 10 min» junto a «Tomado» y «Omitir». Al posponer, el aviso vuelve a sonar diez minutos después, sin cambiar el horario de las siguientes tomas. Actualización opcional.",
  "developer": "Carlos Javier Ruiz Bermúdez",
  "copyrightYear": 2026,
  "sections": [
    {
      "title": "Planificación y acceso rápido",
      "icon": "calendar",
      "modules": [
        {
          "id": "inicio",
          "title": "Inicio y agenda de hoy",
          "description": "Un vistazo a las próximas citas y compromisos.",
          "features": [
            "Panel de hoy, mañana y próximos siete días",
            "Acceso a agenda del día y próximos eventos"
          ]
        },
        {
          "id": "calendario",
          "title": "Calendario y agenda",
          "description": "Planifica lo importante sin dispersarlo entre aplicaciones.",
          "features": [
            "Vistas mensual y cronológica con citas, tareas y eventos",
            "Horas, ubicaciones, categorías, personas, recurrencias y recordatorios",
            "Festivos y acceso a Google Maps desde los eventos",
            "Calendario mensual visible y agenda horaria con desplazamiento independiente, regreso sin saltos",
            "Mantener pulsada una franja horaria libre prepara una cita; las citas existentes pueden arrastrarse"
          ]
        },
        {
          "id": "captura",
          "title": "Captura rápida y voz",
          "description": "Introduce información y revísala antes de guardarla.",
          "features": [
            "Escritura y dictado para crear citas y notas",
            "Interpretación de fecha, categoría y avisos con confirmación"
          ]
        },
        {
          "id": "busqueda",
          "title": "Búsqueda e historial",
          "description": "Encuentra registros y consulta actividad pasada.",
          "features": [
            "Búsquedas desde Inicio o búsqueda global",
            "Filtros y opción para incluir resultados históricos"
          ]
        },
        {
          "id": "tiempo",
          "title": "Tiempo y ubicación",
          "description": "Consulta las condiciones meteorológicas de tu zona.",
          "features": [
            "Tiempo desde la cabecera",
            "Ubicación del dispositivo o localidad elegida en Ajustes"
          ]
        }
      ]
    },
    {
      "title": "Notas, documentos y creación",
      "icon": "note",
      "modules": [
        {
          "id": "notas",
          "title": "Notas",
          "description": "Guarda ideas, anotaciones y referencias.",
          "features": [
            "Creación, lectura y edición de notas",
            "Compartir desde la propia nota"
          ]
        },
        {
          "id": "listas",
          "title": "Listas y compra",
          "description": "Organiza lo que necesitas y reutiliza tus listas.",
          "features": [
            "Listas de compra y otras listas personalizadas",
            "Añadir, marcar y gestionar elementos",
            "Compartir listas compatibles"
          ]
        },
        {
          "id": "documentos",
          "title": "Documentos",
          "description": "Centraliza documentos y sus referencias.",
          "features": [
            "Fichas documentales con archivos adjuntos",
            "Fechas y avisos de caducidad cuando se configuran"
          ]
        },
        {
          "id": "cuaderno",
          "title": "Cuaderno",
          "description": "Escribe reflexiones fuera de la agenda.",
          "features": [
            "Entradas personales y edición",
            "Compartir entradas desde sus fichas"
          ]
        },
        {
          "id": "frases",
          "title": "Frases",
          "description": "Colecciona citas y pensamientos.",
          "features": [
            "Frases con autor y referencias",
            "Consulta de una frase guardada desde el calendario"
          ]
        },
        {
          "id": "grabaciones",
          "title": "Grabadora y transcripción",
          "description": "Conserva reuniones y conversaciones autorizadas.",
          "features": [
            "Grabación y reproducción de audio con controles Reproducir, Pausar, Continuar y Detener",
            "Exportación de archivos y transcripciones",
            "Transcripción local con modelos descargables según configuración"
          ]
        }
      ]
    },
    {
      "title": "Bibliotecas, salud y movilidad",
      "icon": "grid",
      "modules": [
        {
          "id": "libros",
          "title": "Libros",
          "description": "Tu biblioteca personal.",
          "features": [
            "Libros pendientes, en lectura y finalizados",
            "Fichas, notas y sinopsis"
          ]
        },
        {
          "id": "media",
          "title": "Películas y series",
          "description": "Tu filmoteca personal.",
          "features": [
            "Títulos pendientes, en curso y vistos",
            "Sinopsis y organización por estado"
          ]
        },
        {
          "id": "medicacion",
          "title": "Medicación",
          "description": "Organiza las pautas prescritas que tú introduzcas.",
          "features": [
            "Medicamentos, dosis, horarios y duración",
            "Avisos configurables por voz e historial de tomas",
            "Posponer diez minutos los avisos sin alterar las siguientes tomas",
            "No prescribe ni sustituye el consejo médico"
          ]
        },
        {
          "id": "vehiculos",
          "title": "Mis vehículos",
          "description": "Mantenimiento para uno o varios coches.",
          "features": [
            "Operaciones habituales y personalizadas",
            "Kilometraje, historial, revisiones y facturas",
            "Control de próximos mantenimientos por fecha o kilómetros"
          ]
        },
        {
          "id": "lugares",
          "title": "Lugares",
          "description": "Guarda ubicaciones útiles.",
          "features": [
            "Fichas de lugares, direcciones y referencias",
            "Acceso a navegación con Google Maps"
          ]
        },
        {
          "id": "aparcamiento",
          "title": "Aparcamiento",
          "description": "Recuerda dónde dejaste el coche.",
          "features": [
            "Guardar y consultar la ubicación del aparcamiento"
          ]
        }
      ]
    },
    {
      "title": "Nexo Connect y contenido recibido",
      "icon": "shield",
      "modules": [
        {
          "id": "nexo-connect",
          "title": "Nexo Connect: identidad y personas",
          "description": "Conecta tu Nexo con personas de confianza.",
          "features": [
            "Perfil con nombre y fotografía",
            "Invitaciones QR o a distancia y verificación del código de seguridad",
            "Gestión de personas autorizadas: editar, verificar y desvincular"
          ]
        },
        {
          "id": "chat",
          "title": "Chat privado",
          "description": "Mensajes con contactos vinculados.",
          "features": [
            "Conversaciones entre personas verificadas con cifrado extremo a extremo",
            "Indicadores de mensajes sin leer y avisos de recepción cuando se autorizan",
            "Gestión del historial de conversación en el dispositivo"
          ]
        },
        {
          "id": "compartir",
          "title": "Compartir fichas cifradas",
          "description": "Intercambia información desde la ficha que estás viendo.",
          "features": [
            "Envío de fichas y notas compatibles a contactos verificados",
            "Revisión y aceptación de contenido recibido antes de incorporarlo a Nexo",
            "Las opciones de compartir por Android continúan disponibles"
          ]
        },
        {
          "id": "inbox",
          "title": "Inbox y capturas externas",
          "description": "Revisa información llegada desde otras aplicaciones.",
          "features": [
            "Bandeja de capturas para revisar, clasificar y guardar",
            "Fuentes opcionales: SMS, Gmail y Calendar Android",
            "Captura de notificaciones de aplicaciones compatibles, como WhatsApp y Telegram, si concedes permiso",
            "Escaneo manual o automático según Ajustes"
          ]
        },
        {
          "id": "familia",
          "title": "Nexo Familia",
          "description": "Conexión opcional con información familiar.",
          "features": [
            "Conectar y desconectar la sincronización familiar",
            "Gestionar datos familiares desde las funciones compatibles"
          ]
        }
      ]
    },
    {
      "title": "Configuración, privacidad y mantenimiento",
      "icon": "settings",
      "modules": [
        {
          "id": "historial",
          "title": "Historial",
          "description": "Consulta la actividad reciente.",
          "features": [
            "Revisión de operaciones y registros de actividad disponibles"
          ]
        },
        {
          "id": "copias",
          "title": "Copias de seguridad",
          "description": "Protege y traslada tus datos.",
          "features": [
            "Exportar y restaurar información y adjuntos compatibles",
            "Gestión del audio conforme a las opciones de copia disponibles"
          ]
        },
        {
          "id": "ajustes",
          "title": "Ajustes y personalización",
          "description": "Decide cómo funciona Nexo en tu teléfono.",
          "features": [
            "Pantalla inicial, tamaño de visualización y personas",
            "Ubicación del tiempo, entrada de datos y calidad de transcripción",
            "Permisos y opciones de avisos",
            "Temas Vivo, Mediterráneo y Noche seleccionables en Ajustes → Apariencia, con preferencia local"
          ]
        },
        {
          "id": "actualizaciones",
          "title": "Actualizaciones verificadas",
          "description": "Mantén Nexo al día.",
          "features": [
            "Comprobación de la última versión disponible",
            "Instalación de APK oficiales firmadas con confirmación de Android",
            "Información de versión, compilación y diagnóstico técnico",
            "Compartir enlace público a la última versión sin transferir datos personales ni establecer conexiones"
          ]
        }
      ]
    }
  ]
};
