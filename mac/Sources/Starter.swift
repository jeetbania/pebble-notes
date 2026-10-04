import Foundation
import CryptoKit

extension NoteStore {
    func installStarterLibrary(from folder: URL) async throws {
        let marker = root.appendingPathComponent("starter-20261004.complete")
        guard !FileManager.default.fileExists(atPath: marker.path) else { return }
        flush(); guard draft == nil else { throw LeafError.message("Save pending edits before refreshing the starter library") }
        let raw = try Data(contentsOf: folder.appendingPathComponent("library.json"))
        let objects = try JSONSerialization.jsonObject(with: raw) as! [[String: Any]]
        let records = try JSONDecoder().decode([Revision].self, from: raw)
        guard let validator = leaf_open(":memory:") else { throw LeafError.message("Could not validate starter library") }; defer { leaf_close(validator) }
        var images: [String: Data] = [:]
        for object in objects { let json = String(data: try JSONSerialization.data(withJSONObject: object, options: [.sortedKeys]), encoding: .utf8)!; guard leaf_put(validator, json, 0) != 0 else { throw LeafError.message(String(cString: leaf_error(validator))) } }
        for a in records.flatMap({ $0.note.attachments }) { let bytes = try Data(contentsOf: folder.appendingPathComponent(a.id)); guard SHA256.hash(data: bytes).map({ String(format: "%02x", $0) }).joined() == a.id else { throw LeafError.message("Starter image integrity check failed") }; images[a.id] = bytes }
        let oldIds = uniqueHeads.filter { !$0.note.deleted && !$0.noteId.hasPrefix("leaf-starter-") }.map(\.noteId)
        if !(try revisions()).isEmpty { _ = try await DailyBackups.save(raw: rawRevisions(), root: root, force: true); let format = DateFormatter(); format.dateFormat = "yyyy-MM-dd"; let saved = root.appendingPathComponent("backups").appendingPathComponent(format.string(from: Date()) + ".leafbackup"); try Data(contentsOf: saved).write(to: root.appendingPathComponent("before-starter-refresh.leafbackup"), options: .atomic) }
        for (hash, bytes) in images { try bytes.write(to: media.appendingPathComponent(hash), options: .atomic) }
        for id in oldIds { mutate(id) { $0.deleted = true } }
        guard error == nil else { throw LeafError.message(error!) }
        for object in objects { try ingest(String(data: JSONSerialization.data(withJSONObject: object, options: [.sortedKeys]), encoding: .utf8)!, remote: false) }
        try Data("Starter library refreshed; earlier notes remain in Trash and history.".utf8).write(to: marker, options: .atomic)
        select(nil); reload(); _ = try await DailyBackups.save(raw: rawRevisions(), root: root, force: true)
    }
}
