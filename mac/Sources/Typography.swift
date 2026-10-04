import AppKit

enum Typography {
    static func size(_ style: String) -> Double {
        let values: [String: (String, Double, ClosedRange<Double>)] = ["body": ("typeBody",18,14...26), "headline": ("typeHeadline",21,17...34), "subtitle": ("typeSubtitle",24,18...34), "title": ("typeTitle",30,24...44)]
        let value = values[style] ?? values["body"]!
        let stored = UserDefaults.standard.object(forKey: value.0) as? Double ?? value.1
        return stored.isFinite ? min(value.2.upperBound, max(value.2.lowerBound, stored)) : value.1
    }
}
