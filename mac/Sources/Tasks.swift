import SwiftUI
import AppKit

struct TasksHome: View {
    var query = ""
    var dismissSearch: () -> Void = {}
    var clearSearch: () -> Void = {}
    @EnvironmentObject var store: NoteStore
    @LeafState<String> private var filter = "All"
    @AppStorage("taskView") private var layout = "List"
    @LeafState<String> private var stage = "all"
    @LeafState<String?> private var draggedTask = nil
    @LeafState<CGSize> private var taskOffset = .zero
    @LeafState<[String: CGRect]> private var columnFrames = [:]
    @LeafState<String> private var list = "All lists"
    @LeafState<Bool> private var adding = false
    @LeafState<String> private var addingStage = "todo"
    var newTask: Note { var n = Note(); n.task = TaskDetails(completed: addingStage == "done", status: addingStage); return n }
    @LeafState<Revision?> private var scheduling = nil
    @LeafState<Revision?> private var editing = nil
    var tasks: [Revision] { store.uniqueHeads.filter { !$0.note.deleted && !$0.note.archived && $0.note.task != nil } }
    var lists: [String] { ["All lists"] + Array(Set(tasks.compactMap { $0.note.task?.list })).sorted() }
    var visible: [Revision] {
        tasks.filter { r in
            guard let t = r.note.task else { return false }
            if !query.isEmpty && !(r.note.title + " " + r.note.text + " " + r.note.tags.joined(separator: " ")).localizedCaseInsensitiveContains(query) { return false }
            if list != "All lists" && t.list != list { return false }
            if stage != "all" && t.stage != stage { return false }
            if filter == "Completed" { return t.completed }
            if filter == "Today" { return t.dueAt > 0 && t.date < Calendar.current.date(byAdding: .day, value: 1, to: Calendar.current.startOfDay(for: Date()))! }
            if filter == "Upcoming" { return t.dueAt > 0 && t.date >= Calendar.current.date(byAdding: .day, value: 1, to: Calendar.current.startOfDay(for: Date()))! }
            return true
        }.sorted { a, b in let x = a.note.task!, y = b.note.task!; if x.dueAt != y.dueAt { return (x.dueAt == 0 ? Int64.max : x.dueAt) < (y.dueAt == 0 ? Int64.max : y.dueAt) }; return x.priority > y.priority }
    }
    func group(_ t: TaskDetails) -> String {
        if filter != "Today" { return t.dueAt == 0 ? "Anytime" : t.date.formatted(date: .abbreviated, time: .omitted) }
        if t.date < Calendar.current.startOfDay(for: Date()) { return "Overdue" }
        if !t.hasTime { return "Today" }
        let h = Calendar.current.component(.hour, from: t.date); return h < 12 ? "Morning" : h < 18 ? "Afternoon" : "Tonight"
    }
    var groups: [String] { var names: [String] = []; for r in visible { let label = group(r.note.task!); if !names.contains(label) { names.append(label) } }; return names }
    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            HStack(alignment: .center, spacing: 12) {
                VStack(alignment: .leading, spacing: 4) { Text("My Tasks").font(.system(size: 28, weight: .bold)); Text(Date().formatted(date: .abbreviated, time: .omitted)).font(.system(size: 12)).foregroundStyle(.secondary) }.layoutPriority(1)
                Spacer(minLength: 0)
                TaskPills(values: [("List", "list.bullet"), ("Board", "rectangle.split.3x1")], selected: $layout, iconsOnly: true)
                GlassIcon(icon: "plus", label: "New task") { addingStage = "todo"; adding = true }.leafGlass(in: Circle())
            }
            ViewThatFits(in: .horizontal) {
                HStack(spacing: 10) { stagePills; Spacer(minLength: 20); dateAndList }
                VStack(alignment: .leading, spacing: 10) { stagePills; HStack { Spacer(); dateAndList } }
            }
            if visible.isEmpty {
                GhostEmpty(kind: "tasks", title: tasks.isEmpty ? "A little room for what’s next" : "No tasks in this view", detail: tasks.isEmpty ? "Give your next step a place." : "Try another list or status.", actionLabel: tasks.isEmpty ? "Create a task" : "Show all tasks", action: { if tasks.isEmpty { addingStage = "todo"; adding = true } else { filter = "All"; list = "All lists"; stage = "all"; clearSearch() } })
            } else if layout == "Board" {
                LeafScrollView(.horizontal) {
                    HStack(alignment: .top, spacing: 14) {
                        ForEach(["todo", "progress", "review", "done"], id: \.self) { state in
                            VStack(alignment: .leading, spacing: 14) {
                                HStack { Circle().fill(stageColour(state)).frame(width: 6, height: 6); Text(stageName(state)).font(.system(size: 13, weight: .semibold)); Spacer(); Text("\(visible.filter { $0.note.task!.stage == state }.count)").foregroundStyle(.tertiary) }
                                LeafScrollView { VStack(spacing: 10) { ForEach(visible.filter { $0.note.task!.stage == state }) { r in kanbanCard(r).scaleEffect(draggedTask == r.noteId ? 0.96 : 1).offset(draggedTask == r.noteId ? taskOffset : .zero).zIndex(draggedTask == r.noteId ? 10 : 0).highPriorityGesture(DragGesture(minimumDistance: 8, coordinateSpace: .named("taskBoard")).onChanged { value in draggedTask = r.noteId; taskOffset = value.translation }.onEnded { value in if let state = columnFrames.first(where: { $0.value.contains(value.location) })?.key { withAnimation(.spring(response: 0.32, dampingFraction: 0.88)) { store.mutate(r.noteId) { $0.task?.move(to: state) } } }; draggedTask = nil; taskOffset = .zero }) }; Button { addingStage = state; adding = true } label: { Label("Add task", systemImage: "plus").frame(maxWidth: .infinity).padding(10) }.buttonStyle(SoftButtonStyle()) } }.scrollClipDisabled()
                            }.padding(14).frame(width: 246).frame(maxHeight: .infinity).background(Color.primary.opacity(0.025), in: RoundedRectangle(cornerRadius: 18))
                                .zIndex(tasks.first(where: { $0.noteId == draggedTask })?.note.task?.stage == state ? 10 : 0)
                                .background(GeometryReader { proxy in Color.clear.preference(key: TaskColumnFrames.self, value: [state: proxy.frame(in: .named("taskBoard"))]) })
                                .onDrop(of: [.text], delegate: TaskColumnDrop(dragging: $draggedTask) { id in guard tasks.contains(where: { $0.noteId == id }) else { return }; withAnimation(.spring(response: 0.32, dampingFraction: 0.88)) { store.mutate(id) { $0.task?.move(to: state) } } })
                        }
                    }
                }
            } else {
                LeafScrollView { VStack(alignment: .leading, spacing: 12) {
                    if visible.isEmpty { Text("A little room for what’s next.").foregroundStyle(.secondary).padding(.vertical, 36) }
                    ForEach(groups, id: \.self) { name in Text(name).font(.system(size: 12, weight: .medium)).foregroundStyle(.secondary).padding(.top, 12); ForEach(visible.filter { group($0.note.task!) == name }) { r in taskRow(r) } }
                    Button { addingStage = "todo"; adding = true } label: { Label("Add a new task…", systemImage: "plus").frame(maxWidth: .infinity, alignment: .leading).padding(18) }.buttonStyle(SoftButtonStyle())
                }.padding(.bottom, 30) }
            }
        }.frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading).padding(.horizontal, 24).padding(.top, 78).padding(.bottom, 20).coordinateSpace(name: "taskBoard").onPreferenceChange(TaskColumnFrames.self) { columnFrames = $0 }.background(Color.clear.contentShape(Rectangle()).onTapGesture { dismissSearch() })
        .panePresented(isPresented: $adding) { TaskComposer(initial: newTask, save: { n in store.create(); store.update { $0 = n }; store.flush(); store.select(nil); adding = false }, cancel: { adding = false }) }
        .panePresented(item: $scheduling) { r in QuickTaskSchedule(initial: r.note.task!, save: { value in store.mutate(r.noteId) { $0.task = value }; scheduling = nil }, cancel: { scheduling = nil }) }
        .panePresented(item: $editing) { r in TaskComposer(initial: r.note, save: { n in store.mutate(r.noteId) { $0 = n }; editing = nil }, cancel: { editing = nil }) }
    }
    var stagePills: some View { ViewThatFits(in: .horizontal) { stageButtons(iconsOnly: false); stageButtons(iconsOnly: true) } }
    func stageButtons(iconsOnly: Bool) -> some View { TaskPills(values: [("all", "square.stack"), ("todo", "circle"), ("progress", "circle.lefthalf.filled"), ("review", "eye"), ("done", "checkmark.circle")], selected: $stage, iconsOnly: iconsOnly, labels: ["All", "To Do", "Progress", "Review", "Done"]) }
    var dateAndList: some View { HStack(spacing: 8) { Menu { ForEach(["All", "Today", "Upcoming"], id: \.self) { value in Button(value) { filter = value } } } label: { Label(filter, systemImage: "calendar").font(.system(size: 12)).padding(.horizontal, 12).frame(height: 36) }.menuStyle(.button).buttonStyle(SoftButtonStyle(radius: 18)).menuIndicator(.hidden).background(Color.primary.opacity(0.035), in: Capsule()); Menu { ForEach(lists, id: \.self) { value in Button(value) { list = value } } } label: { Label(list, systemImage: "tray").font(.system(size: 12)).lineLimit(1).padding(.horizontal, 12).frame(height: 36) }.menuStyle(.button).buttonStyle(SoftButtonStyle(radius: 18)).menuIndicator(.hidden).background(Color.primary.opacity(0.035), in: Capsule()) } }
    func stageName(_ value: String) -> String { ["todo": "To Do", "progress": "In Progress", "review": "In Review", "done": "Done"][value] ?? value }
    func taskRow(_ r: Revision) -> some View {
        let t = r.note.task!
        return HStack(alignment: .top, spacing: 10) {
            Button { store.mutate(r.noteId) { $0.task?.complete() } } label: { Image(systemName: t.completed ? "checkmark.circle.fill" : "circle").font(.system(size: 23, weight: .light)).foregroundStyle(t.completed ? LeafPalette.accent : Color.secondary) }.buttonStyle(SoftButtonStyle()).help(t.repeatRule == "none" ? "Complete task" : "Complete and schedule next occurrence")
            Button { store.select(r.noteId) } label: {
                VStack(alignment: .leading, spacing: 5) {
                    Text(r.note.displayTitle).font(.system(size: 16, weight: .medium)).fixedSize(horizontal: false, vertical: true).strikethrough(t.completed).opacity(t.completed ? 0.5 : 1)
                    HStack(spacing: 8) { Text(t.list); if t.dueAt > 0 { Text(t.date.formatted(date: .abbreviated, time: t.hasTime ? .shortened : .omitted)).foregroundStyle(t.date < Date() && !t.completed ? .red : .secondary) }; if t.priority > 0 { Image(systemName: "flag.fill").foregroundStyle(t.priority == 3 ? Color.red : t.priority == 2 ? Color.orange : Color.blue) }; if t.repeatRule != "none" { Label(t.repeatRule.capitalized, systemImage: "repeat") }; if !r.note.attachments.isEmpty { Image(systemName: "paperclip") }; let checks = r.note.document.filter { $0.kind == "check" }; if !checks.isEmpty { Text("\(checks.filter(\.checked).count)/\(checks.count)") } }.font(.system(size: 11)).foregroundStyle(.secondary).lineLimit(1)
                }.frame(maxWidth: .infinity, alignment: .leading)
            }.buttonStyle(.plain)
            Menu { quickTaskActions(r); Menu("Status") { ForEach(["todo", "progress", "review", "done"], id: \.self) { state in Button(stageName(state)) { store.mutate(r.noteId) { $0.task?.move(to: state) } } } }; Button("Edit task…") { editing = r }; Button("Open details & subtasks") { store.select(r.noteId) }; Button("Duplicate") { var n = r.note; n.task?.completed = false; n.task?.status = "todo"; store.create(); store.update { $0 = n }; store.flush(); store.select(nil) }; Button("Convert to note") { store.mutate(r.noteId) { $0.task = nil } }; Divider(); Button("Move to Trash") { store.mutate(r.noteId) { $0.deleted = true } } } label: { Image(systemName: "ellipsis").frame(width: 36, height: 36) }.menuStyle(.button).buttonStyle(SoftButtonStyle(radius: 18)).frame(width: 36, height: 36)
        }.padding(16).frame(maxWidth: .infinity, alignment: .leading).contentShape(RoundedRectangle(cornerRadius: 16)).modifier(TaskHover()).background(Color.primary.opacity(0.035), in: RoundedRectangle(cornerRadius: 16)).overlay(RoundedRectangle(cornerRadius: 16).stroke(Color.primary.opacity(0.055), lineWidth: 0.5)).onTapGesture { store.select(r.noteId) }.contextMenu { quickTaskActions(r); Button("Edit task…") { editing = r } }
    }
    func stageColour(_ stage: String) -> Color { switch stage { case "progress": .orange; case "review": .purple; case "done": .green; default: .blue } }
    func chip(_ text: String, _ icon: String, _ colour: Color) -> some View { Label(text, systemImage: icon).font(.system(size: 11, weight: .medium)).lineLimit(1).padding(.horizontal, 7).padding(.vertical, 5).foregroundStyle(colour).background(colour.opacity(0.10), in: Capsule()) }
    func kanbanCard(_ r: Revision) -> some View {
        let t = r.note.task!
        let description = r.note.document.filter { $0.isText && $0.kind != "check" }.map(\.text).joined(separator: " ").trimmingCharacters(in: .whitespacesAndNewlines)
        return VStack(alignment: .leading, spacing: 10) {
            Button { store.select(r.noteId) } label: {
                VStack(alignment: .leading, spacing: 5) { Text(r.note.displayTitle).font(.system(size: 15, weight: .semibold)).lineLimit(3).fixedSize(horizontal: false, vertical: true); if !description.isEmpty { Text(description).font(.system(size: 12)).foregroundStyle(.secondary).lineLimit(2) } }.frame(maxWidth: .infinity, alignment: .leading)
            }.buttonStyle(.plain)
            if !r.note.tags.isEmpty { HStack(spacing: 5) { ForEach(Array(r.note.tags.prefix(2)), id: \.self) { TagPill(text: $0) } }.clipped() }
            ViewThatFits(in: .horizontal) {
                HStack(spacing: 5) { boardMetadata(t) }
                VStack(alignment: .leading, spacing: 5) { boardMetadata(t) }
            }
            HStack(spacing: 5) { Label(t.list, systemImage: "tray").font(.system(size: 11)).foregroundStyle(.secondary).lineLimit(1); Spacer(minLength: 4); if !r.note.attachments.isEmpty { Label("\(r.note.attachments.count)", systemImage: "paperclip").font(.system(size: 11)).foregroundStyle(.secondary) }; taskMenu(r) }
        }.padding(14).frame(maxWidth: .infinity, alignment: .leading).contentShape(RoundedRectangle(cornerRadius: 18)).modifier(TaskHover()).background(.regularMaterial, in: RoundedRectangle(cornerRadius: 18)).background(LinearGradient(colors: [Color.primary.opacity(0.045), Color.primary.opacity(0.02)], startPoint: .topLeading, endPoint: .bottomTrailing), in: RoundedRectangle(cornerRadius: 18)).overlay(RoundedRectangle(cornerRadius: 18).stroke(draggedTask == r.noteId ? stageColour(t.stage).opacity(0.3) : Color.primary.opacity(0.07), lineWidth: draggedTask == r.noteId ? 1 : 0.5)).shadow(color: .black.opacity(draggedTask == r.noteId ? 0.15 : 0.035), radius: draggedTask == r.noteId ? 16 : 4, y: 3).onTapGesture { store.select(r.noteId) }.contextMenu { quickTaskActions(r); Button("Edit task…") { editing = r } }
    }
    @ViewBuilder func boardMetadata(_ t: TaskDetails) -> some View {
        if t.priority > 0 { chip(["", "Low", "Medium", "High"][min(3, max(0, t.priority))], "flag.fill", t.priority == 3 ? .red : t.priority == 2 ? .orange : .blue) }
        if t.dueAt > 0 { chip(t.date.formatted(date: .abbreviated, time: t.hasTime ? .shortened : .omitted), "calendar", t.date < Calendar.current.startOfDay(for: Date()) && !t.completed ? .red : .secondary) }
        if t.repeatRule != "none" { chip(t.repeatRule.capitalized, "repeat", .secondary) }
    }
    func taskMenu(_ r: Revision) -> some View {
        Menu { quickTaskActions(r); Menu("Status") { ForEach(["todo", "progress", "review", "done"], id: \.self) { state in Button(stageName(state)) { store.mutate(r.noteId) { $0.task?.move(to: state) } } } }; Button("Edit task…") { editing = r }; Button("Open details & subtasks") { store.select(r.noteId) }; Button("Duplicate") { var n = r.note; n.task?.completed = false; n.task?.status = "todo"; store.create(); store.update { $0 = n }; store.flush(); store.select(nil) }; Button("Convert to note") { store.mutate(r.noteId) { $0.task = nil } }; Divider(); Button("Move to Trash") { store.mutate(r.noteId) { $0.deleted = true } } } label: { Image(systemName: "ellipsis").frame(width: 30, height: 30) }.menuStyle(.button).buttonStyle(SoftButtonStyle(radius: 18)).menuIndicator(.hidden).frame(width: 30, height: 30)
    }
    @ViewBuilder func quickTaskActions(_ revision: Revision) -> some View {
        Menu("Priority") { ForEach(0...3, id: \.self) { priority in Button(["None", "Low", "Medium", "High"][priority]) { store.mutate(revision.noteId) { $0.task?.priority = priority } } } }
        Button("Today") { shiftDate(revision.noteId, days: 0) }
        Button("Tomorrow") { shiftDate(revision.noteId, days: 1) }
        Button("Custom date & time…") { scheduling = revision }
        Button("No date") { store.mutate(revision.noteId) { $0.task?.dueAt = 0; $0.task?.hasTime = false; $0.task?.remind = false; $0.task?.repeatRule = "none" } }
        SubtleDivider()
    }
    func shiftDate(_ id: String, days: Int) {
        store.mutate(id) { note in guard var task = note.task else { return }; var date = Calendar.current.date(byAdding: .day, value: days, to: Calendar.current.startOfDay(for: Date()))!; if task.hasTime && task.dueAt > 0 { let c = Calendar.current.dateComponents([.hour, .minute], from: task.date); date = Calendar.current.date(bySettingHour: c.hour ?? 9, minute: c.minute ?? 0, second: 0, of: date)! }; task.dueAt = Int64(date.timeIntervalSince1970 * 1000); note.task = task }
    }

}
struct TaskComposer: View {
    @EnvironmentObject var store: NoteStore
    var initial: Note; var save: (Note) -> Void; var cancel: () -> Void
    @LeafState<Note> private var note: Note
    @LeafState<Bool> private var dated: Bool
    @LeafState<Date> private var date: Date
    @LeafState<String> private var newSubtask = ""
    @LeafState<String> private var alertStatus = ""
    @LeafState<Bool> private var calendarVisible = false
    init(initial: Note, save: @escaping (Note) -> Void, cancel: @escaping () -> Void) {
        self.initial = initial; self.save = save; self.cancel = cancel
        var n = initial; if n.task == nil { n.task = TaskDetails() }; n.prepare()
        _note = LeafState(initialValue: n); _dated = LeafState(initialValue: n.task!.dueAt > 0); _date = LeafState(initialValue: n.task!.dueAt > 0 ? n.task!.date : Date())
    }
    var body: some View {
        VStack(alignment: .leading, spacing: 22) {
            HStack { Text(initial.title.isEmpty ? "New task" : "Task details").font(.title2.bold()); Spacer(); Button("Cancel", action: cancel).keyboardShortcut(.cancelAction); Button("Save") { commit() }.keyboardShortcut(.defaultAction).disabled(note.title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty) }
            TextField("What would you like to do?", text: $note.title, axis: .vertical).font(.system(size: 22, weight: .semibold)).textFieldStyle(.plain).padding(.vertical, 6)
            VStack(alignment: .leading, spacing: 12) {
                property("Status", "circle.lefthalf.filled") { TaskPills(values: [("todo", "circle"), ("progress", "circle.lefthalf.filled"), ("review", "eye"), ("done", "checkmark.circle")], selected: Binding(get: { note.task!.stage }, set: { note.task!.move(to: $0) }), labels: ["To Do", "Progress", "Review", "Done"]) }
                property("List", "tray") { TextField("Personal", text: Binding(get: { note.task!.list }, set: { note.task!.list = String($0.prefix(128)) })).textFieldStyle(.plain).padding(8).background(Color.primary.opacity(0.035), in: RoundedRectangle(cornerRadius: 8)) }
                property("Tags", "tag") { VStack(alignment: .leading, spacing: 8) { HStack { ForEach(note.tags.map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }, id: \.self) { TagPill(text: $0) } }; TextField("Add tags, separated by commas", text: Binding(get: { note.tags.joined(separator: ", ") }, set: { note.tags = $0.split(separator: ",", omittingEmptySubsequences: false).map(String.init) })).modifier(PebbleField()) } }
            }
            SubtleDivider()
            VStack(alignment: .leading, spacing: 12) {
                property("Date", "calendar") {
                    HStack { Button { calendarVisible.toggle() } label: { Label(dated ? date.formatted(date: .abbreviated, time: .omitted) : "Set date", systemImage: "calendar").padding(.horizontal, 10).frame(height: 36) }.buttonStyle(SoftButtonStyle(radius: 18)).leafGlass(in: Capsule()).popover(isPresented: $calendarVisible) { TaskCalendar(date: $date, dated: $dated).padding(16).frame(width: 300).presentationBackground(.ultraThinMaterial) }; if dated { Toggle("Time", isOn: Binding(get: { note.task!.hasTime }, set: { note.task!.hasTime = $0 })).toggleStyle(.switch); if note.task!.hasTime { RollingTimePicker(date: $date) } } }
                }
                property("Priority", "flag") { HStack(spacing: 8) { ForEach(0...3, id: \.self) { priority in Button { note.task!.priority = priority } label: { Image(systemName: priority == 0 ? "flag" : "flag.fill").foregroundStyle(priority == 3 ? Color.red : priority == 2 ? .orange : priority == 1 ? .blue : .secondary).frame(width: 36, height: 36).background(Color.primary.opacity(note.task!.priority == priority ? 0.10 : 0), in: Circle()) }.buttonStyle(SoftButtonStyle(radius: 18)).help(["None", "Low", "Medium", "High"][priority]) }; Spacer(); Menu { ForEach(["none", "daily", "weekly", "monthly"], id: \.self) { value in Button(value.capitalized) { note.task!.repeatRule = value } } } label: { Label(note.task!.repeatRule == "none" ? "Repeat" : note.task!.repeatRule.capitalized, systemImage: "repeat").padding(8) }.menuStyle(.button).buttonStyle(SoftButtonStyle()).disabled(!dated) } }
                if dated { property("Reminder", "bell") { Toggle("Notify at the due time", isOn: Binding(get: { note.task!.remind }, set: { enabled in if enabled { Task { let granted = await TaskAlerts.authorize(); note.task!.remind = granted; alertStatus = granted ? "" : "Allow notifications for Pebble Notes in System Settings." } } else { note.task!.remind = false } })).toggleStyle(.switch) }; Text(note.task!.hasTime ? "Repeating tasks advance when completed." : "All-day reminders arrive at 9 AM. Repeats advance when completed.").font(.caption).foregroundStyle(.secondary); if !alertStatus.isEmpty { Text(alertStatus).font(.caption).foregroundStyle(.secondary) } }
            }
            SubtleDivider()
            VStack(alignment: .leading, spacing: 10) {
                Label("Description", systemImage: "text.alignleft").font(.headline)
                HStack(spacing: 4) { ForEach([("bold", "Bold"), ("italic", "Italic"), ("underline", "Underline")], id: \.0) { kind, label in GlassIcon(icon: kind, label: label) { EditorActions.shared.format(kind) } }; GlassIcon(icon: "link", label: "Add link") { EditorActions.shared.link() }; Menu("Style") { ForEach(["Body", "Headline", "Subtitle", "Title"], id: \.self) { style in Button(style) { EditorActions.shared.textStyle(style.lowercased()) } } }.menuStyle(.button).buttonStyle(SoftButtonStyle()); Spacer() }
                ForEach(note.document.filter { $0.isText && $0.kind != "check" }) { block in
                    RichEditor(note: block.asNote, onEdit: { text, spans in note.editBlock(block.id) { $0.text = text; $0.spans = spans } }, onImages: { attach($0) }).frame(height: max(64, block.asNote.attributed.boundingRect(with: NSSize(width: 470, height: CGFloat.greatestFiniteMagnitude), options: [.usesLineFragmentOrigin, .usesFontLeading]).height + 12)).padding(10).background(Color.primary.opacity(0.035), in: RoundedRectangle(cornerRadius: 10))
                }
                if note.document.filter({ $0.isText && $0.kind != "check" }).isEmpty { Button("Add description") { note.blocks = note.document + [DocumentBlock()] } }
            }
            SubtleDivider()
            VStack(alignment: .leading, spacing: 10) {
                Label("Subtasks", systemImage: "checklist").font(.headline)
                ForEach(note.document.filter { $0.kind == "check" }) { block in HStack(spacing: 10) { GlassIcon(icon: block.checked ? "checkmark.circle.fill" : "circle", label: block.checked ? "Mark incomplete" : "Complete subtask") { note.editBlock(block.id) { $0.checked.toggle() } }; TextField("Subtask", text: Binding(get: { block.text }, set: { value in note.editBlock(block.id) { $0.text = value } }), axis: .vertical).textFieldStyle(.plain).strikethrough(block.checked); GlassIcon(icon: "minus.circle", label: "Remove subtask") { note.blocks = note.document.filter { $0.id != block.id } } } }
                HStack { TextField("Add a subtask", text: $newSubtask).modifier(PebbleField()).onSubmit(addSubtask); Button("Add", action: addSubtask).disabled(newSubtask.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty) }
            }
            SubtleDivider()
            VStack(alignment: .leading, spacing: 10) { HStack { Label("Attachments", systemImage: "paperclip").font(.headline); Spacer(); Button("Add files…") { let panel = NSOpenPanel(); panel.allowsMultipleSelection = true; if panel.runModal() == .OK { attach(panel.urls) } } }; ForEach(note.attachments) { a in HStack { Image(systemName: a.mime.hasPrefix("image/") ? "photo" : "doc"); Text(a.name).lineLimit(1); Spacer(); GlassIcon(icon: "minus.circle", label: "Remove attachment") { note.blocks = note.document.filter { $0.mediaId != a.id }; note.prepare() } } } }
            HStack { Spacer(); Button("Cancel", action: cancel); Button("Save task", action: commit).disabled(note.title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty) }
        }.padding(28).frame(width: 580).buttonStyle(MaterialActionStyle()).background(Color.clear.contentShape(Rectangle()).onTapGesture { NSApp.keyWindow?.makeFirstResponder(nil) })
    }
    func property<Content: View>(_ title: String, _ icon: String, @ViewBuilder content: () -> Content) -> some View { HStack(alignment: .center, spacing: 12) { Label(title, systemImage: icon).font(.system(size: 12)).foregroundStyle(.secondary).frame(width: 92, alignment: .leading); content().frame(maxWidth: .infinity, alignment: .leading) } }
    func addSubtask() { for text in newSubtask.split(separator: "\n").map({ String($0).trimmingCharacters(in: .whitespaces) }).filter({ !$0.isEmpty }) { var b = DocumentBlock(); b.kind = "check"; b.text = text; note.blocks = note.document + [b] }; newSubtask = "" }
    func attach(_ urls: [URL]) { do { note.insertMedia(try store.stageFiles(urls), at: nil, offset: nil) } catch { store.error = error.localizedDescription } }
    func commit() {
        NSApp.keyWindow?.makeFirstResponder(nil); addSubtask()
        var n = note; n.title = n.title.trimmingCharacters(in: .whitespacesAndNewlines); n.task!.list = n.task!.list.trimmingCharacters(in: .whitespacesAndNewlines); if n.task!.list.isEmpty { n.task!.list = "Personal" }
        n.task!.dueAt = dated ? Int64((n.task!.hasTime ? date : Calendar.current.startOfDay(for: date)).timeIntervalSince1970 * 1000) : 0
        if !dated { n.task!.hasTime = false; n.task!.repeatRule = "none"; n.task!.remind = false }
        n.prepare(); save(n)
    }
}

struct TaskColumnDrop: DropDelegate {
    @Binding var dragging: String?; var move: (String) -> Void
    func dropUpdated(info: DropInfo) -> DropProposal? { DropProposal(operation: .move) }
    func performDrop(info: DropInfo) -> Bool { guard let id = dragging else { return false }; move(id); dragging = nil; return true }
}

struct TaskColumnFrames: PreferenceKey {
    static var defaultValue: [String: CGRect] = [:]
    static func reduce(value: inout [String: CGRect], nextValue: () -> [String: CGRect]) { value.merge(nextValue(), uniquingKeysWith: { _, new in new }) }
}

struct TaskHover: ViewModifier {
    @LeafState<Bool> private var hovering = false
    func body(content: Content) -> some View { content.background(Color.primary.opacity(hovering ? 0.05 : 0), in: RoundedRectangle(cornerRadius: 16)).onHover { hovering = $0 }.animation(.easeInOut(duration: 0.14), value: hovering) }
}
struct TaskPills: View {
    var values: [(String, String)]; @Binding var selected: String; var iconsOnly = false; var labels: [String] = []
    var body: some View { HStack(spacing: 3) { ForEach(values.indices, id: \.self) { i in Button { selected = values[i].0 } label: { HStack(spacing: 5) { Image(systemName: values[i].1); if !iconsOnly { Text(labels.indices.contains(i) ? labels[i] : values[i].0) } }.font(.system(size: 12, weight: .medium)).padding(.horizontal, iconsOnly ? 12 : 10).frame(height: 34).foregroundStyle(selected == values[i].0 ? Color.primary : .secondary).background(Color.primary.opacity(selected == values[i].0 ? 0.09 : 0), in: Capsule()) }.buttonStyle(SoftButtonStyle(radius: 18)).help(labels.indices.contains(i) ? labels[i] : values[i].0).accessibilityLabel(labels.indices.contains(i) ? labels[i] : values[i].0) } }.padding(3).background(Color.primary.opacity(0.035), in: Capsule()).fixedSize() }
}
struct TaskCalendar: View {
    @Binding var date: Date; @Binding var dated: Bool
    @LeafState<Date> private var month = Date()
    var calendar: Calendar { Calendar.current }
    var start: Date { calendar.date(from: calendar.dateComponents([.year, .month], from: month))! }
    var offset: Int { (calendar.component(.weekday, from: start) - calendar.firstWeekday + 7) % 7 }
    var count: Int { calendar.range(of: .day, in: .month, for: month)!.count }
    var body: some View { VStack(alignment: .leading, spacing: 16) {
        Button { date = calendar.date(byAdding: .day, value: 1, to: Date())!; dated = true; month = date } label: { Label("Tomorrow", systemImage: "sun.max").padding(.horizontal, 10).frame(height: 36).frame(maxWidth: .infinity, alignment: .leading) }.buttonStyle(SoftButtonStyle())
        Button { date = calendar.nextDate(after: Date(), matching: DateComponents(weekday: 7), matchingPolicy: .nextTime)!; dated = true; month = date } label: { Label("Next weekend", systemImage: "sun.horizon").padding(.horizontal, 10).frame(height: 36).frame(maxWidth: .infinity, alignment: .leading) }.buttonStyle(SoftButtonStyle())
        Button { dated = false } label: { Label("No date", systemImage: "calendar.badge.minus").padding(.horizontal, 10).frame(height: 36).frame(maxWidth: .infinity, alignment: .leading) }.buttonStyle(SoftButtonStyle())
        SubtleDivider(); HStack { Text(month.formatted(.dateTime.month(.wide).year())).font(.headline); Spacer(); GlassIcon(icon: "chevron.left", label: "Previous month") { month = calendar.date(byAdding: .month, value: -1, to: month)! }; GlassIcon(icon: "chevron.right", label: "Next month") { month = calendar.date(byAdding: .month, value: 1, to: month)! } }
        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 2), count: 7), spacing: 4) {
            ForEach(0..<7, id: \.self) { i in Text(calendar.veryShortWeekdaySymbols[(calendar.firstWeekday - 1 + i) % 7]).font(.caption).foregroundStyle(.secondary) }
            ForEach(0..<(offset + count), id: \.self) { index in if index < offset { Color.clear.frame(height: 32) } else { let day = index - offset + 1; let value = calendar.date(byAdding: .day, value: day - 1, to: start)!; Button { let time = calendar.dateComponents([.hour, .minute], from: date); date = calendar.date(bySettingHour: time.hour ?? 9, minute: time.minute ?? 0, second: 0, of: value)!; dated = true } label: { Text("\(day)").frame(maxWidth: .infinity).frame(height: 32).background(LeafPalette.accent.opacity(dated && calendar.isDate(value, inSameDayAs: date) ? 0.28 : 0), in: Circle()) }.buttonStyle(SoftButtonStyle(radius: 16)) } }
        }
    }.onAppear { month = date } }
}
