package com.keran.radbroadcast.client;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.SourceDataLine;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 广播播放器：后台线程拉取音频直链并用 JLayer 解码到系统音频输出。
 * 新指令到达时打断当前播放、立刻切换；同一时刻只播一路。
 */
public enum RadioPlayer {
	INSTANCE;

	private final HttpClient http = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();

	private volatile Thread playThread;
	private volatile boolean stopRequested;

	public void play(String url) {
		stop();
		Thread t = new Thread(() -> runPlay(url), "radbroadcast-audio");
		t.setDaemon(true);
		playThread = t;
		t.start();
	}

	public void stop() {
		stopRequested = true;
		Thread t = playThread;
		if (t != null) {
			try { t.join(1200); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
		}
		playThread = null;
		stopRequested = false;
	}

	private void runPlay(String url) {
		try {
			HttpRequest req = HttpRequest.newBuilder(URI.create(url))
					.header("User-Agent", "RadBroadcastClient/1.0")
					.header("Accept", "*/*")
					.timeout(Duration.ofSeconds(15))
					.GET().build();
			HttpResponse<InputStream> resp = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
			if (resp.statusCode() / 100 != 2) return;
			decode(resp.body());
		} catch (Exception ignored) {
		}
	}

	private void decode(InputStream in) {
		Bitstream bitstream = new Bitstream(in);
		Decoder decoder = new Decoder();
		SourceDataLine line = null;
		byte[] pcm = new byte[8192];
		try {
			while (!stopRequested) {
				Header h = bitstream.readFrame();
				if (h == null) break;
				if (line == null) {
					int channels = h.mode() == Header.SINGLE_CHANNEL ? 1 : 2;
					AudioFormat fmt = new AudioFormat(h.frequency(), 16, channels, true, false);
					line = AudioSystem.getSourceDataLine(fmt);
					line.open(fmt);
					try {
						FloatControl gain = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
						gain.setValue(Math.max(gain.getMinimum(), Math.min(0f, gain.getMaximum())));
					} catch (Exception ignored) {
					}
					line.start();
				}
				SampleBuffer sb = (SampleBuffer) decoder.decodeFrame(h, bitstream);
				short[] samples = sb.getBuffer();
				int len = sb.getBufferLength();
				if (pcm.length < len * 2) pcm = new byte[len * 2];
				for (int i = 0; i < len; i++) {
					short s = samples[i];
					pcm[i * 2] = (byte) (s & 0xff);
					pcm[i * 2 + 1] = (byte) ((s >> 8) & 0xff);
				}
				line.write(pcm, 0, len * 2);
				bitstream.closeFrame();
			}
		} catch (Exception ignored) {
		} finally {
			if (line != null) { line.drain(); line.stop(); line.close(); }
			try { bitstream.close(); } catch (Exception ignored) {}
			try { in.close(); } catch (Exception ignored) {}
		}
	}
}
