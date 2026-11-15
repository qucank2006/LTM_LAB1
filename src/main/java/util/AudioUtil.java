package util;

import javax.sound.sampled.*;
import java.io.*;

public class AudioUtil {
    private static TargetDataLine line;
    private static ByteArrayOutputStream audioStream;
    private static volatile boolean isRecording = false;
    private static Thread recordThread;

    public static AudioFormat getAudioFormat() {
        // 16kHz, 16bit, Mono, Signed, LittleEndian
        return new AudioFormat(16000, 16, 1, true, false);
    }

    public static synchronized void startRecording() throws LineUnavailableException {
        if (isRecording) return;
        AudioFormat format = getAudioFormat();
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);

        if (!AudioSystem.isLineSupported(info)) {
            throw new LineUnavailableException("Micro không được hỗ trợ.");
        }

        line = (TargetDataLine) AudioSystem.getLine(info);
        line.open(format);
        line.start();
        isRecording = true;
        audioStream = new ByteArrayOutputStream();

        TargetDataLine recordingLine = line;
        ByteArrayOutputStream recordingStream = audioStream;
        recordThread = new Thread(() -> {
            byte[] buffer = new byte[1024];
            try {
                while (isRecording && recordingLine.isOpen()) {
                    int count = recordingLine.read(buffer, 0, buffer.length);
                    if (count > 0) recordingStream.write(buffer, 0, count);
                }
            } catch (RuntimeException ignored) { /* Device closed or removed. */ }
        });
        recordThread.setDaemon(true);
        recordThread.start();
    }

    public static synchronized byte[] stopRecording() {
        if (!isRecording) {
            return new byte[0];
        }
        isRecording = false;
        if (line != null) {
            line.stop();
            line.close();
        }

        if (recordThread != null) {
            try {
                recordThread.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        try {
            byte[] rawPcm = audioStream.toByteArray();
            if (rawPcm.length == 0) {
                return new byte[0];
            }
            // Đóng gói thành chuẩn file WAV
            AudioFormat format = getAudioFormat();
            ByteArrayInputStream bais = new ByteArrayInputStream(rawPcm);
            AudioInputStream ais = new AudioInputStream(bais, format, rawPcm.length / format.getFrameSize());
            ByteArrayOutputStream wavOut = new ByteArrayOutputStream();
            AudioSystem.write(ais, AudioFileFormat.Type.WAVE, wavOut);
            return wavOut.toByteArray();
        } catch (Exception e) {
            System.err.println("[Audio] Lỗi chuyển đổi WAV: " + e.getMessage());
            return new byte[0];
        }
    }

    public static void playAudio(byte[] wavBytes) {
        if (wavBytes == null || wavBytes.length == 0) return;
        new Thread(() -> {
            try {
                try (ByteArrayInputStream bais = new ByteArrayInputStream(wavBytes);
                     AudioInputStream ais = AudioSystem.getAudioInputStream(bais)) {
                    Clip clip = AudioSystem.getClip();
                    clip.addLineListener(event -> {
                        if (event.getType() == LineEvent.Type.STOP) clip.close();
                    });
                    clip.open(ais);
                    clip.start();
                }
            } catch (Exception e) {
                System.err.println("[Audio] Lỗi phát âm thanh: " + e.getMessage());
            }
        }).start();
    }

    // Tạo âm thanh mẫu (tiếng beep/voice tone) dự phòng nếu máy tính không cắm micro thật
    public static byte[] generateSampleTone() {
        try {
            AudioFormat format = getAudioFormat();
            int sampleRate = 16000;
            int durationSeconds = 2;
            int totalSamples = sampleRate * durationSeconds;
            byte[] pcmData = new byte[totalSamples * 2];

            for (int i = 0; i < totalSamples; i++) {
                double angle = 2.0 * Math.PI * i / (sampleRate / 440.0); // 440Hz nốt La (A4)
                short sample = (short) (Math.sin(angle) * 16000);
                pcmData[2 * i] = (byte) (sample & 0xFF);
                pcmData[2 * i + 1] = (byte) ((sample >> 8) & 0xFF);
            }

            ByteArrayInputStream bais = new ByteArrayInputStream(pcmData);
            AudioInputStream ais = new AudioInputStream(bais, format, totalSamples);
            ByteArrayOutputStream wavOut = new ByteArrayOutputStream();
            AudioSystem.write(ais, AudioFileFormat.Type.WAVE, wavOut);
            return wavOut.toByteArray();
        } catch (Exception e) {
            return new byte[0];
        }
    }
}
