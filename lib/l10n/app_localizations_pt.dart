// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Portuguese (`pt`).
class AppLocalizationsPt extends AppLocalizations {
  AppLocalizationsPt([String locale = 'pt']) : super(locale);

  @override
  String get skip => 'Pular';

  @override
  String get before => 'ANTES';

  @override
  String get after => 'DEPOIS';

  @override
  String get picturePerfectTitle => 'Fotos perfeitas\nna primeira tentativa';

  @override
  String get picturePerfectSubtitle =>
      'Esqueça as dezenas de fotos ruins, não importa se você está atrás ou na frente da câmera.';

  @override
  String get findInspiration => 'Inspire-se com novas ideias';

  @override
  String get findInspirationSubtitle =>
      'Escolha poses e ângulos da coleção pronta ou envie os seus. Salve suas fotos favoritas e planeje suas sessões de fotos facilmente.';

  @override
  String get letCameraGuideYou => 'Seu fotógrafo pessoal direto no celular';

  @override
  String get letCameraGuideYouSubtitle =>
      'Escolha a foto que você quer recriar. Siga as instruções e alinhe as poses. A câmera tirará a foto sozinha assim que a pose coincidir.';

  @override
  String get yourPhotos => 'Suas fotos ';

  @override
  String get stayYours => 'são só suas';

  @override
  String get privacySubtitle =>
      'Todo o processamento acontece no seu dispositivo. Nenhum dado é coletado ou transmitido a terceiros. O app funciona perfeitamente sem internet.';

  @override
  String get oneShotAway =>
      'Guia preciso de IA\npara seus momentos mais preciosos';

  @override
  String get aiGuidance => 'É mais fácil do que parece. Vamos começar?';

  @override
  String get chooseReferenceTitle => 'Escolha uma foto de referência';

  @override
  String get welcomeTitle => 'Boas-vindas ao PreciosAI';

  @override
  String get welcomeSubtitle =>
      'Primeiro, escolha uma foto com a pose e o ângulo que quiser';

  @override
  String get selectFromAlbumsDesc => 'Você pode escolher dos álbuns preparados';

  @override
  String get uploadFromGalleryDesc => 'enviar da galeria';

  @override
  String get useRandomChoiceDesc => 'ou usar seleção aleatória';

  @override
  String get tapToProceedDesc =>
      'Toque aqui para continuar com a foto selecionada';

  @override
  String get deleteAlbumTitle => 'Deseja excluir este álbum?';

  @override
  String deleteAlbumConfirm(String albumTitle) {
    return '\"$albumTitle\" será excluído.';
  }

  @override
  String get cancel => 'Cancelar';

  @override
  String get delete => 'Excluir';

  @override
  String get editAlbum => 'Editar álbum';

  @override
  String get fullBody => 'Plano geral';

  @override
  String get mediumShot => 'Plano médio';

  @override
  String get portrait => 'Retrato';

  @override
  String get degreeOfSimilarity => 'Nível de coincidência';

  @override
  String get visualization => 'Visualização';

  @override
  String get degreeOfSimilarityDesc => 'e o nível de coincidência';

  @override
  String get visualizationDesc =>
      'Você pode configurar os ajustes de visualização';

  @override
  String get low => 'Baixo';

  @override
  String get medium => 'Médio';

  @override
  String get high => 'Alto';

  @override
  String get numFrames => 'Número de fotos no resultado';

  @override
  String get modelLoading => 'Carregando modelo\nAguarde um momento, por favor';

  @override
  String get niceWork => 'Ótimo!';

  @override
  String get poseMatchingHint =>
      'Você verá o esquema da pose desejada e a pose do seu modelo. Coloque o modelo na zona destacada e alinhe as poses.';

  @override
  String get poseZonesHint =>
      'As cápsulas coloridas indicarão quão precisa é a posição de cada parte do corpo. Quando todos os setores ficarem verdes, a pose está perfeita.';

  @override
  String get zoomHint =>
      'O zoom se ajusta automaticamente, mas você sempre pode corrigi-lo manualmente. No momento, só é possível fotografar uma pessoa.';

  @override
  String get autoSaveHint =>
      'A foto final será salva automaticamente na galeria do seu dispositivo na pasta PreciosAI';

  @override
  String get noSkeletonHint =>
      'Não vê a pose do seu modelo? Mova um pouco a câmera';

  @override
  String get mergeSkeletonsHint =>
      'Coloque o modelo na zona destacada e alinhe as poses';

  @override
  String get moveSlowlyHint =>
      'Mova a câmera suavemente e indique ao modelo o que corrigir - inclinação da cabeça, posição dos braços, rotação do corpo';

  @override
  String get newAlbum => 'Novo álbum';

  @override
  String get albumName => 'Título do álbum';

  @override
  String get chooseFromAssets => 'Escolher da coleção';

  @override
  String get chooseFromGallery => 'Escolher da galeria';

  @override
  String get basicImages => 'Coleção';

  @override
  String get gallery => 'Galeria';

  @override
  String get cover => 'Capa';

  @override
  String get chooseFromBasicImages => 'Escolha da coleção';

  @override
  String get createNewAlbum => 'Criar um novo álbum';

  @override
  String get defaultAlbumName => 'Meu álbum';

  @override
  String get done => 'Vamos';

  @override
  String get next => 'Próximo';

  @override
  String visualizationType(String visualization) {
    String _temp0 = intl.Intl.selectLogic(visualization, {
      'empty': 'Nenhuma',
      'skeleton': 'Pose',
      'capsules': 'Cápsulas',
      'skeletonCapsules': 'Pose+Cápsulas',
      'other': 'Unknown',
    });
    return '$_temp0';
  }
}
