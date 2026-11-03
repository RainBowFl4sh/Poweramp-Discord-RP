# Registro de cambios

## 1.2.1

### Mejoras
- **Servicio en segundo plano**: solo se inicia cuando de verdad suena una pista, no ya al abrir Poweramp, y se
  detiene solo cuando ya no suena nada (tras el límite de pausa)
- **Subida de portadas**: nombres de archivo aleatorios en lugar de «cover.jpg»: x0.at había empezado a rechazar las
  subidas

## 1.2.0

### Novedades
- **Vincula tu cuenta de Discord** (pestaña *Estado*): tu nombre real y tu foto de perfil en la vista previa en vivo y en
  la pestaña *Estado*; solo se guarda el perfil público
- **26 idiomas más**: francés, español, portugués (Brasil), italiano, neerlandés, danés, sueco, noruego, finés,
  polaco, checo, húngaro, rumano, griego, turco, serbio, árabe, hindi, indonesio, vietnamita, tailandés, chino,
  japonés y coreano (traducciones con IA; las correcciones son bienvenidas)
- **Selector de idioma** en *Ajustes de la app* (en Android 13+ también en los ajustes de aplicaciones de Android)

### Mejoras
- Menor consumo de batería: «En pausa» desaparece a tiempo aunque el móvil esté en reposo; sin activaciones mientras la presencia
  está desactivada o falta Discord; las reconexiones se espacian
- Menor uso de red y memoria: las portadas se vuelven a comprobar con menos frecuencia, los álbumes desconocidos no se vuelven a
  buscar durante 12 h, las imágenes se liberan cuando la app está en segundo plano
- Correcciones: los ajustes importados se validan y corrigen; reproducir vuelve a mostrar la presencia tras detenerla desde el
  administrador de tareas

## 1.1.0

### Novedades
- **Ajustes de la app** en el menú de tres puntos: tema *Según el sistema* / *Claro* / *Oscuro*, **negro AMOLED** para el
  modo oscuro y colores **Material You** (Android 12+)
- **Acerca de**: la foto de GitHub del autor y enlaces centrados
- **Textos rotativos**: `%artist% || %title%` muestra el artista y el título por turnos (*Intervalo de rotación* en la
  pestaña *Visualización*, 5–60 s)
- **Nuevo icono de la app** al estilo del de Poweramp: un bocadillo con barras de ecualizador (también como icono
  temático de Android 13+); el logotipo de la app, la pantalla de inicio y las imágenes de Discord van a juego

### Más fluidez
- Builds optimizadas (R8 + perfil de referencia): las pantallas y los cambios de pestaña ya no dan tirones
- Los ajustes, el estado de Poweramp y las imágenes se cargan en segundo plano
- La vista previa en vivo solo actualiza la posición de reproducción cada segundo; las pestañas lejanas se abren al
  instante; el registro se desplaza con fluidez

## 1.0.0

### Novedades
- Primera versión: **Discord Rich Presence para Poweramp**, funciona con las builds estables y beta de Poweramp
- **«Escuchando Poweramp»** con barra de progreso (o «Jugando» con el tiempo transcurrido)
- **Portadas sin servicio de streaming**: la portada que muestra Poweramp se sube a x0.at, catbox.moe o Imgur,
  además de búsquedas en línea opcionales (Spotify, Deezer, Apple Music / iTunes, Bandcamp, Discogs,
  MusicBrainz + Cover Art Archive)
- Plantillas de texto con marcadores (`%title%`, `%artist%`, `%album%`, `%bar%` …) y una vista previa en vivo de lo que
  muestra Discord
- Comportamiento en pausa (mostrar «En pausa» u ocultar, quitar tras una pausa) y filtros para streams y carpetas
- Búsqueda de actualizaciones (versiones de GitHub)
- Inglés, alemán, ruso y ucraniano
