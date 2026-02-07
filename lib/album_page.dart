import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart' show rootBundle;
import 'package:flutter_staggered_grid_view/flutter_staggered_grid_view.dart';
import 'package:image_picker/image_picker.dart';
import 'package:uuid/uuid.dart';

import 'additional_albums.dart';
import 'glowing_button.dart';
import 'neural_background_circles.dart';
import 'neuro_scanner.dart';

enum AlbumImageSource { asset, file }

class AlbumImageRef {
  final AlbumImageSource source;
  final String value; // asset path OR file path

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
  final String? imagesGeneralPath; // только папки для статики из ассетов
  final List<AlbumImageRef>? imagesPathList;
  final String uuid = const Uuid().v4();

  Album({
    required this.title,
    required this.cover,
    this.imagesGeneralPath,
    this.imagesPathList,
  });

  Map<String, dynamic> toJson() => {
    'title': title,
    'cover': cover.toJson(),
    'imagesPathList': imagesPathList!.map((e) => e.toJson()).toList(),
  };

  factory Album.fromJson(Map<String, dynamic> json) => Album(
    title: (json['title'] ?? '').toString(),
    cover: AlbumImageRef.fromJson(
      (json['cover'] as Map).cast<String, dynamic>(),
    ),
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

class SelectedPhotoScreen extends StatelessWidget {
  final File imageFile;

  const SelectedPhotoScreen({super.key, required this.imageFile});

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
                      child: Image.file(imageFile, fit: BoxFit.cover),
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
                  child: RippleCircleButton(
                    iconPath: 'assets/icons/done.png',
                    onTap: () => _continue(context, imageFile.path),
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

  @override
  void initState() {
    super.initState();
    builtInAlbums = List<Album>.from(albumsDefault);
    albums.addAll(builtInAlbums);
    _loadUserAlbums();
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

  final Set<int> protectedIndexes = {0, 1, 2};

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
    await AlbumStorage.deleteAlbumByTitle(album.title);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      body: Stack(
        children: [
          const NeuralNetworkWithBlurredCircles(),

          ClipRRect(
            borderRadius: const BorderRadius.only(
              bottomLeft: Radius.circular(24),
              bottomRight: Radius.circular(24),
            ),
            child: Container(
              color: Colors.white.withOpacity(0.35),
              height: kToolbarHeight * 1.3 + MediaQuery.of(context).padding.top,
              child: SafeArea(
                child: Padding(
                  padding: const EdgeInsets.only(top: kToolbarHeight * 1.3 / 4),
                  child: Center(
                    child: Text(
                      "Let's choose a reference photo",
                      style: TextStyle(
                        color: Colors.grey.shade900,
                        fontSize: 26,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ),
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

                      return Stack(
                        children: [
                          AlbumCard(
                            album: album,
                            onLongPress: () => _tryDeleteAlbum(index),
                          ),

                          // Иконка удаления (опционально). Можно убрать, если хочешь только long press.
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
                    },
                  ),
                ),
              ),
              Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Padding(
                    padding: const EdgeInsets.only(bottom: 32),
                    child: RippleCircleButton(
                      iconPath: 'assets/icons/add_photo.png',
                      onTap: pickFromGallery,
                    ),
                  ),
                  Padding(
                    padding: const EdgeInsets.only(bottom: 32),
                    child: RippleCircleButton(
                      iconPath: 'assets/icons/random_2.png',
                      onTap: pickFromGallery,
                    ),
                  ),
                ],
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class AlbumCard extends StatelessWidget {
  final Album album;
  final VoidCallback? onLongPress;

  const AlbumCard({super.key, required this.album, this.onLongPress});

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: () {
        Navigator.push(
          context,
          PageRouteBuilder(
            transitionDuration: const Duration(milliseconds: 450),
            pageBuilder: (_, __, ___) => AlbumDetailScreen(album: album),
            transitionsBuilder: (_, animation, __, child) {
              return FadeTransition(
                opacity: animation,
                child: SlideTransition(
                  position: Tween(
                    begin: const Offset(0, 0.05),
                    end: Offset.zero,
                  ).animate(animation),
                  child: child,
                ),
              );
            },
          ),
        );
      },
      onLongPress: onLongPress,

      child: Hero(
        tag: 'album_${album.title}_${album.uuid}',

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
                      ? Image.asset(album.cover.value, fit: BoxFit.cover)
                      : Image.file(File(album.cover.value), fit: BoxFit.cover),
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

  Map<String, dynamic> _manifestMap = {};
  final ImagePicker _picker = ImagePicker();

  @override
  void initState() {
    super.initState();
    _loadAssetImages();
  }

  Future<void> _pickFromGallery() async {
    final XFile? file = await _picker.pickImage(source: ImageSource.gallery);
    if (file != null) {
      setState(() {
        pickedImage = File(file.path);
        selectedImage = null;
      });
    }
  }

  void _selectImage(AlbumImageRef path) {
    setState(() {
      selectedImage = path;
      pickedImage = null;
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
    final manifestContent = await rootBundle.loadString('AssetManifest.json');
    final manifestMap = json.decode(manifestContent);
    setState(() {
      _manifestMap = manifestMap;
    });
  }

  @override
  Widget build(BuildContext context) {
    final List<AlbumImageRef> images;
    if (widget.album.imagesGeneralPath != null) {
      images = _manifestMap.keys
          .where((key) => key.startsWith(widget.album.imagesGeneralPath!))
          .map((path) => AlbumImageRef.asset(path))
          .toList();
    } else {
      images = widget.album.imagesPathList!;
    }

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
                                  ? Image.asset(img.value, fit: BoxFit.cover)
                                  : Image.file(
                                      File(img.value),
                                      fit: BoxFit.cover,
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
                    child: RippleCircleButton(
                      iconPath: 'assets/icons/done.png',
                      onTap: _continue,
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

class PhotoHeroScreen extends StatelessWidget {
  final AlbumImageRef image;
  final String tag;

  const PhotoHeroScreen({super.key, required this.image, required this.tag});

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
                tag: tag,
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
                        child: image.source == AlbumImageSource.asset
                            ? Image.asset(image.value, fit: BoxFit.cover)
                            : Image.file(File(image.value), fit: BoxFit.cover),
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
                    child: RippleCircleButton(
                      iconPath: 'assets/icons/done.png',
                      onTap: () => _continue(context, image.value),
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
