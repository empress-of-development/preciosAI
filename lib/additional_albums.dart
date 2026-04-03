import 'dart:convert';
import 'dart:io';

import 'package:dotted_border/dotted_border.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart' show rootBundle;
import 'package:image_picker/image_picker.dart';
import 'package:preciosai/l10n/app_localizations.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'album_page.dart';
import 'neural_background_circles.dart';

class AlbumStorage {
  static const _key = 'user_albums';

  static Future<List<Album>> loadAlbums() async {
    final prefs = await SharedPreferences.getInstance();
    final raw = prefs.getString(_key);
    if (raw == null) return [];

    final decoded = (jsonDecode(raw) as List).cast<Map<String, dynamic>>();
    return decoded.map(Album.fromJson).toList();
  }

  static Future<void> saveAlbum(Album album) async {
    final current = await loadAlbums();
    current.add(album);

    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(
      _key,
      jsonEncode(current.map((a) => a.toJson()).toList()),
    );
  }

  static Future<void> saveAlbums(List<Album> albums) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(
      _key,
      jsonEncode(albums.map((a) => a.toJson()).toList()),
    );
  }

  static Future<void> updateAlbum(Album updated) async {
    final prefs = await SharedPreferences.getInstance();
    final raw = prefs.getString(_key);
    final List<dynamic> decoded = raw == null ? [] : (jsonDecode(raw) as List);

    final albums = decoded
        .whereType<Map>()
        .map((e) => Album.fromJson(e.cast<String, dynamic>()))
        .toList();

    final index = albums.indexWhere((a) => a.uuid == updated.uuid);

    if (index == -1) {
      // не нашли — значит uuid не совпал или prefs пусты
      albums.add(updated);
    } else {
      albums[index] = updated;
    }

    await prefs.setString(
      _key,
      jsonEncode(albums.map((a) => a.toJson()).toList()),
    );
  }

  static Future<void> deleteAlbumByUuid(String albumUuid) async {
    final current = await loadAlbums();
    current.removeWhere((a) => a.uuid == albumUuid);

    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(
      _key,
      jsonEncode(current.map((a) => a.toJson()).toList()),
    );
  }
}

class AddAlbumCard extends StatelessWidget {
  final VoidCallback onTap;

  const AddAlbumCard({super.key, required this.onTap});

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return GestureDetector(
      onTap: onTap,
      child: DottedBorder(
        borderType: BorderType.RRect,
        radius: const Radius.circular(22),
        dashPattern: const [10, 8],
        strokeWidth: 5,
        color: Colors.deepPurple.withOpacity(0.7),
        child: ClipRRect(
          borderRadius: BorderRadius.circular(22),
          child: Container(
            decoration: BoxDecoration(
              color: Colors.white.withOpacity(0.06),
              borderRadius: BorderRadius.circular(22),
            ),
            child: Stack(
              children: [
                const Center(
                  child: Icon(Icons.add, size: 56, color: Colors.white),
                ),
                Positioned(
                  left: 12,
                  bottom: 12,
                  child: Text(
                    l10n.createNewAlbum,
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

class CreateAlbumScreen extends StatefulWidget {
  final Album? existing;

  const CreateAlbumScreen({super.key, this.existing});

  @override
  State<CreateAlbumScreen> createState() => _CreateAlbumScreenState();
}

class _CreateAlbumScreenState extends State<CreateAlbumScreen> {
  late final TextEditingController titleCtrl;
  AlbumImageRef? cover;
  late List<AlbumImageRef> items;

  bool get isEdit => widget.existing != null;

  bool get canSave => titleCtrl.text.trim().isNotEmpty && items.isNotEmpty;

  @override
  void initState() {
    super.initState();
    titleCtrl = TextEditingController(text: widget.existing?.title);
    cover = widget.existing?.cover;
    items = List<AlbumImageRef>.from(
      widget.existing?.imagesPathList ?? const [],
    );
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (titleCtrl.text.isEmpty && !isEdit) {
      titleCtrl.text = AppLocalizations.of(context)!.defaultAlbumName;
    }
  }

  @override
  void dispose() {
    titleCtrl.dispose();
    super.dispose();
  }

  Future<void> _pickCover() async {
    final l10n = AppLocalizations.of(context)!;
    final choice = await showModalBottomSheet<String>(
      context: context,
      backgroundColor: Colors.black,
      builder: (_) => SafeArea(
        child: Wrap(
          children: [
            ListTile(
              leading: const Icon(Icons.image, color: Colors.white),
              title: Text(
                l10n.chooseFromAssets,
                style: const TextStyle(color: Colors.white),
              ),
              onTap: () => Navigator.pop(context, 'assets'),
            ),
            ListTile(
              leading: const Icon(Icons.photo_library, color: Colors.white),
              title: Text(
                l10n.chooseFromGallery,
                style: const TextStyle(color: Colors.white),
              ),
              onTap: () => Navigator.pop(context, 'gallery'),
            ),
          ],
        ),
      ),
    );

    if (!mounted || choice == null) return;

    if (choice == 'assets') {
      final selected = await Navigator.push<List<String>>(
        context,
        MaterialPageRoute(
          builder: (_) => const PickAssetsScreen(singleSelect: true),
        ),
      );
      if (selected != null && selected.isNotEmpty) {
        setState(() => cover = AlbumImageRef.asset(selected.first));
      }
    } else {
      final picker = ImagePicker();
      final x = await picker.pickImage(source: ImageSource.gallery);
      if (x != null) {
        setState(() => cover = AlbumImageRef.file(x.path));
      }
    }
  }

  Future<void> _pickFromAssets() async {
    final selected = await Navigator.push<List<String>>(
      context,
      MaterialPageRoute(
        builder: (_) => const PickAssetsScreen(singleSelect: false),
      ),
    );
    if (selected == null) return;

    setState(() {
      items.addAll(selected.map(AlbumImageRef.asset));
      // если обложки ещё нет, поставим первую добавленную
      cover ??= items.isNotEmpty ? items.first : null;
    });
  }

  Future<void> _pickFromGallery() async {
    final picker = ImagePicker();
    final picked = await picker.pickMultiImage();
    if (picked.isEmpty) return;

    setState(() {
      items.addAll(picked.map((x) => AlbumImageRef.file(x.path)));
      cover ??= items.isNotEmpty ? items.first : null;
    });
  }

  void _save() {
    if (!canSave) return;
    final album = Album(
      uuid: widget.existing?.uuid,
      title: titleCtrl.text.trim(),
      cover: cover!,
      imagesPathList: List.unmodifiable(items),
    );
    Navigator.pop(context, album);
  }

  Widget _coverWidget() {
    if (cover == null) {
      return Container(
        color: Colors.white12,
        child: const Icon(Icons.add_a_photo, color: Colors.white, size: 32),
      );
    }
    if (cover!.source == AlbumImageSource.asset) {
      return Image.asset(cover!.value, fit: BoxFit.cover);
    }
    return Image.file(File(cover!.value), fit: BoxFit.cover);
  }

  void _removeItem(AlbumImageRef ref) {
    setState(() {
      items.remove(ref);
      if (cover == ref) {
        cover = items.isNotEmpty ? items.first : null;
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
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
          l10n.newAlbum,
          style: TextStyle(
            color: Colors.grey.shade900,
            fontSize: 26,
            fontWeight: FontWeight.bold,
          ),
        ),
        iconTheme: const IconThemeData(color: Colors.black),
        actions: [
          IconButton(
            icon: Icon(
              Icons.check,
              size: 40,
              color: canSave ? Colors.white : Colors.grey,
            ),
            onPressed: canSave ? _save : null,
          ),
        ],
      ),
      body: Stack(
        children: [
          const NeuralNetworkWithBlurredCircles(),
          SafeArea(
            child: Column(
              children: [
                const SizedBox(height: 16),

                // верхняя строка: обложка и название
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 16),
                  child: Row(
                    children: [
                      GestureDetector(
                        onTap: _pickCover,
                        child: ClipRRect(
                          borderRadius: BorderRadius.circular(10),
                          child: SizedBox(
                            width: 72,
                            height: 72,
                            child: _coverWidget(),
                          ),
                        ),
                      ),
                      const SizedBox(width: 14),
                      Expanded(
                        child: TextField(
                          controller: titleCtrl,
                          style: const TextStyle(
                            color: Colors.white,
                            fontSize: 22,
                            fontWeight: FontWeight.w700,
                          ),
                          decoration: InputDecoration(
                            hintText: l10n.albumName,
                            hintStyle: TextStyle(
                              color: Colors.white.withOpacity(0.4),
                            ),
                            border: InputBorder.none,
                          ),
                          onChanged: (_) => setState(() {}),
                        ),
                      ),
                    ],
                  ),
                ),

                const SizedBox(height: 16),

                // превью
                if (items.isNotEmpty)
                  SizedBox(
                    height: 86,
                    child: ListView.separated(
                      padding: const EdgeInsets.symmetric(horizontal: 16),
                      scrollDirection: Axis.horizontal,
                      itemCount: items.length,
                      separatorBuilder: (_, __) => const SizedBox(width: 10),
                      itemBuilder: (_, i) {
                        final it = items[i];
                        final isCover = (cover == it);

                        return GestureDetector(
                          onTap: () => setState(() => cover = it),
                          child: Stack(
                            children: [
                              ClipRRect(
                                borderRadius: BorderRadius.circular(10),
                                child: SizedBox(
                                  width: 72,
                                  height: 72,
                                  child: it.source == AlbumImageSource.asset
                                      ? Image.asset(it.value, fit: BoxFit.cover)
                                      : Image.file(
                                          File(it.value),
                                          fit: BoxFit.cover,
                                        ),
                                ),
                              ),
                              Positioned(
                                right: 4,
                                top: 4,
                                child: GestureDetector(
                                  onTap: () => _removeItem(it),
                                  child: Container(
                                    padding: const EdgeInsets.all(4),
                                    decoration: BoxDecoration(
                                      color: Colors.black54,
                                      borderRadius: BorderRadius.circular(10),
                                    ),
                                    child: const Icon(
                                      Icons.close,
                                      size: 16,
                                      color: Colors.white,
                                    ),
                                  ),
                                ),
                              ),
                              if (isCover)
                                Positioned(
                                  left: 4,
                                  bottom: 4,
                                  child: Container(
                                    padding: const EdgeInsets.symmetric(
                                      horizontal: 6,
                                      vertical: 2,
                                    ),
                                    decoration: BoxDecoration(
                                      color: Colors.deepPurple,
                                      borderRadius: BorderRadius.circular(8),
                                    ),
                                    child: Text(
                                      l10n.cover,
                                      style: const TextStyle(
                                        color: Colors.white,
                                        fontSize: 11,
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

                const Spacer(),

                // две кнопки снизу
                Padding(
                  padding: const EdgeInsets.fromLTRB(16, 12, 16, 20),
                  child: Row(
                    children: [
                      Expanded(
                        child: ElevatedButton.icon(
                          onPressed: _pickFromAssets,
                          icon: const Icon(Icons.folder),
                          label: Text(
                            l10n.basicImages,
                            textAlign: TextAlign.center,
                            style: const TextStyle(fontSize: 20),
                          ),
                          style: ElevatedButton.styleFrom(
                            backgroundColor: Colors.white12,
                            foregroundColor: Colors.white,
                            padding: const EdgeInsets.symmetric(vertical: 20),
                            shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(14),
                            ),
                          ),
                        ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: ElevatedButton.icon(
                          onPressed: _pickFromGallery,
                          icon: const Icon(Icons.photo_library),
                          label: Text(
                            l10n.gallery,
                            textAlign: TextAlign.center,
                            style: const TextStyle(fontSize: 20),
                          ),
                          style: ElevatedButton.styleFrom(
                            backgroundColor: Colors.deepPurple,
                            foregroundColor: Colors.white,
                            padding: const EdgeInsets.symmetric(vertical: 20),
                            shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(14),
                            ),
                          ),
                        ),
                      ),
                    ],
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

class PickAssetsScreen extends StatefulWidget {
  final bool singleSelect; // для обложки: true
  const PickAssetsScreen({super.key, required this.singleSelect});

  @override
  State<PickAssetsScreen> createState() => _PickAssetsScreenState();
}

class _PickAssetsScreenState extends State<PickAssetsScreen> {
  final List<String> assets = [];
  final Set<String> selected = {};
  Map<String, dynamic> _manifestMap = {};

  @override
  void initState() {
    super.initState();
    _loadAssetImages();
  }

  void _done() {
    Navigator.pop(context, selected.toList());
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
    final l10n = AppLocalizations.of(context)!;
    final List<String> assets = _manifestMap.keys
        .where(
          (String key) =>
              key.startsWith('assets/gallery_images/') &&
              key.toLowerCase().endsWith('.jpg'),
        )
        .toList();

    final canDone = selected.isNotEmpty;

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
        /*
        title: Text(
          l10n.chooseFromBasicImages,
          style: TextStyle(
            color: Colors.grey.shade900,
            fontSize: 26,
            fontWeight: FontWeight.bold,
          ),
        ),
         */
        iconTheme: const IconThemeData(color: Colors.black),
        actions: [
          IconButton(
            icon: Icon(
              Icons.check,
              size: 40,
              color: canDone ? Colors.white : Colors.grey,
            ),
            onPressed: canDone ? _done : null,
          ),
        ],
      ),
      body: Stack(
        children: [
          const NeuralNetworkWithBlurredCircles(),
          GridView.builder(
            padding: const EdgeInsets.all(12),
            gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
              crossAxisCount: 3,
              mainAxisSpacing: 8,
              crossAxisSpacing: 8,
            ),
            itemCount: assets.length,
            itemBuilder: (ctx, i) {
              final path = assets[i];
              final isSel = selected.contains(path);

              return GestureDetector(
                onTap: () {
                  setState(() {
                    if (widget.singleSelect) {
                      selected.clear();
                      selected.add(path);
                    } else {
                      if (isSel) {
                        selected.remove(path);
                      } else {
                        selected.add(path);
                      }
                    }
                  });
                },
                child: Stack(
                  children: [
                    Positioned.fill(
                      child: ClipRRect(
                        borderRadius: BorderRadius.circular(12),
                        child: Image.asset(path, fit: BoxFit.cover),
                      ),
                    ),
                    if (isSel)
                      Container(
                        decoration: BoxDecoration(
                          color: Colors.deepPurple.withOpacity(0.4),
                          borderRadius: BorderRadius.circular(12),
                          border: Border.all(color: Colors.white, width: 3),
                        ),
                        child: const Center(
                          child: Icon(
                            Icons.check,
                            color: Colors.white,
                            size: 40,
                          ),
                        ),
                      ),
                  ],
                ),
              );
            },
          ),
        ],
      ),
    );
  }
}
