import SwiftUI
import AppKit
import CryptoKit
import UniformTypeIdentifiers

extension NoteStore {
    func editBlock(_ id: String, text: String, spans: [TextSpan]) { update(undoKey: "typing:" + id) { $0.editBlock(id) { $0.text = text; $0.spans = spans } } }
    func setList(_ kind: String) { guard let block = activeBlock ?? current?.document.first(where: { $0.isText })?.id else { return }; update { $0.editBlock(block) { $0.kind = $0.kind == kind ? "text" : kind } } }
    func addBlock(_ kind: String) {
        var block = DocumentBlock(); block.kind = kind
        if kind == "table" { block.cells = [["", ""], ["", ""]] }
        update { note in var content = note.document; var anchor = activeBlock
            while let parent = content.first(where: { $0.id == anchor })?.parentId { anchor = parent }
            let group = anchor.map { note.descendants(of: $0) } ?? []
            let position = content.lastIndex(where: { group.contains($0.id) }).map { $0 + 1 } ?? content.count; content.insert(block, at: position); note.blocks = content }
        activeBlock = block.id
    }
    func changeBlock(_ id: String, _ change: (inout DocumentBlock) -> Void) { update { $0.editBlock(id, change) } }
    func addChild(to id: String) {
        var block = DocumentBlock(); block.parentId = id
        update { note in var content = note.document; guard let parent = content.firstIndex(where: { $0.id == id }) else { return }; content[parent].collapsed = false; let ids = note.descendants(of: id); let index = content.lastIndex(where: { ids.contains($0.id) })! + 1; content.insert(block, at: index); note.blocks = content }; activeBlock = block.id
    }
    func moveBlock(_ id: String, by delta: Int) { update { note in let peers = note.document.filter { $0.parentId == note.document.first(where: { $0.id == id })?.parentId }; if let i = peers.firstIndex(where: { $0.id == id }) { let j = min(max(0, i + delta), peers.count - 1); if j == i { return }; if delta < 0 { note.moveGroup(id, before: peers[j].id) } else { let group = note.descendants(of: id); let targetGroup = note.descendants(of: peers[j].id); var content = note.document; let moving = content.filter { group.contains($0.id) }; content.removeAll { group.contains($0.id) }; let target = content.lastIndex(where: { targetGroup.contains($0.id) })! + 1; content.insert(contentsOf: moving, at: target); note.blocks = content } } } }
    func removeBlock(_ id: String) { update { note in let ids = note.descendants(of: id); note.blocks = note.document.filter { !ids.contains($0.id) } } }
    func duplicateBlock(_ id: String) { update { note in let ids = note.descendants(of: id); var content = note.document; let originals = content.filter { ids.contains($0.id) }; let map = Dictionary(uniqueKeysWithValues: originals.map { ($0.id, UUID().uuidString.lowercased()) }); let copies = originals.map { original -> DocumentBlock in var b = original; b.id = map[b.id]!; if let parent = b.parentId, let replacement = map[parent] { b.parentId = replacement }; return b }; if let end = content.lastIndex(where: { ids.contains($0.id) }) { content.insert(contentsOf: copies, at: end + 1); note.blocks = content } } }
    func stageFiles(_ urls: [URL]) throws -> [Attachment] {
        try urls.map { url in let scoped = url.startAccessingSecurityScopedResource(); defer { if scoped { url.stopAccessingSecurityScopedResource() } }; let data = try Data(contentsOf: url); let hash = SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined(); let destination = media.appendingPathComponent(hash); if !FileManager.default.fileExists(atPath: destination.path) { try data.write(to: destination, options: .atomic) }; return Attachment(id: hash, name: url.lastPathComponent, mime: UTType(filenameExtension: url.pathExtension)?.preferredMIMEType ?? "application/octet-stream") }
    }
    func addFiles() { insertionOffset = EditorActions.shared.view?.selectedRange().location; let panel = NSOpenPanel(); panel.allowsMultipleSelection = true; if panel.runModal() == .OK { importFiles(panel.urls) } }
    func importFiles(_ urls: [URL]) {
        do {
            let values = try urls.map { url -> Attachment in
                let data = try Data(contentsOf: url); let hash = SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
                let destination = media.appendingPathComponent(hash); if !FileManager.default.fileExists(atPath: destination.path) { try data.write(to: destination, options: .atomic) }
                return Attachment(id: hash, name: url.lastPathComponent, mime: UTType(filenameExtension: url.pathExtension)?.preferredMIMEType ?? "application/octet-stream")
            }
            if selected == nil { create() }; update { $0.insertMedia(values, at: activeBlock, offset: insertionOffset) }; insertionOffset = nil; flush()
        } catch { self.error = error.localizedDescription }
    }
    func createFolder(_ name: String, emoji: String = "", image: URL? = nil) {
        let name = name.split(separator: "/").map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }.joined(separator: "/"); guard !name.isEmpty else { return }
        let previous = selected
        let id = folderHeads.first(where: { $0.note.collection == name })?.noteId ?? "folder-" + SHA256.hash(data: Data(name.utf8)).map { String(format: "%02x", $0) }.joined()
        select(id)
        var note = current ?? Note(); note.recordType = "folder"; note.collection = name; note.title = name; note.blocks = []; note.folderEmoji = emoji; note.folderImage = ""; note.attachments = []
        if let image { do { let bytes = try Data(contentsOf: image); let hash = SHA256.hash(data: bytes).map { String(format: "%02x", $0) }.joined(); try bytes.write(to: media.appendingPathComponent(hash), options: .atomic); note.folderImage = hash; note.attachments = [Attachment(id: hash, name: image.lastPathComponent, mime: UTType(filenameExtension: image.pathExtension)?.preferredMIMEType ?? "image/png")] } catch { self.error = error.localizedDescription; select(previous); return } }
        commitDraft(Draft(noteId: id, parents: allHeads.first(where: { $0.noteId == id }).map { [$0.id] } ?? [], note: note)); select(previous)
    }
    func renameFolder(_ old: String, to name: String) {
        for r in allHeads where r.note.collection == old || r.note.collection.hasPrefix(old + "/") { mutate(r.noteId) { n in n.collection = name + String(n.collection.dropFirst(old.count)); if n.recordType == "folder" { n.title = n.collection } } }
    }
    func deleteFolder(_ path: String) { guard path != "Personal" else { return }; flush(); guard draft == nil else { return }; let matches = allHeads.filter { $0.note.collection == path || $0.note.collection.hasPrefix(path + "/") }; for revision in matches { mutate(revision.noteId) { note in if note.recordType == "folder" { note.deleted = true } else { note.collection = "Personal" } } }; select(nil) }
    func scheduleBackup() {
        guard let raw = try? rawRevisions(), raw != "[]" else { return }; let directory = root
        Task { do { let result = try await DailyBackups.save(raw: raw, root: directory); backupStatus = result } catch { backupStatus = "Backup needs attention: " + error.localizedDescription } }
    }
    func exportMarkdown() {
        flush(); guard let note = current else { return }; let panel = NSSavePanel(); panel.nameFieldStringValue = note.displayTitle.replacingOccurrences(of: "/", with: "-") + ".zip"
        guard panel.runModal() == .OK, let destination = panel.url else { return }
        do { try PortableExport.write(note: note, media: media, to: destination); status = "Note exported" } catch { self.error = error.localizedDescription }
    }
}

actor DailyBackups {
    static let shared = DailyBackups()
    static func save(raw: String, root: URL, force: Bool = false) async throws -> String { try await shared.write(raw: raw, root: root, force: force) }
    func write(raw: String, root: URL, force: Bool) throws -> String {
        let directory = root.appendingPathComponent("backups"); try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let format = DateFormatter(); format.dateFormat = "yyyy-MM-dd"; let file = directory.appendingPathComponent(format.string(from: Date()) + ".leafbackup")
        if !force, let values = try? file.resourceValues(forKeys: [.contentModificationDateKey]), let date = values.contentModificationDate, Date().timeIntervalSince(date) < 3600 { return "Local backup saved today" }
        let records = try JSONDecoder().decode([Revision].self, from: Data(raw.utf8)); var files: [String: String] = [:]
        for hash in Set(records.flatMap { $0.note.attachments.map(\.id) }) { let data = try Data(contentsOf: root.appendingPathComponent("media").appendingPathComponent(hash)); guard SHA256.hash(data: data).map({ String(format: "%02x", $0) }).joined() == hash else { throw LeafError.message("An attachment failed its integrity check") }; files[hash] = data.base64EncodedString() }
        let object: [String: Any] = ["format": "leaf-backup-1", "revisions": try JSONSerialization.jsonObject(with: Data(raw.utf8)), "media": files]
        try JSONSerialization.data(withJSONObject: object, options: [.sortedKeys]).write(to: file, options: .atomic)
        let backups = try FileManager.default.contentsOfDirectory(at: directory, includingPropertiesForKeys: nil).filter { $0.pathExtension == "leafbackup" }.sorted { $0.lastPathComponent > $1.lastPathComponent }
        for old in backups.dropFirst(7) { try FileManager.default.removeItem(at: old) }
        return "Local backup saved today · up to 7 days kept"
    }
}
