import SwiftUI

struct LibraryRoute: Equatable {
    var section = "Everything"
    var collection = ""
    var note: String? = nil
    var image: String? = nil
}
struct NavigationTrail {
    private(set) var routes = [LibraryRoute()]
    private(set) var index = 0
    var canBack: Bool { index > 0 }
    var canForward: Bool { index + 1 < routes.count }
    mutating func record(_ route: LibraryRoute) {
        guard routes[index] != route else { return }
        routes = Array(routes.prefix(index + 1)); routes.append(route); index += 1
    }
    mutating func step(_ offset: Int) -> LibraryRoute? {
        let next = index + offset
        guard routes.indices.contains(next) else { return nil }
        index = next; return routes[index]
    }
}
struct ShortcutEntry: Identifiable {
    var id: String; var title: String; var key: String; var symbols: String
    var shift = false
}
enum PebbleShortcuts {
    static let entries: [ShortcutEntry] = [
        .init(id: "sidebar", title: "Show / hide sidebar", key: "s", symbols: "⌘ S"),
        .init(id: "search", title: "Search library", key: "k", symbols: "⌘ K"),
        .init(id: "new", title: "New note", key: "n", symbols: "⌘ N"),
        .init(id: "find", title: "Find in note / search", key: "f", symbols: "⌘ F"),
        .init(id: "back", title: "Go back", key: "[", symbols: "⌘ ["),
        .init(id: "forward", title: "Go forward", key: "]", symbols: "⌘ ]"),
        .init(id: "everything", title: "Everything", key: "1", symbols: "⌘ 1"),
        .init(id: "notes", title: "Notes", key: "2", symbols: "⌘ 2"),
        .init(id: "tasks", title: "Tasks", key: "3", symbols: "⌘ 3"),
        .init(id: "images", title: "Images", key: "4", symbols: "⌘ 4"),
        .init(id: "collection", title: "New collection", key: "n", symbols: "⇧ ⌘ N", shift: true),
        .init(id: "format", title: "Show / hide formatting", key: "f", symbols: "⇧ ⌘ F", shift: true)
    ]
    static func send(_ id: String) { NotificationCenter.default.post(name: Notification.Name("pebbleShortcut"), object: id) }
}
struct ShortcutsPanel: View {
    var body: some View {
        VStack(spacing: 0) {
            ForEach(PebbleShortcuts.entries + [.init(id: "settings", title: "Settings", key: ",", symbols: "⌘ ,"), .init(id: "export", title: "Export backup", key: "e", symbols: "⇧ ⌘ E")]) { shortcut in
                HStack { Text(shortcut.title).font(.system(size: 13)); Spacer(); Text(shortcut.symbols).font(.system(size: 12, weight: .medium, design: .monospaced)).padding(.horizontal, 10).padding(.vertical, 6).background(Color.primary.opacity(0.06), in: RoundedRectangle(cornerRadius: 6)).overlay(RoundedRectangle(cornerRadius: 6).stroke(Color.primary.opacity(0.09), lineWidth: 0.5)) }.padding(.vertical, 9)
                if shortcut.id != "export" { SubtleDivider() }
            }
        }
    }
}
