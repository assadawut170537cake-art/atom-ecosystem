import 'package:flutter/material.dart';

import '../core/config.dart';
import '../services/agent_state.dart';
import '../services/master_gate.dart';
import '../services/pipeline_coordinator.dart';
import '../widgets/agent_orb.dart';
import 'chat_screen.dart';

// OLED black home: interactive orb + global voice brain + settings.
class HomeScreen extends StatefulWidget {
  final AgentState agents;
  final Future<bool> Function() askMasterKey;
  final void Function(int) go;
  const HomeScreen({
    super.key,
    required this.agents,
    required this.askMasterKey,
    required this.go,
  });

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  String _lastTranscript = 'แตะที่ลูกแก้ว หรือปุ่มไมค์ด้านล่างเพื่อเริ่มพูด';

  Future<void> _handleVoiceResult(String text) async {
    if (!mounted) return;
    setState(() => _lastTranscript = 'คุณ: "$text"');

    final api = widget.agents.api;
    final reply = _localReply(text);

    try {
      if (api != null && api.secret.isNotEmpty) {
        final session = 'mobile-${widget.agents.agentId}';
        await api.chatAppend(session, 'user', text);
        await api.chatAppend(session, 'assistant', reply);
      }
    } catch (_) {}

    if (!mounted) return;
    setState(() => _lastTranscript = '${widget.agents.agent.name}: "$reply"');
    await widget.agents.voice.speak(reply);
  }

  String _localReply(String t) {
    switch (widget.agents.agentId) {
      case 'friday':
        return 'รับทราบค่ะลูกพี่ บันทึก "$t" เรียบร้อยแล้วค่ะ';
      case 'ultron':
        return 'รับคำสั่ง จะลุยให้ แต่ขั้นแตะระบบจริงต้องกดอนุมัติบนมือถือก่อน';
      default:
        return 'อะตอมรับเรื่องแล้วครับลูกพี่ "$t"';
    }
  }

  Future<void> _switch(BuildContext ctx, String id) async {
    final messenger = ScaffoldMessenger.of(ctx);
    if (id == 'ultron') {
      if (!await MasterGate.hasPin()) {
        if (!ctx.mounted) return;
        await _setupPin(ctx);
        return;
      }
      final ok = await widget.askMasterKey();
      if (!ok) {
        messenger.showSnackBar(
          const SnackBar(content: Text('รหัสมาสเตอร์ไม่ถูกต้อง')),
        );
        return;
      }
    }
    await widget.agents.switchAgent(id);
  }

  Future<void> _setupPin(BuildContext ctx) async {
    final messenger = ScaffoldMessenger.of(ctx);
    final c = TextEditingController();
    final pin = await showDialog<String>(
      context: ctx,
      builder: (_) => AlertDialog(
        backgroundColor: const Color(0xFF141414),
        title: const Text(
          'ตั้งรหัส Master Key',
          style: TextStyle(color: Colors.white),
        ),
        content: TextField(
          controller: c,
          obscureText: true,
          keyboardType: TextInputType.number,
          style: const TextStyle(color: Colors.white),
          decoration: const InputDecoration(hintText: 'ตั้งรหัส 4-8 หลัก'),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('ยกเลิก'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(ctx, c.text.trim()),
            child: const Text('บันทึก'),
          ),
        ],
      ),
    );
    if (pin != null && pin.length >= 4) {
      await MasterGate.setPin(pin);
      messenger.showSnackBar(
        const SnackBar(content: Text('ตั้งรหัส Master Key แล้ว')),
      );
    }
  }

  Future<void> _setupConnection(BuildContext ctx) async {
    final urlController = TextEditingController(text: widget.agents.baseUrl);
    final secretController = TextEditingController(text: widget.agents.secret);
    final messenger = ScaffoldMessenger.of(ctx);

    final saved = await showDialog<bool>(
      context: ctx,
      builder: (_) => AlertDialog(
        backgroundColor: const Color(0xFF141414),
        title: const Text(
          'ตั้งค่าการเชื่อมต่อ Cloud',
          style: TextStyle(color: Colors.white),
        ),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(
              controller: urlController,
              style: const TextStyle(color: Colors.white),
              decoration: const InputDecoration(
                labelText: 'Cloud URL',
                labelStyle: TextStyle(color: Colors.white70),
                hintText: 'https://assadawut-jarvis.online',
              ),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: secretController,
              obscureText: true,
              style: const TextStyle(color: Colors.white),
              decoration: const InputDecoration(
                labelText: 'ATOM Secret (X-Atom-Secret)',
                labelStyle: TextStyle(color: Colors.white70),
                hintText: 'ใส่คีย์ความลับ',
              ),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('ยกเลิก'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('บันทึก'),
          ),
        ],
      ),
    );

    if (saved == true) {
      await widget.agents.updateConnection(
        urlController.text.trim(),
        secretController.text.trim(),
      );
      messenger.showSnackBar(
        const SnackBar(content: Text('บันทึกการตั้งค่าการเชื่อมต่อแล้ว')),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final a = widget.agents.agent;
    final voice = widget.agents.voice;
    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(
        backgroundColor: Colors.black,
        title: Text(a.name, style: TextStyle(color: a.color)),
        actions: [
          IconButton(
            tooltip: 'ตั้งค่าการเชื่อมต่อ Cloud',
            icon: const Icon(Icons.settings, color: Colors.white),
            onPressed: () => _setupConnection(context),
          ),
          IconButton(
            tooltip: widget.agents.muted ? 'เปิดเสียง' : 'Emergency Mute',
            icon: Icon(
              widget.agents.muted ? Icons.volume_off : Icons.volume_up,
              color: widget.agents.muted ? Colors.redAccent : Colors.white,
            ),
            onPressed: () => widget.agents.setMuted(!widget.agents.muted),
          ),
        ],
      ),
      body: ListenableBuilder(
        listenable: widget.agents,
        builder: (_, _) => Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(24),
            child: Column(
              children: [
                // Tappable Agent Orb!
                GestureDetector(
                  onTap: () => voice.toggleListen(
                    onResult: _handleVoiceResult,
                    askMasterKey: widget.askMasterKey,
                  ),
                  child: ListenableBuilder(
                    listenable: voice,
                    builder: (_, _) => AgentOrb(
                      agent: widget.agents.agent,
                      speaking: widget.agents.speaking,
                      muted: widget.agents.muted,
                    ),
                  ),
                ),
                const SizedBox(height: 16),

                // Voice status indicator
                ListenableBuilder(
                  listenable: voice,
                  builder: (_, _) {
                    if (voice.listening) {
                      return Container(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 12,
                          vertical: 6,
                        ),
                        decoration: BoxDecoration(
                          color: Colors.red.withValues(alpha: 0.2),
                          borderRadius: BorderRadius.circular(16),
                          border: Border.all(color: Colors.redAccent),
                        ),
                        child: const Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Icon(Icons.mic, color: Colors.redAccent, size: 16),
                            SizedBox(width: 6),
                            Text(
                              'กำลังฟังเสียงของคุณ...',
                              style: TextStyle(
                                color: Colors.redAccent,
                                fontSize: 13,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                          ],
                        ),
                      );
                    }
                    if (widget.agents.speaking) {
                      return Container(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 12,
                          vertical: 6,
                        ),
                        decoration: BoxDecoration(
                          color: a.color.withValues(alpha: 0.2),
                          borderRadius: BorderRadius.circular(16),
                          border: Border.all(color: a.color),
                        ),
                        child: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Icon(Icons.volume_up, color: a.color, size: 16),
                            const SizedBox(width: 6),
                            Text(
                              '${a.name} กำลังพูด...',
                              style: TextStyle(
                                color: a.color,
                                fontSize: 13,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                          ],
                        ),
                      );
                    }
                    return Text(
                      '⚡ แตะลูกแก้ว Orb หรือปุ่มไมค์เพื่อสั่งงานด้วยเสียง',
                      style: TextStyle(
                        color: Colors.white.withValues(alpha: 0.6),
                        fontSize: 12,
                      ),
                    );
                  },
                ),

                const SizedBox(height: 16),

                // Live transcript card
                GestureDetector(
                  onLongPress: () async {
                    if (_lastTranscript.contains('แตะที่ลูกแก้ว')) return;
                    final pipeline = PipelineCoordinator();
                    final success = await pipeline.ingestMemory(
                      _lastTranscript,
                      customId: 'voice_${DateTime.now().millisecondsSinceEpoch}',
                    );
                    if (context.mounted) {
                      ScaffoldMessenger.of(context).showSnackBar(
                        SnackBar(
                          content: Text(success 
                              ? '✅ บันทึกคำสั่งเสียงลง Supermemory สำเร็จ!' 
                              : '❌ ไม่สามารถบันทึกความจำได้ (ตรวจสอบ API Key)'),
                        ),
                      );
                    }
                  },
                  child: Container(
                    width: double.infinity,
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(
                      color: const Color(0xFF141414),
                      borderRadius: BorderRadius.circular(12),
                      border: Border.all(color: Colors.white12),
                    ),
                    child: Text(
                      _lastTranscript,
                      textAlign: TextAlign.center,
                      style: const TextStyle(color: Colors.white, fontSize: 14),
                    ),
                  ),
                ),

                const SizedBox(height: 20),
                Wrap(
                  spacing: 8,
                  alignment: WrapAlignment.center,
                  children: [
                    for (final ag in AppConfig.agents)
                      ChoiceChip(
                        label: Text(ag.name),
                        selected: widget.agents.agentId == ag.id,
                        selectedColor: ag.color.withValues(alpha: 0.35),
                        onSelected: (_) => _switch(context, ag.id),
                      ),
                  ],
                ),
                const SizedBox(height: 12),
                const Text(
                  'พูดว่า "ต่อสายไฟรเดย์" / "ต่อสายอัลตรอน" / "ตัดสายกลับมาอะตอม"',
                  textAlign: TextAlign.center,
                  style: TextStyle(color: Colors.white54, fontSize: 12),
                ),
                const SizedBox(height: 20),
                Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    ListenableBuilder(
                      listenable: voice,
                      builder: (_, _) => FloatingActionButton.extended(
                        backgroundColor: voice.listening
                            ? Colors.redAccent
                            : a.color,
                        onPressed: () => voice.toggleListen(
                          onResult: _handleVoiceResult,
                          askMasterKey: widget.askMasterKey,
                        ),
                        icon: Icon(
                          voice.listening ? Icons.mic_off : Icons.mic,
                          color: Colors.black,
                        ),
                        label: Text(
                          voice.listening ? 'กำลังฟัง...' : 'กดเพื่อพูด',
                          style: const TextStyle(
                            color: Colors.black,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                      ),
                    ),
                    const SizedBox(width: 12),
                    FilledButton.icon(
                      onPressed: () => Navigator.push(
                        context,
                        MaterialPageRoute(
                          builder: (_) => ChatScreen(
                            agents: widget.agents,
                            askMasterKey: widget.askMasterKey,
                          ),
                        ),
                      ),
                      icon: const Icon(Icons.chat),
                      label: const Text('แชทพิมพ์'),
                    ),
                  ],
                ),
                if (widget.agents.error != null) ...[
                  const SizedBox(height: 12),
                  Text(
                    widget.agents.error!,
                    style: const TextStyle(color: Colors.redAccent),
                  ),
                ],
              ],
            ),
          ),
        ),
      ),
    );
  }
}
