import SwiftUI
import AppKit

struct PebbleField: ViewModifier {
    func body(content: Content) -> some View {
        content.textFieldStyle(.plain).foregroundStyle(.primary).padding(.horizontal, 12).padding(.vertical, 10)
            .frame(minHeight: 38).background(Color.primary.opacity(0.045), in: RoundedRectangle(cornerRadius: 10))
            .overlay(RoundedRectangle(cornerRadius: 10).strokeBorder(Color.primary.opacity(0.10), lineWidth: 0.5))
    }
}
struct TagPill: View {
    var text: String
    var colour: Color {
        let choices: [Color] = [.blue, .purple, .pink, .green, .orange]
        return choices[text.utf8.reduce(0) { ($0 + Int($1)) % choices.count }]
    }
    var body: some View { Text(text).font(.system(size: 12, weight: .medium)).foregroundStyle(colour).padding(.horizontal, 10).padding(.vertical, 6).background(colour.opacity(0.14), in: Capsule()).overlay(Capsule().strokeBorder(colour.opacity(0.20), lineWidth: 0.5)) }
}
struct ProfileSettings: View {
    @Binding var name: String
    @AppStorage("profileBio") private var bio = ""
    @AppStorage("profilePhoto") private var photo = Data()
    @AppStorage("profileColour") private var colour = ""
    @LeafState private var error = ""
    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            HStack(spacing: 18) {
                ZStack {
                    Circle().fill(LeafPalette.color("Accents", colour.isEmpty ? "Blue" : colour).opacity(0.28))
                    if let image = NSImage(data: photo) { Image(nsImage: image).resizable().scaledToFill() }
                    else { Text(String(name.trimmingCharacters(in: .whitespacesAndNewlines).first.map { String($0).uppercased() } ?? "P")).font(.system(size: 30, weight: .semibold)) }
                }.frame(width: 76, height: 76).clipShape(Circle()).accessibilityLabel("Profile picture")
                VStack(alignment: .leading, spacing: 8) {
                    Button("Choose picture…", action: choosePicture)
                    if !photo.isEmpty { Button("Remove picture") { photo = Data() } }
                }
            }
            VStack(alignment: .leading, spacing: 6) { Text("Your name").font(.headline); TextField("What should we call you?", text: $name).modifier(PebbleField()).onChange(of: name) { _, value in if value.count > 40 { name = String(value.prefix(40)) } } }
            VStack(alignment: .leading, spacing: 6) { Text("A little about you").font(.headline); TextField("Optional", text: $bio, axis: .vertical).lineLimit(2...4).modifier(PebbleField()).onChange(of: bio) { _, value in if value.count > 280 { bio = String(value.prefix(280)) } } }
            Text("Your profile stays on this Mac. You can change it whenever you like.").font(.caption).foregroundStyle(Color.primary.opacity(0.72))
            if !error.isEmpty { Text(error).font(.caption).foregroundStyle(.secondary) }
        }.onAppear { if colour.isEmpty { colour = AccentPreference.choices.randomElement() ?? "Blue" } }
    }
    func choosePicture() {
        let panel = NSOpenPanel(); panel.allowedContentTypes = [.image]; panel.allowsMultipleSelection = false
        guard panel.runModal() == .OK, let url = panel.url, let image = NSImage(contentsOf: url), image.size.width > 0, image.size.height > 0 else { return }
        let side: CGFloat = 256
        let rendered = NSImage(size: NSSize(width: side, height: side)); rendered.lockFocus()
        let scale = max(side / image.size.width, side / image.size.height)
        let size = NSSize(width: image.size.width * scale, height: image.size.height * scale)
        image.draw(in: NSRect(x: (side - size.width) / 2, y: (side - size.height) / 2, width: size.width, height: size.height))
        rendered.unlockFocus()
        if let tiff = rendered.tiffRepresentation, let bitmap = NSBitmapImageRep(data: tiff), let data = bitmap.representation(using: .jpeg, properties: [.compressionFactor: 0.85]) { photo = data; error = "" }
        else { error = "That picture could not be opened. Try another image." }
    }
}
