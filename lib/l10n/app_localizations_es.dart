// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Spanish Castilian (`es`).
class AppLocalizationsEs extends AppLocalizations {
  AppLocalizationsEs([String locale = 'es']) : super(locale);

  @override
  String get skip => 'Omitir';

  @override
  String get before => 'ANTES';

  @override
  String get after => 'DESPUÉS';

  @override
  String get picturePerfectTitle => 'Fotos perfectas\nal primer intento';

  @override
  String get picturePerfectSubtitle =>
      'Olvídate de las decenas de fotos fallidas, no importa si estás detrás o delante de la cámara.';

  @override
  String get findInspiration => 'Inspírate con ideas nuevas';

  @override
  String get findInspirationSubtitle =>
      'Elige poses y ángulos de la colección preparada o sube los tuyos. Guarda sus fotos favoritas y planifica fácilmente tus sesiones de fotos.';

  @override
  String get letCameraGuideYou =>
      'Tu fotógrafo personal directo en el teléfono';

  @override
  String get letCameraGuideYouSubtitle =>
      'Elige la foto que quieres recrear. Sigue las instrucciones y alinee las poses. La cámara tomará la foto sola tan pronto como la pose coincida.';

  @override
  String get yourPhotos => 'Tus fotos ';

  @override
  String get stayYours => 'son solo tuyas';

  @override
  String get privacySubtitle =>
      'Todo el procesamiento ocurre en tu dispositivo. No se recopilan ni transmiten datos a terceros. La app funciona perfectamente sin Internet.';

  @override
  String get oneShotAway =>
      'Guía precisa de IA\npara tus momentos más preciosos';

  @override
  String get aiGuidance => 'Es más fácil de lo que parece. ¿Empezamos?';

  @override
  String get chooseReferenceTitle => 'Elige una foto de referencia';

  @override
  String get welcomeTitle => 'Bienvenido a PreciosAI';

  @override
  String get welcomeSubtitle =>
      'Primero, elige una foto con la pose y el ángulo que quieras';

  @override
  String get selectFromAlbumsDesc =>
      'Puedes seleccionar de los álbumes preparados';

  @override
  String get uploadFromGalleryDesc => 'subir desde tu galería';

  @override
  String get useRandomChoiceDesc => 'o utilizar selección aleatoria';

  @override
  String get tapToProceedDesc =>
      'Toca aquí para continuar con la foto seleccionada';

  @override
  String get deleteAlbumTitle => '¿Quieres eliminar este álbum?';

  @override
  String deleteAlbumConfirm(String albumTitle) {
    return '“$albumTitle” será eliminado.';
  }

  @override
  String get cancel => 'Cancelar';

  @override
  String get delete => 'Eliminar';

  @override
  String get editAlbum => 'Editar álbum';

  @override
  String get fullBody => 'Plano general';

  @override
  String get mediumShot => 'Plano medio';

  @override
  String get portrait => 'Retrato';

  @override
  String get degreeOfSimilarity => 'Nivel de coincidencia';

  @override
  String get visualization => 'Visualización';

  @override
  String get degreeOfSimilarityDesc => 'y el nivel de coincidencia';

  @override
  String get visualizationDesc =>
      'Puedes configurar los ajustes de visualización';

  @override
  String get low => 'Bajo';

  @override
  String get medium => 'Medio';

  @override
  String get high => 'Alto';

  @override
  String get numFrames => 'Número de fotos en el resultado';

  @override
  String get modelLoading => 'Cargando modelo\nEspera un momento, por favor';

  @override
  String get niceWork => '¡Genial!';

  @override
  String get poseMatchingHint =>
      'Verás el esquema de la pose deseada y la pose de tu modelo. Coloca al modelo en la zona resaltada y alinea las poses.';

  @override
  String get poseZonesHint =>
      'Los cápsulas coloreados te indicarán con qué precisión coincide la posición de cada parte del cuerpo. Cuando todos los sectores se vuelven verdes, la pose es perfecta.';

  @override
  String get zoomHint =>
      'El zoom se ajusta automáticamente, pero siempre puedes corregirlo manualmente. Actualmente, solo se puede fotografiar a una persona.';

  @override
  String get autoSaveHint =>
      'La foto final se guardará automáticamente en la galería de tu dispositivo en la carpeta PreciosAI';

  @override
  String get noSkeletonHint =>
      '¿No ves la pose de tu modelo? Mueve un poco la cámara';

  @override
  String get mergeSkeletonsHint =>
      'Coloca al modelo en la zona resaltada y alinea las poses';

  @override
  String get moveSlowlyHint =>
      'Mueve la cámara suavemente e indica al modelo qué corregir - inclinación de la cabeza, posición de las manos, rotación del cuerpo';

  @override
  String get newAlbum => 'Nuevo álbum';

  @override
  String get albumName => 'Título del álbum';

  @override
  String get chooseFromAssets => 'Elegir de la colección';

  @override
  String get chooseFromGallery => 'Elegir de la galería';

  @override
  String get basicImages => 'Colección';

  @override
  String get gallery => 'Galería';

  @override
  String get cover => 'Portada';

  @override
  String get chooseFromBasicImages => 'Elige de la colección';

  @override
  String get createNewAlbum => 'Crear un nuevo álbum';

  @override
  String get defaultAlbumName => 'Mi álbum';

  @override
  String get done => 'Vamos';

  @override
  String get next => 'Siguiente';

  @override
  String visualizationType(String visualization) {
    String _temp0 = intl.Intl.selectLogic(visualization, {
      'empty': 'Ninguna',
      'skeleton': 'Pose',
      'capsules': 'Cápsulas',
      'skeletonCapsules': 'Pose+Cápsulas',
      'other': 'Unknown',
    });
    return '$_temp0';
  }
}
