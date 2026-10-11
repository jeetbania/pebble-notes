import SwiftUI
import AppKit

struct PebbleRelease: Codable, Identifiable {
    var version: String; var title: String; var changes: [String]
    var id: String { version }
}
enum ReleaseNotes {
    static var version: String { Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "0.8.0" }
    static var identity: String { version + ":" + (Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "8") }
    static let seenKey = "acknowledgedRelease"
    static var claimed = false
    static var all: [PebbleRelease] {
        guard let url = Bundle.main.url(forResource: "releases", withExtension: "json"), let data = try? Data(contentsOf: url), let releases = try? JSONDecoder().decode([PebbleRelease].self, from: data) else { return [] }
        return releases
    }
    static func claimAutomatic() -> Bool {
        guard !claimed, UserDefaults.standard.string(forKey: seenKey) != identity, !all.isEmpty else { return false }
        claimed = true; return true
    }
    static func acknowledge() { UserDefaults.standard.set(identity, forKey: seenKey) }
    static func entries(history: Bool) -> [PebbleRelease] {
        if history { return all }
        let last = UserDefaults.standard.string(forKey: seenKey)?.split(separator: ":").first.map(String.init)
        guard let last, let index = all.firstIndex(where: { $0.version == last }), index > 0 else { return Array(all.prefix(1)) }
        return Array(all.prefix(index))
    }
}
struct WhatsNew: View {
    var history: Bool; var close: () -> Void
    var body: some View {
        VStack(alignment: .leading, spacing: 24) {
            HStack(spacing: 14) {
                if let url = Bundle.main.url(forResource: "Pebble", withExtension: "icns"), let image = NSImage(contentsOf: url) { Image(nsImage: image).resizable().frame(width: 52, height: 52) }
                VStack(alignment: .leading, spacing: 4) { Text("What’s New").font(.system(size: 26, weight: .bold)); Text("Pebble Notes · " + ReleaseNotes.version).font(.system(size: 12)).foregroundStyle(.secondary) }
                Spacer(); GlassIcon(icon: "xmark", label: "Close changelog", action: close).pebbleGlass(in: Circle())
            }
            ForEach(ReleaseNotes.entries(history: history)) { release in
                VStack(alignment: .leading, spacing: 12) {
                    Text(release.version + " · " + release.title).font(.system(size: 16, weight: .semibold))
                    ForEach(release.changes, id: \.self) { change in HStack(alignment: .top, spacing: 10) { Image(systemName: "checkmark").foregroundStyle(PebblePalette.accent).padding(.top, 2); Text(change).font(.system(size: 14)).fixedSize(horizontal: false, vertical: true) } }
                }
            }
            HStack { Spacer(); Button("Continue", action: close).keyboardShortcut(.defaultAction).buttonStyle(SoftButtonStyle()).padding(8).pebbleGlass(in: Capsule()) }
        }.padding(28).frame(maxWidth: 560).background(.thickMaterial, in: RoundedRectangle(cornerRadius: 22)).overlay(RoundedRectangle(cornerRadius: 22).strokeBorder(.white.opacity(0.12), lineWidth: 0.5)).padding(.horizontal, 20)
            .onExitCommand(perform: close)
    }
}
