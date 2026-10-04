import SwiftUI

struct PebbleOnboarding: View {
    var finish: () -> Void
    var connect: () -> Void
    @LeafState private var page = 0
    @AppStorage("profileName") private var name = ""
    @Environment(\.colorScheme) private var scheme
    @Environment(\.accessibilityReduceMotion) private var reducedMotion
    @Bindable private var accent = AccentPreference.shared
    let titles = ["Little thoughts.\nA little more room.", "Ideas worth keeping.\nPlans worth making.", "Make it feel\nlike your space.", "Your thoughts.\nYour pace."]
    let details = ["A calm home for notes, images, and everything you want to come back to.", "Collect inspiration, write with rich blocks, and turn your next steps into checklists and tasks.", "What should we call you? Your name stays on this Mac, and you can change it later.", "Pebble saves here first, even offline. Connect Google Drive when you want your notes on both devices."]
    func complete() { name = String(name.trimmingCharacters(in: .whitespacesAndNewlines).prefix(40)); UserDefaults.standard.set(true, forKey: "onboardingComplete"); finish() }
    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            HStack { Text("PEBBLE NOTES").font(.caption.weight(.semibold)).foregroundStyle(.secondary); Spacer(); Button("Skip") { complete() } }.frame(height: 36)
            HStack(spacing: 36) {
                artwork.frame(width: 280, height: 300).accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 18) {
                    Text(titles[page]).font(.system(size: 32, weight: .bold))
                    Text(details[page]).font(.system(size: 15)).foregroundStyle(.secondary).fixedSize(horizontal: false, vertical: true)
                    if page == 2 {
                        TextField("Your name · optional", text: $name).modifier(PebbleField()).frame(minHeight: 42).onChange(of: name) { _, next in if next.count > 40 { name = String(next.prefix(40)) } }
                        Text("Choose your accent").font(.caption).foregroundStyle(.secondary)
                        HStack { ForEach(AccentPreference.choices, id: \.self) { choice in Button { accent.name = choice } label: { Circle().fill(LeafPalette.color("Accents", choice)).frame(width: 24, height: 24).overlay { if accent.name == choice { Image(systemName: "checkmark").font(.system(size: 11, weight: .bold)).foregroundStyle(choice == "Yellow" || choice == "Green" || choice == "Orange" ? .black : .white) } }.frame(width: 38, height: 40) }.accessibilityLabel(choice + " accent") } }
                    }
                    if page == 3 { Button("Connect Google Drive") { complete(); connect() }; Text("Optional. No account is needed to start writing.").font(.caption).foregroundStyle(.secondary) }
                }.frame(width: 300, alignment: .leading)
            }.padding(.vertical, 12)
            HStack {
                ForEach(0..<4) { index in Capsule().fill(Color.primary.opacity(index == page ? 0.9 : 0.2)).frame(width: index == page ? 22 : 6, height: 6) }
                Spacer(); if page > 0 { Button("Back") { page -= 1 } }
                Button(page == 3 ? "Start using Pebble" : "Continue") { if page < 3 { page += 1 } else { complete() } }.keyboardShortcut(.defaultAction)
            }
        }.padding(30).buttonStyle(MaterialActionStyle()).animation(reducedMotion ? nil : .easeInOut(duration: 0.18), value: page)
    }
    var artwork: some View {
        ZStack {
            VStack(alignment: .leading, spacing: 14) {
                Image(systemName: page == 1 ? "checklist" : page == 2 ? "person.crop.circle" : page == 3 ? "checkmark.icloud" : "square.and.pencil").font(.system(size: 24)).foregroundStyle(.blue)
                Text(page == 1 ? "A lighter tomorrow" : page == 2 ? (name.isEmpty ? "Your space" : "Hello, " + name) : page == 3 ? "Yours, even offline" : "A little perspective").font(.system(size: 20, weight: .semibold))
                Text(page == 1 ? "✓ Make something small\n○ Collect a new idea\n○ Take a little pause" : "Keep the good things.\nMake room for new ones.").font(.system(size: 13)).lineSpacing(7).foregroundStyle(.secondary)
            }.padding(22).frame(width: 210, height: 210, alignment: .topLeading).background(.regularMaterial, in: RoundedRectangle(cornerRadius: 24)).overlay(RoundedRectangle(cornerRadius: 24).strokeBorder(Color.primary.opacity(0.08))).rotationEffect(.degrees(-9)).offset(x: -16, y: -20).shadow(color: .black.opacity(0.08), radius: 12, y: 8)
            VStack(alignment: .leading, spacing: 20) { Image(systemName: page == 1 ? "checkmark" : "photo").font(.system(size: 24)); Spacer(); Text(page == 1 ? "One step\nat a time" : "A spark of\ninspiration").font(.system(size: 18, weight: .semibold)) }.foregroundStyle(.white).padding(20).frame(width: 140, height: 160).background(LinearGradient(colors: [Color(red: 0.5, green: 0.68, blue: 0.96), Color(red: 0.7, green: 0.69, blue: 0.93), Color(red: 0.88, green: 0.77, blue: 0.67)], startPoint: .topLeading, endPoint: .bottomTrailing), in: RoundedRectangle(cornerRadius: 24)).rotationEffect(.degrees(8)).offset(x: 68, y: 65).shadow(color: .black.opacity(0.06), radius: 12, y: 8)
        }
    }
}
