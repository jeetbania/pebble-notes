import SwiftUI
import AppKit
import ImageIO
import UniformTypeIdentifiers

#if !LEAF_TEST
@main
#endif
struct LeafNotesApp: App {
    @StateObject private var store = NoteStore()
    init() { TaskAlerts.configure(); if let url = Bundle.main.url(forResource: "Leaf", withExtension: "icns"), let image = NSImage(contentsOf: url) { NSApplication.shared.applicationIconImage = image } }
    @AppStorage("appearance") private var appearance = "system"
    var body: some Scene {
        WindowGroup("Pebble Notes") {
            LibraryView().environmentObject(store).frame(minWidth: 790, minHeight: 560)
                .preferredColorScheme(appearance == "light" ? .light : appearance == "dark" ? .dark : nil)
                .onReceive(NotificationCenter.default.publisher(for: NSApplication.willTerminateNotification)) { _ in store.flush() }
        }
        .windowStyle(.hiddenTitleBar).defaultSize(width: 1000, height: 740)
        .commands {
            CommandGroup(replacing: .appSettings) { Button("Settings…") { NotificationCenter.default.post(name: Notification.Name("leafSettings"), object: nil) }.keyboardShortcut(",") }
            CommandGroup(replacing: .newItem) { Button("New Note", systemImage: "square.and.pencil") { PebbleShortcuts.send("new") }.labelStyle(.titleAndIcon).keyboardShortcut("n") }
            CommandMenu("Navigate") {
                ForEach(PebbleShortcuts.entries.filter { $0.id != "new" }) { shortcut in
                    Button(shortcut.title) { PebbleShortcuts.send(shortcut.id) }.keyboardShortcut(KeyEquivalent(shortcut.key.first!), modifiers: shortcut.shift ? [.command, .shift] : [.command])
                }
            }
            CommandGroup(after: .saveItem) {
                Button("Export Backup…", systemImage: "archivebox") { store.exportBackup() }.labelStyle(.titleAndIcon).keyboardShortcut("e", modifiers: [.command, .shift])
                Button("Import Backup…", systemImage: "tray.and.arrow.down") { store.importBackup() }.labelStyle(.titleAndIcon)
            }
        }

    }
}
struct LibraryView: View {
    @AppStorage("showSidebarCounts") private var showSidebarCounts = true
    @EnvironmentObject var store: NoteStore
    @StateObject private var updater = PebbleUpdater.shared
    @StateObject private var clipboard = ClipboardCapture.shared
    @StateObject private var editorActions = EditorActions.shared
    @Environment(\.colorScheme) private var scheme
    @Environment(\.accessibilityReduceMotion) private var reducedMotion
    @Environment(\.accessibilityReduceTransparency) private var reducedTransparency
    @LeafState private var navigation = NavigationTrail()
    @LeafState<[String: CGRect]> private var collectionDropFrames = [:]
    @LeafState<String?> private var folderDropTarget = nil
    @LeafState<Bool> private var dropSettling = false
    @LeafState<String> private var section = "Everything"
    @LeafState<String> private var search = ""
    @LeafState<String> private var collection = ""
    @LeafState<Bool> private var sidebarVisible = true
    @LeafState<Bool> private var searchVisible = false
    @LeafState<Attachment?> private var preview: Attachment?
    @LeafState<Bool> private var showConflicts = false
    @LeafState<Bool> private var newCollection = false
    @LeafState<[String]> private var erasing = []
    @LeafState<String> private var collectionName = ""
    @LeafState<Bool> private var moveCollection = false
    @LeafState<String> private var moveTarget = ""
    @LeafState<Bool> private var formattingVisible = true
    @LeafState<Bool> private var selecting = false
    @LeafState<Set<String>> private var selectedItems = []
    @LeafState<String> private var sortOrder = UserDefaults.standard.string(forKey: "librarySort") ?? "edited"
    @LeafState<[String]> private var manualOrder = UserDefaults.standard.stringArray(forKey: "manualOrder") ?? []
    @LeafState<[String]> private var extraCollections = UserDefaults.standard.stringArray(forKey: "extraCollections") ?? []
    @LeafState<String> private var editingCollection = ""
    @LeafState<String> private var collectionEmoji = ""
    @LeafState<String> private var collectionImage = ""
    @LeafState<Int> private var iconMode = 0
    @LeafState<Int> private var iconVersion = 0
    @LeafState<Bool> private var dailyTools = false
    @LeafState<Bool> private var showSettings = false
    @LeafState<Bool> private var showOnboarding = !UserDefaults.standard.bool(forKey: "onboardingComplete")
    @LeafState private var settingsPage = "Appearance"
    @LeafState<Bool> private var showWhatsNew = false
    @LeafState<Bool> private var releaseHistory = false
    @LeafState<String?> private var dragging = nil
    @LeafState<[String: CGRect]> private var cardFrames = [:]
    @LeafState<CGRect> private var grabbedFrame = .zero
    @LeafState<CGSize> private var dragTranslation = .zero
    @LeafState<Revision?> private var grabbedRevision = nil
    @LeafState<Attachment?> private var grabbedImage = nil
    @AppStorage("calmMotion") private var calmMotion = false
    @FocusState private var searchFocused: Bool
    @FocusState private var emojiFocused: Bool
    @LeafState<[String]> private var folderOrder = UserDefaults.standard.stringArray(forKey: "folderOrder") ?? []
    @LeafState<String?> private var draggedFolder = nil
    @LeafState<[String: CGRect]> private var folderFrames = [:]
    @LeafState<CGRect> private var folderOrigin = .zero
    @LeafState<CGSize> private var folderTranslation = .zero
    @LeafState<String?> private var deletingFolder = nil
    @AppStorage("sidebarWidth") private var sidebarWidth = 240.0
    @LeafState<Double?> private var resizeStart = nil
    @LeafState<CGRect?> private var selectionRect = nil
    @LeafState<Set<String>> private var selectionBefore = []
    var allCollections: [String] { Array(Set(store.collections + extraCollections)).sorted { a, b in let x = folderOrder.firstIndex(of: a) ?? 99999, y = folderOrder.firstIndex(of: b) ?? 99999; return x == y ? a < b : x < y } }
    var title: String { section == "Collection" ? collection : section }
    var motion: Animation? { reducedMotion || calmMotion ? nil : .spring(response: 0.34, dampingFraction: 0.92) }
    var filtered: [Revision] {
        store.uniqueHeads.filter { r in
            let n = r.note
            if n.task != nil && !section.hasPrefix("#") && section != "Trash" && section != "Archive" { return false }
            if section == "Trash" { if !n.deleted { return false } } else if n.deleted { return false }
            if section == "Archive" { if !n.archived { return false } } else if section != "Trash" && n.archived { return false }
            if section.hasPrefix("#") && !n.tags.contains(String(section.dropFirst())) { return false }
            if section == "Pinned" && !n.pinned { return false }
            if section == "Checklists" && !n.document.contains(where: { $0.kind == "check" }) { return false }
            if section == "Images" && !n.attachments.contains(where: { $0.mime.hasPrefix("image/") }) { return false }
            if section == "Collection" && n.collection != collection && !n.collection.hasPrefix(collection + "/") { return false }
            return search.isEmpty || (n.title + " " + n.text + " " + n.collection + " " + n.tags.map { "#" + $0 }.joined(separator: " ")).localizedCaseInsensitiveContains(search)
        }.sorted { a, b in
            if sortOrder != "manual" && a.note.pinned != b.note.pinned { return a.note.pinned }
            if sortOrder == "manual" { return (manualOrder.firstIndex(of: a.noteId) ?? 99999) < (manualOrder.firstIndex(of: b.noteId) ?? 99999) }
            if sortOrder == "added" { return addedDate(a.noteId) > addedDate(b.noteId) }
            return a.createdAt > b.createdAt
        }
    }
    var body: some View {
        HStack(spacing: 0) {
            if sidebarVisible { sidebar.frame(width: sidebarWidth).overlay(alignment: .trailing) { Color.clear.frame(width: 6).contentShape(Rectangle()).onHover { if $0 { NSCursor.resizeLeftRight.push() } else { NSCursor.pop() } }.gesture(DragGesture(minimumDistance: 0, coordinateSpace: .global).onChanged { value in if resizeStart == nil { resizeStart = sidebarWidth }; sidebarWidth = min(360, max(180, resizeStart! + value.translation.width)) }.onEnded { _ in resizeStart = nil }).help("Resize sidebar") }.transition(.move(edge: .leading).combined(with: .opacity)) }
            ZStack(alignment: .top) {
                Group {
                    if section == "Tasks" {
                        ZStack {
                            TasksHome(query: search, dismissSearch: { searchFocused = false; NSApp.keyWindow?.makeFirstResponder(nil) }, clearSearch: { search = ""; searchFocused = false; NSApp.keyWindow?.makeFirstResponder(nil) }).environmentObject(store).opacity(store.current == nil ? 1 : 0).allowsHitTesting(store.current == nil).accessibilityHidden(store.current != nil)
                            if let note = store.current, note.task == nil, let id = store.selected { editor(note, id) }
                        }
                    } else if let note = store.current, note.task == nil, let id = store.selected { editor(note, id) }
                    else { gallery }
                }.frame(maxWidth: .infinity, maxHeight: .infinity).clipped().allowsHitTesting(preview == nil).accessibilityHidden(preview != nil)
                topBar.environment(\.colorScheme, store.current?.style?.chromeScheme ?? scheme).allowsHitTesting(preview == nil).accessibilityHidden(preview != nil).background {
                    if reducedTransparency { Color(nsColor: .windowBackgroundColor) }
                    else { ContentBlur().mask(LinearGradient(stops: [.init(color: .white, location: 0), .init(color: .white, location: 0.58), .init(color: .clear, location: 1)], startPoint: .top, endPoint: .bottom)) }
                }
            }.frame(maxWidth: .infinity, maxHeight: .infinity).modifier(NoteStyleHost(store: store))

            .panePresented(isPresented: $showConflicts) { conflicts }

            .panePresented(isPresented: Binding(get: { store.current?.task != nil }, set: { if !$0 { store.select(nil) } })) { if let n = store.current { TaskComposer(initial: n, save: { value in store.update { $0 = value }; store.flush(); store.select(nil) }, cancel: { store.select(nil) }) } }
            .panePresented(isPresented: $dailyTools) { DailyTools(store: store, onDone: { dailyTools = false }) }
            .panePresented(isPresented: Binding(get: { updater.visible && !showOnboarding && !showWhatsNew }, set: { updater.visible = $0 })) { PebbleUpdateDialog() }
            .panePresented(isPresented: Binding(get: { showWhatsNew && !showOnboarding }, set: { showWhatsNew = $0 })) { WhatsNew(history: releaseHistory) { ReleaseNotes.acknowledge(); showWhatsNew = false } }
            .overlay {
                if let attachment = preview { PhotoViewer(initial: attachment, items: photoItems, store: store, leadingInset: sidebarVisible ? 10 : 150, done: { navigate(-1) }, openNote: { id in preview = nil; store.select(id) }).background(.ultraThinMaterial).transition(.opacity) }
            }
            .background(scheme == .dark ? Color.black.opacity(0.13) : Color.white.opacity(0.26))
            .clipShape(RoundedRectangle(cornerRadius: 14))
            .padding(.vertical, 8).padding(.trailing, 8).padding(.leading, sidebarVisible ? 0 : 8)

        }
        .clipShape(RoundedRectangle(cornerRadius: 22))
        .overlay(alignment: .topLeading) {
            if let r = grabbedRevision, dragging != nil {
                Group { if let a = grabbedImage { ImageCard(attachment: a, collection: r.note.collection, media: store.media, style: r.note.style) } else { NoteCard(revision: r, conflict: false) } }
                    .frame(width: grabbedFrame.width).scaleEffect(dropSettling ? 0.12 : 0.96).opacity(dropSettling ? 0 : 1).shadow(color: .black.opacity(0.18), radius: 12, y: 8)
                    .position(x: grabbedFrame.midX + dragTranslation.width, y: grabbedFrame.midY + dragTranslation.height)
                    .allowsHitTesting(false).zIndex(20)
            }
        }
        .background {
            if reducedTransparency { Color(nsColor: .windowBackgroundColor) }
            else { WindowMaterial().overlay(scheme == .dark ? Color.black.opacity(0.28) : Color.white.opacity(0.28)) }
        }
        .clipShape(RoundedRectangle(cornerRadius: 22))
        .overlay { if showOnboarding { PebbleOnboarding(finish: { showOnboarding = false }, connect: { settingsPage = "Sync"; showSettings = true }).frame(width: 680).frame(maxWidth: .infinity, maxHeight: .infinity).background(WindowMaterial().overlay(scheme == .dark ? Color.black.opacity(0.38) : Color.white.opacity(0.65))) } }
        .overlay(alignment: .bottomTrailing) { if preview == nil && !showWhatsNew && !showOnboarding { ClipboardToast(capture: clipboard).transition(.opacity.combined(with: .move(edge: .bottom))) } }
        .animation(motion, value: clipboard.item?.id)
        .background(ClipboardWindowRegistration(store: store).frame(width: 0, height: 0))
        .background(WindowChrome()).background(EditorFocusDismissal().frame(width: 0, height: 0))
        .ignoresSafeArea(.container, edges: .top)
        .tint(LeafPalette.accent)
        .onChange(of: searchFocused) { _, focused in if !focused { withAnimation(motion) { searchVisible = false; search = "" } } }
        .onAppear { updater.start(); if ReleaseNotes.claimAutomatic() { showWhatsNew = true }; clipboard.start(store); if let url = Bundle.main.url(forResource: "Leaf", withExtension: "icns"), let image = NSImage(contentsOf: url) { NSApp.applicationIconImage = image } }
        .onReceive(NotificationCenter.default.publisher(for: Notification.Name("pebbleOnboarding"))) { _ in showSettings = false; showOnboarding = true }
        .onReceive(NotificationCenter.default.publisher(for: NSWindow.didBecomeKeyNotification)) { _ in store.reload() }
        .disabled(showSettings || deletingFolder != nil || newCollection || !erasing.isEmpty).accessibilityHidden(showSettings || deletingFolder != nil || newCollection || !erasing.isEmpty)
        .overlay { if newCollection { GlassDialog { VStack(alignment: .leading, spacing: 0) { Text(editingCollection.isEmpty ? "New collection" : "Edit collection").font(.title2.bold()).padding([.top, .horizontal], 28); collectionSheet }.buttonStyle(MaterialActionStyle()) } } }
        .overlay { if !erasing.isEmpty { GlassDialog { VStack(alignment: .leading, spacing: 18) { Text("Delete \(erasing.count) item\(erasing.count == 1 ? "" : "s") permanently?").font(.title2.bold()); Text("These items and their saved versions will be removed. This cannot be undone.").foregroundStyle(.secondary); HStack { Button("Cancel") { erasing = [] }; Spacer(); Button("Delete", role: .destructive) { store.permanentlyDelete(erasing); selectedItems.subtract(erasing); selecting = !selectedItems.isEmpty; erasing = [] } } }.padding(28).frame(width: 390).buttonStyle(MaterialActionStyle()) } } }
        .overlay { if showSettings { GlassDialog { SettingsHome(initialPage: settingsPage, onWhatsNew: { showSettings = false; releaseHistory = true; showWhatsNew = true }, onClose: { showSettings = false }).environmentObject(store).frame(width: 700, height: 560) } } }
        .overlay { if deletingFolder != nil { GlassDialog { VStack(alignment: .leading, spacing: 18) { Text("Delete Collection?").font(.title2.bold()); Text("Your notes will be kept in the library. Only this collection and its subcollections will be removed.").foregroundStyle(.secondary); HStack { Spacer(); Button("Cancel") { deletingFolder = nil }.keyboardShortcut(.cancelAction).buttonStyle(SoftButtonStyle()); Button("Delete", role: .destructive) { if let name = deletingFolder { store.deleteFolder(name); extraCollections.removeAll { $0 == name || $0.hasPrefix(name + "/") }; UserDefaults.standard.set(extraCollections, forKey: "extraCollections"); section = "Everything" }; deletingFolder = nil }.padding(.horizontal, 16).padding(.vertical, 8).background(Color.red.opacity(0.16), in: Capsule()).foregroundStyle(.red).buttonStyle(.plain) } }.padding(28).frame(width: 390) } } }

        .popover(isPresented: $moveCollection) { movePicker }
        .animation(motion, value: store.selected)
        .animation(motion, value: searchVisible)
        .onChange(of: section) { _, _ in searchFocused = false; newCollection = false; showSettings = false; dailyTools = false; showConflicts = false }
        .animation(motion, value: sidebarVisible)
        .alert("Leaf needs attention", isPresented: Binding(get: { store.error != nil }, set: { if !$0 { store.error = nil } })) { Button("OK") { store.error = nil } } message: { Text(store.error ?? "") }
        .onReceive(NotificationCenter.default.publisher(for: Notification.Name("leafSettings"))) { _ in if clipboard.activeStore === store { showSettings = true } }
        .onChange(of: route) { _, next in navigation.record(next) }
        .onReceive(NotificationCenter.default.publisher(for: Notification.Name("pebbleShortcut"))) { event in
            guard clipboard.activeStore === store, let action = event.object as? String else { return }; shortcut(action)
        }
        .coordinateSpace(name: "libraryDrag")
        .onPreferenceChange(CollectionDropFrames.self) { collectionDropFrames = $0 }
        .onDisappear { store.flush() }
        .task { MenuAppearance.install(); await store.syncIfConnected(); while !Task.isCancelled { try? await Task.sleep(for: .seconds(45)); if !Task.isCancelled { store.expireTrash(); await store.syncIfConnected() } } }
        .onReceive(NotificationCenter.default.publisher(for: NSApplication.didBecomeActiveNotification)) { _ in clipboard.inspect(); Task { await store.syncIfConnected() } }
    }
    var route: LibraryRoute { LibraryRoute(section: section, collection: collection, note: store.selected, image: preview?.id) }
    func navigate(_ direction: Int) {
        guard let target = navigation.step(direction) else { return }
        section = target.section; collection = target.collection; store.select(target.note); preview = store.uniqueHeads.flatMap { $0.note.attachments }.first { $0.id == target.image }
    }
    func shortcut(_ action: String) {
        if action == "sidebar" { sidebarVisible.toggle(); return }
        if showSettings || showOnboarding || newCollection || !erasing.isEmpty { return }
        switch action {
        case "new": preview = nil; createNote()
        case "search": preview = nil; store.select(nil); searchVisible = true; searchFocused = true
        case "find": if store.selected != nil { dailyTools = true } else { searchVisible = true; searchFocused = true }
        case "back": navigate(-1)
        case "forward": navigate(1)
        case "format": formattingVisible.toggle()
        case "collection": editingCollection = ""; collectionName = ""; collectionEmoji = ""; collectionImage = ""; iconMode = 0; newCollection = true
        case "everything", "notes", "tasks", "images": preview = nil; store.select(nil); section = ["everything":"Everything", "notes":"Notes", "tasks":"Tasks", "images":"Images"][action]!
        default: break
        }
    }
    var sidebar: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack { Spacer(); GlassIcon(icon: "sidebar.left", label: "Hide sidebar") { sidebarVisible = false }.leafGlass(in: Circle()) }.padding(.trailing, -8).frame(height: 52).padding(.bottom, 4)
            LeafScrollView { VStack(alignment: .leading, spacing: 4) {
            nav("Everything", "infinity")
            nav("Notes", "note.text")
            nav("Images", "photo")
            nav("Pinned", "pin")
            nav("Checklists", "checklist")
            nav("Tasks", "calendar.badge.clock")
            HStack {
                Text("Collections").font(.system(size: 12, weight: .medium)).foregroundStyle(.tertiary)
                Spacer(); Button { editingCollection = ""; collectionName = ""; collectionEmoji = ""; collectionImage = ""; iconMode = 0; newCollection = true } label: { Image(systemName: "plus").font(.system(size: 13)).frame(width: 24, height: 24) }.buttonStyle(SidebarButtonStyle()).help("New collection")
            }.padding(.horizontal, 8).padding(.top, 15).padding(.bottom, 7)
            ForEach(allCollections, id: \.self) { name in
                Button { preview = nil; store.select(nil); section = "Collection"; collection = name } label: {
                    HStack(spacing: 10) { collectionIcon(name).frame(width: 28, height: 28); Text(name.split(separator: "/").last.map(String.init) ?? name).font(.system(size: 13, weight: .medium)); Spacer(); if showSidebarCounts { sidebarCountLabel(store.sidebarCount(name, collection: true)) } }.padding(.horizontal, 7).frame(height: 34).modifier(SidebarSelectionSurface(selected: section == "Collection" && collection == name, motion: motion))
                }.overlay(RoundedRectangle(cornerRadius: 8).fill(LeafPalette.accent.opacity(folderDropTarget == name ? 0.14 : 0)).allowsHitTesting(false)).overlay(RoundedRectangle(cornerRadius: 8).stroke(LeafPalette.accent.opacity(folderDropTarget == name ? 0.5 : 0), lineWidth: 1).allowsHitTesting(false)).background(GeometryReader { proxy in Color.clear.preference(key: CollectionDropFrames.self, value: [name: proxy.frame(in: .named("libraryDrag"))]) }).opacity(draggedFolder == name ? 0 : 1).background(GeometryReader { proxy in Color.clear.preference(key: GalleryFrames.self, value: [name: proxy.frame(in: .named("folders"))]) }).highPriorityGesture(folderGrab(name)).padding(.leading, CGFloat(name.split(separator: "/").count - 1) * 12).buttonStyle(SidebarButtonStyle()).contextMenu { Button("Edit collection…", systemImage: "pencil") { editCollection(name) }.labelStyle(.titleAndIcon); if name != "Personal" { Button("Delete Collection", systemImage: "trash") { deletingFolder = name }.labelStyle(.titleAndIcon) } }
            }
            if !Set(store.uniqueHeads.flatMap { $0.note.tags }).isEmpty { Text("Tags").font(.system(size: 12, weight: .medium)).foregroundStyle(.tertiary).padding(.horizontal, 8).padding(.top, 16); ForEach(Array(Set(store.uniqueHeads.filter { !$0.note.deleted }.flatMap { $0.note.tags })).sorted(), id: \.self) { tag in nav("#" + tag, "number") } }
            } }.scrollIndicators(.never)
            Spacer(minLength: 8)
            nav("Archive", "archivebox")
            nav("Trash", "trash")
            HStack(spacing: 0) { Button { showSettings = true } label: { rowLabel("Settings", "gearshape").modifier(SidebarSelectionSurface(selected: showSettings, motion: motion)) }.buttonStyle(SidebarButtonStyle()); SyncIndicator(store: store).padding(.trailing, 8) }

        }.padding(.horizontal, 16).padding(.bottom, 12).coordinateSpace(name: "folders").onPreferenceChange(GalleryFrames.self) { folderFrames = $0 }.overlay(alignment: .topLeading) { if let name = draggedFolder { HStack(spacing: 10) { collectionIcon(name).frame(width: 28, height: 28).modifier(IconDepth()); Text(name.split(separator: "/").last.map(String.init) ?? name).font(.system(size: 13, weight: .medium)); Spacer() }.padding(.horizontal, 7).frame(width: folderOrigin.width, height: 34).background(Color.primary.opacity(0.10), in: RoundedRectangle(cornerRadius: 8)).drawingGroup().transaction { $0.animation = nil }.position(x: folderOrigin.midX + folderTranslation.width, y: folderOrigin.midY + folderTranslation.height).allowsHitTesting(false) } }.background(Color.clear)
    }
    func folderGrab(_ name: String) -> some Gesture {
        DragGesture(minimumDistance: 6, coordinateSpace: .named("folders")).onChanged { value in
            if draggedFolder == nil { draggedFolder = name; folderOrigin = folderFrames[name] ?? .zero }; folderTranslation = value.translation
            if let target = folderFrames.first(where: { $0.key != name && $0.value.contains(value.location) })?.key, let from = allCollections.firstIndex(of: name), let to = allCollections.firstIndex(of: target) { var order = allCollections; order.remove(at: from); order.insert(name, at: to); withAnimation(.easeOut(duration: 0.18)) { folderOrder = order }; UserDefaults.standard.set(order, forKey: "folderOrder") }
        }.onEnded { _ in draggedFolder = nil; folderTranslation = .zero }
    }
    func reorder(_ ids: [String]) { manualOrder = ids + manualOrder.filter { !ids.contains($0) }; sortOrder = "manual"; UserDefaults.standard.set(manualOrder, forKey: "manualOrder"); UserDefaults.standard.set("manual", forKey: "librarySort") }
    func sidebarCountLabel(_ count: Int) -> some View { Text(String(count)).font(.system(size: 12)).monospacedDigit().foregroundStyle(Color.primary.opacity(0.60)).padding(.trailing, 3) }
    func rowLabel(_ label: String, _ icon: String, selected: Bool = false) -> some View {
        HStack(spacing: 10) {
            Image(systemName: icon).font(.system(size: 13, weight: .medium)).frame(width: 28, height: 28)
            Text(label).font(.system(size: 13, weight: .medium)).lineLimit(1); Spacer()
            if showSidebarCounts && label != "Settings" { sidebarCountLabel(store.sidebarCount(label)) }
        }.padding(.horizontal, 7).frame(height: 34).contentShape(RoundedRectangle(cornerRadius: 8))
    }
    func sidebarRow(_ label: String, _ icon: String, selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) { rowLabel(label, icon, selected: selected).modifier(SidebarSelectionSurface(selected: selected, motion: motion)) }.buttonStyle(SidebarButtonStyle())
    }
    func nav(_ label: String, _ icon: String) -> some View { sidebarRow(label, icon, selected: section == label) { preview = nil; store.select(nil); section = label } }
    var topBar: some View {
        ZStack {
            HStack(spacing: 8) {
                if !sidebarVisible { Color.clear.frame(width: 74); GlassIcon(icon: "sidebar.left", label: "Show sidebar") { sidebarVisible = true }.leafGlass(in: Circle()) }
                HStack(spacing: 0) {
                    GlassIcon(icon: "chevron.left", label: "Go back") { navigate(-1) }.disabled(!navigation.canBack)
                    GlassIcon(icon: "chevron.right", label: "Go forward") { navigate(1) }.disabled(!navigation.canForward)
                }.leafGlass(in: Capsule())
                Spacer()
                if section == "Trash", store.selected == nil { GlassIcon(icon: "trash", label: "Empty Trash") { erasing = store.uniqueHeads.filter { $0.note.deleted }.map(\.noteId) }.leafGlass(in: Circle()).disabled(!store.uniqueHeads.contains { $0.note.deleted }) }
                HStack(spacing: 0) {
                    if store.current != nil { NoteStyleButton(store: store, interfaceScheme: scheme) }
                    if store.selected == nil {
                        if searchVisible {
                            Image(systemName: "magnifyingglass").font(.system(size: 15)).foregroundStyle(.secondary).padding(.leading, 12)
                            TextField("Search", text: $search).textFieldStyle(.plain).focused($searchFocused).frame(width: 172).padding(.horizontal, 8).onExitCommand { searchFocused = false }
                        } else {
                            GlassIcon(icon: "magnifyingglass", label: "Search notes") { withAnimation(motion) { searchVisible = true }; searchFocused = true }
                        }
                    }
                    Menu { contextualMenu } label: { Image(systemName: "ellipsis").font(.system(size: 15, weight: .semibold)).foregroundStyle(.secondary).frame(width: 36, height: 36) }.menuStyle(.button).buttonStyle(SoftButtonStyle(radius: 18)).menuIndicator(.hidden).fixedSize().tint(.primary).help("More")
                }.padding(.horizontal, 7).frame(minWidth: 44).frame(height: 38).leafGlass(in: Capsule())
            }
            if store.selected == nil { Text(selecting ? "\(selectedItems.count) selected" : title).font(.system(size: 14, weight: .semibold)).foregroundStyle(.secondary).allowsHitTesting(false).opacity(searchVisible ? 0 : 1) }
            else if formattingVisible { formatting.transition(.opacity.combined(with: .scale(scale: 0.96))) }
        }.padding(.horizontal, 10).frame(height: 52)
    }
    @ViewBuilder var contextualMenu: some View {
        if let id = store.selected {
            Button(formattingVisible ? "Hide Formatting Bar" : "Show Formatting Bar", systemImage: "textformat") { withAnimation(motion) { formattingVisible.toggle() } }.labelStyle(.titleAndIcon)
            Button("Find, Tags & History…", systemImage: "tag") { dailyTools = true }.labelStyle(.titleAndIcon)
            Button("Export Note with Attachments…", systemImage: "square.and.arrow.up") { store.exportMarkdown() }.labelStyle(.titleAndIcon)
            Button("Add File or PDF…", systemImage: "paperclip") { store.addFiles() }.labelStyle(.titleAndIcon)
            Button("Insert Table", systemImage: "tablecells") { store.addBlock("table") }.labelStyle(.titleAndIcon)
            Menu("Note Text Size · \(Int((store.current?.textScale ?? 1) * 100))%", systemImage: "textformat.size") {
                Button("Larger Text", systemImage: "plus.magnifyingglass") { store.update { $0.textScale = min(1.6, $0.textScale + 0.1) } }.labelStyle(.titleAndIcon)
                Button("Smaller Text", systemImage: "minus.magnifyingglass") { store.update { $0.textScale = max(0.8, $0.textScale - 0.1) } }.labelStyle(.titleAndIcon)
                Button("Use Default Size", systemImage: "arrow.counterclockwise") { store.update { $0.textScale = 1 } }.labelStyle(.titleAndIcon)
            }.labelStyle(.titleAndIcon)
            Button("Undo", systemImage: "arrow.uturn.backward") { store.undo() }.labelStyle(.titleAndIcon).disabled(!store.canUndo)
            Button("Redo", systemImage: "arrow.uturn.forward") { store.redo() }.labelStyle(.titleAndIcon).disabled(!store.canRedo)
            Divider(); itemMenu(id)
        } else {
            Button(selecting ? "Done Selecting" : "Select Items", systemImage: "checkmark.circle") { selecting.toggle(); selectedItems = [] }.labelStyle(.titleAndIcon)
            Button("Select All", systemImage: "checkmark.circle.fill") { selecting = true; selectedItems = Set(filtered.map(\.noteId)) }.labelStyle(.titleAndIcon).keyboardShortcut("a", modifiers: .command)
            if selecting && !selectedItems.isEmpty {
                if section == "Trash" { Button("Delete Selected permanently…", systemImage: "trash", role: .destructive) { erasing = Array(selectedItems) }.labelStyle(.titleAndIcon); Button("Restore Selected", systemImage: "arrow.uturn.backward") { for id in selectedItems { store.mutate(id) { $0.deleted = false } }; selectedItems = []; selecting = false }.labelStyle(.titleAndIcon) }
                else {
                    Button("Archive Selected", systemImage: "archivebox") { for id in selectedItems { store.mutate(id) { $0.archived = true } }; selectedItems = []; selecting = false }.labelStyle(.titleAndIcon)
                    Button("Move Selected to Trash", systemImage: "trash") { for id in selectedItems { store.mutate(id) { $0.deleted = true } }; selectedItems = []; selecting = false }.labelStyle(.titleAndIcon)
                }
            }
            Menu("Sort By", systemImage: "arrow.up.arrow.down") {
                ForEach([("manual", "Default (Manual)"), ("edited", "Recently Edited"), ("added", "Recently Added")], id: \.0) { value in
                    Button { sortOrder = value.0; UserDefaults.standard.set(sortOrder, forKey: "librarySort") } label: { if sortOrder == value.0 { Label(value.1, systemImage: "checkmark") } else { Label(value.1, systemImage: value.0 == "manual" ? "hand.draw" : "clock") } }
                }
            }.labelStyle(.titleAndIcon)
            if section == "Trash" { Button("Empty Trash…", systemImage: "trash", role: .destructive) { erasing = filtered.map(\.noteId) }.labelStyle(.titleAndIcon) }; Divider(); Button("New Note", systemImage: "square.and.pencil") { createNote() }.labelStyle(.titleAndIcon); Button("Sync Now", systemImage: "arrow.triangle.2.circlepath") { Task { await store.sync() } }.labelStyle(.titleAndIcon)
            Divider(); Button("Export Backup…", systemImage: "archivebox") { store.exportBackup() }.labelStyle(.titleAndIcon); Button("Import Backup…", systemImage: "tray.and.arrow.down") { store.importBackup() }.labelStyle(.titleAndIcon)
        }
    }
    @ViewBuilder func itemMenu(_ id: String) -> some View {
        if let revision = store.uniqueHeads.first(where: { $0.noteId == id }) {
            Button("Open in Window", systemImage: "macwindow") { openWindow(id) }.labelStyle(.titleAndIcon)
            Button("Select", systemImage: "checkmark.circle") { store.select(nil); selecting = true; selectedItems.insert(id) }.labelStyle(.titleAndIcon)
            Button("Copy", systemImage: "doc.on.doc") { NSPasteboard.general.clearContents(); NSPasteboard.general.setString(revision.note.displayTitle + "\n\n" + revision.note.text, forType: .string) }.labelStyle(.titleAndIcon)
            Divider(); Button("Move…", systemImage: "folder") { moveTarget = id; moveCollection = true }.labelStyle(.titleAndIcon)
            Divider(); Button(revision.note.pinned ? "Unpin" : "Pin", systemImage: "pin") { store.mutate(id) { $0.pinned.toggle() } }.labelStyle(.titleAndIcon)
            Button("Duplicate", systemImage: "doc.on.doc") { store.duplicate(id) }.labelStyle(.titleAndIcon)
            Menu("Order", systemImage: "arrow.up.arrow.down") { Button("Move to Beginning", systemImage: "arrow.up.to.line") { order(id, first: true) }.labelStyle(.titleAndIcon); Button("Move to End", systemImage: "arrow.down.to.line") { order(id, first: false) }.labelStyle(.titleAndIcon) }.labelStyle(.titleAndIcon)
            Divider(); Button(revision.note.archived ? "Unarchive" : "Archive", systemImage: "archivebox") { store.mutate(id) { $0.archived.toggle() }; if store.selected == id { store.select(nil) } }.labelStyle(.titleAndIcon)
            if revision.note.deleted { Button("Delete permanently…", systemImage: "trash", role: .destructive) { erasing = selectedItems.contains(id) ? Array(selectedItems) : [id] }.labelStyle(.titleAndIcon) }
            Button(revision.note.deleted ? "Restore" : "Move to Trash", systemImage: revision.note.deleted ? "arrow.uturn.backward" : "trash") { let ids = selectedItems.contains(id) ? Array(selectedItems) : [id]; for target in ids { store.mutate(target) { $0.deleted = !revision.note.deleted } }; selectedItems.subtract(ids); if store.selected == id { store.select(nil) } }.labelStyle(.titleAndIcon)
        }
    }
    var formatting: some View { EditorToolbar(store: store, showsStyle: false) }
    var gallery: some View {
        GeometryReader { geometry in
            let count = max(3, Int((geometry.size.width - 48 + 18) / 218))
            let width: CGFloat = min(200, (geometry.size.width - 48 - CGFloat(count - 1) * 18) / CGFloat(count))
            ZStack(alignment: .bottom) {
                LeafScrollView {
                    VStack(alignment: .leading, spacing: 20) {
                        if filtered.isEmpty { emptyState.frame(width: max(0, geometry.size.width - 80)).padding(.top, 100) }
                        LazyVGrid(columns: Array(repeating: GridItem(.fixed(width), spacing: 18), count: count), alignment: .leading, spacing: 18) {
                            ForEach(Array(filtered.enumerated()), id: \.element.id) { index, r in
                                Group {
                                if section == "Images" || (r.note.document.filter { $0.isText }.allSatisfy { $0.text.isEmpty } && r.note.attachments.contains { $0.mime.hasPrefix("image/") }) {
                                    ForEach(r.note.attachments.filter { $0.mime.hasPrefix("image/") }) { a in
                                        CardInteraction(selected: selectedItems.contains(r.noteId), action: { openCard(r.noteId, image: a) }) {
                                            ImageCard(attachment: a, collection: r.note.collection, media: store.media, style: r.note.style)
                                        } menu: { Button("View Image", systemImage: "photo") { preview = a }.labelStyle(.titleAndIcon); Button("Copy Image", systemImage: "doc.on.doc") { copyImage(a) }.labelStyle(.titleAndIcon); Divider(); itemMenu(r.noteId) }.frame(width: width).modifier(GalleryGrab(id: r.noteId, dragging: dragging, gesture: grab(r, image: a)))
                                    }
                                } else {
                                    CardInteraction(selected: selectedItems.contains(r.noteId), action: { openCard(r.noteId) }) {
                                        NoteCard(revision: r, conflict: store.heads.filter { $0.noteId == r.noteId }.count > 1)
                                    } menu: { itemMenu(r.noteId) }.frame(width: width).modifier(GalleryGrab(id: r.noteId, dragging: dragging, gesture: grab(r)))

                                }
                                }.modifier(CardEntrance(route: r.noteId, index: index, reduced: reducedMotion || calmMotion))
                            }
                        }
                    }.padding(.horizontal, 24).padding(.top, 98).padding(.bottom, 100).frame(maxWidth: .infinity, alignment: .leading)
                }.scrollIndicators(.never).contentShape(Rectangle())
                    .simultaneousGesture(DragGesture(minimumDistance: 6, coordinateSpace: .named("libraryDrag")).onChanged { value in
                        guard dragging == nil, !cardFrames.values.contains(where: { $0.contains(value.startLocation) }) else { return }
                        if selectionRect == nil { selectionBefore = NSEvent.modifierFlags.contains(.command) ? selectedItems : []; selecting = true }
                        let rect = CGRect(x: min(value.startLocation.x, value.location.x), y: min(value.startLocation.y, value.location.y), width: abs(value.location.x - value.startLocation.x), height: abs(value.location.y - value.startLocation.y))
                        selectionRect = rect; selectedItems = selectionBefore.union(cardFrames.filter { $0.value.intersects(rect) }.map(\.key))
                    }.onEnded { _ in selectionRect = nil })
                    .simultaneousGesture(SpatialTapGesture(coordinateSpace: .named("libraryDrag")).onEnded { value in if !cardFrames.values.contains(where: { $0.contains(value.location) }) { selectedItems.removeAll(); selecting = false; searchFocused = false } })
                if let rect = selectionRect { RoundedRectangle(cornerRadius: 4).fill(LeafPalette.accent.opacity(0.12)).overlay(RoundedRectangle(cornerRadius: 4).stroke(LeafPalette.accent.opacity(0.7), lineWidth: 1)).frame(width: rect.width, height: rect.height).position(x: rect.midX - geometry.frame(in: .named("libraryDrag")).minX, y: rect.midY - geometry.frame(in: .named("libraryDrag")).minY).allowsHitTesting(false) }
                HStack(spacing: 2) {
                    GlassIcon(icon: "square.and.pencil", label: "New note") { createNote() }
                    GlassIcon(icon: "photo.on.rectangle", label: "Add images") { createNote(); store.addImages() }
                }.padding(.horizontal, 10).padding(.vertical, 3).leafGlass(in: Capsule()).padding(.bottom, 18)
            }.onPreferenceChange(GalleryFrames.self) { cardFrames = $0 }
        }
    }
    func grab(_ r: Revision, image: Attachment? = nil) -> some Gesture {
        DragGesture(minimumDistance: 8, coordinateSpace: .named("libraryDrag")).onChanged { value in
            if dragging == nil || dropSettling { dropSettling = false; grabbedFrame = cardFrames[r.noteId] ?? .zero; grabbedRevision = r; grabbedImage = image; dragging = r.noteId }
            dragTranslation = value.translation
            folderDropTarget = collectionDropFrames.first(where: { $0.value.contains(value.location) })?.key
            if folderDropTarget == nil, let target = cardFrames.first(where: { $0.key != r.noteId && $0.value.contains(value.location) })?.key {
                var ids = filtered.map(\.noteId)
                if let from = ids.firstIndex(of: r.noteId), let to = ids.firstIndex(of: target) { ids.remove(at: from); ids.insert(r.noteId, at: to); withAnimation(reducedMotion || calmMotion ? nil : .easeInOut(duration: 0.18)) { reorder(ids) } }
            }
        }.onEnded { value in
            if let target = collectionDropFrames.first(where: { $0.value.contains(value.location) }) {
                store.mutate(r.noteId) { $0.collection = target.key }
                withAnimation(reducedMotion || calmMotion ? nil : .spring(response: 0.25, dampingFraction: 1)) {
                    dragTranslation = CGSize(width: target.value.midX - grabbedFrame.midX, height: target.value.midY - grabbedFrame.midY); dropSettling = true
                }
                DispatchQueue.main.asyncAfter(deadline: .now() + (reducedMotion || calmMotion ? 0 : 0.25)) { if dropSettling { finishCardDrag() } }
            } else { finishCardDrag() }
        }
    }
    func finishCardDrag() { dragging = nil; grabbedRevision = nil; grabbedImage = nil; dragTranslation = .zero; folderDropTarget = nil; dropSettling = false }
    var photoItems: [ViewerPhoto] {
        if let n = store.current, let id = store.selected { return ViewerPhoto.items(note: n, noteId: id) }
        return filtered.flatMap { ViewerPhoto.items(note: $0.note, noteId: $0.noteId) }
    }
    var emptyState: some View {
        let kind = search.isEmpty ? section.lowercased() : "search"
        let title: String = switch kind { case "search": "No matching notes"; case "images": "A place for inspiration"; case "trash": "Trash is empty"; case "archive": "Nothing archived yet"; case "pinned": "Keep favourites close"; case "checklists": "One thing at a time"; default: "Room for a new thought" }
        let detail: String = switch kind { case "search": "Try another word."; case "images": "Add a photo to a note to see it here."; case "trash": "Deleted notes will appear here."; case "archive": "Archived notes stay here for later."; case "pinned": "Pin a note to find it here."; case "checklists": "Start a note with a checklist."; default: "Keep a thought, an image, a little idea." }
        return GhostEmpty(kind: kind, title: title, detail: detail, actionLabel: ["trash", "archive"].contains(kind) ? nil : kind == "search" ? "Clear search" : kind == "pinned" ? "Browse notes" : "Create a note", action: { if kind == "search" { search = "" } else if kind == "pinned" { section = "Everything" } else { createNote() } })
    }
    func editor(_ note: Note, _ id: String) -> some View {
        DocumentEditor(store: store, note: note, noteId: id, onPreview: { preview = $0 }, onConflict: { showConflicts = true }).environment(\.colorScheme, note.style?.documentScheme ?? scheme)
    }
    func createNote() { store.create(); if section == "Collection" { store.update { $0.collection = collection } } }
    func openCard(_ id: String, image: Attachment? = nil) {
        searchFocused = false
        if selecting { if selectedItems.contains(id) { selectedItems.remove(id) } else { selectedItems.insert(id) } }
        else if let image { preview = image } else { store.select(id) }
    }
    func addedDate(_ id: String) -> Int64 { (try? store.revisions().filter { $0.noteId == id }.map(\.createdAt).min()) ?? 0 }
    func order(_ id: String, first: Bool) {
        var ids = filtered.map(\.noteId).filter { $0 != id }; if first { ids.insert(id, at: 0) } else { ids.append(id) }
        manualOrder = ids; sortOrder = "manual"; UserDefaults.standard.set(ids, forKey: "manualOrder"); UserDefaults.standard.set("manual", forKey: "librarySort")
    }
    func copyImage(_ a: Attachment) { if let image = NSImage(contentsOf: store.media.appendingPathComponent(a.id)) { NSPasteboard.general.clearContents(); NSPasteboard.general.writeObjects([image]) } }
    func openWindow(_ id: String) {
        store.flush(); let separateStore = NoteStore(root: store.root, recoverDrafts: false); separateStore.select(id)
        let window = NSWindow(contentRect: NSRect(x: 0, y: 0, width: 1000, height: 740), styleMask: [.titled, .closable, .resizable, .miniaturizable, .fullSizeContentView], backing: .buffered, defer: false)
        let appearance = UserDefaults.standard.string(forKey: "appearance") ?? "system"
        window.title = separateStore.current?.displayTitle ?? "Pebble Notes"
        window.isReleasedWhenClosed = false; window.contentView = NSHostingView(rootView: NoteWindowView(store: separateStore).preferredColorScheme(appearance == "light" ? .light : appearance == "dark" ? .dark : nil)); window.center(); window.makeKeyAndOrderFront(nil)
        _ = NoteWindows.observer
        NoteWindows.windows.append(window)
    }
    var movePicker: some View {
        VStack(alignment: .leading, spacing: 12) { Text("Move to collection").font(.headline); ForEach(allCollections, id: \.self) { name in Button(name) { store.mutate(moveTarget) { $0.collection = name }; moveCollection = false } }; Button("Cancel") { moveCollection = false } }.padding(22).frame(width: 260)
    }
    @ViewBuilder func collectionIcon(_ name: String) -> some View {
        let _ = iconVersion
        if let folder = store.folderHeads.first(where: { $0.note.collection == name })?.note, !folder.folderImage.isEmpty, let image = NSImage(contentsOf: store.media.appendingPathComponent(folder.folderImage)) { Image(nsImage: image).resizable().scaledToFill().clipShape(RoundedRectangle(cornerRadius: 5)) }
        else if let emoji = store.folderHeads.first(where: { $0.note.collection == name })?.note.folderEmoji, !emoji.isEmpty { Text(emoji).font(.system(size: 16)) }
        else if let file = UserDefaults.standard.string(forKey: "collectionImage:" + name), let image = NSImage(contentsOf: store.root.appendingPathComponent(file)) { Image(nsImage: image).resizable().scaledToFill().clipShape(RoundedRectangle(cornerRadius: 5)) }
        else if let emoji = UserDefaults.standard.string(forKey: "collectionEmoji:" + name), !emoji.isEmpty { Text(emoji).font(.system(size: 16)) }
        else { Image(systemName: "folder").font(.system(size: 13)) }
    }
    func editCollection(_ name: String) { editingCollection = name; collectionName = name; let folder = store.folderHeads.first(where: { $0.note.collection == name })?.note; collectionEmoji = folder?.folderEmoji ?? UserDefaults.standard.string(forKey: "collectionEmoji:" + name) ?? ""; collectionImage = (folder?.folderImage.isEmpty == false ? "media/" + folder!.folderImage : nil) ?? UserDefaults.standard.string(forKey: "collectionImage:" + name) ?? ""; iconMode = collectionImage.isEmpty ? (collectionEmoji.isEmpty ? 0 : 1) : 2; newCollection = true }
    var collectionSheet: some View {
        VStack(spacing: 22) {
            HStack { Text("Name").font(.system(size: 14, weight: .semibold)).frame(width: 72, alignment: .leading); TextField("My collection", text: $collectionName).textFieldStyle(.plain).padding(10).background(Color.primary.opacity(0.04), in: RoundedRectangle(cornerRadius: 8)).onSubmit { createCollection() } }
            HStack(spacing: 16) {
                Text("Icon").font(.system(size: 14, weight: .semibold)).frame(width: 72, alignment: .leading)
                ForEach(0..<3) { mode in Button { iconMode = mode; if mode == 2 { chooseCollectionImage() } } label: {
                    ZStack { Circle().fill(Color.primary.opacity(0.06)); Image(systemName: mode == 0 ? "nosign" : mode == 1 ? "face.smiling" : "photo").font(.system(size: 22)).foregroundStyle(.secondary) }.frame(width: 48, height: 48).overlay(Circle().strokeBorder(iconMode == mode ? LeafPalette.accent : .clear, lineWidth: 2))
                }.buttonStyle(SoftButtonStyle()) }; Spacer()
            }
            if iconMode == 1 { HStack { TextField("Emoji", text: $collectionEmoji).textFieldStyle(.plain).padding(10).background(Color.primary.opacity(0.04), in: RoundedRectangle(cornerRadius: 8)).focused($emojiFocused).frame(width: 64); Button("Choose Emoji…") { emojiFocused = true; DispatchQueue.main.async { NSApp.orderFrontCharacterPalette(nil) } }; Text("Pick an emoji for the sidebar").font(.system(size: 12)).foregroundStyle(.secondary) } }
            if iconMode == 2, !collectionImage.isEmpty, let image = NSImage(contentsOf: store.root.appendingPathComponent(collectionImage)) { Image(nsImage: image).resizable().scaledToFit().frame(height: 64).clipShape(RoundedRectangle(cornerRadius: 10)) }
            SubtleDivider()
            HStack { Spacer(); Button("Cancel") { newCollection = false }; Button("Save") { createCollection() }.disabled(collectionName.trimmingCharacters(in: .whitespaces).isEmpty).keyboardShortcut(.defaultAction) }
        }.padding(28).frame(width: 430).animation(motion, value: iconMode)
    }
    func chooseCollectionImage() {
        let panel = NSOpenPanel(); panel.allowedContentTypes = [.image]
        guard panel.runModal() == .OK, let url = panel.url else { return }
        do { let directory = store.root.appendingPathComponent("collection-icons"); try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true); let name = "collection-icons/" + UUID().uuidString + "." + url.pathExtension; try Data(contentsOf: url).write(to: store.root.appendingPathComponent(name), options: .atomic); collectionImage = name } catch { store.error = error.localizedDescription }
    }
    func createCollection() {
        let name = collectionName.trimmingCharacters(in: .whitespacesAndNewlines); guard !name.isEmpty else { return }
        if !editingCollection.isEmpty && editingCollection != name {
            let old = editingCollection
            store.renameFolder(old, to: name)
            extraCollections.removeAll { $0 == old }
            UserDefaults.standard.removeObject(forKey: "collectionEmoji:" + old); UserDefaults.standard.removeObject(forKey: "collectionImage:" + old)
        }
        extraCollections = Array(Set(extraCollections + [name])).sorted(); UserDefaults.standard.set(extraCollections, forKey: "extraCollections")
        UserDefaults.standard.set(iconMode == 1 ? collectionEmoji : "", forKey: "collectionEmoji:" + name)
        UserDefaults.standard.set(iconMode == 2 ? collectionImage : "", forKey: "collectionImage:" + name)
        store.createFolder(name, emoji: iconMode == 1 ? collectionEmoji : "", image: iconMode == 2 && !collectionImage.isEmpty ? store.root.appendingPathComponent(collectionImage) : nil)
        iconVersion += 1; store.select(nil); section = "Collection"; collection = name; newCollection = false
    }
    var conflicts: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Both versions are safe").font(.title2.bold()); Text("Choose a version to continue with. Earlier versions remain in your backup and revision history.").foregroundStyle(.secondary)
            LeafScrollView { ForEach(store.heads.filter { $0.noteId == store.selected }) { r in VStack(alignment: .leading, spacing: 8) { Text(r.note.displayTitle).font(.headline); Text(r.note.deleted ? "Deleted on another device" : String(r.note.text.prefix(500))); Button("Continue with this version") { store.resolve(r); showConflicts = false } }.padding().frame(maxWidth: .infinity, alignment: .leading).background(.quaternary, in: RoundedRectangle(cornerRadius: 14)) } }
            Button("Keep both for now") { showConflicts = false }
        }.padding(28).frame(width: 560, height: 520)
    }
}
@MainActor enum NoteWindows {
    static var windows: [NSWindow] = []
    static let observer = NotificationCenter.default.addObserver(forName: NSWindow.willCloseNotification, object: nil, queue: .main) { notification in
        MainActor.assumeIsolated { if let closing = notification.object as? NSWindow { windows.removeAll { $0 === closing } } }
    }
}
struct CardInteraction<Content: View, Actions: View>: View {
    var selected: Bool; var action: () -> Void
    @ViewBuilder var content: () -> Content
    @ViewBuilder var menu: () -> Actions
    @LeafState<Bool> private var hovering = false
    @Environment(\.accessibilityReduceMotion) var reduced
    var body: some View {
        Button(action: action) { content() }.buttonStyle(SoftButtonStyle(radius: 22))
            .overlay(alignment: .topTrailing) {
                Menu { menu() } label: { Image(systemName: "ellipsis").font(.system(size: 13, weight: .semibold)).foregroundStyle(.primary).frame(width: 28, height: 28).background(.ultraThinMaterial, in: Circle()) }.menuStyle(.button).buttonStyle(SoftButtonStyle(radius: 18)).menuIndicator(.hidden).fixedSize().padding(12).opacity(hovering || selected ? 1 : 0).allowsHitTesting(hovering || selected).help("Card options")
            }
            .overlay(RoundedRectangle(cornerRadius: 22).strokeBorder(selected ? LeafPalette.accent : .clear, lineWidth: 2))
            .overlay(RoundedRectangle(cornerRadius: 22).fill(Color.primary.opacity(hovering ? 0.025 : 0)).allowsHitTesting(false))
            .onHover { hovering = $0 }
            .animation(reduced ? nil : .easeInOut(duration: 0.16), value: hovering)
            .animation(reduced ? nil : .spring(response: 0.25, dampingFraction: 0.92), value: selected)
            .contextMenu { menu() }
    }
}
struct NoteCard: View {
    var revision: Revision; var conflict: Bool
    @Environment(\.colorScheme) var scheme
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(revision.note.displayTitle).font(.system(size: 20, weight: .semibold)).tracking(-0.4).lineLimit(2)
            Text(revision.note.text.isEmpty ? "" : revision.note.text).font(.system(size: 14)).lineSpacing(5).foregroundStyle((revision.note.style ?? NoteStyle()).foreground.opacity(0.65)).frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading).clipped()
                .mask { LinearGradient(stops: [.init(color: .white, location: 0), .init(color: .white, location: 0.68), .init(color: .clear, location: 1)], startPoint: .top, endPoint: .bottom) }
            HStack(spacing: 5) { Spacer(); CollectionPill(name: revision.note.collection); if revision.note.pinned { Image(systemName: "pin.fill").font(.system(size: 9)).foregroundStyle((revision.note.style ?? NoteStyle()).foreground.opacity(0.65)) }; if conflict { Image(systemName: "arrow.triangle.branch").font(.system(size: 10)).foregroundStyle(.orange) }; Spacer() }
        }.padding(.horizontal, 22).padding(.top, 22).padding(.bottom, revision.note.style?.framed == true ? 22 : 8).frame(height: 200).frame(maxWidth: .infinity, alignment: .leading)
            .modifier(NotePreviewSurface(style: revision.note.style))
    }
}
struct CollectionPill: View {
    var name: String
    var body: some View { Text(name).font(.system(size: 11, weight: .medium)).foregroundStyle(.secondary).lineLimit(1).padding(.horizontal, 7).padding(.vertical, 2).overlay(Capsule().stroke(Color.primary.opacity(0.08), lineWidth: 0.7)) }
}
struct ImageCard: View {
    var attachment: Attachment; var collection: String; var media: URL; var style: NoteStyle? = nil
    @Environment(\.colorScheme) var scheme
    var body: some View {
        VStack(spacing: 5) { Thumbnail(url: media.appendingPathComponent(attachment.id)).frame(height: 166).clipShape(RoundedRectangle(cornerRadius: 16)); CollectionPill(name: collection) }
            .padding(6).frame(height: style?.framed == true ? 184 : 200).modifier(NotePreviewSurface(style: style))
    }
}
func imageAspect(_ url: URL) -> Double {
    guard let source = CGImageSourceCreateWithURL(url as CFURL, nil), let p = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any], let w = p[kCGImagePropertyPixelWidth] as? Double, let h = p[kCGImagePropertyPixelHeight] as? Double, h > 0 else { return 1 }
    return w / h
}
struct InlinePhoto: View {
    var attachment: Attachment; var media: URL; var width: Double; var open: () -> Void
    var body: some View { Button(action: open) { Thumbnail(url: media.appendingPathComponent(attachment.id), fit: true).frame(width: min(width, 520 * imageAspect(media.appendingPathComponent(attachment.id))), height: min(520, width / imageAspect(media.appendingPathComponent(attachment.id)))) }.buttonStyle(.plain).accessibilityLabel("View \(attachment.name)").clipShape(RoundedRectangle(cornerRadius: 12)) }
}
struct Thumbnail: View {
    var url: URL; var fit = false
    @LeafState<NSImage?> private var image: NSImage?
    var body: some View {
        GeometryReader { geometry in
            ZStack { Color.primary.opacity(0.025); if let image { Image(nsImage: image).resizable().aspectRatio(contentMode: fit ? .fit : .fill).frame(width: geometry.size.width, height: geometry.size.height).clipped() } else { Image(systemName: "photo").foregroundStyle(.tertiary) } }
        }
        .task(id: url) {
            let result = await Task.detached(priority: .utility) { () -> CGImage? in guard let source = CGImageSourceCreateWithURL(url as CFURL, nil) else { return nil }; return CGImageSourceCreateThumbnailAtIndex(source, 0, [kCGImageSourceCreateThumbnailFromImageAlways: true, kCGImageSourceThumbnailMaxPixelSize: fit ? 1500 : 600, kCGImageSourceCreateThumbnailWithTransform: true] as CFDictionary) }.value
            if let result { image = NSImage(cgImage: result, size: .zero) }
        }
    }
}
@MainActor final class EditorActions: ObservableObject {
    @Published var styleLabel = "Body"
    static let shared = EditorActions(); private weak var lastView: NSTextView?
    var view: NSTextView? {
        get {
            if let window = NSApp?.keyWindow {
                if lastView?.window === window { return lastView }
                func findEditor(_ container: NSView) -> NSTextView? {
                    if let editor = container as? LeafTextView { return editor }
                    for child in container.subviews { if let editor = findEditor(child) { return editor } }
                    return nil
                }
                if let content = window.contentView, let editor = findEditor(content) { return editor }
            }
            return lastView
        }
        set { lastView = newValue }
    }
    func prefixLine(_ prefix: String) {
        guard let view else { return }
        let range = (view.string as NSString).lineRange(for: view.selectedRange())
        let line = (view.string as NSString).substring(with: range)
        let replacement = line.hasPrefix(prefix) ? String(line.dropFirst(prefix.count)) : prefix + line
        if view.shouldChangeText(in: range, replacementString: replacement) { view.insertText(replacement, replacementRange: range); view.didChangeText() }
    }
    func textStyle(_ style: String) {
        styleLabel = style.capitalized
        (view as? LeafTextView)?.styleChanged?(style)
        guard let view, let storage = view.textStorage else { return }
        let selection = view.selectedRange()
        let range = selection.length > 0 ? selection : (view.string as NSString).paragraphRange(for: selection)
        let font = NSFont.systemFont(ofSize: Typography.size(style) * (view.typingAttributes[NSAttributedString.Key("leafTextScale")] as? Double ?? 1), weight: style == "body" ? .regular : .semibold)
        let key = NSAttributedString.Key("leafTextStyle")
        var attributes = view.typingAttributes; attributes[.font] = font
        if style == "body" { attributes.removeValue(forKey: key) } else { attributes[key] = style }
        if range.length > 0 {
            let old = storage.attributedSubstring(from: range)
            view.undoManager?.registerUndo(withTarget: view) { target in target.textStorage?.replaceCharacters(in: range, with: old); target.didChangeText() }
            storage.enumerateAttribute(.font, in: range) { oldFont, part, _ in
                var sized = font
                if let oldFont = oldFont as? NSFont, NSFontManager.shared.traits(of: oldFont).contains(.italicFontMask) { sized = NSFontManager.shared.convert(sized, toHaveTrait: .italicFontMask) }
                let explicit = storage.attribute(NSAttributedString.Key("leafExplicitBold"), at: part.location, effectiveRange: nil) as? Bool == true
                let oldStyle = storage.attribute(NSAttributedString.Key("leafTextStyle"), at: part.location, effectiveRange: nil)
                if let oldFont = oldFont as? NSFont, explicit || (oldStyle == nil && NSFontManager.shared.traits(of: oldFont).contains(.boldFontMask)) {
                    sized = NSFontManager.shared.convert(sized, toHaveTrait: .boldFontMask)
                    storage.addAttribute(NSAttributedString.Key("leafExplicitBold"), value: true, range: part)
                }
                storage.addAttribute(.font, value: sized, range: part)
            }
            if style == "body" { storage.removeAttribute(key, range: range) } else { storage.addAttribute(key, value: style, range: range) }
            view.didChangeText(); view.setSelectedRange(selection)
        }
        view.typingAttributes = attributes; view.window?.makeFirstResponder(view)
    }
    func link() {
        guard let view else { return }
        let alert = NSAlert(); alert.messageText = "Add a link"; alert.addButton(withTitle: "Add"); alert.addButton(withTitle: "Cancel")
        let input = NSTextField(frame: NSRect(x: 0, y: 0, width: 320, height: 24)); input.placeholderString = "https://…"; alert.accessoryView = input
        guard alert.runModal() == .alertFirstButtonReturn, let url = URL(string: input.stringValue), ["https", "http", "mailto"].contains(url.scheme?.lowercased() ?? "") else { return }
        var range = view.selectedRange()
        if range.length == 0 { let text = url.absoluteString; view.insertText(text, replacementRange: range); range.length = text.utf16.count }
        let old = view.textStorage!.attributedSubstring(from: range)
        view.undoManager?.registerUndo(withTarget: view) { target in target.textStorage?.replaceCharacters(in: range, with: old); target.didChangeText() }
        view.textStorage?.addAttribute(.link, value: url.absoluteString, range: range); view.didChangeText(); view.window?.makeFirstResponder(view)
    }
    func format(_ kind: String) {
        guard let view, let storage = view.textStorage else { return }
        let range = view.selectedRange()
        if range.length == 0 {
            var attributes = view.typingAttributes
            if kind == "bold" || kind == "italic" {
                let mask: NSFontTraitMask = kind == "bold" ? .boldFontMask : .italicFontMask
                let font = attributes[.font] as? NSFont ?? .systemFont(ofSize: 18)
                let removing = kind == "bold" && attributes[NSAttributedString.Key("leafTextStyle")] != nil ? attributes[NSAttributedString.Key("leafExplicitBold")] as? Bool == true : NSFontManager.shared.traits(of: font).contains(mask)
                attributes[.font] = removing ? NSFontManager.shared.convert(font, toNotHaveTrait: mask) : NSFontManager.shared.convert(font, toHaveTrait: mask)
                if kind == "bold" { if removing { attributes.removeValue(forKey: NSAttributedString.Key("leafExplicitBold")) } else { attributes[NSAttributedString.Key("leafExplicitBold")] = true } }
            } else { let key: NSAttributedString.Key = kind == "strike" ? .strikethroughStyle : .underlineStyle; if attributes[key] != nil { attributes.removeValue(forKey: key) } else { attributes[key] = 1 } }
            view.typingAttributes = attributes; view.window?.makeFirstResponder(view); return
        }
        view.undoManager?.beginUndoGrouping()
        let old = storage.attributedSubstring(from: range)
        view.undoManager?.registerUndo(withTarget: view) { target in target.textStorage?.replaceCharacters(in: range, with: old); target.didChangeText() }
        if kind == "bold" || kind == "italic" {
            let mask: NSFontTraitMask = kind == "bold" ? .boldFontMask : .italicFontMask
            let font = storage.attribute(.font, at: range.location, effectiveRange: nil) as? NSFont ?? .systemFont(ofSize: 17)
            let removing = kind == "bold" && storage.attribute(NSAttributedString.Key("leafTextStyle"), at: range.location, effectiveRange: nil) != nil ? storage.attribute(NSAttributedString.Key("leafExplicitBold"), at: range.location, effectiveRange: nil) as? Bool == true : NSFontManager.shared.traits(of: font).contains(mask)
            if kind == "bold" { if removing { storage.removeAttribute(NSAttributedString.Key("leafExplicitBold"), range: range) } else { storage.addAttribute(NSAttributedString.Key("leafExplicitBold"), value: true, range: range) } }
            storage.enumerateAttribute(.font, in: range) { value, r, _ in let f = value as? NSFont ?? .systemFont(ofSize: 17); storage.addAttribute(.font, value: removing ? NSFontManager.shared.convert(f, toNotHaveTrait: mask) : NSFontManager.shared.convert(f, toHaveTrait: mask), range: r) }
        } else {
            let key: NSAttributedString.Key = kind == "strike" ? .strikethroughStyle : .underlineStyle
            if storage.attribute(key, at: range.location, effectiveRange: nil) != nil { storage.removeAttribute(key, range: range) } else { storage.addAttribute(key, value: 1, range: range) }
        }
        view.didChangeText(); view.undoManager?.endUndoGrouping()
    }
}
class LeafTextView: NSTextView {
    var images: (([URL]) -> Void)?
    var focused: (() -> Void)?
    var enter: (() -> Bool)?
    var backspace: (() -> Bool)?
    var styleChanged: ((String) -> Void)?
    var awaitingBlock = false
    var queuedInput: [String] = []
    override func insertText(_ value: Any, replacementRange: NSRange) {
        if awaitingBlock { queuedInput.append((value as? NSAttributedString)?.string ?? String(describing: value)); return }
        super.insertText(value, replacementRange: replacementRange)
    }
    lazy var slash = SlashController(self)
    override func keyDown(with event: NSEvent) { if slash.key(event) { return }; super.keyDown(with: event) }
    override func resignFirstResponder() -> Bool { slash.close(); return super.resignFirstResponder() }
    override func deleteBackward(_ sender: Any?) { if selectedRange().location == 0 && selectedRange().length == 0 && backspace?() == true { return }; super.deleteBackward(sender) }
    override func insertNewline(_ sender: Any?) { if awaitingBlock { queuedInput.append("\n"); return }; if NSApp.currentEvent?.modifierFlags.contains(.option) == true { super.insertNewline(sender); return }; if enter?() == true { return }; super.insertNewline(sender) }
    override func mouseDown(with event: NSEvent) {
        let point = convert(event.locationInWindow, from: nil)
        if let layoutManager, let textContainer {
            let local = NSPoint(x: point.x - textContainerOrigin.x, y: point.y - textContainerOrigin.y)
            let index = layoutManager.characterIndex(for: local, in: textContainer, fractionOfDistanceBetweenInsertionPoints: nil)
            if index < (string as NSString).length {
                let range = (string as NSString).lineRange(for: NSRange(location: index, length: 0)); let line = (string as NSString).substring(with: range)
                if index <= range.location + 1 && (line.hasPrefix("☐ ") || line.hasPrefix("☑ ")) { insertText(line.hasPrefix("☐ ") ? "☑" : "☐", replacementRange: NSRange(location: range.location, length: 1)); return }
            }
        }
        super.mouseDown(with: event)
    }
    override func paste(_ sender: Any?) {
        if let urls = NSPasteboard.general.readObjects(forClasses: [NSURL.self], options: nil) as? [URL] { let files = urls.filter { $0.isFileURL }; if !files.isEmpty { images?(files); return } }
        if let image = NSImage(pasteboard: .general), let data = image.tiffRepresentation {
            let url = FileManager.default.temporaryDirectory.appendingPathComponent("Pasted-\(UUID().uuidString).tiff")
            do { try data.write(to: url); images?([url]); try? FileManager.default.removeItem(at: url) } catch { NSSound.beep() }; return
        }
        super.paste(sender)
    }
    override func performKeyEquivalent(with event: NSEvent) -> Bool {
        if event.modifierFlags.contains(.command), let key = event.charactersIgnoringModifiers, ["b", "i", "u"].contains(key) { EditorActions.shared.view = self; EditorActions.shared.format(key == "b" ? "bold" : key == "i" ? "italic" : "underline"); return true }
        return super.performKeyEquivalent(with: event)
    }
    override func becomeFirstResponder() -> Bool { EditorActions.shared.view = self; focused?(); return super.becomeFirstResponder() }
}
class LeafEditorScrollView: NSScrollView {
    override func layout() {
        super.layout()
        guard let text = documentView as? LeafTextView else { return }
        if let font = text.font { let line = font.ascender - font.descender + font.leading; text.textContainerInset.height = contentSize.height <= line * 1.8 ? max(1, (contentSize.height - line) / 2) : 1 }
        text.minSize = NSSize(width: contentSize.width, height: max(32, contentSize.height))
        let size = NSSize(width: contentSize.width, height: max(text.frame.height, max(32, contentSize.height)))
        if text.frame.size != size { text.setFrameSize(size) }
    }
    override func mouseDown(with event: NSEvent) { if let text = documentView as? LeafTextView { window?.makeFirstResponder(text) }; super.mouseDown(with: event) }
}
struct RichEditor: NSViewRepresentable {
    var note: Note; var onEdit: (String, [TextSpan]) -> Void; var onImages: ([URL]) -> Void
    var onFocus: (() -> Void)? = nil
    var onEnter: (() -> Bool)? = nil
    var completed: Bool = false
    var onCommand: ((String) -> Void)? = nil
    var blockId: String? = nil
    var blockStyle: String = "body"
    var onStyle: ((String) -> Void)? = nil
    var onBackspace: (() -> Bool)? = nil
    var displayed: NSAttributedString { let value = NSMutableAttributedString(attributedString: note.attributed); if completed && value.length > 0 { value.enumerateAttribute(.strikethroughStyle, in: NSRange(location: 0, length: value.length)) { existing, range, _ in if existing == nil { value.addAttributes([.strikethroughStyle: NSUnderlineStyle.single.rawValue, NSAttributedString.Key("leafCompletionStrike"): true], range: range) } } }; return value }
    func makeCoordinator() -> Coordinator { Coordinator(self) }
    func makeNSView(context: Context) -> NSScrollView {
        let scroll = LeafEditorScrollView(); scroll.hasVerticalScroller = false; scroll.drawsBackground = false
        let view = LeafTextView(frame: NSRect(x: 0, y: 0, width: 600, height: 32)); view.isRichText = true; view.importsGraphics = false; view.isAutomaticTextReplacementEnabled = true; view.isContinuousSpellCheckingEnabled = true; view.drawsBackground = false; view.textContainerInset = NSSize(width: 0, height: 1); view.textContainer?.lineFragmentPadding = 0; view.font = .systemFont(ofSize: 17); view.isVerticallyResizable = true; view.isHorizontallyResizable = false; view.autoresizingMask = [.width]; view.textContainer?.widthTracksTextView = true; view.textContainer?.containerSize = NSSize(width: 600, height: CGFloat.greatestFiniteMagnitude); view.minSize = NSSize(width: 0, height: 32); view.maxSize = NSSize(width: CGFloat.greatestFiniteMagnitude, height: CGFloat.greatestFiniteMagnitude); view.allowsUndo = true
        view.textStorage?.setAttributedString(displayed); if note.text.isEmpty { view.typingAttributes = [.font: NSFont.systemFont(ofSize: Typography.size(blockStyle) * note.textScale, weight: blockStyle == "body" ? .regular : .semibold), NSAttributedString.Key("leafTextStyle"): blockStyle, .foregroundColor: (note.style ?? NoteStyle()).ink, NSAttributedString.Key("leafTextScale"): note.textScale] }; view.delegate = context.coordinator; view.images = onImages; view.focused = onFocus; view.enter = onEnter; view.backspace = onBackspace; view.styleChanged = onStyle; view.identifier = blockId.map { NSUserInterfaceItemIdentifier(rawValue: $0) }; view.slash.apply = onCommand; scroll.documentView = view
        return scroll
    }
    func updateNSView(_ scroll: NSScrollView, context: Context) {
        context.coordinator.owner = self
        guard let view = scroll.documentView as? LeafTextView else { return }; view.images = onImages; view.focused = onFocus; view.enter = onEnter; view.backspace = onBackspace; view.styleChanged = onStyle; view.identifier = blockId.map { NSUserInterfaceItemIdentifier(rawValue: $0) }; view.slash.apply = onCommand
        var inkAttributes = view.typingAttributes; inkAttributes[.foregroundColor] = (note.style ?? NoteStyle()).ink; view.typingAttributes = inkAttributes
        if note.text.isEmpty { var attributes = view.typingAttributes; let style = attributes[NSAttributedString.Key("leafTextStyle")] as? String ?? blockStyle; let font = attributes[.font] as? NSFont ?? .systemFont(ofSize: 18); attributes[.font] = NSFontManager.shared.convert(font, toSize: Typography.size(style) * note.textScale); attributes[NSAttributedString.Key("leafTextScale")] = note.textScale; view.typingAttributes = attributes }
        if !view.attributedString().isEqual(to: displayed) {
            let range = view.selectedRange(); context.coordinator.updating = true; view.textStorage?.setAttributedString(displayed); view.setSelectedRange(NSRange(location: min(range.location, view.string.utf16.count), length: 0)); context.coordinator.updating = false
        }
    }
    class Coordinator: NSObject, NSTextViewDelegate {
        var owner: RichEditor; var updating = false
        init(_ owner: RichEditor) { self.owner = owner }
        func textViewDidChangeSelection(_ notification: Notification) {
            guard let view = notification.object as? NSTextView else { return }
            EditorActions.shared.view = view; owner.onFocus?(); DispatchQueue.main.async { (view as? LeafTextView)?.slash.inspect() }
            let index = view.selectedRange().location
            let style = index < view.attributedString().length ? view.attributedString().attribute(NSAttributedString.Key("leafTextStyle"), at: index, effectiveRange: nil) as? String : view.typingAttributes[NSAttributedString.Key("leafTextStyle")] as? String
            let label = (style ?? "body").capitalized
            if EditorActions.shared.styleLabel != label { EditorActions.shared.styleLabel = label }
        }
        func textDidChange(_ notification: Notification) { guard !updating, let view = notification.object as? NSTextView else { return }; owner.onEdit(view.string, spansFrom(view.attributedString())); (view as? LeafTextView)?.slash.inspect() }
    }
}
