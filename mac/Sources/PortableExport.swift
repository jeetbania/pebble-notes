import Foundation

enum PortableExport {
    static func markdown(_ note: Note) -> String {
        var lines = ["# " + note.displayTitle, ""]
        if !note.tags.isEmpty { lines += [note.tags.map { "#" + $0 }.joined(separator: " "), ""] }
        var number = 0
        for b in note.document {
            let prefix = String(repeating: "  ", count: b.indent + note.depth(b))
            switch b.kind {
            case "toggle": lines.append(prefix + "> " + b.text)
            case "divider": lines.append("---")
            case "bullet": lines.append(prefix + "- " + b.text)
            case "number": number += 1; lines.append(prefix + "\(number). " + b.text)
            case "check": lines.append(prefix + (b.checked ? "- [x] " : "- [ ] ") + b.text)
            case "image", "file": if let a = note.attachments.first(where: { $0.id == b.mediaId }) { lines.append((b.kind == "image" ? "!" : "") + "[" + b.caption.replacingOccurrences(of: "]", with: "\\]") + "](assets/" + a.id + ")") }
            case "table": for (index,row) in b.cells.enumerated() { lines.append("| " + row.map { $0.replacingOccurrences(of: "|", with: "\\|").replacingOccurrences(of: "\n", with: "<br>") }.joined(separator: " | ") + " |"); if index == 0 { lines.append("| " + row.map { _ in "---" }.joined(separator: " | ") + " |") } }
            default: lines += [prefix + b.text, ""]
            }
        }
        return lines.joined(separator: "\n")
    }
    static func write(note: Note, media: URL, to destination: URL) throws {
        let folder = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString); defer { try? FileManager.default.removeItem(at: folder) }
        try FileManager.default.createDirectory(at: folder.appendingPathComponent("assets"), withIntermediateDirectories: true)
        try Data(markdown(note).utf8).write(to: folder.appendingPathComponent("note.md"))
        try leafEncoder.encode(note).write(to: folder.appendingPathComponent("note.leaf.json"))
        for a in note.attachments { try FileManager.default.copyItem(at: media.appendingPathComponent(a.id), to: folder.appendingPathComponent("assets").appendingPathComponent(a.id)) }
        let process = Process(); process.executableURL = URL(fileURLWithPath: "/usr/bin/ditto"); process.arguments = ["-c", "-k", folder.path, destination.path]; try process.run(); process.waitUntilExit(); guard process.terminationStatus == 0 else { throw LeafError.message("Could not create note export") }
    }
}
