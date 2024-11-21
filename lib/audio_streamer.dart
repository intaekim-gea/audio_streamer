import 'dart:async';

import 'package:flutter/services.dart';

import 'audio_streamer_platform_interface.dart';

class AudioStreamer {
  AudioStreamer._();

  static final AudioStreamer instance = AudioStreamer._();
  Future<void> startRecording(
      [int recordingMode = 7, int sampleRate = 16000]) async {
    try {
      await AudioStreamerPlatform.instance.startRecording(
        recordingMode,
        sampleRate,
      );
    } on PlatformException {
      throw 'Failed to start audio stream.';
    }
  }

  Future<void> stopRecording() async {
    try {
      await AudioStreamerPlatform.instance.stopRecording();
    } on PlatformException {
      throw 'Failed to stop audio stream.';
    }
  }

  Stream<List<int>> get audioStream =>
      AudioStreamerPlatform.instance.audioStream;
}
