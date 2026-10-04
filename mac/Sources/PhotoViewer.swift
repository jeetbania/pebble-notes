import SwiftUI
import AppKit
import ImageIO

struct ViewerPhoto: Identifiable, Equatable {
    var noteId: String; var blockId: String; var attachment: Attachment; var caption: String
    var id: String { noteId + ":" + blockId }
    static func items(note: Note, noteId: String) -> [ViewerPhoto] {
        note.document.filter { $0.kind == "image" }.compactMap { b in guard let a = note.attachments.first(where: { $0.id == b.mediaId && $0.mime.hasPrefix("image/") }) else { return nil }; return ViewerPhoto(noteId: noteId, blockId: b.id, attachment: a, caption: b.caption) }
    }
}
struct PhotoViewer: View {
    let initial: Attachment; let items: [ViewerPhoto]; @ObservedObject var store: NoteStore; var leadingInset: CGFloat = 24
    var done: () -> Void; var openNote: (String) -> Void
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @AppStorage("calmMotion") private var calm = false
    @LeafState<Int> private var selection = 0
    @LeafState<Double> private var zoom = 1
    @LeafState<CGSize> private var pan = .zero
    @LeafState<String> private var caption = ""
    @LeafState<Bool> private var ready = false
    @LeafState<Bool> private var controlsVisible = true
    @LeafState<Bool> private var controlsHovered = false
    @LeafState<Int> private var controlsGeneration = 0
    @LeafState<[ViewerPhoto]?> private var snapshot = nil
    var photos: [ViewerPhoto] { snapshot ?? items }
    var current: ViewerPhoto? { photos.indices.contains(selection) ? photos[selection] : nil }
    var movement: Animation? { reduceMotion || calm ? nil : .spring(response: 0.42, dampingFraction: 0.78) }
    var body: some View {
        ZStack(alignment: .top) {
            GeometryReader { g in
                ZStack {
                    ForEach(Array(photos.enumerated()), id: \.element.id) { i, p in
                        let distance = i - selection
                        if abs(distance) <= 2 {
                            let size = fitted(p, maxWidth: g.size.width * 0.70, maxHeight: max(60, g.size.height - 176))
                            Image(nsImage: NSImage(contentsOf: store.media.appendingPathComponent(p.attachment.id)) ?? NSImage()).resizable().aspectRatio(contentMode: .fit)
                                .frame(width: size.width * (distance == 0 ? zoom : 1), height: size.height * (distance == 0 ? zoom : 1))
                                .clipShape(RoundedRectangle(cornerRadius: 10))

                                .shadow(color: .black.opacity(distance == 0 ? 0.14 : 0.04), radius: 8, y: 5)
                                .scaleEffect(distance == 0 ? 1 : 0.14)
                                .rotation3DEffect(.degrees(distance == 0 ? 0 : distance > 0 ? -18 : 18), axis: (x: 0, y: 1, z: 0))
                                .offset(x: distance == 0 ? pan.width : CGFloat(distance > 0 ? 1 : -1) * g.size.width * 0.40, y: distance == 0 ? pan.height : 0)
                                .opacity(abs(distance) > 1 || zoom > 1.01 && distance != 0 ? 0 : distance == 0 ? 1 : 0.72)
                                .zIndex(distance == 0 ? 2 : 1).allowsHitTesting(abs(distance) <= 1)
                                
                                .accessibilityLabel(p.attachment.name)
                        }
                    }
                }.frame(width: g.size.width, height: g.size.height).clipped().contentShape(Rectangle()).overlay { PhotoInteraction(zoom: $zoom, pan: $pan, previous: { choose(selection - 1) }, next: { choose(selection + 1) }, interacted: { revealControls() }) }
            }
            VStack {             HStack(spacing: 10) {
                HStack(spacing: 0) {
                    GlassIcon(icon: "chevron.left", label: "Back to library") { commitCaption(); done() }
                    GlassIcon(icon: "chevron.right", label: "Next image") { choose(selection + 1) }.disabled(selection >= photos.count - 1)
                }.leafGlass(in: Capsule())
                Spacer(); Text(current?.attachment.name ?? initial.name).font(.system(size: 12, weight: .medium)).foregroundStyle(.secondary).lineLimit(1); Spacer()
                Menu {
                    if let p = current {
                        Button("Open Note") { commitCaption(); openNote(p.noteId) }
                        Button("Copy Image") { if let image = NSImage(contentsOf: store.media.appendingPathComponent(p.attachment.id)) { NSPasteboard.general.clearContents(); NSPasteboard.general.writeObjects([image]) } }
                        Button("Save Image…") { let panel = NSSavePanel(); panel.nameFieldStringValue = p.attachment.name; if panel.runModal() == .OK, let url = panel.url { do { try Data(contentsOf: store.media.appendingPathComponent(p.attachment.id)).write(to: url, options: .atomic) } catch { store.error = error.localizedDescription } } }
                    }
                } label: { Image(systemName: "ellipsis").font(.system(size: 16, weight: .medium)).frame(width: 36, height: 36) }
                    .menuStyle(.button).buttonStyle(SoftButtonStyle(radius: 18)).menuIndicator(.hidden).frame(width: 36, height: 36).modifier(HoverSurface()).leafGlass(in: Circle()).accessibilityLabel("Image options")
                GlassIcon(icon: "xmark", label: "Close image") { commitCaption(); done() }.leafGlass(in: Circle())
            }.padding(.leading, leadingInset).padding(.trailing, 10).frame(height: 52)
; Spacer() }.allowsHitTesting(true)
            VStack { Spacer();
            if zoom > 1.01 {
                ZStack {
                    Color.clear.contentShape(Rectangle())
                    HStack(spacing: 10) {
                    GlassIcon(icon: "minus", label: "Zoom out") { zoom = max(1, zoom - 0.25) }
                    Slider(value: $zoom, in: 1...5).frame(width: 140).accessibilityLabel("Image zoom")
                    GlassIcon(icon: "plus", label: "Zoom in") { zoom = min(5, zoom + 0.25) }
                    Button("Fit") { zoom = 1 }.buttonStyle(SoftButtonStyle()).padding(.trailing, 12)
                }.padding(.horizontal, 6).padding(.vertical, 3).leafGlass(in: Capsule()).opacity(controlsVisible ? 1 : 0).allowsHitTesting(controlsVisible).animation(.easeOut(duration: 0.2), value: controlsVisible)
                }.frame(width: 310, height: 58).contentShape(Rectangle()).onHover { hovering in controlsHovered = hovering; if hovering { revealControls() } else { scheduleControls() } }.simultaneousGesture(TapGesture().onEnded { revealControls() })
            } else {
                TextField("Add caption", text: $caption, axis: .vertical).textFieldStyle(.plain).multilineTextAlignment(.center).font(.system(size: 14)).lineLimit(1...2).frame(maxWidth: 420).onSubmit { commitCaption() }.onChange(of: caption) { _, _ in if ready { commitCaption() } }.padding(.horizontal, 12).padding(.vertical, 16).frame(minHeight: 64)
            }
            }.padding(.bottom, 18)
        }
        .animation(movement, value: selection)
        .onAppear { snapshot = items; selection = photos.firstIndex(where: { $0.attachment.id == initial.id }) ?? 0; caption = current?.caption ?? ""; ready = true }
        .onDisappear { if ready { commitCaption() } }
        .onChange(of: zoom) { _, value in if value <= 1.01 { pan = .zero }; revealControls() }
        .onExitCommand { commitCaption(); done() }
        .background { ViewerKeys(previous: { choose(selection - 1) }, next: { choose(selection + 1) }, close: { commitCaption(); done() }).frame(width: 0, height: 0) }
    }
    func revealControls() { controlsVisible = true; scheduleControls() }
    func scheduleControls() { controlsGeneration += 1; let generation = controlsGeneration; Task { try? await Task.sleep(for: .seconds(2)); if controlsGeneration == generation && !controlsHovered { controlsVisible = false } } }
    func fitted(_ p: ViewerPhoto, maxWidth: CGFloat, maxHeight: CGFloat) -> CGSize {
        guard let source = CGImageSourceCreateWithURL(store.media.appendingPathComponent(p.attachment.id) as CFURL, nil), let data = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any], let width = data[kCGImagePropertyPixelWidth] as? NSNumber, let height = data[kCGImagePropertyPixelHeight] as? NSNumber else { return CGSize(width: maxWidth, height: maxHeight) }
        let w = width.doubleValue, h = height.doubleValue; let fit = min(maxWidth / w, maxHeight / h)
        return CGSize(width: w * fit, height: h * fit)
    }
    func choose(_ index: Int) { guard photos.indices.contains(index), index != selection else { return }; commitCaption(); withAnimation(movement) { selection = index; zoom = 1; pan = .zero; caption = store.uniqueHeads.first(where: { $0.noteId == photos[index].noteId })?.note.document.first(where: { $0.id == photos[index].blockId })?.caption ?? photos[index].caption } }
    func commitCaption() {
        guard let p = current else { return }
        let live = store.uniqueHeads.first(where: { $0.noteId == p.noteId })?.note.document.first(where: { $0.id == p.blockId })?.caption
        guard live != caption else { return }
        if store.selected == p.noteId { store.changeBlock(p.blockId) { $0.caption = caption }; store.flush() }
        else { store.mutate(p.noteId) { $0.editBlock(p.blockId) { $0.caption = caption } } }
    }
}
struct ViewerKeys: NSViewRepresentable {
    var previous: () -> Void; var next: () -> Void; var close: () -> Void
    class Keys: NSView {
        var monitor: Any?; var previous: () -> Void = {}; var next: () -> Void = {}; var close: () -> Void = {}
        override func viewDidMoveToWindow() {
            if let monitor { NSEvent.removeMonitor(monitor); self.monitor = nil }
            guard window != nil else { return }
            monitor = NSEvent.addLocalMonitorForEvents(matching: .keyDown) { [weak self] event in
                guard let self, self.window === NSApp.keyWindow, !(self.window?.firstResponder is NSTextView) else { return event }
                if event.keyCode == 123 { self.previous(); return nil }; if event.keyCode == 124 { self.next(); return nil }; if event.keyCode == 53 { self.close(); return nil }; return event
            }
        }
        deinit { if let monitor { NSEvent.removeMonitor(monitor) } }
    }
    func makeNSView(context: Context) -> Keys { Keys() }
    func updateNSView(_ view: Keys, context: Context) { view.previous = previous; view.next = next; view.close = close }
}
struct PhotoInteraction: NSViewRepresentable {
    @Binding var zoom: Double; @Binding var pan: CGSize; var previous: () -> Void; var next: () -> Void; var interacted: () -> Void = {}
    class Surface: NSView {
        var parent: PhotoInteraction?; var moved = false; var tracking: NSTrackingArea?; var grabPoint = NSPoint.zero; var grabPan = CGSize.zero
        override var isFlipped: Bool { true }
        override func updateTrackingAreas() { super.updateTrackingAreas(); if let tracking { removeTrackingArea(tracking) }; tracking = NSTrackingArea(rect: .zero, options: [.inVisibleRect, .activeInKeyWindow, .cursorUpdate, .mouseEnteredAndExited], owner: self); addTrackingArea(tracking!) }
        override func cursorUpdate(with event: NSEvent) { guard let p = parent else { return }; let x = convert(event.locationInWindow, from: nil).x; (p.zoom <= 1.01 && (x < bounds.width * 0.15 || x > bounds.width * 0.85) ? NSCursor.pointingHand : p.zoom > 1.01 ? NSCursor.openHand : NSCursor.zoomIn).set() }
        override func mouseEntered(with event: NSEvent) { cursorUpdate(with: event) }
        override func resetCursorRects() { guard let p = parent else { return }; addCursorRect(bounds, cursor: p.zoom > 1.01 ? .openHand : .zoomIn) }
        override func mouseDown(with event: NSEvent) { moved = false; grabPoint = convert(event.locationInWindow, from: nil); grabPan = parent?.pan ?? .zero; if parent?.zoom ?? 1 > 1.01 { NSCursor.closedHand.set() }; parent?.interacted() }
        override func mouseDragged(with event: NSEvent) { guard let p = parent, p.zoom > 1 else { return }; moved = true; NSCursor.closedHand.set(); let point = convert(event.locationInWindow, from: nil); p.pan = CGSize(width: grabPan.width + point.x - grabPoint.x, height: grabPan.height + point.y - grabPoint.y) }
        override func mouseUp(with event: NSEvent) { guard let p = parent else { return }; if p.zoom > 1.01 { NSCursor.openHand.set(); p.interacted(); return }; guard !moved else { return }; let x = convert(event.locationInWindow, from: nil).x; if p.zoom <= 1.01 && x < bounds.width * 0.15 { p.previous(); return }; if p.zoom <= 1.01 && x > bounds.width * 0.85 { p.next(); return }; withAnimation(NSWorkspace.shared.accessibilityDisplayShouldReduceMotion || UserDefaults.standard.bool(forKey: "calmMotion") ? nil : .spring(response: 0.35, dampingFraction: 0.88)) { p.zoom = p.zoom > 1.01 ? 1 : 2; p.pan = .zero } }
        override func magnify(with event: NSEvent) { guard let p = parent, true else { return }; p.zoom = min(5, max(1, p.zoom * (1 + event.magnification))) }
        override func scrollWheel(with event: NSEvent) { guard let p = parent, p.zoom > 1 else { return }; p.pan = CGSize(width: p.pan.width + event.scrollingDeltaX, height: p.pan.height - event.scrollingDeltaY) }
    }
    func makeNSView(context: Context) -> Surface { Surface() }
    func updateNSView(_ view: Surface, context: Context) { view.parent = self; view.window?.invalidateCursorRects(for: view) }
}
