import SwiftUI

struct ContentView: View {
    @StateObject private var generator = NoiseGenerator()
    @State private var volume: Double = 0.7
    @AppStorage("pauseOtherAudio") private var pauseOtherAudio: Bool = false

    private let accentOn = Color(red: 0.565, green: 0.792, blue: 0.976) // #90CAF9
    private let bg       = Color(red: 0.059, green: 0.059, blue: 0.059) // #0F0F0F

    private var accentColor: Color { generator.isPlaying ? accentOn : Color(white: 0.2) }

    var body: some View {
        ZStack {
            bg.ignoresSafeArea()

            VStack(spacing: 0) {
                HStack {
                    Text("Sleepr Aid")
                        .font(.system(size: 24, weight: .bold))
                        .foregroundColor(.white)
                    Spacer()
                }
                .padding(.horizontal, 24)
                .padding(.top, 16)

                Spacer()

                PowerButton(isOn: generator.isPlaying, accentColor: accentColor) {
                    if generator.isPlaying {
                        generator.stop()
                    } else {
                        generator.start(type: generator.noiseType, pauseOtherAudio: pauseOtherAudio)
                    }
                }

                Spacer()

                VolumeControl(volume: $volume, accentColor: accentOn) { v in
                    generator.setVolume(Float(v))
                }
                .padding(.horizontal, 24)

                Spacer().frame(height: 40)

                SoundSelector(selected: $generator.noiseType) { type in
                    if generator.isPlaying {
                        generator.stop()
                        generator.start(type: type, pauseOtherAudio: pauseOtherAudio)
                    }
                }
                .padding(.horizontal, 24)

                Spacer().frame(height: 12)

                HStack {
                    Text("Pause other audio")
                        .font(.system(size: 18))
                        .foregroundColor(.white.opacity(0.9))
                    Spacer()
                    Toggle("", isOn: $pauseOtherAudio)
                        .labelsHidden()
                        .tint(accentOn)
                }
                .padding(.horizontal, 20)
                .frame(maxWidth: .infinity)
                .frame(height: 64)
                .background(Color(white: 0.118))
                .cornerRadius(16)
                .padding(.horizontal, 24)

                Spacer().frame(height: 64)
            }
        }
        .preferredColorScheme(.dark)
        .onAppear {
            generator.setVolume(Float(volume))
        }
    }
}

struct PowerButton: View {
    let isOn: Bool
    let accentColor: Color
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            ZStack {
                if isOn {
                    Circle()
                        .fill(
                            RadialGradient(
                                colors: [accentColor.opacity(0.25), .clear],
                                center: .center, startRadius: 0, endRadius: 130
                            )
                        )
                        .frame(width: 260, height: 260)
                }

                Circle()
                    .fill(Color(white: 0.102))
                    .overlay(Circle().stroke(isOn ? accentColor.opacity(0.5) : Color(white: 0.145), lineWidth: 1))
                    .frame(width: 260, height: 260)

                Circle()
                    .fill(Color(white: 0.071))
                    .overlay(Circle().stroke(isOn ? accentColor.opacity(0.2) : Color(white: 0.039), lineWidth: 2))
                    .frame(width: 180, height: 180)

                Image(systemName: "power")
                    .font(.system(size: 72, weight: .thin))
                    .foregroundColor(accentColor)
            }
        }
        .buttonStyle(.plain)
    }
}

struct VolumeControl: View {
    @Binding var volume: Double
    let accentColor: Color
    let onChange: (Double) -> Void

    var body: some View {
        VStack(spacing: 12) {
            Text("\(Int(volume * 100))%")
                .font(.system(size: 16, weight: .medium))
                .foregroundColor(.white.opacity(0.9))

            HStack(spacing: 12) {
                Image(systemName: "speaker.fill")
                    .foregroundColor(.white.opacity(0.6))
                    .frame(width: 24)

                Slider(value: Binding(
                    get: { volume },
                    set: { v in volume = v; onChange(v) }
                ))
                .tint(accentColor)

                Image(systemName: "speaker.wave.3.fill")
                    .foregroundColor(.white.opacity(0.6))
                    .frame(width: 24)
            }
        }
    }
}

struct SoundSelector: View {
    @Binding var selected: NoiseType
    let onSelect: (NoiseType) -> Void

    var body: some View {
        Menu {
            ForEach(NoiseType.allCases) { type in
                Button(type.rawValue) {
                    selected = type
                    onSelect(type)
                }
            }
        } label: {
            HStack {
                Text(selected.rawValue)
                    .font(.system(size: 18))
                    .foregroundColor(.white.opacity(0.9))
                Spacer()
                Image(systemName: "chevron.down")
                    .foregroundColor(.white.opacity(0.6))
                    .font(.system(size: 20))
            }
            .padding(.horizontal, 20)
            .frame(maxWidth: .infinity)
            .frame(height: 64)
            .background(Color(white: 0.118))
            .cornerRadius(16)
        }
    }
}

#Preview {
    ContentView()
}
