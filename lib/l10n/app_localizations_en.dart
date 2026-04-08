// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for English (`en`).
class AppLocalizationsEn extends AppLocalizations {
  AppLocalizationsEn([String locale = 'en']) : super(locale);

  @override
  String get skip => 'Skip intro';

  @override
  String get before => 'BEFORE';

  @override
  String get after => 'AFTER';

  @override
  String get picturePerfectTitle => 'Picture-perfect\nposes, every time';

  @override
  String get picturePerfectSubtitle =>
      'Say goodbye to awkward photos.\nWhether you’re behind the camera or in front of it, we’ll help you capture the perfect shot.';

  @override
  String get findInspiration => 'Find Your\nInspiration.';

  @override
  String get findInspirationSubtitle =>
      'Choose from curated poses or upload your own photo to recreate the look. Save ideas and plan your next shoot effortlessly.';

  @override
  String get letCameraGuideYou => 'Let Your Camera\nGuide You';

  @override
  String get letCameraGuideYouSubtitle =>
      'Our smart overlay detects your subject’s pose.\nFollow the on-screen hints to adjust your pose and angle — the photo is captured automatically once everything aligns perfectly.';

  @override
  String get yourPhotos => 'Your Photos ';

  @override
  String get stayYours => 'Stay Yours';

  @override
  String get privacySubtitle =>
      'We believe in complete privacy.\nAll pose processing happens directly on your device, no data is ever collected or sent to the cloud.\nIt even works perfectly offline.';

  @override
  String get oneShotAway => 'Your perfect pose\nis one shot away';

  @override
  String get aiGuidance =>
      'Precise AI guidance for your\nmost precious moments';

  @override
  String get chooseReferenceTitle => 'Let\'s choose a reference photo';

  @override
  String get welcomeTitle => 'Welcome to PreciosAI';

  @override
  String get welcomeSubtitle =>
      'First, you need to choose a photo with preferred pose and angle';

  @override
  String get selectFromAlbumsDesc => 'You can select from prepared albums';

  @override
  String get uploadFromGalleryDesc => 'upload from your gallery';

  @override
  String get useRandomChoiceDesc => 'or use random choice';

  @override
  String get tapToProceedDesc => 'Tap here to proceed with the selected photo';

  @override
  String get deleteAlbumTitle => 'Do you want delete this album?';

  @override
  String deleteAlbumConfirm(String albumTitle) {
    return '“$albumTitle” will be deleted.';
  }

  @override
  String get cancel => 'Cancel';

  @override
  String get delete => 'Delete';

  @override
  String get editAlbum => 'Edit album';

  @override
  String get fullBody => 'Full body';

  @override
  String get mediumShot => 'Medium shot';

  @override
  String get portrait => 'Portrait';

  @override
  String get degreeOfSimilarity => 'Degree of similarity';

  @override
  String get visualization => 'Visualization';

  @override
  String get degreeOfSimilarityDesc => 'and the degree of similarity';

  @override
  String get visualizationDesc =>
      'Here you can configure the visualization settings';

  @override
  String get low => 'Low';

  @override
  String get medium => 'Medium';

  @override
  String get high => 'High';

  @override
  String get numFrames => 'Number of resulting frames';

  @override
  String get modelLoading => 'Model loading\nWait a moment, please';

  @override
  String get niceWork => 'Nice work!';

  @override
  String get poseMatchingHint =>
      'You will see schematic skeleton of the reference photo and a skeleton that matches your model.\n Place the model in the highlighted sector and merge the skeletons.';

  @override
  String get poseZonesHint =>
      'Auxiliary sectors display the correspondence of body parts to the desired pose. For a perfect shot, each of them should be green.';

  @override
  String get zoomHint =>
      'The zoom will be adjusted automatically, but you can change it yourself if necessary. Currently, only one person can be photographed.';

  @override
  String get autoSaveHint =>
      'The resulting photo will be saved automatically in your gallery in the PreciosAI application folder';

  @override
  String get noSkeletonHint =>
      'If you don\'t see skeleton that matches your model, move the camera a little';

  @override
  String get mergeSkeletonsHint =>
      'Place the model in the highlighted sector and merge the skeletons';

  @override
  String get moveSlowlyHint =>
      'Move the camera slowly and give your model cues';

  @override
  String get newAlbum => 'New Album';

  @override
  String get albumName => 'Album name';

  @override
  String get chooseFromAssets => 'Choose from Assets';

  @override
  String get chooseFromGallery => 'Choose from Gallery';

  @override
  String get basicImages => 'Basic images';

  @override
  String get gallery => 'Gallery';

  @override
  String get cover => 'Cover';

  @override
  String get chooseFromBasicImages => 'Choose from basic reference images';

  @override
  String get createNewAlbum => 'Create new album';

  @override
  String get defaultAlbumName => 'My Album';

  @override
  String get done => 'Done';

  @override
  String get next => 'Next';

  @override
  String visualizationType(String visualization) {
    String _temp0 = intl.Intl.selectLogic(visualization, {
      'empty': 'Empty',
      'skeleton': 'Skeleton',
      'capsules': 'Capsules',
      'skeletonCapsules': 'Skeleton+Capsules',
      'other': 'Unknown',
    });
    return '$_temp0';
  }
}
