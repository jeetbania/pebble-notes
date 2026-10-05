import SwiftUI
import AppKit

struct EditorToolbar: View {
    @ObservedObject var store: NoteStore
    var showsStyle = true
    var styleMenuScheme: ColorScheme? = nil
    @StateObject private var editorActions = EditorActions.shared
    var body: some View {
        HStack(spacing: 0) {
            if showsStyle { NoteStyleButton(store: store, interfaceScheme: styleMenuScheme) }
            Divider().frame(height: 16).padding(.horizontal, 4)
            Menu { ForEach(["Title", "Subtitle", "Headline", "Body"], id: \.self) { style in Button(style, systemImage: "textformat") { EditorActions.shared.textStyle(style.lowercased()) }.labelStyle(.titleAndIcon) } } label: { HStack { Text(editorActions.styleLabel).font(.system(size: 12)); Spacer(); Image(systemName: "chevron.up.chevron.down").font(.system(size: 9)) }.frame(width: 76).padding(.horizontal, 10) }.menuStyle(.button).buttonStyle(SoftButtonStyle(radius: 18)).modifier(HoverSurface()).menuIndicator(.hidden).fixedSize().tint(.primary).help("Text style")
            Divider().frame(height: 16).padding(.horizontal, 4)
            ForEach([("bold", "Bold"), ("italic", "Italic"), ("strikethrough", "Strikethrough")], id: \.0) { item in GlassIcon(icon: item.0, label: item.1) { EditorActions.shared.format(item.0 == "strikethrough" ? "strike" : item.0) } }
            Divider().frame(height: 16).padding(.horizontal, 4)
            GlassIcon(icon: "link", label: "Add link") { EditorActions.shared.link() }
            Divider().frame(height: 16).padding(.horizontal, 4)
            GlassIcon(icon: "list.bullet", label: "Bulleted line") { store.setList("bullet") }
            GlassIcon(icon: "list.number", label: "Numbered line") { store.setList("number") }
            GlassIcon(icon: "checklist", label: "Checklist") { store.setList("check") }
        }.padding(.horizontal, 12).padding(.vertical, 2).leafGlass(in: Capsule())
    }
}
struct NoteWindowView: View {
    @ObservedObject var store: NoteStore
    @Environment(\.colorScheme) private var scheme
    @LeafState<Attachment?> private var preview = nil
    @LeafState<Bool> private var conflict = false
    var body: some View {
        ZStack(alignment: .top) {
            WindowMaterial().overlay(scheme == .dark ? Color.black.opacity(0.28) : Color.white.opacity(0.28))
            if let note = store.current, let id = store.selected {
                DocumentEditor(store: store, note: note, noteId: id, onPreview: { preview = $0 }, onConflict: { conflict = true }).environment(\.colorScheme, note.style?.documentScheme ?? scheme)
                EditorToolbar(store: store, styleMenuScheme: scheme).environment(\.colorScheme, note.style?.chromeScheme ?? scheme).padding(.top, 12)
                if let a = preview { PhotoViewer(initial: a, items: ViewerPhoto.items(note: note, noteId: id), store: store, done: { preview = nil }, openNote: { _ in preview = nil }) }
            }
        }.frame(minWidth: 660, minHeight: 460).background(WindowChrome()).background(EditorFocusDismissal().frame(width: 0, height: 0)).ignoresSafeArea(.container, edges: .top).tint(LeafPalette.accent)
        .environmentObject(store).panePresented(isPresented: $conflict) { VStack(spacing: 16) { Text("Choose a saved version").font(.headline); ForEach(store.heads.filter { $0.noteId == store.selected }) { r in Button(r.note.displayTitle + " · " + String(r.note.text.prefix(80))) { store.resolve(r); conflict = false } }; Button("Keep both for now") { conflict = false } }.padding(24) }
        .onReceive(NotificationCenter.default.publisher(for: NSWindow.didBecomeKeyNotification)) { _ in store.reload() }
        .onReceive(NotificationCenter.default.publisher(for: NSWindow.willCloseNotification)) { _ in store.flush() }
        .onDisappear { store.flush() }
    }
}
