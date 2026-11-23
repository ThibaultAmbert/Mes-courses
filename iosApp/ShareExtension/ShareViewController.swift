import UIKit
import UniformTypeIdentifiers

/// « Partager » une page de recette depuis Safari : ouvre Mes courses sur l'import de cette recette.
final class ShareViewController: UIViewController {
    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        Task { await handle() }
    }

    private func handle() async {
        let items = (extensionContext?.inputItems as? [NSExtensionItem]) ?? []
        let providers = items.flatMap { $0.attachments ?? [] }
        var link: String?
        for p in providers where link == nil {
            if p.hasItemConformingToTypeIdentifier(UTType.url.identifier),
               let url = try? await p.loadItem(forTypeIdentifier: UTType.url.identifier) as? URL {
                link = url.absoluteString
            } else if p.hasItemConformingToTypeIdentifier(UTType.plainText.identifier),
                      let text = try? await p.loadItem(forTypeIdentifier: UTType.plainText.identifier) as? String {
                link = text
            }
        }
        if let link,
           let encoded = link.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed.subtracting(CharacterSet(charactersIn: "&=?+"))),
           let url = URL(string: "mescourses://import?url=\(encoded)") {
            openApp(url)
        }
        extensionContext?.completeRequest(returningItems: nil)
    }

    /// Une extension ne peut pas ouvrir d'app directement : on remonte jusqu'à l'application hôte.
    private func openApp(_ url: URL) {
        var responder: UIResponder? = self
        while let r = responder {
            if let app = r as? UIApplication {
                app.open(url)
                return
            }
            responder = r.next
        }
    }
}
