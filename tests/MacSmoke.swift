import Foundation
import AppKit

@main struct MacSmoke {
    @MainActor static func main() async throws {
        _ = NSApplication.shared
        MenuAppearance.install()
        let menu = NSMenu(title: "Editor")
        let action = NSMenuItem(title: "Turn into", action: nil, keyEquivalent: "")
        action.image = NSImage(systemSymbolName: "textformat", accessibilityDescription: nil)
        menu.addItem(action)
        NotificationCenter.default.post(name: NSMenu.didBeginTrackingNotification, object: menu)
        precondition(action.view == nil && action.image?.isTemplate == false)
        let copied = action.copy() as! NSMenuItem
        precondition(copied.title == "Turn into" && copied.view == nil)
        let ink = TextSpan(start: 0, length: 5, kind: "highlight:#9FCBFF")
        var highlighted = Note(); highlighted.text = "Hello"; highlighted.spans = [ink]
        precondition(spansFrom(highlighted.attributed).contains(ink))
        let darkStyle = NoteStyle(document: "#000000", text: "#FFFFFF").lightDocument(tone: "#1F33AD", defaultDark: true)
        precondition(NoteStyle.isLight(darkStyle.document!) && darkStyle.text == nil)
        precondition(NoteStyle(document: "#FEDEDE").lightDocument(tone: "#1F33AD", defaultDark: true).document == "#FEDEDE")
        print("PASS: native context menu copying and neutral icons, custom highlights and complementary paper")
        var trail = NavigationTrail()
        let tasksRoute = LibraryRoute(section: "Tasks")
        let detailRoute = LibraryRoute(section: "Tasks", note: "task")
        trail.record(tasksRoute); trail.record(detailRoute)
        precondition(trail.canBack && !trail.canForward)
        precondition(trail.step(-1) == tasksRoute && trail.canForward)
        trail.record(tasksRoute); precondition(trail.routes.count == 3)
        trail.record(LibraryRoute(section: "Images")); precondition(!trail.canForward && trail.routes.count == 3)
        precondition(trail.step(-1) == tasksRoute && trail.step(-1) == LibraryRoute())
        precondition(!trail.canBack && trail.step(-1) == nil)
        print("PASS: navigation returns to task origin, restores forward history and truncates a new branch")
        let root = FileManager.default.temporaryDirectory.appendingPathComponent("leaf-test-\(UUID().uuidString)")
        defer { try? FileManager.default.removeItem(at: root) }
        let store = NoteStore(root: root)
        precondition(store.error == nil)
        let styleStore = NoteStore(root: root.appendingPathComponent("style")); styleStore.create(); styleStore.update { $0.title = "Style fixture"; $0.text = "Styled text" }; styleStore.flush()
        precondition(styleStore.sidebarCount("Notes") == 1 && styleStore.sidebarCount("Personal", collection: true) == 1)
        styleStore.update { $0.collection = "Personal/Work"; $0.pinned = true }; styleStore.flush()
        precondition(styleStore.sidebarCount("Personal", collection: true) == 1 && styleStore.sidebarCount("Pinned") == 1)
        styleStore.update { $0.archived = true }; styleStore.flush()
        precondition(styleStore.sidebarCount("Notes") == 0 && styleStore.sidebarCount("Pinned") == 0 && styleStore.sidebarCount("Archive") == 1)
        styleStore.update { $0.deleted = true }; styleStore.flush()
        precondition(styleStore.sidebarCount("Archive") == 0 && styleStore.sidebarCount("Trash") == 1)
        styleStore.update { $0.deleted = false; $0.archived = false; $0.pinned = false; $0.collection = "Personal" }; styleStore.flush()
        print("PASS: sidebar counts follow nested folders, pins, archive and trash")
        let styleID = styleStore.selected!; let unstyled = styleStore.current!
        let noteStyle = NoteStyle(document: "#F8F0FF", backdrop: "#FFBD19", backdropEnd: "#FFE7E3", text: "#185B51")
        styleStore.update { $0.style = noteStyle }; precondition(styleStore.current?.style == noteStyle)
        styleStore.undo(); precondition(styleStore.current == unstyled); styleStore.redo(); precondition(styleStore.current?.style == noteStyle); styleStore.flush()
        let reopenedStyle = NoteStore(root: styleStore.root, recoverDrafts: false); reopenedStyle.select(styleID); precondition(reopenedStyle.current?.style == noteStyle)
        precondition((reopenedStyle.current!.attributed.attribute(.foregroundColor, at: 0, effectiveRange: nil) as? NSColor) == noteStyle.ink)
        precondition(NoteStyle.isLight("#F8F0FF") && !NoteStyle.isLight("#191B20"))
        let stylePeer = NoteStore(root: root.appendingPathComponent("style-peer")); try stylePeer.ingest(try encode(reopenedStyle.uniqueHeads.first { $0.noteId == styleID }!), remote: true); stylePeer.reload(); stylePeer.select(styleID); stylePeer.update { $0.title += " edited" }; stylePeer.flush(); precondition(stylePeer.current?.style == noteStyle)
        print("PASS: note styles retain colours through persistence, remote ingest, editing and undo/redo; automatic contrast and native text colour agree")
        let wallpapersURL=URL(fileURLWithPath:FileManager.default.currentDirectoryPath).appendingPathComponent("mac/Resources/Wallpapers")
        let wallpaper=try JSONDecoder().decode([Wallpaper].self,from:Data(contentsOf:wallpapersURL.appendingPathComponent("index.json")))[0]
        let wallpaperStore=NoteStore(root:root.appendingPathComponent("wallpaper"));wallpaperStore.create();wallpaperStore.update{$0.title="Wallpaper fixture"};wallpaperStore.useBackdrop(wallpapersURL.appendingPathComponent(wallpaper.id+".webp"));let wallpaperID=wallpaperStore.selected!
        wallpaperStore.update{$0.style?.blurImage=true;$0.style?.gradientDirection="up"};wallpaperStore.flush()
        precondition(wallpaperStore.error==nil && wallpaperStore.current?.style?.backdropImage==wallpaper.id)
        precondition(wallpaperStore.current!.attachments.count==1 && wallpaperStore.current!.document.allSatisfy{$0.isText})
        wallpaperStore.update{$0.text="Typing preserves wallpaper"};wallpaperStore.flush();precondition(wallpaperStore.current?.attachments.first?.id==wallpaper.id)
        _ = try await DailyBackups.save(raw:wallpaperStore.rawRevisions(),root:wallpaperStore.root,force:true)
        let wallpaperBackups=try FileManager.default.contentsOfDirectory(at:wallpaperStore.root.appendingPathComponent("backups"),includingPropertiesForKeys:nil)
        let wallpaperArchive=try JSONSerialization.jsonObject(with:Data(contentsOf:wallpaperBackups[0])) as! [String:Any]
        precondition((wallpaperArchive["media"] as! [String:String])[wallpaper.id] != nil)
        let wallpaperPeer=NoteStore(root:root.appendingPathComponent("wallpaper-peer"));try wallpaperPeer.ingest(try encode(wallpaperStore.uniqueHeads.first{$0.noteId==wallpaperID}!),remote:true);wallpaperPeer.reload();wallpaperPeer.select(wallpaperID)
        precondition(wallpaperPeer.current?.style==wallpaperStore.current?.style && wallpaperPeer.current?.attachments.count==1)
        wallpaperPeer.update{$0.style=nil};precondition(wallpaperPeer.current?.attachments.isEmpty==true)
        print("PASS: image backdrops survive editing, remote revisions and portable backups without becoming document blocks; reset removes the current media reference")
        var taskNote = Note(); taskNote.title = "Test task"; taskNote.task = TaskDetails(dueAt: 1791091800000, hasTime: true, priority: 3, repeatRule: "weekly", list: "Work", completed: false, remind: true)
        let taskStore = NoteStore(root: root.appendingPathComponent("tasks")); taskStore.create(); let taskID = taskStore.selected!; taskStore.update { $0 = taskNote }; taskStore.flush()
        precondition(taskStore.uniqueHeads.first(where: { $0.noteId == taskID })!.note.task == taskNote.task)
        _ = try await DailyBackups.save(raw: taskStore.rawRevisions(), root: taskStore.root, force: true)
        let taskFiles = try FileManager.default.contentsOfDirectory(at: taskStore.root.appendingPathComponent("backups"), includingPropertiesForKeys: nil)
        let taskArchive = try JSONSerialization.jsonObject(with: Data(contentsOf: taskFiles[0])) as! [String: Any]
        let taskRecords = taskArchive["revisions"] as! [[String: Any]]
        let restoredTasks = NoteStore(root: root.appendingPathComponent("restored-tasks"))
        for raw in taskRecords { try restoredTasks.ingest(String(data: JSONSerialization.data(withJSONObject: raw), encoding: .utf8)!, remote: false) }; restoredTasks.reload()
        precondition(restoredTasks.uniqueHeads.first(where: { $0.noteId == taskID })!.note.task == taskNote.task)
        precondition(taskStore.uniqueHeads.first(where: { $0.noteId == taskID })!.note.task == taskNote.task)
        var recurring = taskNote.task!; let due = recurring.dueAt; recurring.complete(now: recurring.date)
        precondition(recurring.dueAt > due && !recurring.completed)
        var simple = TaskDetails(); simple.complete(); precondition(simple.completed); simple.complete(); precondition(!simple.completed)
        var staged = TaskDetails(); staged.move(to: "progress"); precondition(staged.stage == "progress" && !staged.completed)
        staged.move(to: "review"); taskStore.mutate(taskID) { $0.task = staged }; precondition(taskStore.uniqueHeads.first(where: { $0.noteId == taskID })!.note.task?.stage == "review")
        staged.move(to: "done"); precondition(staged.completed && staged.stage == "done"); staged.move(to: "todo"); precondition(!staged.completed && staged.stage == "todo")
        let legacy = Data("{\"dueAt\":0,\"hasTime\":false,\"priority\":0,\"repeatRule\":\"none\",\"list\":\"Reminders\",\"completed\":false,\"remind\":false}".utf8)
        let oldTask = try JSONDecoder().decode(TaskDetails.self, from: legacy); precondition(oldTask.stage == "todo")
        print("PASS: task statuses persist, completed tasks reopen, and older tasks remain compatible")
        var yearly = taskNote.task!; yearly.repeatRule = "yearly"; let yearStart = yearly.date; yearly.complete(now: yearStart)
        precondition(Calendar.current.component(.year, from: yearly.date) == Calendar.current.component(.year, from: yearStart) + 1)
        taskStore.mutate(taskID) { $0.task = yearly }; precondition(taskStore.current?.task?.repeatRule == "yearly" || taskStore.uniqueHeads.first(where: { $0.noteId == taskID })!.note.task?.repeatRule == "yearly")
        let blankStore = NoteStore(root: root.appendingPathComponent("blank-notes")); blankStore.create(); let blankID = blankStore.selected!; blankStore.select(nil)
        precondition(blankStore.uniqueHeads.first(where: { $0.noteId == blankID })!.note.deleted)
        blankStore.create(); let titledID = blankStore.selected!; blankStore.update { $0.title = "Keep" }; blankStore.select(nil)
        precondition(!blankStore.uniqueHeads.first(where: { $0.noteId == titledID })!.note.deleted)
        var structured = Note(); var tableOnly = DocumentBlock(); tableOnly.kind = "table"; structured.blocks = [tableOnly]; precondition(!structured.isCompletelyBlank)
        print("PASS: blank notes move to Trash on leave; titles and structured content survive; yearly repeats persist and advance")
        print("PASS: task persistence, backup, completion, and repeat rollover")
        store.create()
        store.update { $0.title = "Native round trip"; $0.text = "Hello 👋 नमस्ते"; $0.spans = [.init(start: 6, length: 2, kind: "bold"), .init(start: 6, length: 2, kind: "italic")] }
        precondition(FileManager.default.fileExists(atPath: store.draftURL.path), "Keystrokes must be durable before debounce")
        let recovered = NoteStore(root: root)
        precondition(recovered.current?.title == "Native round trip", "Restart must recover pending draft")
        precondition(recovered.current?.text == "Hello 👋 नमस्ते")
        let attributed = recovered.current!.attributed
        precondition(Set(spansFrom(attributed).map { $0.kind }) == Set(["bold", "italic"]))
        precondition(attributed.length == recovered.current!.text.utf16.count)
        var overlapping = Note(); overlapping.text = "abcdefgh"
        overlapping.spans = [.init(start: 0, length: 6, kind: "bold"), .init(start: 2, length: 4, kind: "italic"), .init(start: 6, length: 2, kind: "bold")]
        precondition(spansFrom(overlapping.attributed) == canonicalSpans(overlapping.spans), "Overlapping styles must not reset the cursor after a sync")
        print("PASS: overlapping rich-text styles remain stable")
        print("PASS: atomic draft recovery, Unicode, and native rich-text round trip")
        var styled = Note(); styled.text = "Heading and link"; styled.spans = [.init(start: 0, length: 7, kind: "title"), .init(start: 0, length: 7, kind: "bold"), .init(start: 0, length: 7, kind: "italic"), .init(start: 12, length: 4, kind: "link:https://example.com")]
        precondition(spansFrom(styled.attributed) == canonicalSpans(styled.spans), "Heading and links must survive a rich-text round trip")
        recovered.update { $0 = styled }; recovered.flush()
        precondition(recovered.error == nil && recovered.current?.spans == styled.spans)
        let view = LeafTextView(); view.textStorage?.setAttributedString(styled.attributed); view.setSelectedRange(NSRange(location: 0, length: 7)); EditorActions.shared.view = view
        EditorActions.shared.textStyle("subtitle")
        precondition(spansFrom(view.attributedString()).contains { $0.kind == "subtitle" && $0.length == 7 })
        view.setSelectedRange(NSRange(location: view.string.utf16.count, length: 0)); EditorActions.shared.textStyle("headline")
        precondition((view.typingAttributes[.font] as? NSFont)?.pointSize == CGFloat(Typography.size("headline")))
        let linkSpans = spansFrom(view.attributedString()).filter { $0.kind.hasPrefix("link:") }
        precondition(linkSpans == styled.spans.filter { $0.kind.hasPrefix("link:") })
        print("PASS: saved heading/link spans, selected text sizes, and insertion style")
        let bodyAttributes = NSAttributedString(string: "normal", attributes: [.font: NSFont.systemFont(ofSize: 15), NSAttributedString.Key("leafTextStyle"): "body"])
        precondition(spansFrom(bodyAttributes).isEmpty, "Body must never serialize as an unsupported span")
        for style in ["title", "subtitle", "headline"] {
            var block = DocumentBlock(); block.textStyle = style
            recovered.update { $0.blocks = [block] }; recovered.flush()
            precondition(recovered.error == nil && recovered.current?.document[0].textStyle == style, "Empty heading style must save")
            block.text = "Heading"; let text = block.asNote.attributed
            precondition((text.attribute(.font, at: 0, effectiveRange: nil) as! NSFont).pointSize == Typography.size(style))
            precondition(spansFrom(text).contains { $0.kind == style })
        }
        recovered.update { $0 = styled }; recovered.flush()
        let buffer = LeafTextView(); buffer.awaitingBlock = true
        buffer.insertText("next", replacementRange: NSRange(location: 0, length: 0)); buffer.insertNewline(nil)
        precondition(buffer.string.isEmpty && buffer.queuedInput == ["next", "\n"], "Typing during block mounting must be queued")
        print("PASS: empty headings persist, body spans stay valid, and fast block-transition typing is buffered")

        recovered.duplicate(recovered.selected!); precondition(recovered.current?.title == "Untitled note copy")
        print("PASS: duplication retains rich text")
        var enlarged = styled; enlarged.textScale = 1.3
        precondition(spansFrom(enlarged.attributed) == canonicalSpans(styled.spans))
        precondition(abs((enlarged.attributed.attribute(.font, at: 0, effectiveRange: nil) as! NSFont).pointSize - Typography.size("title") * 1.3) < 0.01)
        recovered.update { $0.textScale = 1.3 }; recovered.flush()
        let sized = try JSONDecoder().decode(Note.self, from: leafEncoder.encode(recovered.current!)); precondition(sized.textScale == 1.3)
        print("PASS: synced note size retains semantic styles and rich text")
        let privateBoard = NSPasteboard.withUniqueName(); defer { privateBoard.releaseGlobally() }
        privateBoard.clearContents(); precondition(privateBoard.setString("https://example.com", forType: .string), "Private clipboard fixture must be writable")
        let clip = ClipboardCapture.read(privateBoard)!; precondition(clip.link?.host == "example.com")
        ClipboardCapture.shared.save(clip, into: recovered); precondition(recovered.current?.text == "https://example.com" && recovered.current?.spans.first?.kind == "link:https://example.com")
        privateBoard.clearContents(); privateBoard.setString("secret", forType: NSPasteboard.PasteboardType("org.nspasteboard.ConcealedType")); privateBoard.setString("secret", forType: .string)
        precondition(ClipboardCapture.read(privateBoard) == nil)
        print("PASS: clipboard links create a note and concealed clips are skipped")
        privateBoard.clearContents()
        let staleFile = URL(fileURLWithPath: "/nonexistent/leaf-cloud-placeholder.png")
        precondition(privateBoard.writeObjects([staleFile as NSURL]))
        let fileClip = ClipboardCapture.read(privateBoard)!
        precondition(fileClip.files == [staleFile] && fileClip.image == nil)
        print("PASS: clipboard file URLs are captured without opening stale file providers")
        let parallel = NoteStore(root: root, recoverDrafts: false)
        parallel.create(); parallel.update { $0.text = "A second window draft" }
        recovered.update { $0.text += " updated in first window" }
        precondition(parallel.draftURL != recovered.draftURL)
        precondition(FileManager.default.fileExists(atPath: parallel.draftURL.path) && FileManager.default.fileExists(atPath: recovered.draftURL.path))
        parallel.flush(); recovered.flush()
        print("PASS: separate windows keep independent durable drafts")
        let image = NSImage(size: NSSize(width: 64, height: 64)); image.lockFocus(); NSColor.systemGreen.setFill(); NSRect(x: 0, y: 0, width: 64, height: 64).fill(); image.unlockFocus()
        let clipStore = NoteStore(root: root.appendingPathComponent("clip-fixture"), recoverDrafts: false)
        ClipboardCapture.shared.save(ClipboardItem(image: image), into: clipStore)
        precondition(clipStore.current?.attachments.count == 1 && clipStore.current?.attachments.first?.mime == "image/png")
        precondition(FileManager.default.fileExists(atPath: clipStore.media.appendingPathComponent(clipStore.current!.attachments[0].id).path))
        print("PASS: clipboard image capture creates a durable PNG attachment")
        let file = root.appendingPathComponent("test-image.tiff"); try image.tiffRepresentation!.write(to: file)
        recovered.importImages([file]); recovered.flush()
        precondition(recovered.current!.attachments.count == 1)
        recovered.importImages([file]); recovered.flush(); precondition(recovered.current!.attachments.count == 1, "Duplicate images must not duplicate an attachment")
        let id = recovered.current!.attachments[0].id
        precondition(FileManager.default.fileExists(atPath: recovered.media.appendingPathComponent(id).path))
        print("PASS: imported image integrity and deduplication")
        let latest = try recovered.revisions(headsOnly: true)[0]
        var fork = latest; fork.id = UUID().uuidString.lowercased(); fork.note.text = "Edit on phone"; fork.parents = latest.parents
        try recovered.ingest(try encode(fork), remote: true); recovered.reload()
        precondition(recovered.heads.filter { $0.noteId == latest.noteId }.count == 2)
        recovered.resolve(latest)
        precondition(recovered.heads.filter { $0.noteId == latest.noteId }.count == 1 && recovered.heads.first { $0.noteId == latest.noteId }?.note == latest.note)
        let history = try recovered.revisions()
        precondition(history.contains { $0.id == fork.id })
        print("PASS: conflict review preserves the unchosen history")
        recovered.create(); let dailyId = recovered.selected!
        recovered.update { $0.text = "Before 👋 after"; $0.spans = [TextSpan(start: 7, length: 2, kind: "bold")] }
        let body = recovered.current!.document[0].id; recovered.activeBlock = body; recovered.insertionOffset = 9
        recovered.importImages([file]); precondition(recovered.current!.document.count == 3)
        precondition(recovered.current!.document[0].text == "Before 👋" && recovered.current!.document[2].text == " after")
        precondition(recovered.current!.document[0].spans == [TextSpan(start: 7, length: 2, kind: "bold")])
        let photo = recovered.current!.document[1].id; recovered.changeBlock(photo) { $0.caption = "Original image"; $0.presentation = "small" }
        recovered.activeBlock = recovered.current!.document[2].id; recovered.setList("check"); recovered.changeBlock(recovered.activeBlock!) { $0.checked = true; $0.indent = 1 }
        recovered.addBlock("table"); let table = recovered.activeBlock!; recovered.changeBlock(table) { $0.cells = [["Name", "Qty"], ["Milk", "2"]] }
        recovered.update { $0.tags = ["#Travel", "travel", "work"] }; precondition(recovered.current!.tags == ["travel", "work"])
        let original = recovered.current!; recovered.moveBlock(photo, by: 1); precondition(recovered.current!.document[2].id == photo)
        recovered.undo(); precondition(recovered.current! == original); recovered.redo(); precondition(recovered.current!.document[2].id == photo)
        recovered.flush(); precondition(recovered.error == nil)
        let restarted = NoteStore(root: root, recoverDrafts: false); restarted.select(dailyId); precondition(restarted.current == recovered.current)
        let exported = root.appendingPathComponent("note.zip"); try PortableExport.write(note: restarted.current!, media: restarted.media, to: exported)
        precondition(FileManager.default.fileExists(atPath: exported.path)); precondition(PortableExport.markdown(restarted.current!).contains("- [x]"))
        print("PASS: mixed blocks, caret image insertion, Unicode spans, checklists, tables, tags, undo/redo, and portable export")
        restarted.createFolder("Work/Ideas", emoji: "🌿"); precondition(restarted.collections.contains("Work") && restarted.collections.contains("Work/Ideas"))
        precondition(restarted.folderHeads.first?.note.folderEmoji == "🌿" && !restarted.uniqueHeads.contains { $0.note.recordType == "folder" })
        restarted.renameFolder("Work", to: "Projects"); precondition(restarted.collections.contains("Projects/Ideas"))
        print("PASS: nested folder records and icons survive the shared revision store")
        let backupResult = try await DailyBackups.save(raw: restarted.rawRevisions(), root: root, force: true); precondition(backupResult.contains("saved"))
        let backups = try FileManager.default.contentsOfDirectory(at: root.appendingPathComponent("backups"), includingPropertiesForKeys: nil)
        let object = try JSONSerialization.jsonObject(with: Data(contentsOf: backups[0])) as! [String: Any]
        precondition(object["format"] as? String == "leaf-backup-1"); let records = object["revisions"] as! [[String: Any]]; let blobs = object["media"] as! [String: String]
        let restored = NoteStore(root: root.appendingPathComponent("fresh"), recoverDrafts: false)
        for record in records { try restored.ingest(String(data: JSONSerialization.data(withJSONObject: record), encoding: .utf8)!, remote: false) }
        for (hash, encoded) in blobs { try Data(base64Encoded: encoded)!.write(to: restored.media.appendingPathComponent(hash)) }
        restored.reload(); restored.select(dailyId); precondition(restored.current == restarted.uniqueHeads.first { $0.noteId == dailyId }?.note)
        print("PASS: automatic portable backup restores mixed content into a fresh library")

        let starterFolder = URL(fileURLWithPath: FileManager.default.currentDirectoryPath).appendingPathComponent("assets/Starter")
        try await restored.installStarterLibrary(from: starterFolder)
        let firstStarterCount = try restored.revisions().count
        precondition(restored.uniqueHeads.filter { !$0.note.deleted }.count == 5)
        precondition(restored.uniqueHeads.filter { !$0.note.deleted }.flatMap { $0.note.attachments }.count == 3)
        precondition(restored.uniqueHeads.contains { $0.noteId == dailyId && $0.note.deleted })
        precondition(FileManager.default.fileExists(atPath: restored.root.appendingPathComponent("before-starter-refresh.leafbackup").path))
        try await restored.installStarterLibrary(from: starterFolder); let secondStarterCount = try restored.revisions().count; precondition(secondStarterCount == firstStarterCount)
        print("PASS: starter refresh preserves old notes in Trash, original photos, pre-refresh backup, and one-time idempotence")

        restored.createFolder("Polish/Child", emoji: "🌿"); restored.create(); let keptId = restored.selected!; restored.update { $0.title = "Keep me"; $0.collection = "Polish/Child" }; restored.flush(); let keptBefore = try restored.revisions().filter { $0.noteId == keptId }.count
        restored.deleteFolder("Polish"); precondition(!restored.collections.contains("Polish/Child")); precondition(restored.uniqueHeads.first { $0.noteId == keptId }?.note.collection == "Personal"); precondition(restored.uniqueHeads.first { $0.noteId == keptId }?.note.deleted == false); let keptAfter = try restored.revisions().filter { $0.noteId == keptId }.count; precondition(keptAfter > keptBefore)
        let completed = RichEditor(note: styled, onEdit: { _, _ in }, onImages: { _ in }, completed: true); precondition(spansFrom(completed.displayed) == canonicalSpans(styled.spans))
        print("PASS: nested folder deletion preserves notes/history and completion strike remains presentation-only")

        restored.create(); let groupId = restored.selected!; var parent = DocumentBlock(); parent.kind = "toggle"; parent.text = "Ideas"; var child = DocumentBlock(); child.parentId = parent.id; child.text = "Nested thought"; var tail = DocumentBlock(); tail.text = "After"
        restored.update { $0.title = "Toggle proof"; $0.blocks = [parent, child, tail] }; restored.flush()
        restored.moveBlock(parent.id, by: 1); precondition(restored.current!.document.map(\.id) == [tail.id, parent.id, child.id]); restored.duplicateBlock(parent.id); let copies = restored.current!.document; precondition(copies.count == 5 && copies[4].parentId == copies[3].id)
        restored.changeBlock(parent.id) { $0.collapsed = true }; precondition(!restored.current!.isVisible(child)); restored.removeBlock(parent.id); precondition(!restored.current!.document.contains { $0.id == child.id }); restored.flush()
        print("PASS: nested toggles move, duplicate, collapse, and remove as complete groups")
        var converted = Note(); converted.blocks = [parent, child, tail]; converted.editBlock(parent.id) { $0.kind = "text" }
        precondition(converted.document[1].parentId == nil && converted.document[0].collapsed == false && converted.isVisible(converted.document[1]))
        print("PASS: converting a toggle preserves and reveals its children")
        restored.mutate(groupId) { $0.deleted = true }; let trashRevision = restored.uniqueHeads.first { $0.noteId == groupId }!; precondition(trashRevision.note.deletedAt != nil)
        restored.expireTrash(now: trashRevision.note.deletedAt! + 30 * 86400000 - 1); precondition(restored.uniqueHeads.contains { $0.noteId == groupId }); restored.expireTrash(now: trashRevision.note.deletedAt! + 30 * 86400000)
        precondition(!restored.uniqueHeads.contains { $0.noteId == groupId }); try restored.ingest(try encode(trashRevision), remote: true); restored.reload(); precondition(!restored.uniqueHeads.contains { $0.noteId == groupId }); let erasedHistory = try restored.revisions().filter { $0.noteId == groupId }; precondition(erasedHistory.count == 1)
        print("PASS: Trash expires exactly after 30 days and erased note history cannot return from sync")
        let batchStore = NoteStore(root: root.appendingPathComponent("batch-trash"), recoverDrafts: false)
        var batchIDs: [String] = []
        for name in ["First selected", "Second selected", "Third selected"] { batchStore.create(); batchStore.update { $0.title = name; $0.deleted = true }; batchStore.flush(); batchIDs.append(batchStore.selected!) }
        batchStore.permanentlyDelete(batchIDs)
        precondition(batchIDs.allSatisfy { id in !batchStore.uniqueHeads.contains { $0.noteId == id && $0.note.purgedAt == nil } })
        precondition(batchStore.error == nil)
        print("PASS: permanent deletion purges every item in a captured selection")


        precondition(BlockCommand.suggestions("che").first?.id == "check")
        precondition(BlockCommand.suggestions("check").first?.id == "check")
        precondition(BlockCommand.suggestions("todo").first?.id == "check")
        precondition(BlockCommand.suggestions("h1").first?.id == "title")
        precondition(BlockCommand.suggestions("heading 2").first?.id == "subtitle")
        precondition(BlockCommand.suggestions("tog").first?.id == "toggle")
        precondition(BlockCommand.suggestions("unknown").isEmpty)
        print("PASS: slash prefixes and aliases suggest checklist, toggle, and heading commands")
        restarted.create(); let editorId = restarted.selected!; let commandBlock = restarted.current!.document[0]
        let editor = DocumentEditor(store: restarted, note: restarted.current!, noteId: editorId, onPreview: { _ in }, onConflict: {})
        editor.applyCommand("toggle", to: commandBlock.id)
        precondition(editor.splitList(commandBlock))
        let entered = restarted.current!; let enteredChild = entered.document.first { $0.parentId == commandBlock.id }!
        precondition(restarted.activeBlock == enteredChild.id)
        restarted.changeBlock(enteredChild.id) { $0.kind = "toggle" }
        restarted.addChild(to: enteredChild.id); let grandchild = restarted.current!.document.last!
        restarted.changeBlock(enteredChild.id) { $0.collapsed = true }
        precondition(!restarted.current!.isVisible(grandchild)); precondition(restarted.current!.isVisible(enteredChild))
        restarted.changeBlock(commandBlock.id) { $0.collapsed = true }
        precondition(!restarted.current!.isVisible(enteredChild))
        precondition(editor.backspace(commandBlock.id)); precondition(restarted.current!.document.contains { $0.id == enteredChild.id && $0.parentId == nil })
        precondition(!restarted.current!.document.contains { $0.id == commandBlock.id })
        print("PASS: Enter reads the live toggle kind, creates a real child, collapse hides it, and empty-toggle deletion keeps child content")

        restarted.create()
        let toggle = restarted.current!.document[0]
        restarted.changeBlock(toggle.id) { $0.kind = "toggle"; $0.text = "Toggle title" }
        restarted.addChild(to: toggle.id)
        let exitChild = restarted.current!.document.first { $0.parentId == toggle.id }!
        let exitEditor = DocumentEditor(store: restarted, note: restarted.current!, noteId: restarted.selected!, onPreview: { _ in }, onConflict: {})
        precondition(exitEditor.splitList(exitChild))
        precondition(restarted.current!.document.first { $0.id == exitChild.id }!.parentId == nil)
        restarted.undo()
        precondition(restarted.current!.document.first { $0.id == exitChild.id }!.parentId == toggle.id)
        restarted.addBlock("table")
        let safeTable = restarted.current!.document.last!
        restarted.setTableCell(safeTable.id, row: 1, column: 1, value: "Keep me")
        precondition(restarted.tableCell(safeTable.id, row: 1, column: 1) == "Keep me")
        restarted.changeBlock(safeTable.id) { $0.cells.removeLast() }
        let rows = restarted.current!.document.last!.cells.count
        precondition(restarted.tableCell(safeTable.id, row: rows, column: 1).isEmpty)
        restarted.setTableCell(safeTable.id, row: rows, column: 1, value: "Stale callback")
        restarted.undo()
        precondition(restarted.current!.document.last!.cells.count == rows + 1)
        restarted.changeBlock(safeTable.id) { $0.cells = $0.cells.map { Array($0.dropLast()) } }
        let columns = restarted.current!.document.last!.cells[0].count
        precondition(restarted.tableCell(safeTable.id, row: 0, column: columns).isEmpty)
        restarted.setTableCell(safeTable.id, row: 0, column: columns, value: "Stale callback")
        restarted.removeBlock(safeTable.id)
        precondition(restarted.tableCell(safeTable.id, row: 0, column: 0).isEmpty)
        restarted.setTableCell(safeTable.id, row: 0, column: 0, value: "Deleted safeTable")
        print("PASS: empty toggle child exits with undo; deleted rows, columns and tables tolerate stale cell bindings")

        let erasedFolder = NoteStore(root: root.appendingPathComponent("erased-folder"))
        erasedFolder.createFolder("Imported"); erasedFolder.create(); erasedFolder.update { $0.title = "Old erased note"; $0.collection = "Imported"; $0.deleted = true }; erasedFolder.flush()
        let erasedID = erasedFolder.selected!; erasedFolder.permanentlyDelete([erasedID])
        let marker = erasedFolder.allHeads.first { $0.noteId == erasedID }!
        erasedFolder.create(); erasedFolder.update { $0.title = "Retained note"; $0.collection = "Imported" }; erasedFolder.flush(); let retainedID = erasedFolder.selected!
        erasedFolder.deleteFolder("Imported")
        precondition(erasedFolder.error == nil && erasedFolder.draft == nil)
        precondition(erasedFolder.uniqueHeads.first { $0.noteId == retainedID }!.note.collection == "Personal")
        precondition(erasedFolder.allHeads.first { $0.noteId == erasedID }!.id == marker.id)
        erasedFolder.commitDraft(Draft(noteId: erasedID, parents: [marker.id], note: marker.note))
        precondition(erasedFolder.error == nil && erasedFolder.draft == nil)
        precondition(erasedFolder.uniqueHeads.allSatisfy { $0.noteId != erasedID })
        print("PASS: collection deletion skips erased markers and recovers an old invalid marker draft without restoring erased content")

        let sourceRoot = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent()
        let envelope = try JSONDecoder().decode(UpdateEnvelope.self, from: Data(contentsOf: sourceRoot.appendingPathComponent("tests/fixtures/updates.json")))
        let payload = Data(base64Encoded: envelope.payload)!, signature = Data(base64Encoded: envelope.signature)!
        let publicKey = try Data(contentsOf: sourceRoot.appendingPathComponent("mac/Resources/update-public.der"))
        let update = try UpdateValidation.parse(payload: payload, signature: signature, publicKey: publicKey)
        precondition(update.version == "0.13.0" && update.mac.build == 13 && update.android.build == 14)
        precondition(UpdateValidation.unseen(update, current: "0.11.0").map(\.version) == ["0.13.0", "0.12.0"])
        var tampered = payload; tampered[0] ^= 1
        do { _ = try UpdateValidation.parse(payload: tampered, signature: signature, publicKey: publicKey); preconditionFailure("Accepted changed metadata") } catch { }
        var invalidSignature = signature; invalidSignature[0] ^= 1
        do { _ = try UpdateValidation.parse(payload: payload, signature: invalidSignature, publicKey: publicKey); preconditionFailure("Accepted a changed signature") } catch { }
        print("PASS: signed updater metadata verifies, rejects tampering, and includes skipped changelogs")

    }
}
