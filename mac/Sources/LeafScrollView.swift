import SwiftUI

private struct ScrollMetrics: Equatable {
    var width: CGFloat = 0; var height: CGFloat = 0
    var contentWidth: CGFloat = 0; var contentHeight: CGFloat = 0
    var x: CGFloat = 0; var y: CGFloat = 0
}
struct LeafScrollView<Content: View>: View {
    let axes: Axis.Set
    let content: Content
    @LeafState private var position = ScrollPosition(edge: .top)
    @LeafState private var metrics = ScrollMetrics()
    @LeafState private var verticalStart: CGFloat? = nil
    @LeafState private var horizontalStart: CGFloat? = nil
    @LeafState private var hoverVertical = false
    @LeafState private var hoverHorizontal = false
    init(_ axes: Axis.Set = .vertical, @ViewBuilder content: () -> Content) { self.axes = axes; self.content = content() }
    var body: some View {
        ScrollView(axes) { content }.scrollIndicators(.never).scrollPosition($position)
            .onScrollGeometryChange(for: ScrollMetrics.self) { g in
                ScrollMetrics(width: g.containerSize.width, height: g.containerSize.height, contentWidth: g.contentSize.width + g.contentInsets.leading + g.contentInsets.trailing, contentHeight: g.contentSize.height + g.contentInsets.top + g.contentInsets.bottom, x: g.contentOffset.x + g.contentInsets.leading, y: g.contentOffset.y + g.contentInsets.top)
            } action: { _, next in metrics = next }
            .overlay(alignment: .topTrailing) {
                if axes.contains(.vertical), metrics.contentHeight > metrics.height + 1, metrics.height > 0 {
                    let length = max(24, metrics.height * metrics.height / metrics.contentHeight - 8)
                    let travel = max(1, metrics.height - length - 8)
                    let distance = metrics.contentHeight - metrics.height
                    Capsule().fill(Color.primary.opacity(hoverVertical || verticalStart != nil ? 0.35 : 0.20)).frame(width: 3, height: length).frame(width: 14).contentShape(Rectangle())
                        .offset(y: 4 + min(1, max(0, metrics.y / distance)) * travel)
                        .onHover { hoverVertical = $0 }
                        .gesture(DragGesture(minimumDistance: 0).onChanged { value in if verticalStart == nil { verticalStart = metrics.y }; position.scrollTo(y: min(distance, max(0, verticalStart! + value.translation.height * distance / travel))) }.onEnded { _ in verticalStart = nil })
                        .accessibilityLabel("Scroll vertically").accessibilityAdjustableAction { direction in position.scrollTo(y: min(distance, max(0, metrics.y + (direction == .increment ? 1 : -1) * metrics.height * 0.8))) }
                }
            }
            .overlay(alignment: .bottomLeading) {
                if axes.contains(.horizontal), metrics.contentWidth > metrics.width + 1, metrics.width > 0 {
                    let length = max(24, metrics.width * metrics.width / metrics.contentWidth - 8)
                    let travel = max(1, metrics.width - length - 8)
                    let distance = metrics.contentWidth - metrics.width
                    Capsule().fill(Color.primary.opacity(hoverHorizontal || horizontalStart != nil ? 0.35 : 0.20)).frame(width: length, height: 3).frame(height: 14).contentShape(Rectangle())
                        .offset(x: 4 + min(1, max(0, metrics.x / distance)) * travel)
                        .onHover { hoverHorizontal = $0 }
                        .gesture(DragGesture(minimumDistance: 0).onChanged { value in if horizontalStart == nil { horizontalStart = metrics.x }; position.scrollTo(x: min(distance, max(0, horizontalStart! + value.translation.width * distance / travel))) }.onEnded { _ in horizontalStart = nil })
                        .accessibilityLabel("Scroll horizontally").accessibilityAdjustableAction { direction in position.scrollTo(x: min(distance, max(0, metrics.x + (direction == .increment ? 1 : -1) * metrics.width * 0.8))) }
                }
            }
    }
}
