import SwiftUI
typealias LeafState<Value> = SwiftUI.State<Value>
import Foundation
import AppKit

struct TextSpan: Codable, Equatable { var start: Int; var length: Int; var kind: String }
struct Attachment: Codable, Identifiable, Equatable { var id: String; var name: String; var mime: String }
struct DocumentBlock: Codable, Equatable, Identifiable {
    var id = UUID().uuidString.lowercased()
    var parentId: String? = nil
    var collapsed: Bool? = nil
    var textStyle: String? = nil
    var kind = "text"
    var text = ""
    var spans: [TextSpan] = []
    var checked = false
    var indent = 0
    var mediaId = ""
    var caption = ""
    var presentation = "large"
    var cells: [[String]] = []
    var asNote: Note { var note = Note(); note.text = text; note.spans = spans; if let textStyle, textStyle != "body", !text.isEmpty { note.spans = spans.filter { !["title", "subtitle", "headline"].contains($0.kind) } + [TextSpan(start: 0, length: text.utf16.count, kind: textStyle)] }; return note }
    var isText: Bool { ["text", "bullet", "number", "check", "toggle"].contains(kind) }
}
struct TaskDetails: Codable, Equatable {
    var dueAt: Int64 = 0
    var hasTime = false
    var priority = 0
    var repeatRule = "none"
    var list = "Reminders"
    var completed = false
    var remind = false
    var status: String? = nil
    var stage: String { completed ? "done" : (status ?? "todo") }
    mutating func move(to stage: String) { if stage == "done" && !completed { complete() } else { completed = stage == "done"; status = stage } }
    var date: Date { Date(timeIntervalSince1970: Double(dueAt) / 1000) }
    var alertDate: Date { hasTime ? date : Calendar.current.date(bySettingHour: 9, minute: 0, second: 0, of: date)! }
    mutating func complete(now: Date = Date()) {
        guard repeatRule != "none", dueAt > 0 else { completed.toggle(); status = completed ? "done" : "todo"; return }
        let calendar = Calendar.current
        let component: Calendar.Component = repeatRule == "monthly" ? .month : .day
        let amount = repeatRule == "weekly" ? 7 : 1
        var next = calendar.date(byAdding: component, value: amount, to: date) ?? now
        while next <= now { next = calendar.date(byAdding: component, value: amount, to: next) ?? now.addingTimeInterval(86400) }
        dueAt = Int64(next.timeIntervalSince1970 * 1000); completed = false; status = "todo"
    }
}
struct Note: Codable, Equatable {
    var task: TaskDetails? = nil
    var textScale = 1.0
    var title = ""; var text = ""; var spans: [TextSpan] = []; var attachments: [Attachment] = []
    var collection = "Personal"; var pinned = false; var archived = false; var deleted = false
    var deletedAt: Int64? = nil
    var purgedAt: Int64? = nil
    var blocks: [DocumentBlock]? = nil
    var tags: [String] = []
    var recordType = "note"
    var folderEmoji = ""
    var folderImage = ""
    init() {}
    enum CodingKeys: String, CodingKey { case deletedAt, purgedAt, task, textScale, title, text, spans, attachments, collection, pinned, archived, deleted, blocks, tags, recordType, folderEmoji, folderImage }
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        deletedAt = try c.decodeIfPresent(Int64.self, forKey: .deletedAt)
        purgedAt = try c.decodeIfPresent(Int64.self, forKey: .purgedAt)
        task = try c.decodeIfPresent(TaskDetails.self, forKey: .task)
        textScale = min(1.6, max(0.8, try c.decodeIfPresent(Double.self, forKey: .textScale) ?? 1))
        title = try c.decode(String.self, forKey: .title); text = try c.decode(String.self, forKey: .text)
        spans = try c.decode([TextSpan].self, forKey: .spans); attachments = try c.decode([Attachment].self, forKey: .attachments)
        collection = try c.decode(String.self, forKey: .collection); pinned = try c.decode(Bool.self, forKey: .pinned); archived = try c.decode(Bool.self, forKey: .archived); deleted = try c.decode(Bool.self, forKey: .deleted)
        blocks = try c.decodeIfPresent([DocumentBlock].self, forKey: .blocks)
        tags = try c.decodeIfPresent([String].self, forKey: .tags) ?? []
        recordType = try c.decodeIfPresent(String.self, forKey: .recordType) ?? "note"
        folderEmoji = try c.decodeIfPresent(String.self, forKey: .folderEmoji) ?? ""
        folderImage = try c.decodeIfPresent(String.self, forKey: .folderImage) ?? ""
    }
    var displayTitle: String { title.isEmpty ? "Untitled note" : title }
    var document: [DocumentBlock] {
        if let blocks { return blocks }
        if recordType == "folder" { return [] }
        var body = DocumentBlock(); body.id = "body"; body.text = text; body.spans = spans
        return [body] + attachments.map { media in var b = DocumentBlock(); b.id = "media-" + media.id; b.kind = media.mime.hasPrefix("image/") ? "image" : "file"; b.mediaId = media.id; return b }
    }
    mutating func prepare(previous: Note? = nil) {
        if purgedAt != nil { title = ""; text = ""; spans = []; attachments = []; blocks = []; tags = []; task = nil; folderEmoji = ""; folderImage = ""; deleted = true; return }
        if deleted { if deletedAt == nil { deletedAt = Int64(Date().timeIntervalSince1970 * 1000) } } else { deletedAt = nil }
        if let previous, blocks == previous.blocks, text != previous.text || spans != previous.spans, blocks != nil {
            if let i = blocks?.firstIndex(where: { $0.isText }), blocks?.filter({ $0.isText }).count == 1 { blocks?[i].text = text; blocks?[i].spans = spans }
        }
        if blocks == nil { blocks = document }
        if let previous, blocks == previous.blocks, attachments != previous.attachments {
            let ids = Set(attachments.map(\.id)); blocks?.removeAll { ["image", "file"].contains($0.kind) && !ids.contains($0.mediaId) }
            let represented = Set(blocks?.map(\.mediaId) ?? [])
            for a in attachments where !represented.contains(a.id) { var b = DocumentBlock(); b.kind = a.mime.hasPrefix("image/") ? "image" : "file"; b.mediaId = a.id; blocks?.append(b) }
        }
        if recordType == "note", !(blocks?.contains(where: { $0.isText }) ?? false) { blocks?.append(DocumentBlock()) }
        tags = Array(Set(tags.map { $0.trimmingCharacters(in: .whitespacesAndNewlines).replacingOccurrences(of: "#", with: "").lowercased() }.filter { !$0.isEmpty })).sorted()
        if recordType == "folder" { return }
        text = ""; spans = []
        var number = 0
        for block in blocks ?? [] {
            if !text.isEmpty { text += "\n" }
            let prefix: String
            switch block.kind { case "bullet": prefix = "• "; case "number": number += 1; prefix = "\(number). "; case "check": prefix = block.checked ? "☑ " : "☐ "; default: prefix = "" }
            let content = block.kind == "table" ? block.cells.map { $0.joined(separator: " | ") }.joined(separator: "\n") : block.isText ? block.text : block.caption
            let start = text.utf16.count + prefix.utf16.count
            text += prefix + content
            if block.isText { spans += block.asNote.spans.map { TextSpan(start: start + $0.start, length: $0.length, kind: $0.kind) } }
        }
        let ids = Set((blocks ?? []).filter { ["image", "file"].contains($0.kind) }.map(\.mediaId))
        attachments.removeAll { !ids.contains($0.id) }
    }
    mutating func editBlock(_ id: String, _ change: (inout DocumentBlock) -> Void) {
        if blocks == nil { blocks = document }
        if let index = blocks?.firstIndex(where: { $0.id == id }) {
            let old = blocks![index]
            change(&blocks![index])
            if old.kind == "toggle", blocks![index].kind != "toggle" {
                blocks![index].collapsed = false
                for child in blocks!.indices where blocks![child].parentId == id { blocks![child].parentId = old.parentId }
            }
        }
        prepare()
    }
    mutating func insertMedia(_ media: [Attachment], at id: String?, offset: Int?) {
        if blocks == nil { blocks = document }
        var insertion = (id.flatMap { wanted in blocks?.firstIndex(where: { $0.id == wanted }) }).map { $0 + 1 } ?? blocks!.count
        if let offset, insertion > 0, insertion <= blocks!.count, blocks![insertion - 1].isText {
            let source = blocks![insertion - 1]; let length = source.text.utf16.count; let point = max(0, min(offset, length))
            var left = source; left.text = (source.text as NSString).substring(to: point); left.spans = clippedSpans(source.spans, start: 0, length: point)
            var right = source; right.id = UUID().uuidString.lowercased(); right.text = (source.text as NSString).substring(from: point); right.spans = clippedSpans(source.spans, start: point, length: length - point)
            blocks![insertion - 1] = left; blocks!.insert(right, at: insertion)
        }
        for a in media { if !attachments.contains(where: { $0.id == a.id }) { attachments.append(a) }; var block = DocumentBlock(); block.kind = a.mime.hasPrefix("image/") ? "image" : "file"; block.mediaId = a.id; blocks!.insert(block, at: insertion); insertion += 1 }
        prepare()
    }
}
func clippedSpans(_ spans: [TextSpan], start: Int, length: Int) -> [TextSpan] {
    spans.compactMap { span in let a = max(span.start, start), b = min(span.start + span.length, start + length); return b > a ? TextSpan(start: a - start, length: b - a, kind: span.kind) : nil }
}
struct Revision: Codable, Identifiable {
    var schema = 2; var id: String; var noteId: String; var deviceId: String; var parents: [String]; var createdAt: Int64; var note: Note
}
struct Draft: Codable { var noteId: String; var parents: [String]; var note: Note }
let leafEncoder: JSONEncoder = { let e = JSONEncoder(); e.outputFormatting = [.sortedKeys]; return e }()
func encode<T: Encodable>(_ value: T) throws -> String { String(data: try leafEncoder.encode(value), encoding: .utf8)! }
enum LeafError: LocalizedError {
    case message(String)
    var errorDescription: String? { if case .message(let s) = self { return s }; return nil }
}
extension Note {
    var attributed: NSAttributedString {
        let paragraph = NSMutableParagraphStyle(); paragraph.lineSpacing = 3
        let result = NSMutableAttributedString(string: text, attributes: [.paragraphStyle: paragraph, .font: NSFont.systemFont(ofSize: Typography.size("body") * textScale), NSAttributedString.Key("leafTextScale"): textScale, .foregroundColor: NSColor.labelColor])
        for span in spans.sorted(by: { ["title", "subtitle", "headline"].contains($0.kind) && !["title", "subtitle", "headline"].contains($1.kind) }) {
            guard span.start >= 0, span.length > 0, span.start <= result.length, span.length <= result.length - span.start else { continue }
            let range = NSRange(location: span.start, length: span.length)
            if span.kind == "bold" { result.addAttribute(NSAttributedString.Key("leafExplicitBold"), value: true, range: range) }
            switch span.kind {
            case "bold", "italic":
                result.enumerateAttribute(.font, in: range) { font, r, _ in
                    let mask: NSFontTraitMask = span.kind == "bold" ? .boldFontMask : .italicFontMask
                    result.addAttribute(.font, value: NSFontManager.shared.convert((font as? NSFont) ?? .systemFont(ofSize: 17), toHaveTrait: mask), range: r)
                }
            case "title", "subtitle", "headline":
                result.addAttribute(.font, value: NSFont.systemFont(ofSize: Typography.size(span.kind) * textScale, weight: .semibold), range: range)
                result.addAttribute(NSAttributedString.Key("leafTextStyle"), value: span.kind, range: range)
            case let kind where kind.hasPrefix("link:"):
                result.addAttribute(.link, value: String(kind.dropFirst(5)), range: range)
            case "highlight": result.addAttribute(.backgroundColor, value: NSColor.systemYellow.withAlphaComponent(0.3), range: range)
            case "strike": result.addAttribute(.strikethroughStyle, value: NSUnderlineStyle.single.rawValue, range: range)
            case "underline": result.addAttribute(.underlineStyle, value: NSUnderlineStyle.single.rawValue, range: range)
            default: break
            }
        }
        return result
    }
}
func spansFrom(_ text: NSAttributedString) -> [TextSpan] {
    var spans: [TextSpan] = []
    text.enumerateAttributes(in: NSRange(location: 0, length: text.length)) { attributes, range, _ in
        if let font = attributes[.font] as? NSFont {
            let traits = NSFontManager.shared.traits(of: font)
            if (traits.contains(.boldFontMask) || attributes[NSAttributedString.Key("leafExplicitBold")] as? Bool == true) && (attributes[NSAttributedString.Key("leafTextStyle")] == nil || attributes[NSAttributedString.Key("leafExplicitBold")] as? Bool == true) { spans.append(.init(start: range.location, length: range.length, kind: "bold")) }
            if traits.contains(.italicFontMask) { spans.append(.init(start: range.location, length: range.length, kind: "italic")) }
        }
        if let style = attributes[NSAttributedString.Key("leafTextStyle")] as? String, ["title", "subtitle", "headline"].contains(style) { spans.append(.init(start: range.location, length: range.length, kind: style)) }
        if attributes[.backgroundColor] != nil { spans.append(.init(start: range.location, length: range.length, kind: "highlight")) }
        if let link = attributes[.link] { spans.append(.init(start: range.location, length: range.length, kind: "link:" + String(describing: link))) }
        for (key, kind) in [(NSAttributedString.Key.strikethroughStyle, "strike"), (.underlineStyle, "underline")] {
            if let value = attributes[key] as? Int, value != 0, !(kind == "strike" && attributes[NSAttributedString.Key("leafCompletionStrike")] as? Bool == true) { spans.append(.init(start: range.location, length: range.length, kind: kind)) }
        }
    }
    return canonicalSpans(spans)
}

func canonicalSpans(_ spans: [TextSpan]) -> [TextSpan] {
    var result: [TextSpan] = []
    for kind in Array(Set(spans.map(\.kind))).sorted() {
        var merged: [TextSpan] = []
        for span in spans.filter({ $0.kind == kind && $0.length > 0 }).sorted(by: { $0.start < $1.start }) {
            if let last = merged.last, span.start <= last.start + last.length {
                merged[merged.count - 1].length = max(last.start + last.length, span.start + span.length) - last.start
            } else { merged.append(span) }
        }
        result += merged
    }
    return result.sorted { ($0.start, $0.length, $0.kind) < ($1.start, $1.length, $1.kind) }
}

extension Note {
    func descendants(of id: String) -> Set<String> {
        var ids: Set<String> = [id]
        for _ in 0..<2000 { let more = document.filter { $0.parentId.map(ids.contains) ?? false }.map(\.id); let before = ids.count; ids.formUnion(more); if before == ids.count { break } }
        return ids
    }
    func isVisible(_ block: DocumentBlock, dragging: String? = nil) -> Bool {
        var parent = block.parentId; var seen = Set<String>()
        while let id = parent, seen.insert(id).inserted, let b = document.first(where: { $0.id == id }) { if b.collapsed == true || dragging == id { return false }; parent = b.parentId }
        return true
    }
    func depth(_ block: DocumentBlock) -> Int {
        var parent = block.parentId; var seen = Set<String>()
        while let id = parent, seen.count < 8, seen.insert(id).inserted { parent = document.first(where: { $0.id == id })?.parentId }
        return seen.count
    }
    mutating func moveGroup(_ id: String, before target: String) {
        let ids = descendants(of: id); guard !ids.contains(target) else { return }
        var content = document; let group = content.filter { ids.contains($0.id) }; content.removeAll { ids.contains($0.id) }
        guard let position = content.firstIndex(where: { $0.id == target }) else { return }
        let parent = content[position].parentId
        let moved = group.map { b -> DocumentBlock in var b = b; if b.id == id { b.parentId = parent }; return b }
        content.insert(contentsOf: moved, at: position); blocks = content; prepare()
    }
}
