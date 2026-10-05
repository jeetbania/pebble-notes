import SwiftUI
import CryptoKit
import UniformTypeIdentifiers

@MainActor final class NoteStore: ObservableObject {
    @Published var heads: [Revision] = []
    @Published var selected: String?
    @Published var draft: Draft?
    @Published var status = "Saved on this Mac"
    @Published var error: String?
    @Published var busy = false
    @Published var canUndo = false
    @Published var canRedo = false
    @Published var backupStatus = "Automatic local backups enabled"
    var allowsAutomaticSync: Bool { automaticBackups }
    var activeBlock: String? { willSet { if newValue != activeBlock { objectWillChange.send() } } }
    var insertionOffset: Int?
    private var undoNotes: [Note] = []
    private var redoNotes: [Note] = []
    private var lastUndoKey = ""
    private var lastUndoTime = Date.distantPast
    private let automaticBackups: Bool
    let root: URL
    let media: URL
    private let draftFileName = "pending-draft-" + UUID().uuidString.lowercased() + ".json"
    let deviceId: String
    private var db: OpaquePointer?
    private var pendingCommit: Task<Void, Never>?
    var current: Note? { draft?.note ?? allHeads.first(where: { $0.noteId == selected })?.note }
    var uniqueHeads: [Revision] { allHeads.filter { $0.note.recordType == "note" && $0.note.purgedAt == nil } }
    func sidebarCount(_ section: String, collection: Bool = false) -> Int {
        uniqueHeads.filter { r in
            let n = r.note
            if section == "Trash" && !collection { return n.deleted }
            if section == "Archive" && !collection { return n.archived && !n.deleted }
            guard !n.deleted && !n.archived else { return false }
            if section == "Tasks" && !collection { return n.task != nil }
            if section.hasPrefix("#") && !collection { return n.tags.contains(String(section.dropFirst())) }
            guard n.task == nil else { return false }
            if collection { return n.collection == section || n.collection.hasPrefix(section + "/") }
            switch section {
            case "Pinned": return n.pinned
            case "Images": return n.attachments.contains { $0.mime.hasPrefix("image/") }
            case "Checklists": return n.document.contains { $0.kind == "check" }
            default: return true
            }
        }.count
    }
    var folderHeads: [Revision] { allHeads.filter { $0.note.recordType == "folder" && !$0.note.deleted } }
    var collections: [String] {
        Array(Set(allHeads.filter { !$0.note.deleted }.flatMap { r in let parts = r.note.collection.split(separator: "/"); return parts.indices.map { parts.prefix($0 + 1).joined(separator: "/") } } + ["Personal"])).sorted()
    }
    var allHeads: [Revision] {
        var seen = Set<String>(); return heads.filter { seen.insert($0.noteId).inserted }.map { first in heads.first(where: { $0.noteId == first.noteId && !$0.note.deleted }) ?? first }
    }
    init(root custom: URL? = nil, recoverDrafts: Bool = true) {
        automaticBackups = custom == nil && recoverDrafts
        root = custom ?? FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("LeafNotes", isDirectory: true)
        media = root.appendingPathComponent("media", isDirectory: true)
        deviceId = UserDefaults.standard.string(forKey: "deviceId") ?? UUID().uuidString.lowercased()
        UserDefaults.standard.set(deviceId, forKey: "deviceId")
        do {
            try FileManager.default.createDirectory(at: media, withIntermediateDirectories: true)
            db = leaf_open(root.appendingPathComponent("notes.sqlite").path)
            guard db != nil else { throw LeafError.message("Could not open the note library") }
            reload()
            if recoverDrafts {
                let pending = try FileManager.default.contentsOfDirectory(at: root, includingPropertiesForKeys: nil).filter { $0.lastPathComponent == "pending-draft.json" || ($0.lastPathComponent.hasPrefix("pending-draft-") && $0.pathExtension == "json") }
                for file in pending {
                    draft = try JSONDecoder().decode(Draft.self, from: Data(contentsOf: file)); selected = draft?.noteId; flush()
                    if draft == nil { try? FileManager.default.removeItem(at: file) } else { break }
                }
            }
            if automaticBackups { scheduleBackup(); if let folder = Bundle.main.resourceURL?.appendingPathComponent("Starter"), FileManager.default.fileExists(atPath: folder.appendingPathComponent("library.json").path) { Task { do { try await installStarterLibrary(from: folder) } catch { self.error = error.localizedDescription } } } }
        } catch { self.error = error.localizedDescription }
    }
    var draftURL: URL { root.appendingPathComponent(draftFileName) }
    func revisions(pending: Bool = false, headsOnly: Bool = false) throws -> [Revision] {
        guard let db, let ptr = leaf_list(db, pending ? 1 : 0, headsOnly ? 1 : 0) else { throw LeafError.message("Could not read note library") }
        defer { leaf_free(ptr) }
        return try JSONDecoder().decode([Revision].self, from: Data(String(cString: ptr).utf8))
    }
    func rawRevisions() throws -> String {
        guard let db, let ptr = leaf_list(db, 0, 0) else { throw LeafError.message("Could not read revisions") }
        defer { leaf_free(ptr) }; return String(cString: ptr)
    }
    func ingest(_ raw: String, remote: Bool) throws {
        guard let db, leaf_put(db, raw, remote ? 1 : 0) != 0 else { throw LeafError.message(String(cString: leaf_error(db))) }
    }
    func acknowledge(_ id: String) throws {
        guard let db, leaf_ack(db, id) != 0 else { throw LeafError.message("Could not record sync progress") }
    }
    func reload() { do { heads = try revisions(headsOnly: true); expireTrash(); if automaticBackups { TaskAlerts.refresh(uniqueHeads) } } catch { self.error = error.localizedDescription } }
    func select(_ id: String?) { if selected != id { discardBlankOnLeave() }; flush(); if draft == nil { selected = id; undoNotes = []; redoNotes = []; canUndo = false; canRedo = false; activeBlock = nil; insertionOffset = nil } }
    func discardBlankOnLeave() {
        if let note = current, note.isCompletelyBlank, !note.deleted { update(remember: false) { $0.deleted = true }; flush() }
    }
    func create() {
        discardBlankOnLeave(); flush(); guard draft == nil else { return }
        selected = UUID().uuidString.lowercased()
        var note = Note(); note.prepare(); undoNotes = []; redoNotes = []; canUndo = false; canRedo = false; activeBlock = nil; insertionOffset = nil; draft = Draft(noteId: selected!, parents: [], note: note); persistDraft(); flush()
    }
    func update(undoKey: String = "", remember: Bool = true, _ transform: (inout Note) -> Void) {
        guard let selected, var note = current else { return }
        let original = note; transform(&note); note.prepare(previous: original); guard note != original else { return }
        if remember {
            if undoKey.isEmpty || undoKey != lastUndoKey || Date().timeIntervalSince(lastUndoTime) > 0.8 { undoNotes.append(original); if undoNotes.count > 100 { undoNotes.removeFirst() } }
            redoNotes = []; lastUndoKey = undoKey; lastUndoTime = Date(); canUndo = !undoNotes.isEmpty; canRedo = false
        }
        let parents = draft?.parents ?? allHeads.first(where: { $0.noteId == selected }).map { [$0.id] } ?? []
        draft = Draft(noteId: selected, parents: parents, note: note)
        persistDraft(); pendingCommit?.cancel()
        pendingCommit = Task { try? await Task.sleep(for: .milliseconds(700)); guard !Task.isCancelled else { return }; flush() }
    }
    func commitDraft(_ value: Draft) { draft = value; persistDraft(); flush() }
    func undo() { guard let value = undoNotes.popLast(), let current else { return }; redoNotes.append(current); update(remember: false) { $0 = value }; canUndo = !undoNotes.isEmpty; canRedo = true; lastUndoKey = "" }
    func redo() { guard let value = redoNotes.popLast(), let current else { return }; undoNotes.append(current); update(remember: false) { $0 = value }; canUndo = true; canRedo = !redoNotes.isEmpty; lastUndoKey = "" }
    private func persistDraft() {
        do { try leafEncoder.encode(draft).write(to: draftURL, options: .atomic); status = "Saved on this Mac" }
        catch { self.error = "Local saving failed: \(error.localizedDescription)"; status = "Save needs attention" }
    }
    func flush() {
        pendingCommit?.cancel()
        guard let draft else { return }
        do {
            var note = draft.note; note.prepare()
            let revision = Revision(id: UUID().uuidString.lowercased(), noteId: draft.noteId, deviceId: deviceId, parents: draft.parents, createdAt: Int64(Date().timeIntervalSince1970 * 1000), note: note)
            try ingest(try encode(revision), remote: false)
            try? FileManager.default.removeItem(at: draftURL)
            self.draft = nil; reload(); if automaticBackups { scheduleBackup() }
        } catch { self.error = error.localizedDescription }
    }
    func resolve(_ revision: Revision) {
        flush(); guard draft == nil else { return }
        selected = revision.noteId
        draft = Draft(noteId: revision.noteId, parents: heads.filter { $0.noteId == revision.noteId }.map(\.id), note: revision.note)
        persistDraft(); flush()
    }
    func mutate(_ id: String, _ change: (inout Note) -> Void) {
        let previous = selected; select(id); update(change); flush(); select(previous)
    }
    func duplicate(_ id: String) {
        flush(); guard let source = uniqueHeads.first(where: { $0.noteId == id }) else { return }
        create(); update { $0 = source.note; $0.title = source.note.displayTitle + " copy" }; flush()
    }
    func addImages() {
        let panel = NSOpenPanel(); panel.allowedContentTypes = [.image]; panel.allowsMultipleSelection = true
        if panel.runModal() == .OK { importImages(panel.urls) }
    }
    func importImages(_ urls: [URL]) {
        do {
            var attachments: [Attachment] = []
            for url in urls {
                let scoped = url.startAccessingSecurityScopedResource(); defer { if scoped { url.stopAccessingSecurityScopedResource() } }
                let data = try Data(contentsOf: url)
                guard NSImage(data: data) != nil else { throw LeafError.message("\(url.lastPathComponent) is not a supported image") }
                let hash = SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
                let destination = media.appendingPathComponent(hash)
                if !FileManager.default.fileExists(atPath: destination.path) { try data.write(to: destination, options: .atomic) }
                attachments.append(Attachment(id: hash, name: url.lastPathComponent, mime: UTType(filenameExtension: url.pathExtension)?.preferredMIMEType ?? "application/octet-stream"))
            }
            if selected == nil { create() }
            let offset = insertionOffset ?? EditorActions.shared.view?.selectedRange().location
            update { $0.insertMedia(attachments, at: activeBlock, offset: offset) }; insertionOffset = nil; flush()
        } catch { self.error = error.localizedDescription }
    }
    func exportBackup() {
        flush(); guard draft == nil else { return }
        let panel = NSSavePanel(); panel.nameFieldStringValue = "Pebble Notes Backup.leafbackup"
        guard panel.runModal() == .OK, let url = panel.url else { return }
        do {
            let records = try revisions()
            var files: [String: String] = [:]
            for id in Set(records.flatMap { $0.note.attachments.map(\.id) }) { files[id] = try Data(contentsOf: media.appendingPathComponent(id)).base64EncodedString() }
            let object: [String: Any] = ["format": "leaf-backup-1", "revisions": try JSONSerialization.jsonObject(with: Data(rawRevisions().utf8)), "media": files]
            try JSONSerialization.data(withJSONObject: object, options: [.sortedKeys]).write(to: url, options: .atomic)
            status = "Backup exported"
        } catch { self.error = error.localizedDescription }
    }
    func importBackup() {
        flush(); let panel = NSOpenPanel(); guard panel.runModal() == .OK, let url = panel.url else { return }
        do {
            let data = try Data(contentsOf: url)
            guard let object = try JSONSerialization.jsonObject(with: data) as? [String: Any], object["format"] as? String == "leaf-backup-1", let records = object["revisions"] as? [[String: Any]], let blobs = object["media"] as? [String: String] else { throw LeafError.message("This is not a Leaf backup") }
            // Validate the complete graph in an isolated database before touching the library.
            guard let validation = leaf_open(":memory:") else { throw LeafError.message("Could not validate backup") }
            defer { leaf_close(validation) }
            let existing = try JSONSerialization.jsonObject(with: Data(rawRevisions().utf8)) as! [[String: Any]]
            for record in existing { let raw = String(data: try JSONSerialization.data(withJSONObject: record, options: [.sortedKeys]), encoding: .utf8)!; guard leaf_put(validation, raw, 0) != 0 else { throw LeafError.message("Could not validate current library") } }
            for record in records {
                let raw = String(data: try JSONSerialization.data(withJSONObject: record, options: [.sortedKeys]), encoding: .utf8)!
                guard leaf_put(validation, raw, 0) != 0 else { throw LeafError.message(String(cString: leaf_error(validation))) }
                let r = try JSONDecoder().decode(Revision.self, from: Data(raw.utf8))
                for a in r.note.attachments { guard let b = blobs[a.id], let bytes = Data(base64Encoded: b), SHA256.hash(data: bytes).map({ String(format: "%02x", $0) }).joined() == a.id else { throw LeafError.message("Backup has missing or damaged images") } }
            }
            for (hash, b) in blobs {
                guard hash.count == 64, hash.allSatisfy({ "0123456789abcdef".contains($0) }), let bytes = Data(base64Encoded: b), SHA256.hash(data: bytes).map({ String(format: "%02x", $0) }).joined() == hash else { throw LeafError.message("Invalid backup attachment") }
                try bytes.write(to: media.appendingPathComponent(hash), options: .atomic)
            }
            for record in records { try ingest(String(data: try JSONSerialization.data(withJSONObject: record, options: [.sortedKeys]), encoding: .utf8)!, remote: false) }
            reload(); status = "Backup imported"
        } catch { self.error = error.localizedDescription }
    }
}

extension NoteStore {
    func permanentlyDelete(_ ids: [String]) {
        flush()
        do {
            for id in ids { guard let old = uniqueHeads.first(where: { $0.noteId == id && $0.note.deleted }) else { continue }
                var marker = Note(); marker.collection = old.note.collection; marker.deleted = true; marker.deletedAt = old.note.deletedAt ?? old.createdAt; marker.purgedAt = Int64(Date().timeIntervalSince1970 * 1000); marker.prepare()
                try ingest(try encode(Revision(id: UUID().uuidString.lowercased(), noteId: id, deviceId: deviceId, parents: [], createdAt: marker.purgedAt!, note: marker)), remote: false)
                if selected == id { selected = nil; draft = nil }
            }
            heads = try revisions(headsOnly: true)
            let referenced = Set(try revisions().flatMap { $0.note.attachments.map(\.id) })
            for file in try FileManager.default.contentsOfDirectory(at: media, includingPropertiesForKeys: nil) where !referenced.contains(file.lastPathComponent) { try? FileManager.default.removeItem(at: file) }
        } catch { self.error = error.localizedDescription }
    }
    func expireTrash(now: Int64 = Int64(Date().timeIntervalSince1970 * 1000)) {
        let expired = uniqueHeads.filter { $0.note.deleted && now - ($0.note.deletedAt ?? $0.createdAt) >= 30 * 86400000 }.map(\.noteId)
        if !expired.isEmpty { permanentlyDelete(expired) }
    }
}
