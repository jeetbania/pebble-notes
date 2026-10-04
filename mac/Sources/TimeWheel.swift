import SwiftUI
import AppKit

struct RollingTimePicker: View {
    @Binding var date: Date
    var calendar: Calendar { .current }
    func component(_ kind: Calendar.Component) -> Int { calendar.component(kind, from: date) }
    func set(_ hour: Int, _ minute: Int) { date = calendar.date(bySettingHour: hour, minute: minute, second: 0, of: date) ?? date }
    var hour: Binding<Int?> { Binding(get: { (component(.hour) + 11) % 12 + 1 }, set: { if let value = $0 { set(value % 12 + (component(.hour) >= 12 ? 12 : 0), component(.minute)) } }) }
    var minute: Binding<Int?> { Binding(get: { component(.minute) }, set: { if let value = $0 { set(component(.hour), value) } }) }
    var period: Binding<Int?> { Binding(get: { component(.hour) / 12 }, set: { if let value = $0 { set(component(.hour) % 12 + value * 12, component(.minute)) } }) }
    var body: some View {
        HStack(spacing: 4) {
            TimeWheelColumn(values: Array(1...12), selection: hour, label: "Hour") { String($0) }
            Text(":").font(.title3).accessibilityHidden(true)
            TimeWheelColumn(values: Array(0...59), selection: minute, label: "Minute") { String(format: "%02d", $0) }
            TimeWheelColumn(values: [0, 1], selection: period, label: "Period") { $0 == 0 ? "AM" : "PM" }
        }.frame(width: 220, height: 140).background(Color.primary.opacity(0.035), in: RoundedRectangle(cornerRadius: 12))
    }
}
struct TimeWheelColumn: View {
    var values: [Int]; @Binding var selection: Int?; var label: String; var text: (Int) -> String
    var body: some View {
        NativeTimeWheel(values: values, selection: $selection, label: label, text: text)

        .mask(LinearGradient(stops: [.init(color: .clear, location: 0), .init(color: .white.opacity(0.3), location: 0.20), .init(color: .white, location: 0.40), .init(color: .white, location: 0.60), .init(color: .white.opacity(0.3), location: 0.80), .init(color: .clear, location: 1)], startPoint: .top, endPoint: .bottom))
        .overlay { RoundedRectangle(cornerRadius: 6).fill(Color.primary.opacity(0.06)).frame(height: 32).allowsHitTesting(false) }
        .accessibilityLabel(label).accessibilityValue(selection.map(text) ?? "")
    }
}
struct QuickTaskSchedule: View {
    var initial: TaskDetails; var save: (TaskDetails) -> Void; var cancel: () -> Void
    @LeafState private var date = Date()
    @LeafState private var dated = true
    @LeafState private var hasTime = false
    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            HStack { Text("Schedule task").font(.title2.bold()); Spacer(); Button("Cancel", action: cancel) }
            TaskCalendar(date: $date, dated: $dated)
            if dated { Toggle("Include time", isOn: $hasTime); if hasTime { RollingTimePicker(date: $date) } }
            HStack { Spacer(); Button("Save date") { var task = initial; task.dueAt = dated ? Int64((hasTime ? date : Calendar.current.startOfDay(for: date)).timeIntervalSince1970 * 1000) : 0; task.hasTime = dated && hasTime; if !dated { task.remind = false; task.repeatRule = "none" }; save(task) }.keyboardShortcut(.defaultAction) }
        }.padding(24).frame(width: 340).buttonStyle(MaterialActionStyle()).onAppear { date = initial.dueAt > 0 ? initial.date : Date(); dated = initial.dueAt > 0; hasTime = initial.hasTime }
    }
}

struct NativeTimeWheel: NSViewRepresentable {
    var values: [Int]; @Binding var selection: Int?; var label: String; var text: (Int) -> String
    func makeNSView(context: Context) -> TimeWheelView { TimeWheelView() }
    func updateNSView(_ view: TimeWheelView, context: Context) {
        view.values = values; view.index = values.firstIndex(of: selection ?? values[0]) ?? 0; view.label = label; view.title = text
        view.changed = { selection = $0 }; view.needsDisplay = true
        view.setAccessibilityElement(true); view.setAccessibilityRole(.slider); view.setAccessibilityLabel(label); view.setAccessibilityValue(text(values[view.index]))
    }
}
final class TimeWheelView: NSView {
    var values: [Int] = []; var index = 0; var label = ""; var title: (Int) -> String = { String($0) }; var changed: ((Int) -> Void)?
    private var dragStart: CGFloat = 0; private var startIndex = 0; private var scrollDelta: CGFloat = 0
    override var acceptsFirstResponder: Bool { true }
    override func draw(_ rect: NSRect) {
        guard !values.isEmpty else { return }
        for step in -3...3 {
            let item = index + step; guard values.indices.contains(item) else { continue }
            let font = NSFont.systemFont(ofSize: 18, weight: step == 0 ? .semibold : .regular)
            let string = NSAttributedString(string: title(values[item]), attributes: [.font: font, .foregroundColor: NSColor.labelColor.withAlphaComponent(step == 0 ? 1 : 0.55)])
            let size = string.size(); string.draw(at: NSPoint(x: (bounds.width - size.width) / 2, y: (bounds.height - size.height) / 2 - CGFloat(step) * 32))
        }
    }
    func select(_ value: Int) { let next = min(max(0, value), values.count - 1); guard next != index else { return }; index = next; changed?(values[next]); needsDisplay = true }
    override func mouseDown(with event: NSEvent) { window?.makeFirstResponder(self); dragStart = convert(event.locationInWindow, from: nil).y; startIndex = index }
    override func mouseDragged(with event: NSEvent) { select(startIndex + Int((convert(event.locationInWindow, from: nil).y - dragStart) / 32)) }
    override func mouseUp(with event: NSEvent) { let y = convert(event.locationInWindow, from: nil).y; if abs(y - dragStart) < 5 { select(index + Int(((bounds.height / 2 - y) / 32).rounded())) } }
    override func scrollWheel(with event: NSEvent) { scrollDelta += event.scrollingDeltaY; if abs(scrollDelta) >= 8 { let steps = Int(scrollDelta / 8); scrollDelta -= CGFloat(steps) * 8; select(index - steps) } }
    override func keyDown(with event: NSEvent) { if event.keyCode == 125 { select(index + 1) } else if event.keyCode == 126 { select(index - 1) } else { super.keyDown(with: event) } }
}
