import Foundation
import ImageIO

/// Decoding happens off the UI thread; scrolling back reuses bounded previews.
actor PreviewImageCache {
    static let shared = PreviewImageCache()
    private let images = NSCache<NSString, CGImage>()
    private var pending: [String: Task<CGImage?, Never>] = [:]
    init() { images.totalCostLimit = 64 * 1024 * 1024 }
    func load(_ url: URL, pixels: Int) async -> CGImage? {
        let key = "\(url.path):\(pixels)"
        if let image = images.object(forKey: key as NSString) { return image }
        if let task = pending[key] { return await task.value }
        let task = Task.detached(priority: .utility) { () -> CGImage? in
            guard let source = CGImageSourceCreateWithURL(url as CFURL, nil) else { return nil }
            return CGImageSourceCreateThumbnailAtIndex(source, 0, [
                kCGImageSourceCreateThumbnailFromImageAlways: true,
                kCGImageSourceThumbnailMaxPixelSize: pixels,
                kCGImageSourceCreateThumbnailWithTransform: true,
                kCGImageSourceShouldCacheImmediately: true
            ] as CFDictionary)
        }
        pending[key] = task
        let image = await task.value
        pending[key] = nil
        if let image { images.setObject(image, forKey: key as NSString, cost: image.bytesPerRow * image.height) }
        return image
    }
}
