import SwiftUI
import AppKit
import Observation

@Observable final class AccentPreference {
    static let shared = AccentPreference()
    static let choices = ["Yellow", "Blue", "Purple", "Pink", "Green", "Orange"]
    var name: String { didSet { UserDefaults.standard.set(name, forKey: "accentColor") } }
    private init() { let saved = UserDefaults.standard.string(forKey: "accentColor") ?? "Yellow"; name = Self.choices.contains(saved) ? saved : "Yellow" }
}

/// Semantic colours from the user's original Apple library exports, including alpha.
enum LeafPalette {
    static let themes: [String: [String: Any]] = Dictionary(uniqueKeysWithValues: ["Light", "Dark"].map { theme in
        let url = Bundle.main.url(forResource: theme + ".tokens", withExtension: "json")
        let data = url.flatMap { try? Data(contentsOf: $0) }
        return (theme, data.flatMap { try? JSONSerialization.jsonObject(with: $0) as? [String: Any] } ?? [:])
    })
    static func color(_ group: String, _ key: String) -> Color {
        Color(nsColor: NSColor(name: nil) { appearance in
            let dark = appearance.bestMatch(from: [.darkAqua, .aqua]) == .darkAqua
            let theme = themes[dark ? "Dark" : "Light"] ?? [:]
            guard let value = ((theme[group] as? [String: Any])?[key] as? [String: Any])?["$value"] as? [String: Any], let c = value["components"] as? [Double], c.count == 3 else { return .labelColor }
            return NSColor(srgbRed: c[0], green: c[1], blue: c[2], alpha: value["alpha"] as? Double ?? 1)
        })
    }
    static var accent: Color { color("Accents", AccentPreference.shared.name) }
}
struct WindowMaterial: NSViewRepresentable {
    func makeNSView(context: Context) -> NSVisualEffectView {
        let view = NSVisualEffectView(); view.material = .underWindowBackground; view.blendingMode = .behindWindow; view.state = .active; return view
    }
    func updateNSView(_ view: NSVisualEffectView, context: Context) {}
}
struct SoftButtonStyle: ButtonStyle {
    var radius: CGFloat = 8
    func makeBody(configuration: Configuration) -> some View { HoverFeedback(configuration: configuration, radius: radius) }
    struct HoverFeedback: View {
        let configuration: ButtonStyleConfiguration; var radius: CGFloat
        @LeafState<Bool> private var hovering = false
        @Environment(\.accessibilityReduceMotion) var reduced
        @AppStorage("calmMotion") var calm = false
        var body: some View { configuration.label.frame(minWidth: 32, minHeight: 32).contentShape(Rectangle()).background(Color.primary.opacity(hovering ? 0.055 : 0), in: RoundedRectangle(cornerRadius: radius)).opacity(configuration.isPressed ? 0.65 : 1).scaleEffect(configuration.isPressed && !reduced && !calm ? 0.98 : 1).onHover { hovering = $0 }.animation(reduced || calm ? nil : .easeInOut(duration: 0.14), value: hovering).animation(reduced || calm ? nil : .easeOut(duration: 0.12), value: configuration.isPressed) }
    }
}
extension View {
    @ViewBuilder func leafGlass<S: Shape>(in shape: S) -> some View {
        if #available(macOS 26, *) { self.glassEffect(.regular, in: shape) }
        else { self.background(.ultraThinMaterial, in: shape) }
    }
}
struct GlassIcon: View {
    var icon: String; var label: String; var size: CGFloat = 16; var action: () -> Void
    var body: some View {
        Button(action: action) { Image(systemName: icon).font(.system(size: size, weight: .medium)).frame(width: 36, height: 36) }
            .buttonStyle(SoftButtonStyle(radius: 18)).help(label).accessibilityLabel(label)
    }
}

// Extend content into the native title area; traffic lights stay system controls.
struct WindowChrome: NSViewRepresentable {
    class ChromeView: NSView {
        override func viewDidMoveToWindow() {
            super.viewDidMoveToWindow()
            guard let window else { return }
            window.titlebarAppearsTransparent = true
            window.titleVisibility = .hidden
            window.styleMask.insert(.fullSizeContentView)
            window.isOpaque = false
            window.backgroundColor = .clear
            window.toolbar = nil
            for type in [NSWindow.ButtonType.closeButton, .miniaturizeButton, .zoomButton] {
                if let button = window.standardWindowButton(type) {
                    var frame = button.frame; frame.origin.y -= 10; button.setFrameOrigin(frame.origin)
                }
            }
        }
    }
    func makeNSView(context: Context) -> NSView { ChromeView() }
    func updateNSView(_ view: NSView, context: Context) {}
}
struct CardMaterial: View {
    @Environment(\.colorScheme) var scheme
    @Environment(\.accessibilityReduceTransparency) var reduced
    var body: some View {
        RoundedRectangle(cornerRadius: 22).fill(reduced ? AnyShapeStyle(Color(nsColor: .controlBackgroundColor)) : AnyShapeStyle(.ultraThinMaterial))
            .overlay(RoundedRectangle(cornerRadius: 22).fill(scheme == .dark ? Color.white.opacity(0.07) : Color.white.opacity(0.12)))
            .overlay(RoundedRectangle(cornerRadius: 22).strokeBorder(Color.white.opacity(scheme == .dark ? 0.10 : 0.35), lineWidth: 0.5))
            .shadow(color: .black.opacity(0.10), radius: 5, y: 3)
    }
}

// Sidebar feedback changes the foreground only; no extra hover tile.
struct SidebarButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View { Feedback(configuration: configuration) }
    struct Feedback: View {
        let configuration: ButtonStyleConfiguration
        @LeafState<Bool> private var hovering = false
        var body: some View {
            configuration.label.frame(minWidth: 32, minHeight: 32).contentShape(Rectangle()).foregroundStyle(hovering ? Color.primary : Color.secondary)
                .opacity(configuration.isPressed ? 0.65 : 1).onHover { hovering = $0 }
                .animation(.easeInOut(duration: 0.14), value: hovering)
        }
    }
}
struct PanePresentation<Panel: View>: ViewModifier {
    var presented: Bool; @ViewBuilder var panel: () -> Panel
    @Environment(\.accessibilityReduceTransparency) private var reduced
    func body(content: Content) -> some View {
        content.disabled(presented).accessibilityHidden(presented).overlay {
            if presented {
                ZStack {
                    Rectangle().fill(reduced ? AnyShapeStyle(Color(nsColor: .windowBackgroundColor)) : AnyShapeStyle(.ultraThinMaterial)).overlay(Color.black.opacity(0.10))
                    LeafScrollView { panel().frame(maxWidth: .infinity).padding(.top, 72).padding(.bottom, 24) }
                }.frame(maxWidth: .infinity, maxHeight: .infinity).transition(.opacity)
            }
        }
    }
}
extension View {
    func panePresented<Panel: View>(isPresented: Binding<Bool>, @ViewBuilder content: @escaping () -> Panel) -> some View { modifier(PanePresentation(presented: isPresented.wrappedValue, panel: content)) }
    func panePresented<Item: Identifiable, Panel: View>(item: Binding<Item?>, @ViewBuilder content: @escaping (Item) -> Panel) -> some View { modifier(PanePresentation(presented: item.wrappedValue != nil) { if let value = item.wrappedValue { content(value) } }) }
}
struct CardReorder: DropDelegate {
    var target: String; var order: [String]; @Binding var dragging: String?; var reorder: ([String]) -> Void
    func dropEntered(info: DropInfo) { guard let source = dragging, source != target, let from = order.firstIndex(of: source), let to = order.firstIndex(of: target) else { return }; var ids = order; ids.remove(at: from); ids.insert(source, at: to); withAnimation(NSWorkspace.shared.accessibilityDisplayShouldReduceMotion || UserDefaults.standard.bool(forKey: "calmMotion") ? nil : .spring(response: 0.3, dampingFraction: 1)) { reorder(ids) } }
    func dropUpdated(info: DropInfo) -> DropProposal? { DropProposal(operation: .move) }
    func performDrop(info: DropInfo) -> Bool { dragging = nil; return true }
}

struct HoverSurface: ViewModifier {
    @LeafState<Bool> private var hovering = false
    @Environment(\.accessibilityReduceMotion) var reduced
    func body(content: Content) -> some View { content.background(Color.primary.opacity(hovering ? 0.07 : 0), in: Capsule()).onHover { hovering = $0 }.animation(reduced ? nil : .easeInOut(duration: 0.14), value: hovering) }
}

struct GalleryFrames: PreferenceKey {
    static var defaultValue: [String: CGRect] = [:]
    static func reduce(value: inout [String: CGRect], nextValue: () -> [String: CGRect]) { value.merge(nextValue(), uniquingKeysWith: { _, new in new }) }
}
struct GalleryGrab<G: Gesture>: ViewModifier {
    var id: String; var dragging: String?; var gesture: G
    func body(content: Content) -> some View {
        content.opacity(dragging == id ? 0 : 1)
            .background(GeometryReader { proxy in Color.clear.preference(key: GalleryFrames.self, value: [id: proxy.frame(in: .named("galleryGrab"))]) })
            .highPriorityGesture(gesture)
    }
}

// Sample content inside this window, so scrolling text remains softly visible below chrome.
struct ContentBlur: NSViewRepresentable {
    func makeNSView(context: Context) -> NSVisualEffectView {
        let view = NSVisualEffectView(); view.material = .hudWindow; view.blendingMode = .withinWindow; view.state = .active; view.alphaValue = 0.08
        return view
    }
    func updateNSView(_ view: NSVisualEffectView, context: Context) {}
}

struct IconDepth: ViewModifier {
    var active: Bool = false
    func body(content: Content) -> some View { content.background(Color.primary.opacity(active ? 0 : 0.055), in: RoundedRectangle(cornerRadius: 7)).overlay(RoundedRectangle(cornerRadius: 7).stroke(LinearGradient(colors: [.white.opacity(active ? 0 : 0.22), .black.opacity(active ? 0 : 0.08)], startPoint: .top, endPoint: .bottom), lineWidth: 0.5)).shadow(color: .black.opacity(active ? 0 : 0.14), radius: 2, y: 1) }
}
struct SyncIndicator: View {
    @ObservedObject var store: NoteStore
    @LeafState<Bool> private var visible = false
    var failed: Bool { store.status.localizedCaseInsensitiveContains("pending") || store.status.localizedCaseInsensitiveContains("attention") }
    var body: some View { Image(systemName: store.busy ? "icloud.and.arrow.up" : failed ? "icloud.slash" : "checkmark.icloud").font(.system(size: 13)).foregroundStyle(store.busy ? Color.yellow : failed ? .red : .green).opacity(visible || store.busy || failed ? 1 : 0).frame(width: 22).help(store.status).accessibilityLabel(store.status).animation(.easeOut(duration: 0.3), value: visible).onChange(of: store.status) { _, value in if value.hasPrefix("Synced") { visible = true; Task { try? await Task.sleep(for: .seconds(3)); if !store.busy { visible = false } } } }.onChange(of: store.busy) { _, busy in if busy { visible = true } } }
}

struct GlassDialog<Panel: View>: View {
    @ViewBuilder var panel: () -> Panel
    var body: some View { ZStack { Color.black.opacity(0.12).ignoresSafeArea().contentShape(Rectangle()); panel().background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 24)).overlay(RoundedRectangle(cornerRadius: 24).strokeBorder(Color.white.opacity(0.16), lineWidth: 0.5)).shadow(color: .black.opacity(0.2), radius: 24, y: 10) }.frame(maxWidth: .infinity, maxHeight: .infinity) }
}

struct MaterialActionStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.font(.system(size: 13, weight: .medium)).foregroundStyle(Color.primary).padding(.horizontal, 12).frame(minWidth: 36, minHeight: 36).background(Color.primary.opacity(configuration.isPressed ? 0.14 : 0.075), in: RoundedRectangle(cornerRadius: 10)).overlay(RoundedRectangle(cornerRadius: 10).strokeBorder(Color.primary.opacity(0.07), lineWidth: 0.5)).contentShape(Rectangle())
    }
}
struct SubtleDivider: View { var body: some View { Rectangle().fill(Color.primary.opacity(0.10)).frame(height: 0.5).accessibilityHidden(true) } }
struct AdaptiveSlider: View {
    @Binding var value: Double; var range: ClosedRange<Double>; var step: Double
    var count: Int { min(21, Int((range.upperBound - range.lowerBound) / step) + 1) }
    var body: some View { VStack(spacing: 2) {
        Slider(value: Binding(get: { value }, set: { value = (range.lowerBound + (($0 - range.lowerBound) / step).rounded() * step).clamped(to: range) }), in: range)
        HStack(spacing: 0) { ForEach(0..<count, id: \.self) { i in Circle().fill(Color.primary.opacity(0.45)).frame(width: 2, height: 2); if i < count - 1 { Spacer(minLength: 0) } } }.padding(.horizontal, 9).accessibilityHidden(true)
    }.frame(minHeight: 32) }
}
extension Double { func clamped(to range: ClosedRange<Double>) -> Double { min(range.upperBound, max(range.lowerBound, self)) } }

struct EditorFocusDismissal: NSViewRepresentable {
    final class FocusView: NSView {
        private var monitor: Any?
        override func viewDidMoveToWindow() {
            super.viewDidMoveToWindow()
            if let monitor { NSEvent.removeMonitor(monitor); self.monitor = nil }
            guard window != nil else { return }
            monitor = NSEvent.addLocalMonitorForEvents(matching: .leftMouseDown) { [weak self] event in
                guard let window = self?.window, event.window === window else { return event }
                guard let editor = window.firstResponder as? NSTextView else { return event }
                let point = editor.convert(event.locationInWindow, from: nil)
                if !editor.visibleRect.contains(point) { window.makeFirstResponder(nil) }
                return event
            }
        }
        deinit { if let monitor { NSEvent.removeMonitor(monitor) } }
    }
    func makeNSView(context: Context) -> FocusView { FocusView() }
    func updateNSView(_ view: FocusView, context: Context) {}
}

struct SixDotHandle: View {
    var body: some View { Canvas { context, _ in
        for x in [9.0, 16.0] { for y in [9.0, 16.0, 23.0] { context.fill(Path(ellipseIn: CGRect(x: x - 1.5, y: y - 1.5, width: 3, height: 3)), with: .color(.secondary)) } }
    }.accessibilityHidden(true) }
}
