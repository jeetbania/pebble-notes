import SwiftUI

struct SidebarSelectionSurface: ViewModifier {
    var selected: Bool
    var motion: Animation?
    func body(content: Content) -> some View {
        content.background(alignment: .leading) {
            GeometryReader { proxy in
                RoundedRectangle(cornerRadius: 8)
                    .fill(Color.primary.opacity(selected ? 0.10 : 0.055))
                    .frame(width: selected ? proxy.size.width : 28, height: selected ? 34 : 28)
                    .offset(x: selected ? 0 : 7, y: selected ? 0 : 3)
                    .animation(motion, value: selected)
            }.allowsHitTesting(false)
        }
    }
}

struct CardEntrance: ViewModifier {
    var route: String
    var index: Int
    var reduced: Bool
    @LeafState private var visible = false
    func body(content: Content) -> some View {
        content.opacity(visible ? 1 : 0).task(id: route) {
            var transaction = Transaction(); transaction.disablesAnimations = true
            withTransaction(transaction) { visible = reduced }
            guard !reduced else { return }
            try? await Task.sleep(for: .milliseconds(min(index, 9) * 18))
            guard !Task.isCancelled else { return }
            withAnimation(.easeOut(duration: 0.16)) { visible = true }
        }
    }
}
