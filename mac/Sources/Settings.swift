import SwiftUI
import AppKit

struct SettingsHome: View {
    @EnvironmentObject var store: NoteStore
    @ObservedObject private var updater = PebbleUpdater.shared
    @Environment(\.colorScheme) private var scheme
    var initialPage = "Appearance"
    var onWhatsNew: () -> Void = {}
    var onClose: () -> Void = {}
    @AppStorage("profileName") private var profileName = ""
    @AppStorage("appearance") private var appearance = "system"
    @Bindable private var accent = AccentPreference.shared
    @AppStorage("calmMotion") private var calmMotion = false
    @AppStorage("typeBody") private var typeBody = 15.0
    @AppStorage("typeHeadline") private var typeHeadline = 17.0
    @AppStorage("typeSubtitle") private var typeSubtitle = 19.0
    @AppStorage("typeTitle") private var typeTitle = 26.0
    @AppStorage("clipboardSuggestions") private var clipboardSuggestions = true
    @AppStorage("editorWidth") private var editorWidth = 700.0
    @AppStorage("googleClientId") private var clientId = ""
    @AppStorage("googleClientSecret") private var secret = ""
    @LeafState<String> private var page = "Appearance"
    @LeafState<Bool> private var connecting = false
    @LeafState<String> private var message = ""
    let sections = [("Appearance", "paintpalette"), ("Writing", "textformat"), ("Sync", "arrow.triangle.2.circlepath"), ("Backups", "archivebox"), ("Updates", "arrow.down.circle"), ("About", "info.circle")]
    var body: some View {
        HStack(spacing: 0) {
            VStack(alignment: .leading, spacing: 8) {
                HStack { if let url = Bundle.main.url(forResource: "Leaf", withExtension: "icns"), let image = NSImage(contentsOf: url) { Image(nsImage: image).resizable().frame(width: 34, height: 34) }; Text("Pebble Notes").font(.headline) }.padding(.bottom, 26)
                ForEach(sections, id: \.0) { label, icon in Button { page = label } label: { Label(label, systemImage: icon).frame(maxWidth: .infinity, alignment: .leading).padding(12).background(Color.primary.opacity(page == label ? 0.09 : 0), in: RoundedRectangle(cornerRadius: 10)) }.buttonStyle(SoftButtonStyle()) }
                Spacer(); Button("What’s New · " + ReleaseNotes.version) { onWhatsNew() }.buttonStyle(SoftButtonStyle()).font(.caption).foregroundStyle(Color.primary.opacity(0.85)).frame(maxWidth: .infinity, minHeight: 36, alignment: .leading).contentShape(Rectangle())
            }.padding(20).frame(width: 180).background(Color.clear)
            LeafScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    HStack { Text(page).font(.system(size: 25, weight: .semibold)); Spacer(); GlassIcon(icon: "xmark", label: "Close Settings", action: onClose).leafGlass(in: Circle()) }.padding(.bottom, 4)
                    if page == "Appearance" {
                        caption("Interface theme", "Choose the light, dark, or system appearance.")
                        HStack(spacing: 10) { ForEach([("light", "Light", "sun.max"), ("dark", "Dark", "moon"), ("system", "System", "desktopcomputer")], id: \.0) { value, label, icon in
                            Button { appearance = value } label: { HStack(spacing: 7) { Image(systemName: icon).font(.system(size: 14)); Text(label).font(.system(size: 13)) }.frame(maxWidth: .infinity).frame(height: 40).background(Color.primary.opacity(appearance == value ? 0.09 : 0.035), in: Capsule()).overlay(Capsule().strokeBorder(appearance == value ? LeafPalette.accent.opacity(0.8) : Color.primary.opacity(0.08), lineWidth: appearance == value ? 1.5 : 0.5)) }.buttonStyle(SoftButtonStyle(radius: 20)).accessibilityLabel(label + " theme").accessibilityValue(appearance == value ? "Selected" : "")
                        } }
                        caption("Accent colour", "Choose a colour for controls and selections on this Mac.")
                        HStack(spacing: 8) { ForEach(AccentPreference.choices, id: \.self) { name in
                            Button { accent.name = name } label: { VStack(spacing: 5) { Circle().fill(LeafPalette.color("Accents", name)).frame(width: 28, height: 28).overlay { if accent.name == name { Image(systemName: "checkmark").font(.system(size: 12, weight: .bold)).foregroundStyle(name == "Yellow" || name == "Orange" || name == "Green" ? Color.black : .white) } }; Text(name).font(.system(size: 10)).foregroundStyle(Color.primary.opacity(0.72)) }.frame(maxWidth: .infinity).frame(height: 54).contentShape(Rectangle()) }.buttonStyle(SoftButtonStyle(radius: 10)).accessibilityLabel(name + " accent").accessibilityValue(accent.name == name ? "Selected" : "")
                        } }
                        SubtleDivider(); Toggle("Calmer motion", isOn: $calmMotion); Text("Keep subtle feedback and reduce movement. System Reduce Motion is also respected.").font(.caption).foregroundStyle(Color.primary.opacity(0.72))
                    } else if page == "Writing" {
                        caption("Reading width", "Adjust the maximum width of the note canvas.")
                        AdaptiveSlider(value: $editorWidth, range: 520...820, step: 20); Text("\(Int(editorWidth)) points").font(.caption).foregroundStyle(Color.primary.opacity(0.72))
                        SubtleDivider(); caption("Text sizes", "Defaults for this Mac. A note’s own size adjustment multiplies these values and syncs across devices.")
                        typeRow("Body", value: $typeBody, range: 14...26); typeRow("Heading", value: $typeHeadline, range: 17...34); typeRow("Subtitle", value: $typeSubtitle, range: 18...34); typeRow("Title", value: $typeTitle, range: 24...44)
                        HStack { Text("A quieter place for your thoughts.").font(.system(size: typeBody)); Spacer(); Button("Reset sizes") { typeBody = 15; typeHeadline = 17; typeSubtitle = 19; typeTitle = 26 } }
                        SubtleDivider(); Toggle("Suggest a note from the clipboard", isOn: $clipboardSuggestions)
                        Text("When Pebble becomes active, preview copied text, links, or images. Nothing is saved until you choose Save or paste into the library.").font(.caption).foregroundStyle(Color.primary.opacity(0.72))
                        SubtleDivider(); caption("Formatting", "Use the top toolbar for text styles and lists. Right-click a block for indentation, moving, or duplication.")
                        caption("History & recovery", "Undo and redo are available while a note is open. Saved versions remain under Find, Tags & History.")
                    } else if page == "Sync" {
                        caption("Google Drive", "Notes save locally first. Your private app storage connects Mac and Android.")
                        Text(store.status).font(.headline)
                        HStack { Button(connecting ? "Connecting…" : "Connect Google") { connecting = true; Task { do { try await GoogleAuth.connect(clientId: clientId.trimmingCharacters(in: .whitespacesAndNewlines), secret: secret.trimmingCharacters(in: .whitespacesAndNewlines)); await store.sync(); message = "Connected" } catch { message = error.localizedDescription }; connecting = false } }.disabled(connecting); Button("Sync now") { Task { await store.sync() } }.disabled(store.busy); Button("Disconnect") { TokenVault.remove(); store.status = "Saved on this Mac"; message = "Local notes are kept." }.disabled(store.busy || connecting) }
                        Text(message).font(.caption).foregroundStyle(Color.primary.opacity(0.72))
                        DisclosureGroup("Advanced connection settings") { VStack { TextField("Desktop OAuth client ID", text: $clientId); SecureField("Desktop OAuth client secret", text: $secret) }.textFieldStyle(.roundedBorder).padding(.top, 10) }
                    } else if page == "Updates" {
                        caption("Pebble Notes " + ReleaseNotes.version, "Updates download here, with release notes and your choice of when to install.")
                        Text(updater.message).font(.callout).foregroundStyle(.secondary)
                        Button(updater.checking ? "Checking…" : "Check for updates") { Task { await updater.check(manual: true) } }.disabled(updater.checking || updater.downloading)
                        if updater.available != nil { Button("View available update") { updater.visible = true } }
                    } else if page == "About" {
                        Text("Pebble Notes").font(.system(size: 32, weight: .bold))
                        Text("A quieter home for thoughts, images, and what comes next.").font(.system(size: 17)).foregroundStyle(Color.primary.opacity(0.72))
                        caption("Yours, even offline", "Your library saves on this device first. Google Drive sync is optional, and your original images stay at their original quality.")
                        caption("One space, many ways to think", "Write rich notes, collect visual inspiration, and plan tasks with dates, reminders, and a board. Arrange your library around the way you work.")
                        caption("Room to change your mind", "Note history, Trash, and local backups help you return to earlier work. Export your library whenever you want.")
                        TextField("Your name · optional", text: $profileName).onChange(of: profileName) { _, next in if next.count > 40 { profileName = String(next.prefix(40)) } }.textFieldStyle(.roundedBorder)
                        Button("Replay welcome tour") { NotificationCenter.default.post(name: Notification.Name("pebbleOnboarding"), object: nil) }
                        Text("Version " + ReleaseNotes.version).font(.caption).foregroundStyle(Color.primary.opacity(0.72))
                    } else {
                        caption("Automatic local backups", "Pebble keeps up to seven dated backups on this Mac, saving at most once per hour.")
                        Text(store.backupStatus).font(.caption).foregroundStyle(Color.primary.opacity(0.72))
                        Button("Show backup folder") { NSWorkspace.shared.open(store.root.appendingPathComponent("backups")) }
                        SubtleDivider(); caption("Take a copy with you", "Export notes and original attachments together. Importing a backup retains saved history.")
                        HStack { Button("Export backup…") { store.exportBackup() }; Button("Import backup…") { store.importBackup() } }
                    }
                }.padding(32).frame(maxWidth: .infinity, alignment: .leading)
            }.frame(maxWidth: .infinity).background(scheme == .dark ? Color.black.opacity(0.30) : Color.white.opacity(0.52))
        }.onAppear { page = initialPage }.tint(LeafPalette.accent).buttonStyle(MaterialActionStyle()).toggleStyle(.switch).onExitCommand(perform: onClose).preferredColorScheme(appearance == "light" ? .light : appearance == "dark" ? .dark : nil)
    }
    func typeRow(_ title: String, value: Binding<Double>, range: ClosedRange<Double>) -> some View { HStack { Text(title).frame(width: 70, alignment: .leading); AdaptiveSlider(value: value, range: range, step: 1); Text("\(Int(value.wrappedValue)) pt").monospacedDigit().frame(width: 40) } }
    func caption(_ title: String, _ detail: String) -> some View { VStack(alignment: .leading, spacing: 6) { Text(title).font(.system(size: 14, weight: .semibold)); Text(detail).font(.system(size: 12)).foregroundStyle(Color.primary.opacity(0.72)).fixedSize(horizontal: false, vertical: true) } }
}
