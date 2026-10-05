import SwiftUI
import AppKit

// A real multiline text view: Return is a newline, not TextField submission.
struct TableCellEditor: NSViewRepresentable {
    @Binding var text: String
    var header: Bool
    var ink: NSColor = .labelColor
    func makeCoordinator() -> Coordinator { Coordinator(self) }
    func makeNSView(context: Context) -> NSScrollView {
        let scroll = TableCellScrollView(); scroll.drawsBackground = false; scroll.hasVerticalScroller = false; scroll.hasHorizontalScroller = false
        let view = NSTextView(); view.isRichText = false; view.drawsBackground = false; view.isVerticallyResizable = true; view.isHorizontallyResizable = false; view.autoresizingMask = [.width]; view.textContainerInset = .zero; view.textContainer?.lineFragmentPadding = 0; view.textContainer?.widthTracksTextView = true; view.delegate = context.coordinator; view.string = text; view.font = NSFont.systemFont(ofSize: 15, weight: header ? .medium : .regular); view.textColor = ink; view.setAccessibilityLabel("Table cell"); scroll.documentView = view; return scroll
    }
    func updateNSView(_ scroll: NSScrollView, context: Context) {
        context.coordinator.parent = self
        guard let view = scroll.documentView as? NSTextView else { return }
        if view.string != text { let selection = view.selectedRange(); view.string = text; view.setSelectedRange(NSRange(location: min(selection.location, text.utf16.count), length: 0)) }
        view.font = NSFont.systemFont(ofSize: 15, weight: header ? .medium : .regular); view.textColor = ink
    }
    class Coordinator: NSObject, NSTextViewDelegate {
        var parent: TableCellEditor
        init(_ parent: TableCellEditor) { self.parent = parent }
        func textDidChange(_ notification: Notification) { if let view = notification.object as? NSTextView { parent.text = view.string } }
    }
}

final class TableCellScrollView: NSScrollView {
    override func layout() {
        super.layout()
        guard let text = documentView as? NSTextView else { return }
        text.minSize = NSSize(width: contentSize.width, height: contentSize.height)
        text.setFrameSize(NSSize(width: contentSize.width, height: max(contentSize.height, text.frame.height)))
    }
}
