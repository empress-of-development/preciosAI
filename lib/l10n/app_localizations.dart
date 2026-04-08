import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:intl/intl.dart' as intl;

import 'app_localizations_de.dart';
import 'app_localizations_en.dart';
import 'app_localizations_es.dart';
import 'app_localizations_pt.dart';
import 'app_localizations_ru.dart';

// ignore_for_file: type=lint

/// Callers can lookup localized strings with an instance of AppLocalizations
/// returned by `AppLocalizations.of(context)`.
///
/// Applications need to include `AppLocalizations.delegate()` in their app's
/// `localizationDelegates` list, and the locales they support in the app's
/// `supportedLocales` list. For example:
///
/// ```dart
/// import 'l10n/app_localizations.dart';
///
/// return MaterialApp(
///   localizationsDelegates: AppLocalizations.localizationsDelegates,
///   supportedLocales: AppLocalizations.supportedLocales,
///   home: MyApplicationHome(),
/// );
/// ```
///
/// ## Update pubspec.yaml
///
/// Please make sure to update your pubspec.yaml to include the following
/// packages:
///
/// ```yaml
/// dependencies:
///   # Internationalization support.
///   flutter_localizations:
///     sdk: flutter
///   intl: any # Use the pinned version from flutter_localizations
///
///   # Rest of dependencies
/// ```
///
/// ## iOS Applications
///
/// iOS applications define key application metadata, including supported
/// locales, in an Info.plist file that is built into the application bundle.
/// To configure the locales supported by your app, you’ll need to edit this
/// file.
///
/// First, open your project’s ios/Runner.xcworkspace Xcode workspace file.
/// Then, in the Project Navigator, open the Info.plist file under the Runner
/// project’s Runner folder.
///
/// Next, select the Information Property List item, select Add Item from the
/// Editor menu, then select Localizations from the pop-up menu.
///
/// Select and expand the newly-created Localizations item then, for each
/// locale your application supports, add a new item and select the locale
/// you wish to add from the pop-up menu in the Value field. This list should
/// be consistent with the languages listed in the AppLocalizations.supportedLocales
/// property.
abstract class AppLocalizations {
  AppLocalizations(String locale)
    : localeName = intl.Intl.canonicalizedLocale(locale.toString());

  final String localeName;

  static AppLocalizations? of(BuildContext context) {
    return Localizations.of<AppLocalizations>(context, AppLocalizations);
  }

  static const LocalizationsDelegate<AppLocalizations> delegate =
      _AppLocalizationsDelegate();

  /// A list of this localizations delegate along with the default localizations
  /// delegates.
  ///
  /// Returns a list of localizations delegates containing this delegate along with
  /// GlobalMaterialLocalizations.delegate, GlobalCupertinoLocalizations.delegate,
  /// and GlobalWidgetsLocalizations.delegate.
  ///
  /// Additional delegates can be added by appending to this list in
  /// MaterialApp. This list does not have to be used at all if a custom list
  /// of delegates is preferred or required.
  static const List<LocalizationsDelegate<dynamic>> localizationsDelegates =
      <LocalizationsDelegate<dynamic>>[
        delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
      ];

  /// A list of this localizations delegate's supported locales.
  static const List<Locale> supportedLocales = <Locale>[
    Locale('de'),
    Locale('en'),
    Locale('es'),
    Locale('pt'),
    Locale('ru'),
  ];

  /// Button to skip the introduction video or screens
  ///
  /// In en, this message translates to:
  /// **'Skip intro'**
  String get skip;

  /// No description provided for @before.
  ///
  /// In en, this message translates to:
  /// **'BEFORE'**
  String get before;

  /// No description provided for @after.
  ///
  /// In en, this message translates to:
  /// **'AFTER'**
  String get after;

  /// No description provided for @picturePerfectTitle.
  ///
  /// In en, this message translates to:
  /// **'Picture-perfect\nposes, every time'**
  String get picturePerfectTitle;

  /// No description provided for @picturePerfectSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Say goodbye to awkward photos.\nWhether you’re behind the camera or in front of it, we’ll help you capture the perfect shot.'**
  String get picturePerfectSubtitle;

  /// No description provided for @findInspiration.
  ///
  /// In en, this message translates to:
  /// **'Find Your\nInspiration.'**
  String get findInspiration;

  /// No description provided for @findInspirationSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Choose from curated poses or upload your own photo to recreate the look. Save ideas and plan your next shoot effortlessly.'**
  String get findInspirationSubtitle;

  /// No description provided for @letCameraGuideYou.
  ///
  /// In en, this message translates to:
  /// **'Let Your Camera\nGuide You'**
  String get letCameraGuideYou;

  /// No description provided for @letCameraGuideYouSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Our smart overlay detects your subject’s pose.\nFollow the on-screen hints to adjust your pose and angle — the photo is captured automatically once everything aligns perfectly.'**
  String get letCameraGuideYouSubtitle;

  /// No description provided for @yourPhotos.
  ///
  /// In en, this message translates to:
  /// **'Your Photos '**
  String get yourPhotos;

  /// No description provided for @stayYours.
  ///
  /// In en, this message translates to:
  /// **'Stay Yours'**
  String get stayYours;

  /// No description provided for @privacySubtitle.
  ///
  /// In en, this message translates to:
  /// **'We believe in complete privacy.\nAll pose processing happens directly on your device, no data is ever collected or sent to the cloud.\nIt even works perfectly offline.'**
  String get privacySubtitle;

  /// No description provided for @oneShotAway.
  ///
  /// In en, this message translates to:
  /// **'Your perfect pose\nis one shot away'**
  String get oneShotAway;

  /// No description provided for @aiGuidance.
  ///
  /// In en, this message translates to:
  /// **'Precise AI guidance for your\nmost precious moments'**
  String get aiGuidance;

  /// No description provided for @chooseReferenceTitle.
  ///
  /// In en, this message translates to:
  /// **'Let\'s choose a reference photo'**
  String get chooseReferenceTitle;

  /// No description provided for @welcomeTitle.
  ///
  /// In en, this message translates to:
  /// **'Welcome to PreciosAI'**
  String get welcomeTitle;

  /// No description provided for @welcomeSubtitle.
  ///
  /// In en, this message translates to:
  /// **'First, you need to choose a photo with preferred pose and angle'**
  String get welcomeSubtitle;

  /// No description provided for @selectFromAlbumsDesc.
  ///
  /// In en, this message translates to:
  /// **'You can select from prepared albums'**
  String get selectFromAlbumsDesc;

  /// No description provided for @uploadFromGalleryDesc.
  ///
  /// In en, this message translates to:
  /// **'upload from your gallery'**
  String get uploadFromGalleryDesc;

  /// No description provided for @useRandomChoiceDesc.
  ///
  /// In en, this message translates to:
  /// **'or use random choice'**
  String get useRandomChoiceDesc;

  /// No description provided for @tapToProceedDesc.
  ///
  /// In en, this message translates to:
  /// **'Tap here to proceed with the selected photo'**
  String get tapToProceedDesc;

  /// No description provided for @deleteAlbumTitle.
  ///
  /// In en, this message translates to:
  /// **'Do you want delete this album?'**
  String get deleteAlbumTitle;

  /// No description provided for @deleteAlbumConfirm.
  ///
  /// In en, this message translates to:
  /// **'“{albumTitle}” will be deleted.'**
  String deleteAlbumConfirm(String albumTitle);

  /// No description provided for @cancel.
  ///
  /// In en, this message translates to:
  /// **'Cancel'**
  String get cancel;

  /// No description provided for @delete.
  ///
  /// In en, this message translates to:
  /// **'Delete'**
  String get delete;

  /// No description provided for @editAlbum.
  ///
  /// In en, this message translates to:
  /// **'Edit album'**
  String get editAlbum;

  /// No description provided for @fullBody.
  ///
  /// In en, this message translates to:
  /// **'Full body'**
  String get fullBody;

  /// No description provided for @mediumShot.
  ///
  /// In en, this message translates to:
  /// **'Medium shot'**
  String get mediumShot;

  /// No description provided for @portrait.
  ///
  /// In en, this message translates to:
  /// **'Portrait'**
  String get portrait;

  /// No description provided for @degreeOfSimilarity.
  ///
  /// In en, this message translates to:
  /// **'Degree of similarity'**
  String get degreeOfSimilarity;

  /// No description provided for @visualization.
  ///
  /// In en, this message translates to:
  /// **'Visualization'**
  String get visualization;

  /// No description provided for @degreeOfSimilarityDesc.
  ///
  /// In en, this message translates to:
  /// **'and the degree of similarity'**
  String get degreeOfSimilarityDesc;

  /// No description provided for @visualizationDesc.
  ///
  /// In en, this message translates to:
  /// **'Here you can configure the visualization settings'**
  String get visualizationDesc;

  /// No description provided for @low.
  ///
  /// In en, this message translates to:
  /// **'Low'**
  String get low;

  /// No description provided for @medium.
  ///
  /// In en, this message translates to:
  /// **'Medium'**
  String get medium;

  /// No description provided for @high.
  ///
  /// In en, this message translates to:
  /// **'High'**
  String get high;

  /// No description provided for @numFrames.
  ///
  /// In en, this message translates to:
  /// **'Number of resulting frames'**
  String get numFrames;

  /// No description provided for @modelLoading.
  ///
  /// In en, this message translates to:
  /// **'Model loading\nWait a moment, please'**
  String get modelLoading;

  /// No description provided for @niceWork.
  ///
  /// In en, this message translates to:
  /// **'Nice work!'**
  String get niceWork;

  /// No description provided for @poseMatchingHint.
  ///
  /// In en, this message translates to:
  /// **'You will see schematic skeleton of the reference photo and a skeleton that matches your model.\n Place the model in the highlighted sector and merge the skeletons.'**
  String get poseMatchingHint;

  /// No description provided for @poseZonesHint.
  ///
  /// In en, this message translates to:
  /// **'Auxiliary sectors display the correspondence of body parts to the desired pose. For a perfect shot, each of them should be green.'**
  String get poseZonesHint;

  /// No description provided for @zoomHint.
  ///
  /// In en, this message translates to:
  /// **'The zoom will be adjusted automatically, but you can change it yourself if necessary. Currently, only one person can be photographed.'**
  String get zoomHint;

  /// No description provided for @autoSaveHint.
  ///
  /// In en, this message translates to:
  /// **'The resulting photo will be saved automatically in your gallery in the PreciosAI application folder'**
  String get autoSaveHint;

  /// No description provided for @noSkeletonHint.
  ///
  /// In en, this message translates to:
  /// **'If you don\'t see skeleton that matches your model, move the camera a little'**
  String get noSkeletonHint;

  /// No description provided for @mergeSkeletonsHint.
  ///
  /// In en, this message translates to:
  /// **'Place the model in the highlighted sector and merge the skeletons'**
  String get mergeSkeletonsHint;

  /// No description provided for @moveSlowlyHint.
  ///
  /// In en, this message translates to:
  /// **'Move the camera slowly and give your model cues'**
  String get moveSlowlyHint;

  /// No description provided for @newAlbum.
  ///
  /// In en, this message translates to:
  /// **'New Album'**
  String get newAlbum;

  /// No description provided for @albumName.
  ///
  /// In en, this message translates to:
  /// **'Album name'**
  String get albumName;

  /// No description provided for @chooseFromAssets.
  ///
  /// In en, this message translates to:
  /// **'Choose from Assets'**
  String get chooseFromAssets;

  /// No description provided for @chooseFromGallery.
  ///
  /// In en, this message translates to:
  /// **'Choose from Gallery'**
  String get chooseFromGallery;

  /// No description provided for @basicImages.
  ///
  /// In en, this message translates to:
  /// **'Basic images'**
  String get basicImages;

  /// No description provided for @gallery.
  ///
  /// In en, this message translates to:
  /// **'Gallery'**
  String get gallery;

  /// No description provided for @cover.
  ///
  /// In en, this message translates to:
  /// **'Cover'**
  String get cover;

  /// No description provided for @chooseFromBasicImages.
  ///
  /// In en, this message translates to:
  /// **'Choose from basic reference images'**
  String get chooseFromBasicImages;

  /// No description provided for @createNewAlbum.
  ///
  /// In en, this message translates to:
  /// **'Create new album'**
  String get createNewAlbum;

  /// No description provided for @defaultAlbumName.
  ///
  /// In en, this message translates to:
  /// **'My Album'**
  String get defaultAlbumName;

  /// No description provided for @done.
  ///
  /// In en, this message translates to:
  /// **'Done'**
  String get done;

  /// No description provided for @next.
  ///
  /// In en, this message translates to:
  /// **'Next'**
  String get next;

  /// No description provided for @visualizationType.
  ///
  /// In en, this message translates to:
  /// **'{visualization, select, empty{Empty} skeleton{Skeleton} capsules{Capsules} skeletonCapsules{Skeleton+Capsules} other{Unknown}}'**
  String visualizationType(String visualization);
}

class _AppLocalizationsDelegate
    extends LocalizationsDelegate<AppLocalizations> {
  const _AppLocalizationsDelegate();

  @override
  Future<AppLocalizations> load(Locale locale) {
    return SynchronousFuture<AppLocalizations>(lookupAppLocalizations(locale));
  }

  @override
  bool isSupported(Locale locale) =>
      <String>['de', 'en', 'es', 'pt', 'ru'].contains(locale.languageCode);

  @override
  bool shouldReload(_AppLocalizationsDelegate old) => false;
}

AppLocalizations lookupAppLocalizations(Locale locale) {
  // Lookup logic when only language code is specified.
  switch (locale.languageCode) {
    case 'de':
      return AppLocalizationsDe();
    case 'en':
      return AppLocalizationsEn();
    case 'es':
      return AppLocalizationsEs();
    case 'pt':
      return AppLocalizationsPt();
    case 'ru':
      return AppLocalizationsRu();
  }

  throw FlutterError(
    'AppLocalizations.delegate failed to load unsupported locale "$locale". This is likely '
    'an issue with the localizations generation tool. Please file an issue '
    'on GitHub with a reproducible sample app and the gen-l10n configuration '
    'that was used.',
  );
}
