// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Russian (`ru`).
class AppLocalizationsRu extends AppLocalizations {
  AppLocalizationsRu([String locale = 'ru']) : super(locale);

  @override
  String get skip => 'Пропустить';

  @override
  String get before => 'ДО';

  @override
  String get after => 'ПОСЛЕ';

  @override
  String get picturePerfectTitle => 'Идеальные кадры\nс первого дубля';

  @override
  String get picturePerfectSubtitle =>
      'Забудьте о десятках неудачных фото - неважно, снимаете вы или позируете.';

  @override
  String get findInspiration => 'Вдохновляйтесь новыми идеями';

  @override
  String get findInspirationSubtitle =>
      'Выбирайте позы и ракурсы из готовой коллекции или загружайте свои. Сохраняйте понравившиеся кадры и планируйте съёмки без лишних усилий.';

  @override
  String get letCameraGuideYou => 'Ваш личный фотограф\nпрямо в телефоне';

  @override
  String get letCameraGuideYouSubtitle =>
      'Выберите кадр, который хотите повторить. Следуйте подсказкам и совместите позы. Камера сделает снимок сама, как только поза совпадёт.';

  @override
  String get yourPhotos => 'Ваши фото ';

  @override
  String get stayYours => 'остаются только вашими';

  @override
  String get privacySubtitle =>
      'Вся обработка происходит на вашем устройстве. Данные не собираются и не передаются третьим лицам. Приложение отлично работает без интернета.';

  @override
  String get oneShotAway =>
      'Больше никаких \"переснимем\" и \"я не знаю, куда деть руки\"';

  @override
  String get aiGuidance => 'Это проще, чем кажется. Начнём?';

  @override
  String get chooseReferenceTitle => 'Выберите фото-ориентир';

  @override
  String get welcomeTitle => 'Добро пожаловать в PreciosAI';

  @override
  String get welcomeSubtitle =>
      'Для начала выберите фото-ориентир с нужной позой и ракурсом, которые хотели бы повторить';

  @override
  String get selectFromAlbumsDesc =>
      'Вы можете выбрать фото из готовой коллекции';

  @override
  String get uploadFromGalleryDesc => 'загрузить из своей галереи';

  @override
  String get useRandomChoiceDesc =>
      'или позволить приложению выбрать его случайно';

  @override
  String get tapToProceedDesc =>
      'Нажмите здесь, чтобы продолжить с выбранным фото';

  @override
  String get deleteAlbumTitle => 'Удалить этот альбом?';

  @override
  String deleteAlbumConfirm(String albumTitle) {
    return 'Альбом «$albumTitle» будет удален';
  }

  @override
  String get cancel => 'Отмена';

  @override
  String get delete => 'Удалить';

  @override
  String get editAlbum => 'Изменить альбом';

  @override
  String get fullBody => 'Общий план';

  @override
  String get mediumShot => 'Средний план';

  @override
  String get portrait => 'Портрет';

  @override
  String get degreeOfSimilarity => 'Степень сходства';

  @override
  String get visualization => 'Визуализация';

  @override
  String get degreeOfSimilarityDesc => 'и степень сходства';

  @override
  String get visualizationDesc =>
      'Здесь можно настроить параметры визуализации';

  @override
  String get low => 'Низкая';

  @override
  String get medium => 'Средняя';

  @override
  String get high => 'Высокая';

  @override
  String get numFrames => 'Количество кадров';

  @override
  String get modelLoading => 'Загрузка модели\nПожалуйста, подождите...';

  @override
  String get niceWork => 'Отличная работа!';

  @override
  String get poseMatchingHint =>
      'Вы увидите схему желаемой позы и позы вашей модели в кадре. Разместите модель в мигающей области и совместите позы.';

  @override
  String get poseZonesHint =>
      'Цветные капсулы подскажут, насколько точно совпадает положение каждой части тела. В момент, когда все секторы станут зелёными, поза идеальна.';

  @override
  String get zoomHint =>
      'Зум настраивается автоматически, но его всегда можно подправить вручную. Пока поддерживается съёмка только одного человека.';

  @override
  String get autoSaveHint =>
      'Готовое фото автоматически сохранится в галерее вашего устройства в папке PreciosAI';

  @override
  String get noSkeletonHint =>
      'Не видите позу вашей модели? Немного подвигайте камеру';

  @override
  String get mergeSkeletonsHint =>
      'Поместите модель в мигающую зону и совместите позы';

  @override
  String get moveSlowlyHint =>
      'Плавно двигайте камеру и подсказывайте модели, что подправить - наклон головы, положение рук, разворот корпуса';

  @override
  String get newAlbum => 'Новый альбом';

  @override
  String get albumName => 'Название альбома';

  @override
  String get chooseFromAssets => 'Выбрать из коллекции';

  @override
  String get chooseFromGallery => 'Выбрать из галереи';

  @override
  String get basicImages => 'Коллекция';

  @override
  String get gallery => 'Галерея';

  @override
  String get cover => 'Обложка';

  @override
  String get chooseFromBasicImages => 'Выберите из коллекции';

  @override
  String get createNewAlbum => 'Создать альбом';

  @override
  String get defaultAlbumName => 'Мой альбом';

  @override
  String get done => 'Начать';

  @override
  String get next => 'Далее';

  @override
  String visualizationType(String visualization) {
    String _temp0 = intl.Intl.selectLogic(visualization, {
      'empty': 'Нет',
      'skeleton': 'Поза',
      'capsules': 'Капсулы',
      'skeletonCapsules': 'Поза+Капсулы',
      'other': 'Unknown',
    });
    return '$_temp0';
  }

  @override
  String get about => 'Инфо';

  @override
  String get privacyPolicy => 'Политика\nконфиденциальности';

  @override
  String get appGuide => 'Руководство';

  @override
  String get infoText =>
      'Умная ИИ-камера, которая поможет запечатлеть идеальную позу и сохранить ценные моменты.';

  @override
  String get openSourceLicenses => 'Лицензии открытого ПО';
}
