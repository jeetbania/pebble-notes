import SwiftUI
import AppKit
import PDFKit

struct BlockFrames: PreferenceKey {
    static var defaultValue: [String: CGRect] = [:]
    static func reduce(value: inout [String: CGRect], nextValue: () -> [String: CGRect]) { value.merge(nextValue(), uniquingKeysWith: { _, new in new }) }
}

struct DocumentEditor: View {
    @ObservedObject var store: NoteStore
    @AppStorage("typeBody") private var typeBody = 15.0
    @AppStorage("typeHeadline") private var typeHeadline = 17.0
    @AppStorage("typeSubtitle") private var typeSubtitle = 19.0
    @AppStorage("typeTitle") private var typeTitle = 26.0
    @AppStorage("editorWidth") private var editorWidth = 700.0
    var note: Note; var noteId: String; var onPreview: (Attachment) -> Void; var onConflict: () -> Void
    @LeafState<Attachment?> private var filePreview = nil
    @LeafState<String?> private var draggedBlock = nil
    @LeafState<String?> private var hoveredBlock = nil
    @LeafState<String?> private var blockOptions = nil
    @LeafState<[String: CGRect]> private var blockFrames = [:]
    @LeafState<CGSize> private var blockTranslation = .zero
    @LeafState<[String: Double]> private var columnWidths = [:]
    @LeafState<Double?> private var columnStart = nil
    var body: some View {
        GeometryReader { geometry in
            let width = min(editorWidth, max(300.0, geometry.size.width - 144))
            LeafScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    if store.heads.filter({ $0.noteId == noteId }).count > 1 { Button("Edits from both devices · review", action: onConflict).buttonStyle(.plain).foregroundStyle(.orange).font(.system(size: 12)) }
                    TextField("Untitled note", text: Binding(get: { store.current?.title ?? "" }, set: { value in store.update(undoKey: "title") { $0.title = value } }), axis: .vertical).font(.system(size: typeTitle * note.textScale, weight: .bold)).textFieldStyle(.plain).padding(.bottom, 10)
                    if !note.tags.isEmpty { Text(note.tags.map { "#" + $0 }.joined(separator: "  ")).font(.system(size: 12)).foregroundStyle(.secondary) }
                    ForEach(Array(note.document.enumerated()).filter { note.isVisible($0.element, dragging: draggedBlock) }, id: \.element.id) { index, block in
                        ZStack(alignment: .topLeading) {
                            blockView(block, index: index, width: width).frame(maxWidth: .infinity, alignment: .leading)
                            Button { blockOptions = block.id } label: { SixDotHandle().frame(width: 26, height: 32).contentShape(Rectangle()) }
                                .buttonStyle(.plain).contextMenu { blockMenu(block) }
                                .opacity(hoveredBlock == block.id || draggedBlock == block.id || store.activeBlock == block.id ? 0.85 : 0)
                                .offset(x: block.kind == "toggle" ? -62 : -30).help("Block options · drag to reorder")
                                .accessibilityLabel("Block options")
                                .highPriorityGesture(DragGesture(minimumDistance: 5, coordinateSpace: .named("noteBlocks")).onChanged { value in draggedBlock = block.id; blockTranslation = value.translation }.onEnded { value in
                                    if let target = blockFrames.first(where: { $0.key != block.id && $0.value.contains(value.location) })?.key { withAnimation(.spring(response: 0.35, dampingFraction: 0.82)) { store.update { $0.moveGroup(block.id, before: target) } } }; draggedBlock = nil; blockTranslation = .zero
                                })
                        }
                            .background(GeometryReader { proxy in Color.clear.preference(key: BlockFrames.self, value: [block.id: proxy.frame(in: .named("noteBlocks"))]) })
                            .offset(draggedBlock == block.id ? blockTranslation : .zero).zIndex(draggedBlock == block.id ? 10 : 0).padding(.leading, CGFloat(note.depth(block) * 18)).padding(.top, gap(before: index)).padding(.leading, 64).onHover { hoveredBlock = $0 ? block.id : nil }.padding(.leading, -64).animation(.easeOut(duration: 0.15), value: hoveredBlock)
                    }
                    HStack(spacing: 12) {
                        Button { store.addBlock("text") } label: { Label("Text", systemImage: "plus") }
                        Button { store.addBlock("toggle") } label: { Label("Toggle", systemImage: "arrowtriangle.right.fill") }
                        Button { store.addBlock("table") } label: { Label("Table", systemImage: "tablecells") }
                        Button { store.addFiles() } label: { Label("Attach", systemImage: "paperclip") }
                    }.font(.system(size: 12)).buttonStyle(SoftButtonStyle()).foregroundStyle(.secondary).padding(.top, 12)
                }.frame(width: width, alignment: .leading).padding(.top, 82).padding(.bottom, 90).frame(maxWidth: .infinity)
            }.scrollIndicators(.never).coordinateSpace(name: "noteBlocks").onPreferenceChange(BlockFrames.self) { blockFrames = $0 }
        }.popover(isPresented: Binding(get: { blockOptions != nil }, set: { if !$0 { blockOptions = nil } })) {
            VStack(alignment: .leading, spacing: 4) { if let id = blockOptions, let block = store.current?.document.first(where: { $0.id == id }) { blockMenu(block) } }.padding(12).frame(width: 200).buttonStyle(MaterialActionStyle())
        }.onChange(of: store.current) { _, _ in blockOptions = nil }.panePresented(item: $filePreview) { a in FilePreview(attachment: a, media: store.media, onDone: { filePreview = nil }) }
    }
    func applyCommand(_ command: String, to id: String) {
        store.changeBlock(id) { b in
            b.kind = ["title", "subtitle", "headline", "quote"].contains(command) ? "text" : command
            if command == "table" { b.cells = [["", ""], ["", ""]] }
            if command == "toggle" { b.collapsed = false; b.indent = 0 }
            if command == "quote" { b.indent = 1 }
        }
    }
    func styledBlock(_ block: DocumentBlock) -> Note { var value = block.asNote; value.textScale = note.textScale; return value }
    func gap(before index: Int) -> CGFloat { guard index > 0 else { return 0 }; let block = note.document[index]; let previous = note.document[index - 1]; if block.spans.contains(where: { ["headline", "title", "subtitle"].contains($0.kind) }) { return 22 }; if ["check", "bullet", "number"].contains(block.kind) { return previous.kind == block.kind ? 3 : 7 }; return previous.spans.contains(where: { ["headline", "title", "subtitle"].contains($0.kind) }) ? 6 : 12 }
    @ViewBuilder func blockView(_ block: DocumentBlock, index: Int, width: CGFloat) -> some View {
        if block.isText {
            let height = max(32, styledBlock(block).attributed.boundingRect(with: NSSize(width: width - 42 - CGFloat(block.indent * 18), height: .greatestFiniteMagnitude), options: [.usesLineFragmentOrigin, .usesFontLeading]).height + 4)
            VStack(alignment: .leading, spacing: 4) {
            HStack(alignment: height <= 34 ? .center : .top, spacing: 10) {


                if block.kind == "check" { Button { store.changeBlock(block.id) { $0.checked.toggle() } } label: { Image(systemName: block.checked ? "checkmark.circle.fill" : "circle").font(.system(size: 20)).foregroundStyle(block.checked ? LeafPalette.accent : Color.secondary) }.frame(width: 32, height: 32).buttonStyle(SoftButtonStyle()) }
                else if block.kind == "bullet" || block.kind == "number" { Text(block.kind == "bullet" ? "•" : "\(note.document.prefix(index + 1).filter { $0.kind == "number" }.count).").foregroundStyle(.secondary).frame(minWidth: 20).padding(.top, 2) }
                RichEditor(note: styledBlock(block), onEdit: { text, spans in store.editBlock(block.id, text: text, spans: spans) }, onImages: { store.importFiles($0) }, onFocus: { store.activeBlock = block.id }, onEnter: { splitList(block) }, completed: block.kind == "check" && block.checked, onCommand: { command in applyCommand(command, to: block.id) }, blockId: block.id, onBackspace: { backspace(block.id) }).id(noteId + block.id)
                    .frame(maxWidth: .infinity).frame(height: height).opacity(block.checked ? 0.55 : 1)
            }.padding(.leading, CGFloat(block.indent * 18)).overlay(alignment: .leading) {
                if block.kind == "toggle" { GlassIcon(icon: block.collapsed == true || draggedBlock == block.id ? "arrowtriangle.right.fill" : "arrowtriangle.down.fill", label: "Expand or collapse toggle", size: 11) { store.changeBlock(block.id) { $0.collapsed = !($0.collapsed ?? false) } }.offset(x: -36) }
            }
            if block.kind == "toggle", block.collapsed != true, draggedBlock != block.id, note.descendants(of: block.id).count == 1 { Button { store.addChild(to: block.id); focusBlock(store.activeBlock) } label: { Label("Add inside toggle", systemImage: "plus").font(.caption).foregroundStyle(.secondary) }.buttonStyle(SoftButtonStyle()) }
            }

        } else if block.kind == "table" { table(block) }
        else if block.kind == "divider" { SubtleDivider().padding(.vertical, 8) }
        else if let attachment = note.attachments.first(where: { $0.id == block.mediaId }) {
            VStack(alignment: .leading, spacing: 8) {
                if block.kind == "image" { InlinePhoto(attachment: attachment, media: store.media, width: block.presentation == "large" ? width : min(280, width)) { onPreview(attachment) } }
                else { Button { filePreview = attachment } label: { HStack(spacing: 12) { Image(systemName: "doc").font(.system(size: 24)); VStack(alignment: .leading, spacing: 4) { Text(attachment.name).font(.system(size: 14, weight: .medium)); Text("Open attachment").font(.system(size: 12)).foregroundStyle(.secondary) }; Spacer() }.padding(16).background(.quaternary, in: RoundedRectangle(cornerRadius: 14)) }.buttonStyle(.plain) }
                TextField("Add a caption…", text: Binding(get: { block.caption }, set: { value in store.update(undoKey: "caption:" + block.id) { $0.editBlock(block.id) { $0.caption = value } } })).textFieldStyle(.plain).font(.system(size: 12)).foregroundStyle(.secondary)
            }.padding(.vertical, 6)
        }
    }
    func splitList(_ block: DocumentBlock) -> Bool {
        guard let block = store.current?.document.first(where: { $0.id == block.id }) else { return false }
        if block.kind == "toggle" { store.addChild(to: block.id); focusBlock(store.activeBlock); return true }
        guard block.kind != "text", let view = EditorActions.shared.view else { return false }
        let position = min(view.selectedRange().location, block.text.utf16.count)
        if block.text.isEmpty { store.changeBlock(block.id) { $0.kind = "text" }; return true }
        let text = block.text as NSString
        var next = DocumentBlock(); next.kind = block.kind; next.indent = block.indent; next.parentId = block.parentId; next.text = text.substring(from: position); next.spans = clippedSpans(block.spans, start: position, length: text.length - position)
        store.update { n in var blocks = n.document; if let i = blocks.firstIndex(where: { $0.id == block.id }) { blocks[i].text = text.substring(to: position); blocks[i].spans = clippedSpans(block.spans, start: 0, length: position); blocks.insert(next, at: i + 1); n.blocks = blocks } }
        store.activeBlock = next.id
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) { findNextEditor(after: view) }
        return true
    }
    func focusBlock(_ id: String?, attempts: Int = 0) {
        guard let id, attempts < 8 else { return }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.03) {
            guard let window = NSApp.keyWindow, let root = window.contentView else { return }
            func find(_ node: NSView) -> LeafTextView? { if let text = node as? LeafTextView, text.identifier?.rawValue == id { return text }; return node.subviews.lazy.compactMap(find).first }
            if let text = find(root) { window.makeFirstResponder(text); text.setSelectedRange(NSRange(location: 0, length: 0)) } else { focusBlock(id, attempts: attempts + 1) }
        }
    }
    func backspace(_ id: String) -> Bool {
        guard let block = store.current?.document.first(where: { $0.id == id }), block.text.isEmpty, block.kind == "toggle" else { return false }
        store.update { n in var content = n.document; if n.descendants(of: id).count == 1 { content.removeAll { $0.id == id } } else { for i in content.indices { if content[i].parentId == id { content[i].parentId = block.parentId } }; content.removeAll { $0.id == id } }; n.blocks = content }
        focusBlock(store.current?.document.first(where: { $0.isText })?.id); return true
    }
    func findNextEditor(after view: NSTextView) {
        guard let window = view.window, let root = window.contentView else { return }
        func editors(_ node: NSView) -> [LeafTextView] { (node as? LeafTextView).map { [$0] } ?? node.subviews.flatMap(editors) }
        let all = editors(root); if let i = all.firstIndex(where: { $0 === view }), i + 1 < all.count { window.makeFirstResponder(all[i + 1]); all[i + 1].setSelectedRange(NSRange(location: 0, length: 0)) }
    }
    func columnWidth(_ block: String, _ column: Int) -> Double { let key = "tableWidth:" + noteId + ":" + block + ":" + String(column); return columnWidths[key] ?? (UserDefaults.standard.object(forKey: key) as? Double ?? 160) }
    func table(_ block: DocumentBlock) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            LeafScrollView(.horizontal) { VStack(spacing: 0) { ForEach(block.cells.indices, id: \.self) { row in HStack(spacing: 0) { ForEach(block.cells[row].indices, id: \.self) { col in TextField("", text: Binding(get: { block.cells[row][col] }, set: { value in store.update(undoKey: "cell:\(block.id):\(row):\(col)") { $0.editBlock(block.id) { $0.cells[row][col] = value } } }), axis: .vertical).textFieldStyle(.plain).font(.system(size: 15, weight: row == 0 ? .medium : .regular)).padding(12).frame(width: columnWidth(block.id, col)).frame(minHeight: 42).background(Color.primary.opacity(row == 0 ? 0.05 : 0.02)).border(Color.primary.opacity(0.1), width: 0.5).overlay(alignment: .trailing) { Color.clear.frame(width: 6).contentShape(Rectangle()).onHover { if $0 { NSCursor.resizeLeftRight.push() } else { NSCursor.pop() } }.gesture(DragGesture(minimumDistance: 0, coordinateSpace: .global).onChanged { v in let key = "tableWidth:" + noteId + ":" + block.id + ":" + String(col); if columnStart == nil { columnStart = columnWidth(block.id, col) }; let width = min(600, max(80, columnStart! + v.translation.width)); columnWidths[key] = width; UserDefaults.standard.set(width, forKey: key) }.onEnded { _ in columnStart = nil }) } } } } } }.clipShape(RoundedRectangle(cornerRadius: 10))
            HStack { Button("+ Row") { store.changeBlock(block.id) { if $0.cells.count < 100 { $0.cells.append(Array(repeating: "", count: $0.cells[0].count)) } } }; Button("+ Column") { store.changeBlock(block.id) { if $0.cells[0].count < 12 { $0.cells = $0.cells.map { $0 + [""] } } } } }.font(.system(size: 12)).buttonStyle(SoftButtonStyle()).foregroundStyle(.secondary)
        }
    }
    @ViewBuilder func blockMenu(_ block: DocumentBlock) -> some View {
        Button("Move Up") { store.moveBlock(block.id, by: -1) }; Button("Move Down") { store.moveBlock(block.id, by: 1) }
        Button("Duplicate") { store.duplicateBlock(block.id) }
        if block.isText { Button("Indent") { store.changeBlock(block.id) { $0.indent = min(8, $0.indent + 1) } }; Button("Outdent") { store.changeBlock(block.id) { $0.indent = max(0, $0.indent - 1) } }; Button("Highlight Text") { store.changeBlock(block.id) { if $0.spans.contains(where: { $0.kind == "highlight" }) { $0.spans.removeAll { $0.kind == "highlight" } } else { $0.spans.append(TextSpan(start: 0, length: $0.text.utf16.count, kind: "highlight")) } } } }
        if block.kind == "image" { Button(block.presentation == "large" ? "Small Image" : "Large Image") { store.changeBlock(block.id) { $0.presentation = $0.presentation == "large" ? "small" : "large" } } }
        if block.kind == "table" { Button("Remove Last Row") { store.changeBlock(block.id) { if $0.cells.count > 1 { $0.cells.removeLast() } } }; Button("Remove Last Column") { store.changeBlock(block.id) { if $0.cells[0].count > 1 { $0.cells = $0.cells.map { Array($0.dropLast()) } } } } }
        Divider(); Button("Delete block", role: .destructive) { store.removeBlock(block.id) }
    }
}
struct DailyTools: View {
    @ObservedObject var store: NoteStore
    var onDone: () -> Void = {}
    @LeafState<String> private var query = ""
    @LeafState<String> private var tags = ""
    @LeafState<[Revision]> private var history: [Revision] = []
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack { Text("Find, Tags & History").font(.title2.bold()); Spacer(); Button("Done") { store.update { $0.tags = tags.split(separator: ",").map(String.init) }; onDone() } }
            TextField("Find in this note", text: $query).textFieldStyle(.roundedBorder)
            if !query.isEmpty { LeafScrollView { ForEach(store.current?.document.filter { ($0.text + " " + $0.caption + " " + $0.cells.flatMap { $0 }.joined(separator: " ")).localizedCaseInsensitiveContains(query) } ?? []) { b in Text(b.isText ? b.text : b.kind == "table" ? b.cells.map { $0.joined(separator: " | ") }.joined(separator: "\n") : b.caption).font(.system(size: 14)).textSelection(.enabled).padding(10).frame(maxWidth: .infinity, alignment: .leading).background(.quaternary, in: RoundedRectangle(cornerRadius: 10)) } }.frame(maxHeight: 130) }
            TextField("Tags, separated by commas", text: $tags).textFieldStyle(.roundedBorder)
            Text("Earlier versions").font(.headline)
            LeafScrollView { LazyVStack(alignment: .leading, spacing: 12) { ForEach(history) { revision in VStack(alignment: .leading, spacing: 6) { HStack { Text(Date(timeIntervalSince1970: Double(revision.createdAt) / 1000), style: .date); Text(Date(timeIntervalSince1970: Double(revision.createdAt) / 1000), style: .time); Spacer(); Button("Restore") { store.resolve(revision); onDone() } }; Text(revision.note.text.prefix(200)).font(.system(size: 12)).foregroundStyle(.secondary).lineLimit(3) }.padding(12).background(.quaternary, in: RoundedRectangle(cornerRadius: 12)) } } }
            Text(store.backupStatus).font(.system(size: 11)).foregroundStyle(.secondary)
            Button("Show Automatic Backups") { NSWorkspace.shared.open(store.root.appendingPathComponent("backups")) }
        }.padding(24).frame(width: 580, height: 540).onAppear { tags = store.current?.tags.joined(separator: ", ") ?? ""; store.flush(); history = ((try? store.revisions()) ?? []).filter { $0.noteId == store.selected }.sorted { $0.createdAt > $1.createdAt } }
    }
}

struct FilePreview: View {
    var attachment: Attachment; var media: URL
    var onDone: () -> Void = {}
    var body: some View { VStack(spacing: 16) { HStack { Text(attachment.name).font(.headline); Spacer(); Button("Save Original…") { let panel = NSSavePanel(); panel.nameFieldStringValue = attachment.name; if panel.runModal() == .OK, let destination = panel.url { do { try Data(contentsOf: media.appendingPathComponent(attachment.id)).write(to: destination, options: .atomic) } catch {} } }; Button("Done") { onDone() } }; if attachment.mime == "application/pdf" { NativePDF(url: media.appendingPathComponent(attachment.id)) } else { Spacer(); Image(systemName: "doc").font(.system(size: 60)).foregroundStyle(.secondary); Text("Original file saved on this Mac").foregroundStyle(.secondary); Spacer() } }.padding(24).frame(maxWidth: .infinity).frame(height: 600) }
}
struct NativePDF: NSViewRepresentable {
    var url: URL
    func makeNSView(context: Context) -> PDFView { let view = PDFView(); view.autoScales = true; view.document = PDFDocument(url: url); return view }
    func updateNSView(_ view: PDFView, context: Context) {}
}
