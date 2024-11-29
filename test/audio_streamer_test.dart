import 'package:flutter_test/flutter_test.dart';
import 'package:audio_streamer/audio_streamer.dart';
import 'package:audio_streamer/audio_streamer_platform_interface.dart';
import 'package:audio_streamer/audio_streamer_method_channel.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

class MockAudioStreamerPlatform
    with MockPlatformInterfaceMixin
    implements AudioStreamerPlatform {
  @override
  Stream<List<int>> get audioStream => throw UnimplementedError();

  @override
  Future<void> startRecording(
    int recordingMode,
    int sampleRate,
    double amplificationFactor,
  ) =>
      throw UnimplementedError();

  @override
  Future<void> stopRecording() => throw UnimplementedError();
}

void main() {
  final AudioStreamerPlatform initialPlatform = AudioStreamerPlatform.instance;

  test('$MethodChannelAudioStreamer is the default instance', () {
    expect(initialPlatform, isInstanceOf<MethodChannelAudioStreamer>());
  });

  test('getPlatformVersion', () async {
    // ignore: unused_local_variable
    AudioStreamer audioStreamerPlugin = AudioStreamer.instance;
    MockAudioStreamerPlatform fakePlatform = MockAudioStreamerPlatform();
    AudioStreamerPlatform.instance = fakePlatform;

    // expect(await audioStreamerPlugin.getPlatformVersion(), '42');
  });
}
