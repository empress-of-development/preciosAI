import 'dart:convert';
import 'dart:io';
import 'dart:math';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart' show rootBundle;
import 'package:flutter_staggered_grid_view/flutter_staggered_grid_view.dart';
import 'package:image_picker/image_picker.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:showcaseview/showcaseview.dart';
import 'package:uuid/uuid.dart';

import 'additional_albums.dart';
import 'glowing_button.dart';
import 'neural_background_circles.dart';
import 'neuro_scanner.dart';

enum AlbumImageSource { asset, file }

Map<String, dynamic>? _cachedAssetManifest;

Future<Map<String, dynamic>> _getAssetManifest() async {
  if (_cachedAssetManifest != null) return _cachedAssetManifest!;
  final jsonStr = await rootBundle.loadString('AssetManifest.json');
  _cachedAssetManifest = json.decode(jsonStr);
  return _cachedAssetManifest!;
}

Future<void> _checkAndShowDoneShowcase(
  BuildContext context,
  GlobalKey key,
) async {
  final prefs = await SharedPreferences.getInstance();
  final isFirstRun = prefs.getBool('isFirstRun_doneButton') ?? true;

  if (isFirstRun && context.mounted) {
    await prefs.setBool('isFirstRun_doneButton', false);
    ShowcaseView.get().startShowCase([key]);
  }
}

class AlbumImageRef {
  final AlbumImageSource source;
  final String value; // asset path or file path

  const AlbumImageRef.asset(this.value) : source = AlbumImageSource.asset;

  const AlbumImageRef.file(this.value) : source = AlbumImageSource.file;

  Map<String, dynamic> toJson() => {'source': source.name, 'value': value};

  factory AlbumImageRef.fromJson(Map<String, dynamic> json) => AlbumImageRef._(
    source: AlbumImageSource.values.firstWhere((e) => e.name == json['source']),
    value: json['value'] as String,
  );

  const AlbumImageRef._({required this.source, required this.value});

  @override
  bool operator ==(Object other) {
    if (identical(this, other)) return true;
    return other is AlbumImageRef &&
        other.source == source &&
        other.value == value;
  }

  @override
  int get hashCode => Object.hash(source, value);
}

class Album {
  final String title;
  final AlbumImageRef cover;
  final String? imagesGeneralPath;
  final List<AlbumImageRef>? imagesPathList;
  final String uuid;

  Album({
    String? uuid,
    required this.title,
    required this.cover,
    this.imagesGeneralPath,
    this.imagesPathList,
  }) : uuid = uuid ?? const Uuid().v4();

  Map<String, dynamic> toJson() => {
    'uuid': uuid,
    'title': title,
    'cover': cover.toJson(),
    'imagesGeneralPath': imagesGeneralPath,
    'imagesPathList': (imagesPathList ?? const [])
        .map((e) => e.toJson())
        .toList(),
  };

  factory Album.fromJson(Map<String, dynamic> json) => Album(
    uuid: json['uuid'] as String?,
    title: (json['title'] ?? '').toString(),
    cover: AlbumImageRef.fromJson(
      (json['cover'] as Map).cast<String, dynamic>(),
    ),
    imagesGeneralPath: json['imagesGeneralPath'] as String?,
    imagesPathList: ((json['imagesPathList'] as List?) ?? const [])
        .whereType<Map>()
        .map((e) => AlbumImageRef.fromJson(e.cast<String, dynamic>()))
        .toList(),
  );
}

final List<Album> albumsDefault = [
  Album(
    title: 'Full body',
    cover: const AlbumImageRef.asset(
      'assets/gallery_images/full_body/full_body_1.jpg',
    ),
    imagesGeneralPath: 'assets/gallery_images/full_body/',
  ),
  Album(
    title: 'Medium shot',
    cover: const AlbumImageRef.asset(
      'assets/gallery_images/medium_shot/medium_shot_1.jpg',
    ),
    imagesGeneralPath: 'assets/gallery_images/medium_shot/',
  ),
  Album(
    title: 'Portrait',
    cover: const AlbumImageRef.asset(
      'assets/gallery_images/portrait/portrait_1.jpg',
    ),
    imagesGeneralPath: 'assets/gallery_images/portrait/',
  ),
];

class SelectedPhotoScreen extends StatefulWidget {
  final File imageFile;

  const SelectedPhotoScreen({super.key, required this.imageFile});

  @override
  State<SelectedPhotoScreen> createState() => _SelectedPhotoScreenState();
}

class _SelectedPhotoScreenState extends State<SelectedPhotoScreen> {
  final GlobalKey _doneKey = GlobalKey();

  @override
  void initState() {
    super.initState();

    WidgetsBinding.instance.addPostFrameCallback((_) {
      _checkAndShowDoneShowcase(context, _doneKey);
    });
  }

  void _continue(BuildContext context, String imagePath) {
    Navigator.push(
      context,
      MaterialPageRoute(builder: (_) => ImageScanPage(imagePath: imagePath)),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,

      body: Stack(
        children: [
          Center(
            child: Hero(
              tag: 'selected_photo',
              child: Material(
                color: Colors.transparent,
                child: Container(
                  padding: const EdgeInsets.all(6),
                  decoration: BoxDecoration(
                    color: Colors.transparent,
                    borderRadius: BorderRadius.circular(22),
                    border: Border.all(
                      color: Colors.deepPurpleAccent,
                      width: 4,
                    ),
                  ),
                  child: ClipRRect(
                    borderRadius: BorderRadius.circular(18),
                    child: InteractiveViewer(
                      child: Image.file(widget.imageFile, fit: BoxFit.cover),
                    ),
                  ),
                ),
              ),
            ),
          ),
          Align(
            alignment: Alignment.bottomCenter,
            child: Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Padding(
                  padding: const EdgeInsets.only(bottom: 32),
                  child: Showcase(
                    key: _doneKey,
                    description: 'Tap here to proceed with the selected photo',
                    descTextStyle: const TextStyle(
                      fontSize: 18,
                      fontWeight: FontWeight.w500,
                    ),
                    targetShapeBorder: const CircleBorder(),
                    child: RippleCircleButton(
                      iconPath: 'assets/icons/done.png',
                      onTap: () => _continue(context, widget.imageFile.path),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class AlbumScreen extends StatefulWidget {
  const AlbumScreen({super.key});

  @override
  State<AlbumScreen> createState() => _AlbumScreenState();
}

class _AlbumScreenState extends State<AlbumScreen> {
  File? pickedImage;
  late final List<Album> builtInAlbums;
  final List<Album> albums = [];
  final Set<int> protectedIndexes = {0, 1, 2};

  // Keys for the showcase steps
  final GlobalKey _stepOne = GlobalKey();
  final GlobalKey _stepTwo = GlobalKey();
  final GlobalKey _stepThree = GlobalKey();
  final GlobalKey _stepFour = GlobalKey();
  final GlobalKey _stepFive = GlobalKey();

  @override
  void initState() {
    super.initState();
    builtInAlbums = List<Album>.from(albumsDefault);
    albums.addAll(builtInAlbums);
    _loadUserAlbums();

    // Trigger showcase on first run after layout builds
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _checkFirstRun();
    });
  }

  @override
  void dispose() {
    super.dispose();
  }

  Future<void> _checkFirstRun() async {
    final prefs = await SharedPreferences.getInstance();
    final isFirstRun = prefs.getBool('isFirstRun_albumScreen') ?? true;

    if (isFirstRun && mounted) {
      _startShowcase();
      await prefs.setBool('isFirstRun_albumScreen', false);
    }
  }

  void _startShowcase() async {
    ShowcaseView.get().startShowCase([
      _stepOne,
      _stepTwo,
      _stepThree,
      _stepFour,
      _stepFive,
    ]);

    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool('isFirstRun_doneButton', true);
    await prefs.setBool('isFirstRun_cameraPage', true);
  }

  Future<void> _loadUserAlbums() async {
    try {
      final saved = await AlbumStorage.loadAlbums();
      setState(() {
        final existingIds = albums.map((a) => a.uuid).toSet();
        albums.addAll(saved.where((a) => !existingIds.contains(a.uuid)));
      });
    } catch (e) {
      debugPrint('Failed to load albums: $e');
    }
  }

  Future<void> pickFromGallery() async {
    final picker = ImagePicker();
    final XFile? file = await picker.pickImage(source: ImageSource.gallery);

    if (file != null) {
      setState(() {
        pickedImage = File(file.path);
      });

      // Переход на экран предпросмотра
      Navigator.push(
        context,
        MaterialPageRoute(
          builder: (_) => SelectedPhotoScreen(imageFile: pickedImage!),
        ),
      );
    }
  }

  Future<void> pickRandomfromAssets() async {
    final manifestMap = await _getAssetManifest();

    // Фильтруем нужные файлы
    final jpgFiles = manifestMap.keys
        .where(
          (path) =>
              path.startsWith('assets/gallery_images/') &&
              path.toLowerCase().endsWith('.jpg'),
        )
        .toList();

    if (jpgFiles.isEmpty) return null;

    final random = Random();
    final String? file = jpgFiles[random.nextInt(jpgFiles.length)];

    if (file != null) {
      Navigator.push(
        context,
        MaterialPageRoute(builder: (_) => ImageScanPage(imagePath: file)),
      );
    }
  }

  Future<void> _tryDeleteAlbum(int index) async {
    if (protectedIndexes.contains(index) || index == albums.length) {
      return;
    }

    final album = albums[index];

    final confirm = await showDialog<bool>(
      context: context,
      builder: (_) => AlertDialog(
        title: const Text('Do you want delete this album?'),
        content: Text('“${album.title}” will be deleted.'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Cancel'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Delete'),
          ),
        ],
      ),
    );

    if (confirm != true) return;

    setState(() => albums.removeAt(index));
    await AlbumStorage.deleteAlbumByUuid(album.uuid);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      extendBody: true,
      extendBodyBehindAppBar: true,
      body: MediaQuery.removePadding(
        context: context,
        removeBottom: true,
        child: Stack(
          children: [
            const NeuralNetworkWithBlurredCircles(),

            Align(
              alignment: const Alignment(0.0, -0.3),
              child: Showcase.withWidget(
                key: _stepOne,
                targetShapeBorder: const CircleBorder(),
                targetPadding: EdgeInsets.zero,
                container: SizedBox(
                  width: MediaQuery.of(context).size.width * 0.8,
                  child: const Text(
                    'Welcome to PreciosAI',
                    textAlign: TextAlign.center,
                    style: TextStyle(
                      color: Colors.white,
                      fontSize: 30,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ),
                child: Showcase.withWidget(
                  key: _stepTwo,
                  targetShapeBorder: const CircleBorder(),
                  targetPadding: EdgeInsets.zero,
                  container: SizedBox(
                    width: MediaQuery.of(context).size.width * 0.8,
                    child: const Text(
                      'First, you need to choose a photo\nwith preferred pose and angle',
                      textAlign: TextAlign.center,
                      style: TextStyle(
                        color: Colors.white,
                        fontSize: 24,
                        fontWeight: FontWeight.w500,
                      ),
                    ),
                  ),
                  child: const SizedBox(
                    width: 1,
                    height: 1,
                  ), // Invisible anchor
                ),
              ),
            ),

            ClipRRect(
              borderRadius: const BorderRadius.only(
                bottomLeft: Radius.circular(24),
                bottomRight: Radius.circular(24),
              ),
              child: Container(
                color: Colors.white.withOpacity(0.35),
                height:
                    kToolbarHeight * 1.3 + MediaQuery.of(context).padding.top,
                child: SafeArea(
                  child: Stack(
                    children: [
                      Padding(
                        padding: const EdgeInsets.only(top: kToolbarHeight / 3),
                        child: Center(
                          child: Text(
                            "Let's choose a reference photo",
                            style: TextStyle(
                              color: Colors.grey.shade900,
                              fontSize: 24,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                        ),
                      ),
                      Positioned(
                        right: 5,
                        child: IconButton(
                          icon: Icon(
                            Icons.help_outline,
                            color: Colors.purple.shade900,
                            size: 40,
                          ),
                          onPressed: _startShowcase,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),

            Column(
              children: [
                SizedBox(
                  height: kToolbarHeight + MediaQuery.of(context).padding.top,
                ),
                Expanded(
                  child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: GridView.builder(
                      itemCount: albums.length + 1,
                      gridDelegate:
                          const SliverGridDelegateWithFixedCrossAxisCount(
                            crossAxisCount: 2,
                            mainAxisSpacing: 16,
                            crossAxisSpacing: 16,
                            childAspectRatio: 0.85,
                          ),
                      itemBuilder: (context, index) {
                        if (index == albums.length) {
                          return AddAlbumCard(
                            onTap: () async {
                              final created = await Navigator.push<Album?>(
                                context,
                                MaterialPageRoute(
                                  builder: (_) => const CreateAlbumScreen(),
                                ),
                              );

                              if (created != null) {
                                setState(() => albums.add(created));
                                await AlbumStorage.saveAlbum(created);
                              }
                            },
                          );
                        }

                        final album = albums[index];
                        final canDelete = !protectedIndexes.contains(index);

                        final Widget albumCardWidget = Stack(
                          children: [
                            AlbumCard(
                              album: album,
                              onLongPress: () => _tryDeleteAlbum(index),
                              onTap: () async {
                                final updated = await Navigator.push<Album?>(
                                  context,
                                  PageRouteBuilder(
                                    transitionDuration: const Duration(
                                      milliseconds: 250,
                                    ),
                                    pageBuilder: (_, __, ___) =>
                                        AlbumDetailScreen(album: album),
                                    transitionsBuilder:
                                        (_, animation, __, child) {
                                          return SlideTransition(
                                            position:
                                                Tween(
                                                  begin: const Offset(1.0, 0.0),
                                                  end: Offset.zero,
                                                ).animate(
                                                  CurvedAnimation(
                                                    parent: animation,
                                                    curve: Curves.easeOutQuad,
                                                  ),
                                                ),
                                            child: child,
                                          );
                                        },
                                  ),
                                );

                                if (updated != null) {
                                  final i = albums.indexWhere(
                                    (a) => a.uuid == updated.uuid,
                                  );
                                  if (i != -1) {
                                    setState(() => albums[i] = updated);
                                    if (canDelete)
                                      await AlbumStorage.updateAlbum(updated);
                                  }
                                }
                              },
                            ),
                            if (canDelete)
                              Positioned(
                                top: 8,
                                right: 8,
                                child: GestureDetector(
                                  onTap: () => _tryDeleteAlbum(index),
                                  child: Container(
                                    padding: const EdgeInsets.all(6),
                                    decoration: BoxDecoration(
                                      color: Colors.black54,
                                      borderRadius: BorderRadius.circular(10),
                                    ),
                                    child: const Icon(
                                      Icons.delete_outline,
                                      size: 20,
                                      color: Colors.white,
                                    ),
                                  ),
                                ),
                              ),
                          ],
                        );

                        if (index == 0) {
                          return Showcase(
                            key: _stepThree,
                            description: 'You can select from prepared albums',
                            descTextStyle: const TextStyle(
                              fontSize: 18,
                              fontWeight: FontWeight.w500,
                            ),
                            targetShapeBorder: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(50),
                            ),
                            child: albumCardWidget,
                          );
                        }

                        return albumCardWidget;
                      },
                    ),
                  ),
                ),
                Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Padding(
                      padding: const EdgeInsets.only(bottom: 32),
                      child: Showcase(
                        key: _stepFour,
                        description: 'upload from your gallery',
                        descTextStyle: const TextStyle(
                          fontSize: 18,
                          fontWeight: FontWeight.w500,
                        ),
                        targetShapeBorder: const CircleBorder(),
                        child: RippleCircleButton(
                          iconPath: 'assets/icons/add_photo.png',
                          onTap: pickFromGallery,
                        ),
                      ),
                    ),
                    Padding(
                      padding: const EdgeInsets.only(bottom: 32, left: 16),
                      child: Showcase(
                        key: _stepFive,
                        description: 'or use random choice',
                        descTextStyle: const TextStyle(
                          fontSize: 18,
                          fontWeight: FontWeight.w500,
                        ),
                        targetShapeBorder: const CircleBorder(),
                        child: RippleCircleButton(
                          iconPath: 'assets/icons/random_2.png',
                          onTap: pickRandomfromAssets,
                        ),
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class AlbumCard extends StatelessWidget {
  final Album album;
  final VoidCallback? onTap;
  final VoidCallback? onLongPress;

  const AlbumCard({
    super.key,
    required this.album,
    this.onTap,
    this.onLongPress,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onLongPress: onLongPress,
      onTap: onTap,
      child: Container(
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(22),
          border: Border.all(
            color: Colors.deepPurple.withOpacity(0.7),
            width: 8,
          ),
        ),
        child: ClipRRect(
          borderRadius: BorderRadius.circular(22),
          child: Stack(
            children: [
              Positioned.fill(
                child: album.cover.source == AlbumImageSource.asset
                    ? Image.asset(
                        album.cover.value,
                        fit: BoxFit.cover,
                        cacheWidth: 400,
                      )
                    : Image.file(
                        File(album.cover.value),
                        fit: BoxFit.cover,
                        cacheWidth: 400,
                      ),
              ),
              Positioned.fill(
                child: Container(
                  decoration: BoxDecoration(
                    gradient: LinearGradient(
                      begin: Alignment.bottomCenter,
                      end: Alignment.topCenter,
                      colors: [
                        Colors.black.withOpacity(0.6),
                        Colors.transparent,
                      ],
                    ),
                  ),
                ),
              ),
              Positioned(
                left: 12,
                bottom: 12,
                child: Text(
                  album.title,
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 16,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class AlbumDetailScreen extends StatefulWidget {
  final Album album;

  const AlbumDetailScreen({super.key, required this.album});

  @override
  State<AlbumDetailScreen> createState() => _AlbumDetailScreenState();
}

class _AlbumDetailScreenState extends State<AlbumDetailScreen> {
  AlbumImageRef? selectedImage;
  File? pickedImage;

  List<AlbumImageRef> _images = [];
  bool _isLoading = true;

  final GlobalKey _doneKey = GlobalKey();

  @override
  void initState() {
    super.initState();
    // If we already have the paths, show them immediately to prevent "twitching"

    if (widget.album.imagesPathList != null &&
        widget.album.imagesPathList!.isNotEmpty) {
      _images = widget.album.imagesPathList!;
      _isLoading = false;
    }
    _loadAssetImages();
  }

  void _selectImage(AlbumImageRef path) {
    setState(() {
      selectedImage = path;
      pickedImage = null;
    });

    WidgetsBinding.instance.addPostFrameCallback((_) {
      _checkAndShowDoneShowcase(context, _doneKey);
    });
  }

  void _continue() {
    final imageToUse = pickedImage != null
        ? pickedImage!.path
        : selectedImage!.value;

    Navigator.push(
      context,
      MaterialPageRoute(builder: (_) => ImageScanPage(imagePath: imageToUse)),
    );
  }

  Future<void> _loadAssetImages() async {
    List<AlbumImageRef> loadedImages = [];

    if (widget.album.imagesPathList != null &&
        widget.album.imagesPathList!.isNotEmpty) {
      loadedImages = widget.album.imagesPathList!;
    } else if (widget.album.imagesGeneralPath != null) {
      final manifestMap = await _getAssetManifest();
      loadedImages = manifestMap.keys
          .where((key) => key.startsWith(widget.album.imagesGeneralPath!))
          .map((path) => AlbumImageRef.asset(path))
          .toList();
    }

    if (mounted) {
      setState(() {
        _images = loadedImages;
        _isLoading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final images = _images;

    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        elevation: 0,
        flexibleSpace: Container(
          decoration: BoxDecoration(
            gradient: LinearGradient(
              colors: [Colors.indigo, Colors.grey.shade100],
              begin: Alignment.topLeft,
              end: Alignment.bottomRight,
            ),
            borderRadius: const BorderRadius.vertical(
              bottom: Radius.circular(24),
            ),
          ),
        ),
        title: Text(
          widget.album.title,
          style: TextStyle(
            color: Colors.grey.shade900,
            fontSize: 26,
            fontWeight: FontWeight.bold,
          ),
        ),
        iconTheme: const IconThemeData(color: Colors.black),
        actions: [
          IconButton(
            icon: const Icon(Icons.edit, size: 40),
            tooltip: 'Edit album',
            onPressed: () async {
              final updated = await Navigator.push<Album?>(
                context,
                MaterialPageRoute(
                  builder: (_) => CreateAlbumScreen(existing: widget.album),
                ),
              );

              if (updated != null && mounted) {
                // возврат обновленного альбома назад
                Navigator.pop(context, updated);
              }
            },
          ),
        ],
      ),

      body: Stack(
        children: [
          const NeuralNetworkWithBlurredCircles(),

          Column(
            children: [
              Expanded(
                child: MasonryGridView.count(
                  padding: const EdgeInsets.all(12),
                  crossAxisCount: 2,
                  mainAxisSpacing: 12,
                  crossAxisSpacing: 12,
                  itemCount: images.length,
                  itemBuilder: (_, index) {
                    final img = images[index];
                    final isSelected = selectedImage == img;

                    return GestureDetector(
                      onTap: () {
                        if (selectedImage == img) {
                          // если это второе нажатие, то открываем на весь экран
                          Navigator.push(
                            context,
                            MaterialPageRoute(
                              builder: (_) => PhotoHeroScreen(
                                image: img,
                                tag: 'photo_${widget.album.title}_$index',
                              ),
                            ),
                          );
                        } else {
                          _selectImage(img);
                        }
                      },
                      child: Stack(
                        children: [
                          Hero(
                            tag: 'photo_${widget.album.title}_$index',
                            child: ClipRRect(
                              borderRadius: BorderRadius.circular(16),
                              child: img.source == AlbumImageSource.asset
                                  ? Image.asset(
                                      img.value,
                                      fit: BoxFit.cover,
                                      cacheWidth: 600,
                                    )
                                  : Image.file(
                                      File(img.value),
                                      fit: BoxFit.cover,
                                      cacheWidth: 600,
                                    ),
                            ),
                          ),
                          if (isSelected)
                            Positioned.fill(
                              child: Container(
                                decoration: BoxDecoration(
                                  borderRadius: BorderRadius.circular(16),
                                  border: Border.all(
                                    color: Colors.deepPurpleAccent,
                                    width: 4,
                                  ),
                                ),
                              ),
                            ),
                        ],
                      ),
                    );
                  },
                ),
              ),

              const SizedBox(height: 12),
            ],
          ),
          Align(
            alignment: Alignment.bottomCenter,
            child: Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                // Кнопка продолжить появляется только когда фото выбрано
                if (selectedImage != null || pickedImage != null)
                  Padding(
                    padding: const EdgeInsets.only(bottom: 32),
                    child: Showcase(
                      key: _doneKey,
                      description: 'Tap here to proceed with the selected photo',
                      descTextStyle: const TextStyle(
                        fontSize: 18,
                        fontWeight: FontWeight.w500,
                      ),
                      targetShapeBorder: const CircleBorder(),
                      child: RippleCircleButton(
                        iconPath: 'assets/icons/done.png',
                        onTap: _continue,
                      ),
                    ),
                  ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class PhotoHeroScreen extends StatefulWidget {
  final AlbumImageRef image;
  final String tag;

  const PhotoHeroScreen({super.key, required this.image, required this.tag});

  @override
  State<PhotoHeroScreen> createState() => _PhotoHeroScreenState();
}

class _PhotoHeroScreenState extends State<PhotoHeroScreen> {
  final GlobalKey _doneKey = GlobalKey();

  @override
  void initState() {
    super.initState();

    WidgetsBinding.instance.addPostFrameCallback((_) {
      _checkAndShowDoneShowcase(context, _doneKey);
    });
  }

  void _continue(BuildContext context, String imagePath) {
    Navigator.push(
      context,
      MaterialPageRoute(builder: (_) => ImageScanPage(imagePath: imagePath)),
    );
  }

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: () => Navigator.pop(context),
      child: Scaffold(
        backgroundColor: Colors.black,
        body: Stack(
          children: [
            Center(
              child: Hero(
                tag: widget.tag,
                child: Material(
                  color: Colors.transparent,
                  child: Container(
                    padding: const EdgeInsets.all(6),
                    decoration: BoxDecoration(
                      color: Colors.transparent,
                      borderRadius: BorderRadius.circular(22),
                      border: Border.all(
                        color: Colors.deepPurpleAccent,
                        width: 4,
                      ),
                    ),
                    child: ClipRRect(
                      borderRadius: BorderRadius.circular(18),
                      child: InteractiveViewer(
                        child: widget.image.source == AlbumImageSource.asset
                            ? Image.asset(widget.image.value, fit: BoxFit.cover)
                            : Image.file(File(widget.image.value), fit: BoxFit.cover),
                      ),
                    ),
                  ),
                ),
              ),
            ),
            Align(
              alignment: Alignment.bottomCenter,
              child: Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Padding(
                    padding: const EdgeInsets.only(bottom: 32),
                    child: Showcase(
                      key: _doneKey,
                      description: 'Tap here to proceed with the selected photo',
                      descTextStyle: const TextStyle(
                        fontSize: 18,
                        fontWeight: FontWeight.w500,
                      ),
                      targetShapeBorder: const CircleBorder(),
                      child: RippleCircleButton(
                        iconPath: 'assets/icons/done.png',
                        onTap: () => _continue(context, widget.image.value),
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
