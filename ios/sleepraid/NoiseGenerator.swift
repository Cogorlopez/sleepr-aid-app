import AVFoundation

enum NoiseType: String, CaseIterable, Identifiable {
    case white = "White Noise"
    case pink  = "Pink Noise"
    case brown = "Brown Noise"
    case green = "Green Noise"
    var id: Self { self }
}

class NoiseGenerator: ObservableObject {

    private let engine = AVAudioEngine()
    private var sourceNode: AVAudioSourceNode?

    @Published var isPlaying = false
    var noiseType: NoiseType = .pink

    private var pauseOtherAudio: Bool = false
    private var interruptionObserver: NSObjectProtocol?

    private var volume: Float = 0.7

    // Pink noise IIR state (Paul Kellet algorithm) — written only on the audio thread
    private var b0: Float = 0, b1: Float = 0, b2: Float = 0
    private var b3: Float = 0, b4: Float = 0, b5: Float = 0, b6: Float = 0

    // Brown noise integrator — written only on the audio thread
    private var lastBrown: Float = 0

    // Green noise biquad bandpass filter state (f0=500Hz, Q=1.5, fs=44100)
    private var gx1: Float = 0, gx2: Float = 0
    private var gy1: Float = 0, gy2: Float = 0

    private let whiteGain: Float = 0.25
    private let pinkGain:  Float = 0.85
    private let brownGain: Float = 0.95
    private let greenGain: Float = 0.85  // bandpass output is low-amplitude; boost to match loudness

    func start(type: NoiseType = .pink, pauseOtherAudio: Bool = false) {
        stop()
        noiseType = type

        let format = AVAudioFormat(standardFormatWithSampleRate: 44100, channels: 1)!

        sourceNode = AVAudioSourceNode(format: format) { [weak self] _, _, frameCount, audioBufferList in
            guard let self else { return noErr }
            let abList = UnsafeMutableAudioBufferListPointer(audioBufferList)
            guard let data = abList[0].mData?.assumingMemoryBound(to: Float.self) else { return noErr }
            let type = self.noiseType
            let vol  = self.volume
            for i in 0..<Int(frameCount) {
                data[i] = self.nextSample(type: type) * vol
            }
            return noErr
        }

        let mixer = engine.mainMixerNode
        engine.attach(sourceNode!)
        engine.connect(sourceNode!, to: mixer, format: format)

        do {
            self.pauseOtherAudio = pauseOtherAudio
            if pauseOtherAudio {
                try AVAudioSession.sharedInstance().setCategory(.playback)
                interruptionObserver = NotificationCenter.default.addObserver(
                    forName: AVAudioSession.interruptionNotification,
                    object: AVAudioSession.sharedInstance(),
                    queue: .main
                ) { [weak self] notification in
                    guard let self,
                          let typeValue = notification.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt,
                          let type = AVAudioSession.InterruptionType(rawValue: typeValue)
                    else { return }
                    if type == .began { self.stop() }
                    // .ended intentionally ignored — user must tap Play again
                }
            } else {
                try AVAudioSession.sharedInstance().setCategory(.playback, options: .mixWithOthers)
            }
            try AVAudioSession.sharedInstance().setActive(true)
            try engine.start()
            DispatchQueue.main.async { self.isPlaying = true }
        } catch {
            stop()
        }
    }

    func stop() {
        if let token = interruptionObserver {
            NotificationCenter.default.removeObserver(token)
            interruptionObserver = nil
        }
        engine.stop()
        if let node = sourceNode {
            engine.detach(node)
            sourceNode = nil
        }
        b0 = 0; b1 = 0; b2 = 0; b3 = 0; b4 = 0; b5 = 0; b6 = 0
        lastBrown = 0
        gx1 = 0; gx2 = 0; gy1 = 0; gy2 = 0
        if pauseOtherAudio {
            try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
        }
        DispatchQueue.main.async { self.isPlaying = false }
    }

    func setVolume(_ v: Float) {
        volume = v
    }

    private func nextSample(type: NoiseType) -> Float {
        switch type {
        case .white:
            return Float.random(in: -1...1) * whiteGain

        case .pink:
            let w = Float.random(in: -1...1)
            b0 = 0.99886 * b0 + w * 0.0555179
            b1 = 0.99332 * b1 + w * 0.0750759
            b2 = 0.96900 * b2 + w * 0.1538520
            b3 = 0.86650 * b3 + w * 0.3104856
            b4 = 0.55000 * b4 + w * 0.5329522
            b5 = -0.7616  * b5 - w * 0.0168980
            let pink = (b0 + b1 + b2 + b3 + b4 + b5 + b6 + w * 0.5362) * 0.11
            b6 = w * 0.115926
            return min(1, max(-1, pink)) * pinkGain

        case .brown:
            let w = Float.random(in: -1...1)
            lastBrown = min(1, max(-1, lastBrown + w * 0.02))
            return lastBrown * brownGain

        case .green:
            // Biquad bandpass centered at 500 Hz (f0=500, Q=1.5, fs=44100).
            // Coefficients: b0=0.034771, b1=0, b2=-0.034771, a1=-1.948953, a2=0.953660
            let w = Float.random(in: -1...1)
            let y = 0.034771 * w - 0.034771 * gx2 + 1.948953 * gy1 - 0.953660 * gy2
            gx2 = gx1; gx1 = w
            gy2 = gy1; gy1 = y
            return min(1, max(-1, y)) * greenGain
        }
    }
}
