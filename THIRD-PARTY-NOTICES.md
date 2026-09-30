# Third-party notices — Plasmo Musicshare 1.2.14

This release bundles TarsosDSP core 2.5, developed by Joren Six and contributors at IPEM, University Ghent.
Project: https://github.com/JorenSix/TarsosDSP
Source artifact: https://mvn.0110.be/releases/be/tarsos/dsp/core/2.5/core-2.5-sources.jar
Binary reference: https://mvn.0110.be/releases/be/tarsos/dsp/core/2.5/core-2.5.jar

The bundled core is compiled from the unmodified source artifact in `vendor/tarsos-core-2.5/be`. Its original copyright and license headers are preserved. The complete source artifact and reference binary are supplied in `vendor/downloads` in the source distribution. The project is distributed under GPL version 3 or later; see LICENSE. The integrated release is distributed under GPL-3.0-or-later. The earlier Musicshare code retains its MIT grant and copyright notice in LICENSE-MIT-legacy.

TarsosDSP PitchShifter credits Joren Six and Stephan M. Bernsee. TarsosDSP's README describes it as a translation of Bernsee's frequency-domain pitch-shifting algorithm originally released under the Wide Open License. Other algorithms in the core have their own preserved notices in the corresponding source files.

The supplied `bi.pcm` is user-provided audio converted from bi.mp3. Code licenses do not grant rights to this recording. The original file was provided for embedding in this mod; no third-party redistribution license for the recording is asserted here.

No TarsosDSP microphone dispatcher is started. Plasmo Voice continues to own audio devices, capture gating, encryption, server permissions and networking. Musicshare adapts PCM capture frames to TarsosDSP's overlapping windows and applies effects only to the microphone channel.

The voice panel uses TarsosDSP FlangerEffect, DelayEffect, PitchShifter, HighPass, LowPassFS, BandPass and WaveformSimilarityBasedOverlapAdd (WSOLA). Filter source headers credit Damien Di Fede (2007–2008) under GNU Library GPL version 2 or later; the original notices are preserved and the full version 2 text is included as LGPL-2.0.txt. WSOLA credits Joren Six and Olli Parviainen; see the unmodified source headers. The integrated work is distributed under GPL version 3 or later as stated above.
