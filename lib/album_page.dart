import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart' show rootBundle;
import 'package:flutter_staggered_grid_view/flutter_staggered_grid_view.dart';
import 'package:image_picker/image_picker.dart';

import 'glowing_button.dart';
import 'neural_background_circles.dart';
import 'neuro_scanner.dart';

class Album {
  final String title;
  final String titleImagePath;
  final String imagesPath;
  final int count;

  Album({
    required this.title,
    required this.titleImagePath,
    required this.imagesPath,
    required this.count,
  });
}

final List<Album> albums = [
  Album(
    title: 'Full body',
    count: 3,
    titleImagePath: 'assets/gallery_images/full_body/1.jpg',
    imagesPath: 'assets/gallery_images/full_body/',
  ),
  Album(
    title: 'Medium shot',
    count: 3,
    titleImagePath: 'assets/gallery_images/medium_shot/1.jpg',
    imagesPath: 'assets/gallery_images/medium_shot/',
  ),
  Album(
    title: 'Portrait',
    count: 3,
    titleImagePath: 'assets/gallery_images/portrait/1.jpg',
    imagesPath: 'assets/gallery_images/portrait/',
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

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      //backgroundColor: Colors.grey.shade900,
      backgroundColor: Colors.black,
      /*
      appBar: AppBar(
        backgroundColor: Colors.blueGrey.shade900,
        elevation: 0,
        title: const Text(
          "Let's choose a reference photo",
          style: TextStyle(
            color: Color(0xFFEEEEEE),
            fontSize: 26,
            fontWeight: FontWeight.bold,
          ),
        ),
      ),

      appBar: PreferredSize(
        preferredSize: const Size.fromHeight(kToolbarHeight),
        child: Material(
          elevation: 0,
          color: Colors.white.withOpacity(0.5),
          shape: const RoundedRectangleBorder(
            borderRadius: BorderRadius.vertical(
              bottom: Radius.circular(24),
            ),
          ),
          child: Center(child: const Text(
            "Let's choose a reference photo",
            style: TextStyle(
              color: Color(0xFFEEEEEE),
              fontSize: 26,
              fontWeight: FontWeight.bold,
            ),
          ),
          ),
        ),
      ),
      */
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
                    itemCount: albums.length,
                    gridDelegate:
                        const SliverGridDelegateWithFixedCrossAxisCount(
                          crossAxisCount: 2,
                          mainAxisSpacing: 16,
                          crossAxisSpacing: 16,
                          childAspectRatio: 0.85,
                        ),
                    itemBuilder: (context, index) {
                      final album = albums[index];
                      return AlbumCard(album: album);
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

class RoundedHeader extends StatelessWidget {
  final String title;

  const RoundedHeader({super.key, required this.title});

  @override
  Widget build(BuildContext context) {
    return Container(
      height: 160,
      width: double.infinity,
      decoration: const BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [Color(0xFFFF7A66), Color(0xFFFFA8A0)],
        ),
        borderRadius: BorderRadius.only(
          bottomLeft: Radius.circular(40),
          bottomRight: Radius.circular(40),
        ),
      ),
      child: SafeArea(
        bottom: false,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 20),
          child: Row(
            children: [
              GestureDetector(
                onTap: () => Navigator.pop(context),
                child: const Icon(
                  Icons.arrow_back,
                  color: Colors.white,
                  size: 28,
                ),
              ),
              const SizedBox(width: 12),
              Text(
                title,
                style: const TextStyle(
                  color: Colors.white,
                  fontSize: 24,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class AlbumCard extends StatelessWidget {
  final Album album;

  const AlbumCard({super.key, required this.album});

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
      child: Hero(
        tag: 'album_${album.title}',

        child: Container(
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(22),
            border: Border.all(
              color: Colors.deepPurple.withOpacity(0.22),
              width: 3,
            ),
          ),
          child: ClipRRect(
            borderRadius: BorderRadius.circular(22),
            child: Stack(
              children: [
                Positioned.fill(
                  child: Image.asset(album.titleImagePath, fit: BoxFit.cover),
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
  String? selectedImage;
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

  void _selectImage(String path) {
    setState(() {
      selectedImage = path;
      pickedImage = null;
    });
  }

  void _continue() {
    final imageToUse = pickedImage != null ? pickedImage!.path : selectedImage;

    Navigator.push(
      context,
      MaterialPageRoute(builder: (_) => ImageScanPage(imagePath: imageToUse!)),
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
    final List<String> images = _manifestMap.keys
        .where((String key) => key.startsWith(widget.album.imagesPath))
        .toList();

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
                              child: Image.asset(img, fit: BoxFit.cover),
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
  final String image;
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
                        child: Image.asset(image, fit: BoxFit.contain),
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
                      onTap: () => _continue(context, image),
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
