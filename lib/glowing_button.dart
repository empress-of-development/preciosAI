import 'package:flutter/material.dart';

class RippleCircleButton extends StatefulWidget {
  final VoidCallback onTap;
  final String iconPath;

  const RippleCircleButton({
    super.key,
    required this.onTap,
    required this.iconPath,
  });

  @override
  State<RippleCircleButton> createState() => _RippleCircleButtonState();
}

class _RippleCircleButtonState extends State<RippleCircleButton>
    with SingleTickerProviderStateMixin {
  late AnimationController _controller;
  late Animation<double> _ripple;

  @override
  void initState() {
    super.initState();

    _controller = AnimationController(
      vsync: this,
      duration: const Duration(seconds: 2),
    )..repeat();

    _ripple = Tween<double>(
      begin: 0.0,
      end: 1.0,
    ).animate(CurvedAnimation(parent: _controller, curve: Curves.easeOut));
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: 100,
      height: 100,
      child: Stack(
        alignment: Alignment.center,
        children: [
          AnimatedBuilder(
            animation: _controller,
            builder: (_, __) {
              return CustomPaint(
                painter: RipplePainter(progress: _ripple.value),
                child: const SizedBox.expand(),
              );
            },
          ),

          GestureDetector(
            onTap: widget.onTap,
            child: Container(
              width: 70,
              height: 70,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: Colors.white.withOpacity(0.5),
                border: Border.all(
                  color: Colors.indigo.withOpacity(0.8),
                  width: 2,
                ),
              ),
              padding: const EdgeInsets.all(12),
              child: Image.asset(widget.iconPath),
            ),
          ),
        ],
      ),
    );
  }
}

class RipplePainter extends CustomPainter {
  final double progress;

  RipplePainter({required this.progress});

  @override
  void paint(Canvas canvas, Size size) {
    final center = size.center(Offset.zero);

    final paint = Paint()
      ..color = Colors.white.withOpacity((1 - progress) * 1)
      ..style = PaintingStyle.stroke
      ..strokeWidth = 20;

    final double maxRadius = size.width * 0.35;

    canvas.drawCircle(center, progress * maxRadius, paint);
  }

  @override
  bool shouldRepaint(covariant RipplePainter oldDelegate) => true;
}

class PulsatingIconButton extends StatefulWidget {
  final IconData icon;
  final VoidCallback onPressed;
  final double size;
  final Color color;

  const PulsatingIconButton({
    super.key,
    required this.icon,
    required this.onPressed,
    this.size = 40,
    this.color = Colors.black,
  });

  @override
  State<PulsatingIconButton> createState() => _PulsatingIconButtonState();
}

class _PulsatingIconButtonState extends State<PulsatingIconButton>
    with SingleTickerProviderStateMixin {
  late AnimationController _controller;
  late Animation<double> _animation;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      duration: const Duration(milliseconds: 1200),
      vsync: this,
    )..repeat(reverse: true);

    _animation = Tween<double>(begin: 1.0, end: 1.15).animate(
      CurvedAnimation(parent: _controller, curve: Curves.easeInOut),
    );
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return ScaleTransition(
      scale: _animation,
      child: IconButton(
        icon: Icon(widget.icon, color: widget.color, size: widget.size),
        onPressed: widget.onPressed,
      ),
    );
  }
}
