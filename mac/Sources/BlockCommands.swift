import SwiftUI
import AppKit

struct BlockCommand: Identifiable {
    var id: String; var title: String; var icon: String; var hint: String
    static func suggestions(_ query: String) -> [BlockCommand] {
        let q = query.lowercased().filter { $0.isLetter || $0.isNumber }
        if q.isEmpty { return all }
        let aliases = ["check": "todo task checkbox checklist", "toggle": "toggle collapsible", "title": "heading1 h1 title", "subtitle": "heading2 h2 subtitle", "headline": "heading3 h3 headline", "bullet": "bullet unordered list", "number": "number ordered list", "divider": "divider separator"]
        return all.filter { command in
            let words = (command.title + " " + command.id + " " + (aliases[command.id] ?? "")).lowercased()
            return words.split(separator: " ").contains { $0.hasPrefix(q) } || command.title.lowercased().filter { $0.isLetter || $0.isNumber }.hasPrefix(q)
        }
    }
    static let all: [BlockCommand] = [
        .init(id: "text", title: "Text", icon: "text.alignleft", hint: "Plain writing"),
        .init(id: "title", title: "Heading 1", icon: "textformat.size.larger", hint: "Large heading"),
        .init(id: "subtitle", title: "Heading 2", icon: "textformat.size", hint: "Medium heading"),
        .init(id: "headline", title: "Heading 3", icon: "textformat", hint: "Small heading"),
        .init(id: "bullet", title: "Bulleted list", icon: "list.bullet", hint: "A simple list"),
        .init(id: "number", title: "Numbered list", icon: "list.number", hint: "An ordered list"),
        .init(id: "check", title: "Checklist", icon: "checklist", hint: "Things to do"),
        .init(id: "toggle", title: "Toggle list", icon: "chevron.right", hint: "Collapsible content"),
        .init(id: "table", title: "Table", icon: "tablecells", hint: "Rows and columns"),
        .init(id: "quote", title: "Quote", icon: "quote.opening", hint: "Indented text"),
        .init(id: "divider", title: "Divider", icon: "minus", hint: "Separate sections")
    ]
}
struct SlashMenu: View {
    var commands: [BlockCommand]; var selected: Int; var pick: (String) -> Void
    var body: some View { VStack(alignment: .leading, spacing: 4) {
        Text("Blocks").font(.caption).foregroundStyle(.secondary).padding(.horizontal, 10).padding(.top, 8)
        if commands.isEmpty { Text("No matching commands").foregroundStyle(.secondary).padding(12) }
        LeafScrollView { VStack(spacing: 2) { ForEach(Array(commands.enumerated()), id: \.element.id) { i, command in
            Button { pick(command.id) } label: { HStack(spacing: 10) { Image(systemName: command.icon).frame(width: 24); VStack(alignment: .leading, spacing: 2) { Text(command.title).font(.system(size: 16, weight: .medium)); Text(command.hint).font(.system(size: 13)).foregroundStyle(.secondary) }; Spacer() }.padding(.horizontal, 10).frame(height: 44).background(Color.primary.opacity(i == selected ? 0.08 : 0), in: RoundedRectangle(cornerRadius: 8)) }.buttonStyle(.plain)
        } } }.frame(height: min(320, CGFloat(commands.count * 46 + 4)))
        SubtleDivider(); Text("↑ ↓ to choose   ↩ / tab to insert   esc to close").font(.system(size: 10)).foregroundStyle(.secondary).padding(8)
    }.padding(6).frame(width: 260).background(.regularMaterial) }
}
@MainActor final class SlashController {
    weak var view: LeafTextView?
    var apply: ((String) -> Void)?
    var range = NSRange(location: 0, length: 0)
    var commands: [BlockCommand] = []; var selected = 0
    private let popover = NSPopover()
    private var ignored: String?
    private var host: NSHostingController<SlashMenu>?
    private var lastQuery = ""
    init(_ view: LeafTextView) { self.view = view; popover.behavior = .semitransient; popover.animates = false }
    func inspect() {
        guard let view, apply != nil, view.window?.firstResponder === view else { close(); return }
        let source = view.string as NSString; let caret = min(view.selectedRange().location, source.length)
        let line = source.lineRange(for: NSRange(location: caret, length: 0)); let prefix = source.substring(with: NSRange(location: line.location, length: caret - line.location))
        guard prefix.hasPrefix("/"), prefix.count < 64, !prefix.contains("\n"), prefix != ignored else { if !prefix.hasPrefix("/") { ignored = nil }; close(); return }
        range = NSRange(location: line.location, length: caret - line.location)
        let query = String(prefix.dropFirst()).lowercased(); commands = BlockCommand.suggestions(query); if query != lastQuery { selected = 0; lastQuery = query }; selected = min(selected, max(0, commands.count - 1))
        render()
        if !popover.isShown { let screen = view.firstRect(forCharacterRange: NSRange(location: caret, length: 0), actualRange: nil); let windowRect = view.window!.convertFromScreen(screen); var anchor = view.convert(windowRect, from: nil); anchor.size.width = 1; popover.show(relativeTo: anchor, of: view, preferredEdge: .maxY); view.window?.makeFirstResponder(view) }
    }
    func render() { let menu = SlashMenu(commands: commands, selected: selected, pick: { [weak self] in self?.choose($0) }); if let host { host.rootView = menu } else { let controller = NSHostingController(rootView: menu); host = controller; popover.contentViewController = controller } }
    func key(_ event: NSEvent) -> Bool {
        guard popover.isShown else { return false }
        switch event.keyCode {
        case 53: if let view { ignored = (view.string as NSString).substring(with: range) }; close(); return true
        case 125: selected = min(commands.count - 1, selected + 1); render(); return true
        case 126: selected = max(0, selected - 1); render(); return true
        case 36, 76, 48: if commands.indices.contains(selected) { choose(commands[selected].id) }; return true
        default: return false
        }
    }
    func choose(_ command: String) {
        guard let view else { return }; close(); view.insertText("", replacementRange: range); apply?(command)
        if ["title", "subtitle", "headline", "text"].contains(command) { EditorActions.shared.view = view; EditorActions.shared.textStyle(command == "text" ? "body" : command) }
        else if command != "table" && command != "divider" { view.window?.makeFirstResponder(view) }
    }
    func close() { popover.close() }
}
