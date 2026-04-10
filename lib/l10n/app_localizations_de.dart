// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for German (`de`).
class AppLocalizationsDe extends AppLocalizations {
  AppLocalizationsDe([String locale = 'de']) : super(locale);

  @override
  String get skip => 'Überspringen';

  @override
  String get before => 'VORHER';

  @override
  String get after => 'NACHHER';

  @override
  String get picturePerfectTitle => 'Perfekte Aufnahmen\nbeim ersten Versuch';

  @override
  String get picturePerfectSubtitle =>
      'Schluss mit Dutzenden misslungener Fotos - egal, ob du fotografierst oder posierst.';

  @override
  String get findInspiration => 'Lass dich von neuen Ideen inspirieren';

  @override
  String get findInspirationSubtitle =>
      'Wähle Posen und Blickwinkel aus der fertigen Sammlung oder lade deine eigenen hoch. Speichere deine Favoriten und plane deine Shootings ganz ohne Aufwand.';

  @override
  String get letCameraGuideYou => 'Dein persönlicher Fotograf\ndirekt im Handy';

  @override
  String get letCameraGuideYouSubtitle =>
      'Wähle das Foto, das du nachstellen möchtest. Folge den Hinweisen und gleiche die Posen ab — die Kamera löst automatisch aus, sobald die Pose übereinstimmt.';

  @override
  String get yourPhotos => 'Deine Fotos ';

  @override
  String get stayYours => 'bleiben nur deine';

  @override
  String get privacySubtitle =>
      'Die gesamte Verarbeitung erfolgt auf deinem Gerät. Es werden keine Daten gesammelt oder an Dritte weitergegeben. Die App funktioniert einwandfrei ohne Internet.';

  @override
  String get oneShotAway =>
      'Schluss mit \"nochmal bitte\" und \"ich weiß nicht, wohin mit den Händen\"';

  @override
  String get aiGuidance => 'Es ist einfacher, als du denkst. Legen wir los?';

  @override
  String get chooseReferenceTitle => 'Wähle ein Referenzfoto';

  @override
  String get welcomeTitle => 'Willkommen bei PreciosAI';

  @override
  String get welcomeSubtitle =>
      'Wähle zunächst ein Foto mit der Pose und dem Blickwinkel, die du nachstellen möchtest';

  @override
  String get selectFromAlbumsDesc => 'Du kannst aus fertigen Alben auswählen';

  @override
  String get uploadFromGalleryDesc => 'aus deiner Galerie hochladen';

  @override
  String get useRandomChoiceDesc => 'oder eine zufällige Auswahl nutzen';

  @override
  String get tapToProceedDesc =>
      'Tippe hier, um mit dem ausgewählten Foto fortzufahren';

  @override
  String get deleteAlbumTitle => 'Dieses Album löschen?';

  @override
  String deleteAlbumConfirm(String albumTitle) {
    return 'Das Album \"$albumTitle\" wird gelöscht.';
  }

  @override
  String get cancel => 'Abbrechen';

  @override
  String get delete => 'Löschen';

  @override
  String get editAlbum => 'Album bearbeiten';

  @override
  String get fullBody => 'Ganzkörper';

  @override
  String get mediumShot => 'Halbkörper';

  @override
  String get portrait => 'Porträt';

  @override
  String get degreeOfSimilarity => 'Übereinstimmungsgrad';

  @override
  String get visualization => 'Visualisierung';

  @override
  String get degreeOfSimilarityDesc => 'und den Übereinstimmungsgrad';

  @override
  String get visualizationDesc =>
      'Hier kannst du die Visualisierungseinstellungen anpassen';

  @override
  String get low => 'Niedrig';

  @override
  String get medium => 'Mittel';

  @override
  String get high => 'Hoch';

  @override
  String get numFrames => 'Anzahl der Fotos im Ergebnis';

  @override
  String get modelLoading =>
      'Modell wird geladen\nBitte einen Moment warten...';

  @override
  String get niceWork => 'Toll gemacht!';

  @override
  String get poseMatchingHint =>
      'Du siehst das Schema der gewünschten Pose und die Pose deines Modells im Bild. Platziere das Modell im blinkenden Bereich und gleiche die Posen ab.';

  @override
  String get poseZonesHint =>
      'Farbige Kapseln zeigen dir, wie genau die Position jedes Körperteils übereinstimmt. Wenn alle Sektoren grün werden, ist die Pose perfekt.';

  @override
  String get zoomHint =>
      'Der Zoom wird automatisch angepasst, du kannst ihn aber jederzeit manuell korrigieren. Derzeit wird nur die Aufnahme einer einzelnen Person unterstützt.';

  @override
  String get autoSaveHint =>
      'Das fertige Foto wird automatisch in der Galerie deines Geräts im Ordner PreciosAI gespeichert.';

  @override
  String get noSkeletonHint =>
      'Du siehst die Pose deines Modells nicht? Bewege die Kamera ein wenig';

  @override
  String get mergeSkeletonsHint =>
      'Platziere das Modell im blinkenden Bereich und gleiche die Posen ab';

  @override
  String get moveSlowlyHint =>
      'Bewege die Kamera langsam und sage dem Modell, was es korrigieren soll — Kopfneigung, Armposition, Körperdrehung';

  @override
  String get newAlbum => 'Neues Album';

  @override
  String get albumName => 'Albumtitel';

  @override
  String get chooseFromAssets => 'Aus der Sammlung wählen';

  @override
  String get chooseFromGallery => 'Aus der Galerie wählen';

  @override
  String get basicImages => 'Sammlung';

  @override
  String get gallery => 'Galerie';

  @override
  String get cover => 'Cover';

  @override
  String get chooseFromBasicImages => 'Wähle aus der Sammlung';

  @override
  String get createNewAlbum => 'Neues Album erstellen';

  @override
  String get defaultAlbumName => 'Mein Album';

  @override
  String get done => 'Los geht\'s';

  @override
  String get next => 'Weiter';

  @override
  String visualizationType(String visualization) {
    String _temp0 = intl.Intl.selectLogic(visualization, {
      'empty': 'Keine',
      'skeleton': 'Pose',
      'capsules': 'Kapseln',
      'skeletonCapsules': 'Pose+Kapseln',
      'other': 'Unbekannt',
    });
    return '$_temp0';
  }

  @override
  String get about => 'Über';

  @override
  String get privacyPolicy => 'Datenschutzrichtlinie';

  @override
  String get appGuide => 'App-Anleitung';

  @override
  String get infoText =>
      'Smarte KI-Kamera, die die perfekte Pose trifft und kostbare Momente festhält.';

  @override
  String get openSourceLicenses => 'Open-Source-Lizenzen';
}
