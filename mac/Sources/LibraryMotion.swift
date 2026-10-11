import SwiftUI

struct SidebarSelectionSurface: ViewModifier {
    @Environment(\.colorScheme) private var scheme
    var selected: Bool
    var motion: Animation?
    func body(content: Content) -> some View {
        content.foregroundStyle(scheme == .light ? Color(white: selected ? 0.18 : 0.54) : Color.primary).background(alignment: .leading) {
            GeometryReader { proxy in
                RoundedRectangle(cornerRadius: 8)
                    .fill(scheme == .light ? Color.white.opacity(0.94) : Color.primary.opacity(selected ? 0.10 : 0.055))
                    .overlay(RoundedRectangle(cornerRadius: 8).strokeBorder(scheme == .light ? Color.black.opacity(0.08) : .clear, lineWidth: 0.5))
                    .shadow(color: .black.opacity(scheme == .light ? 0.08 : 0), radius: 2, y: 1)
                    .frame(width: selected ? proxy.size.width : 28, height: selected ? 34 : 28)
                    .offset(x: selected ? 0 : 7, y: selected ? 0 : 3)
                    .animation(motion, value: selected)
            }.allowsHitTesting(false)
        }
    }
}

@MainActor enum CardRevealHistory {
    static var seen: Set<String> = []
}
struct CardEntrance: ViewModifier {
    var route: String // Stable note identity, independent of folder or revision.
    var index: Int
    var reduced: Bool
    @PebbleState private var visible = false
    init(route: String, index: Int, reduced: Bool) { self.route = route; self.index = index; self.reduced = reduced; _visible = PebbleState(wrappedValue: reduced || index >= 16 || CardRevealHistory.seen.contains(route)) }
    func body(content: Content) -> some View {
        content.opacity(visible ? 1 : 0).task(id: route) {
            guard !visible else { CardRevealHistory.seen.insert(route); return }
            if reduced || index >= 16 || CardRevealHistory.seen.contains(route) { visible = true; CardRevealHistory.seen.insert(route); return }
            try? await Task.sleep(for: .milliseconds(min(index, 3) * 18))
            guard !Task.isCancelled else { return }
            withAnimation(.easeOut(duration: 0.16)) { visible = true }
            CardRevealHistory.seen.insert(route)
        }
    }
}
