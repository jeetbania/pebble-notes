import SwiftUI
import AppKit

// Optional per-note colours. Nil retains the app appearance; explicit colours sync unchanged.
struct NoteStyle: Codable, Equatable {
    var document: String? = nil
    var backdrop: String? = nil
    var backdropEnd: String? = nil
    var text: String? = nil
    var framed: Bool { backdrop != nil }
    var customised: Bool { document != nil || framed || text != nil }
    static let palette = ["#FFFCF8", "#F8F0FF", "#E5EEFF", "#DFF4EC", "#FFE7E3", "#FFE8B5", "#FFBD19", "#5F96DB", "#7756AE", "#185B51", "#31343B", "#191B20"]
    static func colour(_ hex: String?) -> Color? { guard let hex, let value = rgb(hex) else { return nil }; return Color(red: Double(value >> 16 & 255)/255, green: Double(value >> 8 & 255)/255, blue: Double(value & 255)/255) }
    static func rgb(_ hex: String) -> UInt32? { guard hex.count == 7, hex.first == "#" else { return nil }; return UInt32(hex.dropFirst(), radix: 16) }
    static func isLight(_ hex: String) -> Bool {
        guard let rgb = rgb(hex) else { return true }
        func channel(_ n: UInt32) -> Double { let v = Double(n)/255; return v <= 0.04045 ? v/12.92 : pow((v+0.055)/1.055, 2.4) }
        return 0.2126*channel(rgb >> 16 & 255) + 0.7152*channel(rgb >> 8 & 255) + 0.0722*channel(rgb & 255) > 0.179
    }
    var ink: NSColor { if let text, let c = Self.colour(text) { return NSColor(c) }; if let document { return Self.isLight(document) ? NSColor(calibratedWhite: 0.10, alpha: 1) : NSColor(calibratedWhite: 0.97, alpha: 1) }; return .labelColor }
    var foreground: Color { Color(nsColor: ink) }
    var paper: Color { Self.colour(document) ?? Color(nsColor: .textBackgroundColor) }
    var chromeScheme: ColorScheme? { (backdrop ?? document).map { Self.isLight($0) ? .light : .dark } }
    var documentScheme: ColorScheme? { document.map { Self.isLight($0) ? .light : .dark } }
    func background() -> LinearGradient { LinearGradient(colors: [Self.colour(backdrop) ?? paper, Self.colour(backdropEnd ?? backdrop) ?? paper], startPoint: .topLeading, endPoint: .bottomTrailing) }
    static func hex(_ colour: Color) -> String { let c = NSColor(colour).usingColorSpace(.sRGB) ?? .white; return String(format: "#%02X%02X%02X", Int((c.redComponent*255).rounded()), Int((c.greenComponent*255).rounded()), Int((c.blueComponent*255).rounded())) }
}

struct NoteStyleButton: View {
    @ObservedObject var store: NoteStore
    @LeafState private var presented = false
    var interfaceScheme: ColorScheme? = nil
    @Environment(\.colorScheme) private var scheme
    var body: some View {
        GlassIcon(icon: "paintbrush", label: "Note style") { presented.toggle() }
            .popover(isPresented: $presented, arrowEdge: .top) { NoteStylePicker(store: store).id(store.selected).padding(18).frame(width: 290).environment(\.colorScheme, interfaceScheme ?? scheme) }
            .onChange(of: store.selected) { _, _ in presented = false }
    }
}
struct NoteStylePicker: View {
    @ObservedObject var store: NoteStore
    @LeafState private var picker: String? = nil
    var style: NoteStyle { store.current?.style ?? NoteStyle() }
    func value(_ key: String) -> String? { switch key { case "Document": style.document; case "Backdrop": style.backdrop; default: style.text } }
    func set(_ key: String, _ value: String?) { store.update(undoKey: "noteStyle") { n in var s = n.style ?? NoteStyle(); switch key { case "Document": s.document = value; case "Backdrop": s.backdrop = value; if value == nil { s.backdropEnd = nil }; default: s.text = value }; n.style = s.customised ? s : nil } }
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack { if picker != nil { GlassIcon(icon: "chevron.left", label: "Back to style") { picker = nil } }; Text(picker ?? "Style").font(.headline); Spacer() }
            StyleMiniature(style: style).frame(height: 130).accessibilityLabel("Live note style preview")
            if let key = picker {
                LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 4), spacing: 10) {
                    Button { set(key, nil) } label: { Image(systemName: key == "Backdrop" ? "rectangle.slash" : "circle.lefthalf.filled").frame(width: 44, height: 44).background(.quaternary, in: Circle()).overlay(Circle().stroke(value(key) == nil ? LeafPalette.accent : .clear, lineWidth: 3)) }.buttonStyle(.plain).help(key == "Backdrop" ? "No backdrop" : "Automatic")
                    ForEach(NoteStyle.palette, id: \.self) { hex in Button { set(key, hex) } label: { Circle().fill(NoteStyle.colour(hex)!).frame(width: 44, height: 44).overlay(Circle().strokeBorder(Color.primary.opacity(0.15), lineWidth: 0.5)).overlay { if value(key) == hex { Image(systemName: "checkmark").foregroundStyle(NoteStyle.isLight(hex) ? .black : .white).fontWeight(.semibold) } } }.buttonStyle(.plain).accessibilityLabel(hex).help(hex) }
                }
                ColorPicker("Custom colour", selection: Binding(get: { NoteStyle.colour(value(key)) ?? .white }, set: { set(key, NoteStyle.hex($0)) }), supportsOpacity: false)
                if key == "Backdrop", style.framed {
                    HStack { Text("Gradient"); Spacer(); Toggle("Gradient", isOn: Binding(get: { style.backdropEnd != nil }, set: { enabled in store.update(undoKey: "noteStyle") { $0.style?.backdropEnd = enabled ? (style.document ?? "#F8F0FF") : nil } })).labelsHidden() }
                    if style.backdropEnd != nil { ColorPicker("Fade colour", selection: Binding(get: { NoteStyle.colour(style.backdropEnd) ?? .white }, set: { colour in store.update(undoKey: "noteStyle") { $0.style?.backdropEnd = NoteStyle.hex(colour) } }), supportsOpacity: false) }
                }
            } else {
                ForEach(["Document", "Backdrop", "Text"], id: \.self) { key in
                    Button { picker = key } label: { HStack { Image(systemName: key == "Document" ? "doc.fill" : key == "Backdrop" ? "square.stack.fill" : "textformat").frame(width: 24); Text(key); Spacer(); RoundedRectangle(cornerRadius: 10).fill(NoteStyle.colour(value(key)) ?? Color.primary.opacity(0.08)).frame(width: 36, height: 30).overlay { if value(key) == nil { Image(systemName: key == "Backdrop" ? "slash.circle" : "circle.lefthalf.filled").font(.system(size: 14)) } }; Image(systemName: "chevron.right").font(.system(size: 11)).foregroundStyle(.secondary) }.padding(.vertical, 7) }.buttonStyle(.plain).accessibilityLabel("\(key) colour")
                }
                SubtleDivider()
                Button { store.update { $0.style = nil } } label: { Label("Reset style", systemImage: "arrow.counterclockwise") }.buttonStyle(SoftButtonStyle()).disabled(!style.customised)
            }
        }
    }
}
struct StyleMiniature: View {
    var style: NoteStyle
    var body: some View {
        VStack(alignment: .leading, spacing: 9) { Text("Note").font(.headline); RoundedRectangle(cornerRadius: 2).fill(style.foreground.opacity(0.18)).frame(height: 5); RoundedRectangle(cornerRadius: 2).fill(style.foreground.opacity(0.12)).frame(width: 80, height: 5); Spacer() }
            .foregroundStyle(style.foreground).padding(16).frame(maxWidth: .infinity).background(style.paper, in: RoundedRectangle(cornerRadius: 12)).shadow(color: .black.opacity(style.framed ? 0.1 : 0), radius: 6, y: 3).padding(style.framed ? 12 : 0).background(style.background(), in: RoundedRectangle(cornerRadius: 16))
    }
}

struct NotePreviewSurface: ViewModifier {
    var style: NoteStyle?
    @Environment(\.colorScheme) private var scheme
    func body(content: Content) -> some View {
        if let style, style.document != nil || style.framed {
            content.foregroundStyle(style.foreground).background(style.paper, in: RoundedRectangle(cornerRadius: style.framed ? 16 : 22))
                .overlay(RoundedRectangle(cornerRadius: style.framed ? 16 : 22).strokeBorder(style.foreground.opacity(0.08), lineWidth: 0.5))
                .padding(style.framed ? 8 : 0).background(style.background(), in: RoundedRectangle(cornerRadius: 22))
                .shadow(color: .black.opacity(0.10), radius: 6, y: 3)
                .environment(\.colorScheme, style.documentScheme ?? scheme)
        } else { content.foregroundStyle(style?.foreground ?? .primary).background(CardMaterial()) }
    }
}
